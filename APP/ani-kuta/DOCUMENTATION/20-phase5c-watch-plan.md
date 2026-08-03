# Phase 5c — Watch Screen Plan

> Plan for the Watch screen (video player). Written per user request:
> "plan out the next phase properly too — phase 5c, the watch page, the watch
> page's fullscreen view and other view, the overall design of the watch page."
>
> **Prerequisite:** Phase 5B's episode → resolver → watch flow is functional
> (the user can reach the WatchKey placeholder). Phase 5c replaces the
> placeholder with the actual MPV player.

---

## 1. Goal

**Build a functional watch screen with the MPV player** that plays the resolved
video URL, with controls, seek, resume, and episode navigation. The user must
be able to: open the watch page → video plays → seek/pause → see controls →
go back.

---

## 2. Two View Modes (per user spec)

### 2.1 Fullscreen Mode (landscape)
- **Player**: fills the entire screen (edge-to-edge, no system bars).
- **Controls overlay**: auto-hides after 4 seconds.
  - Top bar: back button, anime title, episode info ("EP 5 - Title"), quality info.
  - Center: large play/pause button, buffering spinner.
  - Bottom: seek bar + time display + button row:
    - Skip back (-10s), Skip forward (+10s)
    - Subtitle button (opens track picker)
    - Quality button (re-opens resolver)
    - Speed button (opens speed sheet)
    - Next episode (if available)
  - Lock button (locks controls — only unlock button shown).
- **Orientation**: `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`.
- **System bars**: hidden (`WindowInsetsControllerCompat.hide`).
- **Back gesture**: goes to minimized mode (NOT exit).

### 2.2 Minimized Mode (portrait)
- **Player**: 16:9 box at the top (rounded corners).
- **Below**: episode list + description card (scrollable).
- **Controls**: minimal — play/pause on tap, small seek bar, maximize button.
- **Orientation**: `SCREEN_ORIENTATION_SENSOR_PORTRAIT`.
- **System bars**: visible.
- **Back gesture**: exits to Details page.

---

## 3. File Structure (split per D-038)

The old project's WatchScreen.kt is 2386 LOC. Split into:

| File | LOC est. | Purpose |
|------|----------|---------|
| `WatchScreen.kt` | ~400 | Scaffold: state hoisting, lifecycle, immersive mode, BackHandler. |
| `WatchViewModel.kt` | ~300 | Episode list, current video, resume position, player commands. |
| `WatchControlsOverlay.kt` | ~350 | Fullscreen controls (play/pause, seekbar, buttons, auto-hide). |
| `WatchMinimizedBar.kt` | ~150 | Minimized controls (play/pause, small seekbar, maximize). |
| `sheets/SpeedSheet.kt` | ~80 | Playback speed picker (0.25x–4x). |
| `sheets/QualitySheet.kt` | ~200 | Re-opens resolver (switch server/quality mid-playback). |
| `sheets/TrackSheets.kt` | ~150 | Audio + subtitle track pickers. |
| `WatchEpisodeList.kt` | ~250 | Episode list (minimized mode) + episode row composable. |

---

## 4. Player Lifecycle (MPV via AndroidView)

### 4.1 Create
1. `WatchScreen` enters composition → `koinInject<PlayerPreferences>()` + `WatchProgressStore`.
2. `AndroidView` factory inflates `R.layout.mpv_view` → `AnikutaMPVView`.
3. `PlayerInitializer.initMpvView(view, ctx, observer, headers)`:
   - Config dir: `filesDir/mpv/` (mkdirs + copy subfont.ttf + cacert.pem).
   - Write `mpv.conf` + `input.conf` (MpvConfigManager).
   - `view.initialize(mpvDir, cacheDir, "warn")` — calls `initOptions()`:
     - `vo = "gpu"` (or `"gpu-next"` if gpuNext pref).
     - `hwdec = "auto"` (or `"no"` if tryHWDecoding is false).
     - `demuxer-max-bytes = 256MB` (O_MR1+) or 128MB (D-049 video caching).
     - `keep-open = true`.
     - Subtitle preferences.
   - `MPVLib.addLogObserver(obs); MPVLib.addObserver(obs)`.
   - `MPVLib.setOptionString("http-header-fields", headers)`.
4. `PlayerInitializer.loadVideo(view, videoUrl, ctx)`:
   - `resolveUrlForMpv(url, ctx)` — content:// → fd://.
   - `MPVLib.command(["loadfile", url, "replace"])`.

### 4.2 FILE_LOADED event (MPV fires when video metadata is parsed)
1. `setSwitchingEpisode(false)` + `setLoadingState(READY)`.
2. Auto-play if `playerPreferences.autoPlay()`.
3. Load external subtitle/audio tracks via `sub-add` / `audio-add`.
4. **Resume**: `WatchProgressStore.get(contentId, epNum)` → seek to saved position.
   - If >90% watched → restart from 0.
   - Else → seek to saved position + show "Start over" overlay for 10s.

### 4.3 Save progress
- **On dispose**: save final position.
- **Periodic**: every 10 seconds.
- **On episode switch**: save old episode's position before resetting.

### 4.4 Destroy
- `PlayerInitializer.destroyMpv(view, observer)`:
  - `MPVLib.removeLogObserver(obs)`.
  - `MPVLib.removeObserver(obs)`.
  - `MPVLib.command(["stop"])`.
  - `view.destroy()`.

---

## 5. Key Decisions

### 5.1 Single MPV instance
The MPV view is NEVER recreated during fullscreen ↔ minimized transitions.
Only the overlay controls and surrounding layout change. The view is cached
in `var mpvView: AnikutaMPVView?` and reused.

### 5.2 Video caching (D-049)
- `demuxer-max-bytes = 256MB` (allows ~2-3 min of 1080p buffering).
- `demuxer-max-back-bytes = 64MB` (backward cache for seeking).
- `keep-open = true` (keeps file loaded after EOF — seeking works after end).
- `cache-secs` is NOT set (per Aniyomi — relies on demuxer cache only).

### 5.3 Controls auto-hide
- **Fullscreen**: 4 seconds.
- **Minimized**: 5 seconds.
- **Does NOT auto-hide when**: episode switching, video finished, controls locked.
- Tap to toggle show/hide.

### 5.4 Episode navigation
- **Next episode**: if `currentIndex < episodeList.size`, switch.
- **Previous**: not in old project (can add later).
- **On video end**: auto-advance to next (toggleable pref).

---

## 6. WatchRequest (upgrade from temporary WatchKey)

The current `WatchKey` carries `(videoUrl, animeTitle, quality)`. Phase 5c
upgrades to a proper `WatchRequest`:

```kotlin
data class WatchRequest(
    val videoUrl: String,
    val videoHeaders: String? = null,
    val animeTitle: String,
    val episodeUrl: String,
    val episodeNumber: Float,
    val episodeTitle: String,
    val sourceId: Long,
    val episodeList: List<EpisodeInfo>,  // for episode switching
)
```

This is passed from Details → Watch. The episode list enables next/prev
navigation without re-fetching.

---

## 7. Module Structure

New module: `:feature:watch` (api/impl split per Nav3 Pattern B).

```
:feature:watch:api/
  └── WatchKey.kt          (replaces the temporary one in :feature:anime-details:api)

:feature:watch:impl/
  ├── WatchScreen.kt
  ├── WatchViewModel.kt
  ├── WatchControlsOverlay.kt
  ├── WatchMinimizedBar.kt
  ├── sheets/
  │   ├── SpeedSheet.kt
  │   ├── QualitySheet.kt
  │   └── TrackSheets.kt
  └── WatchEpisodeList.kt
```

Dependencies:
- `:core:player` (AnikutaMPVView, PlayerStateHolder, PlayerInitializer, etc.)
- `:core:player-mpv-lib` (the AAR wrapper)
- `:core:video-resolver` (for mid-playback quality switch)
- `:core:watch-progress` (resume position)
- `:core:preferences` (PlayerPreferences)
- `:core:designsystem` (theme, components)

---

## 8. Implementation Order

1. **Create the `:feature:watch` module** (api + impl, build.gradle, settings.gradle).
2. **WatchScreen scaffold** — AndroidView(MPV), lifecycle, BackHandler, immersive mode.
3. **WatchViewModel** — state hoisting, loadVideo, save progress, episode nav.
4. **WatchControlsOverlay** — fullscreen controls (play/pause, seekbar, buttons).
5. **WatchMinimizedBar** — minimized controls.
6. **sheets/SpeedSheet** — speed picker.
7. **sheets/QualitySheet** — re-resolve mid-playback.
8. **Wire navigation** — Details → WatchKey → WatchScreen.
9. **Test end-to-end**: open anime → pick source → episodes → tap episode → resolver → pick video → watch screen plays.

---

## 9. Design Language (per DESIGN-LANGUAGE.md)

- **Fullscreen**: black background, white controls, translucent overlays.
- **Minimized**: themed background, 16:9 player box with rounded corners.
- **Animations**: 300ms FastOutSlowInEasing for all transitions.
- **Controls**: 40dp circular buttons, 22dp white icons (matching Details banner).

---

*This plan is the blueprint for Phase 5c. Implementation starts after the
current fixes (OkHttp version, NetworkOnMainThreadException) are verified.*
