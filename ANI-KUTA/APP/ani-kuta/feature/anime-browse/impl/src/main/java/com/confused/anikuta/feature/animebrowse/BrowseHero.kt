package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay

/**
 * D-253: Browse hero — a full-bleed edge-to-edge auto-advancing pager over the
 * top trending anime with banner images (evolution of D-249's single static
 * hero card).
 *
 * - 260dp tall, no horizontal padding — the banner bleeds to the screen edges
 *   under the pinned CollapsingHeader (modern streaming-app hero pattern).
 * - Auto-advances every 6s with wraparound; the timer restarts on every page
 *   change (LaunchedEffect keyed on currentPage) and skips a tick if the user
 *   is mid-drag. A single hero item renders without pager mechanics.
 * - Each page: rank pill (#N TRENDING, solid primary per the D-215 EP-tag
 *   recipe), 24sp ExtraBold title, meta row (score · eps · year — integer
 *   score, unified with the Library badge language), genre pills.
 * - Page dots bottom-end: the active dot elongates to a 16dp pill (animated).
 */
@Composable
internal fun BrowseHero(
    items: List<AniListAnime>,
    onOpen: (AniListAnime) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-advance: 6s per page, wrap around, skip if the user is dragging.
    if (items.size > 1) {
        LaunchedEffect(pagerState.currentPage, items.size) {
            delay(HERO_AUTO_ADVANCE_MS)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            HeroCard(
                anime = items[page],
                rank = page + 1,
                onClick = { onOpen(items[page]) },
            )
        }

        // Page dots — active dot elongates into a pill.
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val active = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 16.dp else 6.dp,
                        animationSpec = tween(300),
                        label = "heroDot$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            ),
                    )
                }
            }
        }
    }
}

/** A single hero page — banner image + scrim + bottom-start content. */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    rank: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
    ) {
        // Banner image (fallback to cover when an item has no banner).
        AsyncImage(
            model = anime.bannerImage ?: anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom-heavy gradient scrim — strong enough for readable text over
        // any artwork (D-253: stronger than D-249's 0.75/0.95 stops).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        // Content overlay (bottom-start).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Rank pill — solid primary (D-215 EP-tag recipe). No emoji
            // (NavIcons rule: Material vectors only, never emojis).
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = "#$rank TRENDING",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Title (large, bold, 1 line).
            Text(
                text = anime.displayName,
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Meta row: score · episodes · year.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                anime.averageScore?.let { score ->
                    // Integer AniList score — unified with the Library score badge.
                    Text(
                        text = "★ $score",
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
            }
            Spacer(Modifier.height(6.dp))
            // Genre pills (first 3) — Info pill recipe (D-215).
            anime.genres?.takeIf { it.isNotEmpty() }?.take(3)?.let { genres ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    genres.forEach { genre ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = genre,
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
                }
            }
        }
    }
}

private const val HERO_AUTO_ADVANCE_MS = 6_000L
