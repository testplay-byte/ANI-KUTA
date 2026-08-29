package com.confused.anikuta.feature.cloudstreamcontent.di

import com.confused.anikuta.feature.cloudstreamcontent.CloudstreamContentDetailsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin wiring for the CloudStream content screens (session 3). */
val cloudstreamContentModule = module {
    viewModelOf(::CloudstreamContentDetailsViewModel)
}
