package com.confused.anikuta.feature.extensionssettings.testing

import com.confused.anikuta.core.common.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The chain runner (round 82, D-576): executes a target's [ExtensionTest]s in
 * order, converts missing prerequisites into SKIPPED results, and emits every
 * state change live (the UI renders each row as Pending → Running → terminal).
 *
 * Round 84 (D-583) — the HARD-TIMEOUT rework: the old engine wrapped each
 * kind in `withTimeout(kind.timeoutMs)`. That bound is COOPERATIVE — an
 * extension whose `suspend fun` override never suspends (fully blocking
 * code) can never observe it, and the whole run wedged (the device report:
 * stuck on the search test, Stop dead). The wall-clock enforcement moved to
 * [TestIsolation.runKind]: the body runs on a dedicated thread, the awaiting
 * side always returns at the deadline (or on skip/stop), and the wedged
 * thread gets interrupted. A timeout/skip is a VERDICT (FAILED "Timed out
 * after …" / SKIPPED "Skipped by user") — never a hang.
 *
 * SKIP SEMANTICS: when the user skips, the CURRENT kind and every REMAINING
 * kind of this target are emitted as SKIPPED("Skipped by user") and the run
 * advances to the next target with [ChainOutcome.abortedByUser] = true (so
 * the verdict cannot count as healthy). No coroutine is cancelled for a
 * skip — the loop stays perfectly healthy.
 *
 * ROUND 86 (D-590) — the HONEST GATE: a kind whose prerequisites all failed
 * is no longer SKIPPED (the device report: dead sources cascaded as a quiet
 * "skipped" wall). The gate now emits FAILED "Not run — <labels> failed" —
 * the user's rule: "if the search page and the home page fail to load, then
 * all of those tests will just directly be marked as failed". The ONLY
 * SKIPPED verdicts left are the user's own skips, the Stop settle, and the
 * DETAILS FORGIVENESS below.
 *
 * THE DETAILS FORGIVENESS (the user's rule, D-590): "if the details page
 * fails to load but the episode list is loaded, it is considered as a pass;
 * if the details page loads but the episode list does not, it is a fail.
 * A DETAILS failure the EPISODE_LIST survives is rewritten to SKIPPED
 * ("Forgiven — the episode list loaded") at the END of the chain — so
 * every consumer of the verdicts (isHealthy, the store, the stats, the
 * re-run scopes) sees the honest chain-level outcome without any of them
 * knowing the rule.
 *
 * MODULARITY: the engine knows nothing about the individual tests — it just
 * walks the [ExtensionTest] list it is given. Swapping, reordering, adding or
 * removing a test never touches this file's logic.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsTesting".
 */
class ExtensionTestEngine(
    private val tests: List<ExtensionTest>,
) {

    companion object {
        private const val TAG = "Anikuta:Feature:ExtensionsTesting"
    }

    /** The chain's end state — the caller needs to know about user aborts. */
    data class ChainOutcome(val abortedByUser: Boolean)

    /**
     * Runs the whole chain for one target.
     *
     * @param skipSignal set by the run controller when the user skips this
     *   target (reset by the controller before each target).
     * @param onResult called for EVERY state change (RUNNING first, then the
     *   terminal PASSED/FAILED/SKIPPED snapshot for the same kind).
     */
    suspend fun run(
        context: ExtensionTestContext,
        skipSignal: AtomicBoolean,
        onResult: (ExtensionTestKind, TestResult) -> Unit,
    ): ChainOutcome {
        val results = mutableMapOf<ExtensionTestKind, TestResult>()
        var abortedByUser = false
        var skippingRest = false

        for (test in tests) {
            val kind = test.kind

            // ── Skip escalation — the user cut this target short ──
            if (skippingRest) {
                val skipped = TestResult(
                    kind = kind,
                    status = TestStatus.SKIPPED,
                    message = "Skipped by user",
                )
                results[kind] = skipped
                onResult(kind, skipped)
                continue
            }
            if (skipSignal.get()) {
                skippingRest = true
                abortedByUser = true
                Logger.w(TAG) { "${context.target.name}: skip requested — at ${kind.label}" }
                val skipped = TestResult(
                    kind = kind,
                    status = TestStatus.SKIPPED,
                    message = "Skipped by user",
                )
                results[kind] = skipped
                onResult(kind, skipped)
                continue
            }

            // ── Prerequisite gate (anyOf semantics) ──
            // ROUND 86 (D-590): FAILED, not SKIPPED — a kind whose feeders all
            // failed was never run, and the user reads that as a failure of
            // the source, not a skip. The message names the labels that
            // actually failed (not the full anyOf set).
            //
            // ROUND 100 (D-680) — THE ANYOF FIX. The old gate read
            // `missing.isNotEmpty()` → the test only ran when EVERY feeder in
            // requiresAnyOf had PASSED — allOf semantics under an anyOf name.
            // The v1.1.56 device logcat proved the damage: YouTube's
            // HOME_PAGE failed (its first shelf is a channel-mode category
            // that errored silently inside the plugin) while SEARCH PASSED
            // with 6 results — and DETAILS/EPISODE_LIST were still emitted as
            // FAILED "Not run — Home page failed", cascade-killing
            // VIDEO_RESOLVE and STREAM_PLAY too. A chain that had a perfectly
            // good search pool to walk reported 5 failures for a working
            // extension. The gate now honors the documented anyOf contract:
            // the test runs when AT LEAST ONE feeder passed; the
            // "Not run — … failed" verdict fires only when NONE did (and
            // then the label list is by definition the full set).
            if (test.requiresAnyOf.isNotEmpty() &&
                test.requiresAnyOf.none { results[it]?.status == TestStatus.PASSED }
            ) {
                val failedLabels = test.requiresAnyOf.joinToString("/") { it.label }
                val result = TestResult(
                    kind = kind,
                    status = TestStatus.FAILED,
                    message = "Not run — $failedLabels failed",
                )
                results[kind] = result
                onResult(kind, result)
                continue
            }

            // ── RUNNING ──
            onResult(kind, TestResult(kind, TestStatus.RUNNING))
            val startedAt = System.nanoTime()

            val outcome: TestOutcome = when (
                val isolated = TestIsolation.runKind(
                    label = kind.name,
                    timeoutMs = kind.timeoutMs,
                    skipSignal = skipSignal,
                    onDispatcher = { context.ioDispatcher = it },
                ) { test.run(context) }
            ) {
                is TestIsolation.KindResult.Done -> isolated.outcome
                TestIsolation.KindResult.TimedOut -> {
                    Logger.w(TAG) { "${context.target.name} / ${kind.label}: timed out after ${kind.timeoutMs}ms" }
                    TestOutcome.fail(
                        "Timed out after ${TestTimeFormat.format(kind.timeoutMs)}",
                        detail = "The extension's code never returned — it was abandoned mid-call",
                    )
                }
                TestIsolation.KindResult.UserSkipped -> {
                    skippingRest = true
                    abortedByUser = true
                    TestOutcome.skip("Skipped by user")
                }
            }

            val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
            val result = if (outcome.skippedReason != null) {
                TestResult(kind, TestStatus.SKIPPED, durationMs, outcome.skippedReason ?: "Skipped")
            } else if (outcome.passed) {
                TestResult(kind, TestStatus.PASSED, durationMs, outcome.message, outcome.detail, outcome.payload)
            } else {
                TestResult(kind, TestStatus.FAILED, durationMs, outcome.message, outcome.detail, outcome.payload)
            }
            results[kind] = result
            onResult(kind, result)
        }

        // ── THE DETAILS FORGIVENESS (round 86, D-590 — the user's rule) ────
        // A details page that fails to load is NOT a chain failure when the
        // episode list loaded — the entry list is the part playback actually
        // needs. DETAILS success + EPISODE_LIST failure stays a FAIL (the
        // condition below cannot rescue it).
        val detailsResult = results[ExtensionTestKind.DETAILS]
        if (
            detailsResult?.status == TestStatus.FAILED &&
            results[ExtensionTestKind.EPISODE_LIST]?.status == TestStatus.PASSED
        ) {
            val forgiven = detailsResult.copy(
                status = TestStatus.SKIPPED,
                message = "Forgiven — the episode list loaded",
            )
            results[ExtensionTestKind.DETAILS] = forgiven
            onResult(ExtensionTestKind.DETAILS, forgiven)
            Logger.i(TAG) {
                "${context.target.name}: DETAILS forgiven — the episode list loaded" +
                    " (original: ${detailsResult.message})"
            }
        }

        // ── THE SEARCH FORGIVENESS (ROUND 93, D-646 — the user's rule) ─────
        // "If the home page loads but the search page does not load, then
        // that one will be considered as a candidate to move forward, and
        // the details page will be opened from the home page itself. Because
        // some extensions might have issues with the search page, but they
        // do work, so those will be considered as working ones." The chain
        // ALREADY continued from the home pool (DETAILS/EPISODE_LIST accept
        // either feeder); this rewrite makes the VERDICT honest too — a
        // search failure the chain survived is forgiven, so a home-page-
        // working extension with broken search still reads as healthy when
        // everything else passed. (A run that failed elsewhere keeps its
        // failures — only the SEARCH row is rewritten.)
        val searchResult = results[ExtensionTestKind.SEARCH]
        if (
            searchResult?.status == TestStatus.FAILED &&
            results[ExtensionTestKind.HOME_PAGE]?.status == TestStatus.PASSED
        ) {
            val forgiven = searchResult.copy(
                status = TestStatus.SKIPPED,
                message = "Forgiven — continued from the home page",
            )
            results[ExtensionTestKind.SEARCH] = forgiven
            onResult(ExtensionTestKind.SEARCH, forgiven)
            Logger.i(TAG) {
                "${context.target.name}: SEARCH forgiven — continued from the home page" +
                    " (original: ${searchResult.message})"
            }
        }
        return ChainOutcome(abortedByUser = abortedByUser)
    }
}
