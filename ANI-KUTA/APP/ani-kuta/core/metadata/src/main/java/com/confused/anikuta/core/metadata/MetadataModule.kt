package com.confused.anikuta.core.metadata

import com.confused.anikuta.core.metadata.providers.AniListMetadataProvider
import com.confused.anikuta.core.metadata.providers.AniZipEpisodeProvider
import com.confused.anikuta.core.metadata.providers.JikanEpisodeProvider
import com.confused.anikuta.core.metadata.providers.KitsuEpisodeProvider
import com.confused.anikuta.core.metadata.providers.LocalMetadataProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for :core:metadata.
 *
 * Provides:
 * - [MetadataMerger] — merges content + episode metadata from multiple sources.
 * - [MetadataRegistry] — content-level metadata registry (Local + AniList providers).
 * - [EpisodeMetadataEngine] — batch episode metadata orchestrator (D-190).
 * - [EpisodeMetadataProvider] multi-binding — pluggable episode metadata sources
 *   (AniZip > Jikan > Kitsu). Order = priority (first = highest).
 *
 * CORE_RULES §20: All operations logged via the providers' own tags.
 */
val metadataModule = module {
    single { MetadataMerger() }

    // ── Content-level metadata (existing) ──
    // Multi-binding: List<MetadataProvider> (order = priority, first = highest)
    // Local (user overrides) first → AniList second
    single<List<MetadataProvider>>(named("metadataProviders")) {
        listOf(
            LocalMetadataProvider(),
            AniListMetadataProvider(get()),
        )
    }

    single {
        MetadataRegistry(
            providers = get(named("metadataProviders")),
            merger = get(),
        )
    }

    // ── Episode-level metadata (D-190) ──
    // Multi-binding: List<EpisodeMetadataProvider> (order = priority, first = highest)
    // AniZip (primary — richest) → Jikan (filler/recap/score) → Kitsu (canonical titles)
    single<List<EpisodeMetadataProvider>>(named("episodeMetadataProviders")) {
        listOf(
            AniZipEpisodeProvider(get()),
            JikanEpisodeProvider(get()),
            KitsuEpisodeProvider(get()),
        )
    }

    // The engine orchestrates providers + merges results. Public API:
    // fetchEpisodeMetadata(anilistId, malId, episodeCount) — backward-compatible.
    single {
        EpisodeMetadataEngine(
            aniListApi = get(),
            providers = get(named("episodeMetadataProviders")),
            merger = get(),
        )
    }
}
