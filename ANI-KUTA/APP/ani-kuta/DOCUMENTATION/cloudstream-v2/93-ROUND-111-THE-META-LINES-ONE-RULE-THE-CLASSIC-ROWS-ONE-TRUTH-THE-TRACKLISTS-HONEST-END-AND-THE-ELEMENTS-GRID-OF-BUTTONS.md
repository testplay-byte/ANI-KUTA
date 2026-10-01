# Round 111 — The Meta Line's One Rule, The Classic Row's One Truth, The Tracklist's Honest End, And The Elements' Grid Of Buttons

**Round type:** device-round response (on v1.1.67) · **Work orders:** D-726,
D-727, D-728, D-729 · **Branch:** `feature/round-57-cloudstream-downloads`

> **The sandbox event (second occurrence, disclosed):** the sandbox reset
> AGAIN between rounds — `/home/z/ANI-KUTA` and the PAT at
> `/home/z/.secrets/github-credentials` were both gone when this round
> opened. The credentials were rebuilt (the 93-char PAT verified against
> the API), the repo re-cloned with the PAT-embedded remote (the round-106
> convention that survives failed pushes), and the round proceeded from the
> round-110 mainline twin `f724b2e1`. Nothing was lost this time — round
> 110 had been pushed before the reset, exactly as the Session-End
> Checklist demands.

## 1. The device round's verdicts (v1.1.67)

The user's overall verdict was positive — "a lot of things have been
improved, and I am most definitely happy with the results quite a lot this
time… you have properly utilized the tags to everywhere" — with one
structural order: **layout management rules**, first and foremost:

> "the release date and the availability of sub episodes and the
> availability of dub episodes. All of this info will show in a single line
> on the details page and as well as on the player page in the detailed
> layout or the classic layout depending on the page. And I think we should
> go with a proper naming scheme for them, like we should make both of them
> the similar naming, so that it's easier for us… the same will apply to
> the grid view too on both of them. All these three tags will be shown in
> one single line, and there will be no line breaking, never ever. And if…
> there isn't enough space, then what will happen is that it would simplify
> them. Like sub and dub tags will only switch to the first letters, S or
> D, and make it customizable in the future too."

- **The classic/detailed gap**: "In the player page on the detailed view,
  currently the things are apparently not handled that well… the classic
  view on the details page and the detailed view on the player page is most
  definitely not perfect. They are different. So what I want you to do is
  that I want you to utilize the exact same classic layout of the details
  page on the player page too… make sure to link them together properly."
- **The tracklist**: "the release date and the sub and the dub tags need to
  be shown properly and need to be formatted in such a way that they only
  stay on their single line and they do not line break. And one more thing
  about the track list view… it should not show the play button on the
  currently playing episode… just right of the download button, it shows
  the play button. It should not show that play button at all times."
- **The elements**: "having toggles for the elements is most definitely
  not a good idea… a grid layout of buttons which I can click and turn to
  toggle them on or to toggle them off… two options per row… they will be
  in the button kind of format. If I click them, their states will switch…
  I don't feel like there will be any need for description for that…
  implement a simple, beautiful, clean-looking animation… when I click on
  the Elements heading at the top… it will switch the grid layout to Three
  buttons per row. So make sure to give this functionality so I can test
  out how the things will overall look like."

## 2. The root causes

1. **The chips had no line discipline**: every meta-chip site wrapped
   (FlowRows on the player rows + both grids) or could overflow (the
   details CLASSIC's plain Row pushed chips off-screen); no site had a
   space-constrained simplification.
2. **The two "classic" rows were parallel evolutions**: the player's
   DETAILED row predated the D-557 details verdicts (it kept the on-image
   EP tag, the number-box fallback, a 2-line title, a trailing badge
   outside the text column, a 2dp progress underline, a 0.4-alpha surface,
   a static 0.55 dim) — seven deviations from the details CLASSIC anatomy,
   and no shared implementation to stop the drift.
3. **The tracklist's play glyph** predated the current-treatment chrome
   (border + tinted surface + primary number) — pure redundancy.
4. **The Elements cards were SwitchRow lists** — tall, description-heavy,
   and the round's grid-of-buttons order had no widget to receive it.

## 3. The fixes

### D-726 — THE META LINE, ONE LINE EVERYWHERE

New in `EpisodeMetaChips.kt` (the shared episodelist home):

- **`EpisodeMetaLine`** — the date capsule + the type-coded audio capsules
  in ONE horizontal Row that never breaks. Both pages' rows, both grids,
  and the timeline render through this single component ("we should go
  with a proper naming scheme for them… make both of them the similar
  naming, so that it's easier for us" — one name, one implementation).
- **The density ladder** (chosen automatically against the line's REAL
  available width, measured once per row through `rememberTextMeasurer`
  inside `BoxWithConstraints` — narrow phones, landscape, and half-width
  grid cells all adapt on their own):
  1. **FULL** — every chip at its full label;
  2. **INITIALS** — the audio tokens collapse to their first letters
     (SUB→S, DUB→D, HSUB→H — the user's exact spec); other labels keep
     their text;
  3. **CORE-ONLY** — the rare bottom rung for ultra-tight cells: the date +
     the audio initials survive, the other labels drop. The three core
     facts (date + sub availability + dub availability) ALWAYS render and
     NEVER wrap.
- **`EpisodeMetaDensity`** (AUTO / FULL / INITIALS) — the
  future-customization hook the user asked for ("make it customizable in
  the future too"); every call site pins AUTO today.
- `EpisodeAudioChip`'s type map now codes the initials (S keeps SUB's
  primary, D keeps DUB's tertiary, H keeps HSUB's outlineVariant).
- The weighted container composes EVEN WHEN the line is empty — the
  classic row's download-only pills row keeps the control at the END (an
  early-return-before-the-Box bug caught in the paper test).

Wired into: the details CLASSIC row (through the shared row below), the
details GRID (`GridCellChips`), the details TIMELINE (audio-only — the
node owns the date; the card's width makes the compact rung a non-issue,
"exactly as the user predicted"), the player CLASSIC, the player TRACKLIST,
and the player GRID (`PlayerGridChipsFlow`). The BANNER/CINEMA joined pill
was already single-line — untouched (the frozen-surface discipline).

### D-727 — THE CLASSIC ROW, ONE TRUTH (and the naming)

New `EpisodeClassicRow.kt` in `:core:designsystem`'s episodelist package —
the details CLASSIC anatomy **verbatim**, shared by both pages:

- imagery-only 120×68 thumbnail (no EP overlay — the D-557 verdict) with
  the rounded inset watch-progress pill; the 40dp number-disc fallback;
- the SpaceBetween right column: the number-label slot, the one-line title
  plate, the meta line + the download-control slot (at the meta line's end
  when no synopsis, at the synopsis plate's bottom-right when there is
  one);
- the full-width 3dp download-progress bar across the card's bottom;
- the whole-card animated 0.5 watched fade + the thumbnail grayscale.

The player's own chrome survives as parameters: `isCurrent` renders the
primary-tinted surface + the 2dp ring, and the `overlay` slot carries the
arrival pulse. The pages inject their own `downloadControl` (the details'
`EpisodeDownloadControl`, the player's badge) and their own `numberLabel`
(the details' compound-capable `EpisodeNumberLabel`; the player's shared
`EpisodeNumberLabelPlain`) — ONE geometry, two controls, zero drift.

The adapters: the details `EpisodeRow` keeps the display resolution, the
style gates, and `pillsRowVisible` (the unit-locked algebra — the test
stays green, the function stays alive); the player `PlayerEpisodeRow`
RETIRES the EP-tag thumbnail overlay, the number-box fallback, the 2dp
progress underline, the 0.4-alpha surface, and the wrapping FlowRow — the
details anatomy wins, and the player classic row gains the details' inset
pill + the full-width download bar (parity the old row never had).

**The rename**: `PlayerEpisodeListStyle.DETAILED` → `CLASSIC`. The lenient
`fromKey` folds the stored legacy `"DETAILED"` strings (the old pref
default included) into CLASSIC — migration-free; the pref's default string
is now `"CLASSIC"`; the settings picker reads "Classic" (matching the
details page's own picker, "the similar naming"); the caption speaks the
truth ("The details page's Classic row, verbatim"); the layout rules
comments were rewritten to the new contract (the CLASSIC title is ONE
line now — the details anatomy; THE META LINE NEVER BREAKS). All three
player stacks (MPV, CS, the settings preview) parse through `fromKey`, so
the rename touches exactly one enum.

### D-728 — THE TRACKLIST'S HONEST END

The current row's trailing PlayArrow glyph is deleted — the primary border
+ the tinted surface + the primary-toned number already carry "playing";
the glyph's spot was pure redundancy. The watched check stays (it carries
a fact nothing else shows), now with its own conditional 8dp spacer so a
glyph-less row carries no dead trailing gap. The meta line joins through
D-726.

### D-729 — THE ELEMENTS' GRID OF BUTTONS

`EpisodeListSettingsWidgets.kt` (the round-110 shared kit) gains:

- **`EpisodeSettingsCard(onLabelClick)`** — the heading becomes a quiet
  clipped-ripple tap target when the handler is set (the heading stays
  visually identical; the user knows what it does);
- **`ElementToggleEntry`** (title / checked / onToggle / optional search
  anchor) + **`ElementToggleGrid`** (chunked Rows, weight-equal buttons,
  8dp gaps, `animateContentSize` reflow — short rows padded with spacers
  so the widths stay equal) + **`ElementToggleButton`** — an M3 clickable
  Surface (the ripple bounded to the 10dp shape, the 48dp touch minimum)
  whose container and content colors crossfade (220ms FastOutSlowIn) and
  whose leading Check scales 0.2→1 + fades (180ms). **NO descriptions** —
  the round's explicit order.

Both screens:

- the details Elements card = six buttons (Synopsis · Release date ·
  Audio pills · Watch progress · Dim watched · Download buttons) — the
  `el_*` search anchors ride the buttons themselves, and the card keeps
  its LazyColumn item position (the anchor→index map unchanged);
- the player's on/off toggles = a style-filtered grid with the titles
  unified to the details page's own ("Release date", "Watch progress",
  "Dim watched" — the similar-naming order) — Synopsis (CLASSIC+TRACKLIST),
  Release date, Audio pills, Watch progress, Watched checkmark (GRID),
  Episode number + Watched check mark (BANNER), Dim watched;
- the multi-state knobs (the GRID's Currently playing / Episode titles /
  Episode number segmenteds, the BANNER's Number position / Number style /
  Currently playing / the size slider) are NOT on/off toggles — they stay
  rows below the grid, style-gated exactly as before;
- **the heading tap flips the grid between 2 and 3 buttons per row** —
  `rememberSaveable` session state per screen (rotation-safe, never a
  pref — an experiment knob, not a setting, exactly "for the current time
  being").

The search index gained the "classic" keyword on the two player entries.

## 4. CI history

- **Run 1 of 2 — `5fdb4dfd` (the implementation + docs push): Build APK
  run 36860248418 FAILED** on ONE error: `EpisodeRow.kt:247:73 Unresolved
  reference 'collectAsState'`. The D-727 import sweep had pruned it — and
  its twin `getValue` with it (the `by …collectAsState(…)` delegate at
  L248 needs `androidx.compose.runtime.getValue` too, but that diagnostic
  is MASKED until `collectAsState` resolves again — one pruned pair, one
  visible error). The compile order confirmed the rest clean:
  `:core:designsystem` and `:core:preferences` (the new shared
  `EpisodeClassicRow`, the meta line, the player layouts, both pref
  stores) compiled GREEN before the failure; the `:app` module (the four
  settings files) never compiled — it was audited by hand instead (§6).
- **Run 2 of 2 — `bc40bb38` (the CI repair): Build APK run 36872072375
  GREEN.** Both pruned imports restored + the stale-doc sweep riding
  along (comment-level only): the CLASSIC pref docs that still described
  the retired EP-tag overlay and the dead D-554 COMPACT/MINIMAL world;
  the details pref's default string `DETAILED` → `CLASSIC` (every reader
  parses via the lenient `fromKey` — the unit test locks the migration;
  the picker highlight is ordinal-driven, never raw-string-driven); the
  "wrapping FlowRow" comments on both grids now describe the round-111
  single-line meta; the dead `ExperimentalLayoutApi` import + `@OptIn`
  (FlowRow left `EpisodeLayouts.kt` this round); the now-unused
  `height`/`width` imports in the player settings screen. Run 2 of 2 —
  inside the D-472 budget, disclosed here.

## 5. The device-round checklist (v1.1.68)

1. **The meta line, everywhere**: on BOTH pages' classic rows, both grids,
   and the timeline, the date + SUB/DUB chips sit on ONE line and NEVER
   wrap — whatever the width, whatever the tags.
2. **The simplification**: in a tight spot (a half-width grid cell, a row
   with the download control), the SUB/DUB chips collapse to S/D on their
   own — and only when needed.
3. **The classic row, one truth**: the player's classic layout IS the
   details page's Classic row — the quiet EP label above a one-line title,
   no tag on the thumbnail, the disc fallback, the progress pill inside
   the thumb, the download bar across the card's bottom, the whole-card
   watched fade; the picker says "Classic" on both pages.
4. **The tracklist**: the currently-playing row carries NO play glyph
   (the border + tint speak); watched rows keep the check; the meta line
   never breaks.
5. **The elements' grid**: both Elements cards render the on/off toggles
   as equal-width buttons, 2 per row, no descriptions — the colors + the
   check animate on tap; tapping the "Elements" heading flips the grid to
   3 per row (and back).

## 6. Audit record

- Bracket balance 0/0/0 on all nine touched files (comment/string-stripped
  counts).
- The unused-import sweeps: the details `EpisodeRow.kt` adapter's first
  sweep wrongly ate `fillMaxWidth`/`padding`/`dp` (the matcher excluded
  dotted extension usages — `Modifier.padding` needs the import); caught
  by the follow-up grep, fixed. The `getValue`/`setValue` by-delegate
  false positives kept (the round-110 lesson). `EpisodeMetaChips`'
  `remember` and `EpisodeLayouts`' `Arrangement` were genuinely dead —
  pruned.
- Paper tests: the empty-meta-line weight case (above); the download-only
  pills row; the player badge's three placements (the meta line's end, the
  synopsis plate's end, never homeless); the rename's migration path
  (stored "DETAILED"/"COMPACT"/"MINIMAL" → CLASSIC/TRACKLIST via the
  lenient `fromKey`; the picker writes `entries[idx].name` so new writes
  say "CLASSIC"); the ladder's width walk (FULL → INITIALS → CORE-ONLY);
  the tracklist's trailing-glyph gating; the `Downloading(progress: Int)`
  → `.div(100f)` mapping on both adapters.
- The MultiEdit non-atomicity recurred (a two-edit call applied the first
  edit then failed the second, reporting failure with a half-applied
  file); caught immediately by the follow-up read — the python-assert
  pattern (anchor-must-exist before every write) is now the standard for
  surgical text edits.
- **THE PRE-PUSH SUB-AGENT VERIFICATION (the user's explicit order —
  "verify it using sub-agents too before confirming with me"): TWO
  audits ran between the failed run and the repair push.**
  - **3-a — the app-module compile-risk audit**: the `:app` module never
    compiled in run 1 (the build stopped at `:feature:anime-details:impl`),
    so its four files were audited by hand: every referenced symbol
    resolved against the imports (the exact bug class that broke CI), the
    widget-kit call signatures argument-matched, the search anchors
    walked against the LazyColumn item order, the brackets balanced.
    VERDICT: all four PASS — and the audit caught what CI could not show
    yet: the MASKED `getValue` twin behind the one visible error (all 9
    other repo files with `by …collectAsState(…)` delegates import it;
    `EpisodeRow.kt` was the lone exception). The repair push carried both
    imports — saving a third CI run.
  - **3-b — the D-726..D-729 semantic verification**: all four work
    orders PASS with file:line evidence — the meta line is ONE Row that
    physically cannot wrap, with the ladder's three rungs and the
    always-surviving core facts; the classic row is one shared
    implementation called by exactly two thin adapters (the details'
    CompositionLocals injected, the player's chrome parameterized), the
    retired player remnants verified absent, the rename's migration path
    walked; the tracklist row carries no play glyph (the icon survives
    only at the badge/grid-disc/banner-disc sites, all legitimate); the
    elements' grid renders 2/row weight-equal animated buttons with the
    heading-tap 2↔3 flip and the el_* anchors intact at card index 3.
    The stale-doc defects it found (the EP-tag pref doc, the "wrapping
    FlowRow" comments, the dead `ExperimentalLayoutApi`, the details
    pref's "DETAILED" default) were swept in the repair commit.

## 7. Release record

- (to be filled after the release)
