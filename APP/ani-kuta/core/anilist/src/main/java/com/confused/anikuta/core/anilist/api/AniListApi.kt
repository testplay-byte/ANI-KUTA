package com.confused.anikuta.core.anilist.api

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.model.DetailsResponse
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

    private fun executeQuery(query: String): String {
        val requestBody = buildJsonObject {
            put("query", query)
        }.toString().toRequestBody(jsonMediaType)

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
