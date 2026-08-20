package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackeranilist.AniListTracker
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * D-242: The TrackSheet — a bottom sheet for managing AniList tracking.
 *
 * Shows the current track entry (status, progress, score, start/finish dates)
 * + lets the user edit each field. Changes are synced to AniList immediately
 * (optimistic local update + background sync).
 *
 * If the user is not logged in to AniList, shows a "Connect AniList" prompt
 * instead of the edit fields.
 *
 * Design: follows the app's design language (lime accent, warm darks,
 * translucent cards, rounded corners, smooth animations per §22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    trackEntry: TrackEntry?,
    isLoggedIn: Boolean,
    totalEpisodes: Int?,
    onStatusChange: (TrackStatus) -> Unit,
    onProgressChange: (Int) -> Unit,
    onScoreChange: (Int) -> Unit,
    onDatesChange: (startedAt: Long?, completedAt: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.85f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            TrackSheetHeader(onDismiss = onDismiss)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (!isLoggedIn) {
                // ── Not logged in ──
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // ── Status selector ──
            StatusSelector(
                currentStatus = trackEntry?.status,
                onStatusChange = onStatusChange,
            )
            Spacer(Modifier.height(20.dp))

            // ── Progress stepper ──
            ProgressStepper(
                currentProgress = trackEntry?.progress ?: 0,
                totalEpisodes = totalEpisodes,
                onProgressChange = onProgressChange,
            )
            Spacer(Modifier.height(20.dp))

            // ── Score (star rating) ──
            ScoreSelector(
                currentScore = trackEntry?.score,
                onScoreChange = onScoreChange,
            )
            Spacer(Modifier.height(20.dp))

            // ── Start / Finish dates ──
            DateSelectorRow(
                label = "Start date",
                dateMillis = trackEntry?.startedAt,
                onDateChange = { newStart ->
                    onDatesChange(newStart, trackEntry?.completedAt)
                },
            )
            Spacer(Modifier.height(12.dp))
            DateSelectorRow(
                label = "Finish date",
                dateMillis = trackEntry?.completedAt,
                onDateChange = { newFinish ->
                    onDatesChange(trackEntry?.startedAt, newFinish)
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun TrackSheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // AniList-style icon (simple circle with "A")
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "A",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "AniList Tracking",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = RobotoFamily,
                )
                Text(
                    "Sync your progress to AniList",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Not logged in state ────────────────────────────────────────────────────

@Composable
private fun NotLoggedInState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "You're not logged in to AniList.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Go to Settings → Trackers to connect your AniList account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Status selector ────────────────────────────────────────────────────────

@Composable
private fun StatusSelector(
    currentStatus: TrackStatus?,
    onStatusChange: (TrackStatus) -> Unit,
) {
    val statuses = TrackStatus.values()
    Column {
        Text(
            "Status",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Wrap status chips in a Row that wraps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            statuses.take(3).forEach { status ->
                StatusChip(
                    label = status.displayLabel(),
                    isSelected = currentStatus == status,
                    onClick = { onStatusChange(status) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            statuses.drop(3).forEach { status ->
                StatusChip(
                    label = status.displayLabel(),
                    isSelected = currentStatus == status,
                    onClick = { onStatusChange(status) },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "chipBg",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
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

// ── Progress stepper ───────────────────────────────────────────────────────

@Composable
private fun ProgressStepper(
    currentProgress: Int,
    totalEpisodes: Int?,
    onProgressChange: (Int) -> Unit,
) {
    var textProgress by remember(currentProgress) {
        mutableStateOf(currentProgress.toString())
    }
    Column {
        Text(
            "Episodes Watched",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Minus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val newProgress = (currentProgress - 1).coerceAtLeast(0)
                        onProgressChange(newProgress)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", style = MaterialTheme.typography.headlineSmall)
            }
            // Number field
            OutlinedTextField(
                value = textProgress,
                onValueChange = { input ->
                    textProgress = input.filter { it.isDigit() }
                    textProgress.toIntOrNull()?.let { onProgressChange(it) }
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            // Plus button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val max = totalEpisodes ?: 9999
                        val newProgress = (currentProgress + 1).coerceAtMost(max)
                        onProgressChange(newProgress)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
            // Total
            if (totalEpisodes != null) {
                Text(
                    "/ $totalEpisodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Score selector (10-star bar, 0-100 backend) ────────────────────────────

@Composable
private fun ScoreSelector(
    currentScore: Int?,
    onScoreChange: (Int) -> Unit,
) {
    val currentStars = currentScore?.let { (it / 10).coerceIn(0, 10) } ?: 0
    Column {
        Text(
            "Score",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 1..10) {
                Icon(
                    imageVector = if (i <= currentStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Rate $i stars",
                    tint = if (i <= currentStars) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            if (i == currentStars) onScoreChange(0) else onScoreChange(i * 10)
                        },
                )
            }
            Spacer(Modifier.width(8.dp))
            if (currentScore != null && currentScore > 0) {
                Text(
                    String.format("%.1f", currentScore / 10.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Date selector ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectorRow(
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateMillis?.let { dateFormatter.format(Date(it)) } ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (dateMillis != null) {
                TextButton(onClick = { onDateChange(null) }) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChange(it) }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Prompts ────────────────────────────────────────────────────────────────

/**
 * D-242: The "mark all previous episodes as watched" prompt with a 5-second
 * timeout bar.
 *
 * If the user doesn't respond in 5 seconds, auto-confirms (calls onConfirm).
 * The timeout bar animates from full to empty over 5 seconds.
 */
@Composable
fun MarkPreviousEpisodesPrompt(
    episodeNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(episodeNumber) {
        val durationMs = 5000L
        val steps = 100
        val stepDelay = durationMs / steps
        for (i in 0 until steps) {
            progress = 1f - (i.toFloat() / steps)
            delay(stepDelay)
        }
        // Timeout — auto-confirm.
        onConfirm()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Mark watched") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Mark episodes 1–$episodeNumber as watched?") },
        text = {
            Column {
                Text(
                    "You marked episode $episodeNumber as watched, but episodes 1–${episodeNumber - 1} " +
                        "are not marked. Mark them all as watched?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Auto-confirms in ${(progress * 5).toInt()}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * D-242: The "mark series as watched" prompt. Shown when all episodes are
 * watched + the series is FINISHED.
 */
@Composable
fun MarkSeriesWatchedPrompt(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Mark as watched") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
        title = { Text("Mark this series as watched?") },
        text = {
            Text(
                "You've watched all episodes of this series. Mark it as completed on AniList?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}
