package com.confused.anikuta.core.metadata.providers

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.metadata.ContentId
import com.confused.anikuta.core.metadata.ContentIdType
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.metadata.EpisodeMetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Kitsu episode metadata provider. D-190.
 *
 * API: `POST https://kitsu.io/api/graphql` with `lookupMapping(externalId: <anilistId>, externalSite: ANILIST_ANIME)`.
 *
 * Kitsu is a tertiary source (AniZip > Jikan > Kitsu). It provides canonical
 * titles, descriptions, and thumbnails via GraphQL. The GraphQL `lookupMapping`
 * takes an AniList ID directly (no need for Kitsu ID resolution via REST search).
 *
 * ## What this provider contributes (merge priority: 3rd)
 * - `title` (from `titles.canonical` — fallback if AniZip + Jikan lack it)
 * - `description` (from `description.en` — fallback)
 * - `thumbnailUrl` (from `thumbnail.original.url` — fallback)
 *
 * ## What this provider does NOT contribute
 * - `isFiller` / `isRecap` — Kitsu has no filler info. Jikan only.
 * - `titleJapanese` / `titleRomaji` — Kitsu only has `canonical` (usually English). AniZip/Jikan only.
 * - `score` — Kitsu has ratings but on a different scale + not per-episode. Jikan only.
 *
 * ## GraphQL query
 * The query uses `lookupMapping(externalId: ${anilistId}, externalSite: ANILIST_ANIME)`
 * which maps the AniList ID to a Kitsu anime + returns its episodes (up to 2000).
 * No REST search-by-title fallback needed (we always have the AniList ID).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes:Kitsu".
 */
class KitsuEpisodeProvider(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataProvider {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes:Kitsu"
        private const val GRAPHQL_URL = "https://kitsu.io/api/graphql"
    }

    override val id: String = "kitsu"
    override val displayName: String = "Kitsu"
    override val supportedIdTypes: Set<ContentIdType> = setOf(ContentIdType.ANILIST)

    override suspend fun fetchEpisodes(
        contentId: ContentId,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> = withContext(Dispatchers.IO) {
        // Kitsu GraphQL lookupMapping takes AniList ID directly.
        if (contentId.type != ContentIdType.ANILIST) {
            Logger.w(TAG) { "Unsupported ID type: ${contentId.type} (Kitsu needs AniList ID)" }
            return@withContext emptyMap()
        }

        val anilistId = contentId.value
        if (anilistId <= 0) return@withContext emptyMap()

        try {
            val query = """
                query {
                  lookupMapping(externalId: $anilistId, externalSite: ANILIST_ANIME) {
                    __typename
                    ... on Anime {
                      id
                      episodes(first: 2000) {
                        nodes {
                          number
                          titles {
                            canonical
                          }
                          description(locales: ["en", "en-us"])
                          thumbnail {
                            original {
                              url
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val requestJson = buildJsonObject {
                put("query", query)
            }
            val requestBody = json.encodeToString(JsonObject.serializer(), requestJson)
                .toRequestBody("application/json".toMediaType())

            val response = httpClient.newCall(
                Request.Builder()
                    .url(GRAPHQL_URL)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Logger.w(TAG) { "HTTP ${response.code} for anilistId=$anilistId" }
                return@withContext emptyMap()
            }

            val body = response.body?.string() ?: return@withContext emptyMap()
            val gqlResponse = json.decodeFromString<KitsuGraphQLResponse>(body)
            val nodes = gqlResponse.data?.lookupMapping?.episodes?.nodes ?: return@withContext emptyMap()

            val results = mutableMapOf<Int, EpisodeMetadata>()
            nodes.forEach { node ->
                val num = node?.number ?: return@forEach
                if (num !in 1..episodeCount) return@forEach
                results[num] = EpisodeMetadata(
                    episodeKey = "kitsu:al:$anilistId|ep:$num",
                    number = num.toDouble(),
                    title = node.titles?.canonical?.takeIf { it.isNotBlank() },
                    description = node.description?.en ?: node.description?.enUs,
                    thumbnailUrl = node.thumbnail?.original?.url?.takeIf { it.isNotBlank() },
                )
            }
            Logger.i(TAG) { "Fetched ${results.size}/$episodeCount episodes for anilistId=$anilistId" }
            results
        } catch (e: Exception) {
            Logger.w(TAG) { "Fetch failed for anilistId=$anilistId: ${e.message}" }
            emptyMap()
        }
    }

    // ── Response models ──

    @Serializable
    private data class KitsuGraphQLResponse(
        val data: GraphQLData? = null,
    )

    @Serializable
    private data class GraphQLData(
        val lookupMapping: LookupMapping? = null,
    )

    @Serializable
    private data class LookupMapping(
        val id: String? = null,
        val episodes: GraphQLEpisodes? = null,
    )

    @Serializable
    private data class GraphQLEpisodes(
        val nodes: List<GraphQLNode?>? = null,
    )

    @Serializable
    private data class GraphQLNode(
        val number: Int? = null,
        val titles: GraphQLTitles? = null,
        val description: GraphQLDescription? = null,
        val thumbnail: GraphQLThumbnail? = null,
    )

    @Serializable
    private data class GraphQLTitles(
        val canonical: String? = null,
    )

    @Serializable
    private data class GraphQLDescription(
        val en: String? = null,
        @SerialName("en-us") val enUs: String? = null,
    )

    @Serializable
    private data class GraphQLThumbnail(
        val original: GraphQLOriginal? = null,
    )

    @Serializable
    private data class GraphQLOriginal(
        val url: String? = null,
    )
}
