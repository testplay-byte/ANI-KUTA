# 15 — Downloads Page UI (replicate exactly) + "download fails when playing another episode" bug

> All file:line references are relative to `REFERENCES/old-kuta/ANIKUTA/`.
> This doc supersedes nothing — it complements `08-downloads-page-ui.md` (the higher-level UI summary) by going screen-by-screen + composable-by-composable for replication, and it complements `10-player-integration.md` (offline playback) by diagnosing the streaming-vs-download interference bug.

## Table of contents

- **Part A** — Downloads page UI (replicate exactly)
  - A.1 Screen list + navigation
  - A.2 `DownloadsScreen` — full layout, top-to-bottom
  - A.3 `DownloadedFilesScreen` — completed-files browser
  - A.4 `DownloadUiState` — the UI state model
  - A.5 `DownloadViewModel` — state + actions
  - A.6 `DownloadedAnimeCard` (component file) — what's actually used vs dead code
  - A.7 `QueueRow` (component file) — what's actually used vs dead code
  - A.8 `DownloadsEmptyState` (component file) — what's actually used vs dead code
  - A.9 Per-episode download control on the details page (`EpisodeDownloadControl` + `EpisodeDownloadState` + `EpisodesSection`)
  - A.10 Design tokens to preserve verbatim
  - A.11 "Replicate exactly" checklist
- **Part B** — The "download fails when playing another episode" bug
  - B.1 User report
  - B.2 What is NOT the cause (ruled out)
  - B.3 The actual root cause: extension local-proxy-server churn
  - B.4 End-to-end trace of the bug
  - B.5 Why the OLD architecture cannot fix this
  - B.6 The fix the NEW project should implement
  - B.7 Architectural rules to prevent the bug class

---

# Part A — Downloads page UI (replicate exactly)

## A.1 Screen list + navigation

There are **TWO** screens in `:feature:download`:

| Screen | File | Purpose | Reached from |
|---|---|---|---|
| `DownloadsScreen` | `feature/download/.../DownloadsScreen.kt` (569 lines) | Live queue (downloading / queued / paused / errored) | More menu → "Downloads" row |
| `DownloadedFilesScreen` | `feature/download/.../DownloadedFilesScreen.kt` (206 lines) | Completed downloads grouped by anime | The "⬇" icon in `DownloadsScreen`'s top bar (only visible when `state.downloaded.isNotEmpty()`) |

There is also a `DownloadSettingsScreen` (separate file, covered in `07-settings-preferences.md`) opened from the "⚙" icon in `DownloadsScreen`'s top bar.

**Navigation contract** (`DownloadsScreen.kt:96-103`):

```kotlin
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDownloaded: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
)
```

`onBack` is in the signature but the screen does not currently render a back button (the `CollapsingHeader` doesn't get an `onBack` slot here) — the host's nav stack handles back-press via Voyager. `onOpenSettings` and `onOpenDownloaded` wire to the two top-bar icons.

**Navigation contract** (`DownloadedFilesScreen.kt:62-69`):

```kotlin
@Composable
fun DownloadedFilesScreen(
    onBack: () -> Unit,
    onPlayEpisode: ((String, String) -> Unit)? = null,   // (contentId, episodeUrl) → host launches WatchScreen offline
    viewModel: DownloadViewModel = koinViewModel(),
)
```

Note `DownloadedFilesScreen` reuses the SAME `DownloadViewModel` instance (Koin-scoped) — it reads the same `state.downloaded` map as `DownloadsScreen`.

## A.2 `DownloadsScreen` — full layout, top-to-bottom

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadsScreen.kt`

### Top-level structure (lines 117-203)

A single `Column(Modifier.fillMaxSize())` containing, in order:

1. `CollapsingHeader(title = "Downloads", collapsed = collapsed, actions = { ... })`
   - `collapsed` is derived from the `LazyListState` (`firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 20`).
   - Actions slot contains up to TWO `IconButton`s:
     - A `Icons.Filled.Download` icon (`onOpenDownloaded`) — ONLY rendered when `state.downloaded.isNotEmpty()`.
     - A `Icons.Filled.Settings` icon (`onOpenSettings`) — always rendered.
   - Both icons use `tint = MaterialTheme.colorScheme.onBackground`.

2. A `LaunchedEffect(Unit)` that requests `POST_NOTIFICATIONS` permission on Android 13+ (TIRAMISU) if not already granted. This is fired once per composition (key = `Unit`). Uses `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.

3. **Bulk action bar** (`DownloadActionBar`) — only rendered `if (queue.isNotEmpty())`. Contains Pause-all / Resume-all / Retry-all / Cancel-all buttons (conditional on which states are present).

4. **Summary chips row** — only rendered `if (queue.isNotEmpty())`. A `Row` of `StatChip`s for each non-zero count: `downloading`, `queued`, `paused`, `failed`.

5. **Main content** — one of two branches:
   - `if (queue.isEmpty() && state.downloaded.isEmpty())` → `DownloadsEmptyStateContent()` (the in-file private composable, NOT the `DownloadsEmptyState.kt` component — see A.8).
   - `else` → `LazyColumn(state = lazyListState, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp))` of `AnimeSectionCard`s, one per anime title.

6. **3-dot menu bottom sheet** — `if (menuTaskId != null)`, render `EpisodeMenuSheet` for the matched task. Cleared on any action tap.

### Status count derivation (lines 138-142)

```kotlin
val queue = state.queue
val downloading = queue.count { it.status == DownloadStatus.DOWNLOADING }
val queued     = queue.count { it.status == DownloadStatus.QUEUED }
val paused     = queue.count { it.status == DownloadStatus.PAUSED }
val failed     = queue.count { it.status == DownloadStatus.ERROR }
val hasActive  = downloading > 0 || queued > 0
```

### Grouping by anime (lines 144-146)

```kotlin
val groupedByAnime = remember(queue) {
    queue.groupBy { it.request.anime.title }.toList()
}
```

⚠️ **Known minor bug** (per `00-overview.md` §6): grouping by `anime.title` (not `contentId`) would conflate two different anime with the same title. The "Downloaded" section groups by `DownloadedAnimeKey(contentId, ...)` correctly. The new project SHOULD group the live queue by `contentId` instead.

### `DownloadActionBar` (lines 218-249)

A `Surface` with `surfaceVariant.copy(alpha = 0.3f)` background, `RoundedCornerShape(12.dp)`, padding 6dp horizontal/vertical. Inside: a `Row(padding = 8.dp, spacedBy = 6.dp)` of `Surface(weight = 1f, RoundedCornerShape(10.dp), surfaceVariant.copy(alpha = 0.5f), onClick = action)` chips.

The chips are conditionally added in this order:
- `Icons.Filled.Pause` — if `hasActive` (DOWNLOADING or QUEUED present)
- `Icons.Filled.PlayArrow` — if `hasPaused` (PAUSED present)
- `Icons.Filled.Refresh` — if `hasFailed` (ERROR present)
- `Icons.Filled.Close` — if `hasAny` (always when the bar is visible)

Each chip is a 22dp icon centered in a fillMaxWidth column with 12dp vertical padding. Tint: `onSurfaceVariant`.

### `StatChip` (lines 254-263)

A `Surface(RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f))` containing a `Row(padding horizontal = 8.dp, vertical = 4.dp)` of:
- `Text(count, RobotoFamily, 11sp, Bold, color = color)`
- `Text(label, RobotoFamily, 10sp, color = color.copy(alpha = 0.8f))`

The chip colour matches the status: `downloading` → `primary`, `queued`/`paused` → `onSurfaceVariant`, `failed` → `error`.

### `AnimeSectionCard` (lines 268-310) — ONE card per anime

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
)
```

Inside, a `Column`:
1. **Header row** — `Row(padding horizontal = 14.dp, vertical = 12.dp, verticalAlignment = CenterVertically)`:
   - 3dp-wide × 20dp-tall accent bar (`Surface(RoundedCornerShape(2.dp), color = primary)`).
   - 10dp spacer.
   - Anime title (`Text`, RobotoFamily, 14sp, ExtraBold, onSurface, maxLines=1, Ellipsis, `weight(1f)`).
   - 8dp spacer.
   - Episode count badge (`Surface(RoundedCornerShape(6.dp), color = secondaryContainer)` with `Text(downloads.size, RobotoFamily, 11sp, Bold, onSecondaryContainer, padding horizontal=8, vertical=3)`).
2. **Episode rows** — `downloads.forEachIndexed { index, task -> ... }`:
   - Between rows: a 1dp `Box` divider (`fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(outlineVariant.copy(alpha = 0.5f))`).
   - Each row is wrapped in `Surface(color = surfaceVariant.copy(alpha = 0.2f))` containing `EpisodeRow(task, onMenu)`.

### `EpisodeRow` (lines 318-407) — a single episode inside an anime section card

A `Row(fillMaxWidth, verticalAlignment = Top)`:
- **Left column** (`weight(1f), padding horizontal = 10.dp, vertical = 10.dp`):
  - Episode name: `task.request.episode.name.ifBlank { "Episode ${task.request.episode.episodeNumber.toInt()}" }` — RobotoFamily, 13sp, SemiBold, onSurface, maxLines=1, Ellipsis.
  - 4dp spacer.
  - **Info pills row** — `Row(fillMaxWidth, spacedBy = 4.dp, CenterVertically)`:
    - `InfoPill(videoServer)` — if non-blank.
    - `InfoPill(videoAudio.uppercase())` — if non-blank.
    - `InfoPill(videoQuality)` — if non-blank.
    - `SizePill("${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")` — only when DOWNLOADING or PAUSED, and `totalBytes > 0`. If `totalBytes <= 0`, just `formatBytes(downloadedBytes)`.
    - `Spacer(weight = 1f)` — pushes the status pill to the right.
    - Right side: status-dependent pill:
      - DOWNLOADING or PAUSED → `PercentagePill("${task.progress}%")`
      - QUEUED → `InfoPill("Queued")`
      - ERROR → `ErrorPill("Failed")`
      - COMPLETED → `InfoPill("Done", highlight = true)`
  - **Progress bar** (only when DOWNLOADING or PAUSED) — 6dp spacer, then `LinearProgressIndicator(progress = { (task.progress / 100f).coerceIn(0f, 1f) }, modifier = fillMaxWidth().height(6.dp), color = primary, trackColor = surface)`.
  - **Error message** (only when ERROR) — 4dp spacer, then `Text(errorMessage, RobotoFamily, 10sp, error, maxLines=2, Ellipsis)`.
- **Right** — `Box(padding top = 6.dp, end = 6.dp)` containing a 36dp `Surface(RoundedCornerShape(10.dp), surfaceVariant.copy(alpha = 0.5f), onClick = onMenu)` with a 20dp `Icons.Filled.MoreVert` centered (`tint = onSurfaceVariant`).

### Pill composables (lines 412-460)

```kotlin
@Composable private fun InfoPill(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlight) primary.copy(alpha = 0.15f) else surfaceVariant,
    ) {
        Text(text, RobotoFamily, 10sp, SemiBold,
            color = if (highlight) primary else onSurfaceVariant,
            maxLines = 1, softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable private fun SizePill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = surface) {
        Text(text, RobotoFamily, 10sp, color = onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable private fun PercentagePill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = primary.copy(alpha = 0.15f)) {
        Text(text, RobotoFamily, 10sp, Bold, color = primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable private fun ErrorPill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = error.copy(alpha = 0.15f)) {
        Text(text, RobotoFamily, 10sp, Bold, color = error,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
```

### `EpisodeMenuSheet` (lines 465-510) — the 3-dot menu bottom sheet

```kotlin
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
        dragHandle = null,                                                  // design principle #2
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(task.request.episode.name, RobotoFamily, 14sp, ExtraBold, onSurface)
            Spacer(Modifier.height(12.dp))
            when (task.status) {
                DOWNLOADING, QUEUED -> {
                    MenuOption("Pause", Icons.Filled.Pause) { onPause() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                PAUSED -> {
                    MenuOption("Resume", Icons.Filled.PlayArrow) { onResume() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                ERROR -> {
                    MenuOption("Retry", Icons.Filled.Refresh) { onRetry() }
                    MenuOption("Cancel", Icons.Filled.Close, isDestructive = true) { onCancel() }
                }
                else -> {}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

### `MenuOption` (lines 514-525)

A `Row(fillMaxWidth, padding vertical = 10.dp, clickable(onClick))` with:
- 22dp icon, `tint = if (isDestructive) error else onSurface`.
- 16dp spacer.
- `Text(label, RobotoFamily, 14sp, color = if (isDestructive) error else onSurface)`.

### `DownloadsEmptyStateContent` (lines 532-557) — the in-file empty state

```kotlin
@Composable
private fun DownloadsEmptyStateContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Download, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("No downloads yet", RobotoFamily, 16sp, ExtraBold, onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Download episodes from the anime detail page",
                RobotoFamily, 12sp, color = onSurfaceVariant)
        }
    }
}
```

### `formatBytes` helper (lines 561-567)

```kotlin
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
```

## A.3 `DownloadedFilesScreen` — completed-files browser

**File**: `feature/download/.../DownloadedFilesScreen.kt` (206 lines)

### Top-level structure (lines 71-118)

A `Column(fillMaxSize)`:
1. `CollapsingHeader(title = "Downloaded", collapsed = collapsed)` — NO actions slot.
2. Two branches:
   - `if (downloaded.isEmpty())` → centered empty state (icon + "No downloaded files" + "Downloaded episodes will appear here").
   - `else` → `LazyColumn(state, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp))` of one `DownloadedAnimeCard` per anime key.

The `downloaded` map is `Map<DownloadedAnimeKey, List<DownloadTask>>` — already grouped + sorted by the ViewModel (see A.5).

### `DownloadedAnimeCard` — IN-FILE private (lines 122-202)

⚠️ **Important**: `DownloadedFilesScreen.kt` defines its OWN private `DownloadedAnimeCard` composable. The component file `components/DownloadedAnimeCard.kt` is dead code (see A.6). The new project should use the in-file version.

Layout:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
) {
    Column {
        // Header — clickable to expand/collapse
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover (44×62) — only if coverUrl non-blank
            if (!animeKey.coverUrl.isNullOrBlank()) {
                AsyncImage(model = animeKey.coverUrl, contentDescription = animeKey.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 44.dp, height = 62.dp)
                        .clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(10.dp))
            }
            // Title + count column
            Column(modifier = Modifier.weight(1f)) {
                Text(animeKey.title, RobotoFamily, 14sp, ExtraBold, onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${episodes.size} episode${if (episodes.size != 1) "s" else ""}",
                    RobotoFamily, 12sp, color = onSurfaceVariant)
            }
            // Delete-all icon button
            IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete all",
                    tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            // Expand/collapse chevron (NOTE: doesn't rotate — always ChevronRight)
            Icon(Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        // Episode list — only when expanded (default true)
        if (expanded) {
            episodes.sortedBy { it.request.episode.episodeNumber }.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onPlay(task.request.episode.episodeUrl) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("EP ${task.request.episode.episodeNumber.toInt()}",
                        RobotoFamily, 12sp, Bold, primary, modifier = Modifier.width(48.dp))
                    Text(task.request.episode.name.ifBlank { "Episode ${task.request.episode.episodeNumber.toInt()}" },
                        RobotoFamily, 12sp, onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    // Quality pill (if present)
                    if (task.request.videoQuality.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(4.dp), color = surfaceVariant) {
                            Text(task.request.videoQuality, RobotoFamily, 9sp, onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    // Delete-episode icon button
                    IconButton(onClick = { onDelete(task.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete episode",
                            tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
```

⚠️ Note: `expanded` defaults to `true` here (so the list is open by default). The chevron is `Icons.Filled.ChevronRight` and never rotates — a minor visual inconsistency in the old project. The new project should either rotate it or use `ExpandMore`/`ExpandLess`.

## A.4 `DownloadUiState` — the UI state model

**File**: `feature/download/.../DownloadUiState.kt` (45 lines)

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
    val contentId: String,        // e.g. "al:154587"
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)

/** Whether a task's status means it shows in the live queue section. */
val DownloadTask.isInQueueSection: Boolean
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.PAUSED ||
        status == DownloadStatus.ERROR
```

Notes:
- `queue` is the LIVE list (QUEUED + DOWNLOADING + PAUSED + ERROR + COMPLETED — until the 10s auto-clear removes COMPLETED entries).
- `downloaded` is a sorted map keyed by `DownloadedAnimeKey` (sorted alphabetically by lowercase title — see `groupByAnime` in the ViewModel).
- `folderReady` is `false` until the user picks a SAF folder — but the screen doesn't currently use it (the in-file empty state doesn't gate on it; the `DownloadsEmptyState` component file does, but that's dead code — see A.8).
- `isLoading` is `true` until the first state combine emits; not currently surfaced in the UI.

The `DownloadTask` itself lives in `:core:download` (`DownloadTask.kt`, see Part B for its fields).

## A.5 `DownloadViewModel` — state + actions

**File**: `feature/download/.../DownloadViewModel.kt` (105 lines)

### State assembly (`init` block, lines 33-50)

```kotlin
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
    // Per the owner's request: "after downloading, the entries automatically
    // clear out after 10 seconds."
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
```

⚠️ **Note**: the auto-clear launches a NEW coroutine per COMPLETED task per emission. If the queue state emits rapidly (e.g. during a download's progress ticks), the same COMPLETED task could trigger multiple `delay(10_000)` coroutines. The `removeFromQueue` is idempotent (it's a no-op if the task is already gone), so this is wasteful but not buggy.

### User-action surface (lines 71-81)

```kotlin
fun pause(taskId: Long) = viewModelScope.launch { manager.pauseDownload(taskId) }
fun resume(taskId: Long) = viewModelScope.launch { manager.resumeDownload(taskId) }
fun cancel(taskId: Long) = viewModelScope.launch { manager.cancelDownload(taskId) }
fun retry(taskId: Long) = viewModelScope.launch { manager.retryDownload(taskId) }

fun deleteEpisode(taskId: Long) = viewModelScope.launch { manager.deleteDownload(taskId) }

fun deleteAnime(contentId: String) = viewModelScope.launch {
    manager.deleteAnimeDownloads(contentId)
}

fun setDownloadFolder(treeUriString: String) {
    try { manager.setDownloadFolder(treeUriString) }
    catch (e: Exception) { _state.value = _state.value.copy() }
}
```

### `groupByAnime` (lines 96-104) — the completed-downloads grouping

```kotlin
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
```

## A.6 `DownloadedAnimeCard` (component file) — dead code, do NOT replicate

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/components/DownloadedAnimeCard.kt` (182 lines)

This file exists in `components/` but is **NOT used by `DownloadedFilesScreen`** (which defines its own in-file private `DownloadedAnimeCard`). Per `00-overview.md` §6 / `08-downloads-page-ui.md` §14: "Don't replicate the dead code (`QueueRow`, `DownloadsEmptyState` component) — the in-screen private composables supersede them."

Differences from the in-file version:
- Uses `surfaceVariant.copy(alpha = 0.4f)` (vs in-file's `0.3f`).
- `expanded` defaults to `false` (vs in-file's `true`).
- Renders an `ExpandMore`/`ExpandLess` icon (vs in-file's always-`ChevronRight`).
- Uses `AnimatedVisibility(visible = expanded)` (vs in-file's plain `if (expanded)`).
- Padding `horizontal = 16.dp, vertical = 4.dp` (vs in-file's `horizontal = 6.dp`).
- Renders "N episodes downloaded" subtitle (vs in-file's "N episode(s)").

**For the new project**: use the in-file version (A.3), not this component file.

## A.7 `QueueRow` (component file) — dead code, do NOT replicate

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/components/QueueRow.kt` (243 lines)

Same situation as A.6: exists in `components/` but is **NOT used by `DownloadsScreen`** (which defines its own in-file `EpisodeRow` inside `AnimeSectionCard`). The `QueueRow` design — one card per task with cover thumbnail + title + EP number + status label + linear progress + inline action buttons — was the OLD design before the anime-section-grouping redesign. The current `DownloadsScreen` groups multiple episodes under one anime card instead.

**For the new project**: do NOT replicate `QueueRow`. Use the in-file `EpisodeRow` (A.2).

## A.8 `DownloadsEmptyState` (component file) — dead code, do NOT replicate

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/components/DownloadsEmptyState.kt` (96 lines)

Same situation: the component file has a TWO-variant empty state (folder-needed vs no-downloads), but `DownloadsScreen` uses its own in-file `DownloadsEmptyStateContent()` (which only handles the no-downloads case — it does NOT show a folder-setup prompt; it just says "Download episodes from the anime detail page").

**For the new project**: the new project SHOULD use a two-variant empty state (the component file's design is actually better — it handles the SAF-folder-not-picked case). Replicate the `DownloadsEmptyState.kt` design, NOT the in-file `DownloadsEmptyStateContent()`.

The component's design (lines 39-94):
- A `Column(fillMaxWidth, padding vertical = 80.dp, CenterHorizontally, CenterVertically)`:
  - `Icons.Outlined.Download` (56dp tall, `tint = onSurfaceVariant`).
  - 16dp spacer.
  - Title: `"Choose a download folder"` (if `needsFolder`) or `"No downloads yet"` — 16sp ExtraBold, Center-aligned.
  - 8dp spacer.
  - Subtitle: `"Pick a folder to store downloaded episodes. You can change this later in settings."` (if `needsFolder`) or `"Tap the download button on an episode to save it for offline viewing."` — 13sp Normal, onSurfaceVariant, Center-aligned, padding horizontal = 40.dp.
  - 16dp spacer (only if `needsFolder`).
  - "Select folder" button (only if `needsFolder`): `Surface(color = primaryContainer, shape = RoundedCornerShape(50), padding horizontal = 40.dp, clickable(onPickFolder))` with `Text("Select folder", RobotoFamily, 13sp, ExtraBold, onPrimaryContainer, padding horizontal = 20.dp, vertical = 10.dp)`.

## A.9 Per-episode download control on the details page

This is the download button that appears on each episode row in the anime-details screen. Three files:

### A.9.1 `EpisodeDownloadState.kt` (45 lines)

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt`

```kotlin
sealed interface EpisodeDownloadState {
    data object NotDownloaded : EpisodeDownloadState
    data object Resolving     : EpisodeDownloadState   // immediate spinner — 1-3s resolve phase
    data object Queued        : EpisodeDownloadState
    data class  Downloading(val progress: Int) : EpisodeDownloadState
    data object Paused        : EpisodeDownloadState
    data class  Error(val message: String?)    : EpisodeDownloadState
    data object Downloaded    : EpisodeDownloadState
}
```

The state is **defined in `:feature:anime-details`** (NOT `:core:download`) — the feature module stays decoupled from the download engine. The host (`AppController` in `:app`) collects `DownloadManager.episodeDownloadStates: Flow<Map<String, DownloadTask>>` and maps each `DownloadTask` to this sealed type, then passes a lookup lambda into `EpisodesSection`.

The mapping (from `AppController` line 230 + EpisodeDownloadControl KDoc):
- `QUEUED` → `Resolving` if the task ID isn't yet in `downloadTasksFlow` (still in the resolve phase between tap and enqueue) OR `Queued` once the task is in the flow.
- `DOWNLOADING` → `Downloading(progress)`
- `PAUSED` → `Paused`
- `ERROR` → `Error(errorMessage)`
- `COMPLETED` → `Downloaded`
- (no task) → `NotDownloaded`

### A.9.2 `EpisodeDownloadControl.kt` (176 lines) — the state-driven control

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt`

A `Row(verticalAlignment = CenterVertically)` that branches on `state`:

| State | Visuals | Actions |
|---|---|---|
| `NotDownloaded` | 36dp `IconButton` with 20dp `Icons.Filled.Download` (tint = `primary`) | `onDownload` |
| `Resolving` | 18dp `CircularProgressIndicator` (strokeWidth 2dp, `primary`) + 4dp spacer + `CancelButton` | `onCancel` |
| `Queued` | 18dp `CircularProgressIndicator` (strokeWidth 2dp, `onSurfaceVariant`) + 4dp spacer + `CancelButton` | `onCancel` |
| `Downloading(progress)` | If `progress > 0`: `LinearProgressIndicator(progress = { (progress/100f).coerceIn(0f,1f) }, color = primary, trackColor = surface, modifier = size(width=40.dp, height=4.dp).clip(RoundedCornerShape(2.dp)))`. Else: indeterminate `LinearProgressIndicator` (same size + colours). 6dp spacer + `CancelButton`. | `onCancel` |
| `Paused` | 36dp `IconButton` with 20dp `Icons.Filled.PlayArrow` (tint = `primary`) + `CancelButton` | `onResume`, `onCancel` |
| `Error(message)` | 36dp `IconButton` with 20dp `Icons.Filled.Refresh` (tint = `error`) + `CancelButton` | `onRetry`, `onCancel` |
| `Downloaded` | 20dp `Icons.Filled.CheckCircle` (tint = `primary`, NON-interactive) + 2dp spacer + 36dp `IconButton` with 18dp `Icons.Filled.Delete` (tint = `onSurfaceVariant`) | `onDelete` |

`CancelButton` is a private composable: 32dp `IconButton` with 16dp `Icons.Filled.Close` (tint = `onSurfaceVariant`).

```kotlin
@Composable
private fun CancelButton(onCancel: () -> Unit) {
    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Cancel download",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
```

⚠️ The KDoc says "Uses `AnimatedContent` for smooth state transitions" but the actual implementation does NOT use `AnimatedContent` (no import for it). This is a known doc-vs-code mismatch (`00-overview.md` §6 flags it). The new project may add `AnimatedContent` for polish, but the old code does not have it.

### A.9.3 `EpisodesSection.kt` — where the control appears (1021 lines)

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodesSection.kt`

The `EpisodesSection` composable (lines 79-115) accepts download-related params:

```kotlin
@Composable
fun EpisodesSection(
    episodeState: EpisodeState,
    currentMatch: SourceMatcher.SourceMatch?,
    allMatches: List<SourceMatcher.SourceMatch>,
    watchedEpisodes: Set<String>,
    episodeMetadata: Map<Int, EpisodeMetadata>,
    isSearching: Boolean,
    manualSearchResults: List<SourceMatcher.ManualSearchResult>,
    manualSearchErrors: List<Pair<String, String>>,
    autoMatchErrors: List<Pair<String, String>>?,
    hasSearched: Boolean,
    availableSources: List<SourceMatcher.SourceInfo>,
    initialSearchQuery: String,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSwitchSource: (SourceMatcher.SourceMatch) -> Unit,
    onManualSearch: suspend (Long, String) -> Unit,
    onLinkManual: (AnimeCatalogueSource, SAnime) -> Unit,
    onClearManualSearch: () -> Unit,
    showMetadataLoading: Boolean = true,
    metadataFetchComplete: Boolean = false,
    sourceId: Long? = null,
    // ── Downloads (Agent 2) ──
    onDownloadEpisode: (SEpisode, AnimeSource) -> Unit = { _, _ -> },
    downloadStates: Map<String, EpisodeDownloadState> = emptyMap(),   // keyed by episode.url
    onDownloadCancel: (String) -> Unit = {},
    onDownloadResume: (String) -> Unit = {},
    onDownloadRetry: (String) -> Unit = {},
    onDownloadDelete: (String) -> Unit = {},
) { ... }
```

The control is rendered in `EpisodeRow` (lines 440-651), at the END of the top-Section `Row` (after the thumbnail + title/meta column), gated by:

```kotlin
if (showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded) {
    EpisodeDownloadControl(
        state = downloadState,
        onDownload = onDownload,
        onCancel = onDownloadCancel,
        onResume = onDownloadResume,
        onRetry = onDownloadRetry,
        onDelete = onDownloadDelete,
    )
}
```

Where:
- `showDownloadBtn` comes from `displayPrefs?.showDownloadButton ?: true` (a user setting — defaults to true).
- `downloadState` is looked up by `episode.url` from the `downloadStates` map.
- `onDownload` calls `onDownloadEpisode(episode, source)` (the parent of `EpisodesSection` passes this through, ultimately wired to `AppController.downloadEpisode`).

So the per-episode row's full layout is: `[thumbnail (left)] [title + meta column (middle, weight=1f)] [EpisodeDownloadControl (right)]` — the download control is always at the right edge of the row, vertically aligned to the top.

The control is rendered for ALL states EXCEPT when both (a) the user has disabled the download-button pref AND (b) the state is `NotDownloaded`. This ensures that once a download is in-flight or completed, the user can always see + manage it from the episode row, even if the pref is off.

## A.10 Design tokens to preserve verbatim

These tokens are used consistently across all the download UI files. The new project should use the SAME tokens to maintain visual continuity:

| Token | Value | Source |
|---|---|---|
| Font family | `RobotoFamily` | `core/designsystem/theme/Type.kt` |
| Primary colour | `MaterialTheme.colorScheme.primary` (the green theme colour `#B1F256` in the screenshots) | `core/designsystem/theme/Color.kt` |
| Card background | `surfaceVariant.copy(alpha = 0.3f)` (anime section) / `0.4f` (episode row inside section, "More" screen style) / `0.5f` (action bar chips, 3-dot menu button) | direct |
| Episode row inside section | `surfaceVariant.copy(alpha = 0.2f)` | direct |
| Card shape | `RoundedCornerShape(12.dp)` | direct |
| Pill shape | `RoundedCornerShape(6.dp)` (info/size/percentage/error pills) | direct |
| Pill font | 10sp (count + label), SemiBold for info, Bold for percentage/error | direct |
| Section header shape | `RoundedCornerShape(2.dp)` for the 3dp accent bar | direct |
| Bottom-sheet shape | `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)` | direct |
| Bottom-sheet drag handle | `null` (design principle #2 — no drag handle) | direct |
| Empty-state icon surface | `secondaryContainer.copy(alpha = 0.5f)`, `RoundedCornerShape(24.dp)`, size 96dp, with 48dp `Icons.Filled.Download` tinted `onSecondaryContainer.copy(alpha = 0.6f)` | direct |
| Empty-state title | 16sp ExtraBold, `onSurface` | direct |
| Empty-state subtitle | 12sp Normal, `onSurfaceVariant` | direct |
| Episode-row download control | Compact (36dp touch targets, 16-20dp icons, 40dp × 4dp progress bar) | direct |
| Cancel button | 32dp IconButton, 16dp Close icon, `onSurfaceVariant` tint | direct |
| Cover thumbnail (DownloadedAnimeCard header) | 44dp × 62dp, `RoundedCornerShape(6.dp)`, `ContentScale.Crop` | direct |

## A.11 "Replicate exactly" checklist

For the implementation team — replicate these EXACTLY (with the noted exceptions):

1. ✅ `DownloadsScreen.kt` — full layout: CollapsingHeader + permission LaunchedEffect + DownloadActionBar + StatChip row + AnimeSectionCard LazyColumn + EpisodeMenuSheet.
2. ✅ `DownloadedFilesScreen.kt` — full layout + in-file `DownloadedAnimeCard`.
3. ✅ `DownloadUiState.kt` — the data class + `DownloadedAnimeKey` + `isInQueueSection` extension.
4. ✅ `DownloadViewModel.kt` — combine + 10s auto-clear + the 7 user-action methods + `groupByAnime` (sorted alphabetically by lowercase title).
5. ✅ `EpisodeDownloadControl.kt` — the 7-state Row + `CancelButton` private composable.
6. ✅ `EpisodeDownloadState.kt` — the sealed interface in `:feature:anime-details`.
7. ✅ The `EpisodesSection` integration: `downloadStates` map keyed by `episode.url`, `showDownloadBtn` gating, control at the right edge of the episode row.
8. ⚠️ `DownloadsEmptyState.kt` (the component file, two-variant) — USE THIS, not the in-file single-variant `DownloadsEmptyStateContent`. The component's folder-setup prompt is better UX.
9. ❌ `QueueRow.kt` (component file) — DEAD CODE, do NOT replicate. Use the in-file `EpisodeRow`.
10. ❌ `DownloadedAnimeCard.kt` (component file) — DEAD CODE, do NOT replicate. Use the in-file `DownloadedAnimeCard`.
11. 🔧 **Fix while replicating**: group the live queue by `contentId` (not `anime.title`) to avoid same-title conflation.
12. 🔧 **Fix while replicating**: the auto-clear `launch { delay(10_000); removeFromQueue(task.id) }` launches a new coroutine per emission — guard with a `Set<Long>` of already-scheduled task IDs to avoid the leak.
13. 🔧 **Optional polish while replicating**: actually use `AnimatedContent` in `EpisodeDownloadControl` (the KDoc promises it; the code doesn't deliver).

---

# Part B — The "download fails when playing another episode" bug

## B.1 User report

> "In the old download system there is an issue. If that download is happening and if I go on and try to play another episode from another content or anything inside the app, then the old download, which was happening, apparently failed."

In short: a download is in progress (DOWNLOADING). The user opens another anime/episode and presses play (or does "anything inside the app"). The previously-running download transitions to ERROR — i.e. it "fails".

## B.2 What is NOT the cause (ruled out)

Before identifying the root cause, I ruled out the obvious candidates by reading the code. None of these is the cause:

1. **Shared coroutine scope with the player**. The download runs on `DefaultDownloadManager`'s PRIVATE scope:

   `core/download/.../DefaultDownloadManager.kt:46-58`:
   ```kotlin
   class DefaultDownloadManager(
       context: Context,
       private val okHttp: OkHttpClient,
       private val preferences: DownloadPreferences,
       private val store: DownloadStore,
       private val tempCache: TempDownloadCache,
       private val advancedDownloader: AdvancedHttpDownloader,
       private val resumeManager: DownloadResumeManager,
       scope: CoroutineScope = CoroutineScope(
           SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
               DownloadLogger.e("Uncaught coroutine exception in download scope (suppressed)", e)
           }
       ),
   ) : DownloadManager {
   ```

   The DI module (`core/download/.../di/DownloadModule.kt:60-68`) does NOT pass a scope, so the default param is used — a fresh, private, app-lifetime scope. `AppController`'s scope (`CoroutineScope(SupervisorJob() + Dispatchers.Main)`) is separate. The player (`WatchScreen` MPV) uses no Kotlin coroutine scope for its streaming — MPV does its own native HTTP. **Nothing the user does can cancel this scope.**

2. **Shared `OkHttpClient` with the player or extension**. The download uses a dedicated client qualified `named("download")`:

   `core/download/.../di/DownloadModule.kt:48-55`:
   ```kotlin
   single(named("download")) {
       OkHttpClient.Builder()
           .connectTimeout(30, TimeUnit.SECONDS)
           .readTimeout(60, TimeUnit.SECONDS)
           .writeTimeout(60, TimeUnit.SECONDS)
           .retryOnConnectionFailure(true)
           .build()
   }
   ```

   This client has its OWN connection pool + dispatcher (a fresh `OkHttpClient.Builder()` with no `connectionPool(...)` or `dispatcher(...)` override still creates a new pool/dispatcher per `build()`). The extension's `NetworkHelper.client` is a different builder. MPV uses native HTTP. **No OkHttp resource is shared between the download and the player.**

3. **Shared temp cache that gets cleared**. `TempDownloadCache` (`core/download/.../TempDownloadCache.kt`) is per-task — each task has its own dir under `<cacheDir>/anikuta_downloads/<taskId>/`. `cleanupStale()` (which deletes ALL temp dirs) is called only ONCE, in the DI module's `single { TempDownloadCache(get()).also { it.cleanupStale() } }` — at app startup. `cleanupTask(taskId)` is called only from `HttpDownloader.download()`'s `finally` block (per-task, after that task finishes). **Nothing the user does mid-download clears the temp cache of the running download.**

4. **The `connectivityCheck` / Wi-Fi-only toggle**. `DefaultDownloadManager.isNetworkAllowed()` is called ONLY from `DownloadQueue.tryStartNext()` (i.e. when STARTING a new task), not during a running download. Even if Wi-Fi drops mid-download, the running download's coroutine is not cancelled by this check.

5. **`AppController.cancelDownload` / `DownloadViewModel.cancel` being fired automatically**. Both code paths require an explicit user tap (`onDownloadCancel` lambda on the episode row, or the 3-dot menu's "Cancel"). Nothing in the play-episode flow calls these.

6. **The auto-clear completed-entries-after-10s logic**. That logic only fires for `COMPLETED` tasks (`manager.activeDownloads.collect { active -> active.filter { it.status == DownloadStatus.COMPLETED }... }`). It does not touch DOWNLOADING tasks. (`feature/download/.../DownloadViewModel.kt:53-65`.)

7. **The `DownloadQueue.observeJob` (in `DefaultDownloadManager`)**. That collector just updates notifications — it doesn't mutate task state. (`core/download/.../DefaultDownloadManager.kt:84-99`.)

8. **`ResolverService` having shared state**. `ResolverService` is stateless — each `resolve()` call uses `withContext(Dispatchers.IO)` + `withTimeoutOrNull(30_000L)`. No fields, no caches. (`core/video-resolver/.../ResolverService.kt`.)

## B.3 The actual root cause: extension local-proxy-server churn

**The smoking gun** is in the project's own `lessons-learned.md` (line 89):

> "DOUBLE-RESOLVE BUG: Never call getHosterList (or any extension method that creates local proxy servers) TWICE for the same episode. Extensions like AniKotoS create a new local HTTP proxy on each getHosterList call — the second call kills the first call's proxy. User picks a video from the first resolve (dead proxy) → 'loading failed'. The old project's ResolverService.kt resolves EXACTLY ONCE and derives both flat + structured results from the same video list. Always use a single resolve() call and pass the raw Videos list to buildServers() for structured derivation. (source: CI debugging 2026-08-04, root cause of 'loading failed' — log showed two getHosterList calls with different proxy ports 39369→39073)"

That entry describes the **player-side** manifestation of the bug (calling `getHosterList` twice kills the first proxy → MPV can't load). The **download-side** manifestation is the same root cause, just with a different victim: the **download** is the one holding the dead proxy URL.

### The local proxy server

**File**: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/model/HttpServer.kt` (33 lines)

```kotlin
open class HttpServer : NanoHTTPD(0) {        // 0 = bind to a RANDOM free port
    val url: String
        get() = "http://localhost:$listeningPort"

    @Volatile private var isRunning = false

    override fun start() {
        try { super.start(); isRunning = true }
        catch (e: Exception) { Log.d("HttpServer", "Failed to start http server", e) }
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }
}
```

**File**: `core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt:87-95`

```kotlin
/**
 * Enable the use of a local http server.
 *
 * Extensions are responsible for starting the server, but the app
 * will handle closing.
 *
 * @since extensions-lib 17
 */
open val server: HttpServer? = null
```

So: each `AnimeHttpSource` (i.e. each extension) MAY override `server` with its own `HttpServer` subclass. Some extensions (notably AniKotoS and similar "structured" extensions that proxy/rewrite video URLs to strip anti-scraping wrappers like PNG-header prepending) spin up the server **inside `getHosterList()`** or **inside `hosterListParse()`**.

The video URLs these extensions return are then `http://localhost:$listeningPort/...` — pointing at the local proxy. The proxy translates each request into the real CDN URL, fetches it, strips the anti-scraping wrapper, and serves the cleaned bytes to whoever is consuming the URL (MPV for streaming, `HttpDownloader` for downloading).

### Why each `getHosterList` call kills the previous proxy

Two common extension patterns cause this:

1. **Pattern A — fresh server per call**: the extension instantiates a NEW `HttpServer` subclass inside `getHosterList()`. The new server binds to a new random port. The old server is NOT explicitly stopped — but the extension typically holds only ONE reference to "the current server", so the old reference is dropped. Depending on GC timing and whether the old server's `NanoHTTPD` thread is daemonised, the old port may stay alive briefly or die immediately. Either way, the extension's video URLs going forward point at the NEW port.

2. **Pattern B — restart-on-call**: the extension holds a single `HttpServer` field, but calls `server.stop()` + `server = MyServer(); server.start()` at the top of `getHosterList()`. This deterministically kills the old port.

In BOTH patterns, after a new `getHosterList` call, the OLD port (the one whose URL is captured in the in-flight download's `DownloadRequest.videoUrl`) is dead.

The `lessons-learned.md` entry shows concrete evidence: "log showed two getHosterList calls with different proxy ports 39369→39073" — i.e. the port changed between calls.

### Why playing "another episode from another content" triggers it

Even if the user plays an episode from a **different anime**, the SAME extension source may be involved:

- If both anime are matched to the same extension (e.g. both are tracked via AniKotoS), `AppController.resolveEpisode` calls `resolverService.resolve(source, episode)` → `source.getHosterList(newEpisode)`. This is the SAME `AnimeSource` instance (Koin-singleton `AnimeExtensionManager`), so its `server` field is the same. The new call kills the old proxy.
- If the user plays an episode from an anime matched to a DIFFERENT extension, no interference (different `server` field).
- If the user does "anything inside the app" that triggers `getHosterList` — e.g. opening the resolver sheet for a third anime, or pulling-to-refresh the episodes list — same effect.

The user's "or anything inside the app" phrasing matches this — ANY action that calls `getHosterList` on the SAME source kills the in-flight download's proxy.

## B.4 End-to-end trace of the bug

Concrete trace — anime A EP1 is downloading, user opens anime B (same extension source) and taps play on EP1:

| Step | Component | Action | Effect |
|---|---|---|---|
| 1 | `EpisodeDownloadControl` (anime A EP1 row) | user taps download | `onDownload` → `AppController.downloadEpisode(episode, source, watchCtx, contentId)` |
| 2 | `AppController.downloadEpisode` (`AppController.kt:1046-1087`) | `scope.launch { downloadOrchestrator.enqueueDownload(anime, episode, source) }` | Resolving flag set on episode row |
| 3 | `DownloadOrchestrator.enqueueDownload` (`DownloadOrchestrator.kt:81-148`) | `resolver.resolve(source, episode)` → `selectBestVideo(source.id, servers)` → `manager.enqueueDownload(request)` | Returns `EnqueueResult.Success(taskId)` |
| 4 | `ResolverService.resolve` (`ResolverService.kt:51-105`) | `source.getHosterList(episode)` → extension creates `MyServer()`, `server.start()` on port **39369**, returns `Video` list with `videoUrl = "http://localhost:39369/..."` | Proxy A is alive on port 39369 |
| 5 | `DownloadQueue.enqueue` (`DownloadQueue.kt:99-117`) | task created with `request.videoUrl = "http://localhost:39369/..."`, status = QUEUED, `tryStartNext()` called | |
| 6 | `DownloadQueue.launchDownload` (`DownloadQueue.kt:177-244`) | `scope.launch { permits.withPermit { downloader.download(task) { ... } } }` | Job stored in `jobs[task.id]` |
| 7 | `HttpDownloader.download` (`HttpDownloader.kt:79-167`) | `downloadVideoToCache(url = "http://localhost:39369/...", ...)` → `client.newCall(request).execute()` — the OkHttp call hits the LOCAL proxy on port 39369. Bytes flow. | Download in progress, proxy A is being read |
| 8 | **User navigates away, opens anime B (same extension), taps play on EP1** | `AppController.resolveEpisode(episodeB, sourceB, ...)` where `sourceB === source` (same AnimeSource instance) | |
| 9 | `AppController.resolveEpisode` (`AppController.kt:940-946`) | `resolverService.resolve(source, episodeB)` | |
| 10 | `ResolverService.resolve` | `source.getHosterList(episodeB)` → extension's `getHosterList` runs again → creates a NEW `MyServer()`, `server.start()` on port **39073**, returns `Video` list with `videoUrl = "http://localhost:39073/..."`. The OLD server on port 39369 is either stopped explicitly or GC'd. | **Proxy A (port 39369) is dead.** Proxy B (port 39073) is alive. |
| 11 | Back in `HttpDownloader.downloadNormal` (still in step 7's `while (true) { input.read(buffer) }` loop) | The next `input.read(buffer)` on the OkHttp `ResponseBody.byteStream()` throws `java.io.IOException: Connection refused` (or `SocketException: Socket closed`) because the underlying TCP connection to port 39369 was reset. | The exception bubbles up |
| 12 | `HttpDownloader.downloadNormal` catch block (`HttpDownloader.kt:317-322`) | `catch (e: Exception) { throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e) }` | Wrapped in `DownloadException` |
| 13 | `DownloadQueue.launchDownload` catch block (`DownloadQueue.kt:209-217`) | `catch (e: DownloadException) { val errorTask = ... copy(status = ERROR, errorMessage = e.message, ...); mutateTask(task.id) { errorTask }; persistNow(); onTaskError?.invoke(errorTask) }` | **Task status flips to ERROR. UI shows "Failed".** |
| 14 | `EpisodeDownloadControl` (anime A EP1 row) | re-composes with `EpisodeDownloadState.Error("Video download failed: Connection refused")` | Shows retry icon + cancel button |

The download "apparently failed" — exactly as the user described.

## B.5 Why the OLD architecture cannot fix this

The OLD download system has several structural properties that make this bug essentially unavoidable for proxy-URL sources:

1. **The download captures the `videoUrl` at enqueue time and never re-resolves.** `DownloadRequest.videoUrl` is a `String` field set once by `DownloadOrchestrator.buildRequest` (`DownloadOrchestrator.kt:325-345`). `HttpDownloader` uses it verbatim. If the URL is a localhost proxy URL, the download's lifetime is bounded by the proxy's lifetime — and the proxy's lifetime is controlled by the extension's `getHosterList` calls, NOT by the download engine.

2. **The download engine has no awareness that some URLs are localhost-proxy URLs.** `HttpDownloader.downloadVideoToCache` (`HttpDownloader.kt:131-180`) treats every URL the same — `client.newCall(Request.Builder().url(url).build())`. There's no "is this a proxy URL? if so, lease/keep-alive the proxy" branch. The engine can't even tell the difference between `https://cdn.example.com/video.mp4` and `http://localhost:39369/proxy?url=...`.

3. **The resolver and the download engine share no coordination.** `ResolverService.resolve()` and `DownloadQueue.enqueue()` are completely decoupled. Each call to `resolve()` may kill proxies that prior `enqueue()` calls depend on. There's no "this proxy is in use by task N — don't kill it" refcount.

4. **The player's `resolveEpisode` and the downloader's `enqueueDownload` share the SAME `ResolverService` and the SAME `AnimeSource` instances.** (`AppController.kt:100` — `resolverService: ResolverService` and `downloadOrchestrator: DownloadOrchestrator` both Koin-singletons.) So any user action that triggers a resolve — play, switch source, switch episode in the player, refresh episodes list — can kill a download's proxy.

5. **There is no retry-on-proxy-death path.** When the IOException fires, the task goes to ERROR. The user must manually tap "Retry". Retry calls `queue.retry(taskId)` (`DownloadQueue.kt:148-155`) which sets status back to QUEUED + progress = 0 + `tryStartNext()` — but the retry RE-USES the SAME `request.videoUrl` (the now-dead proxy URL). So retry also fails, unless the user happens to trigger a new `getHosterList` for the same source first (which would re-create a proxy, but on a different port — still a dead URL).

6. **There is no foreground service** (`06-notifications-foreground-service.md` flags this gap). Even if the bug weren't about proxy churn, Android 14+ may kill background downloads when the user navigates away. But this is a SEPARATE issue — the user's bug happens while the user is still IN the app.

## B.6 The fix the NEW project should implement

The new project must break the dependency between the download's lifetime and the extension's proxy server's lifetime. Three layers of defense, in order of robustness:

### Fix 1 (PRIMARY): Download via the underlying CDN URL, not the proxy URL

When the resolver returns a `Video`, it should carry BOTH:
- `videoUrl` — the proxy URL (for streaming via MPV — fast, supports anti-scraping stripping).
- `directUrl: String?` — the underlying CDN URL (for downloading — slower, no anti-scraping stripping, but stable).

The download orchestrator should prefer `directUrl` for downloads. If `directUrl` is null (the extension truly only exposes the proxy), fall through to Fix 2.

Concretely:
- `core/video-resolver/.../ResolverVideo.kt` — add `directUrl: String?` field.
- The resolver strategy extracts the direct URL by calling a new `Video.directVideoUrl` extension hook (similar to how the existing `videoUrl` is exposed). Extensions that proxy can override this to return the underlying CDN URL.
- `DownloadOrchestrator.buildRequest` uses `selection.video.directUrl ?: selection.video.url` for `DownloadRequest.videoUrl`.
- The download engine then makes a direct HTTP call to the CDN — no proxy dependency, no churn.

### Fix 2 (SECONDARY): Re-resolve + restart-on-error for proxy-URL downloads

If `directUrl` is null (the extension only exposes a proxy URL), the download engine must treat the proxy URL as **ephemeral** and re-resolve on failure:

- Add a `DownloadRequest.resolveContext: ResolveContext?` field capturing `(sourceId, episodeUrl, serverName, audioLabel, quality)` — enough to re-resolve.
- In `HttpDownloader.downloadNormal`, catch `IOException` specifically. If the request URL is a `localhost` URL AND the task has a `resolveContext`, BEFORE throwing `DownloadException`, attempt ONE re-resolve:
  ```kotlin
  catch (e: IOException) {
      if (task.request.videoUrl.startsWith("http://localhost") && task.request.resolveContext != null) {
          val fresh = reResolver.reResolve(task.request.resolveContext)
          if (fresh != null) {
              // retry with the fresh URL — update the task's request + resume from current bytes
              return downloadVideoToCache(fresh.url, fresh.headers, tempFile, taskId, onProgress)
          }
      }
      throw DownloadException("Video download failed: ${e.message}", e)
  }
  ```
- The re-resolve uses the SAME `ResolverService` + `selectBestVideo` logic, but picks the SAME (server, audio, quality) combination. If the extension's proxy is alive at that moment, the new URL works.
- Cap re-resolve attempts at 2 (one initial + one re-resolve) to avoid infinite loops.

### Fix 3 (TERTIARY): Coordinator that prevents proxy churn

The strongest fix is to add a `ProxyLeaseCoordinator` (in `:core:video-resolver` or `:app`) that:

- Tracks active leases: `Map<ProxyKey, LeaseRefcount>` where `ProxyKey = (sourceId, serverName)` and `LeaseRefcount` counts how many consumers (MPV + each download task) are currently using the proxy.
- Exposes `acquireLease(source, serverName): Lease` and `releaseLease(lease)`.
- Wraps `ResolverService.resolve` so that BEFORE calling `source.getHosterList`, it checks if a lease for `(source.id, ...)` already exists. If yes, reuses the existing resolved videos (whose proxy URLs are still alive). If no, calls `getHosterList` and creates a new lease.
- The download engine calls `acquireLease` before starting the download and `releaseLease` in its `finally` block. The player does the same.
- Result: a second `getHosterList` for the same source is SUPPRESSED while a download is using the proxy. Only when the lease count drops to zero (download finished + player stopped) is the proxy allowed to be re-created.

This is the heaviest fix but the most correct. It eliminates the bug class entirely — no proxy churn means no download failures from proxy death.

### Fix 4 (QUATERNARY): Foreground service for download durability

Independent of the proxy-churn bug, the new project MUST add a foreground service for downloads (per `06-notifications-foreground-service.md`). This prevents Android from killing the download when the app goes to background, which is a SEPARATE failure mode from the proxy-churn one but worth fixing in the same pass.

## B.7 Architectural rules to prevent the bug class

Generalising the fix, the new project should adopt these rules:

1. **The download engine must NEVER depend on the lifetime of a side-effect created by the resolver.** If the resolver creates a resource (proxy server, file descriptor, session token) whose lifetime is shorter than the download's, the download engine must either (a) not use that resource, or (b) hold an explicit lease that prevents the resource from being killed.

2. **URLs captured at enqueue time are NOT durable.** The download engine must treat `videoUrl` as potentially ephemeral. Either:
   - Capture a `directUrl` (no proxy) and use that, OR
   - Capture a `resolveContext` (enough info to re-resolve) and use the proxy URL with re-resolve-on-failure.

3. **`ResolverService.resolve` is NOT idempotent with respect to side-effects.** Calling it twice for the same `(source, episode)` may kill the proxy from the first call. The new project must EITHER make `resolve` idempotent (cache the result + reuse it for the same `(source, episode)` while a lease is held) OR coordinate via a lease coordinator so that the second call doesn't kill the first.

4. **The download scope must be architecturally separate from the playback scope.** The OLD project actually gets this right (`DefaultDownloadManager`'s private scope vs `AppController`'s scope vs MPV's no-scope). The new project should preserve this separation — but ALSO ensure the download doesn't depend on resources held by the playback scope (which the OLD project fails to do, hence this bug).

5. **The download engine must log the URL it's fetching from** (the OLD project does this in `HttpDownloader.download`'s `DownloadLogger.i("  URL: $videoUrl")`). When debugging this bug, that log line is the smoking gun — if the URL is `http://localhost:PORT/...`, the bug is proxy churn; if it's `https://cdn.example.com/...`, the bug is something else (network/server-side). The new project should preserve this logging and ADD a one-time warning when the URL is detected as a localhost URL: "Download depends on extension proxy server — may fail if the proxy is killed by another resolve call."

6. **Add an integration test that exercises the bug scenario.** The test: enqueue a download from a mock source that returns a localhost-proxy URL, then call `resolve` on the same source for a different episode (which kills the proxy), then assert that the download EITHER (a) completes via `directUrl`, OR (b) re-resolves and completes, OR (c) fails gracefully with a clear error message ("proxy server killed by another resolve call — retry will re-resolve"). The test must FAIL if the download just goes to ERROR with a generic "Connection refused" message.

---

## Cross-references to other docs

- `00-overview.md` §6 — the existing "honest notes / bugs / TODOs" list. Add this bug as a new entry (it's not currently listed).
- `02-queue-management.md` — `DownloadQueue` internals; the `launchDownload` catch-block path is where the bug manifests as ERROR status.
- `05-downloaders.md` — `HttpDownloader` pipeline; the `client.newCall(request).execute()` line is where the IOException fires.
- `06-notifications-foreground-service.md` — the foreground-service gap (Fix 4 above).
- `10-player-integration.md` — the player-side manifestation of the same root cause (already known as the "DOUBLE-RESOLVE BUG" in `lessons-learned.md:89`).
- `13-implementation-plan.md` — the phased plan; the proxy-churn fix should be added as a new task in the phase that introduces the download engine.
- `14-auto-download-engine.md` — the new 3-dimensional priority engine; the `directUrl` field addition is a backward-compatible change to `ResolverVideo` that doesn't affect the priority algorithm.
