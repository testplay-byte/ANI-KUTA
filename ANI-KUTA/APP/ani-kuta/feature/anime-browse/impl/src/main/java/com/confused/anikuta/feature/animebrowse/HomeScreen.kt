package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.EmptyState
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.bottomBarClearance // round 148
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.debugapi.DebugContext
import com.confused.anikuta.core.debugapi.LocalDebugContextUpdater
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * HOME screen — the "My screen" (round 148; spec:
 * DOCUMENTATION/PROJECT-MAP/11-HOME-MY-SCREEN-SPEC.md). Replaces the Browse
 * tab's surface; BrowseScreen stays in the module and its hero/card/section
 * composables are REUSED here (the home is a SIBLING of Browse, not a new
 * species).
 *
 * Structure: CollapsingHeader("Home") → pull-to-refresh → LazyColumn with
 * keyed items — the greeting + hero, then the spec-order section stack:
 * Continue watching, What to watch next, Releasing soon, Recently released,
 * Trending now, Popular, Top rated. Each rail is a LazyRow (12dp gutters,
 * 10dp spacing) whose cards reuse the Browse card anatomy (128dp 2:3 poster,
 * hairline 1dp outlineVariant@60% border, 12dp radius, no rest-shadow,
 * press-scale 0.95). Empty rails skip their section (no empty stubs).
 *
 * Taste rules (spec §3): equal edges, one hero (the ONE moving thing — the
 * 12s auto-advance), section headers 14sp ExtraBold primary level across a
 * row, ~16dp vertical rhythm, each section fades/expands in ONCE
 * (fadeIn + expandVertically tween 300 — the BrowseSection pattern),
 * skeleton loading, retry error state.
 *
 * Routing: every [NavKey] passes up through [onNavigate] (the ad gate runs in
 * MainActivity — `is AnimeDetailsKey -> navigateToDetails(navKey)` else direct
 * push, preserved verbatim). The personal rails tap into
 * [onOpenDetailsByMainId] — MainActivity's `openDetailsByMainId` semantics
 * (works even if the content left the library; opens at that episode).
 *
 * CORE_RULES §22: quiet motion. §23: reactive state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (NavKey) -> Unit,
    onOpenDetailsByMainId: (String, Int?) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val heroItems by viewModel.heroItems.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val popular by viewModel.popular.collectAsState()
    val topRated by viewModel.topRated.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val nextUp by viewModel.nextUp.collectAsState()
    val releasingSoon by viewModel.releasingSoon.collectAsState()
    val recentlyReleased by viewModel.recentlyReleased.collectAsState()

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = LocalDebugContextUpdater.current
    val homeCtx = remember(
        state, trending.size, popular.size, topRated.size,
        continueWatching.size, nextUp.size, releasingSoon.size, recentlyReleased.size,
    ) {
        DebugContext(
            screenName = "Home",
            screenData = mapOf(
                "state" to (state::class.simpleName ?: "Unknown"),
                "trendingCount" to trending.size.toString(),
                "popularCount" to popular.size.toString(),
                "topRatedCount" to topRated.size.toString(),
                "continueCount" to continueWatching.size.toString(),
                "nextUpCount" to nextUp.size.toString(),
                "releasingSoonCount" to releasingSoon.size.toString(),
                "recentlyReleasedCount" to recentlyReleased.size.toString(),
                "isRefreshing" to isRefreshing.toString(),
            ),
        )
    }
    LaunchedEffect(homeCtx) { updateDebugContext(homeCtx) }
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
            CollapsingHeader(title = "Home", collapsed = collapsed)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = ptrState,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state) {
                    HomeState.Loading -> BrowseSkeleton()
                    is HomeState.Error -> HomeErrorState(
                        message = (state as HomeState.Error).message,
                        onRetry = { viewModel.refresh() },
                    )
                    HomeState.Content -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // ── Coil warm-up — the SectionPreloader contract
                            // (exact render sizes + no transformations →
                            // memory-cache hits on first compose): the hero
                            // posters, the three discovery rails, and the
                            // personal rails' locally-stored covers, all at
                            // the 128×192 card size the rails render at. ──
                            SectionPreloader(
                                urls = heroItems.map { it.coverUrl },
                                width = 84.dp,
                                height = 126.dp,
                            )
                            SectionPreloader(urls = trending.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = popular.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = topRated.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = continueWatching.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = nextUp.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = releasingSoon.map { it.coverUrl }, width = 128.dp, height = 192.dp)
                            SectionPreloader(urls = recentlyReleased.map { it.coverUrl }, width = 128.dp, height = 192.dp)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomBarClearance(90.dp)),
                            ) {
                                // ── The greeting + THE HERO (the one moving
                                // thing — 12s auto-advance inside BrowseHero).
                                // Nothing else renders until the hero does. ──
                                item(key = "hero") {
                                    HomeHeroBlock(
                                        heroItems = heroItems,
                                        onOpen = { anime ->
                                            onNavigate(AnimeDetailsKey.AniList(anime.id))
                                        },
                                    )
                                }

                                // ── Continue watching (spec §1 #1) ──
                                item(key = "continue_watching") {
                                    BrowseSection(visible = continueWatching.isNotEmpty()) {
                                        HomeRailSection(title = "Continue watching", count = continueWatching.size) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                items(continueWatching, key = { it.mainId }) { item ->
                                                    HomeProgressCard(
                                                        title = item.title,
                                                        coverUrl = item.coverUrl,
                                                        metaLine = buildString {
                                                            append("EP ")
                                                            append(item.episodeNumber)
                                                            item.totalEpisodes?.let { total ->
                                                                append(" of ")
                                                                append(total)
                                                            }
                                                        },
                                                        progressFraction = item.progressFraction,
                                                        onClick = {
                                                            onOpenDetailsByMainId(item.mainId, item.episodeNumber)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── What to watch next (spec §1 #2, derived) ──
                                item(key = "next_up") {
                                    BrowseSection(visible = nextUp.isNotEmpty()) {
                                        HomeRailSection(title = "What to watch next", count = nextUp.size) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                items(nextUp, key = { it.mainId }) { item ->
                                                    HomeProgressCard(
                                                        title = item.title,
                                                        coverUrl = item.coverUrl,
                                                        metaLine = if (item.isAiringNext && item.subtitle != null) {
                                                            "EP ${item.episodeNumber} · ${item.subtitle}"
                                                        } else {
                                                            "EP ${item.episodeNumber}"
                                                        },
                                                        progressFraction = null,
                                                        onClick = {
                                                            onOpenDetailsByMainId(item.mainId, item.episodeNumber)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── Releasing soon (spec §1 #3) ──
                                item(key = "releasing_soon") {
                                    BrowseSection(visible = releasingSoon.isNotEmpty()) {
                                        HomeRailSection(title = "Releasing soon", count = releasingSoon.size) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                items(releasingSoon, key = { "${it.mainId}|${it.scheduledAt}" }) { item ->
                                                    HomeProgressCard(
                                                        title = item.title,
                                                        coverUrl = item.coverUrl,
                                                        metaLine = item.subtitle?.let { sub ->
                                                            "EP ${item.episodeNumber} · $sub"
                                                        } ?: "EP ${item.episodeNumber}",
                                                        progressFraction = null,
                                                        onClick = {
                                                            onOpenDetailsByMainId(item.mainId, item.episodeNumber)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── Recently released (spec §1 #4) ──
                                item(key = "recently_released") {
                                    BrowseSection(visible = recentlyReleased.isNotEmpty()) {
                                        HomeRailSection(title = "Recently released", count = recentlyReleased.size) {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                items(recentlyReleased, key = { it.mainId }) { item ->
                                                    HomeProgressCard(
                                                        title = item.title,
                                                        coverUrl = item.coverUrl,
                                                        metaLine = "EP ${item.episodeNumber}",
                                                        progressFraction = null,
                                                        onClick = {
                                                            onOpenDetailsByMainId(item.mainId, item.episodeNumber)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── Trending now (spec §1 #5, home_trending) ──
                                item(key = "trending_now") {
                                    BrowseSection(visible = trending.isNotEmpty()) {
                                        HomeRailSection(title = "Trending now", count = trending.size) {
                                            AnimeCarousel(
                                                anime = trending,
                                                sectionKey = "home_trending",
                                                onClick = { anime, transitionKey ->
                                                    onNavigate(
                                                        AnimeDetailsKey.AniList(
                                                            anime.id,
                                                            coverUrl = anime.coverUrl,
                                                            title = anime.displayName,
                                                            transitionKey = transitionKey,
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                // ── Popular (spec §1 #6, home_popular) ──
                                item(key = "popular") {
                                    BrowseSection(visible = popular.isNotEmpty()) {
                                        HomeRailSection(title = "Popular", count = popular.size) {
                                            AnimeCarousel(
                                                anime = popular,
                                                sectionKey = "home_popular",
                                                onClick = { anime, transitionKey ->
                                                    onNavigate(
                                                        AnimeDetailsKey.AniList(
                                                            anime.id,
                                                            coverUrl = anime.coverUrl,
                                                            title = anime.displayName,
                                                            transitionKey = transitionKey,
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                // ── Top rated (spec §1 #7, the tail section) ──
                                item(key = "top_rated") {
                                    BrowseSection(visible = topRated.isNotEmpty()) {
                                        HomeRailSection(title = "Top rated", count = topRated.size) {
                                            AnimeCarousel(
                                                anime = topRated,
                                                sectionKey = "home_top_rated",
                                                onClick = { anime, transitionKey ->
                                                    onNavigate(
                                                        AnimeDetailsKey.AniList(
                                                            anime.id,
                                                            coverUrl = anime.coverUrl,
                                                            title = anime.displayName,
                                                            transitionKey = transitionKey,
                                                        ),
                                                    )
                                                },
                                            )
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
 * The first screen's owner: the greeting line + the hero. The hero reuses
 * [BrowseHero] (the Browse tab's 148dp splash hero, 12s auto-advance) with the
 * trending→popular fallback items the VM derives. The air below is the
 * section rhythm — the next section starts at its own header's 14dp.
 */
@Composable
private fun HomeHeroBlock(
    heroItems: List<AniListAnime>,
    onOpen: (AniListAnime) -> Unit,
) {
    val greeting = remember {
        when (java.time.LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = greeting,
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        BrowseSection(visible = heroItems.isNotEmpty()) {
            BrowseHero(items = heroItems, onOpen = onOpen)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * One rail section: the header (14sp ExtraBold primary, level across the row,
 * with a light count) + the row content. The reveal itself is [BrowseSection]
 * (fadeIn + expandVertically, tween 300) — it wraps the whole section once
 * its data arrives, exactly the Browse idiom.
 */
@Composable
private fun HomeRailSection(
    title: String,
    count: Int?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (count != null && count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp),
                    maxLines = 1,
                )
            }
        }
        content()
    }
}

/**
 * The personal-rail card — the BrowseAnimeCard anatomy (128dp 2:3 poster,
 * 12dp corners, hairline 1dp outlineVariant@60% border, no rest-shadow,
 * press-scale 0.95) with the home's own meta zone: the title, then a thin
 * 3dp progress bar (only when a fraction is known), then the "EP N" line.
 */
@Composable
private fun HomeProgressCard(
    title: String,
    coverUrl: String?,
    metaLine: String,
    progressFraction: Float?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "homeCardScale",
    )
    val coverShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(128.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Cover — the barely-there card language (hairline border, no shadow).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(coverShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    coverShape,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Title (1 line).
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The thin 3dp progress bar — only for rows that actually carry a
        // fraction (the Continue rail); a null fraction renders no bar.
        if (progressFraction != null) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0.04f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        // The "EP N" line (+ the card's own subtitle where the rail has one).
        Text(
            text = metaLine,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Home's error state — the retry EmptyState idiom (no dead-end text). Reached
 * only when EVERYTHING failed: all three discovery sections settled with
 * nothing cached AND every personal rail empty.
 */
@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit) {
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
