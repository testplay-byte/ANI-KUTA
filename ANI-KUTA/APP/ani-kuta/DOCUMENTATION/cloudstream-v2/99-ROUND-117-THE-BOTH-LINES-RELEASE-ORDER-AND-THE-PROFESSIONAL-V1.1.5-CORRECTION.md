# Round 117 — The Both-Lines Release Order And The Professional v1.1.5 Correction

**Round type:** the release round (the user's explicit both-lines order — D-738's
recovery path exercised) · **Work orders:** D-742 · **Branch:**
`feature/round-57-cloudstream-downloads` (the mainline; both release branches
cut from its green head db72c737)

> **The order (the user's words):** "I want you to do the debug release, and I
> also want you to do the release, the actual release build to the actual
> published repository. But on the published repository, you made a mistake,
> and I need you to correct that, and that is I need you to make the release
> version version 1.1.5 instead of version 1.1.14." — plus "do not rush
> anything; take your time… make sure that each and every single one of the
> things… are handled properly."

## 1. What the order means (traced before execution)

- **"The debug release"** — the debug line's next per-sequence release on the
  DEV repo (testplay-byte/ANI-KUTA): **v1.1.72** (after v1.1.71, round 114).
- **"The release, the actual release build to the actual published
  repository"** — the PROFESSIONAL line: the all-ABI, release-signed set
  built by `release-build-once.yml`, re-hosted on the OFFICIAL repo
  (`Confused-Creature-180/ANI-KUTA`) — the repo the codebase itself names
  "the PUBLISHED repo" (D-440: release builds' updater points there; the
  playbook's Routine C).
- **"The mistake… version 1.1.5 instead of version 1.1.14"** — round 114's
  professional release went out as **v1.1.14** (the number given in that
  round's order); the user now corrects the record: the release version is
  **1.1.5**. The correction (§7): the mistaken v1.1.14 release + tag on the
  official repo are deleted, the fresh v1.1.5 takes the line's head, and the
  website chip + the official README's five download links re-point.
- **D-738's status:** this order is exactly the "explicit order" D-738 waits
  for — it restores BOTH lines for this round. The default (every round ends
  at CI green; releases need the user's explicit order) resumes afterwards.

## 2. The version analysis (D-742)

- **The professional line after the correction:** v1.1.1 → v1.1.2 → v1.1.3
  (round 79) → **v1.1.5** (round 117). The mistaken v1.1.14 (round 114,
  published 2026-10-01T21:20:20Z, release id 401364896) is deleted as part
  of the correction — the release list must read v1.1.5 → v1.1.3 → v1.1.2 so
  the updater's "highest version" pick is v1.1.5 (a surviving v1.1.14 would
  keep winning that pick — the deletion is not cosmetic, it is functional).
- **versionCode 10105** (the 10000 + minor×100 + patch convention): exceeds
  the v1.1.3 baseline (10103) so devices update in place. **The disclosed
  wrinkle:** a device that installed the mistaken v1.1.14 (versionCode
  10114) can NOT take v1.1.5 as an in-place update — Android blocks
  versionCode downgrades and the updater's tuple compare gates
  (1,1,5) < (1,1,14). Such an install needs a ONE-TIME uninstall first (the
  v1.1.14 release was live for one day; the professional audience is small).
  v1.1.3 installs — the line's real baseline — update cleanly.
- **The debug line:** v1.1.72/10172 (> 10171 — the debug app updates in
  place, D-440).
- **The tag collision (D-564, expected):** the `v1.1.5` tag on the dev repo
  is taken by the old Sep-18 debug release — the professional line builds
  from its release-branch ref exactly per D-564; the dev repo's old tag is
  untouched, and no tag is pushed for the professional bump.

## 3. The debug release — v1.1.72 (Routine A)

`release/1.1.72` cut from the green mainline head db72c737 (the round-116
ledger commit; the implementation 53cbc0ef's Build APK run 37006588128 GREEN
FIRST-TRY) → the bump f70d1fc2 (10172/1.1.72, the D-430 comment block) →
the annotated tag `v1.1.72` ("the gate that holds, the one About door" — the
round-116 set as user-facing bullets) → branch + tag pushed → the tag push
triggered `release-apk.yml` → **run 37036338129 GREEN FIRST-TRY** → verified
LIVE via the API: **published 2026-10-02T16:52:19Z, stable + latest**
(`/releases/latest` → v1.1.72), `ani-kuta-v1.1.72-debug-arm64-v8a.apk`
(63,542,097 bytes) + `SHA256SUMS.txt`, the body = the tag bullets + the
workflow's Install line. 10172 > 10171 → the debug app updates in place.

## 4. The professional build — v1.1.5 (Routine B)

`release/1.1.5` cut from the SAME green head db72c737 → the bump d52de022
(10105/1.1.5, the D-430 comment block carrying the correction rationale) →
the branch pushed (NO tag — D-564) → `release-build-once.yml` dispatched
(tag=v1.1.5, ref=release/1.1.5; HTTP 204) → **run 37036448781 GREEN
FIRST-TRY** (every gate: tag↔versionName, the keystore-from-secrets +
keytool sanity, the all-ABI build, the 5-APK existence audit, the per-APK
lib/ ABI audit, the per-APK apksigner gate — the CN=ANI-KUTA release
signature) → the artifact `ani-kuta-v1.1.5-release-allabi` (id 11241580958,
526,906,685 bytes) downloaded and verified locally:

- `sha256sum -c` — ALL FIVE APKs OK (the workflow's own sums).
- The per-APK `lib/` ABI audit re-run — exact (each split carries only its
  own natives; the universal carries all four).
- The staged re-host set (Routine C's exact naming, `/home/z/r117-release/stage`):
  `Ani-Kuta-<abi>.apk` × 5 (byte-identical renames) + `Ani-Kuta-<abi>.zip`
  × 5 (each `zip -9` around the byte-identical APK — verified by sha256
  inside vs outside, ALL PASS) + `SHA256SUMS.txt` (the ten assets).
  arm64-v8a 62,104,556 / armeabi-v7a 58,908,262 / x86 64,477,326 /
  x86_64 68,316,279 / universal 163,641,706 bytes (APKs).

## 5. CI history

| Run | Workflow | Ref | Result |
|---|---|---|---|
| 37036338129 | Release APK (debug) | tag v1.1.72 (bump f70d1fc2) | **GREEN, FIRST TRY** |
| 37036448781 | Release Build (One-Time) | release/1.1.5 (bump d52de022) | **GREEN, FIRST TRY** |

Two release runs this cycle on the user's explicit both-releases order (the
round-114 precedent; the ≤2 budget consumed exactly). The round's docs push
is docs-only + DASHBOARD (no APK build per D-472; the dashboard Pages deploy
runs — disclosed).

## 6. The device checklist (v1.1.72 debug)

1. **The gate:** Settings → press-and-hold the Debug row for ten full
   seconds → the extension-testing page opens (a still OR gently drifting
   hold; ordinary scrolling over the row and quick taps behave as before).
2. **The one About door:** the Settings hub shows no About & Updates row;
   the More page's About & Updates is the door (with the update dot);
   searching "version"/"about" still lands + pulses there.
3. **The in-place update:** the in-app updater on a v1.1.71 debug install
   offers v1.1.72 (10172 > 10171); installing over keeps library,
   downloads and settings.
4. Regression sweep: the elements' grids, the episodes-list layouts, the
   search landings — all untouched since v1.1.71 (no code moved; only the
   round-116 set rides along).

## 7. THE RE-HOST + THE v1.1.14 CORRECTION — PENDING THE RELEASE PAT

**The blocker (disclosed per the user's standing instruction):** the
correction happens on `Confused-Creature-180/ANI-KUTA`, and the current
dev PAT (testplay-byte's) is **pull-only there** (API-verified this
session: permissions pull=true, push=false). Per the playbook's Routine C
step 1 — "the release PAT lives WITH THE USER — ask for it when the
professional release is ordered" — the ask went out with this round's
report. Everything stageable is staged; the moment the token exists, the
remaining steps are:

1. **Delete the mistaken v1.1.14** on the official repo: the release
   (id 401364896) + the tag (`git/refs/tags/v1.1.14`).
2. **Create the v1.1.5 release**: tag `v1.1.5`, target `main`, stable +
   `make_latest`, the body = the v1.1.5 user-facing bullets + the website
   link (staged at `/home/z/r117-release/release-body.md`; mirrors the
   v1.1.14 body's structure + the one-About-door bullet).
3. **Upload the 11 staged assets** (the 5 APKs + 5 ZIPs + sums). Post-upload
   verification: re-download the arm64 APK + ZIP, checksum-match; the sums
   file byte-identical; the release list order v1.1.5 → v1.1.3 → v1.1.2.
4. **The website** (gh-pages): the footer chip's static fallback
   `v1.1.14 — latest release` → `v1.1.5 — latest release` (index.html line
   ~2127; the site's dynamic half reads `releases/latest` and follows
   automatically). Verify the Pages rebuild + the LIVE chip over HTTPS.
5. **The official README:** the five download links re-point
   `…/releases/download/v1.1.14/…` → `…/releases/download/v1.1.5/…`.
6. **The updater simulation:** the professional app's GitHubUpdateSource —
   v1.1.5 is the highest version on the official repo → the tuple
   (1,1,5) > the installed (1,1,3) → the update IS offered; the ABI-aware
   picker matches `Ani-Kuta-arm64-v8a.apk` via the `"-arm64-v8a."`
   contains-check (the universal fallback intact); the APK's versionCode
   10105 > 10103 → installs over v1.1.3. The debug app's updater is
   untouched (testplay-byte's latest stays the v1.1.72 debug APK — D-447).

**If the sandbox resets before the token arrives:** the artifact
`ani-kuta-v1.1.5-release-allabi` (id 11241580958) lives 90 days on the run's
page; the staged set re-derives from it (rename + zip + sums — §4's gates).

## 8. The sub-agent verification record

None this round — no mainline code changed (the round-116 implementation was
audited + CI-greened last round; this round only cuts release branches whose
ONE commit each is the mechanical AndroidConfig bump, and the release
workflows themselves hard-gate everything: tag↔versionName, ABI audits,
apksigner). The main agent verified every artifact by checksum + ABI audit
locally, and every LIVE fact via the API.

## 9. The ledger updates this round

`memory/decisions.md` (D-742), `memory/progress.md` (the CURRENT STATUS
block + the Round 117 section), `memory/changelog.md` (the Round 117
section), `SESSION.md` (the Current State refresh), the RELEASE-PLAYBOOK's
reference executions (v1.1.72 + v1.1.5). The mainline stays 1.1.20/10120
(D-430). NO dashboard touch (the round-89 standing instruction — the status
facts changed only in the docs; the dashboard's stale v1.1.71/v1.1.14
mentions refresh at the user's focus call, naturally after the re-host
completes).
