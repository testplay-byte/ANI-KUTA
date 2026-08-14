package com.confused.anikuta.core.testcontroller

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.Window
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.DebugWindowRegistry
import com.confused.anikuta.core.testapi.TestControllerConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Captures screenshots via [PixelCopy.request] on the foreground Activity's [Window] (D-200).
 *
 * Originally planned to use [AccessibilityService.takeScreenshot] on API 30+ + [PixelCopy] on
 * API 24-29. But `takeScreenshot`'s signature changed across SDK versions (2-arg deprecated form
 * removed in SDK 36; replaced by a 3-arg form whose first param type varies), making it fragile
 * to compile against SDK 36 while supporting API 30-32 at runtime.
 *
 * [PixelCopy.request] works on ALL API levels (24+), captures SurfaceView content (including the
 * MPV player surface — verified per R-A research), and uses the [DebugWindowRegistry] (bound by
 * ActivityLifecycleCallbacks in `:app/src/debug/DebugInit.kt`). Simpler + more robust than the
 * multi-API `takeScreenshot` path.
 *
 * Limitation: [PixelCopy] captures only our Activity's window (not the full screen including
 * system bars). For testing purposes, the app's window IS the target — system bars are irrelevant.
 * If the Activity is PAUSED (not foregrounded), [DebugWindowRegistry.window] is null → returns
 * null → executor reports "NO_WINDOW".
 *
 * Output: downscale to [TestControllerConstants.SCREENSHOT_MAX_DIMENSION] + JPEG q[SCREENSHOT_JPEG_QUALITY].
 * The 10s [withTimeoutOrNull] guard prevents a hung PixelCopy callback from hanging the executor.
 */
class ScreenshotCapture(
    @Suppress("unused") private val service: android.accessibilityservice.AccessibilityService,
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

    private suspend fun captureRaw(): Bitmap? {
        val window = DebugWindowRegistry.window
            ?: run {
                Logger.w(TAG) { "no foreground window — DebugWindowRegistry unbound (app not resumed?)" }
                return null
            }
        return captureViaPixelCopy(window)
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
