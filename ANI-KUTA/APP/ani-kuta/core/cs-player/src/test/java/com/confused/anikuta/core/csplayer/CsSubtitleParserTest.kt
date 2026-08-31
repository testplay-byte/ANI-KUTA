package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 57 (round 17 — the overlay subtitle renderer): pure-JVM locks for the
 * subtitle file parsing that feeds the Compose overlay. Every shape below was
 * seen in a real provider file during the v0.4.x device rounds (dot-millis
 * SRT, VTT voice tags, ASS commas inside Text, half-broken blocks) — a future
 * refactor cannot silently change what the overlay gets to render.
 *
 * MUST be a class: top-level @Test functions compile into the file facade,
 * which JUnit4 rejects with InvalidTestClassError — the round-15 CI break.
 */
class CsSubtitleParserTest {

    /** Ok-cast helper: fails loudly (cast) when a parse unexpectedly bailed. */
    private fun cuesOf(outcome: CsSubtitleParser.ParseOutcome): List<CsCue> =
        (outcome as CsSubtitleParser.ParseOutcome.Ok).cues

    /** Unsupported-cast helper for the detection/empty locks. */
    private fun reasonOf(outcome: CsSubtitleParser.ParseOutcome): String =
        (outcome as CsSubtitleParser.ParseOutcome.Unsupported).reason

    // ── SRT ──────────────────────────────────────────────────────────────────

    @Test
    fun `srt basic cues parse with multiline text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello there

            2
            00:00:05,500 --> 00:00:08,000
            Second cue
            second line

            3
            00:01:02,250 --> 00:01:04,750
            Third
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1000, 4000, "Hello there"),
                CsCue(5500, 8000, "Second cue\nsecond line"),
                CsCue(62250, 64750, "Third"),
            ),
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)),
        )
    }

    @Test
    fun `srt dot millis and hours optional parse, index lines skipped`() {
        val srt = """
            1
            00:00:01.500 --> 00:00:02.500
            Dot millis

            2
            00:03.000 --> 00:04.000
            Hours optional
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1500, 2500, "Dot millis"),
                CsCue(3000, 4000, "Hours optional"),
            ),
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)),
        )
    }

    @Test
    fun `srt html tags and entities are stripped`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n" +
            "<i>Hello</i> &amp; <u>world</u>&lt;3\n" +
            "a&nbsp;b &quot;q&quot; &#39;s&#39;"

        assertEquals(
            "Hello & world<3\na b \"q\" 's'",
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)).single().text,
        )
    }

    @Test
    fun `srt blocks without a timing line are skipped`() {
        val srt = """
            1
            not a timing line
            stray text

            2
            00:00:01,000 --> 00:00:02,000
            Good cue
        """.trimIndent()

        assertEquals(
            listOf(CsCue(1000, 2000, "Good cue")),
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)),
        )
    }

    // ── WebVTT ───────────────────────────────────────────────────────────────

    @Test
    fun `vtt header note blocks and cue settings are handled`() {
        val vtt = """
            WEBVTT

            NOTE this comment
            spans two lines

            00:00:01.000 --> 00:00:02.000 align:center line:84%
            <v Roger>Hi there</v>

            00:00:04.000 --> 00:00:06.000
            Plain cue
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1000, 2000, "Hi there"),
                CsCue(4000, 6000, "Plain cue"),
            ),
            cuesOf(CsSubtitleParser.parse("text/vtt", vtt)),
        )
    }

    @Test
    fun `vtt cue identifier lines are skipped`() {
        val vtt = """
            WEBVTT

            intro-cue
            00:00:01.000 --> 00:00:03.000
            Identified

            42
            00:00:05.000 --> 00:00:07.000
            Numeric identifier
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1000, 3000, "Identified"),
                CsCue(5000, 7000, "Numeric identifier"),
            ),
            cuesOf(CsSubtitleParser.parse("text/vtt", vtt)),
        )
    }

    @Test
    fun `vtt with bom prefix still parses`() {
        val vtt = "\uFEFFWEBVTT\n\n00:00:01.000 --> 00:00:02.000\nBOM cue"

        assertEquals(
            listOf(CsCue(1000, 2000, "BOM cue")),
            cuesOf(CsSubtitleParser.parse(null, vtt)),
        )
    }

    // ── ASS/SSA ──────────────────────────────────────────────────────────────

    @Test
    fun `ass dialogue text keeps commas and strips override tags`() {
        val ass = """
            [Script Info]
            Title: commas inside text

            [V4+ Styles]
            Format: Name, Fontname

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,Hello, {\i1}world{\i0}, with commas
            Dialogue: 0,0:00:05.00,0:00:08.00,Default,,0,0,0,,Line one\NLine two\hwith space
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1000, 3500, "Hello, world, with commas"),
                CsCue(5000, 8000, "Line one\nLine two with space"),
            ),
            cuesOf(CsSubtitleParser.parse(null, ass)),
        )
    }

    @Test
    fun `ass custom format field order resolves indexes`() {
        val ass = """
            [Events]
            Format: ReadOrder, Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0,0:00:02.00,0:00:04.00,Default,,0,0,0,,Custom order works
        """.trimIndent()

        assertEquals(
            listOf(CsCue(2000, 4000, "Custom order works")),
            cuesOf(CsSubtitleParser.parse("text/x-ssa", ass)),
        )
    }

    @Test
    fun `ass malformed dialogue lines are skipped`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: broken,missing,fields
            not a dialogue at all
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Good
        """.trimIndent()

        assertEquals(
            listOf(CsCue(1000, 2000, "Good")),
            cuesOf(CsSubtitleParser.parse("text/x-ass", ass)),
        )
    }

    // ── Format detection ─────────────────────────────────────────────────────

    @Test
    fun `format detection follows mime then content sniffing`() {
        val srt = "00:00:01,000 --> 00:00:02,000\nHi"
        assertEquals(
            listOf(CsCue(1000, 2000, "Hi")),
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)),
        )

        val vtt = "WEBVTT\n\n00:00:03.000 --> 00:00:04.000\nHo"
        assertEquals(
            listOf(CsCue(3000, 4000, "Ho")),
            cuesOf(CsSubtitleParser.parse("text/vtt", vtt)),
        )
        assertEquals(
            listOf(CsCue(3000, 4000, "Ho")),
            cuesOf(CsSubtitleParser.parse(null, vtt)),
        )

        val ass = "[Events]\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,He"
        assertEquals(
            listOf(CsCue(5000, 6000, "He")),
            cuesOf(CsSubtitleParser.parse("text/x-ssa", ass)),
        )
        assertEquals(
            listOf(CsCue(5000, 6000, "He")),
            cuesOf(CsSubtitleParser.parse(null, ass)),
        )
    }

    @Test
    fun `unrecognizable content and empty input are unsupported`() {
        val prose = CsSubtitleParser.parse(null, "just some random prose\nwithout any timing lines")
        assertTrue(prose is CsSubtitleParser.ParseOutcome.Unsupported)
        assertEquals("no recognizable subtitle format", reasonOf(prose))

        val empty = CsSubtitleParser.parse(null, "")
        assertTrue(empty is CsSubtitleParser.ParseOutcome.Unsupported)
        assertEquals("empty subtitle content", reasonOf(empty))

        assertTrue(CsSubtitleParser.parse("text/vtt", "   ") is CsSubtitleParser.ParseOutcome.Unsupported)
    }

    // ── Post-processing + resilience ─────────────────────────────────────────

    @Test
    fun `flash cues get the 500 ms floor, cues sort by start, blank cues drop`() {
        val srt = """
            1
            00:00:10,000 --> 00:00:10,000
            Flash cue

            2
            00:00:01,000 --> 00:00:05,000
            Early cue

            3
            00:00:20,000 --> 00:00:25,000
            <i></i>

            4
            00:00:12,000 --> 00:00:14,000
            Middle cue
        """.trimIndent()

        assertEquals(
            listOf(
                CsCue(1000, 5000, "Early cue"),
                CsCue(10000, 10500, "Flash cue"),
                CsCue(12000, 14000, "Middle cue"),
            ),
            cuesOf(CsSubtitleParser.parse("application/x-subrip", srt)),
        )
    }

    @Test
    fun `garbage timing lines never throw and yield empty ok`() {
        val garbage = "1\n?? --> ??\n\n2\njunk --> junk\nstray text"

        val outcome = CsSubtitleParser.parse(null, garbage)

        assertTrue(outcome is CsSubtitleParser.ParseOutcome.Ok)
        assertTrue(cuesOf(outcome).isEmpty())
    }
}
