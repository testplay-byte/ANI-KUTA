package com.confused.anikuta.feature.debugbubble.data

import com.confused.anikuta.core.common.LogAppender
import com.confused.anikuta.core.common.LogLevel

/**
 * In-memory ring buffer for the debug bubble's Console tab (Phase DB-4).
 *
 * Implements [LogAppender] — wired into [com.confused.anikuta.core.common.Logger]
 * via `Logger.setAppender(...)` in `:app/src/debug/DebugInit.kt`. Every log call
 * (v/d/i/w/e) appends to this buffer in debug builds.
 *
 * Capacity: 10,000 entries (D-163 — user-specified). At ~200 bytes/entry average
 * → ~2MB max RAM. Oldest evicted when full (FIFO).
 *
 * Throwable is stored as a capped (2KB) string (D-162 M2) — prevents a single
 * error with a huge stack trace from blowing the buffer.
 *
 * Thread-safe: [append] is synchronized (called from network, DB, MPV event
 * threads). [snapshot] + [clear] are also synchronized.
 *
 * CORE_RULES §20: the buffer itself doesn't log (would recurse).
 */
class DebugLogBuffer(
    private val capacity: Int = 10000,
) : LogAppender {

    private val deque = ArrayDeque<LogEntry>()
    private val lock = Any()

    override fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        synchronized(lock) {
            if (deque.size >= capacity) {
                deque.removeFirst()
            }
            val tStr = throwable?.stackTraceToString()?.take(2000)
            deque.addLast(LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwableString = tStr,
            ))
        }
    }

    /** Returns a snapshot of all entries (newest at the end). Thread-safe. */
    fun snapshot(): List<LogEntry> = synchronized(lock) { deque.toList() }

    /** Clear all entries. Thread-safe. */
    fun clear() = synchronized(lock) { deque.clear() }

    /** Current entry count. */
    fun size(): Int = synchronized(lock) { deque.size }

    /**
     * A single log entry.
     *
     * @property timestamp Epoch millis.
     * @property level The log level (V/D/I/W/E).
     * @property tag The log tag (e.g. "Anikuta:Core:Database").
     * @property message The log message.
     * @property throwableString Capped (2KB) stack trace string, or null.
     */
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwableString: String?,
    )
}
