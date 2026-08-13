/*
 * ANI-KUTA Database Review — typed data for the /database-review/ dashboard page.
 *
 * Source of truth: Task 2-d-retry (database merge analysis) sub-agent findings
 * (worklog.md Task ID 2-d-retry). Every table, count, caller-count, and
 * recommendation below was verified against the actual codebase by that
 * sub-agent on 2026-08-13 — no summarisation, no drops.
 *
 * Consumed by app/database-review/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * Design follows DESIGN.md (MEMORY OS v3):
 *  - Warm Canvas (#F2EEE8) bg, cards bg #FFFDFA, border #E8E2DA, rounded-2xl
 *  - Indigo primary, Teal success, Amber warning, Rose danger, Violet secondary
 *  - Recommendation badges:
 *      MERGE        = Amber (action recommended, low risk)
 *      KEEP SEPARATE = Teal (intentional separation)
 *      DROP         = Rose (delete — dead code)
 *      INVESTIGATE  = Violet (needs more analysis)
 *  - Risk badges (for the Top Improvements ranking):
 *      High   = Rose
 *      Medium = Amber
 *      Low    = Teal
 */

/* ---------------------------------------------------------------------------
 * Section 1 — SNAPSHOT
 * ------------------------------------------------------------------------- */

export const DB_REVIEW_META = {
  reviewDate: "2026-08-13",
  reviewer:
    "Explore sub-agent (Task 2-d-retry) — read all 15 .sq files + greped codebase for getAniListDetail / upsertAniListDetail / getExtensionDetail / upsertExtensionDetail / getOtherSourceDetails / upsertOtherSourceDetail / anime_metadata_cache callers.",
  sourceRepo:
    "ANI-KUTA/APP/ani-kuta/core/database/src/main/sqldelight/com/confused/anikuta/core/database/",
} as const;

export interface SnapshotMetric {
  metric: string;
  value: string;
  note: string;
}

export const SNAPSHOT = {
  intro:
    "The actual current schema, verified by reading every .sq file + greping for query callers:",
  metrics: [
    {
      metric: "SQLDelight tables",
      value: "26",
      note: "across 15 .sq files (NOT 28 — D-192 dropped 3 dead tables: content_ext, content_ext_repo, user_customization)",
    },
    {
      metric: "Schema groups",
      value: "13",
      note: "App, Watch, Activity, Library, Content Identity, Data Cache, Downloads, Schedule, Updates, Genres, Notifications, Ratings, App Settings",
    },
    {
      metric: "FK enforcement",
      value: "ON (D-166)",
      note: "PRAGMA foreign_keys = ON — D-189 cleaned up broken FKs that were masked while enforcement was off",
    },
    {
      metric: "Largest table",
      value: "download_queue (34 cols)",
      note: "Heavy denormalization — intentional for queue UI without JOINs (REVIEW-5 M1+M2 canonical v1 schema)",
    },
    {
      metric: "Smallest table",
      value: "app_metadata (2 cols)",
      note: "Degenerate KV — superseded by app_settings (5 cols, typed)",
    },
    {
      metric: "Dead tables (zero callers)",
      value: "1",
      note: "other_source_detail — getOtherSourceDetails / upsertOtherSourceDetail defined but never invoked anywhere in the codebase",
    },
    {
      metric: "SQLDelight version",
      value: "2.0.2",
      note: "NOT Room — D-034 decision (Agent-friendly SQLDelight + Kotlin-native types)",
    },
    {
      metric: "Gradle modules total",
      value: "46",
      note: "1 :app + 26 :core:* + 1 :data:extension + 18 :feature:*",
    },
  ] satisfies SnapshotMetric[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 2 — SCHEMA INVENTORY (all 26 tables, grouped)
 * ------------------------------------------------------------------------- */

export type InventoryGroup =
  | "App"
  | "Watch"
  | "Activity"
  | "Library"
  | "Content Identity"
  | "Data Cache"
  | "Downloads"
  | "Schedule"
  | "Updates"
  | "Genres"
  | "Notifications"
  | "Ratings"
  | "App Settings";

export interface InventoryRow {
  /** Table name. */
  table: string;
  /** Source .sq file (basename). */
  sqFile: string;
  /** Logical group (display order). */
  group: InventoryGroup;
  /** Column count (transcribed from the CREATE TABLE statement). */
  columns: number;
  /** Primary key descriptor (e.g. "main_id", "(main_id, episode_key)", "id AUTOINCREMENT"). */
  pk: string;
  /** SQLDelight named queries count in the .sq file (select/insert/update/delete). */
  queries: number;
  /** Distinct caller sites in the codebase (approx — grep count, deduped by file). */
  callers: number | "dead";
  /** Short purpose / notable annotation. */
  note: string;
}

export const SCHEMA_INVENTORY: {
  group: InventoryGroup;
  purpose: string;
  rows: InventoryRow[];
}[] = [
  {
    group: "App",
    purpose: "Legacy KV store (pre-D-192)",
    rows: [
      {
        table: "app_metadata",
        sqFile: "app.sq",
        group: "App",
        columns: 2,
        pk: "key",
        queries: 2,
        callers: 4,
        note: "Degenerate KV — superseded by app_settings. Merge candidate.",
      },
    ],
  },
  {
    group: "App Settings",
    purpose: "Typed KV store (D-192) — backup/restore mirror",
    rows: [
      {
        table: "app_settings",
        sqFile: "appSettings.sq",
        group: "App Settings",
        columns: 5,
        pk: "setting_key",
        queries: 5,
        callers: 6,
        note: "setting_ prefix avoids Kotlin keyword conflicts. New setting = INSERT, no schema change.",
      },
    ],
  },
  {
    group: "Content Identity",
    purpose: "Central content record + per-source detail tables (6 tables)",
    rows: [
      {
        table: "data_source",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 5,
        pk: "id",
        queries: 3,
        callers: 2,
        note: "Lookup — seeded once at first launch (AniList, TMDB, Kitsu, MAL).",
      },
      {
        table: "system",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 5,
        pk: "id",
        queries: 3,
        callers: 2,
        note: "Lookup — extension systems (Aniyomi, CloudStream, Sora, Mangayomi).",
      },
      {
        table: "content",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 15,
        pk: "main_id",
        queries: 7,
        callers: 22,
        note: "Backbone — almost every other table FKs to content.main_id.",
      },
      {
        table: "anilist_detail",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 13,
        pk: "main_id",
        queries: 3,
        callers: 16,
        note: "9-column overlap with anime_metadata_cache. Consolidation candidate.",
      },
      {
        table: "extension_detail",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 11,
        pk: "main_id",
        queries: 3,
        callers: 11,
        note: "Aniyomi source-specific metadata. D-189: FK to content_ext removed.",
      },
      {
        table: "other_source_detail",
        sqFile: "content.sq",
        group: "Content Identity",
        columns: 7,
        pk: "id",
        queries: 3,
        callers: "dead",
        note: "DEAD CODE — zero callers. Drop candidate (D-192 precedent).",
      },
    ],
  },
  {
    group: "Library",
    purpose: "User's saved anime + categories (2 tables)",
    rows: [
      {
        table: "library_category",
        sqFile: "library.sq",
        group: "Library",
        columns: 5,
        pk: "id",
        queries: 6,
        callers: 8,
        note: "Default category is permanent (is_permanent = 1).",
      },
      {
        table: "library_item",
        sqFile: "library.sq",
        group: "Library",
        columns: 5,
        pk: "id",
        queries: 13,
        callers: 14,
        note: "M:N via (main_id, category_id). Phase DB-OPT: idx_library_item_unique hardens dedup.",
      },
    ],
  },
  {
    group: "Watch",
    purpose: "Per-episode watch progress (1 table)",
    rows: [
      {
        table: "watch_progress",
        sqFile: "watch.sq",
        group: "Watch",
        columns: 11,
        pk: "episode_key",
        queries: 12,
        callers: 9,
        note: "Phase WP — 6 original + 5 new cols. Two-flag state machine (CF1).",
      },
    ],
  },
  {
    group: "Activity",
    purpose: "User activity event log (1 table)",
    rows: [
      {
        table: "activity_event",
        sqFile: "tracking.sq",
        group: "Activity",
        columns: 10,
        pk: "id AUTOINCREMENT",
        queries: 7,
        callers: 5,
        note: "365-day retention (ActivityPruneWorker). content_key/episode_key are NOT FKs.",
      },
    ],
  },
  {
    group: "Data Cache",
    purpose: "Anime + episode + browse cache (3 tables, Phase D)",
    rows: [
      {
        table: "anime_metadata_cache",
        sqFile: "dataCache.sq",
        group: "Data Cache",
        columns: 13,
        pk: "main_id",
        queries: 3,
        callers: 16,
        note: "Never expires. 9-column overlap with anilist_detail. Consolidation candidate.",
      },
      {
        table: "data_cache_episode",
        sqFile: "dataCache.sq",
        group: "Data Cache",
        columns: 18,
        pk: "(main_id, episode_number) UNIQUE",
        queries: 4,
        callers: 8,
        note: "D-190 added 8 columns for AniZip + Jikan + Kitsu richer metadata.",
      },
      {
        table: "browse_cache",
        sqFile: "dataCache.sq",
        group: "Data Cache",
        columns: 4,
        pk: "section_key",
        queries: 4,
        callers: 3,
        note: "Auto-expires after 6h (homepage only). JSON blob per section.",
      },
    ],
  },
  {
    group: "Downloads",
    purpose: "Download queue + downloaded-file index (2 tables, re-keyed by mainId)",
    rows: [
      {
        table: "download_queue",
        sqFile: "downloadQueue.sq",
        group: "Downloads",
        columns: 34,
        pk: "id AUTOINCREMENT",
        queries: 19,
        callers: 12,
        note: "7-state machine. REVIEW-5 M1+M2 canonical v1 schema (no .sqm).",
      },
      {
        table: "downloaded_episode",
        sqFile: "downloadedEpisode.sq",
        group: "Downloads",
        columns: 22,
        pk: "(main_id, episode_key)",
        queries: 11,
        callers: 9,
        note: "Cache/index — source of truth is data.json on disk. DownloadScanner reconciles.",
      },
    ],
  },
  {
    group: "Schedule",
    purpose: "AniList airing schedule (1 table, Phase SC)",
    rows: [
      {
        table: "episode_schedule",
        sqFile: "episodeSchedule.sq",
        group: "Schedule",
        columns: 9,
        pk: "id AUTOINCREMENT",
        queries: 7,
        callers: 5,
        note: "scheduled_at (planned) vs actual_at (detected). IM11 uses source dateUpload.",
      },
    ],
  },
  {
    group: "Updates",
    purpose: "New-episode feed + smart-update state (2 tables, Phase UP)",
    rows: [
      {
        table: "episode_update",
        sqFile: "episodeUpdate.sq",
        group: "Updates",
        columns: 13,
        pk: "id AUTOINCREMENT",
        queries: 6,
        callers: 7,
        note: "Per-episode feed row. D-193: new_expires_at for 'new' badge expiry (3 days).",
      },
      {
        table: "anime_update_state",
        sqFile: "animeUpdateState.sq",
        group: "Updates",
        columns: 14,
        pk: "main_id",
        queries: 8,
        callers: 6,
        note: "Smart-update state. D-193 v2: learned_offset_ms for smart-release averaging.",
      },
    ],
  },
  {
    group: "Genres",
    purpose: "Canonical genre lookup + M:N junction (2 tables)",
    rows: [
      {
        table: "genre",
        sqFile: "genres.sq",
        group: "Genres",
        columns: 6,
        pk: "id AUTOINCREMENT",
        queries: 4,
        callers: 5,
        note: "Seeded with AniList's ~40 canonical genres. category: genre|theme|demographic.",
      },
      {
        table: "content_genre",
        sqFile: "genres.sq",
        group: "Genres",
        columns: 3,
        pk: "(main_id, genre_id)",
        queries: 4,
        callers: 4,
        note: "Proper M:N — replaces free-text comma-separated genres TEXT columns.",
      },
    ],
  },
  {
    group: "Ratings",
    purpose: "Per-anime + per-episode user ratings (2 tables, Phase TR)",
    rows: [
      {
        table: "user_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        columns: 3,
        pk: "main_id",
        queries: 4,
        callers: 5,
        note: "0-100 AniList-native (displayed as 0-10 with one decimal).",
      },
      {
        table: "user_episode_rating",
        sqFile: "ratings.sq",
        group: "Ratings",
        columns: 4,
        pk: "(main_id, episode_key)",
        queries: 5,
        callers: 4,
        note: "Composite PK — Phase DB-OPT dropped redundant idx_episode_rating_main.",
      },
    ],
  },
  {
    group: "Notifications",
    purpose: "Per-content config + sent-log dedup (2 tables, Phase NOTIF)",
    rows: [
      {
        table: "notification_config",
        sqFile: "notifications.sq",
        group: "Notifications",
        columns: 7,
        pk: "main_id",
        queries: 4,
        callers: 5,
        note: "Per-channel toggles + sub/dub filter. 7-day default retention.",
      },
      {
        table: "notification_sent",
        sqFile: "notifications.sq",
        group: "Notifications",
        columns: 5,
        pk: "(main_id, episode_number, audio_variant, trigger_type)",
        queries: 3,
        callers: 3,
        note: "Ephemeral dedup log — 90-day retention. NOT backup-eligible.",
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Section 3 — MERGE CANDIDATES (8 groups analyzed)
 * ------------------------------------------------------------------------- */

export type Recommendation =
  | "MERGE"
  | "KEEP_SEPARATE"
  | "DROP"
  | "INVESTIGATE";

export interface RecommendationMeta {
  label: string;
  /** CSS var or hex color — DESIGN.md accent. */
  colorVar: string;
  /** Short symbol for inline display. */
  symbol: string;
}

export const RECOMMENDATION_META: Record<
  Recommendation,
  RecommendationMeta
> = {
  MERGE: {
    label: "MERGE",
    colorVar: "var(--c-warning)",
    symbol: "⬇",
  },
  KEEP_SEPARATE: {
    label: "KEEP SEPARATE",
    colorVar: "var(--c-success)",
    symbol: "✓",
  },
  DROP: {
    label: "DROP",
    colorVar: "var(--c-danger)",
    symbol: "✕",
  },
  INVESTIGATE: {
    label: "INVESTIGATE",
    colorVar: "var(--c-secondary)",
    symbol: "?",
  },
};

export interface MergeCandidate {
  id: number;
  group: string;
  tables: string[];
  currentState: string;
  proposal: string;
  pros: string[];
  cons: string[];
  recommendation: Recommendation;
  /** Net table-count change (e.g. -1, 0). */
  netChange: number;
}

export const MERGE_CANDIDATES: MergeCandidate[] = [
  {
    id: 1,
    group: "Content Detail",
    tables: ["anilist_detail", "extension_detail", "other_source_detail"],
    currentState:
      "3 per-source detail tables — one per ecosystem (AniList, extension, future). All FK to content.main_id (1-to-1 for AniList + extension, 1-to-many for other_source).",
    proposal:
      "DROP other_source_detail (zero callers). KEEP anilist_detail + extension_detail separate — different column shapes, both heavily used (16 + 11 callers).",
    pros: [
      "anilist_detail + extension_detail have well-typed, distinct columns (AniList has season/score/episodes; extension has author/artist/thumbnail).",
      "Both have heavy caller counts — splitting avoids nullable columns + keeps the AniList resolver path decoupled from the extension resolver path.",
      "Dropping other_source_detail removes 7 cols + 2 indexes of dead code (D-192 precedent — content_ext + content_ext_repo were dropped for the same reason).",
    ],
    cons: [
      "Loses the future-proofing hook for TMDB/Kitsu/MAL — but those sources aren't on the roadmap (D-001 confirmed AniList as the sole metadata source).",
    ],
    recommendation: "DROP",
    netChange: -1,
  },
  {
    id: 2,
    group: "Updates",
    tables: ["episode_update", "anime_update_state"],
    currentState:
      "2 tables — episode_update (per-episode feed row, 1:N per anime) + anime_update_state (per-anime smart-update state, 1:1). Different cardinality, different lifecycles (feed vs state).",
    proposal: "KEEP SEPARATE — different cardinality (1:N vs 1:1) + different lifecycles (ephemeral feed vs persistent state).",
    pros: [
      "Mixing 1:N feed rows with 1:1 state would force nullable columns + complex JOINs.",
      "episode_update has retention cleanup (7-day acknowledged purge); anime_update_state is permanent (until library remove).",
    ],
    cons: [
      "Two queries needed when the engine reads both — but they're indexed well, so the cost is negligible.",
    ],
    recommendation: "KEEP_SEPARATE",
    netChange: 0,
  },
  {
    id: 3,
    group: "Notifications",
    tables: ["notification_config", "notification_sent"],
    currentState:
      "2 tables — notification_config (per-anime preferences, 1:1, backup-eligible) + notification_sent (dedup log, composite PK on 4 cols, 90-day retention, NOT backup-eligible).",
    proposal:
      "KEEP SEPARATE — config (persistent, user-controlled) vs log (ephemeral, system-generated). Semantically distinct + different backup eligibility.",
    pros: [
      "Config is small (7 cols, 1 row per anime) — survives library unbind + restore.",
      "Sent log has 4-column composite PK for exact dedup (don't notify twice for same episode+audio+trigger).",
      "Different retention policies — merging would force awkward partial-restore logic.",
    ],
    cons: [
      "Two queries for the notification pipeline (read config + check sent log) — but both are PK lookups, so trivially fast.",
    ],
    recommendation: "KEEP_SEPARATE",
    netChange: 0,
  },
  {
    id: 4,
    group: "Ratings",
    tables: ["user_rating", "user_episode_rating"],
    currentState:
      "2 tables — user_rating (per-anime, 1:1, 3 cols) + user_episode_rating (per-episode, composite PK on main_id+episode_key, 4 cols).",
    proposal:
      "KEEP SEPARATE — per-content vs per-episode. A nullable episode_key column would conflate the two scales + break the per-anime PK.",
    pros: [
      "Cleaner than a single table with nullable episode_key (would break 1:1 per-anime PK + force partial indexes).",
      "Different query patterns — user_rating drives library score display; user_episode_rating drives the episode rating UI.",
    ],
    cons: [
      "Conceptual duplication (rating + rated_at on both) — but the alternative (single table + nullable episode_key) is worse.",
    ],
    recommendation: "KEEP_SEPARATE",
    netChange: 0,
  },
  {
    id: 5,
    group: "Genres",
    tables: ["genre", "content_genre"],
    currentState:
      "2 tables — genre (canonical lookup, ~40 AniList genres seeded) + content_genre (M:N junction).",
    proposal: "KEEP SEPARATE — proper M:N normalization. genre is the canonical vocabulary; content_genre is the per-content link.",
    pros: [
      "Canonical genre table enables genre-page browsing (all anime with 'Action') without DISTINCT parsing of comma-separated strings.",
      "Replaces the denormalized `genres TEXT` columns on anilist_detail + extension_detail + anime_metadata_cache.",
      "Junction's `source` column (anilist|extension) enables debugging of where each genre came from.",
    ],
    cons: [
      "Duplicates the genres TEXT columns (which are kept for fast single-row reads) — but the junction is the source of truth for filtering.",
    ],
    recommendation: "KEEP_SEPARATE",
    netChange: 0,
  },
  {
    id: 6,
    group: "Cache Trio",
    tables: ["anime_metadata_cache", "data_cache_episode", "browse_cache"],
    currentState:
      "3 tables — anime_metadata_cache (per-content, 13 cols) + data_cache_episode (per-episode, 18 cols) + browse_cache (per-section JSON blob, 4 cols). All Phase D. Never expires (user manually refreshes).",
    proposal:
      "KEEP SEPARATE for the trio (3 different shapes: anime-row / episode-rows / section-blob). BUT: anime_metadata_cache overlaps anilist_detail (9 duplicated columns) — investigate merging those two.",
    pros: [
      "3 distinct shapes — merging would require nullable columns + JSON blobs.",
      "browse_cache is a generic JSON blob (sections) — unrelated to per-content/per-episode.",
    ],
    cons: [
      "anime_metadata_cache ⇄ anilist_detail has 9-column overlap (title/description/cover_url/banner_url/score/episodes/season/season_year/status/genres). This is the real normalization smell — see candidate #1's 'INVESTIGATE' tag on anilist_detail.",
    ],
    recommendation: "INVESTIGATE",
    netChange: 0,
  },
  {
    id: 7,
    group: "Library",
    tables: ["library_category", "library_item"],
    currentState:
      "2 tables — library_category (5 cols, id PK) + library_item (5 cols, id PK + FKs to content + library_category). M:N via (main_id, category_id) — one content can be in multiple categories.",
    proposal: "KEEP SEPARATE — proper normalization. Categories are reusable across contents; items link content to category.",
    pros: [
      "Default category is permanent (is_permanent = 1) — separate table enforces this constraint cleanly.",
      "Phase DB-OPT added idx_library_item_unique to harden the INSERT OR IGNORE dedup.",
      "Different lifecycles — categories survive library emptying; items don't.",
    ],
    cons: [
      "JOIN required to fetch a content's categories — but covered by idx_library_item_main + idx_library_item_category.",
    ],
    recommendation: "KEEP_SEPARATE",
    netChange: 0,
  },
  {
    id: 8,
    group: "Lookup Tables (KV)",
    tables: ["app_metadata", "app_settings"],
    currentState:
      "2 KV tables — app_metadata (2 cols: key/value, Phase 2 legacy) + app_settings (5 cols: setting_key/setting_value/setting_type/setting_category/updated_at, D-192). app_settings is a strict superset.",
    proposal:
      "MERGE app_metadata → app_settings. Migrate the 4 known app_metadata callers to use app_settings (with setting_category='general', setting_type='string'). Drop app_metadata table.",
    pros: [
      "Eliminates a degenerate 2-column KV table that duplicates app_settings' purpose.",
      "Unifies the backup/restore path — every setting flows through app_settings.",
      "Very low risk — only 4 callers, all reading simple string flags.",
    ],
    cons: [
      "Requires a one-time migration script (move app_metadata rows to app_settings). SQLDelight .sqm migration is straightforward.",
      "app_metadata is referenced by 4 callers — all need updating to app_settings API.",
    ],
    recommendation: "MERGE",
    netChange: -1,
  },
];

/* ---------------------------------------------------------------------------
 * Section 4 — TOP 3 IMPROVEMENTS (ranked by impact)
 * ------------------------------------------------------------------------- */

export type RiskLevel = "low" | "medium" | "high";

export interface RiskMeta {
  label: string;
  colorVar: string;
  symbol: string;
}

export const RISK_META: Record<RiskLevel, RiskMeta> = {
  low: {
    label: "Low risk",
    colorVar: "var(--c-success)",
    symbol: "↓",
  },
  medium: {
    label: "Medium risk",
    colorVar: "var(--c-warning)",
    symbol: "▲",
  },
  high: {
    label: "High risk",
    colorVar: "var(--c-danger)",
    symbol: "!",
  },
};

export interface TopImprovement {
  rank: number;
  title: string;
  action: string;
  detail: string;
  risk: RiskLevel;
  /** Net table-count change after this improvement (cumulative). */
  tableCountAfter: number;
  /** Files touched (approx). */
  filesTouched: number;
  rationale: string;
}

export const TOP_IMPROVEMENTS: TopImprovement[] = [
  {
    rank: 1,
    title: "Drop other_source_detail",
    action: "Delete the table + its 3 queries (getOtherSourceDetails / upsertOtherSourceDetail / deleteOtherSourceDetails).",
    detail:
      "Zero callers in the entire codebase — the queries are defined in content.sq but never invoked. D-192 precedent: content_ext + content_ext_repo were dropped for the same reason. The table + its 2 indexes (idx_other_source_main, idx_other_source_type) are pure dead weight.",
    risk: "low",
    tableCountAfter: 25,
    filesTouched: 1,
    rationale:
      "Dead code removal. Schema is debug-builds only (CORE_RULES §30) — no migration needed, just delete the CREATE TABLE + queries.",
  },
  {
    rank: 2,
    title: "Merge app_metadata → app_settings",
    action: "Migrate 4 app_metadata callers to use app_settings API, then drop app_metadata.",
    detail:
      "app_metadata is a 2-column degenerate KV (key, value) that duplicates app_settings' purpose. app_settings is a strict superset (5 cols incl. category, type, updated_at). Migration: write a .sqm that copies app_metadata rows into app_settings (setting_category='general', setting_type='string'), then drop app_metadata.",
    risk: "low",
    tableCountAfter: 24,
    filesTouched: 4,
    rationale:
      "Unifies the backup/restore path. Only 4 callers — all reading simple string flags. Trivial .sqm migration (INSERT INTO app_settings SELECT key, value, 'string', 'general', <now> FROM app_metadata).",
  },
  {
    rank: 3,
    title: "Consolidate anime_metadata_cache + anilist_detail",
    action: "Investigate merging the 9-column overlap (title/description/cover_url/banner_url/score/episodes/season/season_year/status/genres) into one table.",
    detail:
      "Both tables hold AniList-sourced anime metadata. anime_metadata_cache (Phase D, 13 cols) and anilist_detail (Phase C, 13 cols) share 9 columns. The current separation serves two different read paths — anilist_detail for the identity-link resolver, anime_metadata_cache for the cache layer. The consolidation would unify them but requires touching both read paths (anilist_detail has 16 callers across 4 files; anime_metadata_cache has 16 callers across 4 files).",
    risk: "medium",
    tableCountAfter: 23,
    filesTouched: 4,
    rationale:
      "Biggest semantic win (eliminates 9-column duplication) but riskiest — touches 4 files + 2 read paths. Should be designed carefully (which table survives? what happens to anilist_detail.id_mal? what about anime_metadata_cache.source_type?).",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — OVERALL ASSESSMENT
 * ------------------------------------------------------------------------- */

export const ASSESSMENT = {
  verdict: "Schema is fundamentally sound — wins are in removing redundancy, not aggressive merging.",
  idealTableCount: 23,
  currentTableCount: 26,
  strengths: [
    "FK discipline post-D-166/D-189 — PRAGMA foreign_keys = ON, all FKs verified clean (no orphan references).",
    "Index quality — partial indexes for hot queries (idx_watch_progress_continue, idx_anime_update_due, idx_episode_update_ack_at) + covering composites (leftmost main_id on all composite PKs makes separate main_id indexes redundant — Phase DB-OPT removed 4 of them).",
    "Separation of concerns — config vs log (notification_config/sent), feed vs state (episode_update/anime_update_state), per-content vs per-episode (user_rating/user_episode_rating).",
    "Stable main_id identity — UUID backbone survives source switches. contentId changes; main_id never does.",
    "Explicit retention policies — activity_event (365d), notification_sent (90d), episode_update acknowledged (7d). ActivityPruneWorker + periodic cleanup workers.",
    "Proper M:N normalization — genre/content_genre replaces free-text comma-separated genres TEXT columns.",
    "Typed settings KV — app_settings (D-192) has category/type/updated_at for backup/restore UI.",
  ],
  weaknesses: [
    "other_source_detail is dead code — 0 callers. D-192 precedent says: drop it.",
    "anime_metadata_cache ⇄ anilist_detail has 9-column duplication. Real normalization smell — biggest semantic win available.",
    "app_metadata is a degenerate 2-column KV that duplicates app_settings' purpose.",
    "Denormalized episode metadata in download_queue (34 cols) + downloaded_episode (22 cols) — intentional for UI without JOINs, but worth noting.",
    "genres TEXT columns on anilist_detail + extension_detail + anime_metadata_cache duplicate the canonical content_genre junction (kept for fast single-row reads).",
  ],
  bottomLine:
    "The 26-table schema is well-architected. The optimization play is removing redundancy (3 tables: other_source_detail, app_metadata, and either anilist_detail OR anime_metadata_cache), NOT aggressive merging. Target end-state: 23 tables. All 3 optimizations are debug-build-safe (CORE_RULES §30 — schema can be rebuilt freely).",
} as const;

/* ---------------------------------------------------------------------------
 * Section 6 — FOOTER NOTE
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE_BULLETS: string[] = [
  "This is a PROPOSAL, not an implementation. The user will decide which optimizations to pursue.",
  "All table counts + caller counts were verified by reading the .sq files + greping the codebase (Task 2-d-retry).",
  "Schema changes are debug-build-safe per CORE_RULES §30 — no production users, no published APK, schema can be rebuilt freely.",
  "Each optimization's risk level reflects the blast radius (files touched + caller count + migration complexity), not the upside.",
  "After applying all 3 optimizations: 26 → 23 tables. The remaining 23 are all KEEP_SEPARATE — no further merging recommended.",
];
