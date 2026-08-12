package com.confused.anikuta.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.preferences.UpdateMode
import org.koin.compose.koinInject

/**
 * D-193 Phase 3: Combined Updates & Notifications settings screen.
 *
 * Replaces the separate "Notifications" nav row with a combined section that
 * includes update checking settings + notification settings.
 *
 * Sub-screens:
 * - General (this screen) — master toggle, interval, sub/dub checking, test notification
 * - New Anime Defaults — trigger + audio defaults (existing NotificationsSettingsScreen)
 * - Library — per-anime notification config (existing NotificationsLibraryScreen)
 * - Update Categories — per-category checklist (shown when mode = Manual)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    onBack: () -> Unit,
    onOpenDefaults: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCategories: () -> Unit,
    onCheckNow: () -> Unit,
    onSendTestNotification: () -> Unit,
    updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences = koinInject(),
    notificationPreferences: com.confused.anikuta.core.preferences.NotificationPreferences = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()
    val notifEnabled by notificationPreferences.notificationsEnabledFlow().collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates & Notifications") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.automirrored.filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Updates master toggle (3-way: Auto / Manual / Off) ──
            SettingsSectionLabel("Updates")
            SegmentedToggle(
                options = listOf("Auto", "Manual", "Off"),
                selectedIndex = UpdateMode.entries.indexOf(mode),
                onSelect = { idx -> updatePreferences.setMode(UpdateMode.entries[idx]) },
            )
            Text(
                text = when (mode) {
                    UpdateMode.AUTO -> "Automatically check all library anime for new episodes."
                    UpdateMode.MANUAL -> "Only check selected categories for new episodes."
                    UpdateMode.OFF -> "No background checking. Manual refresh still works."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // ── Interval selector (shown when Auto or Manual) ──
            if (mode != UpdateMode.OFF) {
                SettingsNavRow(
                    icon = Icons.Filled.Sync,
                    title = "Check interval",
                    subtitle = formatInterval(intervalHours),
                    onClick = {
                        // Cycle through intervals: 6h → 12h → 24h → 2d → 3d → weekly → 6h
                        val intervals = listOf(6L, 12L, 24L, 48L, 72L, 168L)
                        val currentIdx = intervals.indexOf(intervalHours)
                        val nextIdx = (currentIdx + 1) % intervals.size
                        updatePreferences.setIntervalHours(intervals[nextIdx])
                    },
                )

                // Sub/Dub checking toggles
                ToggleRow(
                    icon = Icons.Filled.CloudSync,
                    title = "Check for sub episodes",
                    subtitle = "Check for new subbed episodes during background updates.",
                    checked = checkSub,
                    onCheckedChange = { updatePreferences.setCheckSub(it) },
                )
                ToggleRow(
                    icon = Icons.Filled.CloudSync,
                    title = "Check for dub episodes",
                    subtitle = "Check for new dubbed episodes during background updates.",
                    checked = checkDub,
                    onCheckedChange = { updatePreferences.setCheckDub(it) },
                )
                ToggleRow(
                    icon = Icons.Filled.LibraryBooks,
                    title = "Check dub on completed anime",
                    subtitle = "Continue checking for dub episodes even after the anime is completed.",
                    checked = checkDubCompleted,
                    onCheckedChange = { updatePreferences.setCheckDubCompleted(it) },
                )

                // Per-category checklist (shown when Manual)
                if (mode == UpdateMode.MANUAL) {
                    SettingsNavRow(
                        icon = Icons.Filled.LibraryBooks,
                        title = "Update categories",
                        subtitle = "Select which categories to check for updates.",
                        onClick = onOpenCategories,
                    )
                }

                // Check now button
                SettingsNavRow(
                    icon = Icons.Filled.AutoMode,
                    title = "Check now",
                    subtitle = "Trigger an immediate manual refresh.",
                    onClick = onCheckNow,
                )
            }

            // ── Notifications ──
            SettingsSectionLabel("Notifications")
            ToggleRow(
                icon = Icons.Filled.Notifications,
                title = "Enable notifications",
                subtitle = "Master toggle for the notification system.",
                checked = notifEnabled,
                onCheckedChange = { notificationPreferences.notificationsEnabled = it },
            )

            if (notifEnabled) {
                SettingsNavRow(
                    icon = Icons.Filled.Notifications,
                    title = "New anime defaults",
                    subtitle = "Default notification triggers + audio preferences.",
                    onClick = onOpenDefaults,
                )
                SettingsNavRow(
                    icon = Icons.Filled.LibraryBooks,
                    title = "Library",
                    subtitle = "Per-anime notification configuration.",
                    onClick = onOpenLibrary,
                )
                SettingsNavRow(
                    icon = Icons.Filled.Send,
                    title = "Send test notification",
                    subtitle = "Verify the notification setup works.",
                    onClick = onSendTestNotification,
                )
            }
        }
    }
}

private fun formatInterval(hours: Long): String = when (hours) {
    6L -> "Every 6 hours"
    12L -> "Every 12 hours"
    24L -> "Every 24 hours"
    48L -> "Every 2 days"
    72L -> "Every 3 days"
    168L -> "Weekly"
    else -> "Every $hours hours"
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
