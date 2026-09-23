package com.confused.anikuta.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
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
 *
 * # The D-564 lifecycle fixes (the v1.1.37 device round: "the You can go
 *   back floating overlay stays there forever… it does not disappear even
 *   if I close the application… automatically disappear after ten seconds
 *   if the user does not press the Go Back button")
 *
 * The old pill had exactly ONE exit: the coordinator's state change when
 * the user came back (the interstitial's `LaunchedEffect(state)` → hide).
 * A user who never came back — kept browsing, moved to other apps, closed
 * ANI-KUTA entirely — left the overlay floating FOREVER, because the
 * TYPE_APPLICATION_OVERLAY window belongs to the (still-cached) PROCESS,
 * not to any activity: nothing removes it. Two new guarantees close that
 * gap, both CONTAINED in this controller (the pill's visuals, the
 * coordinator's state machine, and the popup card are untouched):
 *
 * 1. **The ready window expires.** The moment the pill becomes tappable
 *    ("You can go back" + Go back), a [READY_AUTO_DISMISS_MS] timer starts.
 *    If the user does not press Go back inside it, the pill plays its
 *    normal exit (the green-check bubble — the stay itself DID complete;
 *    only the return offer expired) and the window is removed. The pill's
 *    total life is now bounded in EVERY scenario: ~5s countdown + 10s
 *    ready, no matter what the user does outside.
 * 2. **Closing the app dismisses the pill.** While a pill is alive the
 *    controller watches the application's LIVE-ACTIVITY COUNT (callbacks
 *    registered at show, unregistered at hide): at show time the app is
 *    foregrounded with exactly one activity (the single-activity UI), and
 *    when that LAST activity is destroyed — back-press finish, the recents
 *    swipe, a system kill of the activity — a "Go back" offer for a closed
 *    app is meaningless, so the pill dismisses right away. The check rides
 *    a one-looper-tick beat so a destroy-then-recreate swap (locale or
 *    density changes — the only recreations left; MainActivity's manifest
 *    configChanges already absorb rotation/uiMode) can never zero the
 *    count spuriously.
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
        /** D-564: the ready-state expiry timer (cancelled on every hide). */
        val readyTimeout: Runnable,
        /** D-564: the app-close watcher (unregistered on every hide). */
        val app: Application?,
        val closeWatcher: Application.ActivityLifecycleCallbacks,
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
        // overlap the fresh pill's entrance); hideInternal also cancels the
        // old pill's timers and unregisters its close watcher.
        hideInternal(immediate = true)

        val density = appContext.resources.displayMetrics.density

        // D-564 (1): the ready window's expiry. Armed the moment the pill
        // turns READY (see onReady below); firing it hides the pill with the
        // graceful green-check exit. hideInternal cancels it on every path.
        val readyTimeout = Runnable {
            if (active == null) return@Runnable
            Logger.i(TAG) { "ready window expired (${READY_AUTO_DISMISS_MS / 1000}s, no Go back) — auto-dismissing the return pill" }
            hideInternal(returnedTooEarly = false)
        }

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
                    // D-564 (1): the return offer now EXPIRES — if the user
                    // does not press Go back within the window, the pill
                    // dismisses itself instead of floating forever.
                    mainHandler.postDelayed(pill.readyTimeout, READY_AUTO_DISMISS_MS)
                    Logger.d(TAG) { "pill ready — FLAG_NOT_TOUCHABLE cleared, auto-dismiss armed (${READY_AUTO_DISMISS_MS / 1000}s)" }
                }
            },
        )

        // D-564 (2): the app-close watcher. Registered only while a pill is
        // alive (unregistered in hideInternal). The count starts at ONE —
        // show() runs inside the foregrounded single-activity UI's tap
        // handler, so exactly one activity of ours exists right now. When
        // the last one is destroyed without a replacement (true close), the
        // pill's whole purpose (bring the app back) is gone → dismiss. The
        // posted beat lets a destroy-then-recreate swap (locale/density
        // changes) land its onCreate before the zero-check reads the count.
        var liveActivities = 1
        val closeWatcher = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                liveActivities++
            }

            override fun onActivityDestroyed(activity: Activity) {
                liveActivities--
                if (liveActivities > 0) return
                mainHandler.post {
                    if (liveActivities <= 0 && active != null) {
                        Logger.i(TAG) { "the app's last activity is gone (closed) — dismissing the return pill" }
                        hideInternal(returnedTooEarly = false)
                    }
                }
            }

            // Not interesting for the pill: foreground/background transitions
            // are the ProcessLifecycleOwner's job (the countdown sync), and
            // the coordinator owns the ad session state.
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        }
        val application = appContext as? Application
        if (application == null) {
            // Never seen in practice (applicationContext IS the Application);
            // the pill would just lose the close-watch and rely on the expiry.
            Logger.w(TAG) { "application context is not an Application — the app-close watch is off" }
        }

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

        // Register the close-watch only after the window is actually up — a
        // failed add must not leak a registered watcher.
        application?.registerActivityLifecycleCallbacks(closeWatcher)
        ProcessLifecycleOwner.get().lifecycle.addObserver(stopObserver)
        mainHandler.postDelayed(fallbackStart, FALLBACK_START_MS)
        active = ActivePill(
            windowManager = windowManager,
            view = view,
            params = params,
            stopObserver = stopObserver,
            fallbackStart = fallbackStart,
            readyTimeout = readyTimeout,
            app = application,
            closeWatcher = closeWatcher,
        )
        Logger.i(TAG) { "return pill shown over the browser (window pre-sized ${params.width}x${params.height}, countdown ${durationMs}ms from ON_STOP, auto-dismiss ${READY_AUTO_DISMISS_MS / 1000}s after ready)" }
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
        // D-564: every hide path disarms the new lifecycle machinery — the
        // expiry timer AND the app-close watcher (both directions of the
        // replace path included, so the old pill's watch never survives).
        mainHandler.removeCallbacks(pill.readyTimeout)
        runCatching { pill.app?.unregisterActivityLifecycleCallbacks(pill.closeWatcher) }
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

        /**
         * D-564: how long the tappable "You can go back" offer stays up
         * before the pill dismisses itself — the user's exact spec: the
         * overlay "will automatically disappear after ten seconds if the
         * user does not press the Go Back button".
         */
        private const val READY_AUTO_DISMISS_MS = 10_000L
    }
}
