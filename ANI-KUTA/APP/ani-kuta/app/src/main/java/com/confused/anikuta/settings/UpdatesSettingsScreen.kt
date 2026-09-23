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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.search.SettingsHighlightTarget
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll
import com.confused.anikuta.core.preferences.UpdateMode
import com.confused.anikuta.core.updates.UpdateEngine
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * D-193 v2: Combined Updates & Notifications settings screen.
 *
 * D-388 (round 25 — the updates-page rework): this is now THE "Updates" page
 * (its own row on the Settings home; the round-25 device report went looking
 * for it and found nothing). Changes:
 *  - the header says "Updates";
 *  - a "Check for updates now" action button (runs the engine immediately —
 *    the results notification + the history land as usual);
 *  - "Update check history" is a PROMINENT row right under the mode card
 *    (was buried at the very bottom);
 *  - the Notifications nav row moved into its own section at the end.
 */
@Composable
fun UpdatesSettingsScreen(
    // D-558: the back affordance arrives with the heading-as-back redesign
    // (previously this page had NO back button — only the system gesture).
    onBack: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenCategories: () -> Unit,
    // Task 64 (round 24): opens the content-update history page.
    onOpenCheckLog: () -> Unit = {},
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
    updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences = koinInject(),
    updateScheduler: com.confused.anikuta.core.updates.UpdateScheduler = koinInject(),
    updateEngine: UpdateEngine = koinInject(),
    // Task 80-b: resolves the MANUAL category selection → mainIds for the
    // check-now row (the same resolution the Updates tab uses).
    contentRepository: com.confused.anikuta.core.content.ContentRepository = koinInject(),
) {
    val mode by updatePreferences.mode.collectAsState()
    val intervalHours by updatePreferences.intervalHours.collectAsState()
    val checkSub by updatePreferences.checkSub.collectAsState()
    val checkDub by updatePreferences.checkDub.collectAsState()
    val checkDubCompleted by updatePreferences.checkDubCompleted.collectAsState()

    // D-388 (round 25): the check-now button's state + runner. Runs the engine
    // with the MANUAL trigger (labeled correctly in the history).
    // Task 80-b (honesty fix): the old doc comment claimed the row ran "on the
    // user's Manual-mode category filter — the same semantics as the Updates
    // tab" — but the code passed NO filter at all, so a Manual user tapping
    // check-now silently checked their WHOLE library. Now MANUAL really builds
    // the same filter the Updates tab builds (same prefs, same resolution);
    // AUTO passes no filter (all due); OFF disables the row entirely.
    val scope = rememberCoroutineScope()
    var checkNowRunning by remember { mutableStateOf(false) }
    var checkNowResult by remember { mutableStateOf<String?>(null) }

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
            // D-388 (round 25): the page is "Updates" — its own destination
            // from the Settings home (was the confusingly-labeled
            // "Notifications" entry pointing here).
            CollapsingHeader(
                title = "Updates",
                collapsed = collapsed,
                // D-558: the heading IS the back button (leading arrow + tappable title).
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // ── D-558: the search-landing scroll — MODE-AWARE because
                // several cards are conditional items (categories exist only
                // in MANUAL; interval + check-now exist in AUTO + MANUAL;
                // dub only when not OFF). Task 80-b: the check-now card is
                // now ALWAYS rendered (disabled in OFF) and the interval
                // card renders in AUTO too — the anchor map below matches.
                rememberSettingsAnchorScroll(
                    anchor = highlightAnchor,
                    anchorIndexFor = { anchor ->
                        // The REAL item order (mode-dependent): 0 mode card;
                        // 1 check-now (always present — disabled in OFF);
                        // 2 check-log; (AUTO + MANUAL) 3 interval;
                        // (MANUAL only) 4 categories; (mode != OFF) 5/4
                        // episode-type, 6/5 dub; 7/6 notifications label;
                        // 8/7 the card. (OFF: 3 label, 4 card.)
                        val isManual = mode == UpdateMode.MANUAL
                        val isAuto = mode == UpdateMode.AUTO
                        when (anchor) {
                            "updates_interval" -> if (mode == UpdateMode.OFF) null else 3
                            "updates_categories" -> if (isManual) 4 else null
                            "updates_dub" -> when {
                                isManual -> 6
                                isAuto -> 5
                                else -> null
                            }
                            "update_check_log" -> 2
                            "updates_notifications" -> when {
                                isManual -> 8
                                isAuto -> 7
                                else -> 4
                            }
                            else -> null
                        }
                    },
                    listState = lazyListState,
                )
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
                                        UpdateMode.AUTO -> "Follows each anime's airing schedule"
                                        UpdateMode.MANUAL -> "Only checks selected categories"
                                        UpdateMode.OFF -> "No background checking"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

                    // ── D-388 (round 25): the CHECK-NOW action + the PROMINENT
                    // history entry — directly under the mode card (the
                    // round-25 report could not find either). ──
                    // Task 80-b: the check-now row is ALWAYS rendered now —
                    // in OFF it stays visible but DISABLED with an honest
                    // "Updates are turned off" description (hiding it made
                    // the page look broken; a dead row lies less than a
                    // missing one).
                    item {
                        SeparateCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Task 80-b: OFF disables the row — the
                                    // periodic worker is cancelled in OFF, so
                                    // a "check now" tap would silently do the
                                    // opposite of what the page promises.
                                    .clickable(enabled = !checkNowRunning && mode != UpdateMode.OFF) {
                                        checkNowRunning = true
                                        checkNowResult = null
                                        scope.launch {
                                            runCatching {
                                                // Task 80-b: MANUAL builds the SAME
                                                // manual-mode category filter the
                                                // Updates tab builds (same prefs,
                                                // same resolution — mirrored from
                                                // UpdatesViewModel.checkForUpdates):
                                                // selected categories → mainIds,
                                                // empty selection = all due. AUTO
                                                // passes no filter (all due). This
                                                // is the parity the old comment
                                                // claimed and the code never had.
                                                val filterMainIds: Set<String>? = if (mode == UpdateMode.MANUAL) {
                                                    val selectedCats = updatePreferences.getSelectedCategories()
                                                    if (selectedCats.isEmpty()) {
                                                        null // no filter = check all due (user hasn't picked categories yet)
                                                    } else {
                                                        selectedCats.flatMap { catId ->
                                                            contentRepository.getMainIdsByCategory(catId.toLong())
                                                        }.toSet()
                                                    }
                                                } else {
                                                    null // AUTO — check all due anime
                                                }
                                                updateEngine.checkDueAnime(filterMainIds = filterMainIds, trigger = "manual")
                                            }.onSuccess { found ->
                                                checkNowResult =
                                                    if (found > 0) "$found new episode(s) found" else "No new episodes"
                                            }.onFailure { t ->
                                                checkNowResult = "Check failed: ${t.message}"
                                            }
                                            checkNowRunning = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = if (checkNowRunning || mode == UpdateMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (checkNowRunning) "Checking for updates…" else "Check for updates now",
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (checkNowRunning || mode == UpdateMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    // Local binding — `checkNowResult` is a remember-delegated
                                    // property, so the null-checked form can NOT smart cast
                                    // (Kotlin rule); the local can.
                                    val result = checkNowResult
                                    if (result != null) {
                                        Text(
                                            text = result,
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    } else {
                                        // Task 80-b: the OFF description is the
                                        // honest reason the row is dead.
                                        Text(
                                            text = if (mode == UpdateMode.OFF) "Updates are turned off"
                                            else "Runs an episode check right away",
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SettingsHighlightTarget(anchorId = "update_check_log", activeAnchor = highlightAnchor) {
                        SeparateCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenCheckLog)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update check history",
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Every check, results, next-check timer",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        }
                    }

                    // ── Checking settings (shown when mode != OFF) — each is a SEPARATE card ──
                    if (mode != UpdateMode.OFF) {
                        // Task 80-b: the interval row now renders in BOTH AUTO
                        // and MANUAL (hidden only in OFF). Why: the row used to
                        // be MANUAL-only, but MANUAL cancels the periodic
                        // worker (UpdateScheduler.reschedule only schedules in
                        // AUTO) — so the row was most visible exactly where it
                        // does the least, and INVISIBLE in AUTO, where the
                        // interval actually drives the periodic check. The
                        // description is now mode-honest: AUTO → it controls
                        // the background cadence; MANUAL → it feeds the
                        // next-check projection. Same cycle-through-values +
                        // reschedule() behavior as before.
                        item {
                            SettingsHighlightTarget(anchorId = "updates_interval", activeAnchor = highlightAnchor) {
                            SeparateCard {
                                NavRowContent(
                                    title = "Check interval",
                                    description = when (mode) {
                                        UpdateMode.AUTO -> "How often background checks run"
                                        else -> "Feeds the next-check projection"
                                    },
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
                        }
                        // Task 80-b: categories stay MANUAL-only (AUTO checks
                        // everything due — a category filter would be a lie
                        // there).
                        if (mode == UpdateMode.MANUAL) {
                            item {
                                SettingsHighlightTarget(anchorId = "updates_categories", activeAnchor = highlightAnchor) {
                                SeparateCard {
                                    NavRowContent(
                                        title = "Update categories",
                                        description = "Select which categories to check",
                                        onClick = onOpenCategories,
                                    )
                                }
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
                                        text = "What to notify about (both are checked)",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                            SettingsHighlightTarget(anchorId = "updates_dub", activeAnchor = highlightAnchor) {
                            SeparateCard {
                                SwitchRowContent(
                                    title = "Check dub on completed anime",
                                    description = "Keeps checking for dub after completion",
                                    checked = checkDubCompleted,
                                    onCheckedChange = { updatePreferences.setCheckDubCompleted(it) },
                                )
                            }
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
                        SettingsHighlightTarget(anchorId = "updates_notifications", activeAnchor = highlightAnchor) {
                        SeparateCard {
                            NavRowContent(
                                title = "Notifications",
                                description = "Enable, triggers, per-anime config, test",
                                onClick = onOpenNotifications,
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
                // D-532: the one-line description design language.
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                // D-532: the one-line description design language.
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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

// Task 80-b: formatInterval() removed — its only caller (the interval row's
// description) now renders a mode-honest sentence instead of restating the
// value the trailing badge already shows.

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
