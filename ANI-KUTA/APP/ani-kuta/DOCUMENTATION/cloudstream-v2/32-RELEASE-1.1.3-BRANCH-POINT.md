# release/1.1.3 — the branch point (D-446, round 40)

The first release branch cut from a FEATURE branch (the round-40 model —
one-off by explicit user instruction, NOT a change to the D-442 default):

- **Cut from:** the `feature/ads-return-pill` head `789f4e44` (the D-444-rev
  commit; the branch also carries D-443 + its CI rounds and D-445). The
  FIRST cut (from `5413b7e4`) was discarded BEFORE any publish or install —
  the canceled release run + the deleted tag/branch (the round-35/36
  clean-number precedent) — because the user revised the D-444 artwork
  mid-round (the square 1024×1024 second upload).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.3` / `10103` + this record).
- **Why from the feature branch:** the user's round-40 instruction — "after
  you have successfully done this and also you have done the new release
  version of it from the new branch, then I will test it out" — the release
  exists so the user can device-test the round-40 work. D-442's
  cut-from-main flow remains the DEFAULT for future releases; this is the
  documented one-off. main has NOT received the feature merge — merging
  awaits the user's explicit confirmation after the device round.

## What the release carries (all inherited from the feature branch)

- **D-443** the smart-link floating return pill: when the user taps Continue
  and is redirected to the sponsor, a theme-colored animated capsule floats
  over the browser — a circular countdown ring of `minTimeOutsideMs` + a
  Go-back chip; on countdown completion it crossfades to "You can go back
  now" + pulse. Tapping (any time) brings ANI-KUTA back to the foreground,
  where the existing return gate completes the ad (or Try-again).
  Requires the overlay permission (Settings.canDrawOverlays) — without it
  the pill silently does not appear and the ad flow behaves exactly as
  before.
- **D-444** (as revised) the debug launcher = the user's mascot artwork
  `USER-UPLOADS/IMG_20260918_233212.png` (1024×1024 square — zero padding,
  zero crop, zero distortion at every size): rasters mdpi→xxxhdpi
  (ic_launcher + ic_launcher_round), the adaptive full-bleed background,
  the App Icon hero. The RELEASE launcher (kawaii-mouth adaptive set) is
  untouched — the debug source set only overrides debug builds.
- **D-445** the debug path is arm64-v8a ONLY (the x86_64 emulator exception
  retired end-to-end). The RELEASE line keeps all four ABIs (D-423).
- Debug update source re-affirmed (D-440): the debug build checks
  testplay-byte/ANI-KUTA for new versions at runtime.

## The release routine (this branch)

1. Push this branch → build-apk.yml runs the debug-only push path (green
   required).
2. Tag `v1.1.3` (annotated) on this branch's head → release-apk.yml:
   the all-ABI SIGNED release (arm64-v8a / armeabi-v7a / x86 / x86_64 /
   universal + ANI-KUTA-v1.1.3-RELEASE.zip + SHA256SUMS.txt, no mapping.txt)
   published stable on testplay-byte/ANI-KUTA.
3. Re-host to Confused-Creature-180/ANI-KUTA (where the RELEASE build's
   in-app updater checks, D-440) — the re-host needs the release-agent
   credentials (the dev PAT has no write access there; same as v1.1.2).

## Version rationale

`1.1.3` is the next number after v1.1.2; `10103 > 10102` — the release
installs OVER the installed v1.1.2 (no uninstall churn). Main stays at
0.4.20/85 (D-425 discipline: the bump lives on the release branch, and main
has not received the feature merge).
