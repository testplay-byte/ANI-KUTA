package com.confused.anikuta.data.cloudstream.content

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.isMovieType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * One browsable CloudStream source (= one MainAPI provider registered by a
 * TRUSTED plugin) as the search page's source picker sees it (session 3,
 * provider-execution phase 1).
 *
 * [providerName] is the identity — the ecosystem-qualified key used across the
 * search flow is `"cloudstream:<providerName>"` (doc 16 §5.2 string-key
 * discipline; the aniyomi flow's Long ids stay untouched).
 */
data class CsProviderSource(
    val providerName: String,
    val lang: String,
    val supportedTypes: List<String>,
    /** getMainPage implemented → the source can be browsed without a query. */
    val hasMainPage: Boolean,
    val usesWebView: Boolean,
    /** Parent plugin display metadata (icon/name for the picker row). */
    val pluginName: String,
    val pluginInternalName: String,
    val pluginIconUrl: String?,
    /** The parent plugin's catalog NSFW flag (gates picker visibility). */
    val isNsfw: Boolean,
)

/** A search/browse result card rendered in the shared results grid. */
data class CsContentCard(
    val providerName: String,
    val name: String,
    val url: String,
    val posterUrl: String?,
    /** TvType name (Movie / TvSeries / Anime / …) — chip on the details screen. */
    val type: String?,
    val year: Int?,
)

/** One page of results (page 1 only in phase 1 — the aniyomi flow is page-1 too). */
data class CsContentPage(
    val items: List<CsContentCard>,
    val hasNext: Boolean = false,
)

/**
 * One episode of a series-type content (phase 1: display only — [data] is the
 * opaque handle the future loadLinks session will pass back).
 */
data class CsEpisode(
    val season: Int?,
    val episode: Int?,
    val name: String?,
    val description: String?,
    val data: String,
    /** Dub/Sub track label for anime-type providers, null otherwise. */
    val dubLabel: String? = null,
)

/** The mapped result of MainAPI.load() — everything the CS details screen renders. */
data class CsContentDetails(
    val title: String,
    val url: String,
    val providerName: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val year: Int?,
    /** TvType name — drives the "Supported mode" chip + episode vs movie layout. */
    val type: String?,
    val tags: List<String>,
    /** Rating on a 0..10 scale (Score.toInt(10)). */
    val score10: Int?,
    /** ShowStatus name — "Completed" / "Ongoing" (series types only). */
    val status: String?,
    val durationMinutes: Int?,
    val contentRating: String?,
    val episodes: List<CsEpisode>,
    /** Movie/Live content — the single data handle (no episode list). */
    val movieDataUrl: String?,
    val isMovie: Boolean,
)

/**
 * The CloudStream provider-EXECUTION layer (doc 23 §7 scope, session 3):
 * resolves loaded [MainAPI] providers by name and runs their suspend surface
 * (getMainPage / search / load), mapping every response into the plain UI
 * models above. The aniyomi search flow stays byte-identical — this repository
 * is consumed ONLY by the new CloudStream branches.
 *
 * Logging (CORE_RULES §20): everything logs under the
 * `Anikuta:Data:Cloudstream:Exec` tag with a leading operation prefix
 * (`browse:` / `search:` / `load:` / `resolve:`) so a single logcat filter on
 * the tag shows the whole execution pipeline, and a second filter on the
 * prefix isolates one operation. Durations are logged on every completed call.
 *
 * Error contract: failures THROW (the calling ViewModels render them through
 * the established ExtensionError/Details-error patterns — D-295/D-296: never
 * silent). A provider that returns null parses as an empty page.
 */
class CloudstreamContentRepository(
    private val manager: CloudstreamPluginManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Every provider of every TRUSTED + loaded plugin, as a StateFlow — the
     * search page's picker list. Derives from the manager's installed list so
     * trust/untrust/install/uninstall mutations flow through automatically.
     */
    val sources: StateFlow<List<CsProviderSource>> = manager.installed
        .map { installedList: List<CloudstreamExtension.Installed> ->
            installedList.flatMap { ext ->
                ext.providers.map { info ->
                    CsProviderSource(
                        providerName = info.name,
                        lang = info.lang,
                        supportedTypes = info.supportedTypes,
                        hasMainPage = info.hasMainPage,
                        usesWebView = info.usesWebView,
                        pluginName = ext.name,
                        pluginInternalName = ext.internalName,
                        pluginIconUrl = ext.iconUrl,
                        isNsfw = ext.isNsfw,
                    )
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Execution ───────────────────────────────────────────────────────────

    /**
     * MainAPI.getMainPage(page, mainPage.first()) — the blank-query browse.
     * All shelves of the response are merged into one grid (distinct by url —
     * the D-304 LazyGrid duplicate-key crash guard) and capped.
     *
     * Callers check [CsProviderSource.hasMainPage] first; a provider that
     * didn't implement getMainPage throws NotImplementedError, which surfaces
     * as a normal error (never silent).
     */
    suspend fun mainPage(providerName: String, page: Int = 1): CsContentPage = withContext(Dispatchers.IO) {
        val provider = resolveProvider(providerName)
        val started = System.currentTimeMillis()
        val data = provider.mainPage.firstOrNull()
        val request = MainPageRequest(
            name = data?.name ?: "",
            data = data?.data ?: "",
            horizontalImages = data?.horizontalImages ?: false,
        )
        Logger.i(TAG) { "browse: $providerName page=$page shelf='${request.name}'" }
        try {
            val response = provider.getMainPage(page, request)
                ?: return@withContext CsContentPage(emptyList())
            // Merge every shelf, dedupe by url (D-304), cap for a sane grid.
            val cards = response.items
                .flatMap { shelf -> shelf.list }
                .distinctBy { it.url }
                .take(MAX_BROWSE_ITEMS)
                .map { it.toCard(providerName) }
            Logger.i(TAG) {
                "browse: $providerName page=$page -> ${cards.size} item(s) " +
                    "hasNext=${response.hasNext} in ${System.currentTimeMillis() - started}ms"
            }
            CsContentPage(cards, response.hasNext)
        } catch (t: Throwable) {
            Logger.e(TAG) {
                "browse: $providerName page=$page FAILED in ${System.currentTimeMillis() - started}ms: " +
                    "${t::class.java.simpleName}: ${t.message}"
            }
            throw t
        }
    }

    /** MainAPI.search(query, page) — the live-query path. */
    suspend fun search(providerName: String, query: String, page: Int = 1): CsContentPage = withContext(Dispatchers.IO) {
        val provider = resolveProvider(providerName)
        val started = System.currentTimeMillis()
        Logger.i(TAG) { "search: $providerName query='$query' page=$page" }
        try {
            val result = provider.search(query, page)
                ?: return@withContext CsContentPage(emptyList())
            val cards = result.items
                .distinctBy { it.url } // D-304 duplicate-key crash guard
                .map { it.toCard(providerName) }
            Logger.i(TAG) {
                "search: $providerName query='$query' -> ${cards.size} item(s) " +
                    "hasNext=${result.hasNext} in ${System.currentTimeMillis() - started}ms"
            }
            CsContentPage(cards, result.hasNext)
        } catch (t: Throwable) {
            Logger.e(TAG) {
                "search: $providerName query='$query' FAILED in ${System.currentTimeMillis() - started}ms: " +
                    "${t::class.java.simpleName}: ${t.message}"
            }
            throw t
        }
    }

    /** MainAPI.load(url) — everything the CS content details screen renders. */
    suspend fun load(providerName: String, url: String): CsContentDetails = withContext(Dispatchers.IO) {
        val provider = resolveProvider(providerName)
        val started = System.currentTimeMillis()
        Logger.i(TAG) { "load: $providerName url=${url.take(120)}" }
        try {
            val response = provider.load(url)
                ?: throw IllegalStateException("Provider '$providerName' returned no content for this URL")
            val details = response.toDetails(providerName)
            Logger.i(TAG) {
                "load: $providerName '${details.title}' type=${details.type} " +
                    "episodes=${details.episodes.size} movie=${details.isMovie} " +
                    "in ${System.currentTimeMillis() - started}ms"
            }
            details
        } catch (t: Throwable) {
            Logger.e(TAG) {
                "load: $providerName url=${url.take(120)} FAILED in ${System.currentTimeMillis() - started}ms: " +
                    "${t::class.java.simpleName}: ${t.message}"
            }
            throw t
        }
    }

    fun destroy() {
        scope.cancel()
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Name → live MainAPI. Only TRUSTED plugins ever load, so everything in
     * APIHolder is executable; a missing name means untrusted/uninstalled/
     * errored — a precise, actionable error beats a mysterious NPE later.
     */
    private fun resolveProvider(name: String): MainAPI {
        val provider = APIHolder.getApiFromNameNull(name)
        if (provider == null) {
            Logger.w(TAG) { "resolve: '$name' is not loaded (untrusted, uninstalled, or failed to load)" }
            throw IllegalStateException(
                "CloudStream provider '$name' is not loaded — check its plugin in Settings → Extensions",
            )
        }
        return provider
    }

    private fun SearchResponse.toCard(providerName: String): CsContentCard {
        val year = when (this) {
            is MovieSearchResponse -> year
            is TvSeriesSearchResponse -> year
            is AnimeSearchResponse -> year
            is LiveSearchResponse -> null
            else -> null
        }
        return CsContentCard(
            providerName = providerName,
            name = name,
            url = url,
            posterUrl = posterUrl,
            type = type?.name,
            year = year,
        )
    }

    private fun com.lagradost.cloudstream3.LoadResponse.toDetails(providerName: String): CsContentDetails {
        val episodes = mutableListOf<CsEpisode>()
        var movieDataUrl: String? = null
        var status: String? = null

        when (this) {
            is MovieLoadResponse -> movieDataUrl = dataUrl
            is LiveStreamLoadResponse -> movieDataUrl = dataUrl
            is TvSeriesLoadResponse -> {
                status = showStatus?.name
                episodes += episodesOf(this.episodes)
            }
            is AnimeLoadResponse -> {
                status = showStatus?.name
                // Anime episodes are keyed by DubStatus — flatten into one list,
                // labeling each track so the details screen can group them
                // (Sub before Dub; None first — DubStatus.id order).
                episodes += this.episodes.entries
                    .sortedBy { it.key.id }
                    .flatMap { entry ->
                        val label = when (entry.key) {
                            DubStatus.Dubbed -> "Dub"
                            DubStatus.Subbed -> "Sub"
                            else -> null
                        }
                        episodesOf(entry.value, label)
                    }
            }
            else -> Unit // TorrentLoadResponse etc. — no episode list in phase 1
        }

        val isMovie = movieDataUrl != null || (type != null && type.isMovieType())
        return CsContentDetails(
            title = name,
            url = url,
            providerName = providerName,
            posterUrl = posterUrl,
            bannerUrl = backgroundPosterUrl,
            description = plot,
            year = year,
            type = type?.name,
            tags = tags.orEmpty(),
            score10 = score?.toInt(10),
            status = status,
            durationMinutes = duration,
            contentRating = contentRating,
            episodes = episodes,
            movieDataUrl = movieDataUrl,
            isMovie = isMovie,
        )
    }

    private fun episodesOf(list: List<com.lagradost.cloudstream3.Episode>, dubLabel: String? = null): List<CsEpisode> =
        list.map { ep ->
            CsEpisode(
                season = ep.season,
                episode = ep.episode,
                name = ep.name,
                description = ep.description,
                data = ep.data,
                dubLabel = dubLabel,
            )
        }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Exec"

        /** Browse grids merge every main-page shelf — capped for a sane first render. */
        private const val MAX_BROWSE_ITEMS = 60
    }
}
