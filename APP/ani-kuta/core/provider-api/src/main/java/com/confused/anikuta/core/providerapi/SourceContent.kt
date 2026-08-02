package com.confused.anikuta.core.providerapi

/**
 * A content item (anime, manga, novel) from a source.
 *
 * @param sourceKey The source this came from ("<ecosystemId>:<sourceId>").
 * @param externalId The source's ID for this content.
 * @param title Display title.
 * @param thumbnailUrl Cover/thumbnail URL (nullable).
 * @param url The source's URL for this content (nullable — some sources use IDs, not URLs).
 */
data class SourceContent(
    val sourceKey: String,
    val externalId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val url: String? = null,
) {
    /**
     * The content key: "<sourceKey>:<externalId>".
     * This is the temporary content_key used in the database until the identity system is built.
     */
    val contentKey: String get() = "$sourceKey:$externalId"
}
