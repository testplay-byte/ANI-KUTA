# 07 — Subtitle Management

> Internal subtitle tracks (from MPV `track-list`), external subtitle
> loading (`sub-add` command), subtitle settings (font, size, color,
> position, delay), ASS styling override, and subtitle encoding
> detection.

## 1. Two sources of subtitles

### Internal / embedded subtitles

When mpv loads a video file, it discovers subtitle tracks inside the
container (MKV, MP4 SRT tracks, etc.). These appear in the `track-list`
property, which Animiru observes:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:307-310
propFlow<MPVNode>("track-list")
    .filterNotNull()
    .onEach { onTrackListChanged(it) }
    .launchIn(viewModelScope)
```

The handler:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1351-1368
fun onTrackListChanged(tracks: MPVNode) {
    val tracks = tracks.toObject<List<TrackNode>>(json).ifEmpty { return }
    updateStateData {
        it.copy(
            subtitleTracks = tracks.filter { it.isSubtitle }
                .filterNot { it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true },
            audioTracks = tracks.filter { it.isAudio }
                .filterNot { it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true },
        )
    }

    if (stateData.value.hasLoadedTracks) {
        onTrackAdded(tracks)
    } else {
        updateStateData { it.copy(hasLoadedTracks = true) }
        onTracksLoaded(tracks)
    }
}
```

Two paths:
- **First load** (`hasLoadedTracks == false`) → `onTracksLoaded` — sets
  up external tracks and picks the preferred one.
- **Subsequent track additions** (`hasLoadedTracks == true`) →
  `onTrackAdded` — handles external tracks that were just added via
  `sub-add` / `audio-add`.

### The `TrackNode` model

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVModels.kt:35-91
@Serializable
data class TrackNode(
    val id: Int,
    val type: String,
    @SerialName("src-id") val srcId: Long? = null,
    val title: String? = null,
    val lang: String? = null,
    val image: Boolean? = null,
    @SerialName("albumArt") val albumArt: Boolean? = null,
    val default: Boolean? = null,
    val forced: Boolean? = null,
    val dependent: Boolean? = null,
    @SerialName("visual-impaired") val visualImpaired: Boolean? = null,
    @SerialName("hearing-impaired") val hearingImpaired: Boolean? = null,
    @SerialName("hls-bitrate") val hlsBitrate: Long? = null,
    @SerialName("program-id") val programId: Long? = null,
    val selected: Boolean? = null,
    @SerialName("main-selection") val mainSelection: Long? = null,
    val external: Boolean? = null,
    @SerialName("external-filename") val externalFilename: String? = null,
    val codec: String? = null,
    @SerialName("codec-desc") val codecDesc: String? = null,
    @SerialName("codec-profile") val codecProfile: String? = null,
    @SerialName("ff-index") val ffIndex: Long? = null,
    val decoder: String? = null,
    @SerialName("decoder-desc") val decoderDesc: String? = null,
    @SerialName("demux-w") val demuxW: Long? = null,
    @SerialName("demux-h") val demuxH: Long? = null,
    /* ... many more fields ... */
    val metadata: Map<String, String?>? = null,
) {
    val isVideo = type == "video"
    val isAudio = type == "audio"
    val isSubtitle = type == "sub"
    val isSelected = selected == true

    fun getMetadata(key: String): String? = metadata?.get(key)
    fun hasMetadata(): Boolean = !metadata.isNullOrEmpty()
}
```

This is a direct serialization of mpv's `track-list/N` property tree.
Each track has:
- `id` — mpv's track ID (used for `sid`/`aid`).
- `type` — `"video"`, `"audio"`, or `"sub"`.
- `title`, `lang` — display info.
- `external`, `externalFilename` — true if loaded via `sub-add`/`audio-add`.
- `selected` — true if currently active.
- `default`, `forced`, `dependent` — track flags from the container.
- `codec`, `codecDesc` — e.g. `"ass"`, `"subrip"`.
- Various `demux-*` fields for audio/video tracks.

### The `VideoTrack` sealed interface

The player UI works with a unified `VideoTrack` type that wraps both
internal and external tracks:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVModels.kt:106-145
@Immutable
sealed interface VideoTrack {
    companion object {
        const val TRACK_TITLE_TAG = "aniyomi-track-index"
    }

    data class Internal(val data: TrackNode) : VideoTrack

    data class External(
        val data: Track,
        val index: Int,
        val id: Int? = null,
        val mainSelection: Int = -1,
        val state: TrackState = TrackState.Idle,
    ) : VideoTrack

    val title: String
        get() = when (this) {
            is External -> data.lang
            is Internal -> data.title.orEmpty()
        }

    val lang: String
        get() = when (this) {
            is External -> data.lang
            is Internal -> data.lang.orEmpty()
        }

    val selection: Int
        get() = when (this) {
            is External -> mainSelection
            is Internal -> data.mainSelection?.toInt() ?: -1
        }

    val trackId: Int?
        get() = when (this) {
            is External -> id
            is Internal -> data.id
        }
}
```

- **`Internal`** — wraps a `TrackNode` from mpv's `track-list`.
- **`External`** — wraps a `Track` (the source-api model), with an
  `index`, optional `id` (assigned by mpv after `sub-add`), and a
  `state` (`Idle`/`Loading`/`Error`/`Loaded`).

The `TRACK_TITLE_TAG = "aniyomi-track-index"` is the prefix used when
adding external tracks: mpv is told the track's title is
`"aniyomi-track-index=3"`, which lets the VM find the matching external
track when mpv's `track-list` updates.

## 2. External subtitle loading — `sub-add` command

When the user picks an external subtitle file (via SAF picker), the VM
calls `sub-add`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1491-1518
fun selectSub(track: VideoTrack) {
    when (track) {
        is VideoTrack.External -> {
            if (track.id == null) {
                updateSubtitleTrackAt(track.index) {
                    it.copy(state = TrackState.Loading)
                }
                viewModelScope.launchIO {
                    mpvCommand(
                        "sub-add",
                        track.data.url,
                        "auto",
                        "${VideoTrack.TRACK_TITLE_TAG}=${track.index}",
                    )
                }
            } else {
                updateStateData { it.copy(hasLoadedSubs = true) }
                checkFileLoaded()
                selectSubById(track.id)
            }
        }
        is VideoTrack.Internal -> {
            updateStateData { it.copy(hasLoadedSubs = true) }
            checkFileLoaded()
            selectSubById(track.data.id)
        }
    }
}
```

The `sub-add` command syntax:
```
sub-add <url> [<flags> [<title> [<lang>]]]
```

- `url` — the subtitle file path or `fd://...` (after `openContentFd`).
- `flags` — `"auto"` (let mpv pick), `"cached"` (cache contents), or
  `"select"` (immediately select).
- `title` — used by Animiru as `"aniyomi-track-index=N"` to track the
  external track.

The `"auto"` flag is used here (not `"select"`) because Animiru wants
to wait for the `track-list` event before selecting the track. The
track's mpv-assigned `id` isn't known until then.

### `onTrackAdded` — handling the new track

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1375-1416
private fun onTrackAdded(tracks: List<TrackNode>) {
    val externalSubtitle = tracks.filter {
        it.isSubtitle && it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true
    }
    val externalAudio = tracks.filter {
        it.isAudio && it.title?.startsWith(VideoTrack.TRACK_TITLE_TAG) == true
    }

    externalSubtitle.forEach { track ->
        val idx = track.title!!.split("=")[1].toInt()
        val external = stateData.value.externalSubtitleTracks[idx]

        if (external.id != null) {
            // External subtitle has already been added
            return@forEach
        }

        updateSubtitleTrackAt(idx) {
            it.copy(id = track.id, state = TrackState.Loaded)
        }
        updateStateData { it.copy(hasLoadedSubs = true) }
        checkFileLoaded()
        selectSubById(track.id)
    }

    externalAudio.forEach { track ->
        val idx = track.title!!.split("=")[1].toInt()
        val external = stateData.value.externalAudioTracks[idx]

        if (external.id != null) {
            // External audio has already been added
            return@forEach
        }

        updateAudioTrackAt(idx) {
            it.copy(id = track.id, state = TrackState.Loaded)
        }
        updateStateData { it.copy(hasLoadedAudio = true) }
        checkFileLoaded()
        selectAudioById(track.id, false)
    }
}
```

The flow:
1. mpv fires `track-list` change with a new track whose `title` is
   `"aniyomi-track-index=3"`.
2. The VM parses the index `3`, looks up `externalSubtitleTracks[3]`.
3. Updates that track's `id` to mpv's assigned track ID, sets state to
   `Loaded`.
4. Calls `selectSubById(track.id)` to switch the active subtitle.

### `onTrackLoadedFailure` — error path

If mpv fails to load the external file (e.g. unsupported format, IO
error), the log observer detects the failure:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:292-298
override fun logMessage(prefix: String, level: Int, text: String) {
    if (level == MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
        if (text.startsWith(TRACK_LOAD_FAILURE)) {
            val url = text.removePrefix(TRACK_LOAD_FAILURE).substringBeforeLast(".")
            _eventFlow.tryEmit(Event.TrackLoadFailure(url))
        }
    }
    // ...
}
```

The VM handler:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1621-1642
fun onTrackLoadedFailure(url: String) {
    val subtitleIdx = stateData.value.externalSubtitleTracks.indexOfFirst {
        it.data.url == url
    }
    if (subtitleIdx != -1) {
        updateSubtitleTrackAt(subtitleIdx) {
            it.copy(state = TrackState.Error)
        }
        updateStateData { it.copy(hasLoadedSubs = true) }
        checkFileLoaded()
    }
    val audioIdx = stateData.value.externalAudioTracks.indexOfFirst {
        it.data.url == url
    }
    if (audioIdx != -1) {
        updateAudioTrackAt(audioIdx) {
            it.copy(state = TrackState.Error)
        }
        updateStateData { it.copy(hasLoadedAudio = true) }
        checkFileLoaded()
    }
}
```

The matching external track is marked `TrackState.Error`. The
SubtitleTracksSheet shows an error icon next to it.

## 3. Adding external subtitles via SAF picker

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerSheets.kt:115-130
Sheets.SubtitleTracks -> {
    val subtitlesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) {
        if (it == null) return@rememberLauncherForActivityResult
        onAddSubtitle(it)
    }
    SubtitlesSheet(
        tracks = subtitles.toImmutableList(),
        onSelect = onSelectSubtitle,
        onAddSubtitle = { subtitlesPicker.launch(arrayOf("*/*")) },
        /* ... */
    )
}
```

The SAF picker is launched with `arrayOf("*/*")` (any mime type) —
Animiru doesn't filter by `.srt`/`.ass`/`.vtt` because Android's mime
type detection for subtitle files is unreliable. The `onAddSubtitle`
callback goes to the VM:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1478-1489
fun addSubtitle(uri: Uri) {
    val url = uri.toString()
    val isContentURI = url.startsWith("content://")
    val path = (if (isContentURI) uri.openContentFd(context) else url)
        ?: return
    val name = if (isContentURI) uri.getFileName(context) else null
    if (name == null) {
        mpvCommand("sub-add", path, "cached")
    } else {
        mpvCommand("sub-add", path, "cached", name)
    }
}
```

Note: when adding via SAF, the flag is `"cached"` (cache the file
contents) instead of `"auto"` (when adding from a Video's
subtitleTracks list). The reason: SAF file descriptors may not remain
valid; caching ensures mpv has its own copy.

The `getFileName` helper:
```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt:56-62
internal fun Uri.getFileName(context: Context): String? {
    return context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex)
    }
}
```

This queries the SAF for the file's display name, which becomes the
track title in mpv.

## 4. Initial track selection — `onTracksLoaded`

When the first `track-list` event fires (after `loadfile`), the VM:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1421-1456
private fun onTracksLoaded(tracks: List<TrackNode>) {
    val embeddedSubs = tracks.filter { it.isSubtitle }
    val embeddedAudio = tracks.filter { it.isAudio }
    val currentVideo = stateData.value.currentVideo
    val externalSubs = currentVideo?.subtitleTracks.orEmpty().distinctBy { it.url }
        .mapIndexed { idx, track -> VideoTrack.External(track, idx) }
    val externalAudio = currentVideo?.audioTracks.orEmpty().distinctBy { it.url }
        .mapIndexed { idx, track -> VideoTrack.External(track, idx) }

    updateStateData {
        it.copy(
            externalSubtitleTracks = externalSubs,
            externalAudioTracks = externalAudio,
        )
    }

    val preferredSubtitle = trackSelect.getPreferredTrackIndex(
        tracks = embeddedSubs.map { VideoTrack.Internal(it) } + externalSubs,
        subtitle = true,
    )
    if (preferredSubtitle == null) {
        updateStateData { it.copy(hasLoadedSubs = true) }
    } else {
        selectSub(preferredSubtitle)
    }

    val preferredAudio = trackSelect.getPreferredTrackIndex(
        tracks = embeddedAudio.map { VideoTrack.Internal(it) } + externalAudio,
        subtitle = false,
    )
    if (preferredAudio == null) {
        updateStateData { it.copy(hasLoadedAudio = true) }
    } else {
        selectAudio(preferredAudio, true)
    }
}
```

Two things happen:
1. The `Video` object's `subtitleTracks` / `audioTracks` (from the
   extension) are wrapped in `VideoTrack.External` and stored.
2. `TrackSelect.getPreferredTrackIndex` picks the best track based on
   user preferences.

## 5. `TrackSelect` — preferred-track picker

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/domain/TrackSelect.kt:11-69
class TrackSelect(
    private val subtitlePreferences: SubtitlePreferences = Injekt.get(),
    private val audioPreferences: AudioPreferences = Injekt.get(),
) {
    fun getPreferredTrackIndex(tracks: List<VideoTrack>, subtitle: Boolean = true): VideoTrack? {
        val prefLangs = if (subtitle) {
            subtitlePreferences.preferredSubLanguages.get()
        } else {
            audioPreferences.preferredAudioLanguages.get()
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val whitelist = if (subtitle) {
            subtitlePreferences.subtitleWhitelist.get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val blacklist = if (subtitle) {
            subtitlePreferences.subtitleBlacklist.get()
        } else {
            ""
        }.split(",").filter(String::isNotEmpty).map(String::trim)

        val locales = prefLangs.map(::Locale).ifEmpty {
            listOf(LocaleListCompat.getDefault()[0]!!)
        }

        val chosenLocale = locales.firstOrNull { locale ->
            tracks.any { t -> containsLang(t, locale) }
        }

        val filtered = tracks.withIndex()
            .filterNot { (_, track) ->
                blacklist.any { track.title.contains(it, true) }
            }
            .filter { (_, track) ->
                chosenLocale?.let { containsLang(track, it) } ?: true
            }

        whitelist.forEach { w ->
            filtered.firstOrNull { (_, track) ->
                track.title.contains(w, true)
            }?.let { return it.value }
        }

        return filtered.getOrNull(0)?.value
    }

    private fun containsLang(track: VideoTrack, locale: Locale): Boolean {
        val localName = locale.getDisplayName(locale)
        val englishName = locale.getDisplayName(Locale.ENGLISH).substringBefore(" (")
        val langRegex = Regex("""\b${locale.isO3Language}|${locale.language}\b""", RegexOption.IGNORE_CASE)
        val trackTitle = track.title

        return trackTitle.contains(localName, true) ||
            trackTitle.contains(englishName, true) ||
            track.lang.let { langRegex.find(it) != null }
    }
}
```

Selection algorithm:
1. Parse preferred languages from prefs (comma-separated, e.g. `"en,ja"`).
2. Parse whitelist (titles that must match) and blacklist (titles that
   must not match).
3. Convert language codes to `Locale` objects. If no preferences, use
   the device default locale.
4. Find the first locale that has any matching track.
5. Filter tracks: exclude blacklisted, keep only matching-locale (or
   all if no locale matched).
6. Return the first whitelisted track (if any), else the first filtered
   track.

The `containsLang` check is three-tier:
- Track title contains the locale's display name (in that locale).
- Track title contains the locale's English name.
- Track lang matches the locale's ISO3 or ISO2 code via regex.

This is fuzzy because extension-provided track titles are inconsistent
(some use `"English"`, some `"eng"`, some `"en"`).

## 6. Subtitle settings — applied at MPV init time

Most subtitle settings are applied once at `MPVPlayer` init (see
`03-mpv-initialization.md §6`). They affect the next `loadfile`.

| Setting | Preference | MPV option |
|---------|-----------|------------|
| Font family | `subtitleFont` (default "Sans Serif") | `sub-font` |
| Font size | `subtitleFontSize` (default 55) | `sub-font-size` |
| Font scale | `subtitleFontScale` (default 1.0) | `sub-scale` |
| Bold | `boldSubtitles` (default false) | `sub-bold` |
| Italic | `italicSubtitles` (default false) | `sub-italic` |
| Justify | `subtitleJustification` (default Auto) | `sub-justify` |
| Text color | `textColorSubtitles` (default White) | `sub-color` |
| Border color | `borderColorSubtitles` (default Black) | `sub-outline-color` |
| Background color | `backgroundColorSubtitles` (default Transparent) | `sub-back-color` |
| Border style | `borderStyleSubtitles` (default OutlineAndShadow) | `sub-border-style` |
| Border size | `subtitleBorderSize` (default 3) | `sub-outline-size` |
| Shadow offset | `shadowOffsetSubtitles` (default 0) | `sub-shadow-offset` |
| Position | `subtitlePos` (default 100 = bottom) | `sub-pos` |
| ASS override | `overrideSubsASS` (default No) | `sub-ass-override` |
| Black bars | `subtitleBlackBars` (default false) | `sub-ass-force-margins`, `sub-use-margins` |
| Delay (primary) | `subtitlesDelay` (default 0 ms) | `sub-delay` |
| Delay (secondary) | `subtitlesSecondaryDelay` (default 0 ms) | `secondary-sub-delay` |
| Speed | `subtitlesSpeed` (default 1.0) | `sub-speed` |

## 7. Runtime subtitle changes via panels

The `SubtitleSettingsPanel` (and friends) let the user change these
settings at runtime. Each change calls both `setPropertyX` (immediate
MPV update) and `pref.set(value)` (persist for next session):

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerScreen.kt:331-393
onSubBoldChange = {
    viewModel.setPropertyBoolean("sub-bold", it)
    subtitlePreferences.boldSubtitles.set(it)
},
onSubItalicChange = {
    viewModel.setPropertyBoolean("sub-italic", it)
    subtitlePreferences.italicSubtitles.set(it)
},
onSubJustifyChange = {
    viewModel.setPropertyString("sub-justify", it.value)
    subtitlePreferences.subtitleJustification.set(it)
},
onSubFontChange = {
    viewModel.setPropertyString("sub-font", it)
    subtitlePreferences.subtitleFont.set(it)
},
onSubFontSizeChange = {
    viewModel.setPropertyInt("sub-font-size", it)
    subtitlePreferences.subtitleFontSize.set(it)
},
onSubBorderStyleChange = {
    viewModel.setPropertyString("sub-border-style", it.value)
    subtitlePreferences.borderStyleSubtitles.set(it)
},
onSubBorderSizeChange = {
    viewModel.setPropertyInt("sub-outline-size", it)
    subtitlePreferences.subtitleBorderSize.set(it)
},
onSubShadowOffsetChange = {
    viewModel.setPropertyInt("sub-shadow-offset", it)
    subtitlePreferences.shadowOffsetSubtitles.set(it)
},
onSubColorChange = {
    when (subtitleColorType) {
        SubColorType.Text -> {
            viewModel.setPropertyString("sub-color", it.toColorHexString())
            subtitlePreferences.textColorSubtitles.set(it)
        }
        SubColorType.Border -> {
            viewModel.setPropertyString("sub-outline-color", it.toColorHexString())
            subtitlePreferences.borderColorSubtitles.set(it)
        }
        SubColorType.Background -> {
            viewModel.setPropertyString("sub-back-color", it.toColorHexString())
            subtitlePreferences.backgroundColorSubtitles.set(it)
        }
    }
},
onOverrideAssSubsChange = {
    viewModel.setPropertyString("sub-ass-override", it.value)
    subtitlePreferences.overrideSubsASS.set(it)
},
onSubScaleChange = {
    viewModel.setPropertyFloat("sub-scale", it)
    subtitlePreferences.subtitleFontScale.set(it)
},
onSubPosChange = {
    viewModel.setPropertyInt("sub-pos", it)
    subtitlePreferences.subtitlePos.set(it)
},
```

Most of these are runtime properties (mpv supports changing them mid-playback).
A few (notably `sub-ass-override`, `sub-ass-force-margins`) are
init-time options — mpv applies them on the next file load.

### `SubtitleAssOverride` enum

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt:92-114
enum class SubtitleAssOverride(
    val value: String,
    val titleRes: StringResource,
) {
    No("no", AMMR.strings.player_sheets_subtitles_ass_no),
    Yes("yes", AMMR.strings.player_sheets_subtitles_ass_yes),
    Scale("scale", AMMR.strings.player_sheets_subtitles_ass_scale),
    Force("force", AMMR.strings.player_sheets_subtitles_ass_force),
    Strip("strip", AMMR.strings.player_sheets_subtitles_ass_strip),
    ;
}
```

| Value | Behavior |
|-------|----------|
| `No` | ASS styles are honored as-is. |
| `Yes` | Override ASS with the user's subtitle settings. |
| `Scale` | Override only scale/position, keep ASS colors/fonts. |
| `Force` | Override everything (like Yes, but more aggressive). |
| `Strip` | Strip ASS styling entirely, treat as plain text. |

In `MPVPlayer.setupSubtitlesOptions`:
```kotlin
// MPVPlayer.kt:212-217
subtitlePreferences.overrideSubsASS.get().let {
    mpv.setOptionString("sub-ass-override", it.value)
    if (it != SubtitleAssOverride.No) {
        mpv.setOptionString("sub-ass-justify", "yes")
    }
}
```

When override is on, `sub-ass-justify=yes` is also set so the user's
justify preference applies to ASS subs too.

## 8. Subtitle delay panel

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleDelayPanel.kt (excerpt)
```

The SubtitleDelayPanel is anchored to the right side (like other panels).
It exposes:
- Primary delay (ms) — `sub-delay` (in seconds, divided by 1000).
- Secondary delay (ms) — `secondary-sub-delay`.
- Subtitle speed — `sub-speed` (multiplier).

The panel has Apply and Reset buttons. Apply persists the current
runtime value to the preference (so it survives across videos). Reset
restores from the preference.

From `PlayerScreen.kt:420-443`:
```kotlin
subDelayMsPrimary = subDelay?.times(1000)?.roundToInt() ?: subDelayPref,
subDelayMsSecondary = subDelaySecondary?.times(1000)?.roundToInt() ?: subDelaySecondaryPref,
subSpeed = subSpeed ?: subtitlePreferences.subtitlesSpeed.get().toDouble(),
onSubDelayPrimaryChange = {
    viewModel.setPropertyDouble("sub-delay", it / 1000.0)
},
onSubDelaySecondaryChange = {
    viewModel.setPropertyDouble("secondary-sub-delay", it / 1000.0)
},
onSubSpeedChange = {
    viewModel.setPropertyDouble("sub-speed", it)
},
onSubDelayApply = {
    subtitlePreferences.subtitlesDelay.set((subDelay?.times(1000)?.roundToInt()) ?: 0)
    subtitlePreferences.subtitlesSecondaryDelay.set((subDelaySecondary?.times(1000)?.roundToInt()) ?: 0)
},
onSubDelayReset = {
    viewModel.setPropertyDouble("sub-delay", subtitlePreferences.subtitlesDelay.get() / 1000.0)
    viewModel.setPropertyDouble(
        "secondary-sub-delay",
        subtitlePreferences.subtitlesSecondaryDelay.get() / 1000.0,
    )
    viewModel.setPropertyDouble("sub-speed", subtitlePreferences.subtitlesSpeed.get().toDouble())
},
```

Note: the panel shows `subDelay` from mpv (live value), falling back to
the preference if null. This means if the user changes delay via Lua
script, the panel reflects it.

## 9. Font picker — the system-fonts toggle

The `subtitleSystemFonts` preference controls whether the font picker
shows system fonts in addition to user-uploaded fonts:

```kotlin
// PlayerViewModel.kt:530-571
fun fetchFonts(includeSystemFonts: Boolean): List<String> {
    val fontFiles = mutableListOf<String>()

    storageManager.getFontsDirectory()?.listFiles()?.filter { file ->
        file.name?.lowercase()?.matches(fontExtensionRegex) == true
    }?.mapNotNull {
        try {
            TTFFile.open(it.openInputStream()).families.values.first()
        } catch (_: Exception) {
            null
        }
    }?.let {
        fontFiles.addAll(it)
    }

    if (!includeSystemFonts) {
        return fontFiles.distinct()
    }

    val fontDirectories = listOf(
        "/system/fonts/",
        "/product/fonts/",
    )

    for (directory in fontDirectories) {
        val dir = File(directory)
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            files?.filter { file ->
                file.isFile && file.name.lowercase()?.matches(fontExtensionRegex) == true
            }?.forEach { file ->
                try {
                    fontFiles.add(
                        TTFFile.open(file.inputStream()).families.values.first(),
                    )
                } catch (_: Exception) { }
            }
        }
    }

    return fontFiles.distinct()
}
```

Uses `truetypeparser-light` to extract the font family name from TTF/OTF
files. The result is a list of family names (not file paths) that mpv's
`sub-font` option accepts.

User fonts live in `storageManager.getFontsDirectory()` (SAF location
configured by the user). They're copied to `filesDir/mpv/fonts/` by
`MpvConfig.copyFontsDirectory()` at app start.

## 10. The fonts.conf + fontconfig setup

mpv uses fontconfig to resolve font family names. Animiru generates a
`fonts.conf` at `filesDir/mpv/fonts.conf`:

```kotlin
// app/src/main/java/animiru/feature/mpvfiles/MpvConfig.kt:145-180
private fun writeFontsConf(context: Context, mpvDir: UniFile) {
    val parts = mutableListOf(
        "<fontconfig>",
        // Android system fonts reside here
        "<dir>/system/fonts/</dir>",
        "<dir>/product/fonts/</dir>",
        // User provided fonts
        "<dir>${mpvDir.createDirectory(MPV_FONTS_DIR)!!.filePath!!}</dir>",
        // Point fontconfig to the right cache path so that caching works
        "<cachedir>${context.cacheDir.path}</cachedir>",
        // Conveniently there is *no* Java API to query the system default fonts, but we can
        // manually specify the font families we know Android uses and provides by default.
        // (compare to 60-latin.conf shipped with fontconfig)
        "<alias><family>serif</family>",
        "<prefer><family>Noto Serif</family></prefer>",
        "</alias>",
        "<alias><family>Sans Serif</family>",
        "<prefer>",
        "<family>Roboto</family>",
        "<family>Noto Sans</family>", // other languages
        "</prefer>",
        "</alias>",
        "<alias><family>monospace</family>",
        "<prefer><family>Droid Sans Mono</family></prefer>",
        "</alias>",
        "</fontconfig>",
    )
    try {
        val file = mpvDir.createFile("fonts.conf")
        file?.openOutputStream()?.bufferedWriter()?.use {
            it.write(parts.joinToString("\n"))
        }
    } catch (e: IOException) {
        logcat(LogPriority.ERROR, e) { "Failed to write fonts.conf" }
    }
}
```

This config:
- Includes `/system/fonts/` and `/product/fonts/` (Android system fonts).
- Includes the user's font directory (`filesDir/mpv/fonts/`).
- Caches fontconfig results in `cacheDir` (cleared on app cache clear).
- Defines aliases: `serif` → Noto Serif, `Sans Serif` → Roboto/Noto Sans,
  `monospace` → Droid Sans Mono.

The default subtitle font preference is `"Sans Serif"`, which fontconfig
resolves to Roboto via the alias.

## 11. Subtitle encoding detection — not implemented

Animiru does **not** implement subtitle encoding detection. If a
subtitle file is in a non-UTF-8 encoding (e.g. Shift-JIS for Japanese
subs), mpv will display garbled characters unless the user manually
converts the file.

mpv has a `--sub-codepage` option that can be set to a specific encoding
or `"auto"` (which uses libuchardet if available). Animiru doesn't set
this, so it defaults to mpv's compiled-in default (usually UTF-8 with
fallback to auto-detection).

> ANI-KUTA: If encoding detection becomes a feature, the `sub-codepage`
> option should be added to `MPVPlayer.setupSubtitlesOptions` and
> exposed in the SubtitleSettingsPanel.

## 12. Secondary subtitle display

Animiru supports two simultaneous subtitles (primary + secondary). The
secondary subtitle is rendered above the primary, in a smaller font.

- `secondary-sid` — the mpv property for the secondary subtitle track ID.
- `secondary-sub-delay` — separate delay for the secondary.
- `sub-ass-override` applies to both.

The selection logic in `selectSubById`:
```kotlin
// PlayerViewModel.kt:1520-1531
private fun selectSubById(id: Int) {
    val selectedSubs = Pair(mpv.getPropertyInt("sid"), mpv.getPropertyInt("secondary-sid"))
    when (id) {
        selectedSubs.first -> Pair(selectedSubs.second, null)       // tap primary → make secondary
        selectedSubs.second -> Pair(selectedSubs.first, null)       // tap secondary → remove
        else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
    }.let {
        it.second?.let { setPropertyInt("secondary-sid", it) }
            ?: setPropertyBoolean("secondary-sid", false)
        it.first?.let { setPropertyInt("sid", it) } ?: setPropertyBoolean("sid", false)
    }
}
```

The SubtitleTracksSheet shows `#1` next to the primary and `#2` next to
the secondary.

## 13. Quirks + warnings

1. **`sub-ass-force-margins` is an init-time option** — must be set
   before `loadfile`. ANI-KUTA's prior worklog note (point 5) flagged
   this: setting it at runtime produces wrong subtitle rendering.
   Animiru correctly sets it at init.

2. **`sub-ass-override` is also an init-time option** — same caveat.
   The SubtitleSettingsPanel changes it via `setPropertyString`, which
   affects the *next* file load but not the current one. This is a
   known UX issue; the panel should probably show a hint like
   "takes effect on next video".

3. **External subtitle FD leak risk** — when `Utils.findRealPath`
   fails, the FD is returned as `"fd://$it"` and mpv takes ownership.
   If mpv fails to load the file, the FD leaks. This is rare in
   practice (most SAF files have real paths) but worth noting.

4. **`sub-add` with `"cached"` flag** — caches the file contents in
   memory. For very large subtitle files (rare), this could be
   memory-heavy. The `"auto"` flag (used for Video.subtitleTracks)
   doesn't cache — mpv reads the file on demand.

5. **Track title collision** — if an extension happens to use the
   prefix `"aniyomi-track-index="` in a track title, the VM's parsing
   would break. This is a very unlikely edge case.

6. **`TrackSelect.containsLang` is fuzzy** — it matches on display
   names (English, local) and ISO codes. If an extension uses an
   unusual format (e.g. `"Japanese (Romanji)"`), the match may fail
   and the preferred-language track won't be auto-selected.

7. **No subtitle preview** — the SubtitleSettingsPanel doesn't show a
   live preview of subtitle rendering. The user has to look at the
   actual video to see the effect. This is a UX limitation.

8. **Whitelist overrides language preference** — in `TrackSelect`, the
   whitelist check runs *after* language filtering, but it returns the
   first whitelisted track even if a non-whitelisted track in the
   preferred language exists. This is intentional (whitelist is meant
   to be a hard preference) but could surprise users.
