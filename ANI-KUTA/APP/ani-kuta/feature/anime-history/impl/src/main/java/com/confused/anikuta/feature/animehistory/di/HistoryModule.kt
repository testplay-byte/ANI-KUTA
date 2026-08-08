package com.confused.anikuta.feature.animehistory.di

import com.confused.anikuta.feature.animehistory.HistoryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val historyModule = module {
    viewModelOf(::HistoryViewModel)
}
