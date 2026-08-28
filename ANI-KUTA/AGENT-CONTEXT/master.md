# MASTER — Project Orientation

> You are an AI agent working on **ANI-KUTA**, an Android app rebuild + companion web dashboard.
> This file orients you to the project. For the per-session quick-start checklist, read `SESSION.md` first.

---

## What This Project Is

**ANI-KUTA** — an Android anime streaming/downloading app, rebuilt from scratch from an older working version that lacked planning, documentation, and structure. Goals: modular, highly customizable UI (independent of backend), future-proof, well-documented.

- **GitHub**: `testplay-byte/ANI-KUTA`
- **App ID**: `com.confused.anikuta`
- **Tech**: Kotlin 2.2.0 + Jetpack Compose (explicit 1.10.4-line pins — BOM REMOVED D-322; material3 1.3.1) + MPV (aniyomi-mpv-lib 1.18.n) + SQLDelight 2.0.2 + Koin 4.2.2 (primary DI) + Injekt (secondary, extension binary compat) + Coil 3.0.4 + OkHttp 5.0.0-alpha.14
- **Builds**: GitHub Actions only — ARM `arm64-v8a` ONLY (D-251; test-only x86_64 emulator builds never ship). Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **SDK**: compileSdk 36, targetSdk 36, minSdk 24, JDK 17.

---

## Folder Layout

Per CORE_RULES §4, the repo root contains exactly ONE wrapper folder (`ANI-KUTA/`) with the four project zones inside. `.github/` stays at repo root (GitHub Actions constraint).

```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← SINGLE wrapper folder (all project zones inside)
│   ├── AGENT-CONTEXT/           ← you are here (agent memory + rules, versioned in repo)
│   ├── APP/ani-kuta/            ← Android app (50 Gradle modules, 408 .kt, 25 DB tables / 17 .sq files)
│   ├── DASHBOARD/webpage/       ← Next.js dashboard (14 pages → GitHub Pages)
│   └── REFERENCES/              ← old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           ← CI: build APK + deploy dashboard
```

> ⚠️ Note: the repo root ALSO contains `skills/` (69 generic Z.ai sandbox skills) + a large `worklog.md` — these are accidentally-committed sandbox artifacts that violate CORE_RULES §4. They're tracked on `main`. Cleanup deferred per user (not a current concern). Don't confuse repo-root `skills/` with the real `AGENT-CONTEXT/skills/` (3 project skills: ponytail, subagent-review, README).

---

## What to Read (and When)

**Every session** (before any work):
1. `SESSION.md` — 60-second quick-start (key rules + loop + end checklist)
2. `master.md` (this file) — project orientation
3. `CORE_RULES.md` — non-negotiable rules (**30 sections**)
4. `memory/progress.md` — live status + blockers + Deferred Concerns (read the top "Current Phase" + "Known doc debt" sections first)

**On demand**:
- Starting a task → `workflow.md` (the task loop)
- Touching code → `skills/ponytail.md` (simplicity ladder)
- Reviewing a plan → `skills/subagent-review.md`
- Architecture questions → `knowledge/architecture.md` + `knowledge/module-map.md` (both fully up to date)
- Building UI → `APP/ani-kuta/DESIGN-LANGUAGE.md` (canonical ~140 lines)
- Dashboard work → `knowledge/dashboard.md` + `DASHBOARD/webpage/DESIGN.md`
- Download system → `download-research/` (17 research docs + 5 reviews + REVIEW-D0 + FUTURE-PHASE-DL-GAPS) + `download-research/13-implementation-plan.md` (status table at top)
- **Testing on the sandbox emulator → `knowledge/emulator-testing.md`** (setup from scratch, the sandbox rules — double-fork detach, timeout-wrapped adb, input-text limits, 4GB memory cgroup — daily workflow commands, app testing tricks, troubleshooting). Read it BEFORE touching adb.
- Writing docs → `CORE_RULES.md` §21 (documentation folder organization — CRITICAL)
- Anything else → `navigation.md` (full file index)

---

## The Non-Negotiables

1. **Follow `CORE_RULES.md` at all times.** It wins over everything else.
2. **Workflow**: Understand → Verify → Implement → Verify → Move On. See `workflow.md`.
3. **No assumptions.** Unsure → ask the user. Never guess.
4. **No local APK builds.** GitHub Actions only. No local Android SDK/JDK (CORE_RULES §8).
5. **Keep docs updated** after every task — `progress.md`, `decisions.md`, `lessons-learned.md`, `changelog.md` (CORE_RULES §6, §26).
6. **Send `ntfy.sh` notification** (topic `TASKISDONE`) when a task is done (CORE_RULES §11).
7. **Be honest.** Don't sugarcoat. Flag issues directly. Don't blindly agree.
8. **Push to GitHub at session end** (CORE_RULES §15). Work not pushed can be lost — the sandbox is ephemeral.
9. **Debug builds = schema freedom** (CORE_RULES §30). No migration scripts needed. Old DBs get deleted + recreated.

---

## Current Status

- **Branch**: `test-feature/video-cache-new-download` — the long-lived active branch; every shipped version since v0.2.48 was built here. `main` is the old pre-v0.2.48 baseline; merging is USER-GATED (CORE_RULES §8).
- **Phase**: **ALL MAJOR PHASES COMPLETE** (Phases 0-4, 5a/5b/5c, B/C/D/DL/WP/HI/UP/SC/TR/NOTIF/CW, Debug Bubble, Profile UI v1-v6) — plus the ongoing device-feedback polish loop that has produced v0.2.48 → v0.2.62 (seasons module, episode-list integrity, pull-to-refresh, cover viewer + zoom, shared-element cover transitions, compose 1.10.4 runtime alignment… see `memory/decisions.md` D-240..D-326).
- **Release cadence**: each feedback batch ships as a tagged GitHub Release (in-app updater discovers it); the user device-tests every build on a real OnePlus phone.
- **Modules**: 50 Gradle modules (1 `:app` + 30 `:core:*` + 1 `:data:extension` + 18 `:feature:*` with api/impl splits). 25 SQLDelight tables across 17 `.sq` files. 408 Kotlin files. Decisions D-001..D-326. 180+ lessons learned.
- **Dashboard URL**: `https://testplay-byte.github.io/ANI-KUTA/`.
- **Current focus**: the device-feedback loop (fixes + polish per user report, version bump, release). v0.2.61 (compose compile==runtime alignment, D-322) verified crash-free on device; v0.2.62 (smoother shared-element morph + multi-season-only episode tags) awaiting push after a sandbox-wipe token loss (see SESSION.md).
- **Build sanity guard**: `:app` `checkDependencyAlignment` (D-322) fails any build whose packaged compose/lifecycle versions deviate from the pins in `gradle/libs.versions.toml`.
- **Deferred Concerns** (saved in `memory/progress.md` → "Deferred Concerns"):
  - `HttpDownloader.reResolver` orphaned (D-149) — built but not wired; `:app ReResolver` signatures mismatched.
  - Main-thread `runBlocking` in Downloads→Watch SAF scan (MainActivity.kt:428) — ANR risk.
  - `WatchKey` god-object (15 fields, 5 pre-serialized strings) — refactor to identifier-only (under analysis).
  - Dead/unwired download code: `DownloadVideoPickerSheet`, `setRetryingStatus` (D-151).
  - Nav backstack doesn't survive process death (R7, D-150 accepted limitation) — hybrid `rememberSaveable` fix possible.
  - 4 god-class .kt files >2000 lines (LibraryScreen 2471, DetailsScreen 2277, DetailsViewModel 2159, WatchScreen 2017) — refactor candidates.
  - DB migrations use `onOpen` instead of `.sqm` files — acceptable for debug (§30); needs `.sqm` before production.
  - AniList tracker is a placeholder (OAuth/sync stubs — not yet implemented, expected).
- **Open items** (see `memory/progress.md` → "What's Next"):
  - Download-system device testing + future-phase gaps (D-149, D-151) — DEFERRED per user; plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
  - Nav3: ✅ DECIDED (D-150) — keep hand-rolled nav. Nav3 fully REMOVED from all build files (not just "unused on classpath").
  - Doc-debt sweep: ✅ DONE this session (knowledge/*, master.md, SESSION.md, navigation.md, dashboard data all updated; code comments cleaned).

See `memory/progress.md` for live status. See `navigation.md` for the full file map.
