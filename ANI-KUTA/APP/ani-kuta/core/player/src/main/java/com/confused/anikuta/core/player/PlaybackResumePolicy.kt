package com.confused.anikuta.core.player

/**
 * D-761 (round 126 — the end-of-episode + resume contract): the PURE policies
 * behind "where does playback start?" and "is the episode over?".
 *
 * ## The resume contract (D-751, PB-4)
 *
 * The player used to seek to whatever position was stored — including the
 * position of an episode the user had ALREADY FINISHED, which lands the video
 * at its very end (the "the episode starts already over" bug). The contract now:
 *
 *  - an episode the user COMPLETED starts at **0** — and the old position is
 *    OFFERED (a short "Resume from mm:ss" the user can accept, which
 *    disappears on its own after a few seconds);
 *  - an episode whose stored position is at or beyond the watched threshold
 *    (the same threshold the auto-mark uses) also starts at 0 — the user has
 *    effectively seen it;
 *  - anything below the threshold resumes at the stored position, as before.
 *
 * ## The end-of-episode contract (D-752, PB-2)
 *
 * The MPV player never advanced to the next episode (the CloudStream stack
 * does). The next-episode ACTION now appears at 95 %/outro, and the
 * auto-advance follows the user's `autoPlayNext` preference.
 *
 * ## Units
 *
 * Everything in this file is MILLISECONDS — the unit the nav keys and the CS
 * player already use. The watch-progress STORE keeps seconds (its own legacy
 * contract); the conversion happens at the edge, in one place.
 */
object PlaybackResumePolicy {

    /** The fraction of an episode that counts as "watched" (the auto-mark's threshold). */
    const val DEFAULT_COMPLETION_THRESHOLD = 0.85f

    /** The fraction at which the next-episode action appears. */
    const val END_OF_EPISODE_THRESHOLD = 0.95f

    /** How long the "Resume from mm:ss" offer stays on screen. */
    const val RESUME_OFFER_MS = 5_000L

    /** Where playback starts. */
    enum class StartDecision {
        /** Start from the beginning. */
        START_FROM_BEGINNING,

        /** Seek to the stored position. */
        RESUME_AT_POSITION,
    }

    /**
     * The plan for one playback start.
     *
     * @param decision where playback starts
     * @param offerResume whether the "Resume from mm:ss" offer should be shown
     * @param resumePositionMs the offered position (0 when there is nothing to offer)
     */
    data class ResumePlan(
        val decision: StartDecision,
        val offerResume: Boolean,
        val resumePositionMs: Long,
    )

    /**
     * The plan for a video that is about to start.
     *
     * @param storedPositionMs the stored position, in ms (0 = never watched)
     * @param durationMs the media duration, in ms (0 = unknown)
     * @param isCompleted whether the episode is already marked watched
     * @param completionThreshold the fraction that counts as watched
     */
    fun plan(
        storedPositionMs: Long,
        durationMs: Long,
        isCompleted: Boolean,
        completionThreshold: Float = DEFAULT_COMPLETION_THRESHOLD,
    ): ResumePlan {
        if (storedPositionMs <= 0L) {
            return ResumePlan(StartDecision.START_FROM_BEGINNING, offerResume = false, resumePositionMs = 0L)
        }
        // D-751: a COMPLETED episode starts at 0 — but the old position is
        // still OFFERED (the user may want to re-watch that exact moment).
        if (isCompleted) {
            return ResumePlan(StartDecision.START_FROM_BEGINNING, offerResume = true, resumePositionMs = storedPositionMs)
        }
        // Effectively watched (at or beyond the threshold) → same treatment.
        if (durationMs > 0L && storedPositionMs >= durationMs * completionThreshold) {
            return ResumePlan(StartDecision.START_FROM_BEGINNING, offerResume = true, resumePositionMs = storedPositionMs)
        }
        return ResumePlan(StartDecision.RESUME_AT_POSITION, offerResume = false, resumePositionMs = storedPositionMs)
    }

    /**
     * Whether the end of the episode is near enough for the next-episode action
     * to appear.
     */
    fun shouldOfferNextEpisode(
        positionMs: Long,
        durationMs: Long,
        endThreshold: Float = END_OF_EPISODE_THRESHOLD,
    ): Boolean {
        if (durationMs <= 0L) return false
        return positionMs >= durationMs * endThreshold
    }

    /**
     * Milliseconds → the SECONDS MPV's `seek` command expects. The unit
     * contract in one place (PB-8: the two nav keys used to disagree — one
     * documented seconds, the other milliseconds — which would have been a
     * 1000x bug the moment a caller passed a real position).
     */
    fun toMpvSeconds(milliseconds: Long): Double = milliseconds / 1000.0

    /**
     * A `mm:ss` (or `h:mm:ss`) label for a position in ms.
     */
    fun formatTimestamp(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
