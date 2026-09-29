package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-3): the honest Scroll-to-Current — smooth, centered, clamped
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.59 device round's order: the player's "Scroll to Current" "should
//  scroll with a smooth animation rather than just jumping to the one. It
//  should smoothly start scrolling and it should stop when the currently
//  playing episode is in proper view. Like if it can be centered, then it
//  will be centered, but if it cannot be centered, then it will not be
//  centered and it will just stop there. Like for example, if the very first
//  or very last episode is being played, then that is most definitely never
//  going to be centered."
//
//  WHY the built-in falls short (the round's two root causes):
//   1. `LazyListState.animateScrollToItem` SNAPS INSTANTLY when the target
//      is more than a viewport away (jump-then-glide) — the "just jumping"
//      the user reported on long lists.
//   2. It lands the item TOP-ALIGNED at the viewport's start, not centered.
//
//  THE FIX — a two-phase glide driven entirely through `animateScrollBy`
//  (which is pixel-continuous for ANY distance — no snap by construction):
//   • TRAVEL — when the target is not yet composed, glide by the ESTIMATED
//     pixel distance (the median composed item size × the item span): one
//     long, fully-animated sweep with an accelerate-then-decelerate curve
//     ("smoothly start scrolling"), duration proportional to the distance
//     (250–900ms — fast on huge lists, gentle on short hops).
//   • SETTLE — once the target is composed, a short spring centers its
//     midpoint in the LIST'S OWN viewport (the area below the player —
//     exactly the basis the user specified, since the LazyColumn fills it).
//   • EDGES — the settle that would over/under-scroll simply clamps at the
//     content bounds and stops there: the first/last episodes can never
//     center, per the spec, and the animation never fights the clamp.
// ════════════════════════════════════════════════════════════════════════════

/** The travel curve: accelerate from rest, decelerate into arrival. */
private val SmoothTravelEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

/** The travel duration per viewport of distance (fast on long lists). */
private const val TRAVEL_MS_PER_VIEWPORT = 320
private const val TRAVEL_MS_MIN = 250
private const val TRAVEL_MS_MAX = 900

/** How many travel passes before the honest fallback (a sane bound — the
 *  median-size estimate lands within a couple of rows on real lists). */
private const val MAX_TRAVEL_PASSES = 4

/**
 * Scrolls the list so the item at [index] sits CENTERED in the list's own
 * viewport — smoothly (no snap), with the edges respected (a target too
 * close to either end stops un-centered, naturally clamped).
 *
 * Call from a coroutine scope (a click handler's `scope.launch { }`).
 * Negative/out-of-range indices are a no-op.
 */
suspend fun LazyListState.animateScrollToItemCentered(index: Int) {
    if (index < 0) return
    // Wait-free guard: no layout yet (or past the end) → nothing to do.
    val total = layoutInfo.totalItemsCount
    if (total == 0 || index >= total) return

    // ── The exact centering delta, when the target is already composed. ──
    suspend fun settleIfComposed(): Boolean {
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return false
        val viewportCenter = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2f
        val delta = item.offset + item.size / 2f - viewportCenter
        if (abs(delta) > 0.5f) {
            animateScrollBy(
                delta,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        return true
    }

    // Near target — the single settle glide.
    if (settleIfComposed()) return

    // ── Far target — travel by estimate, then settle. (SA2-F2 fix,
    // lead-verified: a ~zero estimate with an uncomposed target BREAKS to
    // the fallback instead of silently returning — the pathological layout
    // still lands the row in view.) ──
    for (pass in 0 until MAX_TRAVEL_PASSES) {
        val estimate = estimateTravelTo(index)
        if (abs(estimate) < 1f) break
        val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .coerceAtLeast(1)
        val duration = (abs(estimate) / viewport * TRAVEL_MS_PER_VIEWPORT)
            .toInt()
            .coerceIn(TRAVEL_MS_MIN, TRAVEL_MS_MAX)
        animateScrollBy(
            estimate,
            tween(durationMillis = duration, easing = SmoothTravelEasing),
        )
        if (settleIfComposed()) return
    }

    // The pathological-layout fallback (never observed on the player lists —
    // uniform-ish rows estimate well): the built-in animated scroll still
    // lands the row fully in view, just top-aligned.
    animateScrollToItem(index)
}

/**
 * The pixel-distance estimate from the CURRENT scroll position to the
 * position that would center [index] — the median COMPOSED item size times
 * the item span, relative to the first visible item. The median (not the
 * mean) ignores tall headers/cards that happen to be composed; the estimate
 * only needs to land the target INSIDE the composed window for the settle
 * phase to take over — a couple of rows of error is invisible after the
 * settle spring.
 */
private fun LazyListState.estimateTravelTo(index: Int): Float {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return 0f

    // The representative item size — the MEDIAN of the composed sizes.
    val sizes = visible.map { it.size }.filter { it > 0 }.sorted()
    val medianSize = when {
        sizes.isNotEmpty() -> sizes[sizes.size / 2].toFloat()
        else -> (info.viewportEndOffset - info.viewportStartOffset) / 4f
    }

    val first = visible.first()
    // item.offset is relative to the viewport start; the target's estimated
    // top = the first visible item's top + the span × the representative size.
    val targetTop = first.offset + (index - first.index) * medianSize
    val viewportCenter = (info.viewportEndOffset + info.viewportStartOffset) / 2f
    val desiredTop = viewportCenter - medianSize / 2f
    return targetTop - desiredTop
}
