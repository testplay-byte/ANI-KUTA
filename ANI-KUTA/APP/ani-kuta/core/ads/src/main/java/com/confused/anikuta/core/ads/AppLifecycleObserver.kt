package com.confused.anikuta.core.ads

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Observes app-level foreground/background transitions via [ProcessLifecycleOwner]
 * so the smart-link ad can measure how long the user spent OUTSIDE the app
 * (in the browser) — the "try again" gate's core mechanic.
 *
 * # Why ProcessLifecycleOwner (not Activity lifecycle)
 *
 * The smart-link flow: user taps Continue → the ad URL opens in the user's
 * browser (a separate Activity in a separate app) → our app goes to
 * background → the user browses → returns to our app. We need the APP-LEVEL
 * background/foreground transitions, not the Activity-level ones (a Dialog
 * dismissing is NOT an app background). [ProcessLifecycleOwner] fires ON_STOP
 * only when the LAST activity stops (whole app backgrounded) + ON_START when
 * the FIRST activity starts (app returns). That's exactly the signal we want.
 *
 * # Why not reuse :core:activity-tracker
 *
 * The §8 research sub-agent confirmed that module is a batched SQLDelight
 * event logger (track SEARCH/WATCH_START/etc.) — it does NOT observe
 * ProcessLifecycleOwner. So this observer is purpose-built here. (CORE_RULES
 * §5: reuse before write — we checked; there's nothing to reuse.)
 *
 * # Lifecycle
 *
 * The observer must be [register]ed on [ProcessLifecycleOwner] while the ad
 * interstitial is active. The interstitial's `DisposableEffect` registers on
 * show + unregisters on dismiss. [elapsedOutsideMs] + [onReturnToForeground]
 * are no-ops until the first ON_STOP/ON_START cycle.
 */
class AppLifecycleObserver : DefaultLifecycleObserver {

    /** Wall-clock millis of the last ON_STOP (app → background). 0 = never stopped. */
    private val _onStopTimestamp = AtomicLong(0L)

    /**
     * Emits Unit on ON_START when the app returns from a prior ON_STOP.
     * Collected by the interstitial while in [AdGateState.AdInProgress] to
     * trigger the "did the user spend enough time outside?" check.
     *
     * `extraBufferCapacity = 4` + [BufferOverflow.DROP_OLDEST] — we never want
     * a return-to-foreground event to block/suspend the lifecycle callback;
     * dropping the oldest on overflow is fine (only the latest matters).
     */
    private val _onReturnToForeground = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val onReturnToForeground: SharedFlow<Unit> = _onReturnToForeground.asSharedFlow()

    override fun onStop(owner: LifecycleOwner) {
        val now = System.currentTimeMillis()
        _onStopTimestamp.set(now)
        Logger.d(TAG) { "ON_STOP — app backgrounded at $now" }
    }

    override fun onStart(owner: LifecycleOwner) {
        val stopTs = _onStopTimestamp.get()
        if (stopTs > 0L) {
            val elapsed = System.currentTimeMillis() - stopTs
            Logger.d(TAG) { "ON_START — app returned to foreground (was backgrounded ${elapsed}ms)" }
            // Non-suspending emit — safe from a lifecycle callback (no coroutine scope).
            _onReturnToForeground.tryEmit(Unit)
        } else {
            Logger.d(TAG) { "ON_START — first start (no prior ON_STOP), ignoring" }
        }
    }

    /**
     * Ms the user spent outside the app, measured from the last ON_STOP to now.
     * Returns 0 if the app was never backgrounded (no ON_STOP recorded) — the
     * coordinator treats 0 as "too quick" → "Try again".
     */
    fun elapsedOutsideMs(now: Long = System.currentTimeMillis()): Long {
        val stopTs = _onStopTimestamp.get()
        return if (stopTs == 0L) 0L else (now - stopTs).coerceAtLeast(0L)
    }

    /** Clears the recorded ON_STOP timestamp. Called by the coordinator right
     * before opening the browser URL so a stale (earlier) ON_STOP doesn't
     * inflate the elapsed time. */
    fun reset() {
        _onStopTimestamp.set(0L)
        Logger.d(TAG) { "stop timestamp reset" }
    }

    /** Adds this observer to [ProcessLifecycleOwner]. Idempotent. */
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Logger.d(TAG) { "registered on ProcessLifecycleOwner" }
    }

    /** Removes this observer from [ProcessLifecycleOwner]. Idempotent. */
    fun unregister() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        Logger.d(TAG) { "unregistered from ProcessLifecycleOwner" }
    }

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:Lifecycle"
    }
}
