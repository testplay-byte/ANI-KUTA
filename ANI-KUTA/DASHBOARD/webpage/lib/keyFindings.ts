/*
 * ANI-KUTA Key Findings — typed data for the /key-findings/ dashboard page.
 *
 * Source of truth: the full project review of 2026-08-24 (worklog.md Task
 * IDs R-1, R-2, R-3, R-4, R-5). Every metric, concern, fix and doc-drift
 * row below was verified against the actual repo (settings.gradle.kts,
 * 17 .sq files, git log, code greps, GitHub Actions API) — not copied
 * from docs. Do not add unverified findings to this file.
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
  reviewDate: "2026-08-24",
  reviewer: "Main agent + 5 research sub-agents (R-1, R-2, R-3, R-4, R-5)",
  repoState: "test-feature/video-cache-new-download @ f4be250 · D-249 · v0.2.47",
  /** Hero status pills (short repo-state tokens). */
  statusPills: ["test-feature @ f4be250", "D-249", "v0.2.47"],
  method:
    "CORE_RULES + full AGENT-CONTEXT read → 5 parallel research agents (R-1..R-5) → every metric re-verified against source (settings.gradle.kts, 17 .sq files, git log, code greps, GitHub Actions API)",
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
      value: "48",
      note: "docs claimed 46/47 — :core:app-update unlogged + :core:playback-cache added D-243",
    },
    {
      metric: "Kotlin files",
      value: "382",
      note: "docs claimed 331/363 — grew with D-243..D-249",
    },
    {
      metric: "SQLDelight tables",
      value: "24 across 17 .sq files",
      note:
        "playback_cache_entry added D-243; app.sq intentionally empty (app_metadata dropped D-198)",
    },
    {
      metric: "App version",
      value: "0.2.47 (test-feature branch)",
      note: "21 commits ahead of main @ 26e4772; versionCode 47",
    },
    {
      metric: "Decision log",
      value: "commits at D-249",
      note:
        "decisions.md has D-001..D-198 + D-242..D-249; gap D-199..D-241 (43 decisions) still unlogged",
    },
    {
      metric: "Lessons learned",
      value: "190",
      note: "grew from 163 (last review) — MISTAKE/PATTERN/INSIGHT/CORRECTION tags",
    },
    {
      metric: "CI",
      value: "GREEN on test-feature @ f4be250",
      note:
        "Build APK run 32661002201 = success; 2 intermediate CI-fix commits failed then fixed",
    },
    {
      metric: "Unmerged branches",
      value: "2 (incl. this branch)",
      note:
        "test-feature/video-cache-new-download (active, this review) + feature/test-controller-v5 (dormant, D-197..D-202 collision)",
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
  verdictHeadline: "STRONG EXECUTION, UNTESTED NEW ENGINE",
  verdictBody:
    "Video caching (D-243) + parallel download engine (D-244) shipped CI-green with a 4-layer fail-open design, but the parallel engine + HLS AES-128 path is NOT device-tested. The decision log still has a 43-decision gap (D-199..D-241), and the 4 god-class files grew significantly (LibraryScreen 3863 lines, +1392 since last review).",
  indicators: [
    {
      area: "Architecture",
      status: "STRONG",
      line:
        "48 modules, api/impl split, Koin DI clean, new :core:playback-cache isolated as its own module",
    },
    {
      area: "Feature completeness",
      status: "STRONG",
      line:
        "all major phases + video caching + parallel download engine + D-248/D-249 UX/continue-watching/browse overhauls shipped CI-green",
    },
    {
      area: "CI discipline",
      status: "GOOD",
      line:
        "green on D-243..D-249 (verified via GitHub Actions API); ABI-verified; compile-review-then-push loop held — intermediate failures fixed honestly",
    },
    {
      area: "Memory integrity",
      status: "AT RISK",
      line:
        "43 decisions unlogged (D-199..D-241); decisions.md D-198 status stale (says PROPOSAL, actually IMPLEMENTED); progress.md 'Current Phase' header only mentions D-243+D-244 though D-245..D-249 session blocks exist below",
    },
    {
      area: "Device verification",
      status: "GAPS",
      line:
        "video cache (Part A) emulator-tested; parallel download engine (Part B) + HLS AES-128 + stall watchdog + re-resolve path + rotating-key rejection + pause/resume with sidecar are compile-verified only",
    },
    {
      area: "Code health",
      status: "AT RISK",
      line:
        "4 god-classes grew (LibraryScreen 3863, DetailsViewModel 3510, DetailsScreen 3240, WatchScreen 2194); WatchKey still 15 fields; new PlaybackCacheManager 1758 lines",
    },
  ] satisfies HealthIndicator[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 3 — WHAT'S BUILT (16 feature areas)
 * ------------------------------------------------------------------------- */

export interface BuiltArea {
  area: string;
  status: string;
}

export const WHATS_BUILT = [
  {
    area: "Browse",
    status:
      "trending grid + continue-watching carousel (D-249 redesigned) + pull-to-refresh",
  },
  {
    area: "Details",
    status:
      "match-preview card, episode filter/sort/grouping, next-episode countdown, customizable background",
  },
  {
    area: "Library",
    status:
      "grid/list, categories, multi-select, sort, customize sheet, badge customization (D-242)",
  },
  {
    area: "Search",
    status: "AniList search + filter sheet + recent searches (D-248 fixes)",
  },
  {
    area: "Watch (MPV)",
    status:
      "external subs/audio, episode switching, resume-seek, per-episode ratings, 5 loadfile sites cache-aware",
  },
  {
    area: "Video Caching (NEW D-243/D-245/D-247)",
    status:
      "NanoHTTPD proxy on 127.0.0.1, LRU eviction, fail-open, HLS playlist rewriting, progress-window caching [pos-2min, pos+2min], background fill ±32MB, tap-to-play",
  },
  {
    area: "Downloads",
    status:
      "queue + 6-attempt retry + foreground service + offline playback + .data.json persistence",
  },
  {
    area: "Parallel Download Engine (NEW D-244)",
    status:
      "Range probe, budget-capped chunk workers, positional sparse-file writes, exponential backoff, stall watchdog, re-resolve incl 403, chunk sidecar, HLS parallel segments + AES-128-CBC in-memory decryption",
  },
  {
    area: "Download Resilience (NEW D-246)",
    status: "network resilience + instant teardown + cache identity persistence",
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
      "WorkManager smart engine, weighted release averaging, update categories (D-249 UI overhaul)",
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
    status:
      "genre radar, heatmap, timeline, watch-flow, avatar editor (D-248 honest stats)",
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
    title: "Decision log has a 43-decision gap (D-199..D-241)",
    detail:
      "decisions.md on this branch has D-001..D-198 then jumps to D-242..D-249. The 43 missing decisions cover the DB-restructuring implementation, the :core:app-update module, and the D-206..D-238 session work. changelog.md has a matching gap.",
    action: "Backfill from git history before any merge.",
  },
  {
    severity: "critical",
    title: "D-198 status is factually wrong",
    detail:
      "decisions.md says 'PROPOSAL — not implemented, NO schema changes made', but commit 775876a2 (Aug 14) implemented it: content.sq now has main_entry + content_details, 24 tables total. The approval+implementation session was never logged.",
    action: "Correct the entry to IMPLEMENTED + backfill the session.",
  },
  // --- HIGH ---
  {
    severity: "high",
    title: "Parallel download engine NOT device-tested",
    detail:
      "Part B of D-244 (ParallelHttpFetcher, HLS AES-128-CBC in-memory decryption, stall watchdog, re-resolve-incl-403 path, rotating-key rejection, pause/resume with sidecar, anti-shrink guard) is compile-verified only. Part A (cache) IS emulator-tested. The download-device-testing-checklist.md exists but has zero execution evidence against the parallel engine.",
    action: "Run the checklist against Part B on device before merge.",
  },
  {
    severity: "high",
    title: "God-class files grew significantly",
    detail:
      "LibraryScreen.kt 3863 lines (+1392 since last review's 2471), DetailsViewModel.kt 3510 (+1351 vs 2159), DetailsScreen.kt 3240 (+963 vs 2277), WatchScreen.kt 2194 (+177 vs 2017). New: PlaybackCacheManager.kt 1758, MainActivity.kt 1719.",
    action: "Split by responsibility — LibraryScreen first.",
  },
  {
    severity: "high",
    title: "WatchKey still carries 15 fields + 4 parse helpers",
    detail:
      "WatchKey.kt is still a god-object (15 fields, 5 pre-serialized \u001F-delimited strings, 4 parse helpers, 2 sibling data classes). Whole episode list + tracks + metadata shipped through nav backstack; memory weight scales with series length; blocks the process-death (R7) fix.",
    action: "Registry pattern (ResolvedVideosKey precedent exists).",
  },
  {
    severity: "high",
    title: "progress.md 'Current Phase' header stale",
    detail:
      "The Current Phase header (line 6) only mentions 'D-243 + D-244' though the D-245, D-246, D-247, D-248, D-249 session blocks all exist below (lines 18-48). 'What's Next' + 'Blockers' sections reference resolved items as deferred. 'Last Updated' verdict says '2 unmerged branches' (functionality/improvements was merged 2026-08-22, so only this branch + test-controller-v5 = 2 incl. this one, accurate) but says '47 modules / 23 tables' (actual 48 / 24).",
    action: "Update the header + Last Updated to D-249 / 48 / 24.",
  },
  // --- MEDIUM ---
  {
    severity: "medium",
    title: "Dashboard data stale in 8+ places",
    detail:
      "knowledge/* + dashboard data say 46 modules / 26 tables / D-001..D-186; actual 48 / 24 / D-249. The /key-findings/ page (this one) is fresh, but the OTHER dashboard pages (architecture, modules, database) still reflect main @ 26e4772 state. /test-controller/ presents unmerged branch work as current.",
    action: "Truth-sweep session after this branch merges.",
  },
  {
    severity: "medium",
    title: "Extensions page gaps",
    detail:
      "No language filter for ~240 extensions (only search + NSFW + sort); bare AsyncImage with no placeholder/crossfade/cache key → flicker (ExtensionsSettingsScreen.kt).",
    action: "Filter chips + Coil polish.",
  },
  {
    severity: "medium",
    title: "DownloadVideoPickerSheet is dead code",
    detail: "Built, never composed; the ASK fallback just logs a TODO.",
    action: "Wire or delete.",
  },
  {
    severity: "medium",
    title: "feature/test-controller-v5 dormant + D-197..D-202 collision",
    detail:
      "43 commits, unmerged, D-197..D-202 numbering collision with main's D-197/D-198. Its dashboard half is already on main while the app half is not. Its deploy-workflow single-job fix (BlobNotFound) is worth cherry-picking.",
    action: "Dedicated reintegration session.",
  },
  {
    severity: "medium",
    title: "FirstRunSetupDialog empty onClick",
    detail:
      "FirstRunSetupDialog.kt has an empty onClick handler — a real UX bug discovered this review.",
    action: "Wire the onClick or remove the dialog.",
  },
  {
    severity: "medium",
    title: "OkHttp 5.0.0-alpha.14 binary-compat risk",
    detail:
      "The app pins OkHttp 5.0.0-alpha.14 (an alpha). Aniyomi extension binaries were compiled against an older OkHttp; a future breaking change in OkHttp 5 stable could break extension compat.",
    action: "Monitor; pin to stable 5.0.0 when released.",
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
      "Fine for debug (CORE_RULES §30); needs .sqm + user_version before production.",
  },
  {
    severity: "low",
    title: "Release signing not configured",
    detail: "Phase 9; wait for user signal.",
  },
  {
    severity: "low",
    title: "AniListTracker KDoc stale",
    detail:
      "syncEntry IS implemented (D-242, line 282 — full SaveMediaListEntry GraphQL mutation) but the KDoc header still lists it under 'TODO (next session)'. OAuth flow + search/fetchEntry completeness unverified.",
    action: "Update the KDoc; verify OAuth/search completeness.",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — VERIFIED FIXED (balance section — 16 rows)
 * ------------------------------------------------------------------------- */

export interface FixedItem {
  concern: string;
  /** Fix evidence. */
  evidence: string;
}

export const VERIFIED_FIXED = [
  {
    concern: "HttpDownloader.reResolver wired",
    evidence:
      "D-194 ReResolverAdapter + Koin binding (R-4 verified: getOrNull<HttpDownloader.ReResolver>() bound for HttpDownloader + both fetchers)",
  },
  {
    concern: "Main-thread runBlocking",
    evidence:
      "moved to Dispatchers.IO (R-4 verified: all 5 grep matches are comments, no live runBlocking; onPlayEpisode uses appScope.launch + withContext(IO))",
  },
  {
    concern: "Outer retry loop",
    evidence:
      "D-195 RetryPolicy + D-244 reinforced, 3×2=6 attempts with backoff",
  },
  {
    concern: "data.json write-back",
    evidence: "D-196 reconcileDataJsonFromContent on every launch",
  },
  {
    concern: "activity_event wiring",
    evidence: "ActivityTracker.track() at call sites (was zero)",
  },
  {
    concern: "Updates scheduling",
    evidence: "PeriodicWorkRequest enqueued from AnikutaApp.kt",
  },
  {
    concern: "Notification posting",
    evidence: "UpdateEngine → NotificationSender → postNotification, deduped",
  },
  {
    concern: "Download concurrency",
    evidence:
      "Semaphore(1..5) queues properly + D-244 parallel engine; 2nd download no longer cancels 1st",
  },
  {
    concern: "Downloads UI info",
    evidence: "server/audio/quality/size shown; file_size recorded (D-151 Phase D)",
  },
  {
    concern: "Details stale-state flash",
    evidence: "loadGeneration counter discards stale async writes (D-227)",
  },
  {
    concern: "'No source linked' race",
    evidence: "_linkedSource reset synchronously per load (D-227)",
  },
  {
    concern: "Dead user_customization table",
    evidence: "dropped in D-192; DB restructuring landed (24 tables)",
  },
  {
    concern: "AniList syncEntry stub RESOLVED (D-242)",
    evidence:
      "full SaveMediaListEntry GraphQL mutation at AniListTracker.kt:282 (R-4 verified — was a stub returning true)",
  },
  {
    concern: "Video caching actually caches (D-245)",
    evidence:
      "learn-mode serving + HLS playlist rewriting (was: registered rows, ~0 bytes); root cause was unknown-Content-Length redirecting MPV to upstream",
  },
  {
    concern: "Download network resilience (D-246)",
    evidence: "instant teardown + cache identity persistence",
  },
  {
    concern: "Continue-watching lazy-init (D-249)",
    evidence:
      "fixed the lazy-init bug + updates UI overhaul + browse redesign",
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
    claim: "46/47 modules",
    reality:
      "48 modules (:core:app-update unlogged + :core:playback-cache added D-243)",
    where: "knowledge/*, data.ts, master.md",
  },
  {
    claim: "26/28 tables",
    reality:
      "24 tables across 17 .sq files (playback_cache_entry added D-243; app_metadata dropped D-198)",
    where: "knowledge/*, dashboard data",
  },
  {
    claim: "331/363 Kotlin files",
    reality: "382",
    where: "progress.md, data.ts",
  },
  {
    claim: "D-001..D-186/D-193 decisions confirmed",
    reality:
      "commits at D-249; decisions.md has D-001..D-198 + D-242..D-249 (gap D-199..D-241)",
    where: "dashboard home/Footer/decisions",
  },
  {
    claim: "D-198 = PROPOSAL not implemented",
    reality:
      "IMPLEMENTED Aug 14 (commit 775876a2) — content.sq has main_entry + content_details",
    where: "decisions.md",
  },
  {
    claim: "version 0.2.22 (main)",
    reality: "0.2.47 (test-feature branch); versionCode 47",
    where: "prior keyFindings.ts, data.ts",
  },
  {
    claim: "progress.md Current Phase: D-243 + D-244 only",
    reality:
      "D-245..D-249 session blocks exist below (lines 18-48) but header stale",
    where: "progress.md line 6",
  },
  {
    claim: "AniListTracker syncEntry TODO (next session)",
    reality:
      "IMPLEMENTED D-242 (AniListTracker.kt:282 — full SaveMediaListEntry mutation)",
    where: "AniListTracker.kt KDoc",
  },
  {
    claim: "Encrypted HLS unsupported",
    reality:
      "D-244 added in-memory AES-128-CBC decryption + MEDIA-SEQUENCE IVs (rotating-key rejection still applies)",
    where: "prior keyFindings.ts",
  },
  {
    claim: "17-database-schema.md: 21 tables",
    reality: "24 tables",
    where: "DOCUMENTATION/17-database-schema.md (historical)",
  },
  {
    claim: "Only main branch remains",
    reality: "2 unmerged branches (test-feature + test-controller-v5)",
    where: "SESSION.md, master.md",
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
        name: "Device-test the parallel download engine",
        how:
          "execute download-device-testing-checklist.md against Part B (ParallelHttpFetcher, HLS AES-128, stall watchdog, re-resolve-incl-403, pause/resume with sidecar, anti-shrink guard); Part A cache already emulator-tested E2E",
        effort: "~2-3h on device",
      },
      {
        name: "Memory reconciliation pass",
        how:
          "backfill D-199..D-241 (43 decisions) or add a gap-note; fix D-198 status to IMPLEMENTED; update progress.md 'Current Phase' header to D-249; update AniListTracker KDoc (syncEntry done); update 'Last Updated' verdict to 48 modules / 24 tables",
        effort: "~2-3h",
      },
      {
        name: "Merge test-feature → main (after device test)",
        how:
          "verify build on main post-merge (CI); truth-sweep dashboard data (48 modules, 24 tables, D-249); update knowledge/* + data.ts + Sidebar counts; consolidate orphan routes",
        effort: "~1h merge + ~4h dashboard sweep",
      },
    ] satisfies RemainingFeature[],
  },
  next: {
    label: "NEXT",
    timeframe: "1-3 weeks",
    items: [
      {
        name: "WatchKey registry refactor",
        how:
          "store blobs in a registry keyed by ResolvedVideosKey; NavKey becomes identifier-only (mainId + episodeNumber + videoUrl + startPosition); unblocks R7 process-death backstack fix",
        effort: "~4-5h",
      },
      {
        name: "God-class splits",
        how:
          "LibraryScreen (3863) first: sections → composables, logic → ViewModel; then DetailsScreen (3240) + DetailsViewModel (3510); new PlaybackCacheManager (1758) is a candidate to split by responsibility",
        effort: "~12-16h total",
      },
      {
        name: "Extensions UX",
        how:
          "language filter chips (dropdown) + NSFW toggle + installed-status toggle; AsyncImage placeholder/crossfade/memoryCacheKey for ~240 extensions",
        effort: "~3h",
      },
      {
        name: "Test-controller reintegration",
        how:
          "resolve 5 textual conflicts; renumber D-197..D-202 → D-250+; drop TEST_BETA_FEATURE CI triggers; cherry-pick deploy single-job fix",
        effort: "~4-6h",
      },
      {
        name: "AniList tracker completion",
        how:
          "syncEntry done (D-242); verify OAuth flow + search/fetchEntry completeness; real client ID; make sync fail loudly until real",
        effort: "~4-6h",
      },
      {
        name: "FirstRunSetupDialog onClick",
        how:
          "wire the empty onClick handler or remove the dialog (real UX bug)",
        effort: "~0.5h",
      },
      {
        name: "Dashboard truth-sweep",
        how:
          "rewrite database page for 24-table schema; update module count to 48; update decision range to D-249; consolidate orphan routes; dynamic Footer stats",
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
        how:
          "D-030: modular :feature:manga, content-type-aware models already in place",
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
        name: "Rotating-key HLS support",
        how:
          "D-244 rejects rotating keys; needs exoplayer/hls lib decision if required",
      },
    ],
  },
};

/* ---------------------------------------------------------------------------
 * Section 8 — TOP RISKS (8 rows)
 * ------------------------------------------------------------------------- */

export interface TopRisk {
  risk: string;
  likelihood: string;
  impact: string;
  mitigation: string;
}

export const TOP_RISKS = [
  {
    risk: "Parallel engine ships untested",
    likelihood: "High (not device-tested)",
    impact: "High",
    mitigation:
      "Run download-device-testing-checklist.md on Part B before merge",
  },
  {
    risk: "Silent failures erode trust",
    likelihood: "Medium",
    impact: "High",
    mitigation:
      "Fail-open is correct design, but device-test the fail paths; make cache-miss visible in debug bubble",
  },
  {
    risk: "God-class maintainability decline",
    likelihood: "High (LibraryScreen 3863, grew +1392)",
    impact: "Medium",
    mitigation: "Split LibraryScreen before next big feature lands there",
  },
  {
    risk: "Decisions made on stale dashboard data",
    likelihood: "Medium",
    impact: "Medium",
    mitigation:
      "This /key-findings/ page is fresh; truth-sweep the other dashboard pages after merge",
  },
  {
    risk: "Merge corrupts decision log",
    likelihood: "Medium (test-controller-v5 has D-197..D-202 collision)",
    impact: "High",
    mitigation:
      "Renumber test-controller-v5 before reintegration; backfill D-199..D-241",
  },
  {
    risk: "WatchKey Bundle-size crash on process death",
    likelihood: "Low (R7 accepted)",
    impact: "Medium",
    mitigation: "Registry refactor unblocks the rememberSaveable fix",
  },
  {
    risk: "OkHttp 5.0.0-alpha.14 binary-compat break",
    likelihood: "Low",
    impact: "Medium",
    mitigation: "Pin to stable 5.0.0 when released; monitor Aniyomi ext compat",
  },
  {
    risk: "HLS segment cache-key drift",
    likelihood: "Low (hash8, stale files replaced)",
    impact: "Low",
    mitigation: "URL-hash naming is drift-safe; monitor",
  },
] satisfies TopRisk[];

/* ---------------------------------------------------------------------------
 * Section 9 — FOOTER NOTE (temporary-section notice)
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE_BULLETS = [
  "Temporary section — built for the 2026-08-24 review cycle (test-feature/video-cache-new-download @ D-249). Remove when no longer needed (4 files: app/key-findings/page.tsx, lib/keyFindings.ts, 1 NAV_ITEMS entry, 1 Sidebar icon key).",
  "Every metric verified against source on 2026-08-24 (settings.gradle.kts, 17 .sq files, git log, code greps, GitHub Actions API, 5 research sub-agents) — not copied from docs.",
  "This page reflects the test-feature branch state (48 modules, 24 tables, D-249). The other dashboard pages reflect main @ 26e4772 (46 modules, 26 tables, D-193) — a truth-sweep is queued for merge time.",
];
