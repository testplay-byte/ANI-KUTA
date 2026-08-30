package com.confused.anikuta.feature.cswatch.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsEngineEvent
import com.confused.anikuta.core.csplayer.CsPlayerEngine
import com.confused.anikuta.core.csplayer.CsVideoTrack
import com.confused.anikuta.feature.cswatch.api.CsWatchKey
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.qualifier.named

/**
 * The CloudStream watch screen (task 52 / round 12).
 *
 * Architecture (doc cloudstream-v2/02-PLAYBACK-PLAN.md §1): the screen
 * composable owns the Media3 engine + player view + lifecycle effects (the
 * ADR-025 player carve-out — same exemption the MPV watch screen uses);
 * [CsWatchViewModel] owns resolution state, link selection and watch progress.
 * It shares ZERO code with the aniyomi :feature:watch — visual parity via the
 * same design tokens, behavioral parity via the same WatchProgressStore.
 */
@Composable
fun CsWatchScreen(
    key: CsWatchKey,
    onBack: () -> Unit,
    viewModel: CsWatchViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()

    // The playback OkHttp client = the CS runtime's plugin client (registered
    // by :data:cloudstream's DI — keeps com.lagradost.* out of this module).
    val playbackClient = koinInject<OkHttpClient>(named("cloudstreamPlayback"))

    // ── Engine lifecycle (main thread — ExoPlayer's threading rule) ──────────
    val engine = remember { CsPlayerEngine(context.applicationContext, playbackClient) }
    val engineState by engine.state.collectAsState()

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

    // ── Lifecycle scaffolding (CORE_RULES §5: load-bearing, not boilerplate) ─
    // Keep-screen-on while the screen is alive + immersive sticky mode.
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.show(WindowInsetsCompat.Type.systemBars())
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
            Logger.d("Anikuta:CS:Watch") { "play trigger: no active request (id=${live.playRequestId}) — idle" }
            return@LaunchedEffect
        }
        if (live.playGeneration != live.resolveGeneration) {
            Logger.w("Anikuta:CS:Watch") {
                "play trigger: REJECTED stale request id=${live.playRequestId} " +
                    "gen=${live.playGeneration} != current=${live.resolveGeneration} " +
                    "link=${link.displayLabel.take(40)}"
            }
            return@LaunchedEffect
        }
        Logger.i("Anikuta:CS:Watch") {
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

    // Task 53 / RC-3: a NEW episode's resolution start hard-resets the engine —
    // whatever was loaded stops and clears, so nothing stale can play under
    // the resolving overlay regardless of any race.
    LaunchedEffect(uiState.engineResetTick) {
        if (uiState.engineResetTick > 0) {
            engine.reset()
        }
    }

    // ── Engine events → VM decisions ─────────────────────────────────────────
    LaunchedEffect(engine) {
        engine.events.collect { event ->
            when (event) {
                is CsEngineEvent.PlaybackError -> {
                    // F5: the event carries the URL it belongs to — stale errors
                    // (a link already switched away from) are rejected by the VM.
                    Logger.w("Anikuta:CS:Watch") {
                        "engine error → fallback (url=${event.linkUrl?.take(64)})"
                    }
                    viewModel.onEngineError(event.linkUrl)
                }
                CsEngineEvent.Ended -> {
                    val st = engine.state.value
                    viewModel.saveProgress(st.positionMs, st.durationMs)
                    if (!viewModel.onEpisodeEnded()) {
                        Logger.i("Anikuta:CS:Watch") { "episode ended — no next episode" }
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

    // ── Controls auto-hide ────────────────────────────────────────────────────
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, engineState.isPlaying) {
        if (controlsVisible && engineState.isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    var showLinksSheet by remember { mutableStateOf(false) }
    var showSubsSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
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
                    engine.player.currentTracks.groups.getOrNull(it.groupIndex)?.isSelected
                }.getOrDefault(false)
            }?.id
        }
    }

    BackHandler { onBack() }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = engine.player
                    // The glass Compose layer draws the chrome; PlayerView only
                    // renders video + subtitle cues.
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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

            CsWatchViewModel.Phase.PLAYING -> CsControlsOverlay(
                engineState = engineState,
                visible = controlsVisible,
                animeTitle = uiState.animeTitle,
                episodeTitle = uiState.episodeTitle,
                episodeNumber = uiState.episodeNumber,
                providerName = uiState.providerName,
                hasNext = viewModel.nextEpisode() != null,
                hasPrev = viewModel.prevEpisode() != null,
                playbackSpeed = engineState.playbackSpeed,
                onToggleControls = {
                    controlsVisible = !controlsVisible
                },
                onBack = onBack,
                onPlayPause = {
                    engine.playPause()
                    controlsVisible = true
                },
                onSeekTo = { engine.seekTo(it) },
                onOpenLinks = { showLinksSheet = true },
                onOpenSubtitles = { showSubsSheet = true },
                onOpenEpisodes = { showEpisodesSheet = true },
                onNextEpisode = { viewModel.nextEpisode()?.let(viewModel::selectEpisode) },
                onPrevEpisode = { viewModel.prevEpisode()?.let(viewModel::selectEpisode) },
                onSpeedChange = { engine.setPlaybackSpeed(it) },
            )
        }

        if (showLinksSheet) {
            CsLinksSheet(
                links = uiState.links,
                currentLinkUrl = uiState.currentLink?.url,
                failedLinkUrls = uiState.failedLinkUrls,
                hiddenTorrentCount = uiState.hiddenTorrentCount,
                unsupportedDrmCount = uiState.unsupportedDrmCount,
                resolveCompleted = uiState.resolveCompleted,
                subtitleCount = uiState.subtitles.size,
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
                onDismiss = { showSubsSheet = false },
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
    }
}
