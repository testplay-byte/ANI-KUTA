package com.confused.anikuta.core.common

import android.util.Log

/**
 * ANI-KUTA Logger — lambda-based, zero overhead when off.
 *
 * CORE_RULES.md §20:
 * - Filtered: log levels (VERBOSE/DEBUG/INFO/WARN/ERROR), per-module tags.
 * - Toggleable: release builds off, debug on. Runtime toggle in Settings.
 * - Lambda-based: the message lambda is only invoked if enabled + level matches.
 * - Never call `Log.d()` directly — always go through Logger.
 *
 * Tag convention: "Anikuta:<Layer>:<Module>"
 * e.g. Logger.d("Anikuta:Core:Database") { "query executed" }
 *
 * Task 64 (round 24): the in-memory appender plumbing (LogAppender +
 * RingLogBuffer — the in-app console's capture) is REMOVED per the device
 * round ("remove the console logs only"). The Logger is logcat-only again;
 * every other debug tool (the Debug options page, the debug bubble's other
 * tabs, the resolve-list affordances) stays untouched.
 *
 * ROUND 100 (D-683) — the console RETURNS, properly this time. The round-99
 * device report asked for "proper console logging" on the debug line + a
 * better "logcat logging tool", so the capture is back as a first-class,
 * bounded, allocation-light ring buffer INSIDE the Logger itself (not a
 * bolted-on appender layer): every log line the Logger emits is ALSO
 * captured (when capture is on) with its timestamp/level/tag/message and a
 * throwable digest. The debug bubble's Console tab renders it live; release
 * builds keep paying exactly zero (capture defaults off and only the debug
 * line's Application.onCreate turns it on — the same gate the Logger's
 * enabled flag already rides).
 */
object Logger {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var minLevel: LogLevel = LogLevel.VERBOSE

    /** Called from :app Application.onCreate() with :app's BuildConfig.DEBUG. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * D-313: read accessor for callers that mirror a message into BOTH logcat
     * (always) and the Logger pipeline (when enabled) — e.g. the episode-list
     * dumper, which must reach logcat in release builds too.
     */
    val isEnabled: Boolean
        get() = enabled

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    // ── ROUND 100 (D-683): the console ring buffer ────────────────────────────

    /** One captured console line (immutable snapshot — safe to render off-thread). */
    data class ConsoleEntry(
        /** Wall-clock capture time ([System.currentTimeMillis]). */
        val atMs: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        /** First lines of a throwable's stack (when the call carried one). */
        val errorDigest: String? = null,
    )

    /**
     * The capture capacity. Sized for a real debug session: ~1000 lines is
     * the tail anyone actually scrolls; 2500 gives a full testing run
     * (7 kinds × N targets + the bridge/resolver chatter) real history
     * without unbounded growth. At a generous ~300 bytes/line that is well
     * under a megabyte.
     */
    private const val CONSOLE_CAPACITY = 2500

    /** Guards [consoleBuffer] — Logger is called from any thread. */
    private val consoleLock = Any()

    private val consoleBuffer = ArrayDeque<ConsoleEntry>(CONSOLE_CAPACITY)

    /**
     * Monotonic revision — bumped on every capture and every clear. The
     * Console tab polls it (cheap volatile read) and only re-snapshots the
     * buffer when it actually changed.
     */
    @Volatile
    private var consoleRevision: Long = 0L

    /** True when captured lines should be kept (the debug line's gate). */
    @Volatile
    private var captureEnabled: Boolean = false

    /**
     * D-683: turns console capture on/off. The debug line's Application
     * enables it alongside the Logger itself; release builds never call it
     * (capture stays off — the ring buffer never allocates an entry).
     */
    fun setConsoleCaptureEnabled(on: Boolean) {
        captureEnabled = on
        if (!on) clearConsole()
    }

    /** The current revision — poll this, snapshot only on change. */
    val consoleRevisionNow: Long
        get() = consoleRevision

    /**
     * A consistent copy of the captured console (oldest first). Takes the
     * lock once; the entries themselves are immutable.
     */
    fun consoleSnapshot(): List<ConsoleEntry> = synchronized(consoleLock) {
        consoleBuffer.toList()
    }

    /** Empties the console (the Console tab's Clear action). */
    fun clearConsole() {
        synchronized(consoleLock) {
            consoleBuffer.clear()
            consoleRevision++
        }
    }

    /** The bounded capture (called with the lock NOT held). */
    private fun capture(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!captureEnabled) return
        val digest = throwable?.let { t ->
            // The first 3 stack frames — enough to point at the failing code
            // without ballooning memory on error storms.
            buildString {
                append(t::class.java.simpleName)
                t.message?.let { append(": ").append(it.take(200)) }
                t.stackTrace.take(3).forEach { frame ->
                    append("\n    at ").append(frame.toString().take(160))
                }
            }
        }
        synchronized(consoleLock) {
            if (consoleBuffer.size >= CONSOLE_CAPACITY) {
                consoleBuffer.removeFirst()
            }
            consoleBuffer.addLast(
                ConsoleEntry(
                    atMs = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                    errorDigest = digest,
                ),
            )
            consoleRevision++
        }
    }

    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.VERBOSE) {
            val msg = message()
            Log.v(tag, msg, throwable)
            capture(LogLevel.VERBOSE, tag, msg, throwable)
        }
    }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.DEBUG) {
            val msg = message()
            Log.d(tag, msg, throwable)
            capture(LogLevel.DEBUG, tag, msg, throwable)
        }
    }

    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.INFO) {
            val msg = message()
            Log.i(tag, msg, throwable)
            capture(LogLevel.INFO, tag, msg, throwable)
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.WARN) {
            val msg = message()
            Log.w(tag, msg, throwable)
            capture(LogLevel.WARN, tag, msg, throwable)
        }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.ERROR) {
            val msg = message()
            Log.e(tag, msg, throwable)
            capture(LogLevel.ERROR, tag, msg, throwable)
        }
    }
}

enum class LogLevel(val severity: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    NONE(5);
}
