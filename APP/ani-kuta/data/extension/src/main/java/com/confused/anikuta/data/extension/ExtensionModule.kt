package com.confused.anikuta.data.extension

import com.confused.anikuta.data.extension.api.AnimeExtensionApi
import com.confused.anikuta.data.extension.installer.ExtensionInstaller
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.repo.ExtensionRepoApi
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import com.confused.anikuta.data.extension.trust.TrustService
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * DI module for the extension system.
 *
 * Ported from the old project. All bindings are singletons (the manager owns a
 * CoroutineScope, so it must be a singleton).
 *
 * Named qualifiers:
 * - `named("extensionRepo")` — OkHttpClient with 30s/60s timeouts for repo API.
 */
val extensionModule = module {

    // ── HTTP client for repo index fetching + APK downloads ──
    single<OkHttpClient>(named("extensionRepo")) {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // ── Trust ──
    single { TrustService(get()) }

    // ── Repo layer ──
    single { ExtensionRepoRepository(get()) }
    single { ExtensionRepoApi(get(named("extensionRepo"))) }

    // ── API (orchestrator over repos) ──
    single { AnimeExtensionApi(get(), get()) }

    // ── Installer ──
    single { ExtensionInstaller(get(), get(named("extensionRepo"))) }

    // ── Manager (the public façade) ──
    single { ExtensionManager(get(), get(), get(), get(), get(named("extensionRepo"))) }
}
