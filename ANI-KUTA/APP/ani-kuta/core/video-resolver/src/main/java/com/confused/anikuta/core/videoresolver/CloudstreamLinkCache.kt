package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Task 50 (round 10, Fix F — resolver side): the 20-minute CloudStream link
 * cache.
 *
 * Mirrors upstream CloudStream's `RepoLinkGenerator` link cache (keyed
 * `(apiName, episodeId)` upstream, `(sourceId, episodeUrl)` here): a resolved
 * link list is replayed instantly on re-entry — re-resolve / mirror-switch /
 * return-to-the-same-episode all skip the (potentially minutes-long)
 * `provider.loadLinks` pass while the entry is fresh.
 *
 * **CS-only by construction**: only the CloudStream pipeline
 * ([resolveCloudstreamEntries]) calls [put]/[get] — aniyomi entries never
 * enter this cache because aniyomi local-proxy URLs (e.g. AniKoto's
 * `http://127.0.0.1:PORT/...`) are backed by a per-resolve extension server
 * that DIES on re-resolve; replaying them would hand the player dead URLs.
 *
 * Failures are never cached: the pipeline only calls [put] after a
 * non-empty result — an exception propagates to the caller untouched
 * (upstream parity: a failed load is retried, a partial load is cached).
 *
 * [TTL_MS] matches upstream's 20-minute retention. Expired entries are
 * removed lazily on read.
 */
object CloudstreamLinkCache {

    private const val TAG = "Anikuta:Core:VideoResolver:Cache"

    /** Upstream RepoLinkGenerator parity: 20 minutes. Internal for the TTL unit test. */
    internal const val TTL_MS = 20 * 60 * 1000L

    private data class Cached(val entries: List<VideoEntry>, val at: Long)

    /** key: sourceId(=bridge synthetic id) + '\u0000' + episode.url */
    private val map = ConcurrentHashMap<String, Cached>()

    private fun key(sourceId: Long, episodeUrl: String): String =
        "$sourceId\u0000$episodeUrl"

    /**
     * Returns the cached entries for (sourceId, episodeUrl), or null on
     * miss/expiry. Expired entries are removed on read.
     */
    fun get(sourceId: Long, episodeUrl: String): List<VideoEntry>? =
        getWithNow(sourceId, episodeUrl, System.currentTimeMillis())

    /**
     * Test seam: [get] with an injected clock so the TTL boundary is
     * deterministic. Production code always goes through [get].
     */
    internal fun getWithNow(sourceId: Long, episodeUrl: String, nowMs: Long): List<VideoEntry>? {
        val k = key(sourceId, episodeUrl)
        val cached = map[k] ?: return null
        val age = nowMs - cached.at
        if (age >= TTL_MS) {
            map.remove(k)
            Logger.d(TAG) { "Expired after ${age / 1000}s (TTL=${TTL_MS / 1000}s) — sourceId=$sourceId" }
            return null
        }
        Logger.i(TAG) {
            "Cache hit: ${cached.entries.size} links for sourceId=$sourceId (age=${age / 1000}s)"
        }
        return cached.entries
    }

    /**
     * Caches a resolved entry list. No-op on an empty list (upstream parity:
     * the cache is only marked "saturated" when ≥ 1 link arrived).
     */
    fun put(sourceId: Long, episodeUrl: String, entries: List<VideoEntry>) {
        if (entries.isEmpty()) return
        map[key(sourceId, episodeUrl)] = Cached(entries, System.currentTimeMillis())
    }

    /** Drops a single (sourceId, episodeUrl) entry. */
    fun invalidate(sourceId: Long, episodeUrl: String) {
        map.remove(key(sourceId, episodeUrl))
    }

    /** Drops everything. */
    fun clear() {
        map.clear()
    }
}
