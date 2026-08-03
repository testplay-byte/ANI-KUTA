# 10 — Key Takeaways for ANI-KUTA

> Patterns Animiru does better than the current ANI-KUTA, patterns
> Animiru does differently, specific code worth porting (with file
> paths + line numbers), and warnings about anti-patterns.

## A. Things Animiru does BETTER than current ANI-KUTA

> Based on the ANIMIRU-CLONE worklog summary and the analysis in
> docs 01-09.

### A1. Three-bucket player state (vs single blob)

**Animiru:** Splits player state into three `StateFlow`s:
- `stateData: PlayerStateData` — what's playing (stable).
- `uiData: PlayerUiData` — what's shown (medium-frequency).
- `playbackData: PlayerPlaybackData` — fast-changing (position, paused).

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:2803-2890`.

**ANI-KUTA's prior issue:** `PlayerStateHolder` was a single state blob
that recomposed the entire player UI on every position tick.

**Action:** Port the three-bucket split. Position updates should only
recompose the seekbar and time labels, not the controls overlay.

### A2. Typed `propFlow<T>(name)` over MPV properties

**Animiru:** The `MPV` class exposes `propFlow<T>(name): StateFlow<T?>`,
re-exposed as `viewModel.propFlow<T>(name)`. Composables subscribe
individually:
```kotlin
// PlayerScreen.kt:86-92
val mpvVolume by viewModel.propFlow<Int>("volume").collectAsStateWithLifecycle()
val pausedForCache by viewModel.propFlow<Boolean>("paused-for-cache").collectAsStateWithLifecycle()
```

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:386-388`.

**Action:** Implement `propFlow<T>` on ANI-KUTA's MPV wrapper. Each
Composable should subscribe only to the properties it renders. This
dramatically reduces recomposition scope.

### A3. Sheet / Panel / Dialog mutual exclusivity

**Animiru:** Three enums (`Sheets`, `Panels`, `Dialogs`) on `uiData`,
with `setSheet`/`setPanel`/`setDialog` enforcing mutual exclusivity
(setting one clears the others).

Source: `PlayerViewModel.kt:1830-1874`.

**ANI-KUTA's prior issue:** QualitySheet was the only sheet.

**Action:** Port the full set of 7 sheets + 4 panels + 2 dialogs. The
mutual-exclusivity pattern prevents UI jank from overlapping modals.

### A4. QualitySheet with server accordion + lazy hosters

**Animiru:** QualitySheet renders hosters as an accordion. Lazy hosters
(`hoster.lazy = true`) show "Tap to load" and only fetch their video
list when the user expands them. Per-video state (`QUEUE` / `LOAD_VIDEO`
/ `READY` / `ERROR`) shows spinners and error icons inline.

Source:
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt:53-75` (HosterState)
- `PlayerViewModel.kt:1165-1194` (onHosterClicked)

**ANI-KUTA's prior issue:** Per the ANIMIRU-CLONE worklog: "QualitySheet
needs 3-tier hierarchy (Server→Audio→Video) — new resolver only has
flat list."

**Action:** Port the `HosterState` sealed class and the accordion UI.
Lazy hosters are a major UX win for sources with many mirrors.

### A5. SubtitleSettingsSheet + SubtitleDelayPanel + AudioDelayPanel + VideoFiltersPanel

**Animiru:** Full subtitle customization (font, size, color, border,
position, delay, ASS override) via the SubtitleSettingsPanel.
Per-color-type tabs (Text/Border/Background) in the colors card.

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/panels/SubtitleSettingsPanel.kt:29-118`.

**ANI-KUTA's prior issue:** Per ANIMIRU-CLONE worklog:
"SubtitleSettingsSheet + ColorPickerSheet + NumericEntrySheet not ported."

**Action:** Port the full subtitle settings panel. The 3-card layout
(Typography / Colors / Miscellaneous) is clean and worth copying.

### A6. NumericEntrySheet (IntegerPickerDialog)

**Animiru:** A wheel-style picker for choosing integers in a range,
opened via Lua bridge. Source:
`app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/dialogs/IntegerPickerDialog.kt:11-46`.

**Action:** Port the IntegerPickerDialog. Even without the Lua bridge,
it's useful for any "pick a number" UI (e.g. skip-intro length, sleep
timer minutes).

### A7. Picture-in-Picture with full action set

**Animiru:** PiP with three actions (play/pause + skip/previous + next),
source-rect hint for smooth transition, aspect-ratio-aware sizing, and
auto-enter on home button.

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:390-426` (createPipParams).

**Action:** Port the full PiP implementation. The `pipReplaceWithPrevious`
preference (swap skip for previous-episode) is a nice touch.

### A8. `configChanges` includes `uiMode`

**Animiru:** `PlayerActivity` manifest declares
`android:configChanges="orientation|screenLayout|screenSize|smallestScreenSize|keyboardHidden|keyboard|uiMode"`.

Source: `app/src/main/AndroidManifest.xml:143`.

**ANI-KUTA's prior issue:** Per ANIMIRU-CLONE worklog point 10:
"configChanges missing uiMode — theme toggle recreates Activity in new
project."

**Action:** Add `uiMode` to ANI-KUTA's PlayerActivity configChanges.
Otherwise theme toggles destroy the player mid-playback.

### A9. `sub-ass-force-margins` set at init time (not runtime)

**Animiru:** Set via `mpv.setOptionString("sub-ass-force-margins", ...)`
in `MPVPlayer.setupSubtitlesOptions()` — applied at init, affects next
`loadfile`.

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:234-236`.

**ANI-KUTA's prior issue:** Per ANIMIRU-CLONE worklog point 5:
"sub-ass-force-margins set as runtime property (new) vs init-time
option (old) — subtitle rendering may be wrong."

**Action:** Set `sub-ass-force-margins` (and `sub-use-margins`) at init
time via `setOptionString`, not at runtime via `setPropertyString`.

### A10. `cache=yes` explicit in mpv.conf

**Animiru:** The default `mpv.conf` content (when user hasn't set one)
includes `cache=yes`. The user's mpv.conf overrides via `setSafeOptionString`.

**ANI-KUTA's prior issue:** Per ANIMIRU-CLONE worklog point 4:
"cache=yes in new mpv.conf vs absent in old — old relies on
demuxer-max-bytes only."

**Action:** Explicitly set `cache=yes` at init (or in the default
mpv.conf). Combined with `demuxer-max-bytes`, this gives proper
read-ahead buffering.

### A11. Lua bridge for custom buttons

**Animiru:** A Lua script (`aniyomi.lua`) bridges MPV's Lua environment
back to Kotlin via `user-data/aniyomi/*` properties. Custom buttons are
Lua functions registered via `mp.register_script_message`.

Source: `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1979-2095` (handleLuaInvocation).

**Action:** This is complex. ANI-KUTA may skip it initially. If custom
buttons become a feature, this is the pattern to study.

## B. Things Animiru does DIFFERENTLY (not necessarily better)

### B1. Injekt + SharedPreferences (vs Hilt + DataStore)

**Animiru:** Injekt for DI, `PreferenceStore` (SharedPreferences wrapper)
for prefs.

**ANI-KUTA:** Hilt + DataStore (per `CORE_RULES.md`).

**Action:** Don't port Injekt. Port the **structure** of the preference
classes (`PlayerPreferences`, `AudioPreferences`, etc.) but back them
with DataStore. Each `Preference<T>` becomes a DataStore entry.

### B2. Voyager for navigation (player uses plain Activity)

**Animiru:** Uses Voyager for most screens, but `PlayerActivity` is a
plain `BaseActivity` with `setContent { TachiyomiTheme { PlayerScreen(...) } }`.

Source: `PlayerActivity.kt:249-275`.

**Action:** This is fine — ANI-KUTA can use the same pattern (plain
Activity for the player, Voyager/Navigation Compose for the rest).

### B3. Compose ConstraintLayout for controls overlay

**Animiru:** `PlayerControls` uses `ConstraintLayout` from
`androidx.constraintlayout.compose` for the 9-region layout.

Source: `PlayerControls.kt:78-90`.

**Alternative:** ANI-KUTA could use `Box` with manual alignments, but
ConstraintLayout's `constrainAs` is cleaner for anchored regions.
Recommend porting.

### B4. `seeker` library for the seekbar

**Animiru:** Uses `io.github.2307vivek:seeker` for the Seeker composable,
which supports chapters as colored segments and read-ahead buffering
indicator.

Source: `SeekBar.kt:94-117`.

**Action:** Port the `seeker` dependency. It's a small, well-maintained
library that solves a real problem (chapter segments on the seekbar).

### B5. Separate `:source-api` module

**Animiru:** Extension API (`AnimeSource`, `AnimeHttpSource`, `Hoster`,
`Video`, etc.) lives in `:source-api`, a KMP module. The `:app` module
depends on it.

Source: `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/`.

**Action:** ANI-KUTA should have a similar split. The extension API
must be consumable as a separate artifact (for extension developers to
compile against).

## C. Specific code patterns worth porting (with file paths + line numbers)

### C1. MpvSurface — the entire surface lifecycle (51 lines)

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/components/MpvSurface.kt:1-51`

This is the single most directly-portable file. The entire
SurfaceView ↔ mpv.attachSurface/detachSurface lifecycle is here. Copy
verbatim (adjusting imports).

### C2. MPVPlayer init pipeline

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:67-172`

The init block that:
- Writes mpv.conf + input.conf to filesDir/mpv/.
- Creates MPV(context) with config-dir.
- Parses user's mpv.conf to skip already-set options (`setSafeOptionString`).
- Sets ~30 options.
- Observes eof-reached + 15 Lua-bridge properties.

Port this with adjustments for ANI-KUTA's preference classes.

### C3. The `setSafeOptionString` helper

**File:** `MPVPlayer.kt:85-94`

```kotlin
val optionNameRegex = Regex("""^(?:--)?([\w-]+)(?:=|$)""", RegexOption.MULTILINE)
val mpvOptionNames = optionNameRegex.findAll(advancedPreferences.mpvConf.get()).map {
    it.groupValues[1].removePrefix("no-")
}.toSet()

fun setSafeOptionString(name: String, value: String) {
    if (name in mpvOptionNames) return
    mpv.setOptionString(name, value)
}
```

This lets the user's mpv.conf override hardcoded defaults. Worth porting.

### C4. The video loading pipeline (loadHosters + loadVideo + setVideo)

**File:** `PlayerViewModel.kt:840-1069`

The full pipeline from `hosterList` to `mpv.command("loadfile", ...)`.
Includes:
- Concurrent hoster fetch with `coroutineScope { async { ... }.awaitAll() }`.
- AtomicBoolean for "found preferred video" coordination.
- Recursive `loadVideo` fallback to next-best video.
- `setHttpOptions` (header CSV building).
- `setVideo` (start position + loadfile command).

Port with adjustments for ANI-KUTA's data layer.

### C5. Track selection logic (selectSubById with secondary-sid)

**File:** `PlayerViewModel.kt:1520-1531`

```kotlin
private fun selectSubById(id: Int) {
    val selectedSubs = Pair(mpv.getPropertyInt("sid"), mpv.getPropertyInt("secondary-sid"))
    when (id) {
        selectedSubs.first -> Pair(selectedSubs.second, null)
        selectedSubs.second -> Pair(selectedSubs.first, null)
        else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
    }.let {
        it.second?.let { setPropertyInt("secondary-sid", it) }
            ?: setPropertyBoolean("secondary-sid", false)
        it.first?.let { setPropertyInt("sid", it) } ?: setPropertyBoolean("sid", false)
    }
}
```

This enables two-subtitle display. Port verbatim.

### C6. HosterState sealed class + getChangedAt helper

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/sheets/QualitySheet.kt:53-75`

```kotlin
@Stable
sealed class HosterState(open val name: String) {
    data class Idle(override val name: String) : HosterState(name)
    data class Loading(override val name: String) : HosterState(name)
    data class Error(override val name: String) : HosterState(name)
    data class Ready(
        override val name: String,
        val videoList: List<Video>,
        val videoState: List<Video.State>,
    ) : HosterState(name)
}

fun HosterState.Ready.getChangedAt(index: Int, newVideo: Video, newState: Video.State): HosterState.Ready {
    return HosterState.Ready(
        name = this.name,
        videoList = this.videoList.mapIndexed { idx, video -> if (idx == index) newVideo else video },
        videoState = this.videoState.mapIndexed { idx, state -> if (idx == index) newState else state },
    )
}
```

The `getChangedAt` helper creates an immutable copy with one video's
state changed — essential for Compose state updates. Port verbatim.

### C7. PiP params builder

**File:** `PlayerActivity.kt:390-426`

The `createPipParams()` function that:
- Sets title/subtitle on Android 13+.
- Sets auto-enter + seamless resize on Android 12+.
- Builds three RemoteActions (play/pause + skip/prev + next).
- Sets source-rect hint from the player's bounds.
- Computes aspect ratio from video dimensions, clamped to PiP limits.

Port with adjustments for ANI-KUTA's action constants.

### C8. MpvConfig asset-copy pipeline

**File:** `app/src/main/java/animiru/feature/mpvfiles/MpvConfig.kt:32-42`

The `copyFiles()` function that runs at app start to copy:
- User scripts / script-opts / shaders from SAF.
- The bundled `aniyomi.lua` asset.
- User fonts.
- `cacert.pem` asset.
- Generated `fonts.conf`.

Port with adjustments for ANI-KUTA's storage locations.

### C9. fonts.conf generation

**File:** `MpvConfig.kt:145-180`

The `writeFontsConf` function that generates a fontconfig config
pointing at:
- `/system/fonts/` and `/product/fonts/` (Android system fonts).
- The user's font directory.
- The cache directory.
- Aliases for `serif`/`Sans Serif`/`monospace`.

Port verbatim — this is necessary for subtitle font rendering.

### C10. ChapterList merge (ChapterUtils.mergeChapters)

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/ChapterUtils.kt:23-105`

Merges AniSkip timestamps with MPV's embedded chapter list. Handles:
- Sorting by start time.
- Filling gaps between timestamps with empty chapters.
- Deduplication (within 1 second tolerance).
- Coloring Opening/Ending/Recap/MixedOp chapters differently.

Port if AniSkip integration is in scope.

## D. Anti-patterns and warnings

### D1. `runBlocking` in extension loading

**File:** `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:166-172`

```kotlin
return runBlocking {
    val deferred = extPkgs.map {
        async { loadExtension(context, it) }
    }
    deferred.awaitAll()
}
```

`runBlocking` on the main thread during app startup can cause ANR if
there are many extensions. ANI-KUTA should use a suspending init
pattern instead.

### D2. Single giant PlayerViewModel (2928 lines)

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`

The VM handles: state management, MPV event dispatch, hoster loading,
video loading, track selection, seeking, gestures, screenshots, AniSkip,
sleep timer, episode progress, download-ahead, custom buttons, and Lua
bridge. This is a God Object.

**Action for ANI-KUTA:** Split into multiple VMs or delegate to use
cases:
- `PlayerStateViewModel` — stateData, uiData, playbackData.
- `PlayerTrackUseCase` — track selection logic.
- `PlayerHosterUseCase` — hoster/video loading.
- `PlayerSeekUseCase` — seeking + gestures.
- `PlayerScreenshotUseCase` — screenshot save/share.
- `PlayerAniSkipUseCase` — AniSkip integration.

### D3. `vf` option collision (yuv420p vs deband)

**File:** `MPVPlayer.kt:99-136`

`vf` is set in two places:
1. Line 100: `mpv.setOptionString("vf", "format=yuv420p")` (if useYUV420P).
2. Line 134: `mpv.setOptionString("vf", "gradfun=radius=12")` (if Debanding.CPU).

The second call overwrites the first. If both prefs are on, only the
deband filter applies. This is a latent bug.

**Action for ANI-KUTA:** Combine filters into a single `vf` string:
`"vf=gradfun=radius=12,format=yuv420p"` (or whichever order makes
sense).

### D4. FD leak risk in openContentFd

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt:35-41`

```kotlin
internal fun Uri.openContentFd(context: Context): String? {
    return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {
        Utils.findRealPath(it)?.also { _ ->
            ParcelFileDescriptor.adoptFd(it).close()
        } ?: "fd://$it"
    }
}
```

If `Utils.findRealPath` fails, the FD is returned as `"fd://$it"` and
**not** closed by Animiru. If mpv fails to load the file, the FD leaks.

**Action for ANI-KUTA:** Add a fallback that closes the FD if mpv
reports a load failure for an `fd://` URL. Or use a different SAF
bridge (e.g. content-URI proxy server).

### D5. `subtitleBlackBars` runtime behavior

**File:** `MPVPlayer.kt:234-236`

`sub-ass-force-margins` and `sub-use-margins` are set at init time. If
the user toggles `subtitleBlackBars` in the SubtitleSettingsPanel, the
change doesn't take effect until the next video loads. This is a mpv
limitation, but the UI doesn't communicate it.

**Action for ANI-KUTA:** Show a hint in the SubtitleSettingsPanel when
the user changes an init-time option: "Takes effect on next video."

### D6. Reflection-based ext-lib version detection

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt:63-81`

```kotlin
private fun checkHasHosters(source: AnimeHttpSource): Boolean {
    var current: Class<in AnimeHttpSource> = source.javaClass
    while (true) {
        if (current == ParsedAnimeHttpSource::class.java || ...) return false
        if (current.declaredMethods.any { it.name in listOf("getHosterList", ...) }) return true
        current = current.superclass ?: return false
    }
}
```

Walking the class hierarchy looking for method names is brittle. If an
extension renames `getHosterList`, the check fails.

**Action for ANI-KUTA:** When ANI-KUTA drops ext-lib <1.6 support,
remove this check entirely. Until then, port it as-is but add a
comment explaining the brittleness.

### D7. `EarlyReturnException` for short-circuiting coroutines

**File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt:69, 147-149`

```kotlin
class EarlyReturnException(val video: Video) : Exception()

// ... in getBestVideo:
coroutineContext.cancelChildren()
throw EarlyReturnException(resolvedVideo)

// ... at the bottom:
} catch (e: EarlyReturnException) {
    e.video
}
```

Using exceptions for control flow is an anti-pattern. A cleaner
approach uses `select` on the deferreds or a `CompletableDeferred`.

**Action for ANI-KUTA:** Refactor to use `select` or a shared
`CompletableDeferred<Video>`.

### D8. `http-header-fields` is set as option (not property)

**File:** `PlayerViewModel.kt:1089`

```kotlin
mpv.setOptionString("http-header-fields", httpHeaderString)
```

This is correct (http-header-fields is a startup option), but it means
the headers are set globally for the next `loadfile`. If two videos
load in quick succession (e.g. autoplay), the headers from the first
could leak to the second if `setHttpOptions` isn't called for the
second.

**Action for ANI-KUTA:** Always call `setHttpOptions(video)` before
every `loadfile`, even for autoplay transitions. Animiru does this
correctly in `setVideo`, but it's worth being explicit.

### D9. `iconMap` thread safety

**File:** `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:63`

```kotlin
private val iconMap = mutableMapOf<String, Drawable>()
```

`mutableMapOf` is not thread-safe. Icons are loaded lazily via
`loadIcon` (main thread) but the map is read from multiple coroutines.
For a small number of extensions this is fine; for many it could
cause `ConcurrentModificationException`.

**Action for ANI-KUTA:** Use `ConcurrentHashMap` or synchronize access.

### D10. No subtitle encoding detection

**Animiru:** Does not set `--sub-codepage`. Non-UTF-8 subtitle files
may render as garbage.

**Action for ANI-KUTA:** Set `sub-codepage=auto` at init (requires
libuchardet compiled into mpv). Or expose a per-file encoding picker
in the SubtitleTracksSheet.

### D11. `loadNsfwSource` read once at init

**File:** `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:46-48`

```kotlin
private val loadNsfwSource by lazy {
    preferences.showNsfwSource.get()
}
```

`lazy` means it's read once on first access and never updated. Changing
the `showNsfwSource` preference requires an app restart to take
effect.

**Action for ANI-KUTA:** Read this preference live (or re-read on
preference change). NSFW source visibility should toggle without
restart.

## E. Summary — what ANI-KUTA should do

1. **Port the MpvSurface component verbatim** (51 lines).
2. **Port the MPVPlayer init pipeline** with adjustments for ANI-KUTA's
   preference classes (DataStore-backed).
3. **Adopt the three-bucket state pattern** (stateData / uiData / playbackData).
4. **Implement `propFlow<T>(name)`** on the MPV wrapper for fine-grained
   Compose subscriptions.
5. **Port the full sheet/panel/dialog system** with mutual exclusivity.
6. **Port the QualitySheet accordion + lazy hosters**.
7. **Port the SubtitleSettingsPanel** with the 3-card layout.
8. **Port the PiP implementation** with all three actions.
9. **Add `uiMode` to configChanges** in the manifest.
10. **Set `sub-ass-force-margins` and `cache=yes` at init time.**
11. **Use the `seeker` library** for the seekbar (chapter segments).
12. **Split the PlayerViewModel** into smaller use cases — don't repeat
    the 2928-line God Object.
13. **Fix the `vf` option collision** (combine filters).
14. **Don't use `runBlocking`** for extension loading — use suspending init.
15. **Don't use exceptions for control flow** — refactor
    `EarlyReturnException` to `select` or `CompletableDeferred`.

## F. What to skip (not needed for ANI-KUTA)

- **Lua bridge (`aniyomi.lua` + custom buttons)** — complex, low value
  for initial release. Add later if there's demand.
- **AniSkip integration** — external API dependency. Add later if
  product wants it.
- **Discord RPC** — Animiru has it commented out (`// AM (DISCORD_RPC) -->`).
  Skip.
- **Google Drive sync** — Animiru has it (`// AM (SYNC_DRIVE) -->`). Skip
  unless ANI-KUTA wants cloud sync.
- **Shizuku installer** — port later. The PRIVATE installer is
  sufficient for initial release.

## G. Quick reference — file path → ANI-KUTA action

| Animiru file | ANI-KUTA action |
|--------------|-----------------|
| `components/MpvSurface.kt` | Port verbatim |
| `mpv/MPVPlayer.kt` (init) | Port with pref-class adjustments |
| `mpv/MPVModels.kt` (TrackNode, VideoTrack) | Port verbatim |
| `animiru/feature/mpvfiles/MpvConfig.kt` | Port (asset copy pipeline) |
| `PlayerViewModel.kt` (state classes) | Port the 3-bucket split |
| `PlayerViewModel.kt` (loadHosters/loadVideo/setVideo) | Port with data-layer adjustments |
| `PlayerViewModel.kt` (selectSubById) | Port verbatim |
| `PlayerActivity.kt` (PiP) | Port with action-constant adjustments |
| `PlayerActivity.kt` (onKeyDown) | Port with key-mapping adjustments |
| `PipActions.kt` | Port verbatim |
| `controls/PlayerControls.kt` (ConstraintLayout) | Port |
| `controls/components/SeekBar.kt` (SeekbarWithTimers) | Port (uses `seeker` lib) |
| `controls/GestureHandler.kt` | Port with gesture-pref adjustments |
| `controls/PlayerSheets.kt` | Port (dispatcher) |
| `controls/PlayerPanels.kt` | Port (dispatcher) |
| `controls/PlayerDialogs.kt` | Port (dispatcher) |
| `controls/components/sheets/QualitySheet.kt` (HosterState) | Port verbatim |
| `controls/components/sheets/SubtitleTracksSheet.kt` | Port |
| `controls/components/sheets/AudioTracksSheet.kt` | Port |
| `controls/components/sheets/PlaybackSpeedSheet.kt` | Port |
| `controls/components/sheets/MoreSheet.kt` | Port |
| `controls/components/sheets/ChaptersSheet.kt` | Port |
| `controls/components/sheets/ScreenshotSheet.kt` | Port |
| `controls/components/panels/SubtitleSettingsPanel.kt` | Port |
| `controls/components/panels/SubtitleDelayPanel.kt` | Port |
| `controls/components/panels/AudioDelayPanel.kt` | Port |
| `controls/components/panels/VideoSettingsPanel.kt` | Port |
| `controls/components/dialogs/IntegerPickerDialog.kt` | Port |
| `loader/HosterLoader.kt` (selectBestVideo, getResolvedVideo) | Port (refactor EarlyReturnException) |
| `loader/EpisodeLoader.kt` | Port (drop ext-lib <1.6 when ready) |
| `domain/TrackSelect.kt` | Port |
| `domain/BrightnessManager.kt` | Port |
| `domain/AudioManager.kt` | Port |
| `utils/ChapterUtils.kt` (mergeChapters) | Port if AniSkip in scope |
| `settings/*Preferences.kt` | Port structure, back with DataStore |
| `PlayerEnums.kt` | Port |
| `PlayerUtils.kt` (openContentFd, resolveUri) | Port (fix FD leak) |
| `source-api/.../model/Hoster.kt` | Port verbatim |
| `source-api/.../model/Video.kt` | Port verbatim |
| `source-api/.../online/AnimeHttpSource.kt` | Port (ext-lib 16 API) |
| `extension/ExtensionManager.kt` | Port with Hilt adjustments |
| `extension/util/ExtensionLoader.kt` | Port (drop runBlocking) |
| `extension/util/ExtensionInstaller.kt` | Port |
| `extension/installer/Installer.kt` | Port |
| `extension/api/ExtensionApi.kt` | Port |
