/*
 * Phase D — Data Management & Caching (IMPLEMENTED).
 *
 * Source: Phase D plan — local-first storage, smart refresh, image caching.
 *   - 3 new database tables: anime_metadata_cache, episode_metadata_cache, browse_cache
 *   - Multi-stage refresh on details page (episodes → metadata → all) with vibration
 *   - 6-hour auto-update on homepage only (not other pages)
 *   - Image caching via Coil's disk cache (500MB, persistent — survives restart)
 *   - Solid caching (all data persists across restarts)
 *   - Two source types properly separated: data source (AniList/TMDB) + extension source
 *
 * Status: All 5 milestones (D.1–D.5) implemented + CI verified GREEN.
 *
 * Hardcoded for the static dashboard demo — no API calls.
 */

/* ---------------------------------------------------------------------------
 * Hero / status
 * ------------------------------------------------------------------------- */

export const PHASE_D_HERO = {
  title: "Phase D — Data Management & Caching",
  subtitle:
    "Local-first storage, smart multi-stage refresh, image caching — network only for refresh",
  status: "IMPLEMENTED — ALL MILESTONES DONE",
  statusColor: "var(--c-success)",
  summary:
    "Phase D introduces a local-first data layer for the app. All metadata (anime info, episode info, covers) is stored locally in the database — the network is only used for refresh or opening new content. A multi-stage refresh system on the details page (episodes list → metadata → full refresh) provides fine-grained control with vibration feedback. A 6-hour auto-update runs on the homepage only. Image caching via Coil's disk cache (500MB, persistent) ensures covers + thumbnails survive restart. Status: COMPLETE — all 5 milestones (D.1–D.5) implemented + CI verified GREEN on branch `main` (all feature branches merged + deleted).",
} as const;

/* ---------------------------------------------------------------------------
 * 1. Problem statement — 4 cards
 * ------------------------------------------------------------------------- */

export interface ProblemCard {
  key: string;
  title: string;
  icon: string; // emoji or short symbol
  description: string;
  impact: string;
  color: string;
}

export const PROBLEM_STATEMENT: ProblemCard[] = [
  {
    key: "slow",
    title: "Slow Loading",
    icon: "🐌",
    color: "var(--c-danger)",
    description:
      "Every screen waits for the network before rendering. The browse page fetches trending anime from AniList on every open. The details page fetches full AniList data every time an anime is opened.",
    impact: "Every screen waits for network",
  },
  {
    key: "data-usage",
    title: "Unnecessary Data Usage",
    icon: "📶",
    color: "var(--c-warning)",
    description:
      "The same data is fetched repeatedly — anime metadata, episode metadata, and browse sections are re-downloaded on every visit, even when nothing has changed since the last open.",
    impact: "Same data fetched repeatedly",
  },
  {
    key: "offline",
    title: "No Offline Support",
    icon: "✈️",
    color: "var(--c-secondary)",
    description:
      "The library page cannot be browsed without a network connection. Even though the user has already added content, opening the library tab makes a network call to fetch metadata.",
    impact: "Can't browse library without network",
  },
  {
    key: "lost",
    title: "Data Lost on Restart",
    icon: "🔄",
    color: "var(--c-danger)",
    description:
      "The current in-memory cache (D-141) is wiped when the app is killed. Every restart triggers a full re-fetch, even though the data was just loaded minutes ago.",
    impact: "In-memory cache only — not persisted",
  },
];

/* ---------------------------------------------------------------------------
 * 2. Goals — 6 cards
 * ------------------------------------------------------------------------- */

export interface GoalCard {
  key: string;
  number: number;
  title: string;
  tagline: string;
  description: string;
  bullets: string[];
  color: string;
}

export const GOALS: GoalCard[] = [
  {
    key: "local-first",
    number: 1,
    title: "Local-first Data Storage",
    tagline: "DB is the source of truth",
    color: "var(--c-primary)",
    description:
      "All metadata (anime info, episode info, covers) stored locally in the database. Network is only used for refresh or opening new content.",
    bullets: [
      "Anime metadata cached in `anime_metadata_cache` (by mainId).",
      "Episode metadata cached in `episode_metadata_cache` (by mainId + episodeNumber).",
      "Browse sections cached in `browse_cache` (by section_key).",
      "Network only used for refresh or opening NEW content.",
    ],
  },
  {
    key: "smart-refresh",
    number: 2,
    title: "Smart Refresh",
    tagline: "Multi-stage · vibration · 6hr auto (homepage only)",
    color: "var(--c-secondary)",
    description:
      "Multi-stage refresh on the details page (episodes list → metadata → full refresh) with vibration. Pull-to-refresh on browse page. 6-hour auto-update on the homepage only (not other pages).",
    bullets: [
      "Details page: scroll-triggered stages (episodes → metadata → all) with vibration.",
      "Browse page: pull-to-refresh + 6-hour auto-update (homepage only).",
      "Library page: loads from cache, pull-to-refresh forces re-fetch.",
      "Visual indicators + spinning loader at each stage.",
    ],
  },
  {
    key: "image-cache",
    number: 3,
    title: "Image Caching",
    tagline: "500MB · Coil · survives restart",
    color: "var(--c-success)",
    description:
      "Cover images + episode thumbnails stored locally via Coil's disk cache (500MB, configurable in future). Survives restart.",
    bullets: [
      "Coil's built-in `diskCache` with 500MB max size.",
      "Images cached by URL — no manual download needed.",
      "Persistent (stored in app's cache directory).",
      "Cover image auto-updates when URL changes (Coil re-fetches).",
    ],
  },
  {
    key: "solid-cache",
    number: 4,
    title: "Solid Caching",
    tagline: "Persists across restarts",
    color: "var(--c-warning)",
    description:
      "All cached data persists across restarts. No data is lost when the device shuts down.",
    bullets: [
      "All cache tables backed by SQLDelight (SQLite).",
      "In-memory cache (D-141) is replaced by the persistent DB cache.",
      "Data survives app kill, device shutdown, and OS restart.",
      "Library opens instantly from cache on cold start.",
    ],
  },
  {
    key: "performance",
    number: 5,
    title: "Performance",
    tagline: "Instant library · lazy loading",
    color: "var(--c-primary)",
    description:
      "Library loads instantly from local storage. No network calls on tab switch. Lazy loading for smooth scrolling.",
    bullets: [
      "Library loads entirely from `anime_metadata_cache`.",
      "No network calls on tab switch.",
      "`LazyVerticalGrid` / `LazyColumn` with proper keys (`mainId`).",
      "Paginate when library > 100 entries.",
      "Pre-load nearby images (Coil auto).",
      "`derivedStateOf` for filter/sort computations.",
    ],
  },
  {
    key: "two-sources",
    number: 6,
    title: "Two Source Types",
    tagline: "Data source + extension source",
    color: "var(--c-danger)",
    description:
      "Properly handle both the AniList data source (metadata, synopsis, score) AND the extension source (episodes, playing source info).",
    bullets: [
      "Data source (AniList/TMDB/Kitsu) → `anime_metadata_cache` (source_type='anilist').",
      "Extension source (Aniyomi) → `episode_metadata_cache` (episodes list + URLs).",
      "Both caches updated independently on refresh.",
      "Data-source selector (AniList/Extension toggle) determines which cache to read from for display.",
    ],
  },
];

/* ---------------------------------------------------------------------------
 * 3. Database schema — 3 new tables
 * ------------------------------------------------------------------------- */

export type PhaseDGroup = "metadata" | "browse";

export interface PhaseDGroupMeta {
  name: PhaseDGroup;
  label: string;
  purpose: string;
  color: string;
}

export const PHASE_D_GROUPS: PhaseDGroupMeta[] = [
  {
    name: "metadata",
    label: "Metadata Caches",
    purpose:
      "Per-content metadata keyed by mainId — never expires (user manually refreshes)",
    color: "#8B5CF6",
  },
  {
    name: "browse",
    label: "Browse Cache",
    purpose:
      "Browse-page sections (trending, popular) — auto-expires after 6 hours (homepage only)",
    color: "#F59E0B",
  },
];

export const PHASE_D_GROUP_COLOR: Record<PhaseDGroup, string> =
  PHASE_D_GROUPS.reduce(
    (acc, g) => ({ ...acc, [g.name]: g.color }),
    {} as Record<PhaseDGroup, string>,
  );

export const PHASE_D_GROUP_LABEL: Record<PhaseDGroup, string> =
  PHASE_D_GROUPS.reduce(
    (acc, g) => ({ ...acc, [g.name]: g.label }),
    {} as Record<PhaseDGroup, string>,
  );

export interface PhaseDColumn {
  name: string;
  type: string;
  constraints: string;
  description: string;
}

export interface PhaseDTable {
  name: string;
  group: PhaseDGroup;
  description: string;
  isMain?: boolean;
  isNew?: boolean;
  compositePK?: string[];
  columns: PhaseDColumn[];
  demoRows: string[][];
}

export const PHASE_D_TABLES: PhaseDTable[] = [
  // ============ METADATA CACHES (2) ============
  {
    name: "anime_metadata_cache",
    group: "metadata",
    isNew: true,
    description:
      "Full anime metadata for each content (by mainId). NEVER expires — no `expires_at` column. User manually refreshes via the refresh button or pull-to-refresh. `fetched_at` is for display only (e.g. 'Last updated 2h ago'), not for expiration. `source_type` records which source provided this data.",
    columns: [
      {
        name: "main_id",
        type: "TEXT",
        constraints: "PK FK → content(main_id) ON DELETE CASCADE",
        description: "stable mainId from Phase C",
      },
      { name: "title", type: "TEXT", constraints: "NOT NULL", description: "anime name" },
      { name: "description", type: "TEXT", constraints: "", description: "synopsis" },
      { name: "cover_url", type: "TEXT", constraints: "", description: "remote URL" },
      { name: "banner_url", type: "TEXT", constraints: "", description: "" },
      { name: "score", type: "INTEGER", constraints: "", description: "0-100" },
      { name: "episodes", type: "INTEGER", constraints: "", description: "episode count" },
      {
        name: "season",
        type: "TEXT",
        constraints: "",
        description: "`WINTER`/`SPRING`/`SUMMER`/`FALL`",
      },
      { name: "season_year", type: "INTEGER", constraints: "", description: "2023" },
      {
        name: "status",
        type: "TEXT",
        constraints: "",
        description: "`RELEASING`/`FINISHED`/`CANCELLED`",
      },
      { name: "genres", type: "TEXT", constraints: "", description: "comma-separated" },
      {
        name: "source_type",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`anilist`/`tmdb`/`extension` — which source provided this data",
      },
      {
        name: "fetched_at",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis (display only — NOT for expiration)",
      },
    ],
    demoRows: [
      [
        "a1b2c3d4-…",
        "Frieren: Beyond Journey's End",
        "Frieren and her party…",
        "https://…/frieren-cover.jpg",
        "https://…/frieren-banner.jpg",
        "82",
        "28",
        "FALL",
        "2023",
        "FINISHED",
        "Adventure, Drama, Fantasy",
        "anilist",
        "1716820800000",
      ],
      [
        "e5f6a7b8-…",
        "Solo Leveling",
        "Solo Leveling synopsis…",
        "https://…/sololeveling-cover.jpg",
        "—",
        "85",
        "12",
        "WINTER",
        "2024",
        "FINISHED",
        "Action, Adventure, Fantasy",
        "anilist",
        "1716907200000",
      ],
    ],
  },
  {
    name: "episode_metadata_cache",
    group: "metadata",
    isNew: true,
    compositePK: ["main_id", "episode_number"],
    description:
      "Episode-level metadata. Composite primary key (main_id, episode_number). NEVER expires. Sourced from the extension/playing source (Aniyomi extensions).",
    columns: [
      {
        name: "main_id",
        type: "TEXT",
        constraints: "PK FK → content(main_id) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "episode_number",
        type: "REAL",
        constraints: "PK",
        description: "supports 0.5 specials, etc.",
      },
      { name: "title", type: "TEXT", constraints: "", description: "episode title" },
      { name: "description", type: "TEXT", constraints: "", description: "episode synopsis" },
      { name: "thumbnail_url", type: "TEXT", constraints: "", description: "remote URL" },
      { name: "air_date", type: "INTEGER", constraints: "", description: "epoch millis" },
      {
        name: "fetched_at",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis (display only)",
      },
    ],
    demoRows: [
      [
        "a1b2c3d4-…",
        "1",
        "Episode 1",
        "The Journey Begins…",
        "https://…/ep1.jpg",
        "1696118400000",
        "1716820800000",
      ],
      [
        "a1b2c3d4-…",
        "2",
        "Episode 2",
        "It Was a Good Day to Die",
        "https://…/ep2.jpg",
        "1696723200000",
        "1716820800000",
      ],
      [
        "a1b2c3d4-…",
        "3",
        "Episode 3",
        "A Hero's Resolve",
        "https://…/ep3.jpg",
        "1697328000000",
        "1716820800000",
      ],
    ],
  },

  // ============ BROWSE CACHE (1) ============
  {
    name: "browse_cache",
    group: "browse",
    isNew: true,
    description:
      "Browse-page sections (trending, popular, top_rated, etc.). HAS `expires_at` — auto-expires after 6 hours. HOMEPAGE ONLY — no other page auto-refreshes. `data_json` is a serialized list of anime IDs + minimal info.",
    columns: [
      {
        name: "section_key",
        type: "TEXT",
        constraints: "PK",
        description: "`trending`/`popular`/`top_rated`",
      },
      {
        name: "data_json",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "serialized list of anime IDs + minimal info",
      },
      {
        name: "fetched_at",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
      {
        name: "expires_at",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "fetched_at + 6h (browse page only)",
      },
    ],
    demoRows: [
      [
        "trending",
        '[{"id":"a1b2c3d4-…","title":"Frieren"},…]',
        "1716820800000",
        "1716842400000",
      ],
      [
        "popular",
        '[{"id":"e5f6a7b8-…","title":"Solo Leveling"},…]',
        "1716820800000",
        "1716842400000",
      ],
      [
        "top_rated",
        '[{"id":"a1b2c3d4-…","title":"Frieren"},…]',
        "1716820800000",
        "1716842400000",
      ],
    ],
  },
];

/* ---------------------------------------------------------------------------
 * 3.1 ER diagram nodes + edges
 * ------------------------------------------------------------------------- */

export type ERGroup = "content" | PhaseDGroup;

export interface ERNode {
  id: string;
  label: string;
  group: ERGroup;
  isMain?: boolean;
}

export interface EREdge {
  from: string;
  to: string;
  label: string;
}

export const ER_NODES: ERNode[] = [
  // Existing
  { id: "content", label: "content (existing)", group: "content", isMain: true },
  // New — metadata
  { id: "anime_metadata_cache", label: "anime_metadata_cache", group: "metadata" },
  { id: "episode_metadata_cache", label: "episode_metadata_cache", group: "metadata" },
  // New — browse
  { id: "browse_cache", label: "browse_cache", group: "browse" },
];

export const ER_EDGES: EREdge[] = [
  // content → metadata caches
  { from: "content", to: "anime_metadata_cache", label: "main_id" },
  { from: "content", to: "episode_metadata_cache", label: "main_id" },
  // browse_cache is standalone (no FK — keyed by section_key)
];

/* ---------------------------------------------------------------------------
 * 4. Refresh strategy — 3 cards
 * ------------------------------------------------------------------------- */

export interface RefreshCard {
  key: string;
  page: string;
  icon: string;
  tagline: string;
  triggers: string[];
  notes: string;
  color: string;
}

export const REFRESH_STRATEGY: RefreshCard[] = [
  {
    key: "browse",
    page: "Browse Page (Homepage)",
    icon: "🏠",
    tagline: "Pull-to-refresh + 6hr auto-update (homepage only)",
    color: "var(--c-primary)",
    triggers: [
      "6-hour auto-update — checked on browse page open. If the cache is older than 6 hours, refresh in the background. Show old data immediately.",
      "Pull-to-refresh — scroll down past the top → vibration → 'Release to refresh' → loads new data in background → swaps when ready.",
      "Manual refresh button in the header.",
      "Auto-update is HOMEPAGE ONLY — no other page auto-refreshes.",
    ],
    notes:
      "Show old data immediately — never block the UI on refresh. New data swaps in when ready.",
  },
  {
    key: "details",
    page: "Details Page (Multi-stage)",
    icon: "🎬",
    tagline: "Multi-stage: episodes → metadata → all (with vibration)",
    color: "var(--c-secondary)",
    triggers: [
      "Stage 1 — Scroll down a little: vibration → 'Refresh episodes list' → only refreshes episodes from the extension source. If new episodes found, auto-fetch their metadata.",
      "Stage 2 — Scroll down more: vibration → 'Refresh metadata' → only refreshes metadata (synopsis, score, etc.) from the data source (AniList/TMDB). No episode changes.",
      "Stage 3 — Scroll down even more: vibration → 'Refresh all' → full refresh (episodes + metadata + cover images).",
      "Visual indicators: refresh icons show when the user scrolls past each threshold. On release, a circular spinning indicator shows until refresh completes, then smoothly disappears.",
      "The 'Refresh' button in the three-dot menu does a full refresh (same as stage 3).",
    ],
    notes:
      "Each stage has a clear, distinct vibration feedback. Data source + extension source are cached independently.",
  },
  {
    key: "library",
    page: "Library Page",
    icon: "📚",
    tagline: "Loads from cache · pull-to-refresh forces re-fetch",
    color: "var(--c-success)",
    triggers: [
      "Loads entirely from local cache instantly (no network on tab switch).",
      "No auto-refresh — the user manually refreshes via the details page.",
      "Pull-to-refresh: force-refresh all entries' metadata in the background.",
      "Pagination when library > 100 entries.",
      "Pre-load cover images for visible + nearby items (Coil does this automatically).",
    ],
    notes: "Library is the fastest screen — instant load on cold start, no waiting.",
  },
];

/* ---------------------------------------------------------------------------
 * 5. Confirmed decisions (Q-001..Q-005 + additional)
 * ------------------------------------------------------------------------- */

export interface PhaseDDecision {
  id: string;
  question: string;
  answer: string;
}

export const PHASE_D_DECISIONS: PhaseDDecision[] = [
  {
    id: "Q-001",
    question: "Cache expiration",
    answer:
      "Never expires — user manually refreshes. No `expires_at` on metadata tables.",
  },
  {
    id: "Q-002",
    question: "Image cache size",
    answer: "500MB (configurable in future).",
  },
  {
    id: "Q-003",
    question: "Backup file format",
    answer: "Not in Phase D — deferred to a future phase.",
  },
  {
    id: "Q-004",
    question: "Auto-backup",
    answer: "Not needed — deferred.",
  },
  {
    id: "Q-005",
    question: "Refresh vibration",
    answer:
      "Yes — on every page with refresh functionality (homepage + details page).",
  },
];

/* Additional confirmed decisions (not Q-numbered) */
export const PHASE_D_ADDITIONAL_DECISIONS: string[] = [
  "6-hour auto-update is HOMEPAGE ONLY — not on any other page.",
  "Metadata never expires — the user manually refreshes.",
  "All cached data must survive device restart (solid caching, not in-memory only).",
  "Two source types (data source + extension source) must be properly separated + cached independently.",
  "Backup/restore is NOT in Phase D — only the data management + caching.",
];

/* ---------------------------------------------------------------------------
 * 6. Implementation phases (D.1..D.5)
 * ------------------------------------------------------------------------- */

export interface PhaseDMilestone {
  id: string;
  title: string;
  description: string;
  status: "planned" | "in-progress" | "done";
}

export const PHASE_D_MILESTONES: PhaseDMilestone[] = [
  {
    id: "D.1",
    title: "Local metadata cache (anime + episode)",
    description:
      "Add `anime_metadata_cache` + `episode_metadata_cache` tables. Create AnimeMetadataCache + EpisodeMetadataCache repositories. Update DetailsViewModel to read from cache first, then fetch from network if not cached. Update EpisodeMetadataFetcher to use cache. When refreshing, update the cache (not just the in-memory state). Cover images automatically update via Coil when the URL changes.",
    status: "done",
  },
  {
    id: "D.2",
    title: "Browse page cache + refresh",
    description:
      "Add `browse_cache` table. Create BrowseDataCache repository. Update BrowseViewModel to read from cache first. Implement pull-to-refresh with vibration. Implement 6-hour auto-update (homepage only).",
    status: "done",
  },
  {
    id: "D.3",
    title: "Details page multi-stage refresh",
    description:
      "Implement scroll-based refresh triggers (vibration + visual indicators at each stage). Stage 1: refresh episodes list only (from extension source). Stage 2: refresh metadata only (from data source). Stage 3: refresh all (both sources + cover images). Circular spinning indicator during refresh, smooth fade-out when complete. Wire the three-dot menu 'Refresh' button to stage 3.",
    status: "done",
  },
  {
    id: "D.4",
    title: "Image caching",
    description:
      "Configure Coil's disk cache (500MB, persistent). Ensure images survive restart. Pre-download cover images for library entries on first library load.",
    status: "done",
  },
  {
    id: "D.5",
    title: "Library performance",
    description:
      "Library loads entirely from `anime_metadata_cache` (no network on tab switch). Remove the in-memory `anilistCache` — replaced by the persistent DB cache. Background refresh of stale entries (only when the user pulls to refresh). Lazy loading + pagination for large libraries.",
    status: "done",
  },
];

/* ---------------------------------------------------------------------------
 * 7. Future considerations (NOT in Phase D)
 * ------------------------------------------------------------------------- */

export interface FutureConsideration {
  title: string;
  description: string;
}

export const PHASE_D_FUTURE: FutureConsideration[] = [
  {
    title: "Backup / Restore",
    description:
      "Export all data (including images) to a file. Restore on any device. Will be a separate phase.",
  },
  {
    title: "Score / Episode Badges on Covers",
    description:
      "Display score, released episodes, downloaded episodes, sub/dub counts on library cover images.",
  },
  {
    title: "Browse Page Hero Section",
    description: "A large featured anime at the top of the browse page.",
  },
  {
    title: "Dedicated Browse Sections",
    description:
      "Trending, popular, top rated, new releases — each as its own dedicated row/section.",
  },
  {
    title: "Configurable Image Cache Size",
    description:
      "Let the user set the max disk cache size (currently fixed at 500MB).",
  },
];
