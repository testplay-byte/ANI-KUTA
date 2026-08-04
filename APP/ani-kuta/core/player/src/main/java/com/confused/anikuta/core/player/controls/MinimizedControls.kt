package com.confused.anikuta.core.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.player.PlayerLoadingState
import com.confused.anikuta.core.player.PlayerStateHolder
import kotlinx.coroutines.launch

/**
 * Minimized video player controls overlay — clean, minimal UI.
 *
 * Ported from the old project. Layout (when controls are visible):
 *  - Top-left: current time / total duration
 *  - Top-right: subtitle button + quality button
 *  - Center: transparent play/pause icon with themed-dark glass background
 *  - Bottom: minimal seekbar (left, fills width) + maximize button (right)
 *
 * Gestures:
 *  - Single tap: toggle controls
 *  - Double-tap left third: skip -10s
 *  - Double-tap right third: skip +10s
 *  - Double-tap center third: toggle play/pause
 */
@Composable
fun MinimizedControls(
    stateHolder: PlayerStateHolder,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onMaximize: () -> Unit,
    onQualityClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val controlsVisible by stateHolder.controlsVisible.collectAsState()
    val isPlaying by stateHolder.isPlaying.collectAsState()
    val position by stateHolder.position.collectAsState()
    val duration by stateHolder.duration.collectAsState()
    val buffering by stateHolder.buffering.collectAsState()
    val bufferAheadTime by stateHolder.bufferAheadTime.collectAsState()
    val loadingState by stateHolder.loadingState.collectAsState()
    val errorMessage by stateHolder.errorMessage.collectAsState()
    val isSwitching by stateHolder.isSwitching.collectAsState()

    var doubleTapAnim by remember { mutableStateOf<DoubleTapFeedback?>(null) }
    val animAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { stateHolder.updateControlsVisible(!controlsVisible) },
                    onDoubleTap = { offset ->
                        val w = size.width.toFloat()
                        val zone = when {
                            offset.x < w / 3 -> DoubleTapZone.LEFT
                            offset.x > w * 2f / 3f -> DoubleTapZone.RIGHT
                            else -> DoubleTapZone.CENTER
                        }
                        when (zone) {
                            DoubleTapZone.LEFT -> {
                                onSeekRelative(-10)
                                scope.launch {
                                    doubleTapAnim = DoubleTapFeedback.Rewind
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                            DoubleTapZone.RIGHT -> {
                                onSeekRelative(10)
                                scope.launch {
                                    doubleTapAnim = DoubleTapFeedback.Forward
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                            DoubleTapZone.CENTER -> {
                                onTogglePlay()
                                scope.launch {
                                    doubleTapAnim = if (isPlaying) DoubleTapFeedback.Pause else DoubleTapFeedback.Play
                                    animAlpha.snapTo(0f)
                                    animAlpha.animateTo(1f, tween(150))
                                    animAlpha.animateTo(0f, tween(500))
                                    doubleTapAnim = null
                                }
                            }
                        }
                    },
                )
            },
    ) {
        // Gradient overlay for control readability
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
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

        // ── ERROR BANNER (non-intrusive, top-aligned) ──
        // Replaces the old full-screen PlayerErrorOverlay "dialog box".
        // Shows a small dismissable banner at the top of the player with the
        // error message + retry button. The video surface stays visible.
        // Auto-retry (in WatchScreen) handles most errors before this appears.
        if (errorMessage != null) {
            PlayerErrorBanner(
                errorMessage = errorMessage!!,
                onRetry = onRetry,
                onDismiss = onDismissError,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            return@Box  // Skip normal controls — video isn't playing anyway
        }

        // Loading indicator — shows when:
        //  - buffering == true (network stall / paused-for-cache), OR
        //  - isSwitching == true (quality/server switch in progress — video is
        //    being replaced, show spinner), OR
        //  - loadingState == LOADING AND duration == 0 (initial load, video
        //    hasn't started yet)
        // CRITICAL: Do NOT show the spinner just because !isPlaying — that
        // fires when the user manually pauses.
        if (buffering || isSwitching || (loadingState == PlayerLoadingState.LOADING && duration == 0)) {
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

        // Double-tap feedback animation
        doubleTapAnim?.let { feedback ->
            val isCenterAnim = feedback == DoubleTapFeedback.Pause || feedback == DoubleTapFeedback.Play
            val alignment = if (isCenterAnim) Alignment.Center else {
                when (feedback) {
                    DoubleTapFeedback.Rewind -> Alignment.CenterStart
                    DoubleTapFeedback.Forward -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            }
            val sidePadding = if (isCenterAnim) 0.dp else 40.dp
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = alignment,
            ) {
                if (isCenterAnim) {
                    val icon = if (feedback == DoubleTapFeedback.Pause) Icons.Default.Pause else Icons.Default.PlayArrow
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
                    val label = if (feedback == DoubleTapFeedback.Rewind) "-10s" else "+10s"
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
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top-left: time
                Text(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
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
                    TransparentIconButton(
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        onClick = onSubtitleClick,
                    )
                    TransparentIconButton(
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
                        color = themedDarkGlassColor(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
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
                    MinimalSeekbar(
                        position = position,
                        duration = duration,
                        bufferAheadTime = bufferAheadTime,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(8.dp))
                    TransparentIconButton(
                        icon = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        onClick = onMaximize,
                    )
                }
            }
        }
    }
}

private enum class DoubleTapZone { LEFT, CENTER, RIGHT }
private enum class DoubleTapFeedback { Pause, Play, Rewind, Forward }

@Composable
private fun TransparentIconButton(
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
