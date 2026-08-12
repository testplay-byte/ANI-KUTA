package com.confused.anikuta.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger

/**
 * D-193 improvement: Posts a delayed test notification via WorkManager.
 *
 * Scheduled by [NotificationManager.postTestNotification] with a 1-minute delay.
 * Survives app death — the notification is posted even if the app is closed.
 *
 * Posts: "Jujutsu Kaisen — Episode 12 SUB" with notification ID 998.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Notifications:TestDelayed".
 */
class DelayedTestNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "Anikuta:Core:Notifications:TestDelayed"
    }

    override suspend fun doWork(): Result {
        return try {
            val koin = org.koin.core.context.GlobalContext.get()
            val notifManager = koin.get<NotificationManager>()

            // Check if notifications are still enabled (user might have turned them off).
            if (!notifManager.areNotificationsEnabled()) {
                Logger.i(TAG) { "Notifications disabled — skipping delayed test" }
                return Result.success()
            }

            notifManager.postSingleTestNotification(
                notifId = 998,
                title = "New episode available",
                text = "Jujutsu Kaisen — Episode 12 SUB",
            )
            Logger.i(TAG) { "Delayed test notification posted" }
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Delayed test notification failed: ${e.message}" }
            Result.success() // Don't retry — it's just a test.
        }
    }
}
