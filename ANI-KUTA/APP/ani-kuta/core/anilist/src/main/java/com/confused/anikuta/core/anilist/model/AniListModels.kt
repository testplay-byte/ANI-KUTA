package com.confused.anikuta.core.anilist.model

import kotlinx.serialization.Serializable

@Serializable
data class AniListAnime(
    val id: Int,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val description: String? = null,
    val bannerImage: String? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val status: String? = null,
    val idMal: Int? = null,
    // D-235: Next airing episode — fetched on-demand when the user opens an anime.
    // AniList returns airingAt as Unix SECONDS. Convert to millis (* 1000) when storing.
    val nextAiringEpisode: AniListAiringEpisode? = null,
) {
    val displayName: String get() = title.english ?: title.romaji ?: "Unknown"
    val coverUrl: String? get() = coverImage.extraLarge ?: coverImage.large
}

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
data class CoverImage(
    val large: String? = null,
    val extraLarge: String? = null,
)

/**
 * D-235: Next airing episode data from AniList.
 * @param airingAt Unix SECONDS — convert to millis (* 1000) when storing.
 * @param episode The episode number that will air next.
 */
@Serializable
data class AniListAiringEpisode(
    val airingAt: Long,
    val episode: Int,
)

// Response wrappers (match AniList JSON structure)
@Serializable
data class TrendingResponse(val data: TrendingData)

@Serializable
data class TrendingData(val Page: MediaPage)

@Serializable
data class MediaPage(val media: List<AniListAnime>)

@Serializable
data class DetailsResponse(val data: DetailsData)

@Serializable
data class DetailsData(val Media: AniListAnime)

// Search response (reuses TrendingData/Page shape — AniList returns the same Media list)
@Serializable
data class SearchResponse(val data: TrendingData)

// ── Streaming episodes response ──
@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class StreamingEpisodesResponse(val data: StreamingEpisodesData)

@Serializable
data class StreamingEpisodesData(val Media: StreamingEpisodesMedia)

@Serializable
data class StreamingEpisodesMedia(
    val streamingEpisodes: List<AniListStreamingEpisode>? = null,
)
