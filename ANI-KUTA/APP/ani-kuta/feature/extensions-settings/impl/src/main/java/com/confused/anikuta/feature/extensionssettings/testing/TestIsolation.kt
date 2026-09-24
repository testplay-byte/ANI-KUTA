package com.confused.anikuta.feature.extensionssettings.testing

import com.confused.anikuta.core.common.Logger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The per-kind HARD ISOLATION (round 84, D-583) — the fix for the round-84
 * device report: "it gets unresponsive on some of the extensions when they
 * reach the search page testing, and after that the stop button does not
 * work" (Aniyomi only).
 *
 * THE ROOT CAUSE: every safety layer the engine had (withTimeout, job
 * cancellation) is COOPERATIVE. Some extensions override `suspend fun
 * getSearchAnime` with fully blocking code — no suspension points anywhere.
 * A coroutine wedged inside such code can never observe a timeout or a
 * cancel: the `withTimeout` deadline cannot fire (the block never reaches a
 * suspension point), `Job.cancel()` is never seen, and the awaiting engine —
 * and therefore the whole batch loop and the UI's running flag — stalls
 * forever.
 *
 * THE FIX: never let the awaiter's return depend on the tested code's
 * cooperation. Each kind's body runs on a DEDICATED single-thread daemon
 * dispatcher; the caller awaits the body's Deferred under ITS OWN short
 * poll-slice deadline — `await()` is a cancellable suspension, so the
 * caller's deadline ALWAYS fires regardless of what the body does. On a
 * timeout/skip the body is cancelled (it unwinds at its next suspension)
 * and its executor is `shutdownNow()`-ed, which interrupts the wedged
 * thread — synchronous OkHttp calls abort on interrupt.
 *
 * WHY `async`-on-a-dedicated-dispatcher (and not runBlocking on that
 * thread): runBlocking's event loop parks WITHOUT interrupt checks, so
 * `future.cancel(true)` would not reliably unwind it, and a suspending body
 * that re-enters its own single-thread dispatcher via an inner withContext
 * would self-deadlock. The async pattern has neither problem — the body
 * yields the thread at every suspension.
 *
 * THREAD HYGIENE: one executor per KIND-RUN (never a shared pool — a wedged
 * thread must not queue up the next kind), `deferred.cancel()` +
 * `shutdownNow()` in `finally` on every exit path → steady-state extra
 * threads = 0. The only residue is a daemon thread stuck in an
 * interrupt-immune call (classic JVM DNS), which self-releases at the OS
 * resolver timeout.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsTesting".
 */
object TestIsolation {

    private const val TAG = "Anikuta:Feature:ExtensionsTesting"

    /** How long one await slice waits before re-checking skip/deadline. */
    private const val SLICE_MS = 250L

    /** How the awaited kind ended. */
    sealed interface KindResult {
        /** The body returned normally with its outcome. */
        data class Done(val outcome: TestOutcome) : KindResult

        /** The wall-clock deadline fired before the body finished. */
        data object TimedOut : KindResult

        /** The user pressed Skip while this kind was running. */
        data object UserSkipped : KindResult
    }

    /**
     * Runs one kind's body under hard isolation.
     *
     * @param label used for the worker thread name + logs.
     * @param timeoutMs the kind's wall-clock budget (the ONLY clock — the
     *   engine's old cooperative withTimeout is gone).
     * @param skipSignal set by the run controller when the user skips the
     *   current target; polled every slice.
     * @param block the test body (the test's own inner withContext hops are
     *   unchanged).
     */
    suspend fun runKind(
        label: String,
        timeoutMs: Long,
        skipSignal: AtomicBoolean,
        onDispatcher: (kotlinx.coroutines.CoroutineDispatcher) -> Unit = {},
        block: suspend () -> TestOutcome,
    ): KindResult {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Anikuta-ExtTest-$label").apply { isDaemon = true }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        // Publish the kind's dedicated dispatcher to the test context — the
        // tests route their blocking work through it so the interrupt below
        // lands on the actual blocked call.
        onDispatcher(dispatcher)
        // Detached scope: the body's lifetime is tied to THIS scope, not to
        // the awaiting engine coroutine — cancelling the await (timeout /
        // skip) must not surface as a JobCancellationException the body's
        // generic catch could swallow into a bogus "failed" verdict.
        val bodyScope = CoroutineScope(SupervisorJob() + dispatcher)
        var deferred: Deferred<TestOutcome>? = null
        val started = System.nanoTime()
        try {
            deferred = bodyScope.async { block() }
            val deadline = started + timeoutMs * 1_000_000L
            while (true) {
                if (skipSignal.get()) {
                    Logger.w(TAG) { "Isolation[$label]: user skip — abandoning the kind body" }
                    return KindResult.UserSkipped
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    Logger.w(TAG) { "Isolation[$label]: ${timeoutMs}ms deadline fired — abandoning the kind body" }
                    return KindResult.TimedOut
                }
                // The poll slice: await() is cancellable — THIS coroutine's
                // deadline always lands even when the body never suspends.
                val outcome = withTimeoutOrNull(SLICE_MS) { deferred.await() }
                if (outcome != null) return KindResult.Done(outcome)
            }
        } catch (ce: CancellationException) {
            // Stop / scope death — rethrow so the run unwinds (the engine's
            // contract: cancellation is never converted into a verdict).
            throw ce
        } catch (t: Throwable) {
            // The BODY threw (async rethrows the failure at await). Plugin
            // bytecode can throw ANYTHING — capture it as a failed outcome.
            Logger.e(TAG, t) { "Isolation[$label]: body threw" }
            return KindResult.Done(
                TestOutcome.fail("${t::class.java.simpleName}: ${t.message ?: "unknown error"}"),
            )
        } finally {
            // Every exit path: cancel the body, then kill the dedicated
            // thread. shutdownNow() sends the interrupt that un-wedges
            // synchronous extension I/O.
            deferred?.cancel()
            executor.shutdownNow()
        }
    }
}
