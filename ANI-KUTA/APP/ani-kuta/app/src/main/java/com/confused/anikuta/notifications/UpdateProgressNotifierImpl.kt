package com.confused.anikuta.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.updates.MAX_NOTIFICATION_ITEM_LINES
import com.confused.anikuta.core.updates.UpdateCheckSummary
import com.confused.anikuta.core.updates.UpdateProgressNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Task 64 (round 24 — the update-check LIVE status notification):
 * the :app implementation of [UpdateProgressNotifier].
 *
 * D-388 (round 25 — the FULL update-notifications module rework). The
 * round-25 device report drove every change here:
 *  1. "It did not give me any sound at all" → the FINISH/FAIL notification
 *     now posts on a NEW results channel at IMPORTANCE_DEFAULT (sound +
 *     vibration). The LIVE progress feed stays on the silent LOW channel —
 *     a progress indicator should never buzz, but the RESULT should (a
 *     distinct channel also lets the user tune them separately in system
 *     settings).
 *  2. "It did not tell me the name of the anime… what it was searching
 *     for… the next details, like what it will do next" → onFinish now
 *     renders a BigTextStyle notification with PER-ANIME lines (title +
 *     outcome + the engine's next action for that anime), a summary line,
 *     and a NEXT-CHECK line ("Next check: in ~24h · Fri 3:30 PM" — the
 *     device's own 12/24-hour clock), plus the FIRST checked anime's cover
 *     as the large icon.
 *  3. Tapping the notification opens the UPDATE CHECK HISTORY page (the
 *     deep-link extra MainActivity consumes), where the full session —
 *     covers, outcomes, next actions — lives.
 *
 * Everything stays best-effort (runCatching — a SecurityException on
 * Android 13+ without POST_NOTIFICATIONS never breaks the check itself) +
 * posted only when the permission is granted.
 */
class UpdateProgressNotifierImpl(
    private val context: Context,
) : UpdateProgressNotifier {

    companion object {
        private const val TAG = "Anikuta:Notifications:UpdateProgress"

        /** The LIVE progress feed — silent by design. */
        private const val PROGRESS_CHANNEL_ID = "anikuta_update_progress"

        /**
         * D-388: the RESULTS channel — DEFAULT importance (sound + vibration).
         * A fresh channel id (not an importance bump of the old one) because
         * Android never upgrades an existing channel's importance.
         */
        private const val RESULTS_CHANNEL_ID = "anikuta_update_results"

        private const val PROGRESS_NOTIFICATION_ID = 2001
        private const val RESULTS_NOTIFICATION_ID = 2002

        /** Live updates throttle — tight enough to feel real-time. */
        private const val PROGRESS_THROTTLE_MS = 250L

        /** The history deep-link extra MainActivity consumes. */
        private const val EXTRA_OPEN_HISTORY = "open_update_history"
    }

    /** For the async cover load (the large icon) — never blocks the engine. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPostAt = 0L

    init {
        runCatching { ensureChannels() }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // The silent progress feed (unchanged from round 24).
        if (manager.getNotificationChannel(PROGRESS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    PROGRESS_CHANNEL_ID,
                    "Episode check status",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Live progress while the app checks your library for new episodes"
                    setShowBadge(false)
                },
            )
        }
        // D-388: the audible results channel.
        if (manager.getNotificationChannel(RESULTS_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    RESULTS_CHANNEL_ID,
                    "Episode check results",
                    NotificationManager.IMPORTANCE_DEFAULT, // sound + vibration
                ).apply {
                    description = "Results of an episode check — what was found and what happens next"
                    enableVibration(true)
                },
            )
        }
    }

    private fun canPost(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Posts on the SILENT progress channel (the live feed). */
    private fun postProgress(block: NotificationCompat.Builder.() -> Unit) {
        if (!canPost()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                PROGRESS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOnlyAlertOnce(true)
                    .apply(block)
                    .build(),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "Failed to post the check-status notification" }
        }
    }

    /** Posts on the AUDIBLE results channel (finish + failure). */
    private fun postResult(block: NotificationCompat.Builder.() -> Unit) {
        if (!canPost()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                RESULTS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, RESULTS_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setOnlyAlertOnce(false) // the result SHOULD alert
                    .setAutoCancel(true)
                    .setContentIntent(historyPendingIntent())
                    .apply(block)
                    .build(),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "Failed to post the check-results notification" }
        }
    }

    /** D-388: tapping a results notification opens the check history. */
    private fun historyPendingIntent(): PendingIntent? {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return null
            intent.putExtra(EXTRA_OPEN_HISTORY, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                context,
                3002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
    }

    // ── The live progress feed (silent, unchanged behavior) ──

    override fun onCheckStart(trigger: String, totalDue: Int) {
        lastPostAt = System.currentTimeMillis()
        postProgress {
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
        postProgress {
            setContentTitle("Checking for new episodes")
            setContentText("$current/$total — $title")
            setOngoing(true)
            setProgress(total, current, false)
            setWhen(System.currentTimeMillis())
        }
    }

    // ── D-388: the rich results notification ──

    override fun onFinish(summary: UpdateCheckSummary) {
        lastPostAt = 0L
        // D-410 (round 33 — the v1.1.1 publishable round): an EMPTY run is
        // SILENT. The device report: the "Episode check complete — nothing was
        // due for a check this run." notification fired on every app open; it
        // must only show when there was actually something to check.
        // `totalChecked == 0` ⇔ nothing was due (the engine's empty branch
        // builds the summary with a literal 0; a real run passes dueAnime.size
        // — and a real run notifies even when it finds zero new episodes).
        // The live progress notification (2001) only ever fires on real runs;
        // onFailed and the per-episode "new episode found" notifications are
        // a separate path and stay untouched.
        if (summary.totalChecked == 0) return
        // The cover load is async (Coil execute suspends) — post the body now,
        // then re-post with the large icon when/if it resolves. Both land on
        // the audible channel; onlyAlertOnce(false) is set per-post, but the
        // system de-dupes same-id updates that arrive back-to-back.
        postResult { applySummaryBody(summary) }
        loadCoverAsync(summary) { bitmap ->
            if (bitmap != null) {
                postResult {
                    applySummaryBody(summary)
                    setLargeIcon(bitmap)
                }
            }
        }
    }

    /**
     * Renders the summary: title, summary line, BigTextStyle with one line
     * per anime (up to [MAX_NOTIFICATION_ITEM_LINES] + "+N more") and the
     * next-check line ("what it will do next").
     */
    private fun NotificationCompat.Builder.applySummaryBody(summary: UpdateCheckSummary) {
        val found = summary.totalNewEpisodes
        setContentTitle(
            when {
                found > 0 -> "$found new episode${if (found == 1) "" else "s"} found!"
                else -> "Episode check complete"
            },
        )
        setContentText(summaryLine(summary))
        setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(bigBody(summary))
        )
        setWhen(summary.finishedAt)
    }

    /** The collapsed one-liner: "Checked 3 anime — 2 new episodes found". */
    private fun summaryLine(summary: UpdateCheckSummary): String {
        return if (summary.totalNewEpisodes > 0) {
            "Checked ${summary.totalChecked} anime — ${summary.totalNewEpisodes} new episode(s) found"
        } else {
            "Checked ${summary.totalChecked} anime — no new episodes"
        }
    }

    /** The expanded body: per-anime lines + the next-check line. */
    private fun bigBody(summary: UpdateCheckSummary): String {
        return buildString {
            if (summary.items.isEmpty()) {
                append("Nothing was due for a check this run.")
            } else {
                val shown = summary.items.take(MAX_NOTIFICATION_ITEM_LINES)
                shown.forEach { item ->
                    append(item.title)
                    append(" — ")
                    append(perItemLine(item))
                    append('\n')
                }
                if (summary.items.size > shown.size) {
                    append("+${summary.items.size - shown.size} more…\n")
                }
            }
            nextCheckLine(summary)?.let {
                append('\n')
                append(it)
            }
        }
    }

    private fun perItemLine(item: com.confused.anikuta.core.updates.UpdateCheckItemLog): String {
        return when (item.outcome) {
            "new-episodes" -> "+${item.newEpisodes} new (next: ${item.nextAction})"
            "failed", "source-unavailable" -> "${item.outcome} (${item.detail})"
            "skipped" -> "skipped"
            else -> "no new episodes (next: ${item.nextAction})"
        }
    }

    /**
     * The "what it will do next" line — e.g. "Next check: in ~18h · Fri 3:30 PM"
     * (the device's own 12/24-hour clock) or "Next check: manual — up to you".
     *
     * D-391 (round 26): [UpdateCheckSummary.nextCheckAt] is now release-aware
     * — the EARLIEST of the next smart-release check (fires exactly at the
     * next expected episode release + confirms watchability) and the periodic
     * interval. The line labels the SMART case so the "why 18h not 24h" is
     * self-explanatory on the notification itself.
     */
    private fun nextCheckLine(summary: UpdateCheckSummary): String? {
        val next = summary.nextCheckAt ?: return "Next check: manual — whenever you check"
        val now = System.currentTimeMillis()
        val delta = next - now
        val inText = when {
            delta <= 0 -> "now"
            delta < 3_600_000L -> "in ~${delta / 60_000L}m"
            delta < 86_400_000L -> "in ~${delta / 3_600_000L}h"
            else -> "in ~${delta / 86_400_000L}d"
        }
        // android.text.format.DateFormat honors the DEVICE's 12/24-hour setting.
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(next))
        val dayText = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(next))
        val intervalText = summary.intervalHours?.let { " · every ${it}h" } ?: ""
        // D-391: when the next check is EARLIER than a plain interval pass
        // would be, it's a smart release check — say so (it fires at the next
        // actual expected release + confirms the episode is watchable).
        val smart = summary.intervalHours != null &&
            next < now + (summary.intervalHours ?: 24L) * 3_600_000L - 60_000L
        return if (smart) {
            "Next check: $inText · $dayText $timeText (at the next episode's expected release)"
        } else {
            "Next check: $inText · $dayText $timeText$intervalText"
        }
    }

    /** Loads a small cover bitmap for the large icon (best-effort, async). */
    private fun loadCoverAsync(
        summary: UpdateCheckSummary,
        onReady: (android.graphics.Bitmap?) -> Unit,
    ) {
        val coverUrl = summary.items.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
        if (coverUrl == null) {
            onReady(null)
            return
        }
        scope.launch {
            // The exact pattern CoverAccentColor uses (coil3 3.0.4 — the
            // result exposes a nullable image; toBitmap is the coil3 Android
            // extension).
            val bitmap = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(256, 256)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            }.getOrNull()
            onReady(bitmap)
        }
    }

    override fun onFailed(error: String) {
        lastPostAt = 0L
        postResult {
            setContentTitle("Episode check failed")
            setContentText(error)
            setStyle(NotificationCompat.BigTextStyle().bigText(error))
            setWhen(System.currentTimeMillis())
        }
    }

    /** Called by Koin on app teardown — cancels any in-flight cover load. */
    fun destroy() {
        scope.cancel()
    }
}
