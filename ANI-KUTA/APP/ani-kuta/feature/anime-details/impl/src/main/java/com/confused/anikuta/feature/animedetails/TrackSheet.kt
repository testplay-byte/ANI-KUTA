package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * D-242: The TrackSheet — a clean, minimal bottom sheet for AniList tracking.
 *
 * Design principles (per user feedback):
 *  - Shows the SERIES NAME as the header (not "AniList Tracking")
 *  - No drag handle (cleaner look)
 *  - Status is a dropdown (tap to see options, not all chips at once)
 *  - Progress is a scrollable LazyRow (select episode number by scrolling)
 *  - Score is a scrollable LazyRow (select rating by scrolling)
 *  - Dates are simple rows with date pickers
 *  - Remove button at the bottom
 *
 * Follows the app's design language (theme-colored, dark/light mode aware).
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // D-242-fix: no drag handle — cleaner look per user request
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header: series name + close button ──
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
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (!isLoggedIn) {
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // ── Status (dropdown) ──
            StatusDropdown(
                currentStatus = trackEntry?.status,
                onStatusChange = onStatusChange,
            )
            Spacer(Modifier.height(16.dp))

            // ── Progress (scrollable) ──
            if (totalEpisodes != null) {
                ProgressScrollable(
                    currentProgress = trackEntry?.progress ?: 0,
                    totalEpisodes = totalEpisodes,
                    onProgressChange = onProgressChange,
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Score (scrollable) ──
            ScoreScrollable(
                currentScore = trackEntry?.score,
                onScoreChange = onScoreChange,
            )
            Spacer(Modifier.height(16.dp))

            // ── Dates ──
            DateRow(
                label = "Started",
                dateMillis = trackEntry?.startedAt,
                onDateChange = { newStart ->
                    onDatesChange(newStart, trackEntry?.completedAt)
                },
            )
            Spacer(Modifier.height(8.dp))
            DateRow(
                label = "Finished",
                dateMillis = trackEntry?.completedAt,
                onDateChange = { newFinish ->
                    onDatesChange(trackEntry?.startedAt, newFinish)
                },
            )
            Spacer(Modifier.height(20.dp))

            // ── Remove button ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showRemoveConfirm = true },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Remove from Tracking",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemove()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Remove from tracking?") },
            text = {
                Text(
                    "This will remove the series completely from your AniList tracking. " +
                        "Your local watch progress will be kept.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

// ── Not logged in ───────────────────────────────────────────────────────────

@Composable
private fun NotLoggedInState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Not connected to AniList",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Go to Settings → Trackers to connect your AniList account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Status dropdown ─────────────────────────────────────────────────────────

@Composable
private fun StatusDropdown(
    currentStatus: TrackStatus?,
    onStatusChange: (TrackStatus) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val status = currentStatus ?: TrackStatus.WATCHING

    Text(
        "Status",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status.displayLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TrackStatus.values().forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.displayLabel()) },
                    onClick = {
                        onStatusChange(s)
                        expanded = false
                    },
                )
            }
        }
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

// ── Progress scrollable ─────────────────────────────────────────────────────

@Composable
private fun ProgressScrollable(
    currentProgress: Int,
    totalEpisodes: Int,
    onProgressChange: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // D-242-fix: clamp display progress to [0, totalEpisodes] so stale tracker
    // data (progress > total) doesn't break the chip highlighting.
    val displayProgress = currentProgress.coerceIn(0, totalEpisodes)

    // D-242-fix: auto-scroll to the current progress chip on first composition.
    androidx.compose.runtime.LaunchedEffect(displayProgress, totalEpisodes) {
        if (displayProgress > 0) {
            listState.scrollToItem(displayProgress.coerceAtLeast(0))
        }
    }

    Text(
        "Progress: $displayProgress / $totalEpisodes",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    // Scrollable row of episode numbers 0..totalEpisodes
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        items(totalEpisodes + 1) { epNum -> // 0..totalEpisodes (0 = "not started")
            val isSelected = epNum == displayProgress
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(200),
                label = "progChip",
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "progChipText",
            )
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onProgressChange(epNum) },
                color = animatedColor,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (epNum == 0) "—" else epNum.toString(),
                        color = animatedTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Score scrollable ────────────────────────────────────────────────────────

@Composable
private fun ScoreScrollable(
    currentScore: Int?,
    onScoreChange: (Int) -> Unit,
) {
    val currentStars = currentScore?.let { (it / 10).coerceIn(0, 10) } ?: 0

    Text(
        if (currentScore != null && currentScore > 0)
            "Score: ${String.format("%.1f", currentScore / 10.0)} / 10"
        else "Score",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        items(11) { i -> // 0..10 stars
            val isSelected = i <= currentStars && i > 0
            val animatedColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                animationSpec = tween(150),
                label = "starColor",
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        onScoreChange(if (i == currentStars) 0 else i * 10)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Rate $i",
                    tint = animatedColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
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
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDatePicker = true },
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
                TextButton(onClick = { onDateChange(null) }) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onDateChange(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// D-242: Minimal snackbar-style prompts (bottom-anchored, theme-colored)
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-242: A minimal, theme-colored snackbar for "mark all previous episodes".
 *
 * Layout:
 *  - Message on the left
 *  - Timer below the message (small text + progress bar)
 *  - Two buttons on the RIGHT side: Cancel (X icon) + Okay (text)
 *
 * Auto-confirms after 5 seconds.
 */
@Composable
fun MarkPreviousEpisodesSnackbar(
    episodeNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableStateOf(1f) }

    androidx.compose.runtime.LaunchedEffect(episodeNumber) {
        val durationMs = 5000L
        val steps = 100
        val stepDelay = durationMs / steps
        for (i in 1..steps) {
            progress = 1f - (i.toFloat() / steps)
            kotlinx.coroutines.delay(stepDelay)
        }
        onConfirm()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: message + timer
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mark episodes 1–$episodeNumber as watched?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                // Timer progress bar
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
            }
            Spacer(Modifier.width(12.dp))
            // Right: Cancel (X) + Okay buttons
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    "Okay",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * D-242: A minimal, theme-colored snackbar for "mark series as watched".
 * No timeout — waits for user action.
 */
@Composable
fun MarkSeriesWatchedSnackbar(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mark this series as watched?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "All episodes watched. Mark as completed on AniList?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onConfirm() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    "Okay",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
