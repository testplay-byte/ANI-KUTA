package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

import kotlinx.coroutines.withContext

/**
 * EPISODE LIST (round 82, D-576; IO-fixed round 83, D-578): loads the episode
 * list for the chain's test anime. The source call runs on the context's isolation dispatcher (D-583) —
 * the round-82 Main-thread call threw NetworkOnMainThreadException on real
 * aniyomi extensions (see SearchTest's header for the full anatomy). Passes
 * when ≥1 episode comes back — the list lands in the context for
 * VIDEO_RESOLVE. Sources with no episodes for the found entry fail with that
 * message (a real finding, not a skip — the search found SOMETHING but it has
 * no episodes, which the user needs to know).
 */
class EpisodeListTest : ExtensionTest {

    override val kind = ExtensionTestKind.EPISODE_LIST
    override val requiresAnyOf = setOf(
        ExtensionTestKind.SEARCH,
        ExtensionTestKind.HOME_PAGE,
    )

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            val anime = context.foundAnime
                ?: return@withContext TestOutcome.skip("No anime found — search and home page both failed")
            val episodes = context.source.getEpisodeList(anime)
            if (episodes.isEmpty()) {
                TestOutcome.fail(
                    "No episodes for \u201C${anime.title}\u201D",
                    detail = anime.url,
                )
            } else {
                context.episodes = episodes
                val firstName = episodes.first().name
                TestOutcome.pass(
                    "${episodes.size} episode${if (episodes.size == 1) "" else "s"}",
                    detail = firstName,
                )
            }
        }
}
