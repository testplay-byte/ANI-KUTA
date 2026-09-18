package com.confused.anikuta.core.ads

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.confused.anikuta.core.common.Logger

/**
 * Shows/hides the floating return pill (D-443, D-448) — a system overlay
 * window (TYPE_APPLICATION_OVERLAY) that floats over the USER'S BROWSER
 * (bottom-center, a thumb-reach above the bottom edge) while the smart-link
 * ad is in progress: a compact countdown capsule first, and once the
 * min-time elapses the pill grows and its "Go back" button appears —
 * tapping it brings ANI-KUTA back to the foreground.
 *
 * # Who calls it
 *
 * The interstitial (`SmartLinkAdInterstitial`) drives it from the coordinator
 * state it already observes:
 * - `AdGateState.AdInProgress` (the browser is open) → [show] — the pill
 *   floats over the browser counting down `SmartLinkConfig.minTimeOutsideMs`.
 * - Any other state (Idle / AdPending / AdTryAgain — the user is IN the app,
 *   where the in-app interstitial is the right UI) → [hide].
 *
 * The show fires while the app is still foregrounded (the state flips in the
 * Continue-tap handler, before the browser covers the app), and the overlay
 * window survives the app going to background — that's the whole point.
 *
 * # Permission reality (honest, not hidden)
 *
 * A view floating over ANOTHER app requires the SYSTEM_ALERT_WINDOW
 * "Display over other apps" consent (Android 8+ always). [show] checks
 * [Settings.canDrawOverlays] and SILENTLY SKIPS when it's absent — the ad
 * flow is never blocked on it (the user just sees the previous no-pill
 * behavior; the return-timing system works identically without the pill).
 * No settings-redirect nag: the ad system's standing rule is non-intrusive.
 *
 * # Lifecycle safety
 *
 * - [hide] is idempotent; the view is removed via [ReturnPillView.animateOut]
 *   so the exit is animated (§22).
 * - A SAFETY timeout removes the pill after `durationMs + 10 minutes` even
 *   if nothing else does (orphaned-overlay guard: e.g. the user never
 *   returns and the process dies — the window must not outlive its purpose
 *   forever; the system may also kill the process, which drops the window).
 * - All window operations run on the main thread via [mainHandler] (the
 *   interstitial calls from composition = main; the guard is belt+braces).
 * - addView can throw (SecurityException / BadTokenException on odd OEMs) —
 *   caught + logged; the ad flow continues without the pill.
 */
class SmartLinkReturnPillController {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The currently-shown pill, null when hidden. Main thread only. */
    private var active: ActivePill? = null

    private class ActivePill(
        val windowManager: WindowManager,
        val view: ReturnPillView,
        val params: WindowManager.LayoutParams,
        val safetyRemoval: Runnable,
    )

    /**
     * Shows the pill over whatever is on screen (the browser). No-op when the
     * overlay permission is missing or the window can't be added. A pill
     * already showing is replaced (fresh countdown) — the "Try again" loop
     * re-shows it per attempt.
     *
     * @param colors theme colors captured from the interstitial's composition.
     * @param durationMs the countdown length = `SmartLinkConfig.minTimeOutsideMs`
     *   (the same threshold the coordinator judges the return with — the pill
     *   and the state machine can never disagree).
     */
    fun show(context: Context, colors: ReturnPillColors, durationMs: Long) {
        mainHandler.post { showInternal(context.applicationContext, colors, durationMs) }
    }

    /** Hides the pill if shown. Idempotent. */
    fun hide() {
        mainHandler.post { hideInternal(animated = true) }
    }

    private fun showInternal(appContext: Context, colors: ReturnPillColors, durationMs: Long) {
        if (!Settings.canDrawOverlays(appContext)) {
            Logger.d(TAG) { "overlay permission not granted — no return pill (the ad flow continues as before)" }
            return
        }
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Logger.w(TAG) { "no WindowManager — no return pill" }
            return
        }

        // Replace (not stack) an existing pill — the Try-again loop re-shows.
        hideInternal(animated = false)

        val density = appContext.resources.displayMetrics.density
        val view = ReturnPillView(appContext, colors, durationMs) { bringAppToForeground(appContext) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: the browser keeps its keyboard/input. NOT_TOUCH_MODAL:
            // touches OUTSIDE the pill's window bounds pass through to the browser —
            // the pill is WRAP_CONTENT so only its own footprint is interactive.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // D-448 (the v1.1.3 device round): the pill sits at the BOTTOM of
            // the screen — "it should show at the very bottom… with some
            // space" — not at the top. BOTTOM gravity + a positive y offset
            // floats it a thumb-reach above the bottom edge (clear of the
            // browser's own bottom chrome / gesture bar).
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

        val safetyRemoval = Runnable {
            Logger.w(TAG) { "return pill safety timeout — removing an orphaned pill" }
            hideInternal(animated = true)
        }
        mainHandler.postDelayed(safetyRemoval, durationMs + SAFETY_TIMEOUT_MS)
        active = ActivePill(windowManager, view, params, safetyRemoval)
        Logger.i(TAG) { "return pill shown over the browser (countdown ${durationMs}ms)" }
    }

    private fun hideInternal(animated: Boolean) {
        val pill = active ?: return
        active = null
        mainHandler.removeCallbacks(pill.safetyRemoval)
        if (animated) {
            pill.view.animateOut {
                try {
                    pill.windowManager.removeView(pill.view)
                } catch (e: Exception) {
                    // Already removed (e.g. the view was detached by the system).
                    Logger.d(TAG) { "return pill already detached (${e.javaClass.simpleName})" }
                }
            }
        } else {
            try {
                pill.windowManager.removeViewImmediate(pill.view)
            } catch (e: Exception) {
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
        /** Orphan guard: countdown + 10 minutes, then the pill removes itself. */
        private const val SAFETY_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
