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
 *
 * ROUND 120 (D-748): [cleanupStale] no longer eats PAUSED tasks' progress —
 * temp dirs of tasks still in the queue (via [activeDownloadIdProvider]) are
 * exempt from the 24-hour sweep. A download paused for a day used to lose all
 * its bytes on the next app start (TempDownloadCache init → purge), silently
 * resetting to 0% on resume.
 */
class TempDownloadCache(
    private val context: Context,
    /**
     * Round 120: the ids of tasks that still own their temp dir (queued/
     * downloading/paused/retrying/error — anything resumable). Callback, not
     * a set: the queue changes over time and the sweep runs at construction.
     */
    private val activeDownloadIdProvider: () -> Set<Long> = { emptySet() },
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
    fun getTempVideoFile(downloadId: Long, ext: String = "mp4"): File {
        return File(getTempDir(downloadId), "video.$ext")
    }

    /**
     * D-548: a NAMED temp file for a download — the DASH pipeline's
     * per-representation media files (`video.fmp4`, `audio-1.fmp4`, …) and
     * its resume sidecar. Subtitle temps keep their dedicated accessor; the
     * [cleanupTask] preserve/delete rules apply to these the same way (a
     * pause keeps them for resume, a completion deletes them).
     */
    fun getTempFile(downloadId: Long, fileName: String): File {
        return File(getTempDir(downloadId), fileName)
    }

    /** The temp subtitle file for a download (by index). */
    fun getTempSubtitleFile(downloadId: Long, index: Int, ext: String = "vtt"): File {
        return File(getTempDir(downloadId), "$SUBTITLE_FILE_PREFIX$index.$ext")
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
     *
     * ROUND 120 (D-748): dirs owned by tasks still in the queue are EXEMPT —
     * see the class KDoc. Orphan dirs (task cancelled/completed/deleted) keep
     * the 24h lifetime.
     */
    fun cleanupStale() {
        val now = System.currentTimeMillis()
        val activeIds = runCatching(activeDownloadIdProvider).getOrDefault(emptySet())
        tempRoot.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val taskId = dir.name.toLongOrNull()
            if (taskId != null && taskId in activeIds) {
                DownloadLogger.d {
                    "Temp dir ${dir.name} is ${ageHours(now, dir)}h old but its task is " +
                        "still active — keeping it for resume"
                }
                return@forEach
            }
            if (now - dir.lastModified() > STALE_THRESHOLD_MS) {
                dir.deleteRecursively()
                DownloadLogger.i { "Cleaned stale temp dir: ${dir.name} (${ageHours(now, dir)}h old)" }
            }
        }
    }

    private fun ageHours(now: Long, dir: File): Long =
        (now - dir.lastModified()) / (60L * 60L * 1000L)

    init {
        cleanupStale()
    }
}
