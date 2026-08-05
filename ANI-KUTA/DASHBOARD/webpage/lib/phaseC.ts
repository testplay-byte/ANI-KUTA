/*
 * Phase C — Content Identity System (planning data).
 *
 * Source: Phase C planning brief — two-ID architecture, 9-table schema,
 * ER diagram, 6 confirmed decisions (Q-001..Q-006), 5 implementation phases.
 *
 * Hardcoded for the static dashboard demo — no API calls.
 * Kept separate from the page component so the page stays readable.
 */

/* ---------------------------------------------------------------------------
 * Hero / status
 * ------------------------------------------------------------------------- */

export const PHASE_C_HERO = {
  title: "Phase C — Content Identity System",
  subtitle:
    "A unified, future-proof identity layer for cross-source anime tracking",
  status: "PLANNING",
  statusColor: "var(--c-warning)",
  summary:
    "Phase C introduces a two-ID system — a stable Main ID (UUID) plus a structured Content ID — so that anime records survive source switches, multiple metadata providers (AniList, TMDB, Kitsu, MAL) and multiple extension systems (Aniyomi, CloudStream, Sora, MangaYomi) can coexist, and watch progress / library / history can all hang off a single stable key.",
} as const;

/* ---------------------------------------------------------------------------
 * 1. The two-ID system
 * ------------------------------------------------------------------------- */

export interface TwoIdCard {
  key: "main" | "content";
  title: string;
  tagline: string;
  color: string;
  format: string;
  formatMono: string;
  bullets: string[];
  notShownInUi: boolean;
}

export const TWO_ID_SYSTEM: TwoIdCard[] = [
  {
    key: "main",
    title: "Main ID",
    tagline: "Stable · UUID · forever",
    color: "var(--c-primary)",
    format: "Randomly generated UUID",
    formatMono: "550e8400-e29b-41d4-a716-446655440000",
    bullets: [
      "Assigned once when the content record is created.",
      "NEVER changes — survives every source switch, migration, and rename.",
      "Primary key for ALL data stores (library, watch progress, history).",
      "Used as the FK target every other table points to.",
    ],
    notShownInUi: true,
  },
  {
    key: "content",
    title: "Content ID",
    tagline: "Changing · structured · deterministic",
    color: "var(--c-secondary)",
    format: "Structured keyword string",
    formatMono:
      "anilist:aniyomi:https://…:com.aniyomi.anikoto:https://anikoto.cc/anime/frieren",
    bullets: [
      "Deterministically generated from source info (data source + system + repo + extension + URL).",
      "Changes when sources switch — by design.",
      "Used for quick identification + overlapping / duplicate detection.",
      "A duplicate Content ID means two records reference the same external content.",
    ],
    notShownInUi: true,
  },
];

/* ---------------------------------------------------------------------------
 * 2. Content ID format + examples
 * ------------------------------------------------------------------------- */

export const CONTENT_ID_FORMAT =
  "{dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{animeUrl|none}";

export interface ContentIdSegment {
  index: number;
  key: string;
  placeholder: string;
  description: string;
  example: string;
}

export const CONTENT_ID_SEGMENTS: ContentIdSegment[] = [
  {
    index: 1,
    key: "dataSource",
    placeholder: "dataSource",
    description:
      "Which metadata/tracking source — `anilist`, `tmdb`, `kitsu`, `mal`, or `none`.",
    example: "anilist",
  },
  {
    index: 2,
    key: "system",
    placeholder: "system",
    description:
      "Which extension system — `aniyomi`, `cloudstream`, `sora`, `mangayomi`, or `none`.",
    example: "aniyomi",
  },
  {
    index: 3,
    key: "repoUrl",
    placeholder: "repoUrl|none",
    description: "Extension repository URL, or `none` if installed without a repo.",
    example: "https://ani-kuta-repo.github.io",
  },
  {
    index: 4,
    key: "extensionPkg",
    placeholder: "extensionPkg|none",
    description: "Extension package name, or `none` if no extension is involved.",
    example: "com.aniyomi.anikoto",
  },
  {
    index: 5,
    key: "animeUrl",
    placeholder: "animeUrl|none",
    description: "Content's URL on the source, or `none` for metadata-only records.",
    example: "https://anikoto.cc/anime/frieren",
  },
];

export interface ContentIdExample {
  id: string;
  parts: string[];
  note: string;
}

export const CONTENT_ID_EXAMPLES: ContentIdExample[] = [
  {
    id: "full",
    parts: [
      "anilist",
      "aniyomi",
      "https://ani-kuta-repo.github.io",
      "com.aniyomi.anikoto",
      "https://anikoto.cc/anime/frieren",
    ],
    note: "Full chain — AniList metadata + Aniyomi extension from ANI-KUTA repo + AniKoto source URL.",
  },
  {
    id: "no-repo",
    parts: [
      "tmdb",
      "aniyomi",
      "none",
      "com.aniyomi.anikoto",
      "https://anikoto.cc/anime/frieren",
    ],
    note: "TMDB metadata + Aniyomi extension installed without a tracked repo URL.",
  },
  {
    id: "metadata-only",
    parts: ["anilist", "none", "none", "none", "none"],
    note: "Metadata-only record — AniList entry with no extension system attached yet.",
  },
  {
    id: "ext-only",
    parts: [
      "none",
      "aniyomi",
      "https://repo.example.com",
      "com.example.ext",
      "https://source.com/anime/123",
    ],
    note: "Extension-only record — no metadata source linked, content lives purely on an extension.",
  },
];

/* ---------------------------------------------------------------------------
 * 3. Database schema — tables, groups, columns, demo rows
 * ------------------------------------------------------------------------- */

export type PhaseCGroup = "sources" | "content" | "tracking";

export interface PhaseCGroupMeta {
  name: PhaseCGroup;
  label: string;
  purpose: string;
  color: string;
}

export const PHASE_C_GROUPS: PhaseCGroupMeta[] = [
  {
    name: "sources",
    label: "Sources & Extensions",
    purpose: "Who provides metadata, and which extension served the content",
    color: "#0EA5E9",
  },
  {
    name: "content",
    label: "Content Core",
    purpose: "The central content record — the two-ID system lives here",
    color: "#6366F1",
  },
  {
    name: "tracking",
    label: "User Tracking",
    purpose: "Library, watch progress, and history — all keyed on mainId",
    color: "#F59E0B",
  },
];

export const PHASE_C_GROUP_COLOR: Record<PhaseCGroup, string> =
  PHASE_C_GROUPS.reduce(
    (acc, g) => ({ ...acc, [g.name]: g.color }),
    {} as Record<PhaseCGroup, string>,
  );

export const PHASE_C_GROUP_LABEL: Record<PhaseCGroup, string> =
  PHASE_C_GROUPS.reduce(
    (acc, g) => ({ ...acc, [g.name]: g.label }),
    {} as Record<PhaseCGroup, string>,
  );

export interface PhaseCColumn {
  name: string;
  type: string;
  constraints: string; // e.g. "PK AUTOINCREMENT", "NOT NULL UNIQUE", "FK → systems(id)"
  description: string;
}

export interface PhaseCTable {
  name: string;
  group: PhaseCGroup;
  description: string;
  isMain?: boolean;
  isNew?: boolean;
  compositePK?: string[];
  columns: PhaseCColumn[];
  /** Demo rows — each row is an array of strings, one per column (in order). */
  demoRows: string[][];
}

export const PHASE_C_TABLES: PhaseCTable[] = [
  // ---- Sources & Extensions ----
  {
    name: "data_sources",
    group: "sources",
    description:
      "Metadata / tracking sources — AniList, TMDB, Kitsu, MAL. Populated once at first launch.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "name",
        type: "TEXT",
        constraints: "NOT NULL UNIQUE",
        description: "`anilist`, `tmdb`, `kitsu`, `mal`",
      },
      {
        name: "displayName",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`AniList`, `TMDB`, `Kitsu`, `MAL`",
      },
      {
        name: "type",
        type: "TEXT",
        constraints: "NOT NULL DEFAULT `metadata`",
        description: "`metadata`, `tracking`",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "anilist", "AniList", "metadata", "—"],
      ["2", "tmdb", "TMDB", "metadata", "—"],
      ["3", "kitsu", "Kitsu", "metadata", "—"],
      ["4", "mal", "MAL", "metadata", "—"],
    ],
  },
  {
    name: "systems",
    group: "sources",
    description:
      "Extension systems — Aniyomi, CloudStream, Sora, MangaYomi. One row per supported system.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "name",
        type: "TEXT",
        constraints: "NOT NULL UNIQUE",
        description: "`aniyomi`, `cloudstream`, `sora`, `mangayomi`",
      },
      {
        name: "displayName",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`Aniyomi`, `CloudStream`, `Sora`, `MangaYomi`",
      },
      {
        name: "packagePrefix",
        type: "TEXT",
        constraints: "",
        description: "`eu.kanade.tachiyomi`",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "aniyomi", "Aniyomi", "eu.kanade.tachiyomi", "—"],
      ["2", "cloudstream", "CloudStream", "com.lagradost.cloudstream", "—"],
      ["3", "sora", "Sora", "com.sora", "—"],
    ],
  },
  {
    name: "extension_repos",
    group: "sources",
    description:
      "Extension repository URLs. A system can have multiple repos (official + community).",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "systemId",
        type: "INTEGER",
        constraints: "NOT NULL FK → systems(id)",
        description: "",
      },
      {
        name: "url",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`https://ani-kuta-repo.github.io`",
      },
      {
        name: "displayName",
        type: "TEXT",
        constraints: "",
        description: "`ANI-KUTA Extensions`",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "1", "https://ani-kuta-repo.github.io", "ANI-KUTA Extensions", "—"],
      ["2", "1", "https://aniyomi.org/repo", "Aniyomi Official", "—"],
    ],
  },
  {
    name: "extensions",
    group: "sources",
    description:
      "Installed extensions. `repoId` is null when an extension was sideloaded without a repo.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "systemId",
        type: "INTEGER",
        constraints: "NOT NULL FK → systems(id)",
        description: "",
      },
      {
        name: "repoId",
        type: "INTEGER",
        constraints: "FK → extension_repos(id)",
        description: "null if no repo",
      },
      {
        name: "pkgName",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`com.aniyomi.anikoto`",
      },
      {
        name: "name",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`AniKoto`",
      },
      {
        name: "sourceId",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "internal source ID",
      },
      {
        name: "versionName",
        type: "TEXT",
        constraints: "",
        description: "`1.4.3`",
      },
      {
        name: "isNsfw",
        type: "INTEGER",
        constraints: "NOT NULL DEFAULT 0",
        description: "",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "1", "1", "com.aniyomi.anikoto", "AniKoto", "69023", "1.4.3", "0", "—"],
      ["2", "1", "1", "com.aniyomi.gogoanime", "GogoAnime", "69024", "1.4.2", "0", "—"],
    ],
  },

  // ---- Content Core ----
  {
    name: "content",
    group: "content",
    isMain: true,
    description:
      "The central content record. Holds both IDs — `mainId` (stable UUID, PK) and `contentId` (structured, changes). Every tracking table FKs to `mainId`.",
    columns: [
      { name: "mainId", type: "TEXT", constraints: "PK", description: "stable UUID" },
      {
        name: "contentId",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "structured string (changes)",
      },
      { name: "title", type: "TEXT", constraints: "NOT NULL", description: "anime name" },
      {
        name: "contentType",
        type: "TEXT",
        constraints: "NOT NULL DEFAULT `anime`",
        description: "`anime`/`manga`/`novel`/`movie`/`series`",
      },
      {
        name: "contentFormat",
        type: "TEXT",
        constraints: "NOT NULL DEFAULT `video`",
        description: "`video`/`image`/`text`",
      },
      {
        name: "dataSourceId",
        type: "INTEGER",
        constraints: "FK → data_sources(id)",
        description: "which metadata source",
      },
      {
        name: "systemId",
        type: "INTEGER",
        constraints: "FK → systems(id)",
        description: "which extension system",
      },
      {
        name: "extensionRepoId",
        type: "INTEGER",
        constraints: "FK → extension_repos(id)",
        description: "null if no repo",
      },
      {
        name: "extensionId",
        type: "INTEGER",
        constraints: "FK → extensions(id)",
        description: "null if no extension",
      },
      {
        name: "animeUrl",
        type: "TEXT",
        constraints: "",
        description: "content's URL on source",
      },
      {
        name: "displaySource",
        type: "TEXT",
        constraints: "NOT NULL DEFAULT `extension`",
        description: "`anilist`/`extension`",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
      {
        name: "updatedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      [
        "a1b2c3d4-…",
        "anilist:aniyomi:https://ani-kuta-repo.github.io:com.aniyomi.anikoto:https://anikoto.cc/anime/frieren",
        "Frieren: Beyond Journey's End",
        "anime",
        "video",
        "1",
        "1",
        "1",
        "1",
        "https://anikoto.cc/anime/frieren",
        "anilist",
        "—",
        "—",
      ],
      [
        "c3d4e5f6-…",
        "none:aniyomi:https://ani-kuta-repo.github.io:com.aniyomi.anikoto:https://anikoto.cc/anime/obscure",
        "Obscure Anime",
        "anime",
        "video",
        "null",
        "1",
        "1",
        "1",
        "https://anikoto.cc/anime/obscure",
        "extension",
        "—",
        "—",
      ],
      [
        "e5f6a7b8-…",
        "anilist:none:none:none:none:none",
        "Solo Leveling",
        "anime",
        "video",
        "1",
        "null",
        "null",
        "null",
        "null",
        "anilist",
        "—",
        "—",
      ],
    ],
  },
  {
    name: "content_source_link",
    group: "content",
    description:
      "Tracks every source linked to a content record — multiple AniList IDs, TMDB IDs, or extension URLs can all point at one `mainId`.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "mainId",
        type: "TEXT",
        constraints: "NOT NULL FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "sourceType",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "`anilist`/`tmdb`/`extension`",
      },
      {
        name: "sourceRef",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "anilistId or animeUrl",
      },
      {
        name: "linkedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "a1b2c3d4-…", "anilist", "154987", "—"],
      ["2", "a1b2c3d4-…", "extension", "https://anikoto.cc/anime/frieren", "—"],
      ["3", "c3d4e5f6-…", "extension", "https://anikoto.cc/anime/obscure", "—"],
    ],
  },

  // ---- User Tracking ----
  {
    name: "watch_progress",
    group: "tracking",
    isNew: true,
    description:
      "Per-episode watch progress. Composite PK on (mainId, episodeNumber) — one row per episode per content. Keyed entirely on `mainId`, so progress survives any source switch.",
    compositePK: ["mainId", "episodeNumber"],
    columns: [
      {
        name: "mainId",
        type: "TEXT",
        constraints: "NOT NULL FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      { name: "episodeNumber", type: "REAL", constraints: "NOT NULL", description: "" },
      {
        name: "position",
        type: "REAL",
        constraints: "NOT NULL",
        description: "seconds",
      },
      {
        name: "duration",
        type: "REAL",
        constraints: "NOT NULL",
        description: "seconds",
      },
      {
        name: "completed",
        type: "INTEGER",
        constraints: "NOT NULL DEFAULT 0",
        description: "",
      },
      {
        name: "updatedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["a1b2c3d4-…", "1", "480.5", "1440", "0", "—"],
      ["a1b2c3d4-…", "2", "1440", "1440", "1", "—"],
    ],
  },
  {
    name: "library",
    group: "tracking",
    isNew: true,
    description:
      "Library entries. `mainId` is both PK and FK — one row per library item. Adding to library = insert; removing = delete (cascades cleanly).",
    columns: [
      {
        name: "mainId",
        type: "TEXT",
        constraints: "PK FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "addedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["a1b2c3d4-…", "—"],
      ["e5f6a7b8-…", "—"],
    ],
  },
  {
    name: "watch_history",
    group: "tracking",
    isNew: true,
    description:
      "Watch history — append-only log of episodes watched. One row per watch event (an episode can appear multiple times). Keyed on `mainId`.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "" },
      {
        name: "mainId",
        type: "TEXT",
        constraints: "NOT NULL FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "episodeNumber",
        type: "REAL",
        constraints: "NOT NULL",
        description: "",
      },
      {
        name: "watchedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "a1b2c3d4-…", "1", "—"],
      ["2", "a1b2c3d4-…", "2", "—"],
    ],
  },
];

export const PHASE_C_SUMMARY = {
  totalTables: PHASE_C_TABLES.length,
  totalColumns: PHASE_C_TABLES.reduce((sum, t) => sum + t.columns.length, 0),
  newTables: PHASE_C_TABLES.filter((t) => t.isNew).length,
  totalGroups: PHASE_C_GROUPS.length,
} as const;

/* ---------------------------------------------------------------------------
 * 4. ER diagram — nodes + edges
 * ------------------------------------------------------------------------- */

export interface PhaseCERNode {
  id: string;
  label: string;
  group: PhaseCGroup;
  col: number; // 1..12
  row: number; // 1..6
}

export interface PhaseCEREdge {
  from: string;
  to: string;
  cardinality: "1-N" | "1-1";
  label: string;
}

/**
 * Grid layout (12 cols × 6 rows):
 *   Left column (col 2):  data_sources, systems, extension_repos, extensions
 *   Center (col 6):       content (the hub)
 *   Right column (col 10): content_source_link, watch_progress, library, watch_history
 */
export const PHASE_C_ER_NODES: PhaseCERNode[] = [
  { id: "data_sources", label: "data_sources", group: "sources", col: 2, row: 1 },
  { id: "systems", label: "systems", group: "sources", col: 2, row: 2 },
  { id: "extension_repos", label: "extension_repos", group: "sources", col: 2, row: 4 },
  { id: "extensions", label: "extensions", group: "sources", col: 2, row: 5 },
  { id: "content", label: "content", group: "content", col: 6, row: 3 },
  { id: "content_source_link", label: "content_source_link", group: "content", col: 10, row: 1 },
  { id: "watch_progress", label: "watch_progress", group: "tracking", col: 10, row: 2 },
  { id: "library", label: "library", group: "tracking", col: 10, row: 3 },
  { id: "watch_history", label: "watch_history", group: "tracking", col: 10, row: 4 },
];

export const PHASE_C_ER_EDGES: PhaseCEREdge[] = [
  { from: "data_sources", to: "content", cardinality: "1-N", label: "1 ─── N" },
  { from: "systems", to: "content", cardinality: "1-N", label: "1 ─── N" },
  { from: "extension_repos", to: "extensions", cardinality: "1-N", label: "1 ─── N" },
  { from: "systems", to: "extensions", cardinality: "1-N", label: "1 ─── N" },
  { from: "extensions", to: "content", cardinality: "1-N", label: "1 ─── N" },
  { from: "content", to: "content_source_link", cardinality: "1-N", label: "1 ─── N" },
  { from: "content", to: "watch_progress", cardinality: "1-N", label: "1 ─── N" },
  { from: "content", to: "library", cardinality: "1-1", label: "1 ─── 1" },
  { from: "content", to: "watch_history", cardinality: "1-N", label: "1 ─── N" },
];

/* ---------------------------------------------------------------------------
 * 5. Confirmed decisions (Q-001 .. Q-006)
 * ------------------------------------------------------------------------- */

export interface PhaseCDecision {
  id: string;
  question: string;
  answer: string;
}

export const PHASE_C_DECISIONS: PhaseCDecision[] = [
  {
    id: "Q-001",
    question: "Stable vs changing contentId",
    answer:
      "Both: stable Main ID (UUID) + changing Content ID (structured string).",
  },
  {
    id: "Q-002",
    question: "contentId generation",
    answer:
      "Main ID = UUID. Content ID = structured string (deterministic from sources).",
  },
  {
    id: "Q-003",
    question: "contentType",
    answer:
      "anime for now. Future: manga, novel, movie, series. Also contentFormat: video/image/text.",
  },
  {
    id: "Q-004",
    question: "Multiple extension sources?",
    answer: "No — one at a time. Switching = migrate (remove old, add new).",
  },
  {
    id: "Q-005",
    question: "Default display source",
    answer: "User decides (data-source selector lets them pick).",
  },
  {
    id: "Q-006",
    question: "contentId shown in UI?",
    answer: "No — internal only.",
  },
];

/* ---------------------------------------------------------------------------
 * 6. Implementation phases (C.1 .. C.5)
 * ------------------------------------------------------------------------- */

export interface PhaseCMilestone {
  id: string;
  title: string;
  description: string;
  status: "planning" | "todo" | "doing" | "done";
  deliverable: string;
}

export const PHASE_C_MILESTONES: PhaseCMilestone[] = [
  {
    id: "C.1",
    title: "Database schema + content module",
    description:
      "Create all 9 tables, FKs, and the content repository module. Implement Main ID (UUID) + Content ID (structured string) generation. CRUD for content records.",
    status: "planning",
    deliverable: "ContentRepository + schema migrations",
  },
  {
    id: "C.2",
    title: "Integrate with DetailsViewModel",
    description:
      "Wire the content module into the details screen. DetailsViewModel reads from `content` by mainId, resolves the active source, and renders metadata + extension data.",
    status: "planning",
    deliverable: "DetailsViewModel → ContentRepository integration",
  },
  {
    id: "C.3",
    title: "Watch progress (uses mainId)",
    description:
      "Persist per-episode position/duration/completed against `mainId`. Progress survives source switches because it never references the extension or URL directly.",
    status: "planning",
    deliverable: "WatchProgressRepository + player integration",
  },
  {
    id: "C.4",
    title: "Library (uses mainId)",
    description:
      "Library is now a thin table keyed on `mainId`. Add/remove is a single insert/delete. Re-sorting, filtering, and grouping all run off the content record.",
    status: "planning",
    deliverable: "LibraryRepository + library UI refresh",
  },
  {
    id: "C.5",
    title: "History (uses mainId)",
    description:
      "Append-only watch history keyed on `mainId`. The history list joins back to `content` for titles and thumbnails — one query, no source-aware joins.",
    status: "planning",
    deliverable: "HistoryRepository + history screen",
  },
];
