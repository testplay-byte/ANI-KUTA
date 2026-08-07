package com.confused.anikuta.feature.download.di

import com.confused.anikuta.feature.download.DownloadViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for the Downloads feature.
 *
 * D.6: Registers [DownloadViewModel]. The data source ([DownloadManager]) is
 * provided by `downloadModule` (in `:core:download`), so this module only
 * needs the ViewModel.
 */
val downloadFeatureModule: Module = module {
    viewModelOf(::DownloadViewModel)
}
