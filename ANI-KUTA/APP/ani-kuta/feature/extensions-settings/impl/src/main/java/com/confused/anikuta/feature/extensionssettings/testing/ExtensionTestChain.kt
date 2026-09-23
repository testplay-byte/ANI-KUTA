package com.confused.anikuta.feature.extensionssettings.testing

import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.feature.extensionssettings.testing.tests.DetailsTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.EpisodeListTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.HomePageTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.PingTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.SearchTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.StreamPlayTest
import com.confused.anikuta.feature.extensionssettings.testing.tests.VideoResolveTest
import okhttp3.OkHttpClient

/**
 * The test CHAIN factory (round 82, D-576) — the single place that knows the
 * suite's order. Adding a test to the suite = add its kind to
 * [ExtensionTestKind], write its file, append it here. Nothing else changes.
 *
 * Order (the user's spec): Ping → Search → Home page → Details → Episode
 * list → Video resolve → Stream play. The chain is a waterfall — each stage
 * feeds its output into the next through [ExtensionTestContext], and the
 * engine SKIPS stages whose prerequisites failed.
 *
 * FUTURE (documented in doc 64): automated batch schedules and per-extension
 * statistics will reuse this same chain — a persisted runner would call
 * [build] on a schedule and store the emitted [TestResult]s.
 */
object ExtensionTestChain {

    /**
     * @param httpClient the shared test HTTP client (ping + stream play).
     * @param csResolver the CloudStream link resolver (CS video resolve).
     */
    fun build(
        httpClient: OkHttpClient,
        csResolver: CloudstreamLinkResolver,
    ): List<ExtensionTest> = listOf(
        PingTest(httpClient),
        SearchTest(),
        HomePageTest(),
        DetailsTest(),
        EpisodeListTest(),
        VideoResolveTest(csResolver),
        StreamPlayTest(httpClient),
    )
}
