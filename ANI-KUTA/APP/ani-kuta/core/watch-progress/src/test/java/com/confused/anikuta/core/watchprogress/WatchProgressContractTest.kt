package com.confused.anikuta.core.watchprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D-762 (round 127 — the tracking contract): the ONE `firstWatchedAt`
 * contract.
 *
 * The drift: the CS stack wrote it on the first save, the store on the
 * completion — and the CS value never landed (the store recomputes it), so
 * the two writers silently disagreed.
 */
class WatchProgressContractTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `the first watch sets the stamp`() {
        assertEquals(
            now,
            WatchProgressContract.firstWatchedAt(
                existingFirstWatchedAt = null,
                positionSeconds = 5L,
                now = now,
            ),
        )
    }

    @Test
    fun `an episode that has not started has no stamp`() {
        assertNull(
            WatchProgressContract.firstWatchedAt(
                existingFirstWatchedAt = null,
                positionSeconds = 0L,
                now = now,
            ),
        )
    }

    @Test
    fun `the stamp never changes once it is set`() {
        val first = now
        assertEquals(
            first,
            WatchProgressContract.firstWatchedAt(
                existingFirstWatchedAt = first,
                positionSeconds = 900L,
                now = now + 60_000L,
            ),
        )
    }

    @Test
    fun `a later watch does not move the stamp`() {
        // The second watch of an episode must NOT re-stamp it — that is the
        // whole point of the field.
        val first = now
        val later = WatchProgressContract.firstWatchedAt(
            existingFirstWatchedAt = first,
            positionSeconds = 12L,
            now = now + 999_000L,
        )
        assertEquals(first, later)
    }
}
