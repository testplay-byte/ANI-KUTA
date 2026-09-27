// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — part of the syncproviders compat surface (see AuthAPI.kt's
// header). The dex census needs: SyncRepo.<init>(SyncAPI), SyncRepo.authUser(),
// SyncRepo.library() (the Result-mangled suspend), SyncAPI$LibraryList.getName/
// getItems and SyncAPI$LibraryMetadata.getAllLibraryLists — StreamPlay and
// TorraStream wrap [AccountManager.aniListApi] in a SyncRepo and read the
// library view. Everything else mirrors the upstream member set so future
// plugins find the same interop facts (Page/ListSorting deliberately omitted —
// nothing references them and they would drag the whole ui.library surface in).
package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.utils.UiText
import java.util.Date

/**
 * The sync service contract (upstream: an abstract class extending AuthAPI —
 * collapsed here to one open class; no census plugin references AuthAPI and
 * the JVM surface is identical for every member below).
 *
 * OUR IMPLEMENTATIONS ARE INERT: the syncproviders.providers.AniListApi
 * subclass answers every query with the upstream "not logged in" shape
 * (null / empty), so a plugin's sync-powered views render their logged-out
 * state instead of crashing the load.
 */
open class SyncAPI {
    /** Set when the user mutates something a library refresh would show. */
    open var requireLibraryRefresh: Boolean = true

    open val mainUrl: String = "NONE"

    open val supportedWatchTypes: Set<SyncWatchType> = SyncWatchType.entries.toSet()

    /** Which library links this service can open. */
    open val syncIdName: SyncIdName? = null

    /** Modify the watch status of one item. */
    open suspend fun updateStatus(auth: AuthData?, id: String, newStatus: AbstractSyncStatus): Boolean = false

    /** The current watch status of one item. */
    open suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? = null

    /** Metadata about one item. */
    open suspend fun load(auth: AuthData?, id: String): SyncResult? = null

    /** Search this service. */
    open suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? = null

    /** The account's library / bookmarks — the member the census plugins call through [SyncRepo]. */
    open suspend fun library(auth: AuthData?): LibraryMetadata? = null

    /** Maps a site URL to this service's id (helper). */
    open fun urlToId(url: String): String? = null

    /** One search hit (implements the shared response shape). */
    data class SyncSearchResult(
        override val name: String,
        override val apiName: String,
        var syncId: String,
        override val url: String,
        override var posterUrl: String?,
        override var type: TvType? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var id: Int? = null,
        override var score: Score? = null,
    ) : SearchResponse

    /** The status payload the update APIs exchange. */
    abstract class AbstractSyncStatus {
        abstract var status: SyncWatchType
        abstract var score: Score?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }

    data class SyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        override var watchedEpisodes: Int?,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
    ) : AbstractSyncStatus()

    /** The metadata one library item carries. */
    data class SyncResult(
        var id: String,
        var totalEpisodes: Int? = null,
        var title: String? = null,
        var publicScore: Score? = null,
        /** In minutes. */
        var duration: Int? = null,
        var synopsis: String? = null,
        var airStatus: ShowStatus? = null,
        var nextAiring: NextAiring? = null,
        var studio: List<String>? = null,
        var genres: List<String>? = null,
        var synonyms: List<String>? = null,
        var trailers: List<String>? = null,
        var isAdult: Boolean? = null,
        var posterUrl: String? = null,
        var backgroundPosterUrl: String? = null,
        /** Unixtime. */
        var startDate: Long? = null,
        /** Unixtime. */
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<ActorData>? = null,
    )

    /** The library view a sync service exposes (name + items). */
    data class LibraryList(
        val name: UiText,
        val items: List<LibraryItem>,
    )

    /** Every list the service groups the library into. */
    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList>,
    )

    /** One entry inside a [LibraryList]. */
    data class LibraryItem(
        override val name: String,
        override val url: String,
        /** Unchanging id status/score changes target. */
        val syncId: String,
        val episodesCompleted: Int?,
        val episodesTotal: Int?,
        val personalRating: Score?,
        val lastUpdatedUnixTime: Long?,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var posterHeaders: Map<String, String>?,
        override var quality: SearchQuality?,
        val releaseDate: Date?,
        override var id: Int? = null,
        val plot: String? = null,
        override var score: Score? = null,
        val tags: List<String>? = null,
    ) : SearchResponse
}
