package com.confused.anikuta.core.activitytracker

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The internal tracking system (D-045).
 *
 * Records everything the user does — watch events, searches, downloads, library changes,
 * ratings, etc. Data is stored in `activity_event` table and used for the user's own stats
 * (watch time, peak hours, most-watched, etc.).
 *
 * Write batching (I11 fix): events are batched in memory and flushed every 30 seconds
 * or when the batch reaches 50 events, whichever comes first. This reduces DB load
 * and flash wear on low-end devices.
 *
 * Retention: 365 days default, unlimited option (user preference — D-039).
 * Pruning: handled by [ActivityPruneWorker] (WorkManager daily job — Phase 4).
 *
 * Stats calculation: DEFERRED to Phase 6 (I10 fix — Ponytail: no premature abstraction).
 *
 * CORE_RULES §20: All operations are logged via Logger with tag "Anikuta:Core:ActivityTracker".
 */
class ActivityTracker(
    private val database: AnikutaDatabase,
    private val sessionId: String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val batchQueue = ConcurrentLinkedQueue<ActivityEvent>()
    private val batchSize = 50
    private val flushIntervalMs = 30_000L // 30 seconds

    private var lastFlushTime = System.currentTimeMillis()

    /**
     * Track a single event. The event is added to the batch queue and will be
     * flushed to the database when the batch is full or the flush interval elapses.
     *
     * This is non-blocking — the event is queued and the method returns immediately.
     */
    fun track(event: ActivityEvent) {
        batchQueue.add(event)
        Logger.v("Anikuta:Core:ActivityTracker") { "Tracked: ${event.eventType} (batch: ${batchQueue.size})" }

        if (batchQueue.size >= batchSize || System.currentTimeMillis() - lastFlushTime >= flushIntervalMs) {
            flush()
        }
    }

    /**
     * Flush all pending events to the database immediately.
     * Called when the batch is full, the flush interval elapses, or the app is paused.
     */
    fun flush() {
        if (batchQueue.isEmpty()) return

        val events = mutableListOf<ActivityEvent>()
        while (batchQueue.isNotEmpty()) {
            events.add(batchQueue.poll())
        }

        if (events.isEmpty()) return

        lastFlushTime = System.currentTimeMillis()
        Logger.d("Anikuta:Core:ActivityTracker") { "Flushing ${events.size} events to DB" }

        scope.launch {
            try {
                // Insert events one by one (simple, reliable — batching is at the memory queue level)
                events.forEach { event ->
                    database.trackingQueries.insertEvent(
                        event.eventType.value,
                        event.contentKey,
                        event.episodeKey,
                        event.sessionId,
                        event.route,
                        event.contentType,
                        event.durationMs,
                        event.payload,
                        event.timestamp,
                    )
                }
                Logger.d("Anikuta:Core:ActivityTracker") { "Flushed ${events.size} events successfully" }
            } catch (e: Exception) {
                Logger.e("Anikuta:Core:ActivityTracker", e) { "Failed to flush events: ${e.message}" }
                // Re-queue the events for the next flush attempt
                batchQueue.addAll(events)
            }
        }
    }

    /**
     * Prune events older than the given timestamp (for retention).
     * Called by ActivityPruneWorker (Phase 4 — WorkManager daily job).
     */
    suspend fun pruneOldEvents(cutoffTimestamp: Long) {
        Logger.i("Anikuta:Core:ActivityTracker") { "Pruning events older than $cutoffTimestamp" }
        database.trackingQueries.pruneOldEvents(cutoffTimestamp)
        Logger.i("Anikuta:Core:ActivityTracker") { "Prune complete" }
    }
}
