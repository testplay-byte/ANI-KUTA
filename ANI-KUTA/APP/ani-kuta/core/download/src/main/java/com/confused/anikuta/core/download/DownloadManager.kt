package com.confused.anikuta.core.download

import kotlinx.coroutines.flow.StateFlow

/**
 * The download manager interface.
 *
 * D.1.15: Implemented by [DefaultDownloadManager]. The interface is the public
 * API used by `:app`'s DownloadOrchestrator + `:feature:download`'s ViewModel.
 *
 * All methods are non-suspending except [requestFolderRescan] — they delegate
 * to [DownloadQueue] which handles its own coroutine scope.
 *
 * CORE_RULES §7: interface is pure — no logic.
 */
interface DownloadManager {

    /**
     * Enqueues a download request. Creates a [DownloadTask], persists it to the
     * DB, starts the foreground service, and triggers `tryStartNext`.
     *
     * @param request The resolved download request (video URL + headers + tracks).
     * @return The DB row ID of the created task.
     */
    fun enqueueDownload(request: DownloadRequest): Long

    /** Pauses a download (state → PAUSED, releases the download slot). */
    fun pauseDownload(id: Long)

    /** Resumes a paused or errored download (state → QUEUED, triggers tryStartNext). */
    fun resumeDownload(id: Long)

    /** Cancels a download (state → CANCELLED, deletes temp files). */
    fun cancelDownload(id: Long)

    /** Retries an errored download (state → RETRYING, triggers re-download). */
    fun retryDownload(id: Long)

    /** Pauses all active downloads. */
    fun pauseAll()

    /** Resumes all paused/errored downloads. */
    fun resumeAll()

    /** Cancels all downloads (active + queued). */
    fun cancelAll()

    /** The live queue (QUEUED + DOWNLOADING + RETRYING + PAUSED + ERROR tasks). */
    val queue: StateFlow<List<DownloadTask>>

    /** The live downloaded episodes list (for the Downloads screen). */
    val downloadedEpisodes: StateFlow<List<DownloadedEpisode>>

    /**
     * Per-episode download states for the details page UI.
     * Key: `"$mainId:$episodeKey"`, Value: `(status, progress)`.
     *
     * D.2 will replace the `Pair<DownloadStatus, Int>` typealias with the real
     * `EpisodeDownloadState` sealed interface.
     */
    val episodeDownloadStates: StateFlow<Map<String, Pair<DownloadStatus, Int>>>

    /** Whether a specific episode is downloaded. */
    fun isEpisodeDownloaded(mainId: String, episodeKey: String): Boolean

    /** Gets the content:// URI for a downloaded episode (null if not downloaded). */
    fun getDownloadedEpisodeUri(mainId: String, episodeKey: String): String?

    /** Deletes a downloaded episode (file + DB row). */
    fun deleteDownloadedEpisode(mainId: String, episodeKey: String)

    /**
     * Requests a rescan of the download folder (reinstall recognition).
     * Runs on Dispatchers.IO. Called from AnikutaApp.onCreate.
     */
    suspend fun requestFolderRescan()

    /** Releases resources (called from AnikutaApp.onTerminate or onLowMemory). */
    fun release()
}

/** Placeholder typealias — D.2 replaces this with the real sealed interface. */
typealias EpisodeDownloadState = Pair<DownloadStatus, Int>
