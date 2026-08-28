package com.confused.anikuta.core.playbackcache.di

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.network.HttpClientFactory
import com.confused.anikuta.core.playbackcache.PlaybackCacheManager
import com.confused.anikuta.core.playbackcache.PlaybackCachePreferences
import com.confused.anikuta.core.playbackcache.PlaybackCacheStore
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Video playback cache DI (Video Caching plan — PLAN.md Part A).
 *
 * The upstream client is the DOWNLOAD-qualified OkHttpClient (60s read timeout —
 * healthy streams deliver continuously; a dead socket surfaces within 60s).
 */
val playbackCacheModule = module {

    single { PlaybackCachePreferences(get<PreferenceStore>()) }

    single { PlaybackCacheStore(get()) }

    // Named maintenance scope (DownloadModule's downloadScope pattern).
    single(named("playbackCacheScope")) {
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                Logger.e("Anikuta:Core:PlaybackCache", e) { "Uncaught exception in playback cache scope (suppressed)" }
            },
        )
    }

    single {
        PlaybackCacheManager(
            context = androidContext(),
            store = get(),
            preferences = get(),
            client = get<OkHttpClient>(HttpClientFactory.DOWNLOAD),
            scope = get(named("playbackCacheScope")),
        )
    }
}
