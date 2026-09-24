package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import kotlinx.coroutines.withContext

/**
 * HOME PAGE (round 82, D-576; IO-fixed round 83, D-578): loads the source's
 * popular / home page. The source call runs on the context's isolation dispatcher (D-583) — the round-82
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
        withContext(context.ioDispatcher) {
            val page: AnimesPage = context.source.getPopularAnime(1)
            val entries = page.animes
            if (entries.isEmpty()) {
                TestOutcome.fail("Popular / home page returned no entries")
            } else {
                if (context.foundAnime == null) {
                    context.foundAnime = entries.first()
                    context.animeSourceLabel = "home page"
                }
                // ROUND 85: the home pool — the smart fallback when the search
                // candidates are all dead ends.
                context.homeCandidates = entries
                TestOutcome.pass(
                    "${entries.size} entr${if (entries.size == 1) "y" else "ies"} on the home page",
                    detail = entries.first().title,
                )
            }
        }
}
