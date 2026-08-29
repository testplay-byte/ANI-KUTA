package com.confused.anikuta.core.download

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * The single-connection byte-transfer strategy — [HttpDownloader.downloadNormal]
 * extracted VERBATIM (test-feature branch: Parallel Download Engine, PLAN.md B.3).
 *
 * Behavior is byte-for-byte today's legacy path:
 *  - Range-based resume when the temp file already has bytes (`Range: bytes=<len>-`,
 *    206 → append; 200 → restart from scratch).
 *  - Content-Type HLS second-chance detection → delegates to [HlsDownloader].
 *  - Proxy-churn re-resolve on HttpException (incl. 403) + IOException for localhost
 *    URLs (D-149/D-194/D-207) — capped at [MAX_RE_RESOLVE_ATTEMPTS], recursive.
 *  - 8 KB read loop + `ensureActive()` cooperative cancellation.
 *  - HttpException on non-2xx (RetryPolicy type-matching — REVIEW-5 M49).
 *
 * NEW (engine-switch safety, PLAN.md B.4 #2): if a leftover PARALLEL-ENGINE sidecar
 * exists (`.chunks` / `.hls-state.json` / `segments/`), the temp file is a SPARSE
 * pre-allocated file whose `length()` ≠ valid contiguous bytes — delete everything
 * + restart clean (otherwise the legacy resume would publish a file with holes).
 */
class SingleConnectionFetcher(
    private val client: OkHttpClient,
    private val hlsDownloader: HlsDownloader,
    private val store: DownloadStore,
    private val reResolver: HttpDownloader.ReResolver?,
) : VideoFetcher {

    override suspend fun fetch(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        // Engine-switch guard: a parallel-engine sidecar means the temp file is
        // sparse — its length can't be trusted for Range resume. Restart clean
        // (sidecars + spill dir too — a leftover sidecar would re-trigger this
        // guard on every pause/resume otherwise).
        if (hasParallelSidecars(tempFile)) {
            DownloadLogger.w {
                "SingleConnectionFetcher — parallel sidecar detected; temp file is sparse. " +
                    "Deleting + restarting clean (engine switch)."
            }
            tempFile.delete()
            val dir = tempFile.parentFile
            if (dir != null) {
                File(dir, tempFile.name + ParallelHttpFetcher.SIDECAR_SUFFIX).delete()
                File(dir, tempFile.name + HlsDownloader.HLS_SIDECAR_SUFFIX).delete()
                File(dir, "segments").deleteRecursively()
            }
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

    /**
     * The single-threaded Normal method (extracted verbatim from HttpDownloader).
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
            // D-207 FIX: on HTTP errors (esp. 403) for localhost proxy URLs, attempt
            // re-resolve BEFORE re-throwing. The extension's proxy URL contains a
            // token that may be IP-bound / session-bound / expired. Re-resolving
            // gets a fresh token from the extension → retry.
            // Task 48 (CS downloads): short-TTL CloudStream links (linkRotates)
            // self-heal through the same path when they 403.
            val isLocalhost = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
            val linkRotates = resolveContextLinkRotates(resolveContextJson)
            if ((isLocalhost || linkRotates) && resolveContextJson != null && reResolver != null
                && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS
            ) {
                DownloadLogger.w {
                    "HttpException ${e.code} on ${if (isLocalhost) "localhost" else "rotating"} URL — attempting re-resolve " +
                        "(attempt ${reResolveAttempts + 1}/$MAX_RE_RESOLVE_ATTEMPTS): ${e.message}"
                }
                val fresh = reResolver.reResolve(resolveContextJson)
                if (fresh != null) {
                    store.updateDownloadVideoUrl(taskId, fresh.url)
                    FileOutputStream(tempFile).use { /* truncate to 0 */ }
                    return downloadNormal(
                        url = fresh.url,
                        headers = fresh.headers,
                        tempFile = tempFile,
                        taskId = taskId,
                        resolveContextJson = resolveContextJson,
                        onProgress = onProgress,
                        reResolveAttempts = reResolveAttempts + 1,
                    )
                }
            }
            // HttpException IS a DownloadException — re-throw as-is so RetryPolicy
            // can match on its type + read e.code.
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            // ── Proxy-churn fix (REVIEW-5 M15) ──
            // D-149-fix: also guard on http://127.0.0.1 (some extensions use this
            // instead of localhost — see lessons-learned D-092).
            // Task 48 (CS downloads): short-TTL CloudStream links (linkRotates)
            // self-heal through the same path.
            val isLocalhost = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
            val linkRotates = resolveContextLinkRotates(resolveContextJson)
            if ((isLocalhost || linkRotates) && resolveContextJson != null && reResolver != null
                && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS
            ) {
                DownloadLogger.w {
                    "IOException on ${if (isLocalhost) "localhost" else "rotating"} URL — attempting re-resolve " +
                        "(attempt ${reResolveAttempts + 1}/$MAX_RE_RESOLVE_ATTEMPTS): ${e.message}"
                }
                val fresh = reResolver.reResolve(resolveContextJson)
                if (fresh != null) {
                    // D-149-fix: update video_url (the source URL), NOT video_uri (the
                    // content:// result URI).
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
            if ((isLocalhost || linkRotates) && reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS) {
                throw DownloadException(
                    "Source URL died after $MAX_RE_RESOLVE_ATTEMPTS re-resolve attempt(s) — " +
                        "the link expired (rotating host) or the extension's proxy server is being " +
                        "churned by another playback. Original cause: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: Exception) {
            throw DownloadException("Video download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Builds the OkHttp [Request] with optional Range header for resume (copied from HttpDownloader). */
    private fun buildRequest(url: String, headers: String?, resumeFrom: Long): Request {
        return Request.Builder().url(url).apply {
            if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            // D-207 FIX: Accept-Encoding: identity for localhost proxy URLs ONLY
            // (see HttpDownloader.buildRequest for the full rationale).
            if (url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")) {
                header("Accept-Encoding", "identity")
            }
            // D-207 FIX: the smart parser handles commas INSIDE values (e.g. the UA's
            // "(KHTML, like Gecko)") — a naive split(',') swallows Referer/Origin.
            DownloadHeaderParser.parse(headers).forEach { (name, value) ->
                addHeader(name, value)
            }
        }.build()
    }

    companion object {
        /** 8 KB I/O buffer (same as HttpDownloader's legacy path). */
        private const val BUFFER_SIZE = 8 * 1024

        /** REVIEW-5 M15 + M18: cap the inner re-resolve at 1 attempt. */
        private const val MAX_RE_RESOLVE_ATTEMPTS = 1
    }
}

/** True when parallel-engine resume state exists next to [tempFile] (engine-switch guard). */
internal fun hasParallelSidecars(tempFile: File): Boolean {
    val dir = tempFile.parentFile ?: return false
    val chunksSidecar = File(dir, tempFile.name + ParallelHttpFetcher.SIDECAR_SUFFIX)
    val hlsSidecar = File(dir, tempFile.name + HlsDownloader.HLS_SIDECAR_SUFFIX)
    return chunksSidecar.exists() || hlsSidecar.exists() || File(dir, "segments").exists()
}

/** Extension to read the Content-Type from an OkHttp [Response] (null-safe — copied from HttpDownloader). */
private fun Response.contentType(): String? =
    body?.contentType()?.toString()
