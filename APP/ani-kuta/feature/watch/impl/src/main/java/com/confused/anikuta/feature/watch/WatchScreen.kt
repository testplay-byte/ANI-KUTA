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
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
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

    var mpvView by remember { mutableStateOf<AnikutaMPVView?>(null) }
    var mpvInitialized by remember { mutableStateOf(false) }

    val playerMode by stateHolder.playerMode.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val errorMessage by stateHolder.errorMessage.collectAsState()

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
                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    // ── Resolved servers (for QualitySheet) ──
    // Read from the registry if the Details screen passed a key.
    val resolvedServers = remember(watchKey.resolvedVideosKey) {
        if (watchKey.resolvedVideosKey.isNotBlank()) {
            com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry.get(watchKey.resolvedVideosKey)
                ?: emptyList()
        } else emptyList()
    }
    var currentVideoTitle by remember { mutableStateOf("") }
    var currentVideoUrl by remember { mutableStateOf(watchKey.videoUrl) }
    var currentVideoHeaders by remember { mutableStateOf(watchKey.videoHeaders) }
    var currentServerName by remember { mutableStateOf("") }
    var currentAudioVersion by remember { mutableStateOf("") }

    // ── Init MPV + load video (once) ──
    val initMpv: (AnikutaMPVView) -> Unit = remember {
        { view ->
            if (!mpvInitialized) {
                mpvInitialized = true
                val obs = PlayerObserver(stateHolder)
                obs.mpvView = view  // Wire so observer can call loadTracks() on FILE_LOADED

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

                // CRITICAL: Set HTTP headers BEFORE loadfile.
                // Without proper headers, upstream servers return 403 Forbidden.
                val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                    else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                try {
                    MPVLib.setOptionString("http-header-fields", headers)
                    Logger.i(TAG) { "=== MPV LOADFILE ===" }
                    Logger.i(TAG) { "URL: $currentVideoUrl" }
                    Logger.i(TAG) { "Headers (full): $headers" }
                    Logger.i(TAG) { "Video title: $currentVideoTitle" }
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

    // ── Destroy MPV on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            mpvView?.let { view ->
                runCatching { MPVLib.command(arrayOf("stop")) }
                runCatching { view.destroy() }
            }
            Logger.i(TAG) { "MPV destroyed" }
        }
    }

    // ── Layout ──
    // Sheet visibility state (shared between minimized + fullscreen)
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSettingsSheet by remember { mutableStateOf(false) }

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
            stateHolder.updateError(null)
            stateHolder.updateLoadingState(PlayerLoadingState.LOADING)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Retry failed" }
            stateHolder.updateError("Retry failed: ${e.message}")
        }
    }

    // ── Dismiss error handler — just clear the error (close button) ──
    val onDismissError: () -> Unit = {
        Logger.i(TAG) { "Error dismissed by user (close button)" }
        stateHolder.updateError(null)
    }

    // ── Quality switch handler — re-loadfile with new video ──
    val onQualitySelected: (com.confused.anikuta.core.videoresolver.ResolverVideo) -> Unit = { video ->
        Logger.i(TAG) { "=== QUALITY SWITCH ===" }
        Logger.i(TAG) { "New video: ${video.quality} (${video.url})" }
        Logger.i(TAG) { "New headers: ${(video.videoHeaders ?: "").take(120)}" }
        currentVideoUrl = video.url
        currentVideoTitle = video.videoTitle
        currentVideoHeaders = video.videoHeaders ?: ""
        // Set switching flag so efEvent from old file doesn't show a spurious error.
        stateHolder.setSwitching(true)
        try {
            val headers = if (currentVideoHeaders.isNotBlank()) currentVideoHeaders
                else "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
            MPVLib.setOptionString("http-header-fields", headers)
            MPVLib.command(arrayOf("loadfile", video.url, "replace"))
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to switch quality" }
            stateHolder.updateError("Failed to switch: ${e.message}")
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
            onRetry = onRetry,
            onDismissError = onDismissError,
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
            }
        }

        // ── Scrollable content: episode description + episode list ──
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
                            text = "Currently playing episode ${formatEpisodeNumber(watchKey.episodeNumber)}",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = watchKey.episodeTitle.ifBlank { "Episode ${formatEpisodeNumber(watchKey.episodeNumber)}" },
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
                                val isCurrent = ep.url == watchKey.episodeUrl
                                EpisodeListRow(
                                    episode = ep,
                                    isCurrent = isCurrent,
                                    onClick = {
                                        Logger.i(TAG) { "Episode tapped: ${ep.name} (not implemented yet)" }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
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
            onRetry = onRetry,
            onDismissError = onDismissError,
            animeTitle = watchKey.animeTitle,
            episodeInfo = if (watchKey.episodeTitle.isNotBlank()) "EP ${formatEpisodeNumber(watchKey.episodeNumber)}" else "",
            qualityInfo = watchKey.quality,
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
//  Episode list row (minimized mode)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodeListRow(
    episode: SimpleEpisode,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
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
                        text = formatEpisodeNumber(episode.episodeNumber),
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
                text = episode.name,
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

private fun formatEpisodeNumber(num: Float): String = when {
    num == num.toInt().toFloat() -> num.toInt().toString()
    else -> num.toString()
}

