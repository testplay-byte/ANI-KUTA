package com.confused.anikuta.core.designsystem.component.playerlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.common.HapticHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 104 (WS-D / D-703): the PLAYER list's swipe-to-toggle-watched.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.60 device round: "one key thing which I need you to implement is
//  that on the player episodes list there should be the swipe functionality
//  too, exactly like how it is on the details page."
//
//  THE REFERENCE (the details page's Phase WP gesture, EpisodeLayouts.kt's
//  SwipeToToggleWatched) is `internal` to :feature:anime-details — this is
//  the player-scoped twin in :core:designsystem, carrying the EXACT proven
//  algebra:
//    • the threshold — 35% of the screen width, either direction;
//    • the clamp — the drag never exceeds ±1.5× the threshold;
//    • the latch — `stageCross` haptic the moment the threshold is crossed
//      (once per crossing, both ways);
//    • the release — past the threshold: `releaseConfirm` + the toggle;
//      under it: the 300ms spring back to rest;
//    • the background icon — fades in linearly with the swipe progress
//      (CheckCircle when marking watched; VisibilityOff when un-marking),
//      pinned to the side being dragged toward.
//
//  The D-557 lesson rides along verbatim: the freshest callback is read
//  through rememberUpdatedState at CALL time (a pointerInput(Unit) closure
//  never restarts — a stale captured toggle wrote the same value twice).
//
//  THE GRID does not swipe (half-width cells cannot) — its cells long-press
//  to toggle instead (the details page's GRID parity), handled in the
//  layouts file's combinedClickable.
//
//  STRUCTURE (the original "background gone" lesson): the content Box is a
//  NORMAL child (it sizes the wrapper); the icon Surface matchParentSize
//  fills that footprint BEHIND it.
// ════════════════════════════════════════════════════════════════════════════

/**
 * Swipe-to-toggle-watched for one player episode entry. Wrap the entry's
 * card; [modifier] should carry the entry's OUTER padding (the gesture and
 * the background icon then cover exactly the card's visual footprint).
 */
@Composable
fun PlayerEpisodeSwipeToToggle(
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundShape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val swipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    // D-557 (the reference's lesson): pointerInput(Unit) never restarts, so
    // the callback is read through rememberUpdatedState at call time.
    val currentOnToggleWatched by rememberUpdatedState(onToggleWatched)
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx()
    }
    val swipeThresholdPx = screenWidthPx * 0.35f // 35% of screen width

    var thresholdCrossed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Background icon — fades in linearly as the user swipes, full
        // opacity past the threshold.
        val swipeProgress = (abs(swipeOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)
        val iconAlpha = if (thresholdCrossed) 1f else swipeProgress
        Surface(
            color = if (isWatched) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            },
            shape = backgroundShape,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = iconAlpha },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = if (swipeOffset.value > 0) Alignment.CenterStart
                else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.VisibilityOff
                    else Icons.Filled.CheckCircle,
                    contentDescription = if (isWatched) {
                        "Mark as unwatched"
                    } else {
                        "Mark as watched"
                    },
                    tint = if (isWatched) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // The content — translates with the swipe; the gesture lives here so
        // the caller's card keeps its own surface/clip/click chain.
        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdCrossed = false },
                        onDragEnd = {
                            // If past threshold → toggle + haptic. Else smooth
                            // spring back.
                            if (abs(swipeOffset.value) > swipeThresholdPx) {
                                HapticHelper.releaseConfirm(context)
                                currentOnToggleWatched()
                            }
                            coroutineScope.launch {
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            thresholdCrossed = false
                        },
                    ) { _, dragAmount ->
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(
                            minimumValue = -swipeThresholdPx * 1.5f, // allow left cancel
                            maximumValue = swipeThresholdPx * 1.5f,   // allow right toggle
                        )
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        // Haptic feedback when crossing the threshold for the
                        // first time.
                        if (!thresholdCrossed && abs(newValue) > swipeThresholdPx) {
                            thresholdCrossed = true
                            HapticHelper.stageCross(context)
                        } else if (thresholdCrossed && abs(newValue) <= swipeThresholdPx) {
                            thresholdCrossed = false
                        }
                    }
                },
        ) {
            content()
        }
    }
}
