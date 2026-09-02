package com.confused.anikuta.data.cloudstream.content

import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * D-390 (round 26): the DEDICATED CloudStream browse loading module.
 *
 * Round-25 shipped the phased pipeline INSIDE
 * [CloudstreamContentRepository.browseSectionsProgressive] — but the device
 * round-26 report showed (a) content STILL bleeding between categories and
 * (b) loading that "rushes ahead, loading multiple things at once". Both were
 * structural:
 *
 *  - **The bleeding root cause**: the old `shelfLists` matcher had a
 *    permissive final fallback (`else -> lists` — return EVERY list when
 *    nothing matches the shelf name). Many providers ignore
 *    [MainPageRequest.name] and return the WHOLE home for any shelf request;
 *    the fallback then poured the same mixed content into EVERY category row.
 *    The fix that "made it worse" was really this hole never being closed.
 *  - **The rushing root cause**: Phase 2 fired ALL shelf requests in parallel
 *    (`shelves.mapIndexed { async { … } }.awaitAll()`) — for a
 *    name-ignoring provider that's N identical full-home downloads at once.
 *
 * This module owns the WHOLE browse load from now on — one class, one
 * responsibility, nothing else in the data layer touches the phased flow:
 *
 * ```
 * Phase A  ── the PLAN (zero network): read provider.mainPage, emit the
 *             category skeleton (merged row structure) → the UI renders the
 *             full category layout with loading animation immediately.
 * Phase B  ── the CONTENT (STRICTLY SEQUENTIAL, one shelf at a time, in the
 *             provider's own order — nothing is rushed ahead): each shelf's
 *             request lands → its section is emitted the same moment → its
 *             row fills in place, top to bottom.
 *             • strict name matching (exact → fuzzy) — NEVER the all-lists
 *               fallback: an empty row beats mixed content, always;
 *             • static-home detection: when a response is recognized as the
 *               provider's full home (its lists answer OTHER shelves too), it
 *               is captured ONCE as a snapshot — every later shelf is SLICED
 *               from the snapshot with ZERO further network. One fetch
 *               replaces N.
 * Phase C  ── the CANONICAL result: same-title merge + cap + a
 *             duplicate-content safety net (sections that ended up with
 *             identical content are collapsed to one — the last possible
 *             bleeding vector), emitted as [CsBrowseEvent.Complete].
 * ```
 *
 * Cover images are deliberately NOT part of this pipeline: Coil loads them
 * per card, AFTER the row's content is on screen (the "results first, covers
 * after" order the user specified).
 *
 * The repository delegates to this class and keeps only the cache write +
 * provider resolution ([CloudstreamContentRepository.browseSectionsProgressive]).
 */
class CsBrowseLoader(
    private val provider: MainAPI,
    private val providerName: String,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Exec"

        /**
         * D-390: the duplicate-content safety-net comparison depth — two
         * sections whose first [DUP_CHECK_DEPTH] item URLs are identical are
         * considered the same content (see [dropDuplicateContentSections]).
         */
        private const val DUP_CHECK_DEPTH = 5
    }

    /**
     * The static-home snapshot (D-390): when a provider ignores
     * [MainPageRequest.name] its `getMainPage` answer is the FULL home — every
     * list, every time. The first such answer is captured here; every later
     * shelf is sliced out of it with no network at all.
     * `null` until detected (well-behaved providers never set it).
     */
    private var homeSnapshot: List<HomePageList>? = null

    /** Normalized names of ALL of the provider's shelves (the plan). */
    private lateinit var shelfNames: Set<String>

    /**
     * The phased load (see the class KDoc). Cold cancellation semantics are
     * unchanged from the round-25 pipeline: per-shelf failures are TOLERATED
     * (logged; one broken shelf never blanks the page); a Cloudflare block on
     * EVERY shelf surfaces as the browse error (a challenge page is never
     * "no results"); CancellationException always propagates.
     */
    fun load(): Flow<CsBrowseEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            val shelves = provider.mainPage
            shelfNames = shelves.map { CsShelfMatcher.normalize(it.name) }.toSet()
            val started = System.currentTimeMillis()
            Logger.i(TAG) {
                "browse: $providerName — ${shelves.size} shelf(ves), SEQUENTIAL " +
                    "phased load: [${shelves.joinToString { it.name }}]"
            }

            // ── Phase A: the plan — the category skeleton, zero network. ──
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

            // ── Phase B: the content — one shelf at a time, in order. ──
            // D-390: strictly sequential (the user: "it does not rush anything
            // ahead or try to load multiple different things at once"). Rows
            // fill top-to-bottom in the provider's own order — calm,
            // traceable, and the snapshot dedup below means a name-ignoring
            // provider is fetched exactly ONCE.
            var firstCloudflareBlock: com.lagradost.cloudstream3.network.CloudflareBlockedException? = null
            val collected = mutableListOf<CsBrowseSection>()
            for ((index, data) in shelves.withIndex()) {
                val request = MainPageRequest(
                    name = data.name,
                    data = data.data,
                    horizontalImages = data.horizontalImages,
                )
                val cards = try {
                    fetchShelfCards(index, request)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (cf: com.lagradost.cloudstream3.network.CloudflareBlockedException) {
                    if (firstCloudflareBlock == null) firstCloudflareBlock = cf
                    Logger.w(TAG) {
                        "browse: $providerName shelf '${data.name}' blocked by Cloudflare: ${cf.message}"
                    }
                    emptyList()
                } catch (t: Throwable) {
                    Logger.w(TAG) {
                        "browse: $providerName shelf '${data.name}' FAILED: " +
                            "${t::class.java.simpleName}: ${t.message}"
                    }
                    emptyList()
                }
                if (cards.isNotEmpty()) {
                    val section = CsBrowseSection(
                        title = data.name,
                        items = cards,
                        shelfIndex = index, // ORIGINAL mainPage index — never compacted
                    )
                    collected += section
                    // Emitted the moment this shelf lands — the row fills in place.
                    send(CsBrowseEvent.Section(section))
                }
            }

            // Every shelf blocked → surface the block as the browse error (the
            // honest-error contract: a challenge page is NEVER "no results").
            if (collected.isEmpty()) {
                firstCloudflareBlock?.let { throw it }
            }

            // ── Phase C: the canonical result. ──
            val merged = dropDuplicateContentSections(
                mergeSameTitleSections(collected.sortedBy { it.shelfIndex }),
            )
            Logger.i(TAG) {
                "browse: $providerName -> ${merged.size} section(s) in " +
                    "${System.currentTimeMillis() - started}ms" +
                    (homeSnapshot?.let { " (static-home snapshot: ${it.size} list(s), 1 fetch total)" } ?: "")
            }
            send(CsBrowseEvent.Complete(merged))
        }
    }

    // ── Phase B internals ─────────────────────────────────────────────────────

    /**
     * Fetches ONE shelf's cards (D-390). Order of operations:
     *
     *  1. **Snapshot fast-path** — if this provider already proved to be a
     *     static-home (name-ignoring) provider, SLICE the shelf out of the
     *     captured [homeSnapshot] with no network at all.
     *  2. Otherwise call `getMainPage(1, request)` and match STRICTLY:
     *     exact (normalized) name match → fuzzy (containment) match.
     *  3. When nothing matches, decide WHY:
     *     - a single list that belongs to a DIFFERENT shelf → skip it (that
     *       shelf's own request — or the snapshot — will deliver it; taking it
     *       here would bleed);
     *     - a single list that matches no shelf → accept it (a provider that
     *       renamed this shelf's list; the legacy single-list reading);
     *     - multiple lists (or lists answering other shelves) → this is the
     *       FULL HOME → capture the snapshot + slice this shelf from it.
     *     There is NO "return everything" path — the round-25 bleeding hole.
     */
    private suspend fun fetchShelfCards(
        index: Int,
        request: MainPageRequest,
    ): List<CsContentCard> {
        // 1. The static-home fast path — zero network.
        homeSnapshot?.let { snapshot ->
            val sliced = CsShelfMatcher.selectListsForShelf(snapshot, request.name)
            Logger.i(TAG) {
                "browse: $providerName shelf '${request.name}' (#$index) -> " +
                    "${sliced.sumOf { it.list.size }} item(s) SLICED from the static-home " +
                    "snapshot (no network)"
            }
            return sliced.toCards().capped()
        }

        // 2. The live fetch (sequential — one shelf in flight at a time).
        val response: HomePageResponse? = provider.getMainPage(1, request)
        val lists = response?.items.orEmpty()
        if (lists.isEmpty()) {
            Logger.i(TAG) { "browse: $providerName shelf '${request.name}' (#$index) -> empty response" }
            return emptyList()
        }

        val matching = CsShelfMatcher.selectListsForShelf(lists, request.name)
        if (matching.isNotEmpty()) {
            // The answer for THIS shelf (exact or fuzzy). Before using it,
            // check whether the response ALSO answers other shelves — that
            // marks a full-home provider: capture the snapshot so every later
            // shelf is free.
            maybeCaptureSnapshot(lists, requestedShelfName = request.name)
            Logger.i(TAG) {
                "browse: $providerName shelf '${request.name}' (#$index) -> " +
                    "${matching.sumOf { it.list.size }} item(s) via " +
                    "${matching.size} matched list(s)" +
                    (if (homeSnapshot != null) " + snapshot captured" else "")
            }
            return matching.toCards().capped()
        }

        // 3. Nothing matches this shelf by name.
        if (lists.size == 1) {
            val single = lists.first()
            val singleName = CsShelfMatcher.normalize(single.name)
            val belongsToOtherShelf = singleName != CsShelfMatcher.normalize(request.name) &&
                singleName in shelfNames
            if (belongsToOtherShelf) {
                // The provider answered with ANOTHER shelf's list (it ignores
                // the name filter). Taking it here would bleed — skip. That
                // shelf's own request (or the snapshot) delivers the content.
                Logger.w(TAG) {
                    "browse: $providerName shelf '${request.name}' (#$index) got single " +
                        "list '${single.name}' which belongs to ANOTHER shelf — skipped " +
                        "(bleeding guard)"
                }
                return emptyList()
            }
            // A renamed single list — the provider's answer for THIS shelf.
            Logger.i(TAG) {
                "browse: $providerName shelf '${request.name}' (#$index) -> " +
                    "${single.list.size} item(s) via single unmatched list " +
                    "'${single.name}' (renamed-shelf reading)"
            }
            return listOf(single).toCards().capped()
        }

        // Multiple unmatched lists → the full home. Capture + slice (the
        // matcher above already returned empty for this shelf — honest).
        maybeCaptureSnapshot(lists, requestedShelfName = request.name)
        Logger.w(TAG) {
            "browse: $providerName shelf '${request.name}' (#$index) -> NO name match " +
                "in a ${lists.size}-list response (names: ${lists.map { it.name }}) — " +
                "treated as the full home + snapshot captured; this shelf renders empty " +
                "rather than bleeding other categories' content"
        }
        // Slice from the fresh snapshot in case the requested shelf actually
        // matches one of these lists under normalization the strict pass missed.
        val sliced = CsShelfMatcher.selectListsForShelf(homeSnapshot.orEmpty(), request.name)
        return sliced.toCards().capped()
    }

    /**
     * Captures [lists] as the static-home snapshot when the response answers
     * shelves BEYOND the requested one (≥1 other list whose normalized name
     * is one of the provider's shelves). Idempotent — only the FIRST
     * qualifying response wins.
     */
    private fun maybeCaptureSnapshot(lists: List<HomePageList>, requestedShelfName: String) {
        if (homeSnapshot != null) return
        val requested = CsShelfMatcher.normalize(requestedShelfName)
        val answersOtherShelves = lists.any {
            val n = CsShelfMatcher.normalize(it.name)
            n != requested && n in shelfNames
        }
        if (answersOtherShelves) {
            homeSnapshot = lists
            Logger.i(TAG) {
                "browse: $providerName — STATIC-HOME provider detected: a shelf " +
                    "request answered ${lists.size} list(s) covering other shelves too " +
                    "(${lists.map { it.name }}) — snapshot captured, all later shelves " +
                    "slice from it (ONE fetch replaces N)"
            }
        }
    }

    /** Maps + dedupes the selected lists into cards (URL-distinct). */
    private fun List<HomePageList>.toCards(): List<CsContentCard> =
        flatMap { it.list }
            .distinctBy { it.url } // D-304 duplicate-key crash guard
            .map { it.toCsCard(providerName, provider.mainUrl) }

    /** Caps at the per-row limit ([CS_MAX_SECTION_ITEMS]). */
    private fun List<CsContentCard>.capped(): List<CsContentCard> =
        take(CS_MAX_SECTION_ITEMS)

    // ── Phase C internals ─────────────────────────────────────────────────────

    /**
     * D-390: the duplicate-content safety net — the LAST possible bleeding
     * vector. If a provider answers EVERY shelf with the same content (the
     * single-list static-home case the matcher can't detect per-shelf), the
     * sections would all carry identical items. Two sections whose first
     * [DUP_CHECK_DEPTH] item URLs are identical are the same content: keep the
     * FIRST (the provider's own order = the most prominent shelf), drop the
     * rest, log loudly. A row disappearing is honest; N rows with the same
     * mixed content is the bug the user reported twice.
     */
    private fun dropDuplicateContentSections(
        sections: List<CsBrowseSection>,
    ): List<CsBrowseSection> {
        if (sections.size < 2) return sections
        val seen = HashSet<List<String>>()
        val kept = mutableListOf<CsBrowseSection>()
        val dropped = mutableListOf<String>()
        for (section in sections) {
            val signature = section.items.take(DUP_CHECK_DEPTH).map { it.url }
            if (section.items.isNotEmpty() && !seen.add(signature)) {
                dropped += section.title
            } else {
                kept += section
            }
        }
        if (dropped.isNotEmpty()) {
            Logger.w(TAG) {
                "browse: $providerName — duplicate-content safety net: dropped " +
                    "${dropped.size} section(s) with identical content " +
                    "($dropped) — a provider answering every shelf with the same list"
            }
        }
        return kept
    }
}

/**
 * D-390: the STRICT shelf-name matcher — the category-bleeding fix's core.
 *
 * Selection order (per shelf request):
 *  1. **Exact** — [HomePageList.name] equals the shelf name after
 *     normalization (trim + lowercase + collapsed whitespace).
 *  2. **Fuzzy** — containment in either direction ("Latest Updates" ↔
 *     "Latest Updated"); only non-blank, non-trivial names.
 *  3. **NOTHING** — there is deliberately NO "return all lists" fallback.
 *     An empty row is honest; mixed content is the round-25/26 bleeding bug.
 */
internal object CsShelfMatcher {

    /** trim + lowercase + single-space collapse — providers are inconsistent. */
    fun normalize(name: String): String =
        name.trim().lowercase().replace(WHITESPACE, " ")

    /**
     * Selects the lists of [lists] that belong to the shelf [shelfName].
     * NEVER returns every list as a fallback (see the object KDoc).
     */
    fun selectListsForShelf(
        lists: List<HomePageList>,
        shelfName: String,
    ): List<HomePageList> {
        if (lists.isEmpty()) return emptyList()
        val shelf = normalize(shelfName)
        // 1. Exact (normalized).
        val exact = lists.filter { normalize(it.name) == shelf }
        if (exact.isNotEmpty()) return exact
        // 2. Fuzzy — containment, guarded against blank/trivial names.
        if (shelf.isNotBlank()) {
            val fuzzy = lists.filter { list ->
                val n = normalize(list.name)
                n.length >= FUZZY_MIN_LENGTH && (n.contains(shelf) || shelf.contains(n))
            }
            if (fuzzy.isNotEmpty()) return fuzzy
        }
        // 3. Deliberately empty — the all-lists fallback was the bleeding hole.
        return emptyList()
    }

    private val WHITESPACE = Regex("\\s+")

    /** Names shorter than this never fuzzy-match (avoid "new" ⊂ everything). */
    private const val FUZZY_MIN_LENGTH = 4
}

// ── Shared browse mapping (used by the loader + the repository) ─────────────

/** Per-row cap — a row shows ~20 cards; full pagination lives on the subpages. */
internal const val CS_MAX_SECTION_ITEMS = 20

/**
 * Task 64 (round 24 — F): merges sections whose titles match
 * case-insensitively (after a trim) into ONE row — concatenated items,
 * deduped by url, re-capped at [CS_MAX_SECTION_ITEMS], first occurrence's
 * shelfIndex, first-appearance row order. A no-op pass-through when every
 * title is already distinct.
 */
internal fun mergeSameTitleSections(
    sections: List<CsBrowseSection>,
): List<CsBrowseSection> {
    val byNormalizedTitle = LinkedHashMap<String, MutableList<CsBrowseSection>>()
    for (section in sections) {
        byNormalizedTitle.getOrPut(section.title.trim().lowercase()) { mutableListOf() }.add(section)
    }
    if (byNormalizedTitle.size == sections.size) return sections
    Logger.i("Anikuta:Data:Cloudstream:Exec") {
        "browse: merged ${sections.size} -> ${byNormalizedTitle.size} section(s) " +
            "(same-title shelves combined)"
    }
    return byNormalizedTitle.values.map { group ->
        val first = group.first()
        CsBrowseSection(
            title = first.title,
            items = group.flatMap { it.items }
                .distinctBy { it.url }
                .take(CS_MAX_SECTION_ITEMS),
            shelfIndex = first.shelfIndex,
        )
    }
}

/**
 * Task 46: relativizes RELATIVE poster paths against the provider's
 * mainUrl (many providers return "/poster/x.jpg" — Coil silently fails on
 * those; same fix as the bridge's resolveImageUrl).
 */
internal fun absolutize(rawUrl: String?, mainUrl: String): String? {
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

/** Maps a CloudStream search response into the UI card model. */
internal fun SearchResponse.toCsCard(providerName: String, providerMainUrl: String): CsContentCard {
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
