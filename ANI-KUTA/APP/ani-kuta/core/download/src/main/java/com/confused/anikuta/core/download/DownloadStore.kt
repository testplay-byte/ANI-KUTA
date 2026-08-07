package com.confused.anikuta.core.download

import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray

/**
 * Thin adapter between SQLDelight (`download_queue` + `downloaded_episode` tables) and
 * the Kotlin data classes ([DownloadTask] + [DownloadedEpisode]).
 *
 * D.1.2 + 11-db-schema.md §4: the DB is a CACHE/INDEX. The `data.json` files in the
 * user's SAF folder are the durable source of truth for reinstall recognition (the
 * `DownloadScanner` reconciles the DB from `data.json` on startup).
 *
 * REVIEW-5 M9/M11: `setRetryingStatus` + `setErrorStatus` are exposed as separate
 * methods so the queue's retry loop can update them atomically. Both delegate to the
 * SQLDelight queries of the same name.
 *
 * JSON column handling (REVIEW-5 §11.1): `video_headers`, `subtitle_tracks`,
 * `audio_tracks`, `subtitle_uris`, `resolve_context`, `recent_ratios_json` are
 * encoded/decoded via [kotlinx.serialization.json.Json]. The serializer is configured
 * with `encodeDefaults = true` + `ignoreUnknownKeys = true` for forward-compat.
 *
 * The [DownloadContentInfo] / [DownloadEpisodeInfo] composites are flattened into the
 * row columns on insert + re-assembled on read. The [DownloadContentInfo.contentFormat]
 * + [DownloadContentInfo.contentType] fields aren't stored on `download_queue` (the
 * canonical content metadata lives in the `content` table — see
 * `ContentRepository.upsertFromDataJson`); we restore them from the defaults on read.
 */
class DownloadStore(private val database: AnikutaDatabase) {

    private val queueQueries get() = database.downloadQueueQueries
    private val episodeQueries get() = database.downloadedEpisodeQueries

    // ── Queue operations ─────────────────────────────────────────────────────

    /**
     * Inserts a new [DownloadRequest] as a QUEUED task. Returns the new row ID.
     *
     * REVIEW-5 §13.2: the schema's `insertDownloadQueue` query hardcodes `state='QUEUED'`,
     * `progress=0`, `downloaded_bytes=0`, `total_bytes=-1`. We retrieve the auto-generated
     * `id` by re-querying the (main_id, episode_key) tuple (UNIQUE constraint guarantees
     * one match).
     */
    fun insertTask(request: DownloadRequest): Long {
        val now = System.currentTimeMillis()
        val content = request.content
        val episode = request.episode
        queueQueries.insertDownloadQueue(
            main_id = content.mainId,
            episode_key = episode.episodeKey,
            content_id = content.contentId,
            content_title = content.title,
            episode_number = episode.episodeNumber.toDouble(),
            episode_name = episode.name,
            cover_url = content.coverUrl,
            cover_color = content.coverColor?.toLong(),
            source_id = request.sourceId,
            video_server = request.videoServer,
            video_quality = request.videoQuality,
            video_audio = request.videoAudio,
            video_url = request.videoUrl,
            video_headers = request.videoHeaders,
            video_uri = null,
            subtitle_tracks = encodeTrackList(request.subtitleTracks),
            audio_tracks = encodeTrackList(request.audioTracks),
            subtitle_uris = null,
            retry_max_attempts = DEFAULT_RETRY_MAX,
            resolve_context = request.resolveContext,
            queued_at = now,
        )
        return queueQueries.getDownloadTaskByMainAndEpisode(content.mainId, episode.episodeKey)
            .executeAsOne()
            .id
    }

    /**
     * Persists the full mutable state of [task] to the DB. Calls the three
     * update queries (state, progress, video_uri) sequentially. Callers are
     * expected to hold the queue's mutex when invoking this (no per-statement
     * transaction wrapper is used — the queue's mutex serializes writes).
     */
    fun updateTask(task: DownloadTask) {
        val now = System.currentTimeMillis()
        queueQueries.updateDownloadState(
            state = task.status.name,
            progress = task.progress.toLong(),
            error_message = task.lastError,
            started_at = task.startedAt,
            completed_at = task.completedAt,
            updated_at = now,
            id = task.id,
        )
        queueQueries.updateDownloadProgress(
            progress = task.progress.toLong(),
            downloaded_bytes = task.downloadedBytes,
            total_bytes = task.totalBytes,
            prev_total_bytes = 0L,
            prev_estimate_bytes = 0L,
            recent_ratios_json = null,
            updated_at = now,
            id = task.id,
        )
        queueQueries.updateDownloadVideoUri(
            video_uri = task.videoUri,
            subtitle_uris = task.subtitleUris,
            updated_at = now,
            id = task.id,
        )
    }

    /** Updates just the task's status + timestamps + error message. */
    fun updateState(
        id: Long,
        status: DownloadStatus,
        progress: Int,
        startedAt: Long?,
        completedAt: Long?,
        errorMessage: String?,
    ) {
        queueQueries.updateDownloadState(
            state = status.name,
            progress = progress.toLong(),
            error_message = errorMessage,
            started_at = startedAt,
            completed_at = completedAt,
            updated_at = System.currentTimeMillis(),
            id = id,
        )
    }

    /**
     * Updates the byte-progress + the persisted DynamicProgressTracker state.
     *
     * REVIEW-5 M38: `prevTotal` / `prevEstimate` / `recentRatios` are persisted across
     * pause/resume so the progress bar doesn't jump backward.
     */
    fun updateProgress(
        id: Long,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        prevTotal: Long,
        prevEstimate: Long,
        recentRatios: List<Float>,
    ) {
        queueQueries.updateDownloadProgress(
            progress = progress.toLong(),
            downloaded_bytes = downloadedBytes,
            total_bytes = totalBytes,
            prev_total_bytes = prevTotal,
            prev_estimate_bytes = prevEstimate,
            recent_ratios_json = encodeFloatList(recentRatios),
            updated_at = System.currentTimeMillis(),
            id = id,
        )
    }

    /** Sets the result URIs on a COMPLETED task. */
    fun updateResult(id: Long, videoUri: String?, subtitleUris: List<String>) {
        queueQueries.updateDownloadVideoUri(
            video_uri = videoUri,
            subtitle_uris = encodeStringList(subtitleUris),
            updated_at = System.currentTimeMillis(),
            id = id,
        )
    }

    /** REVIEW-5 M9+M11: sets RETRYING status + retry metadata. */
    fun setRetryingStatus(id: Long, attempt: Int, maxAttempts: Int, errorMessage: String?) {
        queueQueries.setRetryingStatus(
            attempt = attempt.toLong(),
            error_message = errorMessage,
            updated_at = System.currentTimeMillis(),
            id = id,
        )
    }

    /** REVIEW-5 M11: sets ERROR status + error message. */
    fun setErrorStatus(id: Long, errorMessage: String?) {
        queueQueries.setErrorStatus(
            error_message = errorMessage,
            updated_at = System.currentTimeMillis(),
            id = id,
        )
    }

    /** Deletes a queue task by ID. */
    fun deleteTask(id: Long) {
        queueQueries.deleteDownloadQueue(id)
    }

    /**
     * Returns all active (non-terminal) tasks in FIFO order (queued_at ASC).
     *
     * REVIEW-D0 I5: includes ERROR + RETRYING (the schema's `getDownloadQueue` query
     * returns QUEUED + DOWNLOADING + RETRYING + PAUSED + ERROR — COMPLETED + CANCELLED
     * are excluded).
     */
    fun getActiveTasks(): List<DownloadTask> =
        queueQueries.getDownloadQueue().executeAsList().map { it.toTask() }

    /** Returns the task for (mainId, episodeKey), or null if no row matches. */
    fun getTaskByMainAndEpisode(mainId: String, episodeKey: String): DownloadTask? =
        queueQueries.getDownloadTaskByMainAndEpisode(mainId, episodeKey)
            .executeAsOneOrNull()
            ?.toTask()

    /**
     * Returns the persisted DynamicProgressTracker state for [id] (REVIEW-5 M38 —
     * restored on resume so the bar doesn't jump backward). Returns `null` if no row.
     */
    fun getTrackerState(id: Long): TrackerState? {
        val row = queueQueries.getDownloadTaskById(id).executeAsOneOrNull() ?: return null
        return TrackerState(
            prevTotal = row.prev_total_bytes,
            prevEstimate = row.prev_estimate_bytes.toInt(),
            recentRatios = decodeFloatList(row.recent_ratios_json),
        )
    }

    /**
     * The persisted DynamicProgressTracker state — restored on resume (REVIEW-5 M38).
     *
     * @param prevTotal The previous tick's `displayTotalBytes` (for the no-backward-jump rule).
     * @param prevEstimate The previous tick's smoothed progress (0..95).
     * @param recentRatios The moving-average window (max 5 entries).
     */
    data class TrackerState(
        val prevTotal: Long,
        val prevEstimate: Int,
        val recentRatios: List<Float>,
    )

    /**
     * Resets DOWNLOADING + RETRYING tasks to QUEUED on startup.
     *
     * REVIEW-5 M6: a task that crashed mid-download (DOWNLOADING) or mid-retry (RETRYING)
     * is reset to QUEUED so the engine can pick it back up. PAUSED + COMPLETED + ERROR
     * + CANCELLED + QUEUED are left untouched.
     */
    fun resetDownloadingToQueued() {
        queueQueries.resetDownloadingToQueued(System.currentTimeMillis())
    }

    // ── Downloaded-episode operations ────────────────────────────────────────

    /**
     * Inserts (or replaces) a [DownloadedEpisode] row.
     *
     * The `insertDownloadedEpisode` query uses `INSERT OR REPLACE` — safe to call for
     * both fresh inserts + re-publishes (the (main_id, episode_key) PK handles dedup).
     */
    fun insertDownloadedEpisode(episode: DownloadedEpisode) {
        val content = episode.content
        val ep = episode.episode
        episodeQueries.insertDownloadedEpisode(
            main_id = content.mainId,
            episode_key = ep.episodeKey,
            content_id = content.contentId,
            content_title = content.title,
            content_format = content.contentFormat,
            content_type = content.contentType,
            episode_number = ep.episodeNumber.toDouble(),
            episode_name = ep.name,
            cover_url = content.coverUrl,
            cover_color = content.coverColor?.toLong(),
            content_folder_uri = episode.videoUri.substringBeforeLast('/'),
            file_path = episode.videoUri,
            file_size = episode.sizeBytes,
            quality = episode.quality,
            video_uri = episode.videoUri,
            video_file_name = episode.videoUri.substringAfterLast('/'),
            subtitle_uris = encodeStringList(episode.subtitleUris),
            source_id = null,
            video_server = null,
            video_audio = null,
            verified_at = System.currentTimeMillis(),
            downloaded_at = episode.completedAt,
        )
    }

    /** Returns all downloaded episodes, newest first (downloaded_at DESC). */
    fun getDownloadedEpisodes(): List<DownloadedEpisode> =
        episodeQueries.getAllDownloadedEpisodes().executeAsList().map { it.toDownloadedEpisode() }

    /** Returns true if (mainId, episodeKey) is in the downloaded_episode table. */
    fun isEpisodeDownloaded(mainId: String, episodeKey: String): Boolean =
        episodeQueries.isEpisodeDownloaded(mainId, episodeKey).executeAsOne()

    /** Returns the content:// URI of a downloaded video, or null if not downloaded. */
    fun getDownloadedVideoUri(mainId: String, episodeKey: String): String? =
        episodeQueries.getDownloadedEpisode(mainId, episodeKey).executeAsOneOrNull()?.video_uri

    /** Deletes a single downloaded episode row. */
    fun deleteDownloadedEpisode(mainId: String, episodeKey: String) {
        episodeQueries.deleteDownloadedEpisode(mainId, episodeKey)
    }

    /**
     * Marks an episode as missing (deletes the row). Called by [DownloadScanner] when
     * `data.json` says an episode should be here but the file is missing on disk.
     */
    fun markEpisodeMissing(mainId: String, episodeKey: String) {
        episodeQueries.deleteDownloadedEpisode(mainId, episodeKey)
    }

    /** Purges CANCELLED tasks (called periodically to keep the queue table lean). */
    fun purgeCancelled() {
        queueQueries.purgeCancelled()
    }

    /** Purges COMPLETED tasks (called after the auto-clear delay). */
    fun purgeCompleted() {
        queueQueries.purgeCompleted()
    }

    // ── JSON column helpers ──────────────────────────────────────────────────

    private fun encodeTrackList(tracks: List<DownloadTrack>): String? =
        if (tracks.isEmpty()) null else JSON.encodeToString(tracks)

    private fun decodeTrackList(json: String?): List<DownloadTrack> =
        if (json.isNullOrBlank()) emptyList()
        else try { JSON.decodeFromString<List<DownloadTrack>>(json) } catch (e: Exception) {
            DownloadLogger.w { "Failed to decode DownloadTrack list — treating as empty: ${e.message}" }
            emptyList()
        }

    private fun encodeStringList(list: List<String>): String? =
        if (list.isEmpty()) null else JSON.encodeToString(ListSerializer(String.serializer()), list)

    private fun decodeStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList()
        else try { JSON.decodeFromString<List<String>>(json) } catch (e: Exception) {
            DownloadLogger.w { "Failed to decode String list — treating as empty: ${e.message}" }
            emptyList()
        }

    private fun encodeFloatList(list: List<Float>): String? =
        if (list.isEmpty()) null else JSON.encodeToString(ListSerializer(Float.serializer()), list)

    private fun decodeFloatList(json: String?): List<Float> =
        if (json.isNullOrBlank()) emptyList()
        else try { JSON.decodeFromString<List<Float>>(json) } catch (e: Exception) {
            DownloadLogger.w { "Failed to decode Float list — treating as empty: ${e.message}" }
            emptyList()
        }

    // ── Row → Model mappers ──────────────────────────────────────────────────

    private fun com.confused.anikuta.core.database.Download_queue.toTask(): DownloadTask {
        val content = DownloadContentInfo(
            mainId = main_id,
            contentId = content_id,
            title = content_title,
            coverUrl = cover_url,
            coverColor = cover_color?.toInt(),
            // Not stored on download_queue — use defaults (the canonical content metadata
            // lives in the `content` table; DownloadScanner keeps it in sync).
            contentFormat = "video",
            contentType = "anime",
        )
        val episode = DownloadEpisodeInfo(
            episodeKey = episode_key,
            episodeNumber = episode_number.toFloat(),
            name = episode_name,
        )
        val status = runCatching { DownloadStatus.valueOf(state) }.getOrDefault(DownloadStatus.QUEUED)
        return DownloadTask(
            id = id,
            content = content,
            episode = episode,
            videoUrl = video_url,
            videoHeaders = video_headers,
            videoUri = video_uri,
            subtitleTracks = decodeTrackList(subtitle_tracks),
            audioTracks = decodeTrackList(audio_tracks),
            subtitleUris = subtitle_uris,
            sourceId = source_id,
            videoServer = video_server ?: "",
            videoQuality = video_quality ?: "",
            videoAudio = video_audio ?: "",
            status = status,
            progress = progress.toInt(),
            downloadedBytes = downloaded_bytes,
            totalBytes = total_bytes,
            resolveContext = resolve_context,
            retryAttempt = retry_attempt.toInt(),
            retryMaxAttempts = retry_max_attempts.toInt(),
            lastError = last_error,
            queuedAt = queued_at,
            startedAt = started_at,
            completedAt = completed_at,
        )
    }

    private fun com.confused.anikuta.core.database.Downloaded_episode.toDownloadedEpisode(): DownloadedEpisode {
        val content = DownloadContentInfo(
            mainId = main_id,
            contentId = content_id,
            title = content_title,
            coverUrl = cover_url,
            coverColor = cover_color?.toInt(),
            contentFormat = content_format,
            contentType = content_type,
        )
        val episode = DownloadEpisodeInfo(
            episodeKey = episode_key,
            episodeNumber = episode_number.toFloat(),
            name = episode_name,
        )
        return DownloadedEpisode(
            content = content,
            episode = episode,
            videoUri = video_uri ?: file_path,
            subtitleUris = decodeStringList(subtitle_uris),
            sizeBytes = file_size,
            quality = quality,
            completedAt = downloaded_at,
        )
    }

    companion object {
        /** Default retry max attempts (used when the request doesn't specify one). */
        private const val DEFAULT_RETRY_MAX = 3L

        /** The shared JSON instance for column encode/decode. */
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
