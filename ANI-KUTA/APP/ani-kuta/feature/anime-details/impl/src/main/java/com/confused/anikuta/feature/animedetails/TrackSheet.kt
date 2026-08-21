package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * D-242: The TrackSheet — a bottom sheet for managing AniList tracking.
 *
 * Redesigned for a modern, minimal, beautiful aesthetic:
 *  - Clean header with gradient accent
 *  - Card-based sections (Status, Progress, Score, Dates)
 *  - FlowRow for status chips (wraps naturally)
 *  - Smooth animations on selection
 *  - Remove button at the bottom with confirmation dialog
 *
 * Design language: follows the app's design (lime accent, warm darks,
 * translucent surfaces, rounded corners, per §22).
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
        dragHandle = {
            // Minimal drag handle — a small pill
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            TrackSheetHeader(onDismiss = onDismiss)
            Spacer(Modifier.height(16.dp))

            if (!isLoggedIn) {
                NotLoggedInState()
                Spacer(Modifier.height(24.dp))
                return@ModalBottomSheet
            }

            // ── Status section ──
            SectionCard(title = "Status") {
                StatusFlowRow(
                    currentStatus = trackEntry?.status,
                    onStatusChange = onStatusChange,
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Progress section ──
            SectionCard(title = "Progress") {
                ProgressRow(
                    currentProgress = trackEntry?.progress ?: 0,
                    totalEpisodes = totalEpisodes,
                    onProgressChange = onProgressChange,
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Score section ──
            SectionCard(title = "Score") {
                ScoreRow(
                    currentScore = trackEntry?.score,
                    onScoreChange = onScoreChange,
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── Dates section ──
            SectionCard(title = "Watch Dates") {
                DateRow(
                    label = "Started",
                    dateMillis = trackEntry?.startedAt,
                    onDateChange = { newStart ->
                        onDatesChange(newStart, trackEntry?.completedAt)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                DateRow(
                    label = "Finished",
                    dateMillis = trackEntry?.completedAt,
                    onDateChange = { newFinish ->
                        onDatesChange(trackEntry?.startedAt, newFinish)
                    },
                )
            }
            Spacer(Modifier.height(20.dp))

            // ── Remove button ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showRemoveConfirm = true },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
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

    // ── Remove confirmation dialog ──
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

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun TrackSheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // AniList logo — a clean circle with gradient
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF02A9FF), // AniList blue
                                Color(0xFF0E6BCA),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "A",
                    color = Color.White,
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
            "Go to Settings → Trackers to connect your AniList account " +
                "and start syncing your watch progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Section card wrapper ───────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

// ── Status flow row ────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StatusFlowRow(
    currentStatus: TrackStatus?,
    onStatusChange: (TrackStatus) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrackStatus.values().forEach { status ->
            StatusChip(
                label = status.displayLabel(),
                isSelected = currentStatus == status,
                onClick = { onStatusChange(status) },
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val targetColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surface
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200),
        label = "chipColor",
    )
    val targetTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(200),
        label = "chipTextColor",
    )
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = animatedColor,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = animatedTextColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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

// ── Progress row ───────────────────────────────────────────────────────────

@Composable
private fun ProgressRow(
    currentProgress: Int,
    totalEpisodes: Int?,
    onProgressChange: (Int) -> Unit,
) {
    var textProgress by remember(currentProgress) {
        mutableStateOf(currentProgress.toString())
    }
    val max = totalEpisodes ?: 9999

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Minus button
        CircularButton(
            text = "−",
            onClick = {
                val newProgress = (currentProgress - 1).coerceAtLeast(0)
                onProgressChange(newProgress)
            },
        )
        // Number field
        OutlinedTextField(
            value = textProgress,
            onValueChange = { input ->
                textProgress = input.filter { it.isDigit() }
                textProgress.toIntOrNull()?.let { parsed ->
                    // D-242-fix: cap at totalEpisodes (can't exceed)
                    onProgressChange(parsed.coerceAtMost(max))
                }
            },
            modifier = Modifier.width(80.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        // Plus button
        CircularButton(
            text = "+",
            onClick = {
                // D-242-fix: cap at totalEpisodes (can't exceed)
                val newProgress = (currentProgress + 1).coerceAtMost(max)
                onProgressChange(newProgress)
            },
        )
        // Total
        if (totalEpisodes != null) {
            Text(
                "/ $totalEpisodes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CircularButton(text: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(100),
        label = "btnScale",
    )
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Score row ──────────────────────────────────────────────────────────────

@Composable
private fun ScoreRow(
    currentScore: Int?,
    onScoreChange: (Int) -> Unit,
) {
    val currentStars = currentScore?.let { (it / 10).coerceIn(0, 10) } ?: 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 1..10) {
                Icon(
                    imageVector = if (i <= currentStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Rate $i stars",
                    tint = if (i <= currentStars) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            if (i == currentStars) onScoreChange(0) else onScoreChange(i * 10)
                        },
                )
            }
        }
        if (currentScore != null && currentScore > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                String.format("%.1f / 10", currentScore / 10.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Date row ───────────────────────────────────────────────────────────────

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
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dateMillis?.let { dateFormatter.format(Date(it)) } ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dateMillis != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

// ════════════════════════════════════════════════════════════════════════════
// D-242: Snackbar-style prompts (bottom-anchored, NOT fullscreen dialogs)
// ════════════════════════════════════════════════════════════════════════════

/**
 * D-242: A bottom-anchored snackbar-style prompt for "mark all previous episodes
 * as watched".
 *
 * Shows at the bottom of the screen with:
 *  - A message ("Mark episodes 1–N as watched?")
 *  - A 5-second timeout progress bar
 *  - Two action buttons: "Cancel" and "Mark"
 *
 * If the user doesn't respond in 5 seconds, auto-confirms (calls onConfirm).
 * This replaces the old fullscreen AlertDialog — the user wanted a toast-like
 * notification at the bottom with two options.
 *
 * Render this as an overlay (in a Box with `Modifier.align(Alignment.BottomCenter)`).
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
        for (i in 0 until steps) {
            progress = 1f - (i.toFloat() / steps)
            kotlinx.coroutines.delay(stepDelay)
        }
        // Timeout — auto-confirm.
        onConfirm()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                "Mark episodes 1–$episodeNumber as watched?",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "You marked episode $episodeNumber but previous episodes aren't watched.",
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            // Timeout progress bar
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Auto-confirms in ${(progress * 5).toInt() + 1}s",
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Mark", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * D-242: A bottom-anchored snackbar-style prompt for "mark series as watched".
 *
 * Shows at the bottom of the screen with a message + two action buttons.
 * No timeout (waits for user action).
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
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                "Mark this series as watched?",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "You've watched all episodes. Mark it as completed on AniList?",
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Not now", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Mark", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
