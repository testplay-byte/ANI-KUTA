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
     * D-392 (round 26): the episode-delete orchestration, in FIVE explicit
     * phases. Every phase logs its outcome — a delete is fully diagnosable
     * from logcat alone:
     *
     *  1. **Locate** the content folder by `mainId` ([findContentFolder] —
     *     walks the SAF format folders + matches the `.data.json`).
     *  2. **Files** — delete the episode's video + every subtitle via
     *     `DocumentsContract.deleteDocument` using the URIs from `.data.json`.
     *  3. **`.data.json`** — remove the episode entry from the on-disk
     *     `.data.json` ([DownloadStorageProvider.removeEpisodeFromDataJson] —
     *     now a 3-attempt retry ladder: normal write → fresh-index write →
     *     nuclear delete-recreate).
     *  4. **Series cleanup** (NEW) — if that was the LAST downloaded episode,
     *     delete the ENTIRE series folder (`.data.json`, `.cover.jpg`,
     *     `.nomedia`, `episodes/`, `subtitles/`, the folder itself) and sweep
     *     every `downloaded_episode` DB row for the anime. See
     *     [maybeDeleteSeriesFolder].
     *  5. **DB + cache** — delete the row + refresh the in-memory cache.
     */
    override suspend fun deleteDownloadedEpisode(mainId: String, episodeKey: String) {
        DownloadLogger.i {
            "deleteDownloadedEpisode — ENTER mainId=$mainId, " +
                "episodeKey='$episodeKey' (len=${episodeKey.length})"
        }

        // D-242-fix4: find the content folder ONCE + reuse throughout.
        // Previously findContentFolder was called twice (step 1 + step 3) — the
        // second call could fail because the SAF tree cache was invalidated by
        // the file deletion in step 1, causing removeEpisodeFromDataJson to be
        // silently skipped (the .data.json still had the deleted episode).
        val contentDir = storage.findContentFolder(mainId)
        DownloadLogger.i {
            "deleteDownloadedEpisode — findContentFolder returned: " +
                if (contentDir != null) {
                    "name='${contentDir.name}', uri=${contentDir.uri}, " +
                        "exists=${contentDir.exists()}, isDirectory=${contentDir.isDirectory}"
                } else {
                    "null (no folder with mainId=$mainId found in any format folder)"
                }
        }

        if (contentDir != null) {
            // ── PHASE 2: delete the episode's files ─────────────────────────
            // Read the episode entry from .data.json to get its file URIs.
            // (withContext(IO) — readDataJson is blocking; deleteDownloadedEpisode
            // can be called from the Main dispatcher via the ViewModel.)
            val dataJson = withContext(Dispatchers.IO) {
                runCatching { storage.readDataJson(contentDir) }.getOrNull()
            }
            if (dataJson == null) {
                DownloadLogger.w {
                    "deleteDownloadedEpisode — readDataJson returned null for " +
                        "contentDir=${contentDir.name} (no .data.json or parse failure)"
                }
            } else {
                DownloadLogger.i {
                    "deleteDownloadedEpisode — readDataJson OK: mainId='${dataJson.mainId}', " +
                        "${dataJson.episodes.size} episode(s), " +
                        "keys=${dataJson.episodes.map { it.episodeKey }}"
                }
            }
            val entry = dataJson?.episodes?.firstOrNull { it.episodeKey == episodeKey }
            DownloadLogger.i {
                "deleteDownloadedEpisode — episode entry lookup for '$episodeKey': " +
                    if (entry != null) {
                        "FOUND (videoUri=${entry.videoUri}, " +
                            "subtitleUris=${entry.subtitleUris.size})"
                    } else {
                        "NOT FOUND — file deletion will be skipped (but .data.json " +
                            "update still attempted)"
                    }
            }

            if (entry != null) {
                // Delete video file by URI.
                entry.videoUri?.let { uriStr ->
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
                // Delete subtitle files by URI.
                for (subUriStr in entry.subtitleUris) {
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
            } else {
                DownloadLogger.w {
                    "deleteDownloadedEpisode — episode entry not found in .data.json " +
                        "(episodeKey=$episodeKey); skipping file deletion"
                }
            }

            // ── PHASE 3: remove the episode from the on-disk .data.json ────
            // Done BEFORE deleting the DB row so the .data.json is always consistent.
            // R1-DATA-JSON-STILL: capture the boolean result + log the FULL stack
            // trace on failure (previous code logged only ${e.message}, hiding the
            // actual exception type + stack from the developer).
            // D-392: the write now retries internally (fresh index → nuclear
            // delete-recreate) — the round-26 report caught a single-shot write
            // failing while the DB row still vanished (.data.json out of sync).
            runCatching {
                val removeResult = storage.removeEpisodeFromDataJson(contentDir, episodeKey)
                DownloadLogger.i {
                    "deleteDownloadedEpisode — removeEpisodeFromDataJson returned=$removeResult " +
                        "for episodeKey='$episodeKey' " +
                        "(true=ok OR idempotent no-match; false=no .data.json / write failed " +
                        "after ALL retries / verify failed)"
                }
            }.onFailure { e ->
                DownloadLogger.e(e) {
                    "deleteDownloadedEpisode — removeEpisodeFromDataJson THREW " +
                        "${e.javaClass.simpleName}: ${e.message}. " +
                        "Stack trace logged above. THIS IS LIKELY THE ROOT CAUSE."
                }
            }

            // ── PHASE 4 (D-392): the series-folder cleanup ──────────────────
            // If that was the last downloaded episode, the WHOLE series folder
            // goes (the round-26 device report) — no husk folder with an empty
            // .data.json left behind in the user's file manager.
            maybeDeleteSeriesFolder(mainId, episodeKey, contentDir)
        } else {
            DownloadLogger.w {
                "deleteDownloadedEpisode — content folder not found for mainId=$mainId; " +
                    "skipping .data.json update entirely (file + DB row deletion will still proceed)"
            }
        }

        // ── PHASE 5: delete the DB row + refresh the cache ─────────────────
        // (always — even if prior phases failed, the row must go so the UI
        // reflects the deletion; the next folder scan reconciles the disk.)
        DownloadLogger.i {
            "deleteDownloadedEpisode — deleting DB row for mainId=$mainId, episodeKey='$episodeKey'"
        }
        store.deleteDownloadedEpisode(mainId, episodeKey)

        // 4. Refresh the cache (always — even if prior steps failed).
        refreshDownloadedEpisodes()
        DownloadLogger.i { "deleteDownloadedEpisode — DONE (cache refreshed)" }
    }

    /**
     * D-392 (round 26): decides + performs the series-folder cleanup after an
     * episode delete. The user's rule: *deleting the very last downloaded
     * episode (or the only one) must remove the whole series folder too.*
     *
     * ## The decision — two independent signals
     *  - **(a) Canonical**: the `.data.json` re-read AFTER the removal now
     *    lists ZERO episodes → this was the last episode → delete the folder.
     *  - **(b) Orphan fallback**: the `.data.json` is missing/unreadable AND
     *    the DB has no remaining rows for the anime (this row excluded) → the
     *    folder is pure residue with nothing the app can still play → delete
     *    it too.
     *
     * If (a) says episodes remain, the folder STAYS — nothing to clean.
     *
     * ## On trigger
     *  1. [DownloadStorageProvider.deleteContentFolder] removes the folder —
     *     with its own belt-and-braces safety ladder (not the SAF root, not a
     *     format folder, identity re-confirmed against `expectedMainId` right
     *     before deletion).
     *  2. EVERY `downloaded_episode` row for the anime is swept
     *     ([DownloadStore.deleteDownloadedEpisodesByMainId]) — any survivor
     *     would point into the deleted folder (phantom downloads that fail on
     *     play + confuse the next scan). Sweeping is safe precisely because
     *     the folder is already gone.
     *
     * All decisions + outcomes are logged — this function is the round-26
     * "delete logic robustness" centerpiece.
     */
    private suspend fun maybeDeleteSeriesFolder(
        mainId: String,
        episodeKey: String,
        contentDir: DocumentFile,
    ) {
        // The authoritative remaining-episodes list — re-read AFTER the
        // removal (PHASE 3). withContext(IO): readDataJson is blocking.
        val dataJsonAfter = withContext(Dispatchers.IO) {
            runCatching { storage.readDataJson(contentDir) }.getOrNull()
        }
        val episodesRemaining = dataJsonAfter?.episodes?.size
        // The DB's view AFTER this row is deleted (PHASE 5 runs later — the
        // row is still present right now, hence the -1).
        val dbRowsForAnime = store.getDownloadedEpisodeCountForAnime(mainId)
        val dbRemaining = (dbRowsForAnime - 1).coerceAtLeast(0)

        val shouldDelete = when {
            dataJsonAfter == null -> {
                DownloadLogger.w {
                    "maybeDeleteSeriesFolder — .data.json unreadable after the removal " +
                        "(mainId=$mainId, folder='${contentDir.name}'). Falling back to the " +
                        "DB signal: dbRemaining=$dbRemaining " +
                        "(dbRowsForAnime=$dbRowsForAnime, this row in flight)."
                }
                dbRemaining == 0
            }
            episodesRemaining == 0 -> true
            else -> false
        }

        if (!shouldDelete) {
            DownloadLogger.i {
                "maybeDeleteSeriesFolder — KEEPING the series folder for mainId=$mainId: " +
                    "episodesRemainingInDataJson=$episodesRemaining, dbRemaining=$dbRemaining " +
                    "(not the last episode)"
            }
            return
        }

        DownloadLogger.i {
            "maybeDeleteSeriesFolder — LAST EPISODE detected (episodeKey='$episodeKey'): " +
                "episodesRemainingInDataJson=$episodesRemaining, dbRemaining=$dbRemaining " +
                "→ deleting the WHOLE series folder '${contentDir.name}' " +
                "(.data.json + .cover.jpg + .nomedia + episodes/ + subtitles/ + the folder)"
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
        } else {
            DownloadLogger.w {
                "maybeDeleteSeriesFolder — series-folder deletion FAILED for " +
                    "mainId=$mainId ('${contentDir.name}'). The DB rows are kept — " +
                    "the next folder scan (DownloadScanner) reconciles the state " +
                    "from what actually remains on disk."
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

        val contentDir = storage.findContentFolder(mainId)
        DownloadLogger.i {
            "deleteDownloadedAnime — findContentFolder returned: " +
                if (contentDir != null) {
                    "name='${contentDir.name}', uri=${contentDir.uri}"
                } else {
                    "null — falling back to the per-episode delete loop"
                }
        }

        if (contentDir != null) {
            val folderDeleted = runCatching {
                storage.deleteContentFolder(contentDir, expectedMainId = mainId)
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
                        "'${contentDir.name}' deleted + $sweptRows DB row(s) swept " +
                        "for mainId=$mainId"
                }
                return
            }
            DownloadLogger.w {
                "deleteDownloadedAnime — whole-folder deletion FAILED — falling " +
                    "back to the per-episode delete loop for mainId=$mainId"
            }
        }

        // Fallback: per-episode deletes (handles files + .data.json + rows).
        val rows = store.getDownloadedEpisodes().filter { it.content.mainId == mainId }
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
