package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListStreamingEpisode
import com.confused.anikuta.core.common.Logger

/**
 * Fetches episode metadata (titles, thumbnails, descriptions, air dates) from
 * multiple sources, merged with first-non-null-wins priority.
 *
 * ## Architecture (highly customizable + future-proof)
 *
 * This is a pluggable system — new sources can be added without modifying
 * existing code. Each [EpisodeMetadataSource] provides metadata for a subset
 * of fields. The fetcher calls all sources in parallel, then merges the
 * results per-field.
 *
 * ## Current sources
 *
 * 1. **AniList streamingEpisodes** — provides title + thumbnail (simplest,
 *    no auth needed).
 *
 * ## Future sources (planned)
 *
 * - **Jikan (MAL API)** — provides title + air date
 * - **Anikage.cc** — provides title + description + thumbnail + air date
 *   (most complete)
 *
 * ## Data flow
 *
 * 1. DetailsViewModel calls `fetchEpisodeMetadata(anilistId, episodeCount)`
 * 2. Fetcher calls all registered sources in parallel
 * 3. Results are merged per-field (first non-null wins)
 * 4. Returns `Map<Int, EpisodeMetadata>` keyed by episode number
 * 5. DetailsViewModel exposes via StateFlow
 * 6. DetailsScreen episode list reads the metadata
 *
 * ## No caching (for now)
 *
 * The user said: "for the current time being you should focus on implementing
 * this metadata fetching functionality on the details page only. There is no
 * need to set up the caching functionality." Every time the user enters the
 * details page, it re-fetches. Caching is a future phase.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes".
 */
class EpisodeMetadataFetcher(
    private val aniListApi: AniListApi,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes"
    }

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anilistId The AniList anime ID.
     * @param episodeCount The number of episodes (from the extension's episode list).
     *   Used to map streaming episode data to episode numbers.
     * @return Map of episode number → EpisodeMetadata. May be empty if no
     *   source provides data.
     */
    suspend fun fetchEpisodeMetadata(
        anilistId: Int,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> {
        Logger.i(TAG) { "Fetching episode metadata: anilistId=$anilistId, episodeCount=$episodeCount" }

        val result = mutableMapOf<Int, EpisodeMetadata>()

        // ── Source 1: AniList streamingEpisodes ──
        try {
            val streamingEpisodes = aniListApi.fetchStreamingEpisodes(anilistId)
            Logger.i(TAG) { "AniList streamingEpisodes: ${streamingEpisodes.size} episodes" }

            if (streamingEpisodes.isNotEmpty()) {
                // Map streaming episodes to episode numbers (1-indexed).
                for ((index, ep) in streamingEpisodes.withIndex()) {
                    val epNum = index + 1
                    if (epNum > episodeCount) break // Don't exceed the extension's episode count

                    val existing = result[epNum]
                    val merged = EpisodeMetadata(
                        episodeKey = "al:$anilistId|ep:$epNum",
                        number = epNum.toDouble(),
                        title = existing?.title ?: ep.title,
                        thumbnailUrl = existing?.thumbnailUrl ?: ep.thumbnail,
                        airDate = existing?.airDate,
                        description = existing?.description,
                    )
                    result[epNum] = merged
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "AniList streamingEpisodes fetch failed: ${e.message}" }
        }

        Logger.i(TAG) { "Episode metadata fetch complete: ${result.size} episodes" }
        return result
    }
}

/**
 * Interface for a pluggable episode metadata source.
 *
 * Future sources (Jikan, Anikage.cc) implement this interface.
 * The fetcher calls all sources in parallel and merges results.
 */
interface EpisodeMetadataSource {
    /** The source name (for logging). */
    val name: String

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anilistId The AniList anime ID.
     * @param episodeCount The number of episodes from the extension.
     * @return Map of episode number → partial EpisodeMetadata (only fill fields
     *   this source provides; leave others null).
     */
    suspend fun fetch(anilistId: Int, episodeCount: Int): Map<Int, EpisodeMetadata>
}
