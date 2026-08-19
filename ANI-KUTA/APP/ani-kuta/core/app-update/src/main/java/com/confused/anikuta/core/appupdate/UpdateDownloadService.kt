package com.confused.anikuta.core.appupdate

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the update download alive when the app is
 * killed from recents.
 *
 * # Design
 *
 * The actual download work is performed by [AppUpdateManager.startDownload]
 * (which launches a coroutine on the manager's long-lived scope). This
 * service does NOT do any downloading itself — it just calls
 * `startForeground` to promote the process to foreground priority, which
 * Android respects even if the user swipes the app from recents.
 *
 * # Notification sharing
 *
 * This service's foreground notification + [UpdateNotificationManager]'s
 * progress notification share the SAME [NOTIFICATION_ID] (9999). The flow:
 *
 * 1. [AppUpdateManager.startDownload] starts this service via
 *    [ContextCompat.startForegroundService] with the [ACTION_START] intent
 *    (carrying the version name).
 * 2. The service calls `startForeground` with a placeholder notification
 *    ("Downloading update v{version}...") — Android 12+ requires this within
 *    5 seconds of `startForegroundService`.
 * 3. [AppUpdateManager.startDownload]'s flow collector calls
 *    [UpdateNotificationManager.showProgress] on every progress emission,
 *    which updates the SAME notification (same ID) with the live percent +
 *    cancel action.
 * 4. On completion, [AppUpdateManager] calls
 *    [UpdateNotificationManager.showComplete] (replaces the progress notif
 *    with a "ready to install" one) and stops this service via [stop].
 * 5. On cancellation, [AppUpdateManager.cancelDownload] calls
 *    [UpdateNotificationManager.cancel] + stops this service.
 *
 * # Why a service and not just WorkManager?
 *
 * WorkManager's `dataSync` worker has a 6-hour daily cap on Android 14+ and
 * is paused during doze. A foreground `dataSync` service can run for up to
 * 6 hours continuously (the same cap, but per-service, not per-app), and the
 * ongoing notification makes the user aware. APK updates typically take
 * seconds to a few minutes (large APKs on slow connections) — well within
 * the cap.
 *
 * # Manifest declaration
 *
 * Must be declared in the app manifest:
 * ```xml
 * <service
 *     android:name="com.confused.anikuta.core.appupdate.UpdateDownloadService"
 *     android:exported="false"
 *     android:foregroundServiceType="dataSync" />
 * ```
 * The `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions must
 * also be declared (already present in this app's manifest).
 */
class UpdateDownloadService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val versionName = intent.getStringExtra(EXTRA_VERSION_NAME)
                if (versionName.isNullOrEmpty()) {
                    // No version name → nothing to display. Stop immediately.
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Synchronously call startForeground with a placeholder
                // notification (Android 12+ 5-second contract). The actual
                // progress updates are posted by UpdateNotificationManager.
                startForegroundCompat(
                    buildProgressNotification(versionName, 0, 0L, null),
                )
            }
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Builds the placeholder progress notification used for `startForeground`.
     *
     * **Important**: this MUST use the same channel + NOTIFICATION_ID as
     * [UpdateNotificationManager] so the service's foreground notification
     * and the manager's progress updates are merged into one notification.
     */
    private fun buildProgressNotification(
        versionName: String,
        percent: Int,
        downloadedBytes: Long,
        totalBytes: Long?,
    ): Notification {
        val builder = NotificationCompat.Builder(this, UpdateNotificationManager.CHANNEL_ID)
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
            builder.setProgress(0, 0, true) // indeterminate
        }
        return builder.build()
    }

    /**
     * Calls `startForeground` with the correct `foregroundServiceType` for
     * Android 14+ (API 34+).
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                UpdateNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(UpdateNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    /** Stops the foreground service + removes the notification. */
    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** Formats a byte count for the progress text. */
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    companion object {
        /** Intent action: start the foreground service for an active download. */
        const val ACTION_START = "com.confused.anikuta.UPDATE_START"

        /** Intent action: stop the foreground service (download done / cancelled). */
        const val ACTION_STOP = "com.confused.anikuta.UPDATE_STOP"

        /** Extra: the version name being downloaded (for the notification title). */
        const val EXTRA_VERSION_NAME = "version_name"

        /**
         * Starts the foreground service (called by [AppUpdateManager.startDownload]).
         *
         * Uses [ContextCompat.startForegroundService] for backward compat
         * (it normalizes the API 26+ `startForegroundService` vs pre-26
         * `startService` difference).
         */
        fun start(context: Context, versionName: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_VERSION_NAME, versionName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Stops the foreground service (called by [AppUpdateManager] on
         * download completion OR cancellation).
         */
        fun stop(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_STOP
            }
            // try startService with STOP action first (so the service gets a
            // chance to call stopForeground cleanly), then fall back to
            // stopService if the service isn't running.
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Background restrictions — fall back to stopService.
            }
            context.stopService(Intent(context, UpdateDownloadService::class.java))
        }
    }
}
