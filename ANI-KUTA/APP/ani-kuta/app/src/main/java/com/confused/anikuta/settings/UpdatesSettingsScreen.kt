package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.UpdateMode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * D-193: Combined Updates & Notifications settings screen.
 *
 * Redesigned to match the project's design language:
 * - CollapsingHeader (shrinks on scroll, with back action)
 * - ScrollBlurOverlay (gradient blur at the top edge)
 * - Custom cards (NOT SettingsGroupCard — because SegmentedToggle needs full width)
 * - AnimatedVisibility (smooth show/hide of sections)
 *
 * The SegmentedToggle is rendered BELOW the title/description (NOT in a trailing slot)
 * to avoid the character-by-character rendering bug.
 *
 * Nav rows use Modifier.clickable — they're NOT inside SettingRow (which has no onClick).
 *
 * Check Now + Send Test Notification use rememberCoroutineScope (NOT GlobalScope).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    onBack: () -> Unit,
    onOpenDefaults: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCategories: () -> Unit,
    updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences = koinInject(),
    notificationPreferences: com.confused.anikuta.core.preferences.NotificationPreferences = koinInject(),
    updateScheduler: com.confused.anikuta.core.updates.UpdateScheduler = koinInject(),
    updateEngine: com.confused.anikuta.core.updates.UpdateEngine = koinInject(),
    notificationManager: com.confused.anikuta.core.notifications.NotificationManager = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()
    val notifEnabled by notificationPreferences.notificationsEnabledFlow().collectAsState(initial = true)

    val scope = rememberCoroutineScope()
    var showCheckNowDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Updates & Notifications",
                collapsed = collapsed,
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ── Updates section: master toggle ──
                    item {
                        SectionLabel("Updates")
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Update mode",
                                    fontFamily = RobotoFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = when (mode) {
                                        UpdateMode.AUTO -> "Checks anime with episodes yet to be released"
                                        UpdateMode.MANUAL -> "Only checks selected categories"
                                        UpdateMode.OFF -> "No background checking. Manual refresh still works."
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                                )
                                // SegmentedToggle BELOW the title/description — full width, NOT in trailing
                                SegmentedToggle(
                                    options = listOf("Auto", "Manual", "Off"),
                                    selectedIndex = UpdateMode.entries.indexOf(mode),
                                    onSelect = { idx ->
                                        updatePreferences.setMode(UpdateMode.entries[idx])
                                        updateScheduler.reschedule()
                                    },
                                )
                            }
                        }
                    }

                    // ── Checking settings (shown when mode != OFF) ──
                    item {
                        AnimatedVisibility(
                            visible = mode != UpdateMode.OFF,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                SectionLabel("Checking")
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        // Interval — only shown in MANUAL mode
                                        if (mode == UpdateMode.MANUAL) {
                                            ToggleSettingRow(
                                                title = "Check interval",
                                                description = formatInterval(intervalHours),
                                                trailingText = formatIntervalShort(intervalHours),
                                                onClick = {
                                                    val intervals = listOf(6L, 12L, 24L, 48L, 72L, 168L)
                                                    val currentIdx = intervals.indexOf(intervalHours)
                                                    val nextIdx = (currentIdx + 1) % intervals.size
                                                    updatePreferences.setIntervalHours(intervals[nextIdx])
                                                    updateScheduler.reschedule()
                                                },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                            // Per-category checklist
                                            NavSettingRow(
                                                title = "Update categories",
                                                description = "Select which categories to check",
                                                onClick = onOpenCategories,
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                        }
                                        // Sub/Dub toggles
                                        SwitchSettingRow(
                                            title = "Check for sub episodes",
                                            description = "Check for new subbed episodes",
                                            checked = checkSub,
                                            onCheckedChange = { updatePreferences.setCheckSub(it) },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                        SwitchSettingRow(
                                            title = "Check for dub episodes",
                                            description = "Check for new dubbed episodes",
                                            checked = checkDub,
                                            onCheckedChange = { updatePreferences.setCheckDub(it) },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                        SwitchSettingRow(
                                            title = "Check dub on completed anime",
                                            description = "Continue checking for dub after completion",
                                            checked = checkDubCompleted,
                                            onCheckedChange = { updatePreferences.setCheckDubCompleted(it) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Check Now button ──
                    item {
                        SectionLabel("Manual Check")
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { showCheckNowDialog = true },
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
                                    fontFamily = RobotoFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // ── Notifications section ──
                    item {
                        SectionLabel("Notifications")
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                SwitchSettingRow(
                                    title = "Enable notifications",
                                    description = "Master switch for all notifications",
                                    checked = notifEnabled,
                                    onCheckedChange = { notificationPreferences.notificationsEnabled = it },
                                )
                            }
                        }
                    }

                    // ── Configuration (shown when notifications enabled) ──
                    item {
                        AnimatedVisibility(
                            visible = notifEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                SectionLabel("Configuration")
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        NavSettingRow(
                                            title = "New anime defaults",
                                            description = "Default triggers + audio preferences",
                                            onClick = onOpenDefaults,
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                        NavSettingRow(
                                            title = "Library",
                                            description = "Per-anime notification configuration",
                                            onClick = onOpenLibrary,
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                        NavSettingRow(
                                            title = "Send test notification",
                                            description = "Posts a demo + delayed notification",
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        notificationManager.postTestNotification()
                                                        Logger.i("Anikuta:Settings") { "Test notification sent" }
                                                    } catch (e: Exception) {
                                                        Logger.e("Anikuta:Settings", e) { "Test notification failed" }
                                                    }
                                                }
                                            },
                                        )
                                    }
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

    // Check Now dialog
    if (showCheckNowDialog) {
        AlertDialog(
            onDismissRequest = { showCheckNowDialog = false },
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
                TextButton(onClick = {
                    showCheckNowDialog = false
                    isChecking = true
                    scope.launch {
                        try {
                            updateEngine.checkDueAnime()
                            Logger.i("Anikuta:Settings") { "Manual check complete" }
                        } catch (e: Exception) {
                            Logger.e("Anikuta:Settings", e) { "Manual check failed" }
                        }
                        isChecking = false
                    }
                }) {
                    Text("Check now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckNowDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Reusable row components ──

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavSettingRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    trailingText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(trailingText, fontWeight = FontWeight.Bold)
        }
    }
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
