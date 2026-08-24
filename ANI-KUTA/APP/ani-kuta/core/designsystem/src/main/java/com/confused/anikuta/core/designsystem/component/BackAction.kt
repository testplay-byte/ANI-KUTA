package com.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small circular back button for use in a [CollapsingHeader]'s `actions` slot.
 *
 * Replaces ~12 private copies that were copy-pasted across the settings screens
 * (D-250). Canonical spec (matches the former `SettingsScreen.kt` private
 * `BackAction` — the de-facto reference all the other copies were cloned from):
 *  - 36dp `Box`, `surfaceVariant` background, [CircleShape].
 *  - 18dp `Icons.AutoMirrored.Filled.ArrowBack`, tinted `onSurfaceVariant`.
 *  - `contentDescription = "Back"` for screen readers.
 *  - Default ripple indication (it's a button, not a card — ripple is correct
 *    here; the no-ripple rule in DESIGN-LANGUAGE §3 "future rules" applies to
 *    *card* press animations, not icon buttons).
 *
 * @param onBack Invoked on click — typically `navController.popBackStack()`.
 * @param modifier Outer modifier (rarely needed — the button is self-sized).
 */
@Composable
fun BackAction(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
