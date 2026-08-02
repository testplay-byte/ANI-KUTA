package com.confused.anikuta.feature.animedetails.di

import com.confused.anikuta.feature.animedetails.DetailsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val detailsModule = module {
    viewModelOf(::DetailsViewModel)
}
