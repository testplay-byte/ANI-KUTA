# Round 39 / Task 79 — the crash-fix round + the first release cut FROM main

The user's round (three device reports + the release + the environment rule):

1. **The library-open crash (release-only):** opening ANY content from the
   library crashed the release build completely
   (`kotlin.NoWhenBranchMatchedException`, DetailsScreen.kt:367) while the
   debug build opened the same content fine.
2. **The update-source repos were crossed:** the DEBUG build's "check for
   updates" was hitting the NEW (published) repository — it must check the
   repo the app is developed in; the RELEASE build checks the published repo.
3. **Notifications must be OFF by default.**
4. **The release flow:** update main → create the new release FROM main →
   create the release branch → complete the release (GitHub Actions builds
   everything; the release APKs are also re-hosted on the Release GitHub
   repo — Confused-Creature-180/ANI-KUTA).
5. **The environment rule:** the sandbox stays LEAN — no heavy software, no
   local processing; everything heavy happens in GitHub Actions. The local
   Android SDK + JDK + Gradle caches were DELETED (≈5GB) and must never be
   reinstalled. CI is the compiler of record (CORE_RULES §8, D-281).

## The fixes

- **D-439 the crash:** total-when hardening at ALL 7 `when` sites over
  `AnimeDetailsKey` (DetailsScreen ×4 incl. the exact line-367 site,
  MainActivity ×3) + `describeDetailsKeyAnomaly()` (api module, pure JVM) —
  the diagnostic that captures the runtime class + classloader identity if
  the anomaly ever recurs. Degradation, never a crash. (The most plausible
  mechanism — R8 class synthesis in the minified round-37 builds — was
  already removed by D-436; this hardening covers every remaining
  mechanism.)
- **D-440 the update repos:** the owner is resolved from the merged
  manifest's `FLAG_DEBUGGABLE` (the runtime truth of the installed build
  type): debug → `testplay-byte/ANI-KUTA` (the dev repo), release →
  `Confused-Creature-180/ANI-KUTA` (the published repo). The ABI-aware
  asset picker (D-423) is ported to main so the release updater offers the
  device's own APK from the 5-asset releases. The Koin resolution lives
  inside the definition lambda (the Module receiver has no `get()`).
- **D-441 the notifications:** the master kill switch's default flipped to
  OFF — a fresh install posts nothing until the user opts in.

## The release (D-442)

1. main: the fixes + the docs (this round) — push → CI debug build green.
2. `release/1.1.2` cut from the round-39 main head — ONE commit on top:
   AndroidConfig 1.1.2/10102 + the branch-point docs. Push → CI green.
3. Tag `v1.1.2` (annotated) → release-apk.yml → the all-ABI SIGNED release
   (arm64-v8a / armeabi-v7a / x86 / x86_64 / universal + the ZIP +
   SHA256SUMS, no mapping.txt) on testplay-byte/ANI-KUTA.
4. Re-host to Confused-Creature-180/ANI-KUTA: the exact asset names
   (version-prefixed — the ABI-aware updater requires them), the SHA256SUMS,
   the release body, stable + latest. Checksums verified against the dev
   release before upload.
5. Post-release verification: the CI apksigner gate (the run log) + the
   SHA256SUMS match + the /releases/latest pointer on BOTH repos.
6. ntfy.sh completion notification.

## What the user's device sees after this round

- The v1.1.2 release APK installs OVER v1.1.1 (10102 > 10101 — no uninstall).
- Library opens cannot crash at the dispatch layer (any anomaly logs +
  degrades instead).
- The release build's "Check for Updates" hits Confused-Creature-180/ANI-KUTA
  (the published repo); the debug build's hits testplay-byte/ANI-KUTA.
- A fresh install: notifications are OFF until the user enables them.
