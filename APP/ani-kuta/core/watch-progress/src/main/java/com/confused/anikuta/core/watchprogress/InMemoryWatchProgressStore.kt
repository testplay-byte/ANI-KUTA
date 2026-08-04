package com.confused.anikuta.core.watchprogress

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory [WatchProgressStore] implementation.
 *
 * **Phase 5c capture-only**: this impl saves watch progress to an in-memory
 * map so the save path is exercised end-to-end, but progress is NOT restored
 * on the next playback yet (restore is Phase 5e when the database is wired).
 *
 * Trade-off: progress is lost on process death. This is acceptable for now —
 * the user explicitly asked for a "capture but don't use" system. When the
 * SQLDelight `watch_progress` table is wired (Phase 5e), this impl is swapped
 * for a database-backed one. The [WatchProgressStore] interface stays the same.
 *
 * ponytail: in-memory map → upgrade to SQLDelight impl in Phase 5e.
 *           Ceiling: full persistent progress with resume + "Continue Watching".
 */
class InMemoryWatchProgressStore : WatchProgressStore {

    companion object {
        private const val TAG = "Anikuta:Core:WatchProgress"
    }

    private val store = ConcurrentHashMap<String, WatchProgress>()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<WatchProgress?>>()
    private val mutex = Mutex()

    override suspend fun save(episodeKey: String, progress: WatchProgress) {
        Logger.d(TAG) { "save: key=$episodeKey pos=${progress.position}s dur=${progress.duration}s" }
        store[episodeKey] = progress
        // Update the reactive flow so any observer gets the new value.
        flows.getOrPut(episodeKey) { MutableStateFlow(null) }.value = progress
    }

    override suspend fun get(episodeKey: String): WatchProgress? {
        return store[episodeKey]
    }

    override fun observe(episodeKey: String): Flow<WatchProgress?> {
        return flows.getOrPut(episodeKey) { MutableStateFlow(store[episodeKey]) }
    }

    override fun observeRecent(limit: Int): Flow<List<WatchProgress>> {
        // Return a single snapshot flow — proper "recent" ordering requires the
        // database (Phase 5e). For now this returns all entries.
        val snapshot = store.values.sortedByDescending { it.lastWatchedAt }.take(limit)
        return MutableStateFlow(snapshot)
    }

    override suspend fun markCompleted(episodeKey: String) {
        val existing = store[episodeKey] ?: return
        val completed = existing.copy(
            completed = true,
            completedAt = System.currentTimeMillis(),
        )
        store[episodeKey] = completed
        flows[episodeKey]?.value = completed
        Logger.d(TAG) { "markCompleted: key=$episodeKey" }
    }

    override suspend fun delete(episodeKey: String) {
        store.remove(episodeKey)
        flows[episodeKey]?.value = null
        Logger.d(TAG) { "delete: key=$episodeKey" }
    }
}
