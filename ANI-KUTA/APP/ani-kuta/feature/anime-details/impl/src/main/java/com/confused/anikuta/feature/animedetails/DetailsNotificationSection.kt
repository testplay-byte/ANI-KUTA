package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.notifications.NotificationConfig
import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.TriggerState
import com.confused.anikuta.core.preferences.NotificationPreferences
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * D-193 v2: Per-anime notification config section for the details page.
 *
 * Only shown when the user has enabled "Customize per anime" on the Notifications
 * settings page. When that toggle is OFF, this section is hidden and the default
 * triggers apply to this anime silently.
 *
 * Lets the user:
 * - Enable/disable notifications for this specific anime.
 * - Override the On Schedule + On Watchable triggers per anime.
 *
 * Reads/writes directly through [NotificationConfigStore] (no ViewModel needed —
 * the config is a single row keyed by mainId).
 *
 * @param mainId The anime's stable main_id.
 */
@Composable
fun DetailsNotificationSection(
    mainId: String?,
    notificationPreferences: NotificationPreferences = koinInject(),
    notificationConfigStore: NotificationConfigStore = koinInject(),
) {
    // Gate the whole section behind the library customization toggle.
    val libraryCustomEnabled by notificationPreferences.libraryCustomizationEnabledFlow()
        .collectAsState(initial = false)

    if (!libraryCustomEnabled || mainId == null) return

    // Load the per-anime config asynchronously on first composition.
    var config by remember(mainId) {
        mutableStateOf<NotificationConfig?>(null)
    }
    LaunchedEffect(mainId) {
        config = notificationConfigStore.getConfig(mainId) ?: NotificationConfig(mainId = mainId)
    }

    val current = config ?: return
    val scope = rememberCoroutineScope()

    fun update(newConfig: NotificationConfig) {
        config = newConfig
        scope.launch { notificationConfigStore.setConfig(newConfig) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = "Notifications",
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Enable toggle for this anime.
            NotificationToggleRow(
                title = "Enable for this anime",
                checked = current.enabled,
                onCheckedChange = { v -> update(current.copy(enabled = v)) },
            )

            // Per-anime trigger overrides — only shown when enabled.
            AnimatedVisibility(
                visible = current.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    NotificationToggleRow(
                        title = "On schedule",
                        checked = current.notifyOnSchedule == TriggerState.ON,
                        onCheckedChange = { enabled ->
                            val s = if (enabled) TriggerState.ON else TriggerState.OFF
                            update(current.copy(notifyOnSchedule = s))
                        },
                    )
                    Text(
                        text = "Notify when the airing time is reached",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    NotificationToggleRow(
                        title = "On watchable",
                        checked = current.notifyOnWatchable == TriggerState.ON,
                        onCheckedChange = { enabled ->
                            val s = if (enabled) TriggerState.ON else TriggerState.OFF
                            update(current.copy(notifyOnWatchable = s))
                        },
                    )
                    Text(
                        text = "Notify when an episode is found on a source",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
