package com.confused.anikuta.core.designsystem.color

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * D-223: Extracts a dominant accent color from an anime cover image using the
 * AndroidX Palette API.
 *
 * **How it works:**
 * 1. Loads a 100×100 downscaled bitmap via Coil3 (fast, ~10KB allocation).
 * 2. Converts the bitmap to ARGB_8888 config if it's HARDWARE-config (Palette
 *    can't read pixels from GPU-backed bitmaps — D-223fix2).
 * 3. Generates a [Palette] from the bitmap.
 * 4. Picks the best swatch: vibrantSwatch → darkVibrantSwatch → mutedSwatch → dominantSwatch.
 * 5. Normalizes the HSL values: saturation ≥ 0.40, lightness ∈ [0.40, 0.65].
 *    This ensures the color is never too gray, too dark, or too light —
 *    it works in BOTH light and dark mode.
 * 6. Returns the ARGB Int (0xFFRRGGBB, alpha forced to FF).
 *
 * **Performance:**
 * - Extraction from a 100×100 bitmap takes 5-20ms on modern devices.
 * - The result is cached in the DB (content_details.cover_accent_argb) —
 *   extraction only happens once per anime.
 * - Runs on Dispatchers.Default (off the main thread).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:DesignSystem:Color".
 */
class CoverColorExtractor(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {

    companion object {
        private const val TAG = "Anikuta:Core:DesignSystem:Color"
        private const val EXTRACTION_SIZE = 100
        private const val MIN_SATURATION = 0.40f
        private const val MIN_LIGHTNESS = 0.40f
        private const val MAX_LIGHTNESS = 0.65f
    }

    /**
     * Extracts the accent ARGB color from a cover image URL.
     * @return the ARGB Int (0xFFRRGGBB), or null if extraction failed.
     */
    suspend fun extract(coverUrl: String): Int? = withContext(Dispatchers.Default) {
        if (coverUrl.isBlank()) return@withContext null

        runCatching {
            Logger.d(TAG) { "Extracting color from: ${coverUrl.take(60)}..." }
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(EXTRACTION_SIZE, EXTRACTION_SIZE)
                .build()
            val result = imageLoader.execute(request)
            val rawBitmap = result.image?.toBitmap() ?: run {
                Logger.w(TAG) { "Coil returned null image" }
                return@withContext null
            }

            // D-223fix2: Coil3 returns HARDWARE-config bitmaps on API 26+.
            // Palette.from(bitmap).generate() crashes with:
            //   "unable to getPixels(), pixel access is not supported on Config#HARDWARE bitmaps"
            // Fix: copy the bitmap to ARGB_8888 config so Palette can read its pixels.
            // This adds ~1ms for a 100×100 bitmap — negligible.
            val bitmap = ensureArgb8888(rawBitmap)
            // Don't recycle the original bitmap if we copied it — Coil manages it.
            // If we didn't copy (already ARGB_8888), still don't recycle.

            val palette = Palette.from(bitmap).generate()
            val swatch = pickAccentSwatch(palette) ?: run {
                Logger.w(TAG) { "No suitable swatch found" }
                return@withContext null
            }
            val normalizedArgb = normalizeForAccent(swatch.rgb)
            Logger.i(TAG) { "Extracted accent: 0x${"%08X".format(normalizedArgb)}" }
            normalizedArgb
        }.getOrElse { e ->
            Logger.w(TAG) { "Color extraction failed: ${e.message}" }
            null
        }
    }

    /**
     * D-223fix2: Ensures the bitmap is in ARGB_8888 config.
     * Coil3 on API 26+ returns HARDWARE bitmaps which Palette can't read.
     * This copies the bitmap to a new ARGB_8888 bitmap if needed.
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            val newBitmap = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(newBitmap)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            newBitmap
        } else {
            bitmap
        }
    }

    private fun pickAccentSwatch(palette: Palette): Palette.Swatch? {
        return palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
    }

    private fun normalizeForAccent(rgb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hsl[1] = hsl[1].coerceIn(MIN_SATURATION, 1.0f)
        hsl[2] = hsl[2].coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl) or 0xFF000000.toInt()
    }
}
