package com.confused.anikuta.feature.animesearch

import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * Converts an [SAnime] (from :core:source-api) to an [ExtensionAnime] (the
 * lightweight api-module model). This lives in :impl so the :api module stays
 * free of the source-api dependency.
 */
fun SAnime.toExtensionAnime(sourceId: Long, sourceName: String): ExtensionAnime =
    ExtensionAnime(
        sourceId = sourceId,
        sourceName = sourceName,
        url = url,
        title = title,
        thumbnailUrl = thumbnail_url,
    )
