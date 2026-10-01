# Round 113 — The Elements' Segment Anatomy, The Synopsis-Off Right Side, The Transfer's Both Channels, And The Two Screens' Parity

**Round type:** device-round response (on v1.1.69) · **Work orders:** D-733,
D-734, D-735, D-736 · **Branch:** `feature/round-57-cloudstream-downloads`

> **The good news first:** the v1.1.69 crash fixes HELD — "now the crashing
> has been fixed, which is a good thing. Now it does not crash on the
> library, or even it does not crash on opening up the details page." The
> round shipped FOUR findings, all four UX/data quality, zero crashes.

## 1. The device round's verdicts (v1.1.69)

1. **THE ELEMENTS' LOOK** — "in the details page, the element selection is
   not good, like it is showing in a list format… The elements should be in
   a dedicated separate section, and the way I wanted the elements to look
   like were just like how the buttons for the layout are, like each
   individual button for the layout, the currently selected one… apparently
   you gave them rounded corner, pill-shaped button-like feel, which is not
   good." The round-112 quiet pill is REJECTED; the reference is the layout
   selector itself.
2. **THE CLASSIC SYNOPSIS-OFF LAYOUT** — "in the classic view on both ones.
   If I have hidden the synopsis… all the details will be shown on the right
   side of the thumbnail image, and it will be formatted properly so that it
   does not interfere with the details, like the sub, the dub episode
   selection and the release date. And also the full, like not the full, but
   the name of the episode will be shown on a single line there." The
   round-112 bottom-section relocation is SUPERSEDED.
3. **THE AUDIO-VERSIONS TRANSFER** — "when I go from the details page to the
   player page, then all of the episodes do not transfer their audio
   versions availability to the next one, next player screen… none of them
   show the sub or dub tags. Only the currently selected episode shows the
   sub or dub episode tags."
4. **THE PARITY DIRECTIVE** — "making sure to give proper care to the player
   page and the details page episodes lists layouts both should almost have
   similar customizability."

## 2. The root causes

- **THE ELEMENTS**: the round-112 `ElementToggleButton` was a
  `RoundedCornerShape(50)` pill with translucent tints and the M3 clickable
  Surface's ≥48dp touch minimum — fat, round, list-shaped. The layout
  selector (`SegmentedToggle`) the user pointed at is the OPPOSITE anatomy:
  one shared container, compact 8dp-corner segments, the selected one SOLID
  primary.
- **THE SYNOPSIS-OFF LAYOUT**: round 112 (D-731) relocated the title to a
  bottom section when the synopsis hides — the user's v1.1.69 order
  supersedes it: EVERYTHING right of the thumbnail, no second section.
- **THE TRANSFER**: the CS player's rows read ONE channel — the serialized
  scanlator — while the details page's rows parse TWO (the scanlator + the
  episode NAME, which carries the bridge's "(Sub)"/"(Dub)" suffix). The CS
  page STRIPS the tags from the rendered names and discards the information;
  whenever the serialized scanlator is blank, every row loses its pills
  while the details page keeps showing them. Two latent serial killers were
  also found in the trace: a multi-line description SPLITS its serialized
  metadata line (the orphaned tail parses as garbage and the episode's WHOLE
  record — title, thumb, date, description, scanlator — silently drops),
  and the episode-list builder had the same newline hole in the name field.
- **THE PARITY GAP**: the player's Elements card mixed the on/off toggles
  with the style-specific multi-state rows (the details screen already
  separates its Cinema/Grid cards); and `gridWatchedCheckmark` existed ONLY
  in the player's preferences — the details GRID coupled its check bubble to
  `dimWatched` (D-720's decoupling never crossed over).

## 3. The fixes

- **D-733 — THE SEGMENT ANATOMY**: `ElementToggleButton` IS a layout-selector
  segment now — the exact `SegmentedToggle` construct: a Box clipped to 8dp
  corners, ON = the SOLID primary fill + onPrimary ExtraBold (the selected
  segment), OFF = TRANSPARENT over the shared container's tint +
  onSurfaceVariant Medium (the unselected segment), the selector's own
  compact 8dp vertical padding, the 220ms color crossfade.
  `ElementToggleGrid` wraps in the selector's own shared container
  (surfaceVariant@0.5, 12dp corners, the 4dp inner padding + gaps) — the
  whole Elements section reads as the layout control's multi-row twin, and
  the fat "list format" pills are gone. The heading-tap 2↔3 testing aid and
  the `el_*` anchors are unchanged.
- **D-734 — THE SYNOPSIS-OFF RIGHT SIDE**: `EpisodeClassicRow`'s bottom
  section renders ONLY with the synopsis on. With it off, the right column
  of the thumbnail stacks the number label → the title plate (weight(1f),
  ONE single line) with the download control at that line's end → the meta
  line with the column's FULL width (the control never interferes with the
  date + SUB/DUB chips — the control placement is the only one that can
  never squeeze the meta line). Both pages render through the one shared
  row; the pure-meta `pillsRowVisible` algebra is untouched.
- **D-735 — THE TRANSFER'S BOTH CHANNELS**: the CS player captures
  `tagByData` — the per-handle flavor tags from the PRE-strip names (the
  raw parsed list) — and UNIONs it with the serialized scanlator in
  `toRowData`'s `subDubLabel` and in the currently-playing card (which
  canonicalizes the union to the SUB/DUB/HSUB token vocabulary — the pill
  can never read "Sub SUB"). Whatever the details page can parse, the
  player's row can too. The serialization itself is hardened both ways: the
  scanlator field falls back to the NAME's "(Sub)"/"(Dub)" tag when the
  episode's own scanlator is blank (the MPV path's name-tagged extensions),
  and EVERY field in both builders is newline-flattened — one serialized
  record per episode, always.
- **D-736 — THE PARITY PASS**: the player's Elements card is PURE (the
  on/off toggles only — "The elements should be in a dedicated separate
  section"); the seven multi-state knobs moved VERBATIM into dedicated
  Grid / Banner cards with the details screen's own appear/disappear
  choreography. The details GRID gains the player's watched-checkmark knob:
  `EpisodeListPreferences.gridWatchedCheckmark` (default true — the
  zero-prefs look is unchanged), the display-style field,
  `EpisodeGridCell`'s D-720 decoupling (the dim owns the grayscale, the
  check owns the bubble), and the GRID-gated "Watched checkmark" element in
  the details Elements grid — mirroring the player's own style-filtered
  entry. The search index gained five entries; both screens' anchor-landing
  maps were re-walked (the audits caught them stale — see §4).

## 4. CI history

- **Run 1 of 2 — `b8055b28` (the implementation push): Build APK run
  36913215895 FAILURE** — the round's ONE compile error, twice over (both
  new style cards): `ColumnScope.AnimatedVisibility` "cannot be called in
  this context with an implicit receiver" — the cards called
  `AnimatedVisibility` directly inside their `item {}` blocks, where no
  ColumnScope receiver exists. The details screen's own Cinema/Grid cards
  carry a wrapper `Column` for exactly this reason — the round-57 **D-498
  "AnimatedVisibility receiver resolution" lesson**, relearned the hard
  way. Both audits (5-a's gotcha sweep + 5-b's restructure walk) missed it:
  the semantic audit verified the cards against the details screen's
  STRUCTURE but not the wrapper Column's RECEIVER role, and the
  compile-risk audit had no precedent-check for bare `AnimatedVisibility`
  in item scope (the repo's only prior item-level uses all lived inside
  card Columns).
- **Run 2 of 2 — `f3e9a67` (the repair): Build APK run 36914176500 GREEN**
  (~3.5 min). The fix: both style cards get the wrapper `Column` — the
  D-498 pattern, no behavior change (an empty Column renders identically
  to a bare invisible AnimatedVisibility).

The pre-push sub-agent audits still paid for themselves: 5-a (compile-risk
— all eleven files PASS on imports/signatures/brackets/modules) and 5-b
(semantic — all four orders PASS) converged on the SAME HIGH defect BEFORE
the push: the search-landing `anchorIndexFor` maps were stale after the
restructure (the player's download/sort would mis-scroll onto the new
cards; the new grid/banner anchors were unmapped → five search entries
would never scroll). Both maps were repaired pre-push, along with 5-b's
LOW finds (the `buildEpisodeListSerialized` newline gap — fixed; four
stale comments — refreshed).

## 5. The device-round checklist (v1.1.70)

1. **The Elements' look**: Settings → Appearance → Episode list (and the
   Player page twin) — the Elements section reads as the layout selector's
   own anatomy: one shared container, compact square-cornered segments, the
   ON segments SOLID theme-colored, the OFF ones quiet. No pills, no fat
   list rows; the heading tap still flips 2↔3 per row.
2. **The classic synopsis-off row**: toggle Synopsis OFF in the Elements —
   every detail sits right of the thumbnail (EP number, the title on one
   single line, the date + SUB/DUB below), NO second section under the
   row; the download control (when on) sits at the title line's end and
   the DUB chip never leaves.
3. **The transfer**: from the details page of a sub/dub series, enter the
   player — EVERY row in the list carries its SUB/DUB pill (not just the
   currently-playing card), in every layout.
4. **The parity**: the player's settings show the Elements card PURE plus
   dedicated Grid/Banner cards that appear/disappear with the layout; the
   details GRID has its own "Watched checkmark" element; both screens'
   search results land on the right cards.

## 6. The sub-agent verification record (the standing order)

- **5-a (compile-risk)**: imports-vs-usage, signature cross-checks (all six
  `EpisodeListDisplayStyle` construction sites, the new pref vs
  `cinemaWatchedCheck`'s exact shape, `CsCurrentlyPlayingSection`'s new
  defaulted param), the `listOf(...).filterNotNull()` inference, bracket
  balance on all eleven files, module boundaries — **PASS**, zero
  compile-blocking defects; the MEDIUM anchor-map catch is §4's story.
- **5-b (semantic)**: D-733 verified trait-by-trait against
  `SegmentedToggle.kt`; D-734's synopsis-on byte-equivalence + the full
  (synopsis × control × pills) matrix; D-735's union traced through
  `buildRowTags.classify()` ("Sub SUB"→SUB, "Kitauji Subs SUB"→SUB) and
  the serialization's fallback chain; D-736's seven knobs verified moved
  VERBATIM (same prefs, options, indices) — **PASS** with the same HIGH
  anchor-map defect + the newline gap (both fixed pre-push).

## 7. The release record

- **release/1.1.70** cut from the ledger head `2363f65`; the bump `7491b8c`
  rides the branch per D-430 (10120→10170, 1.1.20→1.1.70, the comment
  block carrying the round-113 set + the CI history). The annotated tag
  **v1.1.70** (the full user-facing bullet body per D-466) → **Release APK
  run 36914768297 GREEN** (~3.5 min).
- **v1.1.70 / 10170 LIVE 2026-10-01T19:34:35Z** —
  `ani-kuta-v1.1.70-debug-arm64-v8a.apk` (63,542,097 bytes ≈ 60.6 MB) +
  `SHA256SUMS.txt`, stable latest, verified via the API (the releases list
  reads v1.1.70 → v1.1.69 → v1.1.68). No Build APK on the release branch
  (D-472 by design); the docs pushes triggered nothing (paths-ignore
  verified — the ledger commit's push produced no run).
- **Commit chain:** `b8055b2` (impl, CI run 1 FAILED — the D-498 receiver)
  → `f3e9a67` (the repair, CI run 2 GREEN) → `2363f65` (the ledger) →
  `7491b8c` (the release bump, on release/1.1.70) → tag **v1.1.70**
  (Release GREEN) → the mainline twin (this commit).
