package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

/**
 * EPISODE LIST (round 82, D-576): loads the episode list for the chain's test
 * anime. Passes when ≥1 episode comes back — the list lands in the context
 * for VIDEO_RESOLVE. Sources with no episodes for the found entry fail with
 * that message (a real finding, not a skip — the search found SOMETHING but
 * it has no episodes, which the user needs to know).
 */
class EpisodeListTest : ExtensionTest {

    override val kind = ExtensionTestKind.EPISODE_LIST
    override val requiresAnyOf = setOf(
        ExtensionTestKind.SEARCH,
        ExtensionTestKind.HOME_PAGE,
    )

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        val anime = context.foundAnime
            ?: return TestOutcome.skip("No anime found — search and home page both failed")
        val episodes = context.source.getEpisodeList(anime)
        if (episodes.isEmpty()) {
            return TestOutcome.fail(
                "No episodes for \u201C${anime.title}\u201D",
                detail = anime.url,
            )
        }
        context.episodes = episodes
        val firstName = episodes.first().name
        return TestOutcome.pass(
            "${episodes.size} episode${if (episodes.size == 1) "" else "s"}",
            detail = firstName,
        )
    }
}
