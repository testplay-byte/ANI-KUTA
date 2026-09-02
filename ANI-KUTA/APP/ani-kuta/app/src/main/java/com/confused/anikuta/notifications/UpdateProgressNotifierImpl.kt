package com.confused.anikuta.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.UpdateProgressNotifier

/**
 * Task 64 (round 24 — the update-check LIVE status notification):
 * the :app implementation of [UpdateProgressNotifier].
 *
 * The round-23 attempt (reverted with its branch) showed the user the RESULT
 * only after a refresh completed — the round-24 device ask: "It should show me
 * the live status as it is refreshing and such properly… like which content
 * it checked out, the names of them, and all other stuff like that."
 *
 * Design:
 * - ONE notification id — every stage is an update of the same notification.
 * - A silent (IMPORTANCE_LOW) dedicated channel "anikuta_update_progress" —
 *   a progress indicator should never buzz, but the shade shows it updating.
 * - onCheckStart → "Checking for new episodes — N anime queued" (ongoing).
 * - onProgress → the LIVE line: "7/42 — One Piece" with a determinate
 *   progress bar, updated per content item (throttled to 250ms so a fast
 *   run does not flood the notification pipeline; the first + last always
 *   land). THIS is the live-status fix: the names stream in real time.
 * - onFinish → autoCancel "Episode check complete — M new episodes" /
 *   "no new episodes".
 * - onFailed → autoCancel "Episode check failed".
 * - Everything is best-effort (runCatching — a SecurityException on Android
 *   13+ without POST_NOTIFICATIONS never breaks the check itself) + posted
 *   only when the permission is granted.
 */
class UpdateProgressNotifierImpl(
    private val context: Context,
) : UpdateProgressNotifier {

    companion object {
        private const val TAG = "Anikuta:Notifications:UpdateProgress"
        private const val CHANNEL_ID = "anikuta_update_progress"
        private const val NOTIFICATION_ID = 2001

        /** Live updates throttle — tight enough to feel real-time. */
        private const val PROGRESS_THROTTLE_MS = 250L
    }

    private var lastPostAt = 0L

    init {
        runCatching { ensureChannel() }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Episode check status",
                NotificationManager.IMPORTANCE_LOW, // silent — it is a progress feed
            ).apply {
                description = "Live progress while the app checks your library for new episodes"
                setShowBadge(false)
            },
        )
    }

    private fun canPost(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun post(block: NotificationCompat.Builder.() -> Unit) {
        if (!canPost()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOnlyAlertOnce(true)
                    .apply(block)
                    .build(),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "Failed to post the check-status notification" }
        }
    }

    override fun onCheckStart(trigger: String, totalDue: Int) {
        lastPostAt = System.currentTimeMillis()
        post {
            setContentTitle("Checking for new episodes")
            setContentText("$totalDue anime queued")
            setOngoing(true)
            setProgress(totalDue, 0, false)
            setWhen(System.currentTimeMillis())
        }
    }

    override fun onProgress(current: Int, total: Int, title: String) {
        // Throttle: the engine checks up to 3 anime in parallel — a fast run
        // can emit dozens of updates per second. 250ms keeps the stream LIVE
        // without flooding; the last item always lands via onFinish.
        val now = System.currentTimeMillis()
        if (now - lastPostAt < PROGRESS_THROTTLE_MS) return
        lastPostAt = now
        post {
            setContentTitle("Checking for new episodes")
            setContentText("$current/$total — $title")
            setOngoing(true)
            setProgress(total, current, false)
            setWhen(System.currentTimeMillis())
        }
    }

    override fun onFinish(totalChecked: Int, totalNew: Int) {
        lastPostAt = 0L
        post {
            setContentTitle("Episode check complete")
            setContentText(
                if (totalNew > 0) {
                    "Checked $totalChecked anime — $totalNew new episode(s) found"
                } else {
                    "Checked $totalChecked anime — no new episodes"
                },
            )
            setOngoing(false)
            setProgress(0, 0, false)
            setAutoCancel(true)
            setWhen(System.currentTimeMillis())
        }
    }

    override fun onFailed(error: String) {
        lastPostAt = 0L
        post {
            setContentTitle("Episode check failed")
            setContentText(error)
            setOngoing(false)
            setProgress(0, 0, false)
            setAutoCancel(true)
            setWhen(System.currentTimeMillis())
        }
    }
}
