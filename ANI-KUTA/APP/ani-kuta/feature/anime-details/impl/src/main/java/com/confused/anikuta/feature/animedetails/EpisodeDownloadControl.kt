package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The state-driven download control for a single episode row on the details page.
 *
 * D.6 + D.8: Renders 7 states from [EpisodeDownloadState] with smooth
 * [AnimatedContent] transitions between them (D.8 polish — fade in/out 200ms).
 *
 * **States:**
 *  - [EpisodeDownloadState.NotDownloaded] → download icon button.
 *  - [EpisodeDownloadState.Resolving] → spinner (immediate feedback).
 *  - [EpisodeDownloadState.Queued] → spinner + cancel button.
 *  - [EpisodeDownloadState.Downloading] → progress bar + pause/cancel buttons.
 *  - [EpisodeDownloadState.Retrying] → spinner + "Retrying…" label + cancel.
 *  - [EpisodeDownloadState.Paused] → resume + cancel buttons.
 *  - [EpisodeDownloadState.Error] → error icon + retry + cancel buttons.
 *  - [EpisodeDownloadState.Downloaded] → checkmark + delete button.
 *
 * CORE_RULES §22: smooth AnimatedContent transitions (fade, 200ms).
 * CORE_RULES §23: every state is interactive (no dead states).
 *
 * @param state The current download state for this episode.
 * @param onDownload Called when the user taps the download button (NotDownloaded).
 * @param onPause Called when the user taps pause (Downloading).
 * @param onResume Called when the user taps resume (Paused).
 * @param onCancel Called when the user taps cancel (any non-terminal state).
 * @param onRetry Called when the user taps retry (Error).
 * @param onDelete Called when the user taps delete (Downloaded).
 * @param modifier Outer modifier.
 */
@Composable
fun EpisodeDownloadControl(
    state: EpisodeDownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onPlayDownloaded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // D.8: AnimatedContent for smooth state transitions (fade, 200ms).
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(animationSpec = tween(200)) togetherWith
                fadeOut(animationSpec = tween(200)))
        },
        label = "episodeDownloadControl",
        modifier = modifier,
    ) { s ->
        when (s) {
            is EpisodeDownloadState.NotDownloaded -> DownloadButton(onClick = onDownload)

            is EpisodeDownloadState.Resolving -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )

            is EpisodeDownloadState.Queued -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                SmallIconButton(Icons.Filled.Close, "Cancel", onClick = onCancel)
            }

            is EpisodeDownloadState.Downloading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // D-210 FIX: widened the bar from 60dp → 100dp (the user said it was
                // too narrow). Added maxLines=1 + softWrap=false on the percentage
                // Text to prevent line-breaking that was hiding the "%".
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (s.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${s.progress}%",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                    Spacer(Modifier.width(4.dp))
                    SmallIconButton(Icons.Filled.Pause, "Pause", onClick = onPause)
                    SmallIconButton(Icons.Filled.Close, "Cancel", onClick = onCancel)
                }
            }

            is EpisodeDownloadState.Retrying -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Retrying",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(4.dp))
                SmallIconButton(Icons.Filled.Close, "Cancel", onClick = onCancel)
            }

            is EpisodeDownloadState.Paused -> Row(verticalAlignment = Alignment.CenterVertically) {
                SmallIconButton(Icons.Filled.PlayArrow, "Resume", onClick = onResume, tint = MaterialTheme.colorScheme.primary)
                SmallIconButton(Icons.Filled.Close, "Cancel", onClick = onCancel)
            }

            is EpisodeDownloadState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                SmallIconButton(Icons.Filled.Refresh, "Retry", onClick = onRetry, tint = MaterialTheme.colorScheme.primary)
                SmallIconButton(Icons.Filled.Close, "Cancel", onClick = onCancel)
            }

            is EpisodeDownloadState.Downloaded -> {
                // D.FIX: Single checkmark icon in a circle. Tapping shows a
                // dropdown menu with "Play" + "Delete" options.
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .clickable { showMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Downloaded — tap for options",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play", fontFamily = RobotoFamily) },
                            onClick = {
                                showMenu = false
                                onPlayDownloaded()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun DownloadButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = "Download",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
