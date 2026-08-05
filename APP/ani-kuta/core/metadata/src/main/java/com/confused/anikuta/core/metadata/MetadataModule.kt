package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.metadata.providers.AniListMetadataProvider
import com.confused.anikuta.core.metadata.providers.LocalMetadataProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

val metadataModule = module {
    single { MetadataMerger() }

    // Multi-binding: List<MetadataProvider> (order = priority, first = highest)
    // Local (user overrides) first → AniList second
    single<List<MetadataProvider>>(named("metadataProviders")) {
        listOf(
            LocalMetadataProvider(get()),
            AniListMetadataProvider(get()),
        )
    }

    single {
        MetadataRegistry(
            providers = get(named("metadataProviders")),
            merger = get(),
        )
    }

    // Episode metadata fetcher — fetches per-episode titles, thumbnails, etc.
    single { EpisodeMetadataFetcher(get()) }
}
