package com.confused.anikuta.feature.updates.di

import com.confused.anikuta.feature.updates.UpdatesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val updatesFeatureModule = module {
    viewModelOf(::UpdatesViewModel)
}
