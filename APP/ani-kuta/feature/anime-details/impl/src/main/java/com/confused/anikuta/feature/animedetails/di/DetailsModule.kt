package com.confused.anikuta.feature.animedetails.di

import com.confused.anikuta.core.common.model.AnimeDetailsProvider
import com.confused.anikuta.core.videoresolver.VideoResolver
import com.confused.anikuta.data.extension.provider.ExtensionDetailsProvider
import com.confused.anikuta.feature.animedetails.DetailsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val detailsModule = module {
    single { VideoResolver() }
    viewModelOf(::DetailsViewModel)
}
