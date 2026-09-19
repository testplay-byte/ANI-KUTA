# release/1.1.8 — the branch point (D-471, round 45)

The sixth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `efcccf24` (CI green).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.8` / `10108` + this record).
- **THE FIRST RELEASE under the D-466 notes process:** the tag annotation
  carries the USER-FACING bullet list, and release-apk.yml turns the tag
  body into the release notes — the app's What's New shows ONLY those
  clean bullets (no commit log, no duplicate headers, no tables).

## What the release carries (the round-45 fixes on top of the v1.1.7 set)

- **D-466** clean What's New notes (this release is the first to use them).
- **D-467** the exit-fullscreen button: standalone (out of the cluster tray)
  with its theme-color chip restored.
- **D-468** the pill window +40dp/24dp slack — the persistent side/top-bottom
  clipping of the grown pill and the exit bubble fixed.
- **D-469** the seekbar thumb's border ring 2dp + black-40% (thinner, darker).
- **D-470** the seek pill per-orientation: portrait keeps it AT the sides
  (40dp), fullscreen keeps it inward (96dp).

## Version rationale

`1.1.8` is the next number after v1.1.7; `10108 > 10107` — the standalone
debug app updates over its installed v1.1.7 in-app. Main stays at
0.4.20/85 (D-425 discipline).
