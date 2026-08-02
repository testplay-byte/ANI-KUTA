package com.confused.anikuta.core.activitytracker

/**
 * A single activity event recorded by the internal tracking system.
 *
 * @param eventType What happened (watch, search, download, etc.)
 * @param contentKey The content involved (nullable for non-content events like APP_OPEN)
 * @param episodeKey The episode involved (nullable for non-episode events)
 * @param sessionId App session ID (UUID, new per process restart — M9 fix)
 * @param route Screen route when the event occurred
 * @param contentType VIDEO, IMAGE, TEXT
 * @param durationMs Event duration in millis (e.g. watch time for WATCH_PAUSE)
 * @param payload JSON blob for extra data (search query, rating value, etc.)
 * @param timestamp Epoch millis when the event occurred
 */
data class ActivityEvent(
    val eventType: ActivityEventType,
    val contentKey: String? = null,
    val episodeKey: String? = null,
    val sessionId: String,
    val route: String? = null,
    val contentType: String? = null,
    val durationMs: Long? = null,
    val payload: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
