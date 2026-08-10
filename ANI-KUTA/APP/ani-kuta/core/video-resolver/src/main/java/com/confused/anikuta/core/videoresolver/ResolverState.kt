package com.confused.anikuta.core.videoresolver

/**
 * State of video resolution.
 */
sealed interface ResolverState {

    /** Initial state — resolution has not started. */
    data object Idle : ResolverState

    /** Resolution is in progress. */
    data class Loading(val message: String = "Resolving video...") : ResolverState

    /** Resolution succeeded — videos are available.
     *
     * @param videos The flat list of resolved videos (for the ResolverSheet).
     * @param rawEntries The original video entries (Video + hosterName) for
     *   building structured servers via [VideoResolver.buildServers] — avoids
     *   a second getHosterList call.
     */
    data class Success(
        val videos: List<ResolvedVideo>,
        val rawEntries: List<VideoEntry> = emptyList(),
        val servers: List<ResolverServer> = emptyList(),
    ) : ResolverState

    /** Resolution failed. */
    data class Error(val message: String) : ResolverState
}

/**
 * A resolved video with quality information.
 *
 * @param subtitleTracks External subtitle tracks to load via MPV's `sub-add`.
 *   Populated from `Video.subtitleTracks` — the extension provides these as
 *   `Track(url, lang)` pairs. For AniKotoS, the URL is a localhost proxy URL
 *   like `http://127.0.0.1:PORT/sub/0/0`.
 * @param audioTracks External audio tracks to load via MPV's `audio-add`.
 */
data class ResolvedVideo(
    val url: String,
    val quality: String = "Default",
    val directUrl: String? = null,
    /** HTTP headers required by the video URL (from the extension's Video.headers).
     *  Format: "Key: Value,Key2: Value2" (comma-separated for MPV's http-header-fields). */
    val headers: String = "",
    val subtitleTracks: List<ResolverSubtitleTrack> = emptyList(),
    val audioTracks: List<ResolverSubtitleTrack> = emptyList(),
)
