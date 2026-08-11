/*
 * ANI-KUTA database schema — visual data (Phase 3 foundation + Phase WP/UP/SC/TR/NOTIF + D-166 DB optimization).
 *
 * Source: APP/ani-kuta/DOCUMENTATION/17-database-schema.md + core/database/src/main/sqldelight/
 *
 * 28 tables across 15 .sq files (13 logical groups for visualization). The 28 tables
 * below are the PLANNED Phase 1 schema representation — the dashboard visualizes the
 * schema as originally designed. The actual current schema (post-D-166) has the same
 * 28-table count but uses different table names (content, content_ext, anilist_detail,
 * etc. rather than content_uid, external_reference, etc.) — see AGENT-CONTEXT for the
 * canonical list. Hardcoded for the static dashboard demo — no API calls.
 *
 * Each table entry mirrors the SQL CREATE TABLE statement in the doc:
 *  - columns: ordered (name, type, constraints, isPK, isFK, fkTarget, desc)
 *  - indexes: list of named indexes
 *  - group: one of the 13 logical groups
 *  - deferred: true if Phase 6+
 */

export type SchemaGroup =
  | "Identity"
  | "Library"
  | "Watch"
  | "Downloads"
  | "Trackers"
  | "Extensions"
  | "Metadata"
  | "App"
  | "Activity"
  | "Ads"
  | "Updates"
  | "Ratings"
  | "Notifications";

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
  /** Fix annotations from the schema doc (S1, S2, S18, etc.). */
  fixNote?: string;
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
    name: "Identity",
    label: "Identity System",
    purpose: "ContentUID + ExternalReference + merge/split log",
    color: "#6366F1",
    colorVar: "var(--c-primary)",
    dot: "bg-[#6366F1]",
  },
  {
    name: "Library",
    label: "Library",
    purpose: "User's saved anime, categories, statuses",
    color: "#14B8A6",
    colorVar: "var(--c-success)",
    dot: "bg-[#14B8A6]",
  },
  {
    name: "Watch",
    label: "Watch Progress + History",
    purpose: "Per-episode watch progress + watch history log",
    color: "#F59E0B",
    colorVar: "var(--c-warning)",
    dot: "bg-[#F59E0B]",
  },
  {
    name: "Downloads",
    label: "Downloads",
    purpose: "Download queue + downloaded file metadata",
    color: "#8B5CF6",
    colorVar: "var(--c-secondary)",
    dot: "bg-[#8B5CF6]",
  },
  {
    name: "Trackers",
    label: "Trackers",
    purpose: "AniList / MAL / Shikimori sync links + sync state",
    color: "#FF6B6B",
    colorVar: "var(--c-danger)",
    dot: "bg-[#FF6B6B]",
  },
  {
    name: "Extensions",
    label: "Extensions",
    purpose: "Installed sources + extension repos",
    color: "#0EA5E9",
    colorVar: "#0EA5E9",
    dot: "bg-[#0EA5E9]",
  },
  {
    name: "Metadata",
    label: "Metadata Cache",
    purpose: "Content + episode metadata cache (AniList/extension)",
    color: "#EC4899",
    colorVar: "#EC4899",
    dot: "bg-[#EC4899]",
  },
  {
    name: "App",
    label: "App Metadata",
    purpose: "Key-value store for app flags (Phase 2)",
    color: "#22C55E",
    colorVar: "#22C55E",
    dot: "bg-[#22C55E]",
  },
  {
    name: "Activity",
    label: "Activity (deferred)",
    purpose: "User activity event log — Phase 6",
    color: "#A8A29E",
    colorVar: "#A8A29E",
    dot: "bg-[#A8A29E]",
  },
  {
    name: "Ads",
    label: "Ads (deferred)",
    purpose: "Ad impression log — Phase 6",
    color: "#71717A",
    colorVar: "#71717A",
    dot: "bg-[#71717A]",
  },
  {
    name: "Updates",
    label: "Updates + Schedule",
    purpose: "New-episode detection + per-anime update state + airing schedule (Phase UP + SC)",
    color: "#10B981",
    colorVar: "#10B981",
    dot: "bg-[#10B981]",
  },
  {
    name: "Ratings",
    label: "Ratings",
    purpose: "Per-anime + per-episode user ratings (Phase TR)",
    color: "#F43F5E",
    colorVar: "#F43F5E",
    dot: "bg-[#F43F5E]",
  },
  {
    name: "Notifications",
    label: "Notifications",
    purpose: "Notification preferences + sent log (Phase NOTIF)",
    color: "#06B6D4",
    colorVar: "#06B6D4",
    dot: "bg-[#06B6D4]",
  },
];

/* ---------------------------------------------------------------------------
 * 28 tables (planned Phase 1 schema representation) — hardcoded from 17-database-schema.md + Phase WP/UP/SC/TR/NOTIF additions + D-166 DB optimization (extensions.sq + metadata.sq deleted — neither appears below). The actual current schema post-D-166 has the same 28-table count across 15 .sq files but uses different table names (see AGENT-CONTEXT/memory/decisions.md D-166).
 * ------------------------------------------------------------------------- */

export const SCHEMA_TABLES: SchemaTable[] = [
  /* ---- Group 1: Identity System (5 tables) ---- */
  {
    name: "content_uid",
    group: "Identity",
    description:
      "The app's own stable ID for each piece of content (anime, manga, novel). UUID, stable forever.",
    columns: [
      { name: "uid", type: "TEXT", constraints: "PRIMARY KEY", isPK: true, desc: "UUID, app-generated, stable forever" },
      { name: "content_type", type: "TEXT", constraints: "NOT NULL CHECK (IN VIDEO|IMAGE|TEXT)", desc: "Content type enum" },
      { name: "title", type: "TEXT", constraints: "NOT NULL", desc: "Canonical title (best-known)" },
      { name: "match_key", type: "TEXT", constraints: "NOT NULL", desc: "Normalized title + year + type (fuzzy match)" },
      { name: "cover_url", type: "TEXT", desc: "Cover image URL (cached)" },
      { name: "year", type: "INTEGER", desc: "Release year (S18 fix)" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_content_match_key", on: "match_key", note: "Fuzzy matching" },
      { name: "idx_content_year", on: "year", note: "Year-distribution charts (S18)" },
    ],
    fixNote: "S18: added `year` for year-distribution charts on the My screen.",
  },
  {
    name: "external_reference",
    group: "Identity",
    description:
      "Links a ContentUID to an external system (Aniyomi source, Mangayomi source, AniList, MAL, etc.).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid", desc: "The app's ContentUID" },
      { name: "ecosystem", type: "TEXT", constraints: "NOT NULL", desc: "aniyomi|mangayomi|cloudstream|kotatsu|anilist|mal|shikimori" },
      { name: "source_id", type: "TEXT", desc: "Null for trackers (AniList, MAL)" },
      { name: "external_id", type: "TEXT", constraints: "NOT NULL", desc: "The external system's ID" },
      { name: "confidence", type: "TEXT", constraints: "NOT NULL CHECK (IN HIGH|MEDIUM|LOW)", desc: "Match confidence" },
      { name: "is_user_confirmed", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "S16: user-confirmed sync link" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_ext_ref_uid", on: "uid", note: "Find all refs for a content" },
      { name: "idx_ext_ref_unique_with_source", on: "(ecosystem, source_id, external_id)", unique: true, partial: true, note: "Partial unique for extension sources (source_id IS NOT NULL)" },
      { name: "idx_ext_ref_unique_no_source", on: "(ecosystem, external_id)", unique: true, partial: true, note: "Partial unique for trackers (source_id IS NULL)" },
    ],
    fixNote:
      "S1: replaced inline UNIQUE with two partial unique indexes (SQLite treats NULL as distinct). S16: added `is_user_confirmed` — coexists with tracker_link.",
  },
  {
    name: "episode_uid",
    group: "Identity",
    description: "The app's stable ID for each episode. Episodes have their own stable IDs.",
    columns: [
      { name: "uid", type: "TEXT", constraints: "PRIMARY KEY", isPK: true, desc: "UUID" },
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid", desc: "Parent content" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Supports 5.5 for OVAs" },
      { name: "match_key", type: "TEXT", constraints: "NOT NULL", desc: "Normalized title + number (fuzzy match)" },
    ],
    uniques: ["(content_uid, episode_number) — one episode per number per content"],
    indexes: [{ name: "idx_episode_content", on: "content_uid" }],
  },
  {
    name: "episode_external_ref",
    group: "Identity",
    description: "Links an EpisodeUID to an external system's episode.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "episode_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "episode_uid.uid" },
      { name: "ecosystem", type: "TEXT", constraints: "NOT NULL" },
      { name: "source_id", type: "TEXT" },
      { name: "external_id", type: "TEXT", constraints: "NOT NULL" },
      { name: "confidence", type: "TEXT", constraints: "NOT NULL CHECK (IN HIGH|MEDIUM|LOW)" },
    ],
    indexes: [
      { name: "idx_episode_ext_ref_uid", on: "episode_uid" },
      { name: "idx_episode_ext_ref_unique_with_source", on: "(ecosystem, source_id, external_id)", unique: true, partial: true, note: "S1 partial unique for sources" },
      { name: "idx_episode_ext_ref_unique_no_source", on: "(ecosystem, external_id)", unique: true, partial: true, note: "S1 partial unique for trackers" },
    ],
    fixNote: "S1: same partial-unique-index pattern as external_reference.",
  },
  {
    name: "identity_event",
    group: "Identity",
    description:
      "Log of user-initiated identity operations (merge, split, auto-link). Enables undo.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "event_type", type: "TEXT", constraints: "NOT NULL CHECK (IN MERGE|SPLIT|AUTO_LINK|DISMISS_SUGGESTION)" },
      { name: "primary_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid", desc: "Surviving ContentUID (MERGE) or original (SPLIT)" },
      { name: "secondary_uid", type: "TEXT", desc: "Merged-away ContentUID (MERGE) or null" },
      { name: "ref_id_affected", type: "INTEGER", desc: "The external_reference.id moved/created/split" },
      { name: "reason", type: "TEXT", desc: "e.g. user_confirmed, auto_fuzzy_match" },
      { name: "performed_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
      { name: "undone_at", type: "INTEGER", desc: "Null if not undone" },
    ],
    indexes: [
      { name: "idx_identity_event_primary", on: "primary_uid", partial: true, note: "WHERE undone_at IS NULL" },
      { name: "idx_identity_event_undone", on: "undone_at" },
    ],
    fixNote: "S2: added `identity_event` — without it, undo is impossible.",
  },

  /* ---- Group 2: Library (3 tables) ---- */
  {
    name: "category",
    group: "Library",
    description: "User-defined categories (Watching, Completed, Plan to Watch, etc.).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "name", type: "TEXT", constraints: "NOT NULL", desc: "Category name" },
      { name: "content_type", type: "TEXT", constraints: "NOT NULL CHECK (IN VIDEO|IMAGE|TEXT)", desc: "S4: separate anime/manga categories" },
      { name: "sort_order", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Display order" },
      { name: "created_at", type: "INTEGER", constraints: "NOT NULL" },
    ],
    uniques: ["(content_type, name COLLATE NOCASE) — case-insensitive"],
    fixNote:
      "S4: added `content_type` (Aniyomi maintains separate anime/manga categories). S23: `COLLATE NOCASE`.",
  },
  {
    name: "library_entry",
    group: "Library",
    description: "An anime in the user's library. PK = content_uid (one entry per content).",
    columns: [
      { name: "uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "content_uid.uid", desc: "= content_uid.uid" },
      { name: "status", type: "TEXT", constraints: "NOT NULL", desc: "WATCHING|COMPLETED|PAUSED|DROPPED|PLAN_TO_WATCH" },
      { name: "score", type: "INTEGER", desc: "0-100 (user rating)" },
      { name: "notes", type: "TEXT", desc: "User notes" },
      { name: "last_episode_watched", type: "REAL", desc: "Last watched episode number" },
      { name: "total_episodes", type: "INTEGER", desc: "Cached from metadata" },
      { name: "added_at", type: "INTEGER", constraints: "NOT NULL" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL" },
    ],
    indexes: [{ name: "idx_library_status", on: "status" }],
  },
  {
    name: "library_entry_category",
    group: "Library",
    description: "Many-to-many between library entries and categories.",
    columns: [
      { name: "library_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "library_entry.uid" },
      { name: "category_id", type: "INTEGER", constraints: "NOT NULL, FK", isFK: true, fkTarget: "category.id" },
    ],
    compositePK: ["library_uid", "category_id"],
  },

  /* ---- Group 3: Watch (2 tables) ---- */
  {
    name: "watch_progress",
    group: "Watch",
    description: "Per-episode watch progress. Keyed by episode_uid.",
    columns: [
      { name: "episode_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "episode_uid.uid" },
      { name: "position", type: "INTEGER", constraints: "NOT NULL", desc: "Position in seconds" },
      { name: "duration", type: "INTEGER", constraints: "NOT NULL", desc: "Total duration in seconds" },
      { name: "completed", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "S11: explicit completion flag" },
      { name: "completed_at", type: "INTEGER", desc: "S11: completion timestamp" },
      { name: "last_watched_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_watch_progress_last_watched", on: "last_watched_at DESC", note: "Continue Watching queries (S7)" },
    ],
    fixNote:
      "S7: index on `last_watched_at`. S11: explicit `completed` + `completed_at` (replaces fragile 90% threshold).",
  },
  {
    name: "history",
    group: "Watch",
    description: "Watch history log (every time the user watches an episode).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "episode_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "episode_uid.uid" },
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid", desc: "Denormalized for fast queries" },
      { name: "watched_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
      { name: "duration_watched", type: "INTEGER", constraints: "NOT NULL", desc: "Seconds watched in this session" },
      { name: "episode_duration", type: "INTEGER", desc: "S20: duration at watch time" },
    ],
    indexes: [
      { name: "idx_history_watched_at", on: "watched_at DESC", note: "Recent history" },
      { name: "idx_history_content", on: "content_uid", note: "Per-content history" },
      { name: "idx_history_unique", on: "(content_uid, episode_uid, watched_at)", unique: true, note: "S9: merge dedup (UNION by contentUid, episodeUid, timestamp)" },
    ],
    fixNote:
      "S9: unique index for merge dedup. S20: added `episode_duration` for accurate historical stats.",
  },

  /* ---- Group 4: Downloads (2 tables) ---- */
  {
    name: "download_queue",
    group: "Downloads",
    description: "The download queue (episodes to download).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "episode_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "episode_uid.uid" },
      { name: "state", type: "TEXT", constraints: "NOT NULL", desc: "QUEUED|DOWNLOADING|PAUSED|COMPLETED|FAILED" },
      { name: "progress", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "0-100" },
      { name: "error_message", type: "TEXT", desc: "If failed" },
      { name: "queued_at", type: "INTEGER", constraints: "NOT NULL" },
      { name: "started_at", type: "INTEGER" },
      { name: "completed_at", type: "INTEGER" },
    ],
    indexes: [{ name: "idx_download_state", on: "state" }],
  },
  {
    name: "downloaded_episode",
    group: "Downloads",
    description: "Downloaded episode files on disk (metadata only — files on disk).",
    columns: [
      { name: "episode_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "episode_uid.uid" },
      { name: "file_path", type: "TEXT", constraints: "NOT NULL", desc: "Path to downloaded file" },
      { name: "file_size", type: "INTEGER", constraints: "NOT NULL", desc: "Bytes" },
      { name: "quality", type: "TEXT", desc: "e.g. 1080p, 720p" },
      { name: "downloaded_at", type: "INTEGER", constraints: "NOT NULL" },
    ],
  },

  /* ---- Group 5: Trackers (2 tables) ---- */
  {
    name: "tracker_link",
    group: "Trackers",
    description: "Links a ContentUID to a tracker (AniList, MAL, Shikimori).",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid" },
      { name: "tracker_type", type: "TEXT", constraints: "NOT NULL", desc: "anilist|mal|shikimori" },
      { name: "tracker_id", type: "INTEGER", constraints: "NOT NULL", desc: "The tracker's ID for this anime" },
    ],
    compositePK: ["content_uid", "tracker_type"],
  },
  {
    name: "tracker_sync_state",
    group: "Trackers",
    description: "Sync state per tracker (when did we last sync?).",
    columns: [
      { name: "tracker_type", type: "TEXT", constraints: "PRIMARY KEY", isPK: true, desc: "anilist|mal|shikimori" },
      { name: "username", type: "TEXT", desc: "Logged-in user" },
      { name: "last_synced_at", type: "INTEGER", desc: "Epoch millis" },
      { name: "token_expires_at", type: "INTEGER", desc: "OAuth token expiry (token in Keystore — S6)" },
    ],
    fixNote:
      "S6: OAuth tokens (access_token + refresh_token) stored in EncryptedSharedPreferences (Android Keystore), NOT in the DB.",
  },

  /* ---- Group 6: Extensions (2 tables) ---- */
  {
    name: "installed_source",
    group: "Extensions",
    description: "Installed extension sources (Aniyomi/Mangayomi etc.).",
    columns: [
      { name: "ecosystem", type: "TEXT", constraints: "NOT NULL", desc: "aniyomi|mangayomi|etc." },
      { name: "source_id", type: "TEXT", constraints: "NOT NULL", desc: "Source ID within ecosystem" },
      { name: "name", type: "TEXT", constraints: "NOT NULL", desc: "Display name" },
      { name: "version", type: "TEXT", constraints: "NOT NULL", desc: "Extension version" },
      { name: "package_name", type: "TEXT", constraints: "NOT NULL", desc: "S10: PackageInstaller integration" },
      { name: "signature_fingerprint", type: "TEXT", desc: "S10: SHA-256 trust verification" },
      { name: "is_enabled", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "0 or 1" },
      { name: "installed_at", type: "INTEGER", constraints: "NOT NULL" },
      { name: "last_updated_at", type: "INTEGER" },
    ],
    compositePK: ["ecosystem", "source_id"],
    indexes: [{ name: "idx_installed_source_package", on: "package_name" }],
    fixNote: "S10: added `package_name` + `signature_fingerprint`.",
  },
  {
    name: "extension_repo",
    group: "Extensions",
    description: "Extension repositories (URLs that serve extension APKs).",
    columns: [
      { name: "ecosystem", type: "TEXT", constraints: "NOT NULL", desc: "S5: distinguish Aniyomi/Mangayomi repos" },
      { name: "url", type: "TEXT", constraints: "NOT NULL", desc: "Repo URL" },
      { name: "name", type: "TEXT", constraints: "NOT NULL", desc: "Display name" },
      { name: "added_at", type: "INTEGER", constraints: "NOT NULL" },
    ],
    compositePK: ["ecosystem", "url"],
    fixNote: "S5: added `ecosystem` — PK is now `(ecosystem, url)`.",
  },

  /* ---- Group 7: Metadata Cache (2 tables) ---- */
  {
    name: "content_metadata_cache",
    group: "Metadata",
    description:
      "Content-level metadata (description, genres, status, year, author, artist). Cached from AniList/extension sources.",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "content_uid.uid" },
      { name: "description", type: "TEXT", desc: "Synopsis / description" },
      { name: "genres", type: "TEXT", desc: "JSON array of genre strings" },
      { name: "status", type: "TEXT", desc: "RELEASING|FINISHED|NOT_YET_RELEASED|CANCELLED" },
      { name: "year", type: "INTEGER", desc: "Release year" },
      { name: "author", type: "TEXT", desc: "Author/artist (manga — future)" },
      { name: "artist", type: "TEXT", desc: "Artist (manga — future)" },
      { name: "source", type: "TEXT", constraints: "NOT NULL", desc: "anilist|extension|etc." },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "When cached" },
    ],
    fixNote: "S3: added `content_metadata_cache` (Details screen + Backup target).",
  },
  {
    name: "episode_metadata_cache",
    group: "Metadata",
    description: "Cached episode metadata (thumbnails, titles, air dates).",
    columns: [
      { name: "episode_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "episode_uid.uid" },
      { name: "title", type: "TEXT", desc: "Episode title" },
      { name: "thumbnail_url", type: "TEXT", desc: "Episode thumbnail" },
      { name: "air_date", type: "INTEGER", desc: "Air date (epoch millis)" },
      { name: "description", type: "TEXT", desc: "Episode synopsis" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "When cached" },
    ],
  },

  /* ---- Group 8: App Metadata (1 table — already exists) ---- */
  {
    name: "app_metadata",
    group: "App",
    description:
      "Key-value store for app-level flags (schema version, migration flags, etc.). Already exists in Phase 2.",
    columns: [
      { name: "key", type: "TEXT", constraints: "PRIMARY KEY", isPK: true },
      { name: "value", type: "TEXT", constraints: "NOT NULL" },
    ],
  },

  /* ---- Group 9: Activity (DEFERRED — Phase 6) ---- */
  {
    name: "activity_event",
    group: "Activity",
    deferred: true,
    description:
      "User activity event log. Retention: 365 days default, unlimited option.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "event_type", type: "TEXT", constraints: "NOT NULL", desc: "WATCH|SEARCH|BROWSE|DOWNLOAD|AD_SHOWN|etc." },
      { name: "content_uid", type: "TEXT", isFK: true, fkTarget: "content_uid.uid", desc: "Nullable for non-content events" },
      { name: "episode_uid", type: "TEXT", isFK: true, fkTarget: "episode_uid.uid", desc: "Nullable" },
      { name: "session_id", type: "TEXT", constraints: "NOT NULL", desc: "App session ID (for grouping)" },
      { name: "route", type: "TEXT", desc: "Screen route when event occurred" },
      { name: "content_type", type: "TEXT", desc: "VIDEO|IMAGE|TEXT" },
      { name: "duration_ms", type: "INTEGER", desc: "Event duration (e.g. watch time)" },
      { name: "payload", type: "TEXT", desc: "JSON blob for extra data" },
      { name: "timestamp", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_activity_timestamp", on: "timestamp DESC" },
      { name: "idx_activity_type", on: "event_type" },
      { name: "idx_activity_content", on: "content_uid" },
    ],
  },

  /* ---- Group 10: Ads (DEFERRED — Phase 6) ---- */
  {
    name: "ad_impression",
    group: "Ads",
    deferred: true,
    description: "Log of ads shown to the user.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "placement", type: "TEXT", constraints: "NOT NULL", desc: "e.g. anime_details_open, episode_start" },
      { name: "format", type: "TEXT", constraints: "NOT NULL", desc: "interstitial|redirect|video|banner" },
      { name: "content_uid", type: "TEXT", isFK: true, fkTarget: "content_uid.uid", desc: "Associated content (nullable)" },
      { name: "shown_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
      { name: "completed", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "1 if watched to completion, 0 if skipped" },
    ],
    indexes: [{ name: "idx_ad_shown_at", on: "shown_at DESC" }],
  },

  /* ---- Group 11: Updates + Schedule (Phase UP + SC) — 3 new tables ---- */
  {
    name: "episode_update",
    group: "Updates",
    description:
      "Per-episode update record. One row per detected new episode (WorkManager-driven). Phase UP.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid", desc: "Parent content" },
      { name: "episode_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "episode_uid.uid", desc: "The episode" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number (denormalized for fast queries)" },
      { name: "seen", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "0 = unread on Updates screen, 1 = seen by user" },
      { name: "is_subbed", type: "INTEGER", desc: "T4 sub/dub flag — 1 if subbed source detected" },
      { name: "is_dubbed", type: "INTEGER", desc: "T4 sub/dub flag — 1 if dubbed source detected" },
      { name: "detected_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis — when the worker found this episode" },
    ],
    indexes: [
      { name: "idx_episode_update_content", on: "content_uid" },
      { name: "idx_episode_update_seen", on: "seen", note: "Filter unseen updates" },
      { name: "idx_episode_update_detected", on: "detected_at DESC", note: "Recent-first ordering on Updates screen" },
    ],
    uniques: ["(content_uid, episode_uid) — one row per content+episode"],
  },
  {
    name: "anime_update_state",
    group: "Updates",
    description:
      "Per-anime update state — drives the smart-engine backoff (T2 next_check_at) + self-improving interval (T3). Phase UP.",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "content_uid.uid" },
      { name: "status", type: "TEXT", constraints: "NOT NULL", desc: "RELEASING|FINISHED|NOT_YET_RELEASED|CANCELLED — T1 status filter (only RELEASING checked actively)" },
      { name: "last_checked_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis — last WorkManager run for this anime" },
      { name: "next_check_at", type: "INTEGER", constraints: "NOT NULL", desc: "T2 backoff — when to check next (increases if no new episodes found)" },
      { name: "consecutive_misses", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "T3 self-improving — how many checks in a row found nothing (drives next_check_at interval)" },
      { name: "strike_count", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "M3 3-strike rule — auto-pause checks after 3 consecutive failures" },
      { name: "suppressed_for_watched", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "M5 — suppress updates for anime the user is finished with" },
    ],
    indexes: [
      { name: "idx_anime_update_next_check", on: "next_check_at", note: "Worker queries WHERE next_check_at <= now ORDER BY next_check_at" },
      { name: "idx_anime_update_status", on: "status", note: "T1 — only check RELEASING anime" },
    ],
  },
  {
    name: "episode_schedule",
    group: "Updates",
    description:
      "AniList airing schedule for currently-releasing anime. Powers the Schedule screen + actual-release tracking (Phase SC). actual_at is populated by the ActualReleaseUpdater when the WorkManager detects the episode is out.",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid" },
      { name: "episode_number", type: "REAL", constraints: "NOT NULL", desc: "Episode number" },
      { name: "airing_at", type: "INTEGER", constraints: "NOT NULL", desc: "AniList predicted airing time (epoch millis)" },
      { name: "actual_at", type: "INTEGER", desc: "Phase SC-2 — actual release time (set when WorkManager detects the episode); null until then" },
      { name: "time_until_airing", type: "INTEGER", desc: "AniList seconds-until-airing snapshot (for live countdown on Schedule screen)" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "When this row was last refreshed" },
    ],
    compositePK: ["content_uid", "episode_number"],
    indexes: [
      { name: "idx_schedule_airing", on: "airing_at ASC", note: "Schedule screen ordering" },
      { name: "idx_schedule_actual", on: "actual_at", note: "Find episodes whose actual_at just got populated" },
    ],
  },

  /* ---- Group 12: Ratings (Phase TR) — 2 new tables ---- */
  {
    name: "user_rating",
    group: "Ratings",
    description:
      "Per-anime user rating (0-100). Separate from library_entry.score so ratings survive library unbinding. Phase TR.",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "content_uid.uid" },
      { name: "rating", type: "INTEGER", constraints: "NOT NULL", desc: "0-100 (user rating)" },
      { name: "rated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis — last rating change" },
    ],
    indexes: [{ name: "idx_user_rating_rated_at", on: "rated_at DESC" }],
  },
  {
    name: "user_episode_rating",
    group: "Ratings",
    description:
      "Per-episode user rating (0-100). Powers the episode-level rating UI (Phase TR). Composite key on (content_uid, episode_uid).",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid" },
      { name: "episode_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "episode_uid.uid" },
      { name: "rating", type: "INTEGER", constraints: "NOT NULL", desc: "0-100 (user rating)" },
      { name: "rated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    compositePK: ["content_uid", "episode_uid"],
    indexes: [{ name: "idx_user_episode_rating_rated_at", on: "rated_at DESC" }],
  },

  /* ---- Group 13: Notifications (Phase NOTIF) — 2 new tables ---- */
  {
    name: "notification_config",
    group: "Notifications",
    description:
      "Per-anime notification preferences (enabled/disabled, lead time, per-channel toggles). Phase NOTIF.",
    columns: [
      { name: "content_uid", type: "TEXT", constraints: "PRIMARY KEY, FK", isPK: true, isFK: true, fkTarget: "content_uid.uid" },
      { name: "enabled", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "0 = notifications muted for this anime" },
      { name: "notify_on_release", type: "INTEGER", constraints: "NOT NULL DEFAULT 1", desc: "Notify when a new episode is detected" },
      { name: "notify_on_schedule", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Notify when an episode airs (per the schedule)" },
      { name: "lead_time_minutes", type: "INTEGER", constraints: "NOT NULL DEFAULT 0", desc: "Notify N minutes before airing" },
      { name: "updated_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [{ name: "idx_notification_config_enabled", on: "enabled", note: "Find anime with notifications on" }],
  },
  {
    name: "notification_sent",
    group: "Notifications",
    description:
      "Log of sent notifications — used for dedup (don't notify twice for the same episode) + analytics. Phase NOTIF.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PRIMARY KEY AUTOINCREMENT", isPK: true },
      { name: "content_uid", type: "TEXT", constraints: "NOT NULL, FK", isFK: true, fkTarget: "content_uid.uid" },
      { name: "episode_uid", type: "TEXT", isFK: true, fkTarget: "episode_uid.uid", desc: "Nullable for non-episode notifications" },
      { name: "channel", type: "TEXT", constraints: "NOT NULL", desc: "release|schedule|download|system" },
      { name: "title", type: "TEXT", constraints: "NOT NULL", desc: "Notification title" },
      { name: "body", type: "TEXT", desc: "Notification body" },
      { name: "sent_at", type: "INTEGER", constraints: "NOT NULL", desc: "Epoch millis" },
    ],
    indexes: [
      { name: "idx_notification_sent_sent_at", on: "sent_at DESC" },
      { name: "idx_notification_sent_unique", on: "(content_uid, episode_uid, channel)", unique: true, partial: true, note: "Dedup — one notification per (content, episode, channel)" },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Summary — drives the top summary card.
 * ------------------------------------------------------------------------- */

export const SCHEMA_SUMMARY = {
  totalTables: SCHEMA_TABLES.length, // 28
  activeTables: SCHEMA_TABLES.filter((t) => !t.deferred).length, // 26
  deferredTables: SCHEMA_TABLES.filter((t) => t.deferred).length, // 2
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
  // Row 1 — Identity backbone
  { id: "content_uid", label: "content_uid", col: 3, row: 1, group: "Identity" },
  { id: "external_reference", label: "external_reference", col: 6, row: 1, group: "Identity" },
  { id: "identity_event", label: "identity_event", col: 9, row: 1, group: "Identity" },
  // Row 2 — Library + Episode
  { id: "episode_uid", label: "episode_uid", col: 3, row: 2, group: "Identity" },
  { id: "library_entry", label: "library_entry", col: 6, row: 2, group: "Library" },
  { id: "category", label: "category", col: 9, row: 2, group: "Library" },
  // Row 3 — Watch + Metadata
  { id: "watch_progress", label: "watch_progress", col: 2, row: 3, group: "Watch" },
  { id: "history", label: "history", col: 4, row: 3, group: "Watch" },
  { id: "episode_metadata_cache", label: "episode_metadata_cache", col: 6, row: 3, group: "Metadata" },
  { id: "content_metadata_cache", label: "content_metadata_cache", col: 9, row: 3, group: "Metadata" },
  // Row 4 — Downloads + Trackers
  { id: "download_queue", label: "download_queue", col: 2, row: 4, group: "Downloads" },
  { id: "downloaded_episode", label: "downloaded_episode", col: 4, row: 4, group: "Downloads" },
  { id: "tracker_link", label: "tracker_link", col: 6, row: 4, group: "Trackers" },
  { id: "tracker_sync_state", label: "tracker_sync_state", col: 9, row: 4, group: "Trackers" },
  // Row 5 — Extensions + App + Deferred
  { id: "installed_source", label: "installed_source", col: 2, row: 5, group: "Extensions" },
  { id: "extension_repo", label: "extension_repo", col: 4, row: 5, group: "Extensions" },
  { id: "app_metadata", label: "app_metadata", col: 6, row: 5, group: "App" },
  { id: "activity_event", label: "activity_event", col: 9, row: 5, group: "Activity" },
  { id: "ad_impression", label: "ad_impression", col: 11, row: 5, group: "Ads" },
  // Row 6 — Updates + Schedule + Ratings (Phase UP/SC/TR)
  { id: "episode_update", label: "episode_update", col: 2, row: 6, group: "Updates" },
  { id: "anime_update_state", label: "anime_update_state", col: 4, row: 6, group: "Updates" },
  { id: "episode_schedule", label: "episode_schedule", col: 6, row: 6, group: "Updates" },
  { id: "user_rating", label: "user_rating", col: 9, row: 6, group: "Ratings" },
  { id: "user_episode_rating", label: "user_episode_rating", col: 11, row: 6, group: "Ratings" },
  // Row 7 — Notifications (Phase NOTIF)
  { id: "notification_config", label: "notification_config", col: 4, row: 7, group: "Notifications" },
  { id: "notification_sent", label: "notification_sent", col: 6, row: 7, group: "Notifications" },
];

export const ER_EDGES: EREdge[] = [
  // Identity backbone
  { from: "content_uid", to: "external_reference", cardinality: "1-to-many" },
  { from: "content_uid", to: "identity_event", cardinality: "1-to-many" },
  { from: "content_uid", to: "episode_uid", cardinality: "1-to-many" },
  // Library
  { from: "content_uid", to: "library_entry", cardinality: "1-to-1" },
  { from: "library_entry", to: "category", cardinality: "many-to-many", label: "via library_entry_category" },
  // Watch
  { from: "episode_uid", to: "watch_progress", cardinality: "1-to-1" },
  { from: "episode_uid", to: "history", cardinality: "1-to-many" },
  { from: "content_uid", to: "history", cardinality: "1-to-many", label: "denormalized" },
  // Metadata
  { from: "content_uid", to: "content_metadata_cache", cardinality: "1-to-1" },
  { from: "episode_uid", to: "episode_metadata_cache", cardinality: "1-to-1" },
  // Downloads
  { from: "episode_uid", to: "download_queue", cardinality: "1-to-many" },
  { from: "episode_uid", to: "downloaded_episode", cardinality: "1-to-1" },
  // Trackers
  { from: "content_uid", to: "tracker_link", cardinality: "1-to-many" },
  // Activity / Ads (deferred)
  { from: "content_uid", to: "activity_event", cardinality: "1-to-many" },
  { from: "content_uid", to: "ad_impression", cardinality: "1-to-many" },
  // Updates + Schedule (Phase UP + SC)
  { from: "content_uid", to: "anime_update_state", cardinality: "1-to-1" },
  { from: "episode_uid", to: "episode_update", cardinality: "1-to-many" },
  { from: "content_uid", to: "episode_schedule", cardinality: "1-to-many" },
  // Ratings (Phase TR)
  { from: "content_uid", to: "user_rating", cardinality: "1-to-1" },
  { from: "episode_uid", to: "user_episode_rating", cardinality: "1-to-many", label: "via content_uid" },
  // Notifications (Phase NOTIF)
  { from: "content_uid", to: "notification_config", cardinality: "1-to-1" },
  { from: "content_uid", to: "notification_sent", cardinality: "1-to-many" },
];
