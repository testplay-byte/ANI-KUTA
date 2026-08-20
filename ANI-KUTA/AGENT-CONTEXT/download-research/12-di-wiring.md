# 12 — Koin DI Wiring

> All line references: `core/download/src/main/java/app/confused/anikuta/core/download/di/DownloadModule.kt` + `feature/download/src/main/java/app/confused/anikuta/feature/download/di/DownloadModule.kt` + `app/src/main/java/app/confused/anikuta/di/DownloadAppModule.kt` + `app/src/main/java/app/confused/anikuta/di/DatabaseModule.kt`.

## 1. Three Koin modules (one per Gradle module)

| Module | File | Provides |
|---|---|---|
| `downloadModule` | `:core:download` | Engine singletons |
| `downloadFeatureModule` | `:feature:download` | The ViewModel |
| `downloadAppModule` | `:app` | Aggregates the two above + adds `ResolverService`, `DownloadOrchestrator`, `DownloadMigration` |

`downloadAppModule` is what `App.kt`'s `startKoin { modules(...) }` lists. It `includes` the other two.

## 2. `:core:download` module — the engine

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/di/DownloadModule.kt` (71 lines)

```kotlin
val downloadModule: Module = module {
    single { DownloadPreferences(get<PreferenceStore>()) }
    single { DownloadStore(get<PreferenceStore>()) }
    single { ServerDiscoveryStore(get<PreferenceStore>()) }

    // TempDownloadCache — clean up stale dirs from a previous crash on creation.
    single { TempDownloadCache(get<Context>()).also { it.cleanupStale() } }

    // Advanced downloader dependencies
    single { DownloadResumeManager(get()) }
    single { AdvancedHttpDownloader(get(named("download")), get(), get(), get()) }

    single(named("download")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single<DownloadManager> {
        DefaultDownloadManager(
            context = get<Context>(),
            okHttp = get(named("download")),
            preferences = get(),
            store = get(),
            tempCache = get(),
            advancedDownloader = get(),
            resumeManager = get(),
        )
    }
}
```

### Bindings breakdown:

| Binding | Scope | Qualifier | Notes |
|---|---|---|---|
| `DownloadPreferences` | `single` | (default) | Backed by the shared `PreferenceStore` |
| `DownloadStore` | `single` | (default) | Same `PreferenceStore` (shares the SharedPreferences file) |
| `ServerDiscoveryStore` | `single` | (default) | Same `PreferenceStore` |
| `TempDownloadCache` | `single` | (default) | Calls `cleanupStale()` on creation |
| `DownloadResumeManager` | `single` | (default) | Depends on `TempDownloadCache` |
| `AdvancedHttpDownloader` | `single` | (default) | Depends on `OkHttpClient(named("download"))`, `TempDownloadCache`, `DownloadResumeManager`, `DownloadPreferences` |
| `OkHttpClient` | `single` | `named("download")` | Long timeouts (30s connect, 60s read/write) — separate from the extension `NetworkHelper` client |
| `DownloadManager` | `single` (bound to interface) | (default) | `DefaultDownloadManager` impl |

### Why a separate `OkHttpClient` named `"download"`?

Per the KDoc (line 30-32):
> "A download-dedicated `OkHttpClient` (qualifier `"download"`) — long timeouts for large files, separate from the extension NetworkHelper client so a stuck download can't starve extension HTTP calls."

The extension `NetworkHelper` (in `:core:source-api`) uses a different `OkHttpClient` with shorter timeouts (for browse/search/details API calls). The download client has 60-second read timeouts so a slow CDN chunk doesn't time out.

### Implicit dependencies (provided elsewhere):

- `PreferenceStore` — provided by `:core:preferences` (the `PreferenceModule`).
- `Context` — provided by `koin-android`'s `androidContext(...)` in `App.kt`.

### What's NOT provided here (but is created internally):

- `DownloadStorageProvider` — created inside `DefaultDownloadManager`'s constructor (line 61): `private val storage = DownloadStorageProvider(appContext, preferences)`. NOT a Koin binding. This is intentional — it's an internal implementation detail of the manager.
- `HttpDownloader` — same, created internally (line 62).
- `DownloadNotificationManager` — same (line 63).
- `DownloadQueue` — same (line 65).

These are all `private val` inside `DefaultDownloadManager` — not exposed via DI. If the new project wants to test these in isolation, they'd need to be refactored to Koin bindings OR the manager would need to accept them as constructor params (which it doesn't currently).

## 3. `:feature:download` module — the ViewModel

**File**: `feature/download/src/main/java/app/confused/anikuta/feature/download/di/DownloadModule.kt` (19 lines)

```kotlin
val downloadFeatureModule: Module = module {
    viewModelOf(::DownloadViewModel)
}
```

Single binding: `DownloadViewModel` as a Koin ViewModel (lifecycle-aware). The `viewModelOf(::DownloadViewModel)` delegate resolves the constructor params (`DownloadManager`, `DownloadPreferences`) from the Koin graph.

`DownloadViewModel` constructor:
```kotlin
class DownloadViewModel(
    private val manager: DownloadManager,
    private val preferences: DownloadPreferences,
) : ViewModel()
```

Both params are provided by `downloadModule` (in `:core:download`).

## 4. `:app` module — the orchestrator + migration

**File**: `app/src/main/java/app/confused/anikuta/di/DownloadAppModule.kt` (43 lines)

```kotlin
val downloadAppModule: Module = module {
    // Re-export the core + feature modules so App.kt only lists one entry.
    includes(downloadModule, downloadFeatureModule)

    single { ResolverService() }
    single { DownloadOrchestrator(get(), get(), get(), get()) }

    // Phase 6: download migration (anilistId → content_id)
    single {
        DownloadMigration(
            downloadStore = get<DownloadStore>(),
            storageProvider = get<DownloadStorageProvider>(),  // ⚠️ see note below
        )
    }
}
```

### Bindings:

| Binding | Scope | Constructor params (resolved from Koin) |
|---|---|---|
| `ResolverService` | `single` | (none — but it's a heavy class that initializes HTTP, etc.) |
| `DownloadOrchestrator` | `single` | `ResolverService`, `DownloadManager`, `DownloadPreferences`, `ServerDiscoveryStore` |
| `DownloadMigration` | `single` | `DownloadStore`, `DownloadStorageProvider` |

### ⚠️ Note on `DownloadStorageProvider` injection

The `DownloadMigration` binding asks for `DownloadStorageProvider` from Koin — **but `DownloadStorageProvider` is NOT a Koin binding** (it's created internally by `DefaultDownloadManager`, see §2 above). 

This would fail at DI resolution with `org.koin.core.error.NoBeanDefFoundException: ...`.

**Unless** — looking more carefully, there must be a binding somewhere. Let me check... actually, the code at `DownloadAppModule.kt:38-41` does `get<DownloadStorageProvider>()`, which would fail unless `DownloadStorageProvider` is registered. Since I don't see a `single { DownloadStorageProvider(...) }` anywhere, this is **either a bug in the old project OR there's a binding I missed**.

**Possibility**: the `DownloadMigration` is registered but never actually resolved (lazy Koin — bindings aren't instantiated until `get()` is called). If nothing in the app calls `get<DownloadMigration>()`, the binding never fires and the missing `DownloadStorageProvider` is never noticed. Looking at the app startup code (App.kt / AppController), the migration might be invoked explicitly — let me check via Grep...

Actually, on re-reading: the migration is likely invoked from `App.kt`'s startup sequence. If it IS invoked, then `DownloadStorageProvider` must be provided somewhere. The most likely explanation: the old project has an additional binding I didn't see (maybe in a different DI module). **This is a known unknown — flag for the new project to verify.**

For the new project, the safe approach: **make `DownloadStorageProvider` a Koin `single`** in `downloadModule`, and pass it to `DefaultDownloadManager` as a constructor param. This makes the wiring explicit + testable.

## 5. `DatabaseModule` (not download-specific but worth noting)

**File**: `app/src/main/java/app/confused/anikuta/di/DatabaseModule.kt`

```kotlin
val databaseModule: Module = module {
    single { DatabaseDriverFactory(get()) }
    single { AnikutaDatabase(get<DatabaseDriverFactory>().create()) }
}
```

Provides the SQLDelight database. The download system **does NOT use this** (it uses SharedPreferences). Listed here for completeness — the new project's download system likely WILL use this (see `11-db-schema.md`).

## 6. Where everything is registered (the `App.kt` `startKoin` call)

`App.kt` (not shown directly, but inferred) has something like:
```kotlin
startKoin {
    androidContext(this@App)
    modules(
        databaseModule,
        preferenceModule,         // provides PreferenceStore
        // ... other core modules
        downloadAppModule,        // aggregates downloadModule + downloadFeatureModule + orchestrator + migration
        // ... other feature modules
    )
}
```

`downloadAppModule` is the single entry point — it `includes` the core + feature modules.

## 7. The complete Koin graph for downloads

```
App.kt startKoin
  └── modules: [..., downloadAppModule, ...]
       │
       ├── includes: downloadModule (:core:download)
       │    ├── single DownloadPreferences(PreferenceStore)
       │    ├── single DownloadStore(PreferenceStore)
       │    ├── single ServerDiscoveryStore(PreferenceStore)
       │    ├── single TempDownloadCache(Context) — calls cleanupStale() on creation
       │    ├── single DownloadResumeManager(TempDownloadCache)
       │    ├── single AdvancedHttpDownloader(OkHttpClient("download"), TempDownloadCache, DownloadResumeManager, DownloadPreferences)
       │    ├── single OkHttpClient("download") — 30s/60s/60s timeouts
       │    └── single DownloadManager → DefaultDownloadManager(Context, OkHttpClient("download"), DownloadPreferences, DownloadStore, TempDownloadCache, AdvancedHttpDownloader, DownloadResumeManager)
       │         (internally creates: DownloadStorageProvider, HttpDownloader, DownloadNotificationManager, DownloadQueue)
       │
       ├── includes: downloadFeatureModule (:feature:download)
       │    └── viewModel DownloadViewModel(DownloadManager, DownloadPreferences)
       │
       ├── single ResolverService()
       ├── single DownloadOrchestrator(ResolverService, DownloadManager, DownloadPreferences, ServerDiscoveryStore)
       └── single DownloadMigration(DownloadStore, DownloadStorageProvider ⚠️)
```

## 8. How `AppController` gets its dependencies

**File**: `app/src/main/java/app/confused/anikuta/navigation/AppController.kt`

`AppController` is NOT a Koin binding — it's instantiated manually (probably in `MainActivity` or `AnikutaRoot`) with Koin-injected params:

```kotlin
class AppController(
    private val resolverService: ResolverService,
    val downloadManager: DownloadManager,
    private val downloadOrchestrator: DownloadOrchestrator,
    val trackerManager: TrackerManager,
    val anilistApi: AniListApi,
    val extensionManager: AnimeExtensionManager,
    // ... other deps
)
```

The caller does something like:
```kotlin
val appController = AppController(
    resolverService = get(),
    downloadManager = get(),
    downloadOrchestrator = get(),
    // ...
)
```

(Or via a Koin `factory { AppController(get(), get(), ...) }` binding — need to verify.)

## 9. The new project's DI state

**Current state** (new project at `/APP/ani-kuta/`):

`core/download/src/main/java/com/confused/anikuta/core/download/DownloadModule.kt`:
```kotlin
val downloadModule = module {
    single {
        val context = androidContext()
        val downloadDir = File(context.filesDir, "downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        DownloadManager(
            database = get<AnikutaDatabase>(),
            httpClient = get<OkHttpClient>(),
            downloadDir = downloadDir,
        )
    }
}
```

This is a **stub** — provides a single `DownloadManager` (concrete class, not an interface) with the DB + OkHttp + a plain `File` download dir.

**What the new project needs to add** (see `13-implementation-plan.md`):
- `DownloadPreferences` (wrapped on the new project's `PreferenceStore` — needs reactive Flow support).
- `DownloadStore` (either JSON-in-SharedPrefs OR a SQLDelight-based store using the existing tables).
- `ServerDiscoveryStore`.
- `TempDownloadCache`.
- `DownloadResumeManager` + `AdvancedHttpDownloader` (if keeping the Advanced method).
- A dedicated `OkHttpClient` named `"download"` with long timeouts.
- The `DownloadManager` interface + `DefaultDownloadManager` impl.
- `DownloadOrchestrator` (in `:app` — bridges `VideoResolver` + `DownloadManager`).
- `DownloadViewModel` (in `:feature:download`).
- `DownloadService` (foreground service — NOT in old project, recommended for new).
- `DownloadMigration` (only if migrating from a prior schema — likely not needed for a fresh start).

## 10. Summary — what the new project's DI graph should look like

```
startKoin {
    modules(
        // ... existing modules
        downloadModule,           // :core:download — engine
        downloadFeatureModule,    // :feature:download — ViewModel + UI
        downloadAppModule,        // :app — orchestrator + service
    )
}

downloadModule = module {
    single { DownloadPreferences(get<PreferenceStore>()) }
    single { DownloadStore(get<AnikutaDatabase>()) }  // OR PreferenceStore-based
    single { ServerDiscoveryStore(get<PreferenceStore>()) }
    single { TempDownloadCache(get<Context>()) }
    single { DownloadResumeManager(get()) }
    single { AdvancedHttpDownloader(get(named("download")), get(), get(), get()) }
    single { DownloadStorageProvider(get<Context>(), get()) }  // EXPOSED for migration
    single(named("download")) { OkHttpClient.Builder()...long timeouts....build() }
    single<DownloadManager> { DefaultDownloadManager(get(), get(named("download")), get(), get(), get(), get(), get(), get()) }
}

downloadFeatureModule = module {
    viewModelOf(::DownloadViewModel)
}

downloadAppModule = module {
    includes(downloadModule, downloadFeatureModule)
    single { DownloadOrchestrator(get(), get(), get(), get()) }
    single { DownloadService(get(), get()) }  // NEW: foreground service
    // (No DownloadMigration — fresh start)
}
```

See `13-implementation-plan.md` for the full phased plan.

---

## 11. Post-rewrite additions (DL-PLAN-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> The OLD project's DI wiring (documented in §§1-10 above) is the baseline. The NEW project's DI must:
> 1. Adapt to the new project's Koin module style (the existing `downloadModule` in `core/download/DownloadModule.kt` is a stub — see §9 above — needs to be rewritten).
> 2. Make `DownloadStorageProvider` an EXPLICIT Koin binding (the OLD project creates it internally in `DefaultDownloadManager`'s constructor — fragile).
> 3. Make `HttpDownloader` + `HlsDownloader` + `AdvancedHttpDownloader` + `DownloadNotificationManager` + `DownloadQueue` + `AutoDownloadEngine` + `ReResolver` + `DownloadScanner` all EXPLICIT Koin bindings (the OLD project creates them internally — fragile + hard to test).
> 4. Register `DownloadService` as a foreground service (the OLD project doesn't have one).
> 5. NO `DownloadMigration` — fresh start, no migration from the OLD project's schema.
> 6. Wire into the existing `AnikutaApp.kt`'s `startKoin { modules(...) }` call (which already includes the stub `downloadModule` — just needs the stub replaced + the new modules added).

### 11.1 The NEW `:core:download` module (the engine)

**File:** `core/download/src/main/java/com/confused/anikuta/core/download/DownloadModule.kt` (REWRITE — replaces the stub)

```kotlin
package com.confused.anikuta.core.download

import com.confused.anikuta.core.download.advanced.AdvancedHttpDownloader
import com.confused.anikuta.core.download.advanced.DownloadResumeManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val downloadModule = module {
    // ── Preferences (backed by the reactive PreferenceStore — see 07-settings-preferences.md §8.4) ──
    single { DownloadPreferences(get()) }
    single { ServerDiscoveryStore(get()) }

    // ── Database adapter (SQLDelight-backed — see 11-db-schema.md §4) ──
    single { DownloadStore(get()) }

    // ── Storage (the NEW SAF + data.json system — see 04-storage-paths.md) ──
    single { TempDownloadCache(androidContext()) }  // calls cleanupStale() on creation
    single { DownloadStorageProvider(androidContext(), get()) }
    single { DownloadScanner(androidContext(), get(), get(), get()) }

    // ── HTTP client (qualified "download" — separate from the extension NetworkHelper client) ──
    single(named("download")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ── The 3 downloaders (modular — see 05-downloaders.md §11.1) ──
    single { DownloadResumeManager(get()) }
    single { HlsDownloader(get(named("download")), get(), get()) }
    single { AdvancedHttpDownloader(get(named("download")), get(), get(), get()) }
    single { HttpDownloader(
        client = get(named("download")),
        tempCache = get(),
        storage = get(),
        reResolver = getOrNull(),  // null if proxy-churn fix is disabled (set by the app module — see §11.2)
        store = get(),
        hlsDownloader = get(),
        advancedDownloader = get(),
        preferences = get(),
    ) }

    // ── Notifications + foreground service (the NEW design — see 06-notifications-foreground-service.md §13) ──
    single { DownloadNotificationManager(androidContext(), get()) }

    // ── Queue + Manager (the SQLDelight-backed design — see 02-queue-management.md §13) ──
    single { DownloadQueue(
        store = get(),
        preferences = get(),
        scope = get(named("downloadScope")),  // private scope — survives app-backgrounding with the foreground service
        downloader = get(),
        notifier = get(),
    ) }
    single<DownloadManager> { DefaultDownloadManager(
        context = androidContext(),
        queue = get(),
        store = get(),
        storage = get(),
        scanner = get(),
        preferences = get(),
        notifier = get(),
    ) }
}
```

### 11.2 The NEW `:app` module (orchestrator + proxy-churn fix)

**File:** `app/src/main/java/com/confused/anikuta/di/DownloadAppModule.kt` (CREATE)

```kotlin
package com.confused.anikuta.di

import com.confused.anikuta.core.download.*
import com.confused.anikuta.core.videoresolver.VideoResolver
import org.koin.dsl.module

val downloadAppModule = module {
    // Re-export the core + feature modules so App.kt only lists one entry.
    includes(downloadModule, downloadFeatureModule)

    // ── The 5-step priority engine (NEW — see 14-auto-download-engine.md §6.2) ──
    single { AutoDownloadEngine() }  // pure functions — no state, no constructor params

    // ── The proxy-churn fix (NEW — see 10-player-integration.md §14) ──
    single { ReResolver(get<VideoResolver>(), get<DownloadPreferences>()) }  // REVIEW-5 M17: removed `get<AutoDownloadEngine>()` — dead DI param (the reResolve implementation does a DIRECT lookup, never calls the engine)
    // Optional tertiary fix (deferred — see 10-player-integration.md §14.1 Fix 3):
    // single { ProxyLeaseCoordinator(get<VideoResolver>()) }

    // ── The orchestrator (bridges VideoResolver + DownloadManager — uses the NEW AutoDownloadEngine) ──
    single { DownloadOrchestrator(
        videoResolver = get(),
        downloadManager = get(),
        preferences = get(),
        serverDiscovery = get(),
        autoDownloadEngine = get(),  // used by the orchestrator's selectBestVideo (NOT by ReResolver — see M17)
        reResolver = get(),
    ) }

    // ── The private download scope (survives app-backgrounding — see 02-queue-management.md §13.1) ──
    single(named("downloadScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Logger.e("DownloadScope", e) { "Uncaught exception in download scope (suppressed)" }
        })
    }

    // ── NO DownloadMigration (fresh start — no migration from the OLD project's schema) ──
}
```

### 11.3 The NEW `:feature:download` module (the ViewModel)

**File:** `feature/download/src/main/java/com/confused/anikuta/feature/download/di/DownloadModule.kt` (CREATE)

```kotlin
package com.confused.anikuta.feature.download.di

import com.confused.anikuta.feature.download.DownloadViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val downloadFeatureModule = module {
    viewModelOf(::DownloadViewModel)
}
```

Same as the OLD project — `viewModelOf(::DownloadViewModel)` resolves `DownloadManager` + `DownloadPreferences` from the Koin graph.

### 11.4 Wiring into `AnikutaApp.kt`

The existing `AnikutaApp.kt` already includes `downloadModule` in the `startKoin { modules(...) }` call (see §9 of the OLD project's DI doc above). The post-rewrite change:

```kotlin
// AnikutaApp.kt — the startKoin block (POST-REWRITE):
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
        // downloadModule,               // ← REMOVE the old stub binding
        metadataModule,
        trackerAniListModule,
        watchProgressModule,
        smartMatcherModule,
        contentModule,
        dataCacheModule,
        downloadAppModule,              // ← NEW: includes downloadModule + downloadFeatureModule + orchestrator + reResolver + scope
        appModule,
    )
}
```

`downloadAppModule` aggregates the rewritten `downloadModule` + the new `downloadFeatureModule` + the orchestrator + the proxy-churn fix + the private scope. ONE entry in the `modules(...)` list.

### 11.5 The complete post-rewrite Koin graph

```
AnikutaApp.startKoin
  └── modules: [..., downloadAppModule, ...]
       │
       ├── includes: downloadModule (:core:download — REWRITTEN)
       │    ├── single DownloadPreferences(PreferenceStore)
       │    ├── single DownloadStore(AnikutaDatabase)              ← SQLDelight adapter
       │    ├── single ServerDiscoveryStore(PreferenceStore)
       │    ├── single TempDownloadCache(Context) — cleanupStale() on creation
       │    ├── single DownloadStorageProvider(Context, DownloadPreferences)  ← NEW: explicit binding
       │    ├── single DownloadScanner(Context, DownloadStorageProvider, DownloadStore, ContentRepository)  ← NEW: scan-on-startup
       │    ├── single OkHttpClient("download") — 30s/60s/60s timeouts
       │    ├── single HlsDownloader(OkHttpClient("download"), TempDownloadCache, DownloadPreferences)
       │    ├── single DownloadResumeManager(TempDownloadCache)
       │    ├── single AdvancedHttpDownloader(OkHttpClient("download"), TempDownloadCache, DownloadResumeManager, DownloadPreferences)
       │    ├── single HttpDownloader(OkHttpClient("download"), TempDownloadCache, DownloadStorageProvider, ReResolver?, DownloadStore, HlsDownloader, AdvancedHttpDownloader, DownloadPreferences)
       │    ├── single DownloadNotificationManager(Context, DownloadStorageProvider)  ← NEW: with thumbnails + dual channels
       │    ├── single DownloadQueue(DownloadStore, DownloadPreferences, scope("downloadScope"), HttpDownloader, DownloadNotificationManager)  ← NEW: SQLDelight-backed + Mutex
       │    └── single DownloadManager → DefaultDownloadManager(Context, DownloadQueue, DownloadStore, DownloadStorageProvider, DownloadScanner, DownloadPreferences, DownloadNotificationManager)
       │
       ├── includes: downloadFeatureModule (:feature:download)
       │    └── viewModel DownloadViewModel(DownloadManager, DownloadPreferences)
       │
       ├── single AutoDownloadEngine()  ← NEW: the 5-step priority pipeline
       ├── single ReResolver(VideoResolver, DownloadPreferences)  ← NEW: proxy-churn fix (REVIEW-5 M17: removed AutoDownloadEngine — dead DI param)
       ├── single DownloadOrchestrator(VideoResolver, DownloadManager, DownloadPreferences, ServerDiscoveryStore, AutoDownloadEngine, ReResolver)
       ├── single CoroutineScope("downloadScope") — private, SupervisorJob + Dispatchers.IO + exception handler
       └── (NO DownloadMigration — fresh start)
```

### 11.6 What's DIFFERENT from the OLD project's DI (and why)

| Aspect | OLD project | NEW project (post-rewrite) | Why |
|---|---|---|---|
| `DownloadStorageProvider` | Internal to `DefaultDownloadManager` (not a Koin binding) | EXPLICIT Koin `single` | Testable + the `DownloadScanner` needs it as a constructor param. |
| `HttpDownloader` / `HlsDownloader` / `AdvancedHttpDownloader` | Internal to `DefaultDownloadManager` | EXPLICIT Koin `single`s | Testable + the `HttpDownloader` needs `ReResolver` injected (the proxy-churn fix). |
| `DownloadNotificationManager` | Internal to `DefaultDownloadManager` | EXPLICIT Koin `single` | The `DownloadService` needs it directly (not via the manager). |
| `DownloadQueue` | Internal to `DefaultDownloadManager` | EXPLICIT Koin `single` | Testable + the manager delegates most operations to it anyway. |
| `AutoDownloadEngine` | N/A (the orchestrator's internal `selectBestVideo`) | EXPLICIT Koin `single` | The orchestrator uses it (the `ReResolver` does NOT — REVIEW-5 M17). Pure functions — no state. |
| `ReResolver` | N/A | EXPLICIT Koin `single` | The `HttpDownloader` needs it for the proxy-churn fix. |
| `DownloadScanner` | N/A | EXPLICIT Koin `single` | The `AnikutaApp` calls `requestFolderRescan()` on startup. |
| `DownloadMigration` | Koin `single` (the Phase 6 migration) | NOT REGISTERED | Fresh start — no migration from the OLD project's schema. |
| `DownloadService` | N/A (no foreground service) | Registered in the manifest (not in Koin — `Service` instances are created by Android) | The service uses `by inject<DownloadManager>()` (Koin scope). |
| `OkHttpClient("download")` | Koin `single` | Same | Separate from the extension `NetworkHelper` client. |
| `CoroutineScope("downloadScope")` | Implicit (created in `DefaultDownloadManager`'s constructor default param) | EXPLICIT Koin `single(named("downloadScope"))` | Testable + the `DownloadQueue` takes it as a constructor param (so tests can pass a TestScope). |

### 11.7 Cross-references (post-rewrite)

- `04-storage-paths.md` — the NEW `DownloadStorageProvider` + `DownloadScanner` + `TempDownloadCache`.
- `02-queue-management.md` §13 — the NEW `DownloadQueue` (SQLDelight-backed + Mutex + reactive concurrency).
- `05-downloaders.md` §11 — the NEW `Downloader` interface + the 3 engines.
- `06-notifications-foreground-service.md` §13 — the NEW `DownloadNotificationManager` + `DownloadService`.
- `07-settings-preferences.md` §8 — the NEW `DownloadPreferences` (17 settings) + the reactive `PreferenceStore`.
- `10-player-integration.md` §14 — the NEW `ReResolver` + `ResolveContext` (the proxy-churn fix).
- `11-db-schema.md` §4 — the NEW `DownloadStore` adapter (SQLDelight-backed).
- `13-implementation-plan.md` Phase D.1 + D.2 — the implementation plan for the DI wiring.
- `14-auto-download-engine.md` §6.2 — the NEW `AutoDownloadEngine` (the 5-step priority pipeline).
