package com.confused.anikuta.core.player.controls

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Shared "themed dark glass" color helper for the player controls overlays.
 *
 * Returns the user's accent/primary color shifted ~55% toward black, with
 * ~62% opacity for translucency. Used by both MinimizedControls (center
 * play/pause) and FullscreenControls (center play/pause + skip buttons).
 *
 * Ported from the old project's ThemedGlass.kt.
 */
@Composable
internal fun themedDarkGlassColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val darkened = lerp(primary, Color.Black, 0.55f)
    return darkened.copy(alpha = 0.62f)
}

/**
 * D-457: the dedicated PLAY/PAUSE glass — noticeably lighter on the theme
 * color (only ~30% toward black) and more transparent (42% opacity) than
 * [themedDarkGlassColor], per the device round ("make them a little bit on
 * the lighter side of the theme color and also make the background … a
 * little bit transparent"). The icon is enlarged within the same button
 * size at the call sites. Used by the minimized AND fullscreen play/pause.
 */
@Composable
internal fun themedPlayPauseColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val darkened = lerp(primary, Color.Black, 0.30f)
    return darkened.copy(alpha = 0.42f)
}
