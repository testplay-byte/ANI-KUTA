package com.confused.anikuta.core.anilist.api

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.model.DetailsResponse
import com.confused.anikuta.core.anilist.model.SearchResponse
import com.confused.anikuta.core.anilist.model.TrendingResponse
import com.confused.anikuta.core.common.DispatcherProvider
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AniList GraphQL API client.
 * Ponytail: raw POST + kotlinx.serialization. No Apollo client.
 */
class AniListApi(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {
    private val endpoint = "https://graphql.anilist.co"
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun fetchTrending(page: Int = 1, perPage: Int = 20): List<AniListAnime> =
        withContext(dispatchers.io) {
            val query = """
                {
                    Page(page: $page, perPage: $perPage) {
                        media(type: ANIME, sort: TRENDING_DESC) {
                            id
                            title { romaji english }
                            coverImage { large extraLarge }
                            averageScore
                            episodes
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchTrending(page=$page)" }

            val response = executeQuery(query)
            json.decodeFromString<TrendingResponse>(response).data.Page.media
        }

    suspend fun fetchAnimeDetails(id: Int): AniListAnime =
        withContext(dispatchers.io) {
            val query = """
                {
                    Media(id: $id, type: ANIME) {
                        id
                        title { romaji english }
                        coverImage { large extraLarge }
                        bannerImage
                        description
                        averageScore
                        episodes
                        genres
                        season
                        seasonYear
                        status
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchAnimeDetails(id=$id)" }

            val response = executeQuery(query)
            json.decodeFromString<DetailsResponse>(response).data.Media
        }

    /**
     * Search anime on AniList by query string.
     *
     * @param query Search term (matched against romaji/english/native titles).
     * @param page 1-indexed page number for pagination.
     * @param perPage Results per page (default 20).
     * @param sort AniList sort enum string (e.g. "POPULARITY_DESC", "SCORE_DESC",
     *   "START_DATE_DESC", "TITLE_ROMAJI", "TRENDING_DESC", "FAVOURITES_DESC").
     */
    suspend fun searchAnime(
        query: String,
        page: Int = 1,
        perPage: Int = 20,
        sort: String = "POPULARITY_DESC",
    ): List<AniListAnime> = withContext(dispatchers.io) {
        // GraphQL variables — passed via the variables field so the user query
        // is properly escaped (no string interpolation injection risk).
        val gqlQuery = """
            query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort]) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, search: ${'$'}search, sort: ${'$'}sort) {
                        id
                        title { romaji english }
                        coverImage { large extraLarge }
                        averageScore
                        episodes
                        seasonYear
                    }
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("search", query)
            put("page", page)
            put("perPage", perPage)
            put("sort", sort)
        }

        Logger.d("Anikuta:Core:AniList") { "searchAnime(q='$query', page=$page, sort=$sort)" }

        val response = executeQueryWithVariables(gqlQuery, variables)
        json.decodeFromString<SearchResponse>(response).data.Page.media
    }

    private fun executeQuery(query: String): String {
        val requestBody = buildJsonObject {
            put("query", query)
        }.toString().toRequestBody(jsonMediaType)

        return executeRequest(requestBody)
    }

    /**
     * Fetch streaming episode metadata (title + thumbnail) from AniList.
     *
     * AniList's `streamingEpisodes` field provides per-episode titles + thumbnails
     * from streaming services. This is the simplest source — no authentication
     * needed, just the AniList anime ID.
     *
     * Returns a list of (title, thumbnailUrl) pairs, ordered by episode number.
     * May be empty if AniList has no streaming episode data for this anime.
     */
    suspend fun fetchStreamingEpisodes(id: Int): List<AniListStreamingEpisode> =
        withContext(dispatchers.io) {
            val query = """
                {
                    Media(id: $id, type: ANIME) {
                        streamingEpisodes {
                            title
                            thumbnail
                        }
                    }
                }
            """.trimIndent()

            Logger.d("Anikuta:Core:AniList") { "fetchStreamingEpisodes(id=$id)" }

            try {
                val response = executeQuery(query)
                val parsed = json.decodeFromString<StreamingEpisodesResponse>(response)
                parsed.data.Media.streamingEpisodes ?: emptyList()
            } catch (e: Exception) {
                Logger.w("Anikuta:Core:AniList") { "fetchStreamingEpisodes failed: ${e.message}" }
                emptyList()
            }
        }

    private fun executeQueryWithVariables(query: String, variables: kotlinx.serialization.json.JsonObject): String {
        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(jsonMediaType)

        return executeRequest(requestBody)
    }

    private fun executeRequest(requestBody: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e("Anikuta:Core:AniList") { "AniList API error: ${response.code}" }
                throw RuntimeException("AniList API error: ${response.code}")
            }
            return response.body?.string()
                ?: throw RuntimeException("AniList API: empty response body")
        }
    }
}
