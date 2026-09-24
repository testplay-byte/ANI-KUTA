package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.feature.extensionssettings.matchesSearch

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 2 of 5 — PER-SYSTEM TARGET LIST (round 84, D-583; reworked round 85).
//
//  THE ROUND-85 DEVICE REPORT, and what this page does about it:
//    • "when I clicked on Run Tests, it ran the tests on a completely new
//      screen, which was not good. It should run the tests on that same
//      screen" — every run entry now calls the app-scoped CONTROLLER
//      DIRECTLY (in-place): rows animate queued → testing → verdict right
//      here. The dedicated run page remains reachable as a deep view.
//    • "if the user has long-pressed and has opened up the selections, then
//      single clicking will not open up the details" — while a selection is
//      open, TAP toggles selection (never expand); with no selection open,
//      tap expands as before.
//    • "on the very right side of each one of the extensions, there is no
//      need to show the arrow" — the trailing chevron is GONE.
//    • "the options for the test and the full details should be shown at the
//      top… in this screen only the simple tests and their time duration
//      should be shown" — the expanded body leads with the actions and shows
//      ONLY the compact kind/duration rows (no message text).
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun TestingTargetListScreen(
    ecosystem: String,
    onBack: () -> Unit,
    onOpenRun: (String) -> Unit,
    onOpenTarget: (Long) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { ExtensionTestRunController.get(context) }
    val targets by controller.targets.collectAsState()
    val session by controller.session.collectAsState()

    val eco = ecosystem.toEcosystem() ?: TestEcosystem.ANIYOMI
    val ecoTargets = targets.filter { it.ecosystem == eco }

    // Persisted verdicts, refreshed as the live run advances.
    var storedRuns by remember { mutableStateOf(controller.resultStore.loadAll()) }
    val testedTick = session?.testedCount ?: -1
    androidx.compose.runtime.LaunchedEffect(testedTick) {
        if (testedTick >= 0) storedRuns = controller.resultStore.loadAll()
    }

    fun stateFor(id: Long): TargetRunState? {
        val live = session?.states?.get(id)
        // D-589 (round 86): a FRESH session state (not started yet) must not
        // mask the PREVIOUS verdict — the run's queue resets every target to
        // an empty state, so until a target's turn arrives its stored verdict
        // stays the source of truth for the sorting and the chips.
        if (live != null && (live.finished || live.isRunning || live.results.isNotEmpty())) {
            return live
        }
        return storedRuns[id]?.let { storedToRunState(it) }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }

    val matched = if (query.isBlank()) {
        ecoTargets
    } else {
        ecoTargets.filter { matchesSearch(it.name, query.trim()) }
    }

    // D-589 (round 86) — the VERDICT SORT (the user's spec): PASSED first
    // (alphabetical inside the bucket), then the untested, then the FAILED —
    // and inside the failed bucket the ones that failed in FEWER things sit
    // higher, so "if it failed in a lot of things it is much further down,
    // even though it was up in the alphabetical order". The store refresh
    // on every tested target makes the order evolve LIVE during a run, and
    // Modifier.animateItem() glides the rows into their new places.
    val filtered = matched
        .map { target ->
            val state = stateFor(target.id)
            val bucket = when {
                state == null || !state.finished -> 1 // untested
                state.isHealthy -> 0 // passed
                else -> 2 // failed (incl. user-aborted — not a passing verdict)
            }
            Triple(target, bucket, state?.failedCount ?: 0)
        }
        .sortedWith { (a, aBucket, aFails), (b, bBucket, bFails) ->
            when {
                aBucket != bBucket -> aBucket - bBucket
                aBucket == 2 && aFails != bFails -> aFails - bFails
                else -> a.name.lowercase().compareTo(b.name.lowercase())
            }
        }
        .map { it.first }

    // The three run scopes (D-589): eco-scoped id sets derived from the same
    // stateFor verdicts the sorting reads.
    val failedIds = matched.mapNotNull { target ->
        val state = stateFor(target.id)
        if (state != null && state.finished && !state.isHealthy) target.id else null
    }
    val passedIds = matched.mapNotNull { target ->
        val state = stateFor(target.id)
        if (state != null && state.isHealthy) target.id else null
    }

    val runActive = session?.phase == RunPhase.RUNNING
    val allIds = ecoTargets.map { it.id }.toSet()

    // The queue-aware "queued" flag: this ecosystem's rows that are waiting
    // in a live run's queue (ahead of the cursor) — drives the Queued chip.
    val queuedIds: Set<Long> = session?.let { s ->
        if (s.phase != RunPhase.RUNNING) {
            emptySet()
        } else {
            val cursor = s.cursor.coerceAtLeast(0)
            s.queue.drop(cursor).filter { it != s.currentTargetId }.toSet()
        }
    } ?: emptySet()

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "${eco.displayName()} targets",
                collapsed = collapsed,
                onBack = onBack,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ── Search field ──
                item(key = "search") {
                    TestingListSearchField(
                        query = query,
                        onQueryChange = { query = it },
                    )
                }

                // ── Controls row: count + select-all ──
                item(key = "controls") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "${filtered.size} source${if (filtered.size == 1) "" else "s"}",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = if (selectedIds.containsAll(allIds) && allIds.isNotEmpty()) "Deselect all" else "Select all",
                            tint = if (allIds.isNotEmpty() && selectedIds.containsAll(allIds)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedIds = if (allIds.isNotEmpty() && selectedIds.containsAll(allIds)) {
                                        selectedIds - allIds
                                    } else {
                                        selectedIds + allIds
                                    }
                                    if (selectedIds.isNotEmpty()) HapticHelper.lightTick(context)
                                }
                                .padding(7.dp),
                        )
                    }
                }

                // ── The THREE RUN SCOPES (D-589): Run all / Run failed / Run passed ──
                item(key = "run-scopes") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    ) {
                        RunScopePill(
                            text = "Run all",
                            enabled = !runActive && filtered.isNotEmpty(),
                            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            content = MaterialTheme.colorScheme.primary,
                            onClick = {
                                controller.start(
                                    filtered.map { it.id },
                                    "${eco.displayName()} targets",
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        RunScopePill(
                            text = if (failedIds.isEmpty()) "Run failed" else "Run failed · ${failedIds.size}",
                            enabled = !runActive && failedIds.isNotEmpty(),
                            container = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            content = MaterialTheme.colorScheme.error,
                            onClick = { controller.start(failedIds, "${eco.displayName()} failed") },
                            modifier = Modifier.weight(1.15f),
                        )
                        RunScopePill(
                            text = if (passedIds.isEmpty()) "Run passed" else "Run passed · ${passedIds.size}",
                            enabled = !runActive && passedIds.isNotEmpty(),
                            container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                            content = MaterialTheme.colorScheme.tertiary,
                            onClick = { controller.start(passedIds, "${eco.displayName()} passed") },
                            modifier = Modifier.weight(1.15f),
                        )
                    }
                }

                // ── The rows ──
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (query.isBlank()) {
                                "No ${eco.displayName()} sources installed yet."
                            } else {
                                "No source matches \u201C${query.trim()}\u201D."
                            },
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 28.dp, horizontal = 4.dp),
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { target ->
                        TargetListRow(
                            target = target,
                            state = stateFor(target.id),
                            queued = target.id in queuedIds,
                            selected = target.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            expanded = target.id in expandedIds,
                            onToggleExpand = {
                                expandedIds = if (target.id in expandedIds) {
                                    expandedIds - target.id
                                } else {
                                    expandedIds + target.id
                                }
                            },
                            onToggleSelect = {
                                selectedIds = if (target.id in selectedIds) {
                                    selectedIds - target.id
                                } else {
                                    selectedIds + target.id
                                }
                                HapticHelper.lightTick(context)
                            },
                            onOpenDetails = { onOpenTarget(target.id) },
                            onRunOne = {
                                // IN-PLACE: one target's run, right here.
                                controller.start(listOf(target.id), target.name)
                            },
                            runActive = runActive,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // ── Pinned bars: the selection bar WINS over the run strip ──
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text(
                            "Clear",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            // IN-PLACE: the selection runs here — the pinned
                            // bar hands over to the run strip on selection
                            // clear.
                            controller.start(
                                selectedIds.toList(),
                                "Selected (${selectedIds.size})",
                            )
                            selectedIds = emptySet()
                        },
                        enabled = !runActive,
                    ) {
                        Text(
                            text = "Test selected",
                            fontFamily = RobotoFamily,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        // ── Pinned RUN STRIP — the in-place run's always-visible controls ──
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedIds.isEmpty() && runActive,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.label ?: "Testing",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${runProgressLabel(session?.cursor ?: 0, session?.queue?.size ?: 0)}" +
                                " · ${session?.testedCount ?: 0} done",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Details",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                // D-591 (round 86): a single-target run's
                                // "Details" opens THAT target's dedicated
                                // detail page — not the all-targets run view.
                                val queue = session?.queue.orEmpty()
                                if (queue.size == 1) {
                                    onOpenTarget(queue.first())
                                } else {
                                    onOpenRun("")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop testing",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { controller.stop() }
                            .padding(9.dp),
                    )
                }
            }
        }
    }
}

/** The list's search field — the same clean anatomy as the filters search. */
@Composable
private fun TestingListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                RoundedCornerShape(50),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 13.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                textStyle = TextStyle(
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* live filter — nothing to submit */ }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search sources…",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * One of the three run-scope pills (Run all / Run failed / Run passed) — a
 * stadium tappable with the count baked into the label. Disabled while a run
 * is live or when the scope's id set is empty (D-589).
 */
@Composable
private fun RunScopePill(
    text: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * One target row. Tap = expand (or TOGGLE SELECTION while a selection is
 * open), long-press = select. The expanded body leads with the ACTIONS and
 * shows only the compact kind/duration rows — no message text, no chevron.
 */
@Composable
private fun TargetListRow(
    target: TestableTarget,
    state: TargetRunState?,
    queued: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenDetails: () -> Unit,
    onRunOne: () -> Unit,
    runActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                RoundedCornerShape(14.dp),
            ),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        // ROUND 85 selection contract: while a selection is
                        // open, a single tap toggles the row's selection —
                        // it must NOT expand/collapse.
                        onClick = if (selectionMode) onToggleSelect else onToggleExpand,
                        onLongClick = onToggleSelect,
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TargetIconView(target)
                    // The selection badge — only when selected (no checkbox
                    // column). Fully qualified: the RowScope receiver in scope
                    // would otherwise capture the deprecated overload.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(120)),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            target.lang?.let { append(it) }
                            append(" · ")
                            append(target.ecosystem.displayName())
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Exactly ONE trailing indicator — the status chip. The old
                // chevron is gone (the round-85 report).
                TargetStatusChip(state, queued = queued)
            }

            // ── Expanded body (D-589, round 86): TWO SEPARATED BLOCKS — the
            // test results live in their own sub-container, and the actions
            // (Run tests / Full details) sit at the VERY BOTTOM-RIGHT corner
            // of the expansion.
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = Motion.EasingEmphasized)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    // BLOCK 1 — the glanceable kind grid, in its own
                    // separated container ("they should be in the same kind
                    // of block, but they should be separate from each
                    // other").
                    val startedKinds = ExtensionTestKind.entries.filter { kind ->
                        state?.results?.get(kind)?.let { it.status != TestStatus.PENDING } == true
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (startedKinds.isEmpty()) {
                                Text(
                                    text = "Run the tests to see each stage's timing here.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
                                )
                            } else {
                                startedKinds.forEach { kind ->
                                    // Every row fades/expands in on first
                                    // appearance (the "one test shows at a
                                    // time" reveal the user liked).
                                    AppearingKindRow {
                                        KindCompactRow(
                                            kind = kind,
                                            result = state?.results?.get(kind),
                                            runningStartedAtMs = state?.runningKindStartedAtMs,
                                            runningDetail = state?.runningDetail,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // BLOCK 2 — the actions, at the VERY BOTTOM-RIGHT corner.
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Full details",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onOpenDetails)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (runActive) "Testing…" else "Run tests",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (runActive) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = !runActive, onClick = onRunOne)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
