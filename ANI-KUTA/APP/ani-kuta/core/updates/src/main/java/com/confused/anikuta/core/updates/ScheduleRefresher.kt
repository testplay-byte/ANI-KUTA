package com.confused.anikuta.core.updates

/**
 * Interface for refreshing schedule data (airing times from AniList).
 *
 * D-193 Phase 9: Defined in `:core:updates` to avoid a circular dependency
 * (`:core:updates` needs to call `ScheduleEngine.fetchSchedule()` but
 * `:core:schedule` already depends on `:core:updates`). The implementation
 * lives in `:core:schedule` (ScheduleEngine implements this interface) +
 * is wired in `:app` via Koin.
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Implementations should log with their own tag.
 */
fun interface ScheduleRefresher {
    /**
     * Refresh the airing schedule for all auto-update-enabled anime.
     * Called by UpdateCheckWorker before checking for new episodes.
     */
    suspend fun fetchSchedule()
}
