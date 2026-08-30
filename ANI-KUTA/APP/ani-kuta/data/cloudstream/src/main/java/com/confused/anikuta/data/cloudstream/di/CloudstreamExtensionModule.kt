package com.confused.anikuta.data.cloudstream.di

import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.content.CloudstreamBrowseCache
import com.confused.anikuta.data.cloudstream.content.CloudstreamContentRepository
import com.confused.anikuta.data.cloudstream.content.CloudstreamSourceRegistry
import com.confused.anikuta.data.cloudstream.installer.CloudstreamPluginInstaller
import com.confused.anikuta.data.cloudstream.loader.CloudstreamPluginLoader
import com.confused.anikuta.data.cloudstream.repo.CloudstreamPluginStore
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoApi
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
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

    // Session 3 — the provider-EXECUTION layer (browse/search/load over the
    // trusted plugins' live MainAPI providers; consumed by the search page's
    // CloudStream branches + the CS content details screen).
    // Task 48: + the browse cache (memory + disk, stale-while-revalidate) so
    // the search page renders the CloudStream feed INSTANTLY on open.
    single { CloudstreamBrowseCache(androidContext()) }
    single { CloudstreamContentRepository(get(), get()) }

    // Task 52 (round 12 — the playback port): the loadLinks orchestration.
    // Stateless singleton (its link cache lives inside); consumed by the CS
    // watch screen's ViewModel.
    single { com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver() }

    // Task 45 — the SOURCE BRIDGE registry: every trusted provider published as
    // an aniyomi AnimeHttpSource under a stable synthetic id. The app wires it
    // into ExtensionManager.setExternalSources so CloudStream results open the
    // STANDARD details screen (same page as aniyomi extensions).
    single { CloudstreamSourceRegistry(get<CloudstreamContentRepository>().sources) }
}
