// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.InternalAPI

/** Global debug gate read by the mvvm debug helpers (AppUtils-adjacent surface). */
@InternalAPI
object AppDebug {
    @Volatile
    var isDebug: Boolean = false
}
