# Changelog (High-Level)

> One-line-per-change history, grouped by phase.
>
> **Role split:** This file = immutable narrative of completed work (append-only).
> `memory/progress.md` = live mutable checklist. Don't duplicate — link.

## Phase 0 — Environment & Rules Setup
- Initialized AGENT-CONTEXT folder structure (rules, memory, knowledge, skills, planning, questions).
- Wrote master.md, navigation.md, and 5 rule files.
- Cloned empty ANI-KUTA repo into workspace.
- Created `.github/workflows/build-apk.yml` with draft build.
- Sub-agent review (Task 9): 4 critical + 10 important + 6 minor flaws found + fixed (token hygiene, parent .gitignore, workflow guard, ABI verification, doc reconciliation).
- Restructured into single `ANIKUTA-PROJECT/` folder; AGENT-CONTEXT now versioned in the repo (D-003 updated, D-011).
- Scaffolded Android demo under `APP/ani-kuta/` (was `android/`): Kotlin 2.0.21 + Compose, app id `com.confused.anikuta`, abiFilters arm64-v8a + armeabi-v7a, minSdk 24 / targetSdk 35.
- Sub-agent review (Task 7) of Android scaffold: 1 critical + 11 important + 10 minor flaws found + fixed (icon color API level, mipmap fallback, core-ktx bump, kotlinOptions deprecation, dark mode, gitattributes, concurrency, timeout, Gradle bump).
- First push to GitHub → **CI green** ✅ (run 30720451661, artifact `anikuta-apk` 9.04 MB).
- AGENT-CONTEXT overhauled per user core-rules spec: created `CORE_RULES.md` (consolidated all rules), `workflow.md` (canonical task loop), `memory/lessons-learned.md` (self-learning), `knowledge/architecture.md` (design/concept), `skills/ponytail.md` (lazy dev, adapted with Kotlin/Next.js examples). Removed `planning/`, `questions/`, `rules/` folders. Sub-agent review (Task 3): 1 critical + 10 important + 9 minor flaws found + addressed (planning content migrated to workflow.md + changelog; open-questions folded into decisions.md Pending section; ntfy public-topic risk noted; lessons system made concrete; process docs de-overlapped).
- Code folders restructured: `android/` → `APP/ani-kuta/`; created `DASHBOARD/webpage/` (Next.js, planned). CI paths + .gitignore + all doc references updated.
- Added CORE_RULES.md §13–§16: speech-to-text handling (D-021), sub-agent delegation scope (D-018 — webpage sub-agents work only in `DASHBOARD/webpage/`), session-end GitHub backup (D-019 — environment is ephemeral), dashboard design language rule (D-017 — DESIGN.md strictly followed + dark mode).
- Created `SESSION.md`: per-session bootstrap file (key rules + task loop + after-task updates + session-end push checklist + current blockers).
- Created `knowledge/dashboard.md`: dashboard approach (purpose = visual docs for the user, content sections, deployment via GitHub Pages, update process, sub-agent rules).
- ⏳ Demo webpage + GitHub Pages activation PAUSED — user's `design.md` was not in the upload folder. Flagged to user; awaiting re-upload.
- User provided design.md content (MEMORY OS design system). Saved to `DASHBOARD/webpage/DESIGN.md` with dark mode section added (§2.3 Dark Mode Colors + §5.9 Dark Mode Toggle + §9 CSS variables for dark).
- Added CORE_RULES.md §17 (naming consistency) + §18 (take as much time as needed). Decisions D-022..D-026 recorded.
- Created `REFERENCES/old-kuta/DOCUMENTATION/` folder structure (empty, ready for old project download).
- Sub-agent (full-stack-developer) built demo Next.js 16 dashboard webpage: static export (basePath `/ANI-KUTA`), Tailwind 4 with MEMORY OS CSS variables, Inter + JetBrains Mono, dark mode toggle (no-flash), 5 pages (Overview, Modules tree, Decisions filterable, Progress, Architecture flow diagram), 7 reusable components, placeholder data (D-001..D-021, phases 0-6, 10 modules). Build verified ✅.
- Created `.github/workflows/deploy-dashboard.yml` (builds + deploys to GitHub Pages on push). Activated GitHub Pages via API (source = GitHub Actions). URL: `https://testplay-byte.github.io/ANI-KUTA/`.
