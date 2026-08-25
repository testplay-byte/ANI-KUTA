package com.confused.anikuta.feature.animebrowse

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * D-257 / D-262: Browse hero v3 — a padded, rounded, cinematic banner card
 * (evolution of D-256 after device feedback: the hero "looks way too ugly,
 * like a rigid kind of format", the banner felt "forced into a square vibe",
 * and the auto-scroll wraparound was "not smooth, not animated").
 *
 * Changes vs D-256 (D-257) + device-feedback batch #2 (D-262):
 * - **Wider banner aspect**: the hero is an inset 16:9 card (16dp side
 *   margins, 20dp rounded corners, 1dp border — the standard card language)
 *   instead of a full-bleed ~1.2:1 block. 16:9 matches AniList's native banner
 *   ratio, so the artwork shows with minimal cropping.
 * - **Smooth auto-advance (infinite pager)**: the pager is virtually
 *   circular — `pageCount = size × 200`, starting at `size × 100` — so every
 *   auto-advance moves FORWARD exactly one page with a 600ms tween. The old
 *   wraparound scrolled backwards through all pages (the "ugly" glitch).
 *   D-262: the auto-advance loop is now restart-proof (a `while(true)` keyed
 *   on `(pagerState, virtualCount)` — NOT on `currentPage`, which flipped at
 *   the 50% scroll crossing and cancelled the animation mid-flight, leaving
 *   the pager stuck "between two banners" with no snap). CancellationException
 *   is caught → the nearest whole page is snapped so the card never rests
 *   misaligned. Dots read `settledPage` (change on settle, not mid-slide).
 * - **12s auto-advance** (D-262 device feedback: "should be doubled...
 *   maybe 12 seconds"). Was 6s.
 * - **Blurred + darkened backdrop** (D-262 device feedback: "the background
 *   banner should be slightly blurred out and darkened"). The backdrop is
 *   now a CPU-blurred small thumbnail (Coil 3 removed the Transformation API
 *   entirely — there is no `coil3.transform.Transformation`; `Modifier.blur`
 *   is a runtime no-op below API 31). See [BlurredBannerBackdrop] + the
 *   stronger scrim stops (0.30 / 0 / 0 / 0.55 / 0.88).
 * - Cover poster + banner layered as before (D-256 anatomy), rank pill,
 *   2-line title, score/eps/year meta and genre chips, page dots BELOW the
 *   card (centered — never collides with the text block on narrow screens).
 *
 * ```
 * ┌─── 16dp ──────────────────────────────────┐
 * │ ╭───────────────────────────────────────╮ │      ← 16:9, 20dp corners, 1dp border
 * │ │     banner (BLURRED + darkened)        │ │
 * │ │        bottom-heavy scrim               │ │
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

/** A single hero card — blurred banner backdrop + scrim + cover-and-text foreground. */
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
        // ── Layer 1: BLURRED banner backdrop (D-262 — falls back to the cover) ──
        // D-262: the backdrop is now blurred + darkened per device feedback.
        // See BlurredBannerBackdrop for the CPU-blur approach (works on every
        // API — Coil 3 has no Transformation API; Modifier.blur is API 31+).
        BlurredBannerBackdrop(
            url = anime.bannerImage ?: anime.coverUrl,
            contentDescription = anime.displayName,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Layer 2: bottom-heavy gradient scrim (D-262: darkened — was
        // 0.18/0/0/0.45/0.82; now 0.30/0/0/0.55/0.88 so the white text block
        // reads over the blurred artwork). A subtle top scrim keeps the card's
        // top edge soft. ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.88f),
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

/**
 * D-262: the blurred banner backdrop. Works on every API level (minSdk 24).
 *
 * Coil 3 removed the `Transformation` API entirely — there is no
 * `coil3.transform.Transformation`. `Modifier.blur()` uses `RenderEffect`
 * (API 31+ only; silent no-op below). The user's device API is unknown → the
 * blur must work on minSdk 24.
 *
 * Approach: load a small (HERO_BLUR_W_PX × HERO_BLUR_H_PX) thumbnail via the
 * singleton ImageLoader on Dispatchers.IO, namespaced under a custom
 * `memoryCacheKey` so it never collides with the sharp cover/poster requests
 * for the same URL (when banner is null, the cover serves as the backdrop AND
 * as the sharp 84×126 poster — the custom key keeps them separate). Run a
 * 3-pass... actually single-pass box blur on the tiny bitmap (~1ms on IO),
 * then render it full-size via ContentScale.Crop (the GPU upscales
 * bilinearly → the soft backdrop). Result cached in Coil's memory under
 * `"hero-blur:$url"` → instant on adjacent-page preload (SectionPreloader
 * enqueues the same request) and recompose.
 */
@Composable
private fun BlurredBannerBackdrop(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val loader = remember { context.imageLoader }
    val blurred by produceState<ImageBitmap?>(initialValue = null, url) {
        value = null
        if (url.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .size(HERO_BLUR_W_PX, HERO_BLUR_H_PX)
                    .memoryCacheKey("$HERO_BLUR_KEY_PREFIX$url")
                    .build()
                val r = loader.execute(req)
                // D-262: SuccessResult.image is non-null (ImageResult.image is
                // nullable on the sealed interface; SuccessResult overrides it
                // non-null). The `as?` cast + null-coalescing returns null on
                // any non-success result (network error, etc.) → backdrop
                // stays blank until next try.
                val src = (r as? SuccessResult)?.image
                    ?.toBitmap(HERO_BLUR_W_PX, HERO_BLUR_H_PX)
                    ?: return@withContext null
                // Safety net: guarantee the bitmap is at the target decode size
                // (Coil's toBitmap SHOULD scale, but be explicit so the box
                // blur runs on the exact dims we expect).
                val sized = if (src.width == HERO_BLUR_W_PX && src.height == HERO_BLUR_H_PX) {
                    src
                } else {
                    Bitmap.createScaledBitmap(src, HERO_BLUR_W_PX, HERO_BLUR_H_PX, true)
                }
                boxBlur(sized, HERO_BLUR_RADIUS_PX).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        val current = blurred
        if (current != null) {
            Image(
                painter = BitmapPainter(current),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * D-262: a simple in-place-safe box blur (moving-average over a
 * (2*radius+1)² window). Naive O(w*h*radius²) — fine for the 160×90 backdrop
 * (~360k ops per pass; <1ms on IO). Allocates a fresh output bitmap (the
 * input is Coil-owned and must not be recycled here).
 */
private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return src
    val w = src.width
    val h = src.height
    if (w <= 1 || h <= 1) return src
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val result = IntArray(w * h)
    val window = radius * 2 + 1
    val div = (window * window).toFloat()
    for (y in 0 until h) {
        for (x in 0 until w) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (dy in -radius..radius) {
                val yy = (y + dy).coerceIn(0, h - 1)
                for (dx in -radius..radius) {
                    val xx = (x + dx).coerceIn(0, w - 1)
                    val p = pixels[yy * w + xx]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                }
            }
            result[y * w + x] = (
                ((a / div).toInt() shl 24) or
                    ((r / div).toInt() shl 16) or
                    ((g / div).toInt() shl 8) or
                    (b / div).toInt()
                )
        }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(result, 0, w, 0, 0, w, h)
    return out
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

// ── D-262: blurred-backdrop decode size + blur params + cache key ──────────
// 160×90 is a 16:9 thumbnail; the GPU upscales it bilinearly to the full card
// (~1080×590) → the soft backdrop. Box-blur radius 2 on the small bitmap
// adds gaussian-like smoothness. The custom memoryCacheKey namespaces the
// blurred request so it never collides with the sharp cover/poster requests
// for the same URL (per the D-257 lesson: the Android memory-cache key
// excludes size when there are no transformations).
private const val HERO_BLUR_W_PX = 160
private const val HERO_BLUR_H_PX = 90
private const val HERO_BLUR_RADIUS_PX = 2
private const val HERO_BLUR_KEY_PREFIX = "hero-blur:"
