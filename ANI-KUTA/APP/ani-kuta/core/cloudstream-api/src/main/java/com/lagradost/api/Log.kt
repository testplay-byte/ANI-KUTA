// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// The original home of this surface (an unlicensed companion lib + later the GPL
// CloudStream library's multiplatform expect/actual) cannot be reused; this is our
// own object with identical shape. Referenced by 48/80 real plugins.
package com.lagradost.api

import android.util.Log as AndroidLog

/**
 * Plugin-facing logging facade — routes straight into android.util.Log.
 *
 * Task 49 (round 9 — the console logging tool): two hardenings.
 *
 * 1. **runCatching around android.util.Log** — plain JVM unit tests (the
 *    cloudstream-api test sourceset) hit "Method … not mocked" RuntimeExceptions
 *    the moment any code under test logs; wrapped, logging becomes a no-op
 *    off-device instead of a test killer.
 *
 * 2. **[sink]** — an optional in-process tap the app installs at startup so
 *    PLUGIN logging (plugins call `Log.i("TAG", …)` — 48/80 of them) and the
 *    vendored CS layer's raw-Log sites reach the user-facing console
 *    (Settings → Developer tools → Console logs) and its logcat export.
 *    The sink NEVER replaces logcat output, only mirrors it. cloudstream-api
 *    deliberately does not depend on :core:common (module graph), so the hook
 *    is set from the app side.
 */
object Log {
    /** Mirrors every facade call; installed by the app process. Must be cheap + never throw. */
    @Volatile
    var sink: ((level: Level, tag: String, message: String) -> Unit)? = null

    enum class Level { D, I, W, E }

    private fun emit(level: Level, tag: String, message: String) {
        runCatching {
            when (level) {
                Level.D -> AndroidLog.d(tag, message)
                Level.I -> AndroidLog.i(tag, message)
                Level.W -> AndroidLog.w(tag, message)
                Level.E -> AndroidLog.e(tag, message)
            }
        }
        runCatching { sink?.invoke(level, tag, message) }
    }

    fun d(tag: String, message: String) = emit(Level.D, tag, message)
    fun i(tag: String, message: String) = emit(Level.I, tag, message)
    fun w(tag: String, message: String) = emit(Level.W, tag, message)
    fun e(tag: String, message: String) = emit(Level.E, tag, message)
}
