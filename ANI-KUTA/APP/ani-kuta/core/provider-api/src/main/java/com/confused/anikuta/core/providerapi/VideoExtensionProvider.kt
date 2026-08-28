package com.confused.anikuta.core.providerapi

import kotlinx.coroutines.flow.Flow

/**
 * Extension provider for VIDEO content (anime, movies, series).
 *
 * Adds methods for fetching episode lists and playable video URLs.
 * Implemented by the primary extension system (:data:extension).
 *
 * D-302: this interface is now REAL — `AniyomiExtensionProvider` (in
 * :data:extension) implements it over the Aniyomi-compatible extension
 * manager and is registered in Koin. The interface is split in two halves:
 *
 *  - **Content queries** (the original D-031 scaffolding): observe sources,
 *    fetch content/details/episodes/videos. Pure reads bridged to the
 *    ecosystem's source API.
 *  - **Lifecycle management** (D-301/D-302): install/uninstall/enable/
 *    update-check, so settings UIs and future consumers can manage
 *    extensions without binding to ecosystem-specific types.
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

    // ── Lifecycle management (D-302) ──────────────────────────────────────────

    /**
     * Install (or update) an extension by package name from its repository.
     * Implementations handle download + installer dispatch; the terminal state
     * arrives asynchronously through [observeInstalledSources].
     */
    fun install(pkgName: String)

    /** Uninstall an extension by package name. */
    fun uninstall(pkgName: String)

    /** Enable/disable a package's sources without uninstalling. */
    fun setEnabled(pkgName: String, enabled: Boolean)

    /** Trigger an update check against the configured repositories. */
    fun checkForUpdates()
}
