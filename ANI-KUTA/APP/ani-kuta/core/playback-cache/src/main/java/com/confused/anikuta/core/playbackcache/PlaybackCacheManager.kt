package com.confused.anikuta.core.playbackcache

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The playback cache facade.
 *
 * Video Caching plan: DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md (Part A).
 *
 * Responsibilities:
 * - [playbackUrlFor]: called from WatchScreen at every MPV loadfile site. NON-suspend,
 *   fail-open (any failure → return the original URL; MPV never sees the proxy).
 * - [serve]: called from the proxy server's NanoHTTPD worker thread. Blocking IO is
 *   fine there. Range-aware serving with disk slices + upstream gap fetches (tee'd
 *   into the cache file). Pre-body errors → 302 redirect to upstream (fail-open).
 * - Eviction (LRU, size-capped, active-stream-safe), deletion, stale sweep.
 *
 * Threading model:
 * - Main/composable threads only call [playbackUrlFor] (SharedPreferences + map put + a
 *   started-server read — no blocking IO; the server is pre-started from AnikutaApp).
 * - NanoHTTPD worker threads call [serve]/[upstreamUrlFor] (synchronous SQLDelight is
 *   thread-safe; SQLite contention degrades via runCatching, never crashes).
 * - The injected [scope] runs startup maintenance (pre-start, sweep, eviction).
 */
class PlaybackCacheManager(
    private val context: Context,
    private val store: PlaybackCacheStore,
    private val preferences: PlaybackCachePreferences,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {

    /** Fresh upstream info registered at play time (one per cache key, per session). */
    private data class PlayDescriptor(
        val id: PlaybackVideoId,
        val url: String,
        val headers: String,
    )

    /** Live, in-memory state for an open entry (ranges here are the source of truth). */
    private class LiveState(val key: String, val channel: FileChannel) {
        @Volatile var ranges: List<ByteRange> = emptyList()
        @Volatile var cachedBytes: Long = 0L
        @Volatile var contentLength: Long? = null
        @Volatile var contentType: String = "video/mp4"
        @Volatile var complete: Boolean = false
        @Volatile var upstreamSupportsRanges: Boolean = true
        @Volatile var deleting: Boolean = false
        @Volatile var lastFlushAt: Long = 0L
        @Volatile var lastFlushBytes: Long = 0L
        @Volatile var lastTouchAt: Long = 0L
    }

    private val descriptors = ConcurrentHashMap<String, PlayDescriptor>()
    private val liveStates = ConcurrentHashMap<String, LiveState>()
    private val activeCounter = ActiveCounter()
    private val stateInitLock = Any()

    @Volatile private var server: CacheProxyServer? = null
    @Volatile private var lastEvictCheckAt: Long = 0L

    private val cacheDir: File
        get() = File(context.filesDir, "playback-cache").apply { mkdirs() }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API (WatchScreen + settings screen)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the URL MPV should load: the proxy URL when caching applies, or the
     * original URL otherwise. FAIL-OPEN: any failure → the original URL.
     *
     * @param id nullable — null (unknown identity, e.g. no ResolverVideo available)
     *   bypasses the cache entirely.
     */
    fun playbackUrlFor(id: PlaybackVideoId?, upstreamUrl: String, headers: String): String {
        if (id == null) return upstreamUrl
        return try {
            if (!preferences.cacheEnabled) return upstreamUrl
            if (!upstreamUrl.startsWith("http://") && !upstreamUrl.startsWith("https://")) {
                return upstreamUrl
            }
            // Disk-space guard: if free space is low, don't grow the cache.
            if (cacheDir.usableSpace < MIN_FREE_BYTES) {
                Logger.w(TAG) { "Low disk space (${cacheDir.usableSpace / MB} MB free) — bypassing cache" }
                return upstreamUrl
            }
            descriptors[id.cacheKey] = PlayDescriptor(id, upstreamUrl, headers)
            ensureServerStarted()
            val s = server ?: return upstreamUrl
            "${s.baseUrl()}/v/${id.cacheKey}"
        } catch (e: Exception) {
            Logger.e(TAG, e) { "playbackUrlFor failed — bypassing cache (fail-open)" }
            upstreamUrl
        }
    }

    /** Upstream URL for a key (fail-open redirect path). Null when unknown. */
    fun upstreamUrlFor(key: String): String? =
        descriptors[key]?.url ?: store.getSync(key)?.upstreamUrl

    /** Reactive list for the settings screen. */
    fun observeEntries() = store.observeEntries()

    /** Reactive total cached size for the settings screen. */
    fun observeTotalBytes() = store.observeTotalBytes()

    /** Remove one entry (immediate when inactive; deferred to last stream close when active). */
    fun removeEntry(cacheKey: String) {
        scope.launch(Dispatchers.IO) {
            removeEntryInternal(cacheKey)
        }
    }

    /** Remove everything. */
    fun clearAll() {
        scope.launch(Dispatchers.IO) {
            store.listForEvictionSync().forEach { removeEntryInternal(it.cacheKey) }
            Logger.i(TAG) { "Cache cleared" }
        }
    }

    /**
     * Startup maintenance: pre-start the proxy server (avoids a bind() on the main
     * thread at first play), sweep stale entries, evict over-limit entries.
     * Called from AnikutaApp on a background scope.
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        runCatching { ensureServerStarted() }
            .onFailure { Logger.e(TAG, it) { "Failed to start cache proxy server — cache disabled until next app start (fail-open)" } }
        runCatching { sweepStale() }
            .onFailure { Logger.e(TAG, it) { "Stale sweep failed" } }
        runCatching { evictIfNeededInternal() }
            .onFailure { Logger.e(TAG, it) { "Startup eviction failed" } }
        server?.let { Logger.i(TAG) { "Playback cache ready on ${it.baseUrl()} (${store.totalBytesSync() / MB} MB cached)" } }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Proxy serving (NanoHTTPD worker thread)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Serves one proxy request. Returns a NanoHTTPD Response (streaming).
     * FAIL-OPEN: pre-body internal errors return a 302 redirect to the upstream URL —
     * ffmpeg follows redirects and re-sends the globally-set MPV headers (D-199),
     * so playback continues exactly as without the cache.
     */
    internal fun serve(
        server: CacheProxyServer,
        key: String,
        rangeHeader: String?,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val descriptor = descriptors[key]
            ?: return server.notFoundResponse() // proxy URL only exists for registered plays

        val state = acquireState(key, descriptor)
        if (state.deleting) return server.redirectResponse(descriptor.url)

        // Determine total length (probe once if unknown; derive from file when complete).
        ensureTotalKnown(state, descriptor)
        val total = state.contentLength
        if (total == null || total <= 0L) {
            // Unknown-length upstream (chunked, no Content-Length): can't serve fixed-
            // length ranges reliably — redirect and let MPV stream directly.
            Logger.w(TAG) { "serve[$key]: unknown content length — redirecting upstream" }
            return server.redirectResponse(descriptor.url)
        }

        // Parse the client Range header.
        val parsed = parseRange(rangeHeader, total)
        val start = parsed.first
        val end = parsed.second
        if (start >= total) {
            return server.rangeNotSatisfiableResponse()
        }

        val parts = CacheRanges.splitSpan(state.ranges, start, end)
        val length = end - start + 1
        val contentRange = if (rangeHeader != null) "bytes $start-$end/$total" else null
        val status = if (rangeHeader != null) {
            fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT
        } else {
            fi.iki.elonen.NanoHTTPD.Response.Status.OK
        }

        if (headOnly) {
            // HEAD: same headers, zero body. (ffmpeg rarely HEADs media servers.)
            return server.headResponse(status, state.contentType, length, contentRange)
        }

        activeCounter.increment(key)
        val stream = SpanInputStream(
            parts = parts,
            openDisk = { s, e -> diskSlice(state, s, e) },
            openUpstream = { s, e -> upstreamSlice(state, descriptor, s, e) },
        )
        // Bookkeeping when NanoHTTPD closes the stream (client disconnect or completion).
        val wrapped = object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                runCatching { stream.close() }
                flushIfNeeded(state, force = true)
                val active = activeCounter.decrement(key)
                if (active <= 0 && state.deleting) {
                    runCatching { deleteEntryNow(state) }
                }
            }
        }
        return server.streamingResponse(status, state.contentType, wrapped, length, contentRange)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureServerStarted() {
        if (server != null && server?.isAlive == true) return
        synchronized(this) {
            if (server != null && server?.isAlive == true) return
            val s = CacheProxyServer(manager = this)
            // NanoHTTPD 2.3.1: no-arg start() uses default socket read timeout (5s)
            // + 10MB max body — we never read request bodies, so both are fine.
            s.start()
            server = s
            Logger.i(TAG) { "Cache proxy started on ${s.baseUrl()}" }
        }
    }

    /**
     * Creates or refreshes the live state for a key: DB row (insert if new /
     * refresh upstream URL+headers if existing — they change every resolve),
     * stale-file verification, and the open file channel.
     */
    private fun acquireState(key: String, descriptor: PlayDescriptor): LiveState {
        liveStates[key]?.let { existing ->
            // Refresh upstream + LRU touch — throttled (once per 60s per entry):
            // the descriptor is registered at play time, so per-request refreshes
            // (each MPV seek opens a new request) only need to update the LRU stamp.
            val now = System.currentTimeMillis()
            if (now - existing.lastTouchAt > TOUCH_INTERVAL_MS) {
                existing.lastTouchAt = now
                runCatching { store.updateUpstreamSync(key, descriptor.url, descriptor.headers) }
            }
            return existing
        }
        synchronized(stateInitLock) {
            liveStates[key]?.let { return it }
            val file = File(cacheDir, "$key.bin")
            var entry = runCatching { store.getSync(key) }.getOrNull()
            if (entry == null) {
                entry = PlaybackCacheStore.Entry(
                    cacheKey = key,
                    mainId = descriptor.id.mainId,
                    animeTitle = descriptor.id.animeTitle,
                    episodeNumber = descriptor.id.episodeNumber.toDouble(),
                    episodeTitle = descriptor.id.episodeTitle,
                    sourceId = descriptor.id.sourceId,
                    serverKey = descriptor.id.serverKey,
                    quality = descriptor.id.quality,
                    contentType = "video/mp4",
                    upstreamUrl = descriptor.url,
                    upstreamHeaders = descriptor.headers,
                    contentLength = null,
                    cachedBytes = 0L,
                    cachedRanges = emptyList(),
                    complete = false,
                    createdAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis(),
                )
                runCatching { store.insertSync(entry) }
            } else {
                // Refresh the upstream URL/headers (volatile across resolves) + LRU touch.
                runCatching { store.updateUpstreamSync(key, descriptor.url, descriptor.headers) }
            }

            // ── Stale-file verification (PLAN.md A.5 step 2) ──
            var ranges = entry.cachedRanges
            var complete = entry.complete
            var contentLength = entry.contentLength
            if (!file.exists()) {
                if (ranges.isNotEmpty() || complete) {
                    Logger.w(TAG) { "serve[$key]: cache file missing — resetting entry" }
                    ranges = emptyList()
                    complete = false
                }
            } else {
                val fileLen = file.length()
                val clamped = ranges.mapNotNull { r ->
                    when {
                        r.start >= fileLen -> null
                        r.endInclusive >= fileLen -> ByteRange(r.start, fileLen - 1)
                        else -> r
                    }
                }
                if (clamped != ranges) {
                    Logger.w(TAG) { "serve[$key]: clamping ranges to file size ($fileLen)" }
                    ranges = clamped
                }
                if (complete && contentLength != null && fileLen != contentLength) {
                    Logger.w(TAG) { "serve[$key]: complete but file size ≠ content length — resetting" }
                    ranges = emptyList()
                    complete = false
                }
            }

            val channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
            val state = LiveState(key, channel)
            state.ranges = CacheRanges.merge(ranges)
            state.cachedBytes = CacheRanges.totalBytes(state.ranges)
            state.contentLength = contentLength
            state.contentType = entry.contentType.ifBlank { "video/mp4" }
            state.complete = complete
            // A complete entry with unknown length: the file IS the whole content.
            if (state.complete && state.contentLength == null && file.exists() && file.length() > 0) {
                state.contentLength = file.length()
            }
            liveStates[key] = state
            return state
        }
    }

    /**
     * Determines the total content length once (Range: bytes=0-0 probe — HEAD is 405'd
     * by many CDNs). Also captures Content-Type + whether upstream honors Range.
     */
    private fun ensureTotalKnown(state: LiveState, descriptor: PlayDescriptor) {
        if (state.contentLength != null || state.complete) return
        synchronized(state) {
            if (state.contentLength != null || state.complete) return
            runCatching {
                val probe = upstreamRequest(descriptor, rangeStart = 0L, rangeEnd = 0L, includeRange = true)
                client.newCall(probe).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Logger.w(TAG) { "probe[${state.key}]: upstream ${resp.code} — leaving length unknown" }
                        return
                    }
                    resp.header("Content-Type")?.let { if (it.isNotBlank()) state.contentType = it }
                    if (resp.code == 206) {
                        state.upstreamSupportsRanges = true
                        parseContentRangeTotal(resp.header("Content-Range"))?.let { state.contentLength = it }
                    } else {
                        // 200: Range ignored. Total from Content-Length if present.
                        state.upstreamSupportsRanges = false
                        resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }?.let { state.contentLength = it }
                    }
                }
            }.onFailure {
                Logger.w(TAG) { "probe[${state.key}] failed: ${it.message}" }
            }
        }
    }

    /** A bounded disk slice of the cache file. */
    private fun diskSlice(state: LiveState, start: Long, endInclusive: Long): InputStream =
        object : InputStream() {
            private val buf = ByteBuffer.allocate(DISK_BUFFER_BYTES)
            private var pos = start
            private var remaining = endInclusive - start + 1

            override fun read(): Int {
                val n = read(ByteArray(1), 0, 1)
                return if (n <= 0) -1 else buf.get(0).toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0 || len <= 0) return -1
                buf.clear()
                val toRead = minOf(len.toLong(), remaining, DISK_BUFFER_BYTES.toLong()).toInt()
                val n = state.channel.read(buf, pos)
                if (n <= 0) return -1
                buf.flip()
                buf.get(b, off, n)
                pos += n
                remaining -= n
                return n
            }
        }

    /**
     * An upstream gap slice: fetches [start, endInclusive] from upstream (with the
     * stored headers + Range) and tee's every byte into the cache file while it
     * streams through. Handles Range-ignoring upstreams (200) by read-and-discard.
     */
    private fun upstreamSlice(state: LiveState, descriptor: PlayDescriptor, start: Long, endInclusive: Long): InputStream {
        val openEnded = endInclusive >= Long.MAX_VALUE - 1
        val request = upstreamRequest(
            descriptor,
            rangeStart = start,
            rangeEnd = if (openEnded) null else endInclusive,
            includeRange = state.upstreamSupportsRanges,
        )
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("upstream ${response.code} for range $start-$endInclusive")
        }
        val body = response.body.byteStream()
        var skip = 0L
        var serveRemaining = endInclusive - start + 1
        if (response.code == 206) {
            // Range honored: verify the upstream total matches what we recorded.
            val total = parseContentRangeTotal(response.header("Content-Range"))
            if (total != null && state.contentLength != null && total != state.contentLength) {
                response.close()
                // Upstream content changed (re-upload). Reset + fail this stream;
                // the next request re-probes and re-caches from scratch.
                Logger.w(TAG) { "upstream[${state.key}]: content length changed ($total ≠ ${state.contentLength}) — resetting entry" }
                resetEntry(state)
                throw IOException("upstream content length changed")
            }
            // Some servers clamp the range start — honor what they actually sent.
            parseContentRangeStart(response.header("Content-Range"))?.let { actualStart ->
                if (actualStart < start) skip = start - actualStart
            }
        } else {
            // 200: range ignored — full body from 0; skip to our start.
            skip = start
            response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }?.let { len ->
                if (state.contentLength == null && !openEnded && start + serveRemaining == len) {
                    state.contentLength = len
                }
            }
        }
        return TeeInputStream(state, body, skip, start, serveRemaining)
    }

    /**
     * Reads the upstream body, optionally skipping a prefix, tee'ing bytes into the
     * cache file at [teeBase + forwarded] while forwarding to the client.
     */
    private inner class TeeInputStream(
        private val state: LiveState,
        private val source: InputStream,
        skipBytes: Long,
        private val teeBase: Long,
        private var serveRemaining: Long,
    ) : InputStream() {

        private val buf = ByteArray(UPSTREAM_BUFFER_BYTES)
        /** Stateful skip counter — the prefix must be consumed exactly once across reads. */
        private var toSkip = skipBytes
        private var forwarded = 0L

        override fun read(): Int {
            val n = read(buf, 0, 1)
            return if (n <= 0) -1 else buf[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            // Skip the prefix ONCE (read loops — InputStream.skip may skip short).
            while (toSkip > 0) {
                val n = source.read(buf, 0, minOf(toSkip, buf.size.toLong()).toInt())
                if (n <= 0) return -1
                toSkip -= n
            }
            if (serveRemaining <= 0 || len <= 0) return -1
            val want = minOf(len.toLong(), serveRemaining, buf.size.toLong()).toInt()
            val n = source.read(buf, 0, want)
            if (n <= 0) return -1
            // Tee into the cache file (positional write — thread-safe).
            runCatching {
                state.channel.write(ByteBuffer.wrap(buf, 0, n), teeBase + forwarded)
            }.onFailure { Logger.w(TAG) { "tee[${state.key}] write failed: ${it.message}" } }
            forwarded += n
            registerCached(state, teeBase, teeBase + forwarded - 1)
            serveRemaining -= n
            System.arraycopy(buf, 0, b, off, n)
            return n
        }

        override fun close() {
            runCatching { source.close() }
        }
    }

    /** Adds [from, to] to the live ranges (synchronized per state) + throttled DB flush. */
    private fun registerCached(state: LiveState, from: Long, to: Long) {
        if (to < from) return
        val shouldFlush = synchronized(state) {
            state.ranges = CacheRanges.merge(state.ranges + ByteRange(from, to))
            state.cachedBytes = CacheRanges.totalBytes(state.ranges)
            // Completeness: full coverage of [0, total-1].
            state.complete = state.contentLength != null && run {
                val total = state.contentLength!!
                state.ranges.size == 1 && state.ranges.first().start == 0L && state.ranges.first().endInclusive >= total - 1
            }
            val now = System.currentTimeMillis()
            val sinceFlush = now - state.lastFlushAt
            val bytesSinceFlush = state.cachedBytes - state.lastFlushBytes
            if (sinceFlush >= FLUSH_INTERVAL_MS || bytesSinceFlush >= FLUSH_DELTA_BYTES || state.complete) {
                state.lastFlushAt = now
                state.lastFlushBytes = state.cachedBytes
                true
            } else false
        }
        if (shouldFlush) {
            runCatching {
                store.updateProgressSync(
                    cacheKey = state.key,
                    cachedBytes = state.cachedBytes,
                    ranges = state.ranges,
                    complete = state.complete,
                    contentLength = state.contentLength,
                    contentType = state.contentType,
                )
            }
            maybeEvict()
        }
    }

    /** Forces a DB flush (stream close path). */
    private fun flushIfNeeded(state: LiveState, force: Boolean) {
        synchronized(state) {
            if (!force && System.currentTimeMillis() - state.lastFlushAt < FLUSH_INTERVAL_MS) return
            state.lastFlushAt = System.currentTimeMillis()
            state.lastFlushBytes = state.cachedBytes
        }
        runCatching {
            store.updateProgressSync(
                cacheKey = state.key,
                cachedBytes = state.cachedBytes,
                ranges = state.ranges,
                complete = state.complete,
                contentLength = state.contentLength,
                contentType = state.contentType,
            )
        }
    }

    private fun resetEntry(state: LiveState) {
        synchronized(state) {
            state.ranges = emptyList()
            state.cachedBytes = 0L
            state.contentLength = null
            state.complete = false
        }
        runCatching {
            store.updateProgressSync(state.key, 0L, emptyList(), false, null, state.contentType)
        }
    }

    private fun upstreamRequest(
        descriptor: PlayDescriptor,
        rangeStart: Long,
        rangeEnd: Long?,
        includeRange: Boolean,
    ): Request {
        val builder = Request.Builder().url(descriptor.url)
        MpvHeaderParser.parse(descriptor.headers).forEach { (k, v) -> builder.header(k, v) }
        // Byte-offset integrity: never let OkHttp negotiate transparent gzip on
        // range responses (decompressed bytes ≠ compressed offsets → silent cache
        // corruption). Also mirrors the D-207 localhost identity precedent.
        builder.header("Accept-Encoding", "identity")
        if (includeRange) {
            builder.header("Range", if (rangeEnd != null) "bytes=$rangeStart-$rangeEnd" else "bytes=$rangeStart-")
        }
        return builder.build()
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        // "bytes 0-0/1234567" → 1234567 ("*" → null)
        if (header.isNullOrBlank()) return null
        val idx = header.lastIndexOf('/')
        if (idx < 0) return null
        return header.substring(idx + 1).trim().toLongOrNull()
    }

    private fun parseContentRangeStart(header: String?): Long? {
        // "bytes 123-456/..." → 123
        if (header.isNullOrBlank()) return null
        val rangePart = header.substringAfter(' ', "").substringBefore('/')
        val idx = rangePart.indexOf('-')
        if (idx <= 0) return null
        return rangePart.substring(0, idx).trim().toLongOrNull()
    }

    /**
     * Parses a client Range header against a known total.
     * Returns (start, endInclusive). Multi-range → full span (ignored, serve 200).
     */
    private fun parseRange(rangeHeader: String?, total: Long): Pair<Long, Long> {
        if (rangeHeader.isNullOrBlank()) return 0L to total - 1
        val value = rangeHeader.trim().removePrefix("bytes=").trim()
        if (value.contains(',')) return 0L to total - 1 // multi-range: ignore
        val idx = value.indexOf('-')
        return when {
            idx <= 0 -> {
                // Suffix range: "bytes=-N" → last N bytes.
                val n = value.substring(1).trim().toLongOrNull() ?: return 0L to total - 1
                maxOf(0L, total - n) to total - 1
            }
            idx == value.length - 1 -> {
                // Open range: "bytes=N-"
                val start = value.substring(0, idx).trim().toLongOrNull() ?: return 0L to total - 1
                start to total - 1
            }
            else -> {
                val start = value.substring(0, idx).trim().toLongOrNull() ?: return 0L to total - 1
                val end = value.substring(idx + 1).trim().toLongOrNull() ?: (total - 1)
                start to minOf(end, total - 1)
            }
        }
    }

    // ── Deletion + eviction ──

    private fun removeEntryInternal(cacheKey: String) {
        val state = liveStates[cacheKey]
        if (state != null && activeCounter.activeCount(cacheKey) > 0) {
            // Active stream: defer deletion to the last stream close.
            state.deleting = true
            Logger.i(TAG) { "delete[$cacheKey]: deferred (stream active)" }
            return
        }
        if (state != null) deleteEntryNow(state) else deleteOrphan(cacheKey)
    }

    private fun deleteEntryNow(state: LiveState) {
        state.deleting = true
        liveStates.remove(state.key)
        descriptors.remove(state.key)
        runCatching { state.channel.close() }
        val file = File(cacheDir, "${state.key}.bin")
        runCatching { file.delete() }
        runCatching { store.deleteSync(state.key) }
        Logger.i(TAG) { "delete[${state.key}]: removed" }
    }

    private fun deleteOrphan(cacheKey: String) {
        runCatching { File(cacheDir, "$cacheKey.bin").delete() }
        runCatching { store.deleteSync(cacheKey) }
    }

    /** Eviction check throttled (called from proxy threads after flushes). */
    private fun maybeEvict() {
        val now = System.currentTimeMillis()
        if (now - lastEvictCheckAt < EVICT_CHECK_INTERVAL_MS) return
        lastEvictCheckAt = now
        runCatching { evictIfNeededInternal() }
            .onFailure { Logger.w(TAG) { "eviction check failed: ${it.message}" } }
    }

    private fun evictIfNeededInternal() {
        val limit = preferences.maxCacheBytes
        var total = store.totalBytesSync()
        if (total <= limit) return
        val candidates = store.listForEvictionSync() // oldest first
        for (entry in candidates) {
            if (total <= limit) break
            val state = liveStates[entry.cacheKey]
            if (state != null && activeCounter.activeCount(entry.cacheKey) > 0) continue
            if (state != null) deleteEntryNow(state) else deleteOrphan(entry.cacheKey)
            total -= entry.cachedBytes
            Logger.i(TAG) { "evicted ${entry.cacheKey} (${entry.cachedBytes / MB} MB) — total now ${total / MB} MB / limit ${limit / MB} MB" }
        }
    }

    /** Startup sweep: drop entries whose files vanished; fix size mismatches. */
    private fun sweepStale() {
        val entries = store.listForEvictionSync()
        var removed = 0
        for (entry in entries) {
            val file = File(cacheDir, "${entry.cacheKey}.bin")
            if (!file.exists()) {
                deleteOrphan(entry.cacheKey)
                removed++
                continue
            }
            val fileLen = file.length()
            val clamped = entry.cachedRanges.mapNotNull { r ->
                when {
                    r.start >= fileLen -> null
                    r.endInclusive >= fileLen -> ByteRange(r.start, fileLen - 1)
                    else -> r
                }
            }
            val newBytes = CacheRanges.totalBytes(CacheRanges.merge(clamped))
            val newComplete = entry.complete && entry.contentLength == fileLen
            if (clamped != entry.cachedRanges || newBytes != entry.cachedBytes || newComplete != entry.complete) {
                runCatching {
                    store.updateProgressSync(entry.cacheKey, newBytes, clamped, newComplete, entry.contentLength, entry.contentType)
                }
            }
        }
        if (removed > 0) Logger.i(TAG) { "sweepStale: removed $removed orphaned entries" }
    }

    companion object {
        private const val TAG = "Anikuta:Core:PlaybackCache"
        private const val MB = 1024L * 1024L
        private const val MIN_FREE_BYTES = 256L * MB
        private const val DISK_BUFFER_BYTES = 64 * 1024
        private const val UPSTREAM_BUFFER_BYTES = 64 * 1024
        private const val FLUSH_INTERVAL_MS = 2_000L
        private const val FLUSH_DELTA_BYTES = 4L * MB
        private const val EVICT_CHECK_INTERVAL_MS = 30_000L
        private const val TOUCH_INTERVAL_MS = 60_000L
    }
}
