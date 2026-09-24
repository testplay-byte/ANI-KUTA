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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 5 of 5 — TESTING STATISTICS (round 84, D-583) — the dedicated stats
//  page the report asked for ("there should be dedicated stats page for it").
//
//  Reads ONLY the persisted store (verdicts + run history) — no live state —
//  so it is a stable post-run review surface:
//    • totals: targets tested, healthy, failed, run history length;
//    • per-system pass bars (Aniyomi vs CloudStream);
//    • per-kind aggregates (average duration + failure rate, from history);
//    • the recent-runs feed + the current failing list (tap → detail).
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

    val totalTested = storedRuns.size
    val totalHealthy = healthy(storedRuns.values.toList())

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

    // The failing targets right now (the "what should I fix next" list).
    val failing = storedRuns.values
        .filter { run -> run.finished && (run.abortedByUser || run.results.values.any { it.status == TestStatus.FAILED }) }
        .sortedByDescending { run -> run.results.values.count { it.status == TestStatus.FAILED } }

    val targetsById = targets.associateBy { it.id }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

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
                // ── Totals ──
                item(key = "totals") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Totals",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatsBig("$totalTested", "tested", Modifier.weight(1f))
                                StatsBig("$totalHealthy", "healthy", Modifier.weight(1f))
                                StatsBig("${history.size}", "runs", Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Per-system ──
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
                            SystemRateRow("Aniyomi", healthy(aniyomi), aniyomi.size)
                            Spacer(Modifier.height(8.dp))
                            SystemRateRow("CloudStream", healthy(cloudstream), cloudstream.size)
                        }
                    }
                }

                // ── Per-kind table ──
                if (kindStats.isNotEmpty()) {
                    item(key = "kinds-label") {
                        Text(
                            text = "Per-test aggregates",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(key = "kinds-header") {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Text(
                                "TEST",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(2f),
                            )
                            Text(
                                "AVG TIME",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "FAIL RATE",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    items(kindStats.size, key = { "kind-stat-${kindStats.keys.elementAt(it)}" }) { i ->
                        val name = kindStats.keys.elementAt(i)
                        val stat = kindStats.getValue(name)
                        val kind = ExtensionTestKind.entries.firstOrNull { it.name == name }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = kind?.label ?: name,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(2f),
                                )
                                Text(
                                    text = TestTimeFormat.format(stat.avgMs),
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (stat.runCount == 0) "—" else "${(stat.failCount * 100) / stat.runCount}%",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (stat.runCount > 0 && stat.failCount * 2 >= stat.runCount) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // ── Failing right now ──
                if (failing.isNotEmpty()) {
                    item(key = "failing-label") {
                        Text(
                            text = "Needs attention (${failing.size})",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(failing.size, key = { "failing-${failing[it].targetId}" }) { i ->
                        val run = failing[i]
                        val liveTarget = targetsById[run.targetId]
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = run.targetId != 0L) { onOpenTarget(run.targetId) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = liveTarget?.name ?: run.targetName,
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = run.results.values
                                            .filter { it.status == TestStatus.FAILED }
                                            .joinToString(" · ") { it.kind.label }
                                            .ifEmpty { "Skipped by user" },
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TargetStatusChip(storedToRunState(run))
                            }
                        }
                    }
                }

                // ── Run history ──
                if (history.isNotEmpty()) {
                    item(key = "history-label") {
                        Text(
                            text = "Recent runs",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(history.size, key = { "hist-$it-${history[it].startedAtMs}" }) { i ->
                        val entry = history[i]
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.label.ifEmpty { "Run" },
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = RUN_HISTORY_FORMAT.format(Date(entry.finishedAtMs)) +
                                            " · ${entry.totalTargets} target(s)",
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "${entry.passed}✓ ${entry.failed}✗" +
                                        if (entry.aborted > 0) " ${entry.aborted}⏭" else "",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
}

private val RUN_HISTORY_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

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
            Text(
                text = value,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun SystemRateRow(label: String, healthy: Int, total: Int) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$healthy / $total",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        ProportionBar(
            passedCount = healthy,
            failedCount = total - healthy,
            untestedCount = 0,
        )
    }
}
