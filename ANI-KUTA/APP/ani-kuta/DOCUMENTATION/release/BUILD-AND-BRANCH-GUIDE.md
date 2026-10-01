# ANI-KUTA — the build & branch guide (release vs debug, kept separate)

**Round 37 / Task 77 (D-429..D-430). Round 38 / Task 78 revision (D-435..D-436):**
the push path is now **DEBUG-ONLY on every branch** (the user's round-38
instruction), and **R8 minification is retired from the release line**
(D-436 — it broke the extension system on device).
**Round 39 / Task 79 revision (D-439..D-442):** the release branch is now
**cut FROM the mainline** (the mainline carries everything the release needs; the branch is
mainline + ONE version-bump commit), the in-app updater's repo follows the
BUILD TYPE at runtime, and the sandbox is LEAN (no local Android tooling —
GitHub Actions is the only build machine; the local SDK/JDK/Gradle were
deleted).
**Round 115 revision (D-738/D-739, 2026-10-02):** the mainline is
`feature/round-57-cloudstream-downloads` (the default branch — `main` was
deleted D-552, 0 unique commits). The user's standing order: **DEBUG BUILDS
ONLY — all releases suspended until the explicit order** (rounds end at the
CI debug artifact). The full release routines now live in
**`RELEASE-PLAYBOOK.md`** (this folder) — the round-80 repo-external playbook
and the REPO-SETUP-AND-SIGNING / RELEASE-AGENT-STARTER-PROMPT companions were
lost to sandbox resets; the playbook is their in-repo replacement. This is the
standing
reference for how ANI-KUTA is built, which branch produces what, and how the
release version and the debug version are tested side by side on one device.
It is the companion to `RELEASE-PLAYBOOK.md` (the release routines) — this
document covers the DEV side (the mainline + the CI push path).

---

## 1. The branch model (the division of lines)

| Branch | What it is | The debug bubble | CI on push | Used for |
|---|---|---|---|---|
| `feature/round-57-cloudstream-downloads` | **THE MAINLINE** (the default branch; the remote's only branch — `main` was deleted D-552) — where features land and converge between releases. Carries EVERYTHING a release needs (the build line, the workflows — D-430/D-435/D-436). Version: 1.1.20/10120 (D-430: bumps ride release branches). | **PRESENT** (the user's standing instruction: the bubble stays on the mainline) | tests + assembleDebug — the **DEBUG APK only** (D-435, arm64-v8a D-445) — downloadable as the CI artifact | Feature development + the debug test line + the source of every future release cut |
| `release/<version>` (e.g. release/1.1.71, release/1.1.14) | The PUBLISHABLE lines — each **cut FROM the round's green mainline head** (D-442): mainline + ONE commit (the version bump + the branch-point docs). Cut only on the user's explicit order; under D-738 none are cut. | PRESENT (inherited from the mainline — the release APK build itself excludes it via the source-set split; the co-install debug identity is the debug overlay) | The same DEBUG-ONLY push path; releases happen via the TAG path only: `v*` → release-apk.yml (debug line) or the release-build-once.yml dispatch (professional line — D-564: the ref is the release branch) | Cutting actual releases |
| `feature/test-controller-v5` | A kept experiment line (dormant, user order). | — | Same push path | Reference |

Deleted in round 37 (per the user's instruction): `test-feature/video-cache-new-download`,
`streaming/CLOUDSTREAM`, `streaming/CLOUDSTREAM-V2`, `functionality/improvements`.

**The rule (the user's round-37 words):** "we are going to properly build
releases from now on… when we are actually doing a release, then all those
things need to be followed. Besides that those things will not be followed on
the main branch." — i.e. the FULL release routine (all-ABI splits + ZIP +
checksums + the GitHub release + version bump) runs ONLY when an actual
release is cut. Main-branch pushes never trigger it.

## 2. The two build types (and what each produces)

### The DEBUG build (`assembleDebug`)
- Package: **`com.confused.anikuta.debug`** (the `.debug` applicationId suffix, D-429/D-416)
- Version name: `<base>-debug` (e.g. `1.1.20-debug` on the mainline today)
- Icon: **the user-provided mascot artwork** (`USER-UPLOADS/IMG_20260918_233212.png`, D-444 — contain-fit, never cropped) + the label **"ANI-KUTA Debug"** (app/src/debug/res overlay); the release line keeps the kawaii-mouth icon set
- Signing: the committed `anikuta-debug.keystore` (every debug build installs over every other debug build — no uninstall churn)
- Contains: the debug bubble, full logging, all dev tooling
- **The co-install point:** because the applicationId differs, the debug build installs
  and runs SIDE BY SIDE with the release install (`com.confused.anikuta`). The user
  tests the official release + the current dev build on the same device at the same time.
- **D-674 (round 99): the debug line is NON-DEBUGGABLE** — no `adb run-as`, no debugger attach (the dev-repo gate / DEBUG logging / Developer-tools are keyed on IS_DEBUG_LINE + the `.debug` suffix, NOT BuildConfig.DEBUG).

### The RELEASE build (`assembleRelease`)
- Package: `com.confused.anikuta` (no suffix)
- **NOT minified (D-436, round 38): R8 is RETIRED from the release line.**
  The v1.1.1 device round proved the obfuscated release builds broke the
  extension system (some extensions loaded, others failed, the loaded ones
  returned no results — while the unminified debug build with identical code
  worked). The release buildType is `isMinifyEnabled=false` +
  `isShrinkResources=false`; `app/proguard-rules.pro` is DELETED (the D-413
  keep-rule record lives in decisions.md). No mapping.txt is produced anymore.
- **Built ONLY by the tag-driven release-apk.yml** (D-435): NO branch push —
  main, feature, release, streaming, any — ever builds a release APK. A
  release APK exists only through pushing a `v*` tag on the release branch
  (or the release-apk.yml workflow_dispatch with an existing tag).
- Signing: `app/keystore.properties` → the `anikutaRelease` config. In CI the
  file is decoded from the repository's GitHub Actions secrets
  (`ANIKUTA_KEYSTORE_BASE64` + the password secrets) BEFORE the build, so every
  shipped release APK is SIGNED. The apksigner verify gate hard-fails the
  workflow if any APK is unsigned.
- On main the release build keeps dev logging (D-412's release-logging strip
  stays release-line-only). The UPDATER TARGET no longer differs by branch
  (D-440, round 39 — supersedes D-411): the repo is resolved at RUNTIME from
  the build type (FLAG_DEBUGGABLE) — debug → testplay-byte/ANI-KUTA,
  release → Confused-Creature-180/ANI-KUTA — correct on every branch and
  build path.

## 3. How you get APKs (everything comes from CI — never build locally)

CORE_RULES §8: **CI is the only build machine.** Every APK that anyone
installs is produced by GitHub Actions.

1. **Debug test builds (the push path — the ONLY push artifact, D-435):** any
   push to any workflow-tracked branch → the **Build APK** workflow:
   `tests → assembleDebug → the ABI check → the anikuta-apk artifact`.
   Download from the Actions run page:
   - artifact **`anikuta-apk`** — `app-debug.apk` (co-installable debug) —
     **the debug APK only; NO release APK is produced or uploaded on any
     branch push** (the round-37 push-path assembleRelease was removed by the
     user's round-38 instruction after a release APK appeared in the
     main-branch artifact).
2. **An actual release (the tag path — the ONLY release source, D-435):** on
   the RELEASE branch, tag + push `v<version>` → the **Release APK** workflow:
   `tag-version match → keystore decode → assembleRelease -PreleaseAllAbis=true
   → one APK per ABI (arm64-v8a / armeabi-v7a / x86 / x86_64) + universal →
   per-APK lib verification → the HARD apksigner gate →
   ANI-KUTA-v<version>-RELEASE.zip + SHA256SUMS.txt → the stable GitHub release
   with the which-APK table` (no mapping.txt — D-436).
   See `RELEASE-PLAYBOOK.md` (this folder) for the full release routines.

## 4. The side-by-side testing model (the user's workflow)

1. Install the official release APK (from the GitHub Releases page) →
   `com.confused.anikuta` — the real user experience.
2. Install the latest debug artifact from a mainline CI run →
   `com.confused.anikuta.debug` — the current dev build.
3. Both run at the same time; the debug one is visually distinct (the mascot
   icon + "ANI-KUTA Debug" label) and nominally distinct (`-debug` version suffix).
4. Iterate: the committed debug keystore means each new debug artifact installs
   directly over the previous one. **The D-738 wrinkle:** the mainline artifact
   carries 10120 — it will NOT install over a debug RELEASE (e.g. v1.1.71/10171)
   without `adb install -d` or an uninstall; when the user wants in-place device
   updates, they order a debug release (RELEASE-PLAYBOOK.md Routine A).
5. The debug build's identity is consistent EVERYWHERE now (D-438/D-444): the
   mascot launcher icon at every density (the debug overlay's own
   `ic_launcher.webp` rasters for pre-26 devices + the adaptive XMLs, contain-fit
   per D-444) AND the in-app "current icon" display (the App Icon page hero) via
   the debug overlay's own `drawable-nodpi/icon_current.png` — the debug build
   never shows the release kawaii artwork anywhere.

## 5. Version discipline (D-425 — standing rule)

The version NEVER moves without the user's explicit instruction:
- The mainline carries its own dev version (**1.1.20 / 10120** — the mainline
  never takes a release bump; the release BRANCH carries the bump). Don't
  "fix" the gap between 1.1.20 and the shipped v1.1.71 — that's the doctrine.
- `release/1.1.71` carries 1.1.71 / 10171 (the debug line's latest — cut from
  the round-114 green head, one bump commit; 10171 > 10170 → the debug app
  updates in place).
- `release/1.1.14` carries 1.1.14 / 10114 (the professional line's latest —
  10114 > 10103/v1.1.3 → installs over the previous professional release).

## 6. The division of labor (round 36 — standing)

- **The build agent** (this repo's main agent): builds the release AND debug
  versions; owns the code, the CI workflows, the build config, the docs.
- **The repository agent** (the new agent on the published repo): ONLY creates
  and manages the GitHub releases + tags on the published repo
  (Confused-Creature-180/ANI-KUTA) — re-hosting the dev releases, managing the
  icons/ catalog. It never builds. (The old RELEASE-AGENT-STARTER-PROMPT.md
  companion was lost to a sandbox reset — the re-host routine now lives in
  `RELEASE-PLAYBOOK.md` Routine C.)
- **Round-39 note:** when the user instructs the build agent to "manage
  everything" for a round, the build agent ALSO performs the re-host
  (the release-agent routine: download the dev release's assets → verify the
  SHA256SUMS → create the release on the published repo with the EXACT
  version-prefixed asset names → set stable + latest) — as done for v1.1.2,
  v1.1.3, and (round 114) v1.1.14.

## 7. The lean-environment rule (round 39 — standing)

The sandbox carries NO Android SDK, NO JDK, NO Gradle caches (deleted
round 39 per the user's instruction — never reinstall them, and never
install any other heavy software). GitHub Actions is the ONLY build machine
and the ONLY verification path (CORE_RULES §8, the CI-first D-281 loop).
Local verification is limited to checksums (`sha256sum`) and API/log
inspection — no builds, no decompiles, no emulator.
