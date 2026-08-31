package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.csplayer.CsEngineState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Task 54 (round 14) — the CS player control layers, in the EXACT visual
 * language of the aniyomi watch page's controls (MinimizedControls /
 * FullscreenControls): same gradients, same glass surfaces, same double-tap
 * zones, same canvas seekbar. Zero code shared with :core:player (the MPV
 * stack) — these are engine-state ([CsEngineState]) driven replicas so the
 * CloudStream player looks and feels identical without coupling the stacks.
 */

private val ANIM_DURATION = 200

/** Formats ms as m:ss / h:mm:ss (the controls' time format). */
internal fun formatCsTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The aniyomi "themed dark glass" — primary shifted 55% toward black, 62% alpha. */
@Composable
private fun csThemedDarkGlassColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val darkened = lerp(primary, Color.Black, 0.55f)
    return darkened.copy(alpha = 0.62f)
}

// ════════════════════════════════════════════════════════════════════════════
//  MINIMIZED controls (the 16:9 player box overlay — aniyomi MinimizedControls)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Minimized video player controls overlay — clean, minimal UI.
 *
 * Layout (when controls are visible):
 *  - Top-left: current time / total duration
 *  - Top-right: subtitle button + quality button
 *  - Center: play/pause icon with themed-dark glass background
 *  - Bottom: minimal seekbar (left, fills width) + maximize button (right)
 *
 * Gestures:
 *  - Single tap: toggle controls
 *  - Double-tap left third: skip -10s
 *  - Double-tap right third: skip +10s
 *  - Double-tap center third: toggle play/pause
 */
@Composable
internal fun CsMinimizedControls(
    state: CsEngineState,
    visible: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
) {
    var doubleTapAnim by remember { mutableStateOf<CsDoubleTapFeedback?>(null) }
    val animAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun animateFeedback(feedback: CsDoubleTapFeedback) {
        scope.launch {
            doubleTapAnim = feedback
            animAlpha.snapTo(0f)
            animAlpha.animateTo(1f, tween(150))
            animAlpha.animateTo(0f, tween(500))
            doubleTapAnim = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val w = size.width.toFloat()
                        when {
                            offset.x < w / 3 -> {
                                onSeekRelative(-10)
                                animateFeedback(CsDoubleTapFeedback.Rewind)
                            }
                            offset.x > w * 2f / 3f -> {
                                onSeekRelative(10)
                                animateFeedback(CsDoubleTapFeedback.Forward)
                            }
                            else -> {
                                onTogglePlay()
                                animateFeedback(
                                    if (state.isPlaying) CsDoubleTapFeedback.Pause else CsDoubleTapFeedback.Play,
                                )
                            }
                        }
                    },
                )
            },
    ) {
        // Gradient overlay for control readability (the aniyomi scrim stops).
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.25f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )
        }

        // Buffering spinner (glass disc — the aniyomi pattern).
        if (state.bufferState == com.confused.anikuta.core.csplayer.CsBufferState.BUFFERING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Double-tap feedback animation.
        doubleTapAnim?.let { feedback ->
            val isCenterAnim = feedback == CsDoubleTapFeedback.Pause || feedback == CsDoubleTapFeedback.Play
            val alignment = when (feedback) {
                CsDoubleTapFeedback.Rewind -> Alignment.CenterStart
                CsDoubleTapFeedback.Forward -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val sidePadding = if (isCenterAnim) 0.dp else 40.dp
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = alignment,
            ) {
                if (isCenterAnim) {
                    val icon = if (feedback == CsDoubleTapFeedback.Pause) Icons.Default.Pause else Icons.Default.PlayArrow
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = sidePadding),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.45f * animAlpha.value),
                            modifier = Modifier.size(48.dp),
                        ) {}
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = animAlpha.value),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else {
                    val label = if (feedback == CsDoubleTapFeedback.Rewind) "-10s" else "+10s"
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f * animAlpha.value),
                        modifier = Modifier.padding(horizontal = sidePadding),
                    ) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = animAlpha.value),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Controls (show/hide)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-left: time
                Text(
                    text = "${formatCsTime(state.positionMs)} / ${formatCsTime(state.durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 8.dp),
                )

                // Top-right: subtitle + quality
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CsTransparentIconButton(
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        onClick = onSubtitleClick,
                    )
                    CsTransparentIconButton(
                        icon = Icons.Default.HighQuality,
                        contentDescription = "Quality",
                        onClick = onQualityClick,
                    )
                }

                // Center: play/pause with themed-dark glass
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { onTogglePlay() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = csThemedDarkGlassColor(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                // Bottom: seekbar + fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CsMinimalSeekbar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        bufferedMs = state.bufferedMs,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(8.dp))
                    CsTransparentIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        onClick = onMaximize,
                    )
                }
            }
        }
    }
}

private enum class CsDoubleTapFeedback { Pause, Play, Rewind, Forward }

@Composable
private fun CsTransparentIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Minimal seekbar (the aniyomi MinimalSeekbar replica — ms-based)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A minimal, custom seekbar with a thin track and small thumb.
 *
 *  - 5dp track, 14dp thumb that appears during drag
 *  - Drag-to-seek with live position update
 *  - Floating time indicator above the thumb while dragging
 *  - Buffer-ahead segment shown as a lighter strip between progress and end
 */
@Composable
private fun CsMinimalSeekbar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableStateOf(0f) }
    val displayPosition = scrubPosition ?: positionMs.toFloat().coerceAtLeast(0f)
    val maxRange = durationMs.toFloat().coerceAtLeast(1f)
    val progress = (displayPosition / maxRange).coerceIn(0f, 1f)
    val isDragging = scrubPosition != null
    val bufferProgress = if (durationMs > 0 && bufferedMs > 0) {
        (bufferedMs.toFloat() / maxRange).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(maxRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (trackWidthPx > 0) {
                            val ratio = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            scrubPosition = ratio * maxRange
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (trackWidthPx > 0) {
                            val ratio = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                            scrubPosition = ratio * maxRange
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        scrubPosition?.let { onSeekTo(it.toLong()) }
                        scrubPosition = null
                    },
                    onDragCancel = {
                        scrubPosition = null
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Inactive track (background) — 5dp line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f)),
        )
        // Buffer-ahead segment
        if (bufferProgress > progress) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferProgress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            )
        }
        // Active track (progress)
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        // Thumb + floating time indicator — only while dragging
        if (isDragging && trackWidthPx > 0) {
            val thumbOffsetPx = trackWidthPx * progress
            val thumbSize = 14.dp
            val density = LocalDensity.current
            val thumbSizePx = with(density) { thumbSize.toPx() }
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbOffsetPx - thumbSizePx / 2).roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            val indicatorOffsetX = with(density) { 30.dp.toPx() }
            val indicatorOffsetY = with(density) { (-32).dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (thumbOffsetPx - indicatorOffsetX).roundToInt().coerceAtLeast(0),
                            indicatorOffsetY.roundToInt(),
                        )
                    }
                    .width(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = formatCsTime(displayPosition.toLong()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FULLSCREEN controls (the aniyomi FullscreenControls replica)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Fullscreen controls overlay.
 *
 * Layout:
 *  - Top-left: lock + anime title + episode/quality pills
 *  - Top-right: frosted glass row (subtitles, quality, audio, more)
 *  - Center: -10s skip + play/pause (themed-dark glass) + +10s skip
 *  - Bottom: Canvas-drawn seekbar + time + speed/skip/exit
 *
 * Lock mode: only an unlock button at top-left.
 * Auto-hide: 4s. Animations: slideIn/out (top/bottom rows), fade (center).
 */
@Composable
internal fun CsFullscreenControls(
    state: CsEngineState,
    visible: Boolean,
    locked: Boolean,
    animeTitle: String,
    episodeInfo: String,
    qualityInfo: String,
    currentSpeed: Float,
    onToggleControls: () -> Unit,
    onLockToggle: () -> Unit,
    onMinimize: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSkipForward: () -> Unit,
) {
    var isSeeking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (locked) {
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
            CsFsSmallButton(
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
                                onTap = { onToggleControls() },
                                onDoubleTap = { offset ->
                                    if (offset.x < size.width / 2) onSeekRelative(-10)
                                    else onSeekRelative(10)
                                },
                            )
                        }
                    },
            )

            // ── Top elements (slide in from top) ──
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(ANIM_DURATION)) + slideInVertically(tween(ANIM_DURATION), initialOffsetY = { -it }),
                exit = fadeOut(tween(ANIM_DURATION)) + slideOutVertically(tween(ANIM_DURATION), targetOffsetY = { -it }),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top-left: lock + title + pills
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CsFsSmallButton(
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
                                if (episodeInfo.isNotEmpty()) CsFsInfoPill(text = episodeInfo)
                                if (qualityInfo.isNotEmpty()) CsFsInfoPill(text = qualityInfo)
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
                            CsFsSmallButton(icon = Icons.Default.Subtitles, contentDescription = "Subtitles", onClick = onSubtitleClick)
                            CsFsSmallButton(icon = Icons.Default.HighQuality, contentDescription = "Quality", onClick = onQualityClick)
                            CsFsSmallButton(icon = Icons.Default.MusicNote, contentDescription = "Audio", onClick = onAudioClick)
                            CsFsSmallButton(icon = Icons.Default.MoreVert, contentDescription = "More", onClick = onMoreClick)
                        }
                    }
                }
            }

            // ── Center controls (fade only) ──
            AnimatedVisibility(
                visible = visible,
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
                        CsFsSkipButton(label = "-10s", onClick = { onSeekRelative(-10) })
                        Box(contentAlignment = Alignment.Center) {
                            if (state.bufferState == com.confused.anikuta.core.csplayer.CsBufferState.BUFFERING) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp),
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = csThemedDarkGlassColor(),
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clickable { onTogglePlay() },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                            }
                        }
                        CsFsSkipButton(label = "+10s", onClick = { onSeekRelative(10) })
                    }
                }
            }

            // ── Bottom elements (slide in from bottom) ──
            AnimatedVisibility(
                visible = visible,
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
                        CsFullscreenSeekbar(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            bufferedMs = state.bufferedMs,
                            onSeekTo = onSeekTo,
                            onSeekStart = { isSeeking = true },
                            onSeekEnd = { isSeeking = false },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CsFsTimeContainer(text = formatCsTime(state.positionMs), modifier = Modifier.padding(start = 8.dp))
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
                                        CsFsSpeedButton(speed = currentSpeed, onClick = onSpeedClick)
                                        CsFsSkipIconButton(onClick = onSkipForward)
                                    }
                                }
                                CsFsExitButton(onClick = onMinimize)
                                CsFsTimeContainer(text = formatCsTime(state.durationMs))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Canvas-drawn fullscreen seekbar (the aniyomi FullscreenSeekbarCustom replica)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsFullscreenSeekbar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var barWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val displayPosition = scrubPosition ?: positionMs.toFloat().coerceAtLeast(0f)
    val maxRange = durationMs.toFloat().coerceAtLeast(1f)
    val progress = (displayPosition / maxRange).coerceIn(0f, 1f)

    val trackColor = Color.White.copy(alpha = 0.2f)
    val progressColor = MaterialTheme.colorScheme.primary
    val bufferColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val barHeightDp = 6.dp
    val thumbSizeDp = 18.dp
    val bufferProgress = if (durationMs > 0 && bufferedMs > 0) {
        (bufferedMs.toFloat() / maxRange).coerceIn(0f, 1f)
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
                        onSeekTo((fraction * maxRange).toLong())
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
                        scrubPosition?.let { onSeekTo(it.toLong()) }
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
        Canvas(
            modifier = Modifier.fillMaxWidth().height(32.dp),
        ) {
            val barHeight = barHeightDp.toPx()
            val barY = (size.height - barHeight) / 2f
            val barWidth = size.width
            val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

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
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbSize, thumbSize),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }

        // Seek tooltip
        if (scrubPosition != null && barWidthPx > 0) {
            val tooltipText = formatCsTime(scrubPosition!!.toLong())
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
//  UI helper composables (the aniyomi FS helpers, replicated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsFsSmallButton(
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
private fun CsFsSkipButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = csThemedDarkGlassColor(),
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
private fun CsFsSkipIconButton(onClick: () -> Unit) {
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
private fun CsFsSpeedButton(speed: Float, onClick: () -> Unit) {
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
private fun CsFsTimeContainer(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun CsFsExitButton(onClick: () -> Unit) {
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
private fun CsFsInfoPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
