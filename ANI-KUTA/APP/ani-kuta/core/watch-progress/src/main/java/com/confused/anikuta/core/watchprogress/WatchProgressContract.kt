package com.confused.anikuta.core.watchprogress

/**
 * D-762 (round 127 — the tracking contract): the ONE `firstWatchedAt`
 * contract.
 *
 * ## The drift it removes
 *
 * Two writers, two meanings:
 *  - the CS stack wrote `firstWatchedAt` on the FIRST SAVE ("the user started
 *    watching this episode");
 *  - the store set it only on COMPLETION ("the user finished it").
 *
 * The CS value never actually landed (the store recomputes the field on every
 * write), so the app behaved like the store's rule while the CS code claimed
 * another one — a silent contract mismatch that would have surfaced as
 * "when did I first watch this?" drift.
 *
 * ## The contract
 *
 * `firstWatchedAt` = **the first time the episode was watched** — the first
 * save that carries a real position. Not the completion, not the last watch.
 * The completion has its own field (`completedAt`), and the last watch has
 * `lastWatchedAt`; `firstWatchedAt` is the FIRST one, and it never changes
 * once it is set.
 *
 * Pure JVM: the decision is unit-tested, and the store applies it.
 */
internal object WatchProgressContract {

    /**
     * The `firstWatchedAt` value to persist.
     *
     * @param existingFirstWatchedAt the stored value (null = never watched)
     * @param positionSeconds the position being written, in seconds (0 = not started)
     * @param now the current time, in epoch millis
     * @return the value to persist (null = the episode has not been started yet)
     */
    fun firstWatchedAt(
        existingFirstWatchedAt: Long?,
        positionSeconds: Long,
        now: Long,
    ): Long? = existingFirstWatchedAt ?: if (positionSeconds > 0L) now else null
}
