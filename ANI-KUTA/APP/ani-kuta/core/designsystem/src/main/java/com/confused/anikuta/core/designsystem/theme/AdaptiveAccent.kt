package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ROUND 101 (WS-D): the ONE adaptive-accent wrapper.
 *
 * The details page derives a per-content accent from the cover image and
 * re-themes everything it renders with it. Until now that re-theme existed
 * as TWO copy-pasted `MaterialTheme(colorScheme = …)` blocks (the details
 * body + the D-231 episode-settings sheet) and several surfaces the user
 * called out were never wrapped at all (the resolved-streams sheet, the
 * link-sources sheets, the whole player page).
 *
 * This composable is the shared root for all of them:
 *
 * ```
 * AdaptiveAccentTheme(accentArgb = coverAccent?.toLong()) { …surface… }
 * ```
 *
 * A null/0 accent passes through unwrapped (the app's global theme — the
 * exact pre-existing behavior when no accent has been extracted yet).
 *
 * NOTE (design contract): only the primary quartet is overridden — the same
 * four slots both historical copies touched. Backgrounds/surfaces stay the
 * app's own so sheets keep their elevation/material language.
 */
@Composable
fun AdaptiveAccentTheme(
    /** The cover-derived ARGB accent (Long form — matches the nav keys). Null/0 = no accent. */
    accentArgb: Long?,
    content: @Composable () -> Unit,
) {
    val scheme = rememberAdaptiveColorScheme(accentArgb)
    if (scheme == null) {
        content()
        return
    }
    MaterialTheme(colorScheme = scheme) {
        content()
    }
}

/**
 * ROUND 101 (WS-D): the scheme HALF of the wrapper, for call sites that need
 * the scheme VALUE hoisted before their own structure (the details page's
 * body computes it up top and wraps a large subtree with
 * `MaterialTheme(colorScheme = …)`). Returns null when there is no accent —
 * callers keep the app's global scheme then.
 */
@Composable
fun rememberAdaptiveColorScheme(accentArgb: Long?): androidx.compose.ui.graphics.ColorScheme? {
    val accent = accentArgb?.takeIf { it != 0L }?.let { Color(it.toInt()) } ?: return null
    val accentColors = AccentColors.from(accent)
    val isDark = isSystemInDarkTheme()
    // Recomputed only when the accent actually changes (the extraction lands
    // once per content; the remember keys it).
    return androidx.compose.runtime.remember(accent, isDark) {
        MaterialTheme.colorScheme.copy(
            primary = if (isDark) accentColors.darkPrimary else accentColors.lightPrimary,
            onPrimary = if (isDark) accentColors.darkOnPrimary else accentColors.lightOnPrimary,
            primaryContainer = if (isDark) accentColors.darkPrimaryContainer else accentColors.lightPrimaryContainer,
            onPrimaryContainer = if (isDark) accentColors.darkOnPrimaryContainer else accentColors.lightOnPrimaryContainer,
        )
    }
}
