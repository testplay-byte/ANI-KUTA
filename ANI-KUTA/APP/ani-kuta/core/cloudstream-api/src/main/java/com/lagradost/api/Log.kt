// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// The original home of this surface (an unlicensed companion lib + later the GPL
// CloudStream library's multiplatform expect/actual) cannot be reused; this is our
// own object with identical shape. Referenced by 48/80 real plugins.
package com.lagradost.api

import android.util.Log as AndroidLog

/** Plugin-facing logging facade — routes straight into android.util.Log. */
object Log {
    fun d(tag: String, message: String) = AndroidLog.d(tag, message)
    fun i(tag: String, message: String) = AndroidLog.i(tag, message)
    fun w(tag: String, message: String) = AndroidLog.w(tag, message)
    fun e(tag: String, message: String) = AndroidLog.e(tag, message)
}
