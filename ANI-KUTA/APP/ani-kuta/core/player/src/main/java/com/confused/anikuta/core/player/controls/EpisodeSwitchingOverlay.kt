package com.confused.anikuta.core.player.controls

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Overlay shown over the video surface while the player resolves and loads a
 * new episode. Prevents the user from interacting with the video during the
 * switch and gives clear visual feedback that something is happening.
 *
 * Ported from the old project's `EpisodeSwitchingOverlay` (simplified — no
 * thumbnail since the new project's `WatchKey` doesn't carry episode thumbnail
 * URLs yet; can be added later when episode metadata is wired through).
 *
 * Design:
 *  - Dark vertical gradient (so it reads as "loading" even over video).
 *  - Center: spinner + "Loading episode..." + optional episode title.
 *  - A subtle pulsing animation on the text for a live feel (CORE_RULES §22).
 *
 * @param episodeTitle Optional title of the episode being loaded (shown under spinner).
 */
@Composable
fun EpisodeSwitchingOverlay(
    episodeTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    // Subtle pulse on the text so the overlay feels "alive" (§22: smooth animations).
    val infiniteTransition = rememberInfiniteTransition(label = "switchingPulse")
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "textAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.75f),
                    0.5f to Color.Black.copy(alpha = 0.90f),
                    1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading episode...",
                color = Color.White.copy(alpha = textAlpha),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (!episodeTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episodeTitle,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
