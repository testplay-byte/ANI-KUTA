package com.confused.anikuta.feature.animedetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch  // Phase WP: for swipe animation coroutine
import androidx.compose.foundation.layout.offset  // Phase WP: for swipe translation
import androidx.compose.ui.graphics.graphicsLayer  // Phase WP: for watched alpha
import androidx.compose.runtime.rememberCoroutineScope  // Phase WP: for swipe coroutine
import androidx.compose.ui.input.pointer.pointerInput  // Phase WP: for swipe gesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures  // Phase WP
import androidx.compose.material.icons.filled.CheckCircle  // Phase WP
import androidx.compose.material.icons.filled.VisibilityOff  // Phase WP
import androidx.compose.material.icons.filled.Star  // Phase 4
import androidx.compose.material.icons.filled.StarBorder  // Phase 4
import androidx.compose.ui.graphics.ColorFilter  // Phase WP: grayscale
import androidx.compose.ui.graphics.ColorMatrix  // Phase WP: grayscale

/**
 * Details screen — complete UI overhaul matching the old project's design.
 *
 * Layout (one LazyColumn, per old project's DetailContent):
 * 1. DetailBanner — 360dp blurred cover + gradient + 3 action buttons (back,
 *    bookmark, three-dot menu) + cover thumbnail + title + meta row.
 * 2. GenresRow — horizontal scrollable chips.
 * 3. SynopsisSection — collapsible with "Show more/less".
 * 4. InfoSection — key/value table (format, status, season, episodes, score).
 *
 * The 3 top buttons match the old project exactly:
 * - Back (ArrowBack) — left, 40dp black-40%-alpha circle, 22dp white icon.
 * - Bookmark toggle (Bookmark/BookmarkBorder) — right, same styling.
 * - Three-dot menu (MoreHoriz) — right, same styling, opens dropdown.
 *
 * CORE_RULES §22: smooth animations.
 * CORE_RULES §23: reactive state from ViewModel.
 * DESIGN-LANGUAGE.md §2.1: collapsing header behavior (banner is the header).
 * DESIGN-LANGUAGE.md §2.2: scroll blur overlay at the top edge.
 */
@Composable
fun DetailsScreen(
    detailsKey: AnimeDetailsKey,
    onBack: () -> Unit,
    onNavigateToWatch: (mainId: String, videoUrl: String, animeTitle: String, quality: String, episodeUrl: String, episodeNumber: Float, episodeTitle: String, episodeListSerialized: String, videoHeaders: String, resolvedVideosKey: String, sourceId: Long, subtitleTracksSerialized: String, audioTracksSerialized: String, episodeMetadataSerialized: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onDownloadEpisode: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onDownloadSpecificVideo: (eu.kanade.tachiyomi.animesource.model.SEpisode, com.confused.anikuta.core.videoresolver.ResolvedVideo, String, String) -> Unit = { _, _, _, _ -> },
    viewModel: DetailsViewModel = koinViewModel(),
) {
    BackHandler(enabled = true) { onBack() }

    // D.FIX: Inject DownloadManager for offline playback (checking isEpisodeDownloaded
    // + getting the local content:// URI).
    val downloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()

    // Dispatch to the correct load method based on the key type.
    LaunchedEffect(detailsKey) {
        when (detailsKey) {
            is AnimeDetailsKey.AniList -> viewModel.loadFromAniList(detailsKey.animeId)
            is AnimeDetailsKey.Extension -> viewModel.loadFromExtension(
                sourceId = detailsKey.sourceId,
                animeUrl = detailsKey.animeUrl,
                title = detailsKey.title,
                thumbnailUrl = detailsKey.thumbnailUrl,
            )
        }
    }

    val state by viewModel.state.collectAsState()
    val linkedSource by viewModel.linkedSource.collectAsState()
    val episodeState by viewModel.episodeState.collectAsState()
    val episodeMetadata by viewModel.episodeMetadata.collectAsState()
    val resolverState by viewModel.resolverState.collectAsState()
    val resolvedVideosKey by viewModel.resolvedVideosKey.collectAsState()
    val availableSources by viewModel.availableSources.collectAsState()
    val manualSearchState by viewModel.manualSearchState.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    // Phase WP: watch progress for the episode list (watched state + swipe-to-toggle).
    val watchProgress by viewModel.watchProgress.collectAsState()

    // Phase 4: per-anime user rating (0-100, null = unrated).
    val animeRating by viewModel.animeRating.collectAsState()

    // D.FIX: Compute the effective linked source at the top level — used by both
    // the EpisodesSection (inside Success branch) AND the ResolverSheet (outside).
    // For extension entries, viewModel.linkedSource is null — the source is derived
    // from the anime's sourceId/sourceName.
    val effectiveLinkedSource = linkedSource ?: run {
        val anime = (state as? DetailsState.Success)?.anime
        val sourceId = anime?.sourceId
        val sourceName = anime?.sourceName
        if (sourceId != null && sourceName != null) {
            LinkedSource(sourceId, sourceName, anime.animeUrl ?: "")
        } else null
    }

    // Phase B: auto-link state
    val autoLinkState by viewModel.autoLinkState.collectAsState()
    val anilistSearchState by viewModel.anilistSearchState.collectAsState()
    val showManualLinkSheet by viewModel.showManualLinkSheet.collectAsState()

    // Phase C: library state
    val isInLibrary by viewModel.isInLibrary.collectAsState()

    // D-146: Refresh visual feedback
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Phase C: Category sheet state
    val categories by viewModel.categories.collectAsState()
    val contentCategories by viewModel.contentCategories.collectAsState()
    val showCategorySheet by viewModel.showCategorySheet.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showManualSearch by remember { mutableStateOf(false) }
    var showResolverSheet by remember { mutableStateOf(false) }
    var resolverDownloadMode by remember { mutableStateOf(false) }
    var currentEpisode by remember { mutableStateOf<eu.kanade.tachiyomi.animesource.model.SEpisode?>(null) }

    // Phase 2: Auto-select video — when the user clicks an episode, set pendingAutoPlay
    // instead of showResolverSheet. The LaunchedEffect below observes resolverState +
    // when it becomes Success, tries auto-select. If a video is picked → navigate to
    // watch directly (skip the ResolverSheet). If no match → show the ResolverSheet.
    var pendingAutoPlay by remember { mutableStateOf(false) }

    // Phase 3: Auto-play from Continue Watching — if autoPlayEpisode is set on the key,
    // auto-trigger the episode click when episodes are loaded. Uses the Phase 2
    // auto-resolve flow (pendingAutoPlay → tryAutoSelect → navigate to watch).
    val autoPlayEpisode = when (detailsKey) {
        is AnimeDetailsKey.AniList -> detailsKey.autoPlayEpisode
        is AnimeDetailsKey.Extension -> detailsKey.autoPlayEpisode
    }
    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(resolverState, pendingAutoPlay) {
        if (!pendingAutoPlay) return@LaunchedEffect
        when (resolverState) {
            is com.confused.anikuta.core.videoresolver.ResolverState.Success -> {
                pendingAutoPlay = false
                val autoVideo = viewModel.tryAutoSelect()
                if (autoVideo != null) {
                    // Auto-select succeeded — navigate to watch with the picked video.
                    val anime = (state as? DetailsState.Success)?.anime
                    val linked = effectiveLinkedSource
                    val ep = currentEpisode
                    if (anime != null && linked != null && ep != null) {
                        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
                        val epListStr = (episodeState as? EpisodeState.Loaded)?.episodes?.joinToString("\n") { e ->
                            "${e.url}${delim}${e.episode_number}${delim}${e.name}"
                        } ?: ""
                        val subTracksStr = autoVideo.subtitleTracks.joinToString("\n") { "${it.url}${delim}${it.lang}" }
                        val audioTracksStr = autoVideo.audioTracks.joinToString("\n") { "${it.url}${delim}${it.lang}" }
                        val epMetaStr = episodeMetadata.entries.joinToString("\n") { (epNum, meta) ->
                            val title = meta.title ?: ""
                            val thumb = meta.thumbnailUrl ?: ""
                            val date = meta.airDate?.toString() ?: "0"
                            val desc = meta.description ?: ""
                            val scanlator = ep.scanlator ?: ""
                            "$epNum${delim}$title${delim}$thumb${delim}$date${delim}$desc${delim}$scanlator"
                        }
                        Logger.i("Anikuta:Feature:Details") {
                            "Auto-play: navigating to watch with ${autoVideo.quality} (url=${autoVideo.url.take(60)}...)"
                        }
                        onNavigateToWatch(
                            viewModel.currentMainId ?: "",
                            autoVideo.url,
                            anime.displayName,
                            autoVideo.quality,
                            ep.url,
                            ep.episode_number,
                            ep.name,
                            epListStr,
                            autoVideo.headers,
                            resolvedVideosKey,
                            linked.sourceId,
                            subTracksStr,
                            audioTracksStr,
                            epMetaStr,
                        )
                        viewModel.clearResolver()
                    }
                } else {
                    // Auto-select found no match — show the ResolverSheet as fallback.
                    showResolverSheet = true
                }
            }
            is com.confused.anikuta.core.videoresolver.ResolverState.Error -> {
                pendingAutoPlay = false
                showResolverSheet = true
            }
            else -> {
                // Loading — the LaunchedEffect will re-fire when resolverState changes.
                // The loading dialog is shown below via `pendingAutoPlay && resolverState is Loading`.
            }
        }
    }

    // Phase 1c: Loading indicator for auto-select. Shows a small dialog while resolving.
    val showAutoSelectLoading = pendingAutoPlay &&
        resolverState is com.confused.anikuta.core.videoresolver.ResolverState.Loading
    if (showAutoSelectLoading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingAutoPlay = false
                viewModel.clearResolver()
            },
            confirmButton = {},
            title = null,
            text = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Auto-selecting video...",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
    // Phase 3: Auto-play from Continue Watching — if autoPlayEpisode is set on the key,
    // auto-trigger the episode click when episodes are loaded. Uses the Phase 2
    // auto-resolve flow (pendingAutoPlay → tryAutoSelect → navigate to watch).
    LaunchedEffect(episodeState, autoPlayEpisode, hasAutoPlayed) {
        if (autoPlayEpisode == null || hasAutoPlayed) return@LaunchedEffect
        val episodes = (episodeState as? EpisodeState.Loaded)?.episodes
        if (episodes.isNullOrEmpty()) return@LaunchedEffect
        val targetEp = episodes.find { it.episode_number.toInt() == autoPlayEpisode }
        if (targetEp != null) {
            hasAutoPlayed = true
            Logger.i("Anikuta:Feature:Details") {
                "Auto-play from Continue Watching: triggering episode $autoPlayEpisode"
            }
            currentEpisode = targetEp
            resolverDownloadMode = false
            viewModel.resolveEpisode(targetEp)
            pendingAutoPlay = true
        }
    }

    // DB-7: provide debug context for the Current Screen tab.
    // Shows the anime's mainId, resolver state, + relevant DB rows.
    val updateDebugContext = com.confused.anikuta.core.debugapi.LocalDebugContextUpdater.current
    val mainId = viewModel.currentMainId
    val debugCtx = remember(state, mainId, resolverState) {
        val epCount = when (val es = episodeState) {
            is EpisodeState.Loaded -> es.episodes.size
            else -> 0
        }
        com.confused.anikuta.core.debugapi.DebugContext(
            screenName = "Details",
            screenData = buildMap {
                mainId?.let { put("mainId", it) }
                put("resolverState", resolverState::class.simpleName ?: "Unknown")
                put("episodeCount", epCount.toString())
                linkedSource?.let { put("sourceId", it.sourceId.toString()); put("sourceName", it.sourceName) }
            },
            relevantTables = mainId?.let {
                listOf(
                    com.confused.anikuta.core.debugapi.DbReference("content", "main_id", it, "View content row"),
                    com.confused.anikuta.core.debugapi.DbReference("episode_metadata", "main_id", it, "View episode metadata"),
                    com.confused.anikuta.core.debugapi.DbReference("watch_progress", "main_id", it, "View watch progress"),
                )
            } ?: emptyList(),
        )
    }
    androidx.compose.runtime.LaunchedEffect(debugCtx) { updateDebugContext(debugCtx) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val s = state) {
            is DetailsState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }

            is DetailsState.Error -> ErrorState(s.message)

            is DetailsState.Success -> {
                val anime = s.anime
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                // ── 3-stage pull-to-refresh state ──
                // A custom NestedScrollConnection cooperates with the LazyColumn's
                // own scroll: the pull gesture ONLY activates when the list is at
                // the top AND the user keeps dragging down. No spinner on normal
                // upward scroll, no fling jank (unlike the buggy pointerInput /
                // detectVerticalDragGestures approach that was reverted).
                //
                // ARCHITECTURE (fixed from PTR-5 — eliminates the stale-read race):
                //  - pullPx: a synchronous mutableFloatStateOf — the SOURCE OF TRUTH
                //    during a drag. Written and read synchronously in onPreScroll, so
                //    stage detection + haptics are always computed from the CURRENT
                //    pull distance (no stale Animatable.value reads from a pending
                //    coroutine snapTo).
                //  - snapAnim: an Animatable<Float> used ONLY for the spring snap-back
                //    animation in onPreFling. During the snap-back, it drives pullPx
                //    via a snapTo-per-frame pattern so the indicator visual tracks the
                //    spring. When no animation is running, pullPx is the live value.
                val density = LocalDensity.current
                val context = LocalContext.current
                val thresholdPx1 = with(density) { 120.dp.toPx() } // stage 1: episodes
                val thresholdPx2 = with(density) { 240.dp.toPx() } // stage 2: metadata
                val thresholdPx3 = with(density) { 360.dp.toPx() } // stage 3: all

                var pullPx by remember { mutableFloatStateOf(0f) }
                val currentStage = remember { mutableIntStateOf(0) }
                var isAnimatingSnapBack by remember { mutableStateOf(false) }

                fun stageFor(d: Float): Int = when {
                    d >= thresholdPx3 -> 3
                    d >= thresholdPx2 -> 2
                    d >= thresholdPx1 -> 1
                    else -> 0
                }

                val nestedScrollConnection = remember(
                    context, thresholdPx1, thresholdPx2, thresholdPx3, isRefreshing,
                ) {
                    // prevStage is captured by reference — persists for the lifetime
                    // of this connection instance. Used to fire the haptic exactly
                    // once per stage-UP crossing.
                    var prevStage = 0
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            // (1) Disable pull while a refresh is in flight or while
                            //     the snap-back spring is animating.
                            if (isRefreshing || isAnimatingSnapBack) return Offset.Zero
                            val delta = available.y
                            if (delta == 0f) return Offset.Zero
                            // (2) Only consume when the LazyColumn is at the very top.
                            val atTop = lazyListState.firstVisibleItemIndex == 0 &&
                                lazyListState.firstVisibleItemScrollOffset == 0
                            if (!atTop) return Offset.Zero
                            // (3) If dragging up with no pull distance, let the
                            //     LazyColumn handle it (normal scroll).
                            if (delta < 0f && pullPx <= 0f) return Offset.Zero

                            // (4) Apply damping past stage 1 for the iOS/M3
                            //     "resistance" feel.
                            val current = pullPx
                            val damping = if (current > thresholdPx1 && delta > 0f) 0.5f else 1.0f
                            val newPx = (current + delta * damping).coerceAtLeast(0f)

                            // (5) Stage detection + haptic — SYNCHRONOUS, using the
                            //     live pullPx (not a stale Animatable.value). The
                            //     haptic fires exactly once per stage-UP crossing.
                            val newStage = stageFor(newPx)
                            if (newStage > prevStage) {
                                HapticHelper.stageCross(context)
                            }
                            if (newStage != prevStage) {
                                prevStage = newStage
                                currentStage.intValue = newStage
                            }

                            // (6) Write the new pull distance synchronously — the
                            //     indicator (which reads pullPx via the composable)
                            //     will recompose immediately.
                            pullPx = newPx
                            // (7) Consume the entire delta so the LazyColumn
                            //     doesn't try to scroll past the top.
                            return Offset(0f, available.y)
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset = Offset.Zero

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            val stage = stageFor(pullPx)
                            // (8) Dispatch the action for the CURRENT stage at release.
                            when (stage) {
                                1 -> {
                                    viewModel.refreshEpisodesList()
                                    HapticHelper.releaseConfirm(context)
                                }
                                2 -> {
                                    viewModel.refreshMetadata()
                                    HapticHelper.releaseConfirm(context)
                                }
                                3 -> {
                                    viewModel.refreshAll()
                                    HapticHelper.releaseConfirm(context)
                                }
                                // 0 → no action, no haptic
                            }
                            // (9) Spring snap-back to 0. We animate pullPx directly
                            //     frame-by-frame so the indicator visual tracks the
                            //     spring smoothly. isAnimatingSnapBack prevents
                            //     onPreScroll from interfering during the spring.
                            isAnimatingSnapBack = true
                            val anim = Animatable(pullPx)
                            anim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) {
                                pullPx = value
                            }
                            pullPx = 0f
                            isAnimatingSnapBack = false
                            currentStage.intValue = 0
                            prevStage = 0
                            return Velocity.Zero
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                            Velocity.Zero
                    }
                }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                    ) {
                        // ── Banner ──
                        item {
                            DetailBanner(
                                anime = anime,
                                onBack = onBack,
                                saved = isInLibrary,
                                onToggleSave = { viewModel.toggleLibrary() },
                                onLongPressSave = { viewModel.openCategorySheet() },
                                onMore = { showMenu = true },
                                showMenu = showMenu,
                                onDismissMenu = { showMenu = false },
                                onRefresh = {
                                    showMenu = false
                                    viewModel.refreshAll()
                                },
                                // Phase B: AniList link state + callbacks
                                isExtensionEntry = anime.isFromExtension,
                                isAniListLinked = anime.anilistId != null,
                                isAutoLinkSearching = autoLinkState is AutoLinkState.Searching,
                                onLinkAniList = {
                                    showMenu = false
                                    viewModel.openManualLinkSheet()
                                },
                                onUnlinkAniList = {
                                    showMenu = false
                                    viewModel.unlinkAniList()
                                },
                                // D-134: Data source selector — shows when both
                                // anilistId + sourceId are present (both data sources).
                                hasBothDataSources = anime.anilistId != null && anime.sourceId != null,
                                currentDataSourcePriority = anime.dataSourcePriority,
                                onSwitchDataSource = { priority ->
                                    viewModel.switchDataSource(priority)
                                },
                            )
                        }

                        // ── Genres ──
                        anime.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                            item { GenresRow(genres) }
                        }

                        // ── Synopsis ──
                        anime.description?.let { desc ->
                            item { SynopsisSection(desc, animeRating, viewModel::setAnimeRating) }
                        }

                        // ── Episodes section ──
                        item {
                            // D.FIX: effectiveLinkedSource is now computed at the top
                            // of the Success branch — no duplicate here.
                            EpisodesSection(
                                linkedSource = effectiveLinkedSource,
                                episodeState = episodeState,
                                episodeMetadata = episodeMetadata,
                                hasAnilistId = anime.anilistId != null,
                                onOpenSourcePicker = { showManualSearch = true },
                                onUnlinkSource = { viewModel.unlinkSource() },
                                onEpisodeClick = { episode ->
                                    currentEpisode = episode
                                    resolverDownloadMode = false
                                    // D.FIX: Check if this episode is already downloaded.
                                    // If so, play it offline (skip the resolver).
                                    val stateKey = viewModel.episodeDownloadStateKey(episode)
                                    val downloadState = stateKey?.let { downloadStates[it] }
                                    if (downloadState is EpisodeDownloadState.Downloaded) {
                                        // Play offline — use the downloaded video URI.
                                        val mainId = viewModel.currentMainId
                                        if (mainId != null) {
                                            val localUri = downloadManager.getDownloadedEpisodeUri(mainId, episode.url)
                                            if (localUri != null) {
                                                Logger.i("Anikuta:Feature:Details") {
                                                    "onEpisodeClick — episode is downloaded, playing offline: $localUri"
                                                }
                                                val anime = (state as? DetailsState.Success)?.anime
                                                val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
                                                val epListStr = (episodeState as? EpisodeState.Loaded)?.episodes?.joinToString("\n") { e ->
                                                    "${e.url}${delim}${e.episode_number}${delim}${e.name}"
                                                } ?: ""
                                                val epMetaStr = episodeMetadata.entries.joinToString("\n") { (epNum, meta) ->
                                                    val title = meta.title ?: ""
                                                    val thumb = meta.thumbnailUrl ?: ""
                                                    val date = meta.airDate?.toString() ?: "0"
                                                    val desc = meta.description ?: ""
                                                    val scanlator = episode.scanlator ?: ""
                                                    "$epNum${delim}$title${delim}$thumb${delim}$date${delim}$desc${delim}$scanlator"
                                                }
                                                onNavigateToWatch(
                                                    mainId ?: "",
                                                    localUri,
                                                    anime?.displayName ?: "Downloaded",
                                                    "Downloaded",
                                                    episode.url,
                                                    episode.episode_number,
                                                    episode.name,
                                                    epListStr,
                                                    "", // no headers for local file
                                                    "", // no resolvedVideosKey
                                                    effectiveLinkedSource?.sourceId ?: 0L,
                                                    "", // no subtitle tracks (they're on disk)
                                                    "", // no audio tracks
                                                    epMetaStr,
                                                )
                                                return@EpisodesSection
                                            }
                                        }
                                    }
                                    // Not downloaded — resolve + try auto-play (Phase 2).
                                    // If autoSelectVideo is ON: clear resolver (avoid stale state),
                                    // set pendingAutoPlay → LaunchedEffect handles auto-select.
                                    // If OFF: just show the ResolverSheet directly (original behavior).
                                    viewModel.clearResolver()
                                    viewModel.resolveEpisode(episode)
                                    if (viewModel.isAutoSelectEnabled()) {
                                        pendingAutoPlay = true
                                    } else {
                                        showResolverSheet = true
                                    }
                                },
                                downloadStates = downloadStates,
                                onDownloadEpisode = { episode ->
                                    // D.FIX: Show the resolver sheet in download mode —
                                    // the user picks which video to download (same UI as
                                    // play, but the heading says "Download EP" and picking
                                    // a video enqueues a download instead of navigating to watch).
                                    currentEpisode = episode
                                    resolverDownloadMode = true
                                    viewModel.resolveEpisode(episode)
                                    showResolverSheet = true
                                },
                                onPauseEpisodeDownload = { episode ->
                                    viewModel.pauseEpisodeDownload(episode)
                                },
                                onResumeEpisodeDownload = { episode ->
                                    viewModel.resumeEpisodeDownload(episode)
                                },
                                onCancelEpisodeDownload = { episode ->
                                    viewModel.cancelEpisodeDownload(episode)
                                },
                                onRetryEpisodeDownload = { episode ->
                                    viewModel.retryEpisodeDownload(episode)
                                },
                                onDeleteDownloadedEpisode = { episode ->
                                    viewModel.deleteDownloadedEpisode(episode)
                                },
                                episodeDownloadStateKey = { episode ->
                                    viewModel.episodeDownloadStateKey(episode)
                                },
                                // Phase WP: watched state.
                                mainId = viewModel.currentMainId,
                                watchProgress = watchProgress,
                                onToggleWatched = { epKey -> viewModel.toggleWatched(epKey) },
                            )
                        }

                        // ── Info ──
                        item {
                            Spacer(Modifier.height(16.dp))
                            InfoSection(anime)
                        }
                    }

                    ScrollBlurOverlay(
                        scrollOffset = {
                            if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                            else lazyListState.firstVisibleItemScrollOffset.toFloat()
                        },
                        backgroundColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    // D-146: Refresh overlay — shows a spinner when refreshing.
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Refreshing...",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // ── 3-stage pull-to-refresh indicator ──
                    // Visible only while actively pulling (pullPx > 0) AND
                    // not currently refreshing (avoids overlap with the D-146 pill
                    // above, which takes over after a stage-3 release).
                    if (pullPx > 0f && !isRefreshing) {
                        ThreeStagePullIndicator(
                            pullDistancePx = pullPx,
                            stage = currentStage.intValue,
                            thresholdPx1 = thresholdPx1,
                            thresholdPx3 = thresholdPx3,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp),
                        )
                    }
                }
            }
        }
    }

    // ── Manual search sheet (source selection) ──
    if (showManualSearch) {
        ManualSearchSheet(
            availableSources = availableSources,
            manualSearchState = manualSearchState,
            initialQuery = (state as? DetailsState.Success)?.anime?.displayName ?: "",
            onSearch = { source, query -> viewModel.searchSource(source, query) },
            onLink = { source, sAnime ->
                viewModel.linkSource(source, sAnime)
                showManualSearch = false
                viewModel.clearManualSearch()
            },
            onDismiss = {
                showManualSearch = false
                viewModel.clearManualSearch()
            },
        )
    }

    // ── Resolver sheet (video list) ──
    if (showResolverSheet) {
        ResolverSheet(
            resolverState = resolverState,
            episodeNumber = currentEpisode?.episode_number ?: 0f,
            downloadMode = resolverDownloadMode,
            onPickVideo = { video ->
                val anime = (state as? DetailsState.Success)?.anime
                val linked = effectiveLinkedSource
                val ep = currentEpisode
                // D.FIX: Log the guard condition so we can trace failures.
                Logger.i("Anikuta:Feature:Details") {
                    "onPickVideo — downloadMode=$resolverDownloadMode, anime=${anime != null}, linked=${linked != null}, ep=${ep != null}"
                }
                if (anime != null && linked != null && ep != null) {
                    if (resolverDownloadMode) {
                        // D.FIX: Download mode — enqueue the selected video for download.
                        Logger.i("Anikuta:Feature:Details") {
                            "onPickVideo — download mode: calling onDownloadSpecificVideo"
                        }
                        onDownloadSpecificVideo(
                            ep,
                            video,
                            linked.sourceName,
                            linked.sourceId.toString(),
                        )
                        showResolverSheet = false
                        viewModel.clearResolver()
                        return@ResolverSheet
                    }
                    // CRITICAL: Log the URL at pick time so we can trace where it
                    // might become empty between here and the WatchScreen.
                    Logger.i("Anikuta:Feature:Details") {
                        "=== VIDEO PICKED === quality='${video.quality}', url='${video.url}', headers='${video.headers.take(80)}', resolvedVideosKey='$resolvedVideosKey'"
                    }
                    // Serialize the episode list for the watch screen.
                    // CRITICAL: Uses \u001F (Unit Separator) as the delimiter
                    // instead of '|' because episode URLs can contain '|'.
                    val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
                    val epListStr = (episodeState as? EpisodeState.Loaded)?.episodes?.joinToString("\n") { e ->
                        "${e.url}${delim}${e.episode_number}${delim}${e.name}"
                    } ?: ""
                    // Serialize subtitle + audio tracks from the picked video.
                    // CRITICAL: Carrying these directly ensures subtitles are always
                    // available in WatchScreen (no ResolvedVideosRegistry lookup).
                    val subTracksStr = video.subtitleTracks.joinToString("\n") { "${it.url}${delim}${it.lang}" }
                    val audioTracksStr = video.audioTracks.joinToString("\n") { "${it.url}${delim}${it.lang}" }
                    Logger.i("Anikuta:Feature:Details") {
                        "Subtitle tracks: ${video.subtitleTracks.size}, Audio tracks: ${video.audioTracks.size}"
                    }
                    // Serialize episode metadata for the watch page.
                    // Format: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator" per line.
                    val epMetaStr = episodeMetadata.entries.joinToString("\n") { (epNum, meta) ->
                        val title = meta.title ?: ""
                        val thumb = meta.thumbnailUrl ?: ""
                        val date = meta.airDate?.toString() ?: "0"
                        val desc = meta.description ?: ""
                        val scanlator = ep.scanlator ?: ""
                        "$epNum${delim}$title${delim}$thumb${delim}$date${delim}$desc${delim}$scanlator"
                    }
                    onNavigateToWatch(
                        viewModel.currentMainId ?: "",
                        video.url,
                        anime.displayName,
                        video.quality,
                        ep.url,
                        ep.episode_number,
                        ep.name,
                        epListStr,
                        video.headers,
                        resolvedVideosKey,
                        linked.sourceId,
                        subTracksStr,
                        audioTracksStr,
                        epMetaStr,
                    )
                }
                showResolverSheet = false
                viewModel.clearResolver()
            },
            onDismiss = {
                showResolverSheet = false
                viewModel.clearResolver()
            },
        )
    }

    // ── Phase B: Manual link sheet (AniList linking for extension entries) ──
    if (showManualLinkSheet) {
        ManualLinkSheet(
            anilistSearchState = anilistSearchState,
            initialQuery = (state as? DetailsState.Success)?.anime?.displayName ?: "",
            onSearch = { query -> viewModel.searchAniListForLink(query) },
            onLink = { anilistId -> viewModel.linkAniListEntry(anilistId) },
            onSkip = { viewModel.skipAniListLink() },
            onDismiss = { viewModel.skipAniListLink() },
        )
    }

    // ── Phase C: Category picker sheet (long-press bookmark) ──
    if (showCategorySheet) {
        CategoryPickerSheet(
            categories = categories,
            selectedCategoryIds = contentCategories,
            onToggleCategory = { categoryId -> viewModel.toggleCategory(categoryId) },
            onCreateCategory = { name -> viewModel.createCategoryAndAdd(name) },
            onDismiss = { viewModel.dismissCategorySheet() },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Data source selector — for the three-dot dropdown menu (D-130, D-134)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A data-source selector rendered inside the three-dot DropdownMenu.
 *
 * Shows a "Data source" label + a segmented toggle (AniList / Extension).
 * Tapping a segment calls [onSelect] — the caller closes the menu.
 *
 * D-134: The selector only appears when both AniList + extension data are
 * available (the entry is linked). For AniList-only or extension-only entries,
 * the selector is hidden (there's nothing to switch).
 *
 * Future: This will support more sources (TMDB, Kitsu) — the toggle will
 * become a multi-way selector.
 */
@Composable
private fun DataSourceSelectorMenu(
    currentPriority: com.confused.anikuta.core.common.model.DataSourcePriority,
    onSelect: (com.confused.anikuta.core.common.model.DataSourcePriority) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Data source",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                com.confused.anikuta.core.common.model.DataSourcePriority.ANILIST to "AniList",
                com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION to "Extension",
            ).forEach { (priority, label) ->
                val isSelected = currentPriority == priority
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(priority) },
                ) {
                    Text(
                        text = label,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Banner — 360dp blurred cover + gradient + 3 action buttons + cover/title
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailBanner(
    anime: com.confused.anikuta.core.common.model.UnifiedAnime,
    onBack: () -> Unit,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onMore: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onRefresh: () -> Unit = {},
    onLongPressSave: () -> Unit = {},
    // Phase B: AniList link state + callbacks
    isExtensionEntry: Boolean = false,
    isAniListLinked: Boolean = false,
    isAutoLinkSearching: Boolean = false,
    // D-134: Data source selector params.
    // Shows when BOTH anilistId + sourceId are present (both data sources available).
    hasBothDataSources: Boolean = false,
    currentDataSourcePriority: com.confused.anikuta.core.common.model.DataSourcePriority =
        com.confused.anikuta.core.common.model.DataSourcePriority.EXTENSION,
    onSwitchDataSource: (com.confused.anikuta.core.common.model.DataSourcePriority) -> Unit = {},
    onLinkAniList: () -> Unit = {},
    onUnlinkAniList: () -> Unit = {},
) {
    val coverUrl = anime.coverUrl
    // Per user: use the cover image as the background (like old project).
    // The old project uses anime.coverUrl for the background — not bannerImage.
    // A future tint-color system will extract the dominant color from the cover.
    val bannerUrl = coverUrl

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── Background: blurred banner image + gradient ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(8.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            // Gradient overlay: black 20% → transparent → background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // ── Top action row: back (left) + bookmark + more (right) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Row {
                // D-138: Bookmark button with long-press for category picker.
                // Long-press opens the CategoryPickerSheet.
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(40.dp)
                        .combinedClickable(
                            onClick = onToggleSave,
                            onLongClick = onLongPressSave,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (saved) "Remove from library" else "Add to library",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                // Three-dot menu — DropdownMenu is anchored here (next to the button).
                Box {
                    ActionButton(
                        icon = Icons.Filled.MoreHoriz,
                        contentDescription = "More",
                        onClick = onMore,
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = onDismissMenu,
                    ) {
                        // ── D-134: Data source selector (at the top of the menu) ──
                        // Shows when both AniList + extension data are available.
                        if (hasBothDataSources) {
                            DataSourceSelectorMenu(
                                currentPriority = currentDataSourcePriority,
                                onSelect = { priority ->
                                    onSwitchDataSource(priority)
                                    onDismissMenu()
                                },
                            )
                            androidx.compose.material3.HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Refresh", fontFamily = RobotoFamily) },
                            onClick = onRefresh,
                        )
                        DropdownMenuItem(
                            text = { Text("Share", fontFamily = RobotoFamily) },
                            onClick = onDismissMenu,
                        )
                        // ── Phase B: AniList link/unlink (extension entries only) ──
                        if (isExtensionEntry) {
                            androidx.compose.material3.HorizontalDivider()
                            if (isAniListLinked) {
                                DropdownMenuItem(
                                    text = { Text("Unlink AniList", fontFamily = RobotoFamily) },
                                    onClick = onUnlinkAniList,
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Link to AniList", fontFamily = RobotoFamily) },
                                    onClick = onLinkAniList,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom row: cover thumbnail + title + meta ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = anime.displayName,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Phase B: auto-link badge / searching indicator.
                // Shows "Linked to AniList" (green check) when extension entry has anilistId,
                // or a small spinner + "Auto-linking..." while searching.
                if (isExtensionEntry && (isAniListLinked || isAutoLinkSearching)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        if (isAutoLinkSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Auto-linking...",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (isAniListLinked) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Linked to AniList",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Linked to AniList",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Meta row: score · status · episode count
                val metaParts = buildList {
                    anime.averageScore?.let { add("\u2605 $it%") }
                    anime.status?.let { add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    anime.episodes?.let { add("$it eps") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" \u00b7 "),
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Action button — 40dp black-40%-alpha circle, 22dp white icon
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Genres row — horizontal scrollable chips
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun GenresRow(genres: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        genres.forEach { genre ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = genre,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Synopsis — collapsible with "Show more/less"
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SynopsisSection(
    description: String,
    rating: Int? = null,
    onRate: (Int) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val cleanDesc = description.replace(Regex("<[^>]*>"), "")
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Synopsis",
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            StarRatingBar(rating = rating, onRate = onRate)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanDesc,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (cleanDesc.length > 100) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episodes section — heading + source selector + placeholder
// ════════════════════════════════════════════════════════════════════════════

/**
 * Episodes section — shows the "Episodes" heading with a source selector on the
 * right, and a "not implemented" placeholder below.
 *
 * Per user spec: "at least what you can do for the current time being is that
 * you could show the episodes heading at the top. On the right side of that
 * heading you could show the extension selection option or the source selection
 * option. Below, even inside the episodes list, you could say that the episode
 * list is not implemented yet."
 *
 * The source selector is a placeholder for now — tapping it shows a toast-like
 * message. The actual source linking + episode fetching comes in a later step
 * (needs UnifiedAnime + provider infrastructure).
 */
@Composable
private fun EpisodesSection(
    linkedSource: LinkedSource?,
    episodeState: EpisodeState,
    episodeMetadata: Map<Int, com.confused.anikuta.core.metadata.EpisodeMetadata>,
    hasAnilistId: Boolean,
    onOpenSourcePicker: () -> Unit,
    onUnlinkSource: () -> Unit,
    onEpisodeClick: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit,
    downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),
    onDownloadEpisode: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onPauseEpisodeDownload: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onResumeEpisodeDownload: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onCancelEpisodeDownload: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onRetryEpisodeDownload: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onDeleteDownloadedEpisode: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    episodeDownloadStateKey: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> String? = { null },
    // Phase WP: watched state per episode.
    mainId: String? = null,
    watchProgress: Map<String, com.confused.anikuta.core.watchprogress.WatchProgress> = emptyMap(),
    onToggleWatched: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header: "Episodes" + metadata spinner + source selector ──
        // Metadata spinner only shows if we have an AniList ID (extension-only
        // anime has no metadata source → no spinner).
        var metadataFetchDone by remember { mutableStateOf(false) }
        var showMetadataError by remember { mutableStateOf(false) }
        val showMetadataSpinner = hasAnilistId && episodeState is EpisodeState.Loaded &&
            episodeMetadata.isEmpty() && !metadataFetchDone
        LaunchedEffect(episodeState) {
            // Reset when episodes reload.
            if (episodeState !is EpisodeState.Loaded) {
                metadataFetchDone = false
                showMetadataError = false
            }
        }
        LaunchedEffect(episodeMetadata) {
            // When metadata arrives, mark as done + hide spinner.
            if (episodeMetadata.isNotEmpty()) {
                metadataFetchDone = true
                showMetadataError = false
            }
        }
        // Safety timeout: if metadata is still empty after 15s, show error briefly.
        // Only runs if we have an AniList ID (extension-only: no metadata source).
        LaunchedEffect(episodeState) {
            if (hasAnilistId && episodeState is EpisodeState.Loaded) {
                kotlinx.coroutines.delay(15_000L)
                if (episodeMetadata.isEmpty() && !metadataFetchDone) {
                    metadataFetchDone = true
                    showMetadataError = true
                    kotlinx.coroutines.delay(5_000L)
                    showMetadataError = false
                }
            } else if (!hasAnilistId) {
                // No AniList ID → no metadata fetch → mark as done immediately.
                metadataFetchDone = true
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (showMetadataSpinner && !showMetadataError) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (showMetadataError) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Failed to load metadata",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Source selector pill — shows linked source name or "No source".
            Surface(
                color = if (linkedSource != null)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onOpenSourcePicker() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = linkedSource?.sourceName ?: "No source",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (linkedSource != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select source",
                        tint = if (linkedSource != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // ── Episode list / states ──
        when (episodeState) {
            is EpisodeState.Idle -> {
                // No source linked — show placeholder.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No source linked",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap the source selector above to search\nand link an extension source.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            is EpisodeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            is EpisodeState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes found on this source.",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is EpisodeState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Failed to load episodes",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = episodeState.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSourcePicker) {
                        Text("Try another source", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            is EpisodeState.Loaded -> {
                // Episode list — each episode is a row with metadata.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    episodeState.episodes.forEach { episode ->
                        val epNum = episode.episode_number.toInt()
                        val metadata = episodeMetadata[epNum]
                        val stateKey = episodeDownloadStateKey(episode)
                        val downloadState = stateKey?.let { downloadStates[it] }
                            ?: EpisodeDownloadState.NotDownloaded
                        // Phase WP: build the standardized episode key + look up watched state.
                        val epKey = if (mainId != null) "$mainId|${String.format("%05d", epNum)}" else null
                        val progress = epKey?.let { watchProgress[it] }
                        val isWatched = progress?.isWatched ?: false
                        EpisodeRow(
                            episode = episode,
                            metadata = metadata,
                            onClick = { onEpisodeClick(episode) },
                            downloadState = downloadState,
                            onDownload = { onDownloadEpisode(episode) },
                            onPause = { onPauseEpisodeDownload(episode) },
                            onResume = { onResumeEpisodeDownload(episode) },
                            onCancel = { onCancelEpisodeDownload(episode) },
                            onRetry = { onRetryEpisodeDownload(episode) },
                            onDelete = { onDeleteDownloadedEpisode(episode) },
                            onPlayDownloaded = { onEpisodeClick(episode) },
                            isWatched = isWatched,
                            progressFraction = progress?.progressFraction ?: 0f,
                            onToggleWatched = { epKey?.let { onToggleWatched(it) } },
                        )
                    }
                    // Unlink button at the bottom.
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUnlinkSource) {
                        Text(
                            "Unlink source",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    metadata: com.confused.anikuta.core.metadata.EpisodeMetadata?,
    onClick: () -> Unit,
    downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    onDownload: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPlayDownloaded: () -> Unit = {},
    // Phase WP: watched state + swipe-to-toggle.
    isWatched: Boolean = false,
    progressFraction: Float = 0f,
    onToggleWatched: () -> Unit = {},
) {
    // ── Parse display values ──
    val displayTitle = remember(episode, metadata) {
        metadata?.title
            ?: com.confused.anikuta.core.common.EpisodeTitleParser.parseTitle(
                episode.name, episode.episode_number,
            )
            ?: episode.name.ifBlank { "Episode ${formatEpisodeNumber(episode.episode_number)}" }
    }
    val description = metadata?.description ?: episode.summary
    val thumbnailUrl = metadata?.thumbnailUrl
    val epNumText = formatEpisodeNumber(episode.episode_number)
    val dateText = remember(episode, metadata) {
        val airDate = metadata?.airDate
        when {
            airDate != null && airDate > 0 -> formatDate(airDate)
            episode.date_upload > 0 -> formatDate(episode.date_upload)
            else -> null
        }
    }
    // Audio availability — parsed from scanlator + episode name (like old project).
    val audio = remember(episode) { parseAudioAvailability(episode.scanlator, episode.name) }

    // ── Phase WP: swipe-to-toggle watched state ──
    // Custom pointerInput (not SwipeToDismissBox — that's for dismiss, not toggle).
    // Swipe right past threshold → toggle. Spring back smoothly on release.
    // Bidirectional: swipe right to toggle, swipe left to cancel a rightward swipe.
    val swipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx()
    }
    val swipeThresholdPx = screenWidthPx * 0.35f // 35% of screen width

    // Track whether the threshold was crossed DURING the drag (for haptic feedback).
    var thresholdCrossed by remember { androidx.compose.runtime.mutableStateOf(false) }

    // ── Phase WP: watched styling (IM4: alpha fade + grayscale on the thumbnail) ──
    val targetAlpha = if (isWatched) 0.5f else 1.0f
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetAlpha,
        label = "watched_alpha",
    )
    val colorFilter = remember(isWatched) {
        if (isWatched) {
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        } else null
    }

    // ── Card ── (wrapped in a Box for the swipe gesture + background icon)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    ) {
        // Background icon — fades in linearly as the user swipes, full opacity past
        // the threshold. matchParentSize (BoxScope) sizes the background to the card's
        // footprint: the wrapper Box wraps its content height (no bounded height), so
        // fillMaxSize() resolves to 0 height here — that was the "background gone" bug.
        // matchParentSize measures the card first, then fills the same space behind it.
        val swipeProgress = (kotlin.math.abs(swipeOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)
        val iconAlpha = if (thresholdCrossed) 1f else swipeProgress
        Surface(
            color = if (isWatched) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = iconAlpha },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = if (swipeOffset.value > 0) androidx.compose.ui.Alignment.CenterStart
                else androidx.compose.ui.Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.VisibilityOff
                    else Icons.Filled.CheckCircle,
                    contentDescription = if (isWatched) "Mark as unwatched" else "Mark as watched",
                    tint = if (isWatched) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // The actual card — opaque (NOT transparent), translates with the swipe.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdCrossed = false },
                        onDragEnd = {
                            // If past threshold → toggle + haptic. Else smooth spring back.
                            if (kotlin.math.abs(swipeOffset.value) > swipeThresholdPx) {
                                com.confused.anikuta.core.common.HapticHelper.releaseConfirm(context)
                                onToggleWatched()
                            }
                            coroutineScope.launch {
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 300,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                    ),
                                )
                            }
                            thresholdCrossed = false
                        },
                    ) { _, dragAmount ->
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(
                            minimumValue = -swipeThresholdPx * 1.5f, // allow left cancel
                            maximumValue = swipeThresholdPx * 1.5f,   // allow right toggle
                        )
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        // Haptic feedback when crossing the threshold for the first time.
                        if (!thresholdCrossed && kotlin.math.abs(newValue) > swipeThresholdPx) {
                            thresholdCrossed = true
                            com.confused.anikuta.core.common.HapticHelper.stageCross(context)
                        } else if (thresholdCrossed && kotlin.math.abs(newValue) <= swipeThresholdPx) {
                            thresholdCrossed = false
                        }
                    }
                }
                .clickable(onClick = onClick),
        ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            // ══ TOP SECTION: thumbnail (left) + title/meta (right) + download (far right) ══
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Thumbnail (left) with EP tag overlay (TopStart, themed primary) ──
                if (thumbnailUrl != null) {
                    Box(
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            // Phase WP: grayscale when watched (IM4 — GPU-side, cheap).
                            colorFilter = colorFilter,
                        )
                        // EP tag — themed primary background, 6dp corners, Bold White text.
                        // Shows 'EP N' (not just 'N').
                        // Positioned at TopStart (like old project).
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
                        // Phase 2d: watch progress bar at the bottom of the thumbnail (like YouTube).
                        // Only shows when the episode is partially watched (not when fully watched —
                        // fully watched is indicated by grayscale + alpha fade instead).
                        if (progressFraction > 0f && !isWatched) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
                    // No thumbnail — circle episode number (40dp disc)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = epNumText,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }

                // ── Right column: title (top) + date/audio pills (bottom) ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Title — with subtle background surface
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
                    // Date + Audio pills + (download button if no synopsis)
                    if (dateText != null || audio.hasAny || description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Date pill
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
                            // Audio pills — SUB/DUB/HSUB with dot separators
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
                            // D.6: replaced the placeholder toast button with the state-driven
                            // EpisodeDownloadControl (7 states + AnimatedContent transitions).
                            if (description.isNullOrBlank()) {
                                Spacer(Modifier.weight(1f))
                                EpisodeDownloadControl(
                                    state = downloadState,
                                    onDownload = onDownload,
                                    onPause = onPause,
                                    onResume = onResume,
                                    onCancel = onCancel,
                                    onRetry = onRetry,
                                    onDelete = onDelete,
                                    onPlayDownloaded = onPlayDownloaded,
                                )
                            }
                        }
                    }
                }

                // (Download button moved to the synopsis section below, or to
                //  the date/audio pills row if no synopsis)
            }

            // ══ BOTTOM SECTION: Synopsis (below thumbnail + title row) + download button ══
            // If there IS a synopsis: download button goes at the bottom-right of synopsis.
            // If there is NO synopsis: download button goes at the right of the date/audio pills row.
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
                    Spacer(Modifier.width(8.dp))
                    EpisodeDownloadControl(
                        state = downloadState,
                        onDownload = onDownload,
                        onPause = onPause,
                        onResume = onResume,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        onDelete = onDelete,
                        onPlayDownloaded = onPlayDownloaded,
                    )
                }
            } else {
                // No synopsis — move download button up to the date/audio pills row.
                // Show it at the end of the top section's right column.
                // (Already rendered inline in the date/audio pills Row above if no synopsis.)
            }
        }
    }
    } // close the swipe wrapper Box (Phase WP)
}

// ── Audio availability parsing (ported from old project) ──

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

// ════════════════════════════════════════════════════════════════════════════
//  Download button — consistent size in both synopsis + no-synopsis layouts.
//  Shows a toast on tap (download functionality not yet implemented).
//  CORE_RULES §22: ripple feedback on tap (clickable Box).
//  CORE_RULES §23: live UI feedback (toast) — no silent taps.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DownloadEpisodeButton() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable {
                android.widget.Toast.makeText(
                    context,
                    "Download functionality not yet implemented",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = "Download",
            tint = MaterialTheme.colorScheme.primary,
            // 24dp icon — consistent in both layouts. The 40dp Box gives a
            // proper touch target without shrinking the visible icon.
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}

private fun formatEpisodeNumber(num: Float): String {
    return com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(num)
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

// ════════════════════════════════════════════════════════════════════════════
//  Info section — key/value table
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun InfoSection(anime: com.confused.anikuta.core.common.model.UnifiedAnime) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Information",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Season / Year
        if (anime.season != null && anime.seasonYear != null) {
            InfoRow("Season", "${anime.season!!.lowercase().replaceFirstChar { it.uppercase() }} ${anime.seasonYear}")
        } else if (anime.seasonYear != null) {
            InfoRow("Year", anime.seasonYear.toString())
        }

        // Episodes
        anime.episodes?.let { InfoRow("Episodes", it.toString()) }

        // Score
        anime.averageScore?.let { InfoRow("Score", "$it / 100") }

        // Status
        anime.status?.let {
            InfoRow("Status", it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Error state
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't load anime",
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 3-stage pull-to-refresh indicator overlay for the Details screen (PTR-5).
 *
 * Shows a small progress ring that fills as [pullDistancePx] grows from 0 →
 * [thresholdPx3], plus a stage-dependent label:
 *   - stage 0 (pull < thresholdPx1): "Pull to refresh episodes" + onSurfaceVariant.
 *   - stage 1 (≥ thresholdPx1): "Release to refresh episodes" + primary.
 *   - stage 2 (≥ thresholdPx2): "Release to refresh metadata" + tertiary.
 *   - stage 3 (≥ thresholdPx3): "Release to refresh everything" + error.
 *
 * The per-stage color (primary → tertiary → error) gives a clear visual cue of
 * which refresh action will fire on release. Drawn ABOVE the LazyColumn in a
 * Box overlay aligned TopCenter.
 *
 * Haptic feedback is handled in the NestedScrollConnection (not here) — fires
 * exactly once per stage-UP crossing via HapticHelper.stageCross().
 */
@Composable
private fun ThreeStagePullIndicator(
    pullDistancePx: Float,
    stage: Int,
    thresholdPx1: Float,
    thresholdPx3: Float,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (stage) {
        3 -> "Release to refresh everything" to MaterialTheme.colorScheme.error
        2 -> "Release to refresh metadata" to MaterialTheme.colorScheme.tertiary
        1 -> "Release to refresh episodes" to MaterialTheme.colorScheme.primary
        else -> "Pull to refresh episodes" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Progress fills proportionally up to thresholdPx3 (the stage-3 ceiling).
    val progress = (pullDistancePx / thresholdPx3).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            color = color,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = color,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Phase 4: Star Rating Bar (TEMPORARY — for testing)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A row of 10 clickable stars. Each star = 10 points (0-100 backend scale).
 * Tapping a star sets the rating. Tapping the same star again clears it (toggle).
 *
 * @param rating The current rating (0-100, null = unrated).
 * @param onRate Called with the star count (0-10). 0 = clear rating.
 */
@Composable
private fun StarRatingBar(
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
