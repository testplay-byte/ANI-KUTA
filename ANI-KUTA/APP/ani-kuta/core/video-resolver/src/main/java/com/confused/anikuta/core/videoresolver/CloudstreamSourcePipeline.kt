package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "Anikuta:Core:VideoResolver"

/**
 * Task 50 (round 10, Fix C): the CLOUDSTREAM resolution pipeline.
 *
 * Mirrors upstream CloudStream's `APIRepository.loadLinks` semantics — the
 * aniyomi pipeline's habits do NOT apply here (that mix-up is exactly what
 * this split fixes):
 *
 * 1. **Cache first**: [CloudstreamLinkCache] replays a fresh (≤ 20 min)
 *    link list instantly — re-resolve / mirror-switch / return-to-episode
 *    skip the whole loadLinks pass. `forceRefresh` (dead-download-link
 *    re-resolve) bypasses the cache.
 * 2. **NO withTimeoutOrNull wrapper**: the bridge bounds
 *    `provider.loadLinks` itself and KEEPS partial links on timeout (Task 50
 *    bridge restructure — upstream: a timeout only stops FURTHER loading;
 *    streamed links are never discarded). Wrapping it here would discard
 *    exactly those partials.
 * 3. **Cache non-empty results** (partials included — the bridge decides
 *    what a partial is). Failures are NOT cached: getVideoList throws
 *    honest IllegalStateExceptions on total failure and the exception
 *    propagates to the caller (resolve()'s catch → Error state), so a
 *    failed episode is retried on the next entry instead of replaying an
 *    empty/error result forever.
 */
internal suspend fun resolveCloudstreamEntries(
    source: AnimeHttpSource,
    episode: SEpisode,
    forceRefresh: Boolean,
): List<VideoEntry> {
    if (!forceRefresh) {
        CloudstreamLinkCache.get(source.id, episode.url)?.let { cached ->
            Logger.i(TAG) {
                "CloudStream pipeline: replaying ${cached.size} cached links for ${source.name} — ${episode.url.take(60)}"
            }
            return cached
        }
    }

    Logger.i(TAG) {
        "CloudStream pipeline: resolving links for ${source.name} — ${episode.url.take(60)} (forceRefresh=$forceRefresh)"
    }

    // NO withTimeoutOrNull here — see the KDoc above (upstream-mirroring
    // semantics: the bridge's own budget governs, partials are kept).
    val videos = withContext(Dispatchers.IO) { source.getVideoList(episode) }
    val entries = videos.map { VideoEntry(it, null) }

    CloudstreamLinkCache.put(source.id, episode.url, entries)
    return entries
}
