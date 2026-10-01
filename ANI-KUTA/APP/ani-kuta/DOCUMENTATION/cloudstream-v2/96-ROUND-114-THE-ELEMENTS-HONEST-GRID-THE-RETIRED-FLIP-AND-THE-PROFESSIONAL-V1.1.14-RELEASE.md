# Round 114 — The Elements' Honest Grid, The Retired Flip, And The Professional v1.1.14 Release

**Round type:** device-round response (on v1.1.70) + a professional-release order ·
**Work orders:** D-737 · **Branch:** `feature/round-57-cloudstream-downloads`

> **The good news first:** the v1.1.70 round was "handled much better" — the
> round-113 set landed. This round's findings are ONE defect class in the
> Elements grids plus the long-ordered return of the professional release
> line: "I would like you to do a release version of it too, and the release
> version is supposed to be version 1.1.14."

## 1. The device round's verdicts (v1.1.70)

1. **THE DETAILS PAGE'S GRID IS A LIST** — "on the details page, episodes
   list settings, the elements are not shown properly. Like they should be
   shown in grid format, but they are being shown in list format." The
   player page's own grid "at least looks proper, like they are in a grid
   format" — the defect is details-only.
2. **THE SHIFTING COUNTS** — "when I click on the elements, then the status
   of them changes. Like currently it is showing two, and if I click it,
   then three show. But the values change too alongside with it, which is
   not a good experience. And if there are four showing, if I change, then
   it starts to show me three." (Mapping, verified against the code: the
   D-729 heading-tap flip 2↔3 per row + the list bug halving the visible
   buttons — 7 entries at 2/row = 4 visible chunk-rows, at 3/row = 3; the
   chunking also changes WHICH buttons survive.)
3. **THE THIN HEIGHT** — "their height is not proper. Like they look way
   too much thin, both on the details page one and the player page one."
4. **THE PROFESSIONAL RELEASE** — version **1.1.14**, per the release
   documentation: no code pushed to the release repository, ONLY the APK
   (and ZIP) assets uploaded, the website updated accordingly, properly
   signed.

## 2. The root causes

- **THE LIST-FORMAT BUG (the round's core find):** `ElementToggleButton`
  applied the `RowScope.weight(1f)` modifier to the INNER segment Box — but
  every details-page entry is wrapped in `SettingsHighlightTarget` (the
  `el_*` search anchors ride the buttons themselves, D-729), and the
  wrapper's OWN `Box` (a `BoxScope`, not a `RowScope`) is the Row's direct
  child. A weight modifier is PARENT DATA — it is only read from a Row's
  DIRECT children. The wrapper carried no weight; measured wrap-content, it
  passed the full remaining row width to the inner `fillMaxWidth()` segment
  → EVERY anchored button consumed the whole row and its row sibling (plus
  the trailing spacer) collapsed to zero width. Result: one full-width
  button per chunk-row — a "list" — with HALF the entries invisible
  ("Release date", "Watch progress", "Download buttons" never rendered).
  The player screen's entries carry NO anchorIds (the whole card is the
  `player_elements` target) → its buttons always gridded correctly. The
  user's report matches exactly: the same widget, two different outcomes,
  the anchor wrapper the only difference.
- **THE SHIFTING COUNTS:** the D-729 heading-tap testing aid (flip the grid
  2↔3 per row, session-local) was designed when every button was VISIBLE —
  with the weight bug it changed how many buttons rendered at all (and
  which), reading as broken state ("the values change too alongside with
  it"). Even without the bug, the flip's re-pairing of buttons is no
  longer wanted: the aid's experiment is over.
- **THE THIN HEIGHT:** D-733 matched the layout selector's own compact
  segments (13sp text + 8dp vertical padding ≈ 32dp) — correct for the
  selector's connected pill, too thin for standalone grid buttons.
- **THE RELEASE BLOCKER (historical):** the official re-host
  (Confused-Creature-180/ANI-KUTA — where the professional app's updater
  points, D-440) was blocked since round 39 on the release-agent token
  (the dev PAT is pull-only there). The user provided the account's PAT
  this round — verified admin/push — the blocker is GONE.

## 3. The fixes (D-737)

- **THE WEIGHT HAND-OFF:** `ElementToggleButton` restructured — when the
  entry carries an anchor, the weighted modifier is passed to
  `SettingsHighlightTarget`'s OWN `modifier` parameter (the wrapper Box
  becomes the Row's direct weighted child; BOTH the wrapper's dormant and
  active paths keep the weight on that Box), and the segment fills the
  wrapper (`fillMaxWidth()`). Un-anchored entries (the player's) keep the
  weight directly on the segment. The visuals moved into a private
  `ElementSegment` (one implementation, two entry paths).
- **THE RETIRED FLIP:** `ElementToggleGrid` chunks on a fixed
  `ELEMENTS_PER_ROW = 2` (the user's original v1.1.67 spec: "we can do two
  options per row"); the `columns` parameter, `EpisodeSettingsCard`'s
  `onLabelClick` parameter (the Elements cards were its only users), and
  both screens' `elementsThreePerRow` states (+ the now-unused
  `rememberSaveable` imports) are deleted. The grid is stable: same
  buttons, same pairing, every tap.
- **THE HEIGHT:** the segment Box carries `heightIn(min = 44.dp)` with
  centered content — a proper button height between the retired 48dp pills
  (round 112) and the too-thin 32dp segments (round 113).
- **THE PROFESSIONAL RELEASE v1.1.14** (the release order, §7): the
  release-branch bump 1.1.14/10114 on `release/1.1.14` cut from the green
  head → `release-build-once.yml` dispatched (tag=v1.1.14,
  ref=release/1.1.14) → the five release-signed APKs verified → the
  official re-host: the GitHub release on Confused-Creature-180/ANI-KUTA
  (tag v1.1.14, `Ani-Kuta-<abi>.apk` + `Ani-Kuta-<abi>.zip` +
  `SHA256SUMS.txt`, stable + latest, NO code pushed — assets only) → the
  website's static fallback version refreshed. The debug line continues in
  parallel (v1.1.71, the standing per-round loop) — both app identities
  carry the fix.

## 4. CI history

| Run | Workflow | Commit | Result |
|---|---|---|---|
| 36925093812 | Build APK | eaf9445 (D-737 implementation) | **GREEN, FIRST TRY** |

The round's implementation compiled clean on the first run — the
weight-hand-off change is small-surface (one widget file + two call-site
trims), and both sub-agents audited it before the push. The release runs
(debug v1.1.71 + the one-time all-ABI build) are recorded in §7.

## 5. The device-round checklist (v1.1.71 / v1.1.14)

1. **The details page's Elements card:** a true 2-per-row GRID — every
   button visible (Synopsis, Release date, Audio pills, Watch progress,
   Dim watched, Download buttons + Watched checkmark when the GRID layout
   is selected), equal widths, no full-width rows.
2. **The player page's Elements card:** the same grid (its entries
   style-filtered as before) — and BOTH cards' buttons now a proper
   height (~44dp), not thin strips.
3. **The stability:** tapping the "Elements" heading does NOTHING (the
   flip is retired); toggling any element changes only that button's
   state; switching layouts adds/removes only the style-gated entries
   (GRID's checkmark; BANNER's number/check) with the smooth reflow.
4. **The search landings:** searching an element ("synopsis", "audio
   pills"…) still scrolls to the Elements card and pulses the right
   button (the `el_*` anchors ride the buttons; the maps are unchanged).
5. **The professional app:** install v1.1.14 over the v1.1.3 release
   install (the in-app updater offers it; 10114 > 10103) — the fixed
   Elements grids + everything since v1.1.3, signed with the release key.
6. Regression sweep: the classic row, the four layouts, the settings
   search, the downloads — all untouched this round.

## 6. The sub-agent verification record (the standing order)

- **2-a (compile-risk, read-only): 8/8 PASS, zero HIGH/MEDIUM.** The
  import-vs-usage sweep (the retained `mutableStateOf`/`getValue`/
  `setValue`/`remember`; the removed `rememberSaveable` clean in both
  screens; the new `heightIn` import); the repo-wide signature cross-check
  (`onLabelClick`/`columns`/`elementsThreePerRow` = 0 hits; both
  `ElementToggleGrid` callers updated; all 12 `EpisodeSettingsCard` call
  sites label+content-only); the const's Kotlin validity; the modifier
  chain order; brace balance on all three files. **The new audit class** —
  weighted children under non-RowScope wrappers — swept ALL 773 `.weight(`
  occurrences in the app module: every custom composable that receives a
  weighted modifier applies it to its ROOT node; NO other latent instances
  of the bug class exist.
- **2-b (semantic, read-only): 7/7 PASS.** The weight parentage traced
  through both wrapper paths (dormant `Box(modifier)` / active
  `Box(modifier.clip.drawBehind)`); the fixed grid + the flip's total
  absence verified; the 44dp math; both screens' entry sets; the search
  anchors + both `anchorIndexFor` maps re-verified by LazyColumn item
  count (details: 5 items, `el_*`→3; player: 6 items, `player_elements`→1)
  — unchanged and correct; the behavior walk ([2,2,2,1] / [2,2,2] /
  [2,2,1]); the regression sweep. One LOW cosmetic KDoc typo ("odifier]")
  — fixed before the commit.

**The lesson banked:** a `RowScope`/`ColumnScope` weight modifier passed
INTO a composable that wraps content in its own layout node is DEAD —
parent data is only read from the parent's DIRECT children. The wrapper
must take the weight on its own root (exactly what
`SettingsHighlightTarget`'s `modifier` parameter is for). The compile
audit's new standing check: grep for weighted modifiers crossing a
composable boundary whenever a wrapper gains weighted content.

## 7. The release record

*(appended by the twin commit after both releases are LIVE)*
