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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * D-242: TrackSheet — tap-to-expand with smooth animations.
 *
 * Layout:
 *  - Header: series name + close
 *  - Top section: Status | Progress | Score (3 cells, always visible)
 *    - Tap any → expands a vertical scrollable picker with smooth animation
 *  - Separator line
 *  - Bottom section: dates + remove
 *
 * Pickers use a "wheel" feel: items fade + shrink at the edges, center is selected.
 * Animations use tween(300) for smooth transitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    trackEntry: TrackEntry?,
    isLoggedIn: Boolean,
    totalEpisodes: Int?,
    seriesTitle: String,
    onStatusChange: (TrackStatus) -> Unit,
    onProgressChange: (Int) -> Unit,
    onScoreChange: (Int) -> Unit,
    onDatesChange: (startedAt: Long?, completedAt: Long?) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.85f
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var expandedPicker by remember { mutableStateOf<ExpandedPicker?>(null) }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    seriesTitle,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = RobotoFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (!isLoggedIn) {
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // Top section: always show all 3 cells
            val effectiveTotal = totalEpisodes ?: (trackEntry?.progress ?: 0).coerceAtLeast(24)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerCell(
                    label = "Status",
                    value = (trackEntry?.status ?: TrackStatus.WATCHING).displayLabel(),
                    isExpanded = expandedPicker == ExpandedPicker.STATUS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.STATUS) null else ExpandedPicker.STATUS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Progress",
                    value = if (totalEpisodes != null) "${trackEntry?.progress ?: 0}/$totalEpisodes"
                            else "${trackEntry?.progress ?: 0}",
                    isExpanded = expandedPicker == ExpandedPicker.PROGRESS,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.PROGRESS) null else ExpandedPicker.PROGRESS
                    },
                    modifier = Modifier.weight(1f),
                )
                PickerCell(
                    label = "Score",
                    value = trackEntry?.score?.let { String.format("%.1f", it / 10.0) } ?: "—",
                    isExpanded = expandedPicker == ExpandedPicker.SCORE,
                    onClick = {
                        expandedPicker = if (expandedPicker == ExpandedPicker.SCORE) null else ExpandedPicker.SCORE
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Expanded picker — smooth animation
            AnimatedVisibility(
                visible = expandedPicker != null,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    when (expandedPicker) {
                        ExpandedPicker.STATUS -> WheelPicker(
                            items = TrackStatus.entries.map { it.displayLabel() },
                            selectedIndex = TrackStatus.entries.indexOf(trackEntry?.status ?: TrackStatus.WATCHING),
                            onItemClick = { index ->
                                onStatusChange(TrackStatus.entries[index])
                            },
                        )
                        ExpandedPicker.PROGRESS -> WheelPicker(
                            items = (0..effectiveTotal).map { if (it == 0) "Not started" else it.toString() },
                            selectedIndex = (trackEntry?.progress ?: 0).coerceIn(0, effectiveTotal),
                            onItemClick = { index ->
                                onProgressChange(index)
                            },
                        )
                        ExpandedPicker.SCORE -> WheelPicker(
                            items = (0..100).map { if (it == 0) "—" else String.format("%.1f", it / 10.0) },
                            selectedIndex = (trackEntry?.score ?: 0).coerceIn(0, 100),
                            onItemClick = { index ->
                                onScoreChange(index)
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

            // Bottom section: dates + remove
            DateRow("Started", trackEntry?.startedAt) { onDatesChange(it, trackEntry?.completedAt) }
            Spacer(Modifier.height(8.dp))
            DateRow("Finished", trackEntry?.completedAt) { onDatesChange(trackEntry?.startedAt, it) }
            Spacer(Modifier.height(20.dp))

            // Remove button
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showRemoveConfirm = true },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove from Tracking", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            confirmButton = { TextButton(onClick = { showRemoveConfirm = false; onRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } },
            title = { Text("Remove from tracking?") },
            text = { Text("This will remove the series from your AniList tracking. Local watch progress is kept.") },
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
                    Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
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
