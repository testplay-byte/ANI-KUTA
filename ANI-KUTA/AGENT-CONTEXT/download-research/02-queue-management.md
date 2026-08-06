# 02 — Queue Management: `DownloadQueue` internals

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadQueue.kt` (315 lines total).

## 1. The state holder

```kotlin
// DownloadQueue.kt:58-59
private val _tasks = MutableStateFlow(store.purgeCancelled())
val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
```

- A **single** `MutableStateFlow<List<DownloadTask>>` holds **all** tasks (queued + downloading + paused + errored + completed).
- `DefaultDownloadManager` derives three views via `map` (lines 99-105): `activeDownloads` (in-queue, i.e. not COMPLETED), `completedDownloads` (COMPLETED), `allDownloads` (everything).
- Initialization: `_tasks` is populated from `store.purgeCancelled()` — which reads the persisted JSON list from SharedPreferences and filters out any `CANCELLED` tasks left from a prior session.

## 2. ID counter

```kotlin
// DownloadQueue.kt:61
private val idCounter = AtomicLong(loadMaxId() + 1)
```

`loadMaxId()` (line 305) reads `store.getAll().maxOfOrNull { it.id } ?: 0L` — directly from the store (NOT from `_tasks`) so it's safe to call during construction before `_tasks` is initialized. **Initialization order matters**: `_tasks` MUST come before `idCounter` (Kotlin runs property initializers top-to-bottom; this was the cause of an earlier startup crash per the KDoc on lines 51-57).

## 3. Job map

```kotlin
// DownloadQueue.kt:63
private val jobs = mutableMapOf<Long, Job>()
```

Tracks the coroutine `Job` for each active download. Used by `pause`/`cancel` to `jobs.remove(id)?.cancel()`. **NOT thread-safe** — but all mutations happen on the `scope`'s dispatcher (`Dispatchers.IO`).

## 4. Concurrency: Semaphore-based

```kotlin
// DownloadQueue.kt:73-78
@Volatile
private var permits: Semaphore = Semaphore(currentConcurrentLimit())

@Volatile
private var currentLimit: Int = currentConcurrentLimit()
```

`currentConcurrentLimit()` (line 296-297):
```kotlin
private fun currentConcurrentLimit(): Int =
    preferences.concurrentDownloads().get().coerceIn(1, 5)
```

- The semaphore is **rebuilt** when the pref changes — `refreshConcurrency()` (line 154) detects a delta and replaces the Semaphore. **Caveat (per the KDoc)**: rebuilding the Semaphore doesn't carry over already-acquired permits, so an in-flight task that holds a permit from the old semaphore keeps it (the old Semaphore is GC'd once its permit count reaches 0).
- Default = 1 download at a time. UI clamps to 1..5.

## 5. Public operations

### `enqueue(request)` — line 86
```kotlin
fun enqueue(request: DownloadRequest): Long {
    val existing = _tasks.value.firstOrNull { it.key == keyFor(request) }
    if (existing != null) {
        if (existing.status == DownloadStatus.ERROR) {
            resumeInternal(existing.id)  // re-queue an errored task
        }
        return existing.id  // dedup
    }
    val task = DownloadTask(
        id = idCounter.getAndIncrement(),
        request = request,
        status = DownloadStatus.QUEUED,
        createdAt = System.currentTimeMillis(),
    )
    updateTasks(_tasks.value + task)
    persistNow()
    tryStartNext()
    return task.id
}
```

**Dedup behavior**: if a task with the same `"$contentId|$episodeNumber"` key exists:
- `COMPLETED` → returns its ID, no re-download.
- `QUEUED / DOWNLOADING / PAUSED` → returns its ID, no-op.
- `ERROR` → calls `resumeInternal(id)` which sets it back to `QUEUED` + `tryStartNext()`.

### `pause(taskId)` — line 110
```kotlin
fun pause(taskId: Long) {
    val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
    if (task.status != DownloadStatus.DOWNLOADING &&
        task.status != DownloadStatus.QUEUED) return
    jobs.remove(taskId)?.cancel()  // cancels the coroutine job
    mutateTask(taskId) { it.copy(status = DownloadStatus.PAUSED, updatedAt = now()) }
    persistNow()
    tryStartNext()
}
```

Cancelling the Job triggers `CancellationException` inside `launchDownload`'s `try` block — caught at line 238-240 and logged. The status was ALREADY set to PAUSED before `cancel()` returns, so the catch block doesn't overwrite it.

### `resume(taskId)` — line 121
```kotlin
fun resume(taskId: Long) = resumeInternal(taskId)
```
Delegates to `resumeInternal` (line 166):
```kotlin
private fun resumeInternal(taskId: Long) {
    mutateTask(taskId) {
        if (it.status != DownloadStatus.PAUSED && it.status != DownloadStatus.ERROR) return@mutateTask it
        it.copy(status = DownloadStatus.QUEUED, updatedAt = now())
    }
    persistNow()
    tryStartNext()
}
```

**Note**: a paused download restarts from scratch on resume. Per `DownloadManager.kt:21-22` KDoc: "a partial file is discarded + re-downloaded for the MVP; resume-from-offset is a 1DM-method enhancement." However, the **Advanced method** does support per-chunk resume (see `05-downloaders.md`).

### `cancel(taskId)` — line 123
```kotlin
fun cancel(taskId: Long) {
    val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
    jobs.remove(taskId)?.cancel()
    // Note: partial-file cleanup for a cancelled in-progress download is
    // handled by the manager's deleteDownload (storage.deleteEpisode). A
    // fresh re-download also overwrites the partial video file via
    // openVideoOutputStream (which deletes any existing file first).
    updateTasks(_tasks.value.filterNot { it.id == taskId })
    persistNow()
    tryStartNext()
}
```

Cancelled tasks are **removed from the list entirely** — not kept as `CANCELLED`. The `CANCELLED` enum value exists but is effectively dead code (it's only ever set during transitions that immediately remove the task).

### `retry(taskId)` — line 137
```kotlin
fun retry(taskId: Long) {
    mutateTask(taskId) {
        if (it.status != DownloadStatus.ERROR) return@mutateTask it
        it.copy(status = DownloadStatus.QUEUED, progress = 0, errorMessage = null, updatedAt = now())
    }
    persistNow()
    tryStartNext()
}
```

Only works on `ERROR` tasks. Resets `progress = 0`.

### `removeCompleted(taskId)` — line 148
```kotlin
fun removeCompleted(taskId: Long) {
    updateTasks(_tasks.value.filterNot { it.id == taskId })
    persistNow()
}
```
Called by the manager's `removeFromQueue(taskId)` (the 10-second auto-clear). Does NOT delete the file — only the in-memory task record.

### `refreshConcurrency()` — line 154
```kotlin
fun refreshConcurrency() {
    val newLimit = currentConcurrentLimit()
    if (newLimit != currentLimit) {
        currentLimit = newLimit
        permits = Semaphore(newLimit)
        tryStartNext()
    }
}
```
Called when the user changes the `concurrentDownloads` pref. Note: this is NOT automatically called from a Flow collector — the settings screen would need to call it explicitly. Looking at `DownloadSettingsScreen.kt`, the slider just calls `preferences.concurrentDownloads().set(...)` — there's no explicit `refreshConcurrency` call. **Potential bug**: the new limit only takes effect after a restart (when the queue is reconstructed), OR after the next task completes/pauses/cancels (which calls `tryStartNext` — but with the OLD semaphore that has fewer permits than desired). Worth flagging for the new project.

## 6. `tryStartNext()` — the scheduler

**Line 180**:
```kotlin
private fun tryStartNext() {
    if (!connectivityCheck()) {
        DownloadLogger.d("Skipping start — connectivity check failed (Wi-Fi-only?)")
        return
    }
    val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return
    if (jobs.containsKey(next.id)) return  // already launching
    launchDownload(next)
}
```

- Called after every state change (enqueue, pause, resume, cancel, retry, refreshConcurrency, job completion).
- Picks the FIRST `QUEUED` task in list order (FIFO — see §8 below).
- Skips if `connectivityCheck()` fails (Wi-Fi-only pref enforced).
- Cheap if nothing to start.

`connectivityCheck` is injected by `DefaultDownloadManager` (line 69): `{ isNetworkAllowed() }`. `isNetworkAllowed()` (line 242-254) checks `ConnectivityManager` + `NetworkCapabilities.TRANSPORT_WIFI` if `wifiOnly` is on; fails open on error.

## 7. `launchDownload(task)` — the per-task coroutine

**Line 190-271** (key parts):

```kotlin
private fun launchDownload(task: DownloadTask) {
    val job = scope.launch {
        try {
            permits.withPermit {
                // Re-confirm status (may have been paused before the permit was acquired).
                val current = _tasks.value.firstOrNull { it.id == task.id }
                if (current?.status != DownloadStatus.QUEUED) return@withPermit
                mutateTask(task.id) {
                    it.copy(status = DownloadStatus.DOWNLOADING, updatedAt = now())
                }
                persistNow()

                var prevTotal = 0L
                var prevEstimate = 0L

                val completed = downloader.download(task) { downloaded, total ->
                    val update = DynamicProgressTracker.compute(
                        downloaded = downloaded,
                        reportedTotal = total,
                        previousTotal = prevTotal,
                        previousEstimate = prevEstimate,
                    )
                    prevTotal = update.displayTotalBytes
                    prevEstimate = update.updatedEstimate
                    mutateTask(task.id) {
                        it.copy(
                            progress = update.progress,
                            downloadedBytes = downloaded,
                            totalBytes = update.displayTotalBytes,
                            updatedAt = now(),
                        )
                    }
                    persistThrottled()
                }
                mutateTask(task.id) { completed }  // status=COMPLETED, progress=100, videoUri set
                persistNow()
                try { onTaskCompleted?.invoke(completed) }
                catch (e: Exception) { DownloadLogger.w("onTaskCompleted callback failed (non-fatal)", e) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            DownloadLogger.d("Job cancelled: id=${task.id}")
            // Pause/cancel handlers already set the status — nothing to do here.
        } catch (e: DownloadException) {
            val errorTask = _tasks.value.firstOrNull { it.id == task.id }?.copy(
                status = DownloadStatus.ERROR, errorMessage = e.message, updatedAt = now(),
            )
            if (errorTask != null) {
                mutateTask(task.id) { errorTask }
                persistNow()
                try { onTaskError?.invoke(errorTask) }
                catch (cb: Exception) { ... }
            }
        } catch (e: Exception) {
            // same as DownloadException branch, but errorMessage = e.message ?: className
        } finally {
            jobs.remove(task.id)
            tryStartNext()  // a permit freed up
        }
    }
    jobs[task.id] = job
}
```

Key things:
- **Permit acquired inside `scope.launch`** — so the QUEUED→DOWNLOADING transition happens AFTER acquiring the permit. If multiple tasks are QUEUED, only the ones that get permits actually transition; the rest stay QUEUED.
- **Re-confirm status** inside the permit block — defends against a pause issued between `tryStartNext` and the permit acquisition.
- **Progress callback** runs on every byte tick. `DynamicProgressTracker.compute` (see `05-downloaders.md`) smooths the progress (caps at 90%, handles unknown totals).
- `persistThrottled()` (line 283) writes to the store at most once per 1 second (`PERSIST_INTERVAL_MS = 1000L`) — avoids hammering SharedPreferences during a big file.
- `persistNow()` writes immediately — called on state CHANGES (queued/started/paused/completed/error).
- `onTaskCompleted` / `onTaskError` are callbacks set by `DefaultDownloadManager` (line 72-73) — they post the one-shot notifications.
- `finally { tryStartNext() }` — guarantees the next QUEUED task starts when a permit frees up, even on error/cancel.

## 8. Queue ordering — is it FIFO? Can the user reorder?

**No user reordering of the download queue in the OLD project.** The queue is implicitly FIFO:
- New tasks are appended to the end of `_tasks.value` (`updateTasks(_tasks.value + task)` at line 103).
- `tryStartNext` picks the first `QUEUED` task (line 185).

The `DragReorderableList` composable in `:feature:download/components/` is used in `DownloadSettingsScreen` ONLY for **preference list reordering** (quality / audio / server priorities) — NOT for reordering the download queue. So the prompt's expectation that the user can reorder the queue is **not implemented** in the old project. This is a "design decision for the new project" — see `13-implementation-plan.md`.

## 9. Pause/Resume/Cancel/Retry — full state-transition summary

| From → Action | To | Side effects |
|---|---|---|
| QUEUED → pause | PAUSED | Job (if any) cancelled |
| DOWNLOADING → pause | PAUSED | Job cancelled; partial temp file left in cache (cleaned up on next enqueue via `openVideoOutputStream`'s delete-first) |
| QUEUED → cancel | (removed from list) | Job (if any) cancelled; no file cleanup |
| DOWNLOADING → cancel | (removed from list) | Job cancelled; partial temp file cleaned up by `HttpDownloader.download`'s `finally { tempCache.cleanupTask(task.id) }` |
| PAUSED → resume | QUEUED | `tryStartNext()` |
| ERROR → resume | QUEUED | `tryStartNext()` |
| ERROR → retry | QUEUED (progress=0, errorMessage=null) | `tryStartNext()` |
| COMPLETED → removeFromQueue | (removed from list) | File stays on disk |
| COMPLETED → deleteDownload | (removed from list) | `storage.deleteEpisode()` removes the Episode NNN folder |
| ERROR → enqueue same episode | QUEUED | `resumeInternal` is called |

## 10. Persistence strategy

- `persistThrottled()` — at most once per 1 second. Used during progress ticks.
- `persistNow()` — immediate. Used on every state CHANGE (not progress tick).
- Writes the ENTIRE `_tasks` list as JSON to SharedPreferences via `store.setAll(_tasks.value)`.
- On startup: `store.purgeCancelled()` filters out any `CANCELLED` tasks (defensive; cancelled tasks aren't supposed to be persisted, but the purge covers the case of a crash mid-cancel).

## 11. Threading model

- The `scope` passed to `DownloadQueue` is `CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler{...})` — created in `DefaultDownloadManager.kt:53-57`.
- All queue mutations (`mutateTask`, `updateTasks`, `enqueue`, `pause`, etc.) happen on `Dispatchers.IO` (called from `scope.launch` blocks or from suspend functions on `Dispatchers.IO`).
- The `StateFlow` is thread-safe to collect from the main thread — UI just calls `collectAsStateWithLifecycle()`.
- `mutateTask` (line 273) reads `_tasks.value` (atomic) + writes back via `_tasks.value = newList` (atomic) — but NOT in a `Mutex`. **Potential race**: two concurrent `mutateTask` calls on DIFFERENT task IDs could race if they read the same snapshot. In practice this doesn't happen because all calls are funneled through `scope.launch` (single dispatcher = serialized on Dispatchers.IO's thread pool, but Dispatchers.IO is multi-threaded so this CAN race). **Honest note**: the queue's threading is "best-effort" — not strictly correct under high concurrency, but works in practice because the UI rarely fires multiple actions simultaneously. Worth hardening in the new project (use `Mutex` or `Dispatchers.Main`).

## 12. Reactivity summary

| Flow | Backed by | What it emits |
|---|---|---|
| `DownloadQueue.tasks` | `StateFlow<List<DownloadTask>>` | All tasks (the source of truth) |
| `DownloadManager.activeDownloads` | `tasks.map { it.filter { isInQueue } }` | QUEUED + DOWNLOADING + PAUSED + ERROR |
| `DownloadManager.completedDownloads` | `tasks.map { it.filter { status == COMPLETED } }` | COMPLETED only |
| `DownloadManager.allDownloads` | `tasks` (alias) | All |
| `DownloadManager.episodeDownloadStates` | `tasks.map { it.associateBy { it.key } }` | `Map<String, DownloadTask>` keyed by `"$contentId|$episodeNumber"` |
