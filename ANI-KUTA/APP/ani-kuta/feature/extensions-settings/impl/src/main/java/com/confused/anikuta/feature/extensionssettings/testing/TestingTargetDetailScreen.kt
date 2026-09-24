package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
//  PAGE 4 of 5 — TARGET RESULT DETAIL. The round-87 REWORK (D-605..D-610) —
//  the device report on the round-86 page: the dossier was unclean, the stage
//  bar's equal segments ignored the actual timings, the code-window blocks
//  had to go ("I would most definitely want you to remove it completely"),
//  the results belong INSIDE colored per-test blocks, un-run tests must be
//  visible but faded, and the view must never jump after a test finishes —
//  the result lingers, then slides away smoothly. What this page is now:
//    • the DOSSIER: aligned uppercase labels, wrapping values, hairline
//      dividers — every fact readable;
//    • the TIME-PROPORTIONAL STAGE BAR: each segment's LENGTH is the time
//      its test took (live-growing while it runs, expected budget while
//      pending), and every segment is its TEST KIND's color;
//    • the RESULT BLOCKS: each test card is tinted + bordered with its kind
//      color (the search block is pink), the message/detail are clean text
//      rows, the search ladder's stats are stat pills, and the payload
//      (results grid / dossier / episodes / links / preview) renders inside
//      a clearly separated results section WITHIN the block — no code
//      windows anywhere;
//    • the RUN PILL: bordered tinted pill with an icon badge, wrap-content,
//      never full-width;
//    • the TRANSIENT VERDICT BANNER: when a test completes, its verdict
//      slides in under the actions, LINGERS ~2.6s, then slides away — and
//      nothing on this page auto-scrolls, ever.
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

    // ── THE TRANSIENT VERDICT BANNER (D-609): the just-finished test's
    // verdict slides in, lingers, then slides away. Fresh-terminal kinds
    // (not seen this run) trigger it; a kind returning to RUNNING (a re-run)
    // un-sees it.
    val results = state?.results
    var banner by remember { mutableStateOf<Pair<ExtensionTestKind, TestResult>?>(null) }
    var seenTerminalKinds by remember(targetId) { mutableStateOf(setOf<ExtensionTestKind>()) }
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

                // ── THE DOSSIER HEADER (D-605) ──
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
                            Spacer(Modifier.height(12.dp))
                            // The metadata rows — every fact on its own line,
                            // hairline-divided, values that wrap instead of
                            // clipping mid-package-name.
                            val meta = controller.targetMeta(target)
                            val metaRows = buildList {
                                add("System" to target.ecosystem.displayName())
                                meta.version?.let { add("Version" to it) }
                                meta.pkgName?.let { add("Package" to it) }
                                meta.pluginName?.let { add("Plugin" to it) }
                                meta.isNsfw?.let { add("NSFW" to if (it) "Yes" else "No") }
                                meta.siteUrl?.let { add("Site" to it) }
                                testedAt?.let { add("Last tested" to TESTED_AT_FORMAT.format(Date(it))) }
                            }
                            Column {
                                metaRows.forEachIndexed { index, (label, value) ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp)
                                                .height(1.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = 0.12f),
                                                ),
                                        )
                                    }
                                    MetaRow(
                                        label = label,
                                        value = value,
                                        valueMaxLines = if (label == "Package" || label == "Site") 2 else 1,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            // THE TIME-PROPORTIONAL STAGE BAR — length is
                            // time, color is the test (D-606).
                            MultiStageProgressBar(state = state)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Each segment is one test — its length is the time it took",
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // ── THE RUN PILL — bordered tinted pill, wrap content (D-608) ──
                item(key = "actions") {
                    val canRun = !runActive
                    Surface(
                        color = if (canRun) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (canRun) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                Color.Transparent
                            },
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = canRun) {
                                controller.start(listOf(targetId), target.name)
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
                                        if (canRun) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (canRun) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = if (canRun) "Run all tests" else "Testing in progress…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (canRun) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
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

                // ── THE TRANSIENT VERDICT BANNER (D-609): in → linger → away ──
                item(key = "transient") {
                    TransientResultBanner(banner = banner)
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

/**
 * One dossier row (D-605): a small uppercase muted label in a fixed column,
 * the value beside it — aligned, wrapping (long package names and site URLs
 * get TWO lines instead of a mid-string ellipsis), hairline-divided by the
 * caller.
 */
@Composable
private fun MetaRow(label: String, value: String, valueMaxLines: Int) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = RobotoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.width(88.dp).padding(top = 2.dp),
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * THE TIME-PROPORTIONAL STAGE BAR (round 87, D-606). The user's spec: "the
 * bar's length will determine how much time it took on each one of the tasks,
 * and the bar will be colored differently for each one of them."
 *   • a FINISHED segment's width = its ACTUAL durationMs;
 *   • the RUNNING segment's width grows LIVE against the others (floored at
 *     a visible minimum);
 *   • a PENDING segment holds its budget's share, drawn on the faint track;
 *   • every segment is its TEST KIND's color (TestingPalette.kindColor); a
 *     failure dims its hue instead of repainting it — the sequence stays
 *     readable as a timeline.
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
    ) {
        segments.forEach { (kind, result, ms) ->
            val pending = result == null || result.status == TestStatus.PENDING
            val baseColor = TestingPalette.kindColor(kind)
            val fillColor = when {
                pending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
                result?.status == TestStatus.SKIPPED -> baseColor.copy(alpha = 0.30f)
                result?.status == TestStatus.FAILED -> baseColor.copy(alpha = 0.45f)
                else -> baseColor
            }
            val weightFraction by animateFloatAsState(
                targetValue = (ms / totalMs).coerceIn(0.02f, 1f),
                animationSpec = tween(240, easing = Motion.EasingEmphasized),
                label = "stage-${kind.name}",
            )
            Box(
                modifier = Modifier
                    .weight(weightFraction)
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
 * THE TRANSIENT VERDICT BANNER (round 87, D-609): the just-finished test's
 * verdict slides in below the actions, lingers ~2.6 seconds, then slides
 * back out — the view NEVER jumps to the result, the result comes to the
 * user's eye and leaves politely (the round-87 report: "after the test has
 * been performed, it should not automatically move to the very bottom or to
 * the very top… show the results for some time, and then with a smooth
 * animation it should go away").
 */
@Composable
private fun TransientResultBanner(banner: Pair<ExtensionTestKind, TestResult>?) {
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
 * D-607). The user's spec: the results render INSIDE the block ("inside the
 * pink block, there will be the results… a section inside the pink block"),
 * and the code-window view is REMOVED COMPLETELY. What the card holds:
 *   • the kind's color dot + label + LIVE elapsed while running;
 *   • the verdict message + the detail line as clean text rows;
 *   • the search ladder's advanced stats as stat pills (not code lines);
 *   • the RESULTS SECTION — an inset inside the block carrying the payload
 *     (result grid / dossier / episode chips / link rows / live preview).
 * A not-yet-run card sits faded (the user: "grayed out and a little bit
 * faded out colors"), still visible in the sequence.
 */
@Composable
private fun KindDetailCard(
    kind: ExtensionTestKind,
    result: TestResult?,
    state: TargetRunState?,
    modifier: Modifier = Modifier,
) {
    val status = result?.status ?: TestStatus.PENDING
    val pending = status == TestStatus.PENDING
    val running = status == TestStatus.RUNNING
    val accent = TestingPalette.kindColor(kind)
    Surface(
        color = if (pending) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
        } else {
            accent.copy(alpha = 0.10f)
        },
        border = if (pending) {
            null
        } else {
            BorderStroke(1.dp, accent.copy(alpha = 0.30f))
        },
        shape = RoundedCornerShape(13.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (pending) Modifier.alpha(0.55f) else Modifier),
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                // ── THE RESULTS SECTION — inside the colored block ──
                result.payload?.let { payload ->
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = resultsSectionLabel(kind),
                                fontFamily = RobotoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(6.dp))
                            KindPayloadView(kind = kind, payload = payload)
                        }
                    }
                }
            }
        }
    }
}

/** The results section's label — one line per kind, honest and short. */
private fun resultsSectionLabel(kind: ExtensionTestKind): String = when (kind) {
    ExtensionTestKind.SEARCH -> "TOP RESULTS"
    ExtensionTestKind.HOME_PAGE -> "HOME PAGE RESULTS"
    ExtensionTestKind.DETAILS -> "LOADED DETAILS"
    ExtensionTestKind.EPISODE_LIST -> "EPISODES FOUND"
    ExtensionTestKind.VIDEO_RESOLVE -> "RESOLVED LINKS"
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
