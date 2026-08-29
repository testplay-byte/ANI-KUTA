package com.confused.anikuta

import android.app.Application
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.core.activitytracker.activityTrackerModule
import com.confused.anikuta.core.ads.di.adsModule  // D-272: smart-link ad system
import com.confused.anikuta.core.anilist.di.anilistModule
import com.confused.anikuta.core.appupdate.di.appUpdateModule
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
import com.confused.anikuta.data.cloudstream.di.cloudstreamModule
import com.confused.anikuta.feature.cloudstreamcontent.di.cloudstreamContentModule
import com.confused.anikuta.feature.animebrowse.di.browseModule
import com.confused.anikuta.feature.animedetails.di.detailsModule
import com.confused.anikuta.feature.animelibrary.di.libraryModule
import com.confused.anikuta.feature.animesearch.di.searchModule
import com.confused.anikuta.feature.animehistory.di.historyModule
import com.confused.anikuta.core.updates.di.updatesModule
import com.confused.anikuta.core.schedule.di.scheduleModule
import com.confused.anikuta.core.ratings.di.ratingsModule
import com.confused.anikuta.core.notifications.di.notificationsModule
import com.confused.anikuta.core.playbackcache.di.playbackCacheModule
import com.confused.anikuta.feature.updates.di.updatesFeatureModule
import com.confused.anikuta.feature.download.di.downloadFeatureModule
import com.confused.anikuta.settings.ThemePreferences
import com.confused.anikuta.settings.NotificationsSettingsViewModel
import com.confused.anikuta.settings.NotificationsLibraryViewModel
import com.confused.anikuta.settings.VideoCachingViewModel
import com.confused.anikuta.profile.ProfileViewModel
import eu.kanade.tachiyomi.animesource.ExtensionAppHolder
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.fullType
import java.util.UUID

/**
 * Task 44: extends CloudStreamApp (was Application) — the CloudStream compat
 * layer's app holder. super.onCreate() publishes this instance as
 * CloudStreamApp.context, which (a) plugins using getKey/setKey resolve and
 * (b) the Cloudflare challenge solver uses as its fallback WebView context.
 * CloudStreamApp adds nothing else to Application behavior.
 */
class AnikutaApp : com.lagradost.cloudstream3.CloudStreamApp(),
    androidx.work.Configuration.Provider {

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
                historyModule,
                updatesModule,
                updatesFeatureModule,
                scheduleModule,
                ratingsModule,
                notificationsModule,
                activityTrackerModule,
                appUpdateModule,
                adsModule,  // D-272: smart-link ad system
                extensionModule,
                cloudstreamModule,  // Task 41: CloudStream extension system (doc 23)
                cloudstreamContentModule,  // Session 3: CloudStream content screens
                playerModule,
                videoResolverModule,
                downloadModule,
                downloadFeatureModule,
                metadataModule,
                trackerAniListModule,
                watchProgressModule,
                smartMatcherModule,
                contentModule,
                dataCacheModule,
                playbackCacheModule,
                appModule,
            )
            // Debug-only Koin modules (debug-bubble, etc.). No-op in release
            // builds — debugKoinModules() returns emptyList() there.
            // (Phase DB-1 — D-163.)
            modules(debugKoinModules())
        }

        // DB-4: wire debug-only integrations (Logger appender → DebugLogBuffer).
        // No-op in release builds (initDebugIntegrations() is a no-op there).
        // Must run AFTER Koin starts so DebugLogBuffer is resolvable.
        try {
            initDebugIntegrations()
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Failed to init debug integrations" }
        }

        // Seed lookup tables + Default library category (idempotent — INSERT OR IGNORE).
        // Must run AFTER Koin is started so ContentRepository is available.
        try {
            org.koin.core.context.GlobalContext.get().get<ContentSeeder>().seed()
            Logger.i("AnikutaApp") { "Content defaults seeded" }
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Failed to seed content defaults" }
        }

        // D-192: Track APP_OPEN event (internal activity tracker).
        try {
            org.koin.core.context.GlobalContext.get().get<com.confused.anikuta.core.activitytracker.ActivityTracker>().track(
                eventType = com.confused.anikuta.core.activitytracker.ActivityEventType.APP_OPEN,
                route = "app",
            )
        } catch (e: Exception) {
            Logger.w("AnikutaApp") { "Failed to track APP_OPEN: ${e.message}" }
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

        // D-193 Phase 4: Schedule the UpdateCheckWorker using the configurable UpdateScheduler.
        // Reads UpdatePreferences (mode + interval) + schedules/cancels accordingly.
        try {
            org.koin.core.context.GlobalContext.get().get<com.confused.anikuta.core.updates.UpdateScheduler>().reschedule()
        } catch (e: Exception) {
            Logger.e("AnikutaApp", e) { "Failed to schedule UpdateCheckWorker" }
        }

        // D-151-fix: Run the download scanner on app startup. This reconciles the
        // on-disk .data.json files with the DB (write-back of latest metadata) +
        // discovers any downloaded episodes that aren't in the DB (e.g. from a
        // fresh install where the DB was wiped but the SAF folder persists).
        // Runs on a background scope — non-blocking, best-effort.
        try {
            val downloadManager = org.koin.core.context.GlobalContext.get()
                .get<com.confused.anikuta.core.download.DownloadManager>()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
            ).launch {
                downloadManager.requestFolderRescan()
                Logger.i("AnikutaApp") { "Download folder scan completed (data.json reconciliation)" }
            }
        } catch (e: Exception) {
            Logger.w("AnikutaApp") { "Failed to run download folder scan: ${e.message}" }
        }

        // Video caching (test-feature branch): pre-start the cache proxy server
        // (avoids a bind() on the main thread at first play) + startup maintenance
        // (stale sweep + LRU eviction). Background scope — non-blocking, best-effort.
        try {
            val playbackCacheManager = org.koin.core.context.GlobalContext.get()
                .get<com.confused.anikuta.core.playbackcache.PlaybackCacheManager>()
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
            ).launch {
                playbackCacheManager.start()
            }
        } catch (e: Exception) {
            Logger.w("AnikutaApp") { "Failed to start playback cache: ${e.message}" }
        }
    }

    // Phase UP: Configuration.Provider for WorkManager (disables default initializer).
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()

    companion object {
        // App-level infrastructure DI
        private val appModule = module {
            // Network
            // DB-5: wrapDebugOkHttp adds the DebugNetworkStats interceptor in
            // debug builds; no-op (identity) in release builds.
            single<OkHttpClient> { wrapDebugOkHttp(HttpClientFactory().create()) }
            // D.0.4: Download HTTP client — long timeouts, separate connection pool.
            single<OkHttpClient>(HttpClientFactory.DOWNLOAD) { wrapDebugOkHttp(HttpClientFactory().createDownloadClient()) }

            // D.4: Coil ImageLoader with 500MB disk cache (persistent)
            single { ImageLoaderFactory.create(get(), get()) }

            // D-223: Cover color extractor (Palette API) for adaptive theming.
            single { com.confused.anikuta.core.designsystem.color.CoverColorExtractor(get(), get()) }

            // Database
            // DB-9: wrapDebugSqlDriver wraps the driver with DebugSqlDriverWrapper
            // in debug builds (tracks DB writes for the DB Activity view); no-op
            // (identity) in release builds.
            single<SqlDriver> { wrapDebugSqlDriver(DatabaseDriverFactory(get()).create()) }
            single<AnikutaDatabase> { AnikutaDatabase(get()) }

            // Phase SC-2: bind ScheduleStore as ActualReleaseUpdater (breaks the circular
            // dependency between :core:updates + :core:schedule).
            single<com.confused.anikuta.core.updates.ActualReleaseUpdater> {
                get<com.confused.anikuta.core.schedule.ScheduleStore>()
            }

            // D-193 Phase 9: bind ScheduleEngine as ScheduleRefresher (breaks circular dep).
            single<com.confused.anikuta.core.updates.ScheduleRefresher> {
                com.confused.anikuta.core.updates.ScheduleRefresher {
                    get<com.confused.anikuta.core.schedule.ScheduleEngine>().fetchSchedule()
                }
            }

            // D-193 Phase 9: bind NotificationManager as NotificationSender (breaks circular dep).
            // D-198: episode_number Long→Double migration (SQLDelight maps REAL→Double).
            single<com.confused.anikuta.core.updates.NotificationSender> {
                com.confused.anikuta.core.updates.NotificationSender { mainId, episodeNumber, audioVariant, triggerType ->
                    get<com.confused.anikuta.core.notifications.NotificationManager>().postNotification(
                        mainId, episodeNumber, audioVariant, triggerType,
                    )
                }
            }

            // Session ID (for activity tracking — new per process restart)
            single(named("sessionId")) { UUID.randomUUID().toString() }

            // Preferences
            single { PreferenceStore(get()) }
            single { AppPreferences(get()) }
            single { AutoLinkPreferences(get()) }
            single { PlayerPreferences(get()) }
            // D-230: Episode list customization (filters, sort, grouping, thumbnail fallback).
            single { com.confused.anikuta.core.preferences.EpisodeListPreferences(get()) }
            single { ThemePreferences(get()) }
            single { com.confused.anikuta.core.preferences.NotificationPreferences(get()) }
            // D-192: SettingsRepository for backup/restore (mirrors PreferenceStore to app_settings table)
            single { com.confused.anikuta.core.preferences.SettingsRepository(get()) }
            // D-193 Phase 3: UpdatePreferences for the updates settings
            single { com.confused.anikuta.core.preferences.UpdatePreferences(get()) }

            // ViewModels (app-level)
            viewModelOf(::NotificationsSettingsViewModel)
            viewModelOf(::NotificationsLibraryViewModel)
            viewModelOf(::VideoCachingViewModel) // Video caching settings (test-feature branch)
            viewModelOf(::ProfileViewModel) // Profile page

            // D.2: Download orchestrator + re-resolver (bridges :core:video-resolver + :core:download)
            single { com.confused.anikuta.download.ReResolver(get<com.confused.anikuta.core.videoresolver.VideoResolver>()) }
            // D-149-fix: adapter that bridges HttpDownloader.ReResolver (local fun interface
            // in :core:download) to the app-class ReResolver above. DownloadModule.kt resolves
            // this via getOrNull<HttpDownloader.ReResolver>().
            single<com.confused.anikuta.core.download.HttpDownloader.ReResolver> {
                com.confused.anikuta.download.ReResolverAdapter(get(), get())
            }
            single {
                com.confused.anikuta.download.DownloadOrchestrator(
                    get<com.confused.anikuta.core.videoresolver.VideoResolver>(),
                    get<com.confused.anikuta.core.download.DownloadManager>(),
                    get<com.confused.anikuta.core.download.DownloadPreferences>(),
                )
            }
        }
    }
}
