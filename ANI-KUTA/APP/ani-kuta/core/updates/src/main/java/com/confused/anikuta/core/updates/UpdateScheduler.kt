package com.confused.anikuta.core.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.UpdateMode
import com.confused.anikuta.core.preferences.UpdatePreferences
import java.util.concurrent.TimeUnit

/**
 * Schedules / cancels the [UpdateCheckWorker] based on user preferences (D-193 Phase 4).
 *
 * - When `update_mode = AUTO` or `MANUAL`: schedules a periodic worker at the configured interval.
 * - When `update_mode = OFF`: cancels the worker entirely.
 * - When the interval changes: re-enqueues with `ExistingPeriodicWorkPolicy.REPLACE`.
 *
 * Called from:
 * - `AnikutaApp.onCreate()` (initial schedule on app start)
 * - `UpdatesSettingsScreen` (when the user changes the mode or interval)
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:Scheduler".
 */
class UpdateScheduler(
    private val context: Context,
    private val preferences: UpdatePreferences,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Updates:Scheduler"
    }

    /**
     * Read the current preferences + schedule/cancel the worker accordingly.
     * Call this on app start + whenever the user changes update settings.
     */
    fun reschedule() {
        val mode = preferences.getMode()
        val intervalHours = preferences.getIntervalHours()

        if (mode == UpdateMode.OFF) {
            cancel()
            return
        }

        schedule(intervalHours)
    }

    /**
     * Schedule the periodic worker at the given interval.
     * Uses `ExistingPeriodicWorkPolicy.REPLACE` so changing the interval takes effect immediately.
     */
    private fun schedule(intervalHours: Long) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                intervalHours,
                TimeUnit.HOURS,
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UpdateCheckWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )

            Logger.i(TAG) { "UpdateCheckWorker scheduled: every ${intervalHours}h (REPLACE)" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to schedule UpdateCheckWorker" }
        }
    }

    /**
     * Cancel the periodic worker entirely (when mode = OFF).
     */
    fun cancel() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UpdateCheckWorker.PERIODIC_WORK_NAME)
            Logger.i(TAG) { "UpdateCheckWorker cancelled (mode = OFF)" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Failed to cancel UpdateCheckWorker" }
        }
    }
}
