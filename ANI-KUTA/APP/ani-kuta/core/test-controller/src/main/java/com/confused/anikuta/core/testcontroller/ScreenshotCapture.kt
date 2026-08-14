package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.PixelCopy
import android.view.Window
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.DebugWindowRegistry
import com.confused.anikuta.core.testapi.TestControllerConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.coroutines.resume

/**
 * Captures screenshots across API 24-36 (D-200).
 *
 *  - API 30+: [AccessibilityService.takeScreenshot] (2-arg form — deprecated in API 33 but
 *    functional through 36; captures the full display frame buffer including MPV SurfaceViews).
 *  - API 24-29: [PixelCopy.request] on the foreground Activity's [Window] (registered in
 *    [DebugWindowRegistry] by `:app/src/debug` via ActivityLifecycleCallbacks). Also captures
 *    SurfaceView content.
 *
 * Both paths produce a software ARGB_8888 bitmap (the API 30+ hardware bitmap is copied to
 * ARGB_8888 before the hardware buffer is closed). Output: downscale to
 * [TestControllerConstants.SCREENSHOT_MAX_DIMENSION] + JPEG q[SCREENSHOT_JPEG_QUALITY].
 *
 * Returns null on failure (no window, takeScreenshot rejected, PixelCopy error, or 10s timeout)
 * — the executor reports "NO_WINDOW" or "SCREENSHOT_FAILED" to the agent.
 *
 * The 10s [withTimeoutOrNull] guard prevents a hung callback (takeScreenshot returns true but
 * the Consumer never fires — e.g., display off, service disconnected) from hanging the
 * command-execution coroutine forever.
 */
class ScreenshotCapture(
    private val service: AccessibilityService,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Screenshot"
        private const val CAPTURE_TIMEOUT_MS = 10_000L
    }

    /** Capture + downscale + JPEG-compress. Returns JPEG bytes, or null on failure/timeout. */
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

    /** Capture a software ARGB_8888 bitmap. Caller recycles. */
    private suspend fun captureRaw(): Bitmap? = when {
        Build.VERSION.SDK_INT >= 30 -> captureViaTakeScreenshot()
        else -> {
            val window = DebugWindowRegistry.window
                ?: run {
                    Logger.w(TAG) { "no window (API ${Build.VERSION.SDK_INT}) — DebugWindowRegistry unbound" }
                    null
                }
            window?.let { captureViaPixelCopy(it) }
        }
    }

    /**
     * API 30+: AccessibilityService.takeScreenshot(Executor, Consumer<ScreenshotResult>).
     *
     * Returns `boolean` (true if the request was accepted). If it returns false, the callback
     * won't fire — we resume null immediately. If it returns true but the callback never fires
     * (display off, service disconnected), the [withTimeoutOrNull] in [capture] handles the hang.
     *
     * The Consumer is `java.util.function.Consumer` (per AOSP source). We pass it explicitly
     * (not via trailing lambda) to avoid any SAM-ambiguity with `android.util.Consumer` which
     * is also on the classpath.
     */
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private suspend fun captureViaTakeScreenshot(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val executor = Executor { r -> r.run() }
            val consumer = Consumer<AccessibilityService.ScreenshotResult> { result ->
                var softwareBmp: Bitmap? = null
                try {
                    val hardwareBmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    if (hardwareBmp != null) {
                        // Copy to software (ARGB_8888) so we can safely recycle + compress.
                        softwareBmp = hardwareBmp.copy(Bitmap.Config.ARGB_8888, false)
                        runCatching { hardwareBmp.recycle() }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "takeScreenshot wrap/copy failed: ${e::class.java.simpleName}: ${e.message}" }
                } finally {
                    runCatching { result.hardwareBuffer.close() }
                }
                if (cont.isActive) cont.resume(softwareBmp)
            }
            val accepted = service.takeScreenshot(executor, consumer)
            if (!accepted && cont.isActive) cont.resume(null)
        }

    private suspend fun captureViaPixelCopy(window: Window): Bitmap? {
        val w = window.decorView.width.coerceAtLeast(1)
        val h = window.decorView.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(window, bmp, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        cont.resume(bmp)
                    } else {
                        Logger.w(TAG) { "PixelCopy failed: $result" }
                        runCatching { bmp.recycle() }
                        if (cont.isActive) cont.resume(null)
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            } catch (e: Exception) {
                Logger.w(TAG) { "PixelCopy threw: ${e::class.java.simpleName}: ${e.message}" }
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
