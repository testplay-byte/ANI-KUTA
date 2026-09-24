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
