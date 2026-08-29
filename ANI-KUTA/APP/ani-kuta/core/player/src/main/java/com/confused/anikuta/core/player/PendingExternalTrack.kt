package com.confused.anikuta.core.player

/**
 * One external track (subtitle or audio) awaiting load on the next FILE_LOADED.
 *
 * Task 48 (device round 7 — per-track subtitle headers): replaces the old
 * `Pair<String, String>` (url, lang) shape on [PlayerObserver.pendingSubtitleTracks]
 * / [PlayerObserver.pendingAudioTracks] so each track can carry the HTTP headers
 * its URL REQUIRES — some CloudStream providers' subtitle endpoints 403 without
 * the right Referer/User-Agent, and those headers can DIFFER per track (e.g.
 * subs on a different host than the video).
 *
 * [headers] uses the MPV csv format ("Key: Value,Key2: Value2") used everywhere
 * else in the pipeline ([PlayerObserver.trackHeaders], videoHeaders,
 * [com.confused.anikuta.core.player.subtitles.SubtitleDownloadRequest.headers]).
 * Null/blank → the parent video's headers apply (the previous behavior).
 */
data class PendingExternalTrack(
    val url: String,
    val lang: String,
    val headers: String? = null,
)
