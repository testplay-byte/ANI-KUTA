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
import com.confused.anikuta.core.notifications.R
import com.confused.anikuta.core.preferences.NotificationPreferences
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
 *     moved to a DEFAULT-importance (sound + vibration) channel.
 *  2. "It did not tell me the name of the anime… what it was searching
 *     for… the next details, like what it will do next" → onFinish renders
 *     a BigTextStyle notification with PER-ANIME lines, a summary line and
 *     a NEXT-CHECK line, plus the first checked anime's cover as the large
 *     icon.
 *  3. Tapping the notification opens the UPDATE CHECK HISTORY page (the
 *     deep-link extra MainActivity consumes).
 *
 * Task 80-b — the ONE-CHANNEL lifecycle rework (the user order: "when ANY
 * episode check starts, immediately notify me with sound — 'Searching for
 * new episode releases' — then live-update it, then finish honestly, and
 * never leave a stuck scanning card"):
 *  - ONE channel (`anikuta_update_activity`, IMPORTANCE_DEFAULT, sound +
 *    vibration, badge off) now carries the WHOLE check lifecycle. The two
 *    legacy channels are deleted: the old LOW-importance progress channel
 *    could never make a sound (channel importance is immutable after
 *    creation), so the mandated start-alert needed a fresh id. With one
 *    channel + `setOnlyAlertOnce(true)` on the live card, the run sounds
 *    exactly once at its start (the first post of id 2001 alerts; every
 *    later progress update is silent) and exactly once at its result (the
 *    first post of id 2002; the async cover re-post re-alerts no more).
 *  - [onScanStarted] posts IMMEDIATELY — the audible "searching" signal the
 *    contract requires.
 *  - The live card is NOT `ongoing` anymore: the old unswipeable flag turned
 *    the card into a ghost when the process died mid-check. Updates re-post
 *    it anyway, so a swipe mid-check self-corrects on the next emission.
 *  - `setTimeoutAfter(15 min)` auto-expires the live card if the process
 *    dies mid-check — no more stuck "scanning" notification for hours (it
 *    is re-armed on every post, so a genuinely progressing check keeps its
 *    card alive while it really is alive).
 *  - onFinish/cancel paths cancel id 2001 FIRST and unconditionally — the
 *    live card can never outlive its run — then answer honestly: the rich
 *    results card when something was checked, an explicit "caught up" card
 *    for a MANUAL run that had nothing due (the user tapped it; silence
 *    would be a lie), and NOTHING for an empty background run (D-426
 *    preserved).
 *  - [canPost] now ALSO honors the notifications MASTER toggle — with
 *    notifications "off" the user no longer gets check cards at all.
 *
 * Everything stays best-effort (runCatching — a SecurityException on
 * Android 13+ without POST_NOTIFICATIONS never breaks the check itself).
 */
class UpdateProgressNotifierImpl(
    private val context: Context,
    // Task 80-b: the notifications master toggle — checked synchronously in
    // [canPost] the same way core/notifications/NotificationManager reads
    // `preferences.notificationsEnabled` before every post.
    private val notificationPreferences: NotificationPreferences,
) : UpdateProgressNotifier {

    companion object {
        private const val TAG = "Anikuta:Notifications:UpdateProgress"

        /**
         * Task 80-b: the ONE channel for the whole check lifecycle —
         * "searching" → live progress → result/failure. IMPORTANCE_DEFAULT
         * (sound + vibration on by default) because the start signal must be
         * audible; badge off because a progress feed must not dirty the
         * launcher badge.
         */
        private const val ACTIVITY_CHANNEL_ID = "anikuta_update_activity"
        private const val ACTIVITY_CHANNEL_NAME = "Update checks"

        /** Task 80-b: the two retired channels — deleted in [ensureChannels]. */
        private const val LEGACY_PROGRESS_CHANNEL_ID = "anikuta_update_progress"
        private const val LEGACY_RESULTS_CHANNEL_ID = "anikuta_update_results"

        private const val PROGRESS_NOTIFICATION_ID = 2001
        private const val RESULTS_NOTIFICATION_ID = 2002

        /** Live updates throttle — tight enough to feel real-time. */
        private const val PROGRESS_THROTTLE_MS = 250L

        /**
         * Task 80-b: the live card auto-expires 15 minutes after its last
         * update — if the process dies mid-check (and with it every caller
         * that would finish the card), Android removes the card itself
         * instead of leaving a stuck "scanning" notification for hours.
         */
        private const val SCAN_TIMEOUT_MS = 15L * 60L * 1000L

        /** Task 80-b: sensible cap for the failure text on the failure card. */
        private const val MAX_FAILURE_TEXT_CHARS = 120

        /** The history deep-link extra MainActivity consumes (the failure card keeps it). */
        private const val EXTRA_OPEN_HISTORY = "open_update_history"
        /** Round 143: the RESULT card taps into the UPDATES screen (the found list). */
        private const val EXTRA_OPEN_UPDATES = "open_updates"
    }

    /** For the async cover load (the large icon) — never blocks the engine. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPostAt = 0L

    // Task 80-b: live-card bookkeeping. Emissions arrive serialized (the
    // engine fires them from its synchronized block), so plain @Volatile
    // fields are enough — no locks needed.
    /** How many items have COMPLETED their check (drives the determinate bar). */
    @Volatile private var completedCount = 0

    /** The last STARTED item's title — kept in the text so the card names the anime currently in flight. */
    @Volatile private var lastItemTitle = ""

    init {
        runCatching { ensureChannels() }
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Task 80-b: the single activity channel. Created BEFORE the legacy
        // deletions so a notification posted between the two steps still has
        // a home (the same order core/notifications/NotificationManager uses
        // when retiring its legacy channel).
        if (manager.getNotificationChannel(ACTIVITY_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ACTIVITY_CHANNEL_ID,
                    ACTIVITY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT, // sound + vibration
                ).apply {
                    description =
                        "The app checking your library for new episodes — searching, progress and results"
                    enableVibration(true)
                    setShowBadge(false)
                },
            )
        }
        // Task 80-b: delete BOTH legacy channels — the silent LOW progress
        // channel (importance is immutable, so it could never carry the
        // mandated start sound) and the old separate results channel (one
        // channel carries the whole lifecycle now). Same one-time retirement
        // pattern as core/notifications/NotificationManager.ensureChannel.
        if (manager.getNotificationChannel(LEGACY_PROGRESS_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_PROGRESS_CHANNEL_ID)
            Logger.i(TAG) { "legacy channel deleted: $LEGACY_PROGRESS_CHANNEL_ID" }
        }
        if (manager.getNotificationChannel(LEGACY_RESULTS_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_RESULTS_CHANNEL_ID)
            Logger.i(TAG) { "legacy channel deleted: $LEGACY_RESULTS_CHANNEL_ID" }
        }
    }

    private fun canPost(): Boolean {
        // Task 80-b: the notifications MASTER toggle first — with
        // notifications "off" the user opted out of ALL check cards (today
        // the "Checking…"/"complete" cards ignored the toggle). Same
        // synchronous accessor the episode-banner path uses.
        if (!notificationPreferences.notificationsEnabled) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Task 80-b: posts the LIVE check card (id 2001) on the activity channel.
     * Builder defaults shared by every lifecycle state:
     * - the app's own notification icon (the core module's ic_notification —
     *   the same branded silhouette the episode banners use);
     * - onlyAlertOnce(true) → the FIRST post of 2001 sounds (THE start
     *   signal), every silent later update does not re-alert;
     * - ongoing(false) → swipeable (the old unswipeable flag made the card a
     *   ghost when the process died; updates re-post it, so a swipe
     *   mid-check self-corrects);
     * - setTimeoutAfter → auto-expiry if the process dies mid-check (re-armed
     *   on every post, so a live check never times out while progressing);
     * - CATEGORY_PROGRESS + the history deep link + a real timestamp.
     */
    private fun postLive(block: NotificationCompat.Builder.() -> Unit) {
        if (!canPost()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                PROGRESS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, ACTIVITY_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOnlyAlertOnce(true) // first post alerts → THE start sound; updates silent
                    .setOngoing(false) // swipeable — no more unswipeable ghost card
                    .setTimeoutAfter(SCAN_TIMEOUT_MS) // auto-expires a dead process's stuck card
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(historyPendingIntent())
                    .apply(block)
                    .build(),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "Failed to post the check-activity notification" }
        }
    }

    /**
     * Posts the RESULT card (id 2002) on the activity channel. The FIRST post
     * of this id per run alerts (channel sound) — the "one sound at result".
     * Posts that must never re-alert (the async cover re-post, the quiet
     * caught-up card) set onlyAlertOnce(true) themselves.
     */
    private fun postResult(
        // Round 143: the RESULT card opens the UPDATES screen (the user's tap
        // destination); the failure card keeps the check-history deep link.
        openUpdatesOnTap: Boolean = false,
        block: NotificationCompat.Builder.() -> Unit,
    ) {
        if (!canPost()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(
                RESULTS_NOTIFICATION_ID,
                NotificationCompat.Builder(context, ACTIVITY_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOnlyAlertOnce(false) // the result SHOULD alert (once — see the re-post)
                    .setAutoCancel(true)
                    .setContentIntent(if (openUpdatesOnTap) updatesPendingIntent() else historyPendingIntent())
                    .apply(block)
                    .build(),
            )
        }.onFailure { t ->
            Logger.w(TAG, t) { "Failed to post the check-results notification" }
        }
    }

    /** Task 80-b: cancels the live card — unconditionally (no canPost gate:
     *  removing a notification needs no permission and must always happen). */
    private fun cancelLive() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        }
    }

    /** Round 143: the result card opens the UPDATES screen (the found-episodes list). */
    private fun updatesPendingIntent(): PendingIntent? {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return null
            intent.putExtra(EXTRA_OPEN_UPDATES, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(
                context,
                3003,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
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

    // ── Task 80-b: the live check lifecycle (one audible start, silent live updates) ──

    override fun onScanStarted(trigger: String) {
        // The audible start signal — posted IMMEDIATELY, before the engine
        // even knows how much is due (the contract: alert once, "searching").
        postLive {
            setContentTitle("Searching for new episodes")
            setContentText("Looking for new episode releases…")
            setProgress(0, 0, true) // indeterminate — the due count isn't known yet
        }
    }

    override fun onCheckStart(trigger: String, totalDue: Int) {
        // Task 80-b: fresh run state — the bar restarts at 0.
        completedCount = 0
        lastItemTitle = ""
        lastPostAt = System.currentTimeMillis()
        postLive {
            setContentTitle("Checking for new episodes")
            setContentText("$totalDue anime to check")
            setProgress(totalDue, 0, false) // determinate from here on
        }
    }

    override fun onItemStarted(current: Int, total: Int, title: String) {
        // Store the title BEFORE the throttle check: even a throttled event
        // must advance what the NEXT posted update names.
        lastItemTitle = title
        // Throttle: the engine checks up to 3 anime in parallel — a fast run
        // can emit dozens of updates per second. 250ms keeps the stream LIVE
        // without flooding; the last item always lands via onFinish.
        val now = System.currentTimeMillis()
        if (now - lastPostAt < PROGRESS_THROTTLE_MS) return
        lastPostAt = now
        postLive {
            setContentTitle("Checking for new episodes")
            setContentText("Checking $current of $total · $title")
            // Bar = COMPLETED count (not `current` — items start before they finish).
            setProgress(total, completedCount, false)
        }
    }

    override fun onItemCompleted(current: Int, total: Int, title: String, newEpisodes: Int) {
        // Task 80-b: the bar ticks on COMPLETION; the text keeps naming the
        // last STARTED item so the card always shows what is in flight now.
        // Task 80-c review fix: `current` is the engine's STARTED-so-far
        // counter (it increments at item claim under 3-way parallelism, so it
        // reaches `total` while the last items are still in flight) — driving
        // the completed bar from it overcounted. Completed items are counted
        // locally instead; emissions are serialized by the engine's
        // synchronized block, so a plain increment is race-free.
        completedCount += 1
        val now = System.currentTimeMillis()
        if (now - lastPostAt < PROGRESS_THROTTLE_MS) return
        lastPostAt = now
        postLive {
            setContentTitle("Checking for new episodes")
            setContentText("Checking $completedCount of $total · $lastItemTitle")
            setProgress(total, completedCount, false)
        }
    }

    // ── D-388: the rich results notification (+ Task 80-b honesty paths) ──

    override fun onFinish(summary: UpdateCheckSummary) {
        lastPostAt = 0L
        // Task 80-b: FIRST + UNCONDITIONAL — the live card must NEVER outlive
        // the check, whatever the outcome below does. (Cancel needs no
        // permission, so it deliberately bypasses canPost.)
        cancelLive()
        // D-426 (round 37): an EMPTY run is SILENT on the background paths —
        // the periodic worker must not buzz "nothing was due" on every pass.
        // Task 80-b honesty exception: a MANUAL check is a question the user
        // explicitly asked — it must always be ANSWERED. Today it went
        // silent; now it gets the explicit caught-up card.
        if (summary.totalChecked == 0) {
            if (summary.trigger == "manual") {
                // Round 143 (the user's spec): a HEADLINE, not a report — one line, done.
                postResult(openUpdatesOnTap = true) {
                    setContentTitle("No new episodes")
                    setContentText("You're all caught up.")
                    setStyle(NotificationCompat.BigTextStyle().bigText("You're all caught up."))
                    setOnlyAlertOnce(true)
                    setWhen(summary.finishedAt)
                }
            }
            // trigger != "manual" → post nothing (D-426 background silence).
            return
        }
        // The cover load is async (Coil execute suspends) — post the body now,
        // then re-post with the large icon when/if it resolves. Round 143 (N1):
        // the anime COVER STAYS on the result card (the user's explicit keep).
        postResult(openUpdatesOnTap = true) { applySummaryBody(summary) }
        loadCoverAsync(summary) { bitmap ->
            if (bitmap != null) {
                postResult(openUpdatesOnTap = true) {
                    applySummaryBody(summary)
                    // Task 80-b: the re-post MUST NOT re-alert — today the
                    // cover re-post sounded a second time (double sound).
                    setOnlyAlertOnce(true)
                    setLargeIcon(bitmap)
                }
            }
        }
    }

    /**
     * Round 143 (the user's verdict: the raw per-anime format is NOT proper) —
     * the result notification is a HEADLINE: "N new episodes found!" or
     * "Episode check complete" + ONE line ("Checked N anime"). The per-anime
     * outcomes, failure details and the next-check projection live in the
     * in-app Update-check history page (the failure card's deep link), never
     * in the notification.
     */
    private fun NotificationCompat.Builder.applySummaryBody(summary: UpdateCheckSummary) {
        val found = summary.totalNewEpisodes
        setContentTitle(
            when {
                found > 0 -> "$found new episode${if (found == 1) "" else "s"} found!"
                else -> "Episode check complete"
            },
        )
        val body = "Checked ${summary.totalChecked} anime"
        setContentText(body)
        setStyle(NotificationCompat.BigTextStyle().bigText(body))
        setWhen(summary.finishedAt)
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
        // Task 80-b: the live card must not outlive a FAILED run either
        // (today a crash left the "scanning" card stuck with nobody to finish it).
        cancelLive()
        // Task 80-b: the engine hands "ClassName: message" — show it, but
        // capped: a 4k stack-ish message would destroy the card's layout.
        val shown = if (error.length > MAX_FAILURE_TEXT_CHARS) {
            error.take(MAX_FAILURE_TEXT_CHARS) + "…"
        } else {
            error
        }
        postResult {
            setContentTitle("Episode check failed")
            setContentText(shown)
            setStyle(NotificationCompat.BigTextStyle().bigText(shown))
            setWhen(System.currentTimeMillis())
        }
    }

    override fun onCancelled() {
        // Task 80-b: SILENT cleanup (the contract) — remove the live card,
        // post nothing, alert nothing. No throttle: cleanup must always land.
        cancelLive()
    }

    /** Called by Koin on app teardown — cancels any in-flight cover load. */
    fun destroy() {
        // Task 80-b: teardown also removes both cards — today destroy()
        // cancelled nothing for 2001, so a check alive at process death
        // could leave one last ghost behind.
        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(PROGRESS_NOTIFICATION_ID)
            nm.cancel(RESULTS_NOTIFICATION_ID)
        }
        scope.cancel()
    }
}
