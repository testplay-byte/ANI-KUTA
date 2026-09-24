package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

import kotlinx.coroutines.withContext

/**
 * EPISODE LIST (round 82, D-576; IO-fixed round 83, D-578; candidate walk
 * round 85): loads the episode list for the chain's test anime. The source
 * call runs on the context's isolation dispatcher (D-583). Passes when ≥1
 * episode comes back — the list lands in the context for VIDEO_RESOLVE.
 *
 * ROUND 85 — the SMART CANDIDATE WALK: an entry with ZERO episodes is no
 * longer an automatic verdict. The test walks the candidate pool forward
 * (search results, then home-page entries) and the first candidate WITH
 * episodes wins — the chain then resolves THAT entry's streams. Only when
 * every candidate is episode-less does the test fail honestly.
 */
class EpisodeListTest : ExtensionTest {

    override val kind = ExtensionTestKind.EPISODE_LIST
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
            var deadEnds = 0
            var lastTitle = context.foundAnime?.title.orEmpty()
            for (candidate in pool) {
                try {
                    val episodes = context.source.getEpisodeList(candidate)
                    if (episodes.isNotEmpty()) {
                        context.episodes = episodes
                        if (candidate !== context.foundAnime) {
                            context.foundAnime = candidate
                            context.animeSourceLabel = "fallback entry"
                        }
                        val firstName = episodes.first().name
                        return@withContext TestOutcome.pass(
                            "${episodes.size} episode${if (episodes.size == 1) "" else "s"}" +
                                if (deadEnds > 0) " (after $deadEnds empty candidate${if (deadEnds == 1) "" else "s"})" else "",
                            detail = firstName,
                        )
                    }
                    deadEnds++
                    lastTitle = candidate.title
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    deadEnds++
                    lastTitle = candidate.title
                }
            }
            TestOutcome.fail(
                "No episodes for \u201C$lastTitle\u201D" +
                    if (pool.size > 1) " (or any of the ${pool.size - 1} other candidates)" else "",
                detail = context.foundAnime?.url,
            )
        }
}
