package com.confused.anikuta.core.designsystem.component.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.SuccessDark
import com.confused.anikuta.core.designsystem.theme.SuccessLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 106 (WS-C): AppToast — the app's ONE themed toast system.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.62 device round: "if there are any other kind of toast
//  notifications throughout the application, I would like you to improve
//  them, and I would like you to format them properly and theme them
//  appropriately as needed." The app's 20+ `Toast.makeText` sites rendered
//  the UNTHEMED system toast (the grey box, the system font, no tone);
//  the details page's TrackingToastHost (round 104) had already defined the
//  app's approved in-app language — the tone disc + glyph + message on the
//  elevated surface.
//
//  THE DESIGN: that language, GENERALIZED —
//  • [AppToast.show] is callable from ANY context (composables, ViewModels,
//    the activity's threaded helpers) — a thread-safe StateFlow carries the
//    request to the ONE [AppToastHost] rendered at MainActivity's root
//    (over the nav — every screen, the players included).
//  • THREE tones carry the semantics: NEUTRAL (Info glyph, the quiet tone),
//    SUCCESS (CheckCircle in the success green), ERROR (the error glyph in
//    the error tone).
//  • The rhythm matches the approved tracking toast: slide-up + fade-in
//    (280ms), a ~2.4s hold, fade + sink out (320ms); a stage haptic lands
//    with the show; the host is a pass-through Box that never blocks the
//    page beneath it.
//  • The pill sits ABOVE the navigation bar (+16dp) — the v1.1.62 round's
//    placement order ("way too much aligned to the bottom… not a good
//    idea").
//
//  ErrorActivity (the separate crash activity) keeps its system toast — no
//  themed host exists there; documented in doc 88 §1.3.
// ════════════════════════════════════════════════════════════════════════════

/** The toast's tone — the glyph + the color the message speaks with. */
enum class AppToastTone {
    /** The quiet default — an Info glyph in the muted tone. */
    NEUTRAL,

    /** A confirmation — CheckCircle in the success green. */
    SUCCESS,

    /** A failure — the error glyph in the error tone. */
    ERROR,
}

/** One pending toast — [id] de-dups fast re-triggers; [durationMillis] is
 *  the HOLD length (the entrance/exit ride on top). */
data class AppToastRequest(
    val id: Long,
    val message: String,
    val tone: AppToastTone,
    val durationMillis: Long = 2400L,
)

/** The app's toast entry point — call [show] from anywhere, on any thread. */
object AppToast {

    private val _request = MutableStateFlow<AppToastRequest?>(null)

    /** The current pending request (null = nothing showing). */
    val request: StateFlow<AppToastRequest?> = _request.asStateFlow()

    private var nextId = 0L

    /**
     * Shows a themed toast. Thread-safe (a StateFlow set); safe to call
     * before any host composes (the request simply waits — the host plays
     * it on its next collection).
     *
     * @param message The message (keep it short — the pill clips at 2 lines).
     * @param tone The tone (NEUTRAL/SUCCESS/ERROR — the glyph + color).
     * @param durationMillis The HOLD length (default 2.4s; use ~3.6s for
     *   the old LENGTH_LONG sites).
     */
    fun show(
        message: String,
        tone: AppToastTone = AppToastTone.NEUTRAL,
        durationMillis: Long = 2400L,
    ) {
        if (message.isBlank()) return
        _request.value = AppToastRequest(
            id = ++nextId,
            message = message,
            tone = tone,
            durationMillis = durationMillis,
        )
    }

    /** Clears the pending request (the host calls this after the exit). */
    fun consume() {
        _request.value = null
    }
}

/**
 * The toast host — render ONCE at the app root (MainActivity's composition,
 * over the nav host). A pass-through overlay: it never intercepts touches.
 */
@Composable
fun AppToastHost() {
    val request by AppToast.request.collectAsState()

    // The entrance/exit state — the exit runs BEFORE the consume (the pill
    // sinks out, THEN clears; a fast re-trigger plays clean on the new id).
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(request?.id) {
        if (request != null) {
            HapticHelper.stageCross()
            visible = true
            // 280ms entrance + the hold.
            delay(280 + (request?.durationMillis ?: 2400L))
            visible = false
            // 320ms exit, then clear (the AnimatedVisibility's exit runs
            // while the request is still non-null).
            delay(320)
            AppToast.consume()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(280, easing = FastOutSlowInEasing),
        ) + fadeIn(tween(280)),
        exit = fadeOut(tween(320)) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(320, easing = FastOutSlowInEasing),
        ),
    ) {
        // The pass-through frame — BottomCenter, ABOVE the navigation bar
        // (the v1.1.62 placement order) with breathing room.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AppToastPill(
                message = request?.message.orEmpty(),
                tone = request?.tone ?: AppToastTone.NEUTRAL,
            )
        }
    }
}

/** The pill — the TrackingToastHost's approved visual, generalized. */
@Composable
private fun AppToastPill(
    message: String,
    tone: AppToastTone,
) {
    // The scheme-luminance polarity (the SA1-F1 lesson): the ACTIVE SCHEME's
    // background luminance is the truth — the app can force a theme opposite
    // the system.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successGreen = if (darkTheme) SuccessDark else SuccessLight
    val (glyph, toneColor) = when (tone) {
        AppToastTone.SUCCESS -> Icons.Filled.CheckCircle to successGreen
        AppToastTone.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        AppToastTone.NEUTRAL -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = if (darkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // The tone disc + glyph.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .background(toneColor.copy(alpha = 0.16f), CircleShape),
            ) {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = toneColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
