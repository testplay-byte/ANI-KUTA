package com.confused.anikuta.feature.cswatch.api

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the CloudStream watch screen (task 52 / round 12).
 *
 * Mirrors the aniyomi [com.confused.anikuta.feature.watch.WatchKey] SHAPE
 * (serializable key carrying the episode + list context the screen needs)
 * without touching it — the two ecosystems' watch stacks stay separate.
 *
 * The episode "url" for CloudStream content is the provider's opaque data
 * handle (the string `loadLinks` receives) — see the bridge's `toEpisodes()`.
 */
@Serializable
data class CsWatchKey(
    /** The CloudStream provider name — the resolver's lookup key. */
    val providerName: String,
    val animeTitle: String,
    /** The CS episode data handle (SEpisode.url for bridged content). */
    val episodeData: String,
    val episodeNumber: Float,
    val episodeTitle: String = "",
    /** Episode list for in-player switching: "data\u001FepNum\u001Fname" lines
     *  (\u001F = the app-wide EPISODE_FIELD_DELIMITER — URLs may contain '|'). */
    val episodeListSerialized: String = "",
    /** The content's stable main_id — watch progress rides the provider-agnostic store. */
    val mainId: String = "",
    /** The bridged source's synthetic id (CsSourceIds) — parity with WatchKey + future lookups. */
    val sourceId: Long = 0L,
    /** Resume hint in ms; 0 = fresh (the screen still self-checks the progress store). */
    val startPosition: Long = 0L,
) : NavKey {

    /** Parses [episodeListSerialized] into lightweight rows for the episodes sheet. */
    fun parseEpisodeList(): List<CsSimpleEpisode> {
        if (episodeListSerialized.isBlank()) return emptyList()
        val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
        return episodeListSerialized.split("\n").mapNotNull { line ->
            val parts = line.split(delim, limit = 3)
            if (parts.size == 3) {
                CsSimpleEpisode(
                    data = parts[0],
                    episodeNumber = parts[1].toFloatOrNull() ?: 0f,
                    name = parts[2],
                )
            } else null
        }
    }
}

/** Lightweight episode row for the CS episodes sheet. */
data class CsSimpleEpisode(
    val data: String,
    val episodeNumber: Float,
    val name: String,
)
