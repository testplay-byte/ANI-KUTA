package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A "More" screen list row — leading icon, title + subtitle, trailing chevron.
 *
 * Ported from the old project's designsystem (app.confused.anikuta → com.confused.anikuta).
 *
 * Visual rules (match the old project exactly):
 *  - Surface: `surfaceVariant` at 40% alpha, `RoundedCornerShape(12.dp)`,
 *    horizontal 16dp / vertical 4dp outer padding.
 *  - Inner padding: 16dp.
 *  - Icon: 24dp, tinted `primary`. Optional red notification dot overlay at
 *    top-end corner.
 *  - Title: RobotoFamily ExtraBold 16sp, `onSurface`, 1 line ellipsized.
 *  - Subtitle: RobotoFamily Normal 13sp, `onSurfaceVariant`, 1 line
 *    ellipsized (D-532: was 2 — the one-line design language).
 *  - Trailing: `Icons.Filled.ChevronRight`, tinted `onSurfaceVariant`.
 *  - Press feedback: scale 0.97f (no ripple) per CORE_RULES §22.
 *
 * @param icon Leading icon (tinted accent).
 * @param title Row title.
 * @param subtitle Row subtitle.
 * @param onClick Click handler.
 * @param showDot Optional red notification dot at the icon's top-end corner.
 * @param onLongClick D-561: an optional long-press handler — null (default)
 *   keeps the exact tap-only behavior; set (Settings' "Debug options" row)
 *   the row upgrades to combinedClickable and a held press fires it.
 * @param holdActivationMillis ROUND 95 (D-657): when set, the long-press
 *   gate requires holding for THIS many milliseconds instead of the
 *   platform's ~500ms — "the user has to long press on it for a bit more
 *   longer than usually necessary… for 10 seconds." A normal tap still
 *   fires [onClick]; a robbed/cancelled gesture fires nothing; holding the
 *   full window fires [onLongClick] while the finger is still down.
 */
@Composable
fun MoreListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    holdActivationMillis: Long? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "moreRowScale",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                // D-657: the SLOW-HOLD gate replaces the combined clickable
                // entirely when armed — one gesture owner, no double fires.
                if (holdActivationMillis != null) {
                    Modifier.pointerInput(holdActivationMillis) {
                        detectTapOrLongHold(
                            holdMillis = holdActivationMillis,
                            onTap = onClick,
                            onLongHold = { onLongClick?.invoke() },
                            interactionSource = interactionSource,
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null, // No ripple — clean press animation per design language
                        onClick = onClick,
                        // D-561: null (default) = identical to the old .clickable;
                        // non-null = the row's hidden long-press (the debug gate).
                        onLongClick = onLongClick,
                    )
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon + optional red notification dot overlay at top-end corner.
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                if (showDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .background(
                                color = Color(0xFFFF5252),
                                shape = CircleShape,
                            ),
                    )
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // D-532: the ONE-LINE SUBTITLE design language (was 2) —
                    // every row states its business in one line; the clamp
                    // guarantees it, the shortened copy makes it never bite.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A small accent-colored section label for the More screen (e.g. "General",
 * "Activities", "Library", "Account").
 *
 * Style: RobotoFamily ExtraBold 14sp, `primary`, 20dp start / 16dp top / 8dp bottom.
 * Matches the old project's private `SettingsSectionLabel`.
 */
@Composable
fun MoreSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * ROUND 95 (D-657): THE SLOW-HOLD GATE — a tap-or-long-hold detector whose
 * hold window is measured in SECONDS, not the platform's ~500ms. The three
 * outcomes:
 *  • the finger lifts before the window (a tap, any length under it) →
 *    [onTap];
 *  • another gesture steals the press (a scroll, a parent consumer) →
 *    NOTHING fires — a robbed hold is not an activation;
 *  • the finger is STILL DOWN when the window elapses → [onLongHold]
 *    fires immediately (the trailing lift then does nothing — this
 *    handler owns the gesture exclusively).
 * The [interactionSource] keeps the row's press-scale animation honest: the
 * press emits on down and releases/cancels with the gesture's own outcome
 * (a fired hold cancels the visual while the finger is still down — the
 * gate has spoken).
 */
private suspend fun PointerInputScope.detectTapOrLongHold(
    holdMillis: Long,
    onTap: () -> Unit,
    onLongHold: () -> Unit,
    interactionSource: MutableInteractionSource,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        interactionSource.tryEmit(PressInteraction.Press(down.position))
        var sawUp = false
        val heldFullWindow = withTimeoutOrNull(holdMillis) {
            // True when the finger lifted cleanly (a tap); false when the
            // gesture was cancelled/robbed; NEVER returns on the full-hold
            // path (the timeout fires instead).
            sawUp = waitForUpOrCancellation() != null
        } == null
        if (heldFullWindow) {
            onLongHold()
        }
        interactionSource.tryEmit(
            if (sawUp) PressInteraction.Release() else PressInteraction.Cancel(),
        )
        if (sawUp) onTap()
    }
}
