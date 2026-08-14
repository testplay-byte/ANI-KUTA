package com.confused.anikuta.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import kotlin.coroutines.coroutineContext

/**
 * The HLS downloader — pure Kotlin (no ffmpeg). Fetches the `.m3u8` playlist,
 * resolves master playlists to the first variant, parses segments, downloads each
 * segment with retry, and concatenates them into a single `.ts` file.
 *
 * D.1.6 + 05-downloaders.md §11.4:
 *  - REVIEW-5 M32: `estimatedTotal` is REFINED after each segment using the running
 *    average segment size. The OLD draft computed it once + never refined — for
 *    variable-bitrate HLS (ad segments tiny, action scenes large), the estimate
 *    could be off by 2-5x and the bar hit the 95% cap at 50% actual download.
 *  - REVIEW-5 M33: `downloadSegmentWithRetry` downloads each attempt to a
 *    [ByteArrayOutputStream] FIRST + writes to `out` only on success (avoids the
 *    partial-bytes-then-append corruption the OLD draft had).
 *  - REVIEW-5 M39: `probeSegmentSize` uses a 1-byte Range GET (NOT HEAD —
 *    anti-scraping CDNs like megaplay.buzz / kotocdn.site reject HEAD with 405).
 *  - Byte-count-based progress (not segment-count-based) per D.1.7 — the bar
 *    advances smoothly per byte instead of jumping per segment.
 *
 * Encrypted HLS (DRM/AES-128) is rejected — the default downloader can't decrypt.
 *
 * PNG-header stripping: some CDNs prepend a PNG image header to each HLS segment
 * to prevent direct downloading. The extension's `LocalProxyServer` strips this
 * header before serving to MPV; the downloader must do the same — otherwise the
 * concatenated `.ts` file starts with PNG magic bytes and is rejected by the
 * downstream validation.
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
     * @param headers HTTP headers (newline-separated `"Key: Value"`), applied to every
     *   request (playlist + segment fetches).
     * @param tempFile The output file (will be created/overwritten).
     * @param taskId The download task ID (for logging + temp cache).
     * @param onProgress Called after each segment with `(tempFile.length(), estimatedTotal)`.
     */
    suspend fun downloadToCache(
        m3u8Url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        onProgress: (Long, Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
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

        // 3. Encryption check (reject encrypted — needs ffmpeg).
        if (isEncrypted(mediaPlaylistText)) {
            throw DownloadException(
                "Encrypted HLS stream — the default downloader cannot decrypt DRM/AES-128. " +
                    "Use a server that provides an unencrypted stream, or wait for the ffmpeg-based " +
                    "downloader (planned for a future release).",
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
                // average segment size. `estimatedTotal = avgSegSize * totalSegmentCount`.
                // The estimate converges to the real total.
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
        tempFile.length()
    }

    // ── Playlist fetch + parse ───────────────────────────────────────────────

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
     * Downloads a single segment with retry. Returns the number of bytes written to `out`.
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
            // D-200: Removed Accept-Encoding: identity — some CDNs flag it as a bot
            // signal (real browsers never send it). OkHttp handles gzip transparently.
            if (!headers.isNullOrBlank()) {
                headers.split('\n').forEach { line ->
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        addHeader(line.substring(0, sep).trim(), line.substring(sep + 1).trim())
                    }
                }
            }
        }.build()
    }

    companion object {
        /** Max retry attempts per HLS segment (REVIEW-5 §11.4 — was 1 in the OLD project). */
        private const val MAX_SEG_RETRIES = 3
    }
}
