package com.confused.anikuta.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * D-403 (round 28): the CUSTOM animated art of the wizard's welcome screen —
 * deliberately NOT Material-styled (the user's spec: "a custom modern
 * beautiful-looking UI design", not "material in three expressive").
 *
 * Three layers, all drawn in ONE `drawBehind` pass (zero recomposition per
 * frame — the whole animation is draw-phase):
 *  1. the AURORA — six soft radial-gradient blobs drifting around their
 *     anchors on a shared 16s phase (the accent-seeded palette recolors LIVE
 *     with the wizard's theme selection — colorScheme.primary is the seed);
 *  2. the PARTICLE FIELD — 28 seeded dots drifting slowly upward with
 *     alpha-twinkle (a "depth" parallax: nearer dots are bigger + faster);
 *  3. (the caller draws the wordmark + CTA on top — see
 *     [OnboardingWelcomeStep]).
 *
 * The blob/particle LAYOUT is seeded-random but REMEMBERED — the art is
 * stable across recompositions; only the time phase moves.
 */
@Composable
internal fun OnboardingAuroraBackground(
    accent: Color,
    base: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "onboarding_aurora")
    // One shared phase, 0 → 2π over 16s, linear — every layer derives its own
    // motion from it (drifts, twinkles) so the whole canvas breathes together.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "onboardingAuroraPhase",
    )
    // A second, slower phase for the particles' vertical drift.
    val driftPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
        label = "onboardingParticleDrift",
    )

    val blobs = remember(accent) { buildAuroraBlobs(accent) }
    val particles = remember { buildParticles() }
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { 1.6.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Layer 0 — the deep base.
                drawRect(color = base)
                // Layer 1 — the aurora blobs (radial falloffs to transparent,
                // so they never form hard outlines against each other — the
                // same abstract-splash language as the browse hero's
                // SplashOverlay, but TIME-DRIVEN).
                blobs.forEach { blob ->
                    val cx = size.width * (blob.anchorX + blob.driftAmp * sin(phase * blob.speed + blob.phase0))
                    val cy = size.height * (blob.anchorY + blob.driftAmp * 0.6f * cos(phase * blob.speed + blob.phase0))
                    val radius = size.width * blob.radius
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                blob.color.copy(alpha = blob.alpha),
                                blob.color.copy(alpha = blob.alpha * 0.5f),
                                Color.Transparent,
                            ),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
                // Layer 2 — the particle field (seeded, drifting up, twinkling).
                particles.forEach { particle ->
                    val y01 = (particle.seedY - driftPhase * particle.speed) % 1f
                    val wrappedY = if (y01 < 0f) y01 + 1f else y01
                    val twinkle = 0.5f + 0.5f * sin(phase * particle.speed * 3f + particle.phase0)
                    val alpha = particle.alpha * (0.35f + 0.65f * twinkle)
                    val radius = dotRadiusPx * particle.size
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(
                            x = size.width * particle.seedX,
                            y = size.height * wrappedY,
                        ),
                    )
                }
            },
    )
}

/** One aurora blob's (stable, seeded) layout + its drift parameters. */
private data class AuroraBlob(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val alpha: Float,
    val driftAmp: Float,
    val speed: Float,
    val phase0: Float,
    val color: Color,
)

/**
 * The aurora palette: the ACCENT (the live theme seed) carries two blobs,
 * violet + teal complements carry the rest — a cohesive aurora on any
 * accent the user picks (and it RECOLORS when they pick a new one mid-wizard).
 */
private fun buildAuroraBlobs(accent: Color): List<AuroraBlob> = listOf(
    AuroraBlob(0.20f, 0.28f, 0.42f, 0.30f, 0.06f, 1.0f, 0.0f, accent),
    AuroraBlob(0.78f, 0.18f, 0.36f, 0.24f, 0.05f, 1.3f, 1.7f, Color(0xFF9C6BFF)),
    AuroraBlob(0.62f, 0.66f, 0.50f, 0.22f, 0.07f, 0.8f, 3.1f, accent),
    AuroraBlob(0.14f, 0.78f, 0.38f, 0.20f, 0.06f, 1.15f, 4.4f, Color(0xFF2FB8A8)),
    AuroraBlob(0.92f, 0.52f, 0.30f, 0.16f, 0.05f, 1.4f, 2.2f, Color(0xFF9C6BFF)),
    AuroraBlob(0.40f, 0.10f, 0.26f, 0.14f, 0.04f, 1.0f, 5.3f, Color(0xFF2FB8A8)),
)

/** One particle's (stable, seeded) layout + drift parameters. */
private data class AuroraParticle(
    val seedX: Float,
    val seedY: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float,
    val phase0: Float,
    val color: Color,
)

private fun buildParticles(): List<AuroraParticle> {
    val random = Random(seed = 20260903) // fixed seed — stable art across recompositions
    return List(28) {
        AuroraParticle(
            seedX = random.nextFloat(),
            seedY = random.nextFloat(),
            size = 0.6f + random.nextFloat() * 1.4f,
            alpha = 0.12f + random.nextFloat() * 0.30f,
            speed = 0.25f + random.nextFloat() * 0.75f,
            phase0 = random.nextFloat() * 6.28f,
            color = if (it % 3 == 0) Color(0xFFB9A8FF) else Color.White,
        )
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
