package com.confused.anikuta.feature.extensionssettings.testing

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Headers

/**
 * The shared chain state threaded through a target's tests (round 82, D-576).
 *
 * The suite is a WATERFALL: SEARCH and HOME_PAGE write [foundAnime]; DETAILS
 * and EPISODE_LIST consume it; VIDEO_RESOLVE consumes the first episode and
 * writes [resolvedVideoUrl]; STREAM_PLAY consumes the resolved URL. A test
 * whose prerequisite data is missing is SKIPPED with a reason instead of
 * failing confusingly — the engine (not the test) decides the skip, so each
 * test file stays single-purpose.
 *
 * Each test reads what it needs and writes exactly one downstream output —
 * this class is the ONLY shared surface between test files.
 */
class ExtensionTestContext(
    /** The live source object under test (aniyomi source or CS bridge). */
    val source: AnimeCatalogueSource,
    /** The target descriptor (ecosystem, icons, providerName, baseUrl). */
    val target: TestableTarget,
    /** The query the Search test runs (user-configurable on the screen). */
    val searchQuery: String,
) {
    /**
     * D-583: the dispatcher the tests route their BLOCKING work through.
     * The engine points it at each kind's DEDICATED isolation thread before
     * running the kind — so an interruptible-blocking call (synchronous
     * OkHttp) sits on the thread [TestIsolation] can interrupt, and a
     * timeout/skip/stop actually aborts the socket work instead of
     * abandoning a zombie on the shared IO pool. Defaults to the shared IO
     * pool (the round-83 behavior) for anything outside a kind run.
     */
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** First anime from SEARCH (preferred) or HOME_PAGE (fallback). */
    var foundAnime: SAnime? = null

    /** Where [foundAnime] came from — surfaced in the result messages. */
    var animeSourceLabel: String? = null

    /**
     * ROUND 85 — the SMART CANDIDATE POOLS. The found-anime fallback used to
     * be one-shot: the single first search/home entry was the chain's only
     * test subject, so one dead entry (a details page with zero episodes) and
     * the run had nothing to try next. Now every winning stage drops its FULL
     * result list here and DETAILS / EPISODE_LIST walk the pool forward when
     * the current candidate yields nothing — the user's "use any of the
     * contents from the home page to check the details page" ask, made real.
     */

    /** Every entry the winning SEARCH attempt returned (search-first pool). */
    var searchCandidates: List<SAnime> = emptyList()

    /** The home/popular entries (the fallback pool when search found nothing usable). */
    var homeCandidates: List<SAnime> = emptyList()

    /** The episode list loaded by EPISODE_LIST. */
    var episodes: List<SEpisode> = emptyList()

    /** The first resolved stream URL from VIDEO_RESOLVE. */
    var resolvedVideoUrl: String? = null

    /** Human label of the resolved stream (quality/title) for the message. */
    var resolvedVideoLabel: String? = null

    /**
     * Request headers the resolved stream requires (aniyomi Video.headers or
     * the CS link's referer + headers merged into one okhttp Headers).
     */
    var resolvedVideoHeaders: Headers? = null

    /** The CS resolver's first usable link object (kept for diagnostics). */
    var resolvedLinkSummary: String? = null

    /**
     * D-592 (round 86): the LIVE search-ladder channel — the Search test
     * invokes it before EVERY phrase attempt ("Trying \"Link Click\"
     * (Donghua)…") and the controller pipes it into the session state so the
     * rows and cards can show WHICH phrase is being tried while the search
     * runs. Null = no live subscriber (the engine never requires one).
     */
    var onSearchPhrase: ((String) -> Unit)? = null

    /**
     * The deduped candidate walk order for DETAILS / EPISODE_LIST: the
     * current pick first, then the search pool, then the home pool (URL-dedup,
     * title-dedup as a fallback for url-less entries).
     */
    fun candidatePool(): List<SAnime> {
        val seen = HashSet<String>()
        return listOfNotNull(foundAnime)
            .asSequence()
            .plus(searchCandidates)
            .plus(homeCandidates)
            .filter { anime ->
                val key = anime.url.ifBlank { "t:${anime.title}" }
                seen.add(key)
            }
            .toList()
    }

    /**
     * D-592 (round 86) — the RANDOM WALK ORDER for the DETAILS test (the
     * user's spec): "it will open up from the search page or from the home
     * page, but it will prefer the search page's results, and it will
     * randomly pick from the available results — every single time a
     * different details page, picked from the top ten or however many
     * results it provided at first sight."
     *
     * The head of the walk is a SHUFFLED window of the first [window]
     * entries of the preferred pool (search when non-empty, home otherwise);
     * the rest of the deduped candidate pool follows as the fallback tail —
     * the walk-on-error behavior is untouched, only the starting point is
     * randomized per run.
     */
    fun detailsWalkOrder(window: Int = RANDOM_WINDOW): List<SAnime> {
        val preferred = if (searchCandidates.isNotEmpty()) searchCandidates else homeCandidates
        val head = preferred.take(window).shuffled()
        if (head.isEmpty()) return candidatePool()
        val headKeys = head.mapTo(HashSet()) { it.url.ifBlank { "t:${it.title}" } }
        val rest = candidatePool()
            .filter { candidate -> candidate.url.ifBlank { "t:${candidate.title}" } !in headKeys }
        return head + rest
    }

    private companion object {
        /** The randomized top-of-pool window (the user's "top ten"). */
        const val RANDOM_WINDOW = 10
    }
}

/**
 * One test in the suite. Implementations live ONE PER FILE (the user's
 * modularity contract) and must be pure-ish: read [ExtensionTestContext],
 * return a [TestOutcome], never mutate anything but their own context slots.
 */
interface ExtensionTest {
    val kind: ExtensionTestKind

    /**
     * The kinds whose data this test needs — AT LEAST ONE of them must have
     * PASSED for this test to run (anyOf semantics: DETAILS needs an anime
     * from SEARCH *or* HOME_PAGE). Empty set = always runs.
     */
    val requiresAnyOf: Set<ExtensionTestKind>

    suspend fun run(context: ExtensionTestContext): TestOutcome
}
