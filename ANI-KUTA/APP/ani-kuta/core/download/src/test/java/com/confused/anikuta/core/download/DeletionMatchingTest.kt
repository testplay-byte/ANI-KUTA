package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-401 (round 28) → D-404 (round 29): unit tests for the PURE
 * matching decision — [DeletionMatching.matchRemoval].
 *
 * These encode the exact failure modes of the round-28 device report:
 *  - the key mismatch that silently returned "idempotent success" while the
 *    entry stayed on disk (the number-reconciliation fallback now catches it);
 *
 * D-404: the `removalVerified` tests moved to [DataJsonRepairTest] — the
 * strict post-write verification is now the exact-set equality
 * ([DataJsonRepair.episodesEqual]) of the verified rewrite ladder.
 */
class DeletionMatchingTest {

    private fun entry(
        key: String,
        number: Double,
        videoUri: String? = "content://video/$key",
    ): DownloadedEpisodeInfo = DownloadedEpisodeInfo(
        episodeKey = key,
        episodeNumber = number,
        episodeUrl = "https://example.com/$key",
        videoUri = videoUri,
        downloadedAt = 1_700_000_000_000L,
    )

    // ── matchRemoval ──────────────────────────────────────────────────────────

    @Test
    fun `key match removes the entry`() {
        val list = listOf(entry("k1", 1.0), entry("k2", 2.0))
        val match = DeletionMatching.matchRemoval(list, "k1", 1.0)
        assertEquals(listOf("k1"), match.removed.map { it.episodeKey })
        assertTrue(match.keyMatched)
        assertFalse(match.numberReconciled)
    }

    @Test
    fun `key drift falls back to episodeNumber and reports the reconciliation`() {
        // The DB row says k1/ep1, but a scanner rebuild stored the ep-1 entry
        // under a different key — the round-27 code returned "idempotent
        // success" here WITHOUT touching the file (the stale-entry bug).
        val list = listOf(entry("rebuilt-key", 1.0), entry("k2", 2.0))
        val match = DeletionMatching.matchRemoval(list, "k1", 1.0)
        assertEquals(listOf("rebuilt-key"), match.removed.map { it.episodeKey })
        assertFalse(match.keyMatched)
        assertTrue(match.numberReconciled)
    }

    @Test
    fun `key drift without a known number is a true no-op`() {
        val list = listOf(entry("k2", 2.0))
        val match = DeletionMatching.matchRemoval(list, "k1", null)
        assertTrue(match.removed.isEmpty())
        assertFalse(match.keyMatched)
        assertFalse(match.numberReconciled)
    }

    @Test
    fun `nothing matches at all is a true no-op`() {
        val list = listOf(entry("k2", 2.0))
        val match = DeletionMatching.matchRemoval(list, "k1", 1.5)
        assertTrue(match.removed.isEmpty())
    }

    @Test
    fun `fractional episode numbers match exactly`() {
        val list = listOf(entry("k1_5", 1.5), entry("k2", 2.0))
        val match = DeletionMatching.matchRemoval(list, "missing-key", 1.5)
        assertEquals(listOf("k1_5"), match.removed.map { it.episodeKey })
        assertTrue(match.numberReconciled)
    }
}
