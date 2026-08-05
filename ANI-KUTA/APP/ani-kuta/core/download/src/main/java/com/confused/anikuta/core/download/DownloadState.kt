package com.confused.anikuta.core.download

/**
 * Download state for a single episode.
 */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data object Paused : DownloadState
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * A download task for a single episode.
 *
 * @param episodeKey The episode to download.
 * @param videoUrl The video URL to download from.
 * @param filePath Where to save the file on disk.
 * @param quality Quality label (e.g., "1080p").
 */
data class DownloadTask(
    val id: Long,
    val episodeKey: String,
    val videoUrl: String,
    val filePath: String,
    val quality: String? = null,
    val state: DownloadState = DownloadState.Queued,
    val queuedAt: Long = System.currentTimeMillis(),
)
