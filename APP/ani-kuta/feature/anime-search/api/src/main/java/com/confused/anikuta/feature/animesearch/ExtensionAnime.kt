package com.confused.anikuta.feature.animesearch

/**
 * A lightweight model representing an anime from an extension source, for the
 * Search screen's extension-browse mode.
 *
 * This model lives in the `:api` module (no `:core:source-api` dependency) so
 * it can be referenced from navigation keys. The conversion from
 * `eu.kanade.tachiyomi.animesource.model.SAnime` happens in the `:impl` module
 * (which has the source-api dependency) via [toExtensionAnime].
 *
 * @param sourceId The source's ID.
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
)

/**
 * Extension function (in :impl via :core:source-api) to convert an SAnime.
 * Declared here as an extension so the api module stays source-api-free.
 * The actual implementation is in the impl module's SearchViewModel.
 */
