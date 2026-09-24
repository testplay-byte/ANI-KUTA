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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
//  PAGE 4 of 5 — TARGET RESULT DETAIL (round 84, D-583).
//
//  The full per-target verdict: every kind's status, duration, message and
//  diagnostic detail (the report: "much more better detailed results … like
//  the ping test, the search test, the homepage test … all of these need to
//  be performed and handled much better"). Reached from the list expansion,
//  the run queue or the stats page; re-runs start a single-target session.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingTargetDetailScreen(
    targetId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    val testedTick = session?.testedCount ?: -1
    androidx.compose.runtime.LaunchedEffect(testedTick) {
        if (testedTick >= 0) storedRuns = controller.resultStore.loadAll()
    }

    val target = targets.firstOrNull { it.id == targetId }
    val state = session?.states?.get(targetId)
        ?: storedRuns[targetId]?.let { storedToRunState(it) }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val runActive = session?.phase == RunPhase.RUNNING
    val testedAt = storedRuns[targetId]?.testedAtMs

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = target?.name ?: "Test results",
                collapsed = collapsed,
                onBack = onBack,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (target == null) {
                    item(key = "missing") {
                        Text(
                            text = "This source is no longer installed.",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 28.dp, horizontal = 4.dp),
                        )
                    }
                    return@LazyColumn
                }

                // ── Hero ──
                item(key = "hero") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TargetIconView(target, 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.name,
                                        fontFamily = RobotoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(target.ecosystem.displayName())
                                            target.lang?.let { append(" · $it") }
                                            testedAt?.let {
                                                append(" · tested ")
                                                append(TESTED_AT_FORMAT.format(Date(it)))
                                            }
                                        },
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TargetStatusChip(state)
                            }
                            if (state?.finished == true) {
                                Spacer(Modifier.height(10.dp))
                                ProportionBar(
                                    passedCount = state.passedCount,
                                    failedCount = state.failedCount,
                                    untestedCount = state.skippedCount,
                                )
                            }
                        }
                    }
                }

                // ── Actions — IN-PLACE run (round 85): the cards below are
                // live session views, so the run animates right here — no
                // navigation, no orphaned session ──
                item(key = "actions") {
                    Button(
                        onClick = {
                            controller.start(listOf(targetId), target.name)
                        },
                        enabled = !runActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (runActive) "Testing in progress…" else "Run all tests for this source",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                // ── The seven verdict cards ──
                item(key = "results-label") {
                    Text(
                        text = "Test results",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                items(ExtensionTestKind.entries.size, key = { "kind-${ExtensionTestKind.entries[it].name}" }) { i ->
                    val kind = ExtensionTestKind.entries[i]
                    KindDetailCard(
                        kind = kind,
                        result = state?.results?.get(kind),
                        timeoutMs = kind.timeoutMs,
                    )
                }
            }
        }
    }
}

private val TESTED_AT_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

/** One kind's full result card — status, duration, message + detail lines. */
@Composable
private fun KindDetailCard(
    kind: ExtensionTestKind,
    result: TestResult?,
    timeoutMs: Long,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TestStatusIcon(result?.status ?: TestStatus.PENDING)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = kind.label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = kind.description,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 28.dp, top = 3.dp),
            )
            val message = when {
                result == null -> "Not run yet (budget ${TestTimeFormat.format(timeoutMs)})"
                result.status == TestStatus.RUNNING -> "Running…"
                result.message.isNotBlank() -> result.message
                else -> ""
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = when (result?.status) {
                        TestStatus.FAILED -> FontWeight.ExtraBold
                        else -> FontWeight.SemiBold
                    },
                    color = when (result?.status) {
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        TestStatus.PASSED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 28.dp, top = 5.dp),
                )
            }
            if (result?.detail != null && result.detail!!.isNotBlank()) {
                Text(
                    text = result.detail!!,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                )
            }
            // The ACTUAL data — poster strip, dossier, episode chips, servers,
            // byte-proof (the round-85 "show the real results" ask).
            result?.payload?.let { payload ->
                Column(modifier = Modifier.padding(start = 28.dp, top = 6.dp, bottom = 2.dp)) {
                    KindPayloadView(kind = kind, payload = payload)
                }
            }
        }
    }
}
