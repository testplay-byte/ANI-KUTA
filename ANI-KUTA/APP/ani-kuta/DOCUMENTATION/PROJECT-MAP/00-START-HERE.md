# ANI-KUTA — PROJECT MAP (the new-agent entry point)

> **Purpose: everything a NEW AI agent (or human) needs to get full context — what this app
> is, where every part lives, why it is the way it is, and what the current status is.**
> Maintained since round 145 (2026-11-13). The per-round narrative lives in
> `DOCUMENTATION/cloudstream-v2/1xx-*.md`; the decisions in `AGENT-CONTEXT/memory/decisions.md`
> (newest at the TOP). This folder = the ORIENTED map, not the history.

## 0. Read in this order (30 minutes)

1. **THIS file** — the map + the ground rules.
2. `01-CURRENT-STATE.md` — what the app does TODAY (versions, live systems, the last rounds).
3. `02-ARCHITECTURE-MAP.md` — the 59 modules, the dependency rules, the request flows.
4. `03-DATA-LAYER.md` — the 30 tables, the migrations (v1→v4), the identity system.
5. `04-PLAYBACK-PIPELINE.md` — the two players (MPV + CloudStream), the resolver chain, the
   watch tracking.
6. `05-EXTENSIONS-AND-TORRENT.md` — the two extension ecosystems + the embedded torrent
   engine (the removable module).
7. `06-BACKUP-RESTORE-PROFILE.md` — the .anikuta backup system, cross-app interop, the
   profile's tracking surfaces.
8. `07-SHELL-SYSTEMS.md` — MainActivity's navigation, the wizard, notifications, ads, the
   updater, permissions.
9. `09-KNOWN-ISSUES.md` — the LIVE register (every known flaw, severity, and status).
10. `10-TESTING-GUIDE.md` — the per-feature device test checklist.

## 1. What ANI-KUTA is

An Android anime streaming + downloading app (Kotlin, Jetpack Compose, libmpv, SQLDelight,
Koin, minSdk 24 / target+compile 36). Two content ecosystems run side by side:

- **Aniyomi-style extensions** (APK-packaged Kotlin sources — the `eu.kanade.tachiyomi`
  API surface, `core/source-api` + `data/extension`).
- **CloudStream extensions** (JS plugins — the vendored `core/cloudstream-api` compat layer +
  `data/cloudstream`), bridged into the same unified identity model.

Both feed ONE library/identity database (`main_entry` keyed by a synthetic `main_id`) and TWO
players (the MPV stack `feature/watch` + `core/player`; the ExoPlayer-host CS stack
`feature/cs-watch` + `core/cs-player` — CS links NEVER touch MPV directly, they resolve to
plain progressive URLs first). A third engine — **the embedded torrent server** (`core/torrent`,
round 143) — turns magnet/torrent links from CloudStream extensions into localhost HTTP
streams MPV plays; it is deliberately REMOVABLE (`core/torrent/TORRENT-REMOVAL.md`).

## 2. The repositories + the release lines

- **Dev/mainline**: `testplay-byte/ANI-KUTA`, branch `main`. The mainline version stays
  **1.1.20/10120** by doctrine (D-430: bumps ride RELEASE branches only — do not "fix").
- **The debug release line**: vN.N.NN tags trigger `release-apk.yml` → the debug arm64-v8a
  APK + SHA256SUMS.txt published STABLE+LATEST on testplay-byte. The in-app updater of the
  DEBUG line reads this repo. Latest live: **v1.1.86 / 10186** (round 144).
- **The professional line**: the all-ABI release-signed builds published on
  `Confused-Creature-180/ANI-KUTA` (the official repo; its in-app updater reads THERE).
  Latest live: **v1.1.7 / 10107** (round 142; carries rounds 129-141). The website chip + the
  README links live in that repo's gh-pages/main branches.
- **The release discipline**: `DOCUMENTATION/release/RELEASE-PLAYBOOK.md` — Routine A (debug)
  / B (all-ABI build) / C (official re-host, assets only) / D (website+README). Releases need
  the user's EXPLICIT order (D-738); the mainline always ends at CI green.

## 3. The ground rules (violating any of these has burned real rounds)

1. **CI is the only build machine** (CORE_RULES §8): every shipped APK is built in GitHub
   Actions. (Round 144's boundary: the user's machine must NOT run Gradle locally at all —
   the memory incident; when a round cannot compile locally, CI runs are the compile loop —
   batch fixes after the FULL error list lands, one red run per module layer.)
2. **Version bumps ride release branches only** (D-430) — mainline stays 1.1.20/10120.
3. **The tag rides the release branch's bump commit** — tagging main's head fails the
   workflow's tag↔versionName gate (the round-144 wrinkle).
4. **Releases (debug AND professional) need the user's explicit order** (D-738).
5. **The user's machine boundary**: no local Gradle, period (round 143 — the memory filled).
6. **Credentials**: `C:/Users/khurr/Desktop/ANI-KUTA/credentials/` (OUTSIDE the repo):
   `github-credentials` (testplay-byte, push+actions) and `official-repo-credentials`
   (Confused-Creature-180, admin+push for the professional line + the website). NEVER commit,
   log, or paste them.
7. **Heredocs are corrupted on this machine** — scripts go through the Write tool +
   `python -I <file>` (the standing lesson; bash-inline Python mangles quotes/em-dashes).
8. **Interrupted background tasks can NUL-fill files** — after any kill, scan the touched
   files for `\x00` before committing.
9. **Subagents**: up to 3 launched, but the practical concurrency is 2 (the third queues and
   can die with "concurrency limit exceeded" — relaunch/resume it when a slot frees).
10. **The round loop**: device report → research → implement → CI green → docs (the round
    record + the decisions + progress/changelog/SESSION) → the release on the user's order →
    ntfy (`ANIKUTAPROGRESS` progress, `THE-TASK-IS-DONE` completion).

## 4. Where the round ledger lives (the context spine)

- `AGENT-CONTEXT/memory/progress.md` — the CURRENT STATUS block at the TOP is the live truth
  (the "Historical session" paragraphs below it are older context).
- `AGENT-CONTEXT/memory/decisions.md` — D-001..D-867+, newest first; every architectural
  decision carries its why + the file evidence.
- `DOCUMENTATION/cloudstream-v2/1xx-*.md` — the per-round records (100-series = the current
  era; the latest = 124, round 144).
- `AGENT-CONTEXT/memory/changelog.md` — the one-line-per-change history (newest first).
- `planning/` (the WORKSPACE, outside git) — the per-round work logs, the master plans, the
  release staging scripts (bump_*.py, poll_*.py, docs_*.py — the Write-tool + `python -I`
  pattern).

## 5. The one-paragraph status (2026-11-13, post-round-144)

All major phases complete + the long device-feedback polish loop (the debug line at
**v1.1.86** carrying rounds 126-144; the professional line at **v1.1.7**). The current era's
themes: the player polish (gestures/zoom/cinema/Up Next/AniSkip), the .anikuta backup+restore
system with cross-app import/export, the real-minutes profile (watch flow / time DNA /
heatmap), the downloads↔extension-switch preservation, the embedded torrent engine
(CS-first, removable), the interactive setup wizard, the tracking-survival migration (v4),
and the open-by-mainId path (removed-from-library content opens; the episode scroll-to +
highlight). The known open items: the Aniyomi-extension torrent side (T3), torrent downloads
(T4), the new personalized Home, the wizard walkthrough — see `01-CURRENT-STATE.md` §open.
