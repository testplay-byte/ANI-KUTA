package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * EXTENSION TESTING (round 82, D-576) — the screen the user asked for at the
 * top of Settings → Extensions: a minimal, clean, feature-rich way to see
 * WHICH extensions actually work and WHY the others don't.
 *
 * WHAT A RUN DOES — for each tested source the chain (ExtensionTestChain)
 * walks: Ping → Search → Home page → Details → Episode list → Video resolve →
 * Stream play, feeding each stage's output into the next (the waterfall in
 * ExtensionTestContext). Failures carry the reason ("NoClassDefFoundError…",
 * "No results for…", "HTTP 403…"), unmet prerequisites are SKIPPED with the
 * reason — nothing is ever a mystery.
 *
 * BOTH ECOSYSTEMS: aniyomi extension sources AND bridged CloudStream providers
 * are listed (each with its ecosystem chip); the resolve test routes through
 * each ecosystem's own path (source.getVideoList vs the CS link resolver).
 *
 * BATCH: tap checkboxes to select, "Run all" for everything — targets are
 * tested SEQUENTIALLY (one network hammer at a time) with live per-test rows.
 *
 * FUTURE (doc 64): automated schedules + per-extension statistics hang off the
 * same chain/models — the screen is the first consumer, not the last.
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

    // ── Run state ──
    val runStates = remember { mutableStateMapOf<Long, TargetRunState>() }
    var query by rememberSaveable { mutableStateOf("test") }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var expandedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var batchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

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
            results = runStates[target.id]?.results ?: emptyMap(),
            isRunning = true,
        )
        val engine = ExtensionTestEngine(ExtensionTestChain.build(testClient, csResolver))
        val context = ExtensionTestContext(
            source = source,
            target = target,
            searchQuery = query.ifBlank { "test" },
        )
        engine.run(context) { kind, result ->
            val prev = runStates[target.id] ?: TargetRunState()
            runStates[target.id] = prev.copy(
                results = prev.results + (kind to result),
                runningKind = if (result.status == TestStatus.RUNNING) kind else null,
            )
        }
        runStates[target.id] = (runStates[target.id] ?: TargetRunState()).copy(
            isRunning = false,
            runningKind = null,
            finished = true,
        )
    }

    /** Starts a sequential batch run over [list] (per-card Run / Run all). */
    fun startBatch(list: List<TestableTarget>) {
        if (isBatchRunning || list.isEmpty()) return
        isBatchRunning = true
        batchJob = scope.launch {
            list.forEach { target -> runTestsFor(target) }
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

    val selectedTargets = targets.filter { it.id in selectedIds }
    val testedCount = runStates.values.count { it.finished }
    val passSum = runStates.values.sumOf { it.passedCount }
    val failSum = runStates.values.sumOf { it.failedCount }
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extension Testing",
                collapsed = collapsed,
                onBack = onBack,
            )

            // Inner Box — the scroll blur overlay is a top-center sibling of
            // the list (ColumnScope.align only takes Horizontal alignments).
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                // ── Suite summary card ──
                item(key = "summary") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Suite summary",
                                    fontFamily = RobotoFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$testedCount / ${targets.size} tested",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Passed", passSum, MaterialTheme.colorScheme.primary)
                                StatChip("Failed", failSum, MaterialTheme.colorScheme.error)
                                StatChip(
                                    "Untested",
                                    targets.size - testedCount,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { startBatch(targets) },
                                    enabled = !isBatchRunning && targets.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        "Run all tests",
                                        fontFamily = RobotoFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                if (isBatchRunning) {
                                    Button(
                                        onClick = { batchJob?.cancel() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = "Stop testing",
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Stop",
                                            fontFamily = RobotoFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Query editor card ──
                item(key = "query") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Test search query",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(6.dp))
                            OutlinedTextField(
                                value = query,
                                onValueChange = { if (!isBatchRunning) query = it },
                                placeholder = { Text("test", fontSize = 13.sp, fontFamily = RobotoFamily) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = "The Search test runs this query; later tests reuse its first result (falling back to the home page).",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Target cards ──
                items(targets, key = { it.id }) { target ->
                    val state = runStates[target.id]
                    TargetCard(
                        target = target,
                        state = state,
                        selected = target.id in selectedIds,
                        expanded = target.id in expandedIds,
                        selectionEnabled = !isBatchRunning,
                        onToggleSelect = {
                            selectedIds = if (target.id in selectedIds) {
                                selectedIds - target.id
                            } else {
                                selectedIds + target.id
                            }
                        },
                        onToggleExpand = {
                            expandedIds = if (target.id in expandedIds) {
                                expandedIds - target.id
                            } else {
                                expandedIds + target.id
                            }
                        },
                        onRun = { startBatch(listOf(target)) },
                        runEnabled = !isBatchRunning && state?.isRunning != true,
                    )
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

        // ── Batch bar (selection / progress) — pinned above the bottom edge ──
        if (selectedIds.isNotEmpty() || isBatchRunning) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                if (isBatchRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Testing extensions\u2026",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
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
//  Target card + rows
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TargetCard(
    target: TestableTarget,
    state: TargetRunState?,
    selected: Boolean,
    expanded: Boolean,
    selectionEnabled: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onRun: () -> Unit,
    runEnabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    enabled = selectionEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.size(36.dp),
                )
                TargetIcon(target)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(if (target.ecosystem == TestEcosystem.ANIYOMI) "Aniyomi" else "CloudStream")
                            target.lang?.let { append(" \u00b7 $it") }
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TargetStatusChip(state)
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse tests" else "Expand tests",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f)
                        .clickable(onClick = onToggleExpand),
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Test this extension",
                    tint = if (runEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(enabled = runEnabled, onClick = onRun)
                        .padding(2.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 12.dp, bottom = 8.dp)) {
                    ExtensionTestKind.entries.forEach { kind ->
                        TestRow(kind = kind, result = state?.results?.get(kind))
                    }
                }
            }
        }
    }
}

/** One test's row inside an expanded card. */
@Composable
private fun TestRow(kind: ExtensionTestKind, result: TestResult?) {
    val status = result?.status ?: TestStatus.PENDING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        TestStatusIcon(status)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kind.label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val message = when (status) {
                TestStatus.PENDING -> kind.description
                else -> result?.message ?: ""
            }
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = when (status) {
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        TestStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (result != null && result.durationMs > 0 && status != TestStatus.RUNNING) {
            Text(
                text = "${result.durationMs} ms",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TestStatusIcon(status: TestStatus) {
    when (status) {
        TestStatus.PENDING -> Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
        )
        TestStatus.RUNNING -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        TestStatus.PASSED -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Passed",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        TestStatus.FAILED -> Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = "Failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        TestStatus.SKIPPED -> Icon(
            imageVector = Icons.Filled.RemoveCircleOutline,
            contentDescription = "Skipped",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TargetStatusChip(state: TargetRunState?) {
    val (label, fg, bg) = when {
        state?.isRunning == true -> Triple(
            "Testing\u2026",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )
        state != null && state.finished && state.isHealthy -> Triple(
            "Passed ${state.passedCount}/${ExtensionTestKind.entries.size}",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )
        state != null && state.finished -> Triple(
            "${state.failedCount} failed",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        )
        // A cancelled run: partial results, not a verdict.
        state != null && state.results.isNotEmpty() -> Triple(
            "Incomplete",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        else -> Triple(
            "Untested",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StatChip(label: String, count: Int, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$count",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = tint,
            )
        }
    }
}

/** The target's icon: aniyomi Drawable, CS plugin URL, or the letter tile. */
@Composable
private fun TargetIcon(target: TestableTarget) {
    when {
        target.iconDrawable != null -> AsyncImage(
            model = target.iconDrawable,
            contentDescription = "${target.name} icon",
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)),
        )
        target.iconUrl != null -> SubcomposeAsyncImage(
            model = target.iconUrl
                .replace("%size%", "64")
                .replace("%exact_size%", "64"),
            contentDescription = "${target.name} icon",
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)),
            loading = { LetterTile(target.name) },
            error = { LetterTile(target.name) },
        )
        else -> LetterTile(target.name)
    }
}

@Composable
private fun LetterTile(name: String) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(color = color, shape = RoundedCornerShape(7.dp), modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Target construction — the unified source list split into testable targets
// ════════════════════════════════════════════════════════════════════════════

/**
 * Builds the testable target list from BOTH ecosystems:
 * • Aniyomi → every trusted catalogue source (id-mapped to its parent
 *   extension's icon Drawable, the SearchViewModel pattern).
 * • CloudStream → every bridged provider (parent plugin's iconUrl via the
 *   content repository, providerName for the resolver).
 * Aniyomi first, name-sorted inside each ecosystem.
 */
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
            val bridged = (src as? AnimeHttpSource)?.isCloudStreamBridged == true
            TestableTarget(
                id = src.id,
                name = src.name,
                ecosystem = if (bridged) TestEcosystem.CLOUDSTREAM else TestEcosystem.ANIYOMI,
                lang = src.lang,
                iconDrawable = if (bridged) null else aniyomiIcons[src.id],
                iconUrl = if (bridged) csIcons[src.name] else null,
                providerName = if (bridged) src.name else null,
                baseUrl = (src as? AnimeHttpSource)?.baseUrl,
            )
        }
        .sortedWith(compareBy({ it.ecosystem.ordinal }, { it.name.lowercase() }))
}
