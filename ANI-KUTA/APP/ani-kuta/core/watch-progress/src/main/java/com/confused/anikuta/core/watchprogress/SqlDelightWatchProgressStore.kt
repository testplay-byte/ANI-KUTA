package com.confused.anikuta.core.watchprogress

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [WatchProgressStore] implementation.
 *
 * Phase WP (PLAN §2.2): persists to the `watch_progress` table (replaces the
 * old in-memory impl which was capture-only — progress was lost on process death).
 *
 * The 85% auto-mark logic (PLAN §2.3, CF1 two-flag state machine):
 * - On `save()`: if `progress.progressFraction > threshold` (configurable, default 0.85)
 *   AND `autoMarkSuppressed = false` → set `completed = true, completedAt = now`.
 * - If the user rewound (< threshold) AND `completed = true` AND `autoMarkSuppressed = false`
 *   → keep `completed = true` (auto-unwatch NEVER happens — IM9).
 * - `setAutoMarkSuppressed()` (user un-marked): `autoMarkSuppressed = true, completed = false, userMarkedWatched = false`.
 * - `setUserMarkedWatched()` (user marked watched): `userMarkedWatched = true, completed = true`.
 * - `resetAutoMarkSuppressed()` (on FILE_LOADED): `autoMarkSuppressed = false` (does NOT reset completed).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:WatchProgress".
 */
class SqlDelightWatchProgressStore(
    private val database: AnikutaDatabase,
    preferencesStore: PreferenceStore,
) : WatchProgressStore {

    companion object {
        private const val TAG = "Anikuta:Core:WatchProgress"
        private val dispatchers = Dispatchers.IO
    }

    private val watchPreferences = WatchPreferences(preferencesStore)

    override suspend fun save(episodeKey: String, progress: WatchProgress) = withContext(dispatchers) {
        val threshold = watchPreferences.autoMarkThreshold
        val now = System.currentTimeMillis()

        // ── CF1: the 85% auto-mark state machine ──
        val existing = database.watchQueries.getWatchProgress(episodeKey).executeAsOneOrNull()
        val autoMarkSuppressed = existing?.auto_mark_suppressed?.toInt() == 1
        val userMarkedWatched = existing?.user_marked_watched?.toInt() == 1
        val wasCompleted = existing?.completed?.toInt() == 1

        // Determine the new completed state.
        // Rule: if fraction > threshold AND NOT autoMarkSuppressed → completed = true (auto-mark fires).
        // If fraction <= threshold AND was completed AND NOT autoMarkSuppressed → stay completed (rewind, IM9).
        // If autoMarkSuppressed → completed stays false (user un-marked; must re-cross threshold).
        val shouldComplete = when {
            userMarkedWatched -> true // sticky — stays completed regardless.
            autoMarkSuppressed -> false // suppressed — don't auto-mark.
            progress.progressFraction > threshold -> true // auto-mark fires.
            wasCompleted -> true // rewind — keep completed (IM9).
            else -> false
        }

        val completedInt = if (shouldComplete) 1L else 0L
        val completedAt = if (shouldComplete) (existing?.completed_at ?: now) else null

        // If transitioning to completed for the first time, increment watch_count + set first_watched_at.
        val justCompleted = shouldComplete && !wasCompleted
        val newWatchCount = if (justCompleted) (existing?.watch_count?.toInt() ?: 0) + 1 else (existing?.watch_count?.toInt() ?: 0)
        val firstWatchedAt = existing?.first_watched_at ?: if (justCompleted) now else null

        database.watchQueries.upsertWatchProgress(
            episode_key = episodeKey,
            position = progress.position,
            duration = progress.duration,
            completed = completedInt,
            completed_at = completedAt,
            last_watched_at = now,
            main_id = progress.mainId ?: existing?.main_id,
            watch_count = newWatchCount.toLong(),
            first_watched_at = firstWatchedAt,
            auto_mark_suppressed = if (autoMarkSuppressed) 1L else 0L,
            user_marked_watched = if (userMarkedWatched) 1L else 0L,
        )

        if (justCompleted) {
            Logger.i(TAG) {
                "save — auto-marked watched (threshold=${threshold}, fraction=${progress.progressFraction}): " +
                    "key=$episodeKey pos=${progress.position}s dur=${progress.duration}s watchCount=$newWatchCount"
            }
        } else {
            Logger.d(TAG) {
                "save: key=$episodeKey pos=${progress.position}s dur=${progress.duration}s " +
                    "fraction=${progress.progressFraction} completed=$shouldComplete"
            }
        }
    }

    override suspend fun get(episodeKey: String): WatchProgress? = withContext(dispatchers) {
        database.watchQueries.getWatchProgress(episodeKey).executeAsOneOrNull()?.toWatchProgress()
    }

    override fun observe(episodeKey: String): Flow<WatchProgress?> {
        return database.watchQueries.getWatchProgress(episodeKey)
            .asFlow()
            .mapToOneOrNull(dispatchers)
            .map { it?.toWatchProgress() }
    }

    override fun observeRecent(limit: Int): Flow<List<WatchProgress>> {
        return database.watchQueries.getRecentWatchProgress(limit.toLong())
            .asFlow()
            .mapToList(dispatchers)
            .map { it.map { row -> row.toWatchProgress() } }
    }

    override fun observeByMainId(mainId: String): Flow<List<WatchProgress>> {
        return database.watchQueries.getWatchProgressByMainId(mainId)
            .asFlow()
            .mapToList(dispatchers)
            .map { it.map { row -> row.toWatchProgress() } }
    }

    override fun observeContinueWatching(limit: Int): Flow<List<WatchProgress>> {
        return database.watchQueries.getContinueWatching(limit.toLong())
            .asFlow()
            .mapToList(dispatchers)
            .map { it.map { row -> row.toWatchProgress() } }
    }

    override suspend fun markCompleted(episodeKey: String) = withContext(dispatchers) {
        val now = System.currentTimeMillis()
        database.watchQueries.markCompleted(now, episodeKey)
        database.watchQueries.incrementWatchCount(now, episodeKey)
        Logger.i(TAG) { "markCompleted: key=$episodeKey" }
    }

    override suspend fun setAutoMarkSuppressed(episodeKey: String) = withContext(dispatchers) {
        // CF1: user un-marked → suppress auto-mark + clear completed + clear userMarkedWatched.
        database.watchQueries.setAutoMarkSuppressed(1L, episodeKey)
        Logger.i(TAG) { "setAutoMarkSuppressed (user un-marked): key=$episodeKey → isWatched=false" }
    }

    override suspend fun setUserMarkedWatched(episodeKey: String) = withContext(dispatchers) {
        // CF1: user explicitly marked watched (sticky).
        database.watchQueries.setUserMarkedWatched(1L, episodeKey)
        Logger.i(TAG) { "setUserMarkedWatched (user marked watched): key=$episodeKey → isWatched=true (sticky)" }
    }

    override suspend fun toggleWatched(episodeKey: String): Boolean = withContext(dispatchers) {
        val current = get(episodeKey)
        val isWatched = current?.isWatched ?: false
        if (isWatched) {
            setAutoMarkSuppressed(episodeKey)
            false
        } else {
            setUserMarkedWatched(episodeKey)
            true
        }
    }

    override suspend fun resetAutoMarkSuppressed(episodeKey: String) = withContext(dispatchers) {
        // CF1: on FILE_LOADED — re-arms the auto-mark. Does NOT reset completed.
        database.watchQueries.resetAutoMarkSuppressed(episodeKey)
        Logger.d(TAG) { "resetAutoMarkSuppressed (on play start): key=$episodeKey" }
    }

    override suspend fun isWatched(episodeKey: String): Boolean = withContext(dispatchers) {
        get(episodeKey)?.isWatched ?: false
    }

    override suspend fun getWatchedEpisodeCount(mainId: String): Int = withContext(dispatchers) {
        database.watchQueries.getWatchedEpisodeCount(mainId).executeAsOne().toInt()
    }

    override suspend fun clearByMainId(mainId: String) = withContext(dispatchers) {
        database.watchQueries.clearByMainId(mainId)
        Logger.i(TAG) { "clearByMainId: mainId=$mainId (library-remove)" }
    }

    override suspend fun delete(episodeKey: String) = withContext(dispatchers) {
        database.watchQueries.deleteWatchProgress(episodeKey)
        Logger.d(TAG) { "delete: key=$episodeKey" }
    }

    override suspend fun deleteAll() = withContext(dispatchers) {
        database.watchQueries.deleteAllWatchProgress()
        Logger.i(TAG) { "deleteAll: cleared all watch progress" }
    }

    /**
     * Maps a SQLDelight-generated `Watch_progress` row to the [WatchProgress] model.
     */
    private fun com.confused.anikuta.core.database.Watch_progress.toWatchProgress(): WatchProgress {
        return WatchProgress(
            episodeKey = episode_key,
            mainId = main_id,
            position = position,
            duration = duration,
            completed = completed.toInt() == 1,
            completedAt = completed_at,
            lastWatchedAt = last_watched_at,
            watchCount = watch_count.toInt(),
            firstWatchedAt = first_watched_at,
            autoMarkSuppressed = auto_mark_suppressed.toInt() == 1,
            userMarkedWatched = user_marked_watched.toInt() == 1,
        )
    }
}
