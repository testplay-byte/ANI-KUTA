package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The CS player phase overlays (task 52 / Phase D; task 54 / round 14):
 * the resolving state (animated, with provider + episode context) and the
 * honest failure states (retry affordances, hidden-link counts). Design
 * language matches the details screen's auto-select dialog (pulse spinner,
 * centered column).
 *
 * Task 54: these now render INSIDE the 16:9 player box in minimized mode
 * (the watch page below stays visible) — the text sizes are tuned for the
 * smaller stage.
 */

@Composable
internal fun CsResolvingOverlay(
    animeTitle: String,
    episodeNumber: Float,
    providerName: String,
) {
    val transition = rememberInfiniteTransition(label = "cs_resolving")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Resolving streams",
            fontFamily = RobotoFamily,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append(animeTitle)
                append(" · EP ")
                append(if (episodeNumber % 1f == 0f) episodeNumber.toInt() else episodeNumber)
                append("\nvia $providerName")
            },
            fontFamily = RobotoFamily,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CsErrorOverlay(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            fontFamily = RobotoFamily,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            fontFamily = RobotoFamily,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Retry", fontFamily = RobotoFamily)
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Go back", fontFamily = RobotoFamily)
        }
    }
}
