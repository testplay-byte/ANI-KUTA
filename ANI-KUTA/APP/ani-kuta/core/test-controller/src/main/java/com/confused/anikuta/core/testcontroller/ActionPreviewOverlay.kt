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
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation

/**
 * Visual action preview overlay (D-198 v5.1).
 *
 * Shows a small accent-colored circle at the action location for 1 second before
 * performing the action. The circle is:
 *  - Small (24dp diameter)
 *  - No text inside
 *  - No border
 *  - Accent green (#B1F256) at 70% opacity
 *  - Pulsing scale animation (grows slightly then shrinks)
 *
 * Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] — no extra permissions.
 *
 * The 1-second delay is intentional — it shows the user what's about to happen
 * before the action is performed.
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

    var previewDurationMs: Long = DEFAULT_PREVIEW_DURATION_MS

    /**
     * Show a small accent-colored circle at (x, y) for [previewDurationMs], then call [onComplete].
     */
    fun showTapPreview(x: Float, y: Float, onComplete: () -> Unit) {
        handler.post {
            val view = createCircleView()
            val size = (CIRCLE_SIZE_DP * density).toInt()
            val params = createOverlayParams(x.toInt(), y.toInt(), size)
            addOverlayWithAnimation(view, params, onComplete)
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

    private fun addOverlayWithAnimation(
        view: View,
        params: WindowManager.LayoutParams,
        onComplete: () -> Unit,
    ) {
        try {
            // Scale animation: start small, grow to 1.2x, shrink back.
            val scaleAnim = ScaleAnimation(
                0.5f, 1.2f, 0.5f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
            ).apply {
                duration = 300
                fillAfter = true
            }
            // Fade in.
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 150
                fillAfter = true
            }
            view.startAnimation(fadeIn)
            windowManager.addView(view, params)

            // After the preview duration, fade out + remove + perform action.
            handler.postDelayed({
                val fadeOut = AlphaAnimation(1f, 0f).apply {
                    duration = 200
                    fillAfter = true
                    setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(a: Animation?) {}
                        override fun onAnimationRepeat(a: Animation?) {}
                        override fun onAnimationEnd(a: Animation?) {
                            runCatching { windowManager.removeView(view) }
                            onComplete()
                        }
                    })
                }
                view.startAnimation(fadeOut)
            }, previewDurationMs)
        } catch (e: Exception) {
            runCatching { windowManager.removeView(view) }
            onComplete()
        }
    }
}
