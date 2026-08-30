// Task 49 (round 9 — DASH surfacing): locks the pure MPD parser against three
// fixture shapes: static muxed (single video rep, directly playable), static
// separate audio+video (video + audio-add), and dynamic (hidden). MovieBox's
// real manifests are geo-blocked from the sandbox — these fixtures pin the
// structural contract the bridge's sniffer relies on.
package com.lagradost.cloudstream3.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpdParserTest {

    private val staticMuxed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT42S">
          <Period id="1">
            <AdaptationSet contentType="video">
              <Representation id="v1" mimeType="video/mp4" codecs="avc1.64001f" width="1280" height="720" bandwidth="2077144">
                <BaseURL>movie_720.mp4</BaseURL>
                <SegmentBase indexRange="0-999"/>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private val staticSeparateTracks = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT42S">
          <Period id="1">
            <AdaptationSet contentType="video">
              <Representation id="v1080" mimeType="video/mp4" height="1080" bandwidth="4145728">
                <BaseURL>video_1080.m4s</BaseURL>
              </Representation>
              <Representation id="v720" mimeType="video/mp4" height="720" bandwidth="2077144">
                <BaseURL>video_720.m4s</BaseURL>
              </Representation>
            </AdaptationSet>
            <AdaptationSet contentType="audio">
              <Representation id="a1" mimeType="audio/mp4" bandwidth="128000">
                <BaseURL>audio_128.m4s</BaseURL>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private val dynamic = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic" minimumUpdatePeriod="PT2S">
          <Period id="1">
            <AdaptationSet contentType="video">
              <Representation id="v1" mimeType="video/mp4" height="720" bandwidth="2000000">
                <BaseURL>live_720.mp4</BaseURL>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private val segmentTemplate = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT42S">
          <Period id="1">
            <AdaptationSet contentType="video">
              <Representation id="v1" mimeType="video/mp4" height="720" bandwidth="2000000">
                <SegmentTemplate media="seg_${'$'}Number${'$'}.m4s" initialization="init.m4s" duration="4" startNumber="1"/>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun `static muxed single-file manifest parses to one playable video rep`() {
        val info = MpdParser.parse(staticMuxed, "https://cdn.example/vod/manifest.mpd")
        assertFalse(info.dynamic)
        assertEquals(1, info.videoReps.size)
        assertEquals(0, info.audioReps.size)
        val rep = info.videoReps.first()
        assertTrue("SegmentBase over one file is still progressive", rep.singleFile)
        assertEquals(720, rep.height)
        assertEquals("https://cdn.example/vod/movie_720.mp4", rep.url)
    }

    @Test
    fun `static separate tracks parse video reps and the audio rep`() {
        val info = MpdParser.parse(staticSeparateTracks, "https://cdn.example/vod/manifest.mpd")
        assertFalse(info.dynamic)
        assertEquals(2, info.videoReps.size)
        assertEquals(1, info.audioReps.size)
        assertTrue(info.videoReps.all { it.singleFile })
        assertTrue(info.audioReps.first().singleFile)
        assertEquals(
            "https://cdn.example/vod/video_1080.m4s",
            info.videoReps.first { it.height == 1080 }.url,
        )
        assertEquals("https://cdn.example/vod/audio_128.m4s", info.audioReps.first().url)
    }

    @Test
    fun `dynamic manifest is flagged`() {
        val info = MpdParser.parse(dynamic, "https://cdn.example/live/manifest.mpd")
        assertTrue(info.dynamic)
    }

    @Test
    fun `segment-template representations are not single-file`() {
        val info = MpdParser.parse(segmentTemplate, "https://cdn.example/vod/manifest.mpd")
        assertEquals(1, info.videoReps.size)
        assertFalse("SegmentTemplate = many segment files — not progressive", info.videoReps.first().singleFile)
    }

    @Test
    fun `malformed xml yields an empty info instead of throwing`() {
        val info = MpdParser.parse("this is not xml at all <", "https://cdn.example/x.mpd")
        assertFalse(info.dynamic)
        assertTrue(info.videoReps.isEmpty())
        assertTrue(info.audioReps.isEmpty())
    }

    @Test
    fun `doctype XXE attempt is neutralized`() {
        val evil = """
            <?xml version="1.0"?>
            <!DOCTYPE MPD [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <MPD type="static"><Period><AdaptationSet contentType="video">
              <Representation id="v" mimeType="video/mp4" height="480"><BaseURL>&xxe;</BaseURL></Representation>
            </AdaptationSet></Period></MPD>
        """.trimIndent()
        val info = MpdParser.parse(evil, "https://cdn.example/x.mpd")
        // Either empty (doctype rejected) or a rep whose URL is not the file
        // contents — both are safe outcomes.
        info.videoReps.forEach { rep ->
            assertFalse(rep.url.contains("root:"))
        }
    }
}
