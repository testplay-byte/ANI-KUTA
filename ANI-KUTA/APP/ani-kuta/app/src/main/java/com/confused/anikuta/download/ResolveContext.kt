package com.confused.anikuta.download

import kotlinx.serialization.Serializable

/**
 * Captures the resolution context for re-resolve-on-IOException (proxy-churn fix).
 *
 * D.2 + REVIEW-5 M64: 7 fields (the OLD draft listed only 5 — mainId + episodeKey
 * are NEW, used for DB lookups during re-resolve).
 *
 * When [HttpDownloader.downloadNormal] catches an `IOException` on a localhost URL,
 * it uses this context to re-resolve the video via [ReResolver.reResolve]. The
 * re-resolve does a DIRECT lookup by pinned (server, audio, quality) — it does
 * NOT re-run the [AutoDownloadEngine] (REVIEW-5 M17).
 *
 * @param sourceId The extension source ID (for re-calling getHosterList).
 * @param episodeUrl The episode URL on the source.
 * @param serverName The pinned server name (the one that was originally selected).
 * @param audioLabel The pinned audio version label.
 * @param quality The pinned quality label.
 * @param mainId The content mainId (for DB lookups — M64).
 * @param episodeKey The episode key (for DB lookups — M64).
 */
@Serializable
data class ResolveContext(
    val sourceId: Long,
    val episodeUrl: String,
    val serverName: String,
    val audioLabel: String,
    val quality: String,
    val mainId: String,
    val episodeKey: String,
)
