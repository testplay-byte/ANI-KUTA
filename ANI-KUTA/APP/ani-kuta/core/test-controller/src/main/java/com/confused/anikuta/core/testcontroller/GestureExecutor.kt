package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.confused.anikuta.core.testapi.ScrollDirection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Executes UI gestures + actions on the accessibility tree (D-199).
 *
 * Two addressing paths:
 *  - By [nodeId]: looks up the node via [AccessibilityTreeSerializer.lookup] + calls
 *    `performAction(ACTION_CLICK / ACTION_LONG_CLICK / ACTION_SET_TEXT / ACTION_SCROLL_*)`.
 *    Semantic — triggers the app's registered action. Faster + more reliable than gestures.
 *  - By {x, y}: builds a [GestureDescription] + calls [AccessibilityService.dispatchGesture].
 *    Physical touch event. Universal fallback when nodeId is stale.
 *
 * Global actions (back/home/recents/notifications) via [AccessibilityService.performGlobalAction].
 *
 * All gesture dispatch is async (callback-based); wrapped in [suspendCancellableCoroutine]
 * so the executor can `await` completion + report success/failure. The callback fires on the
 * main looper (we pass `Handler(Looper.getMainLooper())`).
 *
 * Main-thread-affine: the executor is called from [TestControllerExecutor] which dispatches
 * to `Dispatchers.Main`. dispatchGesture + performAction + performGlobalAction are all
 * main-thread-safe (Android docs).
 */
class GestureExecutor(
    private val service: AccessibilityService,
    private val serializer: AccessibilityTreeSerializer,
) {

    /** Tap by nodeId (semantic ACTION_CLICK). Returns false if the node is stale. */
    fun tapNode(nodeId: Int): Boolean {
        val node = serializer.lookup(nodeId) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Tap at screen coordinates (physical gesture). */
    suspend fun tapCoords(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        return dispatchSinglePointGesture(x, y, durationMs)
    }

    /** Long-click by nodeId (semantic ACTION_LONG_CLICK). */
    fun longClickNode(nodeId: Int): Boolean {
        val node = serializer.lookup(nodeId) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /** Long-click at coordinates (physical gesture — hold a single point). */
    suspend fun longClickCoords(x: Float, y: Float, durationMs: Long = 800L): Boolean {
        return dispatchSinglePointGesture(x, y, durationMs)
    }

    /** Swipe from (x1,y1) to (x2,y2). Used for swipes + scrolls. */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    /**
     * Scroll at (x, y) (or screen center if null) by [amount] swipes in [direction].
     * Each swipe moves ~SCROLL_STEP_PX pixels. Uses physical gestures (works on any
     * scrollable area, not just registered scrollable nodes).
     */
    suspend fun scroll(x: Float?, y: Float?, direction: ScrollDirection, amount: Int): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val cx = x ?: displayMetrics.widthPixels / 2f
        val cy = y ?: displayMetrics.heightPixels / 2f
        val step = (displayMetrics.heightPixels * 0.35f).coerceAtLeast(300f) // ~35% screen height per swipe
        var lastOk = true
        repeat(amount.coerceAtLeast(1)) {
            val (sx, sy, ex, ey) = when (direction) {
                ScrollDirection.UP -> floatArrayOf(cx, cy + step / 2, cx, cy - step / 2)
                ScrollDirection.DOWN -> floatArrayOf(cx, cy - step / 2, cx, cy + step / 2)
                ScrollDirection.LEFT -> floatArrayOf(cx + step, cy, cx - step, cy)
                ScrollDirection.RIGHT -> floatArrayOf(cx - step, cy, cx + step, cy)
            }
            // Slightly shorten the swipe to avoid triggering edge gestures (back-nav, etc.).
            lastOk = swipe(sx, sy, ex, ey, durationMs = 350L)
            if (!lastOk) return@repeat
        }
        return lastOk
    }

    /** Set text on an editable node. Returns false if the node is stale. */
    fun setText(nodeId: Int, text: String): Boolean {
        val node = serializer.lookup(nodeId) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ── Global actions ──
    fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    fun home(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    fun recents(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    // ── Internals ──

    private suspend fun dispatchSinglePointGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?, id: Int) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(g: GestureDescription?, id: Int) { if (cont.isActive) cont.resume(false) }
            }
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val dispatched = service.dispatchGesture(gesture, callback, handler)
            if (!dispatched && cont.isActive) {
                cont.resume(false) // dispatchGesture returned false (e.g. canPerformGestures not set).
            }
        }
}
