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

    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.VERBOSE) {
            val msg = message()
            Log.v(tag, msg, throwable)
        }
    }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.DEBUG) {
            val msg = message()
            Log.d(tag, msg, throwable)
        }
    }

    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.INFO) {
            val msg = message()
            Log.i(tag, msg, throwable)
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.WARN) {
            val msg = message()
            Log.w(tag, msg, throwable)
        }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.ERROR) {
            val msg = message()
            Log.e(tag, msg, throwable)
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
