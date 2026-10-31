package com.confused.anikuta.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    // D-151-fix: the outer retry policy. Caps total download attempts at
    // retryPolicy.maxAttempts (default 3). See [RetryPolicy] for retryable
    // exception classification + backoff.
    private val retryPolicy: RetryPolicy = RetryPolicy(),
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

    // ROUND 123 (R7): consecutive DNS-failure waits while the connectivity
    // check claims "online" — the bounded escape hatch that keeps a bad
    // hardcoded host from waiting forever. Reset on any successful download
    // and on retry(). Guarded by the queue mutex (touched only in its paths).
    private val consecutiveDnsWaits = mutableMapOf<Long, Int>()

    /** REVIEW-5 M43: guards the 10s auto-clear (prevents the leak per §A.11 fix #12). */
    private val autoClearScheduled = mutableSetOf<Long>()

    /**
     * D-246 → ROUND 121: tasks auto-paused by network loss used to live in an
     * in-memory marker set next to a PAUSED status. The device round showed the
     * cracks (resuming while offline raced the set; the set was invisible to the
     * UI; it died with the process). The marker set is GONE — the task's own
     * status ([DownloadStatus.WAITING_FOR_NETWORK]) IS the marker now: it
     * survives process death, the UI can label it, and there is no second
     * source of truth to drift.
     */

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

    /** Pauses a task (cancels the in-flight download). A user-initiated pause
     * of a WAITING_FOR_NETWORK task converts it to PAUSED — their intent wins
     * over the network-regain auto-resume (status-as-marker: no set to clean). */
    suspend fun pause(taskId: Long) = mutex.withLock {
        pauseInternal(taskId)
    }.also {
        tryStartNext()
    }

    /**
     * Resumes a PAUSED / ERROR / WAITING_FOR_NETWORK task. ROUND 121: when the
     * network is disallowed at the moment of the resume, the task goes to
     * WAITING_FOR_NETWORK — not QUEUED-with-nothing-to-start-it, and never an
     * ERROR. It auto-starts the moment connectivity returns (the device-round
     * order: "when the user resumes them [offline], they will be resumed into
     * waiting states").
     */
    suspend fun resume(taskId: Long) = mutex.withLock {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return@withLock
        if (task.status != DownloadStatus.PAUSED &&
            task.status != DownloadStatus.ERROR &&
            task.status != DownloadStatus.WAITING_FOR_NETWORK
        ) {
            return@withLock
        }
        val networkAllowed = runCatching { connectivityCheck() }.getOrDefault(true)
        val target = if (networkAllowed) DownloadStatus.QUEUED else DownloadStatus.WAITING_FOR_NETWORK
        if (!networkAllowed) {
            DownloadLogger.i {
                "resume — task $taskId resumed while offline: WAITING_FOR_NETWORK " +
                    "(auto-starts when connectivity returns)"
            }
        }
        mutateTaskLocked(taskId) {
            it.copy(status = target, lastError = null)
        }
        store.updateState(
            id = taskId,
            status = target,
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
        // ROUND 123 (R7): retry means restart-from-scratch — discard any
        // partial temp preserved by a transport failure, so the downloader
        // doesn't Range-resume a file the user explicitly reset (the display
        // would say 0% while the file grew from 47%).
        consecutiveDnsWaits.remove(taskId)
        downloader.discardPartial(taskId)
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
        // D-246: clear the PERSISTED progress + tracker state too. The old code only
        // reset the in-memory task — the restored prevTotal/prevEstimate/recentRatios
        // made the restarted bar jump to a stale estimate (and downloaded-bytes could
        // exceed the fresh total). retry() means restart-from-scratch: zero everything.
        store.updateProgress(
            id = taskId,
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = -1L,
            prevTotal = 0L,
            prevEstimate = 0L,
            recentRatios = emptyList(),
        )
    }.also {
        tryStartNext()
    }

    /** Pauses all active + WAITING tasks (a user pause-all converts waiting
     * tasks to PAUSED too — their intent wins over the network-regain
     * auto-resume). */
    suspend fun pauseAll() = mutex.withLock {
        _tasks.value
            .filter {
                it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.RETRYING ||
                    it.status == DownloadStatus.WAITING_FOR_NETWORK
            }
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

    /** Resumes all PAUSED + ERROR + WAITING tasks (offline → they re-mark
     * WAITING_FOR_NETWORK, same rule as the single-task [resume]). */
    suspend fun resumeAll() = mutex.withLock {
        val networkAllowed = runCatching { connectivityCheck() }.getOrDefault(true)
        val target = if (networkAllowed) DownloadStatus.QUEUED else DownloadStatus.WAITING_FOR_NETWORK
        _tasks.value
            .filter {
                it.status == DownloadStatus.PAUSED ||
                    it.status == DownloadStatus.ERROR ||
                    it.status == DownloadStatus.WAITING_FOR_NETWORK
            }
            .forEach { task ->
                mutateTaskLocked(task.id) {
                    it.copy(status = target, lastError = null)
                }
                store.updateState(
                    id = task.id,
                    status = target,
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
     * ROUND 121: network lost → active + queued tasks go to
     * WAITING_FOR_NETWORK (NOT PAUSED — the status itself is the marker, so
     * the UI labels them "Waiting for network" and the auto-resume can't
     * lose them). Network restored → every WAITING task goes back to QUEUED
     * and the scheduler starts them.
     */
    fun onNetworkChanged(isWifi: Boolean, hasInternet: Boolean) {
        scope.launch {
            mutex.withLock {
                if (!hasInternet || (preferences.wifiOnly.get() && !isWifi)) {
                    _tasks.value
                        .filter {
                            it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.RETRYING ||
                                it.status == DownloadStatus.QUEUED
                        }
                        .forEach {
                            setWaitingForNetworkLocked(it.id)
                            DownloadLogger.i {
                                "onNetworkChanged — task ${it.id} → WAITING_FOR_NETWORK " +
                                    "(network lost); auto-resumes when connectivity returns"
                            }
                        }
                }
            }
            // Outside the lock — resume() re-acquires the mutex itself.
            if (hasInternet && (!preferences.wifiOnly.get() || isWifi)) {
                resumeNetworkPaused("network restored")
            }
        }
    }

    /**
     * ROUND 122 (R9) + ROUND 123 (R7): moved to FILE LEVEL (see the bottom of
     * this file) so HttpDownloader's cleanup decision shares the exact
     * classifier. The member wrapper keeps the call sites short.
     */
    private fun Throwable.isTransportFailure(): Boolean = isTransportFailureChain(this)

    /**
     * ROUND 121: auto-resumes tasks waiting on the network — discovered BY
     * STATUS (WAITING_FOR_NETWORK), not via a marker set. Covers tasks that
     * went waiting in a previous process too (the status is persisted).
     *
     * ROUND 122 (R9): ALSO heals the false ERRORs — tasks that died with a
     * network-class failure (offline resume before the real connectivity
     * check, a mid-download Wi-Fi blip that exhausted the wrapped-exception
     * path) re-queue on network return via [resume] (which keeps the partial
     * bytes — retry() would zero them). Only network-class errors are touched;
     * a genuine 404/parse failure stays ERROR.
     */
    private suspend fun resumeNetworkPaused(reason: String) {
        val toResume = _tasks.value
            .filter { it.status == DownloadStatus.WAITING_FOR_NETWORK }
            .map { it.id }
            .plus(
                _tasks.value
                    .filter { it.status == DownloadStatus.ERROR && isNetworkClassError(it.lastError) }
                    .map { it.id },
            )
        if (toResume.isEmpty()) {
            tryStartNext()
            return
        }
        DownloadLogger.i { "resumeNetworkPaused — $reason: auto-resuming ${toResume.size} download(s): $toResume" }
        for (id in toResume) {
            runCatching { resume(id) }
                .onFailure { DownloadLogger.e(it) { "resumeNetworkPaused — resume($id) failed" } }
        }
        tryStartNext()
    }

    /**
     * ROUND 121: true when tasks are waiting on the network — the
     * DownloadService stays alive while this is true so its NetworkCallback
     * can fire the auto-resume when connectivity returns (the old code
     * stopped the service once everything was paused, killing the callback →
     * no auto-restart ever).
     */
    fun hasNetworkPausedTasks(): Boolean =
        _tasks.value.any { it.status == DownloadStatus.WAITING_FOR_NETWORK }

    /**
     * ROUND 121: marks a task WAITING_FOR_NETWORK + cancels its job. Called
     * from the error path when a transport failure happens while the network
     * is disallowed — wait instead of burning retry attempts into ERROR.
     * Acquires the mutex itself (the caller doesn't hold it).
     */
    private suspend fun pauseForNetwork(taskId: Long) {
        mutex.withLock {
            setWaitingForNetworkLocked(taskId)
        }
    }

    // ── Internal pause (M42 — assumes the mutex is held) ─────────────────────

    /**
     * REVIEW-5 M42: internal pause that ASSUMES the mutex is held. Used by
     * [onNetworkChanged] (which already holds the mutex) + by the public [pause]
     * (which acquires the mutex then delegates here).
     *
     * ROUND 121: also accepts WAITING_FOR_NETWORK (a user pause of a waiting
     * task converts it to PAUSED — their intent wins over auto-resume).
     */
    private fun pauseInternal(taskId: Long) {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.status != DownloadStatus.DOWNLOADING &&
            current.status != DownloadStatus.QUEUED &&
            current.status != DownloadStatus.RETRYING &&
            current.status != DownloadStatus.WAITING_FOR_NETWORK
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

    /**
     * ROUND 121: internal WAITING_FOR_NETWORK setter that ASSUMES the mutex is
     * held (the paused-for-network sibling of [pauseInternal]). Non-active
     * statuses (PAUSED/terminal/already waiting) are left alone — a user
     * pause must NOT be un-paused by a network event.
     */
    private fun setWaitingForNetworkLocked(taskId: Long) {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (current.status != DownloadStatus.DOWNLOADING &&
            current.status != DownloadStatus.QUEUED &&
            current.status != DownloadStatus.RETRYING
        ) return
        mutateTaskLocked(taskId) {
            it.copy(status = DownloadStatus.WAITING_FOR_NETWORK, lastError = null)
        }
        store.updateState(
            id = taskId,
            status = DownloadStatus.WAITING_FOR_NETWORK,
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
     *
     * ROUND 120 (D-749): QUEUED candidates are sorted by the user's persisted
     * queue order BEFORE `take(slots)` — a reorder is REAL (the next download
     * starts in the user's order, not just the UI's), while un-reordered
     * queues keep the FIFO behavior (all ids unlisted → stable sort = no-op).
     */
    fun tryStartNext() {
        scope.launch {
            mutex.withLock {
                if (!connectivityCheck()) {
                    DownloadLogger.d { "tryStartNext — connectivity check failed (Wi-Fi-only?), skipping" }
                    return@withLock
                }
                val order = preferences.queueOrder.get()
                val queuedTasks = _tasks.value
                    .filter { it.status == DownloadStatus.QUEUED }
                    .let { candidates ->
                        if (order.isEmpty()) {
                            candidates
                        } else {
                            // Stable sort: listed tasks by their position, then
                            // unlisted (new) tasks in FIFO order after them.
                            candidates.sortedBy { task ->
                                val idx = order.indexOf(task.id.toString())
                                if (idx >= 0) idx else order.size
                            }
                        }
                    }
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
            // D-151-fix: outer retry loop. Catches retryable exceptions (IOException,
            // HttpException 5xx/429) + retries up to retryPolicy.maxAttempts with
            // exponential backoff. Non-retryable exceptions go straight to ERROR.
            // ROUND 130 (the re-test audit, 5.2): the per-attempt progress channel
            // + dbWriter used to leak on every retry/pause/network-wait (the
            // writer was a SIBLING of the download job and the channel was only
            // closed on the success path). They are tracked here so the outer
            // finally (and the next attempt's re-arm) always disposes them.
            var activeProgressChannel: Channel<ProgressUpdate>? = null
            var activeDbWriter: kotlinx.coroutines.Job? = null
            try {
                var attempt = 0
                while (true) {
                    attempt++
                    // ROUND 130 (5.2): dispose the PREVIOUS attempt's pair before
                    // re-arming (close idempotent; cancel a no-op on a finished
                    // job). cancelAndJoin so an in-flight stale updateProgress
                    // cannot land after the next attempt's fresh updateState.
                    activeProgressChannel?.close()
                    activeDbWriter?.cancelAndJoin()
                    try {
                        permits.withPermit {
                            DownloadLogger.i { "launchDownload — permit acquired for task ${task.id} (attempt $attempt/${retryPolicy.maxAttempts})" }
                    mutex.withLock {
                                val current = _tasks.value.firstOrNull { it.id == task.id }
                                if (current?.status != DownloadStatus.QUEUED && current?.status != DownloadStatus.RETRYING) {
                                    DownloadLogger.w { "launchDownload — task ${task.id} status is ${current?.status}, not QUEUED/RETRYING — aborting" }
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
                            // ROUND 130 (the re-test audit, 5.3): the FRESH
                            // snapshot (re-read under this mutex), not the
                            // launch-time capture — a resume-from-47% briefly
                            // wrote the stale launch progress to the DB.
                            progress = current?.progress ?: task.progress,
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
                    activeProgressChannel = progressChannel
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
                    activeDbWriter = dbWriter

                    DownloadLogger.i { "launchDownload — calling downloader.download for task ${task.id}" }
                    val completed = downloader.download(task) { downloaded, totalIn ->
                        // D-246 (user-reported display bug "downloaded > total"): the reported
                        // total is an ESTIMATE (HLS running-average convergence can lag the
                        // real bytes; the persisted total from a prior run may be stale). When
                        // downloaded exceeds it, GROW the total to reality — the bar never
                        // exceeds 100% and the bytes row never shows "120 MB / 96 MB".
                        // ROUND 121 (the "GB then shrank" report): the estimate can also
                        // wobble DOWN as the average converges — the total the user saw
                        // visibly shrank as the download progressed. The displayed/persisted
                        // total is now monotonically non-decreasing (never below what was
                        // already shown/persisted via prevTotal). A real Content-Length is
                        // exact from the first tick, so only estimates change behavior.
                        val rawTotal = if (totalIn in 1..downloaded) downloaded else totalIn
                        val total = if (prevTotal > 0) maxOf(rawTotal, prevTotal) else rawTotal
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
                    consecutiveDnsWaits.remove(task.id)

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
                                sizeBytes = completedTask.totalBytes.takeIf { it > 0 }
                                    ?: completedTask.downloadedBytes,
                                quality = completedTask.videoQuality.ifBlank { null },
                                completedAt = completedTask.completedAt ?: now(),
                                // D-192 Phase 4: pass source tracking fields (were lost on transition)
                                sourceId = completedTask.content.sourceId,
                                videoServer = completedTask.videoServer.ifBlank { null },
                                videoAudio = completedTask.videoAudio.ifBlank { null },
                            )
                            store.insertDownloadedEpisode(downloadedEp)
                            DownloadLogger.i { "launchDownload — inserted into downloaded_episode table for task ${task.id}" }
                            // D-241: the on-disk .data.json episodes list is updated by
                            // HttpDownloader.publishVideoFile flow (it has the storage ref +
                            // runs the upsert right after the video file is published, before
                            // returning the success result to the queue). Nothing to do here.
                        }

                        scheduleAutoClear(task.id)
                    }
                    onTaskCompleted?.invoke(completed)
                        }
                        // D-151-fix: success — exit the retry loop.
                        return@launch
                    } // end inner try
                    catch (e: CancellationException) {
                        // REVIEW-5 M37: pause/cancel — never retry, just propagate.
                        throw e
                    } catch (e: Exception) {
                        // D-246 (user-reported robustness gap): transport error while the
                        // network is disallowed (offline, or Wi-Fi lost with wifi-only) →
                        // PAUSE for auto-resume instead of burning retry attempts into
                        // ERROR. This is what makes Wi-Fi-outage recovery smooth: the
                        // task waits for connectivity instead of dying.
                        // ROUND 122 (R9): the fetchers WRAP raw IOExceptions in
                        // DownloadException ("Video download failed: Unable to resolve
                        // host…") — a bare `is IOException` check never matched and the
                        // wrapped offline failure burned straight into ERROR. The cause
                        // chain is walked now.
                        val networkAllowed = runCatching { connectivityCheck() }.getOrDefault(true)
                        val transportFailure = e.isTransportFailure()
                        val dnsFailure = e.isDnsFailure()
                        DownloadLogger.w {
                            "launchDownload — task ${task.id} failed on attempt $attempt: " +
                                "dns=$dnsFailure transport=$transportFailure networkAllowed=$networkAllowed " +
                                "msg=${e.message} → WAIT|ERROR|RETRY decided below"
                        }
                        // ROUND 123 (R7): DNS-class failures WAIT regardless of
                        // what the check said — a registered-but-unusable
                        // network (dead VPN, lingering Wi-Fi, cellular without
                        // data) passes VALIDATED checks in their dying window
                        // and DNS is the first thing to die (the device round:
                        // the same offline resume still errored). BOUNDED: a
                        // bad hardcoded host in a dead extension must not wait
                        // forever — after MAX_DNS_WAITS consecutive online
                        // checks it becomes a visible, manually retryable ERROR
                        // (which still self-heals on a real network restore —
                        // its message matches the network-error markers).
                        if (dnsFailure || (transportFailure && !networkAllowed)) {
                            if (networkAllowed) {
                                val waits = (consecutiveDnsWaits[task.id] ?: 0) + 1
                                consecutiveDnsWaits[task.id] = waits
                                if (waits > MAX_DNS_WAITS_BEFORE_ERROR) {
                                    consecutiveDnsWaits.remove(task.id)
                                    DownloadLogger.w {
                                        "launchDownload — task ${task.id}: DNS failed $waits× while " +
                                            "the check says online — likely a bad host; surfacing ERROR"
                                    }
                                    setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
                                    _tasks.value.firstOrNull { it.id == task.id }?.let { onTaskError?.invoke(it) }
                                    return@launch
                                }
                            }
                            DownloadLogger.w {
                                "launchDownload — transport/DNS failure for task ${task.id} " +
                                    "(dns=$dnsFailure allowed=$networkAllowed): WAITING_FOR_NETWORK; " +
                                    "will resume when connectivity returns"
                            }
                            pauseForNetwork(task.id)
                            return@launch
                        }
                        // D-151-fix: check if retryable.
                        if (retryPolicy.shouldRetry(e, attempt)) {
                            val backoff = retryPolicy.backoffMillis(attempt)
                            DownloadLogger.w {
                                "launchDownload — retryable error on attempt $attempt/${retryPolicy.maxAttempts} " +
                                    "for task ${task.id}: ${e.message}. Retrying in ${backoff}ms."
                            }
                            setRetryingStatus(
                                taskId = task.id,
                                attempt = attempt,
                                maxAttempts = retryPolicy.maxAttempts,
                                lastError = e.message ?: e.javaClass.simpleName,
                            )
                            delay(backoff)
                            // Loop continues — re-acquire permit on next iteration.
                        } else {
                            DownloadLogger.e(e) {
                                "launchDownload — non-retryable error (attempt $attempt/${retryPolicy.maxAttempts}) " +
                                    "for task ${task.id}: ${e.message}"
                            }
                            setErrorStatus(task.id, e.message ?: e.javaClass.simpleName)
                            _tasks.value.firstOrNull { it.id == task.id }?.let { onTaskError?.invoke(it) }
                            return@launch
                        }
                    }
                } // end while(true)
            } finally {
                // ROUND 130 (5.2): no abandoned writer survives the launch —
                // every exit (success, pause, cancel, error) disposes the pair.
                activeProgressChannel?.close()
                activeDbWriter?.cancelAndJoin()
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

        /**
         * ROUND 123 (R7): DNS-failure waits tolerated while the check says
         * "online" before surfacing a real ERROR (a bad hardcoded host in a
         * dead extension must not wait forever). Two flap cycles, then ERROR —
         * which still self-heals on a genuine network restore.
         */
        private const val MAX_DNS_WAITS_BEFORE_ERROR = 2
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 122 (R9): the network-class error classifier — FILE-level so the
//  DownloadService's keep-alive filter shares it.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The real messages stored in lastError (every wrap site embeds the cause
 * message verbatim): UnknownHostException ("Unable to resolve host …"),
 * ConnectException ("Failed to connect to …"), SocketTimeoutException
 * ("timeout"/"timed out"), SocketException ("Network is unreachable",
 * "Software caused connection abort")… Substring matching over these markers
 * identifies the FALSE errors — tasks that errored only because the network
 * was down — so network-return can heal them (the device report: the
 * offline-resume failures "would not resume even after getting the network").
 * A genuine 404/parse failure never matches and stays ERROR.
 */
private val NETWORK_ERROR_MARKERS = listOf(
    "unable to resolve host",
    "no address associated with hostname",
    "failed to connect to",
    "network is unreachable",
    "software caused connection abort",
    "connection refused",
    "connection reset",
    "econnreset",
    "timed out",
    "timeout",
)

internal fun isNetworkClassError(message: String?): Boolean =
    !message.isNullOrBlank() && NETWORK_ERROR_MARKERS.any { message.contains(it, ignoreCase = true) }

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 123 (R7): the shared failure classifiers — FILE-LEVEL so the
//  HttpDownloader cleanup decision (preserve vs delete the partial) uses the
//  EXACT same classification as the queue's wait path.
// ════════════════════════════════════════════════════════════════════════════

/**
 * True when this throwable (or anything in its cause chain, depth-capped) is
 * a transport failure — the fetchers wrap raw IOExceptions in
 * DownloadException ("Video download failed: …"), so a class check on the
 * surface exception misses.
 */
internal fun isTransportFailureChain(t: Throwable?): Boolean {
    var cur: Throwable? = t
    var depth = 0
    while (cur != null && depth < 8) {
        if (cur is java.io.IOException || cur is HttpException) return true
        cur = cur.cause
        depth++
    }
    return false
}

/**
 * ROUND 123 (R7): a DNS-class failure — the first thing to die on a network
 * that is registered but unusable (dead VPN, lingering Wi-Fi teardown,
 * cellular without data). Classified by TYPE over the cause chain
 * (UnknownHostException) with a message fallback for the fetchers that
 * flatten the cause (the two Android phrasings).
 */
internal fun Throwable.isDnsFailure(): Boolean {
    var cur: Throwable? = this
    var depth = 0
    while (cur != null && depth < 8) {
        if (cur is java.net.UnknownHostException) return true
        cur = cur.cause
        depth++
    }
    val msg = message ?: return false
    return msg.contains("unable to resolve host", ignoreCase = true) ||
        msg.contains("no address associated with hostname", ignoreCase = true)
}
