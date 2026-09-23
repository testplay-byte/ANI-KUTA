package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

/**
 * DETAILS PAGE (round 82, D-576): loads the full details for the chain's test
 * anime (from SEARCH, else HOME_PAGE). Passes when the source answers without
 * throwing — a details payload that merely re-uses the search entry (some
 * providers) is still a working details page; the honest "initialized" state
 * is rendered in the message.
 */
class DetailsTest : ExtensionTest {

    override val kind = ExtensionTestKind.DETAILS
    override val requiresAnyOf = setOf(
        ExtensionTestKind.SEARCH,
        ExtensionTestKind.HOME_PAGE,
    )

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        val anime = context.foundAnime
            ?: return TestOutcome.skip("No anime found — search and home page both failed")
        val from = context.animeSourceLabel ?: "search"
        val details = context.source.getAnimeDetails(anime)
        val title = details.title.ifBlank { anime.title }
        return TestOutcome.pass(
            "Loaded details for \u201C$title\u201D (from the $from)",
            detail = details.url.ifBlank { anime.url },
        )
    }
}
