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

    init {
        // Combine queue + downloaded + folder URI into the UI state.
        viewModelScope.launch {
            combine(
                manager.getQueue(),
                manager.getDownloadedEpisodes(),
                preferences.downloadFolderUri.changes,
            ) { active, downloaded, folderUri ->
                // Group the downloaded episodes by anime.
                val grouped = downloaded.groupBy { it.content }.map { (content, _) ->
                    val firstEpisode = downloaded.first { it.content == content }
                    DownloadedAnimeKey(
                        contentId = content.contentId,
                        mainId = content.mainId,
                        title = content.title,
                        coverUrl = content.coverUrl,
                        coverColor = content.coverColor,
                    ) to downloaded.filter { it.content == content }
                        .map { dEp ->
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
                    queue = active,
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

        // ── D-215: Live download speed tracking ──
        // Samples total downloadedBytes every 2 seconds + calculates the delta.
        // Stops automatically when the ViewModel is cleared (user exits the page).
        viewModelScope.launch {
            var previousTotalBytes = 0L
            while (true) {
                delay(2_000) // Update every 2 seconds.
                val currentTotalBytes = manager.getQueue().value
                    .filter { it.status == DownloadStatus.DOWNLOADING }
                    .sumOf { it.downloadedBytes }
                val delta = currentTotalBytes - previousTotalBytes
                // Speed = delta / 2 seconds. Clamp to >= 0 (in case a download was
                // cancelled between samples, the delta could be negative).
                _downloadSpeed.value = if (delta > 0) delta / 2 else 0L
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

    // ── Downloaded-episode actions ──

    fun deleteEpisode(mainId: String, episodeKey: String) = viewModelScope.launch {
        manager.deleteDownloadedEpisode(mainId, episodeKey)
    }

    fun deleteAnime(mainId: String) = viewModelScope.launch {
        // Delete every downloaded episode for this anime.
        val eps = manager.getDownloadedEpisodes().value
            .filter { it.content.mainId == mainId }
        eps.forEach { ep ->
            manager.deleteDownloadedEpisode(mainId, ep.episode.episodeKey)
        }
    }

    /** Returns the content:// URI for a downloaded episode (null if not downloaded). */
    fun getDownloadedEpisodeUri(mainId: String, episodeKey: String): String? =
        manager.getDownloadedEpisodeUri(mainId, episodeKey)

    /** Persists the SAF folder URI (from the folder picker). */
    fun setDownloadFolder(treeUriString: String) {
        preferences.downloadFolderUri.set(treeUriString)
    }
}
