package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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

// ════════════════════════════════════════════════════════════════════════════
//  PAGE 2 of 5 — PER-SYSTEM TARGET LIST (round 84, D-583).
//
//  The round-84 report on the old card list: the LEFT CHECKBOX was "not
//  good", the right edge stacked THREE indicators (status + chevron + play),
//  and expanding felt janky. This list is the fix:
//    • NO checkbox column — a row is [icon][name+lang][ONE status chip][chevron];
//    • tap expands/collapses (an emphasized-easing animateContentSize, never
//      a jolt), long-press toggles selection (haptic tick) — the checkbox
//      only appears ON the selected rows as a small check badge;
//    • the search field filters rows only (same clean style as the filters
//      bar), the select-all control lives in the header row;
//    • the selection actions live in a pinned bottom bar.
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

    fun stateFor(id: Long): TargetRunState? =
        session?.states?.get(id) ?: storedRuns[id]?.let { storedToRunState(it) }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }

    val filtered = if (query.isBlank()) {
        ecoTargets
    } else {
        ecoTargets.filter { matchesSearch(it.name, query.trim()) }
    }
    val runActive = session?.phase == RunPhase.RUNNING
    val allIds = ecoTargets.map { it.id }.toSet()

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

                // ── Controls row: select-all + run-all ──
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
                        Text(
                            text = "Run all",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (runActive || filtered.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = !runActive && filtered.isNotEmpty()) {
                                    onOpenRun(filtered.joinToString(",") { it.id.toString() })
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
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
                            selected = target.id in selectedIds,
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
                            onRunOne = { onOpenRun(target.id.toString()) },
                            runActive = runActive,
                        )
                    }
                }
            }
        }

        // ── Pinned selection bar ──
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
                            onOpenRun(selectedIds.joinToString(",") { it.toString() })
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
 * One target row — the checkbox-free anatomy. Tap = expand, long-press =
 * select; the selection shows as a small check badge on the icon (never a
 * dedicated column).
 */
@Composable
private fun TargetListRow(
    target: TestableTarget,
    state: TargetRunState?,
    selected: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenDetails: () -> Unit,
    onRunOne: () -> Unit,
    runActive: Boolean,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(Motion.DurationStandard, easing = Motion.EasingEmphasized),
            )
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
                        onClick = onToggleExpand,
                        onLongClick = onToggleSelect,
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TargetIconView(target)
                    // The selection badge — only when selected (no checkbox column).
                    AnimatedVisibility(visible = selected, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
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
                TargetStatusChip(state)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .padding(2.dp),
                )
            }

            // ── Expanded body: the compact seven-kind grid + actions ──
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = Motion.EasingEmphasized)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExtensionTestKind.entries.forEach { kind ->
                        KindResultRow(kind = kind, result = state?.results?.get(kind))
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Run tests",
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
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
                    }
                }
            }
        }
    }
}
