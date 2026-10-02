package com.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
 * @param onLongClick An optional long-press handler — null (default)
 *   keeps the exact tap-only behavior; non-null upgrades the row to
 *   combinedClickable and the platform's ~500ms long-press fires it.
 * @param holdActivationMillis D-657 → D-740: when set, the long-press gate
 *   requires holding for THIS many milliseconds instead of the platform's
 *   ~500ms — "the user has to long press on it for a bit more longer than
 *   usually necessary… for 10 seconds." A normal tap still fires [onClick];
 *   a robbed/cancelled gesture (a scroll stealing the press) fires nothing;
 *   holding the full window fires [onLongClick] while the finger is still
 *   down.
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

    // D-740: latest-callback refs for the hold effect below — the
    // LaunchedEffect captures these ONCE per (source, window) pair, so the
    // lambdas it invokes must never go stale across recompositions.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    // ── D-657 → D-740: THE SLOW-HOLD GATE ─────────────────────────────────
    // The window is measured by a plain composition-scoped coroutine that
    // watches the interaction stream (Press → timer starts; Release/Cancel →
    // timer dies), NOT by a timeout inside the pointer-input restricted
    // suspension scope. Round 95's original detector wrapped
    // waitForUpOrCancellation() in a withTimeoutOrNull INSIDE awaitEachGesture
    // — where the AwaitPointerEventScope MEMBER withTimeoutOrNull wins
    // resolution over the kotlinx import — and that member's timer (a
    // Modifier.Node-scope launch + delay + cross-thread
    // resumeWithException on the restricted-suspension awaiter) silently
    // stopped firing when kotlinx-coroutines moved 1.9.0 → 1.11.0 (round
    // 97, D-662): the gate died on every build from v1.1.54 through v1.1.71.
    // This rebuild sits entirely on machinery that is proven alive on the
    // current dependency set — the platform clickable (every row in the app)
    // drives the interaction stream, and the timer is a normal main-thread
    // withTimeoutOrNull (the same shape Compose's own key-input long-press
    // uses). Outcomes preserved exactly: a tap fires [onClick]; a robbed
    // hold (a scroll) fires nothing; the full window fires [onLongClick]
    // while the finger is still down — and the fired hold swallows its own
    // trailing tap so the row does not ALSO navigate on the lift.
    val holdMillis = holdActivationMillis
    var holdFired by remember { mutableStateOf(false) }
    if (holdMillis != null) {
        LaunchedEffect(interactionSource, holdMillis) {
            var holdJob: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        // A new gesture starts clean: clear any stale fired
                        // flag (a robbed-after-fire hold leaves one behind)
                        // and restart the window.
                        holdFired = false
                        holdJob?.cancel()
                        holdJob = launch {
                            val fired = withTimeoutOrNull(holdMillis) {
                                awaitCancellation()
                            } == null
                            if (fired) {
                                holdFired = true
                                // The gate has spoken — cancel the press
                                // visual while the finger is still down (the
                                // scale returns to 1f; the platform's own
                                // later Release is an orphan no-op for
                                // collectIsPressedAsState's list).
                                interactionSource.tryEmit(
                                    PressInteraction.Cancel(interaction)
                                )
                                currentOnLongClick?.invoke()
                            }
                        }
                    }
                    // The finger lifted (a tap) or the gesture was robbed
                    // (a scroll) — either way the window is dead.
                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    -> holdJob?.cancel()
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                // D-740: the armed gate rides the plain platform clickable —
                // one gesture owner, the interaction stream carries the
                // hold's clock, and the fired-hold flag swallows the
                // trailing tap. Unarmed rows keep the combinedClickable of
                // every other row in the app, byte-for-byte.
                if (holdMillis != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // No ripple — clean press animation per design language
                        onClick = {
                            if (holdFired) {
                                // The hold already fired (the page is open);
                                // this lift is not a tap.
                                holdFired = false
                            } else {
                                currentOnClick()
                            }
                        },
                    )
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
