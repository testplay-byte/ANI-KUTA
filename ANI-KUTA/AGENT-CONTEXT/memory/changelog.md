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
- **User feedback: documentation folder mistake**. Docs 10-16 (new project research/architecture) were incorrectly placed in `REFERENCES/old-kuta/DOCUMENTATION/` (old project analysis zone). Added CORE_RULES.md §21 (Documentation Folder Organization — 3 zones: old project / new project / agent knowledge). Moved docs 10-16 to `APP/ani-kuta/DOCUMENTATION/`. Logged lesson learned. Updated all cross-references.
- **App Design Language doc created** (`APP/ani-kuta/DESIGN-LANGUAGE.md`, 1,882 lines). Sub-agent analyzed 60+ Kotlin source files from the old project (Settings, Profile, Appearance, Episode Settings, Update menu, Search, Library, Anime Details, Watch, Player, ScrollBlurOverlay, theme system). **Verified by main agent** (spot-checked Color.kt values, ScrollBlurOverlay implementation, BottomNavBar specs — all accurate). Key findings: lime #B1F256 primary, 5-tier dark surface ramp (#14111F→#3D3656), translucent cards (no shadow), floating pill bottom nav, gradient scrim scroll blur (not real blur), dynamic cover-color theming, 300ms FastOutSlowInEasing motion.
- **Dashboard updated** (full-stack-dev sub-agent): new `/design` page (app design language with live color swatches + component demos), `/architecture` page rewritten (43-module tree, identity graph, data flow, multi-extension diagram, Phase 2 scaffold), `/decisions` all 15 confirmed, `/progress` updated, overview updated. Build verified ✅.
- Added `knowledge/app-design-language.md` (agent summary pointing to the full doc). Updated navigation.md, master.md, SESSION.md with new folder structure.
- **Phase 2 scaffold complete + CI GREEN** ✅. Replaced the single-module demo with a 12-module multi-module Android app:
  - Build system: `:build-logic` composite build (4 convention plugins), consolidated version catalog, Gradle 8.11.1.
  - Versions: Kotlin 2.2.0, AGP 8.9.1, Compose BOM 2025.03.00, Nav3 1.1.5, Koin 4.2.2, SQLDelight 2.0.2, OkHttp 4.12.0, Coil 3.0.4. compileSdk/targetSdk 36, minSdk 24.
  - Core modules (7): common (Logger, ContentType, DispatcherProvider), designsystem (AnikutaTheme — lime #B1F256 on warm-purple darks), database (SQLDelight app_metadata table), preferences (PreferenceStore), navigation-api (NavKey, ContentMode), network (HttpClientFactory), anilist (GraphQL client — fetchTrending + fetchAnimeDetails).
  - Feature modules (4, api/impl split): anime-browse (BrowseScreen — grid of trending anime, Coil images), anime-details (DetailsScreen — banner, cover, info, description).
  - App module: AnikutaApp (Koin setup + Logger init), MainActivity (single Activity + Nav3 AppRoot — state-owned backstack), AndroidManifest, resources (lime launcher icon, dark theme).
  - Nav3 pattern: mutableStateListOf<NavKey> backstack, type-safe @Serializable NavKeys (AnimeBrowseKey, AnimeDetailsKey). Full NavDisplay adoption deferred to Phase 3.
  - 5 CI iterations to green: (1) build-logic Maven coordinates, (2) compileSdk 36 + SQLDelight 2.0.2, (3) CI timeout 15→30 min, (4) :core:anilist missing deps, (5) OkHttp api vs implementation. 6 lessons learned logged.

## Phase 3 — Core Modules (15 modules, 4 sub-phases)
- **Phase 3a — Foundation** (`7b7de81`): provider-api (ExtensionProvider abstraction, Source/SourceContent/SourceVideo models — multi-extension D-031 foundation), source-api (eu.kanade.tachiyomi.animesource.* — Aniyomi binary-compat contract, 36 files), database (SQLDelight: watch, app, downloadQueue, tracking, extensions, customization, downloadedEpisode, metadata — 8 .sq files). Fixes: coroutines api() in provider-api, nullable ResponseBody for OkHttp 4.x, :core:preferences dep.
- **Phase 3b — Extensions** (`b26f292`): data:extension (ExtensionLoader — child-first classloader, ExtensionManager — install/list/trust, TrustService, model classes). Fixes: :core:preferences dep, JVM signature clash (getTrustedFingerprints property vs getAllTrusted() function — `count` reserved keyword avoided).
- **Phase 3c — Playback** (`915848a`): player (AnikutaMPVView — wraps aniyomi-mpv-lib, PlayerStateHolder, PlaybackStateStore, PlayerObserver, PlayerInitializer), player-mpv-lib (AAR wrapper module for swappable players — D-044), video-resolver (VideoResolver — fetches video list from source, resolves playable URL), download (DownloadManager — queue + state machine, DownloadModule). Fixes: split downloads.sq for SQLDelight query names, RxJava awaitSingle for Observable<List<Video>>, MPVLib format constants (Int not String), abstract MPV methods, koin-android for download.
- **Phase 3d — Supporting** (`dd2ae26` — **Phase 3 COMPLETE**): metadata (MetadataMerger — local > AniList > source, MetadataRegistry, LocalMetadataProvider + AniListMetadataProvider), tracker-api (Tracker interface, BaseTracker, TrackerTypes), tracker-anilist (AniListTracker — OAuth, TrackSyncManager — one-way internal→external relay D-045), activity-tracker (ActivityEvent, ActivityTracker — 365-day default D-039), watch-progress (WatchProgress, WatchProgressStore). Fixes: :core:preferences dep, coroutines api() in tracker-api, nullable String in AniListTracker, TrackLoginState→TrackerLoginState typo.
- **Dashboard updated** (`679973b`): grey dark mode (not brown), stationary sticky header (not floating), Phase 3 module data. Build verified.

## Phase 4 — Feature Screens (in progress)
- **Phase 4a — App shell + nav** (`cf2ddd2`): bottom nav (AnikutaBottomNavBar — floating pill, 4 tabs), enhanced Browse (Coil grid), Details (banner+cover+info), app restructure (AppRoot with state-owned backstack). Fixes: ContentTransform sizeTransform Float (1→1f), AnimatedContent transition, bottom nav ripple/text colors/back gesture/browse padding (user feedback).
- **Phase 4b — Library + Search + More + Settings** (`db1457e`, `3e8c4dd`): Library (grid/list views, sort sheet, customize sheet with Display & Badges tab, category tabs, search bar), Search (AniList search, filter sheet, recent searches), More screen, Settings hub, Appearance screen (General: theme mode, palettes carousel, AMOLED, adaptive colors, header blur effect). Multiple fixes: AnimatedVisibility ColumnScope in LazyColumn, BorderStroke import (foundation not material3), theming (light mode backgrounds/text), bottom-nav hidden on sub-screens, BackHandler on all screens, library sheet height cap, search top padding (`290efa3`).

## Session web-3a43f99b (current) — UI fixes + accent palette system + docs catch-up
- **Library CustomizeSheet height fix**: cap the WHOLE sheet Column at 70% screen height (was capping only the inner LazyColumn at 75%, so sheet = list+header+tabs exceeded the limit). LazyColumn now constrained by parent → wraps when short (Sort), scrolls when tall (Display & Badges). Same 70% cap applied to search FilterSheet for consistency.
- **Browse heading added**: CollapsingHeader "Browse" + grid state + ScrollBlurOverlay (matches Library pattern). Browse previously had no top heading.
- **Accent palette system (D-053) — FUNCTIONAL**: AccentPreset enum (10 presets + CUSTOM) + AccentColors derivation (containers via lerp) in :core:designsystem. AnikutaTheme takes accentSeed param, overrides primary/primaryContainer/onPrimary/onPrimaryContainer. ThemePreferences stores accentPreset + customAccentColor. MainActivity passes resolved seed. PalettesCarousel now reads/writes the preset — tapping applies the accent LIVE (selection ring + check badge, improved card preview). Custom color-picker UI deferred to Phase 5 (selection + storage work now).
- **Documentation catch-up**: progress.md (was stale at Phase 2 → updated to Phase 4 reality), changelog (Phase 3 + Phase 4 entries added), decisions D-052/D-053, lessons-learned (heightIn placement, static-placeholder disappointment). Dashboard updated to Phase 4 (31 modules, D-052/D-053) via sub-agent.

## Session web-3a43f99b (second pass) — Phase 5 re-plan + dashboard fixes + doc-verification rule
- **Phase 5 plan RE-ORDERED (D-054):** User rejected the first plan (identity-first). New order: **5a** Extensions → **5b** Details → **5c** Watch → **5d** Identity → **5e** History/Updates → **5f** Backup/Color-picker. Rationale: make the app *functional/watchable* first (5a–5c), then layer refinements (5d–5f). The minimal `source_link` row in 5b is enough to watch; the full ContentUID graph (5d) is a mechanical migration, not a prerequisite. Plan rewritten in `19-phase5-plan.md`. Open questions Q-056..Q-061.
- **CORE_RULES §26 added** (Documentation Verification — Continuous): same-session doc updates, drift-check at task end (grep stale phase/module/decision refs), sidebar/nav audit, session-end checklist. Created because progress.md said "Phase 2" while in Phase 4, and the dashboard sidebar still showed "Phase 3" after Phase 4 — both user-caught embarrassments.
- **Dashboard fixes (sub-agent, pending):** remove stale "Phase 3" nav item from sidebar, move dark-mode toggle into the stationary Sidebar (remove the scrolling page-level Header), update all data to the new Phase 5 plan, remove unnecessary content, verify build.
- **Lessons logged:** identity-first planning mistake, doc-drift as a recurring pattern, dashboard sidebar/nav audit gap.

## Session web-3a43f99b (third pass) — Phase 5a implementation (extension management)
- **Decisions D-055..D-060 recorded** (user answers to Q-056..Q-061): source browse merged into Search (no separate tab), episode sort descending, video quality ask-each-time, updates in More section, auto-match trusted-sources-only, backup default daily.
- **Phase 5a data layer built** (ported from old project, adapted to new packages):
  - `AnimeExtension` sealed class (Installed/Available/Untrusted) — replaces the old simple `Extension` data class.
  - Repo system: `ExtensionRepo`, `ExtensionRepoApi` (fetch + verify), `ExtensionRepoRepository` (SharedPreferences-backed CRUD), `RepoVerificationResult`.
  - Installer system: `InstallStep` enum, `ExtensionInstaller` (OkHttp download + service dispatch, Mutex serialization), `ExtensionInstallService` (foreground service), `PackageInstallerBackend` (Android PackageInstaller wrapper), `ExtensionInstallReceiver` (dynamic broadcast receiver for package changes).
  - `AnimeExtensionApi` (orchestrator — fetches from all repos, deduplicates).
  - Updated `ExtensionManager` (full: load + trust + install + uninstall + available + hasUpdate/isObsolete).
  - Updated `ExtensionLoader` (uses new AnimeExtension model, TrustService dependency).
  - Updated `ExtensionModule` DI (named OkHttpClient for repo, all singletons).
  - Updated `:data:extension` build.gradle (added koin-android, core-ktx).
- **Phase 5a UI layer built** (new `:feature:extensions-settings` module):
  - `ExtensionsSettingsScreen` — three sections (Trusted Sources / Untrusted / Available), install/trust/uninstall buttons, CollapsingHeader, reactive StateFlow.
  - `ExtensionRepoSettingsScreen` — add/list/delete repos, verify-before-add dialog, FAB.
  - Nav keys (`ExtensionsSettingsKey`, `ExtensionRepoSettingsKey`) in the api module.
- **App wiring**: settings.gradle (2 new modules), app build.gradle (2 new deps), AndroidManifest (permissions: INSTALL_PACKAGES, FOREGROUND_SERVICE, QUERY_ALL_PACKAGES + service declaration), SettingsScreen (Extensions nav row), MainActivity (nav routing).
- **Research**: 3 parallel Explore sub-agents analyzed the old project's extension system (5a), details screen (5b), and watch screen (5c) — comprehensive reports saved as reference for porting.
- **Pending**: source browsing merged into Search (D-055), Phase 5b (Details overhaul), Phase 5c (Watch screen).

## Session web-3a43f99b (fourth pass) — Phase 5a fixes + Search source browsing
- **CRITICAL FIX — ExtensionLoader metadata keys**: Was using `ani.source.class` (invented) instead of the Aniyomi convention (`tachiyomi.animeextension` feature flag + `tachiyomi.animeextension.class` meta-data). Real Aniyomi extensions were invisible — installed extensions didn't show up at all. Fixed to use the exact Aniyomi keys + added `ChildFirstPathClassLoader` (child-first DEX loading for binary-compat) + proper `signingInfo` handling. This was the root cause of the "no untrusted section" bug.
- **ExtensionsSettingsScreen improvements** (per user feedback):
  - CollapsingHeader "Extensions" that shrinks on scroll + ScrollBlurOverlay (was missing).
  - Three sections in dedicated background cards (`ExtensionSectionCard`) with minimal horizontal padding.
  - Untrusted section now shows (was empty because loader couldn't find extensions).
  - Available extensions filtered to exclude installed/untrusted packages (was showing already-installed extensions).
  - Download button shows a circular spinner during install (`installStates` tracking in ExtensionManager — per-package `InstallStep` StateFlow; auto-clears when the extension appears in installed/untrusted via the re-scan).
  - Filters bar: search + sort (name/language/NSFW) + NSFW toggle. Applies to all 3 sections.
  - Trusted sources: long-press enters reorder mode (up/down arrows). Delete button with confirmation dialog.
  - Untrusted: trust + delete buttons.
- **Search page source browsing (D-055)**:
  - `SearchViewModel`: two source modes (ANILIST default, EXTENSION). ANILIST shows trending when no query (per user request). EXTENSION browses selected source's popular anime; searches the source when a query is entered.
  - `ExtensionSourcePickerSheet`: bottom sheet listing all trusted sources. Tapping selects + persists the choice (PreferenceStore).
  - `SourceToggle`: when Extension is already selected, tapping it again opens the source picker (per user spec). Shows the selected source's name as the label.
  - `ExtensionAnime` model (in :api — no source-api dep) + `SAnimeMapper.toExtensionAnime()` (in :impl).
  - `ExtensionResultsGrid` + `ExtensionResultCard` — grid of extension anime.
- **Lessons logged**: loader metadata-key mistake (invented keys vs Aniyomi convention), installable-items filtering pattern, ChildFirstPathClassLoader requirement, withLock suspend-context mistake.

## Session web-3a43f99b (fifth pass) — Phase 5A fixes + 5B Details overhaul + DESIGN-LANGUAGE + CORE_RULE §27
- **Phase 5A fixes** (per user feedback):
  - **Extension icons**: Added `icon: Drawable?` to Installed + Untrusted models. Loader now calls `appInfo.loadIcon(packageManager)`. UI renders via Coil `AsyncImage(model=drawable)`.
  - **Untrust fix**: Was NOT actually revoking trust (just called loadAll which re-trusted the same fingerprint). Now calls `trustService.revoke(signatureHash)` before reloading. Added `signatureHash` field to Installed model.
  - **Uninstall fix**: Removed the `resolveActivity()` guard (returns null on Android 11+ due to package visibility — caused the system dialog to not show). Added `<queries>` block in manifest for ACTION_DELETE + scheme=package. Catches `ActivityNotFoundException` → fallback to app details.
  - **Extensions UI**: Filters button at top (NO default search bar — revealed on tap via AnimatedVisibility). Section cards with clearer separation + tonalElevation + 6dp row spacing. Removed duplicate name in trusted row. Long-press enters reorder mode (combinedClickable).
  - **Search source picker**: "Pick a source" (was "Select source"). Name-only rows (removed language). Selected source: primaryContainer background + plain checkmark (no circular background). Unselected: subtle surfaceVariant.
  - **Search auto-select**: SearchViewModel init auto-selects the top trusted source when none is selected (per user spec). ExtensionError state shows the actual error message (source name + reason) instead of the generic tsundere error. Catches Throwable (not Exception).
  - **Search collapse fix**: Header now collapses when the grid scrolls (was only collapsing on verticalScroll — gridState wasn't checked).
- **Phase 5B — Details page complete UI overhaul**: Rebuilt to match the old project's design exactly. DetailBanner (360dp blurred cover + gradient + 3 action buttons + cover thumbnail + title + meta row). GenresRow (horizontal scrollable chips). SynopsisSection (collapsible). InfoSection (key/value table). ScrollBlurOverlay. 3 top buttons: 40dp black-40%-alpha circles, 22dp white icons. Added material-icons-extended dep. Note: episodes section + source switching + resolver come in a later step (needs UnifiedAnime + provider infrastructure).
- **DESIGN-LANGUAGE.md created**: Fresh start (old one was deleted). 2 confirmed rules: §2.1 Collapsing Header (shrinks on scroll), §2.2 Scroll Blur Overlay. §2.3 Hide-on-Scroll Top Bar (Search specific). Future rules listed as pending.
- **CORE_RULES §27 added**: Tool Failure Recovery (stop after 5 consecutive failures of the same tool — don't hammer, the environment self-recovers).
- **Lessons logged**: resolveActivity returns null on Android 11+ for ACTION_DELETE (package visibility), untrust must actually revoke the fingerprint (not just reload), extension icons need appInfo.loadIcon + Coil AsyncImage(model=drawable).

## Session web-3a43f99b (sixth pass) — CRITICAL Injekt fix + Details episodes + source picker icons
- **CRITICAL FIX — Injekt registration for extension compat**: Extensions crashed with "No registered instance on Factory for type class eu.kanade.tachiyomi.network.NetworkHelper" because Injekt wasn't configured. Aniyomi extensions use Injekt (a service locator) to resolve NetworkHelper, Application, Context, and Json. `AnimeHttpSource.network` is `by injectLazy()` — the first HTTP request triggers `Injekt.get<NetworkHelper>()`. Without registration, the extension crashes. Fixed by adding Injekt registration in `AnikutaApp.onCreate()` (before Koin): `ExtensionAppHolder.init(this)`, `Injekt.addSingleton(Application)`, `Injekt.addSingleton(Context)`, `Injekt.addSingleton(NetworkHelper, NetworkHelper(this))`, `Injekt.addSingletonFactory(Json)`. Also added `kotlinx-serialization-json` dep to `:app`. The source-api module already had Injekt as `api()` dep + AnimeHttpSource already used `injectLazy` + NetworkHelper was already a class — only the registration was missing.
- **Details episodes section**: Added "Episodes" heading with source selector pill on the right ("No source" + dropdown arrow). Below: centered "Episode list is not implemented yet" placeholder with HourglassEmpty icon + helper text. Per user spec: "at least show the episodes heading + source selection option + not implemented placeholder."
- **Details three-dot menu position fix**: Moved DropdownMenu from a TopEnd-aligned overlay (was showing at bottom-left) INTO the banner's action row, wrapped in a Box with the MoreHoriz button. Now the menu appears anchored to the button (proper Compose DropdownMenu behavior).
- **Search source picker icons**: Added `sourceIcons: StateFlow<Map<Long, Drawable>>` to SearchViewModel — maps each source ID to its parent extension's icon. ExtensionSourcePickerSheet now renders a 32dp extension icon on the left side of each source row (via Coil AsyncImage).
- **Lessons logged**: Injekt registration must happen in App.onCreate() before any extension loads (injectLazy is deferred but must resolve by first HTTP call). DropdownMenu must be wrapped in a Box with its anchor button (not overlaid at a fixed position).

## Session web-3a43f99b (seventh pass) — Phase 5B Details episodes + source selection + resolver → watch
- **DetailsViewModel rewritten** — manages the full episode → watch flow:
  - Source linking (persisted per-anilist-id in PreferenceStore as `"sourceId:animeUrl"`).
  - `fetchEpisodes()` — fetches from linked source via `fetchEpisodeList().awaitSingle()`.
  - `searchSource()` — manual search of a single source by title.
  - `resolveEpisode()` — resolves videos via `VideoResolver`.
  - Reactive state: `availableSources`, `linkedSource`, `episodeState`, `manualSearchState`, `resolverState`.
- **DetailsScreen rewritten** — wires EpisodesSection to VM state. Source selector pill shows linked source name. EpisodesSection states: Idle (placeholder), Loading, Empty, Error (with "Try another source" button), Loaded (episode rows with number badge + title, descending per D-056, unlink button at bottom). Episode tap → resolveEpisode() → ResolverSheet.
- **ManualSearchSheet (new)** — bottom sheet for source selection. Header "Link Source" + close. Horizontal source picker (chips). Search field (pre-filled with anime title). Results list (SAnime candidates with thumbnail + title). Tap a result → linkSource() → episodes fetch.
- **ResolverSheet (new)** — bottom sheet for video selection. Header "Pick a video" + close. States: Loading, Error, Success (video list with quality + Direct/Stream label). Tap a video → onNavigateToWatch(videoUrl, title, quality).
- **WatchKey (new, temporary)** — carries videoUrl + animeTitle + quality. Phase 5c will replace with a proper `:feature:watch` module + WatchRequest.
- **Navigation wired** — DetailsScreen.onNavigateToWatch → backstack.add(WatchKey). MainActivity handles WatchKey → placeholder screen (Phase 5c will replace with the actual MPV player).
- **Architecture note**: This is the temporary Phase 5B implementation. Uses AniListAnime for metadata + a simple source_link string in PreferenceStore. Phase 5d will migrate to UnifiedAnime + ContentUID + ExternalReference. The source linking is per-anilist-id — when the user opens the same anime again, the link is restored + episodes auto-fetch.
- **Dependencies added to :feature:anime-details:impl**: `:core:preferences`, `:core:source-api`, `:core:video-resolver`, `:data:extension`, `rxjava` (for Observable.awaitSingle).
- **Lessons logged**: awaitSingle import from `eu.kanade.tachiyomi.util`, when exhaustiveness on sealed interfaces (must handle all branches or add else).

## Session web-3a43f99b (eighth pass) — NetworkOnMainThreadException + OkHttp binary compat + error logging + Phase 5c plan
- **CRITICAL FIX — NetworkOnMainThreadException in VideoResolver**: `fetchVideoList().awaitSingle()` was running network IO on the main thread (the `flow {}` builder runs on the collecting scope's dispatcher, which is `Dispatchers.Main` from `viewModelScope`). Fixed by wrapping in `withContext(Dispatchers.IO)` + `.flowOn(Dispatchers.IO)` on the entire flow. Also catches `Throwable` (not `Exception`) for binary-incompat errors.
- **CRITICAL FIX — OkHttp binary compat**: App used OkHttp 4.12.0 but Aniyomi extensions compile against 5.0.0-alpha.14. The Kotlin metadata hash for `CacheControl.Builder.maxAge()` changed → "No virtual method maxAge-LRDsOJo(J)..." at runtime. Fixed by updating OkHttp to 5.0.0-alpha.14 in `libs.versions.toml`.
- **Better error messages + logging**: All error states now include the exception type (`"${e::class.java.simpleName}: ${e.message}"`). Logger.e calls include the source name + full error context. Applied to: VideoResolver, DetailsViewModel (fetchEpisodes, searchSource, resolveEpisode), SearchViewModel (loadExtensionPopular, searchExtension).
- **Phase 5c Watch Screen Plan** written (`20-phase5c-watch-plan.md`): Two view modes (fullscreen landscape + minimized portrait), file structure (8 files split from old 2386-LOC monolith), player lifecycle (create → load → play → resume → save → destroy), WatchRequest upgrade, module structure (`:feature:watch` api/impl split), implementation order (9 steps).
- **Lessons logged**: awaitSingle doesn't move to IO thread, OkHttp version must match extensions, error messages should include exception type, some extensions override getSearchAnime (expected, not a crash).

## Session web-3a43f99b (ninth pass) — Phase 5c Watch screen (initial MPV player)
- **New module: `:feature:watch`** (api/impl split). WatchKey carries videoUrl + animeTitle + quality + episodeUrl + episodeNumber + episodeTitle.
- **WatchScreen** — plays video via MPV AndroidView:
  - Single `AndroidView(AnikutaMPVView)` — never recreated on mode switches (ADR-025).
  - Two modes: MINIMIZED (portrait, 16:9 player) + FULLSCREEN (landscape, edge-to-edge).
  - Controls overlay: play/pause, seek bar (Slider), time display, back button, quality label, anime title.
  - Auto-hide controls after 4s (fullscreen) / 5s (minimized). Tap to toggle.
  - BackHandler: fullscreen → minimized; minimized → exit.
  - Keep screen on while active. Immersive mode (hide system bars) in fullscreen.
  - Buffering spinner + error display (from MPV efEvent).
  - MPV lifecycle: `PlayerInitializer.initialize` → `MPVLib.command(["loadfile", url])` → auto-play → destroy on dispose.
  - Correct `MPVLib.EventObserver` + `LogObserver` interface method signatures (non-nullable params: `event`, `eventProperty`, `efEvent`, `logMessage`).
- **Navigation wired**: Deleted temporary WatchKey from `:feature:anime-details:api`. MainActivity uses `:feature:watch:api.WatchKey` → `WatchScreen`.
- **2 CI iterations to green**: (1) api module missing deps, (2) MPVLib observer method signatures (non-nullable params).
- **What's deferred** (later iterations): episode list in minimized mode, speed/quality/track sheets, resume position (WatchProgressStore wiring), subtitle/audio track loading.
- **Lessons logged**: MPVLib observer interfaces use non-nullable params (String, not String?), cross-module resource lookup via `resources.getIdentifier`.

## Session web-3a43f99b (tenth pass) — All 4 tasks: episode fix + APK signing + WatchScreen rebuild
- **TASK 1 — Episode loading fix**: Root cause was missing `getAnimeDetails(sAnime)` call before `getEpisodeList(sAnime)`. The search result's `sAnime.url` is a relative URL without leading "/" (e.g. "mushoku-tensei-..."). The default `episodeListRequest` builds `baseUrl + anime.url` = "https://anikototv.to" + "mushoku-tensei-..." = "https://anikototv.tomushoku-tensei-..." (missing "/" → UnknownHostException). `getAnimeDetails` enriches the SAnime with the correct URL format. Ported from old project's `ExtensionDetailsProvider.enrichAnimeDetails`.
- **TASK 4 — APK signing**: Generated `anikuta-debug.keystore` (RSA 2048, 10000 days). Configured `signingConfigs.anikutaDebug` in `app/build.gradle.kts`. Debug build type uses the fixed signing config. Keystore committed to repo (force-added — was gitignored by `*.keystore` rule). User can now update the app without uninstalling.
- **TASK 2+3 — WatchScreen rebuild**: Complete rewrite with minimized + fullscreen modes matching old project:
  - **Minimized mode**: Floating pill top bar (back + "ANI-KUTA" title, collapses on scroll via `animateDpAsState`). 16:9 player with `RoundedCornerShape(14dp)` + 6dp horizontal padding (NOT edge-to-edge). Minimized controls (time, fullscreen button, play/pause, seek bar). Scrollable LazyColumn: episode description card + episode list card (header + count badge + episode rows with number badges, current episode highlighted with primary border + tint).
  - **Fullscreen mode**: Edge-to-edge black player, landscape orientation (`SENSOR_LANDSCAPE`). Controls overlay with gradient scrim. Top bar (back, title, episode info, quality badge). Center (skip back -10s, play/pause 56dp, skip forward +10s). Bottom (seek bar + time + fullscreen exit). Auto-hide after 4s. Fade animation (200ms). System bars hidden (immersive mode).
  - `WatchKey` updated to carry `episodeUrl`, `episodeNumber`, `episodeTitle`, `episodeListSerialized` (pipe-delimited `url|episodeNumber|name` per line). `parseEpisodeList()` deserializes into `SimpleEpisode` list.
  - `DetailsScreen` updated to pass full episode info + serialized episode list via `onNavigateToWatch`.
  - Single shared `PlayerSurface` (AndroidView) — never recreated on mode switches. Parent removal safety.
- **Lessons logged**: `getAnimeDetails` must be called before `getEpisodeList` (URL enrichment).

## Session web-3a43f99b (eleventh pass) — Phase 5c Player Overhaul (Animiru reference + 5 critical fixes + sheet ports)
- **Animiru repo cloned + documented** (sub-agents ANIMIRU-CLONE + ANIMIRU-ANALYSIS). Repo at `REFERENCES/animiru/ANIMIRU/` (depth=1 clone of `https://github.com/Quickdesh/Animiru.git`). 11 documentation files (8,101 lines) at `REFERENCES/animiru/documentation/` covering: overview, player architecture, MPV initialization, player controls, player sheets, video resolution, subtitle management, extension system, player settings, key takeaways. NO code copied into the app — read-only architectural reference (D-065).
- **CRITICAL FIX 1 — Video playback (audio but no video)**: ROOT CAUSE: `AnikutaMPVView.initOptions()` was an empty stub ("No custom init options needed") — `setVo("gpu")` was never called → MPV had no video output configured. Ported the FULL `initOptions()` from the old project (D-061): `setVo("gpu")` (or `"gpu-next"` if `gpuNext` pref), `profile=fast`, `hwdec=auto` (NOT `auto-copy` — see lesson), `msg-level=all=warn`, `keep-open=true`, `input-default-bindings=true`, `ytdl=no`, `tls-verify=yes`, `tls-ca-file=<cacert.pem>`, `demuxer-max-bytes=256MB`, `demuxer-max-back-bytes=64MB`, `vd-lavc-film-grain=cpu` (mpv issue #14651 workaround), `speed`, `alang`, `volume-max`, plus 12 subtitle prefs via `applySubtitlePreferencesInit()`. Also moved `sub-ass-force-margins` + `sub-use-margins` from runtime `setPropertyString` to init-time `setOptionString` in `PlayerInitializer.initialize()` BEFORE `view.initialize()` (required for the render pipeline).
- **CRITICAL FIX 2 — Top padding bug**: ROOT CAUSE: `WatchScreen`'s `DisposableEffect(playerMode)` called `WindowCompat.setDecorFitsSystemWindows(window, true)` in the minimized branch — this conflicted with the app-wide `enableEdgeToEdge()` (sets `false` in `MainActivity.onCreate`). Empty `onDispose { }` left `setDecorFitsSystemWindows=true` on the Activity after the user navigated back from the player → framework auto-padded content view for the status bar AND Compose `statusBarsPadding()` also applied the inset → DOUBLE PADDING on Browse/Library/Search/More. Restart fixed it because `onCreate` re-runs `enableEdgeToEdge()`. Fix (D-062): (a) removed `setDecorFitsSystemWindows(window, true)` from the minimized branch — only `setDecorFitsSystemWindows(false)` in the fullscreen branch (matches old project, which NEVER sets it `true` in minimized); (b) populated the empty `onDispose { }` to restore the app-wide edge-to-edge defaults on screen exit: `setDecorFitsSystemWindows(window, false)`, `controller.show(systemBars())`, `requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED`. Root cause traced by sub-agent RESEARCH-PADDING-BUG.
- **CRITICAL FIX 3 — "Loading failed" overlay stuck**: ROOT CAUSE: `PlayerObserver` didn't clear error state on `FILE_LOADED` — the user was stuck on the error overlay even after a successful quality switch (the new file loaded but the old error stayed visible). Fix: `onEvent(FILE_LOADED)` now calls `updateError(null)` + `updateLoadingState(READY)` + loads subtitle/audio tracks. (Did NOT clear error in `END_FILE` — that fires normally on quality switch; see lesson.)
- **QualitySheet ported (3-tier)**: Replaced `QualitySheetPlaceholder` with the full old-project accordion. Server list (one expanded at a time), each server exposes a FlowRow of quality chips per `ResolverAudioVersion`, tap a chip → re-loadfile with that video's URL + headers. Created data classes `ResolverServer` / `ResolverAudioVersion` / `ResolverVideo` (matching the old project's 3-tier hierarchy — Server → AudioVersion → Video). `DetailsViewModel.resolveEpisode` now also calls `resolveStructured` to populate the 3-tier result.
- **ResolvedVideosRegistry (in-memory singleton, D-063)**: Solves the "WatchKey can't carry `List<ResolverServer>` through Nav3 serialization" problem. `DetailsViewModel.resolveEpisode` stores the structured resolver result in the registry (keyed by episode URL or request ID); `WatchScreen` reads from the registry by key to populate `QualitySheet`. Cleared on Watch exit. Trade-off: lost on process death (acceptable — user re-resolves).
- **SubtitleSettingsSheet ported**: Sticky header + 3 sections (Typography / Colors / Position & Misc) + `NumericEntrySheet` (custom keypad for integer entries like font size, border, position, delay, shadow offset) + `ColorPickerSheet` (preset swatches + RGBA sliders for text/border/background colors). All 12 subtitle prefs added to `PlayerPreferences`: `subtitleFont`, `subtitleFontSize`, `subtitleFontScale`, `subtitleBorderSize`, `boldSubtitles`, `italicSubtitles`, `textColorSubtitles`, `borderColorSubtitles`, `backgroundColorSubtitles`, `subtitlePosition`, `subtitleShadowOffset`, `overrideSubsASS`, `subtitlesDelay`. `AnikutaMPVView.applySubtitlePreferences()` uses `setPropertyInt` / `setPropertyDouble` for numerics (NOT `setPropertyString` — see lesson).
- **SubtitleSettingsSheet design (D-064)**: Uses non-reactive `PlayerPreferences` (existing thin SharedPreferences wrapper) + local `mutableStateOf` per row — NOT the old project's reactive `Preference<T>` Flow API. Simpler, equally responsive, avoids porting the whole `PreferenceStore` + `Preference<T>` abstraction. Trade-off: changes from OUTSIDE the sheet won't auto-propagate (moot — no other screen edits subtitle prefs).
- **SubtitleTracksSheet wired**: `onOpenSettings` callback now swaps the bottom-sheet content from `SubtitleTracksSheet` (track list) to `SubtitleSettingsSheet` (styling). No nav stack push — same sheet, content swap with animation.
- **PlayerInitializer simplified**: Removed `cache=yes`, `hwdec=auto-copy`, `hwdec-codecs`, `sub-ass-force-margins` from the generated `mpv.conf` — these are now set via `setOptionString` in `initOptions()` (init-time, more reliable for the render pipeline). The conf now contains only: `alang`, `sub-font`/`sub-font-size`/`sub-color`/`sub-border-color`/`sub-pos` (with concrete values, NOT template variables), `tls-verify=yes`, `ytdl=no`. Matches the old project's DEFAULT_MPV_CONF shape.
- **configChanges uiMode added**: `AndroidManifest.xml` Activity now declares `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"`. Theme toggle (light/dark/AMOLED) no longer recreates the Activity — matches the old project's manifest. Previously, the whole app restarted on theme change.
- **Sub-agents used this pass**: ANIMIRU-CLONE, ANIMIRU-ANALYSIS, RESEARCH-OLD-PLAYER, RESEARCH-OLD-SHEETS, RESEARCH-PADDING-BUG (research only — main agent applied all fixes).
- **Decisions logged**: D-061 (initOptions root cause), D-062 (top-padding root cause), D-063 (ResolvedVideosRegistry), D-064 (non-reactive subtitle prefs), D-065 (Animiru read-only reference).
- **Lessons logged**: empty `initOptions()` → no video; `setDecorFitsSystemWindows(true)` conflicts with `enableEdgeToEdge()`; empty `onDispose` leaks window state; `setPropertyString` unreliable for numeric MPV properties (use `setPropertyInt`/`setPropertyDouble`); `hwdec=auto-copy` fails on some devices (use `hwdec=auto`); don't clear error in `END_FILE` event (only on `FILE_LOADED`).
- **What's deferred** (next pass): episode switching inside WatchScreen (next/prev from minimized list — needs `PlayerStateHolder` fields: `episodeList`, `currentEpisodeIndex`, `isSwitchingEpisode`), resume position (wire `WatchProgressStore` — save on pause/exit, restore on loadfile), top-nav pill polish, device verification of all 5 fixes.

## Session web-f53f0459 — Phase 5c Player: Stuck-Loading Fix + Episode State + External Subtitles + Capture-Only Progress

### What was done
- **Stuck-loading regression FIXED (D-068, CRITICAL)**: Root cause — `setSwitching(true)` + `updateError()` suppression (intended to ignore old file's END_FILE during switch) left the player in a perpetual loading spinner when a switch ACTUALLY failed (no videos, resolve error, exception, 403, dead proxy). Fix: added `PlayerStateHolder.setSwitchingError(message)` — ALWAYS shows the error (never suppressed) AND clears the switching flag. Used in ALL explicit failure paths (retry catch, quality switch catch, episode switch no-videos/resolve-error/catch). Also added 30s `LaunchedEffect(isSwitching)` watchdog that calls `setSwitchingError("timeout")` if switching stays true for 30s — catches cases where efEvent is suppressed AND FILE_LOADED never fires.
- **Episode-switch state hoisted into PlayerStateHolder (D-069)**: Added 4 new StateFlows: `currentEpisodeUrl`, `currentEpisodeNumber`, `currentEpisodeTitle`, `currentResolvedVideosKey`. Seeded from WatchKey on init, updated on switch via `updateCurrentEpisode(...)`. Episode list highlight, "Currently playing episode N" card, and QualitySheet servers now read from the state holder (reactive) instead of the immutable WatchKey (which never changed after a switch). This fixes the bug where the highlight + "now playing" card + QualitySheet stayed on the OLD episode after a switch.
- **External subtitle/audio track loading re-added (D-070)**: Re-added `pendingSubtitleTracks`, `pendingAudioTracks`, `trackHeaders` fields to `PlayerObserver`. On `FILE_LOADED`, `loadExternalTracks()` sends `sub-add`/`audio-add` on `Dispatchers.IO` (each triggers HTTPS download), waits 300ms, then calls `loadTracksFromMpv()`. Host (`WatchScreen`) sets these fields before every `loadfile`: in `initMpv` (from initial picked video looked up in ResolvedVideosRegistry), in `onQualitySelected`, and in `onEpisodeSwitch`. Fixes the regression where external subtitles from extensions (AniKotoS, etc.) were silently dropped.
- **SubtitleTrackFormatter ported (D-071)**: Created `core/player/subtitles/SubtitleTrackFormatter.kt` with ISO 639 → English name mapping (50+ languages). `AnikutaMPVView.loadTracks()` now uses this formatter. Subtitle sheet shows "English" instead of "eng", "Japanese" instead of "jpn". Also discards ugly filenames (.vtt/.srt/.ass/.ssa, >20-char hashes). Improvement over the old project (which showed raw codes).
- **EpisodeSwitchingOverlay ported (D-073)**: Created `core/player/controls/EpisodeSwitchingOverlay.kt` — dark gradient + spinner + "Loading episode..." + optional title with pulse animation. Shown in both minimized + fullscreen modes when `isSwitching` is true. Covers the video during the switch so the user sees a clear loading state.
- **Speed setter bug fixed (D-073)**: `AnikutaMPVView.playbackSpeed` setter was `setPropertyInt("speed", value.toInt())` — truncated 1.5f→1, 0.5f→0. Changed to `setPropertyDouble("speed", value.toDouble())`.
- **Capture-only WatchProgressStore (D-072)**: Created `InMemoryWatchProgressStore` (writes to in-memory ConcurrentHashMap). Registered in Koin via `watchProgressModule`. `WatchScreen` saves progress every 10s + on dispose. NOT restored yet (Phase 5e when database is wired). Key format: `"$sourceId|$episodeUrl"`.
- **Headers-override bug fixed**: `trackHeaders` is now set on EVERY video change (quality switch AND episode switch), not just quality switch. Previously, after an episode switch, external subtitle downloads used stale/empty headers.
- **Dead Koin registration removed (D-074)**: Removed `singleOf(::PlayerStateHolder)` from `playerModule` — it was never injected (WatchScreen creates its own via `remember`). The old project never registered it in Koin either.
- **CORE_RULES updated**: §5 — added "Player lifecycle scaffolding is NOT boilerplate" + interface-with-one-impl exception for planned future swaps. §7 — added "Player screen carve-out (ADR-025)" for the single-MPV-instance pattern. §17 — added "When porting from REFERENCES/old-kuta/, rewrite all imports from `app.confused.anikuta` to `com.confused.anikuta`".
- **Doc backfill**: D-066 + D-067 (were in progress.md but missing from decisions.md) now backfilled.

### Key decisions
- D-068: Stuck-loading fix — `setSwitchingError()` + 30s watchdog
- D-069: Episode-switch state hoisted into PlayerStateHolder
- D-070: External subtitle/audio track loading re-added
- D-071: SubtitleTrackFormatter with ISO 639 mapping
- D-072: Capture-only InMemoryWatchProgressStore
- D-073: EpisodeSwitchingOverlay + speed setter bug fix
- D-074: Dead singleOf(::PlayerStateHolder) removed

### Files changed
- **Created**: `core/player/subtitles/SubtitleTrackFormatter.kt`, `core/player/controls/EpisodeSwitchingOverlay.kt`, `core/watch-progress/.../InMemoryWatchProgressStore.kt`, `core/watch-progress/.../WatchProgressModule.kt`
- **Modified**: `core/player/PlayerStateHolder.kt` (+episode state, +setSwitchingError), `core/player/PlayerObserver.kt` (+external tracks), `core/player/AnikutaMPVView.kt` (+formatter, +speed fix), `core/player/PlayerModule.kt` (-dead singleton), `feature/watch/impl/.../WatchScreen.kt` (+watchdog, +progress save, +overlay, +state hoisting, +external tracks wiring), `app/.../AnikutaApp.kt` (+watchProgressModule), `AGENT-CONTEXT/CORE_RULES.md` (§5, §7, §17 updates), `AGENT-CONTEXT/memory/decisions.md` (D-066–D-074), `AGENT-CONTEXT/memory/lessons-learned.md` (+4 lessons), `AGENT-CONTEXT/memory/changelog.md` (this entry)

### What's deferred (next pass)
- Wire the 7 dead fullscreen buttons (skip-next, audio, server, speed, more, PiP, rotate) — Phase D.
- 15s fatal-error watchdog (catches silently-stalled HLS) — Phase E.
- Auto-play-next, skip OP/ED, app-exit pause/resume — Phase E.
- Full doc-drift sweep (navigation.md section count, module-map, architecture, old-vs-new, workflow phase table) — Phase F.
- Device verification of all fixes.

## Session web-f53f0459 (continued) — Player Playback Fixes + Remaining Phases

### Critical playback fixes (from user log analysis)
- **TLS CA cert fix (D-075, CRITICAL)**: cacert.pem was 0 bytes → mbedTLS INVALID_FORMAT → ALL HTTPS streams failed. Deleted empty file + guarded tls-ca-file setting. Now mbedTLS falls back to system CA store.
- **Observer cleanup (D-076, CRITICAL)**: MPVLib observers never removed in onDispose → 4x event duplication. Hoisted observer refs + remove in onDispose.
- **Error handling rework (D-077)**: Replaced full-screen error dialog with non-intrusive PlayerErrorBanner (top bar). Added auto-retry (1.5s delay, once per video).
- **Spinner fix (D-078)**: Pause no longer shows loading spinner. Condition changed to `buffering || (loadingState == LOADING && duration == 0)`.
- **Episode switch title (D-078)**: Overlay now shows correct episode name during switching (updateCurrentEpisode called before resolve, not after).
- **Better error messages**: PlayerObserver captures TLS/SSL/HTTP/stream errors into httpError, appended to efEvent message.

### Remaining phases
- **Episode sanitization (D-079)**: Created EpisodeTitleParser in :core:common. Strips prefixes, detects hash/URL/code names, formats numbers (0 → "?"). EpisodeListRow + "Currently playing" card use it.
- **Speed control (D-080)**: Created SpeedSheet (presets + slider). Wired onSpeedClick in FullscreenControls. Applies via setPropertyDouble (fixes 1.5x truncation).
- **Skip-next (D-080)**: Wired onSkipForward → finds next episode + switches.
- **15s fatal-error watchdog (D-081)**: Catches HLS demuxer errors that don't trigger END_FILE. Shows "This server is not responding" after 15s stuck.
- **App-exit pause/resume (D-081)**: ON_STOP pauses playback, ON_START logs return.

### Files changed
- **Created**: `core/common/.../EpisodeTitleParser.kt`, `core/player/controls/SpeedSheet.kt`
- **Modified**: `core/player/AnikutaMPVView.kt` (TLS guard), `core/player/PlayerInitializer.kt` (skip 0-byte assets), `core/player/PlayerObserver.kt` (better error capture), `core/player/PlayerStateHolder.kt` (autoRetryAttempted + clearErrorForRetry), `core/player/controls/PlayerErrorOverlay.kt` (rewritten as PlayerErrorBanner), `core/player/controls/MinimizedControls.kt` (spinner fix + banner), `core/player/controls/FullscreenControls.kt` (spinner fix + banner), `feature/watch/impl/.../WatchScreen.kt` (observer cleanup, auto-retry, watchdog, app-exit pause, speed sheet, skip-next, episode sanitization)

### CI status
- Commits d26c304 + 1e95c9a (critical fixes): CI green
- Commit 97aaa33 (remaining phases): CI failed (LocalLifecycleOwner in DisposableEffect)
- Commit 061c17b (CI fix): CI GREEN ✅

### What's deferred
- Device verification of all fixes (user will test)
- Wiring remaining dead fullscreen buttons (audio, server, more, PiP, rotate) — Phase D continued
- Auto-play-next, skip OP/ED — Phase E continued
- Full doc-drift sweep — Phase F

## Session web-f53f0459 (continued) — User Feedback Round 2

### What was done (based on user device testing feedback)
- **Episode switch STOP (D-082, CRITICAL)**: `onEpisodeSwitch` now calls `MPVLib.command(arrayOf("stop"))` before resolve — old video stops instantly. User reported old episode kept playing while new one loaded.
- **Quality switch overlay fix (D-082, CRITICAL)**: Separated `isSwitching` (error suppression) from `isSwitchingEpisode` (overlay). Quality switches no longer show "Loading episode..." overlay — just the buffering spinner. Episode switches show the overlay.
- **Error banner persistence (D-083)**: Auto-retry no longer clears the error. Banner stays visible during retry. User reported banner "automatically disappears out of the blue."
- **Episode name sanitization (D-084)**: Lowered hash detection threshold from 25 to 15 chars. Added all-caps+digits detection (>10 chars). Episode numbers > 1000 now show "?" (catches timestamps like 1784388992).
- **Subtitle detection (D-085)**: Added 2s delayed track reload after FILE_LOADED (safety for slow-parsing HLS). Better logging (track count + warning when empty).
- **CORE_RULES §3 (D-086)**: Added test checklist rule — always provide a checklist after improvements.

### CI status
- Commit 6fab757: CI GREEN ✅

### What's deferred
- Seek buffering spinner (minor — user said "maybe a concerning thing maybe but maybe not")
- Remaining dead fullscreen buttons (audio, server, more, PiP, rotate)
- Auto-play-next, skip OP/ED
- Full doc-drift sweep

## Phase DL — Download System (`download-system-plan` branch, 41 commits)

> Consolidated entry. The download-system commits were never logged here as they
> were made — this closes that gap. Commit SHAs from `git log download-system-plan`.
> See `progress.md` → "Session — Download System" for the full narrative +
> `download-research/13-implementation-plan.md` for the status table.
>
> **Naming:** "Phase DL.0-DL.8" = the download-system phases (this section).
> Distinct from data-management "Phase D.1-D.5" (decisions D-144..D-147).

### DL.0 — Foundations
- `ba2141f` (DL-RESEARCH): 14 download-system research docs + dashboard webpage.
- `8cb8177` (DL-PLAN-FIX): plan v2 — 5 review rounds (REVIEW-1..5) + 72 MUST-FIX items applied.
- `5849e13` (DL-D0): download data models, preferences, `downloaded_episode` DB schema (re-keyed by `main_id` + `episode_key`; `.data.json` as source of truth; FORMAT folders video/images/text).
- `379f3a6` (DL-D0-FIX): REVIEW-D0 fixes.

### DL.1 — Engine + Storage
- `9b4c5d7` (DL-D1-1): download data models + preferences.
- `b8b5d7b` (DL-D1-2): progress tracker + cache + logger + `DownloadManager` interface.
- `65fe7a4`/`baa7628`/`cebafb0`/`c558beb` (DL-D1-FIX1-4): interface alignment, 30+ compile errors, TempDownloadCache API, FileOutputStream param.
- Delivered: `DefaultDownloadManager`, `HttpDownloader` (Range-resume + validation + HLS re-detection), `HlsDownloader` (pure Kotlin), `DownloadStorageProvider` (SAF + `.data.json` + same-title collision), `DownloadScanner`, `TempDownloadCache`, `DownloadLogger`.

### DL.2 — Orchestrator + AutoDownload + proxy-churn
- `6382dbe` (DL-D2-1): `DownloadOrchestrator`, `AutoDownloadEngine` (5-step: flatten → rank → applyFallbacks → pick → globalFallback), `ReResolver` types.
- `8ad6899`/`add3932`/`5bbb5be`/`e633d81`/`30ed37a` (DL-D2-FIX1-5): video-resolver dep, List<Int> Comparable, ResolverState serialization, ReResolver return-in-collect, missing import.
- ⚠️ **Proxy-churn re-resolve BUILT but NOT WIRED** (D-149, discrepancy D003): `HttpDownloader.reResolver = null` (`DownloadModule.kt:92`); no `downloadAppModule` adapter; interfaces signature-incompatible. Wiring deferred per user.

### DL.3-DL.8 — Queue + Service + Notifications + Settings UI + Downloads UI + Player + QoL (batch)
- `4298cb3` (DL-D3-D8-1): settings UI + downloads page + episode download control + player integration + QoL (single batch commit).
- `e29d616` (DL-D3-D8): wired download states into `DetailsViewModel` + verified all UI files.
- `a926b08`/`d5a8a00`/`e9d5592` (DL-D3-D8-FIX1-3): duplicate `downloadStates`, DownloadNavKeys package, duplicate imports.
- Delivered: `DownloadQueue` (Mutex + Semaphore, REVIEW-5 fixes M6/M11/M15/M31/M34/M36/M37/M38/M41/M42/M43), foreground `DownloadService` (NetworkCallback auto-pause/resume, `onTimeout` API 35+, `onTaskRemoved` restart), `DownloadNotificationManager` (2 channels), 7-section settings UI (drag-reorderable priority/quality/audio/server), downloads page (live queue + bulk actions + downloaded files page), episode download controls on details, player offline integration.

### Offline playback
- `d83915d` (DL-OFFLINE): offline playback + downloaded episode UI + Play/Delete menu.
- `de7c0bc` (DL-PLAYBACK-FIX): `content://` → `fd://` ParcelFileDescriptor conversion.
- `1f85339` (DL-CRITICAL-FIX3): MPV SIGABRT — 500ms surface-readiness delay for `fd://`.
- `66947ea`/`be4d1ea` (DL-REMAINING + FIX): subtitle naming, quality switcher, compile fix.

### Stability / migration / flow fixes
- `616a57f`/`1e34c33`/`5949521` (DL-CRASH-FIX 1-3): DB schema migration crash (drop+recreate download tables; `onOpen` migration; first-run setup dialog).
- `f30b290`/`336f264` (DL-UI-FIX 1-2): download button resolver sheet + 360p/HSUB defaults; `ResolvedVideo` type.
- `6717e02` (DL-FLOW-FIX): download flow logging + 360p/HSUB preference migration.
- `d60bd83`/`2c4c81f` (DL-DOWNLOAD-FIX 1-2): `effectiveLinkedSource` null in resolver sheet; moved to top-level scope.
- `ab86b26` (DL-CRASH-FIX3): Toast on main thread + localhost proxy connection failure handling.
- `8b9d1ab`/`cf01023` (DL-IMPROVE 1-2): downloaded episodes show as downloaded + `data.json` populated + hidden files; `downloaded_episode` DB insert + FK fields.
- `9812814`/`d6f0d21` (DL-IMPROVE 3 + FIX): stale data cache + `DownloadedFilesScreen` navigation + `WatchKey` metadata; `upsertEpisodeMetadataBatch`.
- `454fe86`/`c359aff` (DL-CRITICAL-FIX 1-2): offline playback crash + stale data + episodeUrl caching; `SQLiteException` — `data_cache_episode.episode_url` column missing.
- `234ea15` (METADATA-FIX-v2, branch HEAD): metadata disappearing + episode list + local subtitles.

### Status
- Download system substantially complete. Branch 41 commits ahead of `main`.
- ⚠️ Known gaps: (1) proxy-churn re-resolve not wired (D-149); (2) `127.0.0.1` guard missing in `HttpDownloader.kt:261`; (3) `video_uri` vs `video_url` column bug at `HttpDownloader.kt:271`; (4) `DownloadVideoPickerSheet` built but not wired; (5) outer retry loop not implemented (max 2 attempts, spec 6).
- Deferred: device testing, proxy-churn wiring, Nav3 decision, full doc-debt sweep.

> **Note:** changelog entries for D-087 → D-147 (Phase 5c player polish, Phase B
> auto-link, Phase C content identity, Phase D data-management caching) are
> MISSING from this file — that backfill is part of the deferred doc-debt sweep
> (discrepancy D005). Their decisions ARE recorded in `decisions.md`; their
> progress detail IS in `progress.md`. Only this changelog file lacks them.

## D-FIX-SUB — Downloaded-episode subtitle fixes (this session)

> 5 issues fixed in how downloaded episodes' subtitles are saved, named, and
> loaded offline. See `decisions.md` D-152 for full detail + the device testing
> checklist (`APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md`
> section C) for verification.

- **D-FIX-SUB-1 (CRITICAL): `subtitleUris` never populated.** `HttpDownloader.download()`
  returned `task.copy(videoUri=...)` but NOT `subtitleUris`; `publishVideoFile`
  returned only the video URI string. → offline playback had NO subtitles (files
  existed on disk but URIs were lost). **Fix:** `PublishResult` return type;
  HttpDownloader serializes subtitle URIs onto the task.
- **D-FIX-SUB-2: subtitle fetch sent no headers.** `downloadSubtitlesToCache`
  built the request with no headers → 403 on protected CDNs → subtitles silently
  skipped. **Fix:** `applyTrackHeaders()` (MPV comma-format) + UA fallback.
- **D-FIX-SUB-3: `DownloadTrack` had no `headers` field.** **Fix:** added
  `headers: String?`; `DownloadOrchestrator` passes the video's headers as a
  fallback for each subtitle/audio track.
- **D-FIX-SUB-4: subtitle naming was index-based.** Files were
  `.subtitle_E00001_0.srt`; offline picker showed "Subtitle 1". **Fix:**
  `.subtitle_E{00001}_{lang}_{index}.{ext}`; `MainActivity.extractSubtitleLangFromUri()`
  → picker shows "English" / "Japanese".
- **D-FIX-SUB-5: `DownloadScanner` set `subtitleUris = emptyList()` on reinstall.**
  → offline subtitles lost after reinstall. **Fix:** `findSubtitleUrisForEpisode()`
  re-discovers subtitle files (new + legacy naming) + repopulates the URIs.

### Status
- Sub-agent reviewed (SUB-REVIEW): COMPILES. Reviewer caught a header-format
  logic bug (JSON vs MPV-comma) — fixed before push.
- Awaiting device verification (checklist section C).

## Session — Swipe background / Calendar toggle / Notifications settings UI / CI fix

### Swipe-to-reveal background (D-153)
- **Bug:** the reveal background (tinted Surface + icon) in `DetailsScreen.EpisodeRow` + `HistoryScreen.HistoryRow` was completely invisible. ROOT CAUSE: it used `Modifier.fillMaxSize()` inside a wrap-content-height `Box` — Compose resolves `fillMaxSize` to **0 height** when the incoming max-height constraint is unbounded. The previous session's `matchParentSize → fillMaxSize` "fix" (commit `db26c47`) was the regression (based on the false theory that matchParentSize fails for siblings).
- **Fix:** `matchParentSize()` (BoxScope — designed for siblings; sizes to the card's footprint after the card is measured). Always compose the background; drive visibility via `graphicsLayer { alpha }` fading in linearly with swipe progress. Bumped tint 0.15 → 0.18.

### Calendar toggle (D-154)
- **Bug:** "can't click the calendar button." ROOT CAUSE: `ScheduleListContent` emitted the List/Calendar toggle `Row` + the list/calendar content as **bare siblings** into the parent `Box`. A Box stacks later children on top → the `fillMaxSize` list drew ON TOP of the toggle, hiding it.
- **Fix:** wrap toggle + content in a `Column`. Also: auto-fetch schedule once on first open if DB empty; calendar `verticalScroll`; empty-state hint; gate the Updates-driven `ScrollBlurOverlay` to the Updates tab (it read the Updates `listState` on the Schedule tab → stale scrim).

### Notifications settings UI (D-155 — Phase NOTIF UI completion)
- `NotificationPreferences` (`:core:preferences`, new) — global master kill switch + default trigger/audio prefs. Reactive flows.
- `NotificationManager` now checks the global master toggle first (`:core:notifications` gains `:core:preferences` dep).
- `NotificationsSettingsScreen` + `NotificationsSettingsViewModel` (`:app`, new) — master toggle, "New anime defaults" (5 toggles via `SettingsGroupCard`), per-anime library list with Switch + detail bottom sheet (per-trigger + sub/dub). Notifications nav row in `SettingsScreen` + `NotificationsKey` in `MainActivity`. ViewModel via `viewModelOf` in `appModule`.

### CI false-green fix (D-156)
- Previous commits `db26c47`/`fd1a9a5` FAILED CI (`:app:compileDebugKotlin` — "Cannot access class 'DocumentFile'") but progress.md claimed "CI green". The `scanSubtitleFilesOnDisk` function uses `DocumentFile` directly in `:app`, but `:core:download` declares it as `implementation` (not transitively visible). **Fix:** added `implementation(libs.androidx.documentfile)` to `:app`.

### Branch cleanup
- `feature/watch-progress-history-updates` deleted (local + remote). It was fully merged into main (0 unique commits). Main verified green (run 31275021179, artifact `anikuta-apk` 53 MB). Only `main` remains.

### Status
- ✅ CI genuinely green (run 31275021179, commit 25e5637). Awaiting device verification of swipe background, calendar toggle, + notifications settings screen.
- Subtitles intentionally deferred per user (separate session).

## Session — Calendar UX polish + Notifications tri-state + dedicated Library page

### Calendar UX (D-157)
- **Toggle restyle:** List/Calendar toggle now matches the Updates | Schedule pill (container Surface + per-tab Surface, same colors). Added `List` + `CalendarMonth` icons (tinted with tab text color).
- **"Today" button:** in calendar view, the toggle pill shrinks left (weight 1f) and a "Today" button (`Today` icon + label) appears on the right. Tapping it animates the pager to the current-month page. Wired via a `scrollToTodayRequest: Int` counter observed by `ScheduleCalendarContent` (LaunchedEffect → `animateScrollToPage`).
- **Smooth height animation:** grid height now animates via `animateDpAsState` (spring, no-bouncy, medium-low stiffness) when the displayed month's week count changes (was an abrupt jump).

### Notifications tri-state (D-158)
- `TriggerState` enum (ON / SILENT / OFF) replaces boolean triggers — stored as INTEGER 0/1/2 (backward compatible with old 0/1 data; no migration needed).
- `AudioPref` enum (SUB / DUB / BOTH) replaces the two sub/dub booleans — derived from them (no schema change).
- `NotificationManager`: SILENT triggers post to a new low-importance channel (`anikuta_new_episodes_silent`, IMPORTANCE_LOW) with PRIORITY_LOW (no sound); ON uses the default channel.
- Reusable `SegmentedToggle` component (matches download-settings style).
- Adapting descriptions: e.g. "Notify for sub releases only" / "Notify for dub releases only" / "Notify for sub and dub releases"; "Notify when…" / "Notify silently when…" / "Don't notify when…".
- `NotificationConfig` + enums moved to `:core:common` (package `com.confused.anikuta.core.notifications`) to break the preferences↔notifications circular dep. `:core:preferences` now depends on `:core:common`.

### Master-off hide (D-159)
- The "New anime defaults" section smoothly collapses via `AnimatedVisibility` (fadeIn + expandVertically / fadeOut + shrinkVertically) when the master toggle is off. Wrapped in a `Column` inside the LazyColumn item (LazyItemScope has no ColumnScope).

### Dedicated Library page (D-160)
- Per-anime config moved to `NotificationsLibraryScreen` (reached via a "Library" nav row at the bottom of the settings screen).
- Category filter chips (LazyRow): "All" + every `LibraryCategory`. Selecting filters via `ContentRepository.getMainIdsByCategory(id)`.
- Per-anime list: cover + title + Switch (enable/disable) + chevron → opens advanced-config bottom sheet (tri-state triggers + audio, adapting descriptions).
- `NotificationsLibraryViewModel` (viewModelOf in appModule). `NotificationsLibraryKey` wired in MainActivity.

### Status
- ✅ CI green (run 31277015651, commit b55da53, artifact 53 MB). 3 iterations (enum companion `this` → instance methods; `getInt` default Long→Int; `var by` setValue import + AnimatedVisibility ColumnScope).
- Swipe + calendar toggle confirmed working on device (user feedback this session). Calendar UX + notifications tri-state + library page awaiting device verification.

## Session — Notifications crash fix (SharedPreferences Boolean→Int migration, D-161)

### Crash
- `ClassCastException: java.lang.Boolean cannot be cast to java.lang.Integer` ~1s after opening the Notifications settings page. The UI rendered for a split second (initial StateFlow placeholder), then crashed when the `defaults` flow collected and called `prefs.getInt` on keys that held Booleans from the previous build (D-158 changed the trigger defaults from Boolean to Int but kept the same SharedPreferences keys).

### Fix
- One-time migration in `NotificationPreferences.init`: for each of the 3 trigger keys, `try { store.getInt(key, 0) }`; on `ClassCastException`, read the legacy Boolean, map `true→1 (ON)` / `false→0 (OFF)`, write as Int. Idempotent (absent / already-Int keys untouched). Runs at singleton construction before any flow collection; `SharedPreferences.apply()` updates the in-memory cache synchronously so there's no race.

### Status
- ✅ CI green (run 31277812616, commit 87c4d1e, artifact 53 MB). Calendar UX (toggle/icons/today/height-anim) confirmed working on device. Awaiting verification that the Notifications page no longer crashes.

## Session — Debug Bubble planning + dashboard page (feature/debug-bubble branch)

### Branch
- Created `feature/debug-bubble` from main. All work happens here; main is untouched.
- Added `feature/debug-bubble` to build-apk.yml push triggers (temporary — verifies every push builds).

### CI verification
- Feature branch builds cleanly: run 31278277860 (4e05152), artifact 53 MB. ✅
- Run 31278786368 (13ca829, with plan + dashboard) also green. ✅

### Plan (PLAN.md — 753 lines)
- Full implementation plan for the Debug Bubble: a floating, draggable debug overlay on every screen. Tap to expand a panel with 5 tabs (Current Screen, Database, Console, Network, App Info). Debug-only (`debugImplementation`), trivially removable (~5 edits), zero impact on app code when off.
- Architecture: two-module split (`:core:debug-api` always available + `:feature:debug-bubble` debug-only). Integration: one `DebugBubble()` call in AppRoot. Data sources: hoisted CompositionLocal state, SqlDriver injection, LogAppender interface, OkHttp interceptor.
- 8 implementation phases (DB-1..DB-8, 14-21h total).

### Sub-agent review (D-162)
- A general-purpose sub-agent reviewed the plan. Found 5 CRITICAL + 8 IMPORTANT issues — all verified real by the main agent and incorporated into the plan:
  - C1: CompositionLocal siblings (bubble can't read screen-provided context) → hoisted state.
  - C2/C3/C4: module dependency direction (debugImplementation can't be imported by release-code modules) → two-module split + LogAppender interface + debug-only source set.
  - C5: WatchScreen carve-out → auto-hide + rotation/IME.
  - I1-I8: network interceptor placement, SqlDriver injection, SQL injection, BLOB handling, VM leak cleanup, Animatable consistency, honest removal edit list.
- Main agent's assessment: no false positives. The review was thorough and technically accurate.

### Dashboard
- New page `/debug-bubble` with visual mockup, goals/non-goals, two-module architecture, integration point, removal strategy, bubble/panel specs, 5 tab cards, data sources, sub-agent review summary, implementation phases, open questions.
- New "debug" (bug) icon in Sidebar nav.
- Deployed from `feature/debug-bubble` (Pages source branch + environment branch policy updated to allow the feature branch).
- Live: https://testplay-byte.github.io/ANI-KUTA/debug-bubble/

### Status
- ✅ Planning complete + sub-agent reviewed + dashboard deployed. No app code changed. Implementation (DB-1..DB-8) begins after user reviews + approves the plan.

## Session — Debug Bubble implementation (DB-1..DB-8, feature/debug-bubble branch)

### Branch
- All work on `feature/debug-bubble` (main untouched).

### Implementation (8 phases, each CI-green)
- **DB-1:** module scaffold (`:core:debug-api` + `:feature:debug-bubble`) + draggable squircle bubble + AppRoot integration (hoisted DebugContext state + CompositionLocalProvider wrapping nav content + bubble). Sub-agent review caught 3 issues (Compose runtime dep, offset import, initial-position flash) — all fixed.
- **DB-2:** panel shell (AnimatedVisibility, scrim, expand-direction by bubble position, tab strip) + Current Screen tab (reads LocalDebugContext). Sub-agent review caught 2 issues (status/nav bar insets, drag interception) — both fixed.
- **DB-3:** Database tab. DebugDatabaseBrowser opens a separate read-only SQLiteDatabase. Table chips + scrollable grid + parameterized search + BLOB handling.
- **DB-4:** Console tab. DebugLogBuffer (10,000-entry ring buffer) implements LogAppender (moved to :core:common). Logger.setAppender wired in :app/src/debug. Tag/level filters, clear, auto-scroll.
- **DB-5:** Network tab. DebugNetworkStats OkHttp interceptor (request/byte/error counts, status histogram, recent requests). Wraps both default + download clients via wrapDebugOkHttp.
- **DB-6:** App Info tab. DebugBuildInfo (BuildConfig values via debug/release source-set Koin modules). Build + project + memory sections. All 5 tabs now live (no placeholders).
- **DB-7:** Screen opt-ins. Details, Browse, Watch, Downloads provide LocalDebugContext (screenName, screenData, relevantTables). DisposableEffect clears on exit (prevents VM leak).
- **DB-8:** docs update (this entry + D-163 decision + progress.md).

### CI
- All 8 phases CI green on feature/debug-bubble. Final: run 31283553038, commit 477a256, artifact 54 MB.

### Status
- ✅ All 8 phases implemented + CI green. Awaiting device verification.
- The bubble is visible by default in debug builds (per user). Release builds contain zero debug-bubble code.
- Future: "Show debug bubble" Settings toggle (deferred — bubble is visible-by-default), automated testing via the bubble (future phase per user).

## Session — Dashboard DB Viewer page (feature/debug-bubble branch)

### DB Viewer — client-side JSON inspector
- New `/db-viewer` page on the dashboard: upload a `DB.json` export (drag-and-drop or file picker) and browse every table as a searchable, paginated grid. 100% client-side — no server upload, no API calls.
- **Features (built across multiple commits, final state):**
  - Column drag-to-resize (drag the right border of any column header). Uses `tableLayout: "fixed"` + explicit `width: sum(col widths)` on the `<table>` so `<col>` widths are authoritative (content truncates instead of pushing columns wider).
  - Fullscreen mode (browser native + UI fullscreen — hides hero/sidebar, focuses the grid).
  - Collapsible table sidebar (260px ↔ 56px icon-only, persisted to localStorage).
  - Image fullscreen viewer (click any cover/poster/URL preview → full-screen overlay with fade-in).
  - Cell click popup (modal showing the full value of any cell — handles nulls, URLs, images, multi-line text).
  - Row-number popup (click any row number → modal showing all columns of that row in a two-column key/value layout).
  - Image auto-preview for columns matching `cover|poster|thumbnail` (portrait 40×56) or `image|url` (square 40×40).
  - Search across ALL columns with `<mark>` highlighting.
  - Pagination (50 rows/page).
  - "Try sample DB" button fetches the repo's `DB.json` for instant demo.

### Final polish round (commit 064a9c2)
- **Smart column widths via Canvas measurement** — every column's default width is now `max(measured_header_width + padding, content-aware_default)`. Added `measureHeaderText()` using a cached Canvas 2D context (400-weight 11.5px JetBrains Mono). Replaced the old hardcoded `shortCols`/`mediumCols` lists that gave fixed 100/250px widths (which truncated many headings). Now headings NEVER show truncation dots. Memoized via `useMemo` on `currentTable`.
- **cover_url / banner_url fix** — portrait image columns went from 90px → 200px; square image columns from 90px → 170px. Enough for the 40px preview + a meaningful URL slice, with the heading fully visible.
- **Dedicated row-number section** — column widened 56→72px, numbers bumped 11→16px bold (font-weight 700), header "#" bumped 10.5→15px bold, background changed to `canvas` (distinct from `surface-alt`), right border thickened 2→3px with an extra `boxShadow` for depth. Visually reads as a separate strip.
- **Removed** the unused `MAX_CELL_PREVIEW` constant and the "more"/"less" cell expand button (the cell click popup is sufficient).
- Verified locally with Agent Browser: all 13 headers in `anilist_detail` render with `scrollWidth === offsetWidth` (zero truncation); `cover_url` offsetWidth = 200px; row-number button computed style = `font-size: 16px, font-weight: 700, color: #1a1a1a`.

### Status
- ✅ Deployed to GitHub Pages (via `feature/debug-bubble` branch — Pages environment branch policy updated). Live at https://testplay-byte.github.io/ANI-KUTA/db-viewer/
- Commit `064a9c2` on `feature/debug-bubble`, pushed to origin.

## Session — Debug Bubble DB Activity tracker + sliding charts (feature/debug-bubble branch)

### DB Activity view — now tracks real DB writes (DB-9)
- **Problem:** The DB Activity view in the Network tab was a placeholder. It showed "No database updates tracked yet" and never registered any writes, even when the app was clearly writing to the database (e.g., opening an anime for the first time writes to `content`, `anilist_detail`, `anime_metadata_cache`, and `data_cache_episode`).
- **Root cause:** No DB write tracker existed. `DebugDatabaseBrowser` is read-only (opens a separate `SQLiteDatabase` with `OPEN_READONLY`).
- **Solution — `DebugDbStats` singleton** (`feature/debug-bubble/.../data/DebugDbStats.kt`, new file):
  - Mirrors `DebugNetworkStats`'s structure: atomic write counters (total/insert/update/delete/other), per-table counts (`mutableMapOf<String, Int>`), per-second time-series (`ArrayDeque<DbTimeSeriesBucket>`, 300-bucket cap = 5 min), recent-events ring buffer (`ArrayDeque<DbWriteEvent>`, 50-event cap).
  - `recordWrite(operation, table, sql)` — entry point; called from the SqlDriver wrapper.
  - `snapshot()` — returns an immutable `DbSnapshot`; calls `advanceToNow()` gap-fill (same as `DebugNetworkStats`) so the writes/sec chart slides forward even when the DB is idle.
  - `clear()` — resets all counters + buffers.
  - Thread-safe: atomic counters for the hot path; `synchronized(lock)` for the deque + map + ring buffer.
- **Solution — `DebugSqlDriverWrapper`** (`feature/debug-bubble/.../data/DebugSqlDriverWrapper.kt`, new file):
  - Uses Kotlin interface delegation (`by delegate`) to auto-forward all 7 `SqlDriver` methods to the underlying driver. Only `execute()` is overridden — reads (`executeQuery`) have zero overhead.
  - `parseAndRecord(sql)` — parses the operation (first keyword, uppercase) + table name (regex matching `INSERT INTO`, `INSERT OR REPLACE INTO`, `REPLACE INTO`, `UPDATE`, `DELETE FROM`) from the SQL string. If parsing fails, the event is still counted in the totals with an empty table name.
  - Regex: `^\s*(?:INSERT\s+(?:OR\s+\w+\s+)?INTO|REPLACE\s+INTO|UPDATE|DELETE\s+FROM)\s+["`\[]?(\w+)["`\]]?` (case-insensitive).
- **Wiring** (mirrors the proven `wrapDebugOkHttp` pattern):
  - `wrapDebugSqlDriver(driver: SqlDriver): SqlDriver` added to `app/src/debug/DebugInit.kt` (fetches `DebugDbStats` from Koin, wraps the driver). No-op identity stub in `app/src/release/DebugInit.kt`.
  - `DebugDbStats` registered as a singleton in `DebugBubbleModule.kt`.
  - `AnikutaApp.kt:199` — `single<SqlDriver> { wrapDebugSqlDriver(DatabaseDriverFactory(get()).create()) }`.
  - Koin lazy-resolution: the `single<SqlDriver>` factory runs lazily on first resolution (when `AnikutaDatabase(get())` is constructed, well after Koin starts), so `GlobalContext.get().get<DebugDbStats>()` succeeds.
- **NetworkTab DB Activity view** — replaced the placeholder with real content:
  - 4 stat cards: Writes (total), Ins (inserts), Upd (updates), Del (deletes).
  - Writes/sec chart (5 min, timestamp-based X-axis, slides forward every 2s).
  - Top tables breakdown (per-table write counts, tap to navigate to Database tab).
  - Recent events list (color-coded by operation: green=INSERT, blue=UPDATE, red=DELETE, orange=REPLACE; tap to navigate to the affected table in the Database tab).
- **Catches 100% of writes** from every repository, every ViewModel, every WorkManager job. Zero changes to any repository, `.sq` file, or release code path.

### Charts constantly slide forward (even with zero traffic)
- **Problem:** The two network charts (requests/sec + data usage) only updated when a network request arrived. With zero traffic, the chart was frozen — it didn't "move like time is going."
- **Root cause:** `DebugNetworkStats.recordTimeSeries()` was called ONLY from `intercept()` (on a successful request). `snapshot()` only pruned old buckets — it never appended zero-buckets for elapsed seconds with no traffic. The Canvas X-axis used bucket indices (not timestamps), so even if gaps existed, they'd be drawn at full density.
- **Fix 1 — `advanceToNow()` gap-fill** (in `DebugNetworkStats.snapshot()`):
  - After pruning old buckets, walks forward from `timeSeries.last().timestamp` to `now`, inserting zero-valued `TimeSeriesBucket(timestamp, 0, 0L)` entries for each elapsed second. Capped at 300 iterations (5 min).
  - If the deque is empty (no traffic yet), seeds a single "now" zero-bucket so the chart has a baseline.
  - Same method added to `DebugDbStats.snapshot()` for the writes/sec chart.
- **Fix 2 — timestamp-based Canvas X-axis** (in NetworkTab):
  - Changed from `x = i * stepX` (bucket index) to `x = (bucket.timestamp - windowStart) / windowSpan * width`.
  - `windowStart = System.currentTimeMillis() - 300_000` (5 min ago), `windowSpan = 300_000`.
  - `coerceIn(0f, w)` guards against clock skew.
  - This ensures zero-filled gaps render at the correct temporal position (stretched across the full 5-min window) instead of being compressed.
  - The 2-second `LaunchedEffect` auto-refresh loop now also polls `dbStats.snapshot()` alongside `stats.snapshot()`, so all three charts slide forward every 2 seconds.

### Hoisted viewMode to fix DB Activity minimize bug
- **Problem:** When the user selected "DB Activity" and then minimized the panel, the mini-window automatically switched back to the "Network" view. The DB Activity view had no minimized mode.
- **Root cause:** `viewMode` was `remember`-scoped to the `NetworkTab` composable (local state). `AnimatedVisibility` disposes the expanded NetworkTab on minimize → the `viewMode = "db"` state was lost → the fresh minimized NetworkTab initialized `viewMode` back to `"network"`.
- **Fix:** Hoisted `viewMode` to `DebugPanel` as `networkViewMode` (alongside `activeTab`). Passed it + `onViewModeChange` + `onViewInDb` callbacks to both NetworkTab call sites (expanded + minimized). The DB Activity view now survives the EXPANDED↔MINIMIZED transition.

### Status
- ✅ CI green (run 31339439293, commit 619a174, artifact `anikuta-apk` 54.1 MB).
- Code-reviewed by sub-agent (Task 2-d): no critical or important issues.
- Awaiting device verification: (1) open an anime for the first time → DB Activity should show writes to `content`, `anilist_detail`, `anime_metadata_cache`, `data_cache_episode`; (2) charts should slide forward every 2s even when idle; (3) selecting DB Activity + minimizing should keep the DB Activity view in the mini-window.

## Session — Debug Bubble: read tracking + export logs + filter toggle + dual-line chart (feature/debug-bubble branch)

### Read tracking (SELECT) — now tracks ALL DB operations
- **Problem:** The DB Activity view only tracked writes (INSERT/UPDATE/DELETE/REPLACE). The user wanted to see reads (SELECT) too — "it should detect the reads and it should also detect the writes and it should also detect the updates and deletes."
- **Solution:** `DebugSqlDriverWrapper` now overrides BOTH `execute()` (writes) AND `executeQuery<R>()` (reads). The `executeQuery` override parses SELECT statements via a new `selectTableRegex` that matches `SELECT ... FROM <table>` (including `SELECT DISTINCT`). Also handles `WITH ... SELECT` (CTEs).
- **DebugDbStats rewrite:**
  - New `totalReads: AtomicLong` + `readTableCounts: MutableMap<String, Int>`.
  - `DbWriteEvent` renamed to `DbEvent` with an `isRead: Boolean` flag.
  - `DbTimeSeriesBucket` now has `readCount: Int` + `writeCount: Int` (dual dimension).
  - `DbSnapshot` includes `totalReads`, `readTableCounts`.
  - `recordRead(table, sql)` method — mirrors `recordWrite`.
  - `recordTimeSeries(timestamp, isWrite)` — updates the right dimension.
  - `advanceToNow()` fills both `readCount=0` + `writeCount=0` for idle seconds.
  - Ring buffer increased 50 → 200 (reads are far more frequent than writes).
- **NetworkTab DB Activity UI:**
  - 5 stat cards: Reads (cyan), Writes (gold), Ins (green), Upd (blue), Del (red).
  - Dual-line chart: reads (cyan) + writes (gold) overlaid. `maxVal = maxOf(maxReads, maxWrites).coerceAtLeast(1)` so neither line is clipped. Peak labels show both dimensions.
  - Separate "Top tables (writes)" + "Top tables (reads)" sections.
  - Filter toggle (All / Reads / Writes) for the recent events list. Default = "Writes" (reads would flood the list).
  - In minimized mode: shows only writes (reads would flood the mini-window).
  - `DbEventRow` replaces `DbWriteEventRow` — cyan color for SELECT events; green/blue/red/orange for INSERT/UPDATE/DELETE/REPLACE.

### Export/download log buttons
- **DebugDbStats.exportAsText():** produces a human-readable text log with:
  - SUMMARY (total reads, total writes, INSERT/UPDATE/DELETE/other breakdown).
  - TABLE BREAKDOWN (writes) — per-table write counts, sorted descending.
  - TABLE BREAKDOWN (reads) — per-table read counts, sorted descending.
  - RECENT EVENTS (last 200, newest first) — timestamp, operation, table, truncated SQL.
- **DebugNetworkStats.exportAsText():** produces a human-readable text log with:
  - SUMMARY (total requests, bytes received/sent, errors).
  - STATUS CODES (2xx/3xx/4xx/5xx/err histogram).
  - CATEGORIES (Metadata/Video/Image/Other).
  - TOP SOURCES (per-host counts, sorted descending).
  - RECENT REQUESTS (last 50, newest first) — timestamp, method, category, status, latency, bytes, URL.
- **SAF (Storage Access Framework) export:**
  - Network view header: Download icon → `exportNetLauncher.launch("anikuta_network_<timestamp>.log")`.
  - DB Activity header: Download icon → `exportDbLauncher.launch("anikuta_db_activity_<timestamp>.log")`.
  - Uses `ActivityResultContracts.CreateDocument("text/plain")` — the user picks a file location via the system file picker. The log text is written on a background `Thread` (same pattern as DatabaseTab's JSON export).
  - The exported `.log` files are plain text, easily shareable (e.g., via messaging apps, email, or attached to a GitHub issue).

### Filter toggle + better formatting
- 3-way filter (All / Reads / Writes) for the DB Activity recent events list.
- Separate "Top tables (writes)" + "Top tables (reads)" sections so the user can see which tables are being read vs written.
- Color-coded events: cyan=SELECT, green=INSERT, blue=UPDATE, red=DELETE, orange=REPLACE.
- The filter toggle is hidden in minimized mode (the mini-window always shows writes only).

### Status
- ✅ CI run 31341636467 started (commit 1f65e5d). Awaiting green.
- Code-reviewed by sub-agent (Task 3-a): no critical or important issues. 6 minor issues (3 fixed: stale KDoc in DebugInit.kt, stale comment in DebugBubbleModule.kt, inaccurate comment in DebugSqlDriverWrapper.kt; 3 acceptable tradeoffs: indentation, raw Thread, ring buffer eviction).
- Awaiting device verification: (1) reads should now appear in the DB Activity view (cyan-colored SELECT events); (2) the export buttons should produce `.log` files via the system file picker; (3) the filter toggle should correctly filter events; (4) the dual-line chart should show both reads + writes.

## Session — feature/debug-bubble → main merge + branch cleanup

### Merge
- Fast-forward merged `feature/debug-bubble` (53 commits, 51 files, 8,706 insertions) into `main`.
- The merge was a clean fast-forward (no conflicts) because `main` hadn't moved since the feature branch was created. The only remote-side commit was `30b2cb5` (user uploaded `DB.json` via the GitHub web interface) — rebased locally before pushing.
- All debug bubble work (DB-1..DB-9, D-162..D-165) is now on `main`.

### CI cleanup
- Removed `feature/debug-bubble` from `build-apk.yml` push triggers (the branch is being deleted). Only `main` + tags trigger builds now.
- The `deploy-dashboard.yml` workflow already only triggers on `main` — no change needed. The dashboard changes (DB Viewer page + debug-bubble page) in the merge will trigger a fresh GitHub Pages deployment.

### Branch deletion
- Deleted the local `feature/debug-bubble` branch.
- Deleted the remote `origin/feature/debug-bubble` branch.

### Status
- ✅ `main` is at `0fcc850` (CI cleanup commit). Pushed to origin.
- CI verification in progress (Build APK + Deploy Dashboard workflows triggered by the push to main).
- The `feature/debug-bubble` branch is fully deleted.
- Next: a new AI agent will take over for database optimization work.

## Session — DB Optimization + Ratings UI + Continue Watching + Watch-Progress Fixes (feature/db-optimization-ratings-cw branch)

### Phase 1 — Database Optimization (D-166)
- Deleted dead `extensions.sq` + `metadata.sq` (zero Kotlin call sites). DROP TABLE IF EXISTS in onOpen migration.
- Enabled `PRAGMA foreign_keys = ON` (all ON DELETE CASCADE clauses now active).
- Dropped 6 redundant indexes (leftmost-column of composite UNIQUE/PK). Dropped via DROP INDEX IF EXISTS in onOpen.
- Added 8 missing indexes: continue-watching partial, completed_at, episode_update retention purge partial, notification_sent_at, library_item unique, anilist_detail.anilist_id, content(extension_id, anime_url). All via CREATE INDEX IF NOT EXISTS in onOpen.
- Dedupe DELETE before library_item UNIQUE index creation (hardens migration on existing installs).
- SQLite UPSERT NOT migrated (requires 3.24+; API 24-28 has 3.9-3.22). INSERT OR REPLACE kept.
- CHECK constraints NOT added (can't ALTER TABLE to add CHECK on existing installs). Deferred.

### Phase 1 — Bug Fixes (D-167, D-168, WP-B1)
- **Audio-variants fix (D-167):** Added `source_name` + `scanlator` columns to `data_cache_episode`. Enriched cache write now preserves extension's `ep.name` + `ep.scanlator` via lookup maps. Cache-read SEpisode reconstruction uses `sourceName ?: title`. Fixed offline-fallback `url = animeUrl` → `meta.episodeUrl ?: animeUrl`. Fixed Downloads→Watch scanlator handoff (was hardcoded `""`).
- **Extension trust fix (D-168):** Added per-package `isEnabled` flag to `AnimeExtension.Installed` (independent of signer-level trust). `loadAll()` filters `_sources` by `isEnabled`. `trustExtension()` also `enableExtension(pkgName)`. `untrustExtension()` also `disableExtension(pkgName)`. Switch toggle in ExtensionsSettingsScreen. Backward compat: seed enabled set with all trusted pkgNames on first launch.
- **WP-B1:** `setAutoMarkSuppressed` SQL now clears `completed_at` (was leaving stale data). Added INSERT-when-missing guard (was silent no-op if row didn't exist).

### Phase 2 — Watch-Progress Bug Fixes (D-169)
- **WP-B2:** `resetAutoMarkSuppressed` now called on every FILE_LOADED via `LaunchedEffect(loadingState)` in WatchScreen (was NEVER called → CF1 re-arm broken).
- **WP-B3:** Resume-seek — WatchScreen looks up saved position from `watchProgressStore` on initial FILE_LOADED + seeks via `MPVLib.command(seek, absolute)`. Only on initial load (`hasResumed` flag). Added `startPosition: Long = 0L` to `WatchKey`.
- **WP-B4:** Save old episode's progress at top of `onEpisodeSwitch` before `updateCurrentEpisode` overwrites state.
- **Progress bar:** `LinearProgressIndicator` at bottom of episode thumbnail in Details (like YouTube). Only when partially watched.

### Phase 3 — Continue Watching UI (D-170)
- `ContinueWatchingCarousel` at top of Browse — single horizontal `LazyRow`. Cover thumbnails (or first-letter placeholder), EP badges, progress bars, title.
- `BrowseViewModel.continueWatching` enriches `observeContinueWatching(10)` with content metadata via `ContentRepository`.
- Tap → `AnimeDetailsKey` (resume kicks in on play via WP-B3). TEMPORARY — easy to remove.

### Phase 4 — Ratings UI (D-170)
- Per-anime 10-star `StarRatingBar` on Details synopsis title row (right side). `DetailsViewModel.animeRating` reactive via `ratingStore.observeAnimeRating`. Each star = 10 points (0-100 backend). Tap same star → clear.
- Per-episode 10-star `WatchStarRatingBar` below "Currently playing episode" text in WatchScreen (MinimizedMode). Self-contained `koinInject` + local state.
- CI fix: moved rating state from `WatchScreen` into `MinimizedMode` (was unresolved reference — variables not in scope in child composable).

### Status
- ✅ Phase 1 CI green (run 31348314200).
- ✅ Phase 2 CI green (run 31348683710).
- ✅ Phase 3 CI green (run 31348903899).
- ⚠️ Phase 4 CI: first push failed (5 unresolved reference errors in WatchScreen.kt — rating state in wrong composable scope). Fix pushed (ca4a345). CI green (run 31349493109).
- Branch: `feature/db-optimization-ratings-cw`. Awaiting user device verification before merge to `main`.
- Decisions: D-166..D-170. Lessons: 5 new entries (SQLite UPSERT constraint, extension trust by-signer, cache enrichment preservation, composable scope, INSERT-when-missing guard).

### Profile UI v4 — Tab animation, watch flow redo, donut DNA, heatmap labels, image picker (D-171..D-176)
- **Tab animation (WhatsApp contact-info style):** Full-size tab bar is now item 0 in each tab's LazyColumn (scrolls away naturally → ProfileHeader lands at top, visible). Shrink driven continuously by scroll offset via `graphicsLayer` lambdas (deferred — no recomposition, no "jump"). Mini tab pill moved to header `actions` slot (right side, between title + settings), widened, each segment clickable. `ScrollBlurOverlay` removed.
- **Watch flow (redesigned):** Taller 128dp chart + horizontal grid + y-axis labels + 30dp bars + per-bar count labels + today color. Tap → floating right-side sidebar overlay (AnimatedVisibility slide+fade) with themed primary-tinted bg, anime covers + EP numbers + total duration. Switching bars switches content; tap same bar to close. ViewModel computes per-day `DayWatchSummary`.
- **Time DNA (donut):** True donut via `drawArc(useCenter=false, style=Stroke)`. Center shows current period color + name. Legend below donut. Right side = recently-watched `LazyRow(reverseLayout=true)` so newest is at the far right.
- **Activity heatmap:** Left day markers (M/T/W/T/F/S/S) + bottom month labels per week column (replaces "Tap and scroll" text). Square 12dp cells, gray empty.
- **Settings image picker:** `PickVisualMedia` launcher → copies to `filesDir` → loads as `file://` URI (persists across launches). URL trimmed + live preview.
- **CI fix:** `AnimatedVisibility` RowScope conflict — extracted `WatchFlowSidebarOverlay` top-level composable (D-176).

### Status
- ✅ CI green (run 31422446992, commit abfe23a).
- Branch: `feature/db-optimization-ratings-cw`. Awaiting user device verification.
- Decisions: D-171..D-176. Lessons: 1 new (AnimatedVisibility RowScope-in-Box → extract to scope-less composable).

### Profile UI v5 — Magnetic snap, watch flow sidebar left, donut tint, crop editor, genre highlight (D-177..D-182)
- **Header:** Magnetic snap (animateScrollToItem on scroll-end, 50% threshold). Gradient blur scrim at header bottom (smoothstep fade). Equal-width mini tab segments (weight(1f) + 120dp pill).
- **Watch flow:** Removed default per-bar count labels. Today's bar uses complementary color (hue + 180°). Sidebar from LEFT (TopStart + slideIn from left). Solid background (surface + border). Taller (200dp). Tap-outside + scroll → auto-close.
- **Time DNA:** Split into standalone card. Colors tinted with primary via `lerp(color, primary, 0.25)`.
- **Recently Watched:** Separate card, vertical LIST format. Episode thumbnails (from `data_cache_episode.thumbnail_url`, falls back to cover). Tap → details.
- **Heatmap:** Fixed label bottom cut-off (16dp bottom padding on day-markers, 14dp month-label height).
- **Genre radar:** Selected genre highlighted IN the web — thicker axis, halo ring, highlighted label pill.
- **Avatar crop editor:** New `AvatarCropScreen.kt` — full-screen pan/zoom/crop Dialog. Pinch-to-zoom (1×–5×), pan, circular overlay, saves cropped bitmap to filesDir.
- **Settings:** Separated URL/upload state (fixes mode-switch leak). Tap preview → crop editor.
- **CI fix:** Coil3 `result.image` (not `Success` cast), `minOf` for Dp, regular `val cropSource`.

### Status
- ✅ CI green (run 31428330476, commit 47196ad).
- Branch: `feature/db-optimization-ratings-cw`. Awaiting user device verification.
- Decisions: D-177..D-182. Lessons: 2 new (Coil3 ImageResult API, local property getter restrictions).

### Profile UI v6 — Snap guard, sidebar taller+scrim, Time DNA+Recent side-by-side, heatmap fix (D-183..D-186)
- **Snap guard:** Magnetic snap now only fires when `firstVisibleItemIndex == 0` (near top). Fixes "scroll to bottom → auto-scrolls to top" bug.
- **Watch flow sidebar:** Taller (260dp, taller than chart card). Card-level transparent scrim for reliable tap-outside close.
- **Time DNA + Recently Watched:** Merged into ONE card, side-by-side Row — donut left (own bg), recently watched list right (own bg).
- **Heatmap:** Column bottom padding 24dp, month-label Box 18dp, day-markers bottom padding 20dp — fixes remaining label cut-off.

### Status
- ✅ CI green (run 31431113076, commit 6945df6).
- Branch: `feature/db-optimization-ratings-cw`. Awaiting user device verification before merge to `main`.
- Decisions: D-183..D-186.

### Merge to main (this session)
- `feature/db-optimization-ratings-cw` (55 commits: DB optimization + ratings UI + continue-watching + watch-progress fixes + extension settings + genre system + Profile UI v3→v6) fast-forward merged into `main`.
- No conflicts — `main` hadn't diverged (0 commits on main not on feature).
- CI on `main` verified GREEN (run 31432557415, commit c15b1b8a).
- Feature branch deleted (local + remote). Only `main` remains.

## Session — Doc-Debt Sweep + Deferred-Concerns Registry (docs/doc-debt-sweep branch)

### Project review + documentation alignment
- Main agent performed a comprehensive read-only project review (CORE_RULES + all AGENT-CONTEXT + codebase structure). Identified high/medium/low concerns across code, architecture, and documentation.
- User reviewed each concern + gave dispositions: most deferred to future phases (saved in registry); AniList placeholder accepted; DB-migration concern clarified (debug = no migrations, §30 reinforced); documentation drift = fix now.

### Documentation sweep (D-187)
- **knowledge/ (7 files fully rewritten):** architecture, module-map, tech-stack, old-vs-new, dashboard, project-overview, ui-customization — all now match actual project state (46 modules, 28 tables, 315 .kt, D-001..D-186, Nav3 removed, hand-rolled nav, `main` branch).
- **Top-level (3 files updated):** master.md, SESSION.md, navigation.md — corrected counts, Nav3 stale references, current focus, Deferred Concerns summary.
- **CORE_RULES.md:** §8 clarified (ABI config location + compileSdk 36 context). §30 reinforced with user clarification (debug = no migrations, just recreate; onOpen is convenience guard, not migration system).
- **Code comments cleaned (3 files):** AndroidConfig.kt, app/build.gradle.kts, libs.versions.toml — orphaned Nav3 comments replaced with accurate context.
- **DASHBOARD/webpage/ (13 files, full-stack-dev sub-agent):** lib/data.ts, lib/decisions.ts, lib/schema.ts, lib/testingData.ts, lib/phaseD.ts, lib/downloadsPlan.ts + 6 page components + Footer. All counts corrected, Nav3 false claims fixed (rememberSaveable→mutableStateListOf, StateFlow→when-dispatch, R7 accepted), branch→main. Build PASSED.
- **Remaining (deferred):** 17-database-schema.md (historical, "21 tables"), dashboard schema.ts SCHEMA_TABLES (planned Phase-1 names), decisions.md numbering (D-121/D-037/D-038/D-008/D-009), repo-root pollution.

### Deferred Concerns registry (D-188)
- 11 tracked items established in progress.md → "Deferred Concerns": AniList placeholder, reResolver orphaned, main-thread runBlocking, dead download code, outer retry loop, WatchKey god-object, nav backstack R7, 4 god-class .kt files, DB migrations, release signing, dashboard schema.ts.

### Status
- Branch: `docs/doc-debt-sweep` (awaiting user verification before merge to `main`).
- CI: pending push (comment-only code changes + docs — expected green).
- Decisions: D-187, D-188.

### Next focus
- **Database management + quality** — user will delete app + reinstall (fresh DB), run through a comprehensive ordered test checklist, export the DB via debug bubble, and provide it. Agent will analyze for flaws + propose improvements.


## Session — D-189 FK Crash Fix (feature/fix-fk-crash branch)

### Crash
- User ran the DB test checklist. On Phase 2 (link extension source to AniList anime), app crashed:
  `SQLiteConstraintException: FOREIGN KEY constraint failed` in `ContentQueries.updateContentSources` ← `ContentResolver.linkExtensionToExisting` ← `DetailsViewModel.linkSource`.

### Root cause
- D-166 enabled `PRAGMA foreign_keys = ON`. The `content.extension_id` FK to `content_ext(id)` was semantically wrong — `content_ext` is never populated (zero callers of `getOrCreateExtension`), and the code passes `extensionId = source.id` (Aniyomi internal) at all 6 link/upsert sites. Pre-D-166 (FKs OFF) this was silently dangling; D-166 exposed it. Same bug on `extension_detail.extension_id` → `content_ext(id)`.

### Fix (D-189)
- `content.sq`: removed FK `content.extension_id` → `content_ext(id)` + FK `extension_detail.extension_id` → `content_ext(id)`. Kept columns (plain INTEGERs storing source.id). Kept `content_ext` table (dead, deferred to DB-quality phase). Added D-189 comments.
- `ContentDataJson.kt`: fixed 1 stale KDoc line.
- No code changes — the code already works correctly with `extension_id = source.id`; the FK was the only problem.

### Sub-agent review
- Task i8: ✅ READY TO PUSH. 7 ✅ items, 0 ❌ issues, 3 ⚠️ non-blocking concerns.

### Status
- Branch: `feature/fix-fk-crash` (awaiting user device verification before merge to `main`).
- CI: pending push.
- Decisions: D-189.

### Next
- User reinstalls (required — schema change, fresh install per §30).
- User re-runs Phase 2 of the DB test checklist to verify the crash is gone.
- User continues the checklist → exports DB → provides it for the DB-quality analysis.
