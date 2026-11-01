package com.confused.anikuta.core.backup.di

import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupPreferences
import com.confused.anikuta.core.backup.BackupScheduler
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for the Backup & restore system (round 134, D-816). The
 * BackupManager needs the raw SqlDriver (the generic dump/restore path), the
 * SAF download folder preference, and the backup settings.
 */
val backupModule = module {
    single { BackupPreferences(get<PreferenceStore>()) }
    single {
        BackupManager(
            context = androidContext(),
            driver = get(),
            downloadPreferences = get<DownloadPreferences>(),
            backupPreferences = get(),
        )
    }
    single {
        BackupScheduler(
            context = androidContext(),
            backupPreferences = get(),
            downloadPreferences = get<DownloadPreferences>(),
        )
    }
}
