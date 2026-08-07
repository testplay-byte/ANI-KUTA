package com.confused.anikuta.feature.animelibrary.di

import com.confused.anikuta.feature.animelibrary.LibraryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryModule = module {
    viewModelOf(::LibraryViewModel)
}
