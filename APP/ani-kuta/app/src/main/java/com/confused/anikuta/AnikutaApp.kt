package com.confused.anikuta

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.activitytracker.activityTrackerModule
import com.confused.anikuta.core.anilist.di.anilistModule
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.database.DatabaseDriverFactory
import com.confused.anikuta.core.network.HttpClientFactory
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.download.downloadModule
import com.confused.anikuta.core.metadata.metadataModule
import com.confused.anikuta.core.player.playerModule
import com.confused.anikuta.core.trackeranilist.trackerAniListModule
import com.confused.anikuta.core.videoresolver.videoResolverModule
import com.confused.anikuta.data.extension.extensionModule
import com.confused.anikuta.feature.animebrowse.di.browseModule
import com.confused.anikuta.feature.animedetails.di.detailsModule
import com.confused.anikuta.feature.animelibrary.di.libraryModule
import com.confused.anikuta.feature.animesearch.di.searchModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

class AnikutaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // CORE_RULES §20: Logger init with :app's BuildConfig.DEBUG
        Logger.setEnabled(BuildConfig.DEBUG)

        // Koin init (session ID is provided via appModule — new UUID per process restart)
        startKoin {
            androidContext(this@AnikutaApp)
            modules(
                anilistModule,
                browseModule,
                detailsModule,
                libraryModule,
                searchModule,
                activityTrackerModule,
                extensionModule,
                playerModule,
                videoResolverModule,
                downloadModule,
                metadataModule,
                trackerAniListModule,
                appModule,
            )
        }
    }

    companion object {
        // App-level infrastructure DI
        private val appModule = module {
            // Network
            single<OkHttpClient> { HttpClientFactory().create() }

            // Database
            single<SqlDriver> { DatabaseDriverFactory(get()).create() }
            single<AnikutaDatabase> { AnikutaDatabase(get()) }

            // Session ID (for activity tracking — new per process restart)
            single(named("sessionId")) { UUID.randomUUID().toString() }

            // Preferences
            single { PreferenceStore(get()) }
            single { AppPreferences(get()) }
            single { PlayerPreferences(get()) }
        }
    }
}
