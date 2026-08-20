/*
 * ANI-KUTA Database Restructuring Plan v2 — typed data for the
 * /database-plan/ dashboard page.
 *
 * Source of truth (THE single source of truth):
 *   APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md
 *   (464 lines, 2 Explore sub-agents R-1 + R-2, 4 review iterations).
 *
 * Every table schema, every query, every con, every deferred item below is
 * transcribed from that plan file — no summarisation, no drops. The user is
 * reviewing this page to decide whether to APPROVE the restructuring, so
 * completeness matters more than brevity.
 *
 * Consumed by app/database-plan/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * v2 deltas from v1 (per PLAN.md v2 header note):
 *  - 26 → 22 tables (was 26 → 24 in v1)
 *  - ONE wide `content_details` table (Option A — 26 cols, `data_*` + `ext_*`
 *    prefixes) — NOT two tables (the v1 Option C decision was reversed)
 *  - Drop `app_metadata` (was deferred in v1 — now a core change)
 *  - Keep `data_source` + `system` separate (was implicit in v1 — now an
 *    explicit evaluated + recommended keep-separate change)
 *  - Keep `extension_repo_id` on `main_entry` (was DROPPED in v1)
 *  - Keep `display_source` as a single UX column (was SPLIT into
 *    `active_data_source_type` + `active_extension_type` in v1)
 *  - 10-group presentation (was a flat list in v1)
 *
 * Design follows DESIGN.md (MEMORY OS v3):
 *  - Warm Canvas (#F2EEE8) bg, cards bg #FFFDFA, border #E8E2DA, rounded-2xl
 *  - Indigo primary, Teal success, Amber warning, Rose danger, Violet secondary
 *  - Column status colour coding:
 *      NEW       = Teal    (newly added column)
 *      MODIFIED  = Amber   (type / nullability / constraints / semantics changed)
 *      DROPPED   = Rose    (column removed — struck through)
 *      RENAMED   = Indigo  (column renamed, same data)
 *      UNCHANGED = muted   (carried over as-is)
 *      AXIS      = Violet  (column-group divider — data_* or ext_* axis)
 *  - Risk severity colour coding:
 *      HIGH     = Rose
 *      MEDIUM   = Amber
 *      LOW      = Teal
 *      RESOLVED = Teal ✓ (with explicit "resolved by" note)
 */

/* ---------------------------------------------------------------------------
 * SECTION 1 — HERO / SNAPSHOT
 * ------------------------------------------------------------------------- */

export const PLAN_META = {
  status: "PROPOSAL v2 — NOT YET IMPLEMENTED",
  date: "2026-08-14",
  author:
    "Main agent (researched via 2 Explore sub-agents R-1 + R-2, reviewed via 4 sub-agent iterations)",
  scope:
    "Schema restructuring of the 26-table SQLDelight database → 22 tables. No code changes this phase — this is the plan only.",
  migrationPolicy:
    "Debug builds only — schema can be rebuilt freely per CORE_RULES §30 (drop + recreate, no .sqm migration files needed).",
  changeFromV1:
    "v1 used two tables (Option C). v2 reverses to ONE wide content_details table (Option A) per user directive. Also: keep extension_repo_id, drop app_metadata, keep data_source+system separate, keep display_source as single UX column, 10-group presentation.",
  sourceOfTruth:
    "APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md",
} as const;

export const HERO = {
  title: "Database Restructuring Plan v2",
  kicker: "PROPOSAL v2 — NOT YET IMPLEMENTED",
  description:
    "A full-fledged plan for restructuring the 26-table database → 22 tables via 4 core changes. The headline change: ONE wide content_details table (Option A — 26 cols, data_* + ext_* prefixes) absorbs 4 old tables. Reviewed via 4 sub-agent iterations. Awaiting your approval.",
  badges: [
    { label: "26 → 22 tables", tone: "primary" as const },
    { label: "4 core changes", tone: "secondary" as const },
    { label: "4 review iterations (v2)", tone: "success" as const },
    { label: "Debug-build safe (§30)", tone: "warning" as const },
  ],
  snapshotMetrics: [
    {
      metric: "Tables (before → after)",
      value: "26 → 22",
      note: "5 dropped (anilist_detail, extension_detail, other_source_detail, anime_metadata_cache, app_metadata); 1 NEW (content_details); 1 renamed (content → main_entry). Net −4. Option A — one wide table replaces 4 old ones.",
    },
    {
      metric: "Core changes",
      value: "4",
      note: "Rename content → main_entry · Merge 4 tables → ONE wide content_details (Option A) · Drop app_metadata (absorb into app_settings) · Keep data_source + system separate (R-2 evaluated).",
    },
    {
      metric: "Independent improvements",
      value: "11",
      note: "Bundled — drop description (3 caller migration), fix missing FKs, fix episode_number type (schema + API), drop dead queries/methods/indexes, standardize naming, typed accessors (DataSourceExtras + ExtensionExtras), clearExtensionAxis, updateMainEntryTitle, content_id regeneration, source_ref_id convention, etc.",
    },
    {
      metric: "content_details queries",
      value: "11",
      note: "On the NEW content_details table — getContentDetails, getMainEntryByAniListId (JOIN, hot path), getMainEntryByDataSourceRef (generic), getMainEntryByExtension (denormalized, no JOIN), upsertContentDetails (26 params), updateDataSourceAxis, updateExtensionAxis, clearDataSourceAxis, clearExtensionAxis, deleteContentDetails, getAllContentDetails.",
    },
    {
      metric: "content_details columns",
      value: "26",
      note: "1 PK (main_id) + 13 data-axis (data_* prefix) + 12 extension-axis (ext_* prefix). SQLite supports 2000 columns — non-issue. Two discriminators (data_source_type, extension_type) + two extra_json columns absorb future sources/extensions with zero schema change.",
    },
    {
      metric: "Research basis",
      value: "R-1 + R-2",
      note: "This session: 2 Explore sub-agents (R-1 = content_details design Option A, R-2 = re-evaluate merges + grouping). Prior session: 5 Explore sub-agents (Tasks 2-a through 2-e).",
    },
    {
      metric: "Review iterations",
      value: "4",
      note: "Iteration 1 (1 FLAW + 9 CONCERNS) · Iteration 2A (6 architecture CONCERNS) · Iteration 2B (5 implementation CONCERNS, effort estimate corrected +20-30%) · Iteration 3+4 (12 final checks — APPROVED with minor fixes, ready for dashboard).",
    },
    {
      metric: "Final tables unchanged",
      value: "20",
      note: "20 tables confirmed correctly separated via R-2 research — no merge needed. main_entry gets minor bundled changes (drop description, keep extension_repo_id, keep display_source as UX column).",
    },
    {
      metric: "Effort estimate",
      value: "~750-900 lines · ~32-38 files",
      note: "Per Review v2-2B Check 11. Prior estimate (~600 lines / ~25 files) was under by 20-30%. Includes: 13 read-caller files, 3 write-caller files, 5 wrapper/infra files, 3 description-migration files, 5 episode_number type-change files, 9 .sq files, ~3-4 anilistId Int→Int? propagation files.",
    },
    {
      metric: "Data loss",
      value: "Zero",
      note: "Every dropped column/table is either duplicated, dead (zero callers), or explicitly migrated. Verified by 7 research sub-agents (5 prior session + 2 this session).",
    },
  ],
} as const;

/* ---------------------------------------------------------------------------
 * SECTION 2 — THE 4 CORE CHANGES
 * ------------------------------------------------------------------------- */

export interface CoreChange {
  num: number;
  kind: "RENAME" | "MERGE" | "DROP" | "KEEP-SEPARATE";
  title: string;
  what: string;
  why: string;
  impact: string[];
  accent: "primary" | "success" | "secondary" | "danger" | "warning";
}

export const CORE_CHANGES: CoreChange[] = [
  {
    num: 1,
    kind: "RENAME",
    title: "Rename content → main_entry",
    what: "The identity-hub table is renamed from `content` → `main_entry`. 4 indexes renamed (idx_content_* → idx_main_entry_*). 9 SQLDelight queries renamed (getContentBy* → getMainEntryBy*, insertContent → insertMainEntry, etc.). 1 NEW query added (updateMainEntryTitle). 13 FK declarations across 9 .sq files updated. 1 Kotlin string literal + 1 DbReference updated.",
    why: "The `content` table's real job is the identity hub — it holds the stable `main_id` + the changing `content_id` + links to all per-source detail tables. The name \"content\" is generic + collides with `android.content.ContentResolver` / `android.content.Context` (confusing for new agents). `main_entry` accurately reflects \"the main entry row that all detail rows hang off of.\"",
    impact: [
      "Table name: content → main_entry",
      "4 indexes renamed: idx_content_* → idx_main_entry_* (incl. idx_content_extension_url → idx_main_entry_extension_url)",
      "9 SQLDelight queries renamed (getContentBy* → getMainEntryBy*, insertContent → insertMainEntry, etc.)",
      "1 NEW query: updateMainEntryTitle(mainId, title, updatedAt) — keeps main_entry.title in sync when metadata refresh updates the title",
      "13 FK declarations across 9 .sq files updated: REFERENCES content(main_id) → REFERENCES main_entry(main_id)",
      "1 Kotlin string literal in DatabaseDriverFactory.kt:168 updated",
      "1 DbReference(\"content\", ...) in DetailsScreen.kt:385 → DbReference(\"main_entry\", ...)",
      "Deferred (NOT in this plan): Kotlin class names (ContentRecord → MainEntryRecord, ~24 caller files) + .sq file rename (database.contentQueries property). Separate session.",
    ],
    accent: "primary",
  },
  {
    num: 2,
    kind: "MERGE",
    title: "Merge 4 tables → ONE wide content_details (Option A)",
    what: "`anilist_detail` + `extension_detail` + `other_source_detail` + `anime_metadata_cache` (4 tables) → ONE wide `content_details` table (Option A). 26 columns: 1 PK (main_id) + 13 data-axis columns (data_* prefix) + 12 extension-axis columns (ext_* prefix). Two discriminators (data_source_type, extension_type) + two extra_json columns. 2 indexes. 11 queries (incl. updateDataSourceAxis / updateExtensionAxis for in-place switching, clearDataSourceAxis / clearExtensionAxis for unlink).",
    why: "The user reversed the v1 Option C decision (two tables) → now Option A (one wide table). The user wants ALL metadata (data-source + extension, for all content types + all sources + all extensions) in ONE table. Column prefixes (data_* / ext_*) preserve the fact that AniList + extension metadata can differ (which the user wants to switch between). The two axes are conceptually orthogonal + linked independently. Future-proof: adding a new data source (MAL) or new extension ecosystem (CloudStream) = UPDATE the row with a new discriminator value. Zero schema change.",
    impact: [
      "NEW table: content_details (26 cols, 2 indexes, 11 queries) — Option A per user directive",
      "DROPPED: anilist_detail → merged into content_details data-axis (anilist_id → data_source_ref_id TEXT, synopsis → data_synopsis, id_mal → data_extra_json, others map 1:1)",
      "DROPPED: extension_detail → merged into content_details extension-axis (extension_id → TEXT, description → ext_description, thumbnail_url → ext_thumbnail_url, others 1:1)",
      "DROPPED: other_source_detail → DROPPED (dead code, 0 callers, never written — concept absorbed by data_extra_json)",
      "ABSORBED: anime_metadata_cache → into content_details data-axis (9/12 cols duplicate anilist_detail; 3 unique cols dead: title dups main_entry.title, source_type hardcoded dead, fetched_at write-only dead)",
      "Type change: extension_id + source_ref_id stored as TEXT (uniformity). Kotlin stays Long? for Aniyomi compat — Repository-layer conversion via .toString() / .toLongOrNull() (no ColumnAdapter).",
      "Nullable fields: data_source_type, data_source_ref_id, extension_type, extension_id, source_id, anime_url all nullable — enables clearDataSourceAxis / clearExtensionAxis",
      "11 new queries: getContentDetails, getMainEntryByAniListId, getMainEntryByDataSourceRef, getMainEntryByExtension (denormalized, no JOIN), upsertContentDetails (26 params), updateDataSourceAxis, updateExtensionAxis, clearDataSourceAxis, clearExtensionAxis, deleteContentDetails, getAllContentDetails",
    ],
    accent: "success",
  },
  {
    num: 3,
    kind: "DROP",
    title: "Drop app_metadata (absorbed into app_settings)",
    what: "DROP the `app_metadata` table entirely. It's dead code — 0 Kotlin callers (grep confirmed). Its 2-column schema (key, value) is a strict subset of `app_settings`' 5-column schema. The prior plan (v1) deferred this; R-2 research confirmed it's safe to do now.",
    why: "`app_metadata` is dead code — 0 Kotlin callers. Its 2-column schema (key, value) is a strict subset of `app_settings`' 5-column schema (key, value, type, category, updated_at). The prior plan deferred this; R-2 research confirmed it's safe to do now. Any planned-but-never-wired use cases (schema version tracking) go into `app_settings` with `setting_category='internal'`. Backup filter: `WHERE setting_category != 'internal'` so internal flags don't pollute backups.",
    impact: [
      "DROP the app_metadata table + its 2 queries (setMetadata, getMetadata)",
      "Any planned-but-never-wired use cases (schema version tracking) go into app_settings with setting_category='internal'",
      "Backup filter: WHERE setting_category != 'internal' (so internal flags don't pollute backups)",
      "No data loss — the table was empty (0 rows, 0 callers)",
    ],
    accent: "danger",
  },
  {
    num: 4,
    kind: "KEEP-SEPARATE",
    title: "Keep data_source + system separate (per R-2 recommendation)",
    what: "The user asked about merging `data_source` + `system` (two lookup tables). R-2 research evaluated + recommended **keep separate**. The 4 reasons: (a) different column shapes — `data_source` has `type`, `system` has `package_prefix`; (b) FK integrity — merging into one `lookup` table would weaken FK integrity (can't enforce 'data_source_id points to a data_source row' at the DB level); (c) conceptual separation is real — `data_source` = metadata providers (AniList/TMDB/Kitsu/MAL), `system` = extension ecosystems (Aniyomi/CloudStream/Sora/MangaYomi); (d) only saves 1 table — bad trade.",
    why: "The user said \"if keeping them separate is the best approach then we can go with that.\" R-2 confirms it is. Merging would create technical debt, not reduce it. The conceptual separation is real (metadata providers vs extension ecosystems), the column shapes differ, and FK integrity is a hard DB-level guarantee we'd lose. Only saves 1 table — bad trade.",
    impact: [
      "NO change — both data_source and system tables stay UNCHANGED (in Group 1 — Identity & Sources)",
      "FK integrity preserved: main_entry.data_source_id → data_source(id) + main_entry.system_id → system(id) remain independent",
      "Revisit if the FK integrity concern can be solved cleanly (deferred per §7 item 9)",
    ],
    accent: "warning",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 3 — NEW TABLE SCHEMAS (every column)
 * ------------------------------------------------------------------------- */

/** Column status — drives the colour coding in the rendered schema table. */
export type ColumnStatus =
  | "new"
  | "modified"
  | "dropped"
  | "renamed"
  | "unchanged"
  | "axis";

export interface ColumnSpec {
  name: string;
  type: string;
  constraints: string;
  description: string;
  status: ColumnStatus;
}

/** Colour legend shared by all schema tables. */
export const COLUMN_STATUS_META: Record<
  ColumnStatus,
  { label: string; colorVar: string; symbol: string }
> = {
  new: {
    label: "NEW",
    colorVar: "var(--c-success)",
    symbol: "+",
  },
  modified: {
    label: "MODIFIED",
    colorVar: "var(--c-warning)",
    symbol: "△",
  },
  dropped: {
    label: "DROPPED",
    colorVar: "var(--c-danger)",
    symbol: "×",
  },
  renamed: {
    label: "RENAMED",
    colorVar: "var(--c-primary)",
    symbol: "→",
  },
  unchanged: {
    label: "UNCHANGED",
    colorVar: "var(--c-text-secondary)",
    symbol: "·",
  },
  axis: {
    label: "AXIS DIVIDER",
    colorVar: "var(--c-secondary)",
    symbol: "─",
  },
};

/** Change 1 — main_entry (renamed from content). */
export const MAIN_ENTRY_SCHEMA: {
  tableName: string;
  renameFrom: string;
  sqFile: string;
  purpose: string;
  columns: ColumnSpec[];
  indexes: { name: string; status: ColumnStatus; def: string }[];
  queries: string[];
} = {
  tableName: "main_entry",
  renameFrom: "content",
  sqFile: "content.sq",
  purpose:
    "Identity hub — holds the stable main_id (UUID, never changes) + the changing content_id (regenerated on every source-switch) + lookups to the active data source / extension. One row per piece of content (anime, manga, novel). v2 keeps extension_repo_id + keeps display_source as a single UX-preference column (NOT split — the unified content_details table makes splitting unnecessary).",
  columns: [
    {
      name: "main_id",
      type: "TEXT",
      constraints: "NOT NULL PRIMARY KEY",
      description:
        "Stable UUID assigned once on first sighting; survives all source switches. All child tables FK to this with ON DELETE CASCADE.",
      status: "unchanged",
    },
    {
      name: "content_id",
      type: "TEXT",
      constraints: "NOT NULL",
      description:
        "Structured string (regenerated by ContentIdGenerator on every link/unlink/switch of source — preserves the 'content_id changes when sources switch' invariant).",
      status: "unchanged",
    },
    {
      name: "title",
      type: "TEXT",
      constraints: "NOT NULL",
      description:
        "Display title. Set at first creation from anime.displayName. ALSO updated by the NEW updateMainEntryTitle query when the metadata refresh flow updates the title (was previously only updating the now-dropped anime_metadata_cache.title).",
      status: "modified",
    },
    {
      name: "content_type",
      type: "TEXT",
      constraints: "NOT NULL DEFAULT 'anime'",
      description: "anime | manga | novel (future).",
      status: "unchanged",
    },
    {
      name: "content_format",
      type: "TEXT",
      constraints: "NOT NULL DEFAULT 'video'",
      description: "video | image | text | audio (future).",
      status: "unchanged",
    },
    {
      name: "description",
      type: "TEXT",
      constraints: "(removed)",
      description:
        "DROPPED — has 3 fallback-reader caller sites (MainActivity.kt:671/800, DownloadScanner.kt:276, DownloadStorageProvider.kt:265/281). Migration specified in §4.1: callers must read content_details.data_synopsis (or ext_description as fallback) instead. The column is never WRITTEN non-null today (ContentRepository.insertContent always passes description = null), so dropping it loses no data.",
      status: "dropped",
    },
    {
      name: "data_source_id",
      type: "INTEGER",
      constraints: "nullable, FK→data_source(id) ON DELETE SET NULL",
      description: "Which metadata source is linked (AniList/TMDB/Kitsu/MAL lookup).",
      status: "unchanged",
    },
    {
      name: "system_id",
      type: "INTEGER",
      constraints: "nullable, FK→system(id) ON DELETE SET NULL",
      description: "Which extension system is linked (Aniyomi/CloudStream/Sora/MangaYomi lookup).",
      status: "unchanged",
    },
    {
      name: "extension_repo_id",
      type: "INTEGER",
      constraints: "nullable",
      description:
        "v2 KEEP — per user directive. May be wired up later (possibly renamed to a number). v1 had this as DROPPED — the user reversed that decision.",
      status: "unchanged",
    },
    {
      name: "extension_id",
      type: "INTEGER",
      constraints: "nullable",
      description:
        "Aniyomi INTERNAL source.id (NOT a FK — D-189). Plain nullable INTEGER. KEPT on main_entry as a DENORMALIZED copy of content_details.extension_id for the hot getMainEntryByExtension lookup (no JOIN needed). Aniyomi uses Long IDs — stored as INTEGER here, TEXT on content_details for uniformity.",
      status: "unchanged",
    },
    {
      name: "source_id",
      type: "INTEGER",
      constraints: "nullable",
      description:
        "Same as extension_id (legacy dup, kept for compatibility). Also denormalized for the hot lookup.",
      status: "unchanged",
    },
    {
      name: "anime_url",
      type: "TEXT",
      constraints: "nullable",
      description:
        "The content's URL on the source. KEPT on main_entry as a DENORMALIZED copy of content_details.anime_url for the hot getMainEntryByExtension lookup (no JOIN needed).",
      status: "unchanged",
    },
    {
      name: "display_source",
      type: "TEXT",
      constraints: "nullable",
      description:
        "v2 KEEP as single UX-preference column. Value semantics CHANGED (per Review v2-2A Check 4): stores the AXIS preference — values 'data_source' | 'extension' (was source-name-level 'anilist' | 'extension'). Migration: existing 'anilist' values → 'data_source' on schema rebuild. NOT split into active_data_source_type + active_extension_type (that was v1's plan — no longer needed with the unified content_details table; link state is now implicit in content_details discriminators). The value is write-only metadata today (grep'd displaySource == — ZERO branch sites found).",
      status: "modified",
    },
    {
      name: "created_at",
      type: "INTEGER",
      constraints: "NOT NULL",
      description: "Row creation timestamp (epoch millis).",
      status: "unchanged",
    },
    {
      name: "updated_at",
      type: "INTEGER",
      constraints: "NOT NULL",
      description: "Last-write timestamp (epoch millis).",
      status: "unchanged",
    },
  ],
  indexes: [
    {
      name: "idx_main_entry_content_id",
      status: "renamed",
      def: "ON main_entry(content_id) — was idx_content_content_id",
    },
    {
      name: "idx_main_entry_extension",
      status: "dropped",
      def: "DROPPED — single-column on main_entry(extension_id), redundant with composite idx_main_entry_extension_url (leftmost column covered). Per §4.6.",
    },
    {
      name: "idx_main_entry_extension_url",
      status: "renamed",
      def: "ON main_entry(extension_id, anime_url) — was idx_content_extension_url (composite, for getMainEntryByExtension hot path — uses denormalized cols on main_entry, NO JOIN)",
    },
    {
      name: "idx_content_data_source",
      status: "dropped",
      def: "DROPPED — redundant (no query filters on data_source_id alone; see §4.6)",
    },
  ],
  queries: [
    "getMainEntryByMainId(mainId) — RENAMED from getContentByMainId",
    "getMainEntryByAniListId(anilistId) — RENAMED from getContentByAniListId; now JOINs to content_details (data-axis) for reverse lookup",
    "getMainEntryByExtension(extensionId, animeUrl) — RENAMED from getContentByExtension; uses denormalized main_entry cols (no JOIN)",
    "getMainEntryByContentId(contentId) — RENAMED from getContentByContentId",
    "insertMainEntry(...) — RENAMED from insertContent; column list updated (drops description)",
    "updateMainEntryContentId(mainId, contentId, updatedAt) — RENAMED from updateContentContentId",
    "updateMainEntrySources(...) — RENAMED from updateContentSources; column list updated (drops description)",
    "deleteMainEntry(mainId) — RENAMED from deleteContent",
    "updateContentDisplaySource(mainId, displaySource, updatedAt) — UNCHANGED signature, value semantics change ('data_source' | 'extension' instead of 'anilist' | 'extension')",
    "updateMainEntryTitle(mainId, title, updatedAt) — NEW — keeps title in sync when metadata refresh updates the title",
  ],
};

/** Change 2 — content_details (NEW — THE CENTERPIECE — merges 4 tables). */
export const CONTENT_DETAILS_SCHEMA: {
  tableName: string;
  replaces: string[];
  sqFile: string;
  purpose: string;
  columns: ColumnSpec[];
  indexes: { name: string; status: ColumnStatus; def: string }[];
  queries: string[];
} = {
  tableName: "content_details",
  replaces: ["anilist_detail", "extension_detail", "other_source_detail", "anime_metadata_cache"],
  sqFile: "content.sq",
  purpose:
    "THE CENTERPIECE of v2 — ONE wide table (Option A per user directive) that holds metadata from ANY data source (AniList now, Kitsu/MAL/TMDB later) AND ANY extension (Aniyomi now, CloudStream/Sora/MangaYomi later). Two orthogonal axes (data-source + extension) coexist in one row, distinguished by column prefixes (data_* / ext_*) + discriminator columns (data_source_type / extension_type). Both axes can be switched INDEPENDENTLY via updateDataSourceAxis / updateExtensionAxis. Both axes can be unlinked independently via clearDataSourceAxis / clearExtensionAxis. Future-proof: adding a new source/extension/content-type = UPDATE the row with new discriminator/extras. Zero schema change. Column count: 26 (1 PK + 13 data-axis + 12 ext-axis). SQLite supports 2000 — non-issue.",
  columns: [
    {
      name: "main_id",
      type: "TEXT",
      constraints: "NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE",
      description: "Stable identity link (1:1 with main_entry).",
      status: "unchanged",
    },
    // ─── Data-source (metadata) axis ───
    {
      name: "── Data-source (metadata) axis ──",
      type: "",
      constraints: "",
      description:
        "13 columns prefixed data_*. Hold metadata from AniList (now) or future Kitsu/MAL/TMDB sources. All nullable so clearDataSourceAxis can NULL the entire axis on unlink.",
      status: "axis",
    },
    {
      name: "data_source_type",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Discriminator: 'anilist' | 'kitsu' | 'mal' | 'tmdb'. NULL = no data source linked. Nullable per Review v2-1 — allows clearDataSourceAxis to NULL the field on unlink.",
      status: "new",
    },
    {
      name: "data_source_ref_id",
      type: "TEXT",
      constraints: "nullable",
      description:
        "External ID as TEXT (anilist_id, mal_id, kitsu_id, tmdb_id). TEXT for uniformity across source-ID types. NULL when no data source linked. Migration: anilist_detail.anilist_id (INTEGER) → data_source_ref_id (TEXT). Repository-layer Long↔TEXT conversion: .toString() on write, .toLongOrNull() on read (no ColumnAdapter needed).",
      status: "new",
    },
    {
      name: "data_score",
      type: "INTEGER",
      constraints: "nullable",
      description: "Average score 0-100. Migrated from anilist_detail.score.",
      status: "new",
    },
    {
      name: "data_episodes",
      type: "INTEGER",
      constraints: "nullable",
      description: "Total episode count. Migrated from anilist_detail.episodes.",
      status: "new",
    },
    {
      name: "data_season",
      type: "TEXT",
      constraints: "nullable",
      description: "'WINTER' | 'SPRING' | 'SUMMER' | 'FALL'. Migrated from anilist_detail.season.",
      status: "new",
    },
    {
      name: "data_season_year",
      type: "INTEGER",
      constraints: "nullable",
      description: "Year of season airing. Migrated from anilist_detail.season_year.",
      status: "new",
    },
    {
      name: "data_status",
      type: "TEXT",
      constraints: "nullable",
      description: "'FINISHED' | 'RELEASING' | 'CANCELLED' | 'HIATUS'. Migrated from anilist_detail.status.",
      status: "new",
    },
    {
      name: "data_genres",
      type: "TEXT",
      constraints: "nullable",
      description: "Comma-separated curated genres. Migrated from anilist_detail.genres.",
      status: "new",
    },
    {
      name: "data_synopsis",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Long-form editorial synopsis. Renamed from anilist_detail.synopsis. ALSO absorbs the 3 fallback-reader callers of the dropped main_entry.description column (§4.1).",
      status: "renamed",
    },
    {
      name: "data_cover_url",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Data-source CDN cover image. Migrated from anilist_detail.cover_url. Primary cover (medium for MAL, large for AniList per future §8).",
      status: "new",
    },
    {
      name: "data_banner_url",
      type: "TEXT",
      constraints: "nullable",
      description: "Data-source CDN wide banner. Migrated from anilist_detail.banner_url.",
      status: "new",
    },
    {
      name: "data_extra_json",
      type: "TEXT",
      constraints: "nullable",
      description:
        'JSON: {"id_mal":12345,"trailer_url":"...","age_rating":"PG-13","studio":"WIT"}. Source-specific extras. Parsed via typed DataSourceExtras accessor (§4.7) with ignoreUnknownKeys=true (so adding any new field doesn\'t break parsing of existing rows). Absorbs anilist_detail.id_mal + the dead other_source_detail table\'s concept.',
      status: "new",
    },
    {
      name: "data_updated_at",
      type: "INTEGER",
      constraints: "nullable",
      description: "When the data-source axis was last refreshed. Nullable so clearDataSourceAxis can NULL it.",
      status: "new",
    },
    // ─── Extension (episode source) axis ───
    {
      name: "── Extension (episode source) axis ──",
      type: "",
      constraints: "",
      description:
        "12 columns prefixed ext_*. Hold metadata from Aniyomi (now) or future CloudStream/Sora/MangaYomi ecosystems. All nullable so clearExtensionAxis can NULL the entire axis on unlink.",
      status: "axis",
    },
    {
      name: "extension_type",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Discriminator: 'aniyomi' | 'cloudstream' | 'sora' | 'mangayomi'. NULL = no extension linked. Nullable per Review v2-1 — allows clearExtensionAxis to NULL the field on unlink.",
      status: "new",
    },
    {
      name: "extension_id",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Extension source ID as TEXT (Aniyomi Long stringified; future CloudStream String). TYPE CHANGE from extension_detail.extension_id (was INTEGER). Kotlin types stay Long? via Repository-layer conversion (.toString() on write, .toLongOrNull() on read). Aniyomi uses Long IDs — stored as TEXT for uniformity with future String-ID extensions.",
      status: "modified",
    },
    {
      name: "source_id",
      type: "INTEGER",
      constraints: "nullable",
      description:
        "Aniyomi internal source.id (kept as INTEGER for back-compat with .data.json serialization). NULL for future extensions (CloudStream/Sora/MangaYomi may not have a parallel concept). Kept INTEGER (not TEXT) because .data.json serializes it as Long — avoids breaking backup-compat.",
      status: "modified",
    },
    {
      name: "anime_url",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Extension's content URL. NULLABLE — NULL when no extension linked (was NOT NULL in extension_detail). Nullable so clearExtensionAxis can NULL it on unlink. NOTE: main_entry also keeps a denormalized copy of anime_url for the hot getMainEntryByExtension lookup (no JOIN).",
      status: "modified",
    },
    {
      name: "ext_description",
      type: "TEXT",
      constraints: "nullable",
      description:
        "Source site's short description. Renamed from extension_detail.description (renamed to ext_description to avoid clash with the dropped main_entry.description column).",
      status: "renamed",
    },
    {
      name: "ext_genres",
      type: "TEXT",
      constraints: "nullable",
      description: "Source site's raw genres. Migrated from extension_detail.genres.",
      status: "new",
    },
    {
      name: "ext_status",
      type: "TEXT",
      constraints: "nullable",
      description: "Source site's free-text status. Migrated from extension_detail.status.",
      status: "new",
    },
    {
      name: "ext_author",
      type: "TEXT",
      constraints: "nullable",
      description: "Manga/novel author (NULL for video). Migrated from extension_detail.author.",
      status: "new",
    },
    {
      name: "ext_artist",
      type: "TEXT",
      constraints: "nullable",
      description: "Manga artist (NULL for video + novels). Migrated from extension_detail.artist.",
      status: "new",
    },
    {
      name: "ext_thumbnail_url",
      type: "TEXT",
      constraints: "nullable",
      description: "Source site's thumbnail image URL. Migrated from extension_detail.thumbnail_url.",
      status: "renamed",
    },
    {
      name: "ext_extra_json",
      type: "TEXT",
      constraints: "nullable",
      description:
        'JSON: {"scanlator_group":"...","chapter_count":42,"volume_count":8}. Extension-specific extras. Parsed via typed ExtensionExtras accessor (§4.7) with ignoreUnknownKeys=true.',
      status: "new",
    },
    {
      name: "ext_updated_at",
      type: "INTEGER",
      constraints: "nullable",
      description: "When the extension axis was last refreshed. Nullable so clearExtensionAxis can NULL it.",
      status: "new",
    },
  ],
  indexes: [
    {
      name: "idx_content_details_data_ref",
      status: "new",
      def: "Partial index WHERE data_source_type = 'anilist' ON data_source_ref_id — replaces idx_anilist_detail_anilist_id (hot path for getMainEntryByAniListId reverse lookup)",
    },
    {
      name: "idx_content_details_data_source_ref",
      status: "new",
      def: "Composite ON (data_source_type, data_source_ref_id) — generic, for future Kitsu/MAL/TMDB reverse lookups via getMainEntryByDataSourceRef",
    },
  ],
  queries: [
    "getContentDetails(mainId) — single-row read (SELECT * WHERE main_id = :mainId)",
    "getMainEntryByAniListId(anilistId) — JOIN to main_entry for reverse lookup by AniList ID (hot path — uses idx_content_details_data_ref partial index)",
    "getMainEntryByDataSourceRef(sourceType, sourceRefId) — JOIN for generic reverse lookup (for future Kitsu/MAL/TMDB — uses idx_content_details_data_source_ref composite index)",
    "getMainEntryByExtension(extensionId, animeUrl) — uses DENORMALIZED main_entry.extension_id + main_entry.anime_url (NO JOIN — hot path, kept on main_entry for performance). The index for this query is idx_main_entry_extension_url on main_entry (NOT on content_details).",
    "upsertContentDetails(...) — full-row INSERT OR REPLACE (26 params)",
    "updateDataSourceAxis(...) — partial UPDATE of all 13 data-source fields (for switching data source — ext_* untouched). SINGLE ATOMIC UPDATE, wrapped in DB transaction that ALSO regenerates main_entry.content_id (per §4.10). display_source NOT touched on switch (axis preference stays). Called FROM ContentResolver.linkDataSource, NOT directly from ViewModels.",
    "updateExtensionAxis(...) — partial UPDATE of all 12 extension fields (for switching extension — data_* untouched). SINGLE ATOMIC UPDATE, wrapped in DB transaction that ALSO regenerates main_entry.content_id. Called FROM ContentResolver.linkExtension, NOT directly from ViewModels.",
    "clearDataSourceAxis(mainId) — NULL all 13 data-source fields (for unlink — fixes orphan-row bug). Wrapped in DB transaction with main_entry.content_id UPDATE + main_entry.display_source UPDATE (if the unlinked axis was the preferred display).",
    "clearExtensionAxis(mainId) — NULL all 12 extension fields (for unlink — NEW, fixes the orphan-row bug). The OLD DetailsViewModel.unlinkSource() didn't touch the DB — left orphaned extension_detail rows. clearExtensionAxis keeps the row for re-linking, only marks 'no extension currently active.' Wrapped in DB transaction with main_entry.content_id UPDATE + main_entry.display_source UPDATE.",
    "deleteContentDetails(mainId) — hard delete (used by ContentResolver.unlinkAniList for the heavier operation)",
    "getAllContentDetails() — for backup dump",
  ],
};

/** The 4 dropped tables — what they were + where their data goes. */
export interface DroppedTableSpec {
  table: string;
  sqFile: string;
  status: "DROPPED" | "ABSORBED";
  whatItWas: string;
  whereDataGoes: string;
  callers: string;
}

export const DROPPED_TABLES: DroppedTableSpec[] = [
  {
    table: "anilist_detail",
    sqFile: "content.sq",
    status: "DROPPED",
    whatItWas:
      "Per-main_entry metadata for the AniList data source (1:1 with main_entry). 13 columns: anilist_id (INTEGER PK + FK→content), score, episodes, season, season_year, status, genres, synopsis, cover_url, banner_url, id_mal, updated_at.",
    whereDataGoes:
      "Merged into content_details data-axis (data_* prefix). anilist_id → data_source_ref_id (TEXT), synopsis → data_synopsis, id_mal → data_extra_json (as {\"id_mal\":12345}), others map 1:1 (score→data_score, episodes→data_episodes, etc.). The getAniListDetail query is replaced by getContentDetails + the dataSourceType == 'anilist' check.",
    callers:
      "~13 read-caller files (ProfileViewModel, MainActivity, NotificationsLibraryViewModel, ScheduleViewModel, UpdatesViewModel, HistoryViewModel, LibraryViewModel, DetailsViewModel, BrowseViewModel, DownloadScanner, UpdateEngine, ScheduleEngine, GenreRepository) + 3 write-caller files (DownloadScanner, ContentResolver, DetailsViewModel). The getAniListDetail != null semantics change: now means 'any data source linked' — callers must check dataSourceType == 'anilist' (per Review v2-2A).",
  },
  {
    table: "extension_detail",
    sqFile: "content.sq",
    status: "DROPPED",
    whatItWas:
      "Per-main_entry metadata for the Aniyomi extension (1:1 with main_entry). 14 columns: main_id (PK + FK→content), extension_id (INTEGER), source_id (INTEGER NOT NULL), anime_url (TEXT NOT NULL), title, description, genres, status, author, artist, thumbnail_url, updated_at.",
    whereDataGoes:
      "Merged into content_details extension-axis (ext_* prefix). extension_id → TEXT (was INTEGER), description → ext_description (renamed to avoid clash with dropped main_entry.description), thumbnail_url → ext_thumbnail_url, others map 1:1. Type changes: extension_id INTEGER→TEXT, source_id INTEGER NOT NULL→INTEGER nullable, anime_url TEXT NOT NULL→TEXT nullable (per Review v2-1, enables clearExtensionAxis).",
    callers:
      "Same ~13 read-caller files + 3 write-caller files as anilist_detail (the two tables are read/written together via the ContentResolver wrapper). The clearExtensionAxis query (NEW) fixes the orphan-row bug in the current DetailsViewModel.unlinkSource() flow (line 1693) — that flow only cleared SharedPreferences + left the extension_detail row orphaned.",
  },
  {
    table: "other_source_detail",
    sqFile: "content.sq",
    status: "DROPPED",
    whatItWas:
      "A generic KV table designed for 'future TMDB/Kitsu/MAL data sources' that was NEVER wired up. 0 callers, never written, empty table.",
    whereDataGoes:
      "Concept absorbed by content_details.data_extra_json — source-specific extras go in the JSON column (e.g. {\"age_rating\":\"TV-14\"} for Kitsu). The data_source_type discriminator + data_source_ref_id column handle the 'multiple data sources' use case that other_source_detail was supposed to address. Zero data loss — the table was empty.",
    callers: "Zero — dead code (grep confirmed).",
  },
  {
    table: "anime_metadata_cache",
    sqFile: "dataCache.sq",
    status: "ABSORBED",
    whatItWas:
      "A 12-column cache of anime metadata fetched from AniList. 9 of 12 columns duplicate anilist_detail (score, episodes, season, season_year, status, genres, synopsis, cover_url, banner_url). The 3 unique columns are all dead: title (duplicates main_entry.title), source_type (hardcoded 'anilist', never read), fetched_at (write-only with no refresh-logic reader).",
    whereDataGoes:
      "Absorbed into content_details data-axis (the 9 duplicate columns are already there via the anilist_detail merge). title is already in main_entry.title (kept in sync by the NEW updateMainEntryTitle query). source_type + fetched_at are dead — dropped. The CachedAnimeMetadata data class + 3 DataCacheRepository methods (getAnimeMetadata, upsertAnimeMetadata, deleteAnimeMetadata) are dropped.",
    callers:
      "6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) need redirecting to read from content_details (data-axis) instead. Risk: MEDIUM — write-path convergence (must redirect both reads AND writes to avoid partial staleness). Mitigation: 2-step approach — redirect reads first, then writes; verify with device test.",
  },
  {
    table: "app_metadata",
    sqFile: "app.sq",
    status: "DROPPED",
    whatItWas:
      "A 2-column generic KV table (key, value) for app-level metadata like schema version tracking. Never wired up — 0 Kotlin callers (grep confirmed). Empty table.",
    whereDataGoes:
      "Concept absorbed into app_settings (5-column schema: key, value, type, category, updated_at — strict superset of app_metadata's schema). Any planned-but-never-wired use cases (schema version tracking) go into app_settings with setting_category='internal'. Backup filter: WHERE setting_category != 'internal' so internal flags don't pollute backups.",
    callers: "Zero — dead code (grep confirmed). Empty table (0 rows).",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 4 — QUERIES (new / changed / renamed)
 * ------------------------------------------------------------------------- */

export type QueryStatus = "new" | "renamed" | "unchanged" | "repurposed" | "modified" | "dropped";

export interface QuerySpec {
  name: string;
  signature: string;
  status: QueryStatus;
  description: string;
}

export const QUERY_STATUS_META: Record<
  QueryStatus,
  { label: string; colorVar: string; symbol: string }
> = {
  new: { label: "NEW", colorVar: "var(--c-success)", symbol: "+" },
  renamed: { label: "RENAMED", colorVar: "var(--c-primary)", symbol: "→" },
  modified: { label: "MODIFIED", colorVar: "var(--c-warning)", symbol: "△" },
  unchanged: { label: "UNCHANGED", colorVar: "var(--c-text-secondary)", symbol: "·" },
  repurposed: { label: "REPURPOSED", colorVar: "var(--c-secondary)", symbol: "↻" },
  dropped: { label: "DROPPED", colorVar: "var(--c-danger)", symbol: "×" },
};

export const QUERIES: {
  group: string;
  subtitle: string;
  queries: QuerySpec[];
}[] = [
  {
    group: "main_entry (renamed from content)",
    subtitle:
      "9 existing queries renamed (getContentBy* → getMainEntryBy*) + 1 NEW query for title sync + 1 unchanged query with changed value semantics.",
    queries: [
      {
        name: "getMainEntryByMainId",
        signature: "(mainId)",
        status: "renamed",
        description: "Was getContentByMainId — SELECT * WHERE main_id = :mainId.",
      },
      {
        name: "getMainEntryByAniListId",
        signature: "(anilistId)",
        status: "renamed",
        description:
          "Was getContentByAniListId — JOIN to content_details (data-axis) for reverse lookup. Uses the idx_content_details_data_ref partial index (WHERE data_source_type='anilist'). Hot path.",
      },
      {
        name: "getMainEntryByExtension",
        signature: "(extensionId, animeUrl)",
        status: "renamed",
        description:
          "Was getContentByExtension — WHERE extension_id = :extensionId AND anime_url = :animeUrl on main_entry (NO JOIN — uses denormalized cols + idx_main_entry_extension_url composite index). Hot path.",
      },
      {
        name: "getMainEntryByContentId",
        signature: "(contentId)",
        status: "renamed",
        description: "Was getContentByContentId — SELECT * WHERE content_id = :contentId.",
      },
      {
        name: "insertMainEntry",
        signature: "(...)",
        status: "renamed",
        description:
          "Was insertContent — INSERT OR REPLACE (full row). Column list updated: drops description. Keeps extension_repo_id + display_source per v2.",
      },
      {
        name: "updateMainEntryContentId",
        signature: "(mainId, contentId, updatedAt)",
        status: "renamed",
        description:
          "Was updateContentContentId — UPDATE content_id WHERE main_id. Called on EVERY source-switch (ContentIdGenerator.generate()).",
      },
      {
        name: "updateMainEntrySources",
        signature: "(mainId, dataSourceId, systemId, extensionId, sourceId, animeUrl, contentId, updatedAt)",
        status: "renamed",
        description:
          "Was updateContentSources — UPDATE all source-link fields. Column list updated: drops description. Keeps extension_repo_id.",
      },
      {
        name: "deleteMainEntry",
        signature: "(mainId)",
        status: "renamed",
        description: "Was deleteContent — DELETE WHERE main_id. Cascades to all child tables (incl. content_details via FK).",
      },
      {
        name: "updateContentDisplaySource",
        signature: "(mainId, displaySource, updatedAt)",
        status: "modified",
        description:
          "Signature UNCHANGED. Value semantics CHANGED (per Review v2-2A Check 4): displaySource param now takes 'data_source' | 'extension' (axis-level) instead of 'anilist' | 'extension' (source-name-level). Migration: existing 'anilist' values → 'data_source' on schema rebuild. NOT split into active_*_type columns (that was v1 — no longer needed with unified content_details).",
      },
      {
        name: "updateMainEntryTitle",
        signature: "(mainId, title, updatedAt)",
        status: "new",
        description:
          "NEW — Keeps main_entry.title in sync when the anime metadata refresh flow updates the title. Was previously only updating the now-dropped anime_metadata_cache.title, causing stale-title-after-refresh bug.",
      },
    ],
  },
  {
    group: "content_details (NEW table — the centerpiece)",
    subtitle:
      "11 NEW queries — 4 reads (1 single-row + 3 reverse lookups) + 1 full-row upsert + 2 partial axis-UPDATEs (for in-place switching) + 2 axis-NULL queries (for unlink) + 1 hard delete + 1 backup-dump read.",
    queries: [
      {
        name: "getContentDetails",
        signature: "(mainId)",
        status: "new",
        description:
          "SELECT * WHERE main_id = :mainId. Single-row read. Returns all 26 columns. The typed ContentDetails data class exposes dataExtras: DataSourceExtras + extExtras: ExtensionExtras (parsed once on read).",
      },
      {
        name: "getMainEntryByAniListId",
        signature: "(anilistId)",
        status: "new",
        description:
          "JOIN to main_entry for reverse lookup by AniList ID. HOT PATH — uses idx_content_details_data_ref partial index (WHERE data_source_type='anilist' ON data_source_ref_id). Replaces getContentByAniListId's reverse-lookup path.",
      },
      {
        name: "getMainEntryByDataSourceRef",
        signature: "(sourceType, sourceRefId)",
        status: "new",
        description:
          "JOIN to main_entry for generic reverse lookup. For future Kitsu/MAL/TMDB — uses idx_content_details_data_source_ref composite index on (data_source_type, data_source_ref_id).",
      },
      {
        name: "getMainEntryByExtension",
        signature: "(extensionId, animeUrl)",
        status: "new",
        description:
          "Uses DENORMALIZED main_entry.extension_id + main_entry.anime_url (NO JOIN — hot path, kept on main_entry for performance). The index for this query is idx_main_entry_extension_url on main_entry. NOTE: this query is also listed under main_entry (renamed) — it lives on main_entry, not content_details. Listed here for completeness.",
      },
      {
        name: "upsertContentDetails",
        signature: "(...)",
        status: "new",
        description:
          "Full-row INSERT OR REPLACE (26 params). Replaces upsertAniListDetail + upsertExtensionDetail. Called from ContentResolver on initial link.",
      },
      {
        name: "updateDataSourceAxis",
        signature: "(mainId, dataSourceType, dataSourceRefId, dataScore, dataEpisodes, dataSeason, dataSeasonYear, dataStatus, dataGenres, dataSynopsis, dataCoverUrl, dataBannerUrl, dataExtraJson, dataUpdatedAt)",
        status: "new",
        description:
          "Partial UPDATE of all 13 data-source fields (for in-place switching — ext_* untouched). SINGLE ATOMIC UPDATE statement, wrapped in DB transaction that ALSO regenerates main_entry.content_id (per §4.10). display_source NOT touched on switch (axis preference stays). Called FROM ContentResolver.linkDataSource, NOT directly from ViewModels.",
      },
      {
        name: "updateExtensionAxis",
        signature: "(mainId, extensionType, extensionId, sourceId, animeUrl, extDescription, extGenres, extStatus, extAuthor, extArtist, extThumbnailUrl, extExtraJson, extUpdatedAt)",
        status: "new",
        description:
          "Partial UPDATE of all 12 extension fields (for in-place switching — data_* untouched). SINGLE ATOMIC UPDATE statement, wrapped in DB transaction that ALSO regenerates main_entry.content_id. Called FROM ContentResolver.linkExtension, NOT directly from ViewModels.",
      },
      {
        name: "clearDataSourceAxis",
        signature: "(mainId)",
        status: "new",
        description:
          "NULL all 13 data-source fields (for unlink). The is-linked state is determined by data_source_type IS NOT NULL on content_details. Wrapped in DB transaction with main_entry.content_id UPDATE + main_entry.display_source UPDATE (if the unlinked axis was the preferred display).",
      },
      {
        name: "clearExtensionAxis",
        signature: "(mainId)",
        status: "new",
        description:
          "NULL all 12 extension fields (for unlink — NEW, FIXES THE ORPHAN-ROW BUG). The OLD DetailsViewModel.unlinkSource() (line 1693) only cleared SharedPreferences + left the extension_detail row orphaned. clearExtensionAxis keeps the row for re-linking, only marks 'no extension currently active.' Wrapped in DB transaction with main_entry.content_id UPDATE + main_entry.display_source UPDATE.",
      },
      {
        name: "deleteContentDetails",
        signature: "(mainId)",
        status: "new",
        description: "Hard DELETE (for hard unlink — used by ContentResolver.unlinkAniList). Cascades from main_entry FK anyway.",
      },
      {
        name: "getAllContentDetails",
        signature: "()",
        status: "new",
        description: "For backup dump. Replaces getAllAniListDetails + getAllExtensionDetails.",
      },
    ],
  },
  {
    group: "Dead queries DROPPED (per §4.4)",
    subtitle: "4 dead queries removed — 0 callers each.",
    queries: [
      {
        name: "deleteAnimeMetadata",
        signature: "(mainId)",
        status: "dropped",
        description: "0 callers — anime_metadata_cache table being dropped.",
      },
      {
        name: "deleteEpisodeMetadata",
        signature: "(...)",
        status: "dropped",
        description: "0 callers — CASCADE handles it.",
      },
      {
        name: "deleteBrowseCache + getAllBrowseCache",
        signature: "()",
        status: "dropped",
        description: "Both 0 callers — browse_cache table is kept but these queries are dead.",
      },
      {
        name: "deleteExtensionDetail",
        signature: "(mainId)",
        status: "dropped",
        description: "0 callers — being replaced by clearExtensionAxis on content_details.",
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 5 — INDEPENDENT IMPROVEMENTS (bundled, no merge required)
 * ------------------------------------------------------------------------- */

export interface ImprovementItem {
  id: string;
  title: string;
  body: string;
  detail?: string;
  accent: "primary" | "success" | "warning" | "secondary" | "danger";
}

export const INDEPENDENT_IMPROVEMENTS: ImprovementItem[] = [
  {
    id: "4.1",
    title: "Drop description from main_entry (with caller migration)",
    body: "main_entry.description TEXT — has 3 fallback-reader caller sites (per Review v2-1 FLAW 2): MainActivity.kt:671/800 (description = content.description ?: extDetail?.description), DownloadScanner.kt:276 (description = record.description ?: extDetail?.description), DownloadStorageProvider.kt:265/281 (via DownloadContentInfo). These callers must be migrated to read content_details.data_synopsis (or ext_description as fallback) instead of main_entry.description. The column is never WRITTEN non-null today (ContentRepository.insertContent always passes description = null), so dropping it loses no data — but the read-side callers need updating.",
    detail:
      "v2 correction (per Review v2-1): v1 said '0 callers'. Actual: 3 fallback-reader callers. The §3 line 'no callers' was wrong — the actionable §4.1 enumeration is correct. Migration is mechanical: each caller swaps `main_entry.description` → `content_details.data_synopsis ?: content_details.ext_description`.",
    accent: "danger",
  },
  {
    id: "4.2",
    title: "Fix 2 missing FK declarations (pre-existing bugs)",
    body: "watch_progress.main_id — watch.sq:18 has only a comment, no FK clause. notification_sent.main_id — notifications.sq:38-45 has no FK. Add FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE to both.",
    detail:
      "Precondition (per Review v2-1): adding these FKs will FAIL if existing rows reference non-existent main_id values. Debug-build-only — wipe the DB (dev users clear app data once) before applying the new schema. SQLite can't ALTER TABLE to add a FK, so DatabaseDriverFactory.onOpen must DROP + CREATE these 2 tables (acceptable per CORE_RULES §30).",
    accent: "warning",
  },
  {
    id: "4.3",
    title: "Fix episode_number type mismatch (schema + API)",
    body: "notification_sent.episode_number INTEGER → REAL (was rounding 12.5→12, breaking dedup). episode_schedule.episode_number INTEGER → REAL (same issue). 4 other tables already use REAL for fractional episodes (12.5 for OVAs).",
    detail:
      "v2 CORRECTION (per Review v2-2B Check 9): SQLDelight maps REAL → Kotlin Double (not Long). So this change DOES affect the Kotlin API surface — callers in ScheduleStore, NotificationConfigStore, ActualReleaseUpdater, UpdateEngine, SmartReleaseCheckWorker that currently use Long must change to Double. This is a compile-safe migration (SQLDelight catches the type mismatch at compile time). The plan includes BOTH the schema change AND the Kotlin caller migration. NOT deferred (corrected from prior plan version).",
    accent: "warning",
  },
  {
    id: "4.4",
    title: "Drop 4 dead queries",
    body: "deleteAnimeMetadata (0 callers — table being dropped). deleteEpisodeMetadata (0 callers — CASCADE handles it). deleteBrowseCache + getAllBrowseCache (0 callers). deleteExtensionDetail (0 callers — being replaced by clearExtensionAxis on content_details).",
    accent: "danger",
  },
  {
    id: "4.5",
    title: "Drop 2 dead methods from ContentRepository",
    body: "deleteExtensionDetail() (0 callers — being replaced at the query level by clearExtensionAxis on content_details). getDefaultCategoryCount() (0 callers).",
    accent: "danger",
  },
  {
    id: "4.6",
    title: "Drop redundant indexes",
    body: "idx_content_data_source — no query filters on data_source_id alone. idx_content_extension — single-column on content(extension_id), redundant with composite idx_content_extension_url (leftmost column covered). idx_content_genre_main — duplicates leftmost column of composite PK. idx_library_item_main — duplicates leftmost column of idx_library_item_unique.",
    accent: "danger",
  },
  {
    id: "4.7",
    title: "DataSourceExtras + ExtensionExtras typed accessors",
    body: "The data_extra_json + ext_extra_json columns hold source-specific fields. To avoid repeating JSON-parse logic at every read site, introduce two typed accessors. DataSourceExtras: idMal (Long?), trailerUrl, ageRating, studio, coverUrlLarge, coverUrlSmall. ExtensionExtras: scanlatorGroup, chapterCount (Int?), volumeCount (Int?). Both @Serializable with toJson/fromJson. The Json instance MUST use ignoreUnknownKeys = true (so adding any new field doesn't break parsing of existing rows). The ContentDetails data class exposes dataExtras: DataSourceExtras + extExtras: ExtensionExtras (parsed once on read).",
    accent: "success",
  },
  {
    id: "4.8",
    title: "source_ref_id String↔Int conversion convention",
    body: "data_source_ref_id is TEXT (for uniformity across AniList/Kitsu/MAL/TMDB IDs). But the episode metadata engine (D-190) calls fetchEpisodeMetadata(anilistId: Int, malId: Int?, ...) — it expects Int. CONVENTION: the ContentDetails data class exposes typed accessors — val anilistId: Int?, val malId: Int?, val kitsuId: Int?, val tmdbId: Int?. The malId accessor returns Int? via extras.idMal?.toInt() (extras.idMal is Long? — safer for future MAL IDs).",
    detail:
      "Note (per Review v2-2B Check 2): existing AniListDetail.anilistId is Int (non-null). Changing to Int? (nullable via computed property) breaks 12+ non-null consumers. Mitigation: callers that previously did anilistDetail.anilistId (non-null) must add a null check (contentDetails.anilistId ?: 0 or != null). Kotlin compile-safety catches every missed site. Repository-layer Long↔TEXT conversion via .toString() on write + .toLongOrNull() on read — no ColumnAdapter needed since callers stay in Kotlin Long?.",
    accent: "secondary",
  },
  {
    id: "4.9",
    title: "unlinkSource flow (fixes orphan-row bug)",
    body: "The current DetailsViewModel.unlinkSource() doesn't touch the DB — leaves orphaned extension_detail rows. The NEW clearExtensionAxis(mainId) query NULLs the extension fields (keeps the row for re-linking). unlinkAniList() calls clearDataSourceAxis(mainId) (also NULLs — symmetric). Both also: (1) Update main_entry.display_source if the unlinked axis was the preferred display; (2) Regenerate main_entry.content_id via ContentIdGenerator.generate() + updateMainEntryContentId. All 3 writes (content_details + main_entry.display_source + main_entry.content_id) are in a single DB transaction.",
    detail:
      "NEW method (per Review v2-2A): add ContentResolver.unlinkExtension(mainId) — calls clearExtensionAxis + content_id regeneration + display_source update per this section. (The existing unlinkSource() in DetailsViewModel currently doesn't touch the DB — this new resolver method fixes that.)",
    accent: "primary",
  },
  {
    id: "4.10",
    title: "content_id regeneration + transaction boundaries",
    body: "Every source-switch operation (link/unlink/switch) MUST also call ContentIdGenerator.generate() + repo.updateMainEntryContentId(mainId, newContentId). The existing ContentResolver.linkAniList / linkExtensionToExisting / unlinkAniList already do this — the new updateDataSourceAxis / updateExtensionAxis queries are called FROM these resolver methods, not directly from ViewModels.",
    detail:
      "Transaction boundaries (per Review v2-2A Check 6): Switch flow (updateDataSourceAxis / updateExtensionAxis) — the content_details UPDATE + main_entry.content_id UPDATE are wrapped in a single DB transaction at the resolver layer. display_source is NOT touched on switch (the axis preference stays the same). Unlink flow (clearDataSourceAxis / clearExtensionAxis) — the content_details NULL UPDATE + main_entry.content_id UPDATE + main_entry.display_source UPDATE (if the unlinked axis was the preferred display) are wrapped in a single DB transaction.",
    accent: "primary",
  },
  {
    id: "4.11",
    title: "Standardize naming",
    body: "Indexes: idx_<full_table_name>_<cols>[_unique|_partial]. Retention query params: named :cutoff (not positional ?). audio_variant everywhere (currently video_audio in 2 download tables).",
    accent: "primary",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 6 — FINAL TABLES (22 tables, 10 groups)
 * ------------------------------------------------------------------------- */

export type TableStatus =
  | "RENAMED"
  | "NEW"
  | "UPDATED"
  | "UNCHANGED"
  | "DROPPED"
  | "ABSORBED";

export interface FinalTableSpec {
  table: string;
  sqFile: string;
  group: string;
  status: TableStatus;
  why: string;
  improvements?: string;
}

export const TABLE_STATUS_META: Record<
  TableStatus,
  { label: string; colorVar: string; symbol: string }
> = {
  RENAMED: { label: "RENAMED", colorVar: "var(--c-primary)", symbol: "→" },
  NEW: { label: "NEW", colorVar: "var(--c-success)", symbol: "+" },
  UPDATED: { label: "UPDATED", colorVar: "var(--c-warning)", symbol: "△" },
  UNCHANGED: { label: "UNCHANGED", colorVar: "var(--c-text-secondary)", symbol: "·" },
  DROPPED: { label: "DROPPED", colorVar: "var(--c-danger)", symbol: "×" },
  ABSORBED: { label: "ABSORBED", colorVar: "var(--c-danger)", symbol: "⊆" },
};

export const FINAL_TABLES: {
  group: string;
  subtitle: string;
  rows: FinalTableSpec[];
}[] = [
  {
    group: "Group 1 — Identity & Sources (4 tables)",
    subtitle:
      "The identity hub + lookup tables + the NEW wide content_details table (the centerpiece).",
    rows: [
      {
        table: "main_entry",
        sqFile: "content.sq",
        group: "Identity & Sources",
        status: "RENAMED",
        why: "Identity hub — renamed from content (clearer name, avoids android.content.* collision). v2 keeps extension_repo_id + keeps display_source as single UX column (values 'data_source' | 'extension').",
        improvements: "Drop description (3 caller migration §4.1), keep extension_repo_id, keep display_source as UX column, add updateMainEntryTitle (Change 1 + §4.1)",
      },
      {
        table: "data_source",
        sqFile: "content.sq",
        group: "Identity & Sources",
        status: "UNCHANGED",
        why: "Lookup: AniList/TMDB/Kitsu/MAL. Kept SEPARATE from `system` per R-2 recommendation (Change 4).",
      },
      {
        table: "system",
        sqFile: "content.sq",
        group: "Identity & Sources",
        status: "UNCHANGED",
        why: "Lookup: Aniyomi/CloudStream/Sora/MangaYomi. Kept SEPARATE from `data_source` per R-2 recommendation (Change 4).",
      },
      {
        table: "content_details",
        sqFile: "content.sq",
        group: "Identity & Sources",
        status: "NEW",
        why: "THE CENTERPIECE — ONE wide table (Option A, 26 cols: 1 PK + 13 data_* + 12 ext_*) that merges 4 old tables (anilist_detail, extension_detail, other_source_detail, anime_metadata_cache). Two axes (data-source + extension) coexist in one row, switchable + unlinkable independently. 2 indexes + 11 queries.",
        improvements: "NEW table (Change 2) — 26 cols, 2 indexes, 11 queries",
      },
    ],
  },
  {
    group: "Group 2 — Library (2 tables)",
    subtitle: "User-defined categories + library junction.",
    rows: [
      {
        table: "library_category",
        sqFile: "library.sq",
        group: "Library",
        status: "UNCHANGED",
        why: "User-defined categories. Confirmed keep-separate via R-2 — classic M:N normalization.",
      },
      {
        table: "library_item",
        sqFile: "library.sq",
        group: "Library",
        status: "UPDATED",
        why: "M:N junction (content ↔ category).",
        improvements: "Drop redundant id + index (§4.6), rename FK to main_entry (Change 1)",
      },
    ],
  },
  {
    group: "Group 3 — User Activity (2 tables)",
    subtitle: "Episode watch progress + activity log.",
    rows: [
      {
        table: "watch_progress",
        sqFile: "watch.sq",
        group: "User Activity",
        status: "UPDATED",
        why: "Episode watch progress.",
        improvements: "Add missing FK (§4.2), rename FK to main_entry (Change 1)",
      },
      {
        table: "activity_event",
        sqFile: "tracking.sq",
        group: "User Activity",
        status: "UNCHANGED",
        why: "Activity log. Different cardinality + retention than other tables — keep-separate per R-2.",
      },
    ],
  },
  {
    group: "Group 4 — Updates & Schedule (3 tables)",
    subtitle: "New-episodes feed + per-anime smart-update state + airing schedule.",
    rows: [
      {
        table: "episode_update",
        sqFile: "episodeUpdate.sq",
        group: "Updates & Schedule",
        status: "UPDATED",
        why: "New-episodes feed (1:N). Different cardinality + retention — keep-separate per R-2.",
        improvements: "Rename FK to main_entry (Change 1)",
      },
      {
        table: "anime_update_state",
        sqFile: "animeUpdateState.sq",
        group: "Updates & Schedule",
        status: "UPDATED",
        why: "Per-anime smart-update state (1:1).",
        improvements: "Rename FK to main_entry (Change 1)",
      },
      {
        table: "episode_schedule",
        sqFile: "episodeSchedule.sq",
        group: "Updates & Schedule",
        status: "UPDATED",
        why: "Airing schedule.",
        improvements: "Fix episode_number type INTEGER→REAL (§4.3), rename FK to main_entry (Change 1)",
      },
    ],
  },
  {
    group: "Group 5 — Notifications (2 tables)",
    subtitle: "Per-content notification prefs + sent dedup log.",
    rows: [
      {
        table: "notification_config",
        sqFile: "notifications.sq",
        group: "Notifications",
        status: "UNCHANGED",
        why: "Per-content notification preferences (1:1, backup-eligible).",
      },
      {
        table: "notification_sent",
        sqFile: "notifications.sq",
        group: "Notifications",
        status: "UPDATED",
        why: "Notification dedup log.",
        improvements: "Add missing FK (§4.2), fix episode_number type (§4.3), rename FK to main_entry (Change 1)",
      },
    ],
  },
  {
    group: "Group 6 — Ratings (2 tables)",
    subtitle: "Per-anime + per-episode user ratings.",
    rows: [
      {
        table: "user_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        status: "UPDATED",
        why: "Per-anime rating (1:1). Different access pattern — keep-separate per R-2.",
        improvements: "Rename FK to main_entry (Change 1)",
      },
      {
        table: "user_episode_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        status: "UPDATED",
        why: "Per-episode rating (1:N).",
        improvements: "Rename FK to main_entry (Change 1)",
      },
    ],
  },
  {
    group: "Group 7 — Downloads (2 tables)",
    subtitle: "Active downloads + downloaded-episode index.",
    rows: [
      {
        table: "download_queue",
        sqFile: "downloadQueue.sq",
        group: "Downloads",
        status: "UNCHANGED",
        why: "Active downloads (transient). Different cardinality + retention — keep-separate per R-2.",
      },
      {
        table: "downloaded_episode",
        sqFile: "downloadedEpisode.sq",
        group: "Downloads",
        status: "UNCHANGED",
        why: "Completed downloads index (permanent).",
      },
    ],
  },
  {
    group: "Group 8 — Content Classification (2 tables)",
    subtitle: "Genre lookup + content/genre M:N junction.",
    rows: [
      {
        table: "genre",
        sqFile: "genres.sq",
        group: "Content Classification",
        status: "UNCHANGED",
        why: "Genre lookup (~40 AniList canonical). Classic M:N normalization — keep-separate per R-2.",
      },
      {
        table: "content_genre",
        sqFile: "genres.sq",
        group: "Content Classification",
        status: "UPDATED",
        why: "M:N junction (content ↔ genre).",
        improvements: "Drop redundant index (§4.6), rename FK to main_entry (Change 1)",
      },
    ],
  },
  {
    group: "Group 9 — Caches (2 tables)",
    subtitle: "Episode-level metadata cache + Browse-page JSON blob cache.",
    rows: [
      {
        table: "data_cache_episode",
        sqFile: "dataCache.sq",
        group: "Caches",
        status: "UNCHANGED",
        why: "Episode-level metadata (AniZip/Jikan/Kitsu) — different cardinality + sources than content_details. Handles 100k rows. Keep-separate per R-2.",
      },
      {
        table: "browse_cache",
        sqFile: "dataCache.sq",
        group: "Caches",
        status: "UNCHANGED",
        why: "JSON blob cache for Browse page (6h TTL). Different shape — keep-separate per R-2.",
      },
    ],
  },
  {
    group: "Group 10 — App Configuration (1 table)",
    subtitle: "User settings KV — absorbs the dropped app_metadata.",
    rows: [
      {
        table: "app_settings",
        sqFile: "appSettings.sq",
        group: "App Configuration",
        status: "UPDATED",
        why: "User settings KV. Absorbs the dropped app_metadata (Change 3). Add setting_category='internal' convention for schema-version-tracking flags. Backup filter: WHERE setting_category != 'internal'.",
        improvements: "Absorb app_metadata (Change 3), add setting_category convention",
      },
    ],
  },
  {
    group: "Dropped (was in content.sq + dataCache.sq + app.sq)",
    subtitle: "5 tables removed — 4 merged into content_details, 1 absorbed into app_settings.",
    rows: [
      {
        table: "anilist_detail",
        sqFile: "content.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "Merged into content_details data-axis (Change 2). 13 cols map 1:1 to data_* prefix. anilist_id → data_source_ref_id (TEXT), synopsis → data_synopsis, id_mal → data_extra_json.",
      },
      {
        table: "extension_detail",
        sqFile: "content.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "Merged into content_details extension-axis (Change 2). 12 cols map 1:1 to ext_* prefix. extension_id INTEGER→TEXT, description → ext_description, thumbnail_url → ext_thumbnail_url.",
      },
      {
        table: "other_source_detail",
        sqFile: "content.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "DEAD CODE — 0 callers, never written, empty table. The generic KV table designed for 'future TMDB/Kitsu/MAL' was never wired up. The new content_details.data_extra_json + data_source_type discriminator handle all future data sources. Zero data loss.",
      },
      {
        table: "anime_metadata_cache",
        sqFile: "dataCache.sq",
        group: "Dropped",
        status: "ABSORBED",
        why: "9/12 columns duplicate anilist_detail (now in content_details data-axis). The 3 unique columns are all dead: title duplicates main_entry.title, source_type hardcoded 'anilist' never read, fetched_at write-only with no refresh-logic reader. Absorbed into content_details (Change 2).",
      },
      {
        table: "app_metadata",
        sqFile: "app.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "DEAD CODE — 0 Kotlin callers, 0 rows. Its 2-column schema (key, value) is a strict subset of app_settings' 5-column schema. Absorbed into app_settings with setting_category='internal' convention (Change 3).",
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 7 — CONS + RISKS (severity color-coded)
 * ------------------------------------------------------------------------- */

export type RiskSeverity = "HIGH" | "MEDIUM" | "LOW" | "RESOLVED";

export interface ConOrRisk {
  text: string;
  severity: RiskSeverity;
  /** For RESOLVED risks — what resolved it. */
  resolvedBy?: string;
}

export interface ConRiskGroup {
  group: string;
  items: ConOrRisk[];
}

export const RISK_SEVERITY_META: Record<
  RiskSeverity,
  { label: string; colorVar: string; symbol: string }
> = {
  HIGH: { label: "High", colorVar: "var(--c-danger)", symbol: "!" },
  MEDIUM: { label: "Medium", colorVar: "var(--c-warning)", symbol: "▲" },
  LOW: { label: "Low", colorVar: "var(--c-success)", symbol: "↓" },
  RESOLVED: { label: "Resolved", colorVar: "var(--c-success)", symbol: "✓" },
};

export const CONS_RISKS: ConRiskGroup[] = [
  {
    group: "Change 1 — Rename content → main_entry",
    items: [
      {
        text: "Con: Mechanical churn — 9 .sq files + 4 indexes + 9 queries + 1 Kotlin string + 1 DbReference. Pure cost, no functional benefit beyond clarity.",
        severity: "LOW",
      },
      {
        text: "Risk: LOW. SQLDelight's type-safe code generation catches any missed rename at compile time. String literals (DatabaseDriverFactory.kt:168 raw SQL + DetailsScreen.kt:385 DbReference) are runtime-only — covered by the §9 checklist (DatabaseDriverFactory update + DbReference rename + device test).",
        severity: "LOW",
      },
      {
        text: "Mitigation: Do the table rename first (low risk). Defer the .sq file rename (content.sq → main_entry.sq) + Kotlin class renames (ContentRecord → MainEntryRecord, ~24 caller files) to a separate session.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Change 2 — Merge 4 tables → ONE wide content_details (Option A)",
    items: [
      {
        text: "Con: ~750-900 lines across ~32-38 files need updating (ContentRepository, ContentResolver, ContentModels, DataCacheRepository, 13 read-caller files, 3 write-caller files, 5 episode_number type-change sites). Effort estimate corrected per Review v2-2B Check 11 (prior estimate of ~600 lines / ~25 files was under by 20-30%).",
        severity: "MEDIUM",
      },
      {
        text: "Con: The extension_id type change (INTEGER → TEXT in DB; Kotlin stays Long? for Aniyomi compat). Repository-layer conversion via .toString() on write + .toLongOrNull() on read — no ColumnAdapter needed (per Review v2-2B Check 2 + Review v2-3+4 Check 7).",
        severity: "MEDIUM",
      },
      {
        text: "Con: The getContentDetails != null semantics change (now means 'any data source OR extension linked' — callers must check dataSourceType == 'anilist' or extensionType == 'aniyomi' specifically). ~13 read-caller files affected.",
        severity: "MEDIUM",
      },
      {
        text: "Con: anilistId accessor changes from Int (non-null) to Int? (nullable) — breaks 12+ non-null consumers (per Review v2-2B Check 3). Kotlin compile-safety catches all missed sites.",
        severity: "MEDIUM",
      },
      {
        text: "Con: AniList-only rows have ~12 NULL extension cols; extension-only rows have ~13 NULL data-source cols. ~1 byte/NULL overhead per NULL — negligible. SQLite supports 2000 columns; the 26-col count is a non-issue.",
        severity: "LOW",
      },
      {
        text: "Risk: MEDIUM. The write-path convergence is the riskiest part — if a call site is missed, the merged row could be partially stale (e.g. data-axis updated but ext-axis not, or vice versa).",
        severity: "MEDIUM",
      },
      {
        text: "RESOLVED — the clearExtensionAxis / clearDataSourceAxis queries originally couldn't NULL NOT NULL columns. Resolved by making the axis fields nullable (data_source_type, data_source_ref_id, extension_type, extension_id, source_id, anime_url all nullable).",
        severity: "RESOLVED",
        resolvedBy:
          "Review v2-1 FLAW 1 + Review v2-2A — axis fields explicitly marked nullable in the content_details schema.",
      },
      {
        text: "Mitigation: Debug builds — drop + recreate (CORE_RULES §30). Sub-agent compile review before push. CI is the final gate. The §9 checklist explicitly calls out DatabaseDriverFactory + DbReference updates + a device test on link/switch/unlink cycles.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Change 3 — Drop app_metadata",
    items: [
      {
        text: "Con: None — table is dead code (0 callers, 0 rows).",
        severity: "LOW",
      },
      {
        text: "Risk: ZERO. Grep confirmed 0 Kotlin callers. The 2-column schema is a strict subset of app_settings' 5-column schema.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Change 4 — Keep data_source + system separate",
    items: [
      {
        text: "Con: None — this is a keep-separate decision, not a change. No code impact.",
        severity: "LOW",
      },
      {
        text: "Trade-off: 1 table 'saved' by merging, but FK integrity weakened (can't enforce 'data_source_id points to a data_source row' at the DB level). Not worth it per R-2.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Independent improvements",
    items: [
      {
        text: "The episode_number type fix (INTEGER → REAL) is a SCHEMA + API change — ~5-6 Kotlin caller files (ScheduleStore, NotificationConfigStore, ActualReleaseUpdater, UpdateEngine, SmartReleaseCheckWorker) need Long→Double signature updates. Compile-safe (SQLDelight catches type mismatch).",
        severity: "LOW",
      },
      {
        text: "The description drop requires 3 caller-site migrations (MainActivity.kt:671/800, DownloadScanner.kt:276, DownloadStorageProvider.kt:265/281) — each swaps main_entry.description → content_details.data_synopsis ?: content_details.ext_description. Compile-safe.",
        severity: "LOW",
      },
      {
        text: "Risk: LOW. All improvements are additive or fix existing bugs. Compile-safety is the primary mitigation.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Overall migration risk",
    items: [
      {
        text: "Debug builds only — no production users, no migration scripts needed (CORE_RULES §30). Dev users clear app data once.",
        severity: "LOW",
      },
      {
        text: "No data loss — every dropped column/table is either duplicated, dead (zero callers), or explicitly migrated. Verified by 7 research sub-agents (5 prior session + 2 this session).",
        severity: "LOW",
      },
      {
        text: "CI is the final gate — sub-agent compile review before push, then CI verifies. The §9 checklist has 12 items including device tests on link/switch/unlink cycles.",
        severity: "LOW",
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 8 — DEFERRED / SKIPPED
 * ------------------------------------------------------------------------- */

export interface DeferredItem {
  num: number;
  title: string;
  body: string;
  reason: string;
}

export const DEFERRED_ITEMS: DeferredItem[] = [
  {
    num: 1,
    title: "Kotlin class renames",
    body: "ContentRecord → MainEntryRecord, ContentRepository → MainEntryRepository, ContentResolver → MainEntryResolver, etc. ~24 caller files.",
    reason: "The Kotlin class names are decoupled from the table name. Separate session — high churn, low benefit.",
  },
  {
    num: 2,
    title: ".sq file rename (content.sq → main_entry.sq)",
    body: "Would change database.contentQueries → database.mainEntryQueries property (19 references in ContentRepository + 1 in GenreRepository).",
    reason: "Riskier than the table rename. Separate session. The plan recommends renaming the file too, for consistency, but this is the riskier part.",
  },
  {
    num: 3,
    title: "Split AnimeDetailsProvider interface",
    body: "Split into DataSourceProvider + ExtensionDetailsProvider.",
    reason: "Code-layer refactor, not schema. Separate session.",
  },
  {
    num: 4,
    title: "RetentionCoordinator worker",
    body: "Would centralize the 5 retention queries (activity_event, episode_update, notification_sent, browse_cache, data_cache_episode).",
    reason: "Separate session. Currently each query lives next to its table.",
  },
  {
    num: 5,
    title: "data_cache_episode → INSERT ON CONFLICT DO UPDATE",
    body: "Performance optimization for batch refresh.",
    reason: "Not needed at current scale. Separate session.",
  },
  {
    num: 6,
    title: "CHECK constraints",
    body: "Add CHECK constraints to enforce valid enum values (e.g. status IN ('FINISHED','RELEASING','CANCELLED','HIATUS'), data_source_type IN ('anilist','kitsu','mal','tmdb')).",
    reason: "Included in the plan but optional. Can be added incrementally.",
  },
  {
    num: 7,
    title: "episode_number API surface change (Long→Double in Kotlin callers) — NOT deferred",
    body: "Per Review v2-2B Check 9, SQLDelight maps REAL → Kotlin Double, so the schema change (INTEGER → REAL in notification_sent + episode_schedule) forces the Kotlin API change. Callers in ScheduleStore, NotificationConfigStore, ActualReleaseUpdater, UpdateEngine, SmartReleaseCheckWorker must change Long → Double. This is INCLUDED in this plan, not deferred.",
    reason: "Corrected from prior plan version (v1 said 'deferred' — wrong). The schema + API changes are coupled via SQLDelight's type mapping.",
  },
  {
    num: 8,
    title: "CloudStream/Sora/MangaYomi String extension IDs",
    body: "The extension_id column on content_details is TEXT (DB) but Kotlin types stay Long? for Aniyomi compatibility. When a future extension ecosystem uses truly-String IDs (e.g. CloudStream's 'kawaiiyomistreams.com'), a separate extension_id_str TEXT column will be needed.",
    reason: "Not a concern for Aniyomi-only today. Deferred until CloudStream integration actually lands.",
  },
  {
    num: 9,
    title: "data_source + system merge",
    body: "Evaluated per R-2 — recommended keep-separate. Revisit if the FK integrity concern can be solved cleanly.",
    reason: "Only saves 1 table; weakens FK integrity. Bad trade per R-2.",
  },
  {
    num: 10,
    title: "Library in 'app settings' group",
    body: "Evaluated per R-2 — recommended keep as own group. Library is user data, not app config.",
    reason: "Different semantics. Keep-separate.",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 9 — FUTURE-PROOFING
 * ------------------------------------------------------------------------- */

export interface FutureProofScenario {
  title: string;
  accent: "primary" | "success" | "secondary";
  steps: string[];
  footer: string;
}

export const FUTURE_PROOFING: FutureProofScenario[] = [
  {
    title: "Adding a new data source (e.g. MAL)",
    accent: "primary",
    steps: [
      "Implement MalDataSourceProvider : DataSourceProvider (code layer — mirrors the D-190 episode metadata engine pattern).",
      "User switches data source → ContentResolver.linkDataSource('mal', malId, ...) → calls updateDataSourceAxis on content_details: sets data_source_type='mal', data_source_ref_id=malId, new metadata fields. SINGLE ATOMIC UPDATE, wrapped in DB transaction with main_entry.content_id regeneration.",
      "The main_id stays the same. The previous AniList ID is preserved in data_extra_json as {\"previous_anilist_id\": 12345} for re-switching back.",
      "ZERO SCHEMA CHANGE. (MAL-specific fields like main_picture.medium/.large go into data_extra_json + data_cover_url.) ⚠️ Note (per Review v2-2A Check 7): MAL's related_anime (relations list — sequels/prequels/side stories) is NOT addressable via extra_json alone if cross-content navigation is needed — a future content_relation junction table would be added when that feature is wired. Out of scope for this plan.",
    ],
    footer:
      "Schema unchanged — the data_source_type discriminator + data_extra_json absorb new sources. content_relation junction deferred (future scope for cross-content navigation).",
  },
  {
    title: "Adding a new extension ecosystem (e.g. CloudStream)",
    accent: "secondary",
    steps: [
      "Implement CloudStreamVideoExtensionProvider : VideoExtensionProvider with ecosystemId='cloudstream' (code layer).",
      "User switches extension → ContentResolver.linkExtension('cloudstream', ...) → calls updateExtensionAxis on content_details: sets extension_type='cloudstream', new extension_id/source_id/anime_url. SINGLE ATOMIC UPDATE, wrapped in DB transaction with main_entry.content_id regeneration.",
      "The main_id stays the same. Episode list changes (different numbering) — data_cache_episode rows for the old extension are invalidated + re-fetched.",
      "ZERO SCHEMA CHANGE for Long-ID extensions. ⚠️ CloudStream uses String source IDs — a future extension_id_str TEXT column would be needed (deferred per §7 item 8). For Aniyomi-only (Long IDs), zero schema change.",
    ],
    footer: "Schema unchanged for Aniyomi-style Long IDs. String-ID support deferred (§7 item 8).",
  },
  {
    title: "Independent switching (the headline feature)",
    accent: "success",
    steps: [
      "The data_* + ext_* column prefixes + independent updateDataSourceAxis / updateExtensionAxis queries allow the user to switch either independently.",
      "Example: AniList metadata + Aniyomi extension-A → MAL metadata + Aniyomi-extension-A → MAL metadata + CloudStream-extension-B.",
      "Each combination is a valid state. updateDataSourceAxis touches only the 13 data-source fields; updateExtensionAxis touches only the 12 extension fields. Neither clobbers the other.",
      "main_entry.content_id is regenerated on every switch (preserves the 'content_id changes when sources switch' invariant). display_source (the UX-preference column) is NOT touched on switch — the axis preference stays the same.",
    ],
    footer:
      "Independent switching is enabled by the two-axis design (data_* + ext_* prefixes) — the centerpiece of v2.",
  },
  {
    title: "Future content types (manga, novels, images)",
    accent: "primary",
    steps: [
      "Manga: ext_author + ext_artist already in content_details schema. ext_extra_json for chapter_count/volume_count/scanlator_group. content_format='image' on main_entry.",
      "Novels: ext_extra_json for publisher/isbn. Most fields shared (title, description, genres, cover, status). content_format='text' on main_entry.",
      "Images: sparse row, mostly NULLs. content_format='image' on main_entry. No schema change.",
      "ZERO SCHEMA CHANGE for any content type — the 26-column schema + extra_json covers all of them.",
    ],
    footer:
      "Schema unchanged — the 26-column content_details + extra_json absorb all content types.",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 10 — REVIEW PROCESS (4 iterations — v2)
 * ------------------------------------------------------------------------- */

export interface ReviewIteration {
  num: string;
  title: string;
  subtitle: string;
  accent: "danger" | "warning" | "success" | "primary";
  counts: { label: string; value: number }[];
  found: string[];
  fixed: string[];
}

export const REVIEW_ITERATIONS: ReviewIteration[] = [
  {
    num: "1",
    title: "Iteration 1 — initial review",
    subtitle:
      "First-pass review of the initial v2 draft. Found 1 FLAW + 9 CONCERNS. Tally: ✅ CONFIRMED = 8, ❌ FLAW = 0 (after fix), ⚠️ CONCERN = 4.",
    accent: "danger",
    counts: [
      { label: "FLAW", value: 1 },
      { label: "CONCERN", value: 9 },
    ],
    found: [
      "FLAW: clearExtensionAxis / clearDataSourceAxis queries couldn't NULL NOT NULL columns — the schema said NOT NULL but the queries needed to NULL them on unlink.",
      "CONCERN: extension_id Long↔TEXT type change feasibility (ColumnAdapter is a new pattern in this codebase) — Repository-layer .toString() / .toLongOrNull() conversion is sufficient (no ColumnAdapter needed).",
      "CONCERN: anilistId nullness propagation — breaks 12+ non-null consumers (Int → Int?).",
      "CONCERN: getAniListDetail != null semantics change — now means 'any data source linked' not 'AniList specifically'.",
      "CONCERN: cachedMeta.title → content.title redirect is a behavior change (refresh flow doesn't update main_entry.title).",
      "CONCERN: clearExtensionAxis NULLs propagate to .data.json via DownloadScanner.",
      "CONCERN: FK-add precondition not actionable — SQLite can't ALTER TABLE to add FK.",
      "CONCERN: episode_number REAL fix is a half-fix (Kotlin API surface still truncates).",
    ],
    fixed: [
      "Made data_source_type, data_source_ref_id, extension_type, extension_id, source_id, anime_url NULLABLE (resolves the FLAW).",
      "Confirmed extension_id Long↔TEXT via Repository-layer conversion (.toString() / .toLongOrNull()) — no ColumnAdapter. Kotlin types stay Long? for .data.json compat.",
      "Documented the anilistId nullness propagation + 12+ caller sites needing null checks (compile-safety catches all).",
      "Documented the getAniListDetail != null semantic change — callers must check dataSourceType == 'anilist'.",
      "Added updateMainEntryTitle query — refresh flow now updates main_entry.title too.",
      "Documented that DownloadScanner uses ?: fallbacks for NULL fields (main_id match path survives).",
      "Specified DROP + CREATE blocks for watch_progress + notification_sent in DatabaseDriverFactory.onOpen.",
      "Scoped episode_number fix as schema + API (SQLDelight maps REAL → Double, forcing the Kotlin API change). NOT deferred.",
    ],
  },
  {
    num: "2A",
    title: "Iteration 2A — architecture review",
    subtitle:
      "Architecture-focused review. Found 6 CONCERNS (no FLAWs). Focused on transaction boundaries + display_source value semantics + MAL related_anime + index count consistency.",
    accent: "warning",
    counts: [
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 6 },
    ],
    found: [
      "display_source value semantics — old values were source-name-level ('anilist' | 'extension'); new values should be axis-level ('data_source' | 'extension') to scale to future Kitsu/MAL/TMDB. Migration: existing 'anilist' → 'data_source' on schema rebuild.",
      "updateExtensionAxis / updateExtensionAxis atomicity — must be single atomic UPDATE statements wrapped in DB transaction that ALSO regenerates main_entry.content_id. Switch flow vs unlink flow have different transaction boundaries.",
      "content_id regeneration — every source-switch operation MUST call ContentIdGenerator.generate() + repo.updateMainEntryContentId(mainId, newContentId).",
      "MAL related_anime (relations list — sequels/prequels/side stories) is NOT addressable via extra_json alone if cross-content navigation is needed — a future content_relation junction table would be needed.",
      "Index count consistency — §3 line 121 says 'Indexes (2)'; §9 checklist line 439 says '26 cols, 2 indexes, 11 queries'. Must match.",
      "Multi-size cover URLs — convention needed (primary in data_cover_url, alternatives in data_extra_json).",
    ],
    fixed: [
      "§3 line 151 (main_entry.display_source): value semantics explicitly stated as 'data_source' | 'extension' (axis-level). Migration of existing 'anilist' → 'data_source' explicitly stated.",
      "§4.10 transaction boundaries: switch flow (updateDataSourceAxis / updateExtensionAxis) wraps content_details UPDATE + main_entry.content_id UPDATE in single transaction; display_source NOT touched. Unlink flow (clearDataSourceAxis / clearExtensionAxis) wraps content_details NULL UPDATE + main_entry.content_id UPDATE + main_entry.display_source UPDATE in single transaction.",
      "§4.10: content_id regeneration mandated on every source-switch (called from ContentResolver methods, not directly from ViewModels). NEW ContentResolver.unlinkExtension(mainId) method specified.",
      "§8 line 416: MAL related_anime acknowledged as future scope with content_relation junction table to be added when cross-content navigation is wired. Out of scope for this plan.",
      "§3 line 121 + §9 line 439: both consistently say 'Indexes (2)' and '26 cols, 2 indexes, 11 queries'.",
      "Multi-size cover convention documented (primary in data_cover_url, alternatives in data_extra_json).",
    ],
  },
  {
    num: "2B",
    title: "Iteration 2B — implementation feasibility review",
    subtitle:
      "Implementation-feasibility-focused review. Found 5 CONCERNS (no FLAWs). Corrected the episode_number scope + the effort estimate.",
    accent: "warning",
    counts: [
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 5 },
    ],
    found: [
      "getAniListDetail != null semantics change affects LibraryViewModel:350-365 + similar patterns (~13 caller files).",
      "cachedMeta.title stale-title issue — refresh flow updates cached title but NOT main_entry.title (was previously only updating the now-dropped anime_metadata_cache.title).",
      "clearExtensionAxis NULLs propagate to .data.json via DownloadScanner (could lose extension fields on reinstall).",
      "episode_number INTEGER→REAL is NOT a schema-only fix — SQLDelight maps REAL → Kotlin Double, so the Kotlin API surface (Long) must change to Double. ~5-6 caller files affected (ScheduleStore, NotificationConfigStore, ActualReleaseUpdater, UpdateEngine, SmartReleaseCheckWorker). v1 plan was factually wrong to defer this.",
      "FK-add requires DROP TABLE (SQLite can't ALTER TABLE to add FK) — DatabaseDriverFactory.onOpen needs DROP + CREATE blocks.",
      "Effort estimate ~600 lines / ~25 files is under by 20-30%. Actual: ~32-38 files / ~750-900 lines (13 read-caller + 3 write-caller + 5 wrapper/infra + 3 description-migration + 5 episode_number + 1 DatabaseDriverFactory + 1 DbReference + 3-4 anilistId propagation + 9 .sq files).",
    ],
    fixed: [
      "§4.1: getAniListDetail != null semantic change called out — implementing agent must grep + audit LibraryViewModel:350-365 pattern. Callers must check dataSourceType == 'anilist' specifically.",
      "§4.1: updateMainEntryTitle(mainId, title, updatedAt) query added; refresh flow must call it. Fixes stale-title-after-refresh bug.",
      "§4.9: clearExtensionAxis NULLs accepted as desired behavior — DownloadScanner uses ?: fallbacks, main_id match path survives.",
      "§4.3 + §7 item 7: episode_number scope CORRECTED — schema + API (NOT deferred). SQLDelight maps REAL → Double, so the Kotlin API must change. ~5-6 caller files listed explicitly. Compile-safe.",
      "§4.2: DatabaseDriverFactory.onOpen must include DROP + CREATE blocks for watch_progress + notification_sent.",
      "§6 line 373: effort estimate CORRECTED to ~750-900 lines / ~32-38 files with explicit note '(prior estimate of ~600 lines / ~25 files was under by 20-30%)'.",
    ],
  },
  {
    num: "3+4",
    title: "Iteration 3+4 — final sign-off + confirmation",
    subtitle:
      "Final sign-off review (12 checks) + quick sanity confirmation. APPROVED with minor text-precision fixes. Ready for dashboard.",
    accent: "success",
    counts: [
      { label: "CONFIRMED", value: 11 },
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 1 },
    ],
    found: [
      "All 9 iteration-1+2 fixes verified applied in §1, §3, §4, §6, §7, §8.",
      "§4.3 episode_number scope (2B Check 9) — §1 says 'schema + API (SQLDelight maps REAL→Double)'; §4.3 explicitly lists 5 caller files; §7 item 7 says 'NOT deferred (corrected from prior plan version)'. All 3 sections consistent. ✓",
      "§4.10 transaction boundaries (2A Check 6) — Switch-flow + unlink-flow transaction wrapping explicitly specified; ContentResolver.unlinkExtension(mainId) named as new method. ✓",
      "§3 display_source value semantics (2A Check 4) — values 'data_source' | 'extension' (axis-level); migration 'anilist' → 'data_source' explicitly stated. ✓",
      "§8 MAL related_anime (2A Check 7) — acknowledged as future scope with content_relation junction table. Out of scope for this plan. ✓",
      "§3 index count (2A) — Line 121 says 'Indexes (2)'; §9 checklist line 439 says '26 cols, 2 indexes, 11 queries'. Both consistent. ✓",
      "§4.6 idx_content_extension drop (2A) — explicitly in the redundant-index drop list with rationale. ✓",
      "§6 effort estimate (2B Check 11) — '~750-900 lines across ~32-38 files' with explicit correction note. ✓",
      "§4.1 + §3 description caller migration (2A/2B) — §4.1 enumerates 3 caller sites (MainActivity.kt:671/800, DownloadScanner.kt:276, DownloadStorageProvider.kt:265/281). §3 line 152 says 'has 3 fallback-reader callers — migration specified in §4.1' — does NOT say 'no callers' (old wrong claim removed). ✓",
      "Table count — §1 says '26 → 22'; §5 lists exactly 22 tables across 10 groups (4+2+2+3+2+2+2+2+2+1=22); §5 line 362 confirms 'Total: 22 tables (down from 26)'. ✓",
      "Schema↔improvement consistency — content_details schema (26 cols, 2 indexes, 11 queries) matches §4 improvements; clearExtensionAxis query (§4.9) appears in §3 query list; episode_number REAL (§4.3) matches §5 status flags. ✓",
      "⚠️ MINOR CONCERN: §3 extension_id Long↔TEXT note (2B Check 2) — line 107 says 'Aniyomi Long stringified'; line 374 says 'Kotlin stays Long?'; line 404 (§7) says 'TEXT (DB) but Kotlin types stay Long? for Aniyomi compatibility'. Substance captured but explicit .toString() / .toLongOrNull() Repository-layer method names aren't called out. Implementation will catch via compile-safety. Minor — does not block dashboard presentation.",
    ],
    fixed: [
      "FINAL VERDICT: APPROVE WITH MINOR FIXES — ready for dashboard presentation.",
      "11 of 12 checks CONFIRMED. The single ⚠️ CONCERN is a minor text-precision gap (Check 7) — the substance of the Long↔TEXT conversion is captured but the explicit .toString() / .toLongOrNull() Repository-layer method names aren't called out. This does NOT block dashboard presentation; implementation will catch any missed site via Kotlin compile-safety as the plan explicitly notes.",
      "Optional text amendment before dashboard push (NOT applied — implementation will catch via compile-safety): add an explicit Repository-layer callout near §3 line 107: '(Repository-layer conversion: .toString() on write, .toLongOrNull() on read — no ColumnAdapter needed since callers stay in Kotlin Long?).'",
      "Plan is presentation-ready. Pushing to dashboard at /database-plan/.",
    ],
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 11 — FOOTER NOTE
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE =
  "This is a PROPOSAL v2. No schema changes will be made until the user approves. The dashboard at /database-plan/ presents this plan in a scannable format for review.";

export const FOOTER_NOTE_BULLETS: string[] = [
  "Source of truth: APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md (464 lines, v2).",
  "v2 deltas from v1: ONE wide content_details table (Option A — 26 cols, data_* + ext_* prefixes) — NOT two tables. Drop app_metadata. Keep extension_repo_id. Keep display_source as single UX column. 26 → 22 tables (was 26 → 24 in v1). 10-group presentation.",
  "Research basis: 2 Explore sub-agents this session (R-1 = content_details design Option A, R-2 = re-evaluate merges + grouping) + 5 prior-session Explore sub-agents (Tasks 2-a through 2-e).",
  "Review basis: 4 sub-agent iterations (1, 2A, 2B, 3+4) — 1 FLAW + 20 CONCERNS raised, all addressed. Final verdict: APPROVED WITH MINOR FIXES — ready for dashboard.",
  "Migration policy: debug builds only (CORE_RULES §30) — schema can be rebuilt freely (drop + recreate, no .sqm migration files needed).",
  "Approve this plan → next session implements: schema changes + query renames + caller redirects + verification checklist (§9, 12 items including device tests on link/switch/unlink cycles).",
];
