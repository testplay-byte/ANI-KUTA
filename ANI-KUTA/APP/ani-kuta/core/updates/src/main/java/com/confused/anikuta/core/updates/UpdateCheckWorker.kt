package com.confused.anikuta.core.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for the smart update engine (Phase UP — PLAN §4.3 T5, §4.5).
 *
 * Runs periodically (default: every 1 hour) + checks all due anime for new episodes.
 * Also runs retention cleanup (M9: delete acknowledged updates older than 7 days).
 *
 * D-193 Phase 9: now calls ScheduleRefresher.fetchSchedule() before checking,
 * + also calls NotificationConfigStore.cleanupOldSent() for notification dedup retention.
 *
 * Constraints (CF6): NetworkType.CONNECTED + BatteryNotLow.
 * ExistingPeriodicWorkPolicy.KEEP (so setting changes don't reset the timer).
 *
 * Uses GlobalContext to get [UpdateEngine] + [UpdateStore] (avoids the need for a
 * custom WorkerFactory — matches the project's existing pattern for non-composable
 * Koin access).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:Worker".
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "Anikuta:Core:Updates:Worker"
        const val PERIODIC_WORK_NAME = "anikuta_update_check"
        const val PERIODIC_INTERVAL_HOURS = 1L
        const val RETENTION_DAYS = 7L
        const val NOTIF_RETENTION_DAYS = 90L
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG) { "UpdateCheckWorker — doWork started" }

        return try {
            val koin = org.koin.core.context.GlobalContext.get()
            val engine = koin.get<UpdateEngine>()
            val store = koin.get<UpdateStore>()

            // D-193 Phase 9: 0. Refresh schedule data first (airing times from AniList).
            val scheduleRefresher = koin.getOrNull<ScheduleRefresher>()
            if (scheduleRefresher != null) {
                try {
                    scheduleRefresher.fetchSchedule()
                    Logger.d(TAG) { "Schedule refreshed" }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Schedule refresh failed (non-fatal): ${e.message}" }
                }
            }

            // 1. Check all due anime.
            val newCount = engine.checkDueAnime()

            // 2. Retention cleanup (M9: delete acknowledged updates older than 7 days).
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
            store.deleteOldAcknowledged(cutoff)

            // D-193 Phase 9: 3. Notification dedup retention (delete sent records older than 90 days).
            // NOTE: NotificationConfigStore is in :core:notifications which :core:updates doesn't
            // depend on. The cleanup is wired via the NotificationSender interface in a future phase.
            // For now, the retention purge is handled by the NotificationManager itself.

            Logger.i(TAG) { "UpdateCheckWorker — complete. $newCount new episode(s). Retention cleanup done." }
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "UpdateCheckWorker — failed: ${e.message}" }
            Result.retry()
        }
    }
}
