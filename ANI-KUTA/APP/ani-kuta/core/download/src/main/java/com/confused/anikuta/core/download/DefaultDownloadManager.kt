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
        scope.launch {
            _downloadedEpisodes.collect { downloaded ->
                val current = _episodeDownloadStates.value.toMutableMap()
                for (ep in downloaded) {
                    val key = "${ep.content.mainId}|${ep.episode.episodeKey}"
                    if (key !in current || current[key]?.first != DownloadStatus.DOWNLOADING) {
                        current[key] = EpisodeDownloadState(DownloadStatus.COMPLETED, 100)
                    }
                }
                _episodeDownloadStates.value = current
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
        DownloadLogger.i { "deleteDownloadedEpisode — mainId=$mainId, episodeKey=$episodeKey" }
        // 1. Delete the video file + subtitle files on disk (best-effort).
        runCatching {
            val contentDir = storage.findContentFolder(mainId)
            if (contentDir != null) {
                val numStr = episodeKey.substringAfter('|', "")
                // Look in the "episodes" subfolder (new) + the root (legacy) for the video.
                val episodesDir = contentDir.listFiles().firstOrNull { it.name == "episodes" && it.isDirectory }
                val videoSearchDirs = if (episodesDir != null) listOf(episodesDir, contentDir) else listOf(contentDir)
                for (dir in videoSearchDirs) {
                    for (file in dir.listFiles()) {
                        if (!file.isFile) continue
                        if (numStr.isNotBlank() && file.name?.contains("E$numStr", ignoreCase = true) == true) {
                            file.delete()
                            DownloadLogger.i { "Deleted video: ${file.name}" }
                        }
                    }
                }
                // Look in the "subtitles" subfolder (new) + the root (legacy) for subtitle files.
                val subtitlesDir = contentDir.listFiles().firstOrNull { it.name == "subtitles" && it.isDirectory }
                val subtitleSearchDirs = if (subtitlesDir != null) listOf(subtitlesDir, contentDir) else listOf(contentDir)
                for (dir in subtitleSearchDirs) {
                    for (file in dir.listFiles()) {
                        if (!file.isFile) continue
                        if (numStr.isNotBlank() && file.name?.contains("E$numStr", ignoreCase = true) == true) {
                            file.delete()
                            DownloadLogger.i { "Deleted subtitle: ${file.name}" }
                        }
                    }
                }
            }
        }.onFailure { e ->
            DownloadLogger.w { "deleteDownloadedEpisode — file delete failed (non-fatal): ${e.message}" }
        }
        // 2. Delete the DB row.
        store.deleteDownloadedEpisode(mainId, episodeKey)
        // 3. D-241: remove this episode from the on-disk `.data.json` episodes list.
        //    Best-effort — a failure here doesn't fail the delete (the DB row is
        //    already gone, so the episode is functionally deleted; the scanner will
        //    reconcile the episodes list from the file walk on the next startup).
        runCatching {
            val contentDir = storage.findContentFolder(mainId)
            if (contentDir != null) {
                storage.removeEpisodeFromDataJson(contentDir, episodeKey)
            }
        }.onFailure { e ->
            DownloadLogger.w {
                "deleteDownloadedEpisode — removeEpisodeFromDataJson failed (non-fatal): ${e.message}"
            }
        }
        // 4. Refresh the cache.
        refreshDownloadedEpisodes()
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
