package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM locks for the CS playback mime/quality/url-hygiene mapping
 * (task 52 / Phase B). These encode the upstream CloudStream behavior the
 * engine ports (research R12-A) so a future refactor cannot silently change
 * what reaches ExoPlayer.
 */
class CsMediaTypesTest {

    // ── Video mime per link type (upstream CS3IPlayer map) ──────────────────

    @Test
    fun `video link maps to mp4 mime`() {
        assertEquals("video/mp4", CsMediaTypes.mimeFor(CsLinkType.VIDEO))
    }

    @Test
    fun `m3u8 link maps to hls application mime`() {
        assertEquals("application/x-mpegURL", CsMediaTypes.mimeFor(CsLinkType.M3U8))
    }

    @Test
    fun `dash link maps to mpd application mime`() {
        assertEquals("application/dash+xml", CsMediaTypes.mimeFor(CsLinkType.DASH))
    }

    // ── Subtitle mime by extension (upstream toSubtitleMimeType) ────────────

    @Test
    fun `vtt urls map to text vtt`() {
        assertEquals("text/vtt", CsMediaTypes.subtitleMime("https://host/sub.en.vtt"))
    }

    @Test
    fun `srt urls map to subrip`() {
        assertEquals("application/x-subrip", CsMediaTypes.subtitleMime("https://host/sub.srt"))
    }

    @Test
    fun `xml and ttml urls map to ttml`() {
        assertEquals("application/ttml+xml", CsMediaTypes.subtitleMime("https://host/sub.xml"))
        assertEquals("application/ttml+xml", CsMediaTypes.subtitleMime("https://host/sub.ttml"))
    }

    @Test
    fun `extension-less urls default to subrip`() {
        assertEquals("application/x-subrip", CsMediaTypes.subtitleMime("https://host/subtitle?id=1"))
    }

    // ── Protocol-relative subtitle URL fix (upstream getFixedUrl) ───────────

    @Test
    fun `protocol relative subtitle url gets https scheme`() {
        assertEquals("https://host/sub.vtt", CsMediaTypes.fixSubtitleUrl("//host/sub.vtt"))
    }

    @Test
    fun `normal subtitle url passes through`() {
        assertEquals("https://host/sub.vtt", CsMediaTypes.fixSubtitleUrl("https://host/sub.vtt"))
    }
}

class CsQualityTest {

    @Test
    fun `height ints format as pixels`() {
        assertEquals("1080p", CsQuality.label(1080))
        assertEquals("720p", CsQuality.label(720))
        assertEquals("480p", CsQuality.label(480))
    }

    @Test
    fun `sentinels format as words`() {
        assertEquals("Auto", CsQuality.label(0))
        assertEquals("4K", CsQuality.label(2160))
        assertEquals("Unknown", CsQuality.label(400))
    }

    @Test
    fun `free text labels parse back to heights`() {
        assertEquals(1080, CsQuality.fromLabel("1080p"))
        assertEquals(720, CsQuality.fromLabel("720"))
        assertEquals(2160, CsQuality.fromLabel("4K"))
    }

    @Test
    fun `garbage labels fall back to unknown`() {
        assertEquals(400, CsQuality.fromLabel("Auto"))
        assertEquals(400, CsQuality.fromLabel(null))
        assertEquals(400, CsQuality.fromLabel("hd"))
    }
}

class CsVideoLinkHeadersTest {

    @Test
    fun `referer merges into headers case-insensitively`() {
        val link = CsVideoLink(
            name = "Mirror", url = "https://host/v.mp4", quality = 1080,
            type = CsLinkType.VIDEO, referer = "https://ref/", source = "Prov",
        )
        assertEquals(mapOf("referer" to "https://ref/"), link.allHeaders)
    }

    @Test
    fun `existing referer header is not duplicated`() {
        val link = CsVideoLink(
            name = "Mirror", url = "https://host/v.mp4", quality = 1080,
            type = CsLinkType.VIDEO, referer = "https://ignored/",
            headers = mapOf("Referer" to "https://real/"), source = "Prov",
        )
        assertEquals(mapOf("Referer" to "https://real/"), link.allHeaders)
    }

    @Test
    fun `blank referer adds nothing`() {
        val link = CsVideoLink(
            name = "Mirror", url = "https://host/v.mp4", quality = 1080,
            type = CsLinkType.VIDEO, headers = mapOf("X-A" to "1"), source = "Prov",
        )
        assertEquals(mapOf("X-A" to "1"), link.allHeaders)
    }

    @Test
    fun `user agent extracted case-insensitively`() {
        val link = CsVideoLink(
            name = "M", url = "u", quality = 0, type = CsLinkType.M3U8,
            headers = mapOf("user-agent" to "CS-UA"), source = "P",
        )
        assertEquals("CS-UA", link.userAgent)
    }

    @Test
    fun `display label is name plus quality`() {
        val link = CsVideoLink(
            name = "Mirror", url = "u", quality = 1080, type = CsLinkType.VIDEO, source = "P",
        )
        assertEquals("Mirror 1080p", link.displayLabel)
    }
}
