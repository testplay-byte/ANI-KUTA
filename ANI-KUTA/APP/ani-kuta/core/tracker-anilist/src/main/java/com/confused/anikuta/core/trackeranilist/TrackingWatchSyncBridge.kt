package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.Tracker
import com.confused.anikuta.core.trackerapi.TrackerType
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * ROUND 102 (WS-E — the tracking contract): the watch_progress table's
 * REACTIVE RECONCILER — the root fix for the v1.1.58 report "sometimes if I
 * completely watched an anime or an episode, it would not update in the
 * AniList tracking. It would update in our application, but it would not
 * update in the AniList tracking."
 *
 * THE ROOT CAUSE IT FIXES: the player's 10-second progress saves and the 85%
 * auto-mark write ONLY to the local watch_progress table; the ONLY relay
 * call sites were the details page's manual toggles. Watching an episode IN
 * THE PLAYER marked it locally and never told AniList.
 *
 * THE DESIGN — derived-state reconciliation (deliberately NOT a queue):
 *
 * ```
 * watch_progress (ANY write: the player's saves + auto-mark, the details
 *   toggles, mark-all, the tracker→local pull)      ← the single change signal
 *      ↓  [WatchProgressStore.observeAllHighestWatchedNumbers] (debounced)
 * for each content the user TRACKS (content_tracking_state.tracked = 1):
 *      computed = the highest watched episode number (the AniList progress math)
 *      cached   = track_entry.progress (the last value AniList confirmed)
 *      computed != cached → relay via the tracker (WATCHING / COMPLETED math),
 *                           then refresh the cache on success
 * ```
 *
 * WHY DERIVED-STATE RECONCILIATION AND NOT A RETRY QUEUE: the trigger is a
 * COMPARISON of computed local state against the confirmed cache — so a
 * failed relay is inherently retried by the NEXT watch-progress write (any
 * content's write re-emits the table signal) AND by the next app start (the
 * flow's initial emission sweeps every tracked content — a sync interrupted
 * by app death self-heals). No queue table, no replay complexity, no
 * ordering hazard: the newest computed state always wins because the relay
 * reads it at send time.
 *
 * THE CONTRACT IT OBEYS (TrackSyncManager's gate, mirrored here for the
 * direct-sync path): only TRACKED contents ever relay. Linking alone never
 * syncs. The one-way philosophy is unchanged: local watch state is the
 * source of truth; AniList receives it.
 *
 * THE STATUS MATH (parity with the details page's relay): highest ≥ total
 * AND the series is FINISHED → COMPLETED; highest > 0 → WATCHING; else
 * PLAN_TO_WATCH. The explicit markSeriesAsWatched path (which forces
 * COMPLETED regardless of airing status) keeps its own direct relay — the
 * bridge may additionally relay WATCHING-shaped values first; AniList
 * upserts are idempotent, and the cache comparison prevents loops.
 *
 * MULTI-TRACKER-READY: the tracked-contents lookup and the relay run per
 * registered [Tracker] (AniList today — MAL/Shikimori later ride unchanged).
 */
class TrackingWatchSyncBridge(
    private val watchProgressStore: WatchProgressStore,
    private val contentRepository: ContentRepository,
    private val trackEntryRepository: TrackEntryRepository,
    private val trackers: List<Tracker>,
    // The opt-in gate — fails CLOSED (no repository = nothing relays), the
    // same discipline as TrackSyncManager's gate.
    private val trackingStateRepository: TrackingStateRepository? = null,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:WatchSyncBridge"

        /**
         * The debounce window: the player writes progress every 10s while
         * playing; a burst of toggles or a mark-all writes several rows in a
         * few frames. One reconciliation per quiet window is the right rate
         * (AniList rate limits are generous but not infinite).
         */
        private const val SETTLE_WINDOW_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // THE SUBSCRIPTION — the process-lifetime collector. The flow's
        // INITIAL emission doubles as the startup sweep (self-healing any
        // sync that died with the last process); every later emission is a
        // debounced watch-progress change.
        scope.launch {
            watchProgressStore.observeAllHighestWatchedNumbers()
                .debounce(SETTLE_WINDOW_MS)
                .collectLatest { highestByMainId ->
                    runCatching { reconcile(highestByMainId) }
                        .onFailure { e ->
                            Logger.e(TAG, e) { "reconcile pass failed (next pass retries): ${e.message}" }
                        }
                }
        }
        Logger.i(TAG) { "TrackingWatchSyncBridge started — observing watch_progress (settle=${SETTLE_WINDOW_MS}ms)" }
    }

    /**
     * One reconciliation pass over the tracked set. The [highestByMainId]
     * snapshot is the fresh computed state; everything else is read from the
     * repositories at pass time (the newest values always win).
     */
    private suspend fun reconcile(highestByMainId: Map<String, Int>) {
        if (highestByMainId.isEmpty()) return

        for (tracker in trackers) {
            if (!tracker.isLoggedIn()) continue

            // The tracked set — the contract's working set. Read per pass so
            // a just-flipped opt-in/out is respected immediately.
            val trackedMainIds = trackedMainIdsFor(tracker.type)
            if (trackedMainIds.isEmpty()) continue

            for (mainId in trackedMainIds) {
                val computed = highestByMainId[mainId] ?: continue

                // No AniList link → nothing to relay (the link is the
                // data-source axis; the tracking flag alone is not enough).
                val details = contentRepository.getContentDetails(mainId)
                val anilistId = details?.anilistId ?: continue

                // The comparison against what AniList last confirmed. The
                // cache row is written ONLY after a successful sync (or a
                // TrackSheet open's fetch) — so an equal value means AniList
                // already has it, and any difference (up OR down) is a real
                // pending change.
                val cached = trackEntryRepository.get(mainId, tracker.type)
                if (cached != null && cached.progress == computed) continue

                // The status math (the details page's parity).
                val totalEps = details.dataEpisodes?.toInt() ?: cached?.totalEpisodes ?: 0
                val isFinished = details.dataStatus == "FINISHED"
                val status = when {
                    totalEps > 0 && computed >= totalEps && isFinished -> TrackStatus.COMPLETED
                    computed > 0 -> TrackStatus.WATCHING
                    else -> TrackStatus.PLAN_TO_WATCH
                }

                val now = System.currentTimeMillis()
                val cachedStartedAt = cached?.startedAt
                val cachedCompletedAt = cached?.completedAt
                val entry = (cached ?: TrackEntry(
                    contentKey = mainId,
                    trackerId = anilistId,
                )).copy(
                    status = status,
                    progress = computed,
                    totalEpisodes = if (totalEps > 0) totalEps else cached?.totalEpisodes,
                    // startedAt: preserved once set; stamped on the first
                    // WATCHING/COMPLETED transition (the D-242-fix semantics).
                    startedAt = when {
                        cachedStartedAt != null && cachedStartedAt > 0 -> cachedStartedAt
                        status == TrackStatus.WATCHING ||
                            status == TrackStatus.COMPLETED ||
                            status == TrackStatus.REWATCHING -> now
                        else -> null
                    },
                    // completedAt: stamped when the status first becomes
                    // COMPLETED; cleared when it stops being COMPLETED.
                    completedAt = when {
                        status == TrackStatus.COMPLETED &&
                            (cachedCompletedAt == null || cachedCompletedAt <= 0) -> now
                        status == TrackStatus.COMPLETED -> cachedCompletedAt
                        else -> null
                    },
                    updatedAt = now,
                )

                val success = runCatching { tracker.syncEntry(entry) }
                    .onFailure { e -> Logger.w(TAG) { "sync failed for $mainId (next pass retries): ${e.message}" } }
                    .getOrDefault(false)
                if (success) {
                    // The cache update AFTER a successful sync is what makes
                    // the comparison (and thus the retry) work: the confirmed
                    // value becomes the new baseline.
                    trackEntryRepository.upsert(entry, tracker.type)
                    Logger.i(TAG) {
                        "relayed $mainId → ${tracker.displayName}: progress=$computed, status=$status"
                    }
                }
            }
        }
    }

    /** The tracked-set read (the contract's working set). Fails closed. */
    private suspend fun trackedMainIdsFor(type: TrackerType): List<String> {
        return trackingStateRepository?.getTrackedMainIds(type) ?: emptyList()
    }
}
