# release/1.1.2 — the branch point (D-442, round 39)

The FIRST release branch cut directly from main (the round-39 model):

- **Cut from:** the round-39 main head `c1b8e747` (feat(task79): D-439..D-441).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.2` / `10102` + this record).
- **Everything the release needs is inherited from main** — the build line
  (D-430), the debug-only push path + the tag pipeline (D-435), the R8
  retirement (D-436), the round-37/38 feature set, and the round-39 fixes:
  - **D-439** the total-when hardening (the library-open
    NoWhenBranchMatchedException crash fixed at all 7 dispatches).
  - **D-440** the build-type update repo (debug → testplay-byte/ANI-KUTA,
    release → Confused-Creature-180/ANI-KUTA) + the ABI-aware asset picker.
  - **D-441** notifications OFF by default.

## The release routine (this branch)

1. Push this branch → build-apk.yml runs the debug-only push path (green
   required).
2. Tag `v1.1.2` (annotated) on this branch's head → release-apk.yml:
   the all-ABI SIGNED release (arm64-v8a / armeabi-v7a / x86 / x86_64 /
   universal + ANI-KUTA-v1.1.2-RELEASE.zip + SHA256SUMS.txt, no mapping.txt)
   published stable on testplay-byte/ANI-KUTA.
3. Re-host to Confused-Creature-180/ANI-KUTA (the published repo — where the
   RELEASE build's in-app updater now checks, D-440): the exact
   version-prefixed asset names, SHA256SUMS verified, stable + latest.

## Version rationale

`1.1.2` is the next number after v1.1.1 — the exact number
BUILD-AND-BRANCH-GUIDE §5 anticipated for the first main-cut release, cut by
the user's explicit round-39 instruction. The round-35 v1.1.2 attempt was
deleted before any user install (the user's certificate-error report was on
a CI artifact, never a release APK), so the number is clean.
`10102 > 10101` — the release installs OVER the installed v1.1.1 (no
uninstall churn). Main stays at 0.4.20/85 (D-425: the bump lives on the
release branch).

`release/1.1.1` (the long-lived-branch model) stays for reference — do NOT
build new releases there. Future releases repeat THIS model: fixes land on
main → `release/<next>` cut from the round's main head → tag → re-host.
