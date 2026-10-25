# 110 · ROUND 128 — THE COMMUNITY LINKS, THE PROFILE TOP FIX, AND THE PROFESSIONAL v1.1.6 RELEASE

> 2026-10-25/26. The user's round-128 order: (1) a Discord + Website section
> in More > About below App version / above Updates; (2) fix the Profile
> page's empty top padding at scroll 0; (3) build the ACTUAL signed release
> APK v1.1.6 and publish it properly on the official repo
> (Confused-Creature-180/ANI-KUTA) with the website updated (the device-probe
> "ARCHITEC" label). Decisions D-799..D-801.

## 1. The app fixes (mainline, commit 16c0980a)

- **The Community section (D-799)**: AboutScreen gains a "Community" section
  between App version and Updates — two rows (Discord /
  `https://discord.gg/X7JCCxX4Hr`, Website /
  `https://confused-creature-180.github.io/ANI-KUTA/`) opening via the app's
  raw-Intent pattern (TrackersScreen/UpdateBottomSheet precedent; no
  LocalUriHandler anywhere by design). A private `LinkRow` composable keeps
  the row style identical to the manual-check row.
- **The Profile scroll-0 fix (D-800)**: the empty band above/below the "My
  Profile" heading at the top of the page — the shared `CollapsingHeader`
  gained OPTIONAL expanded-state knobs (expandedTitleSize 32→26sp,
  expandedPaddingTop 8→2, expandedPaddingBottom 4→0 — defaults unchanged so
  every other screen is untouched), the Profile screen passes the tighter
  values, and both tabs' tab-band item lost its extra vertical padding
  (Timeline's top contentPadding 8→0). Compiled + CI GREEN (run
  40210497739).

## 2. The professional v1.1.6 release (the playbook, executed)

- **release/1.1.6** cut from the green mainline head `16c0980a`; ONE bump
  commit `46424a67` (10120/1.1.20 → **10106/1.1.6**; versionCode = 10000 +
  minor×100 + patch; D-430 — the bump rides the release branch only).
- **The signed build**: `release-build-once.yml` dispatched with
  `{tag: v1.1.6, ref: release/1.1.6}` → run **40211073870 GREEN** (the
  keystore-from-secrets signing; the tag↔versionName gate passed). CI runs
  this cycle: exactly 2 (Build APK 40210497739 + Release Build
  40211073870) — inside D-472.
- **The artifact download** (528,205,256 bytes) survived a flaky connection —
  a single fresh `curl` with its own --retry (the resume-loop against the
  302→Azure redirect produced slow partial files; the user steered: download
  once, show progress).
- **Verification**: `sha256sum -c SHA256SUMS.txt` OK ×5; per-APK `lib/` ABI
  audit — each APK carries exactly its ABI, universal all four.
- **The staging**: 5 × `Ani-Kuta-<abi>.apk` (renames, byte-identical) + 5 ×
  `Ani-Kuta-<abi>.zip` (deflate-9) + `SHA256SUMS.txt` (ten APK+ZIP sums).
- **The release**: created on Confused-Creature-180/ANI-KUTA (release id
  423572649) — tag `v1.1.6` targeting the official `main`, **stable +
  make_latest** (the updater depends on stable), user-facing body with the
  download notes. All 11 assets uploaded (201 ×11); `/releases/latest` →
  v1.1.6 with 11 assets; a fresh download of the arm64 APK matched its
  staged sha256 byte-for-byte.
- **The updater effect**: release-line installs (D-440 — release builds
  resolve the OFFICIAL repo at runtime) on v1.1.5/10105 now get the v1.1.6
  update offer; the D-423 ABI picker matches `Ani-Kuta-<abi>.apk`.

## 3. The website + README (D-801)

- **gh-pages** (`https://confused-creature-180.github.io/ANI-KUTA/`): the
  static footer chip fallback → "v1.1.6 — latest release"; the device-probe
  label **"Architecture" → "ARCHITEC"** (the user's exact order). Commit
  `b82d1bb` (via a temp clone of the official repo — the contents-API PUTs
  without `?ref=` landed on the default branch TWICE; see the lesson below).
  Live-verified: the page serves ARCHITEC + the v1.1.6 chip.
- **The official README**: the five download links re-pointed v1.1.5 →
  v1.1.6 (commit `df87201`); live-verified ×5.

## 4. THE INCIDENT (disclosed, resolved)

Two contents-API PUTs intended for gh-pages landed on the official repo's
**main** (the `?ref=gh-pages` param was ignored/dropped — once in a curl
call, once when a `git checkout gh-pages` aborted and the commit went to
main), adding an `index.html` where the zero-code policy forbids one. Both
were removed immediately (commits `c333892`, `3763937` — "remove the
accidental index.html (belongs on gh-pages only)"), and the final state is
verified: official `main` tree = README.md, RELEASES.md, assets/
(.gitignore) — no index.html; the website edits live ONLY on gh-pages. The
lesson is banked: never use the contents API for cross-branch edits — clone
the repo, edit, push (the user's own suggestion, which is what finally
worked).

## 5. Version state

- Mainline `main`: **1.1.20 / 10120** (D-430 untouched).
- The professional line: **v1.1.6 / 10106 — LIVE** on the official repo
  (stable latest), signed by CI, ten download assets + the sums file.
- The debug line: latest release still **v1.1.73 / 10173** (testplay-byte;
  the round-128 mainline code is NOT in any debug release — the next debug
  release is the user's order, D-738).
- `release/1.1.6` remains on testplay-byte as the release-branch record.
