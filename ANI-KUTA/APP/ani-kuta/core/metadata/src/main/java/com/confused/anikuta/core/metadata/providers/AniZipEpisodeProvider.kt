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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * AniZip episode metadata provider. D-190.
 *
 * API: `https://api.ani.zip/mappings?anilist_id=<id>`
 *
 * AniZip is the PRIMARY source (richest data): per-episode `title.en` / `title.ja`
 * / `title.x-jat`, `overview`, `summary`, `image`, `airDate`, `runtime`,
 * `seasonNumber`, `episodeNumber`, `anidbEid`, `rating`, `tvdbId`. Also returns
 * top-level `mappings` with `mal_id` / `kitsu_id` / `themoviedb_id` (useful for
 * cross-ID activation — currently unused but available for future TMDB content).
 *
 * ## What this provider contributes (merge priority: 1st — highest)
 * - `title` (from `title.en`)
 * - `titleJapanese` (from `title.ja` — per-episode, NOT show-level which is bugged)
 * - `titleRomaji` (from `title.x-jat` — per-episode)
 * - `description` (from `overview` ?? `summary`)
 * - `thumbnailUrl` (from `image` — TVDB banners, high quality)
 * - `airDate` (from `airDate` — parsed yyyy-MM-dd → epoch millis)
 * - `runtime` (minutes)
 * - `seasonNumber`
 * - `episodeNumberInSeason` (from `episodeNumber`)
 *
 * ## What this provider does NOT contribute
 * - `isFiller` / `isRecap` — AniZip has no filler info. Jikan is the only source.
 * - `score` — AniZip's `rating` is AniDB score (different scale); Jikan's MAL
 *   score is preferred.
 *
 * ## Episode filtering
 * AniZip returns 246 episodes for Naruto (220 main + ~26 specials). This provider
 * filters to `1..episodeCount` to align with the extension's episode list.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Metadata:Episodes:AniZip".
 */
class AniZipEpisodeProvider(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : EpisodeMetadataProvider {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Episodes:AniZip"
        private const val BASE_URL = "https://api.ani.zip/mappings"
    }

    override val id: String = "anizip"
    override val displayName: String = "AniZip"
    override val supportedIdTypes: Set<ContentIdType> = setOf(ContentIdType.ANILIST, ContentIdType.MAL)

    override suspend fun fetchEpisodes(
        contentId: ContentId,
        episodeCount: Int,
    ): Map<Int, EpisodeMetadata> = withContext(Dispatchers.IO) {
        // AniZip accepts anilist_id or mal_id. We support ANILIST (primary).
        // If a future caller passes MAL, we can also handle it — AniZip accepts ?mal_id=X.
        val url = when (contentId.type) {
            ContentIdType.ANILIST -> "$BASE_URL?anilist_id=${contentId.value}"
            ContentIdType.MAL -> "$BASE_URL?mal_id=${contentId.value}"
            else -> {
                Logger.w(TAG) { "Unsupported ID type: ${contentId.type}" }
                return@withContext emptyMap()
            }
        }

        try {
            val response = httpClient.newCall(
                Request.Builder()
                    .url(url)
                    .headers(
                        Headers.Builder()
                            .set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .set("Accept", "application/json")
                            .build()
                    )
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Logger.w(TAG) { "HTTP ${response.code} for ${contentId.type}=${contentId.value}" }
                return@withContext emptyMap()
            }

            val body = response.body?.string() ?: return@withContext emptyMap()
            val anizipResponse = json.decodeFromString<AniZipResponse>(body)
            val episodes = anizipResponse.episodes ?: return@withContext emptyMap()

            val results = mutableMapOf<Int, EpisodeMetadata>()
            episodes.forEach { (key, ep) ->
                val num = key.toIntOrNull() ?: return@forEach
                // Filter: only episodes 1..episodeCount (skip specials like "S1", "S2")
                if (num !in 1..episodeCount) return@forEach
                results[num] = EpisodeMetadata(
                    episodeKey = "anizip:${contentId.value}|ep:$num",
                    number = num.toDouble(),
                    title = ep.title?.en?.takeIf { it.isNotBlank() },
                    titleJapanese = ep.title?.ja?.takeIf { it.isNotBlank() },
                    titleRomaji = ep.title?.xJat?.takeIf { it.isNotBlank() },
                    description = (ep.overview ?: ep.summary)?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                    thumbnailUrl = ep.image?.takeIf { it.isNotBlank() },
                    airDate = (ep.airDate ?: ep.airdate)?.takeIf { it.isNotBlank() }?.let { parseDateToMillis(it) },
                    runtime = ep.runtime ?: ep.length,
                    seasonNumber = ep.seasonNumber,
                    episodeNumberInSeason = ep.episodeNumber,
                )
            }
            Logger.i(TAG) { "Fetched ${results.size}/$episodeCount episodes for ${contentId.type}=${contentId.value}" }
            results
        } catch (e: Exception) {
            Logger.w(TAG) { "Fetch failed for ${contentId.type}=${contentId.value}: ${e.message}" }
            emptyMap()
        }
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), "").trim()

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
    private data class AniZipResponse(
        val episodes: Map<String, AniZipEpisode>? = null,
        val episodeCount: Int? = null,
        val specialCount: Int? = null,
        val mappings: AniZipMappings? = null,
    )

    @Serializable
    private data class AniZipEpisode(
        val episode: String? = null,
        val title: AniZipTitle? = null,
        val overview: String? = null,
        val summary: String? = null,
        val image: String? = null,
        val airDate: String? = null,
        @SerialName("airdate") val airdate: String? = null,
        val runtime: Int? = null,
        val length: Int? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val absoluteEpisodeNumber: Int? = null,
        val rating: String? = null,
        val anidbEid: Int? = null,
        val tvdbId: Int? = null,
        val tvdbShowId: Int? = null,
    )

    @Serializable
    private data class AniZipTitle(
        val ja: String? = null,
        val en: String? = null,
        @SerialName("x-jat") val xJat: String? = null,
    )

    @Serializable
    private data class AniZipMappings(
        @SerialName("anilist_id") val anilistId: Int? = null,
        @SerialName("mal_id") val malId: Int? = null,
        @SerialName("kitsu_id") val kitsuId: Int? = null,
        @SerialName("thetvdb_id") val tvdbId: Int? = null,
        @SerialName("themoviedb_id") val tmdbId: String? = null, // STRING not Long
        @SerialName("imdb_id") val imdbId: String? = null,
    )
}
