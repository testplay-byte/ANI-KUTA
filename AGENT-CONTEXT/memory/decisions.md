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
- **Status:** ✅ Implemented (Phase 5c, session web-f53f0459). Capture-only — restore deferred to Phase 5e.
- **Date:** Phase 5c (session web-f53f0459).

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
