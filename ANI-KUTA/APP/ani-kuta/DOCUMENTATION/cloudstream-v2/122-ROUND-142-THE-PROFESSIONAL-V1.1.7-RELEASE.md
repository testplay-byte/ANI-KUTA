# Record 122 — Round 142 (D-856): THE PROFESSIONAL v1.1.7 RELEASE

> The user's order: "now i would like you to build the professional release and then upload the
> files properly to the release repository now continue and use the version 1.1.7 and make sure
> to read the docs the files and such related to it. now continue and if any issues let me know"

## 1. The version

**1.1.7 / 10107** — the professional line's next head by the line's own numbering
(v1.1.1 → 1.1.2 → 1.1.3 → 1.1.5 → 1.1.6 → **1.1.7**). 10107 > 10106 (v1.1.6, round 128) so
v1.1.6 devices update in place. D-430 held: the bump rides the release branch only; the
mainline stays 1.1.20/10120.

## 2. Routine B — the all-ABI release build (the playbook, followed exactly)

- **Branch:** `release/1.1.7` cut from the round-141 main head **e3bd6181** (the code tree is
  CI-green 9ae8930f — Build APK 42398295524; the delta is docs-only commits).
- **The ONE bump commit:** b71cd07d — AndroidConfig.kt 10120/1.1.20 → **10107/1.1.7** with the
  professional-release comment block. Branch pushed to testplay-byte.
- **Dispatch (no tag push on the dev repo):** `release-build-once.yml` via
  `POST .../workflows/release-build-once.yml/dispatches` with `{"ref": "release/1.1.7",
  "inputs": {"tag": "v1.1.7", "ref": "release/1.1.7"}}` → HTTP 204 → **run 42411778806** —
  **GREEN first-try** (~9 min). CI-side gates all passed: tag↔versionName match, per-APK
  lib/ ABI audits (4 single-ABI + universal carrying all four), apksigner verification of all
  five APKs. Zero code to the official repo from this run; the dev repo gets NO release
  (D-447 held — verified: testplay-byte `/releases/latest` still **v1.1.85** with only the
  debug arm64 asset + sums).
- **Artifact:** `ani-kuta-v1.1.7-release-allabi` (id 13589123893, 531,878,314 bytes).
  THE WRINKLE (disclosed): the first download attempt failed with HTTP 401 — urllib followed
  the API's 302 to Azure blob storage WITH the Authorization header attached (Azure rejects
  it). Re-downloaded with `curl -sL` (curl strips the auth header on cross-host redirects —
  exactly why the playbook says "resumable curl"). Zip integrity tested, extracted clean.
- **Local verification:** the workflow's own SHA256SUMS.txt verified against all five APKs
  (5/5 sha OK) + the per-APK ABI audit (arm64-v8a / armeabi-v7a / x86 / x86_64 singles exact,
  universal = all four).

## 3. Routine C — the official re-host (Confused-Creature-180/ANI-KUTA)

- **Token:** the official-repo credential is banked in the workspace at
  `credentials/official-repo-credentials` (account Confused-Creature-180) — validated admin+
  push BEFORE the build finished, so the round-117/114 "PAT lives with the user" blocker did
  not recur.
- **Staged (planning/r142-release):** `Ani-Kuta-<abi>.apk` ×5 (renamed byte-identical — sha
  match asserted), `Ani-Kuta-<abi>.zip` ×5 (ZIP_DEFLATED level 9, the APK at the zip ROOT with
  the matching name — the v1.1.6 wire shape; every zip's INNER apk sha256 verified against the
  outer), `SHA256SUMS.txt` (10 lines, `<sha256> *<name>`, LF, newline-terminated — 876 bytes,
  the exact byte size of v1.1.6's sums file).
- **The release:** id **437834754**, tag **v1.1.7** → target_commitish **main** of the official
  repo, **stable (not draft, not prerelease) + make_latest**. Body: the user-facing WHAT'S NEW
  (Player / Backup & restore / My Profile / Downloads & library / Extensions & updates) +
  download notes + the website + Discord links — no hashes, no tables, no internal jargon
  (3,787 bytes).
- **THE INCIDENT (disclosed):** the first upload pass uploaded the arm64 APK (201) then
  CRASHED — the script printed the response WITHOUT parsing JSON for raw uploads
  (`up["size"]` on bytes). Fixed the parser + made the pass idempotent (adopt the release when
  the body matches ours; skip already-attached assets by name + size). The resumed pass
  uploaded the remaining 10 assets (201 × 10).
- **Post-upload verification (all green):** 11/11 assets present, every asset size == the
  staged bytes; SHA256SUMS.txt re-downloaded and BYTE-IDENTICAL to the staged file; the arm64
  APK (62,612,568 bytes, sha 813d12bc3fb1…) and the arm64 ZIP (43,688,250 bytes) re-downloaded
  and checksum-matched; `/releases/latest` → **v1.1.7**.

## 4. Routine D — the website + the README (the D-801 lesson honored)

- **Real CLONE** of the official repo (planning/r142-official) — no contents-API writes, no
  checkout-without-verification.
- **gh-pages:** commit **6aeb034** — the footer chip `v1.1.6 — latest release` → `v1.1.7 —
  latest release` in index.html (exactly 1 line changed — verified with `git show --stat`).
- **main:** commit **4e6a60f** — the README's five download links re-pointed
  `/download/v1.1.6/` → `/download/v1.1.7/` (exactly 5 lines; zero stray v1.1.6 references
  left).
- **Pages:** the build API reported `built @ 6aeb034…`. LIVE fetches: the page serves
  `v1.1.7 — latest release`; the live README has 5× `/download/v1.1.7/` and 0× `/v1.1.6/`.

## 5. The updater simulation (playbook Routine D step 4) — ALL PASS

Run against the LIVE API with GitHubUpdateSource's exact logic (list endpoint, prereleases
included; tag_name strip v → (major,minor,patch); highest tuple wins, stable beats prerelease;
asset pick walks SUPPORTED_ABIS then `-universal.` then first APK):

- device on **1.1.6** → OFFERED **v1.1.7** via `Ani-Kuta-arm64-v8a.apk` (the real versionCode
  10107 > 10106 installs in place; the derived informational code 1010007)
- device on **1.1.5** → OFFERED v1.1.7
- device on **1.1.7** → no update

## 6. The ledger

- **CI (D-472): ONE run this cycle — the dispatch 42411778806** (the release build). Docs
  pushes trigger no CI. The release/1.1.7 branch push triggers no build path (D-435).
- **The repos:** testplay-byte = release/1.1.7 branch at b71cd07d + main at the docs commit;
  Confused-Creature-180 = tag v1.1.7 + release id 437834754 (11 assets) + gh-pages 6aeb034 +
  main 4e6a60f.
- **ntfy:** the ANIKUTAPROGRESS start notice FAILED (ntfy.sh throttling — WinError 10054 × 5);
  the completion notices were retried with long cooldowns (status recorded below when known).
- Next decision: **D-857**; next record: **123**.
