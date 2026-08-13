/*
 * ANI-KUTA Test Checklist — on-device verification of the DC1-DC5 fixes.
 *
 * Replaces the former project-review content (concerns, features, risks).
 * Consumed by app/project-review/page.tsx — an interactive "use client"
 * page that renders checkboxes + persists state to localStorage so the
 * user's verification progress is saved across sessions.
 *
 * Source: /home/z/my-project/worklog.md (DC-FINAL, DC2-RES, DC2-FIX-A/A2,
 * DC3-CRASH-ANALYSIS, DC3-FIX-A2, DC4-RES, DC4-FIX) — every checklist
 * item below maps to a specific code change made in those sessions.
 *
 * Hardcoded for the static export — no API calls.
 */

/* ---------------------------------------------------------------------------
 * Types + categories
 * ------------------------------------------------------------------------- */

export type ChecklistStatus = "pending" | "pass" | "fail" | "n/a";

export interface TestChecklistItem {
  id: string;
  category: string;
  title: string;
  description: string;
  /**
   * Default status — the page lets the user cycle through
   * pending → pass → fail → n/a → pending by clicking the status chip,
   * or check the checkbox to flip pass/pending quickly.
   */
  status: ChecklistStatus;
  notes?: string;
}

export const CHECKLIST_CATEGORIES = [
  "Download — Basic",
  "Download — Concurrency",
  "Download — Retry & Recovery",
  "Download — UI Display",
  "Download — data.json",
  "Extensions — Filters",
  "Extensions — Updates",
  "Extensions — Performance",
  "Details — Stale State",
  "Details — Source Linking",
  "Nav — Backstack Persistence",
  "Overall — App Stability",
] as const;

/* ---------------------------------------------------------------------------
 * Status metadata (semantic colors per DESIGN.md §2.5)
 * ------------------------------------------------------------------------- */

export const STATUS_META: Record<
  ChecklistStatus,
  { label: string; colorVar: string; symbol: string }
> = {
  pending: {
    label: "Pending",
    colorVar: "var(--c-warning)",
    symbol: "○",
  },
  pass: {
    label: "Pass",
    colorVar: "var(--c-success)",
    symbol: "✓",
  },
  fail: {
    label: "Fail",
    colorVar: "var(--c-danger)",
    symbol: "✕",
  },
  "n/a": {
    label: "N/A",
    colorVar: "var(--c-text-secondary)",
    symbol: "—",
  },
};

/* ---------------------------------------------------------------------------
 * Hero / page metadata
 * ------------------------------------------------------------------------- */

export const CHECKLIST_HERO = {
  kicker: "On-Device Verification",
  title: "Test Checklist",
  description:
    "Every fix from the DC1–DC5 sessions needs verification on a real device. " +
    "Check off each item as you test it — your progress is saved locally so you " +
    "can pause and resume. Mark items pass / fail / n/a using the status chip " +
    "on the right of each row.",
  sessionRef: "DC1–DC5 fixes",
  commitRef: "833b7702",
} as const;

/* ---------------------------------------------------------------------------
 * Footer note
 * ------------------------------------------------------------------------- */

export const FOOTER_NOTE =
  "This page tracks on-device verification of the DC1–DC5 fixes. " +
  "Progress is persisted to localStorage on this device only. " +
  "Use the Reset button in the hero to clear all checks.";

/* ---------------------------------------------------------------------------
 * Test checklist items — grouped by category.
 *
 * Each item is a single, testable behavior the user can verify on-device.
 * The `description` field gives concrete steps + the fix it verifies.
 * ------------------------------------------------------------------------- */

export const TEST_CHECKLIST: TestChecklistItem[] = [
  /* ── Download — Basic ─────────────────────────────────────────────── */
  {
    id: "dl-basic-1",
    category: "Download — Basic",
    title: "Download a single episode — completes",
    description:
      "Pick any episode → tap Download → wait for completion. " +
      "Verifies: the DC3-FIX-A2 lazy-resolver lambda fixed the StackOverflow " +
      "on startup, so the download pipeline can actually run.",
    status: "pending",
  },
  {
    id: "dl-basic-2",
    category: "Download — Basic",
    title: "Downloaded file plays correctly (offline)",
    description:
      "After the download finishes, switch to the Watch screen → verify the " +
      "episode plays in MPV with no network. Verifies: SAF content:// → fd:// " +
      "URI handoff still works post-DC2 changes.",
    status: "pending",
  },
  {
    id: "dl-basic-3",
    category: "Download — Basic",
    title: "Downloaded file size shows in the UI (not 0)",
    description:
      "On DownloadsScreen, the downloaded episode's row should show a real byte " +
      "count — not \"0 B\" or blank. Verifies: file_size column is populated by " +
      "HttpDownloader (DC-FIX-2) and not overwritten by the scanner.",
    status: "pending",
  },

  /* ── Download — Concurrency ──────────────────────────────────────── */
  {
    id: "dl-conc-1",
    category: "Download — Concurrency",
    title: "Enqueue ep2 while ep1 downloads → ep1 continues",
    description:
      "Start downloading episode 1. While it's downloading, enqueue episode 2. " +
      "Verify ep1 is NOT cancelled — its progress bar keeps moving. " +
      "Verifies: the queue permits (queueSize > 1) without cancelling the " +
      "active download.",
    status: "pending",
  },
  {
    id: "dl-conc-2",
    category: "Download — Concurrency",
    title: "Enqueue ep3 while ep2 queues → ep2 continues",
    description:
      "Continue from above — enqueue episode 3 while ep2 is downloading. " +
      "Verify ep2 keeps progressing (not cancelled by the new enqueue). " +
      "Verifies: enqueue is non-destructive to in-flight tasks.",
    status: "pending",
  },
  {
    id: "dl-conc-3",
    category: "Download — Concurrency",
    title: "When ep1 finishes → ep2 starts automatically",
    description:
      "Watch the queue. When ep1 completes, ep2 should begin downloading " +
      "within ~1s without any user action. Verifies: Permits.withPermit wakes " +
      "the next queued task correctly.",
    status: "pending",
  },
  {
    id: "dl-conc-4",
    category: "Download — Concurrency",
    title: "Set concurrency to 2 → 2 downloads run simultaneously",
    description:
      "Open Settings → Downloads → set max concurrent downloads to 2. Enqueue " +
      "two episodes back-to-back. Verify BOTH are downloading at the same time " +
      "(both progress bars move). Verifies: DownloadQueue's permit pool size.",
    status: "pending",
  },

  /* ── Download — Retry & Recovery ─────────────────────────────────── */
  {
    id: "dl-retry-1",
    category: "Download — Retry & Recovery",
    title: "Network off mid-download → status shows RETRYING",
    description:
      "Start a download, then turn off Wi-Fi / mobile data mid-stream. " +
      "Verify the row shows RETRYING (amber) — NOT ERROR (red). Verifies: " +
      "RetryPolicy.shouldRetry returns true for IOException + the retry loop " +
      "(DC2-FIX-B / DC-FIX-6) is wired into DownloadQueue.launchDownload.",
    status: "pending",
  },
  {
    id: "dl-retry-2",
    category: "Download — Retry & Recovery",
    title: "Network back on → download retries + completes",
    description:
      "Continue from above — turn the network back on. Verify the download " +
      "auto-resumes (RETRYING → DOWNLOADING) and eventually completes. Verifies: " +
      "the retry backoff kicks in and the resume-from-Range header works.",
    status: "pending",
  },
  {
    id: "dl-retry-3",
    category: "Download — Retry & Recovery",
    title: "Proxy death (switch episodes mid-download) → re-resolve fires",
    description:
      "Start a download on one episode, then immediately tap Download on a " +
      "different episode of the same series (forces the source's resolver " +
      "proxy to die + rebind). Verify the first download re-resolves its " +
      "video URL via the HttpDownloader.ReResolver adapter (DC-FIX-4) instead " +
      "of going to ERROR.",
    status: "pending",
  },

  /* ── Download — UI Display ────────────────────────────────────────── */
  {
    id: "dl-ui-1",
    category: "Download — UI Display",
    title: "Server name pill (not extension name)",
    description:
      "Download an episode whose source provides a server name (e.g. pick " +
      "\"HD-1\"). Verify the download row shows a pill labelled \"HD-1\" — " +
      "NOT the extension's display name. Verifies: DC4-FIX parseServerName " +
      "no longer falls back to sourceName.",
    status: "pending",
  },
  {
    id: "dl-ui-2",
    category: "Download — UI Display",
    title: "Audio version pill (SUB / DUB / etc.)",
    description:
      "Pick an audio version (e.g. SUB). Verify the row shows a pill labelled " +
      "\"SUB\". Verifies: DC4-FIX parseAudioVersion returns the matched keyword " +
      "instead of always returning \"DEFAULT\".",
    status: "pending",
  },
  {
    id: "dl-ui-3",
    category: "Download — UI Display",
    title: "Quality pill (1080p / 720p / etc.)",
    description:
      "Pick a quality (e.g. 1080p). Verify the row shows a pill labelled " +
      "\"1080p\". Verifies: DC4-FIX extractQuality returns the parsed " +
      "resolution instead of falling back to the title.",
    status: "pending",
  },
  {
    id: "dl-ui-4",
    category: "Download — UI Display",
    title: "Long server names truncate with \"…\" (no line break)",
    description:
      "Find an episode whose server name is long (≥ 20 chars). Verify the " +
      "server pill is a single line — truncated with ellipsis if it overflows. " +
      "Verifies: InfoPill uses truncate + whitespace-nowrap.",
    status: "pending",
  },
  {
    id: "dl-ui-5",
    category: "Download — UI Display",
    title: "Progress bar spans full width (no gap below 3-dot button)",
    description:
      "Open DownloadsScreen while a download is active. Verify the progress " +
      "bar extends the full width of the row — there's no empty space below " +
      "the 3-dot menu button. Verifies: DC-FIX row layout fills the trailing " +
      "span correctly.",
    status: "pending",
  },
  {
    id: "dl-ui-6",
    category: "Download — UI Display",
    title: "DownloadedFilesScreen shows same pills as DownloadsScreen",
    description:
      "Open the DownloadedFilesScreen (Library → Downloaded files). Verify " +
      "each completed episode shows the SAME server / audio / quality pills " +
      "as the active DownloadsScreen did. Verifies: DC-FIX-3 ViewModel rewrap " +
      "passes the fields through consistently to both screens.",
    status: "pending",
  },

  /* ── Download — data.json ─────────────────────────────────────────── */
  {
    id: "dl-json-1",
    category: "Download — data.json",
    title: "Only ONE .data.json file per content folder",
    description:
      "After a download, use a file manager to inspect the SAF content " +
      "folder. Verify there is exactly ONE .data.json file — no duplicates " +
      "(no \"content.data.json\" + \".data.json\" pair). Verifies: " +
      "DownloadStorageProvider no longer races when creating the metadata file.",
    status: "pending",
  },
  {
    id: "dl-json-2",
    category: "Download — data.json",
    title: ".data.json has an episodes[] array with metadata",
    description:
      "Open the .data.json file in a text editor. Verify it contains an " +
      "\"episodes\" array where each entry has fileSize, videoServer, videoAudio, " +
      "and quality fields populated (not null/blank). Verifies: " +
      "DownloadStorageProvider.updateEpisodeInDataJson writes the resolved " +
      "fields (DC-FIX + DC4-FIX chain).",
    status: "pending",
  },

  /* ── Extensions — Filters ─────────────────────────────────────────── */
  {
    id: "ext-filters-1",
    category: "Extensions — Filters",
    title: "Languages filter sheet opens with language list",
    description:
      "Open the Extensions page → tap the Languages filter button. Verify a " +
      "bottom sheet opens showing a list of available languages. Verifies: " +
      "DC2-FIX-F multi-language select sheet renders correctly.",
    status: "pending",
  },
  {
    id: "ext-filters-2",
    category: "Extensions — Filters",
    title: "Select 2 languages → Available list filters",
    description:
      "In the Languages sheet, select 2 languages (e.g. English + Japanese). " +
      "Tap Apply. Verify the Available list now shows ONLY extensions whose " +
      "language matches one of the two. Verifies: the filter predicate " +
      "intersects the language set correctly.",
    status: "pending",
  },
  {
    id: "ext-filters-3",
    category: "Extensions — Filters",
    title: "NSFW toggle shows / hides NSFW extensions",
    description:
      "Tap the NSFW toggle in the Extensions header. Verify: ON → NSFW " +
      "extensions appear in the Available list; OFF → they disappear. Verifies: " +
      "the NSFW filter predicate runs against extension.isNsfw.",
    status: "pending",
  },
  {
    id: "ext-filters-4",
    category: "Extensions — Filters",
    title: "Installed + Untrusted NOT filtered (always visible)",
    description:
      "Set a Languages filter + NSFW=off. Verify the Installed and Untrusted " +
      "sections still show ALL installed / untrusted extensions regardless of " +
      "the filter. Verifies: filters apply ONLY to the Available list, never " +
      "to Installed or Untrusted (DC2-FIX-F spec).",
    status: "pending",
  },

  /* ── Extensions — Updates ─────────────────────────────────────────── */
  {
    id: "ext-updates-1",
    category: "Extensions — Updates",
    title: "Update button appears on rows with an available update",
    description:
      "If any installed extension has a new version available, verify its " +
      "row in the Installed list shows an \"Update\" button (or badge). " +
      "Verifies: the extension update-check loop populates updateVersion.",
    status: "pending",
    notes: "If no updates are available, mark this n/a.",
  },
  {
    id: "ext-updates-2",
    category: "Extensions — Updates",
    title: "Tap Update → spinner shows + install completes",
    description:
      "Tap the Update button. Verify: a spinner / loading state appears on " +
      "the button immediately, then disappears when the install finishes. " +
      "Verify the extension's version label updates to the new version. " +
      "Verifies: ExtensionInstaller.install runs to completion + the row " +
      "re-emits with the new version.",
    status: "pending",
    notes: "If no updates are available, mark this n/a.",
  },

  /* ── Extensions — Performance ──────────────────────────────────────── */
  {
    id: "ext-perf-1",
    category: "Extensions — Performance",
    title: "Scroll Available list (240+ extensions) — no lag",
    description:
      "Open Extensions → Available list. Scroll up and down through all " +
      "240+ extensions. Verify the scroll is smooth (no jank, no frame drops " +
      "visible to the eye). Verifies: the lazy column + async icon loading " +
      "(Coil) handles the list size without blocking the main thread.",
    status: "pending",
  },

  /* ── Details — Stale State ─────────────────────────────────────────── */
  {
    id: "details-stale-1",
    category: "Details — Stale State",
    title: "Open content B after closing A → no flash of A's data",
    description:
      "Open content A's Details screen → press back to close. Open content B's " +
      "Details screen. Verify the screen renders B's data immediately — there " +
      "is NO brief flash of A's title / cover / episodes. Verifies: " +
      "DC-FIX-5 (loadGeneration counter) discards stale async emissions from " +
      "the previous DetailsViewModel instance.",
    status: "pending",
  },

  /* ── Details — Source Linking ─────────────────────────────────────── */
  {
    id: "details-src-1",
    category: "Details — Source Linking",
    title: "Cold start with a saved source link → no \"No source linked\" race",
    description:
      "Open a content you've previously linked to a source. Force-stop the app " +
      "and reopen it. Tap the content's Details screen. Verify: there is NO " +
      "transient \"No source linked\" placeholder flashing before the actual " +
      "source link loads. Verifies: DC-FIX synchronous pre-read of " +
      "loadLinkedSource before the first composition.",
    status: "pending",
  },

  /* ── Nav — Backstack Persistence ───────────────────────────────────── */
  {
    id: "nav-back-1",
    category: "Nav — Backstack Persistence",
    title: "Browse → Details → force-stop → reopen lands on Details",
    description:
      "Navigate: Home → Browse → Details of any content. Force-stop the app " +
      "(system recents → swipe away, or `adb shell am force-stop`). Reopen the " +
      "app from the launcher. Verify you land back on the Details screen — " +
      "NOT on Home / Browse. Verifies: R7 / D-150 nav-backstack persistence " +
      "across process death (savedStateHandle / rememberSaveable).",
    status: "pending",
    notes: "This was a known limitation (R7 accepted). If still broken, mark FAIL.",
  },

  /* ── Overall — App Stability ───────────────────────────────────────── */
  {
    id: "stab-1",
    category: "Overall — App Stability",
    title: "App starts without crashing",
    description:
      "Cold-start the app. Verify it reaches the Home screen without crashing " +
      "to the ErrorActivity. Verifies: DC3-FIX-A2 (lazy resolver lambda) " +
      "permanently eliminated the StackOverflowError on startup.",
    status: "pending",
  },
  {
    id: "stab-2",
    category: "Overall — App Stability",
    title: "No ANR during downloads",
    description:
      "Start 2–3 downloads. While they're running, scroll the Browse screen, " +
      "open Details screens, navigate around. Verify: no \"App not responding\" " +
      "dialog appears. Verifies: SAF scans + DB writes are off the main thread " +
      "(DC-FIX-7 — main-thread runBlocking moved).",
    status: "pending",
  },
  {
    id: "stab-3",
    category: "Overall — App Stability",
    title: "No memory leaks during long download sessions",
    description:
      "Leave the app running with downloads + the DownloadsScreen visible for " +
      "5+ minutes. Verify: no progressive slowdown, no OOM crash, no GC thrash " +
      "in logcat. Verifies: StateFlow collectors + DownloadQueue listeners are " +
      "properly cancelled on screen dispose.",
    status: "pending",
  },
];

/* ---------------------------------------------------------------------------
 * Derived: items grouped by category (preserves CHECKLIST_CATEGORIES order)
 * ------------------------------------------------------------------------- */

export function groupByCategory(
  items: TestChecklistItem[],
): { category: string; items: TestChecklistItem[] }[] {
  return CHECKLIST_CATEGORIES.map((category) => ({
    category,
    items: items.filter((it) => it.category === category),
  })).filter((g) => g.items.length > 0);
}
