# MASTER — Project Orientation

> You are an AI agent working on **ANI-KUTA**, an Android app rebuild + companion web dashboard.
> This file orients you to the project. For the per-session quick-start checklist, read `SESSION.md` first.

---

## What This Project Is

**ANI-KUTA** — an Android anime streaming/downloading app, rebuilt from scratch from an older working version that lacked planning, documentation, and structure. Goals: modular, highly customizable UI (independent of backend), future-proof, well-documented.

- **GitHub**: `testplay-byte/ANI-KUTA`
- **App ID**: `com.confused.anikuta`
- **Tech**: Kotlin 2.2.0 + Jetpack Compose (explicit 1.10.4-line pins — BOM REMOVED D-322; material3 1.3.1) + MPV (aniyomi-mpv-lib 1.18.n) + SQLDelight 2.0.2 + Koin 4.2.2 (primary DI) + Injekt (secondary, extension binary compat) + Coil 3.0.4 + OkHttp 5.0.0-alpha.14
- **Builds**: GitHub Actions only — the push path builds the DEBUG APK, `arm64-v8a` ONLY (D-445); shipped releases are ALL-ABI + universal, release-signed in CI (D-423). Under D-738 nothing ships without the user's explicit order. Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **SDK**: compileSdk 36, targetSdk 36, minSdk 24, JDK 17.

---

## Folder Layout

Per CORE_RULES §4, the repo root contains exactly ONE wrapper folder (`ANI-KUTA/`) with the four project zones inside. `.github/` stays at repo root (GitHub Actions constraint).

```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← SINGLE wrapper folder (all project zones inside)
│   ├── AGENT-CONTEXT/           ← you are here (agent memory + rules, versioned in repo)
│   ├── APP/ani-kuta/            ← Android app (57 Gradle modules, 634 .kt, 25 DB tables / 17 .sq files)
│   ├── DASHBOARD/webpage/       ← Next.js dashboard (20 pages → GitHub Pages)
│   └── REFERENCES/              ← old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           ← CI: build APK + deploy dashboard
```

> Note: an OLD warning about repo-root `skills/` + `worklog.md` sandbox artifacts lived here — that pollution is GONE (verified Round 115: the repo root is exactly `.gitattributes`/`.github`/`.gitignore`/`ANI-KUTA/`/`NEW_AGENT_SETUP.md`/`README.md`/`USER-UPLOADS/`). Don't confuse repo-root anything with the real `AGENT-CONTEXT/skills/` (3 project skills: ponytail, subagent-review, README).

---

## What to Read (and When)

**Every session** (before any work):
1. `SESSION.md` — 60-second quick-start (key rules + loop + end checklist)
2. `master.md` (this file) — project orientation
3. `CORE_RULES.md` — non-negotiable rules (**31 sections**)
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
6. **Send `ntfy.sh` notification** when a task is done (CORE_RULES §11 default topic `THE-TASK-IS-DONE`; recent rounds run under the per-round override — SESSION.md's "THIS SESSION'S NOTIFICATION" line says which is current).
7. **Be honest.** Don't sugarcoat. Flag issues directly. Don't blindly agree.
8. **Push to GitHub at session end** (CORE_RULES §15). Work not pushed can be lost — the sandbox is ephemeral.
9. **Debug builds = schema freedom** (CORE_RULES §30). No migration scripts needed. Old DBs get deleted + recreated.

---

## Current Status

- **Branch**: `main` — the MAINLINE and the repo's default branch (renamed FROM `feature/round-57-cloudstream-downloads` in Round 118, D-743 — via the GitHub branch-rename API, history + default-branch status preserved). The 38 `release/*` branches were deleted by the user before Round 118 and stay deleted — future release branches get cut FRESH from `main` per D-442 (the rename restores that doctrine's literal form). `feature/test-controller-v5` stays dormant (kept by user order). Merges/main-branch operations stay USER-GATED (CORE_RULES §8).
- **Phase**: **ALL MAJOR PHASES COMPLETE** + a long device-feedback polish loop (v0.2.x → v1.1.72 debug / v1.1.5 professional) — and since **Round 115 (D-738): DEBUG BUILDS ONLY by default** — every round ends at CI green (the Build APK debug run's artifact); releases of either kind need the user's explicit order (the round-117 order exercised the recovery path, then the default resumed). The release routines live warm in `APP/ani-kuta/DOCUMENTATION/release/RELEASE-PLAYBOOK.md`.
- **Latest LIVE releases** (both round 117, 2026-10-02): **debug v1.1.72/10172** (testplay-byte — the in-app debug updater's line) + **professional v1.1.5/10105** (Confused-Creature-180/ANI-KUTA — the official re-host: 11 assets = 5 release-signed APKs + 5 ZIPs + SHA256SUMS, stable + latest, the website chip + the official README links refreshed). The mistaken v1.1.14 release + tag were DELETED (D-742); a device that installed it needs a one-time uninstall (versionCode downgrade is blocked). Mainline version stays 1.1.20/10120 (D-430: bumps ride release branches).
- **Modules**: 57 Gradle modules (1 `:app` + 33 `:core:*` + 2 `:data:*` + 21 `:feature:*` = 12 features: 9 api/impl splits + 3 singles). 25 SQLDelight tables across 17 `.sq` files. 634 Kotlin files in APP/ani-kuta/. Decisions D-001..D-743. Round records: cloudstream-v2 doc 100 (round 118) — next 101.
- **Dashboard URL**: `https://testplay-byte.github.io/ANI-KUTA/` (status facts refreshed Round 115; deep history stays representative — see D-565's disclosed debt; left as-is per the round-89 standing instruction until the user calls the focus).
- **Current focus**: the D-738 loop — the user's device-round feedback → implement on the mainline → CI green → hand over the artifact + checklist → docs → ntfy. NO release steps until the user orders.
- **Build sanity guard**: `:app` `checkDependencyAlignment` (D-322) fails any build whose packaged compose/lifecycle versions deviate from the pins in `gradle/libs.versions.toml`.
- **Deferred Concerns**: the round-54 historical table in `memory/progress.md` (kept for the record; many items long resolved — e.g. release signing, the updates engine, the notifications system, the AniList tracker) + the LIVE standing items (SESSION.md's Open Items + the round records' NOT-APPLIED sections). Highlights still open: `WatchKey` 17-field god-object; main-thread `runBlocking` in the Downloads→Watch SAF scan; the dead download code (`DownloadVideoPickerSheet`, `setRetryingStatus`); 4 god-class .kt files >2000 lines; DB migrations on `onOpen` (needs `.sqm` before production); the token-matching semantics living in five places.
- **Handoff**: `AGENT-CONTEXT/SESSION.md` is the live orientation (refreshed Round 118); `HANDOFF-ROUND-116.md` (+ its round-117 addendum) and `HANDOFF-ROUND-115.md` are the historical handoffs.

See `memory/progress.md` for live status. See `navigation.md` for the full file map.
