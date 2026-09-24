package com.confused.anikuta.feature.extensionssettings.testing.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DETAILS PAGE (round 82, D-576; IO-fixed round 83, D-578): loads the full
 * details for the chain's test anime (from SEARCH, else HOME_PAGE). The
 * source call runs on [Dispatchers.IO] — the round-82 Main-thread call threw
 * NetworkOnMainThreadException on real aniyomi extensions (see SearchTest's
 * header for the full anatomy). Passes when the source answers without
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

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(Dispatchers.IO) {
            val anime = context.foundAnime
                ?: return@withContext TestOutcome.skip("No anime found — search and home page both failed")
            val from = context.animeSourceLabel ?: "search"
            val details = context.source.getAnimeDetails(anime)
            val title = details.title.ifBlank { anime.title }
            TestOutcome.pass(
                "Loaded details for \u201C$title\u201D (from the $from)",
                detail = details.url.ifBlank { anime.url },
            )
        }
}
