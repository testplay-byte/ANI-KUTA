/*
 * Download System — Implementation Plan (research-complete, planning-stage).
 *
 * Sources:
 *  - AGENT-CONTEXT/download-research/00-overview.md  (executive summary + architecture)
 *  - AGENT-CONTEXT/download-research/01-workflow-click-to-queue.md
 *  - AGENT-CONTEXT/download-research/02-queue-management.md
 *  - AGENT-CONTEXT/download-research/03-state-machine.md
 *  - AGENT-CONTEXT/download-research/04-storage-paths.md  (CRITICAL — folder structure)
 *  - AGENT-CONTEXT/download-research/05-downloaders.md  (HTTP / HLS / Advanced)
 *  - AGENT-CONTEXT/download-research/06-notifications-foreground-service.md
 *  - AGENT-CONTEXT/download-research/07-settings-preferences.md  (15 settings)
 *  - AGENT-CONTEXT/download-research/08-downloads-page-ui.md
 *  - AGENT-CONTEXT/download-research/09-details-page-download-ui.md
 *  - AGENT-CONTEXT/download-research/10-player-integration.md
 *  - AGENT-CONTEXT/download-research/11-db-schema.md
 *  - AGENT-CONTEXT/download-research/12-di-wiring.md
 *  - AGENT-CONTEXT/download-research/13-implementation-plan.md  (6 phases, 7 decisions, risk register)
 *
 * Hardcoded for the static dashboard demo — no API calls.
 */

/* ---------------------------------------------------------------------------
 * Hero / status
 * ------------------------------------------------------------------------- */

export const DOWNLOADS_HERO = {
  title: "Download System — Implementation Plan",
  subtitle:
    "Workflow, storage paths, 7-state machine, auto-download engine, foreground service, the proxy-churn fix, and the 9-phase build plan (D.0 → D.8) for the new ANI-KUTA — now hardened by 5 review rounds + a 72-item fix pass.",
  status: "PLAN HARDENED — 5 REVIEWS",
  statusColor: "var(--c-warning)",
  summary:
    "The download-system plan went through 5 senior review rounds (DL-REVIEW-1 through DL-REVIEW-5), surfaced 72 must-fix items (18 carry-over CRITICALs + 54 new IMPORTANT/CRITICAL findings), and was consolidated in the DL-PLAN-FIX pass. Every fix landed: the unbounded re-resolve recursion (M15) is now capped at 1 attempt, the foreground service starts synchronously (M20) — no more ForegroundServiceDidNotStartInTimeException, the DB schema edits the .sq files directly (no .sqm migration — M1+M2), the local HttpException (M49) replaces dead HTTP-branch code, and the RETRYING state (M9) is now propagated through the state machine, queue, DB schema, and UI. The folder tree was rewritten (video/images/text format folders + 5-digit E00001 padding + data.json per content + .nomedia). The auto-download engine is a 5-step pure-function pipeline (flatten → rank → applyFallbacks → pick → globalFallback) with a new dimensionPriority pref (default [AUDIO, QUALITY, SERVER]). Build estimate grew from 23-30 to 30-40 days to absorb the consolidation pass + the re-review + the inevitable mid-implementation discoveries.",
} as const;

/* ---------------------------------------------------------------------------
 * Architecture overview — module map + data-flow diagram (from 00-overview.md)
 * ------------------------------------------------------------------------- */

export const ARCHITECTURE_DIAGRAM = `┌─────────────────────────────────────────────────────────────────────────┐
│                          :app (orchestrator)                            │
│                                                                         │
│  AnikutaRoot.kt ── AppController ── DownloadOrchestrator                │
│         │                  │                  │                         │
│         │                  │                  ├── ResolverService       │
│         │                  │                  │   (resolves episode →   │
│         │                  │                  │    servers → videos)    │
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
│         │              ┌────────────────────────────────────────┐      │
│         └─────────────▶│ :feature:download                       │      │
│                        │   DownloadsScreen (queue + sections)    │      │
│                        │   DownloadedFilesScreen                 │      │
│                        │   DownloadSettingsScreen                │      │
│                        │   DownloadVideoPickerSheet              │      │
│                        └────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘`;

export interface ModuleMapEntry {
  module: string;
  role: string;
  keyFiles: string;
}

export const MODULE_MAP: ModuleMapEntry[] = [
  {
    module: ":core:download",
    role: "The engine — pure logic, no Compose UI",
    keyFiles:
      "DownloadManager.kt (interface), DefaultDownloadManager.kt (impl), DownloadQueue.kt, DownloadStore.kt, DownloadStorageProvider.kt, HttpDownloader.kt, HlsDownloader.kt, VideoTypeDetector.kt, TempDownloadCache.kt, DynamicProgressTracker.kt, DownloadPreferences.kt, DownloadNotificationManager.kt, DownloadLogger.kt, ServerDiscoveryStore.kt, advanced/AdvancedHttpDownloader.kt, advanced/DownloadResumeManager.kt, di/DownloadModule.kt",
  },
  {
    module: ":app",
    role: "Bridges resolver ↔ engine; hosts global state in AppController",
    keyFiles:
      "download/DownloadOrchestrator.kt, di/DownloadAppModule.kt, migration/DownloadMigration.kt, navigation/AppController.kt, navigation/AnikutaRoot.kt",
  },
  {
    module: ":feature:download",
    role: "Compose UI for the Downloads page + settings + picker sheet",
    keyFiles:
      "DownloadsScreen.kt, DownloadedFilesScreen.kt, DownloadSettingsScreen.kt, DownloadVideoPickerSheet.kt, DownloadViewModel.kt, DownloadUiState.kt, DownloadsMoreEntries.kt, ExtensionSourceInfo.kt, components/{DragReorderableList,QueueRow,DownloadedAnimeCard,DownloadsEmptyState}.kt, di/DownloadModule.kt",
  },
  {
    module: ":feature:anime-details",
    role: "Per-episode download button + state UI on the details page",
    keyFiles:
      "EpisodeDownloadControl.kt, EpisodeDownloadState.kt, EpisodesSection.kt, AnimeDetailScreen.kt, DetailContent.kt",
  },
  {
    module: ":feature:watch",
    role: "Consumes downloaded video URI for offline playback",
    keyFiles:
      "WatchScreen.kt (no direct download dependency — receives a WatchRequest already short-circuited by AppController)",
  },
  {
    module: ":core:preferences",
    role: "Provides the reactive PreferenceStore / Preference<T> interface",
    keyFiles:
      "(used by DownloadStore, DownloadPreferences, ServerDiscoveryStore)",
  },
];

/* ---------------------------------------------------------------------------
 * Workflow: Click → Queue (from 01-workflow-click-to-queue.md)
 * ------------------------------------------------------------------------- */

export interface WorkflowStep {
  step: number;
  title: string;
  description: string;
  fileRef: string;
  codeSnippet?: string;
}

export const WORKFLOW_STEPS: WorkflowStep[] = [
  {
    step: 1,
    title: "User taps the download icon on an episode row",
    description:
      "EpisodeDownloadControl renders a state-driven IconButton(Icons.Filled.Download). The composable takes a sealed EpisodeDownloadState and re-renders for each phase (NotDownloaded / Resolving / Queued / Downloading / Paused / Error / Downloaded). The tap fires onDownload() up the EpisodesSection → DetailContent → AnimeDetailScreen chain.",
    fileRef:
      "feature/anime-details/.../EpisodeDownloadControl.kt:49-164 · EpisodesSection.kt:440-689",
    codeSnippet: `if (showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded) {
    EpisodeDownloadControl(
        state = downloadState,
        onDownload = onDownload,
        onCancel = onDownloadCancel,
        onResume = onDownloadResume,
        onRetry = onDownloadRetry,
        onDelete = onDownloadDelete,
    )
}`,
  },
  {
    step: 2,
    title: "AppController.downloadEpisode() — instant UI feedback",
    description:
      "The host builds a DownloadAnimeInfo(contentId, title, coverUrl), sets resolvingEpisodes[episode.url] = true (drives the Resolving spinner immediately), and launches downloadOrchestrator.enqueueDownload(...) on Dispatchers.IO. The old anilistId hard gate was removed — unlinked extension anime are now downloadable too.",
    fileRef: "app/.../navigation/AppController.kt:1046-1087",
    codeSnippet: `fun downloadEpisode(episode: SEpisode, source: AnimeSource,
                  watchCtx: WatchEpisodeContext, contentId: String) {
    val animeInfo = DownloadAnimeInfo(contentId, watchCtx.animeTitle, watchCtx.coverUrl)
    resolvingEpisodes[episode.url] = true   // immediate UI feedback
    scope.launch {
        try {
            val result = downloadOrchestrator.enqueueDownload(animeInfo, episode, source)
            when (result) {
                is EnqueueResult.Success -> Toast(ctx, "Download started")
                is EnqueueResult.ShowPicker -> downloadPickerTarget = result
                is EnqueueResult.NoSources -> Toast(ctx, "No video sources")
                is EnqueueResult.Error -> Toast(ctx, "Download failed: ${'$'}{result.message}")
            }
        } finally { resolvingEpisodes.remove(episode.url) }
    }
}`,
  },
  {
    step: 3,
    title: "DownloadOrchestrator — resolve + select best video",
    description:
      "Checks manager.isFolderReady() (no folder set = error). Calls resolver.resolve(source, episode) → ResolverResult.Success(servers). Passively records server names via ServerDiscoveryStore for later UI. If autoDownload is OFF → returns ShowPicker (the bottom sheet appears). If ON → selectBestVideo() applies user quality/audio/server preferences + fallback strategy (TRY_NEXT / ASK / DO_NOT_DOWNLOAD).",
    fileRef: "app/.../download/DownloadOrchestrator.kt:65-144, 211-311",
  },
  {
    step: 4,
    title: "buildRequest() — assemble DownloadRequest",
    description:
      "Wraps everything: anime + episode + videoUrl + videoHeaders + subtitleTracks + audioTracks + sourceId + videoServer + videoQuality + videoAudio. The resolver's ResolverVideo carries url, quality, videoHeaders (newline-separated 'Key: Value'), subtitleTracks and audioTracks (both share the Track shape).",
    fileRef: "DownloadOrchestrator.kt:336-360",
  },
  {
    step: 5,
    title: "DownloadManager.enqueueDownload() — validate + queue",
    description:
      "Validates videoUrl is non-blank + storage.isFolderReady(). Returns -1L on invalid request, otherwise delegates to queue.enqueue(request).",
    fileRef: "core/download/.../DefaultDownloadManager.kt:111-121",
    codeSnippet: `override suspend fun enqueueDownload(request: DownloadRequest): Long {
    if (request.videoUrl.isBlank()) { DownloadLogger.e("blank videoUrl"); return -1L }
    if (!storage.isFolderReady())    { DownloadLogger.e("no folder");    return -1L }
    return queue.enqueue(request)
}`,
  },
  {
    step: 6,
    title: "DownloadQueue.enqueue() — dedup + persist + tryStartNext",
    description:
      "Composite key is \"$contentId|$episodeNumber\" with 3-decimal format (e.g. \"al:154587|1.000\"). Dedup: if existing task is COMPLETED → return id, no re-download; QUEUED/DOWNLOADING/PAUSED → no-op; ERROR → resumeInternal() re-queues it. Creates a new DownloadTask(id=++idCounter, status=QUEUED), updates the StateFlow, calls persistNow(), then tryStartNext() which acquires a Semaphore permit (default 1, max 5).",
    fileRef: "core/download/.../DownloadQueue.kt:86-108, 309-310",
    codeSnippet: `private fun keyFor(request: DownloadRequest): String =
    "${'$'}{request.anime.contentId}|${'$'}{\"%.3f\".format(request.episode.episodeNumber)}"`,
  },
  {
    step: 7,
    title: "launchDownload() — DOWNLOADING state + progress callback",
    description:
      "Permit acquired inside scope.launch. Re-confirms status (defends against pause issued between tryStartNext and permit acquisition). Sets DOWNLOADING. Calls downloader.download(task) { downloaded, total -> DynamicProgressTracker.compute(...) → mutateTask(progress, downloadedBytes, totalBytes) }. persistThrottled() writes to store at most once per 1 second during progress ticks; persistNow() on state changes.",
    fileRef: "DownloadQueue.kt:190-271",
  },
  {
    step: 8,
    title: "HttpDownloader.download() — route + validate + publish",
    description:
      "VideoTypeDetector.detectFromUrl() → HLS? delegate to HlsDownloader. ADVANCED method? → AdvancedHttpDownloader (Range probe → N parallel chunks). Else → downloadNormal() single-threaded OkHttp stream. Validates: size ≥ 500 KB, magic-byte check (reject HTML/PNG/JPEG masquerading as video), HLS playlist re-detection. Downloads subtitles to cache (best-effort, parallel). Writes metadata.json. Finally storage.publishToUserFolder() copies validated temp → SAF folder. finally { tempCache.cleanupTask(task.id) } always runs.",
    fileRef: "core/download/.../HttpDownloader.kt (538 lines)",
  },
  {
    step: 9,
    title: "DownloadStorageProvider.publishToUserFolder() — atomic move to SAF",
    description:
      "ensureEpisodeDir() creates <root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/data/subtitles/. Copies video → Episode NNN/video.<ext>. Copies each subtitle → Episode NNN/data/subtitles/<lang>_<i>.<ext>. Copies metadata.json → Episode NNN/data/metadata.json. Returns PublishResult.Success(videoUri, subtitleUris, sizeBytes) — all are content:// URIs.",
    fileRef: "core/download/.../DownloadStorageProvider.kt:169-233",
  },
  {
    step: 10,
    title: "Completion — COMPLETED state, notification, auto-clear",
    description:
      "Queue sets status=COMPLETED, progress=100, videoUri set, persistNow(). onTaskCompleted callback fires notifier.notifyCompleted(task) — one-shot 'Download complete' notification with anime title + EP number. UI: episode row → Downloaded (green checkmark + delete). DownloadsScreen row appears in the queue section, then auto-clears after 10 seconds (file stays on disk — only the in-memory task is removed from the active list, per owner request).",
    fileRef: "DownloadViewModel.kt:51-68 · DownloadQueue.kt:230-235",
  },
];

/* ---------------------------------------------------------------------------
 * State Machine (from 03-state-machine.md)
 * ------------------------------------------------------------------------- */

export const STATE_MACHINE_DIAGRAM = `Queued ──start──▶ Downloading ──100%──▶ Completed
  │                  │
  │                  ├──pause──▶ Paused ──resume──▶ Queued
  │                  ├──error (retryable)──▶ Retrying ──backoff──▶ Downloading
  │                  │                            │
  │                  │                            ├──error (terminal)──▶ Error
  │                  │                            ├──pause──▶ Paused
  │                  │                            ├──cancel──▶ Cancelled (terminal)
  │                  │                            └──restart──▶ Queued (resetDownloadingToQueued)
  │                  ├──error (non-retryable)──▶ Error ──retry──▶ Queued
  │                  └──cancel──▶ Cancelled (terminal)
  └──cancel──▶ Cancelled (terminal)

                       ┌──────────────────────────────────┐
                       │                                  │
                       ▼                                  │
                    ┌───────┐  start (permit acquired) ┌──────────┐
       enqueue ───▶ │QUEUED│ ────────────────────────▶ │DOWNLOADING│
                    └───────┘                            └──────────┘
                       │   ▲                                 │
              pause    │   │ resume                          │
              (rare —  │   │ (also from ERROR + RETRYING)    │ 100%
               before  │   │                                 │
               permit) │   │                                 ▼
                       ▼   │                             ┌──────────┐
                    ┌───────┐                            │COMPLETED │
                    │PAUSED │ ◀──── pause                │(terminal)│
                    └───────┘       │                    └──────────┘
                       ▲            │
                       │            │ error, retryable (IOException / HTTP 5xx / 429)
                       │            ▼
                       │        ┌─────────┐  backoff (1s, 2s, 4s)   ┌──────────┐
                       │        │RETRYING │ ─────────────────────▶ │DOWNLOADING│
                       │        │ (M9)    │                         └──────────┘
                       │        └─────────┘
                       │            │  │  │
                       │            │  │  └──error (max attempts reached)──▶ ERROR
                       │            │  └──cancel──▶ CANCELLED (terminal)
                       │            └──pause──▶ PAUSED
                       │
                       │            error (non-retryable: HTTP 4xx, encrypted HLS, unknown)
                       │            ▼
                       │        ┌───────┐
                       └────────│ ERROR │  ◀── retry (manual)
                          retry └───────┘
                                │
                                │ cancel (any non-terminal state)
                                ▼
                            ┌──────────┐
                            │CANCELLED │  (in practice: removed from
                            │(terminal)│   list entirely, never
                            └──────────┘    persisted as CANCELLED)`;

export interface StateInfo {
  name: string;
  meaning: string;
  terminal: boolean;
  color: string;
}

export const STATE_MACHINE_STATES: StateInfo[] = [
  {
    name: "QUEUED",
    meaning: "In the queue, waiting for a download slot (Semaphore permit).",
    terminal: false,
    color: "var(--c-warning)",
  },
  {
    name: "DOWNLOADING",
    meaning: "Actively downloading — DownloadTask.progress is updating.",
    terminal: false,
    color: "var(--c-primary)",
  },
  {
    name: "RETRYING",
    meaning:
      "NEW (M9) — a retryable error fired (IOException / HTTP 5xx / 429 / proxy-churn). The engine backs off (1s, 2s, 4s exponential) then re-attempts. Retry metadata (attempt / maxAttempts / lastError) lives on DownloadTask (the enum constant can't carry per-instance data). UI shows 'Retrying (2/3)…' pill.",
    terminal: false,
    color: "var(--c-secondary)",
  },
  {
    name: "PAUSED",
    meaning: "User-paused; stays in the queue, can be resumed.",
    terminal: false,
    color: "var(--c-secondary)",
  },
  {
    name: "COMPLETED",
    meaning: "Finished — file + all subtitles are on disk. Terminal.",
    terminal: true,
    color: "var(--c-success)",
  },
  {
    name: "ERROR",
    meaning: "Failed (network / IO / validation / max retries exceeded). Recoverable via retry.",
    terminal: false,
    color: "var(--c-danger)",
  },
  {
    name: "CANCELLED",
    meaning:
      "User-cancelled + file deleted. Terminal. In practice never persisted — the task is removed from the list entirely.",
    terminal: true,
    color: "var(--c-danger)",
  },
];

export interface StateTransition {
  from: string;
  action: string;
  to: string;
  enforcedBy: string;
}

export const STATE_MACHINE_TRANSITIONS: StateTransition[] = [
  {
    from: "(none)",
    action: "enqueue",
    to: "QUEUED",
    enforcedBy: "DownloadQueue.enqueue",
  },
  {
    from: "QUEUED",
    action: "permit acquired",
    to: "DOWNLOADING",
    enforcedBy: "DownloadQueue.launchDownload (line 197-199)",
  },
  {
    from: "DOWNLOADING",
    action: "100%",
    to: "COMPLETED",
    enforcedBy: "HttpDownloader.download returns completed task → mutateTask",
  },
  {
    from: "DOWNLOADING",
    action: "pause",
    to: "PAUSED",
    enforcedBy: "DownloadQueue.pause",
  },
  {
    from: "QUEUED",
    action: "pause",
    to: "PAUSED",
    enforcedBy: "DownloadQueue.pause (also accepts QUEUED)",
  },
  {
    from: "DOWNLOADING",
    action: "retryable error (IOException / HTTP 5xx / 429)",
    to: "RETRYING (M9)",
    enforcedBy: "DownloadQueue.launchDownload catch → setRetryingStatus + delay(backoff)",
  },
  {
    from: "RETRYING",
    action: "backoff elapsed",
    to: "DOWNLOADING (re-attempt)",
    enforcedBy: "DownloadQueue.launchDownload retry loop",
  },
  {
    from: "RETRYING",
    action: "max attempts reached",
    to: "ERROR",
    enforcedBy: "DownloadQueue.launchDownload — setErrorStatus when attempt >= policy.maxAttempts",
  },
  {
    from: "RETRYING",
    action: "pause",
    to: "PAUSED (M10 — pauseInternal accepts RETRYING)",
    enforcedBy: "DownloadQueue.pauseInternal",
  },
  {
    from: "RETRYING",
    action: "cancel",
    to: "CANCELLED (terminal) — cancels the retry loop's delay",
    enforcedBy: "DownloadQueue.cancel (accepts RETRYING)",
  },
  {
    from: "RETRYING",
    action: "app restart",
    to: "QUEUED (resetDownloadingToQueued — M6, WHERE state IN ('DOWNLOADING', 'RETRYING'))",
    enforcedBy: "DownloadQueue startup migration",
  },
  {
    from: "PAUSED",
    action: "resume",
    to: "QUEUED",
    enforcedBy: "DownloadQueue.resumeInternal",
  },
  {
    from: "ERROR",
    action: "resume",
    to: "QUEUED",
    enforcedBy: "DownloadQueue.resumeInternal (accepts ERROR too)",
  },
  {
    from: "ERROR",
    action: "retry (manual)",
    to: "QUEUED (progress=0)",
    enforcedBy: "DownloadQueue.retry",
  },
  {
    from: "DOWNLOADING",
    action: "non-retryable error (HTTP 4xx, encrypted HLS, unknown)",
    to: "ERROR",
    enforcedBy: "DownloadQueue.launchDownload catch blocks",
  },
  {
    from: "QUEUED / DOWNLOADING / PAUSED / ERROR / RETRYING",
    action: "cancel",
    to: "(removed from list)",
    enforcedBy: "DownloadQueue.cancel",
  },
  {
    from: "COMPLETED",
    action: "removeFromQueue",
    to: "(removed from list, file stays)",
    enforcedBy: "DownloadQueue.removeCompleted",
  },
  {
    from: "COMPLETED",
    action: "deleteDownload",
    to: "(removed + file deleted)",
    enforcedBy: "DefaultDownloadManager.deleteDownload",
  },
  {
    from: "ERROR",
    action: "enqueue same episode",
    to: "QUEUED (via resumeInternal)",
    enforcedBy: "DownloadQueue.enqueue (line 91-94)",
  },
];

export const STATE_DISALLOWED_NOTE =
  "Disallowed (silently no-op): pause on PAUSED / ERROR / COMPLETED — only accepts DOWNLOADING + QUEUED + RETRYING. retry on non-ERROR. resume on QUEUED / DOWNLOADING / COMPLETED — only accepts PAUSED + ERROR + RETRYING. Bulk 'Retry all' skips RETRYING tasks (already being retried by the engine — M14).";

/* ---------------------------------------------------------------------------
 * Storage Paths (from 04-storage-paths.md) — CRITICAL
 * ------------------------------------------------------------------------- */

export const STORAGE_TREE = `AniKuta Downloads/                              ← user-selected root (SAF tree URI)
├── video/                                        ← content FORMAT folder (video files)
│   ├── Jujutsu Kaisen/                           ← content folder (human-readable title — NO AniList ID)
│   │   ├── data.json                             ← per-content metadata (the SOURCE OF TRUTH)
│   │   ├── .nomedia                              ← prevents gallery pollution (M54)
│   │   ├── cover.jpg                             ← cached cover image (optional)
│   │   ├── Jujutsu Kaisen - E00001.mp4           ← 5-digit padding, no AniList ID
│   │   ├── Jujutsu Kaisen - E00001.English.0.srt ← subtitle (best-effort)
│   │   ├── Jujutsu Kaisen - E00002.mp4
│   │   ├── Jujutsu Kaisen - E00002.srt
│   │   └── Jujutsu Kaisen - E00012.5.mp4         ← .5 specials keep their fractional suffix (M56)
│   ├── Frieren Beyond Journey's End/             ← sanitized: ":" → space, runs collapsed
│   │   ├── data.json
│   │   ├── .nomedia
│   │   └── Frieren Beyond Journey's End - E00001.mkv   ← extension preserved (mp4/mkv/webm/ts/m4v)
│   └── The Lord of the Rings - Fellowship/       ← a MOVIE (single-file content → video/)
│       ├── data.json
│       └── The Lord of the Rings - Fellowship.mp4      ← no episode number for single-file content
├── images/                                       ← content FORMAT folder (image files)
│   ├── Berserk Manga Volume 01/                  ← manga volume (future)
│   │   ├── data.json
│   │   └── 001.png ... NNN.png
│   └── Pixel Art Collection/                     ← art book (future)
│       ├── data.json
│       └── ...
├── text/                                         ← content FORMAT folder (text files)
│   └── Spice and Wolf - Volume 01/               ← light novel (future)
│       ├── data.json
│       └── Spice and Wolf - Volume 01.epub
└── .anikuta/                                     ← app-managed metadata (hidden, app-owned)
    ├── library_index.json                        ← optional aggregate index (cache only)
    └── scan_state.json                           ← last scan timestamp + hash

# Concrete SAF example:
content://com.android.externalstorage.documents/tree/primary%3AAniKuta%20Downloads/
└── video/
    └── Jujutsu Kaisen/
        ├── data.json
        ├── .nomedia
        ├── Jujutsu Kaisen - E00001.mp4
        └── Jujutsu Kaisen - E00001.English.0.srt

# Why 5-digit padding (E00001, not E001):
#   The old project's 3-digit "Episode 001" padding collides past episode 999. Long-running
#   shounen (One Piece = 1100+ episodes), daily soaps, podcast back-catalogues all need 5-digit.
#   5-digit supports 10,000+ episodes (matches One Piece 01085 = "One Piece - E01085.mp4").`;

export const STORAGE_TEMP_CACHE = `# Temp cache (internal, NOT the user's SAF folder) — internal-cache-first pipeline:
<cacheDir>/anikuta_downloads/
└── <downloadId>/                              ← per-task dir (downloadId == download_queue.id)
    ├── video.<ext>                            ← temp video file (deleted on completion/failure)
    ├── subtitles/                             ← temp subtitle files (downloaded separately)
    │   └── English_0.srt
    ├── cover.jpg                              ← temp cover (downloaded from coverUrl)
    ├── data.json                              ← temp data.json (built incrementally, atomically swapped on success)
    ├── resume.json                            ← (only for Advanced method — chunk progress)
    ├── chunk_0.part                           ← (only for Advanced method)
    ├── chunk_1.part
    └── ...

# TempDownloadCache.hasSpaceFor(totalBytes) is called by tryStartNext BEFORE starting a
# download (M59) — checks cacheDir.usableSpace against the task's totalBytes (or 4GB if
# unknown). Returns false (with a logged reason) if the cache would overflow.

# cleanupStale() runs once at app startup (from DownloadModule.kt's single { TempDownloadCache(...).also { it.cleanupStale() } }).
# Any temp dir present at startup is from a crashed/interrupted download — safe to delete
# (the user's SAF folder has nothing partial).`;

export const STORAGE_DATA_JSON_EXAMPLE = `{
  "schemaVersion": 1,
  "mainId": "550e8400-e29b-41d4-a716-446655440000",
  "contentId": "anilist:aniyomi:https://example.com/index.min.json:com.confused.ext.aniyomi:69023:https://aniyomi.org/anime/jujutsu-kaisen",
  "title": "Jujutsu Kaisen",
  "contentType": "anime",
  "contentFormat": "video",
  "sourceType": "extension",
  "coverUrl": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101522-h5FtnOPkDPkF.png",
  "coverColor": 4280392219,
  "anilistId": 101522,
  "sourceId": 69023,
  "animeUrl": "https://aniyomi.org/anime/jujutsu-kaisen",
  "dataSourceId": 1,
  "systemId": "aniyomi",
  "extensionRepoId": "https://example.com/index.min.json",
  "extensionId": "com.confused.ext.aniyomi",
  "displaySource": "extension",
  "episodes": [
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00001",
      "episodeNumber": 1.0,
      "episodeName": "Ryomen Sukuna",
      "videoFileName": "Jujutsu Kaisen - E00001.mp4",
      "subtitleFileNames": ["Jujutsu Kaisen - E00001.English.0.srt"],
      "quality": "1080p",
      "server": "Vidstreaming",
      "audio": "SUB",
      "sizeBytes": 423456789,
      "downloadedAt": 1720000000000
    },
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00002",
      "episodeNumber": 2.0,
      "episodeName": "For Myself",
      "videoFileName": "Jujutsu Kaisen - E00002.mp4",
      "subtitleFileNames": [],
      "quality": "1080p",
      "server": "Vidstreaming",
      "audio": "SUB",
      "sizeBytes": 410000000,
      "downloadedAt": 1720100000000
    }
  ],
  "createdAt": 1720000000000,
  "updatedAt": 1720100000000
}

# Why data.json is the SOURCE OF TRUTH (not the DB):
#   - Survives app uninstall ✓       (in the user's SAF folder)
#   - Survives app-delete + reinstall ✓
#   - Survives "Clear data" button ✓
#   - The DB is a CACHE/INDEX — on startup, the scan walks the SAF folder, reads every
#     data.json, and UPSERTs the content by mainId.

# The contentId (M4) is a 6-section colon-delimited string:
#   {dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}
#   Example: anilist:aniyomi:https://example.com/index.min.json:com.confused.ext.aniyomi:69023:https://aniyomi.org/anime/jujutsu-kaisen
#   The OLD draft's "anilist:101522" (2-section) would have broken the
#   idx_content_content_id duplicate-detection index.

# ContentDataJson (M5) now stores the FULL FK set (dataSourceId / systemId /
# extensionRepoId / extensionId / displaySource) so the scan's upsertFromDataJson is lossless.`;

export interface StorageNamingRule {
  kind: string;
  pattern: string;
  examples: string;
  notes?: string;
}

export const STORAGE_NAMING_RULES: StorageNamingRule[] = [
  {
    kind: "Format folder (top-level)",
    pattern: "video/ · images/ · text/ · audio/ (future)",
    examples:
      'anime + movies → video/ · manga + art books → images/ · light novels → text/ · audio dramas (future) → audio/',
    notes:
      "REWRITE — folders are named after how content is ENCODED ON DISK, not what kind of work it is. Anime episodes + movies both .mp4 → both live under video/. Survives adding manga/novels/movies/series later without restructuring.",
  },
  {
    kind: "Content folder",
    pattern: "<sanitized-title> (NO mainId suffix, NO AniList ID)",
    examples:
      '"Jujutsu Kaisen" → "Jujutsu Kaisen" · "Frieren: Beyond Journey\'s End" → "Frieren Beyond Journey\'s End" (":" → space, runs collapsed) · "Re:Zero kara..." → "Re Zero kara..."',
    notes:
      "REWRITE — the OLD project appended [al-101522] to the folder name (leaked AniList IDs, ugly, broke if user unlinked from AniList). The mainId lives in data.json. sanitizeFileName also replaces Windows reserved names (CON/PRN/AUX/NUL/COM1-9/LPT1-9) + caps at ~200 chars (M53 + R1-M3).",
  },
  {
    kind: "Episode file name",
    pattern: "<sanitized-title> - E<NNNNN>.<ext> (5-digit zero-padded)",
    examples:
      'Jujutsu Kaisen, EP 1.0f, mp4 → "Jujutsu Kaisen - E00001.mp4" · EP 12.5f → "Jujutsu Kaisen - E00012.5.mp4" (specials keep fractional — M56) · One Piece EP 1085 → "One Piece - E01085.mp4" · Spirited Away (movie) → "Spirited Away.mp4" (single-file content drops the - E00001)',
    notes:
      "REWRITE — the OLD project used 3-digit padding (collides past EP 999) + a per-episode sub-folder (Episode NNN/video.mp4 — wasted inode + extra tap in file managers). NEW: 5-digit + flat files directly in the content folder. formatEpisodeNumber uses a NON-rounding formatter (M56): 12.25 → E00012.25, not E00012.3.",
  },
  {
    kind: "Subtitle file name",
    pattern: "<sanitized-title> - E<NNNNN>.<safeLang>.<index>.<ext>",
    examples:
      'Jujutsu Kaisen - E00001.English.0.srt · Jujutsu Kaisen - E00001.Spanish.1.ass — whitelist ass/srt/vtt/ssa/sub, default srt',
    notes:
      "Subtitles sit next to the video file (same folder). MPV auto-discovers external subs by filename proximity. safeLang replaces non-alphanumerics with spaces, defaulted to \"track\" if blank.",
  },
  {
    kind: "data.json",
    pattern: "<content-folder>/data.json (ONE per content, NOT per episode)",
    examples:
      'video/Jujutsu Kaisen/data.json — schemaVersioned ContentDataJson. Carries mainId + 6-section contentId + full FK set (M5) + episodes[] array.',
    notes:
      "REWRITE — the OLD project wrote a metadata.json PER EPISODE folder (Episode NNN/data/metadata.json). NEW: ONE data.json per CONTENT with the episodes[] array. Reading 100 episodes' metadata = 1 file read (was 100). This is the durable source of truth — survives app uninstall (it's in the user's SAF folder). Scan-on-startup reads every data.json + UPSERTs the content by mainId.",
  },
  {
    kind: ".nomedia",
    pattern: "<content-folder>/.nomedia (created ONCE per content, idempotent — M54)",
    examples:
      "video/Jujutsu Kaisen/.nomedia — empty file. Prevents downloaded .mp4 files from appearing in gallery apps (Google Photos, OEM galleries).",
    notes:
      "NEW (M54) — the OLD project didn't have .nomedia, so downloaded anime episodes polluted the user's gallery. Created in publishToUserFolder step 6 — `if (contentDir.findFile(\".nomedia\") == null) contentDir.createFile(...)`.",
  },
  {
    kind: "Cover image",
    pattern: "<content-folder>/cover.jpg",
    examples:
      "video/Jujutsu Kaisen/cover.jpg — cached from coverUrl at download time (best-effort). If the network call fails, no cover file is written + the UI uses the placeholder.",
    notes:
      "One cover.jpg per content folder, used by the notification thumbnail + the Downloads UI.",
  },
];

export interface StorageDecision {
  title: string;
  recommendation: string;
  rationale: string;
  color: string;
}

export const STORAGE_DECISIONS: StorageDecision[] = [
  {
    title: "SAF (DocumentFile) for the user folder — never java.io.File",
    recommendation: "User picks a single folder via ActivityResultContracts.OpenDocumentTree + takePersistableUriPermission. The app creates + owns its own structure inside (video/ / images/ / text/ / .anikuta/).",
    rationale:
      "User picks any folder (internal storage, SD card, Google Drive). Persistable URI permission survives app restarts AND device reboots. The URI string is stored in SharedPreferences under pref_dl_folder_uri. rootTree() returns null if: no folder set, URI parse fails, OR write permission was revoked.",
    color: "var(--c-primary)",
  },
  {
    title: "Content FORMAT folders, not content TYPE folders (NEW — REWRITE)",
    recommendation:
      "Top-level folders named after how content is ENCODED on disk: video/, images/, text/, audio/ (future). NOT anime/, manga/, novel/, movie/.",
    rationale:
      "Anime episodes + movies are both .mp4/.mkv → both live under video/. Manga volumes (PNG/JPG) → images/. Light novels (EPUB/TXT) → text/. Art books → images/. This survives adding manga/novels/movies/series/audio-dramas later without restructuring. The OLD project's hardcoded anime/ folder doesn't scale.",
    color: "var(--c-success)",
  },
  {
    title: "Internal-cache-first pipeline — temp → validate → atomic publish",
    recommendation:
      "Temp downloads go to <cacheDir>/anikuta_downloads/<downloadId>/. Only after validation (size ≥ 500KB + magic-byte check) is the file copied to the SAF folder.",
    rationale:
      "1) No pollution — partial/corrupt downloads never appear in the user's folder. 2) Performance — writing to internal cache is faster than SAF per-byte writes (no ContentResolver round-trips). 3) Validation — can inspect bytes BEFORE committing. 4) Atomicity — the user's folder only ever contains complete, valid files. cleanupTask(downloadId) always runs in HttpDownloader's finally block; cleanupStale() runs once at app startup. hasSpaceFor(totalBytes) (M59) is checked by tryStartNext before starting a download — refuses if cacheDir.usableSpace < totalBytes (or 4GB if unknown).",
    color: "var(--c-success)",
  },
  {
    title: "mainId is the stable identifier — NO AniList ID in the folder name (NEW)",
    recommendation:
      "Each content folder contains a data.json with a mainId (UUID) + a 6-section contentId. The folder name is the human-readable title (sanitized). NO [al-101522] suffix.",
    rationale:
      "The mainId is the stable UUID — survives source switches + AniList unlinking. The contentId is the 6-section colon-delimited string produced by ContentIdGenerator (anilist:aniyomi:repoUrl:extPkg:sourceId:animeUrl) — changes when sources switch. The OLD project's [al-101522] suffix leaked AniList IDs into the user's folder + broke if the user unlinked from AniList. M53: same-title collision algorithm appends ' (2)', ' (3)' to the folder name if the mainIds differ.",
    color: "var(--c-secondary)",
  },
  {
    title: "Scan-on-startup — data.json is the durable source of truth (NEW)",
    recommendation:
      "On app start (after the user re-selects the same folder), the DownloadScanner walks video/ / images/ / text/ / audio/, reads each data.json, and UPSERTs the content into the DB by mainId.",
    rationale:
      "The DB is a CACHE/INDEX. The data.json files are DURABLE (in the user's SAF folder — survive app uninstall + Clear data + reinstall). The scan uses listFiles() ONCE per content folder + builds a Map<String, DocumentFile> index (M55 — avoids O(N) findFile() per episode = 40,000 ops for 200 contents × 200 episodes). Falls back to 'always scan' if DocumentFile.lastModified() returns 0 or a sentinel (M58 — unreliable on many SAF providers).",
    color: "var(--c-warning)",
  },
  {
    title: "5-digit episode padding (E00001, not E001) — NEW",
    recommendation:
      "Episode files use formatEpisodeNumber: %05d (5-digit zero-padded). Specials keep their fractional suffix (12.5f → E00012.5 — non-rounding formatter M56).",
    rationale:
      "The OLD project's 3-digit padding collides past episode 999. Long-running shounen (One Piece = 1100+ episodes), daily soaps, podcast back-catalogues all need 5-digit. The OLD project's %.1f formatter rounded 12.25 → 12.3 (REAL fractional episodes lost). M56: non-rounding formatter (fractional.toString().removePrefix('0.').trimEnd('0')).",
    color: "var(--c-warning)",
  },
  {
    title: ".nomedia in every content folder (NEW — M54)",
    recommendation:
      "publishToUserFolder creates a .nomedia file (empty) in each content folder, idempotent — `if (contentDir.findFile(\".nomedia\") == null) contentDir.createFile(...)`.",
    rationale:
      "Prevents downloaded .mp4 files from appearing in gallery apps (Google Photos, OEM galleries on Samsung/Xiaomi). The OLD project didn't have .nomedia — downloaded anime episodes polluted the user's gallery.",
    color: "var(--c-danger)",
  },
  {
    title: "No FileProvider for video playback — MPV plays SAF content:// directly",
    recommendation:
      "DownloadStorageProvider.publishToUserFolder returns content:// URIs. The WatchScreen hands them to MPV via resolveUrlForMpv which opens via ContentResolver → ParcelFileDescriptor → fd://<fd_number>.",
    rationale:
      "The FileProvider declared in AndroidManifest.xml is configured for cache-path only (used by the app update installer to share APK files). Downloads don't need it. MPV can read content:// URIs through ContentResolver.openFileDescriptor.",
    color: "var(--c-warning)",
  },
];

export const FILE_PROVIDER_CONFIG = `<!-- app/src/main/res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="updates" path="updates/" />
    <cache-path name="cache" path="." />
</paths>

<!-- AndroidManifest.xml — the provider is wired but only for cache-path (APK install). -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${'$'}{applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>`;

/* ---------------------------------------------------------------------------
 * Download Engines — HTTP vs HLS vs Advanced (from 05-downloaders.md)
 * ------------------------------------------------------------------------- */

export interface DownloaderInfo {
  name: string;
  badge: string;
  badgeColor: string;
  supports: string;
  pipeline: string;
  honestNotes: string[];
}

export const DOWNLOADERS: DownloaderInfo[] = [
  {
    name: "HttpDownloader (single-threaded)",
    badge: "NORMAL",
    badgeColor: "var(--c-primary)",
    supports: "Direct MP4 / MKV / WebM / TS / MOV / AVI — OkHttp GET → stream to file",
    pipeline:
      "8 KB buffer · ensureActive() per read (cooperative cancellation) · no Range request · no resume. Routes via VideoTypeDetector.detectFromUrl → HLS path / Advanced path / downloadNormal(). HLS playlist re-detection: if downloaded file < 500KB AND starts with #EXTM3U, re-download via HlsDownloader with .ts extension.",
    honestNotes: [
      "Header string parsed from newline-separated 'Key: Value' (passed from the resolver).",
      "total = -1 if no Content-Length (chunked encoding) — DynamicProgressTracker handles this case.",
      "The temp file is overwritten on each call (FileOutputStream doesn't append by default).",
    ],
  },
  {
    name: "HlsDownloader (segment concatenator)",
    badge: "HLS",
    badgeColor: "var(--c-secondary)",
    supports: ".m3u8 master + media playlists — pure Kotlin, NO ffmpeg",
    pipeline:
      "1. Fetch playlist text → 2. Master playlist? pickFirstVariant (FIRST variant = typically highest bandwidth, no quality picker) → 3. isEncrypted? reject with clear error (no DRM/AES-128) → 4. parseInitSegment (#EXT-X-MAP) + parseSegments (#EXTINF) → 5. download + concatenate segments into a .ts file → onProgress(tempFile.length(), -1L) after each segment (total unknown for HLS).",
    honestNotes: [
      "PNG-header stripping (stripPngHeader) — some CDNs (megaplay.buzz, kotocdn.site) prepend a PNG image header to each HLS segment to prevent direct downloading. The downloader mirrors the extension's LocalProxyServer behavior.",
      "Always picks the FIRST variant in a master playlist (no quality picker).",
      "No retry on segment failure — one failed segment fails the whole download.",
      "Discontinuities (#EXT-X-DISCONTINUITY) + ad breaks may produce minor glitches but are still playable.",
      ".m4s (fMP4) concatenation works for the common case; edge cases may be glitchy.",
    ],
  },
  {
    name: "AdvancedHttpDownloader (multi-threaded Range + resume)",
    badge: "ADVANCED",
    badgeColor: "var(--c-warning)",
    supports: "Direct videos on Range-supporting servers — N parallel chunks + per-chunk .part files + resume metadata",
    pipeline:
      "1. HEAD probe (GET with Range: bytes=0-0 — many servers reject HEAD) → 206 = Range supported, total from Content-Range; 200 = Range NOT supported, total from Content-Length → 2. Decide single vs multi-threaded (!supportsRange || totalBytes < minSizeBytes || threadCount == 1) → 3. Multi-threaded: split into N chunks, check resume.json, launch N coroutines on Dispatchers.IO each writing to chunk_<i>.part via RandomAccessFile → 4. Per-chunk retry (up to 25 attempts, 1s delay) → 5. On success: concatenate chunks into tempVideoFile, clearResume + delete .part files → 6. On CancellationException: save resume metadata before throwing.",
    honestNotes: [
      "Default advancedMaxRetries is 25 in code but UI slider clamps to 0..10 — INCONSISTENCY (D.6 polish task).",
      "Peak temp usage on a 4-chunk 100MB download: 100MB (chunks) + 100MB (concatenated output) = 200MB. Could be an issue on low-storage devices.",
      "concatenateChunks is sequential (no parallel I/O) — noticeable on 8 chunks of a 1 GB file.",
      "If the URL changes between resume attempts (token expired), the resume metadata is discarded — download restarts from scratch.",
      "RandomAccessFile.seek(chunk.downloaded) positions the write at the resume point — but if the chunk file was corrupted (crash mid-write), the validation only checks size, not content.",
    ],
  },
];

export interface ProgressTrackerInfo {
  problem: string;
  algorithm: string;
  constants: string;
}

export const DYNAMIC_PROGRESS_TRACKER: ProgressTrackerInfo = {
  problem:
    "Many CDNs don't send Content-Length (chunked transfer) → total = -1 → stuck progress bar. Some servers change Content-Length mid-download → progress bar would jump backward. The progress bar should NEVER show 100% until the download is verified complete.",
  algorithm:
    "Case 1 (total known + stable + valid, ≥ 1 MB): effectiveTotal = maxOf(reportedTotal, previousTotal) — bar never goes backward. ratio = (downloaded / effectiveTotal).coerceIn(0, 1). progress = (ratio * 90).coerceIn(0, 90) — capped at 90%.\nCase 2 (total unknown or too small to be real): estimate = maxOf(previousEstimate, downloaded + 10MB) — bar keeps advancing. ratio = (downloaded / estimate).coerceIn(0, 0.9). progress = (ratio * 90).coerceIn(0, 90).\nSanity check: if reportedTotal is 1..1MB but downloaded > reportedTotal, treat as unknown (-1L).",
  constants:
    "MAX_INCOMPLETE_PROGRESS = 90 (bar caps at 90% during download) · INITIAL_ESTIMATE_BYTES = 10MB · MIN_VALID_TOTAL_BYTES = 1MB · aheadBytes = 10MB (changed from 50MB per owner request 2026-07-29 — 50MB was too slow for typical 30-80 MB episodes).",
};

/* ---------------------------------------------------------------------------
 * Queue Management (from 02-queue-management.md)
 * ------------------------------------------------------------------------- */

export interface QueueLogicItem {
  title: string;
  body: string;
  color: string;
}

export const QUEUE_LOGIC: QueueLogicItem[] = [
  {
    title: "Concurrency — Semaphore-based, default 1, max 5",
    body: "A Semaphore(currentConcurrentLimit()) gates downloads. Default = 1 download at a time. UI clamps to 1..5. The semaphore is rebuilt when the pref changes (refreshConcurrency) — but rebuilding doesn't carry over already-acquired permits. CAVEAT: setting the pref in the settings screen does NOT automatically call refreshConcurrency — only takes effect on app restart or next task completion. (D.6 polish task: add a Flow collector in DownloadQueue.init that calls refreshConcurrency() on pref changes.)",
    color: "var(--c-primary)",
  },
  {
    title: "FIFO ordering — implicit, no user reordering",
    body: "New tasks are appended to the end of _tasks.value. tryStartNext picks the first QUEUED task (firstOrNull). There is NO user reordering of the download queue in the old project. The DragReorderableList composable is used ONLY for preference list reordering (quality / audio / server priorities) in DownloadSettingsScreen — NOT for reordering the queue. (New project may add a 'displayOrder' column + reorder method if queue reordering is desired — see design decision D-NEW.)",
    color: "var(--c-secondary)",
  },
  {
    title: "tryStartNext() — the scheduler",
    body: "Called after every state change (enqueue, pause, resume, cancel, retry, refreshConcurrency, job completion). Picks the FIRST QUEUED task in list order. Skips if connectivityCheck() fails (Wi-Fi-only pref enforced — isNetworkAllowed checks ConnectivityManager + NetworkCapabilities.TRANSPORT_WIFI if wifiOnly is on; fails open on error). Skips if jobs.containsKey(next.id) (already launching). Cheap if nothing to start.",
    color: "var(--c-success)",
  },
  {
    title: "Persistence — JSON blob, throttled writes",
    body: "Single MutableStateFlow<List<DownloadTask>> holds ALL tasks (queued + downloading + paused + errored + completed). Writes the entire list as JSON to SharedPreferences via store.setAll(_tasks.value). persistThrottled() at most once per 1 second (progress ticks); persistNow() on every state CHANGE. On startup: store.purgeCancelled() filters out any CANCELLED tasks (defensive — cancelled tasks aren't supposed to be persisted).",
    color: "var(--c-warning)",
  },
  {
    title: "Dedup — composite key",
    body: 'Composite key is "$contentId|$episodeNumber" (3-decimal format, e.g. "al:154587|1.000"). On enqueue, if a task with the same key exists: COMPLETED → return id, no re-download; QUEUED/DOWNLOADING/PAUSED → no-op; ERROR → resumeInternal() re-queues it.',
    color: "var(--c-danger)",
  },
  {
    title: "Threading — best-effort, NOT strictly correct",
    body: "All queue mutations happen on Dispatchers.IO via scope.launch blocks. mutateTask reads _tasks.value (atomic) + writes back via _tasks.value = newList (atomic) — but NOT in a Mutex. POTENTIAL RACE: two concurrent mutateTask calls on DIFFERENT task IDs could race if they read the same snapshot. Works in practice because the UI rarely fires multiple actions simultaneously. D.6 polish: harden with Mutex or Dispatchers.Main.",
    color: "var(--c-primary)",
  },
];

/* ---------------------------------------------------------------------------
 * Settings — all 15 (from 07-settings-preferences.md)
 * ------------------------------------------------------------------------- */

export interface SettingEntry {
  group: string;
  key: string;
  type: string;
  default: string;
  uiLabel: string;
  description?: string;
}

export const SETTINGS: SettingEntry[] = [
  // General (5)
  {
    group: "General",
    key: "pref_dl_folder_uri",
    type: "String",
    default: '""',
    uiLabel: "Download folder",
    description: "SAF tree URI — picked via ActivityResultContracts.OpenDocumentTree.",
  },
  {
    group: "General",
    key: "pref_dl_method",
    type: "Enum DownloadMethod",
    default: "ADVANCED",
    uiLabel: "Download method",
    description: "NORMAL (single-threaded OkHttp, no resume) or ADVANCED (multi-threaded Range + resume).",
  },
  {
    group: "General",
    key: "pref_dl_wifi_only",
    type: "Boolean",
    default: "true",
    uiLabel: "Wi-Fi only",
    description: "Pause downloads on mobile data.",
  },
  {
    group: "General",
    key: "pref_dl_concurrent",
    type: "Int",
    default: "1 (UI clamps 1..5)",
    uiLabel: "Concurrent downloads",
    description: "Slider 1..5. BUG: setting this pref does NOT call DownloadQueue.refreshConcurrency() — takes effect on app restart or next task completion.",
  },
  {
    group: "General",
    key: "pref_dl_show_button",
    type: "Boolean",
    default: "true",
    uiLabel: "Show download button",
    description: "Display the download icon on episode rows.",
  },
  // Auto-download (1)
  {
    group: "Auto-download",
    key: "pref_dl_auto_pick",
    type: "Boolean",
    default: "false",
    uiLabel: "Automatic video selection",
    description: "Auto-select your preferences. If OFF, always show the picker. If ON, selectBestVideo applies preference lists + fallback strategies.",
  },
  // Preference lists (3)
  {
    group: "Preference lists (auto-pick ON)",
    key: "pref_dl_quality_prefs",
    type: "List<String>",
    default: '["1080p", "720p", "480p", "360p"]',
    uiLabel: "Preferred quality — drag to re-order",
    description: "Reordered via DragReorderableList.",
  },
  {
    group: "Preference lists (auto-pick ON)",
    key: "pref_dl_audio_prefs",
    type: "List<String>",
    default: '["SUB", "DUB"]',
    uiLabel: "Preferred audio — drag to re-order",
  },
  {
    group: "Preference lists (auto-pick ON)",
    key: "pref_dl_server_prefs",
    type: "Map<String, List<String>>",
    default: "{} (empty)",
    uiLabel: "Preferred server — per extension",
    description: "Per-sourceId mapping of server name orders. Merged with discovered servers from ServerDiscoveryStore.",
  },
  // Fallback strategies (3)
  {
    group: "Fallback strategies (auto-pick ON)",
    key: "pref_dl_quality_fallback",
    type: "Enum FallbackStrategy",
    default: "TRY_NEXT",
    uiLabel: "If unavailable (quality)",
    description: "TRY_NEXT (try next option) / ASK (show picker sheet) / DO_NOT_DOWNLOAD (error).",
  },
  {
    group: "Fallback strategies (auto-pick ON)",
    key: "pref_dl_audio_fallback",
    type: "Enum FallbackStrategy",
    default: "TRY_NEXT",
    uiLabel: "If unavailable (audio)",
  },
  {
    group: "Fallback strategies (auto-pick ON)",
    key: "pref_dl_server_fallback",
    type: "Enum FallbackStrategy",
    default: "TRY_NEXT",
    uiLabel: "If unavailable (server)",
  },
  // Advanced method settings (3)
  {
    group: "Advanced method settings",
    key: "pref_dl_adv_threads",
    type: "Int",
    default: "8 (UI 1..8)",
    uiLabel: "Parallel threads",
    description: "Number of parallel chunks for Range multi-threading.",
  },
  {
    group: "Advanced method settings",
    key: "pref_dl_adv_retries",
    type: "Int",
    default: "25 ⚠️ (UI clamps 0..10)",
    uiLabel: "Max retries per chunk",
    description: "INCONSISTENCY: code default is 25 but UI slider max is 10. A user who never opens settings gets 25 retries per chunk — could mean very long waits on flaky servers. (D.6 polish: set both to 10.)",
  },
  {
    group: "Advanced method settings",
    key: "pref_dl_adv_min_size_mb",
    type: "Int",
    default: "1 (MB) (UI 1..20)",
    uiLabel: "Min size for multi-threading",
    description: "Files smaller than this are downloaded single-threaded.",
  },
];

export const ENUMS_REFERENCE = `enum class DownloadMethod {
    NORMAL,    // single-threaded OkHttp, no resume
    ADVANCED,  // multi-threaded Range + resume
}

enum class FallbackStrategy {
    TRY_NEXT,         // try next option in preference list
    ASK,              // show the picker sheet
    DO_NOT_DOWNLOAD,  // show error, don't download
}`;

/* ---------------------------------------------------------------------------
 * Downloads Page UI (from 08-downloads-page-ui.md)
 * ------------------------------------------------------------------------- */

export interface DownloadsPageSection {
  name: string;
  description: string;
  details: string[];
  color: string;
}

export const DOWNLOADS_PAGE_UI: DownloadsPageSection[] = [
  {
    name: "DownloadsScreen (live queue page)",
    description:
      "The main page — NOT a bottom-nav tab. Reached via the More menu (DownloadsMoreEntries). Shows ONLY the live queue (grouped by anime title). Completed downloads live on a SEPARATE page (DownloadedFilesScreen) reached via the top-bar Download icon.",
    details: [
      "Top bar: Download icon (only if downloaded non-empty) + Settings gear.",
      "DownloadActionBar (only if queue non-empty): Pause all / Resume all / Retry all / Cancel all — buttons appear conditionally based on which states are present.",
      "Summary chips: '3 downloading  1 queued  2 paused' (only if queue non-empty).",
      "AnimeSectionCard: accent bar (3dp wide × 20dp tall primary color) + anime title + episode count badge + per-episode EpisodeRow separators.",
      "EpisodeRow: episode name + InfoPills (server + audio + quality + size) + PercentagePill + LinearProgressIndicator (only DOWNLOADING/PAUSED) + 3-dot menu → EpisodeMenuSheet.",
      "Auto-clear completed after 10 seconds (per owner request). File stays on disk; only the in-memory task is removed from the active list.",
      "Grouping: queue is grouped by anime.title (NOT contentId) — MINOR BUG: same-title anime would conflate.",
      "POST_NOTIFICATIONS permission requested on first entry (Android 13+).",
    ],
    color: "var(--c-primary)",
  },
  {
    name: "DownloadedFilesScreen (the separate 'Downloaded' page)",
    description:
      "Reached via the top-bar Download icon. Shows COMPLETED tasks grouped by anime (DownloadedAnimeKey: contentId + title + coverUrl + coverColor).",
    details: [
      "Each DownloadedAnimeCard has a 'delete all' (🗑) button + expand toggle.",
      "Each episode row: tap to play offline (calls onPlayEpisode(contentId, episodeUrl)) + per-episode 🗑 to delete.",
      "Episodes sorted by episode number ascending.",
      "Empty state: centered 'No downloaded files' + 'Downloaded episodes will appear here' hint.",
    ],
    color: "var(--c-success)",
  },
  {
    name: "DownloadSettingsScreen (528 lines, 7 sections)",
    description:
      "Layout (top to bottom): CollapsingHeader → Section 1: Download method (Normal/Advanced toggle + advanced sliders when Advanced) → Section 2: General (folder, show-download-button ABOVE Wi-Fi-only per owner, Wi-Fi-only, concurrent downloads slider) → Section 3: Auto-download toggle → Sections 4-6: Preferred quality / audio / server (collapsible, DragReorderableList + FallbackToggle).",
    details: [
      "SectionContainer(label, content) — vertical card with uppercase label + surfaceVariant background.",
      "CollapsibleSection(title, subtitle, isExpanded, onToggle, content) — header row + AnimatedVisibility content.",
      "ToggleRow(title, subtitle, checked, onCheckedChange) — label + subtitle + Switch.",
      "SliderRow(label, value, range, steps, valueText, onChange) — label + value text + Slider.",
      "FallbackToggle(label, strategy, onSelect) — 3-way segmented toggle for TRY_NEXT / ASK / DO_NOT_DOWNLOAD.",
      "CollapsibleExtensionSection(extSource, ...) — per-extension server list. Merges discovered servers with user's saved order.",
      "Empty state for server section: 'No servers discovered yet. Browse or watch anime from this source to discover servers.'",
    ],
    color: "var(--c-secondary)",
  },
  {
    name: "DownloadVideoPickerSheet (manual picker)",
    description:
      "ModalBottomSheet showing the resolver's List<ResolverServer> as an accordion. Each Server is an expandable card. Inside: each AudioVersion (audio.label — e.g. 'SUB', 'DUB') with a FlowRow of QualityButton s. Tapping a quality button calls onVideoSelected(video, serverName, audioLabel).",
    details: [
      "Shown when preferences.autoDownload().get() == false (manual mode).",
      "ALSO shown when auto-download is ON but FallbackStrategy.ASK triggers (preferred quality/audio unavailable).",
      "Rendered by AnikutaRoot.kt:301-313 when appController.downloadPickerTarget is non-null.",
      "onVideoSelected → appController.enqueuePickedVideo(video, serverName, audioLabel) → downloadOrchestrator.enqueueSpecific (skips re-resolution — uses PickerContext stashed at resolve time).",
    ],
    color: "var(--c-warning)",
  },
  {
    name: "Components (in components/)",
    description:
      "DragReorderableList — drag-and-drop reorder for preference lists (quality / audio / server) — NOT for the queue (FIFO). Per-item drag handle (48dp × 48dp), dragged item follows finger via graphicsLayer.translationY (draw-phase only), non-dragged items snap (no animation — intentional, per KDoc, to avoid scroll jank). Calls onReorder only on drag END. Only takes List<String> — not generic.",
    details: [
      "DownloadedAnimeCard — exported public component (183 lines). NOTE: DownloadedFilesScreen has its own private copy of nearly identical code — code duplication, should be consolidated in new project.",
      "DownloadsEmptyState — two variants: needsFolder=true ('Choose a download folder' + 'Select folder' button) or false ('No downloads yet'). NOTE: DownloadsScreen does NOT use this component — has its own private DownloadsEmptyStateContent. Dead code.",
      "QueueRow — 244-line component. NOT used by current DownloadsScreen (which uses its own private EpisodeRow inside AnimeSectionCard). Leftover from an earlier design. DEAD CODE — should be deleted in new project.",
    ],
    color: "var(--c-danger)",
  },
];

/* ---------------------------------------------------------------------------
 * Details Page Download Control (from 09-details-page-download-ui.md)
 * ------------------------------------------------------------------------- */

export interface EpisodeDownloadStateInfo {
  state: string;
  visual: string;
  action: string;
  color: string;
}

export const EPISODE_DOWNLOAD_STATES: EpisodeDownloadStateInfo[] = [
  {
    state: "NotDownloaded",
    visual: "⬇ Download icon (primary tint)",
    action: "Tap → onDownload",
    color: "var(--c-primary)",
  },
  {
    state: "Resolving",
    visual: "⟳ spinner (18dp, primary) + ✕",
    action: "Tap ✕ → onCancel",
    color: "var(--c-warning)",
  },
  {
    state: "Queued",
    visual: "⟳ spinner (18dp, onSurfaceVariant) + ✕",
    action: "Tap ✕ → onCancel",
    color: "var(--c-secondary)",
  },
  {
    state: "Downloading(progress)",
    visual: "━━━●━━ (40×4dp LinearProgressIndicator, primary) + ✕",
    action: "Tap ✕ → onCancel",
    color: "var(--c-primary)",
  },
  {
    state: "Paused",
    visual: "▶ PlayArrow (primary) + ✕",
    action: "Tap ▶ → onResume",
    color: "var(--c-warning)",
  },
  {
    state: "Error(message)",
    visual: "↻ Refresh (error tint) + ✕",
    action: "Tap ↻ → onRetry",
    color: "var(--c-danger)",
  },
  {
    state: "Downloaded",
    visual: "✓ CheckCircle (primary) + 🗑 Delete",
    action: "Tap 🗑 → onDelete",
    color: "var(--c-success)",
  },
];

export const DETAILS_PAGE_NOTES: { title: string; body: string; color: string }[] = [
  {
    title: "Critical design choice — EpisodeDownloadState lives in :feature:anime-details",
    body: "The sealed interface is in :feature:anime-details (NOT :core:download). The feature module is decoupled from the download engine — the host (AppController) maps DownloadTask → EpisodeDownloadState. This means :feature:anime-details has NO dependency on :core:download.",
    color: "var(--c-primary)",
  },
  {
    title: "Resolving is UI-only (not in DownloadStatus)",
    body: "Resolving is driven by AppController.resolvingEpisodes: SnapshotStateMap<String, Boolean> — set to true on tap (instant UI feedback before enqueue), cleared in finally after enqueueDownload returns. The DownloadStatus enum has no Resolving — it's purely a UI state for the 1-3 seconds between tapping download and the task being enqueued.",
    color: "var(--c-warning)",
  },
  {
    title: "Visibility rules — control always shown for non-NotDownloaded",
    body: "showDownloadBtn comes from displayPrefs?.showDownloadButton (default true). The user can hide all download buttons via Settings. But if the episode is already downloading/downloaded, the control STILL shows so the user can manage it. Per EpisodesSection.kt:642: if (showDownloadBtn || downloadState != EpisodeDownloadState.NotDownloaded) EpisodeDownloadControl(...).",
    color: "var(--c-secondary)",
  },
  {
    title: "Cancel-during-resolve doesn't cancel the network call",
    body: "If the user cancels during the Resolving phase (before the task is enqueued), there's no task to cancel — AppController just clears the resolvingEpisodes flag + dismisses the picker if it's showing for this episode. The resolve network call itself keeps running on the resolver's IO scope. For a long-running resolve (3+ seconds), the user might re-tap download, launching a SECOND resolve — the orchestrator doesn't dedupe resolves.",
    color: "var(--c-danger)",
  },
  {
    title: "No batch download in old project",
    body: "Each episode row has its own download button. There's no select-all / multi-select / 'download episodes 1-12' feature. The only way to download multiple episodes is to tap each one individually. The concurrency limit (default 1) means they queue up FIFO. NEW project may add a 'Download all' button at the section level — see implementation plan.",
    color: "var(--c-warning)",
  },
  {
    title: "KDoc claims AnimatedContent but code doesn't use it",
    body: "EpisodeDownloadControl.kt:38 mentions 'Uses AnimatedContent for smooth state transitions' but there's NO AnimatedContent import or call in the file. Doc-vs-code mismatch — transitions are abrupt. D.6 polish task: actually add AnimatedContent.",
    color: "var(--c-danger)",
  },
];

/* ---------------------------------------------------------------------------
 * Notifications + Foreground Service (from 06-notifications-foreground-service.md)
 * ------------------------------------------------------------------------- */

export const NOTIFICATIONS_FOREGROUND_CALLOUT = {
  critical: true,
  title: "CRITICAL GAP (now CLOSED) — Old project had NO foreground Service + the new design pattern was wrong in the first draft",
  body:
    "The OLD project's download system runs in a CoroutineScope(SupervisorJob + Dispatchers.IO) with no startForeground — on Android 14+ (and aggressive OEMs like Xiaomi/Huawei), background downloads get KILLED when the app is backgrounded. The FOREGROUND_SERVICE_DATA_SYNC permission IS declared but used by ExtensionInstallService only. The new project MUST add a DownloadService with foregroundServiceType=\"dataSync\". CRITICAL REVIEW-5 FIXES (M20-M30): (1) M20 — startForeground MUST be SYNCHRONOUS in onStartCommand, copying the ExtensionInstallService.kt:69 pattern (startForegroundCompat(buildPlaceholderNotification()) BEFORE any coroutine work) — otherwise the Android 12+ 5-second contract is violated + ForegroundServiceDidNotStartInTimeException crashes the app. (2) M21+M22 — downloadCover must use COIL 3 (the project's ImageLoaderFactory is Coil 3 — `coil3.SingletonImageLoader.setSafe { ... }`), not Coil 2; the queueCollector runs on Dispatchers.IO + wraps startForeground/notify in withContext(Dispatchers.Main). (3) M25 — DownloadService must implement KoinComponent. (4) M24 — declare notificationManager: NotificationManagerCompat field. (5) M27+M28 — override onTimeout (the 6-hour dataSync cap, API 35+) + onTaskRemoved (re-launch for aggressive OEMs). (6) M29+M30 — PendingIntent request codes 1001/1002 (not 1/2) + .setVisibility(VISIBILITY_PUBLIC) for lock-screen action visibility. (7) M23 — CREATE :core:download/src/main/AndroidManifest.xml declaring ACCESS_NETWORK_STATE + the <service> element (the manifest did NOT exist in the OLD draft).",
  color: "var(--c-danger)",
};

export interface NotificationPlan {
  aspect: string;
  oldProject: string;
  newProject: string;
}

export const NOTIFICATION_PLAN: NotificationPlan[] = [
  {
    aspect: "Channel",
    oldProject: 'Single "anikuta_downloads" IMPORTANCE_LOW (no sound, shows in shade).',
    newProject:
      "TWO channels: anikuta_downloads_progress (IMPORTANCE_LOW, no sound, ongoing) for the summary + progress, + anikuta_downloads_complete (IMPORTANCE_DEFAULT with sound) for completion. Sound on completion only.",
  },
  {
    aspect: "Summary notification",
    oldProject:
      "ID 9001, ongoing, progress bar, throttled 800ms (PROGRESS_THROTTLE_MS). Picks first DOWNLOADING task as primary (or falls back to first active task). Title shows count when > 1 ('Downloading 3 episodes'). setOngoing(true) — can't be swiped. setOnlyAlertOnce(true) + setSilent(true).",
    newProject:
      "Same ID 9001. ADD Pause/Cancel action buttons + .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) (M30 — for lock-screen action visibility). Thumbnail loaded via Coil 3 (M21+M22).",
  },
  {
    aspect: "Completion notification",
    oldProject:
      "ID taskId.toInt() + 10_000 (COMPLETION_OFFSET). stat_sys_download_done icon. setAutoCancel(true). PRIORITY_LOW. Called from DownloadQueue.launchDownload success path via onTaskCompleted callback.",
    newProject: "Same + on the dedicated anikuta_downloads_complete channel (sound plays).",
  },
  {
    aspect: "Error notification",
    oldProject:
      "ID taskId.toInt() + 20_000 (ERROR_OFFSET). stat_notify_error icon. setAutoCancel(true). PRIORITY_DEFAULT (higher than completion's PRIORITY_LOW) — so failures are more visible. Called from DownloadQueue.launchDownload DownloadException + generic Exception catch blocks via onTaskError callback.",
    newProject: "Same — but fires from the RETRYING max-attempts-reached path too (not just the unrecoverable-exception path).",
  },
  {
    aspect: "Tap intent",
    oldProject:
      "Just opens the app's launcher activity (no deep-link to the Downloads screen). PendingIntent.FLAG_IMMUTABLE on API 23+ (required on API 31+).",
    newProject: "Deep-link to the Downloads screen (anikuta://downloads → Nav3 DownloadsKey push).",
  },
  {
    aspect: "POST_NOTIFICATIONS",
    oldProject:
      "Requested on first entry to the Downloads page (Android 13+ only). Result ignored — system remembers the user's choice. If denied, notifier's try/catch SecurityException silently swallows the post failure. In-app UI still works.",
    newProject: "Same.",
  },
  {
    aspect: "Foreground Service",
    oldProject: "**NONE** — runs in CoroutineScope(SupervisorJob + Dispatchers.IO).",
    newProject:
      "ADD DownloadService (foregroundServiceType=\"dataSync\") + SYNCHRONOUS startForeground in onStartCommand (M20 — copies ExtensionInstallService.kt:69). Started when first download starts; stopped when queue empties. Permissions FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC already declared in :app manifest. M23 — CREATE :core:download/src/main/AndroidManifest.xml with ACCESS_NETWORK_STATE + the <service> element.",
  },
  {
    aspect: "Cover thumbnail loading (REVIEW-5 M21+M22 — NEW)",
    oldProject: "Not implemented — no thumbnails on notifications.",
    newProject:
      "Coil 3 (NOT Coil 2): context.imageLoader (Coil 3 extension on PlatformContext, set as singleton in AnikutaApp.kt via coil3.SingletonImageLoader.setSafe { ... }), ImageRequest.Builder(context).data(url).size(96).build(), loader.execute(request).image?.let { image -> image.asDrawable(context).toBitmap() }. downloadCover + loadThumbnail + buildSummaryNotification are all suspend (no runBlocking). The queueCollector runs on Dispatchers.IO; startForeground/notify are wrapped in withContext(Dispatchers.Main).",
  },
  {
    aspect: "Foreground service durability (REVIEW-5 M27+M28 — NEW)",
    oldProject: "N/A (no foreground service).",
    newProject:
      "onTimeout(startId, foregroundServiceType) override — handles the 6-hour dataSync cap on API 35+ (Android stops the service after 6 hours; we honor it + post a 'Download paused — service timed out' notification). onTaskRemoved override — re-launches the service for aggressive OEMs (Xiaomi/Huawei) that kill the service when the user swipes the app from recents.",
  },
];

export const NOTIFICATION_CONSTANTS = `companion object {
    // REVIEW-5 (M20): the canonical synchronous startForeground pattern, copied verbatim
    // from ExtensionInstallService.kt:69 — startForegroundCompat must be called SYNCHRONOUSLY
    // in onStartCommand BEFORE any coroutine work, otherwise the Android 12+ 5-second contract
    // is violated + ForegroundServiceDidNotStartInTimeException crashes the app.
    private const val CHANNEL_PROGRESS = "anikuta_downloads_progress"   // IMPORTANCE_LOW, no sound
    private const val CHANNEL_COMPLETE = "anikuta_downloads_complete"   // IMPORTANCE_DEFAULT, sound on completion
    private const val SUMMARY_ID = 9001
    private const val COMPLETION_OFFSET = 10_000
    private const val ERROR_OFFSET = 20_000
    private const val PROGRESS_THROTTLE_MS = 800L
    private const val ACTION_PAUSE_ALL  = "anikuta.action.PAUSE_ALL"
    private const val ACTION_CANCEL_ALL = "anikuta.action.CANCEL_ALL"
    private const val REQUEST_PAUSE_ALL  = 1001   // M29 — unique PendingIntent request codes (not 1/2)
    private const val REQUEST_CANCEL_ALL = 1002
    @Volatile private var lastProgressAt = 0L
}

// DownloadService.onStartCommand (M20):
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!isForeground) {
        startForegroundCompat(buildPlaceholderNotification())   // SYNCHRONOUS — before any coroutine work
        isForeground = true
    }
    when (intent?.action) {
        ACTION_PAUSE_ALL  -> scope.launch { manager.pauseAll() }
        ACTION_CANCEL_ALL -> scope.launch { manager.cancelAll() }
    }
    return START_STICKY
}
// The queueCollector runs on Dispatchers.IO + wraps startForeground/notify in
// withContext(Dispatchers.Main) (notification APIs are main-thread-only — M22).`;

/* ---------------------------------------------------------------------------
 * Player Integration (from 10-player-integration.md)
 * ------------------------------------------------------------------------- */

export const PLAYER_INTEGRATION_DIAGRAM = `User taps episode row on details page
  │
  ▼
AppController.resolveEpisode(episode, source, watchCtx, contentId)
  │
  ├── BEFORE streaming-resolver dance: check offline short-circuit
  │     │
  │     ▼
  │   downloadManager.isEpisodeDownloaded(contentId, episode.episode_number)
  │     │
  │     ├── 1. Try in-memory task lookup (fast path):
  │     │      findTask(contentId, episodeNumber)
  │     │      → task?.status == COMPLETED  → return true
  │     │
  │     └── 2. Filesystem fallback (source-switching fix):
  │            storage.findEpisodeDirByNumber(contentId, episodeNumber)
  │            → scan <root>/ANIKUTA/downloads/anime/<... [contentId-safe]>/Episode NNN/
  │            → look for video.* file
  │            → return true if found
  │     │
  │     ▼
  │   if (true):
  │     videoUri   = downloadManager.getDownloadedVideoUri(contentId, episodeNumber)
  │     subUris    = downloadManager.getDownloadedSubtitleUris(contentId, episodeNumber)
  │     → build WatchRequest(
  │         videoUrl     = content:// URI,
  │         videoHeaders = null,
  │         source       = null,
  │         videoServer  = "Offline",
  │         subtitleTracks = subUris.map { SubtitleTrack(it, "External") }
  │       )
  │     → pushWatch(WatchRequest)
  │     → return@launch (skip streaming path)
  │
  └── Streaming path (fall-through):
        normal resolver sheet flow`;

export interface PlayerIntegrationNote {
  title: string;
  body: string;
  color: string;
}

export const PLAYER_INTEGRATION_NOTES: PlayerIntegrationNote[] = [
  {
    title: "WatchScreen treats local content:// URI the same as a remote URL",
    body: "There are NO references to 'offline' / 'downloaded' / isEpisodeDownloaded in WatchScreen.kt. The player library handles content:// → fd://<fd_number> conversion via ContentResolver.openFileDescriptor. The download integration just passes the content:// URI as the videoUrl — no special-casing needed in the player.",
    color: "var(--c-primary)",
  },
  {
    title: "MPV plays content:// URIs via fd://",
    body: "Android's ContentResolver.openFileDescriptor(uri, 'r') returns a ParcelFileDescriptor for any content:// URI. MPV can play via fd://<fd_number>. The resolveUrlForMpv helper handles this conversion. Same approach as the LocalProxyServer URLs.",
    color: "var(--c-secondary)",
  },
  {
    title: "Episode switching re-runs the offline check",
    body: "WatchRequest.episodeList carries the full episode list (passed from the details screen). When the user taps 'next episode', the WatchScreen calls back into AppController to resolve the next episode — which RE-RUNS the offline short-circuit for that next episode. Seamless offline transition if next is also downloaded; falls through to streaming resolver if not.",
    color: "var(--c-success)",
  },
  {
    title: "Watch progress recorded normally (keyed by contentId + episodeNumber)",
    body: "Same as streaming. WatchScreen calls watchProgressStore.set(contentId, episodeNumber, position, duration) periodically. contentId is derived from watchRequest.anilistId ('al:$anilistId'). GAP: if anilistId == 0 (unlinked extension anime), no progress recorded — but this is a WatchScreen issue, not a download issue. New project should use contentId directly (D.6 polish).",
    color: "var(--c-warning)",
  },
  {
    title: "Deleted-file race handled gracefully",
    body: "If the user deletes the file via a file manager (bypassing the app), isEpisodeDownloaded returns false → falls through to streaming. If the in-memory task says COMPLETED but the file is gone (deleted mid-session), getDownloadedVideoUri returns null → WatchScreen would try to play a non-existent URI → MPV error → PlayerErrorOverlay shown → user re-downloads.",
    color: "var(--c-danger)",
  },
  {
    title: "NEW project should add an explicit 'Offline' badge",
    body: "There is NO explicit 'Offline' badge in the WatchScreen or the episode row. The only indicator is the green ✓ icon in EpisodeDownloadControl (when state is Downloaded). WatchRequest.videoServer is set to 'Offline' but isn't displayed prominently. New project should add a visible 'Playing offline' badge in the WatchScreen for clarity.",
    color: "var(--c-primary)",
  },
];

/* ---------------------------------------------------------------------------
 * Database Schema (from 11-db-schema.md + 13-implementation-plan.md)
 * ------------------------------------------------------------------------- */

export const DB_SCHEMA_DECISION = {
  title: "Decision D1 — Persistence: SQLDelight (NOT JSON-in-SharedPrefs) — re-keyed by mainId + episodeKey",
  recommendation:
    "Option B1 (lean — separate columns). Tables already exist (download_queue, downloaded_episode). RE-KEY by mainId + episodeKey (5-digit padded, e.g. 'mainId|00001'). REVIEW-5 M1+M2 (R1-C3 + R1-C4): edit the .sq files DIRECTLY — do NOT add a 3.sqm migration file (the project has ZERO .sqm files; SQLDelight 2.x derives the v1 schema directly from the .sq files' CREATE TABLE IF NOT EXISTS statements). The new schema is the canonical v1. Existing dev installs must wipe app data once (adb shell pm clear com.confused.anikuta or uninstall+reinstall). DatabaseDriverFactory.create() does NOT need a migrations=... arg for this rewrite — but the NEXT schema change MUST pair with a real 1.sqm + the DatabaseDriverFactory update.",
  oldProject:
    "JSON-serialized List<DownloadTask> in SharedPreferences under key pref_download_tasks_v1. Reason quoted from DownloadStore.kt:14-23 — 'The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how WatchProgressStore already works.'",
  newProject:
    "Use SQLDelight tables — already exist in core/database/src/main/sqldelight/. Wrap with a reactive StateFlow for UI consumption. Throttle progress writes at the app level (same as old project's persistThrottled). M3: getDownloadedMainIds uses MAX(...) for bare columns (not DISTINCT + GROUP BY). M6: resetDownloadingToQueued SQL is WHERE state IN ('DOWNLOADING', 'RETRYING') (also resets RETRYING — was 'DOWNLOADING' only). M7: add updateDownloadContentId for source-switch sync. M8: state column comment lists all 7 states (incl. RETRYING).",
  color: "var(--c-primary)",
};

export interface DbTableInfo {
  name: string;
  purpose: string;
  schema: string;
  isNew: boolean;
}

export const DB_SCHEMA_TABLES: DbTableInfo[] = [
  {
    name: "download_queue (PROPOSED — Option B1, separate columns, re-keyed by mainId + episodeKey)",
    purpose:
      "The live queue (QUEUED + DOWNLOADING + RETRYING + PAUSED + ERROR tasks). Carries the full task data as columns for queryability. REVIEW-5 M1+M2: edit the .sq file DIRECTLY — NO 3.sqm migration (the project has ZERO .sqm files; SQLDelight 2.x derives v1 from the .sq files' CREATE TABLE statements).",
    isNew: true,
    schema: `-- downloadQueue.sq — EDIT DIRECTLY (M1+M2 — NO 3.sqm migration file).
-- The new schema is the canonical v1. Existing dev installs wipe app data once.
CREATE TABLE download_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    main_id TEXT NOT NULL,                -- the stable UUID (re-keyed from contentId — survives source switches)
    episode_key TEXT NOT NULL,            -- "$mainId|$NNNNN" (5-digit padded — see 04-storage-paths.md §4.2)
    content_id TEXT NOT NULL,             -- 6-section contentId (changes when sources switch)
    episode_url TEXT NOT NULL,
    episode_number REAL NOT NULL,
    episode_name TEXT NOT NULL,
    content_title TEXT NOT NULL,
    cover_url TEXT,
    cover_color INTEGER,
    video_url TEXT NOT NULL,
    video_headers TEXT,
    subtitle_tracks TEXT,                 -- JSON array of DownloadTrack
    audio_tracks TEXT,                    -- JSON array
    source_id INTEGER,                    -- nullable (no fake DEFAULT 0 sentinel)
    video_server TEXT,
    video_quality TEXT,
    video_audio TEXT,
    resolve_context TEXT,                 -- JSON-encoded ResolveContext (M64 — 7 fields) for re-resolve-on-IOException
    state TEXT NOT NULL,                  -- QUEUED / DOWNLOADING / RETRYING (M9) / PAUSED / COMPLETED / ERROR / CANCELLED (M8 — comment lists all 7)
    progress INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    total_bytes INTEGER NOT NULL DEFAULT -1,
    prev_total_bytes INTEGER,             -- M38: persisted across pause/resume (bar doesn't jump backward)
    prev_estimate_bytes INTEGER,          -- M38: same — for the case where total is unknown
    recent_ratios_json TEXT,              -- M31+M38: ArrayDeque<Float>(5) serialized (smoothing window)
    retry_attempt INTEGER NOT NULL DEFAULT 0,         -- M9: retry metadata on the row (enum can't carry per-instance data)
    retry_max_attempts INTEGER NOT NULL DEFAULT 3,    -- M9: per-row
    last_error TEXT,                                   -- M9: last error message (for UI 'Retrying (2/3): lastError')
    error_message TEXT,
    video_uri TEXT,                       -- set on COMPLETED
    subtitle_uris TEXT,                   -- JSON array of content:// URIs
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_download_queue_state ON download_queue(state);
CREATE INDEX idx_download_queue_main ON download_queue(main_id);
CREATE UNIQUE INDEX idx_download_queue_episode ON download_queue(main_id, episode_key);

-- Queries (the relevant ones):
-- resetDownloadingToQueued:  UPDATE download_queue SET state='QUEUED', started_at=NULL
--                            WHERE state IN ('DOWNLOADING', 'RETRYING');   -- M6: also resets RETRYING
-- getDownloadedMainIds:      SELECT main_id, MAX(updated_at), MAX(completed_at)   -- M3: MAX for bare cols (no DISTINCT)
--                            FROM downloaded_episode GROUP BY main_id;
-- updateDownloadContentId:   UPDATE downloaded_episode SET content_id=:new
--                            WHERE main_id=:mainId AND episode_key=:key;       -- M7: source-switch sync
-- isEpisodeDownloaded:       SELECT EXISTS(SELECT 1 FROM downloaded_episode WHERE main_id=:mainId AND episode_key=:key);`,
  },
  {
    name: "download_queue (CURRENT STUB — needs replacement per M1+M2)",
    purpose:
      "Minimal stub table currently in the new project — lacks all task data columns. The stub DownloadManager uses it but it can't carry enough to restart a download without re-resolving.",
    isNew: false,
    schema: `CREATE TABLE IF NOT EXISTS download_queue (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    episode_key TEXT NOT NULL,
    state TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_download_state ON download_queue(state);

-- Queries:
-- insertDownloadQueue, updateDownloadState, getDownloadQueue,
-- getDownloadQueueByState, deleteDownloadQueue, deleteDownloadQueueByEpisode`,
  },
  {
    name: "downloaded_episode (CURRENT + PROPOSED ADDITIONS)",
    purpose:
      "Completed downloads library. Current stub has only the basics. RECOMMENDATION: add main_id, episode_key, content_id, episode_number, content_title, cover_url, verified_at columns for the Downloads-screen grouping + the post-publish verification.",
    isNew: false,
    schema: `CREATE TABLE IF NOT EXISTS downloaded_episode (
    episode_key TEXT NOT NULL PRIMARY KEY,    -- "$mainId|$NNNNN"
    main_id TEXT NOT NULL,                    -- PROPOSED ADDITION (D.0 schema migration)
    content_id TEXT NOT NULL,                 -- PROPOSED ADDITION (for source-switch sync — M7)
    file_path TEXT NOT NULL,                  -- content:// URI (SAF)
    file_size INTEGER NOT NULL,
    episode_number REAL NOT NULL,             -- PROPOSED ADDITION
    content_title TEXT NOT NULL,              -- PROPOSED ADDITION
    cover_url TEXT,                           -- PROPOSED ADDITION
    cover_color INTEGER,                      -- PROPOSED ADDITION
    quality TEXT,
    video_server TEXT,                        -- PROPOSED ADDITION
    video_audio TEXT,                         -- PROPOSED ADDITION
    verified_at INTEGER,                      -- PROPOSED ADDITION — last verification timestamp (16-quality-of-life §4.3)
    downloaded_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_downloaded_episode_main ON downloaded_episode(main_id);

-- Queries:
-- insertDownloadedEpisode (INSERT OR REPLACE)
-- getDownloadedEpisode, getAllDownloadedEpisodes
-- isEpisodeDownloaded (SELECT EXISTS — keyed by main_id + episode_key, NOT contentId)
-- deleteDownloadedEpisode
-- markEpisodeMissing (for scan reconciliation — sets verified_at=0 + flags the UI)`,
  },
];

export const DB_OLD_PROJECT_NO_DOWNLOAD_TABLES =
  "The old ANI-KUTA SQLDelight database (anikuta.db) contains ONLY: animes, episodes, anime_category, animehistory, animetrack, categories + migration files 1.sqm + 2.sqm. NO download_queue, NO downloaded_episode, NO download-related table. Verified by grepping the .sq files for 'download' → no matches. All download state lives in SharedPreferences via DownloadStore.";

/* ---------------------------------------------------------------------------
 * DI Wiring — Koin modules (from 12-di-wiring.md)
 * ------------------------------------------------------------------------- */

export interface KoinModule {
  module: string;
  file: string;
  provides: string;
  bindings: { name: string; scope: string; qualifier: string; notes: string }[];
  color: string;
}

export const DI_MODULES: KoinModule[] = [
  {
    module: "downloadModule",
    file: ":core:download/di/DownloadModule.kt (71 lines)",
    provides: "Engine singletons",
    color: "var(--c-primary)",
    bindings: [
      {
        name: "DownloadPreferences",
        scope: "single",
        qualifier: "(default)",
        notes: "Backed by the shared PreferenceStore.",
      },
      {
        name: "DownloadStore",
        scope: "single",
        qualifier: "(default)",
        notes: "Same PreferenceStore (shares the SharedPreferences file).",
      },
      {
        name: "ServerDiscoveryStore",
        scope: "single",
        qualifier: "(default)",
        notes: "Same PreferenceStore.",
      },
      {
        name: "TempDownloadCache",
        scope: "single",
        qualifier: "(default)",
        notes: "Calls cleanupStale() on creation.",
      },
      {
        name: "DownloadResumeManager",
        scope: "single",
        qualifier: "(default)",
        notes: "Depends on TempDownloadCache.",
      },
      {
        name: "AdvancedHttpDownloader",
        scope: "single",
        qualifier: "(default)",
        notes: "Depends on OkHttpClient(named('download')), TempDownloadCache, DownloadResumeManager, DownloadPreferences.",
      },
      {
        name: "OkHttpClient",
        scope: "single",
        qualifier: 'named("download")',
        notes: "Long timeouts (30s connect, 60s read/write). Separate from the extension NetworkHelper client so a stuck download can't starve extension HTTP calls.",
      },
      {
        name: "DownloadManager",
        scope: "single (bound to interface)",
        qualifier: "(default)",
        notes: "DefaultDownloadManager impl. Internally creates (NOT exposed via DI): DownloadStorageProvider, HttpDownloader, DownloadNotificationManager, DownloadQueue — all private val inside the manager.",
      },
    ],
  },
  {
    module: "downloadFeatureModule",
    file: ":feature:download/di/DownloadModule.kt (19 lines)",
    provides: "The ViewModel",
    color: "var(--c-secondary)",
    bindings: [
      {
        name: "DownloadViewModel",
        scope: "viewModel",
        qualifier: "viewModelOf(::DownloadViewModel)",
        notes: "Lifecycle-aware Koin ViewModel. Constructor params (DownloadManager, DownloadPreferences) resolved from the Koin graph.",
      },
    ],
  },
  {
    module: "downloadAppModule",
    file: ":app/di/DownloadAppModule.kt (43 lines)",
    provides: "Aggregates the two above + adds ResolverService, DownloadOrchestrator, DownloadMigration",
    color: "var(--c-success)",
    bindings: [
      {
        name: "(includes)",
        scope: "—",
        qualifier: "includes(downloadModule, downloadFeatureModule)",
        notes: "Re-exports the core + feature modules so App.kt only lists one entry.",
      },
      {
        name: "ResolverService",
        scope: "single",
        qualifier: "(default)",
        notes: "Heavy class — initializes HTTP etc.",
      },
      {
        name: "DownloadOrchestrator",
        scope: "single",
        qualifier: "(default)",
        notes: "Params: ResolverService, DownloadManager, DownloadPreferences, ServerDiscoveryStore.",
      },
      {
        name: "DownloadMigration",
        scope: "single",
        qualifier: "(default)",
        notes: "⚠️ KNOWN UNKNOWN: asks for DownloadStorageProvider from Koin, but DownloadStorageProvider is NOT a Koin binding (it's created internally by DefaultDownloadManager). Either a bug OR there's a binding I missed. New project: make DownloadStorageProvider an explicit Koin single.",
      },
      {
        name: "DownloadService (NEW)",
        scope: "single",
        qualifier: "(default)",
        notes: "NEW project recommendation — foreground service. Old project has none.",
      },
    ],
  },
];

export const DI_GRAPH = `App.kt startKoin
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
       │    └── single DownloadManager → DefaultDownloadManager(...)
       │         (internally creates: DownloadStorageProvider, HttpDownloader, DownloadNotificationManager, DownloadQueue)
       │
       ├── includes: downloadFeatureModule (:feature:download)
       │    └── viewModel DownloadViewModel(DownloadManager, DownloadPreferences)
       │
       ├── single ResolverService()
       ├── single DownloadOrchestrator(ResolverService, DownloadManager, DownloadPreferences, ServerDiscoveryStore)
       └── single DownloadMigration(DownloadStore, DownloadStorageProvider ⚠️)`;

/* ---------------------------------------------------------------------------
 * Implementation Phases — D.0 → D.6 (from 13-implementation-plan.md)
 * ------------------------------------------------------------------------- */

export interface ImplementationPhase {
  id: string;
  title: string;
  status: "planned";
  days: string;
  goal: string;
  tasks: string[];
  color: string;
}

export const IMPLEMENTATION_PHASES: ImplementationPhase[] = [
  {
    id: "D.0",
    title: "Foundations (the REVIEW-5 consolidation pass)",
    status: "planned",
    days: "2-3 days",
    goal: "Extend the new project's infrastructure to support the download system. All 8 M1-M8 (DB schema) + M49 (HttpException) + M65 (scanner deps) + M23/M63 (manifest) + M26 (drawables) + M12 (delete the stub) fixes land here.",
    color: "var(--c-primary)",
    tasks: [
      "Extend PreferenceStore (:core:preferences) with a reactive Flow<T> API — preferenceFlow<T>(key, default) helper backed by OnSharedPreferenceChangeListener. The UI depends on reactive prefs. M46+M47: Preference<T> interface restored to 7 methods + onStart { emit(get()) } removed (redundant with collectAsState(initial = ...)).",
      "Update SQLDelight schema: edit downloadQueue.sq + downloadedEpisode.sq DIRECTLY (M1+M2 — NO 3.sqm migration file; the project has ZERO .sqm files — SQLDelight 2.x derives v1 from the .sq files). Re-key by mainId + episodeKey (5-digit padded). Add the new columns (resolve_context, prev_total_bytes, prev_estimate_bytes, recent_ratios_json, retry_attempt, retry_max_attempts, last_error). M6: resetDownloadingToQueued WHERE state IN ('DOWNLOADING', 'RETRYING'). M3: getDownloadedMainIds uses MAX(...). M7: add updateDownloadContentId for source-switch sync. M8: state column comment lists all 7 states.",
      'Add the "download" qualified OkHttpClient to :core:network. Long timeouts (30s connect, 60s read/write) — separate from the extension NetworkHelper client.',
      "Add SAF DocumentFile dependency to :core:download's build.gradle.kts: implementation('androidx.documentfile:documentfile:1.0.1').",
      "Add kotlinx-serialization-json to :core:download (for ContentDataJson + DownloadTrack JSON columns + ResumeMetadata).",
      "Delete the stub DownloadManager.kt + DownloadState.kt in :core:download (M12 — the canonical state type is enum class DownloadStatus with 7 UPPERCASE constants: QUEUED, DOWNLOADING, RETRYING, PAUSED, COMPLETED, ERROR, CANCELLED. Retry metadata lives on DownloadTask). The stub DownloadModule.kt will be rewritten in D.1.",
      "Add core/content dependency to :core:download (for the mainId/contentId/ContentRecord types).",
      "M49: define HttpException LOCALLY in :core:download (HttpException.kt — class HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause)). Do NOT add a :core:source-api dependency. HttpDownloader.downloadNormal + HlsDownloader.fetchText/downloadSegment throw it for HTTP errors.",
      "M65: DownloadScanner constructor deps are (Context, DownloadStorageProvider, DownloadStore, ContentRepository, AnilistDetailRepository) — the last two come from :core:content.",
      "M23/M63: CREATE :core:download/src/main/AndroidManifest.xml (does NOT currently exist) declaring <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" /> + the <service android:name=\"...DownloadService\" android:foregroundServiceType=\"dataSync\" /> element.",
      "M26: create :core:download/src/main/res/drawable/ic_pause.xml + ic_cancel.xml vector drawables (referenced by the notification action buttons).",
    ],
  },
  {
    id: "D.1",
    title: "Engine + Storage (NEW data.json system)",
    status: "planned",
    days: "4-5 days",
    goal: "Port + adapt the :core:download engine + implement the NEW SAF/data.json storage system. No UI yet — just the engine + DI + the reinstall-recognition scan. The biggest phase.",
    color: "var(--c-secondary)",
    tasks: [
      "Port + adapt ~24 files: DownloadModels, DownloadContentInfo, DownloadEpisodeInfo, DownloadRequest (add resolveContext: ResolveContext?), DownloadStatus (enum + isTerminal/isActive), DownloadTask (@Serializable, re-keyed by mainId + episodeKey), DownloadManager (interface — rename stub), DownloadStore (NEW — thin wrapper around SQLDelight), DownloadPreferences (all 17 settings — old 15 + dimensionPriority + globalFallback), DownloadLogger, DynamicProgressTracker (smooth progress — M40 restored sanity check), TempDownloadCache (M59: hasSpaceFor), DownloadStorageProvider (the NEW SAF system), ContentDataJson (the schema model + parser + writer), DownloadScanner (scan-on-startup), VideoTypeDetector, HttpDownloader (~538 lines + M15+M16+M17+M35+M37+M49 fixes), HlsDownloader (~333 lines + M32+M33+M39 fixes), DownloadQueue (~315 lines, adapt persistence to SQLDelight, M11+M31+M34+M38+M41+M42+M43+M36), DownloadNotificationManager (NEW design), DefaultDownloadManager, ServerDiscoveryStore, DownloadService (foreground service), di/DownloadModule (Koin bindings).",
      "ContentDataJson + DownloadScanner — these are NEW (not in the old project). They're the heart of the reinstall recognition system. Write thorough unit tests for ContentDataJson parsing + the scanner's reconciliation logic (M55: listFiles() ONCE per content folder + Map<String, DocumentFile> index; M57: scan includes audio/ format folder; M58: fall back to 'always scan' if DocumentFile.lastModified() returns 0).",
      "DownloadStorageProvider.publishToUserFolder — must do the read-modify-write of data.json ATOMICALLY (temp file → copy to SAF). Creates the content folder + .nomedia (M54) + cover.jpg + the video + subtitles + data.json (read-modify-write — never overwrite episodes[] from a different content).",
      "DownloadQueue adapted to SQLDelight (see D.1 critical sub-tasks in 13-implementation-plan.md). M11: setRetryingStatus + setErrorStatus as private methods. M31: recentRatios: ArrayDeque<Float>(5) per-task. M34: INLINE _tasks.value = + Channel-based DB writes (was 60,000+ pending coroutines). M38: persist prevTotal/prevEstimate/recentRatios across pause/resume. M41: mutateTask is suspend fun. M42: onNetworkChanged uses pauseInternal (no deadlock). M43: scheduleAutoClear's autoClearScheduled.add wrapped in mutex.withLock. M36: DynamicProgressTracker.complete() flips 99→100 on COMPLETED.",
      "Create DownloadService (foreground service — M20+M22+M24+M25+M27+M28): implement KoinComponent; declare notificationManager field; use Dispatchers.IO for the queueCollector + withContext(Dispatchers.Main) for startForeground/notify; SYNCHRONOUS startForegroundCompat(buildPlaceholderNotification()) in onStartCommand before any coroutine work; onTimeout (6-hour dataSync cap, API 35+); onTaskRemoved (re-launch for aggressive OEMs).",
      "HttpDownloader.downloadNormal (M15+M16+M17+M35+M37+M49): add reResolveAttempts: Int = 0 parameter; cap at MAX_RE_RESOLVE_ATTEMPTS = 1 (= 2 total download attempts); throw DownloadException on cap exceeded; truncate temp file before the recursive call; throw HttpException(response.code, ...) for HTTP errors; emit intermediate onProgress ticks during validation/subtitles/metadata/publish (96/97/98/99%); the finally block distinguishes CancellationException (preserve resume metadata) from completion/error.",
      "HlsDownloader (M32+M33+M39): refine estimatedTotal after each segment using the running average segment size; downloadSegmentWithRetry downloads each attempt to a ByteArrayOutputStream first + writes to out only on success; probeSegmentSize uses a 1-byte Range GET (not HEAD — anti-scraping CDNs reject HEAD).",
      "Manifest entry: <service android:name=\"com.confused.anikuta.core.download.DownloadService\" android:exported=\"false\" android:foregroundServiceType=\"dataSync\" /> + the ic_pause.xml / ic_cancel.xml drawables.",
      "AnikutaApp.onCreate scan trigger — after Koin starts, call downloadManager.requestFolderRescan() on Dispatchers.IO. This is the reinstall recognition flow.",
      "Testing for D.1: manually trigger a download (via a temporary debug button) + verify folder structure matches the ASCII tree in 04-storage-paths.md §3.1. Verify data.json is created in the content folder with all fields populated. Verify temp cache is cleaned up after the task finishes. Restart the app → verify the scan re-discovers the content + the DB row is recreated. Delete the app → reinstall → re-select the same folder → verify the scan re-registers everything.",
    ],
  },
  {
    id: "D.2",
    title: "Orchestrator + Auto-download engine + proxy-churn fix",
    status: "planned",
    days: "3-4 days",
    goal: "Bridge the resolver + engine + implement the NEW 5-step priority pipeline + the proxy-churn fix (4 layers). The headline feature for the user's 'which dimension matters most' gap.",
    color: "var(--c-warning)",
    tasks: [
      "AutoDownloadEngine.kt (NEW — the 5-step pure-function pipeline: flatten → rank → applyFallbacks → pick → globalFallback). Pure functions over data classes — trivially unit-testable. M44: globalFallback fires based on the picked candidate's match quality (isPerfectMatch = audioRank == 0 && qualityRank == 0 && serverRank == 0), NOT on sortedCandidates.isEmpty(). M45: dimensionPriority default [AUDIO, QUALITY, SERVER] is a DELIBERATE change (the OLD project's effective priority was inconsistent — neither matches).",
      "ProxyLeaseCoordinator.kt (NEW — optional tertiary fix for the proxy-churn bug). Tracks active leases: Map<ProxyKey, LeaseRefcount>. Suppresses a second getHosterList while a download is using the proxy.",
      "ResolverVideo.kt (MODIFY in :core:video-resolver): add directUrl: String? field. The resolver strategy extracts the underlying CDN URL by calling a new Video.directVideoUrl extension hook.",
      "DownloadOrchestrator.kt (in :app): adapt VideoResolver → new project's VideoResolver; adapt ResolverResult/ResolverServer/ResolverVideo types. Internal selectBestVideo impl replaced by AutoDownloadEngine.selectBestVideo. buildRequest uses selection.video.directUrl ?: selection.video.url for DownloadRequest.videoUrl.",
      "ResolveContext.kt (NEW — M64): captures (sourceId, episodeUrl, serverName, audioLabel, quality, mainId, episodeKey) — 7 fields (the OLD draft listed only 5 — mainId + episodeKey needed for DB lookups during re-resolve).",
      "ReResolver.kt (NEW — M17): the re-resolve helper. Caps attempts at 1 (one initial + one re-resolve). Does a DIRECT lookup by pinned (server, audio, quality) — does NOT re-run the AutoDownloadEngine. The autoDownloadEngine: AutoDownloadEngine constructor param was REMOVED (was dead DI param) + from the Koin binding in 12-di-wiring.md §11.2.",
      "EpisodeDownloadState.kt + EpisodeDownloadControl.kt (in :feature:anime-details/impl): M13 — add data class Retrying(attempt, maxAttempts, lastError) variant. The control renders 'Retrying (2/3)…' pill (driven by task.retryAttempt + task.retryMaxAttempts).",
      "Modify EpisodesSection.kt + DetailsScreen.kt + DetailsViewModel.kt: add onDownloadEpisode, downloadStates, onDownloadCancel/Resume/Retry/Delete params.",
      "The proxy-churn fix (D9): Fix 1 (PRIMARY) — directUrl on ResolverVideo + prefer it for downloads. Fix 2 (SECONDARY) — re-resolve-on-IOException for localhost-URL downloads (M15: cap at 1 re-resolve attempt = 2 total download attempts). Fix 3 (TERTIARY) — ProxyLeaseCoordinator (optional, deferred). Fix 4 (QUATERNARY) — foreground service for download durability (architecturally aligned, handled in D.4).",
    ],
  },
  {
    id: "D.3",
    title: "Queue management + Dynamic progress tracking",
    status: "planned",
    days: "2 days",
    goal: "Proper queue start-next logic, configurable concurrency, smooth progress bar (the user's complaint about the bar jumping 95→100 is now actually fixed — M35).",
    color: "var(--c-success)",
    tasks: [
      "Queue management: persisted in SQLDelight download_queue (not in-memory). Concurrency: Semaphore-based, configurable via pref_dl_concurrent (1..5, default 1). On pref change, DownloadQueue.refreshConcurrency() is called via a Flow collector (FIXES the OLD project's bug). Mutex-based thread-safety (M41+M42+M43).",
      "tryStartNext: picks the FIRST QUEUED task in FIFO order. Skips if connectivityCheck() fails. Wi-Fi-only check on every tryStartNext AND every network change (via NetworkCallback). Auto-pauses on metered network (QoL feature — M42 pauseInternal accepts RETRYING).",
      "Dynamic progress tracking: byte-count-based for ALL engines (including HLS — track total bytes downloaded across all segments). Moving average (window of 5 ticks — M31 recentRatios: ArrayDeque<Float>(5)) to smooth out network jitter. M35: emit intermediate onProgress ticks during validation/subtitles/metadata/publish (96/97/98/99%) so the bar doesn't jump 95→100. M36: DynamicProgressTracker.complete() flips 99→100 on COMPLETED. M38: persist prevTotal/prevEstimate/recentRatios across pause/resume (bar doesn't jump backward on resume).",
      "HLS without a known total: estimate using '10 MB ahead' strategy (INITIAL_ESTIMATE_BYTES = 10 MB). M32: refine estimatedTotal after each segment using the running average segment size.",
    ],
  },
  {
    id: "D.4",
    title: "Foreground service + Notifications (REVIEW-5 M20-M30 fixes)",
    status: "planned",
    days: "2-3 days",
    goal: "The foreground service (with the SYNCHRONOUS startForeground pattern — M20) + the new notification design (Coil 3 thumbnails — M21+M22, dual channels, lock-screen action visibility — M30).",
    color: "var(--c-danger)",
    tasks: [
      "DownloadService (M20): startForeground SYNCHRONOUSLY in onStartCommand (copy ExtensionInstallService.kt:69 — startForegroundCompat(buildPlaceholderNotification()) BEFORE any coroutine work). M22: queueCollector runs on Dispatchers.IO + wraps startForeground/notify in withContext(Dispatchers.Main). M24: notificationManager: NotificationManagerCompat field. M25: implement KoinComponent. M27: onTimeout override (6-hour dataSync cap, API 35+). M28: onTaskRemoved override (re-launch for aggressive OEMs).",
      "M21+M22: downloadCover uses Coil 3 (NOT Coil 2): context.imageLoader (Coil 3 extension on PlatformContext, set as singleton in AnikutaApp.kt via coil3.SingletonImageLoader.setSafe { ... }), ImageRequest.Builder(context).data(url).size(96).build(), loader.execute(request).image?.let { image -> image.asDrawable(context).toBitmap() }. downloadCover + loadThumbnail + buildSummaryNotification are all suspend (no runBlocking).",
      "Two channels: anikuta_downloads_progress (IMPORTANCE_LOW, no sound, ongoing) for the summary + progress; anikuta_downloads_complete (IMPORTANCE_DEFAULT with sound) for completion. Sound on completion only.",
      "Summary notification (ID 9001, ongoing): shows the primary task's title + progress bar + thumbnail. Updated every 800ms. ADD Pause/Cancel action buttons. M30: .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) for lock-screen action visibility. M29: PendingIntent request codes 1001/1002 (not 1/2).",
      "Completion notification (ID taskId + 10_000): 'Download complete' + content title + cover thumbnail + auto-cancel. Error notification (ID taskId + 20_000): 'Download failed' + content title + error message + auto-cancel.",
      "Notification tap deep-links to the Downloads screen (anikuta://downloads → Nav3 DownloadsKey push).",
    ],
  },
  {
    id: "D.5",
    title: "Settings page UI (EXACT replication + the NEW Priority order section)",
    status: "planned",
    days: "3 days",
    goal: "Replicate the old project's 528-line DownloadSettingsScreen EXACTLY + ADD the new 'Priority order' collapsible section (the user's 'which dimension matters most' gap is now configurable).",
    color: "var(--c-primary)",
    tasks: [
      "Replicate EXACTLY (per 14-auto-download-engine.md §5 + 15-ui-and-bug-analysis.md Part A): the 528-line DownloadSettingsScreen.kt layout — sections, components, colors, spacings, animations. The 8 private composables (SectionContainer, CollapsibleSection, CollapsibleExtensionSection, SettingsRow, ToggleRow, SliderRow, FallbackToggle, SegmentedRowLocal). The DragReorderableList component (193 lines). The DownloadVideoPickerSheet (233 lines). The DownloadsMoreEntries (38 lines).",
      "ADD the new 'Priority order — what matters most?' collapsible section ABOVE the existing 3 preference-list sections: a DragReorderableList of ['Audio', 'Quality', 'Server'] (maps to enum values when persisting) + a FallbackToggle for the global fallback (BEST_EFFORT / ASK / DO_NOT_DOWNLOAD).",
      "The settings (per 07-settings-preferences.md §2): all 17 settings — the old 15 + dimensionPriority (new — default [AUDIO, QUALITY, SERVER], DELIBERATE behavioural change per M45) + globalFallback (new — default BEST_EFFORT). Stored in PreferenceStore with reactive Flows.",
      "Fix the OLD project's bugs while replicating: the concurrentDownloads pref change must call DownloadQueue.refreshConcurrency() explicitly (the OLD project doesn't, so the new limit only takes effect after restart). The advancedMaxRetries default mismatch (code=25, UI=0..10) — set both to 10.",
    ],
  },
  {
    id: "D.6",
    title: "Downloads page UI + Episode download controls + Player integration",
    status: "planned",
    days: "4-5 days",
    goal: "The :feature:download module + the per-episode download UI + the offline short-circuit. M62: fixed the D.14 → D.6 typo (player integration is part of D.6, not a non-existent D.14).",
    color: "var(--c-secondary)",
    tasks: [
      "Create the Gradle module :feature:download — build.gradle.kts (depends on :core:download, :core:designsystem, :core:preferences, :core:video-resolver for the picker sheet, Compose, Koin), src/main/AndroidManifest.xml (empty), settings.gradle.kts registration.",
      "Port 12 files from old project: DownloadUiState.kt (re-keyed by mainId), DownloadViewModel.kt (combine flows + auto-clear after 10s with the Set<Long> guard per §A.11 fix #12), DownloadsScreen.kt (569 lines, replicate EXACTLY per §A.2), DownloadedFilesScreen.kt (206 lines), DownloadSettingsScreen.kt (528 lines + new 'Priority order' section), DownloadVideoPickerSheet.kt (233 lines), DownloadsMoreEntries.kt (37 lines), ExtensionSourceInfo.kt (16 lines, DTO), components/DragReorderableList.kt (192 lines), components/DownloadedAnimeCard.kt (183 lines), components/DownloadsEmptyState.kt (96 lines — USE THIS two-variant component per §A.11 fix #8), di/DownloadModule.kt (viewModelOf(::DownloadViewModel)).",
      "SKIP QueueRow.kt (244 lines) — dead code in old project, EpisodeRow inside AnimeSectionCard supersedes it.",
      "Wire DownloadsMoreEntries into MoreScreen.kt: item { DownloadsMoreEntries(onOpenDownloads = { navController.push(DownloadsKey) }) }. Create DownloadsKey Nav3 key in :feature:download/api.",
      "Player integration (D.6 — M62): Modify :app's nav controller — before resolving a stream, call downloadManager.isEpisodeDownloaded(mainId, episodeNumber). If true, build a WatchRequest with the local content:// URI + null headers + 'Offline' server label + downloaded subtitle URIs. Push the WatchKey with that WatchRequest. If false, fall through to the streaming resolver.",
      "Verify the player (AnikutaMPVView in :core:player) handles content:// URIs (it should — same approach as the LocalProxyServer URLs). If not, add a resolveUrlForMpv helper that converts content:// → fd://<fd> via ContentResolver.openFileDescriptor.",
      "Add an 'Offline' badge to the WatchScreen (NOT in old project — see 10-player-integration.md §10).",
    ],
  },
  {
    id: "D.7",
    title: "Quality-of-life features (the headline QoL: auto-retry)",
    status: "planned",
    days: "2-3 days",
    goal: "The QoL features from 16-quality-of-life.md — auto-retry (with RETRYING state), auto-resume on network change, auto-pause on metered network, download verification, orphan-file cleanup, auto-clear completed entries after 10s.",
    color: "var(--c-warning)",
    tasks: [
      "Auto error handling/retry (§1): the launchDownload retry loop wraps the download in a try/catch. RetryPolicy.forException (M48: uses exception TYPE matching, not string matching — e is ConnectException || e is SocketException / e is HttpException && e.code in 500..599 / e is HttpException && e.code == 429 / etc.). M50: removed the dead CancellationException branch (the catch above re-throws — unreachable). On retryable error: setRetryingStatus + delay(backoff) + re-attempt. Cap at 3 outer attempts. M19: cap composition — outer 3 × inner 2 (re-resolve) = 6 download attempts max before ERROR.",
      "HttpException (M49): class HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause) — defined LOCALLY in :core:download. HttpDownloader.downloadNormal + HlsDownloader.fetchText/downloadSegment throw it for HTTP errors.",
      "RETRYING state (M9): added to enum (UI shows 'Retrying (2/3)…' pill). M6: resetDownloadingToQueued WHERE state IN ('DOWNLOADING', 'RETRYING') — both reset on restart. M10: pause/cancel/retry accept RETRYING. M11: setRetryingStatus + setErrorStatus as private methods on DownloadQueue. M13: EpisodeDownloadState.Retrying(attempt, maxAttempts, lastError) variant. M14: bulk 'Retry all' skips RETRYING tasks (already being retried).",
      "Auto-resume on network change (§2): NetworkCallback registration in DownloadManager init. M42: onNetworkChanged uses pauseInternal (assumes mutex held) — no deadlock. DOWNLOADING → PAUSED on network loss (preserves progress). PAUSED stays PAUSED on network return (user must explicitly resume). QUEUED tasks auto-start on network return.",
      "Auto-pause on metered network (§3): the same onNetworkChanged callback — the preferences.wifiOnly().get() check determines whether to pause. Posts a one-shot notification: 'Downloads paused — Wi-Fi only is on — connect to Wi-Fi to resume.'",
      "Download verification (§4): size check (≥ 500 KB) + magic-byte check (non-fatal — failures are logged). Post-publish verification (periodic background job re-verifies file exists + size matches).",
      "Orphan-file cleanup (§5): TempDownloadCache.cleanupStale() on startup. SAF folder reconciliation via the DownloadScanner (M55: listFiles() ONCE per content folder + Map<String, DocumentFile> index). Half-written SAF file cleanup (publishToUserFolder deletes existing file before creating new). Empty content folder cleanup after deleting the last episode.",
      "Auto-clear completed entries after 10s (§6): scheduleAutoClear(taskId) — guarded by a Set<Long> (M43: autoClearScheduled.add wrapped in mutex.withLock) to prevent the leak per §A.11 fix #12.",
    ],
  },
  {
    id: "D.8",
    title: "Polish + testing (the REVIEW-6 re-review pass)",
    status: "planned",
    days: "1-2 days",
    goal: "Fix the known bugs + ship-quality polish + the REVIEW-6 re-review of the fixes.",
    color: "var(--c-secondary)",
    tasks: [
      "Fix the DOWNLOADING-on-restart bug: reset to QUEUED on startup (handled in D.1's DownloadQueue adaptation — M6: WHERE state IN ('DOWNLOADING', 'RETRYING')).",
      "Fix the Episode NNN folder-name floor bug (N/A — we use 5-digit padded E00001.5 for fractional specials — M56).",
      "Add AnimatedContent to EpisodeDownloadControl for smooth state transitions (already in D.6).",
      "Add notification action buttons (Pause all / Cancel all) to the summary notification (already in D.4).",
      "Add a deep-link from the notification tap to the Downloads screen (already in D.4).",
      "Test: enqueue a download → verify folder structure → kill app → restart → verify queue persists → play offline → delete → reinstall app → re-select folder → verify scan re-discovers everything.",
      "Test the proxy-churn bug scenario: enqueue a download → play another episode from the same source → verify the download completes via directUrl (or re-resolves within the M15 cap = 2 total download attempts).",
      "REVIEW-6 (recommended next step per DL-PLAN-FIX): a Round 6 review to verify the fixes landed correctly + didn't introduce new inconsistencies. Then Phase D.0 can start.",
    ],
  },
];

export const IMPLEMENTATION_TOTAL_ESTIMATE =
  "Total: 30-40 days (was 23-30 — grew by the REVIEW-5 consolidation pass of +3-4 days for the 72 MUST-FIX items, +1-2 days for REVIEW-6 re-review, +3-4 days for inevitable mid-implementation discoveries). Assumes one developer. D.5 + D.6 can overlap (different files); D.7 can run parallel to D.5/D.6 (different module).";

/* ---------------------------------------------------------------------------
 * Design Decisions — D1..D7 (from 13-implementation-plan.md §4)
 * ------------------------------------------------------------------------- */

export interface DesignDecision {
  id: string;
  question: string;
  options: string[];
  recommendation: string;
  rationale: string;
}

export const DESIGN_DECISIONS: DesignDecision[] = [
  {
    id: "D1",
    question: "Persistence — JSON-in-SharedPrefs OR SQLDelight?",
    options: [
      "Option A: Replicate the old project's JSON-in-SharedPreferences approach (simpler, no DB schema changes, but ignores the already-existing tables).",
      "Option B1: SQLDelight with separate columns (lean — queryable, composite key uniqueness via index, no JSON parsing on every read).",
      "Option B2: SQLDelight with denormalized JSON blob column (matches old project's data model but in a queryable table).",
    ],
    recommendation: "SQLDelight, Option B1 (separate columns).",
    rationale:
      "Tables already exist (download_queue, downloaded_episode). Queryable — 'all COMPLETED for contentId X' is a SQL query, not a list filter. Better story for app restarts (DB survives; can query state directly). Matches the new project's existing architecture (everything else uses SQLDelight). More verbose than B2 but queryable + no JSON parsing on every read. Requires schema migration (3.sqm) since tables already exist.",
  },
  {
    id: "D2",
    question: "SAF folder picker OR app-specific storage?",
    options: [
      "SAF (user picks a folder) — user controls location, survives app uninstall, can be on SD card.",
      "App-specific storage (context.filesDir/downloads/) — simpler, no permission flow, but doesn't survive uninstall.",
    ],
    recommendation: "SAF (replicate old project).",
    rationale:
      "The new project already declares MANAGE_EXTERNAL_STORAGE (for 'All files access' in Setup Wizard) but that's a different model. SAF is the modern, user-friendly approach and works on all API levels. SAF per-byte writes are slow (mitigated by the internal-cache-first pipeline — temp → validate → atomic publish).",
  },
  {
    id: "D3",
    question: "Foreground service — yes or no?",
    options: [
      "NO (replicate old project — runs in CoroutineScope). Works in foreground; killed on backgrounded on Android 14+.",
      "YES — DownloadService with foregroundServiceType=\"dataSync\".",
    ],
    recommendation: "YES — add DownloadService.",
    rationale:
      "Android 14+ kills background downloads aggressively (and earlier on aggressive OEMs like Xiaomi/Huawei). The FOREGROUND_SERVICE_DATA_SYNC permission is already declared. The notification is already needed (for progress) — make it the foreground notification. Implementation: DownloadService starts when the first download starts, calls startForeground(SUMMARY_ID, notification), stops when the queue empties.",
  },
  {
    id: "D4",
    question: "Reactive PreferenceStore — extend or replace?",
    options: [
      "Extend PreferenceStore with a Flow<T> API (preferenceFlow<T> helper backed by OnSharedPreferenceChangeListener).",
      "Use a different mechanism (MutableStateFlow per setting).",
    ],
    recommendation: "Extend PreferenceStore with a Flow<T> API.",
    rationale:
      "The new project's PreferenceStore is non-reactive (just getString/putString/...). The old project's UI relies heavily on Preference.changes(): Flow<T> for live updates. Simpler approach: a preferenceFlow<T>(key, default) helper that returns a Flow<T> backed by OnSharedPreferenceChangeListener. Small addition to :core:preferences, benefits the whole app (not just downloads).",
  },
  {
    id: "D5",
    question: 'Episode-key format — plain string or composite "$contentId|$episodeNumber"?',
    options: [
      "Plain string (current new-project stub).",
      'Composite "$contentId|$episodeNumber" (3-decimal format, e.g. "al:154587|1.000") — old project\'s approach.',
    ],
    recommendation: 'Use the old project\'s composite key ("$contentId|$episodeNumber").',
    rationale:
      "Source-independent — survives extension source switches (the new source has a different episodeUrl but the same episodeNumber). This is the 'source-switching fix' from the old project (Phase 6, ADR-050).",
  },
  {
    id: "D6",
    question: "HLS support — yes or no?",
    options: [
      "NO — direct videos only (many extensions return HLS URLs, so this would fail on a lot of content).",
      "YES — port HlsDownloader as-is (pure Kotlin, no ffmpeg).",
    ],
    recommendation: "YES — port HlsDownloader as-is.",
    rationale:
      "Many anime extensions return HLS URLs. HlsDownloader is pure Kotlin (no ffmpeg dependency), handles unencrypted HLS (the common case), and includes PNG-header stripping for anti-scraping CDNs (megaplay.buzz, kotocdn.site). Encrypted HLS is rejected with a clear error (future 1DM/ffmpeg method would handle it).",
  },
  {
    id: "D7",
    question: "Advanced (multi-threaded) method — yes or no?",
    options: [
      "NO — Normal method + HLS covers most cases.",
      "YES for parity, but Phase D.1.5 (defer).",
    ],
    recommendation: "YES for parity, but make it Phase D.1.5.",
    rationale:
      "The Advanced method (AdvancedHttpDownloader ~400 lines + DownloadResumeManager ~117 lines) is complex — Range probe, N parallel chunks, per-chunk .part files, resume metadata JSON. Ship the Normal method first, add Advanced as a follow-up. The Normal method works for direct MP4/MKV + HLS; Advanced only helps for large direct-video files on slow servers.",
  },
];

/* ---------------------------------------------------------------------------
 * Risk Register (from 13-implementation-plan.md §8)
 * ------------------------------------------------------------------------- */

export interface RiskEntry {
  risk: string;
  likelihood: "Low" | "Medium" | "High";
  mitigation: string;
}

export const RISKS: RiskEntry[] = [
  {
    risk: "SAF provider quirks on specific OEMs (Samsung, Xiaomi)",
    likelihood: "Medium",
    mitigation:
      "Test on multiple devices; fall back to app-specific storage if SAF fails.",
  },
  {
    risk: "Foreground service restrictions on Android 14+",
    likelihood: "High",
    mitigation:
      'Use foregroundServiceType="dataSync"; declare permission; call startForeground within 5s.',
  },
  {
    risk: "HLS segment download failures (flaky CDNs)",
    likelihood: "Medium",
    mitigation:
      "Add per-segment retry (the old project doesn't have this — single failure fails the whole download).",
  },
  {
    risk: "MPV can't play content:// URIs directly",
    likelihood: "Low",
    mitigation: "Verify in D.5; if needed, add resolveUrlForMpv helper.",
  },
  {
    risk: "Large queue (100+ tasks) slows SharedPreferences JSON",
    likelihood: "Low",
    mitigation: "Mitigated by using SQLDelight (Option B).",
  },
  {
    risk: "Concurrent-downloads pref change doesn't take effect immediately",
    likelihood: "Medium",
    mitigation:
      "Add a Flow collector in DownloadQueue that calls refreshConcurrency() on pref changes.",
  },
  {
    risk: "Stale DOWNLOADING tasks on restart",
    likelihood: "High",
    mitigation: "Reset to QUEUED on startup (handled in D.1).",
  },
  {
    risk: "POST_NOTIFICATIONS denied on Android 13+",
    likelihood: "Medium",
    mitigation:
      "Graceful fallback — UI still works; notifier's try/catch swallows the failure.",
  },
];

/* ---------------------------------------------------------------------------
 * Old-Project Bugs to Avoid (from 00-overview.md §6)
 * ------------------------------------------------------------------------- */

export interface OldProjectBug {
  title: string;
  body: string;
  fixInNewProject: string;
  color: string;
}

export const OLD_PROJECT_BUGS: OldProjectBug[] = [
  {
    title: "No foreground Service — background downloads get killed",
    body: "The old project runs downloads in a CoroutineScope(SupervisorJob + Dispatchers.IO) with no startForeground. The FOREGROUND_SERVICE_DATA_SYNC permission is declared but used by ExtensionInstallService only. On Android 14+ (and aggressive OEMs like Xiaomi/Huawei), background downloads are killed when the app is backgrounded.",
    fixInNewProject:
      "Add DownloadService (foregroundServiceType=\"dataSync\") + startForeground within 5s. See Design Decision D3 + Phase D.1.",
    color: "var(--c-danger)",
  },
  {
    title: "DOWNLOADING tasks stay DOWNLOADING forever on restart",
    body: "App is killed mid-download → task is persisted as DOWNLOADING in SharedPreferences. Next launch: the task is in the list as-is, but no tryStartNext() is called automatically on construction. tryStartNext only picks QUEUED tasks — so the orphaned DOWNLOADING task never restarts. The user has to manually pause+resume it.",
    fixInNewProject:
      "Reset any DOWNLOADING tasks to QUEUED on startup (handled in D.1's DownloadQueue adaptation — add a startup query: UPDATE download_queue SET state = 'QUEUED' WHERE state = 'DOWNLOADING').",
    color: "var(--c-danger)",
  },
  {
    title: "Concurrent-downloads pref doesn't take effect immediately",
    body: "DownloadSettingsScreen's slider just calls preferences.concurrentDownloads().set(...) — there's no explicit refreshConcurrency call. The new limit only takes effect on app restart (when DownloadQueue is reconstructed) or when a task happens to complete (triggering tryStartNext with the OLD semaphore that has fewer permits than desired).",
    fixInNewProject:
      "Add a Flow collector in DownloadQueue.init that calls refreshConcurrency() on concurrentDownloads pref changes. (D.6 polish task.)",
    color: "var(--c-warning)",
  },
  {
    title: "advancedMaxRetries default mismatch — code says 25, UI says 0..10",
    body: "DownloadPreferences.kt:148 sets the default to 25. DownloadSettingsScreen.kt:169-170 clamps the slider to 0..10. A user who never opens settings gets 25 retries per chunk — could mean very long waits on flaky servers (25 retries × 1s delay = 25s per chunk).",
    fixInNewProject:
      "Set both code + UI to 10. (D.6 polish task.)",
    color: "var(--c-warning)",
  },
  {
    title: 'Episode NNN folder-name floor — .5 specials collide',
    body: 'DownloadStorageProvider.episodeFolderName uses .toInt() (floor). So 12.5 → "Episode 012", colliding with regular EP 12. Special episodes (S1.E5 = episode 5.5) would overwrite or be overwritten by their integer counterparts.',
    fixInNewProject:
      'Use "Episode 012" for integers, "Episode 012.5" for non-integers. (D.6 polish task.)',
    color: "var(--c-warning)",
  },
  {
    title: "HlsDownloader always picks the FIRST variant — no quality picker",
    body: "HlsDownloader.pickFirstVariant picks the first variant URL in a master playlist (typically the highest bandwidth, but not guaranteed). There's no UI to let the user pick a different quality for HLS streams.",
    fixInNewProject:
      "Acceptable for now — HLS quality picker is a future enhancement. Document the limitation.",
    color: "var(--c-secondary)",
  },
  {
    title: "DownloadsScreen groups queue by anime.title (not contentId)",
    body: "DownloadsScreen.kt groups the queue by anime.title — would conflate two different anime with the same title. The 'Downloaded' section groups by DownloadedAnimeKey(contentId, ...) correctly though. Minor inconsistency.",
    fixInNewProject:
      "Group by contentId everywhere (or DownloadedAnimeKey) — eliminate the title-based grouping in the queue section.",
    color: "var(--c-secondary)",
  },
  {
    title: "KDoc says AnimatedContent but code doesn't use it",
    body: "EpisodeDownloadControl.kt:38 mentions 'Uses AnimatedContent for smooth state transitions' but there's NO AnimatedContent import or call in the file. Doc-vs-code mismatch — state transitions are abrupt.",
    fixInNewProject:
      "Either remove the KDoc claim OR actually add AnimatedContent. (D.6 polish task — recommendation: add it for smoother UX.)",
    color: "var(--c-secondary)",
  },
];

/* ---------------------------------------------------------------------------
 * Footer nav (used by page)
 * ------------------------------------------------------------------------- */

export const DOWNLOADS_PLAN_NAV_FOOTER = {
  prev: { label: "← Planning", href: "/planning/" },
  next: { label: "Phase D →", href: "/phase-d/" },
};

/* ---------------------------------------------------------------------------
 * REVIEW FINDINGS — the 5 review rounds + the 72-item fix pass (DL-PLAN-FIX)
 * Source: REVIEW-1-storage-db.md · REVIEW-2-autodl.md · REVIEW-3-queue-downloaders.md
 *         REVIEW-4-notifications-ui.md · REVIEW-5-final.md
 * ------------------------------------------------------------------------- */

export interface ReviewRound {
  id: string;
  focus: string;
  criticals: number;
  importants: number;
  findings: string;
  color: string;
}

export const REVIEW_ROUNDS: ReviewRound[] = [
  {
    id: "DL-REVIEW-1",
    focus: "Storage + DB schema (04-storage-paths.md + 11-db-schema.md)",
    criticals: 5,
    importants: 9,
    findings:
      "Verified the new SAF/data.json system against source. Found: C1 — data.json contentId was a 2-section 'anilist:101522' (would break the idx_content_content_id duplicate-detection index); C2 — ContentDataJson didn't store the full FK set (upsertFromDataJson would NULL the content table's FK columns); C3+M1 — the .sqm migration plan was wrong (the project has ZERO .sqm files); C4+M2 — DatabaseDriverFactory.create() doesn't need a migrations arg for this rewrite; C5+M3 — getDownloadedMainIds used DISTINCT + GROUP BY (illegal bare columns in SQLite).",
    color: "var(--c-danger)",
  },
  {
    id: "DL-REVIEW-2",
    focus: "Auto-download priority engine + settings (14-auto-download-engine.md + 07-settings-preferences.md)",
    criticals: 2,
    importants: 6,
    findings:
      "Traced the OLD selectBestVideo algorithm — confirmed serverFallback is NEVER READ (dead code) + the implicit priority was INCONSISTENT (audio > quality at the check layer; server > audio > quality at the iteration layer — neither matches). C1+M15 — the new draft's re-resolve recursion was UNBOUNDED (StackOverflowError risk); C2+M44 — globalFallback fired on sortedCandidates.isEmpty() (useless UX — empty picker). Plus 6 importants: M45 dimensionPriority default claim, M46+M47 Preference<T> interface regression, M48 string matching on exception messages, M49 HttpException not defined locally, M50 dead CancellationException branch, M17 dead autoDownloadEngine DI param on ReResolver.",
    color: "var(--c-warning)",
  },
  {
    id: "DL-REVIEW-3",
    focus: "Queue management + downloaders (02-queue-management.md + 05-downloaders.md)",
    criticals: 5,
    importants: 15,
    findings:
      "C1+M15 — HttpDownloader re-resolve recursion unbounded (no reResolveAttempts param). C2+M31 — recentRatios: ArrayDeque<Float>(5) was missing from DynamicProgressTracker.compute signature (wouldn't compile). C3+M32 — estimatedTotal computed once + never refined (the 95→100 jump for variable-bitrate HLS). C4+M33 — HLS per-segment retry wrote partial bytes then appended (corruption that verifyVideoMagicBytes wouldn't catch). C5+M49 — HttpException unresolved (RetryPolicy HTTP branches were dead code). Plus 15 importants across M34-M43: per-tick scope.launch (60,000+ coroutines), missing persist-across-pause-resume, finally block not distinguishing CancellationException, missing DynamicProgressTracker.complete() wiring, HLS HEAD probe rejected by anti-scraping CDNs, etc.",
    color: "var(--c-danger)",
  },
  {
    id: "DL-REVIEW-4",
    focus: "Notifications + foreground service + UI (06-notifications-foreground-service.md + 08 + 09 + 15)",
    criticals: 8,
    importants: 12,
    findings:
      "C1+M20 — startForeground was NOT synchronous (ForegroundServiceDidNotStartInTimeException on Android 12+). C2+M21 — downloadCover used Coil 2 API (project is Coil 3 — wouldn't compile). C3+M22 — startForeground/notify called on Dispatchers.IO (notification APIs are main-thread-only — would crash). C4+M23 — ACCESS_NETWORK_STATE not declared (SecurityException on registerNetworkCallback). C5+M24 — notificationManager field undefined. C6+M25 — KoinComponent missing (by inject<>() wouldn't resolve). C7+M6 — resetDownloadingToQueued SQL only reset DOWNLOADING (RETRYING tasks stuck forever after a crash). C8+M42 — onNetworkChanged deadlocked (mutex.withLock { pause(it.id) } where pause ALSO acquires the mutex = non-reentrant Mutex deadlock). Plus 12 importants: M26 missing ic_pause/ic_cancel drawables, M27+M28 onTimeout/onTaskRemoved handlers, M29 PendingIntent request codes 1/2 (collision risk), M30 lock-screen visibility, etc.",
    color: "var(--c-danger)",
  },
  {
    id: "DL-REVIEW-5",
    focus: "FINAL — the consolidated 72-item MUST-FIX list + cross-doc consistency",
    criticals: 18,
    importants: 36,
    findings:
      "Carried over all 18 unresolved CRITICALs from Reviews 1-4 (none had been addressed). Added 3 NEW CRITICALs + 36 IMPORTANTs across the 15 plan docs. The consolidated list lives in REVIEW-5-final.md §8 (M1-M72). M61: required a NEW §6.1 'Review Findings' section in 13-implementation-plan.md to consolidate all 72 items as explicit action items grouped by phase (A-I) with M-numbers + doc cross-references. M62: fixed D.14 → D.6 typo. M63: removed the (implicit) parenthetical on ACCESS_NETWORK_STATE. M64: ResolveContext captures 7 fields (was 5). M65: DownloadScanner deps include ContentRepository + AnilistDetailRepository. M66-M72: cross-doc consistency fixes (ERROR vs Failed naming, onNetworkChanged divergence, §14.1 vs §14.3 implementation contradiction, dimensionPriority 'preserves old behaviour' claim, etc.).",
    color: "var(--c-danger)",
  },
];

export interface ReviewHighImpactFix {
  rank: number;
  mNumber: string;
  title: string;
  before: string;
  after: string;
  why: string;
  color: string;
}

export const REVIEW_TOP_5_FIXES: ReviewHighImpactFix[] = [
  {
    rank: 1,
    mNumber: "M15",
    title: "Bounded re-resolve recursion in HttpDownloader.downloadNormal",
    before:
      "The draft's downloadNormal caught IOException + recursed on downloadVideoToCache (5 args). The recursion was UNBOUNDED — a proxy-churn loop would recurse until StackOverflowError. Also the catch block referenced the wrong method (5-arg downloadVideoToCache vs the 6-arg downloadNormal that exists).",
    after:
      "Added reResolveAttempts: Int = 0 parameter to downloadNormal. Cap at MAX_RE_RESOLVE_ATTEMPTS = 1 (= 2 total download attempts: 1 initial + 1 re-resolve). On cap exceeded, throw DownloadException(\"Proxy URL died after $N re-resolve attempt(s) — the extension's proxy server is being churned by another playback. Original cause: …\", e). Truncates the temp file before the recursive call (the fresh URL is a NEW proxy on a different port — existing bytes may not be reusable). §14.1 + §11.3 now agree on the catch-block body.",
    why:
      "Prevents the SINGLE most catastrophic runtime failure: a StackOverflowError that takes down the whole process. The proxy-churn bug (user plays another episode from the same source while a download is in-flight) was guaranteed to trigger this recursion.",
    color: "var(--c-danger)",
  },
  {
    rank: 2,
    mNumber: "M20",
    title: "Synchronous startForeground in DownloadService.onStartCommand",
    before:
      "The draft's DownloadService called startForeground from inside a coroutine (queueCollector.launch { ... startForeground(...) }). On Android 12+, the system enforces a 5-second contract: startForeground MUST be called within 5s of ContextCompat.startForegroundService. A coroutine that loads the queue + builds the notification first takes 100-500ms — usually under 5s, but on cold start or with a large queue, it can blow the budget. Result: ForegroundServiceDidNotStartInTimeException crashes the app.",
    after:
      "Read ExtensionInstallService.kt:58-90 — confirmed the canonical pattern: startForegroundCompat(\"Installing extension…\") SYNCHRONOUSLY at line 69 before any coroutine work. Rewrote DownloadService to: implement KoinComponent (M25); declare notificationManager field (M24); use Dispatchers.IO for the queueCollector + withContext(Dispatchers.Main) for startForeground/notify (M22); call startForegroundCompat(buildPlaceholderNotification()) SYNCHRONOUSLY in onStartCommand. The queueCollector now only UPDATES the notification via notificationManager.notify(...) — never calls startForeground itself.",
    why:
      "Prevents ForegroundServiceDidNotStartInTimeException — the second most catastrophic runtime failure (after M15). The placeholder notification satisfies the 5-second contract regardless of queue state. The pattern is copied verbatim from the project's own ExtensionInstallService (proven to work).",
    color: "var(--c-danger)",
  },
  {
    rank: 3,
    mNumber: "M1 + M2",
    title: "The migration plan — direct .sq edit, no .sqm file",
    before:
      "The draft said 'Requires a schema migration (3.sqm) since the tables already exist.' This was based on the assumption that the project uses SQLDelight's incremental .sqm migration system. VERIFIED: the project has ZERO .sqm files — SQLDelight 2.x derives the v1 schema directly from the .sq files' CREATE TABLE IF NOT EXISTS statements. DatabaseDriverFactory.create() does NOT pass a migrations = ... arg.",
    after:
      "Picked option (a): edit the .sq files directly. The new schema becomes v1. Existing dev installs wipe app data once (adb shell pm clear com.confused.anikuta). DatabaseDriverFactory.create() does NOT need a migrations = ... arg for this rewrite — but the NEXT schema change MUST pair with a real 1.sqm + the factory update. Added §3.4 documenting this forward-looking pattern. Added §3.5 noting the stale video_url / content_id after source-switch issue.",
    why:
      "Following the draft's plan would have either (a) added a 3.sqm file that SQLDelight would refuse to compile (no prior 1.sqm/2.sqm — there's no v2 schema to migrate from), OR (b) added an unnecessary migrations arg to DatabaseDriverFactory that does nothing. Both would have caused a confusing build failure. The direct-.sq-edit pattern is the correct SQLDelight 2.x approach.",
    color: "var(--c-danger)",
  },
  {
    rank: 4,
    mNumber: "M49",
    title: "HttpException defined LOCALLY in :core:download",
    before:
      "The draft's HttpDownloader.downloadNormal threw a generic DownloadException(\"HTTP $code…\") for HTTP errors — no code field. RetryPolicy.forException had branches for HTTP 5xx/429/4xx that matched on e.message?.contains(\"HTTP 5\") — fragile string matching that would fail in different locales / JVM versions / CDN error messages. The HTTP branches were effectively DEAD CODE.",
    after:
      "Added class HttpException(val code: Int, message: String, cause: Throwable? = null) : DownloadException(message, cause) to :core:download — does NOT depend on :core:source-api (where a same-named class lives). HttpDownloader.downloadNormal now throws HttpException(response.code, \"HTTP $code for video URL\"). RetryPolicy.forException matches on `e is HttpException && e.code in 500..599` (type matching — M48). The HTTP 5xx/429/4xx branches are no longer dead code. Throw sites: HttpDownloader.downloadNormal + HlsDownloader.fetchText + HlsDownloader.downloadSegment + AdvancedHttpDownloader.probeServer.",
    why:
      "Without this fix, the entire auto-retry feature (QoL §1) was broken for HTTP errors — every HTTP 5xx would fall through to the 'unknown error' branch (Policy(false, 0, { 0 }) — no retry). The user's flaky-CDN complaint ('download fails when the network blips') would NOT have been fixed. Also keeps :core:download's dependency graph minimal (no :core:source-api dependency).",
    color: "var(--c-warning)",
  },
  {
    rank: 5,
    mNumber: "M9 + M11 + M6 + M12 + M13 + M14",
    title: "RETRYING state propagated everywhere (state machine + queue + DB schema + UI)",
    before:
      "The QoL doc proposed a 'Retrying' state but: (1) the state machine doc didn't have it; (2) the queue's setRetryingStatus + setErrorStatus methods were UNDEFINED (M11 — wouldn't compile); (3) the DB's resetDownloadingToQueued SQL only reset DOWNLOADING (M6 — RETRYING tasks stuck forever after a crash); (4) the doc proposed a sealed interface with `data class RETRYING(attempt, maxAttempts, lastError)` — but enum constants can't carry per-instance data (M12); (5) the UI's EpisodeDownloadState didn't have a Retrying variant (M13); (6) bulk 'Retry all' would re-trigger the engine on tasks already being retried (M14).",
    after:
      "M12: picked enum class DownloadStatus (UPPERCASE constants — matches the OLD project). The stub DownloadState.kt (sealed interface, PascalCase variants — Failed/Queued/…) is DELETED in Phase D.0. The new DownloadStatus.kt has 7 constants: QUEUED, DOWNLOADING, RETRYING, PAUSED, COMPLETED, ERROR, CANCELLED. The retry metadata (retryAttempt / retryMaxAttempts / lastError) lives on DownloadTask. M11: setRetryingStatus + setErrorStatus defined as private methods on DownloadQueue. M6: resetDownloadingToQueued SQL is `WHERE state IN ('DOWNLOADING', 'RETRYING')`. M8: state column comment lists all 7 states. M10: pause/cancel/retry accept RETRYING. M13: EpisodeDownloadState.Retrying(attempt, maxAttempts, lastError) variant added. M14: bulk 'Retry all' skips RETRYING.",
    why:
      "Without RETRYING properly propagated, a crash mid-retry would leave the task stuck forever (the startup migration wouldn't reset it). The user's 'auto-retry is broken' complaint would persist. The UI would show 'Queued' for a task that's actually retrying (confusing — the user might tap Retry, re-triggering the engine on a task already being retried).",
    color: "var(--c-warning)",
  },
];

export const REVIEW_FIX_BREAKDOWN = `# The 72 MUST-FIX items, grouped by phase (DL-PLAN-FIX consolidation pass):

A. Migration / DB schema (M1-M8)                    → Phase D.0 (blocks everything)
   M1+M2 direct .sq edit (no .sqm) · M3 MAX for bare cols · M4 6-section contentId
   M5 full FK set · M6 resetDownloadingToQueued includes RETRYING · M7 updateDownloadContentId
   M8 state column comment lists all 7 states

B. State machine + RETRYING propagation (M9-M14)    → Phase D.1/D.2/D.7
   M9 RETRYING enum + diagram + transitions · M10 pause/cancel/retry accept RETRYING
   M11 setRetryingStatus + setErrorStatus defined · M12 pick enum class (UPPERCASE)
   M13 EpisodeDownloadState.Retrying variant · M14 bulk 'Retry all' skips RETRYING

C. Proxy-churn fix (M15-M19)                        → Phase D.2
   M15 reResolveAttempts cap at 1 · M16 §14.1 + §11.3 agree on the catch body
   M17 remove autoDownloadEngine from ReResolver (dead DI) · M18 cap = 1 re-resolve
   M19 cap composition: outer 3 × inner 2 = 6 download attempts max

D. Foreground service + notifications (M20-M30)     → Phase D.4
   M20 synchronous startForeground · M21 Coil 3 · M22 Dispatchers.IO + withContext(Main)
   M23 CREATE :core:download manifest · M24 notificationManager field · M25 KoinComponent
   M26 ic_pause + ic_cancel drawables · M27 onTimeout (6h cap) · M28 onTaskRemoved
   M29 PendingIntent request codes 1001/1002 · M30 VISIBILITY_PUBLIC

E. Queue management + progress tracking (M31-M43)   → Phase D.3
   M31 recentRatios ArrayDeque(5) · M32 estimatedTotal refined per segment
   M33 HLS per-segment ByteArrayOutputStream · M34 INLINE _tasks.value + Channel
   M35 intermediate onProgress ticks (96/97/98/99%) · M36 DynamicProgressTracker.complete()
   M37 finally distinguishes CancellationException · M38 persist prevTotal/prevEstimate
   M39 1-byte Range GET probe (not HEAD) · M40 restored sanity check if-branch
   M41 mutateTask is suspend fun · M42 onNetworkChanged uses pauseInternal (no deadlock)
   M43 scheduleAutoClear autoClearScheduled.add wrapped in mutex.withLock

F. Auto-download engine + settings (M44-M52)        → Phase D.2/D.5
   M44 globalFallback fires on match-quality (not on empty) · M45 dimensionPriority default
   is a DELIBERATE change · M46 Preference<T> 7 methods · M47 removed onStart emit
   M48 RetryPolicy type matching (not string matching) · M49 HttpException local
   M50 removed dead CancellationException branch · M51 (resolved by M49) · M52 §7.5 in
   01-workflow-click-to-queue.md noting the new 5-step AutoDownloadEngine

G. Storage (M53-M60)                                → Phase D.1
   M53 same-title collision algorithm · M54 .nomedia · M55 listFiles() ONCE per content
   M56 non-rounding fractional formatter · M57 audio/ in the scan list · M58 fallback
   to 'always scan' if lastModified returns 0 · M59 hasSpaceFor check · M60 stale
   video_url after source switch (future enhancement)

H. Implementation plan coherence (M61-M65)          → 13-implementation-plan.md §6.1
   M61 (this Review Findings section) · M62 D.14 → D.6 typo · M63 remove (implicit) on
   ACCESS_NETWORK_STATE · M64 ResolveContext 7 fields · M65 DownloadScanner deps

I. Cross-doc consistency (M66-M72)                  → resolved by the fixes above
   M66 ERROR (not Failed) · M67 onNetworkChanged canonical in 02 · M68 §14.1 DIRECT lookup
   M69 §14.1 + §11.3 catch body aligned · M70 'preserves old behaviour' claim removed
   M71 Preference<T> 7 methods + no onStart · M72 resetDownloadingToQueued claim TRUE`;

export const REVIEW_VERDICT = {
  verdict:
    "ALL 72 MUST-FIX items applied across 15 plan docs. The plan is now internally consistent + implements every CRITICAL + IMPORTANT fix from the 5 review rounds. The implementation plan (13-implementation-plan.md §6.1) lists every fix as an explicit action item. Day estimate grew from 23-30 to 30-40 to absorb the consolidation pass.",
  nextStep:
    "A Round 6 review (DL-REVIEW-6) to verify the fixes landed correctly + didn't introduce new inconsistencies. Then Phase D.0 can start.",
  highestImpact:
    "M15 (bounded re-resolve recursion) + M20 (synchronous startForeground) — these two alone prevent the two most catastrophic runtime failures: StackOverflowError + ForegroundServiceDidNotStartInTimeException.",
  color: "var(--c-success)",
};

/* ---------------------------------------------------------------------------
 * AUTO-DOWNLOAD ENGINE — the 5-step priority pipeline (14-auto-download-engine.md)
 * ------------------------------------------------------------------------- */

export const AUTO_DOWNLOAD_PIPELINE = `# The 5-step pure-function pipeline (14-auto-download-engine.md §6.2):

┌─────────────────────────────────────────────────────────────────────────┐
│ Step 1 — flatten(servers, qualityPrefs, audioPrefs, serverPrefs)         │
│   → List<Candidate>                                                      │
│   Walks the resolved tree (List<ResolverServer> → each has                │
│   List<ResolverAudioVersion> → each has List<ResolverVideo>) and emits   │
│   one Candidate per leaf video, carrying the video + serverName +         │
│   audioLabel + serverRank + audioRank + qualityRank + booleans           │
│   (isServerPreferred / isAudioPreferred / isQualityPreferred).           │
└─────────────────────────────────────────────────────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Step 2 — rank(candidates, dimensionPriority)                             │
│   → List<Candidate> (sorted ascending by the rank tuple)                 │
│   For each candidate, build a rank tuple in the user's dimension-priority │
│   order. E.g. dimensionPriority = [AUDIO, QUALITY, SERVER] → the tuple  │
│   is (audioRank, qualityRank, serverRank). Sort all candidates by this   │
│   tuple (ascending — lower rank = better). The first candidate is the    │
│   strict best.                                                           │
└─────────────────────────────────────────────────────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Step 3 — applyFallbacks(ranked, dimensionPriority, prefs, fallbacks)     │
│   → FallbackDecision (Continue / ShowPicker / Error)                     │
│   For each dimension IN dimension-priority order, check whether ANY      │
│   candidate has its preferred value for that dimension. If not, consult  │
│   the per-dimension fallback: TRY_NEXT → continue; ASK → ShowPicker;     │
│   DO_NOT_DOWNLOAD → Error. This generalizes the OLD Steps 1+2 to all     │
│   three dimensions + applies them in the user-defined priority order.    │
│   FIXES the silent serverFallback dead-code bug (now ALL 3 fallbacks     │
│   are consulted).                                                        │
└─────────────────────────────────────────────────────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Step 4 — pick(sortedCandidates)                                          │
│   → Candidate? (the first sorted candidate, or null if empty)            │
│   The candidate with the lowest rank tuple wins. The rank tuple          │
│   naturally handles conflicts: if dimension priority is [AUDIO, QUALITY,  │
│   SERVER] and the user's top audio is available on multiple candidates,  │
│   the tiebreaker is quality (next in priority), then server.             │
└─────────────────────────────────────────────────────────────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Step 5 — globalFallback(picked, sortedCandidates, globalFallback)        │
│   → Selection (Selected / ShowPicker / NoMatch)                          │
│   REVIEW-5 M44: fires based on the picked candidate's MATCH QUALITY,     │
│   NOT on sortedCandidates.isEmpty().                                     │
│   • If sortedCandidates is empty → always NoMatch / Error.                │
│   • Otherwise compute isPerfectMatch = audioRank == 0 &&                 │
│     qualityRank == 0 && serverRank == 0.                                 │
│   • If isPerfectMatch → Selected (success).                              │
│   • If NOT isPerfectMatch → fire globalFallback:                         │
│     - BEST_EFFORT → Selected (the best-effort pick).                     │
│     - ASK → ShowPicker(sortedCandidates) — non-empty picker.             │
│     - DO_NOT_DOWNLOAD → NoMatch (fail when prefs can't be perfectly met).│
└─────────────────────────────────────────────────────────────────────────┘`;

export const AUTO_DOWNLOAD_SETTINGS = `# The NEW dimensionPriority preference (14-auto-download-engine.md §6.1.1):

enum class PreferenceDimension {
    AUDIO,
    QUALITY,
    SERVER,
}

fun dimensionPriority(): Preference<List<PreferenceDimension>> =
    store.getObject(
        KEY_DIMENSION_PRIORITY,
        // REVIEW-5 M45: the OLD draft claimed this default "preserves old behaviour" — that
        // was FALSE. The OLD project's effective priority was INCONSISTENT (check-layer:
        // AUDIO > QUALITY; iteration-layer: SERVER > AUDIO > QUALITY). Neither matches
        // [AUDIO, QUALITY, SERVER]. This default is a DELIBERATE behavioural change reflecting
        // typical user intent (audio is usually the most important for sub/dub preferences).
        // Users who relied on the OLD iteration order (server-first) can flip to
        // [SERVER, AUDIO, QUALITY] in Settings.
        DEFAULT_DIMENSION_PRIORITY,  // [AUDIO, QUALITY, SERVER] — DELIBERATE change (see M45)
        { json.encodeToString(ListSerializer(...), it) },
        { ... decode ... },
    )

companion object {
    val DEFAULT_DIMENSION_PRIORITY = listOf(
        PreferenceDimension.AUDIO,
        PreferenceDimension.QUALITY,
        PreferenceDimension.SERVER,
    )
}

# The NEW globalFallback preference (14-auto-download-engine.md §6.1.2):

enum class GlobalFallbackStrategy {
    BEST_EFFORT,        // fall back to ANY available video (old Step 4 behaviour)
    ASK,                // show the picker sheet
    DO_NOT_DOWNLOAD,    // fail with an error
}

fun globalFallback(): Preference<GlobalFallbackStrategy> =
    store.getEnum(KEY_GLOBAL_FALLBACK, GlobalFallbackStrategy.BEST_EFFORT)

# So the user has FOUR reorderable lists in the new settings UI:
#   1. Dimension priority (new) — the 3 dimensions in order of importance.
#   2. Preferred audio values (existing).
#   3. Preferred quality values (existing).
#   4. Preferred server values per extension (existing).
# Plus the NEW global fallback toggle (BEST_EFFORT / ASK / DO_NOT_DOWNLOAD).`;

export const AUTO_DOWNLOAD_WORKED_EXAMPLE = `# Worked example — dimensionPriority = [AUDIO, QUALITY, SERVER] (audio matters most)
# (14-auto-download-engine.md §6.3)

User settings:
  dimensionPriority = [AUDIO, QUALITY, SERVER]   ← user says audio matters most
  audioPrefs        = ["DUB", "SUB"]
  qualityPrefs      = ["1080p", "720p"]
  serverPrefs       = ["Streamtape", "Vidstreaming"]
  audioFallback     = TRY_NEXT, qualityFallback = TRY_NEXT, serverFallback = TRY_NEXT
  globalFallback    = BEST_EFFORT

Resolved video tree (same as the OLD project's §3.5 example):
  Streamtape (user's #1 server):
    SUB:  1080p, 720p       (no DUB)
  Vidstreaming (user's #2 server):
    SUB:  720p
    DUB:  1080p             ← user's preferred combo is HERE

Step 1 — Flatten:
  ┌───────────┬─────────────┬───────┬─────────┬────────────┬───────────┬────────────┐
  │ Candidate │ server      │ audio │ quality │ serverRank │ audioRank │ qualityRank│
  ├───────────┼─────────────┼───────┼─────────┼────────────┼───────────┼────────────┤
  │ A         │ Streamtape  │ SUB   │ 1080p   │ 0          │ 1         │ 0          │
  │ B         │ Streamtape  │ SUB   │ 720p    │ 0          │ 1         │ 1          │
  │ C         │ Vidstreaming│ SUB   │ 720p    │ 1          │ 1         │ 1          │
  │ D         │ Vidstreaming│ DUB   │ 1080p   │ 1          │ 0         │ 0          │
  └───────────┴─────────────┴───────┴─────────┴────────────┴───────────┴────────────┘

Step 2 — Sort by (audioRank, qualityRank, serverRank):
  1. D — (0, 0, 1)   ← best (top audio, top quality, #2 server)
  2. A — (1, 0, 0)   (SUB, 1080p, Streamtape)
  3. B — (1, 1, 0)   (SUB, 720p, Streamtape)
  4. C — (1, 1, 1)   (SUB, 720p, Vidstreaming)

Step 3 — Per-dim fallback checks (in [AUDIO, QUALITY, SERVER] order):
  AUDIO:   topPref = "DUB".   hasTopPref = true (candidate D). ✓ continue.
  QUALITY: topPref = "1080p". hasTopPref = true (candidates A, D). ✓ continue.
  SERVER:  topPref = "Streamtape". hasTopPref = true (candidates A, B). ✓ continue.

Step 4 — Pick first: Candidate D = Vidstreaming / DUB / 1080p.

Step 5 — globalFallback: isPerfectMatch (D's audioRank=0, qualityRank=0, serverRank=1)?
         NO — serverRank=1 (Vidstreaming is the #2 server). Fire globalFallback = BEST_EFFORT.
         Result: Selected(D) — accept the best-effort pick.

═════════════════════════════════════════════════════════════════════════════
COMPARE: the OLD engine's result for the same scenario = Streamtape / SUB / 1080p (§3.5).
The NEW engine correctly picks DUB on Vidstreaming (the only server with DUB), even though
Streamtape is the user's #1 server. This is exactly what the user wants — "audio is the most
important". The OLD engine couldn't express this because server was the outermost loop AND
server had no real fallback strategy.
═════════════════════════════════════════════════════════════════════════════

# Now flip the dimension priority — [SERVER, QUALITY, AUDIO]:
# Same candidates, same prefs. Sort by (serverRank, qualityRank, audioRank):
#   1. A — (0, 0, 1)   ← best (Streamtape, 1080p, SUB)
#   2. B — (0, 1, 1)   (Streamtape, 720p, SUB)
#   3. D — (1, 0, 0)   (Vidstreaming, 1080p, DUB)
#   4. C — (1, 1, 1)   (Vidstreaming, 720p, SUB)
# Step 4 — Pick first: Candidate A = Streamtape / SUB / 1080p.
#
# The user said "server is the most important, then quality, then audio" — now the engine
# picks Streamtape/SUB/1080p. This matches the OLD engine's behaviour for this case
# (coincidentally), but now it's CONFIGURABLE + the resolution path is consistent
# (no separate "check then iterate" layers).`;

export const AUTO_DOWNLOAD_CUSTOMIZABILITY = `# Why this stays "highly customizable for future changes" (14-auto-download-engine.md §6.4)

The pipeline is structured as a sequence of pure functions, each testable in isolation:

  object AutoDownloadEngine {
      // Pure: tree → flat candidate list.
      fun flatten(servers, qualityPrefs, audioPrefs, serverPrefs): List<Candidate>

      // Pure: candidates + dimensionPriority → sorted candidates.
      fun rank(candidates, dimensionPriority): List<Candidate>

      // Pure: ranked candidates + per-dim fallbacks → either a Selected or a FallbackDecision.
      fun applyFallbacks(ranked, dimensionPriority, prefs, fallbacks): FallbackDecision

      // The orchestrator-facing entry point. Composes the above + handles ASK / DO_NOT_DOWNLOAD
      // / BEST_EFFORT.
      fun selectBestVideo(sourceId, servers, preferences): Selection
  }

Future customizations are easy:
  • Add a 4th dimension (e.g. "subtitles language") → add a PreferenceDimension.SUBTITLES
    enum value + a corresponding prefs list + the rank tuple naturally extends. No algorithm change.
  • Per-dimension weights (instead of strict lexicographic order) → swap the rank tuple for
    a weighted score. rank(candidates, weights) instead of rank(candidates, dimensionPriority).
    The pipeline shape doesn't change.
  • Per-source dimension priority → lift dimensionPriority from a single global pref to
    Map<sourceId, List<PreferenceDimension>> (same shape as serverPreferences).
  • "Strict mode" (must match ALL preferred dimensions, no TRY_NEXT) → add a per-dimension
    "strict" flag — already partially captured by DO_NOT_DOWNLOAD.
  • Conflict resolution rules → applyFallbacks could return a List<Conflict> that the UI
    surfaces as "Your #1 audio (DUB) isn't on your #1 server (Streamtape). Pick: DUB on
    Vidstreaming / SUB on Streamtape / Show picker". The engine already has the data —
    just needs a richer return type.

The pipeline's purity also makes it trivially unit-testable: flatten + rank + applyFallbacks
are pure functions over data classes. The OLD selectBestVideo was a 100-line method with
interleaved reads + branching — much harder to test.`;

/* ---------------------------------------------------------------------------
 * PROXY-CHURN BUG FIX — the 4-layer fix (10-player-integration.md §14 + 15-ui-and-bug-analysis.md Part B)
 * ------------------------------------------------------------------------- */

export const PROXY_CHURN_ROOT_CAUSE = `# The bug (15-ui-and-bug-analysis.md Part B — root-cause analysis)

  ┌──────────────────────────────────────────────────────────────────────────┐
  │ USER ACTION:                                                              │
  │   1. User taps download on Episode A from extension source X              │
  │   2. Download starts — the videoUrl is http://localhost:54321/...         │
  │      (the extension created a LocalProxyServer on port 54321)              │
  │   3. While download is in-flight, user plays Episode B from the SAME      │
  │      source X (taps the episode row → WatchScreen)                        │
  │                                                                            │
  │ WHAT HAPPENS:                                                             │
  │   • The resolver calls source.getHosterList(source.id, episode.url) again │
  │   • The extension's getHosterList creates a NEW LocalProxyServer on port  │
  │     54322 (each call spins up a fresh proxy)                              │
  │   • The OLD proxy on port 54321 is KILLED (the extension only keeps one)  │
  │   • The in-flight download's next input.read(buffer) on port 54321 throws │
  │     IOException("Connection refused")                                      │
  │   • The task flips to ERROR                                                │
  └──────────────────────────────────────────────────────────────────────────┘

WHY THE OLD PROJECT CAN'T FIX IT:
  The OLD DownloadOrchestrator captures the videoUrl at enqueue time + the engine
  makes a single HTTP call to it. There's no mechanism to detect that the URL
  has died + no way to re-resolve. The user has to manually retry the download
  (which re-runs the resolver + gets a fresh proxy URL).

THE FREQUENCY:
  • Any time the user downloads + plays from the same source concurrently → bug.
  • Any time two downloads from the same source overlap → bug (the second one
    kills the first's proxy).
  • Reported by the user as "downloads fail when I play another episode".`;

export const PROXY_CHURN_4_LAYERS = `# The 4-layer fix (10-player-integration.md §14.1)

┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 1 — PRIMARY: directUrl on ResolverVideo + prefer it for downloads │
└─────────────────────────────────────────────────────────────────────────┘
  • core/video-resolver/.../ResolverTypes.kt — add directUrl: String? = null field
    to ResolverVideo.
  • The resolver strategy extracts the direct URL by calling a new
    Video.directVideoUrl extension hook (similar to how videoUrl is exposed).
    Extensions that proxy can override this to return the underlying CDN URL.
  • DownloadOrchestrator.buildRequest uses:
      selection.video.directUrl ?: selection.video.url
    for DownloadRequest.videoUrl.
  • The download engine then makes a DIRECT HTTP call to the CDN — no proxy
    dependency, no churn. Most extensions expose a directUrl (the proxy is
    only needed for anti-scraping stripping during streaming).

┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 2 — SECONDARY: re-resolve-on-IOException for localhost URLs       │
│            (M15 — bounded recursion, M17 — direct lookup, M19 — cap)    │
└─────────────────────────────────────────────────────────────────────────┘
  • If directUrl is null (the extension only exposes a proxy URL), the download
    engine treats the proxy URL as EPHEMERAL + re-resolves on failure.
  • DownloadRequest.resolveContext: ResolveContext? — captures
    (sourceId, episodeUrl, serverName, audioLabel, quality, mainId, episodeKey)
    — 7 fields (M64 — enough to re-resolve + do DB lookups).
  • In HttpDownloader.downloadNormal:
      catch (e: IOException) {
          if (url.startsWith("http://localhost") && resolveContext != null
              && reResolver != null
              && reResolveAttempts < MAX_RE_RESOLVE_ATTEMPTS) {        // M15: cap at 1
              val fresh = reResolver.reResolve(resolveContext)         // M17: DIRECT lookup,
                                                                      //   NOT a re-run of
                                                                      //   AutoDownloadEngine
              if (fresh != null) {
                  store.updateResolveContext(taskId, fresh.url, resolveContext)
                  FileOutputStream(tempFile).use { /* truncate */ }   // fresh proxy = new port
                  return downloadNormal(                               // M16: recurse on downloadNormal
                      url = fresh.url,                                 //   (NOT downloadVideoToCache),
                      headers = fresh.headers,                         //   pass resolveContext + reResolveAttempts + 1
                      tempFile = tempFile,
                      taskId = taskId,
                      resolveContext = resolveContext,
                      onProgress = onProgress,
                      reResolveAttempts = reResolveAttempts + 1,       // M15: bound the recursion
                  )
              }
          }
          if (url.startsWith("http://localhost") && reResolveAttempts >= MAX_RE_RESOLVE_ATTEMPTS) {
              throw DownloadException(
                  "Proxy URL died after $MAX_RE_RESOLVE_ATTEMPTS re-resolve attempt(s) — " +
                  "the extension's proxy server is being churned by another playback.", e,
              )
          }
          throw DownloadException("Video download failed: ...", e)
      }

┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 3 — TERTIARY: ProxyLeaseCoordinator (optional, deferred)           │
└─────────────────────────────────────────────────────────────────────────┘
  • Tracks active leases: Map<ProxyKey, LeaseRefcount> where ProxyKey =
    (sourceId, serverName) + LeaseRefcount counts how many consumers (MPV +
    each download task) are currently using the proxy.
  • Exposes acquireLease(source, serverName): Lease + releaseLease(lease).
  • Wraps VideoResolver.resolve so that BEFORE calling source.getHosterList,
    it checks if a lease for (source.id, ...) already exists. If yes, reuses
    the existing resolved videos (whose proxy URLs are still alive). If no,
    calls getHosterList + creates a new lease.
  • The download engine calls acquireLease before starting + releaseLease in
    its finally block. The player does the same.
  • RESULT: a second getHosterList for the same source is SUPPRESSED while a
    download is using the proxy. The bug class is eliminated entirely.
  • DECISION: Implement Layer 1 + Layer 2 in Phase D.2 (required). Defer
    Layer 3 to a later phase — it's only needed if extensions consistently
    expose only proxy URLs (no directUrl).

┌─────────────────────────────────────────────────────────────────────────┐
│ Layer 4 — QUATERNARY: foreground service for download durability         │
│            (independent of the proxy-churn bug, but architecturally      │
│            aligned — handled in Phase D.4 with the M20 fix)              │
└─────────────────────────────────────────────────────────────────────────┘
  • Independent of the proxy-churn bug, the new project MUST add a foreground
    service for downloads (per 06-notifications-foreground-service.md). This
    prevents Android from killing the download when the app goes to background,
    which is a SEPARATE failure mode from the proxy-churn one but worth fixing
    in the same pass.
  • See the Notifications section for the M20-M30 fixes (synchronous
    startForeground, Coil 3, KoinComponent, etc.).`;

export const PROXY_CHURN_RERESOLVER = `# The ReResolver class (10-player-integration.md §14.3 — REVIEW-5 M17)

class ReResolver(
    private val videoResolver: VideoResolver,
    private val preferences: DownloadPreferences,
) {
    // REVIEW-5 M17: the autoDownloadEngine: AutoDownloadEngine constructor param was REMOVED —
    // it was dead DI (the OLD draft claimed the re-resolve "uses the SAME AutoDownloadEngine"
    // but the reResolve implementation below does a DIRECT lookup by pinned (server, audio,
    // quality), never calling the engine). The engine might pick a DIFFERENT (server, audio,
    // quality) on re-resolve, which would defeat the purpose (the user's pinned choice must
    // be preserved).

    suspend fun reResolve(context: ResolveContext): FreshVideo? {
        val result = videoResolver.resolve(context.sourceId, context.episodeUrl) ?: return null
        // Find the server with the pinned name.
        val server = result.servers.firstOrNull { it.name == context.serverName } ?: return null
        // Find the audio version with the pinned label.
        val audio = server.audioVersions.firstOrNull { it.label == context.audioLabel } ?: return null
        // Find the video with the pinned quality.
        val video = audio.videos.firstOrNull { it.quality == context.quality } ?: return null
        return FreshVideo(
            url = video.directUrl ?: video.url,
            headers = video.videoHeaders,
        )
    }
}

data class FreshVideo(val url: String, val headers: String?)

# The ResolveContext data class (M64 — 7 fields):

@Serializable
data class ResolveContext(
    val sourceId: Long,
    val episodeUrl: String,
    val serverName: String,
    val audioLabel: String,
    val quality: String,
    val mainId: String,        // for DB lookups during re-resolve (the OLD draft listed only 5 fields)
    val episodeKey: String,    // for DB lookups during re-resolve
)

# Persisted in download_queue.resolve_context as a JSON-encoded string. Read by HttpDownloader
# on IOException. The ReResolver uses it to re-run the resolve + pick the SAME (server, audio,
# quality) combination — the user's pinned choice is preserved.

# Cap composition (M19): the OUTER retry loop (16-quality-of-life.md §1.2) caps at 3 attempts.
# The INNER re-resolve (HttpDownloader.downloadNormal) caps at 1 attempt (= 2 total download
# attempts per outer iteration). Total = 3 outer × 2 inner = 6 download attempts maximum before
# the task goes to ERROR.`;

export const PROXY_CHURN_ARCHITECTURAL_RULES = `# 5 architectural rules to prevent the bug class (15-ui-and-bug-analysis.md §B.7)

1. The download engine must NEVER depend on the lifetime of a side-effect created by the
   resolver. If the resolver creates a resource (proxy server, file descriptor, session token)
   whose lifetime is shorter than the download's, the download engine must either (a) not use
   that resource, or (b) hold an explicit lease that prevents the resource from being killed.

2. URLs captured at enqueue time are NOT durable. The download engine must treat videoUrl as
   potentially ephemeral. Either:
   - Capture a directUrl (no proxy) and use that, OR
   - Capture a resolveContext (enough info to re-resolve) and use the proxy URL with
     re-resolve-on-failure.

3. VideoResolver.resolve is NOT idempotent with respect to side-effects. Calling it twice for
   the same (source, episode) may kill the proxy from the first call. The new project must
   EITHER make resolve idempotent (cache the result + reuse it for the same (source, episode)
   while a lease is held) OR coordinate via a lease coordinator so that the second call doesn't
   kill the first.

4. The download scope must be architecturally separate from the playback scope. The OLD project
   actually gets this right (DefaultDownloadManager's private scope vs AppController's scope vs
   MPV's no-scope). The new project should preserve this separation — but ALSO ensure the
   download doesn't depend on resources held by the playback scope.

5. The download engine must log the URL it's fetching from (the OLD project does this in
   HttpDownloader.download's DownloadLogger.i("  URL: $videoUrl")). When debugging this bug,
   that log line is the smoking gun — if the URL is http://localhost:PORT/..., the bug is proxy
   churn; if it's https://cdn.example.com/..., the bug is something else. The new project
   should preserve this logging AND ADD a one-time warning when the URL is detected as a
   localhost URL.`;

/* ---------------------------------------------------------------------------
 * QUALITY OF LIFE — auto-retry + auto-resume + auto-pause + orphan cleanup (16-quality-of-life.md)
 * ------------------------------------------------------------------------- */

export interface QoLFeature {
  id: string;
  title: string;
  headline: string;
  details: string;
  color: string;
}

export const QOL_FEATURES: QoLFeature[] = [
  {
    id: "Q1",
    title: "Auto error handling / retry (the headline QoL)",
    headline:
      "When a download fails (network blip, server 5xx, proxy-churn), the engine retries automatically — no user intervention. The UI shows 'Retrying (2/3)…' instead of a hard ERROR.",
    details:
      "launchDownload wraps the download in a try/catch + a retry loop. RetryPolicy.forException (M48: type matching, not string matching — `e is ConnectException || e is SocketException`, `e is HttpException && e.code in 500..599`, etc.) decides whether to retry. On retryable error: setRetryingStatus (M11) + delay(backoff) + re-attempt. Cap at 3 outer attempts (IOException / HTTP 5xx / 429). M19: cap composition — outer 3 × inner 2 (re-resolve) = 6 download attempts max before ERROR. HTTP 4xx + encrypted HLS + unknown errors are NOT retried (retrying won't help). The RETRYING state (M9) propagates to the UI: 'Retrying (2/3): Connection refused' pill + the same Cancel button as Queued. Survives app restart via M6 (resetDownloadingToQueued WHERE state IN ('DOWNLOADING', 'RETRYING')).",
    color: "var(--c-primary)",
  },
  {
    id: "Q2",
    title: "Auto-resume on network change",
    headline:
      "When the network drops mid-download + comes back, the queue automatically resumes QUEUED tasks. DOWNLOADING tasks are paused (preserving their progress) — the user must explicitly resume them.",
    details:
      "NetworkCallback registration in DownloadManager init. onNetworkChanged (M42: uses pauseInternal — assumes mutex held, no deadlock) handles: (1) network lost → pause all DOWNLOADING + RETRYING tasks (preserve their progress); (2) network back → tryStartNext picks up QUEUED tasks (paused tasks stay paused — the user might have walked away, auto-resuming might burn data they didn't intend to use). This matches the OLD project's behaviour (the OLD project's connectivityCheck only runs on tryStartNext — paused tasks stay paused).",
    color: "var(--c-success)",
  },
  {
    id: "Q3",
    title: "Auto-pause on metered network",
    headline:
      "When the user is on a metered network (mobile data, metered Wi-Fi) AND pref_dl_wifi_only is ON, the queue pauses all in-flight downloads. Posts a one-shot notification explaining WHY.",
    details:
      "The same onNetworkChanged callback (M42) — the preferences.wifiOnly().get() check determines whether to pause. Posts: title 'Downloads paused', text 'Wi-Fi only is on — connect to Wi-Fi to resume.', channel anikuta_downloads_progress (silent), auto-cancel. This tells the user WHY their downloads stopped (so they don't think the app is broken). When the user reconnects to unmetered Wi-Fi, the queue auto-resumes QUEUED tasks.",
    color: "var(--c-warning)",
  },
  {
    id: "Q4",
    title: "Download verification (size + magic bytes)",
    headline:
      "Before publishing a downloaded file to the user's SAF folder, verify it's actually a valid video (not an error page, a redirect, or a PNG masquerading as a video). The user's folder NEVER contains partial/corrupt files.",
    details:
      "validateDownloadedFile: if tempFile.length() < MIN_VALID_VIDEO_BYTES (500 KB) → throw DownloadException (the server returned an error page or redirect). Logs the first 200 bytes as hex for debugging. verifyVideoMagicBytes (non-fatal — failures are logged, not thrown): checks for HTML (3C 21 / 3C 68), PNG (89 50 4E 47), JPEG (FF D8 FF), MP4 (ftyp at offset 4), MKV/WebM (1A 45 DF A3), FLV, AVI, MPEG-TS (0x47 at positions 0/188/376/564/752). Post-publish verification: a periodic background job re-verifies the file exists at the recorded URI + the file size matches the recorded file_size (within 1% — some SAF providers may report slightly different sizes due to block alignment). If verification fails, the row is marked 'missing' + the user is prompted to re-download.",
    color: "var(--c-secondary)",
  },
  {
    id: "Q5",
    title: "Orphan-file cleanup",
    headline:
      "Keep the user's SAF folder + the temp cache clean of orphaned files (from crashes, failed downloads, manual file deletions).",
    details:
      "Temp cache: TempDownloadCache.cleanupStale() on startup — any temp dir present at startup is from a crashed/interrupted download (safe to delete — the user's SAF folder has nothing partial). SAF folder reconciliation: the DownloadScanner.scanAndReconcile() walks video/ / images/ / text/ / audio/ (M57), reads each data.json, verifies each episode entry's video file exists + is non-empty. M55: uses listFiles() ONCE per content folder + builds a Map<String, DocumentFile> index (avoids the O(N) findFile() per episode — was 40,000 ops for 200 contents × 200 episodes). Half-written SAF file cleanup: publishToUserFolder calls contentDir.findFile(videoName)?.delete() before creating the new file. Empty content folder cleanup: after deleting an episode, if the content folder is now empty, the whole folder is deleted.",
    color: "var(--c-danger)",
  },
  {
    id: "Q6",
    title: "Auto-clear completed entries after 10s",
    headline:
      "Keep the live queue tidy — completed downloads auto-clear from the in-memory task list + the DB queue row 10 seconds after completion. The file stays on disk; the downloaded_episode row stays in the DB (it's the durable record).",
    details:
      "scheduleAutoClear(taskId) — guarded by a Set<Long> (M43: autoClearScheduled.add wrapped in mutex.withLock) to prevent the leak per 15-ui-and-bug-analysis.md §A.11 fix #12 (without the guard, the OLD project launches a new coroutine per emission — the same task gets scheduled for auto-clear multiple times). Why 10s: long enough for the user to see the 'Download complete' notification + the green ✓ in the UI; short enough that the queue doesn't pile up with completed entries.",
    color: "var(--c-warning)",
  },
];

export const QOL_RETRY_POLICY_TABLE = `# The retry policy table (16-quality-of-life.md §1.1)

┌────────────────────────────────────────────────────────────┬─────────┬──────────────┬───────────────────────────────────┐
│ Error type                                                 │ Retry?  │ Max attempts │ Backoff                           │
├────────────────────────────────────────────────────────────┼─────────┼──────────────┼───────────────────────────────────┤
│ IOException (network blip, connection reset)               │ ✅      │ 3            │ Exponential: 1s, 2s, 4s            │
│ DownloadException wrapping an IOException                  │ ✅      │ 3            │ Exponential: 1s, 2s, 4s            │
│ HTTP 5xx (server error — M48 type matching)                │ ✅      │ 3            │ Exponential: 1s, 2s, 4s            │
│ HTTP 4xx (client error — 401, 403, 404)                    │ ❌      │ —            │ — (URL is wrong, retry won't help) │
│ HTTP 429 (rate limit — M48 type matching)                  │ ✅      │ 3            │ Retry-After header if present,     │
│                                                            │         │              │ else 5s, 10s, 20s                  │
│ DownloadException("Encrypted HLS stream...")               │ ❌      │ —            │ — (engine can't decrypt)           │
│ DownloadException("Connection refused") on localhost URL   │ ✅ via  │ 2 (1 initial │ Immediate (no backoff — the proxy  │
│ (proxy-churn — M15 bounded recursion)                      │ ReResolver │ + 1 re-resolve) │ is already dead)               │
│ CancellationException (pause/cancel)                       │ ❌      │ —            │ — (not an error, user action)      │
│ Any other Exception                                        │ ❌      │ —            │ — (unknown — don't retry blindly)  │
└────────────────────────────────────────────────────────────┴─────────┴──────────────┴───────────────────────────────────┘

# Cap composition (M19): outer (3 attempts) × inner (2 attempts per outer = 1 initial + 1
# re-resolve) = 6 download attempts maximum before the task goes to ERROR.

# REVIEW-5 M49: HttpException is defined LOCALLY in :core:download — does NOT depend on
# :core:source-api. class HttpException(val code: Int, message: String, cause: Throwable? = null)
# : DownloadException(message, cause). Throw sites: HttpDownloader.downloadNormal +
# HlsDownloader.fetchText + HlsDownloader.downloadSegment + AdvancedHttpDownloader.probeServer.

# REVIEW-5 M48: RetryPolicy.forException uses exception TYPE matching (e is HttpException &&
# e.code in 500..599), NOT fragile string matching on the message (the OLD draft's
# e.message?.contains("Connection refused") breaks in different locales / JVM versions /
# CDN error messages).

# REVIEW-5 M50: the CancellationException branch was unreachable (the catch above it re-throws)
# — removed from RetryPolicy.forException for clarity.`;

