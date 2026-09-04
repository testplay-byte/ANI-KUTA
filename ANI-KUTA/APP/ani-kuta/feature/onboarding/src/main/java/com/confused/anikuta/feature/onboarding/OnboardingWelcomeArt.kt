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
import androidx.compose.ui.graphics.BlendMode
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
 * D-405 → D-406 → D-407 (round 31): the CUSTOM animated art of the wizard's
 * welcome screen — deliberately NOT Material-styled (the user's spec: "a custom
 * modern beautiful-looking UI design").
 *
 * ## D-407: the round-31 report's two asks
 * *"When they combine together with each other, they suddenly change their
 * shades… The globes can transform into different shapes and get merged with
 * the different random places and such, not a simple fixed path."*
 *
 * 1. **THE MERGE SHADE-POP** — the D-406 split drew child B only once
 *    `split > 0.02`, exactly on top of child A, with the SAME brush alpha →
 *    the two stacked gradients composite to a visibly different shade the
 *    instant B appears (and snap back at the merge) — the "suddenly change
 *    their shades when they combine". Fixed TWICE over:
 *    - the whole blob layer now composites with **[BlendMode.Screen]** —
 *      overlapping blobs blend like light (a smooth brightening where they
 *      meet, the liquid-merge look) instead of srcOver's dominance stacking;
 *    - child B is alpha-ramped with the split itself (`alpha ∝ split`), so it
 *      contributes literally nothing at the birth/merge moment and grows in
 *      continuously — no pop is mathematically possible.
 *
 * 2. **THE FIXED PATHS** — D-406's centers rode ONE Lissajous orbit each:
 *    periodic, predictable, "a simple fixed path". D-407 sums THREE
 *    incommensurate sinusoids per axis (amplitudes ~0.10/0.06/0.04 of the
 *    canvas, frequencies at irrational ratios) — the wander never retraces,
 *    never repeats, and the blobs genuinely cross paths at DIFFERENT screen
 *    locations over time ("merged with the different random places").
 *
 * 3. **SHAPE SEQUENCES** — each blob now cycles through a LIST of polygon
 *    silhouettes (hexagon → triangle → pentagon → square → …) on a staged
 *    crossfade (hold → blend → hold, the boundary is seamless because the
 *    blend end-point and the next segment's start are the SAME pure polygon).
 *    The blobs keep morphing organic ↔ shaped as before, but no longer into
 *    the same shape forever — they "transform into different shapes".
 *
 * 4. **RADIUS BREATHING** — a slow per-blob scale pulse so even a blob at a
 *    shape-hold moment never looks frozen.
 *
 * Everything the reports APPROVED is untouched: the single monotonic clock
 * (no phase wraps — frame-nano deltas clamped to 64ms so backgrounding PAUSES
 * the art), ZERO steady-state allocations (pre-allocated [Path]s + reused
 * FloatArrays + cached radial brushes), the outline geometry layer, the
 * approved palette, and the draw-phase-only clock read (one invalidation per
 * frame — no recompositions, no remeasures).
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
    // per frame.
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

                // Layer 1 — the morphing, splitting, wandering organic blobs.
                // D-407: composited with Screen so overlaps BLEND like light
                // (the smooth-merge look) — see the header notes.
                val brushes = art.brushesFor(size.width)
                art.blobs.forEachIndexed { i, blob ->
                    // The wandering center — THREE incommensurate harmonics
                    // per axis (never retraces, never resets, blobs cross at
                    // ever-different places).
                    val cx = size.width * (
                        blob.anchorX +
                            blob.wanderX[0].let { it.amp * sin(t * it.freq + it.phase) } +
                            blob.wanderX[1].let { it.amp * sin(t * it.freq + it.phase) } +
                            blob.wanderX[2].let { it.amp * cos(t * it.freq + it.phase) }
                        )
                    val cy = size.height * (
                        blob.anchorY +
                            blob.wanderY[0].let { it.amp * sin(t * it.freq + it.phase) } +
                            blob.wanderY[1].let { it.amp * cos(t * it.freq + it.phase) } +
                            blob.wanderY[2].let { it.amp * sin(t * it.freq + it.phase) }
                        )

                    // The breathing radius — a slow, continuous scale pulse.
                    val breathe = 1f + blob.breatheAmp *
                        sin(t * blob.breatheFreq + blob.breathePhase)
                    val radiusPx = size.width * blob.radius * breathe

                    // The staged cycles (both are 0 at their wrap boundary,
                    // so the (t/period + phase) mod 1 wrap is seamless).
                    val morph = stagedPulse(((t / blob.morphPeriod) + blob.morphPhase) % 1f)
                    val split = stagedPulse(((t / blob.splitPeriod) + blob.splitPhase) % 1f)

                    // The shape sequence — which pair of polygons this blob is
                    // currently crossfading between (the boundary is seamless:
                    // segment end at mix=1 == next segment start at mix=0).
                    val shapePhase = (t / blob.shapePeriod + blob.shapePhase) %
                        blob.shapeSides.size
                    val shapeIdx = shapePhase.toInt().mod(blob.shapeSides.size)
                    val nextIdx = (shapeIdx + 1).mod(blob.shapeSides.size)
                    val shapeMix = stagedShapeMix(shapePhase - shapeIdx)

                    // The polygons' own slow rotation + the split axis's
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
                            polygonSides = blob.shapeSides[shapeIdx],
                            nextPolygonSides = blob.shapeSides[nextIdx],
                            shapeMix = shapeMix,
                            polygonRound = blob.polygonRound,
                            polyRotation = polyRotation,
                            t = t,
                            shrink = split,
                            brush = brushes[i],
                            alpha = 1f,
                            offsetX = sepX,
                            offsetY = sepY,
                        )
                        // Child B — drawn with an alpha that RAMPS with the
                        // split itself: at the birth/merge moment it
                        // contributes nothing (alpha 0), growing in
                        // continuously as the halves separate — the D-407
                        // fix for "they suddenly change their shades".
                        val childAlpha = ((split - 0.02f) / 0.28f).coerceIn(0f, 1f)
                        if (childAlpha > 0.003f) {
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
                                polygonSides = blob.shapeSides[shapeIdx],
                                nextPolygonSides = blob.shapeSides[nextIdx],
                                shapeMix = shapeMix,
                                polygonRound = blob.polygonRound,
                                polyRotation = polyRotation,
                                t = t,
                                shrink = split,
                                brush = brushes[i],
                                alpha = childAlpha,
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

/**
 * D-407: the staged crossfade mix between two consecutive shapes of a blob's
 * shape sequence, normalized u ∈ [0, 1) within ONE segment:
 * hold-current (55%) → smooth blend (25%) → hold-next (20%).
 * f(0)=0 and f(1)=1, and the NEXT segment starts at mix 0 with the shape this
 * one ends on — the silhouette is continuous across the segment boundary.
 */
private fun stagedShapeMix(u: Float): Float = when {
    u < 0.55f -> 0f
    u < 0.80f -> smooth01((u - 0.55f) / 0.25f)
    else -> 1f
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
 * it with [brush] at [alpha], offset by ([offsetX], [offsetY]) inside the
 * caller's already-translated scope. The silhouette radius at each sample
 * angle:
 *
 * ```
 * r(θ) = R · lerp( organic(θ, t),
 *                   lerp(poly(θ, sidesA), poly(θ, sidesB), shapeMix),
 *                   morph )
 * ```
 *
 * where `organic` is two spatial harmonics drifting in time (the blob
 * "breathes" unevenly around its rim), `sidesA→sidesB` is the shape-sequence
 * crossfade (D-407), and each polygon is itself blended toward the circle by
 * `polygonRound`. [shrink] eases the half slightly smaller as the split
 * widens (a true split, not a duplication). ZERO allocations: the sample
 * arrays and the Path are the caller-owned reused storage.
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
    nextPolygonSides: Int,
    shapeMix: Float,
    polygonRound: Float,
    polyRotation: Float,
    t: Float,
    shrink: Float,
    brush: Brush,
    alpha: Float,
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
        // D-407: the shape-sequence crossfade — polyA blends into polyB by
        // the staged shapeMix (both rotate with the SAME polyRotation, so
        // the crossfade is a pure silhouette morph).
        val polyA = 1f + (polygonRadius(theta, polygonSides, polyRotation) - 1f) * polyMix
        val polyB = 1f + (polygonRadius(theta, nextPolygonSides, polyRotation) - 1f) * polyMix
        val poly = polyA + (polyB - polyA) * shapeMix
        val radius = r * (organic + (poly - organic) * morph)
        xs[i] = radius * cos(theta)
        ys[i] = radius * sin(theta)
    }
    buildClosedPath(path, xs, ys)
    translate(left = offsetX, top = offsetY) {
        // D-407: Screen blend — overlaps blend like light (the liquid-merge
        // look); child B's ramped alpha makes the split birth/merge pop-free.
        drawPath(path = path, brush = brush, alpha = alpha, blendMode = BlendMode.Screen)
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
 * One wander harmonic of a blob's center: `amp · sin/cos(t · freq + phase)`
 * (as a fraction of the canvas dimension it applies to). The three
 * harmonics per axis use INCOMMENSURATE frequencies (irrational ratios) so
 * the summed path never repeats.
 */
private class Wander(
    val amp: Float,
    val freq: Float,
    val phase: Float,
)

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
    // D-407: the center's THREE-harmonic wander per axis (incommensurate).
    val wanderX: List<Wander>,
    val wanderY: List<Wander>,
    // D-407: the slow breathing scale pulse.
    val breatheAmp: Float,
    val breatheFreq: Float,
    val breathePhase: Float,
    // The organic silhouette wobble (two spatial harmonics).
    val wobbleAmp: Float,
    val wobbleFreqA: Float,
    val wobbleFreqB: Float,
    val wobblePhaseA: Float,
    val wobblePhaseB: Float,
    // D-407: the SEQUENCE of shapes the blob transforms through.
    val shapeSides: List<Int>,
    val shapePeriod: Float,
    val shapePhase: Float,
    val polygonRound: Float,
    val polyRotation0: Float,
    // The morph cycle's timing (organic ↔ shaped).
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
 * The blob palette (unchanged since round 29 — the reports were happy with
 * the colors): the LIVE accent, electric violet, rose, warm amber, and teal,
 * layered over the deep base. D-407 changes only the MOTION: three-harmonic
 * wandering (large amplitudes so blobs genuinely meet at different places),
 * shape SEQUENCES (each blob transforms through 3-4 different polygons),
 * and per-blob breathing. Periods/phases stay staggered so no two blobs
 * ever morph, split, or cross in unison.
 */
private fun buildFlowBlobs(accent: Color): List<FlowBlob> = listOf(
    FlowBlob(
        anchorX = 0.26f, anchorY = 0.28f, radius = 0.30f, alpha = 0.30f,
        color = accent,
        wanderX = listOf(
            Wander(0.105f, 0.061f, 0.0f),
            Wander(0.058f, 0.0233f, 1.9f),
            Wander(0.040f, 0.0889f, 3.5f),
        ),
        wanderY = listOf(
            Wander(0.082f, 0.047f, 1.1f),
            Wander(0.052f, 0.0191f, 0.4f),
            Wander(0.036f, 0.0731f, 2.7f),
        ),
        breatheAmp = 0.06f, breatheFreq = 0.09f, breathePhase = 0.3f,
        wobbleAmp = 0.16f, wobbleFreqA = 0.26f, wobbleFreqB = 0.38f,
        wobblePhaseA = 0.0f, wobblePhaseB = 2.1f,
        shapeSides = listOf(6, 3, 5, 4),
        shapePeriod = 23f, shapePhase = 0.0f,
        polygonRound = 0.45f, polyRotation0 = 0.0f,
        morphPeriod = 16f, morphPhase = 0.0f,
        splitPeriod = 22f, splitPhase = 0.15f, splitAngle = 0.4f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.76f, anchorY = 0.24f, radius = 0.26f, alpha = 0.26f,
        color = Color(0xFF8B5CF6), // electric violet
        wanderX = listOf(
            Wander(0.098f, 0.053f, 1.3f),
            Wander(0.061f, 0.0271f, 4.2f),
            Wander(0.034f, 0.0947f, 0.8f),
        ),
        wanderY = listOf(
            Wander(0.086f, 0.043f, 0.4f),
            Wander(0.048f, 0.0217f, 2.6f),
            Wander(0.032f, 0.0779f, 1.5f),
        ),
        breatheAmp = 0.07f, breatheFreq = 0.11f, breathePhase = 2.0f,
        wobbleAmp = 0.18f, wobbleFreqA = 0.30f, wobbleFreqB = 0.22f,
        wobblePhaseA = 1.0f, wobblePhaseB = 0.3f,
        shapeSides = listOf(3, 5, 4, 6),
        shapePeriod = 27f, shapePhase = 0.45f,
        polygonRound = 0.55f, polyRotation0 = 0.5f,
        morphPeriod = 19f, morphPhase = 0.45f,
        splitPeriod = 26f, splitPhase = 0.62f, splitAngle = 2.1f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.58f, anchorY = 0.64f, radius = 0.33f, alpha = 0.24f,
        color = Color(0xFFFF5C8A), // rose
        wanderX = listOf(
            Wander(0.092f, 0.049f, 2.4f),
            Wander(0.055f, 0.0313f, 5.1f),
            Wander(0.038f, 0.0713f, 1.9f),
        ),
        wanderY = listOf(
            Wander(0.088f, 0.038f, 0.9f),
            Wander(0.056f, 0.0173f, 3.3f),
            Wander(0.030f, 0.0831f, 0.2f),
        ),
        breatheAmp = 0.05f, breatheFreq = 0.08f, breathePhase = 4.1f,
        wobbleAmp = 0.14f, wobbleFreqA = 0.24f, wobbleFreqB = 0.34f,
        wobblePhaseA = 2.8f, wobblePhaseB = 1.4f,
        shapeSides = listOf(4, 6, 3, 5),
        shapePeriod = 25f, shapePhase = 0.70f,
        polygonRound = 0.50f, polyRotation0 = 0.9f,
        morphPeriod = 21f, morphPhase = 0.70f,
        splitPeriod = 24f, splitPhase = 0.33f, splitAngle = 3.6f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.22f, anchorY = 0.74f, radius = 0.27f, alpha = 0.22f,
        color = Color(0xFFF59E0B), // warm amber
        wanderX = listOf(
            Wander(0.100f, 0.057f, 0.7f),
            Wander(0.050f, 0.0269f, 2.8f),
            Wander(0.042f, 0.0821f, 4.6f),
        ),
        wanderY = listOf(
            Wander(0.080f, 0.050f, 2.2f),
            Wander(0.054f, 0.0229f, 5.7f),
            Wander(0.034f, 0.0691f, 1.1f),
        ),
        breatheAmp = 0.08f, breatheFreq = 0.10f, breathePhase = 1.2f,
        wobbleAmp = 0.19f, wobbleFreqA = 0.28f, wobbleFreqB = 0.20f,
        wobblePhaseA = 0.5f, wobblePhaseB = 3.0f,
        shapeSides = listOf(5, 4, 6, 3),
        shapePeriod = 29f, shapePhase = 0.85f,
        polygonRound = 0.55f, polyRotation0 = 1.7f,
        morphPeriod = 17.5f, morphPhase = 0.85f,
        splitPeriod = 28f, splitPhase = 0.50f, splitAngle = 5.2f, splitSpread = 0.85f,
    ),
    FlowBlob(
        anchorX = 0.50f, anchorY = 0.14f, radius = 0.22f, alpha = 0.20f,
        color = Color(0xFF2DD4BF), // teal
        wanderX = listOf(
            Wander(0.108f, 0.064f, 3.1f),
            Wander(0.046f, 0.0287f, 1.0f),
            Wander(0.038f, 0.0923f, 5.3f),
        ),
        wanderY = listOf(
            Wander(0.078f, 0.060f, 1.7f),
            Wander(0.044f, 0.0241f, 4.4f),
            Wander(0.034f, 0.0757f, 2.9f),
        ),
        breatheAmp = 0.09f, breatheFreq = 0.12f, breathePhase = 3.4f,
        wobbleAmp = 0.17f, wobbleFreqA = 0.32f, wobbleFreqB = 0.25f,
        wobblePhaseA = 1.8f, wobblePhaseB = 0.9f,
        shapeSides = listOf(3, 4, 5, 6),
        shapePeriod = 21f, shapePhase = 0.30f,
        polygonRound = 0.60f, polyRotation0 = 2.6f,
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
