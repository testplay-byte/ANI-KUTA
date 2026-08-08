package com.confused.anikuta.core.updates.di

import com.confused.anikuta.core.updates.UpdateEngine
import com.confused.anikuta.core.updates.UpdateStore
import org.koin.dsl.module

val updatesModule = module {
    single { UpdateStore(get()) }
    single { UpdateEngine(get(), get(), get(), get(), get()) }
}
