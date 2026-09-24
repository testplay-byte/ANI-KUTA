package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  The BATCH RUN overlay (round 83, D-578) — the "proper, beautiful, clean
//  testing UI" the user asked for during multi-target runs.
//
//  Slide-up card pinned above the bottom edge while a batch runs. It shows:
//  • WHERE the run is — "Testing 3 of 7" on an animated progress bar.
//  • WHAT is under test right now — the target's icon + name + ecosystem.
//  • HOW it is doing — the live per-test rows (spinner → check / cross /
//    skip) for the CURRENT target, exactly like the card's expanded view.
//  • An always-reachable STOP.
//
//  The overlay is presentation-only: every bit of state arrives via params —
//  the screen owns the run machinery (one consumer, swappable in tests).
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun TestingBatchOverlay(
    visible: Boolean,
    currentTarget: TestableTarget?,
    currentState: TargetRunState?,
    index: Int,
    total: Int,
    onStop: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible && currentTarget != null,
        enter = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // ── Run position + Stop ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Testing $index of $total",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp, vertical = 6.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop testing",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Stop",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ── Animated progress bar (eases — never snaps) ──
                val fraction by animateFloatAsState(
                    targetValue = if (total > 0) index.coerceIn(0, total) / total.toFloat() else 0f,
                    animationSpec = tween(350),
                    label = "batchProgress",
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // ── The target under test right now ──
                val target = currentTarget ?: return@Column
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TargetIcon(target)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (target.ecosystem == TestEcosystem.ANIYOMI) "Aniyomi" else "CloudStream",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val liveState = currentState
                    if (liveState != null && !liveState.finished && liveState.results.isNotEmpty()) {
                        Text(
                            text = "${liveState.passedCount} \u2713  ${liveState.failedCount} \u2717",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // ── The live test rows for the current target ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ExtensionTestKind.entries.forEach { kind ->
                            TestRow(kind = kind, result = currentState?.results?.get(kind))
                        }
                    }
                }
            }
        }
    }
}
