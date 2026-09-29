# Round 103 — The Menu's Second Pass, The Tracking Feel, The Honest Scroll, And The Player's Four Paradigms

> Round record 85. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The device round (v1.1.59)

The user's report, decomposed into the five work streams this round executes:

1. **THE THREE-DOT MENU, second pass** — the anchored dropdown "opens up properly and it
   kind of looks clean" (the round-102 form is ACCEPTED), but "it's still not a good kind
   of vibe, like it does not match the overall aesthetic of our app". The ordered changes:
   - **The previous LOGOS** — "I liked the previous kind of logos which you had with the
     bottom-up menu" — the round-101 `DetailsActionSheet`'s ICON DISCS (a 38dp tinted
     circle carrying a 19dp glyph — `ActionSheetRow`'s look) replace the current bare
     20dp `DropdownMenuItem` leading icons.
   - **The data-source options in a ROW** — "in a row kind of format rather than a column,
     like one at the top and one at the bottom. Not like that. They should be shown just
     like how they were previously shown" — the round-101 sheet's side-by-side segmented
     chips, NOT the current stacked checkmarked rows.
   - **The naming contract** — the LEFT chip carries ONLY the list/data-source system's
     name ("AniList" today; future systems append); the RIGHT chip carries the EXTENSION
     TEXT (the linked source's actual name, not the generic word "Extension").
   - **Unlink below** — "below it it should show the option to unlink any list" — the
     Link/Unlink AniList row moves INTO the data-source section, under the chips.
   - **"Open in Web View"** — the label rename from "View in WebView".
   - **The CRIMP** — "you can crimp the menu a bit where the separations are… crimp the
     menu where the data source section ends, and the other options end, and also you can
     crimp the sides where the other sections get started" — inset rounded SECTION CARDS
     with visible gaps between them (the iOS inset-grouped rhythm), replacing the current
     flat list + hairline dividers.
   - **Share is APPROVED** — "much proper now… let's leave it as it is" — the submenu's
     behavior is untouched (only its rows inherit the disc language for coherence).
2. **THE TRACKING FEEL** — the opt-in contract is approved ("currently at least by default
   it does not apply the tracking function… which is perfect"). The ordered changes:
   - **The menu's Tracking row** — text says ONLY "Tracking"; the STATE speaks through
     color: NOT connected = greyed icon + greyish background + greyish text; connected =
     a greenish tone (background + text).
   - **The sheet's heading** — "at the top it gives me the heading of the anime, but that
     is definitely not needed" — REMOVE the series-title header.
   - **The untracked sheet** — "it should not show the remove from tracking button at the
     very first time… Instead what it should do is that it should give me the option to
     start tracking."
   - **Save ≠ start** — "the save button should actually not start the tracking system,
     but it should only be for saving… it should only update it in any list. It should not
     link both of them together." (This amends D-694's "Save = the only persistence path
     AND the opt-in flip" — the opt-in flip MOVES to the new Start Tracking button.)
   - **The selectors** — Status/Progress/Score use "the exact same one which is being used
     for the link sources… that same kind of look and feel and the overall experience" —
     the ManualSearchSheet WHEEL (D-628/D-636: 36dp rows, snap fling, half-viewport edge
     centering, the distance blur falloff, the center highlight, scroll-driven selection)
     — "and also maybe you could add some vibration effects to the scrolling of it too."
   - Everything else about the sheet stays ("I don't want you to change anything which I
     did not mention… It should stay exactly as it is" — bottom-up form, dates, trash can).
3. **THE HONEST SCROLL** — "there is the button to scroll to current, but it apparently
   does not scroll to the current one. And it should scroll with a smooth animation rather
   than just jumping to the one." ROOT CAUSES (verified in code):
   - `LazyListState.animateScrollToItem` **snaps instantly** when the target is more than
     a viewport away (jump-then-glide) — the "just jumping" the user feels on long lists.
   - The landing is **top-aligned** (`animateScrollToItem(idx + headerOffset)`), not
     centered — the user's spec: "it should stop when the currently playing episode is in
     proper view. Like if it can be centered, then it will be centered, but if it cannot
     be centered, then it will not be centered and it will just stop there" (the
     first/last episodes can never center).
   - The centering basis: "the area below the player and through the very bottom" — the
     LazyColumn's OWN viewport (which is exactly that area), not the device screen.
4. **THE PLAYER'S FOUR PARADIGMS** — "the actual episode list on the details page is
   getting a total of four custom display options, but the player page one is not getting
   things like those… build custom ones for that too… just like the other one." Plus:
   - **Actual data in the preview** — "It should properly show the actual live preview of
     the data. It should not show random things… just like how it gets the data for the
     episode list of the details page" (the details page's D-556 library-backed loader).
   - **Sort: ascending/descending ONLY** — "the only sort option which should be given
     here is ascending or descending. It does not need to give any other options" — the
     round-101 sort modes (upload date / alphabetical) are REMOVED; the preview must
     reflect the changes live ("these changes were not being applied in the live preview"
     — the round-102 preview was style-only).

## 1. Root-cause research (pre-implementation, verified against HEAD 1ad99171)

- **`DetailsActionMenu.kt`** (333 lines): the data-source section renders two STACKED
  `DropdownMenuItem`s (AniList row, Extension row — "one at the top and one at the
  bottom") with `MenuLeadingIcon` (a bare 20dp primary-tinted glyph). The round-101
  sheet's disc + chip language lives in the DELETED `DetailsActionSheet.kt` (extracted
  from tag `277c1cfe` — `ActionSheetRow`: 38dp `primary.copy(0.12f)` circle + 19dp icon;
  the chips: `RoundedCornerShape(10.dp)`, `weight(1f)` each, selected = filled primary).
- **`TrackSheet.kt`** (698 lines): the header renders `seriesTitle` + the state chip +
  the trash can; the pickers are `PickerCell` rows expanding a 180dp plain `WheelPicker`
  (no snap, no blur falloff, selection = the remembered `clampedIndex`, not the scrolled
  center). The wheel contract to replicate: `ManualSearchSheet.kt` L765-1155
  (`WHEEL_ROW_HEIGHT=36dp`, `WHEEL_ROW_SPACING=3dp`, the half-viewport `contentPadding`,
  `rememberSnapFlingBehavior`, the `centeredIndex` derivedStateOf, the `hasScrolled`
  guard, the `tapCenteringCount` suppression, `WHEEL_BLUR_BY_DISTANCE=[0,0.8,1.8,3]dp`).
- **`DetailsViewModel.saveTrackEntry`** (L689-749): step 1 flips the opt-in
  (`trackingStateRepository?.setTracked(mid, true)`) — the exact line the user's
  "Save should not start the tracking" order removes. The rest (progress follow +
  remote sync + the SA2-F2 stay-open error surface) is the body BOTH Save and the new
  Start Tracking share.
- **`WatchScreen.kt` L2059-2077 / `CsWatchPage.kt` L406-430**: both call
  `listState.animateScrollToItem(currentIdx + headerOffset)` — the snap + top-alignment
  above. The index math itself is CORRECT (rows start at lazy index 2; +1 for the CS
  sub/dub switcher; verified against both item structures).
- **`PlayerEpisodeListSettingsScreen.kt`** (618 lines): the preview renders
  `PlayerPreviewRow` — a PRIVATE mock with fabricated titles ("The Journey Begins"),
  placeholder glyphs, and no data pipeline. The details page's actual-data loader
  (`loadLibraryPreviewItems` + `reconstructEpisode/Metadata` + `pickPreviewEpisodes`,
  `EpisodeListSettingsScreen.kt` L1033-1119) lives in the SAME `:app` module as the
  player settings screen — reusable by visibility change alone.
- **The details page's four** (`EpisodeRow.kt` L46-104): CLASSIC/GRID/TIMELINE/CINEMA —
  "FOUR completely different paradigms… each is a DIFFERENT STRUCTURE, not a variation
  of one row", rendered through ONE public `EpisodeListEntry` dispatcher the real list
  AND the settings preview both call (the D-481 one-source-of-truth).
- **The player row twins**: `WatchScreen.EpisodeListRow` (L2244) +
  `CsWatchPage.CsEpisodeListRow` (L687) — near-identical private rows fed
  `rowStyle/showSynopsis/showDatePill/dimWatched` strings. A four-paradigm build × 2
  stacks + a preview = 12 renderings if kept private — the shared-renderer move is
  forced by the user's own D-481 framing ("just like the other one").
- **Haptics**: `HapticHelper.lightTick(context)` exists in `:core:common` (API 29+
  `EFFECT_TICK`, one-shot fallbacks) and `feature/anime-details/impl` already depends
  on `:core:common`.
- **Colors**: `Color.kt` has `SuccessDark = 0xFFA5D6A7` but NO light counterpart — the
  tracking green needs `SuccessLight`.

## 2. The design decisions (pre-implementation)

- **D-696 (the menu)**: keep the anchored `DropdownMenu` (approved form), restyle the
  CONTENT: a fixed-width column of inset rounded section cards ("the crimp") —
  DATA SOURCE (the chip ROW + the link/unlink row below) / ACTIONS (Share, Open in Web
  View) / TRACKING (the state-colored row) — every action row wearing the round-101 disc.
  The share submenu keeps its exact behavior; its rows get the disc language.
- **D-697 (the tracking feel)**: the sheet loses the title header (the trash can stays,
  top-right, alone); the three pickers expand the LINK-SOURCES WHEEL (a new
  `TrackingWheelPicker` in TrackSheet.kt implementing the D-628/D-636 contract with
  `HapticHelper.lightTick` on centered-row changes); the button bar becomes contextual —
  untracked: [Save (quiet)] [Start Tracking (primary)]; tracked: [Remove from Tracking
  (quiet error)] [Save (primary)]. `saveTrackEntry` loses the opt-in flip; a NEW
  `startTracking(entry)` carries it (opt-in + progress follow + remote sync).
- **D-698 (the scroll)**: a new `:core:designsystem` extension
  `LazyListState.animateScrollToItemCentered(index)` — a fully-animated glide (NO snap:
  distance-proportional tween through `scroll { animate { scrollBy } }`, the internal
  `animateScrollBy` mechanism driven directly), then an exact-center settle on the
  composed target; edge clamping is inherent (the first/last rows stop un-centered).
  Both player stacks call it with their existing (verified-correct) index math.
- **D-699 (the player's four)**: `PlayerEpisodeListLayouts.kt` in
  `:core:designsystem/component/playerlist/` — `PlayerEpisodeRowData` (the render-only
  bundle), `PlayerEpisodeListStyle` (DETAILED / COMPACT / GRID / BANNER; lenient
  `fromKey`: MINIMAL → COMPACT, unknown → DETAILED), and the ONE dispatcher both player
  stacks AND the settings preview call (the D-481 doctrine, superseding D-688's
  zero-coupling rule for ROW RENDERING only — the stacks still never depend on each
  other; both depend on designsystem). GRID = the two-across poster wall (the stacks
  chunk pairs); BANNER = the full-bleed scrim card. `sortMode` is DELETED from
  `PlayerEpisodeListPreferences` (direction only); the settings preview loads REAL
  library data through the details screen's loader (made `internal` — same module) and
  reflects direction + the watched filter live.

## 3. Execution order

Stream A (the menu) → Stream B (the tracking feel) → Stream C (the scroll) →
Stream D (the four paradigms) → self-review → the two guided sub-agent audits →
CI → the ledger → v1.1.60 → ntfy (topic **TASK808DONE** — the user's explicit
this-session order, which overrides the standing THE-TASK-IS-DONE topic for this task).

---
*(The execution record, the CI history, the judgment calls, and the round-104 checklist
append below as the streams land.)*

---

## 4. The execution record

| Stream | Files | Commit |
|---|---|---|
| WS-1 the menu | DetailsActionMenu.kt (full rework), Color.kt (SuccessLight), DetailsScreen.kt (the sourceName pass) | 1eff1593 |
| WS-2 the tracking feel | TrackSheet.kt (full rework), DetailsViewModel.kt (saveTrackEntry/startTracking/pushTrackEntry), DetailsScreen.kt (the wiring) | 1eff1593 |
| WS-3 the honest scroll | SmoothCenterScroll.kt (NEW), WatchScreen.kt + CsWatchPage.kt (the call sites) | 1eff1593 |
| WS-4 the four paradigms | PlayerEpisodeListLayouts.kt (NEW), PlayerEpisodeListPreferences.kt, WatchScreen.kt + CsWatchPage.kt (the renderer adoption + sort collapse), PlayerEpisodeListSettingsScreen.kt (full rework), EpisodeListSettingsScreen.kt (the loader visibility flips) | 1eff1593 |
| The audit fixes | PlayerEpisodeListSettingsScreen.kt (remember(isGrid)), SmoothCenterScroll.kt (break), DetailsViewModel.kt (the opt-in abort), WatchScreen.kt (the pair-match hardening), the import prunes | 1eff1593 |
| The CI fixes | TrackSheet.kt (background/border/Close imports) | ace365be |

**THE JUDGMENT CALLS (disclosed):**
- **The MPV watched treatment** unifies on the CS tint style (surfaceVariant 0.15) — one
  renderer needs one treatment; the MPV's old alpha-0.5 dim retired. The current-episode
  highlight always wins in both.
- **The MPV download glyph** (an inert Icon the row has carried since round 101) survives
  the shared renderer via `showDownloadHint` — visual parity kept, the CS stack passes false.
- **MINIMAL folds into COMPACT** (the lenient migration): the user's four-paradigm order
  implicitly retires the density variation; the closest rhythm inherits its users.
- **The preview's audio parse is scanlator-only** (the MPV parser also scans the episode
  name) — the loader's reconstructed scanlator already carries the library's real audio
  aggregates, so the preview shows real data either way.
- **The GRID scroll centers the PAIR row** containing the current episode (the lazy items
  are pairs) — the current episode lands in view at the pair's center; a per-cell centering
  is impossible when the cells are not the lazy items.
- **PROCESS SLIP (disclosed):** the first four ledger files (decisions/changelog/progress/
  lessons) rode the CI-fix commit `ace365be` through a careless `git add -A` while the
  ledger was mid-write — the intended dedicated docs commit shrinks to doc 85 + SESSION.

## 5. The CI history (the D-472 ledger — 2 runs, within budget)

1. **Run 36515949685 on 1eff1593 — FAILURE:** three unresolved references in TrackSheet.kt,
   all import-block misses in the rewrite: `foundation.background` (the 610/614 errors were
   its downstream cascades), `foundation.border`, and `material.icons.filled.Close` — the
   round-102 extension-import trap verbatim ("extension properties resolve through IMPORTS,
   never receiver qualification"). A repo-wide sweep of every touched file for the same
   error class found no further gaps (two false positives: WatchScreen's Star/StarBorder
   imports carry trailing comments that broke the sweep's regex).
2. **Run 36516391039 on ace365be — GREEN** (the three imports fixed).

## 6. The sub-agent audits (the standing ≥2 order, D-689)

- **SA1 (the menu + tracking contract): S1-S14 all PASS** — the menu structure/labels/
  states, the share submenu's byte-identical behavior (verified against HEAD), the
  Save/Start contract split, the wheel contract port, the vibration guards, the draft
  semantics, the heading removal, the regression sweep, the compile-risk scan.
- **SA2 (the scroll + player list): T1-T14 PASS** — the scroller's bounded/clamped/
  snap-free math, both stacks' lazy-index correctness in all four shape combinations,
  the renderer's four treatments, the stacks' bundles, the prefs deletion sweep, the
  preview's truth (loader/renderer/direction/filter), the sort collapse, the regression
  sweep, the compile-risk scan.
- **Every finding lead-verified before applying:** SA2-F1 (MEDIUM — the stale `isGrid`
  capture in the preview's collapse: `remember { derivedStateOf { if (isGrid) … } }` never
  re-captures; fixed with `remember(isGrid)`), SA2-F2 (the scroller's ~zero-estimate
  early-`return` skipping the fallback; now `break`), SA1-F2 (the silently-swallowed opt-in
  failure on Start Tracking; now aborts with the error surface before the sync), SA2-F3
  (the GRID pair-match hardening: number + title), and both audits' unused-import lists
  (height/Color/alpha/Search/Tune/fillMaxHeight — pruned).
- **Not applied (documented):** SA1-F3/F4/F5 (INFO — the draft re-seed edge when the
  open's fetch lands late, the blur-wording nuance, the Compose-pulse note) and SA2-F4/F5
  (INFO — the scanlator-only preview parse, the fling-equivalent composition cost of the
  no-snap glide on 1000+ episode lists). All pre-existing or deliberate.

## 7. The round-104 checklist (the device round on v1.1.60)

1. **The menu:** the crimped sections' rhythm; the data-source chips side by side (the
   right chip should show the EXTENSION'S NAME, not "Extension"); the unlink row below
   them; the discs; "Open in Web View"; the Tracking row's grey↔green states.
2. **The tracking sheet:** no anime heading; the wheel's feel (snap + blur + the tick
   vibration while scrolling); untracked shows START TRACKING; Save on an untracked
   content updates AniList WITHOUT linking (verify the menu row stays grey after a
   Save-only round, and that Start Tracking turns it green).
3. **The scroll:** tap Scroll to Current on a LONG list (100+ episodes) — the glide should
   be smooth start-to-stop (no jump) and land the current episode CENTERED in the area
   below the player; episode 1 / the last episode should stop un-centered at the edge.
4. **The player list styles:** Settings → Appearance → Player episode list — the preview
   should show YOUR library's real episodes (titles/dates/thumbnails), the four layouts
   (Detailed/Compact/Grid/Banner), the direction flip reordering the preview, the watched
   filter hiding/showing the watched slot live; the player pages should draw all four
   layouts identically (both stacks, incl. the GRID pair taps switching the right episode).
