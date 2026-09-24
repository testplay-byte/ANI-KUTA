package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 4 of 5 — TARGET RESULT DETAIL. The round-86 REWORK (D-591) — the
//  device report called the old layout "very bad... really bad", so this page
//  is rebuilt in the house design language:
//    • the TOP shows the extension's details in a well-formatted dossier
//      (icon, name, verdict chip, version / package / plugin / NSFW / site);
//    • a MULTI-STAGE PROGRESS BAR: seven stages, one per test, each stage
//      filling LIVE while its test runs (the stage's own wall clock against
//      its budget) and snapping to its verdict color when done;
//    • the run button is a bespoke pill — a DIFFERENT color (tertiary), NOT
//      full width, no stock Material button anywhere;
//    • the results are a TIMELINE: a left rail of connected bubbles (one per
//      test, status-colored) with the per-test cards on the right;
//    • the cards DROP the static descriptions and the duplicated bottom
//      metric chips, and render their live detail lines inside a CODE-WINDOW
//      block (the "coding kind of window vibe") — the search card carries the
//      ladder's advanced stats and the live per-phrase status.
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

                // ── THE DOSSIER HEADER (well-formatted extension details) ──
                item(key = "hero") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(18.dp),
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
                                TargetStatusChip(state)
                            }
                            Spacer(Modifier.height(10.dp))
                            // The metadata rows — every fact on its own line.
                            val meta = controller.targetMeta(target)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                MetaRow("System", target.ecosystem.displayName())
                                meta.version?.let { MetaRow("Version", it) }
                                meta.pkgName?.let { MetaRow("Package", it) }
                                meta.pluginName?.let { MetaRow("Plugin", it) }
                                meta.isNsfw?.let { MetaRow("NSFW", if (it) "Yes" else "No") }
                                meta.siteUrl?.let { MetaRow("Site", it) }
                                testedAt?.let {
                                    MetaRow("Last tested", TESTED_AT_FORMAT.format(Date(it)))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // THE MULTI-STAGE PROGRESS BAR — seven stages, live.
                            MultiStageProgressBar(state = state)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Each stage is one test — it fills while the test runs",
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // ── THE RUN PILL — bespoke, tertiary, NOT full width ──
                item(key = "actions") {
                    Surface(
                        color = if (runActive) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !runActive) {
                                controller.start(listOf(targetId), target.name)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (runActive) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onTertiary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (runActive) {
                                    "Testing in progress…"
                                } else {
                                    "Run all tests for this source"
                                },
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (runActive) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onTertiary
                                },
                            )
                        }
                    }
                }

                // ── THE TIMELINE — one item so the left rail connects ──
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
                                    modifier = Modifier.weight(1f),
                                    timelinePadding = !isLast,
                                )
                            }
                            if (!isLast) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TESTED_AT_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
private fun MetaRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.width(78.dp),
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * THE MULTI-STAGE PROGRESS BAR (D-591): seven segments — one per test. A
 * finished stage fills with its verdict color; the RUNNING stage fills LIVE
 * (its own wall clock against its budget, smoothly animated, capped just
 * under full so the bar never "lies" a completion); pending stages stay on
 * the track color.
 */
@Composable
private fun MultiStageProgressBar(state: TargetRunState?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp),
    ) {
        ExtensionTestKind.entries.forEach { kind ->
            val result = state?.results?.get(kind)
            val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            val fillColor = when (result?.status) {
                TestStatus.PASSED -> MaterialTheme.colorScheme.primary
                TestStatus.FAILED -> MaterialTheme.colorScheme.error
                TestStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(track),
            ) {
                val running = result?.status == TestStatus.RUNNING
                var elapsedMs by remember(state?.runningKindStartedAtMs) {
                    mutableLongStateOf(
                        if (running && state?.runningKindStartedAtMs != null) {
                            System.currentTimeMillis() - state.runningKindStartedAtMs
                        } else {
                            0L
                        },
                    )
                }
                if (running && state?.runningKindStartedAtMs != null) {
                    LaunchedEffect(state.runningKindStartedAtMs) {
                        while (true) {
                            elapsedMs = System.currentTimeMillis() - state.runningKindStartedAtMs
                            delay(150)
                        }
                    }
                }
                val targetFraction = when (result?.status) {
                    TestStatus.PASSED, TestStatus.FAILED, TestStatus.SKIPPED -> 1f
                    TestStatus.RUNNING -> {
                        val budget = kind.timeoutMs.coerceAtLeast(1L)
                        (elapsedMs.toFloat() / budget).coerceIn(0.05f, 0.9f)
                    }
                    else -> 0f
                }
                val fraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(if (running) 160 else 320),
                    label = "stage-${kind.name}",
                )
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(fillColor),
                    )
                }
            }
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

/**
 * One kind's result card on the timeline (D-591 rework): header row (icon +
 * label + duration), the LIVE detail lines inside a CODE-WINDOW block, then
 * the payload. The static per-kind description is GONE; the duplicated
 * bottom metric chips are GONE (the D-592 report).
 */
@Composable
private fun KindDetailCard(
    kind: ExtensionTestKind,
    result: TestResult?,
    state: TargetRunState?,
    modifier: Modifier = Modifier,
    timelinePadding: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TestStatusIcon(result?.status ?: TestStatus.PENDING, size = 16.dp)
                Spacer(Modifier.width(9.dp))
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
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The CODE-WINDOW: the test's own words, monospace on dark.
            // The search card adds the ladder's ADVANCED STATS (how many
            // phrases were tried, which one answered) and, while running,
            // the LIVE per-phrase status.
            val running = result?.status == TestStatus.RUNNING
            val liveLine = if (running && kind == ExtensionTestKind.SEARCH) {
                state?.runningDetail
            } else {
                null
            }
            val codeLines = buildList {
                if (!liveLine.isNullOrBlank()) add(liveLine)
                if (result == null) {
                    add("status: not run yet (budget ${TestTimeFormat.format(kind.timeoutMs)})")
                } else {
                    when (result.status) {
                        TestStatus.RUNNING -> if (liveLine.isNullOrBlank()) add("status: running…")
                        else -> {
                            if (result.message.isNotBlank()) add("> ${result.message}")
                            result.detail?.takeIf { it.isNotBlank() }?.let { add("  ${it}") }
                        }
                    }
                    // The search advanced stats (D-592).
                    result.payload?.searchWinningPhrase?.let { winner ->
                        val attempts = result.payload?.searchAttempts
                        add("attempts: ${attempts ?: 1}" + if (attempts != null && attempts > 1) " → won on #$attempts" else "")
                        add("winning phrase: \u201C$winner\u201D")
                    }
                }
            }
            if (codeLines.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                CodeWindowBlock(
                    lines = codeLines,
                    accentLines = if (!liveLine.isNullOrBlank() || running) setOf(0) else emptySet(),
                )
            }

            // The ACTUAL data — 3×2 result grid, dossier, episode chips,
            // formatted server rows, the live stream preview.
            result?.payload?.let { payload ->
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    KindPayloadView(kind = kind, payload = payload)
                }
            }
        }
    }
}
