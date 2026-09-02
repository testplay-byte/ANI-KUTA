package com.confused.anikuta.core.common

import android.util.Log

/**
 * ANI-KUTA Logger — lambda-based, zero overhead when off.
 *
 * CORE_RULES.md §20:
 * - Filtered: log levels (VERBOSE/DEBUG/INFO/WARN/ERROR), per-module tags.
 * - Toggleable: release builds off, debug on.
 * - Lambda-based: the message lambda is only invoked if the level logs.
 * - Never call `Log.d()` directly — always go through Logger.
 *
 * Tag convention: "Anikuta:<Layer>:<Module>"
 * e.g. Logger.d("Anikuta:Core:Database") { "query executed" }
 *
 * Task 63 (round 23 — D): the console-logging toolkit (ring buffer + console
 * screen + in-app export) is REMOVED by the device spec, and with it the
 * appender/level plumbing that served it. The surviving shape is the
 * ORIGINAL CORE_RULES §20 one plus one deliberate deviation:
 * - [v]/[d]/[i] are gated by [enabled] (set from `:app`'s BuildConfig.DEBUG)
 *   — a clean release logcat: no INFO chatter from the ~200 Logger.i sites.
 * - [w]/[e] ALWAYS log (enabled or not) — they are the crash/failure
 *   diagnostics; losing them in release would blind the crash handler and
 *   every catch-site that reports why something failed (decision D-362's
 *   device-diagnosability concern, kept for the SEVERITY that matters).
 * - [w]/[e] are BEST-EFFORT platform calls (runCatching): on the JVM unit-test
 *   harness android.util.Log is NOT MOCKED and throws — a diagnostics log
 *   line must never become a test failure (or worse, replace the REAL
 *   exception being logged). CI caught this the day w/e stopped being gated
 *   on [enabled] (the CloudstreamLinkResolverTest failure-path locks).
 * - Lambda-based: the message lambda is only invoked if the level logs.
 */
object Logger {

    @Volatile
    private var enabled: Boolean = false

    /** Called from :app Application.onCreate() with :app's BuildConfig.DEBUG. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled) {
            val msg = message()
            Log.v(tag, msg, throwable)
        }
    }

    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled) {
            val msg = message()
            Log.d(tag, msg, throwable)
        }
    }

    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled) {
            val msg = message()
            Log.i(tag, msg, throwable)
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        // Task 63 (D): warnings ALWAYS reach logcat — failure diagnostics.
        // Best-effort: the JVM test harness's unmocked android.util.Log must
        // never surface (it would REPLACE the real failure being logged).
        val msg = message()
        runCatching { Log.w(tag, msg, throwable) }
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        // Task 63 (D): errors ALWAYS reach logcat — crash diagnostics.
        // Best-effort: same JVM-safety as [w].
        val msg = message()
        runCatching { Log.e(tag, msg, throwable) }
    }
}
