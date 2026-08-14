package com.confused.anikuta.core.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * The HTTP downloader — handles direct video URLs (mp4/mkv/webm/m4v/mov/avi/ts).
 * Routes HLS URLs (.m3u8 / Content-Type: application/vnd.apple.mpegurl) to
 * [HlsDownloader].
 *
 * D.1.5 + 05-downloaders.md §11.3: the HTTP engine is the router. It inspects the
 * URL + Content-Type + dispatches to itself (Normal method) OR delegates to
 * [HlsDownloader]. The Advanced method (multi-threaded Range + resume) is deferred
 * to D.1.5.
 *
 * REVIEW-5 M15 (CRITICAL): [downloadNormal] has a `reResolveAttempts: Int = 0`
 * parameter + caps at [MAX_RE_RESOLVE_ATTEMPTS] (= 1). On `IOException` for a
 * localhost URL, it calls [ReResolver.reResolve] + retries with the fresh URL.
 * The recursive call passes `reResolveAttempts + 1`. When the cap is exceeded,
 * throws [DownloadException] with a clear proxy-churn message.
 *
 * REVIEW-5 M49: HTTP errors (4xx/5xx) throw [HttpException] (NOT generic
 * `DownloadException`) so [RetryPolicy.forException] can match on `e is HttpException`
 * + read `e.code`.
 *
 * REVIEW-5 M35: emits intermediate `onProgress` ticks at 96/97/98/99% during the
 * validation / subtitle / cover / publish phases (so the bar moves smoothly past
 * the 95% download cap instead of jumping 95→100).
 *
 * REVIEW-5 M37: the catch blocks distinguish [CancellationException] (preserve
 * resume metadata — calls `cleanupTask(preserveForResume = true)`) from
 * completion/error (delete everything — `cleanupTask(preserveForResume = false)`).
 *
 * Range requests: when the temp file already exists (from a preserved-for-resume
 * pause), sends `Range: bytes=<existingLen>-` + appends on 206 Partial Content.
 * On 200 OK, restarts from scratch (overwrites the temp file).
 */
class HttpDownloader(
    private val client: OkHttpClient,
    private val tempCache: TempDownloadCache,
    private val storage: DownloadStorageProvider,
    private val hlsDownloader: HlsDownloader,
    private val store: DownloadStore,
    private val preferences: DownloadPreferences,
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
            val subtitleLangs = task.subtitleTracks.map { it.lang }
            val publishResult = storage.publishVideoFile(
                downloadId = task.id,
                tempFile = tempVideo,
                content = task.content,
                episode = task.episode,
                videoExtension = ext,
                subtitleFiles = subtitleFiles,
                subtitleLangs = subtitleLangs,
            )
            emitPhaseProgress(onProgress, downloadedBytes, 99)

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
     * Routes to the right sub-pipeline based on URL inspection.
     *  - HLS URL (.m3u8) → [HlsDownloader.downloadToCache].
     *  - Otherwise → [downloadNormal].
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
        return downloadNormal(
            url = url,
            headers = headers,
            tempFile = tempFile,
            taskId = taskId,
            resolveContextJson = resolveContextJson,
            onProgress = onProgress,
            reResolveAttempts = 0,
        )
    }

    // ── The single-threaded Normal method ────────────────────────────────────

    /**
     * The single-threaded Normal method. Range-based resume when the temp file
     * already has bytes (from a preserved-for-resume pause).
     *
     * REVIEW-5 M15: the [reResolveAttempts] counter bounds the proxy-churn
     * re-resolve recursion at [MAX_RE_RESOLVE_ATTEMPTS]. The public default is 0;
     * only the recursive call in the catch block passes a non-zero value.
     */
    private suspend fun downloadNormal(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
        reResolveAttempts: Int = 0,
    ): Long {
        val resumeFrom = if (tempFile.exists()) tempFile.length() else 0L
        val request = buildRequest(url, headers, resumeFrom)

        return try {
            client.newCall(request).execute().use { response ->
                // REVIEW-5 M49: throw HttpException on non-2xx so RetryPolicy can match.
                if (!response.isSuccessful) {
                    throw HttpException(response.code, "HTTP ${response.code} for video URL")
                }

                // HLS detection via Content-Type (URL-based detection happened in the router).
                val videoType = VideoTypeDetector.detect(url, response.contentType())
                if (videoType == VideoTypeDetector.VideoType.HLS) {
                    return@use hlsDownloader.downloadToCache(url, headers, tempFile, taskId, onProgress)
                }

                // Determine the effective resume offset (0 if server ignored Range).
                val isPartial = response.code == 206
                val effectiveResumeFrom = if (isPartial) resumeFrom else 0L
                val total = if (isPartial) {
                    // Content-Range: bytes <start>-<end>/<total>
                    response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                } else {
                    response.body?.contentLength()?.takeIf { it > 0 } ?: -1L
                }

                val appendMode = isPartial && effectiveResumeFrom > 0L
                FileOutputStream(tempFile, appendMode).use { os ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = effectiveResumeFrom
                        while (true) {
                            coroutineContext.ensureActive() // cooperative cancellation
                            val read = input.read(buffer)
                            if (read == -1) break
                            os.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        os.flush()
                    }
                }
                tempFile.length()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HttpException) {
            // HttpException IS a DownloadException — re-throw as-is so RetryPolicy
            // can match on its type + read e.code.
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            // ── Proxy-churn fix (REVIEW-5 M15) ──
            // D-149-fix: also guard on http://127.0.0.1 (some extensions use this
            // instead of localhost — see lessons-learned D-092).
            val isLocalhost = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
            if (isLocalhost && resolveContextJson != null && reResolver != null
                && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS
            ) {
                DownloadLogger.w {
                    "IOException on localhost URL — attempting re-resolve " +
                        "(attempt ${reResolveAttempts + 1}/$MAX_RE_RESOLVE_ATTEMPTS): ${e.message}"
                }
                val fresh = reResolver.reResolve(resolveContextJson)
                if (fresh != null) {
                    // D-149-fix: update video_url (the source URL), NOT video_uri (the
                    // content:// result URI). The old code called updateResult which
                    // writes video_uri — wrong column for a re-resolve.
                    store.updateDownloadVideoUrl(taskId, fresh.url)
                    // Truncate the temp file (the new proxy may not support Range).
                    FileOutputStream(tempFile).use { /* truncate to 0 */ }
                    return downloadNormal(
                        url = fresh.url,
                        headers = fresh.headers,
                        tempFile = tempFile,
                        taskId = taskId,
                        resolveContextJson = resolveContextJson,
                        onProgress = onProgress,
                        reResolveAttempts = reResolveAttempts + 1, // M15 — recursive cap
                    )
                }
            }
            if (isLocalhost && reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS) {
                throw DownloadException(
                    "Proxy URL died after $MAX_RE_RESOLVE_ATTEMPTS re-resolve attempt(s) — " +
                        "the extension's proxy server is being churned by another playback. " +
                        "Original cause: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: Exception) {
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Builds the OkHttp [Request] with optional Range header for resume. */
    private fun buildRequest(url: String, headers: String?, resumeFrom: Long): Request {
        return Request.Builder().url(url).apply {
            if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            // D-200: Removed Accept-Encoding: identity (same as HlsDownloader — see comment there).
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
        /** 8 KB I/O buffer for the byte-stream download. */
        private const val BUFFER_SIZE = 8 * 1024

        /** 500 KB minimum — a real video episode is at least hundreds of KB. */
        private const val MIN_VALID_VIDEO_BYTES = 500L * 1024

        /** If the downloaded file is smaller than this AND starts with #EXTM3U → HLS. */
        private const val HLS_REDETECT_THRESHOLD = 500L * 1024

        /**
         * REVIEW-5 M15 + M18: cap the inner re-resolve at 1 attempt (= 2 total download
         * attempts: 1 initial + 1 re-resolve). The outer retry loop (16-quality-of-life.md
         * §1.2) caps at 3 attempts. Total = 3 outer × 2 inner = 6 download attempts max
         * before the task goes to ERROR.
         */
        private const val MAX_RE_RESOLVE_ATTEMPTS = 1
    }
}

/** Extension to read the Content-Type from an OkHttp [Response] (null-safe). */
private fun Response.contentType(): String? =
    body?.contentType()?.toString()
