package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * EXTENSION TESTING (round 82, D-576; reworked round 83, D-578/D-579) — the
 * screen that shows WHICH extensions actually work and WHY the others don't.
 *
 * WHAT A RUN DOES — for each tested source the chain (ExtensionTestChain)
 * walks: Ping → Search → Home page → Details → Episode list → Video resolve →
 * Stream play, feeding each stage's output into the next (the waterfall in
 * ExtensionTestContext). Round 83: every source call runs on Dispatchers.IO
 * (the round-82 Main-thread calls threw NetworkOnMainThreadException on real
 * aniyomi extensions — the "19 ms failures") and the Search test runs a
 * SMART phrase ladder instead of one user-typed query.
 *
 * ROUND 83 SCREEN (the device report, point by point):
 * • ECOSYSTEM SECTIONS — All / Aniyomi / CloudStream chips; each ecosystem is
 *   its own section (own header, select-all, run-section) so the two systems
 *   can be tested completely separately.
 * • MULTI-SELECT — tap or long-press toggles a card's selection (the
 *   checkbox mirrors it); expansion moved to the card's chevron.
 * • BATCH OVERLAY — while several targets run, a live card shows the current
 *   target, its per-test rows in real time, an animated progress bar and
 *   Stop (TestingBatchOverlay.kt).
 * • SUMMARY — counts + an animated pass/fail/untested proportion bar + the
 *   precision re-runs: "Failed only" / "Passed only" (TestingSummaryCard.kt).
 * • MEMORY (D-579) — results persist in ExtensionTestResultStore: reopening
 *   the screen shows the last verdicts, uninstalled targets are pruned, and
 *   "Clear results" wipes the store.
 * • TIME — human durations everywhere ("1.2 s", "2m 05s") via TestTimeFormat.
 *
 * FUTURE (doc 64): automated schedules + per-extension statistics hang off
 * the same chain/models/store — the screen is the first consumer, not the last.
 *
 * CORE_RULES §22: smooth animations. §20: tag "Anikuta:Feature:ExtensionsTesting".
 */
@Composable
fun ExtensionTestingScreen(
    onBack: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
    csContentRepository: CloudstreamContentRepository = koinInject(),
    csResolver: CloudstreamLinkResolver = koinInject(),
) {
    val sourcesMap by extensionManager.sources.collectAsState()
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val csProviderSources by csContentRepository.sources.collectAsState()

    // The live source objects behind the targets (tests call THEM).
    val sourceById = remember(sourcesMap) {
        sourcesMap.values.filterIsInstance<AnimeCatalogueSource>().associateBy { it.id }
    }
    val targets = remember(sourcesMap, installedExtensions, csProviderSources) {
        buildTargets(sourcesMap, installedExtensions, csProviderSources)
    }

    // ── D-579: the result store — restored on open, saved as targets finish ──
    val appContext = LocalContext.current.applicationContext
    val resultStore = remember { ExtensionTestResultStore(appContext) }
    var restored by remember { mutableStateOf(false) }

    // ── Run state ──
    val runStates = remember { mutableStateMapOf<Long, TargetRunState>() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var expandedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Ecosystem filter ("all" / "aniyomi" / "cloudstream") — the user tests
    // each system completely separately when they want to.
    var ecosystemFilter by rememberSaveable { mutableStateOf("all") }
    var isBatchRunning by remember { mutableStateOf(false) }
    var batchJob by remember { mutableStateOf<Job?>(null) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchIndex by remember { mutableStateOf(0) }
    var batchCurrentTargetId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // Restore persisted verdicts ONCE (then prune targets that no longer exist).
    LaunchedEffect(targets, restored) {
        if (!restored) {
            restored = true
            if (runStates.isEmpty()) {
                resultStore.loadAll().forEach { (id, run) ->
                    runStates[id] = TargetRunState(
                        results = run.results,
                        isRunning = false,
                        finished = run.finished,
                    )
                }
            }
            resultStore.prune(targets.map { it.id }.toSet())
        }
    }

    // The test HTTP client (ping + stream play) — short timeouts, tolerant of
    // redirects; deliberately self-contained so the screen owns no DI changes.
    val testClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** One target's full chain run (suspend — the batch loop awaits it). */
    suspend fun runTestsFor(target: TestableTarget) {
        val source = sourceById[target.id] ?: return
        runStates[target.id] = TargetRunState(
            results = emptyMap(),
            isRunning = true,
        )
        val engine = ExtensionTestEngine(ExtensionTestChain.build(testClient, csResolver))
        val testContext = ExtensionTestContext(
            source = source,
            target = target,
            // Blank → the Search test runs ONLY the smart phrase ladder
            // (the custom query is tried first when the user typed one).
            searchQuery = query.trim(),
        )
        engine.run(testContext) { kind, result ->
            val prev = runStates[target.id] ?: TargetRunState()
            runStates[target.id] = prev.copy(
                results = prev.results + (kind to result),
                runningKind = if (result.status == TestStatus.RUNNING) kind else null,
            )
        }
        val finalState = (runStates[target.id] ?: TargetRunState()).copy(
            isRunning = false,
            runningKind = null,
            finished = true,
        )
        runStates[target.id] = finalState
        // D-579: persist the finished verdict immediately — a crash or a
        // screen close mid-batch never loses completed targets.
        resultStore.saveTarget(target, finalState)
    }

    /** Starts a sequential batch run over [list] (per-card Run / Run all). */
    fun startBatch(list: List<TestableTarget>) {
        if (isBatchRunning || list.isEmpty()) return
        isBatchRunning = true
        batchTotal = list.size
        batchIndex = 0
        batchCurrentTargetId = null
        batchJob = scope.launch {
            list.forEachIndexed { index, target ->
                batchIndex = index + 1
                batchCurrentTargetId = target.id
                runTestsFor(target)
            }
        }.also { job ->
            job.invokeOnCompletion {
                isBatchRunning = false
                // A cancel (Stop / screen close) may leave a target mid-run —
                // settle the spinner but do NOT mark it finished: a killed run
                // is not a completed test (it must not count as tested or
                // healthy). Partial results stay visible on the card.
                runStates.entries.forEach { (id, state) ->
                    if (state.isRunning) {
                        runStates[id] = state.copy(isRunning = false, runningKind = null)
                    }
                }
            }
        }
    }

    // ── Derived lists ──
    val aniyomiTargets = targets.filter { it.ecosystem == TestEcosystem.ANIYOMI }
    val csTargets = targets.filter { it.ecosystem == TestEcosystem.CLOUDSTREAM }
    val showAniyomiSection = ecosystemFilter != "cloudstream" && aniyomiTargets.isNotEmpty()
    val showCsSection = ecosystemFilter != "aniyomi" &&
        (csTargets.isNotEmpty() || ecosystemFilter == "cloudstream")

    val selectedTargets = targets.filter { it.id in selectedIds }
    val testedCount = runStates.values.count { it.finished }
    val passSum = runStates.values.count { it.finished && it.isHealthy }
    val failSum = runStates.values.count { it.finished && !it.isHealthy }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extension Testing",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ── Suite summary (+ precision re-runs + clear) ──
                    item(key = "summary") {
                        TestingSummaryCard(
                            totalCount = targets.size,
                            passedCount = passSum,
                            failedCount = failSum,
                            testedCount = testedCount,
                            isBatchRunning = isBatchRunning,
                            onRunAll = { startBatch(targets) },
                            onRunFailed = {
                                startBatch(targets.filter { t ->
                                    runStates[t.id]?.let { it.finished && !it.isHealthy } == true
                                })
                            },
                            onRunPassed = {
                                startBatch(targets.filter { t -> runStates[t.id]?.isHealthy == true })
                            },
                            onClearResults = {
                                resultStore.clear()
                                runStates.clear()
                            },
                        )
                    }

                    // ── Query editor (styled pill; blank = smart phrases) ──
                    item(key = "query") {
                        TestingQueryField(
                            query = query,
                            enabled = !isBatchRunning,
                            onQueryChange = { query = it },
                        )
                    }

                    // ── Ecosystem chips: All / Aniyomi / CloudStream ──
                    item(key = "eco-chips") {
                        EcosystemChipRow(
                            selected = ecosystemFilter,
                            aniyomiCount = aniyomiTargets.size,
                            csCount = csTargets.size,
                            onSelect = { ecosystemFilter = it },
                        )
                    }

                    // ── Aniyomi section ──
                    if (showAniyomiSection) {
                        item(key = "header-aniyomi", contentType = "sectionHeader") {
                            TestingSectionHeader(
                                title = "Aniyomi extensions",
                                count = aniyomiTargets.size,
                                allSelected = aniyomiTargets.isNotEmpty() &&
                                    aniyomiTargets.all { it.id in selectedIds },
                                onToggleSelectAll = {
                                    selectedIds = if (aniyomiTargets.all { it.id in selectedIds }) {
                                        selectedIds - aniyomiTargets.map { it.id }.toSet()
                                    } else {
                                        selectedIds + aniyomiTargets.map { it.id }.toSet()
                                    }
                                },
                                onRunSection = { startBatch(aniyomiTargets) },
                                runEnabled = !isBatchRunning,
                            )
                        }
                        items(aniyomiTargets, key = { it.id }) { target ->
                            TargetItem(
                                target = target,
                                state = runStates[target.id],
                                selected = target.id in selectedIds,
                                expanded = target.id in expandedIds,
                                interactionEnabled = !isBatchRunning,
                                onToggleSelect = { toggleSelect(target.id) },
                                onToggleExpand = {
                                    expandedIds = if (target.id in expandedIds) {
                                        expandedIds - target.id
                                    } else {
                                        expandedIds + target.id
                                    }
                                },
                                onRun = { startBatch(listOf(target)) },
                                runEnabled = !isBatchRunning,
                            )
                        }
                    }

                    // ── CloudStream section ──
                    if (showCsSection) {
                        item(key = "header-cs", contentType = "sectionHeader") {
                            TestingSectionHeader(
                                title = "CloudStream plugins",
                                count = csTargets.size,
                                allSelected = csTargets.isNotEmpty() &&
                                    csTargets.all { it.id in selectedIds },
                                onToggleSelectAll = {
                                    selectedIds = if (csTargets.all { it.id in selectedIds }) {
                                        selectedIds - csTargets.map { it.id }.toSet()
                                    } else {
                                        selectedIds + csTargets.map { it.id }.toSet()
                                    }
                                },
                                onRunSection = { startBatch(csTargets) },
                                runEnabled = !isBatchRunning,
                            )
                        }
                        if (csTargets.isEmpty()) {
                            item(key = "cs-empty", contentType = "emptyNote") {
                                Text(
                                    text = "No CloudStream plugins installed — add a repository in Settings → Extensions.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(csTargets, key = { it.id }) { target ->
                                TargetItem(
                                    target = target,
                                    state = runStates[target.id],
                                    selected = target.id in selectedIds,
                                    expanded = target.id in expandedIds,
                                    interactionEnabled = !isBatchRunning,
                                    onToggleSelect = { toggleSelect(target.id) },
                                    onToggleExpand = {
                                        expandedIds = if (target.id in expandedIds) {
                                            expandedIds - target.id
                                        } else {
                                            expandedIds + target.id
                                        }
                                    },
                                    onRun = { startBatch(listOf(target)) },
                                    runEnabled = !isBatchRunning,
                                )
                            }
                        }
                    }
                }

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

        // ── Bottom pinned layer: the live batch overlay OR the selection bar ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            val currentTarget = targets.firstOrNull { it.id == batchCurrentTargetId }
            TestingBatchOverlay(
                visible = isBatchRunning,
                currentTarget = currentTarget,
                currentState = currentTarget?.let { runStates[it.id] },
                index = batchIndex,
                total = batchTotal,
                onStop = { batchJob?.cancel() },
            )
            AnimatedVisibility(
                visible = !isBatchRunning && selectedIds.isNotEmpty(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Clear",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedIds = emptySet() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Button(
                            onClick = {
                                val list = selectedTargets
                                selectedIds = emptySet()
                                startBatch(list)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                "Test selected",
                                fontFamily = RobotoFamily,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Screen-local building blocks
// ════════════════════════════════════════════════════════════════════════════

/** The card item wrapper (plain pass-through — keeps the LazyColumn body flat). */
@Composable
private fun TargetItem(
    target: TestableTarget,
    state: TargetRunState?,
    selected: Boolean,
    expanded: Boolean,
    interactionEnabled: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onRun: () -> Unit,
    runEnabled: Boolean,
) {
    TestingTargetCard(
        target = target,
        state = state,
        selected = selected,
        expanded = expanded,
        interactionEnabled = interactionEnabled,
        onToggleSelect = onToggleSelect,
        onToggleExpand = onToggleExpand,
        onRun = onRun,
        runEnabled = runEnabled,
    )
}

/**
 * One ecosystem's section header: title + count + Select-all + Run-section.
 * The dedicated select-all is the batch shortcut (tap cards for individual
 * picks; long-press also toggles).
 */
@Composable
private fun TestingSectionHeader(
    title: String,
    count: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onRunSection: () -> Unit,
    runEnabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "($count)",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = if (allSelected) "Deselect all" else "Select all",
                tint = if (allSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSelectAll)
                    .padding(6.dp),
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Test all in this section",
                tint = if (runEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(enabled = runEnabled, onClick = onRunSection)
                    .padding(4.dp),
            )
        }
    }
}

/** The All / Aniyomi / CloudStream filter chips (test each system separately). */
@Composable
private fun EcosystemChipRow(
    selected: String,
    aniyomiCount: Int,
    csCount: Int,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EcoChip(label = "All", count = aniyomiCount + csCount, selected = selected == "all", onClick = { onSelect("all") })
        EcoChip(label = "Aniyomi", count = aniyomiCount, selected = selected == "aniyomi", onClick = { onSelect("aniyomi") })
        EcoChip(label = "CloudStream", count = csCount, selected = selected == "cloudstream", onClick = { onSelect("cloudstream") })
    }
}

@Composable
private fun EcoChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(
            text = "$label \u00b7 $count",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * The test-query field (D-578 rework): a bordered pill with the search icon
 * that yields to a clear button — and an honest hint: blank means the smart
 * well-known phrase ladder runs (anime / series / movie categories).
 */
@Composable
private fun TestingQueryField(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(50)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { if (enabled) onQueryChange(it) },
                    modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                    textStyle = TextStyle(
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Custom test query (optional)…",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear query",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                            .padding(7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Leave empty to search well-known titles across anime, series and movies. The first phrase with results wins.",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Target construction — the unified source list split into testable targets
//  (unchanged from round 82: aniyomi first, name-sorted inside ecosystems).
// ════════════════════════════════════════════════════════════════════════════

private fun buildTargets(
    sourcesMap: Map<Long, eu.kanade.tachiyomi.animesource.AnimeSource>,
    installedExtensions: List<com.confused.anikuta.data.extension.model.AnimeExtension.Installed>,
    csProviderSources: List<com.confused.anikuta.data.cloudstream.content.CsProviderSource>,
): List<TestableTarget> {
    val aniyomiIcons = buildMap {
        installedExtensions.filter { it.isEnabled }.forEach { ext ->
            ext.sources.forEach { src ->
                ext.icon?.let { put(src.id, it) }
            }
        }
    }
    val csIcons = csProviderSources.associate { it.providerName to it.pluginIconUrl }

    return sourcesMap.values
        .filterIsInstance<AnimeCatalogueSource>()
        .map { src ->
            val bridged = (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)
                ?.isCloudStreamBridged == true
            TestableTarget(
                id = src.id,
                name = src.name,
                ecosystem = if (bridged) TestEcosystem.CLOUDSTREAM else TestEcosystem.ANIYOMI,
                lang = src.lang,
                iconDrawable = if (bridged) null else aniyomiIcons[src.id],
                iconUrl = if (bridged) csIcons[src.name] else null,
                providerName = if (bridged) src.name else null,
                baseUrl = (src as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource)?.baseUrl,
            )
        }
        .sortedWith(compareBy({ it.ecosystem.ordinal }, { it.name.lowercase() }))
}
