package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 4 of 5 — TARGET RESULT DETAIL. The round-87 REWORK (D-605..D-610)
//  plus the round-88 VERDICT (the device: the overall experience was "most
//  definitely not good") and its fixes (D-615..D-621):
//    • the DOSSIER: compressed into a TWO-COLUMN meta grid (the old seven
//      stacked rows ate half the screen before the first result), the
//      duplicated "System" row GONE, and a VERDICT SUMMARY (passed x /
//      failed y / not-run z) answering "how did it go" at a glance (D-617);
//    • the TIME-PROPORTIONAL STAGE BAR: each segment's LENGTH is the time
//      its test took (live-growing while it runs); PENDING segments wear
//      their kind colors (not one gray strip), the 2% clamp is replaced by
//      a renormalized visibility floor, and the per-segment animation waves
//      are gone (D-620);
//    • the RESULT BLOCKS: tinted + bordered with the kind color, message
//      + detail clean text rows — the failure REASON shows IN FULL (the
//      "full details" page no longer ellipsizes the why at two lines),
//      the payload renders inside a labeled results section WITHIN the
//      block, and kinds whose payload view is empty (PING) skip the box
//      entirely (D-618/D-622);
//    • the TRANSIENT VERDICT BANNER: an OVERLAY pinned under the header —
//      it slides in, lingers ~2.6s, slides away, and shifts NOTHING (the
//      old inline item shoved the whole timeline down and back on every
//      completion); SEEDED so a stored page never replays a stale verdict
//      on open (D-615/D-616);
//    • the RUN PILL knows its place in a live run: THIS target queued or
//      running → a "View live run" door; another run live → honestly
//      disabled; otherwise it starts (D-619). The status chip reads
//      "Queued" for a queued target, like the list's.
//    • the rail: a RUNNING test's bubble PULSES — running vs passed is
//      unmistakable without reading a word (D-621).
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingTargetDetailScreen(
    targetId: Long,
    onBack: () -> Unit,
    onOpenRun: () -> Unit = {},
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

    // ── THE TRANSIENT VERDICT BANNER (D-609; reworked round 88, D-615) —
    // the just-finished test's verdict slides in, lingers, then slides
    // away. THE STALE-BANNER FIX: the seen-set is SEEDED with every verdict
    // that already existed at the first composition — the old code treated
    // a stored page's whole history as "fresh", so merely OPENING a tested
    // extension popped an unearned banner for its last verdict (usually
    // "Stream play …"), every single time. Only a verdict that ARRIVES
    // while this screen watches earns the banner now; a kind returning to
    // RUNNING (a re-run) un-sees itself.
    val results = state?.results
    var banner by remember { mutableStateOf<Pair<ExtensionTestKind, TestResult>?>(null) }
    var seenTerminalKinds by remember(targetId) { mutableStateOf(setOf<ExtensionTestKind>()) }
    var bannerSeeded by remember(targetId) { mutableStateOf(false) }
    val runningKinds = results
        ?.filterValues { it.status == TestStatus.RUNNING }
        ?.keys
        ?: emptySet()
    LaunchedEffect(runningKinds) {
        if (runningKinds.isNotEmpty()) {
            seenTerminalKinds = seenTerminalKinds - runningKinds
        }
    }
    LaunchedEffect(results) {
        val terminal = results
            ?.filterValues { it.status != TestStatus.RUNNING && it.status != TestStatus.PENDING }
            ?: emptyMap()
        if (!bannerSeeded && results != null) {
            // First composition with a results snapshot: everything already
            // terminal is HISTORY, not news — mark it seen, silently.
            seenTerminalKinds = seenTerminalKinds + terminal.keys
            bannerSeeded = true
            return@LaunchedEffect
        }
        val fresh = terminal.filterKeys { it !in seenTerminalKinds }
        if (fresh.isNotEmpty()) {
            seenTerminalKinds = seenTerminalKinds + fresh.keys
            val entry = fresh.entries.last()
            banner = entry.key to entry.value
            delay(2600)
            if (banner?.first == entry.key) banner = null
        }
    }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    val runActive = session?.phase == RunPhase.RUNNING
    // ROUND 88 (D-619): THIS page finally knows its place in a live run —
    // the target may be QUEUED or RUNNING inside the session the user is
    // watching from elsewhere; the old code answered both with a dead
    // disabled pill and an "Untested" chip.
    val inLiveRun = runActive &&
        session?.queue?.contains(targetId) == true &&
        session?.states?.get(targetId)?.finished != true
    val testedAt = storedRuns[targetId]?.testedAtMs

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = target?.name ?: "Test results",
                collapsed = collapsed,
                onBack = onBack,
            )
            // D-616: the list lives in its OWN box so the verdict banner can
            // float OVER it, pinned under the header — appearance and exit
            // shift nothing, ever.
            Box(modifier = Modifier.weight(1f)) {
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

                    // ── THE DOSSIER HEADER (D-605; compressed round 88, D-617) ──
                    item(key = "hero") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TargetIconView(target, 48.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = target.name,
                                            fontFamily = RobotoFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = buildString {
                                                append(target.ecosystem.displayName())
                                                target.lang?.let { append(" · $it") }
                                            },
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    // D-619: a queued target reads "Queued" here,
                                    // exactly like it does on the list page.
                                    TargetStatusChip(state, queued = inLiveRun)
                                }
                                // ── THE VERDICT SUMMARY (round 88, D-618) — the
                                // page's answer to "quite a lot of failures": one
                                // glance says how the chain went before a single
                                // block is read.
                                state?.results?.takeIf { it.isNotEmpty() }?.let { results ->
                                    val passed = results.values.count { it.status == TestStatus.PASSED }
                                    val failed = results.values.count { it.status == TestStatus.FAILED }
                                    val decided = results.values.count {
                                        it.status != TestStatus.PENDING && it.status != TestStatus.RUNNING
                                    }
                                    val notRun = ExtensionTestKind.entries.size - decided
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        if (passed > 0) {
                                            VerdictCountChip("Passed", passed, MaterialTheme.colorScheme.primary)
                                        }
                                        if (failed > 0) {
                                            VerdictCountChip("Failed", failed, MaterialTheme.colorScheme.error)
                                        }
                                        if (notRun > 0) {
                                            VerdictCountChip(
                                                "Not run",
                                                notRun,
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                // The metadata GRID — two fact columns instead of
                                // the old seven stacked rows (the hero used to eat
                                // half the screen before the first result; D-617).
                                // The duplicated "System" row is GONE — the
                                // subtitle beside the name already says it.
                                val meta = controller.targetMeta(target)
                                val metaCells = buildList {
                                    meta.version?.let { add(MetaCell("Version", it, wide = false)) }
                                    meta.pluginName?.let { add(MetaCell("Plugin", it, wide = false)) }
                                    meta.pkgName?.let { add(MetaCell("Package", it, wide = true)) }
                                    meta.isNsfw?.let {
                                        add(MetaCell("NSFW", if (it) "Yes" else "No", wide = false))
                                    }
                                    testedAt?.let {
                                        add(MetaCell("Last tested", TESTED_AT_FORMAT.format(Date(it)), wide = false))
                                    }
                                    meta.siteUrl?.let { add(MetaCell("Site", it, wide = true)) }
                                }
                                MetaGrid(cells = metaCells)
                                Spacer(Modifier.height(10.dp))
                                // THE TIME-PROPORTIONAL STAGE BAR — length is
                                // time, color is the test (D-606; pending
                                // segments wear their kind colors, D-620).
                                MultiStageProgressBar(state = state)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Each segment is one test — its length is the time it took",
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    // ── THE RUN PILL (D-608; live-run aware round 88, D-619) —
                    // three honest states: THIS target is queued/running in the
                    // live session → a "View live run" door; a DIFFERENT run is
                    // live → disabled with the truth on the label; otherwise it
                    // starts the run.
                    item(key = "actions") {
                        val canRunHere = !runActive
                        val watchingThisRun = inLiveRun
                        Surface(
                            color = when {
                                // D-624: the live-run accent is the PALETTE's sky
                                // (TestingPalette.SystemB) — the M3 baseline
                                // `tertiary` is the never-themed pale pink the
                                // round-87 report already rejected once.
                                watchingThisRun -> TestingPalette.SystemB.copy(alpha = 0.14f)
                                canRunHere -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    watchingThisRun -> TestingPalette.SystemB.copy(alpha = 0.5f)
                                    canRunHere -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                },
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = watchingThisRun || canRunHere) {
                                    when {
                                        watchingThisRun -> onOpenRun()
                                        canRunHere -> controller.start(listOf(targetId), target.name)
                                    }
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                watchingThisRun -> TestingPalette.SystemB
                                                canRunHere -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = when {
                                            // D-624: the sky bubble is a LIGHT hue —
                                            // black reads on it the way the letter
                                            // tiles always have.
                                            watchingThisRun -> Color.Black
                                            canRunHere -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = when {
                                        watchingThisRun -> "View live run"
                                        canRunHere -> "Run all tests"
                                        else -> "A run is in progress…"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        watchingThisRun -> TestingPalette.SystemB
                                        canRunHere -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "· ${ExtensionTestKind.entries.size} tests",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ── THE TIMELINE — one item so the left rail connects ──
                    item(key = "results-label") {
                        val decidedCount = state?.results?.values
                            ?.count { it.status != TestStatus.PENDING && it.status != TestStatus.RUNNING }
                            ?: 0
                        Text(
                            text = "Test results · $decidedCount of ${ExtensionTestKind.entries.size} done",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(key = "timeline") {
                        val railColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ExtensionTestKind.entries.forEachIndexed { index, kind ->
                                val result = state?.results?.get(kind)
                                val isLast = index == ExtensionTestKind.entries.lastIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                ) {
                                    // The LEFT RAIL — connector → bubble → connector.
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(26.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(if (index == 0) 0.dp else 8.dp)
                                                .background(railColor),
                                        )
                                        TimelineBubble(result?.status ?: TestStatus.PENDING)
                                        if (!isLast) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .weight(1f)
                                                    .background(railColor),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    KindDetailCard(
                                        kind = kind,
                                        result = result,
                                        state = state,
                                        autoPlayPreview = inLiveRun,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (!isLast) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // ── THE TRANSIENT VERDICT BANNER — an OVERLAY now (D-616):
                // pinned under the header, floating ABOVE the list. The old
                // inline item's height snapped in and out on every completion,
                // shoving the whole timeline ~34dp down and back (up to 14
                // jolts per 7-test run) — this one shifts NOTHING, so the
                // page's "the view never jumps" promise finally holds.
                TransientResultBanner(
                    banner = banner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
    }
}

private val TESTED_AT_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

/**
 * One dossier META CELL (round 88, D-617): a small uppercase muted label
 * above (or beside) its value — the two-column grid replaced the seven
 * stacked rows so the hero no longer eats half the screen. Wide cells
 * (long package names and site URLs) take a full row and wrap to two
 * lines instead of a mid-string ellipsis.
 */
private data class MetaCell(val label: String, val value: String, val wide: Boolean)

/** Pairs narrow cells two-per-row; wide cells take a full row. */
private fun buildMetaRows(cells: List<MetaCell>): List<List<MetaCell>> {
    val rows = mutableListOf<List<MetaCell>>()
    var index = 0
    while (index < cells.size) {
        val cell = cells[index]
        if (cell.wide || index == cells.lastIndex || cells[index + 1].wide) {
            rows.add(listOf(cell))
            index += 1
        } else {
            rows.add(listOf(cell, cells[index + 1]))
            index += 2
        }
    }
    return rows
}

@Composable
private fun MetaGrid(cells: List<MetaCell>) {
    val rows = remember(cells) { buildMetaRows(cells) }
    Column {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .height(1.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        ),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                row.forEach { cell ->
                    Column(
                        modifier = if (cell.wide) Modifier.weight(2f) else Modifier.weight(1f),
                    ) {
                        Text(
                            text = cell.label.uppercase(),
                            fontFamily = RobotoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                        Text(
                            text = cell.value,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (cell.wide) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One verdict count chip — the hero's at-a-glance summary (round 88,
 * D-618): "3 Passed · 2 Failed · 2 Not run" before a single block is read.
 */
@Composable
private fun VerdictCountChip(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "$count",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * THE TIME-PROPORTIONAL STAGE BAR (round 87, D-606; honesty pass round 88,
 * D-620). The user's spec: "the bar's length will determine how much time it
 * took on each one of the tasks, and the bar will be colored differently for
 * each one of them."
 *   • a FINISHED segment's width = its ACTUAL durationMs;
 *   • the RUNNING segment's width grows LIVE (its liveMs ticks every 150ms,
 *     so the growth is inherently smooth — no per-segment animation waves,
 *     which used to reflow every neighbor on each step);
 *   • a PENDING segment holds its budget's share but wears its KIND's color
 *     at a faint alpha (the old all-gray strip made "each part a different
 *     color" a pre-run lie);
 *   • tiny segments get a VISIBILITY FLOOR that is RENORMALIZED across the
 *     row (the old 2% clamp stole width from the others and broke the
 *     proportionality it was protecting).
 */
@Composable
private fun MultiStageProgressBar(state: TargetRunState?) {
    val runningStartedAtMs = state?.runningKindStartedAtMs
    var liveMs by remember(runningStartedAtMs) {
        mutableLongStateOf(
            if (runningStartedAtMs != null) System.currentTimeMillis() - runningStartedAtMs else 0L,
        )
    }
    LaunchedEffect(runningStartedAtMs) {
        if (runningStartedAtMs == null) return@LaunchedEffect
        while (true) {
            liveMs = System.currentTimeMillis() - runningStartedAtMs
            delay(150)
        }
    }

    // The segment model: kind + verdict + its TIME (the width's source).
    val segments = ExtensionTestKind.entries.map { kind ->
        val result = state?.results?.get(kind)
        val ms = when {
            result == null || result.status == TestStatus.PENDING -> kind.timeoutMs
            result.status == TestStatus.RUNNING -> liveMs.coerceAtLeast(400L)
            else -> result.durationMs.coerceAtLeast(400L)
        }
        Triple(kind, result, ms.toFloat())
    }
    val totalMs = segments.sumOf { it.third.toDouble() }.toFloat().coerceAtLeast(1f)
    // D-620: floor every fraction for visibility, then RENORMALIZE so the
    // floors never steal width — the row always sums to exactly one bar.
    val minFraction = 0.03f
    val floored = segments.map { (it.third / totalMs).coerceAtLeast(minFraction) }
    val flooredTotal = floored.sum().coerceAtLeast(0.01f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
    ) {
        segments.forEachIndexed { index, (kind, result, ms) ->
            val pending = result == null || result.status == TestStatus.PENDING
            val baseColor = TestingPalette.kindColor(kind)
            val fillColor = when {
                pending -> baseColor.copy(alpha = 0.16f)
                result?.status == TestStatus.SKIPPED -> baseColor.copy(alpha = 0.30f)
                result?.status == TestStatus.FAILED -> baseColor.copy(alpha = 0.55f)
                else -> baseColor
            }
            Box(
                modifier = Modifier
                    .weight(floored[index] / flooredTotal)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(fillColor),
            )
        }
    }
}

/** One timeline bubble — the status-colored circle on the left rail. */
@Composable
private fun TimelineBubble(status: TestStatus) {
    val color = when (status) {
        TestStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        TestStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TestStatus.PASSED -> MaterialTheme.colorScheme.primary
        TestStatus.FAILED -> MaterialTheme.colorScheme.error
        TestStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    if (status == TestStatus.RUNNING) {
        // ROUND 88 (D-621): a RUNNING test's bubble BREATHES — a pulsing ring
        // around a live dot. The old rail mapped running and passed to the
        // exact same solid circle: reading nothing, the two were identical.
        val transition = rememberInfiniteTransition(label = "runningBubble")
        val ringAlpha by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "runningBubbleAlpha",
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(2.dp)
                .clip(CircleShape)
                .border(2.dp, color.copy(alpha = ringAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = ringAlpha)),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(2.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/**
 * THE TRANSIENT VERDICT BANNER (round 87, D-609; round 88, D-616): the
 * just-finished test's verdict slides in, lingers ~2.6 seconds, then slides
 * back out — the view NEVER jumps to the result, the result comes to the
 * user's eye and leaves politely (the round-87 report: "after the test has
 * been performed, it should not automatically move to the very bottom or to
 * the very top… show the results for some time, and then with a smooth
 * animation it should go away"). ROUND 88: the [modifier] lets the caller
 * pin it as an OVERLAY above the list (nothing below it ever shifts).
 */
@Composable
private fun TransientResultBanner(
    banner: Pair<ExtensionTestKind, TestResult>?,
    modifier: Modifier = Modifier,
) {
    // Keep the LAST non-null content so the exit animation has something
    // to show while the row slides away.
    var lastShown by remember { mutableStateOf(banner) }
    val shown = banner ?: lastShown
    SideEffect {
        if (banner != null) lastShown = banner
    }
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(tween(180)) +
            slideInVertically(tween(280, easing = Motion.EasingEmphasized)) { -it },
        exit = fadeOut(tween(320)) +
            slideOutVertically(tween(320, easing = Motion.EasingEmphasized)) { -it },
        modifier = modifier,
    ) {
        if (shown != null) {
            val (kind, result) = shown
            val accent = when (result.status) {
                TestStatus.PASSED -> MaterialTheme.colorScheme.primary
                TestStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val verdictWord = when (result.status) {
                TestStatus.PASSED -> "passed"
                TestStatus.FAILED -> "failed"
                else -> "skipped"
            }
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    TestStatusIcon(result.status, size = 16.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "${kind.label} $verdictWord",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = TestTimeFormat.format(result.durationMs),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One kind's result card on the timeline — THE COLORED BLOCK (round 87,
 * D-607; failure-presentation pass round 88, D-618). The user's spec: the
 * results render INSIDE the block ("inside the pink block, there will be
 * the results… a section inside the pink block"), and the code-window view
 * is REMOVED COMPLETELY. What the card holds:
 *   • the kind's color dot + label + LIVE elapsed while running;
 *   • the verdict message + the detail line as clean text rows — the
 *     detail line shows IN FULL now (the "full details" page ellipsizing
 *     the failure reason at two lines was backwards);
 *   • the search ladder's advanced stats as stat pills (not code lines);
 *   • the RESULTS SECTION — an inset inside the block carrying the payload
 *     (result grid / dossier / episode chips / link rows / live preview),
 *     SKIPPED ENTIRELY when the payload view would render nothing (PING).
 * A not-yet-run card sits faded with ONE fade mechanism (the old stacked
 * card-alpha + surface-alpha double-dim made the labels unreadable).
 */
@Composable
private fun KindDetailCard(
    kind: ExtensionTestKind,
    result: TestResult?,
    state: TargetRunState?,
    modifier: Modifier = Modifier,
    autoPlayPreview: Boolean = true,
) {
    val status = result?.status ?: TestStatus.PENDING
    val pending = status == TestStatus.PENDING
    val running = status == TestStatus.RUNNING
    val accent = TestingPalette.kindColor(kind)
    Surface(
        color = if (pending) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
        } else {
            accent.copy(alpha = 0.10f)
        },
        border = if (pending) {
            // D-618: a pending card keeps a HAIRLINE (design-language
            // consistency) instead of the old double-fade with no border.
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
        } else {
            BorderStroke(1.dp, accent.copy(alpha = 0.30f))
        },
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // ── Header: kind dot + label + (live) duration ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (pending) 0.35f else 0.9f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = kind.label,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (running && state?.runningKindStartedAtMs != null) {
                    LiveElapsedText(
                        startedAtMs = state.runningKindStartedAtMs!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = result?.let { TestTimeFormat.format(it.durationMs) } ?: "—",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (result == null || status == TestStatus.PENDING) {
                Text(
                    text = "Not run yet",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 5.dp, start = 17.dp),
                )
            } else {
                // ── The verdict message + detail as clean text rows ──
                if (result.message.isNotBlank()) {
                    Text(
                        text = result.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (status) {
                            TestStatus.FAILED -> MaterialTheme.colorScheme.error
                            TestStatus.RUNNING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                result.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // The LIVE per-phrase search status (the D-592 pipe).
                if (running && kind == ExtensionTestKind.SEARCH && !state?.runningDetail.isNullOrBlank()) {
                    Text(
                        text = state!!.runningDetail.orEmpty(),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // The search ladder's ADVANCED STATS — stat pills now, not
                // code lines (the round-87 report).
                val winner = result.payload?.searchWinningPhrase
                if (winner != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        SearchStatPill(
                            text = "${result.payload?.searchAttempts ?: 1} " +
                                if ((result.payload?.searchAttempts ?: 1) > 1) "attempts" else "attempt",
                        )
                        SearchStatPill(text = "won on \u201C$winner\u201D")
                    }
                }
                // ── THE RESULTS SECTION — inside the colored block; skipped
                // ENTIRELY when the payload view would render nothing (PING's
                // chips are gone by design — an empty labeled box only made
                // the block look broken; D-622).
                val payload = result.payload
                if (payload != null && kindPayloadHasContent(kind, payload)) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = resultsSectionLabel(kind, payload),
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            )
                            Spacer(Modifier.height(6.dp))
                            KindPayloadView(
                                kind = kind,
                                payload = payload,
                                autoPlayPreview = autoPlayPreview,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The results section's label — one line per kind, with the payload's
 *  count where there is one (the FULL-details page shows how many it has). */
private fun resultsSectionLabel(kind: ExtensionTestKind, payload: TestPayload?): String = when (kind) {
    ExtensionTestKind.SEARCH -> "TOP RESULTS" + payload?.entries?.size?.takeIf { it > 0 }?.let { " · $it" }.orEmpty()
    ExtensionTestKind.HOME_PAGE ->
        "HOME PAGE RESULTS" + payload?.entries?.size?.takeIf { it > 0 }?.let { " · $it" }.orEmpty()
    ExtensionTestKind.DETAILS -> "LOADED DETAILS"
    ExtensionTestKind.EPISODE_LIST ->
        "EPISODES FOUND" + payload?.episodeCount?.takeIf { it > 0 }?.let { " · $it" }.orEmpty()
    ExtensionTestKind.VIDEO_RESOLVE ->
        "RESOLVED LINKS" + payload?.videos?.size?.takeIf { it > 0 }?.let { " · $it" }.orEmpty()
    ExtensionTestKind.STREAM_PLAY -> "LIVE PREVIEW"
    ExtensionTestKind.PING -> "PING"
}

/** One small stat pill (the search ladder's attempts / winning phrase). */
@Composable
private fun SearchStatPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
