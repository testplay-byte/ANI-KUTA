// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3

/** Abstract interface of SearchResponse. */
interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
    var score: Score?
}

data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,
    override var posterUrl: String?,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Deprecated("Use newTorrentSearchResponse", level = DeprecationLevel.ERROR)
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
    ) : this(name, url, apiName, type, posterUrl, null, null, null, null)
}

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Deprecated("Use newMovieSearchResponse", level = DeprecationLevel.ERROR)
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
    ) : this(name, url, apiName, type, posterUrl, null, null, null, null, null)
}

data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var lang: String? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Deprecated("Use newLiveSearchResponse", level = DeprecationLevel.ERROR)
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
    ) : this(name, url, apiName, type, posterUrl, null, null, null, null, null)
}

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var episodes: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Deprecated("Use newTvSeriesSearchResponse", level = DeprecationLevel.ERROR)
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
    ) : this(name, url, apiName, type, posterUrl, null, null, null, null, null, null)
}

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: MutableSet<DubStatus>? = null,
    var otherName: String? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
) : SearchResponse {
    @Deprecated("Use newAnimeSearchResponse", level = DeprecationLevel.ERROR)
    constructor(
        name: String,
        url: String,
        apiName: String,
        type: TvType?,
        posterUrl: String?,
    ) : this(name, url, apiName, type, posterUrl, null, null, null, mutableMapOf(), null, null, null, null)
}

data class SearchResponseList(
    val items: List<SearchResponse>,
    val hasNext: Boolean = false,
)

data class TrailerData(
    val extractorUrl: String,
    val referer: String?,
    val raw: Boolean,
    val headers: Map<String, String> = mapOf(),
)

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean,
)

/** Data class for the homepage response info. */
data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)

/** Data class for the homepage list info. */
data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

/** enum class of Actor roles (Main, Supporting, Background). */
enum class ActorRole {
    Main,
    Supporting,
    Background,
}

/** Data class holding Actor personal information. */
data class Actor(
    val name: String,
    val image: String? = null,
)

/** Data class holding Actor information. */
data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)
