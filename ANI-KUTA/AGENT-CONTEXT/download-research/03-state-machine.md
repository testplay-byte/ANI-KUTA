# 03 — Download State Machine

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStatus.kt` (42 lines) + relevant parts of `DownloadQueue.kt` and `DownloadStore.kt`.

## 1. The `DownloadStatus` enum

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStatus.kt`

> **REVIEW-5 M9 + M12:** the canonical type is `enum class DownloadStatus` (UPPERCASE constants —
> matches the OLD project + `13-implementation-plan.md` line 216 + Review 3 M1's recommendation).
> The NEW project's stub `DownloadState.kt` (sealed interface, PascalCase variants — `Failed`/`Queued`/...)
> is DELETED in Phase D.0 (per `13-implementation-plan.md` task list). The QoL `16-quality-of-life.md`
> §1.3 draft that proposed `sealed interface DownloadStatus` with `data class RETRYING(...)` is
> REVISED to use the enum + put the retry metadata on `DownloadTask` instead (the enum constant
> can't carry per-instance data).

```kotlin
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    RETRYING,   // REVIEW-5 M9 — NEW. Auto-retry in-progress (see 16-quality-of-life.md §1.3).
                // The retry metadata (attempt / maxAttempts / lastError) lives on DownloadTask
                // (see DownloadTask.retryAttempt / retryMaxAttempts / lastError below).
    PAUSED,
    COMPLETED,
    ERROR,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isActive: Boolean get() = this == DOWNLOADING || this == RETRYING
}
```

| State | Meaning |
|---|---|
| `QUEUED` | In the queue, waiting for a download slot (Semaphore permit). |
| `DOWNLOADING` | Actively downloading — `DownloadTask.progress` is updating. |
| `RETRYING` | Auto-retry in-progress — the engine caught a retryable error (`RetryPolicy.forException` returned a non-zero maxAttempts) + is in the backoff delay before the next attempt. UI shows `"Retrying (2/3)…"`. The retry metadata lives on `DownloadTask.retryAttempt` + `retryMaxAttempts` + `lastError`. |
| `PAUSED` | User-paused; stays in the queue, can be resumed. |
| `COMPLETED` | Finished — file + all subtitles are on disk. **Terminal.** |
| `ERROR` | Failed (network/IO/validation) AND all retry attempts exhausted. Recoverable via user-initiated retry. |
| `CANCELLED` | User-cancelled + file deleted. **Terminal.** (In practice never persisted — see `02-queue-management.md` §5.) |

The `DownloadTask` data class gains three fields to support RETRYING (carrying the metadata
the enum constant can't):

```kotlin
@Serializable
data class DownloadTask(
    // ... existing fields ...
    val retryAttempt: Int = 0,
    val retryMaxAttempts: Int = 3,
    val lastError: String? = null,
)
```

These are updated by `setRetryingStatus` + `setErrorStatus` (defined in `02-queue-management.md` §13).

## 2. State machine diagram

> **REVIEW-5 M9:** the diagram below is the ORIGINAL 6-state diagram from the KDoc on
> `DownloadStatus.kt:7-14`. The 7th state (`RETRYING`) is added in §2.1 below — the ASCII
> art is preserved as-is for traceability to the OLD project's KDoc, with a §2.1 addendum
> showing the RETRYING transitions.

Quoted directly from the KDoc on `DownloadStatus.kt:7-14`:

```
Queued ──start──▶ Downloading ──100%──▶ Completed
  │                  │                    
  │                  ├──pause──▶ Paused ──resume──▶ Queued
  │                  ├──error──▶ Error ──retry──▶ Queued
  │                  └──cancel──▶ Cancelled (terminal)
  └──cancel──▶ Cancelled (terminal)
```

```
                       ┌──────────────────────────────────┐
                       │                                  │
                       ▼                                  │
                    ┌───────┐  start (permit acquired) ┌──────────┐
       enqueue ───▶ │QUEUED│ ────────────────────────▶ │DOWNLOADING│
                    └───────┘                            └──────────┘
                       │   ▲                                 │
              pause    │   │ resume                          │ 100%
              (rare —  │   │ (also from ERROR)               │
               before  │   │                                 ▼
               permit) │   │                             ┌──────────┐
                       ▼   │                             │COMPLETED │
                    ┌───────┐                            │(terminal)│
                    │PAUSED │ ◀──── pause                └──────────┘
                    └───────┘       │
                       ▲            │
                       │            │ error (DownloadException
                       │            │  or uncaught Exception)
                       │            ▼
                       │        ┌───────┐
                       └────────│ ERROR │
                          retry └───────┘
                                │
                                │ cancel (any non-terminal state)
                                ▼
                            ┌──────────┐
                            │CANCELLED │  (in practice: removed from
                            │(terminal)│   list entirely, never
                            └──────────┘    persisted as CANCELLED)
```

### 2.1 The RETRYING state (NEW — REVIEW-5 M9)

The 7th state `RETRYING` is added to the diagram above. Here's the augmented diagram with
RETRYING + its transitions:

```
Queued ──start──▶ Downloading ──100%──▶ Completed
  │                  │  │
  │                  │  ├──retryable-error──▶ Retrying ──backoff-elapsed──▶ Downloading (next attempt)
  │                  │  │                         │
  │                  │  │                         ├──max-attempts-exceeded──▶ Error ──retry──▶ Queued
  │                  │  │                         ├──pause──▶ Paused ──resume──▶ Queued
  │                  │  │                         └──cancel──▶ Cancelled (terminal)
  │                  │  │
  │                  ├──pause──▶ Paused ──resume──▶ Queued
  │                  ├──error──▶ Error ──retry──▶ Queued
  │                  └──cancel──▶ Cancelled (terminal)
  └──cancel──▶ Cancelled (terminal)
```

**RETRYING transitions** (REVIEW-5 M9):
- `DOWNLOADING` → retryable error → `RETRYING` (the queue's `setRetryingStatus` is called from
  the retry loop's catch block — see `16-quality-of-life.md` §1.2).
- `RETRYING` → backoff elapsed → `DOWNLOADING` (the retry loop calls `downloader.download(task)`
  again; the queue mutates status back to DOWNLOADING).
- `RETRYING` → max attempts exceeded → `ERROR` (the retry loop's `attempt >= policy.maxAttempts`
  check; the queue calls `setErrorStatus`).
- `RETRYING` → user pause → `PAUSED` (the queue's `pause` accepts RETRYING — cancels the retry
  loop's delay Job + transitions to PAUSED. See REVIEW-5 M10 + `02-queue-management.md` §13.)
- `RETRYING` → user cancel → `CANCELLED` (same as pause but removes from queue + cleans up).
- `RETRYING` → app restart → `QUEUED` (the `resetDownloadingToQueued` SQL now resets BOTH
  DOWNLOADING AND RETRYING — see `11-db-schema.md` §3 line 251. REVIEW-5 M6 / R3-I2 / R4-C7.)

The retry metadata (`attempt`, `maxAttempts`, `lastError`) lives on `DownloadTask` (not on the
enum constant — enums can't carry per-instance data). See §1 above.

## 3. Allowed transitions (reference table)

| From → Action | To | Where enforced |
|---|---|---|
| (none) → `enqueue` | `QUEUED` | `DownloadQueue.enqueue` (line 100) |
| `QUEUED` → permit acquired | `DOWNLOADING` | `DownloadQueue.launchDownload` (line 197-199) |
| `DOWNLOADING` → 100% | `COMPLETED` | `HttpDownloader.download` returns the completed task → `mutateTask(id) { completed }` (line 230) |
| `DOWNLOADING` → pause | `PAUSED` | `DownloadQueue.pause` (line 115) |
| `QUEUED` → pause | `PAUSED` | `DownloadQueue.pause` (line 110-119 — also accepts QUEUED) |
| `PAUSED` → resume | `QUEUED` | `DownloadQueue.resumeInternal` (line 166-174) |
| `ERROR` → resume | `QUEUED` | `DownloadQueue.resumeInternal` (line 168 — accepts ERROR too) |
| `ERROR` → retry | `QUEUED` (progress=0) | `DownloadQueue.retry` (line 137-145) |
| `DOWNLOADING` → retryable error | `RETRYING` | `DownloadQueue.launchDownload` retry loop's catch block → `setRetryingStatus` (`16-quality-of-life.md` §1.2). REVIEW-5 M9. |
| `RETRYING` → backoff elapsed | `DOWNLOADING` | retry loop calls `downloader.download(task)` again → `setDownloadingStatus`. REVIEW-5 M9. |
| `RETRYING` → max attempts exceeded | `ERROR` | retry loop's `attempt >= policy.maxAttempts` check → `setErrorStatus`. REVIEW-5 M9. |
| `RETRYING` → pause | `PAUSED` | `DownloadQueue.pause` (REVIEW-5 M10 — accepts RETRYING; cancels the retry loop's delay Job). |
| `RETRYING` → cancel | `CANCELLED` | `DownloadQueue.cancel` (REVIEW-5 M10). |
| `RETRYING` → app restart | `QUEUED` | `DownloadStore.resetDownloadingToQueued` SQL `WHERE state IN ('DOWNLOADING', 'RETRYING')` (REVIEW-5 M6). |
| `DOWNLOADING` → error (non-retryable OR retries exhausted) | `ERROR` | `DownloadQueue.launchDownload` catch blocks (line 241-263) |
| `QUEUED`/`DOWNLOADING`/`PAUSED`/`RETRYING`/`ERROR` → cancel | (removed from list) | `DownloadQueue.cancel` (line 123-135 — REVIEW-5 M10 added RETRYING to the allowed set) |
| `COMPLETED` → removeFromQueue | (removed from list, file stays) | `DownloadQueue.removeCompleted` (line 148-151) |
| `COMPLETED` → deleteDownload | (removed + file deleted) | `DefaultDownloadManager.deleteDownload` (line 142-148) |
| `ERROR` → enqueue same episode | `QUEUED` (via `resumeInternal`) | `DownloadQueue.enqueue` (line 91-94) |

**Disallowed** (silently no-op):
- `pause` on `PAUSED` / `ERROR` / `COMPLETED` (line 112-113: only accepts `DOWNLOADING` + `QUEUED` + `RETRYING` per REVIEW-5 M10).
- `retry` on non-`ERROR` (line 139: `if (it.status != DownloadStatus.ERROR) return@mutateTask it`).
- `resume` on `QUEUED` / `DOWNLOADING` / `RETRYING` / `COMPLETED` (line 168: only accepts `PAUSED` + `ERROR`).

## 4. How state is persisted

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt` (75 lines)

```kotlin
class DownloadStore(store: PreferenceStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val tasksPref: Preference<List<DownloadTask>> = store.getObject(
        KEY_TASKS,
        emptyList(),
        { list -> json.encodeToString(ListSerializer(DownloadTask.serializer()), list) },
        { str ->
            try { json.decodeFromString(ListSerializer(DownloadTask.serializer()), str) }
            catch (e: Exception) {
                DownloadLogger.w("Failed to decode download store, starting fresh", e)
                emptyList()
            }
        },
    )

    val changes: Flow<List<DownloadTask>> = tasksPref.changes().map { it }
    fun getAll(): List<DownloadTask> = tasksPref.get()
    fun setAll(tasks: List<DownloadTask>) { tasksPref.set(tasks) }
    fun purgeCancelled(): List<DownloadTask> { ... }

    companion object {
        private const val KEY_TASKS = "pref_download_tasks_v1"
    }
}
```

### Key facts:
- **Storage medium**: `SharedPreferences` (via `PreferenceStore.getObject`).
- **Format**: JSON-serialized `List<DownloadTask>` (one big JSON blob under key `pref_download_tasks_v1`).
- **NO DB tables** for downloads in the old project. The SQLDelight tables (`animes`, `episodes`, `animehistory`, etc.) do NOT include any download-related table. (Confirmed by grepping the `.sq` files for "download" → no matches.) See `11-db-schema.md`.
- **Reactivity**: `tasksPref.changes()` is a Flow — the store can be observed reactively. (However, `DownloadQueue` doesn't observe it — it owns the state in `_tasks` and WRITES to the store; the store's `changes` flow is only used by other consumers like migrations.)
- **Throttling**: `DownloadQueue.persistThrottled()` writes at most once per 1 second during progress ticks; `persistNow()` writes on state changes.
- **Crash recovery**: on startup, `DownloadQueue._tasks = MutableStateFlow(store.purgeCancelled())` reads the persisted list, filters out any CANCELLED tasks, and uses the result as the initial state. QUEUED/DOWNLOADING/PAUSED tasks resume as QUEUED (the partial temp file is discarded — `HttpDownloader.download` always writes to a fresh temp file, deleting any existing one via `openVideoOutputStream`'s delete-first).
- **Failure resilience**: if the JSON is corrupt, `getAll()` returns `emptyList()` (the catch in the deserializer). The app starts with a fresh queue rather than crashing.

### What's persisted in each task

`DownloadTask` (from `DownloadTask.kt:26-58`):
```kotlin
@Serializable
data class DownloadTask(
    val id: Long,
    val request: DownloadRequest,        // anime + episode + videoUrl + headers + tracks
    val status: DownloadStatus,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoUri: String? = null,         // content:// URI of the finished video
    val subtitleUris: List<String> = emptyList(),  // content:// URIs of subtitle files
)
```

`DownloadRequest` is also `@Serializable` — carries the full anime + episode + video URL + headers + subtitle/audio track list. The whole thing is one JSON blob.

## 5. How the UI observes state

### `episodeDownloadStates` (per-episode row UI)

**File**: `DefaultDownloadManager.kt:107-109`:
```kotlin
override val episodeDownloadStates: Flow<Map<String, DownloadTask>> =
    queue.tasks.map { list -> list.associateBy { it.key } }
```

Collected by `AppController` (`AppController.kt:228-233`):
```kotlin
private val _downloadTasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
val downloadTasksFlow: StateFlow<Map<String, DownloadTask>> = _downloadTasks.asStateFlow()

init {
    scope.launch {
        downloadManager.episodeDownloadStates.collect { tasks ->
            _downloadTasks.value = tasks
        }
    }
}
```

`AppController` then exposes `downloadTasksFlow` as a `StateFlow` so the UI can `collectAsStateWithLifecycle`. The host maps each `DownloadTask` → `EpisodeDownloadState` (the sealed type) — see `09-details-page-download-ui.md` for the mapping.

### `activeDownloads` + `completedDownloads` (Downloads page UI)

**File**: `DownloadViewModel.kt:36-49`:
```kotlin
init {
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
    // ... auto-clear completed after 10 seconds (lines 51-68)
}
```

The ViewModel combines three flows:
- `manager.activeDownloads` — the live queue.
- `manager.completedDownloads` — the on-disk library.
- `preferences.downloadFolderUri().changes()` — re-emits when the user picks/changes the folder.

## 6. Notification firing (state → notification)

Driven by `DownloadQueue`'s job-completion callbacks (set by `DefaultDownloadManager`):
- `onTaskCompleted` (line 72): `notifier.notifyCompleted(task)` — one-shot notification with "Download complete" + anime title + EP number.
- `onTaskError` (line 73): `notifier.notifyError(task)` — one-shot notification with "Download failed" + error message.

The **ongoing summary notification** is driven by `DefaultDownloadManager.observeJob` (lines 87-97):
```kotlin
private val observeJob = scope.launch {
    queue.tasks.collect { all ->
        try {
            val active = all.filter { it.isInQueue }
            notifier.updateProgress(active)
            if (active.isEmpty()) notifier.cancelActive()
        } catch (e: Exception) {
            DownloadLogger.e("Download state observer failed (non-fatal)", e)
        }
    }
}
```

`notifier.updateProgress` is throttled to 1 update per 800ms (PROGRESS_THROTTLE_MS) — see `06-notifications-foreground-service.md`.

## 7. Restart-after-crash behavior

Per `DownloadManager.kt:19-22` (interface KDoc):
> "the queue is persisted across app restarts via `DownloadStore`; a QUEUED/DOWNLOADING/PAUSED task resumes in QUEUED state on next launch (a partial file is discarded + re-downloaded for the MVP; resume-from-offset is a 1DM-method enhancement)."

Translation:
- App is killed mid-download → task is persisted as `DOWNLOADING` in SharedPreferences.
- Next launch: `DownloadQueue._tasks` is initialized with the persisted list (the `DOWNLOADING` task is in the list as-is).
- BUT no `tryStartNext()` is called automatically on construction. So a `DOWNLOADING` task stays `DOWNLOADING` in memory but nothing is actually downloading. **This is a subtle bug**: on restart, a previously-DOWNLOADING task shows as DOWNLOADING in the UI forever (with the last-known progress), but no actual download is happening.

**Likely intended behavior**: tasks should be reset to `QUEUED` on startup if they were `DOWNLOADING` when the app died. The `purgeCancelled()` only handles `CANCELLED` — not `DOWNLOADING`. Worth flagging for the new project. (Or maybe `tryStartNext()` should be called once after construction — looking at `DefaultDownloadManager`, no it isn't. The queue just sits there until the user enqueues something new.)

Actually re-reading: `tryStartNext()` IS called from `enqueue`/`pause`/`resume`/etc., so any subsequent user action would trigger a `tryStartNext` which would find the orphaned `DOWNLOADING` task. But `tryStartNext` only picks `QUEUED` tasks (line 185: `firstOrNull { it.status == DownloadStatus.QUEUED }`) — so an orphaned `DOWNLOADING` task never restarts. The user would have to manually pause+resume it.

**Definitely a bug**. New project should explicitly reset `DOWNLOADING` → `QUEUED` on startup.

## 8. The `EpisodeDownloadState` sealed type (UI-side)

**File**: `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadState.kt`

```kotlin
sealed interface EpisodeDownloadState {
    data object NotDownloaded : EpisodeDownloadState
    data object Resolving : EpisodeDownloadState       // before enqueue
    data object Queued : EpisodeDownloadState
    data class Downloading(val progress: Int) : EpisodeDownloadState
    data object Paused : EpisodeDownloadState
    data class Error(val message: String?) : EpisodeDownloadState
    data object Downloaded : EpisodeDownloadState
}
```

Note: this is a SEPARATE type from `DownloadStatus` — it lives in `:feature:anime-details` (no dependency on `:core:download`). The host maps `DownloadTask` → `EpisodeDownloadState` for each episode row.

There's no `Resolving` in `DownloadStatus` — that's a UI-only state for the 1-3 seconds between tapping download and the task being enqueued. Driven by `AppController.resolvingEpisodes: SnapshotStateMap<String, Boolean>` (set to `true` on tap, cleared in `finally` after `enqueueDownload` returns).

## 9. Where the state machine lives in the new project

> **REVIEW-5 M12:** the canonical type is `enum class DownloadStatus` (UPPERCASE constants —
> matches the OLD project + `13-implementation-plan.md` line 216 + Review 3 M1's recommendation).
> The NEW project's stub `DownloadState.kt` (sealed interface, PascalCase variants — `Failed`/
> `Queued`/`Downloading`/`Paused`/`Completed`) is **DELETED in Phase D.0** per
> `13-implementation-plan.md` task list.

The new project's stub `DownloadState.kt` (preserved here for traceability — DO NOT implement):
```kotlin
// DELETED in Phase D.0 — replaced by `DownloadStatus.kt` (the enum from §1 above).
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data object Paused : DownloadState
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}
```

Differences vs the canonical enum (all RESOLVED by deleting the stub + using the enum):
- ~~`Failed` instead of `ERROR`~~ → use `ERROR` (matches OLD project + all docs).
- ~~**No `CANCELLED`**~~ → keep `CANCELLED` (the SQL column comment + `02-queue-management.md` + this doc all reference it).
- ~~**No `Resolving`**~~ → keep as UI-only state (`EpisodeDownloadState.Resolving` — never persisted).
- ~~`Downloading` carries `progress: Int` inline~~ → keep `progress` as a separate field on `DownloadTask` (matches OLD project + the DB schema).
- ~~No `RETRYING`~~ → ADD `RETRYING` to the enum (REVIEW-5 M9). The retry metadata (`attempt`, `maxAttempts`, `lastError`) lives on `DownloadTask` (enums can't carry per-instance data).

The QoL `16-quality-of-life.md` §1.3 draft that proposed `sealed interface DownloadStatus` with
`data class RETRYING(attempt, maxAttempts, lastError)` is **REVISED** to use the enum + put the
retry metadata on `DownloadTask` instead. This keeps `status == DownloadStatus.ERROR` style checks
(which the implementation plan + all docs already use) working without refactor to
`status is DownloadStatus.ERROR`.
