package com.confused.anikuta.core.anilist.di

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.anilist.provider.AniListDetailsProvider
import com.confused.anikuta.core.common.DefaultDispatcherProvider
import com.confused.anikuta.core.common.DispatcherProvider
import com.confused.anikuta.core.common.model.AnimeDetailsProvider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val anilistModule = module {
    singleOf(::AniListApi)
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
    // Register AniList as an AnimeDetailsProvider
    single<AnimeDetailsProvider>(qualifier = org.koin.core.qualifier.named("anilist")) {
        AniListDetailsProvider(get())
    }
}
