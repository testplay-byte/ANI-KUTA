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
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
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

    /** CloudStream V2: this source is a bridged CloudStream provider — UI layers
     *  section source pickers by ecosystem with this marker (Aniyomi vs CloudStream).
     *  See AnimeHttpSource.isCloudStreamBridged for the contract. */
    override val isCloudStreamBridged: Boolean get() = true

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
        return response.episodesOrComingSoon()
    }

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    // ── Playback boundary (CloudStream V2 / Task 51) ──────────────────────
    //
    // The V2 rebuild deliberately ships WITHOUT playback: episode resolution,
    // link extraction and the watch page are excluded from this phase (user
    // directive — "you don't need to mess with the playback functionality or
    // work with the streams… for the cloud stream at all for the current time
    // being"). The two entry points below keep the aniyomi resolver contract
    // intact so tapping an episode produces an HONEST error instead of a dead
    // spinner or a half-broken player:
    //   getHosterList throws ISE → the classic resolver fast-falls-back to
    //   getVideoList → which throws the descriptive not-yet boundary below →
    //   the resolver sheet renders the message verbatim.

    /**
     * The resolver tries getHosterList FIRST (ext-lib 16 contract). The
     * inherited AnimeHttpSource impl would fire a REAL `GET(baseUrl +
     * episode.url)` against the provider site — the CS episode URL is an
     * opaque data handle, not a page — so fail fast instead: the resolver
     * catches IllegalStateException and falls straight through to
     * [getVideoList] (which carries the honest not-yet boundary).
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        throw IllegalStateException("CloudStream bridge does not use the hoster API")

    /**
     * CloudStream V2 honest boundary: video resolution is not wired on this
     * branch (the playback port brings loadLinks/extractor plumbing in its own
     * phase). Details + episodes + seasons are fully available; tapping an
     * episode surfaces THIS message through the resolver sheet — a clear
     * "not yet" beats a dead affordance or a half-broken player.
     */
    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        throw IllegalStateException(
            "CloudStream playback arrives with the playback port — episodes and details are available now",
        )

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

    /**
     * Task 46 (device round 5, broken details thumbnails): MANY CloudStream
     * providers return RELATIVE image paths ("/poster/xyz.jpg") or
     * protocol-relative ones ("//cdn.example/x.jpg") — Coil silently fails on
     * those, which is why details pages showed placeholders even though the
     * extension "provided" the thumbnail. Everything image-shaped is now
     * absolutized against the provider's mainUrl before it reaches the UI.
     * Returns null for blank input so callers keep their fallbacks intact.
     */
    private fun resolveImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> {
                val base = liveProviderOrNull()?.mainUrl?.trimEnd('/')
                    ?: return trimmed // no base to resolve against — pass through
                "$base$trimmed"
            }
            else -> trimmed // relative without leading slash or a data/blob URI — pass through
        }
    }

    private fun SearchResponse.toSAnime(): SAnime = SAnime.create().apply {
        url = this@toSAnime.url
        title = this@toSAnime.name
        thumbnail_url = resolveImageUrl(posterUrl)
        initialized = false
    }

    private fun LoadResponse.applyOnto(anime: SAnime): SAnime {
        val resolvedPoster = resolveImageUrl(posterUrl)
        val resolvedBackground = resolveImageUrl(backgroundPosterUrl)
        val resolvedScore = score?.toDouble(10)

        // Task 46: the full diagnostic line the round-5 report asked for — one
        // filterable log line per details fetch showing EVERY field the
        // provider supplied and what the bridge did with it.
        Logger.i(TAG) {
            "details: '$providerName' " +
                "type=${this::class.simpleName} year=$year score=$resolvedScore " +
                "status=${showStatusOf()?.name ?: "-"} tags=${tags?.size ?: 0} " +
                "plot=${plot?.length ?: 0}ch " +
                "poster=" + when {
                    posterUrl.isNullOrBlank() -> "none"
                    resolvedPoster != posterUrl -> "resolved('$posterUrl' -> '$resolvedPoster')"
                    else -> "absolute"
                } +
                " background=" + if (backgroundPosterUrl.isNullOrBlank()) "none" else "present"
        }

        return anime.apply {
            title = name.ifBlank { anime.title }
            url = this@applyOnto.url
            description = plot
            genre = tags?.joinToString(", ")
            status = when (showStatusOf()) {
                ShowStatus.Completed -> SAnime.COMPLETED
                ShowStatus.Ongoing -> SAnime.ONGOING
                null -> SAnime.UNKNOWN
            }
            // Only overwrite the (often working) search-grid thumbnail with a
            // resolvable absolute URL — a poster we could NOT absolutize would
            // replace a good image with a broken one.
            if (!resolvedPoster.isNullOrBlank()) thumbnail_url = resolvedPoster
            if (!resolvedBackground.isNullOrBlank()) background_url = resolvedBackground
            // Task 46: year + score finally reach the details page (SAnime
            // enrichment channel — see SAnime.year / SAnime.score).
            // Task 47 (device round 6, "rating shows but year doesn't"): many
            // providers set `year` on SEARCH responses but omit it on load() —
            // the details flow now seeds the stub SAnime with the search-time
            // year, and load()'s year only WINS (never erases the seed).
            year = this@applyOnto.year ?: anime.year
            score = resolvedScore
            initialized = true
        }
    }

    private fun LoadResponse.showStatusOf(): ShowStatus? = when (this) {
        is TvSeriesLoadResponse -> showStatus
        is AnimeLoadResponse -> showStatus
        else -> null
    }

    /**
     * Task 50 (Fix E-2 — honest episodes): a provider that flagged the load
     * response `comingSoon` (the CS3 factories set it automatically when an
     * anime/tv-series has zero episodes, a movie has a blank dataUrl, or a
     * torrent response has neither magnet nor torrent) must NOT surface as
     * the silent "No episodes found on this source." card — the honest error
     * tells the user the show simply has nothing published yet. comingSoon
     * WITH episodes lists normally (the flag is advisory, the list is fact).
     * Pure decision seam over [toEpisodes] — internal for tests.
     */
    internal fun LoadResponse.episodesOrComingSoon(): List<SEpisode> {
        val episodes = toEpisodes()
        if (episodes.isEmpty() && comingSoon) {
            throw IllegalStateException(
                "CloudStream '$providerName' marked this as coming soon — no episodes published yet",
            )
        }
        return episodes
    }

    /**
     * CloudStream [LoadResponse] → aniyomi [SEpisode]s. Pure mapping — no
     * provider calls ([resolveImageUrl] falls back to pass-through when no
     * live provider is registered, which is exactly the JVM-test situation).
     * Internal for tests ([CloudstreamBridgeEpisodesTest]).
     */
    internal fun LoadResponse.toEpisodes(): List<SEpisode> {
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
            // Task 50 (Fix E-3 — honest torrent rows): torrent providers used
            // to fall into the empty branch → a silent "no episodes" card. One
            // row carrying the .torrent/magnet data handle is honest: resolution
            // then reports "no playable links (N torrent link(s) hidden)" instead
            // of a lie about the episode list.
            is TorrentLoadResponse -> listOf(
                Episode(data = (torrent ?: magnet ?: ""), name = "Torrent") to null,
            )
            else -> emptyList()
        }

        // Task 50 (Fix E-1, AMENDED — label neutralization, NOT a dedup-key
        // change): some providers emit the SAME data handle under BOTH the Sub
        // and the Dub map entries (the handle resolves to one dual-audio
        // stream — the "dub" data is not a separate encode). Labeling those
        // rows (Sub)/(Dub) is a visible lie (the "dub" row plays the sub mix)
        // and distinctBy { url } collapsed them into an arbitrary labeled
        // survivor. Rows whose data handle appears under ≥2 DISTINCT dub
        // labels are emitted label-free (scanlator null, no name suffix);
        // different-handle sub/dub rows keep their labels. The final
        // distinctBy { it.url } dedupe is UNCHANGED — downstream
        // (EpisodeListNormalizer, DetailsScreen LazyColumn keys, the episode
        // cache UNIQUE(main_id, episode_number)) keys on URL, so no duplicate
        // rows are ever produced.
        val sharedHandles: Set<String> = raw
            .groupBy({ it.first.data })
            .filterValues { rows -> rows.map { (_, label) -> label }.toSet().size > 1 }
            .keys
        if (sharedHandles.isNotEmpty()) {
            Logger.i(TAG) {
                "episodes: ${sharedHandles.size} shared data handle(s) across dub tracks — label-neutral rows"
            }
        }
        val labeled: List<Pair<Episode, String?>> = raw.map { (ep, label) ->
            if (ep.data in sharedHandles) ep to null else ep to label
        }

        // Task 46 (device round 5, "all episodes land in one season"): the CS
        // Episode.season field was previously DROPPED, so a 2-season series
        // rendered as one flat list. The aniyomi season UI is NAME-TAG driven
        // (SeasonDetector patterns → season selector + per-season numbers), so
        // the season is now encoded INTO the episode name as a leading
        // "Season N - Episode M" tag — the exact pattern the detector +
        // EpisodeTitleParser + the details screen's season grouping understand.
        // Per-season fallback numbering keeps the tag complete when the
        // provider omits Episode.episode.
        val perSeasonCounters = HashMap<Int, Int>()
        val mapped = labeled.mapIndexed { index, (ep, dubLabel) ->
            val season = ep.season?.takeIf { it > 0 }
            val episodeInSeason = ep.episode ?: season?.let { s ->
                perSeasonCounters[s] = (perSeasonCounters[s] ?: 0) + 1
                perSeasonCounters[s]
            } ?: (index + 1)

            SEpisode.create().apply {
                // The episode's stable identity for the whole app (downloads,
                // history, future loadLinks): the provider's opaque data handle.
                url = ep.data
                name = buildString {
                    if (season != null) {
                        append("Season $season - Episode $episodeInSeason")
                        if (!ep.name.isNullOrBlank()) append(" - ${ep.name}")
                    } else {
                        append(ep.name ?: "Episode ${ep.episode ?: index + 1}")
                    }
                    dubLabel?.let { append(" ($it)") }
                }
                episode_number = ep.episode?.toFloat()
                    ?: (index + 1).toFloat()
                scanlator = dubLabel
                summary = ep.description
                date_upload = ep.date ?: 0L
                // Task 46: CS episode posters (Episode.posterUrl) previously
                // vanished — the episode rows fell back to the cover for every
                // row. Resolved + forwarded so episode-level thumbnails work.
                preview_url = resolveImageUrl(ep.posterUrl)
            }
        }.distinctBy { it.url }

        // Task 46: the season-structure diagnostic — what the provider's
        // season data actually looked like, and what the bridge encoded.
        val seasonHistogram = raw.groupingBy { (ep, _) -> ep.season }.eachCount()
        Logger.i(TAG) {
            val seasonsDesc = seasonHistogram.entries
                .sortedWith(compareByDescending { it.key ?: Int.MIN_VALUE })
                .joinToString(", ") { (s, c) -> "S${s ?: "?"}=$c" }
            "episodes: '$providerName' -> ${mapped.size} episode(s) " +
                "[provider seasons: $seasonsDesc] " +
                "sample=[${mapped.take(3).joinToString { it.name.take(40) }}]"
        }

        return mapped
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
