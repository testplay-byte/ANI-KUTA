package com.confused.anikuta.feature.watch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Watch screen. Carries the video URL + metadata needed
 * to play the video + the episode list for episode switching.
 */
@Serializable
data class WatchKey(
    val videoUrl: String,
    val animeTitle: String,
    val quality: String,
    val episodeUrl: String = "",
    val episodeNumber: Float = 0f,
    val episodeTitle: String = "",
    /** Serialized episode list for episode switching in the watch screen.
     *  Each entry is "url\u001FepisodeNumber\u001Fname" separated by newlines.
     *
     *  CRITICAL: Uses \u001F (ASCII Unit Separator) as the field delimiter
     *  instead of '|' because episode URLs can contain '|' characters
     *  (e.g. some extensions use '|' in their URL scheme). Using '|' as a
     *  delimiter corrupts the URL, episode number, AND name when the URL
     *  contains '|'. \u001F is a control character that never appears in
     *  URLs or episode names. */
    val episodeListSerialized: String = "",
    /** HTTP headers for the video URL — CRITICAL for playback.
     *  Extensions provide headers (Referer, User-Agent, etc.) that upstream
     *  servers require. Without these, the server returns 403 Forbidden.
     *  Format: "Key: Value,Key2: Value2" (comma-separated, like MPV's
     *  http-header-fields option). */
    val videoHeaders: String = "",

    /** Registry key for the full resolved-servers list (for QualitySheet).
     *  The Details screen resolves the videos, stores them in
     *  [com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry], and
     *  passes the key here. The watch screen reads the servers from the
     *  registry to populate the QualitySheet accordion.
     *  Empty string = no pre-resolved servers (QualitySheet will show empty). */
    val resolvedVideosKey: String = "",

    /** The source ID for re-resolving episodes when switching.
     *  When the user taps a different episode in the list, the watch screen
     *  uses this ID to get the source from the ExtensionManager and call
     *  VideoResolver.resolve() for the new episode. */
    val sourceId: Long = 0L,

    /** Serialized subtitle tracks for external subtitle loading via MPV's sub-add.
     *  Format: "url\u001Flang" per line, separated by newlines.
     *  Populated from the picked video's `subtitleTracks` — the extension provides
     *  these as `Track(url, lang)` pairs. For AniKotoS, the URL is a localhost proxy
     *  URL like `http://127.0.0.1:PORT/sub/0/0`.
     *
     *  CRITICAL: Carrying these directly in WatchKey (instead of relying on
     *  ResolvedVideosRegistry lookup) ensures subtitles are always available
     *  even if the registry lookup fails (URL mismatch, key blank, etc.). */
    val subtitleTracksSerialized: String = "",

    /** Serialized audio tracks for external audio loading via MPV's audio-add.
     *  Same format as [subtitleTracksSerialized]. */
    val audioTracksSerialized: String = "",

    /** Serialized episode metadata for the watch page episode list + currently-playing section.
     *  Format: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator" per line.
     *  Empty string = no metadata available (episode list shows basic rows). */
    val episodeMetadataSerialized: String = "",
) : NavKey {

    /**
     * Parse the serialized episode list into a list of SimpleEpisode.
     * Format: "url\u001FepisodeNumber\u001Fname" per line.
     */
    fun parseEpisodeList(): List<SimpleEpisode> {
        if (episodeListSerialized.isBlank()) return emptyList()
        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
        return episodeListSerialized.split("\n").mapNotNull { line ->
            val parts = line.split(delim, limit = 3)
            if (parts.size == 3) {
                SimpleEpisode(
                    url = parts[0],
                    episodeNumber = parts[1].toFloatOrNull() ?: 0f,
                    name = parts[2],
                )
            } else null
        }
    }

    /**
     * Parse the serialized subtitle tracks into a list of (url, lang) pairs.
     * Format: "url\u001Flang" per line.
     */
    fun parseSubtitleTracks(): List<Pair<String, String>> {
        if (subtitleTracksSerialized.isBlank()) return emptyList()
        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
        return subtitleTracksSerialized.split("\n").mapNotNull { line ->
            val parts = line.split(delim, limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }

    /**
     * Parse the serialized audio tracks into a list of (url, lang) pairs.
     * Format: "url\u001Flang" per line.
     */
    fun parseAudioTracks(): List<Pair<String, String>> {
        if (audioTracksSerialized.isBlank()) return emptyList()
        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
        return audioTracksSerialized.split("\n").mapNotNull { line ->
            val parts = line.split(delim, limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }

    /**
     * Parse the serialized episode metadata into a map keyed by episode number.
     * Format: "epNum\u001Ftitle\u001FthumbnailUrl\u001FairDateMillis\u001Fdescription\u001Fscanlator" per line.
     */
    fun parseEpisodeMetadata(): Map<Int, WatchEpisodeMeta> {
        if (episodeMetadataSerialized.isBlank()) return emptyMap()
        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
        return episodeMetadataSerialized.split("\n").mapNotNull { line ->
            val parts = line.split(delim, limit = 6)
            if (parts.size >= 5) {
                val epNum = parts[0].toIntOrNull() ?: return@mapNotNull null
                WatchEpisodeMeta(
                    episodeNumber = epNum,
                    title = parts[1].ifBlank { null },
                    thumbnailUrl = parts[2].ifBlank { null },
                    airDateMillis = parts[3].toLongOrNull() ?: 0L,
                    description = parts[4].ifBlank { null },
                    scanlator = if (parts.size > 5) parts[5] else "",
                )
            } else null
        }.associateBy { it.episodeNumber }
    }
}

/** Lightweight episode info for the watch screen's episode list. */
data class SimpleEpisode(
    val url: String,
    val episodeNumber: Float,
    val name: String,
)

/** Episode metadata for the watch page (passed from DetailsScreen via WatchKey). */
data class WatchEpisodeMeta(
    val episodeNumber: Int,
    val title: String?,
    val thumbnailUrl: String?,
    val airDateMillis: Long,
    val description: String?,
    val scanlator: String,
)
