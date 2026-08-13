/*
 * ANI-KUTA database schema — visual data (actual current schema, post-D-192).
 *
 * Source: APP/ani-kuta/core/database/src/main/sqldelight/com/confused/anikuta/core/database/*.sq
 * (15 .sq files — read in full and transcribed column-by-column).
 *
 * 26 tables across 15 .sq files (13 logical groups for visualization). This
 * file mirrors the ACTUAL current schema — not the planned Phase-1 design.
 * D-166 (PRAGMA foreign_keys = ON) + D-189 (FK cleanup) + D-190 (episode
 * metadata engine) + D-192 (dropped content_ext + content_ext_repo +
 * user_customization — dead code) are all reflected below.
 *
 * Each table entry mirrors the SQL CREATE TABLE statement in the .sq file:
 *  - columns: ordered (name, type, constraints, isPK, isFK, fkTarget, desc)
 *  - indexes: list of named indexes (incl. UNIQUE + partial)
 *  - group: one of the 13 logical groups
 *  - compositePK: when the PK spans multiple columns
 *  - uniques: inline table-level UNIQUE constraints
 *
 * Hardcoded for the static dashboard demo — no API calls.
 */

export type SchemaGroup =
  | "App"
  | "Watch"
  | "Activity"
  | "Library"
  | "Content"
  | "Cache"
  | "Downloads"
  | "Schedule"
  | "Updates"
  | "Genres"
  | "Notifications"
  | "Ratings"
  | "AppSettings";

export interface SchemaColumn {
  name: string;
  type: string;
  /** Inline constraints (PK / NOT NULL / CHECK / DEFAULT / UNIQUE). */
  constraints?: string;
  isPK?: boolean;
  isFK?: boolean;
  fkTarget?: string;
  desc?: string;
}

export interface SchemaIndex {
  name: string;
  /** What it indexes, e.g. "match_key" or "(ecosystem, source_id, external_id) WHERE source_id IS NOT NULL" */
  on: string;
  unique?: boolean;
  partial?: boolean;
  note?: string;
}

export interface SchemaTable {
  name: string;
  group: SchemaGroup;
  deferred?: boolean;
  description: string;
  columns: SchemaColumn[];
  indexes?: SchemaIndex[];
  /** Composite primary key (when the PK is multiple columns). */
  compositePK?: string[];
  /** Inline table-level UNIQUE constraints. */
  uniques?: string[];
  /** Annotation note (e.g. "dead code — zero callers", "Phase DB-OPT"). */
  fixNote?: string;
  /** Source .sq file (basename). */
  sqFile: string;
}

/* ---------------------------------------------------------------------------
 * Group metadata (13 groups, color-coded per DESIGN.md accent palette).
 * ------------------------------------------------------------------------- */

export interface GroupMeta {
  name: SchemaGroup;
  label: string;
  purpose: string;
  /** DESIGN.md accent color hex. */
  color: string;
  /** CSS var fallback. */
  colorVar: string;
  /** Tailwind-friendly dot class. */
  dot: string;
}

export const SCHEMA_GROUPS: GroupMeta[] = [
  {
    name: "App",
    label: "App Metadata",
    purpose: "Legacy 2-column KV store (pre-D-192) — superseded by app_settings",
    color: "#22C55E",
    colorVar: "#22C55E",
    dot: "bg-[#22C55E]",
  },
  {
    name: "AppSettings",
    label: "App Settings",
    purpose: "Typed KV store for ALL app settings (D-192) — backup/restore mirror",
    color: "#0EA5E9",
    colorVar: "#0EA5E9",
    dot: "bg-[#0EA5E9]",
  },
  {
    name: "Content",
    label: "Content Identity",
    purpose: "Central content record + per-source detail tables (AniList / extension / other)",
    color: "#6366F1",
    colorVar: "var(--c-primary)",
    dot: "bg-[#6366F1]",
  },
  {
    name: "Library",
    label: "Library",
    purpose: "User's saved anime + categories (Default is permanent)",
    color: "#14B8A6",
    colorVar: "var(--c-success)",
    dot: "bg-[#14B8A6]",
  },
  {
    name: "Watch",
    label: "Watch Progress",
    purpose: "Per-episode watch progress + completion state machine (Phase WP)",
    color: "#F59E0B",
    colorVar: "var(--c-warning)",
    dot: "bg-[#F59E0B]",
  },
  {
    name: "Activity",
    label: "Activity",
    purpose: "User activity event log — 365-day retention (D-045)",
    color: "#A8A29E",
    colorVar: "#A8A29E",
    dot: "bg-[#A8A29E]",
  },
  {
    name: "Cache",
    label: "Data Cache",
    purpose: "Anime + episode + browse-section metadata cache (Phase D, never expires)",
    color: "#EC4899",
    colorVar: "#EC4899",
    dot: "bg-[#EC4899]",
  },
  {
    name: "Downloads",
    label: "Downloads",
    purpose: "Download queue + downloaded-file index (re-keyed by mainId + episodeKey)",
    color: "#8B5CF6",
    colorVar: "var(--c-secondary)",
    dot: "bg-[#8B5CF6]",
  },
  {
    name: "Schedule",
    label: "Schedule",
    purpose: "AniList airing schedule with actual-release tracking (Phase SC)",
    color: "#06B6D4",
    colorVar: "#06B6D4",
    dot: "bg-[#06B6D4]",
  },
  {
    name: "Updates",
    label: "Updates",
    purpose: "New-episode feed + per-anime smart-update state (Phase UP)",
    color: "#10B981",
    colorVar: "#10B981",
    dot: "bg-[#10B981]",
  },
  {
    name: "Genres",
    label: "Genres",
    purpose: "Canonical AniList genre lookup + content↔genre junction table",
    color: "#F43F5E",
    colorVar: "#F43F5E",
    dot: "bg-[#F43F5E]",
  },
  {
    name: "Ratings",
    label: "Ratings",
    purpose: "Per-anime + per-episode user ratings (Phase TR)",
    color: "#D946EF",
    colorVar: "#D946EF",
    dot: "bg-[#D946EF]",
  },
  {
    name: "Notifications",
    label: "Notifications",
    purpose: "Per-content config + sent-log dedup (Phase NOTIF)",
    color: "#FF6B6B",
    colorVar: "var(--c-danger)",
    dot: "bg-[#FF6B6B]",
  },
];

/* ---------------------------------------------------------------------------
 * 26 tables — actual current schema (post-D-192). Transcribed column-by-column
 * from the 15 .sq files in core/database/src/main/sqldelight/. The `content`
 * table is the backbone — almost every other table FKs to content.main_id.
 * ------------------------------------------------------------------------- */

export const SCHEMA_TABLES: SchemaTable[] = [
  /* ---- Group: App Metadata (1 table) ---- */
  {
    name: "app_metadata",
    group: "App",
    sqFile: "app.sq",
    description:
      "Legacy 2-column key-value store for app flags (schema version, cached flags). Phase 2 — predates app_settings.",
    columns: [
      { name: "key", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, desc: "Setting key" },
      { name: "value", type: "TEXT", constraints: "NOT NULL", desc: "Setting value (string)" },
    ],
    fixNote:
      "Degenerate KV — app_settings is a typed superset (5 cols incl. category/type/updated_at). Merge candidate (see /database-review/).",
  },

  /* ---- Group: App Settings (1 table) ---- */
  {
    name: "app_settings",
    group: "AppSettings",
    sqFile: "appSettings.sq",
    description:
      "Single typed KV table for ALL app settings (D-192). Persistent mirror of PreferenceStore for backup/restore + cross-device sync.",
    columns: [
      { name: "setting_key", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, desc: "Unique setting key (e.g. download_quality, theme_accent)" },
      { name: "setting_value", type: "TEXT", constraints: "NOT NULL", desc: "Serialized value" },
      { name: "setting_type", type: "TEXT", constraints: "NOT NULL DEFAULT 'string'", desc: "bool|int|long|float|string|set — for deserialization" },
      { name: "setting_category", type: "TEXT", constraints: "NOT NULL DEFAULT 'general'", desc: "download|player|appearance|notifications|general — for backup UI" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Last change epoch millis" },
    ],
    fixNote:
      "D-192 — `setting_` prefix avoids Kotlin keyword conflicts (key/value are soft keywords). Adding a setting = one INSERT, no schema change.",
  },

  /* ---- Group: Content Identity (6 tables) ---- */
  {
    name: "data_source",
    group: "Content",
    sqFile: "content.sq",
    description: "Lookup table for metadata/tracking sources (AniList, TMDB, Kitsu, MAL). Seeded once at first launch.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, desc: "Auto-assigned source ID" },
      { name: "name", type: "TEXT", constraints: "NOT NULL UNIQUE", desc: "Stable machine name (e.g. anilist)" },
      { name: "display_name", type: "TEXT", constraints: "NOT NULL", desc: "UI display name" },
      { name: "type", type: "TEXT", constraints: "NOT NULL DEFAULT 'metadata'", desc: "metadata|tracking" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
  },
  {
    name: "system",
    group: "Content",
    sqFile: "content.sq",
    description: "Lookup table for extension systems (Aniyomi, CloudStream, Sora, Mangayomi). Seeded once at first launch.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, desc: "Auto-assigned system ID" },
      { name: "name", type: "TEXT", constraints: "NOT NULL UNIQUE", desc: "Stable machine name (e.g. aniyomi)" },
      { name: "display_name", type: "TEXT", constraints: "NOT NULL", desc: "UI display name" },
      { name: "package_prefix", type: "TEXT", desc: "Android package prefix (e.g. eu.kanade)" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
  },
  {
    name: "content",
    group: "Content",
    sqFile: "content.sq",
    description:
      "The central content record. One row per anime/manga/novel. mainId = stable UUID (never changes); contentId = structured string (changes). The backbone — almost every other table FKs to content.main_id.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, desc: "Stable UUID — the app's own content identity" },
      { name: "content_id", type: "TEXT", constraints: "NOT NULL", desc: "Structured source-specific ID (changes when source switches)" },
      { name: "title", type: "TEXT", constraints: "NOT NULL", desc: "Canonical title" },
      { name: "content_type", type: "TEXT", constraints: "NOT NULL DEFAULT 'anime'", desc: "anime|movie|series|manga|novel" },
      { name: "content_format", type: "TEXT", constraints: "NOT NULL DEFAULT 'video'", desc: "video|images|text|audio" },
      { name: "description", type: "TEXT", desc: "Synopsis" },
      { name: "data_source_id", type: "INTEGER", isFK: true, fkTarget: "data_source.id", desc: "FK → data_source(id) ON DELETE SET NULL" },
      { name: "system_id", type: "INTEGER", isFK: true, fkTarget: "system.id", desc: "FK → system(id) ON DELETE SET NULL" },
      { name: "extension_repo_id", type: "INTEGER", desc: "Plain nullable INTEGER (FK to content_ext_repo removed — table dropped D-192)" },
      { name: "extension_id", type: "INTEGER", desc: "Aniyomi INTERNAL source.id (D-189 — NOT a FK to content_ext)" },
      { name: "source_id", type: "INTEGER", desc: "Source-specific ID" },
      { name: "anime_url", type: "TEXT", desc: "Source-relative anime URL" },
      { name: "display_source", type: "TEXT", constraints: "NOT NULL DEFAULT 'extension'", desc: "extension|anilist (UI hint)" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_content_content_id", on: "content_id", note: "Lookup by source-specific ID" },
      { name: "idx_content_data_source", on: "data_source_id" },
      { name: "idx_content_extension", on: "extension_id" },
      { name: "idx_content_extension_url", on: "(extension_id, anime_url)", note: "Phase DB-OPT: composite for getContentByExtension" },
    ],
    fixNote:
      "D-189 + D-192: extension_id is the Aniyomi source.id (plain INTEGER, NOT a FK). content_ext + content_ext_repo tables dropped (dead code).",
  },
  {
    name: "anilist_detail",
    group: "Content",
    sqFile: "content.sq",
    description:
      "AniList-specific metadata. One row per content (if linked to AniList). The canonical AniList fields — kept separate from extension_detail because the shapes are different.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "anilist_id", type: "INTEGER", constraints: "NOT NULL", desc: "AniList anime ID" },
      { name: "id_mal", type: "INTEGER", desc: "MyAnimeList cross-reference ID" },
      { name: "score", type: "INTEGER", desc: "AniList average score (0-100)" },
      { name: "episodes", type: "INTEGER", desc: "Total episode count (AniList)" },
      { name: "season", type: "TEXT", desc: "WINTER|SPRING|SUMMER|FALL" },
      { name: "season_year", type: "INTEGER", desc: "Season year" },
      { name: "status", type: "TEXT", desc: "RELEASING|FINISHED|NOT_YET_RELEASED|CANCELLED" },
      { name: "genres", type: "TEXT", desc: "Comma-separated genres (also mirrored in content_genre)" },
      { name: "synopsis", type: "TEXT", desc: "AniList synopsis" },
      { name: "cover_url", type: "TEXT", desc: "AniList cover image URL" },
      { name: "banner_url", type: "TEXT", desc: "AniList banner image URL" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_anilist_detail_anilist_id", on: "anilist_id", note: "Phase DB-OPT: getContentByAniListId JOIN filter" },
    ],
    fixNote:
      "9-column overlap with anime_metadata_cache (title/description/score/episodes/season/season_year/status/genres/cover_url/banner_url). Merge candidate (see /database-review/).",
  },
  {
    name: "extension_detail",
    group: "Content",
    sqFile: "content.sq",
    description:
      "Extension-specific metadata. One row per content (if linked to an extension). Source: Aniyami/Mangayomi extension API.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "extension_id", type: "INTEGER", constraints: "NOT NULL", desc: "Aniyomi source.id (NOT a FK to content_ext)" },
      { name: "source_id", type: "INTEGER", constraints: "NOT NULL", desc: "Source-specific ID" },
      { name: "anime_url", type: "TEXT", constraints: "NOT NULL", desc: "Source-relative anime URL" },
      { name: "description", type: "TEXT", desc: "Extension-provided synopsis" },
      { name: "genres", type: "TEXT", desc: "Comma-separated genres" },
      { name: "status", type: "TEXT", desc: "Extension status string" },
      { name: "author", type: "TEXT", desc: "Author (manga)" },
      { name: "artist", type: "TEXT", desc: "Artist (manga)" },
      { name: "thumbnail_url", type: "TEXT", desc: "Extension thumbnail URL" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    fixNote: "D-189: FK to content_ext(id) removed (table dropped) — was semantically wrong + crashed under PRAGMA foreign_keys = ON.",
  },
  {
    name: "other_source_detail",
    group: "Content",
    sqFile: "content.sq",
    description:
      "Generic key-value table for future sources (TMDB, Kitsu, MAL). One row per (main_id, source_type, source_ref_id, key).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, desc: "Auto-assigned row ID" },
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "source_type", type: "TEXT", constraints: "NOT NULL", desc: "tmdb|kitsu|mal|future" },
      { name: "source_ref_id", type: "TEXT", constraints: "NOT NULL", desc: "External ID within source_type" },
      { name: "key", type: "TEXT", constraints: "NOT NULL", desc: "Field key (e.g. title, synopsis)" },
      { name: "value", type: "TEXT", desc: "Field value (nullable)" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_other_source_main", on: "main_id" },
      { name: "idx_other_source_type", on: "(source_type, source_ref_id)" },
    ],
    fixNote:
      "DEAD CODE — zero callers in the codebase (getOtherSourceDetails / upsertOtherSourceDetail defined but never called). Drop candidate (see /database-review/).",
  },

  /* ---- Group: Library (2 tables) ---- */
  {
    name: "library_category",
    group: "Library",
    sqFile: "library.sq",
    description:
      "User-defined categories (Default, Watching, Plan to Watch, etc.). The 'Default' category is permanent (is_permanent = 1) — cannot be deleted.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, desc: "Auto-assigned category ID" },
      { name: "name", type: "TEXT", constraints: "NOT NULL UNIQUE", desc: "Category name (case-sensitive)" },
      { name: "display_order", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "UI sort order" },
      { name: "is_permanent", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "1 = cannot delete/rename (Default)" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
  },
  {
    name: "library_item",
    group: "Library",
    sqFile: "library.sq",
    description:
      "Links content to a category. One content can be in multiple categories. Most queries hit the Default category.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, desc: "Auto-assigned row ID" },
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "category_id", type: "INTEGER", constraints: "NOT NULL", isFK: true, fkTarget: "library_category.id", desc: "FK → library_category(id) ON DELETE CASCADE" },
      { name: "display_order", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Sort order within category" },
      { name: "added_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_library_item_main", on: "main_id" },
      { name: "idx_library_item_category", on: "category_id" },
      { name: "idx_library_item_unique", on: "(main_id, category_id)", unique: true, note: "Phase DB-OPT: hardens INSERT OR IGNORE dedup" },
    ],
  },

  /* ---- Group: Watch Progress (1 table) ---- */
  {
    name: "watch_progress",
    group: "Watch",
    sqFile: "watch.sq",
    description:
      "Per-episode watch progress. Keyed by episode_key (composite string). Phase WP extended with main_id + watch_count + first_watched_at + auto_mark_suppressed + user_marked_watched.",
    columns: [
      { name: "episode_key", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, desc: "Composite episode key" },
      { name: "position", type: "INTEGER", constraints: "NOT NULL", desc: "Current position in seconds" },
      { name: "duration", type: "INTEGER", constraints: "NOT NULL", desc: "Episode duration in seconds" },
      { name: "completed", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "0/1 — explicit completion flag" },
      { name: "completed_at", type: "INTEGER", desc: "Epoch millis when completed (nullable)" },
      { name: "last_watched_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis — last play" },
      { name: "main_id", type: "TEXT", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) (app-level enforced for existing installs; FK in CREATE TABLE for fresh)" },
      { name: "watch_count", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Phase WP — incremented on each completed watch" },
      { name: "first_watched_at", type: "INTEGER", desc: "Phase WP — first watch timestamp" },
      { name: "auto_mark_suppressed", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "CF1 — user un-marked → suppress 85% auto-mark until next play" },
      { name: "user_marked_watched", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "CF1 — user explicitly marked watched (sticky)" },
    ],
    indexes: [
      { name: "idx_watch_progress_last_watched", on: "last_watched_at DESC", note: "Recent-first ordering" },
      { name: "idx_watch_progress_main_id", on: "main_id", note: "Per-anime progress queries" },
      { name: "idx_watch_progress_continue", on: "last_watched_at DESC", partial: true, note: "Phase DB-OPT partial — WHERE completed=0 AND auto_mark_suppressed=0 AND position>0 (O(log N) Continue Watching)" },
      { name: "idx_watch_progress_completed_at", on: "completed_at DESC", note: "Phase DB-OPT — getCompletedEpisodes ORDER BY" },
    ],
    fixNote:
      "Phase WP — 11 cols total (6 original + 5 new). Migration via DatabaseDriverFactory.onOpen. CF1: two-flag state machine (auto_mark_suppressed + user_marked_watched).",
  },

  /* ---- Group: Activity (1 table) ---- */
  {
    name: "activity_event",
    group: "Activity",
    sqFile: "tracking.sq",
    description:
      "User activity event log (D-045). Records everything the user does — watch, search, browse, download. Retention: 365 days default, unlimited option.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "event_type", type: "TEXT", constraints: "NOT NULL", desc: "WATCH|SEARCH|BROWSE|DOWNLOAD|etc." },
      { name: "content_key", type: "TEXT", desc: "Content reference (NOT a FK — plain key)" },
      { name: "episode_key", type: "TEXT", desc: "Episode reference (NOT a FK — plain key)" },
      { name: "session_id", type: "TEXT", constraints: "NOT NULL", desc: "App session ID for grouping" },
      { name: "route", type: "TEXT", desc: "Screen route when event occurred" },
      { name: "content_type", type: "TEXT", desc: "VIDEO|IMAGE|TEXT" },
      { name: "duration_ms", type: "INTEGER", desc: "Event duration (e.g. watch time)" },
      { name: "payload", type: "TEXT", desc: "JSON blob for extra data" },
      { name: "timestamp", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_activity_timestamp", on: "timestamp DESC", note: "Recent events" },
      { name: "idx_activity_type", on: "event_type", note: "Stats by type" },
      { name: "idx_activity_content", on: "content_key", note: "Per-content history" },
    ],
    fixNote: "content_key/episode_key are plain TEXT (NOT FKs). ActivityPruneWorker handles retention.",
  },

  /* ---- Group: Data Cache (3 tables) ---- */
  {
    name: "anime_metadata_cache",
    group: "Cache",
    sqFile: "dataCache.sq",
    description:
      "Stores the full anime metadata per content (Phase D). Never expires — user manually refreshes via the refresh button.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY UNIQUE", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "title", type: "TEXT", constraints: "NOT NULL", desc: "Display title" },
      { name: "description", type: "TEXT", desc: "Synopsis" },
      { name: "cover_url", type: "TEXT", desc: "Cover image URL" },
      { name: "banner_url", type: "TEXT", desc: "Banner image URL" },
      { name: "score", type: "INTEGER", desc: "Average score" },
      { name: "episodes", type: "INTEGER", desc: "Total episode count" },
      { name: "season", type: "TEXT", desc: "WINTER|SPRING|SUMMER|FALL" },
      { name: "season_year", type: "INTEGER", desc: "Season year" },
      { name: "status", type: "TEXT", desc: "RELEASING|FINISHED|NOT_YET_RELEASED|CANCELLED" },
      { name: "genres", type: "TEXT", desc: "Comma-separated genres" },
      { name: "source_type", type: "TEXT", constraints: "NOT NULL DEFAULT 'anilist'", desc: "anilist|extension|etc." },
      { name: "fetched_at", type: "INTEGER", constraints: "NOT NULL", desc: "When this row was cached" },
    ],
    fixNote:
      "9-column overlap with anilist_detail (title/description/cover_url/banner_url/score/episodes/season/season_year/status/genres). Consolidation candidate (see /database-review/).",
  },
  {
    name: "data_cache_episode",
    group: "Cache",
    sqFile: "dataCache.sq",
    description:
      "Per-episode metadata cache. D-190 added 8 columns for richer AniZip + Jikan + Kitsu data. Phase DB-OPT added source_name + scanlator (preserves extension's original ep.name + scanlator through AniList-enriched cache write).",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number (supports 5.5 for OVAs)" },
      { name: "title", type: "TEXT", desc: "AniList display title (enriched)" },
      { name: "description", type: "TEXT", desc: "Episode synopsis" },
      { name: "thumbnail_url", type: "TEXT", desc: "Episode thumbnail" },
      { name: "air_date", type: "INTEGER", desc: "Air date (epoch millis)" },
      { name: "fetched_at", type: "INTEGER", constraints: "NOT NULL", desc: "When cached" },
      { name: "episode_url", type: "TEXT", desc: "Source episode URL" },
      { name: "source_name", type: "TEXT", desc: "Phase DB-OPT — extension's original ep.name (for audio parsing)" },
      { name: "scanlator", type: "TEXT", desc: "Phase DB-OPT — extension's ep.scanlator (for audio parsing)" },
      { name: "is_filler", type: "INTEGER", desc: "D-190 Jikan filler (null=unknown, 0=no, 1=yes)" },
      { name: "is_recap", type: "INTEGER", desc: "D-190 Jikan recap (null=unknown, 0=no, 1=yes)" },
      { name: "title_japanese", type: "TEXT", desc: "D-190 Jikan title_japanese / AniZip title.ja" },
      { name: "title_romaji", type: "TEXT", desc: "D-190 Jikan title_romanji / AniZip title.x-jat" },
      { name: "runtime", type: "INTEGER", desc: "D-190 AniZip/Kitsu episode length (minutes)" },
      { name: "season_number", type: "INTEGER", desc: "D-190 AniZip/Kitsu season number" },
      { name: "episode_number_in_season", type: "INTEGER", desc: "D-190 AniZip episode number within season" },
      { name: "score", type: "REAL", desc: "D-190 Jikan community score (0-10)" },
    ],
    indexes: [
      { name: "idx_data_cache_episode_pk", on: "(main_id, episode_number)", unique: true, note: "Composite unique — one row per (content, episode)" },
    ],
  },
  {
    name: "browse_cache",
    group: "Cache",
    sqFile: "dataCache.sq",
    description:
      "Stores browse/homepage sections (trending, popular, etc.) as JSON blobs. Auto-expires after 6 hours (homepage only).",
    columns: [
      { name: "section_key", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, desc: "Section identifier (e.g. trending, popular_this_season)" },
      { name: "data_json", type: "TEXT", constraints: "NOT NULL", desc: "Serialized section data (JSON)" },
      { name: "fetched_at", type: "INTEGER", constraints: "NOT NULL", desc: "When this section was cached" },
      { name: "expires_at", type: "INTEGER", constraints: "NOT NULL", desc: "When this section expires (fetched_at + 6h)" },
    ],
  },

  /* ---- Group: Downloads (2 tables) ---- */
  {
    name: "download_queue",
    group: "Downloads",
    sqFile: "downloadQueue.sq",
    description:
      "The download queue. Re-keyed by mainId + episodeKey (D.0). 7-state machine: QUEUED|DOWNLOADING|RETRYING|PAUSED|COMPLETED|ERROR|CANCELLED. REVIEW-5 M1+M2: canonical v1 schema (no .sqm migration).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", desc: "Content main_id (denormalized — no explicit FK)" },
      { name: "episode_key", type: "TEXT", constraints: "NOT NULL", desc: "Episode key (denormalized)" },
      { name: "content_id", type: "TEXT", constraints: "NOT NULL", desc: "Source-specific content ID" },
      { name: "content_title", type: "TEXT", constraints: "NOT NULL", desc: "Title (denormalized for queue UI)" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number (denormalized)" },
      { name: "episode_name", type: "TEXT", constraints: "NOT NULL", desc: "Episode name (denormalized)" },
      { name: "cover_url", type: "TEXT", desc: "Cover image URL (denormalized)" },
      { name: "cover_color", type: "INTEGER", desc: "ARGB int for UI tinting" },
      { name: "source_id", type: "INTEGER", desc: "Source ID" },
      { name: "video_server", type: "TEXT", desc: "Server name (for re-resolve)" },
      { name: "video_quality", type: "TEXT", desc: "Quality label (e.g. 1080p)" },
      { name: "video_audio", type: "TEXT", desc: "Audio variant label (e.g. SUB/DUB)" },
      { name: "video_url", type: "TEXT", constraints: "NOT NULL", desc: "Source URL (localhost after proxy-churn)" },
      { name: "video_headers", type: "TEXT", desc: "JSON: Map<String, String>" },
      { name: "video_uri", type: "TEXT", desc: "content:// URI after publish (null while downloading)" },
      { name: "subtitle_tracks", type: "TEXT", desc: "JSON: List<SubtitleTrack>" },
      { name: "audio_tracks", type: "TEXT", desc: "JSON: List<AudioTrack>" },
      { name: "subtitle_uris", type: "TEXT", desc: "JSON: List<String> (content:// URIs after publish)" },
      { name: "state", type: "TEXT", constraints: "NOT NULL DEFAULT 'QUEUED'", desc: "QUEUED|DOWNLOADING|RETRYING|PAUSED|COMPLETED|ERROR|CANCELLED" },
      { name: "progress", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "0-100" },
      { name: "downloaded_bytes", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Bytes downloaded" },
      { name: "total_bytes", type: "INTEGER", constraints: "NOT NULL DEFAULT -1", desc: "-1 = unknown" },
      { name: "prev_total_bytes", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "REVIEW-5 M31/M34/M38 — ETA moving-average" },
      { name: "prev_estimate_bytes", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "REVIEW-5 — ETA moving-average" },
      { name: "recent_ratios_json", type: "TEXT", desc: "JSON: ArrayDeque<Float> (moving-average window)" },
      { name: "retry_attempt", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "REVIEW-5 M9/M11 — current retry count" },
      { name: "retry_max_attempts", type: "INTEGER", constraints: "NOT NULL DEFAULT 3", desc: "Max retries before ERROR" },
      { name: "last_error", type: "TEXT", desc: "Error message (if failed)" },
      { name: "resolve_context", type: "TEXT", desc: "JSON: ResolveContext (7 fields — REVIEW-5 M64 for proxy-churn re-resolve)" },
      { name: "queued_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
      { name: "started_at", type: "INTEGER", desc: "Epoch millis (null while QUEUED)" },
      { name: "completed_at", type: "INTEGER", desc: "Epoch millis (null until COMPLETED)" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Epoch millis — last write" },
    ],
    indexes: [
      { name: "idx_download_queue_main_episode", on: "(main_id, episode_key)", unique: true, note: "One queue entry per (content, episode)" },
      { name: "idx_download_queue_state", on: "state", note: "Fast lookup by state (tryStartNext + UI filter)" },
    ],
    fixNote:
      "34 cols (heavy denormalization — intentional for queue UI without JOINs). D-149-fix: updateDownloadVideoUrl for proxy-churn re-resolve.",
  },
  {
    name: "downloaded_episode",
    group: "Downloads",
    sqFile: "downloadedEpisode.sq",
    description:
      "Downloaded episode files on disk. CACHE/INDEX table — the SOURCE OF TRUTH for reinstall recognition is data.json in each content folder. DownloadScanner reconciles this table with disk state on startup.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", desc: "Content main_id (compositePK — denormalized, no explicit FK)" },
      { name: "episode_key", type: "TEXT", constraints: "NOT NULL", desc: "Episode key (compositePK)" },
      { name: "content_id", type: "TEXT", constraints: "NOT NULL", desc: "Source-specific content ID" },
      { name: "content_title", type: "TEXT", constraints: "NOT NULL", desc: "Title (denormalized for UI)" },
      { name: "content_format", type: "TEXT", constraints: "NOT NULL DEFAULT 'video'", desc: "video|images|text|audio" },
      { name: "content_type", type: "TEXT", constraints: "NOT NULL DEFAULT 'anime'", desc: "anime|movie|series|manga|novel" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number" },
      { name: "episode_name", type: "TEXT", constraints: "NOT NULL", desc: "Episode name" },
      { name: "cover_url", type: "TEXT", desc: "Cover image URL" },
      { name: "cover_color", type: "INTEGER", desc: "ARGB int for UI tinting" },
      { name: "content_folder_uri", type: "TEXT", constraints: "NOT NULL", desc: "DocumentFile URI of the content folder" },
      { name: "file_path", type: "TEXT", constraints: "NOT NULL", desc: "content:// URI to the published video file" },
      { name: "file_size", type: "INTEGER", constraints: "NOT NULL", desc: "Bytes" },
      { name: "quality", type: "TEXT", desc: "e.g. 1080p, 720p" },
      { name: "video_uri", type: "TEXT", desc: "content:// URI (same as file_path — kept for clarity)" },
      { name: "video_file_name", type: "TEXT", constraints: "NOT NULL", desc: "Title - E00001.mp4 (matches file on disk)" },
      { name: "subtitle_uris", type: "TEXT", desc: "JSON: List<String> (content:// URIs)" },
      { name: "source_id", type: "INTEGER", desc: "Source ID" },
      { name: "video_server", type: "TEXT", desc: "Server name" },
      { name: "video_audio", type: "TEXT", desc: "Audio variant label" },
      { name: "verified_at", type: "INTEGER", desc: "Last time file was verified to exist + be non-empty" },
      { name: "downloaded_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    compositePK: ["main_id", "episode_key"],
    fixNote:
      "REVIEW-1 I3: no separate index on main_id — it's the leftmost column of the composite PK (covering index). REVIEW-5 M7: updateDownloadedContentId when user switches sources.",
  },

  /* ---- Group: Schedule (1 table) ---- */
  {
    name: "episode_schedule",
    group: "Schedule",
    sqFile: "episodeSchedule.sq",
    description:
      "AniList airing schedule for currently-releasing anime (Phase SC). scheduled_at = AniList airingAt (PLANNED time); actual_at = ACTUAL release time (null until detected by UpdateEngine).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "anilist_id", type: "INTEGER", desc: "AniList anime ID (for airing API)" },
      { name: "episode_number", type: "INTEGER", constraints: "NOT NULL", desc: "Episode number" },
      { name: "scheduled_at", type: "INTEGER", constraints: "NOT NULL", desc: "AniList airingAt (epoch millis) — PLANNED time" },
      { name: "actual_at", type: "INTEGER", desc: "ACTUAL release time (null until detected; IM11: uses source dateUpload)" },
      { name: "audio_variant", type: "TEXT", constraints: "NOT NULL DEFAULT 'unknown'", desc: "sub|dub|unknown (CF7: NOT NULL for UNIQUE dedup)" },
      { name: "source", type: "TEXT", constraints: "NOT NULL DEFAULT 'anilist'", desc: "anilist|extension|manual (IM5: dropped confidence)" },
      { name: "fetched_at", type: "INTEGER", constraints: "NOT NULL", desc: "When this row was last refreshed" },
    ],
    indexes: [
      { name: "idx_schedule_unique", on: "(main_id, episode_number, audio_variant)", unique: true, note: "One row per (content, episode, audio)" },
      { name: "idx_schedule_at", on: "scheduled_at", note: "Schedule screen ordering" },
    ],
    fixNote: "Phase DB-OPT: idx_schedule_main dropped — redundant with leftmost column of idx_schedule_unique.",
  },

  /* ---- Group: Updates (2 tables) ---- */
  {
    name: "episode_update",
    group: "Updates",
    sqFile: "episodeUpdate.sq",
    description:
      "Per-episode update feed (Phase UP). The 'New episodes' feed. Each row = a new episode discovered by the UpdateEngine. Keyed by (main_id, episode_key, audio_variant) — sub + dub are distinct rows.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "episode_key", type: "TEXT", constraints: "NOT NULL", desc: "The new episode's key" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number" },
      { name: "episode_title", type: "TEXT", desc: "Episode title (nullable)" },
      { name: "source_id", type: "INTEGER", desc: "Which extension source reported it" },
      { name: "audio_variant", type: "TEXT", constraints: "NOT NULL DEFAULT 'unknown'", desc: "sub|dub|unknown (CF7: NOT NULL for UNIQUE dedup)" },
      { name: "discovered_at", type: "INTEGER", constraints: "NOT NULL", desc: "When we found it (epoch millis)" },
      { name: "acknowledged", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "0 = unread, 1 = user dismissed/opened" },
      { name: "acknowledged_at", type: "INTEGER", desc: "M9 — when acknowledged (for retention cleanup)" },
      { name: "batch_type", type: "TEXT", constraints: "NOT NULL DEFAULT 'new'", desc: "D-192 — 'initial' = first-link batch, 'new' = individual new episode" },
      { name: "episode_count", type: "INTEGER", desc: "D-192 — for 'initial' batch, total episode count" },
      { name: "new_expires_at", type: "INTEGER", desc: "D-193 Phase 2 — when the 'new' status expires (discovered_at + 3 days); null for initial batch" },
    ],
    indexes: [
      { name: "idx_episode_update_unique", on: "(main_id, episode_key, audio_variant)", unique: true, note: "Dedup — one row per (content, episode, audio)" },
      { name: "idx_episode_update_discovered", on: "discovered_at DESC", note: "Recent-first feed ordering" },
      { name: "idx_episode_update_unack", on: "(acknowledged, new_expires_at, discovered_at DESC)", note: "D-193 Phase 2 — 'new' feed query" },
      { name: "idx_episode_update_ack_at", on: "acknowledged_at", partial: true, note: "Phase DB-OPT partial — WHERE acknowledged=1 (fast retention purge)" },
    ],
    fixNote: "Phase DB-OPT: idx_episode_update_main_id dropped — redundant with leftmost column of idx_episode_update_unique.",
  },
  {
    name: "anime_update_state",
    group: "Updates",
    sqFile: "animeUpdateState.sq",
    description:
      "Per-anime smart-update state (Phase UP). Drives the smart-engine backoff (CF4) + self-improving interval + 3-strike rule (M3). D-193 Phase 2 added dub tracking + total_episodes; D-193 v2 added learned_offset_ms for smart-release averaging.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "status", type: "TEXT", desc: "RELEASING|FINISHED|NOT_YET_RELEASED|CANCELLED|null (from AniList, M2)" },
      { name: "last_checked_at", type: "INTEGER", desc: "When we last checked for sub updates" },
      { name: "next_check_at", type: "INTEGER", desc: "When we should next check (CF4: with backoff)" },
      { name: "last_known_episode_count", type: "INTEGER", desc: "Highest SUB episode number seen" },
      { name: "next_airing_episode", type: "INTEGER", desc: "From AniList nextAiringEpisode (S4: populated by Schedule fetch)" },
      { name: "next_airing_at", type: "INTEGER", desc: "From AniList airingAt (epoch millis) (S4)" },
      { name: "auto_update_enabled", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "User can disable per-anime (M3: 0 after 3 consecutive failures)" },
      { name: "consecutive_failures", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "M3 — drives 3-strike auto-disable" },
      { name: "backoff_step", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "CF4/Q4: 0=none, 1=1h, 2=2h, 3=4h, 4=8h, 5=24h capped" },
      { name: "last_known_dub_count", type: "INTEGER", desc: "D-193 Phase 2 — highest DUB episode number (null = no dub tracking yet)" },
      { name: "last_checked_dub_at", type: "INTEGER", desc: "D-193 Phase 2 — when we last checked for dub updates" },
      { name: "total_episodes", type: "INTEGER", desc: "D-193 Phase 2 — from AniList episodes field (for completed-anime handling)" },
      { name: "learned_offset_ms", type: "INTEGER", desc: "D-193 v2 — weighted avg of previous offsets (smart-release averaging); null = no history yet" },
    ],
    indexes: [
      { name: "idx_anime_update_due", on: "next_check_at", partial: true, note: "S2 partial — WHERE auto_update_enabled=1 AND status='RELEASING' (worker's query)" },
      { name: "idx_anime_update_due_dub", on: "next_check_at", partial: true, note: "D-193 Phase 2 — WHERE auto_update_enabled=1 AND status='FINISHED' AND COALESCE(last_known_dub_count,0) < COALESCE(total_episodes,0)" },
    ],
  },

  /* ---- Group: Genres (2 tables) ---- */
  {
    name: "genre",
    group: "Genres",
    sqFile: "genres.sq",
    description:
      "Canonical genre lookup table (Phase: Genre Management). Seeded on first launch with AniList's canonical genre vocabulary (~40 items). Replaces the free-text comma-separated `genres TEXT` columns with a normalized relational model.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "NOT NULL PRIMARY KEY AUTOINCREMENT", isPK: true, desc: "Auto-assigned genre ID" },
      { name: "anilist_name", type: "TEXT", constraints: "NOT NULL UNIQUE", desc: "Exact AniList string (e.g. Sci-Fi)" },
      { name: "display_name", type: "TEXT", constraints: "NOT NULL", desc: "UI display name (same as anilist_name for now)" },
      { name: "category", type: "TEXT", constraints: "NOT NULL DEFAULT 'genre'", desc: "genre|theme|demographic — groups genres for profile/filter UI" },
      { name: "sort_key", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "For UI ordering" },
      { name: "is_nsfw", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "1 for Ecchi, Erotica, Hentai" },
    ],
  },
  {
    name: "content_genre",
    group: "Genres",
    sqFile: "genres.sq",
    description:
      "Many-to-many junction between content and genre. The `source` column tracks where the genre came from (anilist or extension) for debugging/migration.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE (compositePK)" },
      { name: "genre_id", type: "INTEGER", constraints: "NOT NULL", isFK: true, fkTarget: "genre.id", desc: "FK → genre(id) ON DELETE CASCADE (compositePK)" },
      { name: "source", type: "TEXT", constraints: "NOT NULL DEFAULT 'anilist'", desc: "anilist|extension — where this genre came from" },
    ],
    compositePK: ["main_id", "genre_id"],
    indexes: [
      { name: "idx_content_genre_main", on: "main_id", note: "Get genres for a content" },
      { name: "idx_content_genre_genre", on: "genre_id", note: "Get contents for a genre" },
    ],
    fixNote: "Proper M:N normalization — replaces free-text comma-separated genres TEXT columns.",
  },

  /* ---- Group: Ratings (2 tables) ---- */
  {
    name: "user_rating",
    group: "Ratings",
    sqFile: "ratings.sq",
    description:
      "Per-anime user rating (0-100, AniList-native; displayed as 0-10 with one decimal). Separate from library so ratings survive library unbinding. Phase TR.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "rating", type: "INTEGER", constraints: "NOT NULL", desc: "0-100 (AniList-native)" },
      { name: "rated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis — last rating change" },
    ],
  },
  {
    name: "user_episode_rating",
    group: "Ratings",
    sqFile: "ratings.sq",
    description:
      "Per-episode user rating (0-100). Powers the episode-level rating UI (Phase TR). Composite key on (main_id, episode_key).",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE (compositePK)" },
      { name: "episode_key", type: "TEXT", constraints: "NOT NULL", desc: "Composite episode key (compositePK)" },
      { name: "rating", type: "INTEGER", constraints: "NOT NULL", desc: "0-100 (same scale as user_rating)" },
      { name: "rated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    compositePK: ["main_id", "episode_key"],
    fixNote: "Phase DB-OPT: idx_episode_rating_main dropped — redundant with leftmost column of composite PK.",
  },

  /* ---- Group: Notifications (2 tables) ---- */
  {
    name: "notification_config",
    group: "Notifications",
    sqFile: "notifications.sq",
    description:
      "Per-content notification preferences (master toggle + per-channel toggles + sub/dub filter). Phase NOTIF. Keyed off main_id (stable UUID for backup/restore).",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL PRIMARY KEY", isPK: true, isFK: true, fkTarget: "content.main_id", desc: "FK → content(main_id) ON DELETE CASCADE" },
      { name: "enabled", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "Master toggle for this anime" },
      { name: "notify_on_schedule", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Notify when airing time is reached" },
      { name: "notify_on_watchable", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "Notify when episode is found on a source" },
      { name: "notify_on_immediate", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Notify immediately when schedule says released" },
      { name: "notify_sub", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "Notify for sub releases" },
      { name: "notify_dub", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Notify for dub releases" },
    ],
  },
  {
    name: "notification_sent",
    group: "Notifications",
    sqFile: "notifications.sq",
    description:
      "Log of sent notifications — used for dedup (don't notify twice for the same episode). NOT backup-eligible (ephemeral). 90-day retention. Phase NOTIF.",
    columns: [
      { name: "main_id", type: "TEXT", constraints: "NOT NULL", desc: "Content main_id (compositePK — no explicit FK; ephemeral log)" },
      { name: "episode_number", type: "INTEGER", constraints: "NOT NULL", desc: "Episode number (compositePK)" },
      { name: "audio_variant", type: "TEXT", constraints: "NOT NULL DEFAULT 'unknown'", desc: "sub|dub|unknown (compositePK)" },
      { name: "trigger_type", type: "TEXT", constraints: "NOT NULL", desc: "schedule|watchable|immediate (compositePK)" },
      { name: "sent_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    compositePK: ["main_id", "episode_number", "audio_variant", "trigger_type"],
    indexes: [
      { name: "idx_notification_sent_at", on: "sent_at", note: "Phase DB-OPT — fast retention purge" },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Summary — drives the top summary card.
 * ------------------------------------------------------------------------- */

export const SCHEMA_SUMMARY = {
  totalTables: SCHEMA_TABLES.length, // 26
  activeTables: SCHEMA_TABLES.filter((t) => !t.deferred).length, // 26 (all active)
  deferredTables: SCHEMA_TABLES.filter((t) => t.deferred).length, // 0
  totalGroups: SCHEMA_GROUPS.length, // 13
  totalSqFiles: 15, // actual .sq files in core/database/src/main/sqldelight/
  totalColumns: SCHEMA_TABLES.reduce((acc, t) => acc + t.columns.length, 0),
  totalIndexes: SCHEMA_TABLES.reduce(
    (acc, t) => acc + (t.indexes?.length ?? 0),
    0,
  ),
};

/* ---------------------------------------------------------------------------
 * Entity Relationship — boxes + lines for the top ER view.
 *
 * Each node is positioned on a 12-col × n-row grid. Edges connect by node id.
 * Rendered as an SVG overlay (lines) + CSS grid (boxes).
 *
 * Layout: `content` is the backbone (row 1 center). Lookups + per-source
 * detail tables branch from content. Library, cache, watch, downloads,
 * schedule, updates, genres, notifications, ratings all FK to content.main_id.
 * Standalone tables (no FK) — browse_cache, activity_event, app_metadata,
 * app_settings — are placed in their own row.
 * ------------------------------------------------------------------------- */

export interface ERNode {
  id: string;
  label: string;
  /** grid column 1-12 */
  col: number;
  /** grid row 1-7 */
  row: number;
  group: SchemaGroup;
}

export interface EREdge {
  from: string;
  to: string;
  label?: string;
  /** "one" / "many" — used for crow's-foot-style annotation. */
  cardinality?: "1-to-many" | "1-to-1" | "many-to-many";
}

export const ER_NODES: ERNode[] = [
  // Row 1 — Lookups + Content backbone + per-source details
  { id: "data_source", label: "data_source", col: 1, row: 1, group: "Content" },
  { id: "system", label: "system", col: 3, row: 1, group: "Content" },
  { id: "content", label: "content", col: 6, row: 1, group: "Content" },
  { id: "anilist_detail", label: "anilist_detail", col: 9, row: 1, group: "Content" },
  { id: "extension_detail", label: "extension_detail", col: 11, row: 1, group: "Content" },

  // Row 2 — Other-source detail + Library + Genres
  { id: "other_source_detail", label: "other_source_detail", col: 1, row: 2, group: "Content" },
  { id: "library_category", label: "library_category", col: 3, row: 2, group: "Library" },
  { id: "library_item", label: "library_item", col: 6, row: 2, group: "Library" },
  { id: "genre", label: "genre", col: 9, row: 2, group: "Genres" },
  { id: "content_genre", label: "content_genre", col: 11, row: 2, group: "Genres" },

  // Row 3 — Data cache trio
  { id: "anime_metadata_cache", label: "anime_metadata_cache", col: 3, row: 3, group: "Cache" },
  { id: "data_cache_episode", label: "data_cache_episode", col: 6, row: 3, group: "Cache" },
  { id: "browse_cache", label: "browse_cache", col: 9, row: 3, group: "Cache" },

  // Row 4 — Watch + Activity + App (standalone)
  { id: "watch_progress", label: "watch_progress", col: 3, row: 4, group: "Watch" },
  { id: "activity_event", label: "activity_event", col: 6, row: 4, group: "Activity" },
  { id: "app_metadata", label: "app_metadata", col: 9, row: 4, group: "App" },
  { id: "app_settings", label: "app_settings", col: 11, row: 4, group: "AppSettings" },

  // Row 5 — Updates + Schedule
  { id: "episode_update", label: "episode_update", col: 3, row: 5, group: "Updates" },
  { id: "anime_update_state", label: "anime_update_state", col: 6, row: 5, group: "Updates" },
  { id: "episode_schedule", label: "episode_schedule", col: 9, row: 5, group: "Schedule" },

  // Row 6 — Downloads
  { id: "download_queue", label: "download_queue", col: 3, row: 6, group: "Downloads" },
  { id: "downloaded_episode", label: "downloaded_episode", col: 6, row: 6, group: "Downloads" },

  // Row 7 — Notifications + Ratings
  { id: "notification_config", label: "notification_config", col: 3, row: 7, group: "Notifications" },
  { id: "notification_sent", label: "notification_sent", col: 6, row: 7, group: "Notifications" },
  { id: "user_rating", label: "user_rating", col: 9, row: 7, group: "Ratings" },
  { id: "user_episode_rating", label: "user_episode_rating", col: 11, row: 7, group: "Ratings" },
];

export const ER_EDGES: EREdge[] = [
  // Content backbone + lookups
  { from: "content", to: "data_source", cardinality: "many-to-many", label: "via data_source_id" },
  { from: "content", to: "system", cardinality: "many-to-many", label: "via system_id" },
  // Per-source detail tables (1-to-1)
  { from: "content", to: "anilist_detail", cardinality: "1-to-1" },
  { from: "content", to: "extension_detail", cardinality: "1-to-1" },
  { from: "content", to: "other_source_detail", cardinality: "1-to-many" },
  // Library
  { from: "content", to: "library_item", cardinality: "1-to-many" },
  { from: "library_category", to: "library_item", cardinality: "1-to-many", label: "via category_id" },
  // Genres (M:N)
  { from: "content", to: "content_genre", cardinality: "1-to-many" },
  { from: "genre", to: "content_genre", cardinality: "1-to-many" },
  // Data cache
  { from: "content", to: "anime_metadata_cache", cardinality: "1-to-1" },
  { from: "content", to: "data_cache_episode", cardinality: "1-to-many" },
  // (browse_cache standalone — no FK)
  // Watch + Updates + Schedule
  { from: "content", to: "watch_progress", cardinality: "1-to-many", label: "via main_id" },
  { from: "content", to: "episode_update", cardinality: "1-to-many" },
  { from: "content", to: "anime_update_state", cardinality: "1-to-1" },
  { from: "content", to: "episode_schedule", cardinality: "1-to-many" },
  // Downloads (denormalized — no explicit FK, but main_id references content)
  { from: "content", to: "download_queue", cardinality: "1-to-many", label: "denormalized" },
  { from: "content", to: "downloaded_episode", cardinality: "1-to-many", label: "denormalized" },
  // Notifications + Ratings
  { from: "content", to: "notification_config", cardinality: "1-to-1" },
  { from: "content", to: "notification_sent", cardinality: "1-to-many", label: "no explicit FK" },
  { from: "content", to: "user_rating", cardinality: "1-to-1" },
  { from: "content", to: "user_episode_rating", cardinality: "1-to-many" },
  // (activity_event, app_metadata, app_settings are standalone — no FK)
];
