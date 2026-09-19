# release/1.1.11 — the branch point (D-490, round 48)

The ninth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `82d5f1a2` (CI green —
  runs 35447640042 on the code + 35448111350 on the renumber).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.11` / `10111` + this record).

## What the release carries (the round-48 fixes on top of the v1.1.10 set)

The v1.1.10 device round's five findings (the handoff:
`AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md`):

- **D-486** the poster pipeline hardening — `EpisodeBannerComposer.buildBanner`
  runs its whole body on `Dispatchers.IO` (the blocking SQLDelight reads left
  the MAIN-dispatch `produceState`/`rememberCoroutineScope` path — the failure
  class behind BOTH the "Couldn't load the preview art" state AND the
  plain-text test notifications); the preview's produceState and the test
  notifications' demo scan run on IO too; every art load is time-bounded
  (`withTimeoutOrNull` 12s — Coil's memory→disk→network order already serves
  the 500MB disk cache offline-first; the fallback chain still yields a
  banner after a timeout); `CancellationException` is rethrown, never
  swallowed; the failure log carries the exception CLASS so the next device
  round needs one logcat line.
- **D-487** the preview's "No episodes available yet" state is reachable —
  the old `failed && !hasContent` gate contradicted the empty path's
  `failed = false` and rendered an eternal spinner; the gate is
  hasContent-first now. The demo-picker contract verified: feed-first → a
  random library content WITH cached episodes (re-rolled per open + the
  Shuffle action) → the honest empty state; no invented "Sample" text.
- **D-488** the Notifications screen's episode-type block mirrors the
  Updates screen EXACTLY: title + description on top, the FULL-WIDTH
  SegmentedToggle below (the trailing-slot 200dp squeeze is gone); the SAME
  shared `UpdatePreferences` keys — both screens stay in sync.
- **D-489** the library badge positions actually reach the screen — the four
  `CoverBadgeRow` call sites hard-coded TOP_START/TOP_END, so the v1.1.10
  TOP_CENTER default never rendered; they now pass the ViewModel's
  `episodeBadgePosition`/`scoreBadgePosition`, and the centered row floats
  4dp inside the top edge.

## Version rationale

`1.1.11` is the next number after v1.1.10; `10111 > 10110` — the standalone
debug app updates over its installed v1.1.10 in-app. Main stays at
0.4.20/85 (D-425 discipline).

## Release mechanics (unchanged, D-447/D-472)

- The tag `v1.1.11` triggers `release-apk.yml` → the DEBUG arm64-v8a APK
  (`ani-kuta-v1.1.11-debug-arm64-v8a.apk` + `SHA256SUMS.txt`), stable +
  `--latest`.
- The release body = the tag annotation's user-facing What's New bullets
  (D-466).
- NEVER merge to main — that happens only when the user says so.
