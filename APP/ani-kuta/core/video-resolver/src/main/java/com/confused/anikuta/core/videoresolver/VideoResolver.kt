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

/**
 * Resolves playable video URLs from extension sources.
 *
 * Calls the extension source's `fetchVideoList` → extracts playable URLs.
 *
 * CRITICAL: All network calls MUST run on `Dispatchers.IO` — the RxJava
 * `Observable` returned by `fetchVideoList` does network IO synchronously
 * when `awaitSingle()` is called. Without `withContext(IO)`, this throws
 * `NetworkOnMainThreadException`.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:VideoResolver".
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
     * @return A Flow emitting [ResolverState] updates. Runs on `Dispatchers.IO`.
     */
    fun resolve(
        source: AnimeHttpSource,
        episodeUrl: String,
    ): Flow<ResolverState> = flow {
        Logger.i(TAG) { "Resolving videos for: $episodeUrl (source: ${source.name})" }
        emit(ResolverState.Loading())

        try {
            // Create an SEpisode from the URL (the source needs it to fetch videos)
            val episode = SEpisode.create().apply {
                url = episodeUrl
                name = "Episode"
            }

            // CRITICAL: fetchVideoList returns Observable<List<Video>> (RxJava)
            // which does network IO. Must run on Dispatchers.IO to avoid
            // NetworkOnMainThreadException.
            val videos = withContext(Dispatchers.IO) {
                source.fetchVideoList(episode).awaitSingle()
            }
            Logger.d(TAG) { "Fetched ${videos.size} videos from ${source.name}" }

            if (videos.isEmpty()) {
                Logger.w(TAG) { "No videos found for $episodeUrl" }
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
            // Catch Throwable (not Exception) — binary-incompat throws NoClassDefFoundError
            // (an Error), and OkHttp version mismatches throw IncompatibleClassChangeError.
            Logger.e(TAG, e) {
                "Resolution failed for $episodeUrl: ${e::class.java.simpleName}: ${e.message}"
            }
            emit(ResolverState.Error(formatError(e)))
        }
    }.flowOn(Dispatchers.IO) // Ensure the entire flow runs on IO.

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

    /**
     * Format an error message for the user. Includes the exception type for
     * debugging (e.g. "NetworkOnMainThreadException", "UnknownHostException").
     */
    private fun formatError(e: Throwable): String {
        val type = e::class.java.simpleName
        val msg = e.message ?: "Unknown error"
        return "$type: $msg"
    }
}
