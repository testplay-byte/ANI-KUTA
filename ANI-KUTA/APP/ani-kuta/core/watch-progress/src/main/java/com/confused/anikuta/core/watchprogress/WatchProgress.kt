package com.confused.anikuta.core.watchprogress

/**
 * Watch progress for a single episode.
 *
 * Phase WP (PLAN §2.2): extended with `mainId`, `watchCount`, `firstWatchedAt`,
 * `autoMarkSuppressed`, `userMarkedWatched` to support the two-flag auto-mark
 * state machine (CF1) + backup-friendly keying.
 *
 * @param episodeKey The standardized episode key: `"${mainId}|${padded_5_digit}"`.
 * @param mainId The content's stable `main_id` (UUID). Null for legacy rows (pre-Phase-WP).
 * @param position Position in seconds.
 * @param duration Total duration in seconds.
 * @param completed 1 if watched to completion (auto OR manual), 0 otherwise.
 * @param completedAt When completed (epoch millis), or null.
 * @param lastWatchedAt When last watched (epoch millis).
 * @param watchCount How many times the user has completed this episode.
 * @param firstWatchedAt The first time the user watched this episode (epoch millis), or null.
 * @param autoMarkSuppressed CF1: true if the user manually un-marked → suppress the 85% auto-mark until next play.
 * @param userMarkedWatched CF1: true if the user explicitly marked watched (sticky).
 * @param playedTimeMs ROUND 127 (D-789): the CUMULATIVE time the user actually
 *   played this episode across ALL sessions (ms). Callers pass the total they
 *   know (persisted base + this session's accumulation); the store records it
 *   monotonic (max of stored vs incoming), so repeating saves never double-count.
 *   The auto-mark requires BOTH the fraction threshold AND this total reaching
 *   [MIN_PLAYED_FOR_WATCHED_MS] — the user's spec: watched = finished past the
 *   threshold AND actually played for more than one minute, TOTALLED over
 *   sessions (so a seek to the end after having genuinely watched the episode
 *   marks it; a fresh failed stream that plays ~2s never can).
 */
data class WatchProgress(
    val episodeKey: String,
    val mainId: String? = null,
    val position: Long,
    val duration: Long,
    val completed: Boolean,
    val completedAt: Long?,
    val lastWatchedAt: Long,
    val watchCount: Int = 0,
    val firstWatchedAt: Long? = null,
    val autoMarkSuppressed: Boolean = false,
    val userMarkedWatched: Boolean = false,
    val playedTimeMs: Long = 0,
) {
    companion object {
        /**
         * ROUND 127 (D-789): the minimum CUMULATIVE actually-played time (ms)
         * an episode needs before the fraction auto-mark may fire. One constant
         * for every gate (the store's auto-mark + the player-side save gates).
         */
        const val MIN_PLAYED_FOR_WATCHED_MS = 60_000L
    }
    /**
     * Progress as a fraction (0.0 to 1.0).
     */
    val progressFraction: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    /**
     * Whether the user has started watching this episode.
     */
    val hasStarted: Boolean get() = position > 0

    /**
     * CF1: the derived "is this episode watched?" value.
     *
     * `(completed AND NOT autoMarkSuppressed) OR userMarkedWatched`
     *
     * This correctly returns FALSE for a SUPPRESSED episode (completed may be true
     * but autoMarkSuppressed is true + userMarkedWatched is false). See PLAN §2.3.
     */
    val isWatched: Boolean
        get() = (completed && !autoMarkSuppressed) || userMarkedWatched
}
