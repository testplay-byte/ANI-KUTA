package com.confused.anikuta.core.download

/**
 * D-401 (round 28) → D-404 (round 29): the PURE MATCHING logic of the
 * episode-deletion pipeline — which entries a delete targets.
 *
 * Extracted from [DownloadStorageProvider] so the decision the round-28
 * device report hinged on is unit-tested (core:download's first test source
 * set) instead of only provable on a device:
 *
 *  - MATCHING — the round-27 ladder matched entries STRICTLY by
 *    `episodeKey`; a `.data.json` rebuilt by the scanner (which merges by
 *    `episodeNumber`) can hold a key that the DB row's delete key doesn't
 *    match. The old code then returned "idempotent success" WITHOUT
 *    touching the file — one of the two silent-success holes that left
 *    stale entries behind. [matchRemoval] falls back to `episodeNumber`
 *    (key-drift reconciliation) and REPORTS which path matched, so the
 *    caller can log the reconciliation loudly.
 *
 * D-404 note: the round-28 `removalVerified` (the strict post-write check)
 * is SUPERSEDED by [DataJsonRepair.episodesEqual] — the exact-set equality
 * the verified rewrite ladder uses — and was removed. The manager still
 * uses [matchRemoval] for Phase-2a entry capture (selecting the URI-deletion
 * targets).
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
}
