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

### D-022 — Dashboard scope confirmed
- **What:** Dashboard shows: module map + progress + decisions + flow diagrams + various other things. Modular, filterable, sortable. Read-only to start.
- **Why:** User confirmation.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-023 — Dashboard design language = MEMORY OS
- **What:** `DASHBOARD/webpage/DESIGN.md` defines the MEMORY OS design system (cream/beige, accent colors, Inter + JetBrains Mono, rounded corners). Dark mode section added. Strictly followed on all pages.
- **Why:** User-provided, tested, reliable.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 0 (dashboard spec).

### D-024 — Old project goes in REFERENCES/old-kuta/
- **What:** When user shares the old project, it's downloaded to `REFERENCES/old-kuta/` (only the shared folder, not the whole repo). Analysis docs go in `REFERENCES/old-kuta/DOCUMENTATION/`.
- **Why:** User requirement — reference material for guiding the rebuild.
- **Status:** ✅ Confirmed by user. Folder structure created (empty).
- **Date:** Phase 0 (dashboard spec).

### D-025 — Naming consistency rule
- **What:** Consistent naming across the project: kebab-case files, PascalCase classes, D-NNN decisions, Q-NNN questions, Conventional Commits.
- **Why:** User requirement — easy searching.
- **Status:** ✅ Confirmed by user (CORE_RULES.md §17).
- **Date:** Phase 0.

### D-026 — Quality over speed
- **What:** Take as much time as needed for tasks. Don't skip workflow steps. Only deadline = push at session end.
- **Why:** User requirement.
- **Status:** ✅ Confirmed by user (CORE_RULES.md §18).
- **Date:** Phase 0.

## Pending Decisions (need user input)

### ✅ Q1/Q2 — Answered
Old project analyzed. See `REFERENCES/old-kuta/DOCUMENTATION/`.

### ✅ Q10 — Dashboard scope confirmed
Module map + progress + decisions + flow diagrams + analytics + planning. Read-only to start.

---

## Phase 1 Architecture Decisions (from dashboard review)

### D-027 — Aniyomi extension compatibility: KEEP (reference forks)
- **What:** Keep Aniyomi extension compatibility. Reference forks (Animiru as base), NOT Aniyomi directly. Plan for future multi-extension support: Mangayomi, sora, cloudstream, kotatsu.
- **Why:** Huge extension ecosystem. Aniyomi unmaintained but forks (Animiru, Anikku) active. Future-proofing for 5+ extension systems.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1 (architecture decisions).
- **Implications:** Database + backup/restore + identity system must support multiple extension sources from day one.

### D-028 — Base app: Animiru
- **What:** Use Animiru as the reference base (not Anikku, not Aniyomi). Build own system as project grows.
- **Why:** User decision — Animiru is anime-only (clean), active, Aniyomi-ext-compat. User doesn't expect to need base apps long-term.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-029 — Notifications: confirmed, episode detection first
- **What:** Build notifications system in Phase 3-4. BUT new-episode-detection system must be built first (not in old project).
- **Why:** User confirmed. Episode detection is a prerequisite for meaningful notifications.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-030 — Manga reader: modular, later (NOT skipped)
- **What:** Manga reader will be PROPERLY implemented later, in a modular way (separate module, combined in app). Future scope = 3 content types:
  - **Video** (anime, movies, series) — current focus
  - **Image** (manga) — later, modular
  - **Text** (novels) — later, modular
- **Why:** User clarification — not skipping, properly implementing later. Architecture must support multiple content types from the start.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.
- **Implications:** Module structure must accommodate content-type plugins. Data model must be content-type-aware. UI must be customizable per content type.

### D-031 — Multi-extension architecture (NEW requirement)
- **What:** The app must support multiple extension ecosystems: Aniyomi (current), Mangayomi, sora, cloudstream, kotatsu (future). Each has its own extension API. The architecture must abstract "extension source" so new ecosystems can be added without rewriting the app.
- **Why:** User requirement — future-proofing. Can't be tied to a single extension system.
- **Status:** ✅ Confirmed by user (implied by D-027 + multi-extension plan).
- **Date:** Phase 1.
- **Implications:** Need an `ExtensionProvider` abstraction. Each ecosystem (Aniyomi, Mangayomi, etc.) = one provider impl. Database stores which provider a source came from.

### D-032 — Identity system: flexible + switchable (backup/restore compat critical)
- **What:** The identity system must be **flexible and switchable** — not locked into one design. The graph-based model (ContentUID + ExternalReference) is the starting point, but it must be easy to evolve. **Backup/restore compat with other apps** (Aniyomi, Animiru, Mangayomi, etc.) is critical — users must be able to import data from those apps seamlessly.
- **Why:** User requirement — identity is complex; wants flexibility. Backup/restore from other apps is a key UX requirement (no bad experience for migrating users).
- **Status:** ✅ Confirmed by user (flexible + switchable + backup/restore compat). Design to be detailed in the Architecture Plan.
- **Date:** Phase 1.

### D-033 — Ads system: modular, customizable, multi-format (DEFERRED, designed)
- **What:** Two modules (`:core:ads` + `:core:activity-tracker`). AdFormat interface handles redirect/video/interstitial/**banner** + extensible. JSON placement registry. Per-interaction state. ActivityDetector. This is a **future plan** — not the focus now, but the architecture must accommodate it.
- **Why:** User requirement — ads wanted, done properly. Banner ads added to the format list per user request. Implementation deferred.
- **Status:** ✅ Confirmed (design accepted, implementation deferred). Architecture must leave room for it.
- **Date:** Phase 1.

### D-034 — Dependency injection: Koin 4.x + Koin Annotations 2.x + Injekt (isolated)
- **What:** Koin for host app. Injekt isolated to Aniyomi extension compat only. Do NOT use Hilt.
- **Why:** Injekt is Aniyomi-only. Koin is KMP-ready. Koin Annotations 2.x matches Hilt's compile-time safety. Koin's `List<T>` multi-binding is cleaner. Proven in old project.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-035 — Database: SQLDelight 2.x (stay, NOT Room)
- **What:** Stay on SQLDelight 2.x. Do NOT switch to Room.
- **Why:** Animiru + Aniyomi + old project all use SQLDelight. Partial unique indexes needed for identity system. Data-transforming migrations. Faster builds. KMP-ready.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-036 — Navigation: Jetpack Navigation 3 (Nav3)
- **What:** Use `androidx.navigation3` 1.0.0+. NOT Voyager. NOT Nav2.
- **Why:** Back-stack bug structurally impossible. Type-safe routes. Modular api/impl split. Dynamic tabs. Deep linking. Agent-friendly.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-039 — Activity tracking: 365-day default, unlimited option
- **What:** Activity tracking retains data for **365 days by default** (not 90). User can set to **unlimited** (their preference — they want to know their full watch history).
- **Why:** User requirement — wants full history access.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 1.

### D-040 — Filtered console logging (CORE_RULES.md §20)
- **What:** Proper filtered console logging for everything. Log levels (VERBOSE/DEBUG/INFO/WARN/ERROR). Per-module Logcat tags. Toggleable for performance (release builds off, debug on). Central `Logger` wrapper in `:core:common`.
- **Why:** User requirement — debugging, error tracking, understanding what happened.
- **Status:** ✅ Confirmed by user (CORE_RULES.md §20).
- **Date:** Phase 1.

### D-041 — Backup/restore: multi-app import compat
- **What:** The backup/restore system must support importing data from other apps: Aniyomi (`.tachibk`), Animiru, Mangayomi, and potentially others. Users migrating from those apps must have a seamless experience.
- **Why:** User requirement — no bad experience for migrating users.
- **Status:** ✅ Confirmed by user. Research needed on each app's backup format.
- **Date:** Phase 1.

### D-042 — Defer identity system + complex DB to Phase 4+
- **What:** The complex identity system (ContentUID + ExternalReference + matching engine + merge/split) is DEFERRED to Phase 4+. Basic DB tables (extensions, metadata cache, activity tracking, user customizations) are kept in Phase 3.
- **Why:** User wants to understand the data + other logic first before committing to the identity system. Needs proper care.
- **Status:** ✅ Confirmed by user (pending the contradiction flag in 18-phase3-plan.md).
- **Date:** Phase 3 (refined).

### D-043 — Extensions: use "Animiru" naming, NO default repos
- **What:** Extension modules use "Animiru" naming (not "Aniyomi"). The `eu.kanade.tachiyomi.*` package stays (binary compat). NO default extension repo URLs — user adds their own.
- **Why:** User requirement — Animiru is the base app. No default repos for user control.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3 (refined).

### D-044 — Player: copy from old project + separate MPV lib module
- **What:** Copy the old project's player (it's perfect). Wrap `aniyomi-mpv-lib` AAR as a separate `:core:player-mpv-lib` module so players can be swapped easily.
- **Why:** User likes the old player. Separate module for future-proofing.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3 (refined).

### D-045 — Internal tracking system is a KEY priority
- **What:** Build a full-fledged internal tracking system (`:core:activity-tracker`) that records everything the user does. This is the user's priority — comes BEFORE AniList tracker sync. Tracks: watch events, time of day, peak hours, ratings, searches, downloads, library changes.
- **Why:** User requirement — wants their own stats, beautiful results, backup-able.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3 (refined).

### D-046 — Metadata fetching split by content type
- **What:** Metadata fetching is split into multiple modules: `:core:metadata-anime` (now), `:core:metadata-movies` (future), `:core:metadata-manga` (future). Plus local metadata (user customizations: custom thumbnail, title, description per episode).
- **Why:** User requirement — future-proofing for multiple content types + user customization.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3 (refined).

### D-047 — Backup/restore: DEFER entirely
- **What:** Backup/restore is deferred to Phase 5+. Needs the identity system + all data tables first. Don't copy-paste from old project — rebuild properly.
- **Why:** User requirement — needs proper setup first.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3 (refined).

### D-048 — UI: buttery smooth animations + live verification
- **What:** UI must have buttery smooth animations (scrolling, transitions, button feedback). Data changes must be verified AND reflected live on screen (optimistic updates, Flow-based, no manual refresh).
- **Why:** User requirement — rich quality features.
- **Status:** ✅ Confirmed by user (CORE_RULES.md §22 + §23).
- **Date:** Phase 3 (refined).

### D-037 — Highly customizable UI (KEY requirement)
- **What:** The UI must be highly customizable. Theme engine, layout options, behavior toggles. Per-content-type customization. Future-proof.
- **Why:** User requirement — stated as key multiple times.
- **Status:** ✅ Confirmed (design needed in Phase 1).
- **Date:** Phase 1.

### D-038 — Modular, documented, agent-friendly codebase
- **What:** Codebase must be:
  - Modular (split into independent modules).
  - Well-documented (every module has README, every step documented as built).
  - Agent-friendly (new AI agents can jump into a specific part without full context, without breaking things).
- **Why:** User requirement — maintainability + future AI agent collaboration.
- **Status:** ✅ Confirmed (already in CORE_RULES, reinforced here).
- **Date:** Phase 1.
