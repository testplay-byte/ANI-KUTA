# ANI-KUTA — THE RELEASE PLAYBOOK

> **Round 115 (D-739): the in-repo rebuild of the release playbook.** The round-80 original was kept "repo-external" and was LOST to a sandbox reset — this file is the consolidated, versioned replacement so it can never be lost again. It encodes rounds 37-114 of release discipline.
>
> **⛔ CURRENT STATUS: DORMANT UNDER D-738 (default; the round-117 exception executed).** The user's round-115 standing order: **debug builds only — NO releases of any kind (debug or professional) until the user's explicit order.** Nothing in this file executes without that order. Round 117 exercised the recovery path — the explicit both-lines order released the debug v1.1.72 + built the professional v1.1.5 (the version correction) — and then the default resumed. When the user orders a release, follow the matching routine below EXACTLY.
>
> Companion doc: `BUILD-AND-BRANCH-GUIDE.md` (the dev/release line split, the build types, the signing setup). The freshest worked example of the FULL professional routine: doc 96 §7 (round 114, v1.1.14). The freshest debug-release example: doc 96 §6 (round 114, v1.1.71).

---

## 0. The invariants (all routines)

1. **The version NEVER moves without the user's explicit order** (D-425). Bumps ride the RELEASE branch only (D-430) — the mainline always carries 1.1.20/10120; don't "fix" that.
2. **CI is the only build machine** (CORE_RULES §8). Every shipped APK is built + signed in GitHub Actions — never locally.
3. **Push path = debug, arm64-v8a only** (D-445). **Shipped releases = ALL FOUR ABIs + universal, release-signed** (D-423).
4. **No R8/minification on the release line** (D-436 — it broke the extension system on device; the release buildType is `isMinifyEnabled=false`).
5. **The updater follows the build type** (D-440): debug builds check `testplay-byte/ANI-KUTA` releases; release builds check `Confused-Creature-180/ANI-KUTA`. The ABI-aware updater matches `-{abi}.` in the asset name — unprefixed names break the device's APK pick.
6. **The release must NOT be marked prerelease** (the in-app updater relies on stable releases being visible).
7. **The tag must match `AndroidConfig.versionName`** — both release workflows verify the match and hard-fail.
8. **Signing:** the release keystore decodes from the repository's GitHub Actions secrets (`ANIKUTA_KEYSTORE_BASE64` + the password secrets) BEFORE the build; the apksigner verify gate hard-fails on any unsigned APK. Debug builds sign with the committed `anikuta-debug.keystore` (every debug build installs over every other debug build).
9. **The CI budget:** ≤2 runs per cycle, disclosed (D-472). Release rounds historically supersede this on the user's explicit both-releases order (round 114 ran 3, disclosed in doc 96 §7).
10. **Zero code goes to the official repo** — the re-host carries ASSETS ONLY (the user's standing rule since round 39).

## 1. Routine A — the per-round DEBUG release (the D-565 loop's ship step)

*The loop that ran v1.1.38 → v1.1.71. Resumes only on the user's order (or their explicit "back to the old loop" phrasing).*

1. The round's implementation commit is CI GREEN on the mainline (`feature/round-57-cloudstream-downloads`) via the Build APK run.
2. Cut `release/<debug-version>` from the green head: `git checkout -b release/1.1.3N <green-sha>`.
3. ONE commit on that branch: the bump (`AndroidConfig.kt`: versionCode → 1013N-style monotonic, versionName → 1.1.3N; keep the D-430 comment block intact).
4. Push the branch, then the **annotated tag** `v1.1.3N` on the bump commit — the tag push triggers `release-apk.yml` (tag-driven; a `v*` tag on the release branch).
5. `release-apk.yml` builds the debug APK (arm64-v8a), creates the GitHub Release on **testplay-byte/ANI-KUTA**: `ani-kuta-vX.Y.Z-debug-arm64-v8a.apk` + `SHA256SUMS.txt`, **stable + --latest**.
6. Poll the run via the Actions API until GREEN; then verify the release LIVE via the API (assets, sizes, stable/latest flags, timestamps).
7. **versionCode must exceed the previous debug release** (the in-app debug updater installs in place — e.g. 10171 > 10170).
8. Docs: the round record's release section + progress/SESSION LIVE facts. ntfy.

*Reference executions: v1.1.69 (doc 94), v1.1.70 (doc 95), v1.1.71 (doc 96 §6 — run 36926038964 GREEN first-try), v1.1.72 (doc 99 §3 — run 37036338129 GREEN first-try, the round-117 both-lines order).*

## 2. Routine B — the PROFESSIONAL release (all-ABI, release-signed)

*The Confused-Creature-180 line: v1.1.2 → v1.1.3 (round 79) → v1.1.14 (round 114 — THE MISTAKEN NUMBER, corrected to v1.1.5 by the user's round-117 order, D-742). Runs ONLY on the user's explicit order with an explicit version number.*

1. Cut `release/<version>` from the round's green mainline head (the same head the round's docs landed on).
2. ONE commit on that branch: the professional bump (`AndroidConfig.kt`: e.g. 10114 / 1.1.14). The versionCode must exceed the last professional release (10114 > 10103) so devices update in place.
3. Push the branch. **Do NOT push a tag** — dispatch `release-build-once.yml` instead (workflow_dispatch):
   - `tag`: `v<version>` (e.g. `v1.1.14`) — it must match versionName.
   - `ref`: `release/<version>` (the branch just pushed — D-564: the tag name may already exist from the old internal rounds, so the professional line builds from its release branch ref).
   - Use the GitHub API: `POST /repos/testplay-byte/ANI-KUTA/actions/workflows/release-build-once.yml/dispatches` with `{"ref": "release/<version>", "inputs": {"tag": "v<version>", "ref": "release/<version>"}}` → expect HTTP 204.
4. The workflow: decodes the keystore from secrets (keytool gate), builds ALL ABIs (`-PreleaseAllAbis=true`: arm64-v8a, armeabi-v7a, x86, x86_64 + universal), and hard-gates on: tag↔versionName match, per-APK `lib/` ABI audit, per-APK apksigner verification. The run artifact = the full APK set (ZIP, ~500MB).
5. Download the artifact (resumable curl — it's big), verify locally: `sha256sum -c` against the workflow's sums + the per-APK lib/ ABI listing (unzip -l; coreutils/unzip only — no Android tooling, CORE_RULES §8).
6. → continue to Routine C (the re-host). The dev repo itself gets NO release from this run.

*Reference execution: v1.1.14 (doc 96 §7 — run 36926131795 GREEN first-try, every gate passed) and its CORRECTED successor v1.1.5 (doc 99 §4 — run 37036448781 GREEN first-try; 10105 > 10103 the v1.1.3 baseline; a device holding the mistaken 10114 needs a one-time uninstall — the disclosed wrinkle).*

## 3. Routine C — the official re-host (Confused-Creature-180/ANI-KUTA)

*Assets ONLY — never code (the user's standing rule). The round-39/76 blocker (no release-agent token) was cleared in round 114 by the user's PAT — and RECURRED in round 117 (the PAT lives with the user per release; round 117's re-host of v1.1.5 sits staged pending the token — doc 99 §7).*

1. **The token:** the release PAT lives WITH THE USER — ask for it when the professional release is ordered (the round-114 re-host used the user-provided PAT in-session; it has admin/push on the official repo). Never commit it, never paste it into logs.
2. **Prepare the assets** (exact naming — the updater matches `-{abi}.`):
   - `Ani-Kuta-<abi>.apk` × 5 (arm64-v8a, armeabi-v7a, x86, x86_64, universal) — byte-identical to the verified artifact APKs (rename only).
   - `Ani-Kuta-<abi>.zip` × 5 — each built `zip -9` around the byte-identical APK; verify by comparing the APK's sha256 inside vs outside the zip.
   - `SHA256SUMS.txt` — one line per file (the ten APK+ZIP assets), sha256, newline-terminated.
3. **Create the release** via the API on `Confused-Creature-180/ANI-KUTA`: tag `v<version>`, target `main` (the official repo's own branch), **stable (NOT prerelease) + --latest**. The release body: clean user-facing bullets (what's new) + the website link — no hashes, no tables, no internal jargon.
4. **Upload the 11 assets** (the 5 APKs + 5 ZIPs + sums) via the upload-asset API. Post-upload verification: re-download the arm64 APK + ZIP, checksum-match against the sums file; confirm the sums file is byte-identical to the staged one; confirm the release list order (new version on top, stable+latest flags set).
5. → continue to Routine D (the website).

## 4. Routine D — the website + README refresh (the official repo's face)

1. **The gh-pages site** (`https://confused-creature-180.github.io/ANI-KUTA/` — served from the `gh-pages` branch, fully dynamic: it fetches `releases/latest` for the real data). The ONE static fallback string: the footer chip in `index.html` ("v1.1.3 — latest release" → the new version) — update via the contents API (a single commit on `gh-pages`).
2. **The official README's download links** (five, one per ABI) — re-point at the new release's assets via the contents API (one commit on `main` of the official repo).
3. Verify Pages rebuilt (the pages-build-deployment status → `built @ <sha>`), then fetch the LIVE page over HTTPS (HTTP 200 + the chip text + the dynamic asset sizes).
4. **The updater simulation (before declaring done):** the professional app's `GitHubUpdateSource` must parse the new version > the previously installed one; the ABI picker must match `Ani-Kuta-<abi>.apk` (universal fallback intact); the derived versionCode must exceed the old (installs over it). The debug app's updater is UNTOUCHED by professional releases (D-447 — the dev repo's latest stays the debug release).

## 5. The post-release ledger (all routines)

- The round record (doc NN §the-release-record): every run ID + verdict, the LIVE timestamps, the asset inventory, the updater simulation, the deviations + their disclosure.
- `memory/progress.md` top block + `SESSION.md` Current State: the new LIVE versions.
- `memory/decisions.md`: the release's decision entry (if the round banks one).
- The twin commit (docs) lands on the mainline AFTER the release is verified LIVE; docs-only pushes trigger no CI (D-472).
- ntfy (short message — the topic 429s).

## 6. The failure playbook (what went wrong historically)

- **The v1.1.1 R8 release broke the extension system** → D-436: no minification, ever, on the release line.
- **A release APK appeared in the main-branch CI artifact** (round 37) → D-435: the push path is debug-ONLY on every branch; release APKs exist only via the tag/dispatch paths.
- **The v1.1.3 tag collision** (the old internal round had taken it) → D-564: `release-build-once.yml` takes an explicit `ref` — the professional line builds from its release branch, not the tag.
- **The round-76 re-host blocked** on the missing release-agent token → cleared round 114 by the user's PAT; the token ALWAYS lives with the user (re-ask per release).
- **ntfy 429s** (rounds 111-115) → retry with backoff, keep it short; the round's completeness never depends on the notification.
- **The round-80 playbook was lost** (kept repo-external, sandbox reset) → this file exists IN the repo now. Keep it here.

---
*Rebuilt at Round 115 (2026-10-02) from: doc 96 §6-7 (the round-114 executions), the round-114 worklog, `BUILD-AND-BRANCH-GUIDE.md`, the workflow files, and SESSION/decisions through D-739. Update this file whenever a release routine changes — it is the single source of release truth.*
