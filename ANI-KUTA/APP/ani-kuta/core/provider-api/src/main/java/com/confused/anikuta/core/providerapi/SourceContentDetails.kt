package com.confused.anikuta.core.providerapi

/**
 * Detailed metadata for a content item, fetched from a source.
 */
data class SourceContentDetails(
    val sourceKey: String,
    val externalId: String,
    val title: String,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    val thumbnailUrl: String? = null,
    val bannerUrl: String? = null,
    val year: Int? = null,
    val author: String? = null,
    val artist: String? = null,
    val episodes: List<SourceEpisode> = emptyList(),
) {
    val contentKey: String get() = "$sourceKey:$externalId"
}
