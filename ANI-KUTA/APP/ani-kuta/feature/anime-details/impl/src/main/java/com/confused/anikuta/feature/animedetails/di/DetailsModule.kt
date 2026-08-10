package com.confused.anikuta.feature.animedetails.di

import com.confused.anikuta.core.videoresolver.VideoResolver
import com.confused.anikuta.feature.animedetails.DetailsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val detailsModule = module {
    single { VideoResolver() }
    // DetailsViewModel's 16 constructor params are resolved by Koin:
    //   AniListApi, ExtensionManager, PreferenceStore, VideoResolver,
    //   EpisodeMetadataFetcher, ExtensionDetailsProvider, AniListDetailsProvider,
    //   AutoLinkService, AutoLinkPreferences, ContentResolver, ContentRepository,
    //   DataCacheRepository, DownloadManager, WatchProgressStore (Phase WP),
    //   RatingStore (Phase 4), PlayerPreferences (Phase 2).
    viewModelOf(::DetailsViewModel)
}
