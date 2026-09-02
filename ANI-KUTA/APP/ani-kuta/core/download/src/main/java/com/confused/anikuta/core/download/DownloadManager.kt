package com.confused.anikuta.core.download

import kotlinx.coroutines.flow.StateFlow

/**
 * The download manager interface.
 *
 * D.1.15: Implemented by [DefaultDownloadManager]. The interface is the public
 * API used by `:app`'s DownloadOrchestrator + `:feature:download`'s ViewModel.
 *
 * Queue-modifying operations are `suspend` because they acquire [DownloadQueue]'s
 * Mutex internally (REVIEW-5 M41 — `mutateTask` is `suspend`).
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
    suspend fun enqueueDownload(request: DownloadRequest): Long

    /** Pauses a download (state → PAUSED, releases the download slot). */
    suspend fun pauseDownload(id: Long)

    /** Resumes a paused or errored download (state → QUEUED, triggers tryStartNext). */
    suspend fun resumeDownload(id: Long)

    /** Cancels a download (state → CANCELLED, deletes temp files). */
    suspend fun cancelDownload(id: Long)

    /** Retries an errored download (state → RETRYING, triggers re-download). */
    suspend fun retryDownload(id: Long)

    /** Pauses all active downloads. */
    suspend fun pauseAll()

    /** Resumes all paused/errored downloads. */
    suspend fun resumeAll()

    /** Cancels all downloads (active + queued). */
    suspend fun cancelAll()

    /** The live queue (QUEUED + DOWNLOADING + RETRYING + PAUSED + ERROR tasks). */
    fun getQueue(): StateFlow<List<DownloadTask>>

    /** The live downloaded episodes list (for the Downloads screen). */
    fun getDownloadedEpisodes(): StateFlow<List<DownloadedEpisode>>

    /**
     * Per-episode download states for the details page UI.
     * Key: `"$mainId|$episodeKey"`, Value: `(status, progress)`.
     *
     * D.2 will replace the `Pair<DownloadStatus, Int>` typealias with the real
     * `EpisodeDownloadState` sealed interface.
     */
    val episodeDownloadStates: StateFlow<Map<String, EpisodeDownloadState>>

    /** Whether a specific episode is downloaded. */
    fun isEpisodeDownloaded(mainId: String, episodeKey: String): Boolean

    /** Gets the content:// URI for a downloaded episode (null if not downloaded). */
    fun getDownloadedEpisodeUri(mainId: String, episodeKey: String): String?

    /**
     * Deletes a downloaded episode (video + subtitles + `.data.json` entry +
     * DB row). If it was the LAST downloaded episode of the anime, the whole
     * series folder is removed too (D-392 — see
     * [DefaultDownloadManager.maybeDeleteSeriesFolder]).
     */
    suspend fun deleteDownloadedEpisode(mainId: String, episodeKey: String)

    /**
     * D-392 (round 26): deletes EVERY downloaded episode of an anime at once —
     * the "delete all" action. Removes the whole series folder (with the
     * identity-checked safety ladder) + sweeps every `downloaded_episode` DB
     * row for the anime. Falls back to a per-episode
     * [deleteDownloadedEpisode] loop when the folder can't be located (or its
     * deletion fails) so the DB is always left consistent.
     */
    suspend fun deleteDownloadedAnime(mainId: String)

    /**
     * Requests a rescan of the download folder (reinstall recognition).
     * Runs on Dispatchers.IO. Called from AnikutaApp.onCreate.
     */
    suspend fun requestFolderRescan()
}

/** Placeholder typealias — D.2 replaces this with the real sealed interface. */
typealias EpisodeDownloadState = Pair<DownloadStatus, Int>
