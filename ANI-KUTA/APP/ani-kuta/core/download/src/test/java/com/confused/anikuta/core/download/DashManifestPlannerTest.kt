package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-552 — the DASH segment planner's span locks (pure JVM). The fixtures are
 * SYNTHETIC minimal MPDs (built here, not recorded) covering the shapes the
 * v1.1.25 device round exposed: the DURATION-LESS manifest whose audio
 * SegmentTimeline uses r="-1" (the "audio 1 was just a random small snippet"
 * root cause — every negative-r entry collapsed to ONE segment while the
 * explicit video timeline stayed complete), the explicit-duration regression
 * shape, the short-audio validation input, the loud timeline-less failure,
 * the SegmentList unknown-span case, the preferredHeight pick, and the
 * multi-period validation skip.
 */
class DashManifestPlannerTest {

    private val manifestUrl = "https://cdn.example.com/ep/index.mpd"

    /** Minimal static MPD wrapper around the given Periods (duration attrs optional).
    NOTE: vararg comes FIRST — a duration-first signature would bind a positional
    period string to [mediaPresentationDuration] (the Kotlin argument-binding trap). */
    private fun mpd(vararg periods: String, mediaPresentationDuration: String? = null): ByteArray = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\"")
        if (mediaPresentationDuration != null) append(" mediaPresentationDuration=\"$mediaPresentationDuration\"")
        append(">\n")
        periods.forEach { append(it) }
        append("</MPD>\n")
    }.toByteArray()

    private fun period(vararg adaptationSets: String): String = """
        <Period>
          ${adaptationSets.joinToString("\n")}
        </Period>
    """.trimIndent()

    /** A video set whose timeline is ONE explicit entry: [segments] × [segSec]s. */
    private fun explicitVideoSet(
        id: String,
        height: Int,
        segSec: Long,
        segments: Long,
        timescale: Long = 1000,
    ): String = """
        <AdaptationSet mimeType="video/mp4" contentType="video">
          <SegmentTemplate timescale="$timescale" media="v-${'$'}Number%05d$.m4s" initialization="v-init.m4s" startNumber="1">
            <SegmentTimeline><S t="0" d="${segSec * timescale}" r="${segments - 1}"/></SegmentTimeline>
          </SegmentTemplate>
          <Representation id="$id" mimeType="video/mp4" bandwidth="1500000" width="${height * 16 / 9}" height="$height"/>
        </AdaptationSet>
    """.trimIndent()

    /** An audio set whose timeline is ONE entry: r="-1" (repeat to period end). */
    private fun negativeRAudioSet(): String = """
        <AdaptationSet mimeType="audio/mp4" contentType="audio">
          <SegmentTemplate timescale="48000" media="a-${'$'}Number%05d$.m4s" initialization="a-init.m4s" startNumber="1">
            <SegmentTimeline><S t="0" d="96000" r="-1"/></SegmentTimeline>
          </SegmentTemplate>
          <Representation id="a1" mimeType="audio/mp4" bandwidth="128000" audioSamplingRate="48000"/>
        </AdaptationSet>
    """.trimIndent()

    /** An audio set with an EXPLICIT timeline ([segments] × [segSec]s). */
    private fun explicitAudioSet(segSec: Long, segments: Int, timescale: Long = 48000): String = """
        <AdaptationSet mimeType="audio/mp4" contentType="audio">
          <SegmentTemplate timescale="$timescale" media="a-${'$'}Number%05d$.m4s" initialization="a-init.m4s" startNumber="1">
            <SegmentTimeline>
              ${List(segments) { i -> """<S t="${i * segSec * timescale}" d="${segSec * timescale}"/>""" }.joinToString("\n")}
            </SegmentTimeline>
          </SegmentTemplate>
          <Representation id="a1" mimeType="audio/mp4" bandwidth="128000" audioSamplingRate="48000"/>
        </AdaptationSet>
    """.trimIndent()

    /** A timeline-LESS audio set (SegmentTemplate @duration, no SegmentTimeline). */
    private fun durationOnlyAudioSet(): String = """
        <AdaptationSet mimeType="audio/mp4" contentType="audio">
          <SegmentTemplate timescale="48000" duration="96000" media="a-${'$'}Number%05d$.m4s" initialization="a-init.m4s" startNumber="1"/>
          <Representation id="a1" mimeType="audio/mp4" bandwidth="128000" audioSamplingRate="48000"/>
        </AdaptationSet>
    """.trimIndent()

    /** THE SNIPPET LOCK — the v1.1.25 device round's root cause. */
    @Test
    fun `duration-less mpd expands audio r=-1 against the video timeline's span`() {
        // 36 × 100s = 3600s of video, explicit timeline; the audio timeline is
        // ONE r="-1" entry of 2s segments. Pre-D-552 the audio plan collapsed
        // to init + 1 segment (a "random small snippet"); post-D-552 it
        // expands to the video-derived 3600s (1800 × 2s segments).
        val plan = DashManifestPlanner.parse(
            mpd(period(explicitVideoSet("v1", 1080, segSec = 100, segments = 36), negativeRAudioSet())),
            manifestUrl,
            null,
        )
        assertNull(plan.unsupportedReason)
        assertEquals(1, plan.audioGroups.size)
        assertEquals(1801, plan.audioGroups[0].size) // init + 1800 media segments
        assertTrue(plan.audioGroups[0].first().isInit)
        assertEquals(3600.0, plan.videoSpanSec!!, 0.001)
        assertEquals(3600.0, plan.audioGroupSpansSec[0]!!, 0.001)
    }

    /** The explicit-duration shape never depended on the derivation — locked. */
    @Test
    fun `mediaPresentationDuration still drives audio r=-1 expansion`() {
        val plan = DashManifestPlanner.parse(
            mpd(
                period(explicitVideoSet("v1", 720, segSec = 100, segments = 36), negativeRAudioSet()),
                mediaPresentationDuration = "PT1H0M0S",
            ),
            manifestUrl,
            null,
        )
        assertNull(plan.unsupportedReason)
        assertEquals(1801, plan.audioGroups[0].size)
        assertEquals(3600.0, plan.audioGroupSpansSec[0]!!, 0.001)
    }

    /** The validation INPUT: a genuinely short audio plan is measurable. */
    @Test
    fun `a short explicit audio timeline reports its small span`() {
        val plan = DashManifestPlanner.parse(
            mpd(period(explicitVideoSet("v1", 1080, segSec = 100, segments = 36), explicitAudioSet(segSec = 2, segments = 2))),
            manifestUrl,
            null,
        )
        assertNull(plan.unsupportedReason)
        assertEquals(3600.0, plan.videoSpanSec!!, 0.001)
        assertEquals(4.0, plan.audioGroupSpansSec[0]!!, 0.001)
    }

    /** The D-552 spec fix: @duration is ALREADY in timescale ticks — a
     *  timeline-less audio set expands against the derived span exactly like
     *  the timeline shapes (the old `duration * timescale` double-scaling
     *  turned a 2s segment into a 96,000s "segment" and would have masked a
     *  snippet behind a huge span). */
    @Test
    fun `timeline-less audio expands against the derived span`() {
        val plan = DashManifestPlanner.parse(
            mpd(period(explicitVideoSet("v1", 720, segSec = 100, segments = 36), durationOnlyAudioSet())),
            manifestUrl,
            null,
        )
        assertNull(plan.unsupportedReason)
        assertEquals(1, plan.audioGroups.size)
        // 3600s / 2s = 1800 media segments + init.
        assertEquals(1801, plan.audioGroups[0].size)
        assertEquals(3600.0, plan.audioGroupSpansSec[0]!!, 0.001)
    }

    /** A timeline-less audio set with NO duration anywhere still fails LOUDLY
     *  (the video's SegmentList span is unknowable, so there is no clock to
     *  borrow) — the downloader then refuses a silent video honestly. */
    @Test
    fun `timeline-less audio without any duration plans zero groups but is counted`() {
        val videoSet = """
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <Representation id="v1" mimeType="video/mp4" bandwidth="1500000" height="720">
                <SegmentList timescale="1000" duration="3600000">
                  <Initialization sourceURL="v-init.mp4"/>
                  <SegmentURL media="v-seg1.mp4"/>
                </SegmentList>
              </Representation>
            </AdaptationSet>
        """.trimIndent()
        val plan = DashManifestPlanner.parse(
            mpd(period(videoSet, durationOnlyAudioSet())),
            manifestUrl,
            null,
        )
        assertNull(plan.unsupportedReason) // the video carries the plan
        assertEquals(1, plan.audioSetsPresent) // the downloader refuses: declared audio, zero planned groups
        assertEquals(0, plan.audioGroups.size)
    }

    /** Timeline-less addressing (SegmentList) leaves the spans unknowable. */
    @Test
    fun `segmentlist addressing reports unknown spans`() {
        val videoSet = """
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <Representation id="v1" mimeType="video/mp4" bandwidth="1500000" height="720">
                <SegmentList timescale="1000" duration="3600000">
                  <Initialization sourceURL="v-init.mp4"/>
                  <SegmentURL media="v-seg1.mp4"/>
                </SegmentList>
              </Representation>
            </AdaptationSet>
        """.trimIndent()
        val audioSet = """
            <AdaptationSet mimeType="audio/mp4" contentType="audio">
              <Representation id="a1" mimeType="audio/mp4" bandwidth="128000">
                <SegmentList timescale="48000" duration="172800000">
                  <Initialization sourceURL="a-init.mp4"/>
                  <SegmentURL media="a-seg1.mp4"/>
                </SegmentList>
              </Representation>
            </AdaptationSet>
        """.trimIndent()
        val plan = DashManifestPlanner.parse(mpd(period(videoSet, audioSet)), manifestUrl, null)
        assertNull(plan.unsupportedReason)
        assertNull(plan.videoSpanSec)
        assertEquals(1, plan.audioGroups.size)
        assertNull(plan.audioGroupSpansSec[0])
    }

    /** The D-539 closest-rep pick is untouched by the span work. */
    @Test
    fun `preferredHeight picks the closest video rep`() {
        val set = """
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <SegmentTemplate timescale="1000" media="v-${'$'}Number%05d$.m4s" initialization="v-init.m4s" startNumber="1">
                <SegmentTimeline><S t="0" d="100000" r="35"/></SegmentTimeline>
              </SegmentTemplate>
              <Representation id="v1080" mimeType="video/mp4" bandwidth="1600000" height="1080"/>
              <Representation id="v720" mimeType="video/mp4" bandwidth="800000" height="720"/>
              <Representation id="v480" mimeType="video/mp4" bandwidth="350000" height="480"/>
            </AdaptationSet>
        """.trimIndent()
        val plan = DashManifestPlanner.parse(mpd(period(set)), manifestUrl, 720)
        assertNull(plan.unsupportedReason)
        assertEquals("v720", plan.videoRep?.id)
        assertEquals(720, plan.videoRep?.height)
    }

    /** Multi-period manifests skip the span validation (per-period vs whole-manifest false positives). */
    @Test
    fun `multi-period manifests leave the spans unvalidated`() {
        val p = period(explicitVideoSet("v1", 720, segSec = 100, segments = 1), negativeRAudioSet())
        val plan = DashManifestPlanner.parse(mpd(p, p), manifestUrl, null)
        assertNull(plan.unsupportedReason)
        assertNull(plan.videoSpanSec)
        assertEquals(2, plan.audioGroups.size)
        assertEquals(listOf<Double?>(null, null), plan.audioGroupSpansSec)
    }
}
