package com.confused.anikuta

import android.app.Application
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.activitytracker.activityTrackerModule
import com.confused.anikuta.core.anilist.di.anilistModule
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.database.DatabaseDriverFactory
import com.confused.anikuta.core.network.HttpClientFactory
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.download.downloadModule
import com.confused.anikuta.core.metadata.metadataModule
import com.confused.anikuta.core.player.playerModule
import com.confused.anikuta.core.content.contentModule
import com.confused.anikuta.core.content.ContentSeeder
import com.confused.anikuta.core.datacache.dataCacheModule
import com.confused.anikuta.core.smartmatcher.smartMatcherModule
import com.confused.anikuta.core.trackeranilist.trackerAniListModule
import com.confused.anikuta.core.videoresolver.videoResolverModule
import com.confused.anikuta.core.watchprogress.watchProgressModule
import com.confused.anikuta.data.extension.extensionModule
import com.confused.anikuta.feature.animebrowse.di.browseModule
import com.confused.anikuta.feature.animedetails.di.detailsModule
import com.confused.anikuta.feature.animelibrary.di.libraryModule
import com.confused.anikuta.feature.animesearch.di.searchModule
import com.confused.anikuta.settings.ThemePreferences
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.fullType
import java.util.UUID

class AnikutaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── Global crash handler (installed FIRST, before anything else) ──
        // Catches uncaught exceptions on any thread → writes crash report →
        // launches ErrorActivity with copyable logs + restart/close buttons.
        Thread.setDefaultUncaughtExceptionHandler(
            com.confused.anikuta.error.AnikutaCrashHandler(this)
        )

        // CORE_RULES §20: Logger init with :app's BuildConfig.DEBUG
        Logger.setEnabled(BuildConfig.DEBUG)

        // ── Extension compat setup (BEFORE Koin, BEFORE any extension loads) ──
        // Extensions use Injekt (a service locator) to resolve NetworkHelper,
        // Application, Context, and Json. Without these registrations, extensions
        // crash with "No registered instance on Factory for type class ...NetworkHelper".
        // D-027: Aniyomi binary-compat contract.
        ExtensionAppHolder.init(this)
        try {
            Injekt.addSingleton(fullType<Application>(), this)
            Injekt.addSingleton(fullType<Context>(), this)
            Injekt.addSingleton(fullType<NetworkHelper>(), NetworkHelper(this))
            Injekt.addSingletonFactory(fullType<Json>()) {
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            }
            Logger.i("AnikutaApp") { "Injekt: Application + Context + NetworkHelper + Json registered" }
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Injekt: failed to register singletons" }
        }

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
                watchProgressModule,
                smartMatcherModule,
                contentModule,
                dataCacheModule,
                appModule,
            )
        }

        // Seed lookup tables + Default library category (idempotent — INSERT OR IGNORE).
        // Must run AFTER Koin is started so ContentRepository is available.
        try {
            org.koin.core.context.GlobalContext.get().get<ContentSeeder>().seed()
            Logger.i("AnikutaApp") { "Content defaults seeded" }
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Failed to seed content defaults" }
        }

        // D.4: Set the Coil ImageLoader as the singleton (AFTER Koin starts).
        try {
            coil3.SingletonImageLoader.setSafe {
                org.koin.core.context.GlobalContext.get().get<coil3.ImageLoader>()
            }
            Logger.i("AnikutaApp") { "Coil ImageLoader set as singleton (500MB disk cache)" }
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Failed to set Coil ImageLoader" }
        }
    }

    companion object {
        // App-level infrastructure DI
        private val appModule = module {
            // Network
            single<OkHttpClient> { HttpClientFactory().create() }

            // D.4: Coil ImageLoader with 500MB disk cache (persistent)
            single { ImageLoaderFactory.create(get(), get()) }

            // Database
            single<SqlDriver> { DatabaseDriverFactory(get()).create() }
            single<AnikutaDatabase> { AnikutaDatabase(get()) }

            // Session ID (for activity tracking — new per process restart)
            single(named("sessionId")) { UUID.randomUUID().toString() }

            // Preferences
            single { PreferenceStore(get()) }
            single { AppPreferences(get()) }
            single { AutoLinkPreferences(get()) }
            single { PlayerPreferences(get()) }
            single { ThemePreferences(get()) }
        }
    }
}
