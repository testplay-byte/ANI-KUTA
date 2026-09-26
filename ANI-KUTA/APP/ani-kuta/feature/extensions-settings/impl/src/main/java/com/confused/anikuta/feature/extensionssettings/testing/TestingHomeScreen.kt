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
import androidx.compose.ui.text.style.TextAlign
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
//    • the two ecosystem cards (reworked round 87, D-600 — the count chip
//      beside the title and the full-width bar);
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

    // D-588 (round 86): PER-SYSTEM health counts. ROUND 87 (D-599): the
    // ring is ONE COMBINED verdict view — the totals span BOTH systems
    // (10 Aniyomi passes + 5 CloudStream passes = one passed arc of 15,
    // SUBDIVIDED by system), exactly the user's example.
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

                // ── Hero: THE SUITE-HEALTH RING, COMBINED (round 87, D-599;
                // ROUND 90, D-627 — the rounded grouped ring) — the user's
                // rule: pass/fail/new are each ONE GROUP spanning BOTH
                // systems ("10 Anyomi passes + 5 CloudStream passes = one
                // passed arc of 15, split into 10 and 5"). Each verdict
                // group's arc is SUBDIVIDED by the two system colors
                // (emerald = Aniyomi, sky = CloudStream — the fixed pair
                // from TestingPalette, never the accent preset). ROUND 90's
                // geometry (the user's exact spec): the sub-arcs meet with
                // NO gap and NO rounding — only the GROUP's outer ends are
                // rounded, and the three groups part by real gaps, so the
                // ring reads as three rounded, spaced slices.
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
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                GroupedDonutChart(
                                    groups = listOf(
                                        DonutGroup(
                                            listOf(
                                                DonutSegment(aPassed, TestingPalette.PassA),
                                                DonutSegment(cPassed, TestingPalette.PassB),
                                            ),
                                        ),
                                        DonutGroup(
                                            listOf(
                                                DonutSegment(aFailed, TestingPalette.FailA),
                                                DonutSegment(cFailed, TestingPalette.FailB),
                                            ),
                                        ),
                                        DonutGroup(
                                            listOf(
                                                DonutSegment(aUntested, TestingPalette.NewA),
                                                DonutSegment(cUntested, TestingPalette.NewB),
                                            ),
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
                                // The COMBINED legend (round 90, D-627 — the
                                // ALIGNED table): one row per VERDICT group
                                // with FIXED columns — the swatches, the
                                // group's total, the centered label, and the
                                // two systems' split — so every row lines up
                                // with its siblings (the old left-flowing
                                // row drifted with every label length).
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    HealthLegendRow(
                                        swatchA = TestingPalette.PassA,
                                        swatchB = TestingPalette.PassB,
                                        countA = aPassed,
                                        countB = cPassed,
                                        label = "Passed",
                                    )
                                    HealthLegendRow(
                                        swatchA = TestingPalette.FailA,
                                        swatchB = TestingPalette.FailB,
                                        countA = aFailed,
                                        countB = cFailed,
                                        label = "Failed",
                                    )
                                    HealthLegendRow(
                                        swatchA = TestingPalette.NewA,
                                        swatchB = TestingPalette.NewB,
                                        countA = aUntested,
                                        countB = cUntested,
                                        label = "New",
                                    )
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
                        accent = TestingPalette.SystemA,
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
                        accent = TestingPalette.SystemB,
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
                            // ROUND 87 (D-601): the idle content is CENTERED
                            // (it hugged the left edge while the sibling pill
                            // centered — the "a bit off" asymmetry).
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
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
            // ROUND 87 (D-600): the count chip sits RIGHT NEXT TO the title
            // ("the actual number of extensions should be shown on the right
            // side of the text itself, not the whole right side"), and the
            // proportion bar runs the FULL remaining width (the 0.8f cap is
            // gone) — the bar expands as far as the card allows.
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = title,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
                if (stateCount > 0) {
                    Spacer(Modifier.height(7.dp))
                    ProportionBar(
                        passedCount = passed,
                        failedCount = failed,
                        untestedCount = stateCount - passed - failed,
                        modifier = Modifier.fillMaxWidth(),
                        passedColor = accent,
                        failedColor = TestingPalette.FailA.takeIf { accent == TestingPalette.SystemA }
                            ?: TestingPalette.FailB,
                    )
                }
            }
        }
    }
}

/**
 * The combined suite-health legend row (round 87, D-599; ROUND 90, D-627 —
 * the ALIGNED TABLE): the verdict group's two system swatches, its COMBINED
 * total, its label, and the two systems' split — each in a FIXED column so
 * every row lines up with its siblings:
 *
 *   [swatches] [total] [ label (centered) ] [ A ] [+] [ B ]
 *
 * The user's round-90 spec: "the total numbers will be aligned properly…
 * the passed text, the failed text, the new text will be center aligned…
 * the other details, like Anyomi plus CloudStream, those details will be
 * properly aligned in the same way too with each other." The A/B counts
 * always render (even 0s) — identical row structures are what make the
 * columns align — and each count carries its system's color so the split
 * reads without a header.
 */
@Composable
private fun HealthLegendRow(
    swatchA: Color,
    swatchB: Color,
    countA: Int,
    countB: Int,
    label: String,
) {
    val total = countA + countB
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = swatchA, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(3.dp))
        Surface(color = swatchB, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(8.dp))
        // The combined total — fixed-width, centered: a true number column.
        Text(
            text = "$total",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        // The label — centered in the flexible middle column.
        Text(
            text = label.lowercase(),
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The split — Aniyomi + CloudStream in fixed sub-columns, each
        // count in its system's color, so the columns line up row to row.
        Text(
            text = "$countA",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = swatchA,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = "+",
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(9.dp),
        )
        Text(
            text = "$countB",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = swatchB,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
        )
    }
}
