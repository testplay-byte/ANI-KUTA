package com.confused.anikuta.core.csplayer

import okhttp3.Interceptor

/**
 * App-side playback models for the CloudStream pipeline (task 52 / round 12).
 *
 * ISOLATION CONTRACT (doc cloudstream-v2/02-PLAYBACK-PLAN.md §2 invariant 2):
 * these mirror the plugin ABI's `ExtractorLink` / `SubtitleFile` / `AudioFile`
 * SHAPES but live entirely in app code — `core:cs-player` and everything
 * downstream never imports `com.lagradost.*`. The mapping happens at the
 * resolver boundary (`data:cloudstream`), where plugin classes are allowed.
 *
 * OkHttp's [Interceptor] is deliberately allowed here (okhttp is a plain app
 * dependency, not a plugin class): it carries the provider's optional
 * `getVideoInterceptor` hook onto the stream requests, exactly like upstream.
 */

/** The playable subset of the ABI's link types (torrent/magnet are filtered upstream of this module). */
enum class CsLinkType {
    /** A single progressive file (mp4/webm/mkv/…). */
    VIDEO,

    /** An HLS manifest (.m3u8) — ExoPlayer plays it natively with ABR + variant tracks. */
    M3U8,

    /** A DASH manifest (.mpd) — ExoPlayer plays it natively. NEVER hidden (the round-10 lesson). */
    DASH,
}

/** One resolved video stream, ready for the engine. */
data class CsVideoLink(
    /** Display name from the provider (e.g. "Mirror", "Streamtape", "HD-1"). */
    val name: String,
    /** The stream URL. */
    val url: String,
    /** Pixel-height quality int (ABI `Qualities` scale: 0=Auto, 400=Unknown, 2160=4K). */
    val quality: Int,
    /** Manifest/file type. */
    val type: CsLinkType,
    /** Referer the host requires ("" = none). Merged into [allHeaders] for requests. */
    val referer: String = "",
    /** Extra request headers (User-Agent included when present — see [userAgent]). */
    val headers: Map<String, String> = emptyMap(),
    /** Origin label — the provider's name (for the links sheet grouping). */
    val source: String,
    /** External audio tracks merged into the playback (rare; anime providers barely use it). */
    val audioTracks: List<CsAudioTrack> = emptyList(),
    /** Optional per-link OkHttp interceptor (the provider `getVideoInterceptor` hook). */
    val requestInterceptor: Interceptor? = null,
) {
    /** Headers + referer merged (upstream `ExtractorLink.getAllHeaders` semantics). */
    val allHeaders: Map<String, String>
        get() {
            val merged = headers.toMutableMap()
            if (referer.isNotBlank() && merged.keys.none { it.equals("referer", true) }) {
                merged["referer"] = referer
            }
            return merged
        }

    /** The User-Agent from the link headers, if the provider set one. */
    val userAgent: String?
        get() = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value

    /** Human label ("1080p" / "Auto" / "4K" / "Unknown") — the ABI `Qualities.getStringByInt` semantics, app-side. */
    val qualityLabel: String
        get() = CsQuality.label(quality)

    /** Sheet row label: "Mirror 1080p" — the upstream sources-dialog format. */
    val displayLabel: String
        get() = "$name $qualityLabel".trim()
}

/** One external subtitle file (sidecar). */
data class CsSubtitle(
    /** Display name (the provider's `lang`). */
    val name: String,
    /** Subtitle file URL (fixed with [CsMediaTypes.fixSubtitleUrl] before it gets here). */
    val url: String,
    /** Request headers for fetching the subtitle file (may differ from the video's). */
    val headers: Map<String, String> = emptyMap(),
    /** Resolved mime (see [CsMediaTypes.subtitleMime]). */
    val mimeType: String,
    /** BCP-47-ish language tag when known (drives auto-selection later). */
    val languageTag: String? = null,
    /** Stable id used for ExoPlayer track bookkeeping. */
    val id: String = "$url|$name",
)

/** One external audio track. */
data class CsAudioTrack(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/** Quality-int → label formatting (ABI `Qualities` companion semantics, app-side). */
object CsQuality {
    fun label(quality: Int): String = when (quality) {
        0 -> "Auto"
        2160 -> "4K"
        400 -> "Unknown"
        else -> "${quality}p"
    }

    /** Parses a free-text quality label ("1080p", "4K") back to a height int; 400 (Unknown) when unparseable. */
    fun fromLabel(label: String?): Int {
        if (label == null) return 400
        val digits = label.filter { it.isDigit() }.toIntOrNull() ?: return 400
        if (label.contains("4K", true)) return 2160
        return if (digits in 100..4320) digits else 400
    }
}
