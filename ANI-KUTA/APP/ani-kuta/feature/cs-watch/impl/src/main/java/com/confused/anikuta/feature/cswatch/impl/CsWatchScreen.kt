package com.confused.anikuta.feature.cswatch.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsEngineEvent
import com.confused.anikuta.core.csplayer.CsPlayerEngine
import com.confused.anikuta.core.csplayer.CsSubtitleStyle
import com.confused.anikuta.core.csplayer.CsVideoTrack
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.feature.cswatch.api.CsWatchKey
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

private const val TAG = "Anikuta:CS:Watch"

/** The watch page's display mode (the aniyomi screen's PlayerMode, replicated). */
private enum class CsPlayerMode { MINIMIZED, FULLSCREEN }

/**
 * The CloudStream watch screen (task 52 / round 12; task 54 / round 14).
 *
 * Architecture (doc cloudstream-v2/02-PLAYBACK-PLAN.md §1): the screen
 * composable owns the Media3 engine + player view + lifecycle effects (the
 * ADR-025 player carve-out — same exemption the MPV watch screen uses);
 * [CsWatchViewModel] owns resolution state, link selection and watch progress.
 * It shares ZERO code with the aniyomi :feature:watch — visual parity via the
 * same design tokens, behavioral parity via the same WatchProgressStore.
 *
 * Task 54 (round 14 — watch-page parity): the screen is now a real two-mode
 * watch PAGE like the aniyomi WatchScreen:
 *  - MINIMIZED (portrait, default): [CsWatchPage] — pill top bar + 16:9
 *    rounded player + "Currently playing" description + episode list below;
 *  - FULLSCREEN (landscape, edge-to-edge): [CsFullscreenControls] in the
 *    aniyomi player's visual language (lock, frosted action row, canvas
 *    seekbar, speed sheet).
 * The RESOLVING / FAILED / NO_LINKS phases render INSIDE the 16:9 player box
 * in minimized mode (the page content stays reachable underneath).
 */
@Composable
fun CsWatchScreen(
    key: CsWatchKey,
    onBack: () -> Unit,
    viewModel: CsWatchViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    // The playback OkHttp client = the CS runtime's plugin client (registered
    // by :data:cloudstream's DI — keeps com.lagradost.* out of this module).
    val playbackClient = koinInject<OkHttpClient>(named("cloudstreamPlayback"))

    // ── Engine lifecycle (main thread — ExoPlayer's threading rule) ──────────
    // Task 55: the preferred subtitle languages ride the engine constructor
    // (MPV slang parity — the engine stays preference-free itself).
    val playerPreferences = koinInject<PlayerPreferences>()
    val engine = remember {
        CsPlayerEngine(
            context = context.applicationContext,
            baseClient = playbackClient,
            preferredSubtitleLanguages = { playerPreferences.preferredSubtitleLanguages },
        )
    }
    val engineState by engine.state.collectAsState()

    // The single PlayerView, re-parented between the minimized player box and
    // the fullscreen surface (the aniyomi screen's PlayerSurface pattern).
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    /** F3: engine position belongs to the CURRENT episode only when the loaded
     *  URL matches the state's current link — otherwise a switch is in flight
     *  and saving would write episode N-1's progress under episode N's key. */
    fun engineBelongsToCurrentLink(st: com.confused.anikuta.core.csplayer.CsEngineState): Boolean =
        st.currentLinkUrl != null && st.currentLinkUrl == viewModel.uiState.value.currentLink?.url

    DisposableEffect(engine) {
        onDispose {
            // Final progress save on exit (the ticker only fires every 10 s) —
            // ONLY when the engine still plays the current link (F3: during an
            // episode switch the engine holds the OLD episode's position while
            // the VM already points at the new one).
            val st = engine.state.value
            if (st.durationMs > 0 && st.positionMs > 0 && engineBelongsToCurrentLink(st)) {
                viewModel.saveProgress(st.positionMs, st.durationMs)
            }
            engine.release()
        }
    }

    LaunchedEffect(key) { viewModel.initialize(key) }

    // ── Display mode + window choreography (the aniyomi screen's pattern) ────
    var playerMode by remember { mutableStateOf(CsPlayerMode.MINIMIZED) }
    var controlsLocked by remember { mutableStateOf(false) }

    // Keep-screen-on while the screen is alive.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Immersive/orientation per mode. Only fullscreen flips
    // setDecorFitsSystemWindows (the aniyomi screen's double-top-padding
    // lesson — minimized NEVER sets it back to true). Dispose restores the
    // app-wide edge-to-edge defaults (no leaked orientation, no hidden bars).
    DisposableEffect(playerMode) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (playerMode == CsPlayerMode.FULLSCREEN) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                (context as? android.app.Activity)?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                // MINIMIZED: show the bars; do NOT flip DecorFitsSystemWindows.
                controller.show(WindowInsetsCompat.Type.systemBars())
                (context as? android.app.Activity)?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        onDispose {
            val w = (context as? android.app.Activity)?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            (context as? android.app.Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Back: fullscreen → minimized; minimized → leave the screen.
    BackHandler(enabled = playerMode == CsPlayerMode.FULLSCREEN) {
        playerMode = CsPlayerMode.MINIMIZED
    }
    BackHandler(enabled = playerMode == CsPlayerMode.MINIMIZED) {
        onBack()
    }

    // Task 53 / RC-3 + R13-REVIEW F3: a NEW episode's resolution start
    // hard-resets the engine — whatever was loaded stops and clears, so nothing
    // stale can play under the resolving overlay regardless of any race.
    // Declared ABOVE the play trigger: on frames where a seed's reset tick and
    // its play request land together, effects run in declaration order —
    // reset-then-start. (Declared below, the same frame would start-then-reset
    // and wipe the fresh media.)
    LaunchedEffect(uiState.engineResetTick) {
        if (uiState.engineResetTick > 0) {
            engine.reset()
        }
    }

    // ── The play trigger: VM says "load this" → engine loads it ──────────────
    // Task 53 / RC-3: the body reads the LIVE StateFlow (viewModel.uiState.value),
    // NOT the composed snapshot — the collectAsState State object lags the
    // StateFlow by one dispatch, and an initialize()-reset landing between
    // composition and this coroutine used to make it replay the PREVIOUS
    // episode's link. The generation lock makes that class of race a logged
    // no-op: a play request only touches the engine while its generation
    // still matches the CURRENT resolution attempt.
    LaunchedEffect(uiState.playRequestId) {
        val live = viewModel.uiState.value
        val link = live.playLink
        if (live.playRequestId <= 0 || link == null) {
            Logger.d(TAG) { "play trigger: no active request (id=${live.playRequestId}) — idle" }
            return@LaunchedEffect
        }
        if (live.playGeneration != live.resolveGeneration) {
            Logger.w(TAG) {
                "play trigger: REJECTED stale request id=${live.playRequestId} " +
                    "gen=${live.playGeneration} != current=${live.resolveGeneration} " +
                    "link=${link.displayLabel.take(40)}"
            }
            return@LaunchedEffect
        }
        Logger.i(TAG) {
            "play trigger: ACCEPT id=${live.playRequestId} gen=${live.playGeneration} " +
                "resume=${live.playIsResume} keepPosition=${live.playKeepPosition} " +
                "link=${link.displayLabel}"
        }
        when {
            // Resume (fresh episode with progress, or same-key re-entry): seek.
            live.playIsResume -> engine.start(link, live.playSubtitles, live.playStartPositionMs)
            // Same-episode link switch (quality/source change, error fallback,
            // subtitle reattach): keep the position (R12-REVIEW F2).
            live.playKeepPosition -> engine.switchLink(link, live.playSubtitles)
            // A NEW episode's first link: FRESH start — never inherit the
            // previous episode's position (F2: auto-advance cascade).
            else -> engine.start(link, live.playSubtitles, 0L)
        }
        // Subtitle reattach flow: after a reload, auto-select the picked sub
        // once its track appears (bounded poll — tracks land post-prepare).
        val pendingSubId = viewModel.consumePendingSubSelectId() ?: return@LaunchedEffect
        var selected = false
        repeat(40) {
            if (engine.selectTextTrackById(pendingSubId)) {
                selected = true
                return@LaunchedEffect
            }
            delay(250)
        }
        if (!selected) {
            Logger.w("Anikuta:CS:Subs") { "reattached sub never exposed its track (id=$pendingSubId)" }
        }
    }

    // ── Engine events → VM decisions ─────────────────────────────────────────
    LaunchedEffect(engine) {
        engine.events.collect { event ->
            when (event) {
                is CsEngineEvent.PlaybackError -> {
                    // F5: the event carries the URL it belongs to — stale errors
                    // (a link already switched away from) are rejected by the VM.
                    val reason = event.error.httpCode?.let { "HTTP $it" } ?: event.error.kind.name.lowercase()
                    Logger.w(TAG) {
                        "engine error → fallback (url=${event.linkUrl?.take(64)}, reason=$reason)"
                    }
                    viewModel.onEngineError(event.linkUrl, reason)
                }
                CsEngineEvent.Ended -> {
                    val st = engine.state.value
                    viewModel.saveProgress(st.positionMs, st.durationMs)
                    if (!viewModel.onEpisodeEnded()) {
                        Logger.i(TAG) { "episode ended — no next episode" }
                    }
                }
            }
        }
    }

    // R12-REVIEW F3-adjacent: an episode switch leaves the OLD episode playing
    // under the resolving overlay — pause it the moment the phase changes.
    LaunchedEffect(uiState.phase) {
        if (uiState.phase != CsWatchViewModel.Phase.PLAYING) {
            engine.pause()
        }
    }

    // ── Periodic progress persistence (the aniyomi screen's 10 s cadence) ────
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            val st = engine.state.value
            if (st.durationMs > 0 && st.positionMs > 0 &&
                viewModel.uiState.value.phase == CsWatchViewModel.Phase.PLAYING &&
                engineBelongsToCurrentLink(st)
            ) {
                viewModel.saveProgress(st.positionMs, st.durationMs)
            }
        }
    }

    // ── Controls auto-hide (5s minimized / 4s fullscreen — the aniyomi timing) ─
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, engineState.isPlaying, playerMode) {
        if (controlsVisible && engineState.isPlaying) {
            delay(if (playerMode == CsPlayerMode.FULLSCREEN) 4_000L else 5_000L)
            controlsVisible = false
        }
    }
    // Mode switches always reveal the controls (fresh reveal in fullscreen;
    // the minimized page re-shows its player controls).
    LaunchedEffect(playerMode) {
        controlsVisible = true
        controlsLocked = false
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    var showLinksSheet by remember { mutableStateOf(false) }
    var showSubsSheet by remember { mutableStateOf(false) }
    var showSubsSettingsSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    // Track snapshots for the sheets (refreshed when opened).
    var videoTracks by remember { mutableStateOf<List<CsVideoTrack>>(emptyList()) }
    var selectedTrackLabel by remember { mutableStateOf<String?>(null) }
    var textTracks by remember { mutableStateOf<List<com.confused.anikuta.core.csplayer.CsTextTrack>>(emptyList()) }
    var selectedSubId by remember { mutableStateOf<String?>(null) }
    // Task 53 / RC-7: embedded audio tracks (DASH multi-audio) for the subs sheet.
    var audioTracks by remember { mutableStateOf<List<com.confused.anikuta.core.csplayer.CsAudioTrackInfo>>(emptyList()) }
    var selectedAudioId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showLinksSheet, uiState.currentLink?.url, engineState.bufferState) {
        if (showLinksSheet) {
            videoTracks = engine.videoTracks()
            val selected = videoTracks.firstOrNull { track ->
                runCatching {
                    engine.player.currentTracks.groups.getOrNull(track.groupIndex)
                        ?.isSelected == true
                }.getOrDefault(false)
            }
            selectedTrackLabel = selected?.label
        }
    }
    LaunchedEffect(showSubsSheet, engineState.bufferState) {
        if (showSubsSheet) {
            textTracks = engine.textTracks()
            selectedSubId = engine.selectedTextTrackId()
            audioTracks = engine.audioTracks()
            selectedAudioId = audioTracks.firstOrNull {
                runCatching {
                    engine.player.currentTracks.groups.getOrNull(it.groupIndex)?.isSelected == true
                }.getOrDefault(false)
            }?.id
        }
    }

    fun seekRelative(seconds: Int) {
        engine.seekTo((engineState.positionMs + seconds * 1000L).coerceIn(0L, engineState.durationMs))
    }

    // The shared player surface (re-parents between modes). Task 55: the
    // subtitle STYLE (the shared PlayerPreferences values) applies at creation
    // so styled subs show from the first frame.
    val playerSurface: @Composable (Modifier) -> Unit = { modifier ->
        CsPlayerSurface(
            playerView = playerView,
            engine = engine,
            onCreate = { view ->
                playerView = view
                engine.applySubtitleStyle(view, currentSubtitleStyle(playerPreferences))
            },
            modifier = modifier,
        )
    }

    // The phase overlays + controls — one content slot for whichever box the
    // player currently lives in (the 16:9 page box or the fullscreen surface).
    val playerOverlay: @Composable () -> Unit = {
        when (uiState.phase) {
            CsWatchViewModel.Phase.RESOLVING -> CsResolvingOverlay(
                animeTitle = uiState.animeTitle,
                episodeNumber = uiState.episodeNumber,
                providerName = uiState.providerName,
            )

            CsWatchViewModel.Phase.FAILED -> CsErrorOverlay(
                title = "Couldn't play this episode",
                message = uiState.resolveError ?: "Unknown error",
                onRetry = viewModel::retryResolution,
                onBack = onBack,
            )

            CsWatchViewModel.Phase.NO_LINKS -> CsErrorOverlay(
                title = "No playable streams",
                message = uiState.resolveError ?: "The provider returned no playable links",
                onRetry = viewModel::retryResolution,
                onBack = onBack,
            )

            CsWatchViewModel.Phase.PLAYING -> if (playerMode == CsPlayerMode.MINIMIZED) {
                CsMinimizedControls(
                    state = engineState,
                    visible = controlsVisible,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onTogglePlay = {
                        engine.playPause()
                        controlsVisible = true
                    },
                    onSeekRelative = ::seekRelative,
                    onSeekTo = { engine.seekTo(it) },
                    onMaximize = { playerMode = CsPlayerMode.FULLSCREEN },
                    onQualityClick = { showLinksSheet = true },
                    onSubtitleClick = { showSubsSheet = true },
                )
            } else {
                CsFullscreenControls(
                    state = engineState,
                    visible = controlsVisible,
                    locked = controlsLocked,
                    animeTitle = uiState.animeTitle,
                    episodeInfo = "EP " + com.confused.anikuta.core.common.EpisodeTitleParser
                        .formatEpisodeNumber(uiState.episodeNumber),
                    qualityInfo = uiState.currentLink?.qualityLabel ?: "",
                    currentSpeed = engineState.playbackSpeed,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onLockToggle = { controlsLocked = !controlsLocked },
                    onMinimize = { playerMode = CsPlayerMode.MINIMIZED },
                    onTogglePlay = {
                        engine.playPause()
                        controlsVisible = true
                    },
                    onSeekRelative = ::seekRelative,
                    onSeekTo = { engine.seekTo(it) },
                    onQualityClick = { showLinksSheet = true },
                    onSubtitleClick = { showSubsSheet = true },
                    onAudioClick = { showSubsSheet = true },
                    onMoreClick = { showEpisodesSheet = true },
                    onSpeedClick = { showSpeedSheet = true },
                    onSkipForward = { viewModel.nextEpisode()?.let(viewModel::selectEpisode) },
                )
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    when (playerMode) {
        CsPlayerMode.MINIMIZED -> CsWatchPage(
            uiState = uiState,
            playerContent = {
                playerSurface(Modifier.fillMaxSize())
                playerOverlay()
            },
            onBack = onBack,
            onEpisodeSwitch = { viewModel.selectEpisode(it) },
            currentEpisodeData = viewModel.currentEpisodeData(),
            mainId = key.mainId,
        )

        CsPlayerMode.FULLSCREEN -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            playerSurface(Modifier.fillMaxSize())
            playerOverlay()
        }
    }

    if (showLinksSheet) {
        CsLinksSheet(
            links = uiState.links,
            currentLinkUrl = uiState.currentLink?.url,
            failedLinkUrls = uiState.failedLinkUrls,
            videoTracks = videoTracks,
            selectedTrackLabel = selectedTrackLabel,
            onLinkSelect = { link ->
                viewModel.selectLink(link)
                showLinksSheet = false
            },
            onTrackSelect = { track ->
                engine.selectVideoTrack(track)
                selectedTrackLabel = track?.label
            },
            onCopyUrl = { url ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("stream url", url))
            },
            onDismiss = { showLinksSheet = false },
            playerPreferences = playerPreferences,
        )
    }

    if (showSubsSheet) {
        val engineTrackIds = textTracks.mapNotNull { it.id }.toSet()
        val pendingSubs = uiState.subtitles.filter { it.id !in engineTrackIds }
        CsSubtitlesSheet(
            tracks = textTracks,
            selectedTrackId = selectedSubId,
            onSelect = { track ->
                engine.selectTextTrack(track)
                selectedSubId = track?.id
                showSubsSheet = false
            },
            pendingSubs = pendingSubs,
            onPendingSubSelect = { sub ->
                viewModel.reattachSubtitles(sub)
                showSubsSheet = false
            },
            audioTracks = audioTracks,
            selectedAudioId = selectedAudioId,
            onAudioSelect = { audio ->
                engine.selectAudioTrack(audio)
                selectedAudioId = audio?.id
            },
            onOpenSettings = {
                showSubsSheet = false
                showSubsSettingsSheet = true
            },
            onDismiss = { showSubsSheet = false },
        )
    }

    // Task 55: the CS subtitle STYLE settings sheet — writes the SAME
    // PlayerPreferences values the aniyomi stack uses and re-applies them to
    // the Media3 view live (no reload needed).
    if (showSubsSettingsSheet) {
        CsSubtitleSettingsSheet(
            onApplySettings = {
                playerView?.let { view ->
                    engine.applySubtitleStyle(view, currentSubtitleStyle(playerPreferences))
                }
            },
            onDismiss = { showSubsSettingsSheet = false },
            playerPreferences = playerPreferences,
        )
    }

    if (showEpisodesSheet) {
        CsEpisodesSheet(
            episodes = uiState.episodes,
            currentData = viewModel.currentEpisodeData(),
            onSelect = { episode ->
                viewModel.selectEpisode(episode)
                showEpisodesSheet = false
            },
            onDismiss = { showEpisodesSheet = false },
        )
    }

    if (showSpeedSheet) {
        CsSpeedSheet(
            currentSpeed = engineState.playbackSpeed,
            onSpeedSelected = { engine.setPlaybackSpeed(it) },
            onDismiss = { showSpeedSheet = false },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Player surface (shared AndroidView — re-parented, never recreated)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The single [PlayerView], moved between the minimized player box and the
 * fullscreen surface. The factory re-uses the remembered instance (detaching
 * it from any previous parent first — the aniyomi PlayerSurface pattern) so
 * the video surface is never torn down on mode switches.
 */
@Composable
private fun CsPlayerSurface(
    playerView: PlayerView?,
    engine: CsPlayerEngine,
    onCreate: (PlayerView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val view = (playerView ?: PlayerView(ctx).apply {
                useController = false
                // The Compose layer draws the chrome; PlayerView only
                // renders video + subtitle cues.
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }).also { v ->
                if (playerView == null) {
                    v.player = engine.player
                    onCreate(v)
                }
                (v.parent as? ViewGroup)?.removeView(v)
            }
            view
        },
        modifier = modifier,
    )
}

/**
 * Task 55: PlayerPreferences → the Media3 subtitle style snapshot (one mapper,
 * used at surface creation + every settings change).
 */
private fun currentSubtitleStyle(prefs: PlayerPreferences): CsSubtitleStyle = CsSubtitleStyle(
    fontSize = prefs.subtitleFontSize,
    borderSize = prefs.subtitleBorderSize,
    bold = prefs.boldSubtitles,
    italic = prefs.italicSubtitles,
    textColor = prefs.textColorSubtitles,
    borderColor = prefs.borderColorSubtitles,
    backgroundColor = prefs.backgroundColorSubtitles,
    shadowOffset = prefs.subtitleShadowOffset,
    position = prefs.subtitlePosition,
)
