package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-763 (round 128 — the CloudStream playback round): the stall predicate's
 * locks.
 *
 * The gap: the CS stack handled errors but not a link that never delivers —
 * the player buffered forever and the user had no way out.
 */
class CsStallPolicyTest {

    private val now = 100_000L

    @Test
    fun `a buffering stream that made no progress is a stall`() {
        assertTrue(
            CsStallPolicy.isStalled(
                bufferingSinceMs = now - 31_000L,
                nowMs = now,
                positionAtStartMs = 5_000L,
                positionNowMs = 5_000L,
            ),
        )
    }

    @Test
    fun `a buffering stream that is still progressing is not a stall`() {
        assertFalse(
            CsStallPolicy.isStalled(
                bufferingSinceMs = now - 31_000L,
                nowMs = now,
                positionAtStartMs = 5_000L,
                positionNowMs = 9_000L,
            ),
        )
    }

    @Test
    fun `a short buffering window is not a stall`() {
        assertFalse(
            CsStallPolicy.isStalled(
                bufferingSinceMs = now - 10_000L,
                nowMs = now,
                positionAtStartMs = 5_000L,
                positionNowMs = 5_000L,
            ),
        )
    }

    @Test
    fun `no buffering window is never a stall`() {
        assertFalse(
            CsStallPolicy.isStalled(
                bufferingSinceMs = 0L,
                nowMs = now,
                positionAtStartMs = 5_000L,
                positionNowMs = 5_000L,
            ),
        )
    }

    @Test
    fun `the timeout is thirty seconds`() {
        assertTrue(CsStallPolicy.STALL_TIMEOUT_MS == 30_000L)
    }
}
