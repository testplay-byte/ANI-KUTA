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
