/*
 * ANI-KUTA Project Review — typed data for the /project-review/ dashboard page.
 *
 * Source of truth: /home/z/my-project/review-findings.md (verified against the
 * actual codebase on 2026-08-13 via 5 parallel Explore sub-agents). Every
 * finding below is transcribed from that spec file — no summarisation, no
 * drops.
 *
 * Consumed by app/project-review/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * Design follows DESIGN.md (MEMORY OS v3):
 *  - Warm Canvas (#F2EEE8) bg, cards bg #FFFDFA, border #E8E2DA, rounded-2xl
 *  - Indigo primary, Teal success, Amber warning, Rose danger, Violet secondary
 *  - Severity colour coding: High=Rose, Medium-High=Rose/Amber, Medium=Amber,
 *    Low-Medium=Amber/Teal, Low=Teal, Expected=Violet, Resolved=Teal ✅
 */

/* ---------------------------------------------------------------------------
 * Section 1 — SNAPSHOT (hero)
 * ------------------------------------------------------------------------- */

export interface SnapshotMetric {
  metric: string;
  value: string;
  note: string;
}

export interface TechStackItem {
  label: string;
  value: string;
}

export const REVIEW_META = {
  reviewDate: "2026-08-13",
  reviewer: "Main agent (fresh full read-through of CORE_RULES + all AGENT-CONTEXT + codebase structure verification via 5 parallel Explore sub-agents)",
  repoState: "branch `main`, latest commit `5b8351ef`, CI green, dashboard live at https://testplay-byte.github.io/ANI-KUTA/",
} as const;

export const SNAPSHOT = {
  project: "ANI-KUTA — Android anime streaming/downloading app (Kotlin + Jetpack Compose + MPV + SQLDelight + Koin)",
  appId: "com.confused.anikuta",
  github: "testplay-byte/ANI-KUTA",
  dashboard: "https://testplay-byte.github.io/ANI-KUTA/",
  metricsIntro: "Verified metrics (re-derived from actual code, NOT docs):",
  metrics: [
    {
      metric: "Gradle modules",
      value: "46",
      note: "1 app + 26 core + 1 data + 18 feature",
    },
    {
      metric: "SQLDelight tables",
      value: "26",
      note: "across 15 .sq files (docs say 28 — stale; D-192 dropped 3 dead tables)",
    },
    {
      metric: "Kotlin files",
      value: "331",
      note: "(docs say 315 — stale; grew from D-193 v2 + Profile UI)",
    },
    {
      metric: "Decisions logged",
      value: "D-001 → D-193",
      note: "(D-121 is missing — numbering gap)",
    },
    {
      metric: "Lessons learned",
      value: "163",
      note: "86 MISTAKE / 49 PATTERN / 25 INSIGHT / 3 CORRECTION",
    },
    {
      metric: "Branch",
      value: "main only",
      note: "all feature branches merged + deleted",
    },
    {
      metric: "Phases complete",
      value: "ALL major phases",
      note: "0-4, 5a/5b/5c, B, C, D, DL.0-DL.8, WP, HI, UP, SC, TR, NOTIF, CW, Debug Bubble, Profile UI v1-v6",
    },
    {
      metric: "CI status",
      value: "✅ GREEN",
      note: "APK build + dashboard deploy both passing",
    },
  ] satisfies SnapshotMetric[],
  techStackIntro: "Tech stack (verified against libs.versions.toml):",
  techStack: [
    { label: "Language / Build", value: "Kotlin 2.2.0, Compose BOM 2025.03.00, AGP 8.9.1, Gradle 8.11.1, JDK 17" },
    { label: "SDK targets", value: "compileSdk 36, targetSdk 36, minSdk 24, ABIs arm64-v8a + armeabi-v7a only" },
    { label: "DI", value: "Koin 4.2.2 (primary DI) + Injekt (isolated to Aniyomi ext binary-compat)" },
    { label: "Database / Player", value: "SQLDelight 2.0.2 (NOT Room), MPV aniyomi-mpv-lib 1.18.n, Coil 3.0.4, OkHttp 5.0.0-alpha.14" },
    { label: "Navigation", value: "Hand-rolled navigation (D-150 — Nav3 tried + REMOVED)" },
    { label: "Dashboard", value: "Next.js 16 + React 19 + Tailwind 4 + TypeScript 5.9.3" },
  ] satisfies TechStackItem[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 2 — PROJECT HEALTH VERDICT
 * ------------------------------------------------------------------------- */

export interface HealthIndicator {
  area: string;
  status: string;
  /** Tone drives the StatusDot colour. */
  tone: "good" | "warning" | "danger";
}

export const HEALTH = {
  verdict: "HEALTHY ✅ with tracked debt",
  bullets: [
    "All major phases complete and on `main`. CI green. Well-documented (AGENT-CONTEXT is comprehensive). Modular 46-module architecture is sound.",
    "BUT there are 9 open concerns needing work + 4 accepted/low-priority items + doc-drift present across 8+ files.",
    "The app is debug-builds only — no production users, no published APK. Schema can be rebuilt freely (CORE_RULES §30).",
    "Biggest gap: many features are built but NOT device-verified. Downloads, Updates, Notifications, Profile UI all need on-device testing.",
  ],
  indicators: [
    { area: "Architecture", status: "✅ Sound — 46 modules, clean layering, contracts between UI/data", tone: "good" },
    { area: "Build/CI", status: "✅ Green — GitHub Actions (APK + dashboard), ABI-verified", tone: "good" },
    { area: "Documentation", status: "⚠️ Comprehensive BUT drifting (8+ files have stale counts)", tone: "warning" },
    { area: "Feature completeness", status: "✅ All major phases done; Phase 6+ (ads, backup, identity migration) deferred", tone: "good" },
    { area: "Device verification", status: "❌ Weak — many features untested on real devices", tone: "danger" },
    { area: "Code quality", status: "⚠️ 4 god-class files >2000 lines; 1 god-object (WatchKey)", tone: "warning" },
    { area: "Test coverage", status: "❌ No automated tests (debug phase — acceptable per §30)", tone: "danger" },
  ] satisfies HealthIndicator[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 3 — WHAT'S BUILT (feature grid)
 * ------------------------------------------------------------------------- */

export interface FeatureArea {
  title: string;
  /** Short headline shown under the title. */
  summary: string;
  /** Bullet list of built capabilities. */
  items: string[];
  /** Accent colour for the title icon dot. */
  accent: "primary" | "success" | "warning" | "secondary" | "danger";
}

export const WHAT_BUILT: FeatureArea[] = [
  {
    title: "Watch flow",
    summary: "Browse → Details → link source → episodes → resolver → MPV player → resume",
    items: [
      "MPV player with quality/subtitle/audio/speed sheets, episode switching, resume-seek, 85% auto-mark-watched, progress bar on episode list",
      "Multi-source episode metadata: AniZip + Jikan (filler detection) + Kitsu — 8 enriched columns",
    ],
    accent: "primary",
  },
  {
    title: "Downloads",
    summary: "Substantially complete, 5 known gaps",
    items: [
      "SAF storage + `.data.json` durable truth + `downloaded_episode` SQLDelight index",
      "HttpDownloader (Range-resume) + HlsDownloader (pure Kotlin)",
      "Foreground DownloadService (NetworkCallback auto-pause/resume, onTimeout API 35+)",
      "Offline playback: `content://` → `fd://` ParcelFileDescriptor bridge",
      "7-section settings UI (drag-reorderable priority/quality/audio/server)",
      "Downloads page (live queue + bulk actions + downloaded files)",
      "AutoDownloadEngine (5-step pipeline)",
    ],
    accent: "success",
  },
  {
    title: "Extensions",
    summary: "Aniyomi binary-compat",
    items: [
      "ChildFirstPathClassLoader, install/list/trust/enable, repo management",
      "Injekt isolated to ext-compat (registered in AnikutaApp.onCreate before Koin)",
    ],
    accent: "secondary",
  },
  {
    title: "Library",
    summary: "Browse + manage your collection",
    items: [
      "grid/list, categories, multi-select, sort, customize sheet",
    ],
    accent: "primary",
  },
  {
    title: "Search",
    summary: "AniList + sources",
    items: [
      "AniList GraphQL + filter sheet + recent searches",
    ],
    accent: "primary",
  },
  {
    title: "Profile UI v6",
    summary: "Personal stats + visualisations",
    items: [
      "genre radar, watch flow sidebar, time DNA donut, heatmap, timeline, avatar crop editor (pinch-zoom)",
    ],
    accent: "secondary",
  },
  {
    title: "Debug Bubble",
    summary: "DB-1..DB-9 — floating developer overlay",
    items: [
      "5-tab floating overlay (Screen/DB/Console/Network/App Info), DB activity tracking, export logs. debugImplementation only.",
    ],
    accent: "warning",
  },
  {
    title: "Updates + Notifications",
    summary: "D-193 v2 — smart-release engine",
    items: [
      "WorkManager smart engine, smart-release polling (learned_offset_ms weighted average), 3 triggers (on_schedule/on_watchable/on_immediate), per-anime tri-state config, dedicated notifications page",
    ],
    accent: "success",
  },
  {
    title: "Ratings",
    summary: "Per-anime + per-episode",
    items: [
      "per-anime 10-star + per-episode 10-star (0-100 backend)",
    ],
    accent: "primary",
  },
  {
    title: "Continue Watching",
    summary: "Browse carousel",
    items: [
      "single-row carousel on Browse",
    ],
    accent: "primary",
  },
  {
    title: "Settings",
    summary: "Personalisation",
    items: [
      "appearance (10 accent presets + CUSTOM), light/dark/AMOLED, 12 subtitle prefs, notification prefs, auto-link strategy",
    ],
    accent: "primary",
  },
  {
    title: "Content identity",
    summary: "Two-ID system",
    items: [
      "two-ID system (Main ID + Content ID), auto-link, SmartMatcher (Levenshtein fuzzy)",
    ],
    accent: "secondary",
  },
  {
    title: "Crash handling",
    summary: "Global + recoverable",
    items: [
      "global AnikutaCrashHandler + ErrorActivity (copyable logs)",
    ],
    accent: "danger",
  },
];

/* ---------------------------------------------------------------------------
 * Section 4 — CONCERNS & ISSUES (the core of the review)
 * ------------------------------------------------------------------------- */

export type Severity =
  | "high"
  | "medium-high"
  | "medium"
  | "low-medium"
  | "low"
  | "expected"
  | "resolved";

export interface Concern {
  id: number;
  concern: string;
  severity: Severity;
  effort: string;
  howToFix: string;
}

export interface AcceptedConcern {
  id: number;
  concern: string;
  severity: Severity;
  note: string;
}

export interface ResolvedConcern {
  id: number;
  concern: string;
  resolvedBy: string;
}

export const CONCERNS_VERIFIED_FACTS: string[] = [
  "HttpDownloader.reResolver is CONFIRMED ORPHANED — `DownloadModule.kt:92` passes `reResolver = null`; the catch block at `HttpDownloader.kt:277` is permanently unreachable dead code. A separate `app/.../ReResolver.kt` class exists + is Koin-registered but has ZERO consumers.",
  "MainActivity runBlocking CONFIRMED at line 470 (not 428 as docs claim) — SAF subtitle disk-scan on the main thread. ANR risk.",
  "4 god-class files CONFIRMED (wc -l): LibraryScreen 2471, DetailsScreen 2282, DetailsViewModel 2263, WatchScreen 2029.",
  "Actual tables = 26 (content.sq has 6 tables; content_ext + content_ext_repo + user_customization dropped in D-192).",
];

/** Open Concerns — need work. 9 items. */
export const CONCERNS_OPEN: Concern[] = [
  {
    id: 6,
    concern: "WatchKey god-object — 15 fields, 5 pre-serialized `\u001F`-delimited strings; MainActivity bloat (127-line inline serialization); blocks nav-backstack fix",
    severity: "medium-high",
    effort: "3-5h",
    howToFix: "Refactor to identifier-only (mainId + episodeNumber + videoUrl + startPosition); fetch rest by ID. Needs durable resolved-video store (D-066 double-resolve bug). Sub-agent review first.",
  },
  {
    id: 15,
    concern: "Download concurrency bug — starting a 2nd download auto-cancels the 1st (should queue, not cancel). DC1-DC5 fixes implemented but device-verification pending.",
    severity: "high",
    effort: "3-4h",
    howToFix: "Verify DC1-DC5 fixes on device. If still broken: investigate `DownloadQueue`/`DownloadOrchestrator` — is max-concurrent=1 cancelling instead of queuing?",
  },
  {
    id: 2,
    concern: "HttpDownloader.reResolver orphaned (D-149) — built but not wired; signatures mismatched (`json:String` vs `context,source,episode`)",
    severity: "medium",
    effort: "6-8h",
    howToFix: "~50-line adapter in `:app` implementing `HttpDownloader.ReResolver` + Koin binding + `DownloadModule.kt:92` null→getOrNull(). Group with #4 + #5.",
  },
  {
    id: 5,
    concern: "Outer retry loop not implemented (D-151) — `RetryPolicy` class referenced in KDoc but doesn't exist; failed downloads go straight to ERROR (max 2 attempts, spec says 6)",
    severity: "medium",
    effort: "3-4h",
    howToFix: "Build `RetryPolicy` class + outer retry loop in `launchDownload` catch block + backoff + notification UX. Group with #2 + #4.",
  },
  {
    id: 3,
    concern: "Main-thread runBlocking (`MainActivity.kt:470`) — `scanSubtitleFilesOnDisk` SAF enumeration on UI thread; ANR risk",
    severity: "medium",
    effort: "1-2h",
    howToFix: "Move to `Dispatchers.IO` + async feed-back. Standalone fix.",
  },
  {
    id: 8,
    concern: "4 god-class .kt files >2000 lines — LibraryScreen 2471, DetailsScreen 2282, DetailsViewModel 2263, WatchScreen 2029",
    severity: "low-medium",
    effort: "8-12h",
    howToFix: "Refactor candidates. Split by responsibility. Not blocking.",
  },
  {
    id: 17,
    concern: "`downloaded_episode.file_size = \"0\"` — file size not recorded after download",
    severity: "low",
    effort: "0.5h",
    howToFix: "Set `file_size` from the downloaded file's length after completion.",
  },
  {
    id: 18,
    concern: "Extensions page lag with ~240 available extensions — 240 icon fetches from raw.githubusercontent.com; no lazy loading / virtualization optimization",
    severity: "medium",
    effort: "2-3h",
    howToFix: "Pre-fetch icons in batch + cache. Investigate synchronous fetch.",
  },
  {
    id: 19,
    concern: "Extensions need better filtering — by language, NSFW, installed status. Currently only basic search.",
    severity: "medium",
    effort: "2-3h",
    howToFix: "Add filter chips (language dropdown, NSFW toggle, installed-status toggle).",
  },
];

/** Accepted / Low-Priority — 4 items. */
export const CONCERNS_ACCEPTED: AcceptedConcern[] = [
  {
    id: 1,
    concern: "AniList tracker is a placeholder — OAuth stores code as token, syncEntry returns true without API call",
    severity: "expected",
    note: "Full AniList GraphQL integration deferred. Not a bug — expected. ~4-6h when prioritized.",
  },
  {
    id: 7,
    concern: "Nav backstack doesn't survive process death (R7, D-150) — `remember { mutableStateListOf }` not `rememberSaveable`",
    severity: "low",
    note: "Accepted limitation. Hybrid `rememberSaveable(saver=listSaver)` fix (~1-2h). Blocked by #6 (WatchKey too large for Bundle).",
  },
  {
    id: 9,
    concern: "DB migrations use `onOpen` not `.sqm` files",
    severity: "low",
    note: "Acceptable for debug per CORE_RULES §30. Needs `.sqm` + `user_version` before production. ~2-4h.",
  },
  {
    id: 10,
    concern: "Release signing not configured — only debug buildType; debug keystore committed",
    severity: "expected",
    note: "Phase 9. Wait for user's production signal. ~1-2h.",
  },
];

/** Recently Resolved (D-192 / D-193) — 7 items, shown as ✅ DONE. */
export const CONCERNS_RESOLVED: ResolvedConcern[] = [
  { id: 12, concern: "activity_event was empty (zero callers)", resolvedBy: "D-192 Phase 2 — wired ActivityTracker.track() at 7 sites" },
  { id: 13, concern: "Updates not detecting new episodes", resolvedBy: "D-192 Phase 3 — reworked Updates (batch_type + episode_count)" },
  { id: 14, concern: "Notifications UI-only", resolvedBy: "D-193 v2 — full notifications system merged to main" },
  { id: 16, concern: "Download missing server/audio info", resolvedBy: "D-192 Phase 4 — data fix done (UI display still deferred)" },
  { id: 20, concern: "Details page stale-state flash", resolvedBy: "D-192 Phase 5 — loadGeneration counter" },
  { id: 21, concern: "Details \"No source linked\" race", resolvedBy: "D-192 Phase 5 — synchronous source-link pre-read" },
  { id: 22, concern: "user_customization table empty", resolvedBy: "D-192 Phase 1 — table dropped (dead)" },
];

/** Dashboard debt — 1 item. */
export const CONCERNS_DASHBOARD: AcceptedConcern[] = [
  {
    id: 11,
    concern: "Dashboard `schema.ts` uses planned Phase-1 table names not actual current schema",
    severity: "low",
    note: "Changes DB page UI. Deferred (dashboard polish). ~2-3h.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — DOC DRIFT CAUGHT
 * ------------------------------------------------------------------------- */

export interface DocDriftRow {
  whatDocsSay: string;
  actual: string;
  filesAffected: string;
}

export const DOC_DRIFT_INTRO =
  "Doc-drift is the dominant systemic risk (4 PATTERN lessons, promoted to CORE_RULES §26). These are documentation-vs-reality discrepancies, NOT code bugs.";

export const DOC_DRIFT: DocDriftRow[] = [
  {
    whatDocsSay: "\"28 tables\"",
    actual: "26 tables",
    filesAffected: "master.md, SESSION.md, knowledge/architecture.md, knowledge/dashboard.md, knowledge/tech-stack.md, lib/data.ts, Footer.tsx, decisions.md (8+ files)",
  },
  {
    whatDocsSay: "\"315 .kt files\"",
    actual: "331 .kt files",
    filesAffected: "master.md, SESSION.md, progress.md",
  },
  {
    whatDocsSay: "\"runBlocking at MainActivity.kt:428\"",
    actual: "line 470",
    filesAffected: "progress.md Deferred Concerns #3",
  },
  {
    whatDocsSay: "\"HttpDownloader guards on `127.0.0.1`\"",
    actual: "guards on `http://localhost`",
    filesAffected: "progress.md, download-research docs",
  },
  {
    whatDocsSay: "\"D-008 compileSdk 35\"",
    actual: "compileSdk 36",
    filesAffected: "decisions.md D-008",
  },
  {
    whatDocsSay: "\"21 tables\"",
    actual: "26 tables",
    filesAffected: "17-database-schema.md (historical — left as-is)",
  },
  {
    whatDocsSay: "decisions.md D-121",
    actual: "MISSING (gap D-120 → D-122)",
    filesAffected: "decisions.md",
  },
  {
    whatDocsSay: "decisions.md D-037/D-038",
    actual: "out of order (placed after D-051)",
    filesAffected: "decisions.md",
  },
  {
    whatDocsSay: "Repo root = single wrapper folder",
    actual: "repo root has `skills/` (69 files) + `worklog.md` committed — violates §4",
    filesAffected: "repo root (deferred per user)",
  },
];

export const DOC_DRIFT_ROOT_CAUSE =
  "Root cause: D-192 dropped 3 tables (28→26) but docs weren't grep-updated in the same session. Lesson L179: \"doc-drift compounds silently — ALWAYS re-derive counts from actual `.sq` files, not from docs.\"";

/* ---------------------------------------------------------------------------
 * Section 6 — FEATURES REMAINING / BACKLOG (Phase 6+)
 * ------------------------------------------------------------------------- */

export interface DesignedNotBuiltRow {
  feature: string;
  decision: string;
  effort: string;
  howToDoIt: string;
}

export interface BacklogGroup {
  title: string;
  /** Optional short subtitle / context line under the title. */
  subtitle?: string;
  /** Tabular rows (used by Designed-but-not-built + Download-future-gaps). */
  rows?: DesignedNotBuiltRow[];
  /** Simple bullet list (used by Skipped-by-user, Production-readiness, Process/quality). */
  bullets?: { label: string; note: string }[];
  /** Ordered numbered list (used by Download system future gaps). */
  numbered?: { label: string; note: string }[];
  /** Footer note rendered under the group. */
  footer?: string;
}

export const FEATURES_REMAINING: BacklogGroup[] = [
  {
    title: "Designed but not built",
    rows: [
      {
        feature: "Ads system",
        decision: "D-033 (designed, deferred)",
        effort: "TBD",
        howToDoIt: "`:core:ads` + `:core:activity-tracker` integration. Architecture leaves room. Details pending user input.",
      },
      {
        feature: "Backup/Restore",
        decision: "D-047 (deferred to Phase 5+)",
        effort: "Large",
        howToDoIt: "Needs identity system + all data tables first. Rebuild properly, don't copy old project. Research in `15-backup-research.md`.",
      },
      {
        feature: "Phase 5d Identity migration",
        decision: "D-054 (deferred)",
        effort: "Medium",
        howToDoIt: "Full ContentUID graph + ExternalReference + matching engine. Current two-ID (Main+Content) is enough to watch; migration is mechanical, not prerequisite.",
      },
      {
        feature: "AniList tracker full impl",
        decision: "D-045 (placeholder)",
        effort: "4-6h",
        howToDoIt: "Full GraphQL integration (OAuth flow, syncEntry API call, fetchEntry/search). Currently stub.",
      },
      {
        feature: "Custom color picker UI",
        decision: "D-053 (Phase 5f)",
        effort: "Medium",
        howToDoIt: "Selection + storage + live-apply. Accent selection works now; full palette editor deferred.",
      },
      {
        feature: "Extension settings UI",
        decision: "Future task",
        effort: "Medium",
        howToDoIt: "Extension's own preferences UI (per-extension config screens).",
      },
    ],
  },
  {
    title: "Skipped by user",
    bullets: [
      { label: "Manga reader (D-030)", note: "Confirmed SKIPPED. Modular slot exists." },
      { label: "Novels (D-030)", note: "Later." },
    ],
  },
  {
    title: "Download system future gaps",
    subtitle: "D-149, D-151 — DEFERRED per user. Full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`",
    numbered: [
      { label: "Wire proxy-churn re-resolve (~50-line adapter)", note: "Concern #2" },
      { label: "Fix `http://localhost` guard + `video_uri`/`video_url` column bug", note: "" },
      { label: "Build `RetryPolicy` + outer retry loop", note: "Concern #5" },
      { label: "Delete or wire `DownloadVideoPickerSheet`", note: "Concern #4" },
    ],
    footer: "Estimated ~6-8 hours total.",
  },
  {
    title: "Production readiness",
    subtitle: "Phase 9 — wait for user signal",
    bullets: [
      { label: "Release signing configuration", note: "" },
      { label: "`.sqm` migration files + `user_version` tracking", note: "replace `onOpen` guards" },
      { label: "CHECK constraints", note: "table rebuild" },
      { label: "SQLite UPSERT migration", note: "blocked by minSdk 24 → SQLite 3.9-3.22; needs 3.24+" },
    ],
  },
  {
    title: "Process / quality",
    bullets: [
      { label: "Device verification of all built features", note: "downloads, updates, notifications, profile UI" },
      { label: "Doc-debt sweep", note: "fix all stale counts (26 tables, 331 .kt files, line 470, etc.) across 8+ files" },
      { label: "God-class refactor", note: "split LibraryScreen/DetailsScreen/DetailsViewModel/WatchScreen by responsibility" },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Section 7 — FORWARD DIRECTION / RECOMMENDATIONS
 * ------------------------------------------------------------------------- */

export interface RecommendationStep {
  step: number;
  title: string;
  body: string;
  bullets?: string[];
  why?: string;
}

export const FORWARD_DIRECTION: RecommendationStep[] = [
  {
    step: 1,
    title: "Device verification FIRST (before any new features)",
    body: "Many features are built but untested on real devices. Get a clean APK from CI, run through:",
    bullets: [
      "Download system (enqueue, pause/resume, offline playback, auto-download, notifications, foreground service survival)",
      "Updates + Notifications (D-193 v2 — new-episode detection, smart-release polling, 3 triggers)",
      "Profile UI v6 (genre radar, watch flow, heatmap, avatar crop)",
      "Multi-source metadata (filler badges from Jikan)",
    ],
    why: "Why first: can't prioritize fixes without knowing what actually works on device.",
  },
  {
    step: 2,
    title: "HIGH-severity open concerns",
    body: "",
    bullets: [
      "#15 Download concurrency — verify DC1-DC5 fixes on device; if still broken, fix queue-vs-cancel logic.",
      "#6 WatchKey refactor — unblocks #7 (nav backstack). Refactor to identifier-only + durable resolved-video store. Sub-agent review first.",
      "#2 + #5 Download re-resolve + retry — wire the orphaned reResolver, build RetryPolicy. Group these (6-8h).",
    ],
  },
  {
    step: 3,
    title: "Architectural debt cleanup",
    body: "",
    bullets: [
      "#3 MainActivity runBlocking — move SAF scan to Dispatchers.IO (1-2h, standalone).",
      "#8 God-class files — split by responsibility (8-12h, not blocking).",
      "Doc-debt sweep — fix all counts (26 tables, 331 files, line 470) across 8+ files. One session, all at once (L154: partial fixes are worse than uniformly stale).",
    ],
  },
  {
    step: 4,
    title: "Phase 6 features (only after the above)",
    body: "",
    bullets: [
      "Ads system (D-033)",
      "Backup/Restore (D-047)",
      "Identity migration (D-054)",
      "AniList tracker full impl",
      "Release signing + production readiness (Phase 9)",
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Section 8 — TOP RISKS
 * ------------------------------------------------------------------------- */

export interface TopRiskRow {
  risk: string;
  whyItMatters: string;
  mitigation: string;
  /** Tone drives the row's left accent stripe colour. */
  tone: "danger" | "warning" | "secondary";
}

export const TOP_RISKS: TopRiskRow[] = [
  {
    risk: "Aniyomi/MPV binary-compat trap",
    whyItMatters: "Recurring (~17 lessons): OkHttp version pin, Injekt registration, metadata keys, cleartext traffic. Every ext/player change risks a runtime crash that CI can't catch.",
    mitigation: "Always cross-reference `REFERENCES/old-kuta/` + Animiru docs. Test on device.",
    tone: "danger",
  },
  {
    risk: "Doc-drift compounding",
    whyItMatters: "4 PATTERN lessons; promoted to §26. Counts stale across 8+ files. Next session can't trust docs.",
    mitigation: "Re-derive counts from code, not docs. Grep ALL files on every structural change (same session).",
    tone: "danger",
  },
  {
    risk: "CI false-greens",
    whyItMatters: "D-156: docs claimed green but CI had failed. No automated gate preventing false claims.",
    mitigation: "ALWAYS poll GitHub Actions API (`conclusion == \"success\"`), never trust docs.",
    tone: "warning",
  },
  {
    risk: "WatchKey god-object",
    whyItMatters: "15 fields, Bundle size risk, blocks nav-backstack fix (R7), MainActivity bloat.",
    mitigation: "Refactor to identifier-only (Step 2).",
    tone: "warning",
  },
  {
    risk: "MainActivity runBlocking",
    whyItMatters: "ANR risk on Downloads→Watch transition if subtitle DB is empty.",
    mitigation: "Move to IO (Step 3, 1-2h).",
    tone: "warning",
  },
  {
    risk: "God-class files",
    whyItMatters: "4 files >2000 lines = maintainability + refactor risk. WatchScreen especially (player lifecycle is fragile).",
    mitigation: "Split by responsibility (Step 3).",
    tone: "secondary",
  },
  {
    risk: "Merge-without-confirmation",
    whyItMatters: "L173: agent merged 5 phases to main without user confirmation (D-192). Erodes trust.",
    mitigation: "workflow.md: merge only on explicit user approval. Green CI ≠ merge authorization.",
    tone: "warning",
  },
  {
    risk: "Features built but untested",
    whyItMatters: "Downloads, Updates, Notifications, Profile UI — all on main, none device-verified. Unknown breakage.",
    mitigation: "Device verification FIRST (Step 1).",
    tone: "danger",
  },
];

/* ---------------------------------------------------------------------------
 * Section 9 — FOOTER NOTE
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE_BULLETS: string[] = [
  "This is a TEMPORARY review section built on 2026-08-13.",
  "It replaces the prior `/project-review/` page (which was a DC1-DC5 test checklist).",
  "Content is verified against the actual codebase (not docs) via 5 parallel Explore sub-agents.",
  "Remove this page + nav item + icon when no longer needed (4 files: `app/project-review/page.tsx`, `lib/projectReview.ts`, the 1 NAV_ITEMS entry, the 1 Sidebar icon key).",
  "For the full decision log, see `/decisions/`. For module details, see `/modules/`. For live progress, see `/progress/`.",
];

/* ---------------------------------------------------------------------------
 * Severity metadata (semantic colours per DESIGN.md §2.5)
 * ------------------------------------------------------------------------- */

export const SEVERITY_META: Record<
  Severity,
  { label: string; colorVar: string; symbol: string }
> = {
  high: {
    label: "High",
    colorVar: "var(--c-danger)",
    symbol: "!",
  },
  "medium-high": {
    label: "Medium-High",
    colorVar: "var(--c-danger)",
    symbol: "!",
  },
  medium: {
    label: "Medium",
    colorVar: "var(--c-warning)",
    symbol: "▲",
  },
  "low-medium": {
    label: "Low-Medium",
    colorVar: "var(--c-warning)",
    symbol: "▲",
  },
  low: {
    label: "Low",
    colorVar: "var(--c-success)",
    symbol: "↓",
  },
  expected: {
    label: "Expected",
    colorVar: "var(--c-secondary)",
    symbol: "○",
  },
  resolved: {
    label: "Resolved",
    colorVar: "var(--c-success)",
    symbol: "✓",
  },
};
