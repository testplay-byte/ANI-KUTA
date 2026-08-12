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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.notifications.AudioPref
import com.confused.anikuta.core.notifications.TriggerState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Notifications settings screen (Phase NOTIF — UI).
 *
 * Reached from [SettingsScreen] → "Notifications" nav row.
 *
 * Sections:
 * 1. **General** — global master kill switch.
 * 2. **New anime defaults** (hidden when master is off) — tri-state triggers
 *    (On / Silent / Off) for schedule / watchable / immediate, plus a tri-state
 *    audio pref (Sub / Dub / Both). Each row's description adapts to the selection.
 * 3. **Library** — a nav row to the dedicated [NotificationsLibraryScreen] for
 *    per-anime configuration.
 *
 * @param onBack Pops this screen.
 * @param onOpenLibrary Navigates to the per-anime library config page.
 */
@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: NotificationsSettingsViewModel = koinViewModel(),
) {
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

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

                    // ── New anime defaults (smoothly hidden when master is off) ──
                    // Wrapped in a Column so AnimatedVisibility's ColumnScope overload
                    // resolves (LazyItemScope doesn't provide ColumnScope).
                    item {
                        Column {
                            AnimatedVisibility(
                                visible = masterEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                DefaultsSection(defaults, viewModel)
                            }
                        }
                    }

                    // ── Library nav row ──
                    item {
                        Text(
                            text = "Library",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                        LibraryNavRow(onOpenLibrary = onOpenLibrary)
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

// ── Defaults section (tri-state toggles + adapting descriptions) ──────────────

@Composable
private fun DefaultsSection(
    defaults: NotificationsSettingsViewModel.Defaults,
    viewModel: NotificationsSettingsViewModel,
) {
    SettingsGroupCard(label = "New anime defaults") {
        // On schedule
        SettingRow(
            title = "On schedule",
            description = triggerDescription("schedule", defaults.onSchedule),
            showDivider = false,
            trailing = {},
        )
        SegmentedToggle(
            options = listOf("On", "Silent", "Off"),
            selectedIndex = TRIGGERS.indexOf(defaults.onSchedule),
            onSelect = { idx -> viewModel.setDefaultOnSchedule(TRIGGERS[idx]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // On watchable
        SettingRow(
            title = "On watchable",
            description = triggerDescription("watchable", defaults.onWatchable),
            showDivider = false,
            trailing = {},
        )
        SegmentedToggle(
            options = listOf("On", "Silent", "Off"),
            selectedIndex = TRIGGERS.indexOf(defaults.onWatchable),
            onSelect = { idx -> viewModel.setDefaultOnWatchable(TRIGGERS[idx]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // On immediate
        SettingRow(
            title = "On immediate",
            description = triggerDescription("immediate", defaults.onImmediate),
            showDivider = false,
            trailing = {},
        )
        SegmentedToggle(
            options = listOf("On", "Silent", "Off"),
            selectedIndex = TRIGGERS.indexOf(defaults.onImmediate),
            onSelect = { idx -> viewModel.setDefaultOnImmediate(TRIGGERS[idx]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // Audio (Sub / Dub / Both)
        SettingRow(
            title = "Audio",
            description = audioDescription(defaults.audioPref),
            showDivider = false,
            trailing = {},
        )
        SegmentedToggle(
            options = listOf("Sub", "Dub", "Both"),
            selectedIndex = AUDIO.indexOf(defaults.audioPref),
            onSelect = { idx -> viewModel.setDefaultAudioPref(AUDIO[idx]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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

// ── Library nav row ──────────────────────────────────────────────────────────

@Composable
private fun LibraryNavRow(onOpenLibrary: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenLibrary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Per-anime notification config + advanced options",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
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
