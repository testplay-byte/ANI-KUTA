package com.confused.anikuta.core.playbackcache

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The local HTTP cache proxy between MPV and the upstream video URL.
 *
 * Routes (session-2 rewrite — see PLAN.md "Session 2" addendum):
 *  - `/v/<cacheKey>`   — the entry root: the progressive video bytes (range-aware
 *     disk slices + upstream gap fetches tee'd into the .bin) OR the REWRITTEN
 *     HLS playlist when the entry is an HLS stream.
 *  - `/p/<cacheKey>/<i>` — HLS variant playlist #i (the master's variant URIs are
 *     rewritten to this route so MPV still does its own quality selection).
 *  - `/s/<cacheKey>/<i|init>` — HLS segment #i (or the EXT-X-MAP init segment),
 *     served from the per-segment cache file or fetched + cached on first touch.
 *
 * - Binds 127.0.0.1 ONLY (never wildcard — the cache serves app-private video
 *   bytes; the HttpServer.kt precedent binds 0.0.0.0 and must NOT be copied).
 * - FAIL-OPEN (hard requirement): a pre-body internal error responds 301
 *   redirect → upstream URL. ffmpeg follows redirects and re-sends the
 *   globally-set MPV headers (D-199), so playback continues exactly as it would
 *   without the cache. Mid-stream failures just close the connection.
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
        val headOnly = method == Method.HEAD
        val path = session.uri ?: "/"
        val rangeHeader = session.headers["range"]

        return try {
            when {
                path.startsWith("/v/") -> {
                    val key = path.removePrefix("/v/").substringBefore('/')
                    if (key.isBlank()) notFoundResponse()
                    else manager.serveEntryRoot(this, key, rangeHeader, headOnly)
                }
                path.startsWith("/p/") -> {
                    val rest = path.removePrefix("/p/")
                    val key = rest.substringBefore('/')
                    val variantIdx = rest.substringAfter('/').toIntOrNull()
                    if (key.isBlank() || variantIdx == null) notFoundResponse()
                    else manager.serveVariantPlaylist(this, key, variantIdx, headOnly)
                }
                path.startsWith("/s/") -> {
                    val rest = path.removePrefix("/s/")
                    val key = rest.substringBefore('/')
                    val segId = rest.substringAfter('/')
                    if (key.isBlank() || segId.isBlank()) notFoundResponse()
                    else manager.serveSegment(this, key, segId, headOnly)
                }
                else -> notFoundResponse()
            }
        } catch (e: Exception) {
            // FAIL-OPEN: pre-body internal error → redirect to the upstream URL when
            // we know it; else 404. ALWAYS logged with the cause (CORE_RULES §20 —
            // the user debugs via logcat).
            com.confused.anikuta.core.common.Logger.e(TAG, e) { "serve: internal error on $path — fail-open" }
            val upstream = runCatching { manager.upstreamUrlFor(keyOf(path)) }.getOrNull()
            if (upstream != null) redirectResponse(upstream) else notFoundResponse()
        }
    }

    private fun keyOf(path: String): String =
        path.removePrefix("/v/").removePrefix("/p/").removePrefix("/s/").substringBefore('/')

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

    /**
     * A streaming chunked response (unknown Content-Length — the passthrough path
     * still tees into the cache; completion is learned on EOF).
     */
    fun chunkedResponse(
        status: Response.Status,
        contentType: String,
        stream: InputStream,
    ): Response {
        val response = newChunkedResponse(status, contentType, stream)
        response.addHeader("Accept-Ranges", "none")
        return response
    }

    /** A small in-memory response (playlists, segments, redirects). */
    fun bytesResponse(
        status: Response.Status,
        contentType: String,
        bytes: ByteArray,
        contentRange: String? = null,
    ): Response {
        val response = newFixedLengthResponse(
            status,
            contentType,
            ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        )
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

/** Tracks active stream count per cache key (eviction safety). */
internal class ActiveCounter {
    private val counts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    fun increment(key: String): Int = counts.getOrPut(key) { AtomicInteger() }.incrementAndGet()

    fun decrement(key: String): Int =
        counts.getOrPut(key) { AtomicInteger() }.decrementAndGet()

    fun activeCount(key: String): Int = counts[key]?.get() ?: 0
}
