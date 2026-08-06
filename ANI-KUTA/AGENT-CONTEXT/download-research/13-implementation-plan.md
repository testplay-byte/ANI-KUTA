# 13 — Implementation Plan: Re-implementing the Download System in the New Project

> **This is the most important deliverable.** The user will use this to actually build the new system.
> New project root: `/home/z/my-project/ANI-KUTA/ANI-KUTA/APP/ani-kuta/`
> Old project root (reference): `/home/z/my-project/ANI-KUTA/ANI-KUTA/REFERENCES/old-kuta/ANIKUTA/`

## 1. Current state of the new project

### What already exists

| Module / File | Status |
|---|---|
| `core/download/` Gradle module | ✅ exists, depends on `core:common`, `core:database`, `core:preferences`, `core:network`, `okhttp`, `kotlinx.coroutines`, `logcat`, `koin` |
| `core/download/src/main/java/.../DownloadManager.kt` | ⚠️ STUB — basic single-threaded HTTP, writes to `context.filesDir/downloads/`, uses SQLDelight directly, no pause/resume/HLS/SAF |
| `core/download/src/main/java/.../DownloadState.kt` | ⚠️ STUB — sealed interface with `Queued / Downloading(progress) / Paused / Completed / Failed(msg)` |
| `core/download/src/main/java/.../DownloadModule.kt` | ⚠️ STUB — single Koin binding for the stub `DownloadManager` |
| `core/database/src/main/sqldelight/.../downloadQueue.sq` | ✅ exists — `download_queue` table (id, episode_key, state, progress, error_message, queued_at, started_at, completed_at) |
| `core/database/src/main/sqldelight/.../downloadedEpisode.sq` | ✅ exists — `downloaded_episode` table (episode_key, file_path, file_size, quality, downloaded_at) |
| `core/preferences/PreferenceStore.kt` | ⚠️ simple SharedPreferences wrapper — **NOT reactive** (no `changes(): Flow<T>` like the old project) |
| `core/video-resolver/` | ✅ exists — `VideoResolver`, `ResolverState`, `ResolverTypes`, `ResolvedVideosRegistry`, `VideoResolverModule` |
| `feature/watch/` | ✅ exists — `WatchScreen`, `WatchKey`, player infrastructure with MPV |
| `feature/anime-details/` | ✅ exists — `DetailsScreen`, `DetailsViewModel`, `ResolverSheet`, `ManualSearchSheet`, etc. (but NO `EpisodeDownloadControl` / `EpisodeDownloadState` yet) |
| `app/src/main/AndroidManifest.xml` | ✅ has `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` permissions declared |
| `app/src/main/java/.../MoreScreen.kt` | ✅ exists — can add a "Downloads" entry |
| minSdk | 24 (Android 7.0) — scoped storage rules apply for API 29+; SAF works on all APIs |
| compileSdk / targetSdk | 36 |

### What's missing

| Component | Status |
|---|---|
| `:feature:download` Gradle module | ❌ doesn't exist |
| `DownloadManager` interface + `DefaultDownloadManager` impl | ❌ (stub is a concrete class) |
| `DownloadQueue` (concurrency, state machine) | ❌ |
| `DownloadStore` (persistence — JSON or SQLDelight) | ❌ |
| `DownloadStorageProvider` (SAF) | ❌ |
| `DownloadPreferences` (all 15 settings) | ❌ |
| `DownloadNotificationManager` + `DownloadService` (foreground) | ❌ |
| `HttpDownloader` / `HlsDownloader` / `VideoTypeDetector` | ❌ |
| `AdvancedHttpDownloader` + `DownloadResumeManager` | ❌ |
| `DynamicProgressTracker` | ❌ |
| `TempDownloadCache` | ❌ |
| `ServerDiscoveryStore` | ❌ |
| `DownloadOrchestrator` (resolver ↔ engine bridge) | ❌ |
| `DownloadViewModel` + `DownloadUiState` | ❌ |
| `DownloadsScreen` + `DownloadedFilesScreen` + `DownloadSettingsScreen` + `DownloadVideoPickerSheet` | ❌ |
| `EpisodeDownloadControl` + `EpisodeDownloadState` (in `:feature:anime-details`) | ❌ |
| Components: `DragReorderableList`, `DownloadedAnimeCard`, etc. | ❌ |
| Reactive `PreferenceStore` (Flow support) | ❌ (current is non-reactive) |

## 2. Architecture differences: old vs new

| Aspect | Old project | New project |
|---|---|---|
| Persistence | JSON in SharedPreferences (`DownloadStore`) | SQLDelight tables (`download_queue` + `downloaded_episode`) — **already exist** |
| Preferences | `PreferenceStore` with reactive `Preference<T>.changes(): Flow<T>` | Simple SharedPreferences wrapper, **no Flow** — needs extending |
| Package name | `app.confused.anikuta.*` | `com.confused.anikuta.*` |
| Module naming | `:core:download`, `:feature:download`, `:app` | Same convention |
| Compose BOM | 2025.03.00 (Material3 1.3.1) | Newer (verify in `gradle/libs.versions.toml`) |
| Navigation | Voyager (`cafe.adriel.voyager.navigator.Navigator`) | Nav3 (NavKey-based — verify) |
| Player | Custom MPV binding (`AnikutaMPVView`) | Same (`core/player/`) |
| Video resolver | `:feature:video-resolver` → moved to `:core:video-resolver` (Phase 8) | `:core:video-resolver` exists with `VideoResolver`, `ResolverTypes` |
| Foreground service | ❌ (runs in CoroutineScope) | **RECOMMENDED**: add `DownloadService` |
| DI framework | Koin | Koin |
| DB framework | SQLDelight (`AnikutaDatabase`) | SQLDelight (`AnikutaDatabase`) |

## 3. Module mapping (old → new)

| Old module | New module | Notes |
|---|---|---|
| `:core:download` | `:core:download` (expand existing stub) | Add all engine files |
| `:feature:download` | `:feature:download` (CREATE) | New Gradle module |
| `:app` `DownloadOrchestrator` | `:app` `DownloadOrchestrator` (CREATE) | Bridges `:core:video-resolver` + `:core:download` |
| `:app` `DownloadAppModule` (DI) | `:app` `DownloadAppModule` (CREATE) | Koin wiring |
| `:app` `AppController` download methods | `:app` `AppController`-equivalent (CREATE/EXTEND) | The new project's nav controller (likely `Nav3`-based) |
| `:feature:anime-details` `EpisodeDownloadControl` + `EpisodeDownloadState` | `:feature:anime-details/impl` (ADD) | Per-episode UI |
| `:feature:anime-details` `EpisodesSection` (download wiring) | `:feature:anime-details/impl` (MODIFY) | Add `onDownloadEpisode` etc. to `EpisodeRow` |

## 4. Design decisions to make (BEFORE coding)

### D1: Persistence — JSON-in-SharedPrefs OR SQLDelight?

**Recommendation: SQLDelight** (use the existing tables). Reasons:
- Tables already exist (`download_queue`, `downloaded_episode`).
- Queryable — "all COMPLETED for contentId X" is a SQL query, not a list filter.
- Better story for app restarts (DB survives; can query state directly).
- Matches the new project's existing architecture (everything else uses SQLDelight).

**Schema changes needed** to carry the full task data:

Option B1 (lean — separate columns):
```sql
CREATE TABLE download_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_id TEXT NOT NULL,            -- "al:154587"
    episode_url TEXT NOT NULL,
    episode_number REAL NOT NULL,
    episode_name TEXT NOT NULL,
    anime_title TEXT NOT NULL,
    cover_url TEXT,
    cover_color INTEGER,
    video_url TEXT NOT NULL,
    video_headers TEXT,
    subtitle_tracks TEXT,                -- JSON array of DownloadTrack
    audio_tracks TEXT,                   -- JSON array
    source_id INTEGER NOT NULL,
    video_server TEXT,
    video_quality TEXT,
    video_audio TEXT,
    state TEXT NOT NULL,                 -- "QUEUED" / "DOWNLOADING" / "PAUSED" / "COMPLETED" / "ERROR"
    progress INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    total_bytes INTEGER NOT NULL DEFAULT -1,
    error_message TEXT,
    video_uri TEXT,                      -- set on COMPLETED
    subtitle_uris TEXT,                  -- JSON array of content:// URIs
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_download_queue_state ON download_queue(state);
CREATE INDEX idx_download_queue_content ON download_queue(content_id);
CREATE UNIQUE INDEX idx_download_queue_episode ON download_queue(content_id, episode_number);
```

Option B2 (denormalized — JSON blob column):
```sql
CREATE TABLE download_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content_id TEXT NOT NULL,
    episode_number REAL NOT NULL,
    state TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    request_json TEXT NOT NULL,          -- full DownloadRequest as JSON
    video_uri TEXT,
    subtitle_uris_json TEXT,
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    updated_at INTEGER NOT NULL
);
```

**Recommendation: Option B1** (separate columns). More verbose but queryable + no JSON parsing on every read. Composite key uniqueness enforced by index.

`downloaded_episode` table: keep as-is OR add `content_id`, `episode_number`, `anime_title`, `cover_url` columns for the Downloads-screen grouping (currently only has `episode_key`, `file_path`, `file_size`, `quality`, `downloaded_at`). **Recommendation: add the columns** — the grouping UI needs them.

### D2: SAF folder picker OR app-specific storage?

The old project uses SAF (user picks a folder). Pros: user controls location, survives app uninstall, can be on SD card. Cons: SAF per-byte writes are slow (mitigated by the internal-cache-first pipeline).

**Recommendation: SAF (replicate old project)**. The new project already declares `MANAGE_EXTERNAL_STORAGE` (for "All files access" in Setup Wizard) but that's a different model. SAF is the modern, user-friendly approach and works on all API levels.

### D3: Foreground service — yes or no?

The old project doesn't have one. **Recommendation: YES** — add `DownloadService` with `foregroundServiceType="dataSync"`. Reasons:
- Android 14+ kills background downloads aggressively.
- The `FOREGROUND_SERVICE_DATA_SYNC` permission is already declared.
- The notification is already needed (for progress) — make it the foreground notification.

Implementation: `DownloadService` starts when the first download starts, calls `startForeground(SUMMARY_ID, notification)`, stops when the queue empties. The `DownloadManager` triggers start/stop via `Context.startForegroundService(Intent(...))` + `Service.stopSelf()`.

### D4: Reactive PreferenceStore

The new project's `PreferenceStore` is non-reactive (just `getString/putString/...`). The old project's UI relies heavily on `Preference.changes(): Flow<T>` for live updates.

**Recommendation**: extend `PreferenceStore` with a `Flow<T>` API. Options:
- Wrap each getter/setter with a `MutableStateFlow` keyed by preference key.
- Use `SharedPreferences.OnSharedPreferenceChangeListener` to broadcast changes to a single `MutableSharedFlow<String>` (key), then derive per-key Flows.

The simpler approach: a `preferenceFlow<T>(key, default)` helper that returns a `Flow<T>` backed by `OnSharedPreferenceChangeListener`. This is a small addition to `:core:preferences` and benefits the whole app (not just downloads).

### D5: Episode-key format

The new project's stub uses `episode_key: String` as a plain string. The old project uses `"$contentId|$episodeNumber"` (3-decimal format). **Recommendation**: use the old project's composite key — source-independent, survives source switches.

### D6: HLS support — yes or no?

The old project has `HlsDownloader` (segment concatenation, no ffmpeg). Many anime extensions return HLS URLs. **Recommendation: YES** — port `HlsDownloader` as-is. It's pure Kotlin (no ffmpeg dependency), handles unencrypted HLS (the common case), and includes PNG-header stripping for anti-scraping CDNs.

### D7: Advanced (multi-threaded) method — yes or no?

The old project has `AdvancedHttpDownloader` with Range-request multi-threading + per-chunk resume. It's complex (~400 lines + the resume manager). **Recommendation: YES for parity, but make it Phase 2** — ship the Normal method first, add Advanced as a follow-up. The Normal method works for direct MP4/MKV + HLS; Advanced only helps for large direct-video files on slow servers.

## 5. Implementation phases

### Phase D.0 — Foundations (1-2 days)

**Goal**: extend the new project's infrastructure to support the download system.

Tasks:
1. **Extend `PreferenceStore`** (`:core:preferences`) with a reactive `Flow<T>` API. Add a `preferenceFlow<T>(key, default)` helper or expose `Preference<T>`-like objects with `changes(): Flow<T>`. (Or — simpler — add a `SharedPreferences.OnSharedPreferenceChangeListener` wrapper that emits a `Flow<Unit>` per key.)
2. **Update SQLDelight schema**: modify `downloadQueue.sq` + `downloadedEpisode.sq` to carry the full task data (see D1 above). Add a migration file (`3.sqm` or similar) since these tables already exist.
3. **Add the `"download"` qualified `OkHttpClient`** to `:core:network` (or wherever the shared client lives). Long timeouts (30s connect, 60s read/write).
4. **Add SAF DocumentFile dependency** to `:core:download`'s `build.gradle.kts`: `implementation("androidx.documentfile:documentfile:1.0.1")`.
5. **Add kotlinx-serialization-json** to `:core:download` (for `DownloadTrack` JSON columns + `metadata.json`).
6. **Delete the stub `DownloadManager.kt` + `DownloadState.kt`** in `:core:download` (they'll be replaced).

### Phase D.1 — Engine (3-4 days)

**Goal**: port the `:core:download` engine. No UI yet — just the engine + DI.

Files to create in `:core:download/src/main/java/com/confused/anikuta/core/download/`:

| File | Source (old project) | Lines | Notes |
|---|---|---|---|
| `DownloadModels.kt` | `DownloadModels.kt` | ~111 | `DownloadAnimeInfo`, `DownloadEpisodeInfo`, `DownloadTrack`, `TrackKind`, `DownloadedEpisode` |
| `DownloadRequest.kt` | `DownloadRequest.kt` | ~46 | Same (change package) |
| `DownloadStatus.kt` | `DownloadStatus.kt` | ~42 | Same enum + `isTerminal` / `isActive` |
| `DownloadTask.kt` | `DownloadTask.kt` | ~58 | `@Serializable` data class (for JSON columns if using Option B2) |
| `DownloadManager.kt` | `DownloadManager.kt` (interface) | ~133 | The interface (rename old stub) |
| `DownloadStore.kt` | `DownloadStore.kt` | ~75 (adapt) | Adapt to SQLDelight (see D1) — OR keep JSON-in-SharedPrefs if going Option A |
| `DownloadPreferences.kt` | `DownloadPreferences.kt` | ~204 | All 15 settings |
| `DownloadLogger.kt` | `DownloadLogger.kt` | ~40 | Same (use `com.confused.anikuta.core.common.Logger` if it has the same API) |
| `DynamicProgressTracker.kt` | `DynamicProgressTracker.kt` | ~123 | Same (pure math) |
| `TempDownloadCache.kt` | `TempDownloadCache.kt` | ~93 | Same |
| `DownloadStorageProvider.kt` | `DownloadStorageProvider.kt` | ~570 | Same (SAF DocumentFile) |
| `VideoTypeDetector.kt` | `VideoTypeDetector.kt` | ~116 | Same |
| `HttpDownloader.kt` | `HttpDownloader.kt` | ~538 | Same |
| `HlsDownloader.kt` | `HlsDownloader.kt` | ~333 | Same |
| `DownloadQueue.kt` | `DownloadQueue.kt` | ~315 | Adapt persistence to SQLDelight |
| `DownloadNotificationManager.kt` | `DownloadNotificationManager.kt` | ~191 | Same |
| `DefaultDownloadManager.kt` | `DefaultDownloadManager.kt` | ~255 | Same |
| `ServerDiscoveryStore.kt` | `ServerDiscoveryStore.kt` | ~83 | Same |
| `DownloadService.kt` | (NEW — no old equivalent) | ~150 | Foreground service — see D3 |
| `advanced/AdvancedHttpDownloader.kt` | same | ~401 | (Phase D.1.5 — defer if wanted) |
| `advanced/DownloadResumeManager.kt` | same | ~117 | (Phase D.1.5) |
| `di/DownloadModule.kt` | `di/DownloadModule.kt` | ~71 | Koin bindings |

**Adapt `DownloadQueue` to SQLDelight**:
- Replace `store.purgeCancelled()` with a SQLDelight query: `DELETE FROM download_queue WHERE state = 'CANCELLED'` (or just don't persist CANCELLED).
- Replace `store.setAll(tasks)` with per-row `updateDownloadState` / `insertDownloadQueue` / `deleteDownloadQueue` queries.
- For progress ticks (throttled), use a single `UPDATE download_queue SET progress = ?, downloaded_bytes = ?, total_bytes = ?, updated_at = ? WHERE id = ?` query.
- On startup, query `SELECT * FROM download_queue` to populate `_tasks`. Reset any `DOWNLOADING` tasks to `QUEUED` (fixes the old project's bug — see `03-state-machine.md` §7).

**Adapt `DownloadStore`**: either delete (if using SQLDelight directly via `AnikutaDatabase.downloadQueueQueries`) OR keep as a thin wrapper around SQLDelight for backward-compat with the JSON shape (not recommended — adds complexity).

**Create `DownloadService`** (foreground service):
```kotlin
class DownloadService : Service() {
    private val manager by inject<DownloadManager>()
    private val notifier by inject<DownloadNotificationManager>()  // OR create here

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notifier.buildSummaryNotification(emptyList())  // initial empty
        startForeground(SUMMARY_ID, notification)
        // Observe the queue — update foreground notification on changes.
        // Stop self when queue empties.
        return START_STICKY
    }

    companion object {
        const val SUMMARY_ID = 9001
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
```

The `DownloadManager` calls `DownloadService.start(context)` on enqueue (if not already running) + `DownloadService.stop(context)` when the queue empties (observed in `DefaultDownloadManager.observeJob`).

**Manifest entry** (in `:core:download`'s or `:app`'s manifest):
```xml
<service
    android:name="com.confused.anikuta.core.download.DownloadService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

**Permissions** (already in `:app` manifest): `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE`, `INTERNET`.

### Phase D.2 — Storage (1-2 days)

**Goal**: SAF folder picker + storage paths.

Already done as part of D.1 (porting `DownloadStorageProvider` + `TempDownloadCache`). Verify:
- Folder picker works (`ActivityResultContracts.OpenDocumentTree`).
- Persistable URI permission taken.
- Folder structure created: `<root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/{video.<ext>, data/{subtitles/, metadata.json}}`.
- Internal-cache-first pipeline works (temp download → validate → publish to SAF).
- Filesystem fallback (`findEpisodeDirByNumber`) works for source switches.

**Testing**: manually trigger a download (via a temporary debug button) + verify the folder structure with a file manager.

### Phase D.3 — Orchestrator + UI wiring (2-3 days)

**Goal**: bridge the resolver + engine + add the per-episode download UI on the details page.

Files to create in `:app/src/main/java/com/confused/anikuta/`:

| File | Source (old project) | Notes |
|---|---|---|
| `download/DownloadOrchestrator.kt` | `download/DownloadOrchestrator.kt` | Adapt `ResolverService` → new project's `VideoResolver`; adapt `ResolverResult` / `ResolverServer` / `ResolverVideo` types |
| `download/EnqueueResult.kt` | (same file in old project) | Sealed interface |
| `download/PickerContext.kt` | (same file) | Data class |
| `di/DownloadAppModule.kt` | `di/DownloadAppModule.kt` | Koin module |
| `navigation/AppController.kt` (EXTEND) | `navigation/AppController.kt` | Add download methods (`downloadEpisode`, `cancelDownload`, etc.) — adapt to the new project's nav controller (Nav3) |

Files to create in `:feature:anime-details/impl/src/main/java/com/confused/anikuta/feature/animedetails/`:

| File | Source | Notes |
|---|---|---|
| `EpisodeDownloadState.kt` | same | Sealed interface (UI-side) |
| `EpisodeDownloadControl.kt` | same | The state-driven composable |

Modify in `:feature:anime-details/impl`:
- `EpisodesSection.kt` (or its equivalent — find it) — add `onDownloadEpisode`, `downloadStates`, `onDownloadCancel/Resume/Retry/Delete` params to the section + row.
- `DetailsScreen.kt` — accept the new download callbacks from the host + pass them down.
- `DetailsViewModel.kt` — expose `downloadStates: StateFlow<Map<String, EpisodeDownloadState>>` (collected from `DownloadManager.episodeDownloadStates`).

The host (in `:app`) provides:
- `downloadEpisode(episode, source, watchCtx, contentId)` — calls `DownloadOrchestrator.enqueueDownload`.
- `cancelDownload / resumeDownload / retryDownload / deleteDownload(contentId, episodeUrl)` — locate task + call manager.
- `enqueuePickedVideo(video, serverName, audioLabel)` — for the picker sheet.
- `downloadPickerTarget: State<EnqueueResult.ShowPicker?>` — drives the picker sheet visibility.

### Phase D.4 — Downloads page UI (3-4 days)

**Goal**: create the `:feature:download` module + the three screens + components.

**Create the Gradle module** `:feature:download`:
- `build.gradle.kts` — depends on `:core:download`, `:core:designsystem`, `:core:preferences`, `:core:video-resolver` (for `ResolverVideo` / `ResolverServer` types in the picker sheet), Compose, Koin.
- `src/main/AndroidManifest.xml` — empty (no permissions needed here).
- `settings.gradle.kts` — register the module.

Files to create in `:feature:download/src/main/java/com/confused/anikuta/feature/download/`:

| File | Source (old project) | Notes |
|---|---|---|
| `DownloadUiState.kt` | same | Data class + `DownloadedAnimeKey` |
| `DownloadViewModel.kt` | same | Combine flows + auto-clear after 10s |
| `DownloadsScreen.kt` | same (570 lines) | The main page — adapt to new project's `CollapsingHeader` etc. |
| `DownloadedFilesScreen.kt` | same (206 lines) | The downloaded library page |
| `DownloadSettingsScreen.kt` | same (528 lines) | Settings page |
| `DownloadVideoPickerSheet.kt` | same (233 lines) | Picker bottom sheet |
| `DownloadsMoreEntries.kt` | same (37 lines) | More-screen entry |
| `ExtensionSourceInfo.kt` | same (16 lines) | DTO |
| `components/DragReorderableList.kt` | same (192 lines) | Drag-and-drop reorder |
| `components/DownloadedAnimeCard.kt` | same (183 lines) | (Or use the in-screen private copy — consolidate) |
| `components/DownloadsEmptyState.kt` | same (96 lines) | (Or use the in-screen private copy — consolidate) |
| `components/QueueRow.kt` | same (244 lines) | **Skip** — dead code in old project, `EpisodeRow` in `AnimeSectionCard` supersedes it |
| `di/DownloadModule.kt` | same (19 lines) | `viewModelOf(::DownloadViewModel)` |

Wire `DownloadsMoreEntries` into `MoreScreen.kt`:
```kotlin
item { DownloadsMoreEntries(onOpenDownloads = { navController.push(DownloadsKey) }) }
```

(Where `DownloadsKey` is a Nav3 key — create it in `:feature:download/api`.)

### Phase D.5 — Player integration (1 day)

**Goal**: offline playback short-circuit.

Modify `:app`'s nav controller (the equivalent of `AppController.resolveEpisode`):
- Before resolving a stream, call `downloadManager.isEpisodeDownloaded(contentId, episodeNumber)`.
- If true, build a `WatchRequest` with the local content:// URI + null headers + "Offline" server label + downloaded subtitle URIs.
- Push the `WatchKey` with that `WatchRequest`.
- If false, fall through to the streaming resolver.

Verify the player (`AnikutaMPVView` in `:core:player`) handles content:// URIs (it should — same approach as the LocalProxyServer URLs). If not, add a `resolveUrlForMpv` helper that converts content:// → `fd://<fd>` via `ContentResolver.openFileDescriptor`.

Add an "Offline" badge to the `WatchScreen` (not in old project — see `10-player-integration.md` §10).

### Phase D.6 — Polish + testing (1-2 days)

- Fix the concurrent-downloads pref bug (call `DownloadQueue.refreshConcurrency()` when the pref changes — add a Flow collector in `DownloadQueue.init`).
- Fix the `advancedMaxRetries` default mismatch (set both code + UI to 10).
- Fix the `DOWNLOADING`-on-restart bug (reset to `QUEUED` on startup — handled in D.1's `DownloadQueue` adaptation).
- Fix the `Episode NNN` folder-name floor bug (use `"Episode 012.5"` for non-integer episode numbers).
- Add `AnimatedContent` to `EpisodeDownloadControl` for smooth state transitions.
- Add notification action buttons (Pause / Cancel) to the summary notification.
- Add a deep-link from the notification tap to the Downloads screen.
- Test: enqueue a download → verify folder structure → kill app → restart → verify queue persists → play offline → delete.

## 6. Total estimate

| Phase | Days |
|---|---|
| D.0 Foundations | 1-2 |
| D.1 Engine | 3-4 |
| D.2 Storage | 1-2 (mostly subsumed by D.1) |
| D.3 Orchestrator + UI wiring | 2-3 |
| D.4 Downloads page UI | 3-4 |
| D.5 Player integration | 1 |
| D.6 Polish + testing | 1-2 |
| **Total** | **12-18 days** |

This assumes one developer. Parallelizable: D.3 + D.4 can overlap (different files).

## 7. Things to flag for the user / design decisions

1. **SAF folder picker is mandatory** — the user must pick a folder before any download works. The first-launch flow should prompt for this (either in a Setup Wizard or on first download tap).
2. **Foreground service** — adds complexity but is necessary for Android 14+. The notification becomes the foreground notification (same ID as the summary).
3. **SQLDelight vs JSON** — recommend SQLDelight (Option B1, separate columns) for queryability + matches new project's architecture. Requires schema migration since the tables already exist.
4. **Reactive PreferenceStore** — needed not just for downloads but for any reactive settings UI. Worth doing as a foundational D.0 task.
5. **HLS support** — necessary for many anime extensions. Port `HlsDownloader` as-is.
6. **Advanced method** — defer to Phase D.1.5 (or skip entirely if the Normal method + HLS covers 95% of cases). The Advanced method's complexity (chunk files, resume metadata, Range probe) is significant.
7. **Episode folder name for `.5` specials** — fix the floor bug. Use `"Episode 012"` for integers, `"Episode 012.5"` for non-integers.
8. **Auto-clear completed after 10s** — keep this behavior (the user explicitly requested it in the old project). The file stays on disk; only the in-memory task is removed.
9. **No batch download** — the old project doesn't have it. Consider adding a "Download all" button at the section level (nice-to-have, not parity).
10. **No queue reordering** — the old project's queue is FIFO. The `DragReorderableList` is only for preference lists. If the user wants queue reordering, that's a NEW feature (would need a `reorder(taskId, newPosition)` method on `DownloadQueue` + a `displayOrder` column in the DB).
11. **`DownloadStorageProvider` as a Koin binding** — the old project creates it internally in `DefaultDownloadManager` BUT the `DownloadMigration` asks for it from Koin (which would fail unless there's a binding I missed). Make it an explicit Koin `single` in the new project — cleaner + testable.
12. **Notification tap deep-link** — the old project just opens the launcher activity. The new project should add a deep-link to the Downloads screen (via Nav3's deep-link support or an Intent extra).
13. **`DownloadMigration`** — the old project has a one-shot migration (anilistId → content_id). The new project is a fresh start — **no migration needed** (skip `DownloadMigration` entirely).
14. **Watch progress for unlinked extension anime** — the old project's WatchScreen doesn't record progress when `anilistId == 0`. The new project should fix this (use `contentId` directly, not `anilistId`).

## 8. Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| SAF provider quirks on specific OEMs (Samsung, Xiaomi) | Medium | Test on multiple devices; fall back to app-specific storage if SAF fails |
| Foreground service restrictions on Android 14+ | High | Use `foregroundServiceType="dataSync"`; declare permission; call `startForeground` within 5s |
| HLS segment download failures (flaky CDNs) | Medium | Add per-segment retry (the old project doesn't have this — single failure fails the whole download) |
| MPV can't play content:// URIs directly | Low | Verify in D.5; if needed, add `resolveUrlForMpv` helper |
| Large queue (100+ tasks) slows SharedPreferences JSON | Low | Mitigated by using SQLDelight (Option B) |
| Concurrent-downloads pref change doesn't take effect immediately | Medium | Add a Flow collector in `DownloadQueue` that calls `refreshConcurrency()` on pref changes |
| Stale `DOWNLOADING` tasks on restart | High | Reset to `QUEUED` on startup (handled in D.1) |
| POST_NOTIFICATIONS denied on Android 13+ | Medium | Graceful fallback — UI still works; notifier's try/catch swallows the failure |

## 9. Cross-references

- `00-overview.md` — high-level architecture + data flow.
- `01-workflow-click-to-queue.md` — the tap-to-queue trace (reference for D.3).
- `02-queue-management.md` — `DownloadQueue` internals (reference for D.1).
- `03-state-machine.md` — state transitions + persistence (reference for D.1).
- `04-storage-paths.md` — folder structure + SAF (reference for D.2).
- `05-downloaders.md` — HTTP / HLS / Advanced engines (reference for D.1).
- `06-notifications-foreground-service.md` — notifications + the foreground-service gap (reference for D.1 + D.6).
- `07-settings-preferences.md` — all 15 settings (reference for D.1 + D.4).
- `08-downloads-page-ui.md` — the Downloads page UI (reference for D.4).
- `09-details-page-download-ui.md` — per-episode UI (reference for D.3).
- `10-player-integration.md` — offline playback (reference for D.5).
- `11-db-schema.md` — DB schema options (reference for D.0 + D.1).
- `12-di-wiring.md` — Koin wiring (reference for D.1 + D.3 + D.4).
