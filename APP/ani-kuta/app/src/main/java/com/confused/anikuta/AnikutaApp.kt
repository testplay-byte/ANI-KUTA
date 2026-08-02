package com.confused.anikuta

import android.app.Application
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.anilist.di.anilistModule
import com.confused.anikuta.core.database.DatabaseDriverFactory
import com.confused.anikuta.core.network.HttpClientFactory
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.feature.animebrowse.di.browseModule
import com.confused.anikuta.feature.animedetails.di.detailsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class AnikutaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // CORE_RULES.md §20: Logger init with :app's BuildConfig.DEBUG
        Logger.setEnabled(BuildConfig.DEBUG)

        // Koin init
        startKoin {
            androidContext(this@AnikutaApp)
            modules(
                anilistModule,
                browseModule,
                detailsModule,
                appModule,
            )
        }
    }

    companion object {
        // App-level infrastructure DI (core modules that don't have their own Koin modules yet)
        private val appModule = module {
            single { HttpClientFactory().create() }
            single { DatabaseDriverFactory(get()) }
            single { PreferenceStore(get()) }
        }
    }
}
