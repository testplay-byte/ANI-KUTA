package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Visual action preview overlay (D-198 v5.2).
 *
 * Shows a small accent-colored circle at the action location for 1 second, then
 * removes it and performs the action.
 *
 * D-198 v5.2 fixes:
 *  - Removed ALL animations (AlphaAnimation, ScaleAnimation) — the animation listeners
 *    never fire on TYPE_ACCESSIBILITY_OVERLAY windows, causing dots to stay forever
 *    and actions to never execute.
 *  - Now uses a simple Handler.postDelayed to remove the view + call onComplete.
 *  - Added clearAllOverlays() for toggle-off cleanup.
 *  - Tracks all active overlay views in a Set for cleanup.
 *
 * The circle is:
 *  - Small (24dp diameter)
 *  - No text, no border
 *  - Accent green (#B1F256) at 70% opacity
 *  - Shown for 1 second, then removed
 */
class ActionPreviewOverlay(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Overlay"
        private const val DEFAULT_PREVIEW_DURATION_MS = 1000L
        private const val CIRCLE_SIZE_DP = 24f
        private const val CIRCLE_COLOR = 0xB3B1F256.toInt() // 70% opacity accent green
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    /** All active overlay views — used by clearAllOverlays() to remove everything. */
    private val activeOverlays = CopyOnWriteArraySet<View>()

    var previewDurationMs: Long = DEFAULT_PREVIEW_DURATION_MS

    /**
     * Show a small accent-colored circle at (x, y) for [previewDurationMs], then call [onComplete].
     * The circle is removed + the action is performed after the delay.
     */
    fun showTapPreview(x: Float, y: Float, onComplete: () -> Unit) {
        handler.post {
            try {
                val view = createCircleView()
                val size = (CIRCLE_SIZE_DP * density).toInt()
                val params = createOverlayParams(x.toInt(), y.toInt(), size)

                windowManager.addView(view, params)
                activeOverlays.add(view)

                // Simple delayed removal — NO animations (they don't fire on TYPE_ACCESSIBILITY_OVERLAY).
                handler.postDelayed({
                    removeOverlay(view)
                    onComplete()
                }, previewDurationMs)
            } catch (e: Exception) {
                // If the overlay fails, just proceed with the action immediately.
                onComplete()
            }
        }
    }

    fun showSwipePreview(x1: Float, y1: Float, x2: Float, y2: Float, onComplete: () -> Unit) {
        val midX = ((x1 + x2) / 2)
        val midY = ((y1 + y2) / 2)
        showTapPreview(midX, midY, onComplete)
    }

    fun showScrollPreview(x: Float, y: Float, direction: String, onComplete: () -> Unit) {
        showTapPreview(x, y, onComplete)
    }

    fun showLabelPreview(x: Float, y: Float, label: String, onComplete: () -> Unit) {
        showTapPreview(x, y, onComplete)
    }

    /**
     * Remove ALL active overlay views from the screen.
     * Called when the test controller is toggled OFF — ensures no dots remain.
     */
    fun clearAllOverlays() {
        handler.post {
            for (view in activeOverlays) {
                removeOverlay(view)
            }
            activeOverlays.clear()
        }
    }

    // ── Internals ──

    private fun createCircleView(): View {
        val view = View(service)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CIRCLE_COLOR)
        }
        view.background = bg
        return view
    }

    private fun createOverlayParams(x: Int, y: Int, size: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            gravity = Gravity.TOP or Gravity.START
            width = size
            height = size
            this.x = x - (size / 2)
            this.y = y - (size / 2)
        }
    }

    private fun removeOverlay(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // View might already be removed — ignore.
        }
        activeOverlays.remove(view)
    }
}
