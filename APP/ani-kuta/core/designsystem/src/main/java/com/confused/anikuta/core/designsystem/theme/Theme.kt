package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 */
@Composable
fun AnikutaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AnikutaTypography,
        shapes = AnikutaShapes,
        content = content,
    )
}
