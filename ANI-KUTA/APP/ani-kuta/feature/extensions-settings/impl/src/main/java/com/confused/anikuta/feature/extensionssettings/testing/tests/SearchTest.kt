package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * SEARCH (round 82, D-576): runs the screen's configured query through the
 * source's search. Passes when ≥1 result comes back — the first result
 * becomes the chain's test anime ([ExtensionTestContext.foundAnime], preferred
 * over the home page's) so DETAILS/EPISODE_LIST exercise a REAL entry.
 */
class SearchTest : ExtensionTest {

    override val kind = ExtensionTestKind.SEARCH
    override val requiresAnyOf = emptySet<ExtensionTestKind>()

    override suspend fun run(context: ExtensionTestContext): TestOutcome {
        val query = context.searchQuery
        val page = context.source.getSearchAnime(1, query, AnimeFilterList())
        val results = page.animes
        if (results.isEmpty()) {
            return TestOutcome.fail("No results for \u201C$query\u201D", detail = context.source.name)
        }
        if (context.foundAnime == null) {
            context.foundAnime = results.first()
            context.animeSourceLabel = "search"
        }
        return TestOutcome.pass(
            "${results.size} result${if (results.size == 1) "" else "s"} for \u201C$query\u201D",
            detail = results.first().title,
        )
    }
}
