package com.confused.anikuta.core.datacache

import org.koin.dsl.module

/**
 * Koin DI module for :core:data-cache.
 */
val dataCacheModule = module {
    single { DataCacheRepository(get()) }
}
