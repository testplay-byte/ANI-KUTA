package com.confused.anikuta.data.extension

import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.trust.TrustService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val extensionModule = module {
    singleOf(::TrustService)
    singleOf(::ExtensionManager)
}
