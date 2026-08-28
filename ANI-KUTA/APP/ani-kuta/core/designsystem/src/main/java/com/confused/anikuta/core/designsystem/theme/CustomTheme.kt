package com.confused.anikuta.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * D-254 / D-261: The user's fully custom theme — the CUSTOM accent preset evolved
 * from "just a custom accent color" into a per-element color editor (Appearance →
 * General → Palettes → Custom, re-tap to open the sheet).
 *
 * Six user-editable elements (D-261 — brightness sliders removed entirely per
 * user feedback "there is no need for the brightness sliders at all"):
 * - [accent]          → the primary color family seed (existing AccentColors.from
 *                       derivation — same as every other preset).
 * - [background]       → the app canvas. The surface ramp is DERIVED from it via
 *                       small lerps toward the text color, so one pick produces a
 *                       coherent theme (no need to hand-tune 5 surfaces).
 * - [heading]          → the big screen titles (CollapsingHeader) — provided to
 *                       the tree via [LocalHeadingColor] (falls back to onBackground
 *                       when not customized).
 * - [card]             → the blocks/surfaces family (surfaceVariant +
 *                       surfaceContainer tiers — the settings cards, sheets, chips).
 * - [cardHeading]      → D-261 NEW — the title text inside cards/blocks (Browse
 *                       card titles, Library grid titles, Search result titles,
 *                       Details block headers). Provided via
 *                       [LocalCardHeadingColor]; falls back to onSurface when
 *                       Unspecified.
 * - [cardDescription]  → D-261 NEW — the body/description text inside cards/blocks
 *                       (Browse card subtitles, Library list meta, Details
 *                       synopsis body). Provided via [LocalCardDescriptionColor];
 *                       falls back to onSurfaceVariant when Unspecified.
 *
 * Custom colors apply as-is in BOTH light & dark mode (the mode toggle only
 * affects the presets); while a custom theme is active the AMOLED black-out is
 * skipped (custom wins — the user picked that background deliberately).
 *
 * Persistence (ThemePreferences): each element is stored as an ARGB Int. The
 * custom key `custom_accent_color` is reused as the accent slot. D-261 also
 * healed legacy v0.2.49/v0.2.50 installs that stored transparent (alpha-less)
 * values via the old `Color.value.toInt()` bug — `loadCustomTheme` treats any
 * stored alpha-0 value as unset and falls back to the default for that element.
 */
data class CustomThemeColors(
    val accent: Color,
    val background: Color,
    val heading: Color,
    val card: Color,
    val cardHeading: Color,
    val cardDescription: Color,
) {
    companion object {
        /** Mirrors the default dark theme (lime on warm darks). */
        fun default(): CustomThemeColors = CustomThemeColors(
            accent = AccentPreset.LIME.seed,
            background = BgDark,
            heading = TextDark,
            card = Surface2Dark,
            cardHeading = TextDark,
            cardDescription = TextMutedDark,
        )
    }
}

/**
 * The heading color for the current tree — [Color.Unspecified] means "not
 * customized, use the default onBackground". Provided by [AnikutaTheme] when
 * a custom theme sets a heading color; read by CollapsingHeader (and the
 * Library header clone, D-261).
 */
val LocalHeadingColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * D-261: the card/block title color for the current tree — [Color.Unspecified]
 * means "not customized, use the default onSurface". Provided by [AnikutaTheme]
 * when a custom theme sets a cardHeading color; read by Browse cards, Library
 * cards, Search result cards, Details block headers.
 */
val LocalCardHeadingColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * D-261: the card/block description color for the current tree —
 * [Color.Unspecified] means "not customized, use the default onSurfaceVariant".
 * Provided by [AnikutaTheme] when a custom theme sets a cardDescription color;
 * read by Browse card subtitles, Library card meta, Details synopsis body.
 */
val LocalCardDescriptionColor = staticCompositionLocalOf { Color.Unspecified }
