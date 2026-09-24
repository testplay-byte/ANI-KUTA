package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 3 of 5 — THE DEDICATED RUN PAGE (round 84, D-583).
//
//  The round-84 report on the round-83 batch experience: "the tests which
//  were being performed were showing on the exact same screen in a pop-up,
//  which was not a good experience… it got stuck on some areas… the stop
//  button does not work". This page is the fix:
//    • a FULL SCREEN run experience (no overlay on the list),
//    • the live session renders from the app-scoped CONTROLLER — Stop and
//      Skip are always wired, and a wedged extension cannot stall the loop
//      (TestIsolation) or the UI (the controller's run scope survives),
//    • SKIP — the "stuck test" escape hatch the report asked for,
//    • per-kind live rows (ping → … → stream play) for the current target,
//    • the queue below, tap-to-detail verdicts, and a completion state with
//      the precision re-runs.
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

    // AUTO-START: this page's csv is the REQUEST. Start it exactly once (per
    // csv, per page instance) — and never while another run is live (the
    // Home banner's "View" push carries an empty csv and must only OBSERVE).
    var armedCsvs by rememberSaveable { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(targetIdsCsv, session?.phase) {
        val phase = session?.phase
        if (targetIdsCsv !in armedCsvs && phase != RunPhase.RUNNING) {
            armedCsvs = armedCsvs + targetIdsCsv
            val ids = targetIdsCsv.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .takeIf { it.isNotEmpty() }
            val label = when {
                ids == null -> "Run all"
                ids.size == targets.size && targets.isNotEmpty() -> "Run all"
                ids.size == 1 -> targets.firstOrNull { it.id == ids.first() }?.name ?: "1 target"
                else -> "Selected (${ids.size})"
            }
            controller.start(ids, label)
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
        ?.filter { id -> s.states[id]?.finished == true && id != s.currentTargetId }
        ?: emptyList()

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

                // ── Current target hero ──
                if (currentTarget != null) {
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
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ExtensionTestKind.entries.forEach { kind ->
                                        KindResultRow(kind = kind, result = currentState?.results?.get(kind))
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
                    items(completedIds.size, key = { "done-${completedIds[it]}" }) { i ->
                        val id = completedIds[i]
                        val target = targetsById[id] ?: return@items
                        val state = s?.states?.get(id)
                        RunQueueRow(
                            name = target.name,
                            target = target,
                            state = state,
                            onClick = { onOpenTarget(id) },
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
                    items(upcoming.size, key = { "up-${upcoming[it]}" }) { i ->
                        val id = upcoming[i]
                        val target = targetsById[id] ?: return@items
                        RunQueueRow(
                            name = target.name,
                            target = target,
                            state = s?.states?.get(id),
                            onClick = { onOpenTarget(id) },
                        )
                    }
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
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
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
