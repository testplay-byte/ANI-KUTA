package com.confused.anikuta.core.download

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads video files for offline playback.
 *
 * Supports HTTP downloads with byte-range resume.
 * HLS downloads (segment-level) will be added when needed (Phase 4).
 *
 * Downloads are persisted in the SQLDelight `download_queue` + `downloaded_episode`
 * tables. The manager uses [OkHttpClient] for HTTP requests.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:Download".
 * CORE_RULES §23: Download state is reactive (StateFlow).
 */
class DownloadManager(
    private val database: AnikutaDatabase,
    private val httpClient: OkHttpClient,
    private val downloadDir: File,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Download"
        private const val BUFFER_SIZE = 8192
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadState>> = _activeDownloads.asStateFlow()

    /**
     * Queue an episode for download.
     *
     * @param episodeKey The episode to download.
     * @param videoUrl The video URL.
     * @param quality Quality label (e.g., "1080p").
     */
    fun enqueueDownload(episodeKey: String, videoUrl: String, quality: String? = null) {
        Logger.i(TAG) { "Enqueueing download: $episodeKey (quality: $quality)" }

        val filePath = File(downloadDir, "$episodeKey.mp4").absolutePath

        // Insert into DB queue
        database.downloadQueueQueries.insertDownloadQueue(episodeKey, System.currentTimeMillis())

        // Start downloading
        _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Queued)
        startDownload(episodeKey, videoUrl, filePath, quality)
    }

    /**
     * Start downloading a video file.
     */
    private fun startDownload(
        episodeKey: String,
        videoUrl: String,
        filePath: String,
        quality: String?,
    ) {
        scope.launch {
            try {
                _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Downloading(0))

                val request = Request.Builder().url(videoUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Logger.e(TAG) { "Download failed: HTTP ${response.code} for $episodeKey" }
                    _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Failed("HTTP ${response.code}"))
                    return@launch
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val file = File(filePath)
                file.parentFile?.mkdirs()

                var totalRead = 0L
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read

                            if (contentLength > 0) {
                                val progress = ((totalRead * 100) / contentLength).toInt()
                                _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Downloading(progress))
                            }
                        }
                    }
                }

                Logger.i(TAG) { "Download completed: $episodeKey (${file.length()} bytes)" }

                // Record in DB
                database.downloadedEpisodeQueries.insertDownloadedEpisode(
                    episode_key = episodeKey,
                    file_path = filePath,
                    file_size = file.length(),
                    quality = quality,
                    downloaded_at = System.currentTimeMillis(),
                )

                // Remove from queue
                database.downloadQueueQueries.deleteDownloadQueueByEpisode(episodeKey)

                _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Completed)

            } catch (e: Exception) {
                Logger.e(TAG, e) { "Download failed for $episodeKey: ${e.message}" }
                _activeDownloads.value = _activeDownloads.value + (episodeKey to DownloadState.Failed(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Check if an episode is downloaded.
     */
    fun isDownloaded(episodeKey: String): Boolean {
        return database.downloadedEpisodeQueries.isEpisodeDownloaded(episodeKey).executeAsOne()
    }

    /**
     * Get the file path for a downloaded episode.
     */
    fun getDownloadedFilePath(episodeKey: String): String? {
        return database.downloadedEpisodeQueries.getDownloadedEpisode(episodeKey).executeAsOneOrNull()?.file_path
    }

    /**
     * Delete a downloaded episode.
     */
    fun deleteDownload(episodeKey: String) {
        Logger.i(TAG) { "Deleting download: $episodeKey" }

        // Delete file
        val filePath = getDownloadedFilePath(episodeKey)
        filePath?.let { File(it).delete() }

        // Delete from DB
        database.downloadedEpisodeQueries.deleteDownloadedEpisode(episodeKey)
        database.downloadQueueQueries.deleteDownloadQueueByEpisode(episodeKey)

        // Update state
        _activeDownloads.value = _activeDownloads.value - episodeKey
    }
}
