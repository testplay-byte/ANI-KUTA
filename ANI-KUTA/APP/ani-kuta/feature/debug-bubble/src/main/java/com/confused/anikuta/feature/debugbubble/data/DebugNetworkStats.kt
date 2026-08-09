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
    private val totalBytesReceived = AtomicLong(0)
    private val totalBytesSent = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val statusBuckets = IntArray(5)
    private val categoryCounts = IntArray(4)
    private val hostCounts = mutableMapOf<String, Int>()
    private val recentRequests = ArrayDeque<RequestRecord>()
    private val lock = Any()
    private val maxRecent = 50

    // Time-series: per-second buckets for the last 5 minutes (300 buckets).
    // Each bucket stores request count + bytes received for that second.
    private val timeSeries = ArrayDeque<TimeSeriesBucket>()
    private val maxTimeSeriesBuckets = 300  // 5 minutes at 1-second resolution

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.currentTimeMillis()
        requestCount.incrementAndGet()
        val category = categorize(request.url.host, request.url.encodedPath)
        val host = request.url.host
        // Track sent bytes (request body).
        val sentBytes = request.body?.contentLength()?.coerceAtLeast(0) ?: 0L
        totalBytesSent.addAndGet(sentBytes)

        return try {
            val response = chain.proceed(request)
            val latencyMs = System.currentTimeMillis() - startMs
            val bytes = response.body?.contentLength()?.coerceAtLeast(0) ?: 0L
            totalBytesReceived.addAndGet(bytes)
            val code = response.code
            synchronized(lock) {
                statusBuckets[bucketFor(code)]++
                categoryCounts[category.ordinal]++
                hostCounts[host] = (hostCounts[host] ?: 0) + 1
                recordTimeSeries(startMs, bytes)
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
        // Prune old time-series buckets (older than 5 minutes).
        val cutoff = System.currentTimeMillis() - 300_000
        while (timeSeries.isNotEmpty() && timeSeries.first().timestamp < cutoff) {
            timeSeries.removeFirst()
        }
        // ── Gap-fill: advance the time-series to "now" so the chart slides
        // forward even when there's no traffic.
        //
        // Without this, the deque only grows when a request arrives → with
        // zero traffic the chart is frozen. The user wants the charts to
        // "constantly move like time is going" — so we synthesize zero-
        // valued buckets for every elapsed second since the last bucket.
        advanceToNow()
        NetworkSnapshot(
            totalRequests = requestCount.get(),
            totalBytesReceived = totalBytesReceived.get(),
            totalBytesSent = totalBytesSent.get(),
            errorCount = errorCount.get(),
            statusBuckets = statusBuckets.copyOf(),
            categoryCounts = categoryCounts.copyOf(),
            hostCounts = hostCounts.toMap(),
            recentRequests = recentRequests.toList(),
            timeSeries = timeSeries.toList(),
        )
    }

    /**
     * Advance the time-series to the current second, inserting zero-valued
     * buckets for every elapsed second with no traffic. This makes the
     * charts slide forward over time even when the app is idle.
     *
     * Called from [snapshot] (which is already polled every 2 s by
     * NetworkTab). Must be called inside `synchronized(lock)`.
     *
     * If the deque is empty (no traffic yet at all), this seeds it with a
     * single "now" zero-bucket so the chart has a baseline to draw from.
     */
    private fun advanceToNow() {
        val nowSecond = System.currentTimeMillis() / 1000 * 1000
        if (timeSeries.isEmpty()) {
            // Seed a baseline zero-bucket so the chart has something to draw.
            timeSeries.addLast(TimeSeriesBucket(timestamp = nowSecond, requestCount = 0, bytesReceived = 0L))
            return
        }
        var last = timeSeries.last().timestamp
        // Cap the number of buckets we insert in one go — if the panel was
        // closed for a long time, we don't want to insert hundreds of zero-
        // buckets. The prune above already removes anything older than 5 min,
        // so `last` is at most 5 min old → at most 300 iterations.
        while (last < nowSecond) {
            last += 1000
            timeSeries.addLast(TimeSeriesBucket(timestamp = last, requestCount = 0, bytesReceived = 0L))
        }
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
    }

    fun clear() {
        requestCount.set(0)
        totalBytesReceived.set(0)
        totalBytesSent.set(0)
        errorCount.set(0)
        synchronized(lock) {
            for (i in statusBuckets.indices) statusBuckets[i] = 0
            for (i in categoryCounts.indices) categoryCounts[i] = 0
            hostCounts.clear()
            recentRequests.clear()
            timeSeries.clear()
        }
    }

    /** Record a request in the time-series (per-second bucket). */
    private fun recordTimeSeries(timestamp: Long, bytes: Long) {
        val secondBucket = timestamp / 1000 * 1000  // round down to the second
        if (timeSeries.isNotEmpty() && timeSeries.last().timestamp == secondBucket) {
            // Same second — update the existing bucket.
            val last = timeSeries.removeLast()
            timeSeries.addLast(last.copy(requestCount = last.requestCount + 1, bytesReceived = last.bytesReceived + bytes))
        } else {
            // New second — add a new bucket.
            timeSeries.addLast(TimeSeriesBucket(timestamp = secondBucket, requestCount = 1, bytesReceived = bytes))
        }
        // Prune if exceeding max.
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
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
        val totalBytesReceived: Long,
        val totalBytesSent: Long,
        val errorCount: Long,
        val statusBuckets: IntArray,
        val categoryCounts: IntArray,
        val hostCounts: Map<String, Int>,
        val recentRequests: List<RequestRecord>,
        val timeSeries: List<TimeSeriesBucket> = emptyList(),
    ) {
        val totalBytes: Long get() = totalBytesReceived + totalBytesSent

        companion object {
            val EMPTY = NetworkSnapshot(0, 0, 0, 0, IntArray(5), IntArray(4), emptyMap(), emptyList(), emptyList())
        }
    }

    /** A per-second time-series bucket for the 5-minute graphs. */
    data class TimeSeriesBucket(
        val timestamp: Long,   // epoch millis, rounded to the second
        val requestCount: Int,
        val bytesReceived: Long,
    )
}
