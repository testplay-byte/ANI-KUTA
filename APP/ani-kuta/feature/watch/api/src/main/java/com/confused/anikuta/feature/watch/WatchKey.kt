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
     *  Each entry is "url|episodeNumber|name" separated by newlines. */
    val episodeListSerialized: String = "",
    /** HTTP headers for the video URL — CRITICAL for playback.
     *  Extensions provide headers (Referer, User-Agent, etc.) that upstream
     *  servers require. Without these, the server returns 403 Forbidden.
     *  Format: "Key: Value,Key2: Value2" (comma-separated, like MPV's
     *  http-header-fields option). */
    val videoHeaders: String = "",
) : NavKey {

    /**
     * Parse the serialized episode list into a list of SimpleEpisode.
     * Format: "url|episodeNumber|name" per line.
     */
    fun parseEpisodeList(): List<SimpleEpisode> {
        if (episodeListSerialized.isBlank()) return emptyList()
        return episodeListSerialized.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) {
                SimpleEpisode(
                    url = parts[0],
                    episodeNumber = parts[1].toFloatOrNull() ?: 0f,
                    name = parts[2],
                )
            } else null
        }
    }
}

/** Lightweight episode info for the watch screen's episode list. */
data class SimpleEpisode(
    val url: String,
    val episodeNumber: Float,
    val name: String,
)
