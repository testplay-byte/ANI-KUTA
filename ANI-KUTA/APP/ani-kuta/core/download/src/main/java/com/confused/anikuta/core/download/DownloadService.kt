package com.confused.anikuta.core.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * The service handles these intent actions:
 *  - [ACTION_PAUSE_ALL] → `manager.pauseAll()`.
 *  - [ACTION_CANCEL_ALL] → `manager.cancelAll()`.
 *  - [ACTION_PAUSE_TASK] (+ [EXTRA_TASK_ID]) → `manager.pauseDownload(id)` —
 *    ROUND 121: the summary notification's SINGLE action (pause the download
 *    it shows). The notification's old Pause-all/Cancel-all pair is retired
 *    per the device round: "there should only be one single button there
 *    which can be used to cancel the current download or pause the current
 *    download, not the all downloads".
 *  - [ACTION_RETRY] (+ [EXTRA_TASK_ID]) → `manager.retryDownload(id)` — the
 *    error notification's Retry button (round 120: the notification used to
 *    be a dead end — tap opened the app, nothing else could restart the task).
 */
class DownloadService : Service(), KoinComponent {

    private val manager by inject<DownloadManager>()
    private val notifier by inject<DownloadNotificationManager>()
    private val queue by inject<DownloadQueue>()

    /** REVIEW-5 M22: heavy work on Dispatchers.IO; only notify() needs Dispatchers.Main. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isForeground = false
    private var queueCollector: Job? = null

    /**
     * D.7: NetworkCallback for auto-pause on metered + auto-resume on network return.
     *
     * Registered in [onCreate] (when the service starts). Unregistered in [onDestroy].
     * Calls [DownloadQueue.onNetworkChanged] which:
     *  - Pauses all DOWNLOADING/RETRYING tasks if network is lost OR if `wifiOnly`
     *    is ON and the active network is metered.
     *  - Resumes (via `tryStartNext`) when network returns and is allowed.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            DownloadLogger.i { "Network available — checking if downloads should resume" }
            evaluateAndNotify(isAvailable = true, eventNetwork = network)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            DownloadLogger.i { "Network lost — pausing active downloads" }
            evaluateAndNotify(isAvailable = false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, capabilities)
            // Re-evaluate on every capability change (e.g. Wi-Fi → mobile switch).
            DownloadLogger.d {
                "Network capabilities changed — re-evaluating (wifi=${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)})"
            }
            evaluateAndNotify(isAvailable = true, eventNetwork = network, eventCapabilities = capabilities)
        }

        /**
         * ROUND 121: the EVENT network's own capabilities win. The old code
         * read `cm.activeNetwork`, which can still point at the DYING network
         * when onAvailable fires for the new one (the callback fires before
         * the system flips the active network) — so every "network restored"
         * event was evaluated against a dead network and read as offline.
         * That is exactly why auto-resume never fired on the device round.
         * The active network is only the LAST fallback now.
         *
         * ROUND 121-fix: NOT private — onCreate's self-healing sweep calls it
         * through the property, and private members of an ANONYMOUS object are
         * not reachable from the enclosing class (run 40023420647).
         */
        fun evaluateAndNotify(
            isAvailable: Boolean,
            eventNetwork: Network? = null,
            eventCapabilities: NetworkCapabilities? = null,
        ) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                queue.onNetworkChanged(isWifi = false, hasInternet = isAvailable)
                return
            }
            val caps = eventCapabilities
                ?: eventNetwork?.let { cm.getNetworkCapabilities(it) }
                ?: cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            val hasInternet = isAvailable && caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            queue.onNetworkChanged(isWifi = isWifi, hasInternet = hasInternet)
        }
    }

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
                // ROUND 121: waiting tasks ride the SAME notification path —
                // they get a "Waiting for network" summary (the old code kept
                // the service alive on their behalf but left the eternal
                // "Preparing downloads…" placeholder on screen).
                val waiting = tasks.filter {
                    it.status == DownloadStatus.WAITING_FOR_NETWORK ||
                        // ROUND 122 (R9): network-class ERRORs keep the service
                        // (and its NetworkCallback) alive too — an ERROR-only
                        // queue used to stop the service, killing the callback
                        // that heals those tasks on network return.
                        (it.status == DownloadStatus.ERROR && isNetworkClassError(it.lastError))
                }
                if (active.isEmpty() && waiting.isEmpty()) {
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
                    val notification = notifier.buildSummaryNotification(active, waiting)
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

        // D.7: register the NetworkCallback for auto-pause/resume.
        registerNetworkCallback()

        // ROUND 121 (self-healing sweep): evaluate connectivity ONCE at service
        // start. onAvailable only fires on TRANSITIONS — a service started while
        // already online would otherwise never learn the network is back, and
        // WAITING_FOR_NETWORK tasks persisted across a process death (offline →
        // killed → reopened online) would sit waiting forever. This one
        // evaluation heals both.
        networkCallback.evaluateAndNotify(isAvailable = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // REVIEW-5 M20: SYNCHRONOUSLY start foreground with a placeholder notification
        // BEFORE any coroutine work. Satisfies the Android 12+ 5-second contract
        // regardless of queue state. Pattern copied from ExtensionInstallService.
        if (!isForeground) {
            startForegroundCompat(notifier.buildPlaceholderNotification())
            isForeground = true
        }

        // ROUND 120 (D-748): intent handling used to `runBlocking` the queue
        // operations on the MAIN thread — a pauseAll during a slow SAF/DB write
        // froze the UI (and the notification action with it). The operations
        // now run detached on the IO scope. NonCancellable matters: a
        // pause-all can itself empty the queue, which makes the collector above
        // stop this service and cancel the scope mid-write — the detached job
        // still finishes persisting every task's PAUSED state.
        when (intent?.action) {
            ACTION_PAUSE_ALL -> launchDetached { manager.pauseAll() }
            ACTION_CANCEL_ALL -> launchDetached { manager.cancelAll() }
            // ROUND 121: the summary notification's single action — pauses the
            // download the notification is ABOUT (see the class doc).
            ACTION_PAUSE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0) {
                    DownloadLogger.i { "Pause action for task $taskId" }
                    launchDetached { manager.pauseDownload(taskId) }
                }
            }
            ACTION_RETRY -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId > 0) {
                    DownloadLogger.i { "Retry action for task $taskId" }
                    // Round 120 (D-748): the tapped notification goes away
                    // immediately — a retried task's state moves to the
                    // summary notification; a lingering error entry with a
                    // now-dead Retry button would lie.
                    runCatching { notifier.cancelErrorNotification(taskId) }
                    launchDetached { manager.retryDownload(taskId) }
                }
            }
        }
        return START_STICKY
    }

    /**
     * Runs [block] on the IO scope OUTSIDE the service's cancellation tree —
     * the job survives `onDestroy`'s `scope.cancel()` (round 120, see
     * [onStartCommand]).
     */
    private fun launchDetached(block: suspend () -> Unit) {
        scope.launch(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            runCatching { block() }
                .onFailure { DownloadLogger.e(it) { "detached service action failed" } }
        }
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
        // ROUND 120 (D-748): was `runBlocking { manager.pauseAll() }` — a main
        // thread block inside the timeout path. Detached + NonCancellable so
        // the pause finishes even though this method immediately stops the
        // service (and onDestroy cancels the scope).
        launchDetached { manager.pauseAll() }
        notifier.notifyTimeLimitReached()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        queueCollector?.cancel()
        unregisterNetworkCallback()
        scope.cancel()
        super.onDestroy()
    }

    /** D.7: Registers the [networkCallback] for auto-pause/resume on network changes. */
    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            DownloadLogger.i { "NetworkCallback registered — auto-pause/resume active" }
        } catch (e: Exception) {
            DownloadLogger.e(e) { "Failed to register NetworkCallback — auto-pause/resume disabled" }
        }
    }

    /** D.7: Unregisters the [networkCallback]. */
    private fun unregisterNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Best-effort — ignore (callback may not have been registered).
        }
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

        /** Round 120: intent action — retry ONE failed task (error notification). */
        const val ACTION_RETRY = "com.confused.anikuta.download.RETRY"

        /** Round 121: intent action — pause ONE task (the summary notification's single action). */
        const val ACTION_PAUSE_TASK = "com.confused.anikuta.download.PAUSE_TASK"

        /** Round 120: the task id extra for [ACTION_RETRY] + [ACTION_PAUSE_TASK]. */
        const val EXTRA_TASK_ID = "task_id"

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
