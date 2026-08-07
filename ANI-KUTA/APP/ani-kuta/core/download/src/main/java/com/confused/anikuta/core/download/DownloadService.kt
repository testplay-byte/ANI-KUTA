package com.confused.anikuta.core.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The foreground service hosting the download engine.
 *
 * D.1.11 + 06-notifications-foreground-service.md §13.7:
 *  - REVIEW-5 M20: synchronous `startForeground` in [onStartCommand] (per the
 *    `ExtensionInstallService.kt` pattern). The service calls `startForeground`
 *    with a placeholder notification BEFORE any coroutine work, satisfying the
 *    Android 12+ 5-second contract regardless of queue state.
 *  - REVIEW-5 M22: heavy work (thumbnail load, SAF I/O) on `Dispatchers.IO`;
 *    only `startForeground` / `NotificationManager.notify` need `Dispatchers.Main`.
 *  - REVIEW-5 M25: [KoinComponent] required for `by inject<>()`.
 *  - REVIEW-5 M24: declares the [NotificationManagerCompat] explicitly (was
 *    undefined in the OLD draft).
 *  - REVIEW-5 M27: [onTimeout] gracefully pauses the queue + posts a one-shot
 *    "time limit reached" notification on the Android 14+ 6-hour cap.
 *  - REVIEW-5 M28: [onTaskRemoved] re-launches the service for aggressive OEMs
 *    (Xiaomi/Huawei) that kill on swipe-from-recents.
 *  - `START_STICKY` return — Android may restart the service if killed; the queue
 *    is persisted in SQLDelight so it recovers.
 *  - `stopSelf` when the queue empties (observed via the [queueCollector]).
 *
 * The service handles two intent actions:
 *  - [ACTION_PAUSE_ALL] → `manager.pauseAll()`.
 *  - [ACTION_CANCEL_ALL] → `manager.cancelAll()`.
 */
class DownloadService : Service(), KoinComponent {

    private val manager by inject<DownloadManager>()
    private val notifier by inject<DownloadNotificationManager>()

    /** REVIEW-5 M22: heavy work on Dispatchers.IO; only notify() needs Dispatchers.Main. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isForeground = false
    private var queueCollector: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Start collecting the queue — but DON'T call startForeground from here. The
        // system gives us 5s after startForegroundService() to call startForeground;
        // we do it synchronously in onStartCommand (per ExtensionInstallService pattern).
        queueCollector = scope.launch {
            manager.getQueue().collect { tasks ->
                val active = tasks.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.QUEUED ||
                        it.status == DownloadStatus.RETRYING
                }
                if (active.isEmpty()) {
                    // Queue emptied — gracefully leave foreground + stop.
                    withContext(Dispatchers.Main) {
                        if (isForeground) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            isForeground = false
                        }
                        stopSelf()
                    }
                } else {
                    // Build the summary notification (suspend — heavy work on IO).
                    val notification = notifier.buildSummaryNotification(active)
                    if (notification != null) {
                        withContext(Dispatchers.Main) {
                            if (!isForeground) {
                                // First non-empty emission — promote to foreground. (Rarely hit
                                // because onStartCommand already called startForeground
                                // synchronously with a placeholder. Kept for the START_STICKY
                                // restart case.)
                                startForegroundCompat(notification)
                                isForeground = true
                            } else {
                                NotificationManagerCompat.from(this@DownloadService)
                                    .notify(DownloadNotificationManager.SUMMARY_ID, notification)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // REVIEW-5 M20: SYNCHRONOUSLY start foreground with a placeholder notification
        // BEFORE any coroutine work. Satisfies the Android 12+ 5-second contract
        // regardless of queue state. Pattern copied from ExtensionInstallService.
        if (!isForeground) {
            startForegroundCompat(notifier.buildPlaceholderNotification())
            isForeground = true
        }

        when (intent?.action) {
            ACTION_PAUSE_ALL -> runBlocking { manager.pauseAll() }
            ACTION_CANCEL_ALL -> runBlocking { manager.cancelAll() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // REVIEW-5 M28: aggressive OEMs may kill the service on swipe-from-recents
        // despite the foreground notification. Re-launch via startForegroundService.
        val restart = Intent(applicationContext, DownloadService::class.java)
        ContextCompat.startForegroundService(applicationContext, restart)
        super.onTaskRemoved(rootIntent)
    }

    /**
     * REVIEW-5 M27: Android 14+ caps `dataSync` foreground services at 6 hours per
     * app per day. After 6 hours, the system calls `onTimeout` (API 35+). We
     * gracefully pause the queue + post a one-shot notification.
     */
    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        runBlocking { manager.pauseAll() }
        notifier.notifyTimeLimitReached()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        queueCollector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** Mirrors `ExtensionInstallService.startForegroundCompat` — explicit type on API 34+. */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                DownloadNotificationManager.SUMMARY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(DownloadNotificationManager.SUMMARY_ID, notification)
        }
    }

    companion object {
        /** Intent action: pause all active downloads. */
        const val ACTION_PAUSE_ALL = "com.confused.anikuta.download.PAUSE_ALL"

        /** Intent action: cancel all downloads (clears the queue). */
        const val ACTION_CANCEL_ALL = "com.confused.anikuta.download.CANCEL_ALL"

        /** Starts the foreground service (called by [DefaultDownloadManager.enqueueDownload]). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java),
            )
        }

        /** Stops the service (called when the queue empties). */
        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
