// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — Ultima reads DataStoreHelper.ResumeWatchingResult.getId() /
// getParentId() on the rows getResumeWatching() hands it. Only the nested data
// class is referenced by the census; the outer object keeps the upstream name
// so the FQN resolves. Upstream's object carries a large preferences/watch-state
// surface — deliberately NOT mirrored (nothing references it; the app's own
// watch-progress lives in :core:watch-progress).
package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

/** Position + duration pair (the watch-progress payload). */
@Serializable
data class PosDur(
    val position: Long,
    val duration: Long,
)

/** The inert data-store helper anchor (see the file header). */
object DataStoreHelper {

    /** One continue-watching row (the shape [com.lagradost.cloudstream3.ui.home.HomeViewModel] hands out). */
    @Serializable
    data class ResumeWatchingResult(
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType? = null,
        override var posterUrl: String? = null,
        val watchPos: PosDur? = null,
        override var id: Int? = null,
        val parentId: Int? = null,
        val episode: Int? = null,
        val season: Int? = null,
        val isFromDownload: Boolean = false,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var score: Score? = null,
    ) : SearchResponse
}
