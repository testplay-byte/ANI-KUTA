package com.confused.anikuta.core.watchprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 120 (D-746) locked the resume contract; round 121 reworked it from
 * the device round: the 60 s recording floor, the 15 s resume rewind, and
 * the near-end-fresh guard. Pure decision logic with no Android
 * dependencies — runs on the JVM test runner.
 */
class PlaybackResumePolicyTest {

    private fun progress(
        position: Long,
        duration: Long = 1000L,
        completed: Boolean = false,
        userMarked: Boolean = false,
        suppressed: Boolean = false,
    ) = WatchProgress(
        episodeKey = "m|00001",
        mainId = "m",
        position = position,
        duration = duration,
        completed = completed,
        completedAt = null,
        lastWatchedAt = 0L,
        watchCount = 0,
        firstWatchedAt = null,
        autoMarkSuppressed = suppressed,
        userMarkedWatched = userMarked,
    )

    @Test
    fun `null progress starts at beginning`() {
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(null),
        )
    }

    @Test
    fun `watched episode starts fresh even with a near-end position`() {
        // The round-119 bug: keep-open pinned position ≈ duration, the dispose
        // save persisted it, and re-open seeked straight to the end.
        val watched = progress(position = 998L, duration = 1000L, completed = true)
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(watched),
        )
    }

    @Test
    fun `sticky user-marked watched starts fresh`() {
        val marked = progress(position = 500L, userMarked = true)
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(marked),
        )
    }

    @Test
    fun `un-marked mid-episode position still resumes`() {
        // The user un-marked it but the stored position is mid-episode — they
        // are re-watching from there, not from zero.
        val suppressed = progress(position = 400L, completed = false, suppressed = true)
        assertEquals(
            PlaybackResumePolicy.Decision.ResumeAtPosition(400L - PlaybackResumePolicy.RESUME_REWIND_SECONDS),
            PlaybackResumePolicy.plan(suppressed),
        )
    }

    @Test
    fun `partial episode resumes fifteen seconds before the saved position`() {
        // Round 121: the user re-lands with context, not mid-scene.
        assertEquals(
            PlaybackResumePolicy.Decision.ResumeAtPosition(485L),
            PlaybackResumePolicy.plan(progress(position = 500L)),
        )
    }

    @Test
    fun `resume never seeks before zero`() {
        assertEquals(
            PlaybackResumePolicy.Decision.ResumeAtPosition(0L),
            PlaybackResumePolicy.plan(progress(position = 65L)),
        )
    }

    @Test
    fun `positions under one minute do not count`() {
        // Round 121: the recording floor moved 5 s → 60 s.
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(progress(position = 59L)),
        )
    }

    @Test
    fun `near-end unmarked position starts fresh`() {
        // The EOF completion write / keep-open pin stores ~duration without
        // the watched mark necessarily landing — never re-open onto the last
        // frame.
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(progress(position = 995L, suppressed = true)),
        )
    }

    @Test
    fun `resume pref off always starts at beginning`() {
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(progress(position = 500L), resumeEnabled = false),
        )
    }

    @Test
    fun `dust positions are not persisted`() {
        assertFalse(PlaybackResumePolicy.shouldPersist(15L, 1400L))
        assertFalse(PlaybackResumePolicy.shouldPersist(59L, 1400L))
    }

    @Test
    fun `real progress and near-completions persist`() {
        assertTrue(PlaybackResumePolicy.shouldPersist(60L, 1400L))
        assertTrue(PlaybackResumePolicy.shouldPersist(500L, 1400L))
        // 85%+ of a short episode always records — it feeds the auto-mark.
        assertTrue(PlaybackResumePolicy.shouldPersist(50L, 55L))
    }

    @Test
    fun `next episode offered at 95 percent`() {
        assertTrue(PlaybackResumePolicy.shouldOfferNextEpisode(950, 1000))
        assertTrue(PlaybackResumePolicy.shouldOfferNextEpisode(999, 1000))
        assertFalse(PlaybackResumePolicy.shouldOfferNextEpisode(949, 1000))
    }

    @Test
    fun `next episode never offered without a duration`() {
        assertFalse(PlaybackResumePolicy.shouldOfferNextEpisode(500, 0))
    }
}
