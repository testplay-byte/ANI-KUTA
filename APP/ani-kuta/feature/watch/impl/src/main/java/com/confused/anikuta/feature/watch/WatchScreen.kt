package com.confused.anikuta.feature.watch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.player.AnikutaMPVView
import com.confused.anikuta.core.player.PlayerInitializer
import com.confused.anikuta.core.player.PlayerMode
import com.confused.anikuta.core.player.PlayerObserver
import com.confused.anikuta.core.player.PlayerStateHolder
import com.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import com.confused.anikuta.core.videoresolver.ResolverVideo
import com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import `is`.xyz.mpv.MPVLib
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
    val episodeList = remember { watchKey.parseEpisodeList() }
    val playerPreferences = koinInject<com.confused.anikuta.core.preferences.PlayerPreferences>()
    val extensionManager = koinInject<com.confused.anikuta.data.extension.manager.ExtensionManager>()
    val videoResolver = koinInject<com.confused.anikuta.core.videoresolver.VideoResolver>()
    val watchProgressStore = koinInject<WatchProgressStore>()
    val scope = rememberCoroutineScope()

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

    // ── Fatal-error watchdog (15s after video starts playing) ──
    // Catches HLS demuxer errors that don't trigger END_FILE (e.g. "error reading
    // packet: Invalid argument" → "treating it as fatal error"). These leave the
    // player stuck: position stays at 0 (or stuck at duration-2) with no error.
    // After 15s of "stuck", we show "This server is not responding" so the user
    // can retry or switch server.
    // Conditions: duration > 0 (video loaded), not playing, no error already shown,
    // and either position == 0 OR position >= duration - 2.
    LaunchedEffect(isPlaying, duration, position) {
        if (duration > 0 && !isPlaying && stateHolder.errorMessage.value == null && !stateHolder.isSwitching.value) {
            val stuck = position == 0 || position >= duration - 2
            if (stuck) {
                delay(15_000L)
                // Re-check after delay — if still stuck, show error
                if (duration > 0 && !stateHolder.isPlaying.value &&
                    stateHolder.errorMessage.value == null && !stateHolder.isSwitching.value &&
                    (stateHolder.position.value == 0 || stateHolder.position.value >= duration - 2)
                ) {
                    Logger.w(TAG) { "Fatal-error watchdog: video stuck for 15s" }
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
    // Phase 5c capture-only: saves to InMemoryWatchProgressStore. Restore is
    // Phase 5e when the database is wired. Reads values directly from the state
    // holder (not collected state) so they're fresh at save time.
    LaunchedEffect(mpvInitialized) {
        if (!mpvInitialized) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            val pos = stateHolder.position.value
            val dur = stateHolder.duration.value
            val epUrl = stateHolder.currentEpisodeUrl.value
            if (dur > 0 && epUrl.isNotBlank()) {
                val epKey = "${watchKey.sourceId}|$epUrl"
                val progress = WatchProgress(
                    episodeKey = epKey,
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

    // ── Resolved servers (for QualitySheet) — reactive to episode switches ──
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
                MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                Logger.i(TAG) { "Auto-retry: loadfile re-sent (banner stays visible)" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Auto-retry failed" }
                stateHolder.setSwitchingError("Retry failed: ${e.message}")
            }
        }
    }

    // ── Init MPV + load video (once) ──
    val initMpv: (AnikutaMPVView) -> Unit = remember {
        { view ->
            if (!mpvInitialized) {
                mpvInitialized = true
                val obs = PlayerObserver(stateHolder)
                obs.mpvView = view  // Wire so observer can call loadTracks() on FILE_LOADED
                observer = obs      // Store reference so switch handlers can set pending tracks

                // Set pending external subtitle/audio tracks from the initial
                // picked video. The observer sends sub-add/audio-add on FILE_LOADED.
                initialPickedVideo?.let { pv ->
                    obs.pendingSubtitleTracks = pv.subtitleTracks.map { Pair(it.url, it.lang) }
                    obs.pendingAudioTracks = pv.audioTracks.map { Pair(it.url, it.lang) }
                    obs.trackHeaders = pv.videoHeaders ?: ""
                    Logger.i(TAG) { "Pending external tracks: ${pv.subtitleTracks.size} subs, ${pv.audioTracks.size} audio" }
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
                val isLocalhost = currentVideoUrl.contains("127.0.0.1") ||
                    currentVideoUrl.contains("localhost")
                try {
                    if (!isLocalhost) {
                        val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                            else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        MPVLib.setOptionString("http-header-fields", headers)
                        Logger.i(TAG) { "=== MPV LOADFILE ===" }
                        Logger.i(TAG) { "URL: $currentVideoUrl" }
                        Logger.i(TAG) { "Headers (full): $headers" }
                        Logger.i(TAG) { "Video title: $currentVideoTitle" }
                    } else {
                        Logger.i(TAG) { "=== MPV LOADFILE (localhost proxy) ===" }
                        Logger.i(TAG) { "URL: $currentVideoUrl" }
                        Logger.i(TAG) { "No headers set (localhost proxy)" }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Failed to set http-header-fields: ${e.message}" }
                }

                Logger.i(TAG) { "Sending loadfile command to MPV..." }
                MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
                MPVLib.setPropertyBoolean("pause", false)
                Logger.i(TAG) { "loadfile command sent. Waiting for FILE_LOADED event..." }
            }
        }
    }

    // ── Destroy MPV on dispose + save final progress + remove observers ──
    DisposableEffect(Unit) {
        onDispose {
            // Save final progress before destroying MPV.
            val pos = stateHolder.position.value
            val dur = stateHolder.duration.value
            val epUrl = stateHolder.currentEpisodeUrl.value
            if (dur > 0 && epUrl.isNotBlank()) {
                val epKey = "${watchKey.sourceId}|$epUrl"
                val progress = WatchProgress(
                    episodeKey = epKey,
                    position = pos.toLong(),
                    duration = dur.toLong(),
                    completed = false,
                    completedAt = null,
                    lastWatchedAt = System.currentTimeMillis(),
                )
                scope.launch {
                    runCatching { watchProgressStore.save(epKey, progress) }
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
            Logger.i(TAG) { "MPV destroyed + observers removed" }
        }
    }

    // ── Layout ──
    // Sheet visibility state (shared between minimized + fullscreen)
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSettingsSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    // ── Retry handler — re-load the current video URL ──
    val onRetry: () -> Unit = {
        Logger.i(TAG) { "=== RETRY: Re-loading video ===" }
        Logger.i(TAG) { "URL: $currentVideoUrl" }
        Logger.i(TAG) { "Headers: ${currentVideoHeaders.take(120)}" }
        stateHolder.setSwitching(true)
        try {
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            MPVLib.command(arrayOf("loadfile", currentVideoUrl, "replace"))
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
        try {
            // For localhost proxy URLs, don't set upstream headers.
            val isLocalhost = video.url.contains("127.0.0.1") || video.url.contains("localhost")
            if (!isLocalhost) {
                val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                    else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                MPVLib.setOptionString("http-header-fields", headers)
            } else {
                Logger.i(TAG) { "Quality switch — localhost proxy URL, no headers set" }
            }
            MPVLib.command(arrayOf("loadfile", video.url, "replace"))
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
            } else {
                MPVLib.setPropertyInt("sid", trackId)
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to set subtitle track: ${e.message}" }
        }
    }

    // ── Speed selection handler — applies live via setPropertyDouble ──
    val onSpeedSelected: (Float) -> Unit = { speed ->
        Logger.i(TAG) { "Speed → $speed" }
        stateHolder.updatePlaybackSpeed(speed)
        try { mpvView?.playbackSpeed = speed }
        catch (e: Exception) { Logger.w(TAG) { "Failed to set speed: ${e.message}" } }
    }

    // ── Episode switch handler — re-resolve and load a different episode ──
    val onEpisodeSwitch: (SimpleEpisode) -> Unit = { ep ->
        Logger.i(TAG) { "=== EPISODE SWITCH ===" }
        Logger.i(TAG) { "New episode: ${ep.name} (num: ${ep.episodeNumber}, url: ${ep.url})" }

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

            scope.launch {
                try {
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
                                if (state.videos.isNotEmpty()) {
                                    val video = state.videos.first()
                                    Logger.i(TAG) { "Episode switch — got ${state.videos.size} videos, picking first: ${video.quality} (${video.url.take(60)})" }

                                    // Build structured servers for QualitySheet +
                                    // find the matching ResolverVideo (first video
                                    // of first server's first audio version) to get
                                    // external tracks + videoTitle.
                                    val servers = videoResolver.buildServers(state.rawVideos, source.name)
                                    val newRegistryKey = if (servers.isNotEmpty()) {
                                        ResolvedVideosRegistry.put(servers)
                                    } else ""
                                    val pickedResolverVideo: ResolverVideo? = servers
                                        .firstOrNull()?.audioVersions?.firstOrNull()?.videos?.firstOrNull()

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

                                    // Set headers + loadfile.
                                    // CRITICAL: For localhost proxy URLs (AniKotoS),
                                    // do NOT set upstream headers (Referer, Origin, etc.).
                                    // The proxy doesn't need them and they may cause
                                    // issues. Only set headers for non-localhost URLs.
                                    val isLocalhost = video.url.contains("127.0.0.1") ||
                                        video.url.contains("localhost")
                                    if (!isLocalhost && video.headers.isNotBlank()) {
                                        MPVLib.setOptionString("http-header-fields", video.headers)
                                        Logger.i(TAG) { "Set http-header-fields for non-localhost URL" }
                                    } else if (!isLocalhost) {
                                        MPVLib.setOptionString("http-header-fields",
                                            "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                                    } else {
                                        Logger.i(TAG) { "Localhost proxy URL — no headers set" }
                                    }
                                    MPVLib.command(arrayOf("loadfile", video.url, "replace"))
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

    // ── Skip forward (next episode) handler ──
    // Finds the next episode in the list and switches to it. If there's no next
    // episode, does nothing. Declared AFTER onEpisodeSwitch (Kotlin requires it).
    val onSkipForward: () -> Unit = {
        val currentUrl = stateHolder.currentEpisodeUrl.value
        val currentIndex = episodeList.indexOfFirst { it.url == currentUrl }
        if (currentIndex >= 0 && currentIndex < episodeList.size - 1) {
            val nextEp = episodeList[currentIndex + 1]
            Logger.i(TAG) { "Skip forward → episode ${nextEp.episodeNumber} (${nextEp.name})" }
            onEpisodeSwitch(nextEp)
        } else {
            Logger.i(TAG) { "Skip forward — no next episode (at end of list)" }
        }
    }

    if (playerMode == PlayerMode.FULLSCREEN) {
        FullscreenMode(
            watchKey = watchKey,
            stateHolder = stateHolder,
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = { mpvView = it },
            onTogglePlay = { MPVLib.setPropertyBoolean("pause", isPlaying) },
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
        )
    } else {
        MinimizedMode(
            watchKey = watchKey,
            episodeList = episodeList,
            stateHolder = stateHolder,
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = { mpvView = it },
            onTogglePlay = { MPVLib.setPropertyBoolean("pause", isPlaying) },
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
            onRefreshTracks = {
                // Manually reload tracks from MPV when the sheet opens.
                // Catches cases where tracks were loaded too early (before
                // external subs finished downloading) or where the track list
                // changed since the last load.
                observer?.let { obs ->
                    val view = mpvView
                    if (view != null) {
                        try {
                            val (subs, audio) = view.loadTracks()
                            stateHolder.updateTracks(subs, audio)
                            Logger.i(TAG) { "Manual track refresh: ${subs.size} subs, ${audio.size} audio" }
                        } catch (e: Exception) {
                            Logger.w(TAG) { "Manual track refresh failed: ${e.message}" }
                        }
                    }
                }
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
}

// ════════════════════════════════════════════════════════════════════════════
//  MINIMIZED MODE — floating top bar + 16:9 player + ported MinimizedControls + episode list
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MinimizedMode(
    watchKey: WatchKey,
    episodeList: List<SimpleEpisode>,
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
) {
    val listState = rememberLazyListState()
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
                )

                // Episode switching overlay — shown over the player while a new
                // episode resolves + loads. ONLY shown for episode switches (not
                // quality/server switches — those just show the buffering spinner).
                if (isSwitchingEpisode) {
                    EpisodeSwitchingOverlay(
                        episodeTitle = switchingEpisodeTitle.ifBlank { null },
                    )
                }
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
                            text = com.confused.anikuta.core.common.EpisodeTitleParser
                                .getDisplayTitle(currentEpisodeTitle, currentEpisodeNumber),
                            fontFamily = RobotoFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (episodeList.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
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
                                        text = "${episodeList.size}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            episodeList.forEach { ep ->
                                val isCurrent = ep.url == currentEpisodeUrl
                                EpisodeListRow(
                                    episode = ep,
                                    isCurrent = isCurrent,
                                    onClick = {
                                        if (!isCurrent) {
                                            onEpisodeSwitch(ep)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            } // end LazyColumn

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
            onSkipForward = onSkipForward,
            onRetry = onRetry,
            onDismissError = onDismissError,
            animeTitle = watchKey.animeTitle,
            episodeInfo = if (watchKey.episodeTitle.isNotBlank()) "EP ${formatEpisodeNumber(watchKey.episodeNumber)}" else "",
            qualityInfo = watchKey.quality,
            currentSpeed = currentSpeed,
        )

        // Episode switching overlay — shown over the fullscreen player while a
        // new episode resolves + loads. ONLY shown for episode switches (not
        // quality/server switches).
        if (isSwitchingEpisode) {
            EpisodeSwitchingOverlay(
                episodeTitle = switchingEpisodeTitle.ifBlank { null },
            )
        }
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
//  Episode list row (minimized mode)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodeListRow(
    episode: SimpleEpisode,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    // Use EpisodeTitleParser to get a clean display title. This handles:
    //  - "Episode 5 - Title" → "Title"
    //  - Hashes/code-like names → "Episode N" (fallback)
    //  - episode_number <= 0 → "?" (bad extension data)
    val displayTitle = com.confused.anikuta.core.common.EpisodeTitleParser
        .getDisplayTitle(episode.name, episode.episodeNumber)
    val epNumText = com.confused.anikuta.core.common.EpisodeTitleParser
        .formatEpisodeNumber(episode.episodeNumber)

    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(width = 44.dp, height = 32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = epNumText,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = displayTitle,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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

