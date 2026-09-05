# Round 38 / Task 78 — The release-hygiene round (the user's v1.1.1 device report)

The user's round-38 report, parsed into its four findings + the standing
instructions:

1. **The main-branch artifact contained BOTH a debug AND a release APK.**
   The user was "not ready for" a release build from main: *"From the main
   branch and any other branch, it should only build the debug version, and
   it should not build the release version alongside it."*
2. **The App Icon page (debug build) carried too much description text.**
   *"Remove the whole description from there, like 'couldn't reach the icon
   folders, check your connection,' and everything like that. Completely
   remove it, and also the bottom one, which says 'Icons come from the
   repository. Is the icon folder on GitHub? Refresh to pick up new ones.'
   This is not needed; just remove it outright. It should just say, 'There
   aren't any icons yet.'"*
3. **In the debug version, the app icon was shown as the release version's**
   (the App Icon page hero displayed the kawaii release artwork instead of
   the debug identity).
4. **The RELEASE builds (both the main-branch push artifact AND the v1.1.1
   release from release/1.1.1) broke the extension system** — some extensions
   loaded, others didn't, and the loaded ones returned no results. The user's
   diagnosis: *"I think it is maybe the R8 obfuscation which is affecting
   it… Maybe we should remove the R8 obfuscation completely."*

Plus the standing reiteration: releases from the releases branch build ALL
the release APK versions (all device types).

---

## Root causes found

- **Finding 1:** `build-apk.yml` (the D-430 port, round 37) ran
  `assembleRelease` (SIGNED) on every push and uploaded BOTH APKs into the
  `anikuta-apk` artifact. ALSO discovered: main's `release-apk.yml` was still
  the STALE pre-round-35 file (it ran `assembleDebug` and attached a debug
  APK to releases!) — the rewritten all-ABI workflow only ever existed on
  release/1.1.1. Main could never have cut a correct release from a main tag.
- **Finding 2:** AppIconScreen's empty/error state carried two long
  instructional NoteCards + a bottom "how it works" note + a "Checking the
  icons folder on GitHub…" loading text.
- **Finding 3:** the hero's fallback `R.drawable.icon_current` (the kawaii
  artwork) lives in the MAIN source set — the debug overlay never overrode
  it. ALSO (pre-26 devices, minSdk 24): the debug overlay only had
  anydpi/anydpi-v26 XMLs; main's density-specific kawaii `ic_launcher.webp`
  rasters WIN over anydpi on API 24–25, so a pre-26 device would show the
  kawaii launcher for the debug build.
- **Finding 4:** the release buildType had R8 full mode (D-413/D-430) with
  the keep-rule surface. The compat packages genuinely live under
  `eu.kanade.**`/`com.lagradost.**` (kept) — but keep-rule coverage cannot be
  GUARANTEED complete for a DexClassLoader plugin ecosystem: R8's
  optimization passes (inlining, merging, dead-code elimination) alter
  runtime-resolved behavior beyond renaming. The debug build (identical code,
  no R8) worked on the user's device; both R8 builds broke. The user ordered
  the retirement.

## The fixes (D-435..D-438)

- **D-435 — the debug-only push path:** `build-apk.yml` rewritten — tests →
  assembleDebug → ABI check (debug APK only) → the `anikuta-apk` artifact.
  The keystore decode, the push-path assembleRelease, the apksigner gate and
  the mapping upload are REMOVED from the push path (they live in
  release-apk.yml, the tag path). `release-apk.yml` on main replaced with the
  current all-ABI SIGNED pipeline (identical to release/1.1.1's, minus the
  mapping asset) — a tag on any line now cuts a correct release.
- **D-436 — the R8 retirement:** the convention plugin's release buildType
  flips to `isMinifyEnabled=false` + `isShrinkResources=false`; NO proguard
  files; `app/proguard-rules.pro` DELETED (the D-413 record lives in
  decisions.md). release-apk.yml no longer stages/uploads a mapping.txt.
  Applies to BOTH main and release/1.1.1.
- **D-437 — the icon page's minimal empty state:** every description removed
  — the empty state (catalog empty OR unreachable) says ONLY "There aren't
  any icons yet."; the bottom "Icons come from the repository…" note deleted;
  the loading row shows a bare spinner (no text). The catalogError state
  variable removed with its UI.
- **D-438 — the debug identity everywhere:** the debug overlay gains its own
  `drawable-nodpi/icon_current.png` (288px, the lime artwork) so the App Icon
  page hero shows the DEBUG icon in debug builds; + density rasters
  `mipmap-{mdpi..xxxhdpi}/ic_launcher.webp` (48/72/108/144/192, lime) so
  pre-26 devices resolve the debug launcher to lime (not main's kawaii webps);
  + its own `values/colors.xml` (`ic_launcher_background` = #B1F256) so the
  overlay is self-contained.

## The release re-cut

release/1.1.1 receives the same fixes (workflows, R8, icon page, debug
identity), then the v1.1.1 tag is re-cut at the SAME exact version
(1.1.1/10101 — version discipline D-425) so the release pipeline produces the
unminified, all-ABI, signed release APKs + the ZIP. The old release + tag are
deleted first (same-version re-release — the device must uninstall the old
v1.1.1 once, same as round 37).

## Verification plan

- Local (verification-only; CI stays the compiler of record):
  `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` +
  `:app:processDebugResources` (the overlay merge check).
- CI on main: the Build APK run must produce ONLY `app-debug.apk` in the
  artifact (download + list it).
- CI on release/1.1.1: same debug-only push path, green.
- The re-release: all five APKs + ZIP + SHA256SUMS, apksigner-verified, the
  dex inspected for NON-obfuscation (readable class names — the D-436 proof).
