package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageRequest
import coil3.imageLoader
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * Browse screen — the home tab (D-253 complete UI overhaul).
 *
 * Layout: CollapsingHeader → pull-to-refresh → scrollable LazyColumn containing:
 *  1. Hero pager (top trending anime — D-257 hero v3: inset 16:9 rounded card
 *     with banner backdrop + cover poster, infinite smooth auto-advance)
 *  2. Continue Watching carousel (16:9 thumbs + play affordance + progress bar)
 *  3. Trending Now / Popular / Top Rated (horizontal card carousels)
 *
 * D-257 changes vs D-256:
 * - Hero redesigned as a padded rounded card (16:9 — the banner's native
 *   aspect; the old full-bleed block cropped it into a square-ish frame) with
 *   page dots below the card and a virtually-circular pager so auto-advance
 *   always slides FORWARD (the old wraparound swept backwards through every
 *   page — the "not smooth / not animated" glitch).
 * - All section cover images are PRELOADED into Coil's caches as soon as the
 *   data arrives (SectionPreloader) — no first-view load waits.
 *
 * D-253 (historical):
 * - Cards: 2:3 covers with 12dp corners + subtle 1dp borders, amber pointed
 *   score corner-tag (unified with the Library badge language, D-252).
 * - Loading = shimmer skeletons (no full-screen spinner); Error = EmptyState
 *   + Retry button (no dead-end text).
 * - Sections fade+expand in when their data arrives — never pop in.
 *
 * CORE_RULES §22: smooth animations. §23: reactive state. The DB-7 debug
 * context, PTR haptic, and the continue-watching direct-play callback
 * contract are preserved exactly from the D-248/D-249 implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigate: (NavKey) -> Unit,
    // D-248: direct-to-player launch for continue-watching cards.
    onPlayContinueWatching: ((ContinueWatchingItem) -> Unit)? = null,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val popular by viewModel.popular.collectAsState()
    val topRated by viewModel.topRated.collectAsState()
    val heroItems by viewModel.heroItems.collectAsState()

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = com.confused.anikuta.core.debugapi.LocalDebugContextUpdater.current
    val browseCtx = remember(state, popular.size, topRated.size) {
        val animeCount = when (state) {
            is BrowseState.Success -> (state as BrowseState.Success).anime.size
            else -> 0
        }
        com.confused.anikuta.core.debugapi.DebugContext(
            screenName = "Browse",
            screenData = mapOf(
                "state" to (state::class.simpleName ?: "Unknown"),
                "animeCount" to animeCount.toString(),
                "popularCount" to popular.size.toString(),
                "topRatedCount" to topRated.size.toString(),
                "isRefreshing" to isRefreshing.toString(),
            ),
        )
    }
    LaunchedEffect(browseCtx) { updateDebugContext(browseCtx) }
    DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }
    val ptrState = rememberPullToRefreshState()
    val context = LocalContext.current

    val thresholdCrossed = ptrState.distanceFraction >= 1f
    LaunchedEffect(thresholdCrossed) {
        if (thresholdCrossed) {
            HapticHelper.stageCross(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(title = "Browse", collapsed = collapsed)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = ptrState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state) {
                    is BrowseState.Loading -> BrowseSkeleton()
                    is BrowseState.Error -> BrowseErrorState(
                        message = s.message,
                        onRetry = { viewModel.refresh() },
                    )
                    is BrowseState.Success -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ── D-257: image preloading ──
                            // Warm Coil's memory+disk caches for every section as
                            // soon as the data arrives, so the first scroll
                            // through a carousel is instant (the user reported
                            // covers visibly loading on first view). Order matters:
                            // the hero loads first, then visible-first sections.
                            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                            val heroCardWidth = screenWidth - 32.dp
                            SectionPreloader(
                                urls = heroItems.flatMap { listOf(it.bannerImage, it.coverUrl) },
                                width = heroCardWidth,
                                height = heroCardWidth * 9f / 16f,
                            )
                            SectionPreloader(
                                urls = continueWatching.map { it.thumbnailUrl ?: it.coverUrl },
                                width = 168.dp,
                                height = 94.dp,
                            )
                            SectionPreloader(urls = s.anime.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = popular.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = topRated.map { it.coverUrl }, width = 128.dp, height = 192.dp)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 90.dp),
                            ) {
                            // ── Hero pager (inset 16:9 card, auto-advancing) ──
                            item(key = "hero") {
                                BrowseSection(visible = heroItems.isNotEmpty()) {
                                    BrowseHero(
                                        items = heroItems,
                                        onOpen = { anime -> onNavigate(AnimeDetailsKey.AniList(anime.id)) },
                                    )
                                }
                            }

                            // ── Continue Watching ──
                            item(key = "cw_header") {
                                BrowseSection(visible = continueWatching.isNotEmpty()) {
                                    BrowseSectionHeader("Continue Watching")
                                }
                            }
                            item(key = "cw_carousel") {
                                BrowseSection(visible = continueWatching.isNotEmpty()) {
                                    ContinueWatchingCarousel(
                                        items = continueWatching,
                                        onNavigate = onNavigate,
                                        onPlay = onPlayContinueWatching,
                                    )
                                }
                            }

                            // ── Trending Now ──
                            item(key = "trending_header") {
                                BrowseSection(visible = s.anime.isNotEmpty()) {
                                    BrowseSectionHeader("Trending Now")
                                }
                            }
                            item(key = "trending_carousel") {
                                BrowseSection(visible = s.anime.isNotEmpty()) {
                                    AnimeCarousel(anime = s.anime) { anime ->
                                        onNavigate(AnimeDetailsKey.AniList(anime.id))
                                    }
                                }
                            }

                            // ── Popular ──
                            item(key = "popular_header") {
                                BrowseSection(visible = popular.isNotEmpty()) {
                                    BrowseSectionHeader("Popular")
                                }
                            }
                            item(key = "popular_carousel") {
                                BrowseSection(visible = popular.isNotEmpty()) {
                                    AnimeCarousel(anime = popular) { anime ->
                                        onNavigate(AnimeDetailsKey.AniList(anime.id))
                                    }
                                }
                            }

                            // ── Top Rated ──
                            item(key = "top_rated_header") {
                                BrowseSection(visible = topRated.isNotEmpty()) {
                                    BrowseSectionHeader("Top Rated")
                                }
                            }
                            item(key = "top_rated_carousel") {
                                BrowseSection(visible = topRated.isNotEmpty()) {
                                    AnimeCarousel(anime = topRated) { anime ->
                                        onNavigate(AnimeDetailsKey.AniList(anime.id))
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/**
 * D-257: preloads a section's images into Coil's memory + disk cache as soon
 * as the data arrives, so covers are already decoded when the user first
 * scrolls a carousel into view (device feedback: "I have to wait for them to
 * load when I see them for the first time").
 *
 * Sizing note (Coil 3.0.4): the memory-cache key excludes size when a request
 * has no transformations, and AsyncImage resolves with INEXACT precision —
 * so a preload at the card's exact pixel dimensions is a memory-cache HIT for
 * the composable later. Emits no UI.
 */
@Composable
private fun SectionPreloader(urls: List<String?>, width: Dp, height: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cleaned = remember(urls) { urls.filter { !it.isNullOrBlank() }.distinct() }
    LaunchedEffect(cleaned) {
        if (cleaned.isEmpty()) return@LaunchedEffect
        val w = with(density) { width.roundToPx() }
        val h = with(density) { height.roundToPx() }
        cleaned.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(w, h)
                    .build(),
            )
        }
    }
}

/**
 * Wraps a section's content so it fades + expands in when its data arrives
 * (D-253: sections never pop in — CORE_RULES §22 "no instant cuts").
 */
@Composable
private fun BrowseSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
    ) {
        content()
    }
}

/**
 * D-253: error state with a Retry action — no more dead-end text.
 */
@Composable
private fun BrowseErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = "Failed to load",
                description = message,
                icon = Icons.Filled.Warning,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Retry",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
