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
) {
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
