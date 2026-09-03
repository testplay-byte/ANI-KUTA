package com.confused.anikuta.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The default [DownloadManager] implementation.
 *
 * D.1.9 + 12-di-wiring.md §11.1: wires [DownloadQueue] + [HttpDownloader] +
 * [HlsDownloader] + [DownloadStorageProvider] + [DownloadScanner] +
 * [DownloadNotificationManager] into a single manager.
 *
 * Public surface:
 *  - All queue operations delegate to [queue].
 *  - [isEpisodeDownloaded] + [getDownloadedEpisodeUri] read directly from [store]
 *    (single-row queries — fast).
 *  - [deleteDownloadedEpisode] deletes from [store] + the SAF folder.
 *  - [requestFolderRescan] calls [scanner.scan] on `Dispatchers.IO`.
 *
 * The [episodeDownloadStates] flow is derived from [queue]'s `tasks` flow — maps
 * each task to a `(DownloadStatus, progress)` pair keyed by `"$mainId|$episodeKey"`.
 * D.2 replaces the placeholder typealias with a proper sealed interface.
 *
 * The private [scope] is `SupervisorJob + Dispatchers.IO` — survives app-backgrounding
 * (with the foreground [DownloadService]). The Koin module injects this scope as a
 * qualified binding (`named("downloadScope")`).
 */
class DefaultDownloadManager(
    private val context: Context,
    private val queue: DownloadQueue,
    private val store: DownloadStore,
    private val storage: DownloadStorageProvider,
    private val scanner: DownloadScanner,
    private val preferences: DownloadPreferences,
    private val notifier: DownloadNotificationManager,
    private val activityTracker: com.confused.anikuta.core.activitytracker.ActivityTracker,
    /**
     * The private scope — survives app-backgrounding. The Koin module binds this as
     * `single(named("downloadScope"))`. Defaults to a new scope if not injected
     * (for tests).
     */
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    ),
) : DownloadManager {

    /**
     * D-401 (round 28): serializes every DELETE operation (single-episode +
     * delete-all). The round-28 device report caught the multi-episode
     * one-by-one delete corrupting the `.data.json`: two in-flight deletes
     * each read the full episodes list before the other's write landed
     * (last-writer-wins resurrected entries), and BOTH computed
     * `dbRemaining = count - 1 > 0`, so NEITHER triggered the last-episode
     * series-folder cleanup — the husk folder + the stale `.data.json`
     * survived on disk. The scanner already carries the same guard
     * (`scanMutex`) for the identical read-modify-write hazard.
     *
     * Lock order: `deleteMutex` → the storage provider's `treeMutex` (never
     * the reverse) — no cycles, no deadlock. [deleteDownloadedAnime]'s
     * per-episode FALLBACK loop runs OUTSIDE this lock and calls the public
     * [deleteDownloadedEpisode] (which re-acquires it per episode) so the
     * non-reentrant Mutex can't self-deadlock.
     */
    private val deleteMutex = Mutex()

    // ── Downloaded-episodes cache (refreshed on every queue change + scan/delete) ─

    private val _downloadedEpisodes = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
    override fun getDownloadedEpisodes(): StateFlow<List<DownloadedEpisode>> =
        _downloadedEpisodes.asStateFlow()

    // ── Per-episode download state (for the details-page UI) ─────────────────

    private val _episodeDownloadStates = MutableStateFlow<Map<String, EpisodeDownloadState>>(emptyMap())
    override val episodeDownloadStates: StateFlow<Map<String, EpisodeDownloadState>>
        get() = _episodeDownloadStates.asStateFlow()

    init {
        // Initial load of the downloaded-episodes cache.
        refreshDownloadedEpisodes()

        // Refresh the per-episode download state flow on every queue change.
        // FIX: combine queue states + downloaded_episodes cache so completed+
        // auto-cleared episodes still show as Downloaded on the details page.
        scope.launch {
            queue.tasks.collect { tasks ->
                val map = mutableMapOf<String, Pair<DownloadStatus, Int>>()
                // 1. Active queue tasks.
                for (task in tasks) {
                    val key = "${task.content.mainId}|${task.episode.episodeKey}"
                    map[key] = task.status to task.progress
                }
                // 2. Downloaded episodes (completed + auto-cleared — not in the queue).
                val downloaded = store.getDownloadedEpisodes()
                for (ep in downloaded) {
                    val key = "${ep.content.mainId}|${ep.episode.episodeKey}"
                    if (key !in map) {
                        map[key] = DownloadStatus.COMPLETED to 100
                    }
                }
                _episodeDownloadStates.value = map.mapValues { (_, pair) ->
                    EpisodeDownloadState(pair.first, pair.second)
                }

                // Best-effort: refresh the downloaded-episodes cache too.
                refreshDownloadedEpisodes()

                // Notify the foreground service of the active task count (so it can
                // update the summary notification + stopSelf when the queue empties).
                val active = tasks.filter { it.status.isActive || it.status == DownloadStatus.QUEUED }
                if (active.isEmpty()) {
                    DownloadService.stop(context)
                }
            }
        }

        // FIX: Also refresh episodeDownloadStates when _downloadedEpisodes changes
        // (covers the case where a download completes + auto-clears from the queue
        // — the downloaded_episode table is the source of truth).
        // D-242-fix3: Also REMOVE stale COMPLETED entries for episodes no longer in
        // the DB (deleted). Without this, the details page showed deleted episodes
        // as "Downloaded" because the map still had the old entry.
        scope.launch {
            _downloadedEpisodes.collect { downloaded ->
                val current = _episodeDownloadStates.value.toMutableMap()
                val downloadedKeys = downloaded
                    .map { "${it.content.mainId}|${it.episode.episodeKey}" }
                    .toSet()
                // Remove COMPLETED entries for episodes no longer in the DB (deleted).
                // Don't touch active states (DOWNLOADING/QUEUED/etc.) — those are queue-owned.
                current.keys
                    .filter { it !in downloadedKeys && current[it]?.first == DownloadStatus.COMPLETED }
                    .forEach { current.remove(it) }
                for (ep in downloaded) {
                    val key = "${ep.content.mainId}|${ep.episode.episodeKey}"
                    if (key !in current || current[key]?.first != DownloadStatus.DOWNLOADING) {
                        current[key] = EpisodeDownloadState(DownloadStatus.COMPLETED, 100)
                    }
                }
                _episodeDownloadStates.value = current
            }
        }

        // D-242-fix: Auto-rescan when the folder URI changes. This is the single
        // source of truth for "the user just picked/changed the SAF folder" —
        // covers ALL folder-selection paths (FirstRunSetupDialog,
        // DownloadSettingsScreen, DownloadsScreen) without requiring each one
        // to remember to call requestFolderRescan(). Without this, picking the
        // folder from FirstRunSetupDialog (the first-run default) leaves the
        // Downloaded page empty until the next app restart.
        scope.launch {
            var lastSeen = preferences.downloadFolderUri.get()
            preferences.downloadFolderUri.changes.collect { uri ->
                if (uri != lastSeen) {
                    lastSeen = uri
                    if (uri.isNotBlank()) {
                        DownloadLogger.i { "Folder URI changed — auto-rescanning" }
                        requestFolderRescan()
                    }
                }
            }
        }
    }

    // ── Queue operations (delegate to [queue]) ───────────────────────────────

    override suspend fun enqueueDownload(request: DownloadRequest): Long {
        DownloadLogger.i {
            "enqueueDownload — mainId=${request.content.mainId}, episode=${request.episode.episodeKey}"
        }
        // D-192: track the download start event
        activityTracker.track(
            eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.DOWNLOAD_START,
            contentKey = request.content.mainId,
            episodeKey = request.episode.episodeKey,
            route = "details",
            contentType = "anime",
        )
        // Start the foreground service BEFORE the queue tries to launch the download
        // (so the 5-second startForeground contract is satisfied on Android 12+).
        DownloadService.start(context)
        val id = queue.enqueue(request)
        queue.tryStartNext()
        return id
    }

    override suspend fun pauseDownload(id: Long) {
        queue.pause(id)
    }

    override suspend fun resumeDownload(id: Long) {
        DownloadService.start(context)
        queue.resume(id)
    }

    override suspend fun cancelDownload(id: Long) {
        queue.cancel(id)
    }

    override suspend fun pauseAll() {
        queue.pauseAll()
    }

    override suspend fun resumeAll() {
        DownloadService.start(context)
        queue.resumeAll()
    }

    override suspend fun cancelAll() {
        queue.cancelAll()
        DownloadService.stop(context)
    }

    override suspend fun retryDownload(id: Long) {
        DownloadService.start(context)
        queue.retry(id)
    }

    override fun getQueue(): StateFlow<List<DownloadTask>> = queue.tasks

    // ── Downloaded-episode operations ────────────────────────────────────────

    override fun isEpisodeDownloaded(mainId: String, episodeKey: String): Boolean =
        store.isEpisodeDownloaded(mainId, episodeKey)

    override fun getDownloadedEpisodeUri(mainId: String, episodeKey: String): String? =
        store.getDownloadedVideoUri(mainId, episodeKey)

    /**
     * D-392 → D-393 → D-401: the episode-delete orchestration — SERIALIZED
     * ([deleteMutex]) and dispatched to [deleteDownloadedEpisodeLocked], whose
     * doc block carries the full phase-by-phase description (D-401's
     * data.json-FIRST order). Every phase logs its outcome — a delete is
     * fully diagnosable from logcat alone.
     */
    override suspend fun deleteDownloadedEpisode(mainId: String, episodeKey: String) {
        // D-401 (round 28): every delete is SERIALIZED — see [deleteMutex].
        // The whole pipeline runs under the lock so the `.data.json`
        // read-modify-write AND the DB-count reads (Phase 4's dbRemaining)
        // see a quiescent tree even when the user fires rapid back-to-back
        // deletes (the round-28 device report's multi-episode corruption).
        deleteMutex.withLock { deleteDownloadedEpisodeLocked(mainId, episodeKey) }
    }

    /**
     * D-401 (round 28) → D-404 (round 29): the episode-deletion pipeline —
     * the user's spec, executed with DB-truth rebuilds + verified writes:
     * *update the `.data.json` of that specific content FIRST, then delete the
     * content properly afterwards, handling each and every single thing
     * properly.*
     *
     * ## The round-29 postmortem (why the round-28 fix still failed on device)
     * The round-28 reorder was correct as far as it went — but the WRITE
     * itself was the bug: `openOutputStream(uri, "w")` does NOT truncate on
     * AOSP ExternalStorageProvider, so the (shorter) post-delete json landed
     * as new-head + old-tail = UNPARSEABLE (the user's "corrupted" report).
     * The unparseable file then made `findContentFolder` skip the folder →
     * the LAST-episode delete skipped every disk phase → "folder stays,
     * data.json stays, file stays". The 1-episode case always looked clean
     * only because the folder-delete path proceeds on unreadable json.
     *
     * ## The phases (each logged; failures are loud, never silent)
     *
     *  - **PHASE 0 — DB capture.** ALL rows for the anime (the rebuild's
     *    input) + this row's episodeNumber (the disk sweep keys on it) + its
     *    videoUri (a deletion URI that does NOT depend on the `.data.json`)
     *    + its content info (the metadata + title fallback for the locate).
     *    Captured BEFORE anything mutates.
     *  - **PHASE 1 — locate.** `findContentFolder(mainId)`; when that finds
     *    NOTHING (a corrupted-beyond-salvage json makes the folder invisible)
     *    the D-404 TITLE FALLBACK locates it by sanitized name — the rebuild
     *    then repairs its `.data.json`.
     *  - **PHASE 2 — `.data.json` FIRST, rebuilt from DB truth.** (2a) A
     *    LENIENT read (salvage applies) captures the episode's entry (its
     *    URIs feed the Phase-3a deletes; `DeletionMatching.matchRemoval`
     *    reconciles key drift). (2b) The NEW episodes list is DERIVED FROM
     *    THE DB — rows-for-anime minus this row, metadata enriched from the
     *    existing entries ([DataJsonRepair.rebuildEpisodesAfterDelete]) — no
     *    match-and-pray removal, no key-drift hole, no ghost entries — and
     *    written via the VERIFIED ladder
     *    ([DownloadStorageProvider.rewriteDataJsonEpisodes]: truncating
     *    `"wt"` write → strict re-read must parse CLEAN and EQUAL the
     *    expected set → retry → nuclear delete+recreate). The write lands
     *    while the tree is untouched.
     *  - **PHASE 3 — the files.** (3a) URI deletes of the CAPTURED video +
     *    subtitle URIs (best-effort — the `.data.json` entry's, with the DB
     *    row's videoUri as the fallback). (3b) The disk-truth sweep
     *    ([DownloadStorageProvider.deleteEpisodeFilesOnDisk]) keyed on the
     *    episode number, with retry passes + a survivors report.
     *  - **PHASE 4 — series-folder cleanup.** If the DB says that was the
     *    LAST episode (dbRemaining == 0 — accurate under the lock), the
     *    whole series folder goes (identity-checked safety ladder) + every
     *    DB row for the anime is swept. See [maybeDeleteSeriesFolder].
     *  - **PHASE 5 — DB row + cache.** The row dies LAST — only after the
     *    disk state is consistent — then the in-memory cache refreshes.
     */
    private suspend fun deleteDownloadedEpisodeLocked(mainId: String, episodeKey: String) {
        DownloadLogger.i {
            "deleteDownloadedEpisode — ENTER mainId=$mainId, " +
                "episodeKey='$episodeKey' (len=${episodeKey.length})"
        }

        // ── PHASE 0: DB capture — before anything mutates ──────────────
        // D-393: capture the episode's NUMBER (the disk sweep keys on it; the
        // row dies in Phase 5, after which the number is unrecoverable).
        // D-401: ALSO capture the row's videoUri — a deletion URI that does
        // not depend on the `.data.json` (belt and braces for the URI deletes
        // when the entry is stale/missing).
        // D-404: capture ALL rows for the anime — the DB-truth rebuild input
        // (rows minus this row = exactly what the durable `.data.json` must
        // list after the delete) + the content info (metadata fallback for
        // the rewrite + the title for the Phase-1 locate fallback).
        val allRowsForAnime = withContext(Dispatchers.IO) {
            runCatching {
                store.getDownloadedEpisodes().filter { it.content.mainId == mainId }
            }.getOrDefault(emptyList())
        }
        val dbRow = allRowsForAnime.find { it.episode.episodeKey == episodeKey }
        val episodeNumber = dbRow?.episode?.episodeNumber
        val dbVideoUri = dbRow?.videoUri
        var contentInfo: DownloadContentInfo? =
            dbRow?.content ?: allRowsForAnime.firstOrNull()?.content
        DownloadLogger.i {
            "deleteDownloadedEpisode — DB capture for mainId=$mainId: " +
                "${allRowsForAnime.size} row(s) total, " +
                if (dbRow != null) {
                    "row FOUND (episodeNumber=$episodeNumber, videoUri=$dbVideoUri, " +
                        "title='${dbRow.content.title}')"
                } else {
                    "row NOT FOUND (already deleted or phantom) — the disk sweep " +
                        "is skipped; the rebuild still heals the `.data.json` from " +
                        "the surviving rows"
                }
        }

        // ── PHASE 1: locate the content folder ───────────────────────
        // D-242-fix4: findContentFolder once + reuse throughout.
        // D-404: when the mainId walk finds NOTHING, a TITLE fallback runs —
        // a folder whose `.data.json` is destroyed (unparseable + unsalvageable)
        // is invisible to the mainId walk; the delete flow knows the title
        // from the DB rows. Located-by-title folders get their `.data.json`
        // REPAIRED by the Phase-2 rebuild.
        var contentDir = storage.findContentFolder(mainId)
        if (contentDir == null && contentInfo != null) {
            contentDir = storage.findContentFolderByTitle(mainId, contentInfo.title)
        }
        DownloadLogger.i {
            "deleteDownloadedEpisode — locate: " +
                if (contentDir != null) {
                    "folder name='${contentDir.name}', uri=${contentDir.uri}, " +
                        "exists=${contentDir.exists()}, isDirectory=${contentDir.isDirectory}"
                } else {
                    "NO folder (mainId walk + title fallback both empty) — no " +
                        ".data.json update, no file deletion; ONLY the DB row is " +
                        "removed (orphans, if any, are logged + reconciled on scan)"
                }
        }

        // (D-404: `folder` — a val snapshot for the phases below; smart casts
        // + lambda captures stay simple.)
        val folder = contentDir
        if (folder != null) {
            // ── PHASE 2a: lenient read + capture the episode's entry ─────
            // The entry's videoUri + subtitleUris feed the Phase-3a deletes;
            // the lenient read SALVAGES a corrupted file (D-404) so the entry
            // is still recoverable from the new-json head.
            val dataJson = withContext(Dispatchers.IO) {
                runCatching { storage.readDataJson(folder) }.getOrNull()
            }
            if (dataJson == null) {
                DownloadLogger.w {
                    "deleteDownloadedEpisode — readDataJson returned null for " +
                        "folder=${folder.name} (no .data.json, or corrupted " +
                        "beyond salvage) — the Phase-2b rebuild will RE-CREATE it " +
                        "from the DB truth"
                }
            } else {
                DownloadLogger.i {
                    "deleteDownloadedEpisode — readDataJson OK: " +
                        "mainId='${dataJson.mainId}', ${dataJson.episodes.size} " +
                        "episode(s), keys=${dataJson.episodes.map { it.episodeKey }}"
                }
            }
            // The content-info fallback chain completes here: DB rows first,
            // then the (possibly salvaged) json's own content fields.
            if (contentInfo == null && dataJson != null) {
                contentInfo = DownloadContentInfo(
                    mainId = dataJson.mainId,
                    contentId = dataJson.contentId,
                    title = dataJson.title,
                    coverUrl = dataJson.coverUrl,
                    contentFormat = dataJson.contentFormat,
                    contentType = dataJson.contentType,
                    description = dataJson.description,
                    dataSourceId = dataJson.dataSourceId,
                    systemId = dataJson.systemId,
                    extensionRepoId = dataJson.extensionRepoId,
                    extensionId = dataJson.extensionId,
                    sourceId = dataJson.sourceId,
                    animeUrl = dataJson.animeUrl,
                    displaySource = dataJson.displaySource,
                    anilistId = dataJson.anilistId,
                )
            }
            // D-404: entry capture via the unit-tested matcher (key match →
            // episodeNumber key-drift reconciliation) — used ONLY to select
            // the URI-deletion targets, never to gate the write.
            val capturedEntries = dataJson?.episodes?.let {
                DeletionMatching.matchRemoval(it, episodeKey, episodeNumber?.toDouble()).removed
            }.orEmpty()
            val entry = capturedEntries.firstOrNull()
            DownloadLogger.i {
                "deleteDownloadedEpisode — entry capture for '$episodeKey': " +
                    if (entry != null) {
                        "FOUND (videoUri=${entry.videoUri}, " +
                            "subtitleUris=${entry.subtitleUris.size})"
                    } else {
                        "NOT FOUND in .data.json (stale, salvaged-partial, or " +
                            "already removed) — the URI deletes fall back to the " +
                            "DB row's videoUri"
                    }
            }

            // ── PHASE 2b: REBUILD + WRITE + VERIFY the `.data.json` FIRST ─
            // (D-404 — the user's pipeline, now with DB-truth semantics: the
            // durable file's episodes list := exactly the DB's surviving
            // rows, so file and app can never disagree. No matching, no
            // drift, no ghosts; a corrupted/missing file is HEALED by the
            // same write.)
            var dataJsonUpdated = false
            if (contentInfo != null) {
                val rebuiltEpisodes = DataJsonRepair.rebuildEpisodesAfterDelete(
                    existingEpisodes = dataJson?.episodes,
                    survivingDbRows = allRowsForAnime,
                    deletedEpisodeKey = episodeKey,
                    deletedEpisodeNumber = episodeNumber?.toDouble(),
                )
                DownloadLogger.i {
                    "deleteDownloadedEpisode — DB-truth rebuild: " +
                        "${dataJson?.episodes?.size ?: "null"} existing entry/entries → " +
                        "${rebuiltEpisodes.size} rebuilt (surviving DB rows = " +
                        "${allRowsForAnime.size - if (dbRow != null) 1 else 0}), " +
                        "keys=${rebuiltEpisodes.map { it.episodeKey }}"
                }
                dataJsonUpdated = runCatching {
                    storage.rewriteDataJsonEpisodes(folder, contentInfo!!, rebuiltEpisodes)
                }.onFailure { e ->
                    DownloadLogger.e(e) {
                        "deleteDownloadedEpisode — rewriteDataJsonEpisodes THREW " +
                            "${e.javaClass.simpleName}: ${e.message}."
                    }
                }.getOrDefault(false)
                DownloadLogger.i {
                    "deleteDownloadedEpisode — rewriteDataJsonEpisodes " +
                        "returned=$dataJsonUpdated for episodeKey='$episodeKey' " +
                        "(true = written + VERIFIED; false = read/write/verify " +
                        "failed after ALL retries — the entry may persist in " +
                        ".data.json)"
                }
                if (!dataJsonUpdated) {
                    DownloadLogger.e {
                        "deleteDownloadedEpisode — .data.json REWRITE UNVERIFIED for " +
                            "'$episodeKey' — the file deletion + DB row deletion still " +
                            "proceed (the episode must die functionally), but the " +
                            ".data.json may be stale (a rescan reconciles it from " +
                            "disk truth)."
                    }
                }
            } else {
                DownloadLogger.e {
                    "deleteDownloadedEpisode — PHASE 2b SKIPPED: no content info " +
                        "available (no DB rows, no readable .data.json) — cannot " +
                        "rebuild the file; proceeding with the file deletes"
                }
            }

            // ── PHASE 3a: delete the episode's files by their captured URIs ──
            // (best-effort — stale URIs are exactly why Phase 3b exists).
            val videoUriToDelete = entry?.videoUri ?: dbVideoUri
            videoUriToDelete?.let { uriStr ->
                runCatching {
                    val uri = android.net.Uri.parse(uriStr)
                    val deleted = android.provider.DocumentsContract.deleteDocument(
                        context.contentResolver, uri,
                    )
                    DownloadLogger.i {
                        "Deleted video file: $uriStr (deleteDocument returned=$deleted)"
                    }
                }.onFailure {
                    DownloadLogger.e(it) {
                        "Failed to delete video $uriStr: ${it.javaClass.simpleName}: ${it.message}"
                    }
                }
            }
            // Delete subtitle files by URI (the `.data.json` entry's list —
            // the DB row carries no subtitle URIs).
            for (subUriStr in entry?.subtitleUris.orEmpty()) {
                runCatching {
                    val uri = android.net.Uri.parse(subUriStr)
                    val deleted = android.provider.DocumentsContract.deleteDocument(
                        context.contentResolver, uri,
                    )
                    DownloadLogger.i {
                        "Deleted subtitle file: $subUriStr (deleteDocument returned=$deleted)"
                    }
                }.onFailure {
                    DownloadLogger.e(it) {
                        "Failed to delete subtitle $subUriStr: ${it.javaClass.simpleName}: ${it.message}"
                    }
                }
            }

            // ── PHASE 3b: the DISK-TRUTH sweep + verification ────────────
            // Deletes by the canonical FILENAME pattern (independent of
            // `.data.json`), then re-lists to VERIFY. This is the on-disk
            // guarantee: the episode's files are actually GONE.
            if (episodeNumber != null) {
                val sweep = runCatching {
                    storage.deleteEpisodeFilesOnDisk(folder, episodeNumber)
                }.onFailure { e ->
                    DownloadLogger.e(e) {
                        "deleteDownloadedEpisode — the disk sweep THREW " +
                            "${e.javaClass.simpleName}: ${e.message}"
                    }
                }.getOrNull()
                if (sweep != null && sweep.survivors.isNotEmpty()) {
                    DownloadLogger.e {
                        "deleteDownloadedEpisode — DISK SWEEP INCOMPLETE: " +
                            "${sweep.survivors.size} file(s) for episode " +
                            "$episodeNumber SURVIVED the deletion passes in " +
                            "'${folder.name}': " +
                            sweep.survivors
                    }
                }
            } else {
                DownloadLogger.w {
                    "deleteDownloadedEpisode — no episode number from the DB row " +
                        "(row already gone?); the disk sweep is skipped — the " +
                        "folder-level cleanup (Phase 4) still applies if this " +
                        "was the last episode"
                }
            }

            // ── PHASE 4: the series-folder cleanup ───────────────────────
            // If that was the last downloaded episode, the WHOLE series folder
            // goes — no husk folder with a stale `.data.json` left behind in
            // the user's file manager.
            maybeDeleteSeriesFolder(mainId, episodeKey, folder)
        } else {
            // D-393: the message tells the truth — with no content folder
            // located, NEITHER the `.data.json` update NOR the file deletes can
            // run; only the DB row (Phase 5) is removed.
            DownloadLogger.e {
                "deleteDownloadedEpisode — content folder NOT FOUND for " +
                    "mainId=$mainId: no .data.json update, no file deletion — " +
                    "ONLY the DB row is removed. If files for this anime exist " +
                    "on disk, they are ORPHANS (a folder rescan will re-import " +
                    "or they must be deleted manually)"
            }
        }

        // ── PHASE 5: delete the DB row + refresh the cache ───────────────
        // (always — even if prior phases failed, the row must go so the UI
        // reflects the deletion; the next folder scan reconciles the disk.)
        DownloadLogger.i {
            "deleteDownloadedEpisode — deleting DB row for mainId=$mainId, episodeKey='$episodeKey'"
        }
        store.deleteDownloadedEpisode(mainId, episodeKey)

        refreshDownloadedEpisodes()
        DownloadLogger.i { "deleteDownloadedEpisode — DONE (cache refreshed)" }
    }

    /**
     * D-392 → D-393 (round 27): decides + performs the series-folder cleanup
     * after an episode delete. The user's rule: *deleting the very last
     * downloaded episode (or the only one) must remove the whole series folder
     * too.*
     *
     * ## The decision (D-393 — the DB is the truth)
     * The round-26 version keyed on the `.data.json` re-read listing ZERO
     * episodes — but a STALE `.data.json` (ghost entries from the round-25
     * sync bug) kept the count above zero and blocked the folder delete,
     * while a `.data.json` with zero entries but live DB rows would have
     * WRONGLY nuked playable files. The DB row count is immune to both:
     * `dbRemaining == 0` (after this row's in-flight deletion) is exactly the
     * user's "that was the last episode" — and when it fires, the folder
     * delete removes EVERYTHING (real files, ghost entries, cover, the husk
     * itself), which is precisely what the round-27 report demanded.
     *
     * ## On trigger
     *  1. [DownloadStorageProvider.deleteContentFolder] removes the folder —
     *     with its own belt-and-braces safety ladder (not the SAF root, not a
     *     format folder, identity re-confirmed against `expectedMainId` right
     *     before deletion).
     *  2. EVERY `downloaded_episode` row for the anime is swept
     *     ([DownloadStore.deleteDownloadedEpisodesByMainId]) — any survivor
     *     would point into the deleted folder (phantom downloads that fail
     *     on play + confuse the next scan).
     *  3. If the folder delete itself fails, the sweep still runs WHEN THE
     *     DISK agrees nothing playable remains (zero episode video files) —
     *     and an ERROR log names exactly what survived otherwise.
     *
     * All decisions + outcomes are logged — this function is the round-26/27
     * "delete logic robustness" centerpiece.
     */
    private suspend fun maybeDeleteSeriesFolder(
        mainId: String,
        episodeKey: String,
        contentDir: DocumentFile,
    ) {
        // The DB's view AFTER this row is deleted (PHASE 5 runs later — the
        // row is still present right now, hence the -1).
        val dbRowsForAnime = store.getDownloadedEpisodeCountForAnime(mainId)
        val dbRemaining = (dbRowsForAnime - 1).coerceAtLeast(0)

        if (dbRemaining > 0) {
            DownloadLogger.i {
                "maybeDeleteSeriesFolder — KEEPING the series folder for " +
                    "mainId=$mainId: dbRemaining=$dbRemaining " +
                    "(dbRowsForAnime=$dbRowsForAnime, this row in flight) — " +
                    "not the last episode"
            }
            return
        }

        DownloadLogger.i {
            "maybeDeleteSeriesFolder — LAST EPISODE detected " +
                "(episodeKey='$episodeKey'): dbRemaining=$dbRemaining " +
                "→ deleting the WHOLE series folder '${contentDir.name}' " +
                "(.data.json + .cover.jpg + .nomedia + episodes/ + subtitles/ + " +
                "the folder + any ghost entries)"
        }
        val folderDeleted = runCatching {
            storage.deleteContentFolder(contentDir, expectedMainId = mainId)
        }.onFailure { e ->
            DownloadLogger.e(e) {
                "maybeDeleteSeriesFolder — deleteContentFolder THREW " +
                    "${e.javaClass.simpleName}: ${e.message}"
            }
        }.getOrDefault(false)

        if (folderDeleted) {
            // Sweep every DB row for the anime — survivors would reference
            // files inside the now-deleted folder.
            val sweptRows = store.deleteDownloadedEpisodesByMainId(mainId)
            DownloadLogger.i {
                "maybeDeleteSeriesFolder — series folder DELETED + swept " +
                    "$sweptRows DB row(s) for mainId=$mainId (the in-flight row's " +
                    "PHASE-5 delete is now a harmless no-op)"
            }
            return
        }

        // The folder delete failed — check whether anything PLAYABLE survives
        // on disk before deciding whether the DB rows can be swept safely.
        val videosRemaining = withContext(Dispatchers.IO) {
            runCatching { storage.countEpisodeVideoFiles(contentDir) }.getOrDefault(-1)
        }
        if (videosRemaining == 0) {
            val sweptRows = store.deleteDownloadedEpisodesByMainId(mainId)
            DownloadLogger.w {
                "maybeDeleteSeriesFolder — folder delete FAILED but the disk " +
                    "shows ZERO episode videos remain (only metadata residue) — " +
                    "swept $sweptRows DB row(s) for mainId=$mainId; the husk folder " +
                    "remains on disk (a rescan or manual delete clears it)"
            }
        } else {
            DownloadLogger.e {
                "maybeDeleteSeriesFolder — series-folder deletion FAILED for " +
                    "mainId=$mainId ('${contentDir.name}') and $videosRemaining " +
                    "episode video(s) still exist on disk — keeping the DB rows so " +
                    "the episodes stay playable; the next folder scan reconciles. " +
                    "THE FILES MUST BE CHECKED (pull-refresh re-triggers the sweep)."
            }
        }
    }

    /**
     * D-392 (round 26): the "delete all" path — removes EVERY downloaded
     * episode of [mainId] in one atomic operation.
     *
     * Primary path: locate the series folder ONCE, delete it WHOLE (the same
     * [DownloadStorageProvider.deleteContentFolder] safety ladder — identity
     * re-confirmed, never the root, never a format folder), then sweep every
     * `downloaded_episode` row for the anime. One folder walk, one deletion —
     * the old per-episode loop did N walks (findContentFolder reads every
     * `.data.json` in the tree per call) and left the empty husk folder.
     *
     * Fallback path (folder not found OR its deletion failed): a sequential
     * [deleteDownloadedEpisode] loop — each iteration handles its own files +
     * `.data.json` entry + row, so the DB still ends consistent even when the
     * folder can't be touched.
     */
    override suspend fun deleteDownloadedAnime(mainId: String) {
        DownloadLogger.i { "deleteDownloadedAnime — ENTER mainId=$mainId (delete-all)" }

        // D-404: rows are fetched up-front (they were previously only read in
        // the fallback loop) — the first row's title feeds the TITLE-FALLBACK
        // locate when the mainId walk finds nothing (a folder whose
        // .data.json is corrupted beyond salvage is invisible to the mainId
        // walk; delete-all must still be able to remove the whole folder).
        val rows = store.getDownloadedEpisodes().filter { it.content.mainId == mainId }
        var contentDir = storage.findContentFolder(mainId)
        if (contentDir == null && rows.isNotEmpty()) {
            contentDir = storage.findContentFolderByTitle(mainId, rows.first().content.title)
        }
        DownloadLogger.i {
            "deleteDownloadedAnime — locate: " +
                if (contentDir != null) {
                    "name='${contentDir.name}', uri=${contentDir.uri}"
                } else {
                    "null — falling back to the per-episode delete loop"
                }
        }

        // D-401: the PRIMARY path (atomic whole-folder delete) runs under the
        // delete mutex. The FALLBACK loop below deliberately runs OUTSIDE it
        // — it calls the PUBLIC deleteDownloadedEpisode, which re-acquires the
        // (non-reentrant) mutex per episode, so it can never self-deadlock.
        // (D-404: `locatedDir` — a val snapshot of the possibly-fallback-located
        // folder; smart casts + lambda capture both stay simple.)
        val locatedDir = contentDir
        deleteMutex.withLock {
            if (locatedDir != null) {
                val folderDeleted = runCatching {
                    storage.deleteContentFolder(locatedDir, expectedMainId = mainId)
                }.onFailure { e ->
                    DownloadLogger.e(e) {
                        "deleteDownloadedAnime — deleteContentFolder THREW " +
                            "${e.javaClass.simpleName}: ${e.message}"
                    }
                }.getOrDefault(false)

                if (folderDeleted) {
                    val sweptRows = store.deleteDownloadedEpisodesByMainId(mainId)
                    refreshDownloadedEpisodes()
                    DownloadLogger.i {
                        "deleteDownloadedAnime — DONE: series folder " +
                            "'${locatedDir.name}' deleted + $sweptRows DB row(s) swept " +
                            "for mainId=$mainId"
                    }
                    return
                }
                DownloadLogger.w {
                    "deleteDownloadedAnime — whole-folder deletion FAILED — falling " +
                        "back to the per-episode delete loop for mainId=$mainId"
                }
            }
        }

        // Fallback: per-episode deletes (handles files + .data.json + rows).
        // D-404: `rows` was hoisted above the locate — the fallback reuses it
        // (a fresh read would also be fine, but the hoisted snapshot is the
        // same list this operation started with).
        DownloadLogger.i {
            "deleteDownloadedAnime — fallback loop over ${rows.size} episode(s) " +
                "for mainId=$mainId"
        }
        rows.forEach { episode ->
            deleteDownloadedEpisode(mainId, episode.episode.episodeKey)
        }
        refreshDownloadedEpisodes()
        DownloadLogger.i { "deleteDownloadedAnime — DONE (fallback path, mainId=$mainId)" }
    }

    override suspend fun requestFolderRescan() {
        DownloadLogger.i { "requestFolderRescan — triggering DownloadScanner.scan()" }
        scanner.scan()
        refreshDownloadedEpisodes()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Reloads the downloaded-episodes cache from the DB. */
    private fun refreshDownloadedEpisodes() {
        _downloadedEpisodes.value = store.getDownloadedEpisodes()
    }

    /**
     * Checks whether the current network is allowed for downloads (Wi-Fi-only pref
     * enforcement). Used by the queue's `connectivityCheck` callback.
     *
     * Returns `true` if:
     *  - `wifiOnly` is OFF (any network is allowed), OR
     *  - `wifiOnly` is ON AND the active network is Wi-Fi (or ethernet).
     *
     * Returns `true` on error (fail-open — better to attempt the download than to
     * silently stall the queue).
     */
    fun isNetworkAllowed(): Boolean {
        if (!preferences.wifiOnly.get()) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(network) ?: return true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            DownloadLogger.w { "isNetworkAllowed — connectivity check failed (fail-open): ${e.message}" }
            true
        }
    }
}
