package com.confused.anikuta.download

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo
import com.confused.anikuta.core.videoresolver.VideoResolver
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.first

/**
 * The re-resolve helper for the proxy-churn fix.
 *
 * D.2 + REVIEW-5 M15/M17: When [HttpDownloader.downloadNormal] catches an
 * `IOException` on a localhost URL, it calls [reResolve] to get a fresh video URL.
 *
 * REVIEW-5 M17: does a DIRECT lookup by pinned (server, audio, quality) — does
 * NOT re-run the [AutoDownloadEngine]. The [autoDownloadEngine] constructor param
 * was REMOVED (was dead DI — R2-I3).
 *
 * Caps attempts at 1 (one initial + one re-resolve). The caller (HttpDownloader)
 * is responsible for the `reResolveAttempts` counter (M15).
 */
class ReResolver(
    private val videoResolver: VideoResolver,
) {

    companion object {
        private const val TAG = "Anikuta:Download:ReResolver"
    }

    /**
     * Re-resolves the video for the given [context].
     *
     * Calls [VideoResolver.resolve] to get a fresh server list, then does a
     * DIRECT lookup by the pinned (server, audio, quality) from [context].
     *
     * @param context The resolve context (7 fields — M64).
     * @param source The extension source (for calling getHosterList).
     * @param episode The episode (for calling getHosterList).
     * @return The fresh [ResolverVideo] with the same (server, audio, quality),
     *   or null if the re-resolve failed (source error, or the pinned combination
     *   is no longer available).
     */
    suspend fun reResolve(
        context: ResolveContext,
        source: AnimeHttpSource,
        episode: SEpisode,
    ): ResolverVideo? {
        Logger.i(TAG) {
            "reResolve — re-resolving for mainId=${context.mainId}, " +
                "server=${context.serverName}, audio=${context.audioLabel}, " +
                "quality=${context.quality}"
        }

        // Collect the resolve result — use first() to get the terminal state.
        val servers: List<ResolverServer> = try {
            val state = videoResolver.resolve(source, episode).first { s ->
                s is com.confused.anikuta.core.videoresolver.ResolverState.Success ||
                    s is com.confused.anikuta.core.videoresolver.ResolverState.Error
            }
            when (state) {
                is com.confused.anikuta.core.videoresolver.ResolverState.Success ->
                    videoResolver.buildServers(state.rawEntries, source.name)
                is com.confused.anikuta.core.videoresolver.ResolverState.Error -> {
                    Logger.e(TAG) { "reResolve — resolve failed: ${state.message}" }
                    emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "reResolve — exception during resolve" }
            return null
        }

        if (servers.isEmpty()) return null

        // Direct lookup by pinned (server, audio, quality).
        for (server in servers) {
            if (!server.name.equals(context.serverName, ignoreCase = true)) continue
            for (audio in server.audioVersions) {
                if (!audio.label.equals(context.audioLabel, ignoreCase = true)) continue
                for (video in audio.videos) {
                    if (video.quality.equals(context.quality, ignoreCase = true)) {
                        Logger.i(TAG) { "reResolve — found matching video: ${video.url}" }
                        return video
                    }
                }
            }
        }

        // Exact match not found — try quality-agnostic (same server + audio).
        for (server in servers) {
            if (!server.name.equals(context.serverName, ignoreCase = true)) continue
            for (audio in server.audioVersions) {
                if (!audio.label.equals(context.audioLabel, ignoreCase = true)) continue
                // Return the first video in the matching server+audio.
                Logger.w(TAG) { "reResolve — quality match not found, using first video in server+audio" }
                return audio.videos.firstOrNull()
            }
        }

        Logger.w(TAG) { "reResolve — no matching video found for pinned server+audio" }
        return null
    }
}
