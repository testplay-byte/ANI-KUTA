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
                    headers = formatHeaders(video.headers),
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
     * Resolve videos AND group them into a 3-tier server/audio/quality hierarchy.
     *
     * Used by the [QualitySheet] in the watch screen. The flat [resolve] method
     * is kept for backward compat with the [ResolverSheet] in the details screen.
     *
     * Grouping strategy (matches old project):
     * - Server: derived from `Video.server` if non-empty, else from the URL host.
     * - Audio version: derived from the quality label's prefix (e.g. "SUB 1080p"
     *   → "SUB"), or "Default" if no prefix.
     * - Video: the URL + quality + headers + title.
     *
     * @param source The AnimeHttpSource to fetch from.
     * @param episode The FULL SEpisode.
     * @return A Flow emitting [StructuredResolverState] updates.
     */
    fun resolveStructured(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): Flow<StructuredResolverState> = flow {
        Logger.i(TAG) { "Resolving structured videos for: ${episode.url} (source: ${source.name})" }
        emit(StructuredResolverState.Loading())

        try {
            val videos = withContext(Dispatchers.IO) {
                resolveVideoEntries(source, episode)
            }
            Logger.d(TAG) { "Fetched ${videos.size} videos from ${source.name}" }

            if (videos.isEmpty()) {
                emit(StructuredResolverState.Error("No videos available"))
                return@flow
            }

            val servers = groupIntoServers(videos, source.name)
            Logger.i(TAG) { "Grouped into ${servers.size} servers: ${servers.map { "${it.name} (${it.audioVersions.sumOf { av -> av.videos.size }})" }}" }
            emit(StructuredResolverState.Success(servers))

        } catch (e: Throwable) {
            Logger.e(TAG, e) {
                "Structured resolution failed for ${episode.url}: ${e::class.java.simpleName}: ${e.message}"
            }
            emit(StructuredResolverState.Error(formatError(e)))
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

    /**
     * Format Video.headers (okhttp3.Headers?) into MPV's
     * http-header-fields format: "Key: Value,Key2: Value2".
     *
     * The old project passes these headers to MPV before loadfile. Without
     * them, upstream servers return 403 Forbidden (missing Referer/UA).
     */
    private fun formatHeaders(headers: okhttp3.Headers?): String {
        if (headers == null || headers.size == 0) return ""
        return (0 until headers.size).joinToString(",") { i ->
            "${headers.name(i)}: ${headers.value(i)}"
        }
    }

    /**
     * Group a flat list of Videos into a 3-tier server/audio/quality hierarchy.
     *
     * Grouping strategy (adapted for ext-lib Video model which has no `server`
     * field — uses `videoTitle` which is the quality label):
     * - **Server**: parse from `videoTitle` if it contains a server separator
     *   (e.g. "ServerName - 1080p"), else the URL host, else the source name.
     * - **Audio version**: parsed from the quality label. If the label starts
     *   with a known audio-version prefix (SUB/DUB/HSUB/MIX), that's the audio
     *   version. Otherwise "Default".
     * - **Video**: the URL + quality + headers + a stable videoTitle.
     *
     * The `videoTitle` field on [ResolverVideo] is a stable identifier used to
     * match the currently-playing video across re-resolutions (proxied URLs
     * change). We construct it as `server|audio|quality|url-hash`.
     */
    private fun groupIntoServers(
        videos: List<Video>,
        sourceName: String,
    ): List<ResolverServer> {
        // Group by server name.
        val byServer = videos.groupBy { video ->
            parseServerName(video.videoTitle, video.url, sourceName)
        }

        return byServer.entries.map { (serverName, serverVideos) ->
            // Within each server, group by audio version.
            val byAudio = serverVideos.groupBy { video -> parseAudioVersion(video.videoTitle) }
            val audioVersions = byAudio.entries.map { (audioLabel, audioVideos) ->
                ResolverAudioVersion(
                    label = audioLabel,
                    videos = audioVideos.map { video ->
                        val quality = parseQuality(video)
                        val title = buildVideoTitle(serverName, audioLabel, quality, video.url)
                        ResolverVideo(
                            quality = quality,
                            url = video.url,
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
     * Examples:
     *   "Vidstream - 1080p" → "Vidstream"
     *   "Mp4Upload 720p" → "Mp4Upload"  (if it matches a known server)
     *   "1080p" → URL host, or sourceName as fallback.
     */
    private fun parseServerName(videoTitle: String, url: String, sourceName: String): String {
        // Known server prefixes (common in Aniyomi extensions).
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
        // Try to extract "ServerName -" prefix.
        val dashIdx = videoTitle.indexOf(" - ")
        if (dashIdx > 0) {
            val candidate = videoTitle.substring(0, dashIdx).trim()
            if (candidate.isNotEmpty() && candidate.length <= 30) return candidate
        }
        // Fallback: URL host.
        return runCatching { java.net.URI(url).host }.getOrNull() ?: sourceName
    }

    /**
     * Parse the audio-version prefix from a quality label.
     * Examples:
     *   "SUB 1080p" → "SUB"
     *   "DUB - 720p" → "DUB"
     *   "HSUB 480p" → "HSUB"
     *   "1080p" → "Default"
     */
    private fun parseAudioVersion(quality: String): String {
        val upper = quality.uppercase().trim()
        // Match a prefix word followed by a space or dash.
        val match = Regex("^(SUB|DUB|HSUB|MIX|RAW)[\\s\\-]").find(upper)
        return match?.groupValues?.get(1) ?: "Default"
    }

    /**
     * Build a stable videoTitle for matching across re-resolutions.
     * Format: "server|audio|quality|url-hash" — unique within a server.
     */
    private fun buildVideoTitle(server: String, audio: String, quality: String, url: String): String {
        val urlHash = url.hashCode().toString(16)
        return "$server|$audio|$quality|$urlHash"
    }
}

/**
 * Structured resolver state — drives the [QualitySheet] UI.
 *
 * Emitted by [VideoResolver.resolveStructured]. The watch screen collects this
 * flow, stores the resulting servers in [ResolvedVideosRegistry], and passes
 * the registry key to the QualitySheet.
 */
sealed interface StructuredResolverState {
    data object Idle : StructuredResolverState
    data class Loading(val message: String = "Resolving video...") : StructuredResolverState
    data class Success(val servers: List<ResolverServer>) : StructuredResolverState
    data class Error(val message: String) : StructuredResolverState
}
