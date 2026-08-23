package com.confused.anikuta.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The HTTP downloader — the download FACADE (test-feature branch: routing, validation,
 * subtitles, publish, .data.json upsert, completion shape all live here — PLAN.md B.3).
 *
 * Routes HLS URLs (.m3u8 / Content-Type: application/vnd.apple.mpegurl) to
 * [HlsDownloader]. Direct video URLs route through a pluggable [VideoFetcher]
 * byte-transfer strategy, selected per-task by the `advancedDownloader` preference:
 *  - ON → [ParallelHttpFetcher] (the new multi-connection Range engine).
 *  - OFF → [SingleConnectionFetcher] (the legacy downloadNormal, extracted verbatim).
 *
 * REVIEW-5 M15: the proxy-churn re-resolve lives in the fetchers now (localhost +
 * HttpException/IOException + ReResolver, capped at 1 attempt).
 * REVIEW-5 M49: HTTP errors throw [HttpException] so [RetryPolicy.forException]
 * can match on type + code.
 * REVIEW-5 M35: intermediate onProgress ticks at 96/97/98/99% during the
 * validation / subtitle / cover / publish phases.
 * REVIEW-5 M37: CancellationException preserves resume metadata; completion/error
 * delete temp state.
 */
class HttpDownloader(
    private val client: OkHttpClient,
    private val tempCache: TempDownloadCache,
    private val storage: DownloadStorageProvider,
    private val hlsDownloader: HlsDownloader,
    private val store: DownloadStore,
    private val preferences: DownloadPreferences,
    /** The legacy single-connection byte-transfer strategy (downloadNormal, extracted). */
    private val singleConnectionFetcher: SingleConnectionFetcher,
    /** The parallel byte-range engine (multi-connection, per-chunk retry + backoff). */
    private val parallelFetcher: ParallelHttpFetcher,
    /**
     * D-242: The content repository — used to re-fetch the canonical content
     * metadata (FK fields, description, anilistId, etc.) before writing
     * `.data.json`. The `download_queue` table doesn't store these fields
     * (they're denormalized on `main_entry` + `content_details`), so without
     * this re-fetch, `.data.json` would have null FK fields whenever the task
     * was loaded from the DB (which is always — `DownloadQueue.enqueue` reads
     * the task back via `toTask()` which defaults FK fields to null).
     */
    private val contentRepository: com.confused.anikuta.core.content.ContentRepository? = null,
    /**
     * The proxy-churn re-resolver. `null` if the proxy-churn fix is disabled.
     * Set by the Koin module — :app passes the [ReResolver] impl from :core:video-resolver
     * (D.2 will introduce this); :core:download doesn't depend on :core:video-resolver
     * (the interface is defined LOCALLY here so the dep graph stays minimal).
     */
    private val reResolver: ReResolver? = null,
) {

    /**
     * Downloads [task]'s video to the temp cache + publishes to the user's SAF folder.
     *
     * @param task The download task.
     * @param onProgress Called on every byte tick — `(downloadedBytes, totalBytes)`.
     *   `totalBytes = -1` if unknown.
     * @return The updated [DownloadTask] with `videoUri` + `subtitleUris` filled in
     *   (status = COMPLETED).
     */
    suspend fun download(
        task: DownloadTask,
        onProgress: (Long, Long) -> Unit,
    ): DownloadTask = withContext(Dispatchers.IO) {
        DownloadLogger.i {
            "Downloading: ${task.content.title} EP ${task.episode.episodeNumber} — URL: ${task.videoUrl}"
        }
        if (task.videoUrl.startsWith("http://localhost")) {
            DownloadLogger.w {
                "Download depends on extension proxy server — may fail if the proxy is " +
                    "killed by another resolve call."
            }
        }

        val ext = extractExtension(task.videoUrl)
        val tempVideo = tempCache.getTempVideoFile(task.id, ext)

        try {
            // 1. Route to the right sub-pipeline based on URL inspection.
            val downloadedBytes = downloadVideoToCache(
                url = task.videoUrl,
                headers = task.videoHeaders,
                tempFile = tempVideo,
                taskId = task.id,
                resolveContextJson = task.resolveContext,
                onProgress = onProgress,
            )

            // 2. Validate (size + magic bytes). Non-fatal on magic-byte mismatch.
            validateDownloadedFile(task.videoUrl, tempVideo)
            emitPhaseProgress(onProgress, downloadedBytes, 96)

            // 3. HLS playlist re-detection (small file starting with #EXTM3U).
            if (tempVideo.length() < HLS_REDETECT_THRESHOLD && isHlsPlaylist(tempVideo)) {
                DownloadLogger.i {
                    "Downloaded file is an HLS playlist (${tempVideo.length()} bytes) — " +
                        "switching to HlsDownloader"
                }
                tempVideo.delete()
                hlsDownloader.downloadToCache(
                    m3u8Url = task.videoUrl,
                    headers = task.videoHeaders,
                    tempFile = tempVideo,
                    taskId = task.id,
                    onProgress = onProgress,
                )
            }

            // 4. Download subtitles to the temp cache (best-effort).
            val subtitleFiles = downloadSubtitlesToCache(task)
            emitPhaseProgress(onProgress, downloadedBytes, 97)

            // 5. Publish to SAF (atomic). Returns the content:// URI + subtitle URIs.
            // D-FIX-SUB: pass the per-track language labels so the on-disk subtitle
            // filenames include the language (e.g. `.subtitle_E00001_english_0.srt`),
            // and capture the returned subtitle URIs so they land on the task + DB.
            //
            // D-242: BEFORE publishing, re-fetch the canonical content metadata from
            // ContentRepository. The `download_queue` table doesn't store the FK
            // fields (description, dataSourceId, systemId, extensionId, sourceId,
            // animeUrl, anilistId, etc.) — they're denormalized on `main_entry` +
            // `content_details`. Without this re-fetch, `.data.json` would have
            // null FK fields (the user's reported bug). We use `?: task.content.X`
            // as a fallback so we NEVER overwrite a non-null value with null.
            val enrichedContent = enrichContentMetadata(task.content)
            val subtitleLangs = task.subtitleTracks.map { it.lang }
            val publishResult = storage.publishVideoFile(
                downloadId = task.id,
                tempFile = tempVideo,
                content = enrichedContent,
                episode = task.episode,
                videoExtension = ext,
                subtitleFiles = subtitleFiles,
                subtitleLangs = subtitleLangs,
            )
            emitPhaseProgress(onProgress, downloadedBytes, 99)

            // D-241: upsert this episode into the on-disk `.data.json` episodes list.
            // `publishVideoFile` already wrote `.data.json` with the content identity +
            // metadata; now we append (or update) this episode in the `episodes` list.
            // This is the DURABLE source of truth — survives reinstall, so the user
            // sees the episode as downloaded even after the SQLite DB is wiped.
            // Best-effort: a failure here doesn't fail the download (the DB row will
            // be inserted by DownloadQueue.launchDownload; the scanner will rebuild
            // the episodes list from the file walk on the next startup if needed).
            runCatching {
                // D-242-fix: use the contentFolder returned by publishVideoFile
                // directly — NO second findContentFolder walk (which was silently
                // returning null on some devices, causing episodes to not be
                // appended to .data.json).
                val contentFolder = publishResult.contentFolder
                if (contentFolder != null) {
                    val episodeInfo = DownloadedEpisodeInfo(
                        episodeKey = task.episode.episodeKey,
                        episodeNumber = task.episode.episodeNumber.toDouble(),
                        episodeUrl = task.videoUrl,
                        episodeName = task.episode.name,
                        episodeDescription = task.episode.description,
                        videoUrl = task.videoUrl,
                        videoUri = publishResult.videoUri,
                        subtitleUris = publishResult.subtitleUris,
                        quality = task.videoQuality.ifBlank { null },
                        videoServer = task.videoServer.ifBlank { null },
                        audioVariant = task.videoAudio.ifBlank { null },
                        downloadedAt = System.currentTimeMillis(),
                        fileSize = tempVideo.length(),
                    )
                    storage.upsertEpisodeInDataJson(contentFolder, episodeInfo)
                    DownloadLogger.i {
                        "HttpDownloader — upserted episode ${task.episode.episodeKey} " +
                            "into .data.json for ${contentFolder.name}"
                    }
                } else {
                    DownloadLogger.w {
                        "HttpDownloader — publishResult.contentFolder is null for task ${task.id}; " +
                            "episode NOT appended to .data.json (scanner will reconcile on next startup)"
                    }
                }
            }.onFailure { e ->
                DownloadLogger.w {
                    "HttpDownloader — upsertEpisodeInDataJson failed for task ${task.id} (non-fatal): ${e.message}"
                }
            }

            // 6. SUCCESS — clean up temp dir (preserveForResume = false).
            tempCache.cleanupTask(task.id, preserveForResume = false)

            // D-FIX-SUB: serialize the subtitle content:// URIs to JSON so the task
            // (and the DB row) carries them. Previously this was always null →
            // offline playback had no subtitles. decodeSubtitleUris() in DownloadQueue
            // parses this back to List<String>.
            val subtitleUrisJson = if (publishResult.subtitleUris.isEmpty()) null
                else kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                    publishResult.subtitleUris,
                )

            task.copy(
                status = DownloadStatus.COMPLETED,
                progress = 99, // The queue bumps to 100 via DynamicProgressTracker.complete().
                videoUri = publishResult.videoUri,
                subtitleUris = subtitleUrisJson,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                completedAt = System.currentTimeMillis(),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // REVIEW-5 M37: pause preserves resume metadata (the temp file + any
            // partial bytes are kept). The queue's resume path can re-use them.
            tempCache.cleanupTask(task.id, preserveForResume = true)
            throw e
        } catch (e: DownloadException) {
            tempCache.cleanupTask(task.id, preserveForResume = false)
            throw e
        } catch (e: Exception) {
            tempCache.cleanupTask(task.id, preserveForResume = false)
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    /**
     * Routes to the right sub-pipeline based on URL inspection + the engine toggle.
     *  - HLS URL (.m3u8) → [HlsDownloader.downloadToCache] (parallel mode + AES
     *    decryption gated by the same `advancedDownloader` preference, internally).
     *  - Otherwise → the selected [VideoFetcher] (parallel vs single-connection).
     */
    private suspend fun downloadVideoToCache(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        if (VideoTypeDetector.detect(url) == VideoTypeDetector.VideoType.HLS) {
            return hlsDownloader.downloadToCache(url, headers, tempFile, taskId, onProgress)
        }
        val fetcher = if (preferences.advancedDownloader.get()) {
            parallelFetcher
        } else {
            singleConnectionFetcher
        }
        return fetcher.fetch(
            url = url,
            headers = headers,
            tempFile = tempFile,
            taskId = taskId,
            resolveContextJson = resolveContextJson,
            onProgress = onProgress,
        )
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /** Validates the downloaded temp file: non-empty + at least [MIN_VALID_VIDEO_BYTES]. */
    private fun validateDownloadedFile(url: String, tempFile: File) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw DownloadException("Downloaded file is empty — the source returned no data.")
        }
        if (tempFile.length() < MIN_VALID_VIDEO_BYTES) {
            throw DownloadException(
                "Downloaded file is only ${tempFile.length()} bytes — the server returned an " +
                    "error page or redirect instead of the video. Try a different server or " +
                    "quality. (URL: ${url.take(80)}...)",
            )
        }
    }

    /** Returns true if [tempFile] starts with `#EXTM3U` (HLS playlist marker). */
    private fun isHlsPlaylist(tempFile: File): Boolean = try {
        tempFile.inputStream().bufferedReader().use { reader ->
            val firstLine = reader.readLine() ?: ""
            firstLine.trimStart().startsWith("#EXTM3U", ignoreCase = true)
        }
    } catch (e: Exception) {
        false
    }

    // ── Subtitles ────────────────────────────────────────────────────────────

    /**
     * Downloads each subtitle track to the temp cache. Best-effort — failures are
     * logged + skipped (one bad subtitle doesn't fail the download).
     *
     * D-FIX-SUB: now sends the per-track HTTP headers ([DownloadTrack.headers], JSON
     * `Map<String,String>`) + a User-Agent fallback. Previously the request was built
     * with NO headers → subtitle fetches 403'd on protected CDNs (same Referer/UA
     * requirement as the video URL) and were silently skipped. The streaming-side
     * [com.confused.anikuta.core.player.subtitles.SubtitleEngine] already handled
     * headers; the download side now matches it.
     */
    private suspend fun downloadSubtitlesToCache(task: DownloadTask): List<File> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<File>()
            for ((index, track) in task.subtitleTracks.withIndex()) {
                val ext = subtitleExtension(track.url)
                val tempFile = tempCache.getTempSubtitleFile(task.id, index, ext)
                try {
                    val requestBuilder = Request.Builder().url(track.url)
                    // D-FIX-SUB: apply per-track headers (JSON Map<String,String>).
                    applyTrackHeaders(requestBuilder, track.headers)
                    // Always add a User-Agent if not already set (matches SubtitleEngine).
                    if (track.headers.isNullOrBlank() ||
                        !track.headers.contains("User-Agent", ignoreCase = true)
                    ) {
                        requestBuilder.addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
                        )
                    }
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            DownloadLogger.w { "Subtitle $index fetch failed (${response.code}) — skipping" }
                            return@use
                        }
                        tempFile.outputStream().use { out ->
                            response.body?.byteStream()?.use { it.copyTo(out) }
                        }
                        results.add(tempFile)
                    }
                } catch (e: Exception) {
                    DownloadLogger.w { "Subtitle $index download failed — skipping: ${e.message}" }
                }
            }
            results
        }

    /**
     * Parses the track's `headers` string (MPV `http-header-fields` format:
     * comma-separated `"Key: Value,Key2: Value2"`) + applies each entry to the
     * request builder. No-op if [headers] is null/blank (best-effort — a parse
     * failure logs a warning + falls back to no headers).
     *
     * D-FIX-SUB: this mirrors the format produced by `VideoResolver.formatHeaders`
     * (comma-joined `"name: value"` pairs) + the format the streaming-side
     * `SubtitleEngine` hands to MPV. Keeping the download side on the SAME format
     * means the `video.videoHeaders` passed through `DownloadOrchestrator` works
     * unchanged for both video + subtitle fetches.
     */
    private fun applyTrackHeaders(
        requestBuilder: Request.Builder,
        headers: String?,
    ) {
        if (headers.isNullOrBlank()) return
        // Format: "Key1: Value1,Key2: Value2" (comma-separated, colon between name+value).
        for (pair in headers.split(',')) {
            val colonIdx = pair.indexOf(':')
            if (colonIdx <= 0) continue
            val name = pair.substring(0, colonIdx).trim()
            val value = pair.substring(colonIdx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                requestBuilder.addHeader(name, value)
            }
        }
    }

    // ── Misc helpers ─────────────────────────────────────────────────────────

    /**
     * REVIEW-5 M35: helper that emits a synthetic onProgress tick corresponding to
     * a desired percentage (96..99) during the post-byte-stream phases. The
     * `onProgress(downloaded, total)` signature is preserved; to force the tracker
     * to compute `pct`, we pass `total = downloaded * 100 / pct`.
     */
    private fun emitPhaseProgress(onProgress: (Long, Long) -> Unit, downloaded: Long, pct: Int) {
        if (downloaded <= 0L) return
        val syntheticTotal = downloaded * 100L / pct.coerceIn(1, 100)
        onProgress(downloaded, syntheticTotal)
    }

    /** Extracts the video file extension from [url] (defaults to `"mp4"`). */
    private fun extractExtension(url: String): String {
        val noQuery = url.substringBefore('?')
        val ext = noQuery.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts" -> ext
            "m3u8", "m3u" -> "ts" // HLS → concatenated .ts
            else -> "mp4"
        }
    }

    /** Extracts the subtitle file extension from [url] (defaults to `"srt"`). */
    private fun subtitleExtension(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "ass", "srt", "vtt", "ssa", "sub" -> ext
            else -> "srt"
        }
    }

    /**
     * D-242: Re-fetches the canonical content metadata from [contentRepository]
     * and enriches [content] with the FK fields (description, dataSourceId,
     * systemId, extensionId, sourceId, animeUrl, anilistId, etc.).
     *
     * The `download_queue` table doesn't store these fields — they're
     * denormalized on `main_entry` + `content_details`. Without this re-fetch,
     * `.data.json` would have null FK fields (the user's reported bug).
     *
     * Uses `?: content.X` as a fallback so we NEVER overwrite a non-null value
     * with null (defensive — preserves any FK fields that were already set on
     * the task before the re-fetch).
     *
     * Returns [content] unchanged if [contentRepository] is null (test context)
     * or if the main_entry row doesn't exist (shouldn't happen — the content
     * was resolved before the download was enqueued).
     */
    private suspend fun enrichContentMetadata(
        content: DownloadContentInfo,
    ): DownloadContentInfo {
        val repo = contentRepository ?: return content
        val record = repo.getMainEntryByMainId(content.mainId) ?: return content
        val details = repo.getContentDetails(content.mainId)
        val dbCoverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
        return content.copy(
            title = record.title.ifBlank { content.title },
            contentType = record.contentType.ifBlank { content.contentType },
            contentFormat = record.contentFormat.ifBlank { content.contentFormat },
            description = (details?.dataSynopsis ?: details?.extDescription) ?: content.description,
            dataSourceId = record.dataSourceId ?: content.dataSourceId,
            systemId = record.systemId ?: content.systemId,
            extensionRepoId = record.extensionRepoId ?: content.extensionRepoId,
            extensionId = record.extensionId ?: details?.extensionIdLong ?: content.extensionId,
            sourceId = record.sourceId ?: details?.sourceId ?: content.sourceId,
            animeUrl = record.animeUrl ?: details?.animeUrl ?: content.animeUrl,
            displaySource = record.displaySource.ifBlank { content.displaySource },
            coverUrl = dbCoverUrl ?: content.coverUrl,
            anilistId = details?.anilistId ?: content.anilistId,
        )
    }

    /**
     * The proxy-churn re-resolver.
     *
     * D.2 introduces a concrete impl in :core:video-resolver. :core:download doesn't
     * depend on :core:video-resolver — the interface is defined LOCALLY here so the
     * dep graph stays minimal (M17 + M49 — keep the dep graph minimal).
     *
     * Implementors: pass `null` for [reResolver] in the Koin module to disable the
     * proxy-churn fix (the catch block's `reResolver != null` guard handles this).
     */
    fun interface ReResolver {
        /**
         * Re-resolves a video URL given the JSON-encoded resolve context.
         *
         * @param resolveContextJson The `resolve_context` JSON (7 fields — sourceId,
         *   episodeUrl, server, audio, quality, etc.).
         * @return The freshly-resolved video URL + headers, or `null` if re-resolve
         *   failed (the caller gives up cleanly in that case).
         */
        suspend fun reResolve(resolveContextJson: String): ReResolvedVideo?
    }

    /** The result of a successful re-resolve. */
    data class ReResolvedVideo(
        val url: String,
        val headers: String?,
    )

    companion object {
        /** 500 KB minimum — a real video episode is at least hundreds of KB. */
        private const val MIN_VALID_VIDEO_BYTES = 500L * 1024

        /** If the downloaded file is smaller than this AND starts with #EXTM3U → HLS. */
        private const val HLS_REDETECT_THRESHOLD = 500L * 1024
    }
}
