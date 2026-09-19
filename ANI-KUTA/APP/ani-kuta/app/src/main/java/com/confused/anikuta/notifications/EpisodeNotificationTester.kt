package com.confused.anikuta.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.updates.UpdateStore
import java.util.concurrent.TimeUnit

/**
 * D-478/D-483: the "Send test notifications" action — poster notifications
 * built from REAL content:
 *
 * 1. Feed-first: the newest rows of the episode_update feed (the contents
 *    whose episodes the checker actually found), latest two distinct.
 * 2. Library fallback: when the feed has nothing usable, pick random
 *    LIBRARY content that HAS cached episodes (via [EpisodeDemoPicker]) —
 *    the user's round-47 spec: "pick one of the contents from the user's
 *    library randomly… use the latest episode of that as the notification
 *    demo."
 *
 * Delivery is STAGGERED per the user's spec: the first posts immediately,
 * the second 5 MINUTES later via WorkManager (survives app death).
 */
class EpisodeNotificationTester(
    private val context: Context,
    private val updateStore: UpdateStore,
    private val contentRepository: ContentRepository,
    private val composer: EpisodeBannerComposer,
    private val notificationManager: NotificationManager,
    private val demoPicker: EpisodeDemoPicker,
) {

    /**
     * Posts up to [count] test poster notifications (1 now + the rest
     * staggered 5 minutes apart). Returns how many were scheduled.
     */
    suspend fun postRecentUpdateNotifications(count: Int = 2): Int {
        // The POST_NOTIFICATIONS gate (Android 13+).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w(TAG) { "test: POST_NOTIFICATIONS not granted" }
                return 0
            }
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Logger.w(TAG) { "test: notifications master toggle is OFF" }
            return 0
        }

        // ── The demo set: feed-first, then the random library fallback. ──
        val demos = mutableListOf<EpisodeDemoPicker.Demo>()
        val seen = LinkedHashSet<String>()
        updateStore.getAllUpdates(limit = 50L).forEach { row ->
            if (row.mainId !in seen) {
                seen.add(row.mainId)
                val title = contentRepository.getMainEntryByMainId(row.mainId)?.title ?: return@forEach
                demos.add(
                    EpisodeDemoPicker.Demo(
                        mainId = row.mainId,
                        title = title,
                        episodeNumber = row.episodeNumber,
                        audioVariant = row.audioVariant.ifBlank { "sub" },
                    ),
                )
            }
        }
        if (demos.size < count) {
            // Top up from the LIBRARY (random eligible content with episodes).
            demos.addAll(demoPicker.pickRandomDistinct(count - demos.size))
        }
        val trimmed = demos.take(count)
        if (trimmed.isEmpty()) {
            Logger.i(TAG) { "test: no feed rows AND no eligible library content — nothing to demo" }
            return 0
        }

        var scheduled = 0
        trimmed.forEachIndexed { index, demo ->
            val delayMinutes = index * 5L  // 1st: now · 2nd: 5 min · (3rd: 10 …)
            if (delayMinutes == 0L) {
                // The FIRST test posts immediately.
                notificationManager.postPosterNotification(
                    notifId = 990 + index,
                    mainId = demo.mainId,
                    title = demo.title,
                    episodeNumber = demo.episodeNumber,
                    audioVariant = demo.audioVariant,
                )
            } else {
                // The SECOND (and any later) test: WorkManager, 5 minutes
                // apart, surviving app death — the payload rides the work
                // data and the worker re-composes the banner at fire time.
                val request = OneTimeWorkRequestBuilder<DelayedPosterTestWorker>()
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .setInputData(
                        workDataOf(
                            DelayedPosterTestWorker.KEY_MAIN_ID to demo.mainId,
                            DelayedPosterTestWorker.KEY_EPISODE to demo.episodeNumber,
                            DelayedPosterTestWorker.KEY_VARIANT to demo.audioVariant,
                        ),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "anikuta_test_poster_delayed_$index",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
            scheduled++
            Logger.i(TAG) { "test poster #$index scheduled for ${demo.title} (delay=${delayMinutes}min)" }
        }
        return scheduled
    }
}
