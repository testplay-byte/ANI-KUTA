# 15 — DEVICE ROUND 24 (Task 64): THE ORDERED RE-DO — REVERT + THE SIX IMPROVEMENTS

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: **the branch was
> REVERTED to `ba3c6937` (v0.4.10/75) per the user's instruction** — the
> round-23 (Task 63 / v0.4.11) changes were device-tested and judged
> unsatisfactory as a whole ("None of them are done properly as I hoped").
> CI verified the revert green (run 33630253190) before any new work.
>
> Delivery order = the user's explicit list, one improvement per commit,
> CI-verified green between items. The improvements not in the list come
> after. NO database changes this round (user: "you don't need to do
> anything with the database at all" — includes the round-23 index/transaction
> hardening, which the revert removed and this round deliberately does NOT
> re-apply).

## The device report (v0.4.11, post-revert baseline v0.4.10)

1. Library performance STILL glitchy/laggy with frame drops while scrolling
   (the round-23 fixes did not improve it on device).
2. Library category chips: long category names do not show fully (dots at the
   end / shrunk); the auto-scroll must work (selected chip centered).
3. Downloads tag: lose the parentheses, bold the episode count only.
   Developer tools: remove ONLY the console-log options; KEEP the debug page
   and all its other functionality.
4. Profile Genres radar: a dedicated genre list section BELOW the "Genres"
   heading (not right of it), bigger heading text, and honest filtering logic
   (genres/categories with no entries never render; a gone/empty default
   filter falls back to All; selecting an empty category must NEVER make the
   whole genres section disappear).
5. CloudStream browse categories: content mixing + duplicate same-name rows.
6. Watch Activity heatmap: the weekday labels' BOTTOM HALF is cut off — fix
   the clipping (not a position shift).
7. (Afterwards) Update-check notifications must show LIVE status while
   refreshing (which content, names, progress), plus a persistent update-check
   history page (what was searched, on which content, success/failure, next
   action). Storage = JSON file (NOT the database, per item 7 above).

## Item 1 — library page performance (gallery-grade)

Research findings (this round, fresh — the round-23 diagnosis blamed only the
2-request Coil cap and called the per-cell code "already well-tuned"; the
device said otherwise):

- **R1 (kept from round 23, fixed harder): the app-wide 2-request image cap.**
  `ImageLoaderFactory`'s dedicated OkHttp client ran `maxRequests = 2` (a
  round-21 SEARCH-page number applied to every image in the app). A library
  fling into never-loaded areas starves visible covers behind off-screen
  completions + cancel/re-enqueue tail churn.
  **Fix: 12 total / 8 per host** (bounded FIFO, complete-what-you-started —
  the round-21 spirit kept; 12 ≈ one full visible grid cell set).
  Coil 3 crossfade semantics were verified from the 3.0.4 sources
  (`ImageRequest.getExtra = extras[key] ?: defaults.extras[key] ?: default`):
  the library's request-level `crossfade(false)` (explicit 0) DOES override
  the loader-level `crossfade(true)` — so library covers were never fading at
  the Coil layer and the loader default stays untouched (search/browse
  unaffected).
- **R2 (kept, done right): `selectCategory` re-ran the full 7-query DB
  pipeline + full entry rebuild + re-enrichment on EVERY category tap.**
  The load queries are full-table anyway (the category filter was applied
  in memory on top of full-row reads). **Fix: the full enriched set + raw
  library_item rows are cached in the VM; `selectCategory` is a pure
  in-memory re-filter on Dispatchers.Default with a single guarded emission.
  Every load path (init, tab-return, PTR, mutations) refreshes the cache, so
  it can never serve stale data.**
- **R3 (new): per-cell animation machinery.** Every grid card / list row
  composed THREE `animateFloatAsState` (press scale + selection alpha +
  reveal alpha) + an interaction-source collector — dozens of idle
  Animatables + LaunchedEffects churned on every fling cell enter/exit.
  **Fix: the selection-alpha animation is composed ONLY in selection mode;
  the reveal machinery is composed ONLY for never-revealed covers (an
  `initiallyRevealed` snapshot taken per cell instance — a remember, NOT a
  reactive read, so a mid-flight `markRevealed()` can never swap the branch
  out from under a running fade).**
- **R4 (new): the shared-element gate flipped the whole grid.** The Boolean
  `coverSharedElementsActive` param (Task 62 M2) flipped at every scroll
  start AND stop; every cell's content lambda captured it, so each flip
  recomposed ALL visible cards. **Fix: the gate is a remembered lambda
  evaluated inside `LibraryCoverImage`'s own scope — a flip now re-executes
  only the cheap cover-image scopes.** The prefs read stays ONCE at the
  grid/list level (M2's rule).
- **R5 (new): the reveal velocity tracker emitted per scroll FRAME.** The
  position signal read `firstVisibleItemIndex * 4096 + scrollOffset`; the
  offset term changes every frame, so a `snapshotFlow` ran its collect math
  on the main dispatcher at frame rate for the duration of every scroll.
  **Fix: index-only signal** (emits per item crossed; the fade duration only
  needs a coarse velocity estimate; fling normalization re-scaled to
  ~1 item / 50 ms = hard fling).

## Item 2 — category chips (full names + centering)

- **Root cause of the truncation (found in code): `IntrinsicSize.Min`.** The
  Task 62 underline fix wrapped the chip Column in
  `Modifier.width(IntrinsicSize.Min)`. A Text's MIN intrinsic width is its
  widest WORD (intrinsic measurement assumes the paragraph can wrap), so any
  multi-word category name (or `Name (count)`) got its column sized to ONE
  WORD and the `maxLines=1 + Ellipsis` text truncated to "My Long Ca…".
  **Fix: `IntrinsicSize.Max`** (max-intrinsic of a single-line text = the
  full line) **+ the ellipsis removed outright** — the LazyRow measures items
  with unbounded main-axis width, so chips render at their full text width
  and the row scrolls.
- **Auto-scroll centering:** selected chip CENTERED on open (instant) and on
  manual taps (animated), with the LazyRow's natural edge clamping (the
  round-23 spec: "excluding the left and right side ones"). Snap → measure →
  negative-offset center; a tap-set flag drives animate-vs-instant
  deterministically (no heuristics).

## Item 3 — downloads tag + console-log removal only

- `(5 Episodes Downloaded)` → `5 Episodes Downloaded` with the number in a
  bold span (`buildAnnotatedString`), everything else unchanged.
- Developer tools: the console-logging FAMILY goes (the in-app console
  screen + its ring buffer + the Logger's console capture + the settings
  row); the debug OPTIONS page and every gated debug affordance stay
  exactly as v0.4.10 had them.

## Item 4 — Profile genres radar rework

- A dedicated genre-chips section UNDER the Genres heading (full genre list
  from the WHOLE library — never from the filtered subset), heading text
  size up.
- Filter chips = All + the user's categories that actually have entries
  (empty categories never render); the persisted default falls back to All
  when its category is gone/empty; the genres section NEVER disappears when
  a filter yields an empty grid (radar + chips + empty-state message render
  from full-library data).

## Item 5 — CS browse categories

- `shelfIndex` = the ORIGINAL mainPage index captured BEFORE empty-shelf
  compaction (the mixing root cause: subpage rows derived their index from
  the COMPACTED list position).
- Same-title sections merge case-insensitively (concat + distinctBy url +
  re-cap; first shelf's index) — no duplicate rows.
- LazyColumn row keys = the stable shelfIndex.

## Item 6 — Watch Activity weekday labels

- The labels' bottom half clips inside the heatmap's fixed-height box: give
  the day-label column enough height/padding to draw the full text (NOT a
  position shift — the user only wants the cutoff gone).

## Item 7 (afterwards) — update-check live notifications + history

- An ongoing progress notification updated per content item while the check
  runs (title + "n/total" + the current content name), finished state with
  the result — LIVE, not after-the-fact.
- A content-update HISTORY: every check logged (trigger, per-content
  outcomes, success/failure, the engine's next action) to a JSON file, with
  a dedicated history page reachable from the updates settings.

## Invariants (unchanged)

- Aniyomi stack: additive/display-layer only; engine/resolver/MPV/download
  byte-untouched.
- CI is the compiler of record; no local Gradle/SDK.
- NO database changes (no schema edits, no migration churn).
- Reveal-once cover system, draw-phase fades, D-285 batching, H2/M3 fixes stay.
- v0.4.10's confirmed features do not regress: post-import crash fix,
  plugin↔repository linkage, Format-sources menu, cover pinch-zoom.

## Version + delivery

`0.4.12/77`. One commit per improvement → CI green between → tag `v0.4.12`
+ release → 2 ntfy.sh notifications → the user's device round.
