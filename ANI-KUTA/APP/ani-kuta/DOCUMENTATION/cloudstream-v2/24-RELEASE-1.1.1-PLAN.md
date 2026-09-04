# Round 33 / Task 73 — the v1.1.1 PUBLISHABLE release track (merge → release branch → publishable build)

User instruction (the round brief):
> 1. Merge this branch (streaming/CLOUDSTREAM-V2) with the main branch. Verify by building from the main branch.
> 2. Create a new branch from main — our very first publishable version, **1.1.1**.
> 3. Remove the unnecessary details: the debug bubble completely (KEEP show-sources / copy button / update-check history), the console logging, unused dependencies.
> 4. Fix the episode-check notification: it must only show if there was something to check for (currently "Nothing was due for a check this run." fires on every app open).
> 5. R8 obfuscation + shrinking, resource shrinking, unused-asset/dependency cleanup. (DexGuard — see the honest note below.)
> 6. The in-app updater points to the NEW published repo: **https://github.com/Confused-Creature-180/ANI-KUTA** (APK-only — no source code there).
> 7. Signing: ASK the user for the keystore details BEFORE the final signed build. The final zip (APK + keystore + signing info) is password-protected; the password is told in chat only.

---

## Phase A — the merge (done first, before any release edits)

- `main` was **0 commits ahead / 111 behind** streaming/CLOUDSTREAM-V2 → a clean
  fast-forwardable merge. Executed as `git merge --no-ff` (a documenting merge
  commit, matching the repo's precedent for main merges).
- Pre-merge workflow housekeeping (the standing note in build-apk.yml): the
  `test-feature/**` push trigger is REMOVED (that line was merged into main at
  v0.2.63), and `release/**` is ADDED for the new publishable line.
- **Verification**: the push to main runs the Build APK workflow (tests +
  assembleDebug + ABI check) — CI GREEN on the merge commit = the merge is
  verified, exactly as the user asked.

## Phase B — the release branch

Branch: **`release/1.1.1`** (from merged main). This is the v1.1.x line.

### D-409 — the debug bubble: COMPLETE removal

The bubble ships in every APK today (CI only ever built `assembleDebug`, so the
debug source-set gating never actually excluded it). Full deletion:

- `:feature:debug-bubble` module (15 files) — DELETED.
- `:core:debug-api` module (3 files — the DebugContext CompositionLocals) — DELETED.
- `app/src/debug/` + `app/src/release/` source sets (6 files: DebugBubbleHost,
  DebugBubbleToggle, DebugInit × 2) — DELETED. This incidentally FIXES the
  latent `assembleRelease` break (the release DebugInit imported
  `DebugBuildInfo` from the debug-only classpath — release never compiled, so
  it was never caught).
- Call-site unwiring: settings.gradle.kts (2 includes), app/build.gradle.kts
  (2 deps), AnikutaApp.kt (debugKoinModules + wrapDebugOkHttp ×2 +
  wrapDebugSqlDriver), MainActivity.kt (DebugBubbleHost() + the hoisted
  debugContext + the two CompositionLocal provides — LocalLibrarySelectionMode
  SURVIVES), the 4 screen writer blocks (Browse/Details/Watch/Downloads DB-7
  blocks + their 4 gradle deps), the DebugSettingsScreen "Developer tools"
  section, the SettingsScreen row subtitle.
- **KEPT (user's explicit list)**: the whole Settings → Debug options page —
  "Show sources" (debug_show_resolve_sources), the resolve-list "Copy button"
  (debug_resolve_copy_button), and the "Update Check History" page — plus the
  crash reporter (ErrorActivity) and the debug keystore (dev install-over).

### D-410 — the episode-check notification gate

The report: the app shows "Episode check complete — nothing was due for a check
this run." on every open. Root: `UpdateCheckWorker` (WorkManager periodic, whose
REPLACE-on-open policy + overdue execution makes it land at app open) →
`UpdateEngine.runCheck` empty branch → `UpdateProgressNotifierImpl.onFinish`
posts the results notification unconditionally.

Fix (notifier-side, one file): `onFinish` early-returns when
`summary.totalChecked == 0` — nothing was due ⇒ silent run. Everything else is
preserved: the live progress notification (only fires on real checks), real
result notifications, `onFailed`, and ALL per-episode "new episode found"
notifications (a separate path — NotificationManager channels 30000+).

### D-411 — the updater repoint + v1.1.1

- `AppUpdateModule.kt`: `owner = "Confused-Creature-180"` (repo stays ANI-KUTA).
- `UpdateBottomSheet.kt`: the "View full release on GitHub →" URL.
- `AndroidConfig.kt`: **versionName 1.1.1 / versionCode 10101**
  (major·10000 + minor·100 + patch — 10101 > 85 keeps the install-over line
  monotonic).
- Existing v0.4.x installs still query the OLD repo — users sideload v1.1.1
  once; from then on updates flow from the new repo. (Documented for the user.)

### D-412 — the release log strip

- `AnikutaApp`: `Logger.setEnabled(BuildConfig.DEBUG)` (release: OFF — the
  lambda-based wrapper makes ~1,276 call sites dead code R8 strips; debug
  builds keep full logging).
- `com.lagradost.api.Log` (the plugin-facing facade, ~36 vendored sites + every
  loaded plugin): gains an `enabled` gate set from the app at startup — D/I/W
  silenced in release; **E stays** (error diagnostics for support).
- KEPT: the crash handler's raw Log.e (it writes the crash file the
  ErrorActivity shows), EpisodeListDumper's mirrored raw path (D-313).

### D-413 — R8 + resource shrinking + release signing plumbing

- `anikuta.android.application.gradle.kts`: release flips to
  `isMinifyEnabled = true` + `isShrinkResources = true`.
- `app/proguard-rules.pro`: real rules. Strategy: obfuscate/shrink/optimize
  ANI-KUTA's own code; keep intact EVERY surface resolved by name at runtime —
  the plugin-compat classpath (com.lagradost.**, eu.kanade.**,
  uy.kohesive.injekt.**, gson, jackson, okhttp3, okio, androidx.appcompat,
  kotlin.**, kotlinx.** — plugin dexes resolve the host parent-first by
  compiled name; R8 cannot see those references), the JNI surfaces
  (is.xyz.mpv.**, com.arthenica.ffmpegkit/smartexception.**), WorkManager
  workers (reflective instantiation), kotlinx-serialization companions, and
  stack-trace readability (SourceFile/LineNumberTable kept; mapping.txt
  uploaded as a CI artifact for decoding).
- Release signing: a `keystore.properties`-driven signingConfig — NO-OP when
  the file is absent (CI verification builds produce an unsigned release APK;
  the build still succeeds). The FINAL phase writes the file from CI secrets
  (the user provides the keystore details then — per instruction, ASKED FIRST).
- CI (build-apk.yml): a new "Build release APK (R8 + resource-shrink
  verification)" step (assembleRelease) + the ABI check extended to the
  release APK + mapping.txt uploaded + timeout 30→45 min.
- **DexGuard — the honest note**: DexGuard is a COMMERCIAL Guardsquare product
  (paid per-company license; cannot be provisioned here). R8 full mode (the
  AGP default we now enable) is the standard equivalent covering obfuscation,
  shrinking, and optimization. Documented rather than pretended.

### D-414 — unused-dependency + catalog cleanup

Verified zero imports before removal:
- `logcat` (com.squareup.logcat) — 14 module declarations, never imported.
- stray `rxjava` — core:player, core:video-resolver, feature:anime-details:impl
  (source-api keeps BOTH rxjava+rxandroid: the plugin ABI needs them).
- `seeker`, `androidx-media` (old, non-media3), `truetype-parser` — core:player.
- Catalog: the 4 dead entries removed; the 5 HARDCODED deps
  (extensions-settings/impl ×4 — including an appcompat 1.6.1-vs-1.7.1 pin
  mismatch; core:notifications ×1) moved into the catalog (fragment-ktx added).
- Asset check: subfont.ttf (6.4MB) is REQUIRED by MPV subtitle rendering —
  kept. No debug-only assets existed. No build/ artifacts are git-tracked.

## Phase C — the FINAL signing (after the user answers)

1. ASK the user: own keystore vs generate one; passwords/alias.
2. Generate/obtain the keystore; store as a GitHub Actions secret (repo-level,
   never committed); wire the release-apk.yml to decode + sign assembleRelease.
3. Tag **v1.1.1** on release/1.1.1 → the Release APK workflow builds the SIGNED
   minified APK → download → zip (APK + keystore + signing-info.txt),
   password-protected (7z/AES) → **password told in chat only**.
4. The user creates the release in Confused-Creature-180/ANI-KUTA from that zip
   (setup guide included in this round's docs + the final report).

## Verification plan

1. CI GREEN on the main merge push (Phase A).
2. CI GREEN on release/1.1.1: unit tests + assembleDebug + **assembleRelease
   (R8)** + ABI checks on both APKs.
3. A pre-push compile-toolchain review of every edited Kotlin file (the
   round-32 lesson: catch the compile breakers BEFORE CI).
4. The final signed APK: CI-built, artifact-downloaded, zip-packaged.
