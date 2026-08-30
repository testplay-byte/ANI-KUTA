package com.confused.anikuta.feature.cswatch.impl.di

import com.confused.anikuta.feature.cswatch.impl.CsWatchViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin wiring for the CloudStream watch screen (task 52 / round 12).
 * The resolver single comes from :data:cloudstream's cloudstreamModule;
 * the WatchProgressStore from core:watch-progress's watchProgressModule.
 */
val csWatchModule = module {
    viewModel {
        CsWatchViewModel(
            resolver = get(),
            watchProgressStore = get(),
            sourceMemory = get(),
        )
    }
}
