package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

/**
 * The debug bubble — a floating, draggable squircle overlay (Phase DB-1).
 *
 * Renders on top of every screen (sibling of the nav content in AppRoot's Box).
 * Draggable anywhere on screen; tap (without dragging) toggles the panel.
 * Position does NOT persist (D-163) — returns to the default (bottom-end) on
 * every app reopen.
 *
 * Gated by [DebugBubblePreferences.visible] (default `true` in debug builds).
 *
 * **Gesture handling (tap-vs-drag fix):** uses a single [awaitEachGesture] that
 * tracks total movement. If the pointer moves < 8px before release, it's a tap
 * → toggle the panel. Otherwise it's a drag → move the bubble. The previous
 * implementation used `detectDragGestures` alone, which never fires onDragEnd
 * for a pure tap (no drag) → the panel never opened.
 *
 * **Visual (D-163 revision):** squircle shape (RoundedCornerShape(16dp) on a
 * 48dp box — a rounded square, not a circle). Light white-to-grey color
 * (surface with high alpha) so it's visible on both light + dark themes.
 *
 * **Tap animation:** a scale-down on press (press → 0.9, release → 1.0) via
 * graphicsLayer, driven by a pressed state.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:DebugBubble".
 */
@Composable
fun DebugBubble(
    preferences: DebugBubblePreferences,
) {
    val visible by preferences.visibleFlow().collectAsStateWithLifecycle(initialValue = preferences.visible)
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Screen dimensions in px (for bounds clamping + default position).
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }

    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
    val insetPx = with(density) { DEFAULT_INSET.toPx() }
    val tapThresholdPx = with(density) { TAP_THRESHOLD.toPx() }

    // Default position: bottom-end (bottom-right), 16dp inset from edges + nav bar.
    val defaultOffset = Offset(
        x = screenWidthPx - bubbleSizePx - insetPx,
        y = screenHeightPx - bubbleSizePx - insetPx - navBarPx,
    )

    val state = remember(defaultOffset) { DebugBubbleState(defaultOffset) }

    // Re-clamp on rotation.
    LaunchedEffect(configuration.orientation) {
        val current = state.offset
        val clamped = clampOffset(current, screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx)
        if (clamped != current) {
            state.updateOffset(clamped)
        }
    }

    // Pressed state for the tap animation (scale-down on press).
    var pressed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "bubble_scale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            // Light white-to-grey tone — visible on both light + dark themes.
            // Uses surface (a near-white in light theme, near-black in dark) with
            // high alpha so the bubble is clearly visible but not jarring.
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            // Squircle: a rounded square (16dp corners on a 48dp box = ~33% radius).
            // NOT a circle (RoundedCornerShape(50) = circle). D-163 revision.
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(BUBBLE_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .offset {
                    IntOffset(state.offset.x.toInt(), state.offset.y.toInt())
                }
                .pointerInput(Unit) {
                    // Single gesture detector that distinguishes tap from drag.
                    // awaitEachGesture gives full control over the gesture lifecycle.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragging = false

                        // Track the gesture until the pointer is released.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                // Pointer released.
                                change.consume()
                                pressed = false
                                if (!isDragging) {
                                    // Pure tap (total movement < threshold) → toggle the panel.
                                    state.toggleExpanded()
                                } else {
                                    // Was a drag → nothing extra to do (offset already updated).
                                }
                                break
                            }
                            // Movement during the gesture.
                            val dx = change.positionChange().x
                            val dy = change.positionChange().y
                            totalDx += dx
                            totalDy += dy
                            if (!isDragging && (abs(totalDx) > tapThresholdPx || abs(totalDy) > tapThresholdPx)) {
                                // Crossed the threshold → this is a drag, not a tap.
                                isDragging = true
                            }
                            if (isDragging) {
                                change.consume()
                                val newValue = clampOffset(
                                    state.offset + Offset(dx, dy),
                                    screenWidthPx, screenHeightPx, bubbleSizePx, statusBarPx, navBarPx,
                                )
                                state.updateOffset(newValue)
                            }
                        }
                        pressed = false
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Debug bubble",
                    // Dark icon on the light bubble (onSurface works for both themes).
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(BUBBLE_ICON_SIZE),
                )
            }
        }

        // ── The debug panel (DB-2) ──
        DebugPanel(
            state = state,
            onDismiss = { state.collapse() },
        )
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

/** The bubble's size (48dp — a rounded square, not a circle). */
private val BUBBLE_SIZE = 48.dp

/** The bug icon size inside the bubble. */
private val BUBBLE_ICON_SIZE = 24.dp

/** Default inset from the screen edges (16dp). */
private val DEFAULT_INSET = 16.dp

/** Movement threshold: < this = tap, >= this = drag. */
private val TAP_THRESHOLD = 8.dp
