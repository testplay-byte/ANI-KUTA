package com.confused.anikuta.core.player

/**
 * D-749 (round 122 — the MPV stall watchdog): the PURE predicate that decides
 * whether the player is REALLY stuck.
 *
 * ## The bug this replaces
 *
 * The old fatal-error watchdog armed on: `duration > 0 && !isPlaying && no
 * error && not switching && (position == 0 || position >= duration - 2)`, and
 * fired 15 s later with "This server is not responding". It knew nothing about
 * WHY playback had stopped, so two completely normal situations produced the
 * error:
 *  - the user paused at the start (position 0) → "the server is not
 *    responding" while the video was perfectly fine;
 *  - the episode ENDED (`keep-open=yes` parks MPV at the end with the position
 *    at `duration`) → the same error at the end of every episode.
 *
 * ## The rule now (D-749)
 *
 * The watchdog fires only on EVIDENCE of a real stall:
 *  - playback HAS started (a video that never loaded is the SWITCHING
 *    watchdog's job — 30 s, "Video failed to load" — not this one);
 *  - the USER has not paused (a pause is a pause, not a stall);
 *  - EOF has not been reached (the end of an episode is not a stall);
 *  - nothing else claimed the failure (an error is already shown, or a
 *    quality/episode switch is in flight);
 *  - and the POSITION DID NOT ADVANCE across the whole sample window.
 *
 * The last clause is the actual evidence: a playing-but-frozen position is the
 * classic server-death signature, and it is exactly what the old watchdog
 * could not see (it required `!isPlaying`).
 *
 * Pure JVM by design: the policy is a value-in/value-out predicate, so it can
 * be unit-tested without a player, a device, or a coroutine.
 */
object PlaybackWatchdogPolicy {

    /**
     * How long the position must stay frozen before it counts as a stall.
     * The old watchdog's budget, kept as-is (the user's "15 seconds is
     * enough").
     */
    const val STALL_WINDOW_MS = 15_000L

    /**
     * The tolerance for "the episode reached its end": MPV parks the position
     * a hair before the exact duration, so the last moments of an episode
     * count as the end.
     */
    const val END_TOLERANCE_MS = 2_000

    /**
     * The evidence the policy judges. Everything the watchdog needs, and
     * nothing it does not.
     */
    data class Evidence(
        /** True once playback has actually started (D-749's "playback has started"). */
        val hasEverPlayed: Boolean,
        /** The player's current playing flag. */
        val isPlaying: Boolean,
        /** True when the pause came from the USER (the play/pause button, the app going to the background). */
        val isUserPaused: Boolean,
        /** True while the player is buffering. */
        val isBuffering: Boolean,
        /** True while a quality/episode switch is in flight. */
        val isSwitching: Boolean,
        /** True when an error is already on screen. */
        val hasError: Boolean,
        /** The media duration in ms (0 = unknown). */
        val durationMs: Int,
        /** The current position in ms. */
        val positionMs: Int,
    ) {
        /** True when playback has reached (or is within the tolerance of) the end. */
        val isAtEnd: Boolean
            get() = durationMs > 0 && positionMs >= durationMs - END_TOLERANCE_MS
    }

    /**
     * Whether the watchdog should ARM at this moment — i.e. whether the state
     * is one in which a frozen position would mean a real stall.
     *
     * Note what is NOT here: the position itself. Whether the position is
     * frozen is decided by comparing the armed evidence with the evidence
     * after the window ([shouldFire]); arming is a statement about the
     * SURROUNDINGS, not about the position.
     */
    fun shouldArm(e: Evidence): Boolean =
        e.hasEverPlayed &&      // playback started — a never-loaded video is the switching watchdog's job
        !e.isUserPaused &&      // the user paused — that is not a stall
        !e.isAtEnd &&           // the episode ended — that is not a stall
        !e.isSwitching &&       // a switch is in flight — the switching watchdog owns that window
        !e.hasError &&          // an error is already shown — do not stack a second one
        e.durationMs > 0        // no duration = nothing loaded yet

    /**
     * Whether the evidence collected AFTER the window proves a real stall.
     *
     * @param armed the evidence at the moment the watchdog armed
     * @param now the evidence after [STALL_WINDOW_MS]
     */
    fun shouldFire(armed: Evidence, now: Evidence): Boolean {
        // The surroundings must still hold: a pause, an EOF, an error or a
        // switch that happened DURING the window means this was never a stall.
        if (!shouldArm(now)) return false
        // The evidence itself: the position must NOT have advanced. A playing
        // but frozen position is the server-death signature; a position that
        // moved is playback working.
        if (now.positionMs > armed.positionMs) return false
        return true
    }
}
