package com.confused.anikuta.core.metadata.providers

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.metadata.ContentId
import com.confused.anikuta.core.metadata.ContentIdType
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.metadata.EpisodeMetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Jikan (MAL) episode metadata provider. D-190.
 *
 * API: `https://api.jikan.moe/v4/anime/{malId}/episodes?page=X`
 *
 * Jikan is the ONLY source with **filler** + **recap** booleans — the key
 * differentiator. Also provides `title_japanese`, `title_romanji`, `score`
 * (MAL community score 0-10), `aired` (ISO datetime).
 *
 * ## What this provider contributes (merge priority: 2nd)
 * - `isFiller` / `isRecap` (UNIQUE — no other source has this)
 * - `titleJapanese` (from `title_japanese` — fallback if AniZip lacks it)
 * - `titleRomaji` (from `title_romanji` — fallback, NBSP trimmed)
 * - `score` (MAL community score — canonical)
 * - `title` (from `title` — fallback if AniZip lacks it)
 * - `airDate` (from `aired` — ISO datetime → epoch millis)
 *
 * ## Rate limiting
 * Jikan has a 3 req/sec sustained limit (429 if exceeded). This provider:
 * - Delays 400ms between pages (≈2.5 req/sec — under the limit for single-anime).
 * - On 429: exponential backoff (1s, 2s, 4s) up to 3 retries per page.
 * - Max 10 pages (1000 episodes — covers even long-running like One Piece).
 *
 * NOTE: For concurrent multi-anime flows (user opens 3 details screens rapidly),
 * the per-call delay is insufficient — a future Phase 2 hardening should add a
 * process-wide `SpecificHostRateLimitInterceptor` on a Jikan-specific OkHttp
 * client. Tracked as a concern (not blocking).
 *
 * ## Episode filtering
 * Jikan returns episodes by MAL episode ID (1-based). This provider maps
 * `mal_id` → episode number + filters to `1..episodeCount`.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes:Jikan".
 */
class JikanEpisodeProvider(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataProvider {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes:Jikan"
        private const val BASE_URL = "https://api.jikan.moe/v4"
        private const val PAGE_DELAY_MS = 400L
        private const val MAX_PAGES = 10
        private const val MAX_RETRIES = 3
    }

    override val id: String = "jikan"
    override val displayName: String = "Jikan (MAL)"
    override val supportedIdTypes: Set<ContentIdType> = setOf(ContentIdType.MAL)

    override suspend fun fetchEpisodes(
        contentId: ContentId,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> = withContext(Dispatchers.IO) {
        // Jikan requires a MAL ID. If the content is AniList-keyed but has malId, use it.
        val malId = when (contentId.type) {
            ContentIdType.MAL -> contentId.value
            ContentIdType.ANILIST -> contentId.malId
            else -> {
                Logger.w(TAG) { "Unsupported ID type: ${contentId.type} (Jikan needs MAL ID)" }
                return@withContext emptyMap()
            }
        }

        if (malId == null || malId <= 0) {
            Logger.d(TAG) { "No MAL ID available — skipping Jikan" }
            return@withContext emptyMap()
        }

        val results = mutableMapOf<Int, EpisodeMetadata>()
        var page = 1
        var hasNext = true

        while (hasNext && page <= MAX_PAGES) {
            val pageData = fetchPageWithRetry(malId, page) ?: break
            val (episodes, pagination) = pageData

            episodes.forEach { ep ->
                val epNum = ep.malId ?: return@forEach
                if (epNum !in 1..episodeCount) return@forEach
                results[epNum] = EpisodeMetadata(
                    episodeKey = "jikan:mal:$malId|ep:$epNum",
                    number = epNum.toDouble(),
                    title = ep.title?.takeIf { it.isNotBlank() }?.trim(),
                    titleJapanese = ep.titleJapanese?.takeIf { it.isNotBlank() }?.trim(),
                    // Jikan title_romanji often has trailing NBSP (\u00A0) — trim() removes it.
                    titleRomaji = ep.titleRomanji?.takeIf { it.isNotBlank() }?.trim(),
                    airDate = ep.aired?.takeIf { it.isNotBlank() }?.let { parseIsoDateToMillis(it) },
                    score = ep.score,
                    isFiller = ep.filler,
                    isRecap = ep.recap,
                )
            }

            hasNext = pagination?.hasNextPage == true
            if (hasNext) {
                page++
                delay(PAGE_DELAY_MS)
            }
        }

        Logger.i(TAG) { "Fetched ${results.size}/$episodeCount episodes for malId=$malId (${page - 1} pages)" }
        results
    }

    /**
     * Fetch one page with exponential backoff on 429.
     * Returns null on unrecoverable failure (HTTP error after retries, parse error).
     */
    private suspend fun fetchPageWithRetry(
        malId: Long,
        page: Int,
    ): Pair<List<JikanEpisode>, JikanPagination?>? = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("$BASE_URL/anime/$malId/episodes?page=$page")
                        .header("Accept", "application/json")
                        .build()
                ).execute()

                if (response.code == 429) {
                    response.close()
                    val backoffMs = 1000L * (1L shl attempt) // 1s, 2s, 4s
                    Logger.w(TAG) { "429 on page $page (attempt ${attempt + 1}/$MAX_RETRIES) — backing off ${backoffMs}ms" }
                    delay(backoffMs)
                    attempt++
                    continue
                }

                if (!response.isSuccessful) {
                    Logger.w(TAG) { "HTTP ${response.code} for malId=$malId page=$page — stopping" }
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<JikanEpisodesResponse>(body)
                return@withContext parsed.data to parsed.pagination
            } catch (e: Exception) {
                Logger.w(TAG) { "Page $page fetch exception (attempt ${attempt + 1}): ${e.message}" }
                attempt++
                if (attempt < MAX_RETRIES) delay(1000L * (1L shl attempt))
            }
        }
        null
    }

    private fun parseIsoDateToMillis(dateStr: String): Long {
        // Jikan returns ISO 8601: "2002-10-03T00:00:00+00:00"
        return try {
            val datePart = dateStr.substringBefore("T")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(datePart)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ── Response models ──

    @Serializable
    private data class JikanEpisodesResponse(
        val data: List<JikanEpisode> = emptyList(),
        val pagination: JikanPagination? = null,
    )

    @Serializable
    private data class JikanEpisode(
        @SerialName("mal_id") val malId: Int? = null,
        val title: String? = null,
        @SerialName("title_japanese") val titleJapanese: String? = null,
        @SerialName("title_romanji") val titleRomanji: String? = null,
        val aired: String? = null,
        val score: Double? = null,
        val filler: Boolean? = null,
        val recap: Boolean? = null,
    )

    @Serializable
    private data class JikanPagination(
        @SerialName("last_visible_page") val lastVisiblePage: Int? = null,
        @SerialName("has_next_page") val hasNextPage: Boolean? = null,
    )
}
