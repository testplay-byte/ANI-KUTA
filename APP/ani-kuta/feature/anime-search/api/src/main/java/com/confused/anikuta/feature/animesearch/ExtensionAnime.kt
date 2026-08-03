package com.confused.anikuta.feature.animesearch

import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * A lightweight wrapper around an extension's [SAnime] for the Search screen's
 * extension-browse mode.
 *
 * When the user selects the "Extension" source in Search, the screen fetches
 * the source's popular/latest anime. Each result is an [ExtensionAnime]
 * pointing to the source + the SAnime. Tapping one navigates to the Details
 * page (Phase 5b) which fetches full details + episodes from the source.
 *
 * Phase 5a: this is the browse-list model. The Details screen (5b) will accept
 * a `(sourceId, animeUrl)` pair to open an extension anime.
 *
 * @param sourceId The source's ID (from [eu.kanade.tachiyomi.animesource.AnimeSource.id]).
 * @param sourceName The source's display name.
 * @param url The SAnime's URL (source-relative identifier).
 * @param title The anime's title.
 * @param thumbnailUrl Optional cover thumbnail URL.
 */
data class ExtensionAnime(
    val sourceId: Long,
    val sourceName: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    companion object {
        fun fromSAnime(sourceId: Long, sourceName: String, sAnime: SAnime): ExtensionAnime =
            ExtensionAnime(
                sourceId = sourceId,
                sourceName = sourceName,
                url = sAnime.url,
                title = sAnime.title,
                thumbnailUrl = sAnime.thumbnail_url,
            )
    }
}
