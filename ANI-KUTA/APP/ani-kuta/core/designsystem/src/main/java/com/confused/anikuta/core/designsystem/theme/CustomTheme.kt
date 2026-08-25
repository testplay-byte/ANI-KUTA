package com.confused.anikuta.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * D-254: The user's fully custom theme — the CUSTOM accent preset evolved
 * from "just a custom accent color" into a per-element color + brightness
 * editor (Appearance → General → Palettes → Custom, re-tap to open the sheet).
 *
 * Four user-editable elements (each with its own brightness offset):
 * - [accent]     → the primary color family seed (existing AccentColors.from
 *                  derivation — same as every other preset).
 * - [background] → the app canvas. The surface ramp is DERIVED from it via
 *                  small lerps toward the text color, so one pick produces a
 *                  coherent theme (no need to hand-tune 5 surfaces).
 * - [heading]    → the big screen titles (CollapsingHeader) — provided to the
 *                  tree via [LocalHeadingColor] (falls back to onBackground
 *                  when not customized).
 * - [card]       → the blocks/surfaces family (surfaceVariant +
 *                  surfaceContainer tiers — the settings cards, sheets, chips).
 *
 * Brightness offsets are −1f..1f (0 = neutral): positive lerps toward white,
 * negative toward black. Applied AFTER the base color, so "dark background +
 * +20% brightness" = a slightly lifted dark background.
 *
 * Custom colors apply as-is in BOTH light & dark mode (the mode toggle only
 * affects the presets); while a custom theme is active the AMOLED black-out is
 * skipped (custom wins — the user picked that background deliberately).
 */
data class CustomThemeColors(
    val accent: Color,
    val background: Color,
    val heading: Color,
    val card: Color,
    val accentBrightness: Float = 0f,
    val backgroundBrightness: Float = 0f,
    val headingBrightness: Float = 0f,
    val cardBrightness: Float = 0f,
) {
    companion object {
        /** Mirrors the default dark theme (lime on warm darks). */
        fun default(): CustomThemeColors = CustomThemeColors(
            accent = AccentPreset.LIME.seed,
            background = BgDark,
            heading = TextDark,
            card = Surface2Dark,
        )
    }
}

/** Applies a brightness offset (−1..1) to a color: positive → white, negative → black. */
fun applyBrightness(color: Color, brightness: Float): Color {
    if (brightness == 0f) return color
    return if (brightness > 0f) {
        lerp(color, Color.White, brightness.coerceIn(0f, 1f))
    } else {
        lerp(color, Color.Black, -brightness.coerceIn(0f, 1f))
    }
}

/** All four colors with their brightness offsets applied. */
fun CustomThemeColors.resolved(): CustomThemeColors = CustomThemeColors(
    accent = applyBrightness(accent, accentBrightness),
    background = applyBrightness(background, backgroundBrightness),
    heading = applyBrightness(heading, headingBrightness),
    card = applyBrightness(card, cardBrightness),
)

/**
 * The heading color for the current tree — [Color.Unspecified] means "not
 * customized, use the default onBackground". Provided by [AnikutaTheme] when
 * a custom theme sets a heading color; read by CollapsingHeader.
 */
val LocalHeadingColor = staticCompositionLocalOf { Color.Unspecified }
