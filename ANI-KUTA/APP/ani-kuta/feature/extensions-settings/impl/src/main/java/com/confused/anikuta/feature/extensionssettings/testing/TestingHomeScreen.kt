package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.color.rememberIconTint
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 1 of 5 — TESTING HOME (round 84, D-583; round-85 rework; ROUND 91
//  D-630 — the v1.1.47 device round's polish pass):
//    • the SUITE-HEALTH RING hero keeps its round-90 geometry, and its
//      legend rows are now DEPTH CARDS — pass/fail/new each in their own
//      elevated section, separated by spacers, labels "pass"/"fail"/"new";
//    • the RECENT-RUN STRIP: rectangular rounded chips whose backgrounds
//      carry each extension's OWN icon tint (extracted via Palette — "theme
//      their background with their icon colors a bit… slightly applied");
//    • the two ecosystem cards wear the depth language (border + glyphs);
//    • the COMMAND FOOTER: rounded-RECTANGLE buttons (no more stadium
//      pills) — Run-all is a SOLID muted-accent fill that asks for
//      CONFIRMATION first, and while a run is live it becomes the
//      elapsed-time + Stop console;
//    • the LIVE BANNER (top) and the footer (bottom) finally say DIFFERENT
//      things: the top shows WHICH extension is being tested and AT WHAT
//      STAGE (a finished stage's verdict lingers ≥1s before yielding —
//      "even though it was completed way too quickly… some padding there
//      so the overall experience is dealt with smoothly"); the bottom
//      shows the run's elapsed time, the n-of-m count and the Stop action;
//    • the header BLUR (ScrollBlurOverlay) joins the app-wide language.
//  Run All still starts IN PLACE (round 85) — the banner/footer take over
//  live. (Future, ordered but NOT yet: a target picker behind Run-all.)
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

    // ── ROUND 91 (D-630): THE STAGE-LINE MACHINE — the live banner's second
    // line. It answers "which extension is being tested and at what stage":
    // while a kind runs, the line says so; when a kind finishes, its VERDICT
    // takes the line and holds it for a MINIMUM of ~1 second before yielding
    // to the next stage — a fast chain (several kinds finishing inside one
    // second) queues its verdicts instead of flashing them away ("even
    // though it was completed way too quickly and the next one has completed
    // too. There will be some padding there going on so that the overall
    // experience is dealt with smoothly").
    val stageLine = rememberStageLine(controller)

    // ROUND 91 (D-630): Run-all CONFIRMATION — the button asks before it
    // starts the whole suite ("it should first of all ask the user for the
    // confirmation whether to start all the tests or not").
    var showRunAllConfirm by remember { mutableStateOf(false) }
    if (showRunAllConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRunAllConfirm = false },
            title = {
                Text(
                    text = "Run all tests?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    text = "All ${targets.size} installed sources will be tested, one after another. You can stop anytime.",
                    fontFamily = RobotoFamily,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showRunAllConfirm = false
                        controller.start(null, "Run all")
                    },
                ) {
                    Text(
                        "Start",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRunAllConfirm = false }) {
                    Text(
                        "Not now",
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

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
            // ROUND 91 (D-630): the header BLUR — the app-wide ScrollBlurOverlay
            // language (DESIGN-LANGUAGE §2.2), on the testing hub at last.
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                // ── Live-run banner (ROUND 91, D-630 — the SPLIT live
                // experience's TOP half): WHICH extension is being tested
                // and AT WHAT STAGE. The verdict linger comes from the
                // stage-line machine; the elapsed time, the n-of-m count
                // and the Stop action live on the BOTTOM footer now — the
                // old duplicate "Testing n of m" at both ends is gone.
                // (The banner is its own composable on purpose: the call
                // site's receiver tower carries a ColumnScope from the
                // enclosing Column, and K2 would bind AnimatedVisibility
                // to the ColumnScope member extension there and reject it —
                // extracted, the call resolves to the top-level overload,
                // exactly like the list screen's pinned bars.)
                item(key = "banner") {
                    LiveRunBanner(
                        visible = runActive,
                        currentName = session?.currentTargetId?.let { targetsById[it]?.name },
                        stageLine = stageLine,
                    )
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
                                // ALIGNED table; ROUND 91, D-629 — the DEPTH
                                // SECTIONS): one row per VERDICT group with
                                // FIXED columns — the swatches, the group's
                                // total, the centered label, and the two
                                // systems' split — so every row lines up with
                                // its siblings (the old left-flowing row
                                // drifted with every label length). Each row
                                // now sits in its own elevated section,
                                // separated by spacers, and the labels read
                                // "pass" / "fail" / "new" (the round-91
                                // report: "instead of the passed text, you
                                // should show pass text. Instead of a failed
                                // text, you should show fail text… separate
                                // each one of those sections with some
                                // spacers, and give some depth to these three
                                // sections").
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HealthLegendRow(
                                        swatchA = TestingPalette.PassA,
                                        swatchB = TestingPalette.PassB,
                                        countA = aPassed,
                                        countB = cPassed,
                                        label = "Pass",
                                    )
                                    HealthLegendRow(
                                        swatchA = TestingPalette.FailA,
                                        swatchB = TestingPalette.FailB,
                                        countA = aFailed,
                                        countB = cFailed,
                                        label = "Fail",
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
                                    // ROUND 91 (D-630): the chip is a RECTANGLE
                                    // with rounded corners ("instead of pill
                                    // shape") and its background carries the
                                    // extension's OWN icon tint, slightly
                                    // applied (Palette extraction; the letter
                                    // tile's hue is the fallback so the chip
                                    // and its never-blank icon always agree).
                                    val chipTint = rememberIconTint(
                                        drawable = chipTarget.iconDrawable,
                                        iconUrl = chipTarget.iconUrl,
                                    ) ?: letterTileColor(chipTarget.name)
                                    Surface(
                                        color = chipTint.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.clickable(onClick = onOpenStats),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 7.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
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

                // ── The two ecosystem cards (D-588, round 86; ROUND 91
                // D-630 — the depth pass: each card carries its system's
                // GLYPH in a tinted well, a hairline border, and the accent
                // edge). The count sits beside the title (D-600) and the
                // proportion bar runs the full remaining width.
                item(key = "systems-label") {
                    SectionLabel("Test a system")
                }
                item(key = "card-aniyomi") {
                    SystemCard(
                        title = "Aniyomi extensions",
                        countLabel = "${aniyomiTargets.size} sources",
                        accent = TestingPalette.SystemA,
                        glyph = Icons.Filled.Tv,
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
                        glyph = Icons.Filled.Cloud,
                        stateCount = csTargets.size,
                        passed = csTargets.count { stateFor(it.id)?.isHealthy == true },
                        failed = csTargets.count { s -> stateFor(s.id)?.let { it.finished && !it.isHealthy } == true },
                        onClick = { onOpenSystem("cloudstream") },
                    )
                }

                // ── THE COMMAND FOOTER (ROUND 91, D-630 — the rounded-
                // RECTANGLE rework): the two stadium pills are gone. The
                // primary is a SOLID MUTED-ACCENT rectangle ("based on the
                // main accent color, but not a way too bright color" — the
                // accent lerped ~20% toward the background reads as a deep,
                // confident fill) that asks for CONFIRMATION before starting
                // the whole suite. While a run is live it becomes the BOTTOM
                // half of the split live experience: the run's LIVE ELAPSED
                // TIME + the n-of-m count + the Stop action (the extension
                // name and the stage live on the TOP banner — no more
                // duplicate progress at both ends). The whole pill still
                // opens the run view on tap.
                item(key = "actions") {
                    // D-630: the solid-but-muted accent — never full neon.
                    val runFill = lerp(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.background,
                        0.20f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = if (runActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                runFill
                            },
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = if (runActive) 0.dp else 2.dp,
                            modifier = Modifier
                                .weight(1.7f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = targets.isNotEmpty()) {
                                    if (runActive) openRunOrTarget() else showRunAllConfirm = true
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
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        // D-630: the run's LIVE clock — the
                                        // bottom half's own stat ("it could
                                        // show the current time at the
                                        // bottom").
                                        session?.startedAtMs?.let { startedAt ->
                                            LiveElapsedText(
                                                startedAtMs = startedAt,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        Text(
                                            text = runProgressLabel(session?.cursor ?: 0, session?.queue?.size ?: 0) +
                                                " · tap to view",
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
                                        text = "Run all tests",
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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
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
                // ROUND 91 (D-630): the header BLUR — pinned over the list's
                // top edge (the app-wide §2.2 language).
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
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
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    stateCount: Int,
    passed: Int,
    failed: Int,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // ROUND 91 (D-630): the system's GLYPH in a tinted well — the
            // card's identity anchor (depth pass).
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
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
 * the ALIGNED TABLE; ROUND 91, D-629 — the DEPTH SECTION): the verdict
 * group's two system swatches, its COMBINED total, its label, and the two
 * systems' split — each in a FIXED column so every row lines up with its
 * siblings:
 *
 *   [swatches] [total] [ label (centered) ] [ A ] [+] [ B ]
 *
 * The whole row now sits in its own ELEVATED SECTION — a hairline-bordered,
 * subtly filled card, separated from its siblings by spacers (the round-91
 * report: "separate each one of those sections with some spacers, and give
 * some depth to these three sections"). The A/B counts always render (even
 * 0s) — identical row structures are what make the columns align — and each
 * count carries its system's color so the split reads without a header.
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
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
                fontWeight = FontWeight.SemiBold,
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
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 91 (D-630): THE STAGE-LINE MACHINE — the live banner's pulse.
//  One small poll loop reads the controller's app-scoped session (the source
//  of truth — screen state copies go stale inside coroutines) and drives a
//  single "what the banner says" value:
//    • a NEW terminal verdict is ENQUEUED the moment it lands, then shown
//      for a MINIMUM of [STAGE_VERDICT_LINGER_MS] — fast chains queue up
//      instead of flashing ("there will be some padding there going on so
//      that the overall experience is dealt with smoothly");
//    • once a verdict's linger expires, the line yields to whatever stage is
//      live (waiting between stages/targets if it must), and re-arms for the
//      next run on a generation change;
//    • the idle path (no run) rests at null and polls gently.
// ════════════════════════════════════════════════════════════════════════════

/** The live banner's second line: the stage the run is at, or the verdict that just landed. */
internal sealed interface StageLine {

    /** A kind is executing right now ([detail] = the live search-ladder phrase, if any). */
    data class Running(val kind: ExtensionTestKind, val detail: String?) : StageLine

    /** A kind just finished — shown for the linger, then handed off. */
    data class Verdict(val kind: ExtensionTestKind, val result: TestResult) : StageLine {
        val verdictWord: String get() = when (result.status) {
            TestStatus.PASSED -> "passed"
            TestStatus.FAILED -> "failed"
            else -> "skipped"
        }
    }
}

/** A finished verdict owns the banner for at least this long (the "padding"). */
private const val STAGE_VERDICT_LINGER_MS = 1000L

/**
 * ROUND 91 (D-630): the live-run banner — the SPLIT live experience's TOP
 * half. WHICH extension is being tested (its name) + AT WHAT STAGE (the
 * stage line from [rememberStageLine], swapped with a soft crossfade).
 * Deliberately a TOP-LEVEL composable: at the LazyColumn call site the
 * receiver tower includes a ColumnScope, which makes K2 bind
 * `AnimatedVisibility(visible=…)` to the ColumnScope member extension and
 * reject the call ("cannot be called in this context with an implicit
 * receiver"); extracted here — no implicit receivers — it resolves to the
 * top-level overload, the same call shape the list screen's pinned bars
 * have shipped with since round 86.
 */
@Composable
private fun LiveRunBanner(
    visible: Boolean,
    currentName: String?,
    stageLine: StageLine?,
) {
    AnimatedVisibility(
        visible = visible,
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
                        text = currentName ?: "Starting…",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The stage line — running or lingering verdict, swapped
                    // with a soft crossfade (never an instant cut).
                    androidx.compose.animation.AnimatedContent(
                        targetState = stageLine,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "stageLine",
                    ) { sl ->
                        Text(
                            text = when (sl) {
                                is StageLine.Running ->
                                    "Testing ${sl.kind.label}…"
                                is StageLine.Verdict ->
                                    "${sl.kind.label} ${sl.verdictWord} · ${TestTimeFormat.format(sl.result.durationMs)}"
                                null -> "Preparing the chain…"
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                sl is StageLine.Verdict && sl.result.status == TestStatus.FAILED ->
                                    MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberStageLine(controller: ExtensionTestRunController): StageLine? {
    val line = remember { mutableStateOf<StageLine?>(null) }
    LaunchedEffect(Unit) {
        val pending = ArrayDeque<StageLine.Verdict>()
        val seen = mutableSetOf<ExtensionTestKind>()
        var lastGeneration = -1L
        var shownAtMs = 0L
        while (isActive) {
            val s = controller.session.value
            if (s != null) {
                // A new run re-arms the machine — no stale verdicts bleed in.
                if (s.generation != lastGeneration) {
                    lastGeneration = s.generation
                    seen.clear()
                    pending.clear()
                    line.value = null
                    shownAtMs = 0L
                }
                val st = s.currentTargetId?.let { s.states[it] }
                if (st != null) {
                    // Enqueue every verdict this screen has not yet shown.
                    st.results.forEach { (kind, result) ->
                        val terminal = result.status != TestStatus.RUNNING &&
                            result.status != TestStatus.PENDING
                        if (terminal && kind !in seen) {
                            seen += kind
                            pending.addLast(StageLine.Verdict(kind, result))
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            val held = line.value is StageLine.Verdict && now - shownAtMs < STAGE_VERDICT_LINGER_MS
            when {
                // A verdict owns its linger; when it expires, the next queued
                // verdict (or the live stage) takes over.
                !held && pending.isNotEmpty() -> {
                    line.value = pending.removeFirst()
                    shownAtMs = now
                }
                !held -> {
                    val s2 = controller.session.value
                    val st2 = s2?.currentTargetId?.let { s2.states[it] }
                    val running = st2?.runningKind
                    if (s2?.phase == RunPhase.RUNNING && running != null &&
                        (line.value as? StageLine.Running)?.kind != running
                    ) {
                        line.value = StageLine.Running(running, st2.runningDetail)
                    }
                }
            }
            // Gentle rest when nothing is running and nothing is queued.
            delay(if (s?.phase == RunPhase.RUNNING || pending.isNotEmpty()) 100L else 500L)
        }
    }
    return line.value
}
