/*
 * ANI-KUTA Review & Roadmap — typed data for the /review/ dashboard page.
 *
 * Source of truth: the full project review #3 of 2026-08-25 (worklog.md
 * Task IDs 0, 1-a..1-e, 2), version-refreshed for v0.2.52 @ 0eb61110
 * (worklog Tasks 8 + 11 + 13 + 14 + 20 + 25: D-257..D-271 refresh rounds +
 * release-state flips — CI green on HEAD, v0.2.52 published). Every metric, concern,
 * fix and doc-drift row below was re-derived from source
 * (settings.gradle.kts, 17 .sq files, git log, code greps, GitHub API) —
 * never copied from docs.
 * Do not add unverified findings to this file.
 *
 * Consumed by app/review/page.tsx — a static Server Component, so no
 * "use client" needed. Hardcoded for the static export — no API calls.
 *
 * TEMPORARY SECTION: replaces the deleted /key-findings/ page (review #2,
 * 2026-08-24) per user instruction. Removal = delete app/review/page.tsx +
 * lib/reviewData.ts + the 1 NAV_ITEMS entry in lib/data.ts (the "findings"
 * Sidebar icon key is shared with the nav entry — remove it too if nothing
 * else reuses it).
 *
 * Severity colours (semantic tokens from globals.css — see DESIGN.md §2.5):
 *  HIGH   = Amber warning   (merge-gate blockers)
 *  MEDIUM = Violet secondary
 *  LOW    = neutral (default chip — text-secondary tint)
 *
 * Health-status colours:
 *  GOOD      = Teal success
 *  DEGRADING = Amber warning
 *  STALE     = Violet secondary
 *  BEHIND    = Rose danger
 *
 * Feature-status colours:
 *  NOT STARTED     = neutral
 *  PARTIALLY BUILT = Amber warning
 *  BUILT-UNTESTED  = Indigo primary
 *  BLOCKED-ON-USER = Rose danger
 */

/* ---------------------------------------------------------------------------
 * META
 * ------------------------------------------------------------------------- */

/** Hero status-pill tones (semantic colour mapping). */
export type StatusTone = "success" | "warning" | "secondary";

export const STATUS_TONE_META: Record<StatusTone, { colorVar: string }> = {
  success: { colorVar: "var(--c-success)" },
  warning: { colorVar: "var(--c-warning)" },
  secondary: { colorVar: "var(--c-secondary)" },
};

export const REVIEW_META = {
  reviewDate: "2026-08-25",
  title: "Project Review & Roadmap",
  description:
    "Full review of the test-feature/video-cache-new-download branch (59 commits ahead of main, v0.2.52) — verified state, open concerns, doc drift, and every remaining feature with its implementation path.",
  /** Hero status pills (short repo-state tokens, tone-coloured). */
  statusPills: [
    { label: "CI GREEN @ 0eb61110", tone: "success" },
    { label: "v0.2.52 RELEASED", tone: "success" },
    { label: "NOT MERGED", tone: "warning" },
    { label: "REVIEW #3", tone: "secondary" },
  ] as readonly { label: string; tone: StatusTone }[],
  reviewer:
    "Main agent + 5 read-only research sub-agents (R-1 concerns · R-2 decisions · R-3 features · R-4 metrics · R-5 dashboard)",
  method:
    "Every metric re-derived from source at 0eb61110 — never copied from docs. CI + release status verified via the GitHub API. Zero local builds (CORE_RULES §8).",
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
      metric: "Branch",
      value: "test-feature/video-cache-new-download @ 0eb61110",
      note: "59 commits ahead of main — NOT merged",
    },
    {
      metric: "CI",
      value: "Build APK GREEN on HEAD (run 32883920077)",
      note:
        "verified via GitHub API — D-266..D-271 batch #4 (Browse CW remove + hero hardware-bitmap fix + last-tab memory + library BEHIND/SEASON_YEAR/LAST_WATCHED sorts + scroll perf derivedStateOf + tracking auto-refresh); docs commit 0eb61110 is green",
    },
    {
      metric: "Release",
      value: "v0.2.52 published (stable)",
      note:
        "ani-kuta-v0.2.52.apk · 59.25 MB · Release APK run 32884229467 · arm64-v8a-only · debug-signed",
    },
    {
      metric: "Version",
      value: "0.2.52 (versionCode 52)",
      note: "+1 per improvement batch (D-251 release discipline)",
    },
    {
      metric: "Gradle modules",
      value: "48",
      note: "1 app + 28 core + 1 data + 18 feature (api/impl splits)",
    },
    {
      metric: "Kotlin",
      value: "390 files · ~85,700 LOC",
      note: "grew from 363 files at the Aug-24 review",
    },
    {
      metric: "Database",
      value: "24 tables · 17 .sq files",
      note: "0 .sqm migrations — debug schema freedom (§30)",
    },
    {
      metric: "Koin modules",
      value: "26 (+2 debug)",
      note: "incl. new playbackCacheModule",
    },
    {
      metric: "Decisions logged",
      value: "D-001..D-198 + D-242..D-272",
      note: "44 IDs missing (D-121 + D-199..D-241)",
    },
    {
      metric: "Lessons learned",
      value: "204",
      note: "docs still claim 163",
    },
    {
      metric: "CI workflows",
      value: "3",
      note: "build-apk · release-apk (new, v* tags) · deploy-dashboard",
    },
    {
      metric: "Emulator env",
      value: "rebuilt + verified (D-251)",
      note: "emulator 35.1.19 · cold boot ≈9 min · TCG, no KVM",
    },
  ] satisfies SnapshotMetric[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 2 — PROJECT HEALTH (verdict + 6 indicators)
 * ------------------------------------------------------------------------- */

export type HealthStatus = "GOOD" | "DEGRADING" | "STALE" | "BEHIND";

export const HEALTH_STATUS_META: Record<HealthStatus, { colorVar: string }> = {
  GOOD: { colorVar: "var(--c-success)" },
  DEGRADING: { colorVar: "var(--c-warning)" },
  STALE: { colorVar: "var(--c-secondary)" },
  BEHIND: { colorVar: "var(--c-danger)" },
};

export interface HealthIndicator {
  area: string;
  status: HealthStatus;
  line: string;
}

export const PROJECT_HEALTH = {
  verdictHeadline:
    "Strong engine, growing debt — one gate: device-verify the branch, then merge.",
  verdictBody:
    "D-243..D-260 shipped CI-green and v0.2.50 is published, but none of the branch is fully device-verified; meanwhile the codebase's largest files keep growing and docs have fallen ~60 claims behind.",
  indicators: [
    {
      area: "CI & release pipeline",
      status: "GOOD",
      line:
        "green on HEAD (0eb61110); automated stable releases on v* tags work (v0.2.52 shipped) — releases still ship debug-signed",
    },
    {
      area: "Feature velocity",
      status: "GOOD",
      line:
        "29 decisions (D-243..D-272): caching, parallel downloads, resilience, UX overhauls, browse redesign + hero v3 + hero blur + palette persistence + random palette, custom palettes + color-picker overhaul, search restore fix + recents section; device-feedback batch #4 — browse CW removed + hero hardware-bitmap fix + last-tab memory + library BEHIND/SEASON_YEAR/LAST_WATCHED fix + scroll perf derivedStateOf + tracking auto-refresh",
    },
    {
      area: "Device verification",
      status: "BEHIND",
      line:
        "the ENTIRE branch (video cache, parallel engine, library modes, releases) awaits one device-test pass",
    },
    {
      area: "Code size discipline",
      status: "DEGRADING",
      line:
        "6 files >1,500 LOC; LibraryScreen grew 2,471 → 4,001 lines (+62%) since main",
    },
    {
      area: "Documentation sync",
      status: "STALE",
      line:
        "~60 stale claims across 12 files; decision log has a 43-ID hole",
    },
    {
      area: "Data-layer quality",
      status: "GOOD",
      line:
        "0 Logger violations, secrets clean, download concurrency + retry + re-resolve all wired",
    },
  ] satisfies HealthIndicator[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 3 — WHAT'S BUILT (branch highlights)
 * ------------------------------------------------------------------------- */

export interface BuiltHighlight {
  /** Highlight title. */
  area: string;
  /** Decision reference(s), e.g. "D-243 · D-245 · D-247". */
  ref: string;
  /** 1-2 line detail. */
  detail: string;
}

export const WHATS_BUILT = [
  {
    area: "Video playback caching",
    ref: "D-243 · D-245 · D-247",
    detail:
      "NanoHTTPD proxy + stable sha256 identity; learn-mode serving (fixes \"registered but never cached\"); HLS playlist rewriting; ±2min progress-window caching. Emulator-verified E2E with a real extension.",
  },
  {
    area: "Parallel download engine",
    ref: "D-244",
    detail:
      "Range-probe + ≤16 chunk workers + sparse positional writes + exponential backoff; parallel HLS with in-memory AES-128-CBC decryption; pause/resume sidecars; single-stream fallback.",
  },
  {
    area: "Download network resilience",
    ref: "D-246",
    detail:
      "auto-pause on Wi-Fi loss → auto-resume on regain; instant Call.cancel teardown; offline transport errors → PAUSED (never burn retries into ERROR).",
  },
  {
    area: "UX overhaul batch",
    ref: "D-248",
    detail:
      "Continue-Watching direct-play; honest profile stats (organic-only counting — fixes \"2,333 episodes/day\"); two-way cover fallback + DownloadScanner anti-shrink guard.",
  },
  {
    area: "Updates + Browse redesign",
    ref: "D-249",
    detail:
      "continue-watching lazy-init race fixed (10s StateFlow await); compact Updates rows + clear-all; Browse hero banner + Trending/Popular/Top-Rated carousels.",
  },
  {
    area: "Settings icon unification",
    ref: "D-250",
    detail:
      "MoreListRow (bare 24dp icons) reused across all settings hubs; shared BackAction in :core:designsystem replaced 12 drifting copies.",
  },
  {
    area: "Library display modes + release pipeline",
    ref: "D-251",
    detail:
      "Comfortable \"Hide Titles\"; Cover-Only square zero-gap edge-to-edge; arm64-v8a-only stable releases; Check-for-Updates rewritten (prerelease-invisible root cause).",
  },
  {
    area: "Hero v2 + device-feedback fixes",
    ref: "D-255 · D-256",
    detail:
      "Palette-switch navigation fix (theme structural stability); custom-palette crash fixed (Compose version-skew root-caused via APK artifact inspection); hero redesigned with cover + banner + genre tags; update-check date parsing fixed for Android 7.x.",
  },
  {
    area: "Browse hero v3 + image preloading",
    ref: "D-257",
    detail:
      "Inset 16:9 rounded hero card (fixes the full-bleed \"square vibe\") + infinite pager that always auto-advances forward one page; SectionPreloader warms Coil memory+disk caches at exact card dims; 1dp rating-tag borders on Browse + Library badges.",
  },
  {
    area: "Search restore + palette editor overhaul",
    ref: "D-258 · D-259",
    detail:
      "Search defaults restore after clearing (idempotent loadDefaults + staleness guards; recents redesigned as a chip cloud); color picker rebuilt on new ThinSlider + keypad precise entry (NumericEntrySheet ported to :core:designsystem, 5-preset lines); palette sheet with sticky header + Reset and gradient scrim. D-260 bumps to v0.2.50.",
  },
  {
    area: "Device-feedback batch #3 (palette + hero + recents)",
    ref: "D-261 · D-262 · D-263 · D-264 · D-265",
    detail:
      "D-261 palette system overhaul + persistence fix (toArgb root cause + corruption migration) + brightness removed + 2 new customizable elements (cardHeading/cardDescription) + 28-site consumer sweep; D-262 hero blurred backdrop + restart-proof auto-advance + 12s cadence (2 CI fix rounds: coil3.request.ImageResult + SuccessResult is top-level); D-263 random palette (Dark/Light/Chaos) + colorful RGBA channel sliders; D-264 search recents dedicated horizontal-scroll section; D-265 version 0.2.51 bump + docs.",
  },
  {
    area: "Device-feedback batch #4 (Browse CW remove + hero fix + tab memory + library sorts + scroll perf + tracking refresh)",
    ref: "D-266 · D-267 · D-268 · D-269 · D-270 · D-271",
    detail:
      "D-266 Browse — removed Continue Watching section (4 files) + fixed hero banner hardware-bitmap crash (.allowHardware(false) + defensive copy in boxBlur for HARDWARE bitmaps + scrim lightened 0.30/0.55/0.88 → 0.22/0.45/0.82; root cause: D-262's boxBlur called getPixels() on a Coil-3 HARDWARE bitmap → IllegalStateException silently caught → backdrop blank); D-267 remember last-selected tab across cold start + recents (AppPreferences.lastTab + MainActivity AppRoot restore + onSelect save); D-268 Library BEHIND + SEASON_YEAR sorts + fixed LAST_WATCHED stub (new getLastWatchedAt query COALESCE(MAX(last_watched_at),0); BEHIND = caught-up top, behind bottom); D-269 scroll perf — collapsed wrapped in derivedStateOf (was read directly in parent → per-frame parent recompose) + contentType on 3 items() + @Immutable on LibraryEntry; D-270 detail tracking auto-refresh — mergeAniListIntoUnified now calls refreshTracking() after the link + resetState clears _trackEntry; D-271 version 0.2.52 bump + docs.",
  },
  {
    area: "AniList tracker",
    ref: "D-242 (on main)",
    detail:
      "real OAuth implicit grant (client 48714), real syncEntry SaveMediaListEntry mutation, fetchEntry, search — the \"placeholder\" era is over.",
  },
  {
    area: "Quality sweeps",
    ref: "D-250/251",
    detail:
      "77 verified-dead imports removed; dead collapsed/blur wiring fixed in SourcePreferences + ExtensionRepo; retry loop + re-resolver + notifications + updates engine all verified wired.",
  },
] satisfies BuiltHighlight[];

/* ---------------------------------------------------------------------------
 * Section 4 — OPEN CONCERNS (15 items, grouped by severity)
 * ------------------------------------------------------------------------- */

export type Severity = "high" | "medium" | "low";

/** Display order for severity groups. */
export const SEVERITIES: readonly Severity[] = ["high", "medium", "low"];

export const SEVERITY_META: Record<
  Severity,
  { label: string; colorVar: string }
> = {
  high: { label: "High", colorVar: "var(--c-warning)" },
  medium: { label: "Medium", colorVar: "var(--c-secondary)" },
  low: { label: "Low / Accepted", colorVar: "var(--c-text-secondary)" },
};

export interface Concern {
  severity: Severity;
  title: string;
  /** Detail with verified evidence (file:line where relevant). */
  detail: string;
  /** Area tag, e.g. "verification", "crash-risk". */
  area: string;
}

export const OPEN_CONCERNS: Concern[] = [
  // --- HIGH ---
  {
    severity: "high",
    title: "Entire branch unverified on device",
    detail:
      "29 decisions, 59 commits — device-feedback rounds addressed (D-255..D-272, v0.2.52 built + released CI-green); full checklist sign-off still pending. The merge gate is blocked on this.",
    area: "verification",
  },
  {
    severity: "high",
    title: "Parallel download engine Part B never runtime-tested",
    detail:
      "ParallelHttpFetcher / parallel HLS / AES-128 decryption tested only by compile probes + CI. Largest untested surface in the project.",
    area: "verification",
  },
  {
    severity: "high",
    title: "java.time without coreLibraryDesugaring at minSdk 24",
    detail:
      "HistoryViewModel.kt:107/117, ScheduleViewModel.kt:95-97, ScheduleStore.kt:56 use OffsetDateTime/LocalDateTime → crash risk on Android 7.x (API 24-25) — GitHubUpdateSource's date parsing was converted to Calendar in D-256. A code comment even claims \"API 26+ is our minSdk\" — wrong, minSdk is 24.",
    area: "crash-risk",
  },
  // --- MEDIUM ---
  {
    severity: "medium",
    title: "Main-thread runBlocking in DownloadService",
    detail:
      "3 live occurrences (onStartCommand ACTION_PAUSE_ALL :182, ACTION_CANCEL_ALL :183, onTimeout :202) block on mutex + per-task DB deletes → ANR risk with large queues.",
    area: "anr",
  },
  {
    severity: "medium",
    title: "God-class growth accelerating",
    detail:
      "LibraryScreen 4,001 · DetailsViewModel 3,510 · DetailsScreen 3,240 · WatchScreen 2,194 · PlaybackCacheManager 1,758 · MainActivity 1,733. All grew on this branch.",
    area: "maintainability",
  },
  {
    severity: "medium",
    title: "Extensions \"Available\" section not virtualized",
    detail:
      "~240 rows compose inside ONE LazyColumn item (ExtensionsSettingsScreen.kt:289-315); icons fire all at once, no placeholder/crossfade → the reported jitter.",
    area: "performance",
  },
  {
    severity: "medium",
    title: "FirstRunSetupDialog \"Skip for now\" does nothing",
    detail:
      "empty onClick (L247-249); dialog recomputes from needsSetup and stays on screen.",
    area: "ux-bug",
  },
  {
    severity: "medium",
    title: "WatchKey god-object persists",
    detail:
      "15 fields + 5 pre-serialized strings through the nav backstack; blocks the process-death backstack fix (R7).",
    area: "architecture",
  },
  {
    severity: "medium",
    title: "Extensions filtering gaps",
    detail:
      "no language filter; drag-reorder not persisted (ExtensionsSettingsScreen.kt:182 TODO \"Phase 5d\").",
    area: "ux-gap",
  },
  // --- LOW ---
  {
    severity: "low",
    title: "DownloadVideoPickerSheet still dead code",
    detail:
      "zero callers; EnqueueResult.ShowPicker branch just logs (MainActivity.kt:1056-1062).",
    area: "dead-code",
  },
  {
    severity: "low",
    title: "Nav backstack lost on process death",
    detail:
      "accepted limitation (D-150); fix blocked by WatchKey size.",
    area: "accepted",
  },
  {
    severity: "low",
    title: "Dashboard DB page shows dropped tables",
    detail:
      "lib/schema.ts still lists planned Phase-1 names (content, anilist_detail, app_metadata…) instead of actual (main_entry, content_details, track_entry, playback_cache_entry…).",
    area: "dashboard-debt",
  },
  {
    severity: "low",
    title: "Permanent 200ms OAuth polling loop in MainActivity",
    detail: "(:386-404) + login-error snackbar TODO (:400).",
    area: "minor",
  },
  {
    severity: "low",
    title: ".sqm migrations absent",
    detail:
      "onOpen idempotent pattern only; fine for debug (§30), required before production.",
    area: "deferred",
  },
  {
    severity: "low",
    title: "Release signing not configured",
    detail:
      "releases ship debug-signed (latest v0.2.52; documented, deliberate — \"release signing is Phase 2\").",
    area: "deferred",
  },
];

/* ---------------------------------------------------------------------------
 * Section 5 — VERIFIED FIXED (14 resolved concerns — the balance section)
 * ------------------------------------------------------------------------- */

export interface VerifiedFixed {
  concern: string;
  /** 1-line evidence (file:line where relevant). */
  evidence: string;
}

export const VERIFIED_FIXED = [
  {
    concern: "AniList tracker fully implemented",
    evidence:
      "real OAuth, syncEntry mutation (AniListTracker.kt:282-338), fetchEntry (:347), search (:398)",
  },
  {
    concern: "reResolver no longer orphaned",
    evidence:
      "ReResolverAdapter + Koin binding; injected into SingleConnectionFetcher, ParallelHttpFetcher, HttpDownloader",
  },
  {
    concern: "MainActivity SAF scan off the main thread",
    evidence:
      "appScope.launch + Dispatchers.IO (all 4 runBlocking matches are now comments)",
  },
  {
    concern: "Outer retry loop implemented",
    evidence:
      "RetryPolicy (3 attempts + backoff); RETRYING state set + displayed",
  },
  {
    concern: "activity_event wired",
    evidence:
      "7 track() call sites (app-open, watch, details×3, download, search)",
  },
  {
    concern: "Updates engine wired",
    evidence:
      "UpdateCheckWorker scheduled from AnikutaApp.onCreate (1h, network+battery); writes episode_update",
  },
  {
    concern: "Notifications post for real",
    evidence:
      "2 channels, per-anime tri-state, sub/dub gating, notification_sent dedup",
  },
  {
    concern: "Download concurrency fixed",
    evidence:
      "semaphore-based scheduler; new downloads QUEUE (never cancel running ones); pref 1..5",
  },
  {
    concern: "Server/audio info recorded + displayed",
    evidence:
      "download completion populates source_id/video_server/video_audio; InfoPills in UI",
  },
  {
    concern: "file_size recorded",
    evidence: "totalBytes (fallback downloadedBytes) on completion",
  },
  {
    concern: "Details stale-state flash fixed",
    evidence:
      "synchronous Loading + loadGeneration guard + resetState on dispose",
  },
  {
    concern: "\"No source linked\" race fixed",
    evidence: "_linkedSource initialized synchronously from PreferenceStore",
  },
  {
    concern: "user_customization dead table dropped",
    evidence: "DROP TABLE IF EXISTS guard in DatabaseDriverFactory",
  },
  {
    concern: "D-250/251 sweeps",
    evidence:
      "dead wiring fixed, 77 dead imports removed, update-checker fixed, SILENT-branch copy-paste bug fixed",
  },
] satisfies VerifiedFixed[];

/* ---------------------------------------------------------------------------
 * Section 6 — DOC DRIFT CAUGHT (top 12 of ~60 stale claims)
 * ------------------------------------------------------------------------- */

export interface DocDriftRow {
  /** File (or location) carrying the stale claim. */
  file: string;
  /** The stale claim itself. */
  claim: string;
  /** The verified reality. */
  reality: string;
}

export const DOC_DRIFT = {
  totalStaleClaims: "~60",
  filesAffected: 12,
  rows: [
    {
      file: "decisions.md",
      claim: "44 decision IDs missing",
      reality:
        "D-121 + the whole D-199..D-241 range — log jumps D-198 → D-242",
    },
    {
      file: "decisions.md D-198",
      claim: "\"PROPOSAL — no schema changes made\"",
      reality:
        "content.sq HAS main_entry + content_details (implemented in 775876a2)",
    },
    {
      file: "master.md + SESSION.md",
      claim: "\"Branch: main (all feature branches merged + deleted)\"",
      reality: "active unmerged branch, 59 commits ahead",
    },
    {
      file: "all knowledge/* files",
      claim: "\"46 modules / 26 tables / 15 .sq\"",
      reality: "48 / 24 / 17",
    },
    {
      file: "knowledge/emulator-testing.md",
      claim:
        "documents the D-246 environment (emulator 37.x, /home/z/android-sdk)",
      reality:
        "D-251 rebuilt it: emulator 35.1.19, /home/z/emu/emu.sh, statvfs shim, -qemu -m 1024",
    },
    {
      file: "lib/data.ts (dashboard)",
      claim:
        "frozen at the D-186 era — 46 modules, D-001..D-186, \"main branch\"",
      reality:
        "48 modules, D-272, active branch; MODULES array missing :core:playback-cache + :core:app-update",
    },
    {
      file: "4 stale KDocs in code",
      claim:
        "AniListTracker \"TODO (next session)\", AppearanceGeneralScreen \"static placeholders\", DetailsScreen:1796 \"not implemented\"",
      reality: "all three features are implemented",
    },
    {
      file: "knowledge/tech-stack.md",
      claim: "CI table lists 2 workflows; ABIs \"arm64+v7\"",
      reality: "3 workflows (release-apk.yml missing); arm64-v8a ONLY",
    },
    {
      file: "knowledge/project-overview.md",
      claim: "\"no published APK\"",
      reality: "stable releases published (latest v0.2.52)",
    },
    {
      file: "FUTURE-PHASE-DL-GAPS.md",
      claim: "items 1/2/4 \"deferred\" (retry, re-resolve, picker)",
      reality: "RetryPolicy + ReResolverAdapter exist and are wired",
    },
    {
      file: "Sidebar vs routes",
      claim: "13 nav items",
      reality: "20 routes exist — 6-7 orphan pages unreachable from nav",
    },
    {
      file: "lessons count in docs",
      claim: "\"163 lessons\"",
      reality: "201 lessons",
    },
  ] satisfies DocDriftRow[],
} as const;

/* ---------------------------------------------------------------------------
 * Section 7 — FEATURES REMAINING (NOW / NEXT / LATER)
 * ------------------------------------------------------------------------- */

export type FeatureStatus =
  | "NOT STARTED"
  | "PARTIALLY BUILT"
  | "BUILT-UNTESTED"
  | "BLOCKED-ON-USER";

export const FEATURE_STATUS_META: Record<FeatureStatus, { colorVar: string }> =
  {
    "NOT STARTED": { colorVar: "var(--c-text-secondary)" },
    "PARTIALLY BUILT": { colorVar: "var(--c-warning)" },
    "BUILT-UNTESTED": { colorVar: "var(--c-primary)" },
    "BLOCKED-ON-USER": { colorVar: "var(--c-danger)" },
  };

export interface RemainingFeature {
  name: string;
  /** Status (absent on LATER/future-phase items). */
  status?: FeatureStatus;
  /** The concrete implementation path. */
  how: string;
  /** Effort estimate (absent where no estimate exists). */
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
    timeframe: "device-verification gate + cheapest wins",
    items: [
      {
        name: "Device-verify the branch (D-243..D-272)",
        status: "BUILT-UNTESTED",
        how: "Install the v0.2.52 release APK → run DOCUMENTATION/download-device-testing-checklist.md §A-I + video-cache checks (replay instant-start, ±2min window bounds, tap-to-play resume) + library display modes + unified settings icons + hero blurred backdrop + 12s auto-advance (never stuck) + palette persistence (survives process-death) + 2 new elements (cardHeading/cardDescription) + random palette (Dark/Light/Chaos) + colorful RGBA sliders + search recents dedicated section + browse CW section removed (verify it's gone) + hero banner backdrop renders (no hardware-bitmap crash) + last-tab memory (cold start reopens in last tab) + library BEHIND + SEASON_YEAR + LAST_WATCHED sorts + scroll perf (no per-frame jank) + tracking auto-refresh after AniList link + in-app update 0.2.51→0.2.52. Logcat tags: Anikuta:Core:PlaybackCache, Anikuta:Core:Download:Parallel, Anikuta:Core:Download:Hls.",
        effort: "~2-3h on device",
      },
      {
        name: "Parallel engine Part B runtime test",
        status: "BUILT-UNTESTED",
        how: "Checklist §D (pause/resume/cancel) + §H (edge cases); advanced downloader ON vs OFF comparison; one HLS+AES source; verify thread slider, retry/backoff, re-resolve on proxy churn (403).",
        effort: "~2-3h",
      },
      {
        name: "Video-cache Part A re-verification on device",
        status: "BUILT-UNTESTED",
        how: "The D-245/D-247 fixes for the user-reported \"registered but never cached\" + \"caches whole episode\" bugs were emulator-verified only; replay same episode/server/quality → instant start from disk; confirm window bounds; tap-to-play resumes correctly.",
        effort: "~1h",
      },
      {
        name: "Merge branch → main",
        status: "BLOCKED-ON-USER",
        how: "Gated on items 1-3 — after device sign-off: merge → verify CI on main → run the dashboard truth-sweep (NEXT #13).",
        effort: "~1h + ~4h sweep",
      },
      {
        name: "Decision-log + KDoc reconciliation",
        status: "NOT STARTED",
        how: "Backfill D-199..D-241 from git history (2026-08-14→08-20) or write explicit gap-notes; flip D-198's status to Implemented; fix the 4 stale KDocs; refresh FUTURE-PHASE-DL-GAPS.md.",
        effort: "~2-3h",
      },
      {
        name: "DB quality analysis on a fresh export",
        status: "BLOCKED-ON-USER",
        how: "User provides a debug-bubble DB export after a clean-install test run; agent analyzes every table for wrong/missing writes (episode_update, notification_sent, watch_progress, activity_event, playback_cache_entry).",
      },
    ] satisfies RemainingFeature[],
  },
  next: {
    label: "NEXT",
    timeframe: "high-value, planned",
    items: [
      {
        name: "WatchKey registry refactor",
        status: "NOT STARTED",
        how: "NavKey becomes identifier-only (mainId + episodeNumber + videoUrl + startPosition); heavy payloads move to a registry keyed by resolvedVideosKey (precedent: ResolvedVideosRegistry). Unblocks the R7 process-death backstack fix via rememberSaveable(listSaver).",
        effort: "~4-5h",
      },
      {
        name: "God-class splits",
        status: "NOT STARTED",
        how: "LibraryScreen (4,001) first: extract section composables + move logic to LibraryViewModel; then the Details pair (3,510 + 3,240); then PlaybackCacheManager (1,758). Each split needs a sub-agent review pass.",
        effort: "~12-16h total",
      },
      {
        name: "Extensions UX polish",
        status: "PARTIALLY BUILT",
        how: "Add language-filter chips + installed-status filter to ExtensionFiltersBar; AsyncImage placeholder/crossfade/memoryCacheKey for the ~240 icons; persist drag-reorder order to PreferenceStore (ExtensionsSettingsScreen.kt:182 TODO).",
        effort: "~3-4h",
      },
      {
        name: "test-controller-v5 branch decision",
        status: "BLOCKED-ON-USER",
        how: "43-commit dormant branch (D-197..D-202 numbering COLLIDES with main's); decide reintegrate (renumber → D-272+, resolve 5 textual conflicts, cherry-pick its deploy-workflow fix) vs abandon.",
        effort: "~4-6h",
      },
      {
        name: "AniList tracker device round-trip + polish",
        status: "BUILT-UNTESTED",
        how: "Code complete; verify OAuth login → track → sync on device; update the stale KDoc; surface login errors (MainActivity.kt:400 snackbar TODO).",
        effort: "~1-2h",
      },
      {
        name: "FirstRunSetupDialog Skip fix",
        status: "NOT STARTED",
        how: "Session-scoped dismissed state (or remove the dead button).",
        effort: "~0.5h",
      },
      {
        name: "Dashboard truth-sweep (post-merge)",
        status: "NOT STARTED",
        how: "All non-review pages reflect main @ D-186; update to 48 modules / 24 tables / D-272 / v0.2.52 / branch truth; rewrite lib/schema.ts to the actual 24 tables; audit the 6-7 orphan routes.",
        effort: "~4h",
      },
      {
        name: "Episode-preload scheduler",
        status: "NOT STARTED",
        how: "The user's own idea (D-247 \"future direction\"): preload predicted-next episodes (next-in-series, continue-watching) into the playback cache so playback starts zero-buffer. The window machinery + identity system are the foundation; needs a metered-network policy decision.",
        effort: "~4-8h",
      },
      {
        name: "Cache-origin tap-to-play polish",
        status: "PARTIALLY BUILT",
        how: "Known v1 limits: (a) no episode switching from cache-origin launches — pass the episode list/registry; (b) dead stored upstream URL fails — re-resolve via cache identity + single retry.",
        effort: "~2-4h",
      },
      {
        name: "\"Delete entire content\" download action",
        status: "NOT STARTED",
        how: "Per-content bulk delete (folder + .data.json + all episode rows + playback-cache entries). DownloadStorageProvider.kt:410 TODO.",
        effort: "~1-2h",
      },
    ] satisfies RemainingFeature[],
  },
  later: {
    label: "LATER",
    timeframe: "future phases",
    items: [
      {
        name: "Backup/restore system",
        how: ".anikuta zip format + Aniyomi .tachibk import compat (design complete: 15-backup-research.md, D-047)",
      },
      {
        name: "Ads system (D-033)",
        how: "fully designed (AdFormat registry + JSON placements + LocalAdSource + ActivityDetector gating); zero code built, deliberately",
      },
      {
        name: "Manga reader (D-030)",
        how: "content_type column future-proofed; no modules yet",
      },
      {
        name: "Novels (D-030)",
        how: "future",
      },
      {
        name: "Multi-extension providers (D-031)",
        how: "Mangayomi/Cloudstream/Kotatsu; Aniyomi-compat only today",
      },
      {
        name: "Identity system evolution (D-032)",
        how: "two-ID system is the starting point; graph model remains the path",
      },
      {
        name: "Production readiness",
        how: "release signing + .sqm migrations + user_version tracking (wait for the user's production signal)",
      },
      {
        name: "Rotating-key HLS support",
        how: "engine deliberately rejects rotating EXT-X-KEY (fails safe); needs an hls-lib decision",
      },
      {
        name: "Notification enhancements",
        how: "open-Watch-directly option + Watch/Dismiss action buttons (user-requested future options)",
      },
      {
        name: "Activity-event statistics (StatsCalculator)",
        how: "events now recorded (7 call sites); the calculator was deferred (ponytail)",
      },
      {
        name: "Debug-bubble extras",
        how: "network tab can't see extension OkHttp traffic (separate NetworkHelper client); DB write danger-zone toggle",
      },
      {
        name: "Configurable image-cache size",
        how: "Coil disk cache hardcoded 500MB",
        effort: "~1-2h",
      },
      {
        name: "Misc accepted risks",
        how: "OkHttp 5.0.0-alpha.14 pin (move to stable 5.0.0 before release), MAL/TMDB trackers, Aniyomi ext-lib 1.5/1.6 TODO cleanups, setup-wizard parity decision",
      },
    ] satisfies RemainingFeature[],
  },
};

/* ---------------------------------------------------------------------------
 * Section 8 — TOP RISKS (8 rows)
 * ------------------------------------------------------------------------- */

export interface TopRisk {
  risk: string;
  impact: string;
  likelihood: string;
  mitigation: string;
}

export const TOP_RISKS = [
  {
    risk: "Branch integration risk — 59 unverified commits on one long-lived branch; conflict surface grows daily",
    impact: "High",
    likelihood: "Medium",
    mitigation: "Device-verify → merge promptly; keep future batches small",
  },
  {
    risk: "Android 7.x crash (java.time, no desugaring)",
    impact: "High",
    likelihood: "Medium",
    mitigation:
      "Add coreLibraryDesugaring OR replace with calendar-based code (~1-2h)",
  },
  {
    risk: "Parallel-engine untested surface",
    impact: "High",
    likelihood: "Medium",
    mitigation: "Dedicated device-test pass (checklist §D + §H)",
  },
  {
    risk: "ANR in DownloadService (main-thread runBlocking)",
    impact: "Medium",
    likelihood: "Low-Medium",
    mitigation: "Move pause/cancel work to a coroutine; post results back",
  },
  {
    risk: "God-class maintainability (trending up)",
    impact: "Medium",
    likelihood: "High",
    mitigation: "Staged splits, LibraryScreen first",
  },
  {
    risk: "Doc drift misleads the next session",
    impact: "Medium",
    likelihood: "High",
    mitigation: "The NOW #5 reconciliation batch",
  },
  {
    risk: "Debug-signed releases in the wild",
    impact: "Low (now)",
    likelihood: "Low",
    mitigation: "Release signing before any public distribution",
  },
  {
    risk: "OkHttp 5.0.0-alpha.14 pre-release pin",
    impact: "Medium",
    likelihood: "Low",
    mitigation: "Pin stable 5.0.0 at production approach",
  },
] satisfies TopRisk[];

/* ---------------------------------------------------------------------------
 * Section 9 — FOOTER NOTE (temporary-section notice)
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE_BULLETS = [
  "This is a TEMPORARY review section (full-project review #3, 2026-08-25) — it replaces the deleted /key-findings/ page (review #2, 2026-08-24) per user instruction. Nothing else on the dashboard changed. State refreshed for v0.2.52 @ 0eb61110 (D-257..D-271 + release flips).",
  "Review method: main agent + 5 read-only research sub-agents; every metric re-derived from source at 0eb61110; CI + release status verified via the GitHub API; zero local builds (CORE_RULES §8).",
  "Recommended immediate next step: run the v0.2.52 device-verification checklist (NOW items) against the released APK, report ✅/❌ per item — then merge.",
  "This page reflects the BRANCH state, not main. Other dashboard pages still reflect main @ the D-186 era (truth-sweep queued for merge time — NEXT #13).",
];
