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
 */
object Logger {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var minLevel: LogLevel = LogLevel.VERBOSE

    /**
     * In-memory log appender (Phase DB). Null by default — zero overhead when
     * not set. Set by `:app/src/debug/DebugInit.kt` to a `DebugLogBuffer`
     * (10,000-entry ring buffer) in debug builds. Release builds never set it.
     */
    @Volatile
    private var appender: LogAppender? = null

    /** Set the in-memory appender (debug builds only). Null = no buffering. */
    fun setAppender(appender: LogAppender?) {
        this.appender = appender
    }

    /** Called from :app Application.onCreate() with :app's BuildConfig.DEBUG. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.VERBOSE) {
            val msg = message()
            Log.v(tag, msg, throwable)
            appender?.append(LogLevel.VERBOSE, tag, msg, throwable)
        }
    }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.DEBUG) {
            val msg = message()
            Log.d(tag, msg, throwable)
            appender?.append(LogLevel.DEBUG, tag, msg, throwable)
        }
    }

    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.INFO) {
            val msg = message()
            Log.i(tag, msg, throwable)
            appender?.append(LogLevel.INFO, tag, msg, throwable)
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.WARN) {
            val msg = message()
            Log.w(tag, msg, throwable)
            appender?.append(LogLevel.WARN, tag, msg, throwable)
        }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.ERROR) {
            val msg = message()
            Log.e(tag, msg, throwable)
            appender?.append(LogLevel.ERROR, tag, msg, throwable)
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
