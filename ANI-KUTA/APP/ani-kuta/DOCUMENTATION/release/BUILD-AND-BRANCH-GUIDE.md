# ANI-KUTA — the build & branch guide (release vs debug, kept separate)

**Round 37 / Task 77 (D-429..D-430). Round 38 / Task 78 revision (D-435..D-436):**
the push path is now **DEBUG-ONLY on every branch** (the user's round-38
instruction), and **R8 minification is retired from the release line**
(D-436 — it broke the extension system on device). This is the standing
reference for how ANI-KUTA is built, which branch produces what, and how the
release version and the debug version are tested side by side on one device.
It is the companion to `REPO-SETUP-AND-SIGNING-GUIDE.md` (the published-repo
routine) — this document covers the DEV side (the `main` branch + the CI push
path).

---

## 1. The branch model (the division of lines)

| Branch | What it is | The debug bubble | CI on push | Used for |
|---|---|---|---|---|
| `main` | The DEV line — where features land and converge between releases. Currently carries the v1.1.x feature set (round 37: the episode-check fix, the share hardening, the minimal debug descriptions, the app-icon system, the co-install line). | **PRESENT** (the user's standing instruction: the bubble stays on main) | tests + assembleDebug — the **DEBUG APK only** (D-435) — downloadable as the CI artifact | Feature development + the debug test line |
| `release/1.1.1` | The PUBLISHABLE line — the clean release track (no debug bubble, release logging gates, the published updater target). | REMOVED (D-409) | The same DEBUG-ONLY push path; releases happen via the TAG path only: `v*` → release-apk.yml → ALL-ABI splits + universal + the ZIP + the GitHub release | Cutting actual releases |
| `feature/test-controller-v5` | A kept experiment line. | — | Same push path | Reference |

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
- Version name: `<base>-debug` (e.g. `0.4.20-debug` on main today)
- Icon: **the old lime icon** + the label **"ANI-KUTA Debug"** (app/src/debug/res overlay)
- Signing: the committed `anikuta-debug.keystore` (every debug build installs over every other debug build — no uninstall churn)
- Contains: the debug bubble, full logging, all dev tooling
- **The co-install point:** because the applicationId differs, the debug build installs
  and runs SIDE BY SIDE with the release install (`com.confused.anikuta`). The user
  tests the official release + the current dev build on the same device at the same time.

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
- On main the release build keeps dev logging + the dev updater target (the
  release-only polish — D-411/D-412 — lives on `release/1.1.1` only).

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
   See `REPO-SETUP-AND-SIGNING-GUIDE.md` §4 for the full routine.

## 4. The side-by-side testing model (the user's workflow)

1. Install the official release APK (from the GitHub Releases page) →
   `com.confused.anikuta` — the real user experience.
2. Install the latest debug artifact from a main-branch CI run →
   `com.confused.anikuta.debug` — the current dev build.
3. Both run at the same time; the debug one is visually distinct (lime icon +
   "ANI-KUTA Debug" label) and nominally distinct (`-debug` version suffix).
4. Iterate: the committed debug keystore means each new debug artifact installs
   directly over the previous one.
5. The debug build's identity is consistent EVERYWHERE now (D-438, round 38):
   the lime launcher icon at every density (the debug overlay's own
   `ic_launcher.webp` rasters for pre-26 devices + the adaptive XMLs) AND the
   in-app "current icon" display (the App Icon page hero) via the debug
   overlay's own `drawable-nodpi/icon_current.png` — the debug build never
   shows the release kawaii artwork anywhere.

## 5. Version discipline (D-425 — standing rule)

The version NEVER moves without the user's explicit instruction:
- `main` carries its own dev version (currently 0.4.20 / 85 — main never got a
  version bump; a future release cut from main would take the next number the
  USER chooses, e.g. 1.1.2).
- `release/1.1.1` carries 1.1.1 / 10101. The round-37 re-release reuses the
  SAME version (the user's explicit instruction — same versionCode, so a device
  holding the old v1.1.1 must uninstall once before installing the re-release).

## 6. The division of labor (round 36 — standing)

- **The build agent** (this repo's main agent): builds the release AND debug
  versions; owns the code, the CI workflows, the build config, the docs.
- **The repository agent** (the new agent on the published repo): ONLY creates
  and manages the GitHub releases + tags on the published repo
  (Confused-Creature-180/ANI-KUTA) — re-hosting the dev releases, managing the
  icons/ catalog. It never builds. See `RELEASE-AGENT-STARTER-PROMPT.md`.
