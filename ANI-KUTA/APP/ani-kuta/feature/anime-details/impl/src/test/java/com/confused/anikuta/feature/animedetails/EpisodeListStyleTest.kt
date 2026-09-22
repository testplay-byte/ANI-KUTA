package com.confused.anikuta.feature.animedetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-554/D-555 — pure-JVM locks for the episode-list appearance algebra:
 * the lenient style-key seeding (now with the legacy-key migration), the
 * display-style defaults, the (style × content) pills-row visibility
 * function (the CLASSIC-path algebra), and the D-555 layout helpers (the
 * TIMELINE node label, the CINEMA ghost number, the short date). Extracted
 * from EpisodeRow.kt + EpisodeLayouts.kt so the gates lock WITHOUT Compose
 * (the D-552 sandbox-test pattern — CI runs assembleDebug only, so these
 * are executed by hand via kotlinc + junit in the sandbox before push).
 */
class EpisodeListStyleTest {

    // ── fromKey: the D-529 lenient seeding + the D-555 legacy migration ──

    @Test
    fun `fromKey accepts the four exact stored keys`() {
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("CLASSIC"))
        assertEquals(EpisodeListRowStyle.GRID, EpisodeListRowStyle.fromKey("GRID"))
        assertEquals(EpisodeListRowStyle.TIMELINE, EpisodeListRowStyle.fromKey("TIMELINE"))
        assertEquals(EpisodeListRowStyle.CINEMA, EpisodeListRowStyle.fromKey("CINEMA"))
    }

    @Test
    fun `fromKey is case-and-space lenient`() {
        assertEquals(EpisodeListRowStyle.GRID, EpisodeListRowStyle.fromKey("grid"))
        assertEquals(EpisodeListRowStyle.CINEMA, EpisodeListRowStyle.fromKey(" Cinema "))
        assertEquals(EpisodeListRowStyle.TIMELINE, EpisodeListRowStyle.fromKey("Timeline"))
    }

    @Test
    fun `legacy D-554 keys migrate to CLASSIC`() {
        // The three-variation first draft was REPLACED by the four-layout
        // redesign — stored DETAILED/COMPACT/MINIMAL values fall back to the
        // look that has always been (never a crash, never a lying highlight).
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("DETAILED"))
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("COMPACT"))
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("MINIMAL"))
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("minimal"))
    }

    @Test
    fun `fromKey falls back to CLASSIC on null unknown and blank`() {
        // D-529: a legacy/unknown pref value must light up the SAME style the
        // renderer draws — the fallback IS CLASSIC (today's look).
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey(null))
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey("DETAILED_V2"))
        assertEquals(EpisodeListRowStyle.CLASSIC, EpisodeListRowStyle.fromKey(""))
    }

    // ── EpisodeListDisplayStyle: the zero-prefs experience == pre-D-554 ──

    @Test
    fun `display style defaults equal the historical behavior`() {
        val style = EpisodeListDisplayStyle()
        assertEquals(EpisodeListRowStyle.CLASSIC, style.rowStyle)
        assertTrue(style.showSynopsis)
        assertTrue(style.showDatePill)
        assertTrue(style.showAudioPills)
        assertTrue(style.showWatchProgress)
        assertTrue(style.dimWatched)
        assertTrue(style.showDownloadControl)
    }

    // ── pillsRowVisible: the CLASSIC-path (style × content) gate algebra ──

    private fun style(
        rowStyle: EpisodeListRowStyle = EpisodeListRowStyle.CLASSIC,
        date: Boolean = true,
        audio: Boolean = true,
        download: Boolean = true,
    ) = EpisodeListDisplayStyle(
        rowStyle = rowStyle,
        showDatePill = date,
        showAudioPills = audio,
        showDownloadControl = download,
    )

    @Test
    fun `classic row with all content shows the pills row`() {
        assertTrue(pillsRowVisible(style(), hasDate = true, hasAudio = true, showsSynopsis = false))
    }

    @Test
    fun `pills row renders when ANY resident survives the gates`() {
        // Date alone, audio alone, or the relocated download control alone.
        assertTrue(pillsRowVisible(style(audio = false), hasDate = true, hasAudio = false, showsSynopsis = false))
        assertTrue(pillsRowVisible(style(date = false), hasDate = false, hasAudio = true, showsSynopsis = false))
        assertTrue(pillsRowVisible(style(date = false, audio = false), hasDate = false, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `pills row hides when every resident is gated off`() {
        // No date, no audio, no download control.
        assertFalse(pillsRowVisible(style(date = false, audio = false, download = false), hasDate = false, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `date pill respects the user toggle`() {
        // The download control is OFF so the date-pill gate is isolated.
        assertFalse(pillsRowVisible(style(date = false, audio = false, download = false), hasDate = true, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `download control relocates to the pills row only when synopsis is absent`() {
        // Synopsis rendered → the download button lives in the synopsis
        // section → the pills row must NOT also render one.
        assertFalse(
            pillsRowVisible(
                style(date = false, audio = false),
                hasDate = false, hasAudio = false, showsSynopsis = true,
            ),
        )
        // Synopsis gated off (user toggle) → the control moves up — exactly
        // the original description.isNullOrBlank() behavior, now driven by
        // the computed flag.
        assertTrue(
            pillsRowVisible(
                style(date = false, audio = false),
                hasDate = false, hasAudio = false, showsSynopsis = false,
            ),
        )
    }

    // ── D-555: the GRID/TIMELINE/CINEMA helpers ──

    @Test
    fun `formatShortDate renders the compact schedule label`() {
        // Jan 1, 2025 00:00 UTC — the sample-episode epoch.
        assertEquals("Jan 1", formatShortDate(1735689600000L))
        assertEquals("", formatShortDate(0L))
        assertEquals("", formatShortDate(-5L))
    }

    @Test
    fun `timeline node label prefers the date and falls back to the episode number`() {
        // The air date is the schedule's primary element while the toggle is
        // on AND a date exists; the spine NEVER goes unlabeled.
        assertEquals("Jan 1", timelineNodeLabel(showDate = true, shortDateText = "Jan 1", epNumText = "3"))
        assertEquals("EP 3", timelineNodeLabel(showDate = true, shortDateText = null, epNumText = "3"))
        assertEquals("EP 3", timelineNodeLabel(showDate = true, shortDateText = "", epNumText = "3"))
        // The user's date toggle off → number nodes.
        assertEquals("EP 3", timelineNodeLabel(showDate = false, shortDateText = "Jan 1", epNumText = "3"))
    }

    @Test
    fun `ghost episode number zero-pads below 100 and stays honest above`() {
        assertEquals("01", ghostEpisodeNumber(1f))
        assertEquals("09", ghostEpisodeNumber(9f))
        assertEquals("42", ghostEpisodeNumber(42f))
        assertEquals("105", ghostEpisodeNumber(105f))
        assertEquals("100", ghostEpisodeNumber(100f))
    }
}
