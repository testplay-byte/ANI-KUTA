package com.confused.anikuta.core.download

/**
 * D-151-fix: Retry policy for the outer download retry loop.
 *
 * Caps the number of download attempts per task. The inner re-resolve loop
 * (in [HttpDownloader.downloadNormal]) caps at 1 re-resolve attempt (= 2 inner
 * downloads). This outer loop multiplies that: 3 outer × 2 inner = 6 max total
 * download attempts before the task goes to ERROR.
 *
 * Retryable exceptions (per [HttpException] KDoc):
 *  - [HttpException] 5xx + 429 → retry (server error or rate-limit).
 *  - [HttpException] 4xx → do NOT retry (client error — won't fix itself).
 *  - [java.io.IOException] → retry (network blip, connection reset).
 *  - [DownloadException] (non-Http) → do NOT retry (validation failure, empty
 *    file, proxy-churn exhaustion — these won't fix themselves).
 *  - Generic [Exception] → do NOT retry (unknown cause — safer to surface).
 *
 * [CancellationException] is never retryable — it means the user paused/cancelled.
 *
 * Backoff is exponential: 5s, 10s, 20s, 40s... (capped at 60s to avoid long
 * delays that feel like the app is stuck).
 *
 * @param maxAttempts The max number of download attempts (1 = no retry, 3 = default).
 * @param baseBackoffMillis The base backoff for attempt 1 (default 5s).
 */
class RetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseBackoffMillis: Long = DEFAULT_BASE_BACKOFF_MS,
) {
    /**
     * Whether the download should be retried after [e] on attempt [currentAttempt].
     *
     * @param currentAttempt The attempt that just failed (1-based: 1 = first try).
     * @return true if another attempt should be made (currentAttempt < maxAttempts
     *   AND the exception is retryable).
     */
    fun shouldRetry(e: Throwable, currentAttempt: Int): Boolean {
        if (currentAttempt >= maxAttempts) return false
        return when (e) {
            is kotlin.coroutines.cancellation.CancellationException -> false
            is HttpException -> e.code in 500..599 || e.code == 429
            is DownloadException -> false // non-Http DownloadException = validation/empty/proxy-churn exhaustion
            is java.io.IOException -> true
            else -> false
        }
    }

    /**
     * The backoff delay before attempt [attempt] (1-based).
     * Exponential: base * 2^(attempt-1), capped at 60s.
     * - attempt 1: 5s
     * - attempt 2: 10s
     * - attempt 3: 20s
     */
    fun backoffMillis(attempt: Int): Long {
        val raw = baseBackoffMillis shl (attempt - 1)
        return minOf(raw, MAX_BACKOFF_MS)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BASE_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
