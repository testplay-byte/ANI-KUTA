package com.confused.anikuta.data.cloudstream.di

import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.loader.CloudstreamPluginLoader
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin wiring for the CloudStream extension system (mirrors :data:extension's
 * ExtensionModule structure — doc 23 §5.1).
 */
val cloudstreamModule = module {

    single<OkHttpClient>(named("cloudstreamRepo")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single { CloudstreamRepoRepository(get()) }
    single { CloudstreamRepoApi(get(named("cloudstreamRepo"))) }
    single { CloudstreamPluginStore(get()) }
    single { CloudstreamPluginInstaller(get(), get(named("cloudstreamRepo"))) }
    single { CloudstreamPluginLoader(get()) }
    single { CloudstreamPluginManager(get(), get(), get(), get(), get(), get(), get()) }
}
