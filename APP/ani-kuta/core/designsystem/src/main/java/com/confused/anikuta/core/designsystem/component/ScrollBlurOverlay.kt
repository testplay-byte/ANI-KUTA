package com.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable scroll-driven gradient scrim overlay.
 *
 * From DESIGN-LANGUAGE.md §5.9 (ScrollBlurOverlay):
 * Sits at the bottom edge of a pinned header. When content scrolls underneath,
 * a gradient (solid background → transparent) fades in smoothly with rounded
 * bottom corners. When scrolled to top, the effect fades out.
 *
 * This does NOT use RenderEffect — it's a gradient scrim (GPU-cheap, no
 * recomposition). The "frosted glass" is an optical illusion.
 *
 * The overlay's alpha is driven by scroll offset via `graphicsLayer` (deferred
 * read — no recomposition on scroll).
 *
 * CORE_RULES §22: smooth animation (smoothstep fade).
 */
@Composable
fun ScrollBlurOverlay(
    scrollOffset: () -> Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    blurHeight: Dp = 36.dp,
    cornerRadius: Dp = 24.dp,
    enabled: Boolean = true,
) {
    if (!enabled) return

    val density = LocalDensity.current
    val fadeDistancePx = with(density) { 24.dp.toPx() }
    val overlapPx = with(density) { (-2).dp.toPx() }

    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = cornerRadius,
        bottomEnd = cornerRadius,
    )

    val gradientColors = listOf(
        backgroundColor,
        backgroundColor.copy(alpha = 0.92f),
        backgroundColor.copy(alpha = 0.70f),
        backgroundColor.copy(alpha = 0.42f),
        backgroundColor.copy(alpha = 0.18f),
        backgroundColor.copy(alpha = 0.05f),
        Color.Transparent,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blurHeight)
            .clip(shape)
            .graphicsLayer {
                val raw = scrollOffset()
                val t = (raw / fadeDistancePx).coerceIn(0f, 1f)
                val smoothed = t * t * (3 - 2 * t)
                this.alpha = smoothed
                this.translationY = overlapPx
            }
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            },
    )
}
