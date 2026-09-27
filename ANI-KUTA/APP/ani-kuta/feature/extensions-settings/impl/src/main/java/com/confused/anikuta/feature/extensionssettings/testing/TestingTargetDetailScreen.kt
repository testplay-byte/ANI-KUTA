package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
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
//
//  ROUND 91 (D-632 — the v1.1.47 device round's full-details pass):
//    • the DOSSIER is COLLAPSED BY DEFAULT — icon + name + chip + chevron;
//      the verdict chips + metadata grid (Language included) expand on tap;
//    • the STAGE BAR moved into its OWN SECTION below the dossier, and its
//      model is EQUAL-BY-DEFAULT: untested segments all share one length,
//      the running one starts smallest and grows live, finished ones hold
//      their actual durations, and every reflow animates;
//    • the RUN PILL is a SOLID muted-accent rectangle that says "Run Tests"
//      and nothing else;
//    • the header carries the EXTENSION'S SETTINGS gear (SourcePreferences
//      through the host) + the app-wide header BLUR;
//    • the TIMELINE's bubbles are CENTERED on their sections, never-run
//      kinds render compact name+color sections (bright, contrasty — no
//      "Not run yet"), and sections EXPAND SMOOTHLY when results arrive;
//    • the VERDICT BANNER is a SOLID elevated surface with a darkening
//      gradient scrim above and below.
//
//  ROUND 93 (D-647 — the v1.1.49 device round's professional pass):
//    • the RESULT CARDS lost their "glowing" full-card color washes — every
//      card is a NEUTRAL surface with a hairline border and a thin LEADING
//      ACCENT edge in the kind's color (the banner language; the kind's hue
//      survives only in the header dot + that edge);
//    • the STAGE TIMINGS section plays the LIVE RUN's motion — one row per
//      STARTED stage (expand+fade reveal), each with a TIME BAR sized
//      against the longest stage shown; the running bar grows live and the
//      earlier bars renormalize (animated) when a new maximum lands; stages
//      that never ran render nothing (the equal-placeholder segmented strip
//      is gone).
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingTargetDetailScreen(
    targetId: Long,
    onBack: () -> Unit,
    // ROUND 91 (D-632): the extension's OWN settings page — the header's
    // gear opens SourcePreferences for this target's source ("when the user
    // clicks on that settings option, he will be led to the settings page
    // of that specific extension").
    onOpenSettings: () -> Unit,
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
                // ROUND 91 (D-632): the extension's settings — the gear in the
                // header's actions slot routes to this source's own settings
                // page (wired by the host to SourcePreferences).
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Extension settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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

                    // ── THE DOSSIER HEADER (D-605; compressed round 88, D-617;
                    // ROUND 91, D-632 — COLLAPSED BY DEFAULT): the hero opens
                    // as the SUMMARY — icon, name, status chip — and the full
                    // details (verdict chips + metadata grid) expand on tap
                    // ("they should not be shown in the expanded version every
                    // time. When the user clicks on it, only then will it
                    // expand and the full details will show").
                    item(key = "hero") {
                        var heroExpanded by rememberSaveable { mutableStateOf(false) }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { heroExpanded = !heroExpanded }
                                    .padding(14.dp),
                            ) {
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
                                        // ROUND 91 (D-632): the ecosystem · lang
                                        // subtitle is GONE from the summary — the
                                        // language lives in the expanded meta
                                        // grid now, where details belong.
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    // D-619: a queued target reads "Queued" here,
                                    // exactly like it does on the list page.
                                    TargetStatusChip(state, queued = inLiveRun)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (heroExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.KeyboardArrowDown
                                        },
                                        contentDescription = if (heroExpanded) {
                                            "Hide details"
                                        } else {
                                            "Show details"
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = heroExpanded,
                                    enter = fadeIn(tween(200)) +
                                        expandVertically(tween(260, easing = Motion.EasingEmphasized)),
                                    exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
                                ) {
                                    Column {
                                        // ── THE VERDICT SUMMARY (round 88, D-618)
                                        // — one glance says how the chain went
                                        // before a single block is read.
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
                                        // ROUND 91 (D-632): the LANGUAGE lives here
                                        // now (its one honest home on this page).
                                        val meta = controller.targetMeta(target)
                                        val metaCells = buildList {
                                            meta.version?.let { add(MetaCell("Version", it, wide = false)) }
                                            meta.pluginName?.let { add(MetaCell("Plugin", it, wide = false)) }
                                            target.lang?.let { add(MetaCell("Language", it.uppercase(), wide = false)) }
                                            meta.isNsfw?.let {
                                                add(MetaCell("NSFW", if (it) "Yes" else "No", wide = false))
                                            }
                                            testedAt?.let {
                                                add(MetaCell("Last tested", TESTED_AT_FORMAT.format(Date(it)), wide = false))
                                            }
                                            meta.pkgName?.let { add(MetaCell("Package", it, wide = true)) }
                                            meta.siteUrl?.let { add(MetaCell("Site", it, wide = true)) }
                                        }
                                        MetaGrid(cells = metaCells)
                                    }
                                }
                            }
                        }
                    }

                    // ── ROUND 91 (D-632) + ROUND 93 (D-647) + ROUND 94
                    // (D-652): THE STAGE TIMINGS — BARS ONLY. The v1.1.50
                    // report: "all the stage timings were shown at the top,
                    // all together combined in a list view, which was not
                    // good… you can only show the bars. You are not supposed
                    // to show any details there or anything like that. Only
                    // the bars will be shown in parallel to each other, very
                    // close to each other, just a slight padding on them
                    // with each other… There won't be any text to them,
                    // nothing, only bars." The motion semantics stay (the
                    // live run's machine): a bar renders ONLY once its stage
                    // STARTED (expand+fade reveal), its length is its time
                    // against the LONGEST stage shown, the running bar grows
                    // live (150ms ticker), and earlier bars RENORMALIZE
                    // (animated) when a new maximum lands — but every label,
                    // dot, duration and caption is GONE. ──
                    item(key = "stage-bar") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                StageTimingBars(state = state)
                            }
                        }
                    }

                    // ── THE RUN PILL (D-608; live-run aware round 88, D-619;
                    // ROUND 91, D-632 — SOLID + PLAIN): three honest states:
                    // THIS target is queued/running in the live session → a
                    // "View live run" door; a DIFFERENT run is live → disabled
                    // with the truth on the label; otherwise a SOLID
                    // MUTED-ACCENT rectangle ("a solid color to it, but not a
                    // way too bright color from the accent color" — the
                    // accent lerped ~20% toward the background) that says
                    // "Run Tests" and nothing else (the "· 7 tests" trailer
                    // is gone: "it does not need to show seven tests or other
                    // things like that. It should only say the text Run
                    // Tests").
                    item(key = "actions") {
                        val canRunHere = !runActive
                        val watchingThisRun = inLiveRun
                        // D-632: the solid-but-muted accent — never full neon.
                        val runFill = lerp(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.background,
                            0.20f,
                        )
                        Surface(
                            color = when {
                                // D-624: the live-run accent is the PALETTE's sky
                                // (TestingPalette.SystemB) — the M3 baseline
                                // `tertiary` is the never-themed pale pink the
                                // round-87 report already rejected once.
                                watchingThisRun -> TestingPalette.SystemB.copy(alpha = 0.14f)
                                canRunHere -> runFill
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = when {
                                watchingThisRun -> BorderStroke(1.dp, TestingPalette.SystemB.copy(alpha = 0.5f))
                                canRunHere -> null
                                else -> BorderStroke(1.dp, Color.Transparent)
                            },
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = if (canRunHere) 2.dp else 0.dp,
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
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = when {
                                        watchingThisRun -> "View live run"
                                        canRunHere -> "Run Tests"
                                        else -> "A run is in progress…"
                                    },
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        watchingThisRun -> TestingPalette.SystemB
                                        canRunHere -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
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
                                    // The LEFT RAIL (ROUND 91, D-632 — the
                                    // CENTERED rail): the bubble sits at each
                                    // section's vertical CENTER, with the
                                    // connector halves filling above and
                                    // below — consecutive bubbles connect
                                    // through their midpoints, the first row
                                    // draws no line above its dot and the
                                    // last none below ("the timeline dots
                                    // need to be centered alongside with it").
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(26.dp).fillMaxHeight(),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .weight(1f)
                                                .background(
                                                    if (index == 0) Color.Transparent else railColor,
                                                ),
                                        )
                                        TimelineBubble(result?.status ?: TestStatus.PENDING, TestingPalette.kindColor(kind))
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .weight(1f)
                                                .background(
                                                    if (isLast) Color.Transparent else railColor,
                                                ),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    // ROUND 91 (D-632): the section wrapper
                                    // ANIMATES its size — a compact pending
                                    // section EXPANDS smoothly into its full
                                    // result card when the run reaches it
                                    // ("after the test has been completed,
                                    // then the section should expand
                                    // smoothly, but it does not expand
                                    // smoothly").
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .animateContentSize(
                                                animationSpec = tween(
                                                    Motion.DurationStandard,
                                                    easing = Motion.EasingEmphasized,
                                                ),
                                            ),
                                    ) {
                                        if (result == null || result.status == TestStatus.PENDING) {
                                            // ROUND 91 (D-632): the DEFAULT
                                            // (not-yet-run) section — SMALL:
                                            // the test's name + its theme
                                            // color, nothing else ("it should
                                            // not say the text Not Run Yet or
                                            // anything like that. It should
                                            // only show a small section with
                                            // the name of the test and the
                                            // theme color… the theme colors
                                            // need to be somewhat on a
                                            // brighter side, like contrasty").
                                            // The dot is FULL-strength kind
                                            // color — never dulled.
                                            val accent = TestingPalette.kindColor(kind)
                                            Surface(
                                                color = accent.copy(alpha = 0.07f),
                                                border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(
                                                        horizontal = 10.dp,
                                                        vertical = 8.dp,
                                                    ),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(9.dp)
                                                            .clip(CircleShape)
                                                            .background(accent),
                                                    )
                                                    Spacer(Modifier.width(9.dp))
                                                    Text(
                                                        text = kind.label,
                                                        fontFamily = RobotoFamily,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        } else {
                                            KindDetailCard(
                                                kind = kind,
                                                result = result,
                                                state = state,
                                                autoPlayPreview = inLiveRun,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                if (!isLast) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // ROUND 91 (D-632): the header BLUR — the app-wide §2.2
                // language, missing on this screen (the device report
                // caught it), pinned over the list's top edge.
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

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
 * ROUND 93 (D-647) → ROUND 94 (D-652) → ROUND 95 (D-655): THE STAGE-TIMING
 * BARS — bars, their in-bar durations, and the one-line completion verdict.
 * The v1.1.50 report retired the labeled row list ("There won't be any
 * text to them, nothing, only bars"); each STARTED stage is one thin bar
 * stacked right against its neighbors with just a hair of padding (3dp),
 * sized against the LONGEST stage shown. The v1.1.51 report then added the
 * two read-outs back in a STRICTLY visual form: "alongside with the bar, we
 * could show small text inside the bars themselves, on the right side, in a
 * way that it is clearly visible. And what it will show is the duration of
 * each one of those bars" — so every bar now carries a small SCRIM CHIP at
 * its right edge holding the duration (the translucent dark pill behind
 * the white 9sp text guarantees contrast over ANY kind color AND over the
 * bare track of a short bar — no clipping, no threshold jumps while the
 * running bar grows). And below the stack: ONE line, never wrapped —
 * "All tests successfully completed in 1m 42s" (a healthy chain) or
 * "All tests failed in 23 s" (any failure), the honest totals. The D-637
 * motion survives: the bar reveals (expand+fade) the moment its stage
 * starts, the running bar GROWS LIVE (150ms ticker, its chip ticking with
 * it), and every earlier bar RENORMALIZES (animated 350ms) whenever a new
 * maximum lands. Never-run stages render nothing; an untested target shows
 * one quiet empty line.
 */
@Composable
private fun StageTimingBars(state: TargetRunState?) {
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

    fun stageMs(kind: ExtensionTestKind): Long {
        val result = state?.results?.get(kind) ?: return 0L
        return when (result.status) {
            TestStatus.RUNNING -> liveMs.coerceAtLeast(STAGE_MIN_MS)
            TestStatus.PENDING -> 0L
            else -> result.durationMs.coerceAtLeast(STAGE_MIN_MS)
        }
    }
    val maxStageMs = ExtensionTestKind.entries
        .maxOf { stageMs(it) }
        .coerceAtLeast(STAGE_MIN_MS)

    // ── D-655: THE COMPLETION VERDICT LINE. One line, never wrapped: the
    // healthy chain states its total ("All tests successfully completed
    // in…"), any failure states the time it failed in ("All tests failed
    // in…"). It lands once the run has SETTLED (live runs stay quiet until
    // their verdict is final); aborted and never-run targets show nothing.
    val decidedResults = state?.results?.values
        ?.filter { it.status != TestStatus.PENDING && it.status != TestStatus.RUNNING }
        .orEmpty()
    val failedCount = decidedResults.count { it.status == TestStatus.FAILED }
    val settledAndDecided = state != null && !state.isRunning && decidedResults.isNotEmpty()
    val completionText: String? = when {
        settledAndDecided && failedCount > 0 -> "All tests failed in"
        settledAndDecided && state?.isHealthy == true -> "All tests successfully completed in"
        else -> null
    }
    val completionTotalMs = decidedResults.sumOf { it.durationMs }

    var anyShown = false
    Column(
        // D-652: "very close to each other, just a slight padding on them
        // with each other" — a 3dp hair between the bars, nothing more.
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.animateContentSize(
            animationSpec = tween(Motion.DurationStandard, easing = Motion.EasingEmphasized),
        ),
    ) {
        ExtensionTestKind.entries.forEach { kind ->
            val kindResult = state?.results?.get(kind)
            val started = kindResult != null && kindResult.status != TestStatus.PENDING
            if (started) anyShown = true
            AnimatedVisibility(
                visible = started,
                enter = fadeIn(tween(180)) +
                    expandVertically(tween(220, easing = Motion.EasingEmphasized)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                val fraction by animateFloatAsState(
                    targetValue = (stageMs(kind).toFloat() / maxStageMs).coerceIn(0.04f, 1f),
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "detailStageBarWidth",
                )
                val baseColor = TestingPalette.kindColor(kind)
                // The verdict still reads at a glance without any text: a
                // failed bar dims, a skipped bar fades to a whisper.
                val fillColor = when (kindResult?.status) {
                    TestStatus.FAILED -> baseColor.copy(alpha = 0.55f)
                    TestStatus.SKIPPED -> baseColor.copy(alpha = 0.30f)
                    else -> baseColor
                }
                // D-655: the bar grew to hold its duration chip (7dp → 15dp).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(fillColor, RoundedCornerShape(50)),
                    )
                    // THE DURATION CHIP — the bar's own time, "inside the
                    // bars themselves, on the right side… clearly visible".
                    // The translucent dark pill guarantees the white text
                    // reads over the light kind colors (sky/amber/lime), over
                    // the dimmed failed/skipped fills, and over the bare
                    // track of a short bar — one anchored position, so the
                    // running bar's live growth never makes it jump.
                    Surface(
                        color = Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp),
                    ) {
                        Text(
                            text = TestTimeFormat.format(stageMs(kind)),
                            fontFamily = RobotoFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (!anyShown) {
            Text(
                text = "Nothing timed yet",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        } else if (completionText != null) {
            // D-655: the ONE-LINE verdict — never wrapped, never a second
            // line ("it will not be formatted to the next line").
            Text(
                text = "$completionText ${TestTimeFormat.format(completionTotalMs)}",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (failedCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** The visibility floor for live/finished weights (D-632; the D-637 twin). */
private const val STAGE_MIN_MS = 250L

/** One timeline bubble — the status-colored circle on the left rail.
 *  ROUND 95 (D-656): "the timeline bubbles should be theme colored" — the
 *  bubble now wears its KIND's color from the testing palette (the same
 *  identity hue as the stage bar + the card heading). FAILED keeps the
 *  error red and SKIPPED dims to a whisper — the verdict must stay legible
 *  (the D-594 doctrine: a failure never masquerades as a system color). */
@Composable
private fun TimelineBubble(status: TestStatus, kindColor: Color) {
    val color = when (status) {
        TestStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        TestStatus.RUNNING -> kindColor
        TestStatus.PASSED -> kindColor
        TestStatus.FAILED -> MaterialTheme.colorScheme.error
        TestStatus.SKIPPED -> kindColor.copy(alpha = 0.35f)
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
 * THE TRANSIENT VERDICT BANNER (round 87, D-609; round 88, D-616; ROUND 91,
 * D-632 — the SOLID pass): the just-finished test's verdict slides in,
 * lingers ~2.6 seconds, then slides back out — the view NEVER jumps to the
 * result, the result comes to the user's eye and leaves politely. The
 * round-91 report: "its background should not be transparent or anything
 * like that, because it would be hard to view it. And also it should have
 * some darkening effect around it so that it is clearly visible" — so the
 * banner itself is now a SOLID elevated surface (surfaceContainerHigh +
 * shadow + hairline + the accent as a leading edge), and a soft DARKENING
 * GRADIENT fades in above and below it, scrimming whatever list content
 * slides underneath.
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
            Column(modifier = Modifier.fillMaxWidth()) {
                // The DARKENING SCRIM — a soft gradient that fades toward the
                // banner from above, so the solid card reads over ANY list
                // content sliding beneath it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                            ),
                        ),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .height(IntrinsicSize.Min),
                    ) {
                        // The accent leading edge — the verdict's color as a
                        // solid bar, readable on the solid surface.
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(accent),
                        )
                        Spacer(Modifier.width(10.dp))
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
                // The scrim's lower half.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * One kind's result card on the timeline (round 87, D-607; failure pass
 * round 88, D-618; ROUND 91, D-632; ROUND 93, D-647; ROUND 95, D-656 —
 * THE RESULTS-SECTION REDESIGN, the v1.1.51 report's item-by-item spec).
 *
 * THE NEW ANATOMY (the user's exact words): "At the top it will give the
 * heading… the heading needs to be made a little bit bigger, and it should
 * be in the theme color, and there does not need to be a dot on the left
 * side of it. And on the right side of it, it will show the actual URL /
 * count… and on the very right side of it… the total duration in which it
 * completed or in which it failed." So:
 *
 *   ┌──────────────────────────────────────────────────────┐
 *   │ Ping        https://example.com            820 ms    │  ← the header
 *   ├──────────────────────────────────────────────────────┤
 *   │ Responded, HTTP 200                                 │  ← the body
 *   │ [ RESULTS / LOADED DETAILS / EPISODES FOUND / … ]    │
 *   └──────────────────────────────────────────────────────┘
 *
 *   • the heading: 15sp ExtraBold in the KIND's color, NO dot, no leading
 *     accent edge ("on the left side of them, it does not need to show the
 *     theme colored line or such" — the D-647 3dp edge is gone);
 *   • the middle read-out per kind — PING the pinged URL, HOME "15 entries",
 *     SEARCH "10, 4, <first name>" (the attempts number only when a second
 *     attempt was made, never the category, the name compresses), EPISODES
 *     "13 episodes" (locale-grouped: "1,000 episodes"), VIDEO "5 links";
 *   • the duration on the VERY right (live while running);
 *   • the body: a PASSED card carries ONLY its payload section (top-3 lists
 *     + Expand, the details dossier, the stream facts + preview); PING keeps
 *     its one "Responded, HTTP n" line; FAILED/SKIPPED cards keep their
 *     reason lines in full; the search stat pills ("won on…") are GONE.
 */
@Composable
private fun KindDetailCard(
    kind: ExtensionTestKind,
    result: TestResult,
    state: TargetRunState?,
    modifier: Modifier = Modifier,
    autoPlayPreview: Boolean = true,
) {
    val status = result.status
    val running = status == TestStatus.RUNNING
    val accent = TestingPalette.kindColor(kind)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        shape = RoundedCornerShape(13.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ── THE HEADER (D-656): [HEADING] [middle read-out] [duration] ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // "the heading needs to be made a little bit bigger, and it
                // should be in the theme color, and there does not need to be
                // a dot on the left side of it."
                Text(
                    text = kind.label,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                kindHeaderInfo(kind, result)?.let { info ->
                    Text(
                        text = info,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                // "on the very right side of it… the total duration in which
                // it completed or in which it failed" — LIVE while running.
                if (running && state?.runningKindStartedAtMs != null) {
                    LiveElapsedText(
                        startedAtMs = state.runningKindStartedAtMs!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = TestTimeFormat.format(result.durationMs),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── THE BODY ──
            // A FAILED or SKIPPED card keeps its reason lines IN FULL (the
            // "full details" page's whole point); a PASSED card carries only
            // its payload section — PING keeps its one honest line.
            if (status == TestStatus.FAILED || status == TestStatus.SKIPPED) {
                if (result.message.isNotBlank()) {
                    Text(
                        text = result.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status == TestStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                result.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    // PING's detail IS the URL — already in the header. The
                    // other kinds' details stay (they carry the failure's why).
                    if (kind != ExtensionTestKind.PING) {
                        Text(
                            text = detail,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            } else if (status == TestStatus.PASSED && kind == ExtensionTestKind.PING) {
                // "below it it will show the normal details like responded,
                // HTTP 200 or other… It would only say responded, HTTP and
                // then the number" (no duration — that is the header's now).
                if (result.message.isNotBlank()) {
                    Text(
                        text = result.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
            // The LIVE per-phrase search status (the D-592 pipe) — the one
            // RUNNING body line that stays.
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
            // ── THE RESULTS SECTION — the payload (D-656: the counts live in
            // the HEADER now; the section label is the plain name) — skipped
            // ENTIRELY when the payload view would render nothing (PING).
            val payload = result.payload
            if (payload != null && kindPayloadHasContent(kind, payload)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = resultsSectionLabel(kind),
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

/**
 * D-656: the header's MIDDLE read-out — the one-line fact each kind carries
 * between its heading and its duration. PING shows the URL it tried; HOME
 * shows its entry total ("15 entries", never "30 entries on the home page");
 * SEARCH shows "10, 4, <name>" — the raw total, the ATTEMPT NUMBER ONLY when
 * a second attempt was made ("it will also not show the total number of
 * attempts unless second attempts were made"), and the first result's name
 * (never the category — "it will not say which kind of content it was"),
 * compressed by the row's ellipsis when there is not enough space; EPISODES
 * shows the locale-grouped total ("13 episodes", "1,000 episodes"); VIDEO
 * shows its link total. DETAILS and STREAM_PLAY carry no middle fact — their
 * payloads are the body.
 */
private fun kindHeaderInfo(kind: ExtensionTestKind, result: TestResult): String? {
    val payload = result.payload
    return when (kind) {
        ExtensionTestKind.PING -> result.detail?.takeIf { it.isNotBlank() }
        ExtensionTestKind.HOME_PAGE -> {
            val count = payload?.entryCount ?: payload?.entries?.size ?: 0
            "$count entries"
        }
        ExtensionTestKind.SEARCH -> buildString {
            append(payload?.entryCount ?: payload?.entries?.size ?: 0)
            val attempts = payload?.searchAttempts ?: 1
            if (attempts > 1) append(", $attempts")
            payload?.entries?.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let { name ->
                append(", $name")
            }
        }
        ExtensionTestKind.DETAILS -> null
        ExtensionTestKind.EPISODE_LIST -> {
            val count = payload?.episodeCount ?: payload?.episodes?.size ?: 0
            "%,d episodes".format(count)
        }
        ExtensionTestKind.VIDEO_RESOLVE -> {
            val count = payload?.videoCount ?: payload?.videos?.size ?: 0
            "$count links"
        }
        ExtensionTestKind.STREAM_PLAY -> null
    }
}

/**
 * The results section's label (D-656: the count suffixes are GONE — the
 * totals live in the card headers now).
 */
private fun resultsSectionLabel(kind: ExtensionTestKind): String = when (kind) {
    ExtensionTestKind.SEARCH -> "RESULTS"
    ExtensionTestKind.HOME_PAGE -> "RESULTS"
    ExtensionTestKind.DETAILS -> "LOADED DETAILS"
    ExtensionTestKind.EPISODE_LIST -> "EPISODES FOUND"
    ExtensionTestKind.VIDEO_RESOLVE -> "RESOLVED LINKS"
    ExtensionTestKind.STREAM_PLAY -> "LIVE PREVIEW"
    ExtensionTestKind.PING -> "PING"
}
