# 02 — Architecture

> Analysis of the old ANIKUTA project's module layout, layering, dependency rules,
> convention plugins, navigation, and dependency injection. Source:
> `REFERENCES/old-kuta/ANIKUTA/`.

---

## 1. Module Tree

The canonical module list lives in `settings.gradle.kts`. The project README
claims **"41 Gradle modules"**, but that count is stale (pre-Phase-9). After a
Phase-9 cleanup pass that removed 10 phantom/empty-stub modules, the
**actual `include()` count is 36 active modules**:

- `:app` — **1** module
- `:core:*` — **16** modules
- `:data:*` — **3** modules
- `:feature:*` — **16** modules

Below is the full active tree, grouped by category. Indented rows are
**removed** entries (the comment in `settings.gradle.kts` explains why).

```
ANIKUTA/
├── :app                                   Application shell (DI, nav, single Activity)
│
├── :core                                  Infrastructure (no UI screens, no Compose UI)
│   ├── :core:common                       Domain models, repository interfaces, ContentId
│   ├── :core:designsystem                 Compose theme + reusable components (Compose lib)
│   ├── :core:database                     SQLDelight schema, migrations, driver factory
│   ├── :core:preferences                  PreferenceStore, ThemePreferences, SetupWizardPrefs
│   ├── :core:provider-api                 MetadataProvider contracts (ADR-041)
│   ├── :core:anilist                      AniList GraphQL client + metadata provider
│   ├── :core:tracker                      AniList + MAL tracker impls + TrackSyncManager
│   ├── :core:episode-metadata             EpisodeMetadataCache + AniList/Jikan/Anikage sources
│   ├── :core:source-api                   Aniyomi-compatible source-api (eu.kanade.* Kotlin)
│   ├── :core:player                       MPV wrapper (AnikutaMPVView) + watch progress + controls
│   ├── :core:update-checker               Episode update checker (new episodes since last seen)
│   ├── :core:download                     Download manager (HTTP + HLS + advanced resume)
│   ├── :core:backup                       Backup/restore (Anikuta + Aniyomi format translator)
│   ├── :core:video-resolver               Resolver service + state (extract playable video URL)
│   ├── :core:ads                          On-device ad interstitial system (ADR for "poison" ads)
│   ├── :core:app-update                   Self-update via GitHub Releases + APK install
│   │
│   ├── ✗ :core:network         [REMOVED Phase 9 — empty stub, 0 .kt files]
│   ├── ✗ :core:source-local    [REMOVED Phase 9 — empty stub; re-add for local-files-as-source]
│   └── ✗ :core:notification    [REMOVED Phase 9 — empty stub; re-add for episode-release notifs ADR-014]
│
├── :data                                  Repository implementations (glue :core ↔ :core:database)
│   ├── :data:anime                        AnimeRepositoryImpl + EpisodeRepositoryImpl + mappers
│   ├── :data:extension                    AnimeExtensionManager + installer + matcher + SAnime mapper
│   ├── :data:history                      HistoryRepositoryImpl + mapper
│   │
│   ├── ✗ :data:manga           [REMOVED Phase 9 — empty stub; re-add for manga reader ADR-009]
│   └── ✗ :data:tracker         [REMOVED Phase 9 — empty stub; tracker impls live in :core:tracker]
│
└── :feature                               UI screens (Compose)
    ├── :feature:browse                    Home tab — Browse screen (AniList trending/seasonal)
    ├── :feature:library                   Library tab — grid + list + categories + sort
    ├── :feature:search                    Search tab — AniList + Extension sources, filters
    ├── :feature:my                        Profile tab — stats, charts, recently-watched
    ├── :feature:history                   History tab — recently watched episodes
    ├── :feature:updates                   Updates tab — schedule + calendar + live-check
    ├── :feature:anime-details             Anime detail page (banner, episodes, source switcher)
    ├── :feature:episode-settings          Episode display/layout/metadata settings hub
    ├── :feature:video-resolver            Resolver sheet UI (modal — picks a video to watch)
    ├── :feature:watch                     Player host screen (embeds :core:player MPV view)
    ├── :feature:extensions-settings       Extensions list + repo management
    ├── :feature:settings                  Appearance, General, Player, About settings
    ├── :feature:trackers                  Tracker list + login (AniList/MAL OAuth)
    ├── :feature:backup                    Backup/restore UI (uses :core:backup + Aniyomi restore)
    ├── :feature:download                  Download queue + downloaded files browser
    ├── :feature:setup-wizard              15-screen onboarding flow (first-launch gate)
    │
    ├── ✗ :feature:home          [REMOVED Phase 9 — empty stub; Home tab = BrowseScreen]
    ├── ✗ :feature:more          [REMOVED Phase 9 — empty stub; More tab in app/MoreScreens.kt]
    ├── ✗ :feature:episode-list  [REMOVED Phase 9 — empty stub; episodes in anime-details/EpisodesSection]
    └── ✗ :feature:player       [REMOVED Phase 9 — empty stub; fullscreen player in :feature:watch]
```

> **Note on the "41 modules" discrepancy.** The `README.md` at the project root
> still says "41 Gradle modules" (stale text from pre-Phase-9). The directories
> for the 9 removed stub modules still exist on disk (each with a `build.gradle.kts`
> + `AndroidManifest.xml` but no `.kt` files), but they are **not** `include()`d
> in `settings.gradle.kts`, so Gradle does not build them. The actual build
> graph contains **36 modules**.

---

## 2. Layering

```
┌──────────────────────────────────────────────────────────────────┐
│  :app  (shell)                                                   │
│  ── MainActivity (single Activity, Compose host)                 │
│  ── App.kt (Koin + Injekt startup, migrations)                   │
│  ── navigation/ (Voyager root, AppController, Destinations)      │
│  ── di/ (DatabaseModule, RepositoryModule, ExtensionModule, …)   │
│  ── migration/ (ContentIdMigrator, DownloadMigration)            │
│  ── error/ (AnikutaCrashHandler, ErrorActivity)                  │
│  ── download/ (DownloadOrchestrator — composes :core:download)   │
└──────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────┐
│  :feature:*  (UI screens)                                        │
│  ── Voyager `Screen` subclasses + Compose composables            │
│  ── ViewModels (koinInject, viewModelScope)                      │
│  ── Reads :core:* + :data:* (for repository interfaces)          │
└──────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────┐
│  :data:*  (repository implementations)                           │
│  ── Implements :core:common/*Repository interfaces               │
│  ── Reads :core:database (SQLDelight queries)                    │
│  ── Reads :core:source-api (extension types)                     │
│  ── Registered into Koin by :app/di/*Module.kt                   │
└──────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────┐
│  :core:*  (infrastructure + domain)                              │
│  ── :core:common (models + repository interfaces)                │
│  ── :core:database (SQLDelight)                                  │
│  ── :core:preferences (PreferenceStore)                          │
│  ── :core:designsystem (Compose theme + components)              │
│  ── :core:source-api (Aniyomi-compatible source contracts)       │
│  ── :core:player (MPV)                                           │
│  ── :core:anilist, :core:tracker, :core:download, :core:backup,  │
│     :core:ads, :core:app-update, :core:video-resolver, …         │
└──────────────────────────────────────────────────────────────────┘
```

### Layering rules (enforced via module dependencies)

| Rule | Description |
|------|-------------|
| **R1** | `:app` is the **only** module that imports from every layer. It is the composition root. |
| **R2** | `:feature:*` modules import from `:core:*` and `:data:*`. They NEVER import from each other (Rule §14, see `:feature:more` docstring in `app/.../navigation/MoreScreens.kt`). |
| **R3** | `:data:*` modules implement interfaces from `:core:common` and call `:core:database`. They never import from `:feature:*` or `:app`. |
| **R4** | `:core:*` modules import from other `:core:*` (allowed). They never import from `:data:*`, `:feature:*`, or `:app`. |
| **R5** | When a UI surface must compose entries from multiple features (e.g., the "More" tab shows Profile + History + Downloads entries), it lives in `:app/navigation/MoreScreens.kt` — NOT in a feature module. This is documented in `MoreScreens.kt`'s KDoc as the explicit reason `:feature:more` was removed in Phase 9. |
| **R6** | `:core:source-api` uses the legacy `eu.kanade.tachiyomi.*` Kotlin package (Aniyomi-compat) so unmodified Aniyomi extensions can be loaded at runtime. |

### Worked example — `:feature:watch` dependency list (`feature/watch/build.gradle.kts`)

```kotlin
implementation(projects.core.common)
implementation(projects.core.designsystem)
implementation(projects.core.preferences)
implementation(projects.core.sourceApi)
implementation(projects.core.player)
implementation(projects.core.episodeMetadata)
implementation(projects.data.anime)          // repository impls
implementation(projects.data.history)        // history repo impl
implementation(projects.core.videoResolver)  // Phase 8 fix: types moved :feature → :core
// NOTE: no other :feature:* dep — rule R2 holds.
```

---

## 3. Module Categories

### 3.1 `:app` — Application shell

The composition root. Single Activity, single Compose tree. Holds:

- `MainActivity.kt` — the only Activity (besides `ErrorActivity`). Sets up
  edge-to-edge + the Compose tree, gates on `SetupWizardPreferences.isCompleted()`.
- `App.kt` — `Application` subclass. Installs `AnikutaCrashHandler`, registers
  Injekt singletons, starts Koin, runs phased DB migrations.
- `navigation/` — Voyager root, `AppController`, all `Destination` screens,
  `MoreScreen`, `SettingsScreen`, ad/update dialogs.
- `di/` — 8 Koin modules: `DatabaseModule`, `RepositoryModule`, `ExtensionModule`,
  `SearchModule`, `DetailsModule`, `ProviderApiModule`, `ContentIdMigratorModule`,
  `DownloadAppModule`.
- `migration/` — `ContentIdMigrator` (re-keys DB rows on link/unlink) +
  `DownloadMigration`.
- `error/` — `AnikutaCrashHandler` (sets `Thread.setDefaultUncaughtExceptionHandler`)
  + `ErrorActivity` (crash log + Restart/Close buttons).
- `download/DownloadOrchestrator.kt` — wraps `:core:download.DownloadManager`
  + `:core:video-resolver.ResolverService` to enqueue downloads.

### 3.2 `:core:*` — Infrastructure (16 modules)

Pure infrastructure. No Compose UI screens, no Activities. (Exceptions:
`:core:designsystem` ships Compose components + theme; `:core:player`
ships one `mpv_view.xml` layout for the AndroidView hosting MPV.)

Key modules:
- **`:core:common`** — Domain models (`Anime`, `Episode`, `History`, `Category`,
  `Track`, `ContentId`, `LocalId`, `SourceProvenance`, `UnifiedAnime`,
  `DetailsRequest`, `DataSource`), repository **interfaces**
  (`AnimeRepository`, `EpisodeRepository`, `HistoryRepository`,
  `CategoryRepository`, `TrackRepository`), and `DispatcherProvider`.
- **`:core:designsystem`** — `AnikutaTheme`, color palette, typography,
  `RobottoFamily` (4 bundled TTFs), `ScrollBlurOverlay`, `CollapsingHeader`,
  `BottomNavBar`, `AnikutaBottomSheet`, etc.
- **`:core:database`** — SQLDelight `.sq` files (`animes.sq`, `episodes.sq`,
  `categories.sq`, `anime_category.sq`, `animehistory.sq`, `animetrack.sq`) +
  2 `.sqm` migrations + `DatabaseDriverFactory`.
- **`:core:preferences`** — `PreferenceStore` (SharedPreferences abstraction),
  `AndroidPreferenceStore`, `ThemePreferences`, `SetupWizardPreferences`,
  `ContentIdPreferences`, `EpisodeDisplayPreferences`, `LinkingPreferences`,
  `DetailsViewPreferences`.
- **`:core:source-api`** — Aniyomi-compatible Kotlin source contracts under
  `eu.kanade.tachiyomi.animesource.*` (`AnimeSource`, `AnimeHttpSource`,
  `ParsedAnimeHttpSource`, `AnimeCatalogueSource`, `SAnime`, `SEpisode`,
  `Video`, `AnimeFilterList`, `NetworkHelper`, OkHttp interceptors).
- **`:core:player`** — `AnikutaMPVView` (extends `is.xyz.mpv.BaseMPVView`),
  `PlayerStateHolder`, `WatchProgressStore`, `PlaybackStateStore`,
  `PlayerPreferences`, `MpvConfigManager`, `PlayerObserver`,
  `controls/` (Fullscreen, Minimized, EpisodeSwitching, SubtitleSettings,
  ColorPicker, NumericEntry sheets), `migration/WatchProgressMigrator`.
- **`:core:tracker`** — `Tracker` interface, `TrackerManager`,
  `TrackSyncManager` (auto-syncs progress), AniList + MAL implementations,
  OAuth helpers (`PkceUtil`, `MalOAuth`).
- **`:core:ads`** — `AdsPreferences`, `AdTracker`, `AdManager` (state machine),
  `AdBranding`. On-device only, no network.
- **`:core:app-update`** — `AppUpdateManager`, `GitHubUpdateSource`,
  `UpdateDownloader`, `ApkInstaller`, `AppUpdatePreferences`.
- **`:core:video-resolver`** — `ResolverService`, `VideoResolverStrategy`,
  `VideoResolverState`, `VideoTitleParser`. Phase 8 fix moved this from
  `:feature:video-resolver` to `:core` so `:feature:watch` could depend on
  the types without a feature→feature dep.
- **`:core:download`** — `DownloadManager`, `DefaultDownloadManager`,
  `HttpDownloader`, `HlsDownloader`, `AdvancedHttpDownloader`,
  `DownloadResumeManager`, `DownloadStore`, `DownloadQueue`,
  `DownloadNotificationManager`, `ServerDiscoveryStore`, `TempDownloadCache`.
- **`:core:backup`** — `BackupManager`, `BackupProvider` + 10 provider impls
  (`AnimeBackupProviders`, `EpisodeBackupProvider`, `EpisodeMetadataBackupProvider`,
  `WatchProgressBackupProvider`, `CategoryBackupProvider`, `SourceLinkBackupProvider`,
  `PreferencesBackupProvider`, `CoverImageProvider`, `TrackerBackupProviderImpl`,
  `TrackerBackupProviderAdapter`), `AutoBackupWorker` (WorkManager),
  `format/AniyomiBackupFormat` + `translation/AniyomiBackupTranslator`
  (reads .proto.gz Aniyomi backups).
- **`:core:provider-api`** — Phase 2 pluggable metadata abstraction
  (ADR-041): `MetadataProvider` interface, `Capabilities`,
  `MetadataProviderRegistry`. AniList is the only impl today; MAL/TMDB
  are future additions (one module + one binding).
- **`:core:episode-metadata`** — `EpisodeMetadataCache`,
  `EpisodeMetadataRepository`, `EpisodeMetadataSource` interface + 4 impls
  (AniList, Jikan/MAL, Anikage-CC), `EpisodeTitleParser`,
  `migration/EpisodeMetadataMigrator`.
- **`:core:update-checker`** — `UpdateChecker`, `EpisodeFetchGateway`,
  `SubDubParser`, `UpdateCheckerPreferences`.
- **`:core:anilist`** — `AniListApi` (GraphQL), `AniListRateLimiter`,
  `LocalAniListCache`, `AniListAnime` model, `AniListMetadataProvider`
  (implements `:core:provider-api.MetadataProvider`),
  `AniListDetailsProvider` (implements `:core:common.AnimeDetailsProvider`).

### 3.3 `:data:*` — Repository implementations (3 modules)

Glue layer. Implements `:core:common/*Repository` interfaces by calling
`:core:database` queries and mapping rows → domain models.

- **`:data:anime`** — `AnimeRepositoryImpl`, `EpisodeRepositoryImpl`,
  `CategoryRepositoryImpl`, mappers (`AnimeMapper`, `EpisodeMapper`,
  `CategoryMapper`), `details/AniListDetailsProvider` (wraps AniList fetch
  into the `AnimeDetailsProvider` contract).
- **`:data:extension`** — `AnimeExtensionManager`, `AnimeExtensionApi`,
  `AnimeExtensionLoader` (child-first classloader), `ChildFirstPathClassLoader`,
  installer stack (`AnimeExtensionInstaller`, `ExtensionInstallService`,
  `PackageInstallerBackend`, `ExtensionInstallReceiver`, `InstallStep`),
  repo (`ExtensionRepoApi`, `ExtensionRepoRepository`, `ExtensionRepo`),
  cache (`SourceLinkStore`, `ExtensionLinkStore`, `SourceLinkBackupAccessImpl`,
  `DetailsViewPreferenceStore`), matcher (`SourceMatcher`),
  migration (`SourceLinkMigrator`), model (`AnimeExtension`, `AnimeLoadResult`),
  trust (`TrustExtension`), details (`ExtensionDetailsProvider`, `SAnimeMapper`),
  updatechecker (`EpisodeFetchGatewayImpl`).
- **`:data:history`** — `HistoryRepositoryImpl`, `HistoryMapper`.

### 3.4 `:feature:*` — UI screens (16 modules)

Each feature ships Voyager `Screen` subclasses + Compose composables +
ViewModels + a Koin DI module. See `06-feature-modules.md` for per-feature
deep dives.

| Feature | Tab/Entry | What it ships |
|---|---|---|
| `:feature:browse` | Home tab | `BrowseScreen` (AniList trending/seasonal) |
| `:feature:library` | Library tab | `LibraryScreen`, grid/list, categories, sort |
| `:feature:search` | Search tab | `SearchScreen`, filters, extension linking sheet |
| `:feature:my` | Profile tab | `ProfileScreen`, stats, charts, distribution |
| `:feature:history` | History tab | `HistoryScreen`, `HistoryUpdatesMoreEntries` |
| `:feature:updates` | Updates tab | `UpdatesScreen`, schedule, calendar, live-check |
| `:feature:anime-details` | pushed | `AnimeDetailScreen` + 12 sub-components |
| `:feature:episode-settings` | pushed | Display/Layout/Metadata/Hub settings |
| `:feature:video-resolver` | modal sheet | `VideoResolverSheet` (UI only; types in :core) |
| `:feature:watch` | pushed | `WatchScreen` (hosts MPV view from :core:player) |
| `:feature:extensions-settings` | pushed | Extensions list + repo management |
| `:feature:settings` | pushed | Appearance/General/Player/About screens |
| `:feature:trackers` | pushed | Tracker list + OAuth login |
| `:feature:backup` | pushed | Backup/restore UI + Aniyomi restore flow |
| `:feature:download` | pushed | Queue + downloaded files browser |
| `:feature:setup-wizard` | first-launch gate | 15-screen onboarding (own theme) |

---

## 4. Dependency Rules (summary)

| From → To | `:app` | `:feature:*` | `:data:*` | `:core:*` |
|---|:---:|:---:|:---:|:---:|
| `:app` | — | ✅ all | ✅ all | ✅ all |
| `:feature:*` | ❌ | ❌ peer-to-peer | ✅ | ✅ |
| `:data:*` | ❌ | ❌ | ❌ peer-to-peer | ✅ |
| `:core:*` | ❌ | ❌ | ❌ | ✅ peer-to-peer |

Three cross-cutting invariants:

1. **Feature modules never import from each other.** Violated once historically
   (`:feature:watch` → `:feature:video-resolver`); fixed in Phase 8 by moving
   the shared types to `:core:video-resolver`. The UI sheet stayed in
   `:feature:video-resolver`.
2. **`MoreScreens.kt` lives in `:app`** because it composes entries from
   `:feature:my` + `:feature:history` + `:feature:download`. (Documented
   in its KDoc as the reason `:feature:more` was removed.)
3. **`:core:source-api` keeps the `eu.kanade.tachiyomi.*` package** so
   Aniyomi extensions load without code changes (ADR-029).

---

## 5. Convention Plugins (buildSrc)

The project uses **buildSrc** (not the newer build-logic include-build
approach) with 4 convention plugins:

| Plugin ID | File | Purpose |
|---|---|---|
| `anikuta.android.application` | `anikuta.android.application.gradle.kts` | Base config for an Android **application** module. Applies `com.android.application` + `org.jetbrains.kotlin.android`. Sets `applicationId`, `versionCode`, `versionName`, `compileSdk`, `minSdk`, `targetSdk` from `AndroidConfig`. Enables core library desugaring. Sets JVM 17. Uses JUnit 5 (`useJUnitPlatform()`). Brings coroutines BOM (`1.10.1`) + desugar (`2.1.5`). |
| `anikuta.android.application.compose` | `anikuta.android.application.compose.gradle.kts` | Adds Compose to an application module. Applies `anikuta.android.application` + `org.jetbrains.kotlin.plugin.compose`. Enables `buildFeatures.compose`. Brings Compose BOM `2025.03.00` + foundation/material3/material-icons-extended/runtime/ui-tooling-preview/ui-util + activity-compose `1.10.1`. |
| `anikuta.library` | `anikuta.library.gradle.kts` | Base config for an Android **library** module. Same as `anikuta.android.application` but for libraries (no `applicationId`/`versionCode`). |
| `anikuta.library.compose` | `anikuta.library.compose.gradle.kts` | Adds Compose to a library module. Applies `anikuta.library` + `org.jetbrains.kotlin.plugin.compose`. Same Compose deps as the application variant + `core-ktx:1.15.0`. |

### buildSrc layout

```
buildSrc/
├── settings.gradle.kts          ← own dependencyResolutionManagement
├── build.gradle.kts             ← `kotlin-dsl` plugin + jvmToolchain(17)
└── src/main/kotlin/
    ├── anikuta.android.application.gradle.kts
    ├── anikuta.android.application.compose.gradle.kts
    ├── anikuta.library.gradle.kts
    ├── anikuta.library.compose.gradle.kts
    └── anikuta/buildlogic/
        ├── AndroidConfig.kt     ← shared constants
        └── ProjectExtensions.kt ← git SHA + commit count + build time helpers
```

`buildSrc/build.gradle.kts` declares its own dependencies on the AGP, Kotlin
Gradle plugin, compose-compiler-gradle plugin, and Spotless — all from the
parent project's version catalogs (which it imports via
`from(files("../gradle/..."))`).

### `AndroidConfig.kt` — the central config object

```kotlin
object AndroidConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    const val NDK = "27.1.12297006"
    const val BUILD_TOOLS = "35.0.1"

    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17

    const val APPLICATION_ID = "app.confused.anikuta"
    const val VERSION_CODE = 100
    const val VERSION_NAME = "1.0.0"
}
```

**Key takeaways:**
- **compileSdk = targetSdk = 36** (Android 16 — bleeding edge).
- **minSdk = 26** (Android 8.0 — covers ~97% of devices, gives us java.time).
- **JDK 17** for both Gradle/AGP and Kotlin JVM target.
- NDK pinned to `27.1.12297006` (for MPV native libs).
- Build-tools `35.0.1` (older than compileSdk 36 — uses the cross-version
  compatibility path).
- `applicationId = app.confused.anikuta` (the `confused` namespace matches the
  GitHub org `Confused-Creature-180`).
- Version 1.0.0, code 100.

### `ProjectExtensions.kt`

Three helper extension functions on `Project`:
- `getBuildTime()` — current millis (for `BuildConfig`).
- `getCommitCount()` — `git rev-list --count HEAD` (with `runCatching` → "1").
- `getGitSha()` — `git rev-parse --short HEAD` (with `runCatching` → "unknown").

These are used to stamp `BuildConfig` fields in modules that need them
(e.g., `:core:app-update` reads the current version to compare against
GitHub Releases).

---

## 6. Navigation — Voyager

The app uses **Voyager 1.0.1** for navigation (ADR-037 — migrated from a
hand-rolled state machine in `MainActivity.kt`).

### Entry point

`MainActivity.kt` (the single Activity):
1. `enableEdgeToEdge()`.
2. `setContent { … }` — gates on `SetupWizardPreferences.isCompleted()`:
   - If `false` → renders `SetupWizardApp(onComplete = { wizardDone = true })`
     (the wizard's own theme, bypasses `AnikutaTheme`).
   - Else → `AnikutaTheme(…full theme prefs…) { AnikutaRoot() }`.
3. Theme is wired reactively — every `ThemePreferences` field
   (`themeMode`, `amoled`, `accentPreset`, `customAccentColor`, `paletteMode`,
   `customBackground`, `customCard`, `customText`) is collected via
   `collectAsStateWithLifecycle`, so changing any of them in the Appearance
   screen recomposes the whole app live (no restart).
4. `onNewIntent` + `handleOAuthIntent` catch `aniyomi://anilist-auth` and
   `aniyomi://myanimelist-auth` OAuth callback URLs and publish them to the
   `MainActivity.pendingOAuthCallback` `MutableStateFlow<String?>`, which
   `AnikutaRoot` collects and forwards to `AppController.handleOAuthCallback`.

### `AnikutaRoot.kt` — the Voyager root

```
Navigator(BrowseTabDestination) { navigator ->
    SideEffect { appController.navigator = navigator }   // inject navigator

    // Observers (LaunchedEffect / DisposableEffect):
    //   1. Download error toast  (collects appController.downloadTasksFlow)
    //   2. OAuth callback        (collects MainActivity.pendingOAuthCallback)
    //   3. Ad return lifecycle   (ON_RESUME → appController.onAdReturn())
    //   4. App update check      (cleanups old APKs → checks GitHub → shows sheet)

    Box {
        FadeTransition(navigator)                        // current screen
        if (showBottomNav) { AnikutaBottomNavBar(...) }  // floating bottom nav
        AppOverlays(appController)                       // modal sheets
    }
}
```

**Bottom nav has 4 tabs** (Home, Library, Search, More). Switching tabs calls
`navigator.replace(newTab)` (single root Navigator, not per-tab Navigators).
"More" is always last (ADR-017).

### `AppController` — central state holder

`AppController.kt` is the **business-logic coordinator** for the shell.
It holds:
- The `Navigator` reference (set via `SideEffect` from `AnikutaRoot`).
- `currentTab` — the active bottom-nav route.
- `resolverState` — drives the `VideoResolverSheet` overlay.
- `linkingTarget` — drives the `ExtensionLinkingSheet` overlay.
- `downloadPickerTarget` — drives the `DownloadVideoPickerSheet` overlay.
- `pendingUnlinkDownloadAction` — drives the unlink-action dialog.
- `pendingAdNavigation` — set by `withAdGate`; drives `AdDialog`.
- `showUpdateDialog` + `showPostInstallSuccess` — drive the update UI.
- `adAwaitingReturn` — true while the user is in the browser for an ad.

Key methods:
- `switchTab(route)` — replaces the navigator root.
- `pushDetail(anilistId)` / `pushExtensionDetail(source, sAnime, anilistId?)` —
  push the anime-details screen, wrapped in `withAdGate`.
- `onLinked(anilistId, wasCached, source, sAnime)` — called after the
  extension-linking sheet links an AniList entry to an extension source.
- `unlinkFromAniList(anilistId, sourceId?, animeUrl?)` — clears the
  bidirectional link, navigates to extension-mode details.
- `onVideoSelected(video)` — pushes the Watch screen with the picked video.
- `enqueuePickedVideo(video, serverName, audioLabel)` — hands off to
  `DownloadOrchestrator`.
- `withAdGate(action)` — if `adManager.shouldShowAd()`, defers `action` and
  shows `AdDialog`; else runs `action()` immediately.
- `handleOAuthCallback(url)` — extracts tracker + code, exchanges for tokens.
- `checkForDownloadErrors(tasks)` — toasts on failed downloads.
- `onAdReturn()` — called from the ON_RESUME lifecycle observer; checks
  min-stay, records the ad if stayed long enough, runs the deferred action.

### `Destinations.kt` — Voyager Screen subclasses

A 623-line file containing every `Screen` in the app as a data class
implementing `cafe.adriel.voyager.core.Screen`. Notable entries:
- `BrowseTabDestination`, `LibraryTabDestination`, `SearchTabDestination`,
  `MoreTabDestination` — the 4 root tabs.
- `AnimeDetailDestination`, `ExtensionAnimeDetailDestination`,
  `LibraryExtensionDetailDestination` — three flavors of the anime-details
  page (AniList mode, Extension mode, Library-no-source mode).
- `WatchDestination` — pushed when the user taps an episode to watch.
- `SettingsDestination`, `ExtensionsSettingsDestination`,
  `TrackersDestination`, `BackupSettingsDestination`,
  `DownloadsScreenDestination`, `DownloadedFilesScreenDestination`,
  `EpisodeSettingsHubDestination`, `ProfileDestination`,
  `HistoryDestination`, `UpdatesDestination` — pushed from MoreScreen or
  tab-bar tabs.

### `AppOverlays` — modal sheets (not navigated screens)

Six overlays render on top of the current screen, driven by `AppController`
state (NOT pushed onto the Voyager back stack):
1. `VideoResolverSheet` (custom Box, not `ModalBottomSheet`)
2. `ExtensionLinkingSheet`
3. `DownloadVideoPickerSheet`
4. Unlink-action `AlertDialog` (Transfer / Delete / Cancel)
5. `AdDialog` (interstitial)
6. `UpdateBottomSheet` + `PostInstallSuccessSheet`

---

## 7. Dependency Injection — Koin + Injekt

The app uses **two DI frameworks in parallel** (ADR-023 + ADR-029):

### Koin (host app)

Koin 4.0.0 (via BOM) is the primary DI. Modules are registered in `App.kt`
inside `startKoin { … }`. The full module list (24 modules):

| Module | Source | Purpose |
|---|---|---|
| `databaseModule` | `:app/di/DatabaseModule.kt` | SQLDelight `Database` instance + `DatabaseDriverFactory` |
| `repositoryModule` | `:app/di/RepositoryModule.kt` | Binds `AnimeRepository`/`EpisodeRepository`/etc. interfaces to `:data:*` impls |
| `extensionModule` | `:app/di/ExtensionModule.kt` | `AnimeExtensionManager`, `SourceMatcher`, `ExtensionRepoApi`, `ExtensionLinkStore`, `SourceLinkStore` |
| `searchModule` | `:app/di/SearchModule.kt` | `SearchViewModel` deps |
| `preferenceModule` | `:core:preferences/di/PreferenceModule.kt` | `PreferenceStore` + every typed preferences object |
| `playerModule` | `:core:player/di/PlayerModule.kt` | `PlayerStateHolder`, `WatchProgressStore`, `PlaybackStateStore`, `PlayerPreferences` |
| `libraryModule` | `:feature:library/di/LibraryModule.kt` | `LibraryViewModel` deps + `LibraryPreferences` |
| `episodeMetadataModule` | `:core:episode-metadata/di/EpisodeMetadataModule.kt` | `EpisodeMetadataRepository`, `EpisodeMetadataCache`, source registry |
| `updateCheckerModule` | `:core:update-checker/di/UpdateCheckerModule.kt` | `UpdateChecker`, `EpisodeFetchGateway` |
| `historyModule` | `:feature:history/di/HistoryModule.kt` | `HistoryViewModel` deps |
| `updatesModule` | `:feature:updates/di/UpdatesModule.kt` | `UpdatesViewModel` deps |
| `trackerModule` | `:core:tracker/di/TrackerModule.kt` | `TrackerManager`, `TrackSyncManager`, AniList + MAL trackers, `TrackRepository` |
| `myModule` | `:feature:my/di/MyModule.kt` | `ProfileViewModel` deps, `ProfilePreferences` |
| `trackersModule` | `:feature:trackers/di/TrackersModule.kt` | `TrackersViewModel` deps |
| `backupModule` | `:core:backup/di/BackupModule.kt` | `BackupManager`, `BackupProvider` impls, `BackupStorage`, `AutoBackupScheduler` |
| `backupFeatureModule` | `:feature:backup/di/BackupFeatureModule.kt` | `BackupViewModel` deps |
| `aniyomiRestoreModule` | `:feature:backup/di/AniyomiRestoreModule.kt` | `AniyomiRestoreFlow`, `AniyomiRestoreViewModel` |
| `downloadAppModule` | `:app/di/DownloadAppModule.kt` | `DownloadOrchestrator` (composes `:core:download.DownloadManager` + resolver service) |
| `navModule` | `:app/navigation/NavModule.kt` | `AniListApi` (shared, cached, rate-limited) + `AppController` |
| `detailsModule` | `:app/di/DetailsModule.kt` | `AnimeDetailsProviderRegistry` (AniList + Extension providers) |
| `providerApiModule` | `:app/di/ProviderApiModule.kt` | `MetadataProviderRegistry` (Phase 2 — AniList is the only impl) |
| `contentIdMigratorModule` | `:app/di/ContentIdMigratorModule.kt` | `ContentIdMigrator` (Phase 5) |
| `adsModule` | `:core:ads/di/AdsModule.kt` | `AdsPreferences`, `AdTracker`, `AdManager` |
| `appUpdateModule` | `:core:app-update/di/AppUpdateModule.kt` | `AppUpdateManager`, `GitHubUpdateSource`, `UpdateDownloader` |

### Injekt (extension compatibility — ADR-029)

Aniyomi/Keiyoushi-family extensions are compiled against
`uy.kohesive.injekt` and call `Injekt.get<T>()` at runtime for several
host-provided singletons. The app **cannot replace Injekt with Koin** for
this — extension bytecode would throw `InjektionException`. So `App.kt`
registers four Injekt singletons before Koin starts:

```kotlin
// Application + Context — Keiyoushi extensions call Injekt.get<Application>()
Injekt.addSingleton(fullType<Application>(), this)
Injekt.addSingleton(fullType<Context>(), this)

// NetworkHelper — AnimeHttpSource resolves it via `by injectLazy()`.
// CRITICAL: NetworkHelper MUST be a class (not interface) — otherwise
// extension bytecode throws IncompatibleClassChangeError on .client access.
val networkHelper = NetworkHelper(this)
Injekt.addSingleton(fullType<NetworkHelper>(), networkHelper)

// Json — Keiyoushi extensions call Injekt.get<Json>() in static
// initializers (e.g. for preference serializers).
Injekt.addSingletonFactory(fullType<Json>()) {
    Json { ignoreUnknownKeys = true; explicitNulls = false }
}
```

Injekt comes from `com.github.mihonapp:injekt:91edab2317` (JitPack).

### Startup order in `App.kt`

1. Install `AnikutaCrashHandler` (FIRST — before anything that might throw).
2. `ExtensionAppHolder.init(this)` — gives extensions access to the
   Application context (used by `ConfigurableAnimeSource`).
3. Register Injekt singletons (above) — wrapped in try/catch.
4. `startKoin { … }` with all 24 modules.
5. Start `TrackSyncManager` (auto-syncs progress to AniList/MAL).
6. Ensure the "Default" category exists (safety net for the DB seed).
7. Run phased DB migrations (each gated by a one-shot preference flag):
   - **Phase 1** — backfill `local_id` + `content_id` columns on existing
     `animes` rows (added by `2.sqm`).
   - **Phase 3** — re-key `WatchProgressStore` + `PlaybackStateStore` from
     `$anilistId:$episodeUrl` to `$contentId|$episodeNumber`.
   - **Phase 4** — re-key `EpisodeMetadataCache` + `SourceLinkStore` +
     `ExtensionLinkStore` from `anilistId` to `contentId`.
   - **Phase 6** — re-key `DownloadStore` tasks + move on-disk folders from
     `[anilistId]` to `[al-anilistId]`.

Each migration is idempotent and wrapped in try/catch; a failure logs a
warning and retries on the next launch (the preference flag is only set
after success).

---

## 8. Key cross-cutting patterns

### 8.1 The `ContentId` identity model

The app's identity story evolved through Phases 1–6:

- **Phase 0** — every anime was keyed by its AniList ID. This broke when the
  user added an extension anime that wasn't on AniList (no AniList ID).
- **Phase 1** — added `local_id` (DB-assigned) + `content_id` (provider-prefixed,
  e.g. `"al:12345"` for AniList, `"ext:42:https://..."` for extension-only)
  columns to `animes`. Backfilled existing rows on first launch.
- **Phase 3–6** — progressively re-keyed every cross-cutting store
  (watch progress, playback state, episode metadata, source links, downloads)
  from `anilistId` to `contentId`. Each re-keying is a separate migration
  class, gated by a one-shot preference flag, run from `App.kt`.

The `ContentId` and `LocalId` classes live in `:core:common/model/` and have
unit tests (`ContentIdTest.kt`, `LocalIdTest.kt`, `SourceProvenanceTest.kt`).

### 8.2 `AnimeDetailsProvider` + `MetadataProvider` registries

Two parallel provider registries abstract the AniList-vs-extension distinction:

- **`AnimeDetailsProviderRegistry`** (`:core:common/model/details/`) — given a
  `DetailsRequest` (either `ByAniListId` or `ByExtension`), returns a
  `UnifiedAnime` by querying the right provider. Two impls:
  - `AniListDetailsProvider` (`:data:anime/details/`)
  - `ExtensionDetailsProvider` (`:data:extension/details/`)
- **`MetadataProviderRegistry`** (`:core:provider-api/`) — pluggable metadata
  abstraction (ADR-041). `AniListMetadataProvider` (`:core:anilist/details/`)
  is the only impl today; adding MAL/TMDB = one module + one Koin binding.

### 8.3 Source linking (bidirectional)

When the user links an AniList entry to an extension source:
- `sourceLinkStore.saveLink("al:$anilistId", source.id, sAnime.url, sAnime.title)`
  — AniList → extension.
- `extensionLinkStore.link(sid, url, anilistId)` — extension → AniList.
- `detailsViewPreferenceStore` records the user's preferred view.

Unlinking (`AppController.unlinkFromAniList`) clears all three.

### 8.4 Crash handler

`AnikutaCrashHandler` (installed first in `App.kt`) catches uncaught
exceptions, writes the stack trace to a file, and launches `ErrorActivity`
showing the crash log + Copy / Restart / Close buttons. This ensures
extension-loading or DB-migration crashes don't silently disappear.

### 8.5 `BETA_BUILD` flag

`app/build.gradle.kts` defines `BuildConfig.BETA_BUILD`:
- `debug` build type → `BETA_BUILD = true` (the update checker points at the
  beta GitHub repo `Confused-Creature-180/APP_BETA`).
- `release` build type → `BETA_BUILD = false` (points at the main repo).

This lets the same code path serve both beta + stable channels.

---

## 9. Observations for the rebuild

These are pointers for `09-rebuild-notes.md`; expanded there.

1. **buildSrc over include-builds** — the convention-plugin setup is simple
   but pinned to Kotlin via the `kotlin-dsl` plugin. The new project should
   consider an `included-builds/build-logic` include for parallel builds +
   caching, but the buildSrc approach works fine for 36 modules.
2. **Two DI frameworks** — Koin + Injekt. The rebuild should keep Injekt
   because Aniyomi extensions require it (ADR-029 is non-negotiable for
   extension compat). Document this clearly to avoid a future "why are we
   running two DIs?" question.
3. **Five-version-catalog split** (libs/androidx/compose/kotlinx/anikuta) —
   verbose but clear. The rebuild could collapse to one catalog with
   prefixes, or keep the split for clarity.
4. **No `proguard-rules.pro` enforcement** — release builds set
   `isMinifyEnabled = false`. The rebuild should turn on R8 + write rules
   before any production release.
5. **Committed debug keystore** — `anikuta-debug.keystore` is committed
   (with `!anikuta-debug.keystore` exception in `.gitignore`). This is for
   CI build consistency (so users can update without uninstalling). The
   rebuild should keep this pattern but document it explicitly.
6. **`compileSdk = targetSdk = 36`** — bleeding edge. The rebuild can
   downgrade to 35 (Android 15) for stability; minSdk 26 is fine.
7. **The `BETA_BUILD` flag** is debug-only and unused in the release path
   (release builds don't run the update checker per spec, but the flag
   exists for future channel routing).
8. **Single Activity + Voyager** — the architecture works. The TODO in
   `AnikutaRoot.kt` notes Voyager 1.0.1 doesn't have `rememberNavigator()`,
   so the back stack is lost on Activity recreate. The rebuild should either
   pin to Voyager ≥1.1.0 (when Saver lands) or build a custom Saver.
9. **Phased DB migrations in `App.kt`** — the rebuild should consider moving
   these to a `:core:database/migration/` module + WorkManager chain so
   `App.kt` stays a thin shell.
