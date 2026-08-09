package com.confused.anikuta.feature.debugbubble.data

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks SQLDelight database writes for the debug bubble's "DB Activity" view
 * (inside the Network tab).
 *
 * **How it's wired:** [DebugSqlDriverWrapper] wraps the app's [SqlDriver]
 * (via the `wrapDebugSqlDriver` function in `:app/src/debug/DebugInit.kt`).
 * Every `execute()` call that looks like a write (INSERT / UPDATE / DELETE /
 * REPLACE) is recorded here via [recordWrite]. Reads (`executeQuery`) are NOT
 * recorded.
 *
 * **What it tracks:**
 * - Total write count (atomic).
 * - Per-operation counts (insert / update / delete / other-write).
 * - Per-table counts (which tables are being written to most).
 * - Per-second time-series (writes/sec for the last 5 min) — gap-filled so
 *   the chart slides forward even when the DB is idle (mirrors
 *   [DebugNetworkStats]'s `advanceToNow` approach).
 * - Recent events ring buffer (last 50 writes with operation + table +
 *   truncated SQL + timestamp).
 *
 * **Thread safety:** atomic counters for the hot path; `synchronized(lock)`
 * for the deque + map + ring buffer (same pattern as [DebugNetworkStats]).
 *
 * **Zero release overhead:** this class is only on the classpath in debug
 * builds (`debugImplementation(project(":feature:debug-bubble"))`). In
 * release builds, the `wrapDebugSqlDriver` stub is an identity function —
 * this class is never instantiated.
 */
class DebugDbStats {

    private val totalWrites = AtomicLong(0)
    private val insertCount = AtomicLong(0)
    private val updateCount = AtomicLong(0)
    private val deleteCount = AtomicLong(0)
    private val otherWriteCount = AtomicLong(0)

    private val tableCounts = mutableMapOf<String, Int>()
    private val recentEvents = ArrayDeque<DbWriteEvent>()
    private val lock = Any()
    private val maxRecent = 50

    // Time-series: per-second buckets for the last 5 minutes (300 buckets).
    // Each bucket stores the write count for that second.
    private val timeSeries = ArrayDeque<DbTimeSeriesBucket>()
    private val maxTimeSeriesBuckets = 300  // 5 minutes at 1-second resolution

    /**
     * Record a single write. Called from [DebugSqlDriverWrapper.execute]
     * for every SQL statement that looks like a write.
     *
     * @param operation One of "INSERT", "UPDATE", "DELETE", "REPLACE",
     *     "UPSERT", or another write keyword (uppercase). Parsed from the
     *     SQL string by the wrapper.
     * @param table The affected table name (parsed from the SQL string).
     *     Empty string if parsing failed (still counted in totals).
     * @param sql The raw SQL string (truncated to 200 chars for the recent-
     *     events list).
     */
    fun recordWrite(operation: String, table: String, sql: String) {
        totalWrites.incrementAndGet()
        val op = operation.uppercase()
        when {
            op.startsWith("INSERT") || op.startsWith("REPLACE") -> insertCount.incrementAndGet()
            op.startsWith("UPDATE") -> updateCount.incrementAndGet()
            op.startsWith("DELETE") -> deleteCount.incrementAndGet()
            else -> otherWriteCount.incrementAndGet()
        }
        val now = System.currentTimeMillis()
        val truncatedSql = if (sql.length > 200) sql.take(200) + "…" else sql
        synchronized(lock) {
            if (table.isNotEmpty()) {
                tableCounts[table] = (tableCounts[table] ?: 0) + 1
            }
            recordTimeSeries(now)
            if (recentEvents.size >= maxRecent) recentEvents.removeFirst()
            recentEvents.addLast(
                DbWriteEvent(
                    operation = op,
                    table = table,
                    sql = truncatedSql,
                    timestamp = now,
                ),
            )
        }
    }

    /**
     * Snapshot the current stats for the UI. Also advances the time-series
     * to "now" (gap-filling zero-buckets for idle seconds) so the chart
     * slides forward on every poll — matching [DebugNetworkStats.snapshot].
     */
    fun snapshot(): DbSnapshot = synchronized(lock) {
        // Prune old time-series buckets (older than 5 minutes).
        val cutoff = System.currentTimeMillis() - 300_000
        while (timeSeries.isNotEmpty() && timeSeries.first().timestamp < cutoff) {
            timeSeries.removeFirst()
        }
        // Gap-fill: advance to "now" so the chart slides forward.
        advanceToNow()
        DbSnapshot(
            totalWrites = totalWrites.get(),
            insertCount = insertCount.get(),
            updateCount = updateCount.get(),
            deleteCount = deleteCount.get(),
            otherWriteCount = otherWriteCount.get(),
            tableCounts = tableCounts.toMap(),
            recentEvents = recentEvents.toList(),
            timeSeries = timeSeries.toList(),
        )
    }

    /** Reset all counters + buffers. Called from the "Clear" button. */
    fun clear() {
        totalWrites.set(0)
        insertCount.set(0)
        updateCount.set(0)
        deleteCount.set(0)
        otherWriteCount.set(0)
        synchronized(lock) {
            tableCounts.clear()
            recentEvents.clear()
            timeSeries.clear()
        }
    }

    /** Record a write in the per-second time-series bucket. */
    private fun recordTimeSeries(timestamp: Long) {
        val secondBucket = timestamp / 1000 * 1000  // round down to the second
        if (timeSeries.isNotEmpty() && timeSeries.last().timestamp == secondBucket) {
            // Same second — update the existing bucket.
            val last = timeSeries.removeLast()
            timeSeries.addLast(last.copy(writeCount = last.writeCount + 1))
        } else {
            // New second — add a new bucket.
            timeSeries.addLast(DbTimeSeriesBucket(timestamp = secondBucket, writeCount = 1))
        }
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
    }

    /**
     * Advance the time-series to the current second, inserting zero-valued
     * buckets for every elapsed second with no writes. This makes the chart
     * slide forward over time even when the DB is idle.
     *
     * Must be called inside `synchronized(lock)`.
     */
    private fun advanceToNow() {
        val nowSecond = System.currentTimeMillis() / 1000 * 1000
        if (timeSeries.isEmpty()) {
            // Seed a baseline zero-bucket so the chart has something to draw.
            timeSeries.addLast(DbTimeSeriesBucket(timestamp = nowSecond, writeCount = 0))
            return
        }
        var last = timeSeries.last().timestamp
        while (last < nowSecond) {
            last += 1000
            timeSeries.addLast(DbTimeSeriesBucket(timestamp = last, writeCount = 0))
        }
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
    }

    /** A single DB write event, shown in the recent-events list. */
    data class DbWriteEvent(
        val operation: String,   // "INSERT", "UPDATE", "DELETE", etc.
        val table: String,       // affected table name (may be empty)
        val sql: String,         // truncated to 200 chars
        val timestamp: Long,     // epoch millis
    )

    /** A per-second time-series bucket for the 5-minute writes/sec chart. */
    data class DbTimeSeriesBucket(
        val timestamp: Long,   // epoch millis, rounded to the second
        val writeCount: Int,
    )

    /** Immutable snapshot of all DB stats for the UI. */
    data class DbSnapshot(
        val totalWrites: Long,
        val insertCount: Long,
        val updateCount: Long,
        val deleteCount: Long,
        val otherWriteCount: Long,
        val tableCounts: Map<String, Int>,
        val recentEvents: List<DbWriteEvent>,
        val timeSeries: List<DbTimeSeriesBucket> = emptyList(),
    ) {
        companion object {
            val EMPTY = DbSnapshot(0, 0, 0, 0, 0, emptyMap(), emptyList(), emptyList())
        }
    }
}
