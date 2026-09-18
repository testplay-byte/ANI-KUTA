# release/1.1.4 — the branch point (D-450, round 41)

The second release branch cut from a FEATURE branch (the round-40/41 model
by explicit user instruction; main still carries NONE of this work — the
merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `056373e2` (the round-41
  CI-green head; the branch carries the D-443 pill + its CI rounds, D-444
  the mascot debug launcher, D-445 the arm64-only debug path, and the
  round-41 fixes: D-447 the debug-only release line, D-448 the pill
  redesign, D-449 the wizard's "Draw over other apps" step).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.4` / `10104` + this record).

## THE FIRST DEBUG-ONLY RELEASE (D-447)

Per the user's v1.1.3 device-round correction, THIS repo's releases carry
ONLY the DEBUG build, ONLY arm64-v8a — the rewritten release-apk.yml builds
assembleDebug (the committed debug keystore), hard-gates the ABI to
arm64-v8a, apksigner-verifies the signature, and publishes
`ani-kuta-v1.1.4-debug-arm64-v8a.apk` + `SHA256SUMS.txt` as a stable
release. The standalone debug app (app id `com.confused.anikuta.debug`,
co-installed with the release app) updates itself in-app from exactly this
release line (D-440). The release-signed all-ABI line belongs to the
published repo (Confused-Creature-180/ANI-KUTA) and happens only after
everything is completed.

The v1.1.3 RELEASE (the all-ABI publication) was DELETED from this repo per
D-447 — the git tag remains as history. The debug device line jumps
1.1.2 (10102) → 1.1.4 (10104), still monotonic (installs over anything
installed).

## Version rationale

`1.1.4` is the next number after v1.1.3; `10104 > 10103`. Main stays at
0.4.20/85 (D-425 discipline: the bump lives on the release branch).
