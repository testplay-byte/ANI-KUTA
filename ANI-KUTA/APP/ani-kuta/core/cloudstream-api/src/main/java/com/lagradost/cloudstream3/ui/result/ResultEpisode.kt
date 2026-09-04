// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Session-1 note (doc 23 §4): minimal shape-only placeholders so the (unused)
// VideoClickAction surface compiles. Zero census plugins reference these types;
// the full 21-field ResultEpisode belongs to the content phase if ever needed.
package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile

/** Link-loading result bundle passed to video click actions. */
data class LinkLoadingResult(
    val links: List<ExtractorLink>,
    val subs: List<SubtitleFile>,
    val syncData: Map<String, String>,
)

/** Flattened episode holder (full upstream model has 21 UI fields — content phase). */
data class ResultEpisode(
    val name: String?,
    val episode: Int,
    val season: Int?,
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val parentId: Int,
) {
    constructor(episode: Episode, apiName: String, id: Int, index: Int, parentId: Int) : this(
        name = episode.name,
        episode = episode.episode ?: index + 1,
        season = episode.season,
        data = episode.data,
        apiName = apiName,
        id = id,
        index = index,
        parentId = parentId,
    )
}
