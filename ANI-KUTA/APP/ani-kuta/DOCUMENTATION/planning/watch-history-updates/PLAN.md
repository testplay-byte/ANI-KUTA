# Plan — Watch Progress, History, Updates, Schedule, Tracking

> **Branch:** `feature/watch-progress-history-updates` (created from `main` at `167f3fd`).
> **Status:** DRAFT (iteration 4 — user decisions integrated + new requirements).
> **Scope:** A big multi-feature plan. The user wants thorough planning + sub-agent review (3-4 iterations) before any implementation. **This document is the plan, NOT the implementation.**
>
> **Iteration 4 changes (user decisions + new requirements):**
> - Q1 resolved: Updates stays under **More** (NOT promoted to bottom-nav).
> - Q2 resolved: auto-mark threshold is **user-configurable** (default 85%, in Settings) — NOT hardcoded. Added a `WatchPreferences` field + Settings UI.
> - Q3 resolved: calendar limits are **fixed** (1 month back + 1 year forward) — NOT configurable.
> - Q4 resolved: **Continue Watching — logic only for now** (the query + derived view + the `getContinueWatching` store method). UI placement deferred to a later decision. Added Phase CW (logic-only).
> - Q5 resolved: **full-fledged notification system** — per-content + per-episode + sub/dub config, 3 trigger types (on-schedule-arrival, on-watchable, on-immediate-release), settings UI. Added Phase NOTIF + §14 (notification system design, sub-agent-designed).
> - **NEW: per-episode ratings.** The user wants to rate individual episodes AND whole content. Added a `user_episode_rating` table (separate from `user_rating`). Phase TR extended.
> - **NEW: "Lego" architecture emphasis.** All systems built as independent, swappable modules. UI strictly separate from logic. Each section improvable without affecting others. Documented as a guiding principle (§0.1).
> - **NEW: highly-detailed internal tracking.** The `activity_event` system must be rich enough to feed any external system + future statistics. Per-episode ratings feed into it.
> - **Implementation cadence:** one phase at a time, verify CI green after each (read GitHub check-runs), sub-agent review per phase, fix errors from the beginning.
> - CF1: replaced broken `manually_marked` single-flag with two columns (`auto_mark_suppressed` + `user_marked_watched`) + corrected `isWatched` derivation.
> - CF2: added §1.8 migration mechanics (edit `.sq` for codegen + idempotent `ALTER TABLE` in `DatabaseDriverFactory.onOpen` for existing installs).
> - CF3: added `FOREIGN KEY ... ON DELETE CASCADE` to all new tables + missing indexes (`watch_progress(main_id)`, `anime_update_state(status)`, etc.).
> - CF4: `next_check_at` now has a backoff rule (1h→2h→4h→8h→24h capped) when a check finds nothing; refreshes from Schedule data when a new episode is found.
> - CF5: details-page self-improving hook now INSERTs into `episode_update` with `acknowledged=1` (so the feed shows it once, then auto-clears — mirrors old project's `acknowledgeResult-before-navigation`).
> - CF6: WorkManager worker now has `NetworkType.CONNECTED` + `BatteryNotLow` + `ExistingPeriodicWorkPolicy.KEEP`; interval 1h (filters by `next_check_at` internally); one-shot `OneTimeWorkRequest` for notify-shortly-after-airing.
> - CF7: `audio_variant TEXT NOT NULL DEFAULT 'unknown'` (was nullable — broke UNIQUE dedup).
> - IM1: `:feature:updates` now has api/impl split (consistency with other features).
> - IM2: dropped `TrackingRepository` facade (pure indirection). Backup enumerates tables directly.
> - IM3: replaced `SwipeToDismissBox` with `pointerInput { detectHorizontalDragGestures }` + spring-back. ONE direction for v1 (right = toggle).
> - IM4: dropped `Modifier.blur` (too expensive). Watched styling = grayscale `ColorFilter` + 0.5 alpha only.
> - IM5: dropped `confidence` column (redundant with `source` + the `actual_at`-over-`scheduled_at` precedence).
> - IM6: `user_rating.rating` is INTEGER 0-100 (AniList-native). Dropped redundant `UNIQUE(main_id)`.
> - IM7: dropped `last_position` (redundant with `position`).
> - IM10: `activity_event` excluded from backup (session_id is ephemeral; events are regenerable from `watch_progress`).
> - IM11: `actual_at` uses source's `dateUpload` when available, falling back to `discovered_at`; if `actual_at < scheduled_at`, prefer `scheduled_at`.
> - IM13: added `Anikuta:Core:Updates:Worker` log tag.
> - IM14: SC (Schedule) split into SC-1 (list/calendar, independent) + SC-2 (actual-release, after UP).
> - M2: AniList status-change handling — Schedule fetch refreshes `anime_update_state.status`; Updates engine re-skips FINISHED.
> - M3: source-uninstall handling — skip with WARN, set `auto_update_enabled = 0` after 3 consecutive failures.
> - M4: WorkManager worker also refreshes Schedule data (unified AniList airing fetch).
> - M5: Updates suppresses already-watched episodes (`watch_progress.completed = 1` → `acknowledged = 1`).
> - M9: `episode_update` retention — added `acknowledged_at`; worker cleans `acknowledged = 1 AND acknowledged_at < now-7d`.
> - S2: partial index for due-check query.
> - S3: `clearByMainId(mainId)` query.
> - S4: unified AniList airing data (Schedule fetch populates `anime_update_state.next_airing_*`).
> - S6: library-change hooks documented.
>
> **Companion analysis (read-only, in sandbox `ani-kuta-analysis/`):**
> - `06-old-history-analysis.md` — old project's History UI + logic + data.
> - `07-old-updates-analysis.md` — old project's Updates UI + the flawed refresh loop.
> - `08-old-schedule-analysis.md` — old project's Schedule (list + calendar) + limits.
> - `09-old-tracking-analysis.md` — old project's DB / watch-progress / backup design.
>
> **Core principle (from user):** take INSPIRATION from the old project's UI + logic, NEVER copy directly (different structure, different package, different contentId system). The new project uses `main_id` (stable UUID per content) + `episode_key` (5-digit padded) — NOT the old project's mutable `content_id`.

---

## 0. What we're building (executive summary)

Five interconnected features, all sharing a centralized tracking + DB layer:

1. **Watch Progress + Watched Status** — per-episode progress persistence (SQLDelight, replacing the current in-memory stub), 85%-auto-mark-watched, manual toggle, swipe-to-toggle on the episode row, watched styling (faded/greyed/blur).
2. **History page** — a beautiful, day-grouped list of recently-watched episodes (in the More section, replacing the current stub). Driven by watch progress.
3. **Updates section** — a feed of new-episode releases for the user's library. Smarter than the old project: only checks ongoing series, uses release dates, auto-checks via WorkManager.
4. **Schedule section** — list view + calendar view of upcoming episode airings (AniList airing schedule). Calendar with 1-month-back / 1-year-forward limits. Sub/dub awareness.
5. **Centralized tracking system** — a single write-sink (`TrackingRepository`) that records watch events, genres, ratings, etc. for future statistics. Backup-friendly (stable `main_id` keys).

**What this plan covers:** DB schema, module structure, the logic for each feature, the UI specs (taking inspiration from the old project), the smart engines (update detection, schedule actual-release), console logging, backup-friendliness, and the implementation phases.

**What this plan does NOT cover (deferred):** full-fledged statistics UI, full backup/restore UI (only the schema is designed to be backup-friendly), MAL/TMDB trackers (AniList only for now), the "self-improving actual-release detection" ML stuff (basic heuristic for now).

### 0.1 Guiding architecture principle — "Lego" modularity (user directive)

The user explicitly wants a **Lego-style architecture**: independent, swappable modules joined together. Concretely:

1. **Each system is its own `:core:*` module** — watch-progress, updates, schedule, ratings, notifications, activity-tracker. Each owns its tables + logic + Koin module. A module can be improved/replaced without touching the others.
2. **UI is strictly separate from logic** (CORE_RULES §7). `:feature:*:impl` modules render state + dispatch user actions; they do NOT contain business logic. ViewModels call `:core:*` stores/repositories; the stores do the work. A UI module can be rewritten without touching the logic module.
3. **Defined contracts (interfaces) between modules.** `WatchProgressStore`, `UpdateEngine`, `ScheduleRepository`, `RatingStore`, `NotificationManager` are interfaces. The impls can be swapped (e.g. SQLDelight → something else later) without breaking consumers.
4. **Non-overlapping table ownership.** Each `:core:*` module owns specific tables + specific columns on shared tables (see §13.6). No two modules write the same column → no write conflicts → modules can evolve independently.
5. **Each module is independently testable + reviewable.** Sub-agent code review happens per-phase, per-module.

This principle is enforced throughout the plan. If a design choice would couple two modules, it's rejected.

---

## 1. Database schema (SQLDelight)

All tables key off `main_id` (stable UUID from `:core:content`) + `episode_key` (5-digit padded, from the content/episode system). This is the key advantage over the old project — **stable for backup/restore**.

### 1.1 `watch_progress` (EXISTS — extend it)

The table already exists at `core/database/.../watch.sq`. Current schema:
```sql
CREATE TABLE IF NOT EXISTS watch_progress (
    episode_key TEXT NOT NULL PRIMARY KEY,
    position INTEGER NOT NULL,           -- seconds
    duration INTEGER NOT NULL,           -- seconds
    completed INTEGER NOT NULL DEFAULT 0,
    completed_at INTEGER,
    last_watched_at INTEGER NOT NULL
);
```

**Extensions needed** (see §1.8 for migration mechanics — the `.sq` CREATE TABLE is EDITED to include the new columns inline for codegen + fresh installs; idempotent `ALTER TABLE ADD COLUMN` in `onOpen` for existing installs):

```sql
-- The EDITED CREATE TABLE in watch.sq (for fresh installs + SQLDelight codegen).
-- Existing 6 columns + 5 new columns = 11 total.
CREATE TABLE IF NOT EXISTS watch_progress (
    episode_key TEXT NOT NULL PRIMARY KEY,
    position INTEGER NOT NULL,                              -- seconds
    duration INTEGER NOT NULL,                              -- seconds
    completed INTEGER NOT NULL DEFAULT 0,
    completed_at INTEGER,
    last_watched_at INTEGER NOT NULL,
    -- NEW COLUMNS (Phase WP): --
    main_id TEXT,                                           -- FK → content(main_id), for JOINs + backup (CF3: app-level enforced for existing installs; FK in CREATE TABLE for fresh)
    watch_count INTEGER NOT NULL DEFAULT 0,                 -- incremented each completed watch
    first_watched_at INTEGER,                               -- the first time the user watched this episode
    auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,        -- CF1: user manually un-marked → suppress auto-mark until next play
    user_marked_watched INTEGER NOT NULL DEFAULT 0          -- CF1: user explicitly marked watched (sticky)
);
CREATE INDEX IF NOT EXISTS idx_watch_progress_main_id ON watch_progress(main_id);
```

(For existing dev installs: `DatabaseDriverFactory.onOpen` runs idempotent `ALTER TABLE watch_progress ADD COLUMN ...` for each of the 5 new columns — see §1.8.)

**Why `main_id`?** The old project keyed off `content_id` (mutable — breaks on extension upgrade). The new project's `main_id` is a stable UUID assigned at content creation, never changes. **All tracking tables key off `main_id`** so backup/restore resolves cleanly.

**Why two flags instead of one `manually_marked`? (CF1)** The single-flag design was semantically broken — it meant both "user said watched" AND "user said unwatched, suppress auto." Replaced with:
- `auto_mark_suppressed` — set when the user manually UN-marks. Suppresses the 85% auto-mark rule until the user watches again (next play resets it to 0).
- `user_marked_watched` — set when the user explicitly marks watched (sticky — stays watched until the user un-marks).

**`isWatched` derivation (CORRECTED):**
```
isWatched = (completed = 1 AND auto_mark_suppressed = 0) OR user_marked_watched = 1
```
This correctly returns FALSE for an episode the user just manually un-marked (completed may still be 1, but auto_mark_suppressed = 1 + user_marked_watched = 0).

**New queries** (add to `watch.sq`):
- `getWatchProgressByMainId(mainId)` — all episodes for an anime (uses the new `idx_watch_progress_main_id`).
- `observeWatchProgressByMainId(mainId): Flow<List<WatchProgress>>` — reactive for the details page.
- `getContinueWatching(limit)` — `WHERE completed = 0 AND auto_mark_suppressed = 0 ORDER BY last_watched_at DESC LIMIT ?` (in-progress, not suppressed).
- `incrementWatchCount(episodeKey)` — bump `watch_count` + set `first_watched_at` if null.
- `setAutoMarkSuppressed(episodeKey, value)` — user un-mark override.
- `setUserMarkedWatched(episodeKey, value)` — user marked-watched override.
- `resetAutoMarkSuppressed(episodeKey)` — called on next play (resets `auto_mark_suppressed = 0`).
- `getWatchedEpisodeCount(mainId)` — for anime-level stats.
- `clearByMainId(mainId)` (S3) — `DELETE FROM watch_progress WHERE main_id = ?` (for library-remove).
- `getAllWatchProgress()` — for backup export.

### 1.2 `activity_event` (EXISTS — no changes)

Already at `tracking.sq`. The `ActivityTracker` (D-045) is already implemented with batched writes. **No changes needed** — it's the append-only event log. Used for statistics. Keys off `content_key` (which maps to `main_id`) + `episode_key`.

### 1.3 `episode_update` (NEW — the Updates feed)

```sql
CREATE TABLE IF NOT EXISTS episode_update (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    main_id TEXT NOT NULL,               -- FK → content(main_id)
    episode_key TEXT NOT NULL,           -- the new episode's key
    episode_number REAL NOT NULL,
    episode_title TEXT,
    source_id INTEGER,                   -- which extension source reported it
    audio_variant TEXT NOT NULL DEFAULT 'unknown',  -- CF7: 'sub' | 'dub' | 'unknown' (NOT NULL — nullable breaks UNIQUE dedup)
    discovered_at INTEGER NOT NULL,      -- when we found it
    acknowledged INTEGER NOT NULL DEFAULT 0,  -- user dismissed / opened it
    acknowledged_at INTEGER,             -- M9: when acknowledged (for retention cleanup)
    FOREIGN KEY (main_id) REFERENCES content(main_id) ON DELETE CASCADE,  -- CF3
    UNIQUE(main_id, episode_key, audio_variant)
);
CREATE INDEX IF NOT EXISTS idx_episode_update_discovered ON episode_update(discovered_at DESC);
CREATE INDEX IF NOT EXISTS idx_episode_update_unack ON episode_update(acknowledged, discovered_at DESC);
CREATE INDEX IF NOT EXISTS idx_episode_update_main_id ON episode_update(main_id);  -- iteration 3: for CASCADE-delete perf + per-anime queries
```

**Why `audio_variant` NOT NULL? (CF7)** SQLite treats NULL as distinct in UNIQUE constraints — two rows with `audio_variant=NULL` would BOTH be allowed, breaking dedup. `NOT NULL DEFAULT 'unknown'` ensures dedup works when sub/dub detection fails (the common case).

**Why `acknowledged_at`? (M9)** Retention — the WorkManager worker runs `DELETE FROM episode_update WHERE acknowledged = 1 AND acknowledged_at < ?` (7-day cleanup) so the table doesn't grow unbounded.

**Why `acknowledged`?** When the user opens the update (navigates to the anime), it's marked acknowledged so it leaves the "New" feed (mirrors the old project's `acknowledgeResult`).

### 1.4 `anime_update_state` (NEW — per-anime smart-update metadata)

```sql
CREATE TABLE IF NOT EXISTS anime_update_state (
    main_id TEXT NOT NULL PRIMARY KEY,   -- FK → content(main_id)
    status TEXT,                         -- 'RELEASING' | 'FINISHED' | 'NOT_YET_RELEASED' | 'CANCELLED' | null (from AniList, M2: refreshed by Schedule fetch)
    last_checked_at INTEGER,             -- when we last checked this anime for updates
    next_check_at INTEGER,               -- when we should next check (CF4: with backoff)
    last_known_episode_count INTEGER,    -- highest episode number we've seen
    next_airing_episode INTEGER,         -- from AniList nextAiringEpisode (S4: populated by Schedule fetch)
    next_airing_at INTEGER,              -- from AniList airingAt (epoch millis) (S4: populated by Schedule fetch)
    auto_update_enabled INTEGER NOT NULL DEFAULT 1,  -- user can disable per-anime (M3: set to 0 after 3 consecutive failures)
    consecutive_failures INTEGER NOT NULL DEFAULT 0,  -- M3: source-uninstall / error tracking
    backoff_step INTEGER NOT NULL DEFAULT 0,           -- iteration 3 (Q4 resolved): current backoff step (0=none, 1=1h, 2=2h, 3=4h, 4=8h, 5=24h capped) — persisted so it survives app restart
    FOREIGN KEY (main_id) REFERENCES content(main_id) ON DELETE CASCADE  -- CF3
);
-- S2: partial index — only due + enabled + releasing anime (the worker's query)
CREATE INDEX IF NOT EXISTS idx_anime_update_due ON anime_update_state(next_check_at)
    WHERE auto_update_enabled = 1 AND status = 'RELEASING';
CREATE INDEX IF NOT EXISTS idx_anime_update_status ON anime_update_state(status);
```

**This is the core of the "smart update" engine.** The old project had these columns on the `animes` table but never used them. We put them in a separate table (modular — `:core:updates` owns this) + actually use them:
- `status` filter: only check `RELEASING` anime. `FINISHED`/`CANCELLED` are skipped (never auto-update).
- `next_check_at`: computed from `next_airing_at + 1h` (check shortly after the expected airing). If no airing data: `last_checked_at + 24h` (daily fallback).
- `last_known_episode_count`: the diff baseline. When we check + see a higher number, that's a new episode → insert into `episode_update`.
- The WorkManager worker queries `next_check_at <= now` → checks only those anime. **No full-library scan.**

### 1.5 `episode_schedule` (NEW — the Schedule data)

```sql
CREATE TABLE IF NOT EXISTS episode_schedule (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    main_id TEXT NOT NULL,               -- FK → content(main_id)
    anilist_id INTEGER,                  -- for the AniList airing API
    episode_number INTEGER NOT NULL,
    scheduled_at INTEGER NOT NULL,       -- AniList airingAt (epoch millis) — the PLANNED time
    actual_at INTEGER,                   -- the ACTUAL release time (null until detected; IM11: uses source dateUpload)
    audio_variant TEXT NOT NULL DEFAULT 'unknown',  -- CF7: NOT NULL (same reason as episode_update)
    source TEXT NOT NULL DEFAULT 'anilist',  -- 'anilist' | 'extension' | 'manual' (IM5: dropped confidence — source precedence at query time)
    fetched_at INTEGER NOT NULL,
    FOREIGN KEY (main_id) REFERENCES content(main_id) ON DELETE CASCADE,  -- CF3
    UNIQUE(main_id, episode_number, audio_variant)
);
CREATE INDEX IF NOT EXISTS idx_schedule_at ON episode_schedule(scheduled_at);
CREATE INDEX IF NOT EXISTS idx_schedule_main ON episode_schedule(main_id);
```

**Why `scheduled_at` + `actual_at`?** The user wants the ACTUAL release date, not just the scheduled one. AniList says "episode 5 airs Aug 10 9:00 JST" — that's `scheduled_at`. The episode actually appears on the extension source at Aug 10 11:30 JST — that's `actual_at`. The Schedule UI shows `actual_at` when available, else `scheduled_at`.

**Why `source` instead of `confidence`? (IM5)** The plan had a `confidence INTEGER` with only 2 values (80/90) — that's a 2-value enum stored as int, redundant with `source`. Dropped. Precedence at query time: `actual_at` (when non-null) over `scheduled_at`; if multiple `source` values, prefer `extension` > `anilist` > `manual` via `ORDER BY CASE`.

**`actual_at` logic (IM11):** when the UpdateEngine finds the episode on a source, set `actual_at = EpisodeInfo.dateUpload` (the source's claimed upload time) when available, falling back to `discovered_at`. If `actual_at < scheduled_at` (source early-release — rare but possible), prefer `scheduled_at` to avoid "released before scheduled" confusion in the UI.

### 1.6 `user_rating` (NEW — for future statistics)

```sql
CREATE TABLE IF NOT EXISTS user_rating (
    main_id TEXT NOT NULL PRIMARY KEY,   -- FK → content(main_id) (IM6: PRIMARY KEY already implies uniqueness — no separate UNIQUE)
    rating INTEGER NOT NULL,             -- IM6: 0-100 (AniList-native; displayed as 0-10 with one decimal)
    rated_at INTEGER NOT NULL,
    FOREIGN KEY (main_id) REFERENCES content(main_id) ON DELETE CASCADE  -- CF3
);
```

The user mentioned tracking ratings. This is a per-anime rating (the whole content). Per-episode ratings are in §1.10.

### 1.10 `user_episode_rating` (NEW — iteration 4: per-episode ratings, user-requested)

The user wants to rate **individual episodes** alongside the whole content. This is a separate table from `user_rating` (§1.6) — per-episode, keyed by `main_id` + `episode_key`.

```sql
CREATE TABLE IF NOT EXISTS user_episode_rating (
    main_id TEXT NOT NULL,               -- FK → content(main_id)
    episode_key TEXT NOT NULL,           -- the standardized episode key (${mainId}|${padded_5_digit})
    rating INTEGER NOT NULL,             -- 0-100 (AniList-native; displayed as 0-10 with one decimal — same scale as user_rating)
    rated_at INTEGER NOT NULL,
    PRIMARY KEY (main_id, episode_key),
    FOREIGN KEY (main_id) REFERENCES content(main_id) ON DELETE CASCADE  -- CF3
);
CREATE INDEX IF NOT EXISTS idx_episode_rating_main ON user_episode_rating(main_id);
```

**Why a separate table?** Per-anime (`user_rating`) + per-episode (`user_episode_rating`) have different cardinalities (1 per anime vs N per anime) + different query patterns. Keeping them separate is cleaner than one table with a nullable `episode_key`. Both feed into the `activity_event` log (RATING_SET, EPISODE_RATING_SET) for future statistics.

**Note on `episode_key`:** uses the standardized `${mainId}|${padded_5_digit}` format (§1.9) — stable for backup/restore. Does NOT include `audio_variant` (sub + dub of the same episode share the rating — defensible default; if the user wants separate sub/dub ratings later, add an `audio_variant` column).

### 1.7 Schema summary

| Table | Status | Purpose | Key | FK CASCADE |
|-------|--------|---------|-----|------------|
| `watch_progress` | EXISTS, EXTEND | per-episode watch state | `episode_key` (+ new `main_id`) | new `main_id` → content |
| `activity_event` | EXISTS, no change | append-only event log (IM10: excluded from backup — `session_id` is ephemeral) | `id` auto | — |
| `episode_update` | NEW | the Updates feed | `id` auto, UNIQUE(main_id, episode_key, audio_variant) | main_id → content |
| `anime_update_state` | NEW | per-anime smart-update metadata | `main_id` | main_id → content |
| `episode_schedule` | NEW | Schedule data (planned + actual) | `id` auto, UNIQUE(main_id, episode_number, audio_variant) | main_id → content |
| `user_rating` | NEW | per-anime user rating | `main_id` | main_id → content |

All migrations are additive `ALTER TABLE` (the project's pattern — no `.sqm` files). Existing dev installs will get the new columns via `DatabaseDriverFactory.onOpen` idempotent overrides.

### 1.8 Migration mechanics (CF2)

**Dual approach required** (NOT either/or):
1. **Edit the `.sq` CREATE TABLE statements** to include the new columns + new queries. This is for **SQLDelight codegen** (generates the Kotlin data classes + query methods) + for **fresh installs** (the CREATE TABLE runs on first launch).
2. **Add idempotent `hasColumn` checks + `ALTER TABLE ADD COLUMN` statements** in `DatabaseDriverFactory.migrateSchemaIfNeeded` (the existing pattern — see the `data_cache_episode.episode_url` migration for the template). This is for **existing dev installs** (whose DB already has the old schema without the new columns).

For the NEW tables (`episode_update`, `anime_update_state`, `episode_schedule`, `user_rating`): the CREATE TABLE in the `.sq` file handles fresh installs; existing installs get them via `CREATE TABLE IF NOT EXISTS` in `onOpen` (idempotent — safe to run every launch).

**Note on FKs for the `watch_progress.main_id` extension:** SQLite cannot add a FOREIGN KEY to an existing table via ALTER TABLE. So for existing installs, the `main_id` column is added without an FK constraint, enforced at the app level (the `ContentRepository` ensures `main_id` is valid). For fresh installs, the FK is declared in the CREATE TABLE. This is an acceptable trade-off (orphan rows are cleaned by `clearByMainId` on library-remove).

**Testing:** clear-app-data test (fresh install) + upgrade test (existing install with old schema → new schema). Verify the new columns are present + queryable.

### 1.9 `episode_key` format standardization (🚨 BLOCKER — prerequisite for Phase WP)

**The problem (found in PLAN-REVIEW-2):** the codebase has **4 different `episode_key` formats**:
1. `WatchScreen.kt:361,574` — `"${watchKey.sourceId}|$epUrl"` (sourceId + episode URL — **NOT stable**: sourceId changes on extension reinstall, epUrl can change).
2. `EpisodeMetadataFetcher.kt:101,125,171,213` — `"al:$anilistId|ep:$epNum"` (AniList-specific — doesn't work for extension-only content).
3. `DownloadScanner.deriveEpisodeKey` — `"${mainId}|${padded_5_digit}"` (stable — mainId is a UUID, episode number is stable).
4. `DetailsViewModel` (episode metadata) — `"${mainId}:${episodeNumber.toInt()}"` (stable-ish, different separator).

This breaks the plan's "stable for backup/restore" claim: if `watch_progress.episode_key` uses format #1, a backup restored on a device with a different sourceId (extension reinstalled) would orphan all progress rows.

**The standard:** `${mainId}|${padded_5_digit}` (format #3 — the DownloadScanner's format). `mainId` is a stable UUID (from `:core:content`); `padded_5_digit` is the zero-padded episode number (`String.format("%05d", episodeNumber.toInt())`). Both survive extension reinstalls, source URL changes, AND backup/restore.

**Migration (part of Phase WP, before the watch_progress work):**
1. **WatchScreen.kt:361,574** — change `val epKey = "${watchKey.sourceId}|$epUrl"` → `val epKey = "${watchKey.mainId}|${String.format("%05d", epNum.toInt())}"`. (Requires `mainId` to be available on `WatchKey` — verify it's passed; if not, add it.)
2. **EpisodeMetadataFetcher.kt** — change `"al:$anilistId|ep:$epNum"` → `"${mainId}|${String.format("%05d", epNum.toInt())}"`. (Requires `mainId` to be available in the fetcher context — verify.)
3. **DetailsViewModel** — change `"${mainId}:${episodeNumber.toInt()}"` → `"${mainId}|${String.format("%05d", episodeNumber.toInt())}"` (use `|` separator consistently + 5-digit padding).
4. **`WatchProgressStore`** — the `episodeKey` param in `save`/`get`/`observe`/`delete` now expects the standardized format. All callers must pass it.
5. **Existing dev installs** — a one-time migration in `DatabaseDriverFactory.onOpen`: for each `watch_progress` row, look up the content by the old key's sourceId/epUrl (best-effort) + rewrite to the new format. If the lookup fails, keep the old key (the row still works locally; it just won't backup-restore cleanly). Log a WARN.

**Note:** this is a pre-existing codebase issue, not something the plan introduces. But the plan's backup-friendliness claim depends on it, so it MUST be resolved first. This is a small, focused change (a few lines in 3 files + the migration) — do it as step 0 of Phase WP.

---

## 2. Watch Progress + Watched Status (Feature 1)

### 2.1 Module structure
- **`:core:watch-progress`** — the interface (`WatchProgressStore`) already exists. Add a new **SQLDelight impl**: `SqlDelightWatchProgressStore` in a new file `core/watch-progress/.../SqlDelightWatchProgressStore.kt`. The Koin module switches from `InMemoryWatchProgressStore` → `SqlDelightWatchProgressStore`.
- **`:core:database`** — add the new columns + queries to `watch.sq` (§1.1) + the migration in `DatabaseDriverFactory.onOpen` (§1.8).
- **`:feature:anime-details:impl`** — the episode row gets swipe + watched styling.

### 2.2 The WatchProgressStore impl (replacing InMemory)
`SqlDelightWatchProgressStore`:
- `save(episodeKey, progress)` → `upsertWatchProgress` + conditionally `incrementWatchCount` (if transitioning to completed) + the 85% auto-mark logic (§2.3).
- `get(episodeKey)` → `getWatchProgress`.
- `observe(episodeKey)` → `asFlow()` of `getWatchProgress`.
- `observeRecent(limit)` → `asFlow()` of `getRecentWatchProgress`.
- `markCompleted(episodeKey)` → `markCompleted` + `incrementWatchCount`.
- `delete(episodeKey)` → `deleteWatchProgress`.
- NEW: `observeByMainId(mainId)` → for the details page episode list.
- NEW: `setAutoMarkSuppressed(episodeKey, value)` → user un-mark override (CF1).
- NEW: `setUserMarkedWatched(episodeKey, value)` → user marked-watched override (CF1).
- NEW: `resetAutoMarkSuppressed(episodeKey)` → called on next play (resets the suppress flag so auto-mark re-arms).
- NEW: `isWatched(episodeKey)` → derived: `(completed = 1 AND auto_mark_suppressed = 0) OR user_marked_watched = 1` (CF1 — corrected).
- NEW: `clearByMainId(mainId)` → for library-remove (S3).

### 2.3 The auto-mark-watched state machine (the 85% rule) — CORRECTED (CF1)

The user's rule: "if progress > 85%, auto-mark as watched UNTIL the user manually un-marks."

**Two flags** (replaces the broken single `manually_marked`):
- `auto_mark_suppressed` — set when the user manually UN-marks. Suppresses the 85% auto-mark until the user watches again (next play resets it to 0).
- `user_marked_watched` — set when the user explicitly marks watched (sticky — stays watched until the user un-marks).

State transitions (iteration 3: `completed=0` consistently in SUPPRESSED states — fixes the diagram-vs-prose inconsistency + the UX bug):
```
NOT_WATCHED (completed=0, auto_mark_suppressed=0, user_marked_watched=0)
  │
  ├─ user watches to >85% ──▶ AUTO_WATCHED (completed=1, auto_mark_suppressed=0, user_marked_watched=0)
  │                              │
  │                              ├─ user manually un-marks ──▶ SUPPRESSED (completed=0, auto_mark_suppressed=1, user_marked_watched=0)
  │                              │                              [shows as UNwatched — isWatched=false]
  │                              │                              │
  │                              │                              └─ user watches again (next play) ──▶ resets auto_mark_suppressed=0
  │                              │                                  [completed=0 stays → isWatched=false until user crosses 85% again]
  │                              │                                  → if >85% again → AUTO_WATCHED (completed=1)
  │                              │
  │                              └─ user rewinds to <85% ──▶ stays AUTO_WATCHED (completed=1 — auto-unwatch NEVER happens, IM9)
  │
  └─ user manually marks watched ──▶ MANUAL_WATCHED (completed=1, auto_mark_suppressed=0, user_marked_watched=1)
                                  [sticky — stays watched regardless of auto-mark]
                                  │
                                  └─ user manually un-marks ──▶ SUPPRESSED (completed=0, auto_mark_suppressed=1, user_marked_watched=0)
```

**Key UX fix (iteration 3):** when transitioning AUTO_WATCHED → SUPPRESSED (user un-marks), `completed` is set to `0` (was ambiguous before). This means: on the next play, `resetAutoMarkSuppressed` clears the suppress flag, but `completed=0` remains — so `isWatched=false` until the user watches past 85% again. This is the correct behavior: un-marking means "I want this to show as unwatched + I don't want it auto-marked until I've actually re-watched it."

**`isWatched` derivation (the key correctness fix):**
```
isWatched = (completed = 1 AND auto_mark_suppressed = 0) OR user_marked_watched = 1
```
This correctly returns FALSE for a SUPPRESSED episode (completed may be 1, but auto_mark_suppressed=1 + user_marked_watched=0).

**Implementation in `SqlDelightWatchProgressStore.save()`:**
- If `progress.progressFraction > 0.85` AND `auto_mark_suppressed = 0` → set `completed = 1, completed_at = now` (the auto-mark fires).
- If `progress.progressFraction <= 0.85` AND `completed = 1` AND `auto_mark_suppressed = 0` → (user rewound) keep `completed = 1` (IM9: auto-unwatch NEVER happens — only manual unwatch). Documented: users who scrub back to rewatch a scene then close expect the episode to stay watched.
- On play start (FILE_LOADED): call `resetAutoMarkSuppressed(episodeKey)` → sets `auto_mark_suppressed = 0` (re-arms the auto-mark for the new session). `completed` is NOT reset here (it stays 0 if the user had un-marked — they must re-cross 85% to re-mark).
- `setAutoMarkSuppressed(episodeKey, true)` (user un-marked) → set `auto_mark_suppressed = 1, completed = 0, user_marked_watched = 0` (iteration 3: `completed=0` so `isWatched=false` until re-watched past 85%).
- `setUserMarkedWatched(episodeKey, true)` → user explicitly marked watched → set `user_marked_watched = 1, completed = 1` (sticky).
- `setUserMarkedWatched(episodeKey, false)` → equivalent to `setAutoMarkSuppressed(episodeKey, true)` (un-mark).

### 2.4 Player integration (where progress is saved)
Currently: `WatchScreen` saves via `InMemoryWatchProgressStore` every 10s + onDispose (D-072). **Switch to `SqlDelightWatchProgressStore`.** Add save-on-pause (the old project missed this — a known flaw). Triggers:
- Every 10s during playback (existing).
- On dispose (existing).
- On pause / ON_STOP (NEW — the old project missed this).
- On episode switch (NEW — so the previous episode's progress is saved before switching).
- On FILE_LOADED for the NEW episode → check resume position (if `progress < 5%` → fresh; if `> 90%` → start over; else resume + show "Start over?" overlay for 10s). (Mirrors old project's resume logic.)

The `anilistId != 0` guard from the old project (which skipped unlinked anime) is **dropped** — the new project's `main_id` works for all content.

### 2.5 Episode row UI — swipe + watched styling

**Swipe-to-toggle** (IM3: ONE direction for v1 — right = toggle):
- Use a **custom `pointerInput { detectHorizontalDragGestures }`** with a threshold + spring-back animation. (IM3: NOT `SwipeToDismissBox` — that's for dismiss, not toggle, and fights LazyColumn scroll.)
- Swipe right → if past 40% of width, toggle `isWatched` via `setUserMarkedWatched(episodeKey, !isWatched)`. If not past threshold, spring back.
- Left swipe: NO action for v1 (per IM3 — "both same" is bad UX). Future: left = remove from history (History screen only).
- Visual feedback during swipe: a background icon (check for watched, eye-off for unwatched) revealed as the card slides right. The card itself translates horizontally (not dismissed).
- Only consume horizontal drags — vertical drags pass through to the LazyColumn (no scroll conflict).

**Watched styling** (IM4: dropped blur — too expensive at thumbnail size on long lists):
- When `isWatched(episodeKey) = true`:
  - The thumbnail is rendered in **grayscale** via `ColorFilter.colorMatrix(ColorMatrix().apply { setSaturation(0f) })` on the `AsyncImage` (cheap — GPU-side).
  - **Reduced opacity** (alpha 0.5f) on the whole row for the "faded out" look.
  - A small "Watched" checkmark badge (overlaid on the thumbnail or beside the title).
  - (NO `Modifier.blur` — IM4: too expensive on a LazyColumn with 200+ watched episodes; invisible at 56×80dp anyway.)
- When unwatched: normal full-color, full opacity.
- Transition: animate the grayscale + alpha in/out over 200ms (`animateFloatAsState` on the saturation + alpha — CORE_RULES §22 buttery animations).

**Where:** the `EpisodeRow` composable in `DetailsScreen.kt` (line ~1378). Add `isWatched: Boolean` + `onToggleWatched: () -> Unit` params. Wire to the ViewModel (which observes `WatchProgressStore.observeByMainId(mainId)` + maps to per-episode `isWatched`).

### 2.6 Console logging (CORE_RULES §20)
Tag: `Anikuta:Core:WatchProgress` + `Anikuta:Feature:Details:EpisodeRow`.
- INFO: `save(episodeKey, position, duration, fraction)` — on each save.
- INFO: `auto-marked watched (85% threshold): episodeKey=...` — when the auto-rule fires.
- INFO: `manual toggle: episodeKey=... → watched/unwatched` — on user swipe.
- DEBUG: `observeByMainId(mainId) emitted N progress rows`.
- WARN: `save failed: ...` — on DB error.

---

## 3. History page (Feature 2)

### 3.1 Module structure
- **`:feature:anime-history:api`** + **`:feature:anime-history:impl`** (NEW — api/impl split per the project's pattern).
  - api: `HistoryRoute` NavKey + the route contract.
  - impl: `HistoryScreen` + `HistoryViewModel`.
- Reads from `:core:watch-progress` (`observeRecent`).

### 3.2 The UI (inspired by the old project — NOT copied)
From `06-old-history-analysis.md`, the old project's History UI:
- `LazyColumn` grouped by day-bucket (Today / Yesterday / This Week / Earlier).
- `CollapsingHeader` that animates 36sp→26sp on scroll.
- Row: portrait cover + title + "Episode N · watched Xh ago" pill + progress bar + watched-time aligned to bar endpoint.
- Trash icon → "Clear all" dialog.

**New project's HistoryScreen (improved):**
- Same day-bucket grouping (Today / Yesterday / This Week / Earlier) — use `java.time.LocalDate` (the old project's `DAY_OF_YEAR + YEAR * 365` is buggy for leap years).
- `CollapsingHeader("History")` with the same shrink-on-scroll animation.
- Row: portrait cover (56×80dp, 8dp rounded) + 16sp Bold title + episode pill + progress bar + "watched Xh ago" timestamp.
- **Improvements over old:**
  - Per-row swipe-to-delete (custom `pointerInput` horizontal drag → delete that episode's progress — IM3: same pattern as the episode-row toggle, not SwipeToDismissBox). Old project had no per-row delete.
  - Long-press → "Remove from history" + "Mark as unwatched" options.
  - Filter chips: All / In-Progress / Completed (old project had none).
  - Loading skeleton (old project just showed "Loading…").
  - Error state (old project masked errors as empty).
  - Tap a row → resume playback (navigate to WatchScreen with the episode + resume position).
- "Clear all" via trash icon in the header → red destructive AlertDialog (mirror old project's `0xFFE53935`).
- Empty state: "No history yet — start watching to build your history."

### 3.3 The logic
- `HistoryViewModel` collects `WatchProgressStore.observeRecent(limit = 100)`.
- Groups by day-bucket (computed from `lastWatchedAt`).
- For each entry: looks up the anime title + cover via `ContentRepository.getContentByMainId(mainId)` (single query per entry — the old project's N+1 pattern is acceptable at this scale, but we batch the lookups if perf is an issue).
- Sorts by `lastWatchedAt DESC`.
- Delete: `WatchProgressStore.delete(episodeKey)` + `ActivityTracker.track(HISTORY_DELETED)`.
- Clear all: `WatchProgressStore.deleteAll()` (NEW method — bulk delete).

### 3.4 Where it launches
- From the More screen's "History" row (currently a stub with empty onClick). Wire `onNavigate = { backstack.add(HistoryKey) }`.
- The `HistoryKey` is registered in `MainActivity`'s `when(currentKey)` dispatch.

### 3.5 Console logging
Tag: `Anikuta:Feature:History`.
- INFO: screen open, row tap (→ resume), delete, clear-all.
- DEBUG: `observeRecent emitted N entries`.

---

## 4. Updates section (Feature 3)

### 4.1 Module structure
- **`:core:updates`** (NEW) — the smart update engine: `UpdateEngine`, `UpdateStateRepository`, `UpdateChecker`, the WorkManager `UpdateCheckWorker`.
- **`:feature:updates:api`** + **`:feature:updates:impl`** (NEW — IM1: api/impl split for consistency with other features). api: `UpdatesRoute` NavKey + the tab-host contract. impl: `UpdatesScreen` (the tab host: Updates | Schedule) + `UpdatesViewModel` + `UpdatesTabStrip` + the Updates + Schedule sub-screens. (Both sub-screens live in the same impl module since they share the tab host.)

### 4.2 The UI (closely mirror the old project — the user called it "perfect")
From `07-old-updates-analysis.md`:
- `CollapsingHeader("Updates")` shrink-on-scroll.
- Centered-pill `UpdatesTabStrip` (Updates | Schedule segmented toggle).
- `PullToRefreshBox` wrapping the content.
- **Updates tab:** `LiveCheckCard` (animated, shows the currently-being-checked anime while a check is running) + "Last checked Xh ago" header + `LazyColumn` with `ListSectionHeader("New" / "Earlier")` + `UpdateRow`.
- **UpdateRow:** 56×80dp cover + 3dp primary vertical `NewBadgeBar` for fresh rows + 16sp Bold title + "N new episode(s)" + `AudioBadges` (SUB/DUB pills) + "Checked Xh ago".
- New rows: `primary.copy(alpha=0.10f)` background. Earlier rows: `surfaceVariant.copy(0.4f)`.
- Empty state: "No new episodes" + dynamic description (lastCheckedAt) + "Check now" action.
- Tap a row → navigate to the anime's details page (and `acknowledgeUpdate(mainId)` clears the "new" state).

### 4.3 The smart update engine (the KEY improvement over the old project)

The old project's flaw (from `07-old-updates-analysis.md`): on pull-to-refresh, it iterates EVERY anime in the library, sequentially, re-searches all trusted sources, no status filter, no release-date awareness, no throttle. **Performance-intensive + strains the sources.**

**New project's smart engine:**

**T1 — Status filter:** only check anime where `anime_update_state.status = 'RELEASING'`. `FINISHED`/`CANCELLED`/`NOT_YET_RELEASED` are skipped permanently (until status changes). This alone cuts the work dramatically (most libraries are mostly finished anime).

**T2 — next_check_at gating with backoff (CF4):** only check anime where `next_check_at <= now`. Computed as:
- If `next_airing_at` is known + this is the FIRST check after it: `next_check_at = next_airing_at + 1h` (check shortly after the expected airing).
- Else (no airing data, OR a check already fired after airing but found nothing): **backoff** — `next_check_at = now + backoff`, where backoff escalates: 1h → 2h → 4h → 8h → 24h (capped). Track the current backoff step per-anime (in-memory or a column on `anime_update_state` — FLAG: add `backoff_step INTEGER DEFAULT 0`?).
- When a new episode IS found: reset backoff to 0 + fetch the next airing node from `anime_update_state.next_airing_*` (populated by the Schedule engine — S4) + set `next_check_at = next_next_airing_at + 1h`.

This means: an anime that airs weekly is checked once after the expected airing. If the source hasn't uploaded yet (simulcast delay), it re-checks with escalating backoff (1h, 2h, 4h...) up to 24h — NOT every worker cycle. **This is the fix for the "strains the website" problem.**

**T3 — Self-improving via details-page visits (CF5):** when the user opens an anime's details page + the episode list refreshes, `DetailsViewModel` calls `UpdateEngine.onEpisodesRefreshed(mainId, latestEpisodeNumber, latestEpisodeDate, audioVariants)` →:
- Updates `last_known_episode_count = max(last_known_episode_count, latestEpisodeNumber)`.
- For any episode number > the old `last_known_episode_count`: **INSERT OR REPLACE into `episode_update` with `acknowledged = 1`** (iteration 3: `INSERT OR REPLACE` — if the worker already inserted with `acknowledged=0`, this overwrites it with `acknowledged=1`, resolving the race condition. The user already found it organically, so it's pre-acknowledged — no notification spam. CF5.)
- Updates `last_checked_at = now`, resets `backoff_step = 0`, recomputes `next_check_at` (from next airing or +24h fallback).
- So the next Updates check skips that anime (it was just checked).

**T4 — Per-episode audio_variant (sub/dub):** each new episode is stored with `audio_variant` parsed from the source (the old project's `SubDubParser` heuristic). An episode can have BOTH a sub + dub update row.

**T5 — WorkManager `UpdateCheckWorker` (CF6):**
- **Periodic worker** (default: every **1 hour** — was 6h; 1h is better for "notify shortly after airing" since the worker internally filters by `next_check_at <= now`, so a 1h cadence just means "check what's due every hour" without over-checking any single anime).
- **Constraints:** `NetworkType.CONNECTED` + `BatteryNotLow` (CF6 — without these, the worker runs offline and fails, or drains battery).
- **ExistingPeriodicWorkPolicy.KEEP** (CF6 — so user setting changes don't reset the timer).
- The worker queries `anime_update_state WHERE next_check_at <= now AND auto_update_enabled = 1 AND status = 'RELEASING'` (uses the partial index S2) + checks only those.
- **Also refreshes Schedule data** (M4 — unified AniList airing fetch: the worker calls the Schedule engine to refresh `next_airing_*` on `anime_update_state` for all library anime, batched 50 IDs). This keeps the due-check gating accurate.
- **Retention cleanup** (M9): the worker also runs `DELETE FROM episode_update WHERE acknowledged = 1 AND acknowledged_at < now - 7d`.
- **One-shot notify-after-airing** (CF6): when the Schedule engine detects a NEW airing (a `next_airing_at` that just appeared), enqueue a `OneTimeWorkRequest` with `initialDelay = next_airing_at + 1h - now` so the check fires shortly after the airing regardless of the periodic worker's cadence. This gives near-real-time notification for new episodes.
- Configurable interval in Settings (min: 1h, to avoid battery drain).

**T6 — Source-link cache:** the old project had a `SourceLinkStore` cache but didn't use it. We use it — once an anime is matched to a source, we cache the mapping + skip re-matching on future checks.

**T7 — Concurrency + throttle:** check at most 3 anime in parallel (Semaphore). Per-source rate limit: max 1 request per 2s per source. Backoff on error.

**The check flow (single anime):**
1. Get the anime's `source_id` from `anime_update_state` (or the source-link cache T6).
2. **M3 — source-uninstall handling:** if the source is not available (extension uninstalled), increment `consecutive_failures`. If `consecutive_failures >= 3`, set `auto_update_enabled = 0` + log WARN (user can re-enable in Settings after reinstalling the source). Skip the check.
3. Call `extensionSource.getEpisodeList(sAnime)` — get the current episode list.
4. Diff: `max(episode.number) > last_known_episode_count`? → new episodes found.
5. For each new episode: parse `audio_variant` (sub/dub via the old project's `SubDubParser` heuristic, default `'unknown'`). **M5 — suppress already-watched (iteration 3: check the derived `isWatched`, not just `completed`):** check `watch_progress` for this episode; if `isWatched = true` (i.e. `(completed=1 AND auto_mark_suppressed=0) OR user_marked_watched=1`), insert with `acknowledged = 1` (no notification — the user already watched it). Else insert with `acknowledged = 0` (shows in the New feed). Fire `ActivityTracker.track(NEW_EPISODE_FOUND)`.
6. Update `anime_update_state`: `last_known_episode_count = max`, `last_checked_at = now`, reset `consecutive_failures = 0`, recompute `next_check_at` (CF4: if new episodes found → from next airing; else → backoff).
7. **IM11 — actual-release detection:** for each new episode found, also update `episode_schedule.actual_at = EpisodeInfo.dateUpload` (the source's claimed upload time) when available, falling back to `discovered_at`. If `actual_at < scheduled_at`, prefer `scheduled_at`.
8. Emit the new updates to the `StateFlow<List<EpisodeUpdate>>` (reactive UI).

**M2 — AniList status-change handling:** the Schedule engine's airing fetch also returns the anime's `status` (RELEASING / FINISHED / etc.). The Schedule engine updates `anime_update_state.status` on every fetch. So if AniList says RELEASING today and FINISHED tomorrow, the Updates engine re-skips it automatically (the partial index S2 only includes `status = 'RELEASING'`).

**Pull-to-refresh behavior:** triggers a check of ALL due anime (next_check_at <= now) — NOT the whole library. If nothing is due, it's a no-op (show "Already up to date"). This is the performance fix.

### 4.4 Console logging
Tag: `Anikuta:Core:Updates` + `Anikuta:Core:Updates:Worker` (IM13) + `Anikuta:Feature:Updates`.
- INFO: check started (N anime due), check complete (M new episodes found), worker fired, worker constraints not met (deferred).
- DEBUG: per-anime check (source, episode count, diff result, next_check_at recomputed).
- WARN: source error / rate limit / source not available (M3: consecutive_failures=N).
- ERROR: check failed (with stack trace).

### 4.5 WorkManager infrastructure setup (CF6 prerequisite — do BEFORE Phase UP)

The project does NOT yet have WorkManager integrated (no `androidx.work` dep, no `Configuration.Provider`, `ActivityPruneWorker` is "Phase 4 future"). This plan is the first WorkManager consumer. Setup steps:

1. **Add the dependency** to `gradle/libs.versions.toml` + the relevant `build.gradle.kts` files:
   ```toml
   [versions]
   workRuntime = "2.10.0"  # latest stable as of Kotlin 2.2.0 / AGP 8.9.1
   [libraries]
   androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
   ```
   Add to `:core:updates` (the worker lives here) + `:app` (for the `Configuration.Provider`).

2. **Disable the default `WorkManagerInitializer`** in `AndroidManifest.xml` (so we can provide a custom `Configuration.Provider` for Koin-injected workers):
   ```xml
   <provider
       android:name="androidx.startup.InitializationProvider"
       android:authorities="${applicationId}.androidx-startup"
       android:exported="false"
       tools:node="merge">
       <meta-data
           android:name="androidx.work.WorkManagerInitializer"
           android:value="androidx.startup"
           tools:node="remove" />
   </provider>
   ```

3. **Implement `Configuration.Provider`** in `AnikutaApp` (the Application class):
   ```kotlin
   class AnikutaApp : Application(), Configuration.Provider {
       override val workManagerConfiguration: Configuration
           get() = Configuration.Builder()
               .setWorkerFactory(KoinWorkerFactory())  // custom factory for Koin-injected workers
               .build()
   }
   ```
   `KoinWorkerFactory`: a custom `WorkerFactory` that delegates to Koin for worker construction (so `UpdateCheckWorker` can inject `UpdateEngine`, `ScheduleEngine`, etc.). Defer to `ExistingWorkerFactory` pattern if simpler.

4. **Register the periodic worker** in `AnikutaApp.onCreate()` (after Koin start):
   ```kotlin
   val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.HOURS)
       .setConstraints(Constraints.Builder()
           .setRequiredNetworkType(NetworkType.CONNECTED)
           .setRequiresBatteryNotLow(true)
           .build())
       .build()
   WorkManager.getInstance(this).enqueueUniquePeriodicWork(
       "anikuta_update_check",
       ExistingPeriodicWorkPolicy.KEEP,  // CF6: KEEP so setting changes don't reset the timer
       updateCheckRequest,
   )
   ```

5. **One-shot notify-after-airing** (CF6): when the Schedule engine detects a NEW airing, enqueue:
   ```kotlin
   val delay = (nextAiringAt + TimeUnit.HOURS.toMillis(1)) - System.currentTimeMillis()
   if (delay > 0) {
       val oneShot = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
           .setInitialDelay(delay, TimeUnit.MILLISECONDS)
           .setConstraints(...)  // same CONNECTED + BatteryNotLow
           .build()
       WorkManager.getInstance(context).enqueueUniqueWork(
           "notify_after_airing_${mainId}_${episodeNumber}",  // CF6: unique name prevents duplicates
           ExistingWorkPolicy.KEEP,  // don't re-enqueue if already pending
           oneShot,
       )
   }
   ```

6. **Testing:** verify the worker fires (logcat: `Anikuta:Core:Updates:Worker`), respects constraints (turn off network → deferred), + the one-shot fires after airing.

---

## 5. Schedule section (Feature 4)

### 5.1 Module structure
- **`:core:schedule`** (NEW) — `ScheduleRepository`, `ScheduleEngine`, the AniList airing-API client.
- **`:feature:updates:impl`** (shared with Updates) — `ScheduleScreen` (list view) + `ScheduleCalendarScreen` (calendar view) + the tab strip. (IM14: SC-1 = list/calendar UI, independent of UP. SC-2 = actual-release detection, wired to UP's UpdateEngine — done in Phase SC-2 after UP.)

### 5.2 The list view UI (mirror old project)
From `08-old-schedule-analysis.md`:
- Day-grouped chronological (ascending): Today / Tomorrow / EEE, MMM d.
- Row: 56×80dp cover + 16sp Bold title + "Episode N" primary pill (left) + countdown (right).
- Live-ticking countdown ("in 14h 36m 24s") for Today/Tomorrow via 1s `LaunchedEffect`. Beyond: "MMM d at h:mm a".

### 5.3 The calendar view UI (mirror old project — the user called it "perfect")
- **Custom-built** calendar (no third-party lib) using `HorizontalPager` of month grids + `BoxWithConstraints` for per-month height.
- **Limits (exact):** `pageCount = 14, initialPage = 1` → page 0 = 1 month back, pages 1-13 = current + 12 months forward = 1 year forward. (Mirror the old project's mechanism — it's clean.)
- Day cells: `aspectRatio(1f)` + border. Primary-tinted bg for has-episodes. Today = highlighted.
- Multi-dot indicator for multiple episodes on one day (colored by each anime's AniList coverColor).
- Tap a date → `ModalBottomSheet(dragHandle = null, skipPartiallyExpanded = true)` → rows: 40×56dp cover + title + "Episode N · HH:mm".
- **Fix the old project's snarky bound-hit messages** ("What are you trying to do?") → replace with a neutral toast: "Can't go further back than 1 month" / "Can't go further forward than 1 year".
- **Fix the 12h/24h inconsistency** (list uses AM/PM, calendar sheet uses 24h) → standardize on 12h AM/PM everywhere (or a user preference — defer).
- Use `java.time.LocalDate` (not the old project's `DAY_OF_YEAR + YEAR * 365`).

### 5.4 The schedule engine (the "actual release" detection)

The user wants the ACTUAL release date, not just AniList's scheduled one. AniList might say "released" but the episode isn't actually out yet (or vice versa).

**Basic heuristic (for now — full ML later):**
- `scheduled_at` = AniList `airingAt` (the planned time).
- `actual_at` = null initially. When the `UpdateEngine` (§4.3, step 7) finds the episode on a source, it sets `actual_at = EpisodeInfo.dateUpload` (the source's claimed upload time) when available, falling back to `discovered_at`. If `actual_at < scheduled_at` (source early-release — rare), prefer `scheduled_at` to avoid "released before scheduled" confusion (IM11). (IM5: dropped the `confidence` column — source precedence at query time: `actual_at` over `scheduled_at`.)
- The Schedule UI shows `actual_at` when available (non-null), else `scheduled_at` (with a "~" prefix or a "scheduled" label).
- **Sub/dub:** separate `episode_schedule` rows per `audio_variant`. The Schedule shows both (a sub airing + a dub airing on different days — common for SimulDub).
- **S4 — unified airing data:** the Schedule engine's airing fetch ALSO populates `anime_update_state.next_airing_episode` + `next_airing_at` + `status` (M2) — so the Updates engine + Schedule engine share the same AniList data. No duplicate fetches.

**Refresh:** on Schedule screen open + pull-to-refresh (the old project had no pull-to-refresh on Schedule — add it) + in the WorkManager worker (M4 — unified fetch). Calls AniList airing API (`media(id_in:$ids){ status nextAiringEpisode{airingAt episode} airingSchedule(notYetAired:true){nodes{episode airingAt}} }`, chunked 50 IDs). 5-min in-memory cache. Persists to `episode_schedule` + `anime_update_state`.

**Timezone:** AniList returns UTC epoch. Display in device local tz (the old project's approach). No "original broadcast timezone" toggle for now (defer).

### 5.5 Console logging
Tag: `Anikuta:Core:Schedule` + `Anikuta:Feature:Schedule`.
- INFO: fetch started (N anime), fetch complete (M schedule entries), calendar page changed.
- DEBUG: per-anime airing data, actual-release detection.
- WARN: AniList API error / rate limit.

---

## 6. Centralized tracking system (Feature 5)

### 6.1 Module structure (IM2: no facade — direct stores)
- **`:core:watch-progress`** — `WatchProgressStore` (SQLDelight impl, §2).
- **`:core:activity-tracker`** — `ActivityTracker` (D-045, already implemented). Append-only event log.
- **`:core:ratings`** (NEW, small) — `RatingStore` for `user_rating`.
- **No `TrackingRepository` facade** (IM2: it was pure indirection). Backup enumerates the tables directly (§6.3). Each store is independent + modular. Consumers inject the specific store they need.

### 6.2 What's tracked (for future statistics)
- **Watch events:** `activity_event` (WATCH_START, WATCH_PAUSE, WATCH_COMPLETE, EPISODE_SWITCH) — already via `ActivityTracker`.
- **Watch progress:** `watch_progress` (position, duration, completed, watch_count, first_watched_at) — via `WatchProgressStore`.
- **Ratings:** `user_rating` — via `RatingStore` (NEW, small).
- **Genres:** derived from the anime's `genre` field (already in `content` / `anilist_details`). No separate table — JOIN at query time for stats.
- **Library changes:** `activity_event` (LIBRARY_ADD, LIBRARY_REMOVE) — already tracked.
- **Update finds:** `activity_event` (NEW_EPISODE_FOUND) — via `ActivityTracker` in the UpdateEngine.

### 6.3 Backup-friendliness
All tables key off `main_id` (stable UUID) + `episode_key` (stable per content). **Backup = dump these tables to JSON. Restore = INSERT OR REPLACE.** The `main_id` survives a restore on a new device (it's a UUID, not derived from anything mutable). This is the key advantage over the old project.

**Tables included in backup:** `watch_progress`, `episode_update`, `anime_update_state`, `episode_schedule`, `user_rating`. (IM10: `activity_event` is EXCLUDED — `session_id` is ephemeral (UUID per process restart), meaningless after restore. The events log is regenerable from `watch_progress` for stats purposes.)

A future `:core:backup` module (Phase 6) will: enumerate the backup-eligible tables → dump to a `.anikuta` zip (mirror old project's format) → restore by reading + UPSERTing. The schema is designed for this — no ephemeral state mixed in.

### 6.4 Console logging
Tag: `Anikuta:Core:WatchProgress` + `Anikuta:Core:ActivityTracker` + `Anikuta:Core:Ratings`.
- INFO: rating set, history cleared, watch event tracked.
- DEBUG: per-store writes.

---

## 7. Console logging (overall, CORE_RULES §20)

Per CORE_RULES §20, filtered + toggleable. Tags by module:
- `Anikuta:Core:WatchProgress` — watch progress saves, auto-mark, manual toggle.
- `Anikuta:Core:Updates` — update check engine.
- `Anikuta:Core:Updates:Worker` (IM13) — WorkManager worker lifecycle.
- `Anikuta:Core:Schedule` — schedule fetch + actual-release detection.
- `Anikuta:Core:ActivityTracker` — event tracking.
- `Anikuta:Core:Ratings` — rating store.
- `Anikuta:Feature:Details:EpisodeRow` — swipe, toggle.
- `Anikuta:Feature:History` — history screen.
- `Anikuta:Feature:Updates` — updates screen.
- `Anikuta:Feature:Schedule` — schedule screen.

All go through the existing `Logger` wrapper (`:core:common`). Levels: VERBOSE (flow tracing), DEBUG (per-query), INFO (user actions), WARN (recoverable), ERROR (exceptions). Toggleable via `BuildConfig.DEBUG` + a runtime toggle in Settings (already exists per D-045).

**Logcat filter for the whole feature set (pasteable into Android Studio):**
```
tag:Anikuta:Core:WatchProgress | tag:Anikuta:Core:Updates | tag:Anikuta:Core:Updates:Worker | tag:Anikuta:Core:Schedule | tag:Anikuta:Core:ActivityTracker | tag:Anikuta:Core:Ratings | tag:Anikuta:Feature:Details:EpisodeRow | tag:Anikuta:Feature:History | tag:Anikuta:Feature:Updates | tag:Anikuta:Feature:Schedule
```

---

## 8. Implementation phases (suggested order)

The features have dependencies. Suggested order (IM14: SC split into SC-1 independent + SC-2 after UP):

**Phase WP (Watch Progress) — foundation, do first:**
0. **🚨 `episode_key` standardization** (§1.9) — prerequisite; change WatchScreen/EpisodeMetadataFetcher/DetailsViewModel to `${mainId}|${padded_5_digit}` + the one-time migration in `onOpen`.
1. Extend `watch_progress` schema (§1.1) + migration (§1.8).
2. `SqlDelightWatchProgressStore` (§2.2) + Koin switch.
3. Player save-on-pause + save-on-episode-switch + resume logic (§2.4).
4. Episode row swipe + watched styling (§2.5).
5. Wire `DetailsViewModel` to observe watch progress per episode.

**Phase HI (History) — depends on WP:**
1. `:feature:anime-history:api` + `:impl`.
2. `HistoryScreen` + `HistoryViewModel` (§3.2, §3.3).
3. Wire from More screen.

**Phase UP (Updates) — independent of WP/HI:**
0. **WorkManager infrastructure setup** (§4.5) — add dep, disable default initializer, `Configuration.Provider`, `KoinWorkerFactory`.
1. `:core:updates` — `UpdateEngine`, `UpdateStateRepository`, `UpdateChecker`.
2. `episode_update` + `anime_update_state` schema (§1.3, §1.4) + migration.
3. `:feature:updates:api` + `:impl` — `UpdatesScreen` (Updates tab) + `UpdatesViewModel`.
4. WorkManager `UpdateCheckWorker` (CF6: constraints, KEEP, 1h, one-shot notify-after-airing).
5. Details-page self-improving hook (§4.3 T3 — CF5: INSERT OR REPLACE with acknowledged=1).
6. M5 suppress already-watched (check derived isWatched) + M3 source-uninstall handling.

**Phase SC-1 (Schedule — list + calendar, INDEPENDENT of UP):**
1. `:core:schedule` — `ScheduleRepository`, AniList airing client.
2. `episode_schedule` schema (§1.5) + migration.
3. `:feature:updates:impl` — `ScheduleScreen` (list) + `ScheduleCalendarScreen` (calendar) + tab strip.
4. S4: Schedule fetch populates `anime_update_state.next_airing_*` + `status` (M2).

**Phase SC-2 (Schedule — actual-release detection, AFTER UP):**
1. Wire the UpdateEngine's episode-find (§4.3 step 7, IM11) to set `episode_schedule.actual_at`.
2. Schedule UI shows `actual_at` when available, else `scheduled_at`.

**Phase TR (Tracking) — mostly done, additions (iteration 4: per-episode ratings):**
1. `user_rating` schema (per-anime) + `RatingStore` (§1.6, §6.2).
2. `user_episode_rating` schema (per-episode, NEW iteration 4) + `EpisodeRatingStore` (§1.10).
3. `:core:ratings` module hosts BOTH stores (or split into `:core:ratings` + `:core:episode-ratings` if they diverge — start unified).
4. (IM2: no facade. Backup enumerates tables directly — deferred to Phase 6.)

**Phase NOTIF (Notification system — NEW iteration 4, after UP + SC):**
1. `:core:notifications` module — `NotificationConfigStore`, `NotificationManager`, `NotificationWorker`.
2. `notification_config` schema (§14 — sub-agent-designed).
3. Settings UI for per-content/per-episode/sub/dub config + 3 trigger types.
4. Wire to `UpdateEngine` (on-new-episode) + `ScheduleEngine` (on-schedule-arrival).

**Phase CW (Continue Watching — logic only, iteration 4):**
1. `getContinueWatching(limit)` query in `watch.sq` (§1.1).
2. `observeContinueWatching(limit): Flow<List<WatchProgress>>` in `WatchProgressStore`.
3. (UI placement deferred — the user will decide later where this appears.)

**Phase INT (Integration + More screen wiring):**
1. Wire More screen: History row → `HistoryKey`, Updates row → `UpdatesKey` (which hosts both Updates + Schedule tabs). (Q1 resolved: Updates stays under More — NOT promoted to bottom-nav.)
2. Console-logging audit (all new code uses the right tags).
3. S6: library-change hooks (insert/delete `anime_update_state` rows on library add/remove).

---

## 9. Open questions for the user — ALL RESOLVED (iteration 4)

*(All 5 questions answered by the user. Documented here for the record.)*

1. **Bottom-nav promotion:** ✅ **RESOLVED** — Updates stays under **More** (NOT promoted to bottom-nav). Matches the old project.
2. **Auto-mark threshold configurability:** ✅ **RESOLVED** — **user-configurable** (default 85%, in Settings). NOT hardcoded. Added a `WatchPreferences` field + a Settings UI slider (50%-95%). The `SqlDelightWatchProgressStore.save()` reads the threshold from preferences at save time.
3. **Schedule calendar back-limit configurability:** ✅ **RESOLVED** — **fixed** (1 month back + 1 year forward). NOT configurable.
4. **Continue Watching placement:** ✅ **RESOLVED** — **logic only for now** (the query + store method). UI placement deferred to a later decision. Added Phase CW (logic-only).
5. **Updates system notification:** ✅ **RESOLVED** — **yes, a full-fledged notification system**. Per-content + per-episode + sub/dub config, 3 trigger types (on-schedule-arrival, on-watchable, on-immediate-release). Added Phase NOTIF + §14 (sub-agent-designed).

---

## 10. Risks + mitigations

- **Swipe + LazyColumn scroll conflict:** (IM3) custom `pointerInput { detectHorizontalDragGestures }` only consumes horizontal drags; vertical passes through. No conflict.
- **Grayscale performance:** `ColorFilter.colorMatrix` is GPU-side, cheap even on 200+ rows. (IM4: dropped the expensive `Modifier.blur`.)
- **WorkManager battery:** (CF6) `NetworkType.CONNECTED` + `BatteryNotLow` constraints, 1h cadence but only checks due anime (next_check_at <= now), user-configurable. The one-shot notify-after-airing is minimal (one check per new airing).
- **AniList rate limit:** airing API chunked at 50 IDs, 5-min cache, fetched on Schedule open + in the worker (not app open). S4: shared between Updates + Schedule.
- **Schema migration:** (CF2) additive ALTER TABLE via `DatabaseDriverFactory.onOpen` (idempotent `hasColumn` checks). Edit `.sq` for codegen. Clear-app-data test + upgrade test required. No data loss.
- **SQLite FK on existing table:** (CF3 note) can't add FK via ALTER TABLE — `watch_progress.main_id` enforced at app level for existing installs, FK in CREATE TABLE for fresh installs. Orphans cleaned by `clearByMainId` on library-remove.
- **Source churn during update check:** (M3) 3-strike rule → `auto_update_enabled = 0` after 3 consecutive failures, user re-enables in Settings.
- **`actual_at < scheduled_at` edge case:** (IM11) prefer `scheduled_at` to avoid UI confusion.
- **NULL audio_variant dedup:** (CF7) `NOT NULL DEFAULT 'unknown'` ensures UNIQUE constraint works.

---

## 11. What's NOT in this plan (explicitly deferred)

- Full statistics UI (Phase 6 — the `activity_event` data is collected now, stats UI later).
- Backup/restore UI (Phase 6 — schema is backup-friendly, UI later).
- MAL/TMDB tracker support (AniList only for now — the provider registry supports adding them later).
- Full-fledged actual-release ML detection (basic heuristic for now).
- Per-episode ratings (per-anime only for now).
- "Original broadcast timezone" toggle (local tz only for now).
- Promoting Updates to a top-level tab (stays under More unless user decides otherwise — Q1).
- Updates system notifications (deferred — Q6).

---

## 12. Next steps

1. **Present the final plan + the 5 open questions** to the user (this iteration — done after this doc).
2. **On user confirmation** → implement phase by phase (WP → HI → UP → SC-1 → SC-2 → TR → INT), with sub-agent code review per phase (CORE_RULES §8 — no local builds).

---

## 13. Module wiring checklist (iteration 3 — for the implementer)

### 13.1 New Gradle modules (add to `settings.gradle.kts`)
```kotlin
include(":core:updates")
include(":core:schedule")
include(":core:ratings")
include(":feature:anime-history:api")
include(":feature:anime-history:impl")
include(":feature:updates:api")
include(":feature:updates:impl")
```

### 13.2 New Koin modules (register in `AnikutaApp.startKoin { modules(...) }`)
- `updatesModule` (in `:core:updates`) — `UpdateEngine`, `UpdateStateRepository`, `UpdateChecker`, `UpdateStore`.
- `scheduleModule` (in `:core:schedule`) — `ScheduleRepository`, `ScheduleEngine`, AniList airing client.
- `ratingsModule` (in `:core:ratings`) — `RatingStore`.
- (The `:feature:*:impl` modules register their own ViewModels via `viewModel { ... }`.)

### 13.3 NavKey definitions (in the feature api modules)
- `HistoryKey` — `@Serializable object HistoryKey : NavKey` in `:feature:anime-history:api`.
- `UpdatesKey` — `@Serializable object UpdatesKey : NavKey` in `:feature:updates:api` (hosts both Updates + Schedule tabs).

### 13.4 MainActivity dispatch (add to the `when(currentKey)` block)
- `is HistoryKey -> HistoryScreen(onBack = pop)`
- `is UpdatesKey -> UpdatesScreen(onBack = pop)` (internally hosts the Updates | Schedule tab strip)

### 13.5 More screen wiring (Phase INT)
- History row: `onNavigate = { backstack.add(HistoryKey) }` (currently a stub).
- Updates row: `onNavigate = { backstack.add(UpdatesKey) }` (currently a stub).

### 13.6 Module dependency graph (for S4 — unified airing data)
- `:core:updates` depends on: `:core:database`, `:core:common`, `:core:content`, `:core:source-api`, `:core:activity-tracker`, `:core:watch-progress` (for M5 isWatched check).
- `:core:schedule` depends on: `:core:database`, `:core:common`, `:core:content`, `:core:anilist`, `:core:network`.
- **Column ownership (non-overlapping):**
  - `:core:schedule` WRITES to `anime_update_state`: `next_airing_episode`, `next_airing_at`, `status` (from AniList airing fetch — S4/M2).
  - `:core:updates` WRITES to `anime_update_state`: `last_checked_at`, `next_check_at`, `last_known_episode_count`, `auto_update_enabled`, `consecutive_failures`, `backoff_step` (from the update check).
  - Both modules READ from `anime_update_state`. No write conflicts (different columns).
- `:core:updates` calls `:core:schedule` to trigger airing refresh in the worker (M4). So `:core:updates` depends on `:core:schedule` (one-way).

### 13.7 WorkManager (§4.5)
- Add `androidx.work:work-runtime-ktx` to `:core:updates` + `:app`.
- `KoinWorkerFactory` in `:app` (or `:core:common`).
- `AnikutaApp` implements `Configuration.Provider`.
- Disable default `WorkManagerInitializer` in manifest.
- Register the periodic `UpdateCheckWorker` in `AnikutaApp.onCreate()`.

### 13.8 New modules added in iteration 4
- `:core:notifications` (Phase NOTIF) — `NotificationConfigStore`, `NotificationManager`, `NotificationWorker`.
- `:core:ratings` hosts both `RatingStore` (per-anime) + `EpisodeRatingStore` (per-episode).
- (No new feature modules for NOTIF — the settings UI lives in the existing `:feature:extensions-settings` or a new `:feature:settings` — decide in Phase NOTIF.)

---

## 14. Notification system design (Phase NOTIF — sub-agent-designed, pending)

> **Status:** PENDING — a sub-agent is designing this section in parallel. The user's requirements:
> - Per-content config (notify for anime X, not for anime Y).
> - Per-episode config (notify for EP 5 of anime X specifically).
> - Sub/dub config (notify for sub releases, dub releases, or both — per content).
> - 3 trigger types:
>   1. **On schedule arrival** — notify when the AniList airing time is reached (the episode is scheduled).
>   2. **On watchable** — notify when the episode is actually available on a source (UpdateEngine found it).
>   3. **On immediate release** — notify immediately when the release schedule is reached (same as #1? or a distinct "don't wait for watchable" mode?).
> - Global settings (master toggle, quiet hours, etc.).
> - The system must be modular + customizable + future-proof.
>
> The sub-agent will design: the `notification_config` schema, the `NotificationConfigStore` interface, the `NotificationManager` (posts system notifications), the trigger wiring (how UpdateEngine + ScheduleEngine call it), the settings UI structure, + the interaction with the existing `DownloadNotificationManager` (2 channels — do we add more?).
>
> **This section will be filled in by the sub-agent's design + integrated before Phase NOTIF implementation begins.** Phases WP/HI/UP/SC-1/SC-2/TR can proceed in the meantime (NOTIF depends on UP + SC).

---

*This is iteration 4 of the plan (user decisions + new requirements integrated). All 5 open questions resolved. Implementation begins — Phase WP first. The notification system (§14) is being designed by a sub-agent in parallel.*
