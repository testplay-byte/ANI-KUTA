/*
 * D-240 / D-241 — Download & Tracking Improvements Plan
 *
 * Sources:
 *  - ANI-KUTA/APP/ani-kuta/core/download/.../ContentDataJson.kt (schema v3)
 *  - ANI-KUTA/APP/ani-kuta/core/download/.../DownloadStorageProvider.kt (write/preserve/upsert/remove/replace)
 *  - ANI-KUTA/APP/ani-kuta/core/download/.../DownloadScanner.kt (rebuild-on-scan + orphan guard)
 *  - ANI-KUTA/APP/ani-kuta/core/download/.../HttpDownloader.kt (upsert-on-publish)
 *  - ANI-KUTA/APP/ani-kuta/core/download/.../DefaultDownloadManager.kt (remove-on-delete)
 *  - ANI-KUTA/APP/ani-kuta/core/appupdate/GitHubUpdateSource.kt (auto-update fix)
 *  - ANI-KUTA/APP/ani-kuta/core/database/DatabaseDriverFactory.kt (migration fix)
 *  - ANI-KUTA/APP/ani-kuta/core/content/ContentResolver.kt (contentId fallback linking)
 *  - ANI-KUTA/APP/ani-kuta/build-logic/.../AndroidConfig.kt (versionCode 24 / versionName 0.2.23)
 *
 * Branch: functionality/improvements (D-240 + D-241)
 * Status: code complete + code-reviewed; pending APK build verification.
 */

/* ---------------------------------------------------------------------------
 * Hero
 * ------------------------------------------------------------------------- */

export const D240_HERO = {
  status: "Code complete — pending APK build",
  statusColor: "var(--c-success)",
  title: "D-240 / D-241 — Download Persistence + Tracking Improvements",
  subtitle:
    "Five root-cause fixes for the download system: auto-update version comparison, " +
    "data.json v3 with per-episode tracking, install/update/reinstall persistence, " +
    "contentId-based fallback linking, and live data.json sync on download/delete.",
  summary:
    "The prior APK (v0.2.22) shipped with three regressions: (1) the auto-update " +
    "checker always reported an update was available because it compared GitHub's " +
    "parsed versionCode against the APK's build versionCode (different scales); " +
    "(2) the on-disk .data.json files had no per-episode info, so after a reinstall " +
    "the user couldn't see which episodes they had downloaded; (3) the DB migration " +
    "dropped the downloaded_episode table on legacy→D.0 upgrades, silently deleting " +
    "the user's download history. D-240 fixes all three + adds defense-in-depth. " +
    "D-241 wires up the episodes list — append on download, remove on delete, " +
    "rebuild on scan.",
};

/* ---------------------------------------------------------------------------
 * The 5 user requirements + their fix status
 * ------------------------------------------------------------------------- */

export type FixStatus = "done" | "partial" | "pending";

export interface FixItem {
  id: string;
  requirement: string;
  rootCause: string;
  fix: string;
  files: string[];
  status: FixStatus;
  priority: "blocking" | "high" | "medium" | "low";
}

export const FIX_ITEMS: FixItem[] = [
  {
    id: "R1",
    requirement:
      "Auto-update should NOT report an update when the user is already on a newer version",
    rootCause:
      "GitHubUpdateSource.fetchLatestUpdate compared parseVersionCode(release.versionName) " +
      "(scale: 0–99,999,999) against the APK's actual build versionCode (scale: build counter, " +
      "was 23). 206 > 23 was always true → always reported an update. The versionName string " +
      "equality check only caught the EXACT-match case.",
    fix:
      "D-240: derive BOTH codes from version NAMES (parseVersionCode(release.versionName) vs " +
      "parseVersionCode(currentVersionName)). Symmetric, consistent scale. Also bumped " +
      "AndroidConfig.versionCode to 24 + versionName to 0.2.23 (defense-in-depth).",
    files: [
      "core/app-update/.../GitHubUpdateSource.kt",
      "build-logic/.../AndroidConfig.kt",
    ],
    status: "done",
    priority: "blocking",
  },
  {
    id: "R2",
    requirement:
      "data.json must include per-episode info (which episodes are downloaded, quality, server, audio)",
    rootCause:
      "ContentDataJson schema v1 was content-level only — no episodes list. After a reinstall, " +
      "the scanner could restore the content identity (mainId, contentId, title, cover) but had " +
      "to re-derive episodes from the file walk (regex on filename). Quality / server / audio " +
      "metadata was LOST because it's not derivable from a filename.",
    fix:
      "D-240: added `episodes: List<DownloadedEpisodeInfo>` to ContentDataJson (schema v2). " +
      "D-241: bumped to schema v3 — added `episodeKey`, `videoUri`, `subtitleUris` to each " +
      "episode entry. `episodeKey` is REQUIRED for the delete flow to find the right entry.",
    files: [
      "core/download/.../ContentDataJson.kt",
    ],
    status: "done",
    priority: "blocking",
  },
  {
    id: "R3",
    requirement:
      "Downloads must survive app update AND app-delete + reinstall",
    rootCause:
      "THREE data-loss paths: (1) DatabaseDriverFactory D.0 migration dropped BOTH download_queue " +
      "AND downloaded_episode on legacy upgrades; (2) DownloadScanner orphan cleanup hard-deleted " +
      "ALL DB rows when the SAF folder was temporarily inaccessible (contentCount == 0); " +
      "(3) downloaded_episode had no ALTER TABLE migration for the D-192 columns (source_id, " +
      "video_server, video_audio) — INSERT crashed on pre-D.192 → D.192+ upgrades.",
    fix:
      "D-240: (a) migration only drops download_queue (NOT downloaded_episode); (b) added " +
      "`if (contentCount == 0 && allDbEpisodes.isNotEmpty()) SKIP orphan cleanup` guard; " +
      "(c) added ALTER TABLE for the 3 D-192 columns with hasColumn() guards.",
    files: [
      "core/database/.../DatabaseDriverFactory.kt",
      "core/download/.../DownloadScanner.kt",
    ],
    status: "done",
    priority: "blocking",
  },
  {
    id: "R4",
    requirement:
      "Downloads must be linked to content after reinstall (even if the user opens via a different source)",
    rootCause:
      "After reinstall, the scanner restored main_entry from .data.json with the OLD mainId. " +
      "But if the user opened the same anime via the extension, ContentResolver.resolveOrCreateForExtension " +
      "called getMainEntryByExtension(extensionId, animeUrl) — which FAILED when the restored " +
      "main_entry had extension_id = null (common for AniList-first content). A NEW mainId was " +
      "created → the existing download was orphaned.",
    fix:
      "D-240: added contentId-based fallback lookup in ContentResolver. Before creating a new " +
      "mainId, try `repo.getMainEntryByContentId(fallbackContentId)`. The contentId is the " +
      "structured 6-section string that carries ALL the source fields — it's the durable linking " +
      "mechanism, NOT mainId (which is a random UUID that changes on reinstall).",
    files: [
      "core/content/.../ContentResolver.kt",
    ],
    status: "done",
    priority: "high",
  },
  {
    id: "R5",
    requirement:
      "data.json must be kept in sync on download + delete (live updates)",
    rootCause:
      "The episodes list field was added to the schema (D-240) but NOTHING populated it. " +
      "writeDataJson preserved the existing list via .copy() — but no code path APPENDED to " +
      "it on download complete, REMOVED from it on episode delete, or REBUILT it on scan.",
    fix:
      "D-241: added 3 new methods to DownloadStorageProvider — upsertEpisodeInDataJson " +
      "(called by HttpDownloader after publishVideoFile), removeEpisodeFromDataJson (called " +
      "by DefaultDownloadManager.deleteDownloadedEpisode), replaceEpisodesInDataJson (called " +
      "by DownloadScanner.scan to rebuild from the file walk). All best-effort — a .data.json " +
      "write failure never fails the parent operation.",
    files: [
      "core/download/.../DownloadStorageProvider.kt",
      "core/download/.../HttpDownloader.kt",
      "core/download/.../DefaultDownloadManager.kt",
      "core/download/.../DownloadScanner.kt",
    ],
    status: "done",
    priority: "blocking",
  },
];

/* ---------------------------------------------------------------------------
 * Schema evolution (v1 → v2 → v3)
 * ------------------------------------------------------------------------- */

export interface SchemaVersion {
  version: number;
  date: string;
  changes: string[];
  exampleEpisodesField: string;
}

export const SCHEMA_EVOLUTION: SchemaVersion[] = [
  {
    version: 1,
    date: "D.1.4 + REVIEW-5 M5",
    changes: [
      "Content-level only — one .data.json per content folder.",
      "Carries all FK columns from the content table (mainId, contentId, title, coverUrl, etc.).",
      "NO per-episode info — scanner had to re-derive episodes from filename regex.",
      "Used for reinstall recognition (restore main_entry with the OLD mainId).",
    ],
    exampleEpisodesField: "(not present)",
  },
  {
    version: 2,
    date: "D-240",
    changes: [
      "Added `episodes: List<DownloadedEpisodeInfo>` field (nullable for v1 forward-compat).",
      "Each episode entry carries: episodeNumber, episodeUrl, episodeName, videoUrl, quality, videoServer, audioVariant, downloadedAt, fileSize.",
      "Schema is forward-compat: v1 files load with episodes = emptyList().",
      "GAP: the field existed but NOTHING populated it (fixed in D-241).",
    ],
    exampleEpisodesField:
      '"episodes": [{ "episodeNumber": 1.0, "episodeUrl": "...", "quality": "1080p", ... }]',
  },
  {
    version: 3,
    date: "D-241",
    changes: [
      "Added `episodeKey: String?` to each episode entry — REQUIRED for delete matching.",
      "Added `videoUri: String?` — the on-disk content:// URI (rebuilt on scan).",
      "Added `subtitleUris: List<String>` — the on-disk subtitle content:// URIs.",
      "All new fields have defaults → v2 files still load; scanner backfills episodeKey from filename.",
      "POPULATED: upsert on download, remove on delete, rebuild on scan.",
    ],
    exampleEpisodesField:
      '"episodes": [{ "episodeKey": "550e8400-...|00001", "videoUri": "content://...", "subtitleUris": [...], ... }]',
  },
];

/* ---------------------------------------------------------------------------
 * Data flow diagrams
 * ------------------------------------------------------------------------- */

export interface FlowStep {
  step: number;
  actor: string;
  action: string;
  result: string;
}

export const DOWNLOAD_COMPLETE_FLOW: FlowStep[] = [
  {
    step: 1,
    actor: "HttpDownloader",
    action: "downloadNormal() completes — temp video + subtitle files in cache",
    result: "tempVideo.mp4, subtitle_0.vtt, subtitle_1.vtt in TempDownloadCache",
  },
  {
    step: 2,
    actor: "DownloadStorageProvider",
    action: "publishVideoFile() — writes .data.json (content identity), cover.jpg, .nomedia, video file, subtitle files to SAF folder",
    result: "<root>/video/<title>/.data.json + episodes/<title> - E00001.mp4 + subtitles/subtitle_E00001_english_0.vtt",
  },
  {
    step: 3,
    actor: "HttpDownloader",
    action: "D-241: builds DownloadedEpisodeInfo from task + publishResult + tempVideo.length()",
    result: "episodeInfo = { episodeKey, episodeNumber, videoUri, subtitleUris, quality, videoServer, audioVariant, downloadedAt, fileSize }",
  },
  {
    step: 4,
    actor: "DownloadStorageProvider",
    action: "upsertEpisodeInDataJson(folder, episodeInfo) — reads existing .data.json, removes any entry with same episodeKey, appends new entry, sorts by episodeNumber, writes back",
    result: ".data.json now has the new episode in its episodes list",
  },
  {
    step: 5,
    actor: "DownloadQueue",
    action: "insertDownloadedEpisode(downloadedEp) — inserts into SQLite downloaded_episode table",
    result: "DB row created (the in-app cache); .data.json is the durable source of truth",
  },
];

export const EPISODE_DELETE_FLOW: FlowStep[] = [
  {
    step: 1,
    actor: "DefaultDownloadManager",
    action: "deleteDownloadedEpisode(mainId, episodeKey) called by UI",
    result: "Looks up content folder via storage.findContentFolder(mainId)",
  },
  {
    step: 2,
    actor: "DefaultDownloadManager",
    action: "Deletes video file (episodes/<title> - E00001.mp4) + subtitle files (subtitles/subtitle_E00001_*.vtt) from SAF folder",
    result: "Files removed from disk (best-effort)",
  },
  {
    step: 3,
    actor: "DownloadStore",
    action: "deleteDownloadedEpisode(mainId, episodeKey) — deletes the DB row",
    result: "SQLite downloaded_episode row removed",
  },
  {
    step: 4,
    actor: "DownloadStorageProvider",
    action: "D-241: removeEpisodeFromDataJson(folder, episodeKey) — reads .data.json, filters out the entry with matching episodeKey (with v2 fallback via derived mainId|epNumPadded), writes back",
    result: ".data.json episodes list no longer contains the deleted episode",
  },
  {
    step: 5,
    actor: "DefaultDownloadManager",
    action: "refreshDownloadedEpisodes() — reloads the in-memory cache from DB",
    result: "UI updates to show the episode as NotDownloaded",
  },
];

export const SCAN_REBUILD_FLOW: FlowStep[] = [
  {
    step: 1,
    actor: "AnikutaApp.onCreate",
    action: "Launches coroutine on Dispatchers.IO → downloadManager.requestFolderRescan()",
    result: "Triggers DownloadScanner.scan() + refreshDownloadedEpisodes()",
  },
  {
    step: 2,
    actor: "DownloadScanner",
    action: "Walks <root>/{video,images,text,audio}/ — for each content folder, reads .data.json",
    result: "ContentDataJson parsed (with episodes list from prior downloads)",
  },
  {
    step: 3,
    actor: "DownloadScanner",
    action: "upsertContentRecord(dataJson) — INSERT OR REPLACE into main_entry (restores OLD mainId)",
    result: "DB content row restored with the durable mainId from .data.json",
  },
  {
    step: 4,
    actor: "DownloadScanner",
    action: "reconcileDataJsonFromContent() — fetches latest DB state, writes back to .data.json if any FK field changed (D-240: preserves non-null .data.json values when DB is null)",
    result: ".data.json metadata kept in sync with DB (post-AniList-sync, post-extension-refresh)",
  },
  {
    step: 5,
    actor: "DownloadScanner",
    action: "D-241: walks episodes/ subfolder, builds rebuiltEpisodes list — for each video file, constructs DownloadedEpisodeInfo (reusing metadata from existing .data.json episodes list matched by episodeKey)",
    result: "rebuiltEpisodes: List<DownloadedEpisodeInfo> — one per on-disk video file",
  },
  {
    step: 6,
    actor: "DownloadScanner",
    action: "insertDownloadedEpisode() for each — restores downloaded_episode DB rows",
    result: "DB cache rebuilt; user sees episodes as Downloaded",
  },
  {
    step: 7,
    actor: "DownloadStorageProvider",
    action: "D-241: replaceEpisodesInDataJson(folder, rebuiltEpisodes) — IF the list changed (avoids unnecessary SAF I/O on every startup)",
    result: ".data.json episodes list is the canonical on-disk state",
  },
  {
    step: 8,
    actor: "DownloadScanner",
    action: "Orphan cleanup — D-240 GUARD: if contentCount == 0 && allDbEpisodes.isNotEmpty(), SKIP cleanup (SAF folder may be inaccessible)",
    result: "No mass data loss when SD card unmounted / permissions revoked",
  },
];

/* ---------------------------------------------------------------------------
 * Tracking functionality audit
 * ------------------------------------------------------------------------- */

export interface TrackingSystem {
  name: string;
  module: string;
  purpose: string;
  storage: string;
  status: "shipped" | "partial" | "planned";
  notes: string;
}

export const TRACKING_SYSTEMS: TrackingSystem[] = [
  {
    name: "Activity Tracker (PRIMARY, internal)",
    module: ":core:activity-tracker",
    purpose:
      "Records EVERYTHING the user does — watch events, searches, downloads, library changes, ratings, extension events, app events. The user's own stats source.",
    storage: "activity_event table (SQLite). 365-day default retention, unlimited option.",
    status: "shipped",
    notes:
      "Write-batched (50 events or 30s flush). 22 event types (WATCH_START/PAUSE/RESUME/COMPLETE/SEEK, LIBRARY_ADD/REMOVE, SEARCH, DOWNLOAD_START/COMPLETE/PAUSE/DELETE, RATING, EXTENSION_*, APP_OPEN/CLOSE, SCREEN_VIEW). Stats calc deferred to Phase 6.",
  },
  {
    name: "Watch Progress",
    module: ":core:watch-progress",
    purpose:
      "Per-episode watch position + duration + completed flag. Drives the resume-playback feature + the watched styling on episode lists.",
    storage: "watch_progress table (SQLite). Keyed by episodeKey (= mainId|epNumPadded).",
    status: "shipped",
    notes:
      "Two-flag auto-mark state machine (CF1): (completed AND NOT autoMarkSuppressed) OR userMarkedWatched. 85% auto-mark threshold (configurable). Swipe-to-toggle. Live reactive Flow updates.",
  },
  {
    name: "Tracker API (contract)",
    module: ":core:tracker-api",
    purpose:
      "Contract for EXTERNAL trackers (AniList, MAL, Shikimori). OAuth login + entry sync + search.",
    storage: "N/A — interface only. Implementations store their own state.",
    status: "shipped",
    notes:
      "Tracker interface: observeLoginState(), startLogin(), handleLoginCallback(), syncEntry(TrackEntry), fetchEntry(trackerId), search(query). TrackerLoginState + TrackerSyncState are reactive Flow.",
  },
  {
    name: "AniList Tracker",
    module: ":core:tracker-anilist",
    purpose:
      "AniList implementation of Tracker. OAuth login, sync watch progress + status to AniList, search anime.",
    storage: "track table (SQLite) + AniList API. OAuth token in encrypted prefs.",
    status: "shipped",
    notes:
      "TrackSyncManager relays data from the internal ActivityTracker to AniList. AniListOAuth handles the OAuth flow (browser-based). Sync state is reactive.",
  },
  {
    name: "Download Tracking (D-241)",
    module: ":core:download (DownloadStorageProvider + DownloadScanner)",
    purpose:
      "Per-episode download state — which episodes are downloaded, their quality, server, audio variant, file size, download timestamp.",
    storage:
      "DUAL: (1) downloaded_episode table (SQLite, the in-app cache), (2) .data.json episodes list (the DURABLE source of truth, survives reinstall).",
    status: "shipped",
    notes:
      "D-241 wired up the .data.json episodes list: upsert on download complete (HttpDownloader), remove on episode delete (DefaultDownloadManager), rebuild on scan (DownloadScanner). All best-effort — a .data.json write failure never fails the parent operation.",
  },
  {
    name: "Update Tracker",
    module: ":core:updates + :feature:updates",
    purpose:
      "Tracks new episode releases from extensions. Drives the Updates screen + notifications.",
    storage: "episode_update + anime_update_state tables (SQLite).",
    status: "shipped",
    notes:
      "WorkManager smart engine: T1 status filter, T2 next_check_at with backoff, T3 self-improving, T4 sub/dub, T7 concurrency, M3 3-strike, M5 suppress-watched.",
  },
  {
    name: "Schedule Tracker",
    module: ":core:schedule",
    purpose:
      "Tracks AniList airing schedule — countdown to next episode, actual release time.",
    storage: "episode_schedule table (SQLite).",
    status: "shipped",
    notes:
      "AniList airing API + ActualReleaseUpdater interface (SC-2). Live countdown on the Schedule screen.",
  },
  {
    name: "Ratings Tracker",
    module: ":core:ratings",
    purpose:
      "User ratings for anime + individual episodes. Drives the rating UI on details + watch screens.",
    storage: "user_rating + user_episode_rating tables (SQLite).",
    status: "shipped",
    notes:
      "Per-anime + per-episode. RatingStore with reactive Flow.",
  },
  {
    name: "Notification Tracker",
    module: ":core:notifications",
    purpose:
      "Tracks which notifications were sent (dedup) + per-anime notification config.",
    storage: "notification_config + notification_sent tables (SQLite).",
    status: "shipped",
    notes:
      "4 channels (release/schedule/download/system). Per-anime config. Dedup via notification_sent table.",
  },
  {
    name: "Continue Watching",
    module: ":core:watch-progress (getContinueWatching query)",
    purpose:
      "Derives the continue-watching rail from watch progress — episodes the user started but hasn't finished.",
    storage: "Derived from watch_progress table (no separate storage).",
    status: "shipped",
    notes:
      "observeContinueWatching Flow. UI deferred (rail not yet rendered on the home screen).",
  },
];

/* ---------------------------------------------------------------------------
 * The v3 .data.json example (full)
 * ------------------------------------------------------------------------- */

export const DATA_JSON_V3_EXAMPLE = `{
  "schemaVersion": 3,
  "mainId": "550e8400-e29b-41d4-a716-446655440000",
  "contentId": "anilist:aniyomi:none:com.aniyomi.anikoto:69023:https://anikoto.example.com/anime/123",
  "title": "Jujutsu Kaisen",
  "contentType": "anime",
  "contentFormat": "video",
  "description": "A boy swallows a cursed talisman...",
  "dataSourceId": 1,
  "systemId": 1,
  "extensionRepoId": null,
  "extensionId": 69023,
  "sourceId": 69023,
  "animeUrl": "https://anikoto.example.com/anime/123",
  "displaySource": "extension",
  "coverUrl": "https://cdn.example.com/covers/123.jpg",
  "anilistId": 101522,
  "episodes": [
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00001",
      "episodeNumber": 1.0,
      "episodeUrl": "https://anikoto.example.com/anime/123/ep1",
      "episodeName": "Episode 1 — The Beginning",
      "videoUrl": "https://cdn.example.com/video/123-ep1.mp4",
      "videoUri": "content://com.android.externalstorage.documents/tree/...%2FJujutsu%20Kaisen%2Fepisodes%2FJujutsu%20Kaisen%20-%20E00001.mp4",
      "subtitleUris": [
        "content://com.android.externalstorage.documents/tree/...%2FJujutsu%20Kaisen%2Fsubtitles%2Fsubtitle_E00001_english_0.vtt"
      ],
      "quality": "1080p",
      "videoServer": "AniKoto",
      "audioVariant": "sub",
      "downloadedAt": 1786069380000,
      "fileSize": 524288000
    },
    {
      "episodeKey": "550e8400-e29b-41d4-a716-446655440000|00002",
      "episodeNumber": 2.0,
      "episodeUrl": "https://anikoto.example.com/anime/123/ep2",
      "episodeName": "Episode 2 — Cursed Womb",
      "videoUrl": "https://cdn.example.com/video/123-ep2.mp4",
      "videoUri": "content://com.android.externalstorage.documents/tree/...%2FJujutsu%20Kaisen%2Fepisodes%2FJujutsu%20Kaisen%20-%20E00002.mp4",
      "subtitleUris": [],
      "quality": "1080p",
      "videoServer": "AniKoto",
      "audioVariant": "sub",
      "downloadedAt": 1786155780000,
      "fileSize": 503316480
    }
  ],
  "createdAt": 1786069380000,
  "updatedAt": 1786155780000
}`;

/* ---------------------------------------------------------------------------
 * Files modified (D-240 + D-241)
 * ------------------------------------------------------------------------- */

export interface FileChange {
  file: string;
  linesAdded: number;
  linesRemoved: number;
  summary: string;
  decisions: string[];
}

export const FILE_CHANGES: FileChange[] = [
  {
    file: "core/download/.../ContentDataJson.kt",
    linesAdded: 95,
    linesRemoved: 25,
    summary:
      "Schema v3. Added episodes list field + DownloadedEpisodeInfo data class (with episodeKey, videoUri, subtitleUris for D-241). Bumped CURRENT_SCHEMA_VERSION to 3.",
    decisions: ["D-240", "D-241"],
  },
  {
    file: "core/download/.../DownloadStorageProvider.kt",
    linesAdded: 180,
    linesRemoved: 25,
    summary:
      "Refactored writeDataJson to delegate to writeDataJsonRaw helper. Added 3 new public methods: upsertEpisodeInDataJson, removeEpisodeFromDataJson, replaceEpisodesInDataJson. Added deriveEpNumPadded helper for v2-fallback matching.",
    decisions: ["D-241"],
  },
  {
    file: "core/download/.../DownloadScanner.kt",
    linesAdded: 95,
    linesRemoved: 20,
    summary:
      "Rebuild episodes list from on-disk file walk (reinstall recognition). D-240: orphan cleanup guard (skip if contentCount == 0). D-240: null-overwrite fix in reconcileDataJsonFromContent.",
    decisions: ["D-240", "D-241"],
  },
  {
    file: "core/download/.../HttpDownloader.kt",
    linesAdded: 35,
    linesRemoved: 0,
    summary:
      "D-241: after publishVideoFile, build DownloadedEpisodeInfo + call upsertEpisodeInDataJson. Best-effort (failure doesn't fail the download).",
    decisions: ["D-241"],
  },
  {
    file: "core/download/.../DefaultDownloadManager.kt",
    linesAdded: 15,
    linesRemoved: 0,
    summary:
      "D-241: in deleteDownloadedEpisode, after DB delete, call removeEpisodeFromDataJson. Best-effort.",
    decisions: ["D-241"],
  },
  {
    file: "core/download/.../DownloadQueue.kt",
    linesAdded: 5,
    linesRemoved: 0,
    summary:
      "Comment-only — points to HttpDownloader as the upsert site (HttpDownloader has the storage ref + all task context).",
    decisions: ["D-241"],
  },
  {
    file: "core/app-update/.../GitHubUpdateSource.kt",
    linesAdded: 12,
    linesRemoved: 8,
    summary:
      "D-240: derive BOTH version codes from version NAMES (symmetric scale). parseVersionCode(release.versionName) vs parseVersionCode(currentVersionName).",
    decisions: ["D-240"],
  },
  {
    file: "build-logic/.../AndroidConfig.kt",
    linesAdded: 2,
    linesRemoved: 2,
    summary:
      "D-240: bumped versionCode 23 → 24, versionName 0.2.22 → 0.2.23.",
    decisions: ["D-240"],
  },
  {
    file: "core/database/.../DatabaseDriverFactory.kt",
    linesAdded: 22,
    linesRemoved: 4,
    summary:
      "D-240: migration only drops download_queue (NOT downloaded_episode). Added ALTER TABLE for downloaded_episode's D-192 columns (source_id, video_server, video_audio) with hasColumn() guards.",
    decisions: ["D-240"],
  },
  {
    file: "core/content/.../ContentResolver.kt",
    linesAdded: 35,
    linesRemoved: 0,
    summary:
      "D-240: contentId-based fallback lookup in resolveOrCreateForAniList + resolveOrCreateForExtension. Before creating a new mainId, try repo.getMainEntryByContentId(fallbackContentId).",
    decisions: ["D-240"],
  },
];

/* ---------------------------------------------------------------------------
 * Verification checklist
 * ------------------------------------------------------------------------- */

export interface VerificationItem {
  id: string;
  description: string;
  method: string;
  status: FixStatus;
  notes: string;
}

export const VERIFICATION_CHECKLIST: VerificationItem[] = [
  {
    id: "V1",
    description: "Auto-update no longer reports a false update on v0.2.23",
    method: "Install v0.2.23 APK → open Settings → Check for updates. Should say 'up to date' (GitHub latest is v0.2.6).",
    status: "pending",
    notes: "Requires APK build via GitHub Actions (workflow: build-apk.yml).",
  },
  {
    id: "V2",
    description: "Download an episode → .data.json has the episode in its episodes list",
    method: "Download an episode → adb pull the .data.json file → verify the episodes array contains the just-downloaded episode with episodeKey, quality, videoServer, audioVariant, downloadedAt, fileSize.",
    status: "pending",
    notes: "Requires APK build + a working extension + a test download.",
  },
  {
    id: "V3",
    description: "Delete an episode → .data.json episodes list no longer contains it",
    method: "After V2, delete the episode from the Downloads screen → adb pull .data.json → verify the episodes array no longer contains the deleted episodeKey.",
    status: "pending",
    notes: "Requires APK build + a completed download (V2).",
  },
  {
    id: "V4",
    description: "Reinstall → scanner restores downloaded episodes from .data.json",
    method: "After V2, uninstall the app → reinstall → select the same SAF folder → verify the episode shows as Downloaded on the details page.",
    status: "pending",
    notes: "Requires APK build + a completed download (V2).",
  },
  {
    id: "V5",
    description: "App update (adb install -r) → downloaded episodes survive",
    method: "After V2, build a new APK → adb install -r → verify the episode still shows as Downloaded.",
    status: "pending",
    notes: "Requires APK build + a completed download (V2).",
  },
  {
    id: "V6",
    description: "Orphan cleanup doesn't fire when SAF folder is inaccessible",
    method: "After V2, revoke SAF permission → restart app → grant permission again → verify the episode still shows as Downloaded (not deleted by orphan cleanup).",
    status: "pending",
    notes: "Requires APK build + a completed download (V2).",
  },
  {
    id: "V7",
    description: "ContentId fallback linking works after reinstall",
    method: "After V4 (reinstall), open the same anime via the extension (not AniList) → verify the episode shows as Downloaded (not orphaned).",
    status: "pending",
    notes: "Requires APK build + a completed download (V2) + reinstall (V4).",
  },
  {
    id: "V8",
    description: "Code review (static) — no compile errors",
    method: "Sub-agent code review of all 6 modified Kotlin files.",
    status: "done",
    notes: "Task ID 11. Verdict: ✅ no BLOCKING compile errors. 1 minor fix applied (episodeName preservation in DownloadScanner).",
  },
];

/* ---------------------------------------------------------------------------
 * Footer nav
 * ------------------------------------------------------------------------- */

export const D240_NAV_FOOTER = {
  prev: { label: "Downloads Plan", href: "/downloads-plan/" },
  next: { label: "Project Review", href: "/project-review/" },
};
