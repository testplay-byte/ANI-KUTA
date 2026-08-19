package com.confused.anikuta.download

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.download.AutoDownloadEngine
import com.confused.anikuta.core.download.DownloadContentInfo
import com.confused.anikuta.core.download.DownloadEpisodeInfo
import com.confused.anikuta.core.download.DownloadManager
import com.confused.anikuta.core.download.DownloadPreferences
import com.confused.anikuta.core.download.DownloadRequest
import com.confused.anikuta.core.download.DownloadTrack
import com.confused.anikuta.core.download.TrackKind
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverState
import com.confused.anikuta.core.videoresolver.ResolverVideo
import com.confused.anikuta.core.videoresolver.VideoResolver
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.first

/**
 * Bridges the video resolver + the download engine.
 *
 * D.2: The orchestrator is the single entry point for "download this episode".
 * It:
 *  1. Resolves the video via [VideoResolver].
 *  2. Uses [AutoDownloadEngine] to select the best video based on the user's
 *     `dimensionPriority` + fallback preferences.
 *  3. Builds a [DownloadRequest] (preferring `directUrl` over `url` — proxy-churn
 *     fix Layer 1) and enqueues it via [DownloadManager].
 *  4. Returns an [EnqueueResult] — Success, ShowPicker (ASK fallback), NoSources,
 *     or Error.
 *
 * For the manual download path (user picks a specific video via the picker sheet),
 * use [enqueueSpecific].
 */
class DownloadOrchestrator(
    private val videoResolver: VideoResolver,
    private val downloadManager: DownloadManager,
    private val preferences: DownloadPreferences,
) {

    companion object {
        private const val TAG = "Anikuta:Download:Orchestrator"
    }

    /**
     * Auto-downloads an episode — resolves videos + uses [AutoDownloadEngine] to
     * pick the best one.
     *
     * @param source The extension source (null if AniList-only → NoSources).
     * @param episode The episode to download.
     * @param content The content identity (mainId, title, cover, etc.).
     * @param episodeInfo The episode identity (episodeKey, episodeNumber, name).
     * @return The [EnqueueResult].
     */
    suspend fun enqueueDownload(
        source: AnimeHttpSource?,
        episode: SEpisode,
        content: DownloadContentInfo,
        episodeInfo: DownloadEpisodeInfo,
    ): EnqueueResult {
        if (source == null) {
            Logger.w(TAG) { "enqueueDownload — no extension source for mainId=${content.mainId}" }
            return EnqueueResult.NoSources
        }

        // 1. Resolve videos.
        val servers: List<ResolverServer> = try {
            resolveServers(source, episode)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "enqueueDownload — resolve failed" }
            return EnqueueResult.Error(e.message ?: "Resolve failed")
        }

        if (servers.isEmpty()) {
            Logger.w(TAG) { "enqueueDownload — no servers resolved" }
            return EnqueueResult.Error("No video sources available")
        }

        // 2. Read preferences.
        val dimensionPriority = preferences.dimensionPriority.get().mapNotNull { dimStr ->
            runCatching { AutoDownloadEngine.PreferenceDimension.valueOf(dimStr) }.getOrNull()
        }
        val preferredAudio = preferences.preferredAudio.get()
        val preferredQualities = preferences.preferredQualities.get()
        val preferredServers = preferences.preferredServers.get()
        val audioFallback = preferences.audioFallback.get().toFallbackStrategy()
        val qualityFallback = preferences.qualityFallback.get().toFallbackStrategy()
        val serverFallback = preferences.serverFallback.get().toFallbackStrategy()
        val globalFallback = preferences.globalFallback.get().toGlobalFallback()

        // 3. Run the auto-download engine.
        val selection = AutoDownloadEngine.selectBestVideo(
            servers = servers,
            dimensionPriority = dimensionPriority,
            preferredAudio = preferredAudio,
            preferredQualities = preferredQualities,
            preferredServers = preferredServers,
            audioFallback = audioFallback,
            qualityFallback = qualityFallback,
            serverFallback = serverFallback,
            globalFallback = globalFallback,
        )

        return when (selection) {
            is AutoDownloadEngine.Selection.Selected -> {
                val video = selection.candidate.video
                val request = buildRequest(
                    content = content,
                    episodeInfo = episodeInfo,
                    video = video,
                    serverName = selection.candidate.server,
                    audioLabel = selection.candidate.audio,
                    sourceId = source.id,
                    episodeUrl = episode.url,
                )
                val taskId = downloadManager.enqueueDownload(request)
                Logger.i(TAG) { "enqueueDownload — success, taskId=$taskId" }
                EnqueueResult.Success(taskId)
            }
            AutoDownloadEngine.Selection.NoCandidates -> {
                Logger.w(TAG) { "enqueueDownload — no candidates (ASK)" }
                EnqueueResult.ShowPicker(servers)
            }
            AutoDownloadEngine.Selection.DoNotDownload -> {
                Logger.i(TAG) { "enqueueDownload — DoNotDownload (global fallback)" }
                EnqueueResult.Error("Download skipped (no preferred source available)")
            }
        }
    }

    /**
     * Enqueues a SPECIFIC video (manual selection via the picker sheet).
     *
     * @param source The extension source.
     * @param episode The episode.
     * @param content The content identity.
     * @param episodeInfo The episode identity.
     * @param video The user-selected video.
     * @param serverName The server name (from the picker).
     * @param audioLabel The audio version label (from the picker).
     */
    suspend fun enqueueSpecific(
        source: AnimeHttpSource,
        episode: SEpisode,
        content: DownloadContentInfo,
        episodeInfo: DownloadEpisodeInfo,
        video: ResolverVideo,
        serverName: String,
        audioLabel: String,
    ): EnqueueResult {
        val request = buildRequest(
            content = content,
            episodeInfo = episodeInfo,
            video = video,
            serverName = serverName,
            audioLabel = audioLabel,
            sourceId = source.id,
            episodeUrl = episode.url,
        )
        val taskId = downloadManager.enqueueDownload(request)
        Logger.i(TAG) { "enqueueSpecific — success, taskId=$taskId" }
        return EnqueueResult.Success(taskId)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun resolveServers(
        source: AnimeHttpSource,
        episode: SEpisode,
    ): List<ResolverServer> {
        // Collect the first Success/Error state from the resolver flow.
        val state = videoResolver.resolve(source, episode).first { s ->
            s is ResolverState.Success || s is ResolverState.Error
        }
        return when (state) {
            is ResolverState.Success -> videoResolver.buildServers(state.rawEntries, source.name)
            is ResolverState.Error -> throw RuntimeException(state.message)
            else -> emptyList()
        }
    }

    /**
     * Builds a [DownloadRequest] from the selected video.
     *
     * REVIEW-5 M15 (proxy-churn fix Layer 1): prefers `video.directUrl` over
     * `video.url` — the direct CDN URL bypasses the extension proxy entirely.
     */
    private fun buildRequest(
        content: DownloadContentInfo,
        episodeInfo: DownloadEpisodeInfo,
        video: ResolverVideo,
        serverName: String,
        audioLabel: String,
        sourceId: Long,
        episodeUrl: String,
    ): DownloadRequest {
        // Proxy-churn fix Layer 1: prefer directUrl (CDN) over url (proxy).
        val downloadUrl = video.directUrl ?: video.url

        // D-FIX-SUB: subtitles from the same source usually need the SAME headers as
        // the video (Referer / User-Agent) to avoid 403. The resolver-side
        // ResolverSubtitleTrack doesn't carry per-track headers yet, so we fall back
        // to the video's headers for every subtitle/audio track. This matches the
        // streaming-side SubtitleEngine behavior (which also uses the video's headers
        // when per-track headers aren't available). Per-track headers can be added
        // later if a source needs different headers per subtitle.
        val fallbackHeaders = video.videoHeaders

        val subtitleTracks = video.subtitleTracks.map { track ->
            DownloadTrack(
                url = track.url,
                lang = track.lang,
                kind = TrackKind.SUBTITLE,
                headers = fallbackHeaders,
            )
        }
        val audioTracks = video.audioTracks.map { track ->
            DownloadTrack(
                url = track.url,
                lang = track.lang,
                kind = TrackKind.AUDIO,
                headers = fallbackHeaders,
            )
        }
        val resolveContext = if (downloadUrl.startsWith("http://localhost") ||
            downloadUrl.startsWith("http://127.0.0.1")
        ) {
            ResolveContext(
                sourceId = sourceId,
                episodeUrl = episodeUrl,
                serverName = serverName,
                audioLabel = audioLabel,
                quality = video.quality,
                mainId = content.mainId,
                episodeKey = episodeInfo.episodeKey,
            ).let { kotlinx.serialization.json.Json.encodeToString(ResolveContext.serializer(), it) }
        } else {
            null
        }

        // (subtitleTracks + audioTracks built above with fallbackHeaders — D-FIX-SUB.)

        return DownloadRequest(
            content = content,
            episode = episodeInfo,
            videoUrl = downloadUrl,
            videoHeaders = video.videoHeaders,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            sourceId = sourceId,
            videoServer = serverName,
            videoQuality = video.quality,
            videoAudio = audioLabel,
            resolveContext = resolveContext,
        )
    }

    private fun String.toFallbackStrategy(): AutoDownloadEngine.FallbackStrategy =
        if (this == "DONT") AutoDownloadEngine.FallbackStrategy.DONT
        else AutoDownloadEngine.FallbackStrategy.TRY_NEXT

    private fun String.toGlobalFallback(): AutoDownloadEngine.GlobalFallback =
        when (this) {
            "ASK" -> AutoDownloadEngine.GlobalFallback.ASK
            "DO_NOT_DOWNLOAD" -> AutoDownloadEngine.GlobalFallback.DO_NOT_DOWNLOAD
            else -> AutoDownloadEngine.GlobalFallback.BEST_EFFORT
        }
}
