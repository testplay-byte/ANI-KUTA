@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable

interface SAnime : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var background_url: String?

    var update_strategy: AnimeUpdateStrategy

    var fetch_type: FetchType

    var season_number: Double

    var initialized: Boolean

    // ── Task 46 (device round 5): optional enrichment fields ──
    // Aniyomi's classic SAnime contract has no year/rating channel, so the
    // CloudStream source bridge had to DROP LoadResponse.year/score and the
    // details page rendered "no year, no rating" for CS entries. These two
    // nullable fields are the honest channel: null (the default) for every
    // classic aniyomi source, populated by sources that actually have the data.
    // Default accessor bodies keep the interface BINARY-COMPATIBLE — external
    // implementers inherit no-op defaults instead of failing to link.

    /** Release year, when the source provides one (null = unknown). */
    var year: Int?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /** Rating on a 0..10 scale, when the source provides one (null = unrated). */
    var score: Double?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copy() = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre
        it.status = status
        it.thumbnail_url = thumbnail_url
        it.background_url = background_url
        it.update_strategy = update_strategy
        it.fetch_type = fetch_type
        it.season_number = season_number
        it.initialized = initialized
        it.year = year
        it.score = score
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SAnime {
            return SAnimeImpl()
        }
    }
}
