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
import com.confused.anikuta.core.designsystem.color.rememberCoverGradientColors
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
 * D-283/D-284: Browse hero v5 — full uncropped banner + 6-color palette
 * gradient + dark veil + reduced height.
 *
 * v4 (D-277) fixed the banner (Fit + TopCenter — whole banner, no crop) and
 * replaced the blurred-cover strip with a SINGLE dominant-color gradient. User
 * device feedback on v0.2.54:
 * - "its height is apparently a bit more than what I hoped for … The bottom
 *   section below the banner is way too much" → **D-283:** card ratio 1.2:1 →
 *   1.4:1 (~15% shorter card; the below-banner zone shrinks ~25%) + poster
 *   84×126 → 76×114 to match.
 * - "the accent theme color … is apparently not correct … utilize some beautiful
 *   gradient kind of effect … Don't use a simple solid color but utilize a smooth
 *   darker kind of gradient. Utilize maybe five or six colors from the cover image
 *   … On top of that gradient effect, apply a slightly blurred dark effect" →
 *   **D-284:** the content zone is now a 6-stop ramp of palette colors from the
 *   cover ([rememberCoverGradientColors] — dominant/vibrant/muted swatches,
 *   darkened into a cinematic band, sorted light → dark), with a soft black veil
 *   layered on top.
 *
 * **Anatomy** (same 16dp inset / 20dp corners / 1dp border card language):
 * - **Full banner, uncropped** (unchanged from v4): `ContentScale.Fit` +
 *   `Alignment.TopCenter` — AniList banners (~3:1) occupy the top ~47% of the
 *   now-shorter card.
 * - **6-color palette gradient**: transparent over the banner (it reads fully),
 *   feathering into the ramp at the junction, then the 6 palette colors blend
 *   smoothly to the bottom. Fallback ramp (null extraction): surfaceVariant
 *   darkened in steps.
 * - **Dark veil (the "slightly blurred dark effect")**: a near-imperceptible →
 *   soft black gradient layered OVER the palette ramp. (A literal
 *   `Modifier.blur()` on a smooth vertical gradient is a visual no-op — there's
 *   no horizontal variance to smear — so the soft darkening veil IS the blurred
 *   feel; it also guarantees white-text contrast on any cover palette.)
 * - Foreground: cover poster (76×114) + rank pill + title + meta + chips,
 *   bottom-aligned on the dark zone.
 *
 * ```
 * ┌──────────────────────────────────────────────┐  ← HERO_CARD_RATIO (1.4:1),
 * │  ▓▓▓▓▓▓ FULL BANNER (Fit, TopCenter) ▓▓▓▓▓▓ │     20dp corners, 1dp border
 * │  ░░░ feather (transparent → ramp[0]) ░░░░░░░ │     — whole banner, no crop
 * │ ┌────┐  #1 TRENDING                          │     6 palette colors (light→dark)
 * │ │cover│  Title (18sp ExtraBold, white)      │     + soft dark veil on top
 * │ │2:3 │  ★ 85 · 24 eps · 2024               │
 * │ └────┘  [Action] [Comedy] [+2]               │
 * └──────────────────────────────────────────────┘
 * ```
 *
 * The pager mechanics (infinite virtual copies, 12s auto-advance, restart-proof
 * loop, dots reading settledPage) are UNCHANGED from D-262 — only the card's
 * internals were redone.
 *
 * History: D-256 (first hero) → D-257 (wider banner) → D-262 (smooth
 * auto-advance) → D-275 (sharp banner + blurred-cover strip) → D-277 (full
 * uncropped banner + single-color palette gradient) → **D-283/D-284** (reduced
 * height + 6-color gradient + dark veil — this version).
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
 * A single hero card — full uncropped banner + 6-color palette gradient + dark
 * veil (D-283/D-284).
 *
 * Layering (bottom → top render order):
 * 1. **Base + banner** — solid ramp[0] (the lightest palette color — shows in
 *    the transparent banner zone so the top stays continuous) + the full banner
 *    via `AsyncImage(ContentScale.Fit, TopCenter)` (no crop). Skipped when no
 *    banner URL → gradient-only header.
 * 2. **Palette gradient overlay** — explicit-position stops: transparent to
 *    [BANNER_FEATHER_END], then the 6 ramp colors blended smoothly to the
 *    bottom. The transparent zone lets the banner read fully; the feather
 *    junction hides the banner's bottom edge into ramp[0] (no hard seam).
 * 3. **Dark veil** — `Brush.verticalGradient` of near-zero → soft black alpha
 *    over everything (the "slightly blurred dark effect", D-284 — see file
 *    KDoc for why a literal blur is a no-op here).
 * 4. **Foreground Row** — cover poster (76×114) + rank pill + title + meta +
 *    chips, bottom-aligned (on the darkest zone).
 */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    rank: Int,
    onClick: () -> Unit,
) {
    // D-284: 5–6 palette colors from the cover, light → dark. Null on blank URL
    // or extraction failure → fall back to a neutral surface ramp so the hero
    // still renders (with a neutral gradient) rather than blanking out.
    val paletteRamp = rememberCoverGradientColors(anime.coverUrl)
    val fallbackBase = MaterialTheme.colorScheme.surfaceVariant
    val ramp = paletteRamp ?: listOf(
        fallbackBase,
        lerp(fallbackBase, Color.Black, 0.18f),
        lerp(fallbackBase, Color.Black, 0.36f),
        lerp(fallbackBase, Color.Black, 0.52f),
        lerp(fallbackBase, Color.Black, 0.66f),
        lerp(fallbackBase, Color.Black, 0.78f),
    )

    // D-284: explicit-position gradient stops — transparent over the banner
    // (it reads fully), feathered junction, then the ramp blended to the bottom.
    val gradientStops = buildList {
        add(0f to Color.Transparent)
        add(BANNER_FEATHER_END to Color.Transparent)
        ramp.forEachIndexed { i, color ->
            val t = BANNER_FEATHER_END +
                (1f - BANNER_FEATHER_END) * (i + 1f) / ramp.size
            add(t to color)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_CARD_RATIO)
            .clip(HeroCardShape)
            .background(ramp.first())
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                HeroCardShape,
            )
            .clickable(onClick = onClick),
    ) {
        // ── Layer 1: FULL banner (Fit + TopCenter — no crop, whole banner).
        // AniList banners are ~3:1; at the card's width they occupy the top
        // ~47% of the (now shorter, D-283) card. Fit guarantees no part of the
        // banner is lost (the user's explicit "won't be cropped" requirement). ──
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

        // ── Layer 2: 6-color palette gradient — transparent over the banner,
        // feathered junction into ramp[0], then the cover's palette colors blend
        // smoothly (light → dark) to the bottom. "Five or six colors from the
        // cover image … smooth blended gradient", NOT a blurred copy of the
        // cover (D-284 device feedback).
        // (verticalGradient's colorStops is a vararg — spread the built list.) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(*gradientStops.toTypedArray())),
        )

        // ── Layer 3: dark veil — the "slightly blurred dark effect" applied on
        // top of the gradient (D-284). A soft black ramp: near-imperceptible on
        // the banner (top), deepening over the content zone so the bottom is
        // clearly darkened + text contrast is guaranteed on any palette. ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        *arrayOf(
                            0f to Color.Black.copy(alpha = 0.04f),
                            BANNER_FEATHER_END to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.32f),
                        ),
                    ),
                ),
        )

        // ── Layer 4: foreground — cover poster + text block, bottom-aligned. ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Cover poster (2:3, matches the carousel card language; D-283:
            // 84×126 → 76×114 to fit the shorter card).
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 114.dp)
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
 * D-283: the hero card's width:height ratio. 1.2:1 (v4) → **1.4:1** per device
 * feedback ("its height is apparently a bit more than what I hoped for … The
 * bottom section below the banner is way too much"): the card is ~15% shorter
 * and the below-banner content zone shrinks ~25%. All pager pages share this
 * ratio so the pager height never jumps between banners of differing aspect
 * ratios. A ~3:1 AniList banner occupies the top ~47% of the card; the poster
 * (76×114 + 12dp bottom padding) may overlap the banner's feathered bottom
 * edge by a few dp on narrower banners — the classic cinematic overlap, and
 * the banner is still fully rendered (Fit, never cropped).
 */
private const val HERO_CARD_RATIO = 1.4f

/**
 * D-284: where the gradient's transparent zone ends (the banner-feather
 * junction). Below this fraction the palette ramp blends in — ramp[0] reaches
 * full opacity at `BANNER_FEATHER_END + (1 - BANNER_FEATHER_END) / ramp.size`,
 * feathering the banner's bottom edge (~40–58% for AniList's 2.5:1–4:1 banners)
 * into the gradient with no hard seam.
 */
private const val BANNER_FEATHER_END = 0.42f

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
