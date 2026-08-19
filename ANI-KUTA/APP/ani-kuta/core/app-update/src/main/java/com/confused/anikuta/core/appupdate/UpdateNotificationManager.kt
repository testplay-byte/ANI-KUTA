package com.confused.anikuta.core.appupdate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Manages the system notification for the in-progress app update download.
 *
 * # Lifecycle
 *
 * 1. [createChannel] — creates the `update_download` notification channel
 *    (idempotent — safe to call multiple times). Called once in
 *    [AppUpdateManager]'s `init` block.
 * 2. [showProgress] — called on every [DownloadProgress] emission while the
 *    download is in-flight. Updates the existing notification (same
 *    [NOTIFICATION_ID]) — the user sees a live progress bar in the shade.
 *
 *    **Important**: this notification is ALSO the foreground-service
 *    notification for [UpdateDownloadService] (same NOTIFICATION_ID). The
 *    service calls `startForeground` with a placeholder, then this manager
 *    updates the same notification with the live progress.
 * 3. [showComplete] — called when the download finishes. Replaces the
 *    ongoing progress notification with a "ready to install" notification
 *    (auto-cancelled when tapped).
 * 4. [cancel] — called from [AppUpdateManager.cancelDownload]. Cancels the
 *    notification entirely (no "cancelled" message — the user explicitly
 *    cancelled, so they don't need to be told).
 *
 * # Cancel action
 *
 * The progress notification has a "Cancel" action button. Tapping it fires
 * a broadcast with the [ACTION_CANCEL] intent — [AppUpdateManager]'s
 * `cancelReceiver` listens for this intent + calls `cancelDownload()`.
 *
 * # Tap action
 *
 * Tapping the notification body (not the Cancel action) launches the app's
 * main activity (which lands on the last-visited screen — typically More →
 * About → Updates).
 *
 * # Permissions
 *
 * - `POST_NOTIFICATIONS` (runtime-permitted on Android 13+) — declared in
 *   the app manifest. The `notify()` calls are wrapped in try/catch for
 *   `SecurityException` so the app doesn't crash if the user revoked the
 *   permission.
 * - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — declared in the
 *   app manifest (for [UpdateDownloadService]).
 *
 * @param context the app context (used for `getLaunchIntentForPackage` +
 *   `NotificationManagerCompat`).
 */
class UpdateNotificationManager(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Creates the notification channel (idempotent — Android ignores duplicate
     * channel creations). Safe to call on every [AppUpdateManager] init.
     */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Update Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress when downloading app updates"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Updates the download-progress notification.
     *
     * @param versionName the version being downloaded (shown in the title).
     * @param percent 0–100 (computed by the caller from
     *   `bytesDownloaded / totalBytes`). 0 if the total size is unknown.
     * @param downloadedBytes how many bytes have been written so far.
     * @param totalBytes the total APK size, or null if unknown (Content-Length
     *   missing). When null, the progress bar is indeterminate.
     */
    fun showProgress(
        versionName: String,
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long?,
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading update v$versionName...")
            .setContentText(
                "$percent% — ${formatBytes(downloadedBytes)}" +
                    if (totalBytes != null) " / ${formatBytes(totalBytes)}" else "",
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (totalBytes != null && totalBytes > 0) {
            builder.setProgress(100, percent.coerceIn(0, 100), false)
        } else {
            // Indeterminate — no total size known yet.
            builder.setProgress(0, 0, true)
        }

        // Cancel action — fires a broadcast that AppUpdateManager.cancelReceiver picks up.
        val cancelIntent = Intent(ACTION_CANCEL).setPackage(context.packageName)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            cancelPendingIntent,
        )

        // Tap action — opens the app (lands on the last-visited screen).
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (openIntent != null) {
            openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        notifySafe(builder.build())
    }

    /**
     * Posts the "download complete" notification (replaces the progress one).
     *
     * Auto-cancels when the user taps it (the system installer is launched
     * separately via [ApkInstaller] — this notification is just a visual cue).
     */
    fun showComplete(versionName: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Update downloaded")
            .setContentText("v$versionName is ready to install")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
        notifySafe(builder.build())
    }

    /**
     * Cancels the notification (called by [AppUpdateManager.cancelDownload]).
     *
     * No "cancelled" notification is posted — the user explicitly tapped
     * Cancel, so they already know.
     */
    fun cancel() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Best-effort — ignore (rare: NotificationManagerCompat from a
            // disposed context).
        }
    }

    /** Wraps `notify` with a try/catch for `SecurityException` (POST_NOTIFICATIONS denied). */
    private fun notifySafe(notification: android.app.Notification) {
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently skip.
            // The download still proceeds; the user just doesn't see the
            // system shade notification.
        } catch (e: Exception) {
            // Best-effort — never crash the download over a notification.
        }
    }

    /** Formats a byte count into a human-readable string (e.g., "12.3 MB"). */
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    companion object {
        /** The notification channel ID for update downloads. */
        const val CHANNEL_ID = "update_download"

        /**
         * The notification ID for the update download notification.
         *
         * **Important**: this MUST match [UpdateDownloadService.NOTIFICATION_ID]
         * so the service's `startForeground` call + this manager's `notify`
         * call update the SAME notification (otherwise the user would see two
         * separate notifications in the shade).
         */
        const val NOTIFICATION_ID = 9999

        /**
         * Broadcast action fired by the notification's "Cancel" button.
         *
         * Received by [AppUpdateManager]'s `cancelReceiver` → calls
         * `cancelDownload()`. The intent is package-scoped
         * (`setPackage(context.packageName)`) so other apps can't trigger it.
         */
        const val ACTION_CANCEL = "com.confused.anikuta.UPDATE_CANCEL"
    }
}
