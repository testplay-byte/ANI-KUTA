package com.confused.anikuta.feature.debugbubble.data

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import kotlin.concurrent.thread

/**
 * ROUND 100 (D-683) — the debug console's LIVE LOGCAT source.
 *
 * `Runtime.exec("logcat")` from an app process returns ONLY that process's
 * own output (the per-UID log filter Android has enforced since 4.1 — no
 * READ_LOGS permission needed), which is exactly the honest scope for a
 * debug tool: everything THIS app prints, including the lines that never
 * pass through our [Logger] (OkHttp's own logging, ART GC notes, system
 * warnings about our process).
 *
 * The reader streams `logcat --pid=<pid> -v time` — `-T 1` seeds the feed
 * with the single most recent matching line (at most one line of overlap
 * with what the app-console source already shows; the session's real
 * history lives there) and then follows every NEW line. The `MM-DD
 * HH:MM:SS.mmm L/Tag (pid): message` format is parsed into [LogcatLine]s.
 *
 * Lifecycle: the reading loop runs on a dedicated thread; the moment the
 * collector goes away, [awaitClose] destroys the process — the blocked
 * readLine unblocks (stream EOF) and the thread exits. No zombie logcat.
 *
 * NOTE: this class never logs through [Logger] on its hot paths — the
 * app-console source would echo the logcat source (the reader reading its
 * own output). The one [Logger.w] fires only on the terminal start-failure
 * path, where no loop exists.
 */
class DebugLogcatReader {

    /** One parsed logcat line (this process's output only). */
    data class LogcatLine(
        /** The raw line as logcat printed it (rendered verbatim). */
        val raw: String,
        /** The single-letter level (V/D/I/W/E/F) when parsed, else null. */
        val level: Char?,
        /** The tag when parsed, else null. */
        val tag: String?,
    )

    /** Parses `MM-DD HH:MM:SS.mmm L/Tag (pid): message` — tolerant of variants. */
    internal fun parseLine(raw: String): LogcatLine {
        // Example: "09-28 13:33:01.257 I/Tag( 6907): message"
        val slash = raw.indexOf('/', raw.indexOf(' ') + 1)
        if (slash <= 0) return LogcatLine(raw, null, null)
        val level = raw.getOrNull(slash - 1)?.takeIf { it in "VDIWEF" }
        if (level == null) return LogcatLine(raw, null, null)
        val openParen = raw.indexOf('(', slash)
        val closeParen = raw.indexOf("):", slash)
        if (openParen <= 0 || closeParen <= openParen) return LogcatLine(raw, null, null)
        val tag = raw.substring(slash + 1, openParen).ifBlank { null }
        return LogcatLine(raw, level, tag)
    }

    /**
     * Streams this process's new logcat lines until the collector cancels.
     * A failed/ended stream finishes with one synthetic notice line — the
     * console shows the reason instead of a silently dead feed.
     */
    fun stream(): Flow<LogcatLine> = callbackFlow {
        // Start the process on the producing (IO) thread — the handle is a
        // val from here on, safely visible to both the reading thread and
        // awaitClose's teardown.
        val process = try {
            ProcessBuilder(
                "logcat",
                "--pid=${android.os.Process.myPid()}",
                "-v", "time",
                // The most recent single line + everything new after it.
                "-T", "1",
            )
                .redirectErrorStream(false)
                .start()
        } catch (t: Throwable) {
            Logger.w("Anikuta:Debug:Console") {
                "logcat stream failed to start: ${t::class.java.simpleName}: ${t.message}"
            }
            trySend(
                LogcatLine(
                    raw = "— logcat unavailable: ${t::class.java.simpleName}" +
                        (t.message?.let { ": $it" } ?: "") + " —",
                    level = 'W',
                    tag = "Console",
                ),
            )
            close()
            return@callbackFlow
        }
        val readerThread = thread(name = "debug-logcat", isDaemon = true) {
            try {
                val reader = BufferedReader(process.inputStream.reader(), 8192)
                // readLine() blocks until the next line, the process dies, or
                // awaitClose's destroyForcibly() breaks the stream (EOF).
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        trySend(parseLine(trimmed))
                    }
                }
            } catch (t: Throwable) {
                trySend(
                    LogcatLine(
                        raw = "— logcat stream error: ${t::class.java.simpleName} —",
                        level = 'W',
                        tag = "Console",
                    ),
                )
            } finally {
                // The stream ended (process death — its own or the teardown's)
                // — complete the flow. Noop if already closed by cancellation.
                close()
            }
        }
        awaitClose {
            // Collector gone (tab closed / source switched) — kill the
            // process; the blocked readLine returns null and the thread
            // finishes. The daemon flag is the final safety net.
            runCatching { process.destroyForcibly() }
            runCatching { readerThread.interrupt() }
        }
    }.flowOn(Dispatchers.IO)
}
