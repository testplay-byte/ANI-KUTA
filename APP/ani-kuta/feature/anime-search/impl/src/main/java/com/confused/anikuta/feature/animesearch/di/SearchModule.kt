package com.confused.anikuta.feature.animesearch.di

import com.confused.anikuta.feature.animesearch.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchModule = module {
    viewModelOf(::SearchViewModel)
}
