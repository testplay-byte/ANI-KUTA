package com.confused.anikuta.core.player

import com.confused.anikuta.core.watchprogress.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 120 (D-746): locks the resume contract. Pure decision logic with no
 * Android dependencies — runs on the JVM test runner (CI's test gate).
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
    fun `un-marked (suppressed) partial episode still resumes`() {
        // The user un-marked it but the stored position is mid-episode — they
        // are re-watching from there, not from zero.
        val suppressed = progress(position = 400L, completed = false, suppressed = true)
        assertEquals(
            PlaybackResumePolicy.Decision.ResumeAtPosition(400L),
            PlaybackResumePolicy.plan(suppressed),
        )
    }

    @Test
    fun `partial episode resumes exactly where left`() {
        assertEquals(
            PlaybackResumePolicy.Decision.ResumeAtPosition(500L),
            PlaybackResumePolicy.plan(progress(position = 500L)),
        )
    }

    @Test
    fun `trivial saved position starts at beginning`() {
        assertEquals(
            PlaybackResumePolicy.Decision.StartAtBeginning,
            PlaybackResumePolicy.plan(progress(position = 4L)),
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
