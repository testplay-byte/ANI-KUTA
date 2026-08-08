package com.confused.anikuta.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.notifications.NotificationConfig
import org.koin.compose.viewmodel.koinViewModel

/**
 * Notifications settings screen (Phase NOTIF — UI).
 *
 * Reached from [SettingsScreen] → "Notifications" nav row.
 *
 * Three sections:
 * 1. **General** — global master kill switch.
 * 2. **New anime defaults** — the trigger + audio prefs seeded into per-anime config
 *    when a user enables notifications for a new anime.
 * 3. **Library** — per-anime notification config. Each row has a master toggle for
 *    that anime; tapping the row opens a detail sheet with per-trigger + sub/dub
 *    toggles.
 *
 * @param onBack Pops this screen.
 */
@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsSettingsViewModel = koinViewModel(),
) {
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // The anime whose detail sheet is open (null = closed).
    var sheetItem by remember { mutableStateOf<AnimeNotifItem?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Notifications",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── General: master toggle ──
                    item {
                        SettingsGroupCard(label = "General") {
                            SettingRow(
                                title = "Enable notifications",
                                description = "Master switch for all episode-release notifications",
                                trailing = {
                                    Switch(
                                        checked = masterEnabled,
                                        onCheckedChange = viewModel::setMasterEnabled,
                                    )
                                },
                            )
                        }
                    }

                    // ── New anime defaults ──
                    item {
                        SettingsGroupCard(label = "New anime defaults") {
                            SettingRow(
                                title = "On schedule",
                                description = "Notify when the airing time is reached",
                                trailing = {
                                    Switch(
                                        checked = defaults.onSchedule,
                                        onCheckedChange = viewModel::setDefaultOnSchedule,
                                    )
                                },
                            )
                            SettingRow(
                                title = "On watchable",
                                description = "Notify when an episode is found on a source",
                                trailing = {
                                    Switch(
                                        checked = defaults.onWatchable,
                                        onCheckedChange = viewModel::setDefaultOnWatchable,
                                    )
                                },
                            )
                            SettingRow(
                                title = "On immediate",
                                description = "Notify as soon as the schedule says released",
                                trailing = {
                                    Switch(
                                        checked = defaults.onImmediate,
                                        onCheckedChange = viewModel::setDefaultOnImmediate,
                                    )
                                },
                            )
                            SettingRow(
                                title = "Sub",
                                description = "Notify for subbed releases",
                                trailing = {
                                    Switch(
                                        checked = defaults.sub,
                                        onCheckedChange = viewModel::setDefaultSub,
                                    )
                                },
                            )
                            SettingRow(
                                title = "Dub",
                                description = "Notify for dubbed releases",
                                showDivider = false,
                                trailing = {
                                    Switch(
                                        checked = defaults.dub,
                                        onCheckedChange = viewModel::setDefaultDub,
                                    )
                                },
                            )
                        }
                    }

                    // ── Library: per-anime config ──
                    item {
                        Text(
                            text = "Library",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                        Text(
                            text = "Enable notifications per anime. Tap a row to customize triggers.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        )
                    }

                    if (loading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Loading library…",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (libraryItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No library anime yet. Add anime to your library to configure per-anime notifications.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(libraryItems, key = { it.mainId }) { item ->
                            AnimeNotifRow(
                                item = item,
                                masterEnabled = masterEnabled,
                                onToggle = { enabled ->
                                    viewModel.setAnimeEnabled(item.mainId, enabled)
                                },
                                onOpenDetail = { sheetItem = item },
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

        // ── Per-anime config bottom sheet ──
        val current = sheetItem
        if (current != null) {
            AnimeConfigSheet(
                item = current,
                onDismiss = { sheetItem = null },
                onUpdate = { transform ->
                    // Persist to the DB + refresh the ViewModel's library list.
                    viewModel.updateAnimeConfig(current.mainId, transform)
                },
            )
        }
    }
}

// ── Library row ──────────────────────────────────────────────────────────────

@Composable
private fun AnimeNotifRow(
    item: AnimeNotifItem,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenDetail),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover (40×56dp)
            if (item.coverUrl != null) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 40.dp, height = 56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Title
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (masterEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Per-anime switch
            Switch(
                checked = item.isEnabled,
                enabled = masterEnabled,
                onCheckedChange = onToggle,
            )
            Spacer(Modifier.width(4.dp))
            // Chevron → opens detail sheet
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Customize",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onOpenDetail),
            )
        }
    }
}

// ── Per-anime config bottom sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeConfigSheet(
    item: AnimeNotifItem,
    onDismiss: () -> Unit,
    onUpdate: (NotificationConfig.() -> NotificationConfig) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The working config — defaults if none exists yet.
    var config by remember(item.mainId) {
        mutableStateOf(item.config ?: NotificationConfig(mainId = item.mainId))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ConfigToggle("Enable notifications", config.enabled) { v ->
                config = config.copy(enabled = v); onUpdate { copy(enabled = v) }
            }
            ConfigToggle("On schedule", config.notifyOnSchedule) { v ->
                config = config.copy(notifyOnSchedule = v); onUpdate { copy(notifyOnSchedule = v) }
            }
            ConfigToggle("On watchable", config.notifyOnWatchable) { v ->
                config = config.copy(notifyOnWatchable = v); onUpdate { copy(notifyOnWatchable = v) }
            }
            ConfigToggle("On immediate", config.notifyOnImmediate) { v ->
                config = config.copy(notifyOnImmediate = v); onUpdate { copy(notifyOnImmediate = v) }
            }
            ConfigToggle("Sub", config.notifySub) { v ->
                config = config.copy(notifySub = v); onUpdate { copy(notifySub = v) }
            }
            ConfigToggle("Dub", config.notifyDub) { v ->
                config = config.copy(notifyDub = v); onUpdate { copy(notifyDub = v) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
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
