# ANI-KUTA — the published-repo setup + signing guide (v1.1.1, Round 33/34 / Tasks 73–74)

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
highest version (stable preferred) → takes the **first asset ending in
`.apk`** → downloads → prompts install.

**Hard requirements for a release to be discoverable:**
1. The release is NOT a draft.
2. The tag is exactly `v` + the version (e.g. `v1.1.1`, `v1.1.2`) — three
   dot-separated integer parts.
3. At least ONE `.apk` asset is attached. (The asset's file name does not
   matter to the updater — but keep the `ani-kuta-v1.1.1.apk` convention for
   humans.)

Notes:
- Unauthenticated GitHub API = 60 requests/hour per IP — the app checks once
  per open (plus the 6-hour dismiss cooldown), so this is ample.
- Prereleases are seen but a stable release of the same version wins.
- **Version comparison is by versionName** (1.1.2 > 1.1.1 > 0.4.20), so bump
  the versionName in `AndroidConfig.kt` for every release; keep versionCode
  monotonic (the v1.x scheme: major·10000 + minor·100 + patch).

## 3. Creating the v1.1.1 release (per the final delivery)

1. Extract the password-protected zip you received in chat (password given
   ONLY in chat).
2. Go to Confused-Creature-180/ANI-KUTA → Releases → **Draft a new release**.
3. "Choose a tag" → type `v1.1.1` → **Create new tag on publish** (target:
   leave empty — an APK-only repo has no commits; GitHub allows tag-on-publish
   with no target).
4. Title: `ANI-KUTA v1.1.1`. Description (suggested): the release notes from
   the chat delivery message.
5. Attach the APK: **Attach binaries** → drop `ani-kuta-v1.1.1.apk`.
6. Verify: NOT "Set as a pre-release"; "Set as the latest release" ✓.
7. **Publish release.**

Verification: open the app → Settings → About & Updates → Check for Updates
on a v1.1.1 install shows "You're up to date"; on an older install (after
sideloading v1.1.1) it offers the update.

## 4. Every future release (the routine)

1. Bump `versionCode`/`versionName` in `AndroidConfig.kt` (dev repo, on the
   release line).
2. Build the signed release APK (round 34's established path: a LOCAL build
   with `app/keystore.properties` present — the keystore + passwords never
   touch GitHub, not even as Actions secrets; the CI keeps its
   unsigned-verification role on every push).
3. Download the APK; upload it as a `vX.Y.Z` release in the published repo
   (steps as §3). That's the whole publish step — no source ever leaves the
   dev repo.

## 4a. The icons/ folder — the App Icon catalog (round 34 / D-418)

The published repo carries an `icons/` folder in its ROOT (main branch):
any image placed there shows up in the app's App Icon page (Settings →
Appearance → App Icon → "More icons"), fetched live from
`https://api.github.com/repos/Confused-Creature-180/ANI-KUTA/contents/icons`.

- **Naming convention**: `icon-01-original.png`, `icon-02-sakura.png`, …
  The `icon-NN` prefix is the app's match key: files whose NN maps to a
  variant baked into that installed release (01–08 in v1.1.1) switch the
  REAL home-screen launcher icon; other numbers/images apply inside the app
  with an honest "until the next release" note (Android only lets apps ship
  launcher icons as resources baked into the APK — new catalog icons become
  home-screen switchable once a release bakes them in).
- The app centers-crops, resizes (512px) and caches each image itself —
  just upload the raw images (square ones look best).
- The zip you received contains the eight starter icons (`icons/` folder) —
  upload them once (GitHub web: Add file → Upload files → drag the PNGs into
  a folder named `icons`), and the v1.1.1 app immediately picks them up.

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

### The rules
1. **Android update installs require the SAME signature forever.** Every
   future ANI-KUTA update must be signed with this key, or users get
   "App not installed" over their existing install (they'd have to uninstall
   + reinstall + lose app data: library, watch progress, downloads index).
2. **Lose the key = lose the update line.** Keep AT LEAST two backups of the
   zip (e.g. your password manager's secure storage + an encrypted cloud
   drive). The zip is AES-encrypted; the password exists in this chat and
   nowhere else.
3. **Never commit the keystore or passwords to any repository.** The
   .gitignore rules (`*.jks`, `*.keystore`, `keystore.properties`) enforce
   the accidental-commit case; the round-34 signing flow keeps the key OFF
   GitHub entirely (local build, not Actions secrets).
4. The debug keystore (dev builds) is separate and already committed — that
   one is intentionally public and disposable.
5. Signature continuity today: v0.4.x installs were debug-signed. v1.1.1
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
- R8 full mode: obfuscation + code/dependency shrinking + resource shrinking;
  the mapping.txt is kept per CI build for stack-trace decoding.
- DexGuard: commercial-only (Guardsquare license) — assessed, not available
  without purchase; R8 full mode is the implemented standard equivalent.
- Dead dependencies removed (logcat lib ×14 modules, stray rxjava ×3, seeker,
  androidx-media, truetype-parser); hardcoded deps moved into the version
  catalog.
- The updater + "View full release" link point at this published repo.
