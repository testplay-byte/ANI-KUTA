package com.confused.anikuta.feature.extensionssettings.testing

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HOME PAGE (round 82, D-576; IO-fixed round 83, D-578): loads the source's
 * popular / home page. The source call runs on [Dispatchers.IO] — the round-82
 * Main-thread call threw NetworkOnMainThreadException on real aniyomi
 * extensions (the 7 ms failure; see SearchTest's header for the full anatomy).
 * Passes when ≥1 entry renders. If SEARCH failed, this test's first entry
 * becomes the chain's test anime so the downstream tests still have something
 * to exercise.
 */
class HomePageTest : ExtensionTest {

    override val kind = ExtensionTestKind.HOME_PAGE
    override val requiresAnyOf = emptySet<ExtensionTestKind>()

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(Dispatchers.IO) {
            val page: AnimesPage = context.source.getPopularAnime(1)
            val entries = page.animes
            if (entries.isEmpty()) {
                TestOutcome.fail("Popular / home page returned no entries")
            } else {
                if (context.foundAnime == null) {
                    context.foundAnime = entries.first()
                    context.animeSourceLabel = "home page"
                }
                TestOutcome.pass(
                    "${entries.size} entr${if (entries.size == 1) "y" else "ies"} on the home page",
                    detail = entries.first().title,
                )
            }
        }
}
