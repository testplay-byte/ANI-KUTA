/*
 * Phase C — Content Identity System (planning data v4).
 *
 * Source: Phase C plan v4 — two-ID system (stable Main ID + changing Content ID),
 * 8 tables (4 lookup + 1 main + 3 detail), deferred watch progress/library/history.
 *
 * Hardcoded for the static dashboard demo — no API calls.
 */

/* ---------------------------------------------------------------------------
 * Hero / status
 * ------------------------------------------------------------------------- */

export const PHASE_C_HERO = {
  title: "Phase C — Content Identity System",
  subtitle:
    "A stable Main ID + a structured Content ID — future-proof cross-source identity",
  status: "FINAL PLAN",
  statusColor: "var(--c-success)",
  summary:
    "Phase C introduces a two-ID system stored in ONE main content table. The Main ID (UUID) never changes — it's the primary key for all future data stores. The Content ID (structured string) changes when sources switch — it enables overlapping detection. Source-specific metadata lives in separate detail tables (anilist_details, extension_details, other_source_details) linked by mainId. This session focuses ONLY on the identity system; watch progress, library, history, and tracking are deferred.",
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
      "Primary key for ALL data stores (library, watch progress, history — when built).",
      "Stored as `mainId` column in the `content` table.",
    ],
    notShownInUi: true,
  },
  {
    key: "content",
    title: "Content ID",
    tagline: "Changing · structured · deterministic",
    color: "var(--c-secondary)",
    format: "Structured keyword string (6 sections)",
    formatMono:
      "anilist:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/frieren",
    bullets: [
      "Deterministically generated from 6 source fields (data source + system + repo + extension + source ID + URL).",
      "Changes when sources switch — by design.",
      "Used for quick identification + overlapping / duplicate detection.",
      "Stored as `contentId` column in the `content` table (same row as mainId).",
    ],
    notShownInUi: true,
  },
];

/* ---------------------------------------------------------------------------
 * 2. Content ID format + examples (v2 — 6 sections with sourceId)
 * ------------------------------------------------------------------------- */

export const CONTENT_ID_FORMAT =
  "{dataSource}:{system}:{repoId|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}";

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
    key: "repoId",
    placeholder: "repoId|none",
    description:
      "Extension repository DB ID (integer). Using the ID instead of the full URL keeps the Content ID short and avoids colon conflicts.",
    example: "1",
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
    key: "sourceId",
    placeholder: "sourceId|none",
    description:
      "Internal source ID within the extension (e.g. 69023), or `none` if no extension.",
    example: "69023",
  },
  {
    index: 6,
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
      "1",
      "com.aniyomi.anikoto",
      "69023",
      "https://anikoto.cc/anime/frieren",
    ],
    note: "Full chain — AniList metadata + Aniyomi extension (repo #1) + AniKoto source (ID 69023) + anime URL.",
  },
  {
    id: "tmdb",
    parts: [
      "tmdb",
      "aniyomi",
      "1",
      "com.aniyomi.anikoto",
      "69023",
      "https://anikoto.cc/anime/frieren",
    ],
    note: "TMDB metadata + same extension/source. Only the data source changed (anilist → tmdb).",
  },
  {
    id: "metadata-only",
    parts: ["anilist", "none", "none", "none", "none", "none"],
    note: "Metadata-only record — AniList entry with no extension system attached yet.",
  },
  {
    id: "ext-only",
    parts: [
      "none",
      "aniyomi",
      "2",
      "com.example.ext",
      "69024",
      "https://source.com/anime/123",
    ],
    note: "Extension-only record — no metadata source linked, content lives purely on an extension.",
  },
];

/* ---------------------------------------------------------------------------
 * 3. Database schema — 8 tables (4 lookup + 1 main + 3 detail)
 * ------------------------------------------------------------------------- */

export type PhaseCGroup = "lookup" | "main" | "detail";

export interface PhaseCGroupMeta {
  name: PhaseCGroup;
  label: string;
  purpose: string;
  color: string;
}

export const PHASE_C_GROUPS: PhaseCGroupMeta[] = [
  {
    name: "lookup",
    label: "Lookup Tables",
    purpose: "Normalized source/system/repo/extension data — seeded once, rarely change",
    color: "#0EA5E9",
  },
  {
    name: "main",
    label: "Main Content Table",
    purpose: "The central record — Main ID + Content ID + core display info",
    color: "#6366F1",
  },
  {
    name: "detail",
    label: "Detail Tables",
    purpose: "Source-specific metadata linked by mainId (one row per source per content)",
    color: "#8B5CF6",
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
  constraints: string;
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
  demoRows: string[][];
}

export const PHASE_C_TABLES: PhaseCTable[] = [
  // ============ LOOKUP TABLES (4) ============
  {
    name: "data_sources",
    group: "lookup",
    description:
      "Metadata / tracking sources — AniList, TMDB, Kitsu, MAL. Seeded once at first launch.",
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
    group: "lookup",
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
    group: "lookup",
    description:
      "Extension repository URLs. The URL points to the index.min.json file. A system can have multiple repos.",
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
        description: "full URL to index.min.json",
      },
      {
        name: "displayName",
        type: "TEXT",
        constraints: "",
        description: "human-readable name",
      },
      {
        name: "createdAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      [
        "1",
        "1",
        "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
        "Yuzono Anime Repo",
        "—",
      ],
      [
        "2",
        "1",
        "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json",
        "Aniyomi Official",
        "—",
      ],
    ],
  },
  {
    name: "extensions",
    group: "lookup",
    description:
      "Installed extensions. `repoId` is null when an extension was sideloaded without a repo. `sourceId` is the internal ID within the extension.",
    columns: [
      { name: "id", type: "INTEGER", constraints: "PK AUTOINCREMENT", description: "our DB ID" },
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
        description: "internal source ID (e.g. 69023)",
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

  // ============ MAIN TABLE (1) ============
  {
    name: "content",
    group: "main",
    isMain: true,
    description:
      "The central content record. Holds BOTH IDs — `mainId` (stable UUID, PK) and `contentId` (structured, changes). Also holds core display info (title, type, format, description) + source links. Detail tables FK to `mainId`.",
    columns: [
      { name: "mainId", type: "TEXT", constraints: "PK", description: "stable UUID" },
      {
        name: "contentId",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "structured string (regenerated on source change)",
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
        description: "`video`/`image`/`text`/`audio`",
      },
      {
        name: "description",
        type: "TEXT",
        constraints: "",
        description: "brief fallback (when no detail linked)",
      },
      {
        name: "dataSourceId",
        type: "INTEGER",
        constraints: "FK → data_sources(id)",
        description: "which metadata source (null if none)",
      },
      {
        name: "systemId",
        type: "INTEGER",
        constraints: "FK → systems(id)",
        description: "which extension system (null if none)",
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
        name: "sourceId",
        type: "INTEGER",
        constraints: "",
        description: "internal source ID (from extension, null if none)",
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
        description: "`anilist`/`extension`/`tmdb`/`kitsu` (which detail to show)",
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
        "anilist:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/frieren",
        "Frieren: Beyond Journey's End",
        "anime",
        "video",
        "—",
        "1",
        "1",
        "1",
        "1",
        "69023",
        "https://anikoto.cc/anime/frieren",
        "anilist",
        "—",
        "—",
      ],
      [
        "c3d4e5f6-…",
        "none:aniyomi:1:com.aniyomi.anikoto:69023:https://anikoto.cc/anime/obscure",
        "Obscure Anime",
        "anime",
        "video",
        "—",
        "null",
        "1",
        "1",
        "1",
        "69023",
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
        "—",
        "1",
        "null",
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

  // ============ DETAIL TABLES (3) ============
  {
    name: "anilist_details",
    group: "detail",
    isNew: true,
    description:
      "AniList-specific metadata for a content. One row per content (if linked to AniList). Stores score, episodes, season, genres, synopsis, cover/banner URLs.",
    columns: [
      {
        name: "mainId",
        type: "TEXT",
        constraints: "PK FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "anilistId",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "AniList anime ID (e.g. 154587)",
      },
      {
        name: "idMal",
        type: "INTEGER",
        constraints: "",
        description: "MAL ID (from AniList)",
      },
      { name: "score", type: "INTEGER", constraints: "", description: "0-100" },
      { name: "episodes", type: "INTEGER", constraints: "", description: "episode count" },
      {
        name: "season",
        type: "TEXT",
        constraints: "",
        description: "`WINTER`/`SPRING`/`SUMMER`/`FALL`",
      },
      { name: "seasonYear", type: "INTEGER", constraints: "", description: "2023" },
      {
        name: "status",
        type: "TEXT",
        constraints: "",
        description: "`RELEASING`/`FINISHED`/`CANCELLED`",
      },
      {
        name: "genres",
        type: "TEXT",
        constraints: "",
        description: "comma-separated: `Adventure, Drama, Fantasy`",
      },
      { name: "synopsis", type: "TEXT", constraints: "", description: "full description" },
      { name: "coverUrl", type: "TEXT", constraints: "", description: "cover image URL" },
      { name: "bannerUrl", type: "TEXT", constraints: "", description: "banner image URL" },
      {
        name: "updatedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis (last AniList fetch)",
      },
    ],
    demoRows: [
      [
        "a1b2c3d4-…",
        "154587",
        "52991",
        "82",
        "28",
        "FALL",
        "2023",
        "FINISHED",
        "Adventure, Drama, Fantasy",
        "Frieren and her party…",
        "https://…/frieren-cover.jpg",
        "https://…/frieren-banner.jpg",
        "—",
      ],
      [
        "e5f6a7b8-…",
        "154587",
        "52991",
        "82",
        "28",
        "FALL",
        "2023",
        "FINISHED",
        "Adventure, Drama, Fantasy",
        "Solo Leveling…",
        "https://…/sololeveling-cover.jpg",
        "https://…/sololeveling-banner.jpg",
        "—",
      ],
    ],
  },
  {
    name: "extension_details",
    group: "detail",
    isNew: true,
    description:
      "Extension-specific metadata for a content. One row per content (if linked to an extension source). Stores the extension's own description, genres, status, author, artist, thumbnail.",
    columns: [
      {
        name: "mainId",
        type: "TEXT",
        constraints: "PK FK → content(mainId) ON DELETE CASCADE",
        description: "",
      },
      {
        name: "extensionId",
        type: "INTEGER",
        constraints: "NOT NULL FK → extensions(id)",
        description: "which extension provided this",
      },
      {
        name: "sourceId",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "internal source ID",
      },
      {
        name: "animeUrl",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "content's URL on this extension",
      },
      {
        name: "description",
        type: "TEXT",
        constraints: "",
        description: "extension-provided description",
      },
      {
        name: "genres",
        type: "TEXT",
        constraints: "",
        description: "comma-separated",
      },
      {
        name: "status",
        type: "TEXT",
        constraints: "",
        description: "extension status code (1=RELEASING, 2=FINISHED)",
      },
      { name: "author", type: "TEXT", constraints: "", description: "" },
      { name: "artist", type: "TEXT", constraints: "", description: "" },
      {
        name: "thumbnailUrl",
        type: "TEXT",
        constraints: "",
        description: "extension-provided thumbnail",
      },
      {
        name: "updatedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis (last extension fetch)",
      },
    ],
    demoRows: [
      [
        "a1b2c3d4-…",
        "1",
        "69023",
        "https://anikoto.cc/anime/frieren",
        "Frieren's journey…",
        "Adventure, Fantasy",
        "2",
        "Yamada Kanehito",
        "Tsukasa Abe",
        "https://anikoto.cc/img/frieren.jpg",
        "—",
      ],
      [
        "c3d4e5f6-…",
        "1",
        "69023",
        "https://anikoto.cc/anime/obscure",
        "An obscure anime…",
        "Drama",
        "2",
        "null",
        "null",
        "https://anikoto.cc/img/obscure.jpg",
        "—",
      ],
    ],
  },
  {
    name: "other_source_details",
    group: "detail",
    isNew: true,
    description:
      "Generic key-value table for future data sources (TMDB, Kitsu, MAL, custom). Allows storing source-specific fields without adding a new table per source.",
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
        description: "`tmdb`/`kitsu`/`mal`/`custom`",
      },
      {
        name: "sourceRefId",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "ID on that source (e.g. TMDB ID `12345`)",
      },
      {
        name: "key",
        type: "TEXT",
        constraints: "NOT NULL",
        description: "field name (e.g. `score`, `synopsis`)",
      },
      {
        name: "value",
        type: "TEXT",
        constraints: "",
        description: "field value (serialized)",
      },
      {
        name: "updatedAt",
        type: "INTEGER",
        constraints: "NOT NULL",
        description: "epoch millis",
      },
    ],
    demoRows: [
      ["1", "a1b2c3d4-…", "tmdb", "12345", "score", "8.2", "—"],
      ["2", "a1b2c3d4-…", "tmdb", "12345", "synopsis", "TMDB's synopsis…", "—"],
      ["3", "a1b2c3d4-…", "tmdb", "12345", "genres", "Adventure,Drama,Fantasy", "—"],
    ],
  },
];

/* ---------------------------------------------------------------------------
 * 4. ER diagram nodes + edges
 * ------------------------------------------------------------------------- */

export interface ERNode {
  id: string;
  label: string;
  group: PhaseCGroup;
  isMain?: boolean;
}

export interface EREdge {
  from: string;
  to: string;
  label: string;
}

export const ER_NODES: ERNode[] = [
  // Lookup
  { id: "data_sources", label: "data_sources", group: "lookup" },
  { id: "systems", label: "systems", group: "lookup" },
  { id: "extension_repos", label: "extension_repos", group: "lookup" },
  { id: "extensions", label: "extensions", group: "lookup" },
  // Main
  { id: "content", label: "content", group: "main", isMain: true },
  // Detail
  { id: "anilist_details", label: "anilist_details", group: "detail" },
  { id: "extension_details", label: "extension_details", group: "detail" },
  { id: "other_source_details", label: "other_source_details", group: "detail" },
];

export const ER_EDGES: EREdge[] = [
  // Lookup → content
  { from: "data_sources", to: "content", label: "dataSourceId" },
  { from: "systems", to: "content", label: "systemId" },
  { from: "extension_repos", to: "content", label: "extensionRepoId" },
  { from: "extensions", to: "content", label: "extensionId" },
  // Lookup internal
  { from: "systems", to: "extension_repos", label: "systemId" },
  { from: "systems", to: "extensions", label: "systemId" },
  { from: "extension_repos", to: "extensions", label: "repoId" },
  // content → detail
  { from: "content", to: "anilist_details", label: "mainId" },
  { from: "content", to: "extension_details", label: "mainId" },
  { from: "content", to: "other_source_details", label: "mainId" },
];

/* ---------------------------------------------------------------------------
 * 5. Confirmed decisions (Q-001..Q-010)
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
      "Both — stable Main ID (UUID) + changing Content ID (structured string), in the SAME `content` table.",
  },
  {
    id: "Q-002",
    question: "contentId generation",
    answer:
      "Main ID = UUID. Content ID = structured string (deterministic from 6 source fields).",
  },
  {
    id: "Q-003",
    question: "contentType + contentFormat",
    answer:
      "contentType: anime (now), manga/novel/movie/series (future). contentFormat: video/image/text/audio.",
  },
  {
    id: "Q-004",
    question: "Multiple extension sources per content?",
    answer: "No — one at a time. Switching = migrate (remove old, add new).",
  },
  {
    id: "Q-005",
    question: "Default display source when auto-link matches",
    answer: "User decides — the data-source selector lets them pick.",
  },
  {
    id: "Q-006",
    question: "contentId shown in UI?",
    answer: "No — internal only.",
  },
  {
    id: "Q-007",
    question: "Session scope",
    answer:
      "This session: content ID system + main + detail + lookup tables ONLY. Watch progress/library/history/tracking deferred.",
  },
  {
    id: "Q-008",
    question: "Detail table approach",
    answer:
      "Separate tables per source type (anilist_details, extension_details, other_source_details) linked by mainId.",
  },
  {
    id: "Q-009",
    question: "Content ID sections",
    answer: "6 sections: dataSource, system, repoId, extensionPkg, sourceId, animeUrl.",
  },
  {
    id: "Q-010",
    question: "Repo URL in Content ID",
    answer:
      "Use repo DB ID (integer) instead of full URL — URL is too long + contains colons.",
  },
];

/* ---------------------------------------------------------------------------
 * 6. Implementation phases (C.1..C.4 this session)
 * ------------------------------------------------------------------------- */

export interface PhaseCMilestone {
  id: string;
  title: string;
  description: string;
  status: "planned" | "in-progress" | "done";
}

export const PHASE_C_MILESTONES: PhaseCMilestone[] = [
  {
    id: "C.1",
    title: "Database schema",
    description:
      "Add SQLDelight .sq files for all 8 tables (4 lookup + 1 main + 3 detail). Seed data_sources + systems on first launch.",
    status: "planned",
  },
  {
    id: "C.2",
    title: "Content module (:core:content)",
    description:
      "Create ContentIdGenerator + ContentRepository + ContentResolver. Register in Koin.",
    status: "planned",
  },
  {
    id: "C.3",
    title: "Integrate with DetailsViewModel",
    description:
      "Add mainId + contentId to UnifiedAnime. Wire ContentResolver into load/link/unlink/switch operations.",
    status: "planned",
  },
  {
    id: "C.4",
    title: "Console logging",
    description:
      "Log Content ID generation, resolution, and source link/unlink operations for debugging.",
    status: "planned",
  },
];

/* ---------------------------------------------------------------------------
 * 7. Deferred to later sessions
 * ------------------------------------------------------------------------- */

export const PHASE_C_DEFERRED: string[] = [
  "watch_progress — table keyed on mainId + episodeNumber",
  "library — table keyed on mainId",
  "watch_history — table keyed on mainId",
  "Internal tracking system — activity events, user behavior",
  "Overlapping detection — detect duplicate Content IDs + offer merge",
  "Backup/restore — export/import the content table",
  "Multi-system support — CloudStream, Sora, MangaYomi extensions",
  "Multi-data-source — TMDB, Kitsu, MAL providers (via other_source_details)",
];
