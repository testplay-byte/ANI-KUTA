package com.confused.anikuta.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 108 (D-713): THE SHARED PREVIEW-COLLAPSE SCROLL — ONE controller for
//  BOTH episode-list settings screens (the details page's and the player
//  page's), replacing the two ~150-line copies that had drifted into the
//  v1.1.64 device round's two defects:
//
//  • THE ENTRY SCROLL — "whenever I enter the … episode list settings, then
//    what should happen is that it should always be scrolled to the very top
//    … apparently sometimes … they were scrolled to the very bottom. Like it
//    remembered the previous situations." The options list's
//    rememberLazyListState is rememberSaveable-backed, so the settings
//    navigation's backstack restore brought the OLD scroll offset (and the
//    stale collapsed preview) back. [resetOnEntry] forces the honest entry:
//    item 0, preview open — called once on entry by each screen (skipped
//    when a settings-search anchor is about to steer the scroll).
//
//  • THE FLICK CHOREOGRAPHY — "if I flicked my finger, then it would not
//    scroll automatically to the bottom … it should smoothly go on and
//    scroll the top section first, and then by scrolling it one of the
//    episodes will hide, and it will hide properly and smoothly as such.
//    And after it has hidden, then it will not allow the user to scroll for
//    a few bit for a few time, and after that it will automatically start
//    scrolling the bottom section as it is. And it will depend on how fast
//    the user scrolled." The v1.1.64 machinery had TWO holes: (1) a
//    -1000px/s threshold let weaker flicks through to the list, whose own
//    fling dispatches as NestedScrollSource.SideEffect — the connection's
//    first line early-returns on that source, so the list scrolled UNDER AN
//    OPEN PREVIEW; (2) the strong-flick momentum handoff drove the list
//    through LazyListState.dispatchRawDelta — the fragile, mutex-less path
//    that never moved on device. THE REWORK, in the user's exact order:
//
//      FLICK (down, preview open) → consume the fling →
//      ① the smooth animated HIDE (the first episode slides away) →
//      ② THE LOCK BEAT (~140ms: every user delta + fling swallowed — "it
//         will not allow the user to scroll for a few bit for a few time") →
//      ③ THE MOMENTUM HANDOFF — the list scrolls BY ITSELF through the
//         CANONICAL scroll-scope decay (`listState.scroll {
//         AnimationState.animateDecay { scrollBy } }` — the exact pattern
//         animateScrollBy is built on: mutex-serialized, velocity-
//         proportional, and cancelled naturally by a fresh touch). The decay
//         carries the ORIGINAL flick velocity, softened (friction 0.55 ≈
//         double the default glide) so the handoff reads like a native fling.
//
//  The DRAG semantics (D-557/D-558) are UNCHANGED: down-drags while open
//  feed the collapse phase first (the halfway latch snaps, then the same
//  gesture releases into the list); up-drags scroll the list first and only
//  the top-leftover expands the preview; an up-fling's leftover settles the
//  expansion. GRID never engages (the compressed-preview exemption); a
//  layout switch re-opens (the screens' LaunchedEffect calls [reopen]).
// ════════════════════════════════════════════════════════════════════════════

/**
 * The tuning constants — ONE place, both screens, documented per beat.
 *
 * ① [HIDE_ANIMATION_MS] — the smooth collapse after a flick (260ms, the
 *    drag-path snap's duration — one visual language for both entrances).
 * ② [LOCK_BEAT_MS] — the post-hide input lock ("a few bit for a few time"):
 *    ~140ms reads as a deliberate beat without testing the user's patience.
 *    (A Long because [delay] takes one; [HIDE_ANIMATION_MS] feeds
 *    [tween]'s Int durationMillis and stays Int.)
 * ③ [FLICK_MIN_VELOCITY] — the tap/noise floor separating a REAL flick
 *    from a tap / micro-drift. Below it the lift does NOT run the hide →
 *    lock → handoff choreography (a tap must never hide the preview) —
 *    but it is STILL swallowed whole: the list's own fling dispatches as
 *    SideEffect, which bypasses this connection, so letting even a weak
 *    velocity through would scroll the list UNDER AN OPEN PREVIEW — the
 *    exact v1.1.64 defect ("a weak flick behaves the SAME — no list
 *    movement under an open preview"). The settle window eases the
 *    preview back and the list never moves.
 */
private const val HIDE_ANIMATION_MS = 260
private const val LOCK_BEAT_MS = 140L
private const val FLICK_MIN_VELOCITY = 350f

/** The gap between the preview's two rows (the screens' Arrangement.spacedBy). */
private const val PREVIEW_ROW_GAP_DP = 8

/**
 * The controller. Created via [rememberPreviewCollapseScroll] (keyed on
 * [enabled] — a layout switch rebuilds it fresh, which is also the re-open).
 * The screen wires `connection` into the options LazyColumn's nestedScroll,
 * writes the measured first-row height into [firstRowHeightPx], and reads
 * [hidePx] for the clip+shift layout.
 *
 * [enabled] is the GRID EXEMPTION (SA1-F1, round-108 audit): false → every
 * connection callback early-returns — the compressed GRID preview never
 * engages the collapse, exactly like the inline code's explicit GRID
 * returns this controller replaced (the remember-key alone was not enough;
 * a mounted connection under GRID could still latch + clip + run the lock).
 */
class PreviewCollapseScrollState(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val density: androidx.compose.ui.unit.Density,
    private val enabled: Boolean = true,
) {
    /** The preview's first-row height in px — written by the screen's onSizeChanged. */
    var firstRowHeightPx by mutableFloatStateOf(0f)

    /** True once the preview has snapped collapsed (the phase anchor). */
    var collapsed by mutableStateOf(false)
        private set

    /** The 0..1 collapse fraction — drives the screen's clip+shift layout. */
    val progress = Animatable(0f)

    /**
     * The hide shift in px: the first row's height + the row gap, scaled by
     * the collapse fraction. This is EXACTLY the shift that hides episode 1
     * and pins episode 2 at the clip's top edge — the screen's clip+shift
     * layout reads it directly.
     */
    val hidePx: Float
        get() = progress.value * collapseDistancePx

    /** The flick sequence's lock — while set, every user delta + fling is swallowed. */
    private var inputLocked by mutableStateOf(false)

    private val dragAccumulator = mutableFloatStateOf(0f)
    private val collapseDistancePx: Float
        get() = (firstRowHeightPx + with(density) {
            PREVIEW_ROW_GAP_DP.dp.toPx()
        }).coerceAtLeast(1f)

    /** D-557: the crossed latch — one halfway crossing per continuous gesture. */
    private var crossedLatch = false

    private var settleJob: Job? = null
    private var sequenceJob: Job? = null

    /** The momentum handoff's decay — exponential, softened to a native-fling glide. */
    private val flingDecay = exponentialDecay<Float>(frictionMultiplier = 0.55f)

    // ── The public lifecycle ──────────────────────────────────────────────

    /**
     * ROUND 108: THE HONEST ENTRY — "it should always be scrolled to the very
     * top." Resets the restored scroll position (rememberSaveable brings the
     * old one back) and re-opens the preview. The screens call this ONCE on
     * entry, and only when NO settings-search anchor is about to steer the
     * scroll (the anchor's animateScrollToItem must win).
     */
    fun resetOnEntry() {
        sequenceJob?.cancel()
        settleJob?.cancel()
        inputLocked = false
        crossedLatch = false
        dragAccumulator.floatValue = 0f
        collapsed = false
        scope.launch {
            progress.snapTo(0f)
            runCatching { listState.scrollToItem(0) }
        }
    }

    /**
     * The layout-switch / GRID-transition re-open — a stale collapsed state
     * under GRID (whose connection never engages) would clip the preview
     * forever.
     */
    fun reopen() {
        sequenceJob?.cancel()
        settleJob?.cancel()
        inputLocked = false
        crossedLatch = false
        dragAccumulator.floatValue = 0f
        collapsed = false
        scope.launch { progress.snapTo(0f) }
    }

    // ── The gesture plumbing ──────────────────────────────────────────────

    private fun clearGesture() {
        crossedLatch = false
        dragAccumulator.floatValue = 0f
    }

    /**
     * The settle window: after the last delta of a gesture, clear the latch
     * and ease the preview to its phase anchor — a drag that ended before the
     * halfway point glides BACK instead of freezing midway.
     */
    private fun settleLater() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(180)
            clearGesture()
            progress.animateTo(
                targetValue = if (collapsed) 1f else 0f,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            )
        }
    }

    /** A drag-phase creep toward the snapped state (never past 45%). */
    private fun creepToward(target: Float) {
        scope.launch { progress.snapTo(target) }
    }

    val connection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // The GRID exemption (SA1-F1) — a disabled connection touches nothing.
            if (!enabled) return Offset.Zero
            // Programmatic scrolls (the search anchor's animateScrollToItem)
            // always pass — they are not user gestures.
            if (source == NestedScrollSource.SideEffect) return Offset.Zero
            // ② THE LOCK BEAT — the flick sequence's window swallows EVERY
            // user delta, either direction ("it will not allow the user to
            // scroll for a few bit for a few time").
            if (inputLocked) return available
            // DOWN while open: THE COLLAPSE PHASE — consume everything; the
            // list does not move until the preview has snapped collapsed.
            if (available.y < 0 && !collapsed) {
                settleJob?.cancel()
                sequenceJob?.cancel()
                if (!crossedLatch) {
                    dragAccumulator.floatValue += kotlin.math.abs(available.y)
                    val halfway = collapseDistancePx / 2f
                    if (dragAccumulator.floatValue >= halfway) {
                        crossedLatch = true
                        collapsed = true
                        dragAccumulator.floatValue = 0f
                        scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(HIDE_ANIMATION_MS, easing = FastOutSlowInEasing),
                            )
                        }
                    } else {
                        val ratio = (dragAccumulator.floatValue / halfway).coerceIn(0f, 1f)
                        creepToward(0.45f * ratio)
                    }
                }
                settleLater()
                // After the snap the connection RELEASES the same gesture —
                // the remaining deltas flow into the list.
                return if (crossedLatch) Offset.Zero else available
            }
            // UP (and everything else): never consume in PRE-scroll — the
            // list scrolls FIRST; the preview only expands from the
            // POST-scroll leftover.
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!enabled) return Offset.Zero
            if (source == NestedScrollSource.SideEffect) return Offset.Zero
            if (inputLocked) return available
            // UP-leftover: the list is at the very top and still has up delta
            // left — THE ONLY DOOR to the expansion.
            if (available.y > 0 && collapsed) {
                settleJob?.cancel()
                sequenceJob?.cancel()
                if (!crossedLatch) {
                    dragAccumulator.floatValue += available.y
                    val halfway = collapseDistancePx / 2f
                    if (dragAccumulator.floatValue >= halfway) {
                        crossedLatch = true
                        collapsed = false
                        dragAccumulator.floatValue = 0f
                        scope.launch {
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(HIDE_ANIMATION_MS, easing = FastOutSlowInEasing),
                            )
                        }
                    } else {
                        val ratio = (dragAccumulator.floatValue / halfway).coerceIn(0f, 1f)
                        creepToward(1f - 0.45f * ratio)
                    }
                }
                settleLater()
                return if (crossedLatch) Offset.Zero else available
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!enabled) return available
            if (inputLocked) return available
            // ANY downward fling velocity while the preview is open is
            // consumed — the list's own fling dispatches as SideEffect
            // (which bypasses this connection), so a pass-through of ANY
            // strength would scroll the list under the open preview.
            if (available.y < 0f && !collapsed) {
                // ① THE FLICK — past the tap floor, run the ordered
                // sequence: consume it, however fast, then smooth hide →
                // lock beat → momentum handoff. The OLD -1000f threshold
                // was the hole: weaker flicks slipped to the list's own
                // fling, whose SideEffect-source deltas bypass this
                // connection entirely.
                if (available.y < -FLICK_MIN_VELOCITY) {
                    val handoffVelocity = available.y
                    collapsed = true
                    clearGesture()
                    settleJob?.cancel()
                    inputLocked = true
                    sequenceJob = scope.launch {
                        // ① The smooth animated hide.
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(HIDE_ANIMATION_MS, easing = FastOutSlowInEasing),
                        )
                        // ② The lock beat.
                        delay(LOCK_BEAT_MS)
                        inputLocked = false
                        // ③ THE MOMENTUM HANDOFF — the canonical scroll-scope
                        // decay (the animateScrollBy pattern): mutex-serialized,
                        // carrying the ORIGINAL flick velocity ("it will depend
                        // on how fast the user scrolled"), and cancelled
                        // naturally when the user touches the list again.
                        listState.scroll {
                            var lastValue = 0f
                            AnimationState(
                                initialValue = 0f,
                                initialVelocity = handoffVelocity,
                            ).animateDecay(flingDecay) {
                                val delta = value - lastValue
                                lastValue = value
                                scrollBy(delta)
                            }
                        }
                    }
                    return Velocity.Zero
                }
                // Below the floor: a tap or a micro-drift — NOT a flick, so
                // no choreography (a tap must never hide the preview) — but
                // the velocity is STILL swallowed: a weak pass-through would
                // fling the list under the open preview through the
                // SideEffect bypass. The settle window (already armed by the
                // last drag delta, re-armed here) eases the preview back and
                // the list stays exactly where it was.
                clearGesture()
                settleLater()
                return Velocity.Zero
            }
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!enabled) return Velocity.Zero
            if (inputLocked) return available
            // UP-fling leftover: the list's own fling finished at the very
            // top with velocity to spare — settle the expansion now ("first
            // of all the bottom section should scroll to the very top", THEN
            // the preview opens).
            if (collapsed && available.y > 0f) {
                collapsed = false
                clearGesture()
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                )
                return available
            }
            return Velocity.Zero
        }
    }
}

/**
 * Remembers ONE controller per (screen, layout-ability). [enabled] is false
 * for the GRID layout (the compressed-preview exemption — its connection
 * never engages); the key rebuilds the controller on a layout switch, which
 * IS the re-open (a fresh controller starts open, unlatched, unlocked). The
 * disposal hook cancels a replaced controller's in-flight sequence — its
 * decay job scrolls the SHARED list state and must not outlive it.
 */
@Composable
fun rememberPreviewCollapseScroll(
    listState: LazyListState,
    enabled: Boolean,
): PreviewCollapseScrollState {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val controller = remember(enabled) {
        PreviewCollapseScrollState(scope, listState, density, enabled)
    }
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.reopen() }
    }
    return controller
}

/**
 * The entry-reset effect both screens mount — ONCE per screen entry (keyed
 * on Unit, so a mid-visit layout switch — which rebuilds the controller —
 * never re-triggers it). [hasAnchor] gates it: a settings-search landing
 * must keep its own animateScrollToItem target.
 */
@Composable
fun PreviewCollapseEntryReset(
    controller: PreviewCollapseScrollState,
    hasAnchor: Boolean,
) {
    LaunchedEffect(Unit) {
        if (!hasAnchor) controller.resetOnEntry()
    }
}
