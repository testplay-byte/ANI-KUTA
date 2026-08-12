package com.confused.anikuta.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.network.HttpClientFactory
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for `:core:download` — the download engine.
 *
 * D.1.13 + 12-di-wiring.md §11.1: replaces the D.0 placeholder `downloadModule`
 * with the full bindings for ALL engine components.
 *
 * Bindings:
 *  - [DownloadPreferences] (backed by the reactive [PreferenceStore])
 *  - [ServerDiscoveryStore]
 *  - [DownloadStore] (SQLDelight adapter)
 *  - [TempDownloadCache] (calls `cleanupStale()` on creation)
 *  - [DownloadStorageProvider] (the NEW SAF + `data.json` system)
 *  - [DownloadScanner] (scan-on-startup engine)
 *  - [HlsDownloader]
 *  - [HttpDownloader] (the router — `reResolver = null` for D.1; D.2 wires [HttpDownloader.ReResolver])
 *  - [DownloadNotificationManager] (two channels + thumbnails + actions)
 *  - [CoroutineScope] (`named("downloadScope")` — survives app-backgrounding)
 *  - [DownloadQueue] (SQLDelight-backed + Mutex + reactive concurrency)
 *  - [DownloadManager] → [DefaultDownloadManager]
 *
 * The DOWNLOAD-qualified [OkHttpClient] is provided by `:app`'s `appModule` (already
 * registered as `single<OkHttpClient>(HttpClientFactory.DOWNLOAD)`). We `get` it
 * here — Koin resolves lazily at `get()` time.
 *
 * CORE_RULES §20: all operations logged via [DownloadLogger].
 */
val downloadModule = module {

    // ── Preferences (backed by the reactive PreferenceStore) ─────────────────
    single { DownloadPreferences(get<PreferenceStore>()) }
    single { ServerDiscoveryStore() }

    // ── Database adapter (SQLDelight-backed) ─────────────────────────────────
    single { DownloadStore(get()) }

    // ── Storage (the NEW SAF + data.json system) ─────────────────────────────
    // TempDownloadCache — calls cleanupStale() on creation.
    single { TempDownloadCache(androidContext()) }
    single {
        DownloadStorageProvider(
            context = androidContext(),
            preferences = get(),
            okHttpClient = get<OkHttpClient>(HttpClientFactory.DOWNLOAD),
        )
    }
    single {
        DownloadScanner(
            context = androidContext(),
            storage = get(),
            store = get(),
            contentRepository = get<ContentRepository>(),
        )
    }

    // ── The downloaders (modular — see 05-downloaders.md §11.1) ──────────────
    single {
        HlsDownloader(
            client = get<OkHttpClient>(HttpClientFactory.DOWNLOAD),
            tempCache = get(),
            preferences = get(),
        )
    }
    single {
        HttpDownloader(
            client = get<OkHttpClient>(HttpClientFactory.DOWNLOAD),
            tempCache = get(),
            storage = get(),
            hlsDownloader = get(),
            store = get(),
            preferences = get(),
            // D.1: reResolver is null (the proxy-churn fix is wired in D.2 via the
            // :app module's downloadAppModule). The HttpDownloader's catch block
            // handles `reResolver == null` by falling through to the standard error.
            reResolver = null,
        )
    }

    // ── Notifications + foreground service (the NEW design — see 06-notifications-foreground-service.md §13) ──
    single {
        DownloadNotificationManager(
            context = androidContext(),
            storage = get(),
            okHttpClient = get<OkHttpClient>(HttpClientFactory.DOWNLOAD),
        )
    }

    // ── The private download scope (survives app-backgrounding) ──────────────
    single(named("downloadScope")) {
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                DownloadLogger.e(e) { "Uncaught exception in download scope (suppressed)" }
            },
        )
    }

    // ── Queue + Manager (SQLDelight-backed + Mutex — see 02-queue-management.md §13) ──
    single {
        val notifScope = get<CoroutineScope>(named("downloadScope"))
        DownloadQueue(
            store = get(),
            preferences = get(),
            scope = get(named("downloadScope")),
            downloader = get(),
            notifier = get(),
            connectivityCheck = { wifiOnlyCheck(androidContext(), get()) },
            onTaskCompleted = { task ->
                // Post the completion notification (with sound + thumbnail). The
                // callback is `suspend` — runs on the queue's scope (Dispatchers.IO).
                notifScope.launch { get<DownloadNotificationManager>().notifyCompleted(task) }
            },
            onTaskError = { task ->
                // Post the error notification (silent + thumbnail).
                notifScope.launch { get<DownloadNotificationManager>().notifyError(task) }
            },
        )
    }
    single<DownloadManager> {
        DefaultDownloadManager(
            context = androidContext(),
            queue = get(),
            store = get(),
            storage = get(),
            scanner = get(),
            preferences = get(),
            notifier = get(),
            activityTracker = get(),
            scope = get(named("downloadScope")),
        )
    }
}

// ── Module-private helpers ──────────────────────────────────────────────────

/**
 * Wi-Fi-only enforcement — used by [DownloadQueue.connectivityCheck].
 *
 * Returns `true` if:
 *  - `wifiOnly` is OFF (any network is allowed), OR
 *  - `wifiOnly` is ON AND the active network is Wi-Fi (or ethernet).
 *
 * Fails open on error (better to attempt the download than to silently stall).
 */
private fun wifiOnlyCheck(context: Context, preferences: DownloadPreferences): Boolean {
    if (!preferences.wifiOnly.get()) return true
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } catch (e: Exception) {
        DownloadLogger.w { "wifiOnlyCheck — connectivity check failed (fail-open): ${e.message}" }
        true
    }
}
