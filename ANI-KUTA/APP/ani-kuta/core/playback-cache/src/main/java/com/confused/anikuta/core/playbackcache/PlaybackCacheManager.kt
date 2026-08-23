package com.confused.anikuta.core.playbackcache

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The playback cache facade (SESSION-2 REWRITE — see PLAN.md "Session 2" addendum).
 *
 * Session-1 defects being fixed here (user report: "episode registered but not
 * cached at all"):
 *  1. **The separate probe**: a `Range: bytes=0-0` GET ran BEFORE serving. When it
 *     failed or the upstream (often an extension localhost proxy) answered 200
 *     without Content-Length, the total stayed unknown → serve() REDIRECTED to the
 *     upstream → playback worked (fail-open!) but NOTHING was cached. The probe is
 *     now REMOVED — the total is learned from the actual serving response by
 *     mirroring the client's Range header upstream.
 *  2. **Unknown-length redirect**: when the total is unknown we now serve a
 *     chunked/learn-mode passthrough that STILL TEES every byte into the cache
 *     (completion learned on EOF). Redirect is reserved for true internal errors.
 *  3. **HLS bypass**: an .m3u8 entry only proxied the tiny PLAYLIST — MPV fetched
 *     the actual segments (absolute CDN URLs) directly, so nothing real was cached.
 *     The proxy now REWRITES playlists (master variants → /p/<key>/<i>, media
 *     segments → /s/<key>/<i>) and caches every segment file under <key>.seg/.
 *
 * New session-2 features:
 *  - **Background fill**: while (and after) an episode plays, a fill job fetches
 *    the remaining gaps/segments until the entry is complete (VOD only for HLS).
 *  - **Tap-to-play**: entries carry subtitle/audio track lists (schema addition) so
 *    the settings screen can rebuild a full WatchKey that plays the exact same
 *    server/quality and resumes from watch progress.
 *
 * Threading: main threads only call [playbackUrlFor]; NanoHTTPD worker threads call
 * the serve* methods (blocking IO OK; sync SQLDelight is thread-safe); the injected
 * scope runs maintenance (startup, sweep, eviction, fills).
 *
 * Logging: every decision point logs INFO with the tag "Anikuta:Core:PlaybackCache"
 * and a short key prefix — see the session summary for the logcat filter.
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
        val subtitleTracks: String,
        val audioTracks: String,
    )

    /** Live, in-memory state for an open entry (ranges here are the source of truth). */
    private class LiveState(val key: String) {
        // ── progressive ──
        var channel: FileChannel? = null
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
        /** The player's read frontier (steers the background fill away from it). */
        @Volatile var lastReadOffset: Long = 0L
        @Volatile var lastLoggedTeeBytes: Long = 0L

        // ── HLS (populated when the playlist is parsed) ──
        @Volatile var isHls: Boolean = false
        @Volatile var hlsVariants: List<String> = emptyList()
        @Volatile var hlsSegments: List<String> = emptyList()
        /** D-247: cumulative start-time (seconds) of each segment — exact window mapping. */
        @Volatile var hlsSegmentStarts: List<Double> = emptyList()
        /** D.247: total playlist duration (seconds, sum of EXTINF); -1 = unknown. */
        @Volatile var hlsTotalDuration: Double = -1.0
        @Volatile var hlsInitUri: String? = null
        @Volatile var hlsVod: Boolean = false
        @Volatile var hlsByterange: Boolean = false
        @Volatile var segmentTotal: Int = 0
        @Volatile var segmentsCached: Int = 0
        @Volatile var segmentBytes: Long = 0L

        // ── D-247: playback progress (pushed by WatchScreen ~1 Hz) ──
        /** Current playback position in seconds; -1 = unknown (window disabled). */
        @Volatile var playbackPosSec: Float = -1f
        /** Total duration in seconds; -1 = unknown. */
        @Volatile var playbackDurSec: Float = -1f
        /** Wall-clock of the last progress push — the fill's idle-exit signal. */
        @Volatile var lastProgressAt: Long = 0L
        /** Last position that triggered a fill start (re-trigger throttle). */
        @Volatile var lastFillTriggerPos: Float = -1f
    }

    private val descriptors = ConcurrentHashMap<String, PlayDescriptor>()
    private val liveStates = ConcurrentHashMap<String, LiveState>()
    private val activeCounter = ActiveCounter()
    private val fills = ConcurrentHashMap<String, Job>()
    private val stateInitLock = Any()

    @Volatile private var server: CacheProxyServer? = null
    @Volatile private var lastEvictCheckAt: Long = 0L

    private val cacheDir: File
        get() = File(context.filesDir, "playback-cache").apply { mkdirs() }

    private fun segDir(key: String): File = File(cacheDir, "$key.seg")

    private fun binFile(key: String): File = File(cacheDir, "$key.bin")

    // ─────────────────────────────────────────────────────────────────────────
    // Public API (WatchScreen + settings screen)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the URL MPV should load: the proxy URL when caching applies, or the
     * original URL otherwise. FAIL-OPEN: any failure → the original URL.
     *
     * @param id nullable — null (unknown identity, e.g. no ResolverVideo available)
     *   bypasses the cache entirely.
     * @param subtitleTracksSerialized / audioTracksSerialized external track lists
     *   (WatchKey wire format) — stored on the entry for tap-to-play.
     */
    fun playbackUrlFor(
        id: PlaybackVideoId?,
        upstreamUrl: String,
        headers: String,
        subtitleTracksSerialized: String = "",
        audioTracksSerialized: String = "",
    ): String {
        val shortKey = id?.cacheKey?.take(8) ?: "?"
        if (id == null) {
            Logger.i(TAG) { "play[$shortKey]: no stable identity — direct playback (no caching)" }
            return upstreamUrl
        }
        return try {
            if (!preferences.cacheEnabled) {
                Logger.i(TAG) { "play[$shortKey]: cache disabled — direct playback" }
                return upstreamUrl
            }
            if (!upstreamUrl.startsWith("http://") && !upstreamUrl.startsWith("https://")) {
                Logger.i(TAG) { "play[$shortKey]: non-http scheme — direct playback" }
                return upstreamUrl
            }
            if (cacheDir.usableSpace < MIN_FREE_BYTES) {
                Logger.w(TAG) { "play[$shortKey]: low disk (${cacheDir.usableSpace / MB} MB free) — direct playback" }
                return upstreamUrl
            }
            descriptors[id.cacheKey] = PlayDescriptor(id, upstreamUrl, headers, subtitleTracksSerialized, audioTracksSerialized)
            ensureServerStarted()
            val s = server ?: return upstreamUrl
            val proxyUrl = "${s.baseUrl()}/v/${id.cacheKey}"
            Logger.i(TAG) {
                "play[$shortKey]: caching ENABLED → $proxyUrl " +
                    "(hls=${looksLikeHls(upstreamUrl)}, subTracks=${subtitleTracksSerialized.lines().size}, " +
                    "audioTracks=${audioTracksSerialized.lines().size})"
            }
            proxyUrl
        } catch (e: Exception) {
            Logger.e(TAG, e) { "play[$shortKey]: playbackUrlFor failed — direct playback (fail-open)" }
            upstreamUrl
        }
    }

    /** Upstream URL for a key (fail-open redirect path). Null when unknown. */
    fun upstreamUrlFor(key: String): String? =
        descriptors[key]?.url ?: store.getSync(key)?.upstreamUrl

    /**
     * D-247: playback progress push (called ~1 Hz from WatchScreen's position/duration
     * state). Drives the progress-window policy: the tee writes only below the window
     * ceiling (pos + AHEAD) and the background fill fetches only within [pos − BEHIND,
     * pos + AHEAD]. Cheap on the caller thread (volatile writes + a throttled fill
     * re-trigger every FILL_RETRIGGER_S seconds of movement).
     */
    fun onPlaybackProgress(cacheKey: String?, positionSec: Float, durationSec: Float) {
        if (cacheKey == null || positionSec < 0f) return
        val state = liveStates[cacheKey] ?: return
        state.playbackPosSec = positionSec
        if (durationSec > 0f) state.playbackDurSec = durationSec
        state.lastProgressAt = System.currentTimeMillis()
        if (kotlin.math.abs(positionSec - state.lastFillTriggerPos) >= FILL_RETRIGGER_S) {
            state.lastFillTriggerPos = positionSec
            descriptors[cacheKey]?.let { maybeStartFill(state, it) }
        }
    }

    /**
     * D-247: the byte ceiling for tee writes — the exclusive upper bound below which
     * streamed bytes are cached. Window-aware: pos + AHEAD mapped to bytes; when the
     * duration is still unknown (early playback) a bounded fallback applies so MPV's
     * read-ahead can never tee the whole file unbounded.
     */
    private fun teeCeilingFor(state: LiveState): Long {
        val total = state.contentLength
        val dur = state.playbackDurSec
        val pos = state.playbackPosSec
        if (total != null && total > 0 && dur > 0f && pos >= 0f) {
            val endSec = minOf(dur, pos + FILL_WINDOW_AHEAD_S)
            return (endSec / dur * total).toLong().coerceIn(1, total)
        }
        // Duration unknown: allow a bounded window past the player's read frontier.
        return state.lastReadOffset + TEE_FALLBACK_AHEAD_BYTES
    }

    /** D-247: the fill window as (startByte, endByte) inclusive; null = no window yet. */
    private fun fillWindowBytes(state: LiveState): Pair<Long, Long>? {
        val total = state.contentLength ?: return null
        if (total <= 0) return null
        val dur = state.playbackDurSec
        val pos = state.playbackPosSec
        if (dur <= 0f || pos < 0f) return null
        val startSec = maxOf(0f, pos - FILL_WINDOW_BEHIND_S)
        val endSec = minOf(dur, pos + FILL_WINDOW_AHEAD_S)
        if (endSec <= startSec) return null
        val s = (startSec / dur * total).toLong().coerceIn(0, total - 1)
        val e = (endSec / dur * total).toLong().coerceIn(0, total - 1)
        return s to e
    }

    /** D-247: the current playback position mapped to a byte offset (ordering hint). */
    private fun playbackPosByte(state: LiveState): Long? {
        val total = state.contentLength ?: return null
        val dur = state.playbackDurSec
        val pos = state.playbackPosSec
        if (total <= 0 || dur <= 0f || pos < 0f) return null
        return (pos / dur * total).toLong().coerceIn(0, total - 1)
    }

    /**
     * D-247: the fill window mapped to an HLS segment index range (exact — via EXTINF
     * cumulative start-times). Null = durations or progress unknown.
     */
    private fun hlsWindowRange(state: LiveState): IntRange? {
        val starts = state.hlsSegmentStarts
        if (starts.isEmpty()) return null
        val pos = state.playbackPosSec
        val dur = state.playbackDurSec
        if (pos < 0f || dur <= 0f) return null
        val startSec = maxOf(0.0, pos - FILL_WINDOW_BEHIND_S.toDouble())
        val endSec = minOf(dur.toDouble(), pos + FILL_WINDOW_AHEAD_S.toDouble())
        val total = state.hlsTotalDuration
        var first = -1
        var last = -1
        for (i in starts.indices) {
            val s = starts[i]
            val e = if (i + 1 < starts.size) starts[i + 1] else total
            if (e <= startSec) continue
            if (s >= endSec) break
            if (first == -1) first = i
            last = i
        }
        return if (first == -1) null else first..last
    }

    /**
     * D-246: cross-session identity recovery. After process death the in-memory
     * ResolvedVideosRegistry is EMPTY, so WatchScreen can't rebuild the cache identity
     * from a ResolverVideo — the cache was silently BYPASSED on every replay in a new
     * session (the user-reported "cached videos still load from the network"). This
     * reconstructs the identity from a PRIOR cache entry for the same content+episode+
     * source.
     *
     * SAFETY RULE (conservative — wrong-identity reuse files one video's bytes under
     * another entry = wrong-content replay corruption, PR-A F3 class):
     * reuse ONLY when there is exactly ONE prior entry for this identity AND its
     * quality label matches the requested one. Multiple entries (e.g. SUB + DUB of
     * the same quality, or different qualities) are ambiguous — the auto-pick's
     * server/audio can't be known without the registry → skip caching (direct play).
     */
    fun knownIdentityFor(
        mainId: String,
        episodeNumber: Float,
        sourceId: Long,
        quality: String,
    ): PlaybackVideoId? {
        if (mainId.isBlank()) return null
        return try {
            val priors = store.findByIdentitySync(mainId, episodeNumber.toDouble(), sourceId)
            when {
                priors.isEmpty() -> {
                    Logger.i(TAG) { "identity[${mainId.take(8)}]: no prior cache entry — no cross-session identity (direct playback)" }
                    null
                }
                priors.size == 1 && priors[0].quality == quality -> {
                    val prior = priors[0]
                    Logger.i(TAG) {
                        "identity[${mainId.take(8)}]: recovered from prior entry " +
                            "'${prior.serverKey}' (${prior.cachedBytes}B cached, complete=${prior.complete})"
                    }
                    PlaybackVideoId(
                        mainId = prior.mainId,
                        animeTitle = prior.animeTitle,
                        episodeNumber = prior.episodeNumber.toFloat(),
                        episodeTitle = prior.episodeTitle,
                        sourceId = prior.sourceId,
                        serverKey = prior.serverKey,
                        quality = prior.quality,
                    )
                }
                else -> {
                    Logger.i(TAG) {
                        "identity[${mainId.take(8)}]: ${priors.size} prior entries " +
                            "(qualities=${priors.map { it.quality }}) — ambiguous, skipping cache " +
                            "(direct playback; wrong-identity reuse could corrupt an entry)"
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "identity[${mainId.take(8)}]: lookup failed: ${e.message} — direct playback" }
            null
        }
    }

    /** Reactive list for the settings screen. */
    fun observeEntries() = store.observeEntries()

    /** Reactive total cached size for the settings screen. */
    fun observeTotalBytes() = store.observeTotalBytes()

    /** Remove one entry (immediate when inactive; deferred to last stream close when active). */
    fun removeEntry(cacheKey: String) {
        Logger.i(TAG) { "delete[${cacheKey.take(8)}]: requested from settings" }
        scope.launch(Dispatchers.IO) {
            removeEntryInternal(cacheKey)
        }
    }

    /** Remove everything. */
    fun clearAll() {
        Logger.i(TAG) { "delete: CLEAR ALL requested from settings" }
        scope.launch(Dispatchers.IO) {
            store.listForEvictionSync().forEach { removeEntryInternal(it.cacheKey) }
            Logger.i(TAG) { "delete: cache cleared" }
        }
    }

    /**
     * Startup maintenance: pre-start the proxy server (avoids a bind() on the main
     * thread at first play), sweep stale entries, evict over-limit entries.
     * Called from AnikutaApp on a background scope.
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        runCatching { ensureServerStarted() }
            .onFailure { Logger.e(TAG, it) { "start: proxy server failed — cache disabled until next app start (fail-open)" } }
        runCatching { sweepStale() }
            .onFailure { Logger.e(TAG, it) { "start: stale sweep failed" } }
        runCatching { evictIfNeededInternal() }
            .onFailure { Logger.e(TAG, it) { "start: startup eviction failed" } }
        server?.let {
            Logger.i(TAG) { "start: playback cache ready on ${it.baseUrl()} (${store.totalBytesSync() / MB} MB cached)" }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry-root serving: HLS playlist rewrite OR progressive bytes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Serves `/v/<key>`: the rewritten HLS playlist when the entry is HLS, or the
     * progressive video bytes otherwise.
     */
    internal fun serveEntryRoot(
        server: CacheProxyServer,
        key: String,
        rangeHeader: String?,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val descriptor = descriptors[key]
            ?: run {
                Logger.w(TAG) { "serve[${key.take(8)}]: no descriptor registered — 404 (stale proxy URL?)" }
                return server.notFoundResponse()
            }
        val state = acquireState(key, descriptor)
        if (state.deleting) {
            Logger.i(TAG) { "serve[${key.take(8)}]: entry deleting — redirect upstream (fail-open)" }
            return server.redirectResponse(descriptor.url)
        }

        Logger.i(TAG) {
            "serve[${key.take(8)}]: ${if (headOnly) "HEAD" else "GET"} range='${rangeHeader ?: "none"}' " +
                "cached=${state.cachedBytes}/${state.contentLength ?: "?"}B (ranges=${state.ranges.size}) " +
                "hls=${state.isHls || looksLikeHls(descriptor.url)}"
        }

        return if (looksLikeHls(descriptor.url) || state.isHls) {
            servePlaylist(server, key, descriptor, state, headOnly)
        } else {
            serveProgressive(server, key, descriptor, state, rangeHeader, headOnly)
        }
    }

    // ── Progressive path ─────────────────────────────────────────────────────

    private fun serveProgressive(
        server: CacheProxyServer,
        key: String,
        descriptor: PlayDescriptor,
        state: LiveState,
        rangeHeader: String?,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val total = state.contentLength
        return if (total != null && total > 0L) {
            serveProgressiveKnownTotal(server, key, descriptor, state, rangeHeader, total, headOnly)
        } else {
            serveProgressiveLearnMode(server, key, descriptor, state, rangeHeader, headOnly)
        }
    }

    /** Total known: precise range math — disk slices for cached parts, ranged gap fetches for the rest. */
    private fun serveProgressiveKnownTotal(
        server: CacheProxyServer,
        key: String,
        descriptor: PlayDescriptor,
        state: LiveState,
        rangeHeader: String?,
        total: Long,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val (start, end) = parseRange(rangeHeader, total)
        if (start >= total) {
            Logger.w(TAG) { "serve[${key.take(8)}]: range start $start >= total $total — 416" }
            return server.rangeNotSatisfiableResponse()
        }
        val parts = CacheRanges.splitSpan(state.ranges, start, end)
        val gapBytes = parts.filter { !it.cached }.sumOf { it.length }
        val diskBytes = parts.filter { it.cached }.sumOf { it.length }
        Logger.i(TAG) {
            "parts[${key.take(8)}]: span $start-$end of $total → " +
                "${parts.count { it.cached }} disk part(s) ($diskBytes B) + " +
                "${parts.count { !it.cached }} gap(s) ($gapBytes B to fetch)"
        }

        val status = if (rangeHeader != null) {
            fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT
        } else {
            fi.iki.elonen.NanoHTTPD.Response.Status.OK
        }
        val contentRange = if (rangeHeader != null) "bytes $start-$end/$total" else null
        val length = end - start + 1

        if (headOnly) {
            return server.headResponse(status, state.contentType, length, contentRange)
        }

        activeCounter.increment(key)
        val stream = SpanInputStream(
            parts = parts,
            openDisk = { s, e -> diskSlice(state, s, e) },
            openUpstream = { s, e -> upstreamSlice(state, descriptor, s, e, expectRange = true) },
            onBytes = { servedTotal -> state.lastReadOffset = start + servedTotal },
            onClose = {
                onStreamClosed(state, descriptor)
            },
        )
        maybeStartFill(state, descriptor)
        return server.streamingResponse(status, state.contentType, stream, length, contentRange)
    }

    /**
     * LEARN MODE (session-2 fix): the total is unknown — mirror the client's Range
     * header upstream VERBATIM and learn everything from the response (code,
     * Content-Range total, Content-Type). No separate probe, no redirect, ALWAYS
     * tee. When even the upstream has no Content-Length, serve chunked + learn the
     * total on EOF.
     */
    private fun serveProgressiveLearnMode(
        server: CacheProxyServer,
        key: String,
        descriptor: PlayDescriptor,
        state: LiveState,
        rangeHeader: String?,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val clientRange = parseRangeLenient(rangeHeader)
        val request = upstreamRequest(descriptor, clientRange?.first, clientRange?.second, sendRange = clientRange != null)
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Logger.w(TAG) { "learn[${key.take(8)}]: upstream open failed: ${e.message} — redirect (fail-open)" }
            return server.redirectResponse(descriptor.url)
        }

        // CRITICAL (CR-C bug #1): NO response.use{} here — a non-local return from
        // inside use{} closes the response (and its body) BEFORE NanoHTTPD's worker
        // reads it, serving a dead stream. On the streaming paths the TeeInputStream
        // owns closing the body; non-streaming exits close the response manually.
        if (!response.isSuccessful) {
            Logger.w(TAG) { "learn[${key.take(8)}]: upstream HTTP ${response.code} — redirect (fail-open)" }
            response.close()
            return server.redirectResponse(descriptor.url)
        }
        response.header("Content-Type")?.takeIf { it.isNotBlank() }?.let { state.contentType = it }
        if (state.isHls || looksLikeHlsContentType(state.contentType)) {
            // Content-Type-detected HLS (URL had no .m3u8): the body is a playlist —
            // switch to the HLS path. Body is fully buffered (playlists are small).
            Logger.i(TAG) { "learn[${key.take(8)}]: Content-Type says HLS — switching to playlist rewrite path" }
            val playlistBytes: ByteArray? = try {
                response.body?.byteStream()?.use { it.readBytes() }
            } finally {
                response.close()
            }
            return if (playlistBytes != null) {
                servePlaylistBytes(server, key, descriptor, state, playlistBytes, headOnly)
            } else {
                server.notFoundResponse()
            }
        }

        val isPartial = response.code == 206
        state.upstreamSupportsRanges = isPartial || clientRange == null

        val bodyStream = response.body?.byteStream()
        if (bodyStream == null) {
            Logger.w(TAG) { "learn[${key.take(8)}]: empty upstream body — redirect (fail-open)" }
            response.close()
            return server.redirectResponse(descriptor.url)
        }

        if (isPartial) {
            val crTotal = parseContentRangeTotal(response.header("Content-Range"))
            val crStart = parseContentRangeStart(response.header("Content-Range")) ?: clientRange?.first ?: 0L
            val crEnd = parseContentRangeEnd(response.header("Content-Range"))
                ?: crTotal?.minus(1)
                ?: response.body?.contentLength()?.takeIf { it > 0 }?.plus(crStart)?.minus(1)
            if (crTotal != null && crTotal > 0) {
                state.contentLength = crTotal
                Logger.i(TAG) { "learn[${key.take(8)}]: 206 → total=$crTotal (learned; ranges=${state.upstreamSupportsRanges})" }
            }
            val length = if (crEnd != null && crEnd >= crStart) crEnd - crStart + 1
            else response.body?.contentLength()?.takeIf { it > 0 } ?: -1L

            if (headOnly) {
                response.close()
                return server.headResponse(
                    fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                    state.contentType,
                    length.coerceAtLeast(0L),
                    response.header("Content-Range"),
                )
            }
            activeCounter.increment(key)
            val tee = TeeInputStream(
                state = state,
                source = bodyStream,
                skipBytes = 0L, // the body starts exactly at crStart
                teeBase = crStart,
                serveRemaining = if (length > 0) length else Long.MAX_VALUE,
                openEnded = length <= 0,
                teeLimit = teeCeilingFor(state),
            )
            val wrapped = wrapStream(state, descriptor, tee)
            return if (length > 0) {
                server.streamingResponse(
                    fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                    state.contentType,
                    wrapped,
                    length,
                    response.header("Content-Range"),
                )
            } else {
                server.chunkedResponse(fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT, state.contentType, wrapped)
            }
        } else {
            // 200: full representation from 0.
            val cl = response.body?.contentLength()?.takeIf { it > 0 }
            if (cl != null) {
                state.contentLength = cl
                Logger.i(TAG) { "learn[${key.take(8)}]: 200 → total=$cl (learned)" }
            } else {
                Logger.i(TAG) { "learn[${key.take(8)}]: 200, no Content-Length — chunked passthrough with tee (total learned on EOF)" }
            }
            if (headOnly) {
                response.close()
                return server.headResponse(
                    fi.iki.elonen.NanoHTTPD.Response.Status.OK,
                    state.contentType,
                    cl ?: 0L,
                    null,
                )
            }
            activeCounter.increment(key)
            val tee = TeeInputStream(
                state = state,
                source = bodyStream,
                skipBytes = 0L,
                teeBase = 0L,
                serveRemaining = cl ?: Long.MAX_VALUE,
                openEnded = cl == null,
                teeLimit = teeCeilingFor(state),
            )
            val wrapped = wrapStream(state, descriptor, tee)
            return if (cl != null) {
                server.streamingResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, state.contentType, wrapped, cl, null)
            } else {
                server.chunkedResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, state.contentType, wrapped)
            }
        }
    }

    /** Wraps a serving stream with bookkeeping (bytes-read tracking + close handling). */
    private fun wrapStream(state: LiveState, descriptor: PlayDescriptor, tee: TeeInputStream): InputStream {
        return object : InputStream() {
            override fun read(): Int = tee.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = tee.read(b, off, len)
            override fun close() {
                runCatching { tee.close() }
                onStreamClosed(state, descriptor)
            }
        }
    }

    /** Stream-close bookkeeping: flush + background-fill trigger + deferred delete. */
    private fun onStreamClosed(state: LiveState, descriptor: PlayDescriptor) {
        flushIfNeeded(state, force = true)
        val active = activeCounter.decrement(state.key)
        Logger.i(TAG) { "serve[${state.key.take(8)}]: stream closed (active=$active, cached=${state.cachedBytes}/${state.contentLength ?: "?"}B)" }
        if (active <= 0 && state.deleting) {
            runCatching { deleteEntryNow(state) }
        } else {
            maybeStartFill(state, descriptor)
        }
    }

    // ── HLS path ─────────────────────────────────────────────────────────────

    /** Fetches the playlist (master or media) + serves it REWRITTEN so all media goes through the proxy. */
    private fun servePlaylist(
        server: CacheProxyServer,
        key: String,
        descriptor: PlayDescriptor,
        state: LiveState,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val bytes = try {
            fetchUpstreamBytes(descriptor, rangeStart = null, rangeEnd = null)
        } catch (e: Exception) {
            Logger.w(TAG) { "hls[${key.take(8)}]: playlist fetch failed: ${e.message} — redirect (fail-open)" }
            return server.redirectResponse(descriptor.url)
        }
        return servePlaylistBytes(server, key, descriptor, state, bytes, headOnly)
    }

    private fun servePlaylistBytes(
        server: CacheProxyServer,
        key: String,
        descriptor: PlayDescriptor,
        state: LiveState,
        playlistBytes: ByteArray,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val text = playlistBytes.toString(Charsets.UTF_8)
        val baseUrl = server.baseUrl()
        state.isHls = true

        val rewritten = if (text.contains("#EXT-X-STREAM-INF")) {
            // MASTER: rewrite each variant URI → /p/<key>/<i> so MPV still picks quality itself.
            val variants = mutableListOf<String>()
            val out = StringBuilder()
            var expectUri = false
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    expectUri = true
                    out.append(raw).append('\n')
                } else if (expectUri && line.isNotEmpty() && !line.startsWith("#")) {
                    val absolute = resolveUri(line, descriptor.url)
                    variants.add(absolute)
                    out.append("$baseUrl/p/$key/${variants.size - 1}").append('\n')
                    expectUri = false
                } else {
                    out.append(raw).append('\n')
                }
            }
            state.hlsVariants = variants
            state.hlsByterange = text.contains("#EXT-X-BYTERANGE")
            Logger.i(TAG) { "hls[${key.take(8)}]: master playlist — ${variants.size} variant(s) rewritten to /p/" }
            out.toString()
        } else {
            // MEDIA: rewrite every segment URI → /s/<key>/<i> + the init map → /s/<key>/init.
            // D-247: also parse EXTINF durations → cumulative segment start-times (the
            // exact time→segment mapping the window policy needs).
            val segments = mutableListOf<String>()
            val durations = mutableListOf<Double>()
            var pendingDur = -1.0
            var initUri: String? = null
            val out = StringBuilder()
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF")) {
                    pendingDur = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: pendingDur
                    out.append(raw).append('\n')
                } else if (line.startsWith("#EXT-X-MAP:")) {
                    val mapUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    if (mapUri != null) {
                        initUri = resolveUri(mapUri, descriptor.url)
                        out.append(raw.replace(mapUri, "$baseUrl/s/$key/init")).append('\n')
                    } else {
                        out.append(raw).append('\n')
                    }
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val absolute = resolveUri(line, descriptor.url)
                    segments.add(absolute)
                    durations.add(pendingDur)
                    pendingDur = -1.0
                    out.append("$baseUrl/s/$key/${segments.size - 1}").append('\n')
                } else {
                    out.append(raw).append('\n')
                }
            }
            state.hlsSegments = segments
            if (durations.isNotEmpty() && durations.all { it > 0 }) {
                val starts = mutableListOf<Double>()
                var t = 0.0
                for (d in durations) {
                    starts.add(t)
                    t += d
                }
                state.hlsSegmentStarts = starts
                state.hlsTotalDuration = t
            } else {
                // Non-standard playlist (missing/unparseable EXTINF): window mapping
                // unavailable — serving falls back to cache-all-requested.
                state.hlsSegmentStarts = emptyList()
                state.hlsTotalDuration = -1.0
            }
            state.hlsInitUri = initUri
            state.hlsVod = text.contains("#EXT-X-ENDLIST")
            state.hlsByterange = text.contains("#EXT-X-BYTERANGE")
            state.segmentTotal = segments.size
            if (state.segmentsCached == 0) recountSegments(state)
            state.complete = state.hlsVod && state.segmentsCached >= state.segmentTotal
            Logger.i(TAG) {
                "hls[${key.take(8)}]: media playlist — ${segments.size} segment(s) rewritten to /s/ " +
                    "(cached=${state.segmentsCached}, vod=${state.hlsVod}, byterange=${state.hlsByterange}, " +
                    "dur=${if (state.hlsTotalDuration > 0) "${state.hlsTotalDuration.toInt()}s" else "?"})"
            }
            runCatching {
                store.updateSegmentStatsSync(key, state.segmentTotal, state.segmentsCached, state.segmentBytes, state.complete)
            }
            out.toString()
        }

        val body = rewritten.toByteArray(Charsets.UTF_8)
        if (headOnly) {
            return server.headResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, HLS_MIME, body.size.toLong(), null)
        }
        if (state.hlsByterange) {
            Logger.w(TAG) { "hls[${key.take(8)}]: EXT-X-BYTERANGE present — segments can't be safely cached by URL; playback proxied but caching limited" }
        }
        maybeStartFill(state, descriptor)
        return server.bytesResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, HLS_MIME, body)
    }

    /** Serves `/p/<key>/<i>`: variant playlist #i, parsed + rewritten as a media playlist. */
    internal fun serveVariantPlaylist(
        server: CacheProxyServer,
        key: String,
        variantIndex: Int,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val descriptor = descriptors[key]
            ?: return server.notFoundResponse()
        val state = acquireState(key, descriptor)
        val variantUrl = state.hlsVariants.getOrNull(variantIndex)
        if (variantUrl == null) {
            Logger.w(TAG) { "hls[${key.take(8)}]: variant #$variantIndex not in memory (stale session?) — redirect (fail-open)" }
            return server.redirectResponse(descriptor.url)
        }
        // CR-C minor fix: the variant descriptor carries the VARIANT URL so relative
        // segment URIs inside the variant playlist resolve against it (not the master).
        val variantDescriptor = PlayDescriptor(descriptor.id, variantUrl, descriptor.headers, descriptor.subtitleTracks, descriptor.audioTracks)
        val bytes = try {
            fetchUpstreamBytes(variantDescriptor, null, null)
        } catch (e: Exception) {
            Logger.w(TAG) { "hls[${key.take(8)}]: variant #$variantIndex fetch failed: ${e.message} — redirect (fail-open)" }
            return server.redirectResponse(variantUrl)
        }
        return servePlaylistBytes(server, key, variantDescriptor, state, bytes, headOnly)
    }

    /** Serves `/s/<key>/<i|init>`: one HLS segment — from the cache file or fetched + cached. */
    internal fun serveSegment(
        server: CacheProxyServer,
        key: String,
        segmentId: String,
        headOnly: Boolean,
    ): fi.iki.elonen.NanoHTTPD.Response {
        val descriptor = descriptors[key]
            ?: return server.notFoundResponse()
        val state = acquireState(key, descriptor)
        val url = if (segmentId == "init") state.hlsInitUri
        else segmentId.toIntOrNull()?.let { state.hlsSegments.getOrNull(it) }
        if (url == null) {
            Logger.w(TAG) { "seg[${key.take(8)}]: #$segmentId not in memory (playlist stale?) — 404; MPV will re-fetch the playlist" }
            return server.notFoundResponse()
        }

        val file = segCacheFile(key, segmentId, url)
        // D-247 window gate: only segments at/below the window's end index are CACHED
        // on fetch. Far-ahead read-ahead (MPV prefetching the whole episode) is served
        // WITHOUT writing — that was the over-caching vector.
        val windowEndIdx = hlsWindowRange(state)?.last
        val cacheThis = segmentId == "init" || windowEndIdx == null ||
            (segmentId.toIntOrNull() ?: -1) <= windowEndIdx
        if (!file.exists()) {
            // A stale file for the same index but a different URL (playlist drift) is garbage — remove it.
            // NOTE: the trailing '_' avoids prefix collisions (seg_5 vs seg_50).
            runCatching {
                segDir(key).listFiles()
                    ?.filter { it.name.startsWith("${segmentNamePrefix(segmentId)}_") && it.name != file.name }
                    ?.forEach { it.delete() }
            }
            val bytes = try {
                fetchUpstreamBytes(descriptor, null, null, urlOverride = url)
            } catch (e: Exception) {
                Logger.w(TAG) { "seg[${key.take(8)}]: #$segmentId fetch failed: ${e.message} — redirect (fail-open)" }
                return server.redirectResponse(url)
            }
            if (cacheThis) {
                runCatching {
                    segDir(key).mkdirs()
                    file.writeBytes(bytes)
                }
                registerSegmentCached(state, segmentId, bytes.size.toLong(), fetched = true)
                Logger.i(TAG) { "seg[${key.take(8)}]: #$segmentId fetched ${bytes.size}B → cached (${state.segmentsCached}/${state.segmentTotal})" }
            } else {
                Logger.i(TAG) {
                    "seg[${key.take(8)}]: #$segmentId fetched ${bytes.size}B — BEYOND window " +
                        "(end idx $windowEndIdx) — served, NOT cached"
                }
            }
            maybeStartFill(state, descriptor)
            if (headOnly) {
                return server.headResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, SEGMENT_MIME, bytes.size.toLong(), null)
            }
            return server.bytesResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, SEGMENT_MIME, bytes)
        }

        if (headOnly) {
            return server.headResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, SEGMENT_MIME, file.length(), null)
        }
        Logger.d(TAG) { "seg[${key.take(8)}]: #$segmentId served from cache (${file.length()}B)" }
        val stream = file.inputStream()
        // The segment cache file IS the tee — wrap only for stats on close.
        val wrapped = object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                runCatching { stream.close() }
                onStreamClosed(state, descriptor)
            }
        }
        activeCounter.increment(key)
        maybeStartFill(state, descriptor)
        return server.streamingResponse(fi.iki.elonen.NanoHTTPD.Response.Status.OK, SEGMENT_MIME, wrapped, file.length(), null)
    }

    /** Updates HLS segment stats (count/bytes/complete) + DB flush + eviction check.
     *
     * CR-C bug #2 fix: stats are RECOUNTED from the .seg directory (the files are the
     * source of truth) instead of incremented — segment fetches race between the
     * proxy worker threads + the background fill, and a plain `+=` on @Volatile
     * fields loses updates. The recount self-heals double-fetch races.
     */
    private fun registerSegmentCached(state: LiveState, segmentId: String, size: Long, fetched: Boolean) {
        if (!fetched) return
        recountSegments(state)
        val becameComplete = synchronized(state) {
            val wasComplete = state.complete
            state.complete = state.hlsVod && state.segmentTotal > 0 && state.segmentsCached >= state.segmentTotal
            state.complete && !wasComplete
        }
        runCatching {
            store.updateSegmentStatsSync(state.key, state.segmentTotal, state.segmentsCached, state.segmentBytes, state.complete)
        }
        if (becameComplete) {
            Logger.i(TAG) { "complete[${state.key.take(8)}]: HLS entry fully cached (${state.segmentsCached} segments, ${state.segmentBytes / MB} MB)" }
        }
        maybeEvict()
    }

    // ── Background fill (session-2 feature) ──────────────────────────────────

    /**
     * Starts the background fill for the entry if applicable. The fill fetches the
     * remaining gaps (progressive) or segments (HLS) until the entry is complete —
     * "while it is playing in the background, everything else of it will load".
     */
    private fun maybeStartFill(state: LiveState, descriptor: PlayDescriptor) {
        if (!preferences.cacheEnabled || state.deleting || state.complete) return
        if (fills.containsKey(state.key)) return
        if (state.isHls) {
            if (!state.hlsVod) {
                Logger.d(TAG) { "fill[${state.key.take(8)}]: live HLS playlist — fill disabled" }
                return
            }
            if (state.hlsByterange) {
                Logger.d(TAG) { "fill[${state.key.take(8)}]: BYTERANGE playlist — fill disabled" }
                return
            }
            if (state.segmentTotal > 0 && state.segmentsCached >= state.segmentTotal) return
        }
        val job = scope.launch(Dispatchers.IO) {
            runCatching { fillLoop(state, descriptor) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logger.w(TAG) { "fill[${state.key.take(8)}]: stopped — ${it.message}" }
                }
        }
        fills[state.key] = job
        job.invokeOnCompletion { fills.remove(state.key, job) }
        Logger.i(TAG) {
            "fill[${state.key.take(8)}]: started (WINDOW mode " +
                "pos=${state.playbackPosSec.toInt()}s/${state.playbackDurSec.toInt()}s, " +
                "cached=${state.cachedBytes + state.segmentBytes}B)"
        }
    }

    /**
     * D-247: tick-based window fill. Each tick computes the CURRENT progress window
     * and fetches at most one block (progressive: ≤ 8 MB) or one segment (HLS) inside
     * it — self-paced (~4 MB/s max), never beyond [pos − BEHIND, pos + AHEAD]. Exits
     * when the entry is deleting, caching is disabled, or the window has been
     * satisfied while no progress arrived for FILL_IDLE_EXIT_MS (re-triggered by the
     * next progress push).
     */
    private suspend fun fillLoop(state: LiveState, descriptor: PlayDescriptor) {
        var errors = 0
        var idleTicks = 0
        while (true) {
            if (state.deleting || !preferences.cacheEnabled) {
                Logger.i(TAG) { "fill[${state.key.take(8)}]: stopped (${if (state.deleting) "deleted" else "disabled"})" }
                return
            }
            val playingRecently = System.currentTimeMillis() - state.lastProgressAt < FILL_IDLE_EXIT_MS
            var worked = false
            if (state.isHls) {
                if (state.hlsSegments.isNotEmpty() && state.hlsVod && !state.hlsByterange) {
                    val range = hlsWindowRange(state)
                    if (range != null) {
                        val nextIdx = range.firstOrNull { idx ->
                            !segCacheFile(state.key, idx.toString(), state.hlsSegments[idx]).exists()
                        }
                        if (nextIdx != null) {
                            try {
                                val bytes = fetchUpstreamBytes(descriptor, null, null, urlOverride = state.hlsSegments[nextIdx])
                                val file = segCacheFile(state.key, nextIdx.toString(), state.hlsSegments[nextIdx])
                                runCatching {
                                    segDir(state.key).mkdirs()
                                    file.writeBytes(bytes)
                                }
                                registerSegmentCached(state, nextIdx.toString(), bytes.size.toLong(), fetched = true)
                                Logger.i(TAG) {
                                    "fill[${state.key.take(8)}]: segment ${nextIdx + 1}/${state.segmentTotal} " +
                                        "cached (${bytes.size}B, window ${range.first}..${range.last})"
                                }
                                worked = true
                                errors = 0
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errors++
                                Logger.w(TAG) { "fill[${state.key.take(8)}]: segment $nextIdx failed ($errors/$FILL_MAX_ERRORS): ${e.message}" }
                                if (errors >= FILL_MAX_ERRORS) return
                                delay(FILL_BACKOFF_MS)
                            }
                        }
                    }
                }
            } else {
                val gap = nextWindowGap(state)
                if (gap != null) {
                    val blockEnd = minOf(gap.endInclusive, gap.start + FILL_BLOCK_BYTES - 1)
                    try {
                        fetchToCache(state, descriptor, gap.start, blockEnd)
                        worked = true
                        errors = 0
                        Logger.i(TAG) {
                            "fill[${state.key.take(8)}]: block $gap.start-$blockEnd (window) " +
                                "→ cached=${state.cachedBytes}/${state.contentLength ?: "?"}B"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors++
                        Logger.w(TAG) { "fill[${state.key.take(8)}]: block $gap.start failed ($errors/$FILL_MAX_ERRORS): ${e.message}" }
                        if (errors >= FILL_MAX_ERRORS) return
                        delay(FILL_BACKOFF_MS)
                    }
                }
            }
            if (worked) {
                idleTicks = 0
                delay(FILL_TICK_MS)
                continue
            }
            // Nothing to fetch in the current window.
            idleTicks++
            if (!playingRecently && idleTicks >= 2) {
                Logger.i(TAG) { "fill[${state.key.take(8)}]: window satisfied + playback idle — exiting (re-triggers on next play)" }
                return
            }
            delay(FILL_TICK_MS * 3)
        }
    }

    /**
     * D-247: the next gap to fill — ONLY gaps intersecting the progress window
     * [pos − BEHIND, pos + AHEAD], behind-gaps first (MPV never fetches those
     * itself; ahead-gaps are usually being teed by the player's own read-ahead).
     * Replaces the old whole-file gap walker.
     */
    private fun nextWindowGap(state: LiveState): ByteRange? {
        val (winStart, winEnd) = fillWindowBytes(state) ?: return null
        val posB = playbackPosByte(state)
        val merged = CacheRanges.merge(state.ranges)
        val behind = mutableListOf<ByteRange>()
        val ahead = mutableListOf<ByteRange>()
        var cursor = 0L
        for (r in merged) {
            if (r.endInclusive < cursor) continue
            if (r.start > cursor) {
                val gapEnd = minOf(r.start - 1, winEnd)
                val gapStart = maxOf(cursor, winStart)
                if (gapStart <= gapEnd) {
                    val g = ByteRange(gapStart, gapEnd)
                    if (posB != null && gapEnd <= posB) behind.add(g) else ahead.add(g)
                }
            }
            cursor = r.endInclusive + 1
        }
        if (cursor <= winEnd) {
            val gapStart = maxOf(cursor, winStart)
            if (gapStart <= winEnd) {
                val g = ByteRange(gapStart, winEnd)
                if (posB != null && g.endInclusive <= posB) behind.add(g) else ahead.add(g)
            }
        }
        return (behind + ahead).firstOrNull()
    }

    /** Fetches [start, endInclusive] upstream + writes it into the cache file (positional). */
    private fun fetchToCache(state: LiveState, descriptor: PlayDescriptor, start: Long, endInclusive: Long) {
        val channel = ensureChannel(state)
        val call = client.newCall(upstreamRequest(descriptor, start, endInclusive, sendRange = state.upstreamSupportsRanges))
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("upstream HTTP ${resp.code} for fill block $start-$endInclusive")
            val body = resp.body?.byteStream() ?: throw IOException("empty fill body")
            var writePos = start
            var toSkip = 0L
            if (resp.code == 206) {
                val actualStart = parseContentRangeStart(resp.header("Content-Range")) ?: start
                if (actualStart > writePos) throw IOException("fill range start mismatch (asked $writePos, got $actualStart)")
                toSkip = writePos - actualStart
            } else {
                toSkip = writePos
            }
            val buffer = ByteArray(UPSTREAM_BUFFER_BYTES)
            body.use { input ->
                while (toSkip > 0) {
                    val n = input.read(buffer, 0, minOf(toSkip, buffer.size.toLong()).toInt())
                    if (n == -1) throw IOException("premature EOF during fill skip at $writePos")
                    toSkip -= n
                }
                while (writePos <= endInclusive) {
                    val want = minOf(buffer.size.toLong(), endInclusive - writePos + 1).toInt()
                    val n = input.read(buffer, 0, want)
                    if (n == -1) throw IOException("premature EOF during fill at $writePos (expected through $endInclusive)")
                    channel.write(ByteBuffer.wrap(buffer, 0, n), writePos)
                    writePos += n
                    // CR-E flag #1 (same class of fix): register only bytes that were written.
                    registerCached(state, start, writePos - 1)
                }
            }
        }
    }

    // ── Stream sources ───────────────────────────────────────────────────────

    private fun ensureChannel(state: LiveState): FileChannel {
        state.channel?.let { return it }
        synchronized(state) {
            state.channel?.let { return it }
            val channel = FileChannel.open(
                binFile(state.key).toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE,
            )
            state.channel = channel
            return channel
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
                val n = ensureChannel(state).read(buf, pos)
                if (n <= 0) return -1
                buf.flip()
                buf.get(b, off, n)
                pos += n
                remaining -= n
                return n
            }
        }

    /**
     * An upstream gap slice: fetches [start, endInclusive] (ranged when supported,
     * plain + skip otherwise) and tee's every byte into the cache file while it
     * streams through.
     */
    private fun upstreamSlice(
        state: LiveState,
        descriptor: PlayDescriptor,
        start: Long,
        endInclusive: Long,
        expectRange: Boolean,
    ): InputStream {
        val openEnded = endInclusive >= Long.MAX_VALUE - 1
        val request = upstreamRequest(
            descriptor,
            start,
            if (openEnded) null else endInclusive,
            sendRange = expectRange && state.upstreamSupportsRanges,
        )
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("upstream ${response.code} for range $start-$endInclusive")
        }
        val body = response.body
        if (body == null) {
            response.close()
            throw IOException("empty upstream body for range $start-$endInclusive")
        }
        val bodyStream = body.byteStream()
        var skip = 0L
        var serveRemaining = endInclusive - start + 1
        if (response.code == 206) {
            val total = parseContentRangeTotal(response.header("Content-Range"))
            if (total != null && state.contentLength != null && total != state.contentLength) {
                response.close()
                Logger.w(TAG) { "upstream[${state.key.take(8)}]: content length changed ($total ≠ ${state.contentLength}) — resetting entry" }
                resetEntry(state)
                throw IOException("upstream content length changed")
            }
            parseContentRangeStart(response.header("Content-Range"))?.let { actualStart ->
                if (actualStart < start) skip = start - actualStart
            }
        } else {
            skip = start
            body.contentLength().takeIf { it > 0 }?.let { len ->
                if (state.contentLength == null && !openEnded && start + serveRemaining == len) {
                    state.contentLength = len
                }
            }
        }
        return TeeInputStream(
            state = state,
            source = bodyStream,
            skipBytes = skip,
            teeBase = start,
            serveRemaining = serveRemaining,
            openEnded = false,
            teeLimit = teeCeilingFor(state),
        )
    }

    /**
     * Reads the upstream body, optionally skipping a prefix, tee'ing bytes into the
     * cache file at [teeBase + forwarded] while forwarding to the client.
     *
     * D-247: writes are bounded by [teeLimit] (exclusive byte offset — the progress
     * window ceiling). Bytes at/above the limit are SERVED but NOT written, so MPV's
     * aggressive read-ahead can never tee a whole episode into the cache.
     */
    private inner class TeeInputStream(
        private val state: LiveState,
        private val source: InputStream,
        skipBytes: Long,
        private val teeBase: Long,
        private var serveRemaining: Long,
        private val openEnded: Boolean,
        private val teeLimit: Long = Long.MAX_VALUE,
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
            // D-247: tee only the clipped portion below the window ceiling.
            // CR-E flag #1 fix: register the range ONLY when the write SUCCEEDED —
            // registering on a failed write created phantom cached ranges that a
            // later disk-slice serve would read back truncated.
            val writeFrom = teeBase + forwarded
            if (writeFrom < teeLimit) {
                val writable = minOf(n.toLong(), teeLimit - writeFrom).toInt()
                if (writable > 0) {
                    val wrote = runCatching {
                        ensureChannel(state).write(ByteBuffer.wrap(buf, 0, writable), writeFrom)
                    }.isSuccess
                    if (wrote) {
                        registerCached(state, writeFrom, writeFrom + writable - 1)
                    } else {
                        Logger.w(TAG) { "tee[${state.key.take(8)}]: write failed at $writeFrom — range NOT registered" }
                    }
                }
            }
            forwarded += n
            serveRemaining -= n
            System.arraycopy(buf, 0, b, off, n)
            return n
        }

        override fun close() {
            runCatching { source.close() }
            if (openEnded && forwarded > 0) {
                // Chunked open-ended stream reached some EOF boundary — learn the total.
                val total = teeBase + forwarded
                if (state.contentLength == null) {
                    state.contentLength = total
                    Logger.i(TAG) { "learn[${state.key.take(8)}]: EOF → total=$total (learned)" }
                }
            }
        }
    }

    /** Adds [from, to] to the live ranges (synchronized per state) + throttled DB flush. */
    private fun registerCached(state: LiveState, from: Long, to: Long) {
        if (to < from) return
        val shouldFlush = synchronized(state) {
            state.ranges = CacheRanges.merge(state.ranges + ByteRange(from, to))
            state.cachedBytes = CacheRanges.totalBytes(state.ranges)
            val totalNow = state.contentLength
            state.complete = totalNow != null &&
                state.ranges.size == 1 && state.ranges.first().start == 0L &&
                state.ranges.first().endInclusive >= totalNow - 1
            val now = System.currentTimeMillis()
            val sinceFlush = now - state.lastFlushAt
            val bytesSinceFlush = state.cachedBytes - state.lastFlushBytes
            // Throttled progress log (every 4MB or on completion).
            if (state.complete || state.cachedBytes - state.lastLoggedTeeBytes >= LOG_TEE_BYTES) {
                state.lastLoggedTeeBytes = state.cachedBytes
                Logger.i(TAG) { "tee[${state.key.take(8)}]: cached=${state.cachedBytes}/${state.contentLength ?: "?"}B (ranges=${state.ranges.size}${if (state.complete) ", COMPLETE" else ""})" }
            }
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
        if (state.isHls) return // HLS stats are flushed by registerSegmentCached.
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

    // ── Upstream helpers ─────────────────────────────────────────────────────

    /** Fetches a full upstream body to memory (playlists, segments, probes). */
    private fun fetchUpstreamBytes(
        descriptor: PlayDescriptor,
        rangeStart: Long?,
        rangeEnd: Long?,
        urlOverride: String? = null,
    ): ByteArray {
        val request = upstreamRequest(
            descriptor,
            rangeStart,
            rangeEnd,
            sendRange = rangeStart != null,
            urlOverride = urlOverride,
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("upstream HTTP ${response.code}")
            }
            val out = ByteArrayOutputStream()
            response.body?.byteStream()?.use { it.copyTo(out) }
            return out.toByteArray()
        }
    }

    private fun upstreamRequest(
        descriptor: PlayDescriptor,
        rangeStart: Long?,
        rangeEnd: Long?,
        sendRange: Boolean,
        urlOverride: String? = null,
    ): Request {
        val builder = Request.Builder().url(urlOverride ?: descriptor.url)
        MpvHeaderParser.parse(descriptor.headers).forEach { (k, v) -> builder.header(k, v) }
        // Byte-offset integrity: never let OkHttp negotiate transparent gzip on
        // range responses (decompressed bytes ≠ compressed offsets → silent cache
        // corruption). Also mirrors the D-207 localhost identity precedent.
        builder.header("Accept-Encoding", "identity")
        if (sendRange && rangeStart != null) {
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

    private fun parseContentRangeEnd(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val rangePart = header.substringAfter(' ', "").substringBefore('/')
        val idx = rangePart.indexOf('-')
        if (idx < 0 || idx == rangePart.length - 1) return null
        return rangePart.substring(idx + 1).trim().toLongOrNull()
    }

    /**
     * Parses a client Range header against a known total.
     * Returns (start, endInclusive). Multi-range → full span (ignored, serve 200).
     */
    private fun parseRange(rangeHeader: String?, total: Long): Pair<Long, Long> {
        if (rangeHeader.isNullOrBlank()) return 0L to total - 1
        val lenient = parseRangeLenient(rangeHeader) ?: return 0L to total - 1
        val start = lenient.first
        val end = lenient.second ?: (total - 1)
        return start to minOf(end, total - 1)
    }

    /**
     * Parses a client Range header WITHOUT a known total: (start, end?) — end null =
     * open-ended. Multi-range → null (caller falls back to full-body semantics).
     */
    private fun parseRangeLenient(rangeHeader: String?): Pair<Long, Long?>? {
        if (rangeHeader.isNullOrBlank()) return null
        val value = rangeHeader.trim().removePrefix("bytes=").trim()
        if (value.contains(',')) return null // multi-range: ignore
        val idx = value.indexOf('-')
        return when {
            idx <= 0 -> null // suffix range "bytes=-N": can't mirror without total — treat as full
            idx == value.length - 1 -> {
                val start = value.substring(0, idx).trim().toLongOrNull() ?: return null
                start to null
            }
            else -> {
                val start = value.substring(0, idx).trim().toLongOrNull() ?: return null
                val end = value.substring(idx + 1).trim().toLongOrNull()
                start to end
            }
        }
    }

    // ── Live state + deletion + eviction ─────────────────────────────────────

    /**
     * Creates or refreshes the live state for a key: DB row (insert if new /
     * refresh upstream URL+headers+tracks if existing), stale-file verification,
     * and (HLS) a recount of cached segment files.
     */
    private fun acquireState(key: String, descriptor: PlayDescriptor): LiveState {
        liveStates[key]?.let { existing ->
            val now = System.currentTimeMillis()
            if (now - existing.lastTouchAt > TOUCH_INTERVAL_MS) {
                existing.lastTouchAt = now
                runCatching {
                    store.updateUpstreamSync(key, descriptor.url, descriptor.headers, descriptor.subtitleTracks, descriptor.audioTracks)
                }
            }
            return existing
        }
        synchronized(stateInitLock) {
            liveStates[key]?.let { return it }
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
                    segmentTotal = 0,
                    segmentsCached = 0,
                    subtitleTracks = descriptor.subtitleTracks,
                    audioTracks = descriptor.audioTracks,
                )
                runCatching { store.insertSync(entry) }
                Logger.i(TAG) { "entry[${key.take(8)}]: registered '${entry.animeTitle}' EP ${entry.episodeNumber} [${entry.serverKey}]" }
            } else {
                runCatching {
                    store.updateUpstreamSync(key, descriptor.url, descriptor.headers, descriptor.subtitleTracks, descriptor.audioTracks)
                }
            }

            val state = LiveState(key)
            if (entry.segmentTotal > 0 || segDir(key).exists()) {
                state.isHls = true
                state.segmentTotal = entry.segmentTotal
                recountSegments(state)
                state.complete = entry.complete
            } else {
                // ── Stale-file verification (progressive) ──
                var ranges = entry.cachedRanges
                var complete = entry.complete
                val file = binFile(key)
                if (!file.exists()) {
                    if (ranges.isNotEmpty() || complete) {
                        Logger.w(TAG) { "entry[${key.take(8)}]: cache file missing — resetting ranges" }
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
                        Logger.w(TAG) { "entry[${key.take(8)}]: clamping ranges to file size ($fileLen)" }
                        ranges = clamped
                    }
                    if (complete && entry.contentLength != null && fileLen != entry.contentLength) {
                        Logger.w(TAG) { "entry[${key.take(8)}]: complete but file size ≠ content length — resetting" }
                        ranges = emptyList()
                        complete = false
                    }
                }
                state.ranges = CacheRanges.merge(ranges)
                state.cachedBytes = CacheRanges.totalBytes(state.ranges)
                state.contentLength = entry.contentLength
                state.contentType = entry.contentType.ifBlank { "video/mp4" }
                state.complete = complete
                if (state.complete && state.contentLength == null && file.exists() && file.length() > 0) {
                    state.contentLength = file.length()
                }
            }
            liveStates[key] = state
            return state
        }
    }

    /** Recomputes segmentsCached + segmentBytes from the .seg directory contents. */
    private fun recountSegments(state: LiveState) {
        val dir = segDir(state.key)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val segmentFiles = files.filter { it.name.startsWith("seg_") }
        val distinctIndices = segmentFiles.mapNotNull { f ->
            f.name.removePrefix("seg_").substringBefore('_').toIntOrNull()
        }.toSet()
        state.segmentsCached = distinctIndices.size
        state.segmentBytes = files.sumOf { it.length() }
    }

    private fun segCacheFile(key: String, segmentId: String, url: String): File {
        val prefix = segmentNamePrefix(segmentId)
        return File(segDir(key), "${prefix}_${hash8(url)}.ts")
    }

    private fun segmentNamePrefix(segmentId: String): String =
        if (segmentId == "init") "init" else "seg_$segmentId"

    private fun hash8(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun removeEntryInternal(cacheKey: String) {
        val state = liveStates[cacheKey]
        if (state != null && activeCounter.activeCount(cacheKey) > 0) {
            state.deleting = true
            Logger.i(TAG) { "delete[${cacheKey.take(8)}]: deferred (stream active)" }
            return
        }
        if (state != null) deleteEntryNow(state) else deleteOrphan(cacheKey)
    }

    private fun deleteEntryNow(state: LiveState) {
        state.deleting = true
        fills.remove(state.key)?.cancel()
        liveStates.remove(state.key)
        descriptors.remove(state.key)
        runCatching { state.channel?.close() }
        runCatching { binFile(state.key).delete() }
        runCatching { segDir(state.key).deleteRecursively() }
        runCatching { store.deleteSync(state.key) }
        Logger.i(TAG) { "delete[${state.key.take(8)}]: removed (bin + ${state.segmentsCached} segments + row)" }
    }

    private fun deleteOrphan(cacheKey: String) {
        fills.remove(cacheKey)?.cancel()
        runCatching { binFile(cacheKey).delete() }
        runCatching { segDir(cacheKey).deleteRecursively() }
        runCatching { store.deleteSync(cacheKey) }
    }

    /** Eviction check throttled (called from proxy threads after flushes). */
    private fun maybeEvict() {
        val now = System.currentTimeMillis()
        if (now - lastEvictCheckAt < EVICT_CHECK_INTERVAL_MS) return
        lastEvictCheckAt = now
        runCatching { evictIfNeededInternal() }
            .onFailure { Logger.w(TAG) { "evict: check failed: ${it.message}" } }
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
            Logger.i(TAG) { "evict[${entry.cacheKey.take(8)}]: removed (${entry.cachedBytes / MB} MB) — total ${total / MB} MB / limit ${limit / MB} MB" }
        }
    }

    /** Startup sweep: drop entries whose files vanished; fix size mismatches. */
    private fun sweepStale() {
        val entries = store.listForEvictionSync()
        var removed = 0
        for (entry in entries) {
            val bin = binFile(entry.cacheKey)
            val seg = segDir(entry.cacheKey)
            if (entry.segmentTotal > 0 || seg.exists()) {
                // HLS entry.
                if (!seg.exists() || seg.listFiles()?.isEmpty() == true) {
                    deleteOrphan(entry.cacheKey)
                    removed++
                    continue
                }
                val state = LiveState(entry.cacheKey)
                state.isHls = true
                state.segmentTotal = entry.segmentTotal
                recountSegments(state)
                if (state.segmentsCached != entry.segmentsCached || state.segmentBytes != entry.cachedBytes) {
                    runCatching {
                        store.updateSegmentStatsSync(entry.cacheKey, entry.segmentTotal, state.segmentsCached, state.segmentBytes, entry.complete)
                    }
                }
            } else {
                if (!bin.exists()) {
                    if (entry.cachedBytes > 0 || entry.complete) {
                        deleteOrphan(entry.cacheKey)
                        removed++
                    }
                    continue
                }
                val fileLen = bin.length()
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
        }
        if (removed > 0) Logger.i(TAG) { "sweep: removed $removed orphaned entries" }
    }

    private fun ensureServerStarted() {
        if (server != null && server?.isAlive == true) return
        synchronized(this) {
            if (server != null && server?.isAlive == true) return
            val s = CacheProxyServer(manager = this)
            s.start()
            server = s
            Logger.i(TAG) { "start: cache proxy listening on ${s.baseUrl()}" }
        }
    }

    companion object {
        private const val TAG = "Anikuta:Core:PlaybackCache"
        private const val MB = 1024L * 1024L
        private const val MIN_FREE_BYTES = 256L * MB
        private const val DISK_BUFFER_BYTES = 64 * 1024
        private const val UPSTREAM_BUFFER_BYTES = 64 * 1024
        private const val FLUSH_INTERVAL_MS = 2_000L
        private const val FLUSH_DELTA_BYTES = 4L * MB
        private const val LOG_TEE_BYTES = 4L * MB
        private const val EVICT_CHECK_INTERVAL_MS = 30_000L
        private const val TOUCH_INTERVAL_MS = 60_000L

        /** Background fill: window-bounded (D-247). One block/segment per FILL_TICK. */
        private const val FILL_BLOCK_BYTES = 8L * MB
        private const val FILL_TICK_MS = 2_000L
        private const val FILL_BACKOFF_MS = 5_000L
        private const val FILL_MAX_ERRORS = 3
        /** The progress window: cache [pos − BEHIND, pos + AHEAD] (seconds). */
        private const val FILL_WINDOW_BEHIND_S = 120f
        private const val FILL_WINDOW_AHEAD_S = 120f
        /** Tee fallback ceiling when the duration is still unknown (bounded read-ahead). */
        private const val TEE_FALLBACK_AHEAD_BYTES = 32L * MB
        /** Fill re-trigger threshold: position movement that restarts the fill job. */
        private const val FILL_RETRIGGER_S = 30f
        /** Fill idle exit: no progress push for this long → the fill exits. */
        private const val FILL_IDLE_EXIT_MS = 60_000L

        private const val HLS_MIME = "application/vnd.apple.mpegurl"
        private const val SEGMENT_MIME = "video/mp2t"

        private fun looksLikeHls(url: String): Boolean {
            val noQuery = url.substringBefore('?')
            return noQuery.endsWith(".m3u8") || noQuery.endsWith(".m3u")
        }

        private fun looksLikeHlsContentType(contentType: String): Boolean {
            val ct = contentType.substringBefore(';').trim().lowercase()
            return ct.contains("mpegurl") || ct.contains("x-mpegurl") || ct.contains("m3u8")
        }

        /** Resolves a possibly-relative URI against a base URL. */
        private fun resolveUri(uri: String, baseUrl: String): String {
            if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
            return try {
                java.net.URI(baseUrl).resolve(uri).toString()
            } catch (e: Exception) {
                baseUrl.substringBeforeLast('/') + "/" + uri
            }
        }
    }
}
