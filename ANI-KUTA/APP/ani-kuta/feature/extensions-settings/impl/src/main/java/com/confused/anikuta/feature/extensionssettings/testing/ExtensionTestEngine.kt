package com.confused.anikuta.feature.extensionssettings.testing

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * The chain runner (round 82, D-576): executes a target's [ExtensionTest]s in
 * order, enforces each kind's timeout, converts missing prerequisites into
 * SKIPPED results, and emits every state change live (the UI renders each row
 * as Pending → Running → terminal).
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

    /**
     * Runs the whole chain for one target.
     *
     * @param onResult called for EVERY state change (RUNNING first, then the
     *   terminal PASSED/FAILED/SKIPPED snapshot for the same kind).
     */
    suspend fun run(
        context: ExtensionTestContext,
        onResult: (ExtensionTestKind, TestResult) -> Unit,
    ) {
        val results = mutableMapOf<ExtensionTestKind, TestResult>()

        for (test in tests) {
            val kind = test.kind

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

            val outcome = try {
                withTimeout(kind.timeoutMs) { test.run(context) }
            } catch (te: TimeoutCancellationException) {
                Logger.w(TAG) { "${context.target.name} / ${kind.label}: timed out after ${kind.timeoutMs}ms" }
                TestOutcome.fail("Timed out after ${kind.timeoutMs / 1000}s")
            } catch (ce: CancellationException) {
                // The user stopped the run (or the screen died) — propagate so
                // the coroutine unwinds cleanly; no terminal result is emitted.
                throw ce
            } catch (t: Throwable) {
                // Plugin bytecode can throw ANYTHING (the bridge guard lesson)
                // — the class name is the most informative short form.
                Logger.e(TAG, t) { "${context.target.name} / ${kind.label}: failed" }
                TestOutcome.fail("${t::class.java.simpleName}: ${t.message ?: "unknown error"}")
            }

            val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
            val result = if (outcome.skippedReason != null) {
                TestResult(kind, TestStatus.SKIPPED, durationMs, outcome.skippedReason ?: "Skipped")
            } else if (outcome.passed) {
                TestResult(kind, TestStatus.PASSED, durationMs, outcome.message, outcome.detail)
            } else {
                TestResult(kind, TestStatus.FAILED, durationMs, outcome.message, outcome.detail)
            }
            results[kind] = result
            onResult(kind, result)
        }
    }
}
