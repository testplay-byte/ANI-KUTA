package com.confused.anikuta.core.trackerapi

import kotlinx.coroutines.flow.Flow

/**
 * Contract for external trackers (AniList, MAL, Shikimori).
 *
 * The app's internal tracking system (`:core:activity-tracker`) is the PRIMARY
 * tracker — it records everything. External trackers implement this interface
 * and receive data RELAYED from the internal tracker via [TrackSyncManager].
 *
 * Architecture:
 * ```
 * User watches anime
 *      ↓
 * ActivityTracker (internal, PRIMARY)
 *      ↓ records everything
 * TrackSyncManager
 *      ↓ formats data for each tracker
 * Tracker (AniList, MAL — SECONDARY)
 *      ↓ syncs to external service
 * ```
 *
 * CORE_RULES §23: All state is reactive (Flow) — login + sync state update live.
 * CORE_RULES §20: Implementations should log with tag "Anikuta:Core:Tracker:<type>".
 */
interface Tracker {

    /** Which tracker type this is. */
    val type: TrackerType

    /** Human-readable name for display. */
    val displayName: String

    // ── Authentication ──

    /** Observe the login state (reactive — CORE_RULES §23). */
    fun observeLoginState(): Flow<TrackerLoginState>

    /** Check if the user is logged in. */
    suspend fun isLoggedIn(): Boolean

    /**
     * Start the OAuth login flow.
     * @return The OAuth URL to open in a browser, or null if login failed.
     */
    suspend fun startLogin(): String?

    /**
     * Handle the OAuth callback (the redirect URL after browser login).
     * @param code The authorization code from the callback.
     * @return True if login succeeded.
     */
    suspend fun handleLoginCallback(code: String): Boolean

    /** Log out. */
    suspend fun logout()

    // ── Sync ──

    /** Observe the sync state (reactive). */
    fun observeSyncState(): Flow<TrackerSyncState>

    /**
     * Sync a track entry to the external tracker.
     * Called by [TrackSyncManager] when relaying data from the internal tracker.
     *
     * @param entry The entry to sync.
     * @return True if sync succeeded.
     */
    suspend fun syncEntry(entry: TrackEntry): Boolean

    /**
     * Fetch the current track entry for a content from the external tracker.
     * Used to merge remote + local state.
     *
     * @param trackerId The tracker's ID for this content.
     * @return The track entry, or null if not tracked.
     */
    suspend fun fetchEntry(trackerId: Int): TrackEntry?

    /**
     * Search for content on the tracker by title.
     * Used when linking an extension anime to a tracker entry.
     *
     * @param query The search query (anime title).
     * @return List of matching tracker entries.
     */
    suspend fun search(query: String): List<TrackEntry>
}
