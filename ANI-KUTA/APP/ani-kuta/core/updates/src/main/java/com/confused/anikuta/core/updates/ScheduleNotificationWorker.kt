package com.confused.anikuta.core.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import java.util.concurrent.TimeUnit

/**
 * D-193 v2: Fires the "on_schedule" notification at the exact airing time.
 *
 * Previously, on_schedule fired opportunistically during a schedule refresh that
 * happened to be within ±1h of airing. This worker fires at the precise moment
 * the episode is scheduled to air — a true "airing time reached" reminder.
 *
 * Scheduled by [ScheduleEngine] when it discovers a future airing time. Uses a
 * unique work name per episode: `schedule_notif_<mainId>_<episode>`. If the
 * schedule changes (episode pushed back), the REPLACE policy reschedules it.
 *
 * The worker delegates to [NotificationSender] (if registered) to post the
 * notification. NotificationManager honors the per-anime + global config before
 * actually posting — so this worker is just the timer.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:ScheduleNotif".
 */
class ScheduleNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "Anikuta:Core:Updates:ScheduleNotif"
        const val KEY_MAIN_ID = "main_id"
        const val KEY_EPISODE_NUMBER = "episode_number"
        const val KEY_AIRING_AT = "airing_at"

        /**
         * Schedule (or reschedule) a one-shot notification at [airingAt].
         * Called by ScheduleEngine when it sees a future airing time.
         */
        fun schedule(
            context: Context,
            mainId: String,
            episodeNumber: Double,
            airingAt: Long,
        ) {
            val now = System.currentTimeMillis()
            val delayMs = (airingAt - now).coerceAtLeast(0L)
            // Don't schedule if the airing time has already passed — the
            // opportunistic path in ScheduleEngine handles recent airings.
            if (delayMs == 0L) return

            val workName = "schedule_notif_${mainId}_$episodeNumber"
            val inputData = Data.Builder()
                .putString(KEY_MAIN_ID, mainId)
                .putDouble(KEY_EPISODE_NUMBER, episodeNumber)
                .putLong(KEY_AIRING_AT, airingAt)
                .build()

            val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
                .setInputData(inputData)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )

            Logger.i(TAG) {
                "Scheduled on_schedule notification: mainId=$mainId ep=$episodeNumber " +
                    "in ${delayMs / 60000}min (at airingAt)"
            }
        }
    }

    override suspend fun doWork(): Result {
        val mainId = inputData.getString(KEY_MAIN_ID) ?: return Result.success()
        val episodeNumber = inputData.getDouble(KEY_EPISODE_NUMBER, -1.0)
        if (episodeNumber < 0) return Result.success()

        Logger.i(TAG) { "Firing on_schedule notification: mainId=$mainId ep=$episodeNumber" }

        return try {
            val koin = org.koin.core.context.GlobalContext.get()
            val notifSender = koin.getOrNull<NotificationSender>()
            if (notifSender != null) {
                notifSender.postNotification(mainId, episodeNumber, "unknown", "schedule")
            } else {
                Logger.w(TAG) { "NotificationSender not registered — can't post on_schedule" }
            }
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "ScheduleNotificationWorker failed: ${e.message}" }
            Result.success() // don't retry — the airing time has passed
        }
    }
}
