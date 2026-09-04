# 16 — DEVICE ROUND 25 (Task 65): THE SEVEN FINDINGS OF THE v0.4.12 ROUND

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: v0.4.12/77
> (round 24 shipped, CI green 33638124850, Release live). The round-24
> device round CONFIRMED the chips, the genres radar, the downloads tag +
> two-step delete state machine, and the CS subpages' general handling.
> This round fixes the seven findings — root-caused from the user's
> logcat + crash report, each item its own commit, CI-verified.

## The device report (v0.4.12)

1. **Library performance, round three**: 3-per-row is smooth; the
   LIST/"per column" mode jitters with heavy frame drops while scrolling
   (logcat captured: 50–268ms frames, many skipped frames, a 16MB
   concurrent GC during one fast fling AFTER all covers were loaded);
   category switching drops performance the same way.
2. **Crash** (crash report attached):
   `IllegalArgumentException: Key "d30475ed-…_9_1788360360000" was already
   used` in a LazyColumn during a draw pass.
3. **Downloads**: the two-step delete's state switch works, but the icon
   appears to grow ~3x; and the deletion itself is instant with no
   animation (wants: an animation, then the row slides out smoothly).
4. **Heatmap weekday labels**: still slightly cut off at the bottom
   (better than v0.4.11 but not fixed); user suggests lifting the labels
   so they align with the heatmap rows.
5. **Details page metadata**: rating · year · status · episode count share
   ONE cramped row with limited space — wants the name at top, then the
   facts as separate rows (max four, with icons, hidden when unavailable).
6. **CS browse**: content from one category bleeding into other
   categories; wants a phased loading system (categories first, then
   contents, a beautiful loading animation meanwhile, then the results,
   covers last).
7. **Update notifications**: no sound; the finish notification said only
   "Episode check completed / Checked one anime / No new episodes" (no
   anime names, no next steps); the Settings page has no visible Updates
   entry; the history (found under Settings → Notifications) shows 24h
   time, no results/covers, and rows don't navigate to Details; wants a
   dedicated "Update Check History" button in Debug options and a special
   next-check entry (timer + which content + how) at the top of the
   history.

## Item 2 — the crash (D-381)

**Root cause (exact match):** the crash key
`{uuid}_{episodeNumber}_{scheduledAt}` is ScheduleListContent.kt's
LazyColumn key. The schedule queries
(`getUpcomingSchedule` / `getTodayAiredSchedule` / `getScheduleForDay`)
INNER JOINed `library_item` — which is **one row PER CATEGORY**
(`idx_library_item_unique` = `(main_id, category_id)`), and the user's
anime `d30475ed-…` is in 2+ categories → every schedule row was returned
TWICE → identical keys → crash.

**Fix (three layers):**
1. ROOT — the three queries are now `EXISTS` semi-joins (at most one
   match, no fan-out). No schema change, no migration.
2. Defensive — ScheduleViewModel/UpdatesViewModel dedupe the composite
   BEFORE enrichment (also saves double DB lookups).
3. Latent-bug hardening — UpdatesScreen keys include `audioVariant`
   (`episode_update` deliberately stores SUB and DUB of the same episode
   as two rows; the old `"mainId_episodeNumber"` key collided → the same
   crash class), and the VM dedupes the display composite as a second
   guard.

## Item 1 — library list-mode scroll jank (D-382)

**Root cause (static, from the user's logcat):** `DetailTagRow` composed a
full nested **LazyRow PER ROW** — SubcomposeLayout +
`rememberSaveable`-backed list state + a gesture/scroll node + an item
provider, with each pill subcomposed during MEASURE — just to render 2–5
fixed pills. The grid modes compose nothing of the sort. That is the only
structural difference between the smooth grid and the janky list mode;
the 50–268ms frames + the 16MB GC churn per fling match (the lazy-layout
machinery + per-recomposition tag-list/string allocations multiplied
across the rows crossing the viewport).

**Fix:** a plain `Row + horizontalScroll` (single measure pass, one
saveable, one gesture node, zero subcomposition — same visuals, an order
of magnitude cheaper) + the `detailTags` list built ONCE inside
`remember(anime, config, badgeColors, colorScheme)` (LibraryEntry /
BadgeColorScheme / ColorScheme are data classes with structural equals —
safe keys). Category-switch jank drops proportionally (the in-memory
re-filter from round 24 is intact; the remaining cost was composing the
heavy rows).

## Item 3 — downloads delete UX (D-384)

- The "~3x size jump" is NOT a coded size change — both states render
  IconButton 32dp + Icon 16dp. It's the GLYPH footprint:
  `Icons.Filled.DeleteForever` fills its 24dp viewport edge-to-edge while
  the plain `Delete` glyph occupies ~60% → the instant swap READ as a 3x
  jump. **TwoStepDeleteIcon**: a fixed frame + a 150ms
  scale/fade `AnimatedContent` morph + a 0.65x armed-glyph scale
  normalization.
- The exit choreography: confirming a delete runs a 110ms settle pulse →
  a 240ms slide-out (towards the END) + fade → only THEN the VM delete
  fires (graphicsLayer translation/alpha/scale — draw phase only). The
  card's `animateContentSize` closes the freed space; the LazyColumn
  items gained `Modifier.animateItem()` so the cards below glide up.
  Applied to BOTH the per-episode rows and the delete-all card.

## Item 4 — heatmap weekday labels (D-385)

**Root cause:** the 8sp `Text` set NO `lineHeight` → it inherited the
ambient Material `bodyLarge` line metrics (24sp!) → an ~8dp glyph centered
inside a 24dp(+) line box; the slot's fixed 14dp max-height +
`TextOverflow.Clip` shaved the bottom ~3dp of every letter. The round-24
taller box was necessary but could never fix a line-box/slot mismatch.
**Fix:** explicit `lineHeight = 10.sp` + a −1dp optical lift (each label's
center lands EXACTLY on its cell row's center — slot center = pitch+7 vs
cell center = pitch+6) + the same lineHeight hardening on the month
labels.

## Item 5 — details metadata (D-386)

`DetailsMetaColumn` replaces the single dot-line: up to FOUR icon rows
under the title, in the spec's order — release year (`CalendarMonth`) →
rating (`Star`, with the AniList 0–100 vs CS 0–10 normalization) → status
(`Adjust` = releasing / `CheckCircle` = finished / `Schedule` = other) →
total episodes (`Movie`). Each row hidden entirely when the fact is
unavailable; four rows ≈ 84dp fits the 150dp cover-height budget of the
banner's bottom row.

## Item 6 — CS browse bleeding + phased loading (D-387)

**The bleeding root cause:** many CloudStream providers IGNORE
`MainPageRequest.name` and return the WHOLE home response (EVERY named
`HomePageList`) for any shelf request. The old
`response.items.flatMap { it.list }` flattened ALL lists regardless of
their names → every category row got the same mixed content.
**Fix:** `shelfLists(response, shelfName)` — the name-matching lists
(case-insensitive, trimmed), falling back to the single list, then to all
lists (providers whose list names never match the shelf names keep the
pre-fix reading). Applied in BOTH the browse pipeline and `browseShelf`
(the category subpages). PLUS: `CloudstreamBrowseCache.put()` now carries
the previous snapshot's `display` forward — a stale-cache background
refresh was nulling the persisted arrangement → the re-read failed
validation → a fresh shuffle re-arranged rows WHILE the user watched (the
other half of the perceived bleeding).

**The phased pipeline** (`browseSectionsProgressive`, channelFlow —
thread-safe `send` from the parallel fetchers):
1. `Categories` — the pre-merged skeleton (titles + original shelf
   indexes, same title-merge as the final result) from
   `provider.mainPage`, BEFORE any network → the UI renders the full
   category structure with shimmer rows instantly;
2. `Section` — per-shelf content the MOMENT its response lands (parallel,
   per-shelf failure tolerated, the slowest shelf no longer gates the
   page);
3. `Complete` — the canonical final result (shelf order restored,
   same-title merge, cap, cache) → the ViewModel's display arrangement
   (restore / smart-shuffle + persist) is byte-identical with the
   pre-rework behavior.

UI: `ExtensionBrowseLoading` — the skeleton + shimmer rows (the
CORE_RULES §22 pulse, 110dp × 2:3 cards mirroring the real layout) + a
tiny per-row spinner while a row is unfilled + an "N of M" status line;
each row fills in as its section lands. Covers (phase 4) stay with Coil
crossfade. Stale-cache refreshes update SILENTLY (a loaded page must never
dissolve back into shimmer); only the no-cache path shows the phases.

## Item 7 — the update-notifications module (D-388)

- **Sound**: finish/fail post on the NEW `anikuta_update_results` channel
  at IMPORTANCE_DEFAULT (sound + vibration). New id — Android never
  upgrades an existing channel's importance. The live progress feed stays
  on the silent LOW channel (a progress indicator should never buzz; a
  RESULT should — and the channels are separately tunable in system
  settings).
- **Rich content**: `onFinish(summary: UpdateCheckSummary)` —
  BigTextStyle with one line per anime (title — outcome — the engine's
  next action, up to 5 + "+N more"), the summary line, and
  "Next check: in ~24h · Fri 3:30 PM" (the DEVICE's 12/24h clock via
  android.text.format.DateFormat), plus the first checked anime's cover
  as the large icon (Coil, async, best-effort). Tapping deep-links to the
  history (`open_update_history` extra — MainActivity pushes
  UpdateCheckLogKey).
- **The history page**: device-12/24h-aware time; the pinned NEXT-CHECK
  card at the top — a live 1-second countdown, WorkManager's REAL
  nextScheduleTimeMillis for the periodic job (falling back to the last
  logged session's projection, then now+interval), the due anime (with
  covers, tap → Details), and the "how" (automatic WorkManager job /
  manual pull-to-refresh / off); every per-content row renders the cover
  (captured at check time — `UpdateCheckItemLog.coverUrl`, default null
  so legacy JSON decodes) and navigates to the anime's Details page via
  the canonical mainId bridge.
- **Settings**: a dedicated Updates row on the Settings home (the report
  went looking for an updates page and found none — it was hidden under
  Notifications); the Updates page gained a Check-for-updates-now button
  (trigger "manual", result shown inline) + the history entry PROMINENT
  (right under the mode card); the Notifications row now points at the
  ALERTS page. Debug options gained the dedicated "Update Check History"
  button.
- **Engine**: `UpdateCheckItemLog.coverUrl`; the log entry records
  `nextCheckAt`; triggers labeled correctly (worker = "periodic", manual
  = "manual" — were ALL mislabeled periodic).

## Verification

- CI per batch: A+B GREEN (run 33653546839); C/D/E/F round FAILED on one
  file (my D-386 insertion left two raw `═══` banner lines without the
  `//` prefix in DetailsScreen.kt — syntax errors :1964/:1978); fixed +
  normalized + brace-balance re-verified (618/618) in the G commit.
- No database SCHEMA changes (the EXISTS semi-joins are query-only); the
  aniyomi stack untouched (all changes are list-level UI or core/updates
  seams).
