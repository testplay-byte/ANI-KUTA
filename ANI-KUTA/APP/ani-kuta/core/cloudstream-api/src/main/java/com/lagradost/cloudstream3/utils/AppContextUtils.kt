// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — the census (Ultima) calls the $default synthetic of
// AppContextUtils.setDefaultFocus(AlertDialog, Int = BUTTON_NEGATIVE): an
// extension declared inside the object, so it compiles to the static-shaped
// (AppContextUtils, AlertDialog, int, int, Object) JVM signature. Upstream
// forces focus onto the negative button on TV/emulator layouts; ANI-KUTA is
// a touch layout, so ours is an honest no-op that keeps the ABI.
package com.lagradost.cloudstream3.utils

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

/** The inert app-context utilities anchor (see the file header). */
object AppContextUtils {

    /**
     * Sets the focus to the negative button on TV/emulator layouts — a no-op
     * here (touch layout), kept for plugin binary compatibility.
     */
    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        // Inert by design: ANI-KUTA has no TV/emulator layout to force focus for.
    }
}
