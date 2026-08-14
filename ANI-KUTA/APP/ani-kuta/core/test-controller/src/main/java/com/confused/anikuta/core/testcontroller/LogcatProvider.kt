package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.LogLevel
import com.confused.anikuta.core.testapi.LogEntry
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer

/**
 * Provides recent logcat lines from [DebugLogBuffer] (D-201 reuse).
 *
 * The buffer is wired into [com.confused.anikuta.core.common.Logger] via `Logger.setAppender(...)`
 * in `:app/src/debug/DebugInit.kt`, so every `Logger.v/d/i/w/e` call appends here. We don't have
 * `READ_LOGS` (system permission) so we can't see other processes' logcat — only our own Logger output,
 * which is exactly what the agent needs for app diagnostics.
 */
class LogcatProvider(
    private val buffer: DebugLogBuffer,
) {
    fun recent(lines: Int, filter: String?, level: String?): List<LogEntry> {
        val snapshot = buffer.snapshot()
        val levelOrdinal = level?.let { runCatching { LogLevel.valueOf(it.uppercase()).ordinal }.getOrNull() }
        val filtered = snapshot.asSequence()
            .filter { levelOrdinal == null || it.level.ordinal >= levelOrdinal }
            .filter { filter.isNullOrBlank() || it.tag.contains(filter, true) || it.message.contains(filter, true) }
            .toList()
        // Take the LAST `lines` (most recent) — the snapshot is oldest→newest.
        val start = (filtered.size - lines).coerceAtLeast(0)
        return filtered.subList(start, filtered.size).map { e ->
            LogEntry(
                timestamp = e.timestamp,
                level = e.level.name,
                tag = e.tag,
                message = e.message + (e.throwableString?.let { "\n$it" } ?: ""),
            )
        }
    }
}
