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
    "Workflow, storage paths, state machine, download engines, foreground service, and the 6-phase build plan (D.0 → D.6) for the new ANI-KUTA.",
  status: "RESEARCH COMPLETE",
  statusColor: "var(--c-success)",
  summary:
    "The old ANI-KUTA download system at REFERENCES/old-kuta/ANIKUTA/ has been fully mapped across 14 research documents. This page is the single living spec the new project will build from. It covers the click→queue flow, the 6-state machine (QUEUED → DOWNLOADING → COMPLETED), the exact SAF folder structure (<root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/...), the 3 download engines (HTTP / HLS / Advanced multi-threaded), all 15 user settings, the new SQLDelight schema (replacing JSON-in-SharedPrefs), the Koin DI graph, the critical foreground-service gap (old has none — new MUST add one), and a 12–18-day, 6-phase implementation plan with 7 confirmed design decisions and an 8-entry risk register.",
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
  │                  ├──error──▶ Error ──retry──▶ Queued
  │                  └──cancel──▶ Cancelled (terminal)
  └──cancel──▶ Cancelled (terminal)

                       ┌──────────────────────────────────┐
                       │                                  │
                       ▼                                  │
                    ┌───────┐  start (permit acquired) ┌──────────┐
       enqueue ───▶ │QUEUED│ ────────────────────────▶ │DOWNLOADING│
                    └───────┘                            └──────────┘
                       │   ▲                                 │
              pause    │   │ resume                          │ 100%
              (rare —  │   │ (also from ERROR)               │
               before  │   │                                 ▼
               permit) │   │                             ┌──────────┐
                       ▼   │                             │COMPLETED │
                    ┌───────┐                            │(terminal)│
                    │PAUSED │ ◀──── pause                └──────────┘
                    └───────┘       │
                       ▲            │
                       │            │ error (DownloadException
                       │            │  or uncaught Exception)
                       │            ▼
                       │        ┌───────┐
                       └────────│ ERROR │
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
    meaning: "Failed (network / IO / validation). Recoverable via retry.",
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
    action: "retry",
    to: "QUEUED (progress=0)",
    enforcedBy: "DownloadQueue.retry",
  },
  {
    from: "DOWNLOADING",
    action: "error",
    to: "ERROR",
    enforcedBy: "DownloadQueue.launchDownload catch blocks",
  },
  {
    from: "QUEUED / DOWNLOADING / PAUSED / ERROR",
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
  "Disallowed (silently no-op): pause on PAUSED / ERROR / COMPLETED — only accepts DOWNLOADING + QUEUED. retry on non-ERROR. resume on QUEUED / DOWNLOADING / COMPLETED — only accepts PAUSED + ERROR.";

/* ---------------------------------------------------------------------------
 * Storage Paths (from 04-storage-paths.md) — CRITICAL
 * ------------------------------------------------------------------------- */

export const STORAGE_TREE = `<USER_FOLDER>/ANIKUTA/
└── downloads/
    └── anime/
        └── <Anime Title [contentId-safe]>/        ← e.g. "Jujutsu Kaisen [al-101522]"
            └── Episode NNN/                       ← 3-digit zero-padded (Episode 001)
                ├── video.<ext>                     ← original format (mp4/mkv/webm/ts/...)
                └── data/
                    ├── subtitles/                  ← ALL subtitle files
                    │   ├── English_0.srt
                    │   └── Spanish_1.ass
                    └── metadata.json               ← cached episode metadata (informational)

# Concrete SAF example:
content://com.android.externalstorage.documents/tree/primary%3AAniKuta%20Downloads/
└── ANIKUTA/
    └── downloads/
        └── anime/
            └── Jujutsu Kaisen [al-101522]/
                ├── Episode 001/
                │   ├── video.mp4
                │   └── data/
                │       ├── subtitles/
                │       │   ├── English_0.srt
                │       │   └── Spanish_1.ass
                │       └── metadata.json
                ├── Episode 002/
                │   └── ...
                └── Episode 012.5/   ← (special episode — floored to 012 in the folder name!)
                    └── ...`;

export const STORAGE_TEMP_CACHE = `# Temp cache (internal, NOT the user's SAF folder) — internal-cache-first pipeline:
<cacheDir>/anikuta_downloads/
└── <taskId>/
    ├── video.<ext>          ← temp video file (deleted on completion/failure)
    ├── subtitles/
    │   └── <lang>_<i>.<ext>
    ├── metadata.json
    ├── resume.json          ← (only for Advanced method — chunk progress)
    ├── chunk_0.part         ← (only for Advanced method)
    ├── chunk_1.part
    └── ...`;

export interface StorageNamingRule {
  kind: string;
  pattern: string;
  examples: string;
  notes?: string;
}

export const STORAGE_NAMING_RULES: StorageNamingRule[] = [
  {
    kind: "Anime folder",
    pattern: "<sanitized-title> [<sanitized-contentId>]",
    examples:
      '"Jujutsu Kaisen" + "al:154587" → "Jujutsu Kaisen [al-154587]" · "Frieren: Beyond Journey\'s End" → "Frieren  Beyond Journey\'s End [al-154587]"',
    notes:
      "sanitizeFileName replaces : with a space. contentId sanitizer replaces : with - AND / with - (stable suffix for endsWith() lookups in deleteAnime / findEpisodeDirByNumber).",
  },
  {
    kind: "Episode folder",
    pattern: "Episode %03d (zero-padded 3-digit, floored)",
    examples:
      '1.0f → "Episode 001" · 12.0f → "Episode 012" · 12.5f → "Episode 012" ⚠️ · -1.0f → "Episode 000"',
    notes:
      "BUG: .5 specials collide with their integer counterparts. NEW project should use \"Episode 012.5\" for non-integers (D.6 polish task).",
  },
  {
    kind: "Video file",
    pattern: "video.<ext>",
    examples:
      'video.mp4 · video.mkv · video.webm · video.ts — whitelist mp4/mkv/webm/avi/mov/m4v/ts, default mp4',
    notes:
      "extractExtension strips the query string first. URL whitelist decides the extension.",
  },
  {
    kind: "Subtitle file",
    pattern: "<safeLang>_<index>.<ext>",
    examples:
      'English_0.srt · Spanish_1.ass · track_2.vtt — whitelist ass/srt/vtt/ssa/sub, default srt',
    notes:
      "safeLang replaces non-alphanumerics with spaces, defaulted to \"track\" if blank.",
  },
  {
    kind: "metadata.json",
    pattern: "Episode NNN/data/metadata.json",
    examples:
      "Pretty-printed EpisodeMetadataCache JSON — contentId, animeTitle, episodeNumber, episodeName, videoUrl, downloadedAt, sourceId.",
    notes:
      "Informational-only — a user browsing the folder with a file manager can identify the episode. Overwritten on re-download. ignoreUnknownKeys = true so old on-disk files (anilistId → contentId) parse cleanly.",
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
    recommendation: "Replicate old project: SAF + ActivityResultContracts.OpenDocumentTree + takePersistableUriPermission.",
    rationale:
      "User picks any folder (internal storage, SD card, Google Drive). Persistable URI permission survives app restarts AND device reboots. The URI string is stored in SharedPreferences under pref_dl_folder_uri. rootTree() returns null if: no folder set, URI parse fails, OR write permission was revoked.",
    color: "var(--c-primary)",
  },
  {
    title: "Internal-cache-first pipeline — temp → validate → atomic publish",
    recommendation:
      "Temp downloads go to <cacheDir>/anikuta_downloads/<taskId>/. Only after validation (size ≥ 500KB + magic-byte check) is the file copied to the SAF folder.",
    rationale:
      "1) No pollution — partial/corrupt downloads never appear in the user's folder. 2) Performance — writing to internal cache is faster than SAF per-byte writes (no ContentResolver round-trips). 3) Validation — can inspect bytes BEFORE committing. 4) Atomicity — the user's folder only ever contains complete, valid files. cleanupTask(taskId) always runs in HttpDownloader's finally block; cleanupStale() runs once at app startup.",
    color: "var(--c-success)",
  },
  {
    title: "Source-independent identity — contentId + episodeNumber",
    recommendation:
      'Composite key "$contentId|$episodeNumber" (3-decimal format). Filesystem fallback: findEpisodeDirByNumber scans the anime/ folder for a directory ending with [sanitized-contentId].',
    rationale:
      "Survives extension source switches — the new source has a different episodeUrl but the same episodeNumber. This is the 'source-switching fix' from the old project (Phase 6, ADR-050). The old identity was anilistId + episodeUrl which broke on source switches.",
    color: "var(--c-secondary)",
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
  title: "CRITICAL GAP — Old project has NO foreground Service; new project MUST add one",
  body:
    "The old ANI-KUTA download system does NOT use a foreground Service. Downloads run in an app-scoped CoroutineScope(SupervisorJob + Dispatchers.IO) and post notifications via NotificationManagerCompat (no startForeground). The FOREGROUND_SERVICE_DATA_SYNC permission IS declared but is used by ExtensionInstallService, NOT by downloads. This works while the app is in the foreground, but on Android 14+ (and even earlier on aggressive OEMs like Xiaomi/Huawei), background downloads without a foreground service can be KILLED when the app is backgrounded. The new project MUST add a DownloadService with foregroundServiceType=\"dataSync\" + startForeground within 5 seconds of starting, posting the ongoing summary notification as the foreground notification.",
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
    newProject: "Same.",
  },
  {
    aspect: "Summary notification",
    oldProject:
      "ID 9001, ongoing, progress bar, throttled 800ms (PROGRESS_THROTTLE_MS). Picks first DOWNLOADING task as primary (or falls back to first active task). Title shows count when > 1 ('Downloading 3 episodes'). setOngoing(true) — can't be swiped. setOnlyAlertOnce(true) + setSilent(true).",
    newProject: "Same + ADD Pause/Cancel action buttons (not in old project).",
  },
  {
    aspect: "Completion notification",
    oldProject:
      "ID taskId.toInt() + 10_000 (COMPLETION_OFFSET). stat_sys_download_done icon. setAutoCancel(true). PRIORITY_LOW. Called from DownloadQueue.launchDownload success path via onTaskCompleted callback.",
    newProject: "Same.",
  },
  {
    aspect: "Error notification",
    oldProject:
      "ID taskId.toInt() + 20_000 (ERROR_OFFSET). stat_notify_error icon. setAutoCancel(true). PRIORITY_DEFAULT (higher than completion's PRIORITY_LOW) — so failures are more visible. Called from DownloadQueue.launchDownload DownloadException + generic Exception catch blocks via onTaskError callback.",
    newProject: "Same.",
  },
  {
    aspect: "Tap intent",
    oldProject:
      "Just opens the app's launcher activity (no deep-link to the Downloads screen). PendingIntent.FLAG_IMMUTABLE on API 23+ (required on API 31+).",
    newProject: "Same initially; ADD deep-link to the Downloads screen (Nav3 deep-link support).",
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
      "**ADD DownloadService** — foregroundServiceType=\"dataSync\" + startForeground(SUMMARY_ID, notification). Started when first download starts; stopped when queue empties. Permissions FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC already declared in :app manifest.",
  },
];

export const NOTIFICATION_CONSTANTS = `companion object {
    private const val CHANNEL_ID = "anikuta_downloads"
    private const val SUMMARY_ID = 9001
    private const val COMPLETION_OFFSET = 10_000
    private const val ERROR_OFFSET = 20_000
    private const val PROGRESS_THROTTLE_MS = 800L
    @Volatile private var lastProgressAt = 0L   // app-wide throttle (defensive @Volatile)
}`;

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
  title: "Decision D1 — Persistence: SQLDelight (NOT JSON-in-SharedPrefs)",
  recommendation:
    "Option B1 (lean — separate columns). Tables already exist (download_queue, downloaded_episode). More verbose than JSON blob but queryable + no JSON parsing on every read. Composite key uniqueness enforced by index. Requires a schema migration (3.sqm) since the tables already exist.",
  oldProject:
    "JSON-serialized List<DownloadTask> in SharedPreferences under key pref_download_tasks_v1. Reason quoted from DownloadStore.kt:14-23 — 'The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how WatchProgressStore already works.'",
  newProject:
    "Use SQLDelight tables — already exist in core/database/src/main/sqldelight/. Wrap with a reactive StateFlow for UI consumption. Throttle progress writes at the app level (same as old project's persistThrottled).",
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
    name: "download_queue (PROPOSED — Option B1, separate columns)",
    purpose:
      "The live queue (QUEUED + DOWNLOADING + PAUSED + ERROR tasks). Carries the full task data as columns for queryability.",
    isNew: true,
    schema: `CREATE TABLE download_queue (
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
    state TEXT NOT NULL,                 -- QUEUED / DOWNLOADING / PAUSED / COMPLETED / ERROR
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
CREATE UNIQUE INDEX idx_download_queue_episode ON download_queue(content_id, episode_number);`,
  },
  {
    name: "download_queue (CURRENT STUB — needs replacement)",
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
      "Completed downloads library. Current stub has only the basics. RECOMMENDATION: add content_id, episode_number, anime_title, cover_url columns for the Downloads-screen grouping.",
    isNew: false,
    schema: `CREATE TABLE IF NOT EXISTS downloaded_episode (
    episode_key TEXT NOT NULL PRIMARY KEY,
    file_path TEXT NOT NULL,             -- content:// URI (SAF)
    file_size INTEGER NOT NULL,
    quality TEXT,
    downloaded_at INTEGER NOT NULL
    -- PROPOSED ADDITIONS (D.0 schema migration):
    -- content_id TEXT NOT NULL,
    -- episode_number REAL NOT NULL,
    -- anime_title TEXT NOT NULL,
    -- cover_url TEXT
);

-- Queries:
-- insertDownloadedEpisode (INSERT OR REPLACE)
-- getDownloadedEpisode, getAllDownloadedEpisodes
-- isEpisodeDownloaded (SELECT EXISTS)
-- deleteDownloadedEpisode`,
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
    title: "Foundations",
    status: "planned",
    days: "1-2 days",
    goal: "Extend the new project's infrastructure to support the download system.",
    color: "var(--c-primary)",
    tasks: [
      "Extend PreferenceStore (:core:preferences) with a reactive Flow<T> API — preferenceFlow<T>(key, default) helper backed by OnSharedPreferenceChangeListener. The UI depends on reactive prefs.",
      "Update SQLDelight schema: modify downloadQueue.sq + downloadedEpisode.sq to carry the full task data (Option B1, separate columns). Add migration file (3.sqm) since the tables already exist.",
      'Add the "download" qualified OkHttpClient to :core:network. Long timeouts (30s connect, 60s read/write) — separate from the extension NetworkHelper client.',
      "Add SAF DocumentFile dependency to :core:download's build.gradle.kts: implementation('androidx.documentfile:documentfile:1.0.1').",
      "Add kotlinx-serialization-json to :core:download (for DownloadTrack JSON columns + metadata.json).",
      "Delete the stub DownloadManager.kt + DownloadState.kt in :core:download (they'll be replaced in D.1).",
    ],
  },
  {
    id: "D.1",
    title: "Engine",
    status: "planned",
    days: "3-4 days",
    goal: "Port the :core:download engine. No UI yet — just the engine + DI.",
    color: "var(--c-secondary)",
    tasks: [
      "Port 19 files from old project (see file list in 13-implementation-plan.md §5): DownloadModels, DownloadRequest, DownloadStatus (enum + isTerminal/isActive), DownloadTask (@Serializable), DownloadManager (interface — rename stub), DownloadStore (adapt to SQLDelight), DownloadPreferences (all 15 settings), DownloadLogger, DynamicProgressTracker (pure math), TempDownloadCache, DownloadStorageProvider (SAF DocumentFile, ~570 lines), VideoTypeDetector, HttpDownloader (~538 lines), HlsDownloader (~333 lines), DownloadQueue (~315 lines, adapt persistence to SQLDelight), DownloadNotificationManager, DefaultDownloadManager, ServerDiscoveryStore, di/DownloadModule (Koin bindings).",
      "Create NEW DownloadService.kt (foreground service — no old equivalent, ~150 lines): foregroundServiceType=\"dataSync\" + startForeground(SUMMARY_ID, notification) within 5s. Started when first download starts; stopped when queue empties.",
      "Adapt DownloadQueue to SQLDelight: replace store.purgeCancelled() with DELETE FROM download_queue WHERE state = 'CANCELLED'. Replace store.setAll(tasks) with per-row updateDownloadState / insertDownloadQueue / deleteDownloadQueue. For progress ticks (throttled), single UPDATE statement. On startup, SELECT * to populate _tasks; RESET any DOWNLOADING tasks to QUEUED (fixes the old project's bug — see 03-state-machine.md §7).",
      "Add DownloadService manifest entry: <service android:name=\"com.confused.anikuta.core.download.DownloadService\" android:exported=\"false\" android:foregroundServiceType=\"dataSync\" />.",
    ],
  },
  {
    id: "D.2",
    title: "Storage",
    status: "planned",
    days: "1-2 days (mostly subsumed by D.1)",
    goal: "SAF folder picker + storage paths — verify the D.1 port.",
    color: "var(--c-success)",
    tasks: [
      "Verify folder picker works (ActivityResultContracts.OpenDocumentTree) + takePersistableUriPermission + URI string stored in prefs.",
      "Verify folder structure created: <root>/ANIKUTA/downloads/anime/<Title [contentId-safe]>/Episode NNN/{video.<ext>, data/{subtitles/, metadata.json}}.",
      "Verify internal-cache-first pipeline works (temp download → validate → publish to SAF).",
      "Verify Filesystem fallback (findEpisodeDirByNumber) works for source switches.",
      "Testing: manually trigger a download (via a temporary debug button) + verify the folder structure with a file manager.",
    ],
  },
  {
    id: "D.3",
    title: "Orchestrator + UI wiring",
    status: "planned",
    days: "2-3 days",
    goal: "Bridge the resolver + engine + add the per-episode download UI on the details page.",
    color: "var(--c-warning)",
    tasks: [
      "Create :app files: download/DownloadOrchestrator.kt (adapt ResolverService → new project's VideoResolver; adapt ResolverResult/ResolverServer/ResolverVideo types), download/EnqueueResult.kt (sealed interface), download/PickerContext.kt (data class), di/DownloadAppModule.kt (Koin module).",
      "EXTEND :app navigation/AppController.kt-equivalent (Nav3-based): add download methods — downloadEpisode, cancelDownload, resumeDownload, retryDownload, deleteDownload(contentId, episodeUrl), enqueuePickedVideo(video, serverName, audioLabel), downloadPickerTarget: State<EnqueueResult.ShowPicker?>.",
      "Create :feature:anime-details/impl files: EpisodeDownloadState.kt (sealed interface, UI-side), EpisodeDownloadControl.kt (state-driven composable).",
      "Modify EpisodesSection.kt + DetailsScreen.kt + DetailsViewModel.kt: add onDownloadEpisode, downloadStates, onDownloadCancel/Resume/Retry/Delete params. DetailsViewModel exposes downloadStates: StateFlow<Map<String, EpisodeDownloadState>> collected from DownloadManager.episodeDownloadStates.",
    ],
  },
  {
    id: "D.4",
    title: "Downloads page UI",
    status: "planned",
    days: "3-4 days",
    goal: "Create the :feature:download module + the three screens + components.",
    color: "var(--c-danger)",
    tasks: [
      "Create the Gradle module :feature:download — build.gradle.kts (depends on :core:download, :core:designsystem, :core:preferences, :core:video-resolver for the picker sheet, Compose, Koin), src/main/AndroidManifest.xml (empty), settings.gradle.kts registration.",
      "Port 12 files from old project: DownloadUiState.kt, DownloadViewModel.kt (combine flows + auto-clear after 10s), DownloadsScreen.kt (570 lines, adapt CollapsingHeader etc.), DownloadedFilesScreen.kt (206 lines), DownloadSettingsScreen.kt (528 lines), DownloadVideoPickerSheet.kt (233 lines), DownloadsMoreEntries.kt (37 lines), ExtensionSourceInfo.kt (16 lines, DTO), components/DragReorderableList.kt (192 lines), components/DownloadedAnimeCard.kt (183 lines), components/DownloadsEmptyState.kt (96 lines), di/DownloadModule.kt (viewModelOf(::DownloadViewModel)).",
      "SKIP QueueRow.kt (244 lines) — dead code in old project, EpisodeRow inside AnimeSectionCard supersedes it.",
      "Wire DownloadsMoreEntries into MoreScreen.kt: item { DownloadsMoreEntries(onOpenDownloads = { navController.push(DownloadsKey) }) }. Create DownloadsKey Nav3 key in :feature:download/api.",
    ],
  },
  {
    id: "D.5",
    title: "Player integration",
    status: "planned",
    days: "1 day",
    goal: "Offline playback short-circuit.",
    color: "var(--c-primary)",
    tasks: [
      "Modify :app's nav controller (equivalent of AppController.resolveEpisode): before resolving a stream, call downloadManager.isEpisodeDownloaded(contentId, episodeNumber). If true, build a WatchRequest with the local content:// URI + null headers + 'Offline' server label + downloaded subtitle URIs. Push the WatchKey with that WatchRequest. If false, fall through to the streaming resolver.",
      "Verify the player (AnikutaMPVView in :core:player) handles content:// URIs (it should — same approach as the LocalProxyServer URLs). If not, add a resolveUrlForMpv helper that converts content:// → fd://<fd> via ContentResolver.openFileDescriptor.",
      "Add an 'Offline' badge to the WatchScreen (NOT in old project — see 10-player-integration.md §10).",
    ],
  },
  {
    id: "D.6",
    title: "Polish + testing",
    status: "planned",
    days: "1-2 days",
    goal: "Fix the known bugs + ship-quality polish.",
    color: "var(--c-secondary)",
    tasks: [
      "Fix the concurrent-downloads pref bug: add a Flow collector in DownloadQueue.init that calls refreshConcurrency() on pref changes.",
      "Fix the advancedMaxRetries default mismatch: set both code + UI to 10.",
      "Fix the DOWNLOADING-on-restart bug: reset to QUEUED on startup (handled in D.1's DownloadQueue adaptation).",
      'Fix the Episode NNN folder-name floor bug: use "Episode 012" for integers, "Episode 012.5" for non-integers.',
      "Add AnimatedContent to EpisodeDownloadControl for smooth state transitions (the KDoc claims it but the code doesn't).",
      "Add notification action buttons (Pause / Cancel) to the summary notification.",
      "Add a deep-link from the notification tap to the Downloads screen.",
      "Test: enqueue a download → verify folder structure → kill app → restart → verify queue persists → play offline → delete.",
    ],
  },
];

export const IMPLEMENTATION_TOTAL_ESTIMATE =
  "Total: 12-18 days (assumes one developer). D.3 + D.4 can overlap (different files).";

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
