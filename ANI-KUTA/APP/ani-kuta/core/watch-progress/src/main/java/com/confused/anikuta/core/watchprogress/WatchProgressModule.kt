package com.confused.anikuta.core.watchprogress

import com.confused.anikuta.core.preferences.PreferenceStore
import org.koin.dsl.module

/**
 * Koin module for watch-progress.
 *
 * Phase WP (PLAN §2.2): registers [SqlDelightWatchProgressStore] as the
 * [WatchProgressStore] impl. The SQLDelight impl persists to the `watch_progress`
 * table + implements the two-flag auto-mark state machine (CF1) + reads the
 * configurable threshold from [WatchPreferences].
 */
val watchProgressModule = module {
    single<WatchProgressStore> {
        SqlDelightWatchProgressStore(
            database = get(),
            preferencesStore = get<PreferenceStore>(),
        )
    }
    single { WatchPreferences(get<PreferenceStore>()) }
}
