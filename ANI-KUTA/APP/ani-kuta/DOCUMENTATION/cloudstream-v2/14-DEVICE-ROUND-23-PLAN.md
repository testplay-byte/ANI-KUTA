# 14 — DEVICE ROUND 23 (Task 63): THE LIBRARY PERFORMANCE DEEP PASS + 8 FIXES

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: v0.4.10/75 (Task 62, CI green 33535540261).
> The v0.4.10 device round CONFIRMED: the post-import crash fix, the plugin↔repository
> linkage, the Format-sources menu, the randomization retrain. Do not regress those.

## The device report (v0.4.10)

1. **Library performance — WORSE than v0.4.9.** Scrolling into never-loaded cover
   areas drops to ~5–6 FPS with heavy jitter; scrolling back up into "never shown"
   areas jitters even after a full bottom-load; continuous scrolling jitters; switching
   to an unloaded category lags for a long time. Goal: **gallery-grade** — smooth
   scroll everywhere, covers loading in the background like a phone gallery.
2. Category chips: auto-scroll should **center the selected chip** when possible
   (edge items excluded — they clamp).
3. Downloads tag: remove the parentheses; **bold the episode count**, rest normal.
4. NEW: background work must show **status in the notification bar** (e.g. "checking
   anime X for new episodes", progress, completion) so the user is never in the blind.
5. REMOVE: Developer tools page + ALL related code + console logging code (clean
   release experience).
6. NEW: Profile **Genres radar filter** — options right of the heading (All +
   per-category), default All, remembered across visits.
7. CS browse page: **category content mixing** + **duplicate category names**.
8. Watch Activity: day-of-week labels at bottom-left move **up a bit**.
9. Database review (confirm health).

## Root causes found (research, 2026-09-02)

### Library performance — three compounding causes

**R1 — the app-wide 2-request image cap (`ImageLoaderFactory`, Task 61).**
`maxRequests = 2` (a SEARCH-page round-21 spec) applies to EVERY Coil image in the
app. Library fling = dozens of cover requests; only 2 run at a time, FIFO. Cells
that scroll out of the LazyGrid cache window are disposed → their requests are
**cancelled**; re-entering cells re-**enqueue at the queue tail**. Net: visible
covers starve, requests thrash (enqueue/cancel churn = the user's "it constantly
tries to load the cover images"), a screenful of covers takes seconds. Category
switch to an unloaded set = the same starvation at full scale.
Library-side per-cell code is already well-tuned (crossfade(false), RGB_565,
reveal-once fades in draw-phase, off-main accent palette, batched DB, H2/M3 fixes) —
the cap is the dominant fixable regression.

**R2 — `selectCategory` re-runs the FULL DB pipeline.** Every category tap →
`reloadFromCache()` → `loadLibraryImpl()` = 7 batch queries + 653-entry
reconstruction + badge enrichment + filter/sort (~100–300 ms) + a brand-new list
instance (grid rebuild). A category switch is a pure in-memory re-filter of data
the VM already holds.

**R3 — chips snap to the start edge** (`scrollToItem`) — no centering.

### Plan A — library performance (gallery-grade)

- **A1.** `ImageLoaderFactory`: concurrency `2 → 8` total / `6` per host. Keeps the
  FIFO + visible-first ordering (LazyGrid composes visible cells first) and the
  round-21 spirit (bounded, complete-what-you-started — OkHttp never preempts),
  but 4× the throughput so visible cells complete before flinging past. Loader-level
  `crossfade(true)` STAYS (search cards rely on it; library requests already
  override with `crossfade(false)`).
- **A2.** `LibraryViewModel`: keep a full-set `allEntries` cache (all categories,
  enriched). `selectCategory` becomes an in-memory re-filter on `Dispatchers.Default`
  (~1–2 ms) + single emission (D-290 discipline preserved: `masterEntries` stays the
  category view the query/sort pipeline re-derives from). Full DB reload stays for:
  init, pull-to-refresh, silent resume refresh.
- **A3.** Chips auto-center: snap+center on open (no animation), animate-to-center
  on manual switches; negative offset centers the item; LazyRow clamps edges
  ("excluding the left and right side ones" — automatic).

### Plan B — downloads tag (DownloadedFilesScreen)

`"(5 Episodes Downloaded)"` → `buildAnnotatedString` with a **bold number span**
and plain-text tail, no brackets; pill Surface/color/size unchanged.

### Plan C — background-task status notifications

The engine already exposes `checkProgress: SharedFlow<CheckProgress(current, total,
mainId, title, coverUrl)>` and `checkDueAnime(): Int`. Pattern: the established
`NotificationSender` seam (interface in `:core:updates`, implementation + Koin in
the app, engine constructor param).
- New `UpdateProgressNotifier` interface (2 methods) in `:core:updates`; engine
  constructor param (defaulted null — tests unchanged); calls at the existing
  `tryEmit` points + `onFinish(newCount)` at the end of `checkDueAnime` → covers
  BOTH the periodic worker AND the manual Updates-screen check.
- Impl in the app module: channel `anikuta_update_progress` (IMPORTANCE_LOW,
  no badge, `setOnlyAlertOnce`, ongoing, `setProgress(current, total)`), text
  "Checking <title> — n/total", title "Checking for new episodes"; best-effort
  cover large-icon; **throttle ≥ 500 ms between posts** (first/last always);
  finish → "Episode check complete — N new episodes" (autoCancel). POST_NOTIFICATIONS
  already handled; notify wrapped in the SecurityException-safe pattern.
- SmartReleaseCheckWorker (single-anime checks) does NOT post progress (noise).

### Plan D — developer tools + console logging removal

Full inventory (from research): delete `DebugSettingsScreen.kt`, `DebugPreferences.kt`
(+ DI), `ResolverDebugReport.kt` (+ 8-test file), `ConsoleLogsScreen.kt` (+ settings
row + nav key), `EpisodeListDumper.kt` (+ 3 DetailsViewModel call sites), the
`feature:debug-bubble` module + `core/debug-api` + dual debug/release source sets
(debug-only, unreachable after the page goes) + all gated affordances in
ResolverSheet / PlayerSheets(QualitySheet) / CsSourceListUi / CsResolveSheet
(debug params, copy circles, raw-URL 10sp lines, report builders, 3 CsSourceListUi
tests) + SettingsScreen rows/params + MainActivity keys/branches +
DetailsScreen/WatchScreen report-context args. `serverNameOf`/`groupServers` and the
CS long-press URL copy STAY (user-facing). Logger: keep `w`/`e` (crash diagnostics),
gate `i`/`d` to debug builds (clean release logcat); RingLogBuffer removed if
orphaned.

### Plan E — Profile genres radar filter

Options = `[All] + library categories` (the user listed AniList-status names, which
are exactly their imported category names — "or based on the other ones if there is
any other category" = custom categories too). Genre counts recompute per filter from
the same `content_genre` junction, restricted to that category's mainIds
(VM-side, off-main); `onGenreClick`'s sheet applies the same filter. Persist the
selected category NAME (`profile_genre_filter` via PreferenceStore), default "All",
re-applied on entry; falls back to All if the category is gone.

### Plan F — CS browse categories (mixing + duplicates)

- **F1 identity:** `CsBrowseSection` gains a defaulted `shelfIndex: Int = -1` — the
  ORIGINAL `provider.mainPage` index, assigned BEFORE empty-shelf compaction.
  Rows/subpages/navigate all carry the ORIGINAL index → `browseShelf` resolves the
  right shelf even when shelves were dropped (the mixing fix).
- **F2 duplicates:** same-title sections (case-insensitive) MERGE in
  `browseSections` (concat + `distinctBy{url}` + cap) keeping the first shelf's
  index — no more 2–3 identically-named rows, and the merge is what gets cached.
- **F3 cache:** `put()` preserves the previous snapshot's `display` (restore
  validates); `CsBrowseDisplayRow` gains a defaulted `title` used for validation —
  restore falls back to a fresh shuffle on any mismatch (size / index / title).
- F4: LazyColumn keys use the unique shelfIndex.

### Plan G — Watch Activity day labels

The left gutter labels shift up (`offset(y = -4.dp)` + equalizing bottom padding) —
"shows a little bit up" without breaking the row alignment math.

### Plan H — database review + hardening

Review verdict: healthy (D-285 batched reads; category/main_id indexes exist).
Two cheap hardening wins shipped: covering index `idx_library_item_main_added
(main_id, added_at)` (the GROUP BY max(added_at) join) and a transaction-wrapped
batch for `deleteCategoryAndMoveToDefault` (kills the per-row INSERT loop).
Backlogged (documented, not churned): `getAllContentDetails` column projection.

### Version + delivery

`0.4.11/76`. Phases as separate commits → static verification (brace/import sweeps,
subagent full-diff reviews) → push → CI green → tag `v0.4.11` + release → 2 ntfy.sh
notifications → device-round test checklist.

## Invariants (unchanged)

- Aniyomi stack: additive/display-layer only; engine/resolver/MPV/download byte-untouched.
- CI is the compiler of record; no local Gradle/SDK.
- Reveal-once cover system, draw-phase fades, D-285 batching, H2/M3 fixes stay.
