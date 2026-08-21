package com.confused.anikuta.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
            // 1. Read the episode entry from .data.json to get its file URIs.
            val dataJson = storage.readDataJson(contentDir)
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

            // 2. Remove this episode from the on-disk `.data.json` episodes list.
            // Done BEFORE deleting the DB row so the .data.json is always consistent.
            // R1-DATA-JSON-STILL: capture the boolean result + log the FULL stack
            // trace on failure (previous code logged only ${e.message}, hiding the
            // actual exception type + stack from the developer).
            runCatching {
                val removeResult = storage.removeEpisodeFromDataJson(contentDir, episodeKey)
                DownloadLogger.i {
                    "deleteDownloadedEpisode — removeEpisodeFromDataJson returned=$removeResult " +
                        "for episodeKey='$episodeKey' " +
                        "(true=ok OR idempotent no-match; false=no .data.json / write failed / verify failed)"
                }
            }.onFailure { e ->
                DownloadLogger.e(e) {
                    "deleteDownloadedEpisode — removeEpisodeFromDataJson THREW " +
                        "${e.javaClass.simpleName}: ${e.message}. " +
                        "Stack trace logged above. THIS IS LIKELY THE ROOT CAUSE."
                }
            }
        } else {
            DownloadLogger.w {
                "deleteDownloadedEpisode — content folder not found for mainId=$mainId; " +
                    "skipping .data.json update entirely (file + DB row deletion will still proceed)"
            }
        }

        // 3. Delete the DB row.
        DownloadLogger.i {
            "deleteDownloadedEpisode — deleting DB row for mainId=$mainId, episodeKey='$episodeKey'"
        }
        store.deleteDownloadedEpisode(mainId, episodeKey)

        // 4. Refresh the cache (always — even if prior steps failed).
        refreshDownloadedEpisodes()
        DownloadLogger.i { "deleteDownloadedEpisode — DONE (cache refreshed)" }
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
