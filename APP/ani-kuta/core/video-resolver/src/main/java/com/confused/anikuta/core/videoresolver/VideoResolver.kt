package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.util.awaitSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves playable video URLs from extension sources.
 *
 * Ported from the old project's `ResolverService`. Key differences from the
 * previous version:
 * 1. Accepts the FULL [SEpisode] (not just the URL string) — extensions may
 *    read `episode.episode_number`, `episode.name`, etc. to construct API URLs.
 *    The old `SEpisode.create().apply { url = ...; name = "Episode" }` left
 *    `episode_number` at its default `-1f`, producing wrong URLs.
 * 2. Tries `getHosterList` FIRST (ext-lib 16+ API), then falls back to
 *    `getVideoList`. Extensions that ONLY implement the hoster-based API
 *    return empty from `getVideoList` → "No videos".
 * 3. Wraps every call in `withContext(IO) + withTimeoutOrNull(30s)`.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:VideoResolver".
 */
class VideoResolver {

    companion object {
        private const val TAG = "Anikuta:Core:VideoResolver"
        private const val SOURCE_TIMEOUT_MS = 30_000L
    }

    /**
     * Resolve videos for an episode from a given source.
     *
     * @param source The AnimeHttpSource to fetch from.
     * @param episode The FULL SEpisode (from the episode list — has url, name,
     *                episode_number, date_upload, scanlator, etc.).
     * @return A Flow emitting [ResolverState] updates. Runs on `Dispatchers.IO`.
     */
    fun resolve(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): Flow<ResolverState> = flow {
        Logger.i(TAG) { "Resolving videos for: ${episode.url} (source: ${source.name}, epNum: ${episode.episode_number})" }
        emit(ResolverState.Loading())

        try {
            val videos = withContext(Dispatchers.IO) {
                resolveVideoEntries(source, episode)
            }
            Logger.d(TAG) { "Fetched ${videos.size} videos from ${source.name}" }

            if (videos.isEmpty()) {
                Logger.w(TAG) { "No videos found for ${episode.url}" }
                emit(ResolverState.Error("No videos available"))
                return@flow
            }

            val resolvedVideos = videos.map { video ->
                ResolvedVideo(
                    url = video.url,
                    quality = parseQuality(video),
                    directUrl = video.videoUrl,
                )
            }

            Logger.i(TAG) { "Resolved ${resolvedVideos.size} videos: ${resolvedVideos.map { it.quality }}" }
            emit(ResolverState.Success(resolvedVideos))

        } catch (e: Throwable) {
            Logger.e(TAG, e) {
                "Resolution failed for ${episode.url}: ${e::class.java.simpleName}: ${e.message}"
            }
            emit(ResolverState.Error(formatError(e)))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Try getHosterList first (ext-lib 16+), fall back to getVideoList.
     *
     * Ported from the old project's ResolverService.resolveVideoEntries.
     */
    private suspend fun resolveVideoEntries(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): List<Video> {
        // Try getHosterList first (ext-lib 16+ API).
        val hosters = try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getHosterList(episode)
            } ?: emptyList()
        } catch (e: IllegalStateException) {
            // Source doesn't support getHosterList — fall back.
            Logger.d(TAG) { "getHosterList not supported by ${source.name}, falling back to getVideoList" }
            emptyList()
        } catch (e: Throwable) {
            Logger.w(TAG, e) { "getHosterList failed for ${source.name}: ${e.message}" }
            emptyList()
        }

        if (hosters.isNotEmpty()) {
            // For each hoster: use hoster.videoList if pre-populated, else source.getVideoList(hoster)
            val videos = mutableListOf<Video>()
            for (hoster in hosters) {
                val hosterVideos = hoster.videoList
                if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                    videos.addAll(hosterVideos)
                } else {
                    try {
                        val resolvedVideos = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            source.getVideoList(hoster)
                        } ?: emptyList()
                        videos.addAll(resolvedVideos)
                    } catch (e: Throwable) {
                        Logger.w(TAG, e) { "getVideoList for hoster ${hoster.hosterName} failed: ${e.message}" }
                    }
                }
            }
            return videos
        }

        // Fallback: old direct API (ext-lib < 16).
        Logger.d(TAG) { "Falling back to getVideoList(episode) for ${source.name}" }
        return try {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getVideoList(episode)
            } ?: emptyList()
        } catch (e: Throwable) {
            Logger.e(TAG, e) { "getVideoList(episode) failed for ${source.name}: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Parse the quality label from a Video.
     */
    private fun parseQuality(video: Video): String {
        if (video.quality.isNotBlank()) {
            return video.quality
        }
        val url = video.url.lowercase()
        val qualityPattern = Regex("(\\d{3,4})p")
        qualityPattern.find(url)?.let { return "${it.groupValues[1]}p" }
        return "Default"
    }

    private fun formatError(e: Throwable): String {
        val type = e::class.java.simpleName
        val msg = e.message ?: "Unknown error"
        return "$type: $msg"
    }
}
