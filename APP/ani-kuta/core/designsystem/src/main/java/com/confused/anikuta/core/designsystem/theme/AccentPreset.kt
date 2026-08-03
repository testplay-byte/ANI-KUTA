package com.confused.anikuta.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The accent palette — the primary color family used throughout the app.
 *
 * 10 accent-only presets + 1 [CUSTOM]. Selecting a preset overrides the theme's
 * `primary` / `primaryContainer` / `onPrimary` / `onPrimaryContainer` colors
 * (light + dark). The background/surface ramp stays fixed (warm darks / warm
 * lights) so ONLY the accent changes — this keeps the proven aesthetic while
 * letting the user pick their accent (D-037: highly customizable UI).
 *
 * [CUSTOM] applies a user-chosen color (stored as ARGB in ThemePreferences).
 * The color-picker UI is Phase 5 — for now CUSTOM defaults to the Lime seed,
 * but the selection + storage mechanism is fully wired so Phase 5 only adds
 * the picker, not the plumbing.
 *
 * Order matches the old project (owner-specified): Lime, Coral, Rose, Amber,
 * Red, Teal, Blue, Cyan, Violet, Emerald, then Custom last.
 */
enum class AccentPreset(
    val displayName: String,
    val seed: Color,
) {
    LIME("Lime", Color(0xFFB1F256)),
    CORAL("Coral", Color(0xFFFF7043)),
    ROSE("Rose", Color(0xFFEC407A)),
    AMBER("Amber", Color(0xFFFFC107)),
    RED("Red", Color(0xFFF44336)),
    TEAL("Teal", Color(0xFF009688)),
    BLUE("Blue", Color(0xFF2196F3)),
    CYAN("Cyan", Color(0xFF00BCD4)),
    VIOLET("Violet", Color(0xFF9C27B0)),
    EMERALD("Emerald", Color(0xFF2E7D32)),
    CUSTOM("Custom", Color(0xFFB1F256)),
    ;

    companion object {
        /** Case-insensitive lookup by name; falls back to [LIME] for unknown values. */
        fun fromName(name: String?): AccentPreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LIME
    }
}

/**
 * The 8 derived theme colors for one accent [seed] (4 dark + 4 light).
 *
 * Container/onContainer colors are DERIVED from the seed via [lerp] against the
 * fixed surface/text colors — consistent across all presets, no per-color
 * hand-tuning needed. This is the "proper" derivation: dark mode uses the vivid
 * seed as primary with a darkened seed for on-primary text; light mode darkens
 * the seed 35% toward black for readable contrast on light backgrounds.
 *
 * @param seed the accent seed color (e.g. [AccentPreset.seed] or a custom color).
 */
data class AccentColors(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
) {
    companion object {
        fun from(seed: Color): AccentColors = AccentColors(
            // Dark: vivid seed on dark bg; on-primary = very dark seed for contrast.
            darkPrimary = seed,
            darkOnPrimary = lerp(seed, Color.Black, 0.85f),
            darkPrimaryContainer = lerp(seed, BgDark, 0.78f),
            darkOnPrimaryContainer = lerp(seed, TextDark, 0.45f),
            // Light: darker seed for contrast on light bg; on-primary = white.
            lightPrimary = lerp(seed, Color.Black, 0.35f),
            lightOnPrimary = Color.White,
            lightPrimaryContainer = lerp(seed, BgLight, 0.78f),
            lightOnPrimaryContainer = lerp(seed, TextLight, 0.45f),
        )

        /** Convenience: resolve a (preset, customColor) pair into a seed. */
        fun seedFor(preset: AccentPreset, customColor: Color?): Color =
            if (preset == AccentPreset.CUSTOM && customColor != null) customColor else preset.seed
    }
}
