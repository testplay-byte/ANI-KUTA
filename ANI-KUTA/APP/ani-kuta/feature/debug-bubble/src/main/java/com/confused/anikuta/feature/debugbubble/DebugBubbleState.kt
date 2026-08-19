package com.confused.anikuta.feature.debugbubble

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
 * Panel state machine:
 * - [PanelState.CLOSED] — panel not shown; only the bubble is visible.
 * - [PanelState.EXPANDED] — full panel shown (tabbed).
 * - [PanelState.MINIMIZED] — panel collapsed to a small peek (tap-outside
 *   minimizes; tapping the bubble closes fully).
 *
 * @property offset The bubble's position in px.
 * @property panelState The current panel state (CLOSED / EXPANDED / MINIMIZED).
 */
class DebugBubbleState(initialOffset: Offset = Offset.Zero) {
    var offset by mutableStateOf(initialOffset)
        private set
    var panelState by mutableStateOf(PanelState.CLOSED)
        private set

    /** The bubble is tappable only when the panel isn't expanded (avoids
     *  conflict with the scrim). */
    val isExpanded: Boolean get() = panelState == PanelState.EXPANDED

    /** Update the bubble's offset. */
    fun updateOffset(value: Offset) { offset = value }

    /** Tap on the bubble: CLOSED → EXPANDED; EXPANDED → CLOSED; MINIMIZED → EXPANDED. */
    fun onBubbleTap() {
        panelState = when (panelState) {
            PanelState.CLOSED, PanelState.MINIMIZED -> PanelState.EXPANDED
            PanelState.EXPANDED -> PanelState.CLOSED
        }
    }

    /** Tap outside the panel (on the scrim): EXPANDED → MINIMIZED (not closed). */
    fun minimize() {
        if (panelState == PanelState.EXPANDED) {
            panelState = PanelState.MINIMIZED
        }
    }

    /** Close the panel fully (back to CLOSED). */
    fun close() { panelState = PanelState.CLOSED }

    /** Expand from minimized. */
    fun expand() { panelState = PanelState.EXPANDED }
}

/** The panel's visibility state. */
enum class PanelState {
    CLOSED,
    EXPANDED,
    MINIMIZED,
}
