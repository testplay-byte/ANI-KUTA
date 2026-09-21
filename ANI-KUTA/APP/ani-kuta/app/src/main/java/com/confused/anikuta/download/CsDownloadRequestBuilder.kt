package com.confused.anikuta.download

import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.download.DownloadContentInfo
import com.confused.anikuta.core.download.DownloadEpisodeInfo
import com.confused.anikuta.core.download.DownloadRequest
import com.confused.anikuta.core.download.DownloadTrack
import com.confused.anikuta.core.download.TrackKind

/**
 * Task 58 (round 18 — the CloudStream downloads port): builds a [DownloadRequest]
 * from an ALREADY-RESOLVED [CsVideoLink].
 *
 * The download engine is SOURCE-AGNOSTIC: everything downstream of a
 * [DownloadRequest] (queue, foreground service, HTTP/HLS fetchers, SAF
 * storage, notifications, the downloaded_episode DB, offline playback lookup)
 * keys on (mainId, episodeKey) and never touches extension code. This builder
 * is the ONLY CS-specific translation layer:
 *
 *  - [CsVideoLink.url] → [DownloadRequest.videoUrl] (TORRENT/MAGNET links
 *    never reach here — the resolver hides + counts them; DASH links ARE
 *    downloadable since D-539 — the DashDownloader caches manifest +
 *    segments into the offline SimpleCache).
 *  - [CsVideoLink.allHeaders] (referer + UA + provider headers, already
 *    merged) → the MPV `http-header-fields` STRING format the engine's
 *    DownloadHeaderParser consumes (comma-separated "Key: Value" pairs —
 *    the parser is comma-safe for UA values).
 *  - the episode's provider [CsSubtitle]s → [DownloadTrack]s (best-effort
 *    sidecars, the engine's D-FIX-SUB fallback: same-source headers).
 *  - display labels (server name / quality / audio flavor) for the queue UI.
 *  - `resolveContext = null`: the aniyomi proxy-churn re-resolve machinery
 *    (ReResolver over ResolverVideo) doesn't apply to CS links — a failed CS
 *    download retries through the queue's own RetryPolicy, and the user can
 *    re-pick from the sheet (the links are re-resolved on every sheet open).
 *
 * Pure object (no Android/Compose deps) — mirrors the app/download package's
 * plain-class convention (DownloadOrchestrator, ReResolver, ResolveContext).
 */
object CsDownloadRequestBuilder {

    /**
     * @param content The content identity (mainId drives the SAF folder + DB keys).
     * @param episode The episode identity (episodeKey = the CS data handle —
     *   the SAME key the details page's download-state chips + offline
     *   playback lookup use).
     * @param link The resolved CloudStream stream the user picked.
     * @param subtitles The episode's provider subtitle tracks (best-effort).
     * @param sourceId The CS bridge's synthetic source id (logging + UI).
     * @param allLinks The sheet's FULL resolved link list (D-550) — the pool
     *   the sibling DASH audio variants are collected from so a downloaded
     *   episode carries every audio version and the offline player can switch
     *   between them. Null/empty = no siblings (single-variant audio).
     */
    fun build(
        content: DownloadContentInfo,
        episode: DownloadEpisodeInfo,
        link: CsVideoLink,
        subtitles: List<CsSubtitle>,
        sourceId: Long?,
        allLinks: List<CsVideoLink> = emptyList(),
    ): DownloadRequest {
        val headers = toMpvHeaderString(link.allHeaders)
        return DownloadRequest(
            content = content,
            episode = episode,
            videoUrl = link.url,
            videoHeaders = headers,
            subtitleTracks = subtitles.map { sub ->
                DownloadTrack(
                    url = sub.url,
                    lang = sub.displayName,
                    kind = TrackKind.SUBTITLE,
                    // D-FIX-SUB parity: sidecars from the same source usually
                    // need the video's referer/UA too (sub headers are merged
                    // into the video's when they're empty).
                    headers = if (sub.headers.isEmpty()) headers else toMpvHeaderString(sub.headers),
                )
            },
            // D-550: the sibling DASH audio variants ride the (otherwise
            // unused-by-downloaders) audio_tracks list as AUDIO_VARIANT
            // tracks — label + manifest URL + the variant's own headers. The
            // DashDownloader plans + downloads their audio sets and labels
            // them in the sidecar manifest; every other downloader ignores
            // the kind, so progressive/HLS downloads are unaffected.
            audioTracks = siblingAudioVariants(link, allLinks).map { sibling ->
                DownloadTrack(
                    url = sibling.url,
                    lang = sibling.audioLabel,
                    kind = TrackKind.AUDIO_VARIANT,
                    headers = toMpvHeaderString(sibling.allHeaders),
                )
            },
            sourceId = sourceId,
            videoServer = link.name,
            videoQuality = link.qualityLabel,
            videoAudio = link.audioLabel,
            resolveContext = null,
        )
    }

    /**
     * D-550: the sibling DASH audio variants of [link] — same server name,
     * DIFFERENT audio-version label, DASH manifest URL (`.mpd`), deduped by
     * (label, url). These are the "MovieBox (English sub)" to the picked
     * "MovieBox (Original Audio)": separate manifests whose audio sets the
     * DashDownloader saves alongside the picked variant's, so the offline
     * player's audio selector can switch between them (the streaming player
     * already could — it re-resolves and re-picks; offline needs the bytes).
     */
    private fun siblingAudioVariants(link: CsVideoLink, allLinks: List<CsVideoLink>): List<CsVideoLink> =
        allLinks
            .filter { candidate ->
                candidate.type == CsLinkType.DASH &&
                    candidate.url != link.url &&
                    candidate.name == link.name &&
                    candidate.audioLabel.isNotBlank() &&
                    candidate.audioLabel != link.audioLabel
            }
            .distinctBy { it.audioLabel to it.url }

    /**
     * Map → the MPV `http-header-fields` format (`"Key: Value,Key2: Value2"`).
     * Null when the map is empty (the engine treats null/blank as "no headers").
     * Values may contain commas — DownloadHeaderParser only splits on commas
     * followed by a header-NAME pattern, so UAs survive intact.
     */
    fun toMpvHeaderString(headers: Map<String, String>): String? =
        headers.takeIf { it.isNotEmpty() }
            ?.entries
            ?.joinToString(",") { (key, value) -> "$key: $value" }
}
