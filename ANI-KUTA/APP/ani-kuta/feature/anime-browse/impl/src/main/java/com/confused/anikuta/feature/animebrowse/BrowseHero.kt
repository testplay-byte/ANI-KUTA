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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.color.rememberCoverDominantColor
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * D-277: Browse hero v4 — full uncropped banner + palette-gradient content zone.
 *
 * Replaces D-275's "16:9 cropped banner + blurred-cover strip", which the user
 * found ugly on device:
 * - the forced `aspectRatio(16f/9f)` + `ContentScale.Crop` discarded ~half of
 *   AniList's natively ~3:1 banner (only the center horizontal band showed);
 * - the "blurred cover bottom strip" (`Modifier.blur(8.dp).scale(1.15f)`)
 *   had a hard rectangular seam against the sharp banner above AND sat directly
 *   behind the foreground cover poster (visually redundant — same image twice).
 *
 * **New anatomy** (same 16dp inset / 20dp corners / 1dp border card language;
 * ratio widened from 16:9 to `HERO_CARD_RATIO` = 1.2:1 so a full banner + a
 * content zone both fit):
 * - **Full banner, uncropped**: the banner renders with `ContentScale.Fit` +
 *   `Alignment.TopCenter` so the WHOLE banner shows — no crop. AniList banners
 *   (~3:1) occupy the top ~40% of the card; the remaining space is the gradient.
 * - **Palette-derived gradient (NOT a blurred cover)**: the cover's dominant
 *   color is extracted via [rememberCoverDominantColor] (Coil + AndroidX
 *   Palette, DI-bound [com.confused.anikuta.core.designsystem.color.CoverColorExtractor]),
 *   then darkened with [lerp] toward black for white-text contrast. The gradient
 *   goes transparent (over the banner) → coverColor (junction) → darkCoverColor
 *   (content zone). This is "colors of the cover image", not a blurred copy —
 *   exactly what the user asked for.
 * - **Smooth blend into the top banner**: the gradient's transparent zone
 *   covers the banner's bottom ~45%, ramping to solid coverColor — so the
 *   banner's bottom edge feathers into the solid color with no hard seam
 *   (the details-page `DetailBanner` recipe adapted: its
 *   `Brush.verticalGradient([Black@0.2, Transparent, background])` becomes
 *   here `[Transparent, Transparent, coverColor, darkCoverColor]`).
 * - If the banner URL is missing, the gradient alone forms the header
 *   (coverColor top → darkCoverColor bottom) — still on-palette, still
 *   cinematic. The foreground cover poster (Layer 3) still shows the cover.
 *
 * The pager mechanics (infinite virtual copies, 12s auto-advance, restart-proof
 * loop, dots reading settledPage) are UNCHANGED from D-262 — only the card's
 * internals were redone.
 *
 * ```
 * ┌──────────────────────────────────────────────┐  ← HERO_CARD_RATIO (1.2:1),
 * │  ▓▓▓▓▓▓ FULL BANNER (Fit, TopCenter) ▓▓▓▓▓▓ │     20dp corners, 1dp border
 * │  ░░░░░░ banner-bottom feather (transparent) ░│     — no crop, whole banner
 * │ ┌────┐  #1 TRENDING                          │     coverColor→darkCoverColor
 * │ │cover│  Title (18sp ExtraBold, white)      │     (dark = readable on any hue)
 * │ │2:3 │  ★ 85 · 24 eps · 2024               │
 * │ └────┘  [Action] [Comedy] [+2]               │
 * └──────────────────────────────────────────────┘
 * ```
 *
 * A single hero item renders as the same card WITHOUT pager mechanics.
 *
 * History: D-256 (first hero) → D-257 (wider banner) → D-262 (smooth
 * auto-advance) → D-275 (sharp banner + blurred-cover strip) → **D-277** (full
 * uncropped banner + palette gradient — this version).
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
                // D-262: dots read settledPage (update on settle, not mid-slide).
                val displayIndex = pagerState.settledPage % items.size

                // Auto-advance: 12s per page (D-262 device feedback — was 6s).
                // D-262 RESTART-PROOF loop: keyed on (pagerState, virtualCount)
                // — NEVER on currentPage (which flipped at the 50% scroll
                // crossing DURING animateScrollToPage, cancelling the effect
                // mid-flight and leaving the pager stuck between two banners
                // with no snap). The while(true) keeps firing across ticks;
                // CancellationException (user grabbed the pager, etc.) is
                // caught → snap to the nearest whole page so the card is
                // always aligned. The effect is only cancelled on dispose
                // (coroutineContext.isActive == false), in which case we
                // rethrow and let it propagate.
                LaunchedEffect(pagerState, virtualCount) {
                    while (true) {
                        delay(HERO_AUTO_ADVANCE_MS)
                        if (pagerState.isScrollInProgress) continue
                        if (pagerState.currentPage >= virtualCount - 1) continue
                        try {
                            pagerState.animateScrollToPage(
                                page = pagerState.currentPage + 1,
                                animationSpec = tween(
                                    durationMillis = HERO_ADVANCE_ANIM_MS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        } catch (e: CancellationException) {
                            if (!currentCoroutineContext().isActive) throw e
                            // Programmatic animation interrupted (e.g. user
                            // grabbed the pager). Never rest between banners —
                            // wait for the gesture to end, then snap to the
                            // nearest whole page so the card is always aligned.
                            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                            withContext(NonCancellable) {
                                if (!pagerState.isScrollInProgress &&
                                    abs(pagerState.currentPageOffsetFraction) > 0.01f
                                ) {
                                    pagerState.scrollToPage(pagerState.currentPage)
                                }
                            }
                        }
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
                // into a pill). Uses the settled display index, not the virtual
                // page.
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

/**
 * A single hero card — full uncropped banner + palette-gradient content zone
 * (D-277).
 *
 * Layering (bottom → top render order):
 * 1. **Base + banner** — solid [coverColor] base (shows wherever the gradient
 *    is transparent) + the full banner via `AsyncImage(ContentScale.Fit,
 *    TopCenter)` (no crop). Skipped when no banner URL → gradient-only header.
 * 2. **Palette gradient overlay** — `Brush.verticalGradient([Transparent,
 *    Transparent, coverColor, darkCoverColor])`. The transparent zone lets the
 *    banner read fully; the ramp to solid coverColor feathers the banner's
 *    bottom into the solid color (no hard seam); the bottom darkCoverColor is
 *    where the white text sits.
 * 3. **Foreground Row** — cover poster (84×126) + rank pill + title + meta +
 *    chips, bottom-aligned (on the dark zone).
 *
 * `coverColor` comes from [rememberCoverDominantColor] (extracted once per
 * cover URL, cached by `produceState`'s key). `darkCoverColor` =
 * `lerp(coverColor, Black, 0.55)` — guaranteed dark enough for white text on
 * any extracted hue/lightness.
 */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    rank: Int,
    onClick: () -> Unit,
) {
    // D-277: extract the cover's dominant color for the gradient. Mid-tone,
    // theme-agnostic (sat ≥ 0.40, lightness ∈ [0.40, 0.65]). Null on blank URL
    // or extraction failure → fall back to a neutral surface color so the hero
    // still renders (with a neutral gradient) rather than blanking out.
    val coverColor = rememberCoverDominantColor(anime.coverUrl)
        ?: MaterialTheme.colorScheme.surfaceVariant
    // Darken the cover color for the gradient's solid bottom — guarantees
    // white-text contrast regardless of the extracted hue/lightness. 0.55 blend
    // toward black lands any mid-tone (lightness 0.40–0.65) at ~0.18–0.29 final
    // lightness → comfortable white-on-color AA contrast.
    val darkCoverColor = lerp(coverColor, Color.Black, 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_CARD_RATIO)
            .clip(HeroCardShape)
            .background(coverColor)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                HeroCardShape,
            )
            .clickable(onClick = onClick),
    ) {
        // ── Layer 1: FULL banner (Fit + TopCenter — no crop, whole banner).
        // AniList banners are ~3:1; at the card's width they occupy the top
        // ~40% of the card. Fit guarantees no part of the banner is lost
        // (the user's explicit "won't be cropped" requirement, D-277). ──
        val bannerUrl = anime.bannerImage
        if (!bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = anime.displayName,
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Layer 2: palette gradient overlay — transparent over the banner,
        // ramps to solid coverColor at the junction (the banner's bottom edge
        // feathers into solid color → no hard seam), then to darkCoverColor at
        // the bottom for white-text contrast. This is "gradient colors of the
        // cover image", NOT a blurred copy of the cover (D-277 device feedback:
        // "rather than being the bold version of the cover image"). ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,    // top — banner reads fully
                            Color.Transparent,     // ~45% — still banner zone
                            coverColor,            // ~55% junction — banner
                                                   //  feathers into solid color
                            darkCoverColor,        // bottom — solid dark (text zone)
                        ),
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
                    .background(Color.Black.copy(alpha = 0.25f)),
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
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Text block.
            Column(modifier = Modifier.weight(1f)) {
                // Rank pill — translucent dark (matches the genre chips' language;
                // sits on the dark gradient zone, readable on any cover hue).
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = "#$rank TRENDING",
                        fontFamily = RobotoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Title — 18sp ExtraBold, up to 2 lines (white: it always sits
                // on the dark gradient zone, regardless of theme).
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

/**
 * D-277: the hero card's width:height ratio (1.2:1 — cinematic, taller than the
 * old 16:9 so a full banner + a content zone both fit). All pager pages share
 * this ratio so the pager height never jumps between banners of differing
 * aspect ratios.
 */
private const val HERO_CARD_RATIO = 1.2f

// D-262: auto-advance interval doubled (was 6s) per device feedback
// "should be doubled... maybe 12 seconds".
private const val HERO_AUTO_ADVANCE_MS = 12_000L

/** Duration of the auto-advance slide animation. */
private const val HERO_ADVANCE_ANIM_MS = 600

/** Genre chips shown in the hero (overflow folds into a "+N" chip). */
private const val HERO_MAX_GENRE_CHIPS = 3

/**
 * Number of virtual copies for the infinite pager. 200 keeps the forward
 * runway effectively endless (~200 minutes of auto-advance at 12s/page) while
 * staying a trivial integer for the modulo math.
 */
private const val HERO_VIRTUAL_COPIES = 200
