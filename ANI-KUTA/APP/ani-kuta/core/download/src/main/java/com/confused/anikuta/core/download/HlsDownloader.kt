package com.confused.anikuta.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

/**
 * The HLS downloader — pure Kotlin (no ffmpeg). Fetches the `.m3u8` playlist,
 * resolves master playlists to the first variant, parses segments, downloads each
 * segment with retry, and concatenates them into a single `.ts` file.
 *
 * test-feature branch (Parallel Download Engine, PLAN.md B.5): `downloadToCache`
 * splits on the `advancedDownloader` preference:
 *  - **Legacy mode (OFF)** — byte-for-byte today's behavior: sequential segment
 *    fetching, encryption → hard reject. Untouched code path (stale parallel state
 *    is cleared first — its FileOutputStream truncates the output).
 *  - **Parallel mode (ON)** — concurrent segment workers (connection-budget-capped)
 *    writing spill files, an ordered writer appending strictly in index order,
 *    in-memory AES-128 decryption (was a hard reject), `#EXT-X-MEDIA-SEQUENCE`
 *    tracking for default-IV derivation, + an append-state sidecar for pause/resume.
 *
 * Legacy review notes preserved:
 *  - REVIEW-5 M32: `estimatedTotal` is REFINED after each segment using the running
 *    average segment size (converges for variable-bitrate HLS).
 *  - REVIEW-5 M33: segments are downloaded to memory FIRST + written only on
 *    success (no partial-append corruption).
 *  - REVIEW-5 M39: `probeSegmentSize` uses a 1-byte Range GET (NOT HEAD —
 *    anti-scraping CDNs reject HEAD with 405).
 *  - Byte-count-based progress per D.1.7.
 *
 * PNG-header stripping: some CDNs prepend a PNG image header to each HLS segment
 * to prevent direct downloading. Stripping happens BEFORE decryption (CDNs wrap
 * encrypted segments too).
 */
class HlsDownloader(
    private val client: OkHttpClient,
    private val tempCache: TempDownloadCache,
    private val preferences: DownloadPreferences,
) {

    /**
     * Downloads an HLS stream to [tempFile]. Returns the total bytes written.
     *
     * @param m3u8Url The `.m3u8` playlist URL.
     * @param headers HTTP headers (MPV comma format `"Key: Value,Key2: Value2"`),
     *   applied to every request (playlist + segment fetches).
     * @param tempFile The output file (will be created/overwritten).
     * @param taskId The download task ID (for logging + temp cache).
     * @param onProgress Called with `(downloadedBytes, estimatedTotal)`.
     */
    suspend fun downloadToCache(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        if (preferences.advancedDownloader.get()) {
            // Cross-engine sweep: stale progressive sidecar next to this temp file.
            File(tempFile.parentFile, tempFile.name + ParallelHttpFetcher.SIDECAR_SUFFIX).delete()
            downloadToCacheParallel(m3u8Url, headers, tempFile, taskId, onProgress)
        } else {
            // Legacy mode: clear stale parallel-HLS state — the legacy path's
            // FileOutputStream(tempFile) truncates the output, so a stale sidecar
            // would corrupt a later parallel resume.
            hlsSidecarFile(tempFile).delete()
            File(tempFile.parentFile, "segments").deleteRecursively()
            downloadToCacheLegacy(m3u8Url, headers, tempFile, taskId, onProgress)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PARALLEL MODE (test-feature branch — PLAN.md B.5)
    // ═════════════════════════════════════════════════════════════════════════

    /** An #EXT-X-KEY declaration (AES-128). */
    private class HlsKey(val method: String, val uri: String, val iv: ByteArray?)

    /** Resume state: which segments have been APPENDED to the temp file (NOT fetched). */
    @Serializable
    private data class HlsSidecar(
        val segmentCount: Int,
        val firstSegmentUrl: String,
        val lastSegmentUrl: String,
        /** Segments [0, appendedThrough) are appended to tempFile. */
        val appendedThrough: Int,
        val initDone: Boolean,
        val appendedBytes: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun downloadToCacheParallel(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        DownloadLogger.i { "HLS parallel download: $m3u8Url" }

        // 1. Fetch the playlist; master → first variant. FIX (pre-existing bug):
        //    the VARIANT URL (not the master URL) is the base for resolving
        //    relative segment + KEY URIs.
        val playlistText = fetchText(m3u8Url, headers)
        val mediaPlaylistText: String
        val baseUrl: String
        if (isMasterPlaylist(playlistText)) {
            val variantUrl = pickFirstVariant(playlistText, m3u8Url)
            mediaPlaylistText = fetchText(variantUrl, headers)
            baseUrl = variantUrl
        } else {
            mediaPlaylistText = playlistText
            baseUrl = m3u8Url
        }

        // 2. Parse: media sequence (default-IV derivation), key (AES-128), map, segments.
        val mediaSequence = parseMediaSequence(mediaPlaylistText)
        val key = parseEncryptionKey(mediaPlaylistText, baseUrl)
        val initSegment = parseInitSegment(mediaPlaylistText, baseUrl)
        val segments = parseSegments(mediaPlaylistText, baseUrl)
        if (segments.isEmpty()) {
            throw DownloadException("HLS playlist has no segments: $m3u8Url")
        }

        // 3. Resume from the append-state sidecar (validate playlist stability).
        val sidecarFile = hlsSidecarFile(tempFile)
        val sidecar = readHlsSidecar(sidecarFile)
        var appendedThrough = 0
        var initDone = false
        var appendedBytes = 0L
        if (sidecar != null &&
            sidecar.segmentCount == segments.size &&
            sidecar.firstSegmentUrl == segments.first() &&
            sidecar.lastSegmentUrl == segments.last()
        ) {
            appendedThrough = sidecar.appendedThrough.coerceIn(0, segments.size)
            initDone = sidecar.initDone
            appendedBytes = sidecar.appendedBytes
            // Truncate to appendedBytes — a crash between append + sidecar write
            // would otherwise leave extra bytes that get re-appended (corruption).
            if (tempFile.exists()) {
                java.io.RandomAccessFile(tempFile, "rw").use { it.setLength(appendedBytes) }
            }
            DownloadLogger.i {
                "HLS parallel resume — $appendedThrough/${segments.size} segments appended " +
                    "($appendedBytes bytes), initDone=$initDone"
            }
        } else {
            if (sidecar != null) sidecarFile.delete() // playlist changed → restart
            tempFile.delete()
        }

        // 4. Fetch the AES key once (all segments share the single key — rotating
        //    keys are rejected in parseEncryptionKey).
        val keyBytes: ByteArray? = if (key != null) fetchKeyBytes(key.uri, headers) else null
        if (key != null && keyBytes == null) {
            throw DownloadException("Failed to fetch HLS AES-128 key: ${key.uri}")
        }

        // 5. Probe the next-unappended segment for an initial estimate.
        var estimatedTotal = -1L // written by the writer, read by the reporter (same-thread write; benign race on read)
        val probeIndex = appendedThrough.coerceAtMost(segments.size - 1)
        val probeSize = probeSegmentSize(segments[probeIndex], headers)
        if (probeSize > 0) {
            estimatedTotal = appendedBytes + probeSize * (segments.size - appendedThrough)
        }

        // 6. Concurrent workers + ordered writer + progress reporter.
        val spillDir = File(tempFile.parentFile, "segments")
        spillDir.mkdirs()
        val workerCount = effectiveWorkerCount()
        // Bound: fetched-but-not-yet-appended segments ≤ workers + 4 (head-of-line
        // write stalls must not accumulate a full episode of spills → 2× disk).
        val spillBound = Semaphore(workerCount + 4)
        val completedSpills = ConcurrentHashMap<Int, File>()
        val fetchedBytes = AtomicLong(0L)
        val cursor = AtomicInteger(appendedThrough)
        val appendBytesRef = AtomicLong(appendedBytes)

        coroutineScope {
            // The ONLY onProgress caller in parallel mode (thread-safety contract).
            val reporter = launch {
                while (true) {
                    delay(REPORT_INTERVAL_MS)
                    onProgress(appendBytesRef.get() + fetchedBytes.get(), estimatedTotal)
                }
            }
            try {
                coroutineScope {
                    // ── Ordered writer: appends strictly in index order. ──
                    launch {
                        FileOutputStream(tempFile, appendedThrough > 0 || initDone).use { out ->
                            var expected = appendedThrough
                            var appendedCount = 0
                            if (!initDone && initSegment != null) {
                                coroutineContext.ensureActive()
                                // Init segments are not AES-encrypted in practice
                                // (EXT-X-MAP) — fetch raw + PNG-strip.
                                val bytes = fetchSegmentBytes(initSegment, headers, keyBytes = null, fixedIv = null, seq = mediaSequence, forInit = true)
                                out.write(bytes)
                                out.flush()
                                appendedBytes += bytes.size
                                appendBytesRef.set(appendedBytes)
                                writeHlsSidecar(sidecarFile, segments, expected, initDone = true, appendedBytes)
                            }
                            while (expected < segments.size) {
                                coroutineContext.ensureActive()
                                val spill = completedSpills.remove(expected)
                                if (spill == null) {
                                    delay(20L)
                                    continue
                                }
                                spill.inputStream().use { it.copyTo(out) }
                                out.flush()
                                appendedBytes += spill.length()
                                appendBytesRef.set(appendedBytes)
                                spill.delete()
                                spillBound.release() // the fetched-not-appended slot frees
                                expected++
                                appendedCount++
                                // REVIEW-5 M32: refine the estimate with the running average.
                                if (appendedCount > 0 && appendedBytes > 0) {
                                    val avg = appendedBytes / (appendedCount + if (initDone) 1 else 0).coerceAtLeast(1)
                                    val refined = avg * segments.size
                                    if (refined > 0) estimatedTotal = refined
                                }
                                // Sidecar AFTER the ordered append (atomic tmp+rename).
                                writeHlsSidecar(sidecarFile, segments, expected, initDone = true, appendedBytes)
                            }
                        }
                    }
                    // ── Workers: pull indices from the shared cursor. ──
                    // NOTE on the semaphore: the WORKER acquires (bounds
                    // fetched-but-not-yet-appended spills); the WRITER releases after
                    // each ordered append (or the worker itself releases on a failed
                    // fetch). withPermit{} here would double-release (runtime crash).
                    repeat(workerCount) {
                        launch {
                            while (true) {
                                val idx = cursor.getAndIncrement()
                                if (idx >= segments.size) return@launch
                                coroutineContext.ensureActive()
                                spillBound.acquire()
                                try {
                                    val bytes = fetchSegmentBytes(
                                        segUrl = segments[idx],
                                        headers = headers,
                                        keyBytes = keyBytes,
                                        fixedIv = key?.iv,
                                        seq = mediaSequence + idx,
                                    )
                                    val spill = File(spillDir, "$idx.ts")
                                    spill.writeBytes(bytes)
                                    completedSpills[idx] = spill
                                    fetchedBytes.addAndGet(bytes.size.toLong())
                                } catch (e: Throwable) {
                                    spillBound.release() // failed fetch — give the slot back
                                    throw e
                                }
                            }
                        }
                    }
                }
            } finally {
                reporter.cancel()
            }
        }

        // 7. Completion: final sample + cleanup.
        onProgress(appendBytesRef.get(), appendBytesRef.get())
        spillDir.deleteRecursively()
        sidecarFile.delete()
        DownloadLogger.i {
            "HLS parallel download complete — ${segments.size} segments, ${tempFile.length()} bytes, " +
                "$workerCount workers${if (key != null) ", AES-128 decrypted" else ""}"
        }
        return tempFile.length()
    }

    /** Scans ALL `#EXT-X-KEY` lines: rejects rotating keys + non-AES-128 methods. */
    private fun parseEncryptionKey(text: String, baseUrl: String): HlsKey? {
        val lines = text.lines().filter { it.startsWith("#EXT-X-KEY") }
        if (lines.isEmpty()) return null
        data class Parsed(val method: String, val resolvedUri: String, val iv: ByteArray?)
        val parsed = lines.mapNotNull { line ->
            val method = Regex("METHOD=([^,\\s]+)").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            if (method.equals("NONE", ignoreCase = true)) return@mapNotNull null
            val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
            val iv = Regex("IV=0[xX]([0-9a-fA-F]+)").find(line)?.groupValues?.get(1)?.let(::hexToBytes)
            Parsed(method, resolveUrl(uri, baseUrl), iv)
        }
        if (parsed.isEmpty()) return null
        if (parsed.size > 1) {
            throw DownloadException(
                "Encrypted HLS with rotating keys (${parsed.size} distinct #EXT-X-KEY lines) — " +
                    "not supported. Try a different server.",
            )
        }
        val single = parsed.first()
        if (!single.method.equals("AES-128", ignoreCase = true)) {
            throw DownloadException(
                "Encrypted HLS with METHOD=${single.method} — only AES-128 is supported " +
                    "(SAMPLE-AES/DRM are not).",
            )
        }
        return HlsKey(single.method, single.resolvedUri, single.iv)
    }

    /** Parses `#EXT-X-MEDIA-SEQUENCE` (default 0 — required for default-IV derivation). */
    private fun parseMediaSequence(text: String): Long =
        Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Fetches the AES-128 key bytes (16 bytes expected). */
    private fun fetchKeyBytes(keyUrl: String, headers: String?): ByteArray? = try {
        val request = buildRequest(keyUrl, headers)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                DownloadLogger.w { "AES key fetch failed (HTTP ${response.code}): $keyUrl" }
                return null
            }
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.size != 16) {
                DownloadLogger.w { "AES key has unexpected size ${bytes?.size ?: -1} (expected 16)" }
            }
            bytes
        }
    } catch (e: Exception) {
        DownloadLogger.w { "AES key fetch error: ${e.message}" }
        null
    }

    /**
     * Fetches one segment to memory with retry (exponential backoff), PNG-strips,
     * then decrypts in memory (AES-128-CBC/NoPadding; IV = attribute or 16-byte
     * big-endian sequence number). No unencrypted bytes touch the disk.
     */
    private suspend fun fetchSegmentBytes(
        segUrl: String,
        headers: String?,
        keyBytes: ByteArray?,
        fixedIv: ByteArray?,
        seq: Long,
        forInit: Boolean = false,
    ): ByteArray {
        var lastError: Exception? = null
        for (attempt in 1..MAX_SEG_RETRIES) {
            try {
                val buffer = ByteArrayOutputStream()
                downloadSegment(segUrl, headers, buffer)
                var bytes = stripPngHeaderIfPresent(buffer.toByteArray())
                if (keyBytes != null && !forInit) {
                    bytes = decryptAes128(bytes, keyBytes, fixedIv, seq)
                }
                return bytes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                DownloadLogger.w {
                    "Segment fetch failed (attempt $attempt/$MAX_SEG_RETRIES): $segUrl — ${e.message}"
                }
                if (attempt < MAX_SEG_RETRIES) {
                    delay(minOf(1000L shl (attempt - 1), MAX_SEG_BACKOFF_MS)) // exponential backoff
                }
            }
        }
        throw DownloadException(
            "Segment failed after $MAX_SEG_RETRIES attempts: $segUrl — ${lastError?.message}",
            lastError,
        )
    }

    /** In-memory AES-128-CBC decryption (NoPadding — HLS segments stay 16-byte aligned). */
    private fun decryptAes128(bytes: ByteArray, keyBytes: ByteArray, fixedIv: ByteArray?, seq: Long): ByteArray {
        if (bytes.isEmpty()) return bytes
        if (bytes.size % 16 != 0) {
            throw DownloadException(
                "AES-128 segment size ${bytes.size} is not 16-byte aligned (sequence $seq) — " +
                    "cannot decrypt (the CDN may be serving wrapped/corrupt data).",
            )
        }
        val iv = fixedIv ?: sequenceIv(seq)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(bytes)
    }

    /** Default IV: the media sequence number as a 16-byte big-endian integer. */
    private fun sequenceIv(seq: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0..7) {
            iv[15 - i] = (seq ushr (8 * i)).toByte()
        }
        return iv
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 1) "0$hex" else hex
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    /** Worker count with the ≤16-connections-per-queue budget. */
    private fun effectiveWorkerCount(): Int {
        val threads = preferences.advancedThreads.get().coerceIn(1, 8)
        val concurrency = preferences.concurrentDownloads.get().coerceIn(1, 5)
        return maxOf(1, minOf(threads, 16 / concurrency))
    }

    // ── Sidecar persistence (atomic: tmp + rename) ───────────────────────────

    private fun hlsSidecarFile(tempFile: File): File =
        File(tempFile.parentFile, tempFile.name + HLS_SIDECAR_SUFFIX)

    private fun readHlsSidecar(file: File): HlsSidecar? = try {
        if (!file.exists()) null
        else json.decodeFromString(HlsSidecar.serializer(), file.readText())
    } catch (e: Exception) {
        DownloadLogger.w { "Failed to read HLS sidecar: ${e.message}" }
        null
    }

    private fun writeHlsSidecar(file: File, segments: List<String>, appendedThrough: Int, initDone: Boolean, appendedBytes: Long) {
        val payload = HlsSidecar(
            segmentCount = segments.size,
            firstSegmentUrl = segments.first(),
            lastSegmentUrl = segments.last(),
            appendedThrough = appendedThrough,
            initDone = initDone,
            appendedBytes = appendedBytes,
        )
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(HlsSidecar.serializer(), payload))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LEGACY MODE (byte-for-byte today's behavior)
    // ═════════════════════════════════════════════════════════════════════════

    private suspend fun downloadToCacheLegacy(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        DownloadLogger.i { "HLS download: $m3u8Url" }

        // 1. Fetch the playlist text.
        val playlistText = fetchText(m3u8Url, headers)
        val baseUrl = m3u8Url

        // 2. Master playlist → pick the first variant.
        val mediaPlaylistText = if (isMasterPlaylist(playlistText)) {
            val variantUrl = pickFirstVariant(playlistText, baseUrl)
            fetchText(variantUrl, headers)
        } else {
            playlistText
        }

        // 3. Encryption check (reject encrypted — needs the parallel engine's AES support).
        if (isEncrypted(mediaPlaylistText)) {
            throw DownloadException(
                "Encrypted HLS stream — the legacy downloader cannot decrypt AES-128. " +
                    "Enable the Advanced downloader in Download settings to download AES-128 streams.",
            )
        }

        // 4. Parse segments + init map.
        val initSegment = parseInitSegment(mediaPlaylistText, baseUrl)
        val segments = parseSegments(mediaPlaylistText, baseUrl)
        if (segments.isEmpty()) {
            throw DownloadException("HLS playlist has no segments: $m3u8Url")
        }

        // 5. REVIEW-5 M32: probe the first segment's size for an initial estimate.
        var estimatedTotal = -1L
        if (segments.isNotEmpty()) {
            val firstSegmentSize = probeSegmentSize(segments.first(), headers)
            if (firstSegmentSize > 0) {
                estimatedTotal = firstSegmentSize * segments.size
            }
        }
        var bytesDownloadedSoFar = 0L
        var segmentsDownloadedSoFar = 0

        // 6. Write the init segment first (if present), then each media segment.
        FileOutputStream(tempFile).use { out ->
            if (initSegment != null) {
                coroutineContext.ensureActive()
                val initSize = downloadSegmentWithRetry(initSegment, headers, out, maxRetries = MAX_SEG_RETRIES)
                bytesDownloadedSoFar += initSize
            }

            for ((index, segUrl) in segments.withIndex()) {
                coroutineContext.ensureActive()
                DownloadLogger.d { "HLS segment ${index + 1}/${segments.size}: $segUrl" }
                val segSize = downloadSegmentWithRetry(segUrl, headers, out, maxRetries = MAX_SEG_RETRIES)
                bytesDownloadedSoFar += segSize
                segmentsDownloadedSoFar += 1

                // REVIEW-5 M32: refine the estimate after each segment using the running
                // average segment size. The estimate converges to the real total.
                if (segmentsDownloadedSoFar > 0) {
                    val avgSegSize = bytesDownloadedSoFar / segmentsDownloadedSoFar
                    val refined = avgSegSize * segments.size
                    if (refined > 0) estimatedTotal = refined
                }
                // REVIEW-5 §11.4: byte-count-based progress (was segment-count-based).
                onProgress(tempFile.length(), estimatedTotal)
            }
        }

        DownloadLogger.i {
            "HLS download complete — ${segments.size} segments, ${tempFile.length()} bytes"
        }
        return tempFile.length()
    }

    // ── Playlist fetch + parse (shared by both modes) ────────────────────────

    /** Fetches the playlist text. */
    private fun fetchText(url: String, headers: String?): String {
        val request = buildRequest(url, headers)
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code} for HLS playlist: $url")
            }
            response.body?.string() ?: throw DownloadException("Empty HLS playlist response: $url")
        }
    }

    /** Returns true if [text] is a master playlist (contains `#EXT-X-STREAM-INF`). */
    private fun isMasterPlaylist(text: String): Boolean =
        text.contains("#EXT-X-STREAM-INF")

    /**
     * Picks the FIRST variant URL from a master playlist (typically the highest
     * bandwidth). The OLD project does the same — no quality picker for HLS in D.1.
     */
    private fun pickFirstVariant(text: String, baseUrl: String): String {
        val lines = text.lines()
        for (i in lines.indices) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                // The next non-comment line is the variant URL.
                for (j in i + 1 until lines.size) {
                    val line = lines[j].trim()
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        return resolveUrl(line, baseUrl)
                    }
                }
            }
        }
        throw DownloadException("Master playlist has no variant URL: $baseUrl")
    }

    /** Returns true if the playlist contains `#EXT-X-KEY` with a non-NONE METHOD. */
    private fun isEncrypted(text: String): Boolean {
        val regex = Regex("#EXT-X-KEY:.*METHOD=([^,\\s]+)")
        return regex.find(text)?.groupValues?.get(1)?.let { method ->
            !method.equals("NONE", ignoreCase = true)
        } ?: false
    }

    /** Parses `#EXT-X-MAP:URI="..."` (for fMP4/.m4s streams). */
    private fun parseInitSegment(text: String, baseUrl: String): String? {
        val regex = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"")
        return regex.find(text)?.groupValues?.get(1)?.let { resolveUrl(it, baseUrl) }
    }

    /** Parses segment URLs (non-comment lines after `#EXTINF` or `#EXT-X-BYTERANGE`). */
    private fun parseSegments(text: String, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = text.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF") || line.startsWith("#EXT-X-BYTERANGE")) {
                // The next non-comment line is the segment URL.
                for (j in i + 1 until lines.size) {
                    val segLine = lines[j].trim()
                    if (segLine.isNotEmpty() && !segLine.startsWith("#")) {
                        segments.add(resolveUrl(segLine, baseUrl))
                        break
                    }
                }
            }
        }
        return segments
    }

    // ── Segment download (with retry + PNG-header stripping) ─────────────────

    /**
     * Downloads a single segment with retry (legacy sequential path). Returns the
     * number of bytes written to `out`.
     *
     * REVIEW-5 M33: downloads each attempt to a [ByteArrayOutputStream] FIRST + writes
     * to `out` only on success. The OLD draft wrote the response body directly to `out`
     * inside the retry loop — if a segment partially downloaded then failed, the retry
     * appended the NEW bytes to the partial bytes → corrupt `.ts` output.
     */
    private suspend fun downloadSegmentWithRetry(
        segUrl: String,
        headers: String?,
        out: OutputStream,
        maxRetries: Int,
    ): Long {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val buffer = ByteArrayOutputStream()
                downloadSegment(segUrl, headers, buffer)
                val bytes = stripPngHeaderIfPresent(buffer.toByteArray())
                out.write(bytes)
                out.flush()
                return bytes.size.toLong()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                DownloadLogger.w {
                    "Segment download failed (attempt $attempt/$maxRetries): $segUrl — ${e.message}"
                }
                if (attempt < maxRetries) {
                    delay(1000L * attempt) // linear backoff
                }
            }
        }
        throw DownloadException(
            "Segment failed after $maxRetries attempts: $segUrl — ${lastError?.message}",
            lastError,
        )
    }

    /** Downloads a single segment to [buffer] (no retry). */
    private fun downloadSegment(segUrl: String, headers: String?, buffer: ByteArrayOutputStream) {
        val request = buildRequest(segUrl, headers)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, "HTTP ${response.code} for HLS segment: $segUrl")
            }
            response.body?.byteStream()?.use { it.copyTo(buffer) }
        }
    }

    /**
     * REVIEW-5 M39: probes the segment size using a 1-byte Range GET instead of HEAD.
     *
     * Many anti-scraping CDNs (megaplay.buzz, kotocdn.site) reject HEAD with 405 or
     * return wrong Content-Length. A 1-byte Range GET is a real GET (passes the same
     * anti-scraping checks as the actual segment download) + the Content-Range header
     * reveals the full size.
     */
    private fun probeSegmentSize(segUrl: String, headers: String?): Long = try {
        val request = buildRequest(segUrl, headers, range = "bytes=0-0")
        client.newCall(request).execute().use { response ->
            // Content-Range: bytes 0-0/12345 → 12345 is the full size.
            val contentRange = response.header("Content-Range")
            if (contentRange != null) {
                val match = Regex("\\d+-\\d+/(\\d+)").find(contentRange)
                match?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
            }
            -1L
        }
    } catch (e: Exception) {
        -1L
    }

    /**
     * PNG-header stripping (mirrors the extension's `stripPngHeader`):
     * 1. Check if segment starts with PNG magic bytes (`89 50 4E 47`).
     * 2. Find the `IEND` marker (end of PNG data).
     * 3. Skip 8 bytes after `IEND` (IEND + CRC).
     * 4. Look for the MPEG-TS sync byte (`0x47`) at a position where `0x47` also
     *    appears 188 bytes later (confirming it's a real sync byte).
     * 5. Return everything from that sync byte onward.
     * 6. Fallback: just cut at the IEND+8 position.
     */
    private fun stripPngHeaderIfPresent(bytes: ByteArray): ByteArray {
        if (bytes.size < 4) return bytes
        // PNG magic: 89 50 4E 47
        if (bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte() ||
            bytes[2] != 0x4E.toByte() || bytes[3] != 0x47.toByte()
        ) {
            return bytes
        }
        // Find IEND marker (49 45 4E 44) — followed by 4 bytes of CRC.
        var iendPos = -1
        for (i in 0..bytes.size - 8) {
            if (bytes[i] == 0x49.toByte() && bytes[i + 1] == 0x45.toByte() &&
                bytes[i + 2] == 0x4E.toByte() && bytes[i + 3] == 0x44.toByte()
            ) {
                iendPos = i
                break
            }
        }
        if (iendPos < 0) return bytes
        // Look for the MPEG-TS sync byte (0x47) starting after IEND+8.
        val startSearch = (iendPos + 8).coerceAtMost(bytes.size - 188 - 1)
        for (i in startSearch until bytes.size - 188) {
            if (bytes[i] == 0x47.toByte() && bytes[i + 188] == 0x47.toByte()) {
                return bytes.copyOfRange(i, bytes.size)
            }
        }
        // Fallback: cut at IEND+8.
        val cutAt = (iendPos + 8).coerceAtMost(bytes.size)
        return bytes.copyOfRange(cutAt, bytes.size)
    }

    // ── URL resolution ───────────────────────────────────────────────────────

    /** Resolves a possibly-relative URL against [baseUrl]. */
    private fun resolveUrl(url: String, baseUrl: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return try {
            URI(baseUrl).resolve(url).toString()
        } catch (e: Exception) {
            // Fallback: directory-relative.
            baseUrl.substringBeforeLast('/') + "/" + url
        }
    }

    // ── Request builder ──────────────────────────────────────────────────────

    /** Builds the OkHttp [Request] with optional headers + optional Range. */
    private fun buildRequest(url: String, headers: String?, range: String? = null): Request {
        return Request.Builder().url(url).apply {
            if (range != null) header("Range", range)
            // D-207 FIX: re-add Accept-Encoding: identity for localhost proxy URLs ONLY.
            // See HttpDownloader.buildRequest for the full rationale.
            if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
                header("Accept-Encoding", "identity")
            }
            // D-207 FIX: use the smart parser (DownloadHeaderParser) instead of
            // split('\n'). See HttpDownloader.buildRequest for the full rationale —
            // the split('\n') bug caused Referer/Origin to be swallowed into the
            // User-Agent value → CDN 403 on HLS playlist fetches.
            DownloadHeaderParser.parse(headers).forEach { (name, value) ->
                addHeader(name, value)
            }
        }.build()
    }

    companion object {
        /** Max retry attempts per HLS segment (REVIEW-5 §11.4 — was 1 in the OLD project). */
        private const val MAX_SEG_RETRIES = 3

        /** Sidecar filename suffix next to the temp file (parallel-mode resume state). */
        const val HLS_SIDECAR_SUFFIX = ".hls-state.json"

        /** Progress reporter interval (parallel mode). */
        private const val REPORT_INTERVAL_MS = 250L

        /** Max backoff per segment retry (exponential, parallel mode). */
        private const val MAX_SEG_BACKOFF_MS = 30_000L
    }
}
