// Task 49 (round 9): locks the pure master-playlist parser the bridge reuses
// for HLS quality selection. Plugin-facing behavior (m3u8Generation /
// generateM3u8) is unchanged — this pins the NEW pure function.
package com.lagradost.cloudstream3.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8ParseMasterPlaylistTest {

    private val masterPlaylist = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=4145728,RESOLUTION=1920x1080,CODECS="avc1.640028"
        1080_v2.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2077144,RESOLUTION=1280x720,CODECS="avc1.64001f"
        720_v2.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1018317,RESOLUTION=854x480,CODECS="avc1.64001e"
        480_v2.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1018317,RESOLUTION=854x480
        480_duplicate_bandwidth.m3u8
    """.trimIndent()

    @Test
    fun `master playlist fans out per-quality variants with heights`() {
        val variants = M3u8Helper.parseMasterPlaylist(
            masterPlaylist,
            "https://cdn.example/video/master.m3u8",
        )
        assertEquals(listOf(1080, 720, 480), variants.map { it.quality })
        assertEquals(
            "https://cdn.example/video/1080_v2.m3u8",
            variants[0].streamUrl,
        )
        assertEquals(
            "https://cdn.example/video/720_v2.m3u8",
            variants[1].streamUrl,
        )
    }

    @Test
    fun `duplicate qualities collapse to the first occurrence`() {
        val variants = M3u8Helper.parseMasterPlaylist(
            masterPlaylist,
            "https://cdn.example/video/master.m3u8",
        )
        assertEquals(3, variants.size)
        assertEquals(480, variants.last().quality)
    }

    @Test
    fun `media playlist returns empty (no variants)`() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg0.ts
            #EXTINF:6.0,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val variants = M3u8Helper.parseMasterPlaylist(
            mediaPlaylist,
            "https://cdn.example/video/index.m3u8",
        )
        assertTrue(variants.isEmpty())
    }

    @Test
    fun `absolute variant URIs are preserved`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1280x720
            https://mirror.other-cdn.net/hls/720/index.m3u8
        """.trimIndent()
        val variants = M3u8Helper.parseMasterPlaylist(
            playlist,
            "https://cdn.example/master.m3u8",
        )
        assertEquals(1, variants.size)
        assertEquals("https://mirror.other-cdn.net/hls/720/index.m3u8", variants[0].streamUrl)
    }

    @Test
    fun `variant without RESOLUTION falls back to the provided quality`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1
            index.m3u8
        """.trimIndent()
        val variants = M3u8Helper.parseMasterPlaylist(
            playlist,
            "https://cdn.example/master.m3u8",
            fallbackQuality = 720,
        )
        assertEquals(1, variants.size)
        assertEquals(720, variants[0].quality)
    }
}
