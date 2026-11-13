package com.confused.anikuta.feature.animebrowse.di

import com.confused.anikuta.feature.animebrowse.BrowseViewModel
import com.confused.anikuta.feature.animebrowse.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Round 148: [HomeViewModel] joins [BrowseViewModel] here — the home is the
 * Browse tab's sibling surface (same module, same Koin init via
 * AnikutaApp.browseModule), so its DI lives next to Browse's. All six
 * constructor deps (AniListApi, DataCacheRepository, WatchProgressStore,
 * ScheduleStore, UpdateStore, ContentRepository) are existing singletons on
 * the app graph — no new bindings needed.
 */
val browseModule = module {
    viewModelOf(::BrowseViewModel)
    viewModelOf(::HomeViewModel)
}
