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
 * A video with its associated hoster name (if from the hoster-based API).
 *
 * The hoster name is used as the server name in the QualitySheet. Without it,
 * the server name would have to be parsed from the video title (which often
 * doesn't contain it — e.g. AniKotoS video titles are just "SUB - 1080p").
 */
data class VideoEntry(
    val video: Video,
    val hosterName: String?,
)

/**
 * Resolves playable video URLs from extension sources.
 *
 * CRITICAL FIX (double-resolve bug): The previous version called `getHosterList`
 * TWICE — once for the flat [resolve] method and once for a separate structured
 * resolve. For extensions like AniKotoS that create a local proxy server on each
 * `getHosterList` call, the second call KILLS the proxy from the first call.
 *
 * Fix: [resolve] now returns BOTH the flat `List<ResolvedVideo>` AND the raw
 * `List<VideoEntry>` in [ResolverState.Success]. The DetailsViewModel calls
 * [buildServers] to derive structured servers from the SAME video list —
 * NO second `getHosterList` call.
 *
 * CRITICAL FIX (hoster name tracking): The previous version lost the
 * `hoster.hosterName` when collecting videos — it just did `videos.addAll()`.
 * This caused the server name to be parsed from the video title (which often
 * doesn't contain it). Now we carry `VideoEntry(video, hosterName)` throughout.
 */
class VideoResolver {

    companion object {
        private const val TAG = "Anikuta:Core:VideoResolver"
        private const val SOURCE_TIMEOUT_MS = 30_000L
    }

    fun resolve(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): Flow<ResolverState> = flow {
        Logger.i(TAG) { "Resolving videos for: ${episode.url} (source: ${source.name}, epNum: ${episode.episode_number})" }
        emit(ResolverState.Loading())

        try {
            val entries = withContext(Dispatchers.IO) {
                resolveVideoEntries(source, episode)
            }
            Logger.d(TAG) { "Fetched ${entries.size} raw video entries from ${source.name}" }

            val validEntries = entries.filter { it.video.videoUrl.isNotBlank() }
            if (validEntries.size < entries.size) {
                Logger.w(TAG) { "Filtered out ${entries.size - validEntries.size} videos with blank videoUrl" }
            }

            if (validEntries.isEmpty()) {
                Logger.w(TAG) { "No valid videos found for ${episode.url} (all had blank videoUrl)" }
                emit(ResolverState.Error("No videos available"))
                return@flow
            }

            val resolvedVideos = validEntries.map { entry ->
                val video = entry.video
                Logger.i(TAG) { "Valid video: quality='${video.videoTitle}', subs=${video.subtitleTracks.size}, audio=${video.audioTracks.size}, hoster=${entry.hosterName}" }
                if (video.subtitleTracks.isNotEmpty()) {
                    video.subtitleTracks.forEach { sub ->
                        Logger.i(TAG) { "  Subtitle track: url=${sub.url.take(80)}, lang=${sub.lang}" }
                    }
                }
                ResolvedVideo(
                    url = video.videoUrl,
                    quality = extractQuality(video.videoTitle),
                    directUrl = video.videoUrl,
                    headers = formatHeaders(video.headers),
                    subtitleTracks = video.subtitleTracks.map { ResolverSubtitleTrack(it.url, it.lang) },
                    audioTracks = video.audioTracks.map { ResolverSubtitleTrack(it.url, it.lang) },
                )
            }

            val totalSubs = resolvedVideos.sumOf { it.subtitleTracks.size }
            Logger.i(TAG) { "Resolved ${resolvedVideos.size} videos, total subtitle tracks: $totalSubs" }

            // Phase 2: build structured servers here (avoids needing a second call to buildServers).
            val servers = groupIntoServers(validEntries, sourceName)
            emit(ResolverState.Success(resolvedVideos, validEntries, servers))

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
     */
    fun buildServers(
        entries: List<VideoEntry>,
        sourceName: String,
    ): List<ResolverServer> {
        val validEntries = entries.filter { it.video.videoUrl.isNotBlank() }
        if (validEntries.isEmpty()) return emptyList()
        val servers = groupIntoServers(validEntries, sourceName)
        Logger.i(TAG) { "Built ${servers.size} servers from ${validEntries.size} videos" }
        servers.forEach { server ->
            Logger.i(TAG) { "  Server: ${server.name} — ${server.audioVersions.size} audio versions, ${server.audioVersions.sumOf { it.videos.size }} videos" }
            server.audioVersions.forEach { av ->
                Logger.i(TAG) { "    Audio: ${av.label} — ${av.videos.size} qualities: ${av.videos.map { it.quality }}" }
            }
        }
        return servers
    }

    /**
     * Try getHosterList first (ext-lib 16+), fall back to getVideoList.
     * Returns VideoEntry list (carries hoster name alongside each video).
     */
    private suspend fun resolveVideoEntries(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): List<VideoEntry> {
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
            val entries = mutableListOf<VideoEntry>()
            for (hoster in hosters) {
                val hosterVideos = hoster.videoList
                if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                    Logger.d(TAG) { "Hoster '${hoster.hosterName}' has ${hosterVideos.size} pre-loaded videos" }
                    for (video in hosterVideos) {
                        entries.add(VideoEntry(video, hoster.hosterName))
                    }
                } else {
                    Logger.d(TAG) { "Hoster '${hoster.hosterName}' is lazy — calling getVideoList(hoster)" }
                    try {
                        val resolved = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            source.getVideoList(hoster)
                        } ?: emptyList()
                        for (video in resolved) {
                            entries.add(VideoEntry(video, hoster.hosterName))
                        }
                    } catch (e: Throwable) {
                        Logger.w(TAG, e) { "getVideoList for hoster ${hoster.hosterName} failed: ${e.message}" }
                    }
                }
            }
            return entries
        }

        // Fallback: old direct API (ext-lib < 16) — no hoster names available.
        Logger.d(TAG) { "Falling back to getVideoList(episode) for ${source.name}" }
        return try {
            val videos = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                source.getVideoList(episode)
            } ?: emptyList()
            videos.map { VideoEntry(it, null) }
        } catch (e: Throwable) {
            Logger.e(TAG, e) { "getVideoList(episode) failed for ${source.name}: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Extract just the quality/resolution from a video title.
     * Examples:
     *   "SUB - 1080p" → "1080p"
     *   "DUB - 720p" → "720p"
     *   "1080p" → "1080p"
     *   "Default" → "Default"
     */
    private fun extractQuality(videoTitle: String): String {
        if (videoTitle.isBlank()) return "Default"
        // Try to find a resolution pattern like "1080p", "720p", "360p"
        val qualityPattern = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
        qualityPattern.find(videoTitle)?.let { return "${it.groupValues[1]}p" }
        // No resolution found — return the title as-is (might be "Default" or custom)
        return videoTitle
    }

    private fun formatError(e: Throwable): String {
        val type = e::class.java.simpleName
        val msg = e.message ?: "Unknown error"
        return "$type: $msg"
    }

    private fun formatHeaders(headers: okhttp3.Headers?): String {
        if (headers == null || headers.size == 0) return ""
        return (0 until headers.size).joinToString(",") { i ->
            "${headers.name(i)}: ${headers.value(i)}"
        }
    }

    /**
     * Group video entries into a 3-tier server/audio/quality hierarchy.
     *
     * Server name priority:
     * 1. Hoster name (if available — from getHosterList)
     * 2. Parsed from video title (known server names or text before " - ")
     * 3. URL host
     * 4. Source name (fallback)
     */
    private fun groupIntoServers(
        entries: List<VideoEntry>,
        sourceName: String,
    ): List<ResolverServer> {
        val byServer = entries.groupBy { entry ->
            // Primary: use hoster name if available
            entry.hosterName?.takeIf { it.isNotBlank() }
                ?: parseServerName(entry.video.videoTitle, entry.video.videoUrl, sourceName)
        }

        return byServer.entries.map { (serverName, serverEntries) ->
            val byAudio = serverEntries.groupBy { entry ->
                parseAudioVersion(entry.video.videoTitle)
            }
            val audioVersions = byAudio.entries.map { (audioLabel, audioEntries) ->
                ResolverAudioVersion(
                    label = audioLabel,
                    videos = audioEntries.map { entry ->
                        val video = entry.video
                        val quality = extractQuality(video.videoTitle)
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
     * Parse the server name from a video title (fallback when no hoster name).
     *
     * Handles formats like:
     *   "HD-1 - Sub - 1080p"       → "HD-1"
     *   "Vidstream-2 - Sub - 1080p" → "Vidstream-2"
     *   "VidPlay-1 - Dub - 720p"   → "VidPlay-1"
     *   "SUB - 1080p"              → (no server name, parse from URL)
     */
    private fun parseServerName(videoTitle: String, url: String, sourceName: String): String {
        // First, try splitting by " - " and taking the first segment.
        // This handles "ServerName - AudioVersion - Quality" format.
        val dashIdx = videoTitle.indexOf(" - ")
        if (dashIdx > 0) {
            val candidate = videoTitle.substring(0, dashIdx).trim()
            if (candidate.isNotEmpty() && candidate.length <= 30) return candidate
        }
        // No " - " separator — try known server names as prefix.
        val knownServers = listOf(
            "Vidstream", "Mp4Upload", "Doodstream", "Streamtape", "MixDrop",
            "StreamSB", "Vidcloud", "Beta2", "Akira", "Googledrive", "HD-1",
            "HD-2", "StreamX", "Vidtubing", "Fastplay", "Arenabokeh",
        )
        val titleLower = videoTitle.lowercase()
        for (server in knownServers) {
            if (titleLower.startsWith(server.lowercase())) return server
        }
        return runCatching { java.net.URI(url).host }.getOrNull() ?: sourceName
    }

    /**
     * Parse the audio-version from a video title.
     *
     * Handles multiple title formats:
     *   "SUB - 1080p"              → "SUB"    (audio at start)
     *   "HD-1 - Sub - 1080p"       → "SUB"    (audio in middle, mixed case)
     *   "Vidstream-2 - Dub - 720p" → "DUB"    (audio in middle, mixed case)
     *   "HSUB - 360p"              → "HSUB"
     *   "1080p"                    → "Default" (no audio version found)
     *
     * The regex searches the ENTIRE title (not just the start) and is
     * case-insensitive — handles "Sub", "sub", "SUB", "DUB", "Dub", etc.
     * Normalizes to uppercase for consistent grouping.
     */
    private fun parseAudioVersion(videoTitle: String): String {
        // Case-insensitive search for audio version keywords anywhere in the title.
        // Word-boundary aware so "HSub" doesn't match as "Sub" first.
        val patterns = listOf(
            Regex("\\b(hsub|hardsub|h-hardsub)\\b", RegexOption.IGNORE_CASE) to "HSUB",
            Regex("\\b(subbed|sub)\\b", RegexOption.IGNORE_CASE) to "SUB",
            Regex("\\b(dubbed|dub)\\b", RegexOption.IGNORE_CASE) to "DUB",
            Regex("\\b(mix)\\b", RegexOption.IGNORE_CASE) to "MIX",
            Regex("\\b(raw)\\b", RegexOption.IGNORE_CASE) to "RAW",
        )
        for ((pattern, label) in patterns) {
            if (pattern.containsMatchIn(videoTitle)) {
                return label
            }
        }
        return "Default"
    }

    /**
     * Build a stable videoTitle for matching across re-resolutions.
     */
    private fun buildVideoTitle(server: String, audio: String, quality: String, url: String): String {
        val urlHash = url.hashCode().toString(16)
        return "$server|$audio|$quality|$urlHash"
    }
}
