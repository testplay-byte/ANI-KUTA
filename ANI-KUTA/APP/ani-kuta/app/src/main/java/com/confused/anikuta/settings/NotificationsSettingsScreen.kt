package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.MoreListRow
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.SettingsGroupCard
import com.confused.anikuta.core.notifications.TriggerState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Notifications settings screen (D-193 v2 — dedicated page).
 *
 * Reached from [UpdatesSettingsScreen] → "Notifications" nav row.
 *
 * Sections:
 * 1. **General** — global master kill switch. When off, everything below dims out.
 * 2. **New anime defaults** (shown when master is on) — 2-way On/Off triggers for
 *    schedule + watchable. These apply to every anime in the library UNLESS the
 *    library customization toggle is on.
 * 3. **Library customization** — a toggle. OFF (default) = the defaults above
 *    apply to all library anime. ON = each anime's details page gains a
 *    notifications section for per-anime overrides. The "Library" nav row
 *    (which opens the per-anime config list) is only shown when this is ON.
 * 4. **Test** — posts a demo notification (immediate) + a delayed one (60s).
 *
 * @param onBack Pops this screen.
 * @param onOpenLibrary Navigates to the per-anime library config page.
 */
@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: NotificationsSettingsViewModel = koinViewModel(),
    notificationManager: com.confused.anikuta.core.notifications.NotificationManager = koinInject(),
) {
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val libraryCustomEnabled by viewModel.libraryCustomizationEnabled.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBatteryDialog by remember { mutableStateOf(false) }

    fun onMasterToggled(enabled: Boolean) {
        viewModel.setMasterEnabled(enabled)
        if (enabled) {
            // Check if battery optimizations are disabled (app is exempt).
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            if (!isIgnoring) {
                showBatteryDialog = true
            }
        }
    }

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
                                        onCheckedChange = ::onMasterToggled,
                                    )
                                },
                            )
                        }
                    }

                    // ── Defaults + library customization + test (hidden when master is off) ──
                    // Wrapped in a Column so AnimatedVisibility's ColumnScope overload resolves.
                    item {
                        Column {
                            AnimatedVisibility(
                                visible = masterEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DefaultsSection(defaults, viewModel)

                                    // ── Library customization toggle ──
                                    SettingsGroupCard(label = "Library customization") {
                                        SettingRow(
                                            title = "Customize per anime",
                                            description = if (libraryCustomEnabled) {
                                                "Per-anime notification settings appear on each anime's details page"
                                            } else {
                                                "Default triggers above apply to all library anime"
                                            },
                                            trailing = {
                                                Switch(
                                                    checked = libraryCustomEnabled,
                                                    onCheckedChange = viewModel::setLibraryCustomizationEnabled,
                                                )
                                            },
                                        )
                                    }
                                    // The "Library" nav row (per-anime config list) is only shown
                                    // when customization is ON. When OFF, the defaults apply silently.
                                    // D-250: moved out of the card + swapped to shared MoreListRow
                                    // for icon-language consistency with the More page (bare 24dp
                                    // primary icon, no chip-box).
                                    AnimatedVisibility(
                                        visible = libraryCustomEnabled,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        MoreListRow(
                                            icon = Icons.Filled.LibraryBooks,
                                            title = "Library",
                                            subtitle = "Per-anime notification config",
                                            onClick = onOpenLibrary,
                                        )
                                    }

                                    // ── Test ──
                                    SettingsGroupCard(label = "Test") {
                                        SettingRow(
                                            title = "Send test notification",
                                            description = "Posts a demo + delayed notification (60s)",
                                            trailing = {
                                                Icon(
                                                    imageVector = Icons.Filled.Send,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
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

    // Battery optimization dialog (moved here from UpdatesSettingsScreen — the master
    // toggle now lives on this page, so the prompt belongs here too).
    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryDialog = false },
            onAllow = {
                showBatteryDialog = false
                try {
                    val batteryIntent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    ).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(batteryIntent)
                } catch (e: Exception) {
                    Logger.e("Anikuta:Settings", e) { "Failed to request battery optimization exemption" }
                }
            },
        )
    }
}

// ── Defaults section (2-way On/Off triggers) ─────────────────────────────────

@Composable
private fun DefaultsSection(
    defaults: NotificationsSettingsViewModel.Defaults,
    viewModel: NotificationsSettingsViewModel,
) {
    SettingsGroupCard(label = "New anime defaults") {
        // On schedule — 2-way On/Off Switch
        SettingRow(
            title = "On schedule",
            description = triggerDescription("schedule", defaults.onSchedule),
            showDivider = true,
            trailing = {
                Switch(
                    checked = defaults.onSchedule == TriggerState.ON,
                    onCheckedChange = { enabled ->
                        viewModel.setDefaultOnSchedule(if (enabled) TriggerState.ON else TriggerState.OFF)
                    },
                )
            },
        )
        // On watchable — 2-way On/Off Switch
        SettingRow(
            title = "On watchable",
            description = triggerDescription("watchable", defaults.onWatchable),
            showDivider = false,
            trailing = {
                Switch(
                    checked = defaults.onWatchable == TriggerState.ON,
                    onCheckedChange = { enabled ->
                        viewModel.setDefaultOnWatchable(if (enabled) TriggerState.ON else TriggerState.OFF)
                    },
                )
            },
        )
    }
}

private fun triggerDescription(trigger: String, state: TriggerState): String {
    val condition = when (trigger) {
        "schedule" -> "when the airing time is reached"
        "watchable" -> "when an episode is found on a source"
        else -> "for this trigger"
    }
    return when (state) {
        TriggerState.ON -> "Notify $condition"
        TriggerState.SILENT -> "Notify silently $condition"
        TriggerState.OFF -> "Don't notify (background still checks)"
    }
}
