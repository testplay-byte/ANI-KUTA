package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ROUND 102 (WS-E): TrackSheet — the tracking contract's configuration UI
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  The v1.1.58 device round's spec, implemented as a DRAFT + explicit commit:
 *
 *  • THE DRAFT — every picker edit lands in a LOCAL draft; NOTHING syncs per
 *    tap anymore. "If the user does not save it and just directly closes the
 *    menu, then those changes will not be saved" — a dismiss (swipe, outside
 *    tap, navigate) discards the draft with it.
 *
 *  • TWO BUTTONS at the bottom — LEFT "Remove from Tracking" (the quiet
 *    style): unlinks the tracking between AniList and the app (the remote
 *    entry is KEPT — the destructive delete lives behind the trash can).
 *    RIGHT "Save" (the theme-colored primary): the ONLY way changes persist;
 *    saving also flips the tracking opt-in ON (configuring + saving IS
 *    "track this anime").
 *
 *  • THE TRASH CAN replaces the old X close (top-right) — "Do you want to
 *    delete it from AniList?" — the REAL remote deletion (AniList's
 *    DeleteMediaListEntry + the local cache row + the opt-in off). Local
 *    watch progress and the rating are the app's own data and stay.
 *
 *  • ERRORS SURFACE — the sheet renders the ViewModel's one-shot error
 *    message inline (Save/Delete/Stop failures), per the user's "it should
 *    properly give the user the error message."
 *
 *  The wheel pickers, the three-cell top section, and the date rows keep the
 *  D-242 anatomy; dragHandle stays null (the app's no-grab-area rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    trackEntry: TrackEntry?,
    isLoggedIn: Boolean,
    totalEpisodes: Int?,
    seriesTitle: String,
    // ── ROUND 102 (WS-E): the contract's state + error surface ──
    isTracked: Boolean,
    error: String?,
    onSave: (TrackEntry) -> Unit,
    onRemoveTracking: () -> Unit,
    onDeleteFromAniList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.85f
    var expandedPicker by remember { mutableStateOf<ExpandedPicker?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── THE DRAFT: seeded from the (cached → remote-fetched) entry; re-seeds
    // when the open's background fetch lands. Every picker below edits THIS —
    // nothing persists until Save. ──
    var draft by remember(trackEntry) {
        mutableStateOf(
            trackEntry ?: TrackEntry(
                contentKey = "",
                trackerId = 0,
                status = TrackStatus.WATCHING,
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: the tracking-state chip + the TRASH CAN ──
            // (ROUND 102: the X is gone — the trash can behind a confirm is
            // the real delete-from-AniList; closing the sheet is the swipe /
            // outside tap, which discards the draft.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        seriesTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = RobotoFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // The two-state answer, live off the opt-in table.
                        text = if (isTracked) "Tracking now" else "Not tracking",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isTracked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        "Delete from AniList",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (!isLoggedIn) {
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // D-242-fix7: when totalEpisodes is null (rare — the details
            // state passes episode list size as fallback), use a large default
            // so the picker shows enough episodes.
            val effectiveTotal = totalEpisodes ?: (draft.progress.coerceAtLeast(100))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerCell(
                    label = "Status",
                    value = draft.status.displayLabel(),
                    isExpanded = expandedPicker == ExpandedPicker.STATUS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.STATUS) null else ExpandedPicker.STATUS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Progress",
                    value = if (totalEpisodes != null) "${draft.progress}/$totalEpisodes"
                    else "${draft.progress}",
                    isExpanded = expandedPicker == ExpandedPicker.PROGRESS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.PROGRESS) null else ExpandedPicker.PROGRESS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Score",
                    value = draft.score?.let { String.format("%.1f", it / 10.0) } ?: "—",
                    isExpanded = expandedPicker == ExpandedPicker.SCORE,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.SCORE) null else ExpandedPicker.SCORE
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Expanded picker — smooth animation (D-242), now editing the DRAFT.
            AnimatedVisibility(
                visible = expandedPicker != null,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    when (expandedPicker) {
                        ExpandedPicker.STATUS -> WheelPicker(
                            items = TrackStatus.entries.map { it.displayLabel() },
                            selectedIndex = TrackStatus.entries.indexOf(draft.status),
                            onItemClick = { index ->
                                draft = draft.copy(status = TrackStatus.entries[index])
                            },
                        )
                        ExpandedPicker.PROGRESS -> WheelPicker(
                            items = (0..effectiveTotal).map { if (it == 0) "Not started" else it.toString() },
                            selectedIndex = draft.progress.coerceIn(0, effectiveTotal),
                            onItemClick = { index ->
                                draft = draft.copy(progress = index)
                            },
                        )
                        ExpandedPicker.SCORE -> WheelPicker(
                            items = (0..100).map { if (it == 0) "—" else String.format("%.1f", it / 10.0) },
                            selectedIndex = (draft.score ?: 0).coerceIn(0, 100),
                            onItemClick = { index ->
                                draft = draft.copy(score = if (index == 0) null else index)
                            },
                        )
                        null -> {}
                    }
                }
            }

            // Separator
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Bottom section: dates (draft edits) — no remove button here
            // anymore (the two-button bar below owns the actions).
            DateRow("Started", draft.startedAt) {
                draft = draft.copy(startedAt = it)
            }
            Spacer(Modifier.height(8.dp))
            DateRow("Finished", draft.completedAt) {
                draft = draft.copy(completedAt = it)
            }
            Spacer(Modifier.height(16.dp))

            // ── ROUND 102 (WS-E): the ERROR surface — the ViewModel's one-shot
            // message, rendered inline above the buttons. ──
            if (!error.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        fontFamily = RobotoFamily,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── ROUND 102 (WS-E): the TWO-BUTTON bar — LEFT Remove from
            // Tracking (quiet), RIGHT Save (the theme-colored primary; the
            // ONLY way changes persist). ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showRemoveConfirm = true },
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Remove from Tracking",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSave(draft) },
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Save",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Remove from Tracking confirm — the unlink semantics, spelled out. ──
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveTracking()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Stop tracking this?") },
            text = {
                Text(
                    "This unlinks the tracking between AniList and ANI-KUTA — the app " +
                        "stops syncing this content. Your AniList entry, watch progress " +
                        "and rating are kept.",
                )
            },
        )
    }

    // ── The trash-can confirm — the REAL delete from AniList. ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteFromAniList()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Do you want to delete it from AniList?") },
            text = {
                Text(
                    "The entry is removed from your AniList list. Your watch progress " +
                        "and rating in ANI-KUTA are kept.",
                )
            },
        )
    }
}

private enum class ExpandedPicker { STATUS, PROGRESS, SCORE }

// ── Wheel-like picker ───────────────────────────────────────────────────────
// Items fade + shrink at edges; center item is highlighted.
// Uses LazyColumn with alpha based on distance from center.

@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)

    LaunchedEffect(clampedIndex) {
        listState.animateScrollToItem(clampedIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = index == clampedIndex
            val alphaAnimated by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                tween(200), "wheelAlpha",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(index) }
                    .padding(vertical = 10.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    item,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = alphaAnimated,
                    modifier = Modifier.alpha(if (isSelected) 1f else 0.5f),
                )
            }
        }
    }
}

// ── Picker cell ─────────────────────────────────────────────────────────────

@Composable
private fun PickerCell(
    label: String,
    value: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgAnimated by animateColorAsState(
        if (isExpanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tween(300), "cellBg",
    )
    val fgAnimated by animateColorAsState(
        if (isExpanded) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(300), "cellFg",
    )
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = bgAnimated,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fgAnimated.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = fgAnimated, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Not logged in ───────────────────────────────────────────────────────────

@Composable
private fun NotLoggedInState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not connected to AniList", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Go to Settings → Trackers to connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun TrackStatus.displayLabel(): String = when (this) {
    TrackStatus.WATCHING -> "Watching"
    TrackStatus.COMPLETED -> "Completed"
    TrackStatus.PAUSED -> "Paused"
    TrackStatus.DROPPED -> "Dropped"
    TrackStatus.PLAN_TO_WATCH -> "Plan to Watch"
    TrackStatus.REWATCHING -> "Rewatching"
}

// ── Date row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    label: String,
    dateMillis: Long?,
    onDateChange: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDatePicker = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    dateMillis?.let { dateFormatter.format(Date(it)) } ?: "Not set",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dateMillis != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dateMillis != null) {
                TextButton(onClick = { onDateChange(null) }) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateChange(it) }; showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Snackbar prompts
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun MarkPreviousEpisodesSnackbar(
    episodeNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(episodeNumber) {
        val steps = 100
        val stepDelay = 5000L / steps
        for (i in 1..steps) {
            progress = 1f - (i.toFloat() / steps)
            kotlinx.coroutines.delay(stepDelay)
        }
        onConfirm()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mark episodes 1–$episodeNumber as watched",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onDismiss() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        "Cancel",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("OK", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MarkSeriesWatchedSnackbar(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mark this series as watched?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onDismiss() },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        "Cancel",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("OK", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
