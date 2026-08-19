package com.confused.anikuta.core.updates

import android.content.Context
import com.confused.anikuta.core.common.Logger
import java.util.concurrent.TimeUnit

/**
 * D-193 Phase 5: Schedules smart-release checks for anime airing within ±1h.
 *
 * Called by [UpdateCheckWorker] after the regular check completes.
 * For each anime where `next_airing_at` is within the next hour:
 * - Schedules a [SmartReleaseCheckWorker] at `airingAt + 10min`.
 * - Max 5 concurrent checks (battery optimization).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:SmartRelease:Scheduler".
 */
class SmartReleaseScheduler(
    private val context: Context,
    private val updateStore: UpdateStore,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Updates:SmartRelease:Scheduler"
        private const val WINDOW_MS = 60 * 60 * 1000L // ±1h
        private const val MAX_CONCURRENT = 5
    }

    /**
     * Check all auto-update-enabled anime for imminent airing times +
     * schedule smart-release checks for those within ±1h.
     */
    fun scheduleImminentChecks() {
        val now = System.currentTimeMillis()
        val windowStart = now
        val windowEnd = now + WINDOW_MS

        // Get all auto-update-enabled anime with next_airing_at in the window.
        val allAnime = updateStore.getAutoUpdateEnabledAnime()
        val imminent = allAnime.filter { state ->
            val airingAt = state.nextAiringAt
            airingAt != null && airingAt >= windowStart && airingAt <= windowEnd &&
                state.nextAiringEpisode != null && state.nextAiringEpisode > 0
        }

        if (imminent.isEmpty()) {
            Logger.d(TAG) { "No anime airing within ±1h" }
            return
        }

        // Limit to MAX_CONCURRENT.
        val toSchedule = imminent.take(MAX_CONCURRENT)
        Logger.i(TAG) { "Scheduling ${toSchedule.size} smart-release checks (of ${imminent.size} imminent)" }

        for (state in toSchedule) {
            val airingAt = state.nextAiringAt!!
            val episodeNumber = state.nextAiringEpisode!!
            SmartReleaseCheckWorker.schedule(
                context = context,
                mainId = state.mainId,
                episodeNumber = episodeNumber.toDouble(),
                airingAt = airingAt,
                attempt = 1,
            )
        }
    }
}
