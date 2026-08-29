// Task 45 (device round 4): the CloudStream → Aniyomi SOURCE BRIDGE.
//
// The user's directive: CloudStream results must open the EXACT SAME details
// screen as aniyomi extensions — "no custom new details page". The standard
// details screen is source-interface driven (DetailsViewModel resolves the
// source by Long id from ExtensionManager and calls getAnimeDetails /
// getEpisodeList), so instead of a parallel UI we bridge every trusted
// CloudStream provider as an [AnimeHttpSource] and register it into the
// ExtensionManager source map under a STABLE synthetic id. Details, episode
// lists, the save button, back stack, tags, background image, AniList
// auto-linking — everything flows through the untouched aniyomi code paths.
//
// Identity contract: id = CS_SOURCE_ID_FLAG | (hash(providerName) & 0xffffffff).
// The flag bit is never set by aniyomi's MD5 ids (sign bit cleared, high bits
// effectively random — the flag makes collisions impossible in practice) and
// the id is deterministic across restarts, so library saves, watch history and
// AniList links keyed on (sourceId, animeUrl) survive app restarts.
package com.confused.anikuta.data.cloudstream.content

import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response

/** Stable synthetic source ids for bridged CloudStream providers. */
object CsSourceIds {

    /** Bit 62 — never set by aniyomi's MD5 ids, marks a bridged CS source. */
    internal const val CS_SOURCE_ID_FLAG: Long = 0x4000_0000_0000_0000L

    /** True when [id] was minted by [idFor] (a bridged CloudStream source). */
    fun isCloudstreamId(id: Long): Boolean = id and CS_SOURCE_ID_FLAG != 0L

    /** Deterministic id for a provider name — stable across restarts/installs. */
    fun idFor(providerName: String): Long =
        CS_SOURCE_ID_FLAG or (providerName.hashCode().toLong() and 0xFFFF_FFFFL)
}

/**
 * One CloudStream [MainAPI] provider exposed as an aniyomi [AnimeHttpSource].
 *
 * The provider is resolved BY NAME on every call (`APIHolder.getApiFromNameNull`)
 * — a bridge instance survives plugin updates/reloads because it never caches
 * the live provider object. All suspend entry points map CloudStream responses
 * into the SAnime/SEpisode models the details screen consumes.
 *
 * HTTP plumbing (client/headers/request builders) is never touched — the CS
 * plugin's own networking (nicehttp `app` + CloudflareKiller) does the actual
 * fetching; the AnimeHttpSource surface exists so every aniyomi code path that
 * casts to AnimeHttpSource (video resolver, WebView episode URL, …) resolves.
 */
class CloudstreamAnimeSourceBridge(
    val providerName: String,
) : AnimeHttpSource() {

    private fun liveProvider(): MainAPI = APIHolder.getApiFromNameNull(providerName)
        ?: throw IllegalStateException(
            "CloudStream provider '$providerName' is not loaded — check Settings → Extensions",
        )

    /**
     * Plugin bytecode can throw ANYTHING (binary-compat NoClassDefFoundError,
     * NoSuchMethodError, …). The aniyomi flow's catch sites catch Exception —
     * so Errors are converted here into a descriptive IllegalStateException
     * (cause chained). Cancellation + Cloudflare blocks pass through untouched.
     */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (cf: com.lagradost.cloudstream3.network.CloudflareBlockedException) {
        throw cf
    } catch (t: Throwable) {
        Logger.e(TAG, t) { "bridge: '$providerName' provider call failed" }
        throw IllegalStateException(
            "CloudStream provider '$providerName' error: ${t::class.java.simpleName}: ${t.message}",
            t,
        )
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    override val id: Long get() = CsSourceIds.idFor(providerName)
    override val name: String get() = providerName
    override val lang: String get() = liveProviderOrNull()?.lang ?: "en"
    override val baseUrl: String get() = liveProviderOrNull()?.mainUrl ?: "https://localhost"
    override val supportsLatest: Boolean = true

    private fun liveProviderOrNull(): MainAPI? = APIHolder.getApiFromNameNull(providerName)

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // ── Catalogue (browse/search) ────────────────────────────────────────────

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val provider = liveProvider()
        if (!provider.hasMainPage) return AnimesPage(emptyList(), false)
        Logger.i(TAG) { "bridge: getPopularAnime '$providerName' page=$page (first shelf)" }
        val firstShelf = provider.mainPage.firstOrNull()
            ?: return AnimesPage(emptyList(), false)
        val response = guard {
            provider.getMainPage(
                page,
                MainPageRequest(
                    name = firstShelf.name,
                    data = firstShelf.data,
                    horizontalImages = firstShelf.horizontalImages,
                ),
            )
        } ?: return AnimesPage(emptyList(), false)
        val items = response.items.flatMap { it.list }.distinctBy { it.url }
        return AnimesPage(items.map { it.toSAnime() }, response.hasNext)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val provider = liveProvider()
        Logger.i(TAG) { "bridge: getSearchAnime '$providerName' query='$query' page=$page" }
        val result = guard { provider.search(query, page) } ?: return AnimesPage(emptyList(), false)
        val items = result.items.distinctBy { it.url }
        return AnimesPage(items.map { it.toSAnime() }, result.hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    // ── Details ──────────────────────────────────────────────────────────────

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        Logger.i(TAG) { "bridge: getAnimeDetails '$providerName' url=${anime.url.take(120)}" }
        val response = guard { liveProvider().load(anime.url) }
            ?: return anime.apply { initialized = true }
        return response.applyOnto(anime)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        Logger.i(TAG) { "bridge: getEpisodeList '$providerName' url=${anime.url.take(120)}" }
        val response = guard { liveProvider().load(anime.url) }
            ?: throw IllegalStateException("Provider '$providerName' returned no content for this URL")
        return response.toEpisodes()
    }

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    // ── Playback (next session) ──────────────────────────────────────────────

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        // Honest error until the loadLinks/extractor session lands (doc 23 §7):
        // the resolver sheet surfaces this message with the WebView escape hatch.
        throw IllegalStateException(
            "CloudStream playback arrives in the next update — this episode's video links aren't wired yet.",
        )
    }

    // ── AnimeHttpSource request/parse plumbing — NEVER called (all suspend
    //    entry points above are overridden); stubbed to fail loudly. ─────────

    override fun popularAnimeRequest(page: Int): Request = unsupported()
    override fun popularAnimeParse(response: Response): AnimesPage = unsupported()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = unsupported()
    override fun searchAnimeParse(response: Response): AnimesPage = unsupported()
    override fun latestUpdatesRequest(page: Int): Request = unsupported()
    override fun latestUpdatesParse(response: Response): AnimesPage = unsupported()
    override fun animeDetailsParse(response: Response): SAnime = unsupported()
    override fun episodeListParse(response: Response): List<SEpisode> = unsupported()
    override fun episodeVideoParse(response: Response): SEpisode = unsupported()
    override fun seasonListParse(response: Response): List<SAnime> = unsupported()
    override fun hosterListParse(response: Response): List<eu.kanade.tachiyomi.animesource.model.Hoster> = unsupported()
    override fun videoListParse(response: Response, hoster: eu.kanade.tachiyomi.animesource.model.Hoster): List<Video> = unsupported()
    override fun videoListParse(response: Response): List<Video> = unsupported()
    override fun videoUrlParse(response: Response): String = unsupported()

    private fun unsupported(): Nothing = throw NotImplementedError(
        "CloudStream bridge does not use AnimeHttpSource HTTP plumbing (provider networking runs inside the plugin)",
    )

    // ── Mapping (CloudStream models → aniyomi models) ────────────────────────

    private fun SearchResponse.toSAnime(): SAnime = SAnime.create().apply {
        url = this@toSAnime.url
        title = this@toSAnime.name
        thumbnail_url = posterUrl
        initialized = false
    }

    private fun LoadResponse.applyOnto(anime: SAnime): SAnime = anime.apply {
        title = name.ifBlank { anime.title }
        url = this@applyOnto.url
        description = plot
        genre = tags?.joinToString(", ")
        status = when (showStatusOf()) {
            ShowStatus.Completed -> SAnime.COMPLETED
            ShowStatus.Ongoing -> SAnime.ONGOING
            null -> SAnime.UNKNOWN
        }
        if (!posterUrl.isNullOrBlank()) thumbnail_url = posterUrl
        background_url = backgroundPosterUrl
        initialized = true
    }

    private fun LoadResponse.showStatusOf(): ShowStatus? = when (this) {
        is TvSeriesLoadResponse -> showStatus
        is AnimeLoadResponse -> showStatus
        else -> null
    }

    private fun LoadResponse.toEpisodes(): List<SEpisode> {
        val raw: List<Pair<Episode, String?>> = when (this) {
            is TvSeriesLoadResponse -> episodes.map { it to null }
            is AnimeLoadResponse -> episodes.entries
                .sortedBy { it.key.id }
                .flatMap { entry ->
                    val label = when (entry.key) {
                        DubStatus.Dubbed -> "Dub"
                        DubStatus.Subbed -> "Sub"
                        else -> null
                    }
                    entry.value.map { it to label }
                }
            is MovieLoadResponse -> listOf(Episode(data = dataUrl, name = "Movie") to null)
            is LiveStreamLoadResponse -> listOf(Episode(data = dataUrl, name = "Live Stream") to null)
            else -> emptyList()
        }
        return raw.mapIndexed { index, (ep, dubLabel) ->
            SEpisode.create().apply {
                // The episode's stable identity for the whole app (downloads,
                // history, future loadLinks): the provider's opaque data handle.
                url = ep.data
                name = buildString {
                    append(ep.name ?: "Episode ${ep.episode ?: index + 1}")
                    dubLabel?.let { append(" ($it)") }
                }
                episode_number = ep.episode?.toFloat() ?: (index + 1).toFloat()
                scanlator = dubLabel
                summary = ep.description
                date_upload = ep.date ?: 0L
            }
        }.distinctBy { it.url }
    }

    companion object {
        internal const val TAG = "Anikuta:Data:Cloudstream:Bridge"
    }
}

/**
 * Observes the TRUSTED CloudStream plugins and publishes every provider as a
 * bridged [AnimeHttpSource] under its stable synthetic id. The app wires this
 * into [com.confused.anikuta.data.extension.manager.ExtensionManager] so the
 * standard details screen resolves CS sources exactly like aniyomi ones.
 * Untrusting a plugin removes its providers from `installed` → the bridges
 * disappear on the next emission (the details screen then reports the source
 * as unavailable — the same behavior as an uninstalled aniyomi extension).
 */
class CloudstreamSourceRegistry(
    providerSources: StateFlow<List<CsProviderSource>>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sources = MutableStateFlow<Map<Long, AnimeHttpSource>>(emptyMap())
    val sources: StateFlow<Map<Long, AnimeHttpSource>> = _sources.asStateFlow()

    init {
        scope.launch {
            providerSources.collect { csSources ->
                val bridged = csSources.associate { cs ->
                    CsSourceIds.idFor(cs.providerName) to CloudstreamAnimeSourceBridge(cs.providerName)
                }
                _sources.value = bridged
                Logger.i(TAG) {
                    "bridge: ${bridged.size} CloudStream source(s) bridged into the extension source map " +
                        "[${bridged.values.joinToString { it.name }}]"
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        internal const val TAG = "Anikuta:Data:Cloudstream:Bridge"
    }
}
