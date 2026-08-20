# 00 — Executive Summary: Old ANI-KUTA Download System

> Source project: `/home/z/my-project/ANI-KUTA/ANI-KUTA/REFERENCES/old-kuta/ANIKUTA/`
> This is **temporary** documentation to drive re-implementation in the new project. Cross-references: see files `01-*` through `13-*` in this folder.

## 1. What it does

The old ANI-KUTA download system lets the user tap a **download button on an episode row** in the anime-details page, and the episode video (plus all its subtitle tracks, plus an informational `metadata.json`) is fetched from the resolved video URL and saved to a **user-picked SAF folder** so it can be played offline later.

Key capabilities:

| Capability | How |
|---|---|
| Direct MP4/MKV/WebM/TS download | `HttpDownloader` — single-threaded OkHttp stream |
| HLS (.m3u8) download | `HlsDownloader` — parses playlist + concatenates segments into a `.ts` file (no ffmpeg) |
| Multi-threaded + resume | `AdvancedHttpDownloader` + `DownloadResumeManager` — Range requests + per-chunk `.part` files |
| Concurrency limit | `DownloadQueue` with a `Semaphore` (1–5 parallel, default 1) |
| Wi-Fi-only | `DownloadPreferences.wifiOnly()` checked before each `tryStartNext()` |
| Pause / Resume / Cancel / Retry | All implemented as queue operations |
| Persistent queue | `DownloadStore` — JSON in `SharedPreferences` (NOT a DB table) |
| Notifications | `DownloadNotificationManager` — ongoing summary + per-task completion/error |
| Auto-clear completed after 10s | `DownloadViewModel` removes COMPLETED tasks from the active list (file stays) |
| SAF storage | `DownloadStorageProvider` — user picks a tree URI; we persist read/write permission |
| Offline playback | `DownloadManager.isEpisodeDownloaded()` + `getDownloadedVideoUri()` short-circuit the resolver |
| Per-episode state UI | `episodeDownloadStates: Flow<Map<String, DownloadTask>>` keyed by `"$contentId|$episodeNumber"` |
| Auto-pick best video | `DownloadOrchestrator.selectBestVideo()` based on preference lists (quality/audio/server) |
| Manual picker fallback | `DownloadVideoPickerSheet` when auto-download is OFF or fallback is `ASK` |

## 2. High-level architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          :app (orchestrator)                            │
│                                                                         │
│  AnikutaRoot.kt ── AppController ── DownloadOrchestrator                │
│         │                  │                  │                         │
│         │                  │                  ├── ResolverService       │
│         │                  │                  │   (resolves episode →   │
│         │                  │                  │    servers → videos)    │
│         │                  │                  │                         │
│         │                  │                  ▼                         │
│         │                  │          DownloadManager (interface)       │
│         │                  │                  │                         │
│         │                  │                  ▼                         │
│         │                  │          DefaultDownloadManager            │
│         │                  │                  │                         │
│         │                  │     ┌────────────┼─────────────┐          │
│         │                  │     ▼            ▼             ▼          │
│         │                  │  DownloadQueue  HttpDownloader  Notifier   │
│         │                  │   (semaphore,   (delegates to               │
│         │                  │    StateFlow,   HLS / Advanced)            │
│         │                  │    persistence)                            │
│         │                  │                  │                         │
│         │                  │     ┌────────────┼─────────────┐          │
│         │                  │     ▼            ▼             ▼          │
│         │                  │  DownloadStore  StorageProvider  TempCache │
│         │                  │  (JSON in       (SAF DocumentFile, (cache) │
│         │                  │   SharedPrefs)  user-picked tree)          │
│         │                  │                                              │
│         │                  └── episodeDownloadStates Flow ──┐           │
│         │                                                     ▼          │
│         │              ┌────────────────────────────────────────┐      │
│         │              │ :feature:anime-details                 │      │
│         │              │   EpisodeRow → EpisodeDownloadControl   │      │
│         │              │   (shows state per episode)             │      │
│         │              └────────────────────────────────────────┘      │
│         │                                                              │
│         │              ┌────────────────────────────────────────┐      │
│         └─────────────▶│ :feature:download                       │      │
│                        │   DownloadsScreen (queue + sections)    │      │
│                        │   DownloadedFilesScreen                 │      │
│                        │   DownloadSettingsScreen                │      │
│                        │   DownloadVideoPickerSheet              │      │
│                        └────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
```

## 3. Module map

| Module | Role | Key files |
|---|---|---|
| `:core:download` | The engine — pure logic, no Compose UI | `DownloadManager.kt` (interface), `DefaultDownloadManager.kt` (impl), `DownloadQueue.kt`, `DownloadStore.kt`, `DownloadStorageProvider.kt`, `HttpDownloader.kt`, `HlsDownloader.kt`, `VideoTypeDetector.kt`, `TempDownloadCache.kt`, `DynamicProgressTracker.kt`, `DownloadPreferences.kt`, `DownloadNotificationManager.kt`, `DownloadLogger.kt`, `ServerDiscoveryStore.kt`, `advanced/AdvancedHttpDownloader.kt`, `advanced/DownloadResumeManager.kt`, `di/DownloadModule.kt` |
| `:app` | Bridges resolver ↔ engine; hosts global state in `AppController` | `download/DownloadOrchestrator.kt`, `di/DownloadAppModule.kt`, `migration/DownloadMigration.kt`, `navigation/AppController.kt`, `navigation/AnikutaRoot.kt` |
| `:feature:download` | Compose UI for the Downloads page + settings + picker sheet | `DownloadsScreen.kt`, `DownloadedFilesScreen.kt`, `DownloadSettingsScreen.kt`, `DownloadVideoPickerSheet.kt`, `DownloadViewModel.kt`, `DownloadUiState.kt`, `DownloadsMoreEntries.kt`, `ExtensionSourceInfo.kt`, `components/{DragReorderableList,QueueRow,DownloadedAnimeCard,DownloadsEmptyState}.kt`, `di/DownloadModule.kt` |
| `:feature:anime-details` | Per-episode download button + state UI on the details page | `EpisodeDownloadControl.kt`, `EpisodeDownloadState.kt`, `EpisodesSection.kt`, `AnimeDetailScreen.kt`, `DetailContent.kt` |
| `:feature:watch` | Consumes downloaded video URI for offline playback | `WatchScreen.kt` (no direct download dependency — receives a `WatchRequest` already short-circuited by `AppController`) |
| `:core:preferences` | Provides the reactive `PreferenceStore` / `Preference<T>` interface | (used by `DownloadStore`, `DownloadPreferences`, `ServerDiscoveryStore`) |

## 4. Data flow: button-click → file-on-disk

```
User taps download icon on episode row
  │
  ▼
EpisodeDownloadControl.onDownload()
  │  (passed in from EpisodesSection → DetailContent → AnimeDetailScreen → AppController)
  ▼
AppController.downloadEpisode(episode, source, watchCtx, contentId)
  │  1. builds DownloadAnimeInfo(contentId, title, coverUrl)
  │  2. sets resolvingEpisodes[episode.url] = true  (immediate UI feedback)
  │  3. launch(IO) { downloadOrchestrator.enqueueDownload(animeInfo, episode, source) }
  ▼
DownloadOrchestrator.enqueueDownload(anime, episode, source)
  │  1. checks manager.isFolderReady() → if not, returns Error
  │  2. resolver.resolve(source, episode) → ResolverResult.Success(servers)
  │  3. serverDiscovery.recordServers(source.id, serverNames)  (passive)
  │  4. if !autoDownload → returns ShowPicker (UI shows DownloadVideoPickerSheet)
  │     else selectBestVideo(sourceId, servers)
  │       - respects qualityPreferences, audioPreferences, serverPreferences
  │       - applies FallbackStrategy (TRY_NEXT / ASK / DO_NOT_DOWNLOAD)
  │  5. buildRequest(...) → DownloadRequest(anime, episode, videoUrl, headers, subs, ...)
  │  6. manager.enqueueDownload(request) → taskId
  ▼
DownloadManager.enqueueDownload(request)
  │  1. validates videoUrl non-blank + storage.isFolderReady()
  │  2. queue.enqueue(request)
  ▼
DownloadQueue.enqueue(request)
  │  1. dedup by composite key "$contentId|$episodeNumber"
  │  2. new DownloadTask(id = ++idCounter, status = QUEUED)
  │  3. update _tasks StateFlow + persistNow()
  │  4. tryStartNext()  (acquires a Semaphore permit if free, launches download job)
  ▼
DownloadQueue.launchDownload(task)
  │  permits.withPermit {  // up to N concurrent (default 1)
  │    status = DOWNLOADING
  │    downloader.download(task) { downloaded, total ->
  │       DynamicProgressTracker.compute(...) → mutateTask(progress, downloadedBytes, totalBytes)
  │    }
  │  }
  ▼
HttpDownloader.download(task, onProgress)
  │  1. VideoTypeDetector.detectFromUrl(url) → HLS? delegate to HlsDownloader
  │  2. else if method == ADVANCED → AdvancedHttpDownloader.download()
  │     (HEAD probe → if Range supported: N parallel chunks → concatenate)
  │  3. else downloadNormal(url, headers, tempFile, onProgress)
  │     - OkHttp GET → Content-Type detection → stream to internal cache file
  │  4. validateDownloadedFile (size >= 500KB)
  │  5. verifyVideoMagicBytes (reject HTML/PNG/JPEG masquerading as video)
  │  6. downloadSubtitlesToCache (best-effort per subtitle track)
  │  7. writeMetadataToCache (episode metadata JSON)
  │  8. storage.publishToUserFolder(...) → copies validated temp → SAF folder
  │  9. finally { tempCache.cleanupTask(task.id) }  (always)
  ▼
DownloadStorageProvider.publishToUserFolder(anime, episode, tempVideo, tempSubs, tempMeta, ext)
  │  ensureEpisodeDir() creates: <root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/data/subtitles/
  │  copies video → Episode NNN/video.<ext>
  │  copies each subtitle → Episode NNN/data/subtitles/<lang>_<i>.<ext>
  │  copies metadata.json → Episode NNN/data/metadata.json
  │  returns PublishResult.Success(videoUri, subtitleUris, sizeBytes)
  ▼
DownloadQueue: status = COMPLETED, progress = 100, videoUri set, subtitleUris set
  │  persistNow()
  │  onTaskCompleted?.invoke(task) → notifier.notifyCompleted(task)
  ▼
UI: episode row state goes from DOWNLOADING → Downloaded (green checkmark)
    DownloadsScreen: row appears in the "queue" section, then auto-clears after 10s
    (the file stays on disk; only the in-memory task is removed from the active list)
```

## 5. Key design decisions (worth knowing for re-implementation)

1. **The `DownloadManager` is an interface** with one implementation (`DefaultDownloadManager`). The original intent was for a future `OneDmDownloadManager` to plug in. **Decision**: re-implement the same interface — keeps the door open.
2. **The queue is NOT in SQLDelight.** It's a JSON-serialized `List<DownloadTask>` in `SharedPreferences` via `PreferenceStore.getObject(...)`. Reason (quoted from `DownloadStore.kt:14-23`): "The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how `WatchProgressStore` already works."
3. **Storage is SAF (`DocumentFile`/content:// URIs), never `java.io.File` for the user folder.** User must pick a folder via `ActivityResultContracts.OpenDocumentTree()`. We persist the URI + take persistable permission. **However**, partial downloads go to the **internal cache** first (`TempDownloadCache`), then a single atomic copy to SAF — this avoids polluting the user folder on failure + is faster than SAF per-byte writes.
4. **Subtitles are always downloaded** (no user option). Audio tracks too if present.
5. **Progress is dynamically tracked** (`DynamicProgressTracker`): never shows 100% until truly complete (caps at 90%), handles unknown Content-Length via a 10MB-ahead estimator.
6. **No foreground service.** The system just runs in an app-scoped `CoroutineScope(SupervisorJob + Dispatchers.IO)`. Notifications are posted via `NotificationManagerCompat` (no `startForeground`). **This is a potential gap** — Android 14+ may kill background downloads. Worth flagging in the new project (see `13-implementation-plan.md`).
7. **Composite key is `"$contentId|$episodeNumber"`** (with 3-decimal-place format, e.g. `"al:154587|1.000"`). Source-independent — survives source switches. This is critical for offline playback after switching extensions.
8. **Filesystem fallback for offline lookup**: when no in-memory task matches, falls back to scanning `<root>/ANIKUTA/downloads/anime/<... [al-NNN]>/Episode NNN/` for a `video.*` file. Handles prior installs / source switches.

## 6. Honest notes / things that look like bugs/TODOs

- `DefaultDownloadManager.observeJob` collects the queue in a background scope — it updates the **summary notification** but the actual `notifyCompleted` / `notifyError` one-shots are posted from `DownloadQueue`'s job-completion handler via `onTaskCompleted` / `onTaskError` callbacks. This is intentional (avoid re-posting on every state emission), but means the wiring is split across two classes.
- `DownloadQueue.enqueue` checks dedup, but if the existing task is `ERROR` it calls `resumeInternal` which moves it to `QUEUED` — but **does not call `tryStartNext` again** after the initial one in `enqueue`. Wait — it does, `resumeInternal` calls `tryStartNext`. OK.
- `DownloadQueue.cancel` removes the task entirely from the list — but a `CANCELLED` status enum exists. The `purgeCancelled()` on startup is now redundant because cancelled tasks never get persisted. Defensive but dead code.
- `HlsDownloader` always picks the **first** variant in a master playlist (typically the highest bandwidth, but not guaranteed). No quality picker for HLS.
- `HlsDownloader` rejects encrypted HLS with a clear error — but a future 1DM/ffmpeg method is supposed to handle it. Not implemented.
- The Advanced method (multi-threaded Range) doesn't support HLS — falls back to single-threaded internally if it detects HLS via a HEAD-probe failure.
- `DownloadNotificationManager` has a static `lastProgressAt` (`@Volatile` companion) — throttles progress notifications app-wide to 1 per 800ms. Acceptable.
- `EpisodeDownloadControl.kt` mentions `AnimatedContent` in its KDoc but doesn't actually use it (no `AnimatedContent` import).
- `DownloadsScreen.kt` groups queue by `anime.title` (not contentId), which would conflate two different anime with the same title. The "Downloaded" section groups by `DownloadedAnimeKey(contentId, ...)` correctly though. Minor inconsistency.

## 7. Cross-references to other docs in this folder

- `01-workflow-click-to-queue.md` — detailed trace of the tap-to-queue flow with code snippets
- `02-queue-management.md` — `DownloadQueue` internals, concurrency, pause/resume/cancel
- `03-state-machine.md` — `DownloadStatus` + transitions + persistence
- `04-storage-paths.md` — exact folder structure, file naming, SAF setup
- `05-downloaders.md` — `HttpDownloader` / `HlsDownloader` / `AdvancedHttpDownloader` internals
- `06-notifications-foreground-service.md` — notifications, channel, no-service warning
- `07-settings-preferences.md` — every setting in `DownloadPreferences` + UI mapping
- `08-downloads-page-ui.md` — `DownloadsScreen` layout + components
- `09-details-page-download-ui.md` — `EpisodeDownloadControl` + per-episode state mapping
- `10-player-integration.md` — how `WatchScreen` consumes the local file URI
- `11-db-schema.md` — confirms no DB tables; JSON-in-SharedPrefs only
- `12-di-wiring.md` — Koin module breakdown
- `13-implementation-plan.md` — **the deliverable** — phased plan for the new project
