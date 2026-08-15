package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Visual action preview overlay (D-198 v5.2 → v5.9).
 *
 * Shows a small accent-colored ring + center dot at the action location for ~1 second,
 * then removes it and performs the action.
 *
 * ## D-198 v5.9 — Coordinate System Fix (the "status bar offset" bug)
 *
 * **Symptom:** The actual tap landed correctly (via `dispatchGesture`), but the green
 * preview dot appeared shifted DOWN by the status bar height.
 *
 * **Root cause:** A coordinate-system mismatch between the two consumers of the same
 * `(x, y)` pair sent by the dashboard:
 *
 * | Consumer | Coordinate system | (0,0) origin |
 * |----------|------------------|--------------|
 * | `AccessibilityService.dispatchGesture(x, y)` | **Raw screen coords** | Top-left of the **physical screen** (includes status bar) |
 * | `WindowManager.LayoutParams` w/ `Gravity.TOP \| Gravity.START` (default) | **Content-area coords** | Top-left of the **content frame** (BELOW the status bar) |
 *
 * The screenshot captured by `takeScreenshot()` includes the status bar, so the dashboard
 * computes `phoneY` in raw screen coords. `dispatchGesture` consumes those directly → correct.
 * But the overlay's `LayoutParams.y` was interpreted as an offset from the content frame's
 * top, which sits `statusBarHeight` pixels below the physical screen top → the dot was
 * rendered `statusBarHeight` pixels too low.
 *
 * **Fix:** Add `FLAG_LAYOUT_IN_SCREEN` to the `LayoutParams` flags. Per the Android docs,
 * this flag "places the window within the entire screen, ignoring decorations around the
 * border (such as the status bar)", which makes the `(x, y)` origin the **physical screen's
 * top-left** — exactly matching `dispatchGesture`. Combined with `FLAG_LAYOUT_NO_LIMITS`
 * (already present, allows the dot to render partially off-screen for edge taps), the dot
 * now lands pixel-perfect on the same spot the gesture hits.
 *
 * ## Visual design (v5.9)
 *
 * The marker is now a **ring + center dot** instead of a flat filled circle:
 *  - Outer ring: 28dp diameter, 70%-opacity accent green fill, 3dp white stroke
 *    (the white stroke guarantees visibility on both light and dark app backgrounds)
 *  - Inner center dot: 6dp solid white — marks the EXACT pixel being tapped
 *  - Shown for [previewDurationMs] (default 1000ms), then removed
 *
 * ## Prior fixes (v5.2, retained)
 *  - No animations (AlphaAnimation/ScaleAnimation listeners never fire on
 *    TYPE_ACCESSIBILITY_OVERLAY windows — Handler.postDelayed is used instead).
 *  - `clearAllOverlays()` for toggle-off cleanup.
 *  - All active overlay views tracked in a Set for cleanup.
 */
class ActionPreviewOverlay(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Overlay"
        private const val DEFAULT_PREVIEW_DURATION_MS = 1000L
        private const val RING_SIZE_DP = 28f
        private const val RING_STROKE_DP = 3f
        private const val CENTER_DOT_DP = 6f
        private const val RING_COLOR = 0xB3B1F256.toInt() // 70% opacity accent green
        private const val STROKE_COLOR = 0xFFFFFFFF.toInt() // solid white
        private const val CENTER_COLOR = 0xFFFFFFFF.toInt() // solid white
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    /** All active overlay views — used by clearAllOverlays() to remove everything. */
    private val activeOverlays = CopyOnWriteArraySet<View>()

    var previewDurationMs: Long = DEFAULT_PREVIEW_DURATION_MS

    /**
     * Show a marker at (x, y) for [previewDurationMs], then call [onComplete].
     * The marker is removed + the action is performed after the delay.
     *
     * **(x, y) must be in raw screen coordinates** (same as `dispatchGesture`),
     * i.e. (0,0) = top-left of the physical screen including the status bar.
     * The `FLAG_LAYOUT_IN_SCREEN` flag on the LayoutParams ensures the overlay
     * window is positioned in this same coordinate space.
     */
    fun showTapPreview(x: Float, y: Float, onComplete: () -> Unit) {
        handler.post {
            try {
                val view = createMarkerView()
                val ringSize = (RING_SIZE_DP * density).toInt()
                val params = createOverlayParams(x.toInt(), y.toInt(), ringSize)

                windowManager.addView(view, params)
                activeOverlays.add(view)

                // Simple delayed removal — NO animations (they don't fire on TYPE_ACCESSIBILITY_OVERLAY).
                handler.postDelayed({
                    removeOverlay(view)
                    onComplete()
                }, previewDurationMs)
            } catch (e: Exception) {
                // If the overlay fails, just proceed with the action immediately.
                Log.w(TAG, "showTapPreview failed at ($x,$y): ${e::class.java.simpleName}: ${e.message}")
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

    /**
     * Creates the marker view: a ring (green fill + white stroke) with a white center dot.
     *
     * Uses a custom [View] with [onDraw] rather than a [GradientDrawable] so we can render
     * both the ring and the center dot in a single view (a GradientDrawable can't easily do
     * a fill + stroke + inner shape).
     */
    private fun createMarkerView(): View {
        return MarkerView(service, density)
    }

    /**
     * Builds the WindowManager params for the overlay.
     *
     * **Flags (D-198 v5.9):**
     *  - `TYPE_ACCESSIBILITY_OVERLAY` — the overlay type (drawn above everything, no permission prompt).
     *  - `FLAG_NOT_FOCUSABLE` — doesn't steal focus from the app.
     *  - `FLAG_NOT_TOUCHABLE` — touch events pass THROUGH the dot to the app beneath.
     *  - `FLAG_LAYOUT_NO_LIMITS` — allows the dot to render partially off-screen (edge taps).
     *  - `FLAG_LAYOUT_IN_SCREEN` — **(v5.9 fix)** positions the window in raw screen
     *    coordinates (origin = physical screen top-left, INCLUDING status bar), matching
     *    `dispatchGesture`'s coordinate system. Without this, the origin is the content
     *    frame (below the status bar), causing a vertical offset equal to the status bar height.
     *
     * **Gravity:** `TOP | START` — the (x, y) is a top-left offset.
     * With `FLAG_LAYOUT_IN_SCREEN`, this offset is from the physical screen's top-left.
     */
    private fun createOverlayParams(x: Int, y: Int, size: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN  // v5.9: raw screen coords
            gravity = Gravity.TOP or Gravity.START
            width = size
            height = size
            // Center the marker on (x, y): shift by -size/2 so the dot's center = (x, y).
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

    /**
     * Custom View that draws the ring + center dot marker.
     *
     * Drawing is done in [onDraw] with anti-aliased [Paint]s for crisp circles at any DPI.
     * The view's [LayoutParams] size is the ring's outer diameter; the center dot is drawn
     * relative to the view's center, so the marker always stays centered on the tap point.
     */
    private class MarkerView(
        context: android.content.Context,
        private val density: Float,
    ) : View(context) {

        private val ringFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RING_COLOR
            style = Paint.Style.FILL
        }

        private val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_DP * density
        }

        private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CENTER_COLOR
            style = Paint.Style.FILL
        }

        private val centerDotRadiusPx = (CENTER_DOT_DP * density) / 2f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val ringRadius = (minOf(width, height) / 2f) - (ringStrokePaint.strokeWidth / 2f)

            // Outer ring: green translucent fill + white stroke border.
            canvas.drawCircle(cx, cy, ringRadius, ringFillPaint)
            canvas.drawCircle(cx, cy, ringRadius, ringStrokePaint)

            // Inner center dot: solid white — marks the EXACT tap pixel.
            canvas.drawCircle(cx, cy, centerDotRadiusPx, centerDotPaint)
        }
    }
}
