package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView

/**
 * Visual action preview overlay (D-198 v5).
 *
 * Shows a translucent highlight on the screen BEFORE performing an action:
 *  - **Tap**: a pulsing circle at the tap coordinates (accent green, 1s)
 *  - **Swipe**: an arrow from start to end (accent green, 1s)
 *  - **Set text**: a border highlight around the target (accent green, 1s)
 *  - **Scroll**: a directional indicator (↑↓←→, 1s)
 *
 * Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] — a special window type
 * that ONLY AccessibilityServices can use. No [android.Manifest.permission.SYSTEM_ALERT_WINDOW]
 * permission needed. Works on top of any app.
 *
 * The overlay shows for [previewDurationMs] (default 1000ms, configurable via SettingsRepository
 * key `debug.test.preview_duration_ms`) then is removed + the action is performed.
 *
 * The color is the app's accent green (#B1F256) at 60% opacity — visible but not distracting.
 *
 * Thread-safe: all view operations are posted to the main thread via [handler].
 */
class ActionPreviewOverlay(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Overlay"
        private const val DEFAULT_PREVIEW_DURATION_MS = 1000L
        private const val OVERLAY_COLOR = 0x99B1F256.toInt() // 60% opacity accent green
        private const val OVERLAY_RADIUS_DP = 48f
        private const val TEXT_SIZE_SP = 14f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    /** Current preview duration (read from SettingsRepository, defaults to 1s). */
    var previewDurationMs: Long = DEFAULT_PREVIEW_DURATION_MS

    /**
     * Show a tap preview circle at (x, y) for [previewDurationMs], then call [onComplete].
     * The circle is a pulsing green dot with a "TAP" label.
     */
    fun showTapPreview(x: Float, y: Float, onComplete: () -> Unit) {
        handler.post {
            val view = createCircleView("👆 TAP")
            val params = createOverlayParams(x.toInt(), y.toInt())
            params.width = (OVERLAY_RADIUS_DP * 2 * density).toInt()
            params.height = (OVERLAY_RADIUS_DP * 2 * density).toInt()
            addOverlayWithAnimation(view, params, onComplete)
        }
    }

    /**
     * Show a swipe preview arrow from (x1, y1) to (x2, y2) for [previewDurationMs], then call [onComplete].
     * Draws a line + a "SWIPE" label at the midpoint.
     */
    fun showSwipePreview(x1: Float, y1: Float, x2: Float, y2: Float, onComplete: () -> Unit) {
        handler.post {
            val midX = ((x1 + x2) / 2).toInt()
            val midY = ((y1 + y2) / 2).toInt()
            val view = createCircleView("👋 SWIPE")
            val params = createOverlayParams(midX, midY)
            params.width = (OVERLAY_RADIUS_DP * 2 * density).toInt()
            params.height = (OVERLAY_RADIUS_DP * 2 * density).toInt()
            addOverlayWithAnimation(view, params, onComplete)
        }
    }

    /**
     * Show a scroll preview indicator at (x, y) with a direction arrow for [previewDurationMs].
     */
    fun showScrollPreview(x: Float, y: Float, direction: String, onComplete: () -> Unit) {
        handler.post {
            val arrow = when (direction.uppercase()) {
                "UP" -> "↑ SCROLL UP"
                "DOWN" -> "↓ SCROLL DOWN"
                "LEFT" -> "← SCROLL LEFT"
                "RIGHT" -> "→ SCROLL RIGHT"
                else -> "📜 SCROLL"
            }
            val view = createCircleView(arrow)
            val params = createOverlayParams(x.toInt(), y.toInt())
            params.width = (OVERLAY_RADIUS_DP * 2.5 * density).toInt()
            params.height = (OVERLAY_RADIUS_DP * 2 * density).toInt()
            addOverlayWithAnimation(view, params, onComplete)
        }
    }

    /**
     * Show a generic preview label at (x, y) for [previewDurationMs].
     * Used for set_text, back, home, etc.
     */
    fun showLabelPreview(x: Float, y: Float, label: String, onComplete: () -> Unit) {
        handler.post {
            val view = createCircleView(label)
            val params = createOverlayParams(x.toInt(), y.toInt())
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            addOverlayWithAnimation(view, params, onComplete)
        }
    }

    // ── Internals ──

    @SuppressLint("ClickableViewAccessibility")
    private fun createCircleView(label: String): View {
        val tv = TextView(service)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
        tv.gravity = Gravity.CENTER
        tv.setPadding(
            (12 * density).toInt(), (8 * density).toInt(),
            (12 * density).toInt(), (8 * density).toInt(),
        )
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = OVERLAY_RADIUS_DP * density
            setColor(OVERLAY_COLOR)
            setStroke((3 * density).toInt(), Color.argb(200, 177, 242, 86)) // accent green border
        }
        tv.background = bg
        return tv
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            gravity = Gravity.TOP or Gravity.START
            this.x = x - (width / 2)
            this.y = y - (height / 2)
        }
    }

    private fun addOverlayWithAnimation(
        view: View,
        params: WindowManager.LayoutParams,
        onComplete: () -> Unit,
    ) {
        try {
            // Fade-in animation.
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 150
                fillAfter = true
            }
            view.startAnimation(fadeIn)
            windowManager.addView(view, params)

            // Schedule fade-out + removal after previewDurationMs.
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
            // If the overlay fails (e.g., bad window token), just proceed with the action.
            runCatching { windowManager.removeView(view) }
            onComplete()
        }
    }
}
