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
                val attempt = tryPhrase(context, phrase)
                outcomes.add(attempt)
                if (attempt is Attempt.Win) return@withContext attempt.outcome
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
        } catch (ce: CancellationException) {
            // D-583: NEVER eat cancellation — a Stop (or scope death) must
            // unwind the ladder, not turn it into "every phrase errored".
            throw ce
        } catch (te: TimeoutCancellationException) {
            // D-583: the inner attempt timeout and an OUTER cancellation-timeout
            // are the same class — disambiguate by the coroutine's own liveness:
            // if THIS coroutine is dead, the TCE belonged to the engine/run
            // budget and must propagate; otherwise it is this attempt's 12s.
            if (!currentCoroutineContext().isActive) throw te
            Attempt.Error("timed out after ${ATTEMPT_TIMEOUT_MS / 1000}s on \u201C${phrase.text}\u201D")
        } catch (t: Throwable) {
            // Plugin bytecode can throw ANYTHING (the bridge guard lesson).
            Attempt.Error("${t::class.java.simpleName}: ${t.message ?: "unknown error"}")
        }
    }

    private companion object {
        const val RUN_TAG = "Anikuta:Feature:ExtensionsTesting"

        /** Per-attempt bound — 4-5 attempts × 12s stays inside the 80s budget. */
        const val ATTEMPT_TIMEOUT_MS = 12_000L

        /** The payload's entry cap — tiny blob, still browsable. */
        const val PAYLOAD_ENTRY_CAP = 12
    }
}
