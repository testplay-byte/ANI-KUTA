package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = PrimaryFgDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    background = BgDark,
    onBackground = TextDark,
    surface = Surface1Dark,
    onSurface = TextDark,
    surfaceVariant = Surface3Dark,
    onSurfaceVariant = TextMutedDark,
    surfaceContainer = Surface2Dark,
    surfaceContainerHigh = Surface3Dark,
    surfaceContainerHighest = Surface4Dark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = PrimaryFgLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    background = BgLight,
    onBackground = TextLight,
    surface = Surface1Light,
    onSurface = TextLight,
    surfaceVariant = Surface3Light,
    onSurfaceVariant = TextMutedLight,
    surfaceContainer = Surface2Light,
    surfaceContainerHigh = Surface3Light,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

/**
 * Builds the fully-custom color scheme (D-254) from a [CustomThemeColors].
 *
 * Derivation rules (one user pick → a coherent theme):
 * - Accent family: the existing [AccentColors.from] derivation from the
 *   (brightness-adjusted) accent seed — identical to every preset.
 * - Text colors: picked by the custom background's luminance — dark text on
 *   light backgrounds, light text on dark ones (works for any pick).
 * - Surface ramp: the background lerped slightly toward the text color
 *   (4 / 8 / 12 / 16 %) — elevation tiers emerge from the single pick.
 * - Card family: the user's card color drives surfaceVariant + the
 *   surfaceContainer tiers (card lerped toward the background).
 * - Outline: the card color lerped toward the text color.
 */
private fun buildCustomColorScheme(custom: CustomThemeColors): ColorScheme {
    val c = custom.resolved()
    val isDarkBg = c.background.luminance() < 0.5f
    val text = if (isDarkBg) TextDark else TextLight
    val textMuted = if (isDarkBg) TextMutedDark else TextMutedLight
    val accent = AccentColors.from(c.accent)

    // Surface ramp derived from the background: small lerps toward text.
    val surface = lerp(c.background, text, 0.04f)
    val surfaceContainer = lerp(c.background, text, 0.08f)
    val surfaceContainerHigh = lerp(c.background, text, 0.12f)
    val surfaceContainerHighest = lerp(c.background, text, 0.16f)

    // Card family: the user's card color + tiers lerped toward the background.
    val cardVariant = c.card
    val cardContainer = lerp(c.card, c.background, 0.35f)

    val outline = lerp(c.card, text, 0.45f)
    val outlineVariant = lerp(c.card, c.background, 0.55f)

    val scheme = if (isDarkBg) DarkColorScheme else LightColorScheme
    return scheme.copy(
        primary = if (isDarkBg) accent.darkPrimary else accent.lightPrimary,
        onPrimary = if (isDarkBg) accent.darkOnPrimary else accent.lightOnPrimary,
        primaryContainer = if (isDarkBg) accent.darkPrimaryContainer else accent.lightPrimaryContainer,
        onPrimaryContainer = if (isDarkBg) accent.darkOnPrimaryContainer else accent.lightOnPrimaryContainer,
        background = c.background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = cardVariant,
        onSurfaceVariant = textMuted,
        surfaceContainer = cardContainer,
        surfaceContainerHigh = lerp(cardContainer, cardVariant, 0.5f),
        surfaceContainerHighest = cardVariant,
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

/**
 * ANI-KUTA theme — lime accent on warm-purple-tinted darks (default).
 * Follows DESIGN-LANGUAGE.md.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param amoled When `true` AND `darkTheme`, swaps backgrounds/surfaces to
 *               pure `Color.Black` for OLED screens (saves power on AMOLED).
 *               SKIPPED while [customTheme] is active — the custom background
 *               wins (D-254).
 * @param accentSeed The accent seed color used to override the primary color
 *                   family (primary / primaryContainer / onPrimary /
 *                   onPrimaryContainer) in both light + dark schemes. Defaults
 *                   to Lime. See [AccentColors.from] for the derivation.
 *                   Pass [AccentPreset.CUSTOM]'s stored custom color here when
 *                   the user selected Custom (D-037: highly customizable UI).
 * @param customTheme D-254: the fully-custom per-element theme. When non-null,
 *                    the scheme is built by [buildCustomColorScheme] (custom
 *                    background + card family + accent + heading via
 *                    [LocalHeadingColor]) and applies in BOTH light and dark
 *                    mode. Pass `null` for every preset except a CONFIGURED
 *                    Custom (a bare Custom selection without configuration
 *                    still works through [accentSeed]).
 * @param content The content to theme.
 */
@Composable
fun AnikutaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    accentSeed: Color = AccentPreset.LIME.seed,
    customTheme: CustomThemeColors? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (customTheme != null) {
        // D-254: fully-custom theme — applies as-is regardless of light/dark.
        buildCustomColorScheme(customTheme)
    } else {
        val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
        // Override the primary color family with the accent seed's derived colors.
        val accent = AccentColors.from(accentSeed)
        val accentScheme = baseScheme.copy(
            primary = if (darkTheme) accent.darkPrimary else accent.lightPrimary,
            onPrimary = if (darkTheme) accent.darkOnPrimary else accent.lightOnPrimary,
            primaryContainer = if (darkTheme) accent.darkPrimaryContainer else accent.lightPrimaryContainer,
            onPrimaryContainer = if (darkTheme) accent.darkOnPrimaryContainer else accent.lightOnPrimaryContainer,
        )
        if (darkTheme && amoled) {
            accentScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF111111),
                surfaceContainer = Color(0xFF0A0A0A),
                surfaceContainerHigh = Color(0xFF161616),
                surfaceContainerHighest = Color(0xFF1A1A1A),
            )
        } else {
            accentScheme
        }
    }

    // D-255 (palette-navigation fix): ALWAYS provide the local + MaterialTheme
    // through the SAME composition structure. The previous if/else around
    // CompositionLocalProvider MOVED `content` between two branches whenever
    // customTheme flipped null ↔ non-null (selecting/deselecting the CUSTOM
    // palette) — moving content between call sites destroys every remember{}
    // under it (including AppRoot's nav backstack), which navigated the user
    // back to Browse. Providing an Unspecified sentinel keeps the structure
    // stable; only the VALUE changes.
    val headingColor = customTheme?.resolved()?.heading ?: Color.Unspecified

    CompositionLocalProvider(LocalHeadingColor provides headingColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AnikutaTypography,
            shapes = AnikutaShapes,
            content = content,
        )
    }
}
