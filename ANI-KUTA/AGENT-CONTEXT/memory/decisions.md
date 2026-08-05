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
