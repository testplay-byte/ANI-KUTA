package com.confused.anikuta.core.player

import com.confused.anikuta.core.watchprogress.WatchProgress

/**
 * Round 120 (D-746): the ONE resume / end-of-episode decision seam, shared by
 * both player stacks (MPV + CloudStream).
 *
 * WHY this exists: the resume logic used to live inline in the watch screen with
 * no watched-episode gate — a completed episode re-opened at its LAST SAVED
 * position (≈ the end, because `keep-open` pins time-pos at EOF and the dispose
 * save persists it). Centralizing it makes the contract testable and identical
 * on both stacks:
 *
 *  - watched episode           → start fresh at 0:00 (the user's explicit rule)
 *  - no progress / < 5 s saved → start fresh at 0:00
 *  - partial (≥ 5 s saved)     → resume EXACTLY where it was left
 *  - resume pref OFF           → always start fresh
 *
 * The end-of-episode trigger fraction lives here too so the MPV stack and the CS
 * stack offer the next episode at the same moment.
 */
object PlaybackResumePolicy {

    /** Saved positions below this are treated as "never really started". */
    const val MIN_RESUME_SECONDS: Long = 5L

    /** When to offer / auto-play the next episode (fraction of duration). */
    const val NEXT_EPISODE_TRIGGER_FRACTION: Float = 0.95f

    /** Up-next card countdown while auto-play is ON (seconds). */
    const val UP_NEXT_COUNTDOWN_SECONDS: Int = 10

    /** How long the "Resumed from mm:ss" affordance stays visible (ms). */
    const val RESUME_PILL_DURATION_MS: Long = 5_000L

    /** The resume decision for one episode. */
    sealed interface Decision {
        /** Start the episode from 0:00. */
        data object StartAtBeginning : Decision

        /** Resume at [positionSeconds] (an already-validated, non-trivial position). */
        data class ResumeAtPosition(val positionSeconds: Long) : Decision
    }

    /**
     * Plans the start position for an episode given its stored progress.
     *
     * @param stored the store row for THIS episode's key (null = never watched).
     * @param resumeEnabled the user's "resume from last position" preference.
     */
    fun plan(stored: WatchProgress?, resumeEnabled: Boolean = true): Decision {
        if (!resumeEnabled) return Decision.StartAtBeginning
        if (stored == null) return Decision.StartAtBeginning
        // Watched (auto-marked OR sticky user-marked, and not currently
        // un-marked-suppressed) → fresh start. `isWatched` encodes exactly that.
        if (stored.isWatched) return Decision.StartAtBeginning
        if (stored.position < MIN_RESUME_SECONDS) return Decision.StartAtBeginning
        return Decision.ResumeAtPosition(stored.position)
    }

    /**
     * Whether the player should surface the next-episode offer now. True once
     * the position crosses [NEXT_EPISODE_TRIGGER_FRACTION] of the duration.
     */
    fun shouldOfferNextEpisode(positionSeconds: Int, durationSeconds: Int): Boolean {
        if (durationSeconds <= 0) return false
        return positionSeconds.toFloat() >= durationSeconds * NEXT_EPISODE_TRIGGER_FRACTION
    }
}
