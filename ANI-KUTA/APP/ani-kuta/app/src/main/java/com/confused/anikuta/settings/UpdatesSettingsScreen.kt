package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.UpdateMode
import org.koin.compose.koinInject

/**
 * D-193 v2: Combined Updates & Notifications settings screen.
 *
 * Changes per user feedback:
 * - No back button (device gesture handles back)
 * - No "Manual Check" section (removed entirely)
 * - Each setting is a SEPARATE card (not divider-separated within a shared card)
 * - Sub/Dub checking is a 3-way toggle (Sub / Dub / Both) — gates NOTIFICATIONS only
 *   (the engine always checks both sub + dub; the toggle is honored by NotificationManager)
 * - Notifications is a single nav row to the dedicated NotificationsSettingsScreen
 *   (not an inline toggle). The dedicated page has the master enable + triggers +
 *   library customization + test button.
 */
@Composable
fun UpdatesSettingsScreen(
    onOpenNotifications: () -> Unit,
    onOpenCategories: () -> Unit,
    // Task 64 (round 24): opens the content-update history page.
    onOpenCheckLog: () -> Unit = {},
    updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences = koinInject(),
    updateScheduler: com.confused.anikuta.core.updates.UpdateScheduler = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // Derive the 3-way audio check state from the two booleans (Sub/Dub/Both toggle).
    val audioCheckIndex = when {
        checkSub && checkDub -> 2 // Both
        checkDub -> 1 // Dub
        else -> 0 // Sub (default)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // No back button — device gesture handles back.
            CollapsingHeader(
                title = "Updates & Notifications",
                collapsed = collapsed,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ── Updates section: master toggle (separate card) ──
                    item {
                        SectionLabel("Updates")
                        SeparateCard {
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
                                        UpdateMode.AUTO -> "Smart checking based on each anime's release schedule"
                                        UpdateMode.MANUAL -> "Only checks selected categories"
                                        UpdateMode.OFF -> "No background checking"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                                )
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

                    // ── Checking settings (shown when mode != OFF) — each is a SEPARATE card ──
                    if (mode != UpdateMode.OFF) {
                        // Interval + categories — only in MANUAL mode
                        if (mode == UpdateMode.MANUAL) {
                            item {
                                SeparateCard {
                                    NavRowContent(
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
                                }
                            }
                            item {
                                SeparateCard {
                                    NavRowContent(
                                        title = "Update categories",
                                        description = "Select which categories to check",
                                        onClick = onOpenCategories,
                                    )
                                }
                            }
                        }

                        // D-193 v2 fix: description clarifies the toggle gates NOTIFICATIONS,
                        // not checking. The engine always checks both sub + dub.
                        item {
                            SeparateCard {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Episode type",
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Which audio variants to notify you about (both are always checked)",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                                    )
                                    SegmentedToggle(
                                        options = listOf("Sub", "Dub", "Both"),
                                        selectedIndex = audioCheckIndex,
                                        onSelect = { idx ->
                                            when (idx) {
                                                0 -> { updatePreferences.setCheckSub(true); updatePreferences.setCheckDub(false) }
                                                1 -> { updatePreferences.setCheckSub(false); updatePreferences.setCheckDub(true) }
                                                2 -> { updatePreferences.setCheckSub(true); updatePreferences.setCheckDub(true) }
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Check dub on completed anime — separate card
                        item {
                            SeparateCard {
                                SwitchRowContent(
                                    title = "Check dub on completed anime",
                                    description = "Continue checking for dub after completion",
                                    checked = checkDubCompleted,
                                    onCheckedChange = { updatePreferences.setCheckDubCompleted(it) },
                                )
                            }
                        }
                    }

                    // ── Notifications section (D-193 v2: a single nav row to the dedicated page) ──
                    // The user wanted notifications to be a completely separate page, not an
                    // inline toggle. This row opens NotificationsSettingsScreen which has the
                    // master enable switch + triggers + library customization + test button.
                    item {
                        SectionLabel("Notifications")
                    }
                    item {
                        SeparateCard {
                            NavRowContent(
                                title = "Notifications",
                                description = "Enable, triggers, per-anime config, test",
                                onClick = onOpenNotifications,
                            )
                        }
                    }

                    // ── Task 64 (round 24): the content-update HISTORY ──
                    // "keep track of when the app actually checked for updates… a
                    // dedicated option to check out the updates log, like a content
                    // update history" — every check session lands there (when it
                    // ran, what it checked, outcomes, the engine's next actions).
                    item {
                        SectionLabel("History")
                    }
                    item {
                        SeparateCard {
                            NavRowContent(
                                title = "Update check history",
                                description = "Every episode check — what was checked, what was found, what happens next",
                                onClick = onOpenCheckLog,
                            )
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
}

// ── Reusable components ──

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

/** A separate rounded card — each setting gets its own. No shared cards with dividers. */
@Composable
private fun SeparateCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun SwitchRowContent(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
private fun NavRowContent(
    title: String,
    description: String,
    onClick: () -> Unit,
    trailingText: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        if (trailingText != null) {
            Text(
                text = trailingText,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

// D-193 Phase 4: Battery optimization dialog.
// Internal so NotificationsSettingsScreen (same package) can reuse it — the dialog
// is shown when the user enables the notifications master toggle.
@Composable
internal fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery optimization", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "This permission is required to get accurate notifications. " +
                    "Without it, notifications may not fire when the app is closed. " +
                    "Tap 'Allow' to disable battery optimization for ANI-KUTA.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onAllow) {
                Text("Allow", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
    )
}
