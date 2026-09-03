package com.confused.anikuta.core.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * D-401 (round 28): serializes EVERY tree mutation this provider performs
     * (all `.data.json` writes — [writeDataJson] / [upsertEpisodeInDataJson] /
     * [removeEpisodeFromDataJson] / [replaceEpisodesInDataJson] — plus the
     * folder-level deletions [deleteContentFolder] and the disk sweep
     * [deleteEpisodeFilesOnDisk]).
     *
     * ## Why it exists
     * The round-28 device report caught the multi-episode delete leaving the
     * `.data.json` STALE: `removeEpisodeFromDataJson` is a read-modify-write of
     * ONE shared file, and NOTHING serialized the writers — a download
     * completing (`upsert`), a scanner reconcile (`writeDataJson`) or a second
     * in-flight delete could interleave between the READ and the WRITE, with
     * last-writer-wins silently resurrecting entries the other path had just
     * removed. [DownloadScanner] already carries its own mutex for exactly
     * this hazard ("interleaved runs clobber each other's writes") — the
     * storage layer now enforces the same guarantee for every caller at once.
     *
     * ## Lock-order safety (no cycles)
     * Only two nesting orders exist in the codebase: the manager's
     * `deleteMutex` → this `treeMutex`, and the scanner's `scanMutex` → this
     * `treeMutex`. Nothing ever acquires those outer locks while holding
     * `treeMutex`, so no deadlock is possible. Private helpers
     * (`writeDataJsonRaw`, the remove-attempt ladder, `deleteFileByName`, …)
     * assume the caller already holds the lock.
     */
    private val treeMutex = Mutex()

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
        // D-242-fix: return the contentFolder so HttpDownloader can pass it directly
        // to upsertEpisodeInDataJson without a second findContentFolder walk.
        PublishResult(
            videoUri = videoTarget.uri.toString(),
            subtitleUris = publishedSubtitleUris,
            contentFolder = contentDir,
        )
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
        val index = folder.listFiles().associateBy { it.name ?: "<null-name>" }
        return readDataJsonIndexed(index)
    }

    /** Same as [readDataJson] but accepts a pre-built index (REVIEW-5 M55). */
    private fun readDataJsonIndexed(index: Map<String, DocumentFile>): ContentDataJson? {
        val dataJsonFile = index[".data.json"] ?: run {
            DownloadLogger.w {
                "readDataJsonIndexed — '.data.json' NOT in index " +
                    "(index.keys=${index.keys.toList()})"
            }
            return null
        }
        return try {
            val stream = context.contentResolver.openInputStream(dataJsonFile.uri)
            if (stream == null) {
                DownloadLogger.w {
                    "readDataJsonIndexed — openInputStream returned null for " +
                        "uri=${dataJsonFile.uri}"
                }
                null
            } else {
                stream.use { input ->
                    val text = input.bufferedReader().readText()
                    DownloadLogger.d {
                        "readDataJsonIndexed — read ${text.length} chars from .data.json " +
                            "(first 200: ${text.take(200)})"
                    }
                    val parsed = ContentDataJson.parse(text)
                    if (parsed == null) {
                        DownloadLogger.w {
                            "readDataJsonIndexed — ContentDataJson.parse returned null " +
                                "(JSON malformed — see ContentDataJson.parse catch)"
                        }
                    }
                    parsed
                }
            }
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "readDataJsonIndexed — FAILED to read .data.json: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
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
    ): Unit = treeMutex.withLock {
        withContext(Dispatchers.IO) {
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
    ): Boolean = treeMutex.withLock {
        withContext(Dispatchers.IO) {
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
    }

    /**
     * D-241 / D-392 (round 26) / D-401 (round 28): Removes a single episode
     * (matched by [episodeKey], with [episodeNumber] as the key-drift
     * reconciliation fallback) from the `episodes` list of the `.data.json`
     * for [folder], with a RETRY LADDER + STRICT verification.
     *
     * ## D-401 (round 28): the call order is now DATA.JSON FIRST
     * The round-28 device report showed the multi-episode delete leaving the
     * `.data.json` stale: the round-27 flow called this AFTER the episode
     * files were deleted — and a sibling deletion can invalidate SAF
     * DocumentFile URIs, so the write itself ran inside the exact window
     * where its target document was most likely stale. The manager now calls
     * this BEFORE any file deletion (the user's explicit pipeline: "update
     * the data.json file of that specific content and then delete that
     * content properly afterwards") — the write lands while the tree is
     * untouched, and the ladder below stays as defense-in-depth.
     *
     * ## D-401: the two silent-success holes are closed
     *  - a key mismatch no longer returns "idempotent success" while the
     *    entry stays on disk — [DeletionMatching.matchRemoval] falls back to
     *    the episode number (the scanner's number-keyed rebuilds can drift
     *    keys) and the reconciliation is logged at WARN;
     *  - the write verification is now STRICT ([DeletionMatching.removalVerified]):
     *    a NULL re-read is a FAILURE (the round-27 `!= null && …any{…}`
     *    null-pass made a dead write look verified), and the re-read must
     *    contain NONE of the removed entries by key OR number.
     *
     * ## D-392: why the retry ladder exists (kept as defense-in-depth)
     *  - **Attempt 1** — the normal path: fresh `listFiles()` index → match →
     *    overwrite the existing `.data.json` document → verify by re-reading.
     *  - **Attempt 2** — identical, after a short settle pause. A sibling
     *    deletion can invalidate previously-resolved `DocumentFile` URIs on
     *    some providers; the FRESH index each attempt is the stale-URI
     *    recovery.
     *  - **Attempt 3 (nuclear)** — deletes the old `.data.json` document and
     *    re-creates it from scratch, then writes. This sidesteps ANY stream-
     *    write weirdness on the existing document (a provider refusing to
     *    truncate, a wedged output stream, a mismatched mime/extension).
     *
     * If the deleted episode was the LAST one in the list the `.data.json`
     * write STILL happens (empty episodes list) — the series-folder cleanup
     * decision in [DefaultDownloadManager] re-reads the file right after and
     * acts on the result.
     *
     * Serialized by [treeMutex] — no other tree mutation (a download's
     * upsert, a scanner reconcile, another delete) can interleave this
     * read-modify-write.
     *
     * Returns `true` on success (including the VERIFIED "entry genuinely not
     * present" case), `false` if the `.data.json` couldn't be read, written,
     * or VERIFIED after ALL attempts (the caller logs but doesn't fail the
     * delete — the DB row deletion still proceeds, so the episode is
     * functionally deleted).
     */
    suspend fun removeEpisodeFromDataJson(
        folder: DocumentFile,
        episodeKey: String,
        episodeNumber: Double? = null,
    ): Boolean = treeMutex.withLock {
        withContext(Dispatchers.IO) {
        // R1-DATA-JSON-STILL: extensive logging to diagnose why .data.json
        // still contains the deleted episode after deleteDownloadedEpisode.
        DownloadLogger.i {
            "removeEpisodeFromDataJson — ENTER folder.name='${folder.name}', " +
                "folder.uri=${folder.uri}, exists=${folder.exists()}, " +
                "isDirectory=${folder.isDirectory}, canWrite=${folder.canWrite()}, " +
                "episodeKey='$episodeKey' (len=${episodeKey.length}, " +
                "utf8Bytes=${episodeKey.toByteArray(Charsets.UTF_8).size}), " +
                "episodeNumber=$episodeNumber"
        }
        var succeeded = false
        for (attempt in 1..DATA_JSON_WRITE_ATTEMPTS) {
            val nuclear = attempt == DATA_JSON_WRITE_ATTEMPTS
            succeeded = try {
                removeEpisodeFromDataJsonAttempt(folder, episodeKey, episodeNumber, attempt, nuclear)
            } catch (e: Exception) {
                DownloadLogger.e(e) {
                    "removeEpisodeFromDataJson — attempt $attempt/${DATA_JSON_WRITE_ATTEMPTS} " +
                        "THREW ${e.javaClass.simpleName}: ${e.message}"
                }
                false
            }
            if (succeeded) break
            // Settle pause between attempts — gives the SAF provider a moment
            // to flush any concurrent tree mutation.
            if (attempt < DATA_JSON_WRITE_ATTEMPTS) {
                DownloadLogger.w {
                    "removeEpisodeFromDataJson — attempt $attempt failed — retrying " +
                        "in ${DATA_JSON_RETRY_PAUSE_MS}ms (fresh index" +
                        (if (attempt + 1 == DATA_JSON_WRITE_ATTEMPTS)
                            " + nuclear delete-recreate" else "") +
                        ")"
                }
                delay(DATA_JSON_RETRY_PAUSE_MS)
            }
        }
        if (!succeeded) {
            DownloadLogger.e {
                "removeEpisodeFromDataJson — ALL $DATA_JSON_WRITE_ATTEMPTS attempts FAILED " +
                    "for '${folder.name}' + episodeKey='$episodeKey'. The .data.json on disk " +
                    "may still list this episode — the DB row is still deleted (functionally " +
                    "removed); the next folder scan reconciles the .data.json from disk."
            }
        }
        succeeded
        }
    }

    /**
     * D-392 / D-401: ONE attempt of the remove-episode ladder (see
     * [removeEpisodeFromDataJson]). Every attempt rebuilds its folder index
     * FRESH via `listFiles()` — never trusts a pre-mutation snapshot.
     * Matching + verification are delegated to the unit-tested
     * [DeletionMatching] (key match → number-drift reconciliation → strict
     * re-read verification).
     *
     * The caller holds [treeMutex].
     */
    private suspend fun removeEpisodeFromDataJsonAttempt(
        folder: DocumentFile,
        episodeKey: String,
        episodeNumber: Double?,
        attempt: Int,
        nuclear: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadLogger.i {
            "removeEpisodeFromDataJson — attempt $attempt/$DATA_JSON_WRITE_ATTEMPTS" +
                "${if (nuclear) " (NUCLEAR: delete + re-create)" else ""} " +
                "for '${folder.name}', key='$episodeKey', episodeNumber=$episodeNumber"
        }
        val rawList = try {
            folder.listFiles()
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "removeEpisodeFromDataJson — folder.listFiles() THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
            return@withContext false
        }
        // D-242-fix6: don't !! on .name — a null name on any child throws
        // KotlinNullPointerException inside associateBy, which is swallowed
        // by the caller's runCatching → silent failure. Use a fallback key.
        val index = rawList.associateBy { it.name ?: "<null-name>" }
        val existing = readDataJsonIndexed(index)
        if (existing == null) {
            DownloadLogger.w {
                "removeEpisodeFromDataJson — readDataJsonIndexed returned null. " +
                    "index has .data.json? ${index.containsKey(".data.json")}. " +
                    "index keys: ${index.keys}. " +
                    "(If '.data.json' is missing → wrong folder or file deleted. " +
                    "If present but null → parse failed — see prior 'Failed to read data.json' log.)"
            }
            return@withContext false
        }
        DownloadLogger.i {
            "removeEpisodeFromDataJson — read .data.json OK: mainId='${existing.mainId}', " +
                "${existing.episodes.size} episode(s) in list. " +
                "Episode keys + lengths: " +
                existing.episodes.map { "'${it.episodeKey}'(len=${it.episodeKey.length})" }
        }
        // D-242-fix6: log each episode key alongside the requested key so we can
        // spot any whitespace / encoding / normalization mismatch by eye.
        existing.episodes.forEach { ep ->
            val sameRef = ep.episodeKey === episodeKey
            val sameVal = ep.episodeKey == episodeKey
            val sameLen = ep.episodeKey.length == episodeKey.length
            DownloadLogger.i {
                "removeEpisodeFromDataJson — COMPARE stored='${ep.episodeKey}' " +
                    "(len=${ep.episodeKey.length}, " +
                    "utf8=${ep.episodeKey.toByteArray(Charsets.UTF_8).size}) " +
                    "vs requested='$episodeKey' (len=${episodeKey.length}) " +
                    "→ sameRef=$sameRef, sameVal=$sameVal, sameLen=$sameLen"
            }
        }
        val before = existing.episodes.size
        // D-401: the unit-tested match decision — key match first, then the
        // episodeNumber fallback (key-drift reconciliation: a scanner rebuild
        // can rewrite the stored key). The old filterNot-by-key + idempotent
        // early-true was the first silent-success hole (the round-28 device
        // report: "the data.json file was not updated").
        val match = DeletionMatching.matchRemoval(existing.episodes, episodeKey, episodeNumber)
        if (match.numberReconciled) {
            DownloadLogger.w {
                "removeEpisodeFromDataJson — KEY DRIFT reconciled by episodeNumber: " +
                    "episodeKey='$episodeKey' matched NO entry, but episodeNumber=" +
                    "$episodeNumber matched ${match.removed.map { it.episodeKey }} — " +
                    "the stored key(s) differ from the DB row's key (a scanner " +
                    "number-keyed rebuild). Removing by number."
            }
        }
        DownloadLogger.i {
            "removeEpisodeFromDataJson — match result: before=$before, " +
                "removing=${match.removed.size}, keyMatched=${match.keyMatched}, " +
                "numberReconciled=${match.numberReconciled}"
        }
        if (match.removed.isEmpty()) {
            // D-401: the read SUCCEEDED and NOTHING matches by key OR number —
            // the entry is genuinely not in this list (an earlier delete or a
            // scanner rebuild already removed it). This is a TRUE idempotent
            // no-op: the file already agrees with the request as-is.
            DownloadLogger.i {
                "removeEpisodeFromDataJson — no entry matches episodeKey='$episodeKey' " +
                    "or episodeNumber=$episodeNumber in ${folder.name}'s .data.json " +
                    "(read OK, ${existing.episodes.size} entries) — idempotent no-op"
            }
            return@withContext true
        }
        val removedEntries = match.removed
        val newList = existing.episodes.filterNot { it in removedEntries }
        val updated = existing.copy(
            episodes = newList,
            updatedAt = System.currentTimeMillis(),
        )
        try {
            if (nuclear) {
                // Attempt 3 — the nuclear fallback: remove the old document
                // entirely, then write through the create path. If the delete
                // fails the write falls back to overwriting the survivor.
                val oldDoc = index[".data.json"]
                val oldDeleted = oldDoc?.delete() ?: false
                DownloadLogger.i {
                    "removeEpisodeFromDataJson — NUCLEAR: old .data.json " +
                        "delete()=$oldDeleted (uri=${oldDoc?.uri})"
                }
                val freshIndex =
                    if (oldDeleted) emptyMap() else index // force createFile when gone
                writeDataJsonRaw(updated, folder, freshIndex)
            } else {
                writeDataJsonRaw(updated, folder, index)
            }
            DownloadLogger.i {
                "removeEpisodeFromDataJson — writeDataJsonRaw completed without exception"
            }
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "removeEpisodeFromDataJson — writeDataJsonRaw THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
            return@withContext false
        }
        // R1-DATA-JSON-STILL: VERIFY the write by re-reading the file.
        // If the write went to the wrong target (stale URI, wrong folder),
        // the re-read will still show the unfiltered list.
        val verifyIndex = folder.listFiles().associateBy { it.name ?: "<null-name>" }
        val verifyExisting = readDataJsonIndexed(verifyIndex)
        DownloadLogger.i {
            "removeEpisodeFromDataJson — VERIFY re-read: " +
                "episodes=${verifyExisting?.episodes?.size ?: "null"}, " +
                "keys=${verifyExisting?.episodes?.map { it.episodeKey }}"
        }
        if (!DeletionMatching.removalVerified(verifyExisting, removedEntries)) {
            DownloadLogger.w {
                "removeEpisodeFromDataJson — VERIFY FAILED: " +
                if (verifyExisting == null) {
                    "the re-read is NULL (unreadable — the write cannot be proven)"
                } else {
                    "a removed entry (by key or number) is STILL in .data.json"
                } +
                    " for episodeKey='$episodeKey'. The write either went to the " +
                    "wrong file, was rolled back, or cannot be confirmed."
            }
            return@withContext false
        }
        DownloadLogger.i {
            "removeEpisodeFromDataJson — ${folder.name} now has ${newList.size} episode(s) " +
                "(removed ${removedEntries.map { it.episodeKey }}) — VERIFIED (strict)"
        }
        true
    }

    /**
     * D-392 (round 26): deletes an ENTIRE content (series) folder —
     * `.data.json`, `.cover.jpg`, `.nomedia`, `episodes/`, `subtitles/` and
     * anything else inside — bottom-up, then the folder itself.
     *
     * Called by [DefaultDownloadManager.deleteDownloadedEpisode] when the
     * deleted episode was the LAST one for the anime (the round-26 device
     * report: "if it was the very last downloaded episode then the whole
     * series folder was supposed to be deleted"). The alternative — leaving a
     * husk folder with an empty `.data.json` — is exactly what the user
     * flagged as wrong.
     *
     * ## Safety checks (belt AND braces — this function must NEVER nuke the
     * wrong thing)
     *  1. [folder] must exist and be a directory.
     *  2. [folder] must NOT be the SAF root itself.
     *  3. [folder].name must NOT be one of [SCAN_FORMATS] (`video`, `images`,
     *     `text`, `audio`) — a format folder is never a series folder.
     *  4. When [expectedMainId] is provided, the `.data.json` is re-read
     *     RIGHT BEFORE the deletion and its `mainId` must match — the folder
     *     identity is re-confirmed at the last possible moment even though
     *     [findContentFolder] already matched it.
     *
     * Children are deleted recursively bottom-up (some SAF providers refuse
     * `delete()` on non-empty directories), each with its own log line.
     *
     * @return `true` only when the folder itself is gone.
     */
    suspend fun deleteContentFolder(
        folder: DocumentFile,
        expectedMainId: String? = null,
    ): Boolean = treeMutex.withLock {
        withContext(Dispatchers.IO) {
        DownloadLogger.i {
            "deleteContentFolder — ENTER folder.name='${folder.name}', " +
                "uri=${folder.uri}, expectedMainId=$expectedMainId"
        }
        // Safety 1 — exists + is a directory.
        if (!folder.exists() || !folder.isDirectory) {
            DownloadLogger.w {
                "deleteContentFolder — ABORT: folder doesn't exist or isn't a " +
                    "directory (name='${folder.name}')"
            }
            return@withContext false
        }
        // Safety 2 — never the SAF root itself.
        val root = getRootFolder()
        if (root != null && folder.uri == root.uri) {
            DownloadLogger.w {
                "deleteContentFolder — ABORT: refusing to delete the SAF ROOT " +
                    "(uri=${folder.uri})"
            }
            return@withContext false
        }
        // Safety 3 — never a format folder (video/ images/ text/ audio/).
        val name = folder.name
        if (name.isNullOrBlank() || name in SCAN_FORMATS) {
            DownloadLogger.w {
                "deleteContentFolder — ABORT: folder name '$name' is blank or a " +
                    "format folder ($SCAN_FORMATS) — not a series folder"
            }
            return@withContext false
        }
        // Safety 4 — identity re-confirmation at the last possible moment.
        if (expectedMainId != null) {
            val dataJson = readDataJson(folder)
            when {
                dataJson == null -> DownloadLogger.w {
                    "deleteContentFolder — .data.json unreadable right before " +
                        "deletion; proceeding on the entry-time identity match " +
                        "(findContentFolder matched mainId=$expectedMainId)"
                }
                dataJson.mainId != expectedMainId -> {
                    DownloadLogger.w {
                        "deleteContentFolder — ABORT: identity mismatch — " +
                            ".data.json mainId='${dataJson.mainId}' != " +
                            "expected='$expectedMainId'. This would delete ANOTHER " +
                            "anime's folder."
                    }
                    return@withContext false
                }
                else -> DownloadLogger.i {
                    "deleteContentFolder — identity CONFIRMED " +
                        "(mainId='${dataJson.mainId}', ${dataJson.episodes.size} " +
                        "episode(s) listed, title='${dataJson.title}')"
                }
            }
        }
        // Recursive bottom-up child deletion — every child logged.
        val childFailures = deleteChildrenRecursively(folder)
        val folderDeleted = folder.delete()
        DownloadLogger.i {
            "deleteContentFolder — RESULT for '${folder.name}': " +
                "folder.delete()=$folderDeleted, childFailures=$childFailures"
        }
        folderDeleted || !folder.exists()
        }
    }

    /**
     * D-392: deletes every child of [dir], deepest-first. Returns the number
     * of child deletions that FAILED (0 = clean sweep). Each success/failure
     * is logged so a partial cleanup is fully diagnosable from logcat.
     */
    private fun deleteChildrenRecursively(dir: DocumentFile): Int {
        var failures = 0
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                failures += deleteChildrenRecursively(child)
            }
            val deleted = child.delete()
            if (deleted) {
                DownloadLogger.i {
                    "deleteContentFolder — deleted '${child.name}' " +
                        "(dir=${child.isDirectory}, uri=${child.uri})"
                }
            } else {
                failures++
                DownloadLogger.w {
                    "deleteContentFolder — FAILED to delete '${child.name}' " +
                        "(dir=${child.isDirectory}, uri=${child.uri})"
                }
            }
        }
        return failures
    }

    // ── D-393 (round 27): the DISK-TRUTH episode file sweep ─────────────────
    //
    // Round-27 device finding: "the files are there — this time it didn't even
    // delete the actual files themselves". The round-26 delete flow deleted
    // files ONLY via the URIs recorded in `.data.json` — if that entry was
    // missing (stale `.data.json` from the round-25 sync bug), had a null
    // videoUri, or the URI delete silently returned false, the FILE SURVIVED
    // while the DB row still died (the app showed it deleted). The sweep
    // below makes the file deletion independent of ANY recorded state: it
    // walks the actual folders and deletes by the deterministic FILENAME
    // pattern (the same `episodeFileName`/subtitle convention every download
    // publishes under), then VERIFIES by re-listing.

    /**
     * D-393 (round 27): deletes one episode's video + subtitle files by
     * DISK TRUTH — a filename-pattern sweep of the content folder's
     * `episodes/` + `subtitles/` subfolders (plus a legacy root-level check,
     * for downloads published before the subfolder layout).
     *
     * Why pattern-based: the canonical video name is
     * `<title> - E<00001>.<ext>` and every subtitle is
     * `subtitle_E<00001>_<lang>_<idx>.<ext>` — the episode token is parsed
     * OUT of each file name (regex, full-token comparison) so EP 1 never
     * matches an `E00001.5` file and vice versa.
     *
     * Verification: after the deletion pass the folders are re-listed; any
     * matching survivor triggers ONE retry round (after a settle pause) and
     * is reported in [DiskSweepReport.survivors]. A clean report has zero
     * survivors — that is the on-disk GUARANTEE the round-27 report asked
     * for, independent of `.data.json` state.
     *
     * @param episodeNumber the episode's number (fractional `.5` supported) —
     *   from the DB row (the app's truth), NOT from `.data.json`.
     */
    suspend fun deleteEpisodeFilesOnDisk(
        folder: DocumentFile,
        episodeNumber: Float,
    ): DiskSweepReport = treeMutex.withLock {
        withContext(Dispatchers.IO) {
        val targetToken = formatEpisodeNumber(episodeNumber)
        DownloadLogger.i {
            "deleteEpisodeFilesOnDisk — ENTER folder='${folder.name}', " +
                "episodeNumber=$episodeNumber (token='$targetToken')"
        }

        // ── Census BEFORE: every file matching the episode's token. ──
        val before = listEpisodeFilesForToken(folder, targetToken)
        DownloadLogger.i {
            "deleteEpisodeFilesOnDisk — census BEFORE: ${before.names.size} " +
                "match(es) [${before.videos} video, ${before.subtitles} subtitle]: " +
                before.names
        }

        // ── The deletion passes (a retry round runs only when a pass leaves
        // survivors — some SAF providers need a settle beat between the
        // sibling deletions). ──
        var lastCensus = before
        for (pass in 1..DISK_SWEEP_PASSES) {
            val stillMatching = listEpisodeFilesForToken(folder, targetToken)
            // Keep the freshest census even when we break — a settle-delayed
            // deletion can land between passes, and a stale non-empty census
            // would wrongly report survivors.
            lastCensus = stillMatching
            if (stillMatching.names.isEmpty()) break // nothing (left) to do
            for (name in stillMatching.names) {
                val deleted = deleteFileByName(folder, name)
                DownloadLogger.i {
                    "deleteEpisodeFilesOnDisk — pass $pass: '$name' " +
                        "delete()=$deleted"
                }
            }
            // Verify: re-list after the pass.
            delay(DISK_SWEEP_VERIFY_PAUSE_MS) // tiny settle before the verify
            lastCensus = listEpisodeFilesForToken(folder, targetToken)
            if (lastCensus.names.isEmpty()) {
                DownloadLogger.i {
                    "deleteEpisodeFilesOnDisk — pass $pass CLEAN — all matches gone"
                }
                break
            }
            DownloadLogger.w {
                "deleteEpisodeFilesOnDisk — pass $pass left " +
                    "${lastCensus.names.size} survivor(s): ${lastCensus.names} — " +
                    if (pass < DISK_SWEEP_PASSES) {
                        "retrying after a settle pause"
                    } else {
                        "FINAL: files remain on disk (the SAF provider refused)"
                    }
            }
            if (pass < DISK_SWEEP_PASSES) delay(DISK_SWEEP_RETRY_PAUSE_MS)
        }

        // ── Census AFTER: the report is (before − after), disk-verified. ──
        val report = DiskSweepReport(
            matchedFiles = before.videos + before.subtitles,
            videosDeleted = (before.videos - lastCensus.videos).coerceAtLeast(0),
            subtitlesDeleted = (before.subtitles - lastCensus.subtitles).coerceAtLeast(0),
            survivors = lastCensus.names,
        )
        DownloadLogger.i {
            "deleteEpisodeFilesOnDisk — RESULT for '${folder.name}' " +
                "ep=$episodeNumber: matched=${report.matchedFiles}, " +
                "videosDeleted=${report.videosDeleted}, " +
                "subtitlesDeleted=${report.subtitlesDeleted}, " +
                "survivors=${report.survivors}"
        }
        report
        }
    }

    /**
     * D-393: counts the episode VIDEO files that remain in [folder]'s
     * `episodes/` subfolder (+ legacy root-level video names). Used by the
     * series-folder cleanup as the DISK half of the "is anything still
     * playable here?" decision.
     */
    fun countEpisodeVideoFiles(folder: DocumentFile): Int {
        val dirs = buildList {
            add(folder)
            folder.listFiles()
                .firstOrNull { it.name == "episodes" && it.isDirectory }
                ?.let(::add)
        }
        return dirs.sumOf { dir ->
            dir.listFiles().count { file ->
                !file.isDirectory && isEpisodeVideoName(file.name ?: "")
            }
        }
    }

    /** D-393: the final census — names still on disk matching [token]. */
    private fun listEpisodeFilesForToken(
        folder: DocumentFile,
        token: String,
    ): RemainingEpisodeFiles {
        val names = mutableListOf<String>()
        var videos = 0
        var subtitles = 0
        val dirs = buildList {
            add(folder)
            folder.listFiles().forEach { child ->
                if (child.isDirectory && (child.name == "episodes" || child.name == "subtitles")) {
                    add(child)
                }
            }
        }
        for (dir in dirs) {
            for (file in dir.listFiles()) {
                if (file.isDirectory) continue
                val name = file.name ?: continue
                val isVideo = isEpisodeVideoName(name)
                val isSub = isSubtitleName(name)
                if (!isVideo && !isSub) continue
                if (parseEpisodeToken(name) != token) continue
                names.add(name)
                if (isVideo) videos++ else subtitles++
            }
        }
        return RemainingEpisodeFiles(names, videos, subtitles)
    }

    /**
     * D-393: deletes ONE file by NAME — looks it up in [folder] and its
     * `episodes/`/`subtitles/` subfolders (the census only reports names, so
     * the delete pass resolves each name fresh; a vanished file counts as
     * deleted — idempotent).
     */
    private fun deleteFileByName(folder: DocumentFile, name: String): Boolean {
        val dirs = buildList {
            add(folder)
            folder.listFiles().forEach { child ->
                if (child.isDirectory && (child.name == "episodes" || child.name == "subtitles")) {
                    add(child)
                }
            }
        }
        for (dir in dirs) {
            val target = dir.listFiles().firstOrNull { it.name == name }
            if (target != null) {
                return target.delete() || !target.exists()
            }
        }
        return true // already gone — idempotent success
    }

    /** D-393: `<anything> - E00001.<ext>` — the canonical video file shape. */
    private fun isEpisodeVideoName(name: String): Boolean =
        VIDEO_NAME_REGEX.containsMatchIn(name)

    /** D-393: `subtitle_E00001_…` — the canonical subtitle file shape. */
    private fun isSubtitleName(name: String): Boolean =
        name.startsWith("subtitle_")

    /**
     * D-393: parses the `E<token>` out of a canonical episode/subtitle file
     * name. Full-token comparison (with any fractional part) so EP 1 never
     * matches `E00001.5`.
     */
    private fun parseEpisodeToken(name: String): String? {
        val videoMatch = VIDEO_NAME_REGEX.find(name)
        if (videoMatch != null) return videoMatch.groupValues[1]
        val subMatch = SUBTITLE_NAME_REGEX.find(name)
        if (subMatch != null) return subMatch.groupValues[1]
        return null
    }

    /**
     * D-393 (round 27): the verified outcome of one episode's disk-truth
     * sweep — what was matched, what actually died, and what (if anything)
     * survived even after the retry round. `survivors.isEmpty()` is the
     * on-disk guarantee; non-empty means the SAF provider refused the
     * deletion (the log carries each refusal).
     */
    data class DiskSweepReport(
        /** How many files matched the episode's token across the census pass. */
        val matchedFiles: Int,
        /** Video files confirmed deleted (census − survivors). */
        val videosDeleted: Int,
        /** Subtitle files confirmed deleted (census − survivors). */
        val subtitlesDeleted: Int,
        /** File names STILL on disk after all passes — empty on success. */
        val survivors: List<String>,
    )

    /** D-393: internal final-census helper return. */
    private data class RemainingEpisodeFiles(
        val names: List<String>,
        val videos: Int,
        val subtitles: Int,
    )

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
    ): Boolean = treeMutex.withLock {
        withContext(Dispatchers.IO) {
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
            DownloadLogger.i {
                "writeDataJsonRaw — writing ${jsonText.length} chars " +
                    "(${tempFile.length()} bytes) to uri=${target.uri}, " +
                    "folder.name='${folder.name}', episodes=${data.episodes.size}"
            }
            copyFile(tempFile, target.uri)
            DownloadLogger.i { "writeDataJsonRaw — copyFile completed" }
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
        val root = getRootFolder() ?: run {
            DownloadLogger.w { "findContentFolder — getRootFolder() returned null (no SAF folder set or no permission)" }
            return@withContext null
        }
        var matchingFolders = 0
        for (format in SCAN_FORMATS) {
            val formatDir = root.findFile(format)?.takeIf { it.isDirectory } ?: continue
            for (contentDir in formatDir.listFiles()) {
                if (!contentDir.isDirectory) continue
                val index = contentDir.listFiles().associateBy { it.name ?: "<null-name>" }
                val dataJson = readDataJsonIndexed(index) ?: continue
                if (dataJson.mainId == mainId) {
                    matchingFolders++
                    if (matchingFolders == 1) {
                        DownloadLogger.i {
                            "findContentFolder — MATCH found (first): " +
                                "format=$format, folder.name='${contentDir.name}', " +
                                "folder.uri=${contentDir.uri}, " +
                                "episodesInDataJson=${dataJson.episodes.size}, " +
                                "keys=${dataJson.episodes.map { it.episodeKey }}"
                        }
                        return@withContext contentDir
                    }
                }
            }
        }
        if (matchingFolders > 1) {
            DownloadLogger.w {
                "findContentFolder — DUPLICATE mainId detected: $matchingFolders folders " +
                    "match mainId=$mainId. Returned the FIRST one. If the user is " +
                    "inspecting a DIFFERENT folder's .data.json, the delete will " +
                    "appear to fail. Run a folder rescan or manually delete the " +
                    "stale duplicate folder."
            }
        } else if (matchingFolders == 0) {
            DownloadLogger.w {
                "findContentFolder — NO folder with mainId=$mainId found in any of " +
                    "formats=$SCAN_FORMATS"
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

        /**
         * D-392: how many attempts the `.data.json` remove-write ladder makes
         * (normal → fresh-index retry → nuclear delete-recreate). See
         * [removeEpisodeFromDataJson].
         */
        private const val DATA_JSON_WRITE_ATTEMPTS = 3

        /** D-392: settle pause between the `.data.json` write attempts (ms). */
        private const val DATA_JSON_RETRY_PAUSE_MS = 250L

        // ── D-393 (round 27): the disk-truth sweep knobs ──
        /** How many census→delete→verify passes the sweep makes (2 retries). */
        private const val DISK_SWEEP_PASSES = 3

        /** Settle pause between the delete pass + its verification census (ms). */
        private const val DISK_SWEEP_VERIFY_PAUSE_MS = 150L

        /** Settle pause between sweep retry rounds (ms). */
        private const val DISK_SWEEP_RETRY_PAUSE_MS = 300L

        /**
         * D-393: the canonical video file shape — `<title> - E00001.<ext>`
         * (group 1 = the full episode token incl. any fractional part).
         */
        private val VIDEO_NAME_REGEX = Regex(""" - E(\d{5}(?:\.\d+)?)\.[^.]+$""")

        /**
         * D-393: the canonical subtitle file shape —
         * `subtitle_E00001_<lang>_<idx>.<ext>` (group 1 = the episode token).
         */
        private val SUBTITLE_NAME_REGEX = Regex("""^subtitle_E(\d{5}(?:\.\d+)?)_.+$""")

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
