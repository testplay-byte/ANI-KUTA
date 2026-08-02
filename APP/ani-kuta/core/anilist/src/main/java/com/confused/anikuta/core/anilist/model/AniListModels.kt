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
