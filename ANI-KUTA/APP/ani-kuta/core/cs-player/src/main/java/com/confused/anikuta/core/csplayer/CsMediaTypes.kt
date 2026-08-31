package com.confused.anikuta.core.csplayer

import androidx.media3.common.MimeTypes

/**
 * Mime-type + URL hygiene for the CS playback pipeline (task 52).
 *
 * The video mapping mirrors upstream CloudStream's CS3IPlayer.loadOnlinePlayer
 * exactly (research R12-A §5): M3U8 → APPLICATION_M3U8, DASH → APPLICATION_MPD,
 * VIDEO → VIDEO_MP4. The subtitle mapping mirrors PlayerSubtitleHelper's
 * `String.toSubtitleMimeType()` (research R12-A §6): extension-based with an
 * SRT default, because most providers ship .srt/.vtt and ExoPlayer needs the
 * mime hint to pick a decoder for sidecar files.
 */
object CsMediaTypes {

    /** The MediaItem mime per link type — the upstream map, verbatim semantics. */
    fun mimeFor(type: CsLinkType): String = when (type) {
        CsLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
        CsLinkType.DASH -> MimeTypes.APPLICATION_MPD
        CsLinkType.VIDEO -> MimeTypes.VIDEO_MP4
    }

    /** Subtitle mime by URL extension (upstream: vtt / srt / xml+ttml / default srt). */
    fun subtitleMime(url: String): String = when {
        url.endsWith("vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        url.endsWith("srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
        url.endsWith("xml", ignoreCase = true) || url.endsWith("ttml", ignoreCase = true) ->
            MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    /**
     * Some providers emit protocol-relative subtitle URLs ("//host/sub.vtt") —
     * ExoPlayer cannot fetch those. Upstream `SubtitleData.getFixedUrl()` fix.
     */
    fun fixSubtitleUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    /**
     * Task 55 (round 15) — CONTENT-based mime detection.
     *
     * The extension guess above lies in the wild: providers serve VTT files
     * from extension-less URLs ("/subtitle?id=3"), and a VTT parsed as SubRip
     * yields ZERO cues — the v0.4.2 device round's "subtitles never attached"
     * class. The resolver fetches the first bytes of each sub (tiny files) and
     * this sniffs the actual format:
     *  - "WEBVTT" magic (BOM-tolerant)            → text/vtt
     *  - "<?xml" / "<tt" TTML root                → application/ttml+xml
     *  - SRT shape: digit line, then "00:00:01,000 --> …" → application/x-subrip
     *  - undetectable                             → null (keep the extension guess)
     */
    fun sniffSubtitleMime(firstBytes: String): String? {
        val head = firstBytes.trimStart('\uFEFF', '﻿').trimStart()
        return when {
            head.startsWith("WEBVTT", ignoreCase = true) -> MimeTypes.TEXT_VTT
            head.startsWith("<?xml", ignoreCase = true) ||
                head.startsWith("<tt", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
            looksLikeSrt(firstBytes) -> MimeTypes.APPLICATION_SUBRIP
            else -> null
        }
    }

    /** SRT sniff: somewhere in the first bytes there is an "HH:MM:SS,mmm -->" arrow line. */
    private fun looksLikeSrt(firstBytes: String): Boolean =
        Regex("\\d{1,2}:\\d{2}:\\d{2},\\d{3}\\s*-->").containsMatchIn(firstBytes)
}
