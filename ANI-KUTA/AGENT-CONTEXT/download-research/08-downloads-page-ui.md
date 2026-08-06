# 08 — Downloads Page UI

> All line references: `feature/download/src/main/java/app/confused/anikuta/feature/download/`. The main page is `DownloadsScreen.kt` (570 lines).

## 1. Navigation: how the user reaches the page

The Downloads page is **NOT** a bottom-nav tab. It's reached via the **More** menu:

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadsMoreEntries.kt` (37 lines)

```kotlin
@Composable
fun DownloadsMoreEntries(
    onOpenDownloads: () -> Unit,
) {
    Column {
        MoreSectionLabel(text = "Library")
        MoreListRow(
            icon = Icons.Filled.Download,
            title = "Downloads",
            subtitle = "Manage downloaded episodes and the download queue",
            onClick = onOpenDownloads,
        )
    }
}
```

The host (AnikutaRoot) wires `DownloadsMoreEntries` into the More screen's `LazyColumn` with a single `item { ... }` call. Tapping it pushes `DownloadsScreen` onto the navigator.

The Downloads screen has two top-bar action icons:
- **Download icon** (only if `state.downloaded.isNotEmpty()`) — opens `DownloadedFilesScreen`.
- **Settings gear** — opens `DownloadSettingsScreen`.

## 2. `DownloadsScreen` layout

**File**: `DownloadsScreen.kt:80-221` (the main composable)

```
┌────────────────────────────────────────────┐
│  Downloads                       ⬇ ⚙       │  ← CollapsingHeader (⬇ only if downloaded non-empty)
├────────────────────────────────────────────┤
│  ⏸  ▶  ↻  ✕                                │  ← DownloadActionBar (only if queue non-empty)
├────────────────────────────────────────────┤
│  3 downloading  1 queued  2 paused          │  ← Summary chips (only if queue non-empty)
├────────────────────────────────────────────┤
│  ┌──────────────────────────────────────┐  │
│  │ ▌ Jujutsu Kaisen                  3  │  │  ← AnimeSectionCard (one per anime title)
│  │──────────────────────────────────────│  │
│  │ Episode 1  [Vidstreaming] [SUB] [1080p] 35% │  ← EpisodeRow
│  │ ─────────●─────────  ───────────  ⋮  │  │
│  │──────────────────────────────────────│  │
│  │ Episode 2  [Vidstreaming] [SUB] [1080p] 78% │
│  │ ───────────────●─────  ───────────  ⋮  │  │
│  │──────────────────────────────────────│  │
│  │ Episode 3  [Vidstreaming] [SUB] [1080p] Queued │
│  │                                    ⋮  │  │
│  └──────────────────────────────────────┘  │
│  ┌──────────────────────────────────────┐  │
│  │ ▌ Frieren                          1  │  │  ← Another AnimeSectionCard
│  │──────────────────────────────────────│  │
│  │ Episode 1  [Streamtape] [DUB] [720p] Failed │
│  │ Server returned 404 error          ⋮  │  │
│  └──────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

### Top-level structure (lines 125-203):

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    CollapsingHeader(
        title = "Downloads",
        collapsed = collapsed,
        actions = {
            if (state.downloaded.isNotEmpty()) {
                IconButton(onClick = onOpenDownloaded) { Icon(Icons.Filled.Download, ...) }
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, ...) }
        },
    )

    if (queue.isNotEmpty()) {
        DownloadActionBar(
            hasActive = downloading > 0 || queued > 0,
            hasPaused = paused > 0,
            hasFailed = failed > 0,
            hasAny = queue.isNotEmpty(),
            onPauseAll = { queue.filter { it.status == DOWNLOADING || it.status == QUEUED }.forEach { viewModel.pause(it.id) } },
            onResumeAll = { queue.filter { it.status == PAUSED }.forEach { viewModel.resume(it.id) } },
            onRetryAll = { queue.filter { it.status == ERROR }.forEach { viewModel.retry(it.id) } },
            onCancelAll = { queue.forEach { viewModel.cancel(it.id) } },
        )
    }

    if (queue.isNotEmpty()) {
        Row(...) {  // Summary chips
            if (downloading > 0) StatChip("$downloading", "downloading", primary)
            if (queued > 0) StatChip("$queued", "queued", onSurfaceVariant)
            if (paused > 0) StatChip("$paused", "paused", onSurfaceVariant)
            if (failed > 0) StatChip("$failed", "failed", error)
        }
    }

    if (queue.isEmpty() && state.downloaded.isEmpty()) {
        DownloadsEmptyStateContent()  // centered "No downloads yet" + icon
    } else {
        LazyColumn(state = lazyListState, ...) {
            groupedByAnime.forEach { (animeTitle, downloads) ->
                item(key = "section_$animeTitle") {
                    AnimeSectionCard(animeTitle, downloads, ...)
                }
            }
        }
    }
}

// 3-dot menu bottom sheet (per-episode actions)
if (menuTaskId != null) {
    val task = queue.firstOrNull { it.id == menuTaskId }
    if (task != null) {
        EpisodeMenuSheet(task, onDismiss = ..., onPause = ..., onResume = ..., onCancel = ..., onRetry = ...)
    } else {
        menuTaskId = null
    }
}
```

### Key observations:

- **Single section**: the page shows ONLY the live queue (grouped by anime title). Completed downloads live on a SEPARATE page (`DownloadedFilesScreen`) reached via the top-bar Download icon. This is different from what one might expect (a single page with both queue + downloaded sections) — but the old project deliberately split them.
- **Auto-clear completed**: COMPLETED tasks are auto-removed from the active list after 10 seconds (see §6 below), so they don't linger in this page. They're still on disk + appear on `DownloadedFilesScreen`.
- **Grouping**: queue is grouped by `anime.title` (NOT contentId) — see honest notes in `00-overview.md`. Minor bug for same-title anime.
- **POST_NOTIFICATIONS permission** is requested on first entry (lines 92-107) — see `06-notifications-foreground-service.md` §8.

## 3. `DownloadActionBar` (bulk operations)

**Lines 227-258**:
```kotlin
@Composable
private fun DownloadActionBar(
    hasActive: Boolean, hasPaused: Boolean, hasFailed: Boolean, hasAny: Boolean,
    onPauseAll: () -> Unit, onResumeAll: () -> Unit, onRetryAll: () -> Unit, onCancelAll: () -> Unit,
) {
    val actions = mutableListOf<Pair<ImageVector, () -> Unit>>()
    if (hasActive) actions.add(Icons.Filled.Pause to onPauseAll)
    if (hasPaused) actions.add(Icons.Filled.PlayArrow to onResumeAll)
    if (hasFailed) actions.add(Icons.Filled.Refresh to onRetryAll)
    if (hasAny) actions.add(Icons.Filled.Close to onCancelAll)
    if (actions.isEmpty()) return

    Surface(...) {
        Row(...) {
            actions.forEach { (icon, action) ->
                Surface(modifier = Modifier.weight(1f), onClick = action) {
                    Box { Icon(icon, ...) }
                }
            }
        }
    }
}
```

Buttons appear conditionally:
- **Pause all** (only if `hasActive`)
- **Resume all** (only if `hasPaused`)
- **Retry all** (only if `hasFailed`)
- **Cancel all** (always if `hasAny`)

Each button has equal weight (`Modifier.weight(1f)`) — so the bar resizes to fit.

## 4. `AnimeSectionCard` (one per anime)

**Lines 278-329**:

A `Surface` card containing:
- Header: accent bar (3dp wide × 20dp tall primary color) + anime title (ExtraBold 14sp) + episode count badge.
- Episode rows: separated by 1dp horizontal lines.

```kotlin
@Composable
private fun AnimeSectionCard(
    animeTitle: String,
    downloads: List<DownloadTask>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onMenu: (Long) -> Unit,
) {
    Surface(...) {
        Column {
            Row {  // Header
                Surface(modifier = Modifier.width(3.dp).height(20.dp), color = primary) {}
                Spacer(Modifier.width(10.dp))
                Text(animeTitle, ...)
                Surface(shape = RoundedCornerShape(6.dp), color = secondaryContainer) {
                    Text("${downloads.size}", ...)
                }
            }
            downloads.forEachIndexed { index, task ->
                if (index > 0) { Box(... 1dp divider ...) }
                Surface(...) {
                    EpisodeRow(task = task, onMenu = { onMenu(task.id) })
                }
            }
        }
    }
}
```

## 5. `EpisodeRow` (per-task row inside the section card)

**Lines 336-429**:

```
┌────────────────────────────────────────────────────┐
│ Episode 1                                          │  ← epName (or "Episode N" if blank)
│ [Vidstreaming] [SUB] [1080p] [35 MB / 90 MB]  35%  │  ← InfoPills row + PercentagePill
│ ──────────●───────────────────                     │  ← LinearProgressIndicator (only DOWNLOADING/PAUSED)
│                                                    │
│                                              ⋮     │  ← 3-dot menu (MoreVert)
└────────────────────────────────────────────────────┘
```

- **Episode name**: `task.request.episode.name.ifBlank { "Episode ${task.request.episode.episodeNumber.toInt()}" }`.
- **Pills row**: server pill + audio pill + quality pill + size pill (only when DOWNLOADING/PAUSED) + status pill on the right.
- **Progress bar**: 6dp tall, primary color, only when DOWNLOADING or PAUSED.
- **Error message**: below the bar, 10sp, error color, when status is ERROR.
- **3-dot menu**: opens `EpisodeMenuSheet` (see §7).

The status pill on the right varies by status:
- `DOWNLOADING / PAUSED` → `PercentagePill("${progress}%")` (primary-tinted).
- `QUEUED` → `InfoPill("Queued")`.
- `ERROR` → `ErrorPill("Failed")`.
- `COMPLETED` → `InfoPill("Done", highlight=true)` (primary-tinted).

Pill components:
- `InfoPill(text, highlight)` — surfaceVariant or primary@0.15f background.
- `SizePill(text)` — surface background, smaller text.
- `PercentagePill(text)` — primary@0.15f background, ExtraBold.
- `ErrorPill(text)` — error@0.15f background, ExtraBold.

## 6. `DownloadViewModel` — the state holder

**File**: `DownloadViewModel.kt` (105 lines)

```kotlin
class DownloadViewModel(
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    init {
        // Combine active + completed + folder-ready into the UI state.
        viewModelScope.launch {
            combine(
                manager.activeDownloads,
                manager.completedDownloads,
                preferences.downloadFolderUri().changes(),
            ) { active, completed, folderUri ->
                DownloadUiState(
                    queue = active,
                    downloaded = groupByAnime(completed),
                    folderReady = folderUri.isNotBlank(),
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }

        // ── Auto-clear completed entries after 10 seconds ──
        viewModelScope.launch {
            manager.activeDownloads.collect { active ->
                active.filter { it.status == DownloadStatus.COMPLETED }.forEach { task ->
                    launch {
                        delay(10_000)
                        manager.removeFromQueue(task.id)
                    }
                }
            }
        }
    }

    fun pause(taskId: Long) = viewModelScope.launch { manager.pauseDownload(taskId) }
    fun resume(taskId: Long) = viewModelScope.launch { manager.resumeDownload(taskId) }
    fun cancel(taskId: Long) = viewModelScope.launch { manager.cancelDownload(taskId) }
    fun retry(taskId: Long) = viewModelScope.launch { manager.retryDownload(taskId) }
    fun deleteEpisode(taskId: Long) = viewModelScope.launch { manager.deleteDownload(taskId) }
    fun deleteAnime(contentId: String) = viewModelScope.launch { manager.deleteAnimeDownloads(contentId) }

    fun setDownloadFolder(treeUriString: String) {
        try { manager.setDownloadFolder(treeUriString) } catch (e: Exception) { ... }
    }

    private fun groupByAnime(tasks: List<DownloadTask>): Map<DownloadedAnimeKey, List<DownloadTask>> {
        return tasks
            .groupBy {
                DownloadedAnimeKey(
                    contentId = it.request.anime.contentId,
                    title = it.request.anime.title,
                    coverUrl = it.request.anime.coverUrl,
                    coverColor = it.request.anime.coverColor,
                )
            }
            .toSortedMap(compareBy { it.title.lowercase() })
    }
}
```

### Auto-clear after 10 seconds — how it works

Per the KDoc (line 51-55):
> "Per the owner's request: 'after downloading, the entries automatically clear out after 10 seconds.' This removes COMPLETED tasks from the active queue (the file stays on disk). Each completed task gets a 10-second delay before removal."

Implementation: collect `manager.activeDownloads`, for each COMPLETED task launch a coroutine that waits 10s then calls `manager.removeFromQueue(task.id)`. If the task is already removed by then, `removeFromQueue` is a no-op (it checks for `task.status == COMPLETED` first).

**Note**: this fires a NEW coroutine per completed task per state emission. If the same task appears in multiple emissions (it will, until removed), multiple coroutines are launched. Each one's `delay(10s)` is independent. Only the first to wake up actually removes the task; the rest no-op. Wasteful but harmless.

## 7. `EpisodeMenuSheet` (per-task actions)

**Lines 468-508** — a `ModalBottomSheet` with `dragHandle = null`:

```
┌────────────────────────────────────────────┐
│ Episode 1                                  │  ← header
├────────────────────────────────────────────┤
│  ⏸  Pause                                  │  ← MenuOption (DOWNLOADING/QUEUED)
│  ↻  Retry                                  │  ← MenuOption (ERROR)
│  ▶  Resume                                 │  ← MenuOption (PAUSED)
│  ✕  Cancel                            (red)│  ← MenuOption (destructive, always)
└────────────────────────────────────────────┘
```

Action options depend on status:
- `DOWNLOADING / QUEUED` → Pause + Cancel.
- `PAUSED` → Resume + Cancel.
- `ERROR` → Retry + Cancel.
- (No options for `COMPLETED` — completed tasks are auto-cleared + managed on the DownloadedFilesScreen.)

`MenuOption(label, icon, isDestructive, onClick)` — `isDestructive` renders the row in error color (used for Cancel).

## 8. `DownloadUiState`

**File**: `DownloadUiState.kt` (45 lines)

```kotlin
data class DownloadUiState(
    val queue: List<DownloadTask> = emptyList(),
    val downloaded: Map<DownloadedAnimeKey, List<DownloadTask>> = emptyMap(),
    val folderReady: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = queue.isEmpty() && downloaded.isEmpty()
}

data class DownloadedAnimeKey(
    val contentId: String,
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)

val DownloadTask.isInQueueSection: Boolean
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.PAUSED ||
        status == DownloadStatus.ERROR
```

- `queue` — the live queue (from `manager.activeDownloads`).
- `downloaded` — completed tasks grouped by anime (sorted alphabetically by title).
- `folderReady` — false until the user picks a SAF folder.
- `isLoading` — true initially, set to false on first combine emission.

## 9. `DownloadedFilesScreen` (the separate "Downloaded" page)

**File**: `DownloadedFilesScreen.kt` (206 lines)

Reached via the top-bar Download icon. Shows COMPLETED tasks grouped by anime:

```
┌────────────────────────────────────────────┐
│  Downloaded                                │  ← CollapsingHeader
├────────────────────────────────────────────┤
│  ┌──────────────────────────────────────┐  │
│  │ [cover]  Jujutsu Kaisen         3 ep │  │  ← DownloadedAnimeCard header (tappable to expand)
│  │          3 episodes downloaded    🗑  │  │
│  │                                  ⌄   │  │
│  │──────────────────────────────────────│  │
│  │ EP 1  Episode 1   [1080p]    🗑      │  │  ← per-episode row (tap = play offline, 🗑 = delete)
│  │ EP 2  Episode 2   [1080p]    🗑      │  │
│  │ EP 3  Episode 3   [1080p]    🗑      │  │
│  └──────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

- Each anime card has a "delete all" (🗑) button + expand toggle.
- Each episode row: tap to play offline (calls `onPlayEpisode(contentId, episodeUrl)` host callback), per-episode 🗑 to delete.
- Episodes sorted by episode number ascending.

Empty state: centered "No downloaded files" + icon + "Downloaded episodes will appear here" hint.

## 10. `DownloadedAnimeCard` (component, in `components/`)

**File**: `components/DownloadedAnimeCard.kt` (183 lines)

Note: this is a SEPARATE component from the `DownloadedAnimeCard` private composable inside `DownloadedFilesScreen.kt:118-206` — they're nearly identical but the one in `components/` is exported (public). The DownloadedFilesScreen actually uses its own private copy. **Code duplication** — should be consolidated.

The `components/DownloadedAnimeCard` exposes:
```kotlin
@Composable
fun DownloadedAnimeCard(
    key: DownloadedAnimeKey,
    episodes: List<DownloadTask>,
    onDeleteEpisode: (taskId: Long) -> Unit,
    onDeleteAll: () -> Unit,
)
```

Header: cover thumbnail (44×62) + title + "N episodes downloaded" + delete-all icon + expand/collapse icon.
Expanded: per-episode rows (`DownloadedEpisodeRow` private composable) with episode number + name + delete button.

## 11. `QueueRow` (component, in `components/`)

**File**: `components/QueueRow.kt` (244 lines)

A row for the live queue — but **NOT used by the current `DownloadsScreen`** (which uses its own private `EpisodeRow` inside `AnimeSectionCard`). This component is leftover from an earlier design (the KDoc mentions "the OLD_ANIKUTA's download queue screen" — the layout was redesigned). Currently dead code in the codebase.

The component:
```kotlin
@Composable
fun QueueRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
)
```

Layout: cover (48×68) + title/episode/progress column + inline action buttons (Pause/Resume/Retry/Cancel based on status). Design: surfaceVariant@0.4f card, RoundedCornerShape(12dp), RobotoFamily, #B1F256 accents.

**Honest note**: should be deleted in the new project — `EpisodeRow` (inside `AnimeSectionCard`) supersedes it.

## 12. `DownloadsEmptyState` (component)

**File**: `components/DownloadsEmptyState.kt` (96 lines)

Two variants:
- `needsFolder = true` — "Choose a download folder" + "Select folder" button.
- `needsFolder = false` — "No downloads yet" + "Tap the download button on an episode to save it for offline viewing."

**Note**: `DownloadsScreen.kt` does NOT actually use this component — it has its own private `DownloadsEmptyStateContent` (line 542-562) which is simpler. So this component is also effectively dead code.

## 13. `DragReorderableList` (component)

**File**: `components/DragReorderableList.kt` (192 lines)

Used in `DownloadSettingsScreen` for reordering preference lists (quality / audio / server). NOT used for reordering the download queue (which is FIFO — see `02-queue-management.md` §8).

Features:
- Per-item drag handle (48dp × 48dp) on the right.
- Dragged item follows finger via `graphicsLayer.translationY` (draw-phase only — no recomposition).
- Non-dragged items snap to new positions (no animation — intentional, per the KDoc, to avoid scroll jank).
- `mutableStateListOf` for the internal reordered copy — calls `onReorder` only on drag END (not during).
- `pointerInput(Unit)` — stable key, gesture never cancelled.

```kotlin
@Composable
fun DragReorderableList(
    items: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Only takes `List<String>`** — not generic. Used for quality/audio/server name lists. Would need to be generalized for other use cases.

## 14. Summary — what the new project should replicate

1. **Two separate pages**: `DownloadsScreen` (live queue) + `DownloadedFilesScreen` (completed library). Reached via the More menu + an in-page top-bar icon.
2. **Group queue by anime** in `AnimeSectionCard` (accent bar + title + count badge + per-episode rows). **Group downloaded by anime** in `DownloadedAnimeCard` (cover + title + episode list + delete-all).
3. **Bulk action bar** (Pause all / Resume all / Retry all / Cancel all) — conditional on which states are present.
4. **Summary chips** for downloading/queued/paused/failed counts.
5. **Per-episode 3-dot menu** (bottom sheet) with status-dependent actions.
6. **Auto-clear completed after 10s** — keeps the queue clean.
7. **DragReorderableList** for preference lists in settings (NOT for queue reordering — that's FIFO).
8. **Don't replicate the dead code** (`QueueRow`, `DownloadsEmptyState` component) — the in-screen private composables supersede them.
9. **Consider**: a single page with both queue + downloaded sections (instead of two pages) — could be a UX improvement. Up to the design.
