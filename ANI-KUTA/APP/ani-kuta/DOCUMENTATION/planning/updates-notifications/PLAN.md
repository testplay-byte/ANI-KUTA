# Updates + Notifications — Architecture Plan (D-193)

> **Status**: DRAFT v2 — revised after 5 sub-agent reviews. Awaiting user approval.
> **Branch**: `feature/updates-notifications-plan`
> **Date**: 2026-08-12
> **Reviews**: 5 sub-agent reviews completed (architecture, smart-release, settings UI, DB schema, final consolidated). All blocking issues addressed in v2.

---

## 0. Known Architectural Decisions (from review sessions)

These were identified as blocking issues in the 5 review sessions + are now resolved in this plan:

| # | Issue | Resolution |
|---|-------|-----------|
| 1 | Circular dep `:core:updates` ↔ `:core:schedule` | Use interface pattern: define `ScheduleRefresher` interface in `:core:updates`, implement in `:core:schedule`, bind in `:app`. Same pattern as `ActualReleaseUpdater`. |
| 2 | SmartReleaseChecker 10-min polling scheduling | Use `OneTimeWorkRequest` chaining with `setInitialDelay`. Unique work name `"smart_release_<mainId>_<epNum>"`. `ExistingWorkPolicy.REPLACE`. Retry counter in `inputData`. |
| 3 | `total_episodes` missing from schema | Add `total_episodes INTEGER` column to `anime_update_state`. Populate from `AniListAnime.episodes` (already queried). |
| 4 | 3 notification triggers not wired | Add `NotificationManager?` to `UpdateEngine` constructor (nullable — tests can pass null). Fire `on_watchable` after `upsertEpisodeUpdate` (worker path only). `on_schedule` fired by ScheduleEngine when airing time reached. `on_immediate` already fires. |
| 5 | `checkSingleAnime` is variant-blind | Rewrite to partition episodes by audio variant, compute max-sub/max-dub separately, find new sub vs new dub independently. |
| 6 | 4 SQL queries need updating + 1 new query + 2 indexes | All specified in §4 below. |
| 7 | "Off" scope ambiguity | "Off" disables UPDATES only (background checking). Notifications master toggle is separate. |
| 8 | State migration `masterEnabled` → `update_mode` | One-time migration: if `notif_master_enabled` was true → `update_mode = "auto"`; if false → `update_mode = "off"`. |
| 9 | `POST_NOTIFICATIONS` runtime permission | Check on Android 13+ before posting. Already requested at first launch (welcome dialog). |
| 10 | `setContentIntent` deep-link | PendingIntent → `MainActivity` with extra `navKey=AnimeDetailsKey.AniList(anilistId)`. |
| 11 | `Flow<CheckProgress>` doesn't fit parallel engine | Use `SharedFlow<CheckProgress>` emitted from within `checkDueAnime`'s per-anime loop. Terminal value: `CheckProgress(total, total, "", "", null)` signals completion. |
| 12 | Hour estimate underestimated | Revised from ~24h to ~34h. See §12. |

---

## 1. Vision

A **unified Updates + Notifications system** that detects new episodes (sub + dub), shows them in the Updates feed, and sends notifications based on user preferences. The two systems are **interlinked**: the Updates engine discovers new episodes → the Notifications engine decides whether + how to alert. They communicate via a defined contract (not direct calls).

### Key principles
- **Interlinked but modular**: Updates engine finds new episodes; Notifications engine decides whether + how to alert. They communicate via interfaces (not direct calls) to avoid circular deps.
- **Future-proof**: configurable intervals, per-category selection, sub/dub tracking, smart release detection.
- **Honest about state**: "new" vs "initial batch" vs "acknowledged" are distinct states with clear retention rules.

---

## 2. Current State (what's built vs missing)

| Component | Status | What's there | What's missing |
|-----------|--------|-------------|----------------|
| `UpdateEngine` | ✅ Built | `checkDueAnime()`, `checkSingleAnime()`, `onEpisodesRefreshed()`, `ensureUpdateState()` | Configurable interval, live-progress callback, sub/dub-aware checking, `NotificationManager` wiring |
| `UpdateCheckWorker` | ✅ Built | 1h periodic, `checkDueAnime()` + retention purge | Configurable interval, `ScheduleRefresher` call, `cleanupOldSent()` call, SmartReleaseChecker |
| `episode_update` table | ✅ Built | `batch_type` + `episode_count` columns (D-192) | `new_expires_at` column, updated `getUnacknowledgedUpdates` query |
| `anime_update_state` table | ✅ Built | `next_check_at`, `last_known_episode_count`, `status` | `last_known_dub_count`, `last_checked_dub_at`, `total_episodes` |
| `NotificationManager` | ✅ Built | `postNotification()`, 2 channels, master toggle, per-anime config | `setContentIntent`, `postTestNotification()`, "watchable" trigger wiring |
| `ScheduleEngine` | ✅ Built | `fetchSchedule()`, `ActualReleaseUpdater` | "schedule" trigger (fire at airing time), `ScheduleRefresher` interface |
| `NotificationsSettingsScreen` | ⚠️ Buggy | 3-way toggle UI, defaults, per-anime library | 3-way toggle bug, no updates settings, UI needs redesign |
| Updates screen | ✅ Built | New + Earlier tabs, refresh button | No live-progress, no "initial batch" rendering, no sub/dub badges |
| Smart release detection | ❌ Missing | — | OneTimeWorkRequest chaining, 10-min polling, skip-after-3 |
| Combined settings section | ❌ Missing | — | Updates + Notifications in one section |

---

## 3. Architecture — Interlinked System

```
┌─────────────────────────────────────────────────────────────┐
│                    SETTINGS (UI)                             │
│  Updates & Notifications (combined section)                  │
│  - Master toggle (3-way: Auto / Manual / Off)                │
│  - Auto-update interval (6h/12h/24h/2d/3d/weekly)            │
│  - Manual: per-category checklist                            │
│  - Notification defaults (triggers + audio)                  │
│  - Notification library (per-anime config)                   │
│  - "Send test notification" button                            │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│              WorkManager (background)                        │
│  UpdateCheckWorker (configurable interval)                   │
│    1. ScheduleRefresher.fetchSchedule() — refresh airing     │
│       (interface — implemented by ScheduleEngine, bound in :app)│
│    2. UpdateEngine.checkDueAnime() — find new episodes       │
│       └─ For each new episode found:                         │
│          ├─ Insert episode_update (batch_type="new")         │
│          ├─ Update anime_update_state (sub + dub counts)     │
│          └─ NotificationManager?.postNotification("watchable")│
│    3. SmartReleaseChecker — OneTimeWorkRequest chaining      │
│       (for anime airing within ±1h)                          │
│    4. Retention purge (episode_update + notification_sent)   │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│              UPDATES FEED (UI)                               │
│  - "New" section (unacknowledged, new_expires_at > now)     │
│  - "Earlier" section (acknowledged OR expired)               │
│  - "Initial batch" rows (text: "Episodes 1-N added")         │
│  - Live-progress banner during refresh (cover + X/Y)         │
│  - Sub/Dub badges per episode                                │
└─────────────────────────────────────────────────────────────┘
```

### Interface pattern (avoids circular deps)
```
:core:updates defines:
  - interface ScheduleRefresher { suspend fun fetchSchedule() }
  - interface NotificationSender { suspend fun postNotification(...) }

:core:schedule implements ScheduleRefresher (bound in :app)
:core:notifications implements NotificationSender (bound in :app)
:app wires both into UpdateEngine + UpdateCheckWorker via Koin
```

---

## 4. DB Schema Changes

### 4a. `anime_update_state` — add dub tracking + total_episodes
```sql
ALTER TABLE anime_update_state ADD COLUMN last_known_dub_count INTEGER;
ALTER TABLE anime_update_state ADD COLUMN last_checked_dub_at INTEGER;
ALTER TABLE anime_update_state ADD COLUMN total_episodes INTEGER;
```
- `last_known_episode_count` → tracks SUB episodes (existing, semantic unchanged for backward compat).
- `last_known_dub_count` → tracks DUB episodes (null = no dub tracking yet).
- `last_checked_dub_at` → separate dub-check timestamp.
- `total_episodes` → from AniList `episodes` field. Used for completed-anime handling (§7c).

**Queries to update:**
- `upsertAnimeUpdateState` — add 3 new columns.
- `updateCheckResult` — add `last_known_dub_count` + `last_checked_dub_at` params.

**New query:**
```sql
-- For dub checking on FINISHED anime (§7c)
getDueDubAnime:
SELECT * FROM anime_update_state
WHERE auto_update_enabled = 1
  AND status = 'FINISHED'
  AND last_known_dub_count < total_episodes
ORDER BY next_check_at ASC;
```

**New index:**
```sql
CREATE INDEX IF NOT EXISTS idx_anime_update_due_dub ON anime_update_state(next_check_at)
WHERE auto_update_enabled = 1 AND status = 'FINISHED' AND last_known_dub_count < total_episodes;
```

### 4b. `episode_update` — add "new" expiry
```sql
ALTER TABLE episode_update ADD COLUMN new_expires_at INTEGER;
```
- Set to `discovered_at + 3 days` (259200000 ms) for `batch_type="new"` rows.
- NULL for `batch_type="initial"` rows (initial batches are never "new").

**Queries to update:**
- `upsertEpisodeUpdate` — add `new_expires_at` param.
- `getUnacknowledgedUpdates` — add `AND (new_expires_at IS NULL OR new_expires_at > :now)` filter.

**Index to update:**
```sql
DROP INDEX IF EXISTS idx_episode_update_unack;
CREATE INDEX IF NOT EXISTS idx_episode_update_unack
  ON episode_update(acknowledged, new_expires_at, discovered_at DESC);
```

### 4c. Migration wiring
All ALTER TABLE commands go in `DatabaseDriverFactory.migrateSchemaIfNeeded()` with `hasColumn` guards (the established pattern). Idempotent.

### 4d. `notification_config` + `notification_sent` — no schema changes
Already have the right columns. Only need to wire the retention purge (`cleanupOldSent`) into the worker.

---

## 5. Settings UI Redesign

### 5a. Combined "Updates & Notifications" section
```
Settings
├── Appearance
├── Extensions
├── Updates & Notifications  ← NEW combined section
│   ├── General              ← master toggle + interval + test notification
│   ├── New Anime Defaults   ← trigger + audio defaults (fixed 3-way toggle)
│   ├── Library              ← per-anime notification config
│   └── Update Categories    ← per-category checklist (manual mode)
├── Player
└── Debug
```

### 5b. General screen
- **Updates master toggle (3-way)**: "Auto updates" / "Manual updates" / "Off"
  - Auto: background checking at the configured interval for ALL library anime.
  - Manual: background checking at the configured interval for SELECTED categories only.
  - Off: no background checking. User must manually refresh. (Notifications master toggle is SEPARATE — "Off" here doesn't disable notifications.)
- **Interval selector** (shown when Auto or Manual):
  - 6 hours / 12 hours / 24 hours / 2 days / 3 days / Weekly
- **Sub/Dub checking toggles**:
  - "Check for new sub episodes" (default ON)
  - "Check for new dub episodes" (default OFF)
- **Notifications master toggle** (separate from updates):
  - "Enable notifications" (Switch, default ON)
- **"Check now" button** — triggers immediate manual refresh with live-progress UI.
- **"Send test notification" button** — posts a demo notification.
  - Checks `POST_NOTIFICATIONS` permission on Android 13+.
  - Gated by notifications master toggle (disabled if notifications are off).
  - Posts: "Demon Slayer — Episode 6 DUB" (uses the user's default audio pref).
  - Dedicated notification ID (so it can be cancelled).

### 5c. 3-way toggle fix (the bug)
**Root cause**: `TriggerState` enum order is `OFF(0), ON(1), SILENT(2)` but the UI list is `listOf(ON, SILENT, OFF)`. Using `state.ordinal` as `selectedIndex` mismatches.

**Fix**: Replace `state.ordinal` with `TRIGGERS.indexOf(state)` at all 8 call sites:
- `NotificationsSettingsScreen.kt` lines 171, 184, 197, 210
- `NotificationsLibraryScreen.kt` lines 341, 352, 363, 374

Note: `AudioPref.ordinal` is NOT buggy (enum order `SUB/DUB/BOTH` already matches the UI list), but we'll change it to `AUDIO.indexOf(audioPref)` for consistency.

### 5d. State migration
One-time migration in `NotificationPreferences.init`:
- If `notif_master_enabled` was true → `update_mode = "auto"`
- If `notif_master_enabled` was false → `update_mode = "off"`
- The `notif_master_enabled` key is kept (notifications master toggle still uses it).

---

## 6. Auto-Update System

### 6a. Configurable WorkManager interval
- Current: hard-coded 1h.
- New: read from `PreferenceStore` (`update_interval_hours`). Re-enqueue with `ExistingPeriodicWorkPolicy.REPLACE` when the preference changes.
- Intervals: 6h / 12h / 24h / 48h / 72h / 168h (weekly).
- When `update_mode = "off"`: `WorkManager.cancelUniqueWork("anikuta_update_check")`.

### 6b. Worker flow (expanded)
```kotlin
class UpdateCheckWorker {
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
}
```

### 6c. Manual mode (per-category)
- When `update_mode = "manual"`: the worker only checks anime in the selected categories.
- `UpdateEngine.checkDueAnime(filterMainIds: Set<String>?)` — if non-null, only checks those.
- The filter is built from selected categories → `ContentRepository.getMainIdsByCategory(categoryId)`.

### 6d. Live-progress
- `UpdateEngine.checkDueAnime()` emits `SharedFlow<CheckProgress>`:
  ```kotlin
  data class CheckProgress(val current: Int, val total: Int, val mainId: String, val title: String, val coverUrl: String?)
  ```
- Emitted before each anime check. Terminal: `CheckProgress(total, total, "", "", null)`.
- `UpdatesViewModel` collects + exposes as `StateFlow<CheckProgress?>`.
- `UpdatesScreen` renders a banner card when non-null.

---

## 7. Smart Release Detection

### 7a. OneTimeWorkRequest chaining (the 10-min polling)
For anime where `anime_update_state.next_airing_at` is within the next hour:
1. At `next_airing_at + 10min`: schedule a `OneTimeWorkRequest<SmartReleaseCheckWorker>` with `setInitialDelay(10, MINUTES)`.
   - Unique work name: `"smart_release_${mainId}_${episodeNumber}"`.
   - `ExistingWorkPolicy.REPLACE`.
   - `inputData`: mainId, episodeNumber, attempt=1.
2. Worker fires → fetches extension episode list → checks if the expected episode exists.
3. If found → mark `actual_at = now`, insert `episode_update`, fire "watchable" notification. Done.
4. If NOT found + attempt < 3 → schedule another `OneTimeWorkRequest` with `setInitialDelay(10, MINUTES)` + `attempt+1`.
5. If NOT found + attempt >= 3 → skip. Don't check again until the next manual refresh.

**Process death safety**: WorkManager survives process death. The `inputData` carries the attempt counter + mainId + episodeNumber, so the chain resumes correctly after reboot.

**Battery**: limited to anime airing within ±1h of the current time. Max 5 concurrent checks (Semaphore in the worker).

### 7b. Retroactive update
When the user manually refreshes an anime's details page + a new episode is found that was previously skipped:
- Update `anime_update_state.last_known_episode_count`.
- Insert `episode_update` row (with `discovered_at = now`).
- Fire "watchable" notification if enabled.
- This is already handled by `onEpisodesRefreshed` (D-192 Phase 3).

### 7c. Completed anime handling
- If `anime_update_state.status = "FINISHED"` AND `total_episodes IS NOT NULL`:
  - **Sub checking**: if `last_known_episode_count >= total_episodes` → STOP sub checking.
  - **Dub checking**: if `update_check_dub = true` AND `last_known_dub_count < total_episodes` → continue checking for dub (using `getDueDubAnime` query).
  - If dub checking is OFF or dub count is complete → set `auto_update_enabled = 0`.

---

## 8. Sub/Dub Tracking

### 8a. Audio variant detection
- `UpdateEngine.parseAudioVariant(scanlator, episodeName)` already exists — returns "sub" / "dub" / "unknown".
- When a new episode is found, the `audio_variant` is stored in `episode_update.audio_variant`.

### 8b. `checkSingleAnime` rewrite (variant-aware)
```kotlin
private suspend fun checkSingleAnime(state: AnimeUpdateState, now: Long): Int {
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
}
```

### 8c. Notification audio filtering
- `NotificationManager.postNotification()` already checks `notify_sub` / `notify_dub`.
- If new episode is sub + `notify_sub = false` → don't notify (but still insert episode_update).
- If new episode is dub + `notify_dub = false` → don't notify (but still insert episode_update).

---

## 9. Notification System

### 9a. Three trigger types
| Trigger | When | Who fires it | Wiring |
|---------|------|-------------|--------|
| **on_schedule** | At the AniList airing time (reminder — episode should be available) | ScheduleEngine | ScheduleEngine checks `airingAt <= now` → fires `postNotification(triggerType = "schedule")`. Text: "Episode N should be available now." |
| **on_watchable** | When the Updates engine actually finds the episode on the extension | UpdateEngine | After `upsertEpisodeUpdate` in `checkSingleAnime` → `notificationSender?.postNotification(triggerType = "watchable")`. Text: "Episode N is now available." |
| **on_immediate** | For past-due episodes (airingAt < now, not yet checked) | ScheduleEngine | Already fires. Text: "Episode N has been released." |

### 9b. Notification content
- Title: "New episode available"
- Text: "<Anime title> — Episode <N> <SUB/DUB>"
- Tap action: `setContentIntent` with PendingIntent → `MainActivity` with extra `navKey=AnimeDetailsKey.AniList(anilistId)`.
- Channel: default (sound) if trigger=ON, silent (no sound) if trigger=SILENT.
- `POST_NOTIFICATIONS` permission checked on Android 13+ before posting.

### 9c. Test notification
- `NotificationManager.postTestNotification()` — new method.
- Posts: "Demon Slayer — Episode 6 DUB" (hardcoded demo, uses default audio pref).
- Dedicated notification ID (999) for cancellation.
- Bypasses per-anime config (it's a test).
- Checks `POST_NOTIFICATIONS` permission first.

### 9d. Dedup + retention
- `notification_sent` table deduplicates: if `(main_id, episode_number, audio_variant, trigger_type)` already exists → don't re-post.
- Retention: `cleanupOldSent(now - 90 days)` called by the worker.
- `episode_update` "new" status: expires after 3 days (`new_expires_at`). Row stays in DB for "Earlier" section but is no longer "New".

---

## 10. Updates Feed UI

### 10a. Live-progress banner
When a refresh is in progress:
- Banner at the top of the Updates screen.
- Shows: cover image (56×80dp) + title + "Checking X of Y…"
- Animated (fade-in, progress bar).
- Disappears when `CheckProgress(total, total, ...)` is emitted.

### 10b. "Initial batch" rendering
- Rows with `batch_type = "initial"`: text "Episodes 1-N added to library", no SUB/DUB badge, already acknowledged.
- Rows with `batch_type = "new"`: "EP N" + SUB/DUB badge.

### 10c. Acknowledgment
- Tapping an episode → navigates to details page → `acknowledgeUpdatesByMainId(mainId)`.
- Row moves from "New" to "Earlier".
- After 3 days, row is no longer "New" even if not acknowledged (`new_expires_at` check).

---

## 11. Bug Fixes (included in this plan)

### 11a. 3-way toggle fix
- Replace `state.ordinal` with `TRIGGERS.indexOf(state)` at 8 call sites. ~10 lines.

### 11b. "No source from library" fix
- In `performAutoLink` (DetailsViewModel), after a successful match, write `preferenceStore.putString(KEY_SOURCE_LINK_PREFIX + anilistId, "${sourceId}:${animeUrl}")`. ~4 lines.

### 11c. `onEpisodesRefreshed` ordering fix
- `onEpisodesRefreshed` should call `ensureUpdateState` internally if the state doesn't exist, then proceed. ~5 lines.

### 11d. `POST_NOTIFICATIONS` + `setContentIntent`
- Check permission on Android 13+ before posting. Add `setContentIntent` to all notifications. ~15 lines.

---

## 12. Implementation Phases (after user approval)

| Phase | Task | Est. (revised) |
|-------|------|------|
| 1 | Bug fixes: 3-way toggle + no-source-from-library + onEpisodesRefreshed ordering | ~2h |
| 2 | DB schema: 5 new columns + 4 query updates + 1 new query + 2 indexes + migration | ~2h |
| 3 | Settings UI: combined section + master toggle + interval + per-category + test notification | ~5h |
| 4 | Auto-update: configurable WorkManager + manual mode + per-category filter + live-progress | ~4h |
| 5 | Smart release detection: OneTimeWorkRequest chaining + 10-min polling + completed-anime | ~7h |
| 6 | Sub/Dub tracking: checkSingleAnime rewrite + separate counts + notification filtering | ~3h |
| 7 | Notification system: wire 3 triggers + tap action + test notification + dedup/retention | ~4h |
| 8 | Updates feed UI: live-progress banner + initial-batch rendering + acknowledgment | ~3h |
| 9 | Interface pattern: ScheduleRefresher + NotificationSender (avoid circular deps) | ~2h |
| 10 | Docs + dashboard + notify | ~2h |
| **Total** | | **~34h** |

---

## 13. Concerns + Open Questions (for user review)

1. **"on_schedule" notification text**: should it say "Episode N should be available now" (reminder) or "Episode N is released" (confident)? My recommendation: "should be available now" — it's honest (the episode may not be on the extension yet).

2. **10-min polling battery impact**: checking 3 times per airing anime. My recommendation: limit to anime airing within ±1h, max 5 concurrent checks.

3. **"Completed" status source**: trust AniList `status` + `episodes` for the total. Use the extension's episode count for `last_known_episode_count`.

4. **`update_mode = "off"` + manual "Check now"**: yes — "off" only disables background checking, not manual refresh.

5. **Per-category manual mode**: check ALL anime in selected categories, regardless of `auto_update_enabled` (which is a failure-backoff flag, not a user preference).

6. **Notification tap action**: deep-link to the details page (so the user can immediately watch).

7. **Should the "Updates" and "Notifications" nav rows be merged into one screen or kept as separate sub-screens under one section?** My recommendation: separate sub-screens (General, Defaults, Library, Categories) under one "Updates & Notifications" section label — matches the user's vision.

8. **Should dub checking default to ON or OFF?** My recommendation: OFF (most users watch sub; dub is opt-in).

---

## 14. Future-Proofing

- **Multi-source**: the `ContentId` + `ContentIdType` system (D-190) means the Updates engine can work with any content type.
- **Multi-content-type**: `content.content_type` (anime/manga/novel) is already present. Updates engine checks `content_type = "anime"` for now; manga/novel updates are a future phase.
- **Configurable intervals**: `update_interval_hours` is a simple integer — adding new intervals is one UI entry.
- **Per-anime override**: `anime_update_state.auto_update_enabled` allows disabling updates for a specific anime.
- **Backup/restore**: all preferences mirrored to `app_settings` table (D-192 Phase 1).
