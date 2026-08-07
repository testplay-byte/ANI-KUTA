# 13 — Implementation Plan: Re-implementing the Download System in the New Project

> **Task ID:** DL-PLAN-REWRITE (this is the MOST IMPORTANT deliverable — the user will use it to actually build the new system).
> New project root: `/home/z/my-project/ANI-KUTA/ANI-KUTA/APP/ani-kuta/`
> Old project root (reference ONLY — do NOT copy its storage path or DI wiring): `/home/z/my-project/ANI-KUTA/ANI-KUTA/REFERENCES/old-kuta/ANIKUTA/`
> **This doc is the post-rewrite version.** It supersedes the prior old-project-mirroring plan with a NEW design that incorporates: (1) the new SAF + `data.json` storage system (`04-storage-paths.md`), (2) the new 3-dimensional priority auto-download engine (`14-auto-download-engine.md` §6), (3) the proxy-churn bug fix (`15-ui-and-bug-analysis.md` Part B), (4) the foreground service + new notification design (`06-notifications-foreground-service.md`), (5) the QoL features (`16-quality-of-life.md`).

## 1. Current state of the new project

### What already exists

| Module / File | Status |
|---|---|
| `core/download/` Gradle module | ✅ exists, depends on `core:common`, `core:database`, `core:preferences`, `core:network`, `okhttp`, `kotlinx.coroutines`, `logcat`, `koin` |
| `core/download/.../DownloadManager.kt` | ⚠️ STUB — basic single-threaded HTTP, writes to `context.filesDir/downloads/`, uses SQLDelight directly, no pause/resume/HLS/SAF |
| `core/download/.../DownloadState.kt` | ⚠️ STUB — sealed interface with `Queued / Downloading(progress) / Paused / Completed / Failed(msg)` |
| `core/download/.../DownloadModule.kt` | ⚠️ STUB — single Koin binding for the stub `DownloadManager` |
| `core/database/.../downloadQueue.sq` | ✅ exists — `download_queue` table (id, episode_key, state, progress, error_message, queued_at, started_at, completed_at) — **needs schema update** (see D.0) |
| `core/database/.../downloadedEpisode.sq` | ✅ exists — `downloaded_episode` table (episode_key, file_path, file_size, quality, downloaded_at) — **needs schema update** (see D.0) |
| `core/preferences/PreferenceStore.kt` | ⚠️ simple SharedPreferences wrapper — **NOT reactive** (no `changes(): Flow<T>` like the old project) |
| `core/content/ContentModels.kt` | ✅ exists — `ContentRecord(mainId, contentId, title, contentType, contentFormat, ...)` — **the mainId/contentId system we key off** |
| `core/video-resolver/` | ✅ exists — `VideoResolver`, `ResolverState`, `ResolverTypes`, `ResolvedVideosRegistry`, `VideoResolverModule` |
| `feature/watch/` | ✅ exists — `WatchScreen`, `WatchKey`, player infrastructure with MPV |
| `feature/anime-details/` | ✅ exists — `DetailsScreen`, `DetailsViewModel`, `ResolverSheet`, `ManualSearchSheet`, etc. (but NO `EpisodeDownloadControl` / `EpisodeDownloadState` yet) |
| `app/src/main/AndroidManifest.xml` | ✅ has `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `VIBRATE` permissions declared. **REVIEW-5 M23/M63:** `ACCESS_NETWORK_STATE` is NOT in the app manifest (the OLD draft incorrectly marked it "(implicit)") — it MUST be declared in `:core:download`'s new manifest (Phase D.4) for `ConnectivityManager.registerNetworkCallback` to work. |
| `app/src/main/java/.../AnikutaApp.kt` | ✅ `startKoin { modules(..., downloadModule, ...) }` — currently only the stub `downloadModule` is registered |
| minSdk | 24 (Android 7.0) — scoped storage rules apply for API 29+; SAF works on all APIs |
| compileSdk / targetSdk | 36 |

### What's missing

| Component | Status |
|---|---|
| `:feature:download` Gradle module | ❌ doesn't exist |
| `DownloadManager` interface + `DefaultDownloadManager` impl | ❌ (stub is a concrete class) |
| `DownloadQueue` (concurrency, state machine, SQLDelight-backed) | ❌ |
| `DownloadStorageProvider` (NEW SAF + `data.json` system) | ❌ |
| `ContentDataJson` model + parser + writer | ❌ |
| `DownloadPreferences` (all 17 settings — see `07-settings-preferences.md` §2) | ❌ |
| `DownloadNotificationManager` + `DownloadService` (foreground) | ❌ |
| `HttpDownloader` / `HlsDownloader` / `AdvancedHttpDownloader` / `VideoTypeDetector` | ❌ |
| `DynamicProgressTracker` (smooth progress — byte-count-based, not segment-count) | ❌ |
| `TempDownloadCache` (per-`downloadId` temp dir, structured) | ❌ |
| `ServerDiscoveryStore` | ❌ |
| `DownloadOrchestrator` (resolver ↔ engine bridge) | ❌ |
| `AutoDownloadEngine` (the 5-step priority pipeline from `14-auto-download-engine.md` §6.2) | ❌ |
| `ProxyLeaseCoordinator` + `directUrl` resolver hook (the proxy-churn fix) | ❌ |
| `DownloadViewModel` + `DownloadUiState` | ❌ |
| `DownloadsScreen` + `DownloadedFilesScreen` + `DownloadSettingsScreen` + `DownloadVideoPickerSheet` | ❌ |
| `EpisodeDownloadControl` + `EpisodeDownloadState` (in `:feature:anime-details`) | ❌ |
| Components: `DragReorderableList`, `DownloadsEmptyState`, etc. | ❌ |
| Reactive `PreferenceStore` (Flow support) | ❌ (current is non-reactive) |
| `DownloadScanner` (the scan-on-startup engine from `04-storage-paths.md` §7) | ❌ |

## 2. Architecture differences: old vs new

| Aspect | Old project | New project |
|---|---|---|
| Storage path | `<root>/ANIKUTA/downloads/anime/<Title [al-123]>/Episode 001/...` | `<root>/{video,images,text}/<Title>/{data.json, <Title> - E00001.mp4}` (see `04-storage-paths.md`) |
| Reinstall recognition | ❌ (DB only — wiped on uninstall) | ✅ (`data.json` per content folder is the source of truth — scan-on-startup) |
| Persistence | JSON in SharedPreferences (`DownloadStore`) | SQLDelight tables (`download_queue` + `downloaded_episode`) — already exist; **needs schema update** to key by `mainId` + `episodeKey` |
| Preferences | `PreferenceStore` with reactive `Preference<T>.changes(): Flow<T>` | Simple SharedPreferences wrapper, **no Flow** — needs extending in D.0 |
| Auto-download engine | 4-step imperative with hardcoded priority (audio > quality > server — inconsistent across layers) | 5-step pure-function pipeline (`flatten → rank → fallback-check → pick → global-fallback`) with user-configurable `dimensionPriority` (see `14-auto-download-engine.md` §6.2) |
| Settings UI | 528-line `DownloadSettingsScreen` | Replicate EXACTLY + add ONE new collapsible section "Priority order" with the `DragReorderableList` for the 3 dimensions |
| Foreground service | ❌ (runs in CoroutineScope) | ✅ `DownloadService` with `foregroundServiceType=dataSync` (per `06-notifications-foreground-service.md`) |
| Notification channels | Single `IMPORTANCE_LOW` channel | Two channels: "Downloads" (silent, ongoing) + "Download complete" (with sound) |
| Notification thumbnails | None (text only) | Cover image thumbnail per content (cached `cover.jpg`) |
| Proxy-churn bug | ❌ (the bug is present — download captures the localhost URL forever, no re-resolve path) | ✅ Fixed via `directUrl` preference + re-resolve-on-IOException + optional `ProxyLeaseCoordinator` (see `10-player-integration.md` §14) |
| Package name | `app.confused.anikuta.*` | `com.confused.anikuta.*` |
| Module naming | `:core:download`, `:feature:download`, `:app` | Same convention |
| Navigation | Voyager | Nav3 (NavKey-based — verify) |
| Player | Custom MPV binding (`AnikutaMPVView`) | Same (`core/player/`) |
| Video resolver | `:feature:video-resolver` → moved to `:core:video-resolver` (Phase 8) | `:core:video-resolver` exists with `VideoResolver`, `ResolverTypes` |
| DI framework | Koin | Koin |
| DB framework | SQLDelight (`AnikutaDatabase`) | SQLDelight (`AnikutaDatabase`) |

## 3. Module mapping (old → new)

| Old module | New module | Notes |
|---|---|---|
| `:core:download` | `:core:download` (expand existing stub) | Add all engine files. **Do NOT** copy the old project's `DownloadStorageProvider` — use the NEW one from `04-storage-paths.md`. |
| `:feature:download` | `:feature:download` (CREATE) | New Gradle module |
| `:app` `DownloadOrchestrator` | `:app` `DownloadOrchestrator` (CREATE) | Bridges `:core:video-resolver` + `:core:download`. Uses the NEW 5-step `AutoDownloadEngine` pipeline. |
| `:app` `DownloadAppModule` (DI) | `:app` `DownloadAppModule` (CREATE) | Koin wiring. Adapt to the new project's Koin module style (see `12-di-wiring.md`). |
| `:app` `AppController` download methods | `:app` `AppController`-equivalent (CREATE/EXTEND) | The new project's nav controller (likely `Nav3`-based) |
| `:feature:anime-details` `EpisodeDownloadControl` + `EpisodeDownloadState` | `:feature:anime-details/impl` (ADD) | Per-episode UI — replicate EXACTLY from `15-ui-and-bug-analysis.md` Part A §A.9. |
| `:feature:anime-details` `EpisodesSection` (download wiring) | `:feature:anime-details/impl` (MODIFY) | Add `onDownloadEpisode` etc. to `EpisodeRow` |

## 4. Design decisions (the NEW ones, post-rewrite)

### D1 — Storage: NEW SAF + `data.json` system (per `04-storage-paths.md`)
**Decision:** Use the NEW storage system documented in `04-storage-paths.md`. NOT the old project's `<root>/ANIKUTA/downloads/anime/<Title [al-123]>/Episode 001/...` structure.

**Why:**
- Future-proof: `video/`, `images/`, `text/` format folders accommodate manga/novels/movies/series without restructuring.
- Reinstall-proof: `data.json` per content folder survives app-delete + reinstall + same-folder-selection (the scan-on-startup re-registers content by `mainId`).
- Human-readable: folder names are just titles, no ID suffixes. File names include the title + 5-digit padded episode number.
- 5-digit padding supports 10,000+ episodes (long-running shounen, daily soaps).
- The `mainId` (stable UUID) is the durable identity — survives source switches + AniList unlinking. Goes in `data.json`, NOT in the folder name.

### D2 — DB: SQLDelight (already exists), re-key by `mainId` + `episodeKey`
**Decision:** Use SQLDelight tables (already exist). Re-key both tables by `mainId` + `episodeKey` (5-digit padded episode number) instead of the current `episode_key` (which is just a string). The `data.json` files are the SOURCE OF TRUTH for reinstall recognition; the DB is a cache/index.

**Why:**
- The new project's content system keys by `mainId` (see `ContentModels.kt`). The download system should align.
- Queryable: "all COMPLETED for `mainId` X" is a SQL query, not a list filter.
- The DB is rebuilt from `data.json` files on scan — no migration needed if the user wipes DB.

### D3 — Foreground service: YES, with `foregroundServiceType="dataSync"`
**Decision:** Add `DownloadService` as a foreground service. The ongoing summary notification doubles as the foreground notification.

**Why:**
- Android 14+ kills background downloads aggressively.
- The `FOREGROUND_SERVICE_DATA_SYNC` permission is already declared in the new project's manifest.
- The notification is already needed (for progress + thumbnails) — make it the foreground notification.

### D4 — Reactive PreferenceStore (REQUIRED for the drag-reorder UI + live updates)
**Decision:** Extend `PreferenceStore` with a `Flow<T>` API. Two implementation options:
- (a) Wrap each getter/setter with a `MutableStateFlow` keyed by preference key (heavy memory).
- (b) Use `SharedPreferences.OnSharedPreferenceChangeListener` to broadcast changes to a single `MutableSharedFlow<String>` (key), then derive per-key Flows (lighter).

**Recommendation: option (b).** Add a `preferenceFlow<T>(key, default)` helper. This is a small addition to `:core:preferences` and benefits the whole app (not just downloads).

**Why required:** The drag-reorder UI (`DragReorderableList`) needs reactive prefs to update other parts of the UI when the user reorders. The old project's UI relies heavily on `Preference.changes(): Flow<T>` for live updates.

### D5 — Episode-key format: `"$mainId|$episodeNumber"` (5-digit padded)
**Decision:** Use `"$mainId|$episodeNumber"` (5-digit padded — e.g. `"550e8400...|00001"`) as the `episode_key`. Source-independent. Survives source switches.

### D6 — HLS support: YES
**Decision:** Port `HlsDownloader` (segment concatenation, no ffmpeg). Pure Kotlin, handles unencrypted HLS (the common case), includes PNG-header stripping for anti-scraping CDNs.

### D7 — Advanced (multi-threaded) method: YES, Phase D.1.5
**Decision:** Port `AdvancedHttpDownloader` (multi-threaded Range + per-chunk resume). Ship the Normal method first (Phase D.1), add Advanced in Phase D.1.5 (or skip if Normal + HLS covers 95% of cases).

### D8 — NEW: Auto-download priority engine (5-step pure-function pipeline)
**Decision:** Replace the old project's hardcoded 4-step `selectBestVideo` with a NEW 5-step pure-function pipeline:
1. `flatten(servers, prefs)` → `List<Candidate>` (pure)
2. `rank(candidates, dimensionPriority)` → sorted `List<Candidate>` (pure)
3. `applyFallbacks(ranked, dimensionPriority, prefs, fallbacks)` → `FallbackDecision` (pure)
4. `pick(ranked, decision)` → `Selected | ShowPicker | Error` (pure)
5. `globalFallback(emptyCandidates, globalFallbackPref)` → `Selection` (pure)

The user adds ONE new preference list (`dimensionPriority: List<PreferenceDimension> = [AUDIO, QUALITY, SERVER]`) + ONE new global fallback (`globalFallback: GlobalFallbackStrategy = BEST_EFFORT`). See `14-auto-download-engine.md` §6.2 for the full design + worked examples.

**Why this is critical:** The user explicitly said: "highly customizable so that in the future we can change this logic easily." The pure-function pipeline + the dimension-priority abstraction make adding a 4th dimension (e.g. "subtitles language") a one-line enum addition. The old engine would need a rewrite.

### D9 — NEW: Proxy-churn bug fix (`directUrl` + re-resolve-on-IOException)
**Decision:** Add `directUrl: String?` to the resolver's `ResolverVideo` type. The orchestrator prefers `directUrl` for downloads (stable CDN URL, no proxy dependency). If `directUrl` is null (extension only exposes the proxy), add a re-resolve-on-IOException path with a 1-attempt cap.

**Optional tertiary fix:** Add a `ProxyLeaseCoordinator` that suppresses a second `getHosterList` while a download is using the proxy.

See `10-player-integration.md` §14 for the full fix.

### D10 — NEW: Notification design (thumbnails + dual channels)
**Decision:** Two notification channels:
- "Downloads" (`IMPORTANCE_LOW`, no sound, ongoing) — for progress.
- "Download complete" (`IMPORTANCE_DEFAULT` with sound) — for completion.

Each completion notification carries the content's cover image as a thumbnail (cached `cover.jpg` from the content folder).

See `06-notifications-foreground-service.md` for the full design.

### D11 — NEW: QoL features (auto-retry, auto-resume, auto-pause, verification, orphan cleanup)
**Decision:** Add the quality-of-life features from `16-quality-of-life.md`:
- Auto error handling/retry (small features, huge impact).
- Auto-resume on network change.
- Auto-pause on metered network (configurable per-pref).
- Download verification via file size + magic-byte check.
- Orphan-file cleanup (temp cache + half-written SAF files).
- Auto-clear completed entries after 10s (per old project — keep behavior).

### D12 — NEW: Settings page UI replicates the old project EXACTLY
**Decision:** Replicate the 528-line `DownloadSettingsScreen.kt` layout EXACTLY (sections, components, colors, spacings, animations). Add ONE new collapsible section "Priority order" ABOVE the existing 3 preference-list sections, using the same `DragReorderableList` component (per `14-auto-download-engine.md` §6.5).

**Why:** The user explicitly said the settings page should look/feel the SAME as the old project. The only structural change is the added "Priority order" section.

### D13 — NEW: Downloads page UI replicates the old project EXACTLY
**Decision:** Replicate the `DownloadsScreen` + `DownloadedFilesScreen` + `EpisodeDownloadControl` EXACTLY per `15-ui-and-bug-analysis.md` Part A. Three "fix while replicating" notes (per `15-ui-and-bug-analysis.md` §A.11):
1. Group the live queue by `mainId` (not `anime.title`).
2. Guard the auto-clear `launch { delay(10_000); removeFromQueue(task.id) }` with a `Set<Long>` of already-scheduled task IDs.
3. (Optional polish) Use `AnimatedContent` in `EpisodeDownloadControl` (the KDoc promises it; the code doesn't deliver).

### D14 — Player integration: offline short-circuit + the proxy-churn fix
**Decision:** Before resolving a stream, check `downloadManager.isEpisodeDownloaded(mainId, episodeNumber)`. If true, build a `WatchRequest` with the local content:// URI + null headers + "Offline" server label. See `10-player-integration.md`.

### D15 — NO migration from the old project
**Decision:** The new project is a fresh start. No `DownloadMigration`. Users of the old project must re-download their library (the old folder structure is incompatible by design — see `04-storage-paths.md` §12).

## 5. Implementation phases

### Phase D.0 — Foundations (2-3 days)

**Goal:** extend the new project's infrastructure to support the download system.

Tasks:
1. **Extend `PreferenceStore`** (`:core:preferences`) with a reactive `Flow<T>` API. Add a `preferenceFlow<T>(key, default)` helper or expose `Preference<T>`-like objects with `changes(): Flow<T>`. Implement via `SharedPreferences.OnSharedPreferenceChangeListener` (option (b) in D4). Verify with a small unit test that a write in one place emits to a Flow collector in another.
2. **Update SQLDelight schema** (`downloadQueue.sq` + `downloadedEpisode.sq`) to key by `mainId` + `episodeKey` (5-digit padded). Add the new columns (`video_url`, `video_headers`, `subtitle_tracks`, `audio_tracks`, `source_id`, `video_server`, `video_quality`, `video_audio`, `content_id`, `episode_number`, `episode_name`, `content_title`, `cover_url`, `cover_color`, `downloaded_bytes`, `total_bytes`, `video_uri`, `subtitle_uris`, `resolve_context`, `prev_total_bytes`, `prev_estimate_bytes`, `recent_ratios_json`, `retry_attempt`, `retry_max_attempts`, `last_error`). **REVIEW-5 M1+M2 (R1-C3 + R1-C4):** Do NOT add a `3.sqm` migration file — the project has ZERO `.sqm` files (SQLDelight 2.x derives the v1 schema directly from the `.sq` files). Edit the `.sq` files directly; the new schema is the canonical v1. Existing dev installs must wipe app data once (`adb shell pm clear com.confused.anikuta` or uninstall+reinstall). `DatabaseDriverFactory.create()` does NOT need a `migrations = …` arg for this rewrite — but the NEXT schema change MUST pair with a real `1.sqm` + the `DatabaseDriverFactory` update (see `11-db-schema.md` §3.3 + §3.4). **REVIEW-5 M6 (R3-I2 / R4-C7):** the `resetDownloadingToQueued` SQL must be `WHERE state IN ('DOWNLOADING', 'RETRYING')` (also resets RETRYING). **REVIEW-5 M3 (R1-C5):** the `getDownloadedMainIds` query uses `MAX(...)` for bare columns (not `DISTINCT` + `GROUP BY`). **REVIEW-5 M7 (R1-I4):** add `updateDownloadContentId` for source-switch sync. **REVIEW-5 M8 (NEW-R5 §4.2):** the `state` column comment lists all 7 states (incl. RETRYING).
3. **Add the `"download"` qualified `OkHttpClient`** to `:core:network` (or `:app`'s `appModule`). Long timeouts (30s connect, 60s read/write). Separate connection pool from the extension `NetworkHelper` client (prevents a stuck download from starving extension HTTP calls).
4. **Add SAF DocumentFile dependency** to `:core:download`'s `build.gradle.kts`: `implementation("androidx.documentfile:documentfile:1.0.1")`.
5. **Add kotlinx-serialization-json** to `:core:download` (for `ContentDataJson`, `DownloadTrack` JSON columns, `ResumeMetadata`).
6. **Delete the stub `DownloadManager.kt` + `DownloadState.kt`** in `:core:download` (they'll be replaced). The stub `DownloadModule.kt` will be rewritten in Phase D.1. **REVIEW-5 M12 (R3-M1 + NEW-R5 §4.2):** the canonical state type is `enum class DownloadStatus` (UPPERCASE constants — matches the OLD project). The stub `DownloadState.kt` (sealed interface, PascalCase variants — `Failed`/`Queued`/…) is DELETED; the new `DownloadStatus.kt` (created in D.1) has 7 constants: `QUEUED`, `DOWNLOADING`, `RETRYING` (NEW — M9), `PAUSED`, `COMPLETED`, `ERROR`, `CANCELLED`. The retry metadata (`retryAttempt`/`retryMaxAttempts`/`lastError`) lives on `DownloadTask` (the enum constant can't carry per-instance data).
7. **Add `core/content` dependency** to `:core:download` (for the `mainId`/`contentId`/`ContentRecord` types).
8. **REVIEW-5 M49 (R3-C5 / R4-C5):** define `HttpException` LOCALLY in `:core:download` (`HttpException.kt` — `class HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause)`). Do NOT add a `:core:source-api` dependency — keep `:core:download`'s dep graph minimal. `HttpDownloader.downloadNormal` + `HlsDownloader.fetchText`/`downloadSegment` throw it for HTTP errors so `RetryPolicy.forException` can match on `e is HttpException`.
9. **REVIEW-5 M65 (NEW-R5 §4.5):** `DownloadScanner` constructor deps are `(Context, DownloadStorageProvider, DownloadStore, ContentRepository, AnilistDetailRepository)` — the last two come from `:core:content` (added in task #7 above). The scanner calls `contentRepository.upsertFromDataJson(...)` + `anilistDetailRepository.upsertFromDataJson(...)` (if `anilistId != null`) during scan.
10. **REVIEW-5 M23/M63 (R4-C4):** CREATE `:core:download/src/main/AndroidManifest.xml` (does NOT currently exist) declaring `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` + the `<service android:name="...DownloadService" android:foregroundServiceType="dataSync" />` element. Without `ACCESS_NETWORK_STATE`, `ConnectivityManager.registerNetworkCallback` SecurityException-crashes on first init.
11. **REVIEW-5 M26 (R4-I5):** create `:core:download/src/main/res/drawable/ic_pause.xml` + `ic_cancel.xml` vector drawables (referenced by the notification action buttons). Alternatively use framework drawables (`android.R.drawable.ic_media_pause`, `android.R.drawable.ic_menu_close_clear_cancel`).

### Phase D.1 — Engine + Storage (4-5 days)

**Goal:** port + adapt the `:core:download` engine. No UI yet — just the engine + DI + the NEW storage system.

Files to create in `:core:download/src/main/java/com/confused/anikuta/core/download/`:

| File | Source (old project) | Notes |
|---|---|---|
| `DownloadModels.kt` | `DownloadModels.kt` | Adapt to use `ContentRecord.mainId` + `ContentRecord.contentId` (not the old `DownloadAnimeInfo` with `contentId` only). |
| `DownloadContentInfo.kt` | (new, derived from `ContentRecord`) | Maps `ContentRecord` → a serializable download-context info. |
| `DownloadEpisodeInfo.kt` | (new) | `episodeKey`, `episodeNumber`, `episodeName`, `episodeUrl?`. |
| `DownloadRequest.kt` | `DownloadRequest.kt` | Add `resolveContext: ResolveContext?` field (for the proxy-churn re-resolve). |
| `DownloadStatus.kt` | `DownloadStatus.kt` | Same enum + `isTerminal` / `isActive`. |
| `DownloadTask.kt` | `DownloadTask.kt` | Re-keyed by `mainId` + `episodeKey`. |
| `DownloadManager.kt` (interface) | `DownloadManager.kt` | Rename old stub to interface. |
| `DownloadStore.kt` | (NEW — not from old project) | Thin wrapper around SQLDelight `downloadQueueQueries` + `downloadedEpisodeQueries`. Adapts the JSON shape to SQLDelight rows. |
| `DownloadPreferences.kt` | `DownloadPreferences.kt` | All 17 settings — the old 15 + `dimensionPriority` + `globalFallback` (see `07-settings-preferences.md` §2). |
| `DownloadLogger.kt` | `DownloadLogger.kt` | Use `com.confused.anikuta.core.common.Logger` (same API). |
| `DynamicProgressTracker.kt` | (NEW, see `05-downloaders.md`) | Smooth progress: byte-count-based + moving-average smoothing. No 90%→100% jumps. |
| `TempDownloadCache.kt` | (NEW, see `04-storage-paths.md` §6.2) | Per-`downloadId` temp dir. Includes `cover.jpg` + `data.json` temp files. `cleanupStale()` on creation. |
| `DownloadStorageProvider.kt` | (NEW, see `04-storage-paths.md`) | The NEW SAF system: format folders (`video/`/`images/`/`text/`), content folder by sanitized title, `data.json` per content, atomic publish, scan-on-startup. |
| `ContentDataJson.kt` | (NEW, see `04-storage-paths.md` §5.1) | The schema model + parser + writer. Schema-versioned + `ignoreUnknownKeys = true`. |
| `DownloadScanner.kt` | (NEW, see `04-storage-paths.md` §7) | The scan-on-startup engine. Walks `video/`/`images/`/`text/`/`audio/` (REVIEW-5 M57 — added `audio`), reads each `data.json`, UPSERTs to `content` + `downloaded_episode` tables by `mainId`. **REVIEW-5 M65:** constructor deps are `(Context, DownloadStorageProvider, DownloadStore, ContentRepository, AnilistDetailRepository)` — the last two come from `:core:content`. **REVIEW-5 M55:** uses `listFiles()` ONCE per content folder + builds a `Map<String, DocumentFile>` index (avoids the O(N) `findFile()` per episode). **REVIEW-5 (R1-I7):** detects duplicate `mainId` folders + keeps the newer one. |
| `VideoTypeDetector.kt` | `VideoTypeDetector.kt` | Same — URL/Content-Type inspection. |
| `HttpDownloader.kt` | `HttpDownloader.kt` | Adapt to NEW storage (`publishToUserFolder` writes to `<root>/video/<title>/...`). Add the re-resolve-on-IOException path for localhost URLs. **REVIEW-5 M15 (R2-C1 / R3-C1):** add `reResolveAttempts: Int = 0` parameter to `downloadNormal`; increment on each recursive call; cap at `MAX_RE_RESOLVE_ATTEMPTS = 1` (= 2 total download attempts); throw `DownloadException("Proxy URL died after $N re-resolve attempts")` when cap exceeded. **REVIEW-5 M49:** throw `HttpException(response.code, "HTTP $code for video URL")` for HTTP errors (not generic `DownloadException`). **REVIEW-5 M35 (R3-I4):** emit intermediate `onProgress` ticks during validation/subtitles/metadata/publish (96/97/98/99%) so the bar doesn't jump 95→100. **REVIEW-5 M37 (R3-I6):** the `finally` block distinguishes `CancellationException` (preserve resume metadata for Advanced) from completion/error (delete everything). **REVIEW-5 M16 (NEW-R5 §4.3):** the recursive call passes `resolveContext` + `reResolveAttempts + 1` (was missing — wouldn't compile + unbounded). |
| `HlsDownloader.kt` | `HlsDownloader.kt` | Same. Use byte-count-based progress (not segment-count-based) — see `05-downloaders.md`. **REVIEW-5 M32 (R3-C3):** refine `estimatedTotal` after each segment using the running average segment size. **REVIEW-5 M33 (R3-C4):** `downloadSegmentWithRetry` downloads each attempt to a `ByteArrayOutputStream` first + writes to `out` only on success (avoids partial-then-append corruption). **REVIEW-5 M39 (R3-I11):** `probeSegmentSize` uses a 1-byte Range GET (not HEAD — anti-scraping CDNs reject HEAD). |
| `DownloadQueue.kt` | `DownloadQueue.kt` | Adapt persistence to SQLDelight. Re-key by `mainId` + `episodeKey`. Mutex for thread-safety (the old project's "best-effort" threading is risky — see `02-queue-management.md` §11). **REVIEW-5 M11 (R3-I7 / R4-C6):** define `setRetryingStatus` + `setErrorStatus` as private methods. **REVIEW-5 M31 (R3-C2):** maintain `recentRatios: ArrayDeque<Float>(5)` per-task + pass to `DynamicProgressTracker.compute(...)`. **REVIEW-5 M34 (R3-I3):** replace per-tick `scope.launch { mutex.withLock { … } }` with INLINE `_tasks.value =` + Channel-based DB writes. **REVIEW-5 M38 (R3-I10):** persist `prevTotal`/`prevEstimate`/`recentRatios` across pause/resume (added columns). **REVIEW-5 M41 (R3-I15):** `mutateTask` is `suspend fun` (acquires mutex internally); `mutateTaskLocked` assumes mutex held. **REVIEW-5 M42 (R4-C8 + R4-M5):** `onNetworkChanged` uses `pauseInternal` (assumes mutex held) — no deadlock. **REVIEW-5 M43 (R4-I10):** `scheduleAutoClear`'s `autoClearScheduled.add` is wrapped in `mutex.withLock`. **REVIEW-5 M36 (R3-I5):** use `DynamicProgressTracker.complete()` to flip 99→100 on COMPLETED. |
| `DownloadNotificationManager.kt` | (NEW design, see `06-notifications-foreground-service.md`) | Two channels, thumbnail notifications, no sound during download, sound on completion. |
| `DefaultDownloadManager.kt` | `DefaultDownloadManager.kt` | Wires queue + HttpDownloader + HlsDownloader + AdvancedHttpDownloader + storage + notifier. Exposes `downloadScanner` for `AnikutaApp` to call on startup. |
| `ServerDiscoveryStore.kt` | `ServerDiscoveryStore.kt` | Same — passive per-source server recording. |
| `DownloadService.kt` | (NEW — no old equivalent) | Foreground service — see D3 + `06-notifications-foreground-service.md`. |
| `advanced/AdvancedHttpDownloader.kt` | same | (Phase D.1.5 — defer if wanted) |
| `advanced/DownloadResumeManager.kt` | same | (Phase D.1.5) |
| `di/DownloadModule.kt` | `di/DownloadModule.kt` | Koin bindings — see `12-di-wiring.md`. |

**Critical D.1 sub-tasks (the NEW design bits):**

1. **`ContentDataJson` + `DownloadScanner`** — these are NEW (not in the old project). They're the heart of the reinstall recognition system. Write thorough unit tests for `ContentDataJson` parsing + the scanner's reconciliation logic.

2. **`DownloadStorageProvider.publishToUserFolder`** — must do the read-modify-write of `data.json` ATOMICALLY (temp file → copy to SAF — see `04-storage-paths.md` §6.4). Test with a mock SAF provider that simulates mid-write crashes.

3. **`DownloadQueue` adapted to SQLDelight**:
   - Replace `store.purgeCancelled()` with `DELETE FROM download_queue WHERE state = 'CANCELLED'`.
   - Replace `store.setAll(tasks)` with per-row `updateDownloadState` / `insertDownloadQueue` / `deleteDownloadQueue` queries.
   - For progress ticks (throttled), use a single `UPDATE download_queue SET progress = ?, downloaded_bytes = ?, total_bytes = ?, updated_at = ? WHERE id = ?` query.
   - On startup, query `SELECT * FROM download_queue` to populate `_tasks`. Reset any `DOWNLOADING` tasks to `QUEUED` (fixes the old project's bug — see `03-state-machine.md` §7).

4. **Create `DownloadService`** (foreground service):
   ```kotlin
   class DownloadService : Service(), KoinComponent {  // REVIEW-5 M25 — KoinComponent required for `by inject<>()`
       private val manager by inject<DownloadManager>()
       private val notifier by inject<DownloadNotificationManager>()
       private val notificationManager by inject<NotificationManagerCompat>()  // REVIEW-5 M24
       private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  // REVIEW-5 M22 — heavy work on IO
       private var isForeground = false

       override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
           // REVIEW-5 M20 — SYNCHRONOUS startForeground with a placeholder (per ExtensionInstallService.kt:69).
           // Satisfies the Android 12+ 5-second contract regardless of queue state.
           if (!isForeground) {
               startForegroundCompat(buildPlaceholderNotification())
               isForeground = true
           }
           when (intent?.action) {
               ACTION_PAUSE_ALL -> runBlocking { manager.pauseAll() }
               ACTION_CANCEL_ALL -> runBlocking { manager.cancelAll() }
           }
           return START_STICKY
       }
       // REVIEW-5 M27 — onTimeout for the 6-hour dataSync cap (API 35+).
       // REVIEW-5 M28 — onTaskRemoved re-launches the service for aggressive OEMs.
       // See 06-notifications-foreground-service.md §13.7 for the full impl.
       companion object {
           const val SUMMARY_ID = 9001
           fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
           fun stop(context: Context) { context.stopService(Intent(context, DownloadService::class.java)) }
       }
   }
   ```
   - The `DownloadManager` calls `DownloadService.start(context)` on enqueue (if not already running) + `DownloadService.stop(context)` when the queue empties.
   - **REVIEW-5 M20:** the `queueCollector` runs on `Dispatchers.IO` + wraps `startForeground`/`notify` in `withContext(Dispatchers.Main)` (notification APIs are main-thread-only).
   - **REVIEW-5 M21 + M22:** `downloadCover` uses Coil 3 (`context.imageLoader`, `coil3.request.ImageRequest`, `image.asDrawable(context).toBitmap()`) — NOT Coil 2. `loadThumbnail` is `suspend` (no `runBlocking`).

5. **Manifest entry** (in `:core:download`'s NEW manifest — REVIEW-5 M23 + M63 — CREATE the manifest; the OLD draft said "add to :core:download's manifest" but the manifest didn't exist):
   ```xml
   <!-- :core:download/src/main/AndroidManifest.xml (NEW) -->
   <manifest xmlns:android="http://schemas.android.com/apk/res/android">
       <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
       <application>
           <service
               android:name="com.confused.anikuta.core.download.DownloadService"
               android:exported="false"
               android:foregroundServiceType="dataSync" />
       </application>
   </manifest>
   ```
   - **REVIEW-5 M26:** also create `:core:download/src/main/res/drawable/ic_pause.xml` + `ic_cancel.xml` vector drawables.

6. **`AnikutaApp.onCreate` scan trigger** — after Koin starts, call `downloadManager.requestFolderRescan()` on `Dispatchers.IO`. This is the reinstall recognition flow.

**Testing for D.1:** manually trigger a download (via a temporary debug button) + verify:
- Folder structure matches the ASCII tree in `04-storage-paths.md` §3.1.
- `data.json` is created in the content folder with all fields populated.
- Temp cache is cleaned up after the task finishes.
- Restart the app → verify the scan re-discovers the content + the DB row is recreated.
- Delete the app → reinstall → re-select the same folder → verify the scan re-registers everything.

### Phase D.2 — Orchestrator + Auto-download engine + proxy-churn fix (3-4 days)

**Goal:** bridge the resolver + engine + implement the NEW priority engine + the proxy-churn fix.

Files to create in `:core:download`:

| File | Source | Notes |
|---|---|---|
| `AutoDownloadEngine.kt` | (NEW — see `14-auto-download-engine.md` §6.2) | The 5-step pure-function pipeline: `flatten → rank → applyFallbacks → pick → globalFallback`. Pure functions over data classes — trivially unit-testable. |
| `ProxyLeaseCoordinator.kt` | (NEW — see `15-ui-and-bug-analysis.md` §B.6 Fix 3) | (Optional tertiary fix.) Tracks active leases: `Map<ProxyKey, LeaseRefcount>`. Suppresses a second `getHosterList` while a download is using the proxy. |

Files to create in `:core:video-resolver`:

| File | Source | Notes |
|---|---|---|
| `ResolverVideo.kt` (MODIFY) | — | Add `directUrl: String?` field. The resolver strategy extracts the underlying CDN URL by calling a new `Video.directVideoUrl` extension hook. |

Files to create in `:app/src/main/java/com/confused/anikuta/`:

| File | Source (old project) | Notes |
|---|---|---|
| `download/DownloadOrchestrator.kt` | `download/DownloadOrchestrator.kt` | Adapt `VideoResolver` → new project's `VideoResolver`; adapt `ResolverResult` / `ResolverServer` / `ResolverVideo` types. Internal `selectBestVideo` impl replaced by `AutoDownloadEngine.selectBestVideo`. `buildRequest` uses `selection.video.directUrl ?: selection.video.url` for `DownloadRequest.videoUrl`. |
| `download/EnqueueResult.kt` | (same file in old project) | Sealed interface. |
| `download/PickerContext.kt` | (same file) | Data class. |
| `download/ResolveContext.kt` | (NEW — see `15-ui-and-bug-analysis.md` §B.6 Fix 2) | Captures `(sourceId, episodeUrl, serverName, audioLabel, quality, mainId, episodeKey)` for re-resolve-on-IOException. **REVIEW-5 M64 (NEW-R5):** includes `mainId` + `episodeKey` (the OLD draft listed only 5 fields) — used for DB lookups during re-resolve. |
| `download/ReResolver.kt` | (NEW) | The re-resolve helper. Caps attempts at 1 (one initial + one re-resolve). **REVIEW-5 M17 (R2-I3 + NEW-R5 §4.4):** does a DIRECT lookup by pinned `(server, audio, quality)` — does NOT re-run the `AutoDownloadEngine`. The `autoDownloadEngine: AutoDownloadEngine` constructor param was REMOVED (was dead DI). Used by `HttpDownloader.downloadNormal` on `IOException` for localhost URLs. |
| `di/DownloadAppModule.kt` | `di/DownloadAppModule.kt` | Koin module — see `12-di-wiring.md`. |
| `navigation/AppController.kt` (EXTEND) | `navigation/AppController.kt` | Add download methods (`downloadEpisode`, `cancelDownload`, etc.) — adapt to the new project's nav controller (Nav3). |

Files to create in `:feature:anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/`:

| File | Source | Notes |
|---|---|---|
| `EpisodeDownloadState.kt` | same | Sealed interface (UI-side). Replicate from `15-ui-and-bug-analysis.md` §A.9. |
| `EpisodeDownloadControl.kt` | same | The state-driven composable. Replicate EXACTLY (with the `AnimatedContent` polish). |

Modify in `:feature:anime-details/impl`:
- `EpisodesSection.kt` — add `onDownloadEpisode`, `downloadStates`, `onDownloadCancel/Resume/Retry/Delete` params to the section + row.
- `DetailsScreen.kt` — accept the new download callbacks from the host + pass them down.
- `DetailsViewModel.kt` — expose `downloadStates: StateFlow<Map<String, EpisodeDownloadState>>` (collected from `DownloadManager.episodeDownloadStates`).

**The proxy-churn fix (D9):**

1. **PRIMARY** — `directUrl` on `ResolverVideo`: when the resolver returns a `Video`, it carries BOTH `url` (proxy URL, for MPV streaming) and `directUrl: String?` (CDN URL, for downloads). The orchestrator's `buildRequest` uses `selection.video.directUrl ?: selection.video.url`. No proxy dependency for sources that expose a direct CDN URL.

2. **SECONDARY** — re-resolve-on-IOException: in `HttpDownloader.downloadNormal`, catch `IOException` specifically. If the request URL is a localhost URL AND the task has a `resolveContext`, BEFORE throwing `DownloadException`, attempt ONE re-resolve via `ReResolver.reResolve(resolveContext)`. If the re-resolve succeeds, retry the download with the fresh URL (resuming from current bytes if possible). Cap re-resolve attempts at 2.

3. **TERTIARY** — `ProxyLeaseCoordinator` (optional): tracks active leases per `(sourceId, serverName)`. Before calling `source.getHosterList`, checks if a lease already exists; reuses the existing resolved videos if so. The download engine + player both acquire/release leases.

4. **QUATERNARY** — foreground service (Phase D.4, but architecturally aligned with this fix): independent of the proxy-churn bug, but worth fixing in the same pass.

### Phase D.3 — Queue management + Dynamic progress tracking (2 days)

**Goal:** proper queue start-next logic, configurable concurrency, smooth progress bar.

**Queue management:**
- Persisted in SQLDelight `download_queue` (not in-memory).
- Concurrency: Semaphore-based, configurable via `pref_dl_concurrent` (1..5, default 1). On pref change, `DownloadQueue.refreshConcurrency()` is called via a Flow collector (fixes the old project's bug where the new limit only took effect after restart).
- Start-next logic: `tryStartNext()` called after every state change. Picks the first QUEUED task in FIFO order. Skips if `connectivityCheck()` fails.
- Wi-Fi-only check: on every `tryStartNext` AND every network change (via `NetworkCallback`). Auto-pauses on metered network (QoL feature).
- Mutex-based thread-safety (the old project's "best-effort" threading is risky — see `02-queue-management.md` §11).
- See `02-queue-management.md` for the full design.

**Dynamic progress tracking:**
- The old project's `DynamicProgressTracker` is segment-count-based for HLS + byte-count-based for direct. The user wants a SMOOTH progress bar with no 90%→100% jumps.
- The NEW design: byte-count-based for ALL engines (including HLS — track total bytes downloaded across all segments, even if the total size is unknown). Use a moving average (window of 5 ticks) to smooth out network jitter. Cap at 95% during download (not 90% — the user complained about 90→100 jumps, but a 95% cap is closer to "real" completion).
- For HLS without a known total: estimate using "10 MB ahead" strategy (the old project's approach, with `INITIAL_ESTIMATE_BYTES = 10 MB`).
- See `05-downloaders.md` §8 (the updated section) for the full algorithm.

### Phase D.4 — Foreground service + Notifications (2-3 days)

**Goal:** the foreground service + the new notification design.

**The foreground service:**
- `DownloadService` starts when the first download starts, calls `startForeground(SUMMARY_ID, notification)`, stops when the queue empties.
- The notification is updated via the queue's StateFlow collector (throttled to 800ms).
- Notification tap deep-links to the Downloads screen (via Nav3 deep-link support or an Intent extra).

**The notification design (per `06-notifications-foreground-service.md`):**
- **Two channels:**
  - `anikuta_downloads_progress` — `IMPORTANCE_LOW`, no sound, ongoing. For the summary + progress.
  - `anikuta_downloads_complete` — `IMPORTANCE_DEFAULT` with sound. For completion.
- **Summary notification** (ID 9001, ongoing): shows the primary task's title + progress bar + thumbnail. Updated every 800ms.
- **Completion notification** (ID `taskId + 10_000`): "Download complete" + content title + cover thumbnail + auto-cancel.
- **Error notification** (ID `taskId + 20_000`): "Download failed" + content title + error message + auto-cancel.
- **Thumbnails:** loaded from the cached `cover.jpg` in the content folder (or downloaded on-demand from `coverUrl` if not cached). Use Coil's `NotificationCompat.BigPictureStyle` for the thumbnail.
- **No sound during download** — the progress channel is `IMPORTANCE_LOW`. **Sound on completion** — the completion channel is `IMPORTANCE_DEFAULT`.
- **Action buttons on the summary notification:** Pause all / Cancel all (per `06-notifications-foreground-service.md`).

### Phase D.5 — Settings page UI (EXACT replication) (3 days)

**Goal:** replicate the old project's `DownloadSettingsScreen` EXACTLY + add the new "Priority order" collapsible section.

**Replicate EXACTLY** (per `14-auto-download-engine.md` §5 + `15-ui-and-bug-analysis.md` Part A):
- The 528-line `DownloadSettingsScreen.kt` layout — sections, components, colors, spacings, animations.
- The 8 private composables: `SectionContainer`, `CollapsibleSection`, `CollapsibleExtensionSection`, `SettingsRow`, `ToggleRow`, `SliderRow`, `FallbackToggle`, `SegmentedRowLocal`.
- The `DragReorderableList` component (193 lines) — replicate as-is (the new dimension-priority list uses the same component).
- The `DownloadVideoPickerSheet` (233 lines) — replicate as-is.
- The `DownloadsMoreEntries` (38 lines) — replicate as-is.

**ADD the new "Priority order" section** (per `14-auto-download-engine.md` §6.5):
- A new `CollapsibleSection` ABOVE the existing 3 preference-list sections.
- Title: "Priority order — what matters most?"
- Content: `DragReorderableList` of `["Audio", "Quality", "Server"]` + a `FallbackToggle` for the global fallback.
- The `DragReorderableList` takes `List<String>` so we map enum names to strings + back.

**The settings** (per `07-settings-preferences.md` §2):
- All 17 settings: the old 15 + `dimensionPriority` (new) + `globalFallback` (new).
- Stored in `PreferenceStore` with reactive Flows.

**Fix the old project's bugs while replicating:**
- The `concurrentDownloads` pref change must call `DownloadQueue.refreshConcurrency()` explicitly (the old project doesn't, so the new limit only takes effect after restart).
- The `advancedMaxRetries` default mismatch (code=25, UI=0..10) — set both to 10.

### Phase D.6 — Downloads page UI + Episode download controls + Player integration (4-5 days)

**Goal:** the `:feature:download` module + the per-episode download UI + the offline short-circuit.

**Create the Gradle module** `:feature:download`:
- `build.gradle.kts` — depends on `:core:download`, `:core:designsystem`, `:core:preferences`, `:core:video-resolver` (for `ResolverVideo`/`ResolverServer` types in the picker sheet), Compose, Koin.
- `src/main/AndroidManifest.xml` — empty (no permissions needed here).
- `settings.gradle.kts` — register the module.

Files to create in `:feature:download/src/main/java/com/confused/anikuta/feature/download/`:

| File | Source (old project) | Notes |
|---|---|---|
| `DownloadUiState.kt` | same | Data class + `DownloadedAnimeKey` (re-keyed by `mainId` — see `15-ui-and-bug-analysis.md` §A.4 + §A.11 fix #11). |
| `DownloadViewModel.kt` | same | Combine flows + auto-clear after 10s (with the `Set<Long>` guard per `15-ui-and-bug-analysis.md` §A.11 fix #12). |
| `DownloadsScreen.kt` | same (569 lines) | The main page — replicate EXACTLY per `15-ui-and-bug-analysis.md` §A.2. |
| `DownloadedFilesScreen.kt` | same (206 lines) | The downloaded library page — replicate EXACTLY per §A.3. |
| `DownloadSettingsScreen.kt` | same (528 lines + new "Priority order" section) | Settings page — replicate EXACTLY per Phase D.5. |
| `DownloadVideoPickerSheet.kt` | same (233 lines) | Picker bottom sheet — replicate as-is. |
| `DownloadsMoreEntries.kt` | same (38 lines) | More-screen entry — replicate as-is. |
| `ExtensionSourceInfo.kt` | same (16 lines) | DTO. |
| `components/DragReorderableList.kt` | same (193 lines) | Drag-and-drop reorder — replicate as-is. |
| `components/DownloadsEmptyState.kt` | same (96 lines) | **USE THIS** two-variant component (per `15-ui-and-bug-analysis.md` §A.11 fix #8) — it's better than the in-file single-variant. |
| `di/DownloadModule.kt` | same (19 lines) | `viewModelOf(::DownloadViewModel)`. |

Wire `DownloadsMoreEntries` into `MoreScreen.kt`:
```kotlin
item { DownloadsMoreEntries(onOpenDownloads = { navController.push(DownloadsKey) }) }
```

**Player integration (D.6):**  <!-- REVIEW-5 M62 (NEW-R5 §4.6): fixed D.14 → D.6 typo -->
- Modify `:app`'s nav controller (the equivalent of `AppController.resolveEpisode`):
  - Before resolving a stream, call `downloadManager.isEpisodeDownloaded(mainId, episodeNumber)`.
  - If true, build a `WatchRequest` with the local content:// URI + null headers + "Offline" server label + downloaded subtitle URIs.
  - Push the `WatchKey` with that `WatchRequest`.
  - If false, fall through to the streaming resolver.
- Verify the player (`AnikutaMPVView` in `:core:player`) handles content:// URIs (it should — same approach as the LocalProxyServer URLs). If not, add a `resolveUrlForMpv` helper that converts content:// → `fd://<fd>` via `ContentResolver.openFileDescriptor`.
- Add an "Offline" badge to the `WatchScreen` (not in old project — see `10-player-integration.md` §10).

### Phase D.7 — Quality-of-life features (2-3 days)

**Goal:** the QoL features from `16-quality-of-life.md`.

- Auto error handling/retry (with exponential backoff + max retries).
- Auto-resume on network change (via `NetworkCallback`).
- Auto-pause on metered network (configurable per-pref `pref_dl_wifi_only`).
- Download verification via file size + magic-byte check (already in `HttpDownloader` — verify + harden).
- Orphan-file cleanup (temp cache on startup + half-written SAF files via the scan reconciliation).
- Auto-clear completed entries after 10s (keep behavior from old project).
- See `16-quality-of-life.md` for the full list + design.

### Phase D.8 — Polish + testing (1-2 days)

- Fix the `DOWNLOADING`-on-restart bug (reset to `QUEUED` on startup — handled in D.1's `DownloadQueue` adaptation).
- Fix the `Episode NNN` folder-name floor bug (N/A — we use 5-digit padded `E00001.5` for fractional specials).
- Add `AnimatedContent` to `EpisodeDownloadControl` for smooth state transitions (already in D.6).
- Add notification action buttons (Pause all / Cancel all) to the summary notification (already in D.4).
- Add a deep-link from the notification tap to the Downloads screen (already in D.4).
- Test: enqueue a download → verify folder structure → kill app → restart → verify queue persists → play offline → delete → reinstall app → re-select folder → verify scan re-discovers everything.
- Test the proxy-churn bug scenario: enqueue a download → play another episode from the same source → verify the download completes via `directUrl` (or re-resolves).

## 6. Total estimate (post-rewrite, post-review-fixes)

| Phase | Days |
|---|---|
| D.0 Foundations | 2-3 |
| D.1 Engine + Storage (NEW data.json system) | 4-5 |
| D.2 Orchestrator + Auto-download engine + proxy-churn fix | 3-4 |
| D.3 Queue management + Dynamic progress tracking | 2 |
| D.4 Foreground service + Notifications | 2-3 |
| D.5 Settings page UI (EXACT replication) | 3 |
| D.6 Downloads page UI + Episode controls + Player integration | 4-5 |
| D.7 Quality-of-life features | 2-3 |
| D.8 Polish + testing | 1-2 |
| **Subtotal (the original estimate)** | **23-30 days** |
| **REVIEW-5 consolidation pass (M1–M72 fixes)** | **+3-4 days** |
| **REVIEW-6 (re-review of the fixes)** | **+1-2 days** |
| **Inevitable mid-implementation discoveries** | **+3-4 days** |
| **Total (post-review-fixes)** | **30-40 days** |

This assumes one developer. Parallelizable: D.5 + D.6 can overlap (different files); D.7 can run parallel to D.5/D.6 (different module).

The total estimate grew from the prior 23-30 days to **30-40 days** because of the REVIEW-5 consolidation pass (72 MUST-FIX items, none of which were addressed in the original draft) + the re-review + the inevitable mid-implementation discoveries when implementing a plan that had 18 carry-over CRITICALs.

## 6.1 Review Findings section (REVIEW-5 M61 — NEW)

> The original draft of this plan did NOT list any of the 18 carry-over CRITICALs from
> Reviews 1–4 as action items. An implementer following the plan verbatim would have shipped
> a non-compiling build (Coil 2 on Coil 3, `HttpException` unresolved, `notificationManager`
> undefined, `KoinComponent` missing, `downloadVideoToCache` arity mismatch in §14), a
> `StackOverflowError` (unbounded re-resolve recursion), a `ForegroundServiceDidNotStartInTimeException`
> crash on Android 12+, corrupt HLS output on flaky CDNs, tasks stuck in RETRYING forever
> after a crash, the user's "progress bar jumps to 100%" complaint NOT actually fixed, +
> a `NoBeanDefFoundException` for `DownloadStorageProvider`.
>
> This section consolidates ALL 72 MUST-FIX items from REVIEW-5 §8 as explicit action items.
> Each item is tagged with the M-number from the consolidated list + the doc cross-reference
> where the fix is specified. Implementers MUST verify each fix landed before signing off the phase.

### A. Migration / DB schema (Phase D.0 — blocks everything)

| # | Action item | Doc cross-ref |
|---|---|---|
| M1 | Edit `.sq` files directly (do NOT add `3.sqm`). Dev installs wipe app data once. | `11-db-schema.md` §3.3 |
| M2 | `DatabaseDriverFactory` doesn't need migrations for this rewrite; future bumps pair with `1.sqm` + the factory update. | `11-db-schema.md` §3.4 |
| M3 | `getDownloadedMainIds` uses `MAX(...)` for bare columns; remove `DISTINCT`. | `11-db-schema.md` §3.2 |
| M4 | `data.json` example `contentId` is a real 6-section string (not `"anilist:101522"`). | `04-storage-paths.md` §5.2 |
| M5 | `ContentDataJson` stores the full FK set (`dataSourceId`/`systemId`/`extensionRepoId`/`extensionId`/`displaySource`) so the upsert is lossless. | `04-storage-paths.md` §5.1 |
| M6 | `resetDownloadingToQueued` SQL: `WHERE state IN ('DOWNLOADING', 'RETRYING')`. | `11-db-schema.md` §3.2 |
| M7 | Add `updateDownloadContentId` query for source-switch sync. | `11-db-schema.md` §3.2 |
| M8 | `state` column comment lists all 7 states (incl. RETRYING). | `11-db-schema.md` §3.2 |

### B. State machine + RETRYING propagation (Phase D.1/D.2/D.7)

| # | Action item | Doc cross-ref |
|---|---|---|
| M9 | Add `RETRYING` to `03-state-machine.md` §1 enum, §2 diagram, §3 transition table. | `03-state-machine.md` §1, §2.1, §3 |
| M10 | `pause`/`cancel`/`retry` accept RETRYING (cancel the retry loop's delay). | `02-queue-management.md` §13.4 |
| M11 | Define `setRetryingStatus` + `setErrorStatus` as private methods on `DownloadQueue`. | `02-queue-management.md` §13.3 |
| M12 | Pick `enum class DownloadStatus` (UPPERCASE constants) as the canonical type. Retry metadata on `DownloadTask`. | `03-state-machine.md` §1, §9 |
| M13 | Add `EpisodeDownloadState.Retrying(attempt, maxAttempts, lastError)` variant. | `09-details-page-download-ui.md` |
| M14 | Bulk "Retry all" skips RETRYING tasks (already being retried by the engine). | `08-downloads-page-ui.md` §3 |

### C. Proxy-churn fix (Phase D.2)

| # | Action item | Doc cross-ref |
|---|---|---|
| M15 | Add `reResolveAttempts: Int = 0` parameter to `downloadNormal`; cap at 1; throw on cap exceeded. | `05-downloaders.md` §11.3, `10-player-integration.md` §14.1 |
| M16 | §14.1 + §11.3 agree on the catch-block body (recurse on `downloadNormal`, pass `resolveContext`, enforce the cap). | `10-player-integration.md` §14.1, `05-downloaders.md` §11.3 |
| M17 | Remove `autoDownloadEngine: AutoDownloadEngine` from `ReResolver`'s constructor + from the Koin binding. Re-resolve does a DIRECT lookup. | `10-player-integration.md` §14.3, `12-di-wiring.md` §11.2 |
| M18 | Clarify: "Cap at 1 re-resolve attempt (2 total download attempts)." | `10-player-integration.md` §14.1 |
| M19 | Document the cap composition: outer (3) × inner (2) = 6 download attempts max. | `16-quality-of-life.md` §1.2 |

### D. Foreground service + notifications (Phase D.4)

| # | Action item | Doc cross-ref |
|---|---|---|
| M20 | Rewrite `DownloadService.onStartCommand` to use synchronous `startForeground` (copy the `ExtensionInstallService.kt` pattern). | `06-notifications-foreground-service.md` §13.7 |
| M21 | Rewrite `downloadCover` against Coil 3 (`context.imageLoader`, `coil3.request.ImageRequest`, `image.asDrawable(context).toBitmap()`). | `06-notifications-foreground-service.md` §13.2 |
| M22 | Move the thumbnail-load path to `Dispatchers.IO`; remove `runBlocking`; only `startForeground`/`notify` on `Dispatchers.Main`. | `06-notifications-foreground-service.md` §13.2, §13.7 |
| M23 | Add `ACCESS_NETWORK_STATE` permission to `:core:download`'s manifest (CREATE the manifest). | `06-notifications-foreground-service.md` §13.8 |
| M24 | Declare `notificationManager` field (use `NotificationManagerCompat.from(this)`). | `06-notifications-foreground-service.md` §13.7 |
| M25 | Add `KoinComponent` to `DownloadService`. | `06-notifications-foreground-service.md` §13.7 |
| M26 | Create `ic_pause.xml` + `ic_cancel.xml` vector drawables (OR use framework drawables). | `06-notifications-foreground-service.md` §13.8 |
| M27 | Document the 6-hour `dataSync` cap + specify an `onTimeout(startId, foregroundServiceType)` handler. | `06-notifications-foreground-service.md` §13.7 |
| M28 | Add `onTaskRemoved` override (re-launch service for aggressive OEMs). | `06-notifications-foreground-service.md` §13.7 |
| M29 | Use unique request codes for PendingIntents (1001/1002 instead of 1/2). | `06-notifications-foreground-service.md` §13.6 |
| M30 | Add `.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` to the summary notification. | `06-notifications-foreground-service.md` §13.2 |

### E. Queue management + progress tracking (Phase D.3)

| # | Action item | Doc cross-ref |
|---|---|---|
| M31 | Maintain `recentRatios: ArrayDeque<Float>(5)` per-task + pass to `DynamicProgressTracker.compute(...)`. | `02-queue-management.md` §13.3 |
| M32 | Refine `estimatedTotal` after each HLS segment using the running average segment size. | `05-downloaders.md` §11.4 |
| M33 | HLS per-segment retry: download to `ByteArrayOutputStream` first, write to `out` only on success. | `05-downloaders.md` §11.4 |
| M34 | Replace per-tick `scope.launch { mutex.withLock { … } }` with INLINE `_tasks.value =` + Channel-based DB writes. | `02-queue-management.md` §13.3 |
| M35 | Emit intermediate `onProgress` ticks during validation/publish (96/97/98/99%). | `05-downloaders.md` §11.3 |
| M36 | Use `DynamicProgressTracker.complete()` to flip 99→100 on COMPLETED. | `05-downloaders.md` §11.2, `02-queue-management.md` §13.3 |
| M37 | `HttpDownloader.download`'s `finally` distinguishes `CancellationException` (preserve resume metadata) from completion/error. | `02-queue-management.md` §13.3 |
| M38 | Persist `prevTotal`/`prevEstimate`/`recentRatios` across pause/resume. | `02-queue-management.md` §13.3 |
| M39 | `probeSegmentSize` uses a 1-byte Range GET (not HEAD). | `05-downloaders.md` §11.4 |
| M40 | Restore the OLD "sanity check" if-branch in `DynamicProgressTracker.compute` (was a no-op). | `05-downloaders.md` §11.2 |
| M41 | `mutateTask` is `suspend fun` (acquires mutex internally); `mutateTaskLocked` assumes mutex held. | `02-queue-management.md` §13.3 |
| M42 | Reconcile `onNetworkChanged` between 02 + 16; extract `pauseInternal` (assumes mutex held). | `02-queue-management.md` §13.4, `16-quality-of-life.md` §2.2 |
| M43 | `scheduleAutoClear`'s `autoClearScheduled.add` is wrapped in `mutex.withLock`. | `02-queue-management.md` §13.3 |

### F. Auto-download engine + settings (Phase D.2/D.5)

| # | Action item | Doc cross-ref |
|---|---|---|
| M44 | Redefine Step 5 (`globalFallback`) to fire based on the picked candidate's match quality, not on `sortedCandidates.isEmpty()`. | `14-auto-download-engine.md` §6.2.5 |
| M45 | Acknowledge that `[AUDIO, QUALITY, SERVER]` is a DELIBERATE change (not "preserves old behaviour"). | `14-auto-download-engine.md` (DEFAULT_DIMENSION_PRIORITY comment + §10 summary) |
| M46 | Add `key()`/`defaultValue()`/`isSet()`/`delete()` to `Preference<T>` interface. | `07-settings-preferences.md` §8.4 |
| M47 | Remove `onStart { emit(get()) }` from `Preference.changes()` (redundant with `collectAsState(initial = ...)`). | `07-settings-preferences.md` §8.4 |
| M48 | `RetryPolicy.forException` uses exception TYPE matching (not string matching on the message). | `16-quality-of-life.md` §1.2 |
| M49 | Define `HttpException` LOCALLY in `:core:download` (don't depend on `:core:source-api`). | `16-quality-of-life.md` §1.2.1 |
| M50 | Remove the dead `CancellationException` branch from `RetryPolicy.forException`. | `16-quality-of-life.md` §1.2 |
| M51 | (Resolved by M49.) `e is DownloadException && e.cause is IOException` now catches HTTP errors via the `HttpException` subclass. | `16-quality-of-life.md` §1.2 |
| M52 | Add a §7.5 to `01-workflow-click-to-queue.md` noting the NEW 5-step `AutoDownloadEngine`. | `01-workflow-click-to-queue.md` §7.5 |

### G. Storage (Phase D.1)

| # | Action item | Doc cross-ref |
|---|---|---|
| M53 | Spec `ensureContentDir`'s same-title collision algorithm (check `mainId`, append " (2)" if different). | `04-storage-paths.md` §4.1 |
| M54 | Create `.nomedia` in content folders (prevent gallery pollution). | `04-storage-paths.md` §6.3 step 6 |
| M55 | Scan uses `listFiles()` ONCE per content folder + `Map<String, DocumentFile>` index. | `04-storage-paths.md` §7.1 |
| M56 | Fractional episode format uses a non-rounding formatter (not `%.1f`). | `04-storage-paths.md` §4.2 |
| M57 | Add `"audio"` to the scan's format-folder list (or remove the `audio/` mention from §3.2). | `04-storage-paths.md` §7.1 |
| M58 | Fall back to "always scan" if `DocumentFile.lastModified()` returns 0 or a sentinel. | `04-storage-paths.md` §7.1 |
| M59 | `tryStartNext` checks `cacheDir.usableSpace` against `totalBytes` before starting. | `04-storage-paths.md` §6.2 |
| M60 | Note: stale `video_url` in `download_queue` after source switch — proactive re-resolve is a future enhancement. | `11-db-schema.md` §3.5 |

### H. Implementation plan coherence

| # | Action item | Doc cross-ref |
|---|---|---|
| M61 | (This section — the Review Findings — lists every M1-M60 as an explicit action item.) | `13-implementation-plan.md` §6.1 |
| M62 | Fix the "D.14" → "D.6" typo. | `13-implementation-plan.md` §5 D.6 |
| M63 | Remove the "(implicit)" parenthetical on `ACCESS_NETWORK_STATE`; add a Phase D.4 task to declare it. | `13-implementation-plan.md` §1, Phase D.0 task #10 |
| M64 | `ResolveContext` captures `(sourceId, episodeUrl, serverName, audioLabel, quality, mainId, episodeKey)` — 7 fields. | `13-implementation-plan.md` Phase D.2 file list |
| M65 | `DownloadScanner.kt` constructor deps include `ContentRepository` + `AnilistDetailRepository`. | `13-implementation-plan.md` Phase D.1 file list |

### I. Cross-doc consistency (resolved by the fixes above)

| # | Action item | Resolved by |
|---|---|---|
| M66 | State name `Failed` vs `ERROR` — pick `ERROR` (enum UPPERCASE). | M12 |
| M67 | `onNetworkChanged` divergent in 02 vs 16 — pick one (the 02 version, using `pauseInternal`). | M42 |
| M68 | §14.1 description contradicts §14.3 implementation — update §14.1 to say "DIRECT lookup, NOT AutoDownloadEngine". | M17 |
| M69 | §14.1 catch block calls `downloadVideoToCache` (5 args) vs §11.3 calls `downloadNormal` (6 args) — align on `downloadNormal` + pass `resolveContext`. | M16 |
| M70 | `dimensionPriority` default "preserves old behaviour" claim in 3 docs — acknowledge the deliberate change. | M45 |
| M71 | `Preference<T>` interface regression (3 methods vs 7) + redundant `onStart`. | M46 + M47 |
| M72 | `resetDownloadingToQueued` claim in QoL §1.3 is FALSE — make it TRUE by updating the SQL. | M6 |

## 7. Things to flag for the user / design decisions

1. **SAF folder picker is mandatory** — the user must pick a folder before any download works. The first-launch flow should prompt for this (either in a Setup Wizard or on first download tap).
2. **The `data.json`-based reinstall recognition** — the user can delete the app + reinstall + re-select the same folder, and all their downloads are re-recognized. This is a NEW feature not in the old project.
3. **The new auto-download priority engine** — the user can now rearrange the THREE preference dimensions (audio, quality, server) as a unified priority list. The engine handles conflicts gracefully (see the worked example in `14-auto-download-engine.md` §6.3).
4. **The proxy-churn bug is architecturally fixed** — downloads prefer `directUrl` (no proxy dependency); re-resolve-on-IOException is the safety net.
5. **The foreground service** — adds complexity but is necessary for Android 14+. The notification becomes the foreground notification.
6. **The new notification design** — thumbnails + no sound during download + sound on completion + dual channels.
7. **The QoL features** — auto-retry, auto-resume, auto-pause, verification, orphan cleanup.
8. **Settings UI replicates the old project EXACTLY** + ONE new "Priority order" collapsible section.
9. **No migration from the old project** — the new storage structure is incompatible by design. Users of the old project must re-download.

## 8. Risk register (post-rewrite)

| Risk | Likelihood | Mitigation |
|---|---|---|
| SAF provider quirks on specific OEMs (Samsung, Xiaomi) | Medium | Test on multiple devices; fall back to app-specific storage if SAF fails |
| Foreground service restrictions on Android 14+ | High | Use `foregroundServiceType="dataSync"`; declare permission; call `startForeground` within 5s |
| **Proxy-churn bug resurfaces** for sources that don't expose `directUrl` | Medium | The re-resolve-on-IOException path handles this; the `ProxyLeaseCoordinator` is the tertiary safety net. Add an integration test for the bug scenario. |
| **`data.json` corruption** (mid-write crash) | Low | Atomic write protocol (temp file → copy to SAF — see `04-storage-paths.md` §6.4). The scan-on-startup skips corrupt files + logs a warning. |
| **Scan-on-startup is slow** for large libraries (200+ contents) | Medium | Incremental scan (skip if `scan_state.json` says nothing changed); DB rows are the fast path for UI queries after startup. |
| **Two contents with the same title** collide on folder name | Low | Append `(2)` to the second folder (per `04-storage-paths.md` §12 honest notes). The `mainId` distinguishes the two contents internally. |
| HLS segment download failures (flaky CDNs) | Medium | Add per-segment retry (the old project doesn't have this — single failure fails the whole download) |
| MPV can't play content:// URIs directly | Low | Verify in D.6; if needed, add `resolveUrlForMpv` helper |
| Large queue (100+ tasks) slows DB queries | Low | Mitigated by using SQLDelight (indexed by `mainId` + `episodeKey`) |
| Concurrent-downloads pref change doesn't take effect immediately | Medium | Add a Flow collector in `DownloadQueue` that calls `refreshConcurrency()` on pref changes |
| Stale `DOWNLOADING` tasks on restart | High | Reset to `QUEUED` on startup (handled in D.1) |
| POST_NOTIFICATIONS denied on Android 13+ | Medium | Graceful fallback — UI still works; notifier's try/catch swallows the failure |
| **5-digit padding confuses users** expecting 3-digit | Low | The user explicitly requested 5-digit padding for content with 10,000+ episodes. The UI shows the un-padded number (`"EP 1"`); only the file name uses padding. |
| **`dimensionPriority` default `[AUDIO, QUALITY, SERVER]`** doesn't match the user's mental model | Low | **REVIEW-5 M45:** the default is a DELIBERATE change (the OLD project's effective priority was inconsistent — neither matches). The user can rearrange via the new "Priority order" section. Users who relied on the OLD iteration order (server-first) can flip to `[SERVER, AUDIO, QUALITY]`. |

## 9. Cross-references (post-rewrite)

- `00-overview.md` — high-level architecture + data flow.
- `01-workflow-click-to-queue.md` — the tap-to-queue trace (reference for D.2/D.6).
- `02-queue-management.md` — `DownloadQueue` internals + the NEW start-next + concurrency design (reference for D.1/D.3).
- `03-state-machine.md` — state transitions + persistence (reference for D.1).
- `04-storage-paths.md` — **NEW storage system** (reference for D.1).
- `05-downloaders.md` — HTTP / HLS / Advanced engines + the NEW smooth progress (reference for D.1/D.3).
- `06-notifications-foreground-service.md` — **NEW notification design** + the foreground service (reference for D.4).
- `07-settings-preferences.md` — all 17 settings + the UI replication spec (reference for D.5).
- `08-downloads-page-ui.md` — the Downloads page UI (higher-level summary, reference for D.6).
- `09-details-page-download-ui.md` — per-episode UI (reference for D.2/D.6).
- `10-player-integration.md` — offline playback + the **proxy-churn bug fix** (reference for D.2/D.6).
- `11-db-schema.md` — **SQLDelight schema (re-keyed by `mainId`)** (reference for D.0/D.1).
- `12-di-wiring.md` — Koin wiring (reference for D.1/D.2/D.4).
- `14-auto-download-engine.md` — **the 5-step priority pipeline** + settings UI (reference for D.2/D.5).
- `15-ui-and-bug-analysis.md` — **Downloads page UI replication spec** + proxy-churn bug (reference for D.2/D.6).
- `16-quality-of-life.md` — **NEW QoL features** (reference for D.7).
