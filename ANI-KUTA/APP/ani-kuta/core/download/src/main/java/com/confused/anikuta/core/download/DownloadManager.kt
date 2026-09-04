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

    /**
     * D-407 → D-408 (round 32): resolves the SUBTITLE TRACKS for a DOWNLOADED
     * episode — the ONE shared answer every playback path uses (the
     * details-page hand-off, the downloads-page hand-off, the in-player
     * episode switch, and the subtitle sheet's "Available in storage"
     * listing), so they can never disagree.
     *
     * A LAYERED chain (round 32) — a single broken link can never yield an
     * empty answer: (0) a stale in-memory cache is reloaded from the DB once;
     * (1) the DB row's `subtitleUris`; when empty, the disk chain — (2) the
     * episode's OWN video file location (the most direct truth, immune to
     * `.data.json` corruption + mainId drift), (3) the mainId manifest walk,
     * (4) the title fallback. Each track carries a human-readable label
     * derived from its on-disk filename ("English", "My Custom Subs", …) —
     * see [DownloadedSubtitleLabels.labelForUri].
     *
     * @param mainId The content's mainId.
     * @param episodeNumber The episode number (1-based; drives the file
     *   pattern + the DB row match).
     * @return the resolved tracks (empty when the episode has no subtitles on
     *   disk or is not downloaded).
     */
    suspend fun resolveSubtitleTracks(mainId: String, episodeNumber: Int): List<ResolvedSubtitleTrack>

    /**
     * D-407 → D-408 (round 32): imports ONE manually-picked subtitle file into a
     * downloaded episode's dedicated `subtitles/` folder — the persistence
     * half of the player's "Add subtitle file" flow.
     *
     * D-408: a DEDUP phase precedes the copy — when the picked file already IS
     * one of the episode's subtitle files (same document or same file name),
     * NO copy is made and the existing track is returned (picking a file from
     * the series' own subtitles/ folder must not create a `_manual_` duplicate).
     *
     * Writes `subtitle_E{num:5}_manual_{name}.{ext}` via SAF, APPENDS the new
     * URI to the DB row's `subtitleUris` AND the folder's `.data.json`
     * episodes entry (the durable source of truth — the scanner rebuilds the
     * DB from it across reinstalls), then refreshes the in-memory cache.
     *
     * @param mainId The content's mainId.
     * @param episodeKey The episode's key (`SEpisode.url`).
     * @param source The picked file's `content://` URI (the SAF picker).
     * @param displayName The picked file's display name (drives the on-disk
     *   filename + the returned label).
     * @return the persisted (or dedup-matched existing) track (URI + label),
     *   or `null` on failure (no DB row, unsupported extension, folder/IO
     *   failure — logged).
     */
    suspend fun importManualSubtitle(
        mainId: String,
        episodeKey: String,
        source: android.net.Uri,
        displayName: String?,
    ): ResolvedSubtitleTrack?
}

/** Placeholder typealias — D.2 replaces this with the real sealed interface. */
typealias EpisodeDownloadState = Pair<DownloadStatus, Int>
