package com.confused.anikuta.feature.watch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star  // Phase 4
import androidx.compose.material.icons.filled.StarBorder  // Phase 4
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.animateScrollToItemCentered
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.player.AnikutaMPVView
import com.confused.anikuta.core.player.PlayerInitializer
import com.confused.anikuta.core.player.PlayerLoadingState
import com.confused.anikuta.core.player.PlayerMode
import com.confused.anikuta.core.player.PlayerObserver
import com.confused.anikuta.core.player.PlayerStateHolder
import com.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import com.confused.anikuta.core.playbackcache.PlaybackCacheManager
import com.confused.anikuta.core.playbackcache.PlaybackVideoId
import com.confused.anikuta.core.playbackcache.serializeTracks
import com.confused.anikuta.core.playbackcache.serverKeyFromVideoTitle
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolverVideo
import com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "Anikuta:Feature:Watch"

/**
 * The Watch screen — plays a video via MPV.
 *
 * Layout (minimized mode) — matches old project:
 * ```
 * ┌─────────────────────────────────┐
 * │  ┌───────────────────────────┐  │  ← Floating pill top bar
 * │  │ ◁ Back  ANIKUTA  ⚙ Gear  │  │     (collapses on scroll)
 * │  └───────────────────────────┘  │
 * ├─────────────────────────────────┤
 * │  ┌───────────────────────────┐  │  ← Player 16:9 (rounded corners)
 * │  │      Video Player         │  │     (NOT edge-to-edge)
 * │  │   [play/pause overlay]    │  │
 * │  │   [seek bar] [⛶]         │  │
 * │  └───────────────────────────┘  │
 * ├─────────────────────────────────┤
 * │  Currently playing episode N   │  ← Episode description (scrollable)
 * │  Episode title                  │
 * │  Synopsis...                    │
 * ├─────────────────────────────────┤
 * │  Episodes (12)                  │  ← Episode list (scrollable)
 * │  ┌──┐ EP 1  Title              │
 * │  └──┘                           │
 * │  ┌──┐ EP 2  Title              │
 * │  └──┘                           │
 * └─────────────────────────────────┘
 * ```
 *
 * Layout (fullscreen mode):
 * ```
 * ┌─────────────────────────────────┐  ← edge-to-edge, black, landscape
 * │ ◁  Title  EP 5   1080p    [⛶] │  ← top bar (auto-hide)
 * │                                 │
 * │      [-10s]  ▶  [+10s]         │  ← center controls (auto-hide)
 * │                                 │
 * │ ═══════════════════════════════ │  ← seek bar (auto-hide)
 * │ 0:30  [1.0x][⏭][✕]       24:00 │  ← bottom controls (auto-hide)
 * └─────────────────────────────────┘
 * ```
 *
 * CORE_RULES §22: smooth animations (300ms FastOutSlowInEasing).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Watch".
 */
@Composable
fun WatchScreen(
    watchKey: WatchKey,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val stateHolder = remember { PlayerStateHolder() }
    // ROUND 122 (R3): the details page serializes this list in the SOURCE's raw
    // order — commonly DESCENDING (EP 12…EP 1) — and every positional walker
    // (skip buttons, the old successor lookup) walked it BACKWARDS on such
    // sources (the "next after ep 2 was ep 1" report; "the back button goes to
    // the next episode"). One ascending sort here (stable — sub/dub duplicate
    // numbers keep source order) makes every consumer consistent.
    val episodeList = remember { watchKey.parseEpisodeList().sortedBy { it.episodeNumber } }
    val episodeMetadata = remember { watchKey.parseEpisodeMetadata() }
    val playerPreferences = koinInject<com.confused.anikuta.core.preferences.PlayerPreferences>()
    val extensionManager = koinInject<com.confused.anikuta.data.extension.manager.ExtensionManager>()
    val videoResolver = koinInject<com.confused.anikuta.core.videoresolver.VideoResolver>()
    val watchProgressStore = koinInject<WatchProgressStore>()
    val subtitleEngine = koinInject<com.confused.anikuta.core.player.subtitles.SubtitleEngine>()
    // D.FIX: DownloadManager — needed to check if an episode is downloaded when
    // switching, so downloaded episodes play offline (fd://) instead of trying
    // to resolve from the network source.
    val downloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()
    // Video caching (test-feature branch): the local HTTP cache proxy between MPV
    // and the video URL. playbackUrlFor() is non-suspend + fail-open — any failure
    // returns the original URL, so the cache can never break playback.
    val playbackCacheManager = koinInject<PlaybackCacheManager>()
    val scope = rememberCoroutineScope()

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = com.confused.anikuta.core.debugapi.LocalDebugContextUpdater.current
    val watchCtx = remember(watchKey) {
        com.confused.anikuta.core.debugapi.DebugContext(
            screenName = "Watch — ${watchKey.animeTitle}",
            screenData = mapOf(
                "mainId" to watchKey.mainId,
                "episodeNumber" to watchKey.episodeNumber.toString(),
                "videoUrl" to (watchKey.videoUrl.take(60) + "…"),
                "episodeCount" to episodeList.size.toString(),
            ),
            relevantTables = if (watchKey.mainId.isNotBlank()) listOf(
                com.confused.anikuta.core.debugapi.DbReference("watch_progress", "main_id", watchKey.mainId, "View watch progress"),
                com.confused.anikuta.core.debugapi.DbReference("downloaded_episode", "main_id", watchKey.mainId, "View downloads"),
            ) else emptyList(),
        )
    }
    androidx.compose.runtime.LaunchedEffect(watchCtx) { updateDebugContext(watchCtx) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }

    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }
    // Hoist the observer so switch handlers can set pending subtitle/audio tracks
    // + track headers before calling loadfile.
    var observer by remember { mutableStateOf<PlayerObserver?>(null) }
    // CRITICAL: Hoist the MPVLib observer wrappers so onDispose can REMOVE them.
    // Without removal, observers accumulate across screen entries → every event
    // fires N times (N = number of entries). This was causing 4x duplication
    // in the logs (4 observers registered after 4 entries).
    var logObserverRef by remember { mutableStateOf<`is`.xyz.mpv.MPVLib.LogObserver?>(null) }
    var eventObserverRef by remember { mutableStateOf<`is`.xyz.mpv.MPVLib.EventObserver?>(null) }
    // D.FIX: ParcelFileDescriptor for offline playback (content:// → fd:// conversion).
    // Kept open while MPV plays from it. Closed on dispose.
    var mpvParcelFileDescriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }

    // ── D-407 (round 31): the MANUAL SUBTITLE IMPORT ──────────────────────────
    // The subtitle sheet's permanent "Add subtitle file" action → the device's
    // multi-select file picker → each picked file is validated (.srt/.vtt/
    // .ass/.ssa/.sub/.ttml), PERSISTED into the downloaded episode's dedicated
    // subtitles/ folder (+ DB + .data.json) when the CURRENT episode is a
    // download, staged into the player cache, and sub-add'ed with the "select"
    // flag — it activates immediately and appears in the refreshed track list.
    val subtitleFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = PickSubtitleFiles(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        Logger.i(TAG) { "Manual subtitle import — ${uris.size} file(s) picked" }
        scope.launch {
            var added = 0
            var rejected = 0
            for (uri in uris) {
                val displayName = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    resolveSubtitleDisplayName(context, uri)
                }
                val fileName = displayName ?: uri.lastPathSegment ?: ""
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext !in com.confused.anikuta.core.download.SUBTITLE_EXTENSIONS) {
                    rejected++
                    Logger.w(TAG) {
                        "Manual subtitle import — unsupported extension '.$ext' " +
                            "(name='${fileName.take(60)}')"
                    }
                    continue
                }
                // Persist when the CURRENT episode is downloaded (mainId +
                // the live episode key from the state holder — correct even
                // after in-player episode switches).
                val mainId = watchKey.mainId
                val currentKey = stateHolder.currentEpisodeUrl.value
                val persisted = if (mainId.isNotBlank() && currentKey.isNotBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            downloadManager.importManualSubtitle(mainId, currentKey, uri, displayName)
                        }.getOrNull()
                    }
                } else {
                    null
                }
                // The track to load NOW: the persisted file's URI (offline) or
                // the picked file's URI (streaming — session-scoped).
                val trackUri = persisted?.uri ?: uri.toString()
                val label = persisted?.label
                    ?: com.confused.anikuta.core.download.DownloadedSubtitleLabels
                        .prettify(fileName.substringBeforeLast('.').ifBlank { "Custom" })
                val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        subtitleEngine.stageSingle(
                            com.confused.anikuta.core.player.subtitles.SubtitleDownloadRequest(
                                url = trackUri,
                                lang = label,
                                headers = "",
                            ),
                        )
                    }.getOrNull()
                }
                if (staged != null) {
                    runCatching {
                        // "select" — the new track becomes ACTIVE immediately;
                        // the sheet (reopened) lists + can switch it like any
                        // other track.
                        MPVLib.command(arrayOf("sub-add", staged.absolutePath, "select", "", label))
                    }.onSuccess {
                        added++
                        Logger.i(TAG) { "Manual subtitle import — sub-add OK: '$label'" }
                        // D-408 (round 32): remember the manually-added track
                        // for this series — it pre-applies on the next open
                        // ("that subtitle file will be remembered… and will be
                        // pre-applied on that").
                        if (mainId.isNotBlank()) {
                            playerPreferences.setPreferredSubtitleTrack(mainId, label)
                        }
                    }.onFailure {
                        Logger.w(TAG) { "Manual subtitle import — sub-add FAILED: ${it.message}" }
                    }
                }
            }
            if (added > 0) {
                // Give MPV a moment to register the track(s), then refresh the
                // state holder so the sheet + any track UI sees them.
                kotlinx.coroutines.delay(600L)
                val view = mpvView
                if (view != null) {
                    runCatching {
                        val (subs, audio) = view.loadTracks()
                        stateHolder.updateTracks(subs, audio)
                        Logger.i(TAG) { "Manual subtitle import — refreshed: ${subs.size} sub track(s)" }
                    }
                }
                // ROUND 106 (WS-C): the themed app toast.
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    if (added == 1) "Subtitle added" else "$added subtitles added",
                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                )
            } else if (rejected > 0) {
                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                    "Unsupported file type (use .srt, .vtt, .ass, .ssa or .sub)",
                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                    durationMillis = 3600,
                )
            }
        }
    }


    // Seed the current-episode state from the WatchKey (immutable Nav3 contract —
    // we track the CURRENTLY playing episode in the state holder so it updates
    // on switch). This fixes the bug where the episode list highlight + "now
    // playing" card + QualitySheet servers stayed on the old episode after a switch.
    LaunchedEffect(Unit) {
        stateHolder.seedEpisodeState(
            url = watchKey.episodeUrl,
            number = watchKey.episodeNumber,
            title = watchKey.episodeTitle,
            resolvedVideosKey = watchKey.resolvedVideosKey,
        )
    }

    val playerMode by stateHolder.playerMode.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val errorMessage by stateHolder.errorMessage.collectAsState()
    val isSwitching by stateHolder.isSwitching.collectAsState()
    val isSwitchingEpisode by stateHolder.isSwitchingEpisode.collectAsState()
    val bufferAheadTime by stateHolder.bufferAheadTime.collectAsState()
    val currentEpisodeUrl by stateHolder.currentEpisodeUrl.collectAsState()
    val currentEpisodeNumber by stateHolder.currentEpisodeNumber.collectAsState()
    val currentEpisodeTitle by stateHolder.currentEpisodeTitle.collectAsState()
    val currentResolvedVideosKey by stateHolder.currentResolvedVideosKey.collectAsState()

    // ── Keep screen on ──
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Immersive mode + orientation for fullscreen ──
    // CRITICAL (top-padding bug fix): The previous code called
    // `setDecorFitsSystemWindows(window, true)` in minimized mode, which
    // conflicts with `enableEdgeToEdge()` in MainActivity (which sets it to
    // `false`). When the user left WatchScreen, the empty `onDispose` left the
    // window in `setDecorFitsSystemWindows=true` state → every subsequent
    // screen got DOUBLE top padding (Android auto-pad + Compose statusBarsPadding).
    //
    // Fix (aligned with old project's pattern):
    // - Only set `false` in fullscreen (never `true` in minimized).
    // - `onDispose` restores the app-wide edge-to-edge defaults.
    DisposableEffect(playerMode) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            if (playerMode == PlayerMode.FULLSCREEN) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                // MINIMIZED: only show the bars. Do NOT flip setDecorFitsSystemWindows
                // to true — that conflicts with enableEdgeToEdge() in MainActivity
                // and leaves the next screen with double top padding.
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                // Force portrait when exiting fullscreen — matches old project.
                // Even if the device is in landscape, the watch page should be portrait.
                // The user can rotate back to landscape if they re-enter fullscreen.
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        onDispose {
            // Leaving WatchScreen: restore the app-wide edge-to-edge defaults so
            // the next screen sees a clean window (no leaked orientation, no
            // hidden bars, no DecorFitsSystemWindows=true).
            val w = (context as? Activity)?.window ?: return@onDispose
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
            androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ── Back handler — only intercept in fullscreen (minimized → exit) ──
    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN) {
        stateHolder.updateMode(PlayerMode.MINIMIZED)
    }

    // ── Auto-hide controls ──
    val isVideoFinished = duration > 0 && position >= duration - 2 && !isPlaying
    LaunchedEffect(controlsVisible, playerMode, isVideoFinished) {
        if (controlsVisible && !isVideoFinished) {
            val delayMs = if (playerMode == PlayerMode.FULLSCREEN) 4000L else 5000L
            delay(delayMs)
            stateHolder.updateControlsVisible(false)
        }
    }

    // Force controls visible when video is finished
    LaunchedEffect(isVideoFinished) {
        if (isVideoFinished) stateHolder.updateControlsVisible(true)
    }

    // ── Clear switching when video starts playing (buffered 1%) ──
    // Once the video has buffered enough (1% of duration), we know the load
    // succeeded. Clear isSwitching + isSwitchingEpisode immediately — don't
    // wait for FILE_LOADED (which may fire late or not at all for some formats).
    // This fixes: "video started to play but loading was still there."
    LaunchedEffect(bufferAheadTime) {
        if (stateHolder.bufferedEnough && stateHolder.isSwitching.value) {
            Logger.i(TAG) { "Video buffered 1% — clearing switching flags" }
            stateHolder.setSwitching(false)
            stateHolder.setSwitchingEpisode(false)
        }
    }

    // ── Switching timeout watchdog (30s) ──
    // SAFETY NET: if isSwitching stays true for 30s, force-clear + show timeout
    // error. The user requested 30s — "it is quite enough for it to actually play."
    LaunchedEffect(isSwitching) {
        if (isSwitching) {
            delay(30_000L)
            if (stateHolder.isSwitching.value) {
                Logger.w(TAG) { "Switching timeout (30s) — force-clearing" }
                stateHolder.setSwitchingError("Video failed to load (timeout — the server took too long to respond)")
            }
        }
    }

    // ── Fatal-error watchdog (15s) — Round 120 (D-749): evidence-driven ────────
    // Catches demuxer errors that leave the player stuck WITHOUT firing efEvent.
    // Round-119 bugs fixed here: the watchdog treated a USER pause at the start
    // and the natural end-of-file (keep-open pins position at duration-2) as
    // "stuck" — users saw "This server is not responding" after pausing at 0:00
    // or ~15s after an episode ended. Now it fires ONLY when genuinely stuck at
    // the start: a loaded file (duration > 0), never played (position == 0),
    // not user-paused, not mid-switch, no error already shown. The end-of-file
    // case is covered by the auto-retry/efEvent path instead.
    LaunchedEffect(isPlaying, duration, position) {
        if (duration > 0 && !isPlaying && position == 0 &&
            !stateHolder.userPaused && stateHolder.errorMessage.value == null &&
            !stateHolder.isSwitching.value
        ) {
            delay(15_000L)
            // Re-check after delay — if still stuck at the start, show error.
            if (duration > 0 && !stateHolder.isPlaying.value && stateHolder.position.value == 0 &&
                !stateHolder.userPaused && stateHolder.errorMessage.value == null &&
                !stateHolder.isSwitching.value
            ) {
                // ROUND 124 (R2): ask MPV ITSELF whether it's paused — the app
                // flag desyncs (a quality-switch loadfile never forced
                // pause=no, so a paused mpv read as "dead server"). A loaded
                // file that is merely PAUSED gets unpaused (every loadfile in
                // this app means autoplay) instead of a false "not responding"
                // error — the device round: the error appeared, vanished and
                // returned in a loop while tapping play started the video.
                val mpvPaused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrDefault(false)
                if (mpvPaused) {
                    Logger.w(TAG) {
                        "Fatal-error watchdog: mpv PAUSED at start (app flag not set) — " +
                            "unpausing instead of erroring"
                    }
                    runCatching { MPVLib.setPropertyBoolean("pause", false) }
                } else {
                    Logger.w(TAG) { "Fatal-error watchdog: stuck at start for 15s (not user-paused)" }
                    stateHolder.setSwitchingError("This server is not responding. Try another server or quality.")
                }
            }
        }
    }

    // ── App-exit pause / resume ──
    // Pause playback when the app goes to background (ON_STOP) and resume when
    // it returns to foreground (ON_START). Uses LifecycleEventObserver to match
    // the old project's behavior. ON_STOP/ON_START (not ON_PAUSE/ON_RESUME) so
    // multi-window focus changes don't trigger a pause.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    Logger.i(TAG) { "App backgrounded — pausing playback" }
                    // D-749: counts as a deliberate pause for the watchdog
                    // (backgrounding at 0:00 must not read as "server stuck").
                    stateHolder.updateUserPaused(true)
                    runCatching { MPVLib.setPropertyBoolean("pause", true) }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    Logger.i(TAG) { "App foregrounded" }
                    // Don't auto-resume — let the user tap play. This matches the
                    // old project's default (resumeOnAppReturn pref can be added later).
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Periodic watch progress save (every 10s) ──
    // Phase WP: saves to SqlDelightWatchProgressStore (persistent — survives app restart).
    // The 85% auto-mark logic is in the store. Reads values directly from the state
    // holder (not collected state) so they're fresh at save time.
    LaunchedEffect(mpvInitialized) {
        if (!mpvInitialized) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            val pos = stateHolder.position.value
            val dur = stateHolder.duration.value
            val epUrl = stateHolder.currentEpisodeUrl.value
            // ROUND 121 (the device round): positions under a minute that are
            // nowhere near completion are NOT recorded — the shared policy's
            // floor ("watched less than one minute isn't recorded at all").
            // ROUND 122 (R2): ALSO require that this episode actually reached
            // playback (a real time-pos event while not switching) and that we
            // are not in an error state — a failed load leaves the OLD file's
            // pinned end-of-file position in the holder, and without these
            // guards that stale position was saved under the NEW episode's
            // key (the "failed episode gets marked watched" report).
            // ROUND 123 (R10): log EVERY evaluation — the gated-out ticks were
            // silent, which made the failed-episode case undiagnosable from
            // logcat alone.
            val persistDecision = dur > 0 && epUrl.isNotBlank() &&
                stateHolder.episodeReachedPlayback &&
                stateHolder.loadingState.value != PlayerLoadingState.ERROR &&
                com.confused.anikuta.core.watchprogress.PlaybackResumePolicy
                    .shouldPersist(pos.toLong(), dur.toLong())
            Logger.d(TAG) {
                "PeriodicSave eval: pos=$pos dur=$dur epNum=${stateHolder.currentEpisodeNumber.value} " +
                    "reachedPlayback=${stateHolder.episodeReachedPlayback} " +
                    "loading=${stateHolder.loadingState.value} → save=$persistDecision"
            }
            if (persistDecision) {
                val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
                val progress = WatchProgress(
                    episodeKey = epKey,
                    mainId = watchKey.mainId.ifBlank { null },
                    position = pos.toLong(),
                    duration = dur.toLong(),
                    completed = false,
                    completedAt = null,
                    lastWatchedAt = System.currentTimeMillis(),
                )
                scope.launch {
                    runCatching { watchProgressStore.save(epKey, progress) }
                        .onFailure { Logger.w(TAG) { "Progress save failed: ${it.message}" } }
                }
            }
        }
    }

    // ── WP-B2 + Round 120 (D-746/D-747/D-748): on FILE_LOADED (READY) ──────────
    // WP-B2: resetAutoMarkSuppressed re-arms the 85% auto-mark (was NEVER called → CF1 broken).
    // D-746: the resume seek runs through the shared PlaybackResumePolicy for
    // EVERY episode (the initial load AND each in-player switch), once per
    // episode key. This replaces the old one-shot `hasResumed` flag, which (a)
    // never resumed switched-to episodes and (b) had no watched-episode gate —
    // a completed episode re-opened at its LAST SAVED position (≈ the end,
    // because keep-open pins time-pos at EOF and the dispose save persists it).
    val loadingState by stateHolder.loadingState.collectAsState()

    // D-749: the switch job + generation — a rapid A→B→C tap sequence used to
    // run three concurrent resolve collectors, and the slowest one's loadfile
    // won (the wrong episode played). The job is cancelled + the generation is
    // bumped on every switch; a resolve result whose generation is stale is
    // discarded before it can touch MPV.
    var switchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var switchGeneration by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // D-746: per-episode resume bookkeeping (one seek per episode key).
    val resumedEpisodeKeys = remember { mutableStateMapOf<String, Boolean>() }
    // The "Resumed · mm:ss [Restart]" affordance: -1 = hidden, else the resumed
    // position in seconds (auto-hides after PlaybackResumePolicy.RESUME_PILL_DURATION_MS).
    var resumePillSec by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }

    // D-747: the Up Next state + the per-episode offer bookkeeping.
    var upNextEpisode by remember { mutableStateOf<SimpleEpisode?>(null) }
    var upNextVisible by remember { mutableStateOf(false) }
    var upNextCountdown by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val offeredNextKeys = remember { mutableStateMapOf<String, Boolean>() }
    // ROUND 122 (R4): the per-episode DISMISSAL latch — the device round showed
    // a dismissed card resurrecting (the mode switch was the correlated action;
    // the real holes were the EOF force-offer bypassing the once-latch and the
    // CS ticker re-offering). One map honored by EVERY offer path muzzles them
    // all; a new episode key naturally re-arms.
    val dismissedNextKeys = remember { mutableStateMapOf<String, Boolean>() }

    // D-748: intro/outro skipping — the per-series timestamps + the pill state
    // + the once-per-episode auto-skip bookkeeping.
    var skipTimestamps by remember(watchKey.mainId) {
        mutableStateOf(playerPreferences.getSkipTimestamps(watchKey.mainId))
    }
    // ROUND 121: the AniSkip service data for the CURRENT episode (per-
    // episode intervals — the service knows more than a per-series stamp).
    // Precedence: AniSkip interval > the manual per-series stamps (fallback
    // for content the service doesn't cover; still editable via the sheet).
    var episodeSkip by remember(watchKey.mainId) {
        mutableStateOf<com.confused.anikuta.core.preferences.PlayerPreferences.EpisodeSkipTimes?>(null)
    }
    val aniSkipClient = koinInject<com.confused.anikuta.core.network.AniSkipClient>()
    val contentRepository = koinInject<com.confused.anikuta.core.content.ContentRepository>()

    // ROUND 122 (R5): the effective skip ranges drawn ON the seek bar —
    // AniSkip wins per side, the manual stamps are the fallback, and the
    // ribbon shows for Off/Button/Auto ALIKE (the user's order: "it should
    // properly show on the progress bar itself from where to where the intro
    // is and from where to where the outro is, regardless of the user's
    // settings"). Deliberately never reads the mode prefs.
    val skipRanges = androidx.compose.runtime.remember(episodeSkip, skipTimestamps) {
        com.confused.anikuta.core.player.controls.SkipRanges(
            introStartSec = episodeSkip?.takeIf { it.hasIntro }?.introStartSec
                ?: skipTimestamps.takeIf { it.hasIntro }?.introStartSec,
            introEndSec = episodeSkip?.takeIf { it.hasIntro }?.introEndSec
                ?: skipTimestamps.takeIf { it.hasIntro }?.introEndSec,
            outroStartSec = episodeSkip?.takeIf { it.hasOutro }?.outroStartSec
                ?: skipTimestamps.takeIf { it.hasOutro }?.outroStartSec,
            outroEndSec = episodeSkip?.takeIf { it.hasOutro }?.outroEndSec,
        )
    }
    var skipPill by remember { mutableStateOf<SkipPillState?>(null) }
    val autoSkippedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val nextUpCardEnabled by playerPreferences.nextUpCardEnabled.changes.collectAsState(
        initial = playerPreferences.nextUpCardEnabled.get(),
    )
    val skipIntroMode by playerPreferences.skipIntroMode.changes.collectAsState(
        initial = playerPreferences.skipIntroMode.get(),
    )
    val skipOutroMode by playerPreferences.skipOutroMode.changes.collectAsState(
        initial = playerPreferences.skipOutroMode.get(),
    )

    // D-746/D-747/D-748: the Up Next successor. ROUND 122 (R3): the old lookup
    // was POSITIONAL (idx + 1 over the raw serialized list — commonly DESCENDING
    // from the details page), so "next" after ep 2 was ep 1. It is now
    // NUMBER-BASED: strictly the nearest HIGHER episode number present in the
    // list — never positional, never wrapping, sub/dub duplicate numbers
    // excluded (strictly >). The list itself is ascending-sorted at parse.
    val nextEpisodeInList: () -> SimpleEpisode? = {
        val currentUrl = stateHolder.currentEpisodeUrl.value
        val currentNum = stateHolder.currentEpisodeNumber.value
        val current = episodeList.firstOrNull { it.url == currentUrl }
            ?: episodeList.firstOrNull { it.episodeNumber == currentNum }
        if (current == null || current.episodeNumber <= 0f) {
            Logger.w(TAG) {
                "Up Next: current episode unresolved — url=${currentUrl.take(48)} num=$currentNum " +
                    "listSize=${episodeList.size} nums=${episodeList.take(6).map { it.episodeNumber }}"
            }
            null
        } else {
            val successor = episodeList
                .filter { it.episodeNumber > current.episodeNumber }
                .minByOrNull { it.episodeNumber }
            if (successor == null) {
                Logger.i(TAG) {
                    "Up Next: no higher episode than ${current.episodeNumber} in list " +
                        "(size=${episodeList.size}) — end of season"
                }
            }
            successor
        }
    }

    /** ROUND 122 (R3): the mirror — the nearest strictly-LOWER episode number. */
    val prevEpisodeInList: () -> SimpleEpisode? = {
        val currentUrl = stateHolder.currentEpisodeUrl.value
        val currentNum = stateHolder.currentEpisodeNumber.value
        val current = episodeList.firstOrNull { it.url == currentUrl }
            ?: episodeList.firstOrNull { it.episodeNumber == currentNum }
        current?.let {
            episodeList.filter { e -> e.episodeNumber < it.episodeNumber }.maxByOrNull { e -> e.episodeNumber }
        }
    }

    // D-747: surfaces the Up Next card for the current episode's successor
    // (once per episode key — the 95% poll and the EOF hook share this).
    // ROUND 122 (R4): a user dismissal is FINAL for the episode — the guard
    // runs BEFORE the once-latch so the EOF hook's force flag cannot resurrect
    // a dismissed card (the device report's resurrection).
    val offerNextEpisode: (Boolean) -> Unit = { force ->
        val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
        if (dismissedNextKeys[epKey] == true) {
            Logger.i(TAG) { "Up Next: suppressed (dismissed by the user) key=$epKey force=$force" }
        } else if (force || offeredNextKeys[epKey] != true) {
            offeredNextKeys[epKey] = true
            val next = nextEpisodeInList()
            if (next != null) {
                Logger.i(TAG) { "Up Next: offering ${next.name} (force=$force, key=$epKey)" }
                upNextEpisode = next
                upNextVisible = true
            } else if (force) {
                Logger.i(TAG) { "Up Next: EOF with no successor (end of list) — key=$epKey" }
            }
        }
    }

    // D-247: when playback fails while playing through the CACHE, the next retry
    // bypasses the cache entirely (direct network) — the episode must never fail
    // because of the cache. Reset on successful load (READY) + on video switches.
    var bypassCacheNextRetry by remember { mutableStateOf(false) }

    // D-192: Activity tracker — track WATCH_START on FILE_LOADED.
    val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker = koinInject()

    // ROUND 122 (R1): the file-loaded effect now keys on fileLoadedGeneration —
    // a counter bumped ONLY by a real MPV_EVENT_FILE_LOADED. The old key was
    // loadingState==READY, which is ALSO the holder's default and the
    // PLAYBACK_RESTART fallback's value: on the downloaded (fd://) path the
    // loadfile is posted 500ms late, so the effect ran before mpv had a file,
    // the resume seek hit an uninitialized core (mpv silently drops pre-init
    // seeks), the latch marked it done — and the downloaded episode played
    // from 0:00 under a "Resumed" pill (the device report). Streaming loaded
    // fast enough to mask it.
    val fileLoadedGeneration by stateHolder.fileLoadedGeneration.collectAsState()
    LaunchedEffect(fileLoadedGeneration) {
        if (fileLoadedGeneration == 0 || !mpvInitialized) return@LaunchedEffect
        // D-247: successful load — cache playback is healthy again; re-arm it.
        bypassCacheNextRetry = false
        val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
        // D-192: Track WATCH_START (the video actually loaded + is ready to play).
        activityTracker.track(
            eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.WATCH_START,
            contentKey = watchKey.mainId,
            episodeKey = epKey,
            route = "watch",
            contentType = "anime",
        )
        // WP-B2: re-arm the 85% auto-mark on every FILE_LOADED.
        scope.launch {
            runCatching { watchProgressStore.resetAutoMarkSuppressed(epKey) }
                .onFailure { Logger.w(TAG) { "resetAutoMarkSuppressed failed: ${it.message}" } }
                .onSuccess { Logger.d(TAG) { "resetAutoMarkSuppressed: key=$epKey (re-armed)" } }
        }
        // ROUND 123 (R4): a FRESH file load resets the offer bookkeeping for
        // this episode — the device round: dismissing the card then replaying
        // the episode never re-showed it. The dismissal survives orientation
        // switches (no file load there) but expires on every reload (replay,
        // retry, quality switch) — a new playback attempt is a fresh chance.
        offeredNextKeys.remove(epKey)
        dismissedNextKeys.remove(epKey)
        // D-746: the shared resume policy — watched → fresh start, partial →
        // exact position −15s (round 121), pref-off → fresh. Once per episode
        // key, so quality switches never re-seek. The seek fires AFTER a real
        // FILE_LOADED (playback initialized — mpv accepts it), and is VERIFIED
        // with a 2-second poll — a dropped seek must never silently cost the
        // resume again (round 123: the event-constant fix made FILE_LOADED
        // trustworthy; this poll is the third layer).
        if (resumedEpisodeKeys[epKey] != true) {
            resumedEpisodeKeys[epKey] = true
            val decision = if (watchKey.startPosition > 0 &&
                stateHolder.currentEpisodeNumber.value == watchKey.episodeNumber
            ) {
                // The details-page resume hint (rare — most callers pass 0).
                com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.Decision.ResumeAtPosition(
                    watchKey.startPosition,
                )
            } else {
                com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.plan(
                    stored = runCatching { watchProgressStore.get(epKey) }.getOrNull(),
                    resumeEnabled = playerPreferences.resumeFromLastPosition,
                )
            }
            val resumeSec = (decision as? com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.Decision
                .ResumeAtPosition)?.positionSeconds ?: 0L
            val storedSnapshot = runCatching { watchProgressStore.get(epKey) }.getOrNull()
                ?.let { "${it.position}/${it.duration}" }
            Logger.i(TAG) {
                "resume: key=$epKey decision=$decision → resumeSec=$resumeSec (stored=$storedSnapshot)"
            }
            if (resumeSec > 0) {
                val genAtLoad = fileLoadedGeneration
                MPVLib.command(arrayOf("seek", resumeSec.toString(), "absolute"))
                resumePillSec = resumeSec
                Logger.i(TAG) { "resume: seek #1 → ${resumeSec}s absolute" }
                // Verify: poll the live position for up to 2s. If the seek was
                // dropped (rare post-fix), re-issue once mid-poll.
                var reissued = false
                var verified = false
                repeat(10) { attempt ->
                    delay(200L)
                    if (stateHolder.fileLoadedGeneration.value != genAtLoad) {
                        return@repeat
                    }
                    val posNow = stateHolder.position.value
                    Logger.d(TAG) { "resume: verify t=${(attempt + 1) * 200}ms pos=$posNow target=$resumeSec" }
                    if (posNow >= resumeSec - 5) {
                        verified = true
                        return@repeat
                    }
                    if (!reissued && attempt >= 2) {
                        reissued = true
                        Logger.w(TAG) { "resume: seek not applied (pos=$posNow) — re-issuing" }
                        MPVLib.command(arrayOf("seek", resumeSec.toString(), "absolute"))
                    }
                }
                if (!verified) {
                    Logger.w(TAG) {
                        "resume: NOT VERIFIED after 2s (pos=${stateHolder.position.value}, target=$resumeSec) — " +
                            "please capture logcat around 'resume:' lines"
                    }
                } else {
                    Logger.i(TAG) { "resume: OK (pos=${stateHolder.position.value})" }
                }
            }
        }
    }

    // D-746: the resume affordance auto-hides (Restart stays tappable meanwhile).
    LaunchedEffect(resumePillSec) {
        if (resumePillSec >= 0) {
            delay(com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.RESUME_PILL_DURATION_MS)
            resumePillSec = -1L
        }
    }

    // ── Round 120 (D-747): the Up Next offer trigger ───────────────────────────
    // Fires at 95% of the duration (the shared policy's fraction), or earlier at
    // the effective outro start (AniSkip's ed interval, else the manual stamp)
    // when one exists. Once per episode key; the countdown effect below handles
    // auto-play vs. button-only.
    LaunchedEffect(position, duration, currentEpisodeUrl, nextUpCardEnabled) {
        if (duration <= 0 || !nextUpCardEnabled) return@LaunchedEffect
        // ROUND 123 (R3): the sanity gates (a stale cross-file pairing right
        // after a switch; an error state) — kept.
        // ROUND 124 (R3): the 95%-fraction window is RETIRED — the device
        // round's order: "it should only appear 10 seconds before the episode
        // is actually ending". A pure last-10-seconds rule now; the
        // outro-based trigger returns once AniSkip carries real data (then
        // the card will ride the outro start).
        if (isSwitching ||
            !stateHolder.episodeReachedPlayback ||
            stateHolder.loadingState.value == PlayerLoadingState.ERROR
        ) {
            return@LaunchedEffect
        }
        val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
        // ROUND 124 (R3b): a dismissal is per-APPROACH — seek back out of the
        // end zone (below duration−15s) and both latches re-arm, so finishing
        // the episode after a dismiss-then-seek-back offers the card again
        // (the device report: dismiss → seek back → reach the end → no card).
        if (dismissedNextKeys[epKey] == true && duration - position > 15f) {
            dismissedNextKeys.remove(epKey)
            offeredNextKeys.remove(epKey)
            Logger.i(TAG) { "UpNext: latches re-armed (seeked back out of the end zone) key=$epKey" }
        }
        if (offeredNextKeys[epKey] == true) return@LaunchedEffect
        val secondsLeft = duration - position
        val reached = secondsLeft <= 10f
        if (reached || dismissedNextKeys[epKey] == true) {
            Logger.i(TAG) {
                "UpNext eval: pos=$position dur=$duration secondsLeft=$secondsLeft " +
                    "reached=$reached dismissed=${dismissedNextKeys[epKey] == true} key=$epKey"
            }
        }
        if (reached) offerNextEpisode(false)
    }

    // The Up Next countdown lives BELOW onEpisodeSwitch (declared later in this
    // composable — Kotlin requires declaration-before-capture).

    // Leaving an episode (switch/dismiss) hides the card for the old episode.
    // ROUND 122 (R4): prune the offer/dismiss latches to the current key only.
    LaunchedEffect(currentEpisodeUrl) {
        upNextVisible = false
        upNextCountdown = 0
        val currentKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
        offeredNextKeys.keys.removeAll { it != currentKey }
        dismissedNextKeys.keys.removeAll { it != currentKey }
    }

    // Reads from the state holder's currentResolvedVideosKey so the QualitySheet
    // shows the CURRENT episode's servers after a switch (not the old episode's).
    val resolvedServers = remember(currentResolvedVideosKey) {
        if (currentResolvedVideosKey.isNotBlank()) {
            ResolvedVideosRegistry.get(currentResolvedVideosKey) ?: emptyList()
        } else emptyList()
    }

    // Find the picked video in the registry (matches watchKey.videoUrl) to seed
    // the video title (for QualitySheet highlight) + external subtitle/audio
    // tracks (for sub-add on FILE_LOADED). This fixes the bug where
    // currentVideoTitle started as "" so the QualitySheet couldn't highlight the
    // currently-playing video.
    val initialPickedVideo: ResolverVideo? = remember(watchKey.videoUrl, watchKey.resolvedVideosKey) {
        if (watchKey.resolvedVideosKey.isNotBlank()) {
            ResolvedVideosRegistry.get(watchKey.resolvedVideosKey)
                ?.flatMap { it.audioVersions }
                ?.flatMap { it.videos }
                ?.firstOrNull { it.url == watchKey.videoUrl }
        } else null
    }

    var currentVideoTitle by remember { mutableStateOf(initialPickedVideo?.videoTitle ?: "") }
    var currentVideoUrl by remember { mutableStateOf(watchKey.videoUrl) }
    var currentVideoHeaders by remember { mutableStateOf(watchKey.videoHeaders) }
    var currentServerName by remember { mutableStateOf("") }
    var currentAudioVersion by remember { mutableStateOf("") }

    // ── Video caching identity (test-feature branch) ──
    // The stable cache identity for the CURRENTLY playing video. Updated at every
    // new-video site (init / quality switch / episode switch); reused by the retry
    // sites. CRITICAL: episode data comes from the LIVE episode state or the new
    // episode at switch time — NEVER from the frozen watchKey (otherwise ep N+1's
    // bytes would be filed under ep N's cache key = wrong-content replay corruption).
    // Null (no stable identity derivable) → caching skipped, playback goes direct.
    //
    // D-246 registry-miss fix: after process death the in-memory ResolvedVideosRegistry
    // is empty (initialPickedVideo == null) — the old code gave up here, silently
    // BYPASSING the cache on every replay in a new session (the user-reported "cached
    // videos still load from the network"). FALLBACK: recover the identity from a
    // prior cache entry (conservative: single matching-quality entry only — see
    // PlaybackCacheManager.knownIdentityFor).
    var currentCacheId by remember {
        mutableStateOf(
            initialPickedVideo?.let { pv ->
                buildCacheId(
                    mainId = watchKey.mainId,
                    animeTitle = watchKey.animeTitle,
                    episodeNumber = watchKey.episodeNumber,
                    episodeTitle = watchKey.episodeTitle,
                    sourceId = watchKey.sourceId,
                    videoTitle = pv.videoTitle,
                )
            } ?: run {
                // Registry miss (new app session / process death) — try the persisted identity.
                val recovered = if (watchKey.mainId.isNotBlank()) {
                    playbackCacheManager.knownIdentityFor(
                        mainId = watchKey.mainId,
                        episodeNumber = watchKey.episodeNumber,
                        sourceId = watchKey.sourceId,
                        quality = watchKey.quality,
                    )
                } else null
                if (recovered != null) {
                    Logger.i(TAG) {
                        "Cache identity recovered from a prior entry — replay will serve from cache"
                    }
                }
                recovered
            }
        )
    }

    /**
     * Wraps a video URL through the cache proxy (fail-open — returns the original URL on any
     * issue). Carries the current video's external track lists so cache entries can rebuild
     * a full WatchKey for tap-to-play from the settings screen.
     */
    var currentSubTracksSerialized by remember { mutableStateOf(watchKey.subtitleTracksSerialized) }
    var currentAudioTracksSerialized by remember { mutableStateOf(watchKey.audioTracksSerialized) }

    fun cachedUrl(url: String, headers: String): String =
        playbackCacheManager.playbackUrlFor(
            id = currentCacheId,
            upstreamUrl = url,
            headers = headers,
            subtitleTracksSerialized = currentSubTracksSerialized,
            audioTracksSerialized = currentAudioTracksSerialized,
        )

    // D-247: push playback progress (~1 Hz from the position/duration state) to the
    // cache manager — drives the progress-window policy (tee ceiling + window fill).
    LaunchedEffect(position, duration) {
        val key = currentCacheId?.cacheKey
        if (key != null && duration > 0 && position >= 0) {
            playbackCacheManager.onPlaybackProgress(key, position.toFloat(), duration.toFloat())
        }
    }

    // ── Auto-retry on error (non-switching errors only) ──
    // When an error occurs (NOT during switching — switching errors are real
    // failures), auto-retry the same URL once after 1.5s. This handles
    // transient failures (network hiccup, brief TLS renegotiation) silently.
    //
    // CRITICAL: Do NOT clear the error during auto-retry. The banner stays
    // visible so the user knows something is wrong. If the retry succeeds,
    // FILE_LOADED clears the error (banner disappears). If the retry fails,
    // the error stays (or gets updated with the new efEvent message).
    // The user explicitly said: "it should always show and it should not
    // automatically disappear out of the blue."
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && !stateHolder.isSwitching.value && !stateHolder.autoRetryAttempted) {
            Logger.i(TAG) { "Auto-retry: error occurred, retrying same URL in 1.5s..." }
            stateHolder.markAutoRetryAttempted()
            delay(1_500L)
            // Re-send loadfile WITHOUT clearing the error. The banner stays visible.
            // If this succeeds, FILE_LOADED clears the error. If it fails, efEvent
            // updates the error (or it stays as-is).
            try {
                val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                    else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                MPVLib.setOptionString("http-header-fields", headers)
                // D-247 cache-failure fallback: if this playback went through the
                // cache, the FIRST retry bypasses it entirely (direct network) — the
                // episode must never fail because of the cache (e.g. corrupt cached
                // bytes). The flag resets on the next successful READY load.
                if (!bypassCacheNextRetry && currentCacheId != null) {
                    Logger.w(TAG) { "Auto-retry: cache playback failed — retrying DIRECT (bypass cache)" }
                    bypassCacheNextRetry = true
                    MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                } else {
                    MPVLib.command(arrayOf("loadfile", cachedUrl(currentVideoUrl, headers), "replace"))
                }
                Logger.i(TAG) { "Auto-retry: loadfile re-sent (banner stays visible)" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Auto-retry failed" }
                stateHolder.setSwitchingError("Retry failed: ${e.message}")
            }
        }
    }

    // ── D-408 (round 32): the REMEMBERED-SUBTITLE PRE-APPLY ─────────────────
    // The per-series memory (PlayerPreferences.getPreferredSubtitleTrack):
    // "" = nothing remembered (default behavior); "off" = the user explicitly
    // chose Off; anything else = the selected track's display label. Invoked
    // from the observer's onTracksLoaded hook (fired after EVERY track-list
    // reload — file loads, the 5s safety reloads, external sub-adds), so the
    // remembered subtitle pre-applies on open, on episode switch, and once
    // the external tracks finish loading. Idempotent: it only sets sid when
    // the current selection differs, and the user's own sheet selections
    // update the memory immediately (no fighting).
    val applyRememberedSubtitle: (List<com.confused.anikuta.core.player.VideoTrack>) -> Unit =
        { subs ->
            val mainId = watchKey.mainId
            val remembered = if (mainId.isNotBlank()) {
                playerPreferences.getPreferredSubtitleTrack(mainId)
            } else ""
            when {
                remembered.isBlank() -> Unit
                remembered == "off" -> {
                    if (stateHolder.currentSubtitleTrack.value > 0) {
                        runCatching { MPVLib.setPropertyString("sid", "no") }
                        Logger.i(TAG) { "Remembered subtitle: OFF (pre-applied)" }
                    }
                }
                else -> {
                    val match = subs.firstOrNull {
                        it.name == remembered ||
                            it.lang?.equals(remembered, ignoreCase = true) == true
                    }
                    if (match != null && stateHolder.currentSubtitleTrack.value != match.id) {
                        runCatching { MPVLib.setPropertyInt("sid", match.id) }
                        Logger.i(TAG) {
                            "Remembered subtitle: '$remembered' → track id ${match.id} (pre-applied)"
                        }
                    }
                }
            }
        }

    // ── Init MPV + load video (once) ──
    val initMpv: (AnikutaMPVView) -> Unit = remember {
        { view ->
            if (!mpvInitialized) {
                mpvInitialized = true
                val obs = PlayerObserver(stateHolder, subtitleEngine)
                obs.mpvView = view  // Wire so observer can call loadTracks() on FILE_LOADED
                observer = obs      // Store reference so switch handlers can set pending tracks
                // D-408 (round 32): the remembered-subtitle pre-apply hook —
                // the observer fires it after every track-list reload.
                obs.onTracksLoaded = applyRememberedSubtitle

                // D-747: the EOF hook — the file reached its natural end. Two
                // jobs: (1) the explicit completion write (save() with the final
                // position runs the store's 85% auto-mark; a suppressed episode
                // stays un-marked — the user's un-mark wins), closing the gap
                // where an EOF inside the 10s periodic-save window could miss
                // completion; (2) force the Up Next card (a straight seek to the
                // end can beat the 95% poll).
                obs.onEofReached = {
                    scope.launch {
                        val dur = stateHolder.duration.value
                        val epUrl = stateHolder.currentEpisodeUrl.value
                        // ROUND 122 (R2) + ROUND 123 (R2): the reached-playback
                        // AND not-error gates — an error-state EOF (stale
                        // metrics) or a demuxer EOF from a dead stream must
                        // never write the end position under this key.
                        val writeAllowed = dur > 0 && epUrl.isNotBlank() &&
                            stateHolder.episodeReachedPlayback &&
                            stateHolder.loadingState.value != PlayerLoadingState.ERROR
                        Logger.i(TAG) {
                            "EOF hook: dur=$dur url=${epUrl.take(40)} reachedPlayback=" +
                                "${stateHolder.episodeReachedPlayback} loading=${stateHolder.loadingState.value} " +
                                "→ write=$writeAllowed"
                        }
                        if (writeAllowed) {
                            val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
                            runCatching {
                                watchProgressStore.save(
                                    epKey,
                                    WatchProgress(
                                        episodeKey = epKey,
                                        mainId = watchKey.mainId.ifBlank { null },
                                        position = dur.toLong(),
                                        duration = dur.toLong(),
                                        completed = false,
                                        completedAt = null,
                                        lastWatchedAt = System.currentTimeMillis(),
                                    ),
                                )
                            }.onSuccess {
                                Logger.i(TAG) { "D-747: EOF completion write OK: $epKey" }
                            }.onFailure {
                                Logger.w(TAG) { "D-747: EOF completion write failed: ${it.message}" }
                            }
                        }
                        // D-747: the EOF offer honors the card preference —
                        // the 95% poll already does; a disabled card must not
                        // reappear at the end either (and `force` must not
                        // resurrect a card dismissed at 95%).
                        // ROUND 123 (R2): the SAME gates as the write — a dead
                        // stream's demuxer EOF must not summon the Up Next card
                        // (the device round: the card appeared over a failure).
                        // ROUND 124 (R1): dur>0 too — a fresh file with no
                        // observed duration cannot be at its natural end.
                        val offerAllowed = nextUpCardEnabled && dur > 0 &&
                            stateHolder.episodeReachedPlayback &&
                            stateHolder.loadingState.value != PlayerLoadingState.ERROR
                        Logger.i(TAG) { "EOF hook: offerAllowed=$offerAllowed" }
                        if (offerAllowed) offerNextEpisode(true)
                    }
                }

                // Set pending external subtitle/audio tracks.
                // PRIMARY source: WatchKey.subtitleTracksSerialized (always available,
                // passed directly from DetailsScreen — no registry lookup needed).
                // FALLBACK: initialPickedVideo from ResolvedVideosRegistry (for
                // cases where the serialized tracks are empty but the registry has them).
                val keySubs = watchKey.parseSubtitleTracks()
                val keyAudios = watchKey.parseAudioTracks()
                if (keySubs.isNotEmpty() || keyAudios.isNotEmpty()) {
                    obs.pendingSubtitleTracks = keySubs
                    obs.pendingAudioTracks = keyAudios
                    obs.trackHeaders = currentVideoHeaders
                    Logger.i(TAG) { "Pending external tracks (from WatchKey): ${keySubs.size} subs, ${keyAudios.size} audio" }
                } else {
                    initialPickedVideo?.let { pv ->
                        obs.pendingSubtitleTracks = pv.subtitleTracks.map { Pair(it.url, it.lang) }
                        obs.pendingAudioTracks = pv.audioTracks.map { Pair(it.url, it.lang) }
                        obs.trackHeaders = pv.videoHeaders ?: ""
                        Logger.i(TAG) { "Pending external tracks (from registry): ${pv.subtitleTracks.size} subs, ${pv.audioTracks.size} audio" }
                    }
                }

                PlayerInitializer.initialize(context, view)

                // Register observer — route all MPV callbacks through PlayerObserver
                // for proper severity routing, HTTP error capture, and event handling.
                val logObs = object : `is`.xyz.mpv.MPVLib.LogObserver {
                    override fun logMessage(prefix: String, level: Int, text: String) {
                        obs.onLogMessage(prefix, level, text)
                    }
                }
                val eventObs = object : `is`.xyz.mpv.MPVLib.EventObserver {
                    override fun event(eventId: Int) { obs.onEvent(eventId) }
                    override fun eventProperty(property: String) {}
                    override fun eventProperty(property: String, value: Long) { obs.onProperty(property, value.toString()) }
                    override fun eventProperty(property: String, value: Boolean) { obs.onProperty(property, if (value) "yes" else "no") }
                    override fun eventProperty(property: String, value: String) { obs.onProperty(property, value) }
                    override fun eventProperty(property: String, value: Double) { obs.onProperty(property, value.toString()) }
                    override fun efEvent(err: String?) {
                        obs.onEfEvent(err)
                    }
                }
                MPVLib.addLogObserver(logObs)
                MPVLib.addObserver(eventObs)
                // Store refs so onDispose can remove them (prevents observer
                // accumulation across screen entries).
                logObserverRef = logObs
                eventObserverRef = eventObs

                // CRITICAL: Set HTTP headers BEFORE loadfile.
                // For localhost proxy URLs (AniKotoS), don't set upstream headers.
                // For content:// URIs (downloaded files), convert to fd:// — MPV can't
                // open SAF content:// URIs directly (it needs a file descriptor).
                val isLocalhost = currentVideoUrl.contains("127.0.0.1") ||
                    currentVideoUrl.contains("localhost")
                val isContentUri = currentVideoUrl.startsWith("content://")
                var loadUrl = currentVideoUrl
                try {
                    if (isContentUri) {
                        // D.FIX: Convert content:// URI to fd:// for MPV.
                        val uri = android.net.Uri.parse(currentVideoUrl)
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            loadUrl = "fd://${pfd.fd}"
                            Logger.i(TAG) { "Converted content:// URI to fd:// (fd=${pfd.fd})" }
                            // Keep pfd open — MPV reads from the fd. It will be closed
                            // when the player disposes.
                            mpvParcelFileDescriptor = pfd
                        } else {
                            Logger.e(TAG) { "Failed to open file descriptor for content:// URI" }
                        }
                    }
                    // D-199: ALWAYS set http-header-fields, even for localhost proxy URLs.
                    // The extension's local proxy server (HttpServer/NanoHTTPD) forwards the
                    // inbound User-Agent to the upstream CDN. If we don't set the extension-
                    // provided headers (which include the correct User-Agent), MPV uses its
                    // default "libmpv" UA → the CDN returns 403 Forbidden.
                    if (!isContentUri) {
                        val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                            else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        MPVLib.setOptionString("http-header-fields", headers)
                        Logger.i(TAG) { "=== MPV LOADFILE (${if (isLocalhost) "localhost proxy" else "network"}) ===" }
                        Logger.i(TAG) { "URL: $loadUrl" }
                        Logger.i(TAG) { "Headers: $headers" }
                        Logger.i(TAG) { "Video title: $currentVideoTitle" }
                    } else {
                        Logger.i(TAG) { "=== MPV LOADFILE (offline fd://) ===" }
                        Logger.i(TAG) { "loadUrl: $loadUrl" }
                        Logger.i(TAG) { "originalUrl: $currentVideoUrl" }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Failed to set http-header-fields: ${e.message}" }
                }

                // ROUND 124 (R1): the INITIAL open never reset the holder —
                // the previous playback's pinned end-of-file metrics + the
                // reached-playback gate survived into the new file (the device
                // logcat: the EOF hook + the Up Next offer fired at
                // FILE_LOADED+2ms with the OLD pos/dur, then the periodic save
                // pinned the end + auto-marked the episode watched). Every
                // switch path already resets; the initial open now does too.
                stateHolder.resetPlaybackMetrics()
                Logger.i(TAG) { "Initial load — playback metrics reset" }
                Logger.i(TAG) { "Sending loadfile command to MPV..." }
                // D.FIX: For fd:// URLs (offline playback), delay 500ms so the
                // SurfaceView's surfaceCreated fires first. Without this delay, MPV
                // tries to initialize vo_android_init before the surface is ready →
                // assertion "vo->opts->WinID != 0" fails → SIGABRT crash.
                // (Ported from the old project's PlayerInitializer.loadVideo.)
                if (loadUrl.startsWith("fd://")) {
                    Logger.i(TAG) { "Offline fd:// URL — delaying loadfile 500ms for surface readiness" }
                    mpvView?.postDelayed({
                        try {
                            MPVLib.command(arrayOf("loadfile", loadUrl, "replace"))
                            MPVLib.setPropertyBoolean("pause", false)
                            // D-749: a fresh load un-pauses — clear the user
                            // flag so the watchdog re-arms for this episode.
                            stateHolder.updateUserPaused(false)
                            Logger.i(TAG) { "loadfile command sent (after 500ms delay). Waiting for FILE_LOADED event..." }
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Failed to load offline video (fd://)" }
                        }
                    }, 500)
                } else {
                    // Video caching: wrap the network URL through the cache proxy
                    // (fail-open — original URL when caching doesn't apply). The
                    // headers were set above (D-199) and are replicated by the proxy.
                    val loadHeaders = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                        else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                    MPVLib.command(arrayOf("loadfile", cachedUrl(loadUrl, loadHeaders), "replace"))
                    MPVLib.setPropertyBoolean("pause", false)
                    // D-749: see the offline branch — the watchdog re-arms on load.
                    stateHolder.updateUserPaused(false)
                    Logger.i(TAG) { "loadfile command sent. Waiting for FILE_LOADED event..." }
                }
            }
        }
    }

    // ── Destroy MPV on dispose + save final progress + remove observers ──
    // D-749: the exit-save scope — independent of the composition scope. The old
    // final save launched into `scope` (rememberCoroutineScope), which is
    // cancelled as the composable leaves composition — the suspend DB write
    // raced that cancellation and could be dropped. The write runs under
    // NonCancellable so the cancelling effect below (declared FIRST → disposed
    // LAST, after the saving effect) can tear the scope down leak-free.
    val exitSaveScope = remember {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
    }
    DisposableEffect(Unit) {
        onDispose { exitSaveScope.cancel() }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Save final progress before destroying MPV.
            val pos = stateHolder.position.value
            val dur = stateHolder.duration.value
            val epUrl = stateHolder.currentEpisodeUrl.value
            // ROUND 121: the same recording floor as the periodic save — a
            // sub-minute exit is not progress. ROUND 122 (R2): the same
            // reached-playback + not-error guards — a failed load must not
            // persist the stale end-of-file position on exit.
            if (dur > 0 && epUrl.isNotBlank() &&
                stateHolder.episodeReachedPlayback &&
                stateHolder.loadingState.value != PlayerLoadingState.ERROR &&
                com.confused.anikuta.core.watchprogress.PlaybackResumePolicy
                    .shouldPersist(pos.toLong(), dur.toLong())
            ) {
                val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
                val progress = WatchProgress(
                    episodeKey = epKey,
                    mainId = watchKey.mainId.ifBlank { null },
                    position = pos.toLong(),
                    duration = dur.toLong(),
                    completed = false,
                    completedAt = null,
                    lastWatchedAt = System.currentTimeMillis(),
                )
                exitSaveScope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { watchProgressStore.save(epKey, progress) }
                            .onFailure { Logger.w(TAG) { "Final progress save failed: ${it.message}" } }
                    }
                }
            }
            // CRITICAL: Remove MPVLib observers BEFORE destroying the view.
            // Without this, observers accumulate across screen entries → every
            // event fires N times (N = number of entries). This was causing
            // 4x event duplication in the logs.
            logObserverRef?.let { runCatching { MPVLib.removeLogObserver(it) } }
            eventObserverRef?.let { runCatching { MPVLib.removeObserver(it) } }
            mpvView?.let { view ->
                runCatching { MPVLib.command(arrayOf("stop")) }
                runCatching { view.destroy() }
            }
            // Clean up downloaded subtitle temp files.
            runCatching { subtitleEngine.cleanupAll() }
            // D.FIX: Close the ParcelFileDescriptor for offline playback.
            runCatching { mpvParcelFileDescriptor?.close() }
            mpvParcelFileDescriptor = null
            Logger.i(TAG) { "MPV destroyed + observers removed + subtitles cleaned + fd closed" }
        }
    }

    // ── Layout ──
    // Sheet visibility state (shared between minimized + fullscreen)
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    // ── D-408 (round 32): the episode's ON-DISK subtitle files for the sheet's
    // "Available in storage" section ──
    // Resolved whenever the subtitle sheet opens (and on an episode change
    // while open) through the SAME layered resolver every playback path uses.
    // Only the files NOT already loaded as MPV tracks are kept (matched by the
    // track name/lang — the sub-add'ed external tracks carry the resolver's
    // label), so the section is the belt-and-braces fallback: when the auto
    // pending-track path delivered everything, it stays empty; when anything
    // upstream dropped them, the user still sees + can load them on tap.
    var storageSubtitles by remember {
        mutableStateOf<List<com.confused.anikuta.core.download.ResolvedSubtitleTrack>>(emptyList())
    }
    LaunchedEffect(showSubtitleSheet, currentEpisodeUrl) {
        if (showSubtitleSheet && watchKey.mainId.isNotBlank()) {
            val epNum = stateHolder.currentEpisodeNumber.value.toInt()
            if (epNum > 0) {
                val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        downloadManager.resolveSubtitleTracks(watchKey.mainId, epNum)
                    }.getOrNull()
                }.orEmpty()
                val loaded = stateHolder.subtitleTracks.value
                storageSubtitles = resolved.filter { r ->
                    loaded.none {
                        it.name == r.label || it.lang?.equals(r.label, ignoreCase = true) == true
                    }
                }
                Logger.i(TAG) {
                    "Storage subtitles — resolved ${resolved.size}, not-yet-loaded " +
                        "${storageSubtitles.size} [labels=${storageSubtitles.map { it.label }}]"
                }
            }
        }
    }

    // D-408 (round 32): loading one storage-row tap — the PROVEN manual-import
    // load path (stage → sub-add "select" → refresh tracks), so the picked
    // file activates immediately and appears in the selector exactly like the
    // manually-imported ones do.
    val onLoadStorageSubtitle: (com.confused.anikuta.core.download.ResolvedSubtitleTrack) -> Unit =
        { track ->
            showSubtitleSheet = false
            scope.launch {
                val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        subtitleEngine.stageSingle(
                            com.confused.anikuta.core.player.subtitles.SubtitleDownloadRequest(
                                url = track.uri,
                                lang = track.label,
                                headers = "",
                            ),
                        )
                    }.getOrNull()
                }
                if (staged != null) {
                    runCatching {
                        MPVLib.command(
                            arrayOf("sub-add", staged.absolutePath, "select", "", track.label),
                        )
                    }.onSuccess {
                        Logger.i(TAG) { "Storage subtitle loaded + selected: '${track.label}'" }
                        // D-408: remember the pick for this series (pre-applies
                        // on the next open).
                        if (watchKey.mainId.isNotBlank()) {
                            playerPreferences.setPreferredSubtitleTrack(watchKey.mainId, track.label)
                        }
                        kotlinx.coroutines.delay(600L)
                        runCatching {
                            mpvView?.let { v ->
                                val (subs, audio) = v.loadTracks()
                                stateHolder.updateTracks(subs, audio)
                            }
                        }
                        com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                            "Subtitle added",
                            com.confused.anikuta.core.designsystem.component.toast.AppToastTone.SUCCESS,
                        )
                    }.onFailure {
                        Logger.w(TAG) { "Storage subtitle sub-add failed: ${it.message}" }
                        com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                            "Could not load the subtitle",
                            com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                        )
                    }
                } else {
                    Logger.w(TAG) { "Storage subtitle staging failed: '${track.label}'" }
                    com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                        "Could not read the subtitle file",
                        com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                    )
                }
            }
        }

    // D.FIX: On-demand resolve for the QualitySheet when playing offline.
    // When the user opens the QualitySheet + resolvedServers is empty (offline
    // playback), resolve the episode on-demand so the user can switch to a
    // live server/quality.
    var isResolvingOnDemand by remember { mutableStateOf(false) }
    // D.FIX: videoResolver + extensionManager are already declared at the top of
    // WatchScreen (lines 147-148) — no need to re-declare here.

    LaunchedEffect(showQualitySheet) {
        if (showQualitySheet && resolvedServers.isEmpty() && !isResolvingOnDemand) {
            // QualitySheet opened but no servers available — resolve on-demand.
            isResolvingOnDemand = true
            Logger.i(TAG) { "QualitySheet opened with no servers — resolving on-demand" }
            val sourceId = watchKey.sourceId
            val source = extensionManager.getSource(sourceId) as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            if (source != null) {
                // Build a minimal SEpisode from the watch key.
                val episode = eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
                    url = watchKey.episodeUrl
                    episode_number = watchKey.episodeNumber
                    name = watchKey.episodeTitle
                }
                try {
                    videoResolver.resolve(source, episode).collect { state ->
                        when (state) {
                            is com.confused.anikuta.core.videoresolver.ResolverState.Success -> {
                                val servers = videoResolver.buildServers(state.rawEntries, source.name)
                                if (servers.isNotEmpty()) {
                                    val key = com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry.put(servers)
                                    stateHolder.updateResolvedVideosKey(key)
                                    Logger.i(TAG) { "On-demand resolve: got ${servers.size} servers" }
                                }
                            }
                            is com.confused.anikuta.core.videoresolver.ResolverState.Error -> {
                                Logger.w(TAG) { "On-demand resolve failed: ${state.message}" }
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "On-demand resolve exception" }
                }
            }
            isResolvingOnDemand = false
        }
    }

    // ── Retry handler — re-load the current video URL ──
    val onRetry: () -> Unit = {
        Logger.i(TAG) { "=== RETRY: Re-loading video ===" }
        Logger.i(TAG) { "URL: $currentVideoUrl" }
        Logger.i(TAG) { "Headers: ${currentVideoHeaders.take(120)}" }
        stateHolder.setSwitching(true)
        // ROUND 122 (R2): zero the stale metrics — a retried failure must not
        // leave the old file's end-of-file position on the seek bar (or in a
        // save: the reached-playback gate is already re-armed by setSwitching).
        stateHolder.resetPlaybackMetrics()
        try {
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            // D-247: after a cache-failure we keep retrying DIRECT until a successful
            // load re-arms the cache (bypassCacheNextRetry resets on READY).
            if (bypassCacheNextRetry) {
                MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
            } else {
                MPVLib.command(arrayOf("loadfile", cachedUrl(currentVideoUrl, headers), "replace"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Retry failed" }
            // Use setSwitchingError so the error is ALWAYS shown (not suppressed
            // by the isSwitching flag) and the spinner stops.
            stateHolder.setSwitchingError("Retry failed: ${e.message}")
        }
    }

    // ── Dismiss error handler — just clear the error (close button) ──
    val onDismissError: () -> Unit = {
        Logger.i(TAG) { "Error dismissed by user (close button)" }
        stateHolder.updateError(null)
    }

    // ── Quality switch handler — re-loadfile with new video ──
    val onQualitySelected: (ResolverVideo) -> Unit = { video ->
        Logger.i(TAG) { "=== QUALITY SWITCH ===" }
        Logger.i(TAG) { "New video: ${video.quality} (${video.url})" }
        Logger.i(TAG) { "New headers: ${(video.videoHeaders ?: "").take(120)}" }
        currentVideoUrl = video.url
        currentVideoTitle = video.videoTitle
        currentVideoHeaders = video.videoHeaders ?: ""
        // Set pending external tracks + headers on the observer so they load on
        // the next FILE_LOADED. This fixes the bug where external subtitles
        // were lost on quality switch.
        observer?.let { obs ->
            obs.pendingSubtitleTracks = video.subtitleTracks.map { Pair(it.url, it.lang) }
            obs.pendingAudioTracks = video.audioTracks.map { Pair(it.url, it.lang) }
            obs.trackHeaders = video.videoHeaders ?: ""
        }
        // Set switching flag so efEvent from old file doesn't show a spurious error.
        stateHolder.setSwitching(true)
        // ROUND 122 (R2): zero the stale metrics (the failed-switch report: the
        // bar pinned at the old file's end) — setSwitching re-armed the
        // reached-playback gate; this clears the stale bar values too.
        stateHolder.resetPlaybackMetrics()
        // ROUND 122 (R8): keep the continuity pin fresh after a manual pick.
        serverKeyFromVideoTitle(video.videoTitle).split('|').let { p ->
            currentServerName = p.getOrElse(0) { "" }
            currentAudioVersion = p.getOrElse(1) { "" }
        }
        // Video caching: new identity for the new video (live episode state — NOT the frozen watchKey).
        currentCacheId = buildCacheId(
            mainId = watchKey.mainId,
            animeTitle = watchKey.animeTitle,
            episodeNumber = stateHolder.currentEpisodeNumber.value,
            episodeTitle = stateHolder.currentEpisodeTitle.value,
            sourceId = watchKey.sourceId,
            videoTitle = video.videoTitle,
        )
        // Video caching: carry the new video's external tracks (for tap-to-play replay).
        currentSubTracksSerialized = serializeTracks(video.subtitleTracks.map { it.url to it.lang })
        currentAudioTracksSerialized = serializeTracks(video.audioTracks.map { it.url to it.lang })
        // D-247: fresh video → re-arm cache playback for retries.
        bypassCacheNextRetry = false
        try {
            // D-199: Always set headers (even for localhost proxy — see initial loadfile comment).
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            MPVLib.command(arrayOf("loadfile", cachedUrl(video.url, headers), "replace"))
            // ROUND 124 (R2): every loadfile means autoplay — force pause=no
            // (the init/switch paths already do; a paused leftover survived
            // the replace and the watchdog read it as a dead server).
            MPVLib.setPropertyBoolean("pause", false)
            stateHolder.updateUserPaused(false)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to switch quality" }
            stateHolder.setSwitchingError("Failed to switch: ${e.message}")
        }
    }

    // ── Subtitle track selection handler ──
    val onSubtitleSelected: (Int) -> Unit = { trackId ->
        try {
            if (trackId <= 0) {
                MPVLib.setPropertyString("sid", "no")
                // D-408 (round 32): remember the explicit Off for this series
                // ("it will remember the state of the tool").
                if (watchKey.mainId.isNotBlank()) {
                    playerPreferences.setPreferredSubtitleTrack(watchKey.mainId, "off")
                }
            } else {
                MPVLib.setPropertyInt("sid", trackId)
                // D-408 (round 32): remember the picked track's label for this
                // series — it pre-applies on the next open / episode switch.
                val label = stateHolder.subtitleTracks.value
                    .firstOrNull { it.id == trackId }?.name
                if (!label.isNullOrBlank() && watchKey.mainId.isNotBlank()) {
                    playerPreferences.setPreferredSubtitleTrack(watchKey.mainId, label)
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to set subtitle track: ${e.message}" }
        }
    }

    // ── Speed selection handler — applies live via setPropertyDouble ──
    val onSpeedSelected: (Float) -> Unit = { speed ->
        Logger.i(TAG) { "Speed → $speed" }
        stateHolder.updatePlaybackSpeed(speed)
        // Round 120 (D-746): persist the pick — the MPV view applies
        // playerPreferences.playbackSpeed at init, but nothing ever WROTE it
        // back, so the choice silently reset to 1x on the next session.
        playerPreferences.playbackSpeed = speed
        try { mpvView?.playbackSpeed = speed }
        catch (e: Exception) { Logger.w(TAG) { "Failed to set speed: ${e.message}" } }
    }

    // ── Episode switch handler — re-resolve and load a different episode ──
    val onEpisodeSwitch: (SimpleEpisode) -> Unit = { ep ->
        Logger.i(TAG) { "=== EPISODE SWITCH ===" }
        Logger.i(TAG) { "New episode: ${ep.name} (num: ${ep.episodeNumber}, url: ${ep.url})" }

        // D-749: supersede any in-flight switch — a rapid A→B→C tap sequence
        // used to run concurrent resolve collectors whose loadfiles raced (the
        // slowest resolve won = the WRONG episode played). Cancelling the job
        // kills an in-flight resolve AND any pending fd:// surface-wait; the
        // generation guard below discards results that slip through the cancel
        // window (collector completion between cancel and cleanup).
        switchJob?.cancel()
        switchJob = null
        val generation = ++switchGeneration

        // ROUND 122 (R8): capture the CONTINUITY PIN from the episode being
        // left — server name + audio flavor + quality — BEFORE anything clears
        // the current episode's registry key. The resolve below then re-picks
        // the same server/audio/quality for the NEW episode (the user's order:
        // "it should pick the server and the resolution which was used for the
        // current episode if available… and maintain the exact same language").
        val continuityPin: EpisodePin? = run {
            val servers = ResolvedVideosRegistry.get(stateHolder.currentResolvedVideosKey.value).orEmpty()
            val located = servers.asSequence()
                .flatMap { s ->
                    s.audioVersions.flatMap { av -> av.videos.map { Triple(s.name, av.label, it) } }
                }
                .firstOrNull { it.third.url == currentVideoUrl || it.third.videoTitle == currentVideoTitle }
            when {
                located != null -> EpisodePin(located.first, located.second, located.third.quality)
                currentVideoTitle.isNotBlank() -> {
                    // Process-death fallback: the title carries the pin.
                    val p = serverKeyFromVideoTitle(currentVideoTitle).split('|')
                    EpisodePin(p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, p.getOrElse(2) { watchKey.quality })
                }
                else -> {
                    Logger.w(TAG) { "Watch continuity: no pin (blank title/registry) — quality-only via WatchKey" }
                    EpisodePin("", "", watchKey.quality)
                }
            }
        }
        currentServerName = continuityPin?.server ?: ""
        currentAudioVersion = continuityPin?.audio ?: ""
        Logger.i(TAG) {
            "Watch continuity: pin captured server='${continuityPin?.server}' " +
                "audio='${continuityPin?.audio}' quality='${continuityPin?.quality}'"
        }

        // WP-B4: Save the OLD episode's progress BEFORE switching (was only saved
        // on the 10s timer + onDispose — if the user switched within 10s, up to
        // 10s of progress was lost). Reads the current state before
        // updateCurrentEpisode overwrites it with the new episode's data.
        val oldPos = stateHolder.position.value
        val oldDur = stateHolder.duration.value
        val oldEpUrl = stateHolder.currentEpisodeUrl.value
        val oldEpNum = stateHolder.currentEpisodeNumber.value
        // ROUND 123 (R2): the not-error guard — a switch made from the error
        // state must not save the stale high position under the old key.
        if (oldDur > 0 && oldEpUrl.isNotBlank() && watchKey.mainId.isNotBlank() &&
            stateHolder.episodeReachedPlayback &&
            stateHolder.loadingState.value != PlayerLoadingState.ERROR &&
            com.confused.anikuta.core.watchprogress.PlaybackResumePolicy
                .shouldPersist(oldPos.toLong(), oldDur.toLong())
        ) {
            val oldKey = buildEpisodeKey(watchKey.mainId, oldEpNum)
            val oldProgress = WatchProgress(
                episodeKey = oldKey,
                mainId = watchKey.mainId,
                position = oldPos.toLong(),
                duration = oldDur.toLong(),
                completed = false,
                completedAt = null,
                lastWatchedAt = System.currentTimeMillis(),
            )
            scope.launch {
                runCatching { watchProgressStore.save(oldKey, oldProgress) }
                    .onFailure { Logger.w(TAG) { "Pre-switch progress save failed: ${it.message}" } }
                    .onSuccess { Logger.d(TAG) { "Pre-switch progress saved: key=$oldKey pos=${oldPos}s dur=${oldDur}s" } }
            }
        }

        // D.FIX: Check if the target episode is downloaded — if so, play it offline
        // (fd://) instead of trying to resolve from the network source. This is
        // critical when playing from the downloads page, and also improves the
        // experience when switching between downloaded episodes from the details page.
        val currentMainId = downloadManager.getDownloadedEpisodes().value
            .firstOrNull { it.videoUri == watchKey.videoUrl }
            ?.content?.mainId
        val offlineUri = if (currentMainId != null) {
            downloadManager.getDownloadedEpisodeUri(currentMainId, ep.url)
        } else null

        if (currentMainId != null && offlineUri != null) {
            // ── Offline playback path (downloaded episode) ──
            // The non-null binding of the (String?) main id — the branch
            // condition proves it, and the explicit local keeps the
            // `resolveSubtitleTracks(mainId: String, …)` call inside the
            // scope.launch lambda off smart-cast propagation entirely.
            val downloadedMainId: String = currentMainId

            Logger.i(TAG) { "Episode switch — episode is DOWNLOADED, playing offline (fd://)" }
            // Video caching: offline playback never goes through the proxy.
            currentCacheId = null
            stateHolder.setSwitching(true)
            stateHolder.setSwitchingEpisode(true)
            stateHolder.updateCurrentEpisode(
                url = ep.url,
                number = ep.episodeNumber,
                title = ep.name,
                resolvedVideosKey = "",
            )
            // D-749: the old file's metrics must not bleed into the new episode
            // (the 10s save would write the OLD position under the NEW key).
            stateHolder.resetPlaybackMetrics()
            switchJob = scope.launch {
                try {
                    // D-749: bail out if a newer switch superseded this one —
                    // before we close the OLD pfd (which would kill the newer
                    // switch's file descriptor) and before the loadfile.
                    if (generation != switchGeneration) {
                        Logger.i(TAG) { "Episode switch (offline) superseded — aborting" }
                        return@launch
                    }
                    // Close the old ParcelFileDescriptor before opening a new one.
                    runCatching { mpvParcelFileDescriptor?.close() }
                    mpvParcelFileDescriptor = null

                    // Convert content:// URI to fd:// for MPV.
                    val uri = android.net.Uri.parse(offlineUri)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val fdUrl = "fd://${pfd.fd}"
                        mpvParcelFileDescriptor = pfd

                        // D-407 (round 31): the SHARED subtitle resolution for
                        // the switched-to episode — the same ONE answer the
                        // details-page + downloads-page hand-offs use (DB
                        // `subtitleUris` first, the episode's dedicated
                        // subtitles/ folder scan as the fallback, REAL labels
                        // from the filenames). The old inline copy labeled
                        // everything "Subtitle N" and had no fallback — the
                        // round-31 report's "does not show the subtitles for
                        // the downloaded episodes".
                        val resolvedSubs = kotlinx.coroutines.withContext(
                            kotlinx.coroutines.Dispatchers.IO,
                        ) {
                            downloadManager.resolveSubtitleTracks(
                                mainId = downloadedMainId,
                                episodeNumber = ep.episodeNumber.toInt(),
                            )
                        }
                        if (resolvedSubs.isNotEmpty()) {
                            observer?.let { obs ->
                                obs.pendingSubtitleTracks = resolvedSubs.map {
                                    it.uri to it.label
                                }
                                obs.trackHeaders = ""
                            }
                            Logger.i(TAG) {
                                "Episode switch — set ${resolvedSubs.size} pending subtitle " +
                                    "track(s) for offline episode " +
                                    "[labels=${resolvedSubs.map { it.label }}]"
                            }
                        } else {
                            Logger.w(TAG) { "Episode switch — no subtitles resolved for downloaded episode (mainId=$currentMainId, epUrl=${ep.url})" }
                        }

                        // D-749: for fd:// URLs, wait 500ms for surface readiness
                        // (same rationale as the initial load) — INSIDE the switch
                        // job, as a plain coroutine delay. The old postDelayed
                        // closure was untracked: two rapid offline switches let
                        // switch #2 close the pfd that switch #1's pending
                        // closure then loaded — a loadfile on a dead descriptor.
                        delay(500L)
                        if (generation != switchGeneration) {
                            Logger.i(TAG) { "Episode switch (offline) superseded during surface wait — aborting" }
                            return@launch
                        }
                        try {
                            MPVLib.command(arrayOf("loadfile", fdUrl, "replace"))
                            MPVLib.setPropertyBoolean("pause", false)
                            // D-749: fresh episode, fresh play state — the
                            // watchdog re-arms (no stale userPaused carry-over).
                            stateHolder.updateUserPaused(false)
                            Logger.i(TAG) { "Episode switch — loadfile sent for offline fd:// (fd=${pfd.fd})" }
                        } catch (e: Exception) {
                            Logger.e(TAG, e) { "Episode switch — offline loadfile failed" }
                            stateHolder.setSwitchingError("Offline playback failed: ${e.message}")
                        }
                    } else {
                        Logger.e(TAG) { "Episode switch — failed to open file descriptor for offline URI" }
                        stateHolder.setSwitchingError("Failed to open downloaded episode file")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "Episode switch — offline playback failed" }
                    stateHolder.setSwitchingError("Offline playback failed: ${e.message}")
                }
            }
        } else {
            // ── Network resolution path (existing code) ──
            val source = if (watchKey.sourceId != 0L) {
                extensionManager.getSource(watchKey.sourceId) as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            } else null

            if (source == null) {
                Logger.w(TAG) { "Cannot switch episode — source not available (sourceId=${watchKey.sourceId})" }
                stateHolder.setSwitchingError("Cannot switch episode: source not available")
            } else {
            // CRITICAL: Do NOT call MPVLib.command(arrayOf("stop")) before switch.
            // The old project does NOT stop before switching — it just calls loadfile
            // with "replace" mode, which replaces the current file (stopping the old
            // video automatically). Calling "stop" first may cause the AniKotoS
            // extension to detect player disconnection and kill its local proxy.
            // The proxy dies 4ms after starting → all video URLs point to dead proxy.
            // By NOT calling stop, the player stays connected → proxy stays alive.
            // The old video stops when loadfile("replace") is sent (step 5 below).
            Logger.i(TAG) { "Episode switch — NOT stopping (loadfile replace will handle it)" }

            // Set switching flags: isSwitching (error suppression) + isSwitchingEpisode
            // (shows the "Loading episode..." overlay).
            stateHolder.setSwitching(true)
            stateHolder.setSwitchingEpisode(true)

            // CRITICAL: Update the episode title IMMEDIATELY (before resolve)
            // so the EpisodeSwitchingOverlay shows the NEW episode's name.
            stateHolder.updateCurrentEpisode(
                url = ep.url,
                number = ep.episodeNumber,
                title = ep.name,
                resolvedVideosKey = "", // will be updated after resolve
            )
            // D-749: clear the old file's metrics (see the offline branch — the
            // periodic save's dur>0 guard then skips the resolve window).
            stateHolder.resetPlaybackMetrics()

            switchJob = scope.launch {
                try {
                    // D-749: a newer switch may have started while this one was
                    // being prepared — check BEFORE the network resolve.
                    if (generation != switchGeneration) {
                        Logger.i(TAG) { "Episode switch superseded before resolve — aborting" }
                        return@launch
                    }
                    // Build a full SEpisode for the resolver.
                    val sEpisode = eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
                        url = ep.url
                        name = ep.name
                        episode_number = ep.episodeNumber
                    }

                    Logger.i(TAG) { "Re-resolving videos for episode ${ep.url}..." }
                    videoResolver.resolve(source, sEpisode).collect { state ->
                        when (state) {
                            is com.confused.anikuta.core.videoresolver.ResolverState.Loading -> {
                                Logger.d(TAG) { "Resolving..." }
                            }
                            is com.confused.anikuta.core.videoresolver.ResolverState.Success -> {
                                // D-749: discard stale results — a superseded
                                // resolve's loadfile must never touch MPV.
                                if (generation != switchGeneration) {
                                    Logger.i(TAG) { "Episode switch resolve superseded — discarding result" }
                                    return@collect
                                }
                                if (state.videos.isNotEmpty()) {
                                    // ROUND 122 (R8): the CONTINUITY PICK —
                                    // mirror the downloads' pinned re-resolve:
                                    // same server + same audio + same quality
                                    // first, then a logged fallback ladder.
                                    // The old code blindly took first-of-first
                                    // (the user's "it should pick the server
                                    // and the resolution which was used for
                                    // the current episode" report).
                                    val servers = videoResolver.buildServers(state.rawEntries, source.name)
                                    val newRegistryKey = if (servers.isNotEmpty()) {
                                        ResolvedVideosRegistry.put(servers)
                                    } else ""
                                    val flatFirst = state.videos.first()
                                    // ROUND 122-fix: pass the flat ResolvedVideo list —
                                    // the ladder matches on the structured servers, then
                                    // maps the pick back to the flat twin (url/headers).
                                    val pinnedPick = pickPinnedVideo(servers, continuityPin, flatFirst, state.videos)
                                    val video = pinnedPick.video
                                    val pickedResolverVideo: ResolverVideo? = pinnedPick.resolverVideo
                                    currentServerName = pinnedPick.serverName
                                    currentAudioVersion = pinnedPick.audioLabel
                                    Logger.i(TAG) {
                                        "Episode switch — got ${state.videos.size} videos; " +
                                            "continuity picked server='${pinnedPick.serverName}' " +
                                            "audio='${pinnedPick.audioLabel}' quality=${video.quality} " +
                                            "(${video.url.take(60)})"
                                    }

                                    // Update local video state.
                                    currentVideoUrl = video.url
                                    currentVideoTitle = pickedResolverVideo?.videoTitle ?: ""
                                    currentVideoHeaders = video.headers

                                    // Update the state holder's current-episode state
                                    // so the episode list highlight + "now playing" card
                                    // + QualitySheet servers reflect the new episode.
                                    stateHolder.updateCurrentEpisode(
                                        url = ep.url,
                                        number = ep.episodeNumber,
                                        title = ep.name,
                                        resolvedVideosKey = newRegistryKey,
                                    )

                                    // Set pending external tracks + headers on the observer.
                                    pickedResolverVideo?.let { pv ->
                                        observer?.let { obs ->
                                            obs.pendingSubtitleTracks = pv.subtitleTracks.map { Pair(it.url, it.lang) }
                                            obs.pendingAudioTracks = pv.audioTracks.map { Pair(it.url, it.lang) }
                                            obs.trackHeaders = pv.videoHeaders ?: video.headers
                                        }
                                    }

                                    // Video caching: new identity for the new episode's video.
                                    currentCacheId = buildCacheId(
                                        mainId = watchKey.mainId,
                                        animeTitle = watchKey.animeTitle,
                                        episodeNumber = ep.episodeNumber,
                                        episodeTitle = ep.name,
                                        sourceId = watchKey.sourceId,
                                        videoTitle = pickedResolverVideo?.videoTitle ?: "",
                                    )
                                    // Video caching: carry the new episode's external tracks (for tap-to-play replay).
                                    currentSubTracksSerialized = pickedResolverVideo?.let { pv ->
                                        serializeTracks(pv.subtitleTracks.map { it.url to it.lang })
                                    } ?: ""
                                    currentAudioTracksSerialized = pickedResolverVideo?.let { pv ->
                                        serializeTracks(pv.audioTracks.map { it.url to it.lang })
                                    } ?: ""
                                    // D-247: fresh episode → re-arm cache playback for retries.
                                    bypassCacheNextRetry = false

                                    // Set headers + loadfile.
                                    // CRITICAL: For localhost proxy URLs (AniKotoS),
                                    // do NOT set upstream headers (Referer, Origin, etc.).
                                    // D-199: Always set headers (even for localhost proxy — see initial loadfile comment).
                                    val headers = if (video.headers.isNotBlank()) video.headers
                                        else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                                    MPVLib.setOptionString("http-header-fields", headers)
                                    MPVLib.command(arrayOf("loadfile", cachedUrl(video.url, headers), "replace"))
                                    Logger.i(TAG) { "Episode switch — loadfile sent for ${video.url.take(80)}" }
                                } else {
                                    stateHolder.setSwitchingError("No videos found for this episode")
                                }
                            }
                            is com.confused.anikuta.core.videoresolver.ResolverState.Error -> {
                                Logger.e(TAG) { "Episode switch resolve failed: ${state.message}" }
                                stateHolder.setSwitchingError("Failed to resolve: ${state.message}")
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "Episode switch failed" }
                    stateHolder.setSwitchingError("Episode switch failed: ${e.message}")
                }
            }
        }
        }
    }

    // ── Skip forward (next episode) handler ──
    // ROUND 122 (R3): number-based (nearest strictly-higher episode number) —
    // the list is ascending-sorted now, but duplicates (sub/dub) and any
    // future order drift can't invert the direction again.
    val onSkipForward: () -> Unit = {
        val nextEp = nextEpisodeInList()
        if (nextEp != null) {
            Logger.i(TAG) { "Skip forward → episode ${nextEp.episodeNumber} (${nextEp.name})" }
            onEpisodeSwitch(nextEp)
        } else {
            Logger.i(TAG) { "Skip forward — no next episode (at end of list)" }
        }
    }

    // ROUND 121: the prev-episode twin (the top-right navigation pair).
    // ROUND 122 (R3): number-based (nearest strictly-lower) — same rule.
    val onSkipBackward: () -> Unit = {
        val prevEp = prevEpisodeInList()
        if (prevEp != null) {
            Logger.i(TAG) { "Skip backward → episode ${prevEp.episodeNumber} (${prevEp.name})" }
            onEpisodeSwitch(prevEp)
        } else {
            Logger.i(TAG) { "Skip backward — no previous episode (at start of list)" }
        }
    }

    // ── Round 120 (D-747): the Up Next countdown ──────────────────────────────
    // Only while auto-play is ON: a 10s countdown on the card, then advance.
    // When OFF the card is button-only — the pref had ZERO consumers before
    // round 120, so this is where the setting finally does something.
    LaunchedEffect(upNextVisible, upNextEpisode) {
        if (!upNextVisible || upNextEpisode == null) return@LaunchedEffect
        if (playerPreferences.autoPlayNext) {
            // ROUND 123 (R3): the countdown only TICKS near the real end — an
            // outro-triggered early card must not auto-advance while most of
            // the episode remains (the user: "the timer should only run based
            // on the proper logical orders"). The card stays visible with the
            // countdown held at full until playback actually reaches 95%.
            upNextCountdown = com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.UP_NEXT_COUNTDOWN_SECONDS
            while (upNextVisible && upNextCountdown > 0) {
                delay(1_000L)
                if (!upNextVisible) return@LaunchedEffect
                val pos = stateHolder.position.value
                val dur = stateHolder.duration.value
                // ROUND 124 (R3): the same window as the offer — the final 10s.
                val nearEnd = dur > 0 && (dur - pos) <= 10
                if (!nearEnd) continue
                upNextCountdown = upNextCountdown - 1
            }
            if (upNextVisible && upNextCountdown <= 0) {
                val next = upNextEpisode
                upNextVisible = false
                if (next != null) onEpisodeSwitch(next)
            }
        } else {
            upNextCountdown = 0
        }
    }

    val onPlayUpNext: () -> Unit = {
        val next = upNextEpisode
        upNextVisible = false
        if (next != null) onEpisodeSwitch(next)
    }
    // ROUND 122 (R4): the dismissal is RECORDED per episode — the EOF force and
    // the 95% poll both check dismissedNextKeys (offerNextEpisode), so the card
    // can never resurrect (the device report: dismiss in one orientation,
    // reappear after the mode switch).
    val onDismissUpNext: () -> Unit = {
        dismissedNextKeys[buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)] = true
        upNextVisible = false
        upNextCountdown = 0
    }

    // ── ROUND 121: the AniSkip fetch — per episode, cached in prefs ───────────
    // Cached-first; the negative latch (markEpisodeSkipFetched) stops re-asks
    // for anime the service doesn't cover. The episode-number check before
    // the state write discards a result that landed after a switch.
    // ROUND 124 (R4): re-keyed on duration availability — the old effect ran
    // ONCE at episode open while duration was still 0, asked the service with
    // episodeLength=0 (a guaranteed miss), and the miss LATCHED permanently
    // (the device round: AniSkip silent even on popular anime). The fetch now
    // waits for the file's real duration.
    LaunchedEffect(currentEpisodeUrl, duration > 0) {
        val epNum = stateHolder.currentEpisodeNumber.value.toInt()
        if (watchKey.mainId.isBlank() || epNum <= 0) {
            episodeSkip = null
            return@LaunchedEffect
        }
        val cached = playerPreferences.getEpisodeSkipTimes(watchKey.mainId, epNum)
        if (cached != null) {
            episodeSkip = cached
            return@LaunchedEffect
        }
        if (playerPreferences.isEpisodeSkipFetched(watchKey.mainId, epNum)) {
            episodeSkip = null
            return@LaunchedEffect
        }
        val durSec = stateHolder.duration.value
        if (durSec <= 0) return@LaunchedEffect
        var hadIdsForLatch = false
        val fetched = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Synchronous DB read + network — off the main thread.
                val details = runCatching {
                    contentRepository.getContentDetails(watchKey.mainId)
                }.getOrNull()
                var mal = details?.malId
                val anilist = details?.anilistId
                // ROUND 124 (R4): the source's episode URLs embed the MAL id
                // ("…&mal=57334&…") — a free, instant fallback when the DB row
                // carries none (the device logcat: malId=null on a known
                // AniList-linked anime).
                if (mal == null || mal <= 0) {
                    mal = Regex("mal=(\\d{1,8})")
                        .find(stateHolder.currentEpisodeUrl.value)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    if (mal != null) {
                        Logger.i(TAG) { "AniSkip: malId=$mal recovered from the episode URL" }
                    }
                }
                // ROUND 123 (R10): the full fetch decision in one line — the
                // device round had AniSkip silently dead for three rounds
                // (GET-404, then stale latches); this names the cause.
                Logger.i(TAG) {
                    "AniSkip fetch: mainId=${watchKey.mainId.take(12)} ep=$epNum malId=$mal " +
                        "anilistId=$anilist durSec=$durSec (latched=" +
                        "${playerPreferences.isEpisodeSkipFetched(watchKey.mainId, epNum)})"
                }
                // ROUND 122 (R5): latch ONLY when the attempt had an id to ask
                // with — a no-id episode (or a failed metadata read) stays
                // eligible forever; latching it made the FIRST failure
                // permanent across restarts (the "AniSkip not working at all"
                // report). A WITH-id miss is definitive (the service answered
                // "no data") and stays latched.
                hadIdsForLatch = (mal != null && mal > 0) || (anilist != null && anilist > 0)
                when {
                    mal != null && mal > 0 ->
                        aniSkipClient.fetchSkipTimesByMal(mal, epNum, durSec.toLong())
                    anilist != null && anilist > 0 ->
                        aniSkipClient.fetchSkipTimes(anilist, epNum, durSec.toLong())
                    else -> null
                }
            }
        }.getOrNull()
        if (hadIdsForLatch) {
            playerPreferences.markEpisodeSkipFetched(watchKey.mainId, epNum)
        }
        val stored = fetched?.let {
            com.confused.anikuta.core.preferences.PlayerPreferences.EpisodeSkipTimes(
                introStartSec = it.intro?.startSec,
                introEndSec = it.intro?.endSec,
                outroStartSec = it.outro?.startSec,
                outroEndSec = it.outro?.endSec,
            )
        }
        playerPreferences.setEpisodeSkipTimes(watchKey.mainId, epNum, stored)
        Logger.i(TAG) {
            "AniSkip: EP $epNum → intro=${fetched?.intro?.let { "${it.startSec}-${it.endSec}s" }} " +
                "outro=${fetched?.outro?.let { "${it.startSec}-${it.endSec}s" }} " +
                "(miss=${fetched == null})"
        }
        if (stateHolder.currentEpisodeNumber.value.toInt() == epNum) episodeSkip = stored
    }

    // ── Round 120 (D-748), reworked ROUND 121: intro/outro skip ──────────────
    // The EFFECTIVE windows: AniSkip's per-episode intervals win; the manual
    // per-series stamps are the fallback (sources the service doesn't cover).
    //  - BUTTON mode: a "Skip intro"/"Skip outro" pill while inside the span.
    //  - AUTO mode: silently seek past — once per episode key per entry.
    // Outro semantics: with an AniSkip ed END, the skip lands at the ed end
    // (post-credits content plays; the episode then ends into the Up Next
    // flow); with only a manual start stamp, the skip jumps to the NEXT
    // episode (the Aniyomi "skip ending" semantic).
    LaunchedEffect(position, duration, currentEpisodeUrl, skipTimestamps, episodeSkip, skipIntroMode, skipOutroMode) {
        if (duration <= 0) {
            skipPill = null
            return@LaunchedEffect
        }
        val epKey = buildEpisodeKey(watchKey.mainId, stateHolder.currentEpisodeNumber.value)
        // ROUND 121-fix: EpisodeSkipTimes and SkipTimestamps are UNRELATED
        // types — the bare `a ?: b` LUB'd to Any and the property accesses
        // below didn't resolve. Normalize to plain Ints here (AniSkip wins,
        // manual stamps are the fallback; manual outros have no end → null,
        // which keeps the jump-to-next-episode semantics for them).
        val aniIntro = episodeSkip?.takeIf { it.hasIntro }
        val aniOutro = episodeSkip?.takeIf { it.hasOutro }
        val manualIntro = skipTimestamps.takeIf { it.hasIntro }
        val manualOutro = skipTimestamps.takeIf { it.hasOutro }
        val introStartSec = aniIntro?.introStartSec ?: manualIntro?.introStartSec
        val introEndSec = aniIntro?.introEndSec ?: manualIntro?.introEndSec
        val outroStartSec = aniOutro?.outroStartSec ?: manualOutro?.outroStartSec
        val outroEndSec = aniOutro?.outroEndSec
        val inIntro = introStartSec != null && introEndSec != null &&
            position >= (introStartSec ?: 0) - 3 &&
            position < (introEndSec ?: 0)
        val inOutro = outroStartSec != null &&
            position >= (outroStartSec ?: 0) &&
            position < duration - 2

        when {
            inIntro && skipIntroMode == "AUTO" -> {
                if (autoSkippedKeys[epKey] != true) {
                    autoSkippedKeys[epKey] = true
                    val target = introEndSec ?: 0
                    Logger.i(TAG) { "D-748: auto-skipping intro → ${target}s ($epKey)" }
                    MPVLib.command(arrayOf("seek", target.toString(), "absolute"))
                }
                skipPill = null
            }
            inIntro && skipIntroMode == "BUTTON" ->
                skipPill = SkipPillState.Intro(endSec = introEndSec ?: 0)
            inOutro && skipOutroMode == "AUTO" -> {
                if (autoSkippedKeys["$epKey|outro"] != true) {
                    autoSkippedKeys["$epKey|outro"] = true
                    // AniSkip ed end → finish the credits (Up Next follows at
                    // EOF); manual start-only → jump the episode entirely.
                    if (outroEndSec != null && outroEndSec < duration - 2) {
                        Logger.i(TAG) { "D-748: auto-skipping outro → ed end ${outroEndSec}s ($epKey)" }
                        MPVLib.command(arrayOf("seek", outroEndSec.toString(), "absolute"))
                    } else {
                        val next = nextEpisodeInList()
                        Logger.i(TAG) { "D-748: auto-skipping outro ($epKey) next=${next?.episodeNumber}" }
                        if (next != null) onEpisodeSwitch(next)
                        else MPVLib.command(arrayOf("seek", duration.toString(), "absolute"))
                    }
                }
                skipPill = null
            }
            inOutro && skipOutroMode == "BUTTON" ->
                skipPill = SkipPillState.Outro(endSec = outroEndSec)
            else -> skipPill = null
        }
    }

    val onSkipPillAction: () -> Unit = {
        when (val pill = skipPill) {
            is SkipPillState.Intro -> {
                Logger.i(TAG) { "Skip intro tapped → ${pill.endSec}s" }
                MPVLib.command(arrayOf("seek", pill.endSec.toString(), "absolute"))
            }
            is SkipPillState.Outro -> {
                // An AniSkip ed end seeks there (post-credits content); a
                // manual start-only outro jumps to the next episode.
                val edEnd = pill.endSec
                if (edEnd != null && edEnd < duration - 2) {
                    Logger.i(TAG) { "Skip outro tapped → ed end ${edEnd}s" }
                    MPVLib.command(arrayOf("seek", edEnd.toString(), "absolute"))
                } else {
                    val next = nextEpisodeInList()
                    Logger.i(TAG) { "Skip outro tapped → next=${next?.episodeNumber}" }
                    if (next != null) onEpisodeSwitch(next)
                    else MPVLib.command(arrayOf("seek", duration.toString(), "absolute"))
                }
            }
            null -> Unit
        }
        skipPill = null
    }

    val onRestartFromStart: () -> Unit = {
        Logger.i(TAG) { "Restart tapped (resume pill)" }
        resumePillSec = -1L
        MPVLib.command(arrayOf("seek", "0", "absolute"))
    }

    // ROUND 121: cancel a stuck switch — kill the in-flight job + clear the
    // switching flags. The previous episode's file keeps playing (its loadfile
    // was never replaced when the cancel lands in the resolve phase).
    val onCancelSwitch: () -> Unit = {
        Logger.i(TAG) { "Switch cancelled by the user" }
        switchJob?.cancel()
        switchJob = null
        stateHolder.setSwitching(false)
        stateHolder.setSwitchingEpisode(false)
    }

    // ── D-748 / ROUND 124 (R5): the manual skip-timestamps EDITOR is REMOVED
    // (the device round's outright order — its only entry died with the
    // drawer chip; the stored stamps still feed the skip fallback below).
    // ROUND 121: the fullscreen episodes browser (the More button's sheet).
    var showEpisodesSheet by remember { mutableStateOf(false) }
    // ROUND 121: the fullscreen episodes browser — the More button's sheet,
    // and the skip editor's new home (the control-bar entry retired for the
    // prev/next episode buttons).
    // D-747: the Up Next bundle built once for BOTH modes (thumbnail falls back
    // to the cover; the countdown rides only when auto-play is ON).
    val upNextUi: UpNextUi? = if (upNextVisible) {
        upNextEpisode?.let { next ->
            val meta = episodeMetadata[next.episodeNumber.toInt()]
            UpNextUi(
                episode = next,
                label = "EP ${formatEpisodeNumber(next.episodeNumber)}",
                title = meta?.title ?: next.name,
                thumbnailUrl = meta?.thumbnailUrl,
                coverFallbackUrl = watchKey.coverUrl,
                countdown = if (playerPreferences.autoPlayNext) upNextCountdown else null,
            )
        }
    } else null

    // ROUND 123 (R6): ONE root Box for the whole mode + overlay region — the
    // drawer used to be composed BEFORE the player as a sibling root node of
    // the nav host's AnimatedContent stack, which drew it UNDER the opaque
    // player surface (invisible + untouchable — the "three dots do nothing"
    // device report, fullscreen AND portrait). Inside this Box the mode is
    // child #1 and the drawer mounts LAST (= topmost draw + hit-test); the
    // ModalBottomSheets are window-level and unaffected by position.
    Box(modifier = Modifier.fillMaxSize()) {
    if (playerMode == PlayerMode.FULLSCREEN) {
        FullscreenMode(
            watchKey = watchKey,
            stateHolder = stateHolder,
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = { mpvView = it },
            onTogglePlay = {
                // D-749: track the deliberate pause for the watchdog.
                stateHolder.updateUserPaused(isPlaying)
                MPVLib.setPropertyBoolean("pause", isPlaying)
            },
            onSeekRelative = { delta -> MPVLib.command(arrayOf("seek", delta.toString(), "relative")) },
            onSeekTo = { pos -> MPVLib.setPropertyInt("time-pos", pos) },
            onBack = { stateHolder.updateMode(PlayerMode.MINIMIZED) },
            onQualityClick = { showQualitySheet = true },
            onSubtitleClick = { showSubtitleSheet = true },
            onSpeedClick = { showSpeedSheet = true },
            onSkipForward = onSkipForward,
            onRetry = onRetry,
            onDismissError = onDismissError,
            isSwitchingEpisode = isSwitchingEpisode,
            switchingEpisodeTitle = currentEpisodeTitle,
            currentSpeed = stateHolder.playbackSpeed.collectAsState().value,
            upNext = upNextUi,
            onPlayUpNext = onPlayUpNext,
            onDismissUpNext = onDismissUpNext,
            skipPill = skipPill,
            onSkipPillAction = onSkipPillAction,
            resumePillSec = resumePillSec,
            onRestartFromStart = onRestartFromStart,
            onPrevEpisode = onSkipBackward,
            onNextEpisode = onSkipForward,
            onOpenEpisodesSheet = { showEpisodesSheet = true },
            onCancelSwitch = onCancelSwitch,
            skipRanges = skipRanges,
        )
    } else {
        MinimizedMode(
            watchKey = watchKey,
            episodeList = episodeList,
            episodeMetadata = episodeMetadata,
            stateHolder = stateHolder,
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = { mpvView = it },
            onTogglePlay = {
                // D-749: track the deliberate pause for the watchdog.
                stateHolder.updateUserPaused(isPlaying)
                MPVLib.setPropertyBoolean("pause", isPlaying)
            },
            onSeekRelative = { delta -> MPVLib.command(arrayOf("seek", delta.toString(), "relative")) },
            onSeekTo = { pos -> MPVLib.setPropertyInt("time-pos", pos) },
            onBack = onBack,
            onMaximize = { stateHolder.updateMode(PlayerMode.FULLSCREEN) },
            onQualityClick = { showQualitySheet = true },
            onSubtitleClick = { showSubtitleSheet = true },
            onRetry = onRetry,
            onDismissError = onDismissError,
            onEpisodeSwitch = onEpisodeSwitch,
            isSwitchingEpisode = isSwitchingEpisode,
            switchingEpisodeTitle = currentEpisodeTitle,
            currentEpisodeUrl = currentEpisodeUrl,
            currentEpisodeNumber = currentEpisodeNumber,
            currentEpisodeTitle = currentEpisodeTitle,
            upNext = upNextUi,
            onPlayUpNext = onPlayUpNext,
            onDismissUpNext = onDismissUpNext,
            skipPill = skipPill,
            onSkipPillAction = onSkipPillAction,
            resumePillSec = resumePillSec,
            onRestartFromStart = onRestartFromStart,
            onPrevEpisode = onSkipBackward,
            onNextEpisode = onSkipForward,
            onCancelSwitch = onCancelSwitch,
            skipRanges = skipRanges,
        )
    }

    // ── Sheets (shared between minimized + fullscreen) ──
    if (showSubtitleSheet) {
        SubtitleTracksSheet(
            tracks = stateHolder.subtitleTracks.collectAsState().value,
            currentTrackId = stateHolder.currentSubtitleTrack.collectAsState().value,
            onTrackSelected = onSubtitleSelected,
            onDismiss = { showSubtitleSheet = false },
            onOpenSettings = {
                showSubtitleSheet = false
                showSubtitleSettingsSheet = true
            },
            // D-407 (round 31): the permanent manual-import action — the sheet
            // dismisses itself (the picker opens over the app; the import
            // re-selects the new track + toasts on completion).
            onAddSubtitleFile = {
                showSubtitleSheet = false
                subtitleFilePicker.launch(SUBTITLE_MIME_HINTS)
            },
            // D-408 (round 32): the episode's on-disk subtitle files (not yet
            // loaded as tracks) + the load handler (the proven stage → sub-add
            // "select" path — they show + apply exactly like the manual imports).
            storageSubtitles = storageSubtitles,
            onLoadStorageSubtitle = onLoadStorageSubtitle,
            onRefreshTracks = {
                // Manually reload tracks from MPV when the sheet opens.
                Logger.i(TAG) { "=== SUBTITLE SHEET OPENED — refreshing tracks ===" }
                Logger.i(TAG) { "WatchKey subtitleTracksSerialized: ${watchKey.subtitleTracksSerialized.take(100)}" }
                Logger.i(TAG) { "WatchKey parsed subtitle tracks: ${watchKey.parseSubtitleTracks().size}" }
                Logger.i(TAG) { "State holder subtitleTracks: ${stateHolder.subtitleTracks.value.size}" }
                Logger.i(TAG) { "State holder currentSubtitleTrack: ${stateHolder.currentSubtitleTrack.value}" }
                observer?.let { obs ->
                    Logger.i(TAG) { "Observer pendingSubtitleTracks: ${obs.pendingSubtitleTracks.size}" }
                    val view = mpvView
                    if (view != null) {
                        try {
                            val trackCount = view.getTrackCount()
                            val (subs, audio) = view.loadTracks()
                            stateHolder.updateTracks(subs, audio)
                            Logger.i(TAG) { "Manual track refresh: ${subs.size} subs, ${audio.size} audio (MPV track-list/count=$trackCount)" }
                            if (subs.isNotEmpty()) {
                                subs.forEach { sub ->
                                    Logger.i(TAG) { "  Sub track: id=${sub.id}, name=${sub.name}, lang=${sub.lang}" }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Manual track refresh failed: ${e.message}" }
                        }
                    } else {
                        Logger.w(TAG) { "mpvView is null — cannot refresh tracks" }
                    }
                } ?: Logger.w(TAG) { "observer is null — cannot refresh tracks" }
            },
        )
    }

    if (showQualitySheet) {
        QualitySheet(
            servers = resolvedServers,
            currentVideoTitle = currentVideoTitle,
            onQualitySelected = onQualitySelected,
            onDismiss = { showQualitySheet = false },
            currentServerName = currentServerName,
            currentAudioVersion = currentAudioVersion,
            // Task 58 (round 18): the debug report's context (Settings → Debug
            // options → Copy button ON → the sheet header's report action).
            animeTitle = watchKey.animeTitle,
            episodeNumber = watchKey.episodeNumber,
        )
    }

    if (showSubtitleSettingsSheet) {
        com.confused.anikuta.core.player.controls.SubtitleSettingsSheet(
            playerPreferences = playerPreferences,
            onApplySettings = {
                try { mpvView?.applySubtitlePreferences() }
                catch (e: Exception) { Logger.w(TAG) { "Failed to apply subtitle settings: ${e.message}" } }
            },
            onDismiss = { showSubtitleSettingsSheet = false },
        )
    }

    if (showSpeedSheet) {
        com.confused.anikuta.core.player.controls.SpeedSheet(
            currentSpeed = stateHolder.playbackSpeed.value,
            onSpeedSelected = onSpeedSelected,
            onDismiss = { showSpeedSheet = false },
        )
    }

        // ROUND 123 (R6): the episodes drawer — mounted LAST inside the root
        // Box (topmost draw + hit-test order over the player surface).
        if (showEpisodesSheet) {
            PlayerEpisodesSheet(
                episodes = episodeList,
                currentUrl = currentEpisodeUrl,
                onEpisodeSelect = { ep ->
                    showEpisodesSheet = false
                    onEpisodeSwitch(ep)
                },
                onDismiss = { showEpisodesSheet = false },
            )
        }
    } // root Box (ROUND 123 R6)
}

// ════════════════════════════════════════════════════════════════════════════
//  MINIMIZED MODE — floating top bar + 16:9 player + ported MinimizedControls + episode list
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MinimizedMode(
    watchKey: WatchKey,
    episodeList: List<SimpleEpisode>,
    episodeMetadata: Map<Int, WatchEpisodeMeta>,
    stateHolder: PlayerStateHolder,
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onBack: () -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onEpisodeSwitch: (SimpleEpisode) -> Unit = {},
    isSwitchingEpisode: Boolean = false,
    switchingEpisodeTitle: String = "",
    currentEpisodeUrl: String = "",
    currentEpisodeNumber: Float = 0f,
    currentEpisodeTitle: String = "",
    // ── Round 120 overlays (D-746/D-747/D-748) ──
    upNext: UpNextUi? = null,
    onPlayUpNext: () -> Unit = {},
    onDismissUpNext: () -> Unit = {},
    skipPill: SkipPillState? = null,
    onSkipPillAction: () -> Unit = {},
    resumePillSec: Long = -1L,
    onRestartFromStart: () -> Unit = {},
    onPrevEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    // ROUND 121: cancel a stuck episode switch (the overlay's button).
    onCancelSwitch: () -> Unit = {},
    // ROUND 122 (R5): the seek-bar skip-range ribbon.
    skipRanges: com.confused.anikuta.core.player.controls.SkipRanges = com.confused.anikuta.core.player.controls.SkipRanges(),
) {
    val listState = rememberLazyListState()

    // Phase 4: per-episode rating state (self-contained in MinimizedMode).
    val ratingStore = koinInject<com.confused.anikuta.core.ratings.RatingStore>()
    val ratingScope = rememberCoroutineScope()
    var episodeRating by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(watchKey.mainId, currentEpisodeNumber) {
        if (watchKey.mainId.isNotBlank()) {
            val epKey = buildEpisodeKey(watchKey.mainId, currentEpisodeNumber)
            episodeRating = runCatching { ratingStore.getEpisodeRating(watchKey.mainId, epKey) }.getOrNull()
        }
    }

    // ── ROUND 102 (WS-F): the PLAYER episode-list customization's new home ──
    // The in-player gear + search are RETIRED (the user's round-102 order:
    // "I don't want you to give the search option there, or even give the
    // settings option there at all"). The customization itself lives in the
    // dedicated Settings page now (Appearance → "Player page") — the
    // SAME PlayerEpisodeListPreferences keys, read here exactly as before,
    // so the list stays fully styleable/sortable — just from the
    // settings surface instead of mid-playback. (SA2-F4: "filterable" was
    // stale — the filter retired in round 104.)
    val playerListPrefs = koinInject<com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences>()
    // ROUND 103 (WS-4): the raw key resolves through the lenient lookup —
    // a stored legacy value ("MINIMAL") maps to the paradigm the renderer
    // actually draws. The display bundle (the four-paradigm knobs) is built
    // from the LIVE collected values — the settings preview and this list
    // re-shape together (the D-481 rule).
    val rowStyleKey by playerListPrefs.rowStyle.changes.collectAsState(initial = playerListPrefs.rowStyle.get())
    // SA2-F1: the download engine — MinimizedMode's OWN inject (the
    // WatchScreen local is out of this function's lexical scope).
    val downloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()
    val listStyle = remember(rowStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.fromKey(rowStyleKey)
    }
    val showSynopsis by playerListPrefs.showSynopsis.changes.collectAsState(initial = playerListPrefs.showSynopsis.get())
    val showDatePill by playerListPrefs.showDatePill.changes.collectAsState(initial = playerListPrefs.showDatePill.get())
    val dimWatched by playerListPrefs.dimWatched.changes.collectAsState(initial = playerListPrefs.dimWatched.get())
    // ROUND 104 (WS-D): the BANNER's controls — the ghost-number toggle +
    // the density knob (both live in the shared display bundle now).
    // ROUND 105 (WS-D): the density knob re-aimed at the item SIZE + the
    // number position/style + the progress bar + the GRID's three knobs.
    val showEpisodeNumber by playerListPrefs.showEpisodeNumber.changes.collectAsState(
        initial = playerListPrefs.showEpisodeNumber.get(),
    )
    val showProgressBar by playerListPrefs.showProgressBar.changes.collectAsState(
        initial = playerListPrefs.showProgressBar.get(),
    )
    val bannerSize by playerListPrefs.bannerSize.changes.collectAsState(
        initial = playerListPrefs.bannerSize.get(),
    )
    val bannerNumberPositionKey by playerListPrefs.bannerNumberPosition.changes.collectAsState(
        initial = playerListPrefs.bannerNumberPosition.get(),
    )
    val bannerNumberPosition = remember(bannerNumberPositionKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberPosition
            .fromKey(bannerNumberPositionKey)
    }
    val bannerNumberStyleKey by playerListPrefs.bannerNumberStyle.changes.collectAsState(
        initial = playerListPrefs.bannerNumberStyle.get(),
    )
    val bannerNumberStyle = remember(bannerNumberStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerNumberStyle
            .fromKey(bannerNumberStyleKey)
    }
    val gridWatchedCheckmark by playerListPrefs.gridWatchedCheckmark.changes.collectAsState(
        initial = playerListPrefs.gridWatchedCheckmark.get(),
    )
    val gridCurrentStyleKey by playerListPrefs.gridCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.gridCurrentStyle.get(),
    )
    val gridCurrentStyle = remember(gridCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerGridCurrentStyle
            .fromKey(gridCurrentStyleKey)
    }
    // ROUND 108 (D-713): the GRID's title-line MODE (the round-105 Boolean
    // retired — "whether to show the episode title or not… or only one
    // line"), + the BANNER's watched check mark (the CINEMA parity).
    val gridTitleModeKey by playerListPrefs.gridTitleMode.changes.collectAsState(
        initial = playerListPrefs.gridTitleMode.get(),
    )
    val gridTitleMode = remember(gridTitleModeKey) {
        com.confused.anikuta.core.common.GridTitleMode.fromKey(gridTitleModeKey)
    }
    // ROUND 110 (D-723): the GRID's number-label placement (under the
    // thumbnail / beside the title) — the lenient fromKey, same doctrine.
    val gridNumberPositionKey by playerListPrefs.gridNumberPosition.changes.collectAsState(
        initial = playerListPrefs.gridNumberPosition.get(),
    )
    val gridNumberPosition = remember(gridNumberPositionKey) {
        com.confused.anikuta.core.common.GridNumberPosition.fromKey(gridNumberPositionKey)
    }
    val bannerWatchedCheck by playerListPrefs.bannerWatchedCheck.changes.collectAsState(
        initial = playerListPrefs.bannerWatchedCheck.get(),
    )
    // ROUND 106 (WS-D): the new knobs — the audio pills, the download
    // button, and the BANNER's currently-playing treatment.
    val showAudioPills by playerListPrefs.showAudioPills.changes.collectAsState(
        initial = playerListPrefs.showAudioPills.get(),
    )
    val showDownloadButton by playerListPrefs.showDownloadButton.changes.collectAsState(
        initial = playerListPrefs.showDownloadButton.get(),
    )
    val bannerCurrentStyleKey by playerListPrefs.bannerCurrentStyle.changes.collectAsState(
        initial = playerListPrefs.bannerCurrentStyle.get(),
    )
    val bannerCurrentStyle = remember(bannerCurrentStyleKey) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerBannerCurrentStyle
            .fromKey(bannerCurrentStyleKey)
    }
    // ROUND 105 (WS-D): the TRACKLIST's column-sizing reference — the LIST's
    // widest episode number, pre-formatted (the row measures it once and the
    // number column exactly fits; the digit hugs the left edge).
    val tracklistReferenceNumber = remember(episodeList) {
        val maxNumber = episodeList.maxOfOrNull { it.episodeNumber } ?: 0f
        formatEpisodeNumber(maxNumber)
    }
    val liveListDisplay = remember(
        listStyle, showSynopsis, showDatePill, showAudioPills, dimWatched,
        showEpisodeNumber, showProgressBar, showDownloadButton, bannerSize,
        bannerNumberPosition, bannerNumberStyle, bannerCurrentStyle,
        gridWatchedCheckmark, gridCurrentStyle, gridTitleMode, tracklistReferenceNumber,
        bannerWatchedCheck, gridNumberPosition,
    ) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay(
            style = listStyle,
            showSynopsis = showSynopsis,
            showDatePill = showDatePill,
            showAudioPills = showAudioPills,
            dimWatched = dimWatched,
            showEpisodeNumber = showEpisodeNumber,
            showProgressBar = showProgressBar,
            showDownloadButton = showDownloadButton,
            bannerSize = bannerSize,
            bannerNumberPosition = bannerNumberPosition,
            bannerNumberStyle = bannerNumberStyle,
            bannerCurrentStyle = bannerCurrentStyle,
            gridWatchedCheckmark = gridWatchedCheckmark,
            gridCurrentStyle = gridCurrentStyle,
            gridTitleMode = gridTitleMode,
            tracklistReferenceNumber = tracklistReferenceNumber,
            bannerWatchedCheck = bannerWatchedCheck,
            gridNumberPosition = gridNumberPosition,
        )
    }
    // ROUND 104 (WS-D): the watched FILTER is RETIRED (the v1.1.60 order:
    // "the option for watch filter… should be completely removed") — the
    // list shows every episode; the DIM treatment (above) stays.
    // ROUND 103 (WS-4): DIRECTION ONLY — the round-101 sort modes are
    // retired per the device round's order ("the only sort option which
    // should be given here is ascending or descending").
    val sortDescending by playerListPrefs.sortDescending.changes.collectAsState(initial = playerListPrefs.sortDescending.get())

    // The watched set (live — the store emits as the player saves progress).
    val watchProgressStore = koinInject<WatchProgressStore>()
    val progressList by remember(watchKey.mainId) {
        if (watchKey.mainId.isNotBlank()) {
            watchProgressStore.observeByMainId(watchKey.mainId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<com.confused.anikuta.core.watchprogress.WatchProgress>())
        }
    }.collectAsState(initial = emptyList())
    val watchedKeys = remember(progressList) {
        progressList.filter { it.isWatched }.map { it.episodeKey }.toSet()
    }
    // ROUND 121: per-episode WATCH FRACTION — the row renderer has supported a
    // partial progress bar (progressFraction) all along; the MPV page never fed
    // it (the CloudStream parity gap). An in-progress episode now shows a
    // partial bar; watched keeps the full-tick treatment.
    val progressFractionByKey = remember(progressList) {
        progressList.mapNotNull { progress ->
            if (progress.duration > 0) {
                progress.episodeKey to
                    (progress.position.toFloat() / progress.duration).coerceIn(0f, 1f)
            } else {
                null
            }
        }.toMap()
    }

    // The display list: sort over the episode number + direction ONLY.
    // (ROUND 104 (WS-D): the watched-filter stage is RETIRED — see the
    // display prefs above; ROUND 102 retired the search stage with the
    // in-player search.)
    val displayEpisodes = remember(episodeList, sortDescending) {
        val list = episodeList.sortedBy { it.episodeNumber }
        if (sortDescending) list.asReversed() else list
    }

    // ROUND 102 (WS-F): the current episode's position in the DISPLAY list —
    // -1 means it is not in the list (defensive; with the filter retired
    // this only happens for a current URL outside the episode list), which
    // hides the header's "Scroll to Current" action.
    // SA2-F3 fix: the lazy-index offset counts EVERY item above the rows —
    // the "Currently playing" card (item 0) + the Episodes header (item 1).
    val currentEpisodeIndexInDisplay = remember(displayEpisodes, currentEpisodeUrl) {
        displayEpisodes.indexOfFirst { it.url == currentEpisodeUrl }
    }
    val episodeListHeaderOffset = 2
    // ROUND 103 (WS-4): under GRID the lazy items are PAIRS (two cells per
    // item) — the current episode's lazy index is its PAIR's index.
    val currentLazyIndex = episodeListHeaderOffset + if (listStyle == com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.GRID) {
        currentEpisodeIndexInDisplay / 2
    } else {
        currentEpisodeIndexInDisplay
    }

    // ── ROUND 104 (WS-C): THE UNSTOPPABLE SCROLL's owner-level state.
    // ROOT-CAUSE FIX: the round-103 code declared `rememberCoroutineScope()`
    // INSIDE the header's lazy item — the moment the glide started, the
    // header scrolled out of the composed window, the item was disposed,
    // and the scope's cancellation KILLED the glide mid-flight ("it just
    // scrolls slightly, but then it stops"). The scope now lives HERE, at
    // the page level, OUTLIVING every lazy item. The ARRIVAL PULSE token
    // bumps ONLY after a COMPLETED glide (a user touch cancels the scroll
    // coroutine BEFORE this line — an interrupted glide never pulses). ──
    val pageScrollScope = rememberCoroutineScope()

    // ── ROUND 106 (WS-D): THE PLAYER PAGE'S DOWNLOADS — the badge's data.
    // The engine's per-episode states (keyed "$mainId|$episodeKey") map
    // into the render-only bundle; the enqueue-in-flight set covers the
    // resolve gap (the controller's suspend call before the queue row
    // exists). The controller is the :app bridge — the extracted
    // handleDownloadEpisode chain. (Page level: the row-level helpers below
    // close over these; the LazyListScope itself is not composable.) ──
    val playerDownloadController = koinInject<com.confused.anikuta.core.download.PlayerDownloadController>()
    val coreDownloadStates by downloadManager.episodeDownloadStates.collectAsState()
    val downloadQueueSnapshot by downloadManager.getQueue().collectAsState()
    val enqueueInFlight = remember { mutableStateMapOf<String, Boolean>() }
    var scrollArrivalPulse by remember { mutableLongStateOf(0L) }

    // ── ROUND 109 (D-721): THE DOWNLOAD PICKER — the v1.1.65 device round:
    // "it directly, automatically started to load, and after loading, it
    // apparently did not show me any bottom-up resolved video lists for it
    // or anything like that, how it gets handled on the details page… it
    // automatically selects one of the video streams and starts downloading
    // it automatically, which is not a good idea." The badge's download tap
    // now resolves and hands the FULL server list to THIS state; the picker
    // sheet (mounted at the page's end) renders it; the USER'S pick — and
    // only the pick — enqueues. The old auto-picking enqueueClassic path is
    // retired from the controller's contract. ──
    var downloadPickerEpisode by remember { mutableStateOf<SimpleEpisode?>(null) }
    var downloadPickerServers by remember {
        mutableStateOf<List<com.confused.anikuta.core.videoresolver.ResolverServer>>(emptyList())
    }
    // The download-folder gate (the details page's D-403 pre-check,
    // player-side): the folder URI's persisted WRITE grant — checked before
    // the sheet opens so a pick can never dead-end at an enqueue error.
    val downloadPreferences = koinInject<com.confused.anikuta.core.download.DownloadPreferences>()
    val downloadFolderContext = LocalContext.current
    fun downloadFolderReady(): Boolean = runCatching {
        val uriStr = downloadPreferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return@runCatching false
        val uri = android.net.Uri.parse(uriStr)
        downloadFolderContext.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }
    }.getOrDefault(false)

    // Wrap in derivedStateOf to prevent excessive recompositions.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 200
        }
    }

    // Get the status bar inset height so the player can slide up to sit
    // FLUSH BELOW the status bar (not behind it).
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Header height: 48dp when expanded, 0dp when collapsed.
    val headerHeight by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 48.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "headerHeight",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Top bar area — wraps in a Box with clipToBounds so the top bar
        // slides up + fades out smoothly when scrolling. The Box height
        // animates from (48dp + statusBarInset) to (0dp + statusBarInset),
        // so the player naturally slides up to sit flush BELOW the status bar. ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + statusBarInset)
                .clipToBounds(),
        ) {
            if (headerHeight > 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .graphicsLayer {
                            alpha = if (headerHeight == 0.dp) 0f else 1f
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ControlButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack,
                            )
                            Text(
                                text = "ANI-KUTA",
                                fontFamily = RobotoFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }

        // ── Player 16:9 with ported MinimizedControls ──
        // The player sits right below the top bar Box. When the top bar
        // collapses, the Box shrinks, and the player slides up smoothly
        // (driven by the headerHeight animation). The player never goes
        // above the status bar because the Box always has at least
        // statusBarInset height.
        // 6dp vertical padding creates a gap between the top bar and the player.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                PlayerSurface(
                    mpvView = mpvView,
                    initMpv = initMpv,
                    onMpvViewCreated = onMpvViewCreated,
                    modifier = Modifier.fillMaxSize(),
                )

                // Ported MinimizedControls — handles all gestures, overlay, seekbar, etc.
                com.confused.anikuta.core.player.controls.MinimizedControls(
                    stateHolder = stateHolder,
                    onTogglePlay = onTogglePlay,
                    onSeekRelative = onSeekRelative,
                    onSeekTo = onSeekTo,
                    onMaximize = onMaximize,
                    onQualityClick = onQualityClick,
                    onSubtitleClick = onSubtitleClick,
                    onRetry = onRetry,
                    onDismissError = onDismissError,
                    onPrevEpisode = onPrevEpisode,
                    onNextEpisode = onNextEpisode,
                    skipRanges = skipRanges,
                )

                // Episode switching overlay — shown over the player while a new
                // episode resolves + loads. ONLY shown for episode switches (not
                // quality/server switches — those just show the buffering spinner).
                if (isSwitchingEpisode) {
                    EpisodeSwitchingOverlay(
                        episodeTitle = switchingEpisodeTitle.ifBlank { null },
                        onCancel = onCancelSwitch,
                    )
                }

                // Round 120 overlays: skip pill + resume pill ONLY — the Up Next
                // card is NOT hosted inside the portrait player box anymore
                // (ROUND 122 (R4): the fullscreen-tuned card clipped against the
                // 16:9 box — no Play button reachable, "looked ugly"). It renders
                // full-width between the player and the episode list below.
                PlayerRound120Overlays(
                    upNext = upNext,
                    onPlayUpNext = onPlayUpNext,
                    onDismissUpNext = onDismissUpNext,
                    skipPill = skipPill,
                    onSkipPillAction = onSkipPillAction,
                    resumePillSec = resumePillSec,
                    onRestartFromStart = onRestartFromStart,
                    showUpNextCard = false,
                )
            }
        }

        // ROUND 122 (R4): the portrait Up Next card — the SAME card content as
        // landscape (thumbnail, EP label, title, ×, Play), as a full-width
        // stationary inset between the player and the episode list. Never
        // clipped, never covers the video, the Play button always reachable.
        androidx.compose.animation.AnimatedVisibility(
            visible = upNext != null,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            upNext?.let { n ->
                com.confused.anikuta.core.designsystem.component.player.UpNextCard(
                    visible = true,
                    episodeLabel = n.label,
                    episodeTitle = n.title,
                    thumbnailUrl = n.thumbnailUrl,
                    coverFallbackUrl = n.coverFallbackUrl,
                    countdownSecondsLeft = n.countdown,
                    countdownTotalSeconds = com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.UP_NEXT_COUNTDOWN_SECONDS,
                    onPlay = onPlayUpNext,
                    onDismiss = onDismissUpNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // ── Scrollable content: episode description + episode list ──
        // Wrapped in a Box so we can overlay a ScrollBlurOverlay at the top edge,
        // creating a gradient blur effect where the content meets the player.
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
            item {
                // ── Currently playing episode details ──
                val currentEpNum = currentEpisodeNumber.toInt()
                val currentMeta = episodeMetadata[currentEpNum]
                val currentDisplayTitle = currentMeta?.title
                    ?: com.confused.anikuta.core.common.EpisodeTitleParser
                        .getDisplayTitle(currentEpisodeTitle, currentEpisodeNumber)
                val currentDescription = currentMeta?.description
                val currentDateText = if (currentMeta != null && currentMeta.airDateMillis > 0) {
                    formatDate(currentMeta.airDateMillis)
                } else null
                val currentAudio = parseAudioAvailability(currentMeta?.scanlator, currentEpisodeTitle)

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Currently playing episode ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(currentEpisodeNumber)}",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentDisplayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Phase 4: per-episode star rating (below the episode title text).
                        Spacer(Modifier.height(6.dp))
                        WatchStarRatingBar(
                            rating = episodeRating,
                            onRate = { stars ->
                                val epKey = buildEpisodeKey(watchKey.mainId, currentEpisodeNumber)
                                ratingScope.launch {
                                    if (stars <= 0) {
                                        ratingStore.deleteEpisodeRating(watchKey.mainId, epKey)
                                    } else {
                                        ratingStore.setEpisodeRating(watchKey.mainId, epKey, stars * 10)
                                    }
                                    episodeRating = if (stars <= 0) null else stars * 10
                                }
                            },
                        )
                        // Date + Audio pills
                        if (currentDateText != null || currentAudio.hasAny) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (currentDateText != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    ) {
                                        Text(
                                            text = currentDateText,
                                            fontFamily = RobotoFamily,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                                if (currentAudio.hasAny) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            currentAudio.labels.forEachIndexed { idx, label ->
                                                if (idx > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(3.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                                                    )
                                                }
                                                Text(
                                                    text = label,
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Synopsis with show more
                        if (!currentDescription.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            var expanded by remember(currentEpNum) { mutableStateOf(false) }
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = currentDescription,
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (currentDescription.length > 60) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (expanded) "Show less" else "Show more",
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { expanded = !expanded },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (episodeList.isNotEmpty()) {
                // D-230: Episodes header — separate item so the episode rows
                // below can be lazy (virtualized). Was a single item{} with
                // forEach{EpisodeListRow} — eager rendering of ALL episodes
                // caused the crash on 1000+ episode series.
                // ROUND 101 (WS-E): the header gained the customization gear
                // + the search magnifier — the player page's dedicated section
                // for its episode list (the details page's D-230 parity).
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Episodes",
                                fontFamily = RobotoFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "${displayEpisodes.size}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // ROUND 102 (WS-F): "Scroll to Current" — the
                            // header's right-side action (replacing the
                            // round-101 search magnifier + settings gear,
                            // both retired per the user's order). A TEXT
                            // action in the primary color; tapping it
                            // animatedly scrolls the episode list to the
                            // currently playing episode. Hidden while the
                            // current episode is outside the display list
                            // (nothing to scroll to).
                            if (currentEpisodeIndexInDisplay >= 0) {
                                Text(
                                    text = "Scroll to Current",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            // ROUND 104 (WS-C): the PAGE-LEVEL
                                            // scope (the round-103 scope lived
                                            // INSIDE this lazy item — its
                                            // disposal mid-glide cancelled the
                                            // scroll: the "scrolls slightly,
                                            // then stops" the device round
                                            // reported). After a COMPLETED
                                            // glide the ARRIVAL PULSE token
                                            // bumps — the current row plays a
                                            // highlight that fades back to its
                                            // normal treatment. A user touch
                                            // cancels this coroutine BEFORE
                                            // the bump (the scroll mutex's
                                            // UserInput priority) — an
                                            // interrupted glide never pulses.
                                            pageScrollScope.launch {
                                                listState.animateScrollToItemCentered(
                                                    currentLazyIndex,
                                                )
                                                scrollArrivalPulse += 1L
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                // ROUND 104 (WS-D): the filtered-empty state is RETIRED
                // with the watched filter (a non-empty episode list can no
                // longer filter down to nothing).
                // D-230: Lazy episode rows — virtualized! Only ~10-15 rows
                // composed at a time (the visible window), not all 1000.
                // ROUND 103 (WS-4): the rows render through the ONE shared
                // four-paradigm dispatcher (PlayerEpisodeListEntry — the
                // same renderer the settings preview draws); under GRID the
                // items are two-across PAIRS.
                // ROUND 104 (WS-D): every entry carries the SWIPE-to-toggle
                // ("exactly like how it is on the details page"; the GRID's
                // cells long-press instead — the shared renderer's parity)
                // + the scroll-arrival pulse token.
                // ── ROUND 106 (WS-D): the row-level download helpers (the
                // composable state hoists to the page level above; these
                // close over it). ──
                fun playerDownloadStateFor(ep: SimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState? {
                    if (watchKey.mainId.isBlank()) return null
                    val key = "${watchKey.mainId}|${ep.url}"
                    if (enqueueInFlight[key] == true) {
                        return com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.InFlight
                    }
                    val raw = coreDownloadStates[key]
                        ?: return com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded
                    return when (raw.first) {
                        com.confused.anikuta.core.download.DownloadStatus.QUEUED,
                        com.confused.anikuta.core.download.DownloadStatus.RETRYING,
                            -> com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.InFlight
                        com.confused.anikuta.core.download.DownloadStatus.DOWNLOADING ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloading(raw.second)
                        com.confused.anikuta.core.download.DownloadStatus.PAUSED ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Paused
                        // ROUND 121: waiting-on-network reads as Paused (auto-resumes).
                        com.confused.anikuta.core.download.DownloadStatus.WAITING_FOR_NETWORK ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Paused
                        com.confused.anikuta.core.download.DownloadStatus.ERROR ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Error
                        com.confused.anikuta.core.download.DownloadStatus.COMPLETED ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.Downloaded
                        com.confused.anikuta.core.download.DownloadStatus.CANCELLED ->
                            com.confused.anikuta.core.designsystem.component.playerlist.PlayerDownloadRenderState.NotDownloaded
                    }
                }
                // The queue's task id for an episode (the DetailsViewModel's
                // lookup, player-side).
                fun downloadTaskIdFor(ep: SimpleEpisode): Long? {
                    if (watchKey.mainId.isBlank()) return null
                    return downloadQueueSnapshot.firstOrNull {
                        it.content.mainId == watchKey.mainId && it.episode.episodeKey == ep.url
                    }?.id
                }
                // The badge's full action bag for one episode.
                fun downloadActionsFor(ep: SimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeDownloadActions {
                    val flowKey = "${watchKey.mainId}|${ep.url}"
                    return com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeDownloadActions(
                        onDownload = {
                            if (watchKey.mainId.isNotBlank()) {
                                // ROUND 109 (D-721): THE DETAILS PAGE'S FLOW,
                                // player-side — the folder gate first (the
                                // D-403 twin), then RESOLVE-AND-SHOW. No
                                // auto-pick: the resolved list rides up to
                                // the picker sheet and the USER chooses.
                                if (!downloadFolderReady()) {
                                    com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                        "No download folder — pick one in Settings → Downloads first",
                                        com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                    )
                                    return@PlayerEpisodeDownloadActions
                                }
                                enqueueInFlight[flowKey] = true
                                pageScrollScope.launch {
                                    val outcome = playerDownloadController.resolveForPicker(
                                        mainId = watchKey.mainId,
                                        episode = com.confused.anikuta.core.download.PlayerDownloadController.EpisodeInfo(
                                            episodeKey = ep.url,
                                            episodeNumber = ep.episodeNumber,
                                            name = ep.name,
                                        ),
                                    )
                                    enqueueInFlight[flowKey] = false
                                    when (outcome) {
                                        is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.PickerReady -> {
                                            downloadPickerEpisode = ep
                                            downloadPickerServers = outcome.servers
                                        }
                                        is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.Started -> Unit
                                        is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.NoSource ->
                                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                                "No download source for this episode",
                                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                            )
                                        is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.CsBridged ->
                                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                                "This episode streams through CloudStream — no classic download",
                                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                            )
                                        is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.Error ->
                                            com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                                "Download failed: ${outcome.message}",
                                                com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                            )
                                    }
                                }
                            }
                        },
                        onPause = {
                            downloadTaskIdFor(ep)?.let { id ->
                                pageScrollScope.launch { downloadManager.pauseDownload(id) }
                            }
                        },
                        onResume = {
                            downloadTaskIdFor(ep)?.let { id ->
                                pageScrollScope.launch { downloadManager.resumeDownload(id) }
                            }
                        },
                        onCancel = {
                            // An enqueue still resolving lands in a moment —
                            // cancel it from the queue once it appears.
                            downloadTaskIdFor(ep)?.let { id ->
                                pageScrollScope.launch { downloadManager.cancelDownload(id) }
                            }
                        },
                        onRetry = {
                            downloadTaskIdFor(ep)?.let { id ->
                                pageScrollScope.launch { downloadManager.retryDownload(id) }
                            }
                        },
                        onPlayDownloaded = {
                            // The offline routing already lives in
                            // onEpisodeSwitch (the downloaded-episode fd://
                            // path).
                            if (ep.url != currentEpisodeUrl) {
                                onEpisodeSwitch(ep)
                            }
                        },
                        // ROUND 108 (D-713): the badge's Downloaded menu offers
                        // Play / Delete — the delete lands here (the details
                        // ViewModel's own call, player-side).
                        onDelete = {
                            if (watchKey.mainId.isNotBlank()) {
                                pageScrollScope.launch {
                                    downloadManager.deleteDownloadedEpisode(
                                        watchKey.mainId,
                                        ep.url,
                                    )
                                }
                            }
                        },
                    )
                }
                // The MPV stack's mapping into the render-only bundle.
                fun toRowData(ep: SimpleEpisode): com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData {
                    val meta = episodeMetadata[ep.episodeNumber.toInt()]
                    val audio = parseAudioAvailability(meta?.scanlator, ep.name)
                    return com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData(
                        episodeNumberText = formatEpisodeNumber(ep.episodeNumber),
                        displayTitle = meta?.title
                            ?: com.confused.anikuta.core.common.EpisodeTitleParser
                                .getDisplayTitle(ep.name, ep.episodeNumber),
                        // ROUND 108 (D-713): the COVER FALLBACK — the details
                        // page's own thumbnail rule, ported (a thumbnail-less
                        // episode shows the cover instead of a number tile).
                        thumbnailUrl = meta?.thumbnailUrl
                            ?: watchKey.coverUrl.takeIf { it.isNotBlank() },
                        dateText = if (meta != null && meta.airDateMillis > 0) {
                            formatDate(meta.airDateMillis)
                        } else null,
                        // ROUND 109 (D-719): the SHORT date — the grid chip's
                        // text (the details grid's own "Jan 1" shape; the
                        // rows above keep the long date).
                        dateTextShort = if (meta != null && meta.airDateMillis > 0) {
                            com.confused.anikuta.core.designsystem.component.episodelist
                                .formatShortDate(meta.airDateMillis)
                        } else null,
                        audioLabels = audio.labels,
                        synopsis = meta?.description,
                        isCurrent = ep.url == currentEpisodeUrl,
                        isWatched = watchKey.mainId.isNotBlank() &&
                            watchedKeys.contains(buildEpisodeKey(watchKey.mainId, ep.episodeNumber)),
                        // ROUND 121: the partial progress bar (see the map above).
                        progressFraction = if (watchKey.mainId.isNotBlank()) {
                            progressFractionByKey[buildEpisodeKey(watchKey.mainId, ep.episodeNumber)] ?: 0f
                        } else {
                            0f
                        },
                        // ROUND 106 (WS-D): the REAL badge state (the old
                        // inert hint glyph is retired).
                        downloadState = if (showDownloadButton) playerDownloadStateFor(ep) else null,
                    )
                }
                if (listStyle == com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle.GRID) {
                    val pairs = displayEpisodes.chunked(2)
                    items(
                        count = pairs.size,
                        key = { i -> pairs[i].joinToString("|") { it.url } },
                    ) { pairIndex ->
                        val pair = pairs[pairIndex]
                        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeGridRow(
                            left = toRowData(pair[0]),
                            right = pair.getOrNull(1)?.let { toRowData(it) },
                            display = liveListDisplay,
                            // ROUND 106 (WS-D): the per-cell download actions,
                            // resolved from the tapped cell's own episode.
                            downloadActions = { data ->
                                // SA2-F10: the SAME number+title match the
                                // onClick resolver uses (a pathological
                                // same-number pair disambiguates by title).
                                val ep = pair.firstOrNull {
                                    formatEpisodeNumber(it.episodeNumber) == data.episodeNumberText &&
                                        (episodeMetadata[it.episodeNumber.toInt()]?.title
                                            ?: com.confused.anikuta.core.common.EpisodeTitleParser
                                                .getDisplayTitle(it.name, it.episodeNumber)) == data.displayTitle
                                } ?: pair.firstOrNull {
                                    formatEpisodeNumber(it.episodeNumber) == data.episodeNumberText
                                } ?: pair[0]
                                if (showDownloadButton) downloadActionsFor(ep) else null
                            },
                            onClick = { data ->
                                // The tapped cell's episode, matched inside its
                                // own pair by the bundle's identity (number +
                                // title, falling back to the number — SA2-F3
                                // hardening: a pathological pair holding two
                                // same-number episodes disambiguates by title).
                                fun titleOf(ep: SimpleEpisode) =
                                    episodeMetadata[ep.episodeNumber.toInt()]?.title
                                        ?: com.confused.anikuta.core.common.EpisodeTitleParser
                                            .getDisplayTitle(ep.name, ep.episodeNumber)
                                val ep = pair.firstOrNull {
                                    formatEpisodeNumber(it.episodeNumber) == data.episodeNumberText &&
                                        titleOf(it) == data.displayTitle
                                } ?: pair.firstOrNull {
                                    formatEpisodeNumber(it.episodeNumber) == data.episodeNumberText
                                } ?: pair[0]
                                if (ep.url != currentEpisodeUrl) {
                                    onEpisodeSwitch(ep)
                                }
                            },
                            // ROUND 104 (WS-D): the GRID's long-press watched
                            // toggle (the swipe's parity — half-width cells
                            // cannot swipe; the details page's GRID parity).
                            onToggleWatched = { data ->
                                val ep = pair.firstOrNull {
                                    formatEpisodeNumber(it.episodeNumber) == data.episodeNumberText
                                } ?: pair[0]
                                if (watchKey.mainId.isNotBlank()) {
                                    pageScrollScope.launch {
                                        watchProgressStore.toggleWatched(
                                            buildEpisodeKey(watchKey.mainId, ep.episodeNumber),
                                        )
                                    }
                                }
                            },
                            arrivalPulse = scrollArrivalPulse,
                        )
                    }
                } else {
                    items(displayEpisodes, key = { it.url }) { ep ->
                        val data = toRowData(ep)
                        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry(
                            data = data,
                            display = liveListDisplay,
                            onClick = {
                                if (!data.isCurrent) {
                                    onEpisodeSwitch(ep)
                                }
                            },
                            // ROUND 106 (WS-D): the download badge's actions.
                            downloadActions = if (showDownloadButton) downloadActionsFor(ep) else null,
                            // ROUND 104 (WS-D): the swipe-to-toggle — the
                            // details page's gesture through the shared
                            // wrapper (the store emits; watchedKeys is live).
                            onToggleWatched = {
                                if (watchKey.mainId.isNotBlank()) {
                                    pageScrollScope.launch {
                                        watchProgressStore.toggleWatched(
                                            buildEpisodeKey(watchKey.mainId, ep.episodeNumber),
                                        )
                                    }
                                }
                            },
                            arrivalPulse = scrollArrivalPulse,
                        )
                    }
                }
            }
            } // end LazyColumn

            // ROUND 102 (WS-F): the in-player episode-list settings sheet is
            // RETIRED — the customization moved to the dedicated Settings page
            // (Appearance → "Player page").

            // ScrollBlurOverlay — gradient at the top edge of the scrollable content,
            // creating a smooth fade where content meets the player.
            com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay(
                scrollOffset = {
                    if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else listState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } // end Box
    }

    // ── ROUND 109 (D-721): THE DOWNLOAD PICKER SHEET — the bottom-up
    //    resolved-video list the v1.1.65 device round ordered ("how it gets
    //    handled on the details page"): the tap resolved the episode's
    //    streams, THIS sheet shows the full server/audio/quality hierarchy,
    //    and the download enqueues ONLY on the user's pick. Dismiss = no
    //    download (cancel-on-dismiss, the CS sheet's own contract). ──
    downloadPickerEpisode?.let { pickerEp ->
        com.confused.anikuta.core.designsystem.component.download.DownloadVideoPickerSheet(
            servers = downloadPickerServers,
            animeTitle = watchKey.animeTitle,
            episodeName = pickerEp.name,
            onVideoSelected = { video, serverName, audioLabel ->
                downloadPickerEpisode = null
                if (watchKey.mainId.isNotBlank()) {
                    pageScrollScope.launch {
                        when (val outcome = playerDownloadController.enqueuePicked(
                            mainId = watchKey.mainId,
                            episode = com.confused.anikuta.core.download.PlayerDownloadController.EpisodeInfo(
                                episodeKey = pickerEp.url,
                                episodeNumber = pickerEp.episodeNumber,
                                name = pickerEp.name,
                            ),
                            video = video,
                            serverName = serverName,
                            audioLabel = audioLabel,
                        )) {
                            // Started: the badge turns InFlight/Downloading on
                            // its own (the queue row lands) — no toast needed.
                            is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.Started -> Unit
                            is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.NoSource ->
                                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                    "No download source for this episode",
                                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                )
                            is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.CsBridged ->
                                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                    "This episode streams through CloudStream — no classic download",
                                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                )
                            is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.Error ->
                                com.confused.anikuta.core.designsystem.component.toast.AppToast.show(
                                    "Download failed: ${outcome.message}",
                                    com.confused.anikuta.core.designsystem.component.toast.AppToastTone.ERROR,
                                )
                            is com.confused.anikuta.core.download.PlayerDownloadController.Outcome.PickerReady -> Unit
                        }
                    }
                }
            },
            onDismiss = { downloadPickerEpisode = null },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FULLSCREEN MODE — edge-to-edge player + ported FullscreenControls
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FullscreenMode(
    watchKey: WatchKey,
    stateHolder: PlayerStateHolder,
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onBack: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    isSwitchingEpisode: Boolean = false,
    switchingEpisodeTitle: String = "",
    currentSpeed: Float = 1.0f,
    // ── Round 120 overlays (D-746/D-747/D-748) ──
    upNext: UpNextUi? = null,
    onPlayUpNext: () -> Unit = {},
    onDismissUpNext: () -> Unit = {},
    skipPill: SkipPillState? = null,
    onSkipPillAction: () -> Unit = {},
    resumePillSec: Long = -1L,
    onRestartFromStart: () -> Unit = {},
    // ── ROUND 121: episode navigation (the top-right cluster) ──
    onPrevEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onOpenEpisodesSheet: () -> Unit = {},
    // ROUND 121: cancel a stuck episode switch (the overlay's button).
    onCancelSwitch: () -> Unit = {},
    // ROUND 122 (R5): the seek-bar skip-range ribbon.
    skipRanges: com.confused.anikuta.core.player.controls.SkipRanges = com.confused.anikuta.core.player.controls.SkipRanges(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = onMpvViewCreated,
            modifier = Modifier.fillMaxSize(),
        )

        // Ported FullscreenControls — handles all gestures, overlay, seekbar, etc.
        com.confused.anikuta.core.player.controls.FullscreenControls(
            stateHolder = stateHolder,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onSeekRelative = onSeekRelative,
            onSeekTo = onSeekTo,
            onMinimize = onBack,
            onLockToggle = { stateHolder.updateControlsLocked(!stateHolder.controlsLocked.value) },
            onQualityClick = onQualityClick,
            onSubtitleClick = onSubtitleClick,
            onSpeedClick = onSpeedClick,
            // ROUND 121: the episodes browser (the previously dead More button).
            onMoreClick = onOpenEpisodesSheet,
            onPrevEpisode = onPrevEpisode,
            onNextEpisode = onNextEpisode,
            onSkipForward = onSkipForward,
            onRetry = onRetry,
            onDismissError = onDismissError,
            animeTitle = watchKey.animeTitle,
            episodeInfo = if (watchKey.episodeTitle.isNotBlank()) "EP ${formatEpisodeNumber(watchKey.episodeNumber)}" else "",
            qualityInfo = watchKey.quality,
            currentSpeed = currentSpeed,
            skipRanges = skipRanges,
        )

        // Episode switching overlay — shown over the fullscreen player while a
        // new episode resolves + loads. ONLY shown for episode switches (not
        // quality/server switches).
        if (isSwitchingEpisode) {
            EpisodeSwitchingOverlay(
                episodeTitle = switchingEpisodeTitle.ifBlank { null },
                onCancel = onCancelSwitch,
            )
        }

        // Round 120 overlays: Up Next card, skip pill, resume pill.
        PlayerRound120Overlays(
            upNext = upNext,
            onPlayUpNext = onPlayUpNext,
            onDismissUpNext = onDismissUpNext,
            skipPill = skipPill,
            onSkipPillAction = onSkipPillAction,
            resumePillSec = resumePillSec,
            onRestartFromStart = onRestartFromStart,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Player surface (shared AndroidView — never recreated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerSurface(
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val layoutId = ctx.resources.getIdentifier("mpv_view", "layout", ctx.packageName)
            val view = (mpvView ?: LayoutInflater.from(ctx).inflate(layoutId, null) as AnikutaMPVView).also { v ->
                if (mpvView == null) {
                    onMpvViewCreated(v)
                    initMpv(v)
                }
            }
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier,
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the private EpisodeListRow is RETIRED — both player
//  stacks and the settings preview render through the ONE shared four-paradigm
//  dispatcher (:core:designsystem/component/playerlist/PlayerEpisodeListEntry).
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int = 40,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

private fun formatEpisodeNumber(num: Float): String {
    return com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(num)
}

// ── Audio availability parsing (shared with details page) ──

private data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
) {
    val hasAny: Boolean get() = hasSub || hasDub || hasHsub
    val labels: List<String> get() = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

private fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

/**
 * Builds the standardized `episode_key` for `WatchProgressStore` saves.
 *
 * Phase WP (episode_key standardization — PLAN §1.9): the standard format is
 * `"${mainId}|${padded_5_digit}"` where `mainId` is the content's stable UUID
 * (from `:core:content`) + `padded_5_digit` is the zero-padded episode number.
 * This format is STABLE across extension reinstalls, source URL changes, AND
 * backup/restore — unlike the old `"$sourceId|$epUrl"` format which broke when
 * `sourceId` changed.
 *
 * Fallback: if `mainId` is blank (shouldn't happen — WatchKey is constructed
 * with it), falls back to the old `"$sourceId|$epUrl"`-style key using the
 * episode number instead of the URL (best-effort — logged WARN so the gap is
 * visible). This is a safety net, not the intended path.
 *
 * @param mainId The content's stable `main_id` (UUID).
 * @param episodeNumber The current episode number (from `PlayerStateHolder`).
 */
private fun buildEpisodeKey(mainId: String, episodeNumber: Float): String {
    if (mainId.isBlank()) {
        com.confused.anikuta.core.common.Logger.w("Anikuta:Feature:Watch") {
            "buildEpisodeKey — mainId is blank; falling back to non-standard key. " +
                "WatchKey should be constructed with mainId. (episodeNumber=$episodeNumber)"
        }
    }
    // Round 120 (D-746): delegated to the single shared builder (the format was
    // hand-rolled in three modules — one drift away from split progress rows).
    return com.confused.anikuta.core.watchprogress.EpisodeKeys.build(mainId, episodeNumber)
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 122 (R8): episode-switch server/quality/audio CONTINUITY
//  Mirrors the downloads' pinned re-resolve matcher (app/download/ReResolver)
//  with a wider logged fallback ladder. Terminal fallback is the old
//  first-of-first behavior — the pick can never fail, only degrade.
// ════════════════════════════════════════════════════════════════════════════

/** The continuity pin captured from the episode being left. */
private data class EpisodePin(val server: String, val audio: String, val quality: String)

/** The continuity pick's result (the video + where it came from, for logging/UI). */
private data class PinnedPick(
    // ROUND 122-fix: the PLAY target is the resolver's flat ResolvedVideo (it
    // carries the extension `headers` mpv needs) — the structured ResolverVideo
    // row has `videoHeaders` but no `headers`, and the switch code reads
    // `video.headers`. The ladder picks a ResolverVideo row, then maps it back
    // to its flat twin by url (VideoResolver builds both from the same
    // Video.videoUrl, so the match is exact).
    val video: ResolvedVideo,
    val resolverVideo: ResolverVideo?,
    val serverName: String,
    val audioLabel: String,
)

/** Extracts the numeric resolution from a quality label ("1080p" → 1080). */
private fun qualityNumber(quality: String): Int? =
    Regex("(\\d{3,4})p").find(quality)?.groupValues?.get(1)?.toIntOrNull()

/** Nearest-resolution video (ties → first); null quality targets take the first. */
private fun nearestQualityVideo(videos: List<ResolverVideo>, targetQuality: String): ResolverVideo? {
    val target = qualityNumber(targetQuality) ?: return videos.firstOrNull()
    return videos.minByOrNull { video ->
        val q = qualityNumber(video.quality)
        if (q == null) Int.MAX_VALUE / 2 else kotlin.math.abs(q - target)
    }
}

private fun pickPinnedVideo(
    servers: List<com.confused.anikuta.core.videoresolver.ResolverServer>,
    pin: EpisodePin?,
    flatFirst: ResolvedVideo,
    resolved: List<ResolvedVideo>,
): PinnedPick {
    // ROUND 122-fix: map a picked structured ResolverVideo row back to its flat
    // ResolvedVideo twin (same Video.videoUrl on both sides) so the caller keeps
    // its ResolvedVideo-based url/headers flow; the flat first-of-list is the
    // terminal fallback.
    fun playVideo(v: ResolverVideo): ResolvedVideo =
        resolved.firstOrNull { it.url == v.url } ?: flatFirst

    val fallback = PinnedPick(
        video = flatFirst,
        resolverVideo = servers.firstOrNull()?.audioVersions?.firstOrNull()?.videos?.firstOrNull(),
        serverName = "",
        audioLabel = "",
    )
    if (pin == null || (pin.server.isBlank() && pin.audio.isBlank() && pin.quality.isBlank())) {
        return fallback
    }
    // Flatten once: (serverName, audioLabel, video) rows in grouping order.
    val rows = servers.flatMap { s ->
        s.audioVersions.flatMap { av -> av.videos.map { Triple(s.name, av.label, it) } }
    }
    if (rows.isEmpty()) return fallback
    val eq = { a: String, b: String -> a.isNotBlank() && b.isNotBlank() && a.equals(b, ignoreCase = true) }

    // L1 — exact server + audio + quality (the ReResolver parity tier).
    rows.firstOrNull { (s, a, v) -> eq(s, pin.server) && eq(a, pin.audio) && eq(v.quality, pin.quality) }
        ?.let { (s, a, v) ->
            Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L1 exact server+audio+quality → $s/$a/${v.quality}" }
            return PinnedPick(playVideo(v), v, s, a)
        }
    // L2 — same server + same audio, nearest resolution.
    rows.filter { (s, a) -> eq(s, pin.server) && eq(a, pin.audio) }
        .let { same ->
            nearestQualityVideo(same.map { it.third }, pin.quality)?.let { v ->
                val hit = same.first { it.third === v || it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L2 same server+audio, nearest quality → ${hit.first}/${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    // L3 — same server (prefer the same audio flavor when it survived), nearest resolution.
    rows.filter { (s, _) -> eq(s, pin.server) }
        .let { sameServer ->
            if (sameServer.isNotEmpty()) {
                val preferred = sameServer.filter { (_, a, _) -> eq(a, pin.audio) }
                    .ifEmpty { sameServer }
                nearestQualityVideo(preferred.map { it.third }, pin.quality)?.let { v ->
                    val hit = preferred.first { it.third.url == v.url }
                    Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L3 same server → ${hit.first}/${hit.second}/${v.quality}" }
                    return PinnedPick(playVideo(v), v, hit.first, hit.second)
                }
            }
        }
    // L4 — same audio flavor on any server (language kept, hoster changed).
    rows.filter { (_, a, _) -> eq(a, pin.audio) }
        .let { sameAudio ->
            nearestQualityVideo(sameAudio.map { it.third }, pin.quality)?.let { v ->
                val hit = sameAudio.first { it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L4 same audio → ${hit.first}/${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    // L5 — the hoster renamed across episodes: fuzzy server match (prefix/
    // contains), smallest name difference, same audio preferred, nearest quality.
    if (pin.server.isNotBlank()) {
        val fuzzy = rows.filter { (s, _, _) ->
            s.startsWith(pin.server, ignoreCase = true) || s.contains(pin.server, ignoreCase = true)
        }.minByOrNull { (s, _, _) -> kotlin.math.abs(s.length - pin.server.length) }
        if (fuzzy != null) {
            val pool = rows.filter { (s, _, _) -> s == fuzzy.first }
            val preferred = pool.filter { (_, a, _) -> eq(a, pin.audio) }.ifEmpty { pool }
            nearestQualityVideo(preferred.map { it.third }, pin.quality)?.let { v ->
                val hit = preferred.first { it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L5 fuzzy server '${pin.server}'→'${hit.first}' ${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    }
    Logger.i("Anikuta:Feature:Watch") { "Watch continuity: no match (L6) — first-of-list fallback" }
    return fallback
}

// ════════════════════════════════════════════════════════════════════════════
//  Round 120 (D-746/D-747/D-748): the player overlays + the skip editor
// ════════════════════════════════════════════════════════════════════════════

/** The Up Next bundle handed to both player modes (built once at the call site). */
private data class UpNextUi(
    val episode: SimpleEpisode,
    val label: String,
    val title: String,
    val thumbnailUrl: String?,
    val coverFallbackUrl: String,
    /** Null = no countdown (auto-play off); 0 = about to advance. */
    val countdown: Int?,
)

/** Which skip pill is showing (computed per position tick). */
private sealed interface SkipPillState {
    data class Intro(val endSec: Int) : SkipPillState

    /** Round 121: [endSec] = AniSkip's ed end (seek there); null = the manual
     *  start-only outro (the pill jumps to the next episode). */
    data class Outro(val endSec: Int?) : SkipPillState
}

/**
 * The round-120 overlay layer over the player surface: the Up Next card
 * (bottom-end), the intro/outro skip pill (above it), and the resume
 * affordance (bottom-center). Pass-through for touches outside its children.
 */
@Composable
private fun PlayerRound120Overlays(
    upNext: UpNextUi?,
    onPlayUpNext: () -> Unit,
    onDismissUpNext: () -> Unit,
    skipPill: SkipPillState?,
    onSkipPillAction: () -> Unit,
    resumePillSec: Long,
    onRestartFromStart: () -> Unit,
    // ROUND 122 (R4): the portrait (minimized) player hosts the card OUTSIDE
    // the 16:9 box now (full-width inset above the episode list) — the old
    // fullscreen-tuned geometry clipped it there.
    showUpNextCard: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showUpNextCard) {
            com.confused.anikuta.core.designsystem.component.player.UpNextCard(
                visible = upNext != null,
                episodeLabel = upNext?.label ?: "",
                episodeTitle = upNext?.title ?: "",
                thumbnailUrl = upNext?.thumbnailUrl,
                coverFallbackUrl = upNext?.coverFallbackUrl,
                countdownSecondsLeft = upNext?.countdown,
                countdownTotalSeconds = com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.UP_NEXT_COUNTDOWN_SECONDS,
                onPlay = onPlayUpNext,
                onDismiss = onDismissUpNext,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // ROUND 122 (R4): the card is fillMaxWidth now — cap it to
                    // its designed width here; the bottom nudge (76→84dp) is
                    // the user's "move the card a little up in landscape".
                    .widthIn(max = 300.dp)
                    .padding(end = 14.dp, bottom = 84.dp),
            )
        }

        skipPill?.let { pill ->
            SkipPill(
                label = when (pill) {
                    is SkipPillState.Intro -> "Skip intro"
                    // ROUND 121-fix: Outro became a data class — needs `is`.
                    is SkipPillState.Outro -> "Skip outro"
                },
                onClick = onSkipPillAction,
                // Sit above the Up Next card when both are showing (fullscreen
                // only — the portrait card no longer lives in this Box, so the
                // old 232dp stack offset is gone with it).
                bottomPadding = if (showUpNextCard && upNext != null) 240.dp else 76.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (resumePillSec >= 0) {
            ResumePill(
                positionSec = resumePillSec,
                onRestart = onRestartFromStart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp),
            )
        }
    }
}

/** The Aniyomi-style skip affordance — a compact glass pill at the bottom-end. */
@Composable
private fun SkipPill(
    label: String,
    onClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .padding(end = 14.dp, bottom = bottomPadding)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The D-746 resume affordance — "Resumed · mm:ss [Restart]", bottom-center for
 * a few seconds after a resume seek. Non-blocking (playback already resumed);
 * Restart is the escape hatch for "I wanted it fresh".
 */
@Composable
private fun ResumePill(
    positionSec: Long,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = modifier.clip(RoundedCornerShape(50)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "Resumed · ${formatPillTime(positionSec)}",
                color = Color.White,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Restart",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The D-748 intro/outro marking sheet — stamps the CURRENT playback position as
 * the intro start/end or the outro start for this series (per-series memory:
 * openings share a length within a season, so one marking covers the rewatch).
 * Persisted through PlayerPreferences (round 120 scoped the database OUT).
 */
// ════════════════════════════════════════════════════════════════════════════
//  ROUND 122 (R7): PlayerEpisodesSheet — the fullscreen More button's episode
//  browser, REBUILT as a RIGHT-SIDE DRAWER (the device round: "instead of the
//  bottom-up menu, a left-right side menu should appear… from the right side…
//  not more than 40% of the screen"). Same content: the episode list (tap to
//  switch without leaving fullscreen) + the skip-timestamps entry.
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun PlayerEpisodesSheet(
    episodes: List<SimpleEpisode>,
    currentUrl: String,
    onEpisodeSelect: (SimpleEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(episodes) { episodes.sortedBy { it.episodeNumber } }
    // The 40% cap: 40% of the real screen width, hard-capped so ultra-wide
    // landscape screens don't grow a ribbon-wide drawer.
    val configuration = LocalConfiguration.current
    val panelMaxWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        ((configuration.screenWidthDp * 0.4f).dp).coerceAtMost(340.dp)
    }
    val listState = rememberLazyListState()
    // Open ON the current episode — a 300-episode list must not open at ep 1.
    // ROUND 124 (R5): CENTER on it (the device round's ask) — an instant prime
    // jump first (the effect can run before the first layout lays items out),
    // then the shared centered settle; the helper no-ops on out-of-range
    // indices and clamps at the content edges, so the very first / very last
    // episode simply rests at its edge (never forced past it, no glitch).
    androidx.compose.runtime.LaunchedEffect(sorted, currentUrl) {
        val idx = sorted.indexOfFirst { it.url == currentUrl }
        if (idx > 0) listState.scrollToItem(idx)
        if (idx >= 0) listState.animateScrollToItemCentered(idx)
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        // The scrim — tap to close (no ripple; a quiet dismissal surface).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        // The panel — slides in from the right edge (the MutableTransitionState
        // makes the ENTER animation run on composition; the composable is
        // conditionally mounted by the caller).
        val enterState = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = enterState,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = panelMaxWidth)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Episodes",
                                fontFamily = RobotoFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "${sorted.size} episodes — tap to play",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // ROUND 124 (R5): the "Skip timestamps" entry is REMOVED
                        // outright (the device round: "why do we need this
                        // option at all? … completely remove it outright").
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp, vertical = 4.dp,
                        ),
                    ) {
                        items(
                            count = sorted.size,
                            key = { i -> sorted[i].url },
                        ) { i ->
                            val ep = sorted[i]
                            val isCurrent = ep.url == currentUrl
                            Surface(
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                onClick = { onEpisodeSelect(ep) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                ) {
                                    Text(
                                        formatEpisodeNumber(ep.episodeNumber),
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.width(46.dp),
                                    )
                                    Text(
                                        com.confused.anikuta.core.common.EpisodeTitleParser
                                            .getDisplayTitle(ep.name, ep.episodeNumber),
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isCurrent) {
                                        Text(
                                            "NOW",
                                            fontFamily = RobotoFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** mm:ss (or h:mm:ss for the long ones) — the pills' compact time format. */
private fun formatPillTime(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, sec)
    } else {
        String.format("%d:%02d", m, sec)
    }
}

/**
 * Builds the video-caching identity for the currently playing video.
 *
 * Video caching (test-feature branch): the cache key must be STABLE across sessions
 * for the same server+audio+quality pick — extension localhost proxy URLs change
 * every resolve (D-066), so identity comes from mainId + episodeNumber + sourceId +
 * the ResolverVideo.videoTitle's "server|audio|quality" prefix (videoTitle is the
 * codebase's documented stable-identity string — see ResolverTypes.kt).
 *
 * Returns null when a stable identity can't be derived (no videoTitle / blank
 * mainId) → caching is skipped for that playback (fail-open, play direct).
 */
private fun buildCacheId(
    mainId: String,
    animeTitle: String,
    episodeNumber: Float,
    episodeTitle: String,
    sourceId: Long,
    videoTitle: String,
): PlaybackVideoId? {
    if (mainId.isBlank()) return null
    val serverKey = serverKeyFromVideoTitle(videoTitle)
    if (serverKey.isBlank()) return null
    return PlaybackVideoId(
        mainId = mainId,
        animeTitle = animeTitle,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        sourceId = sourceId,
        serverKey = serverKey,
        quality = serverKey.substringAfterLast('|'),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Phase 4: Star Rating Bar (TEMPORARY — for testing)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A row of 10 clickable stars for per-episode rating. Each star = 10 points.
 * Tapping a star sets the rating. Tapping the same star again clears it.
 */
@Composable
private fun WatchStarRatingBar(
    rating: Int?,
    onRate: (Int) -> Unit,
) {
    val currentStars = rating?.let { (it / 10).coerceIn(0, 10) } ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..10) {
            Icon(
                imageVector = if (i <= currentStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Rate $i stars",
                tint = if (i <= currentStars) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        if (i == currentStars) onRate(0) else onRate(i)
                    },
            )
        }
    }
}

