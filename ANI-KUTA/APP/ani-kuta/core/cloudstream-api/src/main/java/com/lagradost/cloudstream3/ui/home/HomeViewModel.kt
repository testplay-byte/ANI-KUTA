// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — Ultima reads HomeViewModel.Companion.getResumeWatching() (a
// suspend fun, exactly as named upstream) for its continue-watching hooks. The
// upstream class extends androidx.lifecycle.ViewModel; this anchor stays a
// plain class (nothing in the census instantiates it — only the companion is
// touched — and the compat module does not carry lifecycle-viewmodel). The
// companion answers null ("nothing resume-watched"), the honest empty shape.
package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.utils.DataStoreHelper

/** The inert home-viewmodel anchor (see the file header). */
class HomeViewModel {
    companion object {
        /** The continue-watching list (inert: none). */
        suspend fun getResumeWatching(): List<DataStoreHelper.ResumeWatchingResult>? = null
    }
}
