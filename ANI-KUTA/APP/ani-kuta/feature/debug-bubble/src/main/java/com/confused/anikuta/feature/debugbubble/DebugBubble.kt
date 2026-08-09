package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The debug bubble — a floating, draggable squircle overlay (Phase DB-1).
 *
 * **Drag fix (jitter root cause):** `Modifier.offset {}` is a LAYOUT modifier —
 * it changes the composable's layout position, which shifts the pointer-input
 * coordinate system. When the bubble moves (offset updated), pointer coordinates
 * shift → feedback loop → jitter. **Fix:** use `graphicsLayer { translationX/Y }`
 * instead — it's a DRAW modifier that moves the visual rendering WITHOUT changing
 * the layout/pointer-input coordinate system. Pointer coords stay stable → no
 * feedback → smooth 1:1 drag.
 *
 * **Tap vs drag:** uses `detectTapGestures` + `detectDragGestures` in two
 * separate `pointerInput` blocks. `detectTapGestures` fires `onTap` only for
 * taps (minimal movement). `detectDragGestures` fires for drags (movement
 * exceeds touch slop). Compose handles the priority — they're mutually exclusive.
 *
 * **Drop-bounce:** on drag end, the bubble pulses to 1.15× then back to 1.0×.
 * No scale effect DURING the drag.
 *
 * **Border:** the bubble has a visible border (onSurfaceVariant) for clear
 * identification.
 *
 * **Hidden when minimized:** when the panel is MINIMIZED, the bubble is hidden
 * (per user: "when minimized, the bubble still shows — handle it properly").
 */
@Composable
fun DebugBubble(
    preferences: DebugBubblePreferences,
) {
    val visible by preferences.visibleFlow().collectAsStateWithLifecycle(initialValue = preferences.visible)
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }
    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
    val insetPx = with(density) { DEFAULT_INSET.toPx() }

    val defaultOffset = Offset(
        x = screenWidthPx - bubbleSizePx - insetPx,
        y = screenHeightPx - bubbleSizePx - insetPx - navBarPx,
    )

    val state = remember(defaultOffset) { DebugBubbleState(defaultOffset) }

    LaunchedEffect(configuration.orientation) {
        val current = state.offset
        val clamped = clampOffset(current, screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx)
        if (clamped != current) state.updateOffset(clamped)
    }

    // Drop-bounce: on drag release, pulse 1.0 → 1.15 → 1.0.
    var dropBounceTrigger by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (dropBounceTrigger > 0) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 600f),
        label = "bubble_drop_bounce",
    )
    LaunchedEffect(dropBounceTrigger) {
        if (dropBounceTrigger > 0) {
            kotlinx.coroutines.delay(250)
            dropBounceTrigger = 0
        }
    }

    // Hide the bubble when the panel is minimized (per user).
    val bubbleVisible = state.panelState != PanelState.MINIMIZED

    Box(modifier = Modifier.fillMaxSize()) {
        // ── The debug panel (rendered FIRST so the bubble draws on top) ──
        DebugPanel(
            state = state,
            onMinimize = { state.minimize() },
        )

        // ── The bubble (rendered AFTER the panel so it's on top) ──
        // Hidden when minimized.
        if (bubbleVisible) {
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .graphicsLayer {
                        // CRITICAL: use graphicsLayer (translationX/Y) NOT Modifier.offset{}.
                        // graphicsLayer is a DRAW modifier — moves the visual without
                        // changing the pointer-input coordinate system → no feedback loop.
                        scaleX = scale
                        scaleY = scale
                        translationX = state.offset.x
                        translationY = state.offset.y
                    }
                    // Tap detector — fires onTap only for taps (not drags).
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { state.onBubbleTap() })
                    }
                    // Drag detector — fires onDrag for drags. Uses positionChange()
                    // internally (correct delta, no coordinate-shift issue because
                    // graphicsLayer doesn't shift the coordinate system).
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = { dropBounceTrigger++ },
                            onDragCancel = { },
                        ) { change, dragAmount ->
                            change.consume()
                            val newValue = clampOffset(
                                state.offset + dragAmount,
                                screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx,
                            )
                            state.updateOffset(newValue)
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Debug bubble",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(BUBBLE_ICON_SIZE),
                    )
                }
            }
        }
    }
}

// ── Helpers ──

private fun clampOffset(
    offset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    bubbleSizePx: Float,
    statusBarPx: Float,
    navBarPx: Float,
): Offset {
    val minX = 0f
    val maxX = (screenWidthPx - bubbleSizePx).coerceAtLeast(0f)
    val minY = statusBarPx
    val maxY = (screenHeightPx - bubbleSizePx - navBarPx).coerceAtLeast(statusBarPx)
    return Offset(
        x = offset.x.coerceIn(minX, maxX),
        y = offset.y.coerceIn(minY, maxY),
    )
}

// ── Constants ──

private val BUBBLE_SIZE = 48.dp
private val BUBBLE_ICON_SIZE = 24.dp
private val DEFAULT_INSET = 16.dp
