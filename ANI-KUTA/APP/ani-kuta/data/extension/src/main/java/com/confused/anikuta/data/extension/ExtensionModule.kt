package com.confused.anikuta.data.extension

import com.confused.anikuta.core.common.model.AnimeDetailsProvider
import com.confused.anikuta.data.extension.api.AnimeExtensionApi
import com.confused.anikuta.data.extension.installer.ExtensionInstaller
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.provider.ExtensionDetailsProvider
import com.confused.anikuta.data.extension.repo.ExtensionRepoApi
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import com.confused.anikuta.data.extension.trust.TrustService
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val extensionModule = module {

    single<OkHttpClient>(named("extensionRepo")) {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    single { TrustService(get()) }
    single { ExtensionRepoRepository(get()) }
    single { ExtensionRepoApi(get(named("extensionRepo"))) }
    single { AnimeExtensionApi(get(), get()) }
    single { ExtensionInstaller(get(), get(named("extensionRepo"))) }
    single { ExtensionManager(get(), get(), get(), get(), get(named("extensionRepo")), get()) }

    // Register ExtensionDetailsProvider (injected by DetailsViewModel directly)
    single { ExtensionDetailsProvider(get()) }

    // Also register as AnimeDetailsProvider for the registry pattern
    single<AnimeDetailsProvider>(qualifier = named("extension")) {
        ExtensionDetailsProvider(get())
    }
}
