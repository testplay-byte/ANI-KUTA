package com.confused.anikuta.core.common.model

/**
 * Registry for [AnimeDetailsProvider] implementations.
 *
 * The DetailsViewModel uses this to dispatch to the correct provider based
 * on the entry mode (AniList vs Extension). Future providers (MAL, TMDB)
 * register here without modifying existing code.
 *
 * ## Usage
 *
 * ```kotlin
 * val provider = registry.forEntryMode(EntryMode.EXTENSION)
 * val anime = provider?.fetchFromExtension(sourceId, url, title, thumb)
 * ```
 *
 * ## Multi-provider merge
 *
 * When auto-linking, the registry can provide multiple providers for merging:
 * ```kotlin
 * val base = registry.forEntryMode(EntryMode.EXTENSION)?.fetchFromExtension(...)
 * val merged = registry.forEntryMode(EntryMode.ANILIST)?.mergeInto(base)
 * ```
 */
class AnimeDetailsProviderRegistry {
    private val providers = mutableMapOf<String, AnimeDetailsProvider>()

    fun register(provider: AnimeDetailsProvider) {
        providers[provider.id] = provider
    }

    fun get(id: String): AnimeDetailsProvider? = providers[id]

    /** Get the provider for AniList entries. */
    val anilistProvider: AnimeDetailsProvider?
        get() = providers["anilist"]

    /** Get the provider for extension entries. */
    val extensionProvider: AnimeDetailsProvider?
        get() = providers["extension"]

    /** Get the provider for a specific entry mode. */
    fun forEntryMode(mode: EntryMode): AnimeDetailsProvider? = when (mode) {
        EntryMode.ANILIST -> anilistProvider
        EntryMode.EXTENSION -> extensionProvider
    }
}
