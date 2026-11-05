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
 *
 * ROUND 127 (D-789): [WatchPreferences] is an interface now (JVM-testable
 * store); the production binding is [DefaultWatchPreferences].
 */
val watchProgressModule = module {
    single<WatchProgressStore> {
        SqlDelightWatchProgressStore(
            database = get(),
            preferencesStore = get<PreferenceStore>(),
        )
    }
    single<WatchPreferences> { DefaultWatchPreferences(get<PreferenceStore>()) }
    // ROUND 137 (D-830): the per-day watch-session ledger writer — both player
    // stacks tick it at their save points; the Profile page reads its table.
    // FACTORY (not single): the recorder carries a per-caller delta baseline
    // (last episode key + last session-played ms) — two players sharing one
    // instance would rebaseline each other's ticks and drop their deltas.
    factory { WatchSessionRecorder(database = get()) }
}
