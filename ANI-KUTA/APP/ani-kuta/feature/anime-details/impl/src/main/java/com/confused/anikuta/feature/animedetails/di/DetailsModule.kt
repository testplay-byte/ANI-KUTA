package com.confused.anikuta.feature.animedetails.di

import com.confused.anikuta.core.videoresolver.VideoResolver
import com.confused.anikuta.feature.animedetails.DetailsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val detailsModule = module {
    single { VideoResolver() }
    // D-236: Use viewModel { } instead of viewModelOf(::DetailsViewModel) because
    // the constructor now has 22 params — Koin's viewModelOf has overloads for
    // 21 and 22 params, causing overload resolution ambiguity. The explicit
    // lambda form resolves all params via get() + getOrNull() for nullable ones.
    viewModel {
        DetailsViewModel(
            anilistApi = get(),
            extensionManager = get(),
            preferenceStore = get(),
            videoResolver = get(),
            episodeMetadataEngine = get(),
            extensionProvider = get(),
            anilistProvider = get(),
            autoLinkService = get(),
            autoLinkPreferences = get(),
            contentResolver = get(),
            contentRepository = get(),
            dataCacheRepository = get(),
            downloadManager = get(),
            watchProgressStore = get(),
            ratingStore = get(),
            playerPreferences = get(),
            genreRepository = get(),
            activityTracker = get(),
            updateEngine = get(),
            updateStore = get(),
            scheduleStore = get(),
            coverColorExtractor = getOrNull(),
            reverseAutoLinkService = getOrNull(),
        )
    }
}
