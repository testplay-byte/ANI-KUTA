/*
 * Updates + Notifications — Architecture Plan (D-193).
 *
 * Source: APP/ani-kuta/DOCUMENTATION/planning/updates-notifications/PLAN.md
 *         (519 lines, v3 — revised after 5 sub-agent reviews + 3 additional reviews + 8 user decisions)
 *
 * Status: DRAFT v3 — user decisions incorporated, ready for final review.
 *         Estimated ~34h across 10 phases.
 *
 * Hardcoded for the static dashboard demo — no API calls.
 */

/* ---------------------------------------------------------------------------
 * §0  Hero / status
 * ------------------------------------------------------------------------- */

export const UPDATES_HERO = {
  title: "Updates + Notifications — Architecture Plan (D-193)",
  subtitle:
    "A unified system that detects new episodes (sub + dub), surfaces them in the Updates feed, and fires notifications on three triggers — wired through interface contracts to avoid circular deps. All 8 user decisions have been incorporated.",
  status: "DRAFT v3 — User Decisions Incorporated, Ready for Final Review",
  statusColor: "var(--c-success)",
  branch: "feature/updates-notifications-plan",
  date: "2026-08-12",
  reviews:
    "v3 — 5 sub-agent reviews (architecture · smart-release · settings UI · DB schema · final consolidated) + 3 additional reviews + all 8 user decisions answered. Bug fixes applied: dub default ON, completed-anime disable logic, extension-only anime handling.",
  totalHours: 34,
} as const;

/* ---------------------------------------------------------------------------
 * §1  Vision
 * ------------------------------------------------------------------------- */

export const VISION_SUMMARY = {
  lede:
    "A unified Updates + Notifications system that detects new episodes (sub + dub), shows them in the Updates feed, and sends notifications based on user preferences. The two systems are interlinked: the Updates engine discovers new episodes → the Notifications engine decides whether + how to alert. They communicate via a defined contract (not direct calls).",
  principles: [
    {
      title: "Interlinked but modular",
      body:
        "Updates engine finds new episodes; Notifications engine decides whether + how to alert. They communicate via interfaces (not direct calls) to avoid circular deps.",
    },
    {
      title: "Future-proof",
      body:
        "Configurable intervals, per-category selection, sub/dub tracking, smart release detection.",
    },
    {
      title: "Honest about state",
      body:
        "\"new\" vs \"initial batch\" vs \"acknowledged\" are distinct states with clear retention rules.",
    },
  ],
} as const;

/* ---------------------------------------------------------------------------
 * §2  Architecture — Interlinked System
 *
 * The diagram is split into structured boxes so the page can render it as
 * styled HTML cards instead of a raw ASCII pre-block.
 * ------------------------------------------------------------------------- */

export interface ArchBoxItem {
  text: string;
  indent?: number; // 0 = top, 1 = nested, 2 = nested-nested
  branch?: string; // ├─ └─ style note
}

export interface ArchBox {
  id: string;
  label: string;
  subtitle?: string;
  colorVar: string;
  items: ArchBoxItem[];
}

export const ARCH_BOXES: ArchBox[] = [
  {
    id: "settings",
    label: "SETTINGS (UI)",
    subtitle: "Updates & Notifications — combined section",
    colorVar: "var(--c-primary)",
    items: [
      { text: "Master toggle (3-way: Auto / Manual / Off)" },
      { text: "Auto-update interval (6h / 12h / 24h / 2d / 3d / weekly)" },
      { text: "Manual: per-category checklist" },
      { text: "Notification defaults (triggers + audio)" },
      { text: "Notification library (per-anime config)" },
      { text: '"Send test notification" button' },
    ],
  },
  {
    id: "worker",
    label: "WorkManager (background)",
    subtitle: "UpdateCheckWorker — configurable interval",
    colorVar: "var(--c-secondary)",
    items: [
      {
        text: "scheduleRefresher.fetchSchedule() — refresh airing",
        indent: 1,
        branch: "interface — implemented by ScheduleEngine, bound in :app",
      },
      {
        text: "updateEngine.checkDueAnime() — find new episodes",
        indent: 1,
      },
      {
        text: "For each new episode found:",
        indent: 2,
      },
      { text: 'Insert episode_update (batch_type="new")', indent: 2, branch: "├─" },
      { text: "Update anime_update_state (sub + dub counts)", indent: 2, branch: "├─" },
      { text: 'NotificationManager?.postNotification("watchable")', indent: 2, branch: "└─" },
      {
        text: "SmartReleaseChecker — OneTimeWorkRequest chaining",
        indent: 1,
        branch: "for anime airing within ±1h",
      },
      {
        text: "Retention purge (episode_update + notification_sent)",
        indent: 1,
      },
    ],
  },
  {
    id: "feed",
    label: "UPDATES FEED (UI)",
    subtitle: "New / Earlier / Initial batch + live progress",
    colorVar: "var(--c-success)",
    items: [
      { text: '"New" section (unacknowledged, new_expires_at > now)' },
      { text: '"Earlier" section (acknowledged OR expired)' },
      { text: '"Initial batch" rows (text: "Episodes 1-N added")' },
      { text: "Live-progress banner during refresh (cover + X/Y)" },
      { text: "Sub/Dub badges per episode" },
    ],
  },
];

export const INTERFACE_PATTERN = `:core:updates defines:
  - interface ScheduleRefresher { suspend fun fetchSchedule() }
  - interface NotificationSender { suspend fun postNotification(...) }

:core:schedule implements ScheduleRefresher (bound in :app)
:core:notifications implements NotificationSender (bound in :app)
:app wires both into UpdateEngine + UpdateCheckWorker via Koin`;

/* ---------------------------------------------------------------------------
 * §3  Current State table
 * ------------------------------------------------------------------------- */

export type BuildStatus = "built" | "missing" | "buggy";

export interface CurrentStateRow {
  component: string;
  status: BuildStatus;
  there: string;
  missing: string;
}

export const CURRENT_STATE: CurrentStateRow[] = [
  {
    component: "UpdateEngine",
    status: "built",
    there: "checkDueAnime(), checkSingleAnime(), onEpisodesRefreshed(), ensureUpdateState()",
    missing:
      "Configurable interval, live-progress callback, sub/dub-aware checking, NotificationManager wiring",
  },
  {
    component: "UpdateCheckWorker",
    status: "built",
    there: "1h periodic, checkDueAnime() + retention purge",
    missing:
      "Configurable interval, ScheduleRefresher call, cleanupOldSent() call, SmartReleaseChecker",
  },
  {
    component: "episode_update table",
    status: "built",
    there: "batch_type + episode_count columns (D-192)",
    missing: "new_expires_at column, updated getUnacknowledgedUpdates query",
  },
  {
    component: "anime_update_state table",
    status: "built",
    there: "next_check_at, last_known_episode_count, status",
    missing: "last_known_dub_count, last_checked_dub_at, total_episodes",
  },
  {
    component: "NotificationManager",
    status: "built",
    there: "postNotification(), 2 channels, master toggle, per-anime config",
    missing: "setContentIntent, postTestNotification(), \"watchable\" trigger wiring",
  },
  {
    component: "ScheduleEngine",
    status: "built",
    there: "fetchSchedule(), ActualReleaseUpdater",
    missing: '"schedule" trigger (fire at airing time), ScheduleRefresher interface',
  },
  {
    component: "NotificationsSettingsScreen",
    status: "buggy",
    there: "3-way toggle UI, defaults, per-anime library",
    missing: "3-way toggle bug, no updates settings, UI needs redesign",
  },
  {
    component: "Updates screen",
    status: "built",
    there: "New + Earlier tabs, refresh button",
    missing: "No live-progress, no \"initial batch\" rendering, no sub/dub badges",
  },
  {
    component: "Smart release detection",
    status: "missing",
    there: "—",
    missing: "OneTimeWorkRequest chaining, 10-min polling, skip-after-3",
  },
  {
    component: "Combined settings section",
    status: "missing",
    there: "—",
    missing: "Updates + Notifications in one section",
  },
];

/* ---------------------------------------------------------------------------
 * §0  Known Architectural Decisions (12 items from the 5 review sessions)
 * ------------------------------------------------------------------------- */

export interface ArchDecision {
  num: number;
  issue: string;
  resolution: string;
}

export const ARCH_DECISIONS: ArchDecision[] = [
  {
    num: 1,
    issue: "Circular dep :core:updates ↔ :core:schedule",
    resolution:
      "Use interface pattern: define ScheduleRefresher interface in :core:updates, implement in :core:schedule, bind in :app. Same pattern as ActualReleaseUpdater.",
  },
  {
    num: 2,
    issue: "SmartReleaseChecker 10-min polling scheduling",
    resolution:
      'Use OneTimeWorkRequest chaining with setInitialDelay. Unique work name "smart_release_<mainId>_<epNum>". ExistingWorkPolicy.REPLACE. Retry counter in inputData.',
  },
  {
    num: 3,
    issue: "total_episodes missing from schema",
    resolution:
      "Add total_episodes INTEGER column to anime_update_state. Populate from AniListAnime.episodes (already queried).",
  },
  {
    num: 4,
    issue: "3 notification triggers not wired",
    resolution:
      "Add NotificationManager? to UpdateEngine constructor (nullable — tests can pass null). Fire on_watchable after upsertEpisodeUpdate (worker path only). on_schedule fired by ScheduleEngine when airing time reached. on_immediate already fires.",
  },
  {
    num: 5,
    issue: "checkSingleAnime is variant-blind",
    resolution:
      "Rewrite to partition episodes by audio variant, compute max-sub/max-dub separately, find new sub vs new dub independently.",
  },
  {
    num: 6,
    issue: "4 SQL queries need updating + 1 new query + 2 indexes",
    resolution: "All specified in §4 below.",
  },
  {
    num: 7,
    issue: '"Off" scope ambiguity',
    resolution:
      '"Off" disables UPDATES only (background checking). Notifications master toggle is separate.',
  },
  {
    num: 8,
    issue: "State migration masterEnabled → update_mode",
    resolution:
      'One-time migration: if notif_master_enabled was true → update_mode = "auto"; if false → update_mode = "off".',
  },
  {
    num: 9,
    issue: "POST_NOTIFICATIONS runtime permission",
    resolution:
      "Check on Android 13+ before posting. Already requested at first launch (welcome dialog).",
  },
  {
    num: 10,
    issue: "setContentIntent deep-link",
    resolution:
      "PendingIntent → MainActivity with extra navKey=AnimeDetailsKey.AniList(anilistId).",
  },
  {
    num: 11,
    issue: "Flow<CheckProgress> doesn't fit parallel engine",
    resolution:
      "Use SharedFlow<CheckProgress> emitted from within checkDueAnime's per-anime loop. Terminal value: CheckProgress(total, total, \"\", \"\", null) signals completion.",
  },
  {
    num: 12,
    issue: "Hour estimate underestimated",
    resolution: "Revised from ~24h to ~34h. See §12.",
  },
];

/* ---------------------------------------------------------------------------
 * §4  DB Schema Changes
 * ------------------------------------------------------------------------- */

export const SCHEMA_ANIME_UPDATE_STATE_SQL = `ALTER TABLE anime_update_state ADD COLUMN last_known_dub_count INTEGER;
ALTER TABLE anime_update_state ADD COLUMN last_checked_dub_at INTEGER;
ALTER TABLE anime_update_state ADD COLUMN total_episodes INTEGER;`;

export const SCHEMA_ANIME_UPDATE_STATE_NOTES = [
  "last_known_episode_count → tracks SUB episodes (existing, semantic unchanged for backward compat).",
  "last_known_dub_count → tracks DUB episodes (null = no dub tracking yet).",
  "last_checked_dub_at → separate dub-check timestamp.",
  "total_episodes → from AniList episodes field. Used for completed-anime handling (§7c).",
];

export const SCHEMA_NEW_QUERY_DUB = `-- For dub checking on FINISHED anime (§7c)
getDueDubAnime:
SELECT * FROM anime_update_state
WHERE auto_update_enabled = 1
  AND status = 'FINISHED'
  AND last_known_dub_count < total_episodes
ORDER BY next_check_at ASC;`;

export const SCHEMA_NEW_INDEX_DUB = `CREATE INDEX IF NOT EXISTS idx_anime_update_due_dub ON anime_update_state(next_check_at)
WHERE auto_update_enabled = 1 AND status = 'FINISHED' AND last_known_dub_count < total_episodes;`;

export const SCHEMA_EPISODE_UPDATE_SQL = `ALTER TABLE episode_update ADD COLUMN new_expires_at INTEGER;`;

export const SCHEMA_EPISODE_UPDATE_NOTES = [
  'Set to discovered_at + 3 days (259200000 ms) for batch_type="new" rows.',
  'NULL for batch_type="initial" rows (initial batches are never "new").',
];

export const SCHEMA_INDEX_EPISODE_UPDATE = `DROP INDEX IF EXISTS idx_episode_update_unack;
CREATE INDEX IF NOT EXISTS idx_episode_update_unack
  ON episode_update(acknowledged, new_expires_at, discovered_at DESC);`;

export const SCHEMA_QUERY_UPDATES = [
  {
    query: "upsertAnimeUpdateState",
    change: "Add 3 new columns (last_known_dub_count, last_checked_dub_at, total_episodes).",
  },
  {
    query: "updateCheckResult",
    change: "Add last_known_dub_count + last_checked_dub_at params.",
  },
  {
    query: "upsertEpisodeUpdate",
    change: "Add new_expires_at param.",
  },
  {
    query: "getUnacknowledgedUpdates",
    change: "Add `AND (new_expires_at IS NULL OR new_expires_at > :now)` filter.",
  },
];

export const SCHEMA_MIGRATION_NOTE =
  "All ALTER TABLE commands go in DatabaseDriverFactory.migrateSchemaIfNeeded() with hasColumn guards (the established pattern). Idempotent.";

/* ---------------------------------------------------------------------------
 * §5  Settings UI
 * ------------------------------------------------------------------------- */

export interface SettingsTreeNode {
  label: string;
  note?: string;
  highlight?: boolean;
  children?: SettingsTreeNode[];
}

export const SETTINGS_TREE: SettingsTreeNode[] = [
  { label: "Appearance" },
  { label: "Extensions" },
  {
    label: "Updates & Notifications",
    note: "NEW combined section",
    highlight: true,
    children: [
      { label: "General", note: "master toggle + interval + test notification" },
      { label: "New Anime Defaults", note: "trigger + audio defaults (fixed 3-way toggle)" },
      { label: "Library", note: "per-anime notification config" },
      { label: "Update Categories", note: "per-category checklist (manual mode)" },
    ],
  },
  { label: "Player" },
  { label: "Debug" },
];

export const SETTINGS_GENERAL_ITEMS = [
  {
    title: "Updates master toggle (3-way)",
    body: '"Auto updates" / "Manual updates" / "Off". Auto = background checking at the configured interval for ALL library anime. Manual = checking at the interval for SELECTED categories only. Off = no background checking — user must manually refresh. (Notifications master toggle is SEPARATE — "Off" here doesn\'t disable notifications. Manual refresh ALWAYS works, even when updates are OFF.)',
  },
  {
    title: "Interval selector",
    body: "Shown when Auto or Manual. Options: 6 hours / 12 hours / 24 hours / 2 days / 3 days / Weekly. Default = 24h. Read from PreferenceStore (update_interval_hours); re-enqueued with ExistingPeriodicWorkPolicy.REPLACE when the preference changes.",
  },
  {
    title: "Sub checking toggle",
    body: '"Check for new sub episodes" (default ON). When ON, the worker checks for new sub episodes during background updates.',
  },
  {
    title: "Dub checking toggle",
    body: '"Check for new dub episodes" (default ON — per user decision §13.8). The user explicitly chose ON for the default. When ON, the worker checks for new dub episodes during background updates.',
  },
  {
    title: "Dub-on-completed-anime toggle",
    body: '"Check for dub episodes on completed anime" (default ON). Continue checking for dub episodes even after the anime is FINISHED — but only if at least 1 dub episode has already been released (last_known_dub_count >= 1). If 0 dubs have been found, don\'t keep polling — the anime likely has no dub.',
  },
  {
    title: "Notifications master toggle",
    body: '"Enable notifications" — Switch, default ON. Separate from the updates master toggle. If OFF, no notifications are posted (but updates still appear in the Updates feed).',
  },
  {
    title: '"Check now" button',
    body: "Triggers an immediate manual refresh with live-progress UI. Available even when updates are OFF — manual refresh always works.",
  },
  {
    title: '"Send test notification" button',
    body: 'Posts a demo notification: "Demon Slayer — Episode 6 DUB" (uses the user\'s default audio pref). Checks POST_NOTIFICATIONS permission on Android 13+. Gated by notifications master toggle (disabled if notifications are off). Dedicated notification ID (999) for cancellation. Bypasses per-anime config (it\'s a test).',
  },
];

export const TOGGLE_FIX_ROOT_CAUSE =
  'TriggerState enum order is OFF(0), ON(1), SILENT(2) but the UI list is listOf(ON, SILENT, OFF). Using state.ordinal as selectedIndex mismatches.';

export const TOGGLE_FIX_SOLUTION =
  "Replace state.ordinal with TRIGGERS.indexOf(state) at all 8 call sites — NotificationsSettingsScreen.kt lines 171, 184, 197, 210 + NotificationsLibraryScreen.kt lines 341, 352, 363, 374. (AudioPref.ordinal is NOT buggy — enum order SUB/DUB/BOTH already matches — but we'll change it to AUDIO.indexOf(audioPref) for consistency.)";

export const STATE_MIGRATION_NOTES = [
  'If notif_master_enabled was true → update_mode = "auto"',
  'If notif_master_enabled was false → update_mode = "off"',
  "The notif_master_enabled key is kept (notifications master toggle still uses it).",
];

/* ---------------------------------------------------------------------------
 * §6  Auto-Update System
 * ------------------------------------------------------------------------- */

export const AUTO_UPDATE_INTERVAL_NOTES = [
  "Current: hard-coded 1h.",
  "New: read from PreferenceStore (update_interval_hours). Re-enqueue with ExistingPeriodicWorkPolicy.REPLACE when the preference changes.",
  "Intervals: 6h / 12h / 24h / 48h / 72h / 168h (weekly).",
  'When update_mode = "off": WorkManager.cancelUniqueWork("anikuta_update_check").',
];

export const WORKER_FLOW_CODE = `class UpdateCheckWorker {
    doWork():
        1. scheduleRefresher.fetchSchedule()        // interface — refreshes airing data
        2. val progress = updateEngine.checkDueAnime()  // returns Flow<CheckProgress>
           └─ For each new episode:
              ├─ Insert episode_update (batch_type="new", new_expires_at = now + 3d)
              ├─ Update anime_update_state (sub_count OR dub_count)
              └─ notificationSender?.postNotification("watchable")
        3. smartReleaseScheduler.scheduleImminentChecks()  // OneTimeWorkRequest per anime
        4. notificationConfigStore.cleanupOldSent(now - 90 days)
        5. updateStore.deleteOldAcknowledged(now - 7 days)
}`;

export const MANUAL_MODE_NOTES = [
  'When update_mode = "manual": the worker only checks anime in the selected categories.',
  "UpdateEngine.checkDueAnime(filterMainIds: Set<String>?) — if non-null, only checks those.",
  "The filter is built from selected categories → ContentRepository.getMainIdsByCategory(categoryId).",
];

export const CHECK_PROGRESS_CODE = `data class CheckProgress(
    val current: Int,
    val total: Int,
    val mainId: String,
    val title: String,
    val coverUrl: String?
)`;

export const LIVE_PROGRESS_NOTES = [
  "Emitted before each anime check. Terminal: CheckProgress(total, total, \"\", \"\", null).",
  "UpdatesViewModel collects + exposes as StateFlow<CheckProgress?>.",
  "UpdatesScreen renders a banner card when non-null.",
];

/* ---------------------------------------------------------------------------
 * §7  Smart Release Detection
 * ------------------------------------------------------------------------- */

export interface SmartReleaseStep {
  num: number;
  label: string;
  detail: string;
  outcome: "found" | "retry" | "skip";
}

export const SMART_RELEASE_CHAIN: SmartReleaseStep[] = [
  {
    num: 1,
    label: "next_airing_at + 10 min",
    detail:
      'Schedule a OneTimeWorkRequest<SmartReleaseCheckWorker> with setInitialDelay(10, MINUTES). Unique work name: "smart_release_${mainId}_${episodeNumber}". ExistingWorkPolicy.REPLACE. inputData: mainId, episodeNumber, attempt=1.',
    outcome: "retry",
  },
  {
    num: 2,
    label: "Worker fires → fetch extension episode list",
    detail: "Check if the expected episode exists on the extension.",
    outcome: "retry",
  },
  {
    num: 3,
    label: "+10 min → check",
    detail:
      "If found → mark actual_at = now, insert episode_update, fire \"watchable\" notification. Done.",
    outcome: "found",
  },
  {
    num: 4,
    label: "+20 min → check",
    detail:
      "If NOT found + attempt < 3 → schedule another OneTimeWorkRequest with setInitialDelay(10, MINUTES) + attempt+1.",
    outcome: "retry",
  },
  {
    num: 5,
    label: "+30 min → check",
    detail: "Last attempt. If still not found → skip. Don't check again until the next manual refresh.",
    outcome: "skip",
  },
];

export const SMART_RELEASE_NOTES = [
  {
    title: "Process death safety",
    body: "WorkManager survives process death. The inputData carries the attempt counter + mainId + episodeNumber, so the chain resumes correctly after reboot.",
  },
  {
    title: "Battery",
    body: "Limited to anime airing within ±1h of the current time. Max 5 concurrent checks (Semaphore in the worker).",
  },
  {
    title: "Retroactive update",
    body: "When the user manually refreshes an anime's details page + a new episode is found that was previously skipped → update last_known_episode_count, insert episode_update (discovered_at = now), fire \"watchable\" notification if enabled. Already handled by onEpisodesRefreshed (D-192 Phase 3).",
  },
  {
    title: "Completed anime — sub checking",
    body: "If status = \"FINISHED\" AND total_episodes IS NOT NULL: sub checking stops at last_known_episode_count >= total_episodes. This is the natural end of sub tracking.",
  },
  {
    title: "Completed anime — dub checking (≥1 dub released rule)",
    body: "Dub checking continues after sub is complete IF: (a) update_check_dub = true (default ON), AND (b) the anime has at least 1 dub episode released (last_known_dub_count >= 1) OR no dub check has been performed yet (last_known_dub_count IS NULL), AND (c) last_known_dub_count < total_episodes. The ≥1 rule is critical: if last_known_dub_count = 0 (we already checked + found no dubs), STOP — the anime likely has no dub. Don't waste battery polling a dub that doesn't exist.",
  },
  {
    title: "Completed anime — disable logic (don't disable on partial)",
    body: "auto_update_enabled = 0 is set ONLY when BOTH sub + dub are complete (or dub checking is OFF AND sub is complete). DO NOT disable if only one of sub/dub is complete. Example: sub has reached total_episodes but dub hasn't → keep checking for dub, auto_update_enabled stays 1. This handles anime where the dub is still being released months after the sub finished.",
  },
];

/* ---------------------------------------------------------------------------
 * §8  Sub/Dub Tracking
 * ------------------------------------------------------------------------- */

export const SUBDUB_DETECTION_NOTES = [
  "UpdateEngine.parseAudioVariant(scanlator, episodeName) already exists — returns \"sub\" / \"dub\" / \"unknown\".",
  "When a new episode is found, the audio_variant is stored in episode_update.audio_variant.",
];

export const CHECK_SINGLE_ANIME_CODE = `private suspend fun checkSingleAnime(state: AnimeUpdateState, now: Long): Int {
    val episodes = source.getEpisodeList(sAnime)  // fresh fetch
    val partitioned = episodes.groupBy { parseAudioVariant(it.scanlator, it.name) }

    val subEpisodes = partitioned["sub"] ?: emptyList()
    val dubEpisodes = partitioned["dub"] ?: emptyList()
    val unknownEpisodes = partitioned["unknown"] ?: emptyList()

    val maxSub = (subEpisodes + unknownEpisodes).maxOfOrNull { it.episode_number.toInt() } ?: 0
    val maxDub = dubEpisodes.maxOfOrNull { it.episode_number.toInt() } ?: 0

    val lastKnownSub = state.lastKnownEpisodeCount ?: 0
    val lastKnownDub = state.lastKnownDubCount ?: 0

    var newCount = 0

    // New sub episodes
    for (ep in (subEpisodes + unknownEpisodes)) {
        val epNum = ep.episode_number.toInt()
        if (epNum > lastKnownSub) {
            insertEpisodeUpdate(mainId, ep, "sub", now)
            newCount++
        }
    }

    // New dub episodes
    for (ep in dubEpisodes) {
        val epNum = ep.episode_number.toInt()
        if (epNum > lastKnownDub) {
            insertEpisodeUpdate(mainId, ep, "dub", now)
            newCount++
        }
    }

    // Update state with both counts
    updateStore.updateCheckResult(mainId, now, nextCheckAt, maxSub, maxDub, ...)
    return newCount
}`;

export const NOTIFICATION_AUDIO_FILTER = [
  "NotificationManager.postNotification() already checks notify_sub / notify_dub.",
  "If new episode is sub + notify_sub = false → don't notify (but still insert episode_update).",
  "If new episode is dub + notify_dub = false → don't notify (but still insert episode_update).",
];

/* ---------------------------------------------------------------------------
 * §9  Notification System
 * ------------------------------------------------------------------------- */

export interface TriggerRow {
  trigger: string;
  when: string;
  whoFires: string;
  wiring: string;
}

export const NOTIFICATION_TRIGGERS: TriggerRow[] = [
  {
    trigger: "on_schedule",
    when: "At the AniList airing time — a REMINDER that the episode should be available now. Fires based on the AniList airing date, even if the extension hasn't uploaded the episode yet. It's a \"heads up\" notification.",
    whoFires: "ScheduleEngine",
    wiring:
      'ScheduleEngine checks airingAt <= now → fires postNotification(triggerType = "schedule"). Text: "Episode N should be available now — check your sources."',
  },
  {
    trigger: "on_watchable",
    when: "When the Updates engine ACTUALLY finds the episode on the extension (the episode is confirmed available). This is the \"it's ready to watch\" notification.",
    whoFires: "UpdateEngine",
    wiring:
      'After upsertEpisodeUpdate in checkSingleAnime → notificationSender?.postNotification(triggerType = "watchable"). Text: "Episode N is now available to watch."',
  },
  {
    trigger: "on_immediate",
    when: "For past-due episodes (airingAt < now, not yet checked) — fires when the schedule engine runs + finds episodes that aired in the past but weren't checked yet.",
    whoFires: "ScheduleEngine",
    wiring: 'Already fires. Text: "Episode N has been released."',
  },
];

/* ---------------------------------------------------------------------------
 * §9a  on_schedule vs on_watchable — comparison (user requested clarification).
 * ------------------------------------------------------------------------- */

export interface TriggerComparison {
  axis: string;
  onSchedule: string;
  onWatchable: string;
}

export const TRIGGER_COMPARISON_INTRO =
  "The user asked for a clear explanation of the difference between on_schedule and on_watchable. Both fire on the same episode, but at different times + for different reasons. Here is the side-by-side:";

export const TRIGGER_COMPARISON: TriggerComparison[] = [
  {
    axis: "Source of truth",
    onSchedule: "AniList airingAt timestamp (when it airs in Japan)",
    onWatchable: "Extension episode list (we confirmed it's actually uploaded)",
  },
  {
    axis: "When it fires",
    onSchedule: "At the exact airingAt time (set by AniList)",
    onWatchable:
      "When the Updates engine confirms the episode is on the extension — could be minutes, hours, or days later",
  },
  {
    axis: "What it means",
    onSchedule: "\"Heads up — this episode should be available now, go check.\"",
    onWatchable: "\"Confirmed — the episode is ready to watch right now.\"",
  },
  {
    axis: "Message text",
    onSchedule: "\"Episode N should be available now — check your sources.\"",
    onWatchable: "\"Episode N is now available to watch.\"",
  },
  {
    axis: "Who fires it",
    onSchedule: "ScheduleEngine",
    onWatchable: "UpdateEngine (after upsertEpisodeUpdate)",
  },
  {
    axis: "Why both exist",
    onSchedule:
      "Some extensions upload episodes hours or days after the airing time. on_schedule says \"check now\" so the user knows it's expected.",
    onWatchable:
      "Confirms the episode is actually downloadable — no false positives. This is the reliable signal.",
  },
];

export const TRIGGER_TIMELINE_NOTE =
  "Typical timeline: AniList says airingAt = Tuesday 16:00 JST. on_schedule fires at Tuesday 16:00 JST (\"should be available\"). The extension uploads the episode at Tuesday 18:30 JST. The next worker run (within the configured interval) detects it + on_watchable fires (\"is now available\"). The user gets 2 notifications on the same episode — first a reminder, then a confirmation.";

export const NOTIFICATION_CONTENT = [
  { label: "Title", value: '"New episode available"' },
  { label: "Text", value: '"<Anime title> — Episode <N> <SUB/DUB>"' },
  {
    label: "Tap action",
    value:
      "Deep-link to the details page by default (Phase 7). PendingIntent → MainActivity with extra navKey=AnimeDetailsKey.AniList(anilistId). FUTURE enhancement: a setting to choose whether tapping the notification opens the details page OR directly opens the watch page (using the user's auto-select-video settings).",
  },
  {
    label: "Extension-only",
    value:
      "If the content has no AniList ID, the deep-link uses AnimeDetailsKey.Extension(sourceId, animeUrl) instead. The episode_update row stores source_id + anime_url for this fallback.",
  },
  { label: "Channel", value: "Default (sound) if trigger=ON, silent (no sound) if trigger=SILENT" },
  { label: "Permission", value: "POST_NOTIFICATIONS checked on Android 13+ before posting" },
  {
    label: "Future action buttons",
    value: 'Action buttons on the notification itself: "Watch" (deep-link to player with auto-select-video) / "Dismiss".',
  },
];

/* ---------------------------------------------------------------------------
 * §9b.1  Extension-only anime handling (no AniList ID).
 * ------------------------------------------------------------------------- */

export const EXTENSION_ONLY_NOTES = [
  "Some anime have no AniList ID — extension-only content the user added directly from a source.",
  "total_episodes + status are not available from AniList → use the extension's current episode count as total_episodes + assume status = 'RELEASING'.",
  "Smart-release detection is NOT possible (no next_airing_at) → these anime are checked only on the regular WorkManager interval.",
  "Notification tap deep-links to AnimeDetailsKey.Extension(sourceId, animeUrl) instead of AnimeDetailsKey.AniList(anilistId).",
  "The episode_update row stores source_id + anime_url for the fallback navigation.",
  "All other behaviour (sub/dub partitioning, dedup, retention, audio filtering) is identical to AniList-tracked anime.",
];

export const TEST_NOTIFICATION_NOTES = [
  "NotificationManager.postTestNotification() — new method.",
  'Posts: "Demon Slayer — Episode 6 DUB" (hardcoded demo, uses default audio pref).',
  "Dedicated notification ID (999) for cancellation.",
  "Bypasses per-anime config (it's a test).",
  "Checks POST_NOTIFICATIONS permission first.",
];

export const DEDUP_RETENTION_NOTES = [
  "notification_sent table deduplicates: if (main_id, episode_number, audio_variant, trigger_type) already exists → don't re-post.",
  "Retention: cleanupOldSent(now - 90 days) called by the worker.",
  'episode_update "new" status: expires after 3 days (new_expires_at). Row stays in DB for "Earlier" section but is no longer "New".',
];

/* ---------------------------------------------------------------------------
 * §12  Implementation Phases
 * ------------------------------------------------------------------------- */

export interface PhaseRow {
  phase: number;
  task: string;
  hours: number;
}

export const IMPLEMENTATION_PHASES: PhaseRow[] = [
  {
    phase: 1,
    task: "Bug fixes: 3-way toggle + no-source-from-library + onEpisodesRefreshed ordering",
    hours: 2,
  },
  {
    phase: 2,
    task: "DB schema: 5 new columns + 4 query updates + 1 new query + 2 indexes + migration",
    hours: 2,
  },
  {
    phase: 3,
    task: "Settings UI: combined section + master toggle + interval + per-category + test notification",
    hours: 5,
  },
  {
    phase: 4,
    task: "Auto-update: configurable WorkManager + manual mode + per-category filter + live-progress",
    hours: 4,
  },
  {
    phase: 5,
    task: "Smart release detection: OneTimeWorkRequest chaining + 10-min polling + completed-anime",
    hours: 7,
  },
  {
    phase: 6,
    task: "Sub/Dub tracking: checkSingleAnime rewrite + separate counts + notification filtering",
    hours: 3,
  },
  {
    phase: 7,
    task: "Notification system: wire 3 triggers + tap action + test notification + dedup/retention",
    hours: 4,
  },
  {
    phase: 8,
    task: "Updates feed UI: live-progress banner + initial-batch rendering + acknowledgment",
    hours: 3,
  },
  {
    phase: 9,
    task: "Interface pattern: ScheduleRefresher + NotificationSender (avoid circular deps)",
    hours: 2,
  },
  {
    phase: 10,
    task: "Docs + dashboard + notify",
    hours: 2,
  },
];

export const IMPLEMENTATION_TOTAL_ESTIMATE = 34; // sum of all phase hours

/* ---------------------------------------------------------------------------
 * §13  User Decisions (all 8 questions answered)
 *
 * v3 — the 8 open questions have been answered by the user. They are now
 * DECISIONS, not questions. Each shows the user's answer + a ✅ badge.
 * ------------------------------------------------------------------------- */

export interface UserDecision {
  num: number;
  question: string;
  decision: string;
}

export const USER_DECISIONS: UserDecision[] = [
  {
    num: 1,
    question: '"on_schedule" notification text',
    decision:
      'The user asked for clarification → I explained the difference between on_schedule (time-based reminder) and on_watchable (availability-based confirmation). The user now understands. Text: on_schedule = "Episode N should be available now — check your sources"; on_watchable = "Episode N is now available to watch".',
  },
  {
    num: 2,
    question: "10-min polling battery impact",
    decision:
      "✅ Limit to anime airing within ±1h window + max 5 concurrent checks. This bounds the worst-case battery drain.",
  },
  {
    num: 3,
    question: "Completed anime + dub checking",
    decision:
      "✅ Trust AniList status + episodes. Completed anime STILL get dub checking if: (a) dub checking is ON (default ON), AND (b) the anime has at least 1 dub episode released, AND (c) last_known_dub_count < total_episodes. User gets a toggle: \"Check for dub episodes on completed anime\" (default ON).",
  },
  {
    num: 4,
    question: 'Manual refresh when updates are OFF',
    decision:
      '✅ Yes — update_mode = "off" only disables background checking. Manual refresh works on both the details page and the updates page.',
  },
  {
    num: 5,
    question: "Per-category manual mode",
    decision:
      "✅ Yes — user can configure per-category. Each category has a toggle: \"Check for updates for this category\". Only anime in selected (toggled-on) categories are checked.",
  },
  {
    num: 6,
    question: "Notification tap action",
    decision:
      "✅ Deep-link to details page (Phase 7). Future: option to open the watch page directly with auto-select-video (user wants this later, not Phase 7).",
  },
  {
    num: 7,
    question: "Merge Updates + Notifications nav rows",
    decision:
      "✅ Yes — merge into one \"Updates & Notifications\" section with sub-screens (General, New Anime Defaults, Library, Update Categories).",
  },
  {
    num: 8,
    question: "Dub checking default — ON or OFF?",
    decision:
      "✅ ON. The user explicitly changed the default from my recommendation of OFF to ON. Dub checking is ON by default — the user can toggle it off in settings.",
  },
];

export const USER_DECISIONS_ADDITIONAL = [
  {
    title: "Dub on completed anime — the ≥1 dub released rule",
    body: "If a completed anime has at least 1 dub episode released (last_known_dub_count >= 1), continue checking for more dub episodes. Only stop when last_known_dub_count >= total_episodes or the user disables dub checking. If last_known_dub_count = 0 (we already checked + found no dubs), STOP — the anime likely has no dub.",
  },
  {
    title: "Don't disable on partial completion",
    body: "auto_update_enabled = 0 is set ONLY when BOTH sub + dub are complete (or dub checking is OFF AND sub is complete). DO NOT disable if only one of sub/dub is complete — handles anime where the dub is still being released months after the sub finished.",
  },
  {
    title: "Notification deep-link future option",
    body: "The user wants a future option where tapping the notification can open the watch page directly (with auto-select-video) instead of the details page. This is a future enhancement, not Phase 7.",
  },
  {
    title: "Settings UI — explain every setting",
    body: "The user explicitly asked for the dashboard web page to explain EVERY setting that will be in the app + the logic behind each. See §15 below for the full detailed settings list.",
  },
];

/* ---------------------------------------------------------------------------
 * §15  Settings That Will Be in the App (detailed list for user review).
 *
 * v3 — the user asked for every setting to be explained. These 4 tables
 * cover every setting that will ship in the Updates & Notifications section.
 * ------------------------------------------------------------------------- */

export interface SettingsTableRow {
  setting: string;
  type: string;
  default: string;
  description: string;
}

export interface SettingsTableSection {
  id: string;
  path: string; // e.g. "Settings → Updates & Notifications → General"
  heading: string;
  rows: SettingsTableRow[];
}

export const SETTINGS_TABLES: SettingsTableSection[] = [
  {
    id: "settings-general",
    path: "Settings → Updates & Notifications → General",
    heading: "General",
    rows: [
      {
        setting: "Updates master toggle",
        type: "3-way (Auto / Manual / Off)",
        default: "Auto",
        description:
          "Controls background update checking. Auto = all library anime. Manual = selected categories only. Off = no background checking (manual refresh still works).",
      },
      {
        setting: "Update interval",
        type: "Selector (6h / 12h / 24h / 2d / 3d / weekly)",
        default: "24h",
        description: "How often the background worker checks for new episodes.",
      },
      {
        setting: "Check for sub episodes",
        type: "Toggle",
        default: "ON",
        description: "Check for new sub episodes during background updates.",
      },
      {
        setting: "Check for dub episodes",
        type: "Toggle",
        default: "ON",
        description:
          "Check for new dub episodes during background updates. (Per user decision §13.8 — default is ON.)",
      },
      {
        setting: "Check dub on completed anime",
        type: "Toggle",
        default: "ON",
        description:
          "Continue checking for dub episodes even after the anime is completed (if dub episodes are still being released).",
      },
      {
        setting: "Enable notifications",
        type: "Toggle",
        default: "ON",
        description:
          "Master toggle for the notification system. If OFF, no notifications are posted (but updates still appear in the Updates feed).",
      },
      {
        setting: "Check now",
        type: "Button",
        default: "—",
        description:
          "Triggers an immediate manual refresh with live-progress UI. Available even when updates are OFF (manual refresh always works).",
      },
      {
        setting: "Send test notification",
        type: "Button",
        default: "—",
        description:
          "Posts a demo notification to verify the notification setup works. Disabled when notifications master toggle is OFF. Checks POST_NOTIFICATIONS permission on Android 13+.",
      },
    ],
  },
  {
    id: "settings-defaults",
    path: "Settings → Updates & Notifications → New Anime Defaults",
    heading: "New Anime Defaults",
    rows: [
      {
        setting: "On schedule (trigger)",
        type: "3-way (On / Silent / Off)",
        default: "Off",
        description:
          "Fire a notification at the AniList airing time (reminder — \"should be available now\").",
      },
      {
        setting: "On watchable (trigger)",
        type: "3-way (On / Silent / Off)",
        default: "On",
        description:
          "Fire a notification when the episode is confirmed available on the extension (\"is now available to watch\").",
      },
      {
        setting: "On immediate (trigger)",
        type: "3-way (On / Silent / Off)",
        default: "Off",
        description: "Fire a notification for past-due episodes that weren't checked yet.",
      },
      {
        setting: "Audio preference",
        type: "3-way (Sub / Dub / Both)",
        default: "Both",
        description: "Which audio variants to notify for.",
      },
    ],
  },
  {
    id: "settings-library",
    path: "Settings → Updates & Notifications → Library",
    heading: "Library",
    rows: [
      {
        setting: "Per-anime list",
        type: "List",
        default: "—",
        description:
          "Each library anime has its own notification config (overrides the defaults). Tapping an anime opens a detail sheet with the same 3-way triggers + audio pref.",
      },
    ],
  },
  {
    id: "settings-categories",
    path: "Settings → Updates & Notifications → Update Categories",
    heading: "Update Categories",
    rows: [
      {
        setting: "Per-category checklist",
        type: "List of toggles",
        default: "All ON (when Auto) / None (when Manual)",
        description:
          "Each library category has a toggle: \"Check for updates for this category\". Only shown when Updates master toggle = Manual. Stored in PreferenceStore as a StringSet key update_selected_categories.",
      },
    ],
  },
];

export const SETTINGS_TABLES_INTRO =
  "Every setting that will ship in the Updates & Notifications section, with its type, default, and a description of what it does. The user explicitly asked for this — see §13 additional clarification #4.";

/* ---------------------------------------------------------------------------
 * §14  Future-Proofing
 * ------------------------------------------------------------------------- */

export const FUTURE_PROOFING = [
  {
    title: "Multi-source",
    body: "The ContentId + ContentIdType system (D-190) means the Updates engine can work with any content type.",
  },
  {
    title: "Multi-content-type",
    body: 'content.content_type (anime/manga/novel) is already present. Updates engine checks content_type = "anime" for now; manga/novel updates are a future phase.',
  },
  {
    title: "Configurable intervals",
    body: "update_interval_hours is a simple integer — adding new intervals is one UI entry.",
  },
  {
    title: "Per-anime override",
    body: "anime_update_state.auto_update_enabled allows disabling updates for a specific anime.",
  },
  {
    title: "Backup/restore",
    body: "All preferences mirrored to app_settings table (D-192 Phase 1).",
  },
  {
    title: "Notification watch-page deep-link",
    body: "Future option to open the watch page directly from the notification (with auto-select-video) instead of the details page. The user explicitly wants this as a future enhancement — see §13 additional clarification #3.",
  },
  {
    title: "Notification action buttons",
    body: 'Future "Watch" / "Dismiss" buttons on the notification itself. "Watch" would deep-link directly to the player with auto-select-video.',
  },
];

/* ---------------------------------------------------------------------------
 * Nav footer (prev / next)
 * ------------------------------------------------------------------------- */

export const UPDATES_PLAN_NAV_FOOTER = {
  prev: { label: "← Planning", href: "/planning/" },
  next: { label: "Dashboard →", href: "/" },
} as const;
