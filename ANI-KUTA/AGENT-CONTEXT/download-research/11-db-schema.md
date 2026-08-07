# 11 — Database Schema for Downloads (POST-REWRITE)

> **Task ID:** DL-PLAN-REWRITE
> This is the post-rewrite version. The OLD project's JSON-in-SharedPreferences approach is IRRELEVANT — we use SQLDelight (already exists in the new project) + a NEW `data.json`-based reinstall recognition layer.
> **Cross-references:** `04-storage-paths.md` (the `data.json` schema + scan-on-startup) · `13-implementation-plan.md` Phase D.0 (schema migration) · `02-queue-management.md` (queue queries).

## 1. Bottom line: SQLDelight + `data.json` (the dual-storage model)

The new project uses a **dual-storage model**:

| Layer | Where | Purpose |
|---|---|---|
| **`data.json` (durable)** | One per content folder, in the user's SAF folder | The SOURCE OF TRUTH for reinstall recognition. Survives app-uninstall + reinstall + same-folder-selection. |
| **SQLDelight DB (cache/index)** | `download_queue` + `downloaded_episode` tables in `anikuta.db` | A fast queryable cache. Rebuilt from `data.json` files on scan-on-startup. Can be wiped without data loss. |

The OLD project used **JSON-in-SharedPreferences** for the queue + completed list. We don't — we use SQLDelight (the tables already exist) AND we add the `data.json` layer for reinstall recognition.

## 2. Why the dual-storage model (not just SQLDelight, not just `data.json`)

- **SQLDelight alone is NOT enough** — the DB is wiped on app-uninstall. The user loses their library every time they reinstall.
- **`data.json` alone is NOT enough** — reading every `data.json` on every UI render would be slow (SAF round-trip per file). We need a queryable cache.
- **Together:** `data.json` is the durable source of truth; SQLDelight is the fast cache. The scan-on-startup reconciles them.

This is the same model Android's MediaStore uses (durable files + a queryable index that can be rebuilt).

## 3. The new SQLDelight schema (re-keyed by `mainId` + `episodeKey`)

### 3.1 The current schema (the stub that already exists)

**File:** `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadQueue.sq`

```sql
CREATE TABLE IF NOT EXISTS download_queue (
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

insertDownloadQueue:
INSERT INTO download_queue(episode_key, state, queued_at)
VALUES (?, 'QUEUED', ?);

updateDownloadState:
UPDATE download_queue
SET state = ?, progress = ?, error_message = ?, started_at = ?, completed_at = ?
WHERE id = ?;

getDownloadQueue:
SELECT * FROM download_queue
WHERE state IN ('QUEUED', 'DOWNLOADING', 'PAUSED')
ORDER BY queued_at ASC;

getDownloadQueueByState:
SELECT * FROM download_queue
WHERE state = ?
ORDER BY queued_at ASC;

deleteDownloadQueue:
DELETE FROM download_queue WHERE id = ?;

deleteDownloadQueueByEpisode:
DELETE FROM download_queue WHERE episode_key = ?;
```

**File:** `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadedEpisode.sq`

```sql
CREATE TABLE IF NOT EXISTS downloaded_episode (
    episode_key TEXT NOT NULL PRIMARY KEY,
    file_path TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    quality TEXT,
    downloaded_at INTEGER NOT NULL
);

insertDownloadedEpisode:
INSERT OR REPLACE INTO downloaded_episode(episode_key, file_path, file_size, quality, downloaded_at)
VALUES (?, ?, ?, ?, ?);

getDownloadedEpisode:
SELECT * FROM downloaded_episode WHERE episode_key = ?;

getAllDownloadedEpisodes:
SELECT * FROM downloaded_episode ORDER BY downloaded_at DESC;

isEpisodeDownloaded:
SELECT EXISTS(SELECT 1 FROM downloaded_episode WHERE episode_key = ?);

deleteDownloadedEpisode:
DELETE FROM downloaded_episode WHERE episode_key = ?;
```

**Problems with the current schema:**
1. **Keys by `episode_key` (a string)** — no relationship to `mainId` or `contentId`. Can't query "all COMPLETED for `mainId` X" without deserializing every row.
2. **Missing columns** — no `video_url`, `video_headers`, `subtitle_tracks`, `audio_tracks`, `source_id`, `video_server`, `video_quality`, `video_audio`, `content_title`, `cover_url`, `cover_color`, `downloaded_bytes`, `total_bytes`, `video_uri`, `subtitle_uris`, `resolve_context`. The full `DownloadRequest` data isn't persisted.
3. **No `mainId`** — the new project's content system keys by `mainId` (see `ContentModels.kt`'s `ContentRecord`). The download system should align.
4. **No `content_format`** — the new storage system uses `video/`, `images/`, `text/` format folders. The DB should track which format folder the file is in.

### 3.2 The NEW schema (re-keyed + expanded)

**File:** `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadQueue.sq` (REWRITE)

```sql
-- Download queue table — RE-KEYED by mainId + episodeKey (5-digit padded episode number).
-- The episode_key is "$mainId|$episodeNumberPadded5" (e.g. "550e8400...|00001").
-- Composite UNIQUE constraint on (main_id, episode_key) prevents duplicate queue entries
-- for the same episode of the same content.
--
-- The DB is a CACHE. The data.json files in the user's SAF folder are the SOURCE OF TRUTH
-- for reinstall recognition. On app start, the DownloadScanner walks the SAF folder,
-- reads each data.json, and UPSERTs the content into this table by mainId.
-- See 04-storage-paths.md §7 (scan-on-startup).

CREATE TABLE IF NOT EXISTS download_queue (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,

    -- ── Identity (REQUIRED) ──
    main_id TEXT NOT NULL,                  -- the stable UUID (matches ContentRecord.mainId)
    episode_key TEXT NOT NULL,              -- "$mainId|$episodeNumberPadded5" — stable across source switches

    -- ── Content + episode context (denormalized for queryability) ──
    content_id TEXT NOT NULL,               -- structured content ID (changes on source switch)
    content_title TEXT NOT NULL,            -- human-readable title (for grouping UI)
    content_format TEXT NOT NULL DEFAULT 'video',  -- "video" | "images" | "text" | "audio"
    content_type TEXT NOT NULL DEFAULT 'anime',    -- "anime" | "movie" | "series" | "manga" | "novel" | ...
    cover_url TEXT,                         -- for notification thumbnails
    cover_color INTEGER,                    -- for UI tinting
    episode_number REAL NOT NULL,           -- the raw episode number (1.0, 12.5, etc.)
    episode_name TEXT NOT NULL DEFAULT '',  -- human-readable episode name (for UI display)
    episode_url TEXT,                       -- the extension's episode URL (for re-resolving)

    -- ── Download source info ──
    source_id INTEGER,                      -- extension source ID (nullable — NULL means "no source", NOT 0;
                                            -- see Review 1 I2. ContentRecord.sourceId is `Long?`, so 0 is a fake sentinel.)
    video_url TEXT NOT NULL,                -- the URL to download (prefer directUrl over proxy URL)
    video_headers TEXT,                     -- newline-separated "Key: Value" headers
    subtitle_tracks TEXT,                   -- JSON array of DownloadTrack
    audio_tracks TEXT,                      -- JSON array of DownloadTrack
    video_server TEXT NOT NULL DEFAULT '',  -- captured at resolve time
    video_quality TEXT NOT NULL DEFAULT '', -- captured at resolve time
    video_audio TEXT NOT NULL DEFAULT '',   -- captured at resolve time

    -- ── The proxy-churn re-resolve context (NEW — see 10-player-integration.md §14) ──
    resolve_context TEXT,                   -- JSON-encoded ResolveContext (sourceId, episodeUrl, server, audio, quality)
                                            -- used by HttpDownloader.downloadNormal to re-resolve on IOException for localhost URLs

    -- ── State + progress ──
    state TEXT NOT NULL,                    -- "QUEUED" | "DOWNLOADING" | "RETRYING" | "PAUSED" | "COMPLETED" | "ERROR" | "CANCELLED"
                                            -- RETRYING is the auto-retry in-progress state (see 03-state-machine.md + 16-quality-of-life.md §1.3).
                                            -- resetDownloadingToQueued (below) resets BOTH DOWNLOADING and RETRYING to QUEUED on restart.
    progress INTEGER NOT NULL DEFAULT 0,    -- 0..100 (capped at 95 during download, 100 on completion)
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    total_bytes INTEGER NOT NULL DEFAULT -1,  -- -1 = unknown
    error_message TEXT,

    -- ── Result (set on COMPLETED) ──
    video_uri TEXT,                         -- content:// URI of the published video file (in the user's SAF folder)
    subtitle_uris TEXT,                     -- JSON array of content:// URIs

    -- ── Timestamps ──
    queued_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    updated_at INTEGER NOT NULL
);

-- Indexes for the common UI queries.
CREATE INDEX IF NOT EXISTS idx_download_queue_state ON download_queue(state);
CREATE INDEX IF NOT EXISTS idx_download_queue_main_id ON download_queue(main_id);
CREATE INDEX IF NOT EXISTS idx_download_queue_main_id_state ON download_queue(main_id, state);
CREATE UNIQUE INDEX IF NOT EXISTS uq_download_queue_main_episode ON download_queue(main_id, episode_key);

-- ── Queries ──

insertDownloadQueue:
INSERT INTO download_queue(
    main_id, episode_key, content_id, content_title, content_format, content_type,
    cover_url, cover_color, episode_number, episode_name, episode_url,
    source_id, video_url, video_headers, subtitle_tracks, audio_tracks,
    video_server, video_quality, video_audio, resolve_context,
    state, queued_at, updated_at
) VALUES (
    ?, ?, ?, ?, ?, ?,
    ?, ?, ?, ?, ?,
    ?, ?, ?, ?, ?,
    ?, ?, ?, ?,
    'QUEUED', ?, ?
);

updateDownloadState:
UPDATE download_queue
SET state = ?, progress = ?, error_message = ?, started_at = ?, completed_at = ?, updated_at = ?
WHERE id = ?;

updateDownloadProgress:
-- Throttled to 1/sec by DownloadQueue.persistThrottled(). Single-row update.
UPDATE download_queue
SET progress = ?, downloaded_bytes = ?, total_bytes = ?, updated_at = ?
WHERE id = ?;

updateDownloadResult:
-- Called on COMPLETED. Sets the video_uri + subtitle_uris.
UPDATE download_queue
SET state = 'COMPLETED', progress = 100, video_uri = ?, subtitle_uris = ?, completed_at = ?, updated_at = ?
WHERE id = ?;

updateDownloadResolveContext:
-- Called by HttpDownloader when it re-resolves a localhost URL on IOException.
UPDATE download_queue
SET video_url = ?, resolve_context = ?, updated_at = ?
WHERE id = ?;

getDownloadQueue:
SELECT * FROM download_queue
WHERE state IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'ERROR')
ORDER BY queued_at ASC;

getDownloadQueueByState:
SELECT * FROM download_queue
WHERE state = ?
ORDER BY queued_at ASC;

getDownloadQueueByMainId:
SELECT * FROM download_queue
WHERE main_id = ?
ORDER BY episode_number ASC;

getDownloadTask:
SELECT * FROM download_queue WHERE id = ?;

getDownloadTaskByEpisode:
SELECT * FROM download_queue WHERE main_id = ? AND episode_key = ?;

deleteDownloadQueue:
DELETE FROM download_queue WHERE id = ?;

deleteDownloadQueueByEpisode:
DELETE FROM download_queue WHERE main_id = ? AND episode_key = ?;

deleteDownloadQueueByMainId:
DELETE FROM download_queue WHERE main_id = ?;

-- Called on startup to reset DOWNLOADING + RETRYING tasks to QUEUED.
-- (The old project's bug fix extended to RETRYING — see Review 3 I2 / Review 4 C7 / REVIEW-5 M6.)
-- A task that crashed mid-download (DOWNLOADING) OR mid-retry (RETRYING) is reset to QUEUED so the
-- engine can pick it back up. PAUSED + COMPLETED + ERROR + CANCELLED + QUEUED are left untouched.
resetDownloadingToQueued:
UPDATE download_queue
SET state = 'QUEUED', started_at = NULL, updated_at = ?
WHERE state IN ('DOWNLOADING', 'RETRYING');

-- Called by ContentRepository.updateContentSources when a source switch changes a content's contentId.
-- Keeps the download_queue.content_id column in sync with content.content_id (see Review 1 I4 / REVIEW-5 M7).
-- If option (a) of M7 is chosen instead (drop the column), this query is unnecessary — but we keep
-- the column for debug/log visibility, so we add the sync query.
updateDownloadContentId:
UPDATE download_queue
SET content_id = ?, updated_at = ?
WHERE main_id = ? AND content_id != ?;
```

**File:** `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadedEpisode.sq` (REWRITE)

```sql
-- Downloaded episode files on disk — RE-KEYED by mainId + episodeKey.
-- This table is populated when a download completes (or by the DownloadScanner on startup
-- when it discovers files in the user's SAF folder).
-- The DB is a CACHE. The data.json files are the SOURCE OF TRUTH.

CREATE TABLE IF NOT EXISTS downloaded_episode (
    main_id TEXT NOT NULL,
    episode_key TEXT NOT NULL,

    -- ── File location (in the user's SAF folder) ──
    content_folder_uri TEXT NOT NULL,       -- the DocumentFile URI of the content folder (<root>/video/<title>/)
    video_uri TEXT NOT NULL,                -- content:// URI of the video file
    subtitle_uris TEXT NOT NULL DEFAULT '[]', -- JSON array of content:// URIs

    -- ── Content + episode context (denormalized for the Downloads UI grouping) ──
    content_title TEXT NOT NULL,
    content_format TEXT NOT NULL DEFAULT 'video',
    content_type TEXT NOT NULL DEFAULT 'anime',
    cover_url TEXT,
    cover_color INTEGER,
    episode_number REAL NOT NULL,
    episode_name TEXT NOT NULL DEFAULT '',
    video_file_name TEXT NOT NULL,          -- "Jujutsu Kaisen - E00001.mp4" (matches the file on disk)

    -- ── Download source info (captured at resolve time, informational) ──
    quality TEXT,
    server TEXT,
    audio TEXT,
    source_id INTEGER,                      -- nullable (matches ContentRecord.sourceId: Long?) — Review 1 I2

    -- ── File metadata ──
    file_size INTEGER NOT NULL,             -- in bytes (for free-space checks + verification)
    downloaded_at INTEGER NOT NULL,
    verified_at INTEGER,                    -- last time the file was verified to exist + be non-empty

    PRIMARY KEY (main_id, episode_key)
);

-- NOTE: `idx_downloaded_episode_main_id` was removed — `main_id` is the leftmost column of the
-- composite PRIMARY KEY (main_id, episode_key), so SQLite already uses the PK index for queries
-- on `main_id` alone. The explicit index was redundant (storage + write overhead for no benefit).
-- See Review 1 I3 / REVIEW-5 (consolidated list, section G).
CREATE INDEX IF NOT EXISTS idx_downloaded_episode_downloaded_at ON downloaded_episode(downloaded_at);

insertDownloadedEpisode:
INSERT OR REPLACE INTO downloaded_episode(
    main_id, episode_key, content_folder_uri, video_uri, subtitle_uris,
    content_title, content_format, content_type, cover_url, cover_color,
    episode_number, episode_name, video_file_name,
    quality, server, audio, source_id,
    file_size, downloaded_at, verified_at
) VALUES (
    ?, ?, ?, ?, ?,
    ?, ?, ?, ?, ?,
    ?, ?, ?,
    ?, ?, ?, ?,
    ?, ?, ?
);

getDownloadedEpisode:
SELECT * FROM downloaded_episode WHERE main_id = ? AND episode_key = ?;

getDownloadedEpisodesByMainId:
SELECT * FROM downloaded_episode WHERE main_id = ? ORDER BY episode_number ASC;

getAllDownloadedEpisodes:
SELECT * FROM downloaded_episode ORDER BY downloaded_at DESC;

-- Groups episodes by main_id and returns one row per content.
-- `MAX(...)` aggregates the denormalized columns deterministically (SQLite's "bare columns" rule
-- would otherwise pick an arbitrary row's value, which is non-deterministic if a re-download
-- changed the title/cover mid-stream). `DISTINCT` is redundant with `GROUP BY` and was removed
-- (see Review 1 C5 / REVIEW-5 M3).
getDownloadedMainIds:
SELECT
    main_id,
    MAX(content_title)    AS content_title,
    MAX(content_format)   AS content_format,
    MAX(cover_url)        AS cover_url,
    MAX(cover_color)      AS cover_color
FROM downloaded_episode
GROUP BY main_id
ORDER BY MAX(downloaded_at) DESC;

isEpisodeDownloaded:
SELECT EXISTS(SELECT 1 FROM downloaded_episode WHERE main_id = ? AND episode_key = ?);

getDownloadedVideoUri:
SELECT video_uri FROM downloaded_episode WHERE main_id = ? AND episode_key = ?;

getDownloadedSubtitleUris:
SELECT subtitle_uris FROM downloaded_episode WHERE main_id = ? AND episode_key = ?;

deleteDownloadedEpisode:
DELETE FROM downloaded_episode WHERE main_id = ? AND episode_key = ?;

deleteDownloadedEpisodeByMainId:
DELETE FROM downloaded_episode WHERE main_id = ?;

-- Called by the DownloadScanner when a data.json says an episode should be here
-- but the file is missing on disk.
markEpisodeMissing:
DELETE FROM downloaded_episode WHERE main_id = ? AND episode_key = ?;
```

### 3.3 Migration — `3.sqm` is NOT the right approach (REVIEW-5 M1 + M2 fix)

> **CRITICAL:** The original draft of this section claimed `1.sqm` and `2.sqm` existed in the
> project and proposed adding a `3.sqm`. **That was wrong.** Verified by `find . -name '*.sqm'`
> against `core/database/src/main/sqldelight/`: the project has **ZERO `.sqm` files**.
>
> SQLDelight 2.x (verified `gradle/libs.versions.toml` — `sqldelight = "2.0.2"`) derives the v1
> schema directly from the `.sq` files' `CREATE TABLE IF NOT EXISTS` statements. There is **no
> existing migration chain** to extend. A lone `3.sqm` would either be silently ignored or cause
> the schema version to jump from v1 to v4 with no migration path, crashing existing dev installs
> at startup with `IllegalStateException: Can't migrate database from version 1 to 4 without
> migrations`.
>
> See REVIEW-1 C3 + C4 + REVIEW-5 §4.1 + M1 + M2 for the full diagnosis.

We pick **option (a): edit the `.sq` files directly** (no `.sqm` migration file). This matches
the project's actual state — no shipped beta, no production users, the stub `DownloadManager`
was never used to download anything.

**Concrete steps:**

1. Replace the contents of `core/database/src/main/sqldelight/.../downloadQueue.sq` with the
   NEW schema in §3.2 above. SQLDelight regenerates `AnikutaDatabase` + `DownloadQueueQueries`
   at the next build; the new `CREATE TABLE IF NOT EXISTS` is the canonical v1 schema.
2. Replace the contents of `core/database/src/main/sqldelight/.../downloadedEpisode.sq`
   likewise.
3. **Existing dev installs must wipe app data once** (`adb shell pm clear com.confused.anikuta`
   or uninstall + reinstall). The stub tables' data is not real (the stub `DownloadManager` was
   never used in production), so wiping is safe.
4. **`DatabaseDriverFactory` does NOT need a `migrations = …` arg for this rewrite** — there
   is no migration path to provide. The next schema change after this rewrite (whenever it
   happens) MUST be paired with a real `1.sqm` migration + the `DatabaseDriverFactory` update
   below, so future schema bumps don't crash existing installs.

### 3.4 `DatabaseDriverFactory` — current state + future-proofing (REVIEW-5 M2)

**Current state** (`core/database/.../DatabaseDriverFactory.kt`, verified verbatim):

```kotlin
class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AnikutaDatabase.Schema,
            context = context,
            name = "anikuta.db",
        )
    }
}
```

No `migrations = arrayOf(...)` parameter. SQLDelight 2.x's `AndroidSqliteDriver` constructor
accepts `vararg migrations: SqlDriver.Schema.Feature` (typically `Migration` instances).
Without it, **any future schema-version bump on an existing install crashes the app at startup**.

**For this rewrite (option a):** no change required to `DatabaseDriverFactory` — the `.sq` files
are the v1 schema, no migration is run. Dev installs wipe app data once (per §3.3 step 3).

**For the NEXT schema change (and any future schema bump):** add a `1.sqm` migration file +
update `DatabaseDriverFactory` to pass it. The forward-looking pattern is:

```kotlin
class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AnikutaDatabase.Schema,
            context = context,
            name = "anikuta.db",
            // Future migrations go here. Empty for now (option (a) — no migration needed for the
            // initial rewrite). When 1.sqm is added, pass it: migrations = arrayOf(Migration1).
            // See REVIEW-5 M2.
        )
    }
}
```

The Phase D.0 task list in `13-implementation-plan.md` calls this out explicitly: "Edit the
`.sq` files directly; do NOT add a `3.sqm`. Dev installs must wipe app data once. Future schema
bumps must pair with a real `1.sqm` + `DatabaseDriverFactory` update."

### 3.5 Stale `video_url` / `content_id` after a source switch (REVIEW-5 M60)

When the user switches sources, `ContentRepository.updateContentSources(...)` updates
`content.content_id` for the affected `mainId`. Two download-system columns become stale:

- `download_queue.content_id` — synced by the `updateDownloadContentId` query added in §3.2.
- `download_queue.video_url` — the captured proxy URL is now invalid.

For COMPLETED downloads this doesn't matter (the file is on disk, identified by `mainId` + `episodeKey`).
For QUEUED downloads with a stale URL, the proxy-churn re-resolve path (`10-player-integration.md` §14)
recovers reactively on the first `IOException`. **As a future enhancement** (post-D.8), on app start
the orchestrator should proactively re-resolve any QUEUED task whose `download_queue.content_id`
differs from `content.content_id` (for the same `main_id`) via `ResolveContext` — avoiding the
fail-then-retry round trip. Not a blocker for D.0–D.8.

## 4. The `DownloadStore` adapter

The OLD project had a `DownloadStore` class that wrapped `PreferenceStore.getObject<List<DownloadTask>>`. The NEW project's `DownloadStore` is a thin wrapper around SQLDelight queries:

```kotlin
class DownloadStore(private val database: AnikutaDatabase) {
    private val queueQueries get() = database.downloadQueueQueries
    private val episodeQueries get() = database.downloadedEpisodeQueries

    // ── Queue operations ──
    fun enqueue(request: DownloadRequest, queuedAt: Long): Long {
        // INSERT + return the autoincrement id
        queueQueries.insertDownloadQueue(...)
        return queueQueries.lastInsertedRow().executeAsOne()
    }
    fun updateState(id: Long, state: DownloadStatus, ...) = queueQueries.updateDownloadState(...)
    fun updateProgress(id: Long, progress: Int, downloadedBytes: Long, totalBytes: Long) = queueQueries.updateDownloadProgress(...)
    fun updateResult(id: Long, videoUri: String, subtitleUris: List<String>) = queueQueries.updateDownloadResult(...)
    fun updateResolveContext(id: Long, newUrl: String, newContext: ResolveContext) = queueQueries.updateDownloadResolveContext(...)
    fun getQueue(): List<DownloadTask> = queueQueries.getDownloadQueue().executeAsList().map { it.toModel() }
    fun getByMainId(mainId: String): List<DownloadTask> = queueQueries.getDownloadQueueByMainId(mainId).executeAsList().map { it.toModel() }
    fun delete(id: Long) = queueQueries.deleteDownloadQueue(id)
    fun deleteByEpisode(mainId: String, episodeKey: String) = queueQueries.deleteDownloadQueueByEpisode(mainId, episodeKey)
    fun resetDownloadingToQueued() = queueQueries.resetDownloadingToQueued(System.currentTimeMillis())

    // ── Downloaded-episode operations ──
    fun insertDownloaded(episode: DownloadedEpisode) = episodeQueries.insertDownloadedEpisode(...)
    fun getDownloaded(mainId: String, episodeKey: String): DownloadedEpisode? = episodeQueries.getDownloadedEpisode(mainId, episodeKey).executeAsOneOrNull()?.toModel()
    fun isDownloaded(mainId: String, episodeKey: String): Boolean = episodeQueries.isEpisodeDownloaded(mainId, episodeKey).executeAsOne()
    fun getVideoUri(mainId: String, episodeKey: String): String? = episodeQueries.getDownloadedVideoUri(mainId, episodeKey).executeAsOneOrNull()
    fun deleteDownloaded(mainId: String, episodeKey: String) = episodeQueries.deleteDownloadedEpisode(mainId, episodeKey)
    fun deleteDownloadedByMainId(mainId: String) = episodeQueries.deleteDownloadedEpisodeByMainId(mainId)
    fun markMissing(mainId: String, episodeKey: String) = episodeQueries.markEpisodeMissing(mainId, episodeKey)
}
```

The `toModel()` extension functions map SQLDelight-generated row types to the Kotlin data classes (`DownloadTask`, `DownloadedEpisode`). JSON columns (`subtitle_tracks`, `audio_tracks`, `subtitle_uris`, `resolve_context`) are decoded/encoded via `kotlinx.serialization`.

## 5. Why not JSON-in-SharedPreferences (the OLD project's approach)

The OLD project used `DownloadStore` backed by `PreferenceStore.getObject<List<DownloadTask>>`. Reasons we DON'T:

1. **The tables already exist.** Dropping them OR leaving them unused would be wasteful. SQLDelight is the new project's persistence layer for everything else — using it for downloads is consistent.
2. **Queryable.** "All COMPLETED for `mainId` X" is `SELECT * FROM download_queue WHERE main_id = ? AND state = 'COMPLETED'`. With JSON-in-SharedPrefs, you'd deserialize the whole list + filter in Kotlin.
3. **Survives app crashes.** SharedPreferences writes are atomic per-key, but a crash mid-list-rewrite could lose the whole queue. SQLDelight's per-row updates are atomic per-row.
4. **The `data.json` layer handles reinstall recognition.** The DB doesn't need to be durable — it can be rebuilt from `data.json` files. So we get the speed of SQLDelight + the durability of `data.json`.
5. **The new project's architecture uses SQLDelight everywhere else** (anime library, watch progress, history, categories, etc.). The download system aligning with this is the right call.

## 6. The `data.json` ↔ DB relationship

| Operation | Reads from | Writes to |
|---|---|---|
| Download starts (enqueue) | DB | DB (`download_queue` row inserted) |
| Download progress tick | DB | DB (`updateDownloadProgress`, throttled 1/sec) |
| Download completes | DB | DB (`updateDownloadResult`) + `data.json` (via `DownloadStorageProvider.publishToUserFolder`) + DB (`insertDownloadedEpisode`) |
| Download fails | DB | DB (`updateDownloadState` ERROR) |
| User opens Downloads screen | DB | None (read-only) |
| User opens Downloaded Files screen | DB (`getDownloadedMainIds`) | None |
| User plays an episode offline | DB (`getDownloadedVideoUri`) | None |
| User deletes an episode | DB + `data.json` | Both (`deleteDownloadedEpisode` + `deleteEpisode` in storage) |
| App starts up | SAF folder (scan) | DB (UPSERT rows from `data.json`) |
| User picks a new folder | SAF folder (scan) | DB (UPSERT + reconcile) |
| App crashes mid-download | DB row left in DOWNLOADING | DB reset to QUEUED on next startup (`resetDownloadingToQueued`) |

The DB is the fast path for all UI + engine operations. The `data.json` is read/written only at:
- Download completion (the `publishToUserFolder` step).
- App startup (the scan-on-startup).
- Folder re-selection (the rescan).
- Episode deletion (the `deleteEpisode` step updates `data.json` to remove the episode entry).

## 7. The scan-on-startup reconciliation algorithm

When the app starts (or when the user picks a new folder), the `DownloadScanner` walks the SAF folder + reconciles the DB:

```kotlin
suspend fun scanAndReconcile() {
    val root = storage.rootTree() ?: return
    val now = System.currentTimeMillis()
    val scannedEpisodeKeys = mutableSetOf<Pair<String, String>>()  // (mainId, episodeKey)

    // 1. Walk video/, images/, text/.
    for (formatFolder in listOf("video", "images", "text")) {
        val formatDir = root.findFile(formatFolder)?.takeIf { it.isDirectory } ?: continue
        for (contentDir in formatDir.listFiles()) {
            if (!contentDir.isDirectory) continue
            val dataJsonFile = contentDir.findFile("data.json") ?: continue
            val dataJson = try { readDataJson(contentDir) } catch (e: Exception) { continue }

            for (ep in dataJson.episodes) {
                val videoFile = contentDir.findFile(ep.videoFileName)
                if (videoFile != null && videoFile.isFile && videoFile.length() > 0) {
                    // UPSERT into downloaded_episode.
                    downloadStore.insertDownloaded(DownloadedEpisode(
                        mainId = dataJson.mainId,
                        episodeKey = ep.episodeKey,
                        contentFolderUri = contentDir.uri.toString(),
                        videoUri = videoFile.uri.toString(),
                        // ...
                    ))
                    scannedEpisodeKeys.add(dataJson.mainId to ep.episodeKey)
                }
            }
        }
    }

    // 2. Reconcile: any DB-downloaded episode NOT in the scanned set is "missing" (deleted externally).
    val allDbEpisodes = downloadStore.getAllDownloadedEpisodes()
    for (ep in allDbEpisodes) {
        val key = ep.mainId to ep.episodeKey
        if (key !in scannedEpisodeKeys) {
            downloadStore.markMissing(ep.mainId, ep.episodeKey)
        }
    }

    // 3. Update the scan_state.json under .anikuta/.
    storage.writeScanState(now, scannedEpisodeKeys.size)
}
```

**Why this works for reinstall recognition:**
- The user uninstalls the app → the DB is wiped.
- The user reinstalls + opens the app → picks the same SAF folder.
- The `DownloadScanner` walks the folder, reads every `data.json`, UPSERTs the content into the (fresh) DB.
- All downloads are re-recognized by `mainId`.

**Why the DB can be wiped without data loss:**
- The `data.json` files in the user's SAF folder are durable (not in app data).
- The DB is just a cache — it's rebuilt from `data.json` on the next scan.

## 8. The DB schema vs the `data.json` schema (side-by-side)

| Field | `download_queue` (DB) | `downloaded_episode` (DB) | `ContentDataJson` (data.json) | `EpisodeEntry` (in data.json) |
|---|---|---|---|---|
| mainId | ✅ | ✅ | ✅ | — |
| episodeKey | ✅ | ✅ | — | ✅ |
| contentId | ✅ | — | ✅ | — |
| title | ✅ | ✅ | ✅ | — |
| contentType | ✅ | ✅ | ✅ | — |
| contentFormat | ✅ | ✅ | ✅ | — |
| sourceType | — | — | ✅ | — |
| coverUrl | ✅ | ✅ | ✅ | — |
| anilistId | — | — | ✅ (optional) | — |
| sourceId | ✅ | ✅ | ✅ (optional) | — |
| animeUrl | — | — | ✅ (optional) | — |
| episodeNumber | ✅ | ✅ | — | ✅ |
| episodeName | ✅ | ✅ | — | ✅ |
| videoUrl | ✅ | — | — | — |
| videoUri (result) | ✅ | ✅ | — | — |
| videoFileName | — | ✅ | — | ✅ |
| subtitleUris | ✅ | ✅ | — | ✅ (fileNames) |
| quality | ✅ | ✅ | — | ✅ |
| server | ✅ | ✅ | — | ✅ |
| audio | ✅ | ✅ | — | ✅ |
| state | ✅ | — | — | — |
| progress | ✅ | — | — | — |
| downloadedBytes | ✅ | — | — | — |
| totalBytes | ✅ | — | — | — |
| sizeBytes | — | ✅ | — | ✅ |
| resolveContext | ✅ | — | — | — |
| errorMessage | ✅ | — | — | — |
| timestamps | ✅ (queued/started/completed/updated) | ✅ (downloaded/verified) | ✅ (createdAt/updatedAt) | ✅ (downloadedAt) |

**Observations:**
- The DB stores MORE state than `data.json` (queue state, progress, errors — all transient).
- `data.json` stores LESS state but includes fields the DB doesn't (e.g. `sourceType`, `anilistId`, `animeUrl` — these are stable content metadata, not transient download state).
- The DB is the source of truth for TRANSIENT state (queue, progress). The `data.json` is the source of truth for DURABLE state (content identity + downloaded episode list).

## 9. The `ContentRecord` ↔ `data.json` ↔ DB relationship

The new project's `ContentRecord` (in `core/content/ContentModels.kt`) is the canonical content model. The download system uses it as the source for `data.json` + DB rows:

```
ContentRecord (in DB: content table, key by mainId)
  ↓ (the content repository's UPSERT path)
ContentDataJson (in user's SAF folder: data.json per content)
  ↓ (the DownloadScanner's read path)
downloaded_episode table (in DB: cache for fast UI queries)
```

When a download completes:
1. The orchestrator calls `downloadManager.completeDownload(task)`.
2. The manager:
   a. Writes the `data.json` (via `DownloadStorageProvider.publishToUserFolder`).
   b. Inserts into `downloaded_episode` (via `DownloadStore.insertDownloaded`).
   c. Updates the `download_queue` row (via `DownloadStore.updateDownloadResult`).

When the user opens the Downloads screen:
1. The ViewModel queries `downloadStore.getDownloadedMainIds()` (a single SQL query, fast).
2. For each `mainId`, queries `downloadStore.getDownloadedEpisodesByMainId(mainId)` (a single SQL query per content).
3. The UI groups episodes by `mainId` (NOT by title — see `15-ui-and-bug-analysis.md` §A.11 fix #11).

When the user reinstalls + re-selects the folder:
1. The `DownloadScanner` walks the SAF folder.
2. For each `data.json`, reads the `mainId` + `episodes[]`.
3. UPSERTs into `downloaded_episode` (via `DownloadStore.insertDownloaded` — `INSERT OR REPLACE`).
4. The content table (`content`) is also UPSERTed by `mainId` (via the content repository).
5. The Downloads screen shows everything again.

## 10. Summary

| Aspect | OLD project | NEW project (post-rewrite) |
|---|---|---|
| Persistence medium | SharedPreferences (JSON blob) | SQLDelight tables (`download_queue` + `downloaded_episode`) — already exist; **schema updated in D.0** |
| Schema | None (just a JSON `List<DownloadTask>`) | Re-keyed by `mainId` + `episodeKey`. New columns for full task data + resolve context. |
| Reinstall recognition | ❌ (DB wiped on uninstall) | ✅ `data.json` per content folder (in user's SAF folder) — survives uninstall + reinstall. DB is rebuilt from `data.json` on scan. |
| Reactive | `Preference.changes(): Flow<T>` | `MutableStateFlow` derived from SQLDelight queries (via `DownloadQueue.tasks`) |
| Migrations | `DownloadMigration` (anilistId → content_id) | Edit `.sq` files directly (v1 = new schema) — no `3.sqm` (option (a) per REVIEW-5 M1+M2). Dev installs wipe app data once. Future schema bumps pair with a real `1.sqm` + `DatabaseDriverFactory` update. |
| Queryable | No (must deserialize whole list) | Yes (SQL queries — `WHERE main_id = ?`, `WHERE state = ?`, etc.) |
| Throttling | App-level (`persistThrottled` 1/sec) | DB-level (`updateDownloadProgress` is a single-row UPDATE; throttled to 1/sec by `DownloadQueue`) |
| Source-of-truth | SharedPrefs (the only source) | `data.json` (durable) + SQLDelight (cache). The `data.json` wins on conflict. |

**For the new project:** use the SQLDelight tables (schema updated in D.0), wrap with a `DownloadStore` adapter, and add the `data.json` layer for reinstall recognition. The DB is a cache; the `data.json` files are durable. See `13-implementation-plan.md` Phase D.0 + D.1 for the concrete plan.
