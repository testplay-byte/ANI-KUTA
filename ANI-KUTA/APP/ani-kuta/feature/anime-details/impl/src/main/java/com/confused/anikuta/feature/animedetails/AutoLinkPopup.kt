package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════════════════════
//  D-225c: Auto-link popup — unified UI state + composable
// ════════════════════════════════════════════════════════════════════════════
//
//  A modern floating card that surfaces auto-link progress to the user.
//  Driven by BOTH directions:
//    • FORWARD  (extension → AniList) — from [AutoLinkState]
//    • REVERSE  (AniList → extensions) — from [ReverseAutoLinkState]
//
//  States rendered:
//    Searching → spinner + "Searching AniList…" / "Searching extensions…"
//    Matched   → check icon + "AniList metadata linked" / "Linked to {source}"
//    NoMatch   → search-off icon + "No AniList match" / "No source found" + "Link manually"
//    Error     → error icon + message
//    Skipped   → hidden (silent — feature is off, no need to bother the user)
//
//  Auto-dismiss:
//    • Matched → 3.5s
//    • Error   → 4.0s
//    • NoMatch → stays (needs user action: "Link manually" or close)
//    • Searching → stays (terminal state will replace it)
//
//  Animation: slide-up + fade (Material emphasized easing).
//  ════════════════════════════════════════════════════════════════════════════

/** Which auto-link direction this popup represents. Drives the copy. */
enum class AutoLinkDirection { FORWARD, REVERSE }

/** Unified UI state for the auto-link popup. */
sealed interface AutoLinkPopupState {
    data object Hidden : AutoLinkPopupState

    data class Searching(val direction: AutoLinkDirection) : AutoLinkPopupState

    data class Matched(
        val direction: AutoLinkDirection,
        val detail: String,
        val score: Float? = null,
    ) : AutoLinkPopupState

    data class NoMatch(
        val direction: AutoLinkDirection,
        val detail: String,
    ) : AutoLinkPopupState

    data class Error(
        val direction: AutoLinkDirection,
        val message: String,
    ) : AutoLinkPopupState
}

// ── State mappers (forward + reverse → unified popup state) ──

fun AutoLinkState.toPopupState(): AutoLinkPopupState = when (this) {
    AutoLinkState.Idle -> AutoLinkPopupState.Hidden
    AutoLinkState.Searching -> AutoLinkPopupState.Searching(AutoLinkDirection.FORWARD)
    is AutoLinkState.Matched -> AutoLinkPopupState.Matched(
        direction = AutoLinkDirection.FORWARD,
        detail = if (cached) "AniList metadata linked (cached)" else "AniList metadata linked",
        score = score,
    )
    is AutoLinkState.NoMatch -> AutoLinkPopupState.NoMatch(
        direction = AutoLinkDirection.FORWARD,
        detail = "No AniList match found",
    )
    is AutoLinkState.Skipped -> AutoLinkPopupState.Hidden
    is AutoLinkState.Error -> AutoLinkPopupState.Error(
        direction = AutoLinkDirection.FORWARD,
        message = message,
    )
}

fun ReverseAutoLinkState.toPopupState(): AutoLinkPopupState = when (this) {
    ReverseAutoLinkState.Idle -> AutoLinkPopupState.Hidden
    ReverseAutoLinkState.Searching -> AutoLinkPopupState.Searching(AutoLinkDirection.REVERSE)
    is ReverseAutoLinkState.Matched -> AutoLinkPopupState.Matched(
        direction = AutoLinkDirection.REVERSE,
        detail = "Linked to $sourceName",
        score = score,
    )
    is ReverseAutoLinkState.NoMatch -> AutoLinkPopupState.NoMatch(
        direction = AutoLinkDirection.REVERSE,
        detail = "No source found",
    )
    is ReverseAutoLinkState.Error -> AutoLinkPopupState.Error(
        direction = AutoLinkDirection.REVERSE,
        message = message,
    )
}

// ── Composable ──

/**
 * Floating auto-link status popup.
 *
 * Place inside a [Box] with [Alignment.BottomCenter] (or any alignment) — the
 * popup handles its own enter/exit animation + auto-dismiss.
 *
 * @param state   unified popup state (use [toPopupState] to map from VM state).
 * @param onManualLink  invoked when the user taps "Link manually" (NoMatch state).
 * @param onDismiss     invoked when the popup auto-dismisses OR the user taps ✕.
 *                      The caller should reset the underlying VM state to Idle.
 */
@Composable
fun AutoLinkPopup(
    state: AutoLinkPopupState,
    onManualLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss terminal states (except NoMatch — needs user action).
    // Keying on `state` re-runs the effect whenever the state instance changes.
    LaunchedEffect(state) {
        when (state) {
            is AutoLinkPopupState.Matched -> {
                delay(MATCHED_AUTO_DISMISS_MS)
                onDismiss()
            }
            is AutoLinkPopupState.Error -> {
                delay(ERROR_AUTO_DISMISS_MS)
                onDismiss()
            }
            else -> { /* Searching + NoMatch + Hidden: no auto-dismiss */ }
        }
    }

    AnimatedVisibility(
        visible = state !is AutoLinkPopupState.Hidden,
        enter = slideInVertically(
            animationSpec = tween(Motion.DurationStandard, easing = Motion.EasingEmphasized),
            initialOffsetY = { it }, // slide up from below
        ) + fadeIn(animationSpec = tween(Motion.DurationStandard)),
        exit = slideOutVertically(
            animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasized),
            targetOffsetY = { it }, // slide down off screen
        ) + fadeOut(animationSpec = tween(Motion.DurationShort)),
        modifier = modifier,
    ) {
        PopupCard(state = state, onManualLink = onManualLink, onDismiss = onDismiss)
    }
}

// ── Card content ──

@Composable
private fun PopupCard(
    state: AutoLinkPopupState,
    onManualLink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve display fields from the state. Hidden is never rendered here
    // (AnimatedVisibility in the caller hides it), but we early-return for safety.
    val content: PopupContent = when (state) {
        AutoLinkPopupState.Hidden -> return
        is AutoLinkPopupState.Searching -> PopupContent(
            icon = Icons.Filled.Search,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBg = MaterialTheme.colorScheme.primaryContainer,
            title = "Auto-link",
            subtitle = if (state.direction == AutoLinkDirection.REVERSE)
                "Searching extensions…" else "Searching AniList…",
            showSpinner = true,
            showManualLink = false,
        )
        is AutoLinkPopupState.Matched -> PopupContent(
            icon = Icons.Filled.CheckCircle,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            iconBg = MaterialTheme.colorScheme.primaryContainer,
            title = "Linked",
            subtitle = state.detail + (state.score?.let {
                " (${String.format("%.0f%%", it * 100)})"
            } ?: ""),
            showSpinner = false,
            showManualLink = false,
        )
        is AutoLinkPopupState.NoMatch -> PopupContent(
            icon = Icons.Filled.SearchOff,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
            title = "No match",
            subtitle = state.detail,
            showSpinner = false,
            showManualLink = true,
        )
        is AutoLinkPopupState.Error -> PopupContent(
            icon = Icons.Filled.ErrorOutline,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            iconBg = MaterialTheme.colorScheme.errorContainer,
            title = "Auto-link error",
            subtitle = state.message,
            showSpinner = false,
            showManualLink = false,
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon (or spinner) in a tinted circle.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(content.iconBg),
                contentAlignment = Alignment.Center,
            ) {
                if (content.showSpinner) {
                    CircularProgressIndicator(
                        color = content.tint,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(
                        imageVector = content.icon,
                        contentDescription = null,
                        tint = content.tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title + subtitle.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Optional "Link manually" action (NoMatch only).
            if (content.showManualLink) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onManualLink) {
                    Text(
                        text = "Link manually",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Dismiss ✕ — available on every visible state EXCEPT Searching
            // (can't cancel an in-flight search from the popup; it'll resolve on its own).
            if (state !is AutoLinkPopupState.Searching) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Bundle of display data for a single popup state. */
private data class PopupContent(
    val icon: ImageVector,
    val tint: Color,
    val iconBg: Color,
    val title: String,
    val subtitle: String,
    val showSpinner: Boolean,
    val showManualLink: Boolean,
)

private const val MATCHED_AUTO_DISMISS_MS = 3500L
private const val ERROR_AUTO_DISMISS_MS = 4000L
