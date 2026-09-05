# Round 36 / Task 76 — the v1.1.1 version-discipline round (the plan)

The user's round-35 feedback, verbatim essence:
1. **The downloaded release looked proper** — "everything was looking proper,
   everything looked exactly how I wanted it to be" (the v1.1.2 release: the
   signed all-ABI APKs, the ZIP, the release page — all approved).
2. **BUT the version was wrong**: "you were supposed to stay on version 1.1.1.
   Apparently you switched to a more recent version, 1.1.2, so that is not
   good." → D-424's self-initiated bump was a mistake. The version reverts to
   **1.1.1 / 10101** and STAYS there until the user says otherwise.
3. **Releases for all the available variants** — older Android devices
   (armeabi-v7a), modern devices (arm64-v8a), emulators (x86/x86_64), the
   universal APK — all signed, all zipped by GitHub Actions (the D-423
   pipeline — already exactly this).
4. **The tag path builds ONLY the release version** and zips all of them
   (release-apk.yml already runs nothing but the signed all-ABI
   assembleRelease + the ZIP — confirmed, no changes needed).
5. **The division of labor (new, explicit):** the BUILD agent (this agent)
   is responsible for building the release AND the debug APKs; the RELEASE
   agent (the new repository agent) ONLY handles creating the releases and
   managing the GitHub releases and tags.

## The decisions

- **D-425 — the version-discipline fix.** AndroidConfig reverted to
  versionName 1.1.1 / versionCode 10101 with the standing rule written into
  CORE_RULES §8: the version NEVER moves without the user's explicit
  instruction; monotonic-versionCode concerns get REPORTED with options,
  never silently resolved by a bump. The v1.1.2 tag + release are deleted
  from the dev repo; v1.1.1 is re-tagged on the fixed commit.

## The execution

1. AndroidConfig.kt: 10102/"1.1.2" → 10101/"1.1.1" + the D-425 comment
   (round 35's record kept, the mistake + the fix documented).
2. Docs: CORE_RULES §8 (VERSION DISCIPLINE + DIVISION OF LABOR bullets),
   decisions.md D-425, REPO-SETUP-AND-SIGNING-GUIDE.md (v1.1.1 routine, the
   version rule, the division), RELEASE-AGENT-STARTER-PROMPT.md (the
   division sharpened: the build agent builds release+debug; the release
   agent creates/manages releases+tags on the published repo; never invent
   versions), changelog + progress + this plan.
3. No workflow changes needed — release-apk.yml already builds ONLY the
   release version (all ABI variants, signed, zipped) on tags, and
   build-apk.yml already builds debug + release on pushes. The workflows
   match the user's division exactly as-is.
4. Commit + push → Build APK workflow GREEN (tests + assembleDebug + the
   signed push-path assembleRelease).
5. Delete the v1.1.2 release + tag (the wrong version, my mistake).
6. Tag `v1.1.1` (annotated) on the fixed commit → release-apk.yml: the
   keystore decoded from secrets → assembleRelease -PreleaseAllAbis=true →
   the five APKs → per-APK ABI verification → the apksigner gate →
   ANI-KUTA-v1.1.1-RELEASE.zip + SHA256SUMS.txt + mapping → the stable
   GitHub release.
7. Post-release verification: download the exact user-facing files, apksigner
   verify each, confirm versionName 1.1.1 / versionCode 10101 in the APK,
   confirm the ZIP contents + checksums, confirm /releases/latest = v1.1.1.

## The handoff (after this round)

- The user installs the v1.1.1 arm64-v8a APK (NOTE: if v1.1.2 was installed
  for testing, uninstall it first — 10101 < 10102 is a versionCode downgrade
  and Android will refuse an install-over; a fresh install is clean).
- The user hands RELEASE-AGENT-STARTER-PROMPT.md to the new repository
  agent, which creates + manages the releases and tags on the published repo
  (Confused-Creature-180/ANI-KUTA) by re-hosting the verified assets from
  the dev repo's v1.1.1 release.
- The assets-v1.1.1-delivery temporary prerelease (round 34) stays until the
  user confirms deletion (their decision, per the starter prompt §6).
