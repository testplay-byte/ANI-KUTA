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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import kotlin.random.Random

/**
 * D-289: Browse hero v6 — compact fixed height + abstract splash background +
 * seamless banner↔content blending.
 *
 * User device feedback on v0.2.55 (after v5's reduced-height 6-color gradient):
 * - "The hero section height is very bad … way too tall. I need you to make it
 *   less tall in height" → **fixed [HERO_HEIGHT] = 148dp** — just a little
 *   taller than the 114dp cover poster it frames (v5's 1.4:1 ratio was still
 *   ~234dp tall on a 360dp-wide screen).
 * - "The top banner area … could be in the background, over the cover image and
 *   the background of the text" → the banner is now a **full-bleed background
 *   layer** (Crop + Center) behind everything — atmosphere, not a showcase.
 * - "I did not want you to quite literally go with a gradient … a random splash
 *   of colors … not a smooth gradient … some splash of colors which blend in
 *   together with each other randomly … an abstract splash kind of vibe" →
 *   [SplashOverlay]: 8 soft-edged radial blobs in the cover's own 6-color
 *   palette at seeded-random positions/sizes, layered organically — no linear
 *   ramp anywhere.
 * - "The cover image's colors would blend in smoothly around it. Also the top
 *   banner section would blend in smoothly towards it too. The difference
 *   between where the top banner ends and the bottom section starts would be
 *   minimal … the user can't describe or see it quite clearly where the
 *   boundary is" → there IS no banner-end boundary anymore: the banner spans
 *   the full card, the blobs float over it with soft radial edges everywhere
 *   (denser toward the bottom where the text sits), and one large echo blob
 *   washes the poster's own palette color around the cover. A smooth dark veil
 *   (near-zero at top → deeper at bottom) unifies the layers and guarantees
 *   white-text contrast.
 *
 * **Anatomy** (16dp inset / 20dp corners / 1dp border card language):
 * ```
 * ┌──────────────────────────────────────────────┐  ← fixed 148dp height
 * │ ░▒▓ banner (Crop, full-bleed background) ▓▒░ │     (≈ poster + 34dp)
 * │  ˚˚ splash blobs — cover palette, random ˚˚  │
 * │    ˚˚˚ echo blob around poster ˚˚˚           │
 * │ ┌────┐  #1 TRENDING                          │
 * │ │cover│  Title (18sp ExtraBold, white)      │  ← bottom-aligned, 12dp pad
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
 * uncropped banner + single-color palette gradient) → D-283/D-284 (reduced
 * height + 6-color gradient + dark veil) → **D-289** (compact fixed height +
 * banner-as-background + abstract splash + seamless blend — this version).
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
 * A single hero card — compact fixed height, banner-as-background, abstract
 * splash of cover-palette colors, seamless layering (D-289).
 *
 * Layering (bottom → top render order):
 * 1. **Base + banner** — solid darkest palette color (shows wherever the
 *    banner is missing/failing) + the banner as a full-bleed BACKGROUND layer
 *    (`ContentScale.Crop`, Center) — the user's "banner … in the background,
 *    over the cover image and the background of the text".
 * 2. **[SplashOverlay]** — 8 seeded-random radial blobs in the cover's own
 *    6-color palette (light blobs keep the upper zone airy so the banner still
 *    reads; denser/darker blobs gather toward the content zone) + one large
 *    "echo" blob washing the poster's palette color around the cover ("the
 *    cover image's colors would blend in smoothly around it").
 * 3. **Unifying veil** — a smooth near-zero → deep black ramp over everything.
 *    Because the banner never "ends" (it spans the full card) and every blob
 *    edge is a soft radial falloff, there is no visible boundary anywhere —
 *    the top banner zone and the bottom content zone melt into each other.
 * 4. **Foreground Row** — cover poster (76×114) + rank pill + title + meta +
 *    chips, bottom-aligned on the darkest zone.
 */
@Composable
private fun HeroCard(
    anime: AniListAnime,
    rank: Int,
    onClick: () -> Unit,
) {
    // 5–6 palette colors from the cover (D-284 extractor: dominant/vibrant/
    // muted swatches darkened into a cinematic band). Null on blank URL or
    // extraction failure → neutral surface ramp fallback so the hero still
    // renders rather than blanking out.
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

    // Stable per-item randomness: every hero page derives its own splash
    // layout from its cover URL — reshuffles never happen on recomposition,
    // but each banner gets a genuinely different abstract arrangement.
    val splashSeed = remember(anime.coverUrl, anime.displayName) {
        (anime.coverUrl ?: anime.displayName).hashCode()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(HeroCardShape)
            .background(ramp.last())
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                HeroCardShape,
            )
            .clickable(onClick = onClick),
    ) {
        // ── Layer 1: BANNER — full-bleed background (Crop + Center). ──
        // The banner no longer has to fit or stay uncropped: it is atmosphere
        // behind the splash + content layers (D-289 device feedback). A ~3:1
        // AniList banner scaled to cover a 148dp card loses only its outer
        // wings — the artwork's center (where the subject sits) fills the card.
        val bannerUrl = anime.bannerImage
        if (!bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = anime.displayName,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Layer 2: SPLASH — abstract seeded-random blobs in the cover's own
        // palette ("not a smooth gradient … a random splash of colors which
        // blend in together with each other randomly"). ──
        SplashOverlay(
            colors = ramp,
            seed = splashSeed,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Layer 3: unifying veil — smooth near-zero → deep black. Keeps the
        // top banner zone airy, deepens toward the content zone for white-text
        // contrast, and visually welds banner + splash + base into one
        // continuous field (no detectable "where the banner ends" line). ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.06f),
                        0.5f to Color.Black.copy(alpha = 0.20f),
                        1f to Color.Black.copy(alpha = 0.52f),
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
            // Cover poster (2:3, matches the carousel card language).
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
                // sits on the dark splash zone, readable on any cover hue).
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
                // on the dark splash zone, regardless of theme).
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

// ── D-289: abstract splash ─────────────────────────────────────────────────

/**
 * One splash blob: a soft-edged radial gradient circle, placed and sized as
 * fractions of the card. All fields are fractions so the same seeded layout
 * adapts to any card size.
 */
private data class SplashBlob(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
)

/**
 * Builds the seeded-random blob layout for a hero card (D-289).
 *
 * The arrangement ("abstract splash kind of vibe"):
 * - **2 airy top blobs** — low alpha (0.16–0.26), anywhere in the card: they
 *   keep the banner zone alive with color without burying the artwork.
 * - **5 content blobs** — stronger (0.32–0.55), biased toward the lower ⅔
 *   where the poster + text sit; varied radii (0.30–0.72 of width) overlap
 *   each other and blend organically (SRC_OVER alpha stacking).
 * - **1 poster echo blob** — the palette's lightest color at ~0.5 alpha,
 *   centered just behind the poster's position, ~0.55 of the card width:
 *   "the cover image's colors would blend in smoothly around it".
 *
 * Colors cycle through the cover's palette with a stride so neighboring blobs
 * rarely repeat a hue, and the seeded [Random] makes the layout stable per
 * cover (no reshuffling on recomposition) yet different for every hero page.
 */
private fun buildSplashBlobs(colors: List<Color>, seed: Int): List<SplashBlob> {
    if (colors.isEmpty()) return emptyList()
    val rng = Random(seed)
    val blobs = mutableListOf<SplashBlob>()

    // Poster echo — lightest palette color washing around the cover.
    blobs += SplashBlob(
        centerX = 0.17f,
        centerY = 0.72f,
        radius = 0.55f,
        color = colors.first(),
        alpha = 0.50f,
    )

    // Airy top-zone blobs (let the banner read through).
    repeat(2) { i ->
        blobs += SplashBlob(
            centerX = rng.nextFloat().mapToRange(0.05f, 0.95f),
            centerY = rng.nextFloat().mapToRange(0.08f, 0.45f),
            radius = rng.nextFloat().mapToRange(0.38f, 0.62f),
            color = colors[(i * 2 + 1) % colors.size],
            alpha = rng.nextFloat().mapToRange(0.16f, 0.26f),
        )
    }

    // Content-zone blobs (denser, darker, toward the bottom).
    repeat(5) { i ->
        blobs += SplashBlob(
            centerX = rng.nextFloat().mapToRange(0.0f, 1.0f),
            centerY = rng.nextFloat().mapToRange(0.42f, 1.05f),
            radius = rng.nextFloat().mapToRange(0.30f, 0.72f),
            color = colors[(i * 2 + 2) % colors.size],
            alpha = rng.nextFloat().mapToRange(0.32f, 0.55f),
        )
    }

    return blobs
}

/** Maps rng.nextFloat() (0..1) into [from, to]. */
private fun Float.mapToRange(from: Float, to: Float): Float =
    from + (this * (to - from))

/**
 * The abstract splash layer (D-289) — draws [buildSplashBlobs]'s seeded layout
 * as overlapping soft-edged radial gradients directly behind the hero content.
 *
 * Draw order: the poster echo blob FIRST (it reads as a halo behind the cover
 * once the foreground renders on top), then the random blobs on top of it.
 * Every edge is a radial falloff to transparent, so blobs never form hard
 * outlines against the banner or each other — they "blend in together with
 * each other randomly", exactly the requested abstract-splash feel.
 */
@Composable
private fun SplashOverlay(
    colors: List<Color>,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val blobs = remember(colors, seed) { buildSplashBlobs(colors, seed) }
    Box(
        modifier = modifier.drawBehind {
            blobs.forEach { blob ->
                val center = Offset(
                    x = size.width * blob.centerX,
                    y = size.height * blob.centerY,
                )
                val radius = size.width * blob.radius
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blob.color.copy(alpha = blob.alpha),
                            blob.color.copy(alpha = blob.alpha * 0.55f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        },
    )
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
 * D-289: the hero card's FIXED height. The user's rule: "make sure that the
 * hero section is a little bit taller than the cover image itself" — the
 * poster is 114dp (+ 12dp bottom padding), so 148dp frames it with a ~22dp
 * banner-airy strip above. (v5's 1.4:1 aspect ratio still produced ~234dp on a
 * 360dp-wide screen — "way too tall".) All pager pages share this fixed
 * height so the pager never jumps between banners.
 */
private val HERO_HEIGHT = 148.dp

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
