package com.confused.anikuta.core.designsystem.color

import android.content.Context
import android.graphics.Bitmap
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
 * 1. Loads a 100×100 downscaled bitmap via Coil3 with ARGB_8888 config
 *    (avoids HARDWARE bitmaps that Palette can't read — D-223fix3).
 * 2. Generates a [Palette] from the bitmap.
 * 3. Picks the best swatch: vibrantSwatch → darkVibrantSwatch → mutedSwatch → dominantSwatch.
 * 4. Normalizes the HSL values: saturation ≥ 0.40, lightness ∈ [0.40, 0.65].
 *    This ensures the color is never too gray, too dark, or too light —
 *    it works in BOTH light and dark mode.
 * 5. Returns the ARGB Int (0xFFRRGGBB, alpha forced to FF).
 *
 * **D-223fix3:** Previous approaches tried to convert a HARDWARE bitmap to
 * ARGB_8888 after loading (via Canvas.drawBitmap or Bitmap.copy). Both failed:
 * - D-223fix2 (Canvas.copy): "Software rendering doesn't support hardware bitmaps"
 * - The root cause: Canvas can't draw hardware bitmaps in software rendering mode.
 *
 * The proper fix: tell Coil3 to decode the image as ARGB_8888 from the start
 * via `.bitmapConfig(Bitmap.Config.ARGB_8888)` on the ImageRequest. This avoids
 * HARDWARE config entirely — the bitmap is CPU-readable from the moment it's decoded.
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

            // D-223fix3: Request ARGB_8888 config directly from Coil3.
            // This prevents Coil from returning a HARDWARE-config bitmap
            // (which Palette can't read — getPixels() fails on HARDWARE bitmaps).
            // Previous fixes (Canvas copy, Bitmap.copy) also failed because
            // you can't draw/copy a HARDWARE bitmap in software rendering mode.
            // The fix: decode as ARGB_8888 from the start via bitmapConfig().
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(EXTRACTION_SIZE, EXTRACTION_SIZE)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build()
            val result = imageLoader.execute(request)
            val bitmap = result.image?.toBitmap() ?: run {
                Logger.w(TAG) { "Coil returned null image" }
                return@withContext null
            }

            // Safety check: if despite the config request Coil returned a
            // HARDWARE bitmap (shouldn't happen, but defensive), copy it via
            // Bitmap.copy() which works by allocating a new pixel buffer.
            val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                Logger.w(TAG) { "Got HARDWARE bitmap despite ARGB_8888 request — copying" }
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: run {
                        Logger.w(TAG) { "Bitmap.copy returned null — can't extract" }
                        return@withContext null
                    }
            } else {
                bitmap
            }

            val palette = Palette.from(safeBitmap).generate()
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
