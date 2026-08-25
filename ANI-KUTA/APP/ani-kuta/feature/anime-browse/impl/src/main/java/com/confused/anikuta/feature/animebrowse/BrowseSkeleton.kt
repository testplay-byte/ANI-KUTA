package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.confused.anikuta.core.designsystem.theme.Motion

/**
 * D-253: Browse loading state — shimmer skeletons mirroring the real layout
 * (hero block → continue-watching row → section label → card row) instead of
 * a full-screen spinner (CORE_RULES §22: "smooth skeletons / shimmer, not
 * jarring spinners").
 *
 * The shimmer is a simple reversed alpha pulse on surfaceVariant blocks
 * (1200ms) — cheap, 60fps-safe (alpha only), and theme-adaptive.
 */
@Composable
internal fun BrowseSkeleton() {
    val transition = rememberInfiniteTransition(label = "browseSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = Motion.EasingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulse)
    val cardShape = RoundedCornerShape(12.dp)

    Column(modifier = Modifier.fillMaxSize()) {
        // Hero block (full-bleed).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(block),
        )
        Spacer(Modifier.height(20.dp))

        // Continue-watching row.
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(width = 168.dp, height = 94.dp)
                        .clip(cardShape)
                        .background(block),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Section label.
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(96.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(block),
        )
        Spacer(Modifier.height(10.dp))

        // Card row.
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(192.dp)
                        .clip(cardShape)
                        .background(block),
                )
            }
        }
    }
}
