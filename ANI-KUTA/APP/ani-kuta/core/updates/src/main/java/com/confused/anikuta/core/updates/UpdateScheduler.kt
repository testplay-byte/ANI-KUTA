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
 * - When `update_mode = AUTO`: schedules a periodic worker at the configured interval
 *   (Task 80-c doc fix — MANUAL never schedules; it is strictly on-demand).
 * - When `update_mode = OFF`: cancels the worker entirely.
 * - When the interval changes: re-enqueued with `ExistingPeriodicWorkPolicy.UPDATE`
 *   (Task 80-a — the spec is rewritten in place; see [schedule]).
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
     *
     * D-193 v2: only AUTO mode schedules the periodic background worker. MANUAL
     * mode is strictly on-demand (the user taps Check Now). OFF cancels everything.
     */
    fun reschedule() {
        val mode = preferences.getMode()
        val intervalHours = preferences.getIntervalHours()

        if (mode != UpdateMode.AUTO) {
            cancel()
            return
        }

        schedule(intervalHours)
    }

    /**
     * Schedule the periodic worker at the given interval.
     *
     * Task 80-a: uses `ExistingPeriodicWorkPolicy.UPDATE` (WorkManager 2.10.0).
     * The old REPLACE policy cancelled + re-inserted the periodic work on
     * EVERY app open (AnikutaApp.onCreate → reschedule), resetting the
     * periodic anchor by a full interval each time — checks drifted and the
     * user's "next check" countdown lied. UPDATE rewrites the spec in place
     * while preserving the original anchor; an explicit interval change takes
     * effect at the next period boundary instead of immediately.
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
                // Task 80-a: UPDATE (was REPLACE) — REPLACE reset the periodic
                // anchor on every app open, so the real check cadence drifted
                // from the promised one. UPDATE keeps the anchor; interval
                // edits land at the next period boundary.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )

            Logger.i(TAG) { "UpdateCheckWorker scheduled: every ${intervalHours}h (UPDATE)" }
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
