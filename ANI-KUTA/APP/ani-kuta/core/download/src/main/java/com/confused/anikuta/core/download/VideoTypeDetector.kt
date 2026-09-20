package com.confused.anikuta.core.download

/**
 * Detects the video type (direct HTTP vs HLS vs DASH) from a URL + Content-Type.
 *
 * D.1.11: Same logic as the old project — inspects the URL extension + the
 * Content-Type header to determine which downloader to use.
 *
 * - HLS (.m3u8 or Content-Type: application/vnd.apple.mpegurl) → [HLS]
 * - DASH (.mpd or Content-Type: dash+xml) → [DASH] (D-539: downloadable —
 *   the DashDownloader caches manifest + segments into the offline cache)
 * - Everything else → [HTTP] (direct download: .mp4, .mkv, .webm, etc.)
 */
object VideoTypeDetector {

    enum class VideoType {
        /** Direct video file — use [HttpDownloader]. */
        HTTP,

        /** HLS playlist (.m3u8) — use [HlsDownloader]. */
        HLS,

        /** DASH manifest (.mpd) — D-539: use [DashDownloader] (offline cache). */
        DASH,
    }

    /**
     * Detects the video type from the URL + optional Content-Type header.
     *
     * @param url The video URL.
     * @param contentType The Content-Type response header (nullable).
     * @return The detected [VideoType] (defaults to [VideoType.HTTP]).
     */
    fun detect(url: String, contentType: String? = null): VideoType {
        // Check Content-Type first (more reliable than URL extension).
        if (contentType != null) {
            val lower = contentType.lowercase()
            if (lower.contains("mpegurl") || lower.contains("m3u8")) {
                return VideoType.HLS
            }
            if (lower.contains("dash+xml")) {
                return VideoType.DASH
            }
            if (lower.contains("mp4") || lower.contains("matroska") ||
                lower.contains("webm") || lower.contains("octet-stream")
            ) {
                return VideoType.HTTP
            }
        }

        // Fall back to URL extension.
        val lowerUrl = url.substringBefore('?').lowercase()
        return when {
            lowerUrl.contains(".m3u8") -> VideoType.HLS
            lowerUrl.contains(".mpd") -> VideoType.DASH
            else -> VideoType.HTTP
        }
    }
}
