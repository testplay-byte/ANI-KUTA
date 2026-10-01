# Round 112 — The Crash's Safe Measure, The Synopsis-Off Title's New Home, And The Elements' Quiet Pills

**Round type:** device-round response (on v1.1.68) · **Work orders:** D-730,
D-731, D-732 · **Branch:** `feature/round-57-cloudstream-downloads`

> **The sandbox event (third occurrence, disclosed):** the sandbox reset
> AGAIN before this round — `/home/z/ANI-KUTA` and the PAT at
> `/home/z/.secrets/github-credentials` were both gone. The credentials
> were rebuilt (the 93-char PAT verified against the API, HTTP 200), the
> repo re-cloned with the PAT-embedded remote at the round-111 mainline
> twin `b6a17f19`. Nothing was lost — round 111 had been pushed and
> released before the reset. The dev server (`:3000`) survived and serves
> 200s; the worklog survived.

## 1. The device round's verdicts (v1.1.68)

The round shipped THREE findings, one of them a **hard crash**:

1. **THE CRASH** — "when I tried to enter the details page, apparently the
   app was crashing, and it was giving me errors and issues." The log:
   `java.lang.IllegalStateException: Asking for intrinsic measurements of
   SubcomposeLayout layouts is not supported. This includes components
   that are built on top of SubcomposeLayout, such as lazy lists,
   BoxWithConstraints, TabRow, etc.` — twice (20:07 on the details page,
   20:09 in the settings screen), same signature.
2. **THE ELEMENTS' BUTTONS** — "on the player page… as soon as I went to
   the elements section… it was looking way too ugly… I wanted the UI to
   look like simple, like text with themed colored background, nothing
   else more than that. Or maybe you could give them a button-like feel,
   but apparently you did not give it button-like feel or anything like
   that at all."
3. **THE SYNOPSIS-OFF DUB** — "in the classic view… when I hit the
   synopsis, then the dub episode was not being shown, like the dub tag
   was not being shown." The user's prescribed arrangement: "when the user
   has turned off the synopsis, then the title of the episode… will show
   under the section… under the thumbnail image… under the right side
   details… and the download button will show exactly as it is, like
   instead of the synopsis, it would show the title there in one single
   line."

## 2. The root causes

- **THE CRASH (the timeline)**: `EpisodeLayouts.kt:927` — the timeline
  row's card wrapper `Box(Modifier.fillMaxWidth().height(IntrinsicSize.
  Min).clipToBounds())` — asks its subtree for `minIntrinsicHeight`. Round
  111 (D-726) wired the shared `EpisodeMetaLine` into the timeline card's
  Column, and the line was built on **BoxWithConstraints — a
  SubcomposeLayout**, which cannot answer intrinsic queries. The user's
  stored details layout was TIMELINE → entering the details page measured
  the first row → THROW. The same structure renders in the settings
  screens' live preview (the second crash's frame). The compile-only CI
  could never see it — this is the runtime class of bug only the device
  round can catch.
- **THE DUB CLIP (the classic row)**: with the synopsis toggled off, the
  download control relocated INTO the meta line's row
  (`Row { EpisodeMetaLine(weight(1f)); control }`), shrinking the line's
  width budget. The ladder's `TextMeasurer` estimate also omitted the
  chips' `letterSpacing` (0.3/0.4sp) — a few dp undercount per chip. On
  the user's device the budget fell past the ladder's CORE floor, and a
  Row clips its trailing children: **the last chip — DUB — vanished**.
- **THE BUTTONS**: the round-111 `ElementToggleButton` carried a leading
  Check icon with a permanently reserved 18dp slot (even when OFF), a
  boxy 10dp Surface, and a start-aligned Row — busy, unbalanced, and
  neither the "simple text on themed background" nor a real button feel.

## 3. The fixes

- **D-730 — THE SAFE MEASURE**: `EpisodeMetaLine` is now a PLAIN custom
  `Layout` (a fun-interface `MeasurePolicy` — `metaLineLadderMeasurePolicy`).
  The ladder's variants (FULL / INITIALS / CORE-ONLY) are composed as
  sibling Rows and **measured for real in the measure phase with UNBOUNDED
  max width** — each Row reports its true content width (text, letterSpacing,
  capsule padding — no estimate can drift), and the first variant that
  fits the incoming width is the one placed; the last is the floor (the
  core facts stay, the line clips rather than wraps — by design). A plain
  MeasurePolicy answers intrinsic queries through the interface defaults
  (it re-invokes measure over wrapped measurables — it cannot throw), so
  the timeline's `IntrinsicSize.Min` parent gets a sane answer: the crash
  is dead. The `TextMeasurer`, `BoxWithConstraints`, `MetaChipTextStyle`,
  and `MetaChipHorizontalPadding` are all deleted. The empty-line path
  composes a bare `Box(modifier)` so a caller's weight survives.
- **D-731 — THE SYNOPSIS-OFF TITLE'S NEW HOME**: `EpisodeClassicRow`'s
  BOTTOM section now ALWAYS renders — the synopsis plate when the synopsis
  is on, else the **relocated title plate** (the same plate styling as the
  right column's — 14sp Bold, `maxLines = 1`, one single line) — with the
  download control at its end in BOTH cases, exactly as it is. The
  top-right column keeps the title plate only while the synopsis renders
  below. The meta line's row is PURE meta now — the control NEVER joins it
  (the squeeze that clipped DUB is structurally impossible), so the line
  always gets the right column's full width. `pillsRowVisible` loses its
  `showsSynopsis` parameter entirely (the gate is `date || audio` — the
  unit test locks the new algebra); the player adapter's inline gate drops
  its badge clause the same way.
- **D-732 — THE ELEMENTS' QUIET PILLS**: `ElementToggleButton` is a pill
  (`RoundedCornerShape(50)`) of ONE centered Text — the check glyph, its
  reserved slot, the graphicsLayer scale/fade states, and the leading Row
  are all gone. ON = `primary.copy(alpha = 0.20f)` container + `primary`
  text; OFF = `surfaceVariant.copy(alpha = 0.45f)` + `onSurfaceVariant`.
  The 220ms `animateColorAsState` crossfade and the M3 clickable Surface's
  ripple (bounded to the pill, ≥48dp touch) carry the button feel. The
  grid (2/row weight-equal, the heading tap 2↔3, the `el_*` anchors on
  the buttons) is unchanged.

## 4. CI history

- **Run 1 of 1 — `608861a1` (the implementation + docs push): Build APK
  run 36901785174 GREEN FIRST-TRY** (~5.5 min). The pre-push sub-agent
  verification is why: 5-a's compile-risk audit (all six files) and 5-b's
  semantic audit ran BEFORE the commit — and 5-b's HIGH catch (the finite
  measure maxWidth that would have silently killed the S/D ladder) was
  fixed pre-push, not burned on a CI run.

## 5. The device-round checklist (v1.1.69)

1. **The details page opens**: with the TIMELINE layout stored (the crash
   condition), the page loads and renders — no crash, in the real list
   AND in both settings screens' live previews.
2. **The meta line still never breaks**: every layout (classic, grid,
   timeline, tracklist) keeps the date + SUB/DUB on one line; in tight
   spots the tokens still simplify to S/D on their own (the grid cells
   are the easy test).
3. **The synopsis-off classic row**: toggle Synopsis off on either page —
   the TITLE moves below the thumbnail+details block (one single line,
   full width), the download button stays at that section's end, and the
   DUB tag is right there in the meta line with the date and SUB.
4. **The elements' pills**: both Elements cards render simple text-on-
   themed-background pills — centered text, the ON state clearly themed,
   no icons, no boxes-in-boxes; tapping flips the state with a quiet
   color crossfade; the heading tap still flips 2↔3 per row.

## 6. Audit record

- Bracket balance 0/0/0 on all six touched files (comment/string-stripped
  counts: 30/84, 33/95, 179/524, 48/119, 14/93, 32/87).
- The unused-import audit: all clean except the known `getValue`/
  `setValue` by-delegate false positives (kept per the round-110/111
  lesson). `TextAlign` added; `animateFloatAsState`/`size`/`Icons`/
  `Check`/`Icon`/`graphicsLayer` pruned from the widgets file;
  `BoxWithConstraints`/`LocalDensity`/`TextStyle`/`rememberTextMeasurer`
  pruned from the chips file.
- **THE PRE-PUSH SUB-AGENT VERIFICATION (the standing order): two audits
  ran.**
  - **5-a — the compile-risk audit**: all six files PASS — every import
    resolved, the `return@MeasurePolicy` label validated against two
    CI-green in-repo precedents (`return@Comparator`, `return@Runnable`),
    the `Layout`/`MeasurePolicy` signatures matched, zero 4-arg
    `pillsRowVisible` callers repo-wide, all five `EpisodeMetaLine` call
    sites signature-matched, the classic row's structure walked (both
    if/else arms produce exactly one weighted Surface).
  - **5-b — the semantic verification**: D-731 PASS (the arrangement
    walked case by case; the tests hand-traced), D-732 PASS — and for
    D-730 it proved the crash dead by fetching the pinned
    foundation-layout 1.10.4 sources, **but caught a HIGH regression in
    the first draft**: the variants were measured with a FINITE maxWidth,
    and a Row always reports ≤ its incoming maxWidth — so an overflowing
    FULL variant reported a coerced width that "fit", and the ladder
    would ALWAYS pick FULL (AUTO ≡ FULL; the S/D simplification dead;
    tight lines clipping exactly like v1.1.68). FIXED before the commit:
    the loose constraints now carry `maxWidth = Constraints.Infinity`, so
    the Rows report their TRUE content widths and the comparison is
    honest. (Its two cosmetic finds — the measure policy's unused
    `spacing` parameter and the stale `@param downloadControl` KDoc —
    fixed in the same pass.)
- Paper tests: the ladder's width walk with TRUE widths (215dp → FULL
  with DUB visible; 145dp → S/D; ultra-tight → the core floor, clipping
  only there); the classic row's eight combination cases (synopsis ×
  date × audio × control — no case leaves the title homeless or the
  control orphaned); the empty-line Box's weight survival; the
  intrinsic chain walk (IntrinsicSize.Min → Row → Column → custom
  Layout → defaults → chip height — no throw).

## 7. Release record

- **`release/1.1.69` cut from the green mainline head `240be10`** (the
  ledger commit; the implementation `608861a1`'s Build APK run 36901785174
  GREEN FIRST-TRY). The bump `461ba38` rides the branch per D-430:
  10120 → **10169**, 1.1.20 → **1.1.69** (the D-430 comment block in
  AndroidConfig.kt). No Build APK run on the branch — BY DESIGN (D-472).
- **The annotated tag `v1.1.69`** pushed on the release branch — the FULL
  user-facing bullet body per D-466 (the three round-112 bullets).
- **Release APK run 36902691070 GREEN FIRST-TRY** (~4 min).
- **v1.1.69 / 10169 is LIVE**: published 2026-10-01T17:57:00Z —
  `ani-kuta-v1.1.69-debug-arm64-v8a.apk` (60.6 MB) + `SHA256SUMS.txt`,
  stable latest, verified via the API.
- The mainline twin (this commit) mirrors the record; the mainline
  version stays 1.1.20/10120 (D-430 — do NOT "fix" this).
