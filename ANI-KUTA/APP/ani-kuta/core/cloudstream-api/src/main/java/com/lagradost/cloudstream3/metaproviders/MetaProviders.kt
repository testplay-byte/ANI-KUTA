// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// SKELETONS (doc 23 §4): metaprovider open surfaces (4/80 census plugins subclass
// TmdbProvider, 2/80 TraktProvider). Subclasses LOAD and register cleanly; the TMDb/
// Trakt network calls throw a clear error when invoked (content phase implements).
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val NOT_IMPLEMENTED =
    "Metaprovider runtime is not implemented in this build yet (content session)"

@Serializable
data class TmdbLink(
    @JsonProperty("imdbID") @SerialName("imdbID") val imdbID: String?,
    @JsonProperty("tmdbID") @SerialName("tmdbID") val tmdbID: Int?,
    @JsonProperty("episode") @SerialName("episode") val episode: Int?,
    @JsonProperty("season") @SerialName("season") val season: Int?,
    @JsonProperty("movieName") @SerialName("movieName") val movieName: String? = null,
)

open class TmdbProvider : MainAPI() {
    // This should always be false, but might as well make it easier for forks.
    open val includeAdult = false

    // Use the LoadResponse from the metadata provider.
    open val useMetaLoadResponse = false
    open val apiName = "TMDB"

    // As some sites don't support s0.
    open val disableSeasonZero = true

    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider

    @Serializable
    data class TmdbSeasonSummary(
        @JsonProperty("season_number") @SerialName("season_number") val seasonNumber: Int? = null,
        @JsonProperty("episode_count") @SerialName("episode_count") val episodeCount: Int? = null,
    )

    open suspend fun fetchContentRating(id: Int?, country: String): String? = null

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    open fun loadFromImdb(imdb: String, seasons: List<TmdbSeasonSummary>): LoadResponse? = null

    open fun loadFromTmdb(tmdbId: Int, seasons: List<TmdbSeasonSummary>): LoadResponse? = null

    open fun loadFromImdb(imdb: String): LoadResponse? = null

    open fun loadFromTmdb(tmdbId: Int): LoadResponse? = null

    override suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class TraktProvider : MainAPI() {
    override var name = "Trakt"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    private val traktApiUrl = "https://api.trakt.tv"

    override val mainPage = mainPageOf(
        "$traktApiUrl/movies/trending" to "Trending Movies",
        "$traktApiUrl/movies/popular" to "Popular Movies",
        "$traktApiUrl/shows/trending" to "Trending Shows",
        "$traktApiUrl/shows/popular" to "Popular Shows",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    override suspend fun load(url: String): LoadResponse {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}
