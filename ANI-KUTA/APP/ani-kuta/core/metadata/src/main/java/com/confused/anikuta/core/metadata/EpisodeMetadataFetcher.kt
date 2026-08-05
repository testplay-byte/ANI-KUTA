package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.model.AniListStreamingEpisode
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches episode metadata (titles, thumbnails, descriptions, air dates) from
 * multiple sources, merged with first-non-null-wins priority.
 *
 * ## Sources (in priority order)
 *
 * 1. **Anikage.cc** (PRIMARY) — provides TITLE, DESCRIPTION, THUMBNAIL, AIR_DATE.
 *    Uses AniList ID. Most complete source.
 *    Endpoint: `https://anikage.cc/api/media/anime/{anilistId}/episodes`
 *
 * 2. **Jikan (MAL)** — provides TITLE, AIR_DATE. Uses MAL ID (from AniList's idMal).
 *    Endpoint: `https://api.jikan.moe/v4/anime/{malId}/episodes`
 *
 * 3. **AniList streamingEpisodes** — provides TITLE, THUMBNAIL. Uses AniList ID.
 *    Often returns 0 episodes (data limitation).
 *
 * ## Data flow
 *
 * 1. DetailsViewModel calls `fetchEpisodeMetadata(anilistId, malId, episodeCount)`
 * 2. Fetcher tries Anikage.cc first → if it returns data, merges it
 * 3. Then tries Jikan (if malId available) → merges missing fields
 * 4. Then tries AniList streaming → merges missing fields
 * 5. Returns `Map<Int, EpisodeMetadata>` keyed by episode number
 *
 * ## No caching (for now)
 *
 * Per user request: "for the current time being you should focus on implementing
 * this metadata fetching functionality on the details page only. There is no
 * need to set up the caching functionality." Caching is a future phase.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes".
 */
class EpisodeMetadataFetcher(
    private val aniListApi: AniListApi,
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes"
        private const val ANIKAGE_BASE = "https://anikage.cc/api/media/anime"
        private const val JIKAN_BASE = "https://api.jikan.moe/v4"
    }

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anilistId The AniList anime ID.
     * @param malId The MyAnimeList ID (from AniList's idMal field). May be null.
     * @param episodeCount The number of episodes (from the extension's episode list).
     * @return Map of episode number → EpisodeMetadata. May be empty if no source provides data.
     */
    suspend fun fetchEpisodeMetadata(
        anilistId: Int,
        malId: Int?,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> {
        Logger.i(TAG) { "Fetching episode metadata: anilistId=$anilistId, malId=$malId, episodeCount=$episodeCount" }

        val result = mutableMapOf<Int, EpisodeMetadata>()

        // ── Source 1: Anikage.cc (PRIMARY — most complete) ──
        try {
            val anikageResults = fetchFromAnikage(anilistId)
            Logger.i(TAG) { "Anikage.cc: ${anikageResults.size} episodes" }
            if (anikageResults.isNotEmpty()) {
                for ((epNum, meta) in anikageResults) {
                    if (epNum in 1..episodeCount) {
                        result[epNum] = meta
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Anikage.cc fetch failed: ${e.message}" }
        }

        // ── Source 2: Jikan (MAL) — fills in missing title + airDate ──
        if (malId != null && malId > 0) {
            try {
                delay(500) // courtesy delay for rate limiting
                val jikanResults = fetchFromJikan(malId, anilistId)
                Logger.i(TAG) { "Jikan: ${jikanResults.size} episodes" }
                for ((epNum, meta) in jikanResults) {
                    if (epNum in 1..episodeCount) {
                        val existing = result[epNum]
                        result[epNum] = EpisodeMetadata(
                            episodeKey = "al:$anilistId|ep:$epNum",
                            number = epNum.toDouble(),
                            title = existing?.title ?: meta.title,
                            thumbnailUrl = existing?.thumbnailUrl ?: meta.thumbnailUrl,
                            airDate = existing?.airDate ?: meta.airDate,
                            description = existing?.description ?: meta.description,
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Jikan fetch failed: ${e.message}" }
            }
        }

        // ── Source 3: AniList streamingEpisodes — fills in missing title + thumbnail ──
        try {
            val streamingEpisodes = aniListApi.fetchStreamingEpisodes(anilistId)
            Logger.i(TAG) { "AniList streamingEpisodes: ${streamingEpisodes.size} episodes" }
            if (streamingEpisodes.isNotEmpty()) {
                for ((index, ep) in streamingEpisodes.withIndex()) {
                    val epNum = index + 1
                    if (epNum > episodeCount) break
                    val existing = result[epNum]
                    result[epNum] = EpisodeMetadata(
                        episodeKey = "al:$anilistId|ep:$epNum",
                        number = epNum.toDouble(),
                        title = existing?.title ?: ep.title,
                        thumbnailUrl = existing?.thumbnailUrl ?: ep.thumbnail,
                        airDate = existing?.airDate,
                        description = existing?.description,
                    )
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "AniList streamingEpisodes fetch failed: ${e.message}" }
        }

        Logger.i(TAG) { "Episode metadata fetch complete: ${result.size} episodes" }
        return result
    }

    // ── Anikage.cc source ──

    private suspend fun fetchFromAnikage(anilistId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$ANIKAGE_BASE/$anilistId/episodes")
                        .headers(
                            Headers.Builder()
                                .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                                .set("Accept", "application/json")
                                .build()
                        )
                        .build()
                ).execute()

                if (!response.isSuccessful) {
                    Logger.w(TAG) { "Anikage HTTP ${response.code} for anilistId=$anilistId" }
                    return@withContext results
                }

                val body = response.body?.string() ?: return@withContext results
                val anikageResponse = json.decodeFromString<AnikageResponse>(body)

                anikageResponse.episodes.forEach { ep ->
                    val num = ep.number ?: return@forEach
                    results[num] = EpisodeMetadata(
                        episodeKey = "al:$anilistId|ep:$num",
                        number = num.toDouble(),
                        title = ep.title?.takeIf { it.isNotBlank() },
                        description = ep.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                        thumbnailUrl = ep.image?.takeIf { it.isNotBlank() },
                        airDate = ep.airDate?.takeIf { it.isNotBlank() }?.let { parseDateToMillis(it) },
                    )
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Anikage fetch exception: ${e.message}" }
            }
            results
        }

    // ── Jikan (MAL) source ──

    private suspend fun fetchFromJikan(malId: Int, anilistId: Int): Map<Int, EpisodeMetadata> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<Int, EpisodeMetadata>()
            try {
                var page = 1
                var hasNext = true
                while (hasNext && page <= 10) {
                    val response = httpClient.newCall(
                        Request.Builder()
                            .url("$JIKAN_BASE/anime/$malId/episodes?page=$page")
                            .header("Accept", "application/json")
                            .build()
                    ).execute()

                    if (!response.isSuccessful) {
                        Logger.w(TAG) { "Jikan HTTP ${response.code} for malId=$malId page=$page" }
                        if (response.code == 429) break
                        break
                    }

                    val body = response.body?.string() ?: break
                    val jikanResponse = json.decodeFromString<JikanEpisodesResponse>(body)

                    jikanResponse.data.forEach { ep ->
                        val epNum = ep.malId ?: return@forEach
                        results[epNum] = EpisodeMetadata(
                            episodeKey = "al:$anilistId|ep:$epNum",
                            number = epNum.toDouble(),
                            title = ep.title?.takeIf { it.isNotBlank() },
                            airDate = ep.aired?.let { parseDateToMillis(it) },
                        )
                    }

                    hasNext = jikanResponse.pagination?.hasNextPage == true
                    if (hasNext) {
                        page++
                        delay(400)
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Jikan fetch exception: ${e.message}" }
            }
            results
        }

    private fun stripHtml(text: String): String {
        return text.replace(Regex("<[^>]+>"), "").trim()
    }

    private fun parseDateToMillis(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ── Response models ──

    @Serializable
    private data class AnikageResponse(
        val episodes: List<AnikageEpisode> = emptyList(),
    )

    @Serializable
    private data class AnikageEpisode(
        val number: Int? = null,
        val title: String? = null,
        val description: String? = null,
        val image: String? = null,
        val airDate: String? = null,
    )

    @Serializable
    private data class JikanEpisodesResponse(
        val data: List<JikanEpisode> = emptyList(),
        val pagination: JikanPagination? = null,
    )

    @Serializable
    private data class JikanEpisode(
        val malId: Int? = null,
        val title: String? = null,
        val aired: String? = null,
    )

    @Serializable
    private data class JikanPagination(
        val hasNextPage: Boolean? = null,
    )
}

/**
 * Interface for a pluggable episode metadata source.
 * Future sources implement this interface.
 */
interface EpisodeMetadataSource {
    val name: String
    suspend fun fetch(anilistId: Int, episodeCount: Int): Map<Int, EpisodeMetadata>
}
