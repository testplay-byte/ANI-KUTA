package com.confused.anikuta.core.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.confused.anikuta.core.content.ContentRecord
import com.confused.anikuta.core.content.ContentDetails
import com.confused.anikuta.core.content.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


/**
 * The scan-on-startup engine per `04-storage-paths.md` §7.
 *
 * Walks the SAF folder on app launch (or when the user picks a new folder), reads
 * each content folder's `data.json`, and UPSERTs the content + downloaded-episode
 * rows into the DB. The DB is a CACHE; the `data.json` files are the durable source
 * of truth for reinstall recognition.
 *
 * REVIEW-5 M65: constructor deps are `(Context, DownloadStorageProvider, DownloadStore,
 * ContentRepository, AnilistDetailRepository)`. The last two come from `:core:content`.
 *
 * DEVIATION (D.1): `AnilistDetailRepository` doesn't exist yet in `:core:content` —
 * the [ContentRepository] already has `upsertContentDetails(ContentDetails)` (D-198:
 * was `upsertAniListDetail(AniListDetail)`). We use the [ContentRepository] for both
 * main_entry + content_details UPSERTs.
 *
 * REVIEW-5 M55: every content folder's `listFiles()` is called ONCE + cached in a
 * local `Map<String, DocumentFile>` index for follow-up name lookups.
 *
 * REVIEW-5 (R1-I7): detects duplicate `mainId` folders + keeps the NEWER one (by
 * `data.json.updatedAt`). Older duplicates are skipped + logged.
 *
 * REVIEW-5 M57: scans the four format folders `video/`, `images/`, `text/`, `audio/`
 * (the original draft had only three — added `audio/` for forward-compat with audio
 * dramas).
 */
class DownloadScanner(
    private val context: Context,
    private val storage: DownloadStorageProvider,
    private val store: DownloadStore,
    private val contentRepository: ContentRepository,
) {

    /**
     * D-248: scans can be triggered concurrently (app startup + folder-URI change
     * observer + setDownloadFolder's explicit rescan). The scanner does read-modify-
     * write on .data.json (replaceEpisodesInDataJson) — interleaved runs clobber
     * each other's writes. Serialized with a mutex; a second concurrent call waits.
     */
    private val scanMutex = Mutex()

    /**
     * Runs the scan-on-startup reconciliation.
     *
     * @return A [ScanReport] with counts of discovered content, registered episodes,
     *   and cleaned-up orphans.
     */
    suspend fun scan(): ScanReport = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            scanLocked()
        }
    }

    private suspend fun scanLocked(): ScanReport = withContext(Dispatchers.IO) {
        val root = storage.getRootFolder() ?: return@withContext ScanReport.EMPTY
        val now = System.currentTimeMillis()

        val scannedEpisodeKeys = mutableSetOf<Pair<String, String>>() // (mainId, episodeKey)
        val seenMainIds = mutableMapOf<String, Long>() // mainId → updatedAt (R1-I7 dup detection)
        var contentCount = 0
        var episodeCount = 0
        var skippedDuplicates = 0
        var skippedUnreadable = 0

        for (format in DownloadStorageProvider.SCAN_FORMATS) {
            val formatDir = root.findFile(format)?.takeIf { it.isDirectory } ?: continue
            for (contentDir in formatDir.listFiles()) {
                if (!contentDir.isDirectory) continue
                // REVIEW-5 M55: listFiles() ONCE per content folder + build the index.
                val index = contentDir.listFiles().associateBy { it.name!! }
                val dataJson = readDataJsonIndexed(index)
                if (dataJson == null) {
                    skippedUnreadable++
                    DownloadLogger.w { "Skipping ${contentDir.name} — data.json unreadable or missing" }
                    continue
                }

                // REVIEW-5 (R1-I7): duplicate mainId detection.
                val prevUpdatedAt = seenMainIds[dataJson.mainId]
                if (prevUpdatedAt != null) {
                    if (dataJson.updatedAt <= prevUpdatedAt) {
                        skippedDuplicates++
                        DownloadLogger.w {
                            "Duplicate mainId ${dataJson.mainId} at ${contentDir.name} — " +
                                "older than previously seen, skipping"
                        }
                        continue
                    }
                    DownloadLogger.w {
                        "Duplicate mainId ${dataJson.mainId} — keeping newer folder ${contentDir.name}"
                    }
                }
                seenMainIds[dataJson.mainId] = dataJson.updatedAt

                // UPSERT the content record (lossless — REVIEW-5 M5: all FK columns restored).
                upsertContentRecord(dataJson)
                contentCount++

                // UPSERT the anilist_detail record (if anilistId is set).
                if (dataJson.anilistId != null) {
                    upsertAniListDetail(dataJson)
                }

                // D-151-fix: write-back — update data.json with the latest DB data.
                // After upsertContentRecord (which ensures the DB has at least the
                // data.json data), fetch the latest ContentRecord + details from the
                // DB (which may have been updated by AniList sync, extension refresh,
                // etc.) and write them back to data.json if any field differs.
                reconcileDataJsonFromContent(contentDir, index, dataJson)

                // Discover episode files. Look in the "episodes" subfolder first (new
                // folder structure), then fall back to the content folder root (legacy).
                val episodesDir = index["episodes"]?.takeIf { it.isDirectory }
                val videoIndex = if (episodesDir != null) {
                    episodesDir.listFiles().associateBy { it.name!! }
                } else {
                    index
                }
                // Subtitle files are in the "subtitles" subfolder (new) or root (legacy).
                val subtitlesDir = index["subtitles"]?.takeIf { it.isDirectory }
                val subtitleIndex = if (subtitlesDir != null) {
                    subtitlesDir.listFiles().associateBy { it.name!! }
                } else {
                    index
                }

                // D-242: build the new episodes list from the on-disk file walk.
                //
                // CRITICAL: match against the EXISTING `.data.json` episodes list by
                // `episodeNumber` (Double) — NOT by re-derived episodeKey. The original
                // episodeKey equals `SEpisode.url` (the extension's episode URL, e.g.
                // "/watch/chainsmoker-cat/ep-4"). The runtime lookup
                // (`downloadManager.isEpisodeDownloaded(mainId, episodeKey)`) uses
                // `SEpisode.url`, so the stored key MUST match. If we re-derive the
                // key as `"$mainId|$epNumPadded"`, the runtime lookup will NEVER match
                // → episodes won't show as Downloaded on the details page.
                //
                // For metadata NOT derivable from the file walk (quality, videoServer,
                // videoAudio, videoUrl, episodeDescription, downloadedAt), reuse values
                // from the matching existing entry — this preserves the metadata that
                // was captured at download time.
                val existingEpisodesByNum = dataJson.episodes
                    .associateBy { it.episodeNumber }
                val rebuiltEpisodes = mutableListOf<DownloadedEpisodeInfo>()

                for ((fileName, file) in videoIndex) {
                    if (!file.isFile) continue
                    if (!isVideoFile(fileName)) continue
                    val derivedNumber = deriveEpisodeNumber(fileName) ?: continue
                    val derivedName = deriveEpisodeName(fileName)

                    // D-242: PRESERVE the original episodeKey from .data.json.
                    // The original key = SEpisode.url (set by HttpDownloader at
                    // download time). If no matching .data.json entry exists,
                    // SKIP the orphan file — don't resurrect it with a derived
                    // key ("$mainId|$epNumPadded") that won't match runtime lookups.
                    // D-242-fix7: this was the root cause of .data.json "not updating"
                    // after deletion — the scanner was re-adding deleted episodes with
                    // a derived key on the next startup.
                    val existing = existingEpisodesByNum[derivedNumber.toDouble()]
                    if (existing == null) {
                        DownloadLogger.w {
                            "scan — orphan video file '$fileName' in ${contentDir.name} " +
                                "has no matching .data.json entry; skipping (would " +
                                "resurrect with derived key that doesn't match runtime)"
                        }
                        continue
                    }
                    val episodeKey = existing.episodeKey
                    val episodeNumber = existing.episodeNumber.toFloat()
                    val episodeName = existing.episodeName ?: derivedName
                    val episodeDescription = existing.episodeDescription

                    // D-FIX-SUB: re-discover subtitle files for this episode.
                    // Searches the subtitles/ subfolder (new) or the content root (legacy).
                    val epNumPadded = String.format("%05d", episodeNumber.toInt())
                    val subtitleUris = findSubtitleUrisForEpisode(subtitleIndex, epNumPadded)

                    store.insertDownloadedEpisode(
                        DownloadedEpisode(
                            content = DownloadContentInfo(
                                mainId = dataJson.mainId,
                                contentId = dataJson.contentId,
                                title = dataJson.title,
                                coverUrl = dataJson.coverUrl,
                                coverColor = null,
                                contentFormat = dataJson.contentFormat,
                                contentType = dataJson.contentType,
                            ),
                            episode = DownloadEpisodeInfo(
                                episodeKey = episodeKey,
                                episodeNumber = episodeNumber,
                                name = episodeName,
                                description = episodeDescription,
                            ),
                            videoUri = file.uri.toString(),
                            subtitleUris = subtitleUris,
                            sizeBytes = file.length(),
                            quality = existing?.quality,
                            completedAt = existing?.downloadedAt ?: dataJson.updatedAt,
                        ),
                    )
                    scannedEpisodeKeys.add(dataJson.mainId to episodeKey)
                    episodeCount++

                    // D-242: build the DownloadedEpisodeInfo for this file.
                    // Preserve ALL metadata from the existing entry (matched by
                    // episodeNumber). Only videoUri + subtitleUris + fileSize are
                    // rebuilt from the on-disk file walk (they change after a
                    // reinstall because the SAF URI changes).
                    rebuiltEpisodes += DownloadedEpisodeInfo(
                        episodeKey = episodeKey,
                        episodeNumber = episodeNumber.toDouble(),
                        episodeUrl = existing?.episodeUrl ?: "",
                        episodeName = episodeName,
                        episodeDescription = episodeDescription,
                        videoUrl = existing?.videoUrl,
                        videoUri = file.uri.toString(),
                        subtitleUris = subtitleUris,
                        quality = existing?.quality,
                        videoServer = existing?.videoServer,
                        audioVariant = existing?.audioVariant,
                        downloadedAt = existing?.downloadedAt ?: dataJson.updatedAt,
                        fileSize = file.length(),
                    )
                }

                // D-242: write the rebuilt episodes list back to .data.json.
                // This is the (re)install-recognition mechanism — after a reinstall,
                // the SQLite DB is empty, but the .data.json files on disk survive
                // (they're in the user-selected SAF folder, not the app's internal
                // storage). The scanner reads each .data.json + rebuilds the
                // episodes list from the actual on-disk files. If the user later
                // reinstalls + selects the same SAF folder, the same scan runs +
                // the same episodes list is restored.
                //
                // Only write if the list actually CHANGED (avoids unnecessary SAF
                // I/O on every startup). The compare is by episodeKey set + each
                // entry's key fields (videoUri + subtitleUris change after reinstall
                // because the SAF URI changes).
                //
                // D-248 ANTI-SHRINK GUARD (user-reported: "downloads disappear from
                // the app even though they are there in the files"): a rebuilt list
                // SMALLER than the durable .data.json list means the file walk missed
                // files (SAF latency, transient listing failure) — NOT that the
                // episodes were deleted (deletions go through deleteDownloadedEpisode
                // which updates .data.json itself). In that case: keep the durable
                // list, don't replace .data.json, and protect ALL existing keys from
                // orphan cleanup this pass so rows + the durable list self-heal on
                // the next healthy scan.
                val existingKeyed = dataJson.episodes.sortedBy { it.episodeKey }
                val rebuiltKeyed = rebuiltEpisodes.sortedBy { it.episodeKey }
                val listsDiffer = existingKeyed.size != rebuiltKeyed.size ||
                    existingKeyed.zip(rebuiltKeyed).any { (a, b) ->
                        a.episodeKey != b.episodeKey ||
                            a.videoUri != b.videoUri ||
                            a.subtitleUris != b.subtitleUris ||
                            a.fileSize != b.fileSize ||
                            a.episodeName != b.episodeName ||
                            a.episodeDescription != b.episodeDescription
                    }
                if (listsDiffer) {
                    if (rebuiltEpisodes.size < dataJson.episodes.size) {
                        // SUSPECTED TRANSIENT WALK FAILURE — protect everything.
                        DownloadLogger.w {
                            "scan — ${contentDir.name}: walk found ${rebuiltEpisodes.size} " +
                                "file(s) but .data.json lists ${dataJson.episodes.size} — " +
                                "keeping durable list + protecting rows (transient SAF glitch?)"
                        }
                        dataJson.episodes.forEach { scannedEpisodeKeys.add(dataJson.mainId to it.episodeKey) }
                    } else {
                        DownloadLogger.i {
                            "scan — updating ${contentDir.name} .data.json episodes list: " +
                                "${dataJson.episodes.size} → ${rebuiltEpisodes.size} episode(s)"
                        }
                        storage.replaceEpisodesInDataJson(contentDir, rebuiltEpisodes)
                    }
                }
            }
        }

        // Reconcile: any DB-downloaded episode NOT in the scanned set is "missing"
        // (folder removed from under us, or user deleted files manually).
        // D-240: GUARD — if the scan found 0 content folders but the DB has
        // episodes, DON'T delete them. This prevents mass data loss when the
        // SAF folder is temporarily inaccessible (permissions revoked, SD card
        // unmounted, etc.). The episodes will be re-verified on the next
        // successful scan.
        // D-248: also skip cleanup when ANY folder was unreadable this pass — a
        // partially-failing walk must never delete the rows of the folders it
        // failed to read (they'd look like "missing" but are simply unscanned).
        var orphansCleaned = 0
        val allDbEpisodes = store.getDownloadedEpisodes()
        if (contentCount == 0 && allDbEpisodes.isNotEmpty()) {
            DownloadLogger.w {
                "scan: 0 content folders found but ${allDbEpisodes.size} DB episodes exist — " +
                    "SKIPPING orphan cleanup (SAF folder may be inaccessible)"
            }
        } else if (skippedUnreadable > 0 && allDbEpisodes.isNotEmpty()) {
            DownloadLogger.w {
                "scan: $skippedUnreadable unreadable folder(s) — SKIPPING orphan cleanup " +
                    "this pass (partial walk must not delete unscanned rows; re-runs on next scan)"
            }
        } else {
            for (ep in allDbEpisodes) {
                val key = ep.content.mainId to ep.episode.episodeKey
                if (key !in scannedEpisodeKeys) {
                    store.markEpisodeMissing(ep.content.mainId, ep.episode.episodeKey)
                    orphansCleaned++
                }
            }
        }

        DownloadLogger.i {
            "scan complete — content=$contentCount, episodes=$episodeCount, " +
                "orphans=$orphansCleaned, dups=$skippedDuplicates, unreadable=$skippedUnreadable"
        }
        ScanReport(
            contentFoldersFound = contentCount,
            episodesRegistered = episodeCount,
            orphansCleanedUp = orphansCleaned,
            duplicatesSkipped = skippedDuplicates,
            unreadableFolders = skippedUnreadable,
            scannedAt = now,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Same as [DownloadStorageProvider.readDataJson] but accepts a pre-built index. */
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
     * UPSERTs a [ContentRecord] built from [data].
     *
     * D-242: SKIP the upsert if a row with this [data.mainId] already exists.
     * The DB is the CANONICAL store — the `.data.json` is a durable backup.
     * Previously, this used `INSERT OR REPLACE` which overwrote the DB row
     * with whatever was in `.data.json` (including null FK fields when the
     * `.data.json` was written by a buggy download flow). This caused the
     * scanner to PROPAGATE nulls from `.data.json` into the DB, defeating
     * `reconcileDataJsonFromContent` (which reads the DB to fill `.data.json`
     * nulls — but the DB was now nulled too).
     *
     * Now: only insert if the row is MISSING (the genuine reinstall case).
     * If the row exists, it's already at least as fresh as `.data.json`, so
     * we leave it alone + let `reconcileDataJsonFromContent` bring `.data.json`
     * up to date with the DB.
     */
    private fun upsertContentRecord(data: ContentDataJson) {
        // D-242: don't overwrite an existing main_entry row — the DB is canonical.
        if (contentRepository.getMainEntryByMainId(data.mainId) != null) {
            DownloadLogger.d {
                "upsertContentRecord — mainId=${data.mainId} already exists in DB; " +
                    "skipping upsert (DB is canonical, .data.json will be reconciled)"
            }
            return
        }
        val now = System.currentTimeMillis()
        val record = ContentRecord(
            mainId = data.mainId,
            contentId = data.contentId,
            title = data.title,
            contentType = data.contentType,
            contentFormat = data.contentFormat,
            dataSourceId = data.dataSourceId,
            systemId = data.systemId,
            extensionRepoId = data.extensionRepoId,
            extensionId = data.extensionId,
            sourceId = data.sourceId,
            animeUrl = data.animeUrl,
            displaySource = data.displaySource,
            createdAt = data.createdAt,
            updatedAt = now,
        )
        DownloadLogger.i {
            "upsertContentRecord — restoring mainId=${data.mainId} from .data.json (reinstall recognition)"
        }
        contentRepository.insertMainEntry(record)
    }

    /**
     * UPSERTs the data-source axis of content_details built from [data] (only when anilistId is set).
     *
     * D-242: `updateDataSourceAxis` is a partial UPDATE — it silently no-ops if
     * the `content_details` row doesn't exist. This was Bug #2 in the downloads-
     * detection research: after a reinstall, the scanner restores `main_entry`
     * but the `content_details` row is missing → `getMainEntryByAniListId`
     * (which INNER JOINs `main_entry` + `content_details`) returns null →
     * `resolveOrCreateForAniList` creates a NEW mainId → downloaded episodes
     * are orphaned under the OLD mainId + don't show on the details page.
     *
     * Fix: ensure the `content_details` row EXISTS before the partial UPDATE.
     * We call `upsertContentDetails` with a minimal row (just mainId) if the
     * row is missing — then `updateDataSourceAxis` can fill in the AniList axis.
     */
    private fun upsertAniListDetail(data: ContentDataJson) {
        val now = System.currentTimeMillis()
        // D-248 FIX (user-reported: library covers vanish after every app restart):
        // the OLD code passed a bare ContentDetails(mainId, axis fields only) to
        // updateDataSourceAxis — which overwrites the ENTIRE data_* axis, nulling
        // data_cover_url / score / synopsis / episodes on EVERY scan (every launch),
        // then reconcileDataJsonFromContent wrote the wiped state back to .data.json.
        // Now: start from the EXISTING row (copy preserves everything), update ONLY
        // the axis identity + timestamp; seed dataCoverUrl from .data.json when the
        // row is fresh.
        val existing = contentRepository.getContentDetails(data.mainId)
        val detail = if (existing != null) {
            existing.copy(
                dataSourceType = if (data.anilistId != null) "anilist" else existing.dataSourceType,
                dataSourceRefId = data.anilistId?.toString() ?: existing.dataSourceRefId,
                dataUpdatedAt = now,
            )
        } else {
            DownloadLogger.i {
                "upsertAniListDetail — creating missing content_details row for mainId=${data.mainId}"
            }
            contentRepository.upsertContentDetails(ContentDetails(mainId = data.mainId))
            ContentDetails(
                mainId = data.mainId,
                dataSourceType = if (data.anilistId != null) "anilist" else null,
                dataSourceRefId = data.anilistId?.toString(),
                dataUpdatedAt = now,
                dataCoverUrl = data.coverUrl?.takeIf { it.isNotBlank() },
            )
        }
        contentRepository.updateDataSourceAxis(detail)
    }

    /**
     * D-151-fix: Write-back — updates the on-disk `.data.json` with the latest
     * ContentRecord + ContentDetails from the DB.
     *
     * The user's scenario: "when the user has some episodes downloaded and
     * refreshes + downloads a new episode, the old data.json should be updated
     * with the proper new description, new data source ID, systemId, extensionRepoId,
     * extensionId, sourceId, animeUrl, anilistId."
     *
     * This runs inside [scan] after [upsertContentRecord] (which ensures the DB has
     * at least the data.json data for reinstall recognition). It then fetches the
     * latest DB state (which may be NEWER than data.json — updated by AniList sync,
     * extension detail refresh, etc.) and writes it back to data.json if any field
     * differs. Only writes if there's an actual change (avoids unnecessary SAF I/O).
     */
    private suspend fun reconcileDataJsonFromContent(
        contentDir: DocumentFile,
        index: Map<String, DocumentFile>,
        dataJson: ContentDataJson,
    ) {
        val record = contentRepository.getMainEntryByMainId(dataJson.mainId) ?: return
        // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
        val details = contentRepository.getContentDetails(dataJson.mainId)

        // D-240: Build the latest DownloadContentInfo from the DB state, BUT
        // preserve non-null .data.json values when the DB-side value is null.
        // This prevents the null-overwrite bug where DB nulls (from incomplete
        // D-198 migration) destroy valid data in .data.json.
        val dbCoverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
        val latest = DownloadContentInfo(
            mainId = record.mainId,
            contentId = record.contentId,
            title = record.title,
            coverUrl = dbCoverUrl ?: dataJson.coverUrl,
            coverColor = null,
            contentFormat = record.contentFormat,
            contentType = record.contentType,
            description = (details?.dataSynopsis ?: details?.extDescription) ?: dataJson.description,
            dataSourceId = record.dataSourceId ?: dataJson.dataSourceId,
            systemId = record.systemId ?: dataJson.systemId,
            extensionRepoId = record.extensionRepoId ?: dataJson.extensionRepoId,
            extensionId = record.extensionId ?: details?.extensionIdLong ?: dataJson.extensionId,
            sourceId = record.sourceId ?: details?.sourceId ?: dataJson.sourceId,
            animeUrl = record.animeUrl ?: details?.animeUrl ?: dataJson.animeUrl,
            displaySource = record.displaySource,
            anilistId = details?.anilistId ?: dataJson.anilistId,
        )

        // Compare key fields — only write if something changed.
        val changed = dataJson.title != latest.title ||
            dataJson.description != latest.description ||
            dataJson.dataSourceId != latest.dataSourceId ||
            dataJson.systemId != latest.systemId ||
            dataJson.extensionRepoId != latest.extensionRepoId ||
            dataJson.extensionId != latest.extensionId ||
            dataJson.sourceId != latest.sourceId ||
            dataJson.animeUrl != latest.animeUrl ||
            dataJson.anilistId != latest.anilistId ||
            dataJson.coverUrl != latest.coverUrl ||
            dataJson.contentType != latest.contentType ||
            dataJson.contentFormat != latest.contentFormat ||
            dataJson.displaySource != latest.displaySource

        if (!changed) return

        DownloadLogger.i {
            "reconcileDataJsonFromContent — updating ${contentDir.name} data.json " +
                "(description changed=${dataJson.description != latest.description}, " +
                "dataSourceId changed=${dataJson.dataSourceId != latest.dataSourceId}, " +
                "sourceId changed=${dataJson.sourceId != latest.sourceId}, " +
                "anilistId changed=${dataJson.anilistId != latest.anilistId})"
        }
        storage.writeDataJson(latest, contentDir, index)
    }

    /** Returns true if [fileName] looks like a downloaded video file. */
    private fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /**
     * Derives the episode key from the file name.
     *
     * The episode key is `"$mainId|$numPadded5"` — derived from the file's
     * `E<num>` segment. Returns `null` if no `E<num>` segment is present (e.g.
     * single-file content like movies).
     */
    private fun deriveEpisodeKey(mainId: String, fileName: String): String? {
        val num = deriveEpisodeNumber(fileName) ?: return null
        val intPart = num.toInt()
        val padded = if (num == intPart.toFloat()) "%05d".format(intPart)
        else "%05d.%s".format(intPart, (num - intPart).toString().removePrefix("0.").trimEnd('0'))
        return "$mainId|$padded"
    }

    /** Extracts the episode number from a file name like `<title> - E00001.mp4`. */
    private fun deriveEpisodeNumber(fileName: String): Float? {
        val regex = Regex(" - E(\\d+(?:\\.\\d+)?)\\.")
        val match = regex.find(fileName) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    /** Derives a human-readable episode name from the file name (best-effort). */
    private fun deriveEpisodeName(fileName: String): String {
        // Strip the extension + the ` - E<num>` suffix → just the title.
        val noExt = fileName.substringBeforeLast('.')
        return noExt.substringBeforeLast(" - E").ifBlank { fileName }
    }

    /**
     * Finds the subtitle `content://` URIs for a given episode by scanning the
     * folder's name→DocumentFile index.
     *
     * D-FIX-SUB: on reinstall / re-scan, the subtitle files exist on disk but the
     * DB had no record of them (previously `subtitleUris = emptyList()` was
     * hard-coded). This recovers them so offline playback works after a reinstall.
     *
     * Recognizes ALL naming conventions (backward-compat):
     * - Current: `subtitle_E{epNumPadded}_{lang}_{index}.{ext}` (no dot prefix)
     * - Previous: `.subtitle_E{epNumPadded}_{lang}_{index}.{ext}` (with dot prefix)
     * - Legacy: `.subtitle_E{epNumPadded}_{index}.{ext}` (pre-fix, no lang)
     *
     * Returns the URIs sorted by the trailing index (so track order is preserved),
     * which matches the order the downloader wrote them.
     */
    private fun findSubtitleUrisForEpisode(
        index: Map<String, DocumentFile>,
        epNumPadded: String,
    ): List<String> {
        // Search for both "subtitle_E" (current) and ".subtitle_E" (previous/legacy).
        val matching = index.entries.filter { (name, _) ->
            (name.startsWith("subtitle_E${epNumPadded}_") || name.startsWith(".subtitle_E${epNumPadded}_")) &&
            name.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTENSIONS
        }
        // Sort by the index segment (the last numeric token before the extension).
        // For both new (`_lang_index.ext`) and legacy (`_index.ext`) formats, the
        // index is the segment immediately before the extension.
        return matching.sortedBy { (name, _) ->
            val withoutExt = name.substringBeforeLast('.')
            val lastSegment = withoutExt.substringAfterLast('_')
            lastSegment.toIntOrNull() ?: 0
        }.map { (_, file) -> file.uri.toString() }
    }

    /** Subtitle file extensions recognized by the scanner + downloader. */
    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

    /**
     * The scan report returned by [scan].
     *
     * @param contentFoldersFound Number of unique content folders discovered.
     * @param episodesRegistered Number of downloaded_episode rows UPSERTed.
     * @param orphansCleanedUp Number of stale DB rows removed (folder deleted externally).
     * @param duplicatesSkipped Number of duplicate-mainId folders skipped (R1-I7).
     * @param unreadableFolders Number of folders skipped due to unreadable data.json.
     * @param scannedAt Epoch millis when the scan ran.
     */
    data class ScanReport(
        val contentFoldersFound: Int,
        val episodesRegistered: Int,
        val orphansCleanedUp: Int,
        val duplicatesSkipped: Int,
        val unreadableFolders: Int,
        val scannedAt: Long,
    ) {
        companion object {
            /** Empty report — returned when there's no root folder set. */
            val EMPTY = ScanReport(0, 0, 0, 0, 0, 0L)
        }
    }

    companion object {
        /** Video file extensions we treat as downloadable content. */
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")
    }
}
