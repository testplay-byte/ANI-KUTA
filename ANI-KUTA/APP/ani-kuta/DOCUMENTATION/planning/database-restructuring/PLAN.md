# Database Restructuring Plan — ANI-KUTA

> **Status**: PROPOSAL — not yet implemented. Awaiting user approval.
> **Date**: 2026-08-14
> **Author**: Main agent (researched via 5 parallel Explore sub-agents, reviewed via 4 sub-agent iterations)
> **Scope**: Schema restructuring of the 26-table SQLDelight database. No code changes this phase — this is the plan only.
> **Migration policy**: Debug builds only — schema can be rebuilt freely per CORE_RULES §30 (drop + recreate, no `.sqm` migration files needed).

---

## 1. Executive Summary

This plan restructures the database from **26 tables → 24 tables** through 3 changes:

1. **Rename** `content` → `main_entry` (the identity hub — clearer name, avoids `android.content.*` collision)
2. **Merge** `anilist_detail` + `extension_detail` + `other_source_detail` (3 tables) → `data_source_detail` + `extension_detail` (2 tables), keeping data-source metadata + extension metadata **conceptually separate** per the user's directive
3. **Absorb** `anime_metadata_cache` into `data_source_detail` (9/12 columns were duplicated; 3 columns were dead)

Plus **independent improvements** bundled in (no merge required):
- Drop 2 dead columns from `main_entry` (`description`, `extension_repo_id`)
- Fix 2 missing FK declarations (`watch_progress`, `notification_sent`)
- Fix `episode_number` type mismatch (INTEGER → REAL in `notification_sent` + `episode_schedule`)
- Split `display_source` into `active_data_source_type` + `active_extension_type` (independent switching)
- Drop 4 dead queries + 2 dead methods
- Standardize index naming + retention query param style

**Tables NOT merged** (confirmed keep-separate via research): updates group, notifications group, ratings group, genres group, library group, `data_cache_episode`, `browse_cache`.

---

## 2. Design Principles (the "why" behind every decision)

1. **Data sources ≠ extensions.** Data sources (AniList/Kitsu/MAL/TMDB) provide metadata. Extensions (Aniyomi/CloudStream/Sora/MangaYomi) provide video playback + episode lists. These are orthogonal — a user can switch either independently. The schema must reflect this separation.

2. **Future-proof, not over-engineered.** Adding a new data source (e.g. AnimePlanet) or a new extension ecosystem (e.g. Kotatsu) must NOT require a schema change. Achieved via `source_type` / `extension_type` discriminator columns + `extra_json` for source-specific extras.

3. **In-place switching.** When the user switches the active data source or extension for a content, the existing row is UPDATEd — not deleted + re-inserted. The `main_id` stays stable throughout.

4. **Stable identity.** `main_id` (UUID) is assigned once on first sighting, never changes, survives all source switches. All child tables FK to it with `ON DELETE CASCADE`.

5. **No data loss.** Every column in every dropped table is either (a) duplicated elsewhere, (b) dead (zero callers), or (c) explicitly migrated. Verified by the 5 research sub-agents.

---

## 3. The 3 Core Changes (detailed)

### Change 1 — Rename `content` → `main_entry`

**Why**: The `content` table's real job is the identity hub — it holds the stable `main_id` + the changing `content_id` + links to all per-source detail tables. The name "content" is generic + collides with `android.content.ContentResolver` / `android.content.Context` (confusing for new agents). `main_entry` accurately reflects "the main entry row that all detail rows hang off of."

**What changes**:
- Table name: `content` → `main_entry`
- 4 indexes renamed: `idx_content_*` → `idx_main_entry_*`
- 9 SQLDelight queries renamed: `getContentBy*` → `getMainEntryBy*`, `insertContent` → `insertMainEntry`, etc.
- 1 NEW query added (per §4.12 item 8): `updateMainEntryTitle(mainId, title, updatedAt)` — keeps `main_entry.title` in sync when the anime metadata refresh flow updates the title (was previously only updating the now-dropped `anime_metadata_cache.title`).
- 13 FK declarations across 9 `.sq` files updated: `REFERENCES content(main_id)` → `REFERENCES main_entry(main_id)`
- 1 Kotlin string literal in `DatabaseDriverFactory.kt:168` updated
- 1 `DbReference("content", ...)` in `DetailsScreen.kt:385` → `DbReference("main_entry", ...)`

**What does NOT change** (optional, deferred to a future cleanup):
- Kotlin class names (`ContentRecord`, `ContentRepository`, `ContentResolver`, etc.) — these are decoupled from the table name. Renaming them is a separate, larger churn (~24 caller files). The plan recommends keeping them for now.
- The `.sq` FILE name (`content.sq`) — could be renamed to `main_entry.sq`, but that changes the SQLDelight-generated `database.contentQueries` → `database.mainEntryQueries` (19 references in ContentRepository + 1 in GenreRepository). The plan recommends renaming the file too, for consistency, but this is the riskier part.

**Migration**: Debug builds — drop + recreate. No `.sqm` file needed.

### Change 2 — Merge 3 detail tables → 2 (keeping data source ≠ extension separate)

**⚠️ Type-change note (per Review Iteration 1)**: The `extension_id` + `source_id` columns change from INTEGER → TEXT in the DB. However, **the Kotlin types stay `Long?`** to preserve `.data.json` compatibility (`ContentDataJson.extensionId: Long?` would fail to deserialize if the DB stored a String). The conversion happens at the DB boundary via SQLDelight's column adapter (Long ↔ TEXT). This keeps existing Kotlin code (9 call sites that do `content.extensionId ?: extDetail?.extensionId`) compiling unchanged. Future CloudStream extensions that use String source IDs would need a separate column or a hash-to-Long mapping (deferred — not a concern for Aniyomi-only today).

**Why**: The user wants `anilist_detail` + `extension_detail` + `other_source_detail` merged into a unified structure that:
- Holds metadata from ANY data source (AniList now, Kitsu/MAL/TMDB later)
- Holds metadata from ANY extension (Aniyomi now, CloudStream/Sora/MangaYomi later)
- Updates in-place when the user switches source
- Keeps the two concepts (data source vs extension) SEPARATE

**Design choice**: Option C (from the research) — TWO tables, not one. This honors the user's "keep them separate" directive at the schema level, matching the existing `data_source` vs `system` lookup-table split.

#### New table: `data_source_detail` (replaces `anilist_detail` + `other_source_detail`)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `main_id` | TEXT | NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE | Stable identity link |
| `source_type` | TEXT | nullable | Discriminator: 'anilist' \| 'kitsu' \| 'mal' \| 'tmdb' \| NULL (NULL = no data source linked). **Nullable per Review Iteration 2A** — allows `clearDataSourceAxis` to NULL the field on unlink. |
| `source_ref_id` | TEXT | nullable | The external ID (anilist_id as string, mal_id, kitsu_id, tmdb_id). **Nullable** — NULL when no data source linked. |
| `title` | TEXT | nullable | Display title from the data source |
| `description` | TEXT | nullable | Synopsis/description |
| `genres` | TEXT | nullable | Comma-separated genre list |
| `status` | TEXT | nullable | 'FINISHED' \| 'RELEASING' \| 'CANCELLED' \| 'HIATUS' |
| `score` | INTEGER | nullable | Average score 0-100 |
| `episodes` | INTEGER | nullable | Total episode count |
| `season` | TEXT | nullable | 'WINTER' \| 'SPRING' \| 'SUMMER' \| 'FALL' |
| `season_year` | INTEGER | nullable | Year of season airing |
| `cover_url` | TEXT | nullable | Cover image URL |
| `banner_url` | TEXT | nullable | Banner image URL |
| `extra_json` | TEXT | nullable | Source-specific extras (e.g. `{"id_mal":12345,"trailer_url":"..."}` for AniList; `{"age_rating":"TV-14"}` for Kitsu) |
| `updated_at` | INTEGER | NOT NULL | Last-write timestamp |

**Queries** (8):
- `getDataSourceDetail(mainId)` — SELECT * WHERE main_id = :mainId
- `getMainEntryByDataSourceRef(sourceType, sourceRefId)` — JOIN to main_entry for reverse lookup
- `upsertDataSourceDetail(...)` — INSERT OR REPLACE (full row)
- `updateDataSourceAxis(...)` — partial UPDATE of all data-source fields (for in-place switching)
- `clearDataSourceAxis(mainId)` — NULL out all data-source fields (for unlink)
- `deleteDataSourceDetail(mainId)` — DELETE (for hard unlink)
- `getAllDataSourceDetails()` — for backup dump
- `getDataSourceDetailByAniListId(anilistId)` — convenience: `WHERE source_type='anilist' AND source_ref_id = :anilistId` (preserves the hot lookup path)

**Migration from `anilist_detail`**:
- `anilist_id INTEGER` → `source_ref_id TEXT` (cast to string, nullable)
- `id_mal INTEGER` → moves to `extra_json` as `{"id_mal": <value>}`
- `synopsis TEXT` → renamed to `description TEXT`
- All other columns map 1:1
- Add `source_type TEXT` (nullable, per §4.12 item 1 — allows `clearDataSourceAxis` to NULL it on unlink). Existing rows get `source_type='anilist'` during migration.

#### Updated table: `extension_detail` (extended, not replaced)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `main_id` | TEXT | NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE | Stable identity link |
| `extension_type` | TEXT | nullable | Discriminator: 'aniyomi' \| 'cloudstream' \| 'sora' \| 'mangayomi' \| NULL (NULL = no extension linked). **Nullable per Review Iteration 2A** — allows `clearExtensionAxis` to NULL the field on unlink. NEW COLUMN |
| `extension_id` | TEXT | nullable | Aniyomi source.id (was INTEGER → TEXT for future CloudStream string IDs). **Nullable** — NULL when no extension linked. TYPE CHANGE |
| `source_id` | TEXT | nullable | Same as extension_id (legacy dup, kept for compatibility). **Nullable**. TYPE CHANGE |
| `anime_url` | TEXT | nullable | The content's URL on the source. **Nullable** — NULL when no extension linked. |
| `title` | TEXT | nullable | Display title from the extension |
| `description` | TEXT | nullable | Extension-provided description |
| `genres` | TEXT | nullable | Extension-provided genres |
| `status` | TEXT | nullable | Extension-provided status |
| `author` | TEXT | nullable | Manga author (future manga support) |
| `artist` | TEXT | nullable | Manga artist |
| `thumbnail_url` | TEXT | nullable | Extension-provided thumbnail |
| `extra_json` | TEXT | nullable | Source-specific extras — NEW COLUMN |
| `updated_at` | INTEGER | NOT NULL | Last-write timestamp |

**Queries** (7):
- `getExtensionDetail(mainId)` — SELECT * WHERE main_id = :mainId
- `getMainEntryByExtension(extensionType, extensionId, animeUrl)` — JOIN for reverse lookup
- `upsertExtensionDetail(...)` — INSERT OR REPLACE (full row)
- `updateExtensionAxis(...)` — partial UPDATE of all extension fields (for in-place switching) — NEW QUERY
- `clearExtensionAxis(mainId)` — NULL out all extension fields (for unlink — **fixes the orphan-row bug**) — NEW QUERY
- `deleteExtensionDetail(mainId)` — DELETE (for hard unlink)
- `getAllExtensionDetails()` — for backup dump

**Migration from `extension_detail`**:
- Add `extension_type TEXT` (nullable, per §4.12 item 1 — allows `clearExtensionAxis` to NULL it on unlink). Existing rows get `extension_type='aniyomi'` during migration.
- Change `extension_id` + `source_id` + `anime_url` to nullable (were NOT NULL; now nullable per §4.12 item 1).
- Change `extension_id` + `source_id` from INTEGER → TEXT (SQLite is dynamically typed, so existing Long values work; Kotlin types stay `Long?` per §3 Change 2 note).
- Add `extra_json TEXT` (nullable)
- All other columns unchanged

#### Dropped: `other_source_detail` (DEAD CODE — 0 callers, never written)

The generic KV table designed for "future TMDB/Kitsu/MAL" was never wired up. The new `data_source_detail` table handles all future data sources via the `source_type` discriminator. **Zero data loss** — the table was empty.

### Change 3 — Absorb `anime_metadata_cache` → `data_source_detail`

**Why**: 9 of 12 `anime_metadata_cache` columns duplicate `anilist_detail` (which becomes `data_source_detail`). The 3 unique columns are all dead:
- `title` → duplicates `main_entry.title` (set from the same source)
- `source_type` → hardcoded `'anilist'`, never read
- `fetched_at` → write-only, no refresh-logic reader

**What changes**:
- DROP the `anime_metadata_cache` table
- DROP the 3 `DataCacheRepository` methods: `getAnimeMetadata`, `upsertAnimeMetadata`, `deleteAnimeMetadata` (last one already dead)
- DROP the `CachedAnimeMetadata` data class
- Redirect 6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) to read from `data_source_detail` instead

**Migration**: The 9 duplicate columns are already in `anilist_detail` → `data_source_detail`. The `title` column's data is already in `main_entry.title`. No data loss.

---

## 4. Independent Improvements (bundled, no merge required)

These are improvements the research surfaced. They're independent of the 3 core changes but make sense to bundle:

### 4.1 Drop 2 dead columns from `main_entry`
- `description TEXT` — no UI code reads `main_entry.description` directly (always reads `anilist_detail.synopsis` or `extension_detail.description`). Dead column.
- `extension_repo_id INTEGER` — D-192 dropped the FK + the lookup table; column is always NULL.

### 4.2 Split `display_source` into 2 columns

Current: `display_source TEXT` with values `'anilist'` | `'extension'` — conflates data source + extension into one column.

New:
- `active_data_source_type TEXT` — nullable: `'anilist'` | `'kitsu'` | `'mal'` | `'tmdb'` | NULL
- `active_extension_type TEXT` — nullable: `'aniyomi'` | `'cloudstream'` | `'sora'` | `'mangayomi'` | NULL

This enables **independent switching** — the user can switch the data source (AniList→MAL) without touching the extension (still Aniyomi-extension-A), and vice versa.

**Migration (corrected per Review Iteration 1)**: the migration must check the PRESENCE of link fields (`data_source_id` IS NOT NULL → data source is linked; `system_id` IS NOT NULL → extension is linked), NOT the `display_source` column value. This is because `linkAniList` + `linkExtensionToExisting` don't update `display_source` when cross-linking — so a content row with `display_source='anilist'` can have BOTH detail rows populated. The corrected migration:
- If `data_source_id IS NOT NULL` → `active_data_source_type = 'anilist'` (current default), else NULL
- If `system_id IS NOT NULL` (or `extension_id IS NOT NULL`) → `active_extension_type = 'aniyomi'` (current default), else NULL
- DROP `display_source` after migration

### 4.3 Fix 2 missing FK declarations (pre-existing bugs)
- `watch_progress.main_id` — `watch.sq:18` has only a comment, no FK clause. Add `FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE`.
- `notification_sent.main_id` — `notifications.sq:38-45` has no FK. Add the same.

**⚠️ Precondition (per Review Iteration 1)**: adding these FKs will FAIL if existing rows reference non-existent `main_id` values. Since this is debug-build-only (CORE_RULES §30), the fix is: wipe the DB (dev users clear app data once) before applying the new schema. No `.sqm` migration needed — the fresh CREATE TABLE includes the FK.

### 4.4 Fix `episode_number` type mismatch (real bug)
- `notification_sent.episode_number INTEGER` → `REAL` (was rounding 12.5→12, breaking dedup)
- `episode_schedule.episode_number INTEGER` → `REAL` (same issue)

4 other tables already use `REAL` for fractional episodes (12.5 for OVAs).

### 4.5 Drop 4 dead queries
- `deleteAnimeMetadata`, `deleteEpisodeMetadata`, `deleteBrowseCache`, `getAllBrowseCache` (all 0 callers)
- `deleteExtensionDetail` (0 callers — but being repurposed in Change 2)
- `getAllUserEpisodeRatings` (0 callers — but should be WIRED UP for backup, not deleted)

### 4.6 Drop 2 dead methods from ContentRepository
- `deleteExtensionDetail()` (0 callers)
- `getDefaultCategoryCount()` (0 callers)

### 4.7 Drop redundant indexes
- `idx_content_data_source` — no query filters on `data_source_id` alone
- `idx_content_genre_main` — duplicates leftmost column of composite PK
- `idx_library_item_main` — duplicates leftmost column of `idx_library_item_unique`

### 4.8 Standardize naming
- Indexes: `idx_<full_table_name>_<cols>[_unique|_partial]` (7 indexes have shortened names)
- Retention query params: standardize on named `:cutoff` (2 use positional `?`)
- `audio_variant` everywhere (currently `video_audio` in 2 download tables)

### 4.9 Typed `DataSourceExtras` accessor for `extra_json` (per Review Iteration 1)

The `extra_json` column on `data_source_detail` holds source-specific fields (e.g. AniList's `id_mal`, `trailer_url`; Kitsu's `age_rating`). To avoid repeating JSON-parse logic at every read site (5+ callers, including the episode metadata engine which needs `id_mal` for Jikan API calls), introduce a typed accessor:

```kotlin
// In :core:content, alongside DataSourceDetail
@Serializable
data class DataSourceExtras(
    val idMal: Long? = null,
    val trailerUrl: String? = null,
    val ageRating: String? = null,
    val coverUrlLarge: String? = null,  // for multi-size cover URLs (§4.12 item 6)
    val coverUrlSmall: String? = null,
    // future: add fields as new data sources are added
) {
    fun toJson(): String = extrasJson.encodeToString(this)
    companion object {
        // Per §4.12 item 4: ignoreUnknownKeys = true so adding new fields
        // doesn't break parsing of existing rows.
        private val extrasJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun fromJson(json: String?): DataSourceExtras =
            if (json.isNullOrBlank()) DataSourceExtras()
            else runCatching { extrasJson.decodeFromString(json) }.getOrDefault(DataSourceExtras())
    }
}
```

The `DataSourceDetail` data class exposes `extras: DataSourceExtras` (parsed once on read). Callers do `detail.extras.idMal` instead of parsing JSON. This keeps type safety for the common fields while allowing future sources to add fields without schema changes.

### 4.10 `unlinkSource` flow clarification (per Review Iteration 1)

The current `DetailsViewModel.unlinkSource()` (line 1693) does NOT touch the DB — it only clears SharedPreferences + in-memory state, leaving orphaned `extension_detail` rows. The plan introduces two queries:
- `clearExtensionAxis(mainId)` — NULLs out the extension fields (keeps the row, marks "no extension linked")
- `deleteExtensionDetail(mainId)` — DELETE the row entirely

**Decision**: `unlinkSource()` should call `clearExtensionAxis(mainId)` (not delete). Rationale:
- Keeps the row for re-linking (the `main_id` stays, the data-source side is untouched)
- Matches the asymmetry with `unlinkAniList()` which DELETES the `anilist_detail` row — but that's because AniList unlink is a heavier operation (removes the data-source identity entirely). Extension unlink is lighter — the user is just detaching the extension, not the identity.
- The `active_extension_type` on `main_entry` is set to NULL by `clearExtensionAxis`.

The existing `ContentResolver.unlinkAniList()` (which DELETES `anilist_detail` → will DELETE `data_source_detail`) stays as-is — that's the "hard unlink" path for the data-source side.

### 4.11 `source_ref_id` String↔Int conversion convention (per Review Iteration 1)

The `source_ref_id` column is TEXT (for uniformity across AniList/Kitsu/MAL/TMDB IDs). But the episode metadata engine (D-190) calls `fetchEpisodeMetadata(anilistId: Int, malId: Int?, ...)` — it expects Int.

**Convention**: the `DataSourceDetail` data class exposes typed accessors:
```kotlin
val anilistId: Int? get() = if (sourceType == "anilist") sourceRefId?.toIntOrNull() else null
val malId: Int? get() = if (sourceType == "mal") sourceRefId?.toIntOrNull() else extras.idMal?.toInt()
val kitsuId: Int? get() = if (sourceType == "kitsu") sourceRefId?.toIntOrNull() else null
val tmdbId: Int? get() = if (sourceType == "tmdb") sourceRefId?.toIntOrNull() else null
```

Callers use `detail.anilistId` (typed Int?) instead of `detail.sourceRefId.toIntOrNull()`. This centralizes the conversion + handles the "AniList row also has id_mal in extras" case (for Jikan API calls which need MAL ID even when the active source is AniList).

**⚠️ Note (per Review Iteration 2B Check 2)**: the existing `AniListDetail.anilistId` is `Int` (non-null). Changing to `Int?` (nullable via computed property) breaks 12+ non-null consumers. **Mitigation**: the `DataSourceDetail` data class keeps `sourceRefId: String?` (nullable), but the typed accessors return `Int?`. Callers that previously did `anilistDetail.anilistId` (non-null) must add a null check: `dataSourceDetail.anilistId ?: 0` or `dataSourceDetail.anilistId != null`. This is a deliberate API-surface change — the implementing agent must grep for all `anilistId` usages + add null handling. The compile-safety of Kotlin will catch every missed site.

### 4.12 Review Iteration 2 — consolidated fixes

**From Review 2A (architecture)**:

1. **NOT NULL → nullable (Check 5, FLAW)**: `data_source_detail.source_type` + `source_ref_id` + `extension_detail.extension_type` + `extension_id` + `source_id` + `anime_url` are now **nullable** (see updated schemas in §3 Change 2). This allows `clearDataSourceAxis` / `clearExtensionAxis` to NULL the fields on unlink. The "is linked" state is determined by `main_entry.active_data_source_type IS NOT NULL` / `active_extension_type IS NOT NULL` (or by the detail table fields being non-null).

2. **`updateExtensionAxis` / `updateDataSourceAxis` atomicity (Check 7)**: these are **single atomic UPDATE statements** (multi-column), wrapped in a DB transaction that ALSO updates `main_entry.active_data_source_type` / `active_extension_type` + regenerates `main_entry.content_id` (via `updateContentContentId`). The transaction ensures all 3 writes (detail table + active_*_type + content_id) succeed or fail together.

3. **`content_id` regeneration (Check 9)**: every source-switch operation (link/unlink/switch) MUST also call `ContentIdGenerator.generate()` + `repo.updateContentContentId(mainId, newContentId)` to regenerate `content_id` on `main_entry`. This preserves the invariant "content_id changes when sources switch." The existing `ContentResolver.linkAniList` / `linkExtensionToExisting` / `unlinkAniList` already do this — the new `updateDataSourceAxis` / `updateExtensionAxis` queries must be called FROM these resolver methods (which handle the content_id regeneration), not directly from ViewModels.

4. **`DataSourceExtras.fromJson` ignoreUnknownKeys (Check 4)**: the Json instance MUST use `Json { ignoreUnknownKeys = true }` (matching `ContentDataJson.kt:109-113`). Without it, adding any new field to `extra_json` in the future would silently break parsing of ALL existing rows (the `runCatching` swallows the exception + returns empty).

5. **`malId` accessor type (Check 6)**: `extras.idMal` is `Long?` (matching AniList's id_mal which can exceed Int range for large IDs). The `malId` accessor returns `Int?` via `extras.idMal?.toInt()` — the episode metadata engine takes `Int?`, so the conversion is at the accessor. (If this is a concern, `DataSourceExtras.idMal` could be changed to `Int?` to match — but `Long?` is safer for future MAL IDs.)

6. **Multi-size cover URLs (Check 12)**: convention — `cover_url` holds the PRIMARY cover (medium for MAL, large for AniList). Additional sizes go in `extra_json` as `{"cover_url_large": "...", "cover_url_small": "..."}`. The UI reads `cover_url` for default display + `extras.coverUrlLarge` when a larger image is needed.

**From Review 2B (implementation feasibility)**:

7. **`getAniListDetail != null` semantics change (Check 3)**: post-merge, `getDataSourceDetail(mainId) != null` means "ANY data source is linked" — NOT "AniList specifically is linked." Callers that branch on "AniList is linked" (e.g. `LibraryViewModel:350-365`) must instead check `dataSourceDetail?.sourceType == "anilist"`. The implementing agent must grep for `getAniListDetail` callers + audit each for this semantic change.

8. **`cachedMeta.title → content.title` stale-title issue (Check 4)**: the `anime_metadata_cache` absorption redirects `cachedMeta.title` reads to `main_entry.title`. BUT the refresh flow currently updates `cachedMeta.title` (via `upsertAnimeMetadata`) WITHOUT updating `main_entry.title`. Post-merge, the refresh flow must ALSO call `repo.updateMainEntryTitle(mainId, newTitle)` (new query) to keep `main_entry.title` in sync. **New query needed**: `updateMainEntryTitle(mainId, title, updatedAt)` on `main_entry`.

9. **`clearExtensionAxis` NULLs propagate to `.data.json` (Check 6)**: `DownloadScanner.reconcileDataJsonFromContent` reads `extension_detail` fields to write `.data.json`. If `clearExtensionAxis` NULLs them, the `.data.json` would lose the extension fields. **Mitigation**: `reconcileDataJsonFromContent` already handles nullable fields (it uses `?:` fallbacks). The `.data.json` would correctly reflect "no extension linked" — which is the desired behavior. No fix needed, but the implementing agent should verify the scanner handles NULLs gracefully.

10. **`episode_number` INTEGER→REAL is a half-fix (Check 8)**: the DB type change is correct, but the Kotlin API surface (`episodeNumber: Long` in many places) truncates 12.5→12L at the call site BEFORE reaching the DB. **Scope clarification**: this plan fixes the SCHEMA type (DB column). The API surface change (Long→Float/Double in Kotlin callers) is a SEPARATE task — deferred. The schema fix is still worthwhile (it prevents future breakage if the API is ever updated to use Float). **Document this as "schema-only fix, API surface change deferred."**

11. **FK-add requires DROP TABLE (Check 7)**: SQLite can't ALTER TABLE to add a FK. For debug builds (CORE_RULES §30), the implementing agent must DROP + CREATE the affected tables (`watch_progress`, `notification_sent`) with the FK included. The `DatabaseDriverFactory.onOpen` migration should include `DROP TABLE IF EXISTS` + `CREATE TABLE` blocks for these 2 tables. (Existing dev data is wiped — acceptable per §30.)

---

## 5. Tables NOT Changing (confirmed keep-separate)

These 21 tables are confirmed correctly separated via research. No merge. (Some get the minor improvements from §4.)

| Table | .sq file | Why kept separate | Improvements bundled |
|-------|----------|-------------------|---------------------|
| `data_source` | content.sq | Lookup table (AniList/TMDB/Kitsu/MAL) | — |
| `system` | content.sq | Lookup table (Aniyomi/CloudStream/Sora/MangaYomi) | — |
| `main_entry` (renamed from `content`) | content.sq | Identity hub | Drop 2 dead cols, split display_source, rename |
| `data_source_detail` (merged from anilist_detail + other_source_detail) | content.sq | Data-source metadata | New table |
| `extension_detail` (extended) | content.sq | Extension metadata | Add extension_type + extra_json, fix unlink bug |
| `data_cache_episode` | dataCache.sq | Episode-level metadata (AniZip/Jikan/Kitsu) — different cardinality + sources | Keep PK, maybe switch to INSERT ON CONFLICT |
| `browse_cache` | dataCache.sq | JSON blob cache for Browse page — different shape | — |
| `app_metadata` | app.sq | Generic KV (schema version) | (Future: merge into app_settings — deferred) |
| `app_settings` | appSettings.sq | User settings KV | — |
| `watch_progress` | watch.sq | Episode watch progress | Add missing FK |
| `activity_event` | tracking.sq | Activity log | — |
| `library_category` | library.sq | User categories | Drop redundant UNIQUE |
| `library_item` | library.sq | Library junction | Drop redundant id + index, rename FK |
| `genre` | genres.sq | Genre lookup | — |
| `content_genre` | genres.sq | Genre junction | Drop redundant index, rename FK |
| `episode_update` | episodeUpdate.sq | Updates feed | Drop redundant id, rename FK, add CHECKs |
| `anime_update_state` | animeUpdateState.sq | Per-anime update state | Rename FK, add CHECKs |
| `episode_schedule` | episodeSchedule.sq | Airing schedule | Fix episode_number type, rename FK |
| `notification_config` | notifications.sq | Per-content notif prefs | Rename FK, add CHECKs |
| `notification_sent` | notifications.sq | Notif dedup log | Add missing FK, fix episode_number type, add CHECKs |
| `user_rating` | ratings.sq | Per-anime rating | Rename FK, add CHECK |
| `user_episode_rating` | ratings.sq | Per-episode rating | Rename FK, add CHECK |
| `download_queue` | downloadQueue.sq | Download queue | — |
| `downloaded_episode` | downloadedEpisode.sq | Downloaded episodes index | — |

**Total: 24 tables** (down from 26 — dropped `other_source_detail` + `anime_metadata_cache`; `content`→`main_entry` + `anilist_detail`→`data_source_detail` are renames, net 0).

---

## 6. Cons + Risks (clearly highlighted)

### Change 1 — Rename `content` → `main_entry`
- **Con**: Mechanical churn — 9 .sq files + 4 indexes + 9 queries + 1 Kotlin string + 1 DbReference. Pure cost, no functional benefit beyond clarity.
- **Con**: If the `.sq` FILE is also renamed, `database.contentQueries` → `database.mainEntryQueries` (20 references). Riskier.
- **Risk**: LOW. SQLDelight's type-safe generation catches any missed rename at compile time.
- **Mitigation**: Do the table rename first (low risk). Defer the file rename to a separate session if desired.

### Change 2 — Merge 3 detail tables → 2
- **Con**: ~600 lines across ~25 files need updating (ContentRepository, ContentResolver, ContentModels, 13 read-caller files, 4 write-caller files — actual file count is ~25 per Review 2B Check 10).
- **Con**: The `extension_id` type change (INTEGER → TEXT in DB; Kotlin stays `Long?` per §3 note). The `getAniListDetail != null` semantics change (now means "any data source linked" — callers must check `sourceType == "anilist"` per §4.12 item 7).
- **Con**: `extra_json` loses some type safety (acceptable — `DataSourceExtras` typed accessor covers the 80% case per §4.9).
- **Con**: `anilistId` accessor changes from `Int` (non-null) to `Int?` (nullable) — breaks 12+ non-null consumers (§4.11 note). Kotlin compile-safety catches all.
- **Risk**: MEDIUM. The write-path convergence is the riskiest part — if a call site is missed, the merged row could be partially stale.
- **Risk (RESOLVED)**: the `clearExtensionAxis` / `clearDataSourceAxis` queries originally couldn't NULL NOT NULL columns — **resolved** by making the axis fields nullable (§4.12 item 1).
- **Mitigation**: Debug builds — drop + recreate (CORE_RULES §30). Sub-agent compile review before push. CI is the final gate.

### Change 3 — Absorb `anime_metadata_cache`
- **Con**: 6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) need redirecting.
- **Risk**: MEDIUM — write-path convergence (must redirect both reads AND writes to avoid partial staleness).
- **Mitigation**: Same 2-step approach.

### Independent improvements
- **Con**: The `episode_number` type fix (INTEGER → REAL) requires a table rebuild for `notification_sent` + `episode_schedule` (can't ALTER COLUMN type in SQLite). Debug builds — drop + recreate is fine.
- **Risk**: LOW. All improvements are additive or fix existing bugs.

### Overall migration risk
- **Debug builds only** — no production users, no migration scripts needed (CORE_RULES §30). Dev users clear app data once.
- **No data loss** — every dropped column/table is either duplicated, dead, or explicitly migrated. Verified by 5 research sub-agents.
- **CI is the final gate** — sub-agent compile review before push, then CI verifies.

---

## 7. What's Being Skipped / Deferred (clearly called out)

1. **Kotlin class renames** (`ContentRecord` → `MainEntryRecord`, etc.) — deferred. ~24 caller files. Separate session.
2. **`.sq` file rename** (`content.sq` → `main_entry.sq`) — deferred. Changes `database.contentQueries` property. Separate session.
3. **Merge `app_metadata` → `app_settings`** — deferred. Degenerate KV duplication, but low priority. Separate session.
4. **Split `AnimeDetailsProvider` interface** into `DataSourceProvider` + `ExtensionDetailsProvider` — deferred. Code-layer refactor, not schema. Separate session.
5. **`RetentionCoordinator` worker** — deferred. Would centralize the 5 retention queries. Separate session.
6. **`data_cache_episode` → `INSERT ON CONFLICT DO UPDATE`** — deferred. Performance optimization for batch refresh. Not needed at current scale.
7. **CHECK constraints** — included in the plan but optional. Can be added incrementally.
8. **`episode_number` API surface change** (Long→Float/Double in Kotlin callers) — deferred per §4.12 item 10. This plan fixes the SCHEMA type only; the Kotlin API surface still truncates fractional episodes at the call site. Separate task.
9. **CloudStream/Sora/MangaYomi String extension IDs** — deferred. The `extension_id` column is TEXT (DB) but Kotlin types stay `Long?` for Aniyomi compatibility. When a future extension ecosystem uses truly-String IDs (e.g. CloudStream's `"kawaiiyomistreams.com"`), a separate `extension_id_str TEXT` column or a hash-to-Long mapping will be needed. Not a concern for Aniyomi-only today.

---

## 8. Future-Proofing (how this handles the multi-source + multi-extension vision)

### Adding a new data source (e.g. MAL)
1. Implement `MalDataSourceProvider : DataSourceProvider` (code layer — mirrors the D-190 episode metadata engine pattern)
2. User switches data source → `ContentResolver.linkDataSource("mal", malId, ...)` → UPDATEs the `data_source_detail` row: `source_type='mal'`, `source_ref_id= malId`, new metadata fields
3. The `main_id` stays the same. The previous AniList ID is preserved in `extra_json` as `{"previous_anilist_id": 12345}` for re-switching back.
4. **Zero schema change.** (MAL-specific fields like `main_picture.medium`/`.large` go into `extra_json` + `cover_url` per §4.12 item 6.)

### Adding a new extension ecosystem (e.g. CloudStream)
1. Implement `CloudStreamVideoExtensionProvider : VideoExtensionProvider` with `ecosystemId="cloudstream"` (code layer)
2. User switches extension → `ContentResolver.linkExtension("cloudstream", cloudStreamSourceId, animeUrl, ...)` → UPDATEs the `extension_detail` row: `extension_type='cloudstream'`, new `extension_id`/`source_id`/`anime_url`
3. The `main_id` stays the same. Episode list changes (different numbering) — `data_cache_episode` rows for the old extension are invalidated + re-fetched.
4. **Zero schema change for Long-ID extensions.** ⚠️ CloudStream uses String source IDs (e.g. `"kawaiiyomistreams.com"`) — the current `extension_id` column is TEXT but Kotlin types are `Long?`. A future `extension_id_str TEXT` column or hash-to-Long mapping would be needed (deferred per §7 item 9). For Aniyomi-only (Long IDs), zero schema change.

### Independent switching
The split `active_data_source_type` + `active_extension_type` columns on `main_entry` allow the user to switch either independently. Example: AniList metadata + Aniyomi extension-A → MAL metadata + Aniyomi-extension-A → MAL metadata + CloudStream-extension-B. Each combination is a valid state.

---

## 9. Verification Checklist (for the implementation session)

- [ ] All 13 FK declarations renamed `content` → `main_entry`
- [ ] 2 missing FKs added (`watch_progress`, `notification_sent`)
- [ ] `display_source` split into `active_data_source_type` + `active_extension_type`
- [ ] `anilist_detail` migrated to `data_source_detail` (columns + `source_type` + `source_ref_id` + `extra_json`)
- [ ] `extension_detail` extended (`extension_type` + `extra_json` + INTEGER→TEXT type change)
- [ ] `other_source_detail` DROPPED
- [ ] `anime_metadata_cache` DROPPED + 6 callers redirected
- [ ] `episode_number` type fixed in `notification_sent` + `episode_schedule`
- [ ] Dead columns/queries/methods/indexes removed
- [ ] Sub-agent compile review passes
- [ ] CI green
- [ ] Device test: link source, switch source, unlink source — verify no orphaned rows
- [ ] Device test: download episode, verify data.json + downloaded_episode correct
- [ ] Doc update: progress.md, decisions.md (D-197), changelog.md, knowledge/architecture.md, dashboard schema.ts

---

## 10. Research Basis

This plan is grounded in 5 parallel Explore sub-agents (Tasks 2-a through 2-e) that read the actual codebase + decisions + knowledge files. Key findings:
- **Task 2-a**: `content` table is functionally sound; rename is low-risk mechanical.
- **Task 2-b**: The 3 detail tables can be merged; the `unlinkSource` orphan-row bug is fixed by the new `clearExtensionAxis` query.
- **Task 2-c**: `anime_metadata_cache` has 9/12 duplicate columns + 2 dead → absorb is sound. `data_cache_episode` + `browse_cache` stay separate.
- **Task 2-d**: The codebase already separates data source vs extension at the lookup-table level; Option C (two tables) honors this.
- **Task 2-e**: All 5 keep-separate groups confirmed. Real bugs found: `episode_number` type mismatch, 2 missing FKs.

Full research is in `/home/z/my-project/worklog.md` (Tasks 2-a through 2-e).

---

*This is a PROPOSAL. No schema changes will be made until the user approves. The dashboard at `/database-plan/` presents this plan in a scannable format for review.*
