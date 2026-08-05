package com.confused.anikuta.core.providerapi

/**
 * A content source provided by an extension.
 *
 * @param ecosystemId Which ecosystem this source belongs to.
 * @param sourceId Unique ID within the ecosystem.
 * @param name Display name.
 * @param lang Language code (e.g., "en", "ja").
 * @param isNsfw Whether this source provides NSFW content.
 */
data class Source(
    val ecosystemId: String,
    val sourceId: String,
    val name: String,
    val lang: String = "en",
    val isNsfw: Boolean = false,
) {
    /**
     * The content key for this source: "<ecosystemId>:<sourceId>".
     * Used as a prefix for content_key values in the database.
     */
    val key: String get() = "$ecosystemId:$sourceId"
}
