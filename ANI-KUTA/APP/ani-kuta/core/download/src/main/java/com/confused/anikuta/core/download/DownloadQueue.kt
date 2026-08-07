package com.confused.anikuta.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * The download queue — a SQLDelight-backed, Mutex-protected queue of [DownloadTask]s.
 *
 * D.1.7 + 02-queue-management.md §13: the queue is the source of truth for the UI.
 * The DB (`download_queue` table) is the source of truth for restart recovery.
 *
 * REVIEW-5 fixes implemented here:
 *  - M11: [setRetryingStatus] + [setErrorStatus] are private methods (the OLD draft
 *    called them from the retry loop in `16-quality-of-life.md` §1.2 but never
 *    defined them).
 *  - M31: `recentRatios: ArrayDeque<Float>` is maintained per-task + passed to
 *    [DynamicProgressTracker.compute].
 *  - M34: per-tick DB writes go through a [Channel] consumed by a single coroutine
 *    (was: `scope.launch { mutex.withLock { … } }` per tick — 12,500+ pending
 *    coroutines per task × 5 concurrent = 60,000+ pending coroutines).
 *  - M38: `prevTotal` / `prevEstimate` / `recentRatios` are persisted to the DB
 *    on pause + restored on resume (the bar doesn't jump backward on resume).
 *  - M41: [mutateTask] is `suspend fun` (acquires the mutex internally);
 *    [mutateTaskLocked] assumes the mutex is held.
 *  - M42: [onNetworkChanged] uses [pauseInternal] (assumes the mutex is held) —
 *    no deadlock.
 *  - M43: [scheduleAutoClear]'s `autoClearScheduled.add` is wrapped in
 *    `mutex.withLock` (the OLD draft had it outside — race on the MutableSet).
 *  - M36: uses [DynamicProgressTracker.complete] to flip 99→100 on COMPLETED.
 *
 * Concurrency: a [Semaphore] limits concurrent downloads to [DownloadPreferences.concurrentDownloads]
 * (1..5). The semaphore is rebuilt when the pref changes (reactive — the OLD project's
 * bug here is fixed).
 *
 * The queue is `close()`d by the [DefaultDownloadManager] on app shutdown (cancels
 * the scope + joins all jobs).
 */
class DownloadQueue(
    private val store: DownloadStore,
    private val preferences: DownloadPreferences,
    private val scope: CoroutineScope,
    private val downloader: HttpDownloader,
    private val notifier: DownloadNotificationManager,
    private val connectivityCheck: () -> Boolean = { true },
    private val onTaskCompleted: (suspend (DownloadTask) -> Unit)? = null,
    private val onTaskError: (suspend (DownloadTask) -> Unit)? = null,
    private val mutex: Mutex = Mutex(),
) {

    // ── The in-memory cache (source of truth for the UI) ─────────────────────

    private val _tasks: MutableStateFlow<List<DownloadTask>> = MutableStateFlow(loadInitialTasks())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // ── Concurrency ──────────────────────────────────────────────────────────

    @Volatile
    private var permits: Semaphore = Semaphore(currentConcurrentLimit())

    @Volatile
    private var currentLimit: Int = currentConcurrentLimit()

    // ── Per-task coroutine tracking ──────────────────────────────────────────

    private val jobs = mutableMapOf<Long, Job>()

    /** REVIEW-5 M43: guards the 10s auto-clear (prevents the leak per §A.11 fix #12). */
    private val autoClearScheduled = mutableSetOf<Long>()

    init {
        // Reset stale DOWNLOADING + RETRYING tasks to QUEUED (REVIEW-5 M6).
        store.resetDownloadingToQueued()
        _tasks.value = loadInitialTasks()

        // Reactive concurrency — refresh the semaphore when the pref changes.
        scope.launch {
            preferences.concurrentDownloads.changes.collect {
                refreshConcurrency()
            }
        }

        // Reactive Wi-Fi-only — re-evaluate when the pref changes.
        scope.launch {
            preferences.wifiOnly.changes.collect {
                tryStartNext()
            }
        }
    }

    // ── Public operations ────────────────────────────────────────────────────

    /**
     * Enqueues a new download request. Returns the task ID.
     *
     * Dedup behavior: if a task with the same (mainId, episodeKey) exists:
     *  - COMPLETED → returns its ID, no re-download.
     *  - QUEUED / DOWNLOADING / PAUSED → returns its ID, no-op.
     *  - ERROR → calls [resume] (re-queues the errored task).
     */
    suspend fun enqueue(request: DownloadRequest): Long = mutex.withLock {
        DownloadLogger.i { "enqueue — mainId=${request.content.mainId}, episodeKey=${request.episode.episodeKey}, videoUrl=${request.videoUrl.take(80)}" }
        val existing = store.getTaskByMainAndEpisode(request.content.mainId, request.episode.episodeKey)
        if (existing != null) {
            DownloadLogger.i { "enqueue — existing task found: id=${existing.id}, status=${existing.status}" }
            if (existing.status == DownloadStatus.ERROR) {
                // Re-queue an errored task — release the lock first to avoid deadlock.
                val id = existing.id
                scope.launch { resume(id) }
            }
            return@withLock existing.id
        }
        val id = store.insertTask(request)
        DownloadLogger.i { "enqueue — inserted task id=$id" }
        val task = store.getTaskByMainAndEpisode(request.content.mainId, request.episode.episodeKey)
        if (task != null) {
            _tasks.value = _tasks.value + task
            DownloadLogger.i { "enqueue — task added to _tasks, size=${_tasks.value.size}" }
        } else {
            DownloadLogger.w { "enqueue — task NOT found after insert!" }
        }
        id
    }.also {
        // Outside the lock — tryStartNext re-acquires the mutex itself.
        DownloadLogger.i { "enqueue — calling tryStartNext" }
        tryStartNext()
    }

    /** Pauses a task (cancels the in-flight download). */
    suspend fun pause(taskId: Long) = mutex.withLock {
        pauseInternal(taskId)
    }.also {
        tryStartNext()
    }

    /** Resumes a PAUSED or ERROR task. */
    suspend fun resume(taskId: Long) = mutex.withLock {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return@withLock
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.ERROR) return@withLock
        mutateTaskLocked(taskId) {
            it.copy(status = DownloadStatus.QUEUED, lastError = null)
        }
        store.updateState(
            id = taskId,
            status = DownloadStatus.QUEUED,
            progress = task.progress,
            startedAt = null,
            completedAt = null,
            errorMessage = null,
        )
    }.also {
        tryStartNext()
    }

    /** Cancels a task (removes it from the queue entirely). */
    suspend fun cancel(taskId: Long) = mutex.withLock {
        jobs.remove(taskId)?.cancel()
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        store.deleteTask(taskId)
    }.also {
        tryStartNext()
    }

    /** Retries an ERROR task (resets progress + re-queues). */
    suspend fun retry(taskId: Long) = mutex.withLock {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return@withLock
        if (task.status != DownloadStatus.ERROR) return@withLock
        mutateTaskLocked(taskId) {
            it.copy(
                status = DownloadStatus.QUEUED,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = -1L,
                lastError = null,
                retryAttempt = 0,
            )
        }
        store.updateState(
            id = taskId,
            status = DownloadStatus.QUEUED,
            progress = 0,
            startedAt = null,
            completedAt = null,
            errorMessage = null,
        )
    }.also {
        tryStartNext()
    }

    /** Pauses all active (DOWNLOADING + RETRYING) tasks. */
    suspend fun pauseAll() = mutex.withLock {
        _tasks.value
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING }
            .forEach { pauseInternal(it.id) }
    }

    /** Cancels all tasks (clears the queue). */
    suspend fun cancelAll() = mutex.withLock {
        for (task in _tasks.value) {
            jobs.remove(task.id)?.cancel()
            store.deleteTask(task.id)
        }
        _tasks.value = emptyList()
    }

    /** Resumes all PAUSED + ERROR tasks. */
    suspend fun resumeAll() = mutex.withLock {
        _tasks.value
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.ERROR }
            .forEach { task ->
                mutateTaskLocked(task.id) {
                    it.copy(status = DownloadStatus.QUEUED, lastError = null)
                }
                store.updateState(
                    id = task.id,
                    status = DownloadStatus.QUEUED,
                    progress = task.progress,
                    startedAt = null,
                    completedAt = null,
                    errorMessage = null,
                )
            }
    }.also {
        tryStartNext()
    }

    /** Removes a COMPLETED task from the queue (the file stays on disk). */
    suspend fun removeCompleted(taskId: Long) = mutex.withLock {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        store.deleteTask(taskId)
    }

    // ── Reactive concurrency + network ───────────────────────────────────────

    /** Refreshes the concurrency semaphore when the pref changes. */
    fun refreshConcurrency() {
        val newLimit = currentConcurrentLimit()
        if (newLimit != currentLimit) {
            currentLimit = newLimit
            permits = Semaphore(newLimit)
            tryStartNext()
        }
    }

    /**
     * Called by NetworkCallback (the auto-pause/resume QoL feature).
     *
     * REVIEW-5 M42: uses [pauseInternal] (assumes the mutex is held) — no deadlock
     * (the OLD draft's `mutex.withLock { pause(it.id) }` where `pause` ALSO acquires
     * the mutex = non-reentrant Mutex deadlock).
     */
    fun onNetworkChanged(isWifi: Boolean, hasInternet: Boolean) {
        scope.launch {
            mutex.withLock {
                if (!hasInternet || (preferences.wifiOnly.get() && !isWifi)) {
                    // Pause all DOWNLOADING + RETRYING tasks.
                    _tasks.value
                        .filter {
                            it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.RETRYING
                        }
                        .forEach { pauseInternal(it.id) }
                }
            }
            // Outside the lock — tryStartNext re-acquires the mutex itself.
            if (hasInternet && (!preferences.wifiOnly.get() || isWifi)) {
                tryStartNext()
            }
        }
    }

    // ── Internal pause (M42 — assumes the mutex is held) ─────────────────────

    /**
     * REVIEW-5 M42: internal pause that ASSUMES the mutex is held. Used by
     * [onNetworkChanged] (which already holds the mutex) + by the public [pause]
     * (which acquires the mutex then delegates here).
     */
    private fun pauseInternal(taskId: Long) {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.status != DownloadStatus.DOWNLOADING &&
            current.status != DownloadStatus.QUEUED &&
            current.status != DownloadStatus.RETRYING
        ) return
        mutateTaskLocked(taskId) {
            it.copy(status = DownloadStatus.PAUSED)
        }
        store.updateState(
            id = taskId,
            status = DownloadStatus.PAUSED,
            progress = current.progress,
            startedAt = current.startedAt,
            completedAt = null,
            errorMessage = null,
        )
        jobs[taskId]?.cancel()
    }

    // ── The scheduler ────────────────────────────────────────────────────────

    /**
     * The scheduler — picks the next QUEUED task(s) + launches their download
     * coroutines (up to the concurrency limit).
     */
    fun tryStartNext() {
        scope.launch {
            mutex.withLock {
                if (!connectivityCheck()) {
                    DownloadLogger.d { "tryStartNext — connectivity check failed (Wi-Fi-only?), skipping" }
                    return@withLock
                }
                val queuedTasks = _tasks.value.filter { it.status == DownloadStatus.QUEUED }
                val activeCount = _tasks.value.count {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RETRYING
                }
                val slotsAvailable = (currentLimit - activeCount).coerceAtLeast(0)
                val toStart = queuedTasks.take(slotsAvailable)
                DownloadLogger.i { "tryStartNext — queued=${queuedTasks.size}, active=$activeCount, limit=$currentLimit, slots=$slotsAvailable, toStart=${toStart.size}" }

                for (task in toStart) {
                    if (jobs.containsKey(task.id)) {
                        DownloadLogger.d { "tryStartNext — skipping task ${task.id} (already has a job)" }
                        continue
                    }
                    DownloadLogger.i { "tryStartNext — launching download for task ${task.id}" }
                    launchDownload(task)
                }
            }
        }
    }

    // ── The per-task download coroutine ──────────────────────────────────────

    /**
     * Launches the download coroutine for [task].
     *
     * REVIEW-5 M31/M34/M36/M38 fixes are all wired in here (see the inline comments).
     */
    private fun launchDownload(task: DownloadTask) {
        DownloadLogger.i { "launchDownload — START task ${task.id}, videoUrl=${task.videoUrl.take(80)}" }
        val job = scope.launch {
            try {
                permits.withPermit {
                    DownloadLogger.i { "launchDownload — permit acquired for task ${task.id}" }
                    mutex.withLock {
                        val current = _tasks.value.firstOrNull { it.id == task.id }
                        if (current?.status != DownloadStatus.QUEUED) {
                            DownloadLogger.w { "launchDownload — task ${task.id} status is ${current?.status}, not QUEUED — aborting" }
                            return@withLock
                        }
                        mutateTaskLocked(task.id) {
                            it.copy(
                                status = DownloadStatus.DOWNLOADING,
                                startedAt = now(),
                            )
                        }
                        store.updateState(
                            id = task.id,
                            status = DownloadStatus.DOWNLOADING,
                            progress = task.progress,
                            startedAt = now(),
                            completedAt = null,
                            errorMessage = null,
                        )
                        DownloadLogger.i { "launchDownload — task ${task.id} set to DOWNLOADING" }
                    }

                    // ── REVIEW-5 M31 + M38: per-task tracker state. ──
                    //   - recentRatios: the moving-average window (max 5 entries).
                    //   - prevTotal / prevEstimate: the no-backward-jump + smoothing state.
                    //   On PAUSE: persisted to download_queue (via store.updateProgress).
                    //   On RESUME: restored from the DB via store.getTrackerState.
                    val persisted = store.getTrackerState(task.id)
                    var prevTotal = persisted?.prevTotal?.takeIf { it > 0L } ?: 0L
                    var prevEstimate = persisted?.prevEstimate?.takeIf { it > 0 } ?: 0
                    val recentRatios = ArrayDeque<Float>(DynamicProgressTracker.WINDOW_SIZE).apply {
                        persisted?.recentRatios?.let { addAll(it) }
                    }

                    // ── REVIEW-5 M34: single-coroutine DB writer (consumes the channel). ──
                    val progressChannel = Channel<ProgressUpdate>(Channel.CONFLATED)
                    val dbWriter = scope.launch {
                        var lastFlush = 0L
                        progressChannel.consumeEach { upd ->
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastFlush >= PERSIST_INTERVAL_MS) {
                                store.updateProgress(
                                    id = task.id,
                                    progress = upd.progress,
                                    downloadedBytes = upd.downloadedBytes,
                                    totalBytes = upd.totalBytes,
                                    prevTotal = prevTotal,
                                    prevEstimate = prevEstimate.toLong(),
                                    recentRatios = recentRatios.toList(),
                                )
                                lastFlush = nowMs
                            }
                        }
                    }

                    DownloadLogger.i { "launchDownload — calling downloader.download for task ${task.id}" }
                    val completed = downloader.download(task) { downloaded, total ->
                        DownloadLogger.d { "launchDownload — progress: task=${task.id}, downloaded=$downloaded, total=$total" }
                        // REVIEW-5 M31: maintain the moving-average window in place.
                        val currentRatio = if (total > 0) (downloaded.toFloat() / total) else 0f
                        recentRatios.addLast(currentRatio)
                        while (recentRatios.size > DynamicProgressTracker.WINDOW_SIZE) {
                            recentRatios.removeFirst()
                        }

                        val progress = DynamicProgressTracker.compute(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            prevTotal = prevTotal,
                            prevEstimate = prevEstimate,
                            recentRatios = recentRatios,
                        )
                        prevTotal = if (total > 0) maxOf(total, prevTotal) else prevTotal
                        prevEstimate = progress

                        // REVIEW-5 M34: INLINE update of _tasks.value (no launch, no mutex —
                        // MutableStateFlow.value = is atomic). The DB write is delegated to
                        // the single dbWriter coroutine via the channel.
                        _tasks.value = _tasks.value.map { t ->
                            if (t.id == task.id) t.copy(
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = if (total > 0) total else t.totalBytes,
                            ) else t
                        }
                        progressChannel.trySend(
                            ProgressUpdate(progress, downloaded, if (total > 0) total else -1L),
                        )
                    }

                    progressChannel.close()
                    dbWriter.join()
                    DownloadLogger.i { "launchDownload — downloader.download COMPLETED for task ${task.id}, videoUri=${completed.videoUri}" }

                    mutex.withLock {
                        // REVIEW-5 M36: use DynamicProgressTracker.complete() to flip 99→100.
                        val finalProgress = DynamicProgressTracker.complete()
                        val completedTask = completed.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = finalProgress,
                            completedAt = now(),
                        )
                        mutateTaskLocked(task.id) { completedTask }
                        store.updateState(
                            id = task.id,
                            status = DownloadStatus.COMPLETED,
                            progress = finalProgress,
                            startedAt = completedTask.startedAt,
                            completedAt = completedTask.completedAt,
                            errorMessage = null,
                        )
                        store.updateResult(task.id, completedTask.videoUri, decodeSubtitleUris(completedTask.subtitleUris))

                        // D.FIX: Insert into downloaded_episode table so isEpisodeDownloaded()
                        // returns true + the episode shows as Downloaded in the details page.
                        // Without this, the auto-clear removes the task from the queue after 10s
                        // and the episode shows as NotDownloaded.
                        if (completedTask.videoUri != null) {
                            val downloadedEp = DownloadedEpisode(
                                content = completedTask.content,
                                episode = completedTask.episode,
                                videoUri = completedTask.videoUri,
                                subtitleUris = decodeSubtitleUris(completedTask.subtitleUris),
                                sizeBytes = 0L, // Best-effort — the actual file size is on disk.
                                quality = completedTask.videoQuality.ifBlank { null },
                                completedAt = completedTask.completedAt ?: now(),
                            )
                            store.insertDownloadedEpisode(downloadedEp)
                            DownloadLogger.i { "launchDownload — inserted into downloaded_episode table for task ${task.id}" }
                        }

                        scheduleAutoClear(task.id)
                    }
                    onTaskCompleted?.invoke(completed)
                }
            } catch (e: CancellationException) {
                // REVIEW-5 M37: pause preserves resume metadata. The status was already
                // set to PAUSED by pauseInternal before cancel() returned — nothing to do.
                DownloadLogger.d { "launchDownload — Job cancelled: id=${task.id}" }
            } catch (e: DownloadException) {
                DownloadLogger.e(e) { "launchDownload — DownloadException for task ${task.id}: ${e.message}" }
                setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
                _tasks.value.firstOrNull { it.id == task.id }?.let { onTaskError?.invoke(it) }
            } catch (e: Exception) {
                DownloadLogger.e(e) { "launchDownload — Exception for task ${task.id}: ${e.message}" }
                setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
                _tasks.value.firstOrNull { it.id == task.id }?.let { onTaskError?.invoke(it) }
            } finally {
                jobs.remove(task.id)
                tryStartNext()
            }
        }
        jobs[task.id] = job
    }

    // ── REVIEW-5 M11: status setters ─────────────────────────────────────────

    /** REVIEW-5 M11: sets RETRYING status + retry metadata. */
    private suspend fun setRetryingStatus(
        taskId: Long,
        attempt: Int,
        maxAttempts: Int,
        lastError: String,
    ) = mutex.withLock {
        mutateTaskLocked(taskId) {
            it.copy(
                status = DownloadStatus.RETRYING,
                retryAttempt = attempt,
                retryMaxAttempts = maxAttempts,
                lastError = lastError,
            )
        }
        store.setRetryingStatus(taskId, attempt, maxAttempts, lastError)
    }

    /** REVIEW-5 M11: sets ERROR status + error message. */
    private suspend fun setErrorStatus(taskId: Long, message: String) = mutex.withLock {
        mutateTaskLocked(taskId) {
            it.copy(status = DownloadStatus.ERROR, lastError = message)
        }
        store.setErrorStatus(taskId, message)
    }

    // ── REVIEW-5 M41: mutateTask (acquires mutex) + mutateTaskLocked (assumes held) ──

    /** REVIEW-5 M41: acquires the mutex internally + applies the mutation. */
    private suspend fun mutateTask(taskId: Long, mutation: (DownloadTask) -> DownloadTask) {
        mutex.withLock {
            mutateTaskLocked(taskId, mutation)
        }
    }

    /** REVIEW-5 M41: variant that ASSUMES the caller already holds the mutex. */
    private fun mutateTaskLocked(taskId: Long, mutation: (DownloadTask) -> DownloadTask) {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        val updated = mutation(current)
        _tasks.value = _tasks.value.map { if (it.id == taskId) updated else it }
    }

    /** REVIEW-5 M43: scheduleAutoClear's add is wrapped in mutex.withLock. */
    private fun scheduleAutoClear(taskId: Long) {
        scope.launch {
            mutex.withLock {
                if (taskId in autoClearScheduled) return@withLock
                autoClearScheduled.add(taskId)
            }
            delay(AUTO_CLEAR_DELAY_MS)
            mutex.withLock {
                autoClearScheduled.remove(taskId)
                _tasks.value = _tasks.value.filterNot { it.id == taskId }
                store.deleteTask(taskId)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun loadInitialTasks(): List<DownloadTask> {
        store.purgeCancelled() // defensive — removes any CANCELLED rows left from a crash
        return store.getActiveTasks()
    }

    private fun currentConcurrentLimit(): Int =
        preferences.concurrentDownloads.get().coerceIn(1, 5)

    private fun now(): Long = System.currentTimeMillis()

    /** Parses the JSON-encoded subtitle URIs (best-effort — empty list on failure). */
    private fun decodeSubtitleUris(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * The progress update pushed through the [progressChannel] (REVIEW-5 M34).
     *
     * @param progress The smoothed progress (0..95 during download).
     * @param downloadedBytes The current downloaded bytes.
     * @param totalBytes The current total bytes (-1 if unknown).
     */
    private data class ProgressUpdate(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    companion object {
        /** DB-write throttle (1/sec — avoids hammering SQLite during a big file). */
        private const val PERSIST_INTERVAL_MS = 1000L

        /** The auto-clear delay for COMPLETED tasks (10 sec — per the OLD project's behavior). */
        private const val AUTO_CLEAR_DELAY_MS = 10_000L
    }
}
