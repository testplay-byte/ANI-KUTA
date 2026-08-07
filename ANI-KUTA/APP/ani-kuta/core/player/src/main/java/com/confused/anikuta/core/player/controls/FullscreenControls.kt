package com.confused.anikuta.core.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.player.PlayerLoadingState
import com.confused.anikuta.core.player.PlayerStateHolder
import kotlin.math.roundToInt

private val ANIM_DURATION = 200

/**
 * Fullscreen controls overlay — ported from the old project.
 *
 * Layout:
 *  - Top-left: lock + anime title + episode/quality pills
 *  - Top-right: frosted glass row (server, subtitles, audio, quality, more)
 *  - Center: -10s skip + play/pause (themed-dark glass) + +10s skip
 *  - Bottom: Canvas-drawn seekbar + time + speed/rotate/skip/pip/exit
 *
 * Lock mode: only an unlock button at top-left.
 * Auto-hide: 4s. Animations: slideIn/out (top/bottom rows), fade (center).
 */
@Composable
fun FullscreenControls(
    stateHolder: PlayerStateHolder,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onMinimize: () -> Unit,
    onLockToggle: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit = {},
    onServerClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onPiPClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
    animeTitle: String = "",
    episodeInfo: String = "",
    qualityInfo: String = "",
    currentSpeed: Float = 1.0f,
) {
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val controlsLocked by stateHolder.controlsLocked.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val bufferAheadTime by stateHolder.bufferAheadTime.collectAsState()
    val loadingState by stateHolder.loadingState.collectAsState()
    val errorMessage by stateHolder.errorMessage.collectAsState()
    val isSwitching by stateHolder.isSwitching.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }
    if (isSeeking) stateHolder.updateControlsVisible(true)

    Box(modifier = modifier.fillMaxSize()) {
        if (controlsLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.18f to Color.Transparent,
                        ),
                    ),
            )
            FSSmallButton(
                icon = Icons.Default.Lock,
                contentDescription = "Unlock",
                onClick = onLockToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 4.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.12f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isSeeking) {
                        if (!isSeeking) {
                            detectTapGestures(
                                onTap = { stateHolder.updateControlsVisible(!controlsVisible) },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2) onSeekRelative(-10)
                                    else onSeekRelative(10)
                                },
                            )
                        }
                    },
            )

            // ── ERROR BANNER (non-intrusive, top-aligned) ──
            // Replaces the old full-screen PlayerErrorOverlay "dialog box".
            if (errorMessage != null) {
                PlayerErrorBanner(
                    errorMessage = errorMessage!!,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // ── Top elements (slide in from top) ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(ANIM_DURATION)) + slideInVertically(tween(ANIM_DURATION), initialOffsetY = { -it }),
                exit = fadeOut(tween(ANIM_DURATION)) + slideOutVertically(tween(ANIM_DURATION), targetOffsetY = { -it }),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top-left: lock + title
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FSSmallButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Lock",
                            onClick = onLockToggle,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            if (animeTitle.isNotEmpty()) {
                                Text(
                                    text = animeTitle,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                if (episodeInfo.isNotEmpty()) FSInfoPill(text = episodeInfo)
                                if (qualityInfo.isNotEmpty()) FSInfoPill(text = qualityInfo)
                            }
                        }
                    }

                    // Top-right: frosted glass button row
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 32.dp, top = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            FSSmallButton(icon = Icons.Default.Subtitles, contentDescription = "Subtitles", onClick = onSubtitleClick)
                            FSSmallButton(icon = Icons.Default.HighQuality, contentDescription = "Quality", onClick = onQualityClick)
                            FSSmallButton(icon = Icons.Default.MusicNote, contentDescription = "Audio", onClick = onAudioClick)
                            FSSmallButton(icon = Icons.Default.MoreVert, contentDescription = "More", onClick = onMoreClick)
                        }
                    }
                }
            }

            // ── Center controls (fade only) ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(ANIM_DURATION)),
                exit = fadeOut(tween(ANIM_DURATION)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FSSkipButton(label = "-10s", onClick = { onSeekRelative(-10) })
                        Box(contentAlignment = Alignment.Center) {
                            // Once buffered 1%, hide spinner even if isSwitching is true.
                            val showSpinner = buffering ||
                                (isSwitching && !stateHolder.bufferedEnough) ||
                                (loadingState == PlayerLoadingState.LOADING && duration == 0)
                            if (showSpinner) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp),
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = themedDarkGlassColor(),
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clickable { onTogglePlay() },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                            }
                        }
                        FSSkipButton(label = "+10s", onClick = { onSeekRelative(10) })
                    }
                }
            }

            // ── Bottom elements (slide in from bottom) ──
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(ANIM_DURATION)) + slideInVertically(tween(ANIM_DURATION), initialOffsetY = { it }),
                exit = fadeOut(tween(ANIM_DURATION)) + slideOutVertically(tween(ANIM_DURATION), targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        FullscreenSeekbarCustom(
                            position = position,
                            duration = duration,
                            bufferAheadTime = bufferAheadTime,
                            onSeekTo = onSeekTo,
                            onSeekStart = { isSeeking = true },
                            onSeekEnd = {
                                isSeeking = false
                                stateHolder.updateControlsVisible(true)
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FSTimeContainer(text = formatTime(position), modifier = Modifier.padding(start = 8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        FSSpeedButton(speed = currentSpeed, onClick = onSpeedClick)
                                        FSSmallButton(icon = Icons.Default.RotateRight, contentDescription = "Rotate", onClick = onRotateClick)
                                        FSSkipIconButton(onClick = onSkipForward)
                                    }
                                }
                                FSExitButton(onClick = onMinimize)
                                FSTimeContainer(text = formatTime(duration))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Canvas-drawn fullscreen seekbar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FullscreenSeekbarCustom(
    position: Int,
    duration: Int,
    bufferAheadTime: Int = 0,
    onSeekTo: (Int) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var barWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val displayPosition = scrubPosition ?: position.toFloat().coerceAtLeast(0f)
    val maxRange = duration.toFloat().coerceAtLeast(1f)
    val progress = (displayPosition / maxRange).coerceIn(0f, 1f)

    val trackColor = Color.White.copy(alpha = 0.2f)
    val progressColor = MaterialTheme.colorScheme.primary
    val bufferColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val barHeightDp = 6.dp
    val thumbSizeDp = 18.dp
    val bufferProgress = if (duration > 0 && bufferAheadTime > 0) {
        (bufferAheadTime.toFloat() / maxRange).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(maxRange) {
                detectTapGestures(
                    onTap = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((fraction * maxRange).toInt())
                    },
                )
            }
            .pointerInput(maxRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onSeekStart()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubPosition = fraction * maxRange
                    },
                    onDragEnd = {
                        scrubPosition?.let { onSeekTo(it.toInt()) }
                        scrubPosition = null
                        onSeekEnd()
                    },
                    onDragCancel = {
                        scrubPosition = null
                        onSeekEnd()
                    },
                    onDrag = { change, _ ->
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrubPosition = fraction * maxRange
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(32.dp),
        ) {
            val barHeight = barHeightDp.toPx()
            val barY = (size.height - barHeight) / 2f
            val barWidth = size.width
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())

            drawRoundRect(color = trackColor, topLeft = Offset(0f, barY), size = Size(barWidth, barHeight), cornerRadius = cornerRadius)
            // Buffer-ahead segment (drawn between progress and end)
            if (bufferProgress > progress) {
                drawRoundRect(
                    color = bufferColor,
                    topLeft = Offset(barWidth * progress, barY),
                    size = Size(barWidth * (bufferProgress - progress), barHeight),
                    cornerRadius = cornerRadius,
                )
            }
            drawRoundRect(color = progressColor, topLeft = Offset(0f, barY), size = Size(barWidth * progress, barHeight), cornerRadius = cornerRadius)

            val thumbSize = thumbSizeDp.toPx()
            val thumbX = (barWidth * progress - thumbSize / 2f).coerceAtLeast(0f)
            val thumbY = (size.height - thumbSize) / 2f
            drawRoundRect(color = progressColor, topLeft = Offset(thumbX, thumbY), size = Size(thumbSize, thumbSize), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        }

        // Seek tooltip
        if (scrubPosition != null && barWidthPx > 0) {
            val tooltipText = formatTime(scrubPosition!!.toInt())
            val thumbXpx = (barWidthPx * progress).coerceIn(0f, barWidthPx.toFloat())
            val tooltipOffsetPx = (thumbXpx - 30f).coerceIn(0f, (barWidthPx - 60).toFloat())
            val tooltipOffsetDp = with(density) { tooltipOffsetPx.toDp() }

            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .offset(x = tooltipOffsetDp, y = (-28).dp),
            ) {
                Text(
                    text = tooltipText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  UI helper composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FSSmallButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FSSkipButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = themedDarkGlassColor(),
        modifier = Modifier
            .size(width = 56.dp, height = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FSSkipIconButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next episode", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FSSpeedButton(speed: Float, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "${speed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FSTimeContainer(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun FSExitButton(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FSInfoPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
