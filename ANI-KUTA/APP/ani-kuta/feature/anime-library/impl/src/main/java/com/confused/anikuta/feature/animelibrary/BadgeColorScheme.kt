package com.confused.anikuta.feature.animelibrary

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * D-242-fix15: Theme-adaptive badge colors.
 *
 * Derives SUB, DUB, and Score badge colors from the current theme's primary
 * color using HSV color-space analysis. The system:
 *
 * 1. Extracts the primary color's saturation (a measure of how "vibrant"
 *    the user's chosen accent is).
 * 2. Uses FIXED semantic hues for each badge type (SUB=blue 210°,
 *    DUB=orange 30°, Score=gold 45°) so they're always recognisable.
 * 3. Borrows saturation from the primary (boosted by +0.25, clamped to 0.95)
 *    so badges match the theme's vibrancy — vibrant themes get vibrant
 *    badges, muted themes get slightly muted badges.
 * 4. Adapts value (brightness) for dark vs light theme: containers are
 *    brighter in light theme, dimmer in dark theme; content text is the
 *    opposite for contrast.
 *
 * This produces bright, complementary colors that harmonise with any of
 * the app's 10+ accent presets + AMOLED + adaptive themes, while keeping
 * the semantic blue=SUB, orange=DUB, gold=Score convention.
 *
 * Usage:
 * ```
 * val badgeColors = rememberBadgeColorScheme()
 * CoverBadgeData("12", badgeColors.subContainer, badgeColors.subContent, BadgeIcons.Sub)
 * ```
 */
data class BadgeColorScheme(
    val subContainer: Color,
    val subContent: Color,
    val dubContainer: Color,
    val dubContent: Color,
    val scoreContainer: Color,
    val scoreContent: Color,
)

/**
 * Returns a [BadgeColorScheme] derived from the current [MaterialTheme] primary.
 *
 * Recomputes when the primary color or dark/light mode changes (via
 * [remember] keys). The HSV math is done once per recomposition, not per
 * badge — efficient even for large library grids.
 */
@Composable
fun rememberBadgeColorScheme(): BadgeColorScheme {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()

    return remember(primary, isDark) {
        // Extract the primary's saturation to gauge theme vibrancy.
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(primary.toArgb(), hsv)
        // Boost saturation but ensure a minimum of 0.65 so badges are always vivid.
        val baseSat = (hsv[1] + 0.25f).coerceIn(0.65f, 0.95f)

        // ── SUB: blue (210°) ──
        // Bright, recognisable "subtitles" blue. Slightly brighter container
        // in light theme for visibility; darker content text for contrast.
        val subContainer = colorFromHsv(
            h = 210f,
            s = baseSat,
            v = if (isDark) 0.45f else 0.78f,
        )
        val subContent = colorFromHsv(
            h = 210f,
            s = (baseSat + 0.1f).coerceAtMost(1f),
            v = if (isDark) 0.92f else 0.18f,
        )

        // ── DUB: orange (30°) ──
        // Warm, recognisable "dub" orange. Slightly higher value than SUB
        // because warm colours feel less harsh at high brightness.
        val dubContainer = colorFromHsv(
            h = 30f,
            s = baseSat,
            v = if (isDark) 0.50f else 0.82f,
        )
        val dubContent = colorFromHsv(
            h = 30f,
            s = (baseSat + 0.1f).coerceAtMost(1f),
            v = if (isDark) 0.92f else 0.20f,
        )

        // ── Score: gold (45°) ──
        // Universal "star rating" gold. Always high saturation + high value
        // for maximum visibility regardless of theme vibrancy.
        val scoreContainer = colorFromHsv(
            h = 45f,
            s = 0.95f,
            v = if (isDark) 0.45f else 0.80f,
        )
        val scoreContent = colorFromHsv(
            h = 45f,
            s = 1.0f,
            v = if (isDark) 0.95f else 0.15f,
        )

        BadgeColorScheme(
            subContainer = subContainer,
            subContent = subContent,
            dubContainer = dubContainer,
            dubContent = dubContent,
            scoreContainer = scoreContainer,
            scoreContent = scoreContent,
        )
    }
}

/**
 * Converts HSV components to a Compose [Color] via Android's [AndroidColor.HSVToColor].
 *
 * @param h Hue in degrees [0, 360]
 * @param s Saturation [0, 1]
 * @param v Value (brightness) [0, 1]
 */
private fun colorFromHsv(h: Float, s: Float, v: Float): Color {
    return Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
}
