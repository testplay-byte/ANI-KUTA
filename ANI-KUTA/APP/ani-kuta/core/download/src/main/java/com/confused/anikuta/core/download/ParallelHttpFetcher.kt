package com.confused.anikuta.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Per-fetch OkHttp call registry (singleton fetchers — state MUST be per-invocation).
 * Top-level + internal: shared by ParallelHttpFetcher AND HlsDownloader's parallel
 * mode (D-246 instant-teardown on coroutine cancellation).
 */
internal class CallRegistry {
    private val calls: MutableSet<Call> = Collections.synchronizedSet(mutableSetOf<Call>())
    fun register(call: Call) { calls.add(call) }
    fun unregister(call: Call) { calls.remove(call) }
    fun cancelAll() {
        synchronized(calls) {
            calls.forEach { runCatching { it.cancel() } }
        }
    }
}

/**
 * The parallel byte-range download engine (test-feature branch — Parallel Download
 * Engine, PLAN.md B.4).
 *
 * MPV-inspired throughput techniques (adapted to OkHttp — connection pooling +
 * keep-alive come free; HTTP/2 multiplexing is negotiated automatically via ALPN
 * where the server supports it):
 *  - **Probe** (Range: bytes=0-1 — HEAD is 405'd by anti-scraping CDNs, REVIEW-5 M39):
 *    verifies range support + captures the total size + Content-Type (HLS delegation).
 *  - **Parallel chunk workers** (N = advancedThreads, connection-budget-capped):
 *    each fetches its byte range + writes positionally into a pre-allocated sparse
 *    temp file (no final merge step, no fragmentation).
 *  - **Per-chunk exponential backoff** (2^n seconds capped at 30s, advancedMaxRetries):
 *    IOException / 5xx / 429 / premature-EOF / range-mismatch.
 *  - **Stall watchdog**: a chunk whose elapsed time exceeds chunkSize / 50 KB/s is
 *    aborted + retried (the article's 50 KB/s floor).
 *  - **Active-call registry**: blocked OkHttp reads (≤60s read timeout) are released
 *    via Call.cancel() on teardown/re-resolve.
 *  - **Re-resolve on localhost failures** (D-149/D-194/D-207): on ANY HttpException
 *    (incl. 403 — the primary proxy-churn case) or retry-exhausted IO — cancel
 *    siblings, truncate, rebuild the plan with the fresh URL (max 1, matching
 *    MAX_RE_RESOLVE_ATTEMPTS).
 *  - **Chunk sidecar** (`video.<ext>.chunks`): per-chunk written positions persisted
 *    atomically (tmp+rename) for pause/resume. Absent sidecar + non-empty temp →
 *    restart clean (engine-switch rule).
 *
 * PROGRESS THREAD-SAFETY (PLAN.md B.4 #4): DownloadQueue's onProgress lambda
 * mutates non-thread-safe state (ArrayDeque + captured vars) — a dedicated
 * reporter coroutine samples the AtomicLong every 250ms and is the ONLY
 * onProgress caller during the parallel phase.
 *
 * Fallbacks: server ignores Range (probe 200 / unknown total) → internal
 * single-stream sequential path (same fetcher, no cross-fetcher gymnastics).
 */
class ParallelHttpFetcher(
    private val client: OkHttpClient,
    private val preferences: DownloadPreferences,
    private val hlsDownloader: HlsDownloader,
    private val store: DownloadStore,
    private val reResolver: HttpDownloader.ReResolver?,
) : VideoFetcher {

    /** A mutable chunk position (written position advances as the worker streams). */
    private class Chunk(val start: Long, val end: Long, @Volatile var pos: Long) {
        val size: Long get() = end - start + 1
        val isComplete: Boolean get() = pos > end
    }

    @Serializable
    private data class ChunkState(val start: Long, val end: Long, val pos: Long)

    @Serializable
    private data class ParallelSidecar(val total: Long, val chunks: List<ChunkState>)

    private data class Probe(val parallelCapable: Boolean, val total: Long, val isHls: Boolean)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        // ── Engine-switch guard (PLAN.md B.4 #2) ──
        // No sidecar + non-empty temp = legacy partial OR garbage sparse file —
        // the length can't be trusted for chunk planning. Restart clean.
        val sidecarFile = sidecarFile(tempFile)
        if (tempFile.exists() && tempFile.length() > 0L && !sidecarFile.exists()) {
            DownloadLogger.w {
                "ParallelHttpFetcher — non-empty temp without sidecar (engine switch or " +
                    "legacy partial). Restarting clean."
            }
            tempFile.delete()
        }
        if (!tempFile.exists()) {
            sidecarFile.delete()
            File(tempFile.parentFile, "segments").deleteRecursively()
        }

        downloadWithReResolve(url, headers, tempFile, taskId, resolveContextJson, onProgress)
    }

    // ── The re-resolve loop ──────────────────────────────────────────────────

    private suspend fun downloadWithReResolve(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var currentUrl = url
        var currentHeaders = headers
        var reResolves = 0
        val registry = CallRegistry()
        // D-246: instant teardown on pause/cancel. Coroutine cancellation alone can't
        // interrupt a worker blocked in a socket read (it waits out the 60s read
        // timeout); cancelling the registered OkHttp calls unblocks them immediately,
        // so a network-loss pause stops all connections the moment it happens.
        // (No dispose needed: the handler fires once when the owning job completes,
        // and the job object becomes garbage right after.)
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) registry.cancelAll()
        }

        while (true) {
            // The probe sits INSIDE the try so a probe-stage IOException (dead
            // localhost proxy — the primary churn case) also reaches the re-resolve
            // path, matching the legacy downloader's behavior.
            val result = try {
                val probe = probeUpstream(currentUrl, currentHeaders)
                when {
                    probe.isHls ->
                        hlsDownloader.downloadToCache(currentUrl, currentHeaders, tempFile, taskId, onProgress)
                    !probe.parallelCapable || probe.total <= 0L ->
                        singleStreamFallback(currentUrl, currentHeaders, tempFile, probe.total, registry, onProgress)
                    else ->
                        runParallel(probe, currentUrl, currentHeaders, tempFile, registry, onProgress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fresh = tryReResolve(currentUrl, e, taskId, resolveContextJson, reResolves, tempFile, registry)
                if (fresh != null) {
                    currentUrl = fresh.url
                    currentHeaders = fresh.headers ?: currentHeaders
                    reResolves++
                    null // retry the loop with the fresh URL
                } else {
                    throw wrapIfNeeded(e)
                }
            }
            if (result != null) return result
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("unreachable")
    }

    /**
     * D-149/D-194/D-207 semantics: on ANY exception (esp. HttpException 403 — the
     * primary proxy-churn case) for a localhost URL with a resolve context, get a
     * fresh URL + restart clean (truncate temp + delete sidecar — the fresh proxy
     * may serve different bytes or not honor ranges). Max 1 attempt. Returns null
     * when not eligible or the re-resolve itself failed (caller rethrows original).
     *
     * Task 48 (CS downloads): eligibility ALSO covers short-TTL CloudStream
     * extractor links (resolveContext.linkRotates) — they 403 when the link
     * expires mid-download and self-heal the same way.
     */
    private suspend fun tryReResolve(
        url: String,
        error: Exception,
        taskId: Long,
        resolveContextJson: String?,
        reResolves: Int,
        tempFile: File,
        registry: CallRegistry,
    ): HttpDownloader.ReResolvedVideo? {
        val isLocalhost = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
        val linkRotates = resolveContextLinkRotates(resolveContextJson)
        if ((!isLocalhost && !linkRotates) || resolveContextJson == null || reResolver == null ||
            reResolves >= MAX_RE_RESOLVE_ATTEMPTS
        ) {
            return null
        }
        DownloadLogger.w {
            "ParallelHttpFetcher — ${error.javaClass.simpleName} on ${if (isLocalhost) "localhost" else "rotating"} URL; attempting " +
                "re-resolve (attempt ${reResolves + 1}/$MAX_RE_RESOLVE_ATTEMPTS): ${error.message}"
        }
        // Release sibling workers blocked in reads (≤60s read timeout otherwise).
        registry.cancelAll()
        val fresh = reResolver.reResolve(resolveContextJson) ?: return null
        store.updateDownloadVideoUrl(taskId, fresh.url)
        runCatching { tempFile.delete() }
        sidecarFile(tempFile).delete()
        return fresh
    }

    // ── The parallel engine ──────────────────────────────────────────────────

    private suspend fun runParallel(
        probe: Probe,
        url: String,
        headers: String?,
        tempFile: File,
        registry: CallRegistry,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val total = probe.total

        // ── Chunk plan: resume from the sidecar when it matches the probe total. ──
        val sidecarFile = sidecarFile(tempFile)
        val sidecar = readSidecar(sidecarFile)
        val chunks: List<Chunk>
        if (sidecar != null && sidecar.total == total) {
            chunks = sidecar.chunks.map { Chunk(it.start, it.end, it.pos) }
            val done = chunks.count { it.isComplete }
            DownloadLogger.i {
                "ParallelHttpFetcher — resuming: $done/${chunks.size} chunks complete, " +
                    "${chunks.sumOf { it.pos - it.start }} / $total bytes"
            }
        } else {
            if (sidecar != null) sidecarFile.delete() // total mismatch → restart
            tempFile.delete()
            chunks = buildChunkPlan(total)
        }
        writeSidecar(sidecarFile, total, chunks)

        val downloaded = AtomicLong(chunks.sumOf { it.pos - it.start })
        val raf = java.io.RandomAccessFile(tempFile, "rw")
        // Pre-allocate (sparse on ext4/f2fs — instant, no fragmentation, no runtime
        // allocation stalls). NOTE: this makes tempFile.length() == total even while
        // chunks are in flight — the sidecar (NOT the length) is the progress truth.
        raf.setLength(total)
        val channel: FileChannel = raf.channel

        try {
            coroutineScope {
                // The ONLY onProgress caller during the parallel phase (thread-safety
                // contract — see class KDoc). Also periodically persists the sidecar.
                val reporter = launch {
                    var ticks = 0
                    while (true) {
                        delay(REPORT_INTERVAL_MS)
                        onProgress(downloaded.get(), total)
                        if (++ticks % SIDECAR_FLUSH_TICKS == 0) {
                            runCatching { writeSidecar(sidecarFile, total, chunks) }
                        }
                    }
                }
                try {
                    coroutineScope {
                        chunks.forEach { chunk ->
                            launch {
                                workerChunk(chunk, url, headers, channel, downloaded, registry)
                            }
                        }
                    }
                } finally {
                    reporter.cancel()
                    runCatching { writeSidecar(sidecarFile, total, chunks) }
                }
            }
            // Final sample + completion.
            onProgress(downloaded.get(), total)
            raf.close()
            sidecarFile.delete()
            DownloadLogger.i {
                "ParallelHttpFetcher — complete: ${chunks.size} chunks, $total bytes, " +
                    "${effectiveWorkerCount()} workers"
            }
            return total
        } catch (e: Exception) {
            runCatching { raf.close() }
            throw e
        }
    }

    /** One chunk worker: fetch-loop with per-chunk retry + exponential backoff. */
    private suspend fun workerChunk(
        chunk: Chunk,
        url: String,
        headers: String?,
        channel: FileChannel,
        downloaded: AtomicLong,
        registry: CallRegistry,
    ) {
        val maxRetries = preferences.advancedMaxRetries.get().coerceIn(0, 10)
        var attempt = 0
        while (!chunk.isComplete) {
            coroutineContext.ensureActive()
            try {
                fetchChunk(chunk, url, headers, channel, downloaded, registry)
                return
            } catch (e: CancellationException) {
                throw e // sidecar persisted by runParallel's finally
            } catch (e: HttpException) {
                if (e.code in 500..599 || e.code == 429) {
                    attempt++
                    if (attempt > maxRetries) throw e
                    DownloadLogger.w {
                        "Chunk ${chunk.start}-${chunk.end}: HTTP ${e.code} " +
                            "(attempt $attempt/$maxRetries) — backing off"
                    }
                    delay(backoffMs(attempt))
                } else {
                    // 4xx → fetch level (re-resolve when localhost, else fatal).
                    throw e
                }
            } catch (e: IOException) {
                attempt++
                if (attempt > maxRetries) {
                    throw DownloadException(
                        "Chunk ${chunk.start}-${chunk.end} failed after $maxRetries retries: " +
                            "${e.message ?: e.javaClass.simpleName}",
                        e,
                    )
                }
                DownloadLogger.w {
                    "Chunk ${chunk.start}-${chunk.end}: ${e.message} " +
                        "(attempt $attempt/$maxRetries) — backing off"
                }
                delay(backoffMs(attempt))
            }
        }
    }

    /** Fetches one chunk's remaining range into the file (positional writes). */
    private suspend fun fetchChunk(
        chunk: Chunk,
        url: String,
        headers: String?,
        channel: FileChannel,
        downloaded: AtomicLong,
        registry: CallRegistry,
    ) {
        val call = client.newCall(buildChunkRequest(url, headers, chunk.pos, chunk.end))
        registry.register(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code, "HTTP ${response.code} for chunk ${chunk.start}-${chunk.end}")
                }

                var writePos = chunk.pos
                var toSkip = 0L
                if (response.code == 206) {
                    // Honor the server's actual range start (it may clamp).
                    val actualStart = parseContentRangeStart(response.header("Content-Range"))
                    if (actualStart != null) {
                        if (actualStart > writePos) {
                            throw IOException("range start mismatch: asked $writePos, got $actualStart")
                        }
                        toSkip = writePos - actualStart
                    }
                } else {
                    // 200 — range ignored; the body starts at 0 → skip to our position.
                    toSkip = writePos
                }

                val body = response.body?.byteStream() ?: throw IOException("empty chunk body")
                // Stall watchdog: the chunk must complete within size / 50 KB/s.
                val deadlineAt = System.currentTimeMillis() + (chunk.size / MIN_BYTES_PER_SEC) * 1000L
                val buffer = ByteArray(CHUNK_BUFFER_BYTES)
                body.use { input ->
                    while (toSkip > 0) {
                        val n = input.read(buffer, 0, minOf(toSkip, buffer.size.toLong()).toInt())
                        if (n == -1) throw IOException("premature EOF during skip at chunk ${chunk.start}-${chunk.end}")
                        toSkip -= n
                    }
                    while (writePos <= chunk.end) {
                        coroutineContext.ensureActive()
                        if (System.currentTimeMillis() > deadlineAt) {
                            throw IOException("chunk stalled below $MIN_BYTES_PER_SEC B/s")
                        }
                        val want = minOf(buffer.size.toLong(), chunk.end - writePos + 1).toInt()
                        val n = input.read(buffer, 0, want)
                        if (n == -1) throw IOException("premature EOF at $writePos (expected through ${chunk.end})")
                        channel.write(ByteBuffer.wrap(buffer, 0, n), writePos)
                        writePos += n
                        chunk.pos = writePos
                        downloaded.addAndGet(n.toLong())
                    }
                }
            }
        } finally {
            registry.unregister(call)
        }
    }

    // ── Probe + single-stream fallback ───────────────────────────────────────

    /**
     * Probes upstream capabilities with a 1-byte Range GET (REVIEW-5 M39 — HEAD is
     * 405'd by anti-scraping CDNs). Captures: range support, total size, + HLS
     * detection via Content-Type (mirrors downloadNormal's second-chance).
     */
    private fun probeUpstream(url: String, headers: String?): Probe {
        val request = buildChunkRequest(url, headers, 0L, 1L)
        client.newCall(request).execute().use { response ->
            val contentType = response.body?.contentType()?.toString()
            if (contentType != null &&
                VideoTypeDetector.detect(url, contentType) == VideoTypeDetector.VideoType.HLS
            ) {
                return Probe(parallelCapable = false, total = -1L, isHls = true)
            }
            if (response.code == 206) {
                val total = parseContentRangeTotal(response.header("Content-Range"))
                return Probe(
                    parallelCapable = total != null && total > 0,
                    total = total ?: -1L,
                    isHls = false,
                )
            }
            // 200 (or anything else): Range ignored → single-stream fallback.
            val len = response.body?.contentLength()?.takeIf { it > 0 } ?: -1L
            return Probe(parallelCapable = false, total = len, isHls = false)
        }
    }

    /**
     * Sequential fallback for Range-ignoring servers — a plain streaming GET
     * (behaviorally identical to the legacy single-connection path).
     */
    private suspend fun singleStreamFallback(
        url: String,
        headers: String?,
        tempFile: File,
        totalHint: Long,
        registry: CallRegistry,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val call = client.newCall(buildChunkRequest(url, headers, 0L, null))
        registry.register(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code, "HTTP ${response.code} for video URL")
                }
                val total = response.body?.contentLength()?.takeIf { it > 0 } ?: totalHint
                FileOutputStream(tempFile).use { os ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(CHUNK_BUFFER_BYTES)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n == -1) break
                            os.write(buffer, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                        os.flush()
                    }
                }
                return tempFile.length()
            }
        } finally {
            registry.unregister(call)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildChunkPlan(total: Long): List<Chunk> {
        var count = effectiveWorkerCount()
        var chunkSize = total / count
        // Merge tiny chunks (< 4 MB) — floor 1 chunk.
        while (count > 1 && chunkSize < MIN_CHUNK_BYTES) {
            count--
            chunkSize = total / count
        }
        val chunks = mutableListOf<Chunk>()
        var start = 0L
        while (start < total) {
            val end = minOf(start + chunkSize - 1, total - 1)
            chunks.add(Chunk(start, end, start))
            start = end + 1
        }
        return chunks
    }

    /** advancedThreads, capped by the ≤16-connections-per-queue budget. */
    private fun effectiveWorkerCount(): Int {
        val threads = preferences.advancedThreads.get().coerceIn(1, 8)
        val concurrency = preferences.concurrentDownloads.get().coerceIn(1, 5)
        return maxOf(1, minOf(threads, 16 / concurrency))
    }

    private fun backoffMs(attempt: Int): Long =
        minOf(1000L shl (attempt - 1).coerceIn(0, 10), MAX_BACKOFF_MS)

    /** Builds a ranged chunk request (or an un-ranged GET when [end] is null). */
    private fun buildChunkRequest(url: String, headers: String?, start: Long, end: Long?): Request {
        return Request.Builder().url(url).apply {
            if (end != null) header("Range", "bytes=$start-$end")
            // D-207: identity for localhost proxy URLs only (never direct CDNs).
            if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
                header("Accept-Encoding", "identity")
            }
            DownloadHeaderParser.parse(headers).forEach { (name, value) ->
                addHeader(name, value)
            }
        }.build()
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val idx = header.lastIndexOf('/')
        if (idx < 0) return null
        return header.substring(idx + 1).trim().toLongOrNull()
    }

    private fun parseContentRangeStart(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val rangePart = header.substringAfter(' ', "").substringBefore('/')
        val idx = rangePart.indexOf('-')
        if (idx <= 0) return null
        return rangePart.substring(0, idx).trim().toLongOrNull()
    }

    private fun wrapIfNeeded(e: Exception): Exception = when (e) {
        is HttpException, is DownloadException -> e
        else -> DownloadException(
            "Parallel download failed: ${e.message ?: e.javaClass.simpleName}",
            e,
        )
    }

    // ── Sidecar persistence (atomic: tmp + rename) ───────────────────────────

    private fun sidecarFile(tempFile: File): File =
        File(tempFile.parentFile, tempFile.name + SIDECAR_SUFFIX)

    private fun readSidecar(file: File): ParallelSidecar? = try {
        if (!file.exists()) null
        else json.decodeFromString(ParallelSidecar.serializer(), file.readText())
    } catch (e: Exception) {
        DownloadLogger.w { "Failed to read chunk sidecar: ${e.message}" }
        null
    }

    private fun writeSidecar(file: File, total: Long, chunks: List<Chunk>) {
        val payload = ParallelSidecar(
            total = total,
            chunks = chunks.map { ChunkState(it.start, it.end, it.pos) },
        )
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(ParallelSidecar.serializer(), payload))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        /** Sidecar filename suffix next to the temp video file. */
        const val SIDECAR_SUFFIX = ".chunks"

        private const val CHUNK_BUFFER_BYTES = 64 * 1024
        private const val MIN_CHUNK_BYTES = 4L * 1024 * 1024
        private const val MIN_BYTES_PER_SEC = 50_000L // the 50 KB/s stall floor
        private const val REPORT_INTERVAL_MS = 250L
        private const val SIDECAR_FLUSH_TICKS = 20 // ~5s at 250ms ticks
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MAX_RE_RESOLVE_ATTEMPTS = 1
    }
}
