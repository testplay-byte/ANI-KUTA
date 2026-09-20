// CLEAN-ROOM: original ANI-KUTA code.
//
// D-539: the DASH offline-cache home — the ONE SimpleCache instance the
// pipeline shares (downloader writes, player reads, delete purges). Every
// @UnstableApi media3 call lives behind this object's annotated methods so
// the rest of :core:download never opts in to the unstable surface.
package com.confused.anikuta.core.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.confused.anikuta.core.common.DashCacheKeys
import java.io.File

@OptIn(UnstableApi::class)
object DashCacheStore {

    /** App-private, survives reboots, NOT user-visible (the SAF folder holds only metadata + subs). */
    private const val CACHE_DIR_NAME = "cs_dash_cache"

    /**
     * The process-wide cache. Koin binds this as `single<Cache>` — SimpleCache
     * throws if two instances are created over one folder, so the single
     * binding is the lifetime contract.
     *
     * [NoOpCacheEvictor] = never evict: a "download" the cache silently
     * deletes would be data loss. Growth is managed by the user via the
     * Downloads page (delete), not by an LRU policy.
     */
    fun provide(context: Context): SimpleCache =
        SimpleCache(
            File(context.filesDir, CACHE_DIR_NAME),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context),
        )

    /**
     * Purges EVERY cached span of one DASH episode (the delete path). The
     * manifest URL scopes the key namespace — one CDN folder never leaks
     * into another episode's sweep.
     */
    fun purgeEpisode(cache: Cache, manifestUrl: String) {
        val prefix = DashCacheKeys.episodePrefix(manifestUrl)
        val keys = runCatching { cache.getKeys().filter { it.startsWith(prefix) } }
            .getOrElse { emptyList() }
        if (keys.isEmpty()) {
            DownloadLogger.w { "DashCacheStore.purgeEpisode — no cached keys for $manifestUrl" }
            return
        }
        var purged = 0
        for (key in keys) {
            runCatching { cache.removeResource(key) }
                .onSuccess { purged++ }
                .onFailure { e ->
                    DownloadLogger.w { "DashCacheStore.purgeEpisode — removeResource($key) failed: ${e.message}" }
                }
        }
        DownloadLogger.i { "DashCacheStore.purgeEpisode — purged $purged/${keys.size} key(s) for $manifestUrl" }
    }

    /** Whether ANY span of the episode is cached (diagnostics + scanner trust). */
    fun hasCachedData(cache: Cache, manifestUrl: String): Boolean {
        val prefix = DashCacheKeys.episodePrefix(manifestUrl)
        return runCatching { cache.getKeys().any { it.startsWith(prefix) } }.getOrDefault(false)
    }
}
