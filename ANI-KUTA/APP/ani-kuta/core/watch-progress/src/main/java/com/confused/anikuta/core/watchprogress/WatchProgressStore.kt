package com.confused.anikuta.core.watchprogress

import kotlinx.coroutines.flow.Flow

/**
 * Contract for storing and retrieving watch progress.
 *
 * Writer: `:core:player` (via this interface — C3 layering fix).
 * Reader: `:feature:anime-history:impl` + `:feature:anime-library:impl` + `:feature:anime-details:impl`.
 * Impl: `SqlDelightWatchProgressStore` (Phase WP — persists to the `watch_progress` table).
 *
 * Phase WP (PLAN §2.2): extended with the two-flag auto-mark API
 * (`setAutoMarkSuppressed`, `setUserMarkedWatched`, `resetAutoMarkSuppressed`),
 * `observeByMainId`, `isWatched`, `clearByMainId`, `deleteAll`.
 */
interface WatchProgressStore {

    /**
     * Save watch progress for an episode. Called by the player (batched every 10s + on pause/dispose).
     * Uses UPSERT — if the episode already has progress, it's updated.
     *
     * The 85% auto-mark logic (PLAN §2.3) is applied HERE: if `progress.progressFraction`
     * exceeds the configured threshold AND `autoMarkSuppressed = false`, `completed` is
     * set to true + `completedAt` to now.
     */
    suspend fun save(episodeKey: String, progress: WatchProgress)

    /**
     * Get watch progress for a specific episode.
     * Returns null if the episode has no recorded progress.
     */
    suspend fun get(episodeKey: String): WatchProgress?

    /**
     * Observe watch progress for a specific episode (reactive — CORE_RULES §23).
     * Emits the current progress + any updates.
     */
    fun observe(episodeKey: String): Flow<WatchProgress?>

    /**
     * Observe recently watched episodes (for History + Continue Watching).
     * Returns up to [limit] episodes, ordered by last watched time (most recent first).
     */
    fun observeRecent(limit: Int = 20): Flow<List<WatchProgress>>

    /**
     * Phase WP: Observe all progress for an anime (for the details page episode list).
     * Reactive — emits on every change to any episode of this anime.
     */
    fun observeByMainId(mainId: String): Flow<List<WatchProgress>>

    /**
     * Phase WP: Observe the "Continue Watching" list — in-progress, not suppressed.
     * (UI placement deferred — PLAN §9 Q4.)
     */
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgress>>

    /**
     * Mark an episode as completed.
     */
    suspend fun markCompleted(episodeKey: String)

    /**
     * Phase WP (CF1): user manually un-marked the episode → suppress the 85% auto-mark
     * until the user watches again. Sets `autoMarkSuppressed = true`, `completed = false`,
     * `userMarkedWatched = false`.
     */
    suspend fun setAutoMarkSuppressed(episodeKey: String)

    /**
     * Phase WP (CF1): user explicitly marked the episode as watched (sticky — stays
     * watched regardless of auto-mark). Sets `userMarkedWatched = true`, `completed = true`.
     */
    suspend fun setUserMarkedWatched(episodeKey: String)

    /**
     * Phase WP (CF1): toggle the watched state. If currently watched → un-mark
     * (setAutoMarkSuppressed). If currently unwatched → mark (setUserMarkedWatched).
     * Returns the new watched state.
     */
    suspend fun toggleWatched(episodeKey: String): Boolean

    /**
     * Phase WP (CF1): reset `autoMarkSuppressed` on next play (FILE_LOADED).
     * Re-arms the 85% auto-mark. Does NOT reset `completed` (it stays 0 if the user
     * had un-marked — they must re-cross 85% to re-mark).
     */
    suspend fun resetAutoMarkSuppressed(episodeKey: String)

    /**
     * Phase WP: is this episode watched? (Derived — CF1.)
     */
    suspend fun isWatched(episodeKey: String): Boolean

    /**
     * Phase WP: Get the watched episode count for an anime (for stats).
     */
    suspend fun getWatchedEpisodeCount(mainId: String): Int

    /**
     * Phase WP: Clear all progress for an anime (for library-remove — S3).
     */
    suspend fun clearByMainId(mainId: String)

    /**
     * Delete watch progress for an episode (when removed from library).
     */
    suspend fun delete(episodeKey: String)

    /**
     * Phase WP: Clear ALL watch progress (for the History "Clear all" action).
     */
    suspend fun deleteAll()
}
