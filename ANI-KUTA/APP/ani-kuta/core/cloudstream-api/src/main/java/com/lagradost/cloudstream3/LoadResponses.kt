// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
@file:Suppress("DEPRECATION_ERROR")

package com.lagradost.cloudstream3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

// ─────────────────────────────────────────────────────────────────────────────
// SubtitleFile / AudioFile
// ─────────────────────────────────────────────────────────────────────────────

@ConsistentCopyVisibility
data class SubtitleFile internal constructor(
    // internal (not private) so the facade-bound builders in MainAPI.kt can construct
    // this — upstream they share one file; we keep the binary-compat facade exact.
    var lang: String,
    var url: String,
    var headers: Map<String, String>?,
) {
    @Deprecated("Use newSubtitleFile method", level = DeprecationLevel.WARNING)
    constructor(lang: String, url: String) : this(lang = lang, url = url, headers = null)

    /** Language code to properly filter auto select / download subtitles. */
    val langTag: String?
        get() = SubtitleHelper.fromCodeToLangTagIETF(lang) ?: SubtitleHelper.fromLanguageToTagIETF(lang, true)

    /** Backwards compatible copy. */
    fun copy(
        lang: String = this.lang,
        url: String = this.url,
    ): SubtitleFile = SubtitleFile(lang, url, headers)
}

@ConsistentCopyVisibility
@Serializable
data class AudioFile internal constructor(
    @JsonProperty("url") @SerialName("url") var url: String,
    @JsonProperty("headers") @SerialName("headers") var headers: Map<String, String>? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Season / airing / episode structure
// ─────────────────────────────────────────────────────────────────────────────

data class NextAiring(
    val episode: Int,
    val unixTime: Long,
    val season: Int? = null,
)

@Serializable
data class SeasonData(
    @JsonProperty("season") @SerialName("season") val season: Int,
    @JsonProperty("name") @SerialName("name") val name: String? = null,
    @JsonProperty("displaySeason") @SerialName("displaySeason") val displaySeason: Int? = null, // will use season if null
)

/** Abstract interface of EpisodeResponse. */
interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
    fun getLatestEpisodes(): Map<DubStatus, Int?>

    /** Count all episodes in all previous seasons up until this episode to get a total count. */
    fun getTotalEpisodeIndex(episode: Int, season: Int): Int
}

/** Episode information that will be passed to the LoadLinks function & shown on UI. */
data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var score: Score? = null,
    var description: String? = null,
    var date: Long? = null,
    var runTime: Int? = null,
) {
    @Deprecated(
        "`rating` is the old scoring system, use score instead",
        replaceWith = ReplaceWith("score"),
        level = DeprecationLevel.ERROR,
    )
    var rating: Int?
        set(value) {
            this.score = Score.from(value, 100)
        }
        get() = score?.toInt(100)
}

// ─────────────────────────────────────────────────────────────────────────────
// LoadResponse
// ─────────────────────────────────────────────────────────────────────────────

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?

    var score: Score?
    var tags: List<String>?
    var duration: Int? // in minutes
    var trailers: MutableList<TrailerData>

    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?

    var logoUrl: String?
    var contentRating: String?

    var uniqueUrl: String

    @Deprecated(
        "`rating` is the old scoring system, use score instead",
        replaceWith = ReplaceWith("score"),
        level = DeprecationLevel.ERROR,
    )
    var rating: Int?
        @Suppress("DEPRECATION_ERROR")
        set(value) {
            this.score = Score.fromOld(value)
        }
        @Suppress("DEPRECATION_ERROR")
        get() = score?.toOld()

    companion object {
        var malIdPrefix = "" // malApi.idPrefix
        var kitsuIdPrefix = "" // kitsuApi.idPrefix
        var aniListIdPrefix = "" // aniListApi.idPrefix
        var simklIdPrefix = "" // simklApi.idPrefix
        var isTrailersEnabled = true

        /** Packs a Simkl-compatible id map into a JSON string. */
        fun addIdToString(idString: String?, database: SimklSyncServices, id: String?): String? {
            if (id == null) return idString
            val map = readIdFromString(idString).toMutableMap()
            map[database] = id
            return json.encodeToString(map.mapKeys { it.key.originalName })
        }

        /** Read the id string to get all other ids. */
        fun readIdFromString(idString: String?): Map<SimklSyncServices, String> {
            if (idString == null) return emptyMap()
            val map = runCatching {
                json.decodeFromString<Map<String, String>>(idString)
            }.getOrNull() ?: return emptyMap()
            return map.mapNotNull { (k, v) ->
                SimklSyncServices.entries.firstOrNull { it.originalName == k }?.let { it to v }
            }.toMap()
        }

        fun LoadResponse.isMovie(): Boolean = type.isMovieType()

        @JvmName("addActorNames")
        fun LoadResponse.addActors(actors: List<String>?) {
            if (actors == null) return
            this.actors = actors.map { ActorData(Actor(it)) }
        }

        @JvmName("addActors")
        fun LoadResponse.addActors(actors: List<Pair<Actor, String?>>?) {
            if (actors == null) return
            this.actors = actors.map { (actor, roleString) -> ActorData(actor, null, roleString, null) }
        }

        @JvmName("addActorsRole")
        fun LoadResponse.addActors(actors: List<Pair<Actor, ActorRole?>>?) {
            if (actors == null) return
            this.actors = actors.map { (actor, role) -> ActorData(actor, role, null, null) }
        }

        @JvmName("addActorsOnly")
        fun LoadResponse.addActors(actors: List<Actor>?) {
            if (actors == null) return
            this.actors = actors.map { ActorData(it) }
        }

        fun LoadResponse.getMalId(): String? = syncData[malIdPrefix]
        fun LoadResponse.getKitsuId(): String? = syncData[kitsuIdPrefix]
        fun LoadResponse.getAniListId(): String? = syncData[aniListIdPrefix]
        fun LoadResponse.getImdbId(): String? = syncData["imdb"]
        fun LoadResponse.getTMDbId(): String? = syncData["tmdb"]

        fun LoadResponse.addMalId(id: Int?) {
            if (id != null) syncData[malIdPrefix] = id.toString()
        }

        fun LoadResponse.addKitsuId(id: Int?) {
            if (id != null) syncData[kitsuIdPrefix] = id.toString()
        }

        fun LoadResponse.addAniListId(id: Int?) {
            if (id != null) syncData[aniListIdPrefix] = id.toString()
        }

        /** Internal helper to add simkl ids from other databases. */
        private fun LoadResponse.addSimklId(
            database: SimklSyncServices,
            id: String?,
        ) {
            if (id == null) return
            syncData[simklIdPrefix] = addIdToString(syncData[simklIdPrefix], database, id) ?: return
        }

        fun LoadResponse.addSimklId(id: Int?) {
            addSimklId(SimklSyncServices.Simkl, id?.toString())
        }

        fun LoadResponse.addImdbUrl(url: String?) {
            addImdbId(imdbUrlToIdNullable(url))
        }

        fun LoadResponse.addImdbId(id: String?) {
            if (id != null) {
                syncData["imdb"] = id
                addSimklId(SimklSyncServices.Imdb, id)
            }
        }

        fun LoadResponse.addTMDbId(id: String?) {
            if (id != null) {
                syncData["tmdb"] = id
                addSimklId(SimklSyncServices.Tmdb, id)
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun LoadResponse.addTraktId(id: String?) {
            // Trakt sync is not wired in this host.
        }

        @Suppress("UNUSED_PARAMETER")
        fun LoadResponse.addKitsuId(id: String?) {
            // Duplicate-name overload (string) kept for source compat; kitsu uses Int above.
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrl: String?,
            referer: String? = null,
            addRaw: Boolean = false,
        ) {
            addTrailer(trailerUrl, referer, addRaw, mapOf())
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrl: String?,
            referer: String? = null,
            addRaw: Boolean = false,
            headers: Map<String, String> = mapOf(),
        ) {
            if (!isTrailersEnabled || trailerUrl.isNullOrBlank()) return
            trailers.add(TrailerData(trailerUrl, referer, addRaw, headers))
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun LoadResponse.addTrailer(
            trailerUrls: List<String>?,
            referer: String? = null,
            addRaw: Boolean = false,
        ) {
            if (!isTrailersEnabled) return
            trailerUrls?.forEach { addTrailer(it, referer, addRaw) }
        }

        fun LoadResponse.addScore(score: String?, maxValue: Int = 10) {
            this.score = Score.from(score, maxValue)
        }

        fun LoadResponse.addScore(score: Score?) {
            if (score != null) this.score = score
        }

        @Deprecated(
            "Use addScore",
            replaceWith = ReplaceWith("addScore"),
            level = DeprecationLevel.ERROR,
        )
        fun LoadResponse.addRating(text: String?) {
            addScore(text)
        }

        @Deprecated(
            "Use addScore",
            replaceWith = ReplaceWith("addScore"),
            level = DeprecationLevel.ERROR,
        )
        fun LoadResponse.addRating(value: Int?) {
            this.score = Score.from(value, 100)
        }

        fun LoadResponse.addDuration(input: String?) {
            this.duration = getDurationFromString(input) ?: this.duration
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete LoadResponses
// ─────────────────────────────────────────────────────────────────────────────

data class TorrentLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var magnet: String?,
    var torrent: String?,
    override var plot: String?,
    override var type: TvType = TvType.Torrent,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse

data class AnimeLoadResponse(
    var engName: String? = null,
    var japName: String? = null,
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    override var showStatus: ShowStatus? = null,
    override var plot: String? = null,
    override var tags: List<String>? = null,
    var synonyms: List<String>? = null,
    override var score: Score? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse, EpisodeResponse {
    override fun getLatestEpisodes(): Map<DubStatus, Int?> =
        episodes.mapValues { (_, eps) -> eps.maxOfOrNull { it.episode ?: 0 } }

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displaySeasons = seasonNames ?: return episode
        var count = 0
        for (seasonData in displaySeasons) {
            val displaySeason = seasonData.displaySeason ?: seasonData.season
            if (displaySeason < season) {
                count += episodes.values.maxOfOrNull { list ->
                    list.count { (it.season ?: 0) == displaySeason }
                } ?: 0
            }
        }
        return count + episode
    }
}

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var type: TvType = TvType.Live,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<Episode>,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var showStatus: ShowStatus? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var uniqueUrl: String = url,
) : LoadResponse, EpisodeResponse {
    override fun getLatestEpisodes(): Map<DubStatus, Int?> =
        mapOf(DubStatus.None to episodes.maxOfOrNull { it.episode ?: 0 })

    override fun getTotalEpisodeIndex(episode: Int, season: Int): Int {
        val displaySeasons = seasonNames ?: return episode
        var count = 0
        for (seasonData in displaySeasons) {
            val displaySeason = seasonData.displaySeason ?: seasonData.season
            if (displaySeason < season) {
                count += episodes.count { (it.season ?: 0) == displaySeason }
            }
        }
        return count + episode
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tracker + AniList search models (getTracker support surface)
// ─────────────────────────────────────────────────────────────────────────────

interface IDownloadableMinimum {
    val url: String
    val referer: String
    val headers: Map<String, String>
}

data class Tracker(
    val malId: Int? = null,
    val kitsuId: String? = null,
    val aniId: String? = null,
    val image: String? = null,
    val cover: String? = null,
)

@Serializable
data class AniSearch(
    @JsonProperty("data") @SerialName("data") var data: Data? = Data(),
) {
    @Serializable
    data class Data(
        @JsonProperty("Page") @SerialName("Page") var page: Page? = Page(),
    ) {
        @Serializable
        data class Page(
            @JsonProperty("media") @SerialName("media") var media: ArrayList<Media> = arrayListOf(),
        ) {
            @Serializable
            data class Media(
                @JsonProperty("title") @SerialName("title") var title: Title? = null,
                @JsonProperty("id") @SerialName("id") var id: Int? = null,
                @JsonProperty("idMal") @SerialName("idMal") var idMal: Int? = null,
                @JsonProperty("seasonYear") @SerialName("seasonYear") var seasonYear: Int? = null,
                @JsonProperty("format") @SerialName("format") var format: String? = null,
                @JsonProperty("coverImage") @SerialName("coverImage") var coverImage: CoverImage? = null,
                @JsonProperty("bannerImage") @SerialName("bannerImage") var bannerImage: String? = null,
            ) {
                @Serializable
                data class CoverImage(
                    @JsonProperty("extraLarge") @SerialName("extraLarge") var extraLarge: String? = null,
                    @JsonProperty("large") @SerialName("large") var large: String? = null,
                )

                @Serializable
                data class Title(
                    @JsonProperty("romaji") @SerialName("romaji") var romaji: String? = null,
                    @JsonProperty("english") @SerialName("english") var english: String? = null,
                ) {
                    fun isMatchingTitles(title: String?): Boolean {
                        if (title == null) return false
                        return romaji.equals(title, ignoreCase = true) ||
                            english.equals(title, ignoreCase = true)
                    }
                }
            }
        }
    }
}
