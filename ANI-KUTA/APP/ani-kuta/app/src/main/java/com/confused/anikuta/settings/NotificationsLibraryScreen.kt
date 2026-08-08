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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.notifications.AudioPref
import com.confused.anikuta.core.notifications.NotificationConfig
import com.confused.anikuta.core.notifications.TriggerState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Dedicated per-anime notification config page (Phase NOTIF — UI).
 *
 * Reached from [NotificationsSettingsScreen] → "Library" nav row.
 *
 * Layout:
 * 1. Category filter chips (All + every library category).
 * 2. Filtered anime list — each row: cover + title + a Switch (enable/disable
 *    for that anime) + a chevron that opens the advanced-config sheet.
 * 3. Advanced-config sheet (per-anime): tri-state triggers (On/Silent/Off) +
 *    tri-state audio (Sub/Dub/Both), each with an adapting description.
 *
 * @param onBack Pops this screen.
 */
@Composable
fun NotificationsLibraryScreen(
    onBack: () -> Unit,
    viewModel: NotificationsLibraryViewModel = koinViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val openItem by viewModel.openItem.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Notifications · Library",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            // ── Category filter chips ──
            CategoryChips(
                categories = categories,
                selectedId = selectedCategoryId,
                onSelect = viewModel::selectCategory,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
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
                    } else if (items.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No anime in this category. Add anime to your library to configure per-anime notifications.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(items, key = { it.mainId }) { item ->
                            AnimeNotifRow(
                                item = item,
                                onToggle = { enabled -> viewModel.setAnimeEnabled(item.mainId, enabled) },
                                onOpenDetail = { viewModel.openAdvanced(item) },
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

        // ── Advanced-config bottom sheet ──
        val current = openItem
        if (current != null) {
            AnimeConfigSheet(
                item = current,
                onDismiss = viewModel::closeAdvanced,
                onUpdate = { transform -> viewModel.updateAnimeConfig(current.mainId, transform) },
            )
        }
    }
}

// ── Category chips ───────────────────────────────────────────────────────────

@Composable
private fun CategoryChips(
    categories: List<LibraryCategory>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            CategoryChip(label = "All", selected = selectedId == null) { onSelect(null) }
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(label = category.name, selected = selectedId == category.id) {
                onSelect(category.id)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "catChip_$label",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "catChipFg_$label",
    )
    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ── Library row ──────────────────────────────────────────────────────────────

@Composable
private fun AnimeNotifRow(
    item: AnimeNotifItem,
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
            Text(
                text = item.title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = item.isEnabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Advanced options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onOpenDetail),
            )
        }
    }
}

// ── Advanced-config bottom sheet (tri-state triggers + audio) ────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeConfigSheet(
    item: AnimeNotifItem,
    onDismiss: () -> Unit,
    onUpdate: (NotificationConfig.() -> NotificationConfig) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Working snapshot — defaults if no config exists yet.
    var config by androidx.compose.runtime.remember(item.mainId) {
        androidx.compose.runtime.mutableStateOf(item.config ?: NotificationConfig(mainId = item.mainId))
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
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Per-anime notification config",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Enable toggle
            ConfigToggle("Enable notifications for this anime", config.enabled) { v ->
                config = config.copy(enabled = v); onUpdate { copy(enabled = v) }
            }

            Spacer(Modifier.height(8.dp))

            // On schedule
            ConfigSegmented(
                title = "On schedule",
                description = triggerDescription("schedule", config.notifyOnSchedule),
                options = listOf("On", "Silent", "Off"),
                selected = config.notifyOnSchedule.ordinal,
            ) { idx ->
                val s = TRIGGERS[idx]
                config = config.copy(notifyOnSchedule = s)
                onUpdate { copy(notifyOnSchedule = s) }
            }
            // On watchable
            ConfigSegmented(
                title = "On watchable",
                description = triggerDescription("watchable", config.notifyOnWatchable),
                options = listOf("On", "Silent", "Off"),
                selected = config.notifyOnWatchable.ordinal,
            ) { idx ->
                val s = TRIGGERS[idx]
                config = config.copy(notifyOnWatchable = s)
                onUpdate { copy(notifyOnWatchable = s) }
            }
            // On immediate
            ConfigSegmented(
                title = "On immediate",
                description = triggerDescription("immediate", config.notifyOnImmediate),
                options = listOf("On", "Silent", "Off"),
                selected = config.notifyOnImmediate.ordinal,
            ) { idx ->
                val s = TRIGGERS[idx]
                config = config.copy(notifyOnImmediate = s)
                onUpdate { copy(notifyOnImmediate = s) }
            }
            // Audio
            ConfigSegmented(
                title = "Audio",
                description = audioDescription(config.audioPref),
                options = listOf("Sub", "Dub", "Both"),
                selected = config.audioPref.ordinal,
            ) { idx ->
                val a = AUDIO[idx]
                config = config.copy(audioPref = a)
                onUpdate { copy(audioPref = a) }
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConfigSegmented(
    title: String,
    description: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        SegmentedToggle(options = options, selectedIndex = selected, onSelect = onSelect)
    }
}

// TriggerState enum order must match the "On / Silent / Off" labels.
private val TRIGGERS = listOf(TriggerState.ON, TriggerState.SILENT, TriggerState.OFF)
// AudioPref enum order must match the "Sub / Dub / Both" labels.
private val AUDIO = listOf(AudioPref.SUB, AudioPref.DUB, AudioPref.BOTH)

private fun triggerDescription(trigger: String, state: TriggerState): String {
    val condition = when (trigger) {
        "schedule" -> "when the airing time is reached"
        "watchable" -> "when an episode is found on a source"
        "immediate" -> "as soon as the schedule says released"
        else -> "for this trigger"
    }
    return when (state) {
        TriggerState.ON -> "Notify $condition"
        TriggerState.SILENT -> "Notify silently $condition"
        TriggerState.OFF -> "Don't notify $condition"
    }
}

private fun audioDescription(pref: AudioPref): String = when (pref) {
    AudioPref.SUB -> "Notify for sub releases only"
    AudioPref.DUB -> "Notify for dub releases only"
    AudioPref.BOTH -> "Notify for sub and dub releases"
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
