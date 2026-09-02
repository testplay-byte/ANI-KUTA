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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
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
    /** The site root — Cloudflare WebView-solver target + bridge baseUrl. */
    val mainUrl: String,
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
@kotlinx.serialization.Serializable
data class CsContentCard(
    val providerName: String,
    val name: String,
    val url: String,
    val posterUrl: String?,
    /** TvType name (Movie / TvSeries / Anime / …) — chip on the details screen. */
    val type: String?,
    val year: Int?,
)

/**
 * One browse shelf rendered as its own titled row on the search page (Task 44,
 * device round 3: "show a popular, latest and other sections in row format").
 * [title] is the provider's own shelf name ("Latest Updated", "Most Popular", …).
 *
 * Task 64 (round 24 — F): [shelfIndex] is the shelf's ORIGINAL index in the
 * provider's mainPage — captured BEFORE any empty-shelf compaction or
 * same-title merge. The category subpages ([browseShelf]) resolve their shelf
 * by THIS index, so it must survive every list transformation downstream.
 * -1 = a legacy cached snapshot from before the field existed (the cache
 * treats those as stale and refetches).
 */
@kotlinx.serialization.Serializable
data class CsBrowseSection(
    val title: String,
    val items: List<CsContentCard>,
    val shelfIndex: Int = -1,
)

// NOTE (Task 48): [CsBrowseSection] + [CsContentCard] are @Serializable so
// [CloudstreamBrowseCache] can persist the browse feed — keep every new field
// optional or give it a default to stay snapshot-compatible.

/** One page of results (page 1 only in phase 1 — the aniyomi flow is page-1 too). */
data class CsContentPage(
    val items: List<CsContentCard>,
    val hasNext: Boolean = false,
)

// ── D-387 (round 25): the PHASED browse events ─────────────────────────────

/**
 * D-387 (round 25 — the phased browse loading pipeline): the events emitted
 * by [CloudstreamContentRepository.browseSectionsProgressive]. The user's
 * spec: "it will first of all load up all the categories, then load up all
 * the contents; while it is doing that it will show the beautiful animation
 * of what it is doing; after everything has been loaded it will show all the
 * results properly; after showing the results it will start to load up the
 * cover images" — mapped to:
 *  1. [Categories] — the category skeleton (the MERGED row structure —
 *     titles + original shelf indexes), known BEFORE any network request;
 *  2. [Section] — one shelf's content, emitted the MOMENT its request lands
 *     (rows fill in progressively, in arrival order — the slowest shelf no
 *     longer gates the page);
 *  3. [Complete] — the canonical final result (shelf order + same-title merge
 *     + cap + cache), so the ViewModel can run the display arrangement
 *     (restore / smart-shuffle + persist) exactly as before.
 *
 * Covers (phase 4 of the spec) are NOT part of this pipeline — Coil loads
 * them per card with crossfade as the rows compose (unchanged).
 */
sealed interface CsBrowseEvent {
    /** The category skeleton — every row the page will have, before any fetch. */
    data class Categories(val slots: List<CsBrowseSlot>) : CsBrowseEvent

    /** One shelf's content landed (empty shelves never emit — their slot keeps shimmering until [Complete]). */
    data class Section(val section: CsBrowseSection) : CsBrowseEvent

    /** The canonical final result (merged, capped, cached). */
    data class Complete(val sections: List<CsBrowseSection>) : CsBrowseEvent
}

/**
 * D-387: one skeleton row of the phased browse — [title] + [shelfIndex] of
 * the MERGED row structure (same-title shelves collapse into ONE slot, first
 * occurrence's index — mirroring [CloudstreamContentRepository]'s
 * mergeSameTitleSections, so the skeleton's shape equals the final result's).
 */
data class CsBrowseSlot(
    val title: String,
    val shelfIndex: Int,
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
    private val browseCache: CloudstreamBrowseCache? = null,
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
                        mainUrl = info.mainUrl,
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

    /**
     * Task 46 (device round 5): true once the plugin manager has completed its
     * FIRST load pass (deferred until the first Activity is alive — see
     * [CloudstreamPluginManager.loadedOnce]). Consumers that heal persisted
     * selection state (the search page's "selected CloudStream provider gone →
     * reset to aniyomi" logic) MUST wait on this signal: at cold start
     * [sources] is legitimately EMPTY for the first moments, which the old
     * healing logic misread as "provider uninstalled" — the device-reported
     * "it forgets my CloudStream source after restarting the app" bug.
     */
    val sourcesLoaded: StateFlow<Boolean> = manager.loadedOnce

    /**
     * Task 48 (device round 7): the cached-browse surface for the search
     * page's instant-open flow. Returns the newest snapshot WITHOUT touching
     * the network (memory first, one disk read per cold start). Callers render
     * it immediately, then use [browseIsFresh] to decide whether a background
     * refresh is due (stale-while-revalidate — see [CloudstreamBrowseCache]).
     */
    suspend fun cachedBrowseSections(providerName: String): List<CsBrowseSection>? =
        browseCache?.peek(providerName)?.sections

    /**
     * Task 62 (round 22 — the stable randomized browse): the persisted display
     * arrangement (row order + per-row item order) for [providerName]'s cached
     * browse — null when none was ever saved. The search page RESTORES it on a
     * cold app reopen instead of re-shuffling (the round-22 device report: "I
     * close the app completely and reopen it, it opens on that exact same
     * search page — the results are reloaded [randomized], this is not how
     * things should be handled").
     */
    suspend fun cachedBrowseDisplay(providerName: String): CsBrowseDisplay? =
        browseCache?.peek(providerName)?.display

    /**
     * Task 62: persists the display arrangement onto [providerName]'s snapshot
     * (memory + disk). Called after every shuffle so the NEXT cold reopen
     * renders the exact same arrangement.
     */
    fun saveBrowseDisplay(providerName: String, display: CsBrowseDisplay) {
        browseCache?.saveDisplay(providerName, display)
    }

    /** True when the cached snapshot for [providerName] is younger than the TTL. */
    fun browseIsFresh(providerName: String): Boolean =
        browseCache?.isFresh(providerName) ?: false

    /** Drops one provider's cached browse (provider gone / untrusted). */
    fun invalidateBrowseCache(providerName: String) {
        browseCache?.invalidate(providerName)
    }

    // ── Execution ───────────────────────────────────────────────────────────

    /**
     * The blank-query browse, SECTIONED (Task 44, device round 3): every shelf
     * of the provider's mainPage list is fetched (page 1, in PARALLEL) and
     * mapped to one [CsBrowseSection] — "Latest Updated", "Most Popular", etc.,
     * each rendered as its own titled horizontal row on the search page (the
     * user's "popular, latest and other sections in row format" request).
     *
     * Callers check [CsProviderSource.hasMainPage] first; a provider that
     * didn't implement getMainPage throws NotImplementedError, which surfaces
     * as a normal error (never silent).
     *
     * Per-shelf failure is TOLERATED (logged with the shelf name under the
     * `browse:` prefix — one broken shelf never blanks the whole page); only a
     * failure of EVERY shelf yields an empty result. Coroutine cancellation
     * still propagates from inside the parallel fetches.
     */
    suspend fun browseSections(providerName: String): List<CsBrowseSection> {
        // D-387 (round 25): the classic all-at-once API is now a thin terminal
        // collector of the PHASED pipeline — one implementation, two consumers
        // (this fun for any legacy caller; SearchViewModel collects the events
        // for the progressive UI). The flow throws exactly like this fun used
        // to (all-shelves Cloudflare block / cancellation propagate).
        var result: List<CsBrowseSection> = emptyList()
        browseSectionsProgressive(providerName).collect { event ->
            if (event is CsBrowseEvent.Complete) result = event.sections
        }
        return result
    }

    /**
     * D-387 (round 25 — the phased browse pipeline + the category-bleeding fix):
     * the full [browseSections] behavior expressed as a Flow of
     * [CsBrowseEvent]s.
     *
     * Phase 1 — [CsBrowseEvent.Categories]: the category skeleton (titles +
     * original shelf indexes, SAME-TITLE-MERGED exactly like the final result)
     * is known from `provider.mainPage` BEFORE any network request → emitted
     * immediately, so the UI can show the full category structure with
     * shimmer rows instead of a blank spinner.
     *
     * Phase 2 — [CsBrowseEvent.Section]: every shelf is fetched in parallel
     * (per-shelf failure TOLERATED, logged under the `browse:` prefix; only a
     * failure of EVERY shelf yields an empty result; cancellation propagates);
     * each shelf's section is emitted the MOMENT its response lands — rows
     * fill in progressively, the slowest shelf no longer gates the whole page.
     *
     * THE BLEEDING FIX rides this phase: many CloudStream providers IGNORE
     * MainPageRequest.name and return the WHOLE home response (EVERY named
     * list) for ANY shelf request. The previous code flattened ALL lists
     * regardless of their names — every category row got the same mixed
     * content (the round-25 report: "content from one category was bleeding
     * into other categories"). [shelfLists] now selects the lists that belong
     * to the REQUESTED shelf (name match → single list → all-lists fallback).
     *
     * Phase 3 — [CsBrowseEvent.Complete]: the canonical final result — shelf
     * order restored, same-title sections merged, capped at
     * [MAX_SECTION_ITEMS], cached (non-empty results only — Task 48) — so the
     * ViewModel's display arrangement (restore / smart-shuffle + persist) is
     * byte-identical with the pre-rework behavior.
     */
    fun browseSectionsProgressive(providerName: String): Flow<CsBrowseEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            val provider = resolveProvider(providerName)
            val shelves = provider.mainPage
            val started = System.currentTimeMillis()
            Logger.i(TAG) {
                "browse: $providerName — ${shelves.size} shelf(ves): [${shelves.joinToString { it.name }}]"
            }

            // ── Phase 1: the category skeleton — needs ZERO network. ──
            val skeleton = mergeSameTitleSections(
                shelves.mapIndexed { index, data ->
                    CsBrowseSection(title = data.name, items = emptyList(), shelfIndex = index)
                },
            )
            send(
                CsBrowseEvent.Categories(
                    slots = skeleton.map { CsBrowseSlot(title = it.title, shelfIndex = it.shelfIndex) },
                ),
            )

            // ── Phase 2: fetch every shelf in parallel; emit as each lands. ──
            val firstCloudflareBlock =
                java.util.concurrent.atomic.AtomicReference<com.lagradost.cloudstream3.network.CloudflareBlockedException?>(null)
            val collected = java.util.concurrent.ConcurrentLinkedQueue<CsBrowseSection>()
            coroutineScope {
                shelves.mapIndexed { index, data ->
                    async {
                        val request = MainPageRequest(
                            name = data.name,
                            data = data.data,
                            horizontalImages = data.horizontalImages,
                        )
                        val response = try {
                            provider.getMainPage(1, request)
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (cf: com.lagradost.cloudstream3.network.CloudflareBlockedException) {
                            firstCloudflareBlock.compareAndSet(null, cf)
                            Logger.w(TAG) {
                                "browse: $providerName shelf '${data.name}' blocked by Cloudflare: ${cf.message}"
                            }
                            null
                        } catch (t: Throwable) {
                            Logger.w(TAG) {
                                "browse: $providerName shelf '${data.name}' FAILED: " +
                                    "${t::class.java.simpleName}: ${t.message}"
                            }
                            null
                        }
                        // D-387: the name-matched lists — see [shelfLists] (THE
                        // category-bleeding fix; same discipline as browseShelf).
                        val cards = response
                            ?.let { shelfLists(it, data.name) }
                            .orEmpty()
                            .flatMap { it.list }
                            .distinctBy { it.url }
                            .take(MAX_SECTION_ITEMS)
                            .map { it.toCard(providerName, provider.mainUrl) }
                        Logger.i(TAG) { "browse: $providerName shelf '${data.name}' -> ${cards.size} item(s)" }
                        if (cards.isNotEmpty()) {
                            val section = CsBrowseSection(
                                title = data.name,
                                items = cards,
                                shelfIndex = index,
                            )
                            collected.add(section)
                            // Emitted the moment this shelf lands — thread-safe:
                            // channelFlow's ProducerScope.send is safe from ANY
                            // coroutine inside this scope.
                            send(CsBrowseEvent.Section(section))
                        }
                    }
                }.awaitAll()
            }

            // Every shelf blocked → surface the block as the browse error (the
            // honest-error contract: a challenge page is NEVER "no results").
            firstCloudflareBlock.get()?.let { cf ->
                if (collected.isEmpty()) throw cf
            }

            // ── Phase 3: the canonical final result. ──
            // Shelf order restored (the queue holds ARRIVAL order) so the
            // same-title merge's first-appearance order matches the classic
            // pipeline exactly.
            val mergedSections = mergeSameTitleSections(collected.sortedBy { it.shelfIndex })
            Logger.i(TAG) {
                "browse: $providerName -> ${mergedSections.size} section(s) in ${System.currentTimeMillis() - started}ms"
            }
            // Task 48: every successful (non-empty) browse feeds the instant-open
            // cache — empty results never overwrite a good cached feed.
            if (mergedSections.isNotEmpty()) {
                browseCache?.put(providerName, mergedSections)
            }
            send(CsBrowseEvent.Complete(mergedSections))
        }
    }

    /**
     * D-387 (round 25 — the category-bleeding fix): selects the response's
     * named lists that belong to the REQUESTED shelf. Many CloudStream
     * providers IGNORE MainPageRequest.name and return the WHOLE home
     * response (EVERY named list) for any shelf request — flattening all of
     * them poured every category's content into every row. Selection order:
     *  1. the lists whose [HomePageList.name] matches the shelf name
     *     (case-insensitive, trimmed — providers are inconsistent with casing);
     *  2. a single-list response (nothing to disambiguate — the provider's
     *     one list IS the shelf content);
     *  3. ALL lists flattened — providers whose list names simply never match
     *     the shelf names keep the pre-fix behavior (their lists ARE shelf
     *     content and merging them is the only sane reading).
     */
    private fun shelfLists(
        response: com.lagradost.cloudstream3.HomePageResponse,
        shelfName: String,
    ): List<com.lagradost.cloudstream3.HomePageList> {
        val lists = response.items
        val matching = lists.filter { it.name.trim().equals(shelfName.trim(), ignoreCase = true) }
        return when {
            matching.isNotEmpty() -> matching
            lists.size == 1 -> lists
            else -> lists
        }
    }

    /**
     * Task 64 (round 24 — F): merges sections whose titles match
     * case-insensitively (after a trim) into ONE row — concatenated items,
     * deduped by url, re-capped at [MAX_SECTION_ITEMS], first occurrence's
     * shelfIndex, first-appearance row order. A no-op pass-through when every
     * title is already distinct.
     */
    private fun mergeSameTitleSections(
        sections: List<CsBrowseSection>,
    ): List<CsBrowseSection> {
        val byNormalizedTitle = LinkedHashMap<String, MutableList<CsBrowseSection>>()
        for (section in sections) {
            byNormalizedTitle.getOrPut(section.title.trim().lowercase()) { mutableListOf() }.add(section)
        }
        if (byNormalizedTitle.size == sections.size) return sections
        Logger.i(TAG) {
            "browse: merged ${sections.size} -> ${byNormalizedTitle.size} section(s) " +
                "(same-title shelves combined)"
        }
        return byNormalizedTitle.values.map { group ->
            val first = group.first()
            CsBrowseSection(
                title = first.title,
                items = group.flatMap { it.items }
                    .distinctBy { it.url }
                    .take(MAX_SECTION_ITEMS),
                shelfIndex = first.shelfIndex,
            )
        }
    }

    /**
     * Task 61 (round 21 — the category subpages): ONE provider shelf, PAGED.
     * Resolves the shelf by its ORIGINAL mainPage index (the search page
     * captured it BEFORE the random shuffle) + calls getMainPage(page,
     * request) with the same mapping + dedupe discipline as [browseSections]
     * — but WITHOUT the section item cap (the subpage grid shows everything
     * the provider returns). A Cloudflare block or any failure THROWS (the
     * honest-error contract); a null response parses as an empty page.
     */
    suspend fun browseShelf(providerName: String, shelfIndex: Int, page: Int): CsContentPage = withContext(Dispatchers.IO) {
        val provider = resolveProvider(providerName)
        val shelf = provider.mainPage.getOrNull(shelfIndex)
            ?: throw IllegalStateException(
                "Provider '$providerName' has no shelf at index $shelfIndex (its main page changed?)",
            )
        val started = System.currentTimeMillis()
        Logger.i(TAG) { "browseShelf: $providerName shelf '${shelf.name}' page=$page" }
        val request = MainPageRequest(
            name = shelf.name,
            data = shelf.data,
            horizontalImages = shelf.horizontalImages,
        )
        val response = provider.getMainPage(page, request)
            ?: return@withContext CsContentPage(emptyList())
        // D-387 (round 25 — the category-bleeding fix, same as the browse
        // pipeline): providers that ignore MainPageRequest.name and return
        // the WHOLE home would otherwise pour every category's content into
        // this category's subpage. Name-matched selection first.
        val cards = shelfLists(response, shelf.name)
            .flatMap { it.list }
            .distinctBy { it.url } // D-304 duplicate-key crash guard
            .map { it.toCard(providerName, provider.mainUrl) }
        Logger.i(TAG) {
            "browseShelf: $providerName shelf '${shelf.name}' page=$page -> " +
                "${cards.size} item(s) hasNext=${response.hasNext} " +
                "in ${System.currentTimeMillis() - started}ms"
        }
        CsContentPage(cards, response.hasNext)
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
                .map { it.toCard(providerName, provider.mainUrl) }
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

    /**
     * Task 46: relativizes RELATIVE poster paths against the provider's
     * mainUrl (many providers return "/poster/x.jpg" — Coil silently fails on
     * those; same fix as the bridge's resolveImageUrl).
     */
    private fun absolutize(rawUrl: String?, mainUrl: String): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> mainUrl.trimEnd('/') + trimmed
            else -> trimmed
        }
    }

    private fun SearchResponse.toCard(providerName: String, providerMainUrl: String): CsContentCard {
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
            posterUrl = absolutize(posterUrl, providerMainUrl),
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

        /** Per-section cap — a row shows ~20 cards; full pagination belongs to a future session. */
        private const val MAX_SECTION_ITEMS = 20
    }
}
