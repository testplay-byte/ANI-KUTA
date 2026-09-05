# Round 35 / Task 75 — the v1.1.2 CI-signed all-ABI release round (the plan)

The user's round-34 feedback, verbatim essence:
1. **The APK downloaded from GitHub was UNSIGNED** — `INSTALL_PARSE_FAILED_NO_CERTIFICATES`
   (they downloaded the CI artifact `app-release-unsigned.apk`).
2. **Upload the keystore to GitHub secrets; CI (GitHub Actions) builds AND
   signs — never build the APK locally** (the core rule, restated).
3. **App icons**: no dedicated "More icons" section (one unified grid),
   custom-image import REMOVED (only provided options), the applied icons
   were CUT OFF on all sides + zoomed (every icon) — "reduce the resolution
   instead of cropping in".
4. **Build ALL ABI release versions** (arm64-v8a, armeabi-v7a, x86, x86_64)
   + **create a zip via GitHub Actions**, properly signed.
5. **A full-fledged starter prompt** for the new release agent (the new
   GitHub repository's manager).

## The decisions

- **D-421 — the adaptive-icon crop fix.** Root cause: round-34's bg layers
  were FULL-BLEED 432px artwork; adaptive icons show only 72/108dp (66.7%)
  and guarantee only the 66dp circle (61.1%) → every icon lost its outer
  third on every side. Fix (per the user's own direction): scale each
  artwork's SUBJECT (adaptive-threshold bbox detection) into the 264px safe
  circle; the surround is an EDGE-CLAMPED continuation of the artwork's own
  border pixels (gradients continue seamlessly — a flat fill failed on the
  mono/sunset/void variants) + a feathered paste; grid previews are now
  PRE-MASKED CIRCLES (288px-circle crop of the final layer) so the picker
  shows exactly what the launcher shows. All 8 variants VLM-verified
  (complete artwork, seamless, good size). Legacy rasters (API<26) untouched
  (never masked).
- **D-422 — the App Icon page simplification.** The custom-image SAF import
  removed COMPLETELY (controller importCustomIcon + the picker + the
  section + the customIconPath preference); the GitHub catalog icons merged
  into the ONE home-screen grid (IconGridEntry sealed type: Baked | Catalog,
  4-per-row rows, refresh button next to the section label); hero + cells
  circle-clipped; the honest note retained for unbaked catalog picks (the
  user accepted the limitation explicitly).
- **D-423 — the CI-signed all-ABI release pipeline.** The keystore + the
  three credentials live in the dev repo's GitHub Actions secrets
  (user-authorized; sealed-box upload verified — 4 secrets live).
  `release-apk.yml` (tag-driven) rewritten: secrets decode →
  `assembleRelease -PreleaseAllAbis=true` (the new `splits` block: one APK
  per ABI + universal; the convention plugin widens `ndk.abiFilters` via the
  same property) → per-APK ABI verification → the HARD `apksigner verify`
  gate (any unsigned/corrupt APK fails the workflow BEFORE anything is
  published) → staged assets `ani-kuta-v{V}-{abi}.apk` +
  `ANI-KUTA-v{V}-RELEASE.zip` (all five + SHA256SUMS.txt) + mapping → the
  stable GitHub release with the which-APK table. `build-apk.yml` (push
  path) now ALSO decodes the secrets and signs its release APK (the
  artifact can never again be an installable-looking unsigned APK) + its
  own apksigner check; fork PRs no-op gracefully. The in-app updater
  (GitHubUpdateSource) is now ABI-AWARE: walks `Build.SUPPORTED_ABIS` →
  `-arm64-v8a`/`-armeabi-v7a`/`-x86`/`-x86_64` asset match → `-universal`
  fallback → first-APK legacy fallback.
- **D-424 — version 1.1.2 / 10102** (monotonic over the zip-delivered
  v1.1.1/10101; the first GitHub release produced by the new pipeline).

## The core-rule update

CORE_RULES §8: D-251's arm64-only SHIPPED-APK rule superseded — shipped
releases carry ALL FOUR ABIs (per-ABI splits + universal); the dev/CI push
path stays arm64-only; release signing lives in the secrets with the hard
apksigner gate; local build/sign stays forbidden.

## Deliverables

1. The branch commit (code + resources + workflows + docs) → CI green.
2. The `v1.1.2` tag → the all-ABI signed release (the round's proof).
3. `RELEASE-AGENT-STARTER-PROMPT.md` (§0–§8, paste-ready for the new agent)
   + the chat delivery of the full prompt.
4. The updated `REPO-SETUP-AND-SIGNING-GUIDE.md` (CI-signs flow, ABI-aware
   updater contract, the v1.1.2+ release routine, the secrets rules).

## Verification checklist (the user's device round)

- [ ] The v1.1.2 release page: 5 APKs + ZIP + SHA256SUMS + mapping; every
      APK installs (no certificate errors).
- [ ] arm64-v8a APK installs over the v1.1.1 zip install (same signature,
      10102 > 10101).
- [ ] App Icon page: one grid (8 built-ins + the repo's icons after the
      icons/ folder exists), no custom-image option, no "More icons"
      section.
- [ ] Applied launcher icons: the FULL artwork visible, nothing cut, not
      zoomed — matches the grid previews.
- [ ] In-app Check for Updates (against the published repo once the user
      re-hosts): the device-ABI APK is picked.
