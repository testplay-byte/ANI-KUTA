package com.confused.anikuta.core.videoresolver

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val videoResolverModule = module {
    singleOf(::VideoResolver)
}
