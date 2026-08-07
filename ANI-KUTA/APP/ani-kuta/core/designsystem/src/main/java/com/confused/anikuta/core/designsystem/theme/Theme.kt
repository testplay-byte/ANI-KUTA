package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
 * ANI-KUTA theme — lime accent on warm-purple-tinted darks (default).
 * Follows DESIGN-LANGUAGE.md.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param amoled When `true` AND `darkTheme`, swaps backgrounds/surfaces to
 *               pure `Color.Black` for OLED screens (saves power on AMOLED).
 * @param accentSeed The accent seed color used to override the primary color
 *                   family (primary / primaryContainer / onPrimary /
 *                   onPrimaryContainer) in both light + dark schemes. Defaults
 *                   to Lime. See [AccentColors.from] for the derivation.
 *                   Pass [AccentPreset.CUSTOM]'s stored custom color here when
 *                   the user selected Custom (D-037: highly customizable UI).
 * @param content The content to theme.
 */
@Composable
fun AnikutaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    accentSeed: Color = AccentPreset.LIME.seed,
    content: @Composable () -> Unit,
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // Override the primary color family with the accent seed's derived colors.
    val accent = AccentColors.from(accentSeed)
    val accentScheme = baseScheme.copy(
        primary = if (darkTheme) accent.darkPrimary else accent.lightPrimary,
        onPrimary = if (darkTheme) accent.darkOnPrimary else accent.lightOnPrimary,
        primaryContainer = if (darkTheme) accent.darkPrimaryContainer else accent.lightPrimaryContainer,
        onPrimaryContainer = if (darkTheme) accent.darkOnPrimaryContainer else accent.lightOnPrimaryContainer,
    )
    val colorScheme = if (darkTheme && amoled) {
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnikutaTypography,
        shapes = AnikutaShapes,
        content = content,
    )
}
