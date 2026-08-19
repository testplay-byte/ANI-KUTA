package com.confused.anikuta.core.ratings.di

import com.confused.anikuta.core.ratings.RatingStore
import org.koin.dsl.module

val ratingsModule = module {
    single { RatingStore(get()) }
}
