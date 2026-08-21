package com.confused.anikuta.core.trackeranilist

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * D-242: Local cache for [TrackEntry] — one row per (mainId, trackerType).
 *
 * This is a LOCAL CACHE of the remote tracker entry (AniList MediaList).
 * The remote is the source of truth; we cache here so:
 *  - The TrackSheet opens instantly with cached data (no network wait).
 *  - The tracking state is available offline.
 *  - The details page can show "Watching" / "Completed" badges without a
 *    network round-trip.
 *
 * Write flow: `upsert` is called after every successful remote sync
 * (`AniListTracker.syncEntry` / `fetchEntry`). Read flow: the UI observes
 * `observe(mainId)` which emits whenever the row changes.
 *
 * The `trackerType` column is designed for multi-tracker extensibility
 * (AniList now, MAL/Shikimori future). Currently only [TrackerType.ANILIST]
 * is used, but the schema + API support multiple trackers per content.
 */
class TrackEntryRepository(
    private val database: AnikutaDatabase,
) {

    /**
     * Upserts (insert-or-replace) a [TrackEntry] into the local cache.
     * Called after a successful remote sync.
     */
    suspend fun upsert(entry: TrackEntry, trackerType: TrackerType = TrackerType.ANILIST) {
        withContext(Dispatchers.IO) {
            database.trackQueries.upsertTrackEntry(
                main_id = entry.contentKey,
                tracker_type = trackerType.id,
                tracker_id = entry.trackerId.toLong(),
                status = entry.status.value,
                score = entry.score?.toLong(),
                progress = entry.progress.toLong(),
                total_episodes = entry.totalEpisodes?.toLong(),
                list_id = entry.listId?.toLong(),
                started_at = entry.startedAt,
                completed_at = entry.completedAt,
                updated_at = entry.updatedAt,
            )
            Logger.d(TAG) {
                "upsert — mainId=${entry.contentKey}, tracker=$trackerType, " +
                    "status=${entry.status}, progress=${entry.progress}, score=${entry.score}"
            }
        }
    }

    /**
     * Returns the cached [TrackEntry] for (mainId, trackerType), or null.
     * Synchronous — for one-shot reads.
     */
    suspend fun get(mainId: String, trackerType: TrackerType = TrackerType.ANILIST): TrackEntry? {
        return withContext(Dispatchers.IO) {
            database.trackQueries.getTrackEntry(mainId, trackerType.id)
                .executeAsOneOrNull()
                ?.toTrackEntry()
        }
    }

    /**
     * Observes the cached [TrackEntry] for (mainId, trackerType).
     * Reactive — emits whenever the row changes (via SqlDelight triggers).
     * Used by the TrackSheet + details page badges.
     */
    fun observe(mainId: String, trackerType: TrackerType = TrackerType.ANILIST): Flow<TrackEntry?> {
        return database.trackQueries.getTrackEntry(mainId, trackerType.id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toTrackEntry() }
    }

    /**
     * Returns the cached [TrackEntry] by tracker ID (e.g. AniList anime ID).
     * Used to check if an anime already has a track entry (e.g. when the user
     * opens the details page — "is this already tracked?").
     */
    suspend fun getByTrackerId(
        trackerId: Int,
        trackerType: TrackerType = TrackerType.ANILIST,
    ): TrackEntry? {
        return withContext(Dispatchers.IO) {
            database.trackQueries.getTrackEntryByTrackerId(trackerType.id, trackerId.toLong())
                .executeAsOneOrNull()
                ?.toTrackEntry()
        }
    }

    /**
     * Deletes the cached [TrackEntry] for (mainId, trackerType).
     * Called when the user unlinks the tracker (or deletes the remote entry).
     */
    suspend fun delete(mainId: String, trackerType: TrackerType = TrackerType.ANILIST) {
        withContext(Dispatchers.IO) {
            database.trackQueries.deleteTrackEntry(mainId, trackerType.id)
            Logger.d(TAG) { "delete — mainId=$mainId, tracker=$trackerType" }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Converts a SQLDelight `Track_entry` row to a [TrackEntry]. */
    private fun com.confused.anikuta.core.database.Track_entry.toTrackEntry(): TrackEntry {
        return TrackEntry(
            contentKey = main_id,
            trackerId = tracker_id.toInt(),
            status = runCatching { TrackStatus.valueOf(status) }.getOrDefault(TrackStatus.WATCHING),
            score = score?.toInt(),
            progress = progress.toInt(),
            totalEpisodes = total_episodes?.toInt(),
            listId = list_id?.toInt(),
            startedAt = started_at,
            completedAt = completed_at,
            updatedAt = updated_at,
        )
    }

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:TrackEntryRepo"
    }
}
