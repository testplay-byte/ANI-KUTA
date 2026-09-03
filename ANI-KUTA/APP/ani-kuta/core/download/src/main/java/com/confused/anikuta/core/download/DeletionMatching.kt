package com.confused.anikuta.core.download

/**
 * D-401 (round 28): the PURE decision logic of the episode-deletion
 * `.data.json` pipeline — which entries a delete removes, and whether a
 * post-write re-read PROVES the removal landed.
 *
 * Extracted from [DownloadStorageProvider.removeEpisodeFromDataJson] so the
 * two decisions that the round-28 device report hinged on are unit-tested
 * (core:download's first test source set) instead of only provable on a
 * device:
 *
 *  1. MATCHING — the round-27 ladder matched entries STRICTLY by
 *     `episodeKey`; a `.data.json` rebuilt by the scanner (which merges by
 *     `episodeNumber`) can hold a key that the DB row's delete key doesn't
 *     match. The old code then returned "idempotent success" WITHOUT
 *     touching the file — one of the two silent-success holes that left
 *     stale entries behind. [matchRemoval] now falls back to
 *     `episodeNumber` (key-drift reconciliation) and REPORTS which path
 *     matched, so the caller can log the reconciliation loudly.
 *
 *  2. STRICT VERIFICATION — the round-27 verify treated a NULL re-read as
 *     VERIFIED (`verifyExisting != null && …any{…}`), so a write that went
 *     to a dead URI + a re-read that failed both counted as success — the
 *     second silent-success hole. [removalVerified] requires the re-read to
 *     be NON-NULL and to contain NONE of the removed entries (by key OR by
 *     number).
 *
 * No Android, no SAF, no I/O — plain data-class decisions.
 */
object DeletionMatching {

    /**
     * The outcome of matching a delete request against a `.data.json`
     * episodes list.
     *
     * @property removed The entries the delete removes (empty = nothing in
     *   the list belongs to this episode — a genuine idempotent no-op, which
     *   the caller must still VERIFY against disk truth).
     * @property keyMatched True when at least one entry matched by
     *   `episodeKey` (the normal path).
     * @property numberReconciled True when NOTHING matched by key but the
     *   episodeNumber fallback matched — the key-drift case (a scanner
     *   rebuild changed the stored key). Logged at WARN by the caller: the
     *   DB row's key and the `.data.json` entry's key disagree.
     */
    data class RemovalMatch(
        val removed: List<DownloadedEpisodeInfo>,
        val keyMatched: Boolean,
        val numberReconciled: Boolean,
    )

    /**
     * Which entries does deleting `episodeKey` (episodeNumber as the
     * reconciliation fallback) remove from [episodes]?
     *
     * Match policy:
     *  1. entries with `episodeKey == episodeKey` (the normal path);
     *  2. if NONE matched and [episodeNumber] is known, entries with
     *     `episodeNumber == episodeNumber` (key-drift reconciliation — the
     *     scanner's `associateBy(episodeNumber)` rebuilds can rewrite keys);
     *  3. otherwise nothing (idempotent no-op — the caller still verifies).
     */
    fun matchRemoval(
        episodes: List<DownloadedEpisodeInfo>,
        episodeKey: String,
        episodeNumber: Double?,
    ): RemovalMatch {
        val byKey = episodes.filter { it.episodeKey == episodeKey }
        if (byKey.isNotEmpty()) return RemovalMatch(byKey, keyMatched = true, numberReconciled = false)
        val byNumber = episodeNumber?.let { number ->
            episodes.filter { it.episodeNumber == number }
        }.orEmpty()
        return RemovalMatch(
            removed = byNumber,
            keyMatched = false,
            numberReconciled = byNumber.isNotEmpty(),
        )
    }

    /**
     * Does the post-write re-read [reread] PROVE that [removed] is gone?
     *
     * STRICT (the round-28 fix): a NULL re-read is a FAILURE (the old
     * `verifyExisting != null && …` null-pass made a dead write look
     * verified). The re-read must be non-null and contain NONE of the
     * removed entries — matched by key OR by episodeNumber (either
     * identity still being present means the removal did not land).
     */
    fun removalVerified(
        reread: ContentDataJson?,
        removed: List<DownloadedEpisodeInfo>,
    ): Boolean {
        if (reread == null) return false
        if (removed.isEmpty()) return true
        return reread.episodes.none { existing ->
            removed.any { gone ->
                existing.episodeKey == gone.episodeKey ||
                    existing.episodeNumber == gone.episodeNumber
            }
        }
    }
}
