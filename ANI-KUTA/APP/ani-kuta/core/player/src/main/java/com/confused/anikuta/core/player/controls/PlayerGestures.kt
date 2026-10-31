package com.confused.anikuta.core.player.controls

import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The player's touch-gesture layer (D-809, reworked by the round-132 device
 * round): volume/brightness side swipes, the live-follow horizontal seek,
 * the center-swipe mode actions (cinema / fullscreen), and the pinch zoom.
 *
 * ROUND-132 REWORK, driven by the device report:
 *  - the OSD renders on the side OPPOSITE the finger (a right-side swipe
 *    shows its panel on the LEFT — the finger covered the panel);
 *  - the % label sits in a FIXED-width slot (no 99→100 width jump) and says
 *    MAX at the cap; volume at 0 crossfades to a muted (speaker-slash) icon;
 *  - the OSD panel and the level bar carry shadows; the fill bar + the seek
 *    accents use the THEME color;
 *  - the CENTER third is a dead zone for volume/brightness — it carries the
 *    center-swipe actions instead (cinema / fullscreen transitions);
 *  - the horizontal seek maps a FULL-WIDTH drag to ~SEEK_RANGE_SEC seconds
 *    (5 minutes — the episode-length mapping was "way too much" sensitive);
 *  - [PlayerGestureState.isSeekGestureActive] lets the controls hide the
 *    buffering spinner + the center play/±10s buttons while a seek gesture
 *    is live.
 */

/** What a vertical side-swipe does. */
enum class GestureSideAction { VOLUME, BRIGHTNESS, NONE }

/** Parses the stored preference string (unknown values fall back to NONE). */
fun gestureSideActionFrom(raw: String): GestureSideAction = when (raw.uppercase()) {
    "VOLUME" -> GestureSideAction.VOLUME
    "BRIGHTNESS" -> GestureSideAction.BRIGHTNESS
    else -> GestureSideAction.NONE
}

/** The video scale presets the fullscreen zoom button cycles. */
enum class VideoScaleMode(val label: String) {
    FIT("Fit"),
    FIT_WIDTH("Fill W"),
    FIT_HEIGHT("Fill H"),
    CROP("Crop"),
}

/** The preset-magnet steps for the free pinch zoom (multipliers over fit). */
val ZOOM_SNAP_STEPS = listOf(1f, 1.25f, 1.5f, 2f, 3f)
private val ZOOM_MAX = 4f

/** Gesture wiring, read from preferences by the watch screen. */
data class PlayerGestureConfig(
    val enabled: Boolean = false,
    val rightSide: GestureSideAction = GestureSideAction.VOLUME,
    val leftSide: GestureSideAction = GestureSideAction.BRIGHTNESS,
    val horizontalSeek: Boolean = true,
    /** Pinch-to-zoom (fullscreen only — the watch screen wires it per mode). */
    val zoomEnabled: Boolean = false,
    /**
     * The CENTER-third vertical swipes (round 132): up/down actions fired
     * ONCE per gesture after the travel threshold. Portrait wires cinema +
     * fullscreen; landscape wires exit-to-portrait. `null` = the center is
     * an inert dead zone (the reserved area).
     */
    val centerSwipeUpAction: (() -> Unit)? = null,
    val centerSwipeDownAction: (() -> Unit)? = null,
    /**
     * The center-swipe travel threshold as a fraction of the detector's
     * height. Fullscreen (tall surface) uses the default; the portrait 16:9
     * box raises it — 12% of ~210dp is a pocket-trigger.
     */
    val centerTriggerFraction: Float = PlayerGestureState.CENTER_TRIGGER_FRACTION,
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

    /**
     * TRUE while a horizontal seek gesture is driving playback — the controls
     * read this to suppress the buffering spinner and the center play/±10s
     * buttons ("the loading should not show while I am seeking").
     */
    val isSeekGestureActive: Boolean
        get() = activeKind == PlayerGestureKind.SEEK

    /**
     * Which third of the screen the gesture started on. The OSD displays on
     * the OPPOSITE side (the round-132 report: a right-side swipe's panel
     * "gets covered under my finger, so it should show on the left side").
     */
    var startedOnRightSide by mutableStateOf(false)
        private set

    /** VOLUME/BRIGHTNESS: 0..100. SEEK: the absolute target in seconds. */
    var level by mutableStateOf(0f)
        private set
    /** SEEK: the signed delta from the drag-start position, in seconds. */
    var seekDeltaSec by mutableStateOf(0)
        private set

    // The center-swipe actions — plain fields (not constructor state) so the
    // controls can refresh them EVERY recomposition (the lambda identities
    // change with the cinema/mode state; the detector must never fire a
    // stale one).
    internal var centerSwipeUpAction: (() -> Unit)? = null
    internal var centerSwipeDownAction: (() -> Unit)? = null

    private var startOffset = Offset.Zero
    private var axisLocked = false
    private var dead = false
    private var centerActionFired = false

    private var startVolume = 0f
    private var volumeMax = 100f
    private var startBrightness = 0.5f
    private var startPositionSec = 0
    private var durationSec = 0
    private var lastLiveSeekAt = 0L
    private var window: Window? = null
    private var onSeekTo: ((Int) -> Unit)? = null

    /**
     * Restores the system brightness override. Runs when the controls overlay
     * is disposed so a dimmed window never leaks into the rest of the app.
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
        centerActionFired = false
        activeKind = null
        kind = null
        startedOnRightSide = offset.x >= size.width * 2f / 3f
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
        }

        // The center third is RESERVED (round 132): vertical swipes there are
        // never volume/brightness — they carry the mode actions instead.
        val inCenterThird = !startedOnRightSide &&
            startOffset.x >= size.width / 3f &&
            startOffset.x < size.width * 2f / 3f

        if (abs(dy) > abs(dx)) {
            // ── Vertical ──
            if (inCenterThird) {
                // Fires the configured center action ONCE past the travel
                // threshold (a short accidental drift past the slop must not
                // flip modes); nothing configured = an inert dead zone.
                if (!centerActionFired &&
                    abs(dy) >= size.height * config.centerTriggerFraction
                ) {
                    centerActionFired = true
                    dead = true
                    val action = if (dy < 0) {
                        centerSwipeUpAction ?: config.centerSwipeUpAction
                    } else {
                        centerSwipeDownAction ?: config.centerSwipeDownAction
                    }
                    action?.invoke()
                }
                return
            }
            if (activeKind == null) {
                // The master gate: with gestures OFF only the center actions run.
                if (!config.enabled) {
                    dead = true
                    return
                }
                val action = if (startedOnRightSide) config.rightSide else config.leftSide
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
                else -> Unit
            }
        } else {
            // ── Horizontal: the live seek (works from ANY column — the center
            // reservation only covers the VERTICAL area). ──
            if (activeKind == null) {
                if (!config.enabled || !config.horizontalSeek || durationSec <= 0) {
                    dead = true
                    return
                }
                activeKind = PlayerGestureKind.SEEK
                lastLiveSeekAt = 0L
                kind = activeKind
            }
            // ROUND 132: a FULL-WIDTH drag ≈ SEEK_RANGE_SEC seconds (the
            // device report: the whole episode across one screen width was
            // "way too much"). Clamped to the episode's real bounds.
            val deltaSec = (dx / size.width.toFloat().coerceAtLeast(1f) * SEEK_RANGE_SEC).roundToInt()
            seekDeltaSec = deltaSec
            level = (startPositionSec + deltaSec).toFloat().coerceIn(0f, durationSec.toFloat())
            val now = System.currentTimeMillis()
            if (now - lastLiveSeekAt >= LIVE_SEEK_INTERVAL_MS) {
                lastLiveSeekAt = now
                onSeekTo?.invoke(level.roundToInt())
            }
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
        /** Touch slop before the axis locks (px) — density-scaled at attach. */
        const val GESTURE_SLOP_PX = 24f
        /** How often the horizontal swipe actually seeks while dragging. */
        const val LIVE_SEEK_INTERVAL_MS = 450L
        /** OSD auto-hide safety net when a gesture never sends its end event. */
        const val OSD_TIMEOUT_MS = 2500L
        /** A full-width horizontal drag maps to this many seconds (round 132). */
        const val SEEK_RANGE_SEC = 300
        /** The center-swipe travel threshold, as a fraction of the height. */
        const val CENTER_TRIGGER_FRACTION = 0.12f
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
 * The drag detector — chain it on the SAME node as the tap detector
 * (`Modifier.pointerInput(tap).pointerInput(gestures)`). The round-131
 * build placed it on a LATER SIBLING Box and the device round showed
 * fullscreen taps died (the sibling swallowed the hit path). Same-node
 * pointer inputs is the canonical tap+drag pairing.
 *
 * [window] feeds the brightness gesture; `null` (no Activity) just disables it.
 * [positionSec]/[durationSec] are provider lambdas so the detector does NOT
 * restart (killing in-flight drags) every time the playback position ticks.
 *
 * The center-swipe actions are read from the STATE (refreshed every
 * recomposition by the controls) — never from the captured [config] alone,
 * whose lambda fields may be stale by the time a long gesture fires.
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
            if (!gesturePipelineAlive(config, state)) return@detectDragGestures
            state.begin(offset, size, config, positionSec(), durationSec(), onSeekTo)
        },
        onDrag = { change, _ ->
            if (!gesturePipelineAlive(config, state)) return@detectDragGestures
            state.drag(change.position, size, config, window)
        },
        onDragEnd = { state.end() },
        onDragCancel = { state.cancel() },
    )
}

/**
 * TRUE when the drag pipeline has any reason to run: the master gate is on,
 * or a center-swipe action exists (cinema/fullscreen must work even with
 * the volume/seek gestures switched off in settings).
 */
private fun gesturePipelineAlive(config: PlayerGestureConfig, state: PlayerGestureState): Boolean =
    config.enabled ||
        config.centerSwipeUpAction != null || config.centerSwipeDownAction != null ||
        state.centerSwipeUpAction != null || state.centerSwipeDownAction != null

/**
 * The pinch zoom detector — activates ONLY when a second finger lands
 * (single-finger streams pass through unconsumed so the tap/drag detectors
 * keep working). Consumes from the moment the pinch engages.
 */
suspend fun PointerInputScope.detectTwoFingerZoom(
    onZoom: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                // ANY multi-touch consumes — a two-finger PAN must not drive
                // the single-finger seek, and a first-finger drag that gains a
                // second finger must stand down before more live seeks fire.
                pinching = true
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f) onZoom(zoomChange)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) onEnd()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Video zoom (round 132) — the preset cycle button + the free pinch
// ════════════════════════════════════════════════════════════════════════════

/**
 * The fullscreen video scale state: the preset [VideoScaleMode] cycle AND the
 * free pinch factor (which magnet-snaps to [ZOOM_SNAP_STEPS] on release).
 * Everything stays CENTERED (pan 0 — the zoom anchors at the middle).
 */
class VideoZoomState internal constructor() {

    var mode by mutableStateOf(VideoScaleMode.FIT)
        private set
    /** The free pinch multiplier over the preset scale (1 = none). */
    var freeZoom by mutableStateOf(1f)
        private set
    /** The player surface size (the controls' root Box sets it via onSizeChanged). */
    var containerSize by mutableStateOf(IntSize.Zero)

    /** The button: cycles FIT → Fill W → Fill H → Crop → FIT; resets the pinch. */
    fun cyclePreset() {
        mode = when (mode) {
            VideoScaleMode.FIT -> VideoScaleMode.FIT_WIDTH
            VideoScaleMode.FIT_WIDTH -> VideoScaleMode.FIT_HEIGHT
            VideoScaleMode.FIT_HEIGHT -> VideoScaleMode.CROP
            VideoScaleMode.CROP -> VideoScaleMode.FIT
        }
        freeZoom = 1f
        applyToMpv()
    }

    /** The pinch: multiplies the free factor (anchored at the center, OVER
     * the active preset — pinching from Crop must not snap back to fit). */
    fun pinchZoom(multiplier: Float) {
        freeZoom = (freeZoom * multiplier).coerceIn(1f, ZOOM_MAX)
        applyToMpv()
    }

    /** The magnet: snap the released pinch to the nearest preset step. */
    fun snapOnRelease() {
        freeZoom = ZOOM_SNAP_STEPS.minByOrNull { abs(it - freeZoom) } ?: freeZoom
        applyToMpv()
    }

    /** Leaving fullscreen resets everything (a DisposableEffect calls this). */
    fun reset() {
        mode = VideoScaleMode.FIT
        freeZoom = 1f
        runCatching { MPVLib.setPropertyDouble("video-zoom", 0.0) }
    }

    /** The effective multiplier (the preset's fill scale × the free pinch). */
    fun effectiveScale(): Float {
        val vw = runCatching { MPVLib.getPropertyInt("width") ?: 0 }.getOrDefault(0)
        val vh = runCatching { MPVLib.getPropertyInt("height") ?: 0 }.getOrDefault(0)
        val cw = containerSize.width
        val ch = containerSize.height
        val preset = if (mode == VideoScaleMode.FIT || vw <= 0 || vh <= 0 || cw <= 0 || ch <= 0) {
            1f
        } else {
            val s0 = minOf(cw.toFloat() / vw, ch.toFloat() / vh)
            val fittedW = (vw * s0).coerceAtLeast(1f)
            val fittedH = (vh * s0).coerceAtLeast(1f)
            when (mode) {
                VideoScaleMode.FIT_WIDTH -> cw.toFloat() / fittedW
                VideoScaleMode.FIT_HEIGHT -> ch.toFloat() / fittedH
                VideoScaleMode.CROP -> maxOf(cw.toFloat() / fittedW, ch.toFloat() / fittedH)
                VideoScaleMode.FIT -> 1f
            }
        }
        return preset * freeZoom
    }

    private fun applyToMpv() {
        val scale = effectiveScale().coerceAtLeast(0.05f)
        val zoom = ln(scale.toDouble()) / ln(2.0)
        runCatching { MPVLib.setPropertyDouble("video-zoom", zoom) }
    }
}

/** The fullscreen video zoom state (resets when the fullscreen overlay leaves). */
@Composable
fun rememberVideoZoomState(): VideoZoomState {
    val state = remember { VideoZoomState() }
    DisposableEffect(state) {
        onDispose { state.reset() }
    }
    return state
}

// ════════════════════════════════════════════════════════════════════════════
//  Gesture OSD — opposite side, fixed width, MAX/mute states, shadows, theme
// ════════════════════════════════════════════════════════════════════════════

/**
 * The gesture overlay. ROUND 132: renders on the side OPPOSITE the finger,
 * the % label lives in a fixed-width slot (MAX at the cap, a muted icon +
 * "Muted" at volume 0), the panel + bar carry shadows, and the fill bar /
 * seek accents use the theme color. Renders nothing when idle.
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

    // The panel shows on the side OPPOSITE the finger.
    val displayOnRight = !state.startedOnRightSide

    when (kind) {
        PlayerGestureKind.VOLUME -> SideGestureOsd(
            icon = Icons.Default.VolumeUp,
            mutedIcon = Icons.Default.VolumeOff,
            isVolume = true,
            label = "Volume",
            level = state.level,
            displayOnRight = displayOnRight,
            modifier = modifier,
        )
        PlayerGestureKind.BRIGHTNESS -> SideGestureOsd(
            icon = Icons.Default.BrightnessHigh,
            mutedIcon = null,
            isVolume = false,
            label = "Brightness",
            level = state.level,
            displayOnRight = displayOnRight,
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
    mutedIcon: ImageVector?,
    isVolume: Boolean,
    label: String,
    level: Float,
    displayOnRight: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = isVolume && level <= 0.5f
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(if (displayOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!displayOnRight) {
                VerticalLevelBar(level = level)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                // The panel shadow (the round-132 "give it some shadow").
                modifier = Modifier.shadow(10.dp, RoundedCornerShape(16.dp)),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    // Volume at 0 smoothly crossfades to the muted icon.
                    Crossfade(
                        targetState = muted && mutedIcon != null,
                        label = "gestureOsdIcon",
                    ) { isMuted ->
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else icon,
                            contentDescription = if (isMuted) "Muted" else label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    // FIXED-WIDTH label slot — 99→100 and "MAX" can't shift the panel.
                    Box(
                        modifier = Modifier.width(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                muted && isVolume -> "Muted"
                                level >= 99.5f -> "MAX"
                                else -> "${level.roundToInt()}%"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (displayOnRight) {
                Spacer(modifier = Modifier.width(10.dp))
                VerticalLevelBar(level = level)
            }
        }
    }
}

/** The vertical fill bar beside the side-gesture panel (shadowed, theme-colored). */
@Composable
private fun VerticalLevelBar(level: Float, modifier: Modifier = Modifier) {
    val fill = (level.coerceIn(0f, 100f) / 100f)
    Box(
        modifier = modifier
            .width(6.dp)
            .height(120.dp)
            .shadow(3.dp, RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .fillMaxHeight(fill)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
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
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.shadow(10.dp, RoundedCornerShape(16.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // The seek accents take the THEME color (the round-132 order).
                Icon(
                    if (deltaSec >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = if (deltaSec >= 0) "Seek forward" else "Seek backward",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "${if (deltaSec >= 0) "+" else "-"}${formatTime(abs(deltaSec))}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatTime(targetSec),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** The pinch-zoom readout ("1.5x") — centered, theme-colored, fades with [visible]. */
@Composable
fun ZoomOsd(
    visible: Boolean,
    factor: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.shadow(10.dp, RoundedCornerShape(16.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.AspectRatio,
                    contentDescription = "Zoom",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label ?: "${((factor * 100).roundToInt()) / 100f}x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
