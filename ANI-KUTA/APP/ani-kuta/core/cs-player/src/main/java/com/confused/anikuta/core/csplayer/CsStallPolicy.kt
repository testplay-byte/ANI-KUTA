package com.confused.anikuta.core.csplayer

/**
 * D-763 (round 128 — the CloudStream playback round): the STALL predicate.
 *
 * ## The gap it closes
 *
 * The CloudStream stack handled ERRORS (a dead link, a 403, a parse failure)
 * but not a link that simply never delivers: the player sits in BUFFERING
 * forever, the spinner keeps turning, and the user has no way out. The MPV
 * stack had a watchdog — but it was the over-eager one D-749 replaced (it
 * fired on a user pause and at the end of every episode).
 *
 * ## The rule
 *
 * A stall is EVIDENCE, not a timer alone:
 *  - the player has been BUFFERING for longer than the timeout, AND
 *  - the POSITION HAS NOT ADVANCED since the buffering started.
 *
 * A buffering stream that is still making progress is slow, not stalled — and
 * a stream that finished (or that the user paused) never reaches this check
 * at all, because the buffering window is closed when the state leaves
 * BUFFERING.
 *
 * Pure JVM: the predicate is unit-tested.
 */
object CsStallPolicy {

    /** How long the player may buffer without advancing before it counts as stalled. */
    const val STALL_TIMEOUT_MS = 30_000L

    /**
     * Whether the current buffering window is a stall.
     *
     * @param bufferingSinceMs when the buffering window opened (elapsed millis)
     * @param nowMs the current time (elapsed millis)
     * @param positionAtStartMs the position when the window opened
     * @param positionNowMs the current position
     * @param timeoutMs how long the buffer may last
     */
    fun isStalled(
        bufferingSinceMs: Long,
        nowMs: Long,
        positionAtStartMs: Long,
        positionNowMs: Long,
        timeoutMs: Long = STALL_TIMEOUT_MS,
    ): Boolean {
        if (bufferingSinceMs <= 0L) return false
        if (nowMs - bufferingSinceMs < timeoutMs) return false
        // The evidence: the position did not move.
        return positionNowMs <= positionAtStartMs
    }
}
