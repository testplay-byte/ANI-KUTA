package com.confused.anikuta.core.common

/**
 * Interface for appending log entries to an in-memory buffer (Phase DB).
 *
 * Lives in `:core:common` (always on the classpath) so that [Logger] can hold
 * a reference to it without a circular dependency. The concrete implementation
 * (`DebugLogBuffer`, a 10,000-entry ring buffer) lives in `:feature:debug-bubble`
 * (debug-only) + is wired in `:app/src/debug/DebugInit.kt`.
 *
 * When no appender is set (`Logger.appender == null`, the default in release
 * builds), Logger's `appender?.append(...)` is a no-op — zero overhead.
 */
interface LogAppender {
    /**
     * Append a log entry. Must be thread-safe (called from network, DB, and
     * MPV event threads). O(1) (ring buffer with eviction).
     */
    fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}
