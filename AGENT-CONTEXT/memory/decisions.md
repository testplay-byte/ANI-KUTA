# Decisions Log

> Record of key decisions. Each entry: what, why, when, status.

## Decisions

### D-001 — Build APKs via GitHub Actions only
- **What:** Never build APK locally. Always via GitHub Actions.
- **Why:** User requirement. Reproducible, no local Android toolchain needed.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-002 — Build only ARM64-v8a and armeabi-v7a
- **What:** Restrict ABIs to these two. No x86/x86_64.
- **Why:** User requirement. Matches target devices, keeps APK small.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-003 — AGENT-CONTEXT lives INSIDE the project repo (versioned)
- **What:** `AGENT-CONTEXT/` lives inside `ANIKUTA-PROJECT/` and is **versioned in the GitHub repo** so any future AI agent can clone and pick up immediately.
- **Why:** User requirement — the whole project folder (including agent context) is pushed to GitHub.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (updated).

### D-004 — Frontend/backend separation as core architecture
- **What:** UI layer and data layer are independent, communicating via contracts.
- **Why:** User wants highly customizable UI independent of backend.
- **Status:** ✅ Confirmed by user (stated as core idea).
- **Date:** Phase 0.

### D-005 — Modular app structure
- **What:** App logic split into independent modules, each with one responsibility + README.
- **Why:** User requirement for manageability and future-proofing.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-006 — Companion web dashboard (full Next.js project → GitHub Pages)
- **What:** A full Next.js project at `ANIKUTA-PROJECT/DASHBOARD/webpage/`. GitHub Actions builds and publishes it to **GitHub Pages** on every push.
- **Why:** User wants a visual representation of project logic, modules, progress, decisions — managed and kept up to date.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (path updated in D-011).

### D-007 — App ID = com.confused.anikuta
- **What:** Android applicationId / namespace = `com.confused.anikuta`.
- **Why:** User-chosen.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-008 — SDK levels: minSdk 24, targetSdk 35, compileSdk 35, JDK 17
- **What:** minSdk 24 (Android 7.0), targetSdk/compileSdk 35 (Android 15), JDK 17 for CI.
- **Why:** User-approved recommendations.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-009 — Tech stack: Kotlin + Compose + Hilt + Room + Retrofit, latest stable
- **What:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), AGP 8.7.2, Gradle 8.11.1. Hilt/Room/Retrofit to be added in Phase 1.
- **Why:** User-approved; use latest stable versions.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0.

### D-010 — Project folder structure: ANIKUTA-PROJECT/ (original)
- **What:** Single root folder `ANIKUTA-PROJECT/` containing `AGENT-CONTEXT/`, `android/`, `dashboard/`, `.github/workflows/`.
- **Why:** User requirement — one project folder holding everything.
- **Status:** ~~superseded by D-011~~.
- **Date:** Phase 0.

### D-011 — Restructured folder layout (current)
- **What:** `ANIKUTA-PROJECT/` now contains: `AGENT-CONTEXT/` (overhauled — CORE_RULES.md, workflow.md, no more planning/questions/rules folders), `APP/ani-kuta/` (Android, was `android/`), `DASHBOARD/webpage/` (Next.js, was `dashboard/`), `.github/workflows/`.
- **Why:** User requirement — better manageability, code separated from dashboard, AGENT-CONTEXT consolidated into core rules.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (restructure).

### D-012 — CORE_RULES.md as single rules source
- **What:** All former `rules/*.md` files consolidated into `AGENT-CONTEXT/CORE_RULES.md`. Removed `rules/` folder.
- **Why:** User requirement — one non-negotiable core-rules file, no fragmentation.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (restructure).

### D-013 — workflow.md as canonical task loop
- **What:** `workflow.md` (Understand→Verify→Implement→Verify→Move On) is THE task procedure. `master.md` operating loop and `CORE_RULES.md` dev-flow point to it.
- **Why:** Avoid three overlapping process descriptions.
- **Status:** ✅ Confirmed by user (implied by spec).
- **Date:** Phase 0 (restructure).

### D-014 — Self-learning system (lessons-learned.md)
- **What:** `memory/lessons-learned.md` logs one-line lessons when the user corrects the agent or the agent catches its own mistake. Recurring patterns promote to a rule in CORE_RULES.md.
- **Why:** User requirement — constant learning from mistakes, future iterations free from past issues.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (restructure).

### D-015 — ntfy.sh task notification
- **What:** After every task, send a notification via `curl ... https://ntfy.sh/TASKISDONE`. Topic is public — no secrets in message body.
- **Why:** User requirement.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (restructure).

### D-016 — Dashboard = visual documentation for the USER
- **What:** The web dashboard is documentation meant for the user (not the agent) to understand the system. Modular, filterable, shows modules/screens/plans/decisions/progress/architecture with trees, graphs, workflow diagrams.
- **Why:** User requirement.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-017 — Dashboard design language (DESIGN.md, strictly followed + dark mode)
- **What:** Design language defined in `DASHBOARD/webpage/DESIGN.md` (user-provided, tested). Strictly followed on all pages. Includes a dark mode toggle at the top. Cream tones, rounded corners. Flexible for future improvement.
- **Why:** User requirement.
- **Status:** 🚧 Pending — `design.md` not yet uploaded by user. Demo webpage paused.
- **Date:** Phase 0 (dashboard spec).

### D-018 — Sub-agents build the webpage; main agent owns AGENT-CONTEXT
- **What:** Webpage work delegated to sub-agents. Sub-agents work ONLY in `DASHBOARD/webpage/` — never touch `AGENT-CONTEXT/`. Main agent does all AGENT-CONTEXT updates.
- **Why:** User requirement — separation of concerns.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-019 — Session-end push to GitHub (environment is ephemeral)
- **What:** Every session ends with all changes committed + pushed to GitHub. The environment can clear randomly; GitHub is the source of truth.
- **Why:** User requirement — prevent work loss.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-020 — SESSION.md as the per-session bootstrap file
- **What:** A single file (`AGENT-CONTEXT/SESSION.md`) read at the start of every session. Contains: key rules reminder, the task loop, after-task update list, session-end checklist, current blockers.
- **Why:** User requirement — quick context for the agent each session.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-021 — User uses speech-to-text
- **What:** The user dictates messages via speech-to-text. Transcription errors may occur. Agent corrects obvious errors from context; if unclear, stops and asks.
- **Why:** User requirement — avoid moving in wrong direction on misheard instructions.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

## Pending Decisions (need user input)

### Q1 — What does the app actually do? ⏳
❓ What is ANI-KUTA? Main features and screens?
- User will share the older GitHub project for analysis. Waiting on link/path.

### Q2 — Where is the old project? ⏳
❓ Share repo link or path to the previous (working) version.
- Reference only, won't copy code.

### Q10 — Dashboard scope ⏳
❓ Confirm starter scope for the Next.js dashboard (deployed to GitHub Pages):
- ⭐ Recommended (read-only to start):
  - Module map (visual graph of all modules + dependencies)
  - Progress (read from `memory/progress.md`)
  - Decisions log (read from `memory/decisions.md`)
  - Blockers / open questions (read from this file)
  - Logic / data flow diagram
- Interactive editing later. Confirm or adjust.
