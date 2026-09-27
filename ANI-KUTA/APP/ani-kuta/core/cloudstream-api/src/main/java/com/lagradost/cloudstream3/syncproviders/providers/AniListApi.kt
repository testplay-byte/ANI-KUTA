// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — StreamPlay / TorraStream reference the NESTED data classes
// of this class (CoverImage / LikePageInfo / MediaCoverImage / MediaTitle /
// Recommendation / RecommendationConnection / RecommendationEdge /
// RecommendedMedia / SeasonNextAiringEpisode / Title) to deserialize the
// AniList GraphQL responses their recommendation views read, and grab the
// instance itself from [AccountManager.aniListApi]. The nested shapes below
// mirror the upstream property lists (the getters the census reads are
// getExtraLarge/getLarge/getMedium/getHasNextPage/getEnglish/getRomaji/
// getMediaRecommendation/getEdges/getNode/getCoverImage/getId/getTitle/
// getEpisode); the CLASS itself is inert — every API call answers the
// "not logged in" shape so the plugins' sync views render their empty state.
package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The inert AniList sync API (see the file header). */
open class AniListApi : SyncAPI() {

    @Serializable
    data class Title(
        @JsonProperty("english") @SerialName("english") val english: String?,
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String?,
    )

    @Serializable
    data class CoverImage(
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String?,
    )

    @Serializable
    data class LikePageInfo(
        @JsonProperty("total") @SerialName("total") val total: Int? = null,
        @JsonProperty("currentPage") @SerialName("currentPage") val currentPage: Int? = null,
        @JsonProperty("lastPage") @SerialName("lastPage") val lastPage: Int? = null,
        @JsonProperty("hasNextPage") @SerialName("hasNextPage") val hasNextPage: Boolean? = null,
        @JsonProperty("perPage") @SerialName("perPage") val perPage: Int? = null,
    )

    @Serializable
    data class MediaCoverImage(
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String?,
        @JsonProperty("large") @SerialName("large") val large: String?,
        @JsonProperty("medium") @SerialName("medium") val medium: String?,
        @JsonProperty("color") @SerialName("color") val color: String?,
    )

    @Serializable
    data class MediaTitle(
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String?,
        @JsonProperty("english") @SerialName("english") val english: String?,
        @JsonProperty("native") @SerialName("native") val native: String?,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String?,
    )

    @Serializable
    data class RecommendedMedia(
        @JsonProperty("id") @SerialName("id") val id: Int?,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle?,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage?,
    )

    @Serializable
    data class Recommendation(
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation")
        val mediaRecommendation: RecommendedMedia?,
    )

    @Serializable
    data class RecommendationEdge(
        @JsonProperty("node") @SerialName("node") val node: Recommendation,
    )

    @Serializable
    data class RecommendationConnection(
        @JsonProperty("edges") @SerialName("edges") val edges: List<RecommendationEdge> = emptyList(),
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Recommendation> = emptyList(),
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") @SerialName("episode") val episode: Int?,
        @JsonProperty("timeUntilAiring") @SerialName("timeUntilAiring") val timeUntilAiring: Int?,
    )
}
