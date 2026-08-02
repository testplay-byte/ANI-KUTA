# 18 — Phase 3 Plan (Core Modules)

> Detailed plan for Phase 3: the core infrastructure modules.
> This is the "engine room" — identity, extensions, player, downloads, trackers, backup.
> Phase 4 adds the feature screens that use these.

---

## Overview

Phase 3 builds **14 core modules** in 4 sub-phases. Each sub-phase delivers something testable.

| Sub-phase | Modules | What it delivers |
|-----------|---------|------------------|
| **3a: Foundation** | 4 | Identity system + data repositories (anime + history) |
| **3b: Extensions** | 3 | Extension provider API + Aniyomi extension loading |
| **3c: Playback** | 3 | Video resolver + MPV player + watch progress contract |
| **3d: Supporting** | 4 | Downloads + metadata cache + trackers + backup |

**After Phase 3**: The app can browse → details → resolve video → watch (MPV) → track progress. Library + history screens come in Phase 4.

---

## Sub-Phase 3a: Foundation (Identity + Data)

### Modules

#### 1. `:core:identity`
**Purpose**: The identity system — ContentUID + ExternalReference + IdentityResolver.

**Files:**
```
core/identity/src/main/java/com/confused/anikuta/core/identity/
├── ContentUid.kt              — data class (uid, contentType, title, matchKey, coverUrl, createdAt)
├── ExternalReference.kt       — data class (uid, ecosystem, sourceId, externalId, confidence, createdAt)
├── EpisodeUid.kt              — data class (uid, contentUid, episodeNumber, matchKey)
├── EpisodeExternalRef.kt      — data class
├── Confidence.kt              — enum (HIGH, MEDIUM, LOW)
├── IdentityResolver.kt        — interface (resolveOrCreate, merge, split, suggestMerges)
└── di/IdentityModule.kt       — Koin module (binds IdentityResolver interface)
```

**Dependencies**: `:core:common` (for ContentType, Logger).
**Depends on**: nothing else in Phase 3.

**Key design**: `IdentityResolver` is an **interface**. The graph-based impl lives in `:data:identity`. This keeps `:core:identity` decoupled from the database.

#### 2. `:data:identity`
**Purpose**: SQLDelight implementation of IdentityResolver + the matching engine.

**Files:**
```
data/identity/src/main/java/com/confused/anikuta/data/identity/
├── IdentityRepositoryImpl.kt  — implements IdentityResolver
├── MatchingEngine.kt          — fuzzy title + year + type matching
├── MatchKey.kt                — normalization (lowercase, remove punctuation, etc.)
└── di/IdentityDataModule.kt   — Koin module (binds IdentityRepositoryImpl → IdentityResolver)
```

**SQLDelight queries** (in `core/database/src/main/sqldelight/`):
```
identity.sq — content_uid, external_reference, episode_uid, episode_external_ref queries
```

**Dependencies**: `:core:identity` + `:core:database` + `:core:common`.

**Matching algorithm** (from architecture plan §6):
1. Exact match: ExternalReference(ecosystem, sourceId, externalId) exists → return uid.
2. Tracker bridge: if caller provides trackerIds, find ContentUIDs with matching tracker ExternalReferences.
3. Fuzzy match: matchKey (normalized title + year + type) matches → create new ExternalReference (MEDIUM confidence).
4. No match: create new ContentUID + ExternalReference (HIGH confidence).

#### 3. `:data:anime`
**Purpose**: Repository implementations for anime, episodes, categories, library, history, watch progress.

**Files:**
```
data/anime/src/main/java/com/confused/anikuta/data/anime/
├── AnimeRepositoryImpl.kt     — CRUD for content_uid + library_entry
├── EpisodeRepositoryImpl.kt   — CRUD for episode_uid + episode_metadata_cache
├── CategoryRepositoryImpl.kt  — CRUD for category + library_entry_category
├── HistoryRepositoryImpl.kt   — history queries (recently watched, per-content)
├── WatchProgressRepositoryImpl.kt — watch_progress CRUD
├── mappers/                   — SQLDelight row → domain model mappers
└── di/AnimeDataModule.kt      — Koin module
```

**SQLDelight queries** (in `core/database/src/main/sqldelight/`):
```
library.sq     — library_entry + category + library_entry_category queries
episode.sq     — episode_uid + episode_metadata_cache queries
watch.sq       — watch_progress queries
history.sq     — history queries
```

**Dependencies**: `:core:database` + `:core:identity` + `:core:common`.

**Deliverable**: The database is now fully wired. Can add anime to library, record watch progress, query history. (No UI yet — Phase 4.)

#### 4. `:data:history`
**Purpose**: History repository + WatchProgressStore implementation. Separate from `:data:anime` per architecture plan C3 fix (WatchProgressStore layering).

**Files:**
```
data/history/src/main/java/com/confused/anikuta/data/history/
├── HistoryRepositoryImpl.kt        — history queries (recently watched, per-content)
├── WatchProgressRepositoryImpl.kt  — implements WatchProgressStore (from :core:watch-progress)
└── di/HistoryDataModule.kt         — Koin module
```

**SQLDelight queries** (in `core/database/src/main/sqldelight/`):
```
history.sq  — history queries (INSERT, SELECT recent, SELECT by_content)
watch.sq    — watch_progress queries (UPSERT, SELECT by_episode, SELECT recent)
```

**Dependencies**: `:core:database` + `:core:watch-progress` + `:core:common`.

**Why separate from `:data:anime`**: The architecture plan (C3 fix) explicitly requires `:core:player` to depend on the `WatchProgressStore` interface (in `:core:watch-progress`), NOT on `:data:anime`. If `WatchProgressRepositoryImpl` lived in `:data:anime`, then `:core:player` would need to depend on `:data:anime` — a layering violation. With `:data:history` as a separate module, `:core:player` depends only on `:core:watch-progress` (interface), and `:data:history` implements it.

---

## Sub-Phase 3b: Extension System

### Modules

#### 5. `:core:provider-api`
**Purpose**: The `ExtensionProvider` abstraction. Declares the contracts that all extension ecosystems implement. Split into per-content-type sub-interfaces (architecture plan C1 fix).

**Files:**
```
core/provider-api/src/main/java/com/confused/anikuta/core/providerapi/
├── ExtensionProvider.kt              — sealed interface (ecosystemId, displayName, supportedContentTypes)
├── VideoExtensionProvider.kt         — sub-interface (fetchEpisodeList, fetchVideoList)
├── ImageExtensionProvider.kt         — sub-interface (fetchChapterList, fetchPageList) — future
├── TextExtensionProvider.kt          — sub-interface (fetchChapterList, fetchTextContent) — future
├── Source.kt                         — data class (ecosystem, sourceId, name, lang)
├── SourceContent.kt                  — data class (title, url, thumbnail)
├── SourceEpisode.kt                  — data class (number, name, url)
├── SourceVideo.kt                    — data class (url, quality)
└── di/ProviderApiModule.kt           — Koin module (empty — just contracts)
```

**Dependencies**: `:core:common` (for ContentType).
**Note**: A provider can implement multiple sub-interfaces (e.g. Mangayomi = Video + Image). The UI filters providers by the active ContentMode.

#### 6. `:core:source-api`
**Purpose**: Aniyomi-compatible source API. Ships the `eu.kanade.tachiyomi.animesource.*` package for binary compat with Aniyomi extensions.

**Files:**
```
core/source-api/src/main/java/eu/kanade/tachiyomi/animesource/
├── AnimeSource.kt            — the source interface (Aniyomi-compat)
├── AnimeCatalogueSource.kt   — browseable source
├── AnimeInfo.kt              — anime metadata from a source
├── AnimeEpisode.kt           — episode from a source
├── Video.kt                  — playable video URL
├── SourceManager.kt          — interface for source registry
└── injekt/SourceApiInjekt.kt — Injekt bootstrap (registers Application, Context, NetworkHelper, Json)
```

**Dependencies**: `:core:network` (for NetworkHelper) + Injekt (isolated — this is the ONLY module that imports `uy.kohesive.injekt` besides `:data:extension-aniyomi`).

**Injekt isolation** (architecture plan §3 rule 6 + I1):
- `uy.kohesive.injekt` imports allowed ONLY in `:core:source-api` and `:data:extension-aniyomi`.
- In `:app`, restrict to `AniyomiInjektBootstrap.kt` (Detekt filename allowlist).

#### 7. `:data:extension-aniyomi`
**Purpose**: Loads, installs, and manages Aniyomi APK extensions. Implements `VideoExtensionProvider`.

**Files:**
```
data/extension-aniyomi/src/main/java/com/confused/anikuta/data/extension/
├── AnimeExtensionManager.kt   — manages installed extensions (3 StateFlows: installed, updating, available)
├── ExtensionLoader.kt         — DEX classloader (ChildFirstPathClassLoader)
├── ExtensionInstaller.kt      — PackageInstaller foreground service
├── ExtensionRepoApi.kt        — fetches extension lists from repos
├── ExtensionTrust.kt          — SHA-256 trust set
├── SourceMatcher.kt           — title similarity matching (Aniyomi source → AniList anime)
├── AniyomiExtensionProvider.kt — implements VideoExtensionProvider (from :core:provider-api)
└── di/ExtensionModule.kt      — Koin module
```

**Dependencies**: `:core:provider-api` + `:core:source-api` + `:core:network` + `:core:database` (for `installed_source` + `extension_repo` tables) + `:core:identity` (creates ExternalReference when a source provides content).

**Injekt isolation** (architecture plan §3 rule 6 + I1):
- `uy.kohesive.injekt` imports allowed ONLY in `:core:source-api` and `:data:extension-aniyomi`.
- In `:app`, restrict to `AniyomiInjektBootstrap.kt` (Detekt filename allowlist).
- Injekt registers 4 singletons before extensions load: `Application`, `Context`, `NetworkHelper`, `Json`.

**Deliverable**: Can install Aniyomi extensions, list their sources, fetch anime from a source.

---

## Sub-Phase 3c: Playback Pipeline

### Modules

#### 6. `:core:watch-progress`
**Purpose**: WatchProgressStore interface (contract module). Resolves the layering issue (architecture plan C3).

**Files:**
```
core/watch-progress/src/main/java/com/confused/anikuta/core/watchprogress/
├── WatchProgressStore.kt     — interface (save, get, observe)
├── WatchProgress.kt          — data class (episodeUid, position, duration, lastWatchedAt)
└── di/WatchProgressModule.kt — Koin module (interface only — impl in :data:anime)
```

**Dependencies**: `:core:common` only.
**Note**: The impl (`WatchProgressRepositoryImpl`) lives in `:data:anime` (which has DB access). `:core:player` depends on this interface (writes), `:data:anime` implements it (reads + writes to DB).

#### 7. `:core:video-resolver`
**Purpose**: Calls the extension source's `fetchVideoList` → extracts playable video URLs.

**Files:**
```
core/video-resolver/src/main/java/com/confused/anikuta/core/videoresolver/
├── VideoResolver.kt          — interface (resolve(episode) → Flow<ResolverState>)
├── ResolverState.kt          — sealed (Loading, Success(videos), Error)
├── VideoResolverImpl.kt      — calls ExtensionProvider.fetchVideoList
├── HlsHelper.kt              — PNG anti-scraping header stripping (from old project)
└── di/VideoResolverModule.kt — Koin module
```

**Dependencies**: `:core:source-api` (for Video type) + `:core:common`.
**Note**: `:core:player` does NOT depend on `:core:video-resolver`. The `:feature:anime-watch:impl` mediates (architecture plan I10).

#### 8. `:core:player`
**Purpose**: MPV wrapper + player controls + watch progress writing.

**Files:**
```
core/player/src/main/java/com/confused/anikuta/core/player/
├── AnikutaMPVView.kt          — MPV wrapper (XML-inflated, not Compose)
├── PlayerController.kt        — play/pause/seek/speed/subtitle/audio track
├── PlayerPreferences.kt       — player settings (speed, subtitle style, etc.)
├── PlaybackStateStore.kt      — saves last playback state (speed, tracks)
├── controls/
│   ├── ThemedGlass.kt         — themed dark glass for player controls
│   ├── MinimalSeekbar.kt      — seekbar
│   ├── PlayerControls.kt      — fullscreen controls overlay
│   └── EpisodeSwitchingOverlay.kt — next/prev episode overlay
├── subtitles/
│   └── SubtitleTrackFormatter.kt — subtitle styling
└── di/PlayerModule.kt         — Koin module
```

**Dependencies**: `:core:watch-progress` (writes progress) + `:core:common` + MPV native library (`aniyomi-mpv-lib`).
**Note**: `AnikutaMPVView` is XML-inflated (not Compose) because `obtainStyledAttributes` requires `XmlBlock$Parser` (from old project analysis).

**Deliverable**: Can play a video URL via MPV, save watch progress, show player controls.

---

## Sub-Phase 3d: Supporting Systems

### Modules

#### 9. `:core:download`
**Purpose**: Download manager for offline playback (HTTP + HLS + resume).

**Files:**
```
core/download/src/main/java/com/confused/anikuta/core/download/
├── DownloadManager.kt         — manages download queue
├── DownloadTask.kt            — single download task
├── HlsDownloader.kt           — HLS segment downloader (with PNG header stripping)
├── HttpDownloader.kt          — HTTP byte-range downloader
├── DownloadState.kt           — sealed (Queued, Downloading, Paused, Completed, Failed)
└── di/DownloadModule.kt       — Koin module
```

**Dependencies**: `:core:network` + `:core:database` (for `download_queue` + `downloaded_episode` tables) + `:core:common`.

#### 10. `:core:episode-metadata`
**Purpose**: Caches episode metadata (thumbnails, titles, air dates) from multiple sources.

**Files:**
```
core/episode-metadata/src/main/java/com/confused/anikuta/core/episodemetadata/
├── EpisodeMetadataCache.kt    — cache interface + impl
├── EpisodeMetadataSource.kt   — interface (fetch metadata for an episode)
├── AniListMetadataSource.kt   — fetches from AniList
├── JikanMetadataSource.kt     — fetches from Jikan (MyAnimeList API)
└── di/EpisodeMetadataModule.kt — Koin module (multi-binding: List<EpisodeMetadataSource>)
```

**Dependencies**: `:core:anilist` + `:core:database` (for `episode_metadata_cache` table) + `:core:common`.

#### 11. `:core:tracker`
**Purpose**: AniList + MAL tracker sync.

**Files:**
```
core/tracker/src/main/java/com/confused/anikuta/core/tracker/
├── Tracker.kt                 — interface (login, sync, observe)
├── AniListTracker.kt          — AniList impl (reuses :core:anilist GraphQL client)
├── MalTracker.kt              — MAL impl (OAuth)
├── TrackSyncManager.kt        — orchestrates sync across trackers
├── TrackSyncState.kt          — data class
└── di/TrackerModule.kt        — Koin module (multi-binding: List<Tracker>)
```

**Dependencies**: `:core:anilist` (reuses GraphQL client) + `:core:database` (for `tracker_link` + `tracker_sync_state` tables) + `:core:identity` (links tracker IDs to ContentUIDs).

#### 12. `:core:backup`
**Purpose**: Backup/restore + multi-app import (Aniyomi `.tachibk`, Mangayomi `.backup`).

**Files:**
```
core/backup/src/main/java/com/confused/anikuta/core/backup/
├── BackupManager.kt           — orchestrates backup/restore
├── BackupProvider.kt          — interface (serialize/deserialize one data category)
├── BackupContainer.kt         — in-memory backup data (before writing to file)
├── BackupEntry.kt             — one entry in the container
├── import/
│   ├── BackupImporter.kt      — interface (canImport, import)
│   ├── AniyomiTachibkImporter.kt — handles .tachibk protobuf (Aniyomi + Animiru + Anikku)
│   └── MangayomiBackupImporter.kt — handles .backup JSON-in-zip
├── export/
│   ├── BackupExporter.kt      — interface
│   └── AnikutaBackupExporter.kt — writes .anikuta format (v2)
├── providers/
│   ├── LibraryBackupProvider.kt       — serializes library_entry + content_uid + external_reference
│   ├── AnimeDetailsBackupProvider.kt  — serializes content_metadata_cache (description, genres, status)
│   ├── EpisodeBackupProvider.kt       — serializes episode_uid + episode_external_ref
│   ├── EpisodeMetadataBackupProvider.kt — serializes episode_metadata_cache
│   ├── CategoryBackupProvider.kt      — serializes category + library_entry_category
│   ├── HistoryBackupProvider.kt       — serializes history
│   ├── WatchProgressBackupProvider.kt — serializes watch_progress
│   ├── TrackerBackupProvider.kt       — serializes tracker_link
│   ├── SourceLinkBackupProvider.kt    — serializes installed_source + extension_repo
│   ├── DownloadBackupProvider.kt      — serializes downloaded_episode (metadata only, not files)
│   └── PreferencesBackupProvider.kt   — serializes preferences
└── di/BackupModule.kt         — Koin module (multi-binding: List<BackupProvider>, List<BackupImporter>)
```

**Dependencies**: `:core:identity` (calls `IdentityResolver.resolveOrCreate()` on import) + `:core:database` + `:data:anime` (repositories) + `:data:history` (watch progress + history repos) + `:core:preferences` + `:core:common` + `kotlinx-serialization-protobuf` (for Aniyomi format).

**Layering note** (P4 fix): `:core:backup` depends on repository interfaces from `:data:anime` and `:data:history`. To avoid a tight coupling, `:core:backup` defines a `BackupDataAccessor` interface; `:data:anime` and `:data:history` provide the implementation. This way `:core:backup` only depends on the interface, not the concrete repositories.

**Merge semantics** (from architecture plan §7.5):
- `watch_progress`: MAX(progress_a, progress_b)
- `history`: UNION by (contentUid, episodeUid, timestamp)
- `categories`: UNION by name
- `tracker_bindings`: UNION by (contentUid, ecosystem)
- `library_flag`: OR

**Deliverable**: Can export the app's data to `.anikuta` format, import from Aniyomi/Mangayomi backups.

---

## Phase 3 Module Dependency Graph

```
:core:common ←─────────────────────────────────────────────
    │                                                       
    ├── :core:identity ←── :data:identity ←── :data:anime
    │         ↑                        ↑           │
    │         │                        │           │
    │    :data:extension-aniyomi ──────┘           │
    │         │                                    │
    │    :core:source-api                         │
    │         ↑                                    │
    │    :core:video-resolver                      │
    │                                               │
    ├── :core:watch-progress ←── :core:player      │
    │         ↑                        │           │
    │    :data:anime (impl) ────────────┘           │
    │                                               │
    ├── :core:download ←── :core:network           │
    ├── :core:episode-metadata ←── :core:anilist   │
    ├── :core:tracker ←── :core:anilist ───────────┘
    └── :core:backup ←── :data:anime + :core:identity
```

---

## Phase 3 Deliverables

After all 4 sub-phases:
1. **Identity system** working — ContentUID + ExternalReference, matching engine.
2. **Database** fully populated — all 17 active tables.
3. **Aniyomi extensions** loadable — can install + browse sources.
4. **Video pipeline** working — resolve video URL → play via MPV → save progress.
5. **Downloads** working — queue + download + offline playback.
6. **Trackers** working — AniList/MAL sync.
7. **Backup/restore** working — export + import from Aniyomi/Mangayomi.

**What's NOT in Phase 3** (deferred to Phase 4):
- UI screens for library, history, watch, settings, etc.
- The Phase 2 Browse + Details screens get enhanced to use the new data layer.

---

## Phase 3 Build Order (Recommended)

| Step | Module | Sub-phase | Why this order |
|------|--------|-----------|----------------|
| 1 | `:core:identity` | 3a | Foundation — everything depends on it. |
| 2 | `:data:identity` | 3a | Implements the identity interface. |
| 3 | `:data:anime` | 3a | Repositories for library/episodes/categories. |
| 4 | `:core:watch-progress` | 3c | Contract interface (needed before :data:history). |
| 5 | `:data:history` | 3a | Watch progress impl + history queries (needs watch-progress interface). |
| 6 | `:core:provider-api` | 3b | ExtensionProvider contracts (needed before :data:extension-aniyomi). |
| 7 | `:core:source-api` | 3b | Aniyomi-compat source API (Injekt isolated). |
| 8 | `:data:extension-aniyomi` | 3b | Extension loader (needs provider-api + source-api + identity). |
| 9 | `:core:video-resolver` | 3c | Needs source-api. |
| 10 | `:core:player` | 3c | Needs watch-progress interface. MPV native lib. |
| 11 | `:core:download` | 3d | Needs network. |
| 12 | `:core:episode-metadata` | 3d | Needs anilist + database. |
| 13 | `:core:tracker` | 3d | Needs anilist + identity. |
| 14 | `:core:backup` | 3d | Needs identity + data:anime + data:history. Last — it ties everything together. |

---

## Open Questions for User

1. **MPV native library**: The old project uses `aniyomi-mpv-lib`. Do we use the same, or build a new wrapper? (Recommend: reuse — it's proven.)
2. **Aniyomi extension repo**: Do we include the default Aniyomi extension repo URL, or let users add their own? (Recommend: include defaults but make them removable.)
3. **Tracker OAuth**: AniList uses OAuth2. MAL uses OAuth2. Do we implement both in Phase 3, or AniList first? (Recommend: AniList first — MAL is similar pattern, can follow quickly.)
4. **Backup format**: The `.anikuta` v2 format includes ContentUID + ExternalReference. Should it also include downloaded files (large)? (Recommend: no — backup is metadata only. Downloads are re-downloadable.)

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| MPV native lib build issues | Medium | High | Use pre-built `aniyomi-mpv-lib` AAR. Test on CI. |
| Aniyomi extension compat breaks | Low | High | Pin to a specific Aniyomi source-api version. Test with a few popular extensions. |
| Injekt isolation hard to enforce | Medium | Medium | Detekt rule (path + filename allowlist). CI check. |
| Phase 3 is large (12 modules) | High | Medium | Split into 4 sub-phases. Each sub-phase is independently testable. |
| SQLDelight schema migration complexity | Medium | Medium | Use the old project's proven migration pattern (one-shot flags, try/catch). |
