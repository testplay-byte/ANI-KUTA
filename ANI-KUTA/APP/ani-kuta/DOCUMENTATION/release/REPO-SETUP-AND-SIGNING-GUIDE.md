# ANI-KUTA — the published-repo setup + signing guide (v1.1.1, Rounds 33–36 / Tasks 73–76)

This is the user-facing guide for the NEW publishable GitHub repository:
**https://github.com/Confused-Creature-180/ANI-KUTA** — the APK-only home of
the v1.1.1+ line. The dev repo (testplay-byte/ANI-KUTA) remains the source
repository; NOTHING from it is published to the new repo except the release
APKs.

---

## 1. Creating the repository (one-time)

1. Sign in as **Confused-Creature-180** → New repository.
2. Name: `ANI-KUTA`. Visibility: **Public** (the in-app updater reads the
   GitHub Releases API **unauthenticated** — a private repo would make update
   checks fail with 404 for every user).
3. Do NOT initialize with a README/license (optional, cosmetic — safe either
   way; the updater only reads Releases).
4. Recommended (cosmetic, helps users): a short README like:

   > ANI-KUTA — an Android anime streaming app.
   > Download the latest APK from [Releases](../../releases/latest).
   > Requirements: Android 7.0+ (arm64-v8a). Install over any previous build.

## 2. How the in-app updater sees the repo (what it needs, strictly)

The updater (Settings → About & Updates → Check for Updates) calls:

```
GET https://api.github.com/repos/Confused-Creature-180/ANI-KUTA/releases?per_page=30
```

Then it: skips drafts → parses each tag as `vMAJOR.MINOR.PATCH` → picks the
highest version (stable preferred) → **picks the APK asset matching the
device** (D-423, round 35: releases carry one APK per ABI plus a universal
APK; the updater walks `Build.SUPPORTED_ABIS` in the device's preference
order and takes the asset whose name carries that ABI tag —
`-arm64-v8a`, `-armeabi-v7a`, `-x86`, `-x86_64` — falling back to
`-universal`, then to the first APK) → downloads → prompts install.

**Hard requirements for a release to be discoverable:**
1. The release is NOT a draft.
2. The tag is exactly `v` + the version (e.g. `v1.1.1`, `v1.1.2`) — three
   dot-separated integer parts.
3. At least ONE `.apk` asset is attached. For v1.1.1+ releases the asset
   names follow the pipeline convention (see §4); older single-asset
   releases keep working via the first-APK fallback.

Notes:
- Unauthenticated GitHub API = 60 requests/hour per IP — the app checks once
  per open (plus the 6-hour dismiss cooldown), so this is ample.
- Prereleases are seen but a stable release of the same version wins.
- **Version comparison is by versionName** (1.1.2 > 1.1.1 > 0.4.20). The
  versionName in `AndroidConfig.kt` moves ONLY on the user's EXPLICIT
  instruction (D-425, the version-discipline rule — round 35 shipped v1.1.2
  unasked and round 36 reverted it to 1.1.1, the first official release);
  keep versionCode monotonic within the user-chosen line (the v1.x scheme:
  major·10000 + minor·100 + patch).

## 3. Creating a release in the published repo (v1.1.1+ routine)

The DEV repo (testplay-byte/ANI-KUTA) is the build machine: its
`release-apk.yml` workflow (triggered by a `v*` tag) builds AND signs every
APK in CI — all four ABI splits + universal + the release ZIP + SHA256SUMS —
and attaches them to the dev repo's own release automatically. The published
repo (this one) simply re-hosts those verified assets:

1. Open the dev repo's `v{version}` release (e.g.
   https://github.com/testplay-byte/ANI-KUTA/releases/latest) and download
   the five APKs + `SHA256SUMS.txt` (optionally the ZIP; no mapping file exists anymore — D-436, round 38).
2. Go to Confused-Creature-180/ANI-KUTA → Releases → **Draft a new release**.
3. "Choose a tag" → type `v{version}` → **Create new tag on publish** (target:
   leave empty — an APK-only repo has no commits; GitHub allows tag-on-publish
   with no target).
4. Title: `ANI-KUTA v{version}`. Description: copy the dev release notes
   (they include the which-APK-for-which-device table).
5. Attach the five APKs + `SHA256SUMS.txt` (+ the ZIP if you want it there
   too).
6. Verify: NOT "Set as a pre-release"; "Set as the latest release" ✓.
7. **Publish release.**

Verification: open the app → Settings → About & Updates → Check for Updates
on an older install offers the update (and the device-ABI APK is picked
automatically); on the same version it says "You're up to date".

(The v1.1.1 one-off flow — the password-protected delivery zip — was the
round-34 delivery mechanism, superseded by the CI-signed pipeline. That
pipeline now produces the official **v1.1.1** release itself: round 35
shipped it prematurely as v1.1.2 and round 36 restored the version to
1.1.1 — same CI builds + signs everything, correct version.)

## 4. Every future release (the routine — CI builds AND signs, D-423)

0. **Division of labor (round 36):** the BUILD agent owns the dev repo's
   workflows and builds BOTH the release and the debug APKs in GitHub
   Actions — the tag path builds ONLY the release version (all variants,
   signed, zipped). The RELEASE agent (or the user following §3 by hand)
   creates and manages the GitHub releases + tags on the published repo.
1. On the user's EXPLICIT instruction, set `versionCode`/`versionName` in
   `AndroidConfig.kt` (dev repo, on the
   release line).
2. Push; wait for the `Build APK` workflow to go green (it now also SIGNS the
   push-path release APK with the same secrets, and its own apksigner check
   runs — an unsigned artifact can never be downloaded again).
3. Tag `v{versionName}` and push the tag. The dev repo's `release-apk.yml`
   runs: keystore decoded from the repo's **GitHub Actions secrets** →
   `assembleRelease -PreleaseAllAbis=true` → five APKs
   (`ani-kuta-v{v}-arm64-v8a|armeabi-v7a|x86|x86_64|universal.apk`) →
   per-APK ABI verification → the HARD `apksigner verify` gate →
   `ANI-KUTA-v{v}-RELEASE.zip` (all five + SHA256SUMS.txt) → the GitHub
   release (stable, never prerelease) with the which-APK table.
4. Re-host the verified assets in the published repo (steps as §3). No
   source ever leaves the dev repo; nothing is ever built or signed locally
   (CORE_RULES §8).

The secrets on the dev repo (set once, round 35):
`ANIKUTA_KEYSTORE_BASE64`, `ANIKUTA_KEYSTORE_PASSWORD`,
`ANIKUTA_KEY_ALIAS`, `ANIKUTA_KEY_PASSWORD`.

## 4a. The icons/ folder — the App Icon catalog (D-434, round 37: catalog-ONLY)

The published repo carries an `icons/` folder in its ROOT (main branch):
any image placed there shows up in the app's App Icon page (Settings →
Appearance → App Icon) — the catalog IS the icon list. **Round 37 removed the
8 premade baked app icons completely** (the user: "There are 8 premade app
icons on the app icon page but I told you to remove all of them and you did
not. Remove them properly.") — there are no built-in variants anymore and no
icon-NN matching: every icon in the folder is shown the same way (each as its
FULL artwork in a rounded-corner cell). There is no custom-image upload
(removed round 35). The listing is fetched live from
`https://api.github.com/repos/Confused-Creature-180/ANI-KUTA/contents/icons`.

- **Naming**: any image name works; the name (minus extension, minus a
  leading `icon-NN-` prefix, underscores → spaces) becomes the label under
  the icon. Square images look best.
- **What tapping does**: the icon applies INSIDE THE APP (the App Icon page's
  current-icon display). The home-screen launcher icon stays the release's
  baked icon (the user's own) — Android only lets apps ship launcher icons as
  resources baked into the APK, so a catalog icon becomes the LAUNCHER icon
  when the user picks it for a future release (the catalog is the selection
  surface between releases).
- The app center-crops, resizes (512px) and caches each image itself — just
  upload the raw images.
- The round-34 delivery zip contained eight starter icons (`icons/` folder) —
  **the user chooses what actually goes in the folder** (the premade set, a
  different set, or none). An empty folder simply shows "No icons in the
  folder yet" with a hint.

## 4b. The delivery zip (round 34's actual delivery flow)

The zip you downloaded (from a temporary release asset on the dev repo —
  deleted from GitHub after you confirmed safekeeping; the password was
  shared in chat ONLY and exists nowhere else) contains:

- `ani-kuta-v1.1.1-release.apk` — the SIGNED first release (arm64-v8a).
- `ani-kuta-v1.1.1-debug.apk` — the co-installable debug build (own app id
  `com.confused.anikuta.debug`, old lime icon, "ANI-KUTA Debug" label —
  installs BESIDE the release version).
- `anikuta-release.keystore` — the release keystore (JKS, RSA-2048,
  25-year validity, alias `anikuta`).
- `keystore.properties` — the Gradle-side config (storeFile path needs
  adjusting to wherever you keep the keystore).
- `SIGNING-DETAILS.txt` — all five signing details written out plainly.
- `icons/` — the eight starter PNGs for §4a.
- `README-FIRST.txt` — the quick-start.

## 5. The signing key — storage + rules (CRITICAL — read once, keep forever)

### What you received in the zip
- `anikuta-release.keystore` — the release keystore (JKS: distinct store +
  key passwords, RSA-2048, valid until ~2051, alias `anikuta`).
- `keystore.properties` — the Gradle-side config (storeFile/storePassword/
  keyAlias/keyPassword) used by the signed build.
- `SIGNING-DETAILS.txt` — all values written out plainly (store/key
  passwords, alias, validity, certificate DN, SHA-256 fingerprints) + how
  to verify.

### The rules (updated round 35 / D-423 — the user explicitly authorized
### GitHub-secrets storage; CI is the only build+sign path)

1. **Android update installs require the SAME signature forever.** Every
   future ANI-KUTA update must be signed with this key, or users get
   "App not installed" over their existing install (they'd have to uninstall
   + reinstall + lose app data: library, watch progress, downloads index).
2. **Lose the key = lose the update line.** The keystore now lives in THREE
   places, any of which can restore it: (a) the dev repo's GitHub Actions
   secrets (`ANIKUTA_KEYSTORE_BASE64` + the three password secrets — the
   live signing source), (b) your saved copy of the round-34 delivery zip,
   (c) this guide's record that the key is JKS/RSA-2048/alias `anikuta`. For
   recovery, GitHub repo admins can always RE-set the secrets from a saved
   keystore copy — keep at least one offline backup (the password-manager +
   encrypted-drive pair from round 34 remains the recommendation).
3. **Never commit the keystore or passwords as FILES to any repository.**
   GitHub Actions SECRETS are the authorized storage (encrypted at rest by
   GitHub, never printed in logs, never in the repo tree). The .gitignore
   rules (`*.jks`, `*.keystore`, `keystore.properties`) still enforce the
   accidental-commit case for plain files.
4. **Never build or sign an APK locally** (CORE_RULES §8, reinforced by the
   user in round 35: "you will never build the APK in your own
   environment"). Both workflows build AND sign in CI; the release workflow
   hard-fails on any APK that does not pass `apksigner verify`.
5. The debug keystore (dev builds) is separate and already committed — that
   one is intentionally public and disposable.
6. Signature continuity today: v0.4.x installs were debug-signed. v1.1.1
   (release-signed) is a NEW signature — one final uninstall/reinstall (or a
   fresh install) is expected for devices upgrading from the dev line; every
   v1.1.1+ update after that is in-place.

## 6. What was removed/hardened for the publishable build (context)

- The debug bubble: fully removed (code, modules, source sets).
- Kept per instruction: show-sources, resolve-list copy button, update-check
  history (Settings → Debug options).
- The "nothing was due for a check" notification: silent when a check run had
  nothing to do (real runs still notify).
- Console logging: OFF in release (error-level kept for support).
- NOT minified (D-436, round 38): R8 is RETIRED from the release line — the
  obfuscated v1.1.1 builds broke the extension system on the user's device
  (plugins loaded via DexClassLoader resolve host classes by their compiled
  names) while the unminified debug build with identical code worked. No
  obfuscation, no shrinking, no resource shrinking, no mapping.txt. (DexGuard
  remains commercial-only and was never used.)
- Dead dependencies removed (logcat lib ×14 modules, stray rxjava ×3, seeker,
  androidx-media, truetype-parser); hardcoded deps moved into the version
  catalog.
- The updater + "View full release" link point at this published repo.
