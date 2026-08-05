package com.confused.anikuta.feature.animebrowse.di

import com.confused.anikuta.feature.animebrowse.BrowseViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val browseModule = module {
    viewModelOf(::BrowseViewModel)
}
