package com.confused.anikuta.core.player.controls

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ROUND 131 (D-809): touch gestures for the player surface — BOTH orientations.
 *
 *  - Vertical swipe (right half)  → the configured side action (default VOLUME)
 *  - Vertical swipe (left half)   → the configured side action (default BRIGHTNESS)
 *  - Horizontal swipe             → live seek: the video VISIBLY moves while the
 *    finger drags (a throttled absolute seek every [PlayerGestureState.LIVE_SEEK_INTERVAL_MS])
 *    and an exact seek lands on release. The OSD shows the running target time.
 *
 * [PlayerGestureConfig.enabled] is the master gate — WatchScreen feeds it from
 * the legacy `player_gestures` preference ([com.confused.anikuta.core.preferences.PlayerPreferences.gestureControlsEnabled]),
 * which until this round had no consumer at all.
 */

/** What a vertical side-swipe does. */
enum class GestureSideAction { VOLUME, BRIGHTNESS, NONE }

/** Parses the stored preference string (unknown values fall back to NONE). */
fun gestureSideActionFrom(raw: String): GestureSideAction = when (raw.uppercase()) {
    "VOLUME" -> GestureSideAction.VOLUME
    "BRIGHTNESS" -> GestureSideAction.BRIGHTNESS
    else -> GestureSideAction.NONE
}

/** Gesture wiring, read from preferences by the watch screen. */
data class PlayerGestureConfig(
    val enabled: Boolean = false,
    val rightSide: GestureSideAction = GestureSideAction.VOLUME,
    val leftSide: GestureSideAction = GestureSideAction.BRIGHTNESS,
    val horizontalSeek: Boolean = true,
)

/** The gesture currently driving the OSD. */
enum class PlayerGestureKind { VOLUME, BRIGHTNESS, SEEK }

/**
 * Live state of the in-progress gesture + the actual effectors (MPV volume,
 * window brightness, throttled seek). One instance per controls overlay,
 * created with [rememberPlayerGestureState].
 */
class PlayerGestureState internal constructor() {

    var kind by mutableStateOf<PlayerGestureKind?>(null)
        private set

    // The routing machine — INDEPENDENT of the OSD's [kind] so the OSD can be
    // hidden mid-gesture (the timeout) without killing the drag's effectors.
    private var activeKind: PlayerGestureKind? = null

    /** Density-scaled touch slop (set by [detectPlayerGestures] at attach). */
    internal var slopPx = GESTURE_SLOP_PX

    /** Hides the OSD while the gesture keeps running (the timeout path). */
    fun hideOsdOnly() {
        kind = null
    }

    /** Which half of the screen the gesture started on (OSD placement). */
    var onRightSide by mutableStateOf(false)
        private set
    /** VOLUME/BRIGHTNESS: 0..100. SEEK: the absolute target in seconds. */
    var level by mutableStateOf(0f)
        private set
    /** SEEK: the signed delta from the drag-start position, in seconds. */
    var seekDeltaSec by mutableStateOf(0)
        private set

    private var startOffset = Offset.Zero
    private var axisLocked = false
    private var dead = false

    private var startVolume = 0f
    private var volumeMax = 100f
    private var startBrightness = 0.5f
    private var startPositionSec = 0
    private var durationSec = 0
    private var lastLiveSeekAt = 0L
    private var window: Window? = null
    private var onSeekTo: ((Int) -> Unit)? = null

    /**
     * Restores the system brightness override. Called on lift-off of a
     * brightness gesture? No — the override STAYS (the user chose a level);
     * this only runs when the controls overlay is disposed so we never leak
     * a dimmed window into the rest of the app.
     */
    fun restoreBrightness() {
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        window = null
    }

    internal fun begin(
        offset: Offset,
        size: IntSize,
        config: PlayerGestureConfig,
        positionSec: Int,
        durationSec: Int,
        onSeekTo: (Int) -> Unit,
    ) {
        startOffset = offset
        axisLocked = false
        dead = false
        activeKind = null
        kind = null
        onRightSide = offset.x >= size.width / 2f
        startPositionSec = positionSec
        this.durationSec = durationSec
        this.onSeekTo = onSeekTo
        seekDeltaSec = 0
        level = 0f
    }

    internal fun drag(
        position: Offset,
        size: IntSize,
        config: PlayerGestureConfig,
        window: Window?,
    ) {
        if (dead) return

        val dx = position.x - startOffset.x
        val dy = position.y - startOffset.y

        if (!axisLocked) {
            if (abs(dx) < slopPx && abs(dy) < slopPx) return
            axisLocked = true
            if (abs(dy) > abs(dx)) {
                val action = if (onRightSide) config.rightSide else config.leftSide
                if (action == GestureSideAction.NONE) {
                    dead = true
                    return
                }
                if (action == GestureSideAction.VOLUME) {
                    startVolume = runCatching { (MPVLib.getPropertyInt("volume") ?: 100).toFloat() }
                        .getOrDefault(100f)
                    volumeMax = runCatching { (MPVLib.getPropertyInt("volume-max") ?: 130).toFloat().coerceAtLeast(100f) }
                        .getOrDefault(130f)
                    activeKind = PlayerGestureKind.VOLUME
                    level = startVolume
                } else {
                    this.window = window
                    val current = window?.attributes?.screenBrightness ?: -1f
                    startBrightness = if (current < 0f) 0.5f else current.coerceIn(0f, 1f)
                    activeKind = PlayerGestureKind.BRIGHTNESS
                    level = startBrightness * 100f
                }
            } else {
                if (!config.horizontalSeek || durationSec <= 0) {
                    dead = true
                    return
                }
                activeKind = PlayerGestureKind.SEEK
                lastLiveSeekAt = 0L
            }
            kind = activeKind
        }

        when (activeKind) {
            PlayerGestureKind.VOLUME -> {
                // Full screen height = the whole volume range, up = louder.
                val delta = -dy / size.height.toFloat().coerceAtLeast(1f) * volumeMax
                level = (startVolume + delta).coerceIn(0f, volumeMax)
                runCatching { MPVLib.setPropertyInt("volume", level.roundToInt()) }
            }
            PlayerGestureKind.BRIGHTNESS -> {
                val delta = -dy / size.height.toFloat().coerceAtLeast(1f)
                val target = (startBrightness + delta).coerceIn(0f, 1f)
                level = target * 100f
                window?.let { w ->
                    w.attributes = w.attributes.apply { screenBrightness = target }
                }
            }
            PlayerGestureKind.SEEK -> {
                // Full screen width = the whole episode; the LIVE seek makes
                // the video visibly follow the finger.
                val deltaSec = (dx / size.width.toFloat().coerceAtLeast(1f) * durationSec).roundToInt()
                seekDeltaSec = deltaSec
                level = (startPositionSec + deltaSec).toFloat().coerceIn(0f, durationSec.toFloat())
                val now = System.currentTimeMillis()
                if (now - lastLiveSeekAt >= LIVE_SEEK_INTERVAL_MS) {
                    lastLiveSeekAt = now
                    onSeekTo?.invoke(level.roundToInt())
                }
            }
            null -> Unit
        }
    }

    /** Finger lift: land the final exact seek (SEEK) and hide the OSD. */
    internal fun end() {
        if (activeKind == PlayerGestureKind.SEEK) {
            onSeekTo?.invoke(level.roundToInt())
        }
        activeKind = null
        kind = null
    }

    internal fun cancel() {
        activeKind = null
        kind = null
    }

    companion object {
        /** Touch slop before the axis locks (px). */
        const val GESTURE_SLOP_PX = 24f
        /** How often the horizontal swipe actually seeks while dragging. */
        const val LIVE_SEEK_INTERVAL_MS = 450L
        /** OSD auto-hide safety net when a gesture never sends its end event. */
        const val OSD_TIMEOUT_MS = 2500L
    }
}

/**
 * Creates the per-overlay gesture state. The brightness override is restored
 * when the overlay leaves composition (MINIMIZED↔FULLSCREEN swap or screen
 * exit) so a dimmed window never leaks into the rest of the app.
 */
@Composable
fun rememberPlayerGestureState(): PlayerGestureState {
    val state = remember { PlayerGestureState() }
    DisposableEffect(state) {
        onDispose { state.restoreBrightness() }
    }
    return state
}

/**
 * The drag detector — add as a SEPARATE `pointerInput` on a full-size Box that
 * sits UNDER the buttons (an earlier sibling) so the seekbar and controls keep
 * winning their touches. Coexists with the tap detectors: a tap never exceeds
 * the slop, a drag cancels the tap detector.
 *
 * [window] feeds the brightness gesture; `null` (no Activity) just disables it.
 * [positionSec]/[durationSec] are provider lambdas so the detector does NOT
 * restart (killing in-flight drags) every time the playback position ticks.
 */
suspend fun PointerInputScope.detectPlayerGestures(
    state: PlayerGestureState,
    config: PlayerGestureConfig,
    window: Window?,
    positionSec: () -> Int,
    durationSec: () -> Int,
    onSeekTo: (Int) -> Unit,
) {
    state.slopPx = 8.dp.toPx()
    detectDragGestures(
        onDragStart = { offset ->
            if (!config.enabled) return@detectDragGestures
            state.begin(offset, size, config, positionSec(), durationSec(), onSeekTo)
        },
        onDrag = { change, _ ->
            if (!config.enabled) return@detectDragGestures
            state.drag(change.position, size, config, window)
        },
        onDragEnd = { state.end() },
        onDragCancel = { state.cancel() },
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Gesture OSD — the "proper UI" the round asked for
// ════════════════════════════════════════════════════════════════════════════

/**
 * The gesture overlay: a glass panel hugging the side being adjusted (volume
 * right / brightness left, following the side the gesture started on) with a
 * vertical fill bar and the live percentage — or, for seek, a centered time
 * panel with direction arrows and the running target. Renders nothing when idle.
 */
@Composable
fun GestureOsd(
    state: PlayerGestureState,
    modifier: Modifier = Modifier,
) {
    val kind = state.kind ?: return

    // Safety net: a gesture whose end event never fires can't strand the OSD.
    // hideOsdOnly() (NOT cancel) — a finger resting >2.5s mid-drag is natural
    // while reading the seek target; the gesture must stay live underneath.
    LaunchedEffect(kind, state.level, state.seekDeltaSec) {
        kotlinx.coroutines.delay(PlayerGestureState.OSD_TIMEOUT_MS)
        state.hideOsdOnly()
    }

    when (kind) {
        PlayerGestureKind.VOLUME -> SideGestureOsd(
            icon = Icons.Default.VolumeUp,
            label = "Volume",
            level = state.level,
            onRightSide = state.onRightSide,
            modifier = modifier,
        )
        PlayerGestureKind.BRIGHTNESS -> SideGestureOsd(
            icon = Icons.Default.BrightnessHigh,
            label = "Brightness",
            level = state.level,
            onRightSide = state.onRightSide,
            modifier = modifier,
        )
        PlayerGestureKind.SEEK -> SeekGestureOsd(
            targetSec = state.level.roundToInt(),
            deltaSec = state.seekDeltaSec,
            modifier = modifier,
        )
    }
}

@Composable
private fun SideGestureOsd(
    icon: ImageVector,
    label: String,
    level: Float,
    onRightSide: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(if (onRightSide) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!onRightSide) {
                VerticalLevelBar(level = level)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.height(24.dp).width(24.dp))
                    Text(
                        text = "${level.roundToInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (onRightSide) {
                Spacer(modifier = Modifier.width(10.dp))
                VerticalLevelBar(level = level)
            }
        }
    }
}

/** The vertical fill bar beside the side-gesture panel. */
@Composable
private fun VerticalLevelBar(level: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(6.dp)
            .height(120.dp)
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .fillMaxHeight(level.coerceIn(0f, 100f) / 100f)
                .background(Color.White, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun SeekGestureOsd(
    targetSec: Int,
    deltaSec: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    if (deltaSec >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = if (deltaSec >= 0) "Seek forward" else "Seek backward",
                    tint = Color.White,
                    modifier = Modifier.height(22.dp).width(22.dp),
                )
                Text(
                    text = "${if (deltaSec >= 0) "+" else "-"}${formatTime(abs(deltaSec))}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatTime(targetSec),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
