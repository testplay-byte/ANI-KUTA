package com.confused.anikuta.feature.animelibrary

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * D-242-fix18: Hand-picked, theme-adaptive badge colors.
 *
 * The SUB, DUB, Total, Score, and AllCaughtUp badge colors are hand-picked
 * from Material Design's color palette for consistency and visual harmony.
 *
 * Design principles:
 * - **SUB (blue)**: Lighter, airy shades. Blue 100/900 (light), Blue 200/900 (dark).
 * - **DUB (orange)**: Warm, lighter shades. Deep Orange 100/900 (light), Orange 200/900 (dark).
 * - **Total (green)**: A pleasant green shade — Light Green 100/900 (light),
 *   Light Green 200/900 (dark). Visually distinct from SUB's blue and DUB's orange.
 * - **Score (amber)**: A refined amber. Amber 100/900 (light), Amber 300/900 (dark).
 * - **AllCaughtUp (red/rose)**: A warm red indicating "completed". Red 100/900
 *   (light), Red 200/900 (dark).
 *
 * The `rememberBadgeColorScheme()` function adapts the selection based on
 * whether the app is in dark or light mode.
 */
data class BadgeColorScheme(
    val subContainer: Color,
    val subContent: Color,
    val dubContainer: Color,
    val dubContent: Color,
    val totalContainer: Color,
    val totalContent: Color,
    val scoreContainer: Color,
    val scoreContent: Color,
    val allCaughtUpContainer: Color,
    val allCaughtUpContent: Color,
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
                // SUB: bright sky-blue background, dark navy text (dark theme).
                subContainer = Color(0xFF64B5F6),  // Blue 200
                subContent = Color(0xFF0D47A1),    // Blue 900
                // DUB: bright peach background, deep orange text (dark theme).
                dubContainer = Color(0xFFFFCC80),  // Orange 200
                dubContent = Color(0xFFBF360C),    // Deep Orange 900
                // Total: bright light-green background, dark green text (dark theme).
                totalContainer = Color(0xFFA5D6A7),  // Light Green 200
                totalContent = Color(0xFF1B5E20),    // Green 900
                // Score: warm amber background, dark brown text (dark theme).
                scoreContainer = Color(0xFFFFCA28), // Amber 300
                scoreContent = Color(0xFF3E2723),   // Brown 900
                // AllCaughtUp: bright red background, dark red text (dark theme).
                allCaughtUpContainer = Color(0xFFEF9A9A),  // Red 200
                allCaughtUpContent = Color(0xFFB71C1C),    // Red 900
            )
        } else {
            BadgeColorScheme(
                // SUB: soft sky-blue background, deep navy text (light theme).
                subContainer = Color(0xFFBBDEFB),  // Blue 100
                subContent = Color(0xFF0D47A1),    // Blue 900
                // DUB: soft peach background, burnt-orange text (light theme).
                dubContainer = Color(0xFFFFCCBC),  // Deep Orange 100
                dubContent = Color(0xFFBF360C),    // Deep Orange 900
                // Total: soft light-green background, dark green text (light theme).
                totalContainer = Color(0xFFC8E6C9),  // Light Green 100
                totalContent = Color(0xFF1B5E20),    // Green 900
                // Score: cream background, dark amber text (light theme).
                scoreContainer = Color(0xFFFFECB3), // Amber 100 (soft cream)
                scoreContent = Color(0xFFE65100),   // Amber 900 (deep amber)
                // AllCaughtUp: soft red background, dark red text (light theme).
                allCaughtUpContainer = Color(0xFFFFCDD2),  // Red 100
                allCaughtUpContent = Color(0xFFB71C1C),    // Red 900
            )
        }
    }
}
