package com.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * D-259: A thin settings slider with a rounded-square thumb — the app's
 * replacement for the stock Material3 [androidx.compose.material3.Slider]
 * in customized sheets (device feedback: the default sliders were "way too
 * bad… use thin sliders… with a thumb grabbing area with a square with
 * rounded corners").
 *
 * Anatomy:
 * - **Track**: a thin 4dp rounded bar (active = [thumbColor]/[activeTrackColor],
 *   inactive = [inactiveTrackColor], defaults primary /
 *   surfaceContainerHighest — the same palette the stock slider used).
 * - **Thumb**: an 18dp square with 6dp rounded corners (a "sticker" — filled
 *   with the active color + a 1.5dp surface-color halo so it pops on any
 *   background), always visible so it doubles as the value indicator.
 * - **Touch target**: the whole row is 36dp tall — a comfortable grab area
 *   while the visuals stay thin.
 * - Tap-to-jump AND drag; both update live via [onValueChange], and
 *   [onValueChangeFinished] fires when the gesture settles.
 *
 * Version-skew safety (D-255 lesson): built ONLY from ABI-stable foundation
 * primitives (Box, drawBehind-free backgrounds, pointerInput +
 * detectHorizontalDragGestures / detectTapGestures, onSizeChanged, lambda
 * offset) — the same set MinimalSeekbar ships with. Tap and drag live in two
 * SEPARATE pointerInput modifiers (both detectors are non-returning suspend
 * consumers and cannot share one block). No FlowRow-class APIs.
 *
 * @param value current value (clamped to [valueRange] for the fraction).
 * @param onValueChange fired continuously while dragging / on tap.
 * @param valueRange the allowed range (supports negatives, e.g. -100..100).
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    contentDescription: String = "Slider",
    thumbColor: Color = Color.Unspecified,
    activeTrackColor: Color = Color.Unspecified,
    inactiveTrackColor: Color = Color.Unspecified,
) {
    val resolvedThumb = if (thumbColor.isUnspecified) MaterialTheme.colorScheme.primary else thumbColor
    val resolvedActive = if (activeTrackColor.isUnspecified) resolvedThumb else activeTrackColor
    val resolvedInactive =
        if (inactiveTrackColor.isUnspecified) MaterialTheme.colorScheme.surfaceContainerHighest
        else inactiveTrackColor

    var trackWidthPx by remember { mutableStateOf(0f) }
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) {
        ((value - valueRange.start) / span).coerceIn(0f, 1f)
    } else 0f

    fun valueAt(x: Float): Float {
        if (trackWidthPx <= 0f) return value
        val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
        return valueRange.start + ratio * span
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { this.contentDescription = contentDescription }
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onValueChange(valueAt(offset.x)) },
                    onHorizontalDrag = { change, _ ->
                        onValueChange(valueAt(change.position.x))
                        change.consume()
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() },
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x))
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Inactive track — thin 4dp rounded bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(resolvedInactive),
        )
        // Active track.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(resolvedActive),
        )
        // Thumb — 18dp rounded square with a surface halo, vertically centered,
        // horizontally positioned by the fraction (clamped inside the track).
        if (trackWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset {
                        val thumbPx = 18.dp.toPx()
                        val half = thumbPx / 2f
                        val raw = trackWidthPx * fraction
                        // Keep the thumb fully inside the track's horizontal bounds.
                        val clamped = raw.coerceIn(half, (trackWidthPx - half).coerceAtLeast(half))
                        IntOffset((clamped - half).roundToInt(), 0)
                    }
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp), // halo inset for the inner fill
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                        .background(resolvedThumb),
                )
            }
        }
    }
}
