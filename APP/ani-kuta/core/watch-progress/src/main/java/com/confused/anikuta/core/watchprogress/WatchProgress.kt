package com.confused.anikuta.core.watchprogress

/**
 * Watch progress for a single episode.
 *
 * @param episodeKey Temporary key: "<ecosystem>:<source_id|->:<external_id>"
 * @param position Position in seconds
 * @param duration Total duration in seconds
 * @param completed 1 if watched to completion, 0 otherwise
 * @param completedAt When completed (epoch millis), or null
 * @param lastWatchedAt When last watched (epoch millis)
 */
data class WatchProgress(
    val episodeKey: String,
    val position: Long,
    val duration: Long,
    val completed: Boolean,
    val completedAt: Long?,
    val lastWatchedAt: Long,
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
}
