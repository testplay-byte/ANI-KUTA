package com.confused.anikuta.core.testapi

import android.app.Activity
import android.view.Window

/**
 * Bridge between the foreground Activity's `Window` and the debug-only test-controller.
 *
 * Used by the screenshot capture path on API 24-29 (D-200): `PixelCopy.request(window, ...)`
 * needs an Activity `Window` reference, which the `AccessibilityService` doesn't have. The
 * foreground Activity registers itself here (via `ActivityLifecycleCallbacks` wired in
 * `:app/src/debug/DebugInit.kt` — debug-only, no-op in release).
 *
 * On API 30+ the test-controller uses `AccessibilityService.takeScreenshot()` directly and
 * doesn't need this registry. On API 24-29 it reads [window] for `PixelCopy`.
 *
 * Thread-safe via `@Volatile`. The `Window` ref is only valid while the Activity is RESUMED;
 * `unbind()` is called on `onActivityPaused`. If the test-controller requests a screenshot
 * while no Activity is resumed, it returns `NO_WINDOW_AVAILABLE`.
 */
object DebugWindowRegistry {

    @Volatile private var windowRef: Window? = null
    @Volatile private var activityRef: Activity? = null

    fun bind(activity: Activity) {
        activityRef = activity
        windowRef = activity.window
    }

    fun unbind(activity: Activity) {
        if (activityRef === activity) {
            activityRef = null
            windowRef = null
        }
    }

    val window: Window? get() = windowRef
    val activity: Activity? get() = activityRef
}
