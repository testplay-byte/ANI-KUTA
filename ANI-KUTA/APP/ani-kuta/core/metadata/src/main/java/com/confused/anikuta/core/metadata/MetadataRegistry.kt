package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registry for metadata providers. Queries [List<MetadataProvider>] by content type.
 *
 * Registered via Koin multi-binding: `single<List<MetadataProvider>>(named("metadataProviders"))`.
 * Adding a new provider = one class + one Koin line.
 *
 * CORE_RULES §23: Exposes StateFlow for reactive cache updates.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Metadata:Registry".
 */
class MetadataRegistry(
    private val providers: List<MetadataProvider>,
    private val merger: MetadataMerger,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Metadata:Registry"
    }

    private val _contentCache = MutableStateFlow<Map<String, ContentMetadata>>(emptyMap())
    val contentCache: StateFlow<Map<String, ContentMetadata>> = _contentCache.asStateFlow()

    /**
     * Fetch merged content metadata from all applicable providers.
     *
     * @param contentKey The content to fetch.
     * @param title The title (for search).
     * @param contentType The content type (filters which providers to query).
     * @return The merged metadata, or null if no provider has it.
     */
    suspend fun fetchContentMetadata(
        contentKey: String,
        title: String,
        contentType: ContentType = ContentType.VIDEO,
    ): ContentMetadata? {
        Logger.d(TAG) { "Fetching metadata for: $contentKey ($title)" }

        val applicableProviders = providers.filter { contentType in it.supportedContentTypes }
        Logger.v(TAG) { "Querying ${applicableProviders.size} providers" }

        val results = applicableProviders.map { provider ->
            try {
                provider.fetchContentMetadata(contentKey, title)
            } catch (e: Exception) {
                Logger.w(TAG) { "Provider ${provider.id} failed: ${e.message}" }
                null
            }
        }

        val merged = merger.mergeContent(results)

        if (merged != null) {
            _contentCache.value = _contentCache.value + (contentKey to merged)
            Logger.i(TAG) { "Merged metadata for: ${merged.title}" }
        }

        return merged
    }

    /**
     * Fetch merged episode metadata from all applicable providers.
     */
    suspend fun fetchEpisodeMetadata(
        episodeKey: String,
        contentKey: String,
        episodeNumber: Double,
        contentType: ContentType = ContentType.VIDEO,
    ): EpisodeMetadata? {
        Logger.d(TAG) { "Fetching episode metadata for: $episodeKey (ep $episodeNumber)" }

        val applicableProviders = providers.filter { contentType in it.supportedContentTypes }
        val results = applicableProviders.map { provider ->
            try {
                provider.fetchEpisodeMetadata(episodeKey, contentKey, episodeNumber)
            } catch (e: Exception) {
                Logger.w(TAG) { "Provider ${provider.id} failed: ${e.message}" }
                null
            }
        }

        return merger.mergeEpisode(results)
    }

    /**
     * Get cached content metadata (no network call).
     */
    fun getCachedContentMetadata(contentKey: String): ContentMetadata? {
        return _contentCache.value[contentKey]
    }
}
