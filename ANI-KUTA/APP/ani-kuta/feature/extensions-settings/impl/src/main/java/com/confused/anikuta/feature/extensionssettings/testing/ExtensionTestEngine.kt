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
            val missing = test.requiresAnyOf
                .filter { results[it]?.status != TestStatus.PASSED }
            if (test.requiresAnyOf.isNotEmpty() && missing.isNotEmpty()) {
                val neededLabels = test.requiresAnyOf.joinToString("/") { it.label }
                val result = TestResult(
                    kind = kind,
                    status = TestStatus.SKIPPED,
                    message = "Needs a passing $neededLabels test",
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
        return ChainOutcome(abortedByUser = abortedByUser)
    }
}
