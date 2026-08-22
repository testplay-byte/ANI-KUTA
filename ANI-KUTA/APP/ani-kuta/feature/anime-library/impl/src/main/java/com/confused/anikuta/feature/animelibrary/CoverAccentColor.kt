package com.confused.anikuta.feature.animelibrary

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap

/**
 * D-242-fix19: Adaptive border color extractor.
 *
 * Returns a vibrant/dominant color extracted from [coverUrl], HSL-adjusted
 * so it's visible as a border against BOTH the cover image and the screen
 * background in light or dark theme.
 *
 * - Coil 3 (3.0.4) has no built-in Palette artifact, so we pair
 *   [ImageLoader.execute] with AndroidX [Palette].
 * - Extraction runs off the main thread inside [produceState]; only re-runs
 *   when [coverUrl] actually changes.
 * - Returns null on blank URL, network failure, decode failure, or when no
 *   suitable swatch is available.
 * - HARDWARE bitmaps are handled via ARGB_8888 copy (per D-223fix3).
 *
 * Color adjustment:
 * - Saturation boosted to >= 0.45 so it reads as colored, not gray.
 * - Dark theme:  lightness in [0.55, 0.72] → lighter accent on dark bg.
 * - Light theme: lightness in [0.38, 0.55] → darker accent on light bg.
 */
@Composable
fun rememberCoverAccentColor(coverUrl: String?): Color? {
    val context = LocalContext.current
    val imageLoader: ImageLoader = context.imageLoader
    val isDark = isSystemInDarkTheme()

    val key = coverUrl.orEmpty()
    val state = produceState<Color?>(initialValue = null, key1 = key, key2 = isDark) {
        if (coverUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = extractDominantColor(imageLoader, context, coverUrl, isDark)
    }
    return state.value
}

private suspend fun extractDominantColor(
    imageLoader: ImageLoader,
    context: Context,
    coverUrl: String,
    isDark: Boolean,
): Color? {
    // 100x100 is enough for Palette; smaller = faster + lower memory.
    val request = ImageRequest.Builder(context)
        .data(coverUrl)
        .size(100, 100)
        .build()

    val result = runCatching { imageLoader.execute(request) }.getOrNull() ?: return null
    val image = result.image ?: return null
    val bitmap = runCatching { image.toBitmap() }.getOrNull() ?: return null

    // HARDWARE bitmaps aren't CPU-readable — Palette.from() throws.
    // Copy to ARGB_8888 after loading (D-223fix3 approach).
    val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }

    val palette = Palette.from(safeBitmap).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null

    return adjustForVisibility(swatch.rgb, isDark)
}

/**
 * HSL adjustment so the color is usable as a border in both themes.
 */
private fun adjustForVisibility(rgb: Int, isDark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 1.0f)
    hsl[2] = if (isDark) hsl[2].coerceIn(0.55f, 0.72f)
             else         hsl[2].coerceIn(0.38f, 0.55f)
    val argb = ColorUtils.HSLToColor(hsl) or 0xFF000000.toInt()
    return Color(argb.toLong() and 0xFFFFFFFFL)
}
