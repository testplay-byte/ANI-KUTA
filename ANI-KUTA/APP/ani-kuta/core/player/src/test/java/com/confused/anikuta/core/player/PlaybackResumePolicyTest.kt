package com.confused.anikuta.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-761 (round 126 — the end-of-episode + resume contract): the resume and
 * end-of-episode locks.
 */
class PlaybackResumePolicyTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    // ── The resume contract ───────────────────────────────────────────────────

    @Test
    fun `a never-watched episode starts at the beginning`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 0L, durationMs = 24 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertFalse(plan.offerResume)
    }

    @Test
    fun `a partially watched episode resumes at the stored position`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 5 * minute, durationMs = 24 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.RESUME_AT_POSITION, plan.decision)
        assertFalse(plan.offerResume)
    }

    @Test
    fun `a completed episode starts at the beginning and offers the old position`() {
        // The bug: the old code seeked to the stored position — the video
        // started already over.
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 23 * minute, durationMs = 24 * minute, isCompleted = true)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertTrue("the old position must be OFFERED", plan.offerResume)
        assertEquals(23 * minute, plan.resumePositionMs)
    }

    @Test
    fun `an effectively watched episode starts at the beginning`() {
        // 90 % of a 10-minute episode — past the 85 % watched threshold, but
        // not marked completed (the auto-mark never fired).
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 9 * minute, durationMs = 10 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertTrue(plan.offerResume)
    }

    @Test
    fun `just below the threshold still resumes`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 8 * minute, durationMs = 10 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.RESUME_AT_POSITION, plan.decision)
    }

    @Test
    fun `an unknown duration with a stored position resumes`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 5 * minute, durationMs = 0L, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.RESUME_AT_POSITION, plan.decision)
    }

    // ── The end-of-episode contract ───────────────────────────────────────────

    @Test
    fun `the next-episode action appears at ninety five percent`() {
        assertTrue(PlaybackResumePolicy.shouldOfferNextEpisode(positionMs = 95 * 1000L, durationMs = 100 * 1000L))
    }

    @Test
    fun `the next-episode action does not appear earlier`() {
        assertFalse(PlaybackResumePolicy.shouldOfferNextEpisode(positionMs = 94 * 1000L, durationMs = 100 * 1000L))
    }

    @Test
    fun `an unknown duration never offers the next episode`() {
        assertFalse(PlaybackResumePolicy.shouldOfferNextEpisode(positionMs = 10 * 1000L, durationMs = 0L))
    }

    // ── The unit contract (PB-8) ─────────────────────────────────────────────

    @Test
    fun `milliseconds convert to the seconds mpv wants`() {
        assertEquals(0.0, PlaybackResumePolicy.toMpvSeconds(0L), 0.0001)
        assertEquals(1.5, PlaybackResumePolicy.toMpvSeconds(1500L), 0.0001)
        assertEquals(90.0, PlaybackResumePolicy.toMpvSeconds(90_000L), 0.0001)
    }

    @Test
    fun `the timestamp reads as minutes and seconds`() {
        assertEquals("0:00", PlaybackResumePolicy.formatTimestamp(0L))
        assertEquals("1:30", PlaybackResumePolicy.formatTimestamp(90_000L))
        assertEquals("12:34", PlaybackResumePolicy.formatTimestamp(754_000L))
    }

    @Test
    fun `a long timestamp reads as hours minutes seconds`() {
        assertEquals("1:02:03", PlaybackResumePolicy.formatTimestamp(hour + 2 * minute + 3_000L))
    }
}
