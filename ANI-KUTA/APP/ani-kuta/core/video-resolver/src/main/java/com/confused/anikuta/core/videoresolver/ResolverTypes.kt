package com.confused.anikuta.core.videoresolver

/**
 * Structured resolver types — 3-tier hierarchy: Server → AudioVersion → Video.
 *
 * Ported from the old project's `VideoResolverState.kt`. These are pure Kotlin
 * data classes (no Compose deps) so they live in `:core:video-resolver`.
 *
 * The [QualitySheet] UI consumes these to render the accordion server list +
 * quality chips.
 *
 * ## Why a 3-tier hierarchy?
 *
 * Extensions return a flat `List<Video>`. The old project groups these into:
 * - **Server** — e.g. "Vidstream", "Doodstream", "Mp4Upload". Derived from the
 *   video URL's host or the `Video.server` field if the extension provides it.
 * - **AudioVersion** — e.g. "SUB", "DUB", "HSUB". Derived from the video's
 *   quality label or a separate audio-track marker.
 * - **Video** — the actual playable URL + quality (e.g. "1080p").
 *
 * This grouping lets the user pick a server first (which determines the
 * streaming provider + its headers/requirements), then an audio version,
 * then a specific quality.
 */

/**
 * A server entry — top level of the 3-tier hierarchy.
 */
data class ResolverServer(
    val name: String,
    val audioVersions: List<ResolverAudioVersion>,
)

/**
 * An audio version (SUB/DUB/HSUB/etc.) within a server.
 */
data class ResolverAudioVersion(
    val label: String,
    val videos: List<ResolverVideo>,
)

/**
 * A single video quality option within an audio version.
 *
 * @param quality Display label, e.g. "1080p", "720p", "Default".
 * @param url The playable URL (may be a proxied URL from the extension).
 * @param directUrl D.2: The direct CDN URL (bypasses the extension proxy).
 *    Null if the extension doesn't expose a direct URL. When non-null, the
 *    download orchestrator prefers this over [url] to avoid the proxy-churn
 *    bug (extension local-proxy-server port rotation kills in-flight downloads).
 *    The player still uses [url] (proxy URL) for streaming — directUrl is for
 *    downloads only.
 * @param videoTitle A stable identifier used to match the currently-playing
 *    video across re-resolutions. Proxied URLs change between resolutions,
 *    so we match by title instead.
 * @param videoHeaders HTTP headers (MPV http-header-fields format:
 *    "Key: Value,Key2: Value2") required by the URL. May be null if the
 *    extension provides no special headers.
 * @param subtitleTracks External subtitle tracks to load via MPV's `sub-add`.
 * @param audioTracks External audio tracks to load via MPV's `audio-add`.
 */
data class ResolverVideo(
    val quality: String,
    val url: String,
    val directUrl: String? = null,
    val videoTitle: String = "",
    val videoHeaders: String? = null,
    val subtitleTracks: List<ResolverSubtitleTrack> = emptyList(),
    val audioTracks: List<ResolverSubtitleTrack> = emptyList(),
)

/**
 * A subtitle or audio track from the Video object.
 * Used for external track loading via MPV's sub-add/audio-add commands.
 */
data class ResolverSubtitleTrack(
    val url: String,
    val lang: String = "",
)
