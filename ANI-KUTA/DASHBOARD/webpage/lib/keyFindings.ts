/*
 * ANI-KUTA Key Findings — typed data for the /key-findings/ dashboard page.
 *
 * Source of truth: the full project review of 2026-08-22 (worklog.md Task
 * IDs 0, R-1b, R-2, R-3, R-4, R-5 and 6). Every metric, concern, fix and
 * doc-drift row below was verified against the actual repo (settings.gradle.kts,
 * .sq files, git log, code greps) — not copied from docs. Do not add
 * unverified findings to this file.
 *
 * Consumed by app/key-findings/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * TEMPORARY SECTION (META.temporary): built for this review cycle. Removal =
 * delete app/key-findings/page.tsx + lib/keyFindings.ts + the 1 NAV_ITEMS
 * entry in lib/data.ts + the 1 Sidebar icon key ("findings").
 *
 * Severity colours (semantic tokens from globals.css — see DESIGN.md §2.5):
 *  CRITICAL = Rose danger
 *  HIGH     = Amber warning
 *  MEDIUM   = Violet secondary
 *  LOW      = neutral (default chip — text-secondary tint)
 *
 * Health-status colours:
 *  STRONG / HIGH / GOOD = Teal success · AT RISK / GAPS = Amber warning ·
 *  POOR = Rose danger
 */

/* ---------------------------------------------------------------------------
 * META
 * ------------------------------------------------------------------------- */

export const KEY_FINDINGS_META = {
  reviewDate: "2026-08-22",
  reviewer: "Main agent + 5 research sub-agents (R-1b, R-2, R-3, R-4, R-5)",
  repoState: "main @ 570c68f4 · D-239 · v0.2.22",
  /** Hero status pills (short repo-state tokens). */
  statusPills: ["main @ 570c68f4", "D-239", "v0.2.22"],
  method:
    "CORE_RULES + full AGENT-CONTEXT read → 5 parallel research agents → every metric re-verified against source (settings.gradle.kts, .sq files, git log, code greps)",
  temporary: true,
  removalNote:
    "page + lib + 1 NAV_ITEMS entry + 1 Sidebar icon key",
} as const;

/* ---------------------------------------------------------------------------
 * Section 1 — SNAPSHOT (verified metrics)
 * ------------------------------------------------------------------------- */

export interface SnapshotMetric {
  metric: string;
  value: string;
  note: string;
}

export const SNAPSHOT = {
  metrics: [
    {
      metric: "Gradle modules",
      value: "47",
      note: "docs claimed 46 — :core:app-update added without doc update",
    },
    {
      metric: "Kotlin files",
      value: "363",
      note: "docs claimed 331",
    },
    {
      metric: "SQLDelight tables",
      value: "23 across 15 .sq files",
      note: "docs claimed 26 (older: 28) — D-198 restructuring WAS implemented",
    },
    {
      metric: "App version",
      value: "0.2.22 (main)",
      note: "unmerged branch runs 0.2.46",
    },
    {
      metric: "Decision log",
      value: "commits at D-239",
      note: "decisions.md stops at D-198 — 41 decisions unlogged",
    },
    {
      metric: "Lessons learned",
      value: "163",
      note: "86 MISTAKE · 49 PATTERN · 25 INSIGHT · 3 CORRECTION",
    },
    {
      metric: "CI",
      value: "GREEN on main",
      note: "build-apk + deploy-dashboard both passing",
    },
    {
      metric: "Unmerged branches",
      value: "2",
      note: "functionality/improvements (42 commits, active) + feature/test-controller-v5 (43 commits, dormant)",
    },
  ] satisfies SnapshotMetric[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 2 — PROJECT HEALTH (verdict + 6 indicators)
 * ------------------------------------------------------------------------- */

export type HealthStatus =
  | "STRONG"
  | "HIGH"
  | "GOOD"
  | "AT RISK"
  | "GAPS"
  | "POOR";

export const HEALTH_STATUS_META: Record<
  HealthStatus,
  { colorVar: string }
> = {
  STRONG: { colorVar: "var(--c-success)" },
  HIGH: { colorVar: "var(--c-success)" },
  GOOD: { colorVar: "var(--c-success)" },
  "AT RISK": { colorVar: "var(--c-warning)" },
  GAPS: { colorVar: "var(--c-warning)" },
  POOR: { colorVar: "var(--c-danger)" },
};

export interface HealthIndicator {
  area: string;
  status: HealthStatus;
  line: string;
}

export const PROJECT_HEALTH = {
  verdictHeadline: "SOLID CODE, AT-RISK MEMORY",
  verdictBody:
    "The app architecture and features are strong and CI-green, but the recorded memory (decisions/changelog) lags reality by ~41 decisions, and three divergent lines of work exist (main + 2 branches).",
  indicators: [
    {
      area: "Architecture",
      status: "STRONG",
      line: "47 modules, api/impl split, Koin DI clean, UI/backend separation held",
    },
    {
      area: "Feature completeness",
      status: "HIGH",
      line: "all major phases shipped incl. D-225→D-238 overhauls (auto-link, match-preview, episode customization, schedule)",
    },
    {
      area: "CI discipline",
      status: "GOOD",
      line: "green, ABI-verified, failures tracked honestly since D-156",
    },
    {
      area: "Memory integrity",
      status: "POOR",
      line: "41 decisions unlogged, D-198 status wrong, changelog gap Aug 14–19, 3-way fork",
    },
    {
      area: "Branch hygiene",
      status: "AT RISK",
      line: "2 unmerged branches (85 combined commits), decision-number collision, 1 actively pushed",
    },
    {
      area: "Device verification",
      status: "GAPS",
      line: "download system never device-tested end-to-end; downloaded-subtitle fixes unverified",
    },
  ] satisfies HealthIndicator[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 3 — WHAT'S BUILT (14 feature areas)
 * ------------------------------------------------------------------------- */

export interface BuiltArea {
  area: string;
  status: string;
}

export const WHATS_BUILT = [
  {
    area: "Browse",
    status: "trending grid + continue-watching carousel + pull-to-refresh",
  },
  {
    area: "Details",
    status:
      "match-preview card, episode filter/sort/grouping, next-episode countdown, customizable background",
  },
  {
    area: "Library",
    status: "grid/list, categories, multi-select, sort, customize sheet",
  },
  {
    area: "Search",
    status: "AniList search + filter sheet + recent searches",
  },
  {
    area: "Watch (MPV)",
    status:
      "external subs/audio, episode switching, resume-seek, per-episode ratings",
  },
  {
    area: "Downloads",
    status:
      "queue + 6-attempt retry + foreground service + offline playback + .data.json persistence",
  },
  {
    area: "Extensions",
    status: "Aniyomi-compatible install/trust/enable, repo management",
  },
  {
    area: "Auto-Link",
    status: "forward + reverse + per-anime unlink blacklist",
  },
  {
    area: "Updates",
    status:
      "WorkManager smart engine, weighted release averaging, update categories",
  },
  {
    area: "Notifications",
    status: "per-anime tri-state config, schedule timers, dedup",
  },
  {
    area: "Schedule",
    status: "list + calendar view with per-anime color dots",
  },
  {
    area: "Profile",
    status: "genre radar, heatmap, timeline, watch-flow, avatar editor",
  },
  {
    area: "Debug Bubble",
    status: "5-tab floating panel (DB export, network, console)",
  },
  {
    area: "Platform",
    status: "global crash handler, in-app app-update system, Koin 22 modules",
  },
] satisfies BuiltArea[];

/* ---------------------------------------------------------------------------
 * Section 4 — OPEN CONCERNS (16 items, grouped by severity)
 * ------------------------------------------------------------------------- */

export type Severity = "critical" | "high" | "medium" | "low";

/** Display order for severity groups. */
export const SEVERITIES: readonly Severity[] = [
  "critical",
  "high",
  "medium",
  "low",
];

export const SEVERITY_META: Record<
  Severity,
  { label: string; colorVar: string }
> = {
  critical: { label: "Critical", colorVar: "var(--c-danger)" },
  high: { label: "High", colorVar: "var(--c-warning)" },
  medium: { label: "Medium", colorVar: "var(--c-secondary)" },
  low: { label: "Low / Accepted", colorVar: "var(--c-text-secondary)" },
};

export interface Concern {
  severity: Severity;
  title: string;
  /** 2-3 line detail with evidence (file:line). */
  detail: string;
  /** Suggested action. Absent on accepted/known-limitation items. */
  action?: string;
}

export const OPEN_CONCERNS: Concern[] = [
  // --- CRITICAL ---
  {
    severity: "critical",
    title: "Decision log forked across 3 refs",
    detail:
      "main's decisions.md ends at D-198; feature/test-controller-v5 carries its own D-197..D-202 (different decisions, same numbers); functionality/improvements works at D-240..D-242. A naive merge produces two contradictory D-197/D-198 blocks.",
    action: "Reconciliation pass BEFORE any branch merge.",
  },
  {
    severity: "critical",
    title: "D-198 status is factually wrong",
    detail:
      "decisions.md says 'PROPOSAL — not implemented, NO schema changes made', but commit 775876a2 (Aug 14) implemented it: content.sq now has main_entry + content_details, 23 tables total. The approval+implementation session was never logged.",
    action: "Correct the entry + backfill.",
  },
  // --- HIGH ---
  {
    severity: "high",
    title: "41 decisions unlogged (D-199..D-239)",
    detail:
      "Includes the DB-restructuring implementation, the app-update system (:core:app-update module), D-206..D-238 sessions. changelog.md has a ~5-day gap (Aug 14–19).",
    action: "Backfill from git history.",
  },
  {
    severity: "high",
    title: "AniList tracker reports fake success",
    detail:
      "syncEntry stub returns true without API call (AniListTracker.kt:264-269), trackerId hardcoded 0 (TrackSyncManager.kt:89), login gated on placeholder client ID. The real implementation (track_entry table, sync mutations, TrackSheet UI) exists ONLY on the unmerged functionality/improvements branch.",
    action: "Merge branch + make sync fail loudly until real.",
  },
  {
    severity: "high",
    title: "Download system never device-tested",
    detail:
      "The #1 deferred item across multiple sessions; checklist exists (download-device-testing-checklist.md) but zero execution evidence; downloaded-episode subtitle fixes (D-FIX-SUB) compile-verified only.",
    action: "Run the checklist on device.",
  },
  {
    severity: "high",
    title: "Active branch divergence",
    detail:
      "functionality/improvements (42 commits, v0.2.46, pushed during this review) is clean to merge TODAY but drifts hourly; its fix15–fix20 are undocumented.",
    action: "Land it as soon as its session completes.",
  },
  // --- MEDIUM ---
  {
    severity: "medium",
    title: "God-class files grew",
    detail:
      "DetailsScreen 3165 lines (+883 since last review), DetailsViewModel 2852 (+589), LibraryScreen 2504, WatchScreen 2018.",
    action: "Split by responsibility, Details first.",
  },
  {
    severity: "medium",
    title: "WatchKey still carries 5 serialized blobs",
    detail:
      "Whole episode list + tracks + metadata shipped through nav backstack; memory weight scales with series length; blocks the process-death (R7) fix.",
    action: "Registry pattern (ResolvedVideosKey precedent exists).",
  },
  {
    severity: "medium",
    title: "Dashboard data is stale in 8+ places",
    detail:
      "home/Footer/decisions/progress pages say 'D-001..D-186'; home shows BOTH '26 tables' and '28 tables' (real: 23); database page shows the pre-restructuring schema; 6 of 19 routes unreachable; /test-controller/ presents unmerged branch work as current.",
    action: "Truth-sweep session.",
  },
  {
    severity: "medium",
    title: "Extensions page gaps",
    detail:
      "No language filter for ~240 extensions (only search + NSFW + sort); bare AsyncImage with no placeholder/crossfade/cache key → flicker (ExtensionsSettingsScreen.kt:653-657, 707-711).",
    action: "Filter chips + Coil polish.",
  },
  {
    severity: "medium",
    title: "DownloadVideoPickerSheet is dead code",
    detail:
      "Built, never composed; the ASK fallback just logs (MainActivity.kt:995 TODO).",
    action: "Wire or delete.",
  },
  {
    severity: "medium",
    title: "Test-controller needs a reintegration plan",
    detail:
      "5 textual merge conflicts + D-197..D-202 numbering collision + TEST_BETA_FEATURE CI triggers self-marked 'remove before merging'; its dashboard half is already on main while the app half is not. Its deploy-workflow single-job fix (BlobNotFound) is worth cherry-picking.",
    action: "Dedicated reintegration session.",
  },
  // --- LOW / ACCEPTED ---
  {
    severity: "low",
    title: "Nav backstack lost on process death",
    detail:
      "Accepted limitation (D-150); fix blocked by WatchKey size.",
  },
  {
    severity: "low",
    title: "DB migrations are onOpen-only",
    detail:
      "Fine for debug (§30); needs .sqm + user_version before production.",
  },
  {
    severity: "low",
    title: "Release signing not configured",
    detail: "Phase 9; wait for user signal.",
  },
  {
    severity: "low",
    title: "Encrypted HLS unsupported",
    detail:
      "HlsDownloader is pure Kotlin; encrypted streams won't download. Known limitation.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — VERIFIED FIXED (balance section — 12 rows)
 * ------------------------------------------------------------------------- */

export interface FixedItem {
  concern: string;
  /** Fix evidence. */
  evidence: string;
}

export const VERIFIED_FIXED = [
  {
    concern: "HttpDownloader.reResolver wired",
    evidence: "D-194 ReResolverAdapter + Koin binding (was orphaned)",
  },
  {
    concern: "Main-thread runBlocking",
    evidence: "moved to Dispatchers.IO (ANR risk gone)",
  },
  {
    concern: "Outer retry loop",
    evidence: "D-195 RetryPolicy, 3×2=6 attempts with backoff",
  },
  {
    concern: "data.json write-back",
    evidence: "D-196 reconcileDataJsonFromContent on every launch",
  },
  {
    concern: "activity_event wiring",
    evidence: "ActivityTracker.track() at 6 call sites (was zero)",
  },
  {
    concern: "Updates scheduling",
    evidence: "PeriodicWorkRequest enqueued from AnikutaApp.kt:172",
  },
  {
    concern: "Notification posting",
    evidence: "UpdateEngine → NotificationSender → postNotification, deduped",
  },
  {
    concern: "Download concurrency",
    evidence:
      "Semaphore(1..5) queues properly; 2nd download no longer cancels 1st",
  },
  {
    concern: "Downloads UI info",
    evidence: "server/audio/quality/size shown; file_size recorded",
  },
  {
    concern: "Details stale-state flash",
    evidence: "loadGeneration counter discards stale async writes",
  },
  {
    concern: "'No source linked' race",
    evidence: "_linkedSource reset synchronously per load",
  },
  {
    concern: "Dead user_customization table",
    evidence: "dropped in D-192; DB restructuring landed (23 tables)",
  },
] satisfies FixedItem[];

/* ---------------------------------------------------------------------------
 * Section 6 — DOC DRIFT CAUGHT (claim vs verified reality)
 * ------------------------------------------------------------------------- */

export interface DocDriftRow {
  /** Claim as written in docs. */
  claim: string;
  /** Verified reality. */
  reality: string;
  /** Where the stale claim lives. */
  where: string;
}

export const DOC_DRIFT = [
  {
    claim: "26 tables (docs) / 28 (older docs)",
    reality: "23 tables",
    where: "knowledge/*, dashboard data",
  },
  {
    claim: "46 modules",
    reality: "47 modules",
    where: "master.md, data.ts",
  },
  {
    claim: "331 Kotlin files",
    reality: "363",
    where: "progress.md, data.ts",
  },
  {
    claim: "D-001..D-186 decisions confirmed",
    reality: "commits at D-239; log stops at D-198",
    where: "dashboard home/Footer/decisions",
  },
  {
    claim: "D-198 = PROPOSAL not implemented",
    reality: "IMPLEMENTED Aug 14 (775876a2)",
    where: "decisions.md",
  },
  {
    claim: "Only main branch remains",
    reality: "2 unmerged branches (43+42 commits)",
    where: "progress.md, SESSION.md",
  },
  {
    claim: "14 dashboard pages",
    reality: "19 routes, 11 in nav",
    where: "knowledge/dashboard.md",
  },
  {
    claim: "version 0.2.x unspecified",
    reality: "0.2.22 main / 0.2.46 branch",
    where: "docs silent",
  },
] satisfies DocDriftRow[];

/* ---------------------------------------------------------------------------
 * Section 7 — FEATURES REMAINING (NOW / NEXT / LATER)
 * ------------------------------------------------------------------------- */

export interface RemainingFeature {
  name: string;
  /** How to do it. */
  how: string;
  /** Effort estimate (absent on LATER/Phase 6+ items). */
  effort?: string;
}

export interface FeatureGroup {
  label: string;
  timeframe: string;
  items: RemainingFeature[];
}

export const FEATURES_REMAINING: {
  now: FeatureGroup;
  next: FeatureGroup;
  later: FeatureGroup;
} = {
  now: {
    label: "NOW",
    timeframe: "days",
    items: [
      {
        name: "Land functionality/improvements → main",
        how: "merge after its session ends (clean merge, zero conflicts today); backfill fix15–20 docs first",
        effort: "~1h + doc backfill",
      },
      {
        name: "Memory reconciliation pass",
        how: "backfill D-199..D-239 or add gap-note; fix D-198 status; close changelog gap; adopt branch2's D-240..D-242; renumber branch1's D-197..D-202 → D-243+",
        effort: "~2-3h",
      },
      {
        name: "Download-system device test",
        how: "execute download-device-testing-checklist.md end-to-end; verify downloaded subtitles (D-FIX-SUB)",
        effort: "~2h on device",
      },
    ] satisfies RemainingFeature[],
  },
  next: {
    label: "NEXT",
    timeframe: "1-3 weeks",
    items: [
      {
        name: "Test-controller reintegration",
        how: "resolve 5 conflicts, renumber decisions, drop TEST_BETA_FEATURE triggers, cherry-pick deploy single-job fix",
        effort: "~4-6h",
      },
      {
        name: "AniList tracker completion",
        how: "real client ID + trackerId identity mapping + sync mutations (base exists on branch2); fail loudly until real",
        effort: "~6-8h",
      },
      {
        name: "Extensions UX",
        how: "language filter chips + AsyncImage placeholder/crossfade/memoryCacheKey",
        effort: "~3h",
      },
      {
        name: "WatchKey registry refactor",
        how: "store blobs in a registry keyed by ResolvedVideosKey; NavKey becomes identifier-only; unblocks R7 fix",
        effort: "~4h",
      },
      {
        name: "God-class splits",
        how: "DetailsScreen (3165) first: sections → composables, logic → ViewModel",
        effort: "~8-12h",
      },
      {
        name: "Dashboard truth-sweep",
        how: "fix D-counts, rewrite database page for 23-table schema, consolidate 6 orphan routes, dynamic Footer stats",
        effort: "~4h",
      },
    ] satisfies RemainingFeature[],
  },
  later: {
    label: "LATER",
    timeframe: "Phase 6+",
    items: [
      {
        name: "Backup/restore + custom color picker",
        how: "Phase 5f; Aniyomi .tachibk import compat (D-047)",
      },
      {
        name: "Ads system",
        how: "D-033 designed: AdFormat registry + JSON placements + activity gating",
      },
      {
        name: "Manga reader + novels",
        how: "D-030: modular :feature:manga, content-type-aware models already in place",
      },
      {
        name: "Multi-extension providers",
        how: "D-031: Mangayomi/Cloudstream/Kotatsu via ExtensionProvider",
      },
      {
        name: "Production readiness",
        how: "release signing + .sqm migrations on user signal",
      },
      {
        name: "Encrypted HLS support",
        how: "needs exoplayer/hls lib decision",
      },
    ],
  },
};

/* ---------------------------------------------------------------------------
 * Section 8 — TOP RISKS (5 rows)
 * ------------------------------------------------------------------------- */

export interface TopRisk {
  risk: string;
  likelihood: string;
  impact: string;
  mitigation: string;
}

export const TOP_RISKS = [
  {
    risk: "Merge corrupts decision log",
    likelihood: "High if unreconciled",
    impact: "Critical",
    mitigation: "Reconcile BEFORE merging",
  },
  {
    risk: "Silent failures erode trust",
    likelihood: "Medium",
    impact: "High",
    mitigation:
      "Fake-success sync + unverified downloads — make failures loud, test on device",
  },
  {
    risk: "Parallel sessions conflict",
    likelihood: "Medium",
    impact: "Medium",
    mitigation: "Coordinate merges; merge branch2 promptly",
  },
  {
    risk: "God-class maintainability decline",
    likelihood: "Medium",
    impact: "Medium",
    mitigation: "Split before next big feature lands in Details",
  },
  {
    risk: "Decisions made on stale dashboard data",
    likelihood: "Medium",
    impact: "Medium",
    mitigation: "Truth-sweep the dashboard",
  },
] satisfies TopRisk[];

/* ---------------------------------------------------------------------------
 * Section 9 — FOOTER NOTE (temporary-section notice)
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE_BULLETS = [
  "Temporary section — built for this review cycle. Remove when no longer needed (4 files: app/key-findings/page.tsx, lib/keyFindings.ts, 1 NAV_ITEMS entry, 1 Sidebar icon key).",
  "Every metric verified against source on 2026-08-22 (settings.gradle.kts, .sq files, git log, code greps) — not copied from docs.",
  "Full methodology: CORE_RULES + complete AGENT-CONTEXT read → 5 parallel research sub-agents → main-agent verification.",
];
