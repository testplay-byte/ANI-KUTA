package com.confused.anikuta

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import org.koin.compose.koinInject

/**
 * Debug-only composable: a "Show debug bubble" toggle for the Settings screen.
 *
 * Lives in `:app/src/debug` so `:app/src/main` can call it without a
 * compile-time dependency on `:feature:debug-bubble`. The release source set
 * provides a no-op counterpart.
 *
 * Renders a SettingsGroupCard-style row with a Switch bound to
 * [DebugBubblePreferences.visible] (default true — visible by default per D-163).
 */
@Composable
fun DebugBubbleToggle() {
    val prefs = koinInject<DebugBubblePreferences>()
    val visible by prefs.visibleFlow().collectAsStateWithLifecycle(initialValue = prefs.visible)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Show debug bubble",
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = visible,
            onCheckedChange = { prefs.visible = it },
        )
    }
}
