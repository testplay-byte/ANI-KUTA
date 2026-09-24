package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import com.confused.anikuta.feature.extensionssettings.testing.TestPayload

import kotlinx.coroutines.isActive
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
 * dies on the FIRST entry. It walks the context's candidate pool and the
 * first candidate whose details load wins — a dead first entry (an
 * exception) just moves the walk forward. The successful candidate becomes
 * the chain's [ExtensionTestContext.foundAnime] so EPISODE_LIST exercises
 * the SAME entry.
 *
 * ROUND 86 (D-592) — the RANDOM SEARCH-FIRST PICK: the walk now STARTS from
 * a shuffled window of the top [ExtensionTestContext.detailsWalkOrder]
 * entries — the search pool preferred, a different entry every run (the
 * user: "it will prefer the search page's results, and it will randomly pick
 * from the available results… every single time a different details page").
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
                // D-590 (round 86): FAILED, not SKIPPED — the honest gate in
                // the engine already covers the search+home failure; this
                // defensive path must not contradict it.
                return@withContext TestOutcome.fail("Not run — no anime was found to open")
            }
            val pool = context.detailsWalkOrder()
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
                        // The ACTUAL parsed details — the dossier on the detail page.
                        payload = TestPayload(
                            detailsTitle = title,
                            detailsGenres = details.getGenres()?.take(GENRE_CAP),
                            detailsStatus = statusText(details.status),
                            detailsSynopsis = details.description?.takeIf { it.isNotBlank() }?.take(SYNOPSIS_CAP),
                            detailsThumbnailUrl = details.thumbnail_url ?: candidate.thumbnail_url,
                            // D-592 (round 86): the entry's URL INSIDE the payload
                            // so the dossier card can format it as its own line.
                            detailsUrl = details.url.ifBlank { candidate.url }.ifBlank { null },
                        ),
                    )
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    // D-593: unwind only a REAL run cancellation — a
                    // plugin-internal cancellation is THIS candidate's
                    // error, and the walk moves on.
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw ce
                    lastError = "cancelled: ${ce.message ?: "no message"}"
                } catch (t: Throwable) {
                    lastError = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
                }
            }
            TestOutcome.fail(
                "Details failed for all ${pool.size} candidate entr${if (pool.size == 1) "y" else "ies"}",
                detail = lastError,
            )
        }

    private companion object {
        /** Payload caps — browse-worthy, still tiny. */
        const val GENRE_CAP = 8
        const val SYNOPSIS_CAP = 600

        fun statusText(status: Int): String = when (status) {
            eu.kanade.tachiyomi.animesource.model.SAnime.ONGOING -> "Ongoing"
            eu.kanade.tachiyomi.animesource.model.SAnime.COMPLETED -> "Completed"
            eu.kanade.tachiyomi.animesource.model.SAnime.LICENSED -> "Licensed"
            eu.kanade.tachiyomi.animesource.model.SAnime.PUBLISHING_FINISHED -> "Finished"
            eu.kanade.tachiyomi.animesource.model.SAnime.CANCELLED -> "Cancelled"
            eu.kanade.tachiyomi.animesource.model.SAnime.ON_HIATUS -> "On hiatus"
            else -> "Unknown"
        }
    }
}
