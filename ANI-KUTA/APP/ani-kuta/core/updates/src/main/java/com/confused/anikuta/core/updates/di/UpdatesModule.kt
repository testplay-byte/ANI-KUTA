package com.confused.anikuta.core.updates.di

import com.confused.anikuta.core.updates.UpdateEngine
import com.confused.anikuta.core.updates.UpdateStore
import org.koin.dsl.module

val updatesModule = module {
    single { UpdateStore(get()) }
    // ActualReleaseUpdater is nullable — if :core:schedule isn't available (it is, via :app),
    // the UpdateEngine skips the actual_at update. The binding is in :app's appModule.
    single { UpdateEngine(get(), get(), get(), get(), getOrNull()) }
}
