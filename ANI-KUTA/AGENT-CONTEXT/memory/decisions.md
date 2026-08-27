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

### D-008 — SDK levels: minSdk 24, targetSdk 36, compileSdk 36, JDK 17
- **What:** minSdk 24 (Android 7.0), targetSdk/compileSdk 36 (Android 16 — kept at 36 for Compose BOM 2025.03.00 + future-proofing; was originally bumped for Nav3 but Nav3 was removed in D-150 — SDK stays at 36 because reverting provides no benefit), JDK 17 for CI.
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

### D-049 — Video caching for instant resume
- **What:** Cache ~1 minute before + 1 minute after the user's last watch position in local storage. When the user resumes a previously-watched video, playback starts instantly from the cached segment (no buffering). The actual video stream loads in the background while the cached segment plays.
- **Why:** User requirement — buttery smooth experience, no buffering on resume.
- **Status:** ✅ Confirmed by user. Planned for Phase 3c (document now, implement in player module).
- **Date:** Phase 3c.
- **Implementation:** MPV supports cache via `stream-cache-dir` + `cache-secs` properties. Configure cache to cover the resume position. The cache persists on disk between sessions.

### D-050 — Fix player companion hack
- **What:** The old project's `AnikutaMPVView` uses a companion `lateinit var playerPreferences` because XML-inflated views can't use Koin constructor injection. We will fix this by using a different approach: either Koin's `KoinComponent` interface, or passing preferences via a factory method, or using Compose's `AndroidView` with a programmatic view creation (no XML inflation).
- **Why:** User requirement — "properly handle the controls and all other issues too, like using Companion mode."
- **Status:** ✅ Confirmed by user. Will be fixed during player port.
- **Date:** Phase 3c.

### D-051 — FFmpeg dependency
- **What:** Use `com.github.jmir1:ffmpeg-kit:1.18` (the old project's proven dependency). Research found no better alternative for Android — FFmpegKit is the standard, the jmir1 fork maintains it after the original was deprecated. libmpv.so dynamically links against FFmpeg, so it's required.
- **Why:** User asked to research alternatives. No better option found. APK size increase (~30-50MB) is acceptable for a media app.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 3c.

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

### D-052 — Bottom-up sheets cap at 70% of device screen height
- **What:** All `ModalBottomSheet` (and equivalent bottom-up menus) cap their content at **70% of the device's full screen height**. The cap applies to the WHOLE sheet content (header + tabs + body), not just the inner scrollable list. Use `LocalConfiguration.current.screenHeightDp.dp * 0.70f` as the max, applied via `Modifier.heightIn(max = ...)` on the sheet's root Column. The inner scrollable content (LazyColumn / verticalScroll Column) is then constrained by the parent and scrolls when content exceeds the cap, wraps when short.
- **Why:** User spec — sheets were taking too much vertical space (the "Display & Badges" tab exceeded the limit because `heightIn` was on the inner list only, so sheet = list(75%) + header + tabs ≈ 85%). 70% leaves room for the content behind the sheet and feels balanced. Detecting the real device height (not a hardcoded dp value) adapts per-device.
- **Status:** ✅ Confirmed by user.
- **Date:** Phase 4 (session web-3a43f99b).
- **Applied to:** Library CustomizeSheet, Search FilterSheet. Rule for all future sheets.

### D-053 — Accent palette system (10 presets + CUSTOM, live apply)
- **What:** The app supports 10 accent presets (Lime, Coral, Rose, Amber, Red, Teal, Blue, Cyan, Violet, Emerald) + 1 CUSTOM. Selecting a preset overrides the theme's `primary` / `primaryContainer` / `onPrimary` / `onPrimaryContainer` colors (light + dark). The background/surface ramp stays fixed (warm darks / warm lights) so ONLY the accent changes — preserving the proven aesthetic (D-037 customizable UI). Container colors are DERIVED from the seed via `lerp` against the fixed surface/text colors (no per-color hand-tuning).
- **Architecture:** `AccentPreset` enum + `AccentColors` (derivation) live in `:core:designsystem/theme` (theme concern, no dependency on preferences). `AnikutaTheme` takes an `accentSeed: Color` param. `ThemePreferences` (in :app) stores `accentPreset` (name) + `customAccentColor` (ARGB int) and exposes `resolveAccentSeed(): Color`. `MainActivity` reads prefs and passes the seed to `AnikutaTheme` → live recomposition on change (CORE_RULES §23).
- **CUSTOM:** applies the stored custom color (defaults to Lime). The color-picker UI is Phase 5f — selection + storage + live-apply work now, only the editor is deferred.
- **Why:** User disappointed palettes were static placeholders ("none of the palettes get applied"). This makes them functional while keeping the system clean (derivation, not 80 hand-tuned colors).
- **Status:** ✅ Implemented (Phase 4, session web-3a43f99b). Color picker → Phase 5f.
- **Date:** Phase 4 (session web-3a43f99b).

### D-054 — Phase 5 re-ordered: Extensions → Details → Watch → Identity (functional first)
- **What:** Phase 5 sub-phases re-ordered to: **5a** Extension Management → **5b** Details Page Overhaul → **5c** Watch Screen → **5d** Identity System → **5e** History/Updates → **5f** Backup/Restore + Color Picker. The prior plan put Identity first (5a) — that was rejected.
- **Why:** User directive — "first make the app functional so we can test things, then move to the deeper parts." The watch flow only needs a *minimal* source link (one DB row), NOT the full ContentUID graph. Identity is a *refinement* for portability (backup) + auto-matching, not a *prerequisite* for playback. Putting it first blocked the user from testing anything until the entire identity system was built — wrong priority.
- **Key insight:** 5a–5c deliver a **watchable app** (install extension → browse → details → play). 5d–5f are invisible refinements (identity, history, backup). The minimal `source_link` row from 5b is mechanically migrated to ContentUID + ExternalReference in 5d — no data loss, no UX change.
- **Status:** ✅ Confirmed by user. Plan in `APP/ani-kuta/DOCUMENTATION/19-phase5-plan.md` (rewritten).
- **Date:** Phase 4 (session web-3a43f99b, second pass).
- **Open questions:** Q-056 (source browser placement), Q-057 (episode sort), Q-058 (default video quality), Q-059 (updates placement), Q-060 (auto-match scope), Q-061 (backup frequency). See plan §9.

### D-055 — Source browsing merged into Search page (NO separate tab)
- **What:** There is NO "Sources" tab and NO separate source-browse screen. Source browsing is **smoothly merged into the Search page** itself — like the old project. The user picks a source from within Search, then browses that source's catalog (popular/latest) inline. Extension *management* (install/uninstall/trust/repos) lives in **Settings → Extensions** (clearly separated from browsing).
- **Why:** User directive — "We are different. The source browse will be smoothly merged into the search page itself, like how it is in the old page project. The extension system will be manageable in the settings." Keeps the bottom nav at 4 tabs. Settings are where you expect them.
- **Status:** ✅ Confirmed by user (Q-056 answer).
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-056 — Episode list default sort: descending (newest first)
- **What:** The episode list defaults to **descending** order (newest episode first). Future: add a user toggle for ascending, plus optional grouping (1-100, 101-200, etc.).
- **Why:** User directive — "for the current time being, descending order is okay but later on we can decide on giving the user customization options."
- **Status:** ✅ Confirmed by user (Q-057 answer). Future enhancement: ascending toggle + episode grouping.
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-057 — Default video quality: ask each time (resolver sheet)
- **What:** When the user taps an episode, the **VideoResolver bottom sheet** always appears showing available servers/qualities. The user picks one each time — no auto-selection, no "always use this quality" default. This is the old project's method.
- **Why:** User directive — "for the current time being, we will use the old method, which is asking each time. That is the most appropriate method for us." Future: may add a "remember choice" option.
- **Status:** ✅ Confirmed by user (Q-058 answer).
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-058 — Updates in a dedicated area in the More section
- **What:** The Updates screen lives in the **More section** (like the old project). It is NOT a 5th bottom-nav tab and NOT a section in Library. The user accesses it via More → Updates.
- **Why:** User directive — "the updates are going to be in a new dedicated area in the More section, just like how it is in the old application."
- **Status:** ✅ Confirmed by user (Q-059 answer). Not a Phase 5a–5c concern (Updates is Phase 5e), but the placement is decided now.
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-059 — Auto-match scope: user-enabled (trusted) sources only
- **What:** The auto-matching engine (Phase 5d) searches ONLY **trusted, user-enabled sources** — never all installed sources blindly. The user manages which sources are enabled.
- **Why:** User directive — "only user-enabled sources will be used and searched for and those sources are going to be the trusted ones. The user can easily manage them." Control + performance.
- **Status:** ✅ Confirmed by user (Q-060 answer). Applies to Phase 5d (identity), but the rule is set now.
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-060 — Backup frequency default: daily (options: 6h/12h/daily/weekly)
- **What:** Backup auto-frequency defaults to **daily**. The user can manually select: every 6 hours, every 12 hours, every day, or every week.
- **Why:** User directive — "by default it will be daily but the user can select it manually to be every 6 hours, every 12 hours, every day, or every week."
- **Status:** ✅ Confirmed by user (Q-061 answer). Applies to Phase 5f (backup), but the rule is set now.
- **Date:** Phase 5 (session web-3a43f99b, third pass).

### D-061 — Video playback root cause: empty `initOptions()` (ported full init from old project)
- **What:** `AnikutaMPVView.initOptions()` was EMPTY in the new project — it never called `setVo("gpu")`, so MPV had no video output configured (audio played, screen stayed black). Ported the full `initOptions()` from the old project, which sets (via `setOptionString` unless noted): `setVo("gpu")` (or `"gpu-next"` if `gpuNext` pref), `profile=fast`, `hwdec=auto` (NOT `auto-copy` — see lesson), `msg-level=all=warn`, `keep-open=true`, `input-default-bindings=true`, `ytdl=no`, `tls-verify=yes`, `tls-ca-file=<cacert.pem path>`, `demuxer-max-bytes=256MB` (128MB on old Android), `demuxer-max-back-bytes=64MB`, `vd-lavc-film-grain=cpu` (workaround for mpv issue #14651), `speed`, `alang`, `volume-max`, plus all 12 subtitle prefs via `applySubtitlePreferencesInit()`. Also moved `sub-ass-force-margins` / `sub-use-margins` from runtime `setPropertyString` to init-time `setOptionString` (in `PlayerInitializer.initialize()` BEFORE `view.initialize()`) — required for the render pipeline.
- **Why:** Root-cause investigation (sub-agents RESEARCH-OLD-PLAYER + RESEARCH-PADDING-BUG) found that the new project's `initOptions()` was a no-op stub ("No custom init options needed"). Without an explicit video output (`setVo`), MPV on Android defaults to a VO that doesn't render to the SurfaceView.
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, eleventh pass). Awaiting device verification.
- **Date:** Phase 5c (session web-3a43f99b, eleventh pass).
- **Related lessons:** "Empty initOptions() → no video", "hwdec=auto-copy fails on some devices".

### D-062 — Top padding bug root cause: `setDecorFitsSystemWindows(true)` in minimized mode
- **What:** `WatchScreen`'s `DisposableEffect(playerMode)` called `WindowCompat.setDecorFitsSystemWindows(window, true)` in the minimized branch, which conflicts with the app-wide `enableEdgeToEdge()` call in `MainActivity.onCreate` (that sets it to `false`). The block's `onDispose { }` was EMPTY, so when the user navigated back from the player (typically from minimized mode, since `BackHandler` only intercepts in fullscreen), `setDecorFitsSystemWindows=true` persisted on the Activity. Subsequent screens (Browse/Library/Search/More) rendered with the framework auto-padding the content view for the status bar AND Compose `statusBarsPadding()` also applying the status-bar inset → DOUBLE PADDING → headings pushed down. Restart fixed it because `onCreate` re-runs `enableEdgeToEdge()`.
- **Fix:** (a) Removed `setDecorFitsSystemWindows(window, true)` from the minimized branch — leave `setDecorFitsSystemWindows(false)` set only in the fullscreen branch for defensive clarity (matches the old project's pattern, which NEVER sets it `true` in minimized). (b) Populated the empty `onDispose { }` to restore the app-wide edge-to-edge defaults on screen exit: `setDecorFitsSystemWindows(window, false)`, `controller.show(systemBars())`, `requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED`.
- **Why:** The old project never calls `setDecorFitsSystemWindows(true)` in minimized mode — it only calls `controller.show(systemBars())` and lets `setDecorFitsSystemWindows` stay at its global `false` value. The new project diverged from this proven pattern by adding the spurious `true` call. Root cause traced by sub-agent RESEARCH-PADDING-BUG.
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, eleventh pass). Awaiting device verification.
- **Date:** Phase 5c (session web-3a43f99b, eleventh pass).
- **Related lessons:** "setDecorFitsSystemWindows(true) conflicts with enableEdgeToEdge()", "Empty onDispose leaks window state".

### D-063 — ResolvedVideosRegistry: in-memory singleton to pass servers Details → Watch
- **What:** Created `ResolvedVideosRegistry` — an in-memory singleton (object, held in a `:core:video-resolver` or shared module) that stores the structured resolver result (`List<ResolverServer>`) keyed by episode URL (or a request ID). `DetailsViewModel.resolveEpisode` now also calls `resolveStructured` and stores the result in the registry. `WatchScreen` reads from the registry by key to populate `QualitySheet`. No serialization through `WatchKey` (Nav3) is needed.
- **Why:** The new project's `WatchKey` is `Serializable` with primitive fields only — it can't carry `List<ResolverServer>` / `ResolverAudioVersion` / `ResolverVideo` (which contain `List<Video>` with non-serializable headers) through Nav3. The old project passed rich `WatchRequest` data via a different mechanism. Rather than fight Nav3 serialization (or build a shared-scope ViewModel), an in-memory registry is the simplest solution: Details resolves → stores → Watch reads by key. Lifecycle: cleared on Watch exit, repopulated on next resolve. Trade-off: registry is lost on process death (acceptable — user re-resolves).
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, eleventh pass).
- **Date:** Phase 5c (session web-3a43f99b, eleventh pass).

### D-064 — SubtitleSettingsSheet uses non-reactive PlayerPreferences + local `mutableStateOf`
- **What:** `SubtitleSettingsSheet` reads/writes the 12 subtitle prefs via the existing `PlayerPreferences` (a thin wrapper around a `SharedPreferences`-backed store) — NOT via a reactive `Preference<T>` Flow API. Each row holds its own `mutableStateOf(initialValue)` seeded from `PlayerPreferences.getXxx()`. On user edit, the row updates local state (instant UI feedback) + writes through to `PlayerPreferences.setXxx(...)` + calls `AnikutaMPVView.applySubtitlePreferences()` to push the change to MPV at runtime (via `setPropertyInt` / `setPropertyDouble` for numerics).
- **Why:** The old project uses a reactive `Preference<T>` API (`PreferenceStore.getString(...)`, etc.) that would require porting the whole `PreferenceStore` + `Preference<T>` abstraction + its Compose integration (`.collectAsState()`) to the new project. That's a large refactor for marginal benefit — the subtitle settings sheet is the only consumer right now. Using local `mutableStateOf` + write-through is simpler, equally responsive (the state is local, so recomposition is instant), and avoids coupling the new project to the old `Preference<T>` API. The trade-off: changes from OUTSIDE the sheet (e.g. another screen editing the same pref) won't auto-propagate — but no other screen edits subtitle prefs, so this is moot.
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, eleventh pass).
- **Date:** Phase 5c (session web-3a43f99b, eleventh pass).
- **Related lesson:** "setPropertyString doesn't reliably update numeric MPV properties" — use `setPropertyInt`/`setPropertyDouble` for numerics.

### D-065 — Animiru repo cloned as read-only reference (no code copied into the app)
- **What:** The Animiru GitHub repo (`https://github.com/Quickdesh/Animiru.git`, depth=1) was cloned into `REFERENCES/animiru/ANIMIRU/` and a dedicated analysis subagent produced 11 documentation files (8,101 lines total) in `REFERENCES/animiru/documentation/` covering: overview, player architecture, MPV initialization, player controls, player sheets, video resolution, subtitle management, extension system, player settings, key takeaways. NO code from Animiru was copied into `APP/ani-kuta/` — the repo is strictly a read-only reference.
- **Why:** Animiru is a modern, actively-maintained Tachiyomi/Aniyomi fork with extensive player functionality (MPVPlayer, sheet/panel system, PiP, gesture handler). Having it locally + documented gives the agent a third reference (alongside the old ANIKUTA project + Aniyomi upstream) for player architecture decisions. The doc set captures patterns to port (three-bucket state, sheet mutual exclusivity, IntegerPickerDialog) and anti-patterns to avoid (2928-line God Object PlayerViewModel, `vf` option collision, `EarlyReturnException` control flow, `runBlocking` for extension loading).
- **Status:** ✅ Done (Phase 5c, session web-3a43f99b). Repo + docs committed.
- **Date:** Phase 5c (session web-3a43f99b).
- **Sub-agents:** ANIMIRU-CLONE (repo clone), ANIMIRU-ANALYSIS (documentation).
- **Note:** The Animiru repo is GPL-licensed — code must NOT be copied verbatim into ANI-KUTA (different license strategy). Use only as architectural reference; port patterns, not code.

### D-066 — Double-resolve is FORBIDDEN (single `resolve()` + `buildServers()` derivation)
- **What:** Never call `getHosterList` TWICE for the same episode. AniKotoS creates a new local HTTP proxy on each call — the second call kills the first call's proxy URLs. The new `VideoResolver.resolve()` returns `ResolverState.Success(videos, rawVideos)` — both the flat list AND the raw `List<Video>`. `buildServers(rawVideos)` is a pure function (no source calls) that derives the structured Server → AudioVersion → Video hierarchy.
- **Why:** Previous `VideoResolver` had two methods (`resolve()` + `resolveStructured()`), each calling `getHosterList` internally. `DetailsViewModel` called both → second call killed the first call's proxy → user picked a dead URL → MPV `loadfile` failed → "loading failed".
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, twelfth pass). CI green.
- **Date:** Phase 5c (session web-3a43f99b, twelfth pass).
- **Related lesson:** "DOUBLE-RESOLVE BUG — Never call getHosterList twice for the same episode".

### D-067 — Error overlay is inline on player surface with Close button (not popup)
- **What:** `PlayerErrorOverlay` renders directly on the player surface (full-screen dark overlay with red icon, "Playback Error" title, error message, Close + Retry buttons) — NOT a popup, NOT a force-open of the QualitySheet. The Close button dismisses the error (clears `errorMessage`); Retry re-sends `loadfile` with the current URL.
- **Why:** User spec: "Don't show up a pop-up." The old project's error overlay was also inline on the player surface. Force-opening the QualitySheet on error was confusing (user didn't ask for it).
- **Status:** ✅ Implemented (Phase 5c, session web-3a43f99b, twelfth pass). Reworked 3 times across commits 70d9c59 + be059e5 + 8100d91.
- **Date:** Phase 5c (session web-3a43f99b, twelfth pass).

### D-068 — Stuck-loading fix: `setSwitchingError()` + 30s switching-timeout watchdog
- **What:** Added `PlayerStateHolder.setSwitchingError(message)` — a new error method that ALWAYS shows the error (never suppressed by `isSwitching`) AND clears the switching flag in one call. This fixes the regression where `setSwitching(true)` + `updateError()` suppression (intended to ignore the old file's `END_FILE` during a switch) left the player in a perpetual loading spinner with no error and no recovery when a switch ACTUALLY failed (no videos, resolve error, exception, server 403, dead proxy). Also added a 30s `LaunchedEffect(isSwitching)` watchdog in `WatchScreen` that calls `setSwitchingError("Video failed to load (timeout)")` if `isSwitching` stays true for 30s — catches cases where `efEvent` is suppressed AND `FILE_LOADED` never fires (server hung, network error MPV doesn't surface).
- **Why:** The old project had a 3-layer error handling system: (1) `onFileEnded` for MPV END_FILE errors, (2) 30s switching timeout watchdog, (3) 15s fatal-error "video stuck after load" watchdog. The new project only had layer 1 + the switching-suppression logic, but NOT the timeout watchdog — so any suppressed error = stuck forever. Root cause: `updateError()` suppressed ALL errors during switching, including real failures.
- **Usage:** `setSwitchingError` is used in ALL explicit failure paths: retry catch, quality switch catch, episode switch (no videos / resolve error / catch). `updateError` (suppressed during switching) is ONLY for `efEvent` (MPV's END_FILE) — which is the old file ending during a switch, correctly suppressed.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). Awaiting device verification.
- **Date:** Phase 5c (session web-f53f0459).
- **Related:** CORE_RULES §5 "Player lifecycle scaffolding is NOT boilerplate".

### D-069 — Episode-switch state hoisted into `PlayerStateHolder` (WatchKey stays immutable)
- **What:** Added 4 new `StateFlow`s to `PlayerStateHolder`: `currentEpisodeUrl`, `currentEpisodeNumber`, `currentEpisodeTitle`, `currentResolvedVideosKey`. Seeded from `WatchKey` on init via `seedEpisodeState(...)`, updated on switch via `updateCurrentEpisode(...)`. The episode list highlight, "Currently playing episode N" card, and QualitySheet servers now read from these state-holder flows (reactive to switches) instead of the immutable `WatchKey` fields (which never changed after a switch).
- **Why:** `WatchKey` is a `@Serializable data class : NavKey` — it's immutable per the Nav3 contract. After an episode switch, `watchKey.episodeUrl` / `episodeNumber` / `resolvedVideosKey` still held the OLD episode's values → the episode list highlight stayed on the old row, the "now playing" card showed the old episode, and the QualitySheet showed the old episode's servers. Hoisting the "current episode" state into the state holder (the old project's pattern — it had `currentEpisodeUrl` / `currentEpisodeNumber` on `PlayerStateHolder`) fixes all three issues without mutating `WatchKey`.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-070 — External subtitle/audio track loading re-added to `PlayerObserver`
- **What:** Re-added `pendingSubtitleTracks`, `pendingAudioTracks`, and `trackHeaders` fields to `PlayerObserver`. On `FILE_LOADED`, `loadExternalTracks()` sends `sub-add` / `audio-add` MPV commands (on `Dispatchers.IO` because each triggers an HTTPS download), then waits 300ms before calling `loadTracksFromMpv()` so MPV has time to register the tracks. The host (`WatchScreen`) sets these fields on the observer before every `loadfile`: in `initMpv` (from the initial picked video), in `onQualitySelected`, and in `onEpisodeSwitch`.
- **Why:** The `loadExternalTracks()` method was deleted entirely in a previous session, causing a regression: extensions providing external subtitle/audio URLs (AniKotoS, some Crunchyroll sources) were silently dropped — only muxed tracks worked. `ResolverVideo.subtitleTracks` / `audioTracks` were populated but never consumed. Also fixes the headers-override bug: `trackHeaders` is now set on EVERY video change (quality + episode switch), not just quality switch.
- **Ported from:** Old project's `PlayerObserver` + `WatchScreen` external-track loading logic (sub-add AFTER FILE_LOADED on Dispatchers.IO).
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).
- **Related lesson:** "sub-add MUST be sent AFTER FILE_LOADED — sending before causes MPV to silently drop the track".

### D-071 — `SubtitleTrackFormatter` ported with ISO 639 → English name mapping
- **What:** Created `core/player/subtitles/SubtitleTrackFormatter.kt` — a standalone object that formats MPV track-list display names. Ported from the old project's `SubtitleTrackFormatter` with one improvement: an ISO 639-2/B + ISO 639-1 → English name map (eng → English, jpn → Japanese, etc. — 50+ languages). `AnikutaMPVView.loadTracks()` now uses this formatter instead of the old basic `buildDisplayName()` which showed raw codes ("ENG", "JPN").
- **Rules (mirrors old project + ISO improvement):** A title that looks like an ugly filename (`.vtt`/`.srt`/`.ass`/`.ssa` suffix, or >20-char hash with no spaces) is discarded. When both a real title and a language are available: `"Title (Language)"`. When only one: that one. When neither: `"Track N"`.
- **Why:** The old project showed raw language codes. The new project's `buildDisplayName` was even worse — just `lang.uppercase()`. Users see "ENG" instead of "English". The ISO mapping is an improvement over the old project.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-072 — Capture-only `WatchProgressStore` (in-memory, no restore yet)
- **What:** Created `InMemoryWatchProgressStore` (implements `WatchProgressStore`) — saves watch progress to an in-memory `ConcurrentHashMap`. Registered in Koin via `watchProgressModule`. `WatchScreen` saves progress every 10s + on dispose. Progress is NOT restored on next playback yet (restore is Phase 5e when the database is wired). Key format: `"$sourceId|$episodeUrl"`.
- **Why:** User explicitly asked for a "simple system which captures the watch page progress but never does anything with it because we are going to implement it later on." This exercises the save path end-to-end so it's ready for the database swap. The `WatchProgressStore` interface stays the same — only the impl changes in Phase 5e.
- **ponytail:** in-memory map → upgrade to SQLDelight impl in Phase 5e. Ceiling: full persistent progress with resume + "Continue Watching".
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). → **SUPERSEDED by Phase WP**: `SqlDelightWatchProgressStore` now persists to the `watch_progress` SQLDelight table (Koin binds the SQLDelight impl, NOT the in-memory one). The `InMemoryWatchProgressStore` class has been removed. Key format changed to `"${mainId}|${padded_5_digit}"`. Resume-seek + Continue Watching UI still pending (see progress.md "What's Next" #5).
- **Date:** Phase 5c (session web-f53f0459). Superseded Phase WP.

### D-073 — `EpisodeSwitchingOverlay` ported + speed setter bug fixed
- **What:** (1) Created `core/player/controls/EpisodeSwitchingOverlay.kt` — a Compose overlay shown over the player surface while a new episode resolves + loads. Dark gradient + spinner + "Loading episode..." + optional episode title with a subtle pulse animation. Shown in both minimized and fullscreen modes when `isSwitching` is true. (2) Fixed the speed setter bug in `AnikutaMPVView`: `setPropertyInt("speed", value.toInt())` truncated Float to Int (1.5f → 1, 0.5f → 0). Changed to `setPropertyDouble("speed", value.toDouble())`.
- **Why:** (1) The old project had an `EpisodeSwitchingOverlay` — the new project didn't, so during a switch the user saw a frozen frame with no feedback. (2) The speed setter bug was latent (no SpeedSheet wired yet) but would break 1.5x/0.5x playback the moment a speed control is added.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-074 — Dead `singleOf(::PlayerStateHolder)` removed from `playerModule`
- **What:** Removed the `singleOf(::PlayerStateHolder)` Koin registration from `PlayerModule.kt`. `PlayerStateHolder` is a plain class owned by `WatchScreen` via `remember { PlayerStateHolder() }` (per ADR-025). It was registered as a Koin singleton but NEVER injected anywhere — `WatchScreen` creates its own. The registration was dead weight.
- **Why:** The old project never registered `PlayerStateHolder` in Koin either. Only one `WatchScreen` exists at a time, so it needs its own holder instance, not a shared singleton. Keeping the dead registration was confusing (implied it should be injected).
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-075 — TLS CA cert fix: deleted empty cacert.pem, guarded tls-ca-file
- **What:** The `cacert.pem` file in `core/player/src/main/assets/` was 0 bytes (empty). mbedTLS tried to parse it, failed with `MBEDTLS_ERR_X509_INVALID_FORMAT` (-8576), and did NOT fall back to system CAs → ALL HTTPS streams failed instantly. Fix: (1) deleted the empty file from assets; (2) `AnikutaMPVView.initOptions` now only sets `tls-ca-file` if the file exists AND is non-empty — otherwise lets mbedTLS fall back to the system CA store; (3) `PlayerInitializer.copyAssets` skips 0-byte assets defensively.
- **Why:** An empty cacert.pem is worse than no file — mbedTLS gets INVALID_FORMAT (no fallback) vs FILE_IO_ERROR (falls back to system CAs). The old project never had cacert.pem in assets — mbedTLS fell back. Root cause traced from user logs showing `tls: mbedtls_x509_crt_parse_file for CA cert returned -8576`.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Related lesson:** "Empty cacert.pem causes mbedTLS INVALID_FORMAT — worse than missing the file."

### D-076 — Observer cleanup: remove MPVLib observers on dispose
- **What:** `MPVLib.addLogObserver()` and `MPVLib.addObserver()` were called in `initMpv` but the corresponding `removeLogObserver()` / `removeObserver()` were NEVER called in `onDispose`. Each screen entry added 2 new observers → after N entries, every event fired N times. User logs showed 4x duplication (4 observers after 4 entries). Fix: hoisted `logObserverRef` + `eventObserverRef` as state; `onDispose` now removes both observers before destroying the view.
- **Why:** Without removal, observers accumulate → state corruption (each `updateError` fires Nx, each `setSwitching` fires Nx), error multiplication, track loading races. Root cause traced from user logs showing every event firing 4 times.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-077 — Error handling rework: non-intrusive banner + auto-retry
- **What:** Replaced the full-screen `PlayerErrorOverlay` "dialog box" with a small non-intrusive `PlayerErrorBanner` (top-aligned bar with message + retry + close buttons, video surface stays visible). Added auto-retry: on error (non-switching), auto-retry same URL once after 1.5s delay. `autoRetryAttempted` flag in `PlayerStateHolder` (reset on each new video). `clearErrorForRetry()` method to clear error without clearing switching flag.
- **Why:** User explicitly said: "The error dialog box is not the way to go. I don't want you to show an error dialog box inside the video player itself." Auto-retry handles transient failures (network hiccup, TLS renegotiation) silently before the banner appears.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-078 — Pause→loading spinner fix + episode switch title fix
- **What:** (1) Loading spinner condition was `!isPlaying && (buffering || loadingState == LOADING)` — when user paused, `!isPlaying` was true and if `loadingState` was still LOADING (e.g. during a seek), the spinner showed. Changed to `buffering || (loadingState == LOADING && duration == 0)` — spinner only shows when actually buffering or during initial load (before video has a duration). (2) Episode switch overlay showed old episode name because `currentEpisodeTitle` was only updated AFTER resolve succeeded. Now `updateCurrentEpisode()` is called IMMEDIATELY when the switch starts (before resolve).
- **Why:** User reported "when I pause the video, the loading animation starts to play" and "it was still showing me 'loading episode 14' for a while on the video player" when switching to episode 10.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-079 — EpisodeTitleParser: sanitize extension episode names + numbers
- **What:** Created `EpisodeTitleParser` in `:core:common` (shared by Details + Watch). Strips "Episode X - " prefixes to extract clean titles. Detects hash/URL/code-like names (>25 chars, no spaces, mostly hex) and falls back to "Episode N". Formats episode numbers: 5.0 → "5", 5.5 → "5.5", 0/negative → "?" (handles bad extension data). `EpisodeListRow` + "Currently playing" card now use the parser.
- **Why:** User reported "random numbers for the episodes, like random code words or something like that. The same thing goes for the name of them too." Some extensions return raw URLs/hashes as episode names.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-080 — SpeedSheet + skip-next wired in fullscreen
- **What:** Created `SpeedSheet` (ported from old project) — presets (0.25x–2.0x) + custom slider (0.1x–5.0x). Wired `onSpeedClick` in `FullscreenControls` → opens SpeedSheet. `onSpeedSelected` applies live via `AnikutaMPVView.playbackSpeed = speed` (which uses `setPropertyDouble` — fixes the 1.5x truncation bug). Wired `onSkipForward` → finds next episode in list + switches to it.
- **Why:** User reported "I tried selecting the speed and it did not give me any options." The speed button was dead (Phase D work). Skip-next was also dead.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-081 — Safety nets: 15s fatal-error watchdog + app-exit pause/resume
- **What:** (1) 15s fatal-error watchdog: after video loads (duration > 0), if position stays at 0 or stuck at duration-2 for 15s with no error + not playing, shows "This server is not responding. Try another server or quality." Catches HLS demuxer errors that don't trigger END_FILE. (2) App-exit pause/resume: `DisposableEffect(lifecycleOwner)` with `LifecycleEventObserver` — ON_STOP pauses playback, ON_START logs return. Uses ON_STOP/ON_START (not ON_PAUSE/ON_RESUME) so multi-window focus changes don't trigger a pause.
- **Why:** Ported from old project's 3-layer error handling (efEvent + 30s switching timeout + 15s fatal-error watchdog). The new project only had layers 1 + 2; layer 3 was missing. App-exit pause matches old project's `pauseOnAppExit` behavior.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-082 — Episode switch: STOP current video immediately + separate isSwitchingEpisode
- **What:** Two fixes: (1) `onEpisodeSwitch` now calls `MPVLib.command(arrayOf("stop"))` BEFORE starting the resolve — the old video stops instantly so the user doesn't hear/see it playing while the new one loads. (2) Separated `isSwitching` (error suppression, used for both quality + episode switches) from `isSwitchingEpisode` (overlay display, episode switches only). Quality/server switches no longer show the "Loading episode..." overlay — they just show the buffering spinner.
- **Why:** User reported: "When I switch to a higher resolution video, the video started playing in the background but on the foreground it was still showing me loading." And: "As soon as the user clicks on another episode, the currently playing one should immediately stop all of its actions." The root cause was that quality switches showed the EpisodeSwitchingOverlay (confusing — it says "Loading episode" for a quality switch) AND the old video kept playing during the resolve.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-083 — Error banner should NOT auto-disappear during auto-retry
- **What:** Removed `clearErrorForRetry()` from the auto-retry `LaunchedEffect`. The error banner now stays visible during the auto-retry. If the retry succeeds, `FILE_LOADED` clears the error (banner disappears). If the retry fails, the error stays (or gets updated with the new efEvent message).
- **Why:** User reported: "After it shows playback error, the error shows for a little bit while and then it automatically disappears. That is something which needs to be adjusted for." The auto-retry was clearing the error (hiding the banner) before re-sending loadfile, which made the banner look like it auto-dismissed.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-084 — EpisodeTitleParser: better hash/code detection + large number handling
- **What:** (1) `looksLikeCodeOrHash` lowered threshold from 25 to 15 chars. Added detection for all-caps + digits with no spaces (>10 chars) — catches strings like "DGFV024L2R0V2IXL0F1" (20 chars, all uppercase + digits). (2) `formatEpisodeNumber` now returns "?" for numbers > 1000 — catches timestamps/IDs like "1784388992" that extensions sometimes put in `episode_number`.
- **Why:** User reported: "The episode numbering was something like 1784388992. The actual name of the episode was being shown as some random letters and characters, like DGFV024L2R0V2IXL0F1." The previous threshold (>25 chars) didn't catch 20-char strings.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-085 — Subtitle detection: delayed retry + better logging
- **What:** (1) Added a delayed track reload 2s after `FILE_LOADED` — some HLS formats take a moment to fully parse, and the first `loadTracksFromMpv()` might run before MPV has registered all tracks. The 2s retry catches this. (2) Better logging in `loadTracksFromMpv`: logs the MPV `track-list/count` value + a warning when no tracks are detected, so we can diagnose whether subs are internal (muxed) or external (sub-add).
- **Why:** User reported: "The subtitles were apparently not being detected or not being handled properly." The delayed retry helps with slow-parsing formats. The logging helps diagnose the issue if it persists.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-086 — CORE_RULES §3: test checklist after improvements
- **What:** Added a rule to CORE_RULES §3: "After implementing improvements/fixes, ALWAYS provide a test checklist the user can follow to verify each fix on their device." Format: grouped by category, checkbox format, user reports back ✅/❌/⚠️.
- **Why:** User requested: "Make sure that this checklist functionality is added into the rules too. If improvements have been made and such then the user should be given a checklist so that he can check out what he needs to check."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-087 — Episode list serialization: use \u001F delimiter (not '|')
- **What:** Changed the episode list serialization delimiter from `|` to `\u001F` (ASCII Unit Separator). Updated `WatchKey.parseEpisodeList()` + `DetailsScreen` serialization. Moved `EPISODE_FIELD_DELIMITER` constant to `EpisodeTitleParser` in `:core:common` (both modules depend on it — avoids module cycle).
- **Why:** User reported: "On the details page the name shows properly but on the player page the names change to random strings. Even the episode number does not show properly." Root cause: episode URLs can contain `|` characters (some extensions use `|` in their URL scheme). When the URL contains `|`, the `split("|", limit=3)` puts the URL's `|` tail into the episode number field → corrupts the URL, number, AND name. `\u001F` is a control character that never appears in URLs or names.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-088 — Quality switch spinner: add isSwitching to spinner condition
- **What:** Added `isSwitching` to the loading spinner condition in both `MinimizedControls` and `FullscreenControls`. New condition: `buffering || isSwitching || (loadingState == LOADING && duration == 0)`.
- **Why:** User reported: "The loading animation does not start to play" during quality switch. Root cause: during a quality switch, `loadingState == LOADING` but `duration > 0` (from the previous video), so the old condition `buffering || (loadingState == LOADING && duration == 0)` was false → no spinner. Adding `isSwitching` makes the spinner show during any switch (quality or episode). Episode switches show the EpisodeSwitchingOverlay on top, which covers the spinner.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-089 — Switching timeout: 30s → 60s
- **What:** Increased the switching timeout watchdog from 30s to 60s.
- **Why:** User reported: "It outright gave me this error that video failed to load, time out" during episode switch. Root cause: the 30s watchdog starts when `isSwitching` becomes true, but the resolve phase (network call to extension) can take 20-30s BEFORE loadfile is sent. Then loadfile needs 5-10s to load. Total > 30s → premature timeout. The old project used 30s but its resolve was pre-done on the details page (instant). 60s gives enough time for resolve + load.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-090 — Error banner: tap to copy error message
- **What:** The error banner text is now clickable — tapping it copies the full error message to the clipboard. Label changed to "Playback error (tap to copy)".
- **Why:** User requested: "If I tap the error itself, the text of the error itself, what it should do is that it should copy the whole error message."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-091 — Subtitle detection: detailed logging + longer retry
- **What:** (1) Added INFO-level logging for every `sub-add`/`audio-add` command — logs success AND failure with the URL. (2) Added warning when `trackHeaders` is blank (subs may fail to download). (3) Increased delayed track retry from 2s to 5s. (4) Increased external track load delay from 300ms to 500ms.
- **Why:** User reported: "There were no subtitles. It did not detect any subtitles at all." The detailed logging will show exactly what's happening: whether `sub-add` commands are sent, whether they succeed, whether tracks are registered. The longer delays give external subs more time to download over HTTPS.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). Needs logcat verification.
- **Date:** Phase 5c (session web-f53f0459).

### D-092 — CRITICAL: Cleartext traffic permission (root cause of proxy/subtitle/spinner issues)
- **What:** Added `android:usesCleartextTraffic="true"` + `android:networkSecurityConfig="@xml/network_security_config"` to the AndroidManifest. Created `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="true"` for all domains + system+user trust anchors.
- **Why:** The AniKotoS extension starts a local HTTP proxy on `http://127.0.0.1:PORT` during `getHosterList()`. All video URLs point to this proxy. But Android 9+ blocks cleartext traffic by default → proxy can't be reached → proxy dies immediately (2ms later) → all videos fail → 60s timeout → subtitles can't download → spinner never clears (FILE_LOADED never fires). User logs showed: `Proxy started at http://127.0.0.1:40815` → `Proxy stopped` (2ms later) → 60s timeout. The OLD project had this permission; the NEW project was missing it. Root cause found by comparing old vs new AndroidManifest.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Related lesson:** "Cleartext traffic blocked on Android 9+ — extensions using localhost proxies (AniKotoS) need usesCleartextTraffic + network_security_config.xml."

### D-093 — Subtitle sheet: refresh tracks on open
- **What:** `SubtitleTracksSheet` now takes an `onRefreshTracks` callback, called via `LaunchedEffect(Unit)` when the sheet opens. `WatchScreen` passes a lambda that manually calls `mpvView.loadTracks()` + `stateHolder.updateTracks()`.
- **Why:** Subtitles might not be detected on the first `loadTracksFromMpv()` call (runs too early, before external subs finished downloading). Refreshing when the sheet opens catches cases where tracks were loaded too early or where the track list changed. The user reported the subtitle sheet was empty even for subbed streams.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-094 — Seeking treated as buffering
- **What:** `PlayerObserver.onProperty` now handles the `"seeking"` property → `stateHolder.updateBuffering(value == "yes")`. Previously, `seeking` was observed (in `AnikutaMPVView.observeProperties`) but NOT handled in the observer — so seeking didn't set `buffering=true`.
- **Why:** The old project treats seeking as buffering (`WatchScreen.kt:580`). While MPV is seeking, the video is not playing and the user should see a spinner. The new project's spinner condition includes `buffering`, so without seeking → buffering, seeking wouldn't show the spinner.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-095 — Remove stop command from episode switch (match old project)
- **What:** Removed `MPVLib.command(arrayOf("stop"))` from the episode switch handler. The old project does NOT stop before switching — it just sends `loadfile` with "replace" mode, which replaces the current file (stopping the old video automatically). The new project was calling stop first, which may cause the AniKotoS extension to detect player disconnection and kill its local proxy.
- **Why:** User logs showed the AniKotoS proxy dying 4ms after starting. The old project doesn't call stop and works fine. The stop command may trigger the extension's cleanup logic, killing the proxy before loadfile can connect.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-096 — Don't set HTTP headers for localhost proxy URLs
- **What:** For localhost proxy URLs (`http://127.0.0.1:PORT/...`), do NOT set upstream HTTP headers (Referer, Origin, etc.). The proxy doesn't need them and they may cause issues. Applied to: initMpv, onQualitySelected, onEpisodeSwitch.
- **Why:** AniKotoS proxy URLs are localhost. The upstream headers (Referer: https://megaplay.buzz/) are for the upstream CDN, not for the local proxy. Setting them on localhost may cause the proxy to reject the connection.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-097 — Clear buffering when video starts playing
- **What:** When `pause = no` (video starts playing), immediately clear `buffering = false`. This fixes the quality switch spinner staying visible after the video starts playing.
- **Why:** `paused-for-cache` may not fire `no` immediately after playback starts. The spinner condition includes `buffering`, so if `buffering` is stuck true, the spinner stays. Clearing it on `pause = no` ensures the spinner disappears when the video starts.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-098 — Switching timeout reduced to 30s (user request)
- **What:** Reduced the switching timeout watchdog from 60s back to 30s.
- **Why:** User said: "You could make the timeout just 30 seconds as it is quite enough for it to actually play." 30s is enough for resolve + loadfile + buffer.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-099 — Loading spinner: buffered 1% check
- **What:** Added `bufferedEnough` flag to `PlayerStateHolder`. Set to true when `demuxer-cache-time > position + 1% of duration`. Spinner condition changed to `isSwitching && !bufferedEnough` — once 1% buffered, spinner hides even if isSwitching is still true. `LaunchedEffect(bufferAheadTime)` clears isSwitching + isSwitchingEpisode when bufferedEnough becomes true.
- **Why:** User reported: "video started to play but loading was still there." The spinner stayed because isSwitching was only cleared on FILE_LOADED, which may fire late or not at all for some formats. User suggested: "If the video has buffered 1%, then the loading animation will go away." This is a more reliable signal — once data is flowing, the load succeeded.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-100 — Subtitle "Off" always visible
- **What:** Restructured `SubtitleTracksSheet` to always render the "Off" entry as the first item in the LazyColumn, regardless of whether `tracks` is empty. When empty, shows "Off" + "No subtitles found" message. When non-empty, shows "Off" + actual tracks.
- **Why:** User reported: "I don't even see the OFF option in the subtitles." Root cause: "Off" was inside the `else` branch of `if (tracks.isEmpty())` — hidden when no tracks. Sub-agent investigation confirmed this was the PRIMARY root cause.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).
- **Sub-agent:** SUBTITLE-INVESTIGATOR (analysis only — no code changes by sub-agent).

### D-101 — Subtitles: carry tracks directly in WatchKey (no registry lookup)
- **What:** Added `subtitleTracks` + `audioTracks` to the flat `ResolvedVideo` type. Added `subtitleTracksSerialized` + `audioTracksSerialized` to `WatchKey` (with `parseSubtitleTracks()` + `parseAudioTracks()` methods). Updated `DetailsScreen` to serialize tracks from the picked video + pass them through `onNavigateToWatch`. Updated `WatchScreen.initMpv` to use WatchKey tracks as PRIMARY source, registry lookup as FALLBACK.
- **Why:** User reported subtitles not showing. Sub-agent investigation found the root cause: `ResolvedVideo` (flat type) did NOT carry `subtitleTracks` — only the structured `ResolverVideo` (in registry) did. WatchScreen relied on a registry lookup that could fail. The old project carries `subtitleTracks` directly in `WatchRequest`. Analysis of the AniKotoS extension repo confirmed: subtitles are provided as `Track(url, lang)` pairs in `Video.subtitleTracks`, with URLs like `http://127.0.0.1:PORT/sub/0/0` (localhost proxy).
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Sub-agent:** SUBTITLE-INVESTIGATOR (analysis), aniyomi-extensions repo analysis.

### D-102 — ResolverSheet rebuilt as collapsible accordion
- **What:** Rebuilt the ResolverSheet (video picker bottom sheet) with a collapsible server accordion design matching the old project. Features: header with "Episode N" + close button, collapsible server cards (one open at a time), audio version count chips on the right when collapsed (reversed so SUB is rightmost), FlowRow of quality chips with PlayArrow icon when expanded, proper states (Resolving/NoSources/Error), expand/collapse animations.
- **Why:** User requested: "Instead of directly showing the entries outright, it probably shows them in a properly formatted order with proper collapsible entries and so forth. Only one server can be opened at a time."
- **Architecture:** `ResolverState.Success` now includes `servers: List<ResolverServer>` (structured 3-tier hierarchy) so the sheet has the data it needs without a separate registry lookup. The `ServerAccordion` uses `ResolverVideo` (structured) internally, converts to `ResolvedVideo` (flat) when the user picks a video (finds matching URL in the flat list, or creates from ResolverVideo fields as fallback).
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Sub-agent:** RESOLVER-SHEET-ANALYZER (old project analysis).

### D-103 — Subtitle diagnostic logging + logcat filter rule
- **What:** (1) Upgraded VideoResolver subtitle logging from DEBUG to INFO level — now logs each video's subtitle track count + URL/lang. (2) Added comprehensive diagnostic logging in WatchScreen.onRefreshTracks — logs WatchKey.subtitleTracksSerialized, parsed track count, state holder track count, observer pendingSubtitleTracks, MPV track-list/count, and each track's id/name/lang. (3) Added CORE_RULES §20 rule: logcat filters must be in Android Studio format (`tag:X | tag:Y message~:(?i)(keywords)`), never `adb logcat`.
- **Why:** User reported subtitles still not showing + no logs appeared with their filter. The diagnostic logging will reveal exactly where the subtitle flow breaks. The logcat filter rule ensures future filters are directly pasteable into Android Studio.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-104 — VideoResolver carries hoster names (VideoEntry) + correct parsing
- **What:** Created `VideoEntry(video, hosterName)` data class. `resolveVideoEntries()` now returns `List<VideoEntry>` — carries the hoster name alongside each video. `buildServers()` and `groupIntoServers()` use the hoster name as the PRIMARY server name (falls back to parsing from title). `extractQuality()` extracts just the resolution (e.g. "1080p" from "SUB - 1080p") using regex. `parseAudioVersion()` handles SUB/DUB/HSUB/MIX/RAW/HARDSUB/SUBBED/DUBBED. `ResolverState.Success.rawVideos` → `rawEntries: List<VideoEntry>`.
- **Why:** User reported: "On the very right side it showed me the default text... it was supposed to show the available audio versions (SUB, DUB, HSUB)." And: "the overall entries were shown as full names instead of just showing me the resolution number." Root cause: VideoResolver lost hoster names when collecting videos — `videos.addAll(hosterVideos)` didn't track which hoster each video came from. `parseServerName()` then tried to extract the server name from the video title, but AniKotoS titles are just "SUB - 1080p" (no server name). Also `parseQuality()` returned the full title instead of just the resolution.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Sub-agent:** SUBTITLE-DEEP-DIVE (confirmed subtitle code is correct — issue is likely old test build).

### D-105 — ResolverSheet audio chips show just labels (SUB/DUB/HSUB)
- **What:** Audio version chips in the ResolverSheet now show just the label (e.g. "SUB", "DUB", "HSUB") instead of "count label" (e.g. "2 SUB"). "Default" audio versions are filtered out from the chips — if there's only one audio version and it's "Default", no chips are shown (the server name is enough).
- **Why:** User reported: "it showed me the default text... it was supposed to show the available audio versions." The old project shows just the label.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-106 — Audio version parsing — case-insensitive search anywhere in title
- **What:** Rewrote `parseAudioVersion()` to use case-insensitive regex search ANYWHERE in the title (not just at the start). Checks HSUB first (so "HSub" doesn't match as "Sub"). Handles mixed case ("Sub", "Dub", "HSub"). Also fixed `parseServerName()` to try " - " split first (takes text before first " - " as server name).
- **Why:** User logs showed titles like "HD-1 - Sub - 1080p" where audio version is in the MIDDLE. Old regex `^(SUB|DUB|HSUB)` only matched at start → returned "Default". Also "Vidstream-2" was truncated to "Vidstream" because knownServers prefix match ran before the " - " split.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-107 — Dedicated SubtitleEngine — downloads subs to temp files
- **What:** Created `SubtitleEngine` in `:core:player/subtitles/`. Downloads external subtitle URLs to temp files using OkHttp (with proper headers). Returns local file paths. PlayerObserver sends `sub-add` with LOCAL file path instead of URL. Temp files cleaned up on player destroy.
- **Why:** User requested: "We should create a separate dedicated engine or module to get the subtitles, download them, save them temporarily, and show them in the subtitles menu properly." MPV's `sub-add` with URLs doesn't support custom HTTP headers — subtitle downloads were failing silently.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-108 — EpisodeMetadataFetcher — AniList streamingEpisodes
- **What:** Created `EpisodeMetadataFetcher` in `:core:metadata`. Fetches episode metadata (title, thumbnail) from AniList's `streamingEpisodes` GraphQL field. Returns `Map<Int, EpisodeMetadata>`. Registered in Koin via `metadataModule`. Wired into `DetailsViewModel` — fetches in parallel after episodes load. `EpisodeMetadataSource` interface for future sources (Jikan, Anikage.cc).
- **Why:** User requested: "the metadata fetching capability, like how the old project has a full-fledged function of fetching the metadata and populating the episode list with that."
- **Architecture:** Pluggable source system — new sources implement `EpisodeMetadataSource` without modifying existing code. No caching for now (re-fetch every time). Caching is a future phase.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Sub-agent:** METADATA-RESEARCH (old project analysis).

### D-109 — CRITICAL: Handle PLAYBACK_RESTART as FILE_LOADED fallback for subtitles
- **What:** Added `MPV_EVENT_PLAYBACK_RESTART` (17) handler to `PlayerObserver.onEvent()`. When PLAYBACK_RESTART fires and `isSwitching` is still true or `bufferedEnough` is false, it: clears switching flag, clears error, sets READY, loads external subtitle tracks (sub-add), reloads track list, schedules 5s delayed safety reload.
- **Why:** User logs showed that for some HLS streams, MPV fires event 8 (FILE_ERROR) instead of event 11 (FILE_LOADED). Since FILE_LOADED never fires, `loadExternalTracks()` never runs → `sub-add` is never sent → subtitles don't load. However, the video DOES play — event 17 (PLAYBACK_RESTART) fires right after event 8, meaning playback actually started. The old project doesn't have this issue because it gets FILE_LOADED. This is the root cause of all subtitle failures.
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).
- **Evidence:** User log comparison showed:
  - New project: `FILE_ERROR (8)` at 17:39:49.893, `PLAYBACK_RESTART (17)` at 17:39:49.894 — no FILE_LOADED
  - Old project: `MPV_EVENT_FILE_LOADED` at 17:38:12.395, `sub-add` at 17:38:12.433 — subtitles loaded

### D-110 — Episode metadata UI — thumbnails, titles, descriptions, dates + loading states
- **What:** Updated `EpisodesSection` and `EpisodeRow` in DetailsScreen to display episode metadata (thumbnails, titles, descriptions, air dates) fetched by `EpisodeMetadataFetcher`. Added loading spinner next to "Episodes" header (shows while metadata is being fetched), red "Failed to load metadata" error message (auto-hides after 5s). `EpisodeRow` now shows: thumbnail (120x68dp, from metadata) with episode number overlay, title (from metadata or parsed via EpisodeTitleParser), description (from metadata or episode.summary), air date (formatted as "MMM d, yyyy"). No-thumbnail fallback shows episode number badge.
- **Why:** User requested: "Make sure that it gets implemented in this session and make sure that all the metadata, like the thumbnail images of each individual episode, the titles of each episode, the synopsis or the description, and also the release date and all of those, show properly."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-111 — CORE_RULES §28: Log Comparison Debugging
- **What:** Added new core rule §28 documenting the log comparison debugging methodology that solved the subtitle issue. Rules: ask for logs from BOTH projects, compare exact event sequences, look for MISSING events (not just extra ones), handle fallback events, don't go in circles (3+ same approach = wrong approach), document the root cause.
- **Why:** User requested: "I would like you to create a core rule like this, which handles these situations properly for us and guides us properly on what we should do, how we should handle things and situations like these." The subtitle fix took many sessions of going in circles before the root cause was found via log comparison.
- **Status:** ✅ Implemented.
- **Date:** Phase 5c (session web-f53f0459).

### D-112 — Metadata: 3-source fallback (Anikage.cc + Jikan + AniList streaming)
- **What:** Rewrote `EpisodeMetadataFetcher` to use 3 sources with first-non-null-wins merge: (1) Anikage.cc (PRIMARY — title, description, thumbnail, airDate, uses AniList ID), (2) Jikan/MAL (SECONDARY — title, airDate, uses MAL ID from AniList's idMal), (3) AniList streamingEpisodes (TERTIARY — title, thumbnail). Added `idMal` to AniListAnime model + fetchAnimeDetails GraphQL query. Added OkHttp dep to `:core:metadata`.
- **Why:** User logs showed `AniList streamingEpisodes: 0 episodes` — AniList doesn't have streaming data for every anime. The old project uses 3 sources. Anikage.cc is the most complete (uses AniList ID directly, no MAL mapping needed).
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-113 — Spinner retry loop fixed
- **What:** Added `metadataFetchDone` flag to `EpisodesSection`. Once set (either metadata arrives OR 15s timeout), the spinner hides permanently — no retry loop. Error message shows for 5s then hides. Reset only when episodes reload (new anime).
- **Why:** User reported: "The spinner kept spinning for some time and in the end it said 'Failed to load metadata'. Then it started to try again and it started spinning again but that was not supposed to happen."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459).
- **Date:** Phase 5c (session web-f53f0459).

### D-114 — EpisodeRow UI overhaul — match old project layout + styling
- **What:** Rewrote EpisodeRow to match the old project's layout: (1) Top section = thumbnail (left, 120x68dp) with EP tag overlay (TopStart, themed primary, 6dp corners, Bold White, 11sp) + right column (title on top with surface@0.5f background, date+audio pills on bottom with outlineVariant background) + download icon (far right, 24dp, visual only). (2) Bottom section = synopsis below the entire top row with surface@0.35f background, 12sp, 15sp lineHeight, 2 max lines. No-thumbnail fallback = 40dp circle disc (surfaceVariant, 13sp Bold).
- **Why:** User requested: "The synopsis should be shown below the thumbnail and title row. The release date and SUB/DUB/HSUB availability should be below the title. The EP tag should be themed primary with proper dimensions like the old project. Add a download button (visual only)."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-115 — Audio availability detection (SUB/DUB/HSUB) from extension data
- **What:** Added `parseAudioAvailability(scanlator, episodeName)` — parses SUB/DUB/HSUB from the episode's scanlator field + name. Displays as outlineVariant pills with dot separators (SUB•DUB•HSUB). Only shown if audio availability is detected. Ported from the old project.
- **Why:** User: "The availability of subbed episodes and dubbed episode data is fetched from the extension itself. When you fetch the episodes list, the availability is also sent along with it."
- **Status:** ✅ Implemented.
- **Date:** Phase 5c (session web-f53f0459).

### D-116 — Details banner uses cover image (not banner image)
- **What:** Changed `bannerUrl` from `anime.bannerImage ?: anime.coverUrl` to just `anime.coverUrl`. Matches old project (uses cover image as background).
- **Why:** User: "Utilize the exact same cover image for the background too. A future tint-color system will extract the dominant color from the cover."
- **Status:** ✅ Implemented.
- **Date:** Phase 5c (session web-f53f0459).

### D-117 — Watch page QualitySheet redesigned as accordion
- **What:** Replaced the flat list QualitySheet with a collapsible accordion design matching the ResolverSheet. Header renamed to "Qualities and Servers" with subtitle "Tap a server to expand, then pick a quality." One server open at a time (defaults to currently playing server). Audio version chips on the right (reversed, currently playing highlighted with primary tint). Quality chips with PlayArrow icon — currently playing highlighted with primary background + border. Expand/collapse animations.
- **Why:** User requested: "Inside the video player at the top right corner there is the qualities option. When I click it it shows a bottom-up menu which says the heading 'Pick a video' but this is not correct. I want you to improve it and it should say 'Qualities and Servers'... I need you to redesign it completely just like how it is being done in the old project."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-118 — Details page UI fixes — title 1 line, EP tag 'EP N', download bottom-right
- **What:** (1) Episode title maxLines=1 (was 2). (2) EP tag shows "EP N" not just "N". (3) Download button moved from far-right of top section to bottom-right of synopsis, themed primary tint. No-synopsis fallback shows download at bottom-right anyway.
- **Why:** User: "The title should only be shown on one single line. It should show EP 6, EP 7, EP 8. Move the download button to the very bottom right corner of the episode summary with a themed tinted color."
- **Status:** ✅ Implemented.
- **Date:** Phase 5c (session web-f53f0459).

### D-119 — Watch page episode list with metadata + currently-playing details
- **What:** Watch page episode list now shows rich episode rows matching the details page: thumbnail (120x68dp) with EP tag overlay, title (1 line, surface bg), date pill, audio pills (SUB/DUB/HSUB), synopsis (2 lines, surface bg), download button (bottom-right, themed primary tint). Currently-playing episode details section below the player shows: "Currently playing episode N" header, title (20sp), date + audio pills row, synopsis with "Show more"/"Show less" expand button.
- **Data flow:** DetailsScreen serializes episodeMetadata (title, thumbnail, airDate, description, scanlator) into WatchKey.episodeMetadataSerialized → WatchScreen parses via watchKey.parseEpisodeMetadata() → passed to MinimizedMode → EpisodeListRow + currently-playing section.
- **Why:** User: "The watch page episode list should show the episode metadata properly... Each individual episode is a thumbnail, its title, the description, the sub and dub availability, and the download button. The details below the video player should show the full info of the currently playing episode."
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). CI green.
- **Date:** Phase 5c (session web-f53f0459).

### D-120 — Extension details page Phase A (MVP) — UnifiedAnime + sealed NavKey + providers
- **What:** Implemented Phase A of the extension details page architecture:
  1. `UnifiedAnime` model in `:core:common` — source-agnostic, all fields nullable except title. Has `displayName`, `temporaryContentId`, `isExtensionOnly`, `isFromExtension` properties.
  2. `AnimeDetailsProvider` interface + `AnimeDetailsProviderRegistry` in `:core:common` — modular provider pattern (AniList + Extension providers, registered via Koin with named qualifiers).
  3. `AniListDetailsProvider` in `:core:anilist` — `fetchFromAniList()` + `mergeInto()` for auto-link + `AniListAnime.toUnifiedAnime()` mapper.
  4. `ExtensionDetailsProvider` in `:data:extension` — `fetchFromExtension()` calls `source.getAnimeDetails()`, catches Throwable, falls back to sparse data + `SAnime.toUnifiedAnime()` mapper.
  5. `AnimeDetailsKey` changed to sealed interface: `AniList(animeId)` + `Extension(sourceId, animeUrl, title, thumbnailUrl)`, both `@Serializable`.
  6. `DetailsViewModel`: added `loadFromAniList()` + `loadFromExtension()`. `DetailsState.Success` now holds `UnifiedAnime` (not `AniListAnime`).
  7. `DetailsScreen`: accepts `detailsKey: AnimeDetailsKey` (was `animeId: Int`). `DetailBanner` + `InfoSection` use `UnifiedAnime`.
  8. `SearchScreen`: `onNavigateToExtensionAnime` callback now passes `(sourceId, url, title, thumbnailUrl)` — wired in `MainActivity`.
  9. `BrowseScreen`, `LibraryScreen`: updated to use `AnimeDetailsKey.AniList(...)`.
- **Why:** User: "Currently we are only able to open up the details page from AniList... If the user goes to the search page and switches to the extension and tries to open up an anime from there, then he cannot open up the anime from there."
- **Status:** ✅ Implemented (Phase 5d, session web-f53f0459). CI green.
- **Date:** Phase 5d (session web-f53f0459).
- **Not yet implemented:** Phase B (auto-link, SmartMatcher, manual link sheet), Phase C (contentId system).

### D-122 — Download button consistent size + toast
- **What:** Created `DownloadEpisodeButton` composable (24dp Download icon in 40dp clickable Box). Used in BOTH the no-synopsis layout (date/audio pills row) AND the with-synopsis layout (bottom-right of synopsis). Shows toast "Download functionality not yet implemented" on tap.
- **Why:** User: "I like the size of it when there is no synopsis but when there is a synopsis then the button is way too small. I would like you to make the download button bigger and as big as it will be when there is no synopsis... when I click the download button, it should just give me a small toast notification that 'Download functionality not yet implemented'."
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-123 — `:core:smart-matcher` module
- **What:** New Gradle module `:core:smart-matcher` containing:
  - `TitleNormalizer` — normalizes titles for comparison (lowercase, strip punctuation/parentheticals, remove season/year suffixes).
  - `LevenshteinDistance` — character-level edit distance + similarity ratio (two-row DP).
  - `MatchResult` / `AutoLinkResult` — sealed result types.
  - `SmartMatcherConfig` — threshold + strategy (FUZZY/STRICT/MANUAL) + bonuses.
  - `SmartMatcher` — main matching logic with contains bonus + year bonus.
  - `AutoLinkService` — orchestrator (cache → per-source check → AniList search → SmartMatcher → cache result).
- **Why:** Phase B of the extension details page architecture. Needed a standalone, testable module for fuzzy title matching between extension SAnime titles and AniList search results.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-124 — `AutoLinkPreferences` (global + per-source + cache)
- **What:** Added `AutoLinkPreferences` to `:core:preferences`. Backed by SharedPreferences via PreferenceStore. Stores:
  - Global: `auto_link_enabled` (bool, default true), `auto_link_strategy` (string: fuzzy/strict/manual), `auto_link_threshold` (float, default 0.80).
  - Per-source: `auto_link_source:$sourceId` (string: default/on/off).
  - Link cache: `auto_link_cache:$sourceId:$hash(animeUrl)` (int: anilistId).
  - `isAutoLinkEnabledForSource(sourceId)` resolves global ANDed with per-source override.
- **Why:** Needed persistent storage for auto-link settings. Per-source overrides let users disable auto-link for specific extensions that have unreliable titles. Link cache prevents re-searching AniList on every details page open.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-125 — DetailsViewModel auto-link integration
- **What:** DetailsViewModel constructor expanded to 9 params (added `anilistProvider: AniListDetailsProvider`, `autoLinkService: AutoLinkService`, `autoLinkPreferences: AutoLinkPreferences`). New state flows: `autoLinkState` (Idle/Searching/Matched/NoMatch/Skipped/Error), `anilistSearchState` (Idle/Searching/Empty/Results/Error), `showManualLinkSheet`. New methods: `performAutoLink()`, `searchAniListForLink()`, `linkAniListEntry()`, `skipAniListLink()`, `unlinkAniList()`, `openManualLinkSheet()`, `dismissManualLinkSheet()`, `mergeAniListIntoUnified()`. `loadFromExtension()` now calls `performAutoLink()` after extension details load. `mergeAniListIntoUnified()` sets anilistId + calls `AniListDetailsProvider.mergeInto()` + triggers episode metadata fetch.
- **Why:** Core Phase B integration — wires the auto-link flow into the details page lifecycle.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).
- **Note:** `entryMode` is NOT changed during merge — the entry was opened from an extension search result; auto-linking only enriches it with AniList metadata. `isExtensionOnly` becomes false (anilistId set), but `isFromExtension` stays true (entryMode stays EXTENSION).

### D-126 — ManualLinkSheet
- **What:** Bottom sheet for manual AniList linking. Layout: header "Link to AniList" + explanation text + search field (pre-filled with extension title) + search button + results list (AniList anime rows: cover + title + score + year + "Link" button) + "Skip AniList link" button. Auto-searches on open via LaunchedEffect. Full state handling (Idle/Searching/Empty/Results/Error).
- **Why:** User: "also the manual linking sheet is implemented properly." Needed when auto-link fails (NoMatch) or is disabled (Skipped). Lets the user pick the correct AniList entry by hand.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-127 — AutoLinkSettingsScreen
- **What:** New settings screen accessible from SettingsScreen hub → "Metadata" → "Auto-Link". Two sections:
  1. Global: master toggle (Switch) + strategy selector (Fuzzy/Strict/Manual segmented toggle) + threshold slider (0.50–1.00, only enabled when strategy=Fuzzy + global enabled).
  2. Per-extension: list of installed extensions, each with a 3-way toggle (Default/Always link/Never link). Uses the first source's ID as the key (most extensions have one source).
- **Why:** User: "make sure that the smart search functionality is implemented properly and per extension Auto-linking settings are kind of configured properly." Needed a dedicated settings UI for auto-link configuration.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-128 — DetailsScreen auto-link UI
- **What:** Added to DetailsScreen:
  1. Auto-link badge in the banner: "Linked to AniList" (check icon, primary color) when `isAniListLinked && isExtensionEntry`. "Auto-linking..." (12dp spinner + text) when `isAutoLinkSearching`.
  2. Three-dot menu: "Link to AniList" (if not linked) / "Unlink AniList" (if linked), with a divider. Only for extension entries.
  3. ManualLinkSheet wired: shows when `showManualLinkSheet` is true. Callbacks: onSearch → `searchAniListForLink()`, onLink → `linkAniListEntry()`, onSkip/onDismiss → `skipAniListLink()`.
- **Why:** User needs visual feedback during auto-link + a way to manually relink/unlink.
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-129 — AniListDetailsProvider registered as concrete type
- **What:** `anilistModule` now registers `AniListDetailsProvider` TWICE:
  1. As concrete type `single { AniListDetailsProvider(get()) }` — for direct injection into DetailsViewModel.
  2. As interface `single<AnimeDetailsProvider>(named("anilist")) { AniListDetailsProvider(get()) }` — for the provider registry.
- **Why:** DetailsViewModel needs `AniListDetailsProvider.mergeInto()` (concrete method, not on the interface). Koin can't inject a named-qualified interface as a concrete type. Following the same pattern as `ExtensionDetailsProvider` (which is also registered twice in extensionModule).
- **Status:** ✅ Implemented (Phase B, session web-f53f0459).
- **Date:** Phase B (session web-f53f0459).

### D-130 — Data-source priority + selector (AniList vs Extension)
- **What:** Added `DataSourcePriority` enum (ANILIST/EXTENSION) to `:core:common`. `AnimeDetailsProvider.mergeInto()` now takes a `priority` parameter:
  - `ANILIST`: AniList values overwrite extension values (when both non-null). Used when the user MANUALLY links (they explicitly want AniList data).
  - `EXTENSION`: Extension values are kept; AniList only fills nulls. Used for auto-link (non-intrusive).
  - `title`, `sourceId`, `sourceName`, `animeUrl`, `entryMode` are NEVER overwritten (identity fields).
- Added `dataSourcePriority` field to `UnifiedAnime` so the UI knows which mode is active.
- Added `switchDataSource(priority)` method to DetailsViewModel — re-merges with the new priority.
- Added `DataSourceSelector` composable to DetailsScreen — segmented toggle (AniList/Extension) shown only when both `anilistId` + `sourceId` are non-null (linked entry).
- **Why:** User: "it is giving the user the option to pick from which area he wants to see the details page data populated. Does he want to see the data populated from the AniList or does he want to see the data populated from the extension itself?" Previously, `mergeInto` used first-non-null-wins, so extension data blocked AniList data — the user couldn't see AniList's richer metadata even after linking.
- **Status:** ✅ Implemented (Phase B fix, session web-f53f0459).
- **Date:** Phase B fix (session web-f53f0459).

### D-131 — Stale metadata on details page switch
- **What:** `loadFromAniList()` and `loadFromExtension()` now reset ALL state flows before loading: `_linkedSource`, `_episodeState`, `_episodeMetadata`, `_resolverState`, `_resolvedVideosKey`, `_manualSearchState` (in addition to the already-reset `_state`, `_autoLinkState`, `_showManualLinkSheet`, `_anilistSearchState`).
- **Why:** User: "when I switched from one content details page to another content details page, the metadata which I was being shown was from the old one." The previous code only reset `_state` + auto-link state, leaving episode metadata + resolver state from the previous anime. This was especially visible for unlinked content (no anilistId to trigger a fresh metadata fetch).
- **Status:** ✅ Implemented (Phase B fix, session web-f53f0459).
- **Date:** Phase B fix (session web-f53f0459).

### D-132 — Per-extension override reactivity
- **What:** `AutoLinkSettingsScreen` — each `PerExtensionCard` now holds a local `mutableStateOf` snapshot keyed by `ext.pkgName`. Tapping a toggle updates the local state immediately (UI flips live) AND writes to `AutoLinkPreferences` for persistence.
- **Why:** User: "I set it to always link and never link. Apparently it was not updating the status of it in live view. I had to leave the auto link page and come back to it to see the changes being applied." The previous code read `prefs.getPerSourceOverride(sourceId)` once during composition — it wasn't reactive, so the UI didn't update until recomposition was triggered by something else.
- **Also:** Redesigned the entire AutoLinkSettingsScreen UI — split the single tall card into 4 separate cards (SwitchCard, StrategyCard, ThresholdCard, PerExtensionCard) with shorter subtitles + proper spacing + animated color transitions on the toggles.
- **Status:** ✅ Implemented (Phase B fix, session web-f53f0459).
- **Date:** Phase B fix (session web-f53f0459).

### D-133 — Single-wrapper-folder rule + sandbox recovery rule
- **What:** Two new CORE_RULES:
  - **§4 (Project Structure):** The repo root contains exactly ONE wrapper folder (`ANI-KUTA/`). All 4 project zones (`AGENT-CONTEXT/`, `APP/`, `DASHBOARD/`, `REFERENCES/`) live inside it — never directly at repo root. Exception: `.github/` stays at repo root (GitHub Actions constraint).
  - **§15 (Session-End Backup):** Added "Sandbox Recovery" subsection — if anything feels off (missing files, broken imports, stale state), STOP and re-clone from GitHub. Don't patch over a corrupted environment.
- **Repo reorganization:** Moved all 4 zones into `ANI-KUTA/` wrapper folder. Updated CI workflows (`build-apk.yml` + `deploy-dashboard.yml`) to use new paths (`ANI-KUTA/APP/ani-kuta/`, `ANI-KUTA/DASHBOARD/webpage/`). Updated `.gitignore` paths. Updated `README.md`.
- **Why:** User: "in the main directory, currently where all four folders are present, instead of all those four folders there will be a single folder in which all these things... will be present... This is going to be a core rule." + "if something feels off, the Github sandbox environment is off or something like that, then you will reclone the Github repository."
- **Status:** ✅ Implemented (Phase B fix, session web-f53f0459).
- **Date:** Phase B fix (session web-f53f0459).

### D-134 — Data source selector: fix reactivity + move to three-dot menu
- **What:** Fixed the data source selector bug where clicking "Extension" didn't update the UI (the extension data was overwritten by the previous ANILIST-priority merge and couldn't be recovered).
- **Root cause:** `mergeAniListIntoUnified` overwrote the base UnifiedAnime's fields with AniList data. Switching back to EXTENSION priority did `base.description ?: anilistData.description` — but `base.description` was already AniList's description (not null), so it stayed.
- **Fix:** Added `extensionBase: UnifiedAnime?` and `anilistBase: UnifiedAnime?` to DetailsViewModel. The displayed UnifiedAnime is always computed by `remergeBases(priority)` which merges the two original bases. Switching priority never loses data — both bases are preserved.
- **Also moved** the selector from the LazyColumn body to the three-dot DropdownMenu (per user: "when the user clicks the three-dot toggle at the very top, then at the very top of it it will show the user this kind of segmented toggle").
- **Also made** the selector available for AniList entries with linked sources (per user: "it will be available for any list entries too"). When an AniList entry links a source via `linkSource()`, `extensionBase` is created from the picked SAnime.
- **Also updated** `linkSource()`, `unlinkSource()`, `unlinkAniList()` to manage the bases (create/clear + re-merge).
- **Why:** User: "When I clicked the extension, nothing changed at all. Then I went back and reopened the page and I saw that the results were being shown from Any list." + "What it should be is that when the user clicks the three-dot toggle at the very top..." + "it will be available for any list entries too."
- **Status:** ✅ Implemented (Phase B fix, session web-f53f0459).
- **Date:** Phase B fix (session web-f53f0459).

### D-135 — Phase C plan v4 (final): content identity system scope + detail tables
- **What:** Finalized the Phase C plan with:
  - Content ID format v2: 6 sections (added `sourceId`). Format: `{dataSource}:{system}:{repoId}:{extensionPkg}:{sourceId}:{animeUrl}`. Uses repo DB ID instead of full URL (URL is too long + contains colons).
  - Session scope: ONLY content identity system (main table + detail tables + lookup tables). Watch progress/library/history/tracking DEFERRED.
  - Detail table approach: `anilist_details`, `extension_details`, `other_source_details` — each linked by mainId. Source-specific metadata lives in separate tables, not in the main content table.
  - Real repo URL format: `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json` (ends with index.min.json).
  - 10 confirmed decisions (Q-001 through Q-010).
- **Why:** User reviewed plan v3 and gave detailed feedback: (a) Content ID missing extension ID + source ID, (b) repo URL format clarified (ends with index.min.json), (c) one table per row on web page, (d) split session scope — focus on content ID system only, defer watch progress/library/history/tracking, (e) use separate detail tables per source type.
- **Status:** ✅ Plan finalized. Implementation pending (next session).
- **Date:** Phase C planning (session web-f53f0459).

### D-136 — Phase C implementation: content identity + library system
- **What:** Implemented Phase C (content identity system + library):
  1. New module `:core:content` with ContentIdGenerator, ContentRepository, ContentResolver, ContentSeeder.
  2. 8 SQLDelight tables: `data_source`, `system`, `extension_repo`, `extension`, `content`, `anilist_detail`, `extension_detail`, `other_source_detail`.
  3. 2 library tables: `library_category`, `library_item`. Default category is permanent (is_permanent=1).
  4. ContentResolver resolves anilistId/sourceId+animeUrl → mainId (UUID). Creates content record + detail record if not found.
  5. ContentRepository handles all CRUD + seeds lookup tables + Default category on first launch.
  6. DetailsViewModel: added contentResolver + contentRepository. Calls resolveContentForAniList/resolveContentForExtension on load. Added toggleLibrary() method + isInLibrary state.
  7. DetailsScreen: bookmark button wired to viewModel.toggleLibrary(). saved state from isInLibrary.
  8. LibraryViewModel: rewritten to use ContentRepository instead of PreferenceStore comma-separated IDs. Fetches content records + AniList data for display.
- **Content ID format change**: Uses FULL repo URL (not repo DB ID) per user request — the URL is essential for backup/restore + retrieving more extension IDs.
- **Why:** User: "repo ID should be the repository URL. I don't have any issues with that content ID being way too long... it should be the URL because that URL is essential if the user wants to get more extension IDs and so forth. We will need to set up a system to back up that repo URL too."
- **Subagent review**: All 18 files reviewed for compile errors — clean. No issues found.
- **Status:** ✅ Implemented (Phase C, session web-f53f0459).
- **Date:** Phase C (session web-f53f0459).

### D-137 — Cross-source content deduplication
- **What:** Fixed the duplicate library entries issue. When the user saved an anime from AniList, then opened the same anime from an extension, a SEPARATE content record was created — resulting in 2 library entries for the same anime.
- **Root cause:** `resolveContentForExtension()` didn't check the auto-link cache. It always created a new content record, even if the same anime was already saved from AniList. Additionally, `mergeAniListIntoUnified()` didn't persist the AniList link in the database — the ViewModel state was updated but the `anilist_detail` row was never created.
- **Fix:**
  1. `resolveContentForExtension()` now checks `autoLinkPreferences.getCachedAniListId()` first. If a cached anilistId exists AND a content record already exists for that anilistId → calls `contentResolver.linkExtensionToExisting()` to link the extension entry to the EXISTING mainId (no new content record).
  2. New `ContentResolver.linkExtensionToExisting()` method — updates the existing content record's extension fields + regenerates the contentId.
  3. `mergeAniListIntoUnified()` now calls `contentResolver.linkAniList()` to persist the link in the database (creates `anilist_detail` row + updates `dataSourceId`).
  4. `unlinkAniList()` now calls `contentResolver.unlinkAniList()` to persist the unlink.
- **Why:** User: "when I went to the library page, this time I apparently saw two entries. One was from the Anilist and the other one was from the extension side. This was apparently not supposed to happen."
- **Status:** ✅ Implemented (Phase C, session web-f53f0459). CI #197 green.
- **Date:** Phase C (session web-f53f0459).

### D-138 — Library categories system
- **What:** Implemented the library category system:
  1. **CategoryPickerSheet** — bottom sheet shown when the user long-presses the bookmark button on the details page. Lists all categories with checkboxes. User can toggle categories + create new ones.
  2. **Bookmark button** — now uses `combinedClickable` (onClick = toggle save, onLongClick = open category sheet).
  3. **ContentRepository** — added category CRUD: `getAllCategories`, `createCategory`, `deleteCategory`, `renameCategory`, `addToCategory`, `removeFromCategory`, `isInCategory`, `getCategoriesForContent`, `getMainIdsByCategory`, `countItemsInCategory`.
  4. **DetailsViewModel** — added category state flows + methods: `openCategorySheet`, `toggleCategory`, `createCategoryAndAdd`.
  5. **LibraryViewModel** — added category filtering (`selectCategory`), category management (`showCategoryManagement`, `deleteCategory`, `renameCategory`, `createCategory`). `loadLibrary()` now loads ALL categories + filters by selected category.
  6. **library.sq** — added `renameCategory` + `getCategoriesForContent` queries.
- **Default category** — permanent (is_permanent=1), cannot be deleted or renamed. User-created categories can be deleted/renamed.
- **Why:** User: "on the details page when he long presses on the save button, then a popup will show. In that popup he will see the list of all the categories and he can click on any of the categories and the anime will be added to those categories."
- **Status:** ✅ Implemented (Phase C, session web-f53f0459). CI #197 green.
- **Date:** Phase C (session web-f53f0459).
- **Note:** Library page category tabs UI (showing categories as tabs at the top) + long-press category tab for delete/rename — the ViewModel logic is ready, but the LibraryScreen UI update for tabs is pending. Will be done in the next iteration.

### D-139 — Cross-source dedup root cause fix + library crash + category tabs + STRICT switching
- **What:** Fixed multiple issues from user testing:
  1. **Cross-source dedup root cause**: `linkSource()` now caches the reverse mapping (sourceId, animeUrl) → anilistId via `autoLinkPreferences.cacheAniListId()`. When the same anime is opened from the extension later, `resolveContentForExtension` finds the cached anilistId → finds the existing content record → uses the SAME mainId. Also persists the extension link in the content database + fetches full extension details in the background.
  2. **Library crash fix**: `LibraryViewModel.loadLibrary()` now deduplicates by anilistId — if the same anime appears twice (from AniList + extension), only the first is kept. Prevents `Key "194829" was already used` LazyGrid crash.
  3. **CategoryPickerSheet → popup**: Changed from ModalBottomSheet to AlertDialog (centered popup) per user preference.
  4. **Data source selector STRICT switching**: `remergeBases()` changed from fallback (`primary.X ?: secondary.X`) to STRICT (`primary.X` only). When the user picks "Extension", they see ONLY extension data. When they pick "AniList", they see ONLY AniList data. No mixing. Fixes "the cover switched but nothing else changed."
  5. **Library page category tabs UI**: CategoryTabsRow at the top (horizontal scrollable pills). Shows when 2+ categories. Tapping filters by category. Long-press non-permanent pill → delete/rename dialog. "+" pill → create new category.
- **Why:** User tested D-137/D-138 and reported: (1) extension anime not showing as saved after auto-link, (2) library crash with duplicate keys, (3) category picker should be a popup not bottom sheet, (4) data source selector only switches cover, (5) library page needs category tabs.
- **Status:** ✅ Implemented (Phase C, session web-f53f0459). CI #199 green.
- **Date:** Phase C (session web-f53f0459).

### D-140 — Library crash + 404 + live reload + category tabs + data source selector restore
- **What:** Fixed multiple issues from user testing:
  1. **Library crash (Key "0" already used)**: Created `LibraryEntry` data class with `mainId` (stable UUID) as the key. LibraryViewModel builds `LibraryEntry` from content records. LibraryScreen uses `it.mainId` as the LazyGrid key (was `it.id` = anilistId, which was 0 for extension-only entries).
  2. **404 error**: LibraryScreen's `onNavigateToDetails` now takes `LibraryEntry`. MainActivity checks `entry.hasAniListId` → navigate via AniList. If not → navigate via Extension (sourceId + animeUrl + title + coverUrl).
  3. **Library live reload**: Added `LaunchedEffect(Unit) { viewModel.loadLibrary() }` in LibraryScreen.
  4. **Data source selector disappearing on reopen**: `loadLinkedSource()` now restores `extensionBase` from the content database (`extension_detail` table). If not found, creates a minimal one + fetches full details in the background.
  5. **Category tabs smart features**: "All" only shows if 2+ categories have items. "Default" only shows if it has items. No "+" button. Text+underline style (not bubbles). Optional category counts via `showCategoryCounts` setting.
  6. **Long-press category tab**: Rename / Delete (with 3 options: Cancel / Delete / Move to Default). Default category: long-press does nothing.
  7. **Library header**: Shows total entries count ("15 in Library") as subtitle.
  8. **CategoryPickerSheet**: Removed lock icon. New category auto-selected.
  9. **New settings toggle**: "Show category counts on tabs" in CustomizeSheet.
- **Why:** User reported: library crash with extension-only entries, 404 error, library not updating live, data source selector disappearing on reopen, category tabs UI bad (bubbles), missing smart features, missing delete-with-move-to-default.
- **Status:** ✅ Implemented (Phase C, session web-f53f0459). CI #201 green.
- **Date:** Phase C (session web-f53f0459).

### D-141 — Library UI fixes + multi-select + refresh + cover images + Phase D plan
- **What:** Fixed multiple library UI issues + added multi-select mode + fixed refresh button + wrote Phase D plan:
  1. **Extension-only cover images fix**: Fixed `coverUrl` assignment in LibraryViewModel (was `extDetail?.thumbnailUrl ?: content.description?.let { null }` which always returned null).
  2. **Library heading**: Changed from "Library" + subtitle to "X in Library" as the main heading (when showTotalEntries is on).
  3. **Category count format**: Changed from "Default (3)" to "[3] Default" — count on LEFT, square brackets.
  4. **Delete dialog**: "Move to Default" only shows if category has entries. "Delete" on right, "Cancel" on left, "Move to Default" centered.
  5. **White spacer line** below category tabs.
  6. **In-memory cache**: Added `anilistCache` to prevent re-fetching AniList data on every tab switch. `selectCategory` uses `reloadFromCache()` instead of `loadLibrary()`.
  7. **Multi-select mode**: Long-press a library entry → selection mode. Header shows "X selected". Quick options (Select All / Clear / Invert). Bottom bar: Cancel / Category / Delete. Category picker popup with checkboxes. Delete confirmation dialog.
  8. **Refresh button**: DetailsViewModel.refresh() — re-fetches from AniList or extension. Wired to the three-dot menu "Refresh" item (was a no-op).
  9. **Phase D plan**: Written to `DOCUMENTATION/planning/data-management/PHASE-D-PLAN.md`. Covers: local metadata cache, browse page cache + refresh, details page multi-stage refresh, image caching, backup/restore, library performance.
- **Why:** User reported: extension-only cover images not showing, library heading wrong, category count format wrong, delete dialog formatting, library performance (re-fetching on tab switch), no multi-select, refresh button doesn't work.
- **Status:** ✅ Implemented (Phase C, session web-f53f0459). CI #204 green. Phase D plan written.
- **Date:** Phase C (session web-f53f0459).

### D-142 — Extension cover images + multi-select UI + Phase D plan v2
- **What:** Fixed extension-only cover images (root cause: extension_detail not stored on content creation), changed category count to rounded brackets, styled multi-select buttons with icons, made selection bottom bar replace the nav bar, updated Phase D plan v2 (removed backup/restore, metadata never expires, 6hr homepage only, vibration, solid caching, two source types).
- **Status:** ✅ Implemented. CI #206 green.
- **Date:** Phase C (session web-f53f0459).

### D-143 — Bottom nav bar replacement + library total count
- **What:** 
  1. Bottom nav bar replacement: Added `selectionModeContent` parameter to `AnikutaBottomNavBar`. Created `LibrarySelectionMode` class + `LocalLibrarySelectionMode` CompositionLocal. LibraryScreen syncs selection state via `LaunchedEffect`. AppRoot reads it and passes `SelectionActionBar` (Cancel/Category/Delete with icons) to the nav bar — replacing the nav pills INSIDE the floating pill, not overlaying.
  2. Library header total count: `totalEntries` now shows the TOTAL across ALL categories (fetches `getLibraryMainIds()` separately), not just the selected category.
- **Status:** ✅ Implemented. CI #210 green.
- **Date:** Phase C (session web-f53f0459).

### D-144 — Phase D.1: Local metadata cache implemented
- **What:** Implemented the local metadata cache system (Phase D.1):
  1. New module `:core:data-cache` with DataCacheRepository.
  2. 3 new SQLDelight tables: `anime_metadata_cache`, `data_cache_episode` (renamed from `episode_metadata_cache` to avoid collision with existing `metadata.sq`), `browse_cache`.
  3. DetailsViewModel.loadFromAniList() checks cache first → displays instantly → then fetches from network → updates cache.
  4. LibraryViewModel.loadLibrary() checks cache first → no network on tab switch.
  5. LibraryViewModel.reloadFromCache() uses cache exclusively.
  6. Metadata never expires — user manually refreshes.
  7. All data persists across restarts (SQLite via SQLDelight).
- **CI fixes:** SQLDelight unique index on episode table, table name collision with metadata.sq, missing closing brace in reloadFromCache.
- **Status:** ✅ Implemented. CI #216 green.
- **Date:** Phase D (session web-f53f0459).

### D-145 — Phase D.2-D.5 complete: browse cache, multi-stage refresh, image cache, library pull-to-refresh
- **What:** Implemented all remaining Phase D items:
  1. **D.2: Browse page cache + pull-to-refresh** — BrowseViewModel reads from browse_cache first → instant display. If expired (6h) → background fetch. Pull-to-refresh with vibration + visual indicator.
  2. **D.3: Details page multi-stage refresh** — refreshEpisodesList() (episodes only), refreshMetadata() (metadata only + cache update), refreshAll() (both). Three-dot menu "Refresh" calls refreshAll(). RefreshStage enum + RefreshState sealed interface for future scroll-based triggers.
  3. **D.4: Coil disk cache** — ImageLoaderFactory with 500MB persistent disk cache + 25% memory cache. Registered as Coil singleton. Images survive restarts.
  4. **D.5: Library pull-to-refresh** — refreshLibrary() clears cache + re-fetches. Pull-to-refresh with vibration + visual indicators.
- **CI fixes:** Added Coil deps to :app, fixed Coil 3 API (setSafe takes Factory lambda, directory takes okio.Path).
- **Status:** ✅ Implemented. CI #220 green.
- **Date:** Phase D (session web-f53f0459).

### D-146 — Multi-select category picker + cache-first details + offline mode + refresh feedback
- **What:** Fixed 4 issues from user testing:
  1. Multi-select category picker auto-closes → now allows multiple selections + Done button.
  2. Details page re-fetches from network on reopen → now skips network if cache exists.
  3. Offline mode shows error → now shows cached data when network fails.
  4. Refresh button no visual feedback → added "Refreshing..." overlay with spinner.
- **Status:** ✅ Implemented. CI #223 green.
- **Date:** Phase D (session web-f53f0459).

### D-147 — Episode caching + offline extension fallback
- **What:** 
  1. Episode list caching: `fetchEpisodes()` now checks `data_cache_episode` table first → instant display from cache → no network on reopen. After network fetch → caches episodes locally. After episode metadata fetch → updates cache with enriched metadata.
  2. Offline extension fallback: `loadFromExtension()` catches network failures → calls `tryCachedExtensionData()` → loads from content DB (extension_detail, anime_metadata_cache, data_cache_episode). Extension-only anime now shows full details + episodes when offline.
- **Status:** ✅ Implemented. CI #224 green.
- **Date:** Phase D (session web-f53f0459).

### D-148 — Download System architecture (Phase DL.0-DL.8) — substantially implemented
- **What:** The download system is implemented on the `download-system-plan` branch (41 commits ahead of `main`). Key architecture decisions (confirming the `download-research/` plan):
  1. **`DownloadStatus` is an enum** (not a sealed interface) — REVIEW-5 §2.1 settled the 3 competing doc definitions; the enum is the implemented one.
  2. **Storage: SAF + `.data.json`** — downloads go to a user-selected SAF tree under `<root>/{video,images,text}/<Title>/{data.json, <Title> - E00001.mp4}`. The `data.json` file is the durable source of truth for reinstall recognition; the `downloaded_episode` SQLDelight table is a cache/index reconciled by `DownloadScanner` on startup. 5-digit zero-padded episode keys. Same-title collision handling via content FORMAT folders.
  3. **DB re-keyed by `main_id` + `episode_key`** — the `downloaded_episode` table uses the content-identity keys (not the old project's extension-specific keys), so a download survives an extension swap.
  4. **Offline playback via `content://` → `fd://`** — MPV cannot read SAF `content://` URIs directly. `DownloadStorageProvider` opens a `ParcelFileDescriptor` and passes `fd://<n>` to MPV, with a 500ms surface-readiness delay to avoid SIGABRT (DL-CRITICAL-FIX3, `1f85339`).
  5. **`AutoDownloadEngine` is a 5-step pure-function pipeline** — `flatten → rank → applyFallbacks → pick → globalFallback`, with user-configurable `dimensionPriority` (server / quality / audio). Pure functions for testability.
  6. **Foreground service: `foregroundServiceType="dataSync"`** + 2 notification channels (download progress + download complete). `NetworkCallback` auto-pause on lost connectivity + resume on restore. `onTimeout` (API 35+) + `onTaskRemoved` restart.
  7. **`HttpDownloader`** — Range-request resume, validation, HLS re-detection. `HlsDownloader` — pure Kotlin (no encrypted HLS support).
  8. **Schema evolution via `DatabaseDriverFactory.onOpen`** — idempotent `ALTER TABLE` overrides, no `.sqm` migration files (existing dev installs must wipe app data once per REVIEW-5 M1+M2).
- **Status:** ✅ Substantially implemented (DL.0-DL.8). ⚠️ Known gaps: proxy-churn not wired (D-149); `DownloadVideoPickerSheet` not wired; outer retry loop not implemented (max 2 attempts, spec 6); `127.0.0.1` guard missing; `video_uri`/`video_url` column bug.
- **Why:** The download system is the app's headline missing feature; implemented per the 5-round-reviewed `download-research/` plan.
- **Date:** `download-system-plan` branch (DL-D0 `5849e13` → METADATA-FIX-v2 `234ea15`). Documented retroactively in the analysis-and-doc-update session (the implementation commits never recorded their own decision entries — this closes that gap).

### D-149 — Proxy-churn re-resolve: BUILT but NOT WIRED (known gap, deferred)
- **What:** The proxy-churn re-resolve fix (Phase DL.2) is implemented in code but is **silently disabled**:
  1. `HttpDownloader` (`core/download/.../HttpDownloader.kt:59`) has a `private val reResolver: ReResolver? = null` constructor param.
  2. `DownloadModule.kt:92` constructs `HttpDownloader` with `reResolver = null`, with a comment: "wired in D.2 via the :app module's downloadAppModule".
  3. **No such `downloadAppModule` exists** (grep returns 0 matches — only the comment itself).
  4. The `:app` `ReResolver` class (`app/.../download/ReResolver.kt`) IS registered in Koin (`AnikutaApp.kt:154`) but is never injected into `HttpDownloader`.
  5. The retry path (`HttpDownloader.kt:261-268`) is gated by `reResolver != null` → currently **dead code**.
  6. **Signature incompatibility**: `HttpDownloader.ReResolver` (fun interface, `:core:download`) takes `resolveContextJson: String` → `ReResolvedVideo?`; the `:app` `ReResolver` takes typed `(ResolveContext, AnimeHttpSource, SEpisode)` → `ResolverVideo?`. An adapter is required, not just a Koin binding.
- **Impact:** Downloads of episodes whose video URL is a `http://localhost` proxy URL (AniKotoS and similar extensions spin up a local NanoHTTPD proxy per `getHosterList()` call — see D-066) will **fail when the proxy URL churns**, instead of re-resolving + retrying. The user sees a download error.
- **Two related bugs found during analysis** (fix alongside wiring):
  1. `HttpDownloader.kt:261` only checks `url.startsWith("http://localhost")` — but AniKotoS uses `127.0.0.1` (D-092). Add `127.0.0.1` to the guard.
  2. `HttpDownloader.kt:271` writes the fresh URL to the `video_uri` column, but the download read path uses `video_url`. A `DownloadStore.updateDownloadVideoUrl` query must be added.
- **Wiring plan** (deferred per user — full detail in sandbox `ani-kuta-analysis/04-proxy-churn-explanation.md`):
  1. Create a ~50-line adapter class in `:app` implementing `HttpDownloader.ReResolver`: deserialize `resolveContextJson` → `ResolveContext`, look up `AnimeHttpSource` via `ExtensionManager.getSource(sourceId)`, reconstruct a minimal `SEpisodeImpl` (only `url` needed), call the `:app` `ReResolver.reResolve(...)`, map `ResolverVideo` → `ReResolvedVideo`.
  2. Register via Koin: `single<HttpDownloader.ReResolver> { ReResolverAdapter(get(), get()) }` (in `:app`'s `appModule`, NOT a new `downloadAppModule` — minimal change).
  3. Change `DownloadModule.kt:92` from `reResolver = null` to `reResolver = getOrNull<HttpDownloader.ReResolver>()` (optional/lazy — keeps `:core:download` independent of `:app`).
  4. Fix the two bugs above.
  5. Logging per CORE_RULES §20 (`Anikuta:Core:Download` tag).
  6. Verify on device: trigger a localhost-URL download, kill the proxy, confirm re-resolve fires + download recovers.
- **Status:** ⚠️ NOT WIRED. Deferred per user (awaiting go-ahead). Inner cap (`MAX_RE_RESOLVE_ATTEMPTS = 1`) IS implemented; outer retry loop is NOT (`setRetryingStatus` is dead code, `RetryPolicy` class doesn't exist — actual max attempts = 2, spec says 6).
- **Why:** The DL.2 commit (`6382dbe`) built the types + the `:app` ReResolver, but the adapter wiring was never completed. The code comments are honest about this ("wired in D.2") but `progress.md` didn't capture the gap until this session.
- **Date:** Discovered + documented in the analysis-and-doc-update session.

### D-150 — Navigation: KEEP hand-rolled NavKey backstack (do NOT migrate to Nav3)
- **What:** The app's navigation uses a hand-rolled backstack — `mutableStateListOf<NavKey>` + a `when(currentKey)` dispatch in `MainActivity.kt`. `NavKey` is the project marker `com.confused.anikuta.core.navigation.NavKey`, NOT `androidx.navigation3.NavKey`. Nav3 1.1.5 is declared as a dependency in 7 `build.gradle.kts` files but has ZERO `androidx.navigation3.*` imports — it is unused dead weight on the classpath (and is the reason `compileSdk` was bumped to 36). **Decision: keep the hand-rolled navigation as-is. Do NOT migrate to Nav3.**
- **Accepted limitation:** the hand-rolled backstack does NOT survive process death (held in `remember { }`, not `rememberSaveable`). This means R7 — the back-stack-recreation-on-process-death benefit that motivated choosing Nav3 in `12-nav-research.md` — is **NOT realized**. If the OS kills the app process while the user is several screens deep, the backstack is lost and the app reopens at the root screen. The manifest's `configChanges` mitigates configuration changes (rotation, theme toggle — Activity is not recreated), but NOT process death. This is accepted as a known limitation.
- **Why:** (1) The hand-rolled nav works well for the current 16 screens; the `when`-dispatch is simple and agent-friendly. (2) Migrating to Nav3 is medium-large effort (~2-4 hours + CI) with a real risk — `WatchKey` has 13 fields / 4 large pre-serialized strings that could approach the 1MB `TransactionTooLargeException` Bundle limit. (3) No forcing function (deep links R6 / dynamic tabs R3) is on the near-term roadmap. (4) The user explicitly decided to keep hand-rolled.
- **Nav3 dependency:** fully REMOVED from all build files (libs.versions.toml + all module build.gradle.kts). Zero `androidx.navigation3.*` imports remain. compileSdk stays at 36 (reverting provides no benefit — see D-008).
- **If R7 becomes important later:** the smallest fix is a hybrid — swap `remember { mutableStateListOf<NavKey>(...) }` → `rememberSaveable(saver = listSaver(...))` using kotlinx.serialization polymorphism (~1-2 hours, fixes R7 only without a full Nav3 migration). Sketch in sandbox `ani-kuta-analysis/03-nav3-comparison.md` Option C.1.
- **Status:** ✅ Decided (keep hand-rolled). Docs updated: `12-nav-research.md` (Resolution note at top), `progress.md` (What's Next item 3 → decided), `master.md` + `SESSION.md` (open items). Resolution recorded in `12-nav-research.md`.
- **Date:** analysis-and-doc-update session.

### D-151 — Download system future-phase gaps (consolidated deferred plan)
- **What:** Consolidated the deferred download-system gaps into a single reference doc (`download-research/FUTURE-PHASE-DL-GAPS.md`) so a future phase can pick them up with full context. The items:
  1. **Proxy-churn re-resolve wiring** (D-149) — built but not wired; needs a ~50-line adapter in `:app` + Koin binding + `DownloadModule.kt` change.
  2. **Two bugs in the (dead) re-resolve path** — (a) `HttpDownloader.kt:261` guard misses `127.0.0.1` (AniKotoS uses it, per D-092); (b) `HttpDownloader.kt:271` writes fresh URL to `video_uri` but read path uses `video_url`. Both are in dead code today (zero functional effect) — fix alongside Item 1.
  3. **`DownloadVideoPickerSheet`** — NOT a user-facing bug (corrects earlier analysis). The download works via the ResolverSheet → `handleDownloadSpecificVideo`. The `DownloadVideoPickerSheet` is a separate, redundant sheet for the rare `EnqueueResult.ShowPicker` (ASK fallback) case, logged-only. Options: delete (recommended) or wire for the ASK case.
  4. **Outer automatic retry loop** — `RETRYING` state + `setRetryingStatus()` + `retry_attempt`/`retry_max_attempts` columns + `DEFAULT_RETRY_MAX=3` all EXIST, but `setRetryingStatus` is never called (dead code) and `RetryPolicy` class is referenced in KDoc but doesn't exist. Failed downloads go straight to ERROR (manual retry via `retryDownload()` works). Needs: `RetryPolicy` class (which exceptions retry: 5xx+IOException yes, 4xx+validation no) + outer loop in `launchDownload`'s catch block + backoff strategy + notification UX. 6 design decisions need user input (documented in the future-phase doc).
- **Why deferred (per user):** these are a longer task; downloads work today for non-churning URLs + manual retry recovers transient errors. Grouping them into one future phase ensures they compose correctly (proxy-churn is the inner retry; the outer loop wraps it) and can be device-tested together.
- **Recommended future-phase scope:** Items 1+2+4 together (share the catch-block + RETRYING state + device-test pass) + Item 3 (delete, ~15 min). Estimated ~6-8 hours total.
- **Status:** ⚠️ All DEFERRED per user. Not started. Full plan + adapter sketch + RetryPolicy sketch + design questions in `download-research/FUTURE-PHASE-DL-GAPS.md`. Sandbox deep-dive: `ani-kuta-analysis/04-proxy-churn-explanation.md`.
- **Date:** analysis-and-doc-update session.

### D-152 — D-FIX-SUB: Downloaded-episode subtitle fixes (5 issues)
- **What:** Fixed 5 issues with how downloaded episodes' subtitles are saved, named, and loaded offline. All found during a focused subtitle investigation (this session).
  1. **`subtitleUris` was never populated (CRITICAL).** `HttpDownloader.download()` returned `task.copy(videoUri=...)` but NOT `subtitleUris`. `DownloadStorageProvider.publishVideoFile` returned only the video URI string, discarding the subtitle content:// URIs. → `completedTask.subtitleUris` was always null → DB stored null → **offline playback had no subtitles** (files existed on disk but nobody knew their URIs). **Fix:** added `PublishResult(videoUri, subtitleUris)` return type; `HttpDownloader` now serializes the subtitle URIs to JSON on the task.
  2. **`downloadSubtitlesToCache` sent NO headers.** Built `Request.Builder().url(track.url).build()` with no headers → subtitle fetches 403'd on protected CDNs (Referer/UA required) + were silently skipped. The streaming-side `SubtitleEngine` already handled headers; the download side didn't. **Fix:** added `applyTrackHeaders()` (parses MPV comma-format `"Key: Value,Key2: Value2"` — matches `VideoResolver.formatHeaders`) + a User-Agent fallback.
  3. **`DownloadTrack` had no `headers` field.** Even if #2 were fixed, the model couldn't carry headers from resolver to downloader. **Fix:** added `headers: String? = null` to `DownloadTrack`. `DownloadOrchestrator.buildRequest` now passes the video's `videoHeaders` as a fallback for each subtitle/audio track (subtitles from the same source usually need the same headers).
  4. **Subtitle naming was index-based, not language-based.** Files were `.subtitle_E00001_0.srt` — the `lang` was lost. Offline, the subtitle picker showed "Subtitle 1", "Subtitle 2" (MainActivity). **Fix:** naming is now `.subtitle_E{00001}_{lang}_{index}.{ext}` (lang sanitized to `[a-z0-9-]`, `unknown` if blank). `MainActivity.extractSubtitleLangFromUri()` parses the lang from the filename + title-cases it → picker shows "English" / "Japanese". Legacy filenames still recognized by the scanner for backward compat.
  5. **`DownloadScanner` set `subtitleUris = emptyList()` on reinstall.** On app reinstall / re-scan, subtitle files existed on disk but the scanner didn't re-discover them → offline subtitles lost after reinstall. **Fix:** added `findSubtitleUrisForEpisode()` to the scanner — finds `.subtitle_E{num}_*.{ext}` files (new + legacy naming) in the folder + repopulates `subtitleUris` in track order.
- **Sub-agent reviewed (SUB-REVIEW):** COMPILES, no issues. Reviewer caught a logic bug (header format was JSON in my first pass but the actual format is MPV comma-string) — fixed before push.
- **Files changed (6):**
  - `core/download/.../DownloadModels.kt` (DownloadTrack.headers + PublishResult)
  - `core/download/.../DownloadStorageProvider.kt` (return PublishResult + lang-based naming + sanitizeLangForFileName)
  - `core/download/.../HttpDownloader.kt` (consume PublishResult + set subtitleUris + applyTrackHeaders + UA fallback)
  - `core/download/.../DownloadScanner.kt` (findSubtitleUrisForEpisode + SUBTITLE_EXTENSIONS)
  - `app/.../MainActivity.kt` (extractSubtitleLangFromUri for the picker label)
  - `app/.../download/DownloadOrchestrator.kt` (pass videoHeaders as fallback for subtitle/audio tracks)
- **Status:** ✅ Implemented. Sub-agent reviewed. Awaiting device verification (see `APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md` section C).
- **Note:** existing downloads (made before this fix) will have the OLD subtitle naming + null `subtitleUris` in the DB. They'll show "Subtitle N" labels. Re-downloading fixes them. The scanner's backward-compat means the subtitle FILES are still found after reinstall, but the labels will be "Subtitle N" for old-named files.
- **Date:** analysis-and-doc-update session (subtitle investigation task).

### D-153 — Swipe-to-reveal background uses matchParentSize (NOT fillMaxSize)
- **What:** Fixed the "swipe background completely gone" bug in `DetailsScreen.EpisodeRow` + `HistoryScreen.HistoryRow`. The reveal background (tinted Surface + icon) was invisible because it used `Modifier.fillMaxSize()` inside the swipe-wrapper `Box`, which only declares `fillMaxWidth()` and wraps its content height (no bounded height constraint). In Compose, `fillMaxSize()` with an unbounded max-height constraint resolves to **0 height** → the background was never visible. **Fix:** use `matchParentSize()` (a `BoxScope` modifier) — it sizes the background to the wrapper Box's footprint *after* the card (the size-defining sibling) is measured. Also: always compose the background (removed the `if (iconAlpha > 0f)` guard) and drive visibility purely via `graphicsLayer { alpha }`, fading in linearly with swipe progress.
- **Why the previous "fix" made it worse:** the prior session changed `matchParentSize` → `fillMaxSize` (commit `db26c47`, "fix: swipe bg fillMaxSize") based on the wrong theory that "matchParentSize fails because the background is a sibling, not a parent." That theory is **false** — `matchParentSize` is *designed* for siblings within a Box (it's a `BoxScope` member). The change from matchParentSize → fillMaxSize is exactly what broke it (0 height).
- **Status:** ✅ Implemented + CI green (run 31275021179). Both Details + History screens.
- **Date:** swipe/calendar/notifications session.

### D-154 — ScheduleListContent: toggle + content must be in a Column (Box stacks siblings)
- **What:** Fixed the "can't click the calendar button" bug. `ScheduleListContent` emitted the List/Calendar toggle `Row` and the list/calendar content as **sibling composables** directly into the parent `Box` (from `UpdatesScreen`). A `Box` stacks children in declaration order — later children draw *on top* of earlier ones. The list/calendar content (with `fillMaxSize`) was declared *after* the toggle, so it drew **on top of the toggle**, fully covering it. The toggle was rendered but invisible and untappable. **Fix:** wrap the toggle `Row` + the content in a `Column` so the toggle sits *above* the content (vertical stacking, not z-stacking).
- **Also:** (a) auto-fetch schedule data once on first open if the DB is empty (otherwise the calendar renders empty until manual refresh — looked like "nothing happened"); (b) wrap the calendar in `verticalScroll` so it's never cut off on short screens; (c) add an empty-state hint; (d) gate the Updates-driven `ScrollBlurOverlay` to the Updates tab (it read the Updates `listState` even on the Schedule tab, producing a stale scrim over the toggle).
- **Status:** ✅ Implemented + CI green. Calendar toggle now visible + clickable; calendar auto-populates.
- **Date:** swipe/calendar/notifications session.

### D-155 — Notification settings UI (Phase NOTIF — UI completion)
- **What:** Built the notification settings screen + supporting preferences, completing the NOTIF phase (store + manager were already done; UI was the known gap). Three layers:
  1. **`NotificationPreferences`** (`:core:preferences`, new) — global master kill switch + default trigger/audio prefs (seeded into per-anime config on enable). Reactive via `*Flow()` accessors.
  2. **`NotificationManager`** now respects the global master toggle (`:core:notifications` gains a `:core:preferences` dep; checks `preferences.notificationsEnabled` first in `postNotification`).
  3. **`NotificationsSettingsScreen` + `NotificationsSettingsViewModel`** (`:app`, new) — master toggle, "New anime defaults" section (5 toggles via `SettingsGroupCard`), and a per-anime library list: each row has a Switch + a tappable detail bottom sheet (per-trigger + sub/dub toggles). Reached via a new "Notifications" nav row in `SettingsScreen` + `NotificationsKey` in `MainActivity`.
- **Why per-anime + defaults:** the existing `NotificationConfigStore` is per-content (DB-backed). The defaults let the user set a baseline; enabling a specific anime seeds from defaults, then per-anime overrides are tweakable. The master toggle is the hard kill switch (checked before any per-anime config).
- **Status:** ✅ Implemented + CI green. ViewModel registered via `viewModelOf` in `appModule`.
- **Date:** swipe/calendar/notifications session.

### D-156 — CI was falsely reported green (DocumentFile dep missing in :app)
- **What:** Discovered that the previous session's commits (`db26c47`, `fd1a9a5`) **failed CI** (`:app:compileDebugKotlin` — "Cannot access class 'DocumentFile'"), but `progress.md` claimed "CI green". The `scanSubtitleFilesOnDisk` function (added in `db26c47`, the subtitle disk-scan fallback) uses `DocumentFile` directly in `:app`, but `:core:download` declares `androidx.documentfile` as `implementation` (not `api`), so it isn't transitively visible to `:app`. **Fix:** added `implementation(libs.androidx.documentfile)` to `:app/build.gradle.kts`.
- **Lesson:** always verify CI *actually* passed (poll the run + check conclusion), never trust a self-reported "green" in docs. The false claim meant the user downloaded a build from *before* the failing commits (or no build at all for the latest).
- **Status:** ✅ Fixed + CI green (run 31275021179, artifact `anikuta-apk` 53 MB). Feature branch `feature/watch-progress-history-updates` deleted (was fully merged into main; main now verified green).
- **Date:** swipe/calendar/notifications session.

### D-157 — Calendar toggle restyle + icons + Today button + smooth height animation
- **What:** Four calendar UX improvements per user feedback:
  1. **Toggle restyle:** the List/Calendar toggle now matches the Updates | Schedule pill exactly — same container `Surface(surfaceVariant.copy(alpha=0.5f))` + per-tab `Surface` with primary/transparent colors. Previously it was a bare `Row` of Surfaces with no surrounding pill (looked inconsistent).
  2. **Icons:** `Icons.AutoMirrored.Filled.List` before "List", `Icons.Filled.CalendarMonth` before "Calendar" — both tinted with the tab's text color.
  3. **"Today" button:** in calendar view, the toggle pill shrinks to `weight(1f)` (left) and a "Today" button (`Icons.Filled.Today` + label, primary-tinted) appears on the right. Tapping it animates the pager to `initialPage` (the current-month page, which contains today). Wired via a `scrollToTodayRequest: Int` counter that `ScheduleCalendarContent` observes with a `LaunchedEffect` → `pagerState.animateScrollToPage(initialPage)`. Counter pattern (not a lambda) because the pager state lives inside `ScheduleCalendarContent` and the button lives in the toggle bar above it.
  4. **Smooth height animation:** the calendar grid height (which varies by the month's week count: 5 or 6 rows) now animates via `animateDpAsState` (spring, `DampingRatioNoBouncy` + `StiffnessMediumLow`) instead of jumping abruptly when the displayed month changes.
- **Status:** ✅ Implemented + CI green (run 31277015651, commit b55da53).
- **Date:** calendar/notifications-3way session.

### D-158 — Notification triggers + audio upgraded to tri-state (3-way segmented toggles)
- **What:** Replaced the boolean Sub/Dub/trigger switches with 3-way segmented toggles (matching the download-settings "Best effort / Ask / Don't" style):
  - **Audio:** `AudioPref` enum (SUB / DUB / BOTH), derived from the two existing DB booleans (`notify_sub` + `notify_dub`). No schema change — SUB=(1,0), DUB=(0,1), BOTH=(1,1); (0,0) maps to BOTH as a sensible default.
  - **Triggers** (on schedule / on watchable / on immediate): `TriggerState` enum (ON / SILENT / OFF), stored as INTEGER 0/1/2 — backward-compatible with the old boolean data (0=OFF, 1=ON; 2=SILENT is new). The DB column type stays INTEGER (no migration needed — `CREATE TABLE IF NOT EXISTS` won't recreate, and old 0/1 values remain valid).
  - **Silent notifications:** `NotificationManager` now has a second channel (`anikuta_new_episodes_silent`, IMPORTANCE_LOW). SILENT triggers post there with `PRIORITY_LOW` (no sound); ON uses the default channel.
  - **Adapting descriptions:** each toggle row's description changes with the selection — e.g. "Notify for sub releases only" / "Notify for dub releases only" / "Notify for sub and dub releases"; "Notify when the airing time is reached" / "Notify silently when…" / "Don't notify when…".
- **Why On/Silent/Off for triggers:** the user explicitly wanted a 3-way for triggers (not just on/off). On/Silent/Off is the cleanest 3-state with genuinely different behavior (silent = no sound) + clearly different descriptions. Designed per the user's "set up properly according to our needs" latitude.
- **Circular-dep fix:** `NotificationConfig` + the two enums moved to `:core:common` (package `com.confused.anikuta.core.notifications`) — `:core:preferences` needed to reference `TriggerState`/`AudioPref`, but `:core:notifications` already depends on `:core:preferences`, so putting them in `:core:notifications` would create a cycle. `:core:preferences` now depends on `:core:common`.
- **Reusable component:** `SegmentedToggle` (in `:app/settings`) — same visual as download-settings' SegmentedRowLocal.
- **Status:** ✅ Implemented + CI green. Three CI iterations (AudioPref `subBoolean`/`dubBoolean` were in companion → moved to instance; `store.getInt` default needed `.toInt()` on the Long `dbValue`; `var by remember{mutableStateOf}` needed `setValue` import).
- **Date:** calendar/notifications-3way session.

### D-159 — Notifications defaults hide smoothly when master toggle is off
- **What:** When the "Enable notifications" master toggle is off, the entire "New anime defaults" section now hides via `AnimatedVisibility` (`fadeIn() + expandVertically()` / `fadeOut() + shrinkVertically()`) — a smooth collapse, not an abrupt disappearance. Wrapped in a `Column` inside the LazyColumn item because `AnimatedVisibility`'s `ColumnScope` overload needs a `ColumnScope` receiver (a `LazyItemScope` doesn't provide one).
- **Status:** ✅ Implemented + CI green.
- **Date:** calendar/notifications-3way session.

### D-160 — Dedicated Notifications Library page (category filter + per-anime advanced config)
- **What:** Moved the per-anime library list out of the Notifications settings screen into a dedicated `NotificationsLibraryScreen` (reached via a "Library" nav row at the bottom of the settings screen). The library page has:
  1. **Category filter chips** (`LazyRow`): "All" + every `LibraryCategory` the user has. Selecting a category filters the list via `ContentRepository.getMainIdsByCategory(id)`.
  2. **Per-anime list:** each row = cover + title + a `Switch` (enable/disable for that anime) + a chevron. Tapping the row (or chevron) opens the advanced-config sheet.
  3. **Advanced-config bottom sheet:** per-anime tri-state triggers (On/Silent/Off) + audio (Sub/Dub/Both), each with an adapting description. The sheet holds a local working snapshot (`mutableStateOf(config)`) + persists on every change via `updateAnimeConfig`.
- **New ViewModel:** `NotificationsLibraryViewModel` (registered via `viewModelOf` in `appModule`) — owns categories, selected category, items, the open-item state, and the config-write methods.
- **Nav:** `NotificationsLibraryKey` wired in `MainActivity`; the settings screen passes `onOpenLibrary = { backstack.add(NotificationsLibraryKey) }`.
- **Status:** ✅ Implemented + CI green.
- **Date:** calendar/notifications-3way session.

### D-161 — Crash fix: migrate legacy Boolean notification trigger prefs to Int
- **What:** Fixed the `ClassCastException: java.lang.Boolean cannot be cast to java.lang.Integer` crash that occurred ~1s after opening the Notifications settings page. ROOT CAUSE: D-158 upgraded the 3 trigger defaults (`notif_def_schedule` / `_watchable` / `_immediate`) from Boolean to Int storage but kept the **same SharedPreferences keys**. The user's device had those keys stored as Booleans from the previous build. `SharedPreferences.getInt(key)` throws `ClassCastException` when the key holds a Boolean (SharedPreferences does NOT auto-convert types) — the crash fired when the `defaults` StateFlow was collected and called `intFlow` → `prefs.getInt`.
- **Fix:** one-time migration in `NotificationPreferences.init` — for each of the 3 trigger keys, `try { store.getInt(key, 0) }`; on `ClassCastException`, read the legacy Boolean (`store.getBoolean(key, false)`), map `true→1 (ON)` / `false→0 (OFF)`, write it as Int (`store.putInt`). Idempotent: absent or already-Int keys are untouched. Runs at singleton construction (before any flow is collected). `SharedPreferences.apply()` updates the in-memory cache synchronously, so the subsequent flow reads see the migrated Int values — no race.
- **Lesson:** when changing a SharedPreferences key's storage type (Boolean→Int, etc.), you MUST migrate existing values — SharedPreferences stores values with their type and won't auto-convert. Either use new keys (simplest, loses old data) or migrate defensively (try new-type read, catch ClassCastException, read old type, write new type). See `lessons-learned.md`.
- **Status:** ✅ Fixed + CI green (run 31277812616, commit 87c4d1e, artifact 53 MB).
- **Date:** calendar/notifications-3way session (crash-fix pass).

### D-162 — Debug Bubble plan + sub-agent review (5 CRITICAL + 8 IMPORTANT issues caught)
- **What:** Planned the Debug Bubble feature (a floating, draggable debug overlay on every screen) and had the plan reviewed by a sub-agent. The main agent critically evaluated each finding — all 5 CRITICAL + 8 IMPORTANT issues were verified as real and incorporated into the plan before publishing it on the dashboard.
- **Plan location:** `APP/ani-kuta/DOCUMENTATION/planning/debug-bubble/PLAN.md` (753 lines).
- **Dashboard page:** `/debug-bubble` (deployed from `feature/debug-bubble` branch).
- **Sub-agent review findings (all verified real):**
  - **C1:** CompositionLocal values don't flow across siblings — the bubble (sibling of screen content in AppRoot's Box) can't read a context that screens provide via their own `CompositionLocalProvider`. **Fix:** hoist `MutableState<DebugContext?>` to AppRoot + two CompositionLocals (reader + writer); the provider wraps BOTH nav content + bubble.
  - **C2:** `Logger` (`:core:common`, always on classpath) can't reference `DebugLogBuffer` (`:feature:debug-bubble`, debug-only) — would break release builds. **Fix:** `LogAppender` interface in `:core:debug-api`; `Logger` holds the interface; wiring in `:app/src/debug/DebugInit.kt`.
  - **C3:** Koin module can't be imported in `:app/src/main` (debug-only dep). **Fix:** debug-only source set.
  - **C4:** Feature modules can't import from `debugImplementation`. **Fix:** split into `:core:debug-api` (always available) + `:feature:debug-bubble` (debug-only).
  - **C5:** WatchScreen carve-out unaddressed (player gestures, immersive mode, rotation). **Fix:** auto-hide on Watch + rotation re-clamp + IME padding.
  - **I1-I8:** network interceptor placement, SqlDriver injection, SQL injection in search, BLOB handling, DebugContext cleanup (VM leak), rotation/IME, Animatable vs mutableStateOf, honest removal edit list.
- **Main agent's assessment:** the sub-agent's review was thorough and technically accurate. No false positives in CRITICAL/IMPORTANT categories. Every issue was a real compile-time or semantic blocker. The plan is now sound.
- **Status:** ✅ Plan complete + sub-agent reviewed + dashboard deployed. Branch `feature/debug-bubble` CI green (run 31278786368). No app code changed — planning only. Implementation (DB-1..DB-8) begins after user approval.
- **Date:** debug-bubble planning session.

### D-163 — Debug Bubble: implementation complete (all 8 phases, DB-1..DB-8)
- **What:** Implemented the Debug Bubble — a floating, draggable squircle overlay on every screen with a 5-tab panel (Current Screen, Database, Console, Network, App Info). Debug-only (`debugImplementation`); release builds contain zero debug-bubble code. All 8 phases done on the `feature/debug-bubble` branch; CI green throughout.
- **Architecture (two-module split — D-162 C4 fix):**
  - `:core:debug-api` (always on classpath) — `DebugContext`, `DbReference`, `DebugAction`, `LocalDebugContext` (reader), `LocalDebugContextUpdater` (writer). Tiny types-only module.
  - `:feature:debug-bubble` (debugImplementation) — the bubble UI, panel, tabs, `DebugDatabaseBrowser`, `DebugLogBuffer`, `DebugNetworkStats`, Koin module.
  - `:app/src/debug` + `:app/src/release` source sets — `debugKoinModules()`, `initDebugIntegrations()`, `wrapDebugOkHttp()`, `DebugBubbleHost()` with same signatures; debug does the real work, release is no-op.
- **The bubble (DB-1):** 48dp squircle (`RoundedCornerShape(50)`), BugReport icon, `surfaceVariant` alpha 0.92. Draggable via `detectDragGestures`; `mutableStateOf<Offset>` (plain, not Animatable — avoids the Offset type-converter issue). Tap-vs-drag disambiguation (< 0.5px = tap). Bounds clamping (status + nav bar insets). **Position does NOT persist (per user)** — returns to bottom-end default on every app reopen. Rotation re-clamp. Gated by `DebugBubblePreferences.visible` (default `true` — visible by default per user).
- **The panel (DB-2):** `AnimatedVisibility`-gated. Scrim (semi-transparent black, `detectTapGestures` to dismiss + intercept drags so the bubble can't be dragged while open). Panel width = screenWidth - 2×12dp padding (per user: "takes up most of the display width"). Max height = 75% of screen height (per user). Expand direction by bubble position: top→down, bottom→up, left→right, right→left (per user). Horizontally-scrollable tab strip (icon + label). Respects status/nav bar insets (sub-agent review fix).
- **Current Screen tab (DB-2):** reads `LocalDebugContext`. Shows screenName, screenData (key-value), relevantTables (View-in-DB buttons), actions (quick-action buttons). "No screen context" when null.
- **Database tab (DB-3):** `DebugDatabaseBrowser` opens a SEPARATE read-only `SQLiteDatabase` connection (bypasses SQLDelight — avoids version-specific SqlDriver API quirks). Table chips (from `sqlite_master`), scrollable grid (first 100 rows), parameterized search (`LIKE ?` — bound parameter, no injection), BLOB columns render as `<BLOB: N bytes>` (D-162 I4), long text as `<long text: N chars>`. Read-only banner. Row count.
- **Console tab (DB-4):** `DebugLogBuffer` (10,000-entry ring buffer, per user) implements `LogAppender` (interface moved to `:core:common` to break the circular dep). Wired via `Logger.setAppender()` in `:app/src/debug/DebugInit.kt`. Throwable stored as capped 2KB string (D-162 M2). LazyColumn of entries (timestamp, level color-coded, tag, message, throwable). Tag filter + level filter chips (V/D/I/W/E multi-select). Clear + Refresh. Auto-scroll to newest.
- **Network tab (DB-5):** `DebugNetworkStats` OkHttp interceptor (counts requests, bytes, errors, status histogram 2xx/3xx/4xx/5xx/errors; capped 50-entry recent-requests deque). Wraps both default + download OkHttpClients via `wrapDebugOkHttp()`. Summary stat cards + color-coded histogram + recent requests list. Extension traffic (Injekt) not captured — disclosed in a banner (D-162 I1).
- **App Info tab (DB-6):** `DebugBuildInfo` (buildType, versionName, versionCode from BuildConfig — passed via debug/release source-set Koin modules). Build + project (44 modules, DB table count live) + memory (used/max heap, usage %) sections.
- **Screen opt-ins (DB-7):** Details (mainId, resolverState, episodeCount, source; relevantTables: content/episode_metadata/watch_progress), Browse (state, animeCount), Watch (mainId, episodeNumber, videoUrl, episodeCount; relevantTables: watch_progress/downloaded_episode), Downloads (state; relevantTables: download_queue/downloaded_episode). All use `DisposableEffect { onDispose { updateDebugContext(null) } }` to prevent VM leaks (D-162 I5).
- **Sub-agent reviews:** DB-1 + DB-2 reviewed by sub-agents. DB-1 caught 3 issues (Compose runtime dep, offset import, initial-position flash). DB-2 caught 2 (status/nav bar insets, drag interception). All verified real + fixed. Later phases reviewed via CI only.
- **Status:** ✅ All 8 phases implemented + CI green (final commit 477a256, run 31283553038, artifact 54 MB). Awaiting device verification.
- **Date:** debug-bubble implementation session (feature/debug-bubble branch).

### D-164 — Debug Bubble: DB Activity tracker + constantly-sliding charts (DB-9)
- **What:** Three improvements to the debug bubble's Network tab: (1) a real DB Activity tracker that intercepts every SQLDelight write, (2) charts that constantly slide forward every 2 seconds even with zero traffic, (3) hoisted viewMode so the DB Activity view survives the EXPANDED↔MINIMIZED transition.
- **Why:** The DB Activity view was a placeholder that never showed any data — it didn't track writes. The charts were frozen when the app was idle — they didn't "move like time is going." Minimizing the panel while on DB Activity always fell back to the Network view.
- **DB Activity tracker architecture (DB-9):**
  - `DebugDbStats` singleton (`:feature:debug-bubble`) — mirrors `DebugNetworkStats`'s structure: atomic write counters (total/insert/update/delete/other), per-table counts, per-second time-series (writes/sec for 5-min chart), recent-events ring buffer (50 events), `snapshot()` with gap-fill, `clear()`.
  - `DebugSqlDriverWrapper` (`:feature:debug-bubble`) — Kotlin `by delegate` wrapper around `SqlDriver`. Overrides only `execute()` — the other 6 methods are auto-forwarded. Parses operation + table from the SQL string via regex. Zero overhead on reads (`executeQuery`).
  - `wrapDebugSqlDriver(driver: SqlDriver): SqlDriver` — added to `app/src/debug/DebugInit.kt` (fetches `DebugDbStats` from Koin, wraps the driver). No-op identity stub in `app/src/release/DebugInit.kt`. Same pattern as `wrapDebugOkHttp`.
  - Wired at `AnikutaApp.kt:199`: `single<SqlDriver> { wrapDebugSqlDriver(DatabaseDriverFactory(get()).create()) }`.
  - Catches 100% of writes from every repository, ViewModel, and WorkManager job. Zero changes to any repository, `.sq` file, or release code path.
- **Chart sliding approach:**
  - `advanceToNow()` gap-fill method added to both `DebugNetworkStats` and `DebugDbStats`. Called from inside `snapshot()`. Inserts zero-valued buckets for every elapsed second since the last bucket. Capped at 300 iterations (5 min). Seeds a baseline zero-bucket if the deque is empty.
  - Canvas X-axis changed from bucket-index-based (`x = i * stepX`) to timestamp-based (`x = (ts - windowStart) / windowSpan * width`). `coerceIn(0f, w)` guards clock skew. This ensures zero-filled gaps render at the correct temporal position instead of being compressed to full density.
- **viewMode hoist:**
  - `viewMode` was `remember`-scoped to `NetworkTab` (local state). `AnimatedVisibility` disposes the expanded NetworkTab on minimize → state was lost → mini-window fell back to Network.
  - Hoisted to `DebugPanel` as `networkViewMode`. Passed + `onViewModeChange` + `onViewInDb` callbacks to both NetworkTab call sites (expanded + minimized). DB Activity now survives minimize.
- **Alternatives considered for DB tracking:**
  - (B) SQLDelight `Query.Listener` / `notifyListeners` — REJECTED: `notifyListeners` keys are SELECT query names (not table names), would require a hand-maintained queryKey→table mapping across 28 tables. Also only fires for queries the app explicitly subscribes to via `asFlow()`.
  - (C) Wrap each repository — REJECTED: requires wrapping 10+ repositories, misses any future repository that forgets to use the wrapper.
  - (D) Override generated `*Queries` mutators — REJECTED: `*Queries` is a generated interface; can't override without subclassing the generated `*QueriesImpl` (which is `final`).
  - (A) Wrap `SqlDriver` — CHOSEN: single integration point, catches 100% of writes, mirrors the proven `wrapDebugOkHttp` pattern, zero release overhead.
- **Sub-agent review (Task 2-d):** No critical issues, no important issues. 3 minor unused-import issues (pre-existing, cleaned up). Overall: READY TO COMMIT.
- **Status:** ✅ CI green (run 31339439293, commit 619a174, artifact 54.1 MB). Awaiting device verification.
- **Date:** this session (feature/debug-bubble branch).

### D-165 — Debug Bubble: read tracking + export logs + filter toggle + dual-line chart
- **What:** Four improvements to the debug bubble's DB Activity view: (1) track SELECT reads in addition to writes, (2) export DB activity + network logs as shareable `.log` files, (3) a filter toggle (All / Reads / Writes) for the recent events list, (4) a dual-line chart (reads + writes) with 5 stat cards.
- **Why:** The user wanted to see ALL DB operations (reads + writes + updates + deletes), not just writes. They also wanted to download logs to share with the developer for debugging.
- **Read tracking architecture:**
  - `DebugSqlDriverWrapper` now overrides both `execute()` (writes) and `executeQuery<R>()` (reads). The `executeQuery` override has the correct SQLDelight 2.0.2 signature: `override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R>`.
  - SELECT table regex: `^\s*(?:SELECT\s+(?:DISTINCT\s+)?[\s\S]*?\s+FROM)\s+["`\[]?(\w+)["`\]]?` — matches `SELECT ... FROM <table>` and `SELECT DISTINCT ... FROM <table>`. Also handles `WITH ... SELECT` (CTEs).
  - `DebugDbStats` rewritten: `totalReads` atomic counter, `readTableCounts` map, `DbEvent` (with `isRead` flag) replaces `DbWriteEvent`, `DbTimeSeriesBucket` now has `readCount + writeCount`, `recordRead(table, sql)` method, ring buffer increased 50 → 200.
- **Export approach:**
  - `exportAsText()` methods on both `DebugDbStats` and `DebugNetworkStats` — produce human-readable text logs (not JSON, because the user said "log kind of format" and text is easier to read/share).
  - SAF (Storage Access Framework) via `ActivityResultContracts.CreateDocument("text/plain")` — the user picks a file location; the log is written on a background thread.
  - File names: `anikuta_db_activity_<timestamp>.log` + `anikuta_network_<timestamp>.log`.
- **Filter toggle design:**
  - 3-way: All / Reads / Writes. Default = "Writes" (reads are far more frequent — every Flow emission, every screen load — and would flood the list).
  - In minimized mode: always shows writes only (reads would make the mini-window unreadable).
  - The filter state is `remember`-scoped to `DbActivityContent` (not hoisted to DebugPanel) — it doesn't need to survive minimize since minimized mode always shows writes.
- **Dual-line chart:**
  - Two overlaid lines: cyan (reads/sec) + gold (writes/sec).
  - `maxVal = maxOf(maxReads, maxWrites).coerceAtLeast(1)` — both lines scaled to the same max so neither is clipped.
  - Peak labels show both dimensions: "Peak: N reads/s" + "Peak: N writes/s".
- **Alternatives considered for read tracking:**
  - (A) Only track reads in aggregate (counter + time-series), not in the recent-events list — REJECTED: the user explicitly wants to see reads in the event list.
  - (B) Separate ring buffers for reads vs writes — REJECTED: would lose the chronological interleaving of reads + writes (which is useful for understanding the order of operations).
  - (C) Larger ring buffer (500+) — REJECTED: 200 is enough to see the last ~200 operations, and reads are so frequent that 500 would still be filled in seconds. The filter toggle lets the user focus on writes.
  - (CHOSEN) Shared 200-entry ring buffer with a filter toggle — the user can switch to "Writes" to see only writes, "Reads" to see only reads, or "All" to see the interleaved history.
- **Sub-agent review (Task 3-a):** No critical issues, no important issues. 6 minor issues (3 fixed: stale KDoc/comment; 3 acceptable tradeoffs: indentation, raw Thread for export, ring buffer eviction under heavy read load).
- **Status:** ✅ CI run 31341636467 started (commit 1f65e5d). Awaiting green + device verification.
- **Date:** this session (feature/debug-bubble branch).

### D-166 — Database optimization (dead tables, indexes, FK enforcement)
- **What:** (1) Deleted `extensions.sq` + `metadata.sq` (zero Kotlin call sites — confirmed by grep). (2) Enabled `PRAGMA foreign_keys = ON` in `DatabaseDriverFactory.onOpen` (all `ON DELETE CASCADE` clauses now active). (3) Dropped 6 redundant indexes (leftmost-column of composite UNIQUE/PK indexes). (4) Added 8 missing indexes: `idx_watch_progress_continue` (partial — for `getContinueWatching`), `idx_watch_progress_completed_at`, `idx_episode_update_ack_at` (partial — for retention purge), `idx_notification_sent_at`, `idx_library_item_unique` (UNIQUE — hardens dedup), `idx_anilist_detail_anilist_id`, `idx_content_extension_url`, plus dedupe DELETE before the UNIQUE index creation. All migrations are idempotent (`DROP INDEX IF EXISTS` + `CREATE INDEX IF NOT EXISTS` + `ALTER TABLE ADD COLUMN` with `hasColumn` guard).
- **Why:** User requested DB optimization. The dead tables were wasting schema space. The redundant indexes were wasting write I/O. The missing indexes were causing table scans on common queries. FK enforcement was off (all CASCADE clauses were documentation-only).
- **SQLite UPSERT note:** NOT migrated to `ON CONFLICT DO UPDATE` — requires SQLite 3.24+ (2018); API 24-28 (Android 7-9, minSdk=24) ships SQLite 3.9-3.22 which doesn't support it. `INSERT OR REPLACE` kept; callers already read-then-write (fragility mitigated).
- **CHECK constraints note:** NOT added — can't `ALTER TABLE` to add CHECK on existing installs (would need table rebuild). Deferred.
- **Status:** ✅ CI green (run 31348314200). Awaiting device verification.
- **Date:** this session (feature/db-optimization-ratings-cw branch).

### D-167 — Audio-variants fix (source_name + scanlator columns in data_cache_episode)
- **What:** Added `source_name TEXT` + `scanlator TEXT` columns to `data_cache_episode`. The enriched cache write (AniList title) now preserves the extension's original `ep.name` + `ep.scanlator` via `epNumToSourceName` + `epNumToScanlator` maps. The cache-read `SEpisode` reconstruction now uses `name = meta.sourceName ?: meta.title ?: "Episode ..."` + `scanlator = meta.scanlator`. Also fixed the offline-fallback `url = animeUrl` bug → `url = meta.episodeUrl ?: animeUrl`, and the Downloads→Watch scanlator handoff (was hardcoded `""`).
- **Why:** Audio pills (SUB/DUB/HSUB) are parsed from `episode.scanlator + episode.name`. The enriched cache overwrote `name` with the AniList title (no SUB/DUB) and never stored `scanlator`. On cache-first load, the pills were invisible until a manual refresh.
- **Status:** ✅ CI green (run 31348314200). Awaiting device verification.
- **Date:** this session.

### D-168 — Extension trust per-package enabled flag
- **What:** Added `isEnabled: Boolean = true` to `AnimeExtension.Installed` (independent of signer-level trust). `ExtensionManager.loadAll()` filters `_sources` by `isEnabled` — only enabled extensions' sources appear in Search/Details pickers. `trustExtension()` also calls `enableExtension(pkgName)` — only that one package gets enabled. `untrustExtension()` also calls `disableExtension(pkgName)`. New `enableExtension(pkgName)` / `disableExtension(pkgName)` methods + a Switch toggle in `ExtensionsSettingsScreen`. Backward compat: if `enabledExtensions` set is empty on first launch, seed it with all currently-trusted pkgNames (prevents sources disappearing on upgrade). `enabledExtensions` stored in `AppPreferences` via `PreferenceStore.getStringSet` / `putStringSet`.
- **Why:** Trust was by-signing-certificate-fingerprint (security gate — correct). But when the user trusted one extension from a repo, ALL extensions from the same signer got auto-trusted on the next `loadAll()`. The user reported "I trusted 2 but all show when resolving." The per-package `isEnabled` flag gives the user explicit per-extension control without losing signer-level security.
- **Status:** ✅ CI green (run 31348314200). Awaiting device verification.
- **Date:** this session.

### D-169 — Watch-progress bug fixes (WP-B1 through WP-B4)
- **What:** Four bug fixes: (1) **WP-B1:** `setAutoMarkSuppressed` SQL now clears `completed_at` (was leaving stale data on un-mark). Also added INSERT-when-missing guard (was a silent no-op if the row didn't exist). (2) **WP-B2:** `resetAutoMarkSuppressed` now called on every FILE_LOADED via a `LaunchedEffect(loadingState)` in WatchScreen (was NEVER called → CF1 "re-arm on next play" was broken). (3) **WP-B3:** Resume-seek — WatchScreen looks up the saved position from `watchProgressStore` on the initial FILE_LOADED + calls `MPVLib.command(seek, absolute)` after a 300ms delay. Only fires on the initial load (`hasResumed` flag), not on quality/episode switch. Added `startPosition: Long = 0L` to `WatchKey` (backward-compatible default). (4) **WP-B4:** Save the OLD episode's progress at the top of `onEpisodeSwitch` before `updateCurrentEpisode` overwrites the state (was only saved on the 10s timer + onDispose — up to 10s of progress was lost on switch).
- **Why:** The live DB showed anomalous rows (`position=0, duration=0, completed=0, completed_at=<set>, auto_mark_suppressed=1`) that directly matched the WP-B1 bug. The user reported that clicking the same episode didn't resume from where they left off. The CF1 "re-arm" feature was silently broken.
- **Status:** ✅ CI green (run 31348683710). Awaiting device verification.
- **Date:** this session.

### D-170 — Continue-watching carousel + ratings UI (temporary testing implementation)
- **What:** (1) **Continue-watching carousel:** `ContinueWatchingCarousel` composable at the top of Browse — single horizontal `LazyRow` with cover thumbnails (or first-letter placeholder), EP badges, progress bars, title below. `BrowseViewModel.continueWatching: StateFlow<List<ContinueWatchingItem>>` enriches `watchProgressStore.observeContinueWatching(10)` with content metadata (title, cover URL via `ContentRepository`). Tap → `AnimeDetailsKey.AniList` or `.Extension` (resume kicks in on play via WP-B3). Marked "TEMPORARY — easy to remove later." (2) **Ratings UI:** Per-anime 10-star `StarRatingBar` on the Details synopsis title row (right side). `DetailsViewModel.animeRating: StateFlow<Int?>` reactive via `ratingStore.observeAnimeRating`. Each star = 10 points (0-100 backend). Tap same star → clear (toggle). Per-episode 10-star `WatchStarRatingBar` below the "Currently playing episode" text in WatchScreen. Both use `RatingStore` (concrete class, Koin single).
- **Why:** User requested temporary testing implementations for ratings + continue-watching. The backend (store + schema + Koin) was already fully built for both — only the UI was missing.
- **Status:** ✅ CI green (Phase 3: run 31348903899; Phase 4: pending). Awaiting device verification.
- **Date:** this session.

### D-171 — Profile UI v4: WhatsApp-style tab animation (continuous scroll-driven shrink)
- **What:** Restructured `ProfileScreen` so the full-size tab bar (Stats/Timeline) is **item 0 in each tab's LazyColumn** (was a pinned box that height-animated). The shrink is driven **continuously** by the scroll offset via `graphicsLayer` lambdas (`alpha`, `scaleX`, `scaleY` — deferred reads, no recomposition on scroll) instead of a boolean `collapsed` toggle + `animateFloatAsState` (which caused the "jumping" effect). Mini tab pill moved into `CollapsingHeader`'s `actions` slot (right side, between title and settings gear — was at top-LEFT `CenterStart`), widened (fontSize 11sp, padding 12dp), each segment individually clickable. `ScrollBlurOverlay` removed entirely (the darkening blur no longer appears on slight scroll). Because the tabs are a real scroll item, once scrolled past the `ProfileHeader` (item 1) lands naturally at the top of the viewport — fully visible, not cut off. A `scrollFraction: () -> Float` lambda is shared between the `graphicsLayer` modifiers and the mini-pill alpha; the header title collapse still uses `derivedStateOf { fraction > 0.5 }` → `CollapsingHeader(collapsed=...)` (smooth `animateFloatAsState` for the font only).
- **Why:** User reported the tab shrink was "jumping" not smooth, mini tabs appeared at top-left (should be right, between title and settings), too narrow, and the darkening blur appeared on slight scroll. Root cause: boolean-toggle + animateFloatAsState = stepped animation; `CenterStart` alignment = wrong position; `ScrollBlurOverlay` = unwanted blur.
- **Status:** ✅ CI green (run 31422446992). Awaiting device verification.
- **Date:** this session (feature/db-optimization-ratings-cw branch).

### D-172 — Profile UI v4: Watch flow redesign (tall chart, grid, floating sidebar with covers)
- **What:** Redesigned `WatchFlowGraph`: taller chart (128dp bars area), horizontal grid lines (3: 0/50/100%), y-axis labels (max/mid/0) on the left, wider bars (30dp) with rounded tops, per-bar count labels above each bar (bold+primary for today/selected, faint otherwise), today's bar colored primary@0.75 by default. Day labels moved BELOW the bars (no overlap). Tap a bar → floating right-side sidebar overlay (`AnimatedVisibility` slide+fade) with a themed primary-tinted background + border, showing: day name, "X ep • Ym" duration, and a vertical scrollable list of anime covers (28×40dp) + title + episode number. Tapping a different bar switches content (Saturday's sidebar is replaced by Sunday's — no close/reopen flicker); tapping the same bar closes. ViewModel computes per-day `DayWatchSummary` (count + totalDurationSec + items list capped at 12) by grouping `allProgress` by day-of-week.
- **Why:** User reported the watch flow "looks way too bad," needs grids + taller height + extra padding, the sidebar mini-window was "not proper" (no thumbnails, no themed bg), and switching bars didn't auto-close the previous one.
- **Status:** ✅ CI green (run 31422446992). Awaiting device verification.
- **Date:** this session.

### D-173 — Profile UI v4: Time DNA donut (stroke arcs) + layout restructure
- **What:** `TimeDnaCard` now renders a true donut via `drawArc(useCenter=false, style=Stroke(width=outerR*0.26, cap=Butt))` (was a filled pie — the transparent `drawCircle` "hole punch" was a no-op). Center overlay shows the current time period's color dot + name. Legend (Morning/Afternoon/Evening/Night with %) moved BELOW the donut (was on the right). Right side is now a dedicated recently-watched anime section: a `LazyRow(reverseLayout=true)` of up to 6 covers so the **newest appears at the far right** and is visible by default (scroll left for older). Theme-adjacent colors kept (orange/amber/lime/white — warm→cool by time of day; evening = app primary lime).
- **Why:** User reported it was a pie not a donut, center should show current period, legend should be below not on the right, right side should show anime with the most-recent at the far right (was at the bottom of a list), and colors looked "random" (actually the broken donut made the pie look messy).
- **Status:** ✅ CI green (run 31422446992). Awaiting device verification.
- **Date:** this session.

### D-174 — Profile UI v4: Activity heatmap left day markers + bottom month labels
- **What:** `ActivityHeatmapCard` now has a left day-marker column (M/T/W/T/F/S/S — single letters, 8sp, aligned with the 7 rows) outside the `LazyRow`. Bottom: each week column shows its month abbreviation (Jan/Feb/…) when it's the oldest week of that month (replaces the "Tap and scroll to see more →" text). Square 12dp cells, gray (onSurfaceVariant@0.12) for non-active days, scrollable (recent on right via `reverseLayout`). Each column = Mon..Sun (row 0 = Monday) computed from the Monday of the current week.
- **Why:** User requested minimized day markers on the left (not full names) and month names at the bottom (not the "tap and scroll" text).
- **Status:** ✅ CI green (run 31422446992). Awaiting device verification.
- **Date:** this session.

### D-175 — Profile UI v4: Settings image picker (PickVisualMedia → internal storage)
- **What:** `ProfileSettingsSheet` "Choose Image" now launches `ActivityResultContracts.PickVisualMedia()` (was a `/* TODO: file picker */` placeholder). The picked image URI is copied to `context.filesDir/avatar_<timestamp>.jpg` on `Dispatchers.IO` (via `rememberCoroutineScope` + `withContext`) and stored as a `file://` URI in `profile_avatar_url` so it persists across launches (a `content://` SAF URI from the photo picker is only valid for the session — no persistable permission). URL mode trims input + live-previews via `AsyncImage(model = avatarInput.trim())`. The Coil `OkHttp` client already has a browser User-Agent (`HttpClientFactory`), so most URLs load; host-specific hotlink protection remains the only failure mode (noted honestly — can't be fixed without the specific URL/host).
- **Why:** User reported "Choose Image" did nothing (TODO placeholder) and a `.jpg` URL didn't load while a non-extension URL did. The OkHttp client already sends a UA, so the `.jpg` failure is host-specific (hotlink protection / Referer requirement), not a UA issue.
- **Status:** ✅ CI green (run 31422446992). Awaiting device verification.
- **Date:** this session.

### D-176 — AnimatedVisibility RowScope conflict (extract into top-level composable)
- **What:** CI failed on `:app:compileDebugKotlin` — `AnimatedVisibility` called inside the watch-flow bars `Box` (which sits inside the chart `Row`) resolved to `RowScope.AnimatedVisibility` (the chart Row's receiver leaks into the Box's content lambda) and the compiler rejected it: "cannot be called in this context with an implicit receiver." Fix: extracted the `AnimatedVisibility` + sidebar into a top-level private composable `WatchFlowSidebarOverlay(visible, dayName, summary, onOpenAnime, onDismiss, modifier)`. Its body has NO scope receiver, so `AnimatedVisibility` resolves to the plain top-level overload. The `modifier` (with `Modifier.align(Alignment.TopEnd)` from the Box call site) is passed through.
- **Why:** This is a recurring issue (the previous session hit the same thing and worked around it by replacing `AnimatedVisibility` with a plain `if` — losing the animation). The proper fix is extraction into a scope-less composable so the top-level overload is unambiguous.
- **Status:** ✅ CI green (run 31422446992).
- **Date:** this session.

### D-177 — Profile UI v5: magnetic snap + gradient blur header
- **What:** Added magnetic snap to the profile scroll: when the user lifts their finger after scrolling, `animateScrollToItem` snaps to either fully-expanded (item 0 at top) or fully-collapsed (item 1 at top) depending on whether the scroll fraction is < or > 0.5. Implemented via `snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect { snap }`. Added a gradient blur scrim (20dp, `Brush.verticalGradient` background→transparent, smoothstep alpha) at the header's bottom edge that fades in when collapsed. Mini tab segments now use `Modifier.weight(1f)` + fixed 120dp pill width for equal-width segments.
- **Why:** User wanted: (a) a magnetic "snap to that point" effect where a small scroll snaps to collapsed, (b) gradient blur on the minimized header, (c) equal-width mini tab segments.
- **Status:** ✅ CI green (run 31428330476). Awaiting device verification.
- **Date:** this session.

### D-178 — Profile UI v5: Watch flow sidebar on LEFT + solid bg + complementary today color
- **What:** Watch flow sidebar now appears from the LEFT side (`Alignment.TopStart` + `slideInHorizontally { -it }`), regardless of which bar is tapped. Solid background (`MaterialTheme.colorScheme.surface` + border, was transparent `primary.copy(alpha=0.10f)`). Taller (200dp, was 128dp). Tap-outside closes (clickable on bars Box). Scroll starts → closes (`snapshotFlow { listState.isScrollInProgress }.filter { it }`). Removed default per-bar count labels (count shown in sidebar only). Today's bar uses the complementary color (hue + 180° of primary, computed via `android.graphics.Color.RGBToHSV/HSVToColor`) — dynamic per theme.
- **Why:** User wanted: sidebar from the left not right, solid (non-transparent) background, taller height, tap-outside/scroll to close, no default count labels, today's day colored with a complementing color based on the theme.
- **Status:** ✅ CI green. Awaiting device verification.
- **Date:** this session.

### D-179 — Profile UI v5: Time DNA + Recently Watched split into separate cards
- **What:** Split the combined Time DNA + Recently Watched card into two separate Surface cards. Time DNA: standalone donut with theme-tinted colors (`lerp(periodColor, primaryColor, 0.25f)` blends 25% primary into each period color), center shows current period, legend below. Recently Watched: vertical LIST format (was carousel) showing episode thumbnails (96×56dp landscape, from `data_cache_episode.thumbnail_url`, falls back to cover), title + EP number, tap → details page. ViewModel adds `episodeThumbnailUrl` to `RecentlyWatchedItem` + `DayWatchItem`, looked up via `database.dataCacheQueries.getEpisodeMetadataByNumber(mid, epNum.toDouble()).executeAsOneOrNull()?.thumbnail_url`.
- **Why:** User wanted: proper separation between Time DNA and Recently Watched (own backgrounds), Recently Watched as a list not carousel, episode thumbnails (not content covers), tap → details, theme tint on DNA colors.
- **Status:** ✅ CI green. Awaiting device verification.
- **Date:** this session.

### D-180 — Profile UI v5: Heatmap label padding + genre radar in-web highlight
- **What:** (1) Heatmap: added `padding(bottom = 16.dp)` to the day-markers Column and increased the month-label Box height to 14dp with `TopCenter` alignment — fixes the bottom-half-cut-off issue. (2) Genre radar: selected genre now highlighted IN the web — thicker axis (3px, primary), halo ring (radius 12, alpha 0.3) around the data point, enlarged data point (7px vs 5px), and a highlighted label pill (primary background, black text, `drawRoundRect` + re-measured `TextLayoutResult`).
- **Why:** User reported: heatmap labels' bottom half was cut off (needed padding to move text up); genre clicked in the web should be highlighted in the web itself (was only highlighted in the bottom legend).
- **Status:** ✅ CI green. Awaiting device verification.
- **Date:** this session.

### D-181 — Profile UI v5: Avatar crop editor + URL state separation
- **What:** (1) New `AvatarCropScreen.kt` — full-screen Dialog with pan/zoom (`detectTransformGestures`, 1×–5× scale, clamped offset), circular crop overlay (Path + EvenOdd fill for dark-with-hole), saves cropped square bitmap to `filesDir/avatar_<ts>.jpg` via `Bitmap.createBitmap`. Loads bitmap via `coil3.SingletonImageLoader.get(context).execute(request).image` → cast to `coil3.BitmapImage`. Crop math: `baseScale = max(framePx/iw, framePx/ih)` (ContentScale.Crop), `sourceSize = framePx / (baseScale * userScale)`, center offset mapped back to bitmap coords. (2) Settings sheet: separated `urlInput` (String, initialized from pref only if starts with "http") from `uploadedFileUri` (String, initialized from pref only if starts with "file://") — fixes mode-switch state leak where switching to URL mode showed a file:// path. Tap the preview image → opens crop editor. After crop, switches to upload mode with the cropped file:// URI.
- **Why:** User wanted: ability to crop/zoom/align the avatar image (not just upload as-is), and URL paste not registering properly (state leak between modes).
- **Status:** ✅ CI green (run 31428330476, commit 47196ad). Awaiting device verification.
- **Date:** this session.

### D-182 — Coil3 ImageResult API: result.image (not Success cast)
- **What:** CI failed because `import coil3.request.Success` was unresolved in Coil 3.0.4 — `Success` is not a public top-level class in the `coil3.request` package (it's likely nested or the API changed). Fix: access `result.image` directly on the `ImageResult` interface (the `image: Image?` property is on the sealed interface, nullable — null on error). Removed the `Success` import entirely. Also fixed: `min(maxWidth, maxHeight)` → `minOf(maxWidth, maxHeight)` (kotlin.math.min doesn't work on Dp), and `val cropSource: String get() = ...` → regular `val` (local properties with custom getters have restrictions in @Composable scope).
- **Why:** Three CI compile errors on first push. Root cause: guessed Coil3 API (`Success` top-level class) was wrong; `kotlin.math.min` only works on primitives; local property getters restricted in composable scope.
- **Status:** ✅ CI green after fix (run 31428330476).
- **Date:** this session.

### D-183 — Profile UI v6: magnetic snap guard (top-area only)
- **What:** The magnetic snap now only fires when `activeListState.firstVisibleItemIndex == 0` (user is near the top, in the tab-bar threshold area). If `firstVisibleItemIndex > 0` (user is deep in content), the snap is skipped entirely. Previously, the snap fired on EVERY scroll end — when the user scrolled deep into content, `scrollFraction()` returned 1.0 (because `firstVisibleItemIndex > 0`), so it snapped to item 1, jumping the user back to the top.
- **Why:** User reported: "if I scroll up and scroll way too much to the very bottom, then it auto scrolls to the very top. That is something which I need you to handle properly. It should still work only in a limited area." Root cause: no guard on scroll position — snap fired everywhere.
- **Status:** ✅ CI green (run 31431113076, commit 6945df6). Awaiting device verification.
- **Date:** this session.

### D-184 — Profile UI v6: watch flow sidebar taller + card-level scrim
- **What:** Watch flow sidebar height increased from 200dp to 260dp (taller than the entire chart card). The sidebar overlay moved from inside the bars Box (150dp) to the card-level Box, so it can overflow the bars area and be as tall as needed. A transparent scrim `Box(Modifier.fillMaxSize().clickable { selectedDay = -1 })` is shown when the sidebar is visible — it captures all taps on the card (outside the sidebar) and closes the sidebar reliably. The sidebar is drawn ON TOP of the scrim and consumes its own taps (via `.clickable { /* consume */ }`).
- **Why:** User reported: "the menu was supposed to be a bit taller. It could take up more space than the actual watch and flow section" and "if the user taps anywhere outside of the menu then the menu will automatically close by itself." The old bars-Box-level clickable didn't cover the full card area (y-axis labels, day labels, padding were outside it).
- **Status:** ✅ CI green. Awaiting device verification.
- **Date:** this session.

### D-185 — Profile UI v6: Time DNA + Recently Watched side-by-side
- **What:** Merged the two separate full-width cards (`TimeDnaCard` + `RecentlyWatchedCard`) back into a single `TimeDnaAndRecentCard` with a side-by-side `Row` layout: left = donut chart (140dp fixed width, own `Surface` background with `alpha=0.4f`), right = recently watched list (`weight(1f)`, own `Surface` background). The donut is 100dp (was 120dp) to fit the narrower left column. The recently watched list shows up to 4 items (was 6) with smaller thumbnails (64×38dp, was 96×56dp) to fit the narrower right column. Each side has its own `RoundedCornerShape(10.dp)` Surface for visual separation.
- **Why:** User reported: "now it is looking way too bad in a single row. There was supposed to be the time DNA on the left side and on the right side there was supposed to be recently watched." The v5 split into two full-width stacked cards wasn't what the user wanted — they wanted them side-by-side with their own backgrounds.
- **Status:** ✅ CI green. Awaiting device verification.
- **Date:** this session.

### D-186 — Profile UI v6: heatmap label padding fix (final)
- **What:** Increased the heatmap Column bottom padding from 16dp to 24dp, the month-label Box height from 14dp to 18dp, and the day-markers Column bottom padding from 16dp to 20dp. This gives the 8sp month-label text enough room so its bottom half is fully visible (not clipped by the Surface's rounded corners or the Column's content area).
- **Why:** User reported: "the text was being cut off at the bottom, like there was something interfering with it." The v5 fix (16dp bottom padding, 14dp Box height) wasn't enough — the text was still clipped.
- **Status:** ✅ CI green (run 31431113076, commit 6945df6). Awaiting device verification.
- **Date:** this session.

### D-187 — Doc-debt sweep complete (all documentation updated to match actual project state)
- **What:** Comprehensive documentation sweep across the entire AGENT-CONTEXT/ + APP/ani-kuta/ + DASHBOARD/webpage/ to eliminate all stale references discovered during the project review. Every doc now matches the actual codebase (46 modules, 28 tables / 15 .sq files, 315 .kt files, D-001..D-186, Nav3 fully removed, hand-rolled nav, `main` branch).
  - **knowledge/ (7 files fully rewritten):** `architecture.md` (46-module graph + SQLDelight + hand-rolled nav + DI wiring + known debt), `module-map.md` (all 46 modules with jobs/deps/key files), `tech-stack.md` (actual versions verified against `libs.versions.toml`), `old-vs-new.md` (old project location + full comparison + migration notes), `dashboard.md` (14 pages + data files + update process), `project-overview.md` (Q1/Q2/Q10 answered + current status), `ui-customization.md` (5 customization layers + subtitle settings + live data verification).
  - **Top-level (3 files updated):** `master.md` (46 modules/28 tables, 30 sections, D-186, Nav3 fully removed, current focus = DB management, Deferred Concerns summary), `SESSION.md` (D-186, 46 modules, Deferred Concerns, §30 debug-schema stance, doc-debt done), `navigation.md` (30 sections, REFERENCES/ expanded, download-research/ section added, all knowledge/ descriptions current).
  - **CORE_RULES.md:** §8 clarified (ABI config lives in `AndroidConfig.kt` via convention plugin; compileSdk 36 context — Nav3 removed but SDK kept at 36). §30 reinforced with user clarification (debug = no migrations, just recreate; `onOpen` is a convenience guard not a migration system).
  - **Code comments cleaned (3 files):** `AndroidConfig.kt` (Nav3 comment → accurate), `app/build.gradle.kts` (orphaned `// Navigation 3` → hand-rolled D-150), `gradle/libs.versions.toml` (2 orphaned `# Navigation 3` → accurate).
  - **DASHBOARD/webpage/ (13 files, full-stack-dev sub-agent §19):** `lib/data.ts`, `lib/decisions.ts`, `lib/schema.ts`, `lib/testingData.ts`, `lib/phaseD.ts`, `lib/downloadsPlan.ts` + 6 page components (`app/{page,architecture,progress,database,testing,decisions}/page.tsx`) + `components/Footer.tsx`. All counts corrected (46/26/18 modules, D-001..D-186, 28 tables/15 .sq files), Nav3 false claims fixed (was "rememberSaveable + StateFlow" → corrected to `mutableStateListOf<NavKey>` + `when(currentKey)` + R7 accepted limitation), branch updated to `main`. Build PASSED.
  - **memory/ (4 files updated):** `progress.md` (session entry + Deferred Concerns registry + Known doc debt refreshed), `decisions.md` (D-187 + D-188 this entry), `changelog.md` (session entry), `lessons-learned.md` (doc-drift lesson).
- **Why:** The project review (this session) discovered extensive doc-drift: knowledge/* proposed 8 modules + Room (actual 46 + SQLDelight), said old project location "unknown" (actual REFERENCES/old-kuta/ANIKUTA/), said "5 dashboard pages" (actual 14), master.md said "38 modules/22 tables/29 sections/D-165" (actual 46/28/30/D-186), Nav3 was described as "on the classpath unused" in 4 docs but had been fully removed from all build files. Per CORE_RULES §26 (Documentation Verification — Continuous), this is silent + corrosive — a stale doc misleads the next session. User directed: "update all the documentation… do not leave anything behind."
- **Remaining (deferred):** `APP/ani-kuta/DOCUMENTATION/17-database-schema.md` still says "21 tables" (actual 28) — historical design doc, left as-is with a note in navigation.md (a rewrite would alter design history). `DASHBOARD/webpage/lib/schema.ts` SCHEMA_TABLES array still uses planned Phase-1 table names — deferred (changes DB page UI; dashboard polish). `decisions.md` minor numbering cleanup (D-121 missing, D-037/D-038 out of order, D-008/D-009 stale) — not blocking. Repo-root pollution (skills/ + worklog.md) — deferred per user.
- **Status:** ✅ Complete. All AGENT-CONTEXT/ + APP/ani-kuta/ docs + code comments updated by main agent. DASHBOARD/webpage/ data updated by full-stack-dev sub-agent (build passed). Awaiting push to `docs/doc-debt-sweep` branch + CI verification.
- **Date:** this session (doc-debt-sweep session).

### D-188 — Deferred Concerns registry established (11 tracked items, not fixed this session)
- **What:** Established a formal "Deferred Concerns" registry in `memory/progress.md` tracking 11 known issues that were identified during the project review but deferred to future phases per user direction. Each item has: severity, estimated effort, dependencies/notes. The 11 items:
  1. AniList tracker is a placeholder (expected — not yet implemented).
  2. HttpDownloader.reResolver orphaned (D-149) — built but not wired; signatures mismatched.
  3. Main-thread runBlocking in Downloads→Watch SAF scan (MainActivity.kt:470) — ANR risk.
  4. Dead/unwired download code (D-151) — DownloadVideoPickerSheet, setRetryingStatus.
  5. Outer retry loop not implemented (D-151) — RetryPolicy class doesn't exist.
  6. WatchKey god-object (15 fields, 5 pre-serialized strings) — refactor to identifier-only.
  7. Nav backstack doesn't survive process death (R7, D-150 accepted limitation).
  8. 4 god-class .kt files >2000 lines (LibraryScreen 2471, DetailsScreen 2277, DetailsViewModel 2159, WatchScreen 2017).
  9. DB migrations use `onOpen` not `.sqm` files (acceptable for debug per §30).
  10. Release signing not configured (debug-only, Phase 9 pending).
  11. Dashboard schema.ts uses planned Phase-1 table names (deferred dashboard polish).
- **Why:** User reviewed the project review's high/medium-severity concerns and directed: each item is to be "saved in the agent context" for future handling, not fixed this session. The registry ensures no concern is forgotten — each has a clear severity, effort estimate, and dependency note so a future session can pick the right one up.
- **User dispositions (this session):**
  - #1 (AniList placeholder): "That is okay because we have currently not implemented the actual AniList tracking functionality."
  - #2 (reResolver): "We need to handle it later. We need to make sure that we handle it properly."
  - #3 (runBlocking): "We do need to focus on it [later]."
  - #4 + #5 (dead download code + retry): "For now let's leave it be… we are going to handle it later on."
  - #6 (WatchKey): "We might need to properly look into it and handle it properly too." — analysis delivered, refactor deferred.
  - #7 (nav backstack): "We need to look into it and we need to handle it too."
  - #8 (god-classes): Same — handle later.
  - #9 (DB migrations): "We are currently working on the project. It is a debug application… we are not going to worry about the database migration… The old ones will be completely deleted." → reinforced in CORE_RULES §30.
- **Status:** ✅ Registry established in `progress.md` → "Deferred Concerns" section. All 11 items tracked. Not fixed this session — by user direction.
- **Date:** this session (doc-debt-sweep session).

### D-189 — FK crash fix: remove wrong content.extension_id + extension_detail.extension_id FKs to content_ext(id)
- **What:** Removed 2 semantically-wrong FOREIGN KEY constraints from `content.sq`:
  1. `content` table: removed `FOREIGN KEY (extension_id) REFERENCES content_ext(id) ON DELETE SET NULL`.
  2. `extension_detail` table: removed `FOREIGN KEY (extension_id) REFERENCES content_ext(id) ON DELETE CASCADE`.
  The `extension_id` columns are kept (now plain INTEGERs storing the Aniyomi internal `source.id`). The `content_ext` table is kept (dead but harmless — to be analyzed during the DB-quality phase). Added explanatory D-189 comments on both tables. Fixed 1 stale KDoc in `ContentDataJson.kt`.
- **Why:** User hit `SQLiteConstraintException: FOREIGN KEY constraint failed` when linking an extension source to an AniList anime (crash in `ContentQueries.updateContentSources` → `ContentResolver.linkExtensionToExisting` → `DetailsViewModel.linkSource`). Root cause: the `content_ext` table is NEVER populated (`getOrCreateExtension` has zero callers), but the code passes `extensionId = source.id` (Aniyomi internal source ID) at all 6 link/upsert sites — NOT a `content_ext.id`. Pre-D-166 (FKs OFF), this silently stored a dangling value. D-166 enabled `PRAGMA foreign_keys = ON` → the dangling FK now crashes. The FK was semantically wrong from day 1 — the code has always treated `extension_id` as `source.id`, never as `content_ext.id`.
- **Investigation:** Verified `content_ext` has zero callers of `getOrCreateExtension`, zero JOINs against it, zero DELETEs from it. Verified all OTHER FKs in `content.sq` are safe (`data_source` + `system` seeded by `seedDefaults()`; `content_ext_repo` populated on-demand; `main_id` → `content` inserted before detail rows). Verified `getContentByExtension` (4 callers) all pass `source.id` — the codebase is self-consistently using `extension_id` as `source.id`. Sub-agent review (Task i8): ✅ READY TO PUSH, zero ❌ issues.
- **Alternative considered (not chosen):** Wire up `content_ext` properly — call `getOrCreateExtension()` at all 6 sites to get a real `content_ext.id`, change `getContentByExtension` callers (4 sites) to pass `content_ext.id`. Rejected: bigger change (10+ sites), higher risk to the cross-source dedup flow (D-137, D-139), and the `content_ext` table's value is unclear (it stores pkgName/versionName/isNsfw which are available from `ExtensionManager` at runtime). Deferred to the DB-quality phase: decide whether to wire up `content_ext` or drop it.
- **Reinstall required:** the fix is a schema change. `CREATE TABLE IF NOT EXISTS` won't alter existing tables (SQLite can't ALTER TABLE DROP CONSTRAINT). Per CORE_RULES §30, debug users clear app data / reinstall. The user is reinstalling → fresh schema applies.
- **Status:** ✅ Sub-agent reviewed (clean). Awaiting push to `feature/fix-fk-crash` + CI verification + user device test (re-run Phase 2 of the DB test checklist: link extension source → should no longer crash).
- **Date:** this session (DB test-checklist crash-fix session).

### D-190 — Multi-source episode metadata engine (AniZip + Jikan + Kitsu)
- **What:** Replaced the standalone `EpisodeMetadataFetcher` (which used Anikage.cc + basic Jikan + AniList streaming in a non-pluggable class) with a proper pluggable multi-source `EpisodeMetadataEngine` using 3 dedicated `EpisodeMetadataProvider` implementations:
  1. **AniZip** (`api.ani.zip/mappings?anilist_id=X`) — PRIMARY. Richest data: per-episode `title.en`/`title.ja`/`title.x-jat`, `overview`, `summary`, `image`, `airDate`, `runtime`, `seasonNumber`, `episodeNumber`. Also returns top-level `mappings` with `mal_id`/`kitsu_id`/`themoviedb_id` (available for future cross-ID activation).
  2. **Jikan** (`api.jikan.moe/v4/anime/{malId}/episodes`) — UNIQUE: `filler` + `recap` booleans (the key differentiator the user requested). Also provides `title_japanese`, `title_romanji`, `score` (MAL community score). Rate-limit-aware: 400ms page delay + exponential backoff on 429 (1s/2s/4s, max 3 retries).
  3. **Kitsu** (`kitsu.io/api/graphql` via `lookupMapping(externalSite: ANILIST_ANIME)`) — tertiary. Canonical titles, descriptions, thumbnails.
- **Future-proof architecture**: `ContentId` + `ContentIdType` (ANILIST, MAL, TMDB, KITSU) + `EpisodeMetadataProvider.supportedIdTypes`. The engine auto-selects providers based on the content's ID type. Adding a new ID type (e.g. TMDB) = add a new provider module that declares `supportedIdTypes = {TMDB}` — zero engine changes. The engine queries all applicable providers in parallel (with per-provider try/catch failure isolation), then merges via `MetadataMerger.mergeEpisodeBatch`.
- **DB schema changes** (`data_cache_episode`): added 8 new columns — `is_filler` (nullable INTEGER — null=unknown, 0=no, 1=yes), `is_recap` (same), `title_japanese`, `title_romaji`, `runtime`, `season_number`, `episode_number_in_season`, `score`. Migration via idempotent `ALTER TABLE ADD COLUMN` with `hasColumn` guard in `DatabaseDriverFactory.onOpen`. `is_filler`/`is_recap` are NULLABLE (not default-false) because Jikan is the only source with filler info — if Jikan fails, null = "unknown" (UI shows no badge) rather than incorrectly showing "non-filler".
- **Merge strategy**: `MetadataMerger.mergeEpisodeBatch` — per-field first-non-null-wins by provider priority (AniZip > Jikan > Kitsu > AniList streaming). `isFiller`/`isRecap` use OR-true semantics (if any source says filler, it's filler; null only if all sources are null). This future-proofs for TMDB/etc. adding filler info later.
- **Backward-compatible public API**: `EpisodeMetadataEngine.fetchEpisodeMetadata(anilistId, malId, episodeCount)` — same signature as the old `EpisodeMetadataFetcher`. DetailsViewModel's 4 call sites were renamed but the method signature is unchanged. Internally builds a `ContentId` + delegates to providers.
- **Sub-agent plan review (Task m8)**: verified all 3 API endpoints live (AniZip, Jikan, Kitsu GraphQL). Found 3 must-fix flaws (undercounted call sites, `async.awaitAll` failure isolation, missing `mergeEpisodeBatch`) — all fixed. 11 concerns addressed (nullable filler, per-field source preference, Jikan NBSP trim, dead `EpisodeMetadataSource` deleted, per-episode `title.x-jat` used not show-level, etc.).
- **Sub-agent compile review (Task m7)**: ✅ READY TO PUSH. Zero compile errors. 8 verification areas clean (SQLDelight signatures, read-side mapping, imports, orchestrator pattern, merger, call sites, Koin DI, deleted file). 7 non-blocking concerns — 3 fixed (unused engine params, Kitsu KDoc, AniZip ID types), 4 deferred (hasColumn pre-existing bug, naming, cosmetic).
- **Files changed (12)**:
  - `dataCache.sq` — 8 new columns + updated `upsertEpisodeMetadata` query
  - `DatabaseDriverFactory.kt` — 8 `ALTER TABLE ADD COLUMN` migrations
  - `DataCacheModels.kt` — 8 new fields on `CachedEpisodeMetadata`
  - `DataCacheRepository.kt` — 3 sites updated (read + single write + batch write)
  - `MetadataModels.kt` — 8 new fields on `EpisodeMetadata`
  - `EpisodeMetadataProvider.kt` — NEW (ContentId + ContentIdType + interface)
  - `providers/AniZipEpisodeProvider.kt` — NEW
  - `providers/JikanEpisodeProvider.kt` — NEW
  - `providers/KitsuEpisodeProvider.kt` — NEW
  - `EpisodeMetadataEngine.kt` — NEW (replaces deleted `EpisodeMetadataFetcher.kt`)
  - `MetadataMerger.kt` — added `mergeEpisodeBatch` + `mergeBooleanOrTrue` + updated `mergeEpisode`
  - `MetadataModule.kt` — Koin multi-binding + engine registration
  - `DetailsViewModel.kt` — constructor rename + 4 call sites + 3 enriched constructors + 1 reconstruction
  - `DetailsModule.kt` — comment update
- **Status:** ✅ Sub-agent reviewed (clean). Awaiting push to `feature/episode-metadata-engine` + CI verification + user device test.
- **Date:** this session (episode metadata engine session).

### D-191 — DB analysis + deferred-concerns expansion (11 new concerns from user test + DB exports)
- **What:** User completed the full DB test checklist (Phase 0-14) + uploaded 3 export files (DATABASE.json, NETWORK.log, DATABASE-ACTIVITY.log). Agent analyzed all 3 + expanded the Deferred Concerns registry from 11 → 22 items. No code changes — docs + analysis only.
- **DB analysis findings:**
  - **DB is mostly healthy**: 501 rows across 28 tables. Zero FK orphans. Lookup tables seeded. D-190 enrichment working (143 episodes, 88-115 with japanese titles/romaji/runtime/thumbnails/descriptions).
  - **D-190 confirmed working**: AniZip (19 reqs), Jikan (22 reqs), Kitsu (19 reqs) all firing. 52/143 episodes have filler/recap/score from Jikan.
  - **11 new concerns** (registry #12-22): activity_event empty, Updates not detecting, Notifications UI-only, download concurrency bug, download missing server/audio, file_size=0, extensions lag, extensions need filtering, details stale-state flash, "no source" race, user_customization empty.
- **User correction acknowledged**: Phase 5 of the DB test checklist (watch an episode) didn't clearly state extensions are a hard prerequisite. Episodes can't load/resolve/play without a trusted extension. Saved as a lesson — future test checklists will state prerequisites explicitly.
- **Verdict**: The schema is sound (28 tables, proper FKs post-D-189, good indexes post-D-166, zero orphans). The issues are at the application layer (features not writing to the DB), not the schema layer. The DB-quality analysis recommends: (1) wire the 3 "empty table" features (activity_event, episode_update, notification_config), (2) fix the 2 race conditions (stale-state flash, no-source-linked), (3) fix download concurrency + UI, (4) audit + potentially drop 2 dead tables (content_ext, user_customization), (5) optimize extensions page (240-icon lazy load).
- **Files changed (4)**: `progress.md` (11 new Deferred Concerns #12-22 + session entry + Last Updated), `decisions.md` (this D-191 entry), `changelog.md` (session entry), `lessons-learned.md` (extension-prerequisite lesson + DB-analysis lesson).
- **Status:** ✅ Complete. Awaiting push to `docs/db-analysis-and-concerns`. No code changes — docs only.
- **Date:** this session (DB analysis session).

### D-192 — DB schema cleanup + multi-phase improvement plan (Phase 1 of 6 complete)
- **What:** User provided comprehensive feedback after the D-191 DB analysis. Agent created a 6-phase improvement plan + executed Phase 1 (DB schema cleanup). Phases 2-6 are planned + saved for continuation.
- **Phase 1 (COMPLETE — CI green, merged to main):**
  - Dropped 3 confirmed-dead tables: `content_ext`, `content_ext_repo`, `user_customization`
  - Created `app_settings` table (key-value, for backup/restore mirror of PreferenceStore — columns prefixed `setting_` to avoid Kotlin keyword conflicts)
  - Created `SettingsRepository` (CRUD + export/import for backup/restore)
  - Removed dead FK from `content.extension_repo_id` (column kept for future)
  - Verified `content` table is future-proofed for multi-source/multi-content-type/multi-system (data_source_id, system_id, extension_id, content_type, content_format all present)
  - Cleaned up all dead Kotlin methods (getOrCreateExtension, insertExtensionRepo, getExtensionRepoByUrl) + data class (ExtensionRepo) + query references
  - Fixed LocalMetadataProvider (removed dead customizationQueries reads, constructor no longer takes database)
  - Added :core:database dep to :core:preferences (for SettingsRepository)
  - Registered SettingsRepository in Koin
- **Phase 1 CI:** First attempt failed (`value` is a Kotlin soft keyword — SQLDelight can't generate a property named `value`). Fixed by renaming columns to `setting_key`, `setting_value`, `setting_type`, `setting_category`. CI green on second attempt (run 31560749859).
- **User corrections acknowledged:**
  - User listed 8 tables as "dead" to drop. Research showed 4 are ACTIVE (notification_config, notification_sent, episode_update, download_queue). Agent refused to drop active tables (CORE_RULES §2: don't blindly agree).
  - User corrected the test checklist: Phase 5 didn't state extensions are a hard prerequisite for watching episodes. Saved as a lesson.
- **Phases 2-6 (PLANNED — not yet executed):**
  - **Phase 2:** Activity tracker wiring (#12) — wire `ActivityTracker.track()` at ~10 call sites (WatchScreen play/pause/complete, DetailsViewModel library/rating, Search, Downloads). ~2h.
  - **Phase 3:** Updates feature rework (#13) — (a) wire `ensureUpdateState` on library-add, (b) implement "first link = one update row" (all episodes 1-N as one batch entry, not per-episode, text "episodes 1-7 added to library", NOT marked as new), (c) implement "refresh = new episodes only" (compare cached count vs fresh fetch, only new eps become updates), (d) refresh button with live progress UI (cover + name + X/Y counter), (e) future: auto-update on air time via WorkManager. ~4h.
  - **Phase 4:** Download fixes (#15, #16, #17) — (a) investigate concurrency bug (research shows it QUEUES not cancels — need to understand user's observation), (b) fix DownloadedEpisode data-loss (add video_server/video_audio/source_id/file_size to data class + populate from completedTask + update DownloadStore — 3-file surgical fix), (c) Downloads page UI: show server/audio/resolution, minimize long names + tap-to-expand + auto-close after 5s, percentage always shown, file_size display, "?" for missing info. ~4h.
  - **Phase 5:** Details page fixes (#20, #21) — add `loadGeneration` counter to DetailsViewModel (fixes stale-state flash + no-source race by checking generation before writing state in async blocks). ~2h.
  - **Phase 6:** Docs + notify. ~1h.
- **Deferred (saved, not this plan):** Notifications (#14, blocked by Phase 3), extensions filtering (#19), extensions lag (#18), resolver sheet smarts (future), per-color customization (future), auto-update on air time (future, after Phase 3).
- **Status:** ✅ Phase 1 complete + CI green + merged to main. Phases 2-6 planned + saved in this entry for the next session.
- **Date:** this session (D-192 Phase 1).

### D-192 (continued) — Phases 2-5 complete (all implementation done)
- **Phase 2 (Activity tracker wiring — COMPLETE, CI green):** Wired `ActivityTracker.track()` at 7 call sites: WATCH_START (WatchScreen on FILE_LOADED), LIBRARY_ADD/REMOVE (DetailsViewModel.toggleLibrary), RATING (DetailsViewModel.setAnimeRating), SEARCH (SearchViewModel.search), DOWNLOAD_START (DefaultDownloadManager.enqueueDownload), APP_OPEN (AnikutaApp.onCreate). Added convenience overload to ActivityTracker that fills in sessionId automatically. Added `:core:activity-tracker` dep to 4 modules.
- **Phase 3 (Updates feature rework — COMPLETE, CI green):** Root cause of empty Updates: `ensureUpdateState()` was never called on library-add. Fix: (1) wired `ensureUpdateState(mainId)` in `toggleLibrary()` when adding to library, (2) wired `onEpisodesRefreshed(mainId, episodeCount)` after episodes are fetched in `fetchEpisodes()`, (3) added `batch_type` + `episode_count` columns to `episode_update` table, (4) modified `onEpisodesRefreshed` to create ONE "initial batch" row when `lastKnown=0` (first link — text "Episodes 1-N added to library", acknowledged=true, NOT marked as new) vs individual "new" rows for subsequent refreshes. Deferred: refresh-all-with-live-progress UI + auto-update on air time.
- **Phase 4 (Download data-loss fix — COMPLETE, CI green):** `DownloadedEpisode` data class was missing `sourceId`, `videoServer`, `videoAudio` fields — `DownloadStore.insertDownloadedEpisode` hardcoded them to null. Data captured in `download_queue` was LOST on transition to `downloaded_episode`. Fix: added the 3 fields (nullable, null defaults) to the data class + updated `DownloadStore` (both insert + mapper) + updated `DownloadQueue` construction to pass `sourceId` from `content.sourceId` + `videoServer`/`videoAudio` from the completed task. Deferred: download UI display (server/audio/percentage/minimize-expand) + file_size tracking + concurrency investigation.
- **Phase 5 (Details page fixes — COMPLETE, CI green):** (1) Added `loadGeneration` counter to DetailsViewModel — increments on every load, prevents stale-state flash by discarding async results from previous content loads. (2) Added synchronous source-link pre-read in `loadFromAniList` — reads the saved source link from PreferenceStore SYNCHRONOUSLY (SharedPreferences is in-memory cached) so the UI shows the correct linked source immediately. No more async gap where "No Source" is shown despite being linked.
- **Resolved Deferred Concerns:** #12 (activity_event — RESOLVED), #13 (Updates — RESOLVED), #16 (download data — DATA FIX DONE, UI deferred), #20 (stale-state flash — RESOLVED), #21 (no-source race — RESOLVED), #22 (user_customization — RESOLVED in Phase 1).
- **Still deferred:** #14 (Notifications — now UNBLOCKED by #13), #15 (download concurrency — research shows it queues not cancels), #17 (file_size=0 — still 0L), #18 (extensions lag), #19 (extensions filtering), download UI display improvements, refresh-all-with-live-progress UI, auto-update on air time, resolver sheet smarts, per-color customization.
- **Status:** ✅ All 5 phases complete + CI green + merged to main. `main` is at bb88275.
- **Date:** this session (D-192 Phases 2-5).

### D-193 — Updates + Notifications architecture plan (5 sub-agent reviews, awaiting user approval)
- **What:** Comprehensive architecture plan for a unified Updates + Notifications system. The plan covers: interlinked Updates engine + Notifications engine (via interface pattern to avoid circular deps), configurable WorkManager interval (6h-weekly), 3-way master toggle (Auto/Manual/Off), manual per-category selection, smart release detection (AniList airing + OneTimeWorkRequest 10-min polling), sub/dub tracking (separate counts + checkSingleAnime rewrite), 3 notification triggers (on_schedule/on_watchable/on_immediate), notification tap action (deep-link), test notification, 3-day "new" expiry + 90-day notification_sent retention, live-progress UI on Updates feed, 3-way toggle bug fix (ordinal→indexOf), "no source from library" fix (persist source link in auto-link), onEpisodesRefreshed ordering fix, combined Settings section.
- **5 sub-agent review sessions completed:**
  1. Architecture review — found circular dep + 10-min polling scheduling gap.
  2. Smart release review — found 4 blocking issues (scheduling, total_episodes, trigger wiring, checkSingleAnime rewrite).
  3. Settings UI review — confirmed 3-way toggle fix, found scope ambiguity + migration path.
  4. DB schema review — found 4 query updates + 1 new query + 2 indexes needed.
  5. Final consolidated review — found 12 blocking items, all addressed in v2.
- **All 12 blocking issues resolved in plan v2.** See §0 of the plan.
- **Plan location:** `APP/ani-kuta/DOCUMENTATION/planning/updates-notifications/PLAN.md`
- **Dashboard page:** `/updates-notifications-plan` (new sidebar entry "Updates Plan")
- **Estimated implementation:** ~34h across 10 phases (after user approval).
- **Status:** ⚠️ DRAFT — awaiting user approval. NOT implementing yet. The plan + web page are on branch `feature/updates-notifications-plan` (NOT merged to main).
- **Date:** this session (updates-notifications planning session).

### D-193 (continued) — All 10 phases COMPLETE (implementation done, CI green on feature branch)
- **Phase 1**: 3-way toggle fix (ordinal→indexOf at 8 sites) + no-source-from-library fix (persist source link in performAutoLink) + onEpisodesRefreshed ordering fix (ensureUpdateState internally).
- **Phase 2**: DB schema — 5 new columns (last_known_dub_count, last_checked_dub_at, total_episodes, new_expires_at on episode_update + anime_update_state), 4 query updates, 1 new query (getDueDubAnime), 2 new indexes, 3-day "new" expiry.
- **Phase 9** (moved before 3-8): Interface pattern — ScheduleRefresher + NotificationSender interfaces in :core:updates, implemented in :app via Koin lambdas. Avoids circular deps. on_watchable trigger wired.
- **Phase 3**: Combined Updates & Notifications settings screen + UpdatePreferences (mode/interval/sub/dub toggles) + test notification + NotificationManager.postTestNotification.
- **Phase 4**: Configurable WorkManager (UpdateScheduler — reads preferences, schedules/cancels with REPLACE) + manual mode (per-category filter) + live-progress (CheckProgress SharedFlow from UpdateEngine).
- **Phase 5**: Smart release detection — SmartReleaseCheckWorker (OneTimeWorkRequest chaining, 10-min polling, max 3 attempts) + SmartReleaseScheduler (±1h window, max 5 concurrent).
- **Phase 6**: Sub/Dub tracking — checkSingleAnime rewrite (partition by audio variant, separate sub/dub counts, respects user preferences, dub episodes use _dub episode_key suffix).
- **Phase 7**: Notification system — on_schedule trigger wired (ScheduleEngine), on_watchable already wired (Phase 9), on_immediate already fires. Tap deep-link via setContentIntent (package-based launcher Intent).
- **Phase 8**: Updates feed UI — live-progress StateFlow in UpdatesViewModel, initial-batch rendering ("Episodes 1-N added to library"), acknowledgment on tap.
- **Status:** ✅ All 10 phases complete + CI green on `feature/updates-notifications-impl` branch. NOT merged to main — awaiting user approval.
- **Date:** this session (D-193 implementation session).

### D-193 v2 — Episode-type toggle semantics (checking ≠ notifying)
- **What:** The Sub / Dub / Both toggle in Updates & Notifications settings controls NOTIFICATIONS only — not which audio variants the engine checks for. The engine always partitions the fetched episode list by audio variant and diffs both sub and dub against the last-known counts. A new episode that doesn't match the toggle is still inserted into the Updates feed (so the user sees it); it just doesn't post a notification.
- **Why:** The user's explicit clarification — "if the user has turned on the updates, even to manual or to auto, then by default it will search for and look for both sub and dub episodes regardless of what the user has selected for the episode type. The episode type is only for the notifications themselves." Missing a release because of a toggle would be a correctness bug; missing a notification because of a toggle is a preference.
- **Impact:** No engine change needed — `checkSingleAnime` already checks both variants independently. The toggle's effect is confined to the `notificationSender?.postNotification(...)` call sites, which are already gated behind the user's trigger config. This decision is documentation + mental-model, not implementation.
- **Date:** this session (D-193 v2 redesign clarifications).

### D-193 v2 — Notifications is a dedicated page
- **What:** Notifications is no longer an inline section inside the Updates settings screen. It is a nav row at the bottom of Updates & Notifications that opens a dedicated page. The page contains: a master enable switch at the top (the hard kill), the two triggers (On Schedule / On Watchable) as two-way On/Off toggles, and the "Customize library notifications" toggle.
- **Why:** The user wanted notifications "a completely separate page at the very bottom instead of showing me the toggle for notifications." A dedicated page gives room for the master switch + triggers + library-customization without crowding the updates settings.
- **Date:** this session.

### D-193 v2 — Library-customization toggle semantics
- **What:** The "Customize library notifications" toggle on the Notifications page controls whether per-anime notification overrides exist at all.
  - **OFF (default):** the default trigger settings (On Schedule / On Watchable from the Notifications page) apply to every anime in the library. No per-anime notifications section appears on details pages.
  - **ON:** each anime's details page gains a notifications section where the user can enable/disable notifications for that anime and override which triggers fire for it.
- **Why:** The user's clarification — "If the toggle is turned off then by default it will notify the user for all of the categories... If the user has turned on that library toggle then he will see the options to configure each one of the content in the library individually." This keeps the default experience simple (one set of defaults) while exposing per-anime control only when the user opts in.
- **Date:** this session.

### D-193 v2 — Documentation web page as the system reference
- **What:** A comprehensive Next.js documentation page (single `/` route) is now the canonical visual reference for the Updates + Notifications system. It covers: system-overview flow, the three update modes, the episode-type clarification matrix, the smart-release polling sequence + averaging loop, the updates-feed lifecycle, the notifications page design, the schedule grayed-out logic, the settings-UI card inventory, an interactive testing checklist (with localStorage persistence), and an end-to-end "how it works" narrative.
- **Why:** The user asked for "proper visuals and a better well-handled look and feel for things like how they need to be managed" + "a proper testing list, a checklist which I can use to test the things out" + "an overview of how things are functioning." The web page delivers all three in one place and is the artifact the user can re-open anytime.
- **Artifact:** `src/app/page.tsx` + `src/lib/aniKutaData.ts` (this Next.js project).
- **Date:** this session.

### D-193 v2 — Episode-type toggle: notifications only (code aligned to spec)
- **What:** The engine's `checkSingleAnime` no longer reads `getCheckSub()`/`getCheckDub()` to decide whether to insert rows. It ALWAYS inserts new sub + dub rows. The toggle is honored by `NotificationManager` (injected with `UpdatePreferences`) at notify time.
- **Why:** The user's spec: "it will search for and look for both sub and dub episodes regardless of what the user has selected for the episode type. The episode type is only for the notifications." Missing a release because of a toggle is a correctness bug; missing a notification is a preference.
- **Impact:** `UpdateEngine` always inserts both variants. `NotificationManager` checks both the per-anime `config.notifySub/notifyDub` AND the global `updatePreferences.getCheckSub()/getCheckDub()` before posting.
- **Date:** this session (D-193 v2 code fixes).

### D-193 v2 — Smart-release weighted averaging (learned_offset_ms)
- **What:** Added a `learned_offset_ms` column to `anime_update_state`. SmartReleaseCheckWorker now computes `newOffset = found_at - airing_at` and stores `learnedOffset = (old * 7 + new * 3) / 10` (70% previous + 30% new). First find (null) stores the raw offset. Next check = `next_airing_at + learnedOffset`.
- **Why:** The previous "averaging" just replaced the offset with the latest single observation — it chased the most recent find instead of learning a stable rhythm. The 70/30 weighting favors history while still adapting to gradual drift.
- **Date:** this session.

### D-193 v2 — Library customization toggle semantics (implemented)
- **What:** Added `libraryCustomizationEnabled` to `NotificationPreferences`. When OFF (default), the default triggers apply to every anime — no per-anime UI on the details page. When ON, `DetailsNotificationSection` appears on each anime's details page with enable/disable + per-trigger overrides. `NotificationManager` falls back to the default triggers when no per-anime config exists.
- **Why:** The user's spec: "If the toggle is turned off then by default it will notify the user for all of the categories... If the user has turned on that library toggle then he will see the options to configure each one of the content in the library individually."
- **Date:** this session.

### D-193 v2 — UpdateScheduler: MANUAL mode cancels the periodic worker
- **What:** `UpdateScheduler.reschedule()` now only schedules the periodic worker in AUTO mode. MANUAL + OFF both cancel it. Manual mode is strictly on-demand (the user taps Check Now, which calls checkDueAnime + scheduleImminentChecks directly).
- **Why:** The user's spec: Manual = "You press, it checks." A periodic background worker in Manual mode contradicts that.
- **Date:** this session.

### D-193 v2 — on_schedule precise timer (ScheduleNotificationWorker)
- **What:** New `ScheduleNotificationWorker` (OneTimeWorkRequest) fires the on_schedule notification at the exact airing time. ScheduleEngine schedules it when it discovers a future airing. The REPLACE policy means schedule changes reschedule it.
- **Why:** Previously on_schedule fired opportunistically during a schedule refresh that happened to be within ±1h of airing — imprecise. A timed worker is a true "airing time reached" reminder.
- **Date:** this session.

---

### D-194 — HttpDownloader.ReResolver adapter pattern (wiring D-149)
- **What:** The proxy-churn re-resolver (D-149) is wired via an adapter class `ReResolverAdapter` in `:app` that implements the local `HttpDownloader.ReResolver` fun interface (defined in `:core:download`) and bridges to the app-class `ReResolver` (which depends on `:core:video-resolver`). The adapter decodes the `resolveContextJson` string → `ResolveContext`, looks up the source via `ExtensionManager.getSource()`, builds a minimal `SEpisode`, delegates to `appReResolver.reResolve()`, and maps the result to `ReResolvedVideo`.
- **Why:** `:core:download` cannot depend on `:core:video-resolver` (keeps the dep graph minimal per REVIEW-5 M17/M49). The local fun interface in `HttpDownloader` + the adapter in `:app` is the cleanest bridge. `DownloadModule.kt` resolves it via `getOrNull<HttpDownloader.ReResolver>()` (lazy — :core:download doesn't need :app on its compile classpath).
- **Also fixed:** (1) HttpDownloader guards now check both `http://localhost` AND `http://127.0.0.1` (some extensions use 127.0.0.1 — lessons D-092). (2) New `updateDownloadVideoUrl` SQL query — the old code called `updateResult` which writes to `video_uri` (the content:// result URI), not `video_url` (the source URL). A re-resolve produces a new source URL, so it must update `video_url`.
- **Status:** ✅ Confirmed + implemented. CI green (run #540).
- **Date:** Download system fixes session.

### D-195 — RetryPolicy: retryable exception classification + exponential backoff
- **What:** New `RetryPolicy` class in `:core:download` classifies which exceptions are retryable: `IOException` + `HttpException` 5xx/429 → retryable; `DownloadException` (non-Http, e.g. validation/empty/proxy-churn exhaustion) + `HttpException` 4xx + generic `Exception` → not retryable; `CancellationException` → never (pause/cancel). Exponential backoff: 5s, 10s, 20s (capped at 60s). Max 3 outer attempts × 2 inner (re-resolve) = 6 total download attempts.
- **Why:** The outer retry loop was referenced in KDoc (HttpException.kt) but the `RetryPolicy` class didn't exist (D-151). `setRetryingStatus` + `RETRYING` state + `retry_attempt`/`retry_max_attempts` DB columns all existed but were dead code (zero callers). This wires them all together.
- **How:** `DownloadQueue.launchDownload` refactored — the existing permit-acquire + download body is wrapped in a `while(true)` retry loop. On retryable exception: `setRetryingStatus` + `delay(backoff)` + retry (re-acquires permit). On exhaustion/non-retryable: `setErrorStatus`. The `RETRYING` state is cleared by the next `DOWNLOADING` flip (status check now accepts `QUEUED || RETRYING`).
- **Status:** ✅ Confirmed + implemented. CI green.
- **Date:** Download system fixes session.

### D-196 — data.json write-back via DownloadScanner.reconcileDataJsonFromContent
- **What:** New `reconcileDataJsonFromContent` method in `DownloadScanner` — for each content folder, fetches the latest `ContentRecord` + `AniListDetail` + `ExtensionDetail` from the DB, compares key fields (description, dataSourceId, systemId, extensionRepoId, extensionId, sourceId, animeUrl, anilistId, coverUrl, title, contentType, contentFormat, displaySource) with the existing `.data.json`, and writes back if any field differs (only on change — avoids unnecessary SAF I/O). `requestFolderRescan()` is wired into `AnikutaApp.onCreate` (background IO scope, non-blocking) so the reconciliation runs on every app launch.
- **Why:** The user reported that `.data.json` files were not being updated with the latest metadata (description, dataSourceId, etc.) after the initial download. Root cause: `writeDataJson` was only called by `publishVideoFile` (for the NEW download's folder). OLD data.json files were never re-touched. `requestFolderRescan()` had ZERO callers — the scanner was dead code at runtime. This fix wires the scanner + adds the write-back so old data.json files get updated with the latest DB state on every app launch.
- **Status:** ✅ Confirmed + implemented. CI green.
- **Date:** Download system fixes session.

---

### D-197 — Database restructuring plan (PROPOSAL — not implemented)
- **What:** A full-fledged plan to restructure the database from 26 → 24 tables via 3 changes: (1) rename `content` → `main_entry` (identity hub, clearer name); (2) merge `anilist_detail` + `extension_detail` + `other_source_detail` → `data_source_detail` + `extension_detail` (Option C — two tables, keeping data source ≠ extension conceptually separate per user directive); (3) absorb `anime_metadata_cache` into `data_source_detail` (9/12 columns duplicated, 3 dead). Plus 11 independent improvements (drop 2 dead cols, fix 2 missing FKs, fix episode_number type, split display_source into active_data_source_type + active_extension_type, DataSourceExtras typed accessor, clearExtensionAxis unlink fix, etc.).
- **Why:** User wants the database simpler, better-named, future-proof (handles AniList/Kitsu/MAL/TMDB data sources + Aniyomi/CloudStream/Sora/MangaYomi extensions without schema changes), with data source ≠ extension kept separate. The current 3-table detail split conflates nothing at the schema level but is verbose; the merge simplifies + enables in-place source switching.
- **Design choice:** Option C (two tables) over Option A (one wide table) or Option B (two rows per content). Option C honors the user's "keep data source ≠ extension separate" directive at the schema level — each table has only columns relevant to its concept. Adding a new data source = UPDATE the row with a new `source_type` (zero schema change). Adding a new extension = UPDATE the row with a new `extension_type` (zero schema change for Long-ID extensions).
- **Review:** 4 iterations via sub-agents (NOT self-review per user instruction). Iter 1 found 1 FLAW + 9 CONCERNS. Iter 2A (architecture) + 2B (feasibility) parallel found 2 FLAWS + 11 CONCERNS. Iter 3 (sign-off) found 0 FLAWS + 7 minor. Iter 4 (confirmation) found 2 cosmetic. All fixed. Plan is presentation-ready.
- **Plan location:** `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md` (446 lines).
- **Dashboard:** `/database-plan/` page live at https://testplay-byte.github.io/ANI-KUTA/database-plan/ — shows every table, every column, every query, every con, every deferred item.
- **Status:** ⚠️ PROPOSAL — awaiting user approval. NO schema changes made. Implementation will be a separate session after approval.
- **Date:** Database restructuring plan session.

---

### D-198 — Database restructuring plan v2 (PROPOSAL — not implemented)
- **What:** Revised plan (from D-197) to restructure the database from 26 → 22 tables. Key change from v1: ONE wide `content_details` table (Option A — 26 cols, `data_*`/`ext_*` prefixes) instead of two tables (Option C). Also: drop `app_metadata` (dead code), keep `data_source`+`system` separate (R-2 recommendation), keep `extension_repo_id` (user directive), keep `display_source` as single UX-preference column (values `'data_source'`|`'extension'` — not split). 10-group presentation. 4 changes + 11 independent improvements.
- **Why:** User reversed v1's Option C decision — wants ONE unified `content_details` table that handles ALL content types (video/novel/image/manga) + ALL data sources (AniList/Kitsu/MAL/TMDB) + ALL extensions (Aniyomi/CloudStream/Sora/MangaYomi). Simpler than two tables, future-proof (zero schema change for new sources/extensions/content-types).
- **Table count:** 22 (above user's "under 15" preference). Research confirms remaining 22 tables are genuinely better separate — merging any would create sparse/awkward tables, break FK integrity, or corrupt backup semantics.
- **Review:** 4 iterations via sub-agents. Iter 1: 2 FLAWS fixed. Iter 2A+2B: 0 FLAWS + 7 CONCERNS fixed. Iter 3+4: APPROVED.
- **Plan location:** `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md` (v2, 446 lines).
- **Dashboard:** `/database-plan/` live — shows every table, column, query, con, deferred item.
- **Status:** ⚠️ PROPOSAL v2 — awaiting user approval. NO schema changes made.
- **Date:** Database restructuring plan v2 session.

---

### D-242 — Library cover badge system + advanced RELEASED options (implemented)
- **What:** A comprehensive library cover badge system with edge-to-edge design, theme-adaptive colors, and advanced RELEASED episode badge sub-options. Iteratively refined across fix9–fix14 based on user feedback.
- **Components:**
  1. **CoverBadgeData** — data class replacing `Pair<String, Pair<Color, Color>>` with `text`, `containerColor`, `contentColor`, optional `icon: ImageVector?`.
  2. **CoverBadgeRow** — renders multiple badges side-by-side in a single Row at a cover corner. Edge-to-edge (flush with cover corner), 8sp Bold text, dot separators between badges. Each badge can optionally render an icon (8dp) before the text.
  3. **BadgeIcons** — custom `ImageVector` definitions for `Sub` (subtitle/closed-caption: rectangle frame + 2 horizontal lines) and `Dub` (microphone: capsule body + U-shaped cradle + stand + base). Hand-crafted vector paths using only basic PathBuilder commands (no arcTo for cross-version safety).
  4. **ReleasedAudioFilter** — enum (BOTH, SUB, DUB) controlling which audio type's released episodes to show when badge mode = RELEASED.
  5. **LibraryEntry** extensions — `subEpisodeCount`, `dubEpisodeCount` fields + computed `subUnwatchedCount`, `dubUnwatchedCount` properties.
- **Badge rendering logic (LibraryGridCard):**
  - OFF: shows audio labels (SUB/DUB) as plain text badges (secondaryContainer).
  - TOTAL: shows "EP N" + audio labels.
  - RELEASED + BOTH: shows `[sub-icon] N` (blue) + `[dub-icon] M` (orange) side-by-side. Falls back to "EP N" only when no per-type data exists at all.
  - RELEASED + SUB: shows `[sub-icon] N` (blue). Falls back to released count.
  - RELEASED + DUB: shows `[dub-icon] M` (orange). Falls back to released count.
  - `releasedUnwatchedOnly` toggle: shows unwatched counts (released - watched) instead of total released.
  - Colors are theme-adaptive: SUB = blue (light: #90CAF9/#0D47A1, dark: #1565C0/#BBDEFB), DUB = orange (light: #FFCC80/#BF360C, dark: #E65100/#FFE0B2).
- **CustomizeSheet:**
  - Scroll-to-minimize header animation (like ProfileScreen): pinned header with animated font size (20sp→16sp) + mini tab pill that fades in on scroll. Tab strip as LazyColumn item 0 with `graphicsLayer` alpha/scale. Magnetic snap on scroll end. Collapse threshold = 40dp.
  - RELEASED sub-options section (conditionally shown when episodeBadgeMode = RELEASED): "Released Audio" label + 3 `ReleasedAudioFilterCard` items (Both/Sub/Dub with icons) + "Show unwatched only" SwitchRow.
  - BadgePosition selectors REMOVED — positions hardcoded (EP=TOP_END, Score=TOP_START).
  - DisplayModeCard uses horizontal layout (name LEFT, icon RIGHT).
- **Why:** User wanted edge-to-edge cover badges with episode count, score, SUB/DUB availability — theme-adaptive, compact, side-by-side. Then requested advanced RELEASED options: sub/dub/both selection with SVG icons (microphone for dub, subtitle for sub), different colors for sub vs dub, and option to show only unwatched episodes.
- **Review:** 3 sub-agents reviewed fix14 in parallel (data layer, CustomizeSheet UI, badge rendering). All found NO CRITICAL/WARNING issues. One logic bug found (BOTH+unwatched fallback showing "EP N" when user watched everything) — fixed.
- **Fix history:** fix9 (edge-to-edge + theme-adaptive), fix10 (badge data enrichment), fix11 (horizontal display cards + side-by-side badges), fix12 (remove position selectors + bold text), fix13 (scroll-to-minimize header), fix14 (advanced RELEASED options + SVG icons + unwatched toggle).
- **Status:** ✅ Implemented. Commit `db0535d0` on `functionality/improvements`. Version 0.2.37 (versionCode 37). Awaiting push + CI build.
- **Date:** Library badge customization session.

---

### D-243 — Video playback caching (local HTTP proxy + stable identity)
- **What:** A new `:core:playback-cache` module caches streamed video bytes locally so replays of the SAME video (same server + audio + resolution) start instantly from disk with zero network round-trips. Architecture: a NanoHTTPD proxy on 127.0.0.1 (ephemeral port, pre-started at app startup) sits between MPV and the upstream URL — MPV gets `http://127.0.0.1:PORT/v/<cacheKey>`, the proxy serves cached byte-ranges from `<filesDir>/playback-cache/<key>.bin` and fetches missing ranges upstream (tee'ing into the file, positional FileChannel writes). New `playback_cache_entry` SQLDelight table (23→24 tables) with reactive queries + driver-factory guard for upgrade installs.
- **CRITICAL design points:**
  - **Stable identity, NOT URLs**: extension localhost proxy URLs change every resolve (D-066) — cache key = sha256(mainId + episodeNumber + sourceId + "server|audio|quality" from ResolverVideo.videoTitle minus the volatile urlHash). videoTitle is the codebase's documented stable-identity string.
  - **Live episode state**: the identity's episodeNumber comes from PlayerStateHolder/the new episode at switch time — NEVER the frozen watchKey.episodeNumber (wrong-episode cache corruption).
  - **Fail-open everywhere**: pre-loadfile errors → original URL; proxy pre-body errors → 301/302 redirect to upstream (ffmpeg follows; D-199 global headers keep working); mid-stream → connection close. The cache can never permanently break playback.
  - `Accept-Encoding: identity` on ALL upstream fetches (byte-offset integrity); header-setting code in WatchScreen untouched (D-199/D-095); fd:///content:// bypass; free-disk guard.
  - LRU eviction (limit 100 MB..2 GB, default 512 MB, active-stream-safe via atomic DELETING state + deferred delete); stale-file verification (missing/truncated .bin, content-length mismatch → reset).
- **Settings:** dedicated "Video caching" screen (Settings → Player section): master toggle (default ON), storage-limit slider, usage summary, cached-episodes list with per-entry "Cached: start → X · N% of total (+k segments)" display + delete + clear-all.
- **Why:** User request (test-feature branch): replaying the same episode/server/resolution should "load up the cached one first and start playing directly... without any processing".
- **Plan:** `APP/ani-kuta/DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` (Part A) — reviewed 2 rounds by 5 sub-agents (PR-A/PR-B/PR-C + PR-2A/PR-2B); compile review CR-A (3 compiler-caught errors fixed).
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit 95909b12, CI green). Awaiting user device verification — NOT merged to main.
- **Date:** Video caching + parallel download session.

---

### D-244 — Parallel download engine (MPV-inspired multi-connection downloads)
- **What:** A new multi-connection download method wired to the previously-DEAD "Advanced downloader" settings prefs (`advancedDownloader`/`advancedThreads`/`advancedMaxRetries` existed with UI but zero engine references). HttpDownloader becomes the FACADE (routing/validation/publish/.data.json unchanged); a new `VideoFetcher` seam makes only the "bytes → temp File" stage pluggable:
  - `SingleConnectionFetcher` — today's downloadNormal extracted verbatim (Range-resume, re-resolve recursion, HttpException mapping).
  - `ParallelHttpFetcher` — Range probe (bytes=0-1 GET), N chunk workers (advancedThreads, connection-budget-capped ≤16 per queue), positional writes into a pre-allocated sparse temp file, per-chunk exponential backoff (2^n capped 30s), premature-EOF/range-mismatch/50KB-s-stall handling, active-call registry (Call.cancel teardown), re-resolve on ANY HttpException for localhost (incl. 403 — the primary proxy-churn case), chunk sidecar (`video.<ext>.chunks`) for pause/resume, single-stream fallback for Range-ignoring servers, dedicated 250ms progress-reporter coroutine (DownloadQueue's onProgress lambda mutates non-thread-safe state — workers only touch an AtomicLong).
  - `HlsDownloader` parallel mode — concurrent segment workers + ordered writer (spill files, semaphore-bounded), **in-memory AES-128-CBC decryption** (EXT-X-KEY + EXT-X-MEDIA-SEQUENCE default-IV derivation, 16-byte alignment validation, rotating-key rejection, PNG-strip BEFORE decrypt), append-state sidecar (`video.ts.hls-state.json`) with playlist-stability validation, and the variant-URL base fix (pre-existing relative-URI bug). Legacy mode preserved byte-for-byte.
  - Engine-switch safety: both directions detect foreign sidecars → restart clean (sparse files never published with holes).
- **Settings:** the existing Downloads → Advanced section (toggle + threads + retries sliders) — default flipped ON (the engine is the point; easy off-switch back to legacy).
- **Why:** User request (test-feature branch): an MPV-inspired high-performance download method per the shared article (connection pooling, parallel ranges, concurrent HLS, in-memory decryption, adaptive buffers, exponential backoff) — adapted to OkHttp (pooling/keep-alive/HTTP-2 come free).
- **Plan:** `APP/ani-kuta/DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` (Part B); compile review CR-B (compiler-verified: 4 compile errors + a Semaphore double-release runtime crash + probe-outside-re-resolve + sidecar cleanup — all fixed pre-push).
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit 5cedad58). Awaiting CI + user device verification — NOT merged to main.
- **Date:** Video caching + parallel download session.

---

### D-245 — Video caching session-2: always-cache serving, HLS playlist rewriting, background fill, tap-to-play
- **What:** Fixed the user-reported "episode registered but never cached" defect + delivered the two requested enhancements, on `test-feature/video-cache-new-download` (commit 23a93c8b).
- **Root causes (all fixed):** (1) unknown-Content-Length → 301 redirect upstream = playback OK but zero caching — replaced by learn-mode serving (mirror the client's Range upstream, learn the total from the response; chunked-with-tee when even then unknown; the separate 0-1 probe is GONE); (2) HLS playlists only proxied the playlist text — segments (absolute URLs) bypassed the cache — the proxy now REWRITES playlists (variants → /p/&lt;key&gt;/&lt;i&gt;, segments + EXT-X-MAP → /s/&lt;key&gt;/&lt;i|init&gt;) and caches per-segment files (URL-hash named, drift-safe; BYTERANGE playlists bypass, logged; live playlists don't fill).
- **Background fill:** per-entry job fetches remaining gaps (progressive, 8 MB blocks, player-frontier-aware ±32 MB) or segments (HLS VOD) until complete — "while it is playing, everything else loads". Segment stats recounted from disk (race-safe).
- **Tap-to-play:** entries now carry subtitle/audio track lists (4 new ALTER-guarded columns) — settings rows are clickable → full WatchKey rebuilt → same server/quality/resolution (cache identity guarantees it) + WP-B3 resume from watch progress. Known v1 limits: no episode switching from cache-origin launches; a dead stored upstream URL fails like any dead link (reopen from Details to refresh).
- **CR-C compile probe (real jars, EXIT 0) caught pre-push:** response.use{} closing the streaming body early (critical — dead stream on learn-mode serves); segment-stat races; variant-base-URL resolution.
- **Logging:** every decision point logs under `Anikuta:Core:PlaybackCache` (play/serve/learn/parts/gap/tee/flush/complete/hls/seg/fill/evict/delete/fail-open) — user-debuggable via `tag:Anikuta:Core:PlaybackCache` in Android Studio.
- **Status:** ✅ Implemented on the branch; CI pending at doc time (see progress.md). NOT merged to main.
- **Date:** Video caching session-2.

---

### D-246 — Download network resilience + instant teardown + cache identity persistence + sandbox emulator test environment
- **What (user-reported defects, all fixed on `test-feature/video-cache-new-download`, commit 512279ee + cf4a8a6f):**
  1. **Downloads break on Wi-Fi loss and never auto-restart when the internet returns.** Root causes: (a) `onNetworkChanged` paused active tasks but never resumed them (PAUSED tasks are invisible to `tryStartNext`); (b) the DownloadService stopped itself once everything was paused, killing the NetworkCallback that would have fired the resume; (c) transport errors during an outage burned retries into ERROR. Fixes: `networkPausedTasks` set (pause + remember on loss; auto-resume on regain; user-initiated pause/cancel/pauseAll/cancelAll remove entries — user intent wins), service stays alive while network-paused tasks exist, transport-error-while-offline → PAUSED instead of retry/ERROR, CallRegistry instant teardown (OkHttp `Call.cancel()` on coroutine cancellation — blocked socket reads no longer wait out the 60s read timeout on pause).
  2. **Downloaded size sometimes exceeds total.** The reported total is an estimate (HLS running-average lag, stale persisted totals). Fix: when downloaded exceeds the reported total, the total grows to reality (bar never >100%); `retry()` now also clears the persisted tracker state.
  3. **Cached videos still load from the network (not instant).** The cache identity was rebuilt ONLY from the in-memory ResolvedVideosRegistry — empty in every new app session → identity null → cache silently bypassed on replay. Fix: conservative cross-session identity recovery from a prior cache entry (`findEntriesByIdentity`; single prior entry + matching quality only — ambiguity skips caching rather than risk wrong-content corruption).
- **Sandbox emulator test environment (user-authorized + user-requested):** Android SDK cmdline-tools + platform-tools + emulator + API 30 google_apis x86_64 system image installed in the sandbox (`/home/z/android-sdk`); AVD `anikuta` (720x1280, 1536MB). x86_64 TCG works without KVM (cold boot ~8 min; system_server ANRs are common under 2-core TCG — dismiss with "Wait"). The arm APK runs via libndk_translation (slow); CI now ALSO produces a TEST-ONLY `app-debug-x86_64-emulator.apk` (separate artifact, `-PemulatorX64Build=true`) for native-speed emulator testing — the shipped APK stays arm-only (CORE_RULES §8 exception documented there). adb usage note: wrap every command in `timeout -s KILL N adb ... < /dev/null` (plain adb shell hangs the sandbox's persistent shell).
- **Status:** ✅ Implemented. Commits 512279ee (fixes) + cf4a8a6f (x86_64 CI artifact). CI green for 512279ee (run 32619494659); cf4a8a6f run pending at doc time. NOT merged to main.
- **Date:** Download-resilience + emulator session (2026-08-23).

---

### D-247 — Progress-window caching: cache [pos−2min, pos+2min], never more
- **What (user report: "it caches the whole segments, all available segments of the video"):** both over-caching vectors eliminated on `test-feature/video-cache-new-download` (commits 88abfe34 + 1d82693d, CI green 32646499477 + 32648121661):
  1. **The background fill** chased the entire remaining file → now a tick-based WINDOW fill (one 8MB block / one segment per 2s tick, self-paced): only gaps within [pos−120s, pos+120s], behind-gaps first (MPV never fetches those itself). Idle-exits after 60s without progress; re-triggers every 30s of position movement.
  2. **MPV read-ahead through the serve path** (the bigger one — it streamed + teed the WHOLE file): the tee now writes only below the window ceiling (pos+120s, re-snapshotted per ranged request); for HLS, segments beyond the window end index are SERVED but NOT cached. Emulator-verified with real Anikoto playback: window 0..11 engaged (12 segments, 23MB) while segments #12→#50+ streamed through as pass-through ("BEYOND window — served, NOT cached") — the old code cached all 145 segments.
  3. **Pre-playback fallback cap** (found during emulator testing): before FILE_LOADED fires (slow decode), the window is unknown — MPV's demuxer read-ahead had cached 43MB unbounded. Now bounded by a 32MB byte budget (matches the progressive tee fallback) until position starts flowing.
- **Mapping:** HLS = EXACT via EXTINF cumulative start-times (parsed during playlist rewrite); progressive = proportional byte↔time (CBR approximation). Duration unknown → bounded fallback, real window once WatchScreen pushes position+duration (~1Hz via onPlaybackProgress).
- **Interpretation documented:** watched-path bytes (below the sliding ceiling) stay cached — that IS the instant-replay feature; the PROACTIVE bounds are strict (window ∪ watched-path, never the whole episode ahead of the user).
- **Cache-failure fallback:** if MPV errors while playing through the proxy, the FIRST auto-retry bypasses the cache entirely (direct network loadfile); manual retries stay direct until a successful READY re-arms the cache. Plus all existing fail-opens (pre-loadfile → original URL; pre-body → 301 redirect; mid-stream → close).
- **Also fixed (CR-E):** phantom cached ranges (registerCached ran even when the disk write failed → truncated disk-slice serves; now register-after-successful-write only) + Int-vs-Float progress push (compile-breaker caught by the standalone-jar compile probe).
- **Future direction noted (user's idea, NOT built):** preloading of expected episodes (next-in-series, continue-watching) using this cache so playback starts zero-buffer — the window machinery + identity system are the foundation.
- **Extension compatibility (emulator-verified):** BOTH user extensions install + load + trust + search: Anikoto v14.4 (PRIMARY — full flow works: 30 search results, details, resolve → 4 videos w/ Vidstream-2/HD-1 SUB/DUB 1080p + 20 subtitle tracks, playback through the cache proxy) and AniKoto180 v16.9 (loads + trusts + appears in the source picker). The two APKs are saved in `USER-UPLOADS/extensions/` in the repo (per user request).
- **Status:** ✅ Implemented + emulator-verified end-to-end with real extension playback. NOT merged to main.
- **Date:** Video-caching window session (2026-08-23).

---

### D-248 — UX improvements: continue-watching direct play, honest profile stats, library cover fixes, download hardening, search page fixes
- **What (six user-reported areas, all root-caused via research agents R-A..R-E + compiler-verified by CR-F standalone-jar probes; commits 0650135f + 4f367a81, CI green 32655570777):**
  1. **Continue Watching → player directly** (Browse carousel): tap resolves the episode in the background (source auto-pick identical to the watch screen's episode-switch path) + launches Watch with startPosition resume; falls back to the legacy Details route when unresolvable. Same experience as the Video Caching tap-to-play (user's benchmark).
  2. **Profile statistics honesty** (the "2,333 episodes watched in one day / 52 min" glitch): root cause = AniList-sync + manual bulk marks INSERT watch_progress rows with last_watched_at=now + duration=0 (syncLocalProgressFromTracker fires on EVERY details-open for tracked anime), and EVERY profile stat counted raw rows. Fix: a `countedProgress` set = organic only (`!userMarkedWatched && duration > 0 && progressFraction >= 0.10`); manual marks NEVER count (user directive: tracking lists update normally, profile stats never); <10%-watched never count; Recently Watched requires >20%. All stats (totals, watch flow, Time DNA, heatmap, streak, timeline) use the counted set. duration=0 rows fail automatically.
  3. **Library covers**: (a) two-way fallback at all 5 Library sites (dataCoverUrl ?: extThumbnailUrl + inverse — was single-axis → null-on-preferred-axis = coverless forever); (b) ROOT CAUSE of covers vanishing on restart: DownloadScanner.upsertAniListDetail overwrote the ENTIRE data_* axis with nulls on EVERY startup scan (wiping data_cover_url) → now merges with the existing row (.copy) + seeds cover from .data.json on fresh rows; (c) Details refresh: AniList branch could silently no-op on missing rows + null-out fields the fetch didn't carry → now ensure-row + merge-with-existing (nulls preserve); extension branch NEVER persisted → now persists the ext_* axis the same way.
  4. **Download vanish hardening** ("downloads disappear even though files exist"): (a) ANTI-SHRINK guard — when the file walk rebuilds fewer episodes than the durable .data.json lists (transient SAF listing failure), keep the durable list + protect ALL its keys from orphan cleanup (old code replaced .data.json with the shrunk list + deleted the surplus rows = PERMANENT loss, unfixable due to the D-242 orphan-skip); (b) any unreadable folder suppresses the whole orphan-cleanup pass; (c) scan mutex (concurrent scans raced .data.json read-modify-write).
  5. **Search page**: trending auto-loads on first AniList entry (was: empty until re-tapping the AniList chip — the D-242-fix7 no-auto-load rule existed only because recents were Idle-exclusive); Recent searches now persist while browsing results as the results grid's full-span header item (scrolls away with content; top bar already collapses to title + compact search bar) and hide only when the user actually searches.
  6. **Time DNA layout**: donut 100→112dp, panel padding 8→10dp, donut→legend spacing 8→16dp, legend rows 4→6dp.
- **CI fix en route**: cross-module smart-cast (`item.anilistId` from :feature:anime-browse:impl in :app) — captured to a local val. (CR-F's probe compiled the HELPER verbatim but the fallback branch slipped; the smart-cast rule is now a known lesson.)
- **Status:** ✅ Implemented, CI green. NOT merged to main — awaiting user device verification.
- **Date:** UX-improvements session (2026-08-23).

---

### D-249 — Continue-watching lazy-init fix, Updates UI overhaul, Browse page redesign
- **What (user feedback session; commits 222c0b2e + e6f9f0e4 + 552e06de, CI green 32660716135):**
  1. **Continue-watching direct-play fix** (user: "still opened Details"): ROOT CAUSE = ExtensionManager is a LAZY Koin singleton; the helper was its FIRST resolver on cold start → loadAll()'s async source-map population hadn't finished → getSource() returned null → instant fallback. Fix: `extensionManager.sources.first { it.containsKey(sourceId) }` wrapped in `withTimeoutOrNull(10s)` — awaits the StateFlow emission (already-loaded = immediate).
  2. **Updates UI overhaul** (user: "arrangement needs work, too tall, title should be 1 line"): compact HistoryRow-style anatomy — right column height-locked to the cover (80dp, SpaceBetween), title 1-line only (was 2-line), EP + SUB/DUB pill inline + time-ago on a clean bottom band (was 4 stacked elements). + NEW Clear-all button (deleteAllUpdates query + UpdateStore + VM + broom icon in the header, visible only when the Updates tab has content).
  3. **Browse page complete redesign** (user: "way too basic, ugly, bad"): NEW AniListApi.fetchBrowseSection(sort) (sorted browse WITHOUT search — returns bannerImage + genres + seasonYear + status); multi-section BrowseViewModel (Trending + Popular + Top Rated, each cached independently in browse_cache); NEW layout: Hero banner (top trending — banner image, gradient scrim, #1 TRENDING badge, score/eps/year/genre pills, tap→Details) → Continue Watching carousel → Trending Now → Popular → Top Rated (horizontal card carousels with score-badge overlays, press-scale, 1-line titles, year·status subtitles).
- **CI fixes en route:** missing `kotlinx.serialization.json.int` import + missing `kotlinx.coroutines.flow.first` import (the StateFlow predicate await).
- **Status:** ✅ Implemented, CI green. NOT merged — awaiting user device verification.
- **Date:** UX-fixes session (2026-08-23).

---

### D-250 — Settings-UI icon unification: bare-icon nav rows everywhere + BackAction dedup
- **What (user feedback: More page icons "proper SVG", Settings page icons "some other kind of format, not good"; same for Appearance + other sub-pages — make consistent + cleaner):**
  1. **Root cause**: the More page uses `MoreListRow` (`:core:designsystem`) — bare 24dp `Icon` tinted `primary`, NO container. The Settings/Appearance/Notifications hubs each defined a LOCAL `*NavRow` (`SettingsNavRow`, `AppearanceNavRow`, `LibraryNavRow`) that wrapped the icon in a 36dp `primaryContainer` rounded box ("chip-box") — a *different visual format* even though the same `Icons.Filled.*` glyphs were used. Plus ~12 copy-pasted `private fun BackAction` duplicates across `:app` + `:feature:extensions-settings:impl` (2 with missing `Modifier.size(18.dp)` → icon rendered 24dp; 1 divergent variant in TrackersScreen: transparent bg + `onBackground` tint + 20dp).
  2. **Fix**: (a) **Reused `MoreListRow` directly** in the 3 hubs — deleted the 3 chip-box `*NavRow` defs + swapped all 11 call sites to `MoreListRow(icon=, title=, subtitle=, onClick=)`. Rationale (§5): two composables doing the exact same thing = unrequested abstraction; the settings nav rows ARE the More rows visually now, so share the component. (b) **Promoted `BackAction` to `:core:designsystem`** as `fun BackAction(onBack: () -> Unit, modifier: Modifier = Modifier)` (36dp CircleShape `surfaceVariant` + 18dp `Icons.AutoMirrored.Filled.ArrowBack` tinted `onSurfaceVariant`). Replaced all 12 private copies + 3 inlined bodies (DetailsPage, Trackers, ExtensionRepo). Fixes the 2 missing-size drifts + the Trackers divergent variant. (c) **Fixed the lone feature-module chip-box**: `AutoLinkSettingsScreen.PerExtensionCard` (32dp `primaryContainer` + 18dp `AutoAwesome`) → bare 24dp `Icon` tinted `primary`. (d) **Bug fix**: `NotificationsSettingsScreen.triggerDescription` SILENT branch was a copy-paste of the ON branch (`"Notify $condition"`) → now `"Notify silently $condition"` (matches the correct version in `NotificationsLibraryScreen`). (e) **Dead code removed**: `ConfigSegmented` (never called) from `NotificationsLibraryScreen`; dead `ImageVector` import from `EpisodeListSettingsSheet`. (f) **Layout fix**: moved `LibraryNavRow` OUT of its `SettingsGroupCard` (MoreListRow's baked-in 16dp h-padding would double-pad inside a card) → standalone `MoreListRow` sibling, still wrapped in the existing `AnimatedVisibility`.
  3. **Scope**: 17 files touched (1 new `BackAction.kt` + 11 `:app` settings screens + 4 `:feature:extensions-settings`/`:feature:anime-details` files + 1 dead-import removal). Compile review (sub-agent Task 6) = ✅ PUSH-READY, zero errors. ~30 now-dead imports flagged (Kotlin doesn't fail on unused imports + no `ktlint`/`detekt` config = CI passes; tidy follow-up queued).
- **Design rule established**: `DESIGN-LANGUAGE.md` §2.4 "Nav-Row Icon Language" — every nav-row slot (More + Settings hubs) reuses `MoreListRow` with bare 24dp primary-tinted icons; no per-screen `*NavRow` variants; no `primaryContainer` chip-box. Every settings sub-screen's `CollapsingHeader` uses the shared `BackAction`.
- **Deferred (follow-ups, out of icon-fix scope)**: (a) `SourcePreferencesScreen` + `ExtensionRepoSettingsScreen` dead `collapsed = false` + `scrollOffset = { 0f }` (header never collapses / blur never triggers — wiring needs a `LazyListState`/`PreferenceList` scroll-model change); (b) ~30 dead-import tidy commit; (c) `VideoCachingScreen.kt:349` stale comment.
- **Virtual-device testing**: user asked for details — the sandbox emulator env ALREADY EXISTS at `/home/z/android-sdk` (AVD `anikuta`, API 30 AOSP x86_64 TCG) from the D-246 session; CI produces an `app-debug-x86_64-emulator.apk` artifact (D-246 exception). Full extension-install + playback flow was emulator-verified E2E in the D-246 session.
- **Status:** ✅ Implemented, sub-agent compile-reviewed (PUSH-READY). CI pending push. NOT merged — awaiting user device verification of the unified icon look.
- **Date:** Settings-UI icon-unification session (2026-08-24).

---

### D-251 — Dead-wiring fixes + Library display modes + arm64-only releases + update-checker fix + emulator rebuild
- **What (user feedback session, 5 work items):**
  1. **Dead collapsed/blur wiring FIXED** (D-250 follow-up): `SourcePreferencesScreen` + `ExtensionRepoSettingsScreen` both had `collapsed = false` hardcoded (+ `scrollOffset = { 0f }` / missing overlay). Now wired with the canonical pattern: `rememberLazyListState()` + `collapsed = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 20` → `CollapsingHeader(collapsed = collapsed)`; `state = listState` on the LazyColumn; `ScrollBlurOverlay` (inner Box, `align(TopCenter)`, `if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()`). SourcePreferences hoists the state into `PreferenceList(listState)`; ExtensionRepo gained the overlay it never had. The old SourcePreferences overlay was in the OUTER Box (would scrim over the header) — relocated inside.
  2. **Library Comfortable "Hide Titles" toggle**: new pref `library_comfortable_hide_titles` (SharedPreferences via PreferenceStore, mirrors ComfortableBorderMode pattern). CustomizeSheet Display-tab `TwoWayButton` visible ONLY in COMFORTABLE_GRID (after Title lines; Title lines hides when titles are hidden — no dead controls). `LibraryGridCard(hideTitles)` skips the title Text — keeps 12dp rounded corners + staggered spacing (a "cover-only look, but soft", explicitly distinct from COVER_ONLY).
  3. **Library Cover Only rework**: square covers (`cardShape = RectangleShape` when COVER_ONLY, applied at all 5 shape sites: card clip, outer/cover border modifiers, image clip, selection border) + ZERO gaps (both grid axes `Arrangement.spacedBy(0.dp)`) + full-bleed contentPadding (no side/top padding; bottom kept for nav-bar/action-bar clearance). All gated on `isCoverOnly` — COMPACT_GRID (sharing the branch) unchanged.
  4. **77 verified-dead imports removed** across 15 files (D-250 fallout + older leftovers — incl. dead `NavKey`, `collectAsState`, TrackersScreen pre-existing pile). Audit sub-agent verified each against file bodies first (kept `getValue`/`setValue` delegate imports + still-used `CircleShape`). Stale VideoCachingScreen helper comment fixed.
  5. **Release/versioning overhaul (user instructions)**: version 0.2.47 → **0.2.48** (+1 per improvement batch); shipped APKs now **arm64-v8a ONLY** (armeabi-v7a dropped — AndroidConfig.abiFilters + build-apk.yml Verify-ABIs + CORE_RULES §8); NEW `release-apk.yml` publishes STABLE GitHub releases on `v*` tag push (tag↔versionName verified; asset `ani-kuta-vX.Y.Z.apk`; never prerelease; `--latest`); build-apk.yml x86_64 emulator build now opt-in via `workflow_dispatch` input only (never on pushes).
  6. **Check-for-Updates fixed (app side)**: `GitHubUpdateSource` used `/releases/latest` which EXCLUDES prereleases — every release after v0.2.6 was prerelease=True → the app saw v0.2.6 as latest and reported up-to-date. Rewritten: fetches `/releases?per_page=30`, filters drafts, picks best release (`maxWithOrNull(compareBy(versionTuple, stable-beats-prerelease))`), tuple-based version comparison (old `major*10000+minor*100+patch` packing collides at patch ≥ 100).
  7. **Sandbox emulator rebuilt + verified** (sandbox had been reset): overlayfs rootfs triggers a paranoid 7372.80MB userdata pre-check (actual usage ~350MB) → LD_PRELOAD statvfs shim (`/home/z/emu/freedom.so`, inflates free space for `.avd` paths only); forced 2048MB guest RAM → `-qemu -m 1024` override (qemu takes the LAST -m); no KVM → `-accel off` (TCG); archived emulator 35.1.19 installed. Verified: cold boot ≈9 min, boot_completed=1, home screen renders, RSS ~1.8GB peak, graceful shutdown. One-command helper `/home/z/emu/emu.sh`.
- **Key decisions**: (a) Hide-Titles is a Comfortable-scoped toggle (NOT a new display mode) — keeps the mode count at 4 and the enum stable (persisted by name); (b) Cover Only = full-bleed edge-to-edge wall (no screen-edge padding either — maximal "close together" per user's wording; Instagram-grid style); (c) the update checker surfaces prereleases (fixes detection) while the release workflow publishes stable (fixes distribution) — belt and suspenders; (d) release APKs remain debug-signed (same signature as all prior CI-artifact builds — release signing is Phase 2).
- **Status:** ✅ Implemented, sub-agent compile-reviewed (zero blockers). CI pending push. NOT merged — awaiting user device verification.
- **Date:** Library/releases/emulator session (2026-08-24).

---

### D-252 — Pointed cover badges + corner-aware COVER_ONLY (unified badge language)
- **What (user request: "the episodes tags in the cover only mode need to be handled properly and make pointier"):**
  1. **The "not handled properly" root cause**: `CoverBadgeRow`'s outer shape hard-coded a 12dp outer corner to hug the old rounded covers — on COVER_ONLY's SQUARE covers (D-251) that left a curved sliver of cover art visible behind each badge corner and the badge never reached the corner pixel. New `coverCornerRadius` param: 0.dp for COVER_ONLY, 12.dp for rounded modes; the old 4dp inner-corner rounding (which clipped the pointed tip's base) removed.
  2. **Pointed design**: the chip nearest the cover CENTER tapers into a 45° triangle tip via the new `PointedTagShape` (RTL-aware, tip depth = height/2, extra +4dp padding keeps text clear of the transparent tip) — badges read as pointed flags pointing INTO the cover. Compound sub/dub badge: `clip(pointedShape)` BEFORE `drawBehind` (M3 Surface applies its own clip AFTER user modifiers — the old order would spill the split-painting past the tip; caught by the plan-review agent).
  3. **Shared badge language**: `BadgeColorScheme` moved :feature:anime-library → :core:designsystem/badge (Browse reuses the amber score colors; 2 consumers = justified move, precedent BackAction D-250). Dark/light detection now follows the APPLIED theme (background luminance) instead of `isSystemInDarkTheme()` (the app allows forcing a mode ≠ system).
  4. Dead legacy `CoverBadge` composable removed (zero callers); stale 8sp KDoc fixed (9sp).
- **Scope**: LibraryScreen.kt (CoverBadgeRow + 4 call sites + badgeCornerRadius threading) + 2 new designsystem files.
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit d1152736). Compile-reviewed (Task 7) — clean. Awaiting CI + user device verification. NOT merged to main.
- **Date:** 2026-08-25 (browse-overhaul session).

---

### D-253 — Complete Browse page UI overhaul (hero pager + cards + skeletons)
- **What (user request: "complete UI overhaul of the browse page... beautiful, modern, cleaner, much easier to navigate... top banner image or the hero section... proper beautiful smooth animations... database properly managed" + rating tags "ugly" + "a bit of borders" on covers):** D-249's redesign was "a little bit" — this is the full redo. BrowseScreen.kt (581 lines) split into 4 files (§5):
  1. **BrowseHero.kt** — full-bleed edge-to-edge 260dp `HorizontalPager` over the top-5 trending-with-banner items (VM `hero` → `heroItems`); auto-advance every 6s (LaunchedEffect keyed on currentPage restarts the timer; isScrollInProgress guard; single item = no pager mechanics); animated page dots (active elongates to a 16dp pill); rank pill (#N TRENDING, D-215 recipe, no emoji per NavIcons rule) + 24sp ExtraBold title + integer-score meta row + genre pills over a stronger scrim (0.55/0.97 stops).
  2. **BrowseCards.kt** — 2:3 covers with standardized 12dp corners (was inconsistent 18/14/10) + NEW 1dp outlineVariant@60% borders; **rating tag REPLACED**: the old hard-coded black-65% pill with lime text → the amber pointed corner tag from the shared badge language (D-252), flush at top-start (outer corner clipped by the cover Box's 12dp clip); integer AniList score (0-100, unified with Library); CW cards: same borders + center play affordance (32dp primary circle) + press-scale (had none).
  3. **BrowseSkeleton.kt** — shimmer skeletons (reversed alpha pulse 0.35↔0.75 on surfaceVariant, 1200ms) mirroring the real layout instead of the full-screen spinner (§22).
  4. **BrowseScreen.kt** — sections fade+expand in via AnimatedVisibility when data arrives (no pop-in); error = EmptyState + Retry button; DB-7 debug block, PTR haptic, ScrollBlurOverlay, CW direct-play contract (onPlay → AniList → Extension) preserved exactly.
  5. **BrowseViewModel** — cache reads + JSON parse + CW enrichment moved to Dispatchers.IO (main-thread SQL smell); `refresh()` now PARALLEL (was sequential = 3× slower than init); isRefreshing in-flight counter (fixes the first-finisher-clears-spinner race). Read-through + 6h TTL semantics unchanged; no API/schema changes.
- **Status:** ✅ Implemented (commit 4230821c). Compile-reviewed (Task 7) — clean. Awaiting CI + user device verification. NOT merged.
- **Date:** 2026-08-25.

---

### D-254 — Custom palette editor (per-element theme customization)
- **What (user request: clicking Custom again opens a bottom menu below the palettes where the user can customize the background, accent, top headings, and each element/block color, each with brightness):**
  1. **Model**: NEW `CustomThemeColors` (designsystem) — accent/background/heading/card Colors + 4 brightness Floats (−1..1; `applyBrightness` lerps toward white/black, applied AFTER the base color). Defaults mirror the dark theme (lime on warm darks).
  2. **Scheme derivation** (`buildCustomColorScheme`): one pick → a coherent theme — accent family via the existing `AccentColors.from` derivation; text colors by background LUMINANCE (dark text on light picks, light on dark); surface ramp = background lerped toward text (4/8/12/16%); card family → surfaceVariant/containers; outline lerps. Custom applies in BOTH light & dark mode (mode toggle affects only presets); AMOLED skipped while custom is active (custom background wins — documented in the sheet).
  3. **Heading color**: `LocalHeadingColor` static CompositionLocal (Unspecified sentinel → default onBackground); `CollapsingHeader` titles read it.
  4. **ColorPickerSheet promoted** :core:player → :core:designsystem with a `swatches: List<Pair<Int, String>>` param (default = the player's subtitle palette — zero behavior change for the single player call site); the theme editor passes theme-appropriate swatches per element + forces alpha opaque on live-apply (translucent theme surfaces would break scrims).
  5. **Persistence**: ThemePreferences `customTheme` mutableStateOf loaded from 8 pref keys (4 ARGB + 4 Floats via PreferenceStore.getFloat); legacy custom-accent key seeds the accent (migration); `setCustomTheme` persists + updates state.
  6. **Live apply**: MainActivity passes `customTheme` to AnikutaTheme when preset == CUSTOM — reading `prefs.customTheme.value` inside setContent subscribes the whole app to every editor change (§23 live verification).
  7. **UI**: NEW `CustomPaletteSheet` (bottom sheet, dragHandle null, 65% height cap, scrollable) — live mini-preview (heading + card block + accent pill from the current config, brightness applied) + 4 element editors (swatch button → nested ColorPickerSheet; brightness Slider −100..+100 with +/- readout) + Reset button. `AppearanceGeneralScreen`: re-tapping the ALREADY-selected Custom card opens the sheet; the Custom card's selected badge is a palette (edit) icon instead of a check; stale "static placeholders" ponytail KDoc removed.
  8. **Known caveat**: status-bar icon contrast follows the SYSTEM dark mode (enableEdgeToEdge SystemBarStyle.auto) — a light custom background under system-dark shows light icons. Pre-existing class of issue; revisit at production polish.
- **Status:** ✅ Implemented (commit 7ef10689). Compile-reviewed (Task 7) — 2 errors caught + fixed pre-push (staticCompositionLocalOf is a function not a type; missing @OptIn(ExperimentalMaterial3Api) on CustomPaletteSheet). Awaiting CI + user device verification. NOT merged.
- **Date:** 2026-08-25.

---

### D-255 — Device-feedback fixes: palette-navigation + custom-palette crash + update-check java.time (with the Compose version-skew discovery)
- **What (user device feedback on D-252/253/254, all root-caused with evidence):**
  1. **Palette selection navigated to Browse.** ROOT CAUSE: D-254's `AnikutaTheme` wrapped `content` in `if (headingColor != null) { CompositionLocalProvider { MaterialTheme } } else { MaterialTheme }` — selecting/deselecting the CUSTOM preset flips `customTheme` null↔non-null, MOVING the content between two composition branches. Moving content between call sites destroys every `remember{}` under it — including AppRoot's nav backstack → the app "reset" to Browse. FIX: always provide `LocalHeadingColor` (Unspecified sentinel for presets) through ONE stable structure; only the VALUE changes.
  2. **Custom palette crashed** (`NoSuchMethodError: FlowRow` at ColorPickerSheet.kt:169). ROOT CAUSE — **the Compose version-skew discovery (verified against the CI APK's `META-INF/androidx.versions`)**: the app's RUNTIME compose stack is **1.10.4** (foundation, foundation-layout, runtime, ui) + activity 1.12.4 + lifecycle 2.10.0, while the toml/BOM declares **2025.03.00 → 1.7.8** / activity 1.10.1 / lifecycle 2.8.7. The puller: **koin-compose 4.2.2** transitively depends on `org.jetbrains.compose.foundation:foundation:1.10.2` (JetBrains aliases → androidx artifacts) + koin-compose-viewmodel → `lifecycle-viewmodel-compose:2.9.6`; transitive versions beat the BOM's prefer-constraints at app resolution. Consequence: modules WITH koin-compose (search/details/watch/app…) compile against 1.10.x; **`:core:designsystem` and `:core:player` DON'T** → they compile against 1.7.8 → any API that changed 1.7.8→1.10.x crashes (foundation-layout 1.8 added `itemVerticalAlignment` to FlowRow and removed the old overload). The old player ColorPickerSheet was equally broken (latent — never device-tested). FIX: ColorPickerSheet's FlowRow → manual chunked Rows (version-proof); also fixed the pre-existing preview channel-order bug (`Color(a, r, g, b)` rotated channels — Compose's Int overload is (red, green, blue, alpha)).
  3. **Update-check java.time crash risk**: `GitHubUpdateSource.parseIsoDate` used `java.time.OffsetDateTime` with a comment claiming "API 26+ is our minSdk" — **minSdk is 24**, and `NoClassDefFoundError` is an `Error`, NOT caught by `catch(Exception)` → crash on Android 7.x during update checks. FIX: regex + `java.util.Calendar` (UTC) — works on every API level. (HistoryViewModel/ScheduleViewModel/ScheduleStore have the same latent java.time issue — separate follow-up.)
  4. AMOLED toggle hidden while CUSTOM is active (ignored by design — no dead controls).
- **⚠️ OPEN DECISION FOR THE USER — BOM alignment:** the version skew remains: compile 1.7.8 vs runtime 1.10.4. Options: (a) bump composeBom to the 1.10.4-era BOM + explicitly pin material3 1.3.1 + icons 1.7.8 (aligns compile with the already-shipping runtime; every module recompiles against 1.10.x — CI-verified); (b) leave as-is + avoid changed APIs in non-koin modules (fragile); (c) downgrade koin-compose (loses features). Recommendation: (a), as its own session with device verification. NOT done unilaterally.
- **Method**: crash-report stack analysis + CI-APK artifact inspection (`META-INF/androidx.versions` + dex string-pool signatures) + foundation-layout sources-jar diff (1.7.8 vs 1.8.0 vs 1.9.0) + koin/ffmpeg/seeker/activity POM analysis — no local builds (§8).
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit 592e03b1). Compile-reviewed (Task 10 — caught the 6-value `match.destructured` compile error pre-push). CI pending at doc time. NOT merged.
- **Date:** 2026-08-25 (device-feedback session).

---

### D-256 — Browse hero v2: cover + banner together, proper tags
- **What (user feedback: hero "looks very bad… showing the cover and the banner together properly… showing the relevant tags properly"):** D-253's banner-only hero replaced with the classic poster-hero anatomy. Each pager page layers BOTH images: the banner as the ambient backdrop (crop, full-bleed, falls back to the cover) + the cover POSTER as the anchor element (80×120dp, 2:3, 12dp corners, 1dp outlineVariant border — matching the carousel card language) in a bottom-aligned Row with the text block.
- **Text block**: rank pill (#N TRENDING, solid primary, D-215 recipe, no emoji) → title 20sp ExtraBold, 2 lines max → meta row (`★ 85 · 24 eps · 2024`, integer score unified with the Library) → **genre tag chips** (D-215 Info-pill recipe, up to 3 + a "+N" overflow chip when there are more genres — "relevant tags properly").
- **Retained from D-253**: full-bleed 260→**300dp** pager, 6s auto-advance (drag-guarded, wraparound, timer restarts on page change), animated page dots (active = 16dp elongated pill) bottom-end, tap → Details. Scrim: subtle top (0.25) + stronger bottom (0.55→0.97) for text legibility over any artwork. Skeleton hero block height matched to 300dp.
- **Status:** ✅ Implemented (commit 592e03b1). Compile-reviewed (Task 10). Awaiting CI + user device verification. NOT merged.
- **Date:** 2026-08-25.

---

### D-257 — Browse hero v3 + section image preloading + rating-tag borders
- **What (user device feedback on D-256: hero "looks way too ugly… rigid kind of format", banner "forced into a square vibe", auto-scroll "not smooth, not animated", covers "not preloaded… I have to wait for them to load when I see them for the first time", rating tags need "some border… so it is a bit more clear"):**
  1. **Hero v3 anatomy**: the hero became an INSET 16:9 rounded card (16dp side margins, 20dp corners, 1dp outlineVariant@60% border — the standard card language) instead of the full-bleed ~1.2:1 block. 16:9 is AniList's native banner ratio → minimal cropping = the "wider kind of aspect ratio" the user asked for. Contents retained from D-256: banner backdrop (falls back to cover) + bottom-heavy scrim + 84×126dp poster (10dp corners, white@35% border) + rank pill + 18sp 2-line title + ★score·eps·year meta + genre chips (3 + "+N", translucent dark pills — readable on any artwork). Page dots moved BELOW the card, centered (never collides with the text block on narrow screens). Skeleton hero block restyled to match.
  2. **Infinite pager (the auto-scroll fix)**: `pageCount = size × 200`, `initialPage = size × 100`, display index = `page % size`, wrapped in `key(items.size)` (state recreated if the list size changes → index always starts at 0). Auto-advance always steps FORWARD +1 with a 600ms FastOutSlowIn tween — the old wraparound (last→first) swept BACKWARDS through every page, which was the "not smooth / not animated" glitch. Single-item lists render without pager mechanics. Dots follow the display index.
  3. **Section preloading (the rendering fix)**: new `SectionPreloader` composable in BrowseScreen — `LaunchedEffect(urls) { context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).size(wPx, hPx).build()) }` at density-exact card pixel dims (covers 128×192dp, CW thumbs 168×94dp, hero banners card-sized, posters 84×126dp), URLs null/blank-filtered + deduped, ordered hero-first. Coil 3.0.4 semantics (verified from the published sources): the Android memory-cache key EXCLUDES size when a request has no transformations, and AsyncImage auto-applies INEXACT precision → an exact-dims preload is a memory-cache HIT for the later composable; any size still warms the 500MB disk cache. Resolves the app's custom singleton loader via `coil3.imageLoader` (SingletonImageLoader.setSafe in AnikutaApp).
  4. **Rating-tag borders**: Browse amber pointed score tag gains `BorderStroke(1.dp, scoreContent@50%)` (dark-brown outline on amber300 in dark theme, deep-amber outline on cream in light — m3 1.3.1 Surface border follows PointedTagShape incl. the 45° tip). Library simple chips: same contentColor@50% border. Library compound sub|dub badge: manual stroked Path inside the existing drawBehind (Surface's border param can't trace hand-drawn paint) replicating PointedTagShape geometry for all 3 cases (START tip / END tip / flat rect), ~0.5dp visible (outer half clipped by the shape clip).
- **Method**: 3 parallel research agents (search-bug root cause, palette/picker/keypad mapping, Coil 3.0.4 + badge + version-skew verification from published sources-jars) → plan → plan-review agent (GO-WITH-FIXES; 10 fixes incorporated — search staleness guard, skeleton restyle, two-pointerInput ThinSlider, ScrollBlurOverlay modifier-vs-param, subtitle-swatch side effect, compound-border 3 cases, onQueryChange routing, single-item pager guard, preloader null-filtering, docs scope) → 3 phase commits → compile-review agent (PUSH-READY).
- **CI fix round**: compile review missed that `ImageRequest` lives at `coil3.request.ImageRequest` (not the coil3 root package) — CI run 32845772374 caught it; fixed in abb91ac0 matching the CoverColorExtractor/CoverAccentColor/AvatarCropScreen precedents.
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit 0e0d9c31 + CI fix abb91ac0). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25 (device-feedback session #2).

---

### D-258 — Search: default results restore + chip-based recents
- **What (user feedback: "when I close the search… it does not show me the default search results which appear when I enter the search page for the very first time… It even stays empty even if I go to another page and enter the search page again"; recents UI "improve it a little bit"):**
  1. **Root cause (research-verified)**: the VM is Activity-scoped (custom nav backstack, no per-entry ViewModelStoreOwner — survives tab switches), `onClearQuery` hard-set `Idle`, the debounced collector's blank branch hard-set `Idle` (with a stale D-242-fix7 comment), and `loadTrending()` only ever ran in `init` + manual AniList-chip re-select → after any clear, the screen degraded to recents-only forever (until app restart).
  2. **Fix — single-owner default loading**: `loadDefaults()` (idempotent: skips when query non-blank, a default load is in flight via `defaultsJob?.isActive`, or `showingDefaults` already true) dispatches to `loadTrending()`/`loadExtensionPopular()` (now Job-returning). EVERY "query became blank" path funnels through it: X clear, backspace-to-empty (`onQueryChange`), the debounced collector, `init`, and screen re-entry (`onScreenResume` restores if Idle+blank). `showingDefaults` tracks Success-with-default-content (set by the default loaders, cleared by every search path).
  3. **Staleness guards (plan-review catch)**: every async completion re-checks the query before writing state — a late trending response can no longer clobber fresh search results and vice versa (`if (_query.value.isNotBlank()) return@launch` in the default loaders; `isBlank()` in search/searchExtension, both in try AND catch).
  4. **Recents redesign**: collapsible list card → compact chip cloud (header "Recent searches" 13sp ExtraBold + "Clear all" 12sp primary; FlowRow-wrapped pill chips — surfaceVariant@40% (the search-bar field language), 14dp History icon + 13sp term (160dp max, ellipsized) + 14dp per-chip remove). Collapse/Show-more machinery + the persisted `search_recents_collapsed` pref deleted (chips never need collapsing; max 10 terms ≈ 2 rows). FlowRow is binary-safe in :feature:anime-search:impl (koin-compose → compiles 1.10.x; FilterSheet precedent).
- **Status:** ✅ Implemented (commit 7068b631). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25.

---

### D-259 — Palette editor + color picker overhaul (thin sliders, keypad, sticky header, 5-preset lines)
- **What (user feedback: remove the palette preview; "the slider is way too bad… use thin sliders… thumb grabbing area with a square with rounded corners"; picker "UI looks way too bad… no scrolling functionality… there should only be a total of five preset colors… in a single line… unique, distinct"; "if I tap on the number itself then the custom keyboard will open up like… the subtitles bottom-up menu"; palette sheet: heading + Reset always visible, NO X button, top gradient darkening):**
  1. **NumericEntrySheet ported** :core:player → `:core:designsystem/component` (self-contained — M3 + icons only; zero gradle changes; designsystem already has material-icons-extended for Backspace). SubtitleSettingsSheet imports it from the new home; its call passes a new subtitle-specific 5-swatch set (White/Black/Yellow/Cyan/Transparent — preserving the subtitle-relevant presets incl. Transparent, which the new default dropped).
  2. **NEW `ThinSlider`** (:core:designsystem): thin 4dp rounded track + 18dp rounded-square thumb (6dp corners) with a 1.5dp surface halo, always visible; 36dp touch target (proper grab area); tap-to-jump + drag in TWO SEPARATE pointerInput blocks (both detect* are non-returning suspend consumers); optional onValueChangeFinished + contentDescription. Built ONLY from ABI-stable foundation primitives (MinimalSeekbar precedent) — zero version-skew exposure; replaces every m3 Slider in the customized sheets.
  3. **ColorPickerSheet redesign**: sticky header (title + X) OUTSIDE the scroll area; body `verticalScroll` (heightIn-capped, weight(1f, fill=false)); current-color + hex row; presets = ONE equal-width line of rounded 12dp tiles (selection ring 2dp primary; near-transparent presets get a diagonal slash); RGBA rows = label + ThinSlider + TAPPABLE value chip → nested NumericEntrySheet (0..255, live-applied — the subtitles-sheet interaction). `DefaultColorPickerSwatches` = 5 distinct (White/Black/Red/Green/Blue).
  4. **CustomPaletteSheet redesign**: live mini-preview REMOVED (the app itself is the live preview — MainActivity re-themes on every keystroke); sticky header with title + Reset pill ALWAYS visible and NO close button (dismiss via swipe/scrim — EpisodeListSettingsSheet precedent); body in a Box with a scroll-driven `ScrollBlurOverlay` top scrim (the screens' transitioning-darkening language) under the header; brightness rows = ThinSlider + tappable value chip → NumericEntrySheet (−100..100, live); per-element preset lists cut to exactly 5 distinct colors each.
- **Status:** ✅ Implemented (commit 9b46ee69). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25.

---

### D-260 — Version 0.2.50 + release
- **What:** AndroidConfig 0.2.49 → **0.2.50** (versionCode 50) with the D-257..D-259 device-feedback batch; annotated tag `v0.2.50` → release-apk.yml publishes the stable arm64-only release (`ani-kuta-v0.2.50.apk`, --latest) so a v0.2.49 install updates in-app (checker verified sound in D-251 + D-255's java.time fix). Dashboard version strings refreshed (full-stack-dev sub-agent, DASHBOARD/webpage/ only).
- **Status:** ✅ Implemented. Release verified via API after CI green.
- **Date:** 2026-08-25.

---

### D-261 — Palette system overhaul + persistence fix + 2 new elements
- **What (user device feedback on v0.2.50: custom palette "transparent by default" — Reset recovers; custom theme lost after app restart; "there is definitely no need for the brightness sliders at all" — remove them; add two more customizable elements — "for the heading of the cards and blocks" + "for the description of the cards and blocks"; make sure it persists across restart like the custom one):**
  1. **Persistence root cause (verified, agent 15-b)**: `Color.value.toInt()` returns **0** for every sRGB color. Compose's `Color` is a `value class Color(val value: ULong)` with ARGB packed in the UPPER 32 bits; `.toInt()` truncates to the lower 32 bits = `0x00000000` (transparent). Every `setCustomTheme` write stored 0 into all 4 color keys → on restart `loadCustomTheme` read 0s → transparent theme surfaces → "default was applied" (windowBackground `#14111F` showed through). The `ColorPickerSheet` `initialColor = color.value.toInt()` also opened at 0 → "transparent by default".
  2. **Fix**: `.toArgb()` (`androidx.compose.ui.graphics.toArgb`) at all 6 sites (the 4 writes, the per-key read defaults, the legacy accent default, the `CustomPaletteSheet` `initialColor`) + a one-time corruption migration in `loadCustomTheme` that treats any stored alpha-0 value as unset and heals to the default for that element (v0.2.49/v0.2.50 installs recover instead of staying transparent).
  3. **Brightness removed entirely**: deleted the 4 brightness fields + `applyBrightness()` + `resolved()` from `CustomTheme.kt`; deleted the 4 `putFloat` + 4 `getFloat` + 4 keys from `ThemePreferences.kt`; deleted the brightness Row + ThinSlider + chip + NumericEntrySheet block + `brightness`/`onBrightness` params from `CustomElementEditor`. Brightness never served a unique purpose the color pickers didn't already cover.
  4. **Two new elements** (`cardHeading` + `cardDescription`): `CustomThemeColors` now has 6 fields; 2 new `CompositionLocals` (`LocalCardHeadingColor`, `LocalCardDescriptionColor`, `Color.Unspecified` sentinel — mirroring the `LocalHeadingColor` precedent) provided in the SAME always-on `CompositionLocalProvider` (D-255 structural stability preserved — never branch-switch); 2 new ARGB keys + 2 new `CustomElementEditor` rows + 2 new 5-distinct swatch lists in `CustomPaletteSheet`.
  5. **Consumer sweep (28 sites, phase 1 — the 5 surfaces the user named)**: each card title/description `Text` color arg now reads the local with a `.takeIf { it != Color.Unspecified } ?: <original role>` guard. Browse cards + CW cards + Library cards (incl. a pre-existing gap: the Library header clone never read `LocalHeadingColor` — now does) + Search result cards + Details anime title + block headers + synopsis body + episode rows + InfoRow label/value + MatchPreview. Hero title/meta deliberately kept hardcoded `Color.White` (reads on any artwork over the dark scrim; a custom dark pick would hurt readability). Section headers kept `primary` (the accent design language, distinct from card headings). Phase 2 (Updates/Extensions/History ~15 sites) deferred — clean follow-up batch.
- **Method**: 5 parallel research agents (hero/pager+blur, theme system + persistence bugs, picker+slider+recents, card-text consumers, random palette design) → plan → **plan-review agent** (GO-WITH-FIXES — 6 fixes: KDoc "~100 min" not "~200", ImageRequest line ref :442, ImageResult cast pattern, 2 Brush imports, Theme.kt preserve explicit MaterialTheme form, Bitmap.createScaledBitmap safety net) → 5 phase commits → CI green per phase.
- **Status:** ✅ Implemented on `test-feature/video-cache-new-download` (commit 8c201755). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25 (device-feedback session #3).

---

### D-262 — Browse hero: auto-advance restart-proof + 12s + blurred backdrop + darker scrim
- **What (user device feedback on v0.2.50: hero auto-scroll "scrolled and stopped in the middle between the two banners. It did not stop in the appropriate position" + auto-advance stopped firing afterwards; "the auto-scrolling functionality should be doubled. Instead of roughly 6 seconds, maybe 12 seconds"; the background banner "should be slightly blurred out and darkened but you did not handle it like that"):**
  1. **Auto-advance root cause (verified, agent 15-a)**: the `LaunchedEffect` was keyed on `pagerState.currentPage`, which flips at the 50% scroll crossing DURING `animateScrollToPage` → the effect coroutine was cancelled mid-flight → the pager rested at offset ≈ 0.5 with no snap (snap-fling only runs for user gestures). The effect was also single-shot → once cancelled it never re-armed → "auto-advance stopped firing afterwards."
  2. **Fix**: a `while(true)` loop keyed on `(pagerState, virtualCount)` — NEVER on a page index. `CancellationException` (user grabbed the pager, etc.) is caught → wait for the gesture to end (`snapshotFlow { isScrollInProgress }.first { !it }`) → snap to the nearest whole page via `scrollToPage(currentPage)` inside `withContext(NonCancellable)` so the card is always aligned. Real disposal (`currentCoroutineContext().isActive == false`) rethrows. Dots now read `settledPage` (update on settle, not mid-slide).
  3. **12s**: `HERO_AUTO_ADVANCE_MS` 6_000L → 12_000L.
  4. **Blurred backdrop (works on every API — minSdk 24)**: Coil 3 (3.0.4) REMOVED the `Transformation` API entirely (verified from the coil-core sources jar — there is no `coil3.transform.Transformation`); `Modifier.blur` uses `RenderEffect` = API 31+ only (silent no-op below); the user's device API is unknown → the blur must work on minSdk 24. New `BlurredBannerBackdrop` composable: `produceState<ImageBitmap?>` keyed on the URL → on `Dispatchers.IO`, build an `ImageRequest` at 160×90 px with a custom `memoryCacheKey("hero-blur:$url")` (namespaces the blurred request so it never collides with the sharp cover/poster requests for the same URL — per the D-257 lesson that the Android memory-cache key excludes size when there are no transformations), `execute` via the singleton `imageLoader`, `toBitmap(160, 90)` (with a `Bitmap.createScaledBitmap` safety net to guarantee the exact decode size), a single-pass `boxBlur` (radius 2) on the tiny bitmap (~1ms on IO), `asImageBitmap`, render via `Image(BitmapPainter, ContentScale.Crop)`. The GPU upscales the 160×90 blurred bitmap bilinearly to the full card (~1080×590) → the soft backdrop. Result cached in Coil memory under the custom key → instant on recompose.
  5. **Darker scrim**: gradient stops 0.18/0/0/0.45/0.82 → 0.30/0/0/0.55/0.88 (keeps the shape; helps the white text block read over the blurred artwork).
  6. **SectionPreloader**: the hero preload dropped the banner URLs (the blurred backdrop loads itself) and now warms only the sharp cover posters at 84×126.
- **CI fix rounds (2)**: caught `coil3.ImageResult` should be `coil3.request.ImageResult` + `ImageBitmap` type import (run 32867786472); then `ImageResult.Success` should be `SuccessResult` (top-level, not nested — run 32868462164). Plan-review's cast-pattern fix (Fix C) was the right idea but mis-named the subclass; the sources jar confirmed `coil3.request.SuccessResult` is a top-level `@Poko` class implementing the sealed `ImageResult`.
- **Status:** ✅ Implemented (commits 513f2e3e + cbf8765a + 9d72f45e). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25.

---

### D-263 — Random palette (Dark/Light/Chaos) + colorful channel sliders
- **What (user device feedback on v0.2.50: "make the custom sliders colorful. For example the red slider will be red and the green slider will be green and the blue slider will be blue"; add a random option left of the Reset button with the appropriate icon → opens a bottom-up menu with 3 options: random dark / random light / completely random "no boundaries, can be completely bad"; remember it across restart like the custom one):**
  1. **Colorful sliders**: `ThinSlider` gained a `trackBrush: Brush? = null` param — when non-null, renders a SINGLE full-width gradient bar and SKIPS the active/inactive two-box split (`fillMaxWidth(fraction)` would compress the gradient into the traversed portion and visibly shift it while dragging). The `ColorPickerSheet` `ChannelSliderRow` threads per-channel gradients: Red = `Color(0,g,b,a)` → `Color(255,g,b,a)` (shows what the color would be at each red value, holding g/b/a); Green/Blue symmetric; Alpha = transparent→opaque of the current color. Thumb colors: red `0xFFE53935`, green `0xFF43A047`, blue `0xFF1E88E5`, alpha = default primary. Each gradient is `remember`-keyed on the OTHER channels so it doesn't rebuild per drag frame.
  2. **Random palette** (new `core/designsystem/theme/RandomPalette.kt`): three generators per agent 15-e's HSV ranges. **Random dark**: single family hue drives bg/card/heading/cardHeading/cardDescription (each within ±25°); ranges that ALWAYS produce a readable dark theme (bg V 0.06-0.16, heading V 0.90-1.00, cardDescription V 0.65-0.78); accent is an independent vivid hue (S 0.65-1.0, V 0.55-0.75). **Random light**: mirrored. **Chaos**: every element fully random per-channel with alpha FORCED 0xFF (never trigger the D-261 transparent-theme bug class). Contrast verified (worst-corner WCAG): dark heading-vs-bg ≥ 8.4:1, dark cardDescription-vs-card ≥ 4.5:1; light heading-vs-bg ≥ 8.7:1.
  3. **CustomPaletteSheet**: Random pill (`Icons.Filled.Casino` + "Random" label, `surfaceVariant` `RoundedCornerShape(50)`) added LEFT of the Reset pill, both in a `spacedBy(8.dp)` Row with the title `weight(1f)`. Tapping opens a NESTED `RandomPaletteSheet` (stacked `ModalBottomSheet` — same idiom as `ColorPickerSheet` nesting) with 3 option rows (`Icons.Filled.DarkMode`/`LightMode`/`Shuffle` + label + description). On pick: `prefs.setCustomTheme(randomCustomTheme(kind))` applies + persists (survives restart via D-261's persistence fix); the nested sheet dismisses, parent stays open showing the updated swatches, app re-themes live.
- **Status:** ✅ Implemented (commit da93107c). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25.

---

### D-264 — Search recents dedicated horizontal-scroll section
- **What (user device feedback on v0.2.50: "create a dedicated section for it with a proper background... the searches will show in a single row and I can scroll them right and left... give it some depth, some good-looking UI"):**
  Replaced the D-258 `FlowRow` chip-cloud with a dedicated section card: outer `Surface(surfaceVariant@40%, RoundedCornerShape(16dp), 1dp outlineVariant@60% border)` (the §2.6 card language + depth via border, not shadows); sticky header row (bare `Icons.Filled.History` primary 18dp + "Recent searches" 14sp ExtraBold primary + trailing "Clear all" 12sp SemiBold primary); single `LazyRow` of bordered chips (`surfaceContainerHighest` pops on the tinted container; 1dp `outlineVariant@60%` border for depth; 36dp tall; History icon + 13sp term ellipsized 160dp + per-chip remove X). Signature UNCHANGED → all 3 render sites (SearchScreen idle branch, ResultsGrid header, ExtensionResultsGrid header) get the redesign free. Removed `FlowRow`/`ExperimentalLayoutApi` (the D-255 crasher class — no longer needed; `LazyRow` is the single-row scroll). `:feature:anime-search:impl` has koin-compose → `LazyRow`+`items` binary-safe.
- **Status:** ✅ Implemented (commit c996391b). NOT merged — awaiting user device verification.
- **Date:** 2026-08-25.

---

### D-265 — Version 0.2.51 + docs
- **What:** AndroidConfig 0.2.50 → **0.2.51** (versionCode 51) with the D-261..D-264 device-feedback batch; annotated tag `v0.2.51` → release-apk.yml publishes the stable arm64-only release (`ani-kuta-v0.2.51.apk`, --latest) so a v0.2.50 install updates in-app. Dashboard version strings refreshed (full-stack-dev sub-agent, DASHBOARD/webpage/ only).
- **Status:** ✅ Implemented. Release verified via API after CI green.
- **Date:** 2026-08-25.

---

### D-266 — Browse: remove Continue Watching + fix hero banner hardware-bitmap crash
- **What (user device feedback on v0.2.51):** (1) "I don't want you to show the Continue Watching so remove it" from the Browse page. (2) "the background thumbnail image shows properly but the background banner image does not show at all. There is some darkening effect but the banner image does not show at all behind it."
- **Root cause (hero banner, agent 22-a):** D-262's `BlurredBannerBackdrop.boxBlur()` called `src.getPixels(...)` on a Coil-3 `Bitmap.Config.HARDWARE` bitmap (default on API 26+) → threw `IllegalStateException` → silently caught by the outer `try/catch (_: Exception)` → the backdrop `Box` rendered empty → user saw only the Layer-2 dark scrim (no banner image). The 84×126 cover poster showed because Coil renders hardware bitmaps fine via its own painter (no `getPixels`).
- **Hero fix (BrowseHero.kt):** (1) `.allowHardware(false)` on the `ImageRequest` (import `coil3.request.allowHardware`) so Coil decodes to `ARGB_8888`. (2) Defensive copy in `boxBlur` — `if (src.config == Bitmap.Config.HARDWARE) src.copy(ARGB_8888, true) ?: return src` before `getPixels` (safety net). (3) Scrim lightened 0.30/0/0/0.55/0.88 → 0.22/0/0/0.45/0.82 so the blurred banner artwork reads through behind the text.
- **Continue Watching removal (4 files):** `BrowseScreen.kt` (param + collectAsState + SectionPreloader + cw_header/cw_carousel items), `BrowseCards.kt` (ContinueWatchingCarousel + ContinueWatchingCard + 6 now-unused imports: size, CircleShape, PlayArrow, Icon, LinearProgressIndicator, Icons), `BrowseViewModel.kt` (continueWatching flow + ContinueWatchingItem data class + 2 now-unused constructor params watchProgressStore/contentRepository + 2 imports), `MainActivity.kt` (ContinueWatchingItem import + onPlayContinueWatching lambda arg + buildWatchKeyForContinueWatching function). `BrowseModule.kt` UNCHANGED — `viewModelOf(::BrowseViewModel)` reflects on the new 2-param constructor. `WatchProgressStore` + `watch.sq` remain (used by Library's per-collection continue-watching toggle — a separate feature).
- **Status:** ✅ Implemented (commit a01734ef). CI GREEN. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-267 — Remember last-selected tab across cold start + recents
- **What (user device feedback on v0.2.51):** "It should remember which tab the user was on previously and it should open up in that tab directly. For example if the user was in the library tab then it should always open up the application in the library tab when the user opens up the application after closing it completely, even from the recents."
- **Root cause (agent 22-b):** `MainActivity.kt:362` — `var currentTab by remember { mutableStateOf("browse") }` was NOT persisted (hardcoded default). `backstack` init was also hardcoded to `AnimeBrowseKey`. App always cold-started on Browse.
- **Fix:** `AppPreferences.kt` — added `var lastTab: String` (getString/putString, default "browse") + `KEY_LAST_TAB` constant in the existing companion object (mirrors the `contentMode` pattern; SharedPreferences survives process death). `MainActivity.kt` AppRoot: added `val appPreferences = koinInject<AppPreferences>()` hoisted above the `remember {}` calls; `currentTab` initial value "browse" → `appPreferences.lastTab`; `backstack` initial key `AnimeBrowseKey` → inline `when (appPreferences.lastTab) { "library" -> AnimeLibraryKeyImpl; "search" -> AnimeSearchKey; "more" -> MoreKey; else -> AnimeBrowseKey }`. `onSelect` lambda: added `appPreferences.lastTab = route` right after `currentTab = route` (single write site confirmed via grep). DI: `AppPreferences` already bound (`single { AppPreferences(get()) }` at AnikutaApp.kt:269); no DI module changes.
- **Coverage:** cold start (process killed + reopened) ✓; return from recents (process was killed) ✓; activity recreation ✓ (the pref is the source of truth; no `rememberSaveable` needed — nav-research doc explicitly rejected saveable backstack per R7).
- **Status:** ✅ Implemented (commit e313a24c). CI GREEN. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-268 — Library: BEHIND + SEASON_YEAR sorts + fix LAST_WATCHED stub
- **What (user device feedback on v0.2.51):** "Currently I only have four sorting options but what I want is for you to add some more sorting options. One option which you could add is the behind option. When that option is turned on it will show those contents at the top or at the bottom, with the ones that are behind at the bottom. The ones that are all caught up will be at the top and the ones that are behind will be at the bottom."
- **Root cause + data availability (agent 22-c):** The 4 existing sorts were TITLE, SCORE, DATE_ADDED, LAST_WATCHED — but `LAST_WATCHED` was a NO-OP STUB (returned `filtered` unchanged). `LibraryEntry` already had `releasedEpisodes` + `watchedCount` + derived `unwatchedCount = (released - watched).coerceAtLeast(0)`, all populated by `enrichEntriesWithBadgeData` at load time. So **no new SQL was needed for BEHIND** — `unwatchedCount` was already there. `seasonYear` already existed for SEASON_YEAR. The missing piece for LAST_WATCHED was a `lastWatchedAt` field + a `WatchProgressStore.getLastWatchedAt` method.
- **Fix (5 files):** `LibraryEntry.kt` — added `val lastWatchedAt: Long? = null` field. `WatchProgressStore.kt` — added `suspend fun getLastWatchedAt(mainId: String): Long?` interface method (mirrors `getWatchedEpisodeCount`'s `suspend (String)` signature). `watch.sq` — added `getLastWatchedAt: SELECT COALESCE(MAX(last_watched_at), 0) FROM watch_progress WHERE main_id = :mainId;` (COALESCE guarantees non-null Long; `last_watched_at` column exists at watch.sq:16). `SqlDelightWatchProgressStore.kt` — implemented `getLastWatchedAt` = `executeAsOne().takeIf { it > 0 }` (0 → null = no episodes watched). `LibraryViewModel.kt` — enum `LibrarySortType` gained `BEHIND("Behind")` + `SEASON_YEAR("Year")` (kept `displayName` per plan-review — SortOptionCard reads `type.displayName`); the `applyFilters` `when` block gained BEHIND (`compareBy unwatchedCount thenBy title` — ascending = caught-up first = matches user request), SEASON_YEAR (`compareBy seasonYear`), + the LAST_WATCHED stub replaced with `sortedBy/sortedByDescending { it.lastWatchedAt ?: 0L }`; `enrichEntriesWithBadgeData` populates `lastWatchedAt` alongside `watchedCount`. UI: no changes — `LibraryScreen.kt:1658` iterates `LibrarySortType.entries.forEach` so the 2 new enum values auto-render.
- **Semantics:** BEHIND ascending = caught-up (unwatchedCount 0) at top, behind at bottom (matches user request exactly; default `_sortAscending=true`). LAST_WATCHED ascending = oldest-watched first; descending = most-recent. SEASON_YEAR ascending = oldest year first; descending = newest (user can flip via the existing direction pills).
- **Status:** ✅ Implemented (commit 6f9e977d). CI GREEN. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-269 — Library scroll performance (derivedStateOf + contentType + @Immutable)
- **What (user device feedback on v0.2.51):** "on the library page when I try to scroll, the scrolling is not smooth. It is jittery... If I scroll very fast then it apparently lags and jitters quite a lot."
- **Root cause (agent 22-d):** `LibraryScreen.kt:279-283` — `val collapsed = if (!isList) { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20 } else { listState... }` was read DIRECTLY in the parent composition body, NOT wrapped in `derivedStateOf`. Every scroll frame mutated these `LazyGridState`/`LazyListState` properties → the entire `LibraryScreen` parent recomposed → re-allocated `onEntryClick`/`onEntryLongClick` lambdas → `LibraryGrid`/`LibraryList` could not skip → all per-card anti-patterns re-ran. **Compounds on fling.**
- **Fix (2 files, 4 low-risk high-impact changes):** `LibraryScreen.kt` — (1, HIGH) wrapped `collapsed` in `remember(isList) { derivedStateOf { ... } }` — the parent now only recomposes when `collapsed` FLIPS (true↔false), not on every scroll frame. THE primary scroll-perf fix. Added `import androidx.compose.runtime.derivedStateOf`. (2, HIGH) added `contentType` to all 3 `items()` calls: staggeredItems (COMFORTABLE grid) + items (COMPACT grid) = `{ "card" }`; items (LIST) = `{ "row" }` — lets Compose recycle slots efficiently during fling (recommended even for uniform lists). `LibraryEntry.kt` — (3, MEDIUM) annotated `@Immutable` (all fields are val + `AudioAvailability` is a data class with all val primitives, verified) — lets Compose skip recomposition of items whose `LibraryEntry` reference is unchanged.
- **Deferred to a follow-up if the user still sees jitter:** stabilize `onEntryClick`/`onEntryLongClick` with `remember(...)` (largely mitigated by the derivedStateOf fix — parent no longer recomposes every frame, so lambdas stay stable); gate `rememberCoverAccentColor` when `coverBorderColor != ADAPTIVE` (doubles per-card image load currently); add explicit `size()` to `AsyncImage` calls; hoist `rememberBadgeColorScheme` + wrap per-card badge `when` blocks in `remember`.
- **⚠️ Note:** D-269 + D-270 were bundled into a single commit (`e3bd6285`) due to a `git add -A` staging issue — the first commit captured all 3 modified files (LibraryScreen.kt + LibraryEntry.kt for D-269, DetailsViewModel.kt for D-270). The code is correct; the commit message is D-269-only. Documented here for traceability.
- **Status:** ✅ Implemented (commit e3bd6285, bundled with D-270). CI pending. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-270 — Detail tracking auto-refresh on open + after auto-link + stale reset
- **What (user device feedback on v0.2.51):** "If I go to the search page and then search by any of the extensions and I open it up, after opening it up it gets automatically linked to any list. It does not properly reload the tracking from any list... currently I have to manually go on and refresh the page to actually see that data get updated. Where I went after that the data does not stay updated if I go back and open the exact same one again."
- **Root cause (agent 22-e, H1 + stale-state):** H1 — `mergeAniListIntoUnified` (`DetailsViewModel.kt:2390`) established the AniList link (set `currentAnimeId`, persisted `linkAniList`, `remergeBases` → state Success with anilistId) but never called `refreshTracking()` afterward. H4 (context) — the concurrent `refreshTracking()` launched in `loadFromExtension` (`:1771-1774`) raced the main load — `currentMainId`/`anilistId` were null at launch time → `refreshTracking()` early-returned (`:1528/1531/1532`) → never re-invoked after the link. Stale re-open — `resetState()` (`:1006-1031`) did NOT reset `_trackEntry`/`_pendingRemoteTrackEntry`/the track `show*` prompts — re-opening the same anime showed the previous anime's stale tracking.
- **Fix (DetailsViewModel.kt, 2 targeted changes):** (1) `mergeAniListIntoUnified` — added `val gen = loadGeneration` at the top (before `try`) + `if (loadGeneration == gen) refreshTracking()` after the link step (`refreshContentId`). Now: after the AniList link is established (currentMainId + anilistId set, state Success), `refreshTracking()` runs correctly (no early-return) → tracking status fetched + cached + UI updated. Fixes the extension auto-link path (the user's primary complaint). (2) `resetState` — now clears `_trackEntry` + `_pendingRemoteTrackEntry` + `_showTrackSheet` + `_showMarkPreviousPrompt` (Int? → null) + `_showMarkSeriesPrompt`. Re-opening the same anime starts with clean tracking state (`loadFrom*` repopulates on success). Fixes the "stale data on re-open" complaint.
- **Deferred to a follow-up if needed:** H4 full fix — chain the concurrent `refreshTracking` launches in `loadFromAniList`/`loadFromExtension` to wait for `state.first { it is DetailsState.Success }` before calling (the H1 fix handles the extension auto-link path directly; the AniList-direct path may still benefit). Local cache fallback — `_trackEntry.value = trackEntryRepository?.get(currentMainId)` on open for instant display of last-known tracking while the network refresh runs.
- **⚠️ Note:** D-270's `DetailsViewModel.kt` changes were bundled into commit `e3bd6285` (D-269's commit) due to a `git add -A` staging issue. See D-269's note above.
- **Status:** ✅ Implemented (commit e3bd6285, bundled with D-269). CI pending. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-271 — Version 0.2.52 + docs
- **What:** AndroidConfig 0.2.51 → **0.2.52** (versionCode 52) with the D-266..D-270 device-feedback batch; annotated tag `v0.2.52` → release-apk.yml publishes the stable arm64-only release (`ani-kuta-v0.2.52.apk`, --latest) so a v0.2.51 install updates in-app. Dashboard version strings refreshed (full-stack-dev sub-agent, DASHBOARD/webpage/ only).
- **Status:** ✅ Implemented. Release verification pending CI green.
- **Date:** 2026-08-26.

---

### D-272 — :core:ads module (smart-link ad system architecture)
- **What (user device feedback on v0.2.52 + new feature request):** "implement ad functionality. I want to be able to add multiple kinds of ads in my application for multiple things too, to get monetized... I am thinking about utilizing various techniques for getting monetized... like maybe utilizing smart links... create a full-fledged robust system for it... all of it should be customizable over an update. If the user downloads the latest, these settings of the ads will be updated alongside it. The user will not be given any option at all, most probably, to configure the ads... I want to keep it separate from the other parts of the application, making sure that it does not affect their functionality or such. I want it to be highly customizable and properly built... robust and not intrusive."
- **Architecture (new `:core:ads` module, package `com.confused.anikuta.core.ads`):** An ISOLATED, EXTENSIBLE ad system. The module depends on `:core:common` (Logger), `:core:preferences` (PreferenceStore), `:core:designsystem` (theme for the interstitial UI) — deliberately does NOT depend on `:core:navigation-api` or any `:feature:*`. The coordinator gates a `() -> Unit` proceed-callback; the caller (AppRoot) decides what "proceed" means (e.g. `backstack.add(key)`). This keeps `:core:ads` fully decoupled (CORE_RULES §5/§7 + user's "keep it separate"). Files:
  - `AdsConfig.kt` — `data class AdsConfig(enabled, activeKind, smartLink)` + `sealed interface AdKind { data object SmartLink; /* future: BannerAd, InterstitialVideo, NativeAd */ }` + `data class SmartLinkConfig(url, cooldownMs, minTimeOutsideMs, maxRetries)` + `object DefaultAdsConfig { val current = AdsConfig(...) }`. **The config ships in APK bytecode** — no user-facing setting, no remote config. To change the URL later: edit `SmartLinkConfig.url` here + ship a new release (CORE_RULES §5 exception: interface-with-one-impl OK when future swap is explicitly planned — the user said more ad kinds are coming).
  - `AdPreferences.kt` — isolated `AdPreferences(preferenceStore: PreferenceStore)` with `var lastAdShownTimestamp: Long` (mirrors `AppUpdatePreferences` pattern; separate from `AppPreferences` per user's "keep it separate"). The cooldown survives cold starts (the user said "for the next six hours he will not see any ad at all").
  - `AdsRepository.kt` — `interface AdsRepository` + `AdsRepositoryImpl(preferences)` — config holder + cooldown gate (`isInCooldown()`, `recordAdShown()`, `timeSinceLastAdMs()`, `remainingCooldownMs()`). Interface-bound for future remote-config swap.
  - `AppLifecycleObserver.kt` — `DefaultLifecycleObserver` registered on `ProcessLifecycleOwner.get().lifecycle` (new `androidx.lifecycle:lifecycle-process` dependency — not previously used anywhere). Records ON_STOP timestamp + emits `onReturnToForeground: SharedFlow<Unit>` on ON_START (only if a prior ON_STOP). `elapsedOutsideMs()` measures the time spent outside. The §8 research sub-agent confirmed `:core:activity-tracker` is a batched SQLDelight event logger — NOT reusable for foreground/background tracking; this observer is purpose-built.
  - `AdsModule.kt` (Koin) — registers `AdPreferences`, `AdsRepository`, `AppLifecycleObserver`, `AdsCoordinator`. Added `adsModule,` to `AnikutaApp.kt`'s `modules(...)` list.
  - `build.gradle.kts` — `id("anikuta.library.compose")` (Compose for the interstitial) + deps on `:core:common`, `:core:preferences`, `:core:designsystem`, `androidx.lifecycle.process` (new), `androidx.lifecycle.runtime.compose`, koin, coroutines.
  - `AndroidManifest.xml` — empty (no components).
- **Cooldown:** `SmartLinkConfig.cooldownMs = 6 * 60 * 60 * 1000L` (6 hours, per user "one ad per every for six hours").
- **Placeholder URL:** `SmartLinkConfig.url = "https://example.com/anikuta-sponsor"` — the user said "for the current temporary testing purposes you can use any random URL but later on I will tell you the URL." Change this single line + ship a new release to update.
- **Extensibility:** The `AdKind` sealed interface is the extension point. Adding a new ad kind = add a `data object` + a `when` branch in the interstitial + (if needed) extend the coordinator. No DI changes. The user said "in the future I'm thinking about adding some other kinds of ads too."
- **Status:** ✅ Implemented (commit 15653cf1). Compile-review sub-agent: 0 errors. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-273 — AdsCoordinator state machine + SmartLinkAdInterstitial UI
- **What:** The brain + the UI of the ad system.
- **AdsCoordinator.kt** — `class AdsCoordinator(repository, lifecycleObserver)` with `val state: StateFlow<AdGateState>` + `requestNavigation(proceed: () -> Unit): Boolean` + `onUserContinue(context)` + `onAppReturnedToForeground()` + `onTryAgain(context)` + `cancel()`. State machine: `sealed interface AdGateState { data object Idle; data object AdPending; data class AdInProgress(startedAt, retryCount); data class AdTryAgain(lastElapsedMs, retryCount) }`. Flow: Idle → (requestNavigation, not in cooldown) → AdPending → (onUserContinue opens browser) → AdInProgress → (onAppReturnedToForeground: elapsed ≥ minTime → complete+proceed+Idle; elapsed < min + retries < max → AdTryAgain; retries ≥ max → safety-cap complete). Back (Dialog dismiss) → `cancel()` drops the held proceed-callback (navigation aborted, no cooldown set — non-intrusive escape hatch per user). `completeAd()` records the ad + invokes proceed + sets Idle. Single Koin instance — concurrent `requestNavigation` while non-Idle is rejected (returns false). No-browser (ActivityNotFoundException) → fallback complete (don't trap the user).
- **SmartLinkAdInterstitial.kt** — full-screen Compose `Dialog(properties = DialogProperties(usePlatformDefaultWidth=false, dismissOnBackPress=true, dismissOnClickOutside=false))`. 3 content states via `Crossfade`: AdPending (OpenInNew icon + "Sponsored" + "Continue" button + "Not now" TextButton), AdInProgress (CircularProgressIndicator + "Waiting for you to come back"), AdTryAgain (Refresh icon + "You came back after Xs" + "Try again" button + Cancel). `DisposableEffect` registers `AppLifecycleObserver` on ProcessLifecycleOwner while composed; `LaunchedEffect(state)` collects `onReturnToForeground` while AdInProgress → calls `coordinator.onAppReturnedToForeground()`. Rendered from `:app`'s AppRoot as a sibling of `UpdateBottomSheet`.
- **Try-again flow (per user):** "If the user just clicks the button and he is redirected and then directly comes back, then what it will say is 'Try again'. After trying again it will open up and then the user can come back again." → `onAppReturnedToForeground` checks `lifecycleObserver.elapsedOutsideMs()` against `SmartLinkConfig.minTimeOutsideMs` (default 15s). `< minTime` → AdTryAgain state. User taps "Try again" → `onTryAgain` re-opens the URL → AdInProgress → loop until success or max-retries safety cap.
- **Status:** ✅ Implemented (commit 15653cf1). Compile-review: 0 errors. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-274 — Navigation interception in MainActivity.kt AppRoot
- **What (user):** "for the ads I am thinking about showing them when the user clicks on any of the entries from any page at all. If he clicks on any entry from the home page, from the library page, from the search page, or from the more sections page, from anywhere, he tries to go to the details page. He will be shown the proper ad."
- **Fix (MainActivity.kt AppRoot):** Added `val adsCoordinator = koinInject<AdsCoordinator>()` + `val navigateToDetails: (AnimeDetailsKey) -> Unit = { key -> adsCoordinator.requestNavigation { backstack.add(key) } }` (declared AFTER `backstack` since it closes over `backstack.add`). Converted ALL 10 user-tap navigate-to-Details call sites to route through `navigateToDetails`:
  - Browse (generic `onNavigate`): `when (navKey) { is AnimeDetailsKey -> navigateToDetails(navKey); else -> backstack.add(navKey) }` — pattern-matches because BrowseScreen constructs the key internally (the only feature module that does).
  - Library (`onNavigateToDetails`): both AniList + Extension variants → `navigateToDetails(...)`.
  - Search: both `onNavigateToDetails` (AniList) + `onNavigateToExtensionAnime` (Extension) → `navigateToDetails(...)`.
  - Downloads/DownloadedFiles, Updates, History, Profile: all `onNavigateToDetails` / `onNavigateToAnime` → `navigateToDetails(...)`.
- **Notification deep-link EXCLUDED:** the `LaunchedEffect(notifMainId)` block (app-open-from-notification) deliberately keeps `backstack.add(AnimeDetailsKey.*)` directly — a notification tap is system-initiated, NOT a user tap on an entry (per user "when the user clicks on any of the entries from any page"). Also no previous-screen context for the interstitial to float over on a cold start.
- **Overlay:** `SmartLinkAdInterstitial()` rendered in AppRoot after the `UpdateBottomSheet` block (sibling). Idle = renders nothing.
- **Status:** ✅ Implemented (commit 15653cf1). Compile-review: 0 errors. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-275 — Browse Hero: sharp banner + blurred-cover bottom strip
- **What (user device feedback on v0.2.52):** "The background banner is apparently blurred out way too much so it should not be blurred out at all so that needs adjustment. I do like the banner image to be shown at the top, which is perfect, but at the bottom it gets an empty area. About that empty area what I would like you to do is make it a blurred-out background of the cover image itself. At the top the banner will show and in the bottom empty area the blurred-out view of the cover image will show. Or maybe we can do this: the blurred-out view of the banner will show but make sure that the blur is exactly how it is implemented on the details page, making sure that it is smooth, perfect, beautiful-looking, and proper."
- **Root cause (agent 3-a):** The D-262 `BlurredBannerBackdrop` CPU-box-blurred the banner across the WHOLE card (radius 2 on a 160×90 thumbnail) + the heavy bottom scrim (0.22/0/0/0.45/0.82 alpha black) occluded the blurred banner at the bottom → the user saw a near-solid dark strip (the "empty area"). The banner being "blurred out way too much" = the boxBlur applied to the whole backdrop.
- **Fix (BrowseHero.kt, full rewrite via Write):** New 4-layer HeroCard:
  1. **Layer 1 — SHARP banner** (D-275): plain `AsyncImage(model = anime.bannerImage ?: anime.coverUrl, contentScale = Crop, fillMaxSize)` — NO blur. Fallback `surfaceVariant` when both URLs null.
  2. **Layer 1.5 — BLURRED COVER bottom strip** (D-275, NEW): `AsyncImage(model = anime.coverUrl, modifier = align(BottomCenter).fillMaxWidth().height(140.dp).blur(8.dp).scale(1.15f), contentScale = Crop)` — matches the details-page blur EXACTLY (`Modifier.blur(8.dp).scale(1.15f)`, same recipe as `DetailsScreen.kt:1453`). Fills the "bottom empty area" with the cover artwork, blurred. API 31+ uses RenderEffect (same as DetailsScreen); below 31 it's a no-op → sharp cover (still better than the old empty dark strip).
  3. **Layer 2 — lightened scrim** (D-275): `0.22/0/0/0.45/0.82` → `0.15/0/0/0.30/0.55` so the sharp banner reads at top + the blurred cover reads at bottom + text stays legible.
  4. **Layer 3 — foreground** (unchanged): cover poster (84×126) + rank pill + title + meta + chips, anchored BottomStart.
- **Removals:** `BlurredBannerBackdrop` composable (D-262) + `boxBlur` function (D-262) + `HERO_BLUR_W_PX`/`HERO_BLUR_H_PX`/`HERO_BLUR_RADIUS_PX`/`HERO_BLUR_KEY_PREFIX` constants + 13 now-unused imports (`android.graphics.Bitmap`, `foundation.Image`, `produceState`, `ImageBitmap`, `asImageBitmap`, `BitmapPainter`, `LocalContext`, `coil3.imageLoader`/`ImageRequest`/`SuccessResult`/`allowHardware`/`toBitmap`, `Dispatchers`). KDocs updated (top + HeroCard).
- **Status:** ✅ Implemented (commit 15653cf1). Compile-review: 0 errors. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-276 — Version 0.2.53 + docs
- **What:** AndroidConfig 0.2.52 → **0.2.53** (versionCode 53) with the D-272..D-275 ad-system + Browse-Hero batch; annotated tag `v0.2.53` → release-apk.yml publishes the stable arm64-only release (`ani-kuta-v0.2.53.apk`, --latest) so a v0.2.52 install updates in-app. New `:core:ads` module → module count 47 (was 46). Dashboard version strings + module count refreshed (full-stack-dev sub-agent, DASHBOARD/webpage/ only). Added `APP/ani-kuta/DOCUMENTATION/ads/` architecture doc.
- **Status:** ✅ Implemented. Release verification pending CI green.
- **Date:** 2026-08-26.

---

### D-277 — Browse Hero v4: full uncropped banner + palette-gradient content zone
- **What (user device feedback on v0.2.53):** "on the browse page the top hero section apparently does not look good. It looks ugly, it looks bad, and it is not proper... It will show the banner and the banner will be shown fully at the top. It won't be cropped or anything like that. The full banner will be shown properly. Below it the section will be in the gradient colors of the cover image rather than being the bold version of the cover image. It will smoothly blend into the top banner properly."
- **Root cause (research sub-agents R-1 + R-2):** D-275's HeroCard forced `aspectRatio(16f/9f)` + `ContentScale.Crop` — AniList banners are natively ~3:1 (up to ~3.57:1), so Crop discarded ~40-50% of the banner's vertical content (only the center horizontal band showed; recorded as far back as D-257's "forced into a square vibe"). The "blurred cover bottom strip" (Layer 1.5) had a hard rectangular top edge at exactly 140dp (NO blend with the sharp banner above) AND sat directly behind the foreground cover poster (84×126 + 12dp padding ≈ 138dp of the 140dp strip) — visually redundant, its only visible contribution a 2dp band at its top edge. Zero palette-derived colors anywhere in the hero.
- **Fix (BrowseHero.kt — HeroCard rewritten; pager mechanics untouched):**
  1. **Full banner, uncropped:** `AsyncImage(model = bannerImage, contentScale = ContentScale.Fit, alignment = Alignment.TopCenter, modifier = fillMaxSize)` — the WHOLE banner shows, no crop. AniList banners (~3:1) occupy the top ~40% of the card. Card ratio widened 16:9 → `HERO_CARD_RATIO = 1.2f` (all pager pages share the ratio → pager height never jumps between banners of differing aspect). No-banner items render the gradient alone as the header (the old `bannerImage ?: coverUrl` fallback was dropped — a 2:3 portrait cover Fit in a 1.2:1 card leaves huge side gaps; the foreground poster already shows the cover).
  2. **Palette-derived gradient (NOT a blurred cover):** `coverColor = rememberCoverDominantColor(anime.coverUrl) ?: surfaceVariant`; `darkCoverColor = lerp(coverColor, Color.Black, 0.55f)` (guarantees white-text contrast — any extractor-normalized mid-tone lands at ~0.18–0.29 final lightness). Base `background(coverColor)` + overlay `Brush.verticalGradient([Transparent, Transparent, coverColor, darkCoverColor])`. This is "colors of the cover image" exactly as the user asked — not a blurred copy of the cover image.
  3. **Smooth blend:** the gradient's transparent zone covers the banner's bottom ~45%, ramping to solid coverColor at the ~55% junction — the banner's bottom edge feathers into the solid color with NO hard seam (the details-page `DetailBanner` recipe `[Black@0.2, Transparent, background]` adapted to `[Transparent, Transparent, coverColor, darkCoverColor]`).
  4. Rank pill switched `primary` → `Color.Black.copy(0.45f)` (matches the genre-chip language; sits on the dark zone). Poster placeholder bg `surfaceVariant.copy(0.4f)` → `Color.Black.copy(0.25f)` + placeholder text → White (on-gradient cohesion).
- **New file — `core/designsystem/.../color/CoverAccentColor.kt`:** `@Composable fun rememberCoverDominantColor(coverUrl: String?): Color?` — wraps the EXISTING `CoverColorExtractor.extract()` (Coil 100×100 + Palette + HSL normalize sat≥0.40/lightness∈[0.40,0.65]) in a `produceState` keyed on coverUrl. Constructs the extractor inline via `remember { CoverColorExtractor(context, context.imageLoader) }` — deliberately NOT `koinInject` because `:core:designsystem` has NO Koin dependency (framework-light core module; mirrors the library feature's proven `rememberCoverAccentColor` pattern). `ponytail:` two extraction helpers now exist (this + the library's theme-adaptive one) — consolidate when a third consumer appears.
- **Files:** BrowseHero.kt (rewrite of HeroCard + KDoc + imports — dropped `blur`/`scale` imports, added `lerp` + `rememberCoverDominantColor`), CoverAccentColor.kt (new).
- **Status:** ✅ Implemented (commit 3517d414). Compile-review sub-agent: COMPILE-CLEAN (coil3 3.0.4 `AsyncImage` `alignment`/`contentScale` named-params verified via bytecode dump of the actual artifact; `lerp(Color,Color,Float)` verified against 4 existing compile-tested sites). NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-278 — Shared BrowseCacheCodec + Search offline trending default
- **What (user):** "the search page should open up properly too without the internet, like the default search page results will show and such." Research sub-agent R-3 found `SearchViewModel.loadTrending` had NO disk cache — offline it fell to `SearchUiState.Idle` (recents card only), and even the recents chips would fail on tap (fresh network search).
- **Fix — `object BrowseCacheCodec` (NEW in `:core:anilist/api`):** the browse_cache JSON encode/decode (~30 lines) extracted from BrowseViewModel's private serialize/parse fns, plus the shared section keys (`SECTION_TRENDING`/`SECTION_POPULAR`/`SECTION_TOP_RATED`). Lives in `:core:anilist` (next to `AniListAnime`) so BOTH the Browse and Search features reuse it — Search serves the cached trending payload, which is the EXACT same AniList `TRENDING_DESC` query Browse caches (CORE_RULES §5 — reuse, no parser duplication). `decode` throws on malformed JSON; callers catch + log + treat as "no cache" (graceful degradation, same as the old inline parser).
- **BrowseViewModel:** private `serializeBrowseCache`/`parseBrowseCache` REPLACED with `BrowseCacheCodec.encode/decode` calls (pure refactor — same JSON format, so existing cache rows keep parsing). Companion `SECTION_*` consts now reference the codec's (single source of truth).
- **SearchViewModel.loadTrending — now CACHE-FIRST:** (1) serve `getBrowseCache(SECTION_TRENDING)` decoded instantly → `Success(cached)` + `showingDefaults = true` (a user who opened Browse once already has the row populated); (2) then fetch from network → `Success(fresh)` on success; (3) on network failure, if cache already served → KEEP it (don't clobber with Idle); (4) fall to Idle only when no cache row exists. All paths retain the D-258 staleness guards (query re-checked blank before every state write). Constructor gained `dataCacheRepository: DataCacheRepository` (5th param — resolved by the existing `viewModelOf(::SearchViewModel)` since DataCacheRepository is already a Koin single). `:feature:anime-search:impl` build.gradle gained `implementation(project(":core:data-cache"))`.
- **Why cache-first (not catch-fallback):** offline, the network call fails only after the ~30s OkHttp connect timeout — a catch-only fallback would show Loading for up to 30s before content. Cache-first shows content in ~ms (a DB read + JSON decode), matching BrowseViewModel's own local-first pattern.
- **Files:** BrowseCacheCodec.kt (new), BrowseViewModel.kt, SearchViewModel.kt, anime-search/impl/build.gradle.kts.
- **Status:** ✅ Implemented (commit ccd8aafe). Compile-review: COMPILE-CLEAN (dataJson field name, `SearchUiState.Success(results=)` param, Koin single binding all confirmed). NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-279 — Browse partial-success offline + hero popular fallback
- **What (user):** "Even when the user has no internet, things should load properly. The whole browse page should load properly and also the browse page should open up properly." Research sub-agent R-3 found: trending cache miss + popular/topRated cache hits → `BrowseState.Error` (the whole screen shows the error page even though 2 of 3 sections are cached + renderable).
- **Fix (BrowseViewModel):**
  1. `fetchSection` catch: when the trending fetch fails AND state is still Loading (no cache) AND `_popular` or `_topRated` have data → surface `BrowseState.Success(emptyList())` instead of `Error` — the screen renders the cached popular/topRated sections; only the hero is empty. Falls to `Error` only when ALL three sections have no cache (true cold start + no network).
  2. `heroItems`: now `combine(_state, _popular)` — when trending is empty (cold start + trending cache miss but popular cached), the hero falls back to popular items so the hero still renders with whatever cache exists.
- **Status:** ✅ Implemented (commit ccd8aafe). Compile-review: COMPILE-CLEAN. NOT merged — awaiting user device verification.
- **Date:** 2026-08-26.

---

### D-280 — "Data removed after update" audit (no code change) + Version 0.2.54
- **The audit (research sub-agent R-3, read-only):** The user reported "data seems to be removed after an update". Findings — the DB schema is version 1 with NO `.sqm` files and NO `onUpgrade` (version 1 == 1 → onUpgrade never fires); `DatabaseDriverFactory.migrateSchemaIfNeeded` runs additive, idempotent `ALTER TABLE ADD COLUMN` / `CREATE TABLE IF NOT EXISTS` / `DROP TABLE IF EXISTS <dead-table>` inside `onOpen` (every app open, column-guarded). Searched every `DELETE FROM`/`clear()`/`DROP`/`deleteAll`/`clearCache`/`nuke`/WorkManager purge: the only bulk deletes are user-triggered destructive actions with confirm dialogs (History clear-all, Updates clear-all) or cache-dir APK cleanups. **Conclusion: NO code path wipes data on an app update.** Most likely environmental causes (ranked): (1) APK signature mismatch forces uninstall+reinstall → wipes `/data/data/<pkg>` (DB + image cache) — `android:allowBackup="false"` means no restore path; (2) OS clears `cacheDir/image_cache` under storage pressure (images-only loss); (3) user-triggered clear-all. NOT fixable in code (deliberate design: allowBackup=false, debug-signed caveat already flagged on the dashboard) — offline resilience improved instead (D-278/D-279 make the app degrade gracefully even after a cache clear).
- **Version:** AndroidConfig 0.2.53 → **0.2.54** (versionCode 54) with the D-277..D-279 batch; annotated tag `v0.2.54` → release-apk.yml publishes the stable arm64-only release (`ani-kuta-v0.2.54.apk`, --latest) so a v0.2.53 install updates in-app.
- **Status:** ✅ Implemented. Release verification pending CI green.
- **Date:** 2026-08-26.

---

### D-281 — CORE_RULES §8 rewritten: CI-first compile verification (no sub-agent pre-review)
- **What (user):** "Utilizing sub-agents to find the compile errors is not a great option… You can directly build it using GitHub Actions and if it fails you can analyze the errors there. You most specifically do not need to utilize sub-agents to review it."
- **Change (workflow docs only — zero app code):** CORE_RULES.md §8, SESSION.md task loop, and workflow.md Steps 7/8 + the non-negotiable rules list all updated: the sub-agent compile-review step is REMOVED as a workflow stage. The loop is now **write → push → GitHub Actions builds the APK → read the results/annotations via the GitHub API → fix → repeat.** CI is the compiler of record. Plan-review sub-agents (logic-level review BEFORE implementation) remain allowed; only the post-implementation compile-review pass is gone.
- **Rationale:** the sub-agent pre-review added latency + was strictly weaker than the actual compiler — CI catches everything the review would (and nothing it wouldn't). Validated the same session: run 32977933759 caught a real compile error (D-284's vararg misuse) in ~3 min; the fix (f7740b0) went green on the next run.
- **Files:** CORE_RULES.md, SESSION.md, workflow.md (AGENT-CONTEXT).
- **Status:** ✅ Implemented (commit 93534f6, part of the CI-green f7740b0 push).
- **Date:** 2026-08-26.

---

### D-282 — Tab memory excludes More + Search (cold start lands on Browse/Library only)
- **What (user):** "When I close the app on the More section and reopen it, it should open on Browse or Library — the tab memory should not work on the More section… it also should not work on the Search section."
- **Fix (MainActivity.kt — AppRoot):**
  1. **Read site:** `startTab = appPreferences.lastTab.takeIf { it == "browse" || it == "library" } ?: "browse"` — cold start restores ONLY Browse/Library; a legacy persisted "more"/"search" value (D-267 persisted all four) sanitizes to Browse.
  2. **Write site:** `onSelect` persists ONLY `"browse"`/`"library"` — More + Search are session-scoped by design. The pref keeps the last VALID tab, so Browse → More → close → reopen restores Browse.
  3. **Backstack initialKey:** the `"search" → AnimeSearchKey` / `"more" → MoreKey` mappings removed (unreachable now).
- **Behavior:** close on More/Search → reopen lands on the last main tab; close on Browse/Library → reopens there.
- **Files:** MainActivity.kt.
- **Status:** ✅ Implemented (commit d5625d1, CI GREEN on f7740b0).
- **Date:** 2026-08-26.

---

### D-283 — Browse hero height reduction (card 1.2:1 → 1.4:1)
- **What (user, device test on v0.2.54):** "Its height is apparently a bit more than what I hoped for… the bottom section below the banner is way too much" — the banner itself displayed correctly (D-277's Fit fix confirmed working), but the card was too tall overall.
- **Fix (BrowseHero.kt):** `HERO_CARD_RATIO` 1.2f → **1.4f** (width:height) — the card is ~15% shorter and the below-banner content zone shrinks ~25%. A ~3:1 AniList banner now occupies the top ~47% of the card. Poster 84×126 → **76×114** to fit the shorter card. All pager pages share the ratio (pager height never jumps between banners of differing aspect). The poster (76×114 + 12dp bottom padding) may overlap the banner's feathered bottom edge by a few dp on narrower banners — the classic cinematic overlap; the banner is still fully rendered (Fit, never cropped).
- **Files:** BrowseHero.kt.
- **Status:** ✅ Implemented (commit 4356b5a; compile fix f7740b0 CI GREEN).
- **Date:** 2026-08-26.

---

### D-284 — Browse hero 6-color palette gradient + dark veil
- **What (user):** "Don't use a simple solid color but utilize a smooth darker kind of gradient. Utilize maybe five or six colors from the cover image and utilize them to create a smooth blended gradient effect… On top of that gradient effect, apply a slightly blurred dark effect."
- **Fix:**
  1. **`CoverColorExtractor.extractGradientColors`** (new pipeline, ~90 lines): loads the shared 100×100 swatch bitmap (Coil memory-cache dedupes vs `extract`), collects Palette's named swatches (dominant/vibrant/dark-light vibrant/muted/dark-light muted — topped up with population-ranked swatches when < 6 named), darkens each into a cinematic HSL band (L∈[0.16, 0.42], S∈[0.25, 0.85] — the "smooth darker" feel), sorts light→dark (monotonic luminance = smooth vertical ramp), merges near-duplicate stops (RGB distance < 48), resamples to EXACTLY 6 evenly-spaced stops via piecewise lerp — narrow palettes (grayscale/flat covers) get synthesized intermediates so the ramp is ALWAYS 6 steps.
  2. **`rememberCoverGradientColors`** (new composable in `:core:designsystem` — CoverAccentColor.kt): produceState wrapper, same self-contained no-Koin pattern as D-277's `rememberCoverDominantColor`.
  3. **HeroCard gradient (BrowseHero.kt):** explicit-position stops — `[0 → Transparent, BANNER_FEATHER_END=0.42 → Transparent]` over the banner (it reads fully), feathered junction, then the 6 palette colors blended to the bottom; base `background(ramp[0])`. Fallback (null extraction): surfaceVariant darkened in 6 steps.
  4. **Dark veil:** soft black gradient (0.04 → 0.10 → 0.32 alpha) layered OVER the ramp — the "slightly blurred dark effect". A literal `Modifier.blur()` on a smooth vertical gradient is a visual no-op (no horizontal variance to smear) — documented in KDoc; the veil also guarantees white-text contrast on any cover palette.
  5. **Compile fix (f7740b0):** CI run 32977933759 caught `Brush.verticalGradient(colorStops = List<Pair<Float,Color>>)` — the param is a VARARG. Fixed with spread operators (`*gradientStops.toTypedArray()`, `*arrayOf(...)`). The new §8 loop's first catch.
- **Files:** CoverColorExtractor.kt, CoverAccentColor.kt (new), BrowseHero.kt.
- **Status:** ✅ Implemented (commit 4356b5a + fix f7740b0, CI GREEN).
- **Date:** 2026-08-26.

---

### D-285 — Library batch loader (N+1 + main-thread freeze fix)
- **What (user):** "Switching from Browse to Library, the whole page reloads… The 'All' section is the worst — 653 entries take 4-5 seconds to display."
- **Root causes (both found by reading the code):**
  1. **N+1 queries:** the entry-building loop issued `getMainEntryByMainId` + `getContentDetails` per entry, and `enrichEntriesWithBadgeData` issued `getEpisodeMetadata` + `getWatchedEpisodeCount` + `getLastWatchedAt` per entry — **~5 queries × 653 entries ≈ 3,300 queries** per load.
  2. **ALL on the main thread:** `viewModelScope.launch` dispatches on `Main.immediate`; there was ZERO `withContext` in the ViewModel — the UI froze for the entire 4-5s load.
- **Fix — the load is now 7 queries total, assembled in memory, on `Dispatchers.Default`:**
  - **Batch queries (additive named queries — NO schema changes, §"don't touch the DB structure" honored):** `getAllWatchedCounts` + `getAllLastWatchedAt` (watch.sq — GROUP BY main_id), `getAllEpisodeAudioRows` (dataCache.sq — only the 4 columns the audio parser + counter need), `getAllLibraryMainEntries` (content.sq — main_entry JOIN deduped library_item subquery, added_at DESC order preserved). `getAllLibraryItems` + `getAllContentDetails` already existed — REUSED (my first push added duplicates; CI run 32979727730 failed with "Duplicate SQL identifier" — the §8 loop caught it; fix 0809551 removed the duplicates; the pre-existing queries return the same columns so the Kotlin mappers compile unchanged).
  - **Repository/store batch methods:** `ContentRepository.getAllLibraryItems()→List<LibraryItemRecord>` (new lightweight model) + `getAllLibraryContentRecords()` + `getAllContentDetailsMap()`; `WatchProgressStore.getAllWatchedCounts()/getAllLastWatchedAt()` (+ SqlDelight impls); `DataCacheRepository.getAllEpisodeAudioAggregates()→Map<mainId, EpisodeAudioAggregates>` (new model — released count + audio flags + per-type counts, same parseAudioAvailability semantics as the old loop).
  - **`loadLibraryImpl` rewritten:** categories (1) + library items (1) → category filter + per-category counts + total IN MEMORY; main entries (1) + content details (1) → in-memory join; enrich (3 batch maps). `reloadFromCache` now delegates to it (was a duplicated ~90-line per-entry loop).
  - **Honest finding:** the old "fetch AniList on miss" branch was UNREACHABLE dead code — `anilistId != null` requires `dataSourceType == "anilist"` which implies `hasDataSourceLink == true` which always took the cached branch first. Removed rather than mirrored; entries with no data link have no stored AniList ID to fetch with anyway.
  - **Side fix:** category-filtered mainIds now preserve added_at DESC order (the old `getMainIdsByCategory` had no ORDER BY → the DATE_ADDED sort was inconsistent between All and category views).
- **Files:** watch.sq, dataCache.sq, content.sq, library.sq (comments only), WatchProgressStore.kt, SqlDelightWatchProgressStore.kt, DataCacheRepository.kt, DataCacheModels.kt, ContentRepository.kt, ContentModels.kt, LibraryViewModel.kt.
- **Status:** ✅ Implemented (commits 1e963f3 + 0809551; CI on 0809551 pending at doc time).
- **Date:** 2026-08-26.

---

### D-286 — Library instant tab switch (state retention + scroll position)
- **What (user):** "The page should already be cached and displayed instantaneously" when switching tabs.
- **Fix (LibraryViewModel + LibraryScreen):**
  1. **No more Loading flash:** `loadLibrary()` checks `_state.value is LibraryState.Success` — if the grid is already on screen it stays there while the (now-fast, batched) reload runs silently in the background and swaps the result in. The old unconditional `_state.value = LibraryState.Loading` tore the whole grid down on EVERY tab switch (LaunchedEffect fires loadLibrary on every re-entry into composition).
  2. **Scroll position survives:** `gridState`/`listState` moved into the Activity-scoped ViewModel (`LazyGridState()`/`LazyListState()` constructed outside composition — standard pattern). The old `rememberLazyGridState()` died with the composable when the user left the Library tab, snapping the grid back to the top on every return — part of the "whole page reloads" feel. Coming back now shows the list exactly where the user left it.
- **Files:** LibraryViewModel.kt, LibraryScreen.kt.
- **Status:** ✅ Implemented (commit 1e963f3; CI on 0809551 pending at doc time).
- **Date:** 2026-08-26.

---

### D-287 — Grid scroll performance (5-column smoothness + scroll-back re-loads)
- **What (user):** "In the 5-column mode, the scrolling is not very smooth because it needs to load a lot of images… fast scrolling jitters… when I scroll to the bottom and then scroll back to the top, the images load again." (Progressive loading during forward scroll is approved behavior — keep it.)
- **Fix — new `LibraryCoverImage` composable replacing all 3 cover AsyncImage sites (comfortable grid, compact/cover-only grid, list rows):**
  1. **`crossfade(false)` per cell:** overrides the ImageLoader's global crossfade for dense grid/list cells only (hero/detail images keep it). The 100ms opacity animation ran per cell during fast 5-column scroll (10+ concurrent fades = extra invalidation frames) and re-faded every cover on scroll-back cache repopulation — making the disk re-decode read as a full re-load.
  2. **`bitmapConfig(Bitmap.Config.RGB_565)`:** 2 bytes/pixel instead of ARGB_8888's 4 — halves each cover's footprint in Coil's memory cache (25% of app memory), so a 653-cover "All" grid stops evicting itself mid-scroll; scroll-back now hits the memory cache instead of re-decoding from disk. Covers are opaque (alpha unused — Crop fills the cell) and at thumbnail scale RGB_565 banding is imperceptible.
  - Request built once per URL via `remember(url, context)`; progressive loading UNCHANGED.
- **Files:** LibraryScreen.kt.
- **Status:** ✅ Implemented (commit 1e963f3; CI on 0809551 pending at doc time).
- **Date:** 2026-08-26.

---

### D-289 — Browse hero v6 (compact fixed height + banner-as-background + abstract splash)
- **What (user, device test on v0.2.55):** "The hero section height is very bad… way too tall. I need you to make it less tall." / "The top banner area… could be in the background, over the cover image and the background of the text… make sure that the hero section is a little bit taller than the cover image itself." / "I did not want you to quite literally go with a gradient… a random splash of colors… not a smooth gradient… some splash of colors which blend in together with each other randomly… an abstract splash kind of vibe." / "The cover image's colors would blend in smoothly around it. Also the top banner section would blend in smoothly towards it too. The difference between where the top banner ends and the bottom section starts would be minimal."
- **Fix (BrowseHero.kt — HeroCard internals redone):**
  1. **Fixed `HERO_HEIGHT = 148dp`** replacing the 1.4:1 aspect ratio (~234dp on a 360dp-wide screen): a little taller than the 114dp cover poster it frames (+12dp bottom padding + ~22dp airy strip above).
  2. **Banner as full-bleed background** — `ContentScale.Crop` + `Alignment.Center` over the whole card (atmosphere behind everything; the "uncropped showcase banner" requirement is superseded by the user's new "in the background" instruction).
  3. **SplashOverlay** — 8 soft-edged radial-gradient blobs drawn via `drawBehind` in the cover's own 6-color palette (D-284 extractor): 2 airy low-alpha top blobs (banner still reads), 5 denser bottom-zone blobs (0.32–0.55 alpha, overlapping = organic SRC_OVER blending), 1 poster-echo blob (lightest palette color behind the cover = "cover colors blend around it"). Blob layout seeded by `coverUrl.hashCode()` — stable per item, different per banner. NO linear gradient anywhere.
  4. **Unifying veil** — smooth 0.06 → 0.52 black ramp over everything. Because the banner never "ends" (spans the full card) and every blob edge is a radial falloff, there is no detectable banner↔content boundary — the seamless-blend requirement is structural, not cosmetic.
- **Files:** BrowseHero.kt.
- **Status:** ✅ Implemented (commit 8fa46be; CI GREEN run 32993791653).
- **Date:** 2026-08-26.

---

### D-290 — Library scroll-jump fix (single-emission state pipeline + staggered hoist + dataset-change resets)
- **What (user):** "Sometimes the library page would automatically scroll to the bottom or some middle area… even though previously I was at the very top. When I refreshed the library page, the library page did not stay at the very top… It scrolled way too much down automatically by itself… about the middle."
- **Root cause (R-1 research sub-agent):** `loadLibraryImpl` emitted `Success(entries)` in DATE_ADDED order and THEN `applyFilters()` re-emitted the sorted list. If a recomposition landed between the two writes (preemption/GC-pause window on Dispatchers.Default), the grid composed the UNSORTED list and LazyGrid's key-based anchoring (`key = mainId`) followed the previously first-visible item to its DATE_ADDED rank — the middle of the 653-item list. Additional contributors: COMFORTABLE_GRID's `rememberLazyStaggeredGridState()` was NOT VM-held (died on tab switch while gridState went stale), and dataset changes (category switch, search) kept a stale retained index.
- **Fix (LibraryViewModel + LibraryScreen):**
  1. **Single emission:** the final filtered+sorted list is computed BEFORE any state write (`filterAndSort` pure function shared by loadLibraryImpl and applyFilters) — no unsorted intermediate ordering can ever be composed; the whole bug class is gone.
  2. **`masterEntries`** (unfiltered, unsorted) held in the VM; `applyFilters` re-derives from it — also fixes a LATENT BUG: the old applyFilters re-filtered the ALREADY-filtered state, so clearing a search query could never restore removed entries until a full reload.
  3. **`staggeredState` hoisted to the VM** — Comfortable mode now retains scroll exactly like grid/list modes.
  4. **`resetScrollToTop()`** (non-suspend `requestScrollToItem(0, 0)` on all three states, foundation 1.7+) on category switch and search-query change — a changed dataset presents from its top; no stale-index mid-list landings.
  5. **Resume refresh now invisible:** with single-emission + structural-equality conflation (`LibraryEntry` is a @Immutable data class), the `LaunchedEffect(Unit) { loadLibrary() }` on tab re-entry produces an EQUAL Success that StateFlow DROPS — no flash, no grid teardown, no scroll disturbance; genuinely changed data still swaps in.
- **Files:** LibraryViewModel.kt, LibraryScreen.kt.
- **Status:** ✅ Implemented (commit 8fa46be; CI GREEN run 32993791653).
- **Date:** 2026-08-26.

---

### D-291 — Reveal-once cover animations (velocity-adaptive, one fade per cover, ever)
- **What (user):** "The loading of the images is not smooth. All the images just outright jump into it… I wanted a smoother experience… they would all show up one by one with a smoother animation… The speed of them will be faster as the users scroll faster… if the user jumps into some area directly then it will slow down that area smoothly… If I scroll one time to the very top and then to the very bottom… it should not be loading any images [on the way back]… It should only work if they were not loaded. If previously loaded then no need to reload them completely unless the user refreshes the whole page again."
- **Fix (LibraryScreen + LibraryViewModel):**
  1. **`CoverRevealController`** threaded screen → LibraryGrid/LibraryList → cards → `LibraryCoverImage`: a `State<Float>` velocity factor + isRevealed/markRevealed lambdas backed by the VM's `revealedCoverKeys` set (Activity-scoped — survives tab switches; cleared ONLY by `refreshLibrary()` — pull-to-refresh is the user's explicit "reload everything" signal).
  2. **Reveal-once gate:** an unrevealed cover starts at alpha 0 (soft surfaceVariant@0.35 placeholder reads as reserved space) and fades 0→1 on its FIRST load success; a revealed cover renders at full alpha INSTANTLY — scroll-back and tab-return never re-animate.
  3. **Velocity-adaptive duration:** `rememberScrollVelocityFactor` tracks an EMA over a `snapshotFlow` of the active list's `index*4096 + offset` signal, with a 150ms decay loop (post-fling loads get the calm fade). Sampled NON-reactively at load completion: 240ms calm → 70ms hard fling. Reading it reactively in cells would recompose every cell on every scroll frame — the exact churn D-287 removed.
  4. **Draw-phase animation:** the fade alpha is read inside `graphicsLayer { alpha = revealAlpha.value }` — animating re-DRAWS only the cell's layer; zero recomposition churn (safe for 10+ concurrent fades in 5-column mode).
  5. `crossfade(false)` + `bitmapConfig(RGB_565)` kept from D-287 — the reveal system owns ALL animation.
- **Files:** LibraryScreen.kt, LibraryViewModel.kt.
- **Status:** ✅ Implemented (commit 8fa46be; CI GREEN run 32993791653).
- **Date:** 2026-08-26.

---

### D-292 — Cover accent palette off main thread + extraction gating (scroll-jank fix)
- **What (user):** Library scrolling "was not smooth, it was not proper" — persistent jank in the 653-item grid.
- **Root causes (both found this session):**
  1. `rememberCoverAccentColor`'s `Palette.from(bitmap).generate()` ran ON THE MAIN THREAD — produceState's producer coroutine inherits the composition's Main dispatcher, and `imageLoader.execute()` only suspends for the LOAD; the synchronous generate() (5–20ms per cover) blocked main for every new card entering the viewport.
  2. The call ran UNCONDITIONALLY in LibraryGridCard + LibraryListRow — every card did a 100×100 Coil load + Palette even with cover borders disabled (the default). This was likely the PRIMARY scroll-jank source all along.
- **Fix (CoverAccentColor.kt + LibraryScreen.kt):**
  1. HARDWARE bitmap copy + Palette.generate() + swatch pick now inside `withContext(Dispatchers.Default)`.
  2. 256-entry `LruCache<String, Int>` keyed `url|isDark`, with a `FAILED_EXTRACTION` sentinel so failures are cached too (no retry storms for covers that can't produce a swatch).
  3. Extraction gated on `coverBorderEnabled && coverBorderColor == ADAPTIVE` in BOTH card sites — zero extraction work in the default configuration.
- **Files:** CoverAccentColor.kt, LibraryScreen.kt.
- **Status:** ✅ Implemented (commits 8fa46be + 26beba9; CI GREEN run 32993791653).
- **Date:** 2026-08-26.

### D-294 — Parent-first extension classloader (ROOT FIX: "extensions disappear after trust")

**Context:** User device-report: extensions from salmanbappi/extensions-repo show up UNTRUSTED but vanish from every list the moment they're trusted. 82 extensions (80× lib-16, 2× lib-14), none load.

**Root cause (verified via custom AXML/DEX parsing of the APKs, no Android tooling needed):**
1. *(Loader)* Our `ChildFirstPathClassLoader` let an extension's PARTIAL bundled kotlin-stdlib shadow the host's complete stdlib (app ships kotlin 2.2.0; the template family bundles kotlin 2.0.x partials — 600+ `kotlin.*` classes). Mixed-stdlib class-identity breakage throws during source instantiation (the sb-template's `AnikotoTheme` even has an EAGER `client = network.client.newBuilder()...` initializer that runs at construction). The WORKING extension (anikoto v14.4) is R8-MINIFIED with ZERO kotlin bundled — which is exactly why it never hit the issue.
2. *(UI)* `ExtensionManager.trustExtension` removed the extension from `_untrustedExtensions` and, when `loadExtension` returned `Error`, did NOTHING — the extension vanished from every list. `loadAll`'s `Error` branch only logged.

**Fix:** `ExtensionLoader` now builds a plain **parent-first `PathClassLoader`** — EXACTLY like reference Aniyomi (extensions never shadow host classes; their bundled kotlin is inert dead weight, resolving instead to the host's binary-compatible stdlib). `ChildFirstPathClassLoader` deleted. All host-API refs the failing extensions use were verified compatible first (Video 16-param synthetic ctor, Hoster, AnimesPage 2-arg, AnimeFilter.Select/Separator synthetic ctors, Track(url, lang), rateLimitHost, androidx.preference typealias, Injekt registration) — the classloader was the only incompatibility.
- **Files:** ExtensionLoader.kt (+ ChildFirstPathClassLoader.kt deleted).
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-295 — LoadResult.Error carries the real failure reason

`instantiateSource` now returns Success/Failure per declared source class; failures include the exception class + message ("MovieBox: java.lang.NoSuchMethodError: ..."). `LoadResult.Error` gained a `name` field for display. No more generic "No sources instantiated".
- **Files:** ExtensionLoader.kt, LoadResult.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-296 — Errored extensions are VISIBLE (never silently dropped)

New `AnimeExtension.Errored` state + `ExtensionManager.erroredExtensions` StateFlow. `trustExtension`/`retryExtension` share `applyLoadResult` which routes EVERY LoadResult branch (Success → installed + sources registered; Error → errored with reason; Untrusted → back to untrusted). `loadAll` populates the errored list too. The extensions screen gets a "Failed to Load" section with per-row failure message + Retry / Untrust / Uninstall. `untrustExtension` widened to accept any AnimeExtension (errored rows can go back to untrusted). Trust-time classloading moved off the main thread (Dispatchers.Default).
- **Files:** AnimeExtension.kt, ExtensionManager.kt, ExtensionsSettingsScreen.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-297 — Extension lib-version policy (17.0 known-good, attempt everything)

`LIB_VERSION_MAX` 16.0 → 17.0 (lib-17 APIs — `server`, `getVideoThumbnails`, `getImageTile` — already exist in :core:source-api). Out-of-range versions (older OR newer than known-good) are **attempted anyway** — if the load fails the user sees the visible Errored row with the reason (D-296), never a silent drop. The range is documentation, not a gate.
- **Files:** ExtensionLoader.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-298 — Installed.lang populated + language filter

`Installed.lang` was ALWAYS null (loader gap). Now populated from the instantiated sources (most common non-blank source lang, fallback to first). The extensions screen gains a language filter (globe icon in the filters bar; All + the distinct languages across installed/errored/untrusted/available) applied to every section.
- **Files:** ExtensionLoader.kt, ExtensionsSettingsScreen.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-299 — Virtualized extensions list

All sections previously rendered EVERY row inside one non-virtualized `Column`-in-`item` (the Available section = 80+ rows composed eagerly + 80 icon fetches). Now every section header is its own item (`SectionHeader`, rounded-top card) and every row is its own item with `key` + `contentType` — full LazyColumn virtualization.
- **Files:** ExtensionsSettingsScreen.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-300 — Single canonical install path

`ExtensionManager.installExtension` previously duplicated `ExtensionInstaller.downloadAndInstall`'s entire download + service-dispatch pipeline (a drift hazard). The manager now delegates to the installer (keeping install-state tracking + its own mutex) and its private `downloadApk` + service dispatch were deleted; the unused `okhttpClient` constructor param removed (Koin module updated).
- **Files:** ExtensionManager.kt, ExtensionModule.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-301 — Auto update-checking (GitHub-repo based)

Entering the extensions page now triggers `checkForUpdates()` automatically — throttled to once per 30 min (repeated entries are no-ops), non-blocking, with a subtle Checking state (`UpdateCheckState` StateFlow) that shows the spinner only when the Available list is still empty. Repo-set changes force a fresh check. `hasUpdate` (versionCode comparison against the repo index, computed since D-285-era but inert) now surfaces as an **Update button** on installed rows → installs the newer Available entry via the canonical installer (PackageInstaller replace → PACKAGE_REPLACED broadcast → re-scan).
- **Files:** ExtensionManager.kt, ExtensionsSettingsScreen.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-302 — provider-api made real (VideoExtensionProvider + AniyomiExtensionProvider)

:core:provider-api was a sealed `ExtensionProvider` interface with ZERO implementations (D-031 scaffolding). Now: `VideoExtensionProvider` (sources StateFlow, findSource, install/uninstall/setEnabled/checkForUpdates) + app-owned `SourceDescriptor` model (no third-party types cross the boundary) + `AniyomiExtensionProvider` facade over ExtensionManager, registered in Koin. Existing consumers unchanged (they keep using the manager directly); NEW consumers program against the provider interface so a second ecosystem (Mangayomi/Sora/CloudStream/Kotatsu) can be added without touching feature code.
- **Files:** ExtensionProvider.kt, AniyomiExtensionProvider.kt (new), ExtensionModule.kt.
- **Status:** ✅ Implemented (commit 301c4a78).
- **Date:** 2026-08-27.

### D-303 — Version 0.2.57 (versionCode 57)

Extension-system overhaul release: D-294..D-302.
- **Files:** AndroidConfig.kt.
- **Status:** ✅ Implemented; tag v0.2.57 + release after CI green.
- **Date:** 2026-08-27.
