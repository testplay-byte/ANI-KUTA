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
- Added CORE_RULES.md §19 (webpage work uses full-stack-dev agent). Updated repo README with live dashboard link.
- Downloaded old project from `ANI_KUTA_NEW` repo (sparse checkout of `ANIKUTA_PROJECT/ANIKUTA` folder) → `REFERENCES/old-kuta/ANIKUTA/` (36 active Gradle modules, 631 files, 451 Kotlin files). It's a reimagined Aniyomi (anime streaming app).
- Analyzed old project using 3 parallel sub-agents (core modules, data+feature modules, architecture+build) + main agent synthesis. Produced 10-file structured documentation (5326 lines) in `REFERENCES/old-kuta/DOCUMENTATION/`:
  - 01-overview, 02-architecture, 03-tech-stack, 04-core-modules (1658 lines), 05-data-modules, 06-feature-modules (995 lines), 07-data-flow, 08-features-breakdown, 09-rebuild-notes, README.
  - Key findings: 36 active modules (not 41), two-tier identity (ContentId/LocalId), pluggable Koin registries, gateway interfaces, Aniyomi extension compat, dual DI (Koin+Injekt), Voyager 1.0.1 nav, SQLDelight, MPV player, bleeding-edge versions (Kotlin 2.2.0, Compose BOM 2025.03.00, compileSdk 36).
  - Rebuild notes identify: carry-over patterns (ContentId, pluggable providers, single MPV instance), redesign targets (split WatchScreen 2386 LOC, AnimeDetailsVM 1013 LOC, SetupWizard 1840 LOC), drops (ads system unless wanted, Injekt spread), and 7 key Phase 1 decisions needing user input.
- Researched Aniyomi alternatives (sub-agent web search): Aniyomi confirmed unmaintained (lead dev left Apr 2026). Anikku (~944 stars, Komikku maintainer, most features, Aniyomi-ext-compat) recommended. Animiru (~824 stars, anime-only, clean) as lean alternative. AnymeX ruled out (Flutter cross-platform).
- Updated DESIGN.md to v2: combined old dark mode + new sidebar/charts/checklists design. Sidebar is shrinkable (240px↔64px), rounded-2xl, floating, translucent. Added charts (sparkline, donut, bars, area), checklists, Gantt, Kanban, phase timeline, workflow loop, decision cards.
- Dashboard v2 rebuilt by full-stack-dev sub-agent: 7 pages (Overview, Architecture, Decisions, Modules, Progress, Analytics, Planning), 19 components (12 new), 5 inline-SVG chart components (no external deps), decisions page with 9 architecture decisions showing pros (teal) / cons (rose) + recommendation badges. Build verified ✅.
- Manga reader confirmed SKIPPED by user. Notifications timing: Phase 3-4 (agent decision). Ads system: user wants it + tracking (details pending).
- **Phase 1 architecture research complete.** 4 parallel sub-agents researched the undecided decisions:
  - DB (10-db-research.md): SQLDelight 2.x (stay, NOT Room) — Animiru/Aniyomi/old project all use SQLDelight; partial indexes needed for identity system; Room can't do data-transforming migrations.
  - DI (11-di-research.md): Koin 4.x + Koin Annotations 2.x + Injekt (isolated to Aniyomi ext) — Injekt is Aniyomi-only; Koin is KMP-ready; Koin Annotations 2.x matches Hilt's compile-time safety; proven in old project.
  - Nav (12-nav-research.md): Jetpack Navigation 3 (Nav3, stable Nov 2025) — back stack is StateFlow<List<NavKey>> saved via rememberSaveable, old Voyager bug structurally impossible; type-safe @Serializable routes; modular api/impl split.
  - Ads (13-ads-research.md): Two modules (:core:ads + :core:activity-tracker) — AdFormat interface + JSON placement registry + per-interaction state + ActivityDetector + SQLDelight event-log.
- **Identity system redesigned** (D-032, in 14-architecture-recommendations.md §5): Graph-based model — ContentUID (app's UUID) + ExternalReference (links to external systems) with confidence levels (HIGH/MEDIUM/LOW) + user merge/split. Supports 5+ ecosystems, 3 content types, tracker-optional, cross-ecosystem source switching.
- **Multi-extension architecture** (D-031): ExtensionProvider abstraction, one impl per ecosystem (aniyomi, mangayomi, cloudstream, kotatsu, sora).
- **Multi-content-type architecture** (D-030): ContentType enum (VIDEO/IMAGE/TEXT) + per-type feature modules.
- Synthesis document: 14-architecture-recommendations.md (9 sections + updated tech stack + open questions).
- Dashboard decisions page updated with all 11 decisions + recommendations (full-stack-dev sub-agent). Build verified.
- Tech-stack knowledge file updated — supersedes D-009's tentative Hilt+Room decision.
- **User confirmed all Phase 1 decisions**: SQLDelight (D-035), Koin (D-034), Nav3 (D-036), Identity flexible/switchable (D-032), Ads deferred (D-033), Activity tracking 365-day/unlimited (D-039), Console logging (D-040), Backup/restore multi-app compat (D-041). Added CORE_RULES.md §20 (filtered console logging).
- Backup/restore research (15-backup-research.md): Aniyomi `.tachibk` protobuf (covers Aniyomi+Animiru+Anikku), Mangayomi `.backup` JSON-in-zip. Old project's `AniyomiBackupTranslator` reusable. Recommended: `BackupImporter` interface, two impls, Koin multi-binding. ANI-KUTA's own `.anikuta` format v2.
- **Phase 1 Architecture Plan written** (16-phase1-architecture-plan.md, ~790 lines): full module tree (43 modules), data flow, screen map (Nav3), identity system (ContentUID + ExternalReference + tracker bridge), backup/restore (with §7.5 merge semantics), multi-extension (Video/Image/Text sub-interfaces), multi-content-type, customizable UI, ad system (deferred, no premature abstraction), console logging (lambda-based Logger), Phase 2 scaffold (12 modules).
- **Sub-agent reviewed the plan** (Task 5-REVIEW): 4 critical + 10 important + 16 minor flaws found, ALL fixed. Critical: ExtensionProvider split (C1), shared screens api/impl (C2), WatchProgressStore layering (C3), backup merge semantics (C4). Important: Injekt rule (I1), tracker bridge (I2), AdGate removed (I4), Logger lambda (I5), BuildConfig (I6), Phase 2 trim (I7), core:ui merge (I8), core:network restored (I9), player/resolver boundary (I10).
