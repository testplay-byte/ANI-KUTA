package com.confused.anikuta.core.download

import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────────────────────────────────────
// Content + Episode identity for downloads (re-keyed by mainId per D.0)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Lightweight content identity for downloads.
 *
 * D.1.2: Re-keyed by [mainId] (the stable UUID from the new project's content
 * identity system — see `core/content/ContentModels.kt`). The [contentId] is
 * the structured string that changes when sources switch (used for duplicate
 * detection, not for folder naming).
 *
 * The [title] + [coverUrl] + [coverColor] are carried for the Downloads screen
 * UI (cover + name + tinting) and for the folder name (sanitized title).
 *
 * @param mainId The stable UUID — primary key for downloads. Survives source switches.
 * @param contentId The structured content ID — changes when sources switch.
 * @param title Human-readable title for the folder name + UI.
 * @param coverUrl Cover image URL for the Downloads screen thumbnail.
 * @param coverColor Dominant cover color (ARGB int) for UI tinting; nullable.
 * @param contentFormat "video" | "images" | "text" | "audio" — drives the format folder.
 * @param contentType "anime" | "movie" | "series" | "manga" | "novel" | ...
 */
@Serializable
data class DownloadContentInfo(
    val mainId: String,
    val contentId: String,
    val title: String,
    val coverUrl: String? = null,
    val coverColor: Int? = null,
    val contentFormat: String = "video",
    val contentType: String = "anime",
    // D.FIX: FK columns from ContentRecord — needed for data.json reinstall recognition.
    val description: String? = null,
    val dataSourceId: Long? = null,
    val systemId: Long? = null,
    val extensionRepoId: Long? = null,
    val extensionId: Long? = null,
    val sourceId: Long? = null,
    val animeUrl: String? = null,
    val displaySource: String = "extension",
    val anilistId: Int? = null,
)

/**
 * Episode identity for a download.
 *
 * D.1.2: [episodeKey] is the stable key (derived from the source episode URL
 * or number — used as the composite PK alongside [mainId]).
 *
 * @param episodeKey The stable episode key (matches the DB `episode_key` column).
 * @param episodeNumber The episode number (float; .5 = special). Drives the
 *   `E00001` file name (5-digit zero-padded, per D.1 plan — supports 10,000+ episodes).
 * @param name The episode display name (for the Downloads screen + data.json).
 */
@Serializable
data class DownloadEpisodeInfo(
    val episodeKey: String,
    val episodeNumber: Float,
    val name: String,
)

// ──────────────────────────────────────────────────────────────────────────────
// Subtitle / audio tracks
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A subtitle or audio track to download alongside the video.
 *
 * @param url The remote URL of the track file.
 * @param lang Human-readable language label (e.g. "English", "Japanese").
 * @param kind Whether this is a SUBTITLE or AUDIO track.
 */
@Serializable
data class DownloadTrack(
    val url: String,
    val lang: String = "",
    val kind: TrackKind = TrackKind.SUBTITLE,
    /**
     * HTTP headers required by the subtitle URL, in **MPV `http-header-fields`
     * format**: comma-separated `"Key: Value,Key2: Value2"` (same format as
     * [DownloadRequest.videoHeaders] + what `VideoResolver.formatHeaders` produces).
     *
     * D-FIX-SUB: subtitles often need a Referer / User-Agent to avoid 403 (same as the
     * video URL). Previously the downloader sent NO headers → subtitle fetches 403'd on
     * protected CDNs and were silently skipped. The streaming-side [SubtitleEngine]
     * already handled headers; the download side now matches it.
     *
     * `DownloadOrchestrator.buildRequest` populates this with the video's
     * `videoHeaders` as a fallback (subtitles from the same source usually need the
     * same headers). `null` or blank = no headers.
     */
    val headers: String? = null,
)

@Serializable
enum class TrackKind { SUBTITLE, AUDIO }

// ──────────────────────────────────────────────────────────────────────────────
// Download request (the input to DownloadManager.enqueueDownload)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The input to [DownloadManager.enqueueDownload] — an ALREADY-RESOLVED video.
 *
 * D.1.2 + REVIEW-5 M15/M64: includes [resolveContext] for the proxy-churn
 * re-resolve fix. When the HttpDownloader catches an IOException on a localhost
 * URL, it uses [resolveContext] to re-resolve the video via [ReResolver].
 *
 * @param content The content identity (mainId drives the folder structure).
 * @param episode The episode identity (episodeKey is the offline-playback key).
 * @param videoUrl The direct video file URL to download (from ResolverVideo.url
 *   — or ResolverVideo.directUrl if available, to avoid the proxy dependency).
 * @param videoHeaders HTTP headers required by the video URL (JSON: Map<String, String>).
 * @param subtitleTracks ALL subtitle tracks to download alongside.
 * @param audioTracks Optional audio tracks to download (stored alongside).
 * @param sourceId The extension source ID (for logging + re-resolve).
 * @param videoServer The server name (for UI display).
 * @param videoQuality The quality label (e.g. "1080p") — for UI display.
 * @param videoAudio The audio version label (e.g. "SUB") — for UI display.
 * @param resolveContext The re-resolve context (7 fields) for proxy-churn recovery.
 *   Null if re-resolve is not applicable (e.g. direct CDN URL, no proxy).
 */
@Serializable
data class DownloadRequest(
    val content: DownloadContentInfo,
    val episode: DownloadEpisodeInfo,
    val videoUrl: String,
    val videoHeaders: String? = null,
    val subtitleTracks: List<DownloadTrack> = emptyList(),
    val audioTracks: List<DownloadTrack> = emptyList(),
    val sourceId: Long? = null,
    val videoServer: String = "",
    val videoQuality: String = "",
    val videoAudio: String = "",
    val resolveContext: String? = null,  // JSON: ResolveContext (D.2)
)

// ──────────────────────────────────────────────────────────────────────────────
// Download task (the runtime state)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A download task — the runtime state of a single episode download.
 *
 * D.1.2: Re-keyed by [mainId] + [episodeKey] (matching the DB schema).
 *
 * The [status] enum can't carry per-instance data, so the retry metadata
 * ([retryAttempt], [retryMaxAttempts], [lastError]) lives on this data class
 * (REVIEW-5 M12).
 *
 * @param id The DB auto-increment ID.
 * @param content The content identity.
 * @param episode The episode identity.
 * @param videoUrl The video URL being downloaded.
 * @param videoHeaders HTTP headers (JSON).
 * @param videoUri The content:// URI after publish (null while downloading).
 * @param subtitleTracks Subtitle tracks to download.
 * @param audioTracks Audio tracks to download.
 * @param subtitleUris Downloaded subtitle URIs (JSON).
 * @param sourceId The extension source ID.
 * @param videoServer The server name.
 * @param videoQuality The quality label.
 * @param videoAudio The audio version label.
 * @param status The current lifecycle state.
 * @param progress 0..100.
 * @param downloadedBytes Bytes downloaded so far.
 * @param totalBytes Total bytes (-1 = unknown).
 * @param resolveContext The re-resolve context (JSON).
 * @param retryAttempt Current retry attempt count.
 * @param retryMaxAttempts Max retry attempts.
 * @param lastError Last error message (if status == ERROR).
 * @param queuedAt Epoch millis when queued.
 * @param startedAt Epoch millis when download started.
 * @param completedAt Epoch millis when download completed.
 */
data class DownloadTask(
    val id: Long,
    val content: DownloadContentInfo,
    val episode: DownloadEpisodeInfo,
    val videoUrl: String,
    val videoHeaders: String? = null,
    val videoUri: String? = null,
    val subtitleTracks: List<DownloadTrack> = emptyList(),
    val audioTracks: List<DownloadTrack> = emptyList(),
    val subtitleUris: String? = null,
    val sourceId: Long? = null,
    val videoServer: String = "",
    val videoQuality: String = "",
    val videoAudio: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val resolveContext: String? = null,
    val retryAttempt: Int = 0,
    val retryMaxAttempts: Int = 3,
    val lastError: String? = null,
    val queuedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

// ──────────────────────────────────────────────────────────────────────────────
// Downloaded episode (the on-disk result)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * A completed, on-disk downloaded episode — returned by
 * [DownloadManager.getDownloadedEpisodes] for the Downloads screen.
 *
 * @param content The content identity.
 * @param episode The episode identity.
 * @param videoUri The content:// URI of the downloaded video (playable by MPV).
 * @param subtitleUris The content:// URIs of downloaded subtitle files.
 * @param sizeBytes Total size of the episode file on disk.
 * @param quality The quality label (e.g. "1080p").
 * @param completedAt Epoch millis when the download finished.
 */
data class DownloadedEpisode(
    val content: DownloadContentInfo,
    val episode: DownloadEpisodeInfo,
    val videoUri: String,
    val subtitleUris: List<String>,
    val sizeBytes: Long,
    val quality: String?,
    val completedAt: Long,
)

/**
 * The result of publishing a download to the SAF folder.
 *
 * D-FIX-SUB: `publishVideoFile` previously returned ONLY the video `content://` URI.
 * The subtitle files it wrote to disk were not returned → `HttpDownloader` couldn't
 * set `task.subtitleUris` → the DB stored `null` → offline playback had no subtitles
 * (the files existed on disk but nobody knew their URIs). This class fixes that by
 * returning both.
 *
 * @param videoUri The `content://` URI of the published video file.
 * @param subtitleUris The `content://` URIs of any published subtitle files (in
 *   track order). Empty if the episode had no subtitles.
 */
data class PublishResult(
    val videoUri: String,
    val subtitleUris: List<String> = emptyList(),
)
