package com.confused.anikuta.core.designsystem.color

import android.content.Context
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
 * 2. Generates a [Palette] from the bitmap.
 * 3. Picks the best swatch: vibrantSwatch → darkVibrantSwatch → mutedSwatch → dominantSwatch.
 * 4. Normalizes the HSL values: saturation ≥ 0.40, lightness ∈ [0.40, 0.65].
 *    This ensures the color is never too gray, too dark, or too light —
 *    it works in BOTH light and dark mode.
 * 5. Returns the ARGB Int (0xFFRRGGBB, alpha forced to FF).
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
            val bitmap = result.image?.toBitmap() ?: run {
                Logger.w(TAG) { "Coil returned null image" }
                return@withContext null
            }
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
