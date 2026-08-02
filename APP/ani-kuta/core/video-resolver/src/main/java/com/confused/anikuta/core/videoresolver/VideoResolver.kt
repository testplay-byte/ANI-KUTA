package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Resolves playable video URLs from extension sources.
 *
 * Calls the extension source's `fetchVideoList` → extracts playable URLs.
 * Some sources use anti-scraping (PNG headers, obfuscated HLS) — the
 * resolver handles these transparently.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:VideoResolver".
 *
 * Architecture plan I10: :feature:anime-watch:impl mediates between
 * :core:video-resolver and :core:player. The two core modules are unaware of each other.
 */
class VideoResolver {

    companion object {
        private const val TAG = "Anikuta:Core:VideoResolver"
    }

    /**
     * Resolve videos for an episode from a given source.
     *
     * @param source The AnimeHttpSource to fetch from.
     * @param episodeUrl The episode's URL on the source.
     * @return A Flow emitting [ResolverState] updates.
     */
    fun resolve(
        source: AnimeHttpSource,
        episodeUrl: String,
    ): Flow<ResolverState> = flow {
        Logger.i(TAG) { "Resolving videos for: $episodeUrl" }
        emit(ResolverState.Loading())

        try {
            val videos = source.fetchVideoList(episodeUrl)
            Logger.d(TAG) { "Fetched ${videos.size} videos" }

            if (videos.isEmpty()) {
                Logger.w(TAG) { "No videos found" }
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

        } catch (e: Exception) {
            Logger.e(TAG, e) { "Resolution failed: ${e.message}" }
            emit(ResolverState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Parse the quality label from a Video.
     * Priority: video.quality > parsed from URL > "Default".
     */
    private fun parseQuality(video: Video): String {
        // Video.quality is set by the extension (e.g., "1080p", "720p")
        if (video.quality.isNotBlank()) {
            return video.quality
        }

        // Try to parse from URL (e.g., "1080" in the filename)
        val url = video.url.lowercase()
        val qualityPattern = Regex("(\\d{3,4})p")
        qualityPattern.find(url)?.let { return "${it.groupValues[1]}p" }

        return "Default"
    }
}
