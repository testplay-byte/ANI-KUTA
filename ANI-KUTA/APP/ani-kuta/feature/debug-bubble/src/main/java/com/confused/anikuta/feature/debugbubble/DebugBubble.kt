package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The debug bubble — a floating, draggable squircle overlay (Phase DB-1).
 *
 * Renders on top of every screen (sibling of the nav content in AppRoot's Box).
 * Draggable anywhere on screen; tap (without dragging) toggles the panel
 * (panel implementation is DB-2). Position does NOT persist (D-163) — returns
 * to the default (bottom-end) on every app reopen.
 *
 * Gated by [DebugBubblePreferences.visible] (default `true` in debug builds).
 * The caller (`AppRoot`) also gates on `BuildConfig.DEBUG` — so in release
 * builds this composable is never invoked.
 *
 * **Non-intrusive:** the bubble is a sibling of the nav content, not a child.
 * Its drag updates are internal to [DebugBubbleState.offset] (an [Animatable]);
 * recomposing the bubble doesn't trigger recomposition of the nav content.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:DebugBubble".
 */
@Composable
fun DebugBubble(
    preferences: DebugBubblePreferences,
) {
    val visible by preferences.visibleFlow().collectAsStateWithLifecycle(initialValue = preferences.visible)
    if (!visible) return

    val state = remember { DebugBubbleState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Screen dimensions in px (for bounds clamping + default position).
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }

    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
    val insetPx = with(density) { DEFAULT_INSET.toPx() }

    // Default position: bottom-end (bottom-right), 16dp inset from edges + nav bar.
    val defaultOffset = Offset(
        x = screenWidthPx - bubbleSizePx - insetPx,
        y = screenHeightPx - bubbleSizePx - insetPx - navBarPx,
    )

    // Initialize the offset to the default on first composition (no persistence — D-163).
    LaunchedEffect(Unit) {
        state.offset.snapTo(defaultOffset)
    }

    // Re-clamp on rotation (configChanges means Activity isn't recreated, but
    // LocalConfiguration changes → Compose recomposes). Animate to the clamped
    // position so the bubble doesn't jump off-screen. (D-162 I6)
    LaunchedEffect(configuration.orientation) {
        val current = state.offset.value
        val clamped = clampOffset(current, screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx)
        if (clamped != current) {
            state.offset.animateTo(clamped, spring())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            shape = RoundedCornerShape(50),  // squircle silhouette (D-163)
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(BUBBLE_SIZE)
                .offset {
                    state.offset.value.toIntOffset()
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { state.onDragStart() },
                        onDragEnd = { state.onDragEnd() },
                        onDragCancel = { state.dragged = false },
                    ) { change, dragAmount ->
                        change.consume()
                        if (abs(dragAmount.x) > 0.5f || abs(dragAmount.y) > 0.5f) {
                            state.onDragMoved()
                        }
                        val newValue = clampOffset(
                            state.offset.value + dragAmount,
                            screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx,
                        )
                        scope.launch { state.offset.snapTo(newValue) }
                    }
                },
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Debug bubble",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(BUBBLE_ICON_SIZE),
                )
            }
        }

        // Panel placeholder — DB-2 will replace this with the full tabbed panel.
        // For DB-1, tapping the bubble just toggles a (no-op) expanded state so
        // the drag/tap mechanics can be tested on device.
    }
}

// ── Helpers ──

/** Clamp the bubble offset to keep it fully on-screen. */
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

/** The bubble's diameter (48dp per spec). */
private val BUBBLE_SIZE = 48.dp

/** The bug icon size inside the bubble. */
private val BUBBLE_ICON_SIZE = 22.dp

/** Default inset from the screen edges (16dp). */
private val DEFAULT_INSET = 16.dp
