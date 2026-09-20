# release/1.1.18 — the branch point (D-498, round 55)

The sixteenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/poster-templates` head `827440c1` (CI green
  after ONE fix run: the implementation push 35502594912 failed on a single
  compile error — `drawCenteredText`'s unqualified `textSize = textSize`
  inside `Paint.apply{}` resolved the LHS to the shadowing function
  PARAMETER, a val — fixed by qualification in 827440c1, green on
  35502967752; the pre-push independent review had already caught and fixed
  the two structural blockers — 4× nullable-episodeTitle smart casts and
  PosterCanvasMetrics still living in the deleted PosterLayoutConfig.kt).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.18` / `10118` + this record).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. This cycle's CI spend: run 1 = the
implementation push (35502594912, RED — the one shadowing error), run 2 =
the fix push (35502967752, GREEN), run 3 = this tag's Release APK run. The
one-fix-push pattern (rounds 50–53) is the honest handling of a red
implementation run; the reviewer's structural blockers were caught BEFORE
any push, so the fix run cost exactly the one error the static analysis
could not see (name resolution inside an `apply` receiver scope).

## What the release carries (the round-55 set on top of v1.1.17)

The v1.1.17 device round REVERSED the customization course, verbatim:
"I am not satisfied with the poster banner notifications at all. I think we
are doing a little bit overboard with these things ... We should not give
the users that much customizability. Giving them options for the
notification poster and giving them full customizability flexibility is not
a good option ... I am proposing that we should remove the poster studio
functionality completely. Instead of that we should provide the user with
some predefined templates for the notification poster." Plus the four
section-level asks: "There is a lot of padding on the right and left sides
of it"; "The description should only be one line"; "The shuffle preview: I
would like you to improve the UI of the button ... much better, much more
well-defined, and much more proper. Also simplify it to just shuffle"; "The
live preview at the top will never disappear. It will be stationary. Only
the bottom section will be scrollable. The top live preview section will be
kept exactly as it is."

D-523..D-526 (full detail: `AGENT-CONTEXT/memory/decisions.md`, the
round-55 records):

- **D-523 — THE POSTER STUDIO RETIRED.** PosterCustomizeScreen.kt (1,487
  lines) deleted; PosterCustomizeKey + its nav branch + onOpenCustomize
  deleted; PosterLayoutConfig.kt (the free-form element model) deleted; the
  composer's ABSOLUTE mode deleted (composeAbsolute, drawScaledChip,
  awarenessRects, loadEditorArt/PosterEditorArt); posterLayoutJson /
  `notif_poster_layout_json` deleted (legacy saved layouts are simply
  ignored — no crash, the banner renders the template); PosterDrawing lost
  typefaceFor + awareWrapWidth + the AWARE_* constants. PosterCanvasMetrics
  moved into PosterTemplate.kt.
- **D-524 — THE FIVE PREDEFINED TEMPLATES** (`PosterTemplate` enum, one
  deterministic composer renderer each, all on the shared primitives):
  *Classic* (the approved v1.1.14 look, unchanged), *Spotlight* (the
  billboard: bottom-left text stack, bottom-right small still card whose
  left edge ends the text's wrap width — no overlap by construction,
  branding top-right), *Split* (the magazine cover: tall 420×344 left
  panel, vertically centered right column, ≤3-line title), *Minimal* (the
  symmetric poster: everything centered, no card), *Card* (the info panel:
  a dark rounded panel docks to the bottom, text inside, the still pokes
  above its left edge). Chosen via a FIVE-WAY segmented toggle; pref
  `notif_poster_template` (default classic; lenient fromKey — unknown →
  CLASSIC, never a broken banner).
- **D-525 — THE SCREEN REBUILT.** The live preview + Shuffle are STATIONARY
  above the LazyColumn (only the options scroll; the preview's card label,
  2.56:1 ratio and all seven render states preserved). The 32dp/side
  stacked padding (list 16dp + SettingsGroupCard 16dp) replaced by ONE 8dp
  gutter via the local PosterCard — the preview sits 16dp from each edge,
  the option rows 24dp. Every description is ONE line (short strings +
  maxLines=1 + ellipsis). The Shuffle = one full-width 44dp filled
  ExtraBold button labeled exactly "Shuffle" (the Customize button died
  with the studio). Option groups use the episode-type block's structure
  (title + one-line description above, full-width SegmentedToggle below);
  SegmentedToggle gained the backward-compatible `compact` (12sp) mode.
- **D-526 — THE THREE-WAY ARTWORK SOURCE.** The "Prefer cover art" switch
  became a three-way segmented toggle over posterBackgroundSource:
  *Auto* ("banner", the approved default), *Cover*, *Episode* (NEW — the
  episode's own still fills the stage first, loaded ungated by the card
  toggle but only when the gate made the first chain walk a no-op — the
  review's dead-CDN double-ladder fix).

## Verification (the v1.1.18 device checklist lives in progress.md)

1. The preview NEVER scrolls away — only the Layout/Artwork/Elements cards move.
2. The preview + rows sit noticeably closer to the screen edges.
3. The five templates render genuinely different arrangements; the real test
   notification matches the picked template.
4. Artwork Episode puts the episode's still on the stage (with the card
   toggle off, too).
5. One big full-width "Shuffle" button; every tap changes the content.
6. One-line descriptions everywhere.
7. No Customize button, no landscape editor anywhere.
8. Element switches gate their elements in every template (Minimal has no
   card by design).
9. A legacy studio layout is ignored silently — classic renders, no crash.
