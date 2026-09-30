# Round 108 — The Settings' Honest Scroll, The Flick's Real Choreography, The Compact Layouts' Full Download Contract, The Two Grids' Union, The Banner's Cinema Turn, And The Transfer's Closed Gaps

> Round record 90. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The order (this session's input — the v1.1.64 device round)

1. **THE ENTRY SCROLL** — "whenever I enter the details page or the player page
   episode list settings, then what should happen is that it should always be
   scrolled to the very top… apparently sometimes the whole things were not
   scrolled to the top, but they were scrolled to the very bottom. Like it
   remembered the previous situations."
2. **THE FLICK CHOREOGRAPHY** — "the scrolling animation was not proper… if I
   flicked my finger, then it would not scroll automatically to the bottom.
   When I am at the very top, both of the live previews show. If I flick the
   bottom section up, then what should happen is that it should smoothly go on
   and scroll the top section first, and then by scrolling it one of the
   episodes will hide, and it will hide properly and smoothly as such. And
   after it has hidden, then it will not allow the user to scroll for a few
   bit for a few time, and after that it will automatically start scrolling
   the bottom section as it is. And it will depend on how fast the user
   scrolled, and such the speed will be determined." — same on the player page.
3. **THE COMPACT LAYOUTS' DOWNLOADS** — the classic view's download flow is
   proper; the GRID / TIMELINE / CINEMA views lack proper progress and the
   downloaded-episode options (play vs delete "or other key things").
4. **THE PLAYER GRID ← THE DETAILS GRID** — "go with a similar kind of grid
   view for the player page too, which is being used on the details page…
   below the thumbnail image, it shows the episode number, which is good, and
   below the episode number it shows the title of the episode, the actual
   name of the episode, if it is available in English. But… if the name is
   not available in English, or it only shows the episode number or such,
   then it will not be shown. And also the user will be given the option to
   customize it too, like he can select whether to show the episode title or
   not, and also he can decide whether to show the full episode title or only
   one line" — BOTH pages.
5. **THE BANNER ← THE CINEMA** — "the cinema view on the details page is
   quite good, and I want you to go with a similar kind of thing for the
   banner view too… the customizability should be quite diverse and quite
   proper."
6. **THE TRANSFER** — "the episode data was not properly being transferred
   from the details page to the player page, like the available audio
   versions were not being transferred, and a lot of other things."
7. Documentation + commenting + sub-agent review + virtual/paper testing +
   honest verification. Sandbox restored first (clone + credentials).

## 1. Root-cause research (pre-implementation, verified against HEAD 2170f136)

### 1.1 The entry-scroll bug
Both settings screens build their options list with `rememberLazyListState()`
— a **rememberSaveable**-backed state. The settings navigation restores the
backstack entry (and its saved state registry), so a screen left scrolled
down re-enters **scrolled down** — "it remembered the previous situations,"
verbatim. The preview's collapse latch (`collapseCollapsed`) also survives
re-entry (same mechanism), so a stale collapsed preview can ride along.

### 1.2 The flick bug (two independent defects)
- **THE THRESHOLD HOLE**: `onPreFling` only engages at
  `available.y < -1000f`. A weaker flick passes through to the LazyColumn,
  whose own fling dispatches its deltas as `NestedScrollSource.SideEffect` —
  and the connection's FIRST line early-returns on that source ("programmatic
  scrolls pass through"). Result: the list scrolls **under an open preview**
  (the collapse-first contract silently violated for every flick below the
  threshold).
- **THE DEAD HANDOFF**: a strong flick takes the designed path — consume the
  fling, animate the collapse, then hand the momentum to the list through
  `lazyListState.dispatchRawDelta` inside `AnimationState.animateDecay`. On
  device the handoff is dead (the user: "it would not scroll automatically to
  the bottom") — the raw-delta dispatch outside the scroll mutex is the
  fragile half-documented path. The CANONICAL pattern (the one the framework
  itself uses for `animateScrollBy`) is `listState.scroll {
  AnimationState.animateDecay(spec) { scrollBy(delta) } }` — the scroll
  scope's `scrollBy` is non-suspend, mutex-serialized, and cancels correctly
  under a new user drag.

### 1.3 The compact layouts' download gaps
`EpisodeDownloadBadge` (GRID/TIMELINE/CINEMA) maps `Downloaded` →
`onPlayDownloaded()` directly — **no options** (the classic control offers a
Play/Delete dropdown; the badge's own NOTE admits "onDelete has no gesture
room"). `Downloading` → `onPause()` directly — no menu (classic: Pause +
Cancel). The progress is a 24dp ring with a **7sp** numeral — illegible at
arm's length ("no proper progress of downloading"). The PLAYER's badge is the
same shape AND its action bag (`PlayerEpisodeDownloadActions`) has **no
`onDelete` at all** — nothing on the player can delete a download.

### 1.4 The two grids' drift
The details GRID's text block: `EpisodeNumberLabel` (themed "EP N") on its
own line → the title (13sp SemiBold, 2 lines) → the chips row
(horizontalScroll). The player GRID: "EP N · title" squeezed onto ONE 11sp
line → the chips (FlowRow). Different anatomy, different rhythm. Neither
gates the title: a fallback "Episode 5" (the parser's placeholder) or a
Japanese-only name renders as a "title" line under a number that already
says the same thing. There is no title toggle on the details grid and only a
Boolean on the player grid.

### 1.5 The banner's missing cinema parity
The details CINEMA: 2-line 16sp title, the watched-check knob, the progress
bar. The player BANNER: 1-line 15sp name, no progress bar, no check knob
(dim only). The round-105/106 knobs (number position/style, size, current
PLAY/TINT, pills above the name) are the banner's own and stay.

### 1.6 The transfer's two leaks
- `buildEpisodeMetadataSerialized` iterates **`metadata.entries`** — the
  AniList/AniZip metadata map. An extension-only series (never linked to
  metadata providers — the common CloudStream case) ships an **EMPTY**
  `episodeMetadataSerialized` to the player: no per-episode titles,
  thumbnails, air dates, descriptions, or **scanlators** — and the scanlator
  is exactly what the player's audio pills parse. This is "the available
  audio versions were not being transferred."
- The player rows' thumbnails read `meta?.thumbnailUrl` with **no cover
  fallback** (the details page falls back to the cover) — a thumbnail-less
  series renders a number-tile wall on the player while looking fine on the
  details page. (The flavors are NOT a leak: the CS player rebuilds them
  from the raw names via `CsSubDubSiblings`.)

## 2. THE PLAN

### P1 — ONE shared collapse controller (both settings screens)
New file `app/.../settings/PreviewCollapseScroll.kt`:
`rememberPreviewCollapseScroll(lazyListState, enabled)` returns a controller
exposing `connection`, `hideFraction(firstRowHeightPx)`, `collapsed`,
`reopen()`, `resetOnEntry()`. Semantics:
- **ENTRY RESET**: on remember + on `enabled` change → scroll to item 0
  (unless a search anchor will steer — the screens call `resetOnEntry()`
  themselves so the anchor logic stays theirs) + re-open the preview.
- **DOWN-DRAG while open** (D-557/D-558, unchanged): the collapse phase
  consumes everything; halfway-cross latches → animated snap → release the
  same gesture into the list.
- **DOWN-FLICK while open** (THE REWORK): consume the fling (no threshold
  beyond tap-noise ~200px/s) → animated collapse (260ms) → **THE LOCK BEAT**
  (~140ms consuming all drags/flings — "it will not allow the user to scroll
  for a few bit for a few time") → the momentum handoff:
  `listState.scroll { AnimationState(0f, v).animateDecay(decay) {
  scrollBy(delta) } }` — velocity-proportional ("it will depend on how fast
  the user scrolled"), cancellable by a fresh touch (natural fling feel).
- **UP**: unchanged (list first; the top-leftover expands; the up-fling's
  leftover settles the expansion).
- GRID exemption + the layout-switch re-open ride the `enabled` flag.
Both screens delete their ~150-line inline copies and mount the controller.

### P2 — The download contract everywhere
- `EpisodeDownloadBadge` + `PlayerEpisodeDownloadBadge`: `Downloaded` tap →
  the Play/Delete dropdown (the classic control's contract);
  `Downloading` tap → the Pause/Cancel dropdown. `previewTapAll` stays the
  preview's demo override.
- `PlayerEpisodeDownloadActions` gains `onDelete`; WatchScreen +
  CsWatchPage wire it to `downloadManager.deleteDownloadedEpisode(mainId, key)`.
- A determinate **download-progress bar** on the imagery's bottom edge while
  `Downloading` (details GRID/TIMELINE/CINEMA + player GRID/BANNER) — the
  visible progress the round ordered; it replaces the watch-progress bar's
  spot while a download runs (download wins, watch returns after).

### P3 — The grids' union + the title model
- `core:common` gains `EpisodeGridTitles.kt`: `GridTitleMode`
  (OFF / ONE_LINE / TWO_LINES, lenient fromKey) + `gridShowableTitle()` —
  the REAL-title gate: null on blank, on the "Episode N"-only fallback
  (parseTitle null), on hash/code names, and on CJK-script names ("not
  available in English"). ONE definition, both features + the designsystem.
- The player GRID cell reworks to the details anatomy: themed number line →
  gated title (mode-aware) → the chips FlowRow (the wrap contract stands).
  The over-image player identity (current PLAY/TINT + ring, watched check,
  badge, progress) stays.
- The details GRID: the same gate + mode on its title; its chips row joins
  the FlowRow wrap (both pages one management — never clipped).
- `EpisodeListPreferences.gridTitleMode` +
  `PlayerEpisodeListPreferences.gridTitleMode` ("TWO" default; the player's
  `gridTitles` becomes a tombstone). `PlayerEpisodeListDisplay.gridTitles`
  → `gridTitleMode: GridTitleMode`; `EpisodeListDisplayStyle` gains
  `gridTitleMode`.

### P4 — The banner's cinema turn
The player BANNER: the name grows to the cinema's 2-line 16sp; the progress
bar renders (the `showProgressBar` knob widens to BANNER); a
`bannerWatchedCheck` knob (default OFF — the zero-prefs look is today's)
renders the centered check over the dim; everything else stays.

### P5 — The transfer
- `buildEpisodeMetadataSerialized` iterates the UNION — every episode (the
  extension's own values first, provider metadata filling gaps), so
  extension-only series keep their titles/thumbs/dates/scanlators on the
  player. ONE builder, extracted from the 8 copy-pasted `epListStr` sites
  into a shared `buildEpisodeListSerialized` too.
- `WatchKey` + `CsWatchKey` gain `coverUrl: String = ""`; the details nav
  callbacks carry it; both player stacks' `toRowData` use it as the
  thumbnail fallback (and the currently-playing sections).

## 3. The audit + verification plan
- SA1 (logic): the collapse controller's state machine traces (flick, drag,
  lock, re-entry, GRID switch, anchor interplay) + the badge menus + the
  grid/banner reworks — compile-logic and rule-contract.
- SA2 (parity/blast radius): every caller of every changed symbol (the
  display bundle, the action bag, the keys, the style classes, the prefs),
  the settings previews inheriting through the same renderers, the
  serialization back-compat (old keys → lenient parses).
- CI: push → poll → fix (≤2 runs, D-472).

## 4. The CI history (the D-472 ledger)
(pending — filled as runs happen)

## 5. The sub-agent audits
(pending — filled after SA1/SA2)

## 6. The round-109 checklist (the device round on v1.1.65)
1. Re-enter either episode-list settings screen after scrolling it halfway:
   it opens AT THE TOP, preview open.
2. From the top, flick the options up: the preview's first episode hides
   smoothly, a short beat passes, then the options scroll by themselves —
   faster flick, faster scroll. A weak flick behaves the SAME (no list
   movement under an open preview). Touching mid-scroll stops it.
3. Slow drag: the two-phase snap still holds (preview first, then the list).
4. GRID downloads (details + player): the progress bar is visible on the
   imagery; the badge's downloading tap offers Pause/Cancel; the downloaded
   tap offers Play/Delete; delete works from the player page too.
5. The player GRID reads like the details GRID: number line, title, chips;
   a series with bare "Episode N" names shows NO title line; the settings'
   "Episode titles" segmented (Off / One line / Two lines) reshapes BOTH
   pages' grids live.
6. The player BANNER: 2-line names, the progress bar, the watched check
   knob; the cinema-like feel without losing the banner's own knobs.
7. An extension-only series (no AniList metadata): the player page now
   shows its titles/thumbnails/dates/audio pills; thumbnail-less episodes
   fall back to the cover image.

## 7. The release record (the D-565 loop)
(pending — v1.1.65 cut after CI green)
