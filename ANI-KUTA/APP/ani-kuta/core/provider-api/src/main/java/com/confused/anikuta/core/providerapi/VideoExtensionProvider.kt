package com.confused.anikuta.core.providerapi

import kotlinx.coroutines.flow.Flow

/**
 * Extension provider for VIDEO content (anime, movies, series).
 *
 * Adds methods for fetching episode lists and playable video URLs.
 * Implemented by the primary extension system (:data:extension).
 */
interface VideoExtensionProvider : ExtensionProvider {

    /**
     * Observe the list of installed sources for this ecosystem.
     * Reactive — emits whenever the installed sources change (CORE_RULES §23).
     */
    fun observeInstalledSources(): Flow<List<Source>>

    /**
     * Fetch a page of content from a source (browse/search).
     *
     * @param source The source to fetch from.
     * @param page Page number (1-based).
     * @param query Optional search query. Null = browse mode.
     * @return A list of content items from this page.
     */
    fun fetchContentList(source: Source, page: Int, query: String? = null): Flow<List<SourceContent>>

    /**
     * Fetch detailed metadata for a specific content item.
     */
    fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails>

    /**
     * Fetch the episode list for a content item.
     */
    fun fetchEpisodeList(content: SourceContent): Flow<List<SourceEpisode>>

    /**
     * Fetch the list of playable videos for an episode.
     * Multiple videos = multiple quality options or hosting sources.
     */
    fun fetchVideoList(episode: SourceEpisode): Flow<List<SourceVideo>>
}
