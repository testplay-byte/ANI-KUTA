package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-401 (round 28): unit tests for the PURE deletion-pipeline decisions —
 * [DeletionMatching.matchRemoval] (which entries a delete removes) and
 * [DeletionMatching.removalVerified] (whether a post-write re-read PROVES
 * the removal landed).
 *
 * These encode the exact failure modes of the round-28 device report:
 *  - the key mismatch that silently returned "idempotent success" while the
 *    entry stayed on disk (the number-reconciliation fallback now catches it);
 *  - the NULL re-read that counted as "verified" (now a hard failure).
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

    private fun dataJson(vararg episodes: DownloadedEpisodeInfo): ContentDataJson =
        ContentDataJson(
            mainId = "m1",
            contentId = "c1",
            title = "Series",
            episodes = episodes.toList(),
            createdAt = 1L,
            updatedAt = 1L,
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

    // ── removalVerified ───────────────────────────────────────────────────────

    @Test
    fun `null re-read is a FAILURE not a pass`() {
        // The round-27 null-pass: a dead write + a failed re-read both
        // counted as verified. Now null = not verified = the ladder retries.
        val removed = listOf(entry("k1", 1.0))
        assertFalse(DeletionMatching.removalVerified(null, removed))
    }

    @Test
    fun `empty removal with a readable file verifies`() {
        assertTrue(DeletionMatching.removalVerified(dataJson(entry("k2", 2.0)), emptyList()))
    }

    @Test
    fun `entry gone by key verifies`() {
        val removed = listOf(entry("k1", 1.0))
        assertTrue(DeletionMatching.removalVerified(dataJson(entry("k2", 2.0)), removed))
    }

    @Test
    fun `entry still present by key fails verification`() {
        val removed = listOf(entry("k1", 1.0))
        assertFalse(DeletionMatching.removalVerified(dataJson(entry("k1", 1.0), entry("k2", 2.0)), removed))
    }

    @Test
    fun `entry still present by number alone fails verification`() {
        // The write removed by key but a SAME-NUMBER entry (rebuilt key)
        // still occupies the slot — the episode's file family is still
        // represented, so this is NOT verified.
        val removed = listOf(entry("k1", 1.0))
        assertFalse(DeletionMatching.removalVerified(dataJson(entry("rebuilt-key", 1.0)), removed))
    }

    @Test
    fun `null reread with empty removal still fails`() {
        // Even a no-op delete must be able to READ the file to claim success.
        assertFalse(DeletionMatching.removalVerified(null, emptyList()))
    }
}
