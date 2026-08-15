package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.PixelCopy
import android.view.Window
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.DebugWindowRegistry
import com.confused.anikuta.core.testapi.TestControllerConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Captures screenshots (D-200 v2, D-198 v5.6.2).
 *
 * Strategy:
 * 1. Try AccessibilityService.takeScreenshot() (API 30+) — captures full screen including status bar.
 *    Uses Display.DEFAULT_DISPLAY (0) — Display.INVALID_DISPLAY (-1) causes errorCode=4 on some ROMs.
 * 2. If takeScreenshot fails (any error code), fall back to PixelCopy(window).
 * 3. If PixelCopy also fails, return null → executor reports the error.
 *
 * The fallback chain ensures screenshots work on ALL devices, even OEM ROMs that don't
 * properly implement takeScreenshot (e.g., OnePlus OxygenOS returns errorCode=4 with
 * INVALID_DISPLAY, but works with DEFAULT_DISPLAY or falls back to PixelCopy).
 *
 * Output: downscale to [TestControllerConstants.SCREENSHOT_MAX_DIMENSION] + JPEG q[SCREENSHOT_JPEG_QUALITY].
 */
class ScreenshotCapture(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Screenshot"
        private const val CAPTURE_TIMEOUT_MS = 10_000L

        // Error code names for logging (from AccessibilityService source)
        private fun errorCodeName(code: Int): String = when (code) {
            1 -> "INVALID_DISPLAY_ID"
            2 -> "INVALID_TARGET"
            3 -> "INSUFFICIENT_RESOURCES"
            4 -> "UNSUPPORTED"
            else -> "UNKNOWN($code)"
        }
    }

    /** The last error message — read by the executor for detailed error reporting. */
    @Volatile
    var lastError: String? = null
        private set

    suspend fun capture(): ByteArray? {
        val raw = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captureRaw() } ?: run {
            lastError = "capture timed out after ${CAPTURE_TIMEOUT_MS}ms"
            return null
        }
        return try {
            downscaleAndCompress(raw)
        } catch (e: Exception) {
            lastError = "compress failed: ${e::class.java.simpleName}: ${e.message}"
            Logger.w(TAG) { lastError!! }
            runCatching { raw.recycle() }
            null
        }
    }

    private suspend fun captureRaw(): Bitmap? {
        lastError = null

        // Strategy 1: try takeScreenshot with DEFAULT_DISPLAY (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val result = captureViaAccessibility(Display.DEFAULT_DISPLAY)
            if (result != null) return result
            Logger.w(TAG) { "takeScreenshot(DEFAULT_DISPLAY) failed: $lastError — trying PixelCopy fallback" }

            // Strategy 2: if DEFAULT_DISPLAY failed, try INVALID_DISPLAY (some ROMs prefer -1)
            val result2 = captureViaAccessibility(Display.INVALID_DISPLAY)
            if (result2 != null) return result2
            Logger.w(TAG) { "takeScreenshot(INVALID_DISPLAY) also failed: $lastError — falling back to PixelCopy" }
        }

        // Strategy 3: fall back to PixelCopy(window) — always works, but no status bar
        return captureViaPixelCopy()
    }

    private suspend fun captureViaAccessibility(displayId: Int): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val executor = Executor { cmd -> cmd.run() }
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = result.hardwareBuffer
                        val colorSpace = result.colorSpace
                        val bmp = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()
                        Logger.d(TAG) { "takeScreenshot success: ${bmp?.width}x${bmp?.height} (displayId=$displayId)" }
                        if (cont.isActive) cont.resume(bmp)
                    } catch (e: Exception) {
                        lastError = "wrap hardware buffer failed: ${e::class.java.simpleName}: ${e.message}"
                        Logger.w(TAG) { lastError!! }
                        if (cont.isActive) cont.resume(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    lastError = "takeScreenshot(${if(displayId==Display.DEFAULT_DISPLAY)"DEFAULT" else "INVALID"}_DISPLAY) failed: ${errorCodeName(errorCode)} (code=$errorCode)"
                    Logger.w(TAG) { lastError!! }
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                service.takeScreenshot(displayId, executor, callback)
            } catch (e: Exception) {
                lastError = "takeScreenshot threw: ${e::class.java.simpleName}: ${e.message}"
                Logger.w(TAG) { lastError!! }
                if (cont.isActive) cont.resume(null)
            }
        }

    private suspend fun captureViaPixelCopy(): Bitmap? {
        val window: Window = DebugWindowRegistry.window
            ?: run {
                lastError = "PixelCopy fallback: no foreground window (DebugWindowRegistry unbound)"
                Logger.w(TAG) { lastError!! }
                return null
            }
        val w = window.decorView.width.coerceAtLeast(1)
        val h = window.decorView.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(window, bmp, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        Logger.d(TAG) { "PixelCopy success: ${bmp.width}x${bmp.height} (fallback)" }
                        if (cont.isActive) cont.resume(bmp)
                    } else {
                        lastError = "PixelCopy failed: result=$result"
                        Logger.w(TAG) { lastError!! }
                        runCatching { bmp.recycle() }
                        if (cont.isActive) cont.resume(null)
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            } catch (e: Exception) {
                lastError = "PixelCopy threw: ${e::class.java.simpleName}: ${e.message}"
                Logger.w(TAG) { lastError!! }
                runCatching { bmp.recycle() }
                if (cont.isActive) cont.resume(null)
            }
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
