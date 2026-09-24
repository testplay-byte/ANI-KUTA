package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 3 of 5 — THE DEDICATED RUN PAGE (round 84, D-583; reworked round 85).
//
//  THE ROUND-85 DEVICE REPORT, and what this page does about it:
//    • "I clicked stop → it marked that as stopped, but it will STILL show
//      'now testing'" — the controller's settle now clears the live target
//      and terminalizes dangling results; this page additionally renders the
//      "Now testing" hero ONLY while the phase is RUNNING.
//    • "when I went back and tried clicking run test on it again, the test
//      did not rerun" — the auto-start arming used to be rememberSaveable,
//      which the nav shell keys BY CLASS NAME and restores on the next push:
//      the same csv re-armed = start() never fired again. Arming is now
//      INSTANCE-LOCAL remember, armed only AFTER a successful start.
//    • "the details should be shown below the tests and their details should
//      be proper, like full proper kind of details" — every completed kind
//      row is now TAP-EXPANDABLE, revealing its detail line beneath the row.
//    • the last target joins "Finished" at completion, a "Not tested"
//      section appears after a Stop, and the view follows the live target.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingRunScreen(
    targetIdsCsv: String,
    onBack: () -> Unit,
    onOpenTarget: (Long) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    // AUTO-START (round 85 rework): this page's csv is the REQUEST. The old
    // rememberSaveable armed-set was keyed by CLASS NAME in the nav shell's
    // SaveableStateHolder — pushing this page again with the same csv restored
    // the armed set and start() never fired (the "won't re-run" bug). Now:
    // instance-local state, armed only when the start actually went through;
    // a refusal (another run live) retries when the phase changes. The empty
    // csv (the Home banner's "View" push) only ever OBSERVES.
    var armedThisInstance by remember { mutableStateOf(false) }
    LaunchedEffect(targetIdsCsv, session?.phase) {
        if (armedThisInstance || targetIdsCsv.isBlank()) return@LaunchedEffect
        if (session?.phase == RunPhase.RUNNING) return@LaunchedEffect
        val ids = targetIdsCsv.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .takeIf { it.isNotEmpty() }
        val label = when {
            ids == null -> "Run all"
            ids.size == targets.size && targets.isNotEmpty() -> "Run all"
            ids.size == 1 -> targets.firstOrNull { it.id == ids.first() }?.name ?: "1 target"
            else -> "Selected (${ids.size})"
        }
        if (controller.start(ids, label)) {
            armedThisInstance = true
        }
    }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val s = session
    val total = s?.queue?.size ?: 0
    val done = s?.testedCount ?: 0
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val animatedFraction by animateFloatAsState(fraction, tween(400), label = "runFraction")

    val targetsById = targets.associateBy { it.id }
    val currentTarget = s?.currentTargetId?.let { targetsById[it] }
    val currentState = s?.currentTargetId?.let { s.states[it] }
    val upcoming = s?.queue?.drop((s.cursor.coerceAtLeast(0)).coerceAtMost(s.queue.size))
        ?.filter { it != s.currentTargetId }
        ?: emptyList()
    val completedIds = s?.queue
        ?.filter { id -> s.states[id]?.finished == true }
        ?: emptyList()
    // After a STOP: the queued-but-never-started remainder, honestly named.
    val notTestedIds = if (s?.phase == RunPhase.STOPPED) {
        s.queue.filter { id -> s.states[id]?.finished != true }
    } else {
        emptyList()
    }

    // Follow the live target: scroll the "current" hero into view when it
    // changes and the user is not already looking at it.
    LaunchedEffect(s?.currentTargetId, s?.phase) {
        if (s?.phase == RunPhase.RUNNING && s.currentTargetId != null) {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo.any { it.key == "current" }
            if (!visible) {
                val currentIndex = 1 // progress
                runCatching { listState.animateScrollToItem(currentIndex) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Test run",
                collapsed = collapsed,
                onBack = onBack,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Progress card ──
                item(key = "progress") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = s?.label ?: "No run yet",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                when (s?.phase) {
                                    RunPhase.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "${runProgressLabel(s.cursor, total)} · $done done",
                                            fontFamily = RobotoFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    RunPhase.COMPLETED -> Text(
                                        text = "Completed",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    RunPhase.STOPPED -> Text(
                                        text = "Stopped",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    null -> Unit
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { animatedFraction },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RunStatChip("Passed", s?.passedCount ?: 0, MaterialTheme.colorScheme.primary)
                                RunStatChip("Failed", s?.failedCount ?: 0, MaterialTheme.colorScheme.error)
                                RunStatChip("Skipped", s?.abortedCount ?: 0, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── Controls (Stop + Skip) — live while RUNNING ──
                if (s?.phase == RunPhase.RUNNING) {
                    item(key = "controls") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = controller::stop,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                            }
                            OutlinedButton(
                                onClick = controller::skipCurrent,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Skip target", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // ── Completion actions ──
                if (s?.phase == RunPhase.COMPLETED || s?.phase == RunPhase.STOPPED) {
                    item(key = "aftermath") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if ((s.failedCount + s.abortedCount) > 0) {
                                Button(
                                    onClick = { controller.rerunFailed() },
                                    enabled = s.phase != RunPhase.RUNNING,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Re-run failed (${s.failedCount + s.abortedCount})",
                                        fontFamily = RobotoFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { controller.rerunPassed() },
                                enabled = s.phase != RunPhase.RUNNING && s.passedCount > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Re-run passed (${s.passedCount})",
                                    fontFamily = RobotoFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }

                // ── Current target hero — RUNNING ONLY (the round-85 report:
                // the stale "Now testing" hero after a stop was the bug) ──
                if (currentTarget != null && s?.phase == RunPhase.RUNNING) {
                    item(key = "current") {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TargetIconView(currentTarget, 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentTarget.name,
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "Now testing",
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ExtensionTestKind.entries.forEach { kind ->
                                        LiveKindRow(
                                            kind = kind,
                                            result = currentState?.results?.get(kind),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Completed verdicts (tap for detail) ──
                if (completedIds.isNotEmpty()) {
                    item(key = "done-label") {
                        Text(
                            text = "Finished",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(completedIds, key = { "done-$it" }) { id ->
                        val target = targetsById[id] ?: return@items
                        val state = s?.states?.get(id)
                        RunQueueRow(
                            name = target.name,
                            target = target,
                            state = state,
                            onClick = { onOpenTarget(id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // ── Upcoming queue ──
                if (upcoming.isNotEmpty() && s?.phase == RunPhase.RUNNING) {
                    item(key = "queue-label") {
                        Text(
                            text = "Up next",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(upcoming, key = { "up-$it" }) { id ->
                        val target = targetsById[id] ?: return@items
                        RunQueueRow(
                            name = target.name,
                            target = target,
                            state = s?.states?.get(id),
                            onClick = { onOpenTarget(id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // ── After a STOP: the never-started remainder, honestly ──
                if (notTestedIds.isNotEmpty()) {
                    item(key = "not-tested-label") {
                        Text(
                            text = "Not tested (run stopped)",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(notTestedIds, key = { "nt-$it" }) { id ->
                        val target = targetsById[id] ?: return@items
                        RunQueueRow(
                            name = target.name,
                            target = target,
                            state = s?.states?.get(id),
                            onClick = { onOpenTarget(id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The hero's per-kind row (round 85): the compact line, and — for a
 * terminal result — a TAP-EXPANDABLE detail panel BELOW the row ("the
 * details should be shown below the tests and their details should be
 * proper"). This is where the run page stops looking like the list page.
 */
@Composable
private fun LiveKindRow(
    kind: ExtensionTestKind,
    result: TestResult?,
) {
    var expanded by remember(kind) { mutableStateOf(false) }
    val terminal = result != null && result.status != TestStatus.RUNNING &&
        result.status != TestStatus.PENDING
    val hasDetail = terminal && !result?.detail.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(Motion.DurationStandard, easing = Motion.EasingEmphasized),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasDetail) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        ) {
            KindResultRow(kind = kind, result = result, modifier = Modifier.weight(1f))
            if (hasDetail) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasDetail,
            enter = fadeIn(tween(150)) + expandVertically(tween(180, easing = Motion.EasingEmphasized)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 4.dp, end = 4.dp),
            ) {
                Text(
                    text = result?.message.orEmpty(),
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (result?.status) {
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        TestStatus.PASSED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!result?.detail.isNullOrBlank()) {
                    Text(
                        text = result.detail!!,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunStatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "$count $label",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RunQueueRow(
    name: String,
    target: TestableTarget,
    state: TargetRunState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            TargetIconView(target, 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TargetStatusChip(state)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
