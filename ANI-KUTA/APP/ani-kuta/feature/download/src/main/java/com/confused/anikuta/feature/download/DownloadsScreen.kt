package com.confused.anikuta.feature.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
// ROUND 122-fix: the long-press drag gesture was used without its import.
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.download.DownloadStatus
import com.confused.anikuta.core.download.DownloadTask
import com.confused.anikuta.feature.download.components.DownloadsEmptyState
import com.confused.anikuta.feature.download.components.rememberQueueDragDropState
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Downloads screen — the live queue + per-episode 3-dot menu.
 *
 * D.6: Adapted from the old project's `DownloadsScreen.kt`. Layout:
 * - CollapsingHeader with a settings gear + a "Downloaded" icon (only shows
 *   if the user has at least one completed download).
 * - Action bar (bulk operations: pause all, resume all, retry all, cancel all).
 * - Summary chips (downloading / queued / paused / failed counts).
 * - Anime-sectioned cards: ONE card per anime, containing the header (accent
 *   bar + anime name + episode count badge) + all episode rows inside.
 * - Episode rows: episode name + info pills (server/audio/quality) + progress
 *   bar + 3-dot menu (pause/resume/cancel/retry/delete).
 * - Empty state: setup prompt (no folder) OR "No downloads yet" hint.
 *
 * @param onBack Called when the user taps the back arrow.
 * @param onOpenSettings Called when the user taps the settings gear.
 * @param onOpenDownloaded Called when the user taps the "Downloaded" icon.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDownloaded: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemIndex > 0 ||
        lazyListState.firstVisibleItemScrollOffset > 20

    // DB-7: provide debug context for the Current Screen tab.
    val updateDebugContext = com.confused.anikuta.core.debugapi.LocalDebugContextUpdater.current
    val dlCtx = remember(state) {
        com.confused.anikuta.core.debugapi.DebugContext(
            screenName = "Downloads",
            screenData = mapOf(
                "state" to (state::class.simpleName ?: "Unknown"),
            ),
            relevantTables = listOf(
                com.confused.anikuta.core.debugapi.DbReference("download_queue", "1", "1", "View download queue"),
                com.confused.anikuta.core.debugapi.DbReference("downloaded_episode", "1", "1", "View downloaded episodes"),
            ),
        )
    }
    androidx.compose.runtime.LaunchedEffect(dlCtx) { updateDebugContext(dlCtx) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { updateDebugContext(null) }
    }

    val context = LocalContext.current

    // ── Request POST_NOTIFICATIONS permission (Android 13+) ──
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // SAF folder picker (shown when no folder is set).
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                // Persist via the preferences API.
                viewModel.setDownloadFolder(uri.toString())
            } catch (e: Exception) {
                // Best-effort — ignore.
            }
        }
    }

    // Derive status counts from the queue.
    val queue = state.queue
    val downloading = queue.count { it.status == DownloadStatus.DOWNLOADING }
    val queued = queue.count { it.status == DownloadStatus.QUEUED }
    val paused = queue.count { it.status == DownloadStatus.PAUSED }
    // ROUND 121: network-waiting tasks — their own count + chip (they are NOT
    // paused; lumping them in would hide the auto-resume semantics).
    val waiting = queue.count { it.status == DownloadStatus.WAITING_FOR_NETWORK }
    val failed = queue.count { it.status == DownloadStatus.ERROR }
    // ROUND 122 (R9): waiting tasks count as active — the PAUSE button must
    // show while tasks wait (pausing a waiting task converts it to PAUSED,
    // the user's intent wins; the device report: only RESUME showed).
    val hasActive = downloading > 0 || queued > 0 || waiting > 0

    // Group queue by anime title.
    val groupedByAnime = remember(queue) {
        queue.groupBy { it.content.title }.toList()
    }

    // Track which episode's 3-dot menu is open.
    var menuTaskId by remember { mutableStateOf<Long?>(null) }

    // ── ROUND 120 (D-749): the queue management modes ──
    // SELECT: rows grow checkboxes — bulk pause/resume/remove over the set.
    // REORDER: the grouped cards flatten into one ordered list — moving a row
    // persists the order, and the QUEUE'S SCHEDULER sorts by it, so the list
    // the user arranges IS the order the downloads start in.
    var selectionMode by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    // Exiting select mode always drops the checks; a queue that empties out
    // (cancels, auto-clears) leaves BOTH modes — the toggles' host row is
    // gone with the queue, and a stuck reorderMode would silently re-show the
    // flat card on the next enqueue.
    LaunchedEffect(selectionMode) {
        if (!selectionMode) viewModel.clearSelection()
    }
    LaunchedEffect(queue.isEmpty()) {
        if (queue.isEmpty()) {
            selectionMode = false
            reorderMode = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(
            title = "Downloads",
            collapsed = collapsed,
            // D-558: the heading IS the back button (leading arrow + tappable title);
            // the old top-right back Box retired.
            onBack = onBack,
            actions = {
                // D.FIX: Always show the "Downloaded" button (not conditional on
                // state.downloaded.isNotEmpty()) — the user wants to navigate to
                // the downloaded files page even when the list is empty.
                IconButton(onClick = onOpenDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Downloaded files",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Download settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        // Action bar (bulk operations) + the Round 120 mode toggles — or the
        // selection bar while the user is multi-selecting.
        if (selectionMode) {
            QueueSelectionBar(
                selectedCount = selection.size,
                // ROUND 121: ONE toggle — all-checked shows the clear icon,
                // anything less shows the check-all icon (the old side-by-side
                // DoneAll + Clear pair read as two mystery buttons).
                allSelected = queue.isNotEmpty() && selection.size >= queue.size,
                onToggleSelectAll = {
                    if (selection.size >= queue.size && queue.isNotEmpty()) {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectAll()
                    }
                },
                // ROUND 121: a bulk action EXITS selection mode (the device
                // round: the menu "didn't exit after the bulk action"). The
                // checks clear with the mode (the existing LaunchedEffect).
                onPauseSelected = {
                    viewModel.pauseSelected()
                    selectionMode = false
                },
                onResumeSelected = {
                    viewModel.resumeSelected()
                    selectionMode = false
                },
                onRemoveSelected = {
                    viewModel.cancelSelected()
                    selectionMode = false
                },
                onExit = { selectionMode = false },
            )
        } else if (queue.isNotEmpty()) {
            DownloadActionBar(
                hasActive = hasActive,
                // ROUND 121: waiting-on-network tasks count as resumable here
                // (resume while offline keeps them waiting — no error).
                hasPaused = paused > 0 || waiting > 0,
                hasFailed = failed > 0,
                hasAny = queue.isNotEmpty(),
                onPauseAll = {
                    queue.filter {
                        it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.RETRYING ||
                            it.status == DownloadStatus.WAITING_FOR_NETWORK
                    }.forEach { viewModel.pause(it.id) }
                },
                onResumeAll = {
                    queue.filter {
                        it.status == DownloadStatus.PAUSED ||
                            it.status == DownloadStatus.WAITING_FOR_NETWORK
                    }.forEach { viewModel.resume(it.id) }
                },
                onRetryAll = {
                    queue.filter { it.status == DownloadStatus.ERROR }.forEach { viewModel.retry(it.id) }
                },
                onCancelAll = { viewModel.cancelAll() },
            )
            // Round 120 (D-749): the two mode doors — pill toggles in the
            // action bar's visual family. Reorder stays clickable while
            // active (the pill IS the Done exit — it must never trap the user).
            QueueModeToggles(
                reorderActive = reorderMode,
                selectEnabled = !reorderMode,
                reorderEnabled = reorderMode || queue.size > 1,
                onSelect = { selectionMode = true },
                onToggleReorder = { reorderMode = !reorderMode },
            )
        }

        // Summary chips.
        if (queue.isNotEmpty()) {
            // D-215: collect live download speed for display.
            val downloadSpeed by viewModel.downloadSpeed.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloading > 0) StatChip("$downloading", "downloading", MaterialTheme.colorScheme.primary)
                // D-217: always show speed when downloading (even if 0 KB/s — never
                // disappears during an active download). The old `downloadSpeed > 0`
                // check caused the chip to vanish between samples.
                if (downloading > 0) {
                    StatChip(formatSpeed(downloadSpeed), "speed", MaterialTheme.colorScheme.tertiary)
                }
                if (queued > 0) StatChip("$queued", "queued", MaterialTheme.colorScheme.onSurfaceVariant)
                if (waiting > 0) StatChip("$waiting", "waiting for network", MaterialTheme.colorScheme.tertiary)
                if (paused > 0) StatChip("$paused", "paused", MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed > 0) StatChip("$failed", "failed", MaterialTheme.colorScheme.error)
            }
        }

        if (queue.isEmpty() && state.downloaded.isEmpty()) {
            // Full empty state — folder prompt if no folder set, else "no downloads yet".
            DownloadsEmptyState(
                needsFolder = !state.folderReady,
                onPickFolder = { folderLauncher.launch(null) },
            )
        } else if (reorderMode && queue.isNotEmpty()) {
            // ROUND 120 (D-749): the flat reorder list (replaces the grouped
            // cards until Done). Moves go through the VM — it computes each
            // swap against the LIVE sorted queue, so rapid taps can't race a
            // stale recomposition.
            QueueReorderCard(
                queue = queue,
                // ROUND 122 (R10): drag-to-reorder — the FULL arrangement
                // commits once on drag end.
                onCommitReorder = viewModel::commitReorder,
                onDone = { reorderMode = false },
            )
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groupedByAnime.forEach { (animeTitle, downloads) ->
                    item(key = "section_$animeTitle") {
                        AnimeSectionCard(
                            animeTitle = animeTitle,
                            downloads = downloads,
                            selectionMode = selectionMode,
                            selectedIds = selection,
                            onToggleSelect = viewModel::toggleSelected,
                            onEnterSelect = { taskId ->
                                selectionMode = true
                                viewModel.toggleSelected(taskId)
                            },
                            onPause = { viewModel.pause(it) },
                            onResume = { viewModel.resume(it) },
                            onCancel = { viewModel.cancel(it) },
                            onRetry = { viewModel.retry(it) },
                            onMenu = { menuTaskId = it },
                        )
                    }
                }
            }
        }
    }

    // 3-dot menu bottom sheet.
    if (menuTaskId != null) {
        val task = queue.firstOrNull { it.id == menuTaskId }
        if (task != null) {
            EpisodeMenuSheet(
                task = task,
                onDismiss = { menuTaskId = null },
                onPause = { viewModel.pause(task.id); menuTaskId = null },
                onResume = { viewModel.resume(task.id); menuTaskId = null },
                onCancel = { viewModel.cancel(task.id); menuTaskId = null },
                onRetry = { viewModel.retry(task.id); menuTaskId = null },
            )
        } else {
            menuTaskId = null
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Action bar
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DownloadActionBar(
    hasActive: Boolean,
    hasPaused: Boolean,
    hasFailed: Boolean,
    hasAny: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val actions = mutableListOf<Pair<ImageVector, () -> Unit>>()
    if (hasActive) actions.add(Icons.Filled.Pause to onPauseAll)
    if (hasPaused) actions.add(Icons.Filled.PlayArrow to onResumeAll)
    if (hasFailed) actions.add(Icons.Filled.Refresh to onRetryAll)
    if (hasAny) actions.add(Icons.Filled.Close to onCancelAll)
    if (actions.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            actions.forEach { (icon, action) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onClick = action,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Stat chip
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatChip(count: String, label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(count, fontFamily = RobotoFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontFamily = RobotoFamily, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Anime section card (one card per anime, containing all episode rows)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimeSectionCard(
    animeTitle: String,
    downloads: List<DownloadTask>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onEnterSelect: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onMenu: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column {
            // Header: accent bar + anime name + episode count badge.
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(3.dp).height(20.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(
                    animeTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "${downloads.size}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Episode rows inside the same card.
            downloads.forEachIndexed { index, task ->
                if (index > 0) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selectionMode && task.id in selectedIds) {
                        // Round 120 (D-749): a checked row gets the primary
                        // wash so the selection reads at a glance.
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    },
                ) {
                    EpisodeRow(
                        task = task,
                        selectionMode = selectionMode,
                        isSelected = task.id in selectedIds,
                        onToggleSelect = { onToggleSelect(task.id) },
                        onEnterSelect = { onEnterSelect(task.id) },
                        onMenu = { onMenu(task.id) },
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episode row
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    task: DownloadTask,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelect: () -> Unit,
    onMenu: () -> Unit,
) {
    // D-213: restructured layout:
    //   Row 1: [Episode Name (weight 1f)] [3-dot button]   ← 3-dot is ONLY here, next to the name
    //   Row 2: [server(flex+…)][audio][quality][size] | [45%] ← pills in a weighted inner row,
    //          the status/percentage pill OUTSIDE it (Task 60: always visible)
    //   Row 3: [progress bar — full width, 6dp]             ← old-style inline bar
    //   Row 4: [error text] (if error)
    // The 3-dot is NOT full-height — it's in the top row only. The info row + progress
    // bar use the FULL width below the 3-dot, so the percentage is never cut off.
    //
    // ROUND 120 (D-749): select-mode row interaction — the whole row toggles its
    // check (tap) and a long-press from NORMAL mode enters select mode with this
    // row pre-checked. Outside select mode a tap stays inert (the kebab menu owns
    // the row's actions, unchanged).
    Column(
        // D-215: reduced all spacing by ~50% (user asked for tighter layout).
        // top=2dp (was 4), bottom=5dp (was 10), horizontal=10dp (unchanged).
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 5.dp)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = { if (!selectionMode) onEnterSelect() },
            ),
    ) {
        // ── Row 1: Episode name + checkbox/3-dot button ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            val epName = task.episode.name.ifBlank {
                "Episode ${task.episode.episodeNumber.toInt()}"
            }
            Text(
                epName,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            // Round 120 (D-749): the kebab hides while selecting — the whole
            // row is one big toggle then (two affordances would fight).
            if (!selectionMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    onClick = onMenu,
                ) {
                    // D-214: wider + shorter button (was 32×32 square). Now 40×24.
                    Box(
                        modifier = Modifier.size(width = 40.dp, height = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            // D-214: rotate the icon 90° so the 3 dots are horizontal.
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer(rotationZ = 90f),
                        )
                    }
                }
            }
        }

        // ── Row 2: Info pills + percentage on the right ──
        // Task 60 (round 20): the server name FLEXES now — a weighted,
        // shrinkable pill that ellipsizes with a trailing "…" when the
        // resolver's label is long (the user: "the server name should be
        // shortened and three dots should be shown at the end"), so the
        // percentage pill NEVER loses its space (the v0.4.7 finding: a long
        // server label pushed it out of view). The inner Row owns the pills;
        // the status/percentage pill sits OUTSIDE it — guaranteed visible.
        // The row is shared by BOTH stacks (CS + aniyomi queue rows render
        // through this component), so one fix covers both.
        // D-215: reduced spacer from 4dp → 2dp (~50% reduction as requested).
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.videoServer.isNotBlank()) {
                    ServerInfoPill(task.videoServer, Modifier.weight(1f, fill = false))
                }
                if (task.videoAudio.isNotBlank()) InfoPill(task.videoAudio.uppercase())
                if (task.videoQuality.isNotBlank()) InfoPill(task.videoQuality)
                // ROUND 121: a waiting-on-network task keeps its size pill —
                // it froze mid-download (the bytes + bar show where it stands).
                if (task.status == DownloadStatus.DOWNLOADING ||
                    task.status == DownloadStatus.PAUSED ||
                    task.status == DownloadStatus.WAITING_FOR_NETWORK
                ) {
                    val sizeText = if (task.totalBytes > 0)
                        "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
                    else formatBytes(task.downloadedBytes)
                    SizePill(sizeText)
                }
            }
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                    PercentagePill("${task.progress}%")
                }
                DownloadStatus.QUEUED, DownloadStatus.RETRYING -> {
                    InfoPill(if (task.status == DownloadStatus.RETRYING) "Retrying" else "Queued")
                }
                // ROUND 121: the network-wait label — visibly NOT "Paused"
                // (it resumes by itself the moment connectivity returns).
                DownloadStatus.WAITING_FOR_NETWORK -> InfoPill("Waiting for network")
                DownloadStatus.ERROR -> ErrorPill("Failed")
                DownloadStatus.COMPLETED -> InfoPill("Done", highlight = true)
                else -> {}
            }
        }

        // ── Row 3: Progress bar (old-style inline, full width, 6dp) ──
        // D-213: restored the old-style progress bar — full width of the Column
        // (which is now full-width since the 3-dot moved to Row 1). 6dp tall.
        if (task.status == DownloadStatus.DOWNLOADING ||
            task.status == DownloadStatus.PAUSED ||
            task.status == DownloadStatus.WAITING_FOR_NETWORK
        ) {
            // D-215: reduced spacer from 6dp → 3dp (~50% reduction as requested).
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (task.status == DownloadStatus.WAITING_FOR_NETWORK) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }

        // ── Row 4: Error text (if any) ──
        if (task.status == DownloadStatus.ERROR) {
            task.lastError?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SizePill(text: String) {
    // D-215: changed to secondaryContainer (darker shade than outlineVariant).
    // The user asked for a "darker kind of shade" — secondaryContainer is a
    // distinct darker shade that differentiates the size from other pills.
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Text(
            text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PercentagePill(text: String) {
    // D-214: match DetailsScreen pill style — added lineHeight=14.sp + padding 8dp.
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Text(
            text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ErrorPill(text: String) {
    // D-214: match DetailsScreen pill style — added lineHeight=14.sp + padding 8dp.
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
        Text(
            text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun InfoPill(text: String, highlight: Boolean = false) {
    // D-214: match DetailsScreen pill style — outlineVariant (was surfaceVariant) +
    // lineHeight=14.sp + padding 8dp. This makes the pill height consistent with
    // the DetailsScreen's date/audio pills (which the user said look proper).
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Text(
            text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Task 60 (round 20 — the v0.4.7 device finding): the SERVER pill — the one
 * label that can run long (resolver server names like "HD-1 - Sub - 1080p"
 * or a CS provider's server string). The call site passes
 * `weight(1f, fill = false)` so the pill FLEXES: intrinsic width when the
 * row has room, shrinking with a trailing "…" (the user's "three dots at
 * the end") when it doesn't — the percentage pill always keeps its space.
 * Same styling as [InfoPill] otherwise.
 */
@Composable
private fun ServerInfoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier,
    ) {
        Text(
            text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episode 3-dot menu bottom sheet
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeMenuSheet(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null, // design principle #2
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                task.episode.name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            when (task.status) {
                // ROUND 121: WAITING_FOR_NETWORK behaves like an active task
                // in the menu — Pause takes it over (→ PAUSED, no auto-resume),
                // Cancel removes it.
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.RETRYING,
                DownloadStatus.WAITING_FOR_NETWORK,
                -> {
                    MenuOption("Pause", Icons.Filled.Pause) { onPause() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                DownloadStatus.PAUSED -> {
                    MenuOption("Resume", Icons.Filled.PlayArrow) { onResume() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                DownloadStatus.ERROR -> {
                    MenuOption("Retry", Icons.Filled.Refresh) { onRetry() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                else -> {}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MenuOption(
    label: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = if (isDestructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════
//  ROUND 120 (D-749): queue management — mode toggles, selection bar, reorder card
// ══════════════════════════════════════════════════════════════════════════

/**
 * The two mode doors under the action bar — labeled pill toggles in the same
 * visual family as the action bar's chips. "Select" enters multi-select
 * (rows grow checkboxes); "Reorder" flattens the queue into an ordered list.
 */
@Composable
private fun QueueModeToggles(
    reorderActive: Boolean,
    selectEnabled: Boolean,
    reorderEnabled: Boolean,
    onSelect: () -> Unit,
    onToggleReorder: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QueueModePill(
            icon = Icons.Filled.Checklist,
            label = "Select",
            active = false,
            enabled = selectEnabled,
            modifier = Modifier.weight(1f),
            onClick = onSelect,
        )
        QueueModePill(
            icon = Icons.Filled.SwapVert,
            label = if (reorderActive) "Done reordering" else "Reorder",
            active = reorderActive,
            enabled = reorderEnabled,
            modifier = Modifier.weight(1f),
            onClick = onToggleReorder,
        )
    }
}

@Composable
private fun QueueModePill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The multi-select contextual bar — replaces the action bar while selecting.
 * Row 1: exit + the live count + ONE select-all toggle (ROUND 121: the old
 * side-by-side check-all/clear-checks pair was confusing — the single button
 * shows check-all when anything is unchecked and clear when everything is
 * checked). Row 2: the three bulk operations in the action bar's chip style
 * (pause / resume / remove) — the screen exits selection mode after any of
 * them fires.
 */
@Composable
private fun QueueSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onPauseSelected: () -> Unit,
    onResumeSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Exit selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "$selectedCount selected",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleSelectAll) {
                    if (allSelected) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Deselect all",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            Icons.Filled.DoneAll,
                            contentDescription = "Select all",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectionActionChip(
                    icon = Icons.Filled.Pause,
                    label = "Pause",
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0,
                    onClick = onPauseSelected,
                )
                SelectionActionChip(
                    icon = Icons.Filled.PlayArrow,
                    label = "Resume",
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0,
                    onClick = onResumeSelected,
                )
                SelectionActionChip(
                    icon = Icons.Filled.Delete,
                    label = "Remove",
                    modifier = Modifier.weight(1f),
                    enabled = selectedCount > 0,
                    isDestructive = true,
                    onClick = onRemoveSelected,
                )
            }
        }
    }
}

/**
 * ROUND 121: the reorder row's status line — one label per lifecycle state
 * (the running task reads as running right in the reorder list).
 */
private fun reorderStatusLabel(task: DownloadTask): String = when (task.status) {
    DownloadStatus.DOWNLOADING -> "Downloading — ${task.progress}%"
    DownloadStatus.RETRYING -> "Retrying"
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    DownloadStatus.PAUSED -> "Paused — ${task.progress}%"
    DownloadStatus.ERROR -> "Failed"
    DownloadStatus.COMPLETED -> "Done"
    DownloadStatus.CANCELLED -> "Cancelled"
}

@Composable
private fun SelectionActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/**
 * The flat reorder list — ONE card, every queue task in its persisted order
 * with up/down controls. Each move goes through the VM (computed against the
 * live sorted queue, persisted as the full order — the queue's scheduler and
 * the display list both re-sort on the pref's change flow), so the
 * arrangement survives process death and IS the download order. Up/down
 * buttons (not drag) on purpose: the episode rows are variable-height, and
 * button moves are deterministic without gesture math.
 */
@Composable
private fun QueueReorderCard(
    queue: List<DownloadTask>,
    onCommitReorder: (orderedIds: List<Long>) -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column {
            // Header: what this list means + the exit.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(3.dp).height(20.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Reorder queue",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // ROUND 122 (R10): the drag instruction (the arrows are
                        // gone) — the running task stays pinned on top.
                        "Top of the list downloads first — long-press a row and drag",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onDone,
                ) {
                    Text(
                        "Done",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // The rows. `queue` arrives ALREADY sorted by the persisted order
            // (the ViewModel sorts on queueOrder.changes) — the position you
            // see is the position that downloads.
            //
            // ROUND 122 (R10): LONG-PRESS DRAG instead of the arrow buttons
            // (the device round: "I should be able to long press and drag them
            // up or down"). The list swaps LOCALLY during the drag; the FULL
            // order persists once on drop (commitReorder). The pinned
            // DOWNLOADING/RETRYING rows (the VM sorts them to the top) are
            // non-draggable and cannot be passed — the drag arranges the
            // start order of everything else, which IS what the scheduler
            // consumes.
            val dragListState = rememberLazyListState()
            val pinnedRows = queue.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING
            }
            val localQueue = remember { mutableStateListOf<DownloadTask>() }
            val dragState = rememberQueueDragDropState(dragListState) { from, to ->
                if (to >= pinnedRows) {
                    localQueue.add(to, localQueue.removeAt(from))
                }
            }
            // Resync ONLY while no drag is in flight — progress ticks mutate
            // the queue every second and would clobber an in-flight arrangement.
            LaunchedEffect(queue) {
                if (dragState.draggingItemIndex == null &&
                    localQueue.map { it.id } != queue.map { it.id }
                ) {
                    localQueue.clear()
                    localQueue.addAll(queue)
                }
            }
            LazyColumn(state = dragListState, modifier = Modifier.fillMaxWidth()) {
                items(localQueue.size, key = { localQueue[it].id }) { index ->
                    val task = localQueue[index]
                    val isDragging = dragState.draggingItemIndex == index
                    if (index > 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragState.draggingItemDelta else 0f
                            }
                            .then(
                                if (index >= pinnedRows) {
                                    Modifier.pointerInput(localQueue.size, pinnedRows) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                if (dragState.draggingItemIndex == null) {
                                                    dragState.onDragStart(index)
                                                }
                                            },
                                            onDrag = { change, amount ->
                                                if (dragState.draggingItemIndex == index) {
                                                    change.consume()
                                                    dragState.onDrag(amount)
                                                }
                                            },
                                            onDragEnd = {
                                                if (dragState.draggingItemIndex == index) {
                                                    onCommitReorder(localQueue.map { it.id })
                                                    dragState.onDragInterrupted()
                                                }
                                            },
                                            onDragCancel = {
                                                if (dragState.draggingItemIndex == index) {
                                                    dragState.onDragInterrupted()
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.episode.name.ifBlank {
                                    "Episode ${task.episode.episodeNumber.toInt()}"
                                },
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // ROUND 121 (reorder polish): the anime title AND
                            // the live status per row — the flattened list used
                            // to read as bare episode names with no sense of
                            // what's running / waiting / paused.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    task.content.title,
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "  •  ",
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    reorderStatusLabel(task),
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (task.status) {
                                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                                        DownloadStatus.WAITING_FOR_NETWORK -> MaterialTheme.colorScheme.tertiary
                                        DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                        // ROUND 122 (R10): the drag affordance (long-press the
                        // row, or this handle) replaces the up/down arrows.
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = if (index >= pinnedRows) {
                                "Long-press and drag to reorder"
                            } else {
                                "Downloading — pinned to the top"
                            },
                            tint = if (index >= pinnedRows) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// D-151-fix: made internal (was private) so DownloadedFilesScreen can reuse it
// (same package — com.confused.anikuta.feature.download).
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

/**
 * D-215: Formats a download speed (bytes/sec) into a human-readable string.
 * e.g. 1500000 → "1.4 MB/s", 500000 → "488 KB/s", 800 → "800 B/s".
 */
internal fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
    bytesPerSecond < 1024 * 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024))
    else -> "%.1f GB/s".format(bytesPerSecond / (1024.0 * 1024 * 1024))
}
