package com.confused.anikuta.feature.download

import com.confused.anikuta.core.download.DownloadStatus
import com.confused.anikuta.core.download.DownloadTask

/**
 * UI state for the Downloads screen.
 *
 * Two sections:
 *  - [queue]: tasks that are QUEUED / DOWNLOADING / RETRYING / PAUSED / ERROR
 *    (the live queue — auto-clears COMPLETED after 10s).
 *  - [downloaded]: COMPLETED tasks grouped by anime (for the downloaded-files
 *    page — persistent until the user deletes them).
 *  - [folderUri]: the SAF tree URI string of the user-selected download folder
 *    (blank = no folder set → the empty state shows a setup prompt).
 *
 * D.6: Adapted from the old project's `DownloadUiState.kt` to use the new
 * project's [DownloadTask] (which carries [DownloadContentInfo] + [DownloadEpisodeInfo]
 * instead of the old `request.anime` / `request.episode` nested structure).
 */
data class DownloadUiState(
    val queue: List<DownloadTask> = emptyList(),
    val downloaded: Map<DownloadedAnimeKey, List<DownloadTask>> = emptyMap(),
    val folderUri: String = "",
    val isLoading: Boolean = true,
) {
    /** True if the SAF folder has been picked (the user can start downloading). */
    val folderReady: Boolean get() = folderUri.isNotBlank()

    /** True if there are no active tasks AND no completed downloads. */
    val isEmpty: Boolean get() = queue.isEmpty() && downloaded.isEmpty()
}

/**
 * A grouping key for completed downloads — by anime. Carries the display fields
 * the card needs (title, cover, contentId) so the UI doesn't re-derive them.
 *
 * D.6: keyed by [contentId] (the structured string — NOT the stable mainId, which
 * would group cross-source matches together but the user expects per-source grouping).
 */
data class DownloadedAnimeKey(
    val contentId: String,
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val coverColor: Int?,
)

/** Whether a task's status means it shows in the live queue section. */
val DownloadTask.isInQueueSection: Boolean
    get() = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.RETRYING ||
        status == DownloadStatus.PAUSED ||
        status == DownloadStatus.ERROR
