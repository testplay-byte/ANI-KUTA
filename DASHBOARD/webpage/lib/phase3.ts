/*
 * ANI-KUTA Phase 3 Plan — visual data.
 *
 * Source: APP/ani-kuta/DOCUMENTATION/18-phase3-plan.md
 *
 * Phase 3 builds 14 core modules in 4 sub-phases (3a Foundation, 3b Extensions,
 * 3c Playback, 3d Supporting). Hardcoded for the static dashboard demo.
 */

export type SubPhaseId = "3a" | "3b" | "3c" | "3d";

export interface SubPhase {
  id: SubPhaseId;
  label: string;
  name: string;
  /** What it delivers — 1-line summary. */
  delivers: string;
  /** DESIGN.md accent color hex. */
  color: string;
  colorVar: string;
  /** Soft bg tint (rgba). */
  softBg: string;
  /** Number of modules in this sub-phase. */
  moduleCount: number;
}

export const SUB_PHASES: SubPhase[] = [
  {
    id: "3a",
    label: "3a",
    name: "Foundation",
    delivers: "Identity system + data repositories (anime + history)",
    color: "#14B8A6",
    colorVar: "var(--c-success)",
    softBg: "rgba(20, 184, 166, 0.10)",
    moduleCount: 4,
  },
  {
    id: "3b",
    label: "3b",
    name: "Extensions",
    delivers: "Extension provider API + Aniyomi extension loading",
    color: "#6366F1",
    colorVar: "var(--c-primary)",
    softBg: "rgba(99, 102, 241, 0.10)",
    moduleCount: 3,
  },
  {
    id: "3c",
    label: "3c",
    name: "Playback",
    delivers: "Video resolver + MPV player + watch progress contract",
    color: "#F59E0B",
    colorVar: "var(--c-warning)",
    softBg: "rgba(245, 158, 11, 0.10)",
    moduleCount: 3,
  },
  {
    id: "3d",
    label: "3d",
    name: "Supporting",
    delivers: "Downloads + metadata cache + trackers + backup",
    color: "#8B5CF6",
    colorVar: "var(--c-secondary)",
    softBg: "rgba(139, 92, 246, 0.10)",
    moduleCount: 4,
  },
];

export interface Phase3Module {
  /** Build-order step (1-14). */
  step: number;
  /** Module coordinate (e.g. :core:identity). */
  name: string;
  /** Short label without colons (e.g. "identity"). */
  short: string;
  subPhase: SubPhaseId;
  /** 1-line purpose. */
  purpose: string;
  /** What's in it (key files / classes). */
  keyFiles?: string[];
  /** Dependencies — list of module names (with or without colons). */
  dependsOn: string[];
  /** Deliverable — what it enables once built. */
  deliverable?: string;
  /** Note about layering / design constraint. */
  note?: string;
}

export const PHASE3_MODULES: Phase3Module[] = [
  /* ---- Sub-phase 3a: Foundation (4 modules) ---- */
  {
    step: 1,
    name: ":core:identity",
    short: "identity",
    subPhase: "3a",
    purpose:
      "The identity system — ContentUID + ExternalReference + IdentityResolver.",
    keyFiles: ["ContentUid.kt", "ExternalReference.kt", "EpisodeUid.kt", "IdentityResolver.kt"],
    dependsOn: [":core:common"],
    deliverable: "IdentityResolver interface defined; ready for :data:identity to implement.",
    note: "IdentityResolver is an interface. Graph-based impl lives in :data:identity — keeps :core:identity decoupled from DB.",
  },
  {
    step: 2,
    name: ":data:identity",
    short: "data:identity",
    subPhase: "3a",
    purpose: "SQLDelight implementation of IdentityResolver + the matching engine.",
    keyFiles: ["IdentityRepositoryImpl.kt", "MatchingEngine.kt", "MatchKey.kt"],
    dependsOn: [":core:identity", ":core:database", ":core:common"],
    deliverable: "Identity resolution working — exact match → tracker bridge → fuzzy → create new.",
    note: "Matching algorithm: (1) exact ext-ref lookup → (2) tracker bridge → (3) fuzzy matchKey → (4) create new.",
  },
  {
    step: 3,
    name: ":data:anime",
    short: "data:anime",
    subPhase: "3a",
    purpose:
      "Repository implementations for anime, episodes, categories, library, history, watch progress.",
    keyFiles: ["AnimeRepositoryImpl.kt", "EpisodeRepositoryImpl.kt", "CategoryRepositoryImpl.kt", "HistoryRepositoryImpl.kt", "WatchProgressRepositoryImpl.kt"],
    dependsOn: [":core:database", ":core:identity", ":core:common"],
    deliverable: "Database fully wired — can add anime to library, record watch progress, query history. (No UI yet — Phase 4.)",
  },
  {
    step: 4,
    name: ":core:watch-progress",
    short: "watch-progress",
    subPhase: "3c",
    purpose: "WatchProgressStore interface (contract module). Resolves the layering issue (architecture plan C3).",
    keyFiles: ["WatchProgressStore.kt", "WatchProgress.kt"],
    dependsOn: [":core:common"],
    deliverable: "Contract interface ready for :data:history to implement.",
    note: "Impl lives in :data:history (not :data:anime) so :core:player can depend on the interface only — no layering violation.",
  },
  {
    step: 5,
    name: ":data:history",
    short: "data:history",
    subPhase: "3a",
    purpose:
      "History repository + WatchProgressStore implementation. Separate from :data:anime per C3 fix.",
    keyFiles: ["HistoryRepositoryImpl.kt", "WatchProgressRepositoryImpl.kt"],
    dependsOn: [":core:database", ":core:watch-progress", ":core:common"],
    deliverable: "Watch progress + history persistence working — readable + writable from any module via interfaces.",
    note: "C3 fix: :core:player depends on :core:watch-progress (interface), NOT on :data:anime.",
  },

  /* ---- Sub-phase 3b: Extension System (3 modules) ---- */
  {
    step: 6,
    name: ":core:provider-api",
    short: "provider-api",
    subPhase: "3b",
    purpose:
      "The ExtensionProvider abstraction. Per-content-type sub-interfaces (architecture plan C1 fix).",
    keyFiles: ["ExtensionProvider.kt", "VideoExtensionProvider.kt", "ImageExtensionProvider.kt", "TextExtensionProvider.kt", "Source.kt"],
    dependsOn: [":core:common"],
    deliverable: "ExtensionProvider contracts ready — all extension ecosystems implement these.",
    note: "C1 fix: split into Video/Image/Text sub-interfaces. A provider can implement multiple (e.g. Mangayomi = Video + Image).",
  },
  {
    step: 7,
    name: ":core:source-api",
    short: "source-api",
    subPhase: "3b",
    purpose:
      "Aniyomi-compatible source API. Ships eu.kanade.tachiyomi.animesource.* for binary compat.",
    keyFiles: ["AnimeSource.kt", "AnimeCatalogueSource.kt", "AnimeInfo.kt", "AnimeEpisode.kt", "Video.kt", "SourceManager.kt"],
    dependsOn: [":core:network"],
    deliverable: "Aniyomi extension JARs can be loaded as DEX + their source interfaces resolve.",
    note: "Injekt isolation (rule 6 + I1): uy.kohesive.injekt allowed ONLY here + :data:extension-aniyomi.",
  },
  {
    step: 8,
    name: ":data:extension-aniyomi",
    short: "ext-aniyomi",
    subPhase: "3b",
    purpose:
      "Loads, installs, and manages Aniyomi APK extensions. Implements VideoExtensionProvider.",
    keyFiles: ["AnimeExtensionManager.kt", "ExtensionLoader.kt", "ExtensionInstaller.kt", "ExtensionRepoApi.kt", "ExtensionTrust.kt", "SourceMatcher.kt", "AniyomiExtensionProvider.kt"],
    dependsOn: [":core:provider-api", ":core:source-api", ":core:network", ":core:database", ":core:identity"],
    deliverable: "Can install Aniyomi extensions, list their sources, fetch anime from a source.",
    note: "Injekt registers 4 singletons before extensions load: Application, Context, NetworkHelper, Json.",
  },

  /* ---- Sub-phase 3c: Playback Pipeline (3 modules — watch-progress already at step 4) ---- */
  {
    step: 9,
    name: ":core:video-resolver",
    short: "video-resolver",
    subPhase: "3c",
    purpose: "Calls the extension source's fetchVideoList → extracts playable video URLs.",
    keyFiles: ["VideoResolver.kt", "ResolverState.kt", "VideoResolverImpl.kt", "HlsHelper.kt"],
    dependsOn: [":core:source-api", ":core:common"],
    deliverable: "Episode → list of playable video URLs (with quality variants).",
    note: ":core:player does NOT depend on this. :feature:anime-watch:impl mediates (architecture plan I10).",
  },
  {
    step: 10,
    name: ":core:player",
    short: "player",
    subPhase: "3c",
    purpose: "MPV wrapper + player controls + watch progress writing.",
    keyFiles: ["AnikutaMPVView.kt", "PlayerController.kt", "PlayerPreferences.kt", "PlaybackStateStore.kt", "controls/PlayerControls.kt", "subtitles/SubtitleTrackFormatter.kt"],
    dependsOn: [":core:watch-progress", ":core:common", "aniyomi-mpv-lib"],
    deliverable: "Can play a video URL via MPV, save watch progress, show player controls.",
    note: "AnikutaMPVView is XML-inflated (not Compose) — obtainStyledAttributes requires XmlBlock$Parser.",
  },

  /* ---- Sub-phase 3d: Supporting Systems (4 modules) ---- */
  {
    step: 11,
    name: ":core:download",
    short: "download",
    subPhase: "3d",
    purpose: "Download manager for offline playback (HTTP + HLS + resume).",
    keyFiles: ["DownloadManager.kt", "DownloadTask.kt", "HlsDownloader.kt", "HttpDownloader.kt", "DownloadState.kt"],
    dependsOn: [":core:network", ":core:database", ":core:common"],
    deliverable: "Queue → download → offline playback. HLS + PNG anti-scraping header stripping.",
  },
  {
    step: 12,
    name: ":core:episode-metadata",
    short: "episode-metadata",
    subPhase: "3d",
    purpose: "Caches episode metadata (thumbnails, titles, air dates) from multiple sources.",
    keyFiles: ["EpisodeMetadataCache.kt", "EpisodeMetadataSource.kt", "AniListMetadataSource.kt", "JikanMetadataSource.kt"],
    dependsOn: [":core:anilist", ":core:database", ":core:common"],
    deliverable: "Episode metadata fetched + cached from AniList + Jikan (MyAnimeList).",
    note: "Multi-binding: List<EpisodeMetadataSource> — multiple providers, results merged.",
  },
  {
    step: 13,
    name: ":core:tracker",
    short: "tracker",
    subPhase: "3d",
    purpose: "AniList + MAL tracker sync.",
    keyFiles: ["Tracker.kt", "AniListTracker.kt", "MalTracker.kt", "TrackSyncManager.kt", "TrackSyncState.kt"],
    dependsOn: [":core:anilist", ":core:database", ":core:identity"],
    deliverable: "AniList + MAL sync working — bidirectional library status + watch progress sync.",
    note: "OAuth tokens in EncryptedSharedPreferences (Keystore), not DB (S6).",
  },
  {
    step: 14,
    name: ":core:backup",
    short: "backup",
    subPhase: "3d",
    purpose: "Backup/restore + multi-app import (Aniyomi .tachibk, Mangayomi .backup).",
    keyFiles: ["BackupManager.kt", "BackupProvider.kt", "BackupContainer.kt", "import/AniyomiTachibkImporter.kt", "import/MangayomiBackupImporter.kt", "export/AnikutaBackupExporter.kt", "providers/LibraryBackupProvider.kt", "providers/HistoryBackupProvider.kt"],
    dependsOn: [":core:identity", ":core:database", ":data:anime", ":data:history", ":core:preferences", ":core:common", "kotlinx-serialization-protobuf"],
    deliverable: "Export to .anikuta v2 + import from Aniyomi/Mangayomi backups.",
    note: "P4 fix: BackupDataAccessor interface — :core:backup depends on interface, not concrete repos.",
  },
];

/* ---------------------------------------------------------------------------
 * Dependency graph nodes — positioned on a 14-wide × 8-tall grid for the
 * SVG visualization. Each module is a box; arrows show dependencies
 * (dependent → dependency).
 * ------------------------------------------------------------------------- */

export interface DepGraphNode {
  id: string;
  label: string;
  subPhase: SubPhaseId;
  /** Grid col 1-14 (left → right). */
  col: number;
  /** Grid row 1-8 (top → bottom). */
  row: number;
  step: number;
}

export interface DepGraphEdge {
  from: string;
  to: string;
  /** Optional label for the dependency (e.g. "interface"). */
  label?: string;
}

export const DEP_GRAPH_NODES: DepGraphNode[] = [
  // Column 1 — :core:common (leftmost)
  { id: "common", label: ":core:common", subPhase: "3a", col: 1, row: 4, step: 0 },

  // Column 2 — :core:database / :core:network / :core:anilist (pre-existing)
  { id: "database", label: ":core:database", subPhase: "3a", col: 2, row: 3, step: 0 },
  { id: "network", label: ":core:network", subPhase: "3b", col: 2, row: 5, step: 0 },
  { id: "anilist", label: ":core:anilist", subPhase: "3d", col: 2, row: 7, step: 0 },
  { id: "preferences", label: ":core:preferences", subPhase: "3d", col: 2, row: 8, step: 0 },

  // Column 3 — Foundation interfaces (3a)
  { id: "core-identity", label: ":core:identity", subPhase: "3a", col: 3, row: 2, step: 1 },
  { id: "watch-progress", label: ":core:watch-progress", subPhase: "3c", col: 3, row: 5, step: 4 },

  // Column 4 — Extension contracts (3b)
  { id: "provider-api", label: ":core:provider-api", subPhase: "3b", col: 4, row: 1, step: 6 },
  { id: "source-api", label: ":core:source-api", subPhase: "3b", col: 4, row: 3, step: 7 },

  // Column 5 — Data implementations (3a)
  { id: "data-identity", label: ":data:identity", subPhase: "3a", col: 5, row: 2, step: 2 },
  { id: "data-anime", label: ":data:anime", subPhase: "3a", col: 5, row: 4, step: 3 },
  { id: "data-history", label: ":data:history", subPhase: "3a", col: 5, row: 6, step: 5 },

  // Column 6 — Aniyomi extension loader (3b)
  { id: "ext-aniyomi", label: ":data:extension-aniyomi", subPhase: "3b", col: 6, row: 3, step: 8 },

  // Column 7 — Playback pipeline (3c)
  { id: "video-resolver", label: ":core:video-resolver", subPhase: "3c", col: 7, row: 2, step: 9 },
  { id: "player", label: ":core:player", subPhase: "3c", col: 7, row: 5, step: 10 },

  // Column 8 — Supporting systems (3d)
  { id: "download", label: ":core:download", subPhase: "3d", col: 8, row: 2, step: 11 },
  { id: "episode-metadata", label: ":core:episode-metadata", subPhase: "3d", col: 8, row: 4, step: 12 },
  { id: "tracker", label: ":core:tracker", subPhase: "3d", col: 8, row: 6, step: 13 },
  { id: "backup", label: ":core:backup", subPhase: "3d", col: 8, row: 8, step: 14 },
];

export const DEP_GRAPH_EDGES: DepGraphEdge[] = [
  // :core:common → many
  { from: "core-identity", to: "common" },
  { from: "provider-api", to: "common" },
  { from: "watch-progress", to: "common" },
  { from: "data-anime", to: "common" },
  { from: "data-history", to: "common" },
  { from: "video-resolver", to: "common" },
  { from: "player", to: "common" },
  { from: "download", to: "common" },
  { from: "episode-metadata", to: "common" },
  { from: "backup", to: "common" },

  // :core:identity → :core:common
  { from: "data-identity", to: "core-identity" },
  { from: "data-anime", to: "core-identity" },
  { from: "ext-aniyomi", to: "core-identity" },
  { from: "tracker", to: "core-identity" },
  { from: "backup", to: "core-identity" },

  // :core:database → many
  { from: "data-identity", to: "database" },
  { from: "data-anime", to: "database" },
  { from: "data-history", to: "database" },
  { from: "ext-aniyomi", to: "database" },
  { from: "download", to: "database" },
  { from: "episode-metadata", to: "database" },
  { from: "tracker", to: "database" },
  { from: "backup", to: "database" },

  // :core:network → :core:source-api + download + ext-aniyomi
  { from: "source-api", to: "network" },
  { from: "ext-aniyomi", to: "network" },
  { from: "download", to: "network" },

  // :core:anilist → episode-metadata + tracker
  { from: "episode-metadata", to: "anilist" },
  { from: "tracker", to: "anilist" },

  // :core:preferences → backup
  { from: "backup", to: "preferences" },

  // :core:watch-progress → :core:player (interface) + :data:history (impl)
  { from: "watch-progress", to: "common" },
  { from: "player", to: "watch-progress", label: "interface" },
  { from: "data-history", to: "watch-progress", label: "impl" },

  // Extension contracts
  { from: "ext-aniyomi", to: "provider-api" },
  { from: "ext-aniyomi", to: "source-api" },
  { from: "video-resolver", to: "source-api" },

  // Backup → repos
  { from: "backup", to: "data-anime" },
  { from: "backup", to: "data-history" },
];

/* ---------------------------------------------------------------------------
 * Open questions — the 4 from the plan that need user input.
 * ------------------------------------------------------------------------- */

export interface OpenQuestion {
  id: number;
  topic: string;
  question: string;
  recommendation: string;
  /** Impact area — what's affected if answered a particular way. */
  impact: string;
}

export const PHASE3_OPEN_QUESTIONS: OpenQuestion[] = [
  {
    id: 1,
    topic: "MPV native library",
    question:
      "The old project uses aniyomi-mpv-lib. Do we use the same, or build a new wrapper?",
    recommendation: "Reuse aniyomi-mpv-lib — it's proven.",
    impact: "Affects :core:player build + native lib packaging in the APK.",
  },
  {
    id: 2,
    topic: "Aniyomi extension repo",
    question:
      "Do we include the default Aniyomi extension repo URL, or let users add their own?",
    recommendation: "Include defaults but make them removable.",
    impact: "Affects :data:extension-aniyomi seed data + ExtensionRepoApi.",
  },
  {
    id: 3,
    topic: "Tracker OAuth",
    question:
      "AniList uses OAuth2. MAL uses OAuth2. Do we implement both in Phase 3, or AniList first?",
    recommendation: "AniList first — MAL is similar pattern, can follow quickly.",
    impact: "Affects :core:tracker scope + ship timeline.",
  },
  {
    id: 4,
    topic: "Backup format",
    question:
      "The .anikuta v2 format includes ContentUID + ExternalReference. Should it also include downloaded files (large)?",
    recommendation: "No — backup is metadata only. Downloads are re-downloadable.",
    impact: "Affects :core:backup BackupContainer + providers list.",
  },
];

/* ---------------------------------------------------------------------------
 * Risk assessment (from the plan — informational).
 * ------------------------------------------------------------------------- */

export interface Risk {
  risk: string;
  likelihood: "Low" | "Medium" | "High";
  impact: "Low" | "Medium" | "High";
  mitigation: string;
}

export const PHASE3_RISKS: Risk[] = [
  {
    risk: "MPV native lib build issues",
    likelihood: "Medium",
    impact: "High",
    mitigation: "Use pre-built aniyomi-mpv-lib AAR. Test on CI.",
  },
  {
    risk: "Aniyomi extension compat breaks",
    likelihood: "Low",
    impact: "High",
    mitigation: "Pin to a specific Aniyomi source-api version. Test with a few popular extensions.",
  },
  {
    risk: "Injekt isolation hard to enforce",
    likelihood: "Medium",
    impact: "Medium",
    mitigation: "Detekt rule (path + filename allowlist). CI check.",
  },
  {
    risk: "Phase 3 is large (14 modules)",
    likelihood: "High",
    impact: "Medium",
    mitigation: "Split into 4 sub-phases. Each sub-phase is independently testable.",
  },
  {
    risk: "SQLDelight schema migration complexity",
    likelihood: "Medium",
    impact: "Medium",
    mitigation: "Use the old project's proven migration pattern (one-shot flags, try/catch).",
  },
];

/* ---------------------------------------------------------------------------
 * Phase 3 deliverables (high-level).
 * ------------------------------------------------------------------------- */

export const PHASE3_DELIVERABLES: { id: number; label: string; detail: string }[] = [
  { id: 1, label: "Identity system", detail: "ContentUID + ExternalReference + matching engine." },
  { id: 2, label: "Database fully populated", detail: "All 17 active tables (from Phase 3 plan §Deliverables)." },
  { id: 3, label: "Aniyomi extensions loadable", detail: "Can install + browse sources." },
  { id: 4, label: "Video pipeline", detail: "Resolve URL → play via MPV → save progress." },
  { id: 5, label: "Downloads", detail: "Queue + download + offline playback." },
  { id: 6, label: "Trackers", detail: "AniList/MAL sync." },
  { id: 7, label: "Backup/restore", detail: "Export + import from Aniyomi/Mangayomi." },
];

/* ---------------------------------------------------------------------------
 * Summary stats — drives the top summary card.
 * ------------------------------------------------------------------------- */

export const PHASE3_SUMMARY = {
  totalModules: PHASE3_MODULES.length, // 14
  totalSubPhases: SUB_PHASES.length, // 4
  totalDependencies: PHASE3_MODULES.reduce(
    (acc, m) => acc + m.dependsOn.length,
    0,
  ),
  totalOpenQuestions: PHASE3_OPEN_QUESTIONS.length, // 4
  totalRisks: PHASE3_RISKS.length, // 5
  totalDeliverables: PHASE3_DELIVERABLES.length, // 7
};
