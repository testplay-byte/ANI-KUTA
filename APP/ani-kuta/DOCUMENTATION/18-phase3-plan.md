# 18 — Phase 3 Plan (Refined)

> **Refined based on user feedback (Session: 2026-08-02).**
> Key changes: defer identity system + complex DB, prioritize extensions, copy player from old project, add internal tracking, split metadata fetching, defer backup/restore.

---

## ⚠️ Honest Flag: The DB Deferral Contradiction

The user asked to **defer the database + identity system to a later phase**. This is sound for the COMPLEX identity system (ContentUID + ExternalReference + matching engine). BUT we **cannot fully defer the database** because:

1. **Extensions need storage** — installed extensions, sources, repos must persist across app launches.
2. **Episode metadata caching needs storage** — fetched thumbnails/titles/air dates need to be cached.
3. **Internal tracking needs storage** — the user explicitly wants a "full-fledged tracking system" that records when/what the user watches. This REQUIRES database tables.
4. **User customizations need storage** — custom thumbnails, titles, descriptions per episode.

**Resolution (confirmed by sub-agent review):**
- ✅ **DEFER**: The complex identity system (ContentUID + ExternalReference + matching engine + merge/split). This is the hard part — do it later when we understand the data better.
- ✅ **KEEP**: Basic database tables for extensions, metadata cache, internal tracking, user customizations, watch progress, downloads. These use a **temporary content key** (`content_key` string) instead of ContentUID.
- ✅ **DEFER**: Backup/restore entirely. Needs the identity system first.

**Temporary content key format** (I1 fix):
- Canonical: `"<ecosystem>:<source_id|->:<external_id>"` (e.g., `"animiru:gogo:aot"`, `"anilist:-:16498"`).
- All writers use this format. Phase 4 migration parses it deterministically into ContentUID.

**Phase 2 baseline** (C5 fix — verified by reading `app.sq`):
- Phase 2 created ONLY `app_metadata` (key-value table).
- The identity tables (content_uid, external_reference, etc.) from architecture plan §13 were NOT built.
- Architecture plan §13 was wrong about Phase 2 scope. This plan is the source of truth.

---

## Refined Phase 3 Structure (4 Sub-Phases)

| Sub-phase | Focus | Modules | Status |
|-----------|-------|---------|--------|
| **3a: Foundation** | Basic DB + internal tracking + watch progress + preferences | 4 | Refined |
| **3b: Extensions** | Extension system (MOST CRUCIAL) | 3 | Refined |
| **3c: Playback** | Player + video resolver + downloads | 4 | Refined |
| **3d: Supporting** | Metadata + tracker | 3 | Refined |

**Total: 14 modules** (added `:core:watch-progress` per C3 fix; collapsed metadata from 3 to 1 per I9 fix).

**Deferred to Phase 4+ (after we understand the data):**
- `:core:identity` + `:data:identity` — the complex identity system.
- `:core:backup` — backup/restore + multi-app import.
- `:data:anime` (library/history repositories) — needs identity system.

---

## Sub-Phase 3a: Foundation (Basic DB + Internal Tracking)

### Modules

#### 1. `:core:database` (ENHANCED — already exists from Phase 2)
**Changes**: Add basic tables (NO identity system yet). Uses `content_key` / `episode_key` strings (temporary — Phase 4 migrates to ContentUID).

**New tables** (C1/C4/C6 fix — all tables from 17-schema that don't require identity):
- `installed_source` — installed extensions (ecosystem, source_id, name, version, package_name, **signature_fingerprint**, is_enabled, installed_at, last_updated_at). [C4 fix: signature_fingerprint restored for trust verification]
- `extension_repo` — extension repos (ecosystem, url, name, added_at). **NO default URLs** (user adds their own).
- `episode_metadata_cache` — cached episode metadata (episode_key PK, title, thumbnail_url, air_date, description, source, updated_at).
- `content_metadata_cache` — cached content metadata (content_key PK, title, description, genres, status, year, cover_url, source, updated_at).
- `activity_event` — internal tracking log (id, event_type, content_key, episode_key, session_id, route, content_type, duration_ms, payload, timestamp). Indexed on timestamp DESC + event_type + content_key.
- `user_customization` — user's custom metadata overrides (id, content_key, episode_key, custom_title, custom_thumbnail, custom_description, updated_at). [I3 fix: partial unique indexes for content-level (episode_key IS NULL) vs episode-level (episode_key IS NOT NULL)]
- `watch_progress` — [C2 fix: RESTORED separate table] (episode_key PK, position, duration, completed, completed_at, last_watched_at). Indexed on last_watched_at DESC for "Continue Watching".
- `download_queue` — [C6 fix: added] (id, episode_key, state, progress, error_message, queued_at, started_at, completed_at).
- `downloaded_episode` — [C6 fix: added] (episode_key PK, file_path, file_size, quality, downloaded_at).
- `app_metadata` — already exists (key-value store).

> **Note**: All tables use `content_key` / `episode_key` (strings, format: `"<ecosystem>:<source_id|->:<external_id>"`). The identity system (Phase 4) will introduce ContentUID and migrate these tables.

#### 2. `:core:activity-tracker` (NEW — internal tracking system)
**Purpose**: The user's KEY requirement — a full-fledged internal tracking system. Records everything the user does. This is NOT tracker sync (AniList/MAL) — this is LOCAL tracking for the user's own stats.

**Files:**
```
core/activity-tracker/src/main/java/com/confused/anikuta/core/activitytracker/
├── ActivityTracker.kt          — interface (trackEvent, observeEvents)
├── ActivityEvent.kt            — data class (eventType, contentKey, episodeKey, timestamp, duration, etc.)
├── ActivityRepository.kt       — writes/reads from activity_event table (batched writes — I11 fix)
├── ActivityPruneWorker.kt      — [I11 fix] WorkManager daily job, prunes events older than retention
└── di/ActivityTrackerModule.kt — Koin module
```

**What it tracks:**
- 📺 When the user watches an episode (start, pause, resume, complete).
- ⏰ Time of day/night (to find peak watching hours).
- 📊 How many episodes per day/week/month.
- ⭐ Ratings given.
- 🔍 Searches performed.
- 📥 Downloads started/completed.
- 📖 Library adds/removes.

**Retention**: 365 days default, unlimited option (user preference). Prune worker runs daily.

**Write batching** (I11 fix): Player events are batched in memory, flushed every 30s or on pause/stop (not every 10s) to reduce DB load + flash wear.

**Stats calculation**: DEFERRED to Phase 6 (I10 fix — Ponytail: no premature abstraction). The `StatsCalculator` will be built when the `:feature:anime-my` stats screen is built. Phase 3 only stores raw events.

**Backup**: This data IS included in backups (it's the user's own data — they want to keep it).

#### 3. `:core:watch-progress` (NEW — contract module, C3 fix)
**Purpose**: WatchProgressStore interface. Resolves the layering issue (architecture plan C3). `:core:player` depends on this interface (writes); impl lives in `:data:history` (Phase 4) or a simple impl in `:core:activity-tracker` for now.

**Files:**
```
core/watch-progress/src/main/java/com/confused/anikuta/core/watchprogress/
├── WatchProgressStore.kt     — interface (save, get, observe)
├── WatchProgress.kt          — data class (episodeKey, position, duration, completed, lastWatchedAt)
└── di/WatchProgressModule.kt — Koin module (interface only)
```

**Dependencies**: `:core:common` only.
**Note**: The impl writes to the `watch_progress` table (C2 fix — separate from activity_event). Activity tracker ALSO logs the event (for stats). Two tables, two purposes: progress = current state (small, indexed), activity = historical log (large, append-only).

#### 4. `:core:preferences` (ENHANCED — already exists)
**Changes**: Add `AppPreferences` (content mode, tracking retention, animation preferences) + `PlayerPreferences` (speed, subtitle style, etc. — copied from old project). [I5 fix: PlayerPreferences lives HERE, not in :core:player]

---

## Sub-Phase 3b: Extensions (MOST CRUCIAL)

> The user said: "The most crucial part is the extension system." Build it properly, modularly, well-documented.

### Modules

#### 4. `:core:provider-api`
**Purpose**: The `ExtensionProvider` abstraction. Split into per-content-type sub-interfaces (architecture plan C1 fix).

**Files:**
```
core/provider-api/src/main/java/com/confused/anikuta/core/providerapi/
├── ExtensionProvider.kt              — sealed interface (ecosystemId, displayName, supportedContentTypes)
├── VideoExtensionProvider.kt         — sub-interface (fetchEpisodeList, fetchVideoList)
├── ImageExtensionProvider.kt         — sub-interface (fetchChapterList, fetchPageList) — future
├── TextExtensionProvider.kt          — sub-interface (fetchChapterList, fetchTextContent) — future
├── Source.kt                         — data class (ecosystem, sourceId, name, lang)
├── SourceContent.kt                  — data class
├── SourceEpisode.kt                  — data class
├── SourceVideo.kt                    — data class
└── di/ProviderApiModule.kt           — Koin module (empty — just contracts)
```

#### 6. `:core:source-api`
**Purpose**: Animiru-compatible source API. Ships the `eu.kanade.tachiyomi.animesource.*` package for binary compat with Animiru extensions.

> **Naming note** (D-043): User said to use "Animiru" not "Aniyomi" for naming. The package `eu.kanade.tachiyomi.*` stays (it's the binary-compat contract — per CORE_RULES §17), but module/class names use "Animiru".

> **Scope warning** (I7 fix): The real Animiru `eu.kanade.tachiyomi.animesource.*` package has ~50+ classes (AnimeSource, AnimeCatalogueSource, AnimeInterceptor, ConfigurableAnimeSource, HttpSource, AnimeHttpSource, UnmeteredSource, SourceFactory, Page, Chapter, etc.). Animiru extensions compile against the FULL surface — they will throw `ClassNotFoundException` at load time if any required class is missing. **Copy the entire package from Animiru's source verbatim** — it's the binary-compat contract.

**Files:**
```
core/source-api/src/main/java/eu/kanade/tachiyomi/animesource/
├── AnimeSource.kt            — the source interface (Animiru-compat)
├── AnimeCatalogueSource.kt   — browseable source
├── AnimeInterceptor.kt       — request interceptor
├── ConfigurableAnimeSource.kt — configurable source (preferences)
├── HttpSource.kt             — HTTP-based source
├── AnimeHttpSource.kt        — anime HTTP source
├── AnimeInfo.kt              — anime metadata from a source
├── AnimeEpisode.kt           — episode from a source
├── Video.kt                  — playable video URL
├── SourceManager.kt          — interface for source registry
├── UnmeteredSource.kt        — unmetered source marker
├── SourceFactory.kt          — source factory
└── ... (~50 classes total — copy verbatim from Animiru)
injekt/SourceApiInjekt.kt     — Injekt bootstrap (registers Application, Context, NetworkHelper, Json)
```

**Injekt isolation** (architecture plan §3 rule 6 + I1):
- `uy.kohesive.injekt` imports allowed ONLY in `:core:source-api` and `:data:extension-animiru`.
- In `:app`, restrict to `AnimiruInjektBootstrap.kt` (Detekt filename allowlist).
- Registers 4 singletons before extensions load: `Application`, `Context`, `NetworkHelper`, `Json`.

#### 6. `:data:extension-animiru` (renamed from extension-aniyomi)
**Purpose**: Loads, installs, and manages Animiru APK extensions. Implements `VideoExtensionProvider`.

**Files:**
```
data/extension-animiru/src/main/java/com/confused/anikuta/data/extension/
├── AnimiruExtensionManager.kt   — manages installed extensions (3 StateFlows: installed, updating, available)
├── ExtensionLoader.kt           — DEX classloader (ChildFirstPathClassLoader)
├── ExtensionInstaller.kt        — PackageInstaller foreground service
├── ExtensionRepoApi.kt          — fetches extension lists from user-added repos (NO defaults)
├── ExtensionTrust.kt            — SHA-256 trust set
├── SourceMatcher.kt             — title similarity matching
├── AnimiruExtensionProvider.kt  — implements VideoExtensionProvider
└── di/ExtensionModule.kt        — Koin module
```

**Dependencies**: `:core:provider-api` + `:core:source-api` + `:core:network` + `:core:database` + `:core:common`.

**Key decisions:**
- ❌ **NO default extension repo URLs.** User adds their own repos.
- ✅ Reuse the old project's extension loading logic (proven).
- ✅ Documented thoroughly (the user emphasized this).

**Deliverable**: Can install Animiru extensions from user-added repos, list their sources, fetch anime from a source.

---

## Sub-Phase 3c: Playback

> The user said: "Copy-paste the exact same player from the old project because that player is good."
> **I6 fix**: This is a PORT, not a copy. The old player is XML-View-based → needs `AndroidView` wrapper for Compose. Dependencies (PreferenceStore, NetworkHelper, Logger) must be remapped to ANI-KUTA's modules. MPV lib AAR needs ABI verification (arm64-v8a + armeabi-v7a only).

### Modules

#### 9. `:core:player` (PORTED from old project)
**Purpose**: MPV wrapper + player controls + watch progress. Port the old project's player — it's proven and the user likes it.

**Files:**
```
core/player/src/main/java/com/confused/anikuta/core/player/
├── AnikutaMPVView.kt          — MPV wrapper (XML-inflated — needs AndroidView wrapper for Compose)
├── PlayerController.kt        — play/pause/seek/speed/subtitle/audio track
├── PlaybackStateStore.kt      — saves last playback state
├── controls/
│   ├── ThemedGlass.kt         — themed dark glass (ported)
│   ├── MinimalSeekbar.kt      — seekbar (ported)
│   ├── PlayerControls.kt      — fullscreen controls overlay (ported)
│   └── EpisodeSwitchingOverlay.kt — next/prev episode overlay (ported)
├── subtitles/
│   └── SubtitleTrackFormatter.kt — subtitle styling (ported)
└── di/PlayerModule.kt         — Koin module
```

**Port tasks** (I6 fix):
1. Wrap `AnikutaMPVView` in `AndroidView` for Compose (fullscreen + overlay swap).
2. Remap dependencies: old `PreferenceStore` → `:core:preferences`, old `NetworkHelper` → `:core:network`, old `Logger` → `:core:common.Logger`.
3. Audit copied `PlayerPreferences` for ANI-KUTA-incompatible keys (M13).
4. Verify MPV AAR ABIs (arm64-v8a + armeabi-v7a only — CI check for forbidden lib/x86*).

**MPV native library**: Reuse `aniyomi-mpv-lib` AAR (from old project) as a **separate module** (`:core:player-mpv-lib`) so we can swap players easily in the future.

**Watch progress**: Writes to `:core:watch-progress` interface (C3 fix — NOT directly to activity tracker). The impl writes to `watch_progress` table. Activity tracker ALSO logs the event (for stats).

**CORE_RULES §22 compliance** (animations):
- Player controls overlay: fade-in 200ms, ripple on tap, scale-down on press.
- Seekbar: smooth drag, no jank.
- Episode switching: shared element transition where possible.

**CORE_RULES §23 compliance** (live verification):
- Seek position: optimistic update (UI updates instantly), flush to DB on pause/stop.
- High-frequency writes (≥1/sec) exempt from read-back (rely on transactional write).

#### 8. `:core:video-resolver`
**Purpose**: Calls the extension source's `fetchVideoList` → extracts playable video URLs.

**Files:**
```
core/video-resolver/src/main/java/com/confused/anikuta/core/videoresolver/
├── VideoResolver.kt          — interface (resolve(episode) → Flow<ResolverState>)
├── ResolverState.kt          — sealed (Loading, Success(videos), Error)
├── VideoResolverImpl.kt      — calls ExtensionProvider.fetchVideoList
├── HlsHelper.kt              — PNG anti-scraping header stripping (copied from old project)
└── di/VideoResolverModule.kt — Koin module
```

#### 9. `:core:download` (COPIED from old project)
**Purpose**: Download manager for offline playback. Copy from old project.

**Files:**
```
core/download/src/main/java/com/confused/anikuta/core/download/
├── DownloadManager.kt         — manages download queue (copied)
├── DownloadTask.kt            — single download task (copied)
├── HlsDownloader.kt           — HLS segment downloader (copied)
├── HttpDownloader.kt          — HTTP byte-range downloader (copied)
├── DownloadState.kt           — sealed state
└── di/DownloadModule.kt       — Koin module
```

#### 10. `:core:player-mpv-lib` (NEW — separate module for the native lib)
**Purpose**: Wraps the `aniyomi-mpv-lib` AAR as a separate Gradle module. This way we can swap players easily.

**Structure:**
```
core/player-mpv-lib/
├── build.gradle.kts           — declares the AAR dependency
└── libs/aniyomi-mpv-lib.aar   — the native library (from old project)
```

---

## Sub-Phase 3d: Supporting

### Modules

#### 11. `:core:metadata` (NEW — I9 fix: ONE module, not split by content type)
**Purpose**: Metadata fetching + user customization. Uses a `MetadataProvider` interface with multiple impls (anime now, movies/manga later). [I9 fix: collapsed from `:core:metadata-anime` + `:core:metadata-movies` + `:core:metadata-manga` into one module with providers — avoids duplicating MetadataMerger + LocalMetadataProvider + cache logic]

**Files:**
```
core/metadata/src/main/java/com/confused/anikuta/core/metadata/
├── MetadataProvider.kt          — interface (fetchContentMetadata, fetchEpisodeMetadata, supportedContentTypes)
├── ContentMetadata.kt           — data class (title, description, genres, status, year, coverUrl, ...)
├── EpisodeMetadata.kt           — data class (title, thumbnailUrl, airDate, description, ...)
├── MetadataMerger.kt            — merges multiple sources (local override → AniList → extension)
├── MetadataRegistry.kt          — queries List<MetadataProvider> by content type
├── providers/
│   ├── AniListMetadataProvider.kt  — fetches from AniList GraphQL (reuses :core:anilist — I8 fix)
│   └── LocalMetadataProvider.kt    — fetches from user_customization table (user overrides)
└── di/MetadataModule.kt          — Koin module (multi-binding: List<MetadataProvider>)
```

**Dependencies**: `:core:anilist` (I8 fix — thin adapter, not re-implementation) + `:core:database` + `:core:common`.

**Future providers** (not in Phase 3):
- `TmdbMetadataProvider` — movies and series (when movies content type is added).
- `JikanMetadataProvider` — manga (when manga reader is built).

**User customization** (KEY feature the user wants):
- Users can change the thumbnail image of an anime/episode.
- Users can change the title.
- Users can change the description.
- These overrides are stored in `user_customization` table.
- `LocalMetadataProvider` serves these overrides (highest priority).
- `MetadataMerger` merges: local override → AniList → extension source.

#### 12. `:core:tracker-anilist` (NEW — AniList only for now)
**Purpose**: AniList tracker sync. MAL and others come later. Internal tracking (`:core:activity-tracker`) is the priority — this is secondary.

**Files:**
```
core/tracker-anilist/src/main/java/com/confused/anikuta/core/trackeranilist/
├── AniListTracker.kt          — implements Tracker interface (login, sync, observe)
├── AniListOAuth.kt            — OAuth2 flow
├── TrackSyncManager.kt        — orchestrates sync (internal tracking → AniList)
└── di/TrackerAniListModule.kt — Koin module
```

**Priority**: Internal tracking (`:core:activity-tracker`) is built FIRST (3a). AniList sync (this module) is built LAST (3d). The user wants to understand internal tracking before adding external sync.

#### 13. `:core:tracker-api` (NEW — tracker contracts)
**Purpose**: The `Tracker` interface. Separate from the AniList impl so we can add MAL/Shikimori later.

**Files:**
```
core/tracker-api/src/main/java/com/confused/anikuta/core/trackerapi/
├── Tracker.kt                 — interface (login, sync, observe)
├── TrackerType.kt             — enum (ANILIST, MAL, SHIKIMORI — future)
└── di/TrackerApiModule.kt     — Koin module
```

---

## Phase 3 Build Order (Refined)

| Step | Module | Sub-phase | Why this order |
|------|--------|-----------|----------------|
| 1 | `:core:database` (enhanced) | 3a | Add basic tables (no identity system). |
| 2 | `:core:watch-progress` | 3a | Contract interface (needed before player + activity tracker). |
| 3 | `:core:activity-tracker` | 3a | Internal tracking — the user's KEY requirement. |
| 4 | `:core:preferences` (enhanced) | 3a | Player prefs + app prefs. |
| 5 | `:core:provider-api` | 3b | Extension contracts (needed before extension-animiru). |
| 6 | `:core:source-api` | 3b | Animiru-compat source API (Injekt isolated). [I7: ~50 classes from Animiru] |
| 7 | `:data:extension-animiru` | 3b | Extension loader (MOST CRUCIAL). |
| 8 | `:core:player-mpv-lib` | 3c | Native lib wrapper (separate module). |
| 9 | `:core:player` | 3c | MPV player (ported from old project — I6). |
| 10 | `:core:video-resolver` | 3c | Video URL resolver. |
| 11 | `:core:download` | 3c | Download manager (ported). |
| 12 | `:core:metadata` | 3d | Metadata + user customization (I9: one module). |
| 13 | `:core:tracker-api` | 3d | Tracker contracts. |
| 14 | `:core:tracker-anilist` | 3d | AniList sync (after internal tracking is proven). |

---

## What's Deferred (Phase 4+)

| Deferred item | Why | When |
|---------------|-----|------|
| `:core:identity` + `:data:identity` | Complex — needs proper understanding first. | Phase 4 (after core features work). |
| `:data:anime` (library/history repos) | Needs identity system. | Phase 4. |
| `:core:backup` | Needs identity system + all data tables. | Phase 5. |
| `:core:metadata-movies` | Future content type. | Phase 7+. |
| `:core:metadata-manga` | Future content type. | Phase 7 (manga reader). |
| MAL/Shikimori trackers | AniList first. | Phase 5+. |

---

## Open Questions for User

1. **DB deferral contradiction**: I recommend deferring the IDENTITY SYSTEM but keeping basic tables (extensions, metadata cache, activity tracking, user customizations). Is this what you want? (See "Honest Flag" at the top.)

2. **Watch progress storage**: I propose storing watch progress as `activity_event` rows (the internal tracker handles it). No separate `watch_progress` table for now. OK?

3. **Content identification (temporary)**: Until the identity system is built, how do we identify content? I propose using `(ecosystem, source_id, external_id)` as a composite key, OR AniList ID when available. The identity system (Phase 4) will introduce ContentUID and migrate. OK?

4. **Player copy depth**: When copying the player from the old project, do you want an EXACT copy (same code, same behavior) or should I clean it up / modernize it while copying?

5. **Extension repo UX**: Since there are no default repos, the first-launch experience needs an "Add Extension Repo" screen. Should this be part of the Setup Wizard (Phase 4) or a prompt when the user first opens Browse?

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Extension system complexity | High | High | Build it first (3b), test thoroughly, document well. |
| MPV native lib build issues | Medium | High | Reuse old project's AAR. Separate module for easy swap. |
| Temporary content ID breaks on identity migration | Medium | Medium | Use a stable composite key. Plan the migration path now. |
| Internal tracking schema too simple for future needs | Medium | Medium | Use a flexible `activity_event` table with JSON `payload` column. |
| User customization UX complexity | Medium | Low | Start with basic override (title, thumbnail, description). Expand later. |
