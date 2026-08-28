package com.confused.anikuta.core.playbackcache

import java.io.InputStream

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
 *
 * Session-2: [onBytes] reports forwarded-byte counts to the manager (for
 * last-read-offset tracking that steers the background fill away from the
 * player's read frontier), and [onClose] runs on stream close (flush + fill
 * trigger + deferred-delete check).
 */
internal class SpanInputStream(
    private val parts: List<SpanPart>,
    private val openDisk: (Long, Long) -> InputStream,
    private val openUpstream: (Long, Long) -> InputStream,
    private val onBytes: (Long) -> Unit = {},
    private val onClose: () -> Unit = {},
) : InputStream() {

    private var partIndex = 0
    private var current: InputStream? = null
    private var remainingInPart = 0L
    private var servedTotal = 0L

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
        servedTotal += n
        onBytes(n.toLong())
        return n
    }

    override fun close() {
        closeCurrent()
        onClose()
    }

    private fun closeCurrent() {
        runCatching { current?.close() }
        current = null
    }
}
