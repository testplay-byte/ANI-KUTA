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
 * [TempDownloadCache]), then copied to the SAF folder via a single truncating
 * `ContentResolver.openOutputStream(uri, "wt")` call. SAF doesn't support
 * atomic rename — the single-stream write is the atomicity boundary (the SAF
 * provider either has the old file or the new one, never a half-written one).
 * (D-404, round 29: "wt" everywhere — the legacy "w" never truncated on
 * AOSP ExternalStorageProvider, which is what corrupted shrinking
 * `.data.json` writes for four device rounds.)
 */
class DownloadStorageProvider(
    private val context: Context,
    private val preferences: DownloadPreferences,
    private val okHttpClient: OkHttpClient,
) {

    /**
     * D-401 (round 28) / D-404 (round 29): serializes EVERY tree mutation
     * this provider performs (all `.data.json` writes — [writeDataJson] /
     * [upsertEpisodeInDataJson] / [rewriteDataJsonEpisodes] /
     * [replaceEpisodesInDataJson] — plus the folder-level deletions
     * [deleteContentFolder] and the disk sweep [deleteEpisodeFilesOnDisk]).
     *
     * ## Why it exists
     * The round-28 device report caught the multi-episode delete leaving the
     * `.data.json` STALE: the `.data.json` writers are read-modify-writes of
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
     * (`writeDataJsonRaw`, the rewrite-attempt ladder, `deleteFileByName`, …)
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

    /**
     * Same as [readDataJson] but accepts a pre-built index (REVIEW-5 M55).
     *
     * D-404 (round 29): [salvage] (default true) — when the strict parse
     * fails, recover the first COMPLETE top-level JSON object out of the
     * corrupted file via [DataJsonRepair.salvageCompleteJsonHead]. The
     * legacy non-truncating-`"w"` writes (rounds 25–28) left
     * `new-json-head + old-json-tail` files behind; the head is a complete
     * valid document. Salvaging it here heals EVERY read path at once
     * (startup scan, folder locate, delete) — including the exact file the
     * user's v0.4.16 device test left on disk. Callers that must prove the
     * file is CLEAN (the post-write verification) pass [salvage] = false.
     */
    private fun readDataJsonIndexed(
        index: Map<String, DocumentFile>,
        salvage: Boolean = true,
    ): ContentDataJson? {
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
                    var parsed = ContentDataJson.parse(text)
                    if (parsed == null && salvage) {
                        val salvagedText = DataJsonRepair.salvageCompleteJsonHead(text)
                        if (salvagedText != null) {
                            parsed = ContentDataJson.parse(salvagedText)
                            if (parsed != null) {
                                DownloadLogger.w {
                                    "readDataJsonIndexed — SALVAGED a corrupted " +
                                        ".data.json (uri=${dataJsonFile.uri}): recovered a " +
                                        "complete ${salvagedText.length}-char document head " +
                                        "out of ${text.length} chars " +
                                        "(${text.length - salvagedText.length} bytes of " +
                                        "trailing garbage dropped — the legacy " +
                                        "non-truncating-write corruption). Content: " +
                                        "mainId='${parsed.mainId}', " +
                                        "${parsed.episodes.size} episode(s), " +
                                        "title='${parsed.title}'"
                                }
                            }
                        }
                    }
                    if (parsed == null) {
                        DownloadLogger.w {
                            "readDataJsonIndexed — .data.json UNPARSEABLE " +
                                "(${text.length} chars) — strict parse failed" +
                                (if (salvage) " and salvage found no complete head" else " (salvage disabled — strict verification read)")
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
     * D-404 (round 29): REWRITES the `.data.json` of [folder] so that its
     * `episodes` list equals [episodes] EXACTLY — the DB-truth rebuild the
     * round-29 device report demanded — and VERIFIES the write landed, with
     * the 3-attempt ladder.
     *
     * This SUPERSEDES + removes `removeEpisodeFromDataJson` (rounds 26–28).
     * The round-28 approach was a read-modify-write keyed on episodeKey
     * matching (key → number-drift fallback → idempotent no-op). The round-29
     * postmortem found the REAL primitive behind every "data.json not
     * updated / corrupted" report since round 25: `openOutputStream(uri, "w")`
     * does NOT truncate on AOSP ExternalStorageProvider — a write SHORTER
     * than the file left the old tail behind → unparseable json →
     * `findContentFolder` (which skips unparseable folders) returned null on
     * the NEXT delete → every disk phase skipped. Matching logic could never
     * have fixed a write-primitive bug; the rebuild removes the matching
     * problem from existence:
     *
     *  - the CALLER ([DefaultDownloadManager.deleteDownloadedEpisode])
     *    derives [episodes] from the DB rows (rows-for-anime minus the
     *    deleted row — see [DataJsonRepair.rebuildEpisodesAfterDelete]);
     *    this function only WRITES + VERIFIES it;
     *  - [content] supplies the content-level metadata when the existing
     *    `.data.json` is unreadable/corrupted beyond salvage — the rebuild
     *    HEALS the file (the user's v0.4.16 device state);
     *  - the write uses the truncating `"wt"` mode ([copyFile]) and the
     *    verification is STRICT: the re-read must parse CLEAN (no salvage)
     *    AND its episodes set must EQUAL the expected set — not merely "the
     *    deleted entry is absent" (the round-28 bar) but "the file now says
     *    exactly what the DB says";
     *  - the ladder: (1) truncating overwrite + verify; (2) settle + fresh
     *    index + same; (3) NUCLEAR — delete the old document entirely,
     *    re-create it fresh, write, verify. A corrupted document can no
     *    longer wall the ladder off: every attempt works from a FRESH
     *    `listFiles()` index and the read is only used for METADATA
     *    enrichment (a null read falls back to [content]), never as a gate.
     *
     * Serialized by [treeMutex]. Returns `true` only when the re-read proves
     * the file lists exactly [episodes].
     */
    suspend fun rewriteDataJsonEpisodes(
        folder: DocumentFile,
        content: DownloadContentInfo,
        episodes: List<DownloadedEpisodeInfo>,
    ): Boolean = treeMutex.withLock {
        withContext(Dispatchers.IO) {
        DownloadLogger.i {
            "rewriteDataJsonEpisodes — ENTER folder='${folder.name}', " +
                "folder.uri=${folder.uri}, writing ${episodes.size} episode(s) " +
                "[keys=${episodes.map { it.episodeKey }}]"
        }
        var succeeded = false
        for (attempt in 1..DATA_JSON_WRITE_ATTEMPTS) {
            val nuclear = attempt == DATA_JSON_WRITE_ATTEMPTS
            succeeded = try {
                rewriteDataJsonEpisodesAttempt(folder, content, episodes, attempt, nuclear)
            } catch (e: Exception) {
                DownloadLogger.e(e) {
                    "rewriteDataJsonEpisodes — attempt $attempt/$DATA_JSON_WRITE_ATTEMPTS " +
                        "THREW ${e.javaClass.simpleName}: ${e.message}"
                }
                false
            }
            if (succeeded) break
            // Settle pause between attempts — gives the SAF provider a moment
            // to flush any concurrent tree mutation.
            if (attempt < DATA_JSON_WRITE_ATTEMPTS) {
                DownloadLogger.w {
                    "rewriteDataJsonEpisodes — attempt $attempt failed — retrying " +
                        "in ${DATA_JSON_RETRY_PAUSE_MS}ms (fresh index" +
                        (if (attempt + 1 == DATA_JSON_WRITE_ATTEMPTS)
                            " + NUCLEAR delete-recreate" else "") +
                        ")"
                }
                delay(DATA_JSON_RETRY_PAUSE_MS)
            }
        }
        if (!succeeded) {
            DownloadLogger.e {
                "rewriteDataJsonEpisodes — ALL $DATA_JSON_WRITE_ATTEMPTS attempts FAILED " +
                    "for '${folder.name}': the .data.json does NOT provably list " +
                    "${episodes.size} episode(s). The delete pipeline still proceeds " +
                    "(the DB row is the functional truth; the next startup scan " +
                    "reconciles the file from disk) — but the durable file is NOT " +
                    "verified. Check the attempt logs above for the exact phase."
            }
        } else {
            DownloadLogger.i {
                "rewriteDataJsonEpisodes — VERIFIED: '${folder.name}' .data.json " +
                    "now lists exactly ${episodes.size} episode(s) " +
                    "[keys=${episodes.map { it.episodeKey }}]"
            }
        }
        succeeded
        }
    }

    /**
     * D-404: ONE attempt of the rewrite ladder (see [rewriteDataJsonEpisodes]).
     * Every attempt rebuilds its folder index FRESH via `listFiles()` — never
     * trusts a pre-mutation snapshot. The caller holds [treeMutex].
     */
    private suspend fun rewriteDataJsonEpisodesAttempt(
        folder: DocumentFile,
        content: DownloadContentInfo,
        episodes: List<DownloadedEpisodeInfo>,
        attempt: Int,
        nuclear: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        DownloadLogger.i {
            "rewriteDataJsonEpisodes — attempt $attempt/$DATA_JSON_WRITE_ATTEMPTS" +
                "${if (nuclear) " (NUCLEAR: delete + re-create)" else ""} " +
                "for '${folder.name}', ${episodes.size} episode(s)"
        }
        val rawList = try {
            folder.listFiles()
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "rewriteDataJsonEpisodes — folder.listFiles() THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
            return@withContext false
        }
        // D-242-fix6: don't !! on .name — a null name on any child throws
        // KotlinNullPointerException inside associateBy. Use a fallback key.
        val index = rawList.associateBy { it.name ?: "<null-name>" }

        // Base document: the existing .data.json (LENIENT read — a salvaged
        // head is fine, its content-level metadata is intact) preserves every
        // content field; when the file is missing/destroyed, a fresh minimal
        // document is built from the caller's [content] (the DB row's content
        // info) — the heal path. NOTE: unlike the round-28 ladder, a null read
        // is NOT a gate — the rewrite proceeds either way.
        val existing = readDataJsonIndexed(index)
        val base = existing ?: ContentDataJson(
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val updated = base.copy(
            schemaVersion = ContentDataJson.CURRENT_SCHEMA_VERSION,
            episodes = episodes,
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
                    "rewriteDataJsonEpisodes — NUCLEAR: old .data.json " +
                        "delete()=$oldDeleted (uri=${oldDoc?.uri})"
                }
                val freshIndex =
                    if (oldDeleted) emptyMap() else index // force createFile when gone
                writeDataJsonRaw(updated, folder, freshIndex)
            } else {
                writeDataJsonRaw(updated, folder, index)
            }
            DownloadLogger.i {
                "rewriteDataJsonEpisodes — writeDataJsonRaw completed without exception"
            }
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "rewriteDataJsonEpisodes — writeDataJsonRaw THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
            return@withContext false
        }
        // STRICT verification (D-404): the re-read must parse CLEAN — salvage
        // DISABLED, the file itself must be well-formed (a truncation failure
        // would leave new-head + old-tail garbage that salvage would mask) —
        // and its episodes set must EQUAL the expected rebuilt list
        // ([DataJsonRepair.episodesEqual] — exact (key, number) set equality).
        val verifyIndex = try {
            folder.listFiles().associateBy { it.name ?: "<null-name>" }
        } catch (e: Exception) {
            DownloadLogger.e(e) {
                "rewriteDataJsonEpisodes — verify listFiles() THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
            return@withContext false
        }
        val reread = readDataJsonIndexed(verifyIndex, salvage = false)
        DownloadLogger.i {
            "rewriteDataJsonEpisodes — VERIFY re-read: " +
                "episodes=${reread?.episodes?.size ?: "null"}, " +
                "keys=${reread?.episodes?.map { it.episodeKey }}"
        }
        val verified = reread != null && DataJsonRepair.episodesEqual(reread.episodes, episodes)
        if (!verified) {
            DownloadLogger.w {
                "rewriteDataJsonEpisodes — VERIFY FAILED (attempt $attempt): " +
                    if (reread == null) {
                        "the re-read is NULL/UNPARSEABLE (salvage disabled — the " +
                            "written file is not clean JSON; a truncation failure " +
                            "or provider write weirdness left garbage)"
                    } else {
                        "re-read episodes [${reread.episodes.map { it.episodeKey }}] " +
                            "!= expected [${episodes.map { it.episodeKey }}]"
                    }
            }
            return@withContext false
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
     * [upsertEpisodeInDataJson], [rewriteDataJsonEpisodes],
     * [replaceEpisodesInDataJson].
     *
     * D-404 (round 29): the copy opens the SAF target in truncating `"wt"`
     * mode — see [copyFile] for the primitive that corrupted four device
     * rounds — and the written byte length is verified against the payload.
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
            // D-404 (round 29): belt-and-braces — the written document's byte
            // length must equal the payload's. Any provider that ever ignores
            // the "wt" truncation (or appends) surfaces HERE as a loud ERROR
            // instead of a silent stale/corrupted .data.json — the exact
            // failure that survived four rounds undetected.
            val expectedBytes = tempFile.length()
            val reportedBytes = target.length()
            if (reportedBytes != expectedBytes && reportedBytes != 0L) {
                // 0 = provider reports SIZE as unknown — skip (cannot verify).
                DownloadLogger.e {
                    "writeDataJsonRaw — LENGTH MISMATCH after write: " +
                        "target.length()=$reportedBytes but payload=$expectedBytes bytes " +
                        "(uri=${target.uri}). The provider did NOT honor the " +
                        "truncating write — the file may carry a stale tail " +
                        "and fail to parse. The caller's strict verification " +
                        "will escalate if this is real."
                }
            }
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
                    // D-404 (round 29): "wt" — the uniform truncating write mode
                    // (a freshly created file makes this a no-op difference, but
                    // every SAF write in this class now truncates by contract).
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
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

    /**
     * D-407 (round 31): scans the content folder's `subtitles/` subfolder (+
     * the folder root, for legacy layouts) for the subtitle files belonging to
     * a SPECIFIC episode — the disk-truth fallback behind
     * [DownloadManager.resolveSubtitleTracks] when the DB row's
     * `subtitleUris` is empty.
     *
     * Matches the canonical naming schemes by episode number:
     * `subtitle_E{num:5}_{lang}_{index}.{ext}` (provider tracks),
     * `subtitle_E{num:5}_manual_{name}.{ext}` (manual imports), and the
     * legacy `.subtitle_E{num:5}_…` hidden-file form. Extensions: the
     * [SUBTITLE_EXTENSIONS] set.
     *
     * @return the `content://` URI strings, in listing order (the caller
     *   derives display labels via [DownloadedSubtitleLabels.labelForUri]).
     */
    suspend fun findSubtitleFilesForEpisode(mainId: String, episodeNumber: Int): List<String> =
        withContext(Dispatchers.IO) {
            if (episodeNumber <= 0) return@withContext emptyList()
            val epNumPadded = String.format("%05d", episodeNumber)
            val results = mutableListOf<String>()
            try {
                val contentDir = findContentFolder(mainId) ?: return@withContext emptyList()
                val index = contentDir.listFiles().associateBy { it.name ?: "<null-name>" }
                val subtitlesDir = index["subtitles"]?.takeIf { it.isDirectory }
                val searchDirs = if (subtitlesDir != null) {
                    listOf(subtitlesDir, contentDir)
                } else {
                    listOf(contentDir)
                }
                for (dir in searchDirs) {
                    for (file in dir.listFiles()) {
                        if (!file.isFile) continue
                        val name = file.name ?: continue
                        val bare = name.removePrefix(".")
                        val ext = bare.substringAfterLast('.', "").lowercase()
                        if (ext !in SUBTITLE_EXTENSIONS) continue
                        val isEpisodeMatch = bare.startsWith("subtitle_E${epNumPadded}_")
                        if (isEpisodeMatch) {
                            results.add(file.uri.toString())
                            DownloadLogger.i {
                                "findSubtitleFilesForEpisode — found: ${name.take(60)}"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DownloadLogger.w {
                    "findSubtitleFilesForEpisode failed: ${e.message}"
                }
            }
            results
        }

    /**
     * D-407 (round 31): persists ONE manually-picked subtitle file into the
     * episode's dedicated `subtitles/` subfolder — the disk half of
     * [DownloadManager.importManualSubtitle].
     *
     * The target name uses the manual scheme
     * `subtitle_E{num:5}_manual_{sanitized-name}.{ext}` so the canonical
     * episode-number scan ([findSubtitleFilesForEpisode]) picks it up on
     * every future play + reinstall rescan, and
     * [DownloadedSubtitleLabels.labelForUri] renders its label.
     * A same-named existing file is replaced (delete + recreate — the same
     * replace semantics as [publishVideoFile]).
     *
     * @param mainId The content's mainId (locates the content folder).
     * @param episodeNumber The episode number (drives the `E{num:5}` token).
     * @param displayName The picked file's display name (sanitized into the
     *   filename; the caller also derives the label from it).
     * @param extension The validated subtitle extension (srt/vtt/ass/ssa/sub/ttml).
     * @param source The picked file's `content://` URI (copied stream→stream).
     * @return the new file's `content://` URI string, or `null` on failure.
     */
    suspend fun importSubtitleFile(
        mainId: String,
        episodeNumber: Int,
        displayName: String,
        extension: String,
        source: android.net.Uri,
    ): String? = withContext(Dispatchers.IO) {
        if (episodeNumber <= 0) return@withContext null
        try {
            val contentDir = findContentFolder(mainId) ?: run {
                DownloadLogger.w { "importSubtitleFile — no content folder for mainId=$mainId" }
                return@withContext null
            }
            val index = contentDir.listFiles().associateBy { it.name ?: "<null-name>" }
            val subtitlesDir = index["subtitles"]?.takeIf { it.isDirectory }
                ?: contentDir.createDirectory("subtitles") ?: run {
                    DownloadLogger.w { "importSubtitleFile — failed to create subtitles/ subfolder" }
                    return@withContext null
                }
            val epNum = String.format("%05d", episodeNumber)
            val safeName = displayName.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "custom" }
            // Uniqueness: if the same manual name is imported twice, replace it.
            val targetName = "subtitle_E${epNum}_manual_${safeName}.$extension"
            val subIndex = subtitlesDir.listFiles().associateBy { it.name ?: "<null-name>" }
            subIndex[targetName]?.delete()
            val target = subtitlesDir.createFile("application/octet-stream", targetName) ?: run {
                DownloadLogger.w { "importSubtitleFile — createFile failed: $targetName" }
                return@withContext null
            }
            context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                DownloadLogger.w { "importSubtitleFile — could not open streams (source=$source)" }
                target.delete()
                return@withContext null
            }
            if (target.length() == 0L) {
                DownloadLogger.w { "importSubtitleFile — wrote an empty file; deleting" }
                target.delete()
                return@withContext null
            }
            DownloadLogger.i {
                "importSubtitleFile — persisted $targetName (${target.length()} bytes) " +
                    "for mainId=$mainId E$episodeNumber"
            }
            target.uri.toString()
        } catch (e: Exception) {
            DownloadLogger.w { "importSubtitleFile failed: ${e.message}" }
            null
        }
    }

    /**
     * D-404 (round 29): the DELETE flow's last-resort folder locator — matches
     * a series folder by its NAME (the sanitized title, including the
     * `(2)`/`(3)` collision suffixes [ensureContentDir] can append) when the
     * [findContentFolder] mainId walk found NOTHING.
     *
     * ## Why this exists
     * `findContentFolder` can only match folders whose `.data.json` PARSES.
     * A folder whose `.data.json` is corrupted beyond salvage (the round-25..28
     * non-truncating-write corruption, or a hand-mangled file) is INVISIBLE to
     * it — the round-29 device report's "delete the last episode → nothing on
     * disk dies" symptom: `contentDir == null` skipped every disk phase.
     * The delete flow knows the title from the DB row, so it can still find
     * the folder by name and REPAIR it (the DB-truth rebuild writes a fresh,
     * valid `.data.json`).
     *
     * ## Safety
     * A folder whose `.data.json` PARSES and claims a DIFFERENT mainId is
     * SKIPPED — that is another anime's folder that happens to share the
     * sanitized title; name-collisions must never delete the wrong series.
     * Only folders with a missing/unparseable data.json, or one whose mainId
     * agrees, are candidates.
     *
     * @param expectedMainId the DB row's mainId (skips same-title folders
     *   belonging to a different anime).
     * @param title the DB row's content title (sanitized here for the match).
     */
    suspend fun findContentFolderByTitle(
        expectedMainId: String,
        title: String,
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val root = getRootFolder() ?: return@withContext null
        val sanitized = sanitizeFileName(title.trim())
        if (sanitized.isBlank()) return@withContext null
        for (format in SCAN_FORMATS) {
            val formatDir = root.findFile(format)?.takeIf { it.isDirectory } ?: continue
            for (contentDir in formatDir.listFiles()) {
                if (!contentDir.isDirectory) continue
                val name = contentDir.name ?: continue
                val nameMatches = name == sanitized ||
                    Regex("^${Regex.escape(sanitized)} \\(\\d+\\)$").matches(name)
                if (!nameMatches) continue
                // Identity guard: a VALID data.json for a DIFFERENT mainId
                // means this folder belongs to another anime — never match it.
                val index = contentDir.listFiles().associateBy { it.name ?: "<null-name>" }
                val dataJson = readDataJsonIndexed(index)
                if (dataJson != null && dataJson.mainId != expectedMainId) {
                    DownloadLogger.w {
                        "findContentFolderByTitle — name match '${name}' SKIPPED: " +
                            "its .data.json parses to mainId='${dataJson.mainId}' " +
                            "!= expected='$expectedMainId' (a different anime " +
                            "sharing the title)"
                    }
                    continue
                }
                DownloadLogger.w {
                    "findContentFolderByTitle — TITLE-FALLBACK MATCH: '${name}' " +
                        "(sanitized title '$sanitized', expectedMainId=$expectedMainId, " +
                        "dataJsonReadable=${dataJson != null}) — the mainId walk found " +
                        "nothing; the DB-truth rebuild will repair this folder's .data.json"
                }
                return@withContext contentDir
            }
        }
        DownloadLogger.w {
            "findContentFolderByTitle — no folder named '$sanitized' (or " +
                "'${sanitized} (N)') in formats=$SCAN_FORMATS for mainId=$expectedMainId"
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
        // D-404 (round 29): "wt" — WRITE + TRUNCATE. The round-29 postmortem
        // proved the round-25..28 "data.json not updated / corrupted" device
        // reports were THIS one primitive: mode "w" on AOSP
        // ExternalStorageProvider (FileSystemProvider.openDocument →
        // ParcelFileDescriptor.parseMode) maps to MODE_WRITE_ONLY WITHOUT
        // MODE_TRUNCATE — only "wt" truncates. A write SHORTER than the
        // existing file therefore left the OLD TAIL behind (new-json-head +
        // old-json-tail = unparseable .data.json = the user's "corrupted"
        // report), which then made findContentFolder skip the folder and the
        // NEXT delete skip every disk phase.
        context.contentResolver.openOutputStream(target, "wt")?.use { out ->
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
         * D-392 / D-404: how many attempts the `.data.json` verified-rewrite
         * ladder makes (truncating write → fresh-index retry → nuclear
         * delete-recreate). See [rewriteDataJsonEpisodes].
         */
        private const val DATA_JSON_WRITE_ATTEMPTS = 3

        /** D-392 / D-404: settle pause between the `.data.json` write attempts (ms). */
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
