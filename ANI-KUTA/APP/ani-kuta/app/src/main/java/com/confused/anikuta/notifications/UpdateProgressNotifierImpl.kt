package com.confused.anikuta.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.UpdateProgressNotifier
import java.util.concurrent.atomic.AtomicLong

/**
 * Task 63 (round 23 — C): the notification-bar STATUS of background work.
 *
 * The user's spec: "whenever it is doing some actions or performing some
 * processing (for example, it is searching for new released episodes) it
 * should display the stats in the notification bar … it is testing this
 * anime for its released episodes … so that I am not in the blind. I know
 * what the application is trying to do, how it is trying to do it, and
 * when it is trying to do it."
 *
 * Implements [UpdateProgressNotifier] (the interface lives in `:core:updates`
 * to keep that module dependency-free of `:core:notifications` — the same
 * seam [com.confused.anikuta.core.updates.NotificationSender] uses) and is
 * wired via Koin in `AnikutaApp.appModule`.
 *
 * Design:
 *  - ONE notification ID for everything — a new check run REPLACES the
 *    previous state, so the shade never piles up (hourly periodic checks
 *    show exactly one live status entry).
 *  - The channel is IMPORTANCE_LOW + silent + no badge: this is a STATUS
 *    line, not an alert. Actual NEW episodes still alert through the
 *    dedicated `anikuta_new_episodes` channel.
 *  - Posts are throttled (≥ [MIN_POST_INTERVAL_MS] apart; the first and the
 *    terminal states always post) — the engine emits per anime, which can
 *    be hundreds per run.
 *  - [onProgress] is called from the engine's IO worker threads: all
 *    methods are non-suspend and only touch the notification service
 *    (a fast binder call), safe off the main thread.
 *  - POST_NOTIFICATIONS denial is soft-handled (SecurityException → log +
 *    no-op), matching DownloadNotificationManager's notify pattern.
 *  - ponytail: no cover thumbnail in the status line — the anime TITLE is
 *    the information the user asked for; a large-icon network fetch from
 *    the engine's worker threads is not worth the churn. Upgrade path:
 *    an async large-icon update like DownloadNotificationManager.loadThumbnail.
 *
 * CORE_RULES §20: logged with tag "Anikuta:App:UpdateStatus".
 */
class UpdateProgressNotifierImpl(
    private val context: Context,
) : UpdateProgressNotifier {

    companion object {
        private const val TAG = "Anikuta:App:UpdateStatus"

        /** One silent status channel for all background-check progress. */
        private const val CHANNEL_ID = "anikuta_update_progress"

        /** ONE id — every state replaces the previous one (no shade pile-up). */
        private const val NOTIFICATION_ID = 9101

        /** The engine can emit per anime; posts are throttled to this rate. */
        private const val MIN_POST_INTERVAL_MS = 500L
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    /** Last wall-clock time a progress notification was posted (ms). */
    private val lastPostAt = AtomicLong(0L)

    init {
        ensureChannel()
    }

    override fun onCheckStart(total: Int) {
        post(
            builder()
                .setContentTitle("Checking for new episodes")
                .setContentText(if (total == 1) "1 anime to check" else "$total anime to check")
                .setProgress(total, 0, false)
                .setOngoing(true)
        )
    }

    override fun onProgress(current: Int, total: Int, title: String) {
        // Throttle: first tick, terminal tick, and ≥500ms deltas post; the
        // in-between ticks are dropped (the engine emits per anime — a full
        // library check can emit hundreds of these).
        val now = System.currentTimeMillis()
        val last = lastPostAt.get()
        val isTerminal = current >= total
        if (!isTerminal && now - last < MIN_POST_INTERVAL_MS) return
        if (!lastPostAt.compareAndSet(last, now) && !isTerminal) return

        val text = if (title.isBlank() || title == "Unknown") {
            "$current of $total"
        } else {
            "Checking \"$title\" — $current of $total"
        }
        post(
            builder()
                .setContentTitle("Checking for new episodes")
                .setContentText(text)
                .setProgress(total, current.coerceAtMost(total), false)
                .setOngoing(true)
        )
    }

    override fun onFinish(totalChecked: Int, newEpisodes: Int) {
        val text = when {
            newEpisodes <= 0 -> "No new episodes found"
            newEpisodes == 1 -> "1 new episode found"
            else -> "$newEpisodes new episodes found"
        }
        post(
            builder()
                .setContentTitle("Episode check complete")
                .setContentText(
                    if (totalChecked > 0) "$text — $totalChecked anime checked" else text
                )
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
        )
    }

    override fun onFailed(message: String) {
        post(
            builder()
                .setContentTitle("Episode check stopped")
                .setContentText(
                    if (message.isBlank()) "The background check did not finish"
                    else "The background check did not finish ($message)"
                )
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
        )
    }

    // ── internals ───────────────────────────────────────────────────────────

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            // The codebase's status-icon precedent (NotificationManager uses
            // ic_dialog_info; downloads use stat_sys_download). The sync icon
            // reads as "checking/refresh in progress".
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /** SecurityException-safe notify (POST_NOTIFICATIONS may be denied). */
    private fun post(builder: NotificationCompat.Builder) {
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Logger.i(TAG) { "Progress notification skipped (permission denied)" }
        } catch (e: Exception) {
            Logger.w(TAG) { "Progress notification failed (non-fatal): ${e.message}" }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Update checks",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Status of background checks for new episodes (silent)"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }
}
