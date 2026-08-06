# 09 — Per-Episode Download Control on the Details Page

> All line references: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt` (177 lines) + `EpisodeDownloadState.kt` (45 lines) + `EpisodesSection.kt` + `AppController.kt`.

## 1. The state model

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt` (45 lines)

```kotlin
sealed interface EpisodeDownloadState {
    /** No download exists for this episode. Shows the download button. */
    data object NotDownloaded : EpisodeDownloadState

    /**
     * Resolving video sources (the phase between tapping download + the task
     * being enqueued). Shows an immediate spinner so the user knows the tap
     * registered — the resolve takes 1-3s.
     */
    data object Resolving : EpisodeDownloadState

    /** In the queue, waiting for a download slot. Shows a spinner + cancel. */
    data object Queued : EpisodeDownloadState

    /** Actively downloading. Shows a progress bar + pause/cancel. */
    data class Downloading(val progress: Int) : EpisodeDownloadState

    /** User-paused. Shows a resume + cancel. */
    data object Paused : EpisodeDownloadState

    /** Failed. Shows an error icon + retry + cancel. */
    data class Error(val message: String?) : EpisodeDownloadState

    /** Completed — on disk, ready for offline playback. Shows a checkmark + delete. */
    data object Downloaded : EpisodeDownloadState
}
```

**Critical design choice**: this type lives in `:feature:anime-details` (NOT `:core:download`). The feature module is decoupled from the download engine — the host (AppController) maps `DownloadTask` → `EpisodeDownloadState`. This means `:feature:anime-details` has NO dependency on `:core:download`.

## 2. State mapping: `DownloadTask` → `EpisodeDownloadState`

The host (`AppController`) maintains a `downloadTasksFlow: StateFlow<Map<String, DownloadTask>>` keyed by `"$contentId|$episodeNumber"` (collected from `manager.episodeDownloadStates`, see `03-state-machine.md` §5).

The mapping to `EpisodeDownloadState` happens in the host (looking at the `AppController.downloadTasksFlow` collection in `AnikutaRoot.kt` etc.). The actual mapping function isn't a single named function — it's done inline where `EpisodeRow` is composed. Looking at the chain:

- `AnimeDetailScreen` receives `downloadStates: Map<String, EpisodeDownloadState>` keyed by **episode URL** (`SEpisode.url`).
- `DetailContent` passes through, calling `downloadStates[episode.url] ?: EpisodeDownloadState.NotDownloaded` for each row (line 255).
- `AppController` is responsible for building that map (mapping `downloadTasksFlow` + `resolvingEpisodes` → `EpisodeDownloadState` per episode URL).

The mapping logic (reconstructed from `AppController`):
```kotlin
fun episodeState(episode: SEpisode, contentId: String): EpisodeDownloadState {
    // 1. Resolving takes precedence (instant feedback before enqueue)
    if (resolvingEpisodes[episode.url] == true) return EpisodeDownloadState.Resolving

    // 2. Look up the task by composite key
    val key = "$contentId|${"%.3f".format(episode.episode_number)}"
    val task = downloadTasksFlow.value[key] ?: return EpisodeDownloadState.NotDownloaded

    // 3. Map task status → EpisodeDownloadState
    return when (task.status) {
        DownloadStatus.QUEUED -> EpisodeDownloadState.Queued
        DownloadStatus.DOWNLOADING -> EpisodeDownloadState.Downloading(task.progress)
        DownloadStatus.PAUSED -> EpisodeDownloadState.Paused
        DownloadStatus.ERROR -> EpisodeDownloadState.Error(task.errorMessage)
        DownloadStatus.COMPLETED -> EpisodeDownloadState.Downloaded
        DownloadStatus.CANCELLED -> EpisodeDownloadState.NotDownloaded  // (effectively never happens)
    }
}
```

(`AppController` doesn't expose this exact function — it builds the `downloadStates: Map<String, EpisodeDownloadState>` map at the call site where `AnimeDetailScreen` is composed. The exact code path is in `AnikutaRoot.kt`'s details-screen rendering. The above is the inferred logic from the data flow.)

## 3. The `EpisodeDownloadControl` composable

**File**: `EpisodeDownloadControl.kt:49-164`

```kotlin
@Composable
fun EpisodeDownloadControl(
    state: EpisodeDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            EpisodeDownloadState.NotDownloaded -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = "Download episode",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            EpisodeDownloadState.Resolving -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = primary)
                Spacer(Modifier.size(4.dp))
                CancelButton(onCancel)
            }
            EpisodeDownloadState.Queued -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                CancelButton(onCancel)
            }
            is EpisodeDownloadState.Downloading -> {
                if (state.progress > 0) {
                    LinearProgressIndicator(
                        progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                        color = primary, trackColor = surface,
                        modifier = Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        color = primary, trackColor = surface,
                        modifier = Modifier.size(width = 40.dp, height = 4.dp),
                    )  // indeterminate
                }
                Spacer(Modifier.size(6.dp))
                CancelButton(onCancel)
            }
            EpisodeDownloadState.Paused -> {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.PlayArrow, "Resume download", tint = primary, modifier = Modifier.size(20.dp))
                }
                CancelButton(onCancel)
            }
            is EpisodeDownloadState.Error -> {
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, "Retry download", tint = error, modifier = Modifier.size(20.dp))
                }
                CancelButton(onCancel)
            }
            EpisodeDownloadState.Downloaded -> {
                Icon(Icons.Filled.CheckCircle, "Downloaded", tint = primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(2.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, "Delete download", tint = onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
```

### Visual reference:

| State | Visual | Action |
|---|---|---|
| `NotDownloaded` | ⬇ (Download icon, primary tint) | Tap → `onDownload` |
| `Resolving` | ⟳ spinner (18dp, primary) + ✕ | Tap ✕ → `onCancel` |
| `Queued` | ⟳ spinner (18dp, onSurfaceVariant) + ✕ | Tap ✕ → `onCancel` |
| `Downloading(progress)` | ━━━●━━ (40×4dp bar, primary) + ✕ | Tap ✕ → `onCancel` |
| `Paused` | ▶ (PlayArrow, primary) + ✕ | Tap ▶ → `onResume` |
| `Error(msg)` | ↻ (Refresh, error tint) + ✕ | Tap ↻ → `onRetry` |
| `Downloaded` | ✓ (CheckCircle, primary) + 🗑 | Tap 🗑 → `onDelete` |

The KDoc at line 38 mentions "Uses `AnimatedContent` for smooth state transitions" but there's NO `AnimatedContent` import or call in the file. **Doc-vs-code mismatch** — transitions are abrupt.

## 4. How the control is rendered inside an episode row

**File**: `EpisodesSection.kt:440-689` (`EpisodeRow` composable)

The row layout (simplified):
```
┌────────────────────────────────────────────────────────────┐
│ [Thumbnail or EP#]  Title + Date/Audio pills  [DownloadCtrl]│
│                                                            │
│ (Synopsis below, with optional background)                 │
└────────────────────────────────────────────────────────────┘
```

The download control is rendered at the END of the top Row (line 638-651):

```kotlin
// ── Download control (Agent 2 — Downloads & Offline Playback) ──
// State-driven: download button / progress+cancel / checkmark+delete.
// Shown when the pref is on OR the episode is already downloaded/
// downloading (so the user can always see/manage the state).
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

`showDownloadBtn` comes from `displayPrefs?.showDownloadButton` (default true) — the user can hide all download buttons via Settings. But if the episode is already downloading/downloaded, the control still shows so the user can manage it.

## 5. How the callbacks reach `AppController`

The wiring chain (top-down):

```
AnimeDetailScreen.kt:83-86 — receives from host:
    onDownloadEpisode: (SEpisode, AnimeSource, WatchEpisodeContext) -> Unit,
    downloadStates: Map<String, EpisodeDownloadState>,  // keyed by episode.url
    onDownloadCancel / onDownloadResume / onDownloadRetry / onDownloadDelete: (String) -> Unit,

AnimeDetailScreen.kt:276-277 — passes to DetailContent:
    onDownloadEpisode = onDownloadEpisode,
    downloadStates = downloadStates,

DetailContent.kt:94-96 — same signature, passes to EpisodesSection:
    onDownloadEpisode: (SEpisode, AnimeSource) -> Unit,
    downloadStates: Map<String, EpisodeDownloadState>,
    onDownloadCancel / Resume / Retry / Delete: (String) -> Unit,

DetailContent.kt:216-219 — wraps onDownloadEpisode with watchCtx:
    onDownloadEpisode = { episode, source ->
        onDownloadEpisode(episode, source, watchCtx)
    },
    downloadStates = downloadStates,

DetailContent.kt:252-255 — per-episode row (inside a LazyColumn item):
    onDownloadEpisode(episode, source, watchCtx)  ← passed to EpisodeRow.onDownload
    downloadState = downloadStates[episode.url] ?: EpisodeDownloadState.NotDownloaded,
    onDownloadCancel = { onDownloadCancel(episode.url) },
    onDownloadResume = { onDownloadResume(episode.url) },
    onDownloadRetry = { onDownloadRetry(episode.url) },
    onDownloadDelete = { onDownloadDelete(episode.url) },

EpisodesSection.kt:448-453 — EpisodeRow takes:
    onDownload: () -> Unit,
    downloadState: EpisodeDownloadState,
    onDownloadCancel / Resume / Retry / Delete: () -> Unit,

EpisodesSection.kt:642-651 — renders EpisodeDownloadControl with these callbacks.
```

And the host (`AppController`) provides:

```kotlin
// AppController.kt:1046
fun downloadEpisode(episode: SEpisode, source: AnimeSource, watchCtx: WatchEpisodeContext, contentId: String)

// AppController.kt:1128 (cancel — see below)
fun cancelDownload(contentId: String, episodeUrl: String)

// AppController.kt:1145
fun resumeDownload(contentId: String, episodeUrl: String)

// AppController.kt:1152
fun retryDownload(contentId: String, episodeUrl: String)

// AppController.kt:1159
fun deleteDownload(contentId: String, episodeUrl: String)
```

These all locate the task by `(contentId, episodeUrl)` in `downloadTasksFlow.value` and call the matching `manager` method. Example (cancel):

```kotlin
// AppController.kt:1128-1143
fun cancelDownload(contentId: String, episodeUrl: String) {
    // Special case: if we're still resolving (the user cancelled before enqueue),
    // we can't cancel a non-existent task — just clear the resolving flag + picker.
    if (resolvingEpisodes[episodeUrl] == true) {
        resolvingEpisodes.remove(episodeUrl)
        downloadPickerTarget?.let { if (it.episode.url == episodeUrl) downloadPickerTarget = null }
        return
    }
    val task = downloadTasksFlow.value.values.firstOrNull {
        it.request.anime.contentId == contentId && it.request.episode.episodeUrl == episodeUrl
    }
    if (task == null) {
        Log.w(TAG, "cancelDownload: no task for contentId=$contentId episodeUrl=$episodeUrl")
        return
    }
    scope.launch { downloadManager.cancelDownload(task.id) }
}
```

**Note on the cancel-resolve case**: if the user cancels during the `Resolving` phase (before the task is enqueued), there's no task to cancel — `AppController` just clears the `resolvingEpisodes` flag + dismisses the picker if it's showing for this episode. This is a UX consideration: the resolve network call itself can't be cancelled (it's still running on the resolver's IO scope), but the UI no longer shows the spinner.

## 6. The download tap flow (recap from `01-workflow-click-to-queue.md`)

When the user taps the download icon on a `NotDownloaded` episode:
1. `EpisodeDownloadControl.onDownload()` → `EpisodeRow.onDownload()` → `EpisodesSection.onDownloadEpisode(episode, source)` → `DetailContent.onDownloadEpisode(episode, source, watchCtx)` → `AnimeDetailScreen.onDownloadEpisode(episode, source, watchCtx)` → host's `AppController.downloadEpisode(episode, source, watchCtx, contentId)`.
2. `AppController.downloadEpisode` sets `resolvingEpisodes[episode.url] = true` (instant UI feedback → `Resolving` state) + launches the orchestrator.
3. Orchestrator resolves + enqueues → `EnqueueResult.Success(taskId)` (or `ShowPicker` → bottom sheet appears).
4. `resolvingEpisodes[episode.url]` is cleared in `finally` (line 1084).
5. The next `downloadTasksFlow` emission includes the new task → row state goes to `Queued` → `Downloading(progress)` → `Downloaded`.

## 7. Per-episode state visibility rules

| State | When `showDownloadButton = true` | When `showDownloadButton = false` |
|---|---|---|
| `NotDownloaded` | Shows ⬇ button | Hidden (no control rendered) |
| `Resolving` | Shows ⟳ + ✕ | Shows ⟳ + ✕ (always shown so user can cancel) |
| `Queued` | Shows ⟳ + ✕ | Shows ⟳ + ✕ |
| `Downloading` | Shows bar + ✕ | Shows bar + ✕ |
| `Paused` | Shows ▶ + ✕ | Shows ▶ + ✕ |
| `Error` | Shows ↻ + ✕ | Shows ↻ + ✕ |
| `Downloaded` | Shows ✓ + 🗑 | Shows ✓ + 🗑 |

Per `EpisodesSection.kt:642`:
```kotlin
if (showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded) {
    EpisodeDownloadControl(...)
}
```

So hiding the download button only hides the INITIAL ⬇ — once a download exists, the user can always manage it.

## 8. Batch download (multiple episodes at once)?

**Not implemented in the old project.** Each episode row has its own download button. There's no select-all / multi-select / "download episodes 1-12" feature in `EpisodesSection`.

Looking at `EpisodesSection.kt`'s `EpisodeRow` signature (line 440-454), there's no batch callback. Each row gets its own `onDownload: () -> Unit`.

The only way to download multiple episodes is to tap each one individually. The concurrency limit (default 1) means they queue up FIFO.

**For the new project**: a "Download all" button at the section level would be a nice UX addition. See `13-implementation-plan.md`.

## 9. The "Source unavailable" edge case

If the anime's extension source is no longer installed (`episodeState is EpisodeState.Loaded && currentMatch == null`), the download button is rendered but tapping it does nothing useful — `AppController.downloadEpisode(episode, source, ...)` requires a non-null `AnimeSource`, and the source isn't available.

Looking at `EpisodesSection.kt:237-269`, the section shows a "Source — unavailable" chip when this happens. The user is expected to switch sources via `ManualSearchSheet` before they can download.

## 10. Honest notes / bugs

- **KDoc says `AnimatedContent`, code doesn't use it** (line 38 of `EpisodeDownloadControl.kt`). State transitions are abrupt.
- **No batch download** — minor UX limitation.
- **Cancel-during-resolve doesn't actually cancel the network call** — the resolver's HTTP request keeps running. Only the UI spinner is hidden. For a long-running resolve (3+ seconds), the user might re-tap download, launching a SECOND resolve. The orchestrator doesn't dedupe resolves.
- **`EpisodeDownloadState.Downloading.progress`** is `Int` (0..100). The `DynamicProgressTracker` caps it at 90 during download — so the bar never shows >90% until `Downloaded`. Reasonable.
- **The `downloadState` lookup is per-row** (`downloadStates[episode.url]`), but the map is collected ONCE per screen in `AppController` and rebuilt on every `downloadTasksFlow` emission. For a 24-episode anime, that's 24 map lookups per recomposition — cheap (just HashMap gets), but worth knowing.
- **No "downloaded" badge on the row itself** — the only indicator is the green ✓ icon in the download control. Tapping the row body plays the episode (online OR offline, depending on whether a local copy exists — see `10-player-integration.md`).

## 11. Summary — what the new project should replicate

1. **Separate UI-side `EpisodeDownloadState` sealed type** in `:feature:anime-details` (no `:core:download` dependency).
2. **Map `DownloadTask` → `EpisodeDownloadState`** in the host (AppController-equivalent), keyed by episode URL (NOT by composite key — the row needs to look up by `episode.url`).
3. **The 7 visual states** (NotDownloaded / Resolving / Queued / Downloading / Paused / Error / Downloaded) with the per-state control rendering from §3.
4. **`Resolving` is a UI-only state** driven by a separate `resolvingEpisodes` map (not by the engine).
5. **The control is always shown when the state is non-`NotDownloaded`** — even if the user disabled "Show download button".
6. **Per-row callbacks**: `onDownload / onCancel / onResume / onRetry / onDelete`. The host locates the task by `(contentId, episodeUrl)` and calls the manager.
7. **Cancel-during-resolve**: clear the resolving flag + dismiss the picker (no actual task to cancel).
8. **Consider adding** (not in old project):
   - `AnimatedContent` for smooth state transitions (the KDoc claims it but the code doesn't).
   - A "Download all" / batch-select feature at the section level.
   - A "Downloaded" badge on the row body (in addition to the ✓ icon in the control).
