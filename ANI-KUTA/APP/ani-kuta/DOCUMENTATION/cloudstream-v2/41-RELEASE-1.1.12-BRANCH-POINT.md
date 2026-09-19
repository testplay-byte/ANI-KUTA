# release/1.1.12 — the branch point (D-492, round 49)

The tenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `3614ed9c` (CI green —
  run 35450863700).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.12` / `10112` + this record).

## What the release carries (the round-49 fix on top of the v1.1.11 set)

The v1.1.11 device round returned ONE failure — the same poster-pipeline
error the user had been seeing — but this time WITH the logcat that closed
the case (the library badges were APPROVED; untouched this round):

- **D-491** the poster pipeline's REAL root cause — the device stack:
  `IllegalArgumentException: Software rendering doesn't support hardware
  bitmaps` at `EpisodeBannerComposer.drawCenterCrop` ← compose ← buildBanner.
  Coil decodes into `Bitmap.Config.HARDWARE` by default on API 26+; a
  HARDWARE bitmap cannot be drawn on the composer's SOFTWARE canvas
  (`Bitmap.createBitmap`) — the first draw with the art threw, the catch
  nulled the whole banner, and the preview's failed message blamed the
  user's (perfectly fine) connection. This is what actually failed the live
  preview + the test notifications + the real notifications in v1.1.10 AND
  v1.1.11 (D-486's dispatcher/timeout hardening was real-but-orthogonal;
  the "toBitmap converts hardware bitmaps" comment was false — verified
  against the coil3 3.0.4 bytecode, where a BitmapImage is returned as-is).
  THE FIX (three layers at the loadBitmap choke point): (1)
  `bitmapConfig(ARGB_8888)` on every composer ImageRequest — fresh decodes
  software-safe, and the engine's `isCacheValueValidForHardware`
  memory-cache validation rejects hardware-backed entries and re-decodes
  them from the disk cache (the UI's own loads can never poison the
  composer); (2) `ensureSoftwareSafe()` — the never-crash last line: any
  stray HARDWARE bitmap is copied to ARGB_8888 (copy failure → null → the
  thumb → flat-dark-stage fallback chain still yields a banner); (3) the
  preview's failed message reworded honestly ("The preview hit an
  unexpected error — tap Shuffle to try again.") — with art availability
  unable to fail the banner, failed=true can only mean an internal
  exception; there is no connection angle at all.

Offline-first is unchanged and is the point: the details/episode data are
SQLDelight-local, and art resolves memory → 500MB disk cache → network with
a 12s cap per image; a truly missing image composes the dark-stage banner
instead of failing anything.

## Version rationale

`1.1.12` is the next number after v1.1.11; `10112 > 10111` — the standalone
debug app updates over its installed v1.1.11 in-app. Main stays at
0.4.20/85 (D-425 discipline).

## Release mechanics (unchanged, D-447/D-472)

- The tag `v1.1.12` triggers `release-apk.yml` → the DEBUG arm64-v8a APK
  (`ani-kuta-v1.1.12-debug-arm64-v8a.apk` + `SHA256SUMS.txt`), stable +
  `--latest`.
- The release body = the tag annotation's user-facing What's New bullets
  (D-466).
- NEVER merge to main — that happens only when the user says so.
