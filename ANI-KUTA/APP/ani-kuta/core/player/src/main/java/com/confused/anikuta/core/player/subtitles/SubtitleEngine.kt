package com.confused.anikuta.core.player.subtitles

import android.content.Context
import com.confused.anikuta.core.common.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Dedicated subtitle engine — downloads external subtitle files to temporary
 * local files, then returns the local file paths for MPV's `sub-add` command.
 *
 * ## Why a dedicated engine?
 *
 * MPV's `sub-add` command can accept URLs, but it has limitations:
 * - It doesn't support custom HTTP headers (Referer, User-Agent) per-request
 * - It can't handle HTTPS with self-signed certs or custom CA bundles
 * - Local proxy URLs (AniKotoS) may be dead by the time `sub-add` runs
 * - Some CDNs return 403 without proper headers
 *
 * By downloading the subtitle file ourselves (using OkHttp with proper headers),
 * we bypass all these issues. MPV's `sub-add` with a local file path ALWAYS works.
 *
 * ## Flow
 *
 * 1. WatchScreen sets `pendingSubtitleTracks` (URL + lang + headers) on PlayerObserver
 * 2. On FILE_LOADED, PlayerObserver calls `SubtitleEngine.downloadSubtitles()`
 * 3. SubtitleEngine downloads each URL to `cacheDir/subtitles/{hash}.vtt`
 * 4. Returns `List<DownloadedSubtitle>` (local file path + lang)
 * 5. PlayerObserver sends `sub-add` with the LOCAL file path
 * 6. MPV loads the subtitle from the local file (always works)
 *
 * ## Cleanup
 *
 * Temp files are cleaned up:
 * - When the player is destroyed (onDispose)
 * - When a new video is loaded (old temp files are deleted)
 * - On app startup (stale files from previous sessions)
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Player:Subtitles".
 */
class SubtitleEngine(
    private val context: Context,
    private val client: OkHttpClient,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Player:Subtitles"
        private const val SUBTITLES_DIR = "subtitles"
    }

    private val subtitlesDir: File by lazy {
        File(context.cacheDir, SUBTITLES_DIR).apply { mkdirs() }
    }

    /**
     * Download a list of subtitle tracks to local temp files.
     *
     * @param tracks List of (url, lang, headers) triples.
     *   - url: the subtitle URL (may be a localhost proxy URL or direct HTTPS)
     *   - lang: the language label (e.g. "English", "Japanese")
     *   - headers: HTTP headers in MPV format ("Key: Value,Key2: Value2") or empty
     * @return List of downloaded subtitles (local file path + lang).
     *   Failed downloads are skipped (not included in the result).
     */
    suspend fun downloadSubtitles(
        tracks: List<SubtitleDownloadRequest>,
    ): List<DownloadedSubtitle> {
        if (tracks.isEmpty()) return emptyList()

        // Clean up old temp files from previous loads.
        cleanupOldFiles()

        val results = mutableListOf<DownloadedSubtitle>()
        for (request in tracks) {
            try {
                val localFile = downloadSingle(request)
                if (localFile != null) {
                    results.add(DownloadedSubtitle(localFile.absolutePath, request.lang))
                    Logger.i(TAG) { "Downloaded subtitle: ${request.lang} → ${localFile.name} (${localFile.length()} bytes)" }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to download subtitle ${request.lang}: ${e.message}" }
            }
        }
        Logger.i(TAG) { "Subtitle download complete: ${results.size}/${tracks.size} successful" }
        return results
    }

    /**
     * Download a single subtitle file.
     *
     * D.FIX: Handles three URL types:
     * 1. `content://` URIs — local downloaded subtitle files (SAF). Copied via
     *    ContentResolver (OkHttp can't open SAF content URIs).
     * 2. `file://` URIs or plain file paths — local temp files. Copied directly.
     * 3. `http://` / `https://` URLs — remote subtitles. Downloaded via OkHttp
     *    with proper headers.
     */
    private suspend fun downloadSingle(request: SubtitleDownloadRequest): File? {
        val url = request.url
        if (url.isBlank()) return null

        // Generate a unique filename based on the URL hash.
        val hash = url.hashCode().toString(16)
        val extension = guessExtension(url)
        val outFile = File(subtitlesDir, "$hash.$extension")

        // If the file already exists (from a previous download in this session),
        // reuse it — don't re-download.
        if (outFile.exists() && outFile.length() > 0) {
            Logger.d(TAG) { "Reusing cached subtitle: ${outFile.name}" }
            return outFile
        }

        // ── Handle content:// URIs (local downloaded subtitles via SAF) ──
        if (url.startsWith("content://")) {
            Logger.i(TAG) { "Loading local subtitle (content://): lang=${request.lang}, uri=${url.take(80)}..." }
            return try {
                val uri = android.net.Uri.parse(url)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Logger.w(TAG) { "ContentResolver returned null stream for: ${url.take(80)}" }
                    return null
                }
                if (outFile.length() > 0) {
                    Logger.i(TAG) { "Loaded local subtitle: ${outFile.name} (${outFile.length()} bytes)" }
                    outFile
                } else {
                    Logger.w(TAG) { "Local subtitle file is empty: ${url.take(80)}" }
                    outFile.delete()
                    null
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to load local subtitle (content://): ${e.message}" }
                outFile.delete()
                null
            }
        }

        // ── Handle file:// URIs or plain file paths (local temp files) ──
        if (url.startsWith("file://") || url.startsWith("/")) {
            val srcFile = File(if (url.startsWith("file://")) android.net.Uri.parse(url).path!! else url)
            Logger.i(TAG) { "Loading local subtitle (file): lang=${request.lang}, path=${srcFile.name}" }
            return try {
                if (srcFile.exists() && srcFile.length() > 0) {
                    srcFile.copyTo(outFile, overwrite = true)
                    if (outFile.length() > 0) outFile else null
                } else {
                    Logger.w(TAG) { "Local subtitle file not found or empty: ${srcFile.absolutePath}" }
                    null
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to copy local subtitle: ${e.message}" }
                null
            }
        }

        // ── Handle HTTP/HTTPS URLs (remote subtitles via OkHttp) ──
        // Build the request with headers.
        val requestBuilder = Request.Builder().url(url)
        if (request.headers.isNotBlank()) {
            // Parse "Key: Value,Key2: Value2" format and add as headers.
            for (headerPair in request.headers.split(",")) {
                val colonIdx = headerPair.indexOf(":")
                if (colonIdx > 0) {
                    val name = headerPair.substring(0, colonIdx).trim()
                    val value = headerPair.substring(colonIdx + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        requestBuilder.addHeader(name, value)
                    }
                }
            }
        }
        // Always add a User-Agent if not already set.
        if (request.headers.indexOf("User-Agent", ignoreCase = true) < 0) {
            requestBuilder.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            )
        }

        Logger.i(TAG) { "Downloading remote subtitle: lang=${request.lang}, url=${url.take(80)}..." }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Logger.w(TAG) { "Subtitle download failed: HTTP ${response.code} for ${url.take(60)}" }
                response.close()
                return null
            }
            val body = response.body ?: run {
                Logger.w(TAG) { "Subtitle download failed: empty body" }
                response.close()
                return null
            }
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            response.close()
            if (outFile.length() > 0) outFile else null
        } catch (e: Exception) {
            Logger.w(TAG) { "Subtitle download exception: ${e.message}" }
            null
        }
    }

    /**
     * Guess the file extension from the URL.
     */
    private fun guessExtension(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".vtt") -> "vtt"
            lower.endsWith(".srt") -> "srt"
            lower.endsWith(".ass") -> "ass"
            lower.endsWith(".ssa") -> "ssa"
            lower.endsWith(".sub") -> "sub"
            else -> "vtt" // default to VTT
        }
    }

    /**
     * Clean up old subtitle files (from previous video loads).
     */
    fun cleanupOldFiles() {
        try {
            val files = subtitlesDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile) {
                    file.delete()
                }
            }
            Logger.d(TAG) { "Cleaned up ${files.size} old subtitle files" }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to clean up subtitle files: ${e.message}" }
        }
    }

    /**
     * Clean up ALL subtitle files (called on player destroy).
     */
    fun cleanupAll() {
        cleanupOldFiles()
    }
}

/**
 * A request to download a subtitle file.
 *
 * @param url The subtitle URL (may be a localhost proxy URL or direct HTTPS).
 * @param lang The language label (e.g. "English", "Japanese").
 * @param headers HTTP headers in MPV format ("Key: Value,Key2: Value2") or empty.
 */
data class SubtitleDownloadRequest(
    val url: String,
    val lang: String,
    val headers: String = "",
)

/**
 * A successfully downloaded subtitle file.
 *
 * @param localPath The local file path (e.g. "/data/.../cache/subtitles/abc123.vtt").
 * @param lang The language label.
 */
data class DownloadedSubtitle(
    val localPath: String,
    val lang: String,
)
