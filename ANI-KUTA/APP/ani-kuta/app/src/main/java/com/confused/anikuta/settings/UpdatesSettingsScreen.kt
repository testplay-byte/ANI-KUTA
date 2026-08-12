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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * D-193 v2: Combined Updates & Notifications settings screen.
 *
 * Changes per user feedback:
 * - No back button (device gesture handles back)
 * - No "Manual Check" section (removed entirely)
 * - Each setting is a SEPARATE card (not divider-separated within a shared card)
 * - Sub/Dub checking is a 3-way toggle (Sub / Dub / Both) — replaces 2 separate toggles
 * - Notifications section combines: enable toggle + defaults + library + test (one section)
 * - Notification triggers simplified: 2-way On/Off (no Silent), only on_schedule + on_watchable
 * - Audio preference removed from notification defaults (redundant with sub/dub checking)
 */
@Composable
fun UpdatesSettingsScreen(
    onOpenDefaults: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCategories: () -> Unit,
    updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences = koinInject(),
    notificationPreferences: com.confused.anikuta.core.preferences.NotificationPreferences = koinInject(),
    updateScheduler: com.confused.anikuta.core.updates.UpdateScheduler = koinInject(),
    notificationManager: com.confused.anikuta.core.notifications.NotificationManager = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()
    val notifEnabled by notificationPreferences.notificationsEnabledFlow().collectAsState(initial = true)

    val scope = rememberCoroutineScope()

    // Derive the 3-way audio check state from the two booleans.
    val audioCheckIndex = when {
        checkSub && checkDub -> 2 // Both
        checkDub -> 1 // Dub
        else -> 0 // Sub (default)
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

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
                        // Interval — only in MANUAL mode
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

                        // 3-way audio check toggle (Sub / Dub / Both) — replaces 2 separate toggles
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
                                        text = "Which audio variants to check for",
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

                    // ── Notifications section (combined: enable + defaults + library + test) ──
                    item {
                        SectionLabel("Notifications")
                    }
                    // Enable notifications — separate card
                    item {
                        SeparateCard {
                            SwitchRowContent(
                                title = "Enable notifications",
                                description = "Master switch for all notifications",
                                checked = notifEnabled,
                                onCheckedChange = { notificationPreferences.notificationsEnabled = it },
                            )
                        }
                    }
                    // Sub-items (shown when notifications enabled) — each separate card
                    if (notifEnabled) {
                        item {
                            SeparateCard {
                                NavRowContent(
                                    title = "New anime defaults",
                                    description = "Default notification triggers",
                                    onClick = onOpenDefaults,
                                )
                            }
                        }
                        item {
                            SeparateCard {
                                NavRowContent(
                                    title = "Library",
                                    description = "Per-anime notification configuration",
                                    onClick = onOpenLibrary,
                                )
                            }
                        }
                        item {
                            SeparateCard {
                                NavRowContent(
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
