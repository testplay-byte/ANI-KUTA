package com.confused.anikuta.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.DownloadManager
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.download.DownloadStatus
import com.confused.anikuta.core.download.DownloadTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Downloads screen.
 *
 * D.6: Observes [DownloadManager.getQueue] + [DownloadManager.getDownloadedEpisodes]
 * + the folder URI, combining them into a single [DownloadUiState]. Forwards user
 * actions (pause/resume/cancel/retry/delete) to the manager.
 *
 * Auto-clears COMPLETED tasks from the live queue after 10 seconds (per the owner's
 * request — the file stays on disk, but the entry disappears from the active list).
 */
class DownloadViewModel(
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Download"
        private const val COMPLETED_AUTO_CLEAR_MS = 10_000L
    }

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    /**
     * D-215: Live download speed in bytes/second. Updated every 2 seconds by
     * sampling the total downloadedBytes of all DOWNLOADING tasks. Stops
     * automatically when the ViewModel is cleared (user exits the Downloads page).
     */
    private val _downloadSpeed = MutableStateFlow(0L)
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()

    /** Tracks task IDs we've already scheduled for the 10s auto-clear (avoids dupes). */
    private val autoClearScheduled = mutableSetOf<Long>()

    /**
     * ROUND 120 (D-749): the multi-selection — task ids currently checked in
     * the queue's select mode. A plain StateFlow (NOT part of [state]) so a
     * checkbox tap doesn't re-run the queue/downloaded/folder combine.
     */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    init {
        // Combine queue + downloaded + folder URI + the persisted queue order
        // into the UI state.
        viewModelScope.launch {
            combine(
                manager.getQueue(),
                manager.getDownloadedEpisodes(),
                preferences.downloadFolderUri.changes,
                preferences.queueOrder.changes,
            ) { active, downloaded, folderUri, queueOrder ->
                // ROUND 120 (D-749): the queue renders in the USER's order —
                // listed tasks by position, unlisted (new) tasks after them,
                // FIFO within each group (the exact sort the queue's scheduler
                // applies, so what the user sees IS what downloads next).
                //
                // ROUND 121 (active-first pin): tasks that are RUNNING right
                // now (DOWNLOADING/RETRYING) pin to the very top — the device
                // round's order ("the currently-downloading should always be
                // at the very top"; a running task sat at the bottom). Stable
                // sort, so the queueOrder positions survive underneath, and
                // moveTask (which swaps against THIS list) stays consistent.
                // The grouped view groups by first encounter, so the running
                // task's anime card also lands first automatically.
                val orderedQueue = (
                    if (queueOrder.isEmpty()) {
                        active
                    } else {
                        active.sortedBy { task ->
                            val idx = queueOrder.indexOf(task.id.toString())
                            if (idx >= 0) idx else queueOrder.size
                        }
                    }
                ).let { list ->
                    list.sortedBy { task ->
                        when (task.status) {
                            DownloadStatus.DOWNLOADING -> 0
                            DownloadStatus.RETRYING -> 1
                            else -> 2
                        }
                    }
                }
                // Group the downloaded episodes by anime.
                //
                // Task 60 (round 20 — the Downloaded-page CRASH, "Key
                // 'downloaded_<contentId>' was already used" when scrolling to
                // the bottom): the table DENORMALIZES the content metadata per
                // row (title/cover/coverColor…), so two rows of the SAME anime
                // written by different paths (a cover set on one, null on the
                // other) carry the same contentId but DIFFERENT Content objects
                // — `groupBy { it.content }` split them into TWO groups whose
                // LazyColumn keys are the SAME string, and the list crashed the
                // moment the second card composed. Group by the STABLE
                // contentId instead: ONE group per contentId by construction
                // (unique keys), with the display metadata taken from the
                // richest record (first non-blank cover, else the first).
                val grouped = downloaded.groupBy { it.content.contentId }.map { (contentId, records) ->
                    val content = (
                        records.firstOrNull { !it.content.coverUrl.isNullOrBlank() }
                            ?: records.first()
                        ).content
                    DownloadedAnimeKey(
                        contentId = contentId,
                        mainId = content.mainId,
                        title = content.title,
                        coverUrl = content.coverUrl,
                        coverColor = content.coverColor,
                    ) to records.map { dEp ->
                        // Re-wrap as a DownloadTask for the UI (the UI reuses the
                        // task's request fields — videoServer/quality/audio).
                        // D-151-fix: populate videoServer + videoAudio + sourceId +
                        // downloadedBytes so the downloaded-files page shows the
                        // real resolver server + audio version + file size.
                        DownloadTask(
                            id = dEp.episode.episodeKey.hashCode().toLong(),
                            content = dEp.content,
                            episode = dEp.episode,
                            videoUrl = dEp.videoUri,
                            videoUri = dEp.videoUri,
                            videoQuality = dEp.quality ?: "",
                            status = DownloadStatus.COMPLETED,
                            progress = 100,
                            totalBytes = dEp.sizeBytes,
                            downloadedBytes = dEp.sizeBytes,
                            completedAt = dEp.completedAt,
                            videoServer = dEp.videoServer ?: "",
                            videoAudio = dEp.videoAudio ?: "",
                            sourceId = dEp.sourceId,
                        )
                    }
                }.toMap()
                DownloadUiState(
                    queue = orderedQueue,
                    downloaded = grouped,
                    folderUri = folderUri,
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }

        // ── Auto-clear COMPLETED entries after 10 seconds ──
        // Per the owner's request: "after downloading, the entries automatically
        // clear out after 10 seconds." Removes COMPLETED tasks from the active
        // queue (the file stays on disk).
        viewModelScope.launch {
            manager.getQueue().collect { active ->
                active.filter { it.status == DownloadStatus.COMPLETED }.forEach { task ->
                    if (autoClearScheduled.add(task.id)) {
                        launch {
                            delay(COMPLETED_AUTO_CLEAR_MS)
                            try {
                                manager.cancelDownload(task.id)
                            } catch (e: Exception) {
                                Logger.w(TAG) { "Auto-clear failed for task ${task.id}: ${e.message}" }
                            }
                            autoClearScheduled.remove(task.id)
                        }
                    }
                }
            }
        }

        // ── D-215+D-217: Live download speed tracking (rolling average) ──
        // Samples total downloadedBytes every 2 seconds + calculates a rolling
        // average over the last 3 samples (6 seconds) for a smoother display.
        // Stops automatically when the ViewModel is cleared (user exits the page).
        viewModelScope.launch {
            var previousTotalBytes = 0L
            val recentSpeeds = ArrayDeque<Long>(3) // Rolling window of last 3 speed samples.
            while (true) {
                delay(2_000) // Update every 2 seconds.
                val currentTotalBytes = manager.getQueue().value
                    .filter { it.status == DownloadStatus.DOWNLOADING }
                    .sumOf { it.downloadedBytes }
                val delta = currentTotalBytes - previousTotalBytes
                // Speed = delta / 2 seconds. Clamp to >= 0 (cancellations can make delta negative).
                val instantSpeed = if (delta > 0) delta / 2 else 0L
                // D-217: rolling average over last 3 samples for smoother display.
                // Prevents the speed from flickering between 0 and high values.
                recentSpeeds.addLast(instantSpeed)
                if (recentSpeeds.size > 3) recentSpeeds.removeFirst()
                _downloadSpeed.value = recentSpeeds.sum() / recentSpeeds.size
                previousTotalBytes = currentTotalBytes
            }
        }
    }

    // ── Queue actions ──

    fun pause(taskId: Long) = viewModelScope.launch { manager.pauseDownload(taskId) }
    fun resume(taskId: Long) = viewModelScope.launch { manager.resumeDownload(taskId) }
    fun cancel(taskId: Long) = viewModelScope.launch { manager.cancelDownload(taskId) }
    fun retry(taskId: Long) = viewModelScope.launch { manager.retryDownload(taskId) }

    fun pauseAll() = viewModelScope.launch { manager.pauseAll() }
    fun resumeAll() = viewModelScope.launch { manager.resumeAll() }
    fun cancelAll() = viewModelScope.launch { manager.cancelAll() }

    // ── ROUND 120 (D-749): multi-select + reorder ────────────────────────────

    /** Toggles one task's membership in the selection. */
    fun toggleSelected(taskId: Long) {
        _selection.value = _selection.value.let { if (taskId in it) it - taskId else it + taskId }
    }

    /** Checks every task currently in the queue. */
    fun selectAll() {
        _selection.value = _state.value.queue.map { it.id }.toSet()
    }

    /** Clears the selection (leaves select mode in the screen). */
    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Bulk-pauses every selected task. */
    fun pauseSelected() = viewModelScope.launch {
        _selection.value.forEach { id -> runCatching { manager.pauseDownload(id) } }
        _selection.value = emptySet()
    }

    /** Bulk-resumes every selected task. */
    fun resumeSelected() = viewModelScope.launch {
        _selection.value.forEach { id -> runCatching { manager.resumeDownload(id) } }
        _selection.value = emptySet()
    }

    /** Bulk-cancels (removes) every selected task. */
    fun cancelSelected() = viewModelScope.launch {
        _selection.value.forEach { id -> runCatching { manager.cancelDownload(id) } }
        // Drop the cancelled ids from any persisted order too (prune on write).
        pruneQueueOrder(_selection.value)
        _selection.value = emptySet()
    }

    /**
     * Moves ONE task one slot in the queue order (the reorder list's up/down
     * buttons). Round 120 (D-749): the VM computes the swap against the LIVE
     * sorted queue + writes the full order atomically — a rapid double-tap
     * before recomposition can't compute from a stale remembered list (the
     * screen-side variant could drop the first move). Every write PRUNES to
     * live task ids, so completed/cancelled ids never accumulate in the pref.
     */
    fun moveTask(taskId: Long, up: Boolean) = viewModelScope.launch {
        val liveIds = _state.value.queue.map { it.id }
        val idx = liveIds.indexOf(taskId)
        if (idx < 0) return@launch
        val target = if (up) idx - 1 else idx + 1
        if (target < 0 || target >= liveIds.size) return@launch
        preferences.queueOrder.set(
            liveIds.toMutableList().apply {
                val tmp = this[idx]
                this[idx] = this[target]
                this[target] = tmp
            }.map(Long::toString),
        )
    }

    /** Removes [gone] ids from the persisted order (cancel/delete cleanup). */
    private suspend fun pruneQueueOrder(gone: Collection<Long>) {
        val goneStr = gone.map(Long::toString).toSet()
        val current = preferences.queueOrder.get()
        if (current.any { it in goneStr }) {
            preferences.queueOrder.set(current.filterNot { it in goneStr })
        }
    }

    // ── Downloaded-episode actions ──

    fun deleteEpisode(mainId: String, episodeKey: String) = viewModelScope.launch {
        manager.deleteDownloadedEpisode(mainId, episodeKey)
    }

    fun deleteAnime(mainId: String) = viewModelScope.launch {
        // D-392 (round 26): the atomic delete-all — one folder walk, the whole
        // series folder removed (identity-checked), every DB row swept. The old
        // per-episode loop did N findContentFolder walks and left an empty husk
        // folder behind; the manager now owns the whole operation.
        manager.deleteDownloadedAnime(mainId)
    }

    /** Returns the content:// URI for a downloaded episode (null if not downloaded). */
    fun getDownloadedEpisodeUri(mainId: String, episodeKey: String): String? =
        manager.getDownloadedEpisodeUri(mainId, episodeKey)

    /**
     * Persists the SAF folder URI (from the folder picker).
     *
     * D-242: ALSO triggers a folder rescan. Without this, the Downloaded page
     * would show empty after the user re-selects the folder (e.g. after clearing
     * app data + reinstalling) — the scanner only ran on app startup (when the
     * URI was still blank), so the `downloaded_episode` DB rows were never
     * restored from `.data.json`. The user had to kill + restart the app to
     * trigger the scan. Now the rescan fires immediately after folder selection.
     */
    fun setDownloadFolder(treeUriString: String) {
        preferences.downloadFolderUri.set(treeUriString)
        viewModelScope.launch {
            manager.requestFolderRescan()
        }
    }
}
