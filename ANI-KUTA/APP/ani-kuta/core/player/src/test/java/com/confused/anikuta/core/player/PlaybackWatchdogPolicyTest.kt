package com.confused.anikuta.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-749 (round 122 — the MPV stall watchdog): the predicate's locks.
 *
 * The two false positives the old watchdog produced (the user pausing at the
 * start, and the episode ending) are locked here as "never fires", and the
 * real stall (a playing but frozen position) is locked as "fires".
 */
class PlaybackWatchdogPolicyTest {

    private fun evidence(
        hasEverPlayed: Boolean = true,
        isPlaying: Boolean = false,
        isUserPaused: Boolean = false,
        isBuffering: Boolean = false,
        isSwitching: Boolean = false,
        hasError: Boolean = false,
        durationMs: Int = 600_000,
        positionMs: Int = 120_000,
    ) = PlaybackWatchdogPolicy.Evidence(
        hasEverPlayed = hasEverPlayed,
        isPlaying = isPlaying,
        isUserPaused = isUserPaused,
        isBuffering = isBuffering,
        isSwitching = isSwitching,
        hasError = hasError,
        durationMs = durationMs,
        positionMs = positionMs,
    )

    // ── The arming conditions ────────────────────────────────────────────────

    @Test
    fun `a running video arms the watchdog`() {
        assertTrue(PlaybackWatchdogPolicy.shouldArm(evidence(isPlaying = true)))
    }

    @Test
    fun `a user pause never arms the watchdog`() {
        // The old false positive #1: the user paused at the start and the
        // watchdog called the server dead.
        assertFalse(PlaybackWatchdogPolicy.shouldArm(evidence(isUserPaused = true)))
    }

    @Test
    fun `the end of an episode never arms the watchdog`() {
        // The old false positive #2: keep-open parks MPV at the end.
        assertFalse(
            PlaybackWatchdogPolicy.shouldArm(
                evidence(durationMs = 600_000, positionMs = 600_000),
            ),
        )
    }

    @Test
    fun `the end tolerance covers the last moments of an episode`() {
        assertFalse(
            PlaybackWatchdogPolicy.shouldArm(
                evidence(durationMs = 600_000, positionMs = 599_000),
            ),
        )
    }

    @Test
    fun `a video that never started does not arm the watchdog`() {
        // The switching watchdog (30 s, "Video failed to load") owns that case.
        assertFalse(PlaybackWatchdogPolicy.shouldArm(evidence(hasEverPlayed = false)))
    }

    @Test
    fun `a switch in flight does not arm the watchdog`() {
        assertFalse(PlaybackWatchdogPolicy.shouldArm(evidence(isSwitching = true)))
    }

    @Test
    fun `an error already on screen does not arm the watchdog`() {
        assertFalse(PlaybackWatchdogPolicy.shouldArm(evidence(hasError = true)))
    }

    @Test
    fun `an unknown duration does not arm the watchdog`() {
        assertFalse(PlaybackWatchdogPolicy.shouldArm(evidence(durationMs = 0)))
    }

    // ── The firing conditions ────────────────────────────────────────────────

    @Test
    fun `a frozen position fires the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isPlaying = true, positionMs = 120_000)
        assertTrue(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `an advanced position does not fire the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isPlaying = true, positionMs = 135_000)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `a buffering stall with a frozen position fires the watchdog`() {
        val armed = evidence(isPlaying = true, isBuffering = true, positionMs = 120_000)
        val now = evidence(isPlaying = true, isBuffering = true, positionMs = 120_000)
        assertTrue(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `a buffering stall whose position advances does not fire the watchdog`() {
        val armed = evidence(isPlaying = true, isBuffering = true, positionMs = 120_000)
        val now = evidence(isPlaying = true, isBuffering = true, positionMs = 121_500)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `a pause DURING the window cancels the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isPlaying = false, isUserPaused = true, positionMs = 120_000)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `reaching the end DURING the window cancels the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isPlaying = false, durationMs = 120_000, positionMs = 120_000)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `an error raised DURING the window cancels the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isPlaying = false, hasError = true, positionMs = 120_000)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `a switch started DURING the window cancels the watchdog`() {
        val armed = evidence(isPlaying = true, positionMs = 120_000)
        val now = evidence(isSwitching = true, positionMs = 120_000)
        assertFalse(PlaybackWatchdogPolicy.shouldFire(armed, now))
    }

    @Test
    fun `the window budget is the old fifteen seconds`() {
        assertTrue(PlaybackWatchdogPolicy.STALL_WINDOW_MS == 15_000L)
    }
}
