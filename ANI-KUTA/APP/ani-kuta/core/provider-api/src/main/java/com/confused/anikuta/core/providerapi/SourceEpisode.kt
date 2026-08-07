package com.confused.anikuta.core.providerapi

/**
 * An episode from a source.
 *
 * @param contentKey The content this episode belongs to.
 * @param externalId The source's ID for this episode.
 * @param number Episode number (supports decimals for specials/OVAs, e.g., 5.5).
 * @param name Episode title.
 * @param url The source's URL for this episode (nullable).
 * @param thumbnailUrl Episode thumbnail (nullable).
 * @param dateUpload Upload date (epoch millis, nullable).
 */
data class SourceEpisode(
    val contentKey: String,
    val externalId: String,
    val number: Double,
    val name: String,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val dateUpload: Long? = null,
) {
    /**
     * The episode key: "<contentKey>:<externalId>".
     * This is the temporary episode_key used in the database.
     */
    val episodeKey: String get() = "$contentKey:$externalId"
}
