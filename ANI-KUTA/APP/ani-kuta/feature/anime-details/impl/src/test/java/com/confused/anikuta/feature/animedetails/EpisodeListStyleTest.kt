package com.confused.anikuta.feature.animedetails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-554 (round 66) — pure-JVM locks for the episode-row appearance algebra:
 * the lenient style-key seeding, the display-style defaults, and the
 * (style × content) pills-row visibility function. Extracted from
 * EpisodeRow.kt so the gates lock WITHOUT Compose (the D-552 sandbox-test
 * pattern — CI runs assembleDebug only, so these are executed by hand via
 * kotlinc + junit in the sandbox before every push).
 */
class EpisodeListStyleTest {

    // ── fromKey: the D-529 lenient seeding ──

    @Test
    fun `fromKey accepts the exact stored keys`() {
        assertEquals(EpisodeListRowStyle.DETAILED, EpisodeListRowStyle.fromKey("DETAILED"))
        assertEquals(EpisodeListRowStyle.COMPACT, EpisodeListRowStyle.fromKey("COMPACT"))
        assertEquals(EpisodeListRowStyle.MINIMAL, EpisodeListRowStyle.fromKey("MINIMAL"))
    }

    @Test
    fun `fromKey is case-and-space lenient`() {
        assertEquals(EpisodeListRowStyle.MINIMAL, EpisodeListRowStyle.fromKey("minimal"))
        assertEquals(EpisodeListRowStyle.COMPACT, EpisodeListRowStyle.fromKey(" Compact "))
    }

    @Test
    fun `fromKey falls back to DETAILED on null unknown and blank`() {
        // D-529: a legacy/unknown pref value must light up the SAME style the
        // renderer draws — the fallback IS DETAILED (today's look).
        assertEquals(EpisodeListRowStyle.DETAILED, EpisodeListRowStyle.fromKey(null))
        assertEquals(EpisodeListRowStyle.DETAILED, EpisodeListRowStyle.fromKey("GRID"))
        assertEquals(EpisodeListRowStyle.DETAILED, EpisodeListRowStyle.fromKey(""))
    }

    // ── EpisodeListDisplayStyle: the zero-prefs experience == pre-D-554 ──

    @Test
    fun `display style defaults equal the historical behavior`() {
        val style = EpisodeListDisplayStyle()
        assertEquals(EpisodeListRowStyle.DETAILED, style.rowStyle)
        assertTrue(style.showSynopsis)
        assertTrue(style.showDatePill)
        assertTrue(style.showAudioPills)
        assertTrue(style.showWatchProgress)
        assertTrue(style.dimWatched)
        assertTrue(style.showDownloadControl)
    }

    // ── pillsRowVisible: the (style × content) gate algebra ──

    private fun style(
        rowStyle: EpisodeListRowStyle,
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
    fun `detailed row with all content shows the pills row`() {
        assertTrue(pillsRowVisible(style(EpisodeListRowStyle.DETAILED), hasDate = true, hasAudio = true, showsSynopsis = false))
    }

    @Test
    fun `pills row renders when ANY resident survives the gates`() {
        // Date alone, audio alone, or the relocated download control alone.
        assertTrue(pillsRowVisible(style(EpisodeListRowStyle.DETAILED, audio = false), hasDate = true, hasAudio = false, showsSynopsis = false))
        assertTrue(pillsRowVisible(style(EpisodeListRowStyle.DETAILED, date = false), hasDate = false, hasAudio = true, showsSynopsis = false))
        assertTrue(pillsRowVisible(style(EpisodeListRowStyle.DETAILED, date = false, audio = false), hasDate = false, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `pills row hides when every resident is gated off`() {
        // No date, no audio, no download control.
        assertFalse(pillsRowVisible(style(EpisodeListRowStyle.DETAILED, date = false, audio = false, download = false), hasDate = false, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `date pill never renders in MINIMAL even when present`() {
        // MINIMAL is DEFINED by the bare number-disc row — the date gate is
        // style-level, not content-level. The download control is OFF so the
        // date gate is isolated (with it on, the control relocates here and
        // the row legitimately renders).
        assertFalse(pillsRowVisible(style(EpisodeListRowStyle.MINIMAL, audio = false, download = false), hasDate = true, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `minimal still renders the pills row for the relocated download control`() {
        // The MINIMAL frame keeps number + title + the download control.
        assertTrue(pillsRowVisible(style(EpisodeListRowStyle.MINIMAL), hasDate = true, hasAudio = true, showsSynopsis = false))
    }

    @Test
    fun `date pill respects the user toggle`() {
        // The download control is OFF so the date-pill gate is isolated.
        assertFalse(pillsRowVisible(style(EpisodeListRowStyle.DETAILED, date = false, audio = false, download = false), hasDate = true, hasAudio = false, showsSynopsis = false))
    }

    @Test
    fun `download control relocates to the pills row only when synopsis is absent`() {
        // Synopsis rendered → the download button lives in the synopsis
        // section → the pills row must NOT also render one.
        assertFalse(
            pillsRowVisible(
                style(EpisodeListRowStyle.DETAILED, date = false, audio = false),
                hasDate = false, hasAudio = false, showsSynopsis = true,
            ),
        )
        // Synopsis gated off (user toggle or a style without it) → the
        // control moves up — exactly the original description.isNullOrBlank()
        // behavior, now driven by the computed flag.
        assertTrue(
            pillsRowVisible(
                style(EpisodeListRowStyle.DETAILED, date = false, audio = false),
                hasDate = false, hasAudio = false, showsSynopsis = false,
            ),
        )
    }
}
