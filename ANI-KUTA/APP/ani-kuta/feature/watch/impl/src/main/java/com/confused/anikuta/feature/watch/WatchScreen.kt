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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import com.confused.anikuta.core.player.PlayerLoadingState
import com.confused.anikuta.core.player.PlayerMode
import com.confused.anikuta.core.player.PlayerObserver
import com.confused.anikuta.core.player.PlayerStateHolder
import com.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import com.confused.anikuta.core.playbackcache.PlaybackCacheManager
import com.confused.anikuta.core.playbackcache.PlaybackVideoId
import com.confused.anikuta.core.playbackcache.serializeTracks
import com.confused.anikuta.core.playbackcache.serverKeyFromVideoTitle
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
            if (dur > 0 && epUrl.isNotBlank()) {
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

    // ── WP-B2 + WP-B3: On FILE_LOADED (READY transition), reset auto-mark + seek to resume position ──
    // WP-B2: resetAutoMarkSuppressed re-arms the 85% auto-mark (was NEVER called → CF1 broken).
    // WP-B3: seek to saved startPosition (only on the initial load, not on quality/episode switch).
    val loadingState by stateHolder.loadingState.collectAsState()
    var hasResumed by remember { mutableStateOf(false) }

    // D-192: Activity tracker — track WATCH_START on FILE_LOADED.
    val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker = koinInject()

    LaunchedEffect(loadingState) {
        if (loadingState == PlayerLoadingState.READY && mpvInitialized) {
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
            // WP-B3: seek to the saved resume position (only on the initial load).
            // If watchKey.startPosition is > 0 (passed from DetailsScreen), use it.
            // Otherwise look up from the watch progress store directly.
            if (!hasResumed) {
                hasResumed = true
                val resumePos = if (watchKey.startPosition > 0) {
                    watchKey.startPosition
                } else {
                    val initialEpKey = buildEpisodeKey(watchKey.mainId, watchKey.episodeNumber)
                    runCatching { watchProgressStore.get(initialEpKey) }.getOrNull()?.position ?: 0L
                }
                if (resumePos > 0) {
                    delay(300L) // ensure MPV has fully processed FILE_LOADED before seeking
                    MPVLib.command(arrayOf("seek", resumePos.toString(), "absolute"))
                    Logger.i(TAG) { "WP-B3: Resumed from position ${resumePos}s" }
                }
            }
        }
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
                MPVLib.command(arrayOf("loadfile", cachedUrl(currentVideoUrl, headers), "replace"))
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
                val obs = PlayerObserver(stateHolder, subtitleEngine)
                obs.mpvView = view  // Wire so observer can call loadTracks() on FILE_LOADED
                observer = obs      // Store reference so switch handlers can set pending tracks

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
                    Logger.i(TAG) { "loadfile command sent. Waiting for FILE_LOADED event..." }
                }
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
        try {
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            MPVLib.command(arrayOf("loadfile", cachedUrl(currentVideoUrl, headers), "replace"))
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
        try {
            // D-199: Always set headers (even for localhost proxy — see initial loadfile comment).
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            MPVLib.command(arrayOf("loadfile", cachedUrl(video.url, headers), "replace"))
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

        // WP-B4: Save the OLD episode's progress BEFORE switching (was only saved
        // on the 10s timer + onDispose — if the user switched within 10s, up to
        // 10s of progress was lost). Reads the current state before
        // updateCurrentEpisode overwrites it with the new episode's data.
        val oldPos = stateHolder.position.value
        val oldDur = stateHolder.duration.value
        val oldEpUrl = stateHolder.currentEpisodeUrl.value
        val oldEpNum = stateHolder.currentEpisodeNumber.value
        if (oldDur > 0 && oldEpUrl.isNotBlank() && watchKey.mainId.isNotBlank()) {
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

        if (offlineUri != null) {
            // ── Offline playback path (downloaded episode) ──
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
            scope.launch {
                try {
                    // Close the old ParcelFileDescriptor before opening a new one.
                    runCatching { mpvParcelFileDescriptor?.close() }
                    mpvParcelFileDescriptor = null

                    // Convert content:// URI to fd:// for MPV.
                    val uri = android.net.Uri.parse(offlineUri)
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        val fdUrl = "fd://${pfd.fd}"
                        mpvParcelFileDescriptor = pfd

                        // Look up the downloaded episode for subtitle URIs.
                        val dlEp = downloadManager.getDownloadedEpisodes().value
                            .firstOrNull { it.content.mainId == currentMainId && it.episode.episodeKey == ep.url }
                        val subUris = dlEp?.subtitleUris ?: emptyList()
                        if (subUris.isNotEmpty()) {
                            observer?.let { obs ->
                                obs.pendingSubtitleTracks = subUris.mapIndexed { i, u ->
                                    val lang = com.confused.anikuta.core.common.Logger.w(TAG) { "subtitle URI: ${u.take(60)}" }
                                    Pair(u, "Subtitle ${i + 1}")
                                }
                                obs.trackHeaders = ""
                            }
                            Logger.i(TAG) { "Episode switch — set ${subUris.size} pending subtitle track(s) for offline episode: ${subUris.joinToString("; ") { it.take(60) }}" }
                        } else {
                            Logger.w(TAG) { "Episode switch — no subtitle URIs found for downloaded episode (mainId=$currentMainId, epUrl=${ep.url})" }
                        }

                        // For fd:// URLs, delay 500ms for surface readiness (same as initial load).
                        mpvView?.postDelayed({
                            try {
                                MPVLib.command(arrayOf("loadfile", fdUrl, "replace"))
                                MPVLib.setPropertyBoolean("pause", false)
                                Logger.i(TAG) { "Episode switch — loadfile sent for offline fd:// (fd=${pfd.fd})" }
                            } catch (e: Exception) {
                                Logger.e(TAG, e) { "Episode switch — offline loadfile failed" }
                                stateHolder.setSwitchingError("Offline playback failed: ${e.message}")
                            }
                        }, 500)
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
                                    val servers = videoResolver.buildServers(state.rawEntries, source.name)
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
            episodeMetadata = episodeMetadata,
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
                                    text = "${episodeList.size}",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                // D-230: Lazy episode rows — virtualized! Only ~10-15 rows
                // composed at a time (the visible window), not all 1000.
                items(episodeList, key = { it.url }) { ep ->
                    val isCurrent = ep.url == currentEpisodeUrl
                    val epNum = ep.episodeNumber.toInt()
                    val meta = episodeMetadata[epNum]
                    EpisodeListRow(
                        episode = ep,
                        metadata = meta,
                        isCurrent = isCurrent,
                        onClick = {
                            if (!isCurrent) {
                                onEpisodeSwitch(ep)
                            }
                        },
                    )
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
    metadata: WatchEpisodeMeta?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val displayTitle = metadata?.title
        ?: com.confused.anikuta.core.common.EpisodeTitleParser
            .getDisplayTitle(episode.name, episode.episodeNumber)
    val epNumText = com.confused.anikuta.core.common.EpisodeTitleParser
        .formatEpisodeNumber(episode.episodeNumber)
    val thumbnailUrl = metadata?.thumbnailUrl
    val description = metadata?.description
    val dateText = if (metadata != null && metadata.airDateMillis > 0) {
        formatDate(metadata.airDateMillis)
    } else null
    val audio = parseAudioAvailability(metadata?.scanlator, episode.name)

    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Thumbnail with EP tag overlay ──
                if (thumbnailUrl != null) {
                    Box(
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        coil3.compose.AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            Text(
                                text = "EP $epNumText",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
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
                    Spacer(Modifier.width(10.dp))
                }
                // ── Right column: title + date/audio pills ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = displayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (dateText != null || audio.hasAny || description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (dateText != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Text(
                                        text = dateText,
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
                            if (audio.hasAny) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        audio.labels.forEachIndexed { idx, label ->
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
                            // Download button — shown here (next to pills) when no synopsis.
                            if (description.isNullOrBlank()) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
            // ── Synopsis + download button ──
            // If synopsis exists: download at bottom-right of synopsis.
            // If no synopsis: download at the right of the date/audio pills row.
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = description,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 8.dp, bottom = 2.dp),
                    )
                }
            }
            // No synopsis: download button already rendered inline in the pills row above.
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
        // Best-effort fallback (NOT the standard format — won't be backup-stable).
        return "unknown|${String.format("%05d", episodeNumber.toInt())}"
    }
    return "$mainId|${String.format("%05d", episodeNumber.toInt())}"
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

