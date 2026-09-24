package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  The suite SUMMARY card (round 83, D-578 rework).
//
//  • The counts (passed / failed / untested) as tinted stat chips.
//  • A PROPORTION BAR — one animated horizontal bar whose segments show the
//    healthy / broken / untested share of the suite at a glance (the "graphs"
//    the user asked for, drawn with plain boxes — no chart dependency).
//  • PRECISION RE-RUNS: "Run all", "Failed only", "Passed only" — the user's
//    memory feature: results persist between visits, so a re-test can target
//    exactly the subset the user cares about. Actions are disabled while a
//    batch runs and hidden when their subset is empty.
//  • A small "Clear results" text action (wipes the persisted store).
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun TestingSummaryCard(
    totalCount: Int,
    passedCount: Int,
    failedCount: Int,
    testedCount: Int,
    isBatchRunning: Boolean,
    onRunAll: () -> Unit,
    onRunFailed: () -> Unit,
    onRunPassed: () -> Unit,
    onClearResults: () -> Unit,
) {
    val untested = (totalCount - testedCount).coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Suite summary",
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$testedCount / $totalCount tested",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── The proportion bar (animated segments) ──
            ProportionBar(
                total = totalCount,
                passed = passedCount,
                failed = failedCount,
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Passed", passedCount, MaterialTheme.colorScheme.primary)
                StatChip("Failed", failedCount, MaterialTheme.colorScheme.error)
                StatChip("Untested", untested, MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(14.dp))

            if (isBatchRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Tests are running\u2026",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRunAll,
                        enabled = totalCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Run all",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                    if (failedCount > 0) {
                        OutlinedButton(
                            onClick = onRunFailed,
                            contentPadding = ButtonDefaults.ContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Filled.Autorenew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Failed ($failedCount)",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (passedCount > 0) {
                        OutlinedButton(
                            onClick = onRunPassed,
                            contentPadding = ButtonDefaults.ContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Passed ($passedCount)",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Clear results",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(CircleShape)
                    .clickable(onClick = onClearResults)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * The animated pass/fail/untested proportion bar. Segments ease between
 * recompositions (a re-run visibly reshapes the bar instead of snapping).
 * Zero-width segments simply contribute nothing.
 */
@Composable
private fun ProportionBar(total: Int, passed: Int, failed: Int) {
    val untested = (total - passed - failed).coerceAtLeast(0)
    val safeTotal = if (total > 0) total else 1

    val passedFraction by animateFloatAsState(
        targetValue = passed / safeTotal.toFloat(),
        animationSpec = tween(500),
        label = "proportionPassed",
    )
    val failedFraction by animateFloatAsState(
        targetValue = failed / safeTotal.toFloat(),
        animationSpec = tween(500),
        label = "proportionFailed",
    )
    val untestedFraction = (untested / safeTotal.toFloat()).coerceIn(0f, 1f)

    val passedColor = MaterialTheme.colorScheme.primary
    val failedColor = MaterialTheme.colorScheme.error
    val untestedColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50)),
    ) {
        Segment(fraction = passedFraction.coerceIn(0f, 1f), color = passedColor)
        Segment(fraction = failedFraction.coerceIn(0f, 1f), color = failedColor)
        Segment(fraction = untestedFraction, color = untestedColor)
    }
}

@Composable
private fun Segment(fraction: Float, color: Color) {
    Box(modifier = Modifier.weight(fraction.coerceAtLeast(0.001f)).height(10.dp).background(color))
}

@Composable
internal fun StatChip(label: String, count: Int, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$count",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = tint,
            )
        }
    }
}
