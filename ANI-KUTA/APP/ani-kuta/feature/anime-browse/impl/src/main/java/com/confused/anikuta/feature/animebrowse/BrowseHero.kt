package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.key
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
 * D-257: Browse hero v3 — a padded, rounded, cinematic banner card (evolution
 * of D-256 after device feedback: the hero "looks way too ugly, like a rigid
 * kind of format", the banner felt "forced into a square vibe", and the
 * auto-scroll wraparound was "not smooth, not animated").
 *
 * Changes vs D-256:
 * - **Wider banner aspect**: the hero is now an inset 16:9 card (16dp side
 *   margins, 20dp rounded corners, 1dp border — the standard card language)
 *   instead of a full-bleed ~1.2:1 block. 16:9 matches AniList's native banner
 *   ratio, so the artwork shows with minimal cropping.
 * - **Smooth auto-advance (infinite pager)**: the pager is virtually
 *   circular — `pageCount = size × 200`, starting at `size × 100` — so every
 *   auto-advance moves FORWARD exactly one page with a 600ms tween. The old
 *   wraparound scrolled backwards through all pages (the "ugly" glitch).
 * - Cover poster + banner layered as before (D-256 anatomy), rank pill,
 *   2-line title, score/eps/year meta and genre chips, page dots BELOW the
 *   card (centered — never collides with the text block on narrow screens).
 *
 * ```
 * ┌─── 16dp ──────────────────────────────────┐
 * │ ╭───────────────────────────────────────╮ │      ← 16:9, 20dp corners, 1dp border
 * │ │            banner (crop)              │ │
 * │ │        bottom-heavy scrim             │ │
 * │ │ ┌────┐  #1 TRENDING                   │ │
 * │ │ │cover│  Title (18sp ExtraBold)       │ │
 * │ │ │2:3 │  ★ 85 · 24 eps · 2024          │ │
 * │ │ └────┘  [Action] [Comedy] [+2]        │ │
 * │ ╰───────────────────────────────────────╯ │
 * │                  ● ○ ○ ○ ○                │      ← dots below the card
 * └───────────────────────────────────────────┘
 * ```
 *
 * A single hero item renders as the same card WITHOUT pager mechanics.
 */
@Composable
internal fun BrowseHero(
    items: List<AniListAnime>,
    onOpen: (AniListAnime) -> Unit,
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        if (items.size == 1) {
            // Single item — no pager mechanics (D-257: avoids 200 duplicate
            // virtual pages for a one-item list).
            HeroCard(anime = items.first(), rank = 1, onClick = { onOpen(items.first()) })
        } else {
            // Infinite pager: a large virtual page count with the display index
            // taken modulo the real item count. Auto-advance always steps
            // FORWARD (+1) — the pager never has to sweep backwards to wrap
            // around, which is what made the old auto-scroll look glitchy.
            // key(items.size) recreates the state when the list size changes so
            // the initial page always maps back to display index 0.
            key(items.size) {
                val virtualCount = items.size * HERO_VIRTUAL_COPIES
                val pagerState = rememberPagerState(
                    initialPage = items.size * (HERO_VIRTUAL_COPIES / 2),
                    pageCount = { virtualCount },
                )
                val displayIndex = pagerState.currentPage % items.size

                // Auto-advance: 6s per page; skips a tick if the user is
                // mid-drag; always moves to the NEXT virtual page (smooth
                // forward tween — never a backwards wraparound sweep).
                LaunchedEffect(pagerState.currentPage, virtualCount) {
                    delay(HERO_AUTO_ADVANCE_MS)
                    if (!pagerState.isScrollInProgress &&
                        pagerState.currentPage < virtualCount - 1
                    ) {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(
                                durationMillis = HERO_ADVANCE_ANIM_MS,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val pageDisplayIndex = page % items.size
                    HeroCard(
                        anime = items[pageDisplayIndex],
                        rank = pageDisplayIndex + 1,
                        onClick = { onOpen(items[pageDisplayIndex]) },
                    )
                }

                // Page dots — centered BELOW the card (the active dot elongates
                // into a pill). Uses the display index, not the virtual page.
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(items.size) { index ->
                        HeroDot(active = displayIndex == index)
                    }
                }
            }
        }
    }
}

/** One page dot — the active dot elongates into a 16dp pill (animated). */
@Composable
private fun HeroDot(active: Boolean) {
    val dotWidth by animateDpAsState(
        targetValue = if (active) 16.dp else 6.dp,
        animationSpec = tween(300),
        label = "heroDot",
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = dotWidth, height = 6.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ),
    )
}

/** A single hero card — banner backdrop + scrim + cover-and-text foreground. */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    rank: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(HeroCardShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                HeroCardShape,
            )
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
        // any artwork). A subtle top scrim keeps the card's top edge soft. ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.82f),
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Cover poster (2:3, matches the carousel card language).
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        RoundedCornerShape(10.dp),
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
                        fontSize = 24.sp,
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
                // Title — 18sp ExtraBold, up to 2 lines (white: it always sits
                // on the dark scrim, regardless of theme).
                Text(
                    text = anime.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 21.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(7.dp))
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

/** A genre tag chip — translucent dark pill (readable on any artwork). */
@Composable
private fun HeroGenreChip(label: String, emphasized: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (emphasized) {
            Color.Black.copy(alpha = 0.55f)
        } else {
            Color.Black.copy(alpha = 0.40f)
        },
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Bold,
            color = Color.White.copy(alpha = if (emphasized) 1f else 0.8f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The hero card's outer shape (20dp rounded — the standard card language). */
private val HeroCardShape = RoundedCornerShape(20.dp)

private const val HERO_AUTO_ADVANCE_MS = 6_000L

/** Duration of the auto-advance slide animation. */
private const val HERO_ADVANCE_ANIM_MS = 600

/** Genre chips shown in the hero (overflow folds into a "+N" chip). */
private const val HERO_MAX_GENRE_CHIPS = 3

/**
 * Number of virtual copies for the infinite pager. 200 keeps the forward
 * runway effectively endless (~100 minutes of auto-advance) while staying a
 * trivial integer for the modulo math.
 */
private const val HERO_VIRTUAL_COPIES = 200
