package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * State holder for the debug bubble (Phase DB).
 *
 * UI state (ephemeral — held in `remember { DebugBubbleState() }`, resets on
 * process restart). The drag position is NOT persisted (D-163) — the bubble
 * returns to its default (bottom-end) every time the app reopens.
 *
 * @property offset The bubble's position in px, backed by [Animatable] for
 *           smooth spring-back. Initialized to the default position by
 *           [DebugBubble] on first composition.
 * @property expanded Whether the panel is open.
 * @property dragged Tracks whether the current gesture is a drag (vs a tap).
 *           Used for tap-vs-drag disambiguation (< 8px = tap).
 */
class DebugBubbleState {
    val offset = Animatable(Offset.Zero)
    var expanded by mutableStateOf(false)
        private set
    var dragged by mutableStateOf(false)
        private set

    fun toggleExpanded() { expanded = !expanded }
    fun collapse() { expanded = false }
    fun expand() { expanded = true }

    /** Mark the start of a drag gesture. */
    fun onDragStart() { dragged = false }

    /** Mark that a drag actually moved (distinguishes tap from drag). */
    fun onDragMoved() { dragged = true }

    /** End of a drag gesture. If it didn't actually drag, treat as a tap. */
    fun onDragEnd() {
        if (!dragged) {
            toggleExpanded()
        }
        dragged = false
    }
}
