package com.confused.anikuta.core.playbackcache

import java.security.MessageDigest

/**
 * Models + utilities for the video playback cache.
 *
 * Video Caching plan: DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md (Part A).
 */

/**
 * The stable identity of a playable video. Carries everything needed to (a)
 * derive a stable cache key, (b) populate the settings-screen display, and
 * (c) refresh the upstream URL/headers on each play.
 *
 * CRITICAL: [episodeNumber] must ALWAYS come from the LIVE episode state
 * (PlayerStateHolder.currentEpisodeNumber / the new episode at switch time),
 * never from a frozen WatchKey — otherwise episode N+1's bytes would be filed
 * under episode N's cache key (wrong-content replay corruption).
 *
 * @param serverKey "server|audio|quality" — ResolverVideo.videoTitle minus the
 *    volatile urlHash segment (see ResolverTypes.kt: videoTitle is the codebase's
 *    documented stable-identity string; URLs are NOT stable across resolves).
 */
data class PlaybackVideoId(
    val mainId: String,
    val animeTitle: String,
    val episodeNumber: Float,
    val episodeTitle: String,
    val sourceId: Long,
    val serverKey: String,
    val quality: String,
) {
    /** Stable cache key: sha256 of the identity tuple (hex, 32 chars is enough). */
    val cacheKey: String by lazy {
        val raw = buildString {
            append(mainId).append('\u001F')
            append(episodeNumber.toDouble()).append('\u001F')
            append(sourceId).append('\u001F')
            append(serverKey)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}

/**
 * Derives the [PlaybackVideoId.serverKey] from a ResolverVideo.videoTitle.
 *
 * videoTitle format: "$server|$audio|$quality|$urlHash" (VideoResolver.buildVideoTitle).
 * The urlHash segment is volatile (URL changes each resolve) — drop it.
 * Defensive: if the title doesn't have the expected shape, use it as-is (a stable
 *-but-different key at worst → cache miss, never corruption).
 */
fun serverKeyFromVideoTitle(videoTitle: String): String {
    if (videoTitle.count { it == '|' } >= 3) {
        return videoTitle.substringBeforeLast('|')
    }
    return videoTitle
}

/** An inclusive byte range [start, endInclusive]. */
data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

/**
 * Merged, sorted byte-range set operations for the cache file.
 * Serialization format: "a-b,c-d" (empty string = nothing cached).
 */
object CacheRanges {

    fun parse(serialized: String): List<ByteRange> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val idx = part.indexOf('-')
                if (idx <= 0) return@mapNotNull null
                val start = part.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val end = part.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
                if (end < start) return@mapNotNull null
                ByteRange(start, end)
            }
            .sortedBy { it.start }
    }

    fun serialize(ranges: List<ByteRange>): String =
        merge(ranges).joinToString(",") { "${it.start}-${it.endInclusive}" }

    /** Merges overlapping/adjacent ranges into a sorted, disjoint list. */
    fun merge(ranges: List<ByteRange>): List<ByteRange> {
        if (ranges.size <= 1) return ranges.sortedBy { it.start }
        val sorted = ranges.sortedBy { it.start }
        val out = mutableListOf<ByteRange>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= cur.endInclusive + 1) {
                // Overlapping or adjacent — extend current.
                if (next.endInclusive > cur.endInclusive) {
                    cur = ByteRange(cur.start, next.endInclusive)
                }
            } else {
                out.add(cur)
                cur = next
            }
        }
        out.add(cur)
        return out
    }

    fun totalBytes(ranges: List<ByteRange>): Long = ranges.sumOf { it.length }

    /**
     * Returns the requested [start, end] span split into cached and gap sub-ranges,
     * in ascending order. [end] is inclusive; use Long.MAX_VALUE for "to EOF".
     */
    fun splitSpan(ranges: List<ByteRange>, start: Long, end: Long): List<SpanPart> {
        if (end < start) return emptyList()
        val parts = mutableListOf<SpanPart>()
        var cursor = start
        for (r in merge(ranges)) {
            if (r.endInclusive < cursor) continue
            if (r.start > end) break
            if (r.start > cursor) {
                val gapEnd = minOf(r.start - 1, end)
                if (gapEnd >= cursor) parts.add(SpanPart(cursor, gapEnd, cached = false))
            }
            val hitStart = maxOf(cursor, r.start)
            val hitEnd = minOf(end, r.endInclusive)
            if (hitEnd >= hitStart) parts.add(SpanPart(hitStart, hitEnd, cached = true))
            cursor = hitEnd + 1
            if (cursor > end) break
        }
        if (cursor <= end) parts.add(SpanPart(cursor, end, cached = false))
        return parts
    }

    /**
     * The contiguous cached prefix starting at 0 (the "cached from the start to X"
     * point shown in the settings screen). Returns 0 if byte 0 isn't cached.
     */
    fun contiguousPrefixEnd(ranges: List<ByteRange>): Long {
        val merged = merge(ranges)
        val first = merged.firstOrNull() ?: return 0L
        if (first.start != 0L) return 0L
        return first.endInclusive + 1
    }
}

/** A sub-range of a requested span: either served from the cache file or a gap. */
data class SpanPart(val start: Long, val endInclusive: Long, val cached: Boolean) {
    val length: Long get() = endInclusive - start + 1
}

/**
 * Parses MPV-format header strings ("Key: Value,Key2: Value2") into pairs.
 *
 * Modeled on :core:download's DownloadHeaderParser (which is private to that
 * module): a regex per entry so commas INSIDE values (e.g. the UA string's
 * "(KHTML, like Gecko)") don't split an entry into garbage. A naive split(',')
 * would swallow Referer/Origin into the User-Agent (D-207 lesson class).
 */
object MpvHeaderParser {

    private val entryRegex = Regex("^[A-Za-z][A-Za-z0-9-]*:")

    fun parse(headers: String?): List<Pair<String, String>> {
        if (headers.isNullOrBlank()) return emptyList()
        return headers.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && entryRegex.containsMatchIn(it) }
            .mapNotNull { entry ->
                val idx = entry.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val key = entry.substring(0, idx).trim()
                val value = entry.substring(idx + 1).trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
    }
}

/**
 * Serializes external track lists into the WatchKey wire format: one
 * "url\u001Flang" pair per line. Stored on the cache entry so tap-to-play can
 * rebuild a full WatchKey (external subs/audio survive cache-origin playback).
 */
fun serializeTracks(tracks: List<Pair<String, String>>): String =
    tracks.joinToString("\n") { "${it.first}\u001F${it.second}" }

/**
 * Task 48 (per-track subtitle headers): header-aware overload — appends the
 * MPV csv header string ("Key: Value,…") as an OPTIONAL third field. Tracks
 * without headers serialize EXACTLY like the legacy format, so every parser
 * (WatchKey.parseTracks, deserializeTracks) keeps working unchanged.
 *
 * @JvmName is required: after erasure both overloads would share the JVM
 * signature serializeTracks(Ljava/util/List;)Ljava/lang/String;.
 */
@JvmName("serializeTracksWithHeaders")
fun serializeTracks(tracks: List<Triple<String, String, String?>>): String =
    tracks.joinToString("\n") { (url, lang, headers) ->
        if (headers.isNullOrBlank()) "$url\u001F$lang" else "$url\u001F$lang\u001F$headers"
    }

/** Parses the [serializeTracks] format back into pairs. */
fun deserializeTracks(serialized: String): List<Pair<String, String>> {
    if (serialized.isBlank()) return emptyList()
    return serialized.lines().mapNotNull { line ->
        val idx = line.indexOf('\u001F')
        if (idx <= 0) return@mapNotNull null
        val url = line.substring(0, idx)
        val lang = line.substring(idx + 1)
        if (url.isBlank()) null else url to lang
    }
}
