# release/1.1.13 — the branch point (D-497, round 50)

The eleventh release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `24be155b` (CI green —
  run 35455000790, the round-50 implementation, GREEN on the first push).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.13` / `10113` + this record).

## Process change for THIS release (D-498 — the user's standing instruction)

The release IS the verification build. The previous rhythm (implementation
CI → a device round on the FEATURE build → only then the release cut) is
RETIRED by the user's instruction: "directly try to do the debug release
rather than trying to build it separately… If the release fails we will
know that the APK was not built properly." From now on, after the
implementation push is CI-green the release branch is cut and tagged
IMMEDIATELY, and the user tests on the RELEASE APK via the in-app updater.
The ≤2-runs budget (D-472) maps naturally: run 1 = the implementation push
on the feature branch, run 2 = the tag's Release APK run. A failed release
run is diagnosed from the RELEASE CI logs like any other.

## What the release carries (the round-50 presentation set on top of v1.1.12)

The v1.1.12 device round APPROVED the pipeline ("the results this time are
much more satisfactory — everything is exactly how I hoped for them to be")
and flagged the presentation. Fixes D-493..D-496 (full detail:
`AGENT-CONTEXT/memory/decisions.md`, the round-50 records):

- **D-493 THE BANNER IS A BANNER:** the canvas drops from 1024×576 (16:9)
  to 1024×440 (≈21:9, the classic banner proportion) with a ZONE-ANCHORED
  layout — the text block hangs from the top (title → EPISODE hero →
  episode title), the SUB/DUB chips + the ANI-KUTA wordmark share a bottom
  baseline, and the thumbnail fits aspect-preserved into a 210×330
  right-centered box (any shape fits; nothing can overflow, no matter how
  long the text). THE "GLITCH" root-caused: the old `drawCenterCrop`'s
  `BitmapShader` sampled the bitmap at its NATIVE pixel size, so any source
  smaller than the canvas (every portrait cover) tiled with
  `TileMode.CLAMP` and smeared the right/bottom edge pixels across the
  banner. Replaced with the classic src→dst cover-crop draw (correct for
  ANY input geometry) + Coil `Scale.FILL` background decodes (the covering
  region decodes sharp instead of a fit-box upscale). The no-art stage
  became a styled dark gradient with a soft lime glow.
- **THE SUB/DUB CHIPS:** the engine writes `audioVariant="unknown"` for
  both-variant releases — the old chip gate matched only sub/dub, so the
  badge VANISHED on most demos. Demo paths normalize unknown→sub/dub (the
  demo-honesty rule); the real path stays truthful (unknown → no chip);
  chips are case-insensitive; "both" renders TWO chips; labels center via
  FontMetrics (the old `textSize/3` guess sat the label visibly low).
- **D-494 SHUFFLE ACTUALLY SHUFFLES:** the D-483 feed-first path always
  re-picked the same newest feed row — the button looked dead. Shuffle now
  records the on-stage item into a one-shot exclusion, skips the feed, and
  picks a random LIBRARY item excluding it (two-pass picker: the exclusion
  is a preference, not a rule; a single-entry library degrades to a no-op
  shuffle). The selection cache makes toggle flips re-render the on-stage
  content with the new prefs instead of re-selecting.
- **D-495 THE TEST NOTIFICATIONS:** the user's verbatim spec — the feed is
  no longer consulted for tests; the LIBRARY is the source: a random entry
  for the first post, ANOTHER random entry for the second (any entry
  qualifies; fewer entries cycle with a different episode so both posts go
  out and never read identically); an EMPTY library gets pure-demo payloads
  so the tester ALWAYS delivers. Both posts are full composed posters (the
  delayed post's title rides the WorkManager data).
- **D-496 THE FORMAT:** both post paths share `episodeLabel` — "EP 12",
  never "EP 12.0"; fractional specials survive ("EP 12.5"); the composer's
  hero line gets the same treatment ("EPISODE 12.5").

Offline-first is unchanged and is the point (memory → 500MB disk cache →
network with a 12s cap per image; details/episodes SQLDelight-local;
missing art composes the styled dark stage — D-491's software-safe
pipeline untouched).

## Version rationale

`1.1.13` is the next number after v1.1.12; `10113 > 10112` — the standalone
debug app updates over its installed v1.1.12 in-app. Main stays at
0.4.20/85 (D-425 discipline).

## Release mechanics (unchanged, D-447/D-472/D-466)

- The tag `v1.1.13` triggers `release-apk.yml` → the DEBUG arm64-v8a APK
  (`ani-kuta-v1.1.13-debug-arm64-v8a.apk` + `SHA256SUMS.txt`), stable +
  `--latest`.
- The release body = the tag annotation's user-facing What's New bullets
  (D-466): bullets only — no hashes, no "## What's New" header, no
  download tables (the in-app update sheet renders the body raw).
- NEVER merge to main — that happens only when the user says so.
