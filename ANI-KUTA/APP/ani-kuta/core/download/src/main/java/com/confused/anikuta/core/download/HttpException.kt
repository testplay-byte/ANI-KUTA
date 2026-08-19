package com.confused.anikuta.core.download

/**
 * HTTP-level download error (4xx / 5xx response).
 *
 * D.0.9 + REVIEW-5 M49: defined LOCALLY in `:core:download` (no `:core:source-api`
 * dependency — keeps the dep graph minimal). Thrown by [HttpDownloader] for
 * non-2xx responses so [RetryPolicy.forException] can match on `e is HttpException`
 * and decide whether to retry (5xx + 429 = retryable; 4xx = not retryable).
 *
 * Usage in [HttpDownloader.downloadNormal]:
 * ```
 * if (!response.isSuccessful) {
 *     throw HttpException(response.code, "HTTP ${response.code} for $url")
 * }
 * ```
 *
 * Usage in [RetryPolicy.forException]:
 * ```
 * fun forException(e: Throwable): RetryDecision = when (e) {
 *     is HttpException -> when {
 *         e.code in 500..599 -> Retry(afterMillis = backoff(attempt))
 *         e.code == 429 -> Retry(afterMillis = backoff(attempt))
 *         else -> DoNotRetry  // 4xx — not retryable
 *     }
 *     is java.io.IOException -> Retry(afterMillis = backoff(attempt))
 *     else -> DoNotRetry
 * }
 * ```
 */
class HttpException(
    /** The HTTP status code (e.g. 404, 500, 429). */
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : DownloadException(message, cause)
