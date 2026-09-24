package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 5 of 5 — TESTING STATISTICS (round 84, D-583; the DASHBOARD rework,
//  round 85).
//
//  The device report: "you were only using a single bar to represent the
//  details. You did not show any circular donuts. You did not show any
//  graphs, bars, or anything like that." This page is now a real mini
//  dashboard — every chart is bespoke Canvas work (TestingCharts.kt):
//    • the HEALTH RING hero (animated donut: passed / failed / interrupted)
//      with a count-up center;
//    • the pass-rate TREND over the run history (sparkline);
//    • BY SYSTEM — two mini donuts (Aniyomi vs CloudStream);
//    • STAGE RELIABILITY — a grow-in bar per test kind (fail rate + avg time);
//    • the failing-right-now list and the run-history feed (tap → detail).
//  Reads ONLY the persisted store (verdicts + run history) + the live session
//  tick, so it stays a stable post-run review surface.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingStatsScreen(
    onBack: () -> Unit,
    onOpenTarget: (Long) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    var history by remember { mutableStateOf(controller.resultStore.loadHistory()) }
    val testedTick = session?.testedCount ?: -1
    androidx.compose.runtime.LaunchedEffect(testedTick) {
        if (testedTick >= 0) {
            storedRuns = controller.resultStore.loadAll()
            history = controller.resultStore.loadHistory()
        }
    }

    val aniyomi = storedRuns.values.filter { it.ecosystem == TestEcosystem.ANIYOMI.name }
    val cloudstream = storedRuns.values.filter { it.ecosystem == TestEcosystem.CLOUDSTREAM.name }

    fun healthy(runs: List<ExtensionTestResultStore.StoredTargetRun>): Int =
        runs.count { run -> run.finished && !run.abortedByUser && run.results.values.none { it.status == TestStatus.FAILED } && run.results.values.any { it.status == TestStatus.PASSED } }

    fun interrupted(runs: List<ExtensionTestResultStore.StoredTargetRun>): Int =
        runs.count { it.finished && it.abortedByUser }

    val totalTested = storedRuns.size
    val totalHealthy = healthy(storedRuns.values.toList())
    val totalFailed = totalTested - totalHealthy - interrupted(storedRuns.values.toList())

    // Per-kind aggregates across ALL history entries.
    val kindStats = buildMap {
        history.forEach { entry ->
            entry.kinds.forEach { (name, stat) ->
                val prev = this[name]
                if (prev == null) {
                    put(name, stat)
                } else {
                    val runCount = prev.runCount + stat.runCount
                    val totalMs = prev.avgMs * prev.runCount + stat.avgMs * stat.runCount
                    put(
                        name,
                        ExtensionTestResultStore.StoredKindStat(
                            avgMs = if (runCount == 0) 0L else totalMs / runCount,
                            failCount = prev.failCount + stat.failCount,
                            runCount = runCount,
                        ),
                    )
                }
            }
        }
    }

    // The pass-rate trend, oldest → newest (only completed all-target runs).
    val trendValues = history
        .filter { it.totalTargets > 0 }
        .sortedBy { it.startedAtMs }
        .map { entry -> entry.passed.toFloat() / entry.totalTargets }

    val targetsById = targets.associateBy { it.id }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    // ROUND 87 (D-594): the second system color is the FIXED sky hue — the
    // un-themed Material-baseline tertiary (pale pink) collided with error.
    val tertiaryColor = TestingPalette.SystemB

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Testing statistics",
                collapsed = collapsed,
                onBack = onBack,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── THE HEALTH RING HERO ──
                item(key = "hero-ring") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                DonutChart(
                                    segments = listOf(
                                        DonutSegment(totalHealthy, primaryColor),
                                        DonutSegment(maxOf(totalFailed, 0), errorColor),
                                        DonutSegment(interrupted(storedRuns.values.toList()), restColor),
                                    ),
                                    diameter = 128.dp,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (totalTested == 0) {
                                                "—"
                                            } else {
                                                "${totalHealthy * 100 / totalTested}%"
                                            },
                                            fontFamily = RobotoFamily,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "healthy",
                                            fontFamily = RobotoFamily,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DonutLegendRow(primaryColor, totalHealthy, "healthy")
                                    DonutLegendRow(errorColor, maxOf(totalFailed, 0), "failed")
                                    DonutLegendRow(restColor, interrupted(storedRuns.values.toList()), "interrupted")
                                    DonutLegendRow(tertiaryColor, history.size, "runs logged")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatsBig("$totalTested", "tested", Modifier.weight(1f))
                                StatsBig("$totalHealthy", "healthy", Modifier.weight(1f))
                                StatsBig("${history.size}", "runs", Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Pass-rate TREND (sparkline over the history) ──
                if (trendValues.size >= 2) {
                    item(key = "trend") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Pass-rate trend",
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "last ${trendValues.size} runs",
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                ) {
                                    Sparkline(values = trendValues)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "oldest",
                                        fontFamily = RobotoFamily,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "latest",
                                        fontFamily = RobotoFamily,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── BY SYSTEM — two mini donuts ──
                item(key = "systems") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "By system",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SystemDonutCard(
                                    label = "Aniyomi",
                                    healthy = healthy(aniyomi),
                                    total = aniyomi.size,
                                    color = primaryColor,
                                    restColor = restColor,
                                    modifier = Modifier.weight(1f),
                                )
                                SystemDonutCard(
                                    label = "CloudStream",
                                    healthy = healthy(cloudstream),
                                    total = cloudstream.size,
                                    color = tertiaryColor,
                                    restColor = restColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // ── STAGE RELIABILITY — the per-kind grow-in bars ──
                if (kindStats.isNotEmpty()) {
                    item(key = "kinds-label") {
                        Text(
                            text = "Stage reliability",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(key = "kinds-bars") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier.padding(14.dp),
                            ) {
                                ExtensionTestKind.entries.forEach { kind ->
                                    val stat = kindStats[kind.name] ?: return@forEach
                                    val failFraction = if (stat.runCount == 0) {
                                        0f
                                    } else {
                                        stat.failCount.toFloat() / stat.runCount
                                    }
                                    AnimatedStatBarRow(
                                        label = kind.label,
                                        fraction = failFraction,
                                        valueText = if (stat.runCount == 0) {
                                            "—"
                                        } else {
                                            "${(failFraction * 100).toInt()}% · ${TestTimeFormat.format(stat.avgMs)}"
                                        },
                                        barColor = if (failFraction >= 0.5f) errorColor else primaryColor,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── D-588 (round 86): the "Needs attention" and "Recent runs"
                // sections are GONE (the user: "there is actually no need to
                // show any of those at all either there") — the page keeps the
                // hero ring, the trend, the by-system donuts and the stage
                // bars, then closes with the Clear affordance.
                item(key = "clear") {
                    TextButton(
                        onClick = {
                            controller.clearResults()
                            storedRuns = controller.resultStore.loadAll()
                            history = controller.resultStore.loadHistory()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Clear all results",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}


/** One system's mini donut + counts (the BY SYSTEM section). */
@Composable
private fun SystemDonutCard(
    label: String,
    healthy: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color,
    restColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp),
        ) {
            DonutChart(
                segments = listOf(
                    DonutSegment(healthy, color),
                    DonutSegment((total - healthy).coerceAtLeast(0), restColor),
                ),
                diameter = 72.dp,
                strokeFraction = 0.34f,
            ) {
                Text(
                    text = if (total == 0) "—" else "${healthy * 100 / total}%",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$healthy / $total healthy",
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsBig(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            CountUpText(
                value = value.filter { it.isDigit() }.toIntOrNull() ?: 0,
                fontSize = 18,
            )
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
