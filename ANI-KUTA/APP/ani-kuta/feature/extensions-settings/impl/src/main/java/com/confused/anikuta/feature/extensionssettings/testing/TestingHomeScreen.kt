package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 1 of 5 — TESTING HOME (round 84, D-583; the round-85 rework).
//
//  The round-85 device report: "we should drift away from it a little bit and
//  get some creativeness… the two buttons at the bottom, Run All Tests and
//  Statistics and History, feel a little bit off." This page is now the
//  showpiece:
//    • a HEALTH-RING hero (animated donut + legend + count-up chips) instead
//      of the flat proportion bar;
//    • a recent-run STRIP (the last runs as live chips);
//    • the two ecosystem cards (unchanged — the user liked them);
//    • a COMMAND FOOTER: one designed primary pill (Run all · N, morphing to
//      live progress + Stop while running) + a tonal Stats pill with a
//      failed-count badge — no more stock buttons dumped at the bottom.
//  Run All starts IN PLACE (round 85): the banner/footer take over live.
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingHomeScreen(
    onBack: () -> Unit,
    onOpenSystem: (String) -> Unit,
    onOpenRun: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    // The persisted verdicts + the run history — reloaded whenever a run
    // advances (a finished target is persisted immediately, so the hero
    // follows along live).
    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    var history by remember { mutableStateOf(controller.resultStore.loadHistory()) }
    val testedTick = session?.testedCount ?: -1
    LaunchedEffect(testedTick) {
        if (testedTick >= 0) {
            storedRuns = controller.resultStore.loadAll()
            history = controller.resultStore.loadHistory()
        }
    }

    fun stateFor(id: Long): TargetRunState? =
        session?.states?.get(id) ?: storedRuns[id]?.let { storedToRunState(it) }

    val aniyomiTargets = targets.filter { it.ecosystem == TestEcosystem.ANIYOMI }
    val csTargets = targets.filter { it.ecosystem == TestEcosystem.CLOUDSTREAM }
    val allStates = targets.mapNotNull { stateFor(it.id) }
    val tested = allStates.count { it.finished }
    val passed = allStates.count { it.isHealthy }
    val failed = allStates.count { it.finished && !it.isHealthy }
    val untested = (targets.size - tested).coerceAtLeast(0)

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val runActive = session?.phase == RunPhase.RUNNING

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extension Testing",
                collapsed = collapsed,
                onBack = onBack,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Live-run banner ──
                item(key = "banner") {
                    AnimatedVisibility(
                        visible = runActive,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(180)),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Testing ${runProgressLabel(session?.cursor ?: 0, session?.queue?.size ?: 0)}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = session?.label ?: "",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "View",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = onOpenRun)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop run",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = controller::stop)
                                        .padding(5.dp),
                                )
                            }
                        }
                    }
                }

                // ── Hero: THE HEALTH RING (the round-85 showpiece) ──
                item(key = "hero") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Suite health",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$tested of ${targets.size} tested",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                DonutChart(
                                    segments = listOf(
                                        DonutSegment(passed, MaterialTheme.colorScheme.primary),
                                        DonutSegment(failed, MaterialTheme.colorScheme.error),
                                        DonutSegment(untested, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                                    ),
                                    diameter = 116.dp,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (targets.isEmpty()) "—" else "${passed * 100 / targets.size}%",
                                            fontFamily = RobotoFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "healthy",
                                            fontFamily = RobotoFamily,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DonutLegendRow(MaterialTheme.colorScheme.primary, passed, "healthy")
                                    DonutLegendRow(MaterialTheme.colorScheme.error, failed, "failed")
                                    DonutLegendRow(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        untested,
                                        "untested",
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Recent runs — the history strip (tap → Stats) ──
                if (history.isNotEmpty()) {
                    item(key = "recent-runs") {
                        Column {
                            SectionLabel("Recent runs")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                history.take(6).forEach { entry ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.clickable(onClick = onOpenStats),
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                            Text(
                                                text = entry.label.ifEmpty { "Run" },
                                                fontFamily = RobotoFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${entry.passed}✓ ${entry.failed}✗ · ${entry.totalTargets}",
                                                fontFamily = RobotoFamily,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── The two ecosystem cards ──
                item(key = "systems-label") {
                    SectionLabel("Test a system")
                }
                item(key = "card-aniyomi") {
                    SystemCard(
                        title = "Aniyomi extensions",
                        subtitle = "${aniyomiTargets.size} testable sources",
                        stateCount = aniyomiTargets.size,
                        passed = aniyomiTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = aniyomiTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("aniyomi") },
                    )
                }
                item(key = "card-cloudstream") {
                    SystemCard(
                        title = "CloudStream plugins",
                        subtitle = "${csTargets.size} testable providers",
                        stateCount = csTargets.size,
                        passed = csTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = csTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("cloudstream") },
                    )
                }

                // ── THE COMMAND FOOTER (the round-85 rework of the two off
                // stock buttons): a designed primary pill that MORPHS with the
                // run phase (Run all · N → live n-of-m + Stop) + a tonal Stats
                // pill with a failed-count badge. Run All starts IN PLACE.
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = if (runActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1.7f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = targets.isNotEmpty()) {
                                    if (runActive) onOpenRun() else controller.start(null, "Run all")
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                if (runActive) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = runProgressLabel(session?.cursor ?: 0, session?.queue?.size ?: 0),
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "tap to view",
                                            fontFamily = RobotoFamily,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "Stop run",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable(onClick = controller::stop)
                                            .padding(7.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Run all tests · ${targets.size}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onOpenStats),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Stats",
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    if (failed > 0) {
                                        Text(
                                            text = "$failed failed",
                                            fontFamily = RobotoFamily,
                                            fontSize = 9.sp,
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
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun SystemCard(
    title: String,
    subtitle: String,
    stateCount: Int,
    passed: Int,
    failed: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (stateCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProportionBar(
                        passedCount = passed,
                        failedCount = failed,
                        untestedCount = stateCount - passed - failed,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open $title targets",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
