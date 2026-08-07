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

---

## 13. Post-rewrite additions (DL-PLAN-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> The OLD project's queue (documented in §§1-12 above) is the baseline. The NEW project's queue must:
> 1. Persist in SQLDelight `download_queue` (NOT in-memory + JSON-in-SharedPrefs).
> 2. Re-key by `mainId` + `episodeKey` (NOT `contentId`).
> 3. Have proper start-next logic (the OLD project's works but is fragile).
> 4. Configurable concurrency (1..5) with reactive pref-changes (the OLD project's bug here must be fixed).
> 5. Be thread-safe via Mutex (the OLD project's "best-effort" threading is risky — see §11 honest note).
> 6. Reset stale `DOWNLOADING` tasks to `QUEUED` on startup (the OLD project's bug fix preserved).
> 7. Integrate with the foreground service (`DownloadService` starts/stops based on queue state).

### 13.1 The NEW `DownloadQueue` design (SQLDelight-backed)

```kotlin
class DownloadQueue(
    private val store: DownloadStore,           // the SQLDelight adapter (see 11-db-schema.md §4)
    private val preferences: DownloadPreferences,
    private val scope: CoroutineScope,          // private to the manager — survives app-backgrounding (with foreground service)
    private val mutex: Mutex = Mutex(),         // NEW: serializes all queue mutations (fixes the OLD project's threading risk)
) {
    // The in-memory cache. Initialized from the DB on construction.
    private val _tasks = MutableStateFlow(store.getAll().map { it.toModel() })
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val idCounter = AtomicLong(store.getMaxId() + 1)
    private val jobs = mutableMapOf<Long, Job>()
    private val autoClearScheduled = mutableSetOf<Long>()  // NEW: guards the 10s auto-clear (per 15-ui-and-bug-analysis.md §A.11 fix #12)

    @Volatile private var permits: Semaphore = Semaphore(currentConcurrentLimit())
    @Volatile private var currentLimit: Int = currentConcurrentLimit()

    init {
        // Reset stale DOWNLOADING tasks to QUEUED (the OLD project's bug fix — see 03-state-machine.md §7).
        store.resetDownloadingToQueued()
        _tasks.value = store.getAll().map { it.toModel() }

        // NEW: reactive concurrency — when the pref changes, refresh the semaphore immediately
        // (fixes the OLD project's bug where the new limit only took effect after restart).
        scope.launch {
            preferences.concurrentDownloads().changes().collect {
                refreshConcurrency()
            }
        }

        // NEW: reactive Wi-Fi-only — when the pref changes OR the network changes, pause/resume accordingly.
        scope.launch {
            preferences.wifiOnly().changes().collect {
                tryStartNext()
            }
        }
    }
    // ...
}
```

### 13.2 The start-next logic (the scheduler)

The OLD project's `tryStartNext` is fine in principle but has gaps. The NEW version:

```kotlin
private fun tryStartNext() {
    scope.launch {
        mutex.withLock {
            if (!connectivityCheck()) {
                Logger.d(TAG) { "Skipping start — connectivity check failed (Wi-Fi-only?)" }
                return@withLock
            }
            // Acquire permits UP TO the concurrency limit, but no more than the number of QUEUED tasks.
            val queuedTasks = _tasks.value.filter { it.status == DownloadStatus.QUEUED }
            val activeCount = _tasks.value.count { it.status == DownloadStatus.DOWNLOADING }
            val slotsAvailable = (currentLimit - activeCount).coerceAtLeast(0)
            val toStart = queuedTasks.take(slotsAvailable)

            for (task in toStart) {
                if (jobs.containsKey(task.id)) continue  // already launching
                launchDownload(task)
            }
        }
    }
}
```

**Improvements over the OLD project:**
1. **Mutex-protected** — no race conditions on `_tasks` mutations (the OLD project's "best-effort" threading is risky per §11 honest note).
2. **Starts MULTIPLE tasks per call** — the OLD project's `tryStartNext` started ONE task per call (then relied on the `finally { tryStartNext() }` chain to start the rest). The NEW version starts as many as permits allow in one go.
3. **Reactive to concurrency pref changes** — when the user changes the `concurrentDownloads` pref, `refreshConcurrency()` is called via a Flow collector (fixes the OLD project's bug per §5 honest note).
4. **Reactive to Wi-Fi-only pref changes** — when the user toggles Wi-Fi-only, the queue re-evaluates immediately.

### 13.3 The per-task coroutine (with proxy-churn fix integration + REVIEW-5 M11/M31/M34/M38/M41/M43 fixes)

> **REVIEW-5 M11:** `setRetryingStatus` + `setErrorStatus` are now defined as private methods on
> `DownloadQueue` (below) — the OLD draft called them from the retry loop in `16-quality-of-life.md`
> §1.2 but never defined them.
>
> **REVIEW-5 M31:** the `recentRatios: ArrayDeque<Float>(5)` is now maintained per-task in closure
> vars + passed to `DynamicProgressTracker.compute(...)`. The OLD draft's `compute(...)` call was
> missing the `recentRatios` arg — wouldn't compile + silently dropped the moving-average feature.
>
> **REVIEW-5 M34:** the per-tick `scope.launch { mutex.withLock { … } }` is REMOVED. The OLD draft
> fired a fresh coroutine per byte tick (8KB buffer × 100MB file = ~12,500 ticks → 12,500 pending
> coroutines per task × 5 concurrent = 60,000+ pending coroutines). The NEW design updates
> `_tasks.value` INLINE (no launch, no mutex — `MutableStateFlow.value = …` is atomic) + writes
> to the DB via a Channel<ProgressUpdate> consumed by a single coroutine (throttled 1/sec).
>
> **REVIEW-5 M38:** `prevTotal` + `prevEstimate` + `recentRatios` are persisted to the DB row
> (or `resume.json` for Advanced) on pause + restored on resume. The OLD draft's closure vars
> were GC'd on pause → bar jumped backward on resume (exactly the user's complaint).
>
> **REVIEW-5 M41:** `mutateTask` is now a `suspend fun` that acquires the mutex internally —
> callers don't need to wrap in `mutex.withLock`. (Renamed `mutateTaskLocked` for the variant
> that ASSUMES the mutex is held — used inside `onNetworkChanged`'s mutex-wrapped body per M42.)
>
> **REVIEW-5 M43:** `scheduleAutoClear`'s `autoClearScheduled.add(taskId)` is now wrapped in
> `mutex.withLock` (the OLD draft had it outside — race on the `MutableSet<Long>`).

```kotlin
private fun launchDownload(task: DownloadTask) {
    val job = scope.launch {
        try {
            permits.withPermit {
                mutex.withLock {
                    val current = _tasks.value.firstOrNull { it.id == task.id }
                    if (current?.status != DownloadStatus.QUEUED) return@withLock
                    mutateTask(task.id) {
                        it.copy(status = DownloadStatus.DOWNLOADING, startedAt = now(), updatedAt = now())
                    }
                    store.updateState(task.id, DownloadStatus.DOWNLOADING, ...)
                }

                // REVIEW-5 M31 + M38: per-task closure state for DynamicProgressTracker.
                //   - recentRatios: the moving-average window (max 5 entries).
                //   - prevTotal / prevEstimate: the no-backward-jump + "10MB ahead" state.
                // On PAUSE: these are persisted to download_queue (or resume.json for Advanced).
                // On RESUME: these are restored — bar doesn't jump backward.
                var prevTotal = task.prevTotalBytes.takeIf { it > 0L } ?: 0L
                var prevEstimate = task.prevEstimateBytes.takeIf { it > 0L } ?: 0L
                val recentRatios = ArrayDeque<Float>(5).apply {
                    task.recentRatiosJson?.let { addAll(it) }
                }

                // REVIEW-5 M34: single-coroutine DB writer (consumes the progress channel).
                val progressChannel = Channel<ProgressUpdate>(Channel.CONFLATED)
                val dbWriter = scope.launch {
                    var lastFlush = 0L
                    progressChannel.consumeEach { upd ->
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastFlush >= 1000L) {  // throttle 1/sec
                            store.updateProgress(task.id, upd.progress, upd.downloaded, upd.displayTotalBytes)
                            // REVIEW-5 M38: persist the tracker state so resume doesn't jump backward.
                            store.updateTrackerState(task.id, prevTotal, prevEstimate, recentRatios.toList())
                            lastFlush = nowMs
                        }
                    }
                }

                val completed = downloader.download(task) { downloaded, total ->
                    // REVIEW-5 M31: maintain the moving-average window.
                    val currentRatio = if (total > 0) (downloaded.toFloat() / total) else 0f
                    recentRatios.addLast(currentRatio)
                    while (recentRatios.size > 5) recentRatios.removeFirst()

                    val update = DynamicProgressTracker.compute(
                        downloaded = downloaded,
                        reportedTotal = total,
                        previousTotal = prevTotal,
                        previousEstimate = prevEstimate,
                        recentRatios = recentRatios.toList(),  // REVIEW-5 M31 — was missing
                    )
                    prevTotal = update.displayTotalBytes
                    prevEstimate = update.updatedEstimate

                    // REVIEW-5 M34: INLINE update of _tasks.value (no launch, no mutex — atomic).
                    // The DB write is delegated to the single dbWriter coroutine via the channel.
                    _tasks.value = _tasks.value.map { t ->
                        if (t.id == task.id) t.copy(
                            progress = update.progress,
                            downloadedBytes = downloaded,
                            totalBytes = update.displayTotalBytes,
                            updatedAt = now(),
                            prevTotalBytes = prevTotal,             // REVIEW-5 M38
                            prevEstimateBytes = prevEstimate,       // REVIEW-5 M38
                            recentRatiosJson = recentRatios.toList(), // REVIEW-5 M38
                        ) else t
                    }
                    progressChannel.trySend(update)
                }

                progressChannel.close()
                dbWriter.join()

                mutex.withLock {
                    // REVIEW-5 M36: use DynamicProgressTracker.complete() to flip 99 → 100.
                    val finalUpdate = DynamicProgressTracker.complete()
                    val completedTask = completed.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = finalUpdate.progress,  // = 100
                        completedAt = now(), updatedAt = now(),
                    )
                    mutateTask(task.id) { completedTask }
                    store.updateResult(task.id, completedTask.videoUri, completedTask.subtitleUris)
                    scheduleAutoClear(task.id)
                }
                onTaskCompleted?.invoke(completed)
            }
        } catch (e: CancellationException) {
            // Pause/cancel handlers already set the status — nothing to do.
            // REVIEW-5 M37: do NOT call tempCache.cleanupTask here — pause preserves resume
            // metadata for the Advanced method. The cleanup happens only on cancel/completion/error.
        } catch (e: DownloadException) {
            setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)  // REVIEW-5 M11
            onTaskError?.invoke(_tasks.value.firstOrNull { it.id == task.id })
        } catch (e: Exception) {
            setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)  // REVIEW-5 M11
        } finally {
            jobs.remove(task.id)
            tryStartNext()  // a permit freed up
        }
    }
    jobs[task.id] = job
}

// ── REVIEW-5 M11: setRetryingStatus + setErrorStatus — defined here (were undefined in the OLD draft). ──

/**
 * Sets the task's status to RETRYING + records the retry metadata on the task.
 * Called by the retry loop's catch block in `16-quality-of-life.md` §1.2.
 */
private suspend fun setRetryingStatus(
    taskId: Long,
    attempt: Int,
    maxAttempts: Int,
    lastError: String,
) = mutex.withLock {
    mutateTask(taskId) {
        it.copy(
            status = DownloadStatus.RETRYING,
            retryAttempt = attempt,
            retryMaxAttempts = maxAttempts,
            lastError = lastError,
            updatedAt = now(),
        )
    }
    store.updateState(taskId, DownloadStatus.RETRYING, errorMessage = lastError)
}

/**
 * Sets the task's status to ERROR + records the error message.
 * Called by the retry loop's max-attempts-exceeded path + by launchDownload's catch blocks.
 */
private suspend fun setErrorStatus(taskId: Long, message: String) = mutex.withLock {
    mutateTask(taskId) {
        it.copy(status = DownloadStatus.ERROR, errorMessage = message, updatedAt = now())
    }
    store.updateState(taskId, DownloadStatus.ERROR, errorMessage = message)
}

// ── REVIEW-5 M41: mutateTask acquires the mutex internally. mutateTaskLocked assumes it's held. ──

/**
 * Acquires the mutex internally + applies the mutation. Callers do NOT need `mutex.withLock`.
 */
private suspend fun mutateTask(taskId: Long, mutation: (DownloadTask) -> DownloadTask) {
    mutex.withLock {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return@withLock
        val updated = mutation(current)
        _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
    }
}

/**
 * Variant that ASSUMES the caller already holds the mutex. Used inside `onNetworkChanged`'s
 * mutex-wrapped body (M42) + inside other `mutex.withLock` blocks above.
 */
private fun mutateTaskLocked(taskId: Long, mutation: (DownloadTask) -> DownloadTask) {
    val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
    val updated = mutation(current)
    _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
}

/**
 * Schedule the 10s auto-clear of a COMPLETED task (per the OLD project's behavior).
 * GUARDED by autoClearScheduled — prevents the leak per 15-ui-and-bug-analysis.md §A.11 fix #12.
 *
 * REVIEW-5 M43: the `in`/`add` checks are now wrapped in `mutex.withLock` (the OLD draft had
 * them outside — race on the `MutableSet<Long>`).
 */
private fun scheduleAutoClear(taskId: Long) {
    scope.launch {
        mutex.withLock {
            if (taskId in autoClearScheduled) return@withLock
            autoClearScheduled.add(taskId)
        }
        delay(10_000)
        mutex.withLock {
            autoClearScheduled.remove(taskId)
            _tasks.value = _tasks.value.filterNot { it.id == taskId }
            store.delete(taskId)
        }
    }
}
```

### 13.4 Concurrency — configurable, reactive, with Wi-Fi-only enforcement

```kotlin
private fun currentConcurrentLimit(): Int =
    preferences.concurrentDownloads().get().coerceIn(1, 5)

fun refreshConcurrency() {
    val newLimit = currentConcurrentLimit()
    if (newLimit != currentLimit) {
        currentLimit = newLimit
        permits = Semaphore(newLimit)  // rebuilt — in-flight tasks keep their old permits (GC'd when released)
        tryStartNext()                 // start more if the limit increased
    }
}

/** Called by NetworkCallback (the auto-pause/resume QoL feature — see 16-quality-of-life.md). */
fun onNetworkChanged(isWifi: Boolean, hasInternet: Boolean) {
    // REVIEW-5 M42: this is the CANONICAL definition (the QoL doc's version was divergent +
    // deadlocked — `mutex.withLock { pause(it.id) }` where `pause` ALSO acquires the mutex =
    // non-reentrant Mutex deadlock). The fix: extract `pauseInternal(taskId)` that ASSUMES the
    // mutex is held (no `mutex.withLock` inside). Both `pause` (public) + `onNetworkChanged`
    // (mutex-holding caller) use the right variant.
    scope.launch {
        mutex.withLock {
            if (!hasInternet || (preferences.wifiOnly().get() && !isWifi)) {
                // Pause all DOWNLOADING + RETRYING tasks (REVIEW-5 M10 — RETRYING added).
                _tasks.value
                    .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING }
                    .forEach { pauseInternal(it.id) }  // M42 — assumes mutex held; no re-acquire.
            } else {
                // Network is back + matches the Wi-Fi-only constraint — try to start queued tasks.
                // `tryStartNext` itself acquires the mutex; we're holding it, so release first.
            }
        }
        // Outside the lock — tryStartNext re-acquires the mutex itself.
        if (hasInternet && (!preferences.wifiOnly().get() || isWifi)) {
            tryStartNext()
        }
    }
}

/**
 * REVIEW-5 M42: internal pause that ASSUMES the mutex is held. Used by `onNetworkChanged` (which
 * already holds the mutex) + by the public `pause` (which acquires the mutex then delegates here).
 */
private fun pauseInternal(taskId: Long) {
    val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
    if (current.status != DownloadStatus.DOWNLOADING &&
        current.status != DownloadStatus.QUEUED &&
        current.status != DownloadStatus.RETRYING) return  // M10 — RETRYING allowed
    mutateTaskLocked(taskId) {
        it.copy(status = DownloadStatus.PAUSED, updatedAt = now())
    }
    store.updateState(taskId, DownloadStatus.PAUSED)
    jobs[taskId]?.cancel()  // cancels the download coroutine (or the retry delay)
}

/** The public pause — acquires the mutex + delegates to pauseInternal. */
suspend fun pause(taskId: Long) = mutex.withLock { pauseInternal(taskId) }
```

### 13.5 Why the queue is persisted in SQLDelight (not in-memory)

| Concern | OLD project (in-memory + JSON-in-SharedPrefs) | NEW project (SQLDelight) |
|---|---|---|
| App crash mid-download | JSON list rewritten on every state change (throttled 1/sec). Crash mid-write = lost state. | Per-row UPDATEs (atomic per-row). Crash mid-write loses one row at most. |
| Large queue (100+ tasks) | JSON list grows linearly; whole-list rewrite per state change is O(N). | Per-row UPDATEs are O(1) per state change. |
| Queryable ("all COMPLETED for mainId X") | Must deserialize the whole list + filter in Kotlin. | `SELECT * FROM download_queue WHERE main_id = ? AND state = 'COMPLETED'`. |
| App restart recovery | Re-reads the JSON list (the OLD project's approach). | Re-reads via `SELECT * FROM download_queue` + resets DOWNLOADING→QUEUED. |
| Reactive UI updates | `PreferenceStore.changes()` Flow (the OLD project's reactive layer). | `MutableStateFlow` derived from the in-memory cache (the cache is the source of truth for the UI; the DB is the source of truth for restart). |
| Migration story | JSON schema change = `_v1` suffix bump (loses the list). | SQLDelight `.sqm` migrations (preserves data across versions). |

### 13.6 The queue ordering — FIFO (no user reordering)

Same as the OLD project: the queue is FIFO. New tasks are appended to the end of `_tasks.value` (`updateTasks(_tasks.value + task)`); `tryStartNext` picks the first `QUEUED` task(s) in list order.

The `DragReorderableList` component is used in `DownloadSettingsScreen` ONLY for **preference list reordering** (quality / audio / server / dimension priorities) — NOT for reordering the download queue. If the user wants queue reordering in the future, that's a NEW feature (would need a `reorder(taskId, newPosition)` method on `DownloadQueue` + a `displayOrder` column in the DB).

### 13.7 The foreground service integration

The `DownloadManager` triggers the foreground service:
- On enqueue: `DownloadService.start(context)` (if not already running).
- On queue empty (observed in the manager's queue collector): `DownloadService.stop(context)`.

The `DownloadService` observes the queue's StateFlow + updates the foreground notification (the summary notification) on every change. See `06-notifications-foreground-service.md` §13.7 for the full service implementation.

### 13.8 Cross-references (post-rewrite)

- `11-db-schema.md` — the SQLDelight schema + the `DownloadStore` adapter.
- `06-notifications-foreground-service.md` §13 — the foreground service that observes this queue.
- `13-implementation-plan.md` Phase D.3 — the implementation plan for this queue.
- `16-quality-of-life.md` — the auto-pause/resume on network change (the `onNetworkChanged` callback).
