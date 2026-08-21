package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.Tracker
import com.confused.anikuta.core.trackerapi.TrackerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates sync between the internal tracking system and external trackers.
 *
 * D-242: NOW FULLY WIRED. Resolves `mainId → anilistId` via [contentRepository]
 * + caches the result in [trackEntryRepository]. The `trackerId = 0` placeholder
 * is gone — every sync now uses the real AniList anime ID.
 *
 * Architecture:
 * ```
 * User marks episode watched / rates / etc.
 *      ↓
 * DetailsViewModel (calls relayWatchEvent / relayRating)
 *      ↓
 * TrackSyncManager
 *      ↓ resolves mainId → anilistId via ContentRepository
 *      ↓ formats TrackEntry for each tracker
 *      ↓ relays to external Tracker
 * Tracker (AniList — syncEntry → SaveMediaListEntry mutation)
 *      ↓
 * AniList API
 * ```
 *
 * The relay is ONE-WAY: internal → external. The user's local data is always
 * the source of truth. External trackers don't write back to the internal
 * tracker (that would create conflicts). The `fetchEntry` flow (pull from
 * AniList → update local cache) is a separate path triggered by the TrackSheet
 * "refresh" button or the details page "refresh" button.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:SyncManager".
 * CORE_RULES §23: Sync state is reactive (StateFlow).
 */
class TrackSyncManager(
    private val trackers: List<Tracker>,
    private val contentRepository: ContentRepository,
    private val trackEntryRepository: TrackEntryRepository,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:SyncManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingSyncs = MutableStateFlow<Map<String, TrackStatus>>(emptyMap())
    val pendingSyncs: StateFlow<Map<String, TrackStatus>> = _pendingSyncs.asStateFlow()

    /**
     * Relay a watch event to all logged-in external trackers.
     *
     * D-242: Resolves [contentKey] (mainId) → AniList anime ID via
     * [contentRepository.getContentDetails]. If the content has no AniList link,
     * the sync is silently skipped (the user hasn't linked the anime to AniList).
     *
     * After a successful sync, the [TrackEntryRepository] cache is updated so
     * the TrackSheet shows fresh data immediately.
     *
     * @param contentKey The content's mainId.
     * @param episodeNumber The episode number (drives `progress`).
     * @param status The watch status to relay.
     * @param score Optional score (0-100, if the user rated it).
     */
    fun relayWatchEvent(
        contentKey: String,
        episodeNumber: Double,
        status: TrackStatus,
        score: Int? = null,
    ) {
        Logger.i(TAG) { "Relaying watch event: $contentKey ep$episodeNumber → $status" }

        scope.launch {
            // D-242: resolve mainId → anilistId via ContentRepository.
            val details = contentRepository.getContentDetails(contentKey)
            val anilistId = details?.anilistId
            if (anilistId == null) {
                Logger.d(TAG) {
                    "relayWatchEvent — no AniList link for mainId=$contentKey; skipping sync"
                }
                return@launch
            }

            for (tracker in trackers) {
                if (!tracker.isLoggedIn()) {
                    Logger.d(TAG) { "${tracker.displayName} not logged in — skipping" }
                    continue
                }

                try {
                    // Fetch the cached entry (to preserve score/dates) or build a new one.
                    val cached = trackEntryRepository.get(contentKey, tracker.type)
                    val now = System.currentTimeMillis()

                    // D-242-fix: capture into local vars to enable smart-casting
                    // (can't smart-cast public API properties from a different module).
                    val cachedStartedAt = cached?.startedAt
                    val cachedCompletedAt = cached?.completedAt

                    // D-242-fix: set startedAt on the first watch transition
                    // (null/PLAN_TO_WATCH → WATCHING/COMPLETED). Preserve once set.
                    val startedAt = when {
                        cachedStartedAt != null && cachedStartedAt > 0 -> cachedStartedAt
                        status == TrackStatus.WATCHING ||
                            status == TrackStatus.COMPLETED ||
                            status == TrackStatus.PAUSED ||
                            status == TrackStatus.REWATCHING -> now
                        else -> null
                    }
                    // D-242-fix: set completedAt when status becomes COMPLETED.
                    val completedAt = when {
                        status == TrackStatus.COMPLETED &&
                            (cachedCompletedAt == null || cachedCompletedAt <= 0) -> now
                        status == TrackStatus.COMPLETED -> cachedCompletedAt
                        else -> null
                    }

                    val entry = (cached ?: TrackEntry(
                        contentKey = contentKey,
                        trackerId = anilistId,
                    )).copy(
                        status = status,
                        progress = episodeNumber.toInt(),
                        score = score ?: cached?.score,
                        totalEpisodes = details.dataEpisodes?.toInt() ?: cached?.totalEpisodes,
                        startedAt = startedAt,
                        completedAt = completedAt,
                        updatedAt = now,
                    )

                    val success = tracker.syncEntry(entry)
                    if (success) {
                        // Update the local cache so the TrackSheet shows fresh data.
                        trackEntryRepository.upsert(entry, tracker.type)
                        Logger.i(TAG) {
                            "Synced to ${tracker.displayName}: $contentKey (anilistId=$anilistId, " +
                                "progress=${entry.progress}, status=${entry.status})"
                        }
                    } else {
                        Logger.w(TAG) { "Failed to sync to ${tracker.displayName}: $contentKey" }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "Error syncing to ${tracker.displayName}: ${e.message}" }
                }
            }
        }
    }

    /**
     * Relay a rating to all logged-in external trackers.
     *
     * Preserves the existing status/progress (only updates the score).
     *
     * @param contentKey The content's mainId.
     * @param score The score (0-100, AniList-native scale).
     */
    fun relayRating(contentKey: String, score: Int) {
        Logger.i(TAG) { "Relaying rating: $contentKey → score=$score" }
        scope.launch {
            val details = contentRepository.getContentDetails(contentKey)
            val anilistId = details?.anilistId ?: return@launch

            for (tracker in trackers) {
                if (!tracker.isLoggedIn()) continue
                try {
                    val cached = trackEntryRepository.get(contentKey, tracker.type)
                    val entry = (cached ?: TrackEntry(
                        contentKey = contentKey,
                        trackerId = anilistId,
                    )).copy(
                        score = score,
                        updatedAt = System.currentTimeMillis(),
                    )
                    if (tracker.syncEntry(entry)) {
                        trackEntryRepository.upsert(entry, tracker.type)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "relayRating failed: ${e.message}" }
                }
            }
        }
    }

    /** Get all registered trackers. */
    fun getTrackers(): List<Tracker> = trackers

    /** Get a tracker by type. */
    fun getTracker(type: TrackerType): Tracker? = trackers.find { it.type == type }
}
