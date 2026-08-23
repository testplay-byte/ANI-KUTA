package com.confused.anikuta.core.playbackcache

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The local HTTP cache proxy between MPV and the upstream video URL.
 *
 * - Binds 127.0.0.1 ONLY (never wildcard — the cache serves app-private video
 *   bytes; the HttpServer.kt precedent binds 0.0.0.0 and must NOT be copied).
 * - Serves HTTP Range requests (MPV seeks by issuing ranged requests):
 *   cached sub-ranges come from the .bin file, gaps are fetched from upstream
 *   (with the stored upstream headers) and tee'd into the file while streaming.
 * - Fully-cached entries serve from disk only — the "instant replay" fast path.
 * - FAIL-OPEN (hard requirement, PLAN.md A.5.1): a pre-body internal error
 *   responds 302 Found → upstream URL. ffmpeg follows redirects and re-sends
 *   the globally-set MPV headers (D-199), so playback continues exactly as it
 *   would without the cache. Mid-stream failures just close the connection.
 *   The cache must NEVER permanently break playback.
 */
class CacheProxyServer(
    private val manager: PlaybackCacheManager,
) : NanoHTTPD("127.0.0.1", 0) {

    /** The bound ephemeral port (valid after [start]). */
    val port: Int get() = listeningPort

    fun baseUrl(): String = "http://127.0.0.1:$port"

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        if (method != Method.GET && method != Method.HEAD) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }
        // Path shape: /v/<cacheKey>
        val key = session.uri.removePrefix("/v/").substringBefore('/')
        if (key.isBlank()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }
        val rangeHeader = session.headers["range"]
        return try {
            manager.serve(this, key, rangeHeader, headOnly = method == Method.HEAD)
        } catch (e: Exception) {
            // FAIL-OPEN: pre-body internal error → redirect to the upstream URL.
            // Whatever we know about the upstream comes from the manager; if even
            // that fails, fall through to 404 (nothing better exists).
            val upstream = runCatching { manager.upstreamUrlFor(key) }.getOrNull()
            if (upstream != null) {
                redirectResponse(upstream)
            } else {
                notFoundResponse()
            }
        }
    }

    // ── Response builders ──

    /** A streaming 200/206 response whose body lazily reads disk slices + upstream gaps. */
    fun streamingResponse(
        status: Response.Status,
        contentType: String,
        stream: InputStream,
        length: Long,
        contentRange: String?,
    ): Response {
        val response = newFixedLengthResponse(status, contentType, stream, length)
        response.addHeader("Accept-Ranges", "bytes")
        if (contentRange != null) response.addHeader("Content-Range", contentRange)
        return response
    }

    /** A HEAD response: same headers as GET, zero body bytes but an advertised Content-Length. */
    fun headResponse(
        status: Response.Status,
        contentType: String,
        advertisedLength: Long,
        contentRange: String?,
    ): Response {
        // NanoHTTPD 2.3.1 has no (Status, String, String, long) overload — the
        // InputStream variant with an empty body + totalBytes = advertisedLength
        // gives Content-Length = advertisedLength with zero body bytes (HEAD semantics).
        val response = newFixedLengthResponse(
            status,
            contentType,
            ByteArrayInputStream(ByteArray(0)),
            advertisedLength,
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (contentRange != null) response.addHeader("Content-Range", contentRange)
        return response
    }

    /** Redirect-to-upstream fail-open response. */
    fun redirectResponse(upstreamUrl: String): Response {
        val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_PLAINTEXT, "")
        response.addHeader("Location", upstreamUrl)
        return response
    }

    fun notFoundResponse(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")

    fun rangeNotSatisfiableResponse(): Response =
        newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")

    companion object {
        private const val TAG = "Anikuta:Core:PlaybackCache"
    }
}

/**
 * A composite InputStream that serves a requested span in order, alternating
 * between cached disk slices and upstream gap fetches. Upstream bytes are
 * tee'd into the cache file (positional writes — thread-safe) as they stream
 * through. Opening the next source is LAZY (happens on first read of that
 * part) so NanoHTTPD's worker thread does the blocking IO while streaming.
 *
 * The whole thing is fail-safe at the read level: an upstream IOException
 * propagates out of read() → NanoHTTPD closes the client connection → MPV
 * reconnects (and hits the pre-body fail-open path if the entry is broken).
 */
internal class SpanInputStream(
    private val parts: List<SpanPart>,
    private val openDisk: (Long, Long) -> InputStream,
    private val openUpstream: (Long, Long) -> InputStream,
) : InputStream() {

    private var partIndex = 0
    private var current: InputStream? = null
    private var remainingInPart = 0L

    override fun read(): Int {
        val buf = ByteArray(1)
        val n = read(buf, 0, 1)
        return if (n <= 0) -1 else buf[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len <= 0) return 0
        var cur = current
        if (cur == null || remainingInPart <= 0L) {
            closeCurrent()
            if (partIndex >= parts.size) return -1
            val part = parts[partIndex++]
            remainingInPart = part.length
            cur = if (part.cached) openDisk(part.start, part.endInclusive)
            else openUpstream(part.start, part.endInclusive)
            current = cur
        }
        val toRead = minOf(len.toLong(), remainingInPart).toInt()
        val n = cur.read(b, off, toRead)
        if (n <= 0) {
            // Source ended before the part was fully consumed — treat as EOF of
            // the whole span (upstream abort / disk truncation).
            return -1
        }
        remainingInPart -= n
        return n
    }

    override fun close() {
        runCatching { current?.close() }
        current = null
    }

    private fun closeCurrent() {
        runCatching { current?.close() }
        current = null
    }
}

/** Tracks active stream count per cache key (eviction safety). */
internal class ActiveCounter {
    private val counts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    fun increment(key: String): Int = counts.getOrPut(key) { AtomicInteger() }.incrementAndGet()

    fun decrement(key: String): Int =
        counts.getOrPut(key) { AtomicInteger() }.decrementAndGet()

    fun activeCount(key: String): Int = counts[key]?.get() ?: 0
}
