# 03 — MPV Initialization Pipeline

> Where `mpv.conf` is generated/read, what options are set at init time,
> what properties are observed, hardware decoding config, video output
> config, cache config, network config, and subtitle config.

## 1. Where mpv.conf lives

Animiru's MPV configuration directory is `<filesDir>/mpv/`. This is set
twice — first as `config-dir`, then the wrapper writes the user's custom
`mpv.conf` and `input.conf` files there:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:67-83
init {
    val cachePath: String = context.cacheDir.path

    val mpvDir = UniFile.fromFile(context.filesDir)!!.createDirectory(MPV_DIR)!!

    val mpvConfFile = mpvDir.createFile("mpv.conf")!!
    advancedPreferences.mpvConf.get().let { mpvConfFile.writeText(it) }
    val mpvInputFile = mpvDir.createFile("input.conf")!!
    advancedPreferences.mpvInput.get().let { mpvInputFile.writeText(it) }

    mpv = MPV(context) {
        it.setOptionString("config", "yes")
        it.setOptionString("config-dir", context.filesDir.resolve(MPV_DIR).toString())
        it.setOptionString("gpu-shader-cache-dir", cachePath)
        it.setOptionString("icc-cache-dir", cachePath)
        it.setOptionString("keep-open", "yes")
    }
    // ...
}
```

The `MPV_DIR` constant is `"mpv"` (`MPVPlayer.kt:414`). So on a typical
device the path is `/data/data/xyz.Quickdev.Animiru.mi/files/mpv/mpv.conf`.

The `advancedPreferences.mpvConf` preference stores user-edited config as
a string (default empty). The user can put any mpv option line in there
(`vo=gpu-next`, `cache=yes`, `hr-seek=yes`, etc.) — Animiru parses those
and avoids re-setting them in code (the `setSafeOptionString` helper).

## 2. `MpvConfig.copyFiles()` — the assets + user files pipeline

Before the player can be created, `MpvConfig` (a separate class in the
`:app` module) must have copied the runtime files to `filesDir/mpv/`:

```kotlin
// app/src/main/java/animiru/feature/mpvfiles/MpvConfig.kt:32-42
fun copyFiles() {
    if (copyJob?.isActive == true) return

    copyJob = scope.launchIO {
        val mpvDir = getMpvDir()
        copyUserFiles(mpvDir)
        copyFontsDirectory(mpvDir)
        copyAssets(mpvDir)
        writeFontsConf(context, mpvDir)
    }
}
```

What it copies, in order:

1. **`copyUserFiles`** (line 48-71): if `advancedPreferences.mpvUserFiles`
   is on, copies the contents of `storageManager.getScriptsDirectory()`,
   `getScriptOptsDirectory()`, and `getShadersDirectory()` (all
   user-accessible SAF locations) into `filesDir/mpv/scripts/`,
   `script-opts/`, and `shaders/`. Then writes the bundled
   `aniyomi.lua` asset into `scripts/aniyomi.lua`. Also writes
   `custombuttons.lua` from the user's custom buttons.

2. **`copyFontsDirectory`** (line 111-116): copies user fonts into
   `filesDir/mpv/fonts/`. The comment "TODO: I think this is a bad hack"
   acknowledges this should ideally be done via a fontconfig directory
   directive.

3. **`copyAssets`** (line 118-143): copies `cacert.pem` from assets into
   `filesDir/mpv/cacert.pem` (used for `tls-ca-file`). Skips if file
   size matches (cached).

4. **`writeFontsConf`** (line 145-180): generates a `fonts.conf` file for
   fontconfig that:
   - Includes `/system/fonts/` and `/product/fonts/` (Android system fonts).
   - Includes the user fonts directory.
   - Points the fontconfig cache to `context.cacheDir`.
   - Defines font aliases (serif→Noto Serif, sans-serif→Roboto/Noto Sans,
     monospace→Droid Sans Mono).

The subdirectories are versioned constants (`MpvConfig.kt:202-208`):
```kotlin
companion object {
    const val MPV_DIR = "mpv"
    const val MPV_FONTS_DIR = "fonts"
    const val MPV_SCRIPTS_DIR = "scripts"
    const val MPV_SCRIPTS_OPTS_DIR = "script-opts"
    const val MPV_SHADERS_DIR = "shaders"
}
```

> ANI-KUTA: This asset-copy pipeline is a process-startup cost. Animiru
> calls `MpvConfig.copyFiles()` from `AppModule` / app initialization
> (not shown in this file). The same call must happen before
> `MPVPlayer(...)` is constructed. The Lua bridge (`aniyomi.lua`) is
> essential for custom buttons but optional otherwise.

## 3. `setSafeOptionString` — avoiding conflicts with user's mpv.conf

After writing the user's `mpv.conf`, the init code parses it to find which
option names it sets, then uses `setSafeOptionString` to skip those when
setting hardcoded options:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:85-94
val optionNameRegex = Regex("""^(?:--)?([\w-]+)(?:=|$)""", RegexOption.MULTILINE)
val mpvOptionNames = optionNameRegex.findAll(advancedPreferences.mpvConf.get()).map {
    it.groupValues[1].removePrefix("no-")
}.toSet()

// Set mpv option unless it's present in mpv.conf
fun setSafeOptionString(name: String, value: String) {
    if (name in mpvOptionNames) return
    mpv.setOptionString(name, value)
}
```

This means user-edited mpv.conf options **take priority** over Animiru's
hardcoded defaults. Important for power users.

## 4. Hardcoded options set at init time

The full list of `setOptionString` / `setSafeOptionString` calls in
`MPVPlayer.init`:

### Video output + decoder

| Option | Value | Source | Notes |
|--------|-------|--------|-------|
| `vo` | `"gpu"` or `"gpu-next"` | `MPVPlayer.kt:96` | Decided by `decoderPreferences.gpuNext` in `PlayerViewModel.kt:169` and passed to MPVPlayer constructor. |
| `profile` | `"fast"` | `MPVPlayer.kt:97` (safe) | mpv's built-in fast profile. |
| `hwdec` | `"mediacodec,mediacodec-copy"` or `"no"` | `MPVPlayer.kt:98` | Based on `decoderPreferences.tryHWDecoding`. |
| `vf` | `"format=yuv420p"` | `MPVPlayer.kt:100` | Only if `decoderPreferences.useYUV420P` is on. Some devices need this for hwdec. |

### Logging + input

| Option | Value | Source |
|--------|-------|--------|
| `msg-level` | `"all=v"` if verbose, else `"all=warn"` | `MPVPlayer.kt:103` |
| `input-default-bindings` | `true` (set as property, not option) | `MPVPlayer.kt:104` |
| `idle` | `"yes"` | `MPVPlayer.kt:105` |
| `ytdl` | `"no"` | `MPVPlayer.kt:106` |

### Network / TLS

| Option | Value | Source |
|--------|-------|--------|
| `tls-verify` | `"yes"` | `MPVPlayer.kt:107` (safe) |
| `tls-ca-file` | `"<filesDir>/mpv/cacert.pem"` | `MPVPlayer.kt:108` (safe) |

> ANI-KUTA note: The `tls-ca-file` is the system `cacert.pem` shipped as
> an asset (Mozilla's CA bundle). This is necessary because Android's
> system trust store isn't directly readable by mpv's TLS code. ANI-KUTA
> must ship this file too, or use `--tls-ca-file=/system/etc/security/cacerts`.

### Track selection (forced)

| Option | Value | Source | Notes |
|--------|-------|--------|-------|
| `sid` | `"no"` | `MPVPlayer.kt:111` | Disable auto subtitle selection. VM picks after `track-list` event. |
| `aid` | `"no"` | `MPVPlayer.kt:112` | Disable auto audio selection. Same reason. |

### Cache

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:114-117
val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
setSafeOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
setSafeOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
```

- 64 MiB forward + 64 MiB backward on Android 8.1+.
- 32 MiB each on Android 8.0 (lower memory ceiling).
- Forward + backward = total ~128 MiB or ~64 MiB.

### Screenshot directory

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:119-122
val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).also {
    it.mkdirs()
}
mpv.setOptionString("screenshot-directory", screenshotDir.path)
```

The screenshot directory is `Pictures/` on shared storage. Used by the
`screenshot-to-file` MPV command (`PlayerViewModel.kt:2610-2621`).

### Video filters (brightness, saturation, contrast, gamma, hue)

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:124-126
VideoFilters.entries.forEach {
    mpv.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
}
```

`VideoFilters` enum (`PlayerEnums.kt:145-175`):
- `brightness` → `pref_player_filter_brightness` (default 0)
- `saturation` → `pref_player_filter_saturation` (default 0)
- `contrast` → `pref_player_filter_contrast` (default 0)
- `gamma` → `pref_player_filter_gamma` (default 0)
- `hue` → `pref_player_filter_hue` (default 0)

### Playback speed

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:128
mpv.setOptionString("speed", playerPreferences.playerSpeed.get().toString())
```

The default playback speed (1.0 by default). Runtime changes via the
SpeedSheet go through `setPropertyFloat("speed", value)` instead.

### Film grain workaround

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:130
setSafeOptionString("vd-lavc-film-grain", "cpu")
```

Forces film-grain AV1 decoding to CPU (instead of GPU) — workaround for
mpv bug [#14651](https://github.com/mpv-player/mpv/issues/14651).

### Debanding

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:132-136
when (decoderPreferences.debanding.get()) {
    Debanding.None -> {}
    Debanding.CPU -> mpv.setOptionString("vf", "gradfun=radius=12")
    Debanding.GPU -> mpv.setOptionString("deband", "yes")
}
```

Two deband modes:
- **CPU**: applies `gradfun` video filter (CPU-based).
- **GPU**: enables mpv's built-in `deband` option (GPU shader).

Note: setting `vf` here **overrides** the `vf=format=yuv420p` set on
line 100 (whichever runs last wins). This is a minor bug-shaped quirk:
if both `useYUV420P` and `debanding == CPU` are on, only the deband
filter takes effect. Probably acceptable since both are uncommon to
enable together.

### Statistics overlay

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:138-143
advancedPreferences.playerStatisticsPage.get().let {
    if (it != 0) {
        mpv.command("script-binding", "stats/display-stats-toggle")
        mpv.command("script-binding", "stats/display-page-$it")
    }
}
```

If the user has selected a statistics page (1-5), the MPV stats overlay
is shown at startup.

## 5. Audio setup (init time)

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:180-201
private fun setupAudio() {
    mpv.setOptionString("alang", audioPreferences.preferredAudioLanguages.get())
    mpv.setOptionString("audio-delay", (audioPreferences.audioDelay.get() / 1000.0).toString())
    mpv.setOptionString("audio-pitch-correction", audioPreferences.enablePitchCorrection.get().toString())
    mpv.setOptionString("volume-max", (audioPreferences.volumeBoostCap.get() + 100).toString())

    audioPreferences.audioChannels.get().let {
        mpv.setPropertyString(it.property, it.value)
    }

    val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
        it.setAudioAttributes(
            AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
        )
        it.setOnAudioFocusChangeListener(this)
    }.build()
    AudioManagerCompat.requestAudioFocus(audioManager, request).let {
        if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
        audioFocusRequest = request
    }
}
```

| MPV option | Value |
|------------|-------|
| `alang` | User's preferred audio languages (comma-separated, e.g. `"eng,jpn"`). |
| `audio-delay` | Saved audio delay in seconds (ms / 1000). |
| `audio-pitch-correction` | Boolean; preserves pitch at non-1.0 speed. |
| `volume-max` | `boostCap + 100` (default 130, so volume can go to 130%). |
| `audio-channels` OR `af` | Depends on `AudioChannels` enum. `AutoSafe` → `audio-channels=auto`. `ReverseStereo` → `af=pan=[stereo|c0=c1|c1=c0]`. |

The `AudioManager.AUDIOFOCUS_GAIN` request is for system audio focus —
phone calls, other apps playing audio, etc. Pauses playback on
`AUDIOFOCUS_LOSS`, ducks to 50% volume on
`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`.

## 6. Subtitle setup (init time)

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:203-237
private fun setupSubtitlesOptions() {
    mpv.setOptionString("sub-delay", (subtitlePreferences.subtitlesDelay.get() / 1000.0).toString())
    mpv.setOptionString("sub-speed", subtitlePreferences.subtitlesSpeed.get().toString())
    mpv.setOptionString(
        "secondary-sub-delay",
        (subtitlePreferences.subtitlesSecondaryDelay.get() / 1000.0).toString(),
    )

    mpv.setOptionString("sub-font", subtitlePreferences.subtitleFont.get())
    subtitlePreferences.overrideSubsASS.get().let {
        mpv.setOptionString("sub-ass-override", it.value)
        if (it != SubtitleAssOverride.No) {
            mpv.setOptionString("sub-ass-justify", "yes")
        }
    }
    mpv.setOptionString("sub-font-size", subtitlePreferences.subtitleFontSize.get().toString())
    mpv.setOptionString("sub-bold", if (subtitlePreferences.boldSubtitles.get()) "yes" else "no")
    mpv.setOptionString("sub-italic", if (subtitlePreferences.italicSubtitles.get()) "yes" else "no")
    mpv.setOptionString("sub-justify", subtitlePreferences.subtitleJustification.get().value)
    mpv.setOptionString("sub-color", subtitlePreferences.textColorSubtitles.get().toColorHexString())
    mpv.setOptionString(
        "sub-back-color",
        subtitlePreferences.backgroundColorSubtitles.get().toColorHexString(),
    )
    mpv.setOptionString("sub-outline-color", subtitlePreferences.borderColorSubtitles.get().toColorHexString())
    mpv.setOptionString("sub-outline-size", subtitlePreferences.subtitleBorderSize.get().toString())
    mpv.setOptionString("sub-border-style", subtitlePreferences.borderStyleSubtitles.get().value)
    mpv.setOptionString("sub-shadow-offset", subtitlePreferences.shadowOffsetSubtitles.get().toString())
    mpv.setOptionString("sub-pos", subtitlePreferences.subtitlePos.get().toString())
    mpv.setOptionString("sub-scale", subtitlePreferences.subtitleFontScale.get().toString())

    val showBlackBars = if (subtitlePreferences.subtitleBlackBars.get()) "yes" else "no"
    mpv.setOptionString("sub-ass-force-margins", showBlackBars)
    mpv.setOptionString("sub-use-margins", showBlackBars)
}
```

Subtitle-related MPV options set at init:

| MPV option | Default | Purpose |
|------------|---------|---------|
| `sub-delay` | 0 (ms / 1000) | Primary subtitle delay in seconds. |
| `sub-speed` | 1.0 | Subtitle FPS multiplier (for non-1.0 video FPS). |
| `secondary-sub-delay` | 0 | Secondary subtitle delay (for two-subtitle display). |
| `sub-font` | "Sans Serif" | Default font family. |
| `sub-ass-override` | "no" | Whether to override ASS styles. `No`/`Yes`/`Scale`/`Force`/`Strip`. |
| `sub-ass-justify` | "yes" (if override != No) | Justify ASS subtitles. |
| `sub-font-size` | 55 | Font size in points. |
| `sub-bold` | "no" | Bold subtitles. |
| `sub-italic` | "no" | Italic subtitles. |
| `sub-justify` | "auto" | Subtitle alignment: `left`/`center`/`right`/`auto`. |
| `sub-color` | white (#FFFFFFFF) | Text color. |
| `sub-back-color` | transparent | Background color. |
| `sub-outline-color` | black | Border color. |
| `sub-outline-size` | 3 | Border thickness. |
| `sub-border-style` | "outline-and-shadow" | Border style enum. |
| `sub-shadow-offset` | 0 | Drop shadow distance. |
| `sub-pos` | 100 | Vertical position (0=top, 100=bottom). |
| `sub-scale` | 1.0 | Font scale multiplier. |
| `sub-ass-force-margins` | "no" | Force subtitles into black-bar area. |
| `sub-use-margins` | "no" | Use margin area for subtitles. |

> ANI-KUTA note from prior worklog (ANIMIRU-CLONE summary, point 5):
> `sub-ass-force-margins` is set as a **runtime property** in the new
> ANI-KUTA code but as an **init-time option** in Animiru. Animiru's
> approach is correct — these are init-time options because they're
> applied to the next file load, not the currently-playing one.

## 7. Properties observed

After options are set, `MPVPlayer.init` calls `addObserver(this)` and
`addLogObserver(this)`, then observes a specific set of properties:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:145-171
mpv.addObserver(this)
mpv.addLogObserver(this)

setupSubtitlesOptions()
setupAudio()

mapOf(
    "eof-reached" to MPV.mpvFormat.MPV_FORMAT_FLAG,

    "user-data/aniyomi/show_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/toggle_ui" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/show_panel" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/software_keyboard" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/set_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/reset_button_title" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/toggle_button" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/switch_episode" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/pause" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/seek_by" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/seek_to" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/seek_by_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/seek_to_with_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/launch_int_picker" to MPV.mpvFormat.MPV_FORMAT_STRING,
    "user-data/aniyomi/show_seek_text" to MPV.mpvFormat.MPV_FORMAT_STRING,
).forEach { (name, format) ->
    mpv.observeProperty(name, format)
}
```

Only two **non-Lua** properties are explicitly observed in `MPVPlayer`:
- `eof-reached` (boolean) — fires `Event.EOF`.

The rest are all `user-data/aniyomi/*` — the Lua-bridge properties. The
Lua script writes to these to invoke VM methods (see `02-player-architecture.md §12`).

### Where the rest of the property subscriptions live

The ViewModel subscribes to many more properties via
`viewModel.propFlow<T>(name)`. The `propFlow` extension internally calls
`mpv.observeProperty`. These subscriptions are in `PlayerViewModel.init`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:283-363
combine(
    propFlow<Double>("video-params/aspect"),
    propFlow<Int>("video-params/rotate"),
) { aspect, rotation -> aspect to rotation }
    .onEach { (aspect, rotation) -> _aspectRatio.update { ... } }
    .launchIn(viewModelScope)

propFlow<Int>("video-params/w")
    .filterNotNull()
    .onEach { v -> updateStateData { it.copy(videoWidth = v) } }
    .launchIn(viewModelScope)

propFlow<Int>("video-params/h")
    .filterNotNull()
    .onEach { v -> updateStateData { it.copy(videoHeight = v) } }
    .launchIn(viewModelScope)

propFlow<MPVNode>("track-list")
    .filterNotNull()
    .onEach { onTrackListChanged(it) }
    .launchIn(viewModelScope)

propFlow<MPVNode>("chapter-list")
    .filterNotNull()
    .onEach { onChapterListChanged(it) }
    .launchIn(viewModelScope)

propFlow<Int>("chapter")
    .onEach { onChapterChanged(it) }
    .launchIn(viewModelScope)

propFlow<Int>("duration")
    .filterNotNull()
    .onEach { v -> updatePlaybackData { it.copy(duration = v) } }
    .launchIn(viewModelScope)

propFlow<Int>("time-pos")
    .filterNotNull()
    .onEach { onSecondReached(it) }
    .launchIn(viewModelScope)

propFlow<Boolean>("pause")
    .filterNotNull()
    .onEach { v -> updatePlaybackData { it.copy(paused = v) } }
    .launchIn(viewModelScope)

propFlow<Int>("volume-max")
    .filterNotNull()
    .onEach { v -> updateStateData { it.copy(volumeBoostCap = v) } }
    .launchIn(viewModelScope)

propFlow<MPVNode>("sid")
    .onEach { onSubtitleTrackSelectChange() }
    .launchIn(viewModelScope)

propFlow<MPVNode>("secondary-sid")
    .onEach { onSubtitleTrackSelectChange() }
    .launchIn(viewModelScope)

propFlow<MPVNode>("aid")
    .onEach { onAudioTrackSelectChange() }
    .launchIn(viewModelScope)

propFlow<Long>("user-data/current-anime/intro-length")
    .filterNotNull()
    .onEach { setAnimeSkipIntroLength(it) }
    .launchIn(viewModelScope)
```

### PlayerScreen-side subscriptions

The `PlayerScreen` composable also subscribes to a bunch of properties
via `viewModel.propFlow<T>(...)`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerScreen.kt:86-92, 179-300
val mpvVolume        = viewModel.propFlow<Int>("volume")
val pausedForCache   = viewModel.propFlow<Boolean>("paused-for-cache")
val coreIdle         = viewModel.propFlow<Boolean>("core-idle")
val readAhead        = viewModel.propFlow<Float>("demuxer-cache-time")
val remaining        = viewModel.propFlow<Int>("playtime-remaining")
val playbackSpeed    = viewModel.propFlow<Float>("speed")
val currentChapter   = viewModel.propFlow<Int>("chapter")
val mpvDecoder       = viewModel.propFlow<String>("hwdec-current")
val mpvAudioPitchCorrection = viewModel.propFlow<Boolean>("audio-pitch-correction")
val subDelay         = viewModel.propFlow<Double>("sub-delay")
val subDelaySecondary = viewModel.propFlow<Double>("secondary-sub-delay")
val subSpeed         = viewModel.propFlow<Double>("sub-speed")
val audioDelay       = viewModel.propFlow<Double>("audio-delay")
val isBold           = viewModel.propFlow<Boolean>("sub-bold")
val isItalic         = viewModel.propFlow<Boolean>("sub-italic")
val subJustify       = viewModel.propFlow<String>("sub-justify")
val subFont          = viewModel.propFlow<String>("sub-font")
val subFontSize      = viewModel.propFlow<Int>("sub-font-size")
val subBorderStyle   = viewModel.propFlow<String>("sub-border-style")
val subBorderSize    = viewModel.propFlow<Int>("sub-outline-size")
val subShadowOffset  = viewModel.propFlow<Int>("sub-shadow-offset")
val subColor         = viewModel.propFlow<String>("sub-color")
val subBorderColor   = viewModel.propFlow<String>("sub-outline-color")
val subBackgroundColor = viewModel.propFlow<String>("sub-back-color")
val overrideAssSubs  = viewModel.propFlow<String>("sub-ass-override")
val subScale         = viewModel.propFlow<Float>("sub-scale")
val subPos           = viewModel.propFlow<Int>("sub-pos")
val mpvGpuNext       = viewModel.propFlow<String>("vo")
```

These are the **runtime-readable** MPV properties — changes to them via
the player UI (e.g. user moves a slider) call `setPropertyX` and the
property change comes back via the flow, updating the UI. This forms the
reactive loop.

## 8. The `MPVPlayer.Event` sealed interface

The `eventFlow` emits these:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:404-410
sealed interface Event {
    data object FileLoaded : Event
    data class EOF(val value: Boolean) : Event
    data class TrackLoadFailure(val url: String) : Event
    data class EndFile(val node: MPVNode) : Event
    data class LuaEvent(val property: String, val value: String) : Event
}
```

How each is produced:

- **`FileLoaded`** — `MPV_EVENT_FILE_LOADED` (mpv fired when the file is
  loaded and metadata/tracks are available). The VM uses this to:
  `setMpvOptions`, `setMpvMediaTitle`, `setupChapters`,
  `setupPlayerOrientation`, `checkFileLoaded`, and trigger AniSkip lookup.

- **`EOF(value)`** — `eof-reached` property change. If `value == true` and
  `autoPlayEnabled`, advances to next episode.

- **`TrackLoadFailure(url)`** — parsed from mpv log: when an external
  subtitle/audio file fails to load, mpv logs
  `"Can not open external file <url>."` at ERROR level. The MPV log
  observer strips the prefix and emits this event. The VM marks the
  external track as `TrackState.Error`.

- **`EndFile(node)`** — `MPV_EVENT_END_FILE`. The node contains a
  `file_error` field if the file ended due to error. The VM extracts the
  error message, appends any HTTP error captured separately, shows a
  toast, marks the video as `Video.State.ERROR`, and either switches to
  the next-best video (if `switchOnFailure`) or sets `isStopped = true`.

- **`LuaEvent(property, value)`** — any `user-data/aniyomi/*` property
  change. Dispatched via `handleLuaInvocation` (see
  `02-player-architecture.md §12`).

## 9. The MPV log observer

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:292-308
override fun logMessage(prefix: String, level: Int, text: String) {
    if (level == MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
        if (text.startsWith(TRACK_LOAD_FAILURE)) {
            val url = text.removePrefix(TRACK_LOAD_FAILURE).substringBeforeLast(".")
            _eventFlow.tryEmit(Event.TrackLoadFailure(url))
        }
    }

    val logPriority = when (level) {
        MPV.mpvLogLevel.MPV_LOG_LEVEL_FATAL, MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> LogPriority.ERROR
        MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> LogPriority.WARN
        MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> LogPriority.INFO
        else -> LogPriority.VERBOSE
    }
    if (text.contains("HTTP error")) httpError = text.removePrefix("http: ")
    logcat("$TAG/$prefix", logPriority) { text }
}
```

Two side effects of the log observer:
1. **Track load failure detection** — watches for the
   `"Can not open external file "` prefix in ERROR logs.
2. **HTTP error capture** — watches for `"HTTP error"` in log text, stashes
   the message in `httpError`. The `endFile` handler appends this to the
   toast so the user sees why a video failed.

The `TAG` is `"mpv"` (`MPVPlayer.kt:413`). All log lines are tagged
`mpv/<prefix>` where prefix is mpv's internal module name (e.g.
`mpv/ffmpeg`, `mpv/demuxer`, etc.).

## 10. Sequence diagram — init flow

```
App.onCreate (process start)
    │
    ▼
MpvConfig.copyFiles()              (async, IO dispatcher)
    ├─ copyUserFiles (scripts/, script-opts/, shaders/)
    ├─ copyFontsDirectory (fonts/)
    ├─ copyAssets (cacert.pem)
    └─ writeFontsConf (fonts.conf)
    │
    ▼ (later, when user taps episode)
PlayerActivity.onCreate
    │
    ▼
PlayerViewModel.<init>
    │  videoOutput = if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu"
    │  player = MPVPlayer(context, videoOutput)
    │         │
    │         ▼
    │     MPVPlayer.<init>
    │         ├─ create mpvDir = filesDir/mpv/
    │         ├─ write mpv.conf from advancedPreferences.mpvConf
    │         ├─ write input.conf from advancedPreferences.mpvInput
    │         ├─ mpv = MPV(context) { setOptionString("config","yes"); ... }
    │         ├─ parse mpv.conf for option names → setSafeOptionString helper
    │         ├─ setOptionString("vo", videoOutput)
    │         ├─ setSafeOptionString("profile", "fast")
    │         ├─ setOptionString("hwdec", ...)
    │         ├─ setOptionString("msg-level", ...)
    │         ├─ setOptionString("idle", "yes")
    │         ├─ setOptionString("ytdl", "no")
    │         ├─ setSafeOptionString("tls-verify", "yes")
    │         ├─ setSafeOptionString("tls-ca-file", "<filesDir>/mpv/cacert.pem")
    │         ├─ setOptionString("sid", "no"); setOptionString("aid", "no")
    │         ├─ setSafeOptionString("demuxer-max-bytes", "...")
    │         ├─ setSafeOptionString("demuxer-max-back-bytes", "...")
    │         ├─ setOptionString("screenshot-directory", "<Pictures>/")
    │         ├─ for each VideoFilters: setOptionString(filter.mpvProperty, value)
    │         ├─ setOptionString("speed", playerSpeed)
    │         ├─ setSafeOptionString("vd-lavc-film-grain", "cpu")
    │         ├─ when debanding: setOptionString("vf"/"deband", ...)
    │         ├─ if statisticsPage != 0: command("script-binding", "stats/display-stats-toggle")
    │         ├─ mpv.addObserver(this); mpv.addLogObserver(this)
    │         ├─ setupSubtitlesOptions()  (sets ~20 sub-* options)
    │         ├─ setupAudio()  (alang, audio-delay, volume-max, audio-channels, audio focus)
    │         └─ observeProperty for eof-reached + 15 user-data/aniyomi/* properties
    │
    ▼
viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
    │  (loads anime/episode/source, sets up playlist, fetches hosters)
    │
    ▼
viewModel.loadHosters(hosterList, hostIndex, videoIndex)
    │  (async per-hoster fetch; for each Ready hoster: loadVideo)
    │
    ▼
viewModel.loadVideo(video, hosterIdx, videoIdx)
    │  ├─ setHttpOptions(video)   ← http-header-fields
    │  ├─ set "start" position
    │  └─ mpv.command("loadfile", url, "replace", "0", videoOptions)
    │
    ▼
MPV_EVENT_FILE_LOADED fires
    │
    ▼
MPVPlayer.event(MPV_EVENT_FILE_LOADED, data)
    │  handler.post { _eventFlow.tryEmit(Event.FileLoaded) }
    │
    ▼
PlayerViewModel.handlePlayerFlow(Event.FileLoaded)
    ├─ setMpvOptions (reads metadata for MPV_ARGS_TAG)
    ├─ setMpvMediaTitle (writes force-media-title)
    ├─ setupChapters (merges AniSkip + ext-lib timestamps)
    ├─ setupPlayerOrientation
    ├─ checkFileLoaded (waits for hasLoadedSubs && hasLoadedAudio)
    └─ aniSkipResponse → addTimeStamps
```

## 11. Quirks + warnings

1. **`vf` option collisions** — `vf` is set in multiple places (line 100
   for `format=yuv420p`, line 134 for `gradfun`). The last call wins.
   This is a latent bug if both `useYUV420P` and `Debanding.CPU` are on.

2. **`sub-ass-force-margins` as init-time option** — must be init time,
   not runtime. ANI-KUTA's prior attempt set it at runtime which produces
   wrong subtitle rendering (prior worklog note).

3. **`http-header-fields` is a startup option** — it MUST be set via
   `setOptionString` BEFORE `loadfile`. Setting it as a property doesn't
   work for the next file. Animiru does this correctly
   (`PlayerViewModel.kt:1089`).

4. **`start` position is set via `set` command, not option** —
   `mpvCommand("set", "start", value)` is set before `loadfile`. This is
   a mpv quirk: setting `--start=` as an option in the `loadfile` 5th
   argument also works, but `set` is more readable and survives across
   `loadfile replace` calls.

5. **The `tls-ca-file` is a Mozilla bundle** — shipped as
   `app/src/main/assets/cacert.pem`. This bundle must be kept up to date
   (Mozilla rotates CAs periodically). Animiru does not auto-update it.

6. **`config-dir` is `filesDir/mpv/`** — this directory also stores
   `mpv.conf`, `input.conf`, `fonts.conf`, `cacert.pem`, `scripts/`,
   `script-opts/`, `shaders/`, `fonts/`. A user clearing app data wipes
   everything; a user clearing app *cache* (via system settings) only
   wipes `cacheDir` (shader cache + icc cache + fontconfig cache).

7. **`keep-open=yes` is hardcoded** — when a file ends, mpv doesn't
   destroy itself; it stays open at the last frame. This is what enables
   the EOF → autoplay-next-episode flow without Activity recreation.

8. **`ytdl=no` is hardcoded** — Animiru disables youtube-dl integration.
   All URL resolution must happen via the extension source.

9. **`gpu-next` vs `gpu`** — `gpu-next` is mpv's modern VO based on
   `ra ` (rendering abstraction). It's opt-in via
   `decoderPreferences.gpuNext`. The `vo` is also re-set in
   `MpvSurface.surfaceCreated` (`mpv.setPropertyString("vo", videoOutput)`)
   because the VO must be re-attached after surface lifecycle events.
