package com.confused.anikuta.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resume and end-of-episode locks.
 */
class PlaybackResumePolicyTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    // -- The resume contract -------------------------------------------------

    @Test
    fun `a never-watched episode starts at the beginning`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 0L, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertEquals(0L, plan.resumePositionMs)
    }

    @Test
    fun `a partially watched episode resumes at the stored position`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 5 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.RESUME_AT_POSITION, plan.decision)
        assertEquals(5 * minute, plan.resumePositionMs)
    }

    @Test
    fun `a completed episode starts at the beginning`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 23 * minute, isCompleted = true)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertEquals(0L, plan.resumePositionMs)
    }

    @Test
    fun `a nearly finished but incomplete episode still resumes`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 9 * minute, isCompleted = false)
        assertEquals(PlaybackResumePolicy.StartDecision.RESUME_AT_POSITION, plan.decision)
        assertEquals(9 * minute, plan.resumePositionMs)
    }

    @Test
    fun `a completed episode with no stored position starts at the beginning`() {
        val plan = PlaybackResumePolicy.plan(storedPositionMs = 0L, isCompleted = true)
        assertEquals(PlaybackResumePolicy.StartDecision.START_FROM_BEGINNING, plan.decision)
        assertEquals(0L, plan.resumePositionMs)
    }

    // -- The end-of-episode contract -----------------------------------------

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

    // -- The unit contract (PB-8) --------------------------------------------

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
