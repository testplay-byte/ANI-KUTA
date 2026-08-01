# MASTER — Read This First (Every Session)

> You are an AI agent working on **ANI-KUTA**, an Android app rebuild + companion web dashboard.
> This file is your entry point. Read it every session. It's short on purpose.

---

## What This Project Is

**ANI-KUTA** — an Android app, rebuilt from scratch from an older working version that lacked planning, documentation, and structure. Goals: modular, highly customizable UI (independent of backend), future-proof, well-documented.

- **GitHub**: `testplay-byte/ANI-KUTA`
- **App ID**: `com.confused.anikuta`
- **Tech**: Kotlin + Jetpack Compose (latest stable)
- **Builds**: GitHub Actions only — ARM `arm64-v8a` + `armeabi-v7a` only. Never local.

---

## Folder Layout

```
ANIKUTA-PROJECT/
├── AGENT-CONTEXT/        ← you are here (agent memory + rules, versioned in repo)
├── APP/ani-kuta/         ← Android app (Gradle + Kotlin + Compose)
├── DASHBOARD/webpage/    ← Next.js dashboard (→ GitHub Pages, planned)
└── .github/workflows/    ← CI: build APK (+ deploy dashboard, planned)
```

---

## What to Read (and When)

**Every session** (before any work):
1. `master.md` (this file)
2. `CORE_RULES.md` — non-negotiable rules
3. `memory/progress.md` — live status + blockers

**On demand**:
- Starting a task → `workflow.md` (the task loop)
- Touching code → `skills/ponytail.md` (simplicity ladder)
- Reviewing a plan → `skills/subagent-review.md`
- Architecture questions → `knowledge/architecture.md`
- Anything else → `navigation.md` (full file index)

---

## The Non-Negotiables

1. **Follow `CORE_RULES.md` at all times.** It wins over everything else.
2. **Workflow**: Understand → Verify → Implement → Verify → Move On. See `workflow.md`.
3. **No assumptions.** Unsure → ask the user. Never guess.
4. **No local APK builds.** GitHub Actions only.
5. **Keep docs updated** after every task — `progress.md`, `decisions.md`, `lessons-learned.md`.
6. **Send `ntfy.sh` notification** (topic `TASKISDONE`) when a task is done.
7. **Be honest.** Don't sugarcoat. Flag issues directly. Don't blindly agree.

---

## Current Status

- **Phase**: 0 done (CI green). Phase 1 (architecture) blocked — need old project reference from user.
- **Open blockers**: see `memory/progress.md` → "Blockers / Open Questions".

See `memory/progress.md` for live status. See `navigation.md` for the full file map.
