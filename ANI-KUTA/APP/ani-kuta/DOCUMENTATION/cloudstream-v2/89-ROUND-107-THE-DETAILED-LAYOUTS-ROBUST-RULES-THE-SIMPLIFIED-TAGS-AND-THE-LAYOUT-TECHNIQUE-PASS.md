# Round 107 — The Detailed Layout's Robust Rules, The Simplified Tags, And The Layout-Technique Pass

> Round record 89. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The order (this session's input)

"Then work on and improve the player page episode list **details layout
management** — make the layout rules robust on how things should be shown
and formatted, like **line breaking rules** and other key things like
**simplifying the tags** and **making the UI better**. Make sure that
everything is properly looked into and managed properly. Make sure every
layout technique is used properly, how it should be and how it would make
the things look proper. Make sure that the things are being handled with
care and with planning. Also work on the other optimizations too."

Decomposed:

1. **THE DETAILED LAYOUT (the focus)** — robust, principled rules for how
   every element is shown and formatted.
2. **LINE-BREAKING RULES** — the wrap/truncate policy per element, made
   explicit and robust (not accidental).
3. **SIMPLIFYING THE TAGS** — the metadata pills: deduped, normalized,
   ordered, visually quieter.
4. **MAKE THE UI BETTER** — rhythm, hierarchy, leftovers.
5. **EVERY LAYOUT TECHNIQUE USED PROPERLY** — FlowRow for wrapping,
   weights for flexible columns, min-sizes for grow-able tiles,
   ellipsis for truncation, no-wrap for unbreakable runs.
6. **OTHER OPTIMIZATIONS** — the DRY consolidation + the parity sweep.

## 1. Root-cause research (pre-implementation, verified against HEAD c1bf7ca5)

### 1.1 The line-breaking gaps in the DETAILED row (`PlayerEpisodeRow`)

- **THE TITLE hard-cuts at ONE line** (`maxLines = 1`,
  `TextOverflow.Ellipsis`) — a long episode title ("The One Where
  Something Very Long Happens") loses everything past the width while the
  row still has vertical room to spare (the 2-line title + 1-line pills
  stack = EXACTLY the 68dp thumbnail height at 1× font scale — the row
  height would NOT even grow in the common case).
- **The fallback number tile can wrap mid-number** — the thumbnail-less
  branch sizes a FIXED `44×32dp` Surface but its `Text` has no
  `maxLines`/`softWrap` guard: a wide number ("1000.5" at 12sp
  ExtraBold ≈ 42dp) soft-wraps INSIDE the fixed tile ("1000" / ".5"),
  centered — a mid-number break. NUMBERS MUST NEVER BREAK.
- **A leftover empty `Row` wrapper around the synopsis** (lines 758-783)
  — `verticalAlignment = Alignment.Bottom` + a trailing blank, the
  skeleton of the round-106-retired download-hint glyph. Dead structure.

### 1.2 The tag model's duplication (the "simplifying" root cause)

FOUR copies of the same ad-hoc `buildList` build the pills (DETAILED
~610-618, TRACKLIST ~850-857, GRID ~1189-1196, BANNER ~1626-1633), each
concatenating `audioLabels + subDubLabel + flavorLabels` RAW:

- **THE CS DUPLICATION**: a CS row maps `subDubLabel = meta?.scanlator`
  (frequently the literal string "Sub" or "Dub") AND
  `flavorLabels = ep.flavors` (["SUB","DUB"]) — the row renders
  **"Sub" + "SUB" + "DUB"**: the SAME fact twice in different casing.
- **CASING DRIFT**: the MPV stack parses to canonical uppercase
  ("SUB"), the CS scanlator arrives in title case ("Sub") — one row's
  pills disagree with another's for the identical fact.
- **NO ORDER GUARANTEE**: the concatenation order is
  audio→scanlator→flavors, so the same fact-set renders in different
  orders on different stacks.

### 1.3 The EP tag + the Pill rhythm

- The EP tag ("EP 5", the DETAILED row's only surviving corner tag per
  the round-104 order) is solid primary, 11sp Bold, 6/2dp padding,
  corner 6dp — visually HEAVIER than it needs to be next to the slimmed
  pills; a modest slim (10sp, 5/2dp, corner 5) keeps the identity anchor
  while quieting the row.
- The shared `Pill()` is solid `outlineVariant`, 10sp Medium, 8/2dp
  padding, corner 6dp — tightening to 7/2dp + corner 5dp reduces the
  per-pill footprint without touching legibility (the solid fill stays:
  the BANNER's pills sit on a dark scrim where translucent fills lose
  text contrast).

### 1.4 The cross-layout consistency gaps

- The GRID's fallback number `Text` (26sp) and the BANNER's fallback
  "EP N" (30sp) also lack the never-break guard.
- The TRACKLIST keeps its 1-line title (its compact identity — INTENDED,
  documented as a rule, not a gap), the BANNER's name keeps 1 line
  (scrim space), the GRID's title already wraps to 2.

## 2. THE RULE SET (the round's deliverable — documented in the file header too)

**THE LINE-BREAKING RULES** (per element, per layout):

| Element | DETAILED | TRACKLIST | GRID | BANNER |
|---|---|---|---|---|
| Title / name | **2 lines** + ellipsis (NEW) | 1 line + ellipsis (its compact identity) | 2 lines + ellipsis | 1 line + ellipsis (scrim space) |
| Synopsis | 2 lines + ellipsis | 2 lines + ellipsis | — | — |
| Pills / chips | wrap, never clipped | wrap, never clipped | wrap, never clipped | wrap, never clipped |
| Numbers (EP tag, fallback tiles, the hero numeral) | never wrap | never wrap (exact-fit column) | never wrap | never wrap |

Supporting techniques: a 2-line title + 1-line pill row stacks to
EXACTLY the 68dp thumbnail at 1× font scale (SA1's corrected arithmetic:
36sp title lines + 8dp title padding + 6dp spacer + 18dp pill row = 68dp)
→ the DETAILED row's height stays STABLE through the common wrap cases
(larger font scales grow the row gracefully — the thumbnail
 top-anchors, nothing clips; the SA1-audited geometry). The right column
is a TOP-ANCHORED stack: the pills sit directly under the title (the
pre-existing `SpaceBetween` was INERT — a wrap-content column has no
free space — and is now removed as dead code, zero visual delta).

**THE TAG MODEL** (ONE builder — `buildRowTags` — replacing the four
ad-hoc copies):

- **R-T1 DEDUPE** — case-insensitive: "Sub" + "SUB" is ONE pill.
- **R-T2 NORMALIZE** — the audio vocabulary (SUB / DUB / HSUB) always
  renders in canonical uppercase; a label that CONTAINS a vocabulary
  token ("Kitauji Subs") yields that token — the MPV stack's
  `parseAudioAvailability` semantics, now shared by the CS scanlator
  path (cross-stack consistency).
- **R-T3 ORDER** — date (when shown) leads, then the audio set in
  canonical order (SUB, DUB, HSUB), then any non-vocabulary labels
  (first-seen, original casing — no data loss).
- **R-T4 NEVER CLIPPED** — the builder returns every tag; the FlowRows
  wrap (the round-106 contract stands).

**THE LEFTOVER CLEANUP** — the synopsis's dead `Row` wrapper goes; the
DETAILED fallback tile grows (`defaultMinSize`) while GRID/BANNER's
fallbacks carry the never-break guard; the EP tag slims (10sp, 5/2dp,
corner 5dp); the `Pill` tightens (7/2dp, corner 5dp); the inert
`SpaceBetween` on the DETAILED right column is removed (SA1-F3 —
zero visual delta).

## 3. The implementation plan (one file + the doc)

ALL in `core/designsystem/.../playerlist/PlayerEpisodeListLayouts.kt`
(render-only — no caller, pref, or schema changes; the settings preview
inherits every rule through the same renderer):

1. The `buildRowTags` builder + the `AUDIO_TOKEN_ORDER` constant + the
   file-header ROUND 107 rule block.
2. `Pill()`: 7/2dp padding, corner 5dp.
3. `PlayerEpisodeRow` (DETAILED): shared builder; title `maxLines = 2` +
   `lineHeight = 18.sp`; EP tag slim; the fallback tile
   `defaultMinSize(44, 32)` + never-break Text; the synopsis's dead Row
   removed.
4. `PlayerTracklistRow`: shared builder (title stays 1 line — the rule).
5. `PlayerEpisodeGridCell`: shared builder; the fallback Text
   never-breaks.
6. `PlayerEpisodeBannerCard`: shared builder (chips); the fallback Text
   never-breaks.

## 4. The audit + verification plan

- Sub-agent audit SA1: the layouts file end-to-end (the rule table vs
  the code, the builder's logic, the compose technique usage, the
  geometry stability claim).
- Sub-agent audit SA2: the cross-stack parity (the callers' data shapes
  vs the builder's contract; the settings preview; the search of any
  other Pill/tag consumers).
- CI: push → poll → fix (≤2 runs, D-472).

## 5. The CI history (the D-472 ledger — 1 run, WITHIN the ≤2 budget)

- CI run 1 (**36664738397**, Build APK on 40da8f66): **GREEN
  FIRST-TRY** — the two read-only audits before the push paid for
  themselves again (zero compile blockers, zero logic defects at CI).

## 6. The sub-agent audits (the standing ≥2 order, D-689)

**SA1 — the layouts file (rules + geometry + compile-logic):** PASS, NO
HIGH, NO MEDIUM. Every task verified: the builder's Kotlin constructs
compile-sound (`audio +=` on the captured val MutableSet, `getOrPut` on
the LinkedHashMap, the local `classify` fun, zero unused imports after
the dead-Row removal); the six tag-model traces all correct (the CS
"Sub"+"SUB" dedup, "Kitauji Subs" → SUB, the knob-off date-only path,
HSUB-once, blank-label no-crash, "dub"/"DUB" once); the four call sites
wire the gated dates + the shared knob; the fallback tile verified
against the material3 1.3.1 sources (Surface's propagateMinConstraints
= true → the mins reach the center-Box; short numbers center, wide
numbers grow); the synopsis removal clean; the regression sweep clean
(4 @OptIn sites, TRACKLIST/BANNER 1-line titles, GRID 2-line,
dispatcher/badge/pulse untouched). FIVE LOWs: the 65dp figure was wrong
(the real stack is EXACTLY 68dp — corrected in §1.1/§2 and the file
header); the "tiles grow" plural (only DETAILED grows — corrected);
the PRE-EXISTING inert `SpaceBetween` on the right column (a
wrap-content column has no free space — REMOVED as dead code, zero
visual delta); the blank-date guard (APPLIED to the builder); the
"Hard Sub" spaced-label edge (consistent with the MPV parse —
documented, not fixed).

**SA2 — the callers + parity (blast radius):** PASS, NO HIGH, NO
MEDIUM. The public surface is byte-identical (comment-stripped diff:
one import + two private members + private-body edits only); the MPV
caller (canonical single-token labels) can never duplicate; the CS
caller's four traces verified (the headline "Sub"+["SUB","DUB"] →
[SUB, DUB] fix; "Crunchyroll" survives as an other-label; "Kitauji
Subs" → SUB = the cross-stack parity; "HSUB" once); the settings
preview inherits everything through the public entry points with zero
changes; the repo sweep found NO other consumer of the changed
symbols; the behavior ledger maps every change to the ordered scope
(the GRID's 2-line title pre-existed; the BANNER's check-chip lead and
the dim/badge geometry untouched). THREE LOWs: the merged-label gap
("Sub/Dub" yielded [SUB] vs the MPV parse's [SUB, DUB] — FIXED with
the exact MPV token semantics in `classify`); the provider-name
folding is a visible CS-only change beyond the dedup (documented in
§7); the token-matching semantics now live in five places repo-wide
(a future consolidation candidate — the designsystem already depends
on core:common — NOT done this round, scope discipline).

**Lead-verified + applied:** the MPV-parity `classify` rewrite
(SA2-F1), the blank-date guard (SA1-F4), the inert-SpaceBetween
removal (SA1-F3), and both doc figure corrections. Documented, not
applied: the "Hard Sub" edge, the five-places consolidation note.

## 7. The round-108 checklist (the device round on v1.1.64)

1. DETAILED rows with LONG titles: the title wraps to a second line
   (ellipsis only past two); the row's height stays stable; the pills
   stay under the title.
2. CS rows whose scanlator said "Sub": ONE "SUB" pill — no "Sub"+"SUB"
   duplication anywhere.
3. The pills read date → SUB → DUB → HSUB → (provider) in every layout.
4. The EP tag is slimmer; the pills are tighter; no visual regressions
   on the BANNER's scrim chips.
5. Thumbnail-less rows (if any): the number tile grows for wide numbers,
   never wraps mid-number.
6. A MERGED audio label ("Sub/Dub") on the CS stack: BOTH pills (SUB and
   DUB) — the MPV parse's exact semantics (SA2-F1's fix).
7. Font-scale spot-check: at a larger system font scale the DETAILED rows
   grow gracefully (the thumbnail top-anchors; the title/pills stack
   never clips).
