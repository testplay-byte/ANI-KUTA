# REVIEW-1 — Storage + DB + Content ID Adaptation

> **Task ID:** DL-REVIEW-1
> **Reviewer:** senior-review-agent
> **Scope:** Review Round 1 of 5 — `04-storage-paths.md` (NEW storage) + `11-db-schema.md` (SQLDelight) + storage/DB phases of `13-implementation-plan.md`.
> **Method:** Every claim verified against the actual new-project source files (`ContentModels.kt`, `ContentIdGenerator.kt`, `ContentRepository.kt`, `downloadQueue.sq`, `downloadedEpisode.sq`, `DatabaseDriverFactory.kt`, `build.gradle.kts`, `content.sq`).
> **Verdict:** **APPROVED WITH CHANGES** — design is fundamentally sound, but several CRITICAL issues (the migration plan is built on a non-existent migration chain; the `data.json` ↔ `content` table FK gap; the same-title collision algorithm is unimplemented) must be fixed before D.0/D.1 start.

---

## Checklist

### 1. Storage folder tree — **PASS WITH CONCERNS**

**Verified sound:**
- The `video/images/text` format-based split (instead of content-type `anime/manga/novel`) is a genuinely future-proof idea. Anime, movies, live-action series all → `video/`. Manga + art books → `images/`. Novels → `text/`. Adding a new content type later doesn't restructure the tree. ✅
- `data.json` per content folder is durable (it lives in the user's SAF folder, not in app data). Survives uninstall + reinstall + same-folder-selection. ✅ Matches the worklog's stated goal.
- App-delete-reinstall-same-folder: the scan-on-startup reads every `data.json` and UPSERTs by `mainId`. The DB is a cache. This is the right model — it mirrors Android's MediaStore pattern (durable files + queryable index). ✅

**Concerns:**
- **No `.anikuta/` migration from old project.** The doc states "no migration script from the old project" (§12) — by design. Acceptable for a fresh-start project, but worth flagging that any existing old-project testers will lose their library. The plan acknowledges this.
- **Audio-content gap.** §3.2 mentions "audio drama" → `audio/` format folder. But §3.1's ASCII tree only shows `video/`, `images/`, `text/`. The scan-on-startup in §7.1 hard-codes `listOf("video", "images", "text")` — no `audio/`. If an `audio/` folder exists, it's silently skipped. Either add `"audio"` to the scan list, or remove the audio-drama mention from §3.2 to avoid future confusion. (Minor — no audio content is planned for v1.)
- **The `.anikuta/` hidden folder.** Created lazily on first scan, contains `scan_state.json`. SAF's `DocumentFile` doesn't guarantee dot-prefixed folders are hidden from file managers (it depends on the provider — some show them). The doc could recommend using `NomediaMedia` flag or a `.nomedia` file inside the content folders to prevent video files from appearing in gallery apps. Not mentioned anywhere — likely a real-world user complaint waiting to happen.

---

### 2. File naming — **PASS WITH CONCERNS**

**Verified correct:**
- 5-digit padding `E00001` for episode numbers up to 99,999. ✅ Handles One Piece (~1,100 episodes), daily soaps, podcast back-catalogues. The old project's 3-digit would collide past 999.
- No AniList ID suffix in folder or file names. ✅ The folder is just `<Title>`, the file is `<Title> - E00001.<ext>`. Matches the stated rule in §1.4.
- Single-file content (movies) drops the ` - E00001` segment. ✅ §4.3 handles this cleanly.

**Concerns:**
- **Sanitization example contradicts the rule.** §4.1 says `"Frieren: Beyond Journey's End"` → `"Frieren  Beyond Journey's End"` (double space). But §4.1 also says "collapses runs of whitespace". These contradict — if `:` is replaced with a space and runs of whitespace are collapsed, the result should be `"Frieren Beyond Journey's End"` (single space). Either the example is wrong or the rule is wrong. Fix the example.
- **Fractional episode format `%.1f` rounding bug.** §4.2 + §13 both acknowledge that `12.25` would be formatted as `00012.3` (rounding) — but the comment says "fix by switching to a more careful formatter if non-standard fractional episodes appear in practice". This is acceptable for now (real-world fractional episodes are almost always `.5`), but the formatter should be hardened before v1 ships. Suggested fix: split on `.` and concat without rounding: `"${intPart.padStart(5, '0')}${if (frac > 0) ".$frac" else ""}"`.
- **Path traversal — verified safe.** `/` is in the replaced-character set, so `"../etc/passwd"` → `".. etc passwd"`. No way to escape the content folder. ✅
- **Windows reserved names not handled.** `CON`, `PRN`, `AUX`, `NUL`, `COM1`-`COM9`, `LPT1`-`LPT9` are reserved on Windows. If the SAF folder is on a FAT32/exFAT SD card mounted on Windows later, a content folder literally named `CON` would be problematic. Extremely unlikely (no anime is titled "CON"), but worth a one-line safeguard in `sanitizeFileName`. (Minor.)
- **No filename length cap.** Some SAF providers limit file names to 255 bytes. A content with a very long title (e.g. *"Re:Zero − Starting Life in Another World Season 2 — Memory Snow OVA Special Edition"*) + ` - E00001.mp4` could approach the limit. `sanitizeFileName` should cap at ~200 chars to leave room for the episode suffix + extension. Not mentioned.

---

### 3. `data.json` schema — **FAIL (CRITICAL gap)**

**Verified good:**
- `schemaVersion: Int = 1` field present + parser uses `ignoreUnknownKeys = true` + `coerceInputValues = true`. ✅ Forward-compatible for additions. Breaking changes bump version + dispatch to a per-version adapter. Solid.
- `mainId` + `contentId` both stored. ✅ `mainId` is the stable UUID (matches `ContentRecord.mainId`), `contentId` is the structured string (matches `ContentRecord.contentId`).
- `episodes[]` array in one `data.json` per content (not per-episode `metadata.json` like the old project). ✅ Fewer SAF round-trips — 1 file read per content vs N per episode.

**CRITICAL issues:**

**C1 — `contentId` example value is wrong.** §5.2 shows `"contentId": "anilist:101522"`. This is NOT what `ContentIdGenerator.generate(...)` produces. Verified in `ContentIdGenerator.kt:34-50`: the format is a **6-section colon-delimited string** `{dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}`. A real example would look like `anilist:aniyomi:https://example.com/index.min.json:com.example.ext:69023:https://aniyomi.org/anime/jujutsu-kaisen`. The example will mislead implementers into storing a 2-section string and breaking the duplicate-detection logic in `content.sq`'s `idx_content_content_id` index. Fix the example + add a comment that the value must be produced by `ContentIdGenerator`.

**C2 — `data.json` ↔ `content` table FK gap.** The `content` table (verified in `content.sq:63-83`) has these FK columns: `data_source_id`, `system_id`, `extension_repo_id`, `extension_id`, `source_id`, `anime_url`, `display_source`. The `ContentDataJson` schema (§5.1) only stores `anilistId`, `sourceId`, `animeUrl`. The scan-on-startup calls `contentRepository.upsertFromDataJson(dataJson)` (§7.1) — but this method is not defined anywhere in the actual `ContentRepository.kt`, AND there's no way to reconstruct `dataSourceId`/`systemId`/`extensionRepoId`/`extensionId` from `data.json` alone. After a reinstall, the upsert would either:
  - (a) Fail FK validation (if the columns are NOT NULL — they're nullable, so no fail).
  - (b) NULL out the FK columns, breaking the content's source linkage.
  - (c) Need a reverse-lookup by `sourceId` + `animeUrl` to find the `extensionId` (which itself requires the extension to still be installed).

  The doc must either:
  - Add `dataSourceId/systemId/extensionRepoId/extensionId/displaySource` to `ContentDataJson`.
  - OR document the lookup-and-relink strategy (scan → for each data.json, look up extension by sourceId/animeUrl → if found, restore FKs; if not, mark as "source unavailable").
  - OR explicitly say "the upsertFromDataJson only restores the basic ContentRecord fields; the user must re-link the source manually after reinstall" (a UX downgrade).

  Currently the doc is silent on this. Implementers will hit this wall during D.1.

**Concerns:**
- **Episode-key is stored redundantly.** `EpisodeEntry.episodeKey = "$mainId|$episodeNumberPadded5"` — the `mainId` part is already known from the parent `ContentDataJson.mainId`. This is intentional (allows the EpisodeEntry to be self-identifying), but bloats the JSON. Acceptable.
- **No checksum/integrity field.** A corrupted `data.json` (e.g. user edited it by hand and made a typo) is silently skipped (§7.5). No way to detect tampering. For v1 this is fine — power users who hand-edit JSON are on their own.
- **`createdAt`/`updatedAt` on ContentDataJson** — `updatedAt` is bumped on every download (§6.3 step 5). This means a 100-episode download bumps `updatedAt` 100 times. Acceptable but creates noise if any system relies on `updatedAt` for sync.

---

### 4. DB schema — **FAIL (CRITICAL: migration plan is built on a non-existent migration chain)**

**Verified conceptually sound:**
- Re-keying by `mainId + episodeKey` matches the new project's `ContentRecord.mainId`-first approach. ✅
- The episode_key format `"$mainId|$episodeNumberPadded5"` is stable across source switches. ✅
- The dual-storage model (`data.json` durable + DB cache) is sound. ✅
- Indexes on `state`, `main_id`, `(main_id, state)`, UNIQUE on `(main_id, episode_key)` cover the common UI queries. ✅

**CRITICAL issues:**

**C3 — The migration plan claims `1.sqm` and `2.sqm` exist; they don't.** §3.3 says: *"The current tables already exist (created by the existing migration chain — `1.sqm`, `2.sqm`)."* Verified by `find ... -name '*.sqm'` and `LS` of the sqldelight directory: **there are zero `.sqm` files in the project**. SQLDelight 2.0.2 (verified in `gradle/libs.versions.toml:20`) derives the v1 schema directly from the `.sq` files' `CREATE TABLE IF NOT EXISTS` statements; the project is at schema v1. There is NO existing migration chain.

The proposed `3.sqm` would be the first `.sqm` file — but SQLDelight expects sequential migrations starting from `1.sqm` (1.sqm migrates v1→v2, 2.sqm migrates v2→v3, etc.). A lone `3.sqm` either won't compile or won't run (no v2 state to migrate from).

**The correct fix is one of:**
- **(a) Preferred for a fresh project:** Edit `downloadQueue.sq` + `downloadedEpisode.sq` directly (the new schema becomes v1). Existing dev installs wipe app data once. This matches the project's actual state (no shipped beta, no production users).
- **(b) If migrations are explicitly desired:** Add a `1.sqm` file (not `3.sqm`) that does `DROP TABLE IF EXISTS download_queue; DROP TABLE IF EXISTS downloaded_episode;` followed by the new `CREATE TABLE` statements. The `.sq` files are also updated to the new schema (canonical v2). Existing v1 installs run `1.sqm` on next launch.

The doc's `3.sqm` plan as written will fail at build/runtime. **Must fix before D.0.**

**C4 — `DatabaseDriverFactory` doesn't pass migrations.** Verified in `DatabaseDriverFactory.kt:11-18`:
```kotlin
fun create(): SqlDriver {
    return AndroidSqliteDriver(
        schema = AnikutaDatabase.Schema,
        context = context,
        name = "anikuta.db",
    )
}
```
There's no `migrations = arrayOf(...)` parameter. SQLDelight 2.x `AndroidSqliteDriver` constructor accepts a `vararg migrations: Migration` argument. Without it, **any schema version mismatch on an existing install crashes the app at startup** with `IllegalStateException: Can't migrate database from version N to M without migrations`. If option (a) above is chosen (edit .sq files directly), every existing dev install must wipe app data — the doc should call this out. If option (b) is chosen (1.sqm migration), `DatabaseDriverFactory` MUST be updated to pass the migration.

The doc doesn't address this. **Must fix before D.0.**

**C5 — `getDownloadedMainIds` query is malformed.** §3.2:
```sql
SELECT DISTINCT main_id, content_title, content_format, cover_url, cover_color
FROM downloaded_episode
ORDER BY MAX(downloaded_at) DESC
GROUP BY main_id;
```
Three problems:
1. `DISTINCT` is redundant with `GROUP BY main_id` — they deduplicate identically. Remove `DISTINCT`.
2. SQLite's "bare columns" rule means `content_title`, `content_format`, `cover_url`, `cover_color` get values from an arbitrary row in the group. This is fine for denormalized data IF all rows in a group have identical values, but is non-deterministic if any value differs (e.g. title changed mid-download, or a re-download with a different cover). Better: `MAX(content_title) AS content_title, MAX(content_format) AS content_format, ...` to make it deterministic.
3. `ORDER BY MAX(downloaded_at) DESC` is valid (aggregate in ORDER BY against a GROUP BY query) — this part is fine.

Fix the query before implementation.

**Concerns:**
- **Redundant `idx_downloaded_episode_main_id` index.** `main_id` is already the leftmost column of the composite PRIMARY KEY `(main_id, episode_key)`. SQLite uses the PK index for queries on `main_id` alone. The explicit index duplicates storage + write overhead for no benefit. Remove it (or document why it's needed — it isn't).
- **`download_queue.content_id` not updated on source switch.** The download_queue stores `content_id` (the structured string). When the user switches sources, `ContentRepository.updateContentSources(...)` updates the `content` table's `content_id` (verified in `ContentRepository.kt:162-184`), but there's no query in the new download_queue schema to update `content_id` for existing queue rows. This means a queued (or even completed) download's `content_id` becomes stale after a source switch. The episode_key (which is `mainId`-prefixed) is still correct, so functionality isn't broken — but any debug/log UI showing `content_id` would be misleading. Add an `updateDownloadContentId(mainId, oldContentId, newContentId)` query, or just drop `content_id` from `download_queue` (it's denormalized for debugging only — the canonical value is in `content.content_id`).
- **`sourceId INTEGER NOT NULL DEFAULT 0` in `download_queue`.** But `ContentRecord.sourceId` is `Long?` (nullable). What does `0` mean? Source 0 doesn't exist in `content_ext`. Use `NULL` instead of `0` for "no source". Same issue in `downloaded_episode.source_id`.
- **No `state` index on `downloaded_episode`.** Probably fine — queries filter by `main_id` (PK index) or by `downloaded_at` (covered). Just noting.

---

### 5. Scan-on-startup — **PASS WITH CONCERNS**

**Verified sound:**
- Algorithm walks `video/`/`images/`/`text/`, reads each `data.json`, UPSERTs to DB by `mainId`. ✅
- Reconciles DB rows: any DB-downloaded episode not in the scanned set is marked missing. ✅
- Corrupt `data.json` is skipped + logged. ✅
- Triggered on app start, on folder re-selection, on pull-to-refresh — NOT on every download. ✅
- Performance: incremental scan via `scan_state.json` is mentioned. ✅

**Concerns:**
- **Two folders with the same `mainId` — UNADDRESSED.** §7 doesn't mention this scenario. If a user manually copies a content folder (e.g. to "back it up"), the scan would UPSERT twice — second wins, first folder's files are orphaned in the SAF. The doc should specify: "if two folders have the same mainId, log a warning + keep the one with the newer `updatedAt` (or the one with more episodes). Mark the other as 'duplicate' for the user to resolve." At minimum, log it.
- **User renames a folder — works by accident.** The scan reads `data.json` (which has the correct `mainId`), and uses `ep.videoFileName` (stored in `data.json`) to find files inside the renamed folder. So renaming the folder only is safe. ✅ But renaming a video FILE inside the folder would mark the episode as missing — because the scan looks up by `videoFileName` from data.json. The doc should mention this explicitly so implementers add a "file rename detected, update data.json" reconciliation step (or at least a clear log line).
- **`scan_state.json` incremental optimization.** §7.3 says "if the SAF folder's `lastModified` is older than T, skip the scan". But SAF `DocumentFile.lastModified()` is unreliable on many providers (some always return 0, some return the folder creation time, not the last child modification). The optimization should fall back to "always scan" if `lastModified()` returns 0 or a sentinel value. Worth a one-line note.
- **No partial-scan capability.** A 200-content library requires ~600 SAF calls (200 listFiles on format folders + 200 listFiles on content folders + 200 findFile for data.json + N findFile for episode files). On a slow SD card, this could take 30+ seconds. The doc mentions caching `Map<mainId, DocumentFile>` in memory but doesn't spec it. For v1 this is acceptable (one-shot at startup); for v2 a content-folder-level dirty flag (in `scan_state.json`) would help.

---

### 6. Temp cache — **PASS**

**Verified sound:**
- Per-task dir under `context.cacheDir/anikuta_downloads/<downloadId>/`. ✅
- Internal-cache-first means partial/corrupt downloads NEVER appear in the user's SAF folder. ✅
- `cleanupStale()` on app startup deletes any leftover dirs (from a previous crash). ✅
- Layout includes `video.<ext>`, `subtitles/`, `cover.jpg`, `data.json` (temp), `resume.json` (Advanced method), `chunk_N.part` (Advanced method). ✅
- Atomic publish: video is fully downloaded to temp, validated (size + magic bytes), then copied to SAF. ✅

**Minor concerns:**
- **`cleanupStale()` race with START_STICKY foreground service.** If the app crashes mid-download and Android restarts the foreground service (per `START_STICKY`), the service might be running when `cleanupStale()` runs on next `AnikutaApp.onCreate`. The doc says "Any temp dir present at startup is from a crashed/interrupted download" — but if the service is restarting, the temp dir might still be in use. Mitigation: `cleanupStale()` should check if `DownloadService` is running (or use a file lock per downloadId) before deleting. The doc should call this out. (Low likelihood — most crashes don't restart the service cleanly.)
- **No size cap on the temp cache.** A 4 GB movie download fills 4 GB of internal cache. If the device is low on internal storage, this fails. `DownloadQueue.tryStartNext` should check `context.cacheDir.usableSpace` against the task's `totalBytes` before starting. Not mentioned in this doc (might be in `02-queue-management.md`).
- **`deleteRecursively()` on a SAF DocumentFile is slow** — but the temp cache is on internal storage (regular `java.io.File`), so `deleteRecursively()` is fast. ✅

---

### 7. SAF/Scoped Storage — **PASS WITH CONCERNS**

**Verified correct:**
- `ActivityResultContracts.OpenDocumentTree()` + `takePersistableUriPermission` is the canonical Android SAF approach. ✅
- Persistable URI permissions survive app restarts AND device reboots. ✅
- `DocumentFile.fromTreeUri()` + `canWrite()` check is the right pattern. ✅
- minSdk 24 — SAF works on all APIs from 19+. Scoped storage (Android 10+) doesn't apply because SAF is the explicit escape hatch. ✅
- Foreground service `foregroundServiceType="dataSync"` for Android 14+ is mentioned in the implementation plan (D3). ✅

**Concerns:**
- **999-open-files limit — UNADDRESSED.** Some SAF providers (notably SD cards via OTG, certain Xiaomi MIUI versions, and some cloud-storage SAF providers) have a hard limit on simultaneously-open file descriptors (~999). For a 200-content scan that opens 1 data.json + N video files per content, this could exhaust the limit. The doc mentions SAF `listFiles()` is slow but doesn't mention the open-files limit. Mitigation: ensure every `openInputStream`/`openOutputStream` is wrapped in `use { }` (auto-close), and avoid holding multiple streams open concurrently during the scan. Worth a one-line warning in §7.3.
- **`DocumentFile.findFile()` is O(N) per call.** It lists all children and matches by name. For a content folder with 200 episode files, calling `findFile(videoName)` for each episode is 200 × O(200) = 40,000 ops. The scan should call `listFiles()` ONCE per content folder and build a `Map<String, DocumentFile>` index, then look up by name. The doc's algorithm in §7.1 calls `contentDir.findFile(ep.videoFileName)` per episode — this is the slow path. Worth flagging as a performance fix.
- **SAF provider quirks on Samsung/Xiaomi** — mentioned in the risk register (§8 of `13-implementation-plan.md`). ✅
- **`takePersistableUriPermission` can fail silently.** §2.1 catches `SecurityException` but some OEMs return a URI that's persistable but later fails on `openOutputStream`. The `rootTree()` accessor's `canWrite()` check catches this at access time. ✅
- **MediaStore scanning.** A `.mp4` file in the user's SAF folder WILL be picked up by the system MediaStore and appear in gallery/music apps (depending on the provider). The doc doesn't mention adding a `.nomedia` file to each content folder to prevent this. Without `.nomedia`, users will see "Jujutsu Kaisen - E00001.mp4" in their gallery — a real-world complaint. **Add `.nomedia` creation to `publishToUserFolder`.**

---

### 8. Content ID adaptation — **PASS WITH CONCERNS**

**Verified correct:**
- `mainId` (stable UUID) is the primary key in both `data.json` and the DB (`download_queue.main_id`, `downloaded_episode.main_id`, composite PK). ✅
- `contentId` (structured, changes on source switch) is stored as a secondary field in `download_queue.content_id` and `ContentDataJson.contentId`. ✅
- The episode_key `"$mainId|$episodeNumberPadded5"` is source-independent. ✅ When the user switches sources, the same mainId + episodeKey still maps to the same downloaded file.
- Source-switch scenario for COMPLETED downloads: the file is on disk, no re-resolve needed, the `mainId` matches. ✅
- Source-switch scenario for IN-FLIGHT downloads: covered by the proxy-churn fix in `10-player-integration.md` (re-resolve-on-IOException for localhost URLs).

**Concerns:**
- **Stale `content_id` in `download_queue` after source switch** — already noted in §4 above. The download_queue's `content_id` column isn't updated when `ContentRepository.updateContentSources(...)` runs. Either drop the column (it's denormalized for debugging only) or add a sync query.
- **Stale `video_url` in `download_queue` after source switch.** Same issue — the captured URL becomes invalid. For COMPLETED downloads this doesn't matter (the file is on disk). For QUEUED downloads that haven't started, the orchestrator should re-resolve via `ResolveContext` before starting. The doc covers this conceptually via the re-resolve-on-IOException path, but that's reactive (waits for failure) rather than proactive (re-resolves before starting). Worth a note: "on app start, any QUEUED task with a stale `content_id` (compared to `content.content_id`) should be re-resolved proactively".
- **`sourceType` field in `ContentDataJson` is fuzzy.** §5.1 says `"anilist" | "extension" | "tmdb" | "manual"`. But `ContentRecord.displaySource` (verified in `ContentModels.kt:24`) defaults to `"extension"` — there's no enum/constant. The `data.json` field should either reference `ContentRecord.displaySource` directly (a single source of truth) or define a clear enum. Right now it's a freeform string with examples — implementers might invent new values.
- **`anilistId` stored in `data.json` but not in `ContentRecord`.** `ContentRecord` doesn't have `anilistId` — it's in `AniListDetail` (separate table, joined by `mainId`). The `data.json` stores it directly, which is convenient but creates a redundancy: after a reinstall, the scan UPSERTs to BOTH `content` (basic fields) AND `anilist_detail` (anilistId). The doc shows `contentRepository.upsertFromDataJson(dataJson)` but doesn't show the `anilistDetailRepository.upsertFromDataJson(dataJson)` step. Either show both UPSERTs, or move `anilistId` out of `data.json` (force re-link after reinstall).

---

## CRITICAL issues (must fix before D.0 / D.1)

| # | Issue | Where | Fix |
|---|---|---|---|
| **C1** | `data.json` example `"contentId": "anilist:101522"` is wrong. Real format is 6-section per `ContentIdGenerator.kt`. | `04-storage-paths.md` §5.2 | Fix the example to a real 6-section value + add a comment that the value MUST be produced by `ContentIdGenerator.generate(...)`. |
| **C2** | `data.json` doesn't store `dataSourceId/systemId/extensionRepoId/extensionId/displaySource`. The scan's `upsertFromDataJson(dataJson)` can't restore the FK columns of the `content` table — would NULL them out, breaking source linkage. | `04-storage-paths.md` §5.1 + §7.1 | Either (a) add the FK columns to `ContentDataJson`, OR (b) document the reverse-lookup strategy (scan → look up extension by `sourceId` + `animeUrl` → restore FKs), OR (c) explicitly downgrade UX ("user must re-link source after reinstall"). |
| **C3** | Migration plan claims `1.sqm` and `2.sqm` exist — they don't. Project has ZERO `.sqm` files. The proposed `3.sqm` will fail (SQLDelight 2.x expects sequential migrations starting at `1.sqm`). | `11-db-schema.md` §3.3 | Either (a) edit the `.sq` files directly (v1 → new v1; dev installs wipe app data), OR (b) add a `1.sqm` (not `3.sqm`) that DROPs + CREATEs. Update §3.3 accordingly. |
| **C4** | `DatabaseDriverFactory.create()` doesn't pass `migrations = ...` to `AndroidSqliteDriver`. Any schema version change crashes existing installs at startup. | `11-db-schema.md` §3.3 + actual `DatabaseDriverFactory.kt` | If option (a) above: call out "dev installs must wipe app data". If option (b): update `DatabaseDriverFactory` to pass `migrations = arrayOf(MyMigration)`. |
| **C5** | `getDownloadedMainIds` query has both `DISTINCT` and `GROUP BY` (redundant) + non-deterministic bare-column values. | `11-db-schema.md` §3.2 | Rewrite as `SELECT main_id, MAX(content_title) AS content_title, MAX(content_format) AS content_format, MAX(cover_url) AS cover_url, MAX(cover_color) AS cover_color FROM downloaded_episode GROUP BY main_id ORDER BY MAX(downloaded_at) DESC;` |

## IMPORTANT issues (should fix)

| # | Issue | Where | Fix |
|---|---|---|---|
| **I1** | Same-title collision algorithm is unimplemented. `contentFolderName()` (§4.1) just sanitizes the title with no collision detection. Two different `mainId`s with the same title would write to the same folder and overwrite each other's `data.json`. §13 mentions "option (a) append `(2)`" but `ensureContentDir` (referenced in §6.3) isn't shown — does it detect collisions? | `04-storage-paths.md` §4.1 + §6.3 | Spec `ensureContentDir`: (1) check if folder exists, (2) if yes, read its `data.json` and check `mainId`, (3) if `mainId` matches → reuse, (4) if `mainId` differs → append ` (2)`, ` (3)`, etc. until a free slot is found. Add this to §4.1. |
| **I2** | `sourceId INTEGER NOT NULL DEFAULT 0` in both DB tables, but `ContentRecord.sourceId` is `Long?`. `0` isn't a real source ID in `content_ext`. | `11-db-schema.md` §3.2 | Change to `source_id INTEGER` (nullable). Default NULL, not 0. |
| **I3** | Redundant `idx_downloaded_episode_main_id` index — `main_id` is already the leftmost column of the composite PK, so SQLite uses the PK index. | `11-db-schema.md` §3.2 | Remove the redundant index. |
| **I4** | Stale `content_id` in `download_queue` after source switch. `ContentRepository.updateContentSources` updates `content.content_id` but nothing updates `download_queue.content_id`. | `11-db-schema.md` §3.2 | Either (a) drop `content_id` from `download_queue` (it's denormalized), OR (b) add an `updateDownloadContentId(mainId, newContentId)` query and call it from `ContentRepository.updateContentSources`. |
| **I5** | `.nomedia` file not created in content folders. Downloaded `.mp4` files will appear in gallery apps. | `04-storage-paths.md` §6.3 | Add `contentDir.createFile("application/octet-stream", ".nomedia")` to `publishToUserFolder`. |
| **I6** | 999-open-files limit + `DocumentFile.findFile()` O(N) — not mentioned. Scan performance on large libraries will be poor. | `04-storage-paths.md` §7.1 + §7.3 | (a) Ensure every SAF stream is wrapped in `use { }`. (b) In the scan, call `contentDir.listFiles()` ONCE per folder, build a `Map<String, DocumentFile>`, then look up by name. (c) Add a note about the open-files limit. |
| **I7** | Two folders with the same `mainId` (user manually copied a folder) — unaddressed. Second UPSERT wins, first folder's files are orphaned. | `04-storage-paths.md` §7 | Add a check: if scan encounters two folders with the same `mainId`, log a warning + keep the one with the newer `updatedAt`. |
| **I8** | `DocumentFile.lastModified()` is unreliable on many SAF providers. The incremental-scan optimization in §7.3 may never trigger or always trigger. | `04-storage-paths.md` §7.3 | Fall back to "always scan" if `lastModified()` returns 0 or a sentinel. Document this. |
| **I9** | Fractional episode format `%.1f` rounds `12.25` → `12.3`. Acknowledged in §13 but not fixed. | `04-storage-paths.md` §4.2 | Replace `%.1f` with a non-rounding formatter: split on `.`, pad the integer part to 5 digits, append `.<fraction>` if non-zero. |

## MINOR issues (nice to fix)

| # | Issue | Where | Fix |
|---|---|---|---|
| **M1** | Sanitization example shows double space (`"Frieren  Beyond"`) but the rule says "collapses runs of whitespace" (single space). | `04-storage-paths.md` §4.1 | Fix the example to show single space. |
| **M2** | `audio/` format folder mentioned in §3.2 but not in the scan's `listOf("video", "images", "text")`. | `04-storage-paths.md` §7.1 | Either add `"audio"` to the scan list or remove the audio-drama mention from §3.2. |
| **M3** | Windows reserved names (`CON`, `PRN`, `AUX`, `NUL`) not handled in `sanitizeFileName`. | `04-storage-paths.md` §4.1 | One-line safeguard: replace reserved names with `Unknown`. (Extremely unlikely to matter.) |
| **M4** | No filename length cap in `sanitizeFileName`. Very long titles + episode suffix + extension could approach 255 bytes. | `04-storage-paths.md` §4.1 | Cap at ~200 chars to leave room for the suffix. |
| **M5** | `sourceType` in `ContentDataJson` is a freeform string. Should reference `ContentRecord.displaySource` (single source of truth) or define an enum. | `04-storage-paths.md` §5.1 | Reference `displaySource` field. |
| **M6** | `anilistId` in `data.json` but the scan only shows `upsertFromDataJson` to `content` table — not to `anilist_detail`. Either show both UPSERTs or remove `anilistId` from `data.json`. | `04-storage-paths.md` §7.1 | Show the second UPSERT to `anilist_detail`. |
| **M7** | Stale `video_url` in `download_queue` after source switch — re-resolve-on-IOException is reactive, not proactive. QUEUED tasks with a stale URL will fail-then-retry instead of pre-resolving. | `11-db-schema.md` §3.2 | Add a note: "on app start, any QUEUED task whose `content_id` differs from `content.content_id` (for the same `main_id`) should be proactively re-resolved via `ResolveContext`." |
| **M8** | `cleanupStale()` race with `START_STICKY` foreground service restart. If the service is restarting when cleanup runs, in-flight temp dirs could be deleted. | `04-storage-paths.md` §6.2 | Check if `DownloadService` is running before `cleanupStale()`, or use a per-downloadId file lock. |
| **M9** | `data.json` `updatedAt` bumped on every download → noisy for sync systems. | `04-storage-paths.md` §5 | Use a separate `lastEpisodeAddedAt` field; keep `updatedAt` for schema/format changes only. |
| **M10** | No size cap on the temp cache. A 4 GB movie download fills 4 GB of internal cache. | `04-storage-paths.md` §6 | `tryStartNext` should check `cacheDir.usableSpace` against `totalBytes` before starting. |

---

## Overall verdict: **APPROVED WITH CHANGES**

The dual-storage model (`data.json` durable + SQLDelight cache) is fundamentally sound and is the right architecture for a download system that needs to survive app-uninstall + reinstall. The folder tree (`video/images/text` format folders + per-content `data.json`) is future-proof. The `mainId`-keyed identity is correctly aligned with the new project's `ContentRecord` system. The temp-cache-first + atomic-publish pipeline is the correct way to keep partial files out of the user's folder.

However, the plan cannot be implemented as-written. The five CRITICAL issues (C1–C5) must be addressed before Phase D.0 starts:

1. **C3 + C4 (the migration plan)** are the most urgent — the doc's `3.sqm` approach will literally not build or will crash existing installs at startup. The fix is either (a) edit the `.sq` files directly + have dev installs wipe app data, or (b) add a `1.sqm` (not `3.sqm`) + update `DatabaseDriverFactory` to pass the migration. The doc author seems to have assumed SQLDelight works like Room (which supports arbitrary migration version numbers); it doesn't.

2. **C2 (the data.json ↔ content table FK gap)** is the next most urgent — the scan-on-startup's `upsertFromDataJson` cannot restore the `content` table's source-linkage FKs because the data.json doesn't store them. The doc must either expand the schema OR document the lookup-and-relink strategy OR explicitly downgrade the reinstall UX. Currently the doc is silent — implementers will hit this wall mid-D.1.

3. **C1 (the contentId example)** is a quick fix but a real one — if an implementer copy-pastes the example, the duplicate-detection index `idx_content_content_id` will not work as expected.

4. **C5 (the malformed query)** is also a quick fix — `DISTINCT` + `GROUP BY` is a code smell; the bare columns are non-deterministic.

The IMPORTANT issues (I1–I9) should be addressed in the same revision pass — especially **I1 (same-title collision algorithm)** and **I5 (`.nomedia` file)**, both of which will cause real-world user complaints if shipped without a fix.

The MINOR issues (M1–M10) can be deferred to a post-implementation polish pass.

**Recommendation:** Update `04-storage-paths.md` §4.1, §5.1, §5.2, §6.3, §7.1, §7.3 + `11-db-schema.md` §3.2, §3.3, §3.4 (new section on `DatabaseDriverFactory` change) + `13-implementation-plan.md` Phase D.0 task #2 to address C1–C5 + I1–I9. Then proceed to Phase D.0.

The next review round (DL-REVIEW-2) should focus on the queue management + state machine + downloaders (`02-queue-management.md`, `03-state-machine.md`, `05-downloaders.md`).
