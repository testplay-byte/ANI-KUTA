# Database Restructuring Plan v2 — ANI-KUTA

> **Status**: PROPOSAL v2 (revised from v1) — not yet implemented. Awaiting user approval.
> **Date**: 2026-08-14
> **Author**: Main agent (researched via 2 Explore sub-agents R-1 + R-2, reviewed via 4 sub-agent iterations)
> **Scope**: Schema restructuring of the 26-table SQLDelight database → **22 tables**. No code changes this phase — this is the plan only.
> **Migration policy**: Debug builds only — schema can be rebuilt freely per CORE_RULES §30 (drop + recreate, no `.sqm` migration files needed).
> **Change from v1**: The user reversed the Option C decision (two tables) → now Option A (one wide `content_details` table). Also: keep `extension_repo_id`, drop `app_metadata`, keep `data_source`+`system` separate, keep `display_source` as single UX column (not split), 10-group presentation.

---

## 1. Executive Summary

This plan restructures the database from **26 tables → 22 tables** through 4 changes:

1. **Rename** `content` → `main_entry` (the identity hub — clearer name, avoids `android.content.*` collision)
2. **Merge** `anilist_detail` + `extension_detail` + `other_source_detail` + `anime_metadata_cache` (4 tables) → **one wide `content_details` table** (Option A — the user's revised direction). Handles ALL content types (video/novel/image/manga) + ALL data sources (AniList/Kitsu/MAL/TMDB) + ALL extensions (Aniyomi/CloudStream/Sora/MangaYomi).
3. **Drop** `app_metadata` (dead code — 0 Kotlin callers, absorbed into `app_settings`)
4. **Keep** `data_source` + `system` separate (different column shapes, FK integrity — per R-2 recommendation)

Plus **independent improvements** bundled in:
- Drop `description` from `main_entry` (has 3 fallback-reader callers — migration specified in §4.1). **Keep `extension_repo_id`** per user directive.
- Fix 2 missing FK declarations (`watch_progress`, `notification_sent`)
- Fix `episode_number` type mismatch (INTEGER → REAL in `notification_sent` + `episode_schedule`) — schema + API (SQLDelight maps REAL→Double)
- Drop 4 dead queries + 2 dead methods
- Drop redundant indexes
- `DataSourceExtras` + `ExtensionExtras` typed accessors for `extra_json`
- `clearExtensionAxis` query (fixes the orphan-row unlink bug)
- `updateMainEntryTitle` query (keeps title in sync on metadata refresh)
- Standardize index naming + retention query param style

**Tables NOT merged** (confirmed keep-separate via R-2 research): updates group, notifications group, ratings group, genres group, library group, downloads group, `data_cache_episode`, `browse_cache`, `data_source`, `system`. Each has a sound architectural reason (different cardinality, different retention, different access pattern, or classic M:N normalization).

**Table count honesty**: 22 is above the user's "under 15" preference. The research confirms the remaining 22 tables are genuinely better separate — merging any of them would create sparse/awkward tables, break FK integrity, or corrupt backup semantics. 22 is the floor without forcing bad merges.

---

## 2. Design Principles (the "why" behind every decision)

1. **One `content_details` table, two axes.** Data-source metadata (AniList/Kitsu/MAL/TMDB) + extension metadata (Aniyomi/CloudStream/Sora/MangaYomi) live in ONE table, distinguished by column prefixes (`data_*` / `ext_*`) + discriminator columns (`data_source_type` / `extension_type`). This is the user's revised direction — simpler than two tables, handles all content types.

2. **Future-proof, not over-engineered.** Adding a new data source (e.g. MAL) or a new extension ecosystem (e.g. CloudStream) = UPDATE the row with a new `data_source_type` / `extension_type`. **Zero schema change.** Source-specific extras go in `data_extra_json` / `ext_extra_json`.

3. **In-place switching.** When the user switches the active data source or extension, the existing row is UPDATEd (via `updateDataSourceAxis` / `updateExtensionAxis`) — not deleted + re-inserted. The `main_id` stays stable throughout. Both axes can be switched independently.

4. **Stable identity.** `main_id` (UUID) is assigned once on first sighting, never changes, survives all source switches. All child tables FK to it with `ON DELETE CASCADE`.

5. **No data loss.** Every column in every dropped table is either (a) duplicated elsewhere, (b) dead (zero callers), or (c) explicitly migrated. Verified by 7 research sub-agents (5 in prior session + 2 this session).

6. **Keep what's separate, separate.** The 7 groups confirmed keep-separate by R-2 research are NOT merged. Forcing them would create technical debt, not reduce it.

---

## 3. The 4 Core Changes (detailed)

### Change 1 — Rename `content` → `main_entry`

**Why**: The `content` table's real job is the identity hub — it holds the stable `main_id` + the changing `content_id` + links to all per-source detail tables. The name "content" is generic + collides with `android.content.ContentResolver` / `android.content.Context`. `main_entry` accurately reflects "the main entry row that all detail rows hang off of."

**What changes**:
- Table name: `content` → `main_entry`
- 4 indexes renamed: `idx_content_*` → `idx_main_entry_*`
- 9 SQLDelight queries renamed: `getContentBy*` → `getMainEntryBy*`, `insertContent` → `insertMainEntry`, etc.
- 1 NEW query: `updateMainEntryTitle(mainId, title, updatedAt)` — keeps `main_entry.title` in sync when metadata refresh updates the title
- 13 FK declarations across 9 `.sq` files updated: `REFERENCES content(main_id)` → `REFERENCES main_entry(main_id)`
- 1 Kotlin string literal in `DatabaseDriverFactory.kt:168` updated
- 1 `DbReference("content", ...)` in `DetailsScreen.kt:385` → `DbReference("main_entry", ...)`

**What does NOT change** (deferred to separate sessions):
- Kotlin class names (`ContentRecord`, `ContentRepository`, etc.) — decoupled from table name. ~24 caller files. Separate session.
- The `.sq` FILE name (`content.sq`) — could be renamed to `main_entry.sq`, changes `database.contentQueries` property. Separate session.

**Migration**: Debug builds — drop + recreate. No `.sqm` file needed.

### Change 2 — Merge 4 tables → one wide `content_details` (Option A)

**Why**: The user wants `anilist_detail` + `extension_detail` + `other_source_detail` + `anime_metadata_cache` merged into a single `content_details` table that:
- Holds metadata from ANY data source (AniList now, Kitsu/MAL/TMDB later)
- Holds metadata from ANY extension (Aniyomi now, CloudStream/Sora/MangaYomi later)
- Updates in-place when the user switches source (both axes independently)
- Handles ALL content types (video, novel, image, manga — most fields shared)
- Is future-proof (zero schema change for new sources/extensions/content-types)

**Design choice**: Option A — one wide table with column prefixes (`data_*` / `ext_*`) + discriminators + `extra_json`. The two axes (data-source + extension) are conceptually orthogonal + linked independently. Column prefixes preserve the fact that AniList + extension metadata can differ (which the user wants to switch between).

#### New table: `content_details`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `main_id` | TEXT | NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE | Stable identity link |
| **── Data-source (metadata) axis ──** | | | |
| `data_source_type` | TEXT | nullable | Discriminator: 'anilist' \| 'kitsu' \| 'mal' \| 'tmdb'. NULL = no data source linked. |
| `data_source_ref_id` | TEXT | nullable | External ID as TEXT (anilist_id, mal_id, kitsu_id, tmdb_id). TEXT for uniformity. |
| `data_score` | INTEGER | nullable | Average score 0-100 |
| `data_episodes` | INTEGER | nullable | Total episode count |
| `data_season` | TEXT | nullable | 'WINTER' \| 'SPRING' \| 'SUMMER' \| 'FALL' |
| `data_season_year` | INTEGER | nullable | Year of season airing |
| `data_status` | TEXT | nullable | 'FINISHED' \| 'RELEASING' \| 'CANCELLED' \| 'HIATUS' |
| `data_genres` | TEXT | nullable | Comma-separated curated genres |
| `data_synopsis` | TEXT | nullable | Long-form editorial synopsis |
| `data_cover_url` | TEXT | nullable | Data-source CDN cover image |
| `data_banner_url` | TEXT | nullable | Data-source CDN wide banner |
| `data_extra_json` | TEXT | nullable | JSON: `{"id_mal":12345,"trailer_url":"...","age_rating":"PG-13","studio":"WIT"}` |
| `data_updated_at` | INTEGER | nullable | When the data-source axis was last refreshed |
| **── Extension (episode source) axis ──** | | | |
| `extension_type` | TEXT | nullable | Discriminator: 'aniyomi' \| 'cloudstream' \| 'sora' \| 'mangayomi'. NULL = no extension linked. |
| `extension_id` | TEXT | nullable | Extension source ID as TEXT (Aniyomi Long stringified; future CloudStream String) |
| `source_id` | INTEGER | nullable | Aniyomi internal source.id (kept as INTEGER for back-compat; NULL for future extensions) |
| `anime_url` | TEXT | nullable | Extension's content URL (nullable so `clearExtensionAxis` can NULL it) |
| `ext_description` | TEXT | nullable | Source site's short description |
| `ext_genres` | TEXT | nullable | Source site's raw genres |
| `ext_status` | TEXT | nullable | Source site's free-text status |
| `ext_author` | TEXT | nullable | Manga/novel author (NULL for video) |
| `ext_artist` | TEXT | nullable | Manga artist (NULL for video + novels) |
| `ext_thumbnail_url` | TEXT | nullable | Source site's thumbnail image URL |
| `ext_extra_json` | TEXT | nullable | JSON: `{"scanlator_group":"...","chapter_count":42,"volume_count":8}` |
| `ext_updated_at` | INTEGER | nullable | When the extension axis was last refreshed |

**Column count: 26** (1 PK + 13 data-axis + 12 extension-axis). SQLite supports 2000 — non-issue.

**Indexes** (2):
- `idx_content_details_data_ref` — partial WHERE `data_source_type = 'anilist'` on `data_source_ref_id` (replaces `idx_anilist_detail_anilist_id` — hot path for `getMainEntryByAniListId`)
- `idx_content_details_data_source_ref` — composite on `(data_source_type, data_source_ref_id)` (generic, for future Kitsu/MAL/TMDB)

**Note on extension lookup** (per Review v2-1 FLAW 1): the `getMainEntryByExtension` hot path uses the **denormalized `extension_id` + `anime_url` columns on `main_entry`** (kept there for fast single-table lookup, no JOIN). The index for this query is `idx_main_entry_extension_url` on `main_entry` (renamed from `idx_content_extension_url`). No index needed on `content_details` for this query — the denormalized columns on `main_entry` are the source of truth for the reverse-lookup hot path.

**Queries** (11):
- `getContentDetails(mainId)` — single-row read
- `getMainEntryByAniListId(anilistId)` — JOIN for reverse lookup by AniList ID (hot path)
- `getMainEntryByDataSourceRef(sourceType, sourceRefId)` — generic reverse lookup (for future Kitsu/MAL/TMDB)
- `getMainEntryByExtension(extensionId, animeUrl)` — uses denormalized `main_entry.extension_id` + `main_entry.anime_url` (NO JOIN — hot path, kept on main_entry for performance)
- `upsertContentDetails(...)` — full-row INSERT OR REPLACE (26 params)
- `updateDataSourceAxis(...)` — partial UPDATE of all data-source fields (for switching data source — ext_* untouched)
- `updateExtensionAxis(...)` — partial UPDATE of all extension fields (for switching extension — data_* untouched)
- `clearDataSourceAxis(mainId)` — NULL all data-source fields (for unlink — fixes orphan-row bug)
- `clearExtensionAxis(mainId)` — NULL all extension fields (for unlink — **NEW, fixes the orphan-row bug**)
- `deleteContentDetails(mainId)` — hard delete
- `getAllContentDetails()` — for backup dump

**Migration from the 4 dropped tables**:
- `anilist_detail` → `content_details` data-axis: `anilist_id` → `data_source_ref_id` (TEXT), `synopsis` → `data_synopsis`, `id_mal` → `data_extra_json`, others map 1:1
- `extension_detail` → `content_details` extension-axis: `extension_id` → TEXT, `description` → `ext_description`, `thumbnail_url` → `ext_thumbnail_url`, others map 1:1
- `other_source_detail` → DROPPED (dead code, 0 callers — concept absorbed by `data_extra_json`)
- `anime_metadata_cache` → ABSORBED into data-axis (9/12 columns duplicate `anilist_detail`; 3 unique cols dead: `title` duplicates `main_entry.title`, `source_type` hardcoded dead, `fetched_at` write-only dead)

#### `main_entry` changes (bundled with Change 2)

The `main_entry` table (renamed from `content`) gets these changes:
- **Keep `extension_repo_id`** (user directive — will be wired up later, possibly renamed to a number)
- **Keep `extension_id` + `source_id` + `anime_url`** (denormalized for the hot `getMainEntryByExtension` lookup — avoids a JOIN on every extension-source open)
- **Keep `display_source`** as a single UX-preference column (which axis to PREFER for display — NOT a link-state flag; link state is implicit in `content_details` discriminators). **NOT split** into `active_data_source_type` + `active_extension_type` (that was v1's plan — no longer needed with the unified table). **Value semantics (per Review v2-2A Check 4)**: `display_source` stores the AXIS preference — values `'data_source'` \| `'extension'`. Migrate existing `'anilist'` values to `'data_source'` on schema rebuild. (The old values were source-name-level; the new values are axis-level, which scales to future Kitsu/MAL/TMDB.)
- **Drop `description`** (has 3 fallback-reader callers — migration specified in §4.1; column is never written non-null today)
- **Add `updateMainEntryTitle` query** (keeps title in sync when metadata refresh updates the title)

#### Dropped tables (4):
- `anilist_detail` → merged into `content_details` (data-axis)
- `extension_detail` → merged into `content_details` (extension-axis)
- `other_source_detail` → DROPPED (dead code, 0 callers, never written)
- `anime_metadata_cache` → ABSORBED into `content_details` (data-axis — 9/12 cols duplicate, 3 dead)

### Change 3 — Drop `app_metadata` (absorbed into `app_settings`)

**Why**: `app_metadata` is dead code — 0 Kotlin callers (grep confirmed). Its 2-column schema (key, value) is a strict subset of `app_settings`' 5-column schema. The prior plan deferred this; R-2 research confirmed it's safe to do now.

**What changes**:
- DROP the `app_metadata` table + its 2 queries (`setMetadata`, `getMetadata`)
- Any planned-but-never-wired use cases (schema version tracking) go into `app_settings` with `setting_category='internal'`
- Backup filter: `WHERE setting_category != 'internal'` (so internal flags don't pollute backups)

**No data loss** — the table was empty (0 rows, 0 callers).

### Change 4 — Keep `data_source` + `system` separate (per R-2 recommendation)

**Why**: The user asked about merging these. R-2 research evaluated + recommended **keep separate**:
1. Different column shapes (`data_source` has `type` column; `system` has `package_prefix` column)
2. FK integrity: `main_entry.data_source_id` → `data_source(id)` + `main_entry.system_id` → `system(id)` — merging into one `lookup` table would weaken FK integrity (can't enforce "data_source_id points to a data_source row" at the DB level)
3. Conceptual separation is real: `data_source` = metadata providers (AniList/TMDB/Kitsu/MAL); `system` = extension ecosystems (Aniyomi/CloudStream/Sora/MangaYomi)
4. Only saves 1 table — bad trade

The user said "if keeping them separate is the best approach then we can go with that." R-2 confirms it is.

---

## 4. Independent Improvements (bundled, no merge required)

### 4.1 Drop `description` from `main_entry` (with caller migration)
- `description TEXT` — used as a fallback in 3 places (per Review v2-1 FLAW 2):
  - `MainActivity.kt:671, 800` — `description = content.description ?: extDetail?.description`
  - `DownloadScanner.kt:276` — `description = record.description ?: extDetail?.description`
  - `DownloadStorageProvider.kt:265, 281` — via `DownloadContentInfo`
- These callers must be migrated to read `content_details.data_synopsis` (or `ext_description` as fallback) instead of `main_entry.description`.
- The column is never WRITTEN non-null today (ContentRepository.insertContent always passes `description = null`), so dropping it loses no data — but the read-side callers need updating.
- **Keep `extension_repo_id`** per user directive.

### 4.2 Fix 2 missing FK declarations (pre-existing bugs)
- `watch_progress.main_id` — `watch.sq:18` has only a comment, no FK clause. Add `FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE`.
- `notification_sent.main_id` — `notifications.sq:38-45` has no FK. Add the same.

**Precondition**: adding these FKs will FAIL if existing rows reference non-existent `main_id` values. Debug builds — wipe the DB (dev users clear app data once). No `.sqm` migration needed. SQLite can't ALTER TABLE to add a FK — DROP + CREATE the affected tables.

### 4.3 Fix `episode_number` type mismatch (schema + API)
- `notification_sent.episode_number INTEGER` → `REAL` (was rounding 12.5→12, breaking dedup)
- `episode_schedule.episode_number INTEGER` → `REAL` (same issue)

**Scope correction (per Review v2-2B Check 9)**: SQLDelight maps `REAL` → Kotlin `Double` (not `Long`). So this change DOES affect the Kotlin API surface — callers in `ScheduleStore`, `NotificationConfigStore`, `ActualReleaseUpdater`, `UpdateEngine`, `SmartReleaseCheckWorker` that currently use `Long` must change to `Double`. This is a compile-safe migration (SQLDelight catches the type mismatch at compile time). The plan includes BOTH the schema change AND the Kotlin caller migration.

### 4.4 Drop 4 dead queries
- `deleteAnimeMetadata` (0 callers — table being dropped)
- `deleteEpisodeMetadata` (0 callers — CASCADE handles it)
- `deleteBrowseCache` + `getAllBrowseCache` (0 callers)
- `deleteExtensionDetail` (0 callers — being replaced by `clearExtensionAxis`)

### 4.5 Drop 2 dead methods from ContentRepository
- `deleteExtensionDetail()` (0 callers)
- `getDefaultCategoryCount()` (0 callers)

### 4.6 Drop redundant indexes
- `idx_content_data_source` — no query filters on `data_source_id` alone
- `idx_content_extension` — single-column on `content(extension_id)`, redundant with composite `idx_content_extension_url` (leftmost column covered)
- `idx_content_genre_main` — duplicates leftmost column of composite PK
- `idx_library_item_main` — duplicates leftmost column of `idx_library_item_unique`

### 4.7 `DataSourceExtras` + `ExtensionExtras` typed accessors

The `data_extra_json` + `ext_extra_json` columns hold source-specific fields. To avoid repeating JSON-parse logic at every read site, introduce typed accessors:

```kotlin
@Serializable
data class DataSourceExtras(
    val idMal: Long? = null,
    val trailerUrl: String? = null,
    val ageRating: String? = null,
    val studio: String? = null,
    val coverUrlLarge: String? = null,  // for multi-size cover URLs
    val coverUrlSmall: String? = null,
) {
    fun toJson(): String = extrasJson.encodeToString(this)
    companion object {
        private val extrasJson = Json { ignoreUnknownKeys = true }
        fun fromJson(json: String?): DataSourceExtras =
            if (json.isNullOrBlank()) DataSourceExtras()
            else runCatching { extrasJson.decodeFromString(json) }.getOrDefault(DataSourceExtras())
    }
}

@Serializable
data class ExtensionExtras(
    val scanlatorGroup: String? = null,
    val chapterCount: Int? = null,
    val volumeCount: Int? = null,
) {
    // same pattern
}
```

The `ContentDetails` data class exposes `dataExtras: DataSourceExtras` + `extExtras: ExtensionExtras` (parsed once on read). `ignoreUnknownKeys = true` so adding new fields doesn't break parsing of existing rows.

### 4.8 `source_ref_id` String↔Int conversion convention

`data_source_ref_id` is TEXT (for uniformity). The episode metadata engine (D-190) calls `fetchEpisodeMetadata(anilistId: Int, malId: Int?, ...)`. The `ContentDetails` data class exposes typed accessors:

```kotlin
val anilistId: Int? get() = if (dataSourceType == "anilist") dataSourceRefId?.toIntOrNull() else null
val malId: Int? get() = if (dataSourceType == "mal") dataSourceRefId?.toIntOrNull() else dataExtras.idMal?.toInt()
val kitsuId: Int? get() = if (dataSourceType == "kitsu") dataSourceRefId?.toIntOrNull() else null
val tmdbId: Int? get() = if (dataSourceType == "tmdb") dataSourceRefId?.toIntOrNull() else null
```

**Note**: the existing `AniListDetail.anilistId` is `Int` (non-null). Changing to `Int?` breaks 12+ non-null consumers. Kotlin compile-safety catches all missed sites.

### 4.9 `unlinkSource` flow (fixes orphan-row bug)

The current `DetailsViewModel.unlinkSource()` doesn't touch the DB — leaves orphaned `extension_detail` rows. The new `clearExtensionAxis(mainId)` query NULLs the extension fields (keeps the row for re-linking). `unlinkAniList()` calls `clearDataSourceAxis(mainId)` (also NULLs — symmetric). Both also:
1. Update `main_entry.display_source` if the unlinked axis was the preferred display
2. Regenerate `main_entry.content_id` via `ContentIdGenerator.generate()` + `updateMainEntryContentId`

All 3 writes (content_details + main_entry.display_source + main_entry.content_id) are in a single DB transaction.

### 4.10 `content_id` regeneration + transaction boundaries

Every source-switch operation (link/unlink/switch) MUST also call `ContentIdGenerator.generate()` + `repo.updateMainEntryContentId(mainId, newContentId)`. The existing `ContentResolver.linkAniList` / `linkExtensionToExisting` / `unlinkAniList` already do this — the new `updateDataSourceAxis` / `updateExtensionAxis` queries are called FROM these resolver methods, not directly from ViewModels.

**Transaction boundaries (per Review v2-2A Check 6)**:
- **Switch flow** (`updateDataSourceAxis` / `updateExtensionAxis`): the detail-table UPDATE + `main_entry.content_id` UPDATE are wrapped in a single DB transaction at the resolver layer. `display_source` is NOT touched on switch (the axis preference stays the same).
- **Unlink flow** (`clearDataSourceAxis` / `clearExtensionAxis`): the detail-table NULL UPDATE + `main_entry.content_id` UPDATE + `main_entry.display_source` UPDATE (if the unlinked axis was the preferred display) are wrapped in a single DB transaction.

**New method (per Review v2-2A)**: add `ContentResolver.unlinkExtension(mainId)` — calls `clearExtensionAxis` + content_id regeneration + display_source update per §4.9. (The existing `unlinkSource()` in DetailsViewModel currently doesn't touch the DB — this new resolver method fixes that.)

### 4.11 Standardize naming
- Indexes: `idx_<full_table_name>_<cols>[_unique|_partial]`
- Retention query params: named `:cutoff` (not positional `?`)
- `audio_variant` everywhere (currently `video_audio` in 2 download tables)

---

## 5. Final Table List (22 tables, 10 groups)

### Group 1 — Identity & Sources (4 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `main_entry` | content.sq | RENAMED from `content` | Identity hub. Drop `description`, keep `extension_repo_id`, keep `display_source` as UX preference. |
| `data_source` | content.sq | UNCHANGED | Lookup: AniList/TMDB/Kitsu/MAL. Keep separate per R-2. |
| `system` | content.sq | UNCHANGED | Lookup: Aniyomi/CloudStream/Sora/MangaYomi. Keep separate per R-2. |
| `content_details` | content.sq | NEW (merges 4 tables) | One wide table, 26 cols, data_* + ext_* prefixes. |

### Group 2 — Library (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `library_category` | library.sq | UNCHANGED | User-defined categories. |
| `library_item` | library.sq | UNCHANGED | M:N junction (content ↔ category). Drop redundant id + index. |

### Group 3 — User Activity (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `watch_progress` | watch.sq | UPDATED | Add missing FK. |
| `activity_event` | tracking.sq | UNCHANGED | Activity log. |

### Group 4 — Updates & Schedule (3 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `episode_update` | episodeUpdate.sq | UNCHANGED | New-episodes feed (1:N). |
| `anime_update_state` | animeUpdateState.sq | UNCHANGED | Per-anime smart-update state (1:1). |
| `episode_schedule` | episodeSchedule.sq | UPDATED | Fix `episode_number` type (INTEGER→REAL). |

### Group 5 — Notifications (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `notification_config` | notifications.sq | UNCHANGED | Per-content prefs (1:1, backup-eligible). |
| `notification_sent` | notifications.sq | UPDATED | Add missing FK, fix `episode_number` type. |

### Group 6 — Ratings (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `user_rating` | ratings.sq | UNCHANGED | Per-anime rating (1:1). |
| `user_episode_rating` | ratings.sq | UNCHANGED | Per-episode rating (1:N). |

### Group 7 — Downloads (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `download_queue` | downloadQueue.sq | UNCHANGED | Active downloads (transient). |
| `downloaded_episode` | downloadedEpisode.sq | UNCHANGED | Completed downloads index (permanent). |

### Group 8 — Content Classification (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `genre` | genres.sq | UNCHANGED | Genre lookup (~40 AniList canonical). |
| `content_genre` | genres.sq | UNCHANGED | M:N junction. Drop redundant index. |

### Group 9 — Caches (2 tables)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `data_cache_episode` | dataCache.sq | UNCHANGED | Episode metadata (AniZip/Jikan/Kitsu). Handles 100k rows. |
| `browse_cache` | dataCache.sq | UNCHANGED | JSON blob cache for Browse page (6h TTL). |

### Group 10 — App Configuration (1 table)
| Table | .sq file | Status | Notes |
|-------|----------|--------|-------|
| `app_settings` | appSettings.sq | UPDATED | Absorbs `app_metadata` (dropped). Add `setting_category='internal'` convention. |

**Dropped tables (4)**: `anilist_detail`, `extension_detail`, `other_source_detail`, `anime_metadata_cache` → all merged into `content_details`.

**Total: 22 tables** (down from 26).

---

## 6. Cons + Risks (clearly highlighted)

### Change 1 — Rename `content` → `main_entry`
- **Con**: Mechanical churn — 9 .sq files + 4 indexes + 9 queries + 1 Kotlin string + 1 DbReference. Pure cost, no functional benefit beyond clarity.
- **Risk**: LOW. SQLDelight's type-safe generation catches any missed rename at compile time.

### Change 2 — Merge 4 tables → `content_details`
- **Con**: ~750-900 lines across ~32-38 files need updating (ContentRepository, ContentResolver, ContentModels, DataCacheRepository, 13 read-caller files, 4 write-caller files, 5 episode_number type-change sites). **Effort estimate corrected per Review v2-2B Check 11** (prior estimate of ~600 lines / ~25 files was under by ~20-30%).
- **Con**: The `extension_id` type change (INTEGER → TEXT in DB; Kotlin stays `Long?`). The `getAniListDetail != null` semantics change (now means "any data source linked" — callers must check `dataSourceType == "anilist"`).
- **Con**: `anilistId` accessor changes from `Int` (non-null) to `Int?` (nullable) — breaks 12+ non-null consumers. Kotlin compile-safety catches all.
- **Con**: AniList-only rows have ~13 NULL extension cols; extension-only rows have ~14 NULL data-source cols. ~1 byte/NULL overhead — negligible.
- **Risk**: MEDIUM. The write-path convergence is the riskiest part — if a call site is missed, the row could be partially stale.
- **Mitigation**: Debug builds — drop + recreate. Sub-agent compile review before push. CI is the final gate.

### Change 3 — Drop `app_metadata`
- **Con**: None — table is dead code (0 callers, 0 rows).
- **Risk**: ZERO.

### Change 4 — Keep `data_source` + `system` separate
- **Con**: None — this is a keep-separate decision, not a change.
- **Trade-off**: 1 table "saved" by merging, but FK integrity weakened. Not worth it per R-2.

### Overall migration risk
- **Debug builds only** — no production users, no migration scripts needed (CORE_RULES §30).
- **No data loss** — every dropped column/table is either duplicated, dead, or explicitly migrated.
- **CI is the final gate** — sub-agent compile review before push, then CI verifies.

---

## 7. What's Being Skipped / Deferred (clearly called out)

1. **Kotlin class renames** (`ContentRecord` → `MainEntryRecord`, etc.) — deferred. ~24 caller files. Separate session.
2. **`.sq` file rename** (`content.sq` → `main_entry.sq`) — deferred. Changes `database.contentQueries` property. Separate session.
3. **Split `AnimeDetailsProvider` interface** into `DataSourceProvider` + `ExtensionDetailsProvider` — deferred. Code-layer refactor, not schema. Separate session.
4. **`RetentionCoordinator` worker** — deferred. Would centralize the 5 retention queries. Separate session.
5. **`data_cache_episode` → `INSERT ON CONFLICT DO UPDATE`** — deferred. Performance optimization for batch refresh. Not needed at current scale.
6. **CHECK constraints** — included in the plan but optional. Can be added incrementally.
7. **`episode_number` API surface change** (Long→Double in Kotlin callers) — included in this plan per Review v2-2B Check 9. SQLDelight maps REAL→Double, so the schema change forces the Kotlin API change. NOT deferred (corrected from prior plan version).
8. **CloudStream/Sora/MangaYomi String extension IDs** — deferred. The `extension_id` column is TEXT (DB) but Kotlin types stay `Long?` for Aniyomi compatibility. When a future extension uses truly-String IDs, a separate `extension_id_str TEXT` column will be needed.
9. **`data_source` + `system` merge** — evaluated per R-2, recommended keep-separate. Revisit if the FK integrity concern can be solved cleanly.
10. **Library in "app settings" group** — evaluated per R-2, recommended keep as own group. Library is user data, not app config.

---

## 8. Future-Proofing (how this handles the multi-source + multi-extension vision)

### Adding a new data source (e.g. MAL)
1. Implement `MalDataSourceProvider : DataSourceProvider` (code layer)
2. User switches data source → `ContentResolver.linkDataSource("mal", malId, ...)` → calls `updateDataSourceAxis` on `content_details`: sets `data_source_type='mal'`, `data_source_ref_id=malId`, new metadata fields
3. The `main_id` stays the same. The previous AniList ID is preserved in `data_extra_json` as `{"previous_anilist_id": 12345}` for re-switching back.
4. **Zero schema change.** (MAL-specific fields like `main_picture.medium`/`.large` go into `data_extra_json` + `data_cover_url`.) **⚠️ Note (per Review v2-2A Check 7)**: MAL's `related_anime` (relations list — sequels/prequels/side stories) is NOT addressable via `extra_json` alone if cross-content navigation is needed — a future `content_relation` junction table would be added when that feature is wired. Out of scope for this plan.

### Adding a new extension ecosystem (e.g. CloudStream)
1. Implement `CloudStreamVideoExtensionProvider : VideoExtensionProvider` with `ecosystemId="cloudstream"` (code layer)
2. User switches extension → `ContentResolver.linkExtension("cloudstream", ...)` → calls `updateExtensionAxis` on `content_details`: sets `extension_type='cloudstream'`, new `extension_id`/`source_id`/`anime_url`
3. The `main_id` stays the same. Episode list changes (different numbering) — `data_cache_episode` rows for the old extension are invalidated + re-fetched.
4. **Zero schema change for Long-ID extensions.** ⚠️ CloudStream uses String source IDs — a future `extension_id_str TEXT` column would be needed (deferred per §7 item 8).

### Independent switching
The `data_*` + `ext_*` column prefixes + independent `updateDataSourceAxis` / `updateExtensionAxis` queries allow the user to switch either independently. Example: AniList metadata + Aniyomi extension-A → MAL metadata + Aniyomi-extension-A → MAL metadata + CloudStream-extension-B. Each combination is a valid state.

### Future content types (manga, novels, images)
- **Manga**: `ext_author` + `ext_artist` already in schema. `ext_extra_json` for `chapter_count`/`volume_count`/`scanlator_group`.
- **Novels**: `ext_extra_json` for `publisher`/`isbn`. Most fields shared (title, description, genres, cover, status).
- **Images**: sparse row, mostly NULLs. No schema change.
- **Zero schema change** for any content type — the 26-column schema + `extra_json` covers all of them.

---

## 9. Verification Checklist (for the implementation session)

- [ ] All 13 FK declarations renamed `content` → `main_entry`
- [ ] 2 missing FKs added (`watch_progress`, `notification_sent`) — DROP + CREATE the tables
- [ ] `content_details` table created (26 cols, 2 indexes, 11 queries)
- [ ] `anilist_detail` + `extension_detail` + `other_source_detail` + `anime_metadata_cache` DROPPED
- [ ] `app_metadata` DROPPED (absorbed into `app_settings`)
- [ ] `main_entry`: drop `description`, keep `extension_repo_id`, keep `display_source`, add `updateMainEntryTitle`
- [ ] `episode_number` type fixed in `notification_sent` + `episode_schedule`
- [ ] Dead queries/methods/indexes removed
- [ ] `DataSourceExtras` + `ExtensionExtras` typed accessors implemented
- [ ] Sub-agent compile review passes
- [ ] CI green
- [ ] Device test: link source, switch source, unlink source — verify no orphaned rows
- [ ] Doc update: progress.md, decisions.md, changelog.md, knowledge/architecture.md, dashboard

---

## 10. Research Basis

This plan v2 is grounded in:
- **Prior session**: 5 Explore sub-agents (Tasks 2-a through 2-e) — content table, detail tables, cache trio, data source vs extension, keep-separate groups.
- **This session**: 2 Explore sub-agents (R-1 + R-2) — content_details design (Option A) + re-evaluate merges + grouping.
- **4 review iterations** via sub-agents (not self-review per user instruction).

Full research is in `/home/z/my-project/worklog.md` (Tasks 2-a through 2-e, R-1, R-2, review-1 through review-4).

---

*This is a PROPOSAL v2. No schema changes will be made until the user approves. The dashboard at `/database-plan/` presents this plan in a scannable format for review.*
