package com.confused.anikuta.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
 * D-405 (round 29): the CUSTOM animated art of the wizard's welcome screen —
 * deliberately NOT Material-styled (the user's spec: "a custom modern
 * beautiful-looking UI design", not "material in three expressive").
 *
 * ## The round-29 rework
 * The v0.4.16 welcome used a radial-glow AURORA (six soft circles) + a
 * 28-particle field drifting upward. The device report: "I was not satisfied
 * with the overall background animations… What I wanted was some animated
 * shapes in the background, some animated kind of blobs moving around, and
 * other stuff like that, with some different colors." The art is now built
 * from actual SHAPES:
 *
 *  1. **THE MORPHING BLOBS** — five large ORGANIC shapes whose SILHOUETTES
 *     continuously morph (8 control points per blob, each with its own
     * wobble amplitude/speed/phase — a closed Catmull-style cubic path) and
 *     whose centers drift around the canvas on a slow orbit. Each fills with
 *     a soft radial gradient (its color fading to transparent) so the shapes
 *     layer into each other without hard seams.
 *  2. **THE GEOMETRY LAYER** — three slowly rotating outline shapes (two
 *     circles, one rounded square — "other stuff like that") at hairline
 *     stroke weights and low alpha, adding structured depth over the organic
 *     blobs.
 *
 * Everything is drawn in ONE `drawBehind` pass (zero recomposition per
 * frame — the whole animation is draw-phase). The layout is
 * seeded-parameterized and REMEMBERED — the art is stable across
 * recompositions; only the time phases move. The accent blob recolors LIVE
 * with the theme selection.
 */
@Composable
internal fun OnboardingBlobBackground(
    accent: Color,
    base: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "onboarding_blobs")
    // Phase A — the MORPH phase: drives every control point's wobble
    // (each point has its own speed multiplier, so the silhouettes never
    // lock into a synchronized pulse).
    val morphPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing)),
        label = "onboardingBlobMorph",
    )
    // Phase B — the DRIFT phase: the slow orbital motion of each blob's
    // center (and the geometry layer's rotation).
    val driftPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "onboardingBlobDrift",
    )

    val blobs = remember(accent) { buildMorphingBlobs(accent) }
    val outlines = remember { buildOutlineShapes() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Layer 0 — the deep base.
                drawRect(color = base)

                // Layer 1 — the morphing organic blobs.
                blobs.forEach { blob ->
                    // The blob's drifting center (a slow Lissajous orbit —
                    // it never retraces the same path).
                    val cx = size.width * (
                        blob.anchorX + blob.driftAmp *
                            sin(driftPhase * blob.driftSpeed + blob.phase0)
                        )
                    val cy = size.height * (
                        blob.anchorY + blob.driftAmp * 0.7f *
                            cos(driftPhase * blob.driftSpeed * 1.3f + blob.phase0)
                        )
                    val baseRadius = size.width * blob.radius
                    // The blob's silhouette: 8 control points, each wobbling
                    // its radius independently — the shape BREATHES.
                    val points = Array(8) { i ->
                        val angle = (2f * PI.toFloat() * i / 8f) + blob.rotation0
                        val wobble = sin(
                            morphPhase * blob.wobbleSpeeds[i] + blob.wobblePhases[i],
                        )
                        val r = baseRadius * (1f + blob.wobbleAmp * wobble)
                        Offset(
                            x = cx + r * cos(angle),
                            y = cy + r * sin(angle),
                        )
                    }
                    val path = smoothClosedPath(points)
                    // Soft organic fill — the color fades to transparent at
                    // the silhouette's bounding radius, so overlapping blobs
                    // blend (no hard seams).
                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                blob.color.copy(alpha = blob.alpha),
                                blob.color.copy(alpha = blob.alpha * 0.45f),
                                Color.Transparent,
                            ),
                            center = Offset(cx, cy),
                            radius = baseRadius * 1.5f,
                        ),
                    )
                }

                // Layer 2 — the rotating outline geometry (structured depth).
                outlines.forEach { shape ->
                    val cx = size.width * shape.anchorX
                    val cy = size.height * shape.anchorY
                    val rotationDeg = 360f * shape.rotationSpeed * driftPhase + shape.rotation0
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

/**
 * Builds a smooth CLOSED cubic path through [points] (Catmull-Rom-style:
 * each segment's control points derive from the neighbors — the curve
 * passes THROUGH every point with C1 continuity, closing seamlessly).
 */
private fun smoothClosedPath(points: Array<Offset>): Path {
    val path = Path()
    val n = points.size
    if (n < 3) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        // Catmull-Rom → cubic Bézier control points.
        val c1 = Offset(
            x = p1.x + (p2.x - p0.x) / 6f,
            y = p1.y + (p2.y - p0.y) / 6f,
        )
        val c2 = Offset(
            x = p2.x - (p3.x - p1.x) / 6f,
            y = p2.y - (p3.y - p1.y) / 6f,
        )
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.close()
    return path
}

/** One morphing blob's (stable, seeded) layout + its morph/drift parameters. */
private data class MorphBlob(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val alpha: Float,
    val wobbleAmp: Float,
    val wobbleSpeeds: FloatArray,
    val wobblePhases: FloatArray,
    val driftAmp: Float,
    val driftSpeed: Float,
    val phase0: Float,
    val rotation0: Float,
    val color: Color,
)

/**
 * The blob palette (round 29 — "the colors were looking good but could be
 * improved"): a RICHER jewel set on a deeper base — live accent, electric
 * violet, rose, warm amber, and teal — layered violet-over-rose for depth.
 */
private fun buildMorphingBlobs(accent: Color): List<MorphBlob> = listOf(
    MorphBlob(
        anchorX = 0.22f, anchorY = 0.26f, radius = 0.30f, alpha = 0.30f,
        wobbleAmp = 0.22f,
        wobbleSpeeds = floatArrayOf(1.0f, 1.7f, 1.3f, 2.1f, 0.9f, 1.5f, 1.9f, 1.2f),
        wobblePhases = floatArrayOf(0.0f, 1.1f, 2.3f, 3.0f, 4.1f, 5.0f, 0.7f, 2.0f),
        driftAmp = 0.05f, driftSpeed = 1.0f, phase0 = 0.0f, rotation0 = 0.0f,
        color = accent,
    ),
    MorphBlob(
        anchorX = 0.80f, anchorY = 0.20f, radius = 0.26f, alpha = 0.26f,
        wobbleAmp = 0.25f,
        wobbleSpeeds = floatArrayOf(1.4f, 0.8f, 1.9f, 1.1f, 2.2f, 1.0f, 1.6f, 0.9f),
        wobblePhases = floatArrayOf(0.5f, 2.1f, 4.2f, 1.0f, 3.3f, 5.4f, 2.7f, 0.2f),
        driftAmp = 0.06f, driftSpeed = 1.4f, phase0 = 1.8f, rotation0 = 0.4f,
        color = Color(0xFF8B5CF6), // electric violet
    ),
    MorphBlob(
        anchorX = 0.66f, anchorY = 0.64f, radius = 0.34f, alpha = 0.24f,
        wobbleAmp = 0.20f,
        wobbleSpeeds = floatArrayOf(0.9f, 1.6f, 1.2f, 2.0f, 1.4f, 0.8f, 1.8f, 1.1f),
        wobblePhases = floatArrayOf(3.1f, 0.9f, 5.0f, 2.2f, 1.3f, 4.4f, 0.1f, 3.8f),
        driftAmp = 0.07f, driftSpeed = 0.8f, phase0 = 3.4f, rotation0 = 0.9f,
        color = Color(0xFFFF5C8A), // rose
    ),
    MorphBlob(
        anchorX = 0.16f, anchorY = 0.78f, radius = 0.28f, alpha = 0.22f,
        wobbleAmp = 0.28f,
        wobbleSpeeds = floatArrayOf(1.8f, 1.1f, 2.3f, 0.9f, 1.5f, 2.0f, 1.2f, 1.7f),
        wobblePhases = floatArrayOf(1.9f, 4.0f, 0.6f, 3.2f, 5.1f, 1.4f, 2.8f, 0.3f),
        driftAmp = 0.05f, driftSpeed = 1.2f, phase0 = 5.1f, rotation0 = 1.7f,
        color = Color(0xFFF59E0B), // warm amber
    ),
    MorphBlob(
        anchorX = 0.46f, anchorY = 0.10f, radius = 0.22f, alpha = 0.20f,
        wobbleAmp = 0.24f,
        wobbleSpeeds = floatArrayOf(1.2f, 2.0f, 0.9f, 1.6f, 2.1f, 1.0f, 1.4f, 1.9f),
        wobblePhases = floatArrayOf(4.5f, 1.2f, 3.0f, 0.4f, 2.6f, 5.3f, 1.8f, 4.1f),
        driftAmp = 0.06f, driftSpeed = 1.6f, phase0 = 2.2f, rotation0 = 2.6f,
        color = Color(0xFF2DD4BF), // teal
    ),
)

/** The rotating outline geometry layer. */
private data class OutlineShape(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val alpha: Float,
    val rotationSpeed: Float,
    val rotation0: Float,
    val color: Color,
    val kind: OutlineKind,
    val strokeWidthDp: Float,
)

private enum class OutlineKind { CIRCLE, SQUARE }

private fun buildOutlineShapes(): List<OutlineShape> = listOf(
    OutlineShape(
        anchorX = 0.84f, anchorY = 0.74f, radius = 0.11f, alpha = 0.14f,
        rotationSpeed = 0.30f, rotation0 = 0f,
        color = Color(0xFFB9A8FF), kind = OutlineKind.CIRCLE, strokeWidthDp = 1.5f,
    ),
    OutlineShape(
        anchorX = 0.10f, anchorY = 0.44f, radius = 0.07f, alpha = 0.12f,
        rotationSpeed = -0.22f, rotation0 = 30f,
        color = Color(0xFF2DD4BF), kind = OutlineKind.SQUARE, strokeWidthDp = 1.5f,
    ),
    OutlineShape(
        anchorX = 0.55f, anchorY = 0.86f, radius = 0.16f, alpha = 0.08f,
        rotationSpeed = 0.12f, rotation0 = 0f,
        color = Color.White, kind = OutlineKind.CIRCLE, strokeWidthDp = 1.0f,
    ),
)

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
