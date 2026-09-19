# release/1.1.10 — the branch point (D-485, round 47)

The eighth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `4b8961bd` (CI green).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.10` / `10110` + this record).

## What the release carries (the round-47 fixes on top of the v1.1.9 set)

- **D-483** the demo content system: when the update feed is empty, the
  poster preview AND the test notifications pick RANDOM library content
  that has cached episodes (latest episode as the demo) — re-rolled per
  screen open, plus a "Shuffle preview" action; the honest "No episodes
  available yet" state when nothing qualifies.
- **D-483** the banner art fallback chain: banner → cover → the data-source
  extras' large cover → the episode thumbnail (the v1.1.9 "no actual
  banner" fix — fresh library content has no cached data-axis art).
- **D-483** the staggered test posts: the first immediately, the second
  5 minutes later via DelayedPosterTestWorker (WorkManager, survives app
  death).
- **D-483** the pill exit animation slowed ~40%.
- **D-484** the episode-type toggle on the Notifications screen is the
  design-system SegmentedToggle (parity with the Updates screen).
- **D-483** the library episode badges CENTERED at the top
  (BadgePosition.TOP_CENTER is the new default).

## Version rationale

`1.1.10` is the next number after v1.1.9; `10110 > 10109` — the standalone
debug app updates over its installed v1.1.9 in-app. Main stays at
0.4.20/85 (D-425 discipline).
