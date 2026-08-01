# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**
>
> **Role split:** This file = curated, mutable checklist (what's done / what's next / blockers).
> `memory/changelog.md` = immutable narrative of completed phases. Don't duplicate — link.

## Current Phase
**Phase 0 — done.** Phase 1 (architecture) pending — blocked on old project reference.

## What's Done
- [x] Restructured into `ANIKUTA-PROJECT/` (single root folder, versioned on GitHub).
- [x] AGENT-CONTEXT lives inside the repo (versioned) per user decision.
- [x] Android demo scaffolded under `APP/ani-kuta/`: Gradle + Kotlin 2.0.21 + Compose, app id `com.confused.anikuta`, abiFilters arm64-v8a + armeabi-v7a, minSdk 24 / targetSdk 35.
- [x] CI workflow `build-apk.yml`: wrapper validation, debug build, ABI verification, concurrency + timeout. **First push → CI green** ✅.
- [x] AGENT-CONTEXT overhauled: `CORE_RULES.md`, `workflow.md`, rewritten `master.md`, `memory/lessons-learned.md`, `knowledge/architecture.md`, `skills/ponytail.md`. Removed `planning/`, `questions/`, `rules/` (consolidated). Sub-agent reviewed.
- [x] Code folders restructured: `android/` → `APP/ani-kuta/`; `DASHBOARD/webpage/` created (Next.js, planned).

## What's Next
1. Push this restructure → verify CI still green with new paths.
2. Get user to share the old project (Q1/Q2) → begin Phase 1 (architecture planning).
3. Scaffold the Next.js dashboard + Pages deploy workflow (after Q10 scope confirmed).

## Blockers / Open Questions
⚠️ Q1/Q2/Q10 open — full detail in `memory/decisions.md` → "Pending Decisions".
- Q1: What does the app do? (need old project to analyze)
- Q2: Where is the old project? (repo link/path)
- Q10: Dashboard scope — confirm starter scope (module map + progress + decisions + flow diagram, read-only).

## Last Updated
- Session: web-3a43f99b-57b3-4d89-a26a-63737d005c8f
- By: main agent
- Note: AGENT-CONTEXT overhauled per user's core-rules spec; about to push + verify CI.
