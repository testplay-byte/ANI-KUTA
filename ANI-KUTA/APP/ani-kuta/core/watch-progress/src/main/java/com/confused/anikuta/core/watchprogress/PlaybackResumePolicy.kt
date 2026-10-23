package com.confused.anikuta.core.watchprogress

/**
 * Round 120 (D-746), reworked round 121: the ONE resume / end-of-episode
 * decision seam, shared by both player stacks (MPV + CloudStream) and the
 * details page.
 *
 * Lives in :core:watch-progress (moved from :core:player in round 121) so
 * every consumer — the MPV watch screen, the CS watch screen, and any future
 * surface — reaches it through the module they already depend on for
 * [WatchProgress].
 *
 * THE CONTRACT (the round-121 device round's corrections folded in):
 *
 *  - resume pref OFF                → always start fresh
 *  - no stored progress             → start fresh
 *  - watched episode                → start fresh (never resume at the end)
 *  - saved < 60 s                   → start fresh (under a minute "wasn't
 *    really started" — nothing under the minute mark counts as progress at
 *    all, per the round-121 order)
 *  - saved within 15 s of the end   → start fresh (the EOF completion write
 *    and keep-open's pinned time-pos both store ~duration; a re-open must
 *    never seek to "the very last point")
 *  - otherwise                      → resume 15 s BEFORE the saved position,
 *    so the user re-lands with context instead of mid-scene
 *
 * The end-of-episode trigger fraction lives here too so both stacks offer
 * the next episode at the same moment.
 */
object PlaybackResumePolicy {

    /**
     * Saved positions below this are treated as "never really started" — and
     * (via [shouldPersist]) are not even WRITTEN. Round 121: 5 s → 60 s by
     * the user's order ("if the user has watched an episode less than one
     * minute, then it won't even be recorded").
     */
    const val MIN_RESUME_SECONDS: Long = 60L

    /**
     * Resume this many seconds BEFORE the saved position — the user re-lands
     * with a little context ("resume from 15 seconds before the time").
     */
    const val RESUME_REWIND_SECONDS: Long = 15L

    /**
     * A saved position within this of the (known) duration counts as
     * completed — reopen fresh instead of seeking to the last frame. Covers
     * the EOF write (pos = duration) and keep-open pinning time-pos at EOF.
     */
    const val NEAR_END_SECONDS: Long = 15L

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
        // Near the end = completed, even when the watched mark didn't land
        // (suppressed) — never re-open onto the last frame.
        val duration = stored.duration
        if (duration > 0 && stored.position >= duration - NEAR_END_SECONDS) {
            return Decision.StartAtBeginning
        }
        val target = (stored.position - RESUME_REWIND_SECONDS).coerceAtLeast(0L)
        return Decision.ResumeAtPosition(target)
    }

    /**
     * Round 121: whether a progress row is worth PERSISTING at all. Positions
     * under a minute that are nowhere near completion are dust — the round's
     * order is that they "won't even be recorded". A near-complete position
     * always persists (it feeds the 85% auto-mark + the completion state).
     */
    fun shouldPersist(positionSeconds: Long, durationSeconds: Long): Boolean {
        if (positionSeconds >= MIN_RESUME_SECONDS) return true
        if (durationSeconds <= 0) return false
        return positionSeconds.toFloat() >= durationSeconds * 0.85f
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
