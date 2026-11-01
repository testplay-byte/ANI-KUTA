package com.confused.anikuta.core.backup.di

import com.confused.anikuta.core.backup.BackupManager
import com.confused.anikuta.core.backup.BackupPreferences
import com.confused.anikuta.core.backup.BackupScheduler
import com.confused.anikuta.core.backup.ExtensionBackupBridge
import com.confused.anikuta.core.backup.OfflineImageStore
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for the Backup & restore system (round 134, D-816). The
 * BackupManager needs the raw SqlDriver (the generic dump/restore path), the
 * SAF download folder preference, and the backup settings.
 *
 * ROUND 135: the [ExtensionBackupBridge] binding is OPTIONAL
 * (`getAll<ExtensionBackupBridge>()` — empty when the app layer hasn't
 * registered its implementation, e.g. a unit-test Koin) — without it the
 * engine simply skips the extension payload instead of crashing at first
 * resolve.
 */
val backupModule = module {
    single { BackupPreferences(get<PreferenceStore>()) }
    // ROUND 136: the offline-image store (the restored library's offline
    // covers live here; the app's Coil interceptor reads it).
    single { OfflineImageStore(androidContext()) }
    single {
        val bridges = getAll<ExtensionBackupBridge>()
        BackupManager(
            context = androidContext(),
            driver = get(),
            downloadPreferences = get<DownloadPreferences>(),
            backupPreferences = get(),
            extensionBridge = bridges.firstOrNull(),
            offlineImageStore = get(),
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
