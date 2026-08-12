package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.UpdateMode
import org.koin.compose.koinInject

/**
 * D-193 Phase 3 + improvements: Combined Updates & Notifications settings screen.
 *
 * Redesigned to match the project's design language:
 * - CollapsingHeader (shrinks on scroll, with back action)
 * - ScrollBlurOverlay (gradient blur at the top edge)
 * - SettingsGroupCard (labeled cards for grouping settings)
 * - AnimatedVisibility (smooth show/hide of sections)
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
    updateScheduler: com.confused.anikuta.core.updates.UpdateScheduler = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()
    val notifEnabled by notificationPreferences.notificationsEnabledFlow().collectAsState(initial = true)

    // D-193 improvement: Check Now popup state
    var showCheckNowDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Updates & Notifications",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── Updates section ──
                    item {
                        SettingsGroupCard(label = "Updates") {
                            // 3-way master toggle
                            SettingRow(
                                title = "Update mode",
                                description = when (mode) {
                                    UpdateMode.AUTO -> "Automatically checks anime with episodes yet to be released"
                                    UpdateMode.MANUAL -> "Only checks selected categories"
                                    UpdateMode.OFF -> "No background checking. Manual refresh still works."
                                },
                            ) {
                                SegmentedToggle(
                                    options = listOf("Auto", "Manual", "Off"),
                                    selectedIndex = UpdateMode.entries.indexOf(mode),
                                    onSelect = { idx ->
                                        updatePreferences.setMode(UpdateMode.entries[idx])
                                        updateScheduler.reschedule()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // ── Update settings (shown when mode != OFF) ──
                    item {
                        Column {
                            AnimatedVisibility(
                                visible = mode != UpdateMode.OFF,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                SettingsGroupCard(label = "Checking") {
                                    // Interval selector — only shown in MANUAL mode (per user feedback)
                                    if (mode == UpdateMode.MANUAL) {
                                        SettingRow(
                                            title = "Check interval",
                                            description = formatInterval(intervalHours),
                                            trailing = {
                                                TextButton(onClick = {
                                                    val intervals = listOf(6L, 12L, 24L, 48L, 72L, 168L)
                                                    val currentIdx = intervals.indexOf(intervalHours)
                                                    val nextIdx = (currentIdx + 1) % intervals.size
                                                    updatePreferences.setIntervalHours(intervals[nextIdx])
                                                    updateScheduler.reschedule()
                                                }) {
                                                    Text(formatIntervalShort(intervalHours), fontWeight = FontWeight.Bold)
                                                }
                                            },
                                        )
                                    }

                                    // Per-category checklist — only shown in MANUAL mode
                                    if (mode == UpdateMode.MANUAL) {
                                        SettingRow(
                                            title = "Update categories",
                                            description = "Select which categories to check",
                                            trailing = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "Open",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                        )
                                    }

                                    // Sub/Dub checking toggles
                                    SettingRow(
                                        title = "Check for sub episodes",
                                        description = "Check for new subbed episodes",
                                        trailing = {
                                            Switch(
                                                checked = checkSub,
                                                onCheckedChange = { updatePreferences.setCheckSub(it) },
                                            )
                                        },
                                    )
                                    SettingRow(
                                        title = "Check for dub episodes",
                                        description = "Check for new dubbed episodes",
                                        trailing = {
                                            Switch(
                                                checked = checkDub,
                                                onCheckedChange = { updatePreferences.setCheckDub(it) },
                                            )
                                        },
                                    )
                                    SettingRow(
                                        title = "Check dub on completed anime",
                                        description = "Continue checking for dub after completion",
                                        showDivider = false,
                                        trailing = {
                                            Switch(
                                                checked = checkDubCompleted,
                                                onCheckedChange = { updatePreferences.setCheckDubCompleted(it) },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Check Now button ──
                    item {
                        SettingsGroupCard(label = "Manual Check") {
                            SettingRow(
                                title = "Check now",
                                description = "Trigger an immediate refresh",
                                showDivider = false,
                                trailing = {
                                    Icon(
                                        Icons.Filled.Sync,
                                        contentDescription = "Check",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                        Surface(
                            onClick = { showCheckNowDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AutoMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Check now",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // ── Notifications section ──
                    item {
                        SettingsGroupCard(label = "Notifications") {
                            SettingRow(
                                title = "Enable notifications",
                                description = "Master switch for all notifications",
                                trailing = {
                                    Switch(
                                        checked = notifEnabled,
                                        onCheckedChange = { notificationPreferences.notificationsEnabled = it },
                                    )
                                },
                            )
                        }
                    }

                    // ── Notification sub-settings (shown when notifications enabled) ──
                    item {
                        Column {
                            AnimatedVisibility(
                                visible = notifEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                SettingsGroupCard(label = "Configuration") {
                                    SettingRow(
                                        title = "New anime defaults",
                                        description = "Default triggers + audio preferences",
                                        trailing = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                    )
                                    SettingRow(
                                        title = "Library",
                                        description = "Per-anime notification configuration",
                                        trailing = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                    )
                                    SettingRow(
                                        title = "Send test notification",
                                        description = "Posts a demo + delayed notification",
                                        showDivider = false,
                                        trailing = {
                                            Icon(
                                                Icons.Filled.Send,
                                                contentDescription = "Send",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }

    // D-193 improvement: Check Now popup with animation
    if (showCheckNowDialog) {
        CheckNowDialog(
            onDismiss = { showCheckNowDialog = false },
            onConfirm = {
                showCheckNowDialog = false
                onCheckNow()
            },
        )
    }
}

// ── Check Now Dialog ──

@Composable
private fun CheckNowDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check for updates", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "This will check all your library anime for new episodes right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Progress will be shown on the Updates page",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Check now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ── Helpers ──

private fun formatInterval(hours: Long): String = when (hours) {
    6L -> "Every 6 hours"
    12L -> "Every 12 hours"
    24L -> "Every 24 hours"
    48L -> "Every 2 days"
    72L -> "Every 3 days"
    168L -> "Weekly"
    else -> "Every $hours hours"
}

private fun formatIntervalShort(hours: Long): String = when (hours) {
    6L -> "6h"
    12L -> "12h"
    24L -> "24h"
    48L -> "2d"
    72L -> "3d"
    168L -> "1w"
    else -> "${hours}h"
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
        )
    }
}
