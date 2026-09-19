package com.confused.anikuta.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.notifications.NotificationManager
import org.koin.core.context.GlobalContext

/**
 * D-483: posts the SECOND test poster notification, 5 minutes after the
 * first — the staggered delivery the user expects ("one test notification
 * now and the other one 5 minutes later"). Scheduled by
 * [EpisodeNotificationTester.postRecentUpdateNotifications] with the demo
 * payload in the work input data; resolves its collaborators from Koin
 * (the same GlobalContext pattern as DelayedTestNotificationWorker).
 * Survives app death.
 */
class DelayedPosterTestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val mainId = inputData.getString(KEY_MAIN_ID) ?: return Result.success()
            val episodeNumber = inputData.getDouble(KEY_EPISODE, 1.0)
            val audioVariant = inputData.getString(KEY_VARIANT) ?: "sub"

            val koin = GlobalContext.get()
            val contentRepository = koin.get<ContentRepository>()
            val composer = koin.get<EpisodeBannerComposer>()
            val notificationManager = koin.get<NotificationManager>()

            if (!notificationManager.areNotificationsEnabled()) {
                Logger.i(TAG) { "notifications disabled — skipping the delayed test poster" }
                return Result.success()
            }

            val title = contentRepository.getMainEntryByMainId(mainId)?.title ?: "Unknown anime"
            notificationManager.postPosterNotification(
                notifId = 998,
                mainId = mainId,
                title = title,
                episodeNumber = episodeNumber,
                audioVariant = audioVariant,
            )
            Logger.i(TAG) { "the delayed (5 min) test poster posted for $title" }
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "the delayed test poster failed: ${e.message}" }
            Result.success() // Don't retry — it's just a test.
        }
    }

    companion object {
        private const val TAG = "Anikuta:App:DelayedPoster"
        const val KEY_MAIN_ID = "demo_main_id"
        const val KEY_EPISODE = "demo_episode"
        const val KEY_VARIANT = "demo_variant"
    }
}
