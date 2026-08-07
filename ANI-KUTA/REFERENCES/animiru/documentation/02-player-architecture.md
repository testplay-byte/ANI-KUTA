# 02 — Player Architecture

> How MPV is integrated into the Animiru app, the player view/ViewModel
> pattern, the UI components, the video loading pipeline, track management,
> seekbar/progress, fullscreen/minimized switching, and PiP.

## 1. High-level architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│                          PlayerActivity                                │
│  (Android Activity, singleTask, supports PiP)                          │
│  app/.../ui/player/PlayerActivity.kt                                  │
│                                                                       │
│  Responsibilities:                                                    │
│   - host Compose UI (setContent { TachiyomiTheme { PlayerScreen(...) }})│
│   - receive Intent extras (animeId, episodeId, hostList, hostIndex,   │
│     vidIndex) via onNewIntent                                         │
│   - mediaSession (Bluetooth/lock-screen controls)                     │
│   - PiP params + BroadcastReceiver for PiP actions                    │
│   - hardware key dispatch (volume, media keys, D-pad)                 │
│   - software keyboard show/hide                                       │
│   - screenshot share/save routing                                     │
└──────┬────────────────────────────────────────────────────────────────┘
       │ viewModels<PlayerViewModel>()
       ▼
┌───────────────────────────────────────────────────────────────────────┐
│                          PlayerViewModel                              │
│  (AndroidViewModel, holds all player state)                           │
│  app/.../ui/player/PlayerViewModel.kt  (~2928 lines)                 │
│                                                                       │
│  Three StateFlow<UiState> exposed:                                   │
│   - stateData : PlayerStateData   (anime, episode, video, tracks, ...)│
│   - uiData    : PlayerUiData      (sheet/panel/dialog shown,         │
│                                    controls visibility, prefs snapshot)│
│   - playbackData : PlayerPlaybackData (position, duration, paused,   │
│                                    brightness, volume, seeking)       │
│                                                                       │
│  Also exposes:                                                       │
│   - eventFlow : SharedFlow<Event>  (one-shot: toast, finish, PiP,    │
│                                     keyboard, screenshot share, ...)  │
│   - aspectRatio : StateFlow<Double?>                                │
│   - player : MPVPlayer  (the MPV wrapper, created in init)           │
│   - mpv    : MPV       (is.xyz.mpv.MPV instance)                     │
│   - propFlow<T>(name) : StateFlow<T?>  (typed wrapper over           │
│                                       mpv.propFlow<T>)                │
└──────┬────────────────────────────────────────────────────────────────┘
       │ owns (1:1)
       ▼
┌───────────────────────────────────────────────────────────────────────┐
│                          MPVPlayer                                    │
│  (thin wrapper around is.xyz.mpv.MPV)                                 │
│  app/.../ui/player/mpv/MPVPlayer.kt  (417 lines)                     │
│                                                                       │
│  - creates MPV(context) { ... } in init                               │
│  - writes mpv.conf + input.conf to filesDir/mpv/                     │
│  - calls setOptionString for ~30 options (hwdec, vo, cache, sub, ...)│
│  - observes ~16 MPV properties (eof-reached + user-data/aniyomi/*)   │
│  - implements MPV.EventObserver, MPV.LogObserver,                    │
│    AudioManager.OnAudioFocusChangeListener                           │
│  - exposes eventFlow: SharedFlow<Event>                              │
│  - key dispatch via onKey(KeyEvent) → mpv.command("keydown", ...)    │
└──────┬────────────────────────────────────────────────────────────────┘
       │ delegates to
       ▼
┌───────────────────────────────────────────────────────────────────────┐
│                is.xyz.mpv.MPV  (mpv-android-lib, JNI)                 │
│  Native libmpv + libplayer.so (JNI glue). Loaded via AAR.            │
│  - attachSurface(Surface) / detachSurface()                          │
│  - setOptionString(name, value)                                      │
│  - setPropertyString/Int/Boolean/Float/Double/Node                   │
│  - getPropertyInt/String/Boolean/Node                                │
│  - observeProperty(name, format)                                     │
│  - command(vararg String)                                            │
│  - propFlow<T>(name) : StateFlow<T?>  ← Kotlin extension             │
│  - addObserver(EventObserver) / addLogObserver(LogObserver)          │
│  - close()  ← destroys native mpv handle                              │
└───────────────────────────────────────────────────────────────────────┘
```

## 2. The activity → viewmodel → player wiring

### Activity launches with intent extras

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:114-132
companion object {
    fun newIntent(
        context: Context,
        animeId: Long?,
        episodeId: Long?,
        hostList: List<Hoster>? = null,
        hostIndex: Int? = null,
        vidIndex: Int? = null,
    ): Intent {
        return Intent(context, PlayerActivity::class.java).apply {
            putExtra("animeId", animeId)
            putExtra("episodeId", episodeId)
            hostIndex?.let { putExtra("hostIndex", it) }
            vidIndex?.let { putExtra("vidIndex", it) }
            hostList?.let { putExtra("hostList", it.serialize()) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
```

Three launch modes:
1. **From anime details** — `animeId + episodeId` only; Animiru resolves
   hosters itself.
2. **From quality sheet "remember selection"** — `animeId + episodeId +
   hostList (serialized) + hostIndex + vidIndex`. The hostList is pre-fetched
   and passed via `SerializableHoster.serialize()` (see
   `source-api/.../model/Hoster.kt:60-71`).
3. **Episode switch** — `animeId + newEpisodeId`. The ViewModel tears down
   the old video and re-inits.

### onCreate → onNewIntent

The Activity's `onCreate` finishes setting up media session + setContent,
then calls `onNewIntent(this.intent)`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:186-278
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    registerSecureActivity(this)
    super.onCreate(savedInstanceState)

    setupMediaSession()
    viewModel.setupPlayerOrientation()
    // ... global exception handler ...
    viewModel.eventFlow.onEach { event -> /* dispatch */ }.launchIn(lifecycleScope)

    setContent {
        TachiyomiTheme {
            PlayerScreen(
                viewModel = viewModel,
                onBack = { /* PiP or finish */ },
                modifier = Modifier.fillMaxSize().onGloballyPositioned {
                    pipRect = it.boundsInWindow().toRect()
                },
            )
        }
    }

    onNewIntent(this.intent)
}
```

`onNewIntent` (line 134-184):
1. Reads extras; bails if `animeId`/`episodeId` missing.
2. Dismisses "new episodes" notification.
3. Saves current watching progress.
4. Sets `isLoadingEpisode = true` and `isLoadingHosters = true` on the VM.
5. Calls `viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)`
   — this loads anime from DB, sets up episode playlist, fetches hosters
   (synchronously if `hostList` is non-empty, otherwise from `EpisodeLoader`).
6. Then `viewModel.loadHosters(...)` runs in a coroutine — this is the
   async part where each hoster is fetched concurrently.

### Activity lifecycle

| Method | What it does |
|--------|--------------|
| `onCreate` | Setup media session, setContent, call `onNewIntent`. |
| `onStart` | Set PiP params, edge-to-edge, immersive sticky, hide system bars, FLAG_KEEP_SCREEN_ON, set cutout mode (SHORT_EDGES if fullscreen). |
| `onResume` | If was exiting (`isPlayerExiting == true`), reset exiting, restore volume from system. |
| `onPause` | Save progress. If in PiP, just super. Else, set `isPlayerExiting = true`. If finishing: `stop` mpv command. Else: `pause()`. |
| `onStop` | If in PiP and screen on, delete pending episodes (download-manager cleanup). |
| `onDestroy` | `viewModel.player.release()`, release media session, unregister noisy receiver. |
| `onUserLeaveHint` | Auto-enter PiP if `pipOnExit` is on and currently playing. |
| `onPictureInPictureModeChanged` | If leaving PiP and lifecycle == CREATED: release player + finish after 100ms. If entering PiP: hide controls, register PiP BroadcastReceiver. |
| `onConfigurationChanged` | If not in PiP: re-apply aspect ratio. If in PiP: hide controls. |
| `onKeyDown` / `onKeyUp` | Volume up/down → VM, DPad left/right → double-tap seek handlers, Space → pause/unpause, Media keys → mapped via SingleActionGesture. Other keys → `viewModel.onKey(event)` → MPVPlayer → `mpv.command("keydown", "<key>")`. |
| `onSaveInstanceState` | If not changing configurations: `viewModel.onSaveInstanceStateNonConfigurationChange()`. |

### `release()` on the MPVPlayer

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/mpv/MPVPlayer.kt:389-402
fun release() {
    if (isExiting) return
    isExiting = true

    audioFocusRequest?.let {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
    }
    audioFocusRequest = null

    handler.removeCallbacksAndMessages(null)
    mpv.removeObserver(this)
    mpv.removeLogObserver(this)
    mpv.close()   // ← destroys native mpv handle
}
```

`isExiting` is the volatile flag checked at the top of every EventObserver
callback. Once `release()` runs, no further events are emitted on
`eventFlow`. This guards against race conditions during Activity teardown.

## 3. The PlayerViewModel — three-bucket state

The ViewModel exposes three separate `StateFlow`s instead of one giant UI
state. This is important: it means **Composables can subscribe to only the
slice of state they care about**, reducing recomposition.

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:2803-2890
@Stable
data class PlayerStateData(
    val isStopped: Boolean = false,
    val hasTrackers: Boolean = false,
    val incognitoMode: Boolean = false,
    val currentPlaylist: List<Episode> = emptyList(),
    val currentPlaylistIndex: Int = -1,
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val isEpisodeOnline: Boolean = false,
    val currentEpisode: Episode? = null,
    val currentAnime: Anime? = null,
    val currentSource: AnimeSource? = null,
    val currentVideo: Video? = null,
    val videoHeight: Int = 0,
    val videoWidth: Int = 0,
    val maxVolume: Int,
    val volumeBoostCap: Int? = null,
    val hasLoadedTracks: Boolean = false,
    val hasLoadedSubs: Boolean = false,
    val hasLoadedAudio: Boolean = false,
    val chapters: List<Segment> = emptyList(),
    val currentChapter: Segment? = null,
    val subtitleTracks: List<TrackNode> = emptyList(),
    val audioTracks: List<TrackNode> = emptyList(),
    val externalSubtitleTracks: List<VideoTrack.External> = emptyList(),
    val externalAudioTracks: List<VideoTrack.External> = emptyList(),
    val hosterList: List<Hoster> = emptyList(),
    val hosterState: List<HosterState> = emptyList(),
    val isPipAvailable: Boolean = false,
)

@Stable
data class PlayerUiData(
    val isLoadingHosters: Boolean = false,
    val isLoadingEpisode: Boolean = false,
    val previousPauseState: Boolean? = false,
    val hosterExpandedList: List<Boolean> = emptyList(),
    val selectedHosterVideoIndex: Pair<Int, Int> = Pair(-1, -1),
    val mediaTitle: String = "",
    val animeTitle: String = "",
    val controlsShown: Boolean = true,
    val statusBarShown: Boolean = false,
    val seekBarShown: Boolean = true,
    val isControlsLocked: Boolean = false,
    val playerUpdate: PlayerUpdates = PlayerUpdates.None,
    val isBrightnessSliderShown: Boolean = false,
    val isVolumeSliderShown: Boolean = false,
    val sheetShown: Sheets = Sheets.None,
    val panelShown: Panels = Panels.None,
    val dialogShown: Dialogs = Dialogs.None,
    val dismissSheet: Boolean = false,
    val fontList: List<String> = emptyList(),
    val customButtons: List<CustomButton> = emptyList(),
    val primaryButtonTitle: String = "",
    val primaryButton: CustomButton? = null,
    val skipIntroText: String? = null,
    /* + a snapshot of prefs that don't change at runtime */
    val reduceMotion: Boolean = false,
    val playerTimeToDisappearMs: Int = 4000,
    val swapVolumeAndBrightness: Boolean = false,
    val boostCap: Int = 30,
    val displayVolumeAsPercentage: Boolean = true,
    val showLoadingCircle: Boolean = true,
    val invertDuration: Boolean = false,
    val smoothSeeking: Boolean = false,
    val autoPlayEnabled: Boolean = false,
    val showChapterIndicator: Boolean = true,
    val playerSpeedPref: Float = 1f,
)

@Stable
data class PlayerPlaybackData(
    val paused: Boolean = false,
    val position: Int = 0,
    val duration: Int = 0,
    val currentVolume: Int,
    val currentBrightness: Float,
    val currentOrientation: Int? = null,
    val isSeeking: Boolean = false,
    val seekText: String? = null,
    val doubleTapSeekAmount: Int = 0,
    val isSeekingForwards: Boolean = false,
    val gestureSeekAmount: Pair<Int, Int>? = null,
    val remainingTime: Int = 0,
    val netflixTimeout: Int? = null,
)
```

The three-bucket split:
- **`stateData`** — what's being played (anime/episode/video/tracks). Stable
  across UI recompositions. Driven by domain layer + MPV events.
- **`uiData`** — what's currently shown (controls/sheets/panels/dialogs).
  Pure UI state. Some prefs are snapshotted here at VM init (read once,
  not re-collected).
- **`playbackData`** — fast-changing playback state (position, paused,
  brightness, volume, seeking). Updates many times per second via
  `propFlow<Int>("time-pos")`.

> ANI-KUTA: This three-bucket separation is worth porting. The previous
> ANI-KUTA `PlayerStateHolder` had a single state blob that recomposed the
> entire player UI on every position tick.

## 4. Subscribing to MPV properties — `propFlow<T>`

The MPV wrapper exposes a typed `propFlow<T>(name): StateFlow<T?>` extension
that wraps `mpv.observeProperty` + `MPV.EventObserver.eventProperty`. The
ViewModel re-exposes it as `viewModel.propFlow<T>(name)`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:386-388
inline fun <reified T> propFlow(name: String): StateFlow<T?> {
    return mpv.propFlow<T>(name)
}
```

And then PlayerScreen subscribes:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerScreen.kt:86-92
val mpvVolume by viewModel.propFlow<Int>("volume").collectAsStateWithLifecycle()
val pausedForCache by viewModel.propFlow<Boolean>("paused-for-cache").collectAsStateWithLifecycle()
val coreIdle by viewModel.propFlow<Boolean>("core-idle").collectAsStateWithLifecycle()
val readAhead by viewModel.propFlow<Float>("demuxer-cache-time").collectAsStateWithLifecycle()
val remaining by viewModel.propFlow<Int>("playtime-remaining").collectAsStateWithLifecycle()
val playbackSpeed by viewModel.propFlow<Float>("speed").collectAsStateWithLifecycle()
val currentChapter by viewModel.propFlow<Int>("chapter").collectAsStateWithLifecycle()
```

These flow into specific UI components (e.g. `pausedForCache` shows a
CircularProgressIndicator in the center). The Compose pieces only
recompose when the property they care about changes.

## 5. The video loading pipeline

```
                         PlayerActivity.onNewIntent
                                     │
                                     ▼
                  viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
                                     │
              ┌──────────────────────┼────────────────────────────────┐
              │                                                      │
   if hostList non-empty                                  else: EpisodeLoader.getHosters
   parse via SerializableHoster.toHosterList()            (calls source.getHosterList or
   set qualityIndex = (hostIndex, vidIndex)                source.getVideoList on ext-lib 16)
              │                                                      │
              └─────────────────────► currentHosterList ◄────────────┘
                                     │
                                     ▼
                  viewModel.loadHosters(hosterList, hostIndex, videoIndex)
                                     │
                  ┌──────────────────┼─────────────────────────────┐
                  │ for each hoster (async via coroutineScope):    │
                  │   EpisodeLoader.loadHosterVideos(source, hoster)│
                  │     → returns HosterState.Ready/Error           │
                  │   updateStateData with new HosterState          │
                  │   if hosterIdx == hostIndex: loadVideo(...)     │
                  └──────────────────┬─────────────────────────────┘
                                     │
                          if no preferred video loaded:
                          HosterLoader.selectBestVideo(stateData.hosterState)
                          → (bestHosterIdx, bestVideoIdx)
                                     │
                                     ▼
                            loadVideo(video, hosterIdx, videoIdx)
                                     │
                  ┌──────────────────┼────────────────────────────────┐
                  │ 1. update selectedHosterVideoIndex on uiData       │
                  │ 2. update hosterState[idx].videoState[vidx] = LOAD_VIDEO│
                  │ 3. pause() until loaded                            │
                  │ 4. HosterLoader.getResolvedVideo(source, video)    │
                  │    → source.resolveVideo(video) if !initialized    │
                  │ 5. if resolvedVideo.videoUrl empty:                │
                  │      try next best video (recursive)               │
                  │ 6. else: update hosterState[idx].videoState[vidx] = READY│
                  │       setVideo(resolvedVideo)                      │
                  └──────────────────┬────────────────────────────────┘
                                     │
                                     ▼
                              setVideo(video)
                                     │
                  ┌──────────────────┼────────────────────────────────┐
                  │ 1. setHttpOptions(video) — writes http-header-fields │
                  │ 2. set start position:                              │
                  │    - if isLoadingEpisode: episode.last_second_seen  │
                  │      (or 0 if seen and !preserveWatchingPosition)   │
                  │    - else: playbackData.position (current position) │
                  │ 3. build mpvArgs string from video.mpvArgs +        │
                  │    forced (sid=no, aid=no)                          │
                  │ 4. mpv.command("loadfile", url, "replace", "0",     │
                  │                videoOptions)                        │
                  └─────────────────────────────────────────────────────┘
```

### Key code: `setVideo`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1032-1069
private fun setVideo(video: Video?) {
    if (player.isExiting) return
    if (video == null) return

    updateStateData { it.copy(isStopped = false) }
    setHttpOptions(video)

    if (uiData.value.isLoadingEpisode) {
        stateData.value.currentEpisode?.let { episode ->
            val preservePos = playerPreferences.preserveWatchingPosition.get()
            val resumePosition = if (episode.seen && !preservePos) {
                0L
            } else {
                episode.last_second_seen
            }
            mpvCommand("set", "start", "${resumePosition / 1000F}")
        }
    } else {
        mpvCommand("set", "start", playbackData.value.position.toString())
    }

    // We handle selecting these in the viewmodel
    val mpvOpts = listOf(
        Pair("sid", "no"),
        Pair("aid", "no"),
    )
    val videoOptions = (video.mpvArgs + mpvOpts).joinToString(",") { (option, value) ->
        "$option=\"$value\""
    }

    mpvCommand(
        "loadfile",
        parseVideoUrl(video.videoUrl)!!,
        "replace",
        "0",
        videoOptions,
    )
}
```

Notable points:
- **`sid=no, aid=no`** are forced into every `loadfile` — track selection is
  entirely controlled by the ViewModel after `track-list` event fires.
- **`mpvArgs`** is per-video metadata: extensions can attach arbitrary
  MPV options to a specific video (e.g. `--http-header-fields=...`,
  `--user-agent=...`). They go in the 5th argument of `loadfile` as a
  comma-separated `key="value"` list.
- **`start` position** is set via `mpvCommand("set", "start", ...)` BEFORE
  `loadfile` — MPV reads it when the new file loads.

### Key code: `setHttpOptions`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1076-1090
private fun setHttpOptions(video: Video) {
    if (!stateData.value.isEpisodeOnline) return
    val source = stateData.value.currentSource as? AnimeHttpSource
        ?: return

    val headers = (video.headers ?: source.headers)
        .toMultimap()
        .mapValues { it.value.firstOrNull() ?: "" }

    val httpHeaderString = headers.map {
        it.key + ": " + it.value.replace(",", "\\,")
    }.joinToString(",")

    mpv.setOptionString("http-header-fields", httpHeaderString)
}
```

- Skipped for local files (`isEpisodeOnline == false`).
- Falls back to `source.headers` if `video.headers` is null.
- Commas in header values are escaped as `\,` (MPV's CSV format).
- Set via `setOptionString` (NOT `setPropertyString`), because
  `http-header-fields` is a *startup* option — it must be set before
  `loadfile`.

### Key code: `loadHosters` — concurrent fetch + early exit

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:840-931
fun loadHosters(hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
    val hasFoundPreferredVideo = AtomicBoolean(false)

    updateStateData { it.copy(hosterList = hosterList) }
    updateUiData { it.copy(hosterExpandedList = List(hosterList.size) { true }) }

    val source = stateData.value.currentSource
        ?: throw Exception("No source available")

    getHosterVideoLinksJob?.cancel()
    getHosterVideoLinksJob = viewModelScope.launchIO {
        updateStateData {
            it.copy(
                hosterState = hosterList.map { hoster ->
                    if (hoster.lazy) {
                        HosterState.Idle(hoster.hosterName)
                    } else if (hoster.videoList == null) {
                        HosterState.Loading(hoster.hosterName)
                    } else {
                        val videoList = hoster.videoList!!
                        HosterState.Ready(
                            hoster.hosterName,
                            videoList,
                            List(videoList.size) { Video.State.QUEUE },
                        )
                    }
                },
            )
        }

        try {
            coroutineScope {
                hosterList.mapIndexed { hosterIdx, hoster ->
                    async {
                        val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)
                        updateHosterStateAt(hosterIdx, hosterState)
                        if (hosterState is HosterState.Ready) {
                            // Try the user's preferred (hostIndex, videoIndex) first
                            if (hosterIdx == hosterIndex) {
                                hosterState.videoList.getOrNull(videoIndex)?.let {
                                    hasFoundPreferredVideo.set(true)
                                    val success = loadVideo(it, hosterIndex, videoIndex)
                                    if (!success) {
                                        hasFoundPreferredVideo.set(false)
                                    }
                                }
                            }

                            // Try the source's preferred video
                            val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                            if (prefIndex != -1 && hosterIndex == -1) {
                                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                    if (uiData.value.selectedHosterVideoIndex == Pair(-1, -1)) {
                                        val success = loadVideo(
                                            hosterState.videoList[prefIndex],
                                            hosterIdx,
                                            prefIndex,
                                        )
                                        if (!success) {
                                            hasFoundPreferredVideo.set(false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()

                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                    val (hosterIdx, videoIdx) = HosterLoader.selectBestVideo(stateData.value.hosterState)
                    if (hosterIdx == -1) {
                        throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                    }
                    val video = (stateData.value.hosterState[hosterIdx] as HosterState.Ready).videoList[videoIdx]
                    loadVideo(video, hosterIdx, videoIdx)
                }
            }
        } catch (e: CancellationException) {
            updateStateData {
                it.copy(
                    hosterState = it.hosterList.map { h -> HosterState.Idle(h.hosterName) },
                )
            }
            throw e
        }
    }
}
```

Three-tier fallback:
1. **User-specified** `(hostIndex, videoIndex)` if non-empty.
2. **Source-preferred** video (where `video.preferred == true`).
3. **Best available** — `HosterLoader.selectBestVideo` picks first non-empty
   URL.

This is the pipeline that produces the quality sheet's list of hosters +
videos and the "selected" highlight.

## 6. Player UI components

```
PlayerScreen (root composable)
  ├─ MpvSurface            ← SurfaceView → mpv.attachSurface
  ├─ GestureHandler        ← pointerInput: tap, double-tap, long-press, drag
  ├─ DoubleTapToSeekOvals  ← visual overlay for double-tap seek animation
  ├─ OrientationOverlay    ← sets activity.requestedOrientation
  ├─ SystemAwakeOverlay    ← FLAG_KEEP_SCREEN_ON toggle when paused
  ├─ BrightnessOverlay     ← window.attributes.screenBrightness + Canvas dim
  ├─ SystemBarOverlay      ← hide/show system bars via WindowInsetsController
  └─ CompositionLocalProvider(playerRipple, LocalContentColor.White)
      ├─ PlayerControls     ← the actual controls overlay (see 04)
      ├─ PlayerSheets       ← bottom sheets (QualitySheet, SubtitlesSheet, ...)
      ├─ PlayerPanels       ← right-side panels (SubtitleSettings, VideoFilters, ...)
      └─ PlayerDialogs      ← centered dialogs (EpisodeList, IntegerPicker)
```

`PlayerControls` uses a `ConstraintLayout` with 9 anchored regions:
- `topLeftControls` — back button + anime/episode title
- `topRightControls` — auto-play, subtitles, audio, quality, more buttons
- `bottomLeftControls` — lock, rotation, speed, chapter
- `bottomRightControls` — skip-intro/custom button, PiP, aspect ratio
- `centerControls` — prev/play-pause/next (or loading spinner)
- `seekbar` — SeekbarWithTimers (Seeker + position/duration labels)
- `volumeSlider` / `brightnessSlider` — vertical sliders (one each side)
- `unlockControlsButton` — lock icon shown when controls are locked
- `playerUpdates` — transient text (aspect ratio change, speed, etc.)

All regions are wrapped in `AnimatedVisibility` with slide-in/slide-out
animations (300ms exit, 100ms enter). See `04-player-controls.md` for detail.

## 7. Subtitle/audio track management (overview)

Tracks come from two sources:
1. **Internal/embedded** — MPV fires `track-list` property change; the VM
   deserializes it into `List<TrackNode>`. Each `TrackNode` has `id`, `type`
   (`"video"`/`"audio"`/`"sub"`), `lang`, `title`, `selected`, etc.
2. **External** — user adds via SAF picker, or the `Video` object includes
   `subtitleTracks: List<Track>` and `audioTracks: List<Track>` from the
   extension. Loaded via `mpv.command("sub-add", url, "auto", "aniyomi-track-index=N")`
   or `"audio-add", ...`.

The VM tracks each external track's `TrackState` (Idle → Loading → Loaded /
Error) so the UI can show a spinner or error icon next to it.

Selection logic:
- `selectSub(track)` / `selectAudio(track)` decides whether to add the
  external track first or just switch `sid`/`aid`.
- `selectSubById(id)` implements **secondary-sid** logic: tapping a
  selected subtitle makes it the secondary; tapping a non-selected
  subtitle makes it primary. This is the Aniyomi "two subtitles" feature.

See `07-subtitle-management.md` for full detail.

## 8. Quality/server selection

The QualitySheet shows a `HosterState`-driven accordion:
- For each hoster: a row showing hoster name + state (Idle/Loading/Ready/Error).
- Tapping a Ready hoster expands/collapses its video list.
- Tapping an Idle hoster triggers `EpisodeLoader.loadHosterVideos(source, hoster, force = true)` — this is the **lazy hoster** pattern.
- Tapping a video calls `onVideoClicked(hosterIdx, videoIdx)` → `viewModel.loadVideo(...)`.

State machine for each video:
```
QUEUE → LOAD_VIDEO → READY
                  ↘ ERROR
```
Loading is reflected as a small `CircularProgressIndicator` next to the
video title; errors show an `ErrorOutline` icon.

See `05-player-sheets.md` for the sheet UI detail and `06-video-resolution.md`
for the resolver pipeline.

## 9. Seekbar / progress tracking

The SeekbarWithTimers composable uses the `seeker` library:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/components/SeekBar.kt:94-117
Seeker(
    value = position.coerceIn(0f, duration),
    range = 0f..duration,
    onValueChange = onValueChange,
    onValueChangeFinished = onValueChangeFinished,
    readAheadValue = readAheadValue,
    segments = chapters.filter { it.start in 0f..duration }.let {
        if (it.isNotEmpty() && it[0].start != 0f) {
            persistentListOf(Segment("", 0f)) + it
        } else {
            it
        } + it
    },
    modifier = Modifier.weight(1f),
    colors = SeekerDefaults.seekerColors(
        progressColor = MaterialTheme.colorScheme.primary,
        thumbColor = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.background,
        readAheadColor = MaterialTheme.colorScheme.inversePrimary,
    ),
)
```

- `value` = current position (driven by `propFlow<Int>("time-pos")`).
- `readAheadValue` = `demuxer-cache-time` (how much is buffered ahead).
- `segments` = chapter list (colored ticks on the seekbar).

Seeking:
- `onValueChange` (during drag) → `PlayerEvent.Seek(position)` → `seekTo(position)`.
  - `seekTo` calls `mpv.command("seek", position, if (smoothSeeking) "absolute" else "absolute+keyframes")`.
- `onValueChangeFinished` → `PlayerEvent.SeekFinished` → `updatePlaybackData { it.copy(isSeeking = false) }`.

## 10. Fullscreen / minimized mode switching

Animiru's "fullscreen" is the system UI immersion + cutout mode:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:330-355
override fun onStart() {
    super.onStart()
    setPictureInPictureParams(createPipParams())
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LOW_PROFILE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen.get()) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }
}
```

There is **no separate "minimized" mode** distinct from PiP. The two modes
are:
1. **Fullscreen** — immersive system bars, content draws into cutouts.
2. **PiP** — system-managed floating window (separate from the Activity
   lifecycle; the Activity receives `onPictureInPictureModeChanged`).

The `playerFullscreen` preference only controls cutout mode. There is no
"mini-player" within the app.

## 11. Picture-in-Picture

PiP is fully wired. Three entry points:
1. **Back press while playing** — if `pipOnExit` pref is on, enters PiP
   instead of finishing (`PlayerActivity.kt:253-261`).
2. **Home button while playing** — `onUserLeaveHint` enters PiP if
   `pipOnExit` is on (`PlayerActivity.kt:323-328`).
3. **PiP button in controls** — explicit `PlayerEvent.EnterPip` →
   `Event.EnterPip` → `enterPictureInPictureMode(createPipParams())`.

PiP parameters (`createPipParams` at `PlayerActivity.kt:390-426`):
- **Title/Subtitle** (Android 13+): anime title + episode name.
- **Auto-enter** (Android 12+): only if playing and `pipOnExit` is on.
- **Seamless resize** (Android 12+): only if playing.
- **Actions** (`createPipActions`): play/pause + (skip ±10s OR prev
  episode) + next episode. Three actions max.
- **Source rect hint** = the player's bounds in window (for smooth
  transition animation).
- **Aspect ratio** = video's aspect ratio (from `videoWidth/videoHeight`),
  clamped to `[0.42, 2.38]` (PiP API limits). Falls back to 16:9.

PiP actions are wired via a `BroadcastReceiver`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt:459-477
pipReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != PIP_INTENTS_FILTER) return
        when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
            PIP_PAUSE -> viewModel.pause()
            PIP_PLAY -> viewModel.unpause()
            PIP_NEXT -> viewModel.nextEpisode(next = true)
            PIP_PREVIOUS -> viewModel.nextEpisode(next = false)
            PIP_SKIP -> viewModel.seekBy(10)
        }
        setPictureInPictureParams(createPipParams())
    }
}
```

The action constants live in `PipActions.kt:107-113`:
```kotlin
const val PIP_INTENTS_FILTER = "pip_control"
const val PIP_INTENT_ACTION = "media_control"
const val PIP_PAUSE = 1
const val PIP_PLAY = 2
const val PIP_PREVIOUS = 3
const val PIP_NEXT = 4
const val PIP_SKIP = 5
```

The `pipReplaceWithPrevious` preference swaps the first action: if true,
show "previous episode"; if false, show "skip 10s forward".

When leaving PiP:
- If lifecycle == CREATED (user dismissed PiP), release player + finish
  Activity after 100ms delay (`PlayerActivity.kt:436-443`).
- Otherwise (user returned to app), restore saved brightness.

## 12. The Lua bridge — `aniyomi.lua`

Animiru ships a Lua script (`app/src/main/assets/aniyomi.lua`) that
bridges MPV's Lua scripting environment back into Kotlin. This is how
custom buttons work: the user writes Lua, and the Lua calls back into
Kotlin via `mp.set_property("user-data/aniyomi/...", value)`.

The Lua script is copied to `filesDir/mpv/scripts/aniyomi.lua` by
`MpvConfig.copyFiles()` (see `animiru/feature/mpvfiles/MpvConfig.kt:64-71`).

The VM observes all `user-data/aniyomi/*` properties (16 of them, see
`MPVPlayer.kt:154-171`) and dispatches them via `handleLuaInvocation`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1979-2095
fun handleLuaInvocation(property: String, value: String) {
    val data = value.removePrefix("\"").removeSuffix("\"").ifEmpty { return }

    when (property.substringAfterLast("/")) {
        "show_text" -> updateUiData { it.copy(playerUpdate = PlayerUpdates.ShowText(data)) }
        "toggle_ui" -> when (data) {
            "show" -> showControls()
            "toggle" -> if (uiData.value.controlsShown) hideControls() else showControls()
            "hide" -> { /* dismiss all sheets/panels/dialogs + hideControls */ }
        }
        "show_panel" -> when (data) {
            "subtitle_settings" -> setPanel(Panels.SubtitleSettings)
            "subtitle_delay" -> setPanel(Panels.SubtitleDelay)
            "audio_delay" -> setPanel(Panels.AudioDelay)
            "video_filters" -> setPanel(Panels.VideoFilters)
        }
        "set_button_title" -> updateUiData { it.copy(primaryButtonTitle = data) }
        "reset_button_title" -> uiData.value.customButtons.firstOrNull { it.isFavorite }?.let { setPrimaryCustomButtonTitle(it) }
        "switch_episode" -> when (data) {
            "n" -> nextEpisode(next = true)
            "p" -> nextEpisode(next = false)
        }
        "launch_int_picker" -> {
            val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
            val defaultValue = mpv.getPropertyInt(pickerProperty)!!
            setDialog(Dialogs.IntegerPicker(
                defaultValue = defaultValue,
                minValue = start.toInt(),
                maxValue = stop.toInt(),
                step = step.toInt(),
                nameFormat = nameFormat,
                title = title,
                onChange = { setPropertyInt(pickerProperty, it) },
                onDismissRequest = { setDialog(Dialogs.None) },
            ))
        }
        "show_seek_text" -> {
            val (forward, text) = data.split("|", limit = 2)
            showSeekText(forward == "true", text)
        }
        "pause" -> when (data) {
            "pause" -> pause()
            "unpause" -> unpause()
            "pauseunpause" -> pauseUnpause()
        }
        "seek_to_with_text" -> { val (seekValue, text) = data.split("|", limit = 2); seekToWithText(seekValue.toInt(), text) }
        "seek_by_with_text" -> { val (seekValue, text) = data.split("|", limit = 2); seekByWithText(seekValue.toInt(), text) }
        "seek_by" -> seekByWithText(data.toInt(), null)
        "seek_to" -> seekToWithText(data.toInt(), null)
        "toggle_button" -> { /* show/hide primary custom button */ }
        "software_keyboard" -> {
            viewModelScope.launch {
                when (data) {
                    "show" -> _eventFlow.emit(Event.SetKeyboard(true))
                    "hide" -> _eventFlow.emit(Event.SetKeyboard(false))
                    "toggle" -> _eventFlow.emit(Event.ToggleKeyboard)
                }
            }
        }
    }

    setPropertyString(property, "")  // ← clear so the next write re-fires
}
```

This is a powerful extension point: a Lua script can drive the entire
player UI. The custom-button system uses it (`button${id}()` Lua functions
registered via `mp.register_script_message`).

> ANI-KUTA: This Lua bridge is complex but powerful. ANI-KUTA may want to
> skip it initially and only add it if custom buttons become a feature.

## 13. Sleep timer + AniSkip integration

The VM has a `startTimer(seconds)` method that uses a `Job` + `delay`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:2788-2800
fun startTimer(seconds: Int) {
    timerJob?.cancel()
    updatePlaybackData { it.copy(remainingTime = seconds) }
    if (seconds < 1) return
    timerJob = viewModelScope.launch {
        for (time in seconds downTo 0) {
            updatePlaybackData { it.copy(remainingTime = time) }
            delay(1.seconds)
        }
        setPropertyBoolean("pause", true)
        _eventFlow.emit(Event.ToastResource(AYMR.strings.toast_sleep_timer_ended))
    }
}
```

AniSkip integration calls a public API (`AniSkipApi`) based on MAL ID
(fetched via AniList lookup if needed), gets skip timestamps, and merges
them into MPV's `chapter-list` via `MPVNode`. The skip button appears
automatically when entering an opening/ending chapter.

> ANI-KUTA: AniSkip is an external dependency on a public API; whether to
> port it depends on product scope.
