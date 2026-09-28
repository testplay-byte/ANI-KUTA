package com.confused.anikuta.core.trackeranilist

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * ROUND 102 (WS-E — the tracking contract): the user's per-content tracking
 * INTENT, persisted in `content_tracking_state`.
 *
 * THE CONTRACT this repository backs:
 * NOTHING syncs from the app to a tracker unless the user explicitly tracked
 * the content. Linking a content to AniList (auto or manual) is a DATA-SOURCE
 * decision — it never implies tracking. The user opts in per content via:
 *  - the TrackSheet's **Save** button (configuring + saving = tracking now), or
 *  - any other explicit "track this" surface built later.
 * They opt out via the TrackSheet's **Remove from Tracking** (which unlinks
 * the tracking between AniList and the app — the remote AniList entry is
 * KEPT; deleting it entirely is the separate trash-can action).
 *
 * WHY A SEPARATE TABLE (not a track_entry column): `track_entry` is a CACHE
 * of the remote entry — its rows appear and disappear with remote syncs, so
 * its presence can't carry user intent. A dedicated (main_id, tracker_type)
 * row exists exactly when the user has made a tracking decision, survives
 * delete-from-AniList cycles (tracked = 0 rows are kept deliberately), and
 * observes reactively for the details page's "Tracking now / Not tracking"
 * label. See track.sq's header comment for the full rationale.
 *
 * Multi-tracker-ready: every API takes [TrackerType] (AniList today —
 * MAL/Shikimori later ride the same table + this repository unchanged).
 */
class TrackingStateRepository(
    private val database: AnikutaDatabase,
) {

    /**
     * The one-shot intent read. `null` = no decision recorded (the effective
     * value is NOT tracked — the contract's default).
     */
    suspend fun isTracked(
        mainId: String,
        trackerType: TrackerType = TrackerType.ANILIST,
    ): Boolean = withContext(Dispatchers.IO) {
        database.trackQueries
            .getTrackingState(mainId, trackerType.id)
            .executeAsOneOrNull()
            ?.tracked == 1L
    }

    /**
     * The reactive intent read — re-emits whenever the row (or table)
     * changes. Feeds the details page's three-dot menu label + the
     * TrackSheet's state.
     */
    fun observeIsTracked(
        mainId: String,
        trackerType: TrackerType = TrackerType.ANILIST,
    ): Flow<Boolean> {
        return database.trackQueries
            .observeTrackingState(mainId, trackerType.id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.tracked == 1L }
    }

    /**
     * Records the user's tracking decision (the ONLY writer of intent).
     * `tracked = true` = "Tracking now"; `tracked = false` = "Not tracking"
     * (kept as a row so the decision is remembered across cycles).
     */
    suspend fun setTracked(
        mainId: String,
        tracked: Boolean,
        trackerType: TrackerType = TrackerType.ANILIST,
    ) = withContext(Dispatchers.IO) {
        database.trackQueries.upsertTrackingState(
            main_id = mainId,
            tracker_type = trackerType.id,
            tracked = if (tracked) 1L else 0L,
            updated_at = System.currentTimeMillis(),
        )
        Logger.d(TAG) {
            "setTracked — mainId=$mainId, tracker=$trackerType, tracked=$tracked"
        }
    }

    /**
     * Every currently-tracked content for a tracker — the sync bridge's
     * working set (only these are ever reconciled/relayed).
     */
    suspend fun getTrackedMainIds(
        trackerType: TrackerType = TrackerType.ANILIST,
    ): List<String> = withContext(Dispatchers.IO) {
        database.trackQueries
            .getTrackedMainIds(trackerType.id)
            .executeAsList()
    }

    /** Deletes the intent row entirely (library removal rides the FK cascade). */
    suspend fun delete(
        mainId: String,
        trackerType: TrackerType = TrackerType.ANILIST,
    ) = withContext(Dispatchers.IO) {
        database.trackQueries.deleteTrackingState(mainId, trackerType.id)
        Logger.d(TAG) { "delete — mainId=$mainId, tracker=$trackerType" }
    }

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:TrackingStateRepo"
    }
}
