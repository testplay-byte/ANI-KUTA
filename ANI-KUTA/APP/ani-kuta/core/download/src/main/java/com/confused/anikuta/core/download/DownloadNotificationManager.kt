package com.confused.anikuta.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The download notification manager — posts ongoing progress, completion, and error
 * notifications per `06-notifications-foreground-service.md` §13.
 *
 * REVIEW-5 M20+M21+M22+M24+M25: uses the synchronous startForeground pattern (the
 * [DownloadService] calls [buildPlaceholderNotification] + `startForeground` in
 * `onStartCommand` BEFORE any coroutine work), suspend thumbnail loading, and
 * [NotificationManagerCompat].
 *
 * DEVIATION (D.1 — Coil dep not yet wired into :core:download): the plan calls for
 * the Coil 3 API (`context.imageLoader`, `coil3.request.ImageRequest`,
 * `image.asDrawable(context).toBitmap()`). Adding Coil to `:core:download`'s deps
 * requires modifying `build.gradle.kts` (forbidden by rule #1). The D.1
 * implementation uses [BitmapFactory] for cached `cover.jpg` + plain OkHttp for the
 * network fallback. Functionally equivalent — D.2/D.4 can swap in Coil when the dep
 * is added.
 *
 * Two channels:
 *  - [CHANNEL_PROGRESS] (`anikuta_downloads_progress`) — `IMPORTANCE_LOW`, no sound,
 *    used for the ongoing summary + error notifications.
 *  - [CHANNEL_COMPLETE] (`anikuta_downloads_complete`) — `IMPORTANCE_DEFAULT`, with
 *    sound, used for completion notifications.
 *
 * Notification IDs:
 *  - [SUMMARY_ID] (9001) — the ongoing progress notification (also the foreground
 *    notification for [DownloadService]).
 *  - [COMPLETION_OFFSET] (10_000) — `taskId + 10_000` for completion notifications.
 *  - [ERROR_OFFSET] (20_000) — `taskId + 20_000` for error notifications.
 */
class DownloadNotificationManager(
    private val context: Context,
    private val storage: DownloadStorageProvider,
    /**
     * Used for the network-fallback cover-image fetch (when no `cover.jpg` is cached
     * in the SAF folder). REVIEW-5 M22: this fetch is `suspend` + runs on
     * `Dispatchers.IO` (no `runBlocking` on the main thread).
     */
    private val okHttpClient: OkHttpClient,
) {

    init {
        ensureChannels()
    }

    /**
     * Builds the placeholder foreground notification (used by [DownloadService] for
     * the synchronous `startForeground` call in `onStartCommand`).
     */
    fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ANI-KUTA")
            .setContentText("Preparing downloads…")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    /**
     * REVIEW-5 M22: builds the summary notification (suspend — heavy work on IO).
     * Used by [DownloadService] on every queue state change.
     *
     * Returns `null` if [active] is empty (the caller should `stopForeground` + `stopSelf`).
     */
    suspend fun buildSummaryNotification(active: List<DownloadTask>): Notification? {
        if (active.isEmpty()) return null
        val primary = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
            ?: active.firstOrNull()
            ?: return null

        val title = if (active.size == 1) {
            "${primary.content.title} — EP ${primary.episode.episodeNumber.toInt()}"
        } else {
            "Downloading ${active.size} episodes"
        }
        val progressText = if (primary.totalBytes > 0) {
            "${primary.progress}% • ${formatBytes(primary.downloadedBytes)} / ${formatBytes(primary.totalBytes)}"
        } else {
            "${primary.progress}% • ${formatBytes(primary.downloadedBytes)}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(progressText)
            .setProgress(100, primary.progress.coerceAtLeast(0), primary.progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openDownloadsScreenIntent())
            .addAction(android.R.drawable.ic_media_pause, "Pause all", pauseAllIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel all", cancelAllIntent())

        // Thumbnail (cover image of the primary task's content). REVIEW-5 M22: suspend.
        val thumbnail = loadThumbnail(primary.content.mainId, primary.content.coverUrl)
        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
        }
        return builder.build()
    }

    /**
     * Posts the completion notification (with sound via [CHANNEL_COMPLETE]).
     *
     * REVIEW-5 §13.3: `BigPictureStyle` with the cover thumbnail + auto-cancel.
     */
    suspend fun notifyCompleted(task: DownloadTask) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(
                    "${task.content.title} — EP ${task.episode.episodeNumber.toInt()}",
                )
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openDownloadsScreenIntent())

            val thumbnail = loadThumbnail(task.content.mainId, task.content.coverUrl)
            if (thumbnail != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(thumbnail)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(task.content.title),
                ).setLargeIcon(thumbnail)
            }

            notify(task.id.toInt() + COMPLETION_OFFSET, builder.build())
        } catch (e: SecurityException) {
            DownloadLogger.w { "Cannot post completion notification (permission denied)" }
        } catch (e: Exception) {
            DownloadLogger.w { "notifyCompleted failed (non-fatal): ${e.message}" }
        }
    }

    /** Posts the error notification (on the silent [CHANNEL_PROGRESS]). */
    suspend fun notifyError(task: DownloadTask) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download failed")
                .setContentText(
                    "${task.content.title} — ${task.lastError ?: "Unknown error"}",
                )
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openDownloadsScreenIntent())

            val thumbnail = loadThumbnail(task.content.mainId, task.content.coverUrl)
            if (thumbnail != null) builder.setLargeIcon(thumbnail)

            notify(task.id.toInt() + ERROR_OFFSET, builder.build())
        } catch (e: SecurityException) {
            DownloadLogger.w { "Cannot post error notification (permission denied)" }
        } catch (e: Exception) {
            DownloadLogger.w { "notifyError failed (non-fatal): ${e.message}" }
        }
    }

    /** Cancels the summary notification (called when the queue empties). */
    fun cancelSummary() {
        try {
            NotificationManagerCompat.from(context).cancel(SUMMARY_ID)
        } catch (e: Exception) {
            DownloadLogger.w { "cancelSummary failed (non-fatal): ${e.message}" }
        }
    }

    /** Posts the "time limit reached" notification (Android 14+ 6-hour cap). */
    fun notifyTimeLimitReached() {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Downloads paused")
                .setContentText("Time limit reached (Android 14+ caps dataSync services at 6h/day).")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            notify(SUMMARY_ID, builder.build())
        } catch (e: Exception) {
            DownloadLogger.w { "notifyTimeLimitReached failed (non-fatal): ${e.message}" }
        }
    }

    // ── Thumbnail loading ────────────────────────────────────────────────────

    /**
     * Loads the cover image thumbnail for the notification.
     *
     * REVIEW-5 M22: this function is `suspend` + called from `withContext(Dispatchers.IO)`
     * by the callers. The OLD draft was synchronous + called on `Dispatchers.Main` —
     * triggered ANRs on slow CDNs.
     *
     * DEVIATION (D.1): the plan calls for Coil 3. We use [BitmapFactory] for the
     * cached `cover.jpg` + plain OkHttp for the network fallback (Coil isn't yet a
     * `:core:download` dep).
     *
     * Strategy:
     * 1. Try the cached `cover.jpg` in the content's SAF folder (no network).
     * 2. If not cached, download from [coverUrl] via OkHttp (best-effort).
     * 3. If both fail, return `null` — the notification shows without a thumbnail.
     */
    private suspend fun loadThumbnail(mainId: String, coverUrl: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            // 1. Try the cached cover.jpg.
            val contentDir = storage.findContentFolder(mainId)
            if (contentDir != null) {
                val coverFile = contentDir.listFiles().firstOrNull { it.name == "cover.jpg" }
                if (coverFile != null) {
                    try {
                        context.contentResolver.openInputStream(coverFile.uri)?.use { input ->
                            val raw = BitmapFactory.decodeStream(input)
                            if (raw != null) return@withContext scaleForNotification(raw)
                        }
                    } catch (e: Exception) {
                        DownloadLogger.w { "loadThumbnail — cached cover.jpg decode failed: ${e.message}" }
                    }
                }
            }
            // 2. Network fallback via OkHttp.
            downloadCover(coverUrl)
        }

    /** Downloads the cover from [coverUrl] + decodes to a [Bitmap] (best-effort). */
    private suspend fun downloadCover(coverUrl: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            if (coverUrl.isNullOrBlank()) return@withContext null
            try {
                val request = Request.Builder().url(coverUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    scaleForNotification(raw)
                }
            } catch (e: Exception) {
                DownloadLogger.w { "downloadCover — fetch failed: ${e.message}" }
                null
            }
        }

    /** Scales [bitmap] to a notification-friendly size (96x96 max). */
    private fun scaleForNotification(bitmap: Bitmap): Bitmap {
        val max = THUMBNAIL_SIZE
        if (bitmap.width <= max && bitmap.height <= max) return bitmap
        val ratio = minOf(max.toFloat() / bitmap.width, max.toFloat() / bitmap.height)
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    // ── Channels + intents ───────────────────────────────────────────────────

    /** Creates the two notification channels (idempotent). */
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Channel 1: ongoing progress — silent (no sound during download).
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Download progress notifications (silent during download)"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(progressChannel)

        // Channel 2: completion — plays the default notification sound.
        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            "Download complete",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications when downloads finish (with sound)"
            setShowBadge(true)
            enableVibration(true)
        }
        nm.createNotificationChannel(completeChannel)
    }

    /**
     * The deep-link tap intent — opens the app's launcher activity with the
     * `anikuta://downloads` deep-link.
     *
     * NOTE: the host's `MainActivity.onNewIntent` should intercept this URI +
     * push the `DownloadsKey` onto the Nav3 stack. For D.1, the launcher intent
     * alone is sufficient (the user lands on the home screen + can navigate).
     */
    private fun openDownloadsScreenIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        intent.data = Uri.parse("anikuta://downloads")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** REVIEW-5 M29: unique request-code prefix (1001) so Pause doesn't collide. */
    private fun pauseAllIntent(): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL
        }
        return PendingIntent.getService(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** REVIEW-5 M29: unique request-code prefix (1002) so Cancel doesn't collide. */
    private fun cancelAllIntent(): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL_ALL
        }
        return PendingIntent.getService(
            context, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Wraps NotificationManagerCompat.notify with a try/catch (POST_NOTIFICATIONS denied). */
    private fun notify(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            DownloadLogger.w { "Cannot post notification (permission denied): id=$id" }
        }
    }

    /** Formats a byte count for the progress text. */
    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    companion object {
        /** The ongoing progress channel (silent). */
        const val CHANNEL_PROGRESS = "anikuta_downloads_progress"

        /** The completion channel (with sound). */
        const val CHANNEL_COMPLETE = "anikuta_downloads_complete"

        /** The summary notification ID (also the foreground notification ID). */
        const val SUMMARY_ID = 9001

        /** Completion notifications use `taskId + 10_000`. */
        const val COMPLETION_OFFSET = 10_000

        /** Error notifications use `taskId + 20_000`. */
        const val ERROR_OFFSET = 20_000

        /** The thumbnail max size (px). */
        private const val THUMBNAIL_SIZE = 96
    }
}
