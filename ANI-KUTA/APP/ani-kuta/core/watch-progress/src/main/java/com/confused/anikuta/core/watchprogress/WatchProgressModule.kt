package com.confused.anikuta.core.watchprogress

import org.koin.dsl.module

/**
 * Koin module for watch-progress.
 *
 * Registers [InMemoryWatchProgressStore] as the [WatchProgressStore] impl.
 *
 * ponytail: in-memory impl → swap for SQLDelight-backed impl in Phase 5e
 *           when the database is wired. The interface stays the same, so
 *           only this module file changes.
 */
val watchProgressModule = module {
    single<WatchProgressStore> { InMemoryWatchProgressStore() }
}
