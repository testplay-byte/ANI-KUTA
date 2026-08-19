package com.confused.anikuta.core.updates

/**
 * Interface for updating the actual release time of an episode (Phase SC-2).
 *
 * Implemented by [com.confused.anikuta.core.schedule.ScheduleStore] in :core:schedule.
 * This interface lives in :core:updates to break the circular dependency
 * (:core:updates → :core:schedule → :core:updates). The UpdateEngine uses this
 * interface; the impl is injected via Koin (bound in :app where both modules are visible).
 */
fun interface ActualReleaseUpdater {
    /**
     * Updates the actual_at for a specific episode.
     * @param mainId The content's main_id.
     * @param episodeNumber The episode number.
     * @param actualAt The actual release timestamp (epoch millis).
     */
    fun updateActualAt(mainId: String, episodeNumber: Double, actualAt: Long)
}
