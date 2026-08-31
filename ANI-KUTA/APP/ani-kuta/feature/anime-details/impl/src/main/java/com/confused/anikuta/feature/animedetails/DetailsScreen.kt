package com.confused.anikuta.feature.animedetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle  // D-226: reverse auto-link match
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SearchOff  // D-226: reverse auto-link no-match
import androidx.compose.material.icons.filled.Security  // D-209: Cloudflare error icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api  // D-314: PullToRefreshBox opt-in
import androidx.compose.material3.pulltorefresh.PullToRefreshBox  // D-314: simple pull-to-refresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle  // D-317: S-n/E-m compound tag spans
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.onGloballyPositioned  // D-315: cover bounds
import androidx.compose.ui.layout.boundsInRoot  // D-315: cover bounds
import com.confused.anikuta.core.designsystem.animation.coverSharedElement  // D-320
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
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
@OptIn(ExperimentalMaterial3Api::class)  // D-314: PullToRefreshBox + rememberPullToRefreshState
@Composable
fun DetailsScreen(
    detailsKey: AnimeDetailsKey,
    onBack: () -> Unit,
    onNavigateToWatch: (mainId: String, videoUrl: String, animeTitle: String, quality: String, episodeUrl: String, episodeNumber: Float, episodeTitle: String, episodeListSerialized: String, videoHeaders: String, resolvedVideosKey: String, sourceId: Long, subtitleTracksSerialized: String, audioTracksSerialized: String, episodeMetadataSerialized: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    // Task 52 (round 12 — the playback port): CloudStream episode taps route
    // here INSTEAD of the classic resolver/watch pipeline. Primitives (not
    // CsWatchKey) to keep :feature:anime-details free of feature-to-feature
    // deps — MainActivity builds the key. Args: providerName, animeTitle,
    // episodeData (the CS data handle), episodeNumber, episodeTitle,
    // episodeListSerialized, mainId, sourceId, episodeMetadataSerialized
    // (task 54 / round 14 — the CS watch page's per-episode metadata, same
    // wire format as the aniyomi watch key's field).
    onNavigateToCsWatch: (String, String, String, Float, String, String, String, Long, String) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onDownloadEpisode: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit = {},
    onDownloadSpecificVideo: (eu.kanade.tachiyomi.animesource.model.SEpisode, com.confused.anikuta.core.videoresolver.ResolvedVideo, String, String, String) -> Unit = { _, _, _, _, _ -> },
    // D-209: Cloudflare manual solver — launched from the episode error card.
    onOpenCloudflareWebView: (url: String, sourceName: String) -> Unit = { _, _ -> },
    viewModel: DetailsViewModel = koinViewModel(),
) {
    BackHandler(enabled = true) { onBack() }

    // D.FIX: Inject DownloadManager for offline playback (checking isEpisodeDownloaded
    // + getting the local content:// URI).
    val downloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()
    // D-231: Episode list preferences (filters, sort, grouping).
    val episodeListPrefs = koinInject<com.confused.anikuta.core.preferences.EpisodeListPreferences>()

    // D-227: Reset ALL per-anime state when LEAVING the Details screen.
    // Because the ViewModel is Activity-scoped (same instance reused across
    // navigations), without this reset the old anime's Success state would
    // flash briefly when opening a new anime (the "shadow" issue). This
    // DisposableEffect fires onDispose when the detailsKey changes or the
    // screen is popped — clearing _state to Loading so the next open starts clean.
    DisposableEffect(detailsKey) {
        onDispose {
            viewModel.resetState()
        }
    }

    // Dispatch to the correct load method based on the key type.
    LaunchedEffect(detailsKey) {
        when (detailsKey) {
            is AnimeDetailsKey.AniList -> viewModel.loadFromAniList(detailsKey.animeId)
            is AnimeDetailsKey.Extension -> viewModel.loadFromExtension(
                sourceId = detailsKey.sourceId,
                animeUrl = detailsKey.animeUrl,
                title = detailsKey.title,
                thumbnailUrl = detailsKey.thumbnailUrl,
                year = detailsKey.year,
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

    // D-223: Per-anime accent color (extracted from cover image).
    val coverAccent by viewModel.coverAccent.collectAsState()
    // D-234: Next-episode release info (for the countdown card).
    val nextEpisodeInfo by viewModel.nextEpisodeInfo.collectAsState()

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
    // D-226: Reverse auto-link state — drives the live-preview in the episodes section.
    val reverseAutoLinkState by viewModel.reverseAutoLinkState.collectAsState()
    val anilistSearchState by viewModel.anilistSearchState.collectAsState()
    val showManualLinkSheet by viewModel.showManualLinkSheet.collectAsState()

    // D-228: Match-preview visibility — keeps the preview card visible for a
    // minimum duration (MATCH_PREVIEW_MIN_MS) after a reverse auto-link match,
    // even after episodes finish loading. The user complained the preview
    // disappeared too quickly to verify the link. This state is set true when
    // the match arrives, and auto-dismissed after the delay via LaunchedEffect.
    var matchPreviewVisible by remember { mutableStateOf(false) }
    LaunchedEffect(reverseAutoLinkState) {
        if (reverseAutoLinkState is ReverseAutoLinkState.Matched) {
            matchPreviewVisible = true
            kotlinx.coroutines.delay(MATCH_PREVIEW_MIN_MS)
            matchPreviewVisible = false
        }
    }

    // Phase C: library state
    val isInLibrary by viewModel.isInLibrary.collectAsState()

    // D-231: Collect episode list preferences reactively so the episode list
    // re-filters/re-sorts live when the user changes settings in the bottom sheet.
    val downloadedFilter by episodeListPrefs.downloadedFilter.changes.collectAsState(
        initial = episodeListPrefs.downloadedFilter.get(),
    )
    val watchedFilter by episodeListPrefs.watchedFilter.changes.collectAsState(
        initial = episodeListPrefs.watchedFilter.get(),
    )
    val sortMode by episodeListPrefs.sortMode.changes.collectAsState(
        initial = episodeListPrefs.sortMode.get(),
    )
    val sortDescending by episodeListPrefs.sortDescending.changes.collectAsState(
        initial = episodeListPrefs.sortDescending.get(),
    )
    val audioFilter by episodeListPrefs.audioFilter.changes.collectAsState(
        initial = episodeListPrefs.audioFilter.get(),
    )
    val groupingSize by episodeListPrefs.groupingSize.changes.collectAsState(
        initial = episodeListPrefs.groupingSize.get(),
    )
    // D-234: Show next episode release card.
    val showNextEpisode by episodeListPrefs.showNextEpisode.changes.collectAsState(
        initial = episodeListPrefs.showNextEpisode.get(),
    )

    // D-317: organize mode (three states) + the season-in-tag toggle.
    val organizeMode by episodeListPrefs.organizeMode.changes.collectAsState(
        initial = episodeListPrefs.organizeMode.get(),
    )
    val seasonTagInNumber by episodeListPrefs.seasonTagInNumber.changes.collectAsState(
        initial = episodeListPrefs.seasonTagInNumber.get(),
    )

    // Task 55 (round 15): the sub/dub display mode for series whose episode
    // rows carry "(Sub)"/"(Dub)" tags (bridged CloudStream sources).
    // SEPARATE (default) keeps the rows apart + a Sub | Dub switcher; COMBINED
    // merges sibling rows (the resolve flow then resolves BOTH flavors).
    val subDubMode by episodeListPrefs.subDubMode.changes.collectAsState(
        initial = episodeListPrefs.subDubMode.get(),
    )
    var subDubFlavor by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("SUB")
    }

    // D-307/D-308/D-312/D-317: Season analysis on the RAW episode list — one
    // pass produces the groups (selector + settings sheet), the per-episode
    // assignments (S/E tag), and the per-season display numbers (slice tags).
    // Built from the raw list (not the filtered one) so the season structure
    // stays stable regardless of active filters; keyed on episodeMetadata too
    // so the structure improves when provider hints arrive after the list.
    val rawEpisodesForSeasons = (episodeState as? EpisodeState.Loaded)?.episodes
    val seasonInfo = remember(rawEpisodesForSeasons, episodeMetadata) {
        rawEpisodesForSeasons?.let { eps ->
            val hints = eps.map { ep ->
                val meta = episodeMetadata[ep.episode_number.toInt()]
                val sn = meta?.seasonNumber?.takeIf { it > 0 }
                if (meta != null && sn != null) {
                    com.confused.anikuta.core.seasons.ProviderSeasonHint(
                        seasonNumber = sn,
                        episodeNumberInSeason = meta.episodeNumberInSeason,
                    )
                } else {
                    null
                }
            }
            analyzeEpisodeSeasons(eps, hints)
        }
    }
    // `detectedSeasons` is the STRUCTURAL fact (independent of the user's
    // organizeMode preference) so the settings sheet keeps offering the
    // Seasons/Off/Number-groups choice even right after the user switches
    // away from Seasons (D-307 review fix).
    val detectedSeasons = seasonInfo?.groups
    val seasonGroups = if (organizeMode == "SEASONS") detectedSeasons else null

    // D-146: Refresh visual feedback
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Phase C: Category sheet state
    val categories by viewModel.categories.collectAsState()
    val contentCategories by viewModel.contentCategories.collectAsState()
    val showCategorySheet by viewModel.showCategorySheet.collectAsState()

    // D-242: Tracking state.
    val showTrackSheet by viewModel.showTrackSheet.collectAsState()
    val trackEntry by viewModel.trackEntry.collectAsState()
    val isTrackerLoggedIn by viewModel.isTrackerLoggedIn.collectAsState()
    val showMarkPreviousPrompt by viewModel.showMarkPreviousPrompt.collectAsState()
    val showMarkSeriesPrompt by viewModel.showMarkSeriesPrompt.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showManualSearch by remember { mutableStateOf(false) }
    var showResolverSheet by remember { mutableStateOf(false) }
    var resolverDownloadMode by remember { mutableStateOf(false) }
    var currentEpisode by remember { mutableStateOf<eu.kanade.tachiyomi.animesource.model.SEpisode?>(null) }

    // D-315: full-screen cover viewer (opened by tapping the banner cover).
    var showCoverViewer by remember { mutableStateOf(false) }
    var coverViewerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // ── D-320: shared-element cover transition (experimental) ──
    // The SOURCE screen (Browse/Search/Library card) carries the cover it
    // rendered + the shared-element key through the nav key. Resolving the key
    // here lets BOTH the loading skeleton and the banner cover morph from the
    // tapped card (and back on back-navigation). Null when the feature is
    // disabled or the source had no cover.
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val navKeyCoverUrl = when (val k = detailsKey) {
        is AnimeDetailsKey.AniList -> k.coverUrl
        is AnimeDetailsKey.Extension -> k.thumbnailUrl
    }
    val navKeyTitle = when (val k = detailsKey) {
        is AnimeDetailsKey.AniList -> k.title
        is AnimeDetailsKey.Extension -> k.title
    }
    val sharedCoverKey = if (appPrefs.coverTransitionEnabled) {
        detailsKey.transitionKey
    } else null

    // D-230: Episode list settings sheet + search state.
    var showEpisodeSettingsSheet by remember { mutableStateOf(false) }
    var showEpisodeSearch by remember { mutableStateOf(false) }
    var episodeSearchQuery by remember { mutableStateOf("") }
    // D-231: Current group index (for the episode group switcher).
    var currentGroupIndex by remember { mutableIntStateOf(0) }
    // D-308: Current season chip index for the season selector
    // (0 = "All", 1..n = detected seasons, n+1 = "Other" when present).
    var currentSeasonIndex by remember { mutableIntStateOf(0) }

    // D-231: Hoisted lazyListState so we can auto-scroll to the episodes section
    // when the settings sheet opens (so the user sees live changes).
    val detailsLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    // D-231: Track the scroll position before the sheet opens so we can restore it on dismiss.
    var savedScrollPosition by remember { mutableIntStateOf(0) }
    var savedScrollOffset by remember { mutableIntStateOf(0) }

    // D-231: Auto-scroll to the episodes section when the settings sheet opens,
    // so the user sees live changes. Restore the original position on dismiss.
    // D-232: Fixed — use animateScrollToItem for BOTH open + restore (smooth both
    // ways). Removed the false guard (savedScrollPosition > 0) that prevented
    // restore when the user was at the very top.
    LaunchedEffect(showEpisodeSettingsSheet) {
        if (showEpisodeSettingsSheet) {
            // Save current position.
            savedScrollPosition = detailsLazyListState.firstVisibleItemIndex
            savedScrollOffset = detailsLazyListState.firstVisibleItemScrollOffset
            // Smooth scroll to the episodes section (item index 3).
            detailsLazyListState.animateScrollToItem(3, scrollOffset = 0)
        } else {
            // Smooth restore to the original position.
            detailsLazyListState.animateScrollToItem(
                savedScrollPosition,
                scrollOffset = savedScrollOffset,
            )
        }
    }

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

    LaunchedEffect(pendingAutoPlay) {
        if (!pendingAutoPlay) return@LaunchedEffect
        // Poll resolverState every 100ms until it's Success or Error.
        // (LaunchedEffect captures the Compose state value at composition time,
        // but `resolverState` is collected via collectAsState() which updates
        // the local val on recomposition — the LaunchedEffect coroutine reads
        // the latest value each iteration because Compose state reads in
        // coroutines are deferred to the snapshot.)
        while (pendingAutoPlay) {
            when (val rs = resolverState) {
                is ResolverState.Success -> {
                    // Ensure the loading dialog shows for at least 2 seconds for a good UX.
                    kotlinx.coroutines.delay(2000)
                    pendingAutoPlay = false
                    Logger.i("Anikuta:Feature:Details") { "Auto-play: resolverState is Success — trying auto-select..." }
                    val autoVideo = viewModel.tryAutoSelect(rs)
                    if (autoVideo != null) {
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
                            // D-306: extension-first merge (shared with the episode rows).
                            val epMetaStr = buildEpisodeMetadataSerialized(
                                episodes = (episodeState as? EpisodeState.Loaded)?.episodes ?: emptyList(),
                                metadata = episodeMetadata,
                                currentScanlator = ep.scanlator,
                            )
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
                        Logger.i("Anikuta:Feature:Details") { "Auto-play: tryAutoSelect returned null — showing ResolverSheet" }
                        showResolverSheet = true
                    }
                    return@LaunchedEffect
                }
                is ResolverState.Error -> {
                    pendingAutoPlay = false
                    showResolverSheet = true
                    return@LaunchedEffect
                }
                else -> {
                    // Loading or Idle — wait + re-check.
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    // Phase 2: Loading indicator for auto-select. Shows a beautiful animated dialog while resolving.
    val showAutoSelectLoading = pendingAutoPlay &&
        resolverState is ResolverState.Loading
    if (showAutoSelectLoading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingAutoPlay = false
                viewModel.clearResolver()
            },
            confirmButton = {},
            title = null,
            text = {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    // Animated loading spinner with pulse effect.
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "auto_select")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.15f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                        ),
                        label = "scale",
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Auto-selecting video",
                        fontFamily = RobotoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Finding the best server, audio, and quality...",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
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
                    com.confused.anikuta.core.debugapi.DbReference("main_entry", "main_id", it, "View main_entry row"),
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

    // D-223: When a cover accent is available, compute a derived ColorScheme
    // with the primary family overridden for this anime.
    val adaptiveAccent = coverAccent?.let { androidx.compose.ui.graphics.Color(it) }
    val adaptiveColorScheme = adaptiveAccent?.let { accent ->
        val accentColors = com.confused.anikuta.core.designsystem.theme.AccentColors.from(accent)
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        MaterialTheme.colorScheme.copy(
            primary = if (isDark) accentColors.darkPrimary else accentColors.lightPrimary,
            onPrimary = if (isDark) accentColors.darkOnPrimary else accentColors.lightOnPrimary,
            primaryContainer = if (isDark) accentColors.darkPrimaryContainer else accentColors.lightPrimaryContainer,
            onPrimaryContainer = if (isDark) accentColors.darkOnPrimaryContainer else accentColors.lightOnPrimaryContainer,
        )
    }

    // D-223: Wrap the Box in the adaptive color scheme (or use the default if null).
    val effectiveColorScheme = adaptiveColorScheme ?: MaterialTheme.colorScheme

    // D-230: Hoist onEpisodeClick to screen level so both the Success branch's
    // items() AND the EpisodeSearchSheet (outside MaterialTheme) can use it.
    // R12-REVIEW F4: [fromAutoPlay] preserves the continue-watching autoplay's
    // ORIGINAL semantics when it routes through here — autoplay always
    // auto-navigates (the isAutoSelectEnabled gate is a tap-time preference)
    // and skips the downloaded-file branch (the old flow resolved ONLINE).
    val onEpisodeClick: (eu.kanade.tachiyomi.animesource.model.SEpisode, Boolean) -> Unit = onEpisodeClick@{ episode, fromAutoPlay ->
        currentEpisode = episode
        resolverDownloadMode = false
        val stateKey = viewModel.episodeDownloadStateKey(episode)
        val downloadState = stateKey?.let { downloadStates[it] }
        if (!fromAutoPlay && downloadState is EpisodeDownloadState.Downloaded) {
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
                    // D-306: extension-first merge (shared with the episode rows).
                    val epMetaStr = buildEpisodeMetadataSerialized(
                        episodes = (episodeState as? EpisodeState.Loaded)?.episodes ?: emptyList(),
                        metadata = episodeMetadata,
                        currentScanlator = episode.scanlator,
                    )
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
                    return@onEpisodeClick
                }
            }
        }
        // Not downloaded — resolve + try auto-play (Phase 2).
        // CloudStream V2 (task 52): bridged CS sources route to the DEDICATED
        // CS watch screen — loadLinks resolution + the Media3 player live
        // there. The classic resolver path (below) is aniyomi-only.
        if (viewModel.isLinkedSourceCloudStream()) {
            val anime = (state as? DetailsState.Success)?.anime
            val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
            val epListStr = (episodeState as? EpisodeState.Loaded)?.episodes?.joinToString("\n") { e ->
                "${e.url}${delim}${e.episode_number}${delim}${e.name}"
            } ?: ""
            // Task 54 (round 14): per-episode metadata for the CS watch page
            // (title/thumbnail/air date/description/sub-dub). Same builder as
            // the aniyomi hand-off — one format, both watch stacks consume it.
            // Task 56 (F3a): the TITLE fields are tag-stripped — the CS watch
            // page's pill carries the flavor, the title never repeats it. The
            // scanlator field rides the raw episodes (the flavor signal).
            val csMetaStr = buildEpisodeMetadataSerialized(
                episodes = (episodeState as? EpisodeState.Loaded)?.episodes
                    ?.map { e ->
                        eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
                            copyFrom(e)
                            name = stripSubDubTag(e.name)
                        }
                    } ?: emptyList(),
                metadata = episodeMetadata,
                currentScanlator = episode.scanlator,
            )
            Logger.i("Anikuta:Feature:Details") {
                "onEpisodeClick — CloudStream source: routing to the CS player " +
                    "(EP ${episode.episode_number}, provider=${effectiveLinkedSource?.sourceName})"
            }
            onNavigateToCsWatch(
                effectiveLinkedSource?.sourceName ?: "",
                anime?.displayName ?: "",
                episode.url, // the CS data handle
                episode.episode_number,
                stripSubDubTag(episode.name),
                epListStr,
                viewModel.currentMainId ?: "",
                effectiveLinkedSource?.sourceId ?: 0L,
                csMetaStr,
            )
            return@onEpisodeClick
        }
        viewModel.clearResolver()
        viewModel.resolveEpisode(episode)
        if (fromAutoPlay || viewModel.isAutoSelectEnabled()) {
            pendingAutoPlay = true
        } else {
            showResolverSheet = true
        }
    }

    // Phase 3: Auto-play from Continue Watching — if autoPlayEpisode is set on
    // the key, fire the hoisted click handler once episodes load (declared
    // AFTER onEpisodeClick so the effect can reference it; declaration order in
    // composition is irrelevant to effect timing).
    LaunchedEffect(episodeState, autoPlayEpisode, hasAutoPlayed, onEpisodeClick) {
        if (autoPlayEpisode == null || hasAutoPlayed) return@LaunchedEffect
        val episodes = (episodeState as? EpisodeState.Loaded)?.episodes
        if (episodes.isNullOrEmpty()) return@LaunchedEffect
        val targetEp = episodes.find { it.episode_number.toInt() == autoPlayEpisode }
        if (targetEp != null) {
            hasAutoPlayed = true
            Logger.i("Anikuta:Feature:Details") {
                "Auto-play from Continue Watching: triggering episode $autoPlayEpisode"
            }
            onEpisodeClick(targetEp, true)
        }
    }

    MaterialTheme(colorScheme = effectiveColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (val s = state) {
                is DetailsState.Loading -> {
                    // D-320: when the source screen handed us a cover (shared-
                    // element transition), show a banner SKELETON at the exact
                    // banner geometry instead of a bare centered spinner — the
                    // morphing cover has a landing spot the instant the screen
                    // composes, even before the data loads, and the swap to the
                    // Success banner is seamless (identical layout).
                    if (navKeyCoverUrl != null && sharedCoverKey != null) {
                        DetailsSkeletonBanner(
                            coverUrl = navKeyCoverUrl,
                            title = navKeyTitle,
                            sharedCoverKey = sharedCoverKey,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        }
                    }
                }

            is DetailsState.Error -> ErrorState(s.message)

            is DetailsState.Success -> {
                val anime = s.anime
                val lazyListState = detailsLazyListState // D-231: use hoisted state.

                // ── D-314: SIMPLE pull-to-refresh ──
                // Replaced the custom 3-stage NestedScrollConnection (stage 1 =
                // refresh episodes, stage 2 = metadata, stage 3 = everything) with
                // the official Material 3 PullToRefreshBox — the same pattern Browse
                // + Library already use. ONE gesture, ONE action: a full
                // viewModel.refreshAll(), EXACTLY like the three-dot "Refresh"
                // button (user spec 2026-08-28: "a simple pull-to-refresh … it
                // will refresh the whole page exactly like how it will be
                // refreshed using the refresh button"). The themed M3 indicator
                // appears while isRefreshing is true (driven by refreshAll).
                val ptrState = rememberPullToRefreshState()
                val ptrContext = LocalContext.current
                // One haptic the moment the pull first crosses the refresh
                // threshold. derivedStateOf: reading distanceFraction directly
                // here would invalidate the whole Success branch every pull
                // frame. The !isRefreshing gate skips the snap-to-1 that M3
                // applies on button-triggered refreshes (haptic = pull only).
                val ptrThresholdCrossed by remember {
                    androidx.compose.runtime.derivedStateOf {
                        ptrState.distanceFraction >= 1f && !isRefreshing
                    }
                }
                LaunchedEffect(ptrThresholdCrossed) {
                    if (ptrThresholdCrossed) {
                        HapticHelper.stageCross(ptrContext)
                    }
                }

                // D-228: onEpisodeClick is now hoisted to screen level (before
                // MaterialTheme) so both the Success branch + EpisodeSearchSheet
                // (outside MaterialTheme) can use it.

                // D-318: custom themed indicator — a surface pill that slides
                // down + scales in with the pull, fills a determinate arc while
                // pulling, then spins indeterminately for the WHOLE refresh
                // (isRefreshing now clears only when the refreshes complete —
                // see DetailsViewModel.refreshAll). Default M3 indicator was too
                // plain + vanished while the fetch was still running.
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAll() },
                    state = ptrState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshIndicator(
                            state = ptrState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
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
                                // D-315: cover tap → full-screen viewer.
                                onCoverClick = { bounds ->
                                    coverViewerBounds = bounds
                                    showCoverViewer = true
                                },
                                // D-320: shared-element key — the banner cover
                                // morphs from the source card on entry + back.
                                sharedCoverKey = sharedCoverKey,
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
                                // D-242: Tracking — opens the TrackSheet.
                                onOpenTracking = {
                                    showMenu = false
                                    viewModel.openTrackSheet()
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

                        // ── Episodes section (D-228: flattened for virtualization) ──
                        // Header + non-Loaded states (Idle/Loading/Empty/Error/CloudflareBlocked)
                        // are rendered inside EpisodesSection. When Loaded, the episode rows
                        // are emitted as lazy items() BELOW — proper Compose virtualization.
                        //
                        // D-232: Compute the processed/grouped episodes BEFORE the
                        // EpisodesSection call so the group switcher data is available
                        // for the header (the switcher is inline in the header now).
                        val rawEpisodes = (episodeState as? EpisodeState.Loaded)?.episodes
                        val processedEpisodes = if (rawEpisodes != null) {
                            applyEpisodeListPreferences(
                                episodes = rawEpisodes,
                                metadata = episodeMetadata,
                                downloadStates = downloadStates,
                                watchProgress = watchProgress,
                                mainId = viewModel.currentMainId,
                                downloadedFilter = downloadedFilter,
                                watchedFilter = watchedFilter,
                                audioFilter = audioFilter,
                                sortMode = sortMode,
                                sortDescending = sortDescending,
                            )
                        } else null
                        // D-307/D-308: season detection — when the episode names carry
                        // season tags ("( Season 5 - Episode 12 - ... )") AND the user
                        // hasn't opted for plain grouping, seasons take over the
                        // organization (chip selector) and number-range grouping is
                        // suppressed (single group → the range switcher hides).
                        // seasonGroups is computed at the top level (shared with the
                        // settings sheet) from the RAW episode list.
                        // Season selector options: "All" (null) + each season bucket.
                        val seasonOptions: List<SeasonGroup?> =
                            seasonGroups?.let { listOf<SeasonGroup?>(null) + it } ?: emptyList()
                        val selectedSeasonIndex = currentSeasonIndex.coerceIn(0, (seasonOptions.size - 1).coerceAtLeast(0))
                        val selectedSeason = seasonOptions.getOrNull(selectedSeasonIndex)
                        val episodeGroups = if (processedEpisodes != null) {
                            when {
                                // Seasons active: one implicit group (the full list) —
                                // the season chips do the slicing.
                                seasonGroups != null -> listOf(EpisodeGroup(0, 0, 0, processedEpisodes))
                                // D-317 OFF mode: a flat list — no season selector,
                                // no number-range groups (single group → switcher hides).
                                organizeMode == "OFF" -> listOf(EpisodeGroup(0, 0, 0, processedEpisodes))
                                else -> groupEpisodes(processedEpisodes, groupingSize)
                            }
                        } else null
                        val currentGroup = if (episodeGroups != null && episodeGroups.size > 1) {
                            episodeGroups.getOrElse(currentGroupIndex) { episodeGroups.first() }
                        } else null
                        // D-308: the season slice wins when seasons are active. The
                        // slice = the selected season's URLs ∩ the processed (filtered +
                        // sorted) list, so filters/sort apply WITHIN the season.
                        // "All" (null option) → the full processed list.
                        val episodesToShow = when {
                            seasonGroups != null && selectedSeason != null -> {
                                val seasonUrls = selectedSeason.episodes.map { it.url }.toSet()
                                processedEpisodes?.filter { it.url in seasonUrls }
                            }
                            seasonGroups != null -> processedEpisodes
                            currentGroup != null -> currentGroup.episodes
                            else -> processedEpisodes
                        }

                        // Task 55 (round 15): the sub/dub display modes (bridged
                        // CloudStream lists carrying "(Sub)"/"(Dub)" tags).
                        //  - COMBINED: sibling rows merge into one (tag stripped;
                        //    the primary Sub handle rides the row — the CS resolve
                        //    flow re-finds the sibling + resolves BOTH flavors);
                        //  - SEPARATE: the selected flavor's rows only (the Sub |
                        //    Dub chip row renders above the list when both exist).
                        // GATED on a CloudStream-bridged source: the feature is
                        // CS-scoped by spec, so aniyomi extension lists are a
                        // GUARANTEED no-op (ADDITIVE-ONLY invariant — some aniyomi
                        // exts use scanlator/name conventions that would otherwise
                        // trigger the filter chips on the legacy stack).
                        val subDubTagged = viewModel.isLinkedSourceCloudStream() &&
                            episodesToShow.orEmpty().any { subDubEpisodeTag(it) != null }
                        val subDubBothFlavors = subDubTagged && run {
                            val tags = episodesToShow.orEmpty().mapNotNull { subDubEpisodeTag(it) }.toSet()
                            "SUB" in tags && "DUB" in tags
                        }
                        val displayEpisodes = when {
                            !subDubTagged -> episodesToShow
                            subDubMode == "COMBINED" -> episodesToShow?.let(::mergeSubDubEpisodeRows)
                            // Single-flavor lists (DUB-only etc.) render unfiltered —
                            // no switcher exists to change the flavor, and filtering
                            // would blank the list (review finding round-15 R2).
                            subDubBothFlavors -> episodesToShow?.filter {
                                subDubEpisodeTag(it) == subDubFlavor || subDubEpisodeTag(it) == null
                            }
                            else -> episodesToShow
                        }
                        val showSubDubSwitcher = subDubMode != "COMBINED" && subDubBothFlavors

                        // Task 56 (round 16 — F3): the RENDERED rows for CS
                        // sub/dub lists carry per-flavor ORDINALS + tag-stripped
                        // names. The Dub list restarts at "EP 1" (the raw numbers
                        // globally continue — the normalizer's uniqueness contract
                        // — and they keep their identity roles: progress, cache,
                        // metadata, the CS hand-off). The name never repeats the
                        // flavor the switcher + audio pill already show. Identity
                        // fields (url/episode_number/scanlator) ride the copies
                        // untouched, so clicks, downloads and watched-state keys
                        // are byte-identical to the pre-display rows.
                        val subDubOrdinals = if (subDubTagged) {
                            subDubFlavorOrdinals(episodesToShow.orEmpty())
                        } else emptyMap()
                        val subDubDisplayEpisodes = if (subDubTagged) {
                            displayEpisodes.orEmpty().map { ep ->
                                eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
                                    copyFrom(ep)
                                    name = stripSubDubTag(ep.name)
                                }
                            }
                        } else displayEpisodes.orEmpty()

                        item {
                            EpisodesSection(
                                linkedSource = effectiveLinkedSource,
                                episodeState = episodeState,
                                episodeMetadata = episodeMetadata,
                                hasAnilistId = anime.anilistId != null,
                                reverseAutoLinkState = reverseAutoLinkState,
                                matchPreviewVisible = matchPreviewVisible,
                                onOpenSourcePicker = { showManualSearch = true },
                                onOpenCloudflareWebView = onOpenCloudflareWebView,
                                onUnlinkSource = { viewModel.unlinkSource() },
                                onEpisodeClick = { ep -> onEpisodeClick(ep, false) },
                                downloadStates = downloadStates,
                                onDownloadEpisode = { episode ->
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
                                mainId = viewModel.currentMainId,
                                watchProgress = watchProgress,
                                onToggleWatched = { epKey -> viewModel.toggleWatched(epKey) },
                                onOpenEpisodeSettings = { showEpisodeSettingsSheet = true },
                                onOpenEpisodeSearch = { showEpisodeSearch = true },
                                // D-232: Group switcher data (inline in header).
                                currentGroup = currentGroup,
                                totalGroups = episodeGroups?.size ?: 0,
                                onPrevGroup = { if (currentGroupIndex > 0) currentGroupIndex-- },
                                onNextGroup = {
                                    val max = (episodeGroups?.size ?: 1) - 1
                                    if (currentGroupIndex < max) currentGroupIndex++
                                },
                                // D-308: Season selector data (below the header row,
                                // between the source pill and the episode list).
                                seasonOptions = seasonOptions,
                                selectedSeasonIndex = selectedSeasonIndex,
                                onSelectSeason = { index -> currentSeasonIndex = index },
                            )
                        }

                        // D-228: Lazy episode rows — virtualized! Only ~15 rows are
                        // composed at a time (the visible window), not all 1000.
                        // key = { it.url } gives each row a stable identity.
                        //
                        // D-229: The episode list is HIDDEN while the match-preview card
                        // is visible (matchPreviewVisible == true). The user should only
                        // see the preview card during that window — the episodes load in
                        // the background but don't appear until the preview dismisses.
                        //
                        // D-232: rawEpisodes/processedEpisodes/episodeGroups/currentGroup/
                        // episodesToShow are now computed ABOVE (before EpisodesSection).

                        if (episodesToShow != null && !matchPreviewVisible) {
                            // D-233: Empty-state when filters produce no results.
                            if (episodesToShow.isEmpty() && rawEpisodes?.isNotEmpty() == true) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = "No episodes match your filters",
                                            fontFamily = RobotoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { episodeListPrefs.resetFilters() }) {
                                            Text(
                                                "Reset filters",
                                                fontFamily = RobotoFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            } else {
                            // D-234: Next-episode card — shows at the top of the list
                            // when showNextEpisode is enabled + there's a future episode.
                            // D-237: Use a unique key to prevent LazyColumn key collisions
                            // with episode rows (which are keyed by it.url).
                            if (showNextEpisode && nextEpisodeInfo != null) {
                                item(key = "next_episode_card") {
                                    NextEpisodeCard(nextEpisodeInfo!!)
                                }
                            }
                            // D-232: Group switcher is now INLINE in the EpisodesSection
                            // header (between "Episodes" text and source pill), not here.
                            // Task 55: the Sub/Dub switcher chips (SEPARATE mode,
                            // both flavors) — the SeasonSelectorRow chip language.
                            if (showSubDubSwitcher) {
                                item(key = "subdub-switcher") {
                                    SubDubSelectorRow(
                                        selected = subDubFlavor,
                                        onSelect = { subDubFlavor = it },
                                    )
                                }
                            }
                            items(subDubDisplayEpisodes, key = { it.url }) { episode ->
                                val epNum = episode.episode_number.toInt()
                                val metadata = episodeMetadata[epNum]
                                // D-317: contextual episode tag.
                                //  - A specific season is selected → the episode's
                                //    PER-SEASON number (S1 rows show 1..8, S2 rows
                                //    show 1..8 — user spec).
                                //  - All list of MULTI-season content (+ the
                                //    season-tag setting is on) → "S-n/E-m" with
                                //    season + episode in two shades of the theme
                                //    color.
                                //  - Otherwise → the plain global "EP n" tag
                                //    (unchanged).
                                // D-324: the compound tag requires an ACTIVATED
                                // multi-season structure (groups != null ⇔ the
                                // detector found ≥2 seasons). No-season and
                                // single-season content ALWAYS shows the plain
                                // tag — "S-1/E-5" there is noise the user
                                // explicitly rejected (2026-08-29 feedback).
                                val episodeTag: EpisodeTag? = when {
                                    seasonGroups != null && selectedSeason != null -> {
                                        seasonInfo?.seasonNumbersByEpisodeUrl?.get(episode.url)
                                            ?.let { EpisodeTag(season = null, number = it.toString()) }
                                    }
                                    seasonTagInNumber && seasonInfo?.groups != null -> {
                                        val a = seasonInfo?.assignmentsByEpisodeUrl?.get(episode.url)
                                        if (a?.season != null && a.episodeInSeason != null) {
                                            EpisodeTag(season = a.season, number = a.episodeInSeason.toString())
                                        } else {
                                            null
                                        }
                                    }
                                    else -> null
                                }
                                // Task 56 (F3b): CS sub/dub rows with no season tag
                                // show the per-flavor ORDINAL — "EP 1" on the Dub
                                // list's first row instead of the continuing raw
                                // number. Season tags (when active) stay authoritative.
                                val effectiveEpisodeTag = episodeTag
                                    ?: subDubOrdinals[episode.url]?.let {
                                        EpisodeTag(season = null, number = it.toString())
                                    }
                                val stateKey = viewModel.episodeDownloadStateKey(episode)
                                val downloadState = stateKey?.let { downloadStates[it] }
                                    ?: EpisodeDownloadState.NotDownloaded
                                val mainId = viewModel.currentMainId
                                // Task 57 (round 17 — P1): the watched-progress
                                // key is the flavor ORDINAL for tagged CS lists
                                // (sub-5 ↔ dub-5 = ONE episode → ONE row → ONE
                                // toggle — watching sub 80% shows on the dub
                                // row). Untagged lists keep the raw number
                                // (byte-identical aniyomi behavior). The key
                                // FORMAT is unchanged — only the number source.
                                val identityNum = if (subDubTagged) {
                                    subDubOrdinals[episode.url] ?: epNum
                                } else epNum
                                val epKey = if (mainId != null) "$mainId|${String.format("%05d", identityNum)}" else null
                                val progress = epKey?.let { watchProgress[it] }
                                val isWatched = progress?.isWatched ?: false
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                                    EpisodeRow(
                                        episode = episode,
                                        metadata = metadata,
                                        episodeTag = effectiveEpisodeTag,
                                        onClick = { onEpisodeClick(episode, false) },
                                        downloadState = downloadState,
                                        fallbackCoverUrl = anime.coverUrl,
                                        onDownload = { currentEpisode = episode; resolverDownloadMode = true; viewModel.resolveEpisode(episode); showResolverSheet = true },
                                        onPause = { viewModel.pauseEpisodeDownload(episode) },
                                        onResume = { viewModel.resumeEpisodeDownload(episode) },
                                        onCancel = { viewModel.cancelEpisodeDownload(episode) },
                                        onRetry = { viewModel.retryEpisodeDownload(episode) },
                                        onDelete = { viewModel.deleteDownloadedEpisode(episode) },
                                        onPlayDownloaded = { onEpisodeClick(episode, false) },
                                        isWatched = isWatched,
                                        progressFraction = progress?.progressFraction ?: 0f,
                                        onToggleWatched = { epKey?.let { viewModel.toggleWatched(it) } },
                                    )
                                }
                            }
                            // Unlink button at the bottom.
                            item {
                                TextButton(
                                    onClick = { viewModel.unlinkSource() },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        "Unlink source",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                            } // end else (non-empty episodesToShow)
                        }

                        // ── Info ──
                        item {
                            Spacer(Modifier.height(16.dp))
                            // D-193 v2: per-anime notification config (only shown when the
                            // user has enabled "Customize per anime" on the Notifications page).
                            DetailsNotificationSection(mainId = viewModel.currentMainId)
                        }
                        item {
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

                    // D-314: the D-146 "Refreshing..." pill + the 3-stage pull
                    // indicator are GONE — the Material 3 PullToRefreshBox's own
                    // themed indicator covers ALL refresh feedback (pull-triggered
                    // AND button-triggered, via isRefreshing).
                }
            }
        }

        // D-315: full-screen cover viewer — rendered as the LAST child of the
        // root Box so it layers over the entire details page (inside the
        // adaptive MaterialTheme so its buttons pick up the per-anime accent).
        val viewerBounds = coverViewerBounds
        val viewerCoverUrl = (state as? DetailsState.Success)?.anime?.coverUrl
        if (showCoverViewer && viewerBounds != null && viewerCoverUrl != null) {
            CoverViewerOverlay(
                imageUrl = viewerCoverUrl,
                contentDescription = (state as? DetailsState.Success)?.anime?.displayName,
                originBounds = viewerBounds,
                onDismiss = { showCoverViewer = false },
            )
        }
    }
    } // end MaterialTheme(colorScheme = effectiveColorScheme) { ... }

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
            // D-210: "Open in WebView" on the resolver Error state — opens the
            // source's episode page in a WebView so the user can solve Cloudflare
            // or browse the source manually. Null if no source is linked.
            onOpenInWebView = {
                viewModel.getSourceEpisodeUrl()?.let { url ->
                    val sourceName = effectiveLinkedSource?.sourceName ?: "Source"
                    onOpenCloudflareWebView(url, sourceName)
                }
            },
            onPickVideo = { video, serverName, audioLabel ->
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
                        // D-151-fix: pass the resolver serverName + audioLabel (not
                        // linked.sourceName which is the extension name).
                        Logger.i("Anikuta:Feature:Details") {
                            "onPickVideo — download mode: calling onDownloadSpecificVideo (server=$serverName, audio=$audioLabel)"
                        }
                        onDownloadSpecificVideo(
                            ep,
                            video,
                            serverName,
                            linked.sourceId.toString(),
                            audioLabel,
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
                    // D-306: extension-first merge (shared with the episode rows) —
                    // format: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator" per line.
                    val epMetaStr = buildEpisodeMetadataSerialized(
                        episodes = (episodeState as? EpisodeState.Loaded)?.episodes ?: emptyList(),
                        metadata = episodeMetadata,
                        currentScanlator = ep.scanlator,
                    )
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

    // D-230: Episode list settings bottom sheet (tap "Episodes" text).
    // D-231: Wrapped in the adaptive accent color scheme so the sheet inherits
    // the per-anime dynamic theming (matches the rest of the details page).
    if (showEpisodeSettingsSheet) {
        val accentColorScheme = coverAccent?.let { argb ->
            val accent = androidx.compose.ui.graphics.Color(argb)
            val accentColors = com.confused.anikuta.core.designsystem.theme.AccentColors.from(accent)
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme.colorScheme.copy(
                primary = if (isDark) accentColors.darkPrimary else accentColors.lightPrimary,
                onPrimary = if (isDark) accentColors.darkOnPrimary else accentColors.lightOnPrimary,
                primaryContainer = if (isDark) accentColors.darkPrimaryContainer else accentColors.lightPrimaryContainer,
                onPrimaryContainer = if (isDark) accentColors.darkOnPrimaryContainer else accentColors.lightOnPrimaryContainer,
            )
        } ?: MaterialTheme.colorScheme
        MaterialTheme(colorScheme = accentColorScheme) {
            EpisodeListSettingsSheet(
                onDismiss = { showEpisodeSettingsSheet = false },
                // D-307: structural flag (independent of the organizeMode
                // preference) — only offer the Seasons/Grouping choice when the
                // current anime actually has a multi-season structure.
                seasonsDetected = detectedSeasons != null,
            )
        }
    }

    // D-230: Episode search field (swipe-right on "Episodes" text).
    // onEpisodeClick is hoisted to screen level so it's in scope here.
    if (showEpisodeSearch) {
        EpisodeSearchSheet(
            episodes = (episodeState as? EpisodeState.Loaded)?.episodes ?: emptyList(),
            episodeMetadata = episodeMetadata,
            query = episodeSearchQuery,
            onQueryChange = { episodeSearchQuery = it },
            onEpisodeClick = { episode ->
                showEpisodeSearch = false
                episodeSearchQuery = ""
                onEpisodeClick(episode, false)
            },
            onDismiss = {
                showEpisodeSearch = false
                episodeSearchQuery = ""
            },
        )
    }

    // D-242: TrackSheet — AniList tracking management.
    if (showTrackSheet) {
        TrackSheet(
            trackEntry = trackEntry,
            isLoggedIn = isTrackerLoggedIn,
            totalEpisodes = (state as? DetailsState.Success)?.anime?.episodes
                ?: (episodeState as? EpisodeState.Loaded)?.episodes?.size,
            seriesTitle = (state as? DetailsState.Success)?.anime?.displayName ?: "Tracking",
            onStatusChange = viewModel::updateTrackStatus,
            onProgressChange = viewModel::updateTrackProgress,
            onScoreChange = viewModel::updateTrackScore,
            onDatesChange = viewModel::updateTrackDates,
            onRemove = viewModel::removeTrackEntry,
            onDismiss = viewModel::dismissTrackSheet,
        )
    }

    // D-242: "Mark all previous episodes as watched" — bottom-anchored snackbar
    // (NOT a fullscreen dialog, per user feedback). 5s timeout with auto-confirm.
    showMarkPreviousPrompt?.let { epNum ->
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MarkPreviousEpisodesSnackbar(
                episodeNumber = epNum,
                onConfirm = { viewModel.markAllPreviousWatched(epNum) },
                onDismiss = viewModel::dismissMarkPreviousPrompt,
            )
        }
    }

    // D-242: "Mark series as watched" — bottom-anchored snackbar (only if FINISHED + all watched).
    if (showMarkSeriesPrompt) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MarkSeriesWatchedSnackbar(
                onConfirm = viewModel::markSeriesAsWatched,
                onDismiss = viewModel::dismissMarkSeriesPrompt,
            )
        }
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
    // D-315: cover tap → full-screen viewer (carries the cover's on-screen
    // bounds so the viewer can expand from the exact position).
    onCoverClick: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    // D-320: shared-element key for the cover-transition (experimental).
    sharedCoverKey: String? = null,
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
    // D-242: Tracking — opens the TrackSheet.
    onOpenTracking: () -> Unit = {},
) {
    val coverUrl = anime.coverUrl
    // D-236: Background image source — cover or banner (with fallback).
    val appPrefs = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    val bgSource = appPrefs.detailsBackgroundSource
    val tintEnabled = appPrefs.detailsBannerTint
    val animationEnabled = appPrefs.detailsBannerAnimation && appPrefs.animationsEnabled
    // D-236: Select the background image based on user preference.
    // If BANNER is selected but bannerUrl is null, fall back to cover.
    val bannerUrl = when (bgSource) {
        "BANNER" -> anime.bannerUrl ?: coverUrl
        else -> coverUrl // "COVER" (default)
    }

    // D-236: Slow pan animation — infinite transition that moves the image.
    // D-237: Increased speed (12s X, 16s Y — was 20s/28s).
    val panX by if (animationEnabled) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "bgPanX")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(12_000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "panX",
            )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val panY by if (animationEnabled) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "bgPanY")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(16_000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "panY",
            )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── Background: blurred banner image + tint + gradient ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(8.dp)
                        // D-236: Scale up 15% so panning never reveals edges.
                        .then(if (animationEnabled) Modifier.scale(1.15f) else Modifier)
                        // D-236: Apply slow pan offset.
                        .then(
                            if (animationEnabled) {
                                Modifier.offset(
                                    x = androidx.compose.ui.unit.lerp((-48).dp, 48.dp, panX),
                                    y = androidx.compose.ui.unit.lerp((-24).dp, 24.dp, panY),
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            // D-236: Accent-color tint overlay (30% alpha) — between the image
            // and the gradient so the tint colors the image without blocking the
            // gradient's readability effect.
            if (tintEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                )
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
                        // ── D-242: Tracking (highlighted, separate) ──
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Tracking",
                                    fontFamily = RobotoFamily,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onOpenTracking,
                        )
                        androidx.compose.material3.HorizontalDivider()
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
        // D-238: Align the title/meta Column to the bottom of the cover thumbnail
        // (was top-aligned by default — user requested bottom alignment).
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (coverUrl != null) {
                // D-315: track the cover's on-screen bounds so the full-screen
                // viewer can expand from this exact position.
                var coverBounds by remember {
                    mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
                }
                AsyncImage(
                    model = coverUrl,
                    contentDescription = anime.displayName,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .coverSharedElement(sharedCoverKey)
                        .clip(RoundedCornerShape(12.dp))
                        .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                        .clickable { onCoverClick(coverBounds) },
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                // D-238: Tap the title to silently copy to clipboard.
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                Text(
                    text = anime.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(anime.displayName))
                    },
                )
                Spacer(modifier = Modifier.height(6.dp))
                // D-238: Removed the "Linked to AniList" badge — no longer needed.
                // Only show the auto-linking spinner while searching.
                if (isExtensionEntry && isAutoLinkSearching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
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
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Meta row: score · year · status · episode count.
                // CloudStream V2: the year next to the title (populated for both
                // AniList entries and CS entries — the search-time seed covers
                // providers whose load() omits year).
                val metaParts = buildList {
                    anime.averageScore?.let { add("\u2605 $it%") }
                    anime.seasonYear?.let { add(it.toString()) }
                    anime.status?.let { add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    anime.episodes?.let { add("$it eps") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" \u00b7 "),
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
            )
            StarRatingBar(rating = rating, onRate = onRate)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanDesc,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
    reverseAutoLinkState: ReverseAutoLinkState = ReverseAutoLinkState.Idle,
    matchPreviewVisible: Boolean = false,
    onOpenSourcePicker: () -> Unit,
    // D-209: callback to open the Cloudflare WebView solver.
    onOpenCloudflareWebView: (url: String, sourceName: String) -> Unit,
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
    // D-230: callback to open the episode list settings bottom sheet.
    onOpenEpisodeSettings: () -> Unit = {},
    // D-230: callback to open the episode search (swipe-right gesture).
    onOpenEpisodeSearch: () -> Unit = {},
    // D-232: Group switcher data — null when grouping is inactive.
    currentGroup: EpisodeGroup? = null,
    totalGroups: Int = 0,
    onPrevGroup: () -> Unit = {},
    onNextGroup: () -> Unit = {},
    // D-308: Season selector (rendered between the header row and the episode
    // list). Non-empty ONLY when a multi-season structure is detected.
    seasonOptions: List<SeasonGroup?> = emptyList(),
    selectedSeasonIndex: Int = 0,
    onSelectSeason: (Int) -> Unit = {},
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // D-231: swipe-right → open episode search (with visual feedback + haptic).
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        var searchTriggered = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                                searchTriggered = false
                            },
                            onDragEnd = {
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                // Trigger search when the user swipes right past 80px.
                                if (totalDrag > 80f && !searchTriggered) {
                                    searchTriggered = true
                                    onOpenEpisodeSearch()
                                }
                            },
                        )
                    }
                    // D-230: tap → open episode list settings sheet.
                    .clickable { onOpenEpisodeSettings() },
            ) {
                Text(
                    text = "Episodes",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
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
            // D-232: Group switcher — inline between "Episodes" text and source pill.
            if (currentGroup != null && totalGroups > 1) {
                Spacer(Modifier.width(8.dp))
                EpisodeGroupSwitcher(
                    currentGroup = currentGroup,
                    totalGroups = totalGroups,
                    onPrev = onPrevGroup,
                    onNext = onNextGroup,
                )
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

        // ── D-308: Season selector ──
        // Horizontally-scrollable season chips between the source selector and
        // the episode list (user spec). Only rendered when seasons were detected
        // (seasonOptions is empty otherwise) + episodes are actually loaded.
        if (seasonOptions.isNotEmpty() && episodeState is EpisodeState.Loaded) {
            SeasonSelectorRow(
                options = seasonOptions,
                selectedIndex = selectedSeasonIndex,
                onSelect = onSelectSeason,
            )
        }

        // ── Episode list / states ──
        when (episodeState) {
            is EpisodeState.Idle -> {
                // D-226: Live-preview the reverse auto-link search here.
                // Instead of a static "No source linked" message, show the user
                // what's happening: searching extensions → match found → loading
                // episodes. Only falls back to "No source linked" when the
                // reverse auto-link is Idle (feature off / not applicable) or Error.
                when (reverseAutoLinkState) {
                    is ReverseAutoLinkState.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "Searching extensions…",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Looking for a matching source\nfor this anime.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    is ReverseAutoLinkState.Matched -> {
                        // D-228: Match-preview card (extracted to MatchPreviewCard).
                        MatchPreviewCard(
                            sourceName = reverseAutoLinkState.sourceName,
                            animeTitle = reverseAutoLinkState.animeTitle,
                            thumbnailUrl = reverseAutoLinkState.thumbnailUrl,
                            showLoadingHint = true,
                        )
                    }
                    is ReverseAutoLinkState.NoMatch -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "No source found",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Auto-link couldn't find a matching extension.\nTap below to search manually.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = onOpenSourcePicker) {
                                    Text(
                                        text = "Link source manually",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    is ReverseAutoLinkState.Idle, is ReverseAutoLinkState.Error -> {
                        // No reverse auto-link active — show the original placeholder.
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
                }
            }

            is EpisodeState.Loading -> {
                // D-228: When the reverse auto-link found a match and episodes
                // are now loading, show the match-preview card. Reuses MatchPreviewCard.
                val matched = reverseAutoLinkState as? ReverseAutoLinkState.Matched
                if (matched != null) {
                    MatchPreviewCard(
                        sourceName = matched.sourceName,
                        animeTitle = matched.animeTitle,
                        thumbnailUrl = matched.thumbnailUrl,
                        showLoadingHint = true,
                    )
                } else {
                    // No reverse match — standard loading spinner.
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

            is EpisodeState.CloudflareBlocked -> {
                // D-209: Cloudflare blocked the episode fetch + the headless solver failed.
                // Show "Open in WebView" (solve manually) + "Try another source".
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Cloudflare protection",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${episodeState.sourceName} is behind Cloudflare and the " +
                            "automatic bypass failed. Tap \"Open in WebView\" to solve the " +
                            "challenge manually, then re-open this anime — cookies are saved.",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = {
                        onOpenCloudflareWebView(episodeState.url, episodeState.sourceName)
                    }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open in WebView", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSourcePicker) {
                        Text("Try another source", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            is EpisodeState.Loaded -> {
                // D-228: The episode list is now rendered as lazy `items(...)` in
                // the OUTER LazyColumn (not here). This file only renders the match
                // preview card (if visible) above the episode list. The episode rows
                // + unlink button are emitted directly by the outer LazyColumn for
                // proper Compose virtualization (~60x node reduction for 1000 eps).
                val matched = reverseAutoLinkState as? ReverseAutoLinkState.Matched
                if (matchPreviewVisible && matched != null) {
                    MatchPreviewCard(
                        sourceName = matched.sourceName,
                        animeTitle = matched.animeTitle,
                        thumbnailUrl = matched.thumbnailUrl,
                        showLoadingHint = false,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-228: MatchPreviewCard — reusable card showing the reverse auto-link match
//  (cover image + matched title + source badge + optional loading hint).
//  Used in: Idle+Matched, Loading+Matched, Loaded+matchPreviewVisible.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MatchPreviewCard(
    sourceName: String,
    animeTitle: String,
    thumbnailUrl: String?,
    showLoadingHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // "Linked to" badge.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Linked to $sourceName",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Preview card: cover image (left) + matched title (right).
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover image (or placeholder if no thumbnail).
                if (!thumbnailUrl.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = animeTitle,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = animeTitle.firstOrNull()?.uppercase() ?: "?",
                            fontFamily = RobotoFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                // Matched title + optional loading hint.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animeTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showLoadingHint) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Loading episodes…",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** D-228: Minimum time the match-preview card stays visible (ms). */
private const val MATCH_PREVIEW_MIN_MS = 4000L

// ════════════════════════════════════════════════════════════════════════════
//  D-234: NextEpisodeCard — shows the upcoming episode with a countdown
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun NextEpisodeCard(info: NextEpisodeInfo) {
    // D-234: Live countdown — update every second.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info.airingAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val remainingMs = (info.airingAtMillis - now).coerceAtLeast(0)
    val days = remainingMs / (1000 * 60 * 60 * 24)
    val hours = (remainingMs / (1000 * 60 * 60)) % 24
    val minutes = (remainingMs / (1000 * 60)) % 60
    val seconds = (remainingMs / 1000) % 60
    val countdownText = when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
    val releaseDateText = remember(info.airingAtMillis) {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy • HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(info.airingAtMillis))
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number badge.
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "EP ${info.episodeNumber}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            // Release info.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (info.isComingSoon) "Coming soon" else "Next episode",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = if (info.isComingSoon) "Episode ${info.episodeNumber}" else releaseDateText,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // Countdown or "Coming soon" badge.
            if (info.isComingSoon) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Soon",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = countdownText,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-308: SeasonSelectorRow — horizontally-scrollable season chips (between
//  the source selector and the episode list). Clicking a chip selects it AND
//  smoothly centers it in the row (user spec).
// ════════════════════════════════════════════════════════════════════════════

/**
 * Season chip selector. Options: "All" (index 0) + one chip per detected
 * season + a trailing "Other" chip when untagged episodes exist.
 *
 * Centering: when the selection changes, the row animates so the selected
 * chip lands in the horizontal center (chips are visible when tapped, so the
 * exact width is known; ±estimate is fine for programmatic changes).
 */
@Composable
private fun SeasonSelectorRow(
    options: List<SeasonGroup?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Center the selected chip whenever the selection changes (tap or
    // programmatic). On first composition this runs too but harmlessly clamps
    // at scroll position 0 (D-308 review note).
    androidx.compose.runtime.LaunchedEffect(selectedIndex, options.size) {
        if (options.size <= 1) return@LaunchedEffect
        kotlinx.coroutines.delay(50) // let the new chip layout settle
        val layoutInfo = listState.layoutInfo
        val viewportWidth = layoutInfo.viewportSize.width
        if (viewportWidth <= 0) return@LaunchedEffect
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (item != null) {
            // Center the chip: its start should land at viewportCenter - width/2.
            // animateScrollToItem's scrollOffset positions the item relative to
            // the viewport start — a negative value pulls it INTO the viewport
            // (the standard centering recipe). Clamped naturally at the row ends.
            val targetOffset = -(viewportWidth / 2 - item.size / 2)
            listState.animateScrollToItem(selectedIndex, targetOffset)
        } else {
            // Chip not laid out yet (e.g. selection clamped after a reload) —
            // estimate a chip width (~90dp) so centering still roughly lands;
            // any later tap self-corrects (D-308 review fix).
            val estimatedWidth = with(density) { 90.dp.toPx() }.toInt()
            val targetOffset = -(viewportWidth / 2 - estimatedWidth / 2)
            listState.animateScrollToItem(selectedIndex, targetOffset)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = 2.dp,
        ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = options.size,
            key = { index ->
                when (val option = options[index]) {
                    // null option = the "All" chip; SeasonGroup(null) = "Other".
                    null -> "season-all"
                    else -> option.season?.let { "season-$it" } ?: "season-other"
                }
            },
        ) { index ->
            SeasonChip(
                label = when (val option = options[index]) {
                    null -> "All"
                    // null season = the "Other" bucket (untagged episodes).
                    else -> option.season?.let { "Season $it" } ?: "Other"
                },
                isSelected = index == selectedIndex,
                onClick = {
                    // Always propagate (D-308 review fix): re-tapping the visually
                    // selected chip must write the index back — otherwise a
                    // clamped stale selection (after an episode reload shrinks the
                    // options) leaves the chip unresponsive.
                    com.confused.anikuta.core.common.HapticHelper.lightTick(context)
                    onSelect(index)
                },
            )
        }
    }
}

/** One season pill. Selected = primary fill; unselected = translucent surface. */
@Composable
private fun SeasonChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingStandard),
        label = "seasonChipScale",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(Motion.DurationShort),
        label = "seasonChipBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(Motion.DurationShort),
        label = "seasonChipFg",
    )
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-231: EpisodeGroupSwitcher — shows between "Episodes" text and source pill
//  when grouping is active. Lets the user switch between groups of episodes.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EpisodeGroupSwitcher(
    currentGroup: EpisodeGroup,
    totalGroups: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            // Previous button.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = currentGroup.index > 0, onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Previous group",
                    tint = if (currentGroup.index > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp).rotate(90f),
                )
            }
            // Group label — D-233: lowEpisode is always the smaller number.
            Text(
                text = "EP ${currentGroup.lowEpisode}-${currentGroup.highEpisode}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            // Next button.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = currentGroup.index < totalGroups - 1, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next group",
                    tint = if (currentGroup.index < totalGroups - 1)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp).rotate(-90f),
                )
            }
        }
    }
}

/**
 * D-306: Serialize episode metadata for the Watch screen with the SAME
 * extension-first priority the episode rows use (EpisodeDisplayResolver), so
 * Details and Watch render identical titles/thumbnails/descriptions.
 * Provider-only values fill the gaps; the per-episode scanlator comes from the
 * extension episode (falling back to the current episode's, as before).
 *
 * Format per line: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator".
 */
private fun buildEpisodeMetadataSerialized(
    episodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
    metadata: Map<Int, com.confused.anikuta.core.metadata.EpisodeMetadata>,
    currentScanlator: String?,
): String {
    val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
    val byNumber = episodes.associateBy { it.episode_number.toInt() }
    return metadata.entries.joinToString("\n") { (epNum, meta) ->
        val ext = byNumber[epNum]
        val title = ext?.let { EpisodeDisplayResolver.extensionTitle(it) } ?: meta.title ?: ""
        val thumb = ext?.preview_url?.takeIf { it.isNotBlank() } ?: meta.thumbnailUrl ?: ""
        val date = meta.airDate?.toString() ?: "0"
        val desc = ext?.summary?.takeIf { it.isNotBlank() } ?: meta.description ?: ""
        val scanlator = ext?.scanlator ?: currentScanlator ?: ""
        "$epNum${delim}$title${delim}$thumb${delim}$date${delim}$desc${delim}$scanlator"
    }
}

/**
 * D-317: Display override for the episode number badge.
 *
 * @param season Non-null → render the "S-n/E-m" compound tag (season +
 *        episode in two shades of the theme color). D-324: only ever set for
 *        ACTIVATED multi-season content — no-season and single-season lists
 *        always use the plain tag below.
 * @param number The number text to display (per-season number inside a season
 *        slice, or whatever the caller resolved).
 */
data class EpisodeTag(
    val season: Int?,
    val number: String,
)

@Composable
private fun EpisodeRow(
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    metadata: com.confused.anikuta.core.metadata.EpisodeMetadata?,
    onClick: () -> Unit,
    // D-317: contextual episode tag (per-season number / "S-n/E-m" compound).
    episodeTag: EpisodeTag? = null,
    downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    // D-229: Fallback cover URL (the anime's cover image) — used when the
    // episode has no per-episode thumbnail. Prevents bare circle placeholders.
    fallbackCoverUrl: String? = null,
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
    // D-306: extension-first resolution — the extension's own title/description/
    // thumbnail WIN; provider metadata (AniZip/Jikan/Kitsu/AniList) fills the gaps.
    // Shared rules live in EpisodeDisplayResolver (single source of truth).
    val displayTitle = remember(episode, metadata) {
        EpisodeDisplayResolver.title(episode, metadata)
    }
    val description = remember(episode, metadata) {
        EpisodeDisplayResolver.description(episode, metadata)
    }
    // D-230: Thumbnail fallback is now configurable via EpisodeListPreferences.
    // - "COVER" → fall back to the anime's cover image (default).
    // - "NONE" → no image (bare placeholder).
    val episodeListPrefs = koinInject<com.confused.anikuta.core.preferences.EpisodeListPreferences>()
    val thumbnailFallback by episodeListPrefs.thumbnailFallback.changes.collectAsState(
        initial = episodeListPrefs.thumbnailFallback.get(),
    )
    val thumbnailUrl = when {
        // D-306: extension-provided preview_url first.
        !episode.preview_url.isNullOrBlank() -> episode.preview_url
        !metadata?.thumbnailUrl.isNullOrBlank() -> metadata?.thumbnailUrl
        thumbnailFallback == "COVER" -> fallbackCoverUrl
        else -> null
    }
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
        // D-211: changed from Surface to Box so we can overlay a full-width download
        // progress bar at the bottom (under the buttons, spanning the entire card width).
        Box(
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
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        // Shows 'EP N' (not just 'N'). Positioned at TopStart (like old project).
                        // D-317: contextual variants — a per-season number inside a season
                        // slice, or the "S-3/E-5" compound tag (season + episode in two
                        // shades of the theme color, slash separator) in the All list when
                        // the "season in episode tag" setting is on.
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            if (episodeTag != null && episodeTag.season != null) {
                                val tagColor = MaterialTheme.colorScheme.onPrimary
                                val compoundTag = androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = tagColor.copy(alpha = 0.68f),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("S-${episodeTag.season}") }
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = tagColor.copy(alpha = 0.45f),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("/") }
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = tagColor,
                                            fontWeight = FontWeight.ExtraBold,
                                        ),
                                    ) { append("E-${episodeTag.number}") }
                                }
                                Text(
                                    text = compoundTag,
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    letterSpacing = 0.3.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else {
                                Text(
                                    text = "EP ${episodeTag?.number ?: epNumText}",
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
                            // Task 56: the disc shows the same number the EP tag
                            // would — the per-flavor ordinal for CS sub/dub rows.
                            Text(
                                text = episodeTag?.number ?: epNumText,
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
                            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
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
                                        color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                                                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
        // D-211: full-width download progress bar overlay at the bottom of the card.
        // Spans the ENTIRE card width (under the buttons too). Doesn't add height —
        // it's an overlay on the Box, aligned BottomCenter. Only shows when downloading.
        if (downloadState is EpisodeDownloadState.Downloading) {
            LinearProgressIndicator(
                progress = { (downloadState.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
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
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
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
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Error state
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorState(message: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    // D-223fix: Truncate long error messages (e.g. raw SQLite stack traces).
    // Show first ~150 chars + "..." if longer. User can copy the full message.
    val displayMessage = if (message.length > 150) {
        message.take(150) + "..."
    } else {
        message
    }

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
            text = displayMessage,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (message.length > 150) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.TextButton(onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message))
                }) {
                    Text("Copy error", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-320: Details loading skeleton (shared-element landing spot)
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-320: Loading-state banner skeleton at the EXACT DetailBanner geometry
 * (360dp blurred backdrop + gradient + 100×150dp cover at BottomStart + title).
 *
 * Shown only when the source screen handed us a cover via the nav key (the
 * shared-element transition path): the morphing cover lands here the instant
 * the details screen composes — no blank flash while the data loads — and the
 * later swap to the real Success banner is pixel-seamless because the cover
 * sits at identical bounds. A centered spinner fills the rest of the screen.
 */
@Composable
private fun DetailsSkeletonBanner(
    coverUrl: String,
    title: String?,
    sharedCoverKey: String,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Blurred backdrop (same recipe as the real banner).
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(8.dp),
                )
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
            // Bottom row: the morphing cover + title.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .coverSharedElement(sharedCoverKey)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            fontFamily = RobotoFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  D-318: Custom pull-to-refresh indicator (details page)
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-318: A themed refresh indicator that REPLACES the default M3 one (user
 * report: "it should show a better-looking refresh one coming down, and the
 * refresh animation should play until it is refreshed properly").
 *
 * Behavior (all values derived from [state], no extra recomposition):
 * - Rest: fully transparent (nothing visible).
 * - Pulling: slides down from the top edge, scales 0.55→1 with the pull, and a
 *   determinate arc fills 0→100% of the pull distance (theme primary on a
 *   floating surface disc with a soft shadow).
 * - Refreshing: full-size disc with an indeterminate spinner — stays for the
 *   WHOLE refresh ([isRefreshing] is driven by refreshAll's real completion).
 *
 * All per-frame values are read inside graphicsLayer — the composition itself
 * never invalidates during a pull.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)  // PullToRefreshState
@Composable
private fun PullToRefreshIndicator(
    state: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(top = 18.dp)
            .graphicsLayer {
                val fraction = state.distanceFraction.coerceAtLeast(0f)
                val appear = fraction.coerceIn(0f, 1f)
                // While refreshing M3 snaps distanceFraction to 1 → fully shown.
                val shown = if (isRefreshing) 1f else appear
                scaleX = 0.55f + 0.45f * shown
                scaleY = 0.55f + 0.45f * shown
                alpha = shown
                // Slide down as the pull grows (subtle — stays near the top).
                translationY = 10.dp.toPx() * shown
            },
    ) {
        Box(
            modifier = Modifier.padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                CircularProgressIndicator(
                    progress = { state.distanceFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
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

// ════════════════════════════════════════════════════════════════════════════
//  Task 55 (round 15): sub/dub episode display helpers
//  (bridged CloudStream lists whose rows carry "(Sub)"/"(Dub)" tags)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The sub/dub tag of an episode row: the bridge appends " (Sub)"/" (Dub)" to
 * the NAME and mirrors it in `scanlator`. Null = a neutral row (dual-audio
 * shared handle — never merged).
 */
private fun subDubEpisodeTag(ep: eu.kanade.tachiyomi.animesource.model.SEpisode): String? {
    val name = ep.name?.trim()?.uppercase() ?: ""
    if (name.endsWith("(SUB)")) return "SUB"
    if (name.endsWith("(DUB)")) return "DUB"
    // The bridge mirrors its exact label ("Sub"/"Dub") into scanlator —
    // matching ONLY those keeps aniyomi scanlator conventions (SUBBED/
    // DUBBED group tags etc.) out of the CS-scoped feature (review R3).
    return when (ep.scanlator?.trim()?.uppercase()) {
        "SUB" -> "SUB"
        "DUB" -> "DUB"
        else -> null
    }
}

/**
 * Strips the trailing tag from an episode name (COMBINED display).
 */
private fun stripSubDubTag(name: String?): String {
    val n = name?.trim() ?: return ""
    val upper = n.uppercase()
    return if (upper.endsWith("(SUB)") || upper.endsWith("(DUB)")) n.dropLast(6).trim() else n
}

/**
 * Task 56 (round 16 — F3b): per-flavor display ordinals, the SEpisode twin
 * of CsSubDubSiblings.flavorOrdinals (anime-details cannot import
 * feature:cs-watch:api — the replication rule). Keyed by the row's url.
 *
 * WHY: the details pipeline guarantees globally-unique episode numbers
 * (EpisodeListNormalizer renumbers duplicates 1..N in list order — sub
 * first), so the second flavor's rows always CONTINUE (dub 13–24 for a
 * 12+12 show) and number-equality pairing never matches. Ordinals renumber
 * each flavor 1..N by (episode_number, list position) — display + pairing
 * ONLY; the raw numbers keep their identity roles (watch progress, the
 * episode cache, metadata maps, the CS hand-off).
 */
private fun subDubFlavorOrdinals(
    episodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
): Map<String, Int> {
    val ordinals = HashMap<String, Int>()
    listOf("SUB", "DUB").forEach { flavor ->
        val rows = episodes.withIndex()
            .filter { subDubEpisodeTag(it.value) == flavor }
            .sortedWith(compareBy({ (i, ep) -> ep.episode_number }, { (i, _) -> i }))
        rows.forEachIndexed { ordinal, (_, ep) ->
            ordinals[ep.url] = ordinal + 1
        }
    }
    return ordinals
}

/**
 * COMBINED display: merges sub/dub sibling rows (same flavor ORDINAL,
 * opposite tags) into ONE row — the tag stripped, the Sub row's handle kept
 * (the CS resolve flow re-finds the sibling and resolves BOTH flavors; the
 * user picks via the sheet's audio chips).
 *
 * Task 56: pairing runs on ordinals (F4) — a 12+12 show merges into 12 rows
 * even though the dub rows carry continuing numbers (13–24).
 */
private fun mergeSubDubEpisodeRows(
    episodes: List<eu.kanade.tachiyomi.animesource.model.SEpisode>,
): List<eu.kanade.tachiyomi.animesource.model.SEpisode> {
    if (episodes.none { subDubEpisodeTag(it) != null }) return episodes
    val ordinals = subDubFlavorOrdinals(episodes)
    val out = mutableListOf<eu.kanade.tachiyomi.animesource.model.SEpisode>()
    val usedUrls = mutableSetOf<String>()
    episodes.forEach { ep ->
        if (ep.url in usedUrls) return@forEach
        val tag = subDubEpisodeTag(ep)
        if (tag == null) {
            out += ep
            usedUrls += ep.url
            return@forEach
        }
        val ordinal = ordinals[ep.url]
        val sibling = episodes.firstOrNull { other ->
            other.url != ep.url &&
                subDubEpisodeTag(other)?.let { it != tag } == true &&
                ordinals[other.url] == ordinal
        }
        if (sibling == null) {
            out += ep
            usedUrls += ep.url
        } else {
            usedUrls += sibling.url
            val primary = if (tag == "SUB") ep else sibling
            usedUrls += primary.url
            // Merge the metadata-bearing fields of the primary + strip the tag.
            // Task 57 (round 17 — P2): scanlator = "Sub/Dub" — the merged row's
            // flavor signal. EpisodeRow consumes scanlator ONLY through
            // parseAudioAvailability (it never renders the raw string), and
            // "Sub/Dub" contains both words → the existing SUB·DUB audio pill
            // renders. Round 16 nulled it and the pill vanished. The value is
            // tag-neutral for subDubEpisodeTag ("SUB/DUB" ≠ the exact "SUB"/
            // "DUB" labels), so the merged row never re-merges or re-filters.
            out += eu.kanade.tachiyomi.animesource.model.SEpisode.create().apply {
                url = primary.url
                name = stripSubDubTag(primary.name)
                episode_number = primary.episode_number
                scanlator = "Sub/Dub"
                date_upload = primary.date_upload
                preview_url = primary.preview_url
                summary = primary.summary
                fillermark = primary.fillermark
            }
        }
    }
    return out
}

/**
 * The Sub | Dub chip row (SEPARATE mode) — the SeasonSelectorRow chip
 * language, fixed two options.
 */
@Composable
private fun SubDubSelectorRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        listOf("SUB" to "Sub", "DUB" to "Dub").forEach { (value, label) ->
            val isSelected = selected == value
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}
