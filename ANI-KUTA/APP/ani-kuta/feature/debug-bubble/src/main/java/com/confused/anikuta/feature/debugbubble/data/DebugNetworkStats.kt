package com.confused.anikuta.feature.debugbubble.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * OkHttp interceptor that records network stats for the debug bubble's Network
 * tab (Phase DB-5).
 *
 * Counts: total requests, total bytes (response body), status-code histogram
 * (2xx/3xx/4xx/5xx/network-errors), + a capped (50) deque of recent requests.
 *
 * O(1) per request (atomic increments + capped synchronized deque). Registered
 * as an interceptor on both the default + download OkHttpClients in debug builds
 * (via `wrapDebugOkHttp` in `:app/src/debug/DebugInit.kt`).
 *
 * **Extension traffic caveat (D-162 I1):** extensions use a SEPARATE OkHttpClient
 * via Injekt (NetworkHelper) — the interceptor does NOT see extension HTTP calls.
 * The Network tab shows app-level traffic (AniList API, source API calls via
 * the Koin client, downloads) only.
 *
 * CORE_RULES §20: doesn't log (would recurse via Logger → appender → …).
 */
class DebugNetworkStats : Interceptor {

    private val requestCount = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val statusBuckets = IntArray(5) // 2xx / 3xx / 4xx / 5xx / network-errors
    private val recentRequests = ArrayDeque<RequestRecord>()
    private val lock = Any()
    private val maxRecent = 50

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.currentTimeMillis()
        requestCount.incrementAndGet()

        return try {
            val response = chain.proceed(request)
            val latencyMs = System.currentTimeMillis() - startMs
            val bytes = response.body?.contentLength()?.coerceAtLeast(0) ?: 0L
            totalBytes.addAndGet(bytes)
            val code = response.code
            bucketFor(code).let { idx ->
                synchronized(lock) { statusBuckets[idx]++ }
            }
            addRecent(RequestRecord(
                method = request.method,
                host = request.url.host,
                path = request.url.encodedPath,
                status = code,
                latencyMs = latencyMs,
                bytes = bytes,
                timestamp = startMs,
            ))
            response
        } catch (e: Exception) {
            errorCount.incrementAndGet()
            synchronized(lock) { statusBuckets[4]++ }  // network-errors bucket
            addRecent(RequestRecord(
                method = request.method,
                host = request.url.host,
                path = request.url.encodedPath,
                status = -1,  // network error
                latencyMs = System.currentTimeMillis() - startMs,
                bytes = 0L,
                timestamp = startMs,
                error = e.message,
            ))
            throw e
        }
    }

    /** Immutable snapshot for the UI. */
    fun snapshot(): NetworkSnapshot = synchronized(lock) {
        NetworkSnapshot(
            totalRequests = requestCount.get(),
            totalBytes = totalBytes.get(),
            errorCount = errorCount.get(),
            statusBuckets = statusBuckets.copyOf(),
            recentRequests = recentRequests.toList(),
        )
    }

    /** Clear all stats. */
    fun clear() {
        requestCount.set(0)
        totalBytes.set(0)
        errorCount.set(0)
        synchronized(lock) {
            for (i in statusBuckets.indices) statusBuckets[i] = 0
            recentRequests.clear()
        }
    }

    private fun bucketFor(code: Int): Int = when (code / 100) {
        2 -> 0  // 2xx
        3 -> 1  // 3xx
        4 -> 2  // 4xx
        5 -> 3  // 5xx
        else -> 4  // network-errors / unknown
    }

    private fun addRecent(record: RequestRecord) {
        synchronized(lock) {
            if (recentRequests.size >= maxRecent) recentRequests.removeFirst()
            recentRequests.addLast(record)
        }
    }

    /** A single recorded request. */
    data class RequestRecord(
        val method: String,
        val host: String,
        val path: String,
        val status: Int,  // -1 = network error
        val latencyMs: Long,
        val bytes: Long,
        val timestamp: Long,
        val error: String? = null,
    )

    /** Immutable snapshot of all stats. */
    data class NetworkSnapshot(
        val totalRequests: Long,
        val totalBytes: Long,
        val errorCount: Long,
        val statusBuckets: IntArray,  // [2xx, 3xx, 4xx, 5xx, errors]
        val recentRequests: List<RequestRecord>,
    ) {
        companion object {
            val EMPTY = NetworkSnapshot(0, 0, 0, IntArray(5), emptyList())
        }
    }
}
