# Module Map

> Every Gradle module: name, job, dependencies. Verified against `settings.gradle.kts` + every module's `build.gradle.kts` at **Round 115 (2026-10-02)**.
> The architecture **design/concept** (layer diagrams, module graph) lives in `architecture.md`.

## Status: 57 modules — ALL BUILT ✅

```
:app  ──→  :feature:*:{api,impl}  ──→  :core:*  ──→  :core:common
 :app  ──→  :data:extension  ──→  :core:source-api (Aniyomi binary-compat)
 :app  ──→  :data:cloudstream  ──→  :core:cloudstream-api (CS3 plugin binary-compat)
 :feature:debug-bubble  (debugImplementation only — release builds contain zero debug-bubble code)
```

### Rules
- Feature **impl** modules may depend on sibling **api** modules (anime-details:api is depended on by browse/library/search/history/updates impls; watch:api by history impl) — but NEVER on a sibling **impl**. Only `:app` depends on impls.
- Feature modules use **api/impl split** (Nav3 Pattern B — though Nav3 itself was removed D-150, the split pattern stayed): `:feature:X:api` (NavKey + contracts) + `:feature:X:impl` (Screen + ViewModel).
- Core modules may depend on other core modules, but no cycles.
- `:core:source-api` uses Injekt (isolated to Aniyomi ext binary-compat); everything else uses Koin.

---

## :app (1 module)
| Module | Job | Depends On |
|--------|-----|------------|
| `:app` | App shell — `AnikutaApp` (Koin **30 modules** + `debugKoinModules()` 2 more in debug + Injekt registration + crash handler), `MainActivity` (hand-rolled nav — **44 NavKey branches**, 3075 lines), BrowsePreloader, non-modularized screens (More, Profile, Settings, Notifications, updates, download, error, webview, pluginimport) | all `:core:*` (except `:core:seasons`, transitive via common) + `:data:extension` + `:data:cloudstream` + all `:feature:*` (debug-bubble via `debugImplementation`) |

## :core (33 modules)
| Module | Job | Key Files | Depends On |
|--------|-----|-----------|------------|
| `:core:common` | Logger, DispatcherProvider, ContentType, EpisodeTitleParser, EpisodeGridTitles, HapticHelper, UnifiedAnime models, DASH-manifest helpers | `Logger.kt`, `EpisodeTitleParser.kt`, `model/UnifiedAnime.kt` | `:core:seasons` (EpisodeTitleParser consumes SeasonDetector) |
| `:core:seasons` | D-312 dedicated season-management engine — SeasonDetector pattern registry + provider-hint fusion | `SeasonDetector.kt`, `SeasonPatterns.kt`, `SeasonModels.kt` | — |
| `:core:designsystem` | AnikutaTheme (lime #B1F256, 10 accent presets + CUSTOM — D-053, warm-dark ramp, AMOLED), components, SharedTransitionLocals (D-320), Palette-based adaptive accent (D-223), the shared episode-list kit (D-727) | `Theme.kt`, `AccentPreset.kt`, `AdaptiveAccent.kt` | `:core:common`, `:core:video-resolver` (model types only — D-721) |
| `:core:database` | SQLDelight schema — **25 tables / 17 .sq files** (app.sq intentionally empty since D-198). `DatabaseDriverFactory` (onOpen idempotent migration: FK enforcement, hasColumn-guarded ALTERs) | `DatabaseDriverFactory.kt`, `content.sq`, `watch.sq` | — |
| `:core:preferences` | `PreferenceStore` (reactive Flow accessors) + per-domain preference objects (App, Player, EpisodeList, PlayerEpisodeList, Notification, AutoLink, AppIcon, Update, Debug) + SettingsRepository + NsfwFilterMode | `PreferenceStore.kt`, `SettingsRepository.kt` | `:core:common`, `:core:database` |
| `:core:navigation-api` | `NavKey` sealed-class contracts (hand-rolled D-150 — NOT Nav3), `\u001F` delimiter constant | `NavKey.kt` | — |
| `:core:network` | OkHttp `HttpClientFactory` (browser UA; default + download-qualified clients) | `HttpClientFactory.kt` | — |
| `:core:anilist` | AniList GraphQL client + `AniListDetailsProvider` + BrowseCacheCodec — browse, details, schedule | `AniListApi.kt`, `AniListDetailsProvider.kt` | `:core:common`, `:core:network` |
| `:core:watch-progress` | `SqlDelightWatchProgressStore` — episode_key standardization (mainId\|padded-5), 85% auto-mark, continue-watching | `SqlDelightWatchProgressStore.kt`, `WatchPreferences.kt` | `:core:common`, `:core:database`, `:core:preferences` |
| `:core:activity-tracker` | `ActivityTracker` + `ActivityDetector` — 365-day/unlimited activity_event log (D-039, D-045) | `ActivityTracker.kt`, `ActivityEventType.kt` | `:core:common`, `:core:database` |
| `:core:provider-api` | `ExtensionProvider` + Video/Image/Text sub-interfaces (multi-extension D-031) | `ExtensionProvider.kt` | `:core:common` |
| `:core:source-api` | Aniyomi/Keiyoushi binary-compat contract (`eu.kanade.tachiyomi.animesource.*`, NetworkHelper, CloudflareInterceptor, Brotli). Injekt-isolated. | `AnimeHttpSource.kt`, `Video.kt` | `:core:network` |
| `:core:player-mpv-lib` | AAR wrapper module for `aniyomi-mpv-lib` (swappable players — D-044) | `build.gradle.kts` (AAR only) | — |
| `:core:player` | `AnikutaMPVView` (Compose AndroidView), `PlayerStateHolder`, `PlayerObserver`, `PlayerInitializer`, controls/ + subtitles/, mpv.conf | `AnikutaMPVView.kt`, `PlayerStateHolder.kt` | `:core:player-mpv-lib`, `:core:common`, `:core:designsystem`, `:core:preferences`, `:core:watch-progress`, `:core:source-api`, `:core:network` |
| `:core:video-resolver` | `VideoResolver` (single resolve() + buildServers — D-066 no double-resolve), `ResolvedVideosRegistry`, ResolverDebugReport | `VideoResolver.kt`, `ResolvedVideosRegistry.kt` | `:core:common`, `:core:source-api`, `:core:provider-api` |
| `:core:download` | 7-state machine: `DownloadManager`/`DownloadQueue`, Http/Hls/**Dash** downloaders, SAF `DownloadStorageProvider` (.data.json), `AutoDownloadEngine`, `DownloadScanner`/`Service`/`NotificationManager`, DASH offline manifests (round-57 CS downloads) | `DownloadQueue.kt`, `DashDownloader.kt`, `DownloadStorageProvider.kt` | `:core:common`, `:core:database`, `:core:preferences`, `:core:network`, `:core:content`, `:core:video-resolver`, `:core:activity-tracker`, `:core:cs-player` |
| `:core:metadata` | Multi-source episode-metadata engine — `MetadataProvider` registry (AniList, Kitsu, Jikan, AniZip, Local) + `MetadataMerger` + `EpisodeMetadataEngine` | `MetadataRegistry.kt`, `EpisodeMetadataEngine.kt` | `:core:common`, `:core:database`, `:core:anilist` |
| `:core:tracker-api` | `Tracker` interface, `BaseTracker`, `TrackerTypes` | `Tracker.kt`, `BaseTracker.kt` | `:core:common` |
| `:core:tracker-anilist` | **FULL AniList tracker (no longer a stub)**: `AniListOAuth`, `AniListTracker` (GraphQL sync, ~580 lines), `TrackSyncManager`, `TrackEntryRepository`, `TrackingStateRepository`, `TrackingWatchSyncBridge`, status mapper (module total 1471 lines) | `AniListTracker.kt`, `TrackingWatchSyncBridge.kt` | `:core:common`, `:core:tracker-api`, `:core:preferences`, `:core:anilist`, `:core:database`, `:core:content`, `:core:activity-tracker`, `:core:watch-progress` |
| `:core:smart-matcher` | `SmartMatcher` + `AutoLinkService` + `ReverseAutoLinkService` — Levenshtein fuzzy matching, title normalization, AniList search + link cache (Phase B) | `SmartMatcher.kt`, `AutoLinkService.kt`, `TitleNormalizer.kt` | `:core:common`, `:core:preferences`, `:core:anilist`, `:core:source-api`, `:data:extension` |
| `:core:share` | Round 101 (WS-C) share system — `ContentShareLinkFactory` + share models: three targets (extension URL, data-source page, `anikuta://content/{mainId}` deep link); pure link-building, no Android framework dep | `ContentShareLinkFactory.kt` | `:core:common` |
| `:core:content` | `ContentRepository` + `ContentResolver` + `ContentIdGenerator` + `GenreRepository` — two-ID system (Main ID + Content ID); content.sq 4 tables + genres | `ContentRepository.kt`, `ContentIdGenerator.kt` | `:core:common`, `:core:database`, `:core:preferences` |
| `:core:data-cache` | `DataCacheRepository` — browse_cache + data_cache_episode (Phase D.1; anime_metadata_cache dropped) | `DataCacheRepository.kt` | `:core:common`, `:core:database` |
| `:core:updates` | Smart release-check engine + WorkManager (`UpdateCheckWorker`, `SmartReleaseCheckWorker`/`Scheduler`, `ScheduleNotificationWorker`, `ScheduleRefresher`, `ActualReleaseUpdater`) + `UpdateStore` + check reporting | `UpdateEngine.kt`, `SmartReleaseCheckWorker.kt` | `:core:common`, `:core:database`, `:core:content`, `:core:source-api`, `:core:watch-progress`, `:core:preferences`, `:data:extension` |
| `:core:schedule` | `ScheduleEngine` + `ScheduleStore` — episode_schedule table + airing-time computation (Phase SC) | `ScheduleEngine.kt` | `:core:common`, `:core:database`, `:core:content`, `:core:anilist`, `:core:updates`, `:core:notifications` |
| `:core:ratings` | `RatingStore` — user_rating + user_episode_rating (0-100 scale, Phase TR) | `RatingStore.kt` | `:core:common`, `:core:database` |
| `:core:notifications` | `NotificationManager` + `NotificationConfigStore` + `NotificationArtProvider` — per-anime tri-state config, poster art (reworked round 80, D-566..D-569) | `NotificationManager.kt`, `NotificationArtProvider.kt` | `:core:common`, `:core:database`, `:core:content`, `:core:preferences` |
| `:core:app-update` | In-app updater — `AppUpdateManager`, `GitHubUpdateSource`, `UpdateDownloader`/`UpdateDownloadService`, `ApkInstaller`, `UpdateNotificationManager` | `AppUpdateManager.kt`, `GitHubUpdateSource.kt` | `:core:common`, `:core:preferences` |
| `:core:ads` | D-272 smart-link interstitial ad system — `AdsCoordinator`, `SmartLinkAdInterstitial`, `ReturnPillView`/`SmartLinkReturnPillController`, `AdsRepository`, overlay permissions | `AdsCoordinator.kt`, `SmartLinkAdInterstitial.kt` | `:core:common`, `:core:preferences`, `:core:designsystem` |
| `:core:playback-cache` | Video caching — localhost `CacheProxyServer` + `PlaybackCacheManager` + `SpanInputStream` (playback_cache_entry table) | `CacheProxyServer.kt`, `PlaybackCacheManager.kt` | `:core:common`, `:core:database`, `:core:preferences`, `:core:network` |
| `:core:debug-api` | `DebugContext`, `DbReference`, `DebugAction`, `LocalDebugContext` — types-only, always on classpath (D-162) | `DebugContext.kt` | `:core:common` |
| `:core:cloudstream-api` | CloudStream V2 — clean-room CS3 plugin binary-compat surface (`com.lagradost.cloudstream3.*` — MainAPI, plugins, extractors, syncproviders) | the `com/lagradost/cloudstream3/` tree | — |
| `:core:cs-player` | CloudStream V2 (task 52) — Media3 ExoPlayer playback engine host; CS links NEVER touch MPV | `CsPlayerEngine.kt`, `CsHttpDataSourceFactory.kt`, `LocalDashDataSource.kt` | `:core:common` |

## :data (2 modules)
| Module | Job | Depends On |
|--------|-----|------------|
| `:data:extension` | Aniyomi extension system runtime — `ExtensionLoader` (child-first classloader), `ExtensionManager`, `TrustService`, repo management (`ExtensionRepoRepository`/`Api`), installer (+ provider bridge, AnimeExtensionApi) | `:core:common`, `:core:database`, `:core:network`, `:core:preferences`, `:core:provider-api`, `:core:source-api` |
| `:data:cloudstream` | CloudStream V2 extension runtime (loader/manager/repos/bridge) — `CloudstreamPluginManager`/`Loader`/`Installer` (+CsSharedPluginFormat), repo mgmt, content bridge (`CloudstreamContentRepository` + `CloudstreamAnimeSourceBridge` + `CsBrowseLoader`), playback (`CloudstreamLinkResolver`, `CsSourceMemory`) | `:core:cloudstream-api` (api), `:core:provider-api` (api), `:core:source-api` (api), `:core:common`, `:core:preferences`, `:core:cs-player` |

## :feature (21 modules — 12 features)
| Module | Job | api deps | impl deps |
|--------|-----|----------|-----------|
| `:feature:anime-browse` | Browse screen — trending grid + continue-watching carousel (D-170) + pull-to-refresh + hero | `:core:navigation-api` | own api + `:feature:anime-details:api` + `:core:{debug-api, navigation-api, designsystem, preferences, anilist, common, data-cache, watch-progress, content}` |
| `:feature:anime-details` | Details screen — banner/cover/synopsis/episodes/elements, auto-link badge, source selector, star rating, download controls, seasons, share, trackers (22 files) | `:core:navigation-api` | own api + `:core:{debug-api, designsystem, anilist, common, seasons, metadata, navigation-api, preferences, source-api, video-resolver, smart-matcher, share, content, data-cache, download, watch-progress, ratings, activity-tracker, updates, schedule, notifications, tracker-anilist, tracker-api}` + `:data:{extension, cloudstream}` |
| `:feature:anime-library` | Library screen — grid/list, categories, multi-select, sort, customize sheet | `:core:navigation-api` | own api + `:feature:anime-details:api` + `:core:{designsystem, anilist, common, content, data-cache, watch-progress, navigation-api, database, preferences}` |
| `:feature:anime-search` | Search screen — AniList + extension search, filter sheet, recent searches, CloudStream category browse (CsCategoryScreen) | `:core:navigation-api` (+ CsCategoryKey, ExtensionAnime models, tab-exit signal) | own api + `:feature:anime-details:api` + `:core:{designsystem, anilist, common, data-cache, navigation-api, preferences, source-api, activity-tracker}` + `:data:{extension, cloudstream}` |
| `:feature:extensions-settings` | Extensions management — install/list/trust/enable, repo management, source prefs, CloudStream plugin detail screen, extension TEST SUITE (search/home/details/episodes/resolve/stream/ping + charts) | `:core:navigation-api` | own api + `:core:{designsystem, common, navigation-api, preferences, source-api, provider-api, cs-player}` + `:data:{extension, cloudstream}` |
| `:feature:download` | Downloads screens — live queue (drag-reorder), bulk actions, downloaded files page, download settings | `:core:{download, debug-api, designsystem, common, preferences, navigation-api, video-resolver}` | (single module) |
| `:feature:onboarding` | D-403 (round 28) first-run setup wizard (single module — the feature:download precedent) — theme picker (live re-theme) + permissions | `:core:{download, preferences, designsystem, common, navigation-api}` | (single module) |
| `:feature:watch` | Watch screen (MPV) — player surface + controls + episode switching + subtitle/audio sheets + manual subtitle import. ADR-025 carve-out: screen owns MPV lifecycle | `:core:navigation-api`, `:core:common` (WatchKey — 17 fields) | own api + `:core:{debug-api, designsystem, common, navigation-api, player, player-mpv-lib, preferences, video-resolver, watch-progress, ratings, download, playback-cache, activity-tracker, source-api}` + `:data:extension` |
| `:feature:cs-watch` | CloudStream V2 (task 52) — the dedicated CS watch screen (Media3 ExoPlayer via `:core:cs-player`): loadLinks resolution, resolve sheet, subtitle fetch/overlay/settings, DASH quality probe — mirrors the watch UX, zero aniyomi code shared | `:core:navigation-api`, `:core:common` (CsWatchKey + CsSubDubSiblings) | own api + `:core:{cs-player, designsystem, common, navigation-api, preferences, watch-progress, ratings, download}` + `:data:cloudstream` |
| `:feature:anime-history` | History screen — watch history list + swipe-to-delete | `:core:navigation-api` | own api + `:feature:anime-details:api` + `:feature:watch:api` + `:core:{navigation-api, designsystem, common, content, watch-progress, preferences}` |
| `:feature:updates` | Updates feed + schedule screens (list + calendar) | `:core:navigation-api` | own api + `:feature:anime-details:api` + `:core:{navigation-api, designsystem, common, content, updates, preferences, schedule}` |
| `:feature:debug-bubble` | Debug Bubble — floating draggable overlay, 5-tab panel (Screen/Database/Console/Network/App Info). **debugImplementation only** (D-163) — zero code in release builds | `:core:{debug-api, common, designsystem, preferences, database, network}` | (single module) |

## Module Count Summary
| Layer | Count |
|-------|-------|
| `:app` | 1 |
| `:core:*` | 33 |
| `:data:*` | 2 |
| `:feature:*` | 21 (12 features: 9 api/impl splits — anime-browse, anime-details, anime-library, anime-search, extensions-settings, watch, cs-watch, anime-history, updates — + 3 singles: download, onboarding, debug-bubble) |
| **Total** | **57** |

---
*Round-115 refresh (2026-10-02): fully re-verified against settings.gradle.kts + every module's build.gradle.kts by a read-only research sub-agent (task 2-a). The previous version carried three eras of drift at once (header 50 / core section 26 / summary 30) — the summary is now single-sourced.*
