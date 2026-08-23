package com.confused.anikuta.core.playbackcache

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.database.Playback_cache_entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed store for playback cache entries (RatingStore pattern).
 *
 * All access is suspend + Dispatchers.IO from coroutines, OR direct synchronous
 * calls from the proxy server's NanoHTTPD worker threads (SQLite/AndroidSqliteDriver
 * is thread-safe; the extension HttpServer precedent in :core:source-api proves
 * the pairing). The proxy wraps its calls in runCatching (SQLite contention
 * degrades to a fail-open redirect, never a crash).
 */
class PlaybackCacheStore(private val database: AnikutaDatabase) {

    private val queries get() = database.playbackCacheQueries

    /** A domain-facing snapshot of one cache entry (ranges parsed). */
    data class Entry(
        val cacheKey: String,
        val mainId: String,
        val animeTitle: String,
        val episodeNumber: Double,
        val episodeTitle: String,
        val sourceId: Long,
        val serverKey: String,
        val quality: String,
        val contentType: String,
        val upstreamUrl: String,
        val upstreamHeaders: String,
        val contentLength: Long?,
        val cachedBytes: Long,
        val cachedRanges: List<ByteRange>,
        val complete: Boolean,
        val createdAt: Long,
        val lastAccessedAt: Long,
        val segmentTotal: Int,
        val segmentsCached: Int,
        val subtitleTracks: String,
        val audioTracks: String,
    ) {
        /** True when this entry is an HLS stream cached as per-segment files. */
        val isHls: Boolean get() = segmentTotal > 0
    }

    suspend fun insert(entry: Entry) = withContext(Dispatchers.IO) { insertSync(entry) }

    /** Synchronous variant for the proxy server's worker threads. */
    fun insertSync(entry: Entry) {
        queries.insertEntry(
            cache_key = entry.cacheKey,
            main_id = entry.mainId,
            anime_title = entry.animeTitle,
            episode_number = entry.episodeNumber,
            episode_title = entry.episodeTitle,
            source_id = entry.sourceId,
            server_key = entry.serverKey,
            quality = entry.quality,
            content_type = entry.contentType,
            upstream_url = entry.upstreamUrl,
            upstream_headers = entry.upstreamHeaders,
            content_length = entry.contentLength,
            cached_bytes = entry.cachedBytes,
            cached_ranges = CacheRanges.serialize(entry.cachedRanges),
            complete = if (entry.complete) 1L else 0L,
            created_at = entry.createdAt,
            last_accessed_at = entry.lastAccessedAt,
            segment_total = entry.segmentTotal.toLong(),
            segments_cached = entry.segmentsCached.toLong(),
            subtitle_tracks = entry.subtitleTracks,
            audio_tracks = entry.audioTracks,
        )
    }

    suspend fun get(cacheKey: String): Entry? = withContext(Dispatchers.IO) { getSync(cacheKey) }

    fun getSync(cacheKey: String): Entry? =
        queries.getEntry(cacheKey).executeAsOneOrNull()?.toDomain()

    /** Refresh upstream URL/headers + track lists (they change every resolve) + LRU touch. Synchronous (proxy thread). */
    fun updateUpstreamSync(
        cacheKey: String,
        url: String,
        headers: String,
        subtitleTracks: String,
        audioTracks: String,
    ) {
        queries.updateUpstream(
            upstream_url = url,
            upstream_headers = headers,
            subtitle_tracks = subtitleTracks,
            audio_tracks = audioTracks,
            last_accessed_at = System.currentTimeMillis(),
            cache_key = cacheKey,
        )
    }

    /** Progress flush. UPDATE-only — a deleted row is never resurrected (tombstone rule). */
    fun updateProgressSync(
        cacheKey: String,
        cachedBytes: Long,
        ranges: List<ByteRange>,
        complete: Boolean,
        contentLength: Long?,
        contentType: String,
    ) {
        queries.updateProgress(
            cached_bytes = cachedBytes,
            cached_ranges = CacheRanges.serialize(ranges),
            complete = if (complete) 1L else 0L,
            content_length = contentLength,
            content_type = contentType,
            cache_key = cacheKey,
        )
    }

    /** HLS segment stats flush (cachedBytes = sum of segment file sizes). */
    fun updateSegmentStatsSync(cacheKey: String, segmentTotal: Int, segmentsCached: Int, cachedBytes: Long, complete: Boolean) {
        queries.updateSegmentStats(
            segments_cached = segmentsCached.toLong(),
            cached_bytes = cachedBytes,
            segment_total = segmentTotal.toLong(),
            complete = if (complete) 1L else 0L,
            cache_key = cacheKey,
        )
    }

    suspend fun delete(cacheKey: String) = withContext(Dispatchers.IO) { deleteSync(cacheKey) }

    /** Synchronous delete (proxy/eviction threads — mirrors insertSync/getSync). */
    fun deleteSync(cacheKey: String) {
        queries.deleteEntry(cacheKey)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        queries.deleteAll()
    }

    /** Reactive list for the settings screen (most recently accessed first). */
    fun observeEntries(): Flow<List<Entry>> =
        queries.listEntries().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    /** Reactive total cached size for the settings screen. */
    fun observeTotalBytes(): Flow<Long> =
        queries.totalCachedBytes().asFlow().mapToOne(Dispatchers.IO)

    /** Synchronous eviction candidates (oldest first). */
    fun listForEvictionSync(): List<Entry> =
        queries.listEntriesForEviction().executeAsList().map { it.toDomain() }

    /** Synchronous total (eviction check on the proxy thread). */
    fun totalBytesSync(): Long =
        queries.totalCachedBytes().executeAsOne()

    private fun Playback_cache_entry.toDomain(): Entry = Entry(
        cacheKey = cache_key,
        mainId = main_id,
        animeTitle = anime_title,
        episodeNumber = episode_number,
        episodeTitle = episode_title,
        sourceId = source_id,
        serverKey = server_key,
        quality = quality,
        contentType = content_type,
        upstreamUrl = upstream_url,
        upstreamHeaders = upstream_headers,
        contentLength = content_length,
        cachedBytes = cached_bytes,
        cachedRanges = CacheRanges.parse(cached_ranges),
        complete = complete == 1L,
        createdAt = created_at,
        lastAccessedAt = last_accessed_at,
        segmentTotal = segment_total.toInt(),
        segmentsCached = segments_cached.toInt(),
        subtitleTracks = subtitle_tracks,
        audioTracks = audio_tracks,
    )
}
