package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.activitytracker.ActivityTracker
import com.confused.anikuta.core.activitytracker.ActivityEventType
import com.confused.anikuta.core.common.Logger
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
 * Architecture (user's vision):
 * ```
 * User watches anime
 *      ↓
 * ActivityTracker (internal, PRIMARY)
 *      ↓ records everything (watch events, ratings, library changes)
 * TrackSyncManager
 *      ↓ formats data for each tracker
 *      ↓ relays to external trackers
 * Tracker (AniList — SECONDARY)
 *      ↓ syncs to external service
 * ```
 *
 * This manager:
 * 1. Listens to activity events from [ActivityTracker].
 * 2. When a watch-complete or rating event occurs, formats it for each tracker.
 * 3. Relays the formatted data to the external [Tracker] implementations.
 *
 * The relay is ONE-WAY: internal → external. External trackers don't write back
 * to the internal tracker (that would create conflicts). The user's local data
 * is always the source of truth.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:SyncManager".
 * CORE_RULES §23: Sync state is reactive (StateFlow).
 */
class TrackSyncManager(
    private val trackers: List<Tracker>,
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
     * Called when the internal tracker records a WATCH_COMPLETE event.
     * Formats the event for each tracker and syncs.
     *
     * @param contentKey The content that was watched.
     * @param episodeNumber The episode number.
     * @param status The watch status to relay.
     * @param score Optional score (if the user rated it).
     */
    fun relayWatchEvent(
        contentKey: String,
        episodeNumber: Double,
        status: TrackStatus,
        score: Int? = null,
    ) {
        Logger.i(TAG) { "Relaying watch event: $contentKey ep$episodeNumber → $status" }

        scope.launch {
            for (tracker in trackers) {
                if (!tracker.isLoggedIn()) {
                    Logger.d(TAG) { "${tracker.displayName} not logged in — skipping" }
                    continue
                }

                try {
                    // Format the entry for this tracker
                    val entry = TrackEntry(
                        contentKey = contentKey,
                        trackerId = 0, // TODO: Resolve contentKey → tracker ID (needs identity system)
                        status = status,
                        score = score,
                        progress = episodeNumber.toInt(),
                    )

                    val success = tracker.syncEntry(entry)
                    if (success) {
                        Logger.i(TAG) { "Synced to ${tracker.displayName}: $contentKey" }
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
     * @param contentKey The content that was rated.
     * @param score The score (0-100).
     */
    fun relayRating(contentKey: String, score: Int) {
        Logger.i(TAG) { "Relaying rating: $contentKey → $score" }
        relayWatchEvent(contentKey, 0.0, TrackStatus.WATCHING, score)
    }

    /**
     * Get all registered trackers.
     */
    fun getTrackers(): List<Tracker> = trackers

    /**
     * Get a tracker by type.
     */
    fun getTracker(type: TrackerType): Tracker? = trackers.find { it.type == type }
}
