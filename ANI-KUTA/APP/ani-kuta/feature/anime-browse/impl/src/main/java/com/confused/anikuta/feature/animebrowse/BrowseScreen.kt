package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
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
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * Browse screen — the home tab (D-249 UI overhaul).
 *
 * Layout: CollapsingHeader → pull-to-refresh → scrollable LazyColumn containing:
 *  1. Hero banner (top trending anime with banner image, gradient overlay, meta)
 *  2. Continue Watching carousel (if in-progress episodes exist)
 *  3. Trending Now (horizontal card carousel)
 *  4. Popular (horizontal card carousel)
 *  5. Top Rated (horizontal card carousel)
 *
 * Each section loads cache-first (6h TTL) + fetches independently from AniList.
 * Cards feature press-scale animation, score badge overlays, and clean typography
 * per the app design language (lime accent, warm darks, Roboto, translucent surfaces).
 *
 * CORE_RULES §22: smooth animations. §23: reactive state.
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
    val hero by viewModel.hero.collectAsState()

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
                    is BrowseState.Loading -> LoadingScreen()
                    is BrowseState.Error -> ErrorScreen(s.message)
                    is BrowseState.Success -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp),
                        ) {
                            // ── Hero Banner ──
                            if (hero != null) {
                                item(key = "hero") {
                                    HeroBanner(
                                        anime = hero!!,
                                        onClick = { onNavigate(AnimeDetailsKey.AniList(hero!!.id)) },
                                    )
                                }
                            }

                            // ── Continue Watching ──
                            if (continueWatching.isNotEmpty()) {
                                item(key = "cw_header") { SectionHeader("Continue Watching") }
                                item(key = "cw_carousel") {
                                    ContinueWatchingCarousel(
                                        items = continueWatching,
                                        onNavigate = onNavigate,
                                        onPlay = onPlayContinueWatching,
                                    )
                                }
                            }

                            // ── Trending Now ──
                            if (s.anime.isNotEmpty()) {
                                item(key = "trending_header") { SectionHeader("Trending Now") }
                                item(key = "trending_carousel") {
                                    AnimeCarousel(
                                        anime = s.anime,
                                    ) { anime -> onNavigate(AnimeDetailsKey.AniList(anime.id)) }
                                }
                            }

                            // ── Popular ──
                            if (popular.isNotEmpty()) {
                                item(key = "popular_header") { SectionHeader("Popular") }
                                item(key = "popular_carousel") {
                                    AnimeCarousel(
                                        anime = popular,
                                    ) { anime -> onNavigate(AnimeDetailsKey.AniList(anime.id)) }
                                }
                            }

                            // ── Top Rated ──
                            if (topRated.isNotEmpty()) {
                                item(key = "top_rated_header") { SectionHeader("Top Rated") }
                                item(key = "top_rated_carousel") {
                                    AnimeCarousel(
                                        anime = topRated,
                                    ) { anime -> onNavigate(AnimeDetailsKey.AniList(anime.id)) }
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

// ── Hero Banner ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroBanner(anime: AniListAnime, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        // Banner image (fallback to cover).
        AsyncImage(
            model = anime.bannerImage ?: anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Gradient scrim (bottom-heavy, like DetailsScreen's banner).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        // Content overlay (bottom-aligned).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // "Trending #1" badge.
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "🔥 #1 TRENDING",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            // Title (large, bold, 1 line).
            Text(
                text = anime.displayName,
                fontFamily = RobotoFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // Meta row: score · episodes · year · genres.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                anime.averageScore?.let { score ->
                    Text(
                        text = "★ ${(score / 10.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it) }}",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                anime.episodes?.let { eps ->
                    Text(
                        text = "· $eps eps",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                anime.seasonYear?.let { year ->
                    Text(
                        text = "· $year",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // First 2 genres as subtle pills.
                anime.genres?.take(2)?.forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = genre,
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Section header ──────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

// ── Anime carousel (horizontal card row) ───────────────────────────────────────

@Composable
private fun AnimeCarousel(
    anime: List<AniListAnime>,
    onClick: (AniListAnime) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            AnimeCarouselCard(item, onClick)
        }
    }
}

@Composable
private fun AnimeCarouselCard(anime: AniListAnime, onClick: (AniListAnime) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
            ),
    ) {
        // Cover with score badge overlay.
        Box {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            // Score badge (bottom-start, translucent dark pill).
            anime.averageScore?.let { score ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                ) {
                    Text(
                        text = "★ ${(score / 10.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it) }}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Title (1 line).
        Text(
            text = anime.displayName,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Subtitle: year · status.
        val subtitle = listOfNotNull(
            anime.seasonYear?.toString(),
            anime.status?.lowercase()?.replaceFirstChar { it.uppercase() },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Loading / Error ─────────────────────────────────────────────────────────────

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Failed to load",
                fontFamily = RobotoFamily,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ── Continue Watching carousel (D-248 direct-play) ─────────────────────────────

@Composable
private fun ContinueWatchingCarousel(
    items: List<ContinueWatchingItem>,
    onNavigate: (NavKey) -> Unit,
    onPlay: ((ContinueWatchingItem) -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { "${it.mainId}|${it.episodeNumber}" }) { item ->
            ContinueWatchingCard(item = item, onClick = {
                if (onPlay != null) {
                    onPlay(item)
                } else if (item.anilistId != null) {
                    onNavigate(AnimeDetailsKey.AniList(item.anilistId, autoPlayEpisode = item.episodeNumber))
                } else if (item.sourceId > 0 && item.animeUrl.isNotBlank()) {
                    onNavigate(AnimeDetailsKey.Extension(item.sourceId, item.animeUrl, item.title, null, autoPlayEpisode = item.episodeNumber))
                }
            })
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            val imageUrl = item.thumbnailUrl ?: item.coverUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = item.title.firstOrNull()?.uppercase() ?: "?",
                    fontFamily = RobotoFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            ) {
                Text(
                    text = "EP ${item.episodeNumber}",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (item.progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.title,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
