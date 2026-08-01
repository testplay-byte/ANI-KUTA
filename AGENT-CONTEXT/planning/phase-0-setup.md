# Phase 0 — Environment & Rules Setup

## Goal
Set up the workspace, agent context, rules, and documentation structure so all future work is organized and future-proof.

## Steps
1. [x] Inspect current environment (git, node, bun available; no adb).
2. [x] Clone `ANI-KUTA` repo (was empty — fresh start).
3. [x] Create `AGENT-CONTEXT/` folder structure.
4. [x] Write `master.md`, `navigation.md`.
5. [x] Write rules: communication, coding, build, architecture, git.
6. [x] Write memory: progress, decisions, changelog.
7. [x] Write knowledge: project-overview, tech-stack, module-map, ui-customization, old-vs-new.
8. [x] Write skills: planning-checklist, subagent-review.
9. [x] Write planning README + this phase file + stub phase-1.
10. [x] Create `questions/open-questions.md`.
11. [x] Add `ANI-KUTA/.gitignore` (Android-standard) and parent `.gitignore` entries for `AGENT-CONTEXT/`, `ANI-KUTA/`, `worklog.md`.
12. [x] Remove GitHub token from `ANI-KUTA/.git/config` remote URL; use credential store instead.
13. [x] Rewrite `build-apk.yml` with gradlew guard, PR trigger, ABI verification step; drop bogus `-PabiFilters` flag.
14. [x] Sub-agent review of this setup (flaws found + verified + fixed).
15. [ ] User answers open questions.
16. [ ] Commit initial scaffold to ANI-KUTA repo (README + .gitignore + workflow) and push.

## Assumptions
- AGENT-CONTEXT stays outside the ANI-KUTA Git repo (private agent memory).
- Kotlin + Jetpack Compose as the Android stack (to confirm).
- Next.js app doubles as the visualization dashboard.

## Risks
- Agent context could drift from actual repo state if not updated every session → mitigated by mandatory `progress.md` updates.
- Sub-agent may raise false-positive flaws → mitigated by verification step.

## Sub-Agent Review Notes
A Plan sub-agent reviewed the full setup (Task ID 9). It found 4 critical, 10 important, 6 minor flaws. All verified by the main agent. Fixes applied:
- 🔴 Token removed from git remote URL → credential store.
- 🔴 `AGENT-CONTEXT/`, `ANI-KUTA/`, `worklog.md` added to parent `.gitignore`.
- 🔴 Workflow guarded with `if: hashFiles('gradlew') != ''` so empty-repo pushes don't break CI.
- 🔴 Bogus `-PabiFilters` flag removed; ABI config delegated to `build.gradle.kts`; CI now verifies APK `lib/` folders.
- 🟡 `phase-1-architecture.md` stub created; module list reconciled (module-map.md canonical); "ARM 64 v7" terminology fixed; dashboard wording set to future tense; D-003 marked confirmed; `worklog.md` added to navigation + operating loop; PR trigger + release-signing TODO added.
- 🟢 targetSdk → 35; naming-convention note added; decisions.md deduped; .gitignore steps logged here; changelog/progress split defined.

## User Confirmations Needed
See `questions/open-questions.md`.
