package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
 * D-256: Browse hero v2 — full-bleed edge-to-edge auto-advancing pager over
 * the top trending anime (evolution of D-253 after user feedback: "showing the
 * cover and the banner together properly… showing the relevant tags properly").
 *
 * Each page layers BOTH images — the banner as the ambient backdrop and the
 * cover poster as the anchor element (classic AniList/Netflix hero anatomy):
 *
 * ```
 * ┌──────────────────────────────────────────────┐
 * │              banner (crop, full-bleed)       │
 * │          bottom-heavy gradient scrim         │
 * │                                              │
 * │ ┌────────┐   #1 TRENDING                     │
 * │ │        │   Title (20sp ExtraBold, 2 lines) │
 * │ │ cover  │   ★ 85 · 24 eps · 2024            │
 * │ │  2:3   │   [Action] [Comedy] [+2]          │
 * │ └────────┘                       ● ○ ○ ○ ○   │
 * └──────────────────────────────────────────────┘
 * ```
 *
 * - 300dp tall; poster 80×120dp (2:3, 12dp corners, 1dp border) bottom-aligned
 *   with the text block; 16dp page padding.
 * - Genre tags: proper chips (D-215 Info-pill recipe) — up to 3, with a "+N"
 *   overflow chip when there are more genres.
 * - Auto-advances every 6s with wraparound; the timer restarts on every page
 *   change and skips a tick if the user is mid-drag. A single hero item
 *   renders without pager mechanics.
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

    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
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

/** A single hero page — banner backdrop + scrim + cover-and-text foreground. */
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
        // ── Layer 1: banner as the ambient backdrop (falls back to the cover). ──
        AsyncImage(
            model = anime.bannerImage ?: anime.coverUrl,
            contentDescription = anime.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Layer 2: bottom-heavy gradient scrim (strong enough for text over
        // any artwork). A subtle top scrim keeps the status-bar edge soft. ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
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

        // ── Layer 3: foreground — cover poster + text block, bottom-aligned. ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Cover poster (2:3, matches the carousel card language).
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        RoundedCornerShape(12.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                if (anime.coverUrl != null) {
                    AsyncImage(
                        model = anime.coverUrl,
                        contentDescription = null, // decorative — title carries the meaning
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = anime.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontFamily = RobotoFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Text block.
            Column(modifier = Modifier.weight(1f)) {
                // Rank pill — solid primary (D-215 EP-tag recipe). No emoji.
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "#$rank TRENDING",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Title — 20sp ExtraBold, up to 2 lines.
                Text(
                    text = anime.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // Meta row: score · episodes · year.
                val metaParts = buildList {
                    anime.averageScore?.let { add("★ $it") }
                    anime.episodes?.let { add("$it eps") }
                    anime.seasonYear?.let { add(it.toString()) }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  ·  "),
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Genre tag chips — up to 3 + a "+N" overflow chip.
                anime.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        genres.take(HERO_MAX_GENRE_CHIPS).forEach { genre ->
                            HeroGenreChip(label = genre)
                        }
                        if (genres.size > HERO_MAX_GENRE_CHIPS) {
                            HeroGenreChip(
                                label = "+${genres.size - HERO_MAX_GENRE_CHIPS}",
                                emphasized = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A genre tag chip — the D-215 Info-pill recipe (surfaceVariant @ 65%). */
@Composable
private fun HeroGenreChip(label: String, emphasized: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val HERO_AUTO_ADVANCE_MS = 6_000L

/** Genre chips shown in the hero (overflow folds into a "+N" chip). */
private const val HERO_MAX_GENRE_CHIPS = 3
