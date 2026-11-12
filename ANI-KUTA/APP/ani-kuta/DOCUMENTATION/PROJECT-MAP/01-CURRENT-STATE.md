# 01 — CURRENT STATE (verified 2026-11-13, post-round-144/v1.1.86)

> Every number here was RE-VERIFIED against the code this audit (not copied forward).
> The stale-count lesson: the old docs said "57 modules / 25 tables / 634 files" — reality:
> **59 modules / 30 tables / 678 app .kt files**. Re-verify at every phase boundary.

## 1. The verified counts

| Fact | Value | How to re-verify |
|---|---|---|
| Modules | **59** (1 app + 34 core + 2 data + 22 feature = 13 features) | `grep -c '^include(' settings.gradle.kts` |
| The latest core module | `core/torrent` (round 143 — the removable torrent engine) | `core/torrent/TORRENT-DESIGN.md` |
| SQLDelight tables | **30** | `grep -rh 'CREATE TABLE' core/database/src/main/sqldelight --include='*.sq' --include='*.sqm'` |
| Schema files | 17 `.sq` + 3 `.sqm` (v1→v2, v2→v3, v3→v4) | `core/database/src/main/sqldelight/com/confused/anikuta/core/database/` |
| Kotlin files (APP tree) | **678** | `find . -name '*.kt' -not -path '*/build/*' \| wc -l` |
| The decisions ledger | D-001..D-867+ | `AGENT-CONTEXT/memory/decisions.md` (newest first) |
| The round ledger | 1..124 | `DOCUMENTATION/cloudstream-v2/` (the 100-series) |
| Live debug release | **v1.1.86 / 10186** (round 144; 76,076,032 bytes arm64 — includes the torrent .so) | testplay-byte /releases/latest |
| Live professional release | **v1.1.7 / 10107** (round 142) | Confused-Creature-180/ANI-KUTA /releases/latest |
| Mainline version | 1.1.20 / 10120 (D-430 — correct, do not "fix") | `AndroidConfig.kt:547-548` |

## 2. What works TODAY (the user-facing systems)

- **Library** (feature/anime-library): categories, the customize sheet, the badges
  (defaults: compact grid, 3 columns, 1 title line, RELEASED episode badge SUB+DUB, score
  OFF, caught-up OFF, total ON — the round-144 defaults).
- **Browse/Search** (anime-browse / anime-search): the AniList browse (hero + trending/
  popular/top-rated) + the CS provider shelves (the de-facto extension home).
- **Details** (anime-details): 4 episode-list layouts (CLASSIC/GRID/TIMELINE/CINEMA) + the
  element toggles + the sort (default DESCENDING, round 144) + the seasons/groups + the
  target-episode arrival (scroll-to + highlight, D-863) + the metadata preservation merge
  (D-854) + the download-state fallback resolution.
- **Players**: the MPV stack (feature/watch — gestures, pinch-zoom, cinema mode, Up Next at
  the last 10s, AniSkip, the cumulative 60s watch-mark) and the CS stack (feature/cs-watch —
  the resolve sheet, the D-553 server memory, the TORRENT seam). Both feed the session ledger.
- **Downloads** (core/download + feature/download): the queue, offline resume, SAF storage,
  the DASH offline cache, the downloaded-episode playback (the number-alias keys).
- **Torrent** (core/torrent, round 143): CS-first magnet streaming via the embedded
  TorrServer-lineage Go server; default OFF behind the disclaimer; REMOVABLE
  (TORRENT-REMOVAL.md). Streaming only so far — T4 (downloads through the queue) is open.
- **Backup & restore** (core/backup + the app screens): the .anikuta format (the DB + the
  settings + the covers + the extensions), merge-only restore, automatic backups, the
  cross-app import (Aniyomi/Anikku/AniList GDPR/CloudStream) + export + the extension
  batch-install picker.
- **Profile** (app/profile): real-minutes tracking (watch flow, time DNA, streak, heatmap,
  genre radar, timeline), recently-watched + the day sheet (SESSION-LEDGER-derived, D-862),
  the open-by-mainId clicks (removed-from-library content opens, D-863).
- **Notifications** (core/notifications + core/updates + app/notifications): the episode
  banners (5 poster templates through the real composer), the smart update-check engine
  (learned release offsets, backoff), the check lifecycle notifications (the live card + the
  minimal result card, round 143).
- **The wizard** (feature/onboarding, round 144): 12 steps — the personalize gate ("Lets
  check them out" / "Later in the app") + the three live-preview config steps (details
  presets / player styles / notifications+poster) + the storage/restore/permissions steps
  ("all permissions are optional") + the improved summary.
- **Ads** (core/ads): the sponsor interstitial gated by the 3-DAY launch grace (round 143;
  the D-407 one-shot superseded), the return pill, the debug always-sponsor toggle.
- **Updater** (core/app-update): the ABI-aware updater (D-423), the debug↔official repo
  split (D-440), the real-error contract (D-815).
- **Permissions hub** (Settings → Permissions, round 143): every askable permission with
  live state + one-tap allow.

## 3. The open work (ordered by the user's stated priority)

1. **T3 — the Aniyomi-extension torrent side**: the `torrentutils` contract
   (`TorrentUtils.getTorrentInfo`) in source-api + the bridge wiring; deferred until the CS
   side is device-validated (the CS side shipped in v1.1.86).
2. **T4 — torrent downloads**: the stream URL as a DownloadRequest source + the DATA_SYNC
   FGS + the settings expansion (storage/metered/trackers).
3. **The new personalized Home ("My page")**: replace/augment Browse with continue-watching
   (the store is READY: `watch.sq` getContinueWatching + the unused `library_show_continue_
   watching` toggle to remove), next-releasing (episode_schedule), recommendations (AniList
   GraphQL Media.recommendations — the client call does not exist yet). The layout planning
   happens WITH the user.
4. **The wizard walkthrough** (coach-marks) — sequenced after the Home so it tours the final IA.
5. The issues register (`09-KNOWN-ISSUES.md`) — the audit findings awaiting triage.

## 4. The verification posture

- **CI**: `build-apk.yml` (assembleDebug, arm64-v8a) on every main push; `release-apk.yml`
  on `v*` tags; `release-build-once.yml` (dispatch, all-ABI) for the professional line.
- **The test wall**: 219+ tests across 10 modules (round 127) — CI runs assembleDebug ONLY;
  the suite runs locally (`./gradlew test`). ⚠ The user's machine boundary (round 143): NO
  local Gradle until the user says otherwise — the migration tests (SchemaMigrationTest,
  now 6 tests incl. the v3→v4 pair) are pinned but UNRUN since round 144 (disclosed).
- The last 3 rounds' device-testing checklists live in the replies + the round records.
