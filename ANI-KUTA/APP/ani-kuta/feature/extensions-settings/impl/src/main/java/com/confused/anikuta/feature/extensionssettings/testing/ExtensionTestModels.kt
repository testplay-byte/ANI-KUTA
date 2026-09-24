package com.confused.anikuta.feature.extensionssettings.testing

import android.graphics.drawable.Drawable

/**
 * The Extension Testing screen's shared models (round 82, D-576).
 *
 * DESIGN CONTRACT (the user's spec): every test is a SEPARATE file — one test
 * failing must never force edits anywhere else. New tests are added by (1)
 * adding an [ExtensionTestKind] entry, (2) implementing [ExtensionTest] in its
 * own file, (3) appending it to the chain in `ExtensionTestChain` — nothing
 * else in the module changes. Future roadmap hooks (automated test runs,
 * per-extension statistics, a persisted results table) hang off these same
 * models — see DOCUMENTATION/cloudstream-v2/64-ROUND-82.
 */

/** Which extension system the tested source belongs to. */
enum class TestEcosystem {
    ANIYOMI,
    CLOUDSTREAM,
}

/**
 * The individual tests in the suite. [timeoutMs] bounds each test — a hanging
 * site can never wedge the whole run (the engine enforces it).
 */
enum class ExtensionTestKind(
    val label: String,
    val description: String,
    val timeoutMs: Long,
) {
    /** Network reachability of the source's site + round-trip time. */
    PING("Ping", "Reaches the source's site and measures the round trip", 15_000L),

    /** Runs the configured query through the source's search. */
    // D-578: the timeout bounds the WHOLE multi-phrase ladder (custom query +
    // 4 well-known phrases; SearchTest inner-bounds each attempt at 12s), so
    // the round-82 20s cap could cut a legitimately slow source mid-ladder.
    // D-583: 60s was EXACTLY 5×12s (zero headroom — any inter-attempt latency
    // fired the engine budget mid-ladder); 80s gives the ladder real slack.
    SEARCH("Search", "Tries the test query + well-known phrases and counts the results", 80_000L),

    /** Loads the source's popular / home page. */
    HOME_PAGE("Home page", "Loads the source's popular / home page", 20_000L),

    /** Loads full details for the first anime found by SEARCH or HOME_PAGE. */
    DETAILS("Details page", "Loads the full details for a found anime", 25_000L),

    /** Loads the episode list for the found anime. */
    EPISODE_LIST("Episode list", "Loads the episode list for a found anime", 25_000L),

    /**
     * Resolves a playable stream for an episode. Round 85: the budget rose
     * 45s → 90s — the device report caught resolves giving up "after about
     * five seconds" (the CS resolver's degenerate-timeout clamp + the test
     * skipping the production hoster ladder, both fixed this round). A
     * PATIENT resolve is the point: slow hosters deserve the wait, and the
     * hard isolation still guarantees the run moves on at the deadline.
     */
    VIDEO_RESOLVE("Video resolve", "Resolves a playable stream for an episode", 90_000L),

    /** Range-fetches real bytes from the resolved stream URL. */
    STREAM_PLAY("Stream play", "Fetches bytes from the resolved stream URL", 20_000L),
}

/** Lifecycle of one test inside a run. */
enum class TestStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
}

/** What a single test reports back to the engine. */
data class TestOutcome(
    val passed: Boolean,
    val message: String,
    val detail: String? = null,
    val skippedReason: String? = null,
) {
    companion object {
        fun pass(message: String, detail: String? = null) =
            TestOutcome(passed = true, message = message, detail = detail)

        fun fail(message: String, detail: String? = null) =
            TestOutcome(passed = false, message = message, detail = detail)

        /** Prerequisite missing — the engine turns this into a SKIPPED result. */
        fun skip(reason: String) =
            TestOutcome(passed = false, message = "Skipped", skippedReason = reason)
    }
}

/** A frozen snapshot of one test's result (rendered by the UI rows). */
data class TestResult(
    val kind: ExtensionTestKind,
    val status: TestStatus,
    val durationMs: Long = 0L,
    val message: String = "",
    val detail: String? = null,
)

/**
 * One testable source (aniyomi extension source OR bridged CloudStream
 * provider). [iconDrawable] / [iconUrl] are mutually exclusive per ecosystem;
 * [providerName] is the CloudStream link-resolver key; [baseUrl] is the ping
 * target (null → the ping fails fast with an honest message).
 */
data class TestableTarget(
    val id: Long,
    val name: String,
    val ecosystem: TestEcosystem,
    val lang: String?,
    val iconDrawable: Drawable?,
    val iconUrl: String?,
    val providerName: String?,
    val baseUrl: String?,
)

/**
 * The per-target run state the UI renders (immutable snapshots — the run
 * controller holds them in its session map).
 *
 * D-583: a target the user SKIPPED (or whose run was stopped mid-way after
 * a Skip) carries [abortedByUser] — it must NOT count as healthy: a verdict
 * the user cut short is not a passing verdict.
 */
data class TargetRunState(
    val results: Map<ExtensionTestKind, TestResult> = emptyMap(),
    val runningKind: ExtensionTestKind? = null,
    val isRunning: Boolean = false,
    val finished: Boolean = false,
    val abortedByUser: Boolean = false,
) {
    val passedCount: Int get() = results.values.count { it.status == TestStatus.PASSED }
    val failedCount: Int get() = results.values.count { it.status == TestStatus.FAILED }
    val skippedCount: Int get() = results.values.count { it.status == TestStatus.SKIPPED }

    /** The overall card verdict: all chain tests passed AND not user-aborted. */
    val isHealthy: Boolean
        get() = finished && !abortedByUser && failedCount == 0 && passedCount > 0
}
