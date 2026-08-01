# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**

## Current Phase
**Phase 0 — done.** Phase 1 (architecture) pending — blocked on old project reference. Dashboard demo pending — blocked on design.md upload.

## What's Done
- [x] Restructured into `ANIKUTA-PROJECT/` (single root folder, versioned on GitHub).
- [x] AGENT-CONTEXT lives inside the repo (versioned) per user decision.
- [x] Android demo scaffolded under `APP/ani-kuta/`: Gradle + Kotlin 2.0.21 + Compose, app id `com.confused.anikuta`, abiFilters arm64-v8a + armeabi-v7a, minSdk 24 / targetSdk 35. **CI green** ✅.
- [x] AGENT-CONTEXT overhauled: `CORE_RULES.md`, `workflow.md`, rewritten `master.md`, `memory/lessons-learned.md`, `knowledge/architecture.md`, `skills/ponytail.md`. Removed `planning/`, `questions/`, `rules/` folders. Sub-agent reviewed.
- [x] Code folders restructured: `android/` → `APP/ani-kuta/`; `DASHBOARD/webpage/` created.
- [x] Added CORE_RULES.md §13–§16: speech-to-text handling, sub-agent delegation scope, session-end GitHub backup, dashboard design language rule.
- [x] Created `SESSION.md` (per-session bootstrap file: key rules + loop + end checklist).
- [x] Created `knowledge/dashboard.md` (dashboard approach: purpose, content, deployment, update process, sub-agent rules).

## What's Next
1. ⏳ **User uploads `design.md`** → place at `DASHBOARD/webpage/DESIGN.md`, add dark-mode section.
2. Build demo Next.js webpage (cream design, dark mode toggle, tree + decisions view) using a webpage sub-agent.
3. Create GitHub Pages deploy workflow + activate Pages.
4. Get user to share old project (Q1/Q2) → begin Phase 1 (architecture planning).

## Blockers / Open Questions
⚠️ Full detail in `memory/decisions.md` → "Pending Decisions".
- **design.md** — user providing the dashboard design language file. Demo webpage paused. (D-017)
- Q1: What does the app do? (need old project to analyze)
- Q2: Where is the old project? (repo link/path)
- Q10: Dashboard scope — confirm starter scope (module map + progress + decisions + flow diagram, read-only).

## Last Updated
- Session: web-3a43f99b-57b3-4d89-a26a-63737d005c8f
- By: main agent
- Note: Dashboard rules + SESSION.md + dashboard knowledge doc added. Pushing to GitHub. Demo webpage paused pending design.md.
