# 11 — Database Schema for Downloads

> Verified by: grepping the old project's SQLDelight `.sq` files for "download" (no matches), reading `DownloadStore.kt`, reading `app/src/main/java/app/confused/anikuta/di/DatabaseModule.kt`, and listing `core/database/src/main/sqldelight/`.

## 1. Bottom line: **Downloads are NOT in the database.**

The old ANI-KUTA project **does not use SQLDelight (or any DB) for download persistence**. The download queue + completed list are persisted as a **JSON-serialized `List<DownloadTask>`** in `SharedPreferences` via `PreferenceStore.getObject(...)`.

The SQLDelight database (`anikuta.db`) contains only these tables (from `core/database/src/main/sqldelight/`):
- `animes` (anime library entries with two-tier identity, status-tracking, library columns)
- `episodes` (episode metadata per anime)
- `anime_category` (join table: anime ↔ category)
- `animehistory` (watch history events)
- `animetrack` (tracker links per anime)
- `categories` (library categories)
- migration files: `1.sqm`, `2.sqm`

**No `download_queue`, `downloaded_episode`, or any download-related table.**

## 2. Why no DB for downloads?

Quoted from `DownloadStore.kt:14-23`:

> Why not SQLDelight? The download state is small (tens of tasks, not thousands) and highly mutable (progress ticks). A pref-backed JSON list is simpler, has no migration cost, and matches how `WatchProgressStore` already works. The plan's status-tracking columns (ADR-024) apply to anime/episode DB rows, not to the transient download queue. A SQLDelight migration is a documented future option if the queue grows.

### Reasoning breakdown:
- **Size**: tens of tasks, not thousands. SharedPreferences JSON is fine.
- **Mutability**: progress ticks every ~100ms during a download. SharedPreferences writes are throttled to 1/sec by `DownloadQueue.persistThrottled()`. SQLDelight would also need throttling — no advantage.
- **Simplicity**: no schema, no migrations, no DAO boilerplate. Just `Json.encodeToString` + `tasksPref.set(...)`.
- **Consistency**: `WatchProgressStore` (the existing pattern for persisting watch progress) uses the same approach. Sticking with it keeps the codebase uniform.
- **Future option**: if the queue grows (e.g. batch download of 100+ episodes), migrating to SQLDelight is "documented" but not implemented.

## 3. What IS persisted in SharedPreferences

**File**: `core/download/src/main/java/app/confused/anikuta/core/download/DownloadStore.kt`

```kotlin
class DownloadStore(store: PreferenceStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val tasksPref: Preference<List<DownloadTask>> = store.getObject(
        KEY_TASKS,                            // "pref_download_tasks_v1"
        emptyList(),
        { list -> json.encodeToString(ListSerializer(DownloadTask.serializer()), list) },
        { str ->
            try { json.decodeFromString(ListSerializer(DownloadTask.serializer()), str) }
            catch (e: Exception) { emptyList() }
        },
    )

    val changes: Flow<List<DownloadTask>> = tasksPref.changes().map { it }
    fun getAll(): List<DownloadTask> = tasksPref.get()
    fun setAll(tasks: List<DownloadTask>) { tasksPref.set(tasks) }
    fun purgeCancelled(): List<DownloadTask> { ... }

    companion object {
        private const val KEY_TASKS = "pref_download_tasks_v1"
    }
}
```

**Single key**: `pref_download_tasks_v1`. Value = JSON array of `DownloadTask` objects.

### What's in each `DownloadTask` (serialized)

From `DownloadTask.kt:26-58`:
```kotlin
@Serializable
data class DownloadTask(
    val id: Long,
    val request: DownloadRequest,        // nested serializable
    val status: DownloadStatus,          // enum
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoUri: String? = null,
    val subtitleUris: List<String> = emptyList(),
)
```

And `DownloadRequest`:
```kotlin
@Serializable
data class DownloadRequest(
    val anime: DownloadAnimeInfo,        // nested serializable
    val episode: DownloadEpisodeInfo,    // nested serializable
    val videoUrl: String,
    val videoHeaders: String? = null,
    val subtitleTracks: List<DownloadTrack> = emptyList(),
    val audioTracks: List<DownloadTrack> = emptyList(),
    val sourceId: Long = 0L,
    val videoServer: String = "",
    val videoQuality: String = "",
    val videoAudio: String = "",
)
```

So the entire task (including the original request — anime info, episode info, video URL, headers, subtitle/audio tracks) is persisted. This is a **denormalized snapshot** — the task carries everything needed to restart the download without re-resolving.

**Trade-off**: a single JSON blob is easy but means:
- No queries (can't say "give me all COMPLETED tasks for contentId X" without deserializing the whole list).
- The whole list is rewritten on every state change (throttled to 1/sec for progress ticks, immediate for state changes).
- A JSON schema change requires a migration (handled by `ignoreUnknownKeys = true` + the `_v1` suffix on the key — bumping the suffix starts a fresh list).

## 4. Other download-related SharedPreferences keys

From `DownloadPreferences.kt` (see `07-settings-preferences.md`):
- `pref_dl_folder_uri` (String) — SAF tree URI.
- `pref_dl_method` (enum) — NORMAL / ADVANCED.
- `pref_dl_wifi_only` (Boolean).
- `pref_dl_concurrent` (Int).
- `pref_dl_show_button` (Boolean).
- `pref_dl_auto_pick` (Boolean).
- `pref_dl_quality_prefs` (JSON List<String>).
- `pref_dl_audio_prefs` (JSON List<String>).
- `pref_dl_server_prefs` (JSON Map<String, List<String>>).
- `pref_dl_quality_fallback`, `pref_dl_audio_fallback`, `pref_dl_server_fallback` (enum).
- `pref_dl_adv_threads`, `pref_dl_adv_retries`, `pref_dl_adv_min_size_mb` (Int).

From `ServerDiscoveryStore.kt`:
- `pref_dl_server_discovery_v1` (JSON Map<String, List<String>>) — discovered server names per source.

From `DownloadMigration.kt`:
- (implicit) `pref_download_migration_v1_done` — gates the Phase 6 migration (anilistId → content_id).

## 5. The `DatabaseModule` (what's actually wired in DI)

**File**: `app/src/main/java/app/confused/anikuta/di/DatabaseModule.kt`

```kotlin
val databaseModule: Module = module {
    single { DatabaseDriverFactory(get()) }
    single { AnikutaDatabase(get<DatabaseDriverFactory>().create()) }
}
```

Provides:
- `DatabaseDriverFactory` — creates the Android SQLite driver.
- `AnikutaDatabase` — the SQLDelight database instance (used by anime/episode/history/category repositories).

**No download-related bindings here.** The download store is wired in `DownloadModule` (see `12-di-wiring.md`).

## 6. The Phase 6 migration (anilistId → content_id)

**File**: `app/src/main/java/app/confused/anikuta/migration/DownloadMigration.kt` (178 lines)

When the project moved from `anilistId: Int` to `contentId: String` (Phase 6, ADR-050), a one-shot migration was needed:

```kotlin
class DownloadMigration(
    private val downloadStore: DownloadStore,
    private val storageProvider: DownloadStorageProvider,
) {
    suspend fun migrate(): Result {
        val taskResult = migrateTasks()
        val folderResult = migrateFolders()
        ...
    }

    private fun migrateTasks(): TaskResult {
        val tasks = downloadStore.getAll()
        // For each task with empty contentId + non-null legacyAnilistId:
        //   derive contentId = "al:$legacyAnilistId"
        //   write back to the store
        ...
    }

    private fun migrateFolders(): FolderResult {
        // For each task with non-empty contentId starting with "al:":
        //   find the legacy folder "<Title [anilistId]>"
        //   rename to "<Title [al-anilistId]>"
        ...
    }
}
```

Two parts:
1. **Re-key DownloadStore tasks** — for each persisted task with empty `contentId`, derive it from `legacyAnilistId` (`"al:$legacyAnilistId"`). Atomic `store.setAll(updated)` at the end.
2. **Move on-disk folders** — rename each anime folder from `<Title [154587]>` to `<Title [al-154587]>` via `storageProvider.renameLegacyAnimeFolder(...)`. Best-effort: SAF `DocumentFile.renameTo` is provider-dependent; failures are logged but don't block.

The migration is idempotent (tasks with non-empty contentId are skipped; folders ending with `[al-...]` are skipped). Runs on first launch post-Phase-6-update, gated by `pref_download_migration_v1_done`.

## 7. Comparison: NEW project's DB schema (already exists!)

**Critical for the implementation plan**: the new project at `/home/z/my-project/ANI-KUTA/ANI-KUTA/APP/ani-kuta/` ALREADY has SQLDelight tables for downloads:

### `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadQueue.sq`
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

### `core/database/src/main/sqldelight/com/confused/anikuta/core/database/downloadedEpisode.sq`
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

### The new project's stub `DownloadManager`

The new project has a stub `DownloadManager` that uses these tables (see `core/download/src/main/java/com/confused/anikuta/core/download/DownloadManager.kt`). It:
- Inserts into `download_queue` on enqueue.
- Updates state via `updateDownloadState`.
- On completion, inserts into `downloaded_episode` + deletes from `download_queue`.
- On `isDownloaded`, queries `downloaded_episode`.
- On `getDownloadedFilePath`, queries `downloaded_episode`.
- On `deleteDownload`, deletes from both tables + deletes the file.

**But it's a minimal stub** — no:
- HLS support
- Advanced (multi-threaded) support
- Pause/resume/retry/cancel
- Subtitle download
- Metadata.json
- SAF folder picker (uses `context.filesDir/downloads/` instead)
- Reactive state (uses `MutableStateFlow<Map<String, DownloadState>>` but doesn't persist it — relies on the DB)
- Concurrency limit
- Wi-Fi-only check
- Notifications
- Storage validation / magic-byte check
- Internal-cache-first pipeline (writes directly to the final file)
- Composite key dedup

## 8. Architectural decision for the new project

The new project has two choices:

### Option A: Replicate the old project's JSON-in-SharedPreferences approach
- Pros: simpler, no DB schema changes, matches the old project exactly.
- Cons: ignores the already-existing `download_queue` + `downloaded_episode` tables (would need to drop them OR leave them unused).

### Option B: Use the existing SQLDelight tables (recommended)
- Pros: leverages the new project's existing schema, proper queryable storage, easier to extend (e.g. "all COMPLETED for contentId X" is a single SQL query instead of deserializing the whole list).
- Cons: needs schema changes to match the old project's data model — e.g.:
  - Add columns: `video_url`, `video_headers`, `subtitle_tracks` (JSON), `audio_tracks` (JSON), `source_id`, `video_server`, `video_quality`, `video_audio`, `content_id`, `episode_number`, `episode_url`, `episode_name`, `anime_title`, `cover_url`, `downloaded_bytes`, `total_bytes`, `video_uri`, `subtitle_uris` (JSON).
  - OR: keep the table lean (just state + episode_key) and store the rest as a JSON blob column.
- The `downloaded_episode` table already has `file_path` (a plain String) — but the old project uses content:// URIs (SAF). Either store the content:// URI in `file_path` OR migrate to a file-path-based approach (simpler but loses SAF flexibility).

**Recommendation** (see `13-implementation-plan.md`): Option B with a denormalized JSON column for the full `DownloadRequest` (matches the old project's data model but in a queryable table). Drop the existing stub `DownloadManager` and rebuild it following the old project's architecture.

## 9. Summary

| Aspect | Old project | New project (current) |
|---|---|---|
| Storage medium | SharedPreferences (JSON blob) | SQLDelight tables (already exist) |
| Schema | None (just a JSON `List<DownloadTask>`) | `download_queue` + `downloaded_episode` tables |
| Reactive | `Preference.changes(): Flow<T>` | NOT reactive (would need to wrap with `MutableStateFlow`) |
| Migrations | `DownloadMigration` (anilistId → content_id) | None (fresh start) |
| Queryable | No (must deserialize whole list) | Yes (SQL queries) |
| Throttling | App-level (`persistThrottled` 1/sec) | DB-level (would need explicit throttling) |

**For the new project**: use the SQLDelight tables (they're already there), but adapt the schema to carry the full task data (either as columns or as a JSON blob column). Wrap with a reactive `StateFlow` for UI consumption. See `13-implementation-plan.md` for the concrete plan.
