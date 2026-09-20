// CLEAN-ROOM: original ANI-KUTA code.
//
// D-539: the CS DASH downloader — the third artifact kind beside the
// progressive HTTP path and the HLS concatenator. Instead of producing a
// single file (which fMP4 DASH cannot be without a muxer the app doesn't
// ship), it caches the manifest + the chosen representations' init/media
// segments into the app-private SimpleCache (media3-datasource), keyed by
// DashCacheKeys — and offline playback rides the SAME cache through
// CsPlayerEngine.startOfflineDash.
//
// Pipeline (one queue task, the same DownloadQueue/notifications/retry
// machinery as every other download):
//  1. fetch the manifest (flattened provider headers) → plan segments
//     (DashManifestPlanner; DRM/live/unaddressable manifests fail HONESTLY);
//  2. cache every part through media3's CacheWriter over a
//     CacheDataSource (createDataSourceForDownloading) — the writer skips
//     already-cached spans natively, so pause→resume re-caches nothing;
//  3. subtitles to temp → SAF publish (storage.publishDashEpisode: .data.json
//     + .cover.jpg + subtitles/, NO video file) → .data.json upsert with the
//     dashManifestUrl marker;
//  4. the completed task's videoUri is the "csdash:<manifestUrl>" marker uri
//     the whole app treats as the DASH-cache identity (playback routers,
//     delete purge, scanner reconstruction).
package com.confused.anikuta.core.download

import com.confused.anikuta.core.common.DashCacheKeys
import com.confused.anikuta.core.content.ContentRepository
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource

class DashDownloader(
    private val client: OkHttpClient,
    private val storage: DownloadStorageProvider,
    private val tempCache: TempDownloadCache,
    private val cache: androidx.media3.datasource.cache.Cache,
    /** D-242 parity with HttpDownloader: re-fetch the canonical FK fields for `.data.json`. */
    private val contentRepository: ContentRepository? = null,
) {

    /**
     * Downloads [task]'s DASH episode into the offline cache.
     *
     * @param task The download task (videoUrl = the manifest URL, videoHeaders
     *   = the MPV-format header string flattened at enqueue time).
     * @param onProgress `(downloadedBytes, totalBytes)`; total is the
     *   bandwidth-derived estimate or -1 when unknowable.
     * @return The COMPLETED task (videoUri = the csdash marker uri).
     */
    suspend fun download(
        task: DownloadTask,
        onProgress: (Long, Long) -> Unit,
    ): DownloadTask = withContext(Dispatchers.IO) {
        val manifestUrl = task.videoUrl
        DownloadLogger.i {
            "DashDownloader — downloading ${task.content.title} EP ${task.episode.episodeNumber} " +
                "manifest=$manifestUrl"
        }
        val headers = DownloadHeaderParser.parse(task.videoHeaders).toMap()

        // ── 1. Fetch + plan the manifest ─────────────────────────────────────
        val manifestXml = try {
            fetchText(manifestUrl, headers)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Could not fetch the DASH manifest: ${e.message ?: e.javaClass.simpleName}", e)
        }
        val plan = DashManifestPlanner.parse(manifestXml, manifestUrl, preferredHeightOf(task.videoQuality))
        if (plan.unsupportedReason != null) {
            // DRM / live / unaddressable — the honest refusal (the user asked
            // for the "No downloadable sources" wall to become a real download;
            // a manifest we genuinely cannot take must still say WHY).
            throw DownloadException(plan.unsupportedReason)
        }
        DownloadLogger.i {
            "DashDownloader — plan: ${plan.parts.size} part(s), video=${plan.videoRep?.id} " +
                "(${plan.videoRep?.height}p @ ${plan.videoRep?.bandwidth}bps), audioReps=${plan.audioRepIds.size}, " +
                "estimate=${plan.estimatedBytes}"
        }

        val totalHint = plan.estimatedBytes.takeIf { it > 0L } ?: -1L

        // ── 2. Cache every part (resume = the writer skips cached spans) ─────
        val upstreamFactory = OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory { dataSpec -> DashCacheKeys.build(manifestUrl, dataSpec.uri.toString()) }

        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "DashDownloader-${task.id}").apply { isDaemon = true }
        }
        var doneBytes = 0L
        try {
            for (part in plan.parts) {
                currentCoroutineContext().ensureActive()
                doneBytes += cachePart(cacheDataSourceFactory, part, headers, totalHint, doneBytes, onProgress, executor)
            }
        } finally {
            executor.shutdownNow()
        }
        DownloadLogger.i { "DashDownloader — cached $doneBytes bytes across ${plan.parts.size} part(s)" }

        // ── 3. Subtitles → temp (best-effort, the D-FIX-SUB header rules) ────
        val subtitleFiles = downloadSubtitlesToCache(task)
        emitPhaseProgress(onProgress, doneBytes, 96)

        // ── 4. Publish: SAF folder with .data.json + cover + subs, NO video ──
        val enrichedContent = enrichContentMetadata(task.content)
        val subtitleLangs = task.subtitleTracks.map { it.lang }
        val publishResult = storage.publishDashEpisode(
            downloadId = task.id,
            content = enrichedContent,
            episode = task.episode,
            subtitleFiles = subtitleFiles,
            subtitleLangs = subtitleLangs,
            manifestUrl = manifestUrl,
            cachedBytes = doneBytes,
        )
        emitPhaseProgress(onProgress, doneBytes, 99)

        runCatching {
            val contentFolder = publishResult.contentFolder
            if (contentFolder != null) {
                val episodeInfo = DownloadedEpisodeInfo(
                    episodeKey = task.episode.episodeKey,
                    episodeNumber = task.episode.episodeNumber.toDouble(),
                    episodeUrl = task.videoUrl,
                    episodeName = task.episode.name,
                    episodeDescription = task.episode.description,
                    videoUrl = task.videoUrl,
                    videoUri = null, // DASH has no published file — the cache key rules
                    subtitleUris = publishResult.subtitleUris,
                    quality = task.videoQuality.ifBlank { null },
                    videoServer = task.videoServer.ifBlank { null },
                    audioVariant = task.videoAudio.ifBlank { null },
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = doneBytes,
                    dashManifestUrl = manifestUrl,
                )
                storage.upsertEpisodeInDataJson(contentFolder, episodeInfo)
                DownloadLogger.i {
                    "DashDownloader — upserted ${task.episode.episodeKey} into .data.json " +
                        "(dashManifestUrl set)"
                }
            } else {
                DownloadLogger.w {
                    "DashDownloader — publishResult.contentFolder null for task ${task.id}; " +
                        "episode NOT appended to .data.json (scanner reconciles on startup)"
                }
            }
        }.onFailure { e ->
            DownloadLogger.w {
                "DashDownloader — upsertEpisodeInDataJson failed for task ${task.id} (non-fatal): ${e.message}"
            }
        }

        tempCache.cleanupTask(task.id, preserveForResume = false)

        val subtitleUrisJson = if (publishResult.subtitleUris.isEmpty()) null
            else kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                publishResult.subtitleUris,
            )

        task.copy(
            status = DownloadStatus.COMPLETED,
            progress = 99, // the queue bumps to 100 via DynamicProgressTracker.complete()
            videoUri = DashCacheKeys.URI_SCHEME + manifestUrl,
            subtitleUris = subtitleUrisJson,
            downloadedBytes = doneBytes,
            totalBytes = doneBytes,
            completedAt = System.currentTimeMillis(),
        )
    }

    // ── part caching ─────────────────────────────────────────────────────────

    /**
     * Caches ONE part on the executor thread. `writer.cancel()` on coroutine
     * cancellation makes media3's cache() throw InterruptedIOException
     * promptly (it polls the cancel flag between reads), so pause/cancel
     * never hangs on a stuck CDN read.
     */
    private suspend fun cachePart(
        factory: CacheDataSource.Factory,
        part: DashPart,
        headers: Map<String, String>,
        totalHint: Long,
        doneBefore: Long,
        onProgress: (Long, Long) -> Unit,
        executor: java.util.concurrent.ExecutorService,
    ): Long = suspendCancellableCoroutine { cont ->
        val dataSpec = DataSpec.Builder()
            .setUri(part.url)
            .setPosition(part.position)
            .setLength(part.length)
            .setHttpRequestHeaders(headers)
            .build()
        var partBytes = 0L
        val writer = CacheWriter(
            factory.createDataSourceForDownloading(),
            dataSpec,
            /* temporaryBuffer = */ null,
        ) { _, bytesCached, _ ->
            if (bytesCached > partBytes) partBytes = bytesCached
            onProgress(doneBefore + bytesCached, totalHint)
        }
        val future = executor.submit {
            try {
                writer.cache()
                cont.resume(partBytes)
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }
        cont.invokeOnCancellation {
            writer.cancel()
            future.cancel(false)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun fetchText(url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("The DASH manifest returned HTTP ${response.code}")
            }
            val body = response.body ?: throw DownloadException("The DASH manifest response was empty")
            return body.string()
        }
    }

    /** "1080p" → 1080; "Auto"/"Unknown"/blank → null (best rep). */
    private fun preferredHeightOf(qualityLabel: String): Int? {
        val digits = qualityLabel.filter { it.isDigit() }
        val h = digits.toIntOrNull() ?: return null
        return h.takeIf { it in 100..4320 }
    }

    private fun emitPhaseProgress(onProgress: (Long, Long) -> Unit, downloaded: Long, pct: Int) {
        if (downloaded <= 0L) return
        val syntheticTotal = downloaded * 100L / pct.coerceIn(1, 100)
        onProgress(downloaded, syntheticTotal)
    }

    /** The D-FIX-SUB subtitle fetch (the HttpDownloader pattern, localized). */
    private suspend fun downloadSubtitlesToCache(task: DownloadTask): List<java.io.File> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<java.io.File>()
            for ((index, track) in task.subtitleTracks.withIndex()) {
                currentCoroutineContext().ensureActive()
                val tempFile = tempCache.getTempSubtitleFile(task.id, index, subtitleExtension(track.url))
                try {
                    val requestBuilder = Request.Builder().url(track.url)
                    DownloadHeaderParser.parse(track.headers).forEach { (name, value) ->
                        requestBuilder.header(name, value)
                    }
                    if (track.headers.isNullOrBlank() ||
                        !track.headers.contains("User-Agent", ignoreCase = true)
                    ) {
                        requestBuilder.header(
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DownloadLogger.w { "Subtitle $index download failed — skipping: ${e.message}" }
                }
            }
            results
        }

    private fun subtitleExtension(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "ass", "srt", "vtt", "ssa", "sub" -> ext
            else -> "srt"
        }
    }

    /** D-242 parity with HttpDownloader: re-fetch the canonical FK fields. */
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
            providerName = content.providerName,
        )
    }
}
