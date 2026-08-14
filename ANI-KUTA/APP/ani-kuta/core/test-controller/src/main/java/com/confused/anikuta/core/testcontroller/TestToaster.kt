package com.confused.anikuta.core.testcontroller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Toast helper for the test-controller (D-198 v2).
 *
 * Shows short toasts from the AccessibilityService (which has no UI thread by default —
 * toasts must be posted to the main looper). Throttled so the same message doesn't spam
 * if the broker keeps failing + retrying.
 *
 * Thread-safe. The [init] call is idempotent — safe to call from onServiceConnected
 * (the applicationContext is stable across the service lifecycle).
 */
internal object TestToaster {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null
    @Volatile private var lastToastMessage: String? = null
    @Volatile private var lastToastTime: Long = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Show a short toast. Throttled: the same message shows at most once per [throttleMs]
     * (default 3s). Different messages always show immediately.
     */
    fun show(message: String, throttleMs: Long = 3000L) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        if (message == lastToastMessage && now - lastToastTime < throttleMs) return
        lastToastTime = now
        lastToastMessage = message
        handler.post {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}
