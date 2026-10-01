# Round 110 — The Tags Unified Everywhere, The Number's Place, The Banner's Cinema Truth, And The Settings' One Anatomy

**Round type:** device-round response (on v1.1.66) · **Work orders:** D-722,
D-723, D-724, D-725 · **Branch:** `feature/round-57-cloudstream-downloads`

> **THE SANDBOX EVENT (disclosed):** this round was implemented TWICE. The
> first implementation (local commit `a72cf53d`, 15 files, +1281/−641) was
> complete and self-audited but the sandbox reset destroyed BOTH the working
> tree AND the PAT at `/home/z/.secrets/github-credentials` before the push —
> the commit never reached the remote and is gone. The user re-provided the
> credentials; the round was re-implemented from the surviving research
> record (the shared worklog) against the round-109 head `5bb6f72c`. The
> redo is the shipping implementation; every work order below describes what
> actually landed. Lesson: the Session-End Checklist's "pushed to GitHub"
> line exists for exactly this — an unpushed round is one reset from loss.

## 1. The device round's verdicts (v1.1.66)

The three residual gaps the round closed:

> "the tags are most definitely not good in the player page" — the round-109
> chip unification reached the GRID but the player's DETAILED row, TRACKLIST
> row, and BANNER still rendered the OLD flat-gray 5dp `Pill()` rectangle
> (`outlineVariant`, no type coding) while the details page's rows had carried
> the type-coded capsules since D-556.

- **The banner vs the CINEMA gap**: the player banner still ran its
  round-105 anatomy (14dp corners, a 2-stop 0.78 scrim, a chips FlowRow +
  name, the badge at the corner opposite the number, full-bleed 3dp edge
  bars) while the details CINEMA had matured through D-558/D-559 into a
  richer card (16dp, 3-stop scrim, the meta column with the joined pill,
  BottomEnd badge, inset pills).
- **The settings screens' drift**: the details screen still rendered
  duplicated inner headings ("Layout"/"Cinema"/"Grid" Texts under card labels
  that already said the same) which the player screen had removed in round
  105; both screens carried five private near-identical widget copies; and
  the round-108 Grid card had shifted the LazyColumn's item indices without
  moving the `el_*` search anchors (an off-by-one — a search hit for
  "Synopsis" scrolled to the Grid card).

## 2. The root causes

1. **The rows' chips**: round 109 (D-719) unified the GRID's chips through
   the shared `EpisodeMetaChips.kt` components but only touched the grid
   cell — the two row styles and the banner kept calling the private
   `Pill()`, so the tag complaint survived the round.
2. **The number's place**: both grids rendered exactly one arrangement
   (the label under the thumb). The user's grid-parity doctrine (the two
   grids are ONE design) had no knob for the arrangement itself.
3. **The banner**: it was a *parallel evolution* of the same idea — built in
   round 104/105 from a different spec than the CINEMA card, never reconciled.
4. **The settings**: each screen grew its own widget privates per the
   poster-page's old "no shared visibility" reasoning; the details screen
   never received the round-105 heading cleanup; the anchor map was written
   before the Grid card existed.

## 3. The fixes

### D-722 — THE ROWS' SHARED CAPSULES (Pill() retired)

The DETAILED row and the TRACKLIST row now render the details page's own
chip vocabulary — the quiet date capsule (`EpisodeDateChip`, carrying the
LONG date, exactly like the details CLASSIC row) leading, then the
type-coded audio capsules (`EpisodeAudioChip`: SUB primary / DUB tertiary /
HSUB neutral). The shared TAG MODEL still builds the audio list
(`buildRowTags` with `dateText = null` — the date renders as its own
guarded chip, so the audio-pills knob can never strand it). The BANNER's
chips FlowRow is replaced by the D-724 meta column — and with that, `Pill()`
has ZERO callers and is DELETED. ONE chip language on both pages.

### D-723 — THE GRID'S NUMBER-POSITION KNOB (+ the calmer corner)

`GridNumberPosition` (UNDER_THUMB / BESIDE_DETAILS, lenient `fromKey` — the
D-529 doctrine) joins `core/common`'s `EpisodeGridTitles.kt`; the
`gridNumberPosition` pref joins BOTH stores; both display bundles carry it;
both grid cells render the two mirrored arrangements through new shared
per-side composables — `GridCellTitleLine`/`GridCellChips` (details) and
`PlayerGridTitleLine`/`PlayerGridChipsFlow` (player):

- **UNDER_THUMB** (the default — the zero-prefs look is byte-identical to
  round 109): the themed "EP N" label on its OWN line, the gated mode-aware
  title under it, the chips under that.
- **BESIDE_DETAILS**: the label rides the TITLE's line — the number in its
  own primary ExtraBold typography prefixing the title (the compact
  arrangement the pre-108 player grid carried, now on both grids' shared
  anatomy; with the title gated out the label simply stands alone).

PLUS the shared `GridThumbnailCorner = 12.dp` in `EpisodeMetaChips.kt` —
both cells' plates AND the player's current-episode ring clip through ONE
constant (16dp → 12dp, the calmer corner), so the geometry can never drift.

### D-724 — THE BANNER'S CINEMA, VERBATIM

`PlayerEpisodeBannerCard` rebuilt to the details `EpisodeCinemaCard` anatomy
wholesale: the 16dp corners on a plain `surfaceVariant` plate; the THREE-STOP
scrim (transparent → 0.30 black @ 45% → 0.85); the black-0.30 watched overlay
with the optional centered check in the CINEMA's own z-order (UNDER the ghost
number); the ZERO-PADDED ghost number (`bannerGhostNumber`: "01"…"99", 100+
honest, fractions kept) on all three number texts + the bare plate; the
bottom-left META COLUMN (the 16sp two-line name + ONE joined
"date · audio · WATCHED" pill in the White-0.18 capsule — the WATCHED fact
rides `isWatched && !isCurrent`, the D-720 decoupling: it is a FACT, not the
dim treatment); the download badge at BottomEnd 12dp; and the INSET progress
pills with the CINEMA's `showDownloadButton` gating (the round-108 audit's
SA2-F8 closed — the knob gates the download bar too now). The banner's OWN
knobs (number toggle/position/style, the size scale, the current PLAY/TINT
treatment, the watched-check toggle) all stand. The dispatcher's swipe shape
follows to 16dp.

### D-725 — THE SETTINGS' ONE ANATOMY

NEW `EpisodeListSettingsWidgets.kt` — the shared kit:
`EpisodeSettingsCard` / `SwitchRow` / `SegmentedRow` / `Caption`. Both
screens' five private components (the details' two + the player's three)
are DELETED; every card, row, and caption renders through the kit. The
details screen's duplicated inner headings (Layout/Cinema/Grid) are GONE
(the card label IS the heading — the round-105 player rule, now both
screens'). The round-108 `el_*` anchor off-by-one is FIXED (the elements
map to item 3; the new `grid_title`/`grid_number` anchors map to item 2).
Both Grid sections gain the "Episode number" segmented row
("Below thumbnail" / "Beside title"). Four search entries join the index
(the details' `grid_title`/`grid_number` with their own anchors; the
player's pair under `player_elements`). The remaining static caption texts
(the per-style layout caption, the download note, the sort description, the
details footer hint) render through the shared `Caption`.

## 4. The round-111 checklist (the device round on v1.1.67)

1. **The tags, everywhere**: on the player page, the DETAILED row and the
   TRACKLIST row now show the SAME type-coded capsules as the details page
   (the quiet date capsule + SUB primary / DUB tertiary / HSUB neutral) —
   no flat-gray rectangles anywhere on either page.
2. **The banner = the cinema**: the player BANNER reads as the details
   CINEMA's twin — the 3-stop scrim, the zero-padded ghost number ("01"),
   the bottom-left name + ONE joined "date · audio · WATCHED" pill, the
   badge at the bottom-right, the inset progress pills.
3. **The number's place**: Settings → either page → Grid → "Episode
   number" → "Beside title": the EP label rides the title's line on BOTH
   grids; "Below thumbnail" restores the classic stack. The plates' corners
   read calmer (12dp) on both grids.
4. **The settings' one anatomy**: both episode-list settings screens render
   the same cards/rows/captions; no duplicated inner headings; a settings
   search for "Synopsis" lands on the Elements card (not the Grid card).
5. **The search**: "episode number" / "grid title" find the new knobs on
   both pages.

## 5. The CI history (the D-472 ledger)

(to be filled as the runs complete)

## 6. The audit record

The redo was self-audited line-by-line: bracket balance 0/0 on all 14
touched files (13 modified + 1 new); the stale-reference sweep is clean —
`Pill(`, `EpisodeListCard`, `EpisodeListSwitchRow`, `PlayerListCard`,
`PlayerSwitchRow`, `PlayerSegmentedRow` live only in comments (zero code
refs); 13 orphaned imports pruned across four files (the deleted components'
`Surface`/`Switch`/`RoundedCornerShape`/`ColumnScope`/`Spacer`/
`FontWeight`/`TextOverflow`/`Check`/`formatShortDate`/`Text`/`sp`/
`RobotoFamily` — each verified by occurrence count before removal;
`getValue`/`setValue` kept as the known `by`-delegate false positives);
`@OptIn(ExperimentalLayoutApi::class)` added to the two new FlowRow-carrying
chips composables; the details module's unit test constructs
`EpisodeListDisplayStyle` with named args only (the new defaulted field is
safe); paper tests: the rows' capsule gate combinations (date-only /
audio-only / knob-off), the two grid arrangements under every title mode,
the banner's z-order walk, the ghost-number shapes ("5"→"05", "5.5"→"05.5",
"105"→"105"), the anchor index walk (0 layout · 1 cinema · 2 grid · 3
elements), and the fromKey leniency table.

## 7. The release record (the D-565 loop)

(to be filled at the release)
