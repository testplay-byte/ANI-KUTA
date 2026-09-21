package com.confused.anikuta.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D-551 — the DASH video-height probe parser's pure-JVM locks. The fixtures
 * are SYNTHETIC minimal MPDs (built here, not recorded) covering the shapes
 * the resolve sheet's probe must survive: the aoneroom-style static template
 * manifest (3 video reps + 1 audio rep), audio-declared sets, garbage bytes,
 * and a non-MPD root.
 */
class DashManifestHeightsTest {

    /** Minimal static MPD wrapper around the given AdaptationSet XML. */
    private fun mpd(vararg adaptationSets: String): ByteArray = """
        <?xml version="1.0" encoding="utf-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
          <Period>
            ${adaptationSets.joinToString("\n")}
          </Period>
        </MPD>
    """.trimIndent().toByteArray()

    /** A video AdaptationSet with one Representation per [height]. */
    private fun videoSet(vararg heights: Int): String = """
        <AdaptationSet mimeType="video/mp4" contentType="video">
          <SegmentTemplate timescale="1000" media="chunk-stream0-$Number%05d$.m4s" initialization="init-stream0.m4s" startNumber="1"/>
          ${heights.joinToString("\n") { h ->
              """<Representation id="v$h" mimeType="video/mp4" bandwidth="${h * 1000}" width="${h * 16 / 9}" height="$h"/>"""
          }}
        </AdaptationSet>
    """.trimIndent()

    private val audioSet = """
        <AdaptationSet mimeType="audio/mp4" contentType="audio">
          <SegmentTemplate timescale="48000" media="chunk-stream1-$Number%05d$.m4s" initialization="init-stream1.m4s" startNumber="1"/>
          <Representation id="a1" mimeType="audio/mp4" bandwidth="128000" audioSamplingRate="48000"/>
        </AdaptationSet>
    """.trimIndent()

    @Test
    fun `the aoneroom shape lists every video rep descending`() {
        val heights = DashManifestHeights.parse(
            mpd(videoSet(1080, 720, 480), audioSet),
        )
        assertEquals(listOf(1080, 720, 480), heights)
    }

    @Test
    fun `heights dedupe and sort descending across sets`() {
        val heights = DashManifestHeights.parse(
            mpd(videoSet(480, 1080), videoSet(720), audioSet),
        )
        assertEquals(listOf(1080, 720, 480), heights)
    }

    @Test
    fun `audio reps never leak a height`() {
        // An audio rep carries no height — the audio set yields nothing even
        // when it is the only set in the manifest.
        assertNull(DashManifestHeights.parse(mpd(audioSet)))
    }

    @Test
    fun `a content-component audio set is skipped even without set attrs`() {
        val set = """
            <AdaptationSet mimeType="audio/mp4">
              <ContentComponent contentType="audio" id="1"/>
              <Representation id="a1" mimeType="audio/mp4" bandwidth="128000"/>
            </AdaptationSet>
        """.trimIndent()
        assertNull(DashManifestHeights.parse(mpd(set)))
    }

    @Test
    fun `garbage and non-mpd roots return null`() {
        assertNull(DashManifestHeights.parse(byteArrayOf()))
        assertNull(DashManifestHeights.parse("not xml at all".toByteArray()))
        assertNull(DashManifestHeights.parse("<html><body>403 Forbidden</body></html>".toByteArray()))
    }

    @Test
    fun `a video set with no height attributes returns null`() {
        val set = """
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <Representation id="v1" mimeType="video/mp4" bandwidth="1000"/>
            </AdaptationSet>
        """.trimIndent()
        assertNull(DashManifestHeights.parse(mpd(set)))
    }
}
