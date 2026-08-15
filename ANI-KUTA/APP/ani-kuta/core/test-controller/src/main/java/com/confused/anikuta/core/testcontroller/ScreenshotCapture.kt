package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.TestControllerConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Captures screenshots via [AccessibilityService.takeScreenshot] (D-200 v2, D-198 v5.6).
 *
 * v5.6: switched from PixelCopy(window) to AccessibilityService.takeScreenshot().
 * This captures the FULL display including status bar, navigation bar, and system UI —
 * the screenshot's pixel space matches AccessibilityNodeInfo.getBoundsInScreen exactly,
 * so the dashboard's overlay drawing + click coordinates are correctly aligned without
 * any offset or scaling error.
 *
 * API 30+ only (takeScreenshot was added in API 30/R). The 3-arg signature
 * `takeScreenshot(int displayId, Executor, TakeScreenshotCallback)` compiles cleanly
 * on SDK 36 (the previous 2-arg form was REMOVED in SDK 36).
 *
 * The callback is a dedicated TakeScreenshotCallback interface (NOT Consumer).
 * Uses Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) + MUST close the
 * hardware buffer to avoid GPU memory leaks.
 *
 * Output: downscale to [TestControllerConstants.SCREENSHOT_MAX_DIMENSION] + JPEG q[SCREENSHOT_JPEG_QUALITY].
 */
class ScreenshotCapture(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Screenshot"
        private const val CAPTURE_TIMEOUT_MS = 10_000L
    }

    suspend fun capture(): ByteArray? {
        val raw = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captureRaw() } ?: return null
        return try {
            downscaleAndCompress(raw)
        } catch (e: Exception) {
            Logger.w(TAG) { "compress failed: ${e::class.java.simpleName}: ${e.message}" }
            runCatching { raw.recycle() }
            null
        }
    }

    private suspend fun captureRaw(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Logger.w(TAG) { "takeScreenshot requires API 30+; got ${Build.VERSION.SDK_INT}" }
            return null
        }
        return captureViaAccessibility()
    }

    private suspend fun captureViaAccessibility(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val executor = Executor { cmd -> cmd.run() }
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = result.hardwareBuffer
                        val colorSpace = result.colorSpace
                        val bmp = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()
                        if (cont.isActive) cont.resume(bmp)
                    } catch (e: Exception) {
                        Logger.w(TAG) { "wrap hardware buffer failed: ${e::class.java.simpleName}: ${e.message}" }
                        if (cont.isActive) cont.resume(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Logger.w(TAG) { "takeScreenshot failed: errorCode=$errorCode" }
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                service.takeScreenshot(Display.INVALID_DISPLAY, executor, callback)
            } catch (e: Exception) {
                Logger.w(TAG) { "takeScreenshot threw: ${e::class.java.simpleName}: ${e.message}" }
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun downscaleAndCompress(src: Bitmap): ByteArray {
        val maxDim = TestControllerConstants.SCREENSHOT_MAX_DIMENSION
        val origW = src.width
        val origH = src.height
        val scale = maxDim.toFloat() / maxOf(origW, origH)
        val scaled = if (scale < 1f) {
            val nw = (origW * scale).toInt().coerceAtLeast(1)
            val nh = (origH * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, nw, nh, true).also {
                if (it !== src) runCatching { src.recycle() }
            }
        } else src
        return try {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, TestControllerConstants.SCREENSHOT_JPEG_QUALITY, baos)
            baos.toByteArray()
        } finally {
            runCatching { scaled.recycle() }
        }
    }
}
