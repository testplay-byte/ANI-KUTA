package com.confused.anikuta.core.player.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The double-tap seek feedback state + indicator — shared by the MPV stack's
 * minimized AND fullscreen controls (D-456).
 *
 * # D-456 (the v1.1.5 device round)
 *
 * - **Cumulative**: consecutive double-taps on the same side accumulate the
 *   pill's label (+10 → +20 → +30 …) instead of showing a static "+10s"
 *   four times. Switching sides restarts the count.
 * - **2× the visible duration**: the old pill was visible ~500ms (150ms in +
 *   500ms out, gone immediately); the new one holds ~1s after the fade-in
 *   before fading out (~1.3s total visible), and every re-tap RESETS the
 *   hold (the previous job is cancelled — which also fixes the old
 *   overlapping-animations flicker on rapid taps).
 * - **Fullscreen parity**: the fullscreen controls previously had NO
 *   indicator at all (the double-tap seeked silently); they now render the
 *   same pill via this shared component.
 */
@Stable
class DoubleTapSeekState internal constructor(private val scope: CoroutineScope) {

    /** Whether the indicator is on screen. */
    var visible by mutableStateOf(false)
        private set

    /** Which side the pill hugs (true = forward/right). */
    var forward by mutableStateOf(true)
        private set

    /** The accumulated seek total (+10, +20, +30 …). */
    var totalSeconds by mutableIntStateOf(0)
        private set

    /** The pill's shared alpha (driven by the hold animation). */
    val alpha = Animatable(0f)

    private var job: Job? = null

    /**
     * Registers one double-tap seek of [stepSeconds] in [forward] direction:
     * accumulates the total when the same side is already showing, restarts
     * the visible-hold window, and schedules the fade-out.
     */
    fun accumulate(forward: Boolean, stepSeconds: Int = 10) {
        val sameSide = visible && this.forward == forward
        this.forward = forward
        totalSeconds = if (sameSide) totalSeconds + stepSeconds else stepSeconds
        visible = true
        job?.cancel()
        job = scope.launch {
            if (alpha.value < 1f) {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(120))
            }
            // The D-456 hold — double the old visible window; every re-tap
            // restarts it from here (the previous job was cancelled above).
            delay(HOLD_MS)
            alpha.animateTo(0f, tween(300))
            visible = false
            totalSeconds = 0
        }
    }

    private companion object {
        /** The visible hold (~1s) — 2× the old indicator's ~500ms life. */
        const val HOLD_MS = 1_000L
    }
}

/** Remembers the shared double-tap seek state for one controls surface. */
@Composable
fun rememberDoubleTapSeekState(): DoubleTapSeekState {
    val scope = rememberCoroutineScope()
    return remember { DoubleTapSeekState(scope) }
}

/**
 * The seek feedback pill ("+20s" / "-10s"), hugging the tapped side. Renders
 * nothing while the state is hidden. Same visual language as the old
 * per-screen pills (black rounded pill, white bold label) so both the
 * minimized and fullscreen surfaces read identically.
 */
@Composable
fun DoubleTapSeekIndicator(
    state: DoubleTapSeekState,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            contentAlignment = if (state.forward) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f * state.alpha.value),
            ) {
                Text(
                    text = (if (state.forward) "+" else "-") + "${state.totalSeconds}s",
                    color = Color.White.copy(alpha = state.alpha.value),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
