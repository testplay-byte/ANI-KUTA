# Module Map

> Every Gradle module: name, job, dependencies. Verified against `settings.gradle.kts` + `build.gradle.kts` files.
> The architecture **design/concept** (layer diagrams, module graph) lives in `architecture.md`.

## Status: 50 modules — ALL BUILT ✅

```
:app  ──→  :feature:*:{api,impl}  ──→  :core:*  ──→  :core:common
 :app  ──→  :data:extension  ──→  :core:source-api (Aniyomi binary-compat)
 :feature:debug-bubble  (debugImplementation only — release builds contain zero debug-bubble code)
```

### Rules
- Feature modules never depend on each other. Communicate via `:core` contracts or navigation (`NavKey`).
- Feature modules use **api/impl split** (Nav3 Pattern B — though Nav3 itself was removed D-150, the split pattern stayed): `:feature:X:api` (NavKey + contracts) + `:feature:X:impl` (Screen + ViewModel).
- Core modules may depend on other core modules, but no cycles.
- `:core:source-api` uses Injekt (isolated to Aniyomi ext binary-compat); everything else uses Koin.

---

## :app (1 module)
| Module | Job | Depends On |
|--------|-----|------------|
| `:app` | App shell — `AnikutaApp` (Koin 22 modules + Injekt registration + crash handler), `MainActivity` (hand-rolled nav `when(currentKey)`), ViewModels, screens not yet modularized (Profile, Settings, Notifications settings), ErrorActivity, `:app/src/debug` + `:app/src/release` source sets | all `:feature:*:impl` + most `:core:*` + `:data:extension` |

## :core (26 modules)
| Module | Job | Key Files | Depends On |
|--------|-----|-----------|------------|
| `:core:common` | Logger wrapper (filtered, toggleable), Dispatchers, Result, ContentType, constants | `Logger.kt` | — |
| `:core:designsystem` | AnikutaTheme — lime #B1F256 accent, warm-dark surfaces, 10 accent presets (D-053), components | `Theme.kt`, `Color.kt`, `Type.kt`, `Shapes.kt` | `:core:common` |
| `:core:database` | SQLDelight schema — **26 tables / 15 .sq files**. `DatabaseDriverFactory` (onOpen migration: FK enforcement, index management). | `*.sq`, `DatabaseDriverFactory.kt` | `:core:common` |
| `:core:preferences` | `PreferenceStore` (reactive Flow accessors), AppPreferences, PlayerPreferences, DownloadPreferences, NotificationPreferences, AutoLinkPreferences, DebugBubblePreferences | `PreferenceStore.kt` | `:core:common` |
| `:core:navigation-api` | `NavKey` sealed-class contracts (hand-rolled, D-150 — NOT Nav3), `ContentMode`, `\u001F` delimiter constant | `NavKey.kt` | `:core:common` |
| `:core:network` | OkHttp `HttpClientFactory` (browser UA, 2 clients: default + download-qualified) | `HttpClientFactory.kt` | `:core:common` |
| `:core:anilist` | AniList GraphQL client + `AniListDetailsProvider` (MetadataProvider) — browse, details, schedule | `AniListApi.kt`, `AniListDetailsProvider.kt` | `:core:network`, `:core:common` |
| `:core:watch-progress` | `SqlDelightWatchProgressStore` — episode_key standardization, 85% auto-mark (Phase WP), continue-watching query | `SqlDelightWatchProgressStore.kt` | `:core:database`, `:core:common` |
| `:core:activity-tracker` | `ActivityTracker` + `ActivityDetector` — 365-day/unlimited event log (D-039, D-045) | `ActivityTracker.kt` | `:core:database`, `:core:common` |
| `:core:provider-api` | `ExtensionProvider` + Video/Image/Text sub-interfaces (multi-extension D-031) | `ExtensionProvider.kt` | `:core:common` |
| `:core:source-api` | Aniyomi binary-compat contract (`eu.kanade.tachiyomi.animesource.*`, 36+ files). Injekt-isolated. | `AnimeHttpSource.kt`, `Video.kt`, etc. | `:core:common` (+ Injekt) |
| `:core:player-mpv-lib` | AAR wrapper module for `aniyomi-mpv-lib` (swappable players — D-044) | `build.gradle.kts` (AAR) | — |
| `:core:player` | `AnikutaMPVView` (Compose AndroidView), `PlayerStateHolder`, `PlayerObserver`, `PlayerInitializer`, mpv.conf, sheets (Quality, Subtitle, Speed, Audio) | `AnikutaMPVView.kt`, `PlayerStateHolder.kt` | `:core:player-mpv-lib`, `:core:common`, `:core:designsystem` |
| `:core:video-resolver` | `VideoResolver` — single resolve() + buildServers() (D-066 no double-resolve) | `VideoResolver.kt` | `:core:source-api`, `:core:common` |
| `:core:download` | 7-state machine + `DownloadManager`, `HttpDownloader`, `HlsDownloader`, `DownloadStorageProvider` (SAF + .data.json), `AutoDownloadEngine`, `DownloadScanner`, `DownloadOrchestrator`, `DownloadService`, `DownloadNotificationManager` (Phase DL.0-DL.8) | `DownloadQueue.kt`, `HttpDownloader.kt`, etc. | `:core:database`, `:core:network`, `:core:common`, `:core:video-resolver` |
| `:core:metadata` | `AnimeMetadataCache` + `EpisodeMetadataCache` (Phase D — local-first, never expires) + `MetadataMerger` + `MetadataRegistry` | `MetadataMerger.kt` | `:core:database`, `:core:common` |
| `:core:tracker-api` | `Tracker` interface, `BaseTracker`, `TrackEntry`, `TrackSyncManager` (one-way internal→external relay) | `Tracker.kt`, `TrackSyncManager.kt` | `:core:common` |
| `:core:tracker-anilist` | `AniListTracker` — OAuth + GraphQL (currently a placeholder stub — Phase 3d; full impl deferred) | `AniListTracker.kt` | `:core:tracker-api`, `:core:preferences`, `:core:common` |
| `:core:smart-matcher` | `SmartMatcher` + `AutoLinkService` — Levenshtein fuzzy matching, AniList search + link cache (Phase B) | `SmartMatcher.kt`, `AutoLinkService.kt` | `:core:anilist`, `:core:common` |
| `:core:content` | `ContentRepository` + `ContentResolver` + `ContentIdGenerator` + `ContentSeeder` — two-ID system (Main ID + Content ID), 8 content tables (Phase C) | `ContentRepository.kt` | `:core:database`, `:core:common` |
| `:core:data-cache` | `DataCacheRepository` — anime_metadata_cache + data_cache_episode + browse_cache (Phase D.1) | `DataCacheRepository.kt` | `:core:database`, `:core:common` |
| `:core:updates` | Smart update engine + WorkManager — new-episode detection (Phase UP) | `UpdateEngine.kt` | `:core:database`, `:core:common` |
| `:core:schedule` | Schedule list + calendar view data — episode_schedule table (Phase SC) | `ScheduleRepository.kt` | `:core:database`, `:core:anilist`, `:core:common` |
| `:core:ratings` | `RatingStore` — user_rating + user_episode_rating (0-100 scale, Phase TR) | `RatingStore.kt` | `:core:database`, `:core:common` |
| `:core:notifications` | `NotificationManager` + `NotificationConfigStore` — 2 channels (default + silent), per-anime tri-state config (Phase NOTIF) | `NotificationManager.kt` | `:core:database`, `:core:common` |
| `:core:debug-api` | `DebugContext`, `DbReference`, `DebugAction`, `LocalDebugContext` (reader/writer CompositionLocals) — types-only, always on classpath (D-162) | `DebugContext.kt` | `:core:common` |

## :data (1 module)
| Module | Job | Depends On |
|--------|-----|------------|
| `:data:extension` | `ExtensionLoader` (child-first classloader), `ExtensionManager` (install/list/trust/enable), `TrustService`, `AnimeExtensionApi`, extension installer service | `:core:source-api`, `:core:provider-api`, `:core:preferences`, `:core:common` |

## :feature (18 modules — api/impl split)
| Module | Job | api deps | impl deps |
|--------|-----|----------|-----------|
| `:feature:anime-browse` | Browse screen — trending grid + continue-watching carousel (D-170) + pull-to-refresh | `:core:navigation-api` | `:core:designsystem`, `:core:anilist`, `:core:data-cache`, `:core:content`, `:core:watch-progress` |
| `:feature:anime-details` | Details screen — banner, cover, synopsis, episodes, auto-link badge, data-source selector, star rating (D-170), download controls | `:core:navigation-api` | `:core:designsystem`, `:core:anilist`, `:core:content`, `:core:metadata`, `:core:video-resolver`, `:core:smart-matcher`, `:core:data-cache`, `:core:ratings` |
| `:feature:anime-library` | Library screen — grid/list, categories, multi-select, sort, customize sheet | `:core:navigation-api` | `:core:designsystem`, `:core:content`, `:core:anilist`, `:core:data-cache` |
| `:feature:anime-search` | Search screen — AniList search + filter sheet + recent searches | `:core:navigation-api` | `:core:designsystem`, `:core:anilist`, `:core:data-cache` |
| `:feature:extensions-settings` | Extensions management — install/list/trust/enable, repo management, source prefs | `:core:navigation-api` | `:core:designsystem`, `:data:extension` |
| `:feature:download` | Downloads screen — live queue, bulk actions, downloaded files page | `:core:navigation-api` | `:core:designsystem`, `:core:download` |
| `:feature:watch` | Watch screen — MPV player surface + controls + episode switching + subtitle/audio sheets + episode star rating (D-170). ADR-025 carve-out: screen owns MPV lifecycle. | `:core:navigation-api` | `:core:player`, `:core:video-resolver`, `:core:watch-progress`, `:core:ratings`, `:core:download` |
| `:feature:anime-history` | History screen — watch history list + swipe-to-delete | `:core:navigation-api` | `:core:designsystem`, `:core:watch-progress`, `:core:activity-tracker` |
| `:feature:updates` | Updates screen — new-episode feed | `:core:navigation-api` | `:core:designsystem`, `:core:updates` |
| `:feature:debug-bubble` | Debug Bubble — floating draggable overlay, 5-tab panel (Current Screen, Database, Console, Network, App Info). **debugImplementation only** (D-163). | `:core:debug-api`, `:core:database`, `:core:network`, `:core:common` | (same) |

## Module Count Summary
| Layer | Count |
|-------|-------|
| `:app` | 1 |
| `:core:*` | 30 |
| `:data:*` | 1 |
| `:feature:*` (api+impl splits) | 18 |
| **Total** | **50** |
