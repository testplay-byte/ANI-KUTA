package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.testapi.ActivityEventSummary

/**
 * Provides recent activity-tracker events (D-201 reuse).
 *
 * Queries the `activity_event` table directly via SQLDelight's generated `trackingQueries`
 * (defined in `core/database/src/main/sqldelight/.../tracking.sq`). The [ActivityTracker]
 * class itself is write-only (batches in a queue), so for reads we go straight to the DB.
 *
 * Columns: id, event_type, content_key, episode_key, session_id, route, content_type,
 * duration_ms, payload, timestamp.
 */
class ActivityLogsProvider(
    private val database: AnikutaDatabase,
) {
    fun recent(lines: Int, eventType: String?): List<ActivityEventSummary> {
        val queries = database.trackingQueries
        val rows = if (eventType.isNullOrBlank()) {
            queries.getRecentEvents(lines.toLong()).executeAsList()
        } else {
            queries.getEventsByType(eventType, lines.toLong()).executeAsList()
        }
        return rows.map { e ->
            ActivityEventSummary(
                id = e.id,
                timestamp = e.timestamp,
                eventType = e.event_type,
                contentKey = e.content_key,
                episodeKey = e.episode_key,
                route = e.route,
                durationMs = e.duration_ms,
                sessionId = e.session_id,
            )
        }
    }
}
