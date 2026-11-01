package com.confused.anikuta.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.confused.anikuta.core.common.Logger
import java.util.concurrent.TimeUnit

/**
 * The periodic backup worker (round 134, D-817). Runs the automatic backup
 * into the app's selected folder — LOCAL work only (no network constraint:
 * the payload is a local file copy), battery-not-low as the sole guard.
 *
 * Koin access via GlobalContext (the project's worker pattern — no custom
 * WorkerFactory). CORE_RULES §20: logged with "Anikuta:Core:Backup".
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "Anikuta:Core:Backup"
        const val PERIODIC_WORK_NAME = "anikuta_backup_periodic"
    }

    override suspend fun doWork(): Result {
        Logger.i(TAG) { "BackupWorker — periodic run started" }
        val koin = org.koin.core.context.GlobalContext.get()
        val manager = koin.get<BackupManager>()
        val preferences = koin.get<BackupPreferences>()
        return try {
            val options = BackupOptions(includeImages = preferences.includeImages.get())
            val result = manager.createBackup(options)
            result.fold(
                onSuccess = { manifest ->
                    Logger.i(TAG) { "BackupWorker: backup created (${manifest.rowCount.values.sum()} rows, ${manifest.imageCount} images)" }
                    Result.success()
                },
                onFailure = { e ->
                    // A missing/unwritable folder is the common benign case
                    // (the user's storage got disconnected) — do not retry
                    // storm; the next period tries again.
                    Logger.w(TAG, e) { "BackupWorker: backup failed (will retry next period)" }
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            Logger.e(TAG, e) { "BackupWorker: crashed" }
            Result.retry()
        }
    }
}

/**
 * Schedules/cancels the periodic backup. Called on app open + whenever the
 * user edits the Backup & restore settings. ExistingPeriodicWorkPolicy.UPDATE
 * (the D-193 lesson: REPLACE reset the periodic anchor on every app open).
 */
class BackupScheduler(
    private val context: Context,
    private val backupPreferences: BackupPreferences,
    private val downloadPreferences: com.confused.anikuta.core.download.DownloadPreferences,
) {
    private companion object {
        const val SCHEDULER_TAG = "Anikuta:Core:Backup"
    }

    /** Call on app start + on any Backup & restore setting change. */
    fun reschedule() {
        val enabled = backupPreferences.autoBackupEnabled.get()
        val folderSet = downloadPreferences.downloadFolderUri.get().isNotBlank()
        if (!enabled || !folderSet) {
            cancel()
            return
        }
        val intervalHours = backupPreferences.autoBackupIntervalHours.get()
            .coerceIn(6, 168).toLong()
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BackupWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Logger.i(SCHEDULER_TAG) { "BackupWorker scheduled: every ${intervalHours}h (UPDATE)" }
        } catch (e: Exception) {
            Logger.w(SCHEDULER_TAG, e) { "BackupScheduler: failed to schedule" }
        }
    }

    private fun cancel() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.PERIODIC_WORK_NAME)
        } catch (e: Exception) {
            Logger.w(SCHEDULER_TAG, e) { "BackupScheduler: cancel failed" }
        }
    }
}
