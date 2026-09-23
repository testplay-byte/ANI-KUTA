package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome

/**
 * HOME PAGE (round 82, D-576): loads the source's popular / home page (the
 * aniyomi catalogue's getPopularAnime — for bridged CloudStream providers the
 * bridge maps it to the provider's first main-page shelf). Passes when ≥1
 * entry renders. If SEARCH failed, this test's first entry becomes the chain's
 * test anime so the downstream tests still have something to exercise.
 */
class HomePageTest : ExtensionTest {

    override val kind = ExtensionTestKind.HOME_PAGE
    override val requiresAnyOf = emptySet<ExtensionTestKind>()

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        val page = context.source.getPopularAnime(1)
        val entries = page.animes
        if (entries.isEmpty()) {
            return TestOutcome.fail("Popular / home page returned no entries")
        }
        if (context.foundAnime == null) {
            context.foundAnime = entries.first()
            context.animeSourceLabel = "home page"
        }
        return TestOutcome.pass(
            "${entries.size} entr${if (entries.size == 1) "y" else "ies"} on the home page",
            detail = entries.first().title,
        )
    }
}
