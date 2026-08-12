package com.confused.anikuta.core.metadata.providers

import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.metadata.ContentMetadata
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.metadata.MetadataProvider

/**
 * Metadata provider for user customizations (overrides).
 *
 * D-192: The `user_customization` table was dropped (it was READ-ONLY — never
 * written to, always returned null). The user-override feature (custom title /
 * thumbnail / description per content) was designed (D-046) but never built.
 *
 * This provider is kept as a placeholder in the DI multi-binding so the
 * MetadataRegistry priority order (Local > AniList > extension) is preserved
 * for when the feature is built. It currently returns null for all lookups.
 *
 * When the feature is built: create a new `user_overrides` table (or reuse
 * `app_settings` with a key prefix) + implement the read/write here.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Metadata:Local".
 */
class LocalMetadataProvider : MetadataProvider {

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
        // D-192: user_customization table dropped. Returns null until the
        // user-override feature is built.
        return null
    }

    override suspend fun fetchEpisodeMetadata(
        episodeKey: String,
        contentKey: String,
        episodeNumber: Double,
    ): EpisodeMetadata? {
        // D-192: user_customization table dropped. Returns null until the
        // user-override feature is built.
        return null
    }
}
