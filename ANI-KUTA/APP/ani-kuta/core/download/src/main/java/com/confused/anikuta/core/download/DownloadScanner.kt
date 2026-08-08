package com.confused.anikuta.core.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.confused.anikuta.core.content.AniListDetail
import com.confused.anikuta.core.content.ContentRecord
import com.confused.anikuta.core.content.ContentRepository
import kotlinx.coroutines.Dispatchers
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
 * the [ContentRepository] already has `upsertAniListDetail(AniListDetail)`. We use
 * the [ContentRepository] for both content + anilist UPSERTs. When D.2 introduces a
 * dedicated `AnilistDetailRepository`, this scanner should be updated to inject it
 * as a separate dep (per REVIEW-5 M65).
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
     * Runs the scan-on-startup reconciliation.
     *
     * @return A [ScanReport] with counts of discovered content, registered episodes,
     *   and cleaned-up orphans.
     */
    suspend fun scan(): ScanReport = withContext(Dispatchers.IO) {
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

                for ((fileName, file) in videoIndex) {
                    if (!file.isFile) continue
                    if (!isVideoFile(fileName)) continue
                    val episodeKey = deriveEpisodeKey(dataJson.mainId, fileName) ?: continue
                    val episodeNumber = deriveEpisodeNumber(fileName) ?: continue
                    val episodeName = deriveEpisodeName(fileName)

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
                            ),
                            videoUri = file.uri.toString(),
                            subtitleUris = subtitleUris,
                            sizeBytes = file.length(),
                            quality = null,
                            completedAt = dataJson.updatedAt,
                        ),
                    )
                    scannedEpisodeKeys.add(dataJson.mainId to episodeKey)
                    episodeCount++
                }
            }
        }

        // Reconcile: any DB-downloaded episode NOT in the scanned set is "missing"
        // (folder removed from under us, or user deleted files manually).
        var orphansCleaned = 0
        val allDbEpisodes = store.getDownloadedEpisodes()
        for (ep in allDbEpisodes) {
            val key = ep.content.mainId to ep.episode.episodeKey
            if (key !in scannedEpisodeKeys) {
                store.markEpisodeMissing(ep.content.mainId, ep.episode.episodeKey)
                orphansCleaned++
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

    /** UPSERTs a [ContentRecord] built from [data] (REVIEW-5 M5 — lossless). */
    private fun upsertContentRecord(data: ContentDataJson) {
        val now = System.currentTimeMillis()
        val record = ContentRecord(
            mainId = data.mainId,
            contentId = data.contentId,
            title = data.title,
            contentType = data.contentType,
            contentFormat = data.contentFormat,
            description = data.description,
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
        // ContentRepository.insertContent uses INSERT OR REPLACE — UPSERT semantics.
        contentRepository.insertContent(record)
    }

    /** UPSERTs an [AniListDetail] built from [data] (only when anilistId is set). */
    private fun upsertAniListDetail(data: ContentDataJson) {
        val detail = AniListDetail(
            mainId = data.mainId,
            anilistId = data.anilistId!!,
            updatedAt = System.currentTimeMillis(),
        )
        contentRepository.upsertAniListDetail(detail)
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
     * Recognizes BOTH naming conventions (backward-compat with pre-fix downloads):
     * - New: `.subtitle_E{epNumPadded}_{lang}_{index}.{ext}` (D-FIX-SUB)
     * - Legacy: `.subtitle_E{epNumPadded}_{index}.{ext}` (pre-fix)
     *
     * Returns the URIs sorted by the trailing index (so track order is preserved),
     * which matches the order the downloader wrote them.
     */
    private fun findSubtitleUrisForEpisode(
        index: Map<String, DocumentFile>,
        epNumPadded: String,
    ): List<String> {
        val prefix = ".subtitle_E${epNumPadded}_"
        val matching = index.entries.filter { (name, _) ->
            name.startsWith(prefix) && name.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTENSIONS
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
