package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

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
 *
 * Task 50 (round 10, Fix C — pipeline separation): the two extension
 * ecosystems have fundamentally different link-resolution semantics, so
 * [resolve] dispatches into TWO separate pipelines via
 * [AnimeHttpSource.isCloudStreamBridged]:
 *  - **Aniyomi pipeline** ([resolveAniyomiEntries], AniyomiSourcePipeline.kt)
 *    — hoster-list probing with memoized unsupported-API detection, per-hoster
 *    error isolation, resolver-side timeouts, and the lazy `resolveVideo` pass.
 *  - **CloudStream pipeline** ([resolveCloudstreamEntries],
 *    CloudstreamSourcePipeline.kt) — upstream-mirroring semantics: no outer
 *    timeout (the bridge bounds `provider.loadLinks` itself and KEEPS partial
 *    links on timeout), no hoster probe, and a 20-minute link cache
 *    ([CloudstreamLinkCache]) so re-resolve / mirror-switch / return-to-episode
 *    replay instantly. `forceRefresh` bypasses that cache (dead download links).
 */
class VideoResolver {

    companion object {
        private const val TAG = "Anikuta:Core:VideoResolver"

        /** H-1: name of the single raw server used when NOTHING in the list parses. */
        private const val RAW_SERVER_NAME = "All Videos"
    }

    /**
     * Resolves the episode's videos.
     *
     * @param forceRefresh Task 50: bypass the CloudStream link cache — used by
     *   the download re-resolve path, where the cached link list may contain
     *   the very URLs that just died. No effect on the aniyomi pipeline.
     */
    fun resolve(
        source: AnimeHttpSource,
        episode: SEpisode,
        forceRefresh: Boolean = false,
    ): Flow<ResolverState> = flow {
        Logger.i(TAG) {
            "Resolving videos for: ${episode.url} (source: ${source.name}, " +
                "epNum: ${episode.episode_number}, cs=${source.isCloudStreamBridged}, " +
                "forceRefresh=$forceRefresh)"
        }
        emit(ResolverState.Loading())

        try {
            val entries = withContext(Dispatchers.IO) {
                resolveVideoEntries(source, episode, forceRefresh)
            }

            // Video.kt's deprecated constructor maps a null videoUrl to the
            // literal "null" — treat that exactly like a blank URL.
            val validEntries = entries.filter { it.video.videoUrl.isNotBlank() && it.video.videoUrl != "null" }
            if (validEntries.size < entries.size) {
                Logger.w(TAG) { "Filtered out ${entries.size - validEntries.size} videos with blank/null videoUrl" }
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
                    quality = extractQuality(video),
                    directUrl = video.videoUrl,
                    headers = formatHeaders(video.headers),
                    subtitleTracks = video.subtitleTracks.map {
                        ResolverSubtitleTrack(it.url, it.lang, trackHeadersCsv(it.headers))
                    },
                    audioTracks = video.audioTracks.map {
                        ResolverSubtitleTrack(it.url, it.lang, trackHeadersCsv(it.headers))
                    },
                )
            }

            val totalSubs = resolvedVideos.sumOf { it.subtitleTracks.size }
            Logger.i(TAG) { "Resolved ${resolvedVideos.size} videos, total subtitle tracks: $totalSubs" }

            // Phase 2: build structured servers here (avoids needing a second call to buildServers).
            val servers = groupIntoServers(validEntries)
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
        // Same blank/"null" filter as resolve() — the deprecated Video
        // constructor maps a null videoUrl to the literal "null".
        val validEntries = entries.filter { it.video.videoUrl.isNotBlank() && it.video.videoUrl != "null" }
        if (validEntries.isEmpty()) return emptyList()
        val servers = groupIntoServers(validEntries)
        Logger.i(TAG) { "Built ${servers.size} servers from ${validEntries.size} videos (source: $sourceName)" }
        servers.forEach { server ->
            Logger.i(TAG) { "  Server: ${server.name} — ${server.audioVersions.size} audio versions, ${server.audioVersions.sumOf { it.videos.size }} videos" }
            server.audioVersions.forEach { av ->
                Logger.i(TAG) { "    Audio: ${av.label} — ${av.videos.size} qualities: ${av.videos.map { it.quality }}" }
            }
        }
        return servers
    }

    /**
     * Task 50 (round 10, Fix C): dispatch into the ecosystem-specific
     * pipeline. CloudStream-bridged sources manage their OWN link-resolution
     * budget inside getVideoList (upstream semantics: partial links kept on
     * timeout) — they must not be wrapped in the aniyomi pipeline's timeouts
     * or probed via getHosterList. Everything else keeps the restored
     * aniyomi behavior.
     */
    private suspend fun resolveVideoEntries(
        source: AnimeHttpSource,
        episode: SEpisode,
        forceRefresh: Boolean,
    ): List<VideoEntry> =
        if (source.isCloudStreamBridged) {
            resolveCloudstreamEntries(source, episode, forceRefresh)
        } else {
            resolveAniyomiEntries(source, episode)
        }

    /**
     * Extract just the quality/resolution for a video.
     *
     * H-1 (old-kuta VideoTitleParser port): prefer the extension-provided
     * [Video.resolution] field, then the `(\d{3,4})p` title pattern, then the
     * title itself.
     * Examples:
     *   resolution=1080                  → "1080p"
     *   "SUB - 1080p"                    → "1080p"
     *   "DUB - 720p"                     → "720p"
     *   "1080p"                          → "1080p"
     *   blank title, no resolution       → "Default"
     */
    private fun extractQuality(video: Video): String {
        video.resolution?.let { return "${it}p" }
        val videoTitle = video.videoTitle
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
        // Task 49: bound the sheet message — ResolverSheet renders it verbatim
        // and some provider exceptions carry whole stack pages.
        return ("$type: $msg").take(300)
    }

    private fun formatHeaders(headers: okhttp3.Headers?): String {
        if (headers == null || headers.size == 0) return ""
        return (0 until headers.size).joinToString(",") { i ->
            "${headers.name(i)}: ${headers.value(i)}"
        }
    }

    /**
     * Task 48 (per-track subtitle headers): a [eu.kanade.tachiyomi.animesource.model.Track]'s
     * header map → the MPV csv format ("Key: Value,Key2: Value2") the rest of
     * the pipeline speaks; null/empty maps stay null (→ parent video headers).
     */
    private fun trackHeadersCsv(headers: Map<String, String>?): String? {
        if (headers.isNullOrEmpty()) return null
        return headers.entries.joinToString(",") { "${it.key}: ${it.value}" }.
            ifEmpty { null }
    }

    // ── H-1: server grouping intelligence (old-kuta VideoTitleParser port) ──

    /**
     * Audio-version + language tokens (H-1): these are NEVER treated as
     * server names (case-insensitive whole-part match while scanning
     * `" - "` title segments).
     *
     * Documented edge: a CloudStream link source literally named one of
     * these tokens ("ENG", "SUB", …) falls through to auto-naming
     * ("Server A") — rare and acceptable.
     */
    private val AUDIO_TOKENS = setOf(
        "SUB", "SUBBED", "HSUB", "HARDSUB", "H-SUB", "HARDSUBBED",
        "DUB", "DUBBED", "A-DUB", "ADUB",
        // Language names extensions sometimes use as the audio-version part:
        "JAPANESE", "ENGLISH", "SPANISH", "FRENCH", "GERMAN", "PORTUGUESE",
        "ITALIAN", "KOREAN", "CHINESE", "RUSSIAN",
        "ENG", "JPN", "ESP", "FRA", "DEU", "POR", "ITA", "KOR", "CHI", "RUS",
    )

    /** A pure quality segment, e.g. "1080p" / "720p" — never a server name. */
    private val QUALITY_PART_REGEX = Regex("^\\d{3,4}p$", RegexOption.IGNORE_CASE)

    /** Audio-version display order: SUB → DUB → HSUB → MIX → RAW → Default. */
    private val AUDIO_ORDER = listOf("SUB", "DUB", "HSUB", "MIX", "RAW", "Default")

    private fun isAudioToken(text: String): Boolean = text.uppercase() in AUDIO_TOKENS

    /**
     * Group video entries into a 3-tier server/audio/quality hierarchy.
     *
     * H-1 (round 10 — ported from old-kuta's VideoTitleParser, replacing the
     * old first-segment-blindly parser that named "SUB" as a server):
     *
     *  - **Server name priority**: (1) hoster name when non-blank and not the
     *    [Hoster.NO_HOSTER_LIST] sentinel; (2) the first `" - "` title segment
     *    that is not an audio token, not a pure quality pattern and not all
     *    digits; (3) unparseable entries are auto-named "Server A", "Server
     *    B", … (one per entry — old-kuta rule). When NOTHING in the whole
     *    list parses, everything lands in ONE raw server named "All Videos"
     *    with audio "Default" (the old RawResolverStrategy, inlined).
     *  - **Sorting**: servers alphabetically (case-insensitive); audio
     *    versions [AUDIO_ORDER]; videos by quality descending, numeric-aware
     *    ("1080p" > "720p" > unknown), unknown last.
     *
     * HARD CONSTRAINT: every [ResolverVideo.videoTitle] keeps the exact
     * `"server|audio|quality|urlHash"` pipe format produced by
     * [buildVideoTitle] — the WatchScreen quality ladder and the
     * episode-switch matcher parse it.
     */
    private fun groupIntoServers(entries: List<VideoEntry>): List<ResolverServer> {
        // Nothing yields a server name anywhere → single raw fallback server.
        if (entries.all { serverNameOf(it) == null }) {
            return listOf(
                ResolverServer(
                    name = RAW_SERVER_NAME,
                    audioVersions = listOf(
                        ResolverAudioVersion(
                            label = "Default",
                            videos = entries
                                .sortedByDescending { qualitySortRank(extractQuality(it.video)) }
                                .map { entry -> buildResolverVideo(entry, RAW_SERVER_NAME, "Default") },
                        ),
                    ),
                ),
            )
        }

        // Resolve server names; unparseable entries get auto-names.
        var autoIndex = 0
        val named = ArrayList<Pair<String, VideoEntry>>(entries.size)
        for (entry in entries) {
            val server = serverNameOf(entry) ?: autoServerName(autoIndex++)
            named.add(server to entry)
        }

        return named
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
            .map { (serverName, serverEntries) ->
                val audioVersions = serverEntries
                    .groupBy { parseAudioVersion(it.video.videoTitle) }
                    .entries
                    .sortedBy { (label, _) -> audioSortRank(label) }
                    .map { (audioLabel, audioEntries) ->
                        ResolverAudioVersion(
                            label = audioLabel,
                            videos = audioEntries
                                .sortedByDescending { qualitySortRank(extractQuality(it.video)) }
                                .map { entry -> buildResolverVideo(entry, serverName, audioLabel) },
                        )
                    }
                ResolverServer(name = serverName, audioVersions = audioVersions)
            }
    }

    /**
     * Server-name priority for one entry (H-1): hoster name (non-blank, not
     * the [Hoster.NO_HOSTER_LIST] sentinel — the flat getVideoList path wraps
     * its videos in a sentinel hoster, which carries no server information),
     * else [parseServerName] on the title, else null (unparseable).
     */
    private fun serverNameOf(entry: VideoEntry): String? {
        entry.hosterName
            ?.takeIf { it.isNotBlank() && it != Hoster.NO_HOSTER_LIST }
            ?.let { return it }
        return parseServerName(entry.video.videoTitle)
    }

    /**
     * Parse the server name from a video title (fallback when no hoster name).
     *
     * The first `" - "` segment (or the whole title when there is no
     * separator) that is NOT an audio token ([AUDIO_TOKENS]), NOT a pure
     * quality pattern (`1080p`) and NOT all digits is the server name.
     * Returns null when no segment qualifies.
     *
     * Handles formats like:
     *   "HD-1 - Sub - 1080p"        → "HD-1"   (first segment wins)
     *   "Vidstream-2 - Dub - 720p"  → "Vidstream-2"
     *   "SUB - 1080p"               → null     (audio token + quality only)
     *   "SourceLabel - Label 720p"  → "SourceLabel" (CS bridge titles)
     */
    private fun parseServerName(videoTitle: String): String? {
        val parts = videoTitle.split(" - ").map { it.trim() }.filter { it.isNotBlank() }
        for (part in parts) {
            if (isAudioToken(part)) continue
            if (QUALITY_PART_REGEX.matches(part)) continue
            if (part.all { it.isDigit() }) continue
            return part
        }
        return null
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

    /** Auto-name for an unparseable entry: "Server A", "Server B", … (H-1). */
    private fun autoServerName(index: Int): String =
        if (index < 26) "Server ${'A' + index}" else "Server ${index + 1}"

    /** [AUDIO_ORDER] rank for sorting; unknown labels sort last. */
    private fun audioSortRank(label: String): Int {
        val idx = AUDIO_ORDER.indexOf(label)
        return if (idx >= 0) idx else AUDIO_ORDER.size
    }

    /**
     * Numeric quality rank for descending sort: "1080p" → 1080, "720p" → 720.
     * Unknown labels ("Default", custom) rank [Int.MIN_VALUE] → sort last.
     */
    private fun qualitySortRank(quality: String): Int =
        quality.removeSuffix("p").toIntOrNull() ?: Int.MIN_VALUE

    /** Maps one entry into the tier hierarchy's leaf [ResolverVideo]. */
    private fun buildResolverVideo(
        entry: VideoEntry,
        serverName: String,
        audioLabel: String,
    ): ResolverVideo {
        val video = entry.video
        val quality = extractQuality(video)
        return ResolverVideo(
            quality = quality,
            url = video.videoUrl,
            videoTitle = buildVideoTitle(serverName, audioLabel, quality, video.videoUrl),
            videoHeaders = formatHeaders(video.headers),
            subtitleTracks = video.subtitleTracks.map {
                ResolverSubtitleTrack(it.url, it.lang, trackHeadersCsv(it.headers))
            },
            audioTracks = video.audioTracks.map {
                ResolverSubtitleTrack(it.url, it.lang, trackHeadersCsv(it.headers))
            },
        )
    }

    /**
     * Build a stable videoTitle for matching across re-resolutions.
     * Format: "server|audio|quality|urlHash" — parsed by the WatchScreen
     * quality ladder and the episode-switch matcher. DO NOT change.
     */
    private fun buildVideoTitle(server: String, audio: String, quality: String, url: String): String {
        val urlHash = url.hashCode().toString(16)
        return "$server|$audio|$quality|$urlHash"
    }
}
