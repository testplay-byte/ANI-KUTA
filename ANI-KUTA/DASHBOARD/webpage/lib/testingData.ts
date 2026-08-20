/*
 * ANI-KUTA dashboard — Testing page data.
 *
 * Sources:
 *  - worklog.md (Task IDs DASHBOARD-TESTING, 4-b, 4-c, plus feature history)
 *  - APP/ani-kuta/DOCUMENTATION/* (phase plans)
 *  - AGENT-CONTEXT/memory/decisions.md (D-001, D-005, D-149, D-150, D-152, D-169)
 *
 * All data is static — no API calls.
 *
 * The TestingChecklist component persists checkmarks to localStorage under
 * the key "ani-kuta:testing-checklist:v1". If you change any step `id`,
 * bump the version suffix so old saved state is invalidated.
 */

/* ---------------------------------------------------------------------------
 * Types
 * ------------------------------------------------------------------------- */

export interface TestingStep {
  /** Stable unique id (used as the localStorage key for the checkmark). */
  id: string;
  /** Human-readable instruction shown next to the checkbox. */
  text: string;
}

export interface TestingSection {
  /** Stable unique id for the section. */
  id: string;
  /** Short phase tag, e.g. "Phase WP". */
  phase: string;
  /** Section heading, e.g. "Watch Progress + Watched Status". */
  title: string;
  /** One-line summary shown under the heading. */
  description: string;
  /** Accent color (CSS var or hex) used for the section bar + checkbox glow. */
  accentColor: string;
  /** Steps to check off. */
  steps: TestingStep[];
}

export interface LogcatFilter {
  /** Feature name shown on the left. */
  feature: string;
  /** Pasteable Logcat tag filter. */
  filter: string;
  /** Accent color for the chip. */
  color: string;
}

export type ConcernSeverity = "deferred" | "missing" | "stale" | "debt";

export interface TestingConcern {
  /** Decision/reference id, e.g. "D-149". */
  id: string;
  /** Short headline. */
  title: string;
  /** Longer body explaining the concern. */
  body: string;
  /** Classification — drives the left-border accent color. */
  severity: ConcernSeverity;
}

/* ---------------------------------------------------------------------------
 * Severity → color + label mapping (used by the concerns section).
 * ------------------------------------------------------------------------- */

export const CONCERN_SEVERITY_META: Record<
  ConcernSeverity,
  { label: string; color: string }
> = {
  deferred: { label: "Deferred", color: "var(--c-warning)" },
  missing: { label: "Not implemented", color: "var(--c-danger)" },
  stale: { label: "Stale", color: "var(--c-secondary)" },
  debt: { label: "Doc debt", color: "var(--c-primary)" },
};

/* ---------------------------------------------------------------------------
 * Testing checklist sections.
 *
 * Ordered by feature area (Watch Progress → History → Updates → Schedule →
 * Download). Each section maps to a phase tag referenced in the project
 * worklog and DOCUMENTATION/* plans.
 * ------------------------------------------------------------------------- */

export const TESTING_SECTIONS: TestingSection[] = [
  {
    id: "wp",
    phase: "Phase WP",
    title: "Watch Progress + Watched Status",
    description:
      "Verify episode watch-progress tracking, the 85% auto-mark threshold, swipe-to-toggle, and SQLDelight persistence.",
    accentColor: "var(--c-primary)",
    steps: [
      {
        id: "wp-install",
        text: "Download + install the latest CI APK from the `main` branch (all feature branches merged).",
      },
      {
        id: "wp-load",
        text: "Open an anime's details page → the episode list should load.",
      },
      {
        id: "wp-auto-mark",
        text: "Play an episode → watch past 85% → the episode should auto-mark as watched (grayscale + faded).",
      },
      {
        id: "wp-swipe",
        text: "Swipe right on an episode row → it should toggle watched/unwatched with a spring animation.",
      },
      {
        id: "wp-persist",
        text: "Close + reopen the app → the watched state should persist (SQLDelight).",
      },
      {
        id: "wp-unmark",
        text: "Manually un-mark a watched episode → it should show as unwatched.",
      },
      {
        id: "wp-remark",
        text: "Play the unmarked episode again → watch past 85% → it should re-auto-mark.",
      },
    ],
  },
  {
    id: "hi",
    phase: "Phase HI",
    title: "History Page",
    description:
      "Recently-watched episodes grouped by day, swipe-to-delete, row tap navigation, and the clear-all flow.",
    accentColor: "var(--c-secondary)",
    steps: [
      {
        id: "hi-open",
        text: "Go to More → History → should show recently-watched episodes grouped by day (Today / Yesterday / This Week / Earlier).",
      },
      {
        id: "hi-row",
        text: "Each row should show: cover, title, \"EP N · watched Xh ago\", progress bar, duration.",
      },
      {
        id: "hi-swipe-delete",
        text: "Swipe left on a row → should delete that entry.",
      },
      {
        id: "hi-tap-nav",
        text: "Tap a row → should navigate to the anime's details page.",
      },
      {
        id: "hi-clear-all",
        text: "Tap the trash icon → \"Clear all\" dialog → confirm → all history should be cleared.",
      },
    ],
  },
  {
    id: "up",
    phase: "Phase UP",
    title: "Updates Page",
    description:
      "Background update checking, the Updates | Schedule tab strip, new-episode surfacing, and the hourly WorkManager worker.",
    accentColor: "var(--c-success)",
    steps: [
      {
        id: "up-open",
        text: "Go to More → Updates → should show the Updates | Schedule tab strip.",
      },
      {
        id: "up-check",
        text: "Tap \"Check for updates\" (refresh icon) → should check due anime for new episodes.",
      },
      {
        id: "up-new",
        text: "If new episodes are found → should appear in the \"New\" section.",
      },
      {
        id: "up-ack",
        text: "Tap an update row → should navigate to the anime's details page + acknowledge the update.",
      },
      {
        id: "up-worker",
        text: "The WorkManager worker should run automatically every 1 hour (check logcat for \"UpdateCheckWorker\").",
      },
    ],
  },
  {
    id: "sc",
    phase: "Phase SC",
    title: "Schedule Page",
    description:
      "Upcoming episode airings grouped by day, EP N pill, live-ticking countdowns, and AniList airing refresh.",
    accentColor: "var(--c-warning)",
    steps: [
      {
        id: "sc-open",
        text: "Go to More → Updates → tap the \"Schedule\" tab.",
      },
      {
        id: "sc-groups",
        text: "Should show upcoming episode airings grouped by day (Today / Tomorrow / EEE MMM d).",
      },
      {
        id: "sc-row",
        text: "Each row should show: cover, title, \"EP N\" pill, live-ticking countdown.",
      },
      {
        id: "sc-refresh",
        text: "Tap \"Refresh\" → should fetch airing data from AniList.",
      },
    ],
  },
  {
    id: "dl",
    phase: "Phase DL",
    title: "Download System",
    description:
      "Previously-tested download pipeline: completion, offline playback, and language-tagged subtitle files.",
    accentColor: "var(--c-danger)",
    steps: [
      {
        id: "dl-complete",
        text: "Download an episode → should complete + show in Downloads page.",
      },
      {
        id: "dl-offline",
        text: "Play a downloaded episode offline → should play from local storage.",
      },
      {
        id: "dl-subs",
        text: "Subtitles should be named with the language + show in the subtitle picker.",
      },
    ],
  },
];

/* ---------------------------------------------------------------------------
 * Logcat capture instructions.
 * ------------------------------------------------------------------------- */

/** The pasteable "all at once" filter (concatenation of every feature filter). */
export const LOGCAT_ALL_FILTER =
  "tag:Anikuta:Core:WatchProgress | tag:Anikuta:Feature:Details:EpisodeRow | tag:Anikuta:Feature:History | tag:Anikuta:Core:Updates | tag:Anikuta:Core:Updates:Worker | tag:Anikuta:Feature:Updates | tag:Anikuta:Core:Schedule | tag:Anikuta:Feature:Schedule | tag:Anikuta:Core:Download";

/** Per-feature filters — each has a copy button in the UI. */
export const LOGCAT_FILTERS: LogcatFilter[] = [
  {
    feature: "Watch Progress",
    filter:
      "tag:Anikuta:Core:WatchProgress | tag:Anikuta:Feature:Details:EpisodeRow",
    color: "var(--c-primary)",
  },
  {
    feature: "History",
    filter: "tag:Anikuta:Feature:History",
    color: "var(--c-secondary)",
  },
  {
    feature: "Updates",
    filter:
      "tag:Anikuta:Core:Updates | tag:Anikuta:Core:Updates:Worker | tag:Anikuta:Feature:Updates",
    color: "var(--c-success)",
  },
  {
    feature: "Schedule",
    filter: "tag:Anikuta:Core:Schedule | tag:Anikuta:Feature:Schedule",
    color: "var(--c-warning)",
  },
  {
    feature: "Download",
    filter: "tag:Anikuta:Core:Download | tag:Anikuta:Core:Player:Subtitles",
    color: "var(--c-danger)",
  },
];

/** Ordered how-to steps for capturing logs in Android Studio. */
export const LOGCAT_HOWTO_STEPS: string[] = [
  "Open Android Studio → View → Tool Windows → Logcat.",
  "Connect your device (USB debugging enabled).",
  "In the Logcat filter bar, paste the filter for the feature you're testing.",
];

/** What to look for — maps log level to meaning. */
export const LOGCAT_LEVELS: { level: string; meaning: string; color: string }[] = [
  {
    level: "INFO",
    meaning: "User actions (save, toggle, check, fetch).",
    color: "var(--c-success)",
  },
  {
    level: "WARN",
    meaning: "Recoverable errors (source unavailable, rate limit).",
    color: "var(--c-warning)",
  },
  {
    level: "ERROR",
    meaning: "Failures (DB errors, exceptions).",
    color: "var(--c-danger)",
  },
];

/** Tips for sharing logs back to the agent. */
export const LOGCAT_SHARING_TIPS: string[] = [
  "Don't send the full raw logcat — it's too noisy.",
  "Filter by the relevant tag(s) above.",
  "Copy the filtered output (usually 20-50 lines per test action).",
  "Paste it in the chat with a note: \"Here are the logs for [feature] when I [action]\".",
];

/* ---------------------------------------------------------------------------
 * Concerns + open questions (rendered at the very bottom of the page).
 * ------------------------------------------------------------------------- */

export const TESTING_CONCERNS: TestingConcern[] = [
  {
    id: "D-149",
    title: "Proxy-churn re-resolve is NOT wired",
    body: "Downloads of localhost-proxy URLs may fail if the proxy is churned. Deferred to a future phase.",
    severity: "deferred",
  },
  {
    id: "DL-RETRY",
    title: "Outer retry loop not implemented",
    body: "Failed downloads go straight to ERROR (manual retry works). Max attempts = 2, spec says 6.",
    severity: "missing",
  },
  {
    id: "SC-CAL",
    title: "Calendar view not implemented",
    body: "The Schedule page has a list view only. The custom HorizontalPager calendar with 1mo/1yr limits is deferred.",
    severity: "deferred",
  },
  {
    id: "NOTIF",
    title: "Notification system not implemented",
    body: "The full design is ready but the implementation is pending (3 trigger types, per-content/per-episode config).",
    severity: "missing",
  },
  {
    id: "RATING",
    title: "Rating UI not implemented",
    body: "The RatingStore + schema are ready but the composable for setting/displaying ratings is pending.",
    severity: "missing",
  },
  {
    id: "CW-UI",
    title: "Continue Watching UI not placed",
    body: "The query + store method exist but the UI placement is undecided (Library? Browse? Home?).",
    severity: "missing",
  },
  {
    id: "DASH-STALE",
    title: "Dashboard data refresh (RESOLVED)",
    body: "RESOLVED — Dashboard data refreshed (Task 20 / DASHBOARD-REFRESH). Dashboard now correctly shows 46 modules / D-001..D-186 decisions / 28 tables across 15 .sq files / all phases done (incl. Phase DB debug-bubble + Profile UI v1–v6). Keeping this entry for historical reference; the testing checklist below still applies to the refreshed state.",
    severity: "stale",
  },
  {
    id: "D-150",
    title: "Nav3 dependency removed (RESOLVED)",
    body: "RESOLVED — Nav3 was fully REMOVED from all build.gradle.kts files (D-150 confirmed by main agent). The app uses hand-rolled nav via `mutableStateListOf<NavKey>` + `when(currentKey)` dispatch. R7 (process-death backstack survival) accepted as known limitation — backstack uses `remember` not `rememberSaveable`. Only orphaned comments remain (cleaned up by main agent).",
    severity: "stale",
  },
  {
    id: "D-001",
    title: "Repo root pollution",
    body: "skills/ (69 generic Z.ai skills) + 234KB worklog.md are committed to the repo root (D-001). Cleanup deferred per user.",
    severity: "debt",
  },
  {
    id: "D-005",
    title: "Doc-debt sweep deferred",
    body: "knowledge/* files + decisions.md numbering are stale (D-005).",
    severity: "debt",
  },
];
