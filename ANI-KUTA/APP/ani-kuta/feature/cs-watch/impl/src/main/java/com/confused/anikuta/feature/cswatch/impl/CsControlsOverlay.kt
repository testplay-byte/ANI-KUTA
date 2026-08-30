package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsEngineState

/**
 * The CS player control layer (task 52 / Phase D) — Compose over the bare
 * PlayerView, mirroring the aniyomi watch page's glass aesthetic (dark
 * surface, primary-accent actions, animated reveal, auto-hide). Zero code
 * shared with :feature:watch:impl — visual parity via the same design tokens.
 */

/** Formats ms as m:ss / h:mm:ss. */
internal fun formatCsTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun csGlassColor(): Color =
    Color.Black.copy(alpha = 0.45f)

@Composable
internal fun CsControlsOverlay(
    engineState: CsEngineState,
    visible: Boolean,
    animeTitle: String,
    episodeTitle: String,
    episodeNumber: Float,
    providerName: String,
    hasNext: Boolean,
    hasPrev: Boolean,
    playbackSpeed: Float,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenLinks: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onNextEpisode: () -> Unit,
    onPrevEpisode: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    // Local drag state so the slider doesn't fight the 200ms position ticker.
    var dragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0f) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onToggleControls() },
    ) {
        // ── Center: buffering spinner / play-pause ───────────────────────────
        if (engineState.bufferState == com.confused.anikuta.core.csplayer.CsBufferState.BUFFERING) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .background(csGlassColor(), CircleShape)
                    .padding(8.dp),
            )
        } else {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPrevEpisode,
                        enabled = hasPrev,
                        modifier = Modifier
                            .size(46.dp)
                            .background(csGlassColor(), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous episode",
                            tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f),
                        )
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .background(csGlassColor(), CircleShape),
                    ) {
                        Icon(
                            if (engineState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (engineState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(
                        onClick = onNextEpisode,
                        enabled = hasNext,
                        modifier = Modifier
                            .size(46.dp)
                            .background(csGlassColor(), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next episode",
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        animeTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val epLine = buildString {
                        append("EP ")
                        append(if (episodeNumber % 1f == 0f) episodeNumber.toInt() else episodeNumber)
                        if (episodeTitle.isNotBlank()) append(" · $episodeTitle")
                    }
                    Text(
                        epLine,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    providerName,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(end = 14.dp, start = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        // ── Bottom bar: seek + actions ────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatCsTime(if (dragging) dragPositionMs.toLong() else engineState.positionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.Start,
                    )
                    Slider(
                        value = if (dragging) dragPositionMs else engineState.positionMs.toFloat(),
                        onValueChange = {
                            dragging = true
                            dragPositionMs = it
                        },
                        onValueChangeFinished = {
                            onSeekTo(dragPositionMs.toLong())
                            dragging = false
                        },
                        valueRange = 0f..(engineState.durationMs.coerceAtLeast(1L)).toFloat(),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(26.dp),
                    )
                    Text(
                        formatCsTime(engineState.durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.End,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Speed (compact dropdown)
                    Box {
                        Text(
                            "${playbackSpeed}x",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { speedMenuOpen = true }
                                .background(csGlassColor(), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${speed}x",
                                            fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        onSpeedChange(speed)
                                        speedMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    CsControlAction(label = "Subtitles", onClick = onOpenSubtitles)
                    CsControlAction(label = "Streams", onClick = onOpenLinks)
                    CsControlAction(label = "Episodes", onClick = onOpenEpisodes)
                }
            }
        }
    }
}

@Composable
private fun CsControlAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(csGlassColor(), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
