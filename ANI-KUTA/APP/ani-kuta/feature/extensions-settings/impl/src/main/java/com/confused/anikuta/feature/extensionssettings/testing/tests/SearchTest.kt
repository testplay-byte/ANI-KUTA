package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.SearchPhrase
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import com.confused.anikuta.feature.extensionssettings.testing.TestPayload
import com.confused.anikuta.feature.extensionssettings.testing.TestPayloadEntry
import com.confused.anikuta.feature.extensionssettings.testing.TestingSearchPhrases
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * SEARCH (round 82, D-576; reworked round 83, D-578).
 *
 * THE ROUND-82 BUG (the user's "search failed in 19 ms" report): the test
 * called `source.getSearchAnime(...)` directly on the SCREEN's dispatcher —
 * the MAIN thread. Real aniyomi extensions perform the network exchange at
 * subscription time on the calling thread, so Android threw
 * NetworkOnMainThreadException almost instantly (the 19/7 ms failures). The
 * CloudStream bridge dispatches internally, which is why CS sources passed
 * while every anime extension "failed". Production search never hits this —
 * SearchViewModel wraps every call in `withContext(context.ioDispatcher)` — and
 * now so does this test.
 *
 * THE SMART QUERY LADDER (the user's spec): one query proving nothing means
 * nothing. The test tries the user's custom query first (when non-blank),
 * then the well-known phrases from [TestingSearchPhrases] — anime, series,
 * movie categories — and the FIRST attempt returning ≥1 result wins. Each
 * attempt is independently caught (a phrase erroring moves the ladder on)
 * and inner-bounded by [ATTEMPT_TIMEOUT_MS] so one slow site cannot eat the
 * whole [ExtensionTestKind.SEARCH] budget. The test fails only when EVERY
 * attempt failed — and the message says exactly what happened.
 *
 * The first winning result becomes the chain's test anime
 * ([ExtensionTestContext.foundAnime], preferred over the home page's) so
 * DETAILS/EPISODE_LIST exercise a REAL entry.
 */
class SearchTest : ExtensionTest {

    override val kind = ExtensionTestKind.SEARCH
    override val requiresAnyOf = emptySet<ExtensionTestKind>()

    /** What one ladder attempt ended with (drives the aggregate failure). */
    private sealed interface Attempt {
        /** Results found — the ladder stops here. */
        data class Win(val outcome: TestOutcome) : Attempt

        /** The request completed but the source returned zero results. */
        data object Empty : Attempt

        /** The request itself threw / timed out — carries the short reason. */
        data class Error(val reason: String) : Attempt
    }

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            // The ladder: the user's custom query (when set) first, then the
            // well-known phrase set. Blank query = smart phrases only.
            val attempts = buildList {
                context.searchQuery.isNotBlank().let { isCustom ->
                    if (isCustom) add(SearchPhrase(context.searchQuery.trim(), "Custom query"))
                }
                addAll(TestingSearchPhrases.wellKnown)
            }

            val outcomes = mutableListOf<Attempt>()
            for (phrase in attempts) {
                // D-592 (round 86): the LIVE per-phrase status — the UI shows
                // WHICH phrase is being tried while the ladder walks.
                context.onSearchPhrase?.invoke(
                    "Trying \"${phrase.text}\" (${phrase.category})…",
                )
                Logger.d(RUN_TAG) {
                    "Search: ${context.target.name} trying \"${phrase.text}\" (${phrase.category})"
                }
                val attempt = tryPhrase(context, phrase)
                outcomes.add(attempt)
                Logger.d(RUN_TAG) {
                    val verdict = when (attempt) {
                        is Attempt.Win -> "WIN (${(attempt.outcome.payload?.entries?.size ?: 0)} entries)"
                        Attempt.Empty -> "empty"
                        is Attempt.Error -> "error (${attempt.reason})"
                    }
                    "Search: ${context.target.name} \"${phrase.text}\" → $verdict"
                }
                if (attempt is Attempt.Win) return@withContext attempt.outcome.withLadderStats(
                    tried = outcomes.size,
                    total = attempts.size,
                    phrase = phrase,
                )
            }

            // Nothing won — build the honest aggregate failure.
            val errors = outcomes.filterIsInstance<Attempt.Error>()
            val empties = outcomes.count { it is Attempt.Empty }
            val detail = when {
                errors.isNotEmpty() ->
                    "${errors.size} of ${attempts.size} attempts errored — last: ${errors.last().reason}"
                empties == attempts.size ->
                    "Every phrase returned an empty result list"
                else -> "No phrase produced results"
            }
            Logger.w(RUN_TAG) { "Search: all ${attempts.size} attempts failed for ${context.target.name} ($detail)" }
            TestOutcome.fail(
                "No results from any of the ${attempts.size} test phrases",
                detail = detail,
            )
        }

    /**
     * One ladder attempt. A phrase-level timeout or error NEVER fails the
     * whole test — the ladder moves on (only the engine's outer
     * [ExtensionTestKind.SEARCH] timeout governs the total budget).
     */
    private suspend fun tryPhrase(
        context: ExtensionTestContext,
        phrase: SearchPhrase,
    ): Attempt {
        return try {
            val page = withTimeout(ATTEMPT_TIMEOUT_MS) {
                context.source.getSearchAnime(1, phrase.text, AnimeFilterList())
            }
            val results = page.animes
            if (results.isEmpty()) return Attempt.Empty

            if (context.foundAnime == null) {
                context.foundAnime = results.first()
                context.animeSourceLabel = "search"
            }
            // ROUND 85: drop the WHOLE winning page into the candidate pool —
            // DETAILS/EPISODE_LIST walk it forward when the first entry is dead.
            context.searchCandidates = results
            Attempt.Win(
                TestOutcome.pass(
                    "${results.size} result${if (results.size == 1) "" else "s"} " +
                        "for \u201C${phrase.text}\u201D (${phrase.category})",
                    detail = results.first().title,
                    // The ACTUAL results — rendered as thumbnail cards.
                    payload = TestPayload(
                        entries = results.take(PAYLOAD_ENTRY_CAP).map { anime ->
                            TestPayloadEntry(title = anime.title, thumbnailUrl = anime.thumbnail_url)
                        },
                    ),
                ),
            )
        } catch (te: TimeoutCancellationException) {
            // ROUND 87 (D-593) — THE CATCH-ORDER FIX: a TCE IS-A
            // CancellationException, and this catch USED to sit AFTER the CE
            // catch — dead code — so this attempt's OWN 12s timeout matched
            // the CE branch, was rethrown, escaped the isolation wrapper and
            // KILLED THE WHOLE RUN (the device report: one slow phrase and
            // every test after it died). Now the attempt timeout is caught
            // FIRST and handled as a phrase error; the isActive guard keeps
            // a genuine outer cancellation unwinding.
            if (!currentCoroutineContext().isActive) throw te
            Attempt.Error("timed out after ${ATTEMPT_TIMEOUT_MS / 1000}s on \u201C${phrase.text}\u201D")
        } catch (ce: CancellationException) {
            // D-583 + D-593: unwind ONLY when THIS coroutine is the one being
            // cancelled (a Stop / run death). A cancellation born INSIDE the
            // plugin (its own withTimeout, its flow scopes) leaves this
            // coroutine alive — that is THIS PHRASE's error, not the run's:
            // the ladder moves on (and the isolation wrapper would convert
            // any escape into a verdict anyway — this is the polite path).
            if (!currentCoroutineContext().isActive) throw ce
            Attempt.Error("cancelled on \u201C${phrase.text}\u201D")
        } catch (t: Throwable) {
            // Plugin bytecode can throw ANYTHING (the bridge guard lesson).
            Attempt.Error("${t::class.java.simpleName}: ${t.message ?: "unknown error"}")
        }
    }

    /**
     * D-592 (round 86): the LADDER STATS — the search payload now carries how
     * many phrases were tried before one answered and WHICH one answered, so
     * the search card can show the advanced stats the user asked for ("what
     * number of searches it did — did the first search it did give
     * responses, or the second one, or such").
     */
    private fun TestOutcome.withLadderStats(
        tried: Int,
        total: Int,
        phrase: SearchPhrase,
    ): TestOutcome {
        val payload = this.payload ?: TestPayload()
        return copy(
            payload = payload.copy(
                searchAttempts = tried,
                searchWinningPhrase = phrase.text,
                searchWinningCategory = phrase.category,
            ),
            detail = detail?.let { "$it · attempt $tried of $total" }
                ?: "attempt $tried of $total",
        )
    }

    private companion object {
        const val RUN_TAG = "Anikuta:Feature:ExtensionsTesting"

        /** Per-attempt bound — 4-5 attempts × 12s stays inside the 80s budget. */
        const val ATTEMPT_TIMEOUT_MS = 12_000L

        /** The payload's entry cap — tiny blob, still browsable. */
        const val PAYLOAD_ENTRY_CAP = 12
    }
}
