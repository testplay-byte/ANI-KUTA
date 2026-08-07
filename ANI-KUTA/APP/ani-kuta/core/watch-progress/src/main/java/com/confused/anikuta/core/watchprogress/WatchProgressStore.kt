package com.confused.anikuta.core.watchprogress

import kotlinx.coroutines.flow.Flow

/**
 * Contract for storing and retrieving watch progress.
 *
 * Writer: `:core:player` (via this interface — C3 layering fix).
 * Reader: Phase 4 `:feature:anime-history:impl` + `:feature:anime-library:impl`.
 * Impl: Phase 4 `:data:history` (writes to `watch_progress` table via `:core:database`).
 *
 * Phase 3a: a simple impl is provided in `:core:activity-tracker` for now.
 * Phase 4: the real impl moves to `:data:history`.
 */
interface WatchProgressStore {

    /**
     * Save watch progress for an episode. Called by the player (batched every 30s).
     * Uses UPSERT — if the episode already has progress, it's updated.
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
     * Get recently watched episodes (for "Continue Watching" section).
     * Returns up to [limit] episodes, ordered by last watched time (most recent first).
     */
    fun observeRecent(limit: Int = 20): Flow<List<WatchProgress>>

    /**
     * Mark an episode as completed.
     */
    suspend fun markCompleted(episodeKey: String)

    /**
     * Delete watch progress for an episode (when removed from library).
     */
    suspend fun delete(episodeKey: String)
}
