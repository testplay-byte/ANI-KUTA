package com.confused.anikuta.feature.animesearch

/**
 * A lightweight model representing content from an extension source, for the
 * Search screen's extension-browse mode.
 *
 * This model lives in the `:api` module (no `:core:source-api` dependency) so
 * it can be referenced from navigation keys. The conversion from
 * `eu.kanade.tachiyomi.animesource.model.SAnime` happens in the `:impl` module
 * (which has the source-api dependency) via [toExtensionAnime].
 *
 * Session 3 (CloudStream execution phase 1): [sourceKey] carries the
 * ecosystem-qualified identity `"cloudstream:<providerName>"` for CloudStream
 * results (doc 16 §5.2 string-key discipline). Null = an aniyomi result, whose
 * identity remains [sourceId] — the aniyomi flow is byte-identical to before.
 * The results grid keys rows on `sourceKey ?: sourceId` and the details
 * navigation branches on it.
 *
 * @param sourceId The source's ID (aniyomi; -1 for CloudStream results).
 * @param sourceKey Ecosystem-qualified key — `"cloudstream:<providerName>"` for
 *   CloudStream results, null for aniyomi results.
 * @param sourceName The source's display name.
 * @param url The content URL (source-relative identifier).
 * @param title The content's title.
 * @param thumbnailUrl Optional cover thumbnail URL.
 * @param year Release year when the search result carried one (Task 47 —
 *   CloudStream search responses include `year` even when the provider's
 *   load() omits it; threaded to the details screen as a fallback seed).
 */
data class ExtensionAnime(
    val sourceId: Long,
    val sourceName: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val sourceKey: String? = null,
    val year: Int? = null,
)

/**
 * Extension function (in :impl via :core:source-api) to convert an SAnime.
 * Declared here as an extension so the api module stays source-api-free.
 * The actual implementation is in the impl module's SearchViewModel.
 */
