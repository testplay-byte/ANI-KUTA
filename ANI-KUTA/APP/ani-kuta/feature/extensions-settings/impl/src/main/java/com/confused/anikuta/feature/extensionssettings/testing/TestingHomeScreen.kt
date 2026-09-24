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
import androidx.compose.ui.graphics.Color
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
    onOpenTarget: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    // The persisted verdicts — reloaded whenever a run advances (a finished
    // target is persisted immediately, so the hero follows along live).
    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    val testedTick = session?.testedCount ?: -1
    LaunchedEffect(testedTick) {
        if (testedTick >= 0) {
            storedRuns = controller.resultStore.loadAll()
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

    // D-588 (round 86): PER-SYSTEM health counts — the ring's two halves.
    fun systemCounts(list: List<TestableTarget>): Triple<Int, Int, Int> {
        val states = list.mapNotNull { stateFor(it.id) }
        val t = states.count { it.finished }
        val p = states.count { it.isHealthy }
        val f = states.count { it.finished && !it.isHealthy }
        return Triple(p, f, (list.size - t).coerceAtLeast(0))
    }
    val (aPassed, aFailed, aUntested) = systemCounts(aniyomiTargets)
    val (cPassed, cFailed, cUntested) = systemCounts(csTargets)

    // D-588 (round 86): the recent-run strip's data — the last tested
    // TARGETS (not run summaries), so every chip is the extension's own
    // ICON + NAME and nothing more (the user's exact ask).
    val targetsById = targets.associateBy { it.id }
    val recentRuns = storedRuns.values
        .sortedByDescending { it.testedAtMs }
        .take(8)

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val runActive = session?.phase == RunPhase.RUNNING

    // D-592 (round 86): the LEAVE GUARD on the hub — backing out of the
    // extension-testing section while a run is live prompts first; the
    // confirm cancels ALL the tests, exactly as the user specified.
    var showLeaveDialog by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = runActive) { showLeaveDialog = true }
    if (showLeaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = {
                Text(
                    text = "Leave extension testing?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    text = "Leaving now will cancel all the tests that are still running.",
                    fontFamily = RobotoFamily,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showLeaveDialog = false
                        controller.stop()
                        onBack()
                    },
                ) {
                    Text(
                        "Leave and cancel",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLeaveDialog = false }) {
                    Text(
                        "Stay",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    // D-591 (round 86): a single-target session's deep views route to the
    // DEDICATED target page, not the generic run page.
    val openRunOrTarget: () -> Unit = {
        val queue = session?.queue.orEmpty()
        if (queue.size == 1) onOpenTarget(queue.first()) else onOpenRun()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extension Testing",
                collapsed = collapsed,
                onBack = {
                    if (runActive) showLeaveDialog = true else onBack()
                },
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
                                        .clickable(onClick = openRunOrTarget)
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

                // ── Hero: THE HEALTH RING — split into TWO SYSTEM HALVES
                // (D-588, round 86): the same ring, six segments — the
                // Aniyomi half then the CloudStream half, parted by a thin
                // gap ("the separation will not be that much visible... both
                // of them will be shown together as one"). The passed color
                // marks the system (primary vs tertiary); failure + untested
                // share their universal colors.
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
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                DonutChart(
                                    segments = listOf(
                                        DonutSegment(aPassed, MaterialTheme.colorScheme.primary),
                                        DonutSegment(aFailed, MaterialTheme.colorScheme.error),
                                        DonutSegment(
                                            aUntested,
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            gapAfterDegrees = if (csTargets.isNotEmpty()) 4f else 0f,
                                        ),
                                        DonutSegment(cPassed, MaterialTheme.colorScheme.tertiary),
                                        DonutSegment(cFailed, MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                                        DonutSegment(
                                            cUntested,
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                        ),
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
                                // The two-system legend — a caption row per
                                // system, the three statuses underneath.
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Aniyomi",
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DonutLegendRow(MaterialTheme.colorScheme.primary, aPassed, "pass")
                                        DonutLegendRow(MaterialTheme.colorScheme.error, aFailed, "fail")
                                        DonutLegendRow(
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            aUntested,
                                            "new",
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "CloudStream",
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DonutLegendRow(MaterialTheme.colorScheme.tertiary, cPassed, "pass")
                                        DonutLegendRow(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            cFailed,
                                            "fail",
                                        )
                                        DonutLegendRow(
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                            cUntested,
                                            "new",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Recent runs — the ICON + NAME strip (D-588, round 86):
                // the old label+counts chips were "ugly" — each chip is now
                // just the extension's icon and its name, nothing more.
                if (recentRuns.isNotEmpty()) {
                    item(key = "recent-runs") {
                        Column {
                            SectionLabel("Recently tested")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                recentRuns.forEach { run ->
                                    val live = targetsById[run.targetId]
                                    val chipTarget = live ?: TestableTarget(
                                        id = run.targetId,
                                        name = run.targetName,
                                        ecosystem = run.ecosystem.toEcosystem() ?: TestEcosystem.ANIYOMI,
                                        lang = null,
                                        iconDrawable = null,
                                        iconUrl = null,
                                        providerName = null,
                                        baseUrl = null,
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.clickable(onClick = onOpenStats),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                                        ) {
                                            TargetIconView(chipTarget, size = 22.dp)
                                            Spacer(Modifier.width(7.dp))
                                            Text(
                                                text = run.targetName,
                                                fontFamily = RobotoFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── The two ecosystem cards (D-588, round 86): the count
                // moved OUT of the subtitle to a RIGHT-SIDE highlighted chip,
                // the trailing arrow is GONE, and each card carries its
                // system's accent blend.
                item(key = "systems-label") {
                    SectionLabel("Test a system")
                }
                item(key = "card-aniyomi") {
                    SystemCard(
                        title = "Aniyomi extensions",
                        countLabel = "${aniyomiTargets.size} sources",
                        accent = MaterialTheme.colorScheme.primary,
                        stateCount = aniyomiTargets.size,
                        passed = aniyomiTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = aniyomiTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("aniyomi") },
                    )
                }
                item(key = "card-cloudstream") {
                    SystemCard(
                        title = "CloudStream plugins",
                        countLabel = "${csTargets.size} providers",
                        accent = MaterialTheme.colorScheme.tertiary,
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
                                    if (runActive) openRunOrTarget() else controller.start(null, "Run all")
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "Stats",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (failed > 0) {
                                    Spacer(Modifier.width(7.dp))
                                    // The failed-count bubble — the pill's one
                                    // honest accent (D-588 restyle).
                                    Surface(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            text = "$failed",
                                            fontFamily = RobotoFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
    countLabel: String,
    accent: Color,
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
            // The system's accent edge — the card's color blend (D-588).
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.75f)),
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
            Spacer(Modifier.width(10.dp))
            // The count — a RIGHT-SIDE highlighted chip (the round-86 ask:
            // "the amount of extensions should be shown on the right side and
            // in a highlighted view"). The old trailing arrow is GONE.
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = countLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
    }
}
