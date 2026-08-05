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

    /** Called from :app Application.onCreate() with :app's BuildConfig.DEBUG. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.VERBOSE) {
            Log.v(tag, message(), throwable)
        }
    }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.DEBUG) {
            Log.d(tag, message(), throwable)
        }
    }

    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.INFO) {
            Log.i(tag, message(), throwable)
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.WARN) {
            Log.w(tag, message(), throwable)
        }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled && minLevel <= LogLevel.ERROR) {
            Log.e(tag, message(), throwable)
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
