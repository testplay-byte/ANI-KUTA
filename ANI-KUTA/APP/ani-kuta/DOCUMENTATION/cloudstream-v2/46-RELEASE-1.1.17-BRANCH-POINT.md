# release/1.1.17 — the branch point (D-498, round 54)

The fifteenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `2b5d7730` (CI green on
  the FIRST implementation run — 35474291318; the pre-push independent
  review round caught the one compile blocker — a local-function forward
  reference in the studio — BEFORE the push, so the implementation needed
  no fix CI at all; the reviewer re-verified the fix ALL CLEAR with a
  standalone kotlinc parse).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.17` / `10117` + this record).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. The ≤2-runs budget (D-472) maps: run 1 = the
implementation push (35474291318, green first try), run 2 = this tag's
Release APK run.

## What the release carries (the round-54 set on top of v1.1.16)

The v1.1.16 device round praised the button look ("the customize button and
the shuffle preview buttons were looking good") and then demanded real
layout planning for the rest: "That screen does not look that well managed,
that well planned, that well handled … You are not considering the layout,
the UI, the top notification area … There should be padding at the top for
the notification bar so that the buttons do not show under it. The save
button: I am unable to click the save button due to it being under the
notification bar"; "the content title are not … aware of their elements
surrounding them so they do not adjust their length properly as needed …
the content title is showing under some corner elements"; and the test
notification's "bottom description could be made simpler … maybe we can
simplify it more to just 'New Episode'." Plus the standing instruction:
"Think about the UI, the layout, the elements, and everything like that
properly." D-518..D-521 (full detail:
`AGENT-CONTEXT/memory/decisions.md`, the round-54 records):

- **D-518 THE STUDIO SHELL REBUILT (the round's headline):** the header
  lived INSIDE the left panel with ZERO inset handling on a forced-landscape
  edge-to-edge screen — the whole control row, Save included, rendered
  UNDER the status bar. The shell is now the canonical editor shape:
  `statusBarsPadding()` once at the root; a FULL-WIDTH header bar
  (FilledTonalIconButton back · "Poster studio" + a gesture-hint subtitle ·
  a labelled OutlinedButton Reset · a labelled primary Button Save — real
  ≥40dp targets, the round's named unclickable Save included); a divider;
  the body Row carrying `navigationBarsPadding()` on the WHOLE body
  (SENSOR_LANDSCAPE can put the bar on either vertical edge); the left
  panel's sections on tonal SectionCards (ELEMENTS / COLOR / OPTIONS —
  planned structure instead of a raw list); the slider strip on its own
  card below the preview. DSL trap recorded: LazyListScope `items{}` cannot
  run inside a plain composable lambda — the ELEMENTS card iterates a plain
  `for`.
- **D-519 THE ELEMENT-AWARE TEXT COLUMNS:** in ABSOLUTE mode the title and
  the episode title wrapped to the right margin regardless of everything,
  so any chip or thumbnail pinned on the text's row painted ON TOP of it.
  `PosterDrawing.awareWrapWidth` ends the column BEFORE the left edge of
  any other visible element sharing the text's vertical band: the band is
  TWO-PASS and text-driven (lines counted at the FULL width first — a
  one-line title is never shrunk by chips that merely sit below it), the
  neighbour rects carry ONLY left/top/bottom (zero measurement →
  deterministic and identical on the composer AND the studio), and a
  candidate narrower than AWARE_MIN_WIDTH=120 (or a neighbour entirely to
  the text's left) is skipped rather than collapsing the column. The
  composer clamps the awareness anchors EXACTLY as it renders (legacy-JSON
  safety); the studio declares the awareness funs BEFORE their first caller
  (Kotlin local funs are not visible above their declaration — the round's
  one CI-validated blocker) and hoists the two wrap widths into composition
  (never per draw frame). THE BONUS CRASH FIX: the composer's thumbnail
  render clamp was the LAST unguarded `coerceIn(0f, H−h)` — at a saved
  thumbnail scale > ~1.78 (the studio's own slider allows 2.0) the box was
  taller than the canvas, the clamp threw IllegalArgumentException, and
  EVERY real notification silently fell back to plain text while the studio
  still previewed a banner; all four clamps are now range-guarded.
- **D-520 THE SHUFFLE THAT ALWAYS SHUFFLES:** the D-494 handshake (exclude =
  the on-stage id) silently DEGRADED into a no-op whenever the on-stage id
  was still null — a tap landing before the first compose finished produced
  a null exclude, which the producer read as "not a shuffle tap" and reused
  the cached selection: the pulse played and NOTHING changed. The handshake
  is now an explicit `shufflePending` flag consumed FIRST on the main
  thread of every producer pass (a tap mid-flight cancels the pass and the
  next pass re-reads the flag), forcing a re-selection on EVERY tap; a null
  on-stage id still reaches the deck path via the "" sentinel. And the
  compose's silent wait is gone: a `shuffling` state (cleared in a finally
  that also runs on cancellation) docks a slim indeterminate progress rail
  to the preview's bottom edge while the new banner composes. The
  pulse/spin/crossfade feedback and the no-repeat deck are untouched;
  toggle flips still never re-select.
- **D-521 THE SIMPLER DESCRIPTION:** `shortDescription()` (the title
  parameter is gone) returns the constant "New Episode" on BOTH banner post
  paths — the headline above it already carries the content's name, so the
  collapsed heads-up card reads TITLE / New Episode with nothing else. The
  non-banner fallback text card keeps its fuller episode text.

## The verification trail

- Implementation: commit `2b5d7730` — self review round 1 (brace/paren
  balance + leftover sweep on all five files), then the independent
  reviewer (kotlinc 2.0.21 parse + reference checks): 1 BLOCKER (the
  forward reference), 3 RISKs (applied: the width hoist, the render-exact
  clamps, the range-guarded thumb clamp — the silent no-banner crash),
  2 NITs (applied: re-indent, the body-wide nav inset). Fix re-verified
  ALL CLEAR.
- CI: run 35474291318 GREEN on the first push (2b5d7730).
- Release: tag `v1.1.17` → the Release APK workflow (run 2 of the budget).
- Docs: the round-54 records (decisions D-518..D-521, progress, changelog,
  handoff §11) committed on the feature branch (64b68a70) and cherry-picked
  here after the release goes green.

## Version math

`10117 > 10116` — the debug app updates over its installed v1.1.16 in-app.
Main stays at 0.4.20/85 (D-425/D-430); the merge to main awaits the user's
explicit confirmation.
