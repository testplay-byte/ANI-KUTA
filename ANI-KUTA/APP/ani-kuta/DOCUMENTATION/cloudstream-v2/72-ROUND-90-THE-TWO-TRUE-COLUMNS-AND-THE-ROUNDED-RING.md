# 72 — ROUND 90: THE SHEET'S TWO TRUE COLUMNS + THE ROUNDED GROUPED RING (D-626, D-627)

> Round: 90 · Date: 2026-09-27 · Base: v1.1.46 (round 89) · Ship: v1.1.47/10147
> Input: the user's v1.1.46 device report — "The Link Sources menu is not proper… I updated to the
> latest version, the version 1.1.46, but the Link Sources bottom-up menu is most definitely not
> good. I want the experience to be much better, like on the left side and the right side, two
> different sources should be shown… **So let's improve it and let's move forward and not go
> backward anymore.**" Plus the suite-health donut order (rounded spaced slices, the combined
> groups, the aligned legend) and a request to ANALYZE the testing systems properly.

---

## 0. THE DIRECTION DECISION (round 89's revert is superseded, not undone)

The round-89 revert (D-625) restored the v1.1.43 single alphabetical list — and the user's
v1.1.46 verdict on it is that it is just as unsatisfactory as the experiments were. The
round-90 order is explicit: the two-column DIRECTION (Aniyomi LEFT, CloudStream RIGHT) is what
they wanted all along — "apparently you did not handle it better" — so this round RE-BUILDS the
columns to their exact spec instead of cherry-picking the D-614 blob. Why not cherry-pick: the
D-614 implementation had no scroll-selection, no linked-content card, the "search below" hint,
and the String-based query paste — four of the five things this round exists to fix. The round-89
annotation's recovery note ("cherry-pick, never re-implement") applied to restoring the OLD
columns verbatim; the user's round-90 order is a NEW, fuller design. The file's section banner
was rewritten to tell this story.

## 1. D-626 — THE LINK SOURCES SHEET, REBUILT TO SPEC

`ManualSearchSheet.kt` (full rework, +809/−273 across 4 files with the ring work) + the
`DetailsScreen.kt` call site. Top to bottom, the sheet is now:

```
┌────────────────────────────────────────────────────────┐
│ Link Source                                         ✕ │
│ ┌──────────────────┐  ┌──────────────────┐            │
│ │ ● Aniyomi      9 │  │ ● CloudStream  6 │  ← columns │
│ │  ┊ (scrolls)  ┊  │  │  ┊ (scrolls)  ┊  │            │
│ │  ┊ centered   ┊  │  │  ┊ centered   ┊  │            │
│ └──────────────────┘  └──────────────────┘            │
│ [ 🔍 Search <selected extension name>…          ✕ ]    │  ← live placeholder
│      Tap a source to select it, then search above.     │  ← FIXED copy
│ ┌────────────────────────────────────────────────┐     │
│ │ [cover]  Content title (top right)            │     │  ← NEW card
│ │          12 episodes · Releasing · 85/100 ·    │     │
│ │          Winter 2024 · Linked via <source>     │     │
│ └────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────┘
```

### 1a. The two true columns
Aniyomi LEFT, CloudStream RIGHT — each its own rounded card (`surfaceVariant @ 0.35f`,
14dp corners) with a FIXED heading (accent dot — emerald/sky, the established TestingPalette
system hues — + bold label + count) and its own independently scrolling `LazyColumn`. Both
buckets arrive pre-alphabetized (round 85). Rows are the D-614 half-width proportions (20dp
icons, 11sp names, the full selected treatment: tint + border + ExtraBold + check bubble, and
the persistent linked ✓). Equal-width via `weight(1f)`; the cards wrap their own content height
(the round-85 "ceiling, not fixed height" rule survives in both columns).

### 1b. Scroll-driven selection (the new interaction)
The row nearest a column's vertical CENTER is that column's "centered" row:
`derivedStateOf` over `listState.layoutInfo` (nearest `|item.center − viewport.center|`).
While the user has interacted with a column, every change of its centered row flows out through
`onCentered` → the sheet's `selectedSource` moves there **live** — and the search bar's
placeholder renames itself to that source ("Search <extension name>…"). No snap, no wheel
chrome: a normal list that selects what it centers. Tap-to-select stays first-class (with the
haptic; scroll-selection has none — a flick can cross many rows).

**The interaction guard** (the subtle part): only USER scrolls drive selection. Each column arms
a `hasScrolled` flag on its first observed `isScrollInProgress`; without the guard, the column
that does NOT hold the seed would fire its (arbitrary) initial centered row on composition and
steal the pre-selection. The guard also makes the seeded centering scroll harmless even if a
programmatic `scrollBy` flips `isScrollInProgress` — it centers the SEED, so the report lands on
the already-selected source.

**The seeded centering** (D-578, upgraded): the linked source's row is scrolled to the EXACT
vertical center on open (`scrollToItem`-seeded start + a post-layout `scrollBy` delta — no
pre-layout flash). The seed is the CURRENT selection, recomputed at panel composition — so
"Change" (back from the results view) reopens the columns centered on whatever the user last
selected, not on the stale link.

### 1c. The paste, optimized
The query is a `TextFieldValue` now (was a `String`). The first-focus content-name paste (D-582)
sets `TextFieldValue(initialQuery, TextRange(initialQuery.length))` — the caret lands at the END
of the pasted name. The String overload could drop the caret at offset 0, so the user's first
keystroke PREPENDED instead of appending — one of the "slight issues" from the device report.
The round-85 blur contract (focus lost + idle + no results → field empties, paste re-arms) and
the D-587 focus-killer IME watcher are unchanged. `rememberSaveable(stateSaver =
TextFieldValue.Saver)` keeps rotation safety.

### 1d. The hint, fixed
"Tap a source to select it, then **search above**." — the search bar sits ABOVE the hint; the
round-86 copy said "below", which read wrong from the bottom of the sheet. (The user dictated
exactly this fix.)

### 1e. The linked-content card (the round-90 addition)
Below the hint at the very bottom, in BOTH modes (columns and results): the content's cover on
the left (54×74dp, poster-cropped via Coil, quiet letter-tile fallback), its name at the top
right (14sp ExtraBold, 2 lines), and its key details underneath — "12 episodes · Releasing ·
85/100 · Winter 2024" in the details page's own formatting language, plus "Linked via <source>"
in the primary color when a link exists. Built at the call site from the `UnifiedAnime` (+ the
effective linked source) into a new `LinkedContentInfo` bundle — the sheet stays decoupled from
the model. The user always sees WHAT they are picking a source for.

### 1f. The ime-aware cap (the D-612 mechanism, restored with the card)
`listMaxHeight = screenHeight − 300dp − (ime + nav insets)`, floored at 140dp. The 300dp reserve
carries the header (~58) + column headings (~30) + search row (~66) + hint (~28) + card (~104) +
closing paddings (~14). This is what keeps the search bar AND the card on the sheet with the
keyboard open — the known v1.1.46 squish consequence is thereby also resolved (the round-89
disclosed trade-off closes: the fix is back, on the NEW layout).

## 2. D-627 — THE SUITE-HEALTH RING: ROUNDED, SPACED, COMBINED

### 2a. The geometry (TestingCharts.kt — new `GroupedDonutChart`)
The user's spec, exactly: "rounded, spaced donut slices… the donut will not be split into six
sections. It will only be split into three sections… the two past sections will be combined
together, like there won't be any spacing between them… That space will not be rounded off…
Only the very right side of one of them will be rounded off and the very left side of one of
them will be rounded off. But the rounders, it won't be rounded off way too much either."

- **The ring is THREE groups** (passed / failed / new), each group's span proportional to its
  combined count; the two systems' sub-arcs inside a group divide that span and meet with **no
  gap and no rounding** — butt caps, a clean color seam (emerald|sky, red|orange, gray|gray).
- **Only the group's outer ends are rounded**: painted as near-zero-sweep arcs with
  `StrokeCap.Round` — a semicircle end cap of `strokeWidth/2` EXACTLY on the group boundary, in
  the color of the sub-arc that touches it there. The cap's angular reach is
  `(strokeWidth/2)/centerRadius` (~10° at the hero's 116dp/0.30 proportions) — the standard,
  subtle stroke-end rounding, never bulbous.
- **The group gap is derived from the cap geometry**: `2 × capAngle + 3°` (~23°) — neighbouring
  caps can never touch, and the visual gap lands ≈1.15× the stroke width (the classic
  rounded-slice donut proportion).
- Zero-count groups contribute no span and no gap; the entry sweep animation, the per-group idle
  track underlay (the entry reads as the arc growing along a track — D-602's lit empty state,
  now per-group so the GAPS stay pure card surface), and the center slot all match `DonutChart`.
- A single-group ring (the "everything is new" state) starts half a gap past 12 o'clock so its
  gap sits centered at the top.
- The stats screen's own hero + `SystemDonutCard` donuts are untouched — they use the unchanged
  `DonutChart` (different surface, not in the order).

### 2b. The aligned legend (TestingHomeScreen.kt — `HealthLegendRow` rebuilt)
The user's spec: "the total numbers will be aligned properly… the passed text, the failed text,
the new text will be center aligned… on the very right side, the other details, like Anyomi plus
CloudStream, those details will be properly aligned in the same way too." The legend is now a
fixed-column table:

```
[swatch][swatch] [total] [ label · centered ] [ A ] [+] [ B ]
```

Every row renders the identical structure (the A/B split ALWAYS shows, even 0s — identical
structures are what make columns align), each split count in its system's color so the pair
reads without a header. The old left-flowing row (whose "· 10 + 5" tail drifted with every label
length) is gone.

## 3. THE TESTING-SYSTEMS ANALYSIS (the user's "look into those areas… get a better understanding")

Requested as preparation for future focused work on the testing feature; no code changed here —
this section is the record of how the system actually works end to end.

**The pipeline.** `TestingHomeScreen` (the hub) → per-system target lists → `ExtensionTestRunController`
(the singleton session owner: queue, phases, per-target `TargetRunState` snapshots, stop/skip
signals) → `ExtensionTestEngine` (walks the chain for ONE target) → `ExtensionTestChain.build`
(7 tests in the user's round-86 order: Ping → Home page → Search → Details → Episode list →
Video resolve → Stream play) → each test's `ExtensionTestContext` (the WATERFALL state:
foundAnime from SEARCH/HOME_PAGE feeds DETAILS/EPISODE_LIST; EPISODE_LIST feeds VIDEO_RESOLVE;
the resolved stream feeds STREAM_PLAY).

**The isolation model (D-583).** Every kind runs inside `TestIsolation.runKind` — a dedicated
thread with a HARD wall-clock deadline (`kind.timeoutMs`, 15–90s per kind), because extension
`suspend` overrides can be fully blocking and a cooperative `withTimeout` can never fire. A
timeout is a VERDICT (FAILED "Timed out after…"), never a hang; the wedged thread is abandoned.

**The honest gate (D-590).** A kind whose prerequisite feeders ALL failed is FAILED "Not run —
<labels> failed", not SKIPPED — dead sources cascade loudly. The only SKIPPED verdicts left:
user skips, the Stop settle, and the DETAILS FORGIVENESS.

**The details forgiveness (D-590).** DETAILS failing while EPISODE_LIST passes is rewritten to
SKIPPED "Forgiven — the episode list loaded" at chain end (the episode list is what playback
actually needs). DETAILS passing + EPISODE_LIST failing stays a FAIL.

**The verdict math.** `TargetRunState.isHealthy = finished && !abortedByUser && failedCount == 0
&& passedCount > 0` — a user-cut-short run is never healthy. The hub's ring counts per system:
`p = isHealthy`, `f = finished && !isHealthy`, `untested = targets − tested` — the ring's three
groups are EXACTLY these, combined across systems (D-599) and now rendered by D-627's geometry.

**The persistence.** `ExtensionTestResultStore` — per-target verdicts + rich payloads (capped
entry/episode/video lists, stream headers for the preview) + run-history entries (per-kind
stats, pass counts) as JSON projections; the hub/stats screens read the store and re-load on the
session's testedCount tick, so finished targets appear live.

**Where this is going (the user's signal).** The user wants to "focus on the actual testing of
them a bit better" — the natural next moves, all already scaffolded by the models: automated
batch schedules and per-extension statistics (documented in doc 64 as the chain's future), and
the stats derivations above are the only places a new aggregate would need to hook.

## 4. CI + THE RELEASE

- **CI:** implementation commit `3f81856f` pushed to the mainline → Build APK run **36248211351**
  → *(recorded in the progress ledger when it lands)*.
- **The release (v1.1.47/10147, the standing per-round debug loop — D-565):** `release/1.1.47`
  cut from the green round-90 ledger head; the bump commit (`10120 → 10147`, `1.1.20 → 1.1.47`)
  rides the branch per D-430 (the mainline stays 1.1.20/10120 — D-430 doctrine, do not "fix");
  the annotated tag v1.1.47 carries the honest what-you'll-see bullet body (D-466); Release APK
  run → green → the stable-latest release publishes with the arm64-v8a debug APK +
  SHA256SUMS.txt → the in-app updater (debug builds check this repo, D-440) picks it up.

## 5. Open items for the next device round

- The sheet is a REWORK of a much-reworked surface — the device round should specifically
  check: the two columns' readability at the user's font scale, the scroll-selection feel
  (does the centered row read as "the" selection?), the live placeholder rename, the paste's
  caret behavior, the card's details for an extension-only entry, and the keyboard-open layout
  (the cap's 300dp reserve is an estimate — the honest knob if anything still clips).
- The ring's group-gap (~23°) and cap proportions are geometry-derived; if the user wants the
  slices chunkier or the gaps tighter, the stroke fraction and the +3° breathing room are the
  two knobs (both documented in the code).
- The dashboard's data did not change at the status level (round, version, decision counters
  move — the standing round-89 "leave the dashboard as-is" instruction still holds).
