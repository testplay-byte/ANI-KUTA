package com.confused.anikuta.core.player

/**
 * The pure policies behind "where does playback start?" and "is the episode over?".
 *
 * ## The resume contract
 *
 *  - a COMPLETED episode starts at **0** - never at the stored end position,
 *    and with NO "Resume from mm:ss" offer;
 *  - a partially watched episode resumes at its stored position;
 *  - a never-watched episode starts at 0.
 *
 * ## The end-of-episode contract
 *
 * The next-episode action appears at 95 %/outro. Auto-advance follows the
 * user's `autoPlayNext` preference.
 *
 * ## Units
 *
 * Everything in this file is MILLISECONDS - the unit the nav keys and the CS
 * player already use. The watch-progress STORE keeps seconds (its own legacy
 * contract); the conversion happens at the edge, in one place.
 */
object PlaybackResumePolicy {

    /** The fraction at which the next-episode action appears. */
    const val END_OF_EPISODE_THRESHOLD = 0.95f

    /** How long the one-shot action banner stays on screen. */
    const val ACTION_BANNER_MS = 5_000L

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
     * @param resumePositionMs the seek position (0 when starting from the beginning)
     */
    data class ResumePlan(
        val decision: StartDecision,
        val resumePositionMs: Long,
    )

    /**
     * The plan for a video that is about to start.
     *
     * @param storedPositionMs the stored position, in ms (0 = never watched)
     * @param isCompleted whether the episode is already marked watched
     */
    fun plan(
        storedPositionMs: Long,
        isCompleted: Boolean,
    ): ResumePlan {
        if (storedPositionMs <= 0L || isCompleted) {
            return ResumePlan(StartDecision.START_FROM_BEGINNING, resumePositionMs = 0L)
        }
        return ResumePlan(StartDecision.RESUME_AT_POSITION, resumePositionMs = storedPositionMs)
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
     * Milliseconds -> the SECONDS MPV's `seek` command expects. The unit
     * contract in one place (PB-8: the two nav keys used to disagree - one
     * documented seconds, the other milliseconds - which would have been a
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
