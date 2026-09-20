# release/1.1.19 — the branch point (D-498, round 56)

The seventeenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/round-56-polish` head `d592cd12` (implementation
  commit `a0fe0919` + the round-56 docs commit; CI run 35508580132 GREEN on
  the FIRST run — the independent pre-push review had already eliminated the
  compile-risk class, and its one RISK — the templateKey seeding through
  fromKey so a legacy "split" pref lights Duo — was fixed before the push).
- **This branch = that head + ONE commit** (this record; the version bump to
  `1.1.19` / `10119` rode the implementation commit `a0fe0919`).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. This cycle's CI spend: run 1 = the
implementation push (35508580132, GREEN first-try — the no-fix-CI pattern
the D-472 budget wants), run 2 = this tag's Release APK run. The docs-only
pushes (the round-56 memory records) cost nothing (paths-ignore).

## What the release carries (the round-56 set on top of v1.1.18)

The v1.1.18 device round validated the five-template SYSTEM ("I am quite
satisfied with the overall end results ... You have handled it exactly how
I hoped for it to be") and picked its winners — Spotlight "quite good",
Card "the best one which I have seen" — while demanding: "the classic one
is most definitely not good ... it needs to be redone properly"; "not
satisfied with the split one ... we should completely rethink it. We
should also rethink its name"; Minimal's title/height/left-alignment; the
poster-off collapse ("The only toggle which will show is the poster
notification toggle at the very top"); the ONE-LINE description language
across the Notifications/Updates/Settings pages ("there are a lot of
descriptions ... showing on more than one line") plus the trailing divider
lines "which is not good"; the library badge round (prominence shadow, the
badge "a little bit" up, a relatable Total icon — the film-strip "feels
like a chip rather than anything relatable"); and the browse round (overall
polish; "The rating is not shown properly either").

D-528..D-534 (full detail: `AGENT-CONTEXT/memory/decisions.md`, the
round-56 records):

- **D-528 — CLASSIC v2.** The label-first flow on one shared centerline:
  the tag row as an EYEBROW, then the ≤2-line title, then the episode
  title — the whole stack vertically centered, the still card at
  360×202.5. The old top-hung asymmetric composition is dead.
- **D-529 — DUO replaces SPLIT.** The true split-screen: left half
  full-bleed episode art, right half a solid dark panel, a 5px lime seam
  welding them; the text stack vertically centered in the panel. New layout
  AND new name; the "split" pref key aliases to DUO (fromKey), and the
  settings screen seeds templateKey through fromKey so the toggle always
  highlights what renders.
- **D-530 — MINIMAL v2.** The left-aligned floating column: one left edge
  for every line, the title at the shared 44px, tighter gaps, vertically
  centered — the "height reduced", the alignment left.
- **D-531 — THE MASTER-TOGGLE COLLAPSE.** "Poster notifications" owns the
  top card of the poster page; flipping it off fades + shrinks the live
  preview, the Shuffle, the Layout, the Artwork and the Elements away —
  only the toggle remains. The preview producer skips its whole
  selection/compose pipeline while off.
- **D-532 — THE ONE-LINE DESCRIPTION LANGUAGE.** Structural: the shared
  SettingRow description and the MoreListRow subtitle (2→1) clamp to one
  line, every local row helper matches, every wrapping copy on the three
  named pages (plus the Appearance/Details/CustomPalette/VideoCaching
  holdouts) was shortened. The notifications page's four cards lost their
  trailing dividers.
- **D-533 — THE BADGE ROUND.** The Total icon is a PLAYLIST (play triangle
  + episode bars) — the film-strip's white perforations were flattened away
  by the single-color Icon tint (the "chip" reading). The TOP_CENTER float
  is 2dp (was 4); every badge shadow 2→4dp so the counts stop blending into
  the covers.
- **D-534 — THE BROWSE ROUND.** The score tag is the D-480 soft pill (the
  last pointed tag in the app), floating inset with a soft shadow; the hero
  promotes the score into its own amber pill beside the rank pill; every
  carousel cover lifts on a 3dp shadow.

Also in this cycle (research only, NO behavior change): the CloudStream
"Only DASH streams were found" download failure fully diagnosed —
`AGENT-CONTEXT/download-research/17-CS-DASH-DOWNLOAD-RESEARCH.md` is the
next session's launch pad (the DASH filter lives in the CS resolve sheet's
download mode; playback works because Media3 plays mpd natively; the
recommended path is a libmpv dump spike first, Media3 DownloadManager
second).

## Verification expectations

Tag `v1.1.19` → the Release APK workflow builds the debug arm64-v8a APK,
verifies the version match (1.1.19), publishes `ani-kuta-v1.1.19-
debug-arm64-v8a.apk` + `SHA256SUMS.txt` stable + `--latest`. 10119 > 10118:
the debug app updates over its installed v1.1.18 in-app.
