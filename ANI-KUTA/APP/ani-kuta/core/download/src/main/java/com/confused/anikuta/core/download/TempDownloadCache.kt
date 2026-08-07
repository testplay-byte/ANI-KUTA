package com.confused.anikuta.core.download

import android.content.Context
import java.io.File

/**
 * Per-download temporary cache directory.
 *
 * D.1.8: Each download gets its own temp directory under
 * `context.cacheDir/downloads/<downloadId>/`. This holds the in-progress video
 * file + subtitle files until they're published to the user's SAF folder.
 *
 * REVIEW-5 M37: [cleanupTask] distinguishes `CancellationException` (preserve
 * for resume) from completion/error (delete everything).
 */
class TempDownloadCache(
    private val context: Context,
) {
    companion object {
        private const val TAG = "TempDownloadCache"
        private const val TEMP_DIR_NAME = "downloads"
        private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val VIDEO_FILE_NAME = "video.tmp"
        private const val SUBTITLE_FILE_PREFIX = "subtitle_"
        private const val SUBTITLE_FILE_SUFFIX = ".tmp"
    }

    private val tempRoot: File by lazy {
        File(context.cacheDir, TEMP_DIR_NAME).also { it.mkdirs() }
    }

    /** The temp directory for a specific download. */
    fun getTempDir(downloadId: Long): File {
        val dir = File(tempRoot, downloadId.toString())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** The temp video file for a download. */
    fun getTempVideoFile(downloadId: Long): File {
        return File(getTempDir(downloadId), VIDEO_FILE_NAME)
    }

    /** The temp subtitle file for a download (by index). */
    fun getTempSubtitleFile(downloadId: Long, index: Int): File {
        return File(getTempDir(downloadId), "$SUBTITLE_FILE_PREFIX$index$SUBTITLE_FILE_SUFFIX")
    }

    /**
     * Cleans up the temp directory for a download.
     *
     * REVIEW-5 M37: if [preserveForResume] is true (e.g. the download was
     * paused or cancelled via CancellationException), the video file is kept
     * so the download can resume. Otherwise (completion or error), everything
     * is deleted.
     */
    fun cleanupTask(downloadId: Long, preserveForResume: Boolean = false) {
        val dir = getTempDir(downloadId)
        if (!dir.exists()) return

        if (preserveForResume) {
            // Keep the video file for resume; delete only subtitle temps.
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(SUBTITLE_FILE_PREFIX)) {
                    file.delete()
                }
            }
        } else {
            // Delete everything.
            dir.deleteRecursively()
        }
    }

    /**
     * Cleans up stale temp directories (older than 24 hours).
     * Called on TempDownloadCache creation (via init) to prevent temp cache
     * from growing unboundedly across app restarts.
     */
    fun cleanupStale() {
        val now = System.currentTimeMillis()
        tempRoot.listFiles()?.forEach { dir ->
            if (dir.isDirectory && now - dir.lastModified() > STALE_THRESHOLD_MS) {
                dir.deleteRecursively()
                DownloadLogger.i { "Cleaned stale temp dir: ${dir.name}" }
            }
        }
    }

    init {
        cleanupStale()
    }
}
