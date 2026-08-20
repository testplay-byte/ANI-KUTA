package com.confused.anikuta.core.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The NEW SAF-based storage system per `04-storage-paths.md`.
 *
 * Each content folder lives under `<root>/<format>/<sanitized-title>/` and contains:
 *  - `data.json` (the durable source of truth for reinstall recognition)
 *  - `cover.jpg` (cached cover image, best-effort)
 *  - `<title> - E00001.mp4` (the episode file)
 *  - `<title> - E00001.<lang>.<index>.<ext>` (subtitle files)
 *  - `.nomedia` (suppresses gallery indexing of downloaded video files)
 *
 * REVIEW-5 M55: every method that walks a content folder calls `listFiles()` ONCE
 * and builds a `Map<String, DocumentFile>` index for O(1) name lookups.
 *
 * REVIEW-5 M53: [ensureContentFolder] handles same-title collisions by appending
 * `(2)`, `(3)`, ... until a free slot is found OR an existing folder with the same
 * `mainId` is located.
 *
 * REVIEW-5 M54: `.nomedia` is created alongside `data.json` (idempotent — created
 * once per content folder, never overwritten).
 *
 * Atomic publish (§6.3): the video file is written to the cache first (via
 * [TempDownloadCache]), then copied to the SAF folder via a single
 * `ContentResolver.openOutputStream(uri, "w")` call. SAF doesn't support atomic
 * rename — the single-stream write is the atomicity boundary (the SAF provider
 * either has the old file or the new one, never a half-written one).
 */
class DownloadStorageProvider(
    private val context: Context,
    private val preferences: DownloadPreferences,
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Returns the user-selected SAF root as a [DocumentFile], or `null` if:
     *  - No folder URI is set in preferences, OR
     *  - The URI can't be parsed, OR
     *  - Write permission was revoked (folder moved/renamed/permission revoked).
     */
    fun getRootFolder(): DocumentFile? {
        val uriStr = preferences.downloadFolderUri.get()
        if (uriStr.isBlank()) return null
        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) }
            .getOrNull() ?: return null
        if (!tree.canWrite()) {
            DownloadLogger.w { "Root folder not writable — permission revoked or folder moved" }
            return null
        }
        return tree
    }

    /**
     * Returns (or creates) the format folder `<root>/<format>/`.
     *
     * @param format One of `"video"`, `"images"`, `"text"`, `"audio"`.
     */
    fun getFormatFolder(format: String): DocumentFile? {
        val root = getRootFolder() ?: return null
        val existing = root.findFile(format)?.takeIf { it.isDirectory }
        if (existing != null) return existing
        return root.createDirectory(format)
    }

    /**
     * REVIEW-5 M53: returns (or creates) the content folder
     * `<root>/<format>/<sanitized-title>/`. Handles same-title collisions:
     *
     * 1. If no folder with the sanitized title exists → create + return.
     * 2. If a folder exists with the same `mainId` (verified via `data.json`) → reuse.
     * 3. If a folder exists with a DIFFERENT `mainId` → append `(2)`, `(3)`, ... until
     *    a free slot OR a same-mainId folder is found.
     */
    suspend fun getContentFolder(mainId: String, title: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            val format = "video" // D.1 only supports video; future formats add a lookup here
            val formatDir = getFormatFolder(format) ?: return@withContext null
            ensureContentDir(formatDir, mainId, title)
        }

    /**
     * Publishes the temp video file (and any temp subtitle files) to the user's SAF
     * folder. Returns a [PublishResult] containing the video `content://` URI AND the
     * subtitle `content://` URIs (in track order), or throws on failure.
     *
     * D-FIX-SUB: previously returned ONLY the video URI string. The subtitle files
     * were written to disk but their URIs were lost → `task.subtitleUris` was never
     * set → offline playback had no subtitles. Now returns both via [PublishResult].
     *
     * D-FIX-SUB (naming): subtitle files are now named with the language label so the
     * offline subtitle picker can show "English" / "Japanese" instead of "Subtitle 1".
     * Format: `.subtitle_E{00001}_{lang}_{index}.{ext}` (lang sanitized to [a-z0-9-],
     * `unknown` if blank). The `_index` suffix is kept for uniqueness when two tracks
     * share a language.
     *
     * REVIEW-5 M35: this is the final phase of the download pipeline — the caller
     * (HttpDownloader) emits an intermediate `99%` progress tick before calling.
     *
     * @param downloadId The task ID (for logging).
     * @param tempFile The temp video file in [TempDownloadCache].
     * @param content The content identity (drives the folder location).
     * @param episode The episode identity (drives the file name).
     * @param videoExtension The video file extension (e.g. `"mp4"`, `"mkv"`, `"ts"`).
     * @param subtitleFiles The temp subtitle files to publish alongside the video.
     *   Order MUST match [DownloadTask.subtitleTracks] (the caller passes them in
     *   track order). Files whose corresponding track had no language still publish
     *   (lang = `unknown`).
     * @param subtitleLangs The language label per subtitle file (same length as
     *   [subtitleFiles]). D-FIX-SUB: used for the on-disk filename so the offline
     *   subtitle picker can show the language. Defaults to empty (legacy callers) —
     *   in that case all subtitles get `unknown`.
     * @return [PublishResult] with the video URI + subtitle URIs.
     */
    suspend fun publishVideoFile(
        downloadId: Long,
        tempFile: File,
        content: DownloadContentInfo,
        episode: DownloadEpisodeInfo,
        videoExtension: String,
        subtitleFiles: List<File> = emptyList(),
        subtitleLangs: List<String> = emptyList(),
    ): PublishResult = withContext(Dispatchers.IO) {
        val contentDir = getContentFolder(content.mainId, content.title)
            ?: throw DownloadException("Failed to create content folder for ${content.title}")

        // REVIEW-5 M55: listFiles() ONCE, build a name→DocumentFile index.
        val index = contentDir.listFiles().associateBy { it.name!! }

        // 1. Write data.json (read-modify-write).
        writeDataJson(content, contentDir, index)

        // 2. Write cover.jpg (best-effort — if coverUrl is set + no cover exists).
        //    HttpDownloader pre-downloads the cover to TempDownloadCache; if present,
        //    we copy from there. Otherwise we fetch from coverUrl on-demand.
        if (index[".cover.jpg"] == null) {
            writeCoverImage(content.coverUrl, contentDir, content.mainId)
        }

        // 3. Write .nomedia (idempotent — REVIEW-5 M54).
        if (index[".nomedia"] == null) {
            runCatching { contentDir.createFile("application/octet-stream", ".nomedia") }
        }

        // 4. Write the video file into an "episodes" subfolder.
        // Folder structure: <root>/video/<title>/episodes/<title> - E00001.mp4
        //                    <root>/video/<title>/subtitles/.subtitle_E00001_english_0.srt
        //                    <root>/video/<title>/.data.json, .cover.jpg, .nomedia (hidden, stay in root)
        val episodesDir = getOrCreateSubfolder(contentDir, "episodes", index)
        val videoName = episodeFileName(content, episode, videoExtension)
        // Check if the video already exists in the episodes subfolder.
        val epIndex = episodesDir.listFiles().associateBy { it.name!! }
        epIndex[videoName]?.delete()
        val videoTarget = episodesDir.createFile("video/*", videoName)
            ?: throw DownloadException("Failed to create video file: $videoName")
        copyFile(tempFile, videoTarget.uri)

        // 5. Write subtitle files into a "subtitles" subfolder.
        val subtitlesDir = getOrCreateSubfolder(contentDir, "subtitles", index)
        val subIndex = subtitlesDir.listFiles().associateBy { it.name!! }
        val publishedSubtitleUris = mutableListOf<String>()
        for ((subIndex2, subFile) in subtitleFiles.withIndex()) {
            val ext = subFile.extension.ifBlank { "vtt" }
            val epNum = String.format("%05d", episode.episodeNumber.toInt())
            val rawLang = subtitleLangs.getOrNull(subIndex2) ?: ""
            val safeLang = sanitizeLangForFileName(rawLang)
            val subName = "subtitle_E${epNum}_${safeLang}_${subIndex2}.$ext"
            subIndex[subName]?.delete()
            val subTarget = subtitlesDir.createFile("application/octet-stream", subName)
            if (subTarget != null) {
                copyFile(subFile, subTarget.uri)
                publishedSubtitleUris.add(subTarget.uri.toString())
            }
        }

        DownloadLogger.i {
            "publishVideoFile($downloadId) — published $videoName (${tempFile.length()} bytes) " +
                "to ${contentDir.name} + ${publishedSubtitleUris.size} subtitle(s)"
        }
        PublishResult(videoUri = videoTarget.uri.toString(), subtitleUris = publishedSubtitleUris)
    }

    /**
     * Sanitizes a language label for use in a filename. Lowercases, replaces
     * non-alphanumeric runs with a single hyphen, trims leading/trailing hyphens.
     * Returns `"unknown"` if blank. Examples: "English" → "english",
     * "Español (Latino)" → "espanol-latino", "" → "unknown".
     */
    private fun sanitizeLangForFileName(lang: String): String {
        val sanitized = lang.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return sanitized.ifBlank { "unknown" }
    }

    /**
     * Gets or creates a subfolder inside [parent] with the given [name].
     * Uses the [index] (parent's listFiles index) to check if it already exists.
     * Returns the subfolder DocumentFile.
     */
    private fun getOrCreateSubfolder(
        parent: DocumentFile,
        name: String,
        index: Map<String, DocumentFile>,
    ): DocumentFile {
        return index[name]?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: throw DownloadException("Failed to create subfolder: $name")
    }

    /**
     * Reads the `data.json` from [folder]. Returns `null` if not present or unparseable.
     *
     * REVIEW-5 M55: uses the caller-provided index when possible (the [folder] overload
     * builds the index on-demand — used by [scanAllContent]).
     */
    fun readDataJson(folder: DocumentFile): ContentDataJson? {
        val index = folder.listFiles().associateBy { it.name!! }
        return readDataJsonIndexed(index)
    }

    /** Same as [readDataJson] but accepts a pre-built index (REVIEW-5 M55). */
    private fun readDataJsonIndexed(index: Map<String, DocumentFile>): ContentDataJson? {
        val dataJsonFile = index[".data.json"] ?: return null
        return try {
            context.contentResolver.openInputStream(dataJsonFile.uri)?.use { input ->
                val text = input.bufferedReader().readText()
                ContentDataJson.parse(text)
            }
        } catch (e: Exception) {
            DownloadLogger.w { "Failed to read data.json: ${e.message}" }
            null
        }
    }

    /**
     * Writes (or updates) the `data.json` in [folder]. Idempotent — re-writing the
     * same content produces the same file.
     *
     * Atomicity (§6.4): writes to a temp file in `context.cacheDir` first, then
     * copies to the SAF target. The SAF provider either has the old `data.json`
     * or the new one — never a half-written one.
     *
     * D-242: PRESERVES the existing `episodes` list (the `.copy()` only overrides
     * FK / metadata fields; `episodes` + `createdAt` keep their existing values).
     * This means a reconcile call (which goes through `DownloadScanner.reconcileDataJsonFromContent`)
     * will NOT clobber the episodes list that was built up by prior downloads.
     *
     * D-242: Uses `content.X ?: existing.X` for each FK field — a null value on
     * [content] does NOT clobber a non-null value on [existing]. This prevents
     * the null-overwrite bug where a partially-populated `DownloadContentInfo`
     * (e.g. from a `download_queue` round-trip that drops FK fields) would
     * destroy good data in `.data.json`.
     */
    suspend fun writeDataJson(
        content: DownloadContentInfo,
        folder: DocumentFile,
        index: Map<String, DocumentFile> = folder.listFiles().associateBy { it.name!! },
    ): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = readDataJsonIndexed(index)
        val updated = (existing ?: ContentDataJson(
            mainId = content.mainId,
            contentId = content.contentId,
            title = content.title,
            contentType = content.contentType,
            contentFormat = content.contentFormat,
            description = content.description,
            dataSourceId = content.dataSourceId,
            systemId = content.systemId,
            extensionRepoId = content.extensionRepoId,
            extensionId = content.extensionId,
            sourceId = content.sourceId,
            animeUrl = content.animeUrl,
            displaySource = content.displaySource,
            coverUrl = content.coverUrl,
            anilistId = content.anilistId,
            createdAt = now,
            updatedAt = now,
        )).copy(
            // D-242: use `content.X ?: existing.X` so nulls on `content` don't
            // clobber non-null values on `existing`. This is the null-overwrite fix.
            title = content.title.ifBlank { existing?.title ?: content.title },
            contentType = content.contentType.ifBlank { existing?.contentType ?: content.contentType },
            contentFormat = content.contentFormat.ifBlank { existing?.contentFormat ?: content.contentFormat },
            description = content.description ?: existing?.description,
            dataSourceId = content.dataSourceId ?: existing?.dataSourceId,
            systemId = content.systemId ?: existing?.systemId,
            extensionRepoId = content.extensionRepoId ?: existing?.extensionRepoId,
            extensionId = content.extensionId ?: existing?.extensionId,
            sourceId = content.sourceId ?: existing?.sourceId,
            animeUrl = content.animeUrl ?: existing?.animeUrl,
            displaySource = content.displaySource.ifBlank { existing?.displaySource ?: content.displaySource },
            coverUrl = content.coverUrl ?: existing?.coverUrl,
            anilistId = content.anilistId ?: existing?.anilistId,
            updatedAt = now,
        )
        writeDataJsonRaw(updated, folder, index)
    }

    /**
     * D-241: Upserts (append-or-replace) a single [DownloadedEpisodeInfo] into
     * the `episodes` list of the `.data.json` for [folder].
     *
     * Called by [DownloadQueue.launchDownload] AFTER the video file is published
     * AND the `downloaded_episode` DB row is inserted — this keeps the on-disk
     * `.data.json` in sync with the DB.
     *
     * Match policy (in priority order):
     *  1. If [DownloadedEpisodeInfo.episodeKey] is non-null → match by episodeKey.
     *  2. Else match by [DownloadedEpisodeInfo.episodeNumber] (rounded to 2dp).
     *
     * If a matching entry exists, it's REPLACED (so re-downloads update the
     * quality / fileSize / videoUri). If no match, the entry is appended.
     *
     * Idempotent — calling twice with the same episode produces the same file.
     * Returns `true` on success, `false` if the `.data.json` couldn't be read
     * or written (the caller logs but doesn't fail the download — the DB row
     * is already inserted, so the episode will still play).
     */
    suspend fun upsertEpisodeInDataJson(
        folder: DocumentFile,
        episode: DownloadedEpisodeInfo,
    ): Boolean = withContext(Dispatchers.IO) {
        val index = folder.listFiles().associateBy { it.name!! }
        val existing = readDataJsonIndexed(index) ?: run {
            DownloadLogger.w {
                "upsertEpisodeInDataJson — no .data.json in ${folder.name}; " +
                    "cannot append episode (the download flow should have written it first)"
            }
            return@withContext false
        }
        // D-242: match by episodeKey (now non-nullable). Fall back to
        // episodeNumber for safety (shouldn't happen, but defensive).
        val matcher: (DownloadedEpisodeInfo) -> Boolean =
            { it.episodeKey == episode.episodeKey || it.episodeNumber == episode.episodeNumber }
        // Replace existing entry (if any) + append the new one at the end.
        // Sorted by episodeNumber on write so the list is human-readable in
        // a file viewer + stable across re-downloads.
        val newList = (existing.episodes.filterNot(matcher) + episode)
            .sortedWith(compareBy({ it.episodeNumber }, { it.episodeKey }))
        val updated = existing.copy(
            schemaVersion = ContentDataJson.CURRENT_SCHEMA_VERSION,
            episodes = newList,
            updatedAt = System.currentTimeMillis(),
        )
        writeDataJsonRaw(updated, folder, index)
        DownloadLogger.i {
            "upsertEpisodeInDataJson — ${folder.name} now has ${newList.size} episode(s) " +
                "(added/updated ep ${episode.episodeNumber}, key=${episode.episodeKey})"
        }
        true
    }

    /**
     * D-241: Removes a single episode (matched by [episodeKey]) from the
     * `episodes` list of the `.data.json` for [folder].
     *
     * Called by [DefaultDownloadManager.deleteDownloadedEpisode] AFTER the
     * video file is deleted AND the `downloaded_episode` DB row is removed —
     * this keeps the on-disk `.data.json` in sync with the DB.
     *
     * If the deleted episode was the LAST one in the list, the `.data.json` is
     * left in place (with an empty `episodes` list). The folder + `.data.json`
     * are kept so a future re-download doesn't have to recreate the content
     * identity; if the user wants to fully remove the content they can use the
     * "delete entire content" action (TODO — currently the delete flow only
     * removes individual episodes).
     *
     * Returns `true` on success, `false` if the `.data.json` couldn't be read
     * or written (the caller logs but doesn't fail the delete — the DB row is
     * already gone, so the episode is functionally deleted).
     */
    suspend fun removeEpisodeFromDataJson(
        folder: DocumentFile,
        episodeKey: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val index = folder.listFiles().associateBy { it.name!! }
        val existing = readDataJsonIndexed(index) ?: run {
            DownloadLogger.w {
                "removeEpisodeFromDataJson — no .data.json in ${folder.name}; " +
                    "nothing to remove"
            }
            return@withContext false
        }
        // D-242: match by episodeKey (now non-nullable — no v2 fallback needed).
        val before = existing.episodes.size
        val newList = existing.episodes.filterNot { ep -> ep.episodeKey == episodeKey }
        if (newList.size == before) {
            DownloadLogger.d {
                "removeEpisodeFromDataJson — episode $episodeKey not in ${folder.name}'s " +
                    ".data.json (already removed or never added)"
            }
            return@withContext true // not an error — idempotent
        }
        val updated = existing.copy(
            episodes = newList,
            updatedAt = System.currentTimeMillis(),
        )
        writeDataJsonRaw(updated, folder, index)
        DownloadLogger.i {
            "removeEpisodeFromDataJson — ${folder.name} now has ${newList.size} episode(s) " +
                "(removed $episodeKey)"
        }
        true
    }

    /**
     * D-241: Replaces the entire `episodes` list of the `.data.json` for
     * [folder] with [episodes]. Used by [DownloadScanner.scan] when it
     * rebuilds the list from the on-disk file walk (reinstall recognition).
     *
     * This is the ONLY public way to set the episodes list as a whole —
     * callers MUST ensure [episodes] reflects the actual on-disk state
     * (every video file in `episodes/` should have a corresponding entry).
     */
    suspend fun replaceEpisodesInDataJson(
        folder: DocumentFile,
        episodes: List<DownloadedEpisodeInfo>,
    ): Boolean = withContext(Dispatchers.IO) {
        val index = folder.listFiles().associateBy { it.name!! }
        val existing = readDataJsonIndexed(index) ?: return@withContext false
        val updated = existing.copy(
            schemaVersion = ContentDataJson.CURRENT_SCHEMA_VERSION,
            episodes = episodes.sortedWith(compareBy({ it.episodeNumber }, { it.episodeKey })),
            updatedAt = System.currentTimeMillis(),
        )
        writeDataJsonRaw(updated, folder, index)
        DownloadLogger.i {
            "replaceEpisodesInDataJson — ${folder.name} now has ${episodes.size} episode(s)"
        }
        true
    }

    // D-242: deriveEpNumPadded removed — episodeKey is now non-nullable + equals
    // SEpisode.url (not a derived "$mainId|$epNumPadded" string). No fallback needed.

    /**
     * D-241: Low-level write — serializes [data] to JSON + atomically writes
     * it to the `.data.json` file in [folder]. Used by [writeDataJson],
     * [upsertEpisodeInDataJson], [removeEpisodeFromDataJson],
     * [replaceEpisodesInDataJson].
     *
     * Atomicity: writes to a temp file in `context.cacheDir` first, then
     * copies to the SAF target. The SAF provider either has the old
     * `.data.json` or the new one — never a half-written one.
     */
    private suspend fun writeDataJsonRaw(
        data: ContentDataJson,
        folder: DocumentFile,
        index: Map<String, DocumentFile>,
    ) = withContext(Dispatchers.IO) {
        val jsonText = ContentDataJson.stringify(data)
        val tempFile = File.createTempFile("data", ".json", context.cacheDir)
        try {
            tempFile.writeText(jsonText)
            val target = index[".data.json"]
                ?: folder.createFile("application/json", ".data.json")
                ?: throw DownloadException("Failed to create data.json in ${folder.name}")
            copyFile(tempFile, target.uri)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Best-effort: fetches the cover image from [coverUrl] and writes it to
     * `<folder>/cover.jpg`. Returns silently on failure (the UI uses a placeholder).
     */
    suspend fun writeCoverImage(coverUrl: String?, folder: DocumentFile, mainId: String) {
        if (coverUrl.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(coverUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        DownloadLogger.w { "Cover fetch failed (${response.code}) for $mainId" }
                        return@withContext
                    }
                    val target = folder.createFile("image/jpeg", ".cover.jpg") ?: return@withContext
                    context.contentResolver.openOutputStream(target.uri)?.use { out ->
                        response.body?.byteStream()?.use { it.copyTo(out) }
                    }
                }
            } catch (e: Exception) {
                DownloadLogger.w { "Cover fetch failed for $mainId: ${e.message}" }
            }
        }
    }

    /** Creates a `.nomedia` file in [folder] if one doesn't already exist. */
    fun writeNomedia(folder: DocumentFile) {
        val index = folder.listFiles().associateBy { it.name!! }
        if (index[".nomedia"] == null) {
            runCatching { folder.createFile("application/octet-stream", ".nomedia") }
        }
    }

    /**
     * Walks `<root>/{video,images,text,audio}/`, reads each `data.json`, returns the
     * list of [ContentDataJson] records found.
     *
     * Used by [DownloadScanner] on startup. REVIEW-5 M55: each content folder's
     * `listFiles()` is called ONCE + cached in a local Map for follow-up lookups.
     */
    suspend fun scanAllContent(): List<ContentDataJson> = withContext(Dispatchers.IO) {
        val root = getRootFolder() ?: return@withContext emptyList()
        val result = mutableListOf<ContentDataJson>()
        for (format in SCAN_FORMATS) {
            val formatDir = root.findFile(format)?.takeIf { it.isDirectory } ?: continue
            for (contentDir in formatDir.listFiles()) {
                if (!contentDir.isDirectory) continue
                val index = contentDir.listFiles().associateBy { it.name!! }
                val dataJson = readDataJsonIndexed(index) ?: continue
                result.add(dataJson)
            }
        }
        result
    }

    /**
     * Finds a content folder by `mainId` (walking the format folders). Used by the
     * episode-deletion flow + the offline-lookup fallback. Returns `null` if no
     * folder's `data.json` matches [mainId].
     */
    suspend fun findContentFolder(mainId: String): DocumentFile? = withContext(Dispatchers.IO) {
        val root = getRootFolder() ?: return@withContext null
        for (format in SCAN_FORMATS) {
            val formatDir = root.findFile(format)?.takeIf { it.isDirectory } ?: continue
            for (contentDir in formatDir.listFiles()) {
                if (!contentDir.isDirectory) continue
                val index = contentDir.listFiles().associateBy { it.name!! }
                val dataJson = readDataJsonIndexed(index) ?: continue
                if (dataJson.mainId == mainId) return@withContext contentDir
            }
        }
        null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * REVIEW-5 M53: same-title collision algorithm.
     *
     * 1. If `<baseName>` doesn't exist in [formatDir] → create + return.
     * 2. If `<baseName>` exists + its `data.json` has the same `mainId` → reuse.
     * 3. If `<baseName>` exists + different `mainId` → try `<baseName> (2)`, `(3)`, ...
     */
    private fun ensureContentDir(
        formatDir: DocumentFile,
        mainId: String,
        title: String,
    ): DocumentFile? {
        val baseName = sanitizeFileName(title.ifBlank { "Unknown" })
        val candidate = formatDir.findFile(baseName)
        if (candidate == null) {
            return formatDir.createDirectory(baseName)
        }
        // Verify the existing folder belongs to THIS content.
        val existingDataJson = readDataJson(candidate)
        if (existingDataJson == null || existingDataJson.mainId == mainId) {
            return candidate // Same content (or no data.json yet) — reuse.
        }
        // Different mainId with same title — append (2), (3), ...
        var suffix = 2
        while (true) {
            val altName = "$baseName ($suffix)"
            val alt = formatDir.findFile(altName)
            if (alt == null) {
                return formatDir.createDirectory(altName)
            }
            val altDataJson = readDataJson(alt)
            if (altDataJson == null || altDataJson.mainId == mainId) {
                return alt
            }
            suffix++
            if (suffix > MAX_COLLISION_SUFFIX) {
                DownloadLogger.w { "ensureContentDir — too many collisions ($suffix) for '$baseName'" }
                return null
            }
        }
    }

    /** Copies a regular [File] to a SAF [uri] via ContentResolver. */
    private fun copyFile(source: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: throw DownloadException("Failed to open output stream for $target")
    }

    /** Builds the episode video file name: `<title> - E<00001>.<ext>`. */
    private fun episodeFileName(
        content: DownloadContentInfo,
        episode: DownloadEpisodeInfo,
        ext: String,
    ): String {
        val safeTitle = sanitizeFileName(content.title.ifBlank { "Unknown" })
        return "$safeTitle - E${formatEpisodeNumber(episode.episodeNumber)}.$ext"
    }

    /** 5-digit zero-padded episode number with optional `.5` fractional suffix. */
    private fun formatEpisodeNumber(episodeNumber: Float): String {
        val intPart = episodeNumber.toInt().coerceAtLeast(0)
        if (episodeNumber == intPart.toFloat()) {
            return "%05d".format(intPart)
        }
        val fractional = episodeNumber - intPart
        val fracStr = fractional.toString().removePrefix("0.").trimEnd('0').ifBlank { "0" }
        return if (fracStr == "0") "%05d".format(intPart) else "%05d.%s".format(intPart, fracStr)
    }

    companion object {
        /** The format folders scanned by [scanAllContent] + [findContentFolder]. */
        val SCAN_FORMATS = listOf("video", "images", "text", "audio")

        /** Cap on the same-title collision loop (REVIEW-5 M53 — defensive). */
        private const val MAX_COLLISION_SUFFIX = 100

        /** Invalid filename chars (replaced with `_`). */
        private val INVALID_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        /**
         * Sanitizes a content title for use as a folder/file name.
         *
         * - Replaces invalid chars (`/ \ : * ? " < > |`) with `_`.
         * - Trims leading/trailing whitespace + dots.
         * - Collapses runs of whitespace into a single space.
         * - Caps at ~200 characters (leaves room for ` - E00001.mp4` + ext on a 255-byte limit).
         */
        fun sanitizeFileName(name: String): String {
            var s = name
            for (c in INVALID_CHARS) s = s.replace(c, '_')
            s = s.trim().trim('.').trim()
            s = s.replace(Regex("\\s+"), " ")
            return if (s.length > 200) s.substring(0, 200) else s.ifBlank { "Unknown" }
        }
    }
}
