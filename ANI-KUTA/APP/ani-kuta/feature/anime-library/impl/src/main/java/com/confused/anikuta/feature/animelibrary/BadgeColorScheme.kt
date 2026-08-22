package com.confused.anikuta.feature.animelibrary

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * D-242-fix16: Hand-picked, theme-adaptive badge colors.
 *
 * The SUB, DUB, and Score badge colors are hand-picked from Material Design's
 * color palette for consistency and visual harmony. They are NOT derived from
 * HSV math — instead, specific light/dark shade pairs are chosen for maximum
 * readability and aesthetic appeal.
 *
 * Design principles:
 * - **SUB (blue)**: Lighter, airy shades. Light theme uses Blue 100/900 pairing
 *   (soft sky-blue background, deep navy text). Dark theme uses Blue 200/50
 *   (bright sky-blue background, near-white text).
 * - **DUB (orange)**: Warm, lighter shades. Light theme uses Deep Orange 100/900
 *   (soft peach background, burnt-orange text). Dark theme uses Orange 200/50
 *   (bright peach background, cream text).
 * - **Score**: A refined amber that's softer than pure gold. Light theme uses
 *   Amber 50/900 (cream background, dark amber text). Dark theme uses Amber
 *   300/900 (warm amber background, dark brown text).
 *
 * The `rememberBadgeColorScheme()` function adapts the selection based on
 * whether the app is in dark or light mode.
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
 * Returns a [BadgeColorScheme] with hand-picked colors adapted to the current
 * light/dark mode. Recomputes only when dark/light mode changes.
 */
@Composable
fun rememberBadgeColorScheme(): BadgeColorScheme {
    val isDark = isSystemInDarkTheme()

    return remember(isDark) {
        if (isDark) {
            BadgeColorScheme(
                // SUB: bright sky-blue background, near-white text (dark theme).
                subContainer = Color(0xFF64B5F6),  // Blue 200
                subContent = Color(0xFF0D47A1),    // Blue 900 (dark navy for contrast)
                // DUB: bright peach background, cream text (dark theme).
                dubContainer = Color(0xFFFFCC80),  // Orange 200
                dubContent = Color(0xFFBF360C),    // Deep Orange 900
                // Score: warm amber background, dark brown text (dark theme).
                scoreContainer = Color(0xFFFFCA28), // Amber 300
                scoreContent = Color(0xFF3E2723),   // Brown 900
            )
        } else {
            BadgeColorScheme(
                // SUB: soft sky-blue background, deep navy text (light theme).
                subContainer = Color(0xFFBBDEFB),  // Blue 100
                subContent = Color(0xFF0D47A1),    // Blue 900
                // DUB: soft peach background, burnt-orange text (light theme).
                dubContainer = Color(0xFFFFCCBC),  // Deep Orange 100
                dubContent = Color(0xFFBF360C),    // Deep Orange 900
                // Score: cream background, dark amber text (light theme).
                scoreContainer = Color(0xFFFFECB3), // Amber 100 (soft cream)
                scoreContent = Color(0xFFE65100),   // Amber 900 (deep amber)
            )
        }
    }
}
