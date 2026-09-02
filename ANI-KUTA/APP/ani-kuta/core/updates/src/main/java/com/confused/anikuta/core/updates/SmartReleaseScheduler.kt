package com.confused.anikuta.core.updates

import android.content.Context
import com.confused.anikuta.core.common.Logger

/**
 * D-193 Phase 5 → D-391 (round 26): schedules SMART-RELEASE checks for every
 * FUTURE airing the app knows about.
 *
 * ## What this is (the Smart Update System, as the user asked to have it)
 * The periodic [UpdateCheckWorker] sweep is the SAFETY NET — it re-checks
 * everything on a fixed cadence. THIS scheduler is the PRECISION instrument:
 * for every anime whose next episode's AniList airing time is known, a
 * one-shot [SmartReleaseCheckWorker] is enqueued to fire at
 * `airingAt + learned offset` — i.e. exactly when the episode is expected to
 * actually appear on the source. The worker then CONFIRMS the episode is
 * really there (watchable) and fires the "watchable" notification.
 *
 * ## The round-26 fix
 * The old version only looked ±1h ahead (WINDOW_MS = 1h, MAX 5) and was ONLY
 * called when the periodic worker happened to run inside that window — so a
 * release 18h away was never pre-scheduled, and the "next check" countdown
 * (23h away, the periodic interval) bore no relation to it. Now:
 *  - the horizon is [HORIZON_DAYS] (7 days) — every future airing gets its
 *    one-shot as soon as the schedule data is refreshed;
 *  - the FIRST attempt lands at `airingAt + learnedOffsetMs` (the per-anime
 *    learned delay; +10min for a first-time anime), not a fixed +10min;
 *  - it is called from THREE places: [UpdateCheckWorker] (after each periodic
 *    sweep), [com.confused.anikuta.core.schedule.ScheduleEngine] (right after
 *    the schedule data refresh — the authoritative trigger, since that's when
 *    new airing times are discovered), and the manual Check Now.
 *
 * The per-episode unique work name (`smart_release_<mainId>_<ep>`) + the
 * REPLACE policy mean re-running with fresher schedule data simply re-aims
 * the one-shots — never duplicates.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Updates:SmartRelease:Scheduler".
 */
class SmartReleaseScheduler(
    private val context: Context,
    private val updateStore: UpdateStore,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Updates:SmartRelease:Scheduler"

        /** D-391: schedule one-shots for every airing within the next 7 days. */
        private const val HORIZON_DAYS = 7L

        /** D-391: WorkManager isn't infinitely scalable — cap the one-shots. */
        private const val MAX_SCHEDULED = 40

        /** D-391: the default first-check delay when no learned offset exists. */
        private const val DEFAULT_FIRST_OFFSET_MS = 10L * 60 * 1000

        /**
         * The WorkManager TAG stamped on every smart-release one-shot — the
         * update-check history page queries it to compute the REAL next check
         * time (the earliest scheduled one-shot, not the periodic interval).
         */
        const val WORK_TAG = "smart_release"
    }

    /**
     * Schedules smart-release one-shot checks for every auto-update-enabled
     * anime with a known FUTURE airing time (within [HORIZON_DAYS], capped at
     * [MAX_SCHEDULED], soonest first — the cap never drops the imminent ones).
     *
     * Each one-shot fires at `airingAt + learnedOffsetMs` (default
     * +10min for anime with no learned offset yet — the system learns the real
     * source delay from every confirmed find, see
     * [SmartReleaseCheckWorker]).
     */
    fun scheduleUpcomingChecks() {
        val now = System.currentTimeMillis()
        val horizonEnd = now + HORIZON_DAYS * 24 * 60 * 60 * 1000L

        // Every enabled anime with a known future airing (episode number + time).
        val upcoming = updateStore.getAutoUpdateEnabledAnime()
            .filter { state ->
                val airingAt = state.nextAiringAt
                airingAt != null && airingAt > now && airingAt <= horizonEnd &&
                    state.nextAiringEpisode != null && state.nextAiringEpisode > 0
            }
            .sortedBy { it.nextAiringAt!! } // soonest first — the cap keeps the imminent ones

        if (upcoming.isEmpty()) {
            Logger.d(TAG) { "No upcoming airings within $HORIZON_DAYS days — nothing to schedule" }
            return
        }

        val toSchedule = upcoming.take(MAX_SCHEDULED)
        Logger.i(TAG) {
            "Scheduling ${toSchedule.size} smart-release one-shot(s) (of ${upcoming.size} " +
                "upcoming airings within $HORIZON_DAYS days) — each at airingAt + its " +
                "learned/expected release delay"
        }

        for (state in toSchedule) {
            val airingAt = state.nextAiringAt!!
            val episodeNumber = state.nextAiringEpisode!!
            // The learned delay: how long after the AniList airing time this
            // anime's episodes actually appear on the source (learned from
            // every confirmed find). Default +10min for a first-time anime.
            val offsetMs = (state.learnedOffsetMs ?: DEFAULT_FIRST_OFFSET_MS)
                .coerceIn(0L, 24L * 60 * 60 * 1000) // sanity clamp: 0..24h
            SmartReleaseCheckWorker.schedule(
                context = context,
                mainId = state.mainId,
                episodeNumber = episodeNumber.toDouble(),
                airingAt = airingAt,
                attempt = 1,
                offsetMs = offsetMs,
            )
        }
    }
}
