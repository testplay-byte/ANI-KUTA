package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

import kotlinx.coroutines.withContext

/**
 * DETAILS PAGE (round 82, D-576; IO-fixed round 83, D-578; candidate walk
 * round 85): loads the full details for the chain's test anime. The source
 * call runs on the context's isolation dispatcher (D-583). Passes when the
 * source answers without throwing — a details payload that merely re-uses the
 * search entry (some providers) is still a working details page; the honest
 * "initialized" state is rendered in the message.
 *
 * ROUND 85 — the SMART CANDIDATE WALK (the user's spec): the test no longer
 * dies on the FIRST entry. It walks the context's candidate pool (the chain's
 * pick, then the search results, then the home-page entries) and the first
 * candidate whose details load wins — a dead first entry (an exception) just
 * moves the walk forward. The successful candidate becomes the chain's
 * [ExtensionTestContext.foundAnime] so EPISODE_LIST exercises the SAME entry.
 */
class DetailsTest : ExtensionTest {

    override val kind = ExtensionTestKind.DETAILS
    override val requiresAnyOf = setOf(
        ExtensionTestKind.SEARCH,
        ExtensionTestKind.HOME_PAGE,
    )

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            if (context.foundAnime == null) {
                return@withContext TestOutcome.skip("No anime found — search and home page both failed")
            }
            val pool = context.candidatePool()
            var lastError: String? = null
            pool.forEachIndexed { index, candidate ->
                val from = when {
                    index == 0 -> context.animeSourceLabel ?: "search"
                    context.searchCandidates.any { it.url == candidate.url } ->
                        "search result #${index + 1}"
                    else -> "home page entry #${index + 1}"
                }
                try {
                    val details = context.source.getAnimeDetails(candidate)
                    val title = details.title.ifBlank { candidate.title }
                    // Lock the working candidate in for the rest of the chain.
                    context.foundAnime = candidate
                    context.animeSourceLabel = from
                    return@withContext TestOutcome.pass(
                        "Loaded details for \u201C$title\u201D (from the $from)",
                        detail = details.url.ifBlank { candidate.url },
                    )
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    lastError = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
                }
            }
            TestOutcome.fail(
                "Details failed for all ${pool.size} candidate entr${if (pool.size == 1) "y" else "ies"}",
                detail = lastError,
            )
        }
}
