package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry for resolved video servers.
 *
 * ## Why this exists
 *
 * The [QualitySheet] in the watch screen needs the full list of resolved
 * servers (to show the accordion + quality chips). But [WatchKey] is
 * `@Serializable` and carries only primitives — we can't easily serialize
 * the nested `List<ResolverServer>` structure through Nav3.
 *
 * Instead, the Details screen resolves the videos, stores them here under a
 * random key, and passes the key to the watch screen via [WatchKey.resolvedVideosKey].
 * The watch screen reads the servers from this registry.
 *
 * ## Lifecycle
 *
 * Entries are NOT automatically cleared — they live until the process dies.
 * This is intentional: if the user navigates back to the watch screen after
 * briefly leaving (e.g. to check another tab), the resolved videos are still
 * available. The registry is bounded by user sessions (typically <50 entries).
 *
 * ## Thread safety
 *
 * Uses [ConcurrentHashMap] — safe to read/write from any thread.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:VideoResolver:Registry".
 */
object ResolvedVideosRegistry {

    private const val TAG = "Anikuta:Core:VideoResolver:Registry"

    private val store = ConcurrentHashMap<String, List<ResolverServer>>()

    /**
     * Store resolved servers under a new random key.
     * @return The key (UUID string) to pass to the watch screen.
     */
    fun put(servers: List<ResolverServer>): String {
        val key = UUID.randomUUID().toString()
        store[key] = servers
        Logger.d(TAG) { "Stored ${servers.size} servers under key $key (total: ${store.size})" }
        return key
    }

    /**
     * Retrieve resolved servers by key.
     * @return The servers, or null if the key is unknown (e.g. process restarted).
     */
    fun get(key: String): List<ResolverServer>? {
        val servers = store[key]
        if (servers == null) {
            Logger.w(TAG) { "No servers found for key $key (process may have restarted)" }
        }
        return servers
    }

    /**
     * Remove an entry (optional — called when the watch screen is destroyed
     * and the user is unlikely to return).
     */
    fun remove(key: String) {
        store.remove(key)
        Logger.d(TAG) { "Removed key $key (remaining: ${store.size})" }
    }

    /**
     * Clear all entries (for testing / app reset).
     */
    fun clear() {
        store.clear()
        Logger.d(TAG) { "Cleared all entries" }
    }
}
