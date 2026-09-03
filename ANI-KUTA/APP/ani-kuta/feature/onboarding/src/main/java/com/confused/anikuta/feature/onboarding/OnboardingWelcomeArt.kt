package com.confused.anikuta.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * D-405 → D-406 (round 30): the CUSTOM animated art of the wizard's welcome
 * screen — deliberately NOT Material-styled (the user's spec: "a custom
 * modern beautiful-looking UI design").
 *
 * ## D-406: why the round-29 art stuttered, and the new engine
 * The round-30 device report: "there was some stuttering there. Occasionally
 * it would skip some frames or jump into some frames afterwards. It still
 * keeps resetting and some stuff like that." Two concrete defects, both
 * fixed AT THE ROOT this round:
 *
 * 1. **THE PHASE-WRAP RESET** — the old engine drove everything from two
 *    `rememberInfiniteTransition` phases that each ran 0 → 2π and then
 *    WRAPPED back to 0. The blobs multiplied those phases by NON-INTEGER
 *    speeds (1.7, 2.1, 1.4…), so at the wrap `sin(2π × 1.7) ≠ sin(0)` —
 *    every blob's silhouette SNAPPED and its center TELEPORTED on a fixed
 *    11s/24s schedule. That was the "keeps resetting / jumps into frames".
 *    The new engine runs ONE MONOTONIC CLOCK — frame-nano deltas,
 *    clamped so a backgrounded app PAUSES the art instead of jumping it —
 *    and every motion is `sin/cos(t · f + φ)` of ever-growing `t`:
 *    NOTHING wraps, NOTHING resets, ever.
 *
 * 2. **PER-FRAME ALLOCATIONS** — the old draw pass built a fresh `Path`,
 *    an `Array(8)` and 8 `Offset`s per blob per frame (~50 heap objects a
 *    frame → GC churn → the "skipped frames"). The new pass pre-allocates
 *    every [Path] and reuses FloatArrays; the radial-gradient brushes are
 *    cached and rebuilt ONLY when the canvas width or accent changes. The
 *    steady state allocates ZERO objects per frame.
 *
 * ## The motion (the report: "smooth flowing shapes which would change into
 * different shapes and would split sometimes and other stuff like that")
 * - each blob's silhouette blends an ORGANIC wobble (two spatial harmonics
 *   drifting in time) with a ROUNDED REGULAR POLYGON (its own side count —
 *   triangle / square / pentagon / hexagon) along a staged MORPH cycle:
 *   hold-organic → smooth morph → hold-shaped → smooth return;
 * - each blob also carries a staged SPLIT cycle — two halves born at the
 *   SAME center with the SAME shape (indistinguishable from one blob) that
 *   drift apart along a slowly precessing axis, easing slightly smaller as
 *   they separate, then merge back; the halves' wobble phases diverge ONLY
 *   in proportion to the split, so both the birth and the merge are
 *   perfectly continuous — no pop, no jump;
 * - the centers ride continuous Lissajous orbits, and a hairline outline
 *   geometry layer rotates on the same monotonic clock.
 *
 * All of it is draw-phase work: the clock state is read ONLY inside
 * `drawBehind` → one draw invalidation per frame, zero recompositions, and
 * the layout is seeded-parameterized and remembered — the art is stable
 * across recompositions. The accent blob recolors LIVE with the theme.
 */
@Composable
internal fun OnboardingBlobBackground(
    accent: Color,
    base: Color,
    modifier: Modifier = Modifier,
) {
    // ── The single monotonic clock ──
    // Written once per frame by the frame-clock loop; read ONLY by the
    // drawBehind below (a draw-phase read → the write invalidates the draw
    // phase alone — no recomposition, no remeasure). The per-frame delta is
    // CLAMPED to 64ms: when the app backgrounds and the frame clock stalls,
    // the art resumes exactly where it paused instead of leaping forward.
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrame = -1L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame >= 0L) {
                    val dt = (now - lastFrame) / 1_000_000_000f
                    timeSec += dt.coerceAtMost(0.064f)
                }
                lastFrame = now
            }
        }
    }

    // ── The stable art state ──
    // Rebuilt ONLY when the accent changes (a rare theme pick). All Paths,
    // sample arrays, and the brush cache live here — nothing is allocated
    // per frame. (The brush cache rebuilds itself on the first draw at a
    // new canvas width, see [BlobArt.brushesFor].)
    // NOTE: the clock is read ONLY inside the drawBehind lambda below — a
    // draw-phase read, so the per-frame write invalidates the DRAW alone
    // (zero recomposition, zero remeasure). Reading it here in the
    // composable body would recompose this whole subtree every frame.
    val art = remember(accent) { BlobArt(buildFlowBlobs(accent)) }
    val outlines = remember { buildOutlineShapes() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // The monotonic clock — read ONCE per draw invocation (this
                // is the draw-phase state read; see the note above).
                val t = timeSec

                // Layer 0 — the deep base.
                drawRect(color = base)

                // Layer 1 — the morphing, splitting organic blobs.
                val brushes = art.brushesFor(size.width)
                art.blobs.forEachIndexed { i, blob ->
                    // The drifting center — a continuous Lissajous orbit of
                    // ever-growing t (never retraces, never resets).
                    val cx = size.width * (
                        blob.anchorX + blob.driftAmpX *
                            sin(t * blob.driftFreqX + blob.driftPhaseX)
                        )
                    val cy = size.height * (
                        blob.anchorY + blob.driftAmpY *
                            cos(t * blob.driftFreqY + blob.driftPhaseY)
                        )
                    val radiusPx = size.width * blob.radius

                    // The staged cycles (both are 0 at their wrap boundary,
                    // so the (t/period + phase) mod 1 wrap is seamless).
                    val morph = stagedPulse(((t / blob.morphPeriod) + blob.morphPhase) % 1f)
                    val split = stagedPulse(((t / blob.splitPeriod) + blob.splitPhase) % 1f)

                    // The polygon's own slow rotation + the split axis's
                    // slow precession (both continuous functions of t).
                    val polyRotation = t * 0.13f + blob.polyRotation0
                    val axis = blob.splitAngle + t * 0.045f
                    val separation = radiusPx * blob.splitSpread * split
                    val sepX = cos(axis) * separation
                    val sepY = sin(axis) * separation * 0.8f

                    translate(left = cx, top = cy) {
                        // Child A — always drawn (this IS the blob while merged).
                        drawFlowChild(
                            path = art.paths[i * 2],
                            xs = art.xs,
                            ys = art.ys,
                            radiusPx = radiusPx,
                            wobbleAmp = blob.wobbleAmp,
                            wobbleFreqA = blob.wobbleFreqA,
                            wobbleFreqB = blob.wobbleFreqB,
                            wobblePhaseA = blob.wobblePhaseA,
                            wobblePhaseB = blob.wobblePhaseB,
                            morph = morph,
                            polygonSides = blob.polygonSides,
                            polygonRound = blob.polygonRound,
                            polyRotation = polyRotation,
                            t = t,
                            shrink = split,
                            brush = brushes[i],
                            offsetX = sepX,
                            offsetY = sepY,
                        )
                        // Child B — drawn only once the split is visible. At
                        // the moment it appears its wobble phase shift is
                        // ~0.011 rad and its center sits exactly on child A:
                        // it is INVISIBLE at birth and diverges continuously
                        // as the halves separate. No pop.
                        if (split > 0.02f) {
                            drawFlowChild(
                                path = art.paths[i * 2 + 1],
                                xs = art.xs,
                                ys = art.ys,
                                radiusPx = radiusPx,
                                wobbleAmp = blob.wobbleAmp,
                                wobbleFreqA = blob.wobbleFreqA,
                                wobbleFreqB = blob.wobbleFreqB,
                                // The phase divergence grows WITH the split —
                                // at full separation the halves undulate
                                // independently; at the merge they are
                                // identical again.
                                wobblePhaseA = blob.wobblePhaseA + 0.55f * split,
                                wobblePhaseB = blob.wobblePhaseB + 0.40f * split,
                                morph = morph,
                                polygonSides = blob.polygonSides,
                                polygonRound = blob.polygonRound,
                                polyRotation = polyRotation,
                                t = t,
                                shrink = split,
                                brush = brushes[i],
                                offsetX = -sepX,
                                offsetY = -sepY,
                            )
                        }
                    }
                }

                // Layer 2 — the rotating outline geometry (structured depth).
                // Rotation is (t · speed) mod 360 — visually periodic, so the
                // mod boundary is seamless.
                outlines.forEach { shape ->
                    val cx = size.width * shape.anchorX
                    val cy = size.height * shape.anchorY
                    val rotationDeg = (t * shape.rotationSpeedDegPerSec) % 360f + shape.rotation0
                    val strokePx = shape.strokeWidthDp.dp.toPx()
                    rotate(degrees = rotationDeg, pivot = Offset(cx, cy)) {
                        when (shape.kind) {
                            OutlineKind.CIRCLE -> drawCircle(
                                color = shape.color.copy(alpha = shape.alpha),
                                radius = size.width * shape.radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                            OutlineKind.SQUARE -> drawRoundRect(
                                color = shape.color.copy(alpha = shape.alpha),
                                topLeft = Offset(
                                    cx - size.width * shape.radius,
                                    cy - size.width * shape.radius,
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width * shape.radius * 2f,
                                    size.width * shape.radius * 2f,
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    size.width * shape.radius * 0.35f,
                                ),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// The draw math (all pure functions, zero allocation)
// ─────────────────────────────────────────────────────────────────────────────

private const val TWO_PI = (2.0 * PI).toFloat()

/** The number of silhouette sample points (≥ 4 per polygon side + slack). */
private const val SAMPLE_POINTS = 24

/**
 * The staged 0→1→0 pulse of one cycle, normalized u ∈ [0, 1):
 * hold-low (30%) → smooth rise (12%) → hold-high (20%) → smooth fall (12%)
 * → hold-low (26%). f(0) == f(1) == 0, so the cycle boundary is seamless —
 * the phase mod 1 wrap can never produce a jump.
 */
private fun stagedPulse(u: Float): Float = when {
    u < 0.30f -> 0f
    u < 0.42f -> smooth01((u - 0.30f) / 0.12f)
    u < 0.62f -> 1f
    u < 0.74f -> 1f - smooth01((u - 0.62f) / 0.12f)
    else -> 0f
}

/** Hermite smoothstep — C1-continuous easing for the staged pulses. */
private fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The radius of a REGULAR POLYGON at angle [theta] (relative to its
 * circumradius, which is 1). Sharp corners; blend the result with the
 * circle (1.0) by `1 - polygonRound` for the soft-cornered look.
 */
private fun polygonRadius(theta: Float, sides: Int, rotation: Float): Float {
    val seg = TWO_PI / sides
    var a = (theta - rotation) % seg
    if (a < 0f) a += seg
    val cosHalfSeg = cos(PI.toFloat() / sides)
    return cosHalfSeg / cos(a - seg * 0.5f)
}

/**
 * Renders ONE blob half into its (pre-allocated, reused) [path] and draws
 * it with [brush], offset by ([offsetX], [offsetY]) inside the caller's
 * already-translated scope. The silhouette radius at each sample angle:
 *
 * ```
 * r(θ) = R · lerp(organic(θ, t), roundedPolygon(θ), morph)
 * ```
 *
 * where `organic` is two spatial harmonics drifting in time (the blob
 * "breathes" unevenly around its rim) and `roundedPolygon` is the
 * regular-polygon radius blended toward the circle. [shrink] eases the
 * half slightly smaller as the split widens (a true split, not a
 * duplication). ZERO allocations: the sample arrays and the Path are the
 * caller-owned reused storage.
 */
private fun DrawScope.drawFlowChild(
    path: Path,
    xs: FloatArray,
    ys: FloatArray,
    radiusPx: Float,
    wobbleAmp: Float,
    wobbleFreqA: Float,
    wobbleFreqB: Float,
    wobblePhaseA: Float,
    wobblePhaseB: Float,
    morph: Float,
    polygonSides: Int,
    polygonRound: Float,
    polyRotation: Float,
    t: Float,
    shrink: Float,
    brush: Brush,
    offsetX: Float,
    offsetY: Float,
) {
    val r = radiusPx * (1f - 0.22f * shrink)
    val polyMix = 1f - polygonRound
    for (i in 0 until SAMPLE_POINTS) {
        val theta = TWO_PI * i / SAMPLE_POINTS
        val organic = 1f + wobbleAmp * (
            0.6f * sin(theta * 2f + wobblePhaseA + t * wobbleFreqA) +
                0.4f * sin(theta * 3f + wobblePhaseB + t * wobbleFreqB)
            )
        val poly = 1f + (polygonRadius(theta, polygonSides, polyRotation) - 1f) * polyMix
        val radius = r * (organic + (poly - organic) * morph)
        xs[i] = radius * cos(theta)
        ys[i] = radius * sin(theta)
    }
    buildClosedPath(path, xs, ys)
    translate(left = offsetX, top = offsetY) {
        drawPath(path = path, brush = brush)
    }
}

/**
 * Builds a smooth CLOSED cubic path through the sample points
 * (Catmull-Rom-style: each segment's control points derive from the
 * neighbors — the curve passes THROUGH every point with C1 continuity and
 * closes seamlessly) INTO the caller's reused [Path] (reset + refill —
 * no allocation).
 */
private fun buildClosedPath(path: Path, xs: FloatArray, ys: FloatArray) {
    val n = xs.size
    path.reset()
    path.moveTo(xs[0], ys[0])
    for (i in 0 until n) {
        val i0 = (i - 1 + n) % n
        val i1 = i
        val i2 = (i + 1) % n
        val i3 = (i + 2) % n
        val c1x = xs[i1] + (xs[i2] - xs[i0]) / 6f
        val c1y = ys[i1] + (ys[i2] - ys[i0]) / 6f
        val c2x = xs[i2] - (xs[i3] - xs[i1]) / 6f
        val c2y = ys[i2] - (ys[i3] - ys[i1]) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, xs[i2], ys[i2])
    }
    path.close()
}

// ─────────────────────────────────────────────────────────────────────────────
// The stable (seeded) art parameters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One flowing blob's full parameter set — every value is a STABLE seed
 * (remembered once); only the time phases move at draw time.
 */
private class FlowBlob(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
    // The center's continuous Lissajous orbit.
    val driftAmpX: Float,
    val driftFreqX: Float,
    val driftPhaseX: Float,
    val driftAmpY: Float,
    val driftFreqY: Float,
    val driftPhaseY: Float,
    // The organic silhouette wobble (two spatial harmonics).
    val wobbleAmp: Float,
    val wobbleFreqA: Float,
    val wobbleFreqB: Float,
    val wobblePhaseA: Float,
    val wobblePhaseB: Float,
    // The shape it morphs into.
    val polygonSides: Int,
    val polygonRound: Float,
    val polyRotation0: Float,
    // The morph cycle's timing.
    val morphPeriod: Float,
    val morphPhase: Float,
    // The split cycle's timing + axis.
    val splitPeriod: Float,
    val splitPhase: Float,
    val splitAngle: Float,
    val splitSpread: Float,
)

/**
 * The pre-allocated, reused draw storage + the brush cache. One instance
 * per accent (rebuilt only when the theme's accent changes); the brushes
 * additionally rebuild themselves when the canvas width changes (the only
 * other input to their geometry).
 */
private class BlobArt(
    val blobs: List<FlowBlob>,
) {
    /** Two pre-allocated Paths per blob (child A + child B). */
    val paths: Array<Path> = Array(blobs.size * 2) { Path() }

    /** Reused per-child sample storage (children are built sequentially). */
    val xs = FloatArray(SAMPLE_POINTS)
    val ys = FloatArray(SAMPLE_POINTS)

    private var cachedWidth = -1f
    private var cachedBrushes: List<Brush>? = null

    /**
     * The per-blob radial brushes (centered at Offset.Zero — the caller
     * translates the draw scope to each blob's center, so the SAME brush
     * serves both split halves). Cached; rebuilt only on a width change.
     */
    fun brushesFor(width: Float): List<Brush> {
        val cached = cachedBrushes
        if (cached != null && cachedWidth == width) return cached
        val fresh = blobs.map { blob ->
            Brush.radialGradient(
                colors = listOf(
                    blob.color.copy(alpha = blob.alpha),
                    blob.color.copy(alpha = blob.alpha * 0.45f),
                    Color.Transparent,
                ),
                center = Offset.Zero,
                radius = width * blob.radius * 1.5f,
            )
        }
        cachedBrushes = fresh
        cachedWidth = width
        return fresh
    }
}

/**
 * The blob palette (unchanged from round 29 — the round-30 report was happy
 * with the colors): the LIVE accent, electric violet, rose, warm amber, and
 * teal, layered over the deep base. The per-blob morph/split periods and
 * phases are staggered so shapes transform and halves separate at
 * DIFFERENT moments — "would split sometimes", never in unison.
 */
private fun buildFlowBlobs(accent: Color): List<FlowBlob> = listOf(
    FlowBlob(
        anchorX = 0.24f, anchorY = 0.26f, radius = 0.30f, alpha = 0.30f,
        color = accent,
        driftAmpX = 0.045f, driftFreqX = 0.060f, driftPhaseX = 0.0f,
        driftAmpY = 0.035f, driftFreqY = 0.048f, driftPhaseY = 1.1f,
        wobbleAmp = 0.16f, wobbleFreqA = 0.26f, wobbleFreqB = 0.38f,
        wobblePhaseA = 0.0f, wobblePhaseB = 2.1f,
        polygonSides = 6, polygonRound = 0.45f, polyRotation0 = 0.0f,
        morphPeriod = 16f, morphPhase = 0.0f,
        splitPeriod = 22f, splitPhase = 0.15f, splitAngle = 0.4f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.80f, anchorY = 0.22f, radius = 0.26f, alpha = 0.26f,
        color = Color(0xFF8B5CF6), // electric violet
        driftAmpX = 0.050f, driftFreqX = 0.050f, driftPhaseX = 1.3f,
        driftAmpY = 0.040f, driftFreqY = 0.055f, driftPhaseY = 0.4f,
        wobbleAmp = 0.18f, wobbleFreqA = 0.30f, wobbleFreqB = 0.22f,
        wobblePhaseA = 1.0f, wobblePhaseB = 0.3f,
        polygonSides = 3, polygonRound = 0.55f, polyRotation0 = 0.5f,
        morphPeriod = 19f, morphPhase = 0.45f,
        splitPeriod = 26f, splitPhase = 0.62f, splitAngle = 2.1f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.62f, anchorY = 0.66f, radius = 0.33f, alpha = 0.24f,
        color = Color(0xFFFF5C8A), // rose
        driftAmpX = 0.060f, driftFreqX = 0.043f, driftPhaseX = 2.4f,
        driftAmpY = 0.050f, driftFreqY = 0.038f, driftPhaseY = 0.9f,
        wobbleAmp = 0.14f, wobbleFreqA = 0.24f, wobbleFreqB = 0.34f,
        wobblePhaseA = 2.8f, wobblePhaseB = 1.4f,
        polygonSides = 4, polygonRound = 0.50f, polyRotation0 = 0.9f,
        morphPeriod = 21f, morphPhase = 0.70f,
        splitPeriod = 24f, splitPhase = 0.33f, splitAngle = 3.6f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.18f, anchorY = 0.78f, radius = 0.27f, alpha = 0.22f,
        color = Color(0xFFF59E0B), // warm amber
        driftAmpX = 0.040f, driftFreqX = 0.055f, driftPhaseX = 0.7f,
        driftAmpY = 0.045f, driftFreqY = 0.050f, driftPhaseY = 2.2f,
        wobbleAmp = 0.19f, wobbleFreqA = 0.28f, wobbleFreqB = 0.20f,
        wobblePhaseA = 0.5f, wobblePhaseB = 3.0f,
        polygonSides = 5, polygonRound = 0.55f, polyRotation0 = 1.7f,
        morphPeriod = 17.5f, morphPhase = 0.85f,
        splitPeriod = 28f, splitPhase = 0.50f, splitAngle = 5.2f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.48f, anchorY = 0.12f, radius = 0.22f, alpha = 0.20f,
        color = Color(0xFF2DD4BF), // teal
        driftAmpX = 0.055f, driftFreqX = 0.050f, driftPhaseX = 3.1f,
        driftAmpY = 0.030f, driftFreqY = 0.060f, driftPhaseY = 1.7f,
        wobbleAmp = 0.17f, wobbleFreqA = 0.32f, wobbleFreqB = 0.25f,
        wobblePhaseA = 1.8f, wobblePhaseB = 0.9f,
        polygonSides = 3, polygonRound = 0.60f, polyRotation0 = 2.6f,
        morphPeriod = 15f, morphPhase = 0.30f,
        splitPeriod = 20f, splitPhase = 0.80f, splitAngle = 1.2f, splitSpread = 0.85f,
    ),
)

/** The rotating outline geometry layer (unchanged from round 29). */
private data class OutlineShape(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val alpha: Float,
    /** Degrees per second — continuous rotation, mod-360 seamless. */
    val rotationSpeedDegPerSec: Float,
    val rotation0: Float,
    val color: Color,
    val kind: OutlineKind,
    val strokeWidthDp: Float,
)

private enum class OutlineKind { CIRCLE, SQUARE }

private fun buildOutlineShapes(): List<OutlineShape> = listOf(
    OutlineShape(
        anchorX = 0.84f, anchorY = 0.74f, radius = 0.11f, alpha = 0.14f,
        rotationSpeedDegPerSec = 11f, rotation0 = 0f,
        color = Color(0xFFB9A8FF), kind = OutlineKind.CIRCLE, strokeWidthDp = 1.5f,
    ),
    OutlineShape(
        anchorX = 0.10f, anchorY = 0.44f, radius = 0.07f, alpha = 0.12f,
        rotationSpeedDegPerSec = -8f, rotation0 = 30f,
        color = Color(0xFF2DD4BF), kind = OutlineKind.SQUARE, strokeWidthDp = 1.5f,
    ),
    OutlineShape(
        anchorX = 0.55f, anchorY = 0.86f, radius = 0.16f, alpha = 0.08f,
        rotationSpeedDegPerSec = 5f, rotation0 = 0f,
        color = Color.White, kind = OutlineKind.CIRCLE, strokeWidthDp = 1.0f,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// The wordmark + the rotating tagline (unchanged — approved by the device
// reports of rounds 29 and 30)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-405 (round 29): the ROTATING TAGLINE — the welcome's tagline smoothly
 * crossfades + slides through a set of lines every ~3.6s (the device
 * report: the tagline "should be changing smoothly to other taglines").
 *
 * AnimatedContent gives the outgoing line a gentle upward exit while the
 * incoming one settles in from below — the emphasized curve, matching the
 * wizard's motion language. The slot height is stable (the text is always
 * one line), so nothing below the tagline jumps.
 */
@Composable
internal fun RotatingTagline(
    taglines: List<String>,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    if (taglines.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(taglines) {
        while (true) {
            kotlinx.coroutines.delay(3_600L)
            index = (index + 1) % taglines.size
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (
                    fadeIn(tween(420, easing = Motion.EasingEmphasized)) +
                        slideInVertically(
                            animationSpec = tween(420, easing = Motion.EasingEmphasized),
                            initialOffsetY = { it / 3 },
                        )
                    ) togetherWith (
                    fadeOut(tween(240)) +
                        slideOutVertically(
                            animationSpec = tween(240),
                            targetOffsetY = { -it / 3 },
                        )
                    )
            },
            label = "onboardingTaglineRotation",
        ) { i ->
            Text(
                text = taglines[i.coerceIn(taglines.indices)],
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The staggered letter-by-letter wordmark reveal — each glyph slides up +
 * fades in on its own 90ms delay on the emphasized curve. The tail ("KUTA")
 * renders in the live accent color so the wordmark also re-colors with the
 * theme selection.
 *
 * [onFinished] fires once the last letter has landed (the caller chains the
 * tagline + CTA entrances after it).
 */
@Composable
internal fun StaggeredWordmark(
    text: String,
    accent: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    var revealed by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        revealed = 0
        text.forEachIndexed { index, _ ->
            kotlinx.coroutines.delay(90L)
            revealed = index + 1
        }
        onFinished()
    }
    Row(modifier = modifier) {
        text.forEachIndexed { index, ch ->
            AnimatedVisibility(
                visible = index < revealed,
                enter = slideInVertically(
                    animationSpec = tween(360, easing = Motion.EasingEmphasized),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(tween(360, easing = Motion.EasingEmphasized)),
            ) {
                Text(
                    text = ch.toString(),
                    fontFamily = RobotoFamily,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = TextUnit(1.5f, TextUnitType.Sp),
                    color = if (index >= text.length - 4) accent else textColor,
                )
            }
        }
    }
}
