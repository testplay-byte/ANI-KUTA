/*
 * ANI-KUTA Project Review — live findings (this session).
 *
 * Source: live codebase review (Tasks 0, 3-a, 3-b, 3-c, 3-d, 4) +
 * AGENT-CONTEXT/memory/* cross-reference. Hardcoded for static export —
 * no API calls.
 *
 * This file is consumed by app/project-review/page.tsx — a temporary,
 * additively-added section. No existing dashboard content depends on it.
 */

/* ---------------------------------------------------------------------------
 * Severity + status enums (semantic colors per DESIGN.md §2.5)
 * ------------------------------------------------------------------------- */

export type Severity = "high" | "medium" | "low" | "expected";

export const SEVERITY_META: Record<
  Severity,
  { label: string; colorVar: string; symbol: string; rank: number }
> = {
  high: { label: "HIGH", colorVar: "var(--c-danger)", symbol: "🔴", rank: 0 },
  medium: { label: "MEDIUM", colorVar: "var(--c-warning)", symbol: "🟠", rank: 1 },
  low: { label: "LOW", colorVar: "var(--c-success)", symbol: "🟡", rank: 2 },
  expected: { label: "EXPECTED", colorVar: "var(--c-secondary)", symbol: "⚪", rank: 3 },
};

export type ConcernStatus = "open" | "partially-fixed" | "expected" | "accepted";

export const CONCERN_STATUS_META: Record<
  ConcernStatus,
  { label: string; colorVar: string }
> = {
  open: { label: "Open", colorVar: "var(--c-danger)" },
  "partially-fixed": { label: "Partially fixed", colorVar: "var(--c-warning)" },
  expected: { label: "Expected", colorVar: "var(--c-secondary)" },
  accepted: { label: "Accepted limitation", colorVar: "var(--c-text-secondary)" },
};

/* ---------------------------------------------------------------------------
 * Section 1 — Hero / snapshot
 * ------------------------------------------------------------------------- */

export const HERO = {
  kicker: "Live Project Review",
  title: "ANI-KUTA Project Review",
  description:
    "A consolidated, at-a-glance view of project health — what's done, what's broken, what's left, and where to go next.",
  reviewedBadge: "Reviewed this session",
  commitHash: "57bbd17",
  commitLabel: "D-193 v2 Updates + Notifications",
  metrics: [
    { label: "Modules", value: "46" },
    { label: "DB tables", value: "26" },
    { label: ".sq files", value: "15" },
    { label: "Kotlin files", value: "331" },
    { label: "Decisions", value: "D-001→D-193" },
    { label: "Lessons", value: "134" },
    { label: "Phases", value: "all merged to main" },
    { label: "CI", value: "green" },
  ],
} as const;

/* ---------------------------------------------------------------------------
 * Section 2 — Project health summary (StatusDotLabel rows)
 * ------------------------------------------------------------------------- */

export interface HealthRow {
  colorVar: string;
  title: string;
  detail: string;
}

export const HEALTH_ROWS: HealthRow[] = [
  {
    colorVar: "var(--c-success)",
    title: "All major phases complete",
    detail:
      "Phases 0-4, 5a/b/c, B, C, D, WP, HI, UP, SC, TR, NOTIF, CW, Debug Bubble, Profile UI v1-v6 all merged to main.",
  },
  {
    colorVar: "var(--c-success)",
    title: "CI green",
    detail:
      "Latest run 31639789917 on commit 57bbd17 (D-193 v2 Updates + Notifications).",
  },
  {
    colorVar: "var(--c-warning)",
    title: "Debug-build only",
    detail:
      "No production users; schema can be rebuilt freely (CORE_RULES §30).",
  },
  {
    colorVar: "var(--c-danger)",
    title: "22 deferred concerns tracked",
    detail: "Known issues deferred per user; see Section 4 below.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 3 — What's built (compact grid)
 * ------------------------------------------------------------------------- */

export interface BuiltItem {
  group: string;
  detail: string;
}

export const BUILT_ITEMS: BuiltItem[] = [
  {
    group: "App shell",
    detail:
      "Hand-rolled nav (Nav3 removed D-150), crash handler + ErrorActivity, 24 NavKey dispatch.",
  },
  {
    group: "Screens",
    detail:
      "Browse, Details, Library, Search, Watch, Downloads, Extensions, History, Updates, Schedule, Notifications, Profile (v6), Settings, Appearance.",
  },
  {
    group: "Player",
    detail:
      "MPV (aniyomi-mpv-lib 1.18.n) — full init, 12 subtitle prefs, episode switching, error handling, 15s fatal watchdog, app-exit pause/resume.",
  },
  {
    group: "Downloads",
    detail:
      "DL.0-DL.8 — HttpDownloader (range-resume) + HlsDownloader (pure Kotlin), SAF storage + .data.json, foreground service, 2 notification channels, offline playback (content://→fd://).",
  },
  {
    group: "Content identity",
    detail:
      "Two-ID system (Main ID + Content ID), 6 lookup/detail tables, cross-source dedup (Phase C).",
  },
  {
    group: "Data management",
    detail:
      "Local-first metadata cache (Phase D), 6-hr browse auto-update, multi-stage details refresh, Coil 500MB disk cache.",
  },
  {
    group: "Auto-link",
    detail: "SmartMatcher (Levenshtein fuzzy) + AutoLinkService (Phase B).",
  },
  {
    group: "Episode metadata",
    detail:
      "Multi-source engine — AniZip (primary) + Jikan (filler/recap) + Kitsu (tertiary) — D-190.",
  },
  {
    group: "Watch progress",
    detail:
      "SQLDelight-persisted, 85% auto-mark, continue-watching carousel (Phase WP/CW).",
  },
  {
    group: "Updates + Notifications",
    detail:
      "WorkManager smart engine, 3-way toggle (Auto/Manual/Off), smart-release weighted averaging, 3 notification triggers, deep-link (D-193 v2).",
  },
  {
    group: "Ratings",
    detail: "Per-anime + per-episode 0-100 (Phase TR).",
  },
  {
    group: "Activity tracker",
    detail: "365-day event log, wired at 7 call sites (D-192).",
  },
  {
    group: "Debug bubble",
    detail:
      "Floating overlay, 5 tabs (Screen/DB/Console/Network/App Info), debugImplementation only (D-163).",
  },
  {
    group: "Dashboard",
    detail: "14 pages → GitHub Pages (this very site).",
  },
];

/* ---------------------------------------------------------------------------
 * Section 4 — Concerns & Issues (severity-grouped — THE KEY SECTION)
 * ------------------------------------------------------------------------- */

export interface Concern {
  id: number;
  severity: Severity;
  title: string;
  detail: string;
  status: ConcernStatus;
  statusNote?: string; // e.g. "(D-192)", "Est. ~0.5h", "(D-149)"
  estEffort?: string;
}

export const CONCERNS: Concern[] = [
  /* 🔴 HIGH — functional gaps */
  {
    id: 1,
    severity: "high",
    title: "Download concurrency",
    detail:
      "Starting a 2nd download cancels the 1st (needs user reproduce; research says it queues not cancels).",
    status: "open",
  },
  {
    id: 2,
    severity: "high",
    title: "downloaded_episode.file_size = \"0\"",
    detail: "File size not recorded after download.",
    status: "open",
    estEffort: "~0.5h",
  },
  {
    id: 3,
    severity: "high",
    title: "Download UI missing server/audio info",
    detail:
      "source_id, video_server, video_audio were NULL in the download row.",
    status: "partially-fixed",
    statusNote: "D-192 Phase 4 added the fields; UI display remaining",
  },

  /* 🟠 MEDIUM — architectural debt */
  {
    id: 4,
    severity: "medium",
    title: "HttpDownloader.reResolver orphaned",
    detail:
      "D-149 — built but not wired; DownloadModule.kt:92 hardcodes reResolver = null. Catch block silently falls through on localhost proxy death.",
    status: "open",
    estEffort: "~6-8h",
    statusNote: "D-149",
  },
  {
    id: 5,
    severity: "medium",
    title: "Main-thread runBlocking in Downloads→Watch SAF scan",
    detail:
      "MainActivity.kt:470 (docs said 428 — doc-drift). ANR risk.",
    status: "open",
    estEffort: "~1-2h",
  },
  {
    id: 6,
    severity: "medium",
    title: "Outer retry loop not implemented",
    detail:
      "D-151 — RetryPolicy referenced in KDoc but doesn't exist; failed downloads go straight to ERROR.",
    status: "open",
    estEffort: "~3-4h",
    statusNote: "D-151",
  },
  {
    id: 7,
    severity: "medium",
    title: "WatchKey god-object",
    detail:
      "15 fields, 5 pre-serialized \\u001F strings — Bundle size risk, blocks nav backstack persistence.",
    status: "open",
    estEffort: "~3-5h",
  },
  {
    id: 8,
    severity: "medium",
    title: "Nav backstack doesn't survive process death",
    detail:
      "R7, D-150 — remember { mutableStateListOf } not rememberSaveable. Blocked by #7.",
    status: "accepted",
    statusNote: "R7 / D-150",
  },
  {
    id: 9,
    severity: "medium",
    title: "4 god-class .kt files >2000 lines",
    detail:
      "LibraryScreen 2471, DetailsScreen 2282, DetailsViewModel 2263, WatchScreen 2029 (total 9045 lines).",
    status: "open",
    estEffort: "~8-12h",
  },
  {
    id: 10,
    severity: "medium",
    title: "Extensions page lag with ~240 available extensions",
    detail: "240 icon requests to raw.githubusercontent.com.",
    status: "open",
    estEffort: "~2-3h",
  },
  {
    id: 11,
    severity: "medium",
    title: "Extensions need better filtering",
    detail: "By language, NSFW, installed status.",
    status: "open",
    estEffort: "~2-3h",
  },
  {
    id: 12,
    severity: "medium",
    title: "Details page stale-state flash",
    detail:
      "Opening content B after closing A briefly shows A's data.",
    status: "partially-fixed",
    statusNote: "D-192 Phase 5 loadGeneration counter",
  },
  {
    id: 13,
    severity: "medium",
    title: "Details \"No source linked\" race",
    detail: "Async loadLinkedSource.",
    status: "partially-fixed",
    statusNote: "D-192 Phase 5 synchronous pre-read",
  },

  /* 🟡 LOW — debug-acceptable */
  {
    id: 14,
    severity: "low",
    title: "DB migrations use onOpen ALTER TABLE, not .sqm files",
    detail:
      "Acceptable for debug per CORE_RULES §30; needs .sqm + user_version before production.",
    status: "open",
    estEffort: "~2-4h",
  },
  {
    id: 15,
    severity: "low",
    title: "Release signing not configured",
    detail:
      "Only debug buildType; debug keystore committed. Phase 9.",
    status: "open",
    estEffort: "~1-2h",
  },
  {
    id: 16,
    severity: "low",
    title: "Dashboard lib/schema.ts uses planned Phase-1 table names",
    detail:
      "content_uid, etc. — not the actual current schema.",
    status: "open",
    estEffort: "~2-3h",
  },
  {
    id: 17,
    severity: "low",
    title: "user_customization table was empty",
    detail: "Settings lived in SharedPreferences.",
    status: "partially-fixed",
    statusNote: "D-192 dropped the table; app_settings created",
  },

  /* ⚪ EXPECTED — placeholder by design */
  {
    id: 18,
    severity: "expected",
    title: "AniList tracker is a placeholder",
    detail:
      "AniListTracker.kt OAuth stores code as token; syncEntry returns true without API call.",
    status: "expected",
    estEffort: "~4-6h",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — Doc-drift callout
 * ------------------------------------------------------------------------- */

export const DOC_DRIFT_ITEMS: { text: string; mono: string[] }[] = [
  {
    text: "Actual SQLDelight tables = 26 (docs everywhere say \"28\"). Worklog Task 3-a's \"actual 28\" is itself stale.",
    mono: ["26", "28"],
  },
  {
    text: "Actual .kt files = 331 (docs say \"315\").",
    mono: ["331", "315"],
  },
  {
    text: "Main-thread runBlocking is at MainActivity.kt:470 (docs say line 428).",
    mono: ["MainActivity.kt:470", "428"],
  },
  {
    text: "HttpDownloader guards on http://localhost (docs say 127.0.0.1).",
    mono: ["http://localhost", "127.0.0.1"],
  },
  {
    text: "decisions.md numbering drift: D-121 missing, D-037/D-038 out of order, D-008 says compileSdk 35 (actual 36).",
    mono: ["D-121", "D-037/D-038", "D-008", "35", "36"],
  },
  {
    text: "17-database-schema.md still says \"21 tables\" (historical doc).",
    mono: ["17-database-schema.md", "21"],
  },
  {
    text: "Repo root pollution: skills/ (69 generic sandbox skills) + a large worklog.md committed on main — violates CORE_RULES §4. Deferred per user.",
    mono: ["skills/", "worklog.md", "CORE_RULES §4"],
  },
];

/* ---------------------------------------------------------------------------
 * Section 6 — Features left / pending
 * ------------------------------------------------------------------------- */

export interface FeatureItem {
  name: string;
  ref?: string; // decision ref like "D-033"
  how: string;
}

export const FEATURES_DESIGNED: FeatureItem[] = [
  {
    name: "Ads system",
    ref: "D-033",
    how: "Fully designed (AdFormat interface, AdPlacementRegistry, AdSource, ActivityDetector); build per the design doc when ready.",
  },
  {
    name: "Backup/restore",
    ref: "D-047",
    how: "Research done (supports Aniyomi/Animiru/Mangayomi import); needs identity + all data tables (mostly ready post-Phase C/D).",
  },
  {
    name: "Manga reader",
    ref: "D-030",
    how: "Modular, later; new :core:metadata-manga + :feature:manga-reader.",
  },
  {
    name: "Novels",
    ref: "D-030",
    how: "Later; same modular pattern as Manga.",
  },
  {
    name: "Multi-extension providers",
    ref: "D-031",
    how: "Mangayomi/Cloudstream/Kotatsu behind the existing ExtensionProvider abstraction; one provider impl per ecosystem.",
  },
  {
    name: "Identity system evolution",
    ref: "D-032",
    how: "Flexible + switchable; current two-ID (Main + Content) is the starting point.",
  },
  {
    name: "AniList tracker full implementation",
    how: "Replace placeholder with real OAuth + GraphQL sync.",
  },
  {
    name: "Custom color picker / palette editor",
    how: "Phase 5f — palette editor for the CUSTOM accent preset.",
  },
  {
    name: "Activity-tracker UI",
    how: "Data is collected at 7 sites; just needs a visualization screen (Profile expansion or dedicated History-style screen).",
  },
  {
    name: "Release signing",
    how: "Phase 9 — configure release keystore + signing config when ready to publish.",
  },
];

export const FEATURES_NEEDS_VERIFICATION: FeatureItem[] = [
  {
    name: "Download system device testing",
    how: "DL.0-DL.8 implemented; needs on-device verification (enqueue, pause/resume, offline playback, auto-download, notifications, foreground service survival). Checklist: APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md.",
  },
  {
    name: "Subtitle loading for downloaded episodes",
    how: "D-152 fixes are in but UNVERIFIED on real device.",
    ref: "D-152",
  },
];

export const FEATURES_DEFERRED_DOWNLOAD_GAPS: FeatureItem[] = [
  {
    name: "Wire proxy-churn re-resolve",
    ref: "D-149",
    how: "~50-line adapter in :app + Koin binding.",
  },
  {
    name: "Fix http://localhost guard + video_uri/video_url column bug",
    how: "HttpDownloader localhost guard + DB column mismatch.",
  },
  {
    name: "Implement outer retry loop",
    ref: "D-151",
    how: "RetryPolicy class + catch-block loop.",
  },
  {
    name: "Delete or wire DownloadVideoPickerSheet",
    how: "Unused UI sheet — either wire or remove.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 7 — Recommended forward direction (numbered)
 * ------------------------------------------------------------------------- */

export interface Recommendation {
  title: string;
  detail: string;
}

export const RECOMMENDATIONS: Recommendation[] = [
  {
    title: "Device verification FIRST",
    detail:
      "Before any new features, run the DB test checklist + verify the 4 recent phases (DB-opt, ratings, continue-watching, watch-progress) + D-193 v2 Updates/Notifications on a real device. CI-green ≠ device-verified.",
  },
  {
    title: "Tackle HIGH-severity deferred concerns",
    detail:
      "Download concurrency (#1), file_size=0 (#2), finish download UI server/audio display (#3).",
  },
  {
    title: "Architectural debt cleanup",
    detail:
      "WatchKey refactor (unblocks R7 nav backstack persistence), move SAF scan off main thread (#5), split the 4 god-class files incrementally (#9).",
  },
  {
    title: "Wire the orphaned download re-resolve",
    detail: "D-149 — ~6-8h per the saved plan.",
  },
  {
    title: "Phase 6 features in priority order",
    detail:
      "Activity-tracker UI → AniList tracker → Custom color picker → Backup/restore → Ads → Manga/Novels → Multi-extension providers → Release signing.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 8 — Top risks
 * ------------------------------------------------------------------------- */

export interface Risk {
  title: string;
  detail: string;
  mitigation: string;
}

export const RISKS: Risk[] = [
  {
    title: "Aniyomi/MPV binary-compat trap",
    detail:
      "Invented contracts (metadata keys, OkHttp versions, classloaders, Observable-vs-suspend) silently break extensions.",
    mitigation:
      "Always verify against REFERENCES/old-kuta/ + Aniyomi reference before porting.",
  },
  {
    title: "Doc-drift is silent + corrosive",
    detail: "Multiple instances caught this review.",
    mitigation:
      "CORE_RULES §26 drift-check at every task end; grep ALL docs, not just the one being edited.",
  },
  {
    title: "CI green claims must be verified via API",
    detail: "A prior session claimed green but CI had failed.",
    mitigation:
      "Poll conclusion == \"success\" + read failure annotations; never trust a docs-only \"CI green\" line.",
  },
  {
    title: "NEVER install Android SDK/JDK locally",
    detail: "CORE_RULES §8 — local environment is CODE ANALYSIS ONLY.",
    mitigation:
      "Read code line-by-line + use Explore subagents + push to CI + read failure logs.",
  },
  {
    title: "No merging to main without explicit user confirmation",
    detail: "workflow.md gate.",
    mitigation:
      "Green CI is necessary not sufficient; the MERGE decision is the user's.",
  },
  {
    title: "SQLite/SharedPreferences migration hazards",
    detail:
      "Schema changes don't apply on existing installs; type changes cause ClassCastException.",
    mitigation:
      "Use .sqm migrations or onOpen table-rebuild before production; migrate SharedPreferences keys defensively.",
  },
  {
    title: "Sub-agent output verification",
    detail: "Sub-agents find real flaws but some are false positives.",
    mitigation:
      "Verify findings by reading source code yourself before implementing the fix.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 9 — Footer note (rendered above the global Footer)
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE =
  "This is a temporary review section, added additively — no existing dashboard content was modified. Source: live codebase + AGENT-CONTEXT review this session. Remove this page when no longer needed.";
