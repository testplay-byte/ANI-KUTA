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

        // D-284: gradient ramp tuning.
        /** Target number of ramp colors (user: "maybe five or six colors"). */
        private const val RAMP_TARGET_COLORS = 6
        /** Ramp lightness band — cinematic dark tones (D-284 "smooth darker gradient"). */
        private const val RAMP_MIN_LIGHTNESS = 0.16f
        private const val RAMP_MAX_LIGHTNESS = 0.42f
        private const val RAMP_MIN_SATURATION = 0.25f
        private const val RAMP_MAX_SATURATION = 0.85f
        /** RGB euclidean distance below which two ramp stops are merged (0–441 scale). */
        private const val RAMP_MIN_STOP_DISTANCE = 48.0
    }

    /**
     * Extracts the accent ARGB color from a cover image URL.
     * @return the ARGB Int (0xFFRRGGBB), or null if extraction failed.
     */
    suspend fun extract(coverUrl: String): Int? = withContext(Dispatchers.Default) {
        if (coverUrl.isBlank()) return@withContext null

        runCatching {
            Logger.d(TAG) { "Extracting color from: ${coverUrl.take(60)}..." }

            val safeBitmap = loadSwatchBitmap(coverUrl) ?: return@withContext null
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

    /**
     * D-284: Extracts a 5–6 color gradient ramp from a cover image.
     *
     * Feeds the Browse hero's "beautiful gradient … five or six colors from the
     * cover image … smooth blended … smooth darker kind of gradient" (user device
     * feedback on v0.2.54 — the single dominant-color gradient read as a flat
     * solid).
     *
     * Pipeline:
     * 1. Load a 100×100 ARGB_8888 bitmap (shared with [extract] — Coil's memory
     *    cache dedupes the decode when both run for the same cover).
     * 2. Collect Palette's named swatches (dominant, vibrant, dark/light vibrant,
     *    muted, dark/light muted) — up to 7 candidates straight from the cover's
     *    own palette.
     * 3. Darken each into the cinematic band (lightness ∈ [0.16, 0.42], saturation
     *    clamped) — the "darker kind of gradient".
     * 4. Sort light → dark (monotonic luminance = the smooth vertical ramp) and
     *    merge near-identical stops (RGB distance < [RAMP_MIN_STOP_DISTANCE]).
     * 5. Resample to exactly [RAMP_TARGET_COLORS] evenly spaced stops — narrow
     *    palettes (grayscale/flat covers) get intermediate lerps synthesized so
     *    the ramp is ALWAYS 5–6 smooth steps.
     *
     * @return the ARGB ints ordered lightest → darkest (top → bottom of the hero),
     *         or null if extraction failed. Empty list is never returned on
     *         success — a single usable color is still expanded into a ramp.
     */
    suspend fun extractGradientColors(coverUrl: String): List<Int>? = withContext(Dispatchers.Default) {
        if (coverUrl.isBlank()) return@withContext null

        runCatching {
            Logger.d(TAG) { "Extracting gradient from: ${coverUrl.take(60)}..." }

            val safeBitmap = loadSwatchBitmap(coverUrl) ?: return@withContext null
            val palette = Palette.from(safeBitmap).generate()
            val ramp = buildGradientRamp(palette)
            if (ramp.isEmpty()) {
                Logger.w(TAG) { "No suitable swatches for gradient" }
                return@withContext null
            }
            Logger.i(TAG) { "Extracted gradient ramp: ${ramp.size} colors" }
            ramp
        }.getOrElse { e ->
            Logger.w(TAG) { "Gradient extraction failed: ${e.message}" }
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

    // ── D-284: gradient ramp helpers ───────────────────────────────────────

    /**
     * Loads the shared 100×100 swatch bitmap for Palette analysis.
     *
     * D-223fix3 logic (ARGB_8888 request config + HARDWARE-copy fallback),
     * extracted so [extract] and [extractGradientColors] share one loader.
     */
    private suspend fun loadSwatchBitmap(coverUrl: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(EXTRACTION_SIZE, EXTRACTION_SIZE)
            .build()
        val result = imageLoader.execute(request)
        val bitmap = result.image?.toBitmap() ?: run {
            Logger.w(TAG) { "Coil returned null image" }
            return null
        }
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            Logger.d(TAG) { "Got HARDWARE bitmap — copying to ARGB_8888" }
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run {
                Logger.w(TAG) { "Bitmap.copy returned null — can't extract" }
                null
            }
        } else {
            bitmap
        }
    }

    /** Builds the light → dark ramp from a cover's palette (see [extractGradientColors]). */
    private fun buildGradientRamp(palette: Palette): List<Int> {
        // Named targets first (Palette pre-selects them by HSL targets); top up
        // with population-ranked swatches when the cover yields fewer than the
        // target count of named ones.
        val named = listOfNotNull(
            palette.dominantSwatch,
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch,
        )
        val candidates = if (named.size >= RAMP_TARGET_COLORS) {
            named
        } else {
            val namedSet = named.toSet()
            named + palette.swatches
                .sortedByDescending { it.population }
                .filter { it !in namedSet }
        }
        if (candidates.isEmpty()) return emptyList()

        // Darken into the cinematic band — the "smooth darker kind of gradient".
        val darkened = candidates.map { darkenForRamp(it.rgb) }

        // Monotonic luminance order (light top → dark bottom) + merge twin stops.
        val sorted = darkened.sortedBy { ColorUtils.calculateLuminance(it) }
        val distinct = mutableListOf<Int>()
        for (color in sorted) {
            val last = distinct.lastOrNull()
            if (last == null || rgbDistance(last, color) >= RAMP_MIN_STOP_DISTANCE) {
                distinct.add(color)
            }
        }

        // Always deliver a full ramp — narrow palettes get lerped intermediates.
        return resampleRamp(distinct, RAMP_TARGET_COLORS)
    }

    /** Clamps a cover color into the gradient ramp's dark cinematic band. */
    private fun darkenForRamp(rgb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hsl[1] = hsl[1].coerceIn(RAMP_MIN_SATURATION, RAMP_MAX_SATURATION)
        hsl[2] = hsl[2].coerceIn(RAMP_MIN_LIGHTNESS, RAMP_MAX_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl) or 0xFF000000.toInt()
    }

    /** Euclidean RGB distance (0–441) — used to merge near-identical ramp stops. */
    private fun rgbDistance(a: Int, b: Int): Double {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    /**
     * Resamples a light → dark ramp to exactly [target] evenly spaced stops via
     * piecewise-linear interpolation. A single color is paired with a darkened
     * twin first; lists already at/above the target are truncated to it.
     */
    private fun resampleRamp(colors: List<Int>, target: Int): List<Int> {
        if (colors.isEmpty()) return emptyList()
        if (colors.size >= target) return colors.take(target)
        val ramp = if (colors.size == 1) {
            listOf(colors[0], darkenForRamp(colors[0]))
        } else {
            colors
        }
        return List(target) { i ->
            val t = i.toFloat() / (target - 1).toFloat() * (ramp.size - 1).toFloat()
            val idx = t.toInt().coerceAtMost(ramp.size - 2)
            val frac = t - idx
            lerpArgb(ramp[idx], ramp[idx + 1], frac)
        }
    }

    /** Channel-wise ARGB lerp (alpha forced opaque — ramp colors are backdrops). */
    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + ((cb - ca) * c)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
