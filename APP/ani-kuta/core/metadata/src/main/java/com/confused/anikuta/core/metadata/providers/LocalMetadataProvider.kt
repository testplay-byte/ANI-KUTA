package com.confused.anikuta.core.metadata.providers

import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.metadata.ContentMetadata
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.metadata.MetadataProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Metadata provider that reads user customizations (overrides) from the database.
 *
 * This is the HIGHEST priority provider — user overrides always win.
 * Users can customize: title, thumbnail, description per anime/episode.
 *
 * Reads from the `user_customization` table (via SQLDelight queries).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Metadata:Local".
 */
class LocalMetadataProvider(
    private val database: AnikutaDatabase,
) : MetadataProvider {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Local"
    }

    override val id: String = "local"
    override val displayName: String = "User Customizations"
    override val supportedContentTypes: Set<ContentType> = setOf(
        ContentType.VIDEO,
        ContentType.IMAGE,
        ContentType.TEXT,
    )

    override suspend fun fetchContentMetadata(contentKey: String, title: String): ContentMetadata? {
        Logger.d(TAG) { "Checking user customization for: $contentKey" }

        val row = database.customizationQueries.getContentCustomization(contentKey).executeAsOneOrNull()
            ?: return null

        Logger.d(TAG) { "Found user override for: $contentKey" }

        return ContentMetadata(
            contentKey = contentKey,
            title = row.custom_title ?: title, // Fall back to the provided title
            coverUrl = row.custom_thumbnail,
            description = row.custom_description,
        )
    }

    override suspend fun fetchEpisodeMetadata(
        episodeKey: String,
        contentKey: String,
        episodeNumber: Double,
    ): EpisodeMetadata? {
        Logger.d(TAG) { "Checking episode customization for: $episodeKey" }

        val row = database.customizationQueries.getEpisodeCustomization(episodeKey).executeAsOneOrNull()
            ?: return null

        Logger.d(TAG) { "Found episode override for: $episodeKey" }

        return EpisodeMetadata(
            episodeKey = episodeKey,
            number = episodeNumber,
            title = row.custom_title,
            thumbnailUrl = row.custom_thumbnail,
            description = row.custom_description,
        )
    }
}
