package com.confused.anikuta.feature.debugbubble.data

import java.util.concurrent.atomic.AtomicLong
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks SQLDelight database operations (both reads AND writes) for the debug
 * bubble's "DB Activity" view (inside the Network tab).
 *
 * **How it's wired:** [DebugSqlDriverWrapper] wraps the app's [SqlDriver]
 * (via the `wrapDebugSqlDriver` function in `:app/src/debug/DebugInit.kt`).
 * Every `execute()` call (INSERT / UPDATE / DELETE / REPLACE) is recorded via
 * [recordWrite]. Every `executeQuery()` call (SELECT) is recorded via
 * [recordRead].
 *
 * **What it tracks:**
 * - Total read count (SELECT queries) — atomic.
 * - Total write count (INSERT / UPDATE / DELETE / REPLACE) — atomic.
 * - Per-operation breakdown: insert / update / delete / other-write.
 * - Per-table read counts + per-table write counts.
 * - Per-second time-series (reads/sec + writes/sec for the last 5 min) —
 *   gap-filled so the chart slides forward even when the DB is idle.
 * - Recent events ring buffer (last 200 events — reads + writes interleaved,
 *   each with operation + table + truncated SQL + timestamp).
 *
 * **Thread safety:** atomic counters for the hot path; `synchronized(lock)`
 * for the deque + maps + ring buffer (same pattern as [DebugNetworkStats]).
 *
 * **Zero release overhead:** this class is only on the classpath in debug
 * builds (`debugImplementation(project(":feature:debug-bubble"))`). In
 * release builds, the `wrapDebugSqlDriver` stub is an identity function —
 * this class is never instantiated.
 */
class DebugDbStats {

    // ── Write counters ──
    private val totalWrites = AtomicLong(0)
    private val insertCount = AtomicLong(0)
    private val updateCount = AtomicLong(0)
    private val deleteCount = AtomicLong(0)
    private val otherWriteCount = AtomicLong(0)

    // ── Read counters ──
    private val totalReads = AtomicLong(0)

    // ── Per-table breakdowns (separate reads vs writes) ──
    private val writeTableCounts = mutableMapOf<String, Int>()
    private val readTableCounts = mutableMapOf<String, Int>()

    // ── Recent events (reads + writes interleaved, capped at 200) ──
    private val recentEvents = ArrayDeque<DbEvent>()
    private val lock = Any()
    private val maxRecent = 200

    // ── Time-series: per-second buckets for the last 5 minutes (300 buckets) ──
    // Each bucket stores both readCount + writeCount for that second.
    private val timeSeries = ArrayDeque<DbTimeSeriesBucket>()
    private val maxTimeSeriesBuckets = 300  // 5 minutes at 1-second resolution

    /**
     * Record a single write. Called from [DebugSqlDriverWrapper.execute]
     * for every SQL statement that looks like a write (INSERT / UPDATE /
     * DELETE / REPLACE).
     *
     * @param operation One of "INSERT", "UPDATE", "DELETE", "REPLACE",
     *     "UPSERT", or another write keyword (uppercase).
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
                writeTableCounts[table] = (writeTableCounts[table] ?: 0) + 1
            }
            recordTimeSeries(now, isWrite = true)
            if (recentEvents.size >= maxRecent) recentEvents.removeFirst()
            recentEvents.addLast(
                DbEvent(
                    operation = op,
                    table = table,
                    sql = truncatedSql,
                    timestamp = now,
                    isRead = false,
                ),
            )
        }
    }

    /**
     * Record a single read (SELECT). Called from
     * [DebugSqlDriverWrapper.executeQuery] for every SQL query that looks
     * like a SELECT.
     *
     * @param table The queried table name (parsed from the SQL string).
     *     Empty string if parsing failed (still counted in totals).
     * @param sql The raw SQL string (truncated to 200 chars for the recent-
     *     events list).
     */
    fun recordRead(table: String, sql: String) {
        totalReads.incrementAndGet()
        val now = System.currentTimeMillis()
        val truncatedSql = if (sql.length > 200) sql.take(200) + "…" else sql
        synchronized(lock) {
            if (table.isNotEmpty()) {
                readTableCounts[table] = (readTableCounts[table] ?: 0) + 1
            }
            recordTimeSeries(now, isWrite = false)
            if (recentEvents.size >= maxRecent) recentEvents.removeFirst()
            recentEvents.addLast(
                DbEvent(
                    operation = "SELECT",
                    table = table,
                    sql = truncatedSql,
                    timestamp = now,
                    isRead = true,
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
            totalReads = totalReads.get(),
            totalWrites = totalWrites.get(),
            insertCount = insertCount.get(),
            updateCount = updateCount.get(),
            deleteCount = deleteCount.get(),
            otherWriteCount = otherWriteCount.get(),
            writeTableCounts = writeTableCounts.toMap(),
            readTableCounts = readTableCounts.toMap(),
            recentEvents = recentEvents.toList(),
            timeSeries = timeSeries.toList(),
        )
    }

    /** Reset all counters + buffers. Called from the "Clear" button. */
    fun clear() {
        totalWrites.set(0)
        totalReads.set(0)
        insertCount.set(0)
        updateCount.set(0)
        deleteCount.set(0)
        otherWriteCount.set(0)
        synchronized(lock) {
            writeTableCounts.clear()
            readTableCounts.clear()
            recentEvents.clear()
            timeSeries.clear()
        }
    }

    /** Record an event in the per-second time-series bucket. */
    private fun recordTimeSeries(timestamp: Long, isWrite: Boolean) {
        val secondBucket = timestamp / 1000 * 1000  // round down to the second
        if (timeSeries.isNotEmpty() && timeSeries.last().timestamp == secondBucket) {
            // Same second — update the existing bucket.
            val last = timeSeries.removeLast()
            timeSeries.addLast(
                if (isWrite) last.copy(writeCount = last.writeCount + 1)
                else last.copy(readCount = last.readCount + 1),
            )
        } else {
            // New second — add a new bucket.
            timeSeries.addLast(
                DbTimeSeriesBucket(
                    timestamp = secondBucket,
                    readCount = if (isWrite) 0 else 1,
                    writeCount = if (isWrite) 1 else 0,
                ),
            )
        }
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
    }

    /**
     * Advance the time-series to the current second, inserting zero-valued
     * buckets for every elapsed second with no activity. This makes the chart
     * slide forward over time even when the DB is idle.
     *
     * Must be called inside `synchronized(lock)`.
     */
    private fun advanceToNow() {
        val nowSecond = System.currentTimeMillis() / 1000 * 1000
        if (timeSeries.isEmpty()) {
            // Seed a baseline zero-bucket so the chart has something to draw.
            timeSeries.addLast(DbTimeSeriesBucket(timestamp = nowSecond, readCount = 0, writeCount = 0))
            return
        }
        var last = timeSeries.last().timestamp
        while (last < nowSecond) {
            last += 1000
            timeSeries.addLast(DbTimeSeriesBucket(timestamp = last, readCount = 0, writeCount = 0))
        }
        while (timeSeries.size > maxTimeSeriesBuckets) timeSeries.removeFirst()
    }

    /**
     * Export all DB activity data as a human-readable text log suitable for
     * sharing (e.g., to share with a developer for debugging).
     *
     * Format:
     * ```
     * ANI-KUTA Debug Bubble — DB Activity Log
     * Exported: 2026-08-09 22:45:00
     * ========================================
     *
     * SUMMARY
     * =======
     * Total reads:  1,234
     * Total writes:   234
     *   INSERTs:      180
     *   UPDATEs:       40
     *   DELETEs:       10
     *   Other:          4
     *
     * TABLE BREAKDOWN (writes)
     * ========================
     * content:              80
     * anilist_detail:       36
     * ...
     *
     * TABLE BREAKDOWN (reads)
     * =======================
     * content:             500
     * anilist_detail:      300
     * ...
     *
     * RECENT EVENTS (last 200, newest first)
     * ======================================
     * [22:44:59] INSERT content
     *   INSERT INTO content (main_id, ...) VALUES (?, ?, ...)
     *
     * [22:44:58] SELECT anilist_detail
     *   SELECT * FROM anilist_detail WHERE anilist_id = ?
     * ...
     * ```
     */
    fun exportAsText(): String {
        val snap = snapshot()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        sb.appendLine("ANI-KUTA Debug Bubble — DB Activity Log")
        sb.appendLine("Exported: ${dateFmt.format(Date())}")
        sb.appendLine("========================================")
        sb.appendLine()

        // ── Summary ──
        sb.appendLine("SUMMARY")
        sb.appendLine("=======")
        sb.appendLine("Total reads:  ${"%,d".format(snap.totalReads)}")
        sb.appendLine("Total writes: ${"%,d".format(snap.totalWrites)}")
        sb.appendLine("  INSERTs:    ${"%,d".format(snap.insertCount)}")
        sb.appendLine("  UPDATEs:    ${"%,d".format(snap.updateCount)}")
        sb.appendLine("  DELETEs:    ${"%,d".format(snap.deleteCount)}")
        sb.appendLine("  Other:      ${"%,d".format(snap.otherWriteCount)}")
        sb.appendLine()

        // ── Table breakdown (writes) ──
        if (snap.writeTableCounts.isNotEmpty()) {
            sb.appendLine("TABLE BREAKDOWN (writes)")
            sb.appendLine("========================")
            val sortedWrites = snap.writeTableCounts.entries.sortedByDescending { it.value }
            val maxNameLen = (sortedWrites.maxOfOrNull { it.key.length } ?: 0).coerceAtLeast(4)
            sortedWrites.forEach { (table, count) ->
                sb.appendLine("${table.padEnd(maxNameLen)}  ${"%,d".format(count)}")
            }
            sb.appendLine()
        }

        // ── Table breakdown (reads) ──
        if (snap.readTableCounts.isNotEmpty()) {
            sb.appendLine("TABLE BREAKDOWN (reads)")
            sb.appendLine("=======================")
            val sortedReads = snap.readTableCounts.entries.sortedByDescending { it.value }
            val maxNameLen = (sortedReads.maxOfOrNull { it.key.length } ?: 0).coerceAtLeast(4)
            sortedReads.forEach { (table, count) ->
                sb.appendLine("${table.padEnd(maxNameLen)}  ${"%,d".format(count)}")
            }
            sb.appendLine()
        }

        // ── Recent events ──
        sb.appendLine("RECENT EVENTS (last ${snap.recentEvents.size}, newest first)")
        sb.appendLine("======================================")
        if (snap.recentEvents.isEmpty()) {
            sb.appendLine("(no events recorded)")
        } else {
            snap.recentEvents.reversed().forEach { event ->
                val time = timeFmt.format(Date(event.timestamp))
                sb.appendLine("[$time] ${event.operation} ${event.table.ifEmpty { "(unknown table)" }}")
                sb.appendLine("  ${event.sql}")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /** A single DB event (read or write), shown in the recent-events list. */
    data class DbEvent(
        val operation: String,   // "SELECT", "INSERT", "UPDATE", "DELETE", etc.
        val table: String,       // affected table name (may be empty)
        val sql: String,         // truncated to 200 chars
        val timestamp: Long,     // epoch millis
        val isRead: Boolean,     // true = SELECT, false = write
    )

    /** A per-second time-series bucket for the 5-minute reads/writes chart. */
    data class DbTimeSeriesBucket(
        val timestamp: Long,   // epoch millis, rounded to the second
        val readCount: Int,
        val writeCount: Int,
    )

    /** Immutable snapshot of all DB stats for the UI. */
    data class DbSnapshot(
        val totalReads: Long,
        val totalWrites: Long,
        val insertCount: Long,
        val updateCount: Long,
        val deleteCount: Long,
        val otherWriteCount: Long,
        val writeTableCounts: Map<String, Int>,
        val readTableCounts: Map<String, Int>,
        val recentEvents: List<DbEvent>,
        val timeSeries: List<DbTimeSeriesBucket> = emptyList(),
    ) {
        companion object {
            val EMPTY = DbSnapshot(0, 0, 0, 0, 0, 0, emptyMap(), emptyMap(), emptyList(), emptyList())
        }
    }
}
