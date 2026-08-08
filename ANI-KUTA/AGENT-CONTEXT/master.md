# MASTER — Project Orientation

> You are an AI agent working on **ANI-KUTA**, an Android app rebuild + companion web dashboard.
> This file orients you to the project. For the per-session quick-start checklist, read `SESSION.md` first.

---

## What This Project Is

**ANI-KUTA** — an Android anime streaming/downloading app, rebuilt from scratch from an older working version that lacked planning, documentation, and structure. Goals: modular, highly customizable UI (independent of backend), future-proof, well-documented.

- **GitHub**: `testplay-byte/ANI-KUTA`
- **App ID**: `com.confused.anikuta`
- **Tech**: Kotlin 2.2.0 + Jetpack Compose (BOM 2025.03.00) + MPV (aniyomi-mpv-lib 1.18.n) + SQLDelight 2.0.2 + Koin 4.2.2 (primary DI) + Injekt (secondary, extension binary compat) + Coil 3.0.4 + OkHttp 5.0.0-alpha.14
- **Builds**: GitHub Actions only — ARM `arm64-v8a` + `armeabi-v7a` only. Never local. Never install Android SDK/JDK locally (CORE_RULES §8).
- **SDK**: compileSdk 36 (Nav3 1.1.5 requires it), minSdk 24.

---

## Folder Layout

Per CORE_RULES §4, the repo root contains exactly ONE wrapper folder (`ANI-KUTA/`) with the four project zones inside. `.github/` stays at repo root (GitHub Actions constraint).

```
ANI-KUTA/                        ← repo root (git)
├── ANI-KUTA/                    ← SINGLE wrapper folder (all project zones inside)
│   ├── AGENT-CONTEXT/           ← you are here (agent memory + rules, versioned in repo)
│   ├── APP/ani-kuta/            ← Android app (38 Gradle modules, 247 .kt, 22 DB tables)
│   ├── DASHBOARD/webpage/       ← Next.js dashboard (→ GitHub Pages)
│   └── REFERENCES/              ← old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           ← CI: build APK + deploy dashboard
```

> ⚠️ Note: the repo root ALSO contains `skills/` (69 generic Z.ai sandbox skills) + a 234KB `worklog.md` — these are accidentally-committed sandbox artifacts that violate CORE_RULES §4. They're tracked on both `main` and `download-system-plan`. Cleanup deferred per user (not a current concern). Don't confuse repo-root `skills/` with the real `AGENT-CONTEXT/skills/` (3 project skills: ponytail, subagent-review).

---

## What to Read (and When)

**Every session** (before any work):
1. `SESSION.md` — 60-second quick-start (key rules + loop + end checklist)
2. `master.md` (this file) — project orientation
3. `CORE_RULES.md` — non-negotiable rules (**29 sections**)
4. `memory/progress.md` — live status + blockers (read the top "Current Phase" + "Known doc debt" sections first)

**On demand**:
- Starting a task → `workflow.md` (the task loop)
- Touching code → `skills/ponytail.md` (simplicity ladder)
- Reviewing a plan → `skills/subagent-review.md`
- Architecture questions → `knowledge/architecture.md` + `APP/ani-kuta/DOCUMENTATION/16-phase1-architecture-plan.md` ⚠️ (knowledge/architecture.md is STALE — describes 8 proposed modules, actual is 38; see discrepancy D005)
- Building UI → `APP/ani-kuta/DESIGN-LANGUAGE.md` (canonical, ~140 lines — NOT 1882; that was the old deleted version)
- Dashboard work → `knowledge/dashboard.md` + `DASHBOARD/webpage/DESIGN.md`
- Download system → `download-research/` (14 research docs + 5 reviews + REVIEW-D0) + `download-research/13-implementation-plan.md` (status table at top)
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

---

## Current Status

- **Branch**: `download-system-plan` (41 commits ahead of `main`, 0 behind). This is the active working branch.
- **Phase**: **Phase DL — Download System (substantially complete).** Phases 0-4, 5a/5b/5c (watch screen), Phase B (auto-link), Phase C (content identity + library), Phase D (data-management caching), and the download system (Phase DL.0-DL.8) are all done.
- **Modules**: 38 Gradle modules (1 `:app` + 21 `:core:*` + 1 `:data:extension` + 7 `:feature:*` with api/impl splits). 247 Kotlin files, 22 SQLDelight tables.
- **Dashboard URL**: `https://testplay-byte.github.io/ANI-KUTA/`
- **Open items** (see `memory/progress.md` → "What's Next"):
  - Download-system device testing.
  - Download system future-phase gaps (D-149, D-151): proxy-churn re-resolve wiring + 2 re-resolve bugs + outer retry loop + DownloadVideoPickerSheet cleanup. All DEFERRED per user — full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
  - Nav3: ✅ DECIDED (D-150) — keep hand-rolled nav. R7 accepted as known limitation. Nav3 1.1.5 dep unused (future cleanup option). Resolution in `APP/ani-kuta/DOCUMENTATION/12-nav-research.md`.
  - Doc-debt sweep (discrepancy D005 — knowledge/* + decisions.md numbering). Deferred.
  - Phase 5e: watch-progress persistence (currently in-memory, D-072).

See `memory/progress.md` for live status. See `navigation.md` for the full file map.
