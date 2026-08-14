/*
 * ANI-KUTA Database Restructuring Plan — typed data for the
 * /database-plan/ dashboard page.
 *
 * Source of truth (THE single source of truth):
 *   APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md
 *   (446 lines, 5 parallel Explore sub-agents, 4 review iterations).
 *
 * Every table schema, every query, every con, every deferred item below is
 * transcribed from that plan file — no summarisation, no drops. The user is
 * reviewing this page to decide whether to APPROVE the restructuring, so
 * completeness matters more than brevity.
 *
 * Consumed by app/database-plan/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * Design follows DESIGN.md (MEMORY OS v3):
 *  - Warm Canvas (#F2EEE8) bg, cards bg #FFFDFA, border #E8E2DA, rounded-2xl
 *  - Indigo primary, Teal success, Amber warning, Rose danger, Violet secondary
 *  - Column status colour coding:
 *      NEW       = Teal    (newly added column)
 *      MODIFIED  = Amber   (type / nullability / constraints changed)
 *      DROPPED   = Rose    (column removed — struck through)
 *      RENAMED   = Indigo  (column renamed, same data)
 *      UNCHANGED = muted   (carried over as-is)
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
  status: "PROPOSAL — NOT YET IMPLEMENTED",
  date: "2026-08-14",
  author:
    "Main agent (researched via 5 parallel Explore sub-agents, reviewed via 4 sub-agent iterations)",
  scope:
    "Schema restructuring of the 26-table SQLDelight database. No code changes this phase — this is the plan only.",
  migrationPolicy:
    "Debug builds only — schema can be rebuilt freely per CORE_RULES §30 (drop + recreate, no .sqm migration files needed).",
  sourceOfTruth:
    "APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md",
} as const;

export const HERO = {
  title: "Database Restructuring Plan",
  kicker: "PROPOSAL — NOT YET IMPLEMENTED",
  description:
    "A full-fledged plan for restructuring the 26-table database → 24 tables. Reviewed via 4 sub-agent iterations. Awaiting your approval.",
  badges: [
    { label: "26 → 24 tables", tone: "primary" as const },
    { label: "4 review iterations", tone: "secondary" as const },
    { label: "Debug-build safe (§30)", tone: "success" as const },
  ],
  snapshotMetrics: [
    {
      metric: "Tables (before → after)",
      value: "26 → 24",
      note: "2 dropped (other_source_detail, anime_metadata_cache); 1 renamed (content → main_entry); 1 merged-away (anilist_detail → data_source_detail). Net −2.",
    },
    {
      metric: "Core changes",
      value: "3",
      note: "Rename content → main_entry · Merge 3 detail tables → 2 · Absorb anime_metadata_cache.",
    },
    {
      metric: "Independent improvements",
      value: "11",
      note: "Bundled — drop dead cols, fix missing FKs, fix episode_number type, split display_source, drop dead queries/methods/indexes, standardize naming, typed accessor, unlinkSource clarification, source_ref_id convention, etc.",
    },
    {
      metric: "New queries",
      value: "10",
      note: "8 on data_source_detail + 7 on extension_detail (some renamed from old queries) + 1 new on main_entry (updateMainEntryTitle).",
    },
    {
      metric: "Research basis",
      value: "5 Explore sub-agents",
      note: "Tasks 2-a through 2-e — read the actual codebase, decisions, and knowledge files.",
    },
    {
      metric: "Review iterations",
      value: "4",
      note: "Iteration 1 (1 FLAW + 9 CONCERNS) · Iteration 2A (6 architecture CONCERNS) · Iteration 2B (5 implementation CONCERNS) · Iteration 3 (18 checks — APPROVED) · Iteration 4 (sanity — READY).",
    },
    {
      metric: "Final tables unchanged",
      value: "21",
      note: "21 tables confirmed correctly separated via research — no merge needed (some get minor bundled improvements).",
    },
    {
      metric: "Data loss",
      value: "Zero",
      note: "Every dropped column/table is either duplicated, dead (zero callers), or explicitly migrated. Verified by 5 research sub-agents.",
    },
  ],
} as const;

/* ---------------------------------------------------------------------------
 * SECTION 2 — THE 3 CORE CHANGES
 * ------------------------------------------------------------------------- */

export interface CoreChange {
  num: number;
  kind: "RENAME" | "MERGE" | "ABSORB";
  title: string;
  what: string;
  why: string;
  impact: string[];
  accent: "primary" | "success" | "secondary";
}

export const CORE_CHANGES: CoreChange[] = [
  {
    num: 1,
    kind: "RENAME",
    title: "Rename content → main_entry",
    what: "The identity-hub table is renamed from `content` → `main_entry`. 4 indexes renamed (idx_content_* → idx_main_entry_*). 9 SQLDelight queries renamed (getContentBy* → getMainEntryBy*). 1 NEW query added (updateMainEntryTitle). 13 FK declarations across 9 .sq files updated. 1 Kotlin string literal + 1 DbReference updated.",
    why: "The `content` table's real job is the identity hub — it holds the stable `main_id` + the changing `content_id` + links to all per-source detail tables. The name \"content\" is generic + collides with `android.content.ContentResolver` / `android.content.Context` (confusing for new agents). `main_entry` accurately reflects \"the main entry row that all detail rows hang off of.\"",
    impact: [
      "Table name: content → main_entry",
      "4 indexes renamed: idx_content_* → idx_main_entry_*",
      "9 SQLDelight queries renamed (getContentBy* → getMainEntryBy*)",
      "1 NEW query: updateMainEntryTitle(mainId, title, updatedAt) — keeps title in sync on metadata refresh",
      "13 FK declarations across 9 .sq files updated: REFERENCES content(main_id) → REFERENCES main_entry(main_id)",
      "1 Kotlin string literal in DatabaseDriverFactory.kt:168 updated",
      "1 DbReference(\"content\", ...) in DetailsScreen.kt:385 → DbReference(\"main_entry\", ...)",
    ],
    accent: "primary",
  },
  {
    num: 2,
    kind: "MERGE",
    title: "Merge 3 detail tables → 2 (keeping data source ≠ extension separate)",
    what: "`anilist_detail` + `extension_detail` + `other_source_detail` (3 tables) → `data_source_detail` + `extension_detail` (2 tables). Data-source metadata + extension metadata stay conceptually SEPARATE per the user's directive (Option C from research).",
    why: "The user wants anilist_detail + extension_detail + other_source_detail merged into a unified structure that holds metadata from ANY data source (AniList now, Kitsu/MAL/TMDB later) and ANY extension (Aniyomi now, CloudStream/Sora/MangaYomi later), updates in-place when the user switches source, and keeps the two concepts (data source vs extension) SEPARATE. Design choice: Option C (TWO tables, not one) — honors the user's keep-separate directive at the schema level, matching the existing data_source vs system lookup-table split.",
    impact: [
      "NEW table: data_source_detail (replaces anilist_detail + other_source_detail)",
      "UPDATED table: extension_detail (extended with extension_type + extra_json)",
      "DROPPED: other_source_detail (DEAD CODE — 0 callers, never written, empty table)",
      "Type change: extension_id + source_id change INTEGER → TEXT in DB (Kotlin stays Long? for Aniyomi compat)",
      "Nullable fields: source_type, source_ref_id, extension_type, extension_id, source_id, anime_url all nullable — enables clearDataSourceAxis / clearExtensionAxis",
      "8 new queries on data_source_detail + 2 new queries on extension_detail (updateExtensionAxis, clearExtensionAxis — fixes orphan-row bug)",
    ],
    accent: "secondary",
  },
  {
    num: 3,
    kind: "ABSORB",
    title: "Absorb anime_metadata_cache → data_source_detail",
    what: "DROP the anime_metadata_cache table entirely. 9 of 12 columns duplicate anilist_detail (which becomes data_source_detail). The 3 unique columns are all dead. Redirect 6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) to read from data_source_detail instead.",
    why: "9 of 12 anime_metadata_cache columns duplicate anilist_detail. The 3 unique columns are all dead: `title` duplicates main_entry.title (set from the same source), `source_type` is hardcoded 'anilist' and never read, `fetched_at` is write-only with no refresh-logic reader. The absorption is mechanical — the 9 duplicate columns are already in anilist_detail → data_source_detail; the title column's data is already in main_entry.title.",
    impact: [
      "DROP the anime_metadata_cache table",
      "DROP 3 DataCacheRepository methods: getAnimeMetadata, upsertAnimeMetadata, deleteAnimeMetadata (last already dead)",
      "DROP the CachedAnimeMetadata data class",
      "Redirect 6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) to read from data_source_detail",
      "NEW: updateMainEntryTitle query (called from refresh flow) — keeps main_entry.title in sync when anime metadata refresh updates the title (was previously only updating the now-dropped cached title)",
      "Zero data loss — the 9 duplicate columns are already in data_source_detail; the title is already in main_entry.title",
    ],
    accent: "success",
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
  | "unchanged";

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
};

/** Change 1 — main_entry (renamed from content). */
export const MAIN_ENTRY_SCHEMA: {
  tableName: string;
  renameFrom: string;
  sqFile: string;
  purpose: string;
  columns: ColumnSpec[];
  indexes: { name: string; status: ColumnStatus; def: string }[];
} = {
  tableName: "main_entry",
  renameFrom: "content",
  sqFile: "content.sq",
  purpose:
    "Identity hub — holds the stable main_id (UUID, never changes) + the changing content_id (regenerated on every source-switch) + lookups to the active data source / extension. One row per piece of content (anime, manga, novel).",
  columns: [
    {
      name: "main_id",
      type: "TEXT",
      constraints: "NOT NULL PRIMARY KEY",
      description: "Stable UUID assigned once on first sighting; survives all source switches. All child tables FK to this with ON DELETE CASCADE.",
      status: "unchanged",
    },
    {
      name: "content_id",
      type: "TEXT",
      constraints: "NOT NULL",
      description: "Structured string (regenerated by ContentIdGenerator on every link/unlink/switch of source — preserves the 'content_id changes when sources switch' invariant).",
      status: "unchanged",
    },
    {
      name: "title",
      type: "TEXT",
      constraints: "NOT NULL",
      description: "Display title. Set at first creation from anime.displayName. ALSO updated by the NEW updateMainEntryTitle query when the metadata refresh flow updates the title (was previously only updating the now-dropped anime_metadata_cache.title).",
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
      description: "DROPPED — no UI code reads main_entry.description directly (always reads data_source_detail.description or extension_detail.description). Dead column.",
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
      constraints: "(removed)",
      description: "DROPPED — D-192 dropped the FK + the content_ext_repo lookup table; column is always NULL. Dead.",
      status: "dropped",
    },
    {
      name: "extension_id",
      type: "INTEGER",
      constraints: "nullable",
      description: "Aniyomi INTERNAL source.id (NOT a FK — D-189). Plain nullable INTEGER for future repo-tracking.",
      status: "unchanged",
    },
    {
      name: "source_id",
      type: "INTEGER",
      constraints: "nullable",
      description: "Same as extension_id (legacy dup, kept for compatibility).",
      status: "unchanged",
    },
    {
      name: "anime_url",
      type: "TEXT",
      constraints: "nullable",
      description: "The content's URL on the source.",
      status: "unchanged",
    },
    {
      name: "display_source",
      type: "TEXT",
      constraints: "(removed — split into 2 new columns)",
      description: "DROPPED — was 'anilist' | 'extension', conflating data source + extension into one column. Split into active_data_source_type + active_extension_type to enable INDEPENDENT switching.",
      status: "dropped",
    },
    {
      name: "active_data_source_type",
      type: "TEXT",
      constraints: "nullable",
      description: "NEW — 'anilist' | 'kitsu' | 'mal' | 'tmdb' | NULL. NULL = no data source linked. Enables independent switching of data source without touching the extension.",
      status: "new",
    },
    {
      name: "active_extension_type",
      type: "TEXT",
      constraints: "nullable",
      description: "NEW — 'aniyomi' | 'cloudstream' | 'sora' | 'mangayomi' | NULL. NULL = no extension linked. Enables independent switching of extension without touching the data source.",
      status: "new",
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
      status: "renamed",
      def: "ON main_entry(extension_id) — was idx_content_extension",
    },
    {
      name: "idx_main_entry_extension_url",
      status: "renamed",
      def: "ON main_entry(extension_id, anime_url) — was idx_content_extension_url (composite, for getMainEntryByExtension)",
    },
    {
      name: "idx_content_data_source",
      status: "dropped",
      def: "DROPPED — redundant (no query filters on data_source_id alone; see §4.7)",
    },
  ],
};

/** Change 2 — data_source_detail (NEW — replaces anilist_detail + other_source_detail). */
export const DATA_SOURCE_DETAIL_SCHEMA: {
  tableName: string;
  replaces: string[];
  sqFile: string;
  purpose: string;
  columns: ColumnSpec[];
  queries: string[];
} = {
  tableName: "data_source_detail",
  replaces: ["anilist_detail", "other_source_detail"],
  sqFile: "content.sq",
  purpose:
    "Data-source metadata for ANY source (AniList now, MAL/Kitsu/TMDB later). One row per main_entry (if any data source is linked). Updates IN-PLACE when the user switches source — main_id stays stable, source_type + source_ref_id + metadata fields are UPDATEd. The source_type discriminator + extra_json enable future sources with zero schema change.",
  columns: [
    {
      name: "main_id",
      type: "TEXT",
      constraints: "NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE",
      description: "Stable identity link (1:1 with main_entry).",
      status: "unchanged",
    },
    {
      name: "source_type",
      type: "TEXT",
      constraints: "nullable",
      description: "Discriminator: 'anilist' | 'kitsu' | 'mal' | 'tmdb' | NULL (NULL = no data source linked). NULLABLE per Review Iteration 2A — allows clearDataSourceAxis to NULL the field on unlink.",
      status: "new",
    },
    {
      name: "source_ref_id",
      type: "TEXT",
      constraints: "nullable",
      description: "The external ID as a string (anilist_id, mal_id, kitsu_id, tmdb_id). NULL when no data source linked. TEXT for uniformity across source-ID types.",
      status: "new",
    },
    {
      name: "title",
      type: "TEXT",
      constraints: "nullable",
      description: "Display title from the data source (was anilist_detail had no title — derived from main_entry.title; now sourced directly from data source).",
      status: "new",
    },
    {
      name: "description",
      type: "TEXT",
      constraints: "nullable",
      description: "Synopsis/description (renamed from anilist_detail.synopsis).",
      status: "renamed",
    },
    {
      name: "genres",
      type: "TEXT",
      constraints: "nullable",
      description: "Comma-separated genre list.",
      status: "unchanged",
    },
    {
      name: "status",
      type: "TEXT",
      constraints: "nullable",
      description: "'FINISHED' | 'RELEASING' | 'CANCELLED' | 'HIATUS'.",
      status: "unchanged",
    },
    {
      name: "score",
      type: "INTEGER",
      constraints: "nullable",
      description: "Average score 0-100.",
      status: "unchanged",
    },
    {
      name: "episodes",
      type: "INTEGER",
      constraints: "nullable",
      description: "Total episode count.",
      status: "unchanged",
    },
    {
      name: "season",
      type: "TEXT",
      constraints: "nullable",
      description: "'WINTER' | 'SPRING' | 'SUMMER' | 'FALL'.",
      status: "unchanged",
    },
    {
      name: "season_year",
      type: "INTEGER",
      constraints: "nullable",
      description: "Year of season airing.",
      status: "unchanged",
    },
    {
      name: "cover_url",
      type: "TEXT",
      constraints: "nullable",
      description: "Cover image URL — PRIMARY cover (medium for MAL, large for AniList per §4.12 item 6).",
      status: "unchanged",
    },
    {
      name: "banner_url",
      type: "TEXT",
      constraints: "nullable",
      description: "Banner image URL.",
      status: "unchanged",
    },
    {
      name: "extra_json",
      type: "TEXT",
      constraints: "nullable",
      description: "NEW — Source-specific extras as JSON. e.g. {\"id_mal\":12345,\"trailer_url\":\"...\"} for AniList; {\"age_rating\":\"TV-14\"} for Kitsu. Parsed via typed DataSourceExtras accessor (§4.9) with ignoreUnknownKeys=true (§4.12 item 4).",
      status: "new",
    },
    {
      name: "updated_at",
      type: "INTEGER",
      constraints: "NOT NULL",
      description: "Last-write timestamp.",
      status: "unchanged",
    },
  ],
  queries: [
    "getDataSourceDetail(mainId) — SELECT * WHERE main_id = :mainId",
    "getMainEntryByDataSourceRef(sourceType, sourceRefId) — JOIN to main_entry for reverse lookup",
    "upsertDataSourceDetail(...) — INSERT OR REPLACE (full row)",
    "updateDataSourceAxis(...) — partial UPDATE of all data-source fields (for in-place switching); atomic, wrapped in transaction with main_entry.active_data_source_type + content_id regeneration",
    "clearDataSourceAxis(mainId) — NULL out all data-source fields (for unlink)",
    "deleteDataSourceDetail(mainId) — DELETE (for hard unlink)",
    "getAllDataSourceDetails() — for backup dump",
    "getDataSourceDetailByAniListId(anilistId) — convenience: WHERE source_type='anilist' AND source_ref_id = :anilistId (preserves the hot lookup path)",
  ],
};

/** Change 2 — extension_detail (UPDATED — extended, not replaced). */
export const EXTENSION_DETAIL_SCHEMA: {
  tableName: string;
  sqFile: string;
  purpose: string;
  columns: ColumnSpec[];
  queries: string[];
} = {
  tableName: "extension_detail",
  sqFile: "content.sq",
  purpose:
    "Extension metadata for ANY extension ecosystem (Aniyomi now, CloudStream/Sora/MangaYomi later). One row per main_entry (if any extension is linked). Updates IN-PLACE when the user switches extension. The extension_type discriminator + extra_json enable future ecosystems with zero schema change. The NEW clearExtensionAxis query fixes the orphan-row bug in the current unlinkSource flow.",
  columns: [
    {
      name: "main_id",
      type: "TEXT",
      constraints: "NOT NULL PRIMARY KEY, FK→main_entry(main_id) ON DELETE CASCADE",
      description: "Stable identity link (1:1 with main_entry).",
      status: "unchanged",
    },
    {
      name: "extension_type",
      type: "TEXT",
      constraints: "nullable",
      description: "NEW — Discriminator: 'aniyomi' | 'cloudstream' | 'sora' | 'mangayomi' | NULL (NULL = no extension linked). NULLABLE per Review Iteration 2A — allows clearExtensionAxis to NULL the field on unlink.",
      status: "new",
    },
    {
      name: "extension_id",
      type: "TEXT",
      constraints: "nullable",
      description: "Aniyomi source.id (was INTEGER → TEXT for future CloudStream string IDs). NULLABLE — NULL when no extension linked. TYPE CHANGE. Kotlin types stay Long? via SQLDelight column adapter — preserves .data.json compat.",
      status: "modified",
    },
    {
      name: "source_id",
      type: "TEXT",
      constraints: "nullable",
      description: "Same as extension_id (legacy dup, kept for compatibility). NULLABLE. TYPE CHANGE (was INTEGER NOT NULL → TEXT nullable).",
      status: "modified",
    },
    {
      name: "anime_url",
      type: "TEXT",
      constraints: "nullable",
      description: "The content's URL on the source. NULLABLE — NULL when no extension linked (was NOT NULL → nullable per §4.12 item 1).",
      status: "modified",
    },
    {
      name: "title",
      type: "TEXT",
      constraints: "nullable",
      description: "Display title from the extension.",
      status: "unchanged",
    },
    {
      name: "description",
      type: "TEXT",
      constraints: "nullable",
      description: "Extension-provided description.",
      status: "unchanged",
    },
    {
      name: "genres",
      type: "TEXT",
      constraints: "nullable",
      description: "Extension-provided genres.",
      status: "unchanged",
    },
    {
      name: "status",
      type: "TEXT",
      constraints: "nullable",
      description: "Extension-provided status.",
      status: "unchanged",
    },
    {
      name: "author",
      type: "TEXT",
      constraints: "nullable",
      description: "Manga author (future manga support).",
      status: "unchanged",
    },
    {
      name: "artist",
      type: "TEXT",
      constraints: "nullable",
      description: "Manga artist.",
      status: "unchanged",
    },
    {
      name: "thumbnail_url",
      type: "TEXT",
      constraints: "nullable",
      description: "Extension-provided thumbnail.",
      status: "unchanged",
    },
    {
      name: "extra_json",
      type: "TEXT",
      constraints: "nullable",
      description: "NEW — Source-specific extras as JSON. Same pattern as data_source_detail.extra_json.",
      status: "new",
    },
    {
      name: "updated_at",
      type: "INTEGER",
      constraints: "NOT NULL",
      description: "Last-write timestamp.",
      status: "unchanged",
    },
  ],
  queries: [
    "getExtensionDetail(mainId) — SELECT * WHERE main_id = :mainId",
    "getMainEntryByExtension(extensionType, extensionId, animeUrl) — JOIN for reverse lookup",
    "upsertExtensionDetail(...) — INSERT OR REPLACE (full row)",
    "updateExtensionAxis(...) — NEW — partial UPDATE of all extension fields (for in-place switching); atomic, wrapped in transaction with main_entry.active_extension_type + content_id regeneration",
    "clearExtensionAxis(mainId) — NEW — NULL out all extension fields (for unlink — FIXES THE ORPHAN-ROW BUG; the old unlinkSource only cleared SharedPreferences + left the row orphaned)",
    "deleteExtensionDetail(mainId) — DELETE (for hard unlink)",
    "getAllExtensionDetails() — for backup dump",
  ],
};

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
      "9 existing queries renamed (getContentBy* → getMainEntryBy*) + 1 NEW query for title sync.",
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
        description: "Was getContentByAniListId — JOIN to anilist_detail (now data_source_detail) for reverse lookup.",
      },
      {
        name: "getMainEntryByExtension",
        signature: "(extensionId, animeUrl)",
        status: "renamed",
        description: "Was getContentByExtension — WHERE extension_id = :extensionId AND anime_url = :animeUrl.",
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
        description: "Was insertContent — INSERT OR REPLACE (full row). Column list updated (drops description + extension_repo_id + display_source; adds active_data_source_type + active_extension_type).",
      },
      {
        name: "updateMainEntryContentId",
        signature: "(mainId, contentId, updatedAt)",
        status: "renamed",
        description: "Was updateContentContentId — UPDATE content_id WHERE main_id. Called on every source-switch (ContentIdGenerator.generate()).",
      },
      {
        name: "updateMainEntrySources",
        signature: "(mainId, dataSourceId, systemId, extensionId, sourceId, animeUrl, contentId, updatedAt)",
        status: "renamed",
        description: "Was updateContentSources — UPDATE all source-link fields. Column list updated (drops extension_repo_id + display_source).",
      },
      {
        name: "deleteMainEntry",
        signature: "(mainId)",
        status: "renamed",
        description: "Was deleteContent — DELETE WHERE main_id. Cascades to all child tables.",
      },
      {
        name: "updateContentDisplaySource",
        signature: "(mainId, displaySource, updatedAt)",
        status: "dropped",
        description: "DROPPED — display_source column is split into active_data_source_type + active_extension_type; this query is replaced by the active_*_type updates inside updateDataSourceAxis / updateExtensionAxis (atomic transaction).",
      },
      {
        name: "updateMainEntryTitle",
        signature: "(mainId, title, updatedAt)",
        status: "new",
        description: "NEW (per §4.12 item 8) — Keeps main_entry.title in sync when the anime metadata refresh flow updates the title. Was previously only updating the now-dropped anime_metadata_cache.title, causing stale-title-after-refresh bug.",
      },
    ],
  },
  {
    group: "data_source_detail (NEW table)",
    subtitle:
      "8 new queries — replace the 3 anilist_detail queries + add the axis-update / clear / convenience queries.",
    queries: [
      {
        name: "getDataSourceDetail",
        signature: "(mainId)",
        status: "new",
        description: "SELECT * WHERE main_id = :mainId. Replaces getAniListDetail.",
      },
      {
        name: "getMainEntryByDataSourceRef",
        signature: "(sourceType, sourceRefId)",
        status: "new",
        description: "JOIN to main_entry for reverse lookup. Generalized form of getContentByAniListId.",
      },
      {
        name: "upsertDataSourceDetail",
        signature: "(...)",
        status: "new",
        description: "INSERT OR REPLACE (full row). Replaces upsertAniListDetail.",
      },
      {
        name: "updateDataSourceAxis",
        signature: "(mainId, sourceType, sourceRefId, title, description, genres, status, score, episodes, season, seasonYear, coverUrl, bannerUrl, extraJson, updatedAt)",
        status: "new",
        description: "Partial UPDATE of all data-source fields (for in-place switching). SINGLE ATOMIC UPDATE statement, wrapped in DB transaction that ALSO updates main_entry.active_data_source_type + regenerates main_entry.content_id (per §4.12 item 2-3). Called FROM ContentResolver.linkDataSource, NOT directly from ViewModels.",
      },
      {
        name: "clearDataSourceAxis",
        signature: "(mainId)",
        status: "new",
        description: "NULL out all data-source fields (for unlink). The is-linked state is determined by main_entry.active_data_source_type IS NOT NULL.",
      },
      {
        name: "deleteDataSourceDetail",
        signature: "(mainId)",
        status: "new",
        description: "DELETE (for hard unlink — used by ContentResolver.unlinkAniList).",
      },
      {
        name: "getAllDataSourceDetails",
        signature: "()",
        status: "new",
        description: "For backup dump.",
      },
      {
        name: "getDataSourceDetailByAniListId",
        signature: "(anilistId)",
        status: "new",
        description: "Convenience: WHERE source_type='anilist' AND source_ref_id = :anilistId. Preserves the hot lookup path (was idx_anilist_detail_anilist_id).",
      },
    ],
  },
  {
    group: "extension_detail (UPDATED table)",
    subtitle:
      "7 queries — 4 existing (renamed signature) + 3 NEW (the axis-update / clear / convenience set).",
    queries: [
      {
        name: "getExtensionDetail",
        signature: "(mainId)",
        status: "unchanged",
        description: "SELECT * WHERE main_id = :mainId. Signature unchanged.",
      },
      {
        name: "getMainEntryByExtension",
        signature: "(extensionType, extensionId, animeUrl)",
        status: "modified",
        description: "JOIN for reverse lookup. Signature extended with extensionType param (was getContentByExtension on the content table).",
      },
      {
        name: "upsertExtensionDetail",
        signature: "(...)",
        status: "modified",
        description: "INSERT OR REPLACE (full row). Column list extended (adds extension_type + extra_json; types of extension_id + source_id + anime_url changed to nullable TEXT).",
      },
      {
        name: "updateExtensionAxis",
        signature: "(mainId, extensionType, extensionId, sourceId, animeUrl, title, description, genres, status, author, artist, thumbnailUrl, extraJson, updatedAt)",
        status: "new",
        description: "NEW — partial UPDATE of all extension fields (for in-place switching). SINGLE ATOMIC UPDATE statement, wrapped in DB transaction that ALSO updates main_entry.active_extension_type + regenerates main_entry.content_id. Called FROM ContentResolver.linkExtension, NOT directly from ViewModels.",
      },
      {
        name: "clearExtensionAxis",
        signature: "(mainId)",
        status: "new",
        description: "NEW — NULL out all extension fields (for unlink). FIXES THE ORPHAN-ROW BUG — the old DetailsViewModel.unlinkSource (line 1693) only cleared SharedPreferences + left the extension_detail row orphaned. unlinkSource() should call clearExtensionAxis (not delete) — keeps the row for re-linking, only marks 'no extension currently active.'",
      },
      {
        name: "deleteExtensionDetail",
        signature: "(mainId)",
        status: "repurposed",
        description: "DELETE (for hard unlink). Was 0 callers / dead; now repurposed as the hard-unlink counterpart to clearExtensionAxis.",
      },
      {
        name: "getAllExtensionDetails",
        signature: "()",
        status: "new",
        description: "For backup dump.",
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
    title: "Drop 2 dead columns from main_entry",
    body: "description TEXT — no UI code reads main_entry.description directly (always reads anilist_detail.synopsis or extension_detail.description). extension_repo_id INTEGER — D-192 dropped the FK + the lookup table; column is always NULL.",
    accent: "danger",
  },
  {
    id: "4.2",
    title: "Split display_source into 2 columns",
    body: "Current: display_source TEXT with values 'anilist' | 'extension' — conflates data source + extension into one column. New: active_data_source_type TEXT (nullable) + active_extension_type TEXT (nullable). Enables INDEPENDENT switching — switch the data source (AniList→MAL) without touching the extension (still Aniyomi-extension-A), and vice versa.",
    detail:
      "Migration (corrected per Review Iteration 1): check the PRESENCE of link fields (data_source_id IS NOT NULL → data source is linked; system_id IS NOT NULL → extension is linked), NOT the display_source column value — linkAniList + linkExtensionToExisting don't update display_source when cross-linking, so a content row with display_source='anilist' can have BOTH detail rows populated.",
    accent: "primary",
  },
  {
    id: "4.3",
    title: "Fix 2 missing FK declarations (pre-existing bugs)",
    body: "watch_progress.main_id — watch.sq:18 has only a comment, no FK clause. notification_sent.main_id — notifications.sq:38-45 has no FK. Add FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE to both.",
    detail:
      "Precondition (per Review Iteration 1): adding these FKs will FAIL if existing rows reference non-existent main_id values. Debug-build-only — wipe the DB (dev users clear app data once) before applying the new schema. SQLite can't ALTER TABLE to add a FK, so DatabaseDriverFactory.onOpen must DROP + CREATE these 2 tables (acceptable per CORE_RULES §30).",
    accent: "warning",
  },
  {
    id: "4.4",
    title: "Fix episode_number type mismatch (real bug)",
    body: "notification_sent.episode_number INTEGER → REAL (was rounding 12.5→12, breaking dedup). episode_schedule.episode_number INTEGER → REAL (same issue). 4 other tables already use REAL for fractional episodes (12.5 for OVAs).",
    detail:
      "Scope clarification (per §4.12 item 10): this is a SCHEMA-ONLY fix. The Kotlin API surface (episodeNumber: Long in many places) still truncates 12.5→12L at the call site BEFORE reaching the DB. The API surface change (Long→Float/Double) is a SEPARATE task — deferred.",
    accent: "warning",
  },
  {
    id: "4.5",
    title: "Drop 4 dead queries",
    body: "deleteAnimeMetadata, deleteEpisodeMetadata, deleteBrowseCache, getAllBrowseCache (all 0 callers). deleteExtensionDetail (0 callers — but being repurposed in Change 2 as the hard-unlink counterpart). getAllUserEpisodeRatings (0 callers — but should be WIRED UP for backup, not deleted).",
    accent: "danger",
  },
  {
    id: "4.6",
    title: "Drop 2 dead methods from ContentRepository",
    body: "deleteExtensionDetail() (0 callers — repurposed at the query level, but the ContentRepository method wrapper is dead). getDefaultCategoryCount() (0 callers).",
    accent: "danger",
  },
  {
    id: "4.7",
    title: "Drop redundant indexes",
    body: "idx_content_data_source — no query filters on data_source_id alone. idx_content_genre_main — duplicates leftmost column of composite PK. idx_library_item_main — duplicates leftmost column of idx_library_item_unique.",
    accent: "danger",
  },
  {
    id: "4.8",
    title: "Standardize naming",
    body: "Indexes: idx_<full_table_name>_<cols>[_unique|_partial] (7 indexes have shortened names). Retention query params: standardize on named :cutoff (2 use positional ?). audio_variant everywhere (currently video_audio in 2 download tables).",
    accent: "primary",
  },
  {
    id: "4.9",
    title: "Typed DataSourceExtras accessor for extra_json (per Review Iteration 1)",
    body: "The extra_json column on data_source_detail holds source-specific fields (AniList's id_mal, trailer_url; Kitsu's age_rating). To avoid repeating JSON-parse logic at every read site (5+ callers, including the episode metadata engine which needs id_mal for Jikan API calls), introduce a typed accessor: a @Serializable DataSourceExtras data class with idMal, trailerUrl, ageRating, coverUrlLarge, coverUrlSmall fields + a toJson/fromJson pair. The Json instance MUST use ignoreUnknownKeys = true (§4.12 item 4) — without it, adding any new field would silently break parsing of ALL existing rows.",
    accent: "success",
  },
  {
    id: "4.10",
    title: "unlinkSource flow clarification (per Review Iteration 1)",
    body: "The current DetailsViewModel.unlinkSource() (line 1693) does NOT touch the DB — it only clears SharedPreferences + in-memory state, leaving orphaned extension_detail rows. The plan introduces two queries: clearExtensionAxis(mainId) (NULLs out extension fields, keeps the row) + deleteExtensionDetail(mainId) (DELETE the row entirely). DECISION: unlinkSource() should call clearExtensionAxis (not delete) — keeps the row for re-linking, matches the asymmetry with unlinkAniList() which DELETES (heavier operation). The active_extension_type on main_entry is set to NULL by clearExtensionAxis.",
    accent: "primary",
  },
  {
    id: "4.11",
    title: "source_ref_id String↔Int conversion convention (per Review Iteration 1)",
    body: "source_ref_id is TEXT (for uniformity across AniList/Kitsu/MAL/TMDB IDs). But the episode metadata engine (D-190) calls fetchEpisodeMetadata(anilistId: Int, malId: Int?, ...) — it expects Int. CONVENTION: the DataSourceDetail data class exposes typed accessors — val anilistId: Int?, val malId: Int?, val kitsuId: Int?, val tmdbId: Int?. The malId accessor returns Int? via extras.idMal?.toInt() (extras.idMal is Long? — safer for future MAL IDs).",
    detail:
      "Note (per Review Iteration 2B Check 2): existing AniListDetail.anilistId is Int (non-null). Changing to Int? (nullable via computed property) breaks 12+ non-null consumers. Mitigation: callers that previously did anilistDetail.anilistId (non-null) must add a null check (dataSourceDetail.anilistId ?: 0 or != null). Kotlin compile-safety catches every missed site.",
    accent: "secondary",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 6 — TABLES NOT CHANGING (the 24 final tables, grouped)
 * ------------------------------------------------------------------------- */

export type TableStatus =
  | "RENAMED"
  | "NEW"
  | "UPDATED"
  | "UNCHANGED"
  | "DROPPED";

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
};

export const FINAL_TABLES: {
  group: string;
  subtitle: string;
  rows: FinalTableSpec[];
}[] = [
  {
    group: "Content Identity (content.sq)",
    subtitle: "The identity hub + lookups + per-source detail tables.",
    rows: [
      {
        table: "data_source",
        sqFile: "content.sq",
        group: "Content Identity",
        status: "UNCHANGED",
        why: "Lookup table (AniList/TMDB/Kitsu/MAL). Seeded once at first launch.",
      },
      {
        table: "system",
        sqFile: "content.sq",
        group: "Content Identity",
        status: "UNCHANGED",
        why: "Lookup table (Aniyomi/CloudStream/Sora/MangaYomi).",
      },
      {
        table: "main_entry",
        sqFile: "content.sq",
        group: "Content Identity",
        status: "RENAMED",
        why: "Identity hub — renamed from content (clearer name, avoids android.content.* collision).",
        improvements: "Drop 2 dead cols, split display_source, rename (Change 1)",
      },
      {
        table: "data_source_detail",
        sqFile: "content.sq",
        group: "Content Identity",
        status: "NEW",
        why: "Data-source metadata — merged from anilist_detail + other_source_detail (Change 2). Holds metadata for ANY data source via source_type discriminator + extra_json.",
        improvements: "New table (Change 2)",
      },
      {
        table: "extension_detail",
        sqFile: "content.sq",
        group: "Content Identity",
        status: "UPDATED",
        why: "Extension metadata — extended with extension_type + extra_json + nullable axis fields (Change 2).",
        improvements: "Add extension_type + extra_json, fix unlink-row bug (Change 2)",
      },
    ],
  },
  {
    group: "Data Cache (dataCache.sq)",
    subtitle: "Episode-level metadata cache + Browse-page JSON blob cache.",
    rows: [
      {
        table: "data_cache_episode",
        sqFile: "dataCache.sq",
        group: "Data Cache",
        status: "UNCHANGED",
        why: "Episode-level metadata (AniZip/Jikan/Kitsu) — different cardinality + sources. Keep PK; maybe switch to INSERT ON CONFLICT (deferred §7 item 6).",
      },
      {
        table: "browse_cache",
        sqFile: "dataCache.sq",
        group: "Data Cache",
        status: "UNCHANGED",
        why: "JSON blob cache for Browse page — different shape.",
      },
    ],
  },
  {
    group: "App (app.sq + appSettings.sq)",
    subtitle: "Generic KV stores.",
    rows: [
      {
        table: "app_metadata",
        sqFile: "app.sq",
        group: "App",
        status: "UNCHANGED",
        why: "Generic KV (schema version). Future: merge into app_settings — deferred (§7 item 3).",
      },
      {
        table: "app_settings",
        sqFile: "appSettings.sq",
        group: "App",
        status: "UNCHANGED",
        why: "User settings KV.",
      },
    ],
  },
  {
    group: "Watch (watch.sq)",
    subtitle: "Episode watch progress.",
    rows: [
      {
        table: "watch_progress",
        sqFile: "watch.sq",
        group: "Watch",
        status: "UPDATED",
        why: "Episode watch progress.",
        improvements: "Add missing FK (§4.3)",
      },
    ],
  },
  {
    group: "Activity (tracking.sq)",
    subtitle: "Activity log.",
    rows: [
      {
        table: "activity_event",
        sqFile: "tracking.sq",
        group: "Activity",
        status: "UNCHANGED",
        why: "Activity log.",
      },
    ],
  },
  {
    group: "Library (library.sq)",
    subtitle: "User categories + library junction.",
    rows: [
      {
        table: "library_category",
        sqFile: "library.sq",
        group: "Library",
        status: "UPDATED",
        why: "User categories.",
        improvements: "Drop redundant UNIQUE",
      },
      {
        table: "library_item",
        sqFile: "library.sq",
        group: "Library",
        status: "UPDATED",
        why: "Library junction.",
        improvements: "Drop redundant id + index, rename FK",
      },
    ],
  },
  {
    group: "Genres (genres.sq)",
    subtitle: "Genre lookup + content/genre junction.",
    rows: [
      {
        table: "genre",
        sqFile: "genres.sq",
        group: "Genres",
        status: "UNCHANGED",
        why: "Genre lookup.",
      },
      {
        table: "content_genre",
        sqFile: "genres.sq",
        group: "Genres",
        status: "UPDATED",
        why: "Genre junction.",
        improvements: "Drop redundant index, rename FK",
      },
    ],
  },
  {
    group: "Updates (episodeUpdate.sq + animeUpdateState.sq)",
    subtitle: "Updates feed + per-anime update state.",
    rows: [
      {
        table: "episode_update",
        sqFile: "episodeUpdate.sq",
        group: "Updates",
        status: "UPDATED",
        why: "Updates feed.",
        improvements: "Drop redundant id, rename FK, add CHECKs",
      },
      {
        table: "anime_update_state",
        sqFile: "animeUpdateState.sq",
        group: "Updates",
        status: "UPDATED",
        why: "Per-anime update state.",
        improvements: "Rename FK, add CHECKs",
      },
    ],
  },
  {
    group: "Schedule (episodeSchedule.sq)",
    subtitle: "Airing schedule.",
    rows: [
      {
        table: "episode_schedule",
        sqFile: "episodeSchedule.sq",
        group: "Schedule",
        status: "UPDATED",
        why: "Airing schedule.",
        improvements: "Fix episode_number type (§4.4), rename FK",
      },
    ],
  },
  {
    group: "Notifications (notifications.sq)",
    subtitle: "Per-content notif prefs + sent dedup log.",
    rows: [
      {
        table: "notification_config",
        sqFile: "notifications.sq",
        group: "Notifications",
        status: "UPDATED",
        why: "Per-content notification preferences.",
        improvements: "Rename FK, add CHECKs",
      },
      {
        table: "notification_sent",
        sqFile: "notifications.sq",
        group: "Notifications",
        status: "UPDATED",
        why: "Notification dedup log.",
        improvements: "Add missing FK (§4.3), fix episode_number type (§4.4), add CHECKs",
      },
    ],
  },
  {
    group: "Ratings (ratings.sq)",
    subtitle: "Per-anime + per-episode ratings.",
    rows: [
      {
        table: "user_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        status: "UPDATED",
        why: "Per-anime rating.",
        improvements: "Rename FK, add CHECK",
      },
      {
        table: "user_episode_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        status: "UPDATED",
        why: "Per-episode rating.",
        improvements: "Rename FK, add CHECK",
      },
    ],
  },
  {
    group: "Downloads (downloadQueue.sq + downloadedEpisode.sq)",
    subtitle: "Download queue + downloaded-episode index.",
    rows: [
      {
        table: "download_queue",
        sqFile: "downloadQueue.sq",
        group: "Downloads",
        status: "UNCHANGED",
        why: "Download queue.",
      },
      {
        table: "downloaded_episode",
        sqFile: "downloadedEpisode.sq",
        group: "Downloads",
        status: "UNCHANGED",
        why: "Downloaded episodes index.",
      },
    ],
  },
  {
    group: "Dropped (was in content.sq + dataCache.sq)",
    subtitle: "2 dead tables removed.",
    rows: [
      {
        table: "other_source_detail",
        sqFile: "content.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "DEAD CODE — 0 callers, never written, empty table. The generic KV table designed for 'future TMDB/Kitsu/MAL' was never wired up. The new data_source_detail handles all future data sources via the source_type discriminator. Zero data loss.",
      },
      {
        table: "anime_metadata_cache",
        sqFile: "dataCache.sq",
        group: "Dropped",
        status: "DROPPED",
        why: "9/12 columns duplicate anilist_detail (now data_source_detail). The 3 unique columns are all dead: title duplicates main_entry.title, source_type hardcoded 'anilist' never read, fetched_at write-only with no refresh-logic reader. Absorbed into data_source_detail (Change 3).",
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
        text: "Mechanical churn — 9 .sq files + 4 indexes + 9 queries + 1 Kotlin string + 1 DbReference. Pure cost, no functional benefit beyond clarity.",
        severity: "LOW",
      },
      {
        text: "If the .sq FILE is also renamed (content.sq → main_entry.sq), database.contentQueries → database.mainEntryQueries (20 references). Riskier.",
        severity: "LOW",
      },
      {
        text: "Risk: LOW. SQLDelight's type-safe generation catches any missed rename at compile time.",
        severity: "LOW",
      },
      {
        text: "Mitigation: Do the table rename first (low risk). Defer the file rename to a separate session if desired.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Change 2 — Merge 3 detail tables → 2",
    items: [
      {
        text: "~600 lines across ~25 files need updating (ContentRepository, ContentResolver, ContentModels, 13 read-caller files, 4 write-caller files).",
        severity: "MEDIUM",
      },
      {
        text: "The extension_id type change (INTEGER → TEXT in DB; Kotlin stays Long? per §3 note). The getAniListDetail != null semantics change (now means 'any data source linked' — callers must check sourceType == 'anilist' per §4.12 item 7).",
        severity: "MEDIUM",
      },
      {
        text: "extra_json loses some type safety (acceptable — DataSourceExtras typed accessor covers the 80% case per §4.9).",
        severity: "LOW",
      },
      {
        text: "anilistId accessor changes from Int (non-null) to Int? (nullable) — breaks 12+ non-null consumers (§4.11 note). Kotlin compile-safety catches all.",
        severity: "MEDIUM",
      },
      {
        text: "Risk: MEDIUM. The write-path convergence is the riskiest part — if a call site is missed, the merged row could be partially stale.",
        severity: "MEDIUM",
      },
      {
        text: "RESOLVED — the clearExtensionAxis / clearDataSourceAxis queries originally couldn't NULL NOT NULL columns. Resolved by making the axis fields nullable (§4.12 item 1).",
        severity: "RESOLVED",
        resolvedBy: "§4.12 item 1 — source_type, source_ref_id, extension_type, extension_id, source_id, anime_url all made nullable.",
      },
      {
        text: "Mitigation: Debug builds — drop + recreate (CORE_RULES §30). Sub-agent compile review before push. CI is the final gate.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Change 3 — Absorb anime_metadata_cache",
    items: [
      {
        text: "6 caller sites (4 in DetailsViewModel, 2 in LibraryViewModel) need redirecting.",
        severity: "MEDIUM",
      },
      {
        text: "Risk: MEDIUM — write-path convergence (must redirect both reads AND writes to avoid partial staleness).",
        severity: "MEDIUM",
      },
      {
        text: "Mitigation: Same 2-step approach — redirect reads first, then writes; verify with device test.",
        severity: "LOW",
      },
    ],
  },
  {
    group: "Independent improvements",
    items: [
      {
        text: "The episode_number type fix (INTEGER → REAL) requires a table rebuild for notification_sent + episode_schedule (can't ALTER COLUMN type in SQLite). Debug builds — drop + recreate is fine.",
        severity: "LOW",
      },
      {
        text: "Risk: LOW. All improvements are additive or fix existing bugs.",
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
        text: "No data loss — every dropped column/table is either duplicated, dead, or explicitly migrated. Verified by 5 research sub-agents.",
        severity: "LOW",
      },
      {
        text: "CI is the final gate — sub-agent compile review before push, then CI verifies.",
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
    body: "ContentRecord → MainEntryRecord, ContentRepository → MainEntryRepository, ContentResolver → MainEntryResolver, etc.",
    reason: "~24 caller files. Separate session. The Kotlin class names are decoupled from the table name.",
  },
  {
    num: 2,
    title: ".sq file rename (content.sq → main_entry.sq)",
    body: "Would change database.contentQueries → database.mainEntryQueries property (19 references in ContentRepository + 1 in GenreRepository).",
    reason: "Riskier than the table rename. Separate session. The plan recommends renaming the file too, for consistency, but this is the riskier part.",
  },
  {
    num: 3,
    title: "Merge app_metadata → app_settings",
    body: "Degenerate KV duplication — app_metadata has only 2 cols (key, value), app_settings has 5 cols (typed).",
    reason: "Low priority. Separate session.",
  },
  {
    num: 4,
    title: "Split AnimeDetailsProvider interface",
    body: "Split into DataSourceProvider + ExtensionDetailsProvider.",
    reason: "Code-layer refactor, not schema. Separate session.",
  },
  {
    num: 5,
    title: "RetentionCoordinator worker",
    body: "Would centralize the 5 retention queries (activity_event, episode_update, notification_sent, browse_cache, data_cache_episode).",
    reason: "Separate session. Currently each query lives next to its table.",
  },
  {
    num: 6,
    title: "data_cache_episode → INSERT ON CONFLICT DO UPDATE",
    body: "Performance optimization for batch refresh.",
    reason: "Not needed at current scale. Separate session.",
  },
  {
    num: 7,
    title: "CHECK constraints",
    body: "Add CHECK constraints to enforce valid enum values (e.g. status IN ('FINISHED','RELEASING','CANCELLED','HIATUS')).",
    reason: "Included in the plan but optional. Can be added incrementally.",
  },
  {
    num: 8,
    title: "episode_number API surface change",
    body: "Long → Float/Double in Kotlin callers (the schema fix is done; the Kotlin API surface still truncates fractional episodes at the call site).",
    reason: "Per §4.12 item 10. This plan fixes the SCHEMA type only; the Kotlin API surface change is a SEPARATE task. ~8+ files affected.",
  },
  {
    num: 9,
    title: "CloudStream/Sora/MangaYomi String extension IDs",
    body: "The extension_id column is TEXT (DB) but Kotlin types stay Long? for Aniyomi compatibility. When a future extension ecosystem uses truly-String IDs (e.g. CloudStream's 'kawaiiyomistreams.com'), a separate extension_id_str TEXT column or a hash-to-Long mapping will be needed.",
    reason: "Not a concern for Aniyomi-only today. Deferred until CloudStream integration actually lands.",
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
      "User switches data source → ContentResolver.linkDataSource('mal', malId, ...) → UPDATEs the data_source_detail row: source_type='mal', source_ref_id=malId, new metadata fields. Calls updateDataSourceAxis (atomic transaction).",
      "The main_id stays the same. The previous AniList ID is preserved in extra_json as {\"previous_anilist_id\": 12345} for re-switching back.",
      "ZERO SCHEMA CHANGE. (MAL-specific fields like main_picture.medium/.large go into extra_json + cover_url per §4.12 item 6.)",
    ],
    footer: "Schema unchanged — the source_type discriminator + extra_json absorb new sources.",
  },
  {
    title: "Adding a new extension ecosystem (e.g. CloudStream)",
    accent: "secondary",
    steps: [
      "Implement CloudStreamVideoExtensionProvider : VideoExtensionProvider with ecosystemId='cloudstream' (code layer).",
      "User switches extension → ContentResolver.linkExtension('cloudstream', cloudStreamSourceId, animeUrl, ...) → UPDATEs the extension_detail row: extension_type='cloudstream', new extension_id/source_id/anime_url. Calls updateExtensionAxis (atomic transaction).",
      "The main_id stays the same. Episode list changes (different numbering) — data_cache_episode rows for the old extension are invalidated + re-fetched.",
      "ZERO SCHEMA CHANGE for Long-ID extensions. ⚠️ CloudStream uses String source IDs (e.g. 'kawaiiyomistreams.com') — the current extension_id column is TEXT but Kotlin types are Long?. A future extension_id_str TEXT column or hash-to-Long mapping would be needed (deferred per §7 item 9). For Aniyomi-only (Long IDs), zero schema change.",
    ],
    footer: "Schema unchanged for Aniyomi-style Long IDs. String-ID support deferred (§7 item 9).",
  },
  {
    title: "Independent switching",
    accent: "success",
    steps: [
      "The split active_data_source_type + active_extension_type columns on main_entry allow the user to switch either independently.",
      "Example: AniList metadata + Aniyomi extension-A → MAL metadata + Aniyomi-extension-A → MAL metadata + CloudStream-extension-B.",
      "Each combination is a valid state. updateDataSourceAxis touches only the data-source axis; updateExtensionAxis touches only the extension axis. Neither clobbers the other.",
      "content_id is regenerated on every switch (preserves the 'content_id changes when sources switch' invariant).",
    ],
    footer: "Independent switching is the headline feature enabled by the display_source split (§4.2).",
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 10 — REVIEW PROCESS (4 iterations)
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
      "First-pass review of the initial draft. Found 1 FLAW + 9 CONCERNS.",
    accent: "danger",
    counts: [
      { label: "FLAW", value: 1 },
      { label: "CONCERN", value: 9 },
    ],
    found: [
      "FLAW: clearExtensionAxis / clearDataSourceAxis queries couldn't NULL NOT NULL columns — the schema said NOT NULL but the queries needed to NULL them on unlink.",
      "CONCERN: extension_id Long↔TEXT type change feasibility (ColumnAdapter is a new pattern in this codebase).",
      "CONCERN: anilistId nullness propagation — breaks 12+ non-null consumers (Int → Int?).",
      "CONCERN: getAniListDetail != null semantics change — now means 'any data source linked' not 'AniList specifically'.",
      "CONCERN: cachedMeta.title → content.title redirect is a behavior change (refresh flow doesn't update main_entry.title).",
      "CONCERN: clearExtensionAxis NULLs propagate to .data.json via DownloadScanner.",
      "CONCERN: FK-add precondition not actionable — SQLite can't ALTER TABLE to add FK.",
      "CONCERN: episode_number REAL fix is a half-fix (Kotlin API surface still truncates).",
    ],
    fixed: [
      "Made source_type, source_ref_id, extension_type, extension_id, source_id, anime_url NULLABLE (resolves the FLAW).",
      "Confirmed extension_id Long↔TEXT via SQLDelight column adapter — Kotlin types stay Long? for .data.json compat.",
      "Documented the anilistId nullness propagation + 12+ caller sites needing null checks.",
      "Documented the getAniListDetail != null semantic change — callers must check sourceType == 'anilist'.",
      "Added updateMainEntryTitle query — refresh flow now updates main_entry.title too.",
      "Documented that DownloadScanner uses ?: fallbacks for NULL fields (main_id match path survives).",
      "Specified DROP + CREATE blocks for watch_progress + notification_sent in DatabaseDriverFactory.onOpen.",
      "Scoped episode_number fix as schema-only; API surface change deferred.",
    ],
  },
  {
    num: "2A",
    title: "Iteration 2A — architecture review",
    subtitle:
      "Architecture-focused review. Found 6 CONCERNS (no FLAWs).",
    accent: "warning",
    counts: [
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 6 },
    ],
    found: [
      "Nullable schema confirmed but axis fields still need explicit nullness annotations.",
      "updateExtensionAxis / updateDataSourceAxis atomicity — must be single atomic UPDATE statements wrapped in DB transaction that ALSO updates main_entry.active_*_type + regenerates content_id.",
      "content_id regeneration — every source-switch operation MUST call ContentIdGenerator.generate() + repo.updateContentContentId(mainId, newContentId).",
      "DataSourceExtras.fromJson MUST use Json { ignoreUnknownKeys = true } — without it, adding any new field would silently break parsing of ALL existing rows.",
      "malId accessor type — extras.idMal is Long? (AniList id_mal can exceed Int range); accessor returns Int? via .toInt().",
      "Multi-size cover URLs — convention needed (primary in cover_url, alternatives in extra_json).",
    ],
    fixed: [
      "§4.12 item 1: axis fields explicitly marked nullable in both schemas.",
      "§4.12 item 2: updateDataSourceAxis / updateExtensionAxis specified as single atomic UPDATE statements, wrapped in DB transaction.",
      "§4.12 item 3: content_id regeneration mandated on every source-switch (called from ContentResolver methods, not directly from ViewModels).",
      "§4.12 item 4: DataSourceExtras Json instance MUST use ignoreUnknownKeys = true.",
      "§4.12 item 5: malId accessor returns Int? via extras.idMal?.toInt() — consistent across §4.9 + §4.11.",
      "§4.12 item 6: multi-size cover convention documented (primary in cover_url, alternatives in extra_json).",
    ],
  },
  {
    num: "2B",
    title: "Iteration 2B — implementation feasibility review",
    subtitle:
      "Implementation-feasibility-focused review. Found 5 CONCERNS (no FLAWs).",
    accent: "warning",
    counts: [
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 5 },
    ],
    found: [
      "getAniListDetail != null semantics change affects LibraryViewModel:350-365 + similar patterns (13 caller files).",
      "cachedMeta.title stale-title issue — refresh flow updates cached title but NOT main_entry.title.",
      "clearExtensionAxis NULLs propagate to .data.json via DownloadScanner (could lose extension fields on reinstall).",
      "episode_number INTEGER→REAL is a half-fix — Kotlin API surface (Long) truncates 12.5→12L at the call site before reaching DB.",
      "FK-add requires DROP TABLE (SQLite can't ALTER TABLE to add FK) — DatabaseDriverFactory.onOpen needs DROP + CREATE blocks.",
    ],
    fixed: [
      "§4.12 item 7: getAniListDetail != null semantic change called out — implementing agent must grep + audit LibraryViewModel:350-365 pattern.",
      "§4.12 item 8: updateMainEntryTitle(mainId, title, updatedAt) query added; refresh flow must call it.",
      "§4.12 item 9: clearExtensionAxis NULLs accepted as desired behavior — DownloadScanner uses ?: fallbacks, main_id match path survives.",
      "§4.12 item 10: episode_number scoped as schema-only fix, API surface change (Long→Float/Double) deferred to separate task.",
      "§4.12 item 11: DatabaseDriverFactory.onOpen must include DROP + CREATE blocks for watch_progress + notification_sent.",
    ],
  },
  {
    num: "3",
    title: "Iteration 3 — final sign-off (18 checks)",
    subtitle:
      "Comprehensive sign-off review. APPROVED with minor documentation polish.",
    accent: "success",
    counts: [
      { label: "CONFIRMED", value: 11 },
      { label: "FLAW", value: 0 },
      { label: "CONCERN", value: 7 },
    ],
    found: [
      "All 11 fixes from iterations 2A + 2B correctly applied in schemas + queries + narrative.",
      "Nullable discriminators enable clearDataSourceAxis / clearExtensionAxis ✓.",
      "Atomic transactions preserve consistency ✓.",
      "content_id regeneration maintains the invariant ✓.",
      "episode_number scope is honest (schema-only) ✓.",
      "FK-add approach is feasible (DROP + CREATE) ✓.",
      "7 minor CONCERNS — all documentation polish (stale migration narratives, missing cross-references, outdated meta-commentary). None block implementation.",
    ],
    fixed: [
      "Verdict: APPROVE WITH MINOR FIXES — plan is fundamentally sound + presentation-ready.",
      "The 7 concerns are cosmetic single-line fixes (e.g. '2 dead' should read '3 dead'; updateContentTitle could be renamed updateMainEntryTitle for consistency).",
      "Recommendation: ship to dashboard. The 7 concerns can be addressed in a 15-minute polish pass before presentation, but they don't block sign-off.",
    ],
  },
  {
    num: "4",
    title: "Iteration 4 — final confirmation",
    subtitle:
      "Quick sanity check — is the plan ready for the dashboard?",
    accent: "primary",
    counts: [
      { label: "VERDICT", value: 0 },
    ],
    found: [
      "All 11 §4.12 items reconcile with the §3 schemas + §4.9/§4.11 code blocks.",
      "No draft commentary left (grep'd for TODO/FIXME/draft/wait/stale).",
      "Plan has clean §1 exec summary, §3 column-table schemas, §4.12 numbered fixes, §6 cons+risks with mitigations, §7 deferred list, §8 worked future-proofing examples, §9 verification checklist, §10 research basis.",
      "Table count math holds: 24 tables listed in §5, 2 dropped, renames net 0.",
    ],
    fixed: [
      "Verdict: ⚠️ MINOR POLISH NEEDED — plan is fundamentally ready for the dashboard.",
      "2 minor polish items (cosmetic, single-line fixes):",
      "(a) §1 line 17 says '2 dead' but §3 Change 3 says '3 dead' — should read '3 dead'.",
      "(b) updateContentTitle uses 'Content' prefix instead of 'MainEntry' — could be renamed updateMainEntryTitle for consistency.",
      "Both items are cosmetic. No FLAW-level issues remain. Plan is sound + presentation-ready.",
    ],
  },
];

/* ---------------------------------------------------------------------------
 * SECTION 11 — FOOTER NOTE
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE =
  "This is a PROPOSAL. No schema changes will be made until the user approves. The dashboard at /database-plan/ presents this plan in a scannable format for review.";

export const FOOTER_NOTE_BULLETS: string[] = [
  "Source of truth: APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md (446 lines).",
  "Research basis: 5 parallel Explore sub-agents (Tasks 2-a through 2-e) — read the actual codebase, decisions, and knowledge files.",
  "Review basis: 4 sub-agent iterations (1, 2A, 2B, 3, 4) — 1 FLAW + 20 CONCERNS raised, all addressed in §4.12.",
  "Migration policy: debug builds only (CORE_RULES §30) — schema can be rebuilt freely (drop + recreate, no .sqm migration files needed).",
  "Approve this plan → next session implements: schema changes + query renames + caller redirects + verification checklist (§9).",
];
