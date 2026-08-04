package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves playable video URLs from extension sources.
 *
 * CRITICAL FIX (double-resolve bug): The previous version called `getHosterList`
 * TWICE — once for the flat [resolve] method and once for a separate structured
 * resolve. For extensions like AniKotoS that create a local proxy server on each
 * `getHosterList` call, the second call KILLS the proxy from the first call →
 * the user picks a video with a dead proxy URL → "loading failed".
 *
 * Fix: [resolve] now returns BOTH the flat `List<ResolvedVideo>` AND the raw
 * `List<Video>` in [ResolverState.Success]. The DetailsViewModel calls
 * [buildServers] to derive structured servers from the SAME video list —
 * NO second `getHosterList` call.
 *
 * Ported from the old project's `ResolverService.kt`. Key behaviors:
 * - Calls `getHosterList` (ext-lib 16+) first, falls back to `getVideoList(episode)`.
 * - Checks `hoster.videoList` first (non-lazy hosters like AnikotoS).
 * - FILTERS OUT videos where `videoUrl` is blank (matching old project line 49).
 * - Uses `video.videoUrl` (NOT the deprecated `video.url`).
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
     * Returns a Flow emitting [ResolverState] updates. Runs on `Dispatchers.IO`.
     *
     * The DetailsViewModel collects this flow. When it reaches Success, the
     * DetailsViewModel ALSO derives structured servers from the same result
     * (no second getHosterList call).
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
            Logger.d(TAG) { "Fetched ${videos.size} raw videos from ${source.name}" }

            // CRITICAL: Filter out videos with blank videoUrl — matching old project
            // (ResolverService.kt line 49: `videoEntries.filter { it.video.videoUrl.isNotBlank() }`).
            // The old project NEVER uses video.url (deprecated). If videoUrl is blank,
            // the video is unplayable and should be rejected.
            val validVideos = videos.filter { it.videoUrl.isNotBlank() }
            if (validVideos.size < videos.size) {
                Logger.w(TAG) { "Filtered out ${videos.size - validVideos.size} videos with blank videoUrl" }
            }

            if (validVideos.isEmpty()) {
                Logger.w(TAG) { "No valid videos found for ${episode.url} (all had blank videoUrl)" }
                emit(ResolverState.Error("No videos available"))
                return@flow
            }

            val resolvedVideos = validVideos.map { video ->
                Logger.d(TAG) { "Valid video: quality='${video.videoTitle}', videoUrl='${video.videoUrl.take(80)}', subs=${video.subtitleTracks.size}, audio=${video.audioTracks.size}" }
                ResolvedVideo(
                    url = video.videoUrl,
                    quality = parseQuality(video),
                    directUrl = video.videoUrl,
                    headers = formatHeaders(video.headers),
                    subtitleTracks = video.subtitleTracks.map { ResolverSubtitleTrack(it.url, it.lang) },
                    audioTracks = video.audioTracks.map { ResolverSubtitleTrack(it.url, it.lang) },
                )
            }

            Logger.i(TAG) { "Resolved ${resolvedVideos.size} videos: ${resolvedVideos.map { it.quality }}" }
            emit(ResolverState.Success(resolvedVideos, validVideos))

        } catch (e: Throwable) {
            Logger.e(TAG, e) {
                "Resolution failed for ${episode.url}: ${e::class.java.simpleName}: ${e.message}"
            }
            emit(ResolverState.Error(formatError(e)))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Build structured servers from an ALREADY-RESOLVED video list.
     * Does NOT call getHosterList again — derives from the flat result.
     *
     * Called by DetailsViewModel after `resolve()` succeeds, to populate
     * the ResolvedVideosRegistry for the QualitySheet.
     */
    fun buildServers(
        videos: List<Video>,
        sourceName: String,
    ): List<ResolverServer> {
        val validVideos = videos.filter { it.videoUrl.isNotBlank() }
        if (validVideos.isEmpty()) return emptyList()
        val servers = groupIntoServers(validVideos, sourceName)
        Logger.i(TAG) { "Built ${servers.size} servers from ${validVideos.size} videos" }
        return servers
    }

    /**
     * Try getHosterList first (ext-lib 16+), fall back to getVideoList.
     *
     * Ported from the old project's ResolverService.resolveVideoEntries.
     * Key fix for AnikotoS: checks `hoster.videoList` first (non-lazy hosters).
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
            Logger.d(TAG) { "getHosterList not supported by ${source.name}, falling back to getVideoList" }
            emptyList()
        } catch (e: Throwable) {
            Logger.w(TAG, e) { "getHosterList failed for ${source.name}: ${e.message}" }
            emptyList()
        }

        if (hosters.isNotEmpty()) {
            Logger.i(TAG) { "Got ${hosters.size} hosters from ${source.name}" }
            // For each hoster: use hoster.videoList if pre-populated (non-lazy, like AnikotoS),
            // else source.getVideoList(hoster) (lazy hosters).
            val videos = mutableListOf<Video>()
            for (hoster in hosters) {
                val hosterVideos = hoster.videoList
                if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                    Logger.d(TAG) { "Hoster '${hoster.hosterName}' has ${hosterVideos.size} pre-loaded videos" }
                    videos.addAll(hosterVideos)
                } else {
                    Logger.d(TAG) { "Hoster '${hoster.hosterName}' is lazy — calling getVideoList(hoster)" }
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
        if (video.videoTitle.isNotBlank()) {
            return video.videoTitle
        }
        val url = video.videoUrl.lowercase()
        val qualityPattern = Regex("(\\d{3,4})p")
        qualityPattern.find(url)?.let { return "${it.groupValues[1]}p" }
        return "Default"
    }

    private fun formatError(e: Throwable): String {
        val type = e::class.java.simpleName
        val msg = e.message ?: "Unknown error"
        return "$type: $msg"
    }

    /**
     * Format Video.headers (okhttp3.Headers?) into MPV's
     * http-header-fields format: "Key: Value,Key2: Value2".
     */
    private fun formatHeaders(headers: okhttp3.Headers?): String {
        if (headers == null || headers.size == 0) return ""
        return (0 until headers.size).joinToString(",") { i ->
            "${headers.name(i)}: ${headers.value(i)}"
        }
    }

    /**
     * Group a flat list of Videos into a 3-tier server/audio/quality hierarchy.
     */
    private fun groupIntoServers(
        videos: List<Video>,
        sourceName: String,
    ): List<ResolverServer> {
        val byServer = videos.groupBy { video ->
            parseServerName(video.videoTitle, video.videoUrl, sourceName)
        }

        return byServer.entries.map { (serverName, serverVideos) ->
            val byAudio = serverVideos.groupBy { video -> parseAudioVersion(video.videoTitle) }
            val audioVersions = byAudio.entries.map { (audioLabel, audioVideos) ->
                ResolverAudioVersion(
                    label = audioLabel,
                    videos = audioVideos.map { video ->
                        val quality = parseQuality(video)
                        val title = buildVideoTitle(serverName, audioLabel, quality, video.videoUrl)
                        ResolverVideo(
                            quality = quality,
                            url = video.videoUrl,
                            videoTitle = title,
                            videoHeaders = formatHeaders(video.headers),
                            subtitleTracks = video.subtitleTracks.map {
                                ResolverSubtitleTrack(it.url, it.lang)
                            },
                            audioTracks = video.audioTracks.map {
                                ResolverSubtitleTrack(it.url, it.lang)
                            },
                        )
                    },
                )
            }
            ResolverServer(name = serverName, audioVersions = audioVersions)
        }
    }

    /**
     * Parse the server name from a video title.
     */
    private fun parseServerName(videoTitle: String, url: String, sourceName: String): String {
        val knownServers = listOf(
            "Vidstream", "Mp4Upload", "Doodstream", "Streamtape", "MixDrop",
            "StreamSB", "Vidcloud", "Beta2", "Akira", "Googledrive", "HD-1",
            "HD-2", "StreamX", "Vidtubing", "Fastplay", "Arenabokeh",
        )
        val titleLower = videoTitle.lowercase()
        for (server in knownServers) {
            if (titleLower.startsWith(server.lowercase())) return server
            if (" - $server".lowercase() in titleLower) return server
        }
        val dashIdx = videoTitle.indexOf(" - ")
        if (dashIdx > 0) {
            val candidate = videoTitle.substring(0, dashIdx).trim()
            if (candidate.isNotEmpty() && candidate.length <= 30) return candidate
        }
        return runCatching { java.net.URI(url).host }.getOrNull() ?: sourceName
    }

    /**
     * Parse the audio-version prefix from a quality label.
     */
    private fun parseAudioVersion(quality: String): String {
        val upper = quality.uppercase().trim()
        val match = Regex("^(SUB|DUB|HSUB|MIX|RAW)[\\s\\-]").find(upper)
        return match?.groupValues?.get(1) ?: "Default"
    }

    /**
     * Build a stable videoTitle for matching across re-resolutions.
     */
    private fun buildVideoTitle(server: String, audio: String, quality: String, url: String): String {
        val urlHash = url.hashCode().toString(16)
        return "$server|$audio|$quality|$urlHash"
    }
}

/**
 * Structured resolver state — drives the [QualitySheet] UI.
 */
sealed interface StructuredResolverState {
    data object Idle : StructuredResolverState
    data class Loading(val message: String = "Resolving video...") : StructuredResolverState
    data class Success(val servers: List<ResolverServer>) : StructuredResolverState
    data class Error(val message: String) : StructuredResolverState
}
