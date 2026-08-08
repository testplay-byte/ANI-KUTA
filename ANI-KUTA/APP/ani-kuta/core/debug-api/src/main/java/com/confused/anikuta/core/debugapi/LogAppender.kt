package com.confused.anikuta.core.debugapi

import com.confused.anikuta.core.common.LogLevel

/**
 * Interface for appending log entries to an in-memory buffer (Phase DB).
 *
 * Lives in `:core:debug-api` (always on the classpath) so that [Logger]
 * (in `:core:common`) can hold a reference to it without depending on the
 * debug-only `:feature:debug-bubble` module. The concrete implementation
 * (`DebugLogBuffer`, a 10,000-entry ring buffer) lives in
 * `:feature:debug-bubble` and is wired in `:app/src/debug/DebugInit.kt`.
 *
 * When no appender is set (`Logger.appender == null`, the default), Logger's
 * `appender?.append(...)` is a no-op — zero overhead in release builds.
 */
interface LogAppender {
    /**
     * Append a log entry. Must be thread-safe (called from network, DB, and
     * MPV event threads). O(1) (ring buffer with eviction).
     */
    fun append(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}
