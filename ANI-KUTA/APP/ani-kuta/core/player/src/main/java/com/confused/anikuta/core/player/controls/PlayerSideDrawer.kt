package com.confused.anikuta.core.player.controls

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * ROUND 125 (R2/R3/R4): the shared side-drawer shell for the player's
 * fullscreen surfaces — one implementation, three consumers (qualities/
 * servers, subtitles, subtitle settings).
 *
 * The round-124 lesson, encoded: the right-side drawer is a FULLSCREEN
 * presentation. Portrait keeps the bottom-up sheets (the device round's
 * correction: "if I am in portrait mode and I click on the subtitles option
 * or the qualities option, it opens up a right side menu which is not good…
 * properly revert this completely to the exact same how it was before") —
 * the callers decide via their `asDrawer` flag, not this shell.
 *
 * ROUND 125 (R4): the settings panel slides from the LEFT ([fromLeft]) —
 * the mirror of the options drawer on the right ("the subtitle menu settings
 * should not appear from the bottom up… they should appear from the left
 * side, just like how the subtitle options appear from the right side, the
 * subtitle settings will appear from the left side smoothly as the right
 * side subtitle options disappear").
 *
 * Geometry: [widthFraction] of the real screen width, hard-capped so
 * ultra-wide landscape screens don't grow a ribbon-wide drawer. The
 * qualities/subtitles drawers pass a wider pair than the episodes drawer
 * (the round-125 note: "the qualities menu could be a little bit more
 * wider").
 */
@Composable
fun PlayerSideDrawerShell(
    onDismiss: () -> Unit,
    fromLeft: Boolean = false,
    widthFraction: Float = 0.40f,
    maxWidthDp: Int = 360,
    content: @Composable ColumnScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val panelMaxWidth = with(LocalDensity.current) {
        ((configuration.screenWidthDp * widthFraction).dp).coerceAtMost(maxWidthDp.dp)
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        // The scrim — tap to close (no ripple; a quiet dismissal surface).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        // The panel — slides in from the requested edge (the
        // MutableTransitionState makes the ENTER animation run on composition;
        // the caller mounts this composable conditionally).
        val enterState = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = enterState,
            enter = slideInHorizontally(
                initialOffsetX = { if (fromLeft) -it else it },
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { if (fromLeft) -it else it },
            ) + fadeOut(),
            modifier = Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = if (fromLeft) {
                    RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                } else {
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                },
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = panelMaxWidth)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    content = content,
                )
            }
        }
    }
}
