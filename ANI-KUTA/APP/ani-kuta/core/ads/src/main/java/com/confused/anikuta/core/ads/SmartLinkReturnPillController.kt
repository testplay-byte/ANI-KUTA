package com.confused.anikuta.core.ads

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.confused.anikuta.core.common.Logger

/**
 * Shows/hides the floating return pill (D-443, D-448, rebuilt D-454) — a
 * system overlay window (TYPE_APPLICATION_OVERLAY) that floats over the
 * USER'S BROWSER (bottom-center, a thumb-reach above the bottom edge) while
 * the smart-link ad is in progress: a compact countdown capsule first, and
 * once the min-time elapses the pill grows and its "Go back" button appears.
 *
 * # The D-454 fixes
 *
 * - **The window is sized ONCE.** The view is constructed + measured in its
 *   READY shape before the window is added; the window takes that measured
 *   size and the surface never resizes — the grow/exit animations are pure
 *   property transforms inside the surface (the v1.1.5 stutter came from
 *   animating a window resize).
 * - **Touch pass-through by state.** While counting the window carries
 *   FLAG_NOT_TOUCHABLE (nothing about the pill is interactive yet — every
 *   touch, including over the pill, goes to the browser). The view calls
 *   [onReady] when the countdown completes; the flag is cleared and the pill
 *   becomes tappable.
 * - **The countdown is synced to the app's actual ON_STOP** — the exact
 *   signal the coordinator's outside-clock measures — so the ring can no
 *   longer run ahead of the gate (the "returned early but the pill showed
 *   success" report). A 2.5s fallback starts the countdown anyway if no
 *   ON_STOP ever lands (e.g. odd multi-window timing).
 * - **hide(returnedTooEarly)** picks the exit mark: the green check on
 *   success, the RED X when the coordinator judged the return too early.
 */
class SmartLinkReturnPillController {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The currently-shown pill, null when hidden. Main thread only. */
    private var active: ActivePill? = null

    private class ActivePill(
        val windowManager: WindowManager,
        val view: ReturnPillView,
        val params: WindowManager.LayoutParams,
        val stopObserver: DefaultLifecycleObserver,
        val fallbackStart: Runnable,
    )

    /**
     * Shows the pill over whatever is on screen (the browser). Must be called
     * on the main thread (the Continue / Try-again tap handlers are). The
     * window is sized ONCE to the pill's pre-measured ready shape. A pill
     * already showing is replaced (fresh countdown) — the Try-again loop
     * re-shows it per attempt.
     *
     * @param colors theme colors captured from the interstitial's composition.
     * @param durationMs the countdown length = `SmartLinkConfig.minTimeOutsideMs`
     *   (the same threshold the coordinator judges the return with).
     */
    fun show(context: Context, colors: ReturnPillColors, durationMs: Long) {
        if (!Settings.canDrawOverlays(context.applicationContext)) {
            Logger.d(TAG) { "overlay permission not granted — no return pill (the ad flow continues as before)" }
            return
        }
        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Logger.w(TAG) { "no WindowManager — no return pill" }
            return
        }

        // Replace (not stack) an existing pill — the Try-again loop re-shows.
        // The old pill is removed INSTANTLY (no exit animation — it would
        // overlap the fresh pill's entrance).
        hideInternal(immediate = true)

        val density = appContext.resources.displayMetrics.density

        var countdownStarted = false
        val view = ReturnPillView(
            context = appContext,
            pillColors = colors,
            totalMs = durationMs,
            onGoBack = { bringAppToForeground(appContext) },
            onReady = {
                // The pill is now interactive — give the window back its
                // touches (a params update, not a surface resize).
                val pill = active
                if (pill != null) {
                    pill.params.flags = pill.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    runCatching { pill.windowManager.updateViewLayout(pill.view, pill.params) }
                    Logger.d(TAG) { "pill ready — FLAG_NOT_TOUCHABLE cleared" }
                }
            },
        )

        // The countdown starts when the app ACTUALLY backgrounds (ON_STOP —
        // the coordinator's own clock), with a fallback for odd timing.
        val stopObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!countdownStarted) {
                    countdownStarted = true
                    view.startCountdown()
                }
            }
        }
        val fallbackStart = Runnable {
            if (!countdownStarted && active != null) {
                Logger.w(TAG) { "no ON_STOP within the fallback window — starting the countdown anyway" }
                countdownStarted = true
                view.startCountdown()
            }
        }

        val params = WindowManager.LayoutParams().apply {
            // The D-454 jitter fix: the window is sized ONCE — the surface
            // never resizes for the pill's whole life. D-468: the size is the
            // pre-measured READY shape PLUS generous slack — the v1.1.7
            // device round still showed the ready pill's sides and the pop's
            // top/bottom clipped, so an exact-fit window is too tight (sub-pixel
            // text-measure drift compounds). The capsule is centered in the
            // bigger window; the extra margin is non-interactive while
            // counting (FLAG_NOT_TOUCHABLE) and inert when ready.
            width = view.measuredWidth + (40 * density).toInt()
            height = view.measuredHeight + (24 * density).toInt()
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            // NOT_FOCUSABLE: the browser keeps its keyboard/input. NOT_TOUCHABLE
            // while counting: every touch (including over the pill) passes to
            // the browser — nothing about the pill is interactive yet. Cleared
            // via updateViewLayout when the countdown completes (onReady).
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            format = PixelFormat.TRANSLUCENT
            // D-448: bottom-center, a thumb-reach above the bottom edge —
            // clear of the browser's own bottom chrome / gesture bar.
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            // SecurityException (consent revoked mid-flight), BadTokenException
            // (odd OEM window-token rules) — never break the ad flow over the pill.
            Logger.e(TAG, e) { "failed to add the return-pill overlay window — continuing without it" }
            return
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(stopObserver)
        mainHandler.postDelayed(fallbackStart, FALLBACK_START_MS)
        active = ActivePill(windowManager, view, params, stopObserver, fallbackStart)
        Logger.i(TAG) { "return pill shown over the browser (window pre-sized ${params.width}x${params.height}, countdown ${durationMs}ms from ON_STOP)" }
    }

    /**
     * Hides the pill with the exit animation. Idempotent.
     *
     * @param returnedTooEarly true when the coordinator judged the return too
     *   early (AdTryAgain) — the exit pops a RED X instead of the green check.
     */
    fun hide(returnedTooEarly: Boolean) {
        hideInternal(returnedTooEarly)
    }

    private fun hideInternal(returnedTooEarly: Boolean = false, immediate: Boolean = false) {
        val pill = active ?: return
        active = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(pill.stopObserver)
        mainHandler.removeCallbacks(pill.fallbackStart)
        if (immediate) {
            // The replace path — no animation, no overlap with the new pill.
            try {
                pill.windowManager.removeViewImmediate(pill.view)
            } catch (e: Exception) {
                Logger.d(TAG) { "return pill already detached (${e.javaClass.simpleName})" }
            }
            return
        }
        pill.view.beginExit(returnedTooEarly) {
            try {
                pill.windowManager.removeView(pill.view)
            } catch (e: Exception) {
                // Already removed (e.g. the view was detached by the system).
                Logger.d(TAG) { "return pill already detached (${e.javaClass.simpleName})" }
            }
        }
    }

    /** The pill's "Go back" action: foreground ANI-KUTA. The app's ON_START
     * fires → AppLifecycleObserver emits → the coordinator completes (or
     * Try-again-s) the ad. Decoupled from MainActivity: the launch intent
     * keeps `:core:ads` free of any :app / navigation knowledge. */
    private fun bringAppToForeground(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        if (launch == null) {
            Logger.w(TAG) { "no launch intent for our own package — cannot go back" }
            return
        }
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "failed to bring the app back to the foreground" }
        }
    }

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:ReturnPillCtl"
        /** If no ON_STOP lands within this window, start the countdown anyway. */
        private const val FALLBACK_START_MS = 2_500L
    }
}
