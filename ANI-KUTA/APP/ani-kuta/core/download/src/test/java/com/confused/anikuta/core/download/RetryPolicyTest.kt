package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * D-758 (round 123 — the download reliability round): the retry
 * classification locks.
 *
 * The classification is what decides whether a failed attempt burns one of the
 * three attempts and lands in ERROR, or waits. It must be EXPLICIT: every
 * exception type either retries or does not, and nothing falls through by
 * accident.
 */
class RetryPolicyTest {

    private val policy = RetryPolicy()

    // ── Retryable ────────────────────────────────────────────────────────────

    @Test
    fun `a server error is retryable`() {
        assertTrue(policy.shouldRetry(HttpException(500, "server error"), currentAttempt = 1))
        assertTrue(policy.shouldRetry(HttpException(503, "unavailable"), currentAttempt = 1))
    }

    @Test
    fun `a rate limit is retryable`() {
        assertTrue(policy.shouldRetry(HttpException(429, "too many requests"), currentAttempt = 1))
    }

    @Test
    fun `an io exception is retryable`() {
        // The network blip / connection reset — the classic transient failure.
        assertTrue(policy.shouldRetry(IOException("connection reset"), currentAttempt = 1))
    }

    // ── NOT retryable ────────────────────────────────────────────────────────

    @Test
    fun `a client error is not retryable`() {
        // 4xx will not fix itself: a 403 stays a 403.
        assertFalse(policy.shouldRetry(HttpException(403, "forbidden"), currentAttempt = 1))
        assertFalse(policy.shouldRetry(HttpException(404, "not found"), currentAttempt = 1))
    }

    @Test
    fun `a validation failure is not retryable`() {
        assertFalse(policy.shouldRetry(DownloadException("empty file"), currentAttempt = 1))
    }

    @Test
    fun `an unknown exception is not retryable`() {
        // Unknown cause -> surface it instead of burning attempts.
        assertFalse(policy.shouldRetry(IllegalStateException("who knows"), currentAttempt = 1))
    }

    @Test
    fun `a cancellation is never retryable`() {
        // The user paused or cancelled — never a retry.
        assertFalse(
            policy.shouldRetry(kotlinx.coroutines.CancellationException("cancelled"), currentAttempt = 1),
        )
    }

    // ── The attempt budget ───────────────────────────────────────────────────

    @Test
    fun `the last attempt does not retry`() {
        assertFalse(policy.shouldRetry(IOException("boom"), currentAttempt = 3))
        assertTrue(policy.shouldRetry(IOException("boom"), currentAttempt = 2))
    }

    @Test
    fun `the default budget is three attempts`() {
        assertEquals(3, RetryPolicy.DEFAULT_MAX_ATTEMPTS)
    }

    // ── The backoff ──────────────────────────────────────────────────────────

    @Test
    fun `the backoff grows exponentially and is capped`() {
        assertEquals(5_000L, policy.backoffMillis(1))
        assertEquals(10_000L, policy.backoffMillis(2))
        assertEquals(20_000L, policy.backoffMillis(3))
        assertEquals(60_000L, policy.backoffMillis(10))
    }
}
