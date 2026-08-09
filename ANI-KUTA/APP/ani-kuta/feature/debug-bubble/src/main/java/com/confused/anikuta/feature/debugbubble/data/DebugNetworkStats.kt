package com.confused.anikuta.feature.debugbubble.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * OkHttp interceptor that records network stats for the debug bubble's Network
 * tab (Phase DB-5).
 *
 * Counts: total requests, total bytes, status-code histogram, + categorized
 * counts (metadata / video / image / other). Categorization is by URL pattern:
 * - AniList GraphQL API → metadata
 * - Common video extensions (.mp4, .m3u8, .ts, .mkv) → video
 * - Image extensions (.jpg, .png, .webp) → image
 * - Everything else → other
 *
 * O(1) per request (atomic increments + capped synchronized deque).
 *
 * **Extension traffic caveat (D-162 I1):** extensions use a separate Injekt
 * OkHttpClient — not captured.
 */
class DebugNetworkStats : Interceptor {

    private val requestCount = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val statusBuckets = IntArray(5) // 2xx / 3xx / 4xx / 5xx / network-errors
    private val categoryCounts = IntArray(4) // metadata / video / image / other
    private val recentRequests = ArrayDeque<RequestRecord>()
    private val lock = Any()
    private val maxRecent = 50

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.currentTimeMillis()
        requestCount.incrementAndGet()
        val category = categorize(request.url.host, request.url.encodedPath)

        return try {
            val response = chain.proceed(request)
            val latencyMs = System.currentTimeMillis() - startMs
            val bytes = response.body?.contentLength()?.coerceAtLeast(0) ?: 0L
            totalBytes.addAndGet(bytes)
            val code = response.code
            synchronized(lock) {
                statusBuckets[bucketFor(code)]++
                categoryCounts[category.ordinal]++
            }
            addRecent(RequestRecord(
                method = request.method,
                host = request.url.host,
                path = request.url.encodedPath,
                status = code,
                latencyMs = latencyMs,
                bytes = bytes,
                timestamp = startMs,
                category = category,
            ))
            response
        } catch (e: Exception) {
            errorCount.incrementAndGet()
            synchronized(lock) {
                statusBuckets[4]++
                categoryCounts[category.ordinal]++
            }
            addRecent(RequestRecord(
                method = request.method,
                host = request.url.host,
                path = request.url.encodedPath,
                status = -1,
                latencyMs = System.currentTimeMillis() - startMs,
                bytes = 0L,
                timestamp = startMs,
                category = category,
                error = e.message,
            ))
            throw e
        }
    }

    fun snapshot(): NetworkSnapshot = synchronized(lock) {
        NetworkSnapshot(
            totalRequests = requestCount.get(),
            totalBytes = totalBytes.get(),
            errorCount = errorCount.get(),
            statusBuckets = statusBuckets.copyOf(),
            categoryCounts = categoryCounts.copyOf(),
            recentRequests = recentRequests.toList(),
        )
    }

    fun clear() {
        requestCount.set(0)
        totalBytes.set(0)
        errorCount.set(0)
        synchronized(lock) {
            for (i in statusBuckets.indices) statusBuckets[i] = 0
            for (i in categoryCounts.indices) categoryCounts[i] = 0
            recentRequests.clear()
        }
    }

    private fun bucketFor(code: Int): Int = when (code / 100) {
        2 -> 0
        3 -> 1
        4 -> 2
        5 -> 3
        else -> 4
    }

    private fun categorize(host: String, path: String): RequestCategory {
        val lower = (host + path).lowercase()
        return when {
            "anilist.co" in lower || "graphql" in lower -> RequestCategory.METADATA
            lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".ts") ||
                lower.endsWith(".mkv") || "video" in lower || "stream" in lower -> RequestCategory.VIDEO
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".gif") || "image" in lower ||
                "cover" in lower || "thumbnail" in lower -> RequestCategory.IMAGE
            else -> RequestCategory.OTHER
        }
    }

    private fun addRecent(record: RequestRecord) {
        synchronized(lock) {
            if (recentRequests.size >= maxRecent) recentRequests.removeFirst()
            recentRequests.addLast(record)
        }
    }

    enum class RequestCategory(val label: String) {
        METADATA("Metadata"),
        VIDEO("Video"),
        IMAGE("Image"),
        OTHER("Other"),
    }

    data class RequestRecord(
        val method: String,
        val host: String,
        val path: String,
        val status: Int,
        val latencyMs: Long,
        val bytes: Long,
        val timestamp: Long,
        val category: RequestCategory,
        val error: String? = null,
    )

    data class NetworkSnapshot(
        val totalRequests: Long,
        val totalBytes: Long,
        val errorCount: Long,
        val statusBuckets: IntArray,
        val categoryCounts: IntArray,
        val recentRequests: List<RequestRecord>,
    ) {
        companion object {
            val EMPTY = NetworkSnapshot(0, 0, 0, IntArray(5), IntArray(4), emptyList())
        }
    }
}
