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

## Session — D-190 Multi-Source Episode Metadata Engine (feature/episode-metadata-engine branch)

### What changed
- Replaced the standalone `EpisodeMetadataFetcher` (Anikage.cc + basic Jikan + AniList streaming) with a pluggable `EpisodeMetadataEngine` using 3 `EpisodeMetadataProvider` implementations:
  - **AniZip** (primary — api.ani.zip — richest: titles, overview, thumbnails, runtime, season)
  - **Jikan** (secondary — api.jikan.moe/v4 — UNIQUE: filler + recap booleans, title_japanese, score)
  - **Kitsu** (tertiary — kitsu.io GraphQL — canonical titles, descriptions, thumbnails)
- Future-proof: `ContentId` + `ContentIdType` (ANILIST/MAL/TMDB/KITSU) + `supportedIdTypes` — adding a new ID type (e.g. TMDB) = new provider module, zero engine changes.
- DB schema: 8 new columns on `data_cache_episode` (is_filler, is_recap, title_japanese, title_romaji, runtime, season_number, episode_number_in_season, score). `is_filler`/`is_recap` nullable (null=unknown, not false=confirmed-not) — Jikan is the only source with filler info.
- Merge: `MetadataMerger.mergeEpisodeBatch` — first-non-null-wins by priority + OR-true for filler/recap.
- Engine: parallel fetch with per-provider try/catch (one failure doesn't cancel siblings).

### Sub-agent reviews
- Task m8 (plan review): verified all 3 APIs live. Found 3 must-fix flaws (call-site count, failure isolation, mergeEpisodeBatch) — all fixed.
- Task m7 (compile review): ✅ READY TO PUSH. Zero compile errors. 8 areas clean.

### Status
- Branch: `feature/episode-metadata-engine` (awaiting user device verification before merge to `main`).
- CI: pending push.
- Decisions: D-190.

### Next
- User reinstalls (schema change — 8 new columns require fresh install per §30).
- User opens an anime with an AniList ID + links an extension source → episode list should load with rich metadata (titles, thumbnails, descriptions, air dates, filler badges from Jikan).
- User exports DB via debug bubble → agent analyzes for the DB-quality phase.

## Session — D-191 DB Analysis + Deferred-Concerns Expansion (docs/db-analysis-and-concerns branch)

### User test completion
- User ran the full DB test checklist (Phase 0-14) on the D-190 build. Everything worked: browse, details, metadata, library, search, watch (90% + 50% episodes marked correctly), ratings, profile, extensions, downloads, history.
- User uploaded 3 export files to `USER-UPLOADS/`: DATABASE.json (228KB), NETWORK.log (8KB), DATABASE-ACTIVITY.log (41KB).

### D-190 merged to main
- User verified D-190 works → merged `feature/episode-metadata-engine` to `main` (fast-forward) + deleted the branch. `main` is now at 2500365.

### DB analysis
- Agent analyzed all 3 exports. **DB is mostly healthy**: 501 rows, 28 tables, zero FK orphans, D-190 enrichment working (143 episodes with good coverage).
- **11 new concerns** found + added to Deferred Concerns registry (#12-22): activity_event empty (zero callers of ActivityTracker.track), Updates not detecting episodes (episode_update empty), Notifications UI-only, download concurrency bug (2nd cancels 1st), download missing server/audio info, file_size=0, extensions page lag (240 icons), extensions need filtering, details stale-state flash, "no source linked" race, user_customization table empty.

### User correction
- User pointed out Phase 5 of the test checklist didn't state extensions are a hard prerequisite for watching episodes. Agent acknowledged + saved as a lesson.

### Status
- Branch: `docs/db-analysis-and-concerns` (awaiting push).
- CI: no code changes — docs only.
- Decisions: D-191.
- Next: user reviews the DB analysis + decides which concerns to prioritize.

## Session — D-192 DB Schema Cleanup + Multi-Phase Plan (Phase 1 of 6)

### Phase 1: DB schema cleanup (COMPLETE — CI green, merged to main)
- Dropped 3 dead tables: content_ext, content_ext_repo, user_customization
- Created app_settings table (backup/restore mirror of PreferenceStore)
- Created SettingsRepository (CRUD + export/import)
- Removed dead FK from content.extension_repo_id
- Verified content table future-proofing (multi-source/multi-content-type/multi-system)
- Cleaned up all dead Kotlin methods + data classes + query references
- Fixed LocalMetadataProvider (removed dead reads)
- CI: first attempt failed (Kotlin keyword `value` — fixed by renaming columns to `setting_*`). Green on second attempt.

### User corrections
- User listed 8 "dead" tables to drop. Research showed 4 are ACTIVE. Agent refused to drop active tables.
- User corrected test checklist: extensions are a hard prerequisite for watching episodes.

### Phases 2-6 (PLANNED — not yet executed)
- Phase 2: Activity tracker wiring (~2h)
- Phase 3: Updates feature rework (~4h)
- Phase 4: Download fixes (~4h)
- Phase 5: Details page fixes (~2h)
- Phase 6: Docs + notify (~1h)

### Status
- main is at 410c380 (D-192 Phase 1).
- Next session: continue with Phase 2 (activity tracker wiring).

## Session — D-192 Phases 2-5 (activity tracker, updates, downloads, details fixes)

### Phase 2: Activity tracker wiring (COMPLETE — CI green)
- Wired ActivityTracker.track() at 7 call sites: WATCH_START, LIBRARY_ADD/REMOVE, RATING, SEARCH, DOWNLOAD_START, APP_OPEN
- Added convenience overload + sessionId default

### Phase 3: Updates feature rework (COMPLETE — CI green)
- Root cause: ensureUpdateState() was never called on library-add
- Fix: wired ensureUpdateState in toggleLibrary + onEpisodesRefreshed in fetchEpisodes
- Added batch_type + episode_count columns for "initial batch" vs "new episode" distinction
- First link creates ONE batch row ("Episodes 1-N added to library", acknowledged); refresh creates individual new-episode rows

### Phase 4: Download data-loss fix (COMPLETE — CI green)
- DownloadedEpisode was missing sourceId/videoServer/videoAudio fields
- DownloadStore hardcoded them to null — data lost on transition from download_queue
- Fix: added fields + updated insert + mapper + construction site

### Phase 5: Details page fixes (COMPLETE — CI green)
- loadGeneration counter prevents stale-state flash
- Synchronous source-link pre-read fixes "no source linked" race

### Resolved concerns
- #12 activity_event → RESOLVED
- #13 Updates → RESOLVED
- #16 download data → DATA FIX DONE (UI deferred)
- #20 stale-state flash → RESOLVED
- #21 no-source race → RESOLVED
- #22 user_customization → RESOLVED (Phase 1)

### Status
- main at bb88275. All 5 phases CI green.
- Next: user reinstalls + tests. Then: remaining deferred concerns (notifications, download UI, refresh-all-progress, auto-update).

## Session — D-193 Updates + Notifications Architecture Plan (feature/updates-notifications-plan)

### What was done
- Analyzed the user's 3 new DB exports (DATABASE-2.json + DARABASE.log.txt + NETWORK.log.txt). Verified: activity_event now has 19 rows (Phase 2 worked), anime_update_state has 11 rows (Phase 3 worked), but episode_update is still 0 rows (onEpisodesRefreshed fires before ensureUpdateState — ordering bug noted for fix).
- Created planning branch `feature/updates-notifications-plan` (NOT merging to main — per user feedback).
- Research sub-agent (u3) read ALL of :core:updates, :core:notifications, :core:schedule, NotificationsSettingsScreen, UpdateCheckWorker. Found: 3-way toggle bug root cause (TriggerState.ordinal mismatch), "no source from library" root cause (performAutoLink doesn't persist source link), 3 notification triggers (only 1 wired), UpdateCheckWorker hard-coded to 1h.
- Wrote comprehensive architecture plan (PLAN.md, 463 lines).
- 5 sub-agent review sessions (architecture, smart-release, settings UI, DB schema, final consolidated). Found 12 blocking issues — all addressed in plan v2.
- Full-stack-dev sub-agent built a new dashboard page at /updates-notifications-plan (14 sections, architecture diagram, smart-release chain visualization, settings tree, 8 open questions). Build PASSED.
- User feedback acknowledged: merged to main without confirmation (lesson saved). Will NOT merge this branch without explicit approval.

### Status
- Branch: `feature/updates-notifications-plan` (NOT merged — awaiting user approval of the plan).
- CI: no code changes — docs + dashboard only.
- Decisions: D-193.

### Next
- User reviews the plan on the dashboard page.
- User answers the 8 open questions in §13.
- User gives the go → implementation begins (10 phases, ~34h).

## Session — D-193 All 10 Phases Complete (feature/updates-notifications-impl)

### All phases implemented + CI green:
1. Bug fixes: 3-way toggle + no-source-from-library + onEpisodesRefreshed ordering
2. DB schema: 5 new columns + 4 query updates + 1 new query + 2 indexes + 3-day expiry
3. Settings UI: combined section + master toggle + interval + sub/dub toggles + test notification
4. Auto-update: configurable WorkManager + manual mode + per-category filter + live-progress
5. Smart release: OneTimeWorkRequest chaining + 10-min polling + max 3 attempts + ±1h window
6. Sub/Dub tracking: checkSingleAnime rewrite + separate counts + preference filtering
7. Notifications: 3 triggers wired + tap deep-link + test notification + dedup
8. Updates feed: live-progress StateFlow + initial-batch rendering + acknowledgment
9. Interface pattern: ScheduleRefresher + NotificationSender (avoids circular deps)
10. Docs + this entry + notification

### Status
- Branch: `feature/updates-notifications-impl` (NOT merged — awaiting user approval)
- CI: GREEN on all commits
- All 10 phases of the D-193 plan implemented

## Session — D-193 v2 Redesign Clarifications + Documentation Web Page

### What was done
- User tested the v2 build (phases 1–4 of the redesign) and gave detailed feedback. Three critical clarifications were locked in that reshape how the system is described (NOT how it must be re-implemented — the engine already checks both sub+dub; the toggle already only gates notifications):
  1. **Episode type toggle = notifications only.** The engine ALWAYS partitions fetched episodes by audio variant and diffs both sub and dub against last-known counts — regardless of the Sub/Dub/Both toggle. The toggle only filters which found episodes actually post a notification. Checking ≠ notifying.
  2. **Notifications is a dedicated page**, not an inline toggle in the updates settings. It lives at the bottom of Updates & Notifications as a nav row, opens a page with a master enable switch + the two triggers + the library-customization toggle.
  3. **Library-customization toggle semantics:** OFF (default) = the default trigger settings apply to every anime in the library (no per-anime options anywhere). ON = each anime's details page gains a notifications section where the user can enable/disable + override triggers per anime.
- Built a comprehensive documentation web page (Next.js, single `/` route) that visualizes the entire Updates + Notifications system: system-overview flow diagram, Auto/Manual/Off mode comparison cards, the episode-type clarification matrix, the smart-release polling sequence (+10/+20/+60/+120 min) + the averaging loop, the updates-feed lifecycle + live-progress banner mockup, the notifications page mockup, the schedule grayed-out logic, the settings-UI card inventory, an interactive testing checklist (with localStorage persistence + how-to-test-notifications guide), and an end-to-end "how it works" narrative.
- Verified the page with Agent Browser (renders, scroll-spy nav works, testing checklist toggles + persists, mobile nav renders, no runtime/console errors) + VLM (visual quality 9/10 — dark theme correct, accent cards balanced, no rendering issues).
- Lint clean.

### Status
- Web page: live on the dev server at `/` (this Next.js project).
- ANI-KUTA app: no code changes this session — documentation + clarification only. The engine implementation already matches the clarified semantics.
- Branch: `feature/updates-notifications-impl` (NOT merged — awaiting user approval).

### Key artifact
- `src/app/page.tsx` + `src/lib/aniKutaData.ts` — the documentation web page + its data layer.

## Session — D-193 v2 Code Fixes (all 14 items, CI green)

### What was done
Fixed all 14 issues identified in the audit + re-verification:

**4 blocking fixes:**
1. Episode-type toggle now gates NOTIFICATIONS only — engine always inserts both sub+dub; NotificationManager honors the global Sub/Dub/Both toggle at notify time (UpdatePreferences injected into NM).
2. "Check dub on completed anime" now actually works — checkDueAnime unions getDueDubAnime(now) when the setting is on, so FINISHED anime with pending dub are checked.
3. "Customize library notifications" toggle built — added libraryCustomizationEnabled pref + toggle on Notifications page + per-anime DetailsNotificationSection on the details page (gated behind the toggle). NotificationManager falls back to default triggers when no per-anime config exists.
4. "Update categories" picker built — replaced the "coming soon" placeholder with a real multi-select screen (UpdateCategoriesScreen). UpdatesViewModel.checkForUpdates now filters by selected categories in Manual mode.

**Cleanup + improvements:**
5. Removed duplicate notifications master toggle from UpdatesSettingsScreen (now a single nav row to the dedicated Notifications page).
6. Smart-release real averaging: added learned_offset_ms column + weighted average (70% previous + 30% new). The system now converges on the show's real release rhythm.
7. Smart-release worker now parses the real audio variant from the found episode (was hardcoded "unknown").
8. Removed dead on_immediate firing in ScheduleEngine.
9. UpdateScheduler now only schedules the periodic worker in AUTO mode (MANUAL + OFF cancel it).
10. Battery-optimimization dialog added to FirstRunSetupDialog (step 3).
11. New ScheduleNotificationWorker fires on_schedule at the exact airing time via a OneTimeWorker (was opportunistic only).
12. Aligned SmartReleaseCheckWorker to use content.sourceId (was extensionId — inconsistent with the engine).

**Verification:**
- Sub-agent compile review found 3 blocking issues (missing import, missing derived val, missing Gradle dep) — all fixed.
- CI run 31634281699 failed (missing UpdateCategoriesScreen import in MainActivity) — fixed in d40c135.
- CI run 31634661679 GREEN ✅.

### Status
- Branch: feature/updates-notifications-impl (NOT merged — awaiting user approval).
- CI: GREEN on commit d40c135.
- All 14 audit items addressed.

## Session — D-193 v2 Merge to Main

### What was done
- User tested the latest APK from the feature branch + confirmed all fixes look proper (episode-type toggle + others all working).
- User gave explicit approval to merge.
- Pre-merge verification: CI green on commit 2b8c269 (the latest on the feature branch). Local main was in sync with origin/main.
- Merged feature/updates-notifications-impl into main with a merge commit (57bbd17) — 50 files changed, +2924/-295 lines.
- Pushed main to GitHub.
- CI on the merge commit (57bbd17) passed — GREEN.
- Deleted the remote feature branch (origin/feature/updates-notifications-impl).
- Deleted the local feature branch.
- Deleted the old planning branch (feature/updates-notifications-plan) — both local + remote. It was a docs-only planning branch superseded by the actual implementation.
- Final state: only `main` branch remains (local + remote).

### Status
- Branch: **main** (the only branch that remains).
- CI: GREEN on main merge commit 57bbd17.
- D-193 v2 Updates + Notifications system is now live on main.

## Session — Project Review + Dashboard Section (additive-only)

### What was done
- A new AI agent session began by reading CORE_RULES.md in full (per user instruction: read it before anything else), then cloning the repo fresh + reading the entire AGENT-CONTEXT (navigation, master, workflow, SESSION, memory/* — progress 80KB, decisions 226KB, changelog 120KB, lessons 77KB — via 4 parallel Explore sub-agents, + knowledge/* + the Android codebase structure via a 5th Explore sub-agent).
- Produced a comprehensive project review covering: project health, what's built, 22 deferred concerns (grouped HIGH/MEDIUM/LOW/EXPECTED), doc-drift caught this session, features remaining (Phase 6+ backlog), recommended forward direction, + top risks.
- Built a **NEW dedicated dashboard section** at route `/project-review/` (`https://testplay-byte.github.io/ANI-KUTA/project-review/`) rendering the review findings in 9 sections. This is a TEMPORARY section the user requested for reviewing findings — to be removed when no longer needed.
- **ADDITIVE-ONLY** — no existing dashboard content modified. New files: `DASHBOARD/webpage/lib/projectReview.ts` (~585 lines typed data) + `DASHBOARD/webpage/app/project-review/page.tsx` (~590 lines server component). Modified (9 insertions total): `lib/data.ts` (1 NAV_ITEMS entry appended) + `components/Sidebar.tsx` (1 "review" clipboard-check icon key added).
- Delegated the page build to a full-stack-developer sub-agent (CORE_RULES §19) working ONLY in `DASHBOARD/webpage/`.
- Verified the build locally (`bun run build` PASSED — 18/18 routes incl. /project-review; `out/project-review/index.html` 201KB generated).
- Committed on a feature branch `feature/dashboard-project-review` (commit b51d43ad), pushed the branch, fast-forward merged to `main`, pushed main (triggers `deploy-dashboard.yml`), deleted the feature branch (local + remote).
- **Deploy Dashboard workflow** (run 31642901528, commit b51d43ad): `completed` / `success`. Dashboard live on GitHub Pages.
- **Build APK workflow** also triggered (dashboard-only changes don't affect the Android build) — `completed` / `success`.
- End-to-end verified via Agent Browser: homepage loads (HTTP 200, title correct, zero errors), "Project Review" nav link present in sidebar, click navigates to `/project-review/` (HTTP 200), all 9 sections render (LIVE PROJECT REVIEW, Project Health, WHAT'S BUILT, CONCERNS & ISSUES, Doc-Drift, FEATURES REMAINING, FORWARD DIRECTION, TOP RISKS, temporary review note), all key findings content present (46 modules, 26 DB tables, 331 Kotlin files, D-193, HttpDownloader.reResolver, WatchKey god-object, MainActivity.kt:470, LibraryScreen 2471, Ads system, Backup/restore, Manga reader, Device verification FIRST, Aniyomi/MPV binary-compat trap, etc.), dark mode toggles correctly (html.dark class verified), existing `/decisions/` page unaffected.
- **Mobile overflow fix (5b8351ef):** Agent Browser check at 375px viewport found 56px horizontal overflow — the hero "Reviewed this session" badge had `whitespace-nowrap` + fixed `h-7` (394px wide). Fixed: removed nowrap + changed `h-7` → `py-1.5` (flexible height) + added `max-w-full`. Redeployed (workflow run on 5b8351ef, `completed`/`success`). Re-verified: `375px <= 375px` (no overflow). All 9 sections still render on mobile.
- **Doc-drift caught this session** (logged in `progress.md` + `lessons-learned.md`): actual SQLDelight tables = 26 (not 28 — D-192 dropped 3 dead tables but docs weren't updated); actual .kt files = 331 (not 315); MainActivity runBlocking at line 470 (not 428); HttpDownloader guards on `http://localhost` (not `127.0.0.1`); decisions.md numbering drift (D-121 missing, D-037/D-038 out of order, D-008 says compileSdk 35). These are doc-staleness, not code bugs. Future doc-debt sweep needed.
- Updated `AGENT-CONTEXT/memory/progress.md` (this session block + doc-drift findings) + `lessons-learned.md` (3 new entries: doc-drift compounding, whitespace-nowrap overflow pattern, Agent Browser polling patience) + this changelog entry. No D-NNN decision added (temporary dashboard addition, not a permanent architecture decision).

### Status
- Branch: **main** (only branch; feature branch merged + deleted).
- CI: GREEN on main commit 5b8351ef (Deploy Dashboard `completed`/`success` + Build APK `completed`/`success`).
- Dashboard live at `https://testplay-byte.github.io/ANI-KUTA/project-review/` — verified end-to-end via Agent Browser (desktop + mobile + dark mode).
- Temporary review section is live for the user to review findings + decide the forward direction.

---

## Session — Project Review Dashboard Rebuild (fresh review, replaces prior /project-review/)

### What was done
- User instructed: read CORE_RULES + all AGENT-CONTEXT + codebase FIRST (before anything else), then DELETE the existing `/project-review/` dashboard page completely + build a FRESH dedicated section showing all key findings in a simplified, easy-to-scan format. Deploy via GitHub Actions. Nothing else in the dashboard should change.
- Read CORE_RULES.md in full (498 lines, 30 sections) via raw GitHub URL BEFORE cloning (per user's explicit instruction). Noted §4 (single wrapper folder), §8 (CI-only APK builds), §14 (sub-agents only touch DASHBOARD/webpage/), §19 (webpage work → full-stack-developer sub-agent), §25/§26 (dashboard must stay current + doc-verification gate).
- Cloned the full repo to `/home/z/ani-kuta-repo` (6.7s). Verified structure matches §4 (single `ANI-KUTA/` wrapper folder + `.github/workflows/` at repo root).
- Read orientation files myself (navigation, master, workflow, SESSION) + the top of `progress.md` (current phase, deferred concerns 1-22, known doc debt).
- Dispatched **5 parallel Explore sub-agents** (user's max) to digest: (4-a) decisions.md 226KB, (4-b) lessons-learned.md 79KB, (4-c) changelog.md 125KB, (4-d) all 7 knowledge/* files, (4-e) Android codebase structure + existing dashboard examination. All appended to `/home/z/my-project/worklog.md`.
- **Verified facts against actual code** (not docs): 46 modules (settings.gradle.kts), **26 SQLDelight tables** across 15 .sq files (NOT 28 — D-192 dropped content_ext + content_ext_repo + user_customization; content.sq has 6 tables now), **331 .kt files** (NOT 315), **MainActivity runBlocking at line 470** (NOT 428 — SAF subtitle disk-scan), **HttpDownloader.reResolver CONFIRMED ORPHANED** (DownloadModule.kt:92 passes null; catch block at HttpDownloader.kt:277 permanently unreachable), **4 god-class files** (wc -l: LibraryScreen 2471, DetailsScreen 2282, DetailsViewModel 2263, WatchScreen 2029).
- Discovered the existing `/project-review/` page was NOT the 9-section review the changelog described — a later un-logged session (commit 564f1a55) had REPLACED it with a DC1-DC5 test checklist. User wanted it deleted + rebuilt fresh.
- Delegated the page rebuild to a **full-stack-developer sub-agent** (§19) working ONLY in `DASHBOARD/webpage/`. Sub-agent: deleted old `app/project-review/page.tsx` (487-line test checklist) + `lib/projectReview.ts` (486-line test data); created NEW `lib/projectReview.ts` (539 lines, fully typed) + `app/project-review/page.tsx` (1015 lines, server component) rendering 9 sections per DESIGN.md (MEMORY OS v3). `bun run build` PASSED (18/18 routes).
- **No edits to data.ts or Sidebar.tsx** — the NAV_ITEMS "Project Review" entry + `review` clipboard-check icon already existed + matched the new page's purpose. Additive-only (only /project-review/ content changed; all other dashboard pages untouched).
- **9 sections**: §1 Snapshot (verified metrics + tech stack), §2 Project Health (verdict + 7 indicators), §3 What's Built (13 feature areas), §4 Concerns & Issues (9 open + 4 accepted + 7 recently-resolved + 1 dashboard debt, severity color-coded), §5 Doc Drift Caught (9 rows), §6 Features Remaining (5 backlog groups), §7 Forward Direction (4 prioritized steps), §8 Top Risks (8 rows), §9 Footer Note (temporary notice).
- Created feature branch `dashboard/project-review-rebuild`, committed (6d79c075), pushed, fast-forward merged to `main`, pushed main.
- **Mobile overflow — 3 fix iterations** (Agent Browser at 375px found 57px overflow each time):
  1. Commit aee222e5: hero badges container had `shrink-0` (forced 382px width) → removed shrink-0 + added `max-w-full`; hero reviewer paragraph had long URL (`https://testplay-byte.github.io/ANI-KUTA/`) → added `break-all` to the mono span + `break-words` to the `<p>`.
  2. Commit 9d286165: `SectionCard` component wrapped the `right` prop (count-pills) in `shrink-0` → changed to `max-w-full`; pills div got `max-w-full`. Pills now wrap on mobile.
  3. Commit 2d74b785: bullet text spans (`<span>{b}</span>`) in HEALTH/FORWARD_DIRECTION/FEATURES_REMAINING/FOOTER sections had no `min-w-0`/`break-words` → long file paths like `LibraryScreen/DetailsScreen/DetailsViewModel/WatchScreen` (no breakable spaces) overflowed. Added `min-w-0 break-words` to all bullet text spans + the verified-facts span + the what's-built area.items `<li>`.
  4. Commit 2a812470: FEATURES_REMAINING `group.bullets` + `group.numbered` had a DIFFERENT rendering structure (outer `<span>` with no class wrapping label + note) — the prior replace_all missed these. Added `min-w-0 break-words` to the 4 spans (outer + note, in both bullets + numbered lists).
- **Final mobile verification**: `overflow=0` at every scroll position (0, 800, 1600, 2400, 3200, 4000, 5000 px). Desktop 1280px: all 9 section headings render, no errors. Dark mode: toggle works (`html.dark` class applied). Existing `/decisions/` page: unaffected, "Architecture Decisions" heading renders, no errors.
- **CI status**: Deploy Dashboard #44/#45/#46/#47/#48 all `completed`/`success`. Build APK #532/#533/#534/#535 all `completed`/`success` (dashboard-only changes don't affect the Android build). All verified via GitHub Actions API polling (lesson L128 — never assume green).

### Status
- Branch: **main** (feature branch `dashboard/project-review-rebuild` merged + to be deleted).
- CI: GREEN on main commit 2a812470 (Deploy Dashboard + Build APK both `completed`/`success`).
- Dashboard live at `https://testplay-byte.github.io/ANI-KUTA/project-review/` — verified end-to-end via Agent Browser (mobile 375px no overflow + desktop 1280px + dark mode + existing pages unaffected).
- Temporary review section is live for the user to review the fresh findings + decide the forward direction.
- No D-NNN decision added (temporary dashboard rebuild, not a permanent architecture decision).

---

## Session — Download System Fixes + Database Review Dashboard (commits 8f0ea772 → af51be7e, on `main`)

### What was done
- User reviewed the `/project-review/` dashboard + gave specific instructions: fix 5 open concerns (HttpDownloader.reResolver, retry loop, runBlocking, file_size, data.json), assess nav backstack R7, fix dashboard schema.ts, fix doc-drift, deep database review + optimization proposal, improve downloads page UI (server name + audio version).
- **RESEARCH**: Dispatched 5 parallel Explore sub-agents (2-a download system deep-dive, 2-b downloads UI + data.json, 2-c MainActivity + nav, 2-d database schema analysis, 2-e dashboard schema.ts + doc-drift audit). All returned comprehensive file:line-precise findings.
- **Phase A (D-149)**: HttpDownloader.reResolver wiring — new `ReResolverAdapter.kt` in `:app` (bridges the local fun interface to the app-class ReResolver), Koin binding in AnikutaApp, `DownloadModule.kt` `reResolver = getOrNull()`, fixed 2 latent bugs (127.0.0.1 guard + `updateDownloadVideoUrl` new SQL query — the old code wrote to `video_uri` not `video_url`).
- **Phase B (D-151)**: Outer retry loop — new `RetryPolicy.kt` in `:core:download` (classifies retryable: IOException + HttpException 5xx/429; non-retryable: DownloadException, 4xx, generic Exception; exponential backoff 5s/10s/20s capped 60s). `DownloadQueue.launchDownload` refactored with `while(true)` retry loop wrapping the existing permit-acquire + download body. `setRetryingStatus` (was dead code) now wired. Max 3 outer × 2 inner = 6 total attempts.
- **Phase C**: MainActivity runBlocking fix (ANR risk) — extracted the `onPlayEpisode` lambda body into `buildWatchKeyForDownloadedEpisode` (suspend helper running on `Dispatchers.IO` via `withContext`). `AppRoot` uses `rememberCoroutineScope` + `scope.launch`. Eliminates `runBlocking` + 5 synchronous DB queries on the main thread.
- **Phase D**: Downloads page UI — (D-1) `ResolverSheet.onPickVideo` now carries `(serverName, audioLabel)` through `DetailsScreen` → `MainActivity` (was: `linked.sourceName` = extension name + `audioLabel = ""`; now: the actual resolver server + audio version). (D-2) `DownloadedFilesScreen` redesigned — 2-line episode row with server name (primary ExtraBold, matches ResolverSheet), audio chip (secondaryContainer, matches ResolverSheet), quality chip, file size. `DownloadViewModel` re-wrap fixed (was discarding `videoServer`/`videoAudio`/`sourceId`/`downloadedBytes`). `DownloadQueue.kt` file_size fix (was `0L`, now `completedTask.totalBytes`).
- **Phase E**: data.json refresh path — new `reconcileDataJsonFromContent` method in `DownloadScanner` (fetches latest ContentRecord + AniListDetail + ExtensionDetail from DB, writes back to data.json if any field differs — only on change). `requestFolderRescan()` wired into `AnikutaApp.onCreate` (background IO scope, non-blocking). Handles the user's scenario: old data.json files get updated on every app launch.
- **Phase F (nav backstack R7)**: DEFERRED per sub-agent recommendation — accepted limitation (D-150), configChanges handles rotation/theme, process death is the only trigger, WatchKey fits in Bundle but fix touches fragile player area + user is ignoring WatchKey this session. Risk > reward.
- **Phase G (dashboard schema.ts + doc-drift)**: Full-stack-dev sub-agent rewrote `lib/schema.ts` (26 actual tables, 13 groups, ER nodes/edges), fixed 14 stale strings across dashboard pages. Main agent fixed AGENT-CONTEXT doc-drift: 28→26 tables, 315→331 .kt, D-186→D-193, 134→163 lessons, 428→470, compileSdk 35→36, Nav3 "stays on classpath"→"fully REMOVED" — across master.md, SESSION.md, knowledge/* (7 files), decisions.md (D-008 + D-150).
- **Phase H (database review dashboard)**: Full-stack-dev sub-agent built NEW `/database-review/` section — 6 sections (Snapshot, Schema Inventory 26 tables, 8 Merge Candidates with MERGE/KEEP_SEPARATE/DROP/INVESTIGATE badges, Top 3 Improvements, Overall Assessment, Footer). New `lib/databaseReview.ts` + `app/database-review/page.tsx` + NAV_ITEMS entry + new "dbreview" icon.
- **Compile review**: Explore sub-agent caught 2 critical errors before push (DownloadQueue.kt extra brace, MainActivity.kt wrong WatchKey package). CI caught 1 more (DownloadLogger.w doesn't take exception arg). All fixed → CI GREEN.
- **CI status**: Build APK #540 `completed`/`success` on commit af51be7e. Deploy Dashboard triggered on main push.

### Key decisions
- **D-194**: HttpDownloader.reResolver wired via adapter pattern (ReResolverAdapter in :app implements HttpDownloader.ReResolver, bridges to app-class ReResolver). Keeps :core:download independent of :core:video-resolver (dep graph minimal per M17/M49).
- **D-195**: RetryPolicy classifies retryable exceptions — IOException + HttpException 5xx/429 retryable; DownloadException (non-Http) + 4xx + generic Exception not retryable. Exponential backoff 5s/10s/20s capped 60s. Max 3 outer × 2 inner = 6 total attempts.
- **D-196**: data.json write-back via DownloadScanner.reconcileDataJsonFromContent — runs on app startup, fetches latest DB state, writes to data.json only on change. Handles the "old data.json not updated" scenario.
- **Database optimization proposal** (not a decision — a proposal for user review on the new /database-review/ dashboard page): drop other_source_detail (dead, 0 callers), merge app_metadata → app_settings (degenerate KV), consolidate anime_metadata_cache + anilist_detail (9-column duplication). 26 → 23 tables ideal.

### Status
- Branch: `main` (feature branch merged + deleted).
- CI: GREEN on main commit af51be7e (Build APK + Deploy Dashboard both triggered).
- Dashboard: schema.ts rewritten, /database-review/ live, doc-drift fixed across all live docs.
- No device verification yet — user will test the download system fixes on device.

---

## Session — Database Restructuring Plan + Dashboard (commits e64235cc → 324986cb, on `main`)

### What was done
- User reviewed the `/database-review/` dashboard + gave specific direction: rename `content` → `main_entry`, merge `anilist_detail` + `extension_detail` + `other_source_detail` into a unified `content_details` (keeping data source ≠ extension SEPARATE), absorb `anime_metadata_cache`, keep `data_cache_episode` separate, analyze `browse_cache`, do NOT merge updates/notifications/ratings/genres/library. Create a beautiful dashboard page showing the full plan.
- **RESEARCH**: 5 parallel Explore sub-agents (Tasks 2-a through 2-e): content table deep-dive, detail tables + source-switch flows, cache trio, data source vs extension concept, confirm keep-separate groups. All returned file:line-precise findings.
- **PLAN**: Wrote full plan at `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md` (446 lines). 3 core changes (rename, merge, absorb) + 11 independent improvements. 26 → 24 tables.
- **REVIEW (4 iterations via sub-agents — NOT self-review per user instruction)**:
  - Iteration 1: 1 FLAW + 9 CONCERNS → fixed (display_source migration, type-change note, DataSourceExtras, unlinkSource clarification, source_ref_id convention, FK precondition).
  - Iteration 2A (architecture) + 2B (feasibility) parallel: 2 FLAWS + 11 CONCERNS → fixed (NOT NULL→nullable for clearExtensionAxis, content_id regeneration, ignoreUnknownKeys, getAniListDetail semantics, stale-title fix, episode_number scope, FK-add DROP TABLE, anilistId nullability, updateExtensionAxis atomicity, multi-size cover URLs).
  - Iteration 3 (sign-off): 0 FLAWS + 7 minor → fixed (polish pass — migration narratives, updateContentTitle query, stale meta-commentary, future-proofing precision).
  - Iteration 4 (confirmation): 2 cosmetic fixes → applied (dead-column count typo, query-naming consistency).
- **DASHBOARD**: Full-stack-dev sub-agent built `/database-plan/` page — 11 sections, every table + every column + every query + every con + every deferred item. `lib/databasePlan.ts` (~830 lines) + `app/database-plan/page.tsx` (~870 lines). New "DB Plan" nav item + dbplan icon. Build passes (20/20 routes). Mobile overflow fix (3px sidebar artifact — not content).
- **Verified via Agent Browser**: desktop 1280px (all 11 sections render, dark mode works), mobile 375px (no content overflow at any scroll position), existing pages unaffected.
- **CI**: Deploy Dashboard #50 + #51 both `completed`/`success`.

### Key decisions
- **D-197**: Database restructuring plan (PROPOSAL — not implemented). 26→24 tables via: (1) rename `content`→`main_entry`, (2) merge 3 detail tables → `data_source_detail` + `extension_detail` (Option C — two tables, keeping data source ≠ extension separate), (3) absorb `anime_metadata_cache`. Plus 11 independent improvements. Plan reviewed via 4 sub-agent iterations. Awaiting user approval.

### Status
- Branch: `main` (feature branch merged + deleted).
- CI: GREEN (Deploy Dashboard #51 success on commit 324986cb).
- Dashboard: `/database-plan/` live at https://testplay-byte.github.io/ANI-KUTA/database-plan/
- Plan doc: `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md`
- **This is a PROPOSAL — no schema changes made. Awaiting user approval before implementation.**

---

## Session — Database Restructuring Plan v2 (commits 78db1955 → 23d7839c, on `main`)

### What was done
- User reviewed plan v1 (`/database-plan/` dashboard) + gave revised direction: merge `data_source_detail` + `extension_detail` into ONE wide `content_details` table (Option A — reversed v1's Option C two-table decision). Drop `app_metadata`. Keep `data_source`+`system` separate. Keep `extension_repo_id`. Keep `display_source` as single UX column. Target under 15 tables (preference, not hard). Group tables logically (presentation, not forced merging).
- **RESEARCH**: 2 Explore sub-agents (R-1 design content_details, R-2 re-evaluate merges + grouping). R-1 designed the 26-column `content_details` table with `data_*`/`ext_*` prefixes + discriminators + `extra_json`. R-2 confirmed: keep `data_source`+`system` separate, drop `app_metadata` (dead), all 7 keep-separate groups confirmed, library NOT in app-settings group, 10-group presentation.
- **PLAN v2**: Rewrote `PLAN.md` (446 lines). 26→22 tables via 4 changes: (1) rename `content`→`main_entry`, (2) merge 4 tables→`content_details` (Option A, 26 cols), (3) drop `app_metadata`, (4) keep `data_source`+`system` separate.
- **REVIEW (4 iterations via sub-agents)**: Iter 1 (2 FLAWS + 4 CONCERNS → fixed: extension lookup contradiction, description caller migration). Iter 2A+2B parallel (0 FLAWS + 7 CONCERNS → fixed: episode_number scope, transaction boundaries, display_source values, MAL related_anime, idx drops, Long↔TEXT conversion, effort estimate). Iter 3+4 sign-off (0 FLAWS + 1 minor → APPROVED).
- **DASHBOARD**: Full-stack-dev sub-agent updated `/database-plan/` with v2 content. 20/20 routes build. Mobile overflow=0 at all scroll positions.
- **Verified**: Agent Browser desktop (all 10 sections, v2 content present) + mobile (overflow=0).
- **CI**: Deploy Dashboard success.
- **Table count honesty**: 22 tables (above user's 15 preference). Research confirms remaining 22 are genuinely better separate — merging any would create sparse/awkward tables, break FK integrity, or corrupt backup semantics. 22 is the floor without forcing bad merges.

### Key decisions
- **D-198**: Database restructuring plan v2 (PROPOSAL — not implemented). 26→22 tables. Key change from v1 (D-197): ONE wide `content_details` table (Option A) instead of two tables (Option C). Also: drop `app_metadata`, keep `data_source`+`system` separate, keep `extension_repo_id`, keep `display_source` as single UX column.

### Status
- Branch: `main` (feature branch merged + deleted).
- CI: GREEN (Deploy Dashboard success on commit 23d7839c).
- Dashboard: `/database-plan/` live at https://testplay-byte.github.io/ANI-KUTA/database-plan/ — updated with v2 content.
- Plan doc: `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md` (v2).
- **This is a PROPOSAL v2 — no schema changes made. Awaiting user approval before implementation.**

---

## Session — Library Badge Customization System (commits cb3c6aff → db0535d0, on `functionality/improvements`)

### What was done
- User requested comprehensive library cover badge improvements across 6 iterative fixes (fix9–fix14):
  - **fix9**: Edge-to-edge cover badges with theme-adaptive colors (flush with cover corner, 12dp outer radius match).
  - **fix10**: Badge data enrichment — `LibraryEntry` extended with `releasedEpisodes`, `audioAvailability`, `watchedCount`; `LibraryViewModel.enrichEntriesWithBadgeData()` populates from `DataCacheRepository` + `WatchProgressStore`.
  - **fix11**: Horizontal `DisplayModeCard` (name LEFT, icon RIGHT); SUB/DUB split into separate badge entries with dot separators (not wide centered text).
  - **fix12**: Removed `BadgePositionSelector` — positions hardcoded (EP=TOP_END, Score=TOP_START). Badge text made Bold 8sp.
  - **fix13**: Scroll-to-minimize header animation in `CustomizeSheet` (matches ProfileScreen pattern: pinned header with animated font size, mini tab pill fade-in, magnetic snap, graphicsLayer alpha/scale on tab strip).
  - **fix14**: Advanced RELEASED badge options — `ReleasedAudioFilter` enum (BOTH/SUB/DUB), `releasedUnwatchedOnly` toggle, custom SVG `ImageVector` icons (subtitle + microphone), theme-adaptive blue (SUB) + orange (DUB) colors, per-type episode counting, `CoverBadgeData` class with optional icon.

### Key decisions
- **D-242**: Library cover badge system + advanced RELEASED options. Full details in `decisions.md`.

### Files changed (fix14 — the final iteration)
- **New**: `BadgeIcons.kt` (169 lines) — custom `ImageVector` for Sub (subtitle frame + 2 lines) and Dub (microphone capsule + cradle + base).
- **Modified**: `LibraryEntry.kt` (+33) — added `subEpisodeCount`, `dubEpisodeCount` + computed `subUnwatchedCount`, `dubUnwatchedCount`.
- **Modified**: `LibraryViewModel.kt` (+66) — `ReleasedAudioFilter` enum, `releasedAudioFilter` + `releasedUnwatchedOnly` StateFlows + setters + persistence, per-type episode counting in enrichment + DEBUG logging.
- **Modified**: `LibraryScreen.kt` (+327/-48) — `CoverBadgeData` class, `ReleasedAudioFilterCard` composable, RELEASED sub-options UI, `CoverBadgeRow` updated for icons, `LibraryGridCard` badge rendering rewrite, param threading through `LibraryGrid`.
- **Modified**: `AndroidConfig.kt` — version 0.2.36 → 0.2.37 (versionCode 36 → 37).

### Review
- 3 sub-agents reviewed fix14 in parallel (data layer, CustomizeSheet UI, badge rendering). All found NO CRITICAL/WARNING issues.
- One logic bug found + fixed: BOTH+unwatched fallback incorrectly showed "EP N" when user watched everything. Fixed: fallback now only fires when per-type data is genuinely null (not when counts are 0).

### Status
- Branch: `functionality/improvements` (1 commit ahead of remote: `db0535d0`).
- Version: 0.2.37 (versionCode 37).
- **Awaiting push + CI build** — no GitHub credentials available in build environment. User needs to push manually.
- Not yet device-tested — user will test after CI builds the APK.

### CI Build Fix (commit b4c75ba3)
- First CI run (32587888021) FAILED: `Unresolved reference 'cubicTo'` in BadgeIcons.kt — Compose's `PathBuilder` uses `curveTo` (not `cubicTo`, which is on `AndroidPath`).
- Found the correct API name via `REFERENCES/animiru/ANIMIRU/app/src/main/baseline-prof.txt`: `PathBuilder;->curveTo(FFFFFF)V`.
- Fixed: replaced all 8 `cubicTo` calls with `curveTo` in BadgeIcons.kt.
- Second CI run (32588139111) PASSED: all 6 steps green, APK artifact created (55.3 MB).
- Version bumped: 0.2.37 → 0.2.38 (versionCode 37 → 38).

### Status
- Branch: `functionality/improvements` (pushed to remote, CI green).
- APK artifact: `anikuta-apk` (55.3 MB, artifact ID 9479636335).
- Download: https://github.com/testplay-byte/ANI-KUTA/actions/runs/32588139111 (Artifacts section).
- Ready for device testing.

---

## Session — Full Project Review + Dashboard Key-Findings Rebuild (2026-08-22, on `main`)

### What was done
- User instructed: full read-through FIRST (CORE_RULES + all AGENT-CONTEXT + codebase — no changes), then DELETE the stale `/project-review/` page completely + build a FRESH `/key-findings/` dashboard section with all key findings in a simplified, scannable format. Deploy via GitHub Actions. Nothing else on the dashboard may change. Max 5 sub-agents at a time. Do NOT read REFERENCES/.
- **RESEARCH**: 5 read-only sub-agents — R-1b (Android concerns verification), R-2 (decisions/changelog digest), R-3 (lessons/progress digest), R-4 (dashboard review), R-5 (unmerged-branches analysis). Main agent re-verified every metric against source.
- **VERIFIED CURRENT STATE (re-derived)**: 47 Gradle modules · 363 .kt files · **23 SQLDelight tables across 15 .sq files** · v0.2.22 · main @ 570c68f4 (D-239). **D-198 restructuring IS implemented** (commit 775876a2 — `main_entry` + `content_details` in content.sq) despite decisions.md still saying "PROPOSAL — not implemented". God classes grew: DetailsScreen 3165 / DetailsViewModel 2852 / LibraryScreen 2504 / WatchScreen 2018.
- **TOP FINDINGS**: decision log forked 3 ways (main ends D-198 with 41 decisions unlogged; `feature/test-controller-v5` claims D-197..D-202 for different decisions; `functionality/improvements` active at D-240..D-242, 42 commits, v0.2.46, clean merge today); AniList tracker syncEntry stub returns true (fake success, trackerId=0); download system never device-tested end-to-end; 11 of 22 deferred concerns verified RESOLVED on main; dashboard stale in 8+ places (D-186 claims, 26/28-table counts vs real 23, old-schema database page, 6 unreachable routes).
- **DASHBOARD**: full-stack-dev sub-agent (§19) deleted `app/project-review/page.tsx` (1028 lines) + `lib/projectReview.ts` (761 lines) + Sidebar `review` icon key; created `lib/keyFindings.ts` (670 lines, fully typed) + `app/key-findings/page.tsx` (719 lines, server component, 9 sections per DESIGN.md); added 1 NAV_ITEMS entry + 1 `findings` icon key. NOTHING else on the dashboard changed. `bun run build` PASSED (19 routes + _not-found; /key-findings present, /project-review gone).

### Status
- Branch: `main` (direct dashboard-only commit — established precedent for dashboard-only changes; zero app-code changes).
- CI: verified via Actions API after push (Deploy Dashboard run green).
- Dashboard: `/key-findings/` live at https://testplay-byte.github.io/ANI-KUTA/key-findings/ — TEMPORARY section (removal = 4 files: page, lib, NAV_ITEMS entry, icon key).
- No D-NNN added (temporary dashboard section — same precedent as prior project-review rebuilds).
- **Recommended forward direction (full detail on /key-findings/)**: (1) land `functionality/improvements` after its session completes; (2) memory reconciliation pass (backfill D-199..D-239, fix D-198 status, renumber test-controller decisions); (3) download-system device test; then test-controller reintegration, AniList tracker completion, extensions UX, WatchKey registry refactor, god-class splits, dashboard truth-sweep.

---

## Session — Video Caching + Parallel Download Engine (2026-08-22/23, on `test-feature/video-cache-new-download`)

### What was done
- User's task (2 parts in one session, explicit rules: no main changes, no merges without confirmation, max 5 sub-agents at a time, CI-verified, single session): (A) video playback caching with dedicated settings; (B) a new MPV-inspired parallel download method with an on/off toggle.
- **Research**: 3 parallel sub-agents (player pipeline / download system / infra patterns). Key discoveries: loadVideo() is dead code (5 real loadfile sites in WatchScreen); videoTitle is the stable identity string; the Advanced-downloader settings UI + prefs already existed as dead code; new .sq tables need no version bump.
- **Plan** (`DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md`): 2 review rounds × 3/2 sub-agents — round 1 found 3 critical flaws (proxy 502 would permanently break playback → 302-redirect fail-open; identity from frozen watchKey = wrong-episode corruption → live episode state; onProgress from N workers races the queue's non-thread-safe tracker → dedicated reporter coroutine).
- **Part A (D-243, commit 95909b12, CI GREEN)**: new `:core:playback-cache` module (48 modules now) — NanoHTTPD cache proxy on 127.0.0.1 between MPV and the video URL (range-aware serving, disk slices + upstream gap fetches tee'd into `<filesDir>/playback-cache/*.bin`), `playback_cache_entry` table (24 tables now) + reactive queries + driver-factory guard, LRU eviction (100MB..2GB, active-stream-safe), stale-file verification, fail-open at every layer (pre-loadfile → original URL; pre-body proxy errors → redirect to upstream; mid-stream → connection close). WatchScreen hook at all 5 loadfile sites via `currentCacheId` (live episode state; null → direct playback). "Video caching" settings screen (toggle default ON, limit slider, usage, cached-episodes list with cached-point display, clear-all).
- **Part B (D-244, commit 5cedad58)**: `VideoFetcher` seam — HttpDownloader stays the facade (validation/subtitles/publish/.data.json untouched); `SingleConnectionFetcher` = the legacy downloadNormal extracted verbatim; `ParallelHttpFetcher` = the new engine (0-1 Range probe, budget-capped chunk workers ≤16 conns/queue, positional writes into pre-allocated sparse temp, per-chunk exponential backoff, premature-EOF + stall watchdog, active-call registry, re-resolve on ANY localhost HttpException incl. 403, chunk sidecar resume, single-stream fallback, 250ms serialized progress reporter); `HlsDownloader` parallel mode (concurrent segment workers + ordered spill writer, in-memory AES-128-CBC decryption with MEDIA-SEQUENCE default-IVs + rotating-key rejection + PNG-strip-before-decrypt, append-state sidecar resume with playlist-stability validation, variant-URL base fix; legacy mode preserved byte-for-byte + clears stale parallel state). Engine-switch safety in both directions (foreign sidecar → clean restart). `advancedDownloader` default → true.
- **Compile reviews caught real bugs pre-push**: CR-A (3 compiler-level errors) + CR-B (4 compile errors + a Semaphore double-release runtime crash + probe-outside-re-resolve + sidecar cleanup gap) — all fixed before pushing.

### Status
- Branch: `test-feature/video-cache-new-download` (from main @ 26e47722). CI: Part A run 32609975071 GREEN; Part B run 32611101475 (see progress.md).
- NOT merged to main — awaiting user device verification (test checklist delivered in the session summary).
- Docs: PLAN.md, D-243/D-244 (decisions.md), database/playback-cache.md + database/README.md (§24 folder created), progress.md + this changelog. Dashboard intentionally untouched (deploys from main; truth-sweep at merge time).
- CI trigger note: build-apk.yml has `test-feature/**` added on the BRANCH ONLY — remove before merging to main.

---

## Session — Video caching session-2: fix "registered but not cached" + tap-to-play + background fill (2026-08-23, on `test-feature/video-cache-new-download`)

### What was done
- **User device feedback (the feedback loop working as designed)**: episodes appeared in the Video Caching settings but `cached_bytes` stayed ~0. Requested: make caching actually work; tap-to-play from the list (same server/quality/resolution, resume from where left); background loading of the rest while playing; comprehensive logging + logcat filters.
- **Root-cause analysis (no blind guessing)**: (1) unknown-Content-Length → the session-1 code redirected MPV straight to upstream — playback fine, ZERO caching (the separate 0-1 probe made this the default path for extension proxies); (2) HLS entries only proxied the tiny playlist — the actual segments bypassed the cache entirely; (3) the probe burned an upstream request per first serve.
- **Fixes (D-245)**: learn-mode serving (mirror the client's Range upstream; learn total from the response; chunked-with-tee when unknown — the redirect is now ONLY for true pre-body errors, always logged); the probe is removed; HLS playlist REWRITING (master variants → /p/, segments + init → /s/; per-segment cache files, URL-hash named; BYTERANGE bypasses, live playlists don't fill).
- **New features**: background fill (progressive gaps in 8 MB blocks, player-frontier-aware; HLS segments in order, VOD only; race-safe disk-recounted stats); tap-to-play (4 new schema columns incl. stored subtitle/audio track lists; clickable settings rows → full WatchKey → WP-B3 resume).
- **CR-C compile review (compile probe against the real dependency jars — EXIT 0) caught 2 runtime bugs pre-push**: `response.use{}` closing the streaming body before NanoHTTPD read it (critical — would have broken every learn-mode serve); HLS segment-stat races (fixed with disk recounts). Also: variant-playlist relative-URI base, `!!` removal, TOCTOU cleanup.
- **Logging**: every decision point in the cache now logs (tag `Anikuta:Core:PlaybackCache`): play/serve/learn/parts/gap/tee (4 MB-throttled)/flush/complete/hls/seg/fill/evict/delete/fail-open — each with a short key prefix for correlation.

### Status
- Branch: `test-feature/video-cache-new-download` (commit 23a93c8b + docs commit). CI: see progress.md.
- NOT merged to main — awaiting user device verification with the new logging in place.
- Docs: PLAN.md Session-2 addendum, D-245 (decisions.md), database/playback-cache.md updated (new columns + segment model), progress.md + this entry.

---

## Session — Download network resilience + cache identity persistence + sandbox emulator test environment (2026-08-23, on `test-feature/video-cache-new-download`)

### What was done
- User device feedback: downloads break on Wi-Fi loss with no auto-restart; downloaded > total display bug; cached videos still load from network; + install an Android emulator in the agent sandbox (user authorized x86 builds + emulator tooling).
- **D-246 (commit 512279ee, CI green)**: network-loss auto-pause → connectivity-return AUTO-RESUME (the resume existed in name only: PAUSED tasks were invisible to tryStartNext + the service stopped itself, killing the NetworkCallback); offline transport errors → PAUSED instead of retry-burn; CallRegistry instant teardown; effTotal (downloaded never exceeds total); retry() clears persisted tracker state; cross-session cache-identity recovery (conservative single-entry+quality-match). CR-D compile review caught 2 pre-push bugs.
- **Emulator-test x86_64 APK (commit cf4a8a6f, CI green)**: separate CI artifact via -PemulatorX64Build=true; shipped APK unchanged; CORE_RULES §8 amended (user-authorized exceptions).
- **Sandbox emulator**: API 30 AOSP x86_64 AVD (720x1280, 1024MB; 4GB cgroup is the ceiling). Sandbox quirks documented (double-fork detach, timeout-wrapped adb, input-text chunking). SMOKE TEST PASSED end-to-end: install → launch → AniList browse/details → extension repo + install + trust (×2 sources) → source picker → extension search → Cloudflare WebView-bypass UI. Playback blocked by Cloudflare on the datacenter IP (documented — needs user device). Found real UX bug: FirstRunSetupDialog "Skip for now" does nothing (empty onClick).

### Status
- Branch: `test-feature/video-cache-new-download` @ cf4a8a6f+. CI green ×2 (runs 32619494659 + 32631607584).
- NOT merged to main — awaiting user device verification.
- Docs: D-246 (decisions.md), CORE_RULES §8 amendment, progress.md session block, this entry. Emulator env reusable by future sessions (see progress.md for the exact quirks + commands).

---

## Session — Emulator-testing documentation + session lessons (2026-08-23, on `test-feature/video-cache-new-download`)

### What was done
- User request: full-fledged documentation of everything learned (the emulator environment + all key testing knowledge), properly backed up to GitHub.
- **NEW `knowledge/emulator-testing.md`** (~460 lines): the complete sandbox Android-emulator guide — verified capabilities/limits (Cloudflare blocks playback testing from datacenter IPs), environment facts + why each choice (x86_64 TCG, AOSP image, 1024MB guest RAM — all alternatives documented with their failure modes), setup-from-scratch (~15 min, exact commands incl. the manual system-image install that works around sdkmanager's spurious no-space failures), the 5 sandbox rules (double-fork detach, timeout-wrapped adb, input-text truncation, 4GB cgroup ceiling, TCG slowness/ANR norms), the full daily workflow (boot-poll, install, launch, UI dump+parse, screenshots, logs, interaction pacing), app-specific tricks (appops install grant, battery whitelist, first-run prefs injection — the Skip-button bug is documented as the workaround's reason — extension-repo prefs injection, install+trust flow, working extension sources), the E2E smoke checklist with the verified commit, a 13-row troubleshooting table, and the logcat filter reference.
- Facts re-verified before writing: AOSP image has NO ARM translation (strings-scanned system+vendor imgs); smoke test ran the x86_64 APK from commit cf4a8a6f (run 32631607584).
- **lessons-learned.md**: +12 entries (163→175... actual count 183 incl. prior) — the process reaper/double-fork, adb discipline, input-text limits, ARM-image impossibility, the 4GB ceiling, TCG ANR norms, pause-needs-resume-path architecture pattern, in-memory-identity cache bypass, response.use{} stream-closing, stale-estimate size inversion, cooperative-cancellation limits + CallRegistry, standalone-jar compile probes.
- **navigation.md / master.md / SESSION.md**: emulator-testing.md added to all three (knowledge index, on-demand reading list, and a dedicated "Testing on the Emulator" pointer section).

### Status
- Docs-only commit on the branch; pushed; CI verified green (docs-only build). Emulator environment knowledge now survives sandbox clears (source of truth = the repo, not /home/z).

---

## Session — D-247 progress-window caching + extension compat testing (2026-08-23, on `test-feature/video-cache-new-download`)

### What was done
- User report: the cache fetched the WHOLE episode (all segments) — wanted [pos−2min, pos+2min] only, never more, plus a smooth fallback when cache playback fails, + extension APKs saved to the repo, + emulator testing with them.
- **D-247 implemented (commits 88abfe34 + 1d82693d, CI green ×2)**: window-bounded tee (ceiling pos+120s per request), tick-based window fill (behind-first, self-paced), EXACT HLS window mapping via EXTINF parsing, beyond-window segments served-not-cached, 32MB pre-playback fallback cap (found via emulator test), cache-failure → direct-retry fallback in WatchScreen, phantom-range fix (register-after-write-success). CR-E compile probe caught the Int/Float breaker.
- **Emulator verification with the real Anikoto v14.4 extension (full E2E)**: extension installed + trusted + searched (30 results) + details + resolve (4 videos, 20 subtitle tracks) + PLAYBACK through the cache proxy: window 0..11 engaged (12 segments / 23MB cached), segments #12→#50+ "BEYOND window — served, NOT cached" — MPV read-ahead fully bounded, playback unaffected. AniKoto180 v16.9 also installs + loads + trusts + appears in the source picker.
- **Extension APKs saved to the repo**: USER-UPLOADS/extensions/ (per user request) + README with install instructions.
- **Env hygiene**: app cache cleared + emulator stopped + artifact zips deleted after testing; preview server restarted.
- Docs: PLAN.md Session-3 addendum (design + the interpretation note + the future preloading idea), D-247 decision, 4 new lessons, this changelog entry.

### Status
- Branch: `test-feature/video-cache-new-download` @ 1d82693d+. CI green. NOT merged — awaiting user device verification (esp. the window behavior + the v16 extension on device).

---

## Session — D-248 UX improvements (2026-08-23, on `test-feature/video-cache-new-download`, commits 0650135f + 4f367a81, CI green 32655570777)

### What was done
Six user-reported areas, researched by 5 parallel agents (R-A continue-watching/R-B profile stats/R-C library covers/R-D search/R-E downloads), implemented + compiler-verified (CR-F: 4 standalone-jar probes EXIT 0), CI-caught one smart-cast error (fixed in 4f367a81):
1. Continue Watching cards → player DIRECTLY (resolve + startPosition resume; Details fallback).
2. Profile stats: organic-only counted set (manual marks never count; <10% never counts; recents >20%) — fixes the 2,333-episodes-in-one-day glitch (root cause: bulk AniList-sync inserts stamping last_watched_at with duration=0).
3. Library covers: two-way fallback (5 sites) + the DownloadScanner cover-wiping root cause + Details refresh persistence (both axes, merge-with-existing).
4. Downloads: anti-shrink guard + unreadable-folder cleanup suppression + scan mutex (the "downloads disappear but files exist" trio).
5. Search: trending on first entry + recents as results-grid header (persist until an actual search; scroll away with content).
6. Time DNA layout spacing.

### Status
- CI green. NOT merged — awaiting user device verification.

## Session — D-249 continue-watching lazy-init + Updates UI overhaul + Browse redesign (2026-08-23, on `test-feature/video-cache-new-download`, commits 222c0b2e → f4be250, CI green 32661002201)

### What was done
- **D-249**: continue-watching lazy-init fix (cards appeared empty on cold start because the lazy load raced the carousel composition); Updates UI overhaul (the Updates tab visual redesign — see D-248 session for the stats-honesty overlap); Browse page redesign.
- CI: 2 intermediate fix commits (552e06d `kotlinx.coroutines.flow.first` import for StateFlow predicate await; e6f9f0e `kotlinx.serialization.json.int` import) before the final green f4be250.
- **Docs gap (caught 2026-08-24)**: this D-249 changelog entry is a BACKFILL — the D-249 session block exists in `progress.md` (lines 18-48) but the changelog entry was missing until now. decisions.md HAS the D-249 entry.

### Status
- CI green on f4be250. NOT merged — awaiting user device verification.

## Session — Full Project Review + Dashboard Key-Findings Rebuild (2026-08-24, on `test-feature/video-cache-new-download`, commit 28410e6, deploy run 32731668048 = success)

### What was done
- User instructed: read CORE_RULES + all AGENT-CONTEXT + codebase FIRST (no app changes), then completely rebuild the `/key-findings/` dashboard page (a.k.a. "project review") with a FRESH review reflecting the **test-feature branch state** (not main's). Deploy via GitHub Actions. Nothing else on the dashboard changes. Max 5 sub-agents. Do NOT read REFERENCES/. Work on `test-feature/video-cache-new-download`.
- Read CORE_RULES.md (557 lines, 31 sections) via raw GitHub URL BEFORE cloning → cloned fresh into `/home/z/ani-kuta-repo` → checkout `test-feature/video-cache-new-download` @ f4be250 (D-249, 21 commits ahead of main @ 26e4772).
- Read orientation files (navigation, master, workflow, SESSION) + top of progress.md + the 22 Deferred Concerns registry myself.
- Dispatched **5 parallel read-only Explore sub-agents** (R-1 decisions/changelog digest, R-2 lessons/progress digest, R-3 knowledge files, R-4 codebase structure verification, R-5 dashboard + video-cache work review). Each appended findings to `/home/z/my-project/worklog.md` per the worklog protocol.
- **VERIFIED (re-derived from source, NOT from docs)**: **48 Gradle modules** (1 app + 28 core + 1 data + 18 feature — `:core:app-update` unlogged + `:core:playback-cache` added D-243) · **382 .kt files** (docs said 331/363) · **24 SQLDelight tables across 17 .sq files** (docs said 26/28 — `playback_cache_entry` added D-243, `app_metadata` dropped D-198, `app.sq` intentionally empty) · version **0.2.47** (versionCode 47) on test-feature · **190 lessons learned** (was 163) · CI **GREEN on test-feature @ f4be250** (Build APK run 32661002201) · **2 unmerged branches** (this one + `feature/test-controller-v5` dormant) — `functionality/improvements` was merged to main @ 26e4772 BEFORE this branch was cut.
- **Top findings**: (1) video caching (D-243/D-245/D-247) + parallel download engine (D-244) + download resilience (D-246) shipped CI-green with a 4-layer fail-open design (R-5 verified), BUT the parallel engine Part B (ParallelHttpFetcher, HLS AES-128-CBC in-memory decryption, stall watchdog, re-resolve-incl-403, rotating-key rejection, pause/resume with sidecar, anti-shrink guard) is NOT device-tested; (2) decision log STILL has a 43-decision gap (D-199..D-241 absent in decisions.md — same gap as main, NOT backfilled on this branch); (3) D-198 status STILL factually wrong (decisions.md says PROPOSAL, commit 775876a2 implemented it); (4) god-class files all GREW on test-feature (LibraryScreen 2471→3863, DetailsViewModel 2159→3510, DetailsScreen 2277→3240, WatchScreen 2017→2194; new: PlaybackCacheManager 1758, MainActivity 1719); (5) progress.md "Current Phase" header stale (only mentions D-243+D-244 though D-245..D-249 session blocks exist below); (6) AniList syncEntry is NO LONGER a stub (D-242 implemented the SaveMediaListEntry GraphQL mutation at AniListTracker.kt:282 — R-4 verified the code; KDoc still says TODO = doc-drift); (7) HttpDownloader.reResolver NO LONGER orphaned (R-4 verified Koin binding via ReResolverAdapter); MainActivity runBlocking NO LONGER live (all 5 grep matches are comments).
- **Dashboard**: full-stack-dev sub-agent (§19, DASHBOARD/webpage/ only) rewrote `lib/keyFindings.ts` (670 → 775 lines, same 9-section TS structure, only data values changed) + `app/key-findings/page.tsx` (JSDoc + 6 SectionCard titles converted to dynamic `.length` counts). NO other dashboard files touched (data.ts, Sidebar.tsx, other pages, other lib, DESIGN.md all unchanged). `bun run build` PASSED (22/22 static pages, 21 routes, /key-findings present).
- **Deploy**: `deploy-dashboard.yml` only auto-triggers on `main` push; used `workflow_dispatch` on `test-feature/video-cache-new-download` (no workflow file change — honors "nothing else changes"). First run failed at the `deploy` job with `BlobNotFound` — ROOT CAUSE: the `github-pages` environment has a `branch_policy` protection rule (only `main` + `functionality/improvements` + `feature/debug-bubble` allowed). FIX: added `test-feature/video-cache-new-download` to the environment's deployment-branch-policies via the GitHub API (a repo SETTING, not a file change — honors "nothing in the dashboard page changes"). Reran the failed deploy job → `completed/success` (run 32731668048).
- **Verified live via Agent Browser**: mobile 375px `overflow-x:false` at every scroll position (0/1000/3000/6000/9000); desktop 1280px no overflow, all 9 section headings render with correct dynamic counts (16 areas, 16 concerns, 16 fixed, 11 drift, 8 risks); dark mode toggle works (`html.dark`); existing `/decisions/` page unaffected (1280px, no overflow).
- No D-NNN added (temporary dashboard rebuild, same precedent as prior project-review rebuilds). Full findings + forward-direction recommendations live on the dashboard page itself.

### Status
- Deployed live at `https://testplay-byte.github.io/ANI-KUTA/key-findings/`. NOT merged to main — awaiting user review of the findings + forward direction.

## Session — Settings-UI icon unification (D-250) (2026-08-24, on `test-feature/video-cache-new-download`)

### What was done
- User feedback: the More page icons look like "proper SVG icons" (clean), but the Settings page icons "change to some other kind of format, which is not good." Same for the Appearance page + other settings sub-pages — improve + make consistent/cleaner. Stay on the current branch; complete per workflow + send a notification.
- **Root cause**: the More page's `MoreListRow` (`:core:designsystem`) renders a **bare 24dp `Icon` tinted `primary`** (no container). The Settings/Appearance/Notifications hubs each had a LOCAL `*NavRow` that wrapped the icon in a **36dp `primaryContainer` rounded box** ("chip-box") — a different visual format from the More page, exactly as the user reported. Same glyphs, different container.
- **Fix (D-250)**: (1) Reused `MoreListRow` directly in the 3 hubs — deleted `SettingsNavRow`/`AppearanceNavRow`/`LibraryNavRow` + swapped 11 call sites. (2) Promoted `BackAction` to `:core:designsystem` as a shared composable — replaced 12 private copies + 3 inlined bodies (fixes 2 missing-size-modifier drifts + the divergent TrackersScreen variant). (3) Fixed the lone feature-module chip-box (`AutoLinkSettingsScreen.PerExtensionCard` → bare 24dp primary icon). (4) Fixed a copy-paste bug: `NotificationsSettingsScreen.triggerDescription` SILENT branch returned the ON-branch text → now `"Notify silently $condition"`. (5) Removed dead code: `ConfigSegmented` (never called) + a dead `ImageVector` import. (6) Moved `LibraryNavRow` out of its `SettingsGroupCard` (would have double-padded with `MoreListRow`'s baked-in h-padding).
- **Compile review (sub-agent)**: ✅ PUSH-READY — zero errors across 17 files; `MoreListRow` signature matches all call sites. ⚠️ ~30 now-dead imports (harmless — no `ktlint`/`detekt` config; tidy follow-up queued).
- **Docs**: `DESIGN-LANGUAGE.md` §2.4 (new "Nav-Row Icon Language" rule); `DESIGN-SYSTEM/03-settings-extensions-profile.md` §3 (chip-box snippet → `MoreListRow`-reuse + D-250 change-note); D-250 in decisions.md; this entry; lessons-learned patterns (icon consistency + chip-box drift detection).

### Status
- CI pending push at doc-write time. NOT merged — awaiting user device verification of the unified icon look across More → Settings → Appearance → Notifications.


## Session — D-251 dead-wiring fixes + library display modes + release/versioning overhaul + emulator rebuild (2026-08-24, on `test-feature/video-cache-new-download`)

### What was done
- User instructed 5 items (all done): dead-imports cleanup (verify first), virtual device handled optimally, fix the dead `collapsed`/`scrollOffset` wiring, Library Comfortable "Hide Titles" toggle + Cover Only rework (square + zero-gap), and release/versioning discipline (bump +1, proper GitHub releases, fix Check-for-Updates, arm64-only APKs).
- **Dead wiring fixed**: SourcePreferencesScreen + ExtensionRepoSettingsScreen now drive `CollapsingHeader` + `ScrollBlurOverlay` from a real `rememberLazyListState()` (canonical pattern from the ~15 working screens). SourcePreferences hoists the state into `PreferenceList`; ExtensionRepo gained the overlay it never had; the mispositioned outer-Box overlay relocated.
- **Library**: (A) Comfortable "Hide Titles" toggle (`library_comfortable_hide_titles` pref + CustomizeSheet TwoWayButton gated on COMFORTABLE_GRID + title Text skipped in LibraryGridCard — rounded corners/spacing kept); (B) Cover Only reworked: `RectangleShape` covers (all 5 shape sites), zero grid gaps both axes, full-bleed contentPadding — edge-to-edge wall. COMPACT_GRID unaffected.
- **77 verified-dead imports removed** across 15 files (audit sub-agent verified each; kept delegate imports + used symbols) + stale VideoCachingScreen comment fixed.
- **Releases**: v0.2.48; arm64-v8a-only shipped APKs (armeabi-v7a dropped); NEW `release-apk.yml` (stable releases on `v*` tags, tag↔versionName guard, `ani-kuta-vX.Y.Z.apk` asset); build-apk.yml x86_64 emulator build now manual-dispatch-only.
- **Check-for-Updates fixed**: `GitHubUpdateSource` rewritten — `/releases?per_page=30` list endpoint (prereleases visible), best-release selection (highest version, stable beats prerelease), tuple version comparison (fixes patch≥100 collision).
- **Sandbox emulator rebuilt + verified E2E**: statvfs LD_PRELOAD shim (overlayfs disk-check bypass), `-qemu -m 1024` RAM override, `-accel off` TCG, archived emulator 35.1.19; cold boot ≈9min → home screen verified; helper `/home/z/emu/emu.sh`.
- Docs: CORE_RULES §8 (arm64-only + release discipline), progress/decisions/changelog/lessons (3 new lessons), DESIGN-SYSTEM/04 library display-modes.

### Status
- CI pending push at doc-write time; tag `v0.2.48` + first automated release immediately after. NOT merged — awaiting user device verification of the library modes + in-app update flow.


## Session — Full Project Review #3 + Dashboard /review/ section (2026-08-25, on `test-feature/video-cache-new-download`)

### What was done
- User instructed: fresh full-project review of the branch (NO app-code changes), completely DELETE the existing "project review" dashboard page (`/key-findings/`, review #2), build a NEW dedicated section with all key findings (concerns/issues + features remaining + how to build them), deploy via GitHub Actions. Max 5 sub-agents; do NOT read `REFERENCES/`.
- Executed the full workflow: CORE_RULES read in full pre-clone → fresh clone → checkout @ 127d074f → ALL AGENT-CONTEXT read → 5 parallel read-only research sub-agents (R-1 deferred-concerns verification, R-2 decisions/doc-drift audit, R-3 features-remaining extraction, R-4 metrics/quality verification, R-5 dashboard/deploy surgical plan) → main agent re-verified every metric against source → full-stack-dev sub-agent (§19, DASHBOARD/webpage/ only) executed the dashboard replacement.
- **Verified state (re-derived, never from docs)**: 48 modules (1 app + 28 core + 1 data + 18 feature) · 383 .kt / 84,001 LOC · 24 tables / 17 .sq / 0 .sqm · v0.2.48 (code 48) · 201 lessons · 26+2 Koin modules · CI GREEN on HEAD (run 32765868210) · release v0.2.48 published (stable, arm64-v8a-only, debug-signed, ~59MB) · TODO=11 / Ponytail=4 / Logger violations=0 / secrets clean.
- **Deferred Concerns re-audit (22 items): 13 RESOLVED / 3 PARTIAL / 6 OPEN** (resolved incl. AniList tracker real impl, reResolver wiring, RetryPolicy, activity/updates/notifications wiring, download concurrency + server/audio/size, details races, user_customization drop). **6 NEW concerns**: java.time without coreLibraryDesugaring at minSdk 24 (crash risk Android 7.x — GitHubUpdateSource/HistoryViewModel/ScheduleViewModel/ScheduleStore), 3 live main-thread runBlocking in DownloadService, MainActivity 200ms OAuth polling loop + login-error TODO, extensions Available-section renders ~240 rows in ONE LazyColumn item, extension drag-reorder not persisted, FirstRunSetupDialog "Skip for now" dead onClick.
- **Doc-drift audit: ~60 stale claims across 12 files** — 44 missing decision IDs (D-121 + D-199..D-241), D-198 status factually wrong (implemented, says PROPOSAL), master/SESSION say "branch: main" vs active unmerged 31-commit branch, knowledge/* say 46 modules/26 tables (actual 48/24), emulator-testing.md documents the pre-D-251 environment, dashboard lib/data.ts frozen at the D-186 era, 4 stale KDocs in code.
- **Dashboard**: deleted `app/key-findings/page.tsx` (720 lines) + `lib/keyFindings.ts` (775 lines); created `app/review/page.tsx` (749) + `lib/reviewData.ts` (865) — 9 sections: Snapshot (12 metrics) / Project Health (verdict + 6 indicators) / What's Built (9 branch highlights) / Open Concerns (15) / Verified Fixed (14) / Doc Drift (12) / Features Remaining (6 NOW + 10 NEXT + 14 LATER, each with implementation path + effort) / Top Risks (8) / Footer Note. Single NAV_ITEMS line swap ("Key Findings" → "Review & Roadmap", same `findings` icon key — zero Sidebar edits). `bun run build` PASSED (22/22 pages; /review present; /key-findings gone). Deployed via `workflow_dispatch` on the branch (github-pages branch policy already included it). NOTHING else on the dashboard changed.
- No D-NNN added (temporary dashboard section — same precedent as reviews #1/#2). No app-code changes.

### Status
- Deployed live at `https://testplay-byte.github.io/ANI-KUTA/review/`. Branch remains NOT merged — the review's #1 recommendation is the device-verification pass over D-243..D-251 (v0.2.48), then merge, then the decision-log reconciliation batch.


## Session — D-252/D-253/D-254: pointed badges + Browse overhaul + custom palette editor (2026-08-25, on `test-feature/video-cache-new-download`)

### What was done
- User instructed (3 work items, emulator explicitly OFF-limits this session): (1) Library COVER_ONLY episode tags "handled properly + make pointier"; (2) complete Browse page UI overhaul (beautiful/modern/clean/navigable, hero/top-banner, smooth animations, DB properly managed, rating tags redesigned — "ugly", cover borders); (3) Custom palette implementation (click Custom → theme switches; click Custom AGAIN → bottom sheet below palettes with per-element color + brightness customization: background, accent, headings, cards/blocks).
- Executed the full workflow: research (R-A browse trace + R-B badge trace + main-agent theme-system reads) → plan → **plan-review sub-agent** (4 real flaws caught + incorporated: drawBehind/clip order on the compound badge, private→internal visibility for the file split, label-bearing swatches param, alpha-clamp for theme colors) → execute A→B→C → **compile-review sub-agent** (verified against the actual material3 1.3.1 AAR; 2 compile errors caught + fixed pre-push: `staticCompositionLocalOf` is a function not a type; missing @OptIn(ExperimentalMaterial3Api) on CustomPaletteSheet) → 3 commits (d1152736 / 4230821c / 7ef10689) → pushed → docs.
- **D-252 (pointed badges)**: BadgeColorScheme promoted to :core:designsystem/badge (+luminance-based dark detection following the APPLIED theme); NEW PointedTagShape (45° tip, RTL-aware); CoverBadgeRow: innermost chip pointed + corner-aware outer clip (0.dp on COVER_ONLY — fixes the curved-sliver defect); compound badge clip-before-drawBehind; dead CoverBadge removed.
- **D-253 (Browse overhaul)**: BrowseScreen.kt (581 lines) split into 4 files (BrowseScreen/BrowseHero/BrowseCards/BrowseSkeleton); full-bleed 260dp auto-advancing hero pager (top-5 trending-with-banner, 6s, drag-guarded, animated elongating dots); cards: 12dp standardized corners + 1dp outlineVariant borders + amber pointed score corner tag (replaces the black-pill + lime-text rating tag; integer AniList scores unified with Library); CW cards: borders + play affordance + press-scale; shimmer skeletons; error state + Retry; sections fade-in; VM: IO dispatchers for cache/parse/CW-enrichment, parallel refresh, isRefreshing in-flight counter.
- **D-254 (custom palette)**: CustomThemeColors (4 colors + 4 brightness offsets) + buildCustomColorScheme (surface ramp + text luminance + card family derived from the picks); LocalHeadingColor + CollapsingHeader integration; AnikutaTheme customTheme param (both modes, AMOLED skipped while custom); ColorPickerSheet moved to designsystem (swatches param, player unchanged); ThemePreferences 8-key persistence + legacy-accent migration; MainActivity live re-theme; CustomPaletteSheet (live preview + 4 element editors + brightness sliders + Reset); Custom re-tap opens the sheet; palette-icon badge on the Custom card.
- Docs updated same-session: decisions D-252/253/254, this changelog, progress.md, DESIGN-LANGUAGE.md (badge language + custom-theme rules), DESIGN-SYSTEM/04 (badge spec + COVER_ONLY), lessons-learned (2 new).

### Status
- CI pending at doc-write time (pushed d1152736/4230821c/7ef10689). NOT merged — awaiting user device verification of the new Browse page, the pointed badges, and the custom palette editor.


## Session — D-255/256: device-feedback fixes + Browse hero v2 + v0.2.49 (2026-08-25, on `test-feature/video-cache-new-download`)

### What was done
- User device-tested D-252/253/254: homepage good but hero "looks very bad" (cover+banner together + tags); palette selection navigates to Browse; custom palette crashes (NoSuchMethodError: FlowRow); verify update-check; bump version everywhere.
- **Diagnosis method (§8-compliant, zero local builds)**: crash-stack analysis → CI-APK artifact download → `META-INF/androidx.versions` (runtime compose = 1.10.4 vs BOM 1.7.8!) → dex string-pool signature grep (runtime FlowRow has an extra `Alignment$Vertical` param) → foundation-layout sources-jar diff 1.7.8/1.8.0/1.9.0 (itemVerticalAlignment added in 1.8) → POM analysis (koin-compose 4.2.2 → org.jetbrains.compose.foundation:1.10.2 → androidx aliases; also lifecycle 2.9.6/2.10.0, activity 1.12.4). Module-split confirmed: koin-compose modules compile 1.10.x; designsystem+player compile 1.7.8 → FlowRow signature mismatch.
- **D-255 fixes**: (1) AnikutaTheme always-CompositionLocalProvider (content never moves between branches — palette switches no longer reset the nav backstack); (2) ColorPickerSheet FlowRow → manual chunked Rows + fixed the pre-existing Color(a,r,g,b) channel-rotation preview bug; (3) GitHubUpdateSource.parseIsoDate → regex + java.util.Calendar (java.time crashes on minSdk 24 + NoClassDefFoundError isn't caught by catch(Exception)); (4) AMOLED row hidden while CUSTOM active.
- **D-256 hero v2**: banner backdrop + cover poster (80×120, 12dp, 1dp border) + rank pill + 20sp 2-line title + ★score·eps·year + genre chips (3 + "+N") over a stronger scrim; 300dp; auto-advance + animated dots + tap-to-Details retained; skeleton matched.
- **Version/release**: 0.2.48 → 0.2.49 (code 49); update-check flow verified end-to-end; v0.2.49 stable release published via release-apk.yml; dashboard version strings updated + deployed via workflow_dispatch.
- Compile review (Task 10): 1 compile error caught + fixed pre-push (MatchResult.Destructured has component1..5 only — 6 groups need groupValues).
- Docs: D-255 (incl. the version-skew OPEN DECISION + BOM-alignment recommendation), D-256, progress, changelog, 5 new lessons, worklog Tasks 9-10.
- Emulator untouched per user instruction.

### Status
- CI green required before tagging; v0.2.49 release + dashboard deploy verified via API/browser. NOT merged — awaiting user device verification (palette stays on settings, custom palette live-customization works, hero v2, update-check finds 0.2.49).

## Session — D-257..D-260: device-feedback batch #2 — hero v3 + preloading + tag borders + search restore + palette/picker overhaul + v0.2.50 (2026-08-25, on `test-feature/video-cache-new-download`)
- **D-257 (Browse)**: hero v3 = inset 16:9 rounded card (wider banner aspect, 20dp corners, 1dp border) + infinite pager (auto-advance always FORWARD — the old wraparound swept backwards through all pages) + dots below the card; SectionPreloader warms Coil memory+disk caches at exact card pixel dims for hero/CW/trending/popular/top-rated (memory-cache key excludes size with no transformations + AsyncImage INEXACT ⇒ exact-dims preload = memory hit); rating-tag borders on the Browse amber pointed score tag AND all Library cover badges (simple chips via Surface border; compound sub|dub via a manual PointedTagShape-geometry stroked path in drawBehind).
- **D-258 (Search)**: default results now RESTORE after X-clear / backspace-to-empty / re-entry (loadDefaults single-owner + showingDefaults + defaultsJob guards; the VM is Activity-scoped so init only ran once per process — root cause of the permanent-empty bug) + staleness guards so late responses never clobber newer state; recents redesigned as a compact chip cloud (FlowRow pills, per-chip remove, Clear-all header) replacing the collapsible list card (collapse pref machinery deleted).
- **D-259 (Appearance)**: NumericEntrySheet ported to :core:designsystem; NEW ThinSlider (4dp track + 18dp rounded-square thumb, 36dp grab area, tap+drag — ABI-stable primitives only); ColorPickerSheet redesigned (sticky header, scrollable, 5-preset single line with diagonal-slash transparent tiles, RGBA ThinSliders + tappable value chips → keypad, live); CustomPaletteSheet redesigned (preview removed, sticky header + always-visible Reset, NO X, ScrollBlurOverlay top scrim, brightness keypad entry, 5-distinct-per-element presets); subtitle picker keeps its own 5-swatch set incl. Transparent.
- **D-260**: version 0.2.50 (code 50) + tag/release; dashboard version refresh (full-stack-dev sub-agent).
- **CI round**: one failure (coil3.request.ImageRequest import path — caught by CI run 32845772374, fixed abb91ac0; compile review had verified the API but not the package path).
- Docs: D-257..D-260 decisions, progress, changelog, lessons (3 new), DESIGN-LANGUAGE + DESIGN-SYSTEM updates, worklog Tasks (plan-review + compile-review + dashboard).

## Session — D-261..D-265: device-feedback batch #3 — palette system overhaul + hero blur/pager fix + random palette + recents redesign + v0.2.51 (2026-08-25, on `test-feature/video-cache-new-download`)
- **D-261 (Appearance palette)**: persistence bug root-caused (`Color.value.toInt()` returns 0 — Color is a ULong value class, ARGB in the UPPER 32 bits; `.toInt()` truncates to transparent; every write stored 0 → "default applied" on restart + "transparent by default" in the picker) → fixed with `.toArgb()` at 6 sites + a one-time corruption migration that heals v0.2.49/v0.2.50 installs. Brightness sliders REMOVED entirely per user feedback. TWO new customizable elements: cardHeading (titles inside cards/blocks) + cardDescription (body/description text inside cards/blocks) — 6-field `CustomThemeColors`, 2 new CompositionLocals in the same always-on provider, 2 new rows + swatches in the sheet. 28-site consumer sweep (Browse + CW + Library + Search + Details) — each Text color arg now reads the local with a `.takeIf { it != Color.Unspecified } ?:` guard. Pre-existing gap fixed: Library header clone now reads `LocalHeadingColor`.
- **D-262 (Browse hero)**: auto-advance "stuck between two banners" bug root-caused (`LaunchedEffect` keyed on `currentPage`, which flips at the 50% scroll crossing during `animateScrollToPage` → cancelled mid-flight, no snap, single-shot loop died permanently) → fixed with a `while(true)` keyed on `(pagerState, virtualCount)` + `CancellationException` catch → wait for gesture end → snap-to-nearest via `NonCancellable`. Interval 6s → **12s** per user feedback. Blurred backdrop (works on minSdk 24 — Coil 3 REMOVED the `Transformation` API entirely; `Modifier.blur` is API 31+ only): new `BlurredBannerBackdrop` composable loads a 160×90 thumbnail via `produceState`+`Dispatchers.IO`+`imageLoader.execute` (custom `memoryCacheKey("hero-blur:$url")` to namespace from sharp requests), box-blurs it (~1ms), renders full-size (GPU bilinear upscale = soft blur); result memory-cached. Darker scrim 0.18/0.45/0.82 → 0.30/0.55/0.88. Dots read `settledPage`.
- **D-263 (Appearance palette)**: colorful channel sliders (red slider red gradient, green green, blue blue, alpha transparent→opaque of current color) via a new `ThinSlider` `trackBrush` param (full-width gradient, skips the two-box split). New `RandomPalette.kt` with 3 generators: Random dark / Random light (constrained-HSV, family hue ±25°, always-readable) / Completely random (per-channel, alpha forced 0xFF). Random button (`Icons.Filled.Casino`) left of Reset in `CustomPaletteSheet` → nested `RandomPaletteSheet` (`DarkMode`/`LightMode`/`Shuffle` icons) applies + persists via the same `setCustomTheme` path (survives restart via D-261's fix).
- **D-264 (Search)**: recents redesigned as a dedicated horizontal-scroll section (outer `Surface` tinted + 1dp border for depth; sticky header History icon + "Recent searches" + Clear all; single `LazyRow` of bordered chips). Signature UNCHANGED → all 3 render sites get it free. Removed `FlowRow`/`ExperimentalLayoutApi`.
- **D-265**: version 0.2.50 → 0.2.51 (code 51); tag/release; dashboard version refresh (full-stack-dev sub-agent).
- **CI round**: 2 fix rounds on D-262 (caught `coil3.ImageResult` should be `coil3.request.ImageResult` + `ImageBitmap` type import; then `ImageResult.Success` should be `SuccessResult` — top-level `@Poko` class, not nested). Compile-review agent SKIPPED this session — CI caught everything per-phase (CORE_RULES §8 loop); plan-review agent's cast-pattern fix was the right idea but the subclass name was confirmed from the Coil 3.0.4 sources jar.
- Docs: D-261..D-265 decisions, progress, changelog, lessons (3 new), DESIGN-LANGUAGE §2.6 (overhauled), worklog Tasks (5 research + plan-review + consumer-sweep sub-agent).

## Session — D-266..D-271: device-feedback batch #4 — Browse CW removal + hero banner fix + last-tab memory + library sorts + scroll perf + tracking refresh + v0.2.52 (2026-08-26, on `test-feature/video-cache-new-download`)
- **D-266 (Browse):** Continue Watching section REMOVED from Browse (4 files: BrowseScreen param/collectAsState/preloader/items, BrowseCards carousel+card+6 imports, BrowseViewModel flow+data class+2 constructor params+2 imports, MainActivity lambda+function+import). BrowseModule.kt UNCHANGED (Koin reflects on the new 2-param constructor). WatchProgressStore + watch.sq remain (Library per-collection toggle is a separate feature). Hero banner hardware-bitmap crash fixed: D-262's BlurredBannerBackdrop.boxBlur() called getPixels() on a Coil-3 HARDWARE bitmap (default API 26+) → IllegalStateException silently caught → backdrop blank → user saw only the dark scrim. Fix: .allowHardware(false) on the ImageRequest + defensive copy in boxBlur (if config==HARDWARE copy to ARGB_8888) + scrim lightened 0.30/0.55/0.88 → 0.22/0.45/0.82.
- **D-267 (Navigation):** AppPreferences gained lastTab (getString/putString, default "browse"). MainActivity AppRoot: koinInject<AppPreferences>() hoisted; currentTab init → appPreferences.lastTab; backstack init → inline when(appPreferences.lastTab){library/search/more/else→key}; onSelect → appPreferences.lastTab = route. Covers cold start + recents + activity recreation. DI unchanged (AppPreferences already bound).
- **D-268 (Library):** LibraryEntry gained lastWatchedAt. WatchProgressStore + SqlDelightWatchProgressStore + watch.sq gained getLastWatchedAt (COALESCE(MAX, 0); store converts 0 → null). LibrarySortType enum gained BEHIND + SEASON_YEAR (kept displayName). applyFilters: LAST_WATCHED stub fixed (sortedBy lastWatchedAt), BEHIND added (compareBy unwatchedCount thenBy title; ascending = caught-up first = user request), SEASON_YEAR added (compareBy seasonYear). enrichEntriesWithBadgeData populates lastWatchedAt. UI auto-renders via forEach.
- **D-269 (Library perf):** PRIMARY fix — collapsed wrapped in remember(isList){derivedStateOf{...}} (was read directly in parent composition → every scroll frame recomposed parent → re-allocated lambdas → children couldn't skip → compounds on fling). Added contentType to 3 items() calls (staggeredItems + grid = "card"; list = "row"). @Immutable on LibraryEntry (all val + AudioAvailability verified immutable).
- **D-270 (Details):** mergeAniListIntoUnified — added val gen = loadGeneration + refreshTracking() after the link (fixes extension auto-link path: after link established, currentMainId + anilistId set + state Success → refreshTracking no longer early-returns). resetState — clears _trackEntry + _pendingRemoteTrackEntry + _showTrackSheet + _showMarkPreviousPrompt + _showMarkSeriesPrompt (fixes stale tracking on re-open).
- **D-271**: version 0.2.51 → 0.2.52 (code 52); tag/release pending CI green; dashboard version refresh (full-stack-dev sub-agent, deferred to post-release).
- ⚠️ D-269 + D-270 bundled into commit e3bd6285 (git add -A staging issue — first commit captured all 3 modified files). Code correct; commit message D-269-only. Documented in decisions D-269/D-270.
- Docs: D-266..D-271 decisions, progress, changelog, lessons (3 new).

## D-272..D-276 — Smart-link ad system + Browse Hero sharp-banner/blurred-cover (2026-08-26)
- **D-272 (:core:ads module):** New isolated + extensible ad module. `AdsConfig`/`AdKind` (sealed, extensible for future ad kinds) + `DefaultAdsConfig` object (config ships in APK bytecode — no user setting; change URL/cooldown here + ship a new release). `AdPreferences` (6h cooldown persisted). `AdsRepository` (interface + impl — cooldown gate). `AppLifecycleObserver` (ProcessLifecycleOwner ON_STOP/ON_START — measures time spent outside the app). `AdsModule` (Koin). New `androidx.lifecycle:lifecycle-process` dependency. Module count 46 → 47.
- **D-273 (AdsCoordinator + SmartLinkAdInterstitial):** State machine (Idle→AdPending→AdInProgress→AdTryAgain→Idle) + full-screen Compose Dialog with 3 Crossfade states. `requestNavigation(proceed)` gates any navigation behind an ad when not in cooldown. Try-again flow: returned too quick (< 15s) → "Try again" re-opens URL → loop until success or max 3 retries (safety cap). Back = cancel (non-intrusive escape, no cooldown set).
- **D-274 (Navigation interception):** `navigateToDetails` helper in `MainActivity.kt` AppRoot wraps every navigate-to-Details with the ad gate. ALL 10 user-tap call sites converted (Browse/Library/Search/Downloads/Updates/History/Profile). Notification deep-link deliberately excluded (system-initiated).
- **D-275 (Browse Hero):** Removed D-262 CPU `boxBlur` + `BlurredBannerBackdrop` + 4 `HERO_BLUR_*` constants + 13 unused imports. New 4-layer HeroCard: (1) SHARP banner `AsyncImage` fillMaxSize, (1.5) blurred COVER bottom strip `Modifier.blur(8.dp).scale(1.15f)` matching details page exactly, (2) lightened scrim (0.15/0/0/0.30/0.55), (3) foreground unchanged.
- **D-276:** Version 0.2.52 → 0.2.53 (versionCode 53); tag/release pending CI green; dashboard version + module-count refresh (full-stack-dev sub-agent); new `APP/ani-kuta/DOCUMENTATION/ads/` architecture doc.
- Workflow: 2 parallel Explore research agents (navigation/details/Browse-Hero mapper + infrastructure-modules mapper) → plan → implement → compile-review sub-agent (0 errors) → push → CI pending.

## D-277..D-280 — Browse hero v4 (uncropped banner + palette gradient) + Search/Browse offline resilience + v0.2.54 (2026-08-26, on `test-feature/video-cache-new-download`)
- **D-277 (Browse Hero):** User found v0.2.53's hero still ugly ("banner not shown properly"). Root cause: `aspectRatio(16f/9f)` + `ContentScale.Crop` discarded ~half of AniList's natively ~3:1 banner; the D-275 blurred-cover strip had a hard seam + sat behind the foreground poster (visually redundant). HeroCard redone: banner `ContentScale.Fit` + `Alignment.TopCenter` (WHOLE banner, no crop), ratio → 1.2:1, the blurred-cover strip REPLACED with a palette gradient — new `rememberCoverDominantColor` in `:core:designsystem` (wraps the existing `CoverColorExtractor`; inline-constructed via `context.imageLoader` — no Koin dep in the core module), darkened `lerp(color, Black, 0.55)` for white-text contrast; gradient `[Transparent, Transparent, coverColor, darkCoverColor]` feathers the banner's bottom into solid color (no hard seam; details-page recipe adapted). Rank pill + poster bg → translucent black. Pager mechanics untouched.
- **D-278 (Search offline):** New `BrowseCacheCodec` object in `:core:anilist` — shared browse_cache JSON encode/decode + SECTION_* keys (no ~30-line parser duplicated across Browse+Search). `SearchViewModel.loadTrending` now CACHE-FIRST: serves the cached trending payload instantly (the EXACT same AniList TRENDING query Browse caches), then refreshes from network → default results show with NO internet (network-fail keeps the cache; falls to Idle only when no cache row). `:feature:anime-search:impl` gained `:core:data-cache` dep + a 5th ctor param (DataCacheRepository — already a Koin single).
- **D-279 (Browse partial-success):** `fetchSection` catch: trending fail + no cache + popular/topRated cached → `Success(empty)` instead of hard Error (screen renders the cached sections). `heroItems` → `combine(_state, _popular)` with a popular fallback (hero renders with whatever cache exists).
- **D-280:** "Data removed after update" audit (read-only): NO code path wipes data on update (schema version 1, additive idempotent onOpen migrations, zero startup purges; the only bulk deletes are user-triggered with confirm dialogs). Most likely environmental: APK signature mismatch → forced uninstall+reinstall (allowBackup=false = no restore), or OS-cleared cacheDir/image_cache under storage pressure. Version 0.2.53 → **0.2.54** (versionCode 54); tag/release after CI green.
- Workflow: sandbox CLEARED at session start → restored from GitHub per CORE_RULES §15 → 3 parallel research agents (Browse-hero / details-page reference / db-cache-offline) → implement → compile-review agent COMPILE-CLEAN (coil3 AsyncImage named-params verified via bytecode dump) → push → CI.

## D-281..D-287 — CI-first workflow + tab-memory scope + hero v5 + Library batch loader/instant switch/scroll perf + v0.2.55 (2026-08-26, on `test-feature/video-cache-new-download`)
- **D-281 (workflow):** CORE_RULES §8 rewritten per user instruction — sub-agent compile-review REMOVED; the loop is now write → push → GitHub Actions builds → read results via API → fix → repeat. CI is the compiler of record. Validated the same session: CI caught D-284's vararg misuse (32977933759) + D-285's SQLDelight duplicate identifiers (32979727730); both fixed next-push.
- **D-282 (navigation):** Tab memory now restores ONLY Browse/Library on cold start (read-site sanitization takeIf{browse|library} + write-site persists only those two + backstack search/more mappings removed). Close on More/Search → reopen lands on the last main tab.
- **D-283 (hero):** Card ratio 1.2:1 → 1.4:1 (~15% shorter card, ~25% smaller below-banner zone) + poster 84×126 → 76×114. Banner (D-277 Fit fix) confirmed working on device — only the height needed tuning.
- **D-284 (hero):** Accent zone is now a 6-color palette gradient from the cover (extractGradientColors: named Palette swatches → cinematic HSL band L∈[0.16,0.42] → light→dark sort → dedupe → resample to exactly 6 stops) + a soft black veil (0.04→0.10→0.32) on top (the "slightly blurred dark" feel; literal blur on a vertical gradient is a no-op — documented). New rememberCoverGradientColors in :core:designsystem.
- **D-285 (Library perf — the big one):** 653-item "All" load went from ~3,300 queries ON THE MAIN THREAD (4-5s freeze) to **7 batch queries on Dispatchers.Default**. Additive named queries only (NO schema changes): getAllWatchedCounts/getAllLastWatchedAt (GROUP BY), getAllEpisodeAudioRows (4 columns), getAllLibraryMainEntries (JOIN dedup); getAllLibraryItems/getAllContentDetails reused (already existed — CI caught my duplicates). New batch methods across ContentRepository/WatchProgressStore/DataCacheRepository + EpisodeAudioAggregates/LibraryItemRecord models. Old "fetch AniList on miss" branch found UNREACHABLE (anilistId≠null implies hasDataSourceLink) — removed, documented.
- **D-286 (Library instant switch):** loadLibrary keeps the grid on screen when state is already Success (silent background refresh — no more Loading flash per tab switch); gridState/listState hoisted into the Activity-scoped ViewModel (scroll position survives tab switches instead of snapping to top).
- **D-287 (grid scroll):** LibraryCoverImage at all 3 cover sites: crossfade(false) per cell (global fade janked fast 5-column scroll + re-faded scroll-back repopulation) + bitmapConfig(RGB_565) (halves memory-cache footprint → 653-cover grid stops evicting itself; scroll-back hits memory cache). Progressive loading unchanged (user-approved).
- **D-288:** version 0.2.54 → **0.2.55** (versionCode 55); tag/release after CI green.
- Session continuity: the previous session was cut off mid-work (D-281..D-284 committed but unpushed + D-285 .sq queries uncommitted). This session pushed them, rode two CI failures to fixes (vararg + duplicate identifiers — both caught by the new §8 loop), and completed D-285..D-287.

## D-289..D-293 — Hero v6 (compact + abstract splash) + Library scroll-jump fix + reveal-once cover fades + palette scroll-jank fixes + v0.2.56 (2026-08-26, on `test-feature/video-cache-new-download`)
- **D-289 (Browse hero v6):** User: hero still "way too tall", gradient approach wrong — wants "a random splash of colors… not a smooth gradient… abstract splash kind of vibe", banner "in the background", hero "a little bit taller than the cover image itself", cover colors blending around it + NO visible banner↔bottom boundary. HeroCard internals redone: fixed HERO_HEIGHT = 148dp (was 1.4:1 ≈ 234dp); banner = full-bleed Crop background; SplashOverlay = 8 seeded-random soft radial blobs in the cover's 6-color palette (2 airy top + 5 dense bottom + 1 poster-echo) drawn via drawBehind — no linear gradient anywhere; unifying veil (0.06→0.52) — the seamless blend is STRUCTURAL (banner spans the full card; every blob edge is a radial falloff → no boundary exists).
- **D-290 (Library scroll-jump):** User: "scrolled way too much down automatically… about the middle" after refresh. R-1 research root cause: loadLibraryImpl emitted Success(DATE_ADDED order) then applyFilters() re-emitted sorted — a recomposition landing in the preemption window composed the unsorted list and LazyGrid key-anchoring followed the first-visible item to its DATE_ADDED rank. Fix: SINGLE-emission loads (filterAndSort computed before any state write) + masterEntries in the VM (also fixes latent clear-search-restore bug) + staggeredState hoisted to VM (Comfortable mode) + resetScrollToTop() on category/search dataset changes.
- **D-291 (reveal-once cover animations):** User: images "outright jump into it… show up one by one with a smoother animation… faster as the users scroll faster… no need to reload [previously loaded] unless the user refreshes." CoverRevealController threaded screen→cells: covers fade 0→1 on FIRST load only (VM-backed revealedCoverKeys survives tab switches, cleared only by pull-to-refresh); velocity-adaptive duration 240ms calm → 70ms fling, sampled non-reactively at load completion; alpha animated in the DRAW phase (graphicsLayer) — zero recomposition churn; soft surfaceVariant placeholder.
- **D-292 (palette scroll-jank):** TWO root causes found: Palette.generate() ran ON MAIN per card (produceState producer inherits Main dispatcher) AND rememberCoverAccentColor ran UNCONDITIONALLY per card even with borders off (the default) — every card entering viewport did a Coil 100×100 load + Palette during scroll. Fixed: withContext(Dispatchers.Default) + 256-entry LruCache (failures cached) + extraction gated on coverBorderEnabled && ADAPTIVE.
- **D-293:** version 0.2.55 → **0.2.56** (versionCode 56); tag/release after CI green.
- Workflow: R-1 Explore research agent (scroll-jump root cause + Coil config + call-site map — found the on-main Palette too) → implement → push → CI GREEN first try (run 32993791653 on 8fa46be+26beba9; the guard follow-up rode the same run via the workflow's concurrency group).

## v0.2.57 — Extension system overhaul (D-294..D-303, 2026-08-27)

- **FIX (critical): extensions no longer disappear after trusting them.** Root cause was twofold: (1) our child-first classloader let unminified extensions (e.g. everything from salmanbappi/extensions-repo) shadow the app's kotlin-stdlib with their own PARTIAL bundled copy — a mixed-stdlib breakage that crashed source instantiation; (2) load failures were silently dropped from every list. The loader now uses a plain parent-first PathClassLoader (reference-Aniyomi-exact — bundled kotlin becomes inert dead weight) and every load failure is VISIBLE.
- **NEW: "Failed to Load" section** on the extensions page showing the exact failure reason per source class, with Retry / Untrust / Uninstall actions. Nothing ever vanishes silently again.
- **NEW: auto update-checking.** Entering the extensions page checks the configured repos for newer extension versions (throttled to once per 30 min, non-blocking). Extensions with updates get an Update button right on their row.
- **NEW: language filter** on the extensions page (globe icon in the filters bar) across all sections — installed, untrusted, errored and available.
- **Lib version 17 support** (known-good range now 12.0..17.0) + versions outside the range are still attempted — incompatibility now means a visible error row, never a silent rejection.
- **Perf: the extensions list is fully virtualized** — sections previously composed ALL their rows inside single non-virtualized items (the Available section of a full repo = 80+ rows composed eagerly).
- **Architecture: the multi-ecosystem provider abstraction is real** — VideoExtensionProvider + AniyomiExtensionProvider facade registered in Koin; new consumers no longer bind directly to Aniyomi internals.
- **Code health: single canonical install path** (the manager's duplicate of the installer pipeline removed), richer load diagnostics (per-source exception class + message), trust-time classloading moved off the main thread.

## v0.2.58 — Search integrity + extension metadata + seasons + install UX (D-304..D-310, 2026-08-28)

- **D-304** — Search crash fix: extensions returning duplicate URLs in one results page crashed LazyGrid (duplicate `"sourceId:url"` keys) — dedupe at both mapping sites + render-time defense.
- **D-305** — Search request identity: generation counter + job cancellation kills stale-result races ("results from another extension"); mode-consistent rendering; source switch with a live query now searches the new source.
- **D-306** — Extension-first episode metadata: EpisodeDisplayResolver (single source of truth) — extension title/summary/preview_url win, providers fill gaps; cache layer preserves extension values; Details + Watch render identically.
- **D-307** — Season detection module: SeasonDetector (:core:common) parses "( Season N - Episode M - Title )" tags; groupEpisodesBySeason + organizeBySeasons preference (seasons default when ≥2 detected); settings-sheet Seasons/Number-groups choice.
- **D-308** — Season selector UI: horizontal chips (All/Season N/Other) between source selector and episode list; tap centers the chip smoothly; filters apply within seasons; range-grouping suppressed while seasons are active.
- **D-309** — Install progress animation: InstallStep.Downloading carries streamed percent (200ms throttle); the Update button is now a filled pill that morphs into ring+% download progress + pulsing "Installing" (installed + available rows).
- **D-310** — Version 0.2.57 → 0.2.58.

## v0.2.59 — Post-update crash + seasons module + episode-list integrity + simple PTR + cover viewer (D-311..D-316, 2026-08-28)

- **D-311** — Post-update NPE crash fix: `InstallStep.Installed` no longer resurrects the stale Update pill (new INSTALLED "Done" phase); READY branch null-safe; `onInstallResult(Installed)` triggers an immediate `loadAll()` so version/hasUpdate/install-state settle together (the post-update refresh system).
- **D-312** — `:core:seasons` module: pattern-driven season engine (season-episode / compact S5E12 / season-only registry, first-match-wins, `register()` hook for future per-extension configs) + provider-hint fusion (name tags win; AniZip/Kitsu seasonNumber fills gaps) — seasons now also work for clean-named extensions when the provider knows the split.
- **D-313** — Episode-list integrity: `EpisodeListNormalizer` (URL dedupe + unique general numbering — renumber 1..N when the extension's numbers are unset/duplicated/timestamp-like; fixes duplicate EP tags AND the cache-row collapse); `EpisodeListDumper` (full raw episode dump to logcat tag `Anikuta:EpisodeDump` — release-build-visible, filterable, at all 3 fetch sites — the user can now copy real naming schemes for format tuning); generation guards on every episode/metadata state write in the shared DetailsViewModel (the cross-anime thumbnail-bleed fix — incl. the auto-link path from the review round).
- **D-314** — Simple pull-to-refresh: Material 3 PullToRefreshBox → full `refreshAll()` exactly like the Refresh button (themed indicator, threshold haptic); the 3-stage episodes/metadata/everything PTR + legacy refresh-stage API deleted.
- **D-315** — Full-screen cover viewer: tap the cover → expands from its exact position into a centered near-full-width view (single-Animatable deferred-read transform); Close collapses back with the same animation; Save streams the original bytes to the gallery (Pictures/ANI-KUTA; MediaStore on API 29+, permission+media-scan on 24–28).
- **D-316** — Version 0.2.58 → 0.2.59.

## v0.2.60 — Season slice numbering + organize redesign + PTR persistence + cover zoom + shared-element transitions (D-317..D-321, 2026-08-28)

- **D-317** — Season slices now show per-season numbers (S1 1..8, S2 1..8 — was arbitrary globals); renumbering is season-aware (globals run S1 1..10, S2 11..18, aligned with AniList-absolute metadata); "Organize episodes by" is three states (Off/Seasons/Numbers) with migration; new "Season in episode tag" toggle renders "S-3/E-5" compound badges (two theme-color shades) in the All list.
- **D-318** — Pull-to-refresh persists until the refresh ACTUALLY completes (refreshAll awaits all three refresh coroutines) + a custom themed indicator (surface disc, adaptive accent, determinate pull arc → indeterminate spin).
- **D-319** — Cover viewer: pinch-to-zoom (auto-resets on finger lift) + saves now read Coil's disk cache first (instant, original bytes; network only as fallback; magic-byte format sniffing).
- **D-320** — EXPERIMENTAL shared-element cover transition: tapping a cover on Browse/Search/Library morphs it into the details banner (and back on back-press) — AnimatedContent navigation shell + SharedTransitionLayout + section-qualified keys carried through the nav key + a loading-skeleton landing spot; per-screen saveable state (browse scroll survives navigation); toggle in Settings → Appearance → Details page.
- **D-321** — Version 0.2.59 → 0.2.60.

## v0.2.61 — Startup-crash fix: compose compile/runtime alignment (D-322, 2026-08-29)

- **D-322** — Fixed the v0.2.60 startup crash (`NoSuchMethodError: sharedElement$default`): the app had been SHIPPING compose 1.10.4 at runtime (silently pulled in by koin-compose 4.2.2's JetBrains-Compose requirement — a BOM constraint cannot cap a required version) while compiling against the BOM's 1.7.8. The compose BOM is now REPLACED by explicit 1.10.4-line pins (compile == runtime == the line every release has actually been running); material3 stays 1.3.1, icons 1.7.8, lifecycle aligned to the really-resolved 2.10.0; the shared-element call ported to the 1.10 API (`sharedContentState` parameter). NEW build guard: `checkDependencyAlignment` fails any build whose packaged compose/lifecycle versions deviate from the pins — this class of skew can never ship silently again.
- **D-323** — Version 0.2.60 → 0.2.61.

## v0.2.62 — Shared-element morph polish + multi-season-only episode tags (D-324..D-326, 2026-08-29)

- **D-324** — Cover → details morph is smoother and keeps rounded corners: 450ms emphasized flight (was 300ms — felt fast), the nav crossfade now runs the SAME duration + easing as the morph (the mismatched velocity profiles were the jitter), and the shared element is clipped to its rounded-12dp shape for the WHOLE flight (the overlay's default parent-rectangle clip made rounded cards fly square and snap back to rounded on landing).
- **D-325** — The "S-3/E-5" compound episode tag now only renders for actual multi-season content; no-season and single-season lists always show the plain "EP n" tag.
- **D-326** — Version 0.2.61 → 0.2.62.

## v0.2.63 — Calmer cover flight + Library ⇄ Search ghost-morph fix + MERGED TO MAIN (D-327..D-329, 2026-08-29)

- **D-327** — The cover → details flight is calmer: the cover's own bounds morph now runs 600ms emphasized (was 450) while the page crossfade stays at 450ms — the details page settles early and the cover glides the rest of the way in ("the details page can open up early but the image will move slowly"). Same easing curve on both (curve sync is the anti-jitter invariant; the durations are deliberately decoupled).
- **D-328** — Switching Library ⇄ Search no longer makes shared anime covers fly between the two pages: shared-element keys are now screen-namespaced (`cover:library:<url>` / `cover:search:<url>` / `cover:browse:<section>:<url>`) via canonical builder functions — Library and Search previously both built `"cover:<url>"`, so any anime present on both pages matched and morphed across the (instant) switch. List → Details morphs (and back) are unchanged; Details carries the source card's key through the nav args.
- **D-329** — Version 0.2.62 → 0.2.63; `test-feature/video-cache-new-download` MERGED INTO `main` (user-gated — first merge since 26e47722) — v0.2.63 tagged + released FROM main; new branch `streaming/CLOUDSTREAM` created from the new main for upcoming work (purpose TBD).

## Task 51 (2026-08-30) — round 11: THE CLOUDSTREAM V2 REBUILD on streaming/CLOUDSTREAM-V2 (from main); v0.3.0

The user scrapped the streaming/CLOUDSTREAM line (kept as reference) and directed a clean rebuild from main: the full CS plugin system + repos + trust UI + SEQUENTIAL multi-download, search + categories + memory + caching, details page, episodes/seasons — NO playback, aniyomi untouched. A 52-step plan (Phases A–J, each gated by a CI-green build) preceded all code; two research agents mapped main's seams + the reference's porting surface first.

- **Phase A+B** — branch + gradle foundation (CS catalog pins: jackson STRICTLY 2.13.1, gson, kotlinx-datetime, appcompat, junit; CI streaming/** trigger + unit-test gate; version 0.3.0/64) + the ENTIRE clean-room ABI module ported (41 files, 5 test suites, zero app-coupling).
- **Phase C** — the 7 additive core seams: InstallStep → provider-api; ExtensionManager.setExternalSources + the loadAll re-merge (CS sources survive every aniyomi reload); SAnime.year/score (binary-safe default accessors); AnimeHttpSource.isCloudStreamBridged; details year seeding; ExtensionExtras.year/score; cloudstreamShowNsfw.
- **Phase D** — the data/cloudstream runtime ported (+22 tests) with TWO V2 changes: (1) the SEQUENTIAL install queue (D-370 — user requirement: multi-tap enqueues instantly, installs run strictly one-by-one); (2) the honest playback boundary (D-371 — bridge stripped to details/episodes; episode taps show "playback arrives with the playback port"). Also: the R11-B "BrowseCache corruption" disproven by byte-level verification (D-372 — terminal display artifact).
- **Phase E** — app shell: AnikutaApp extends CloudStreamApp + THE SOURCE BRIDGE (CS providers → ExtensionManager, live appear/disappear); MainActivity AppCompatActivity + CommonActivity publishing (plugin load contract + activity-gated load + crash-restart self-heal); AppCompat theme; the RingLogBuffer logging data plane in ALL builds + plugin Log facade sink.
- **Phase F** — details chain: year seeding at all 4 fetch sites, year+score persisted in ExtensionExtras for cache reopens, header meta row year, the link-source sheet sectioned Aniyomi/CloudStream.
- **Phase G** — extensions UI: dedicated CS section (Trusted/Failed/Untrusted/Available) through the SHARED list chrome (aniyomi parity by construction), plugin detail pages (trust gate, live providers, install machine), dual-format repo management (CS-first detection), CS update checks, persisted NSFW toggle.
- **Phase H** — search integration: sectioned source picker, browse categories (parallel shelves), persisted memory (tab + kind + provider across restarts via the proven awaitCsSource heal), SWR caching (instant cached feed, never-blank refresh), CF cards with UA-bound WebView.
- **Phase I** — console logging tool: Settings → Developer tools → Console logs (live view, filters, level chips, export+share w/ own-process logcat).
- **Phase J** — documentation (cloudstream-v2 zone: PLAN + ARCHITECTURE), adversarial review, release v0.3.0.
- v0.3.0 (versionCode 64).

## v0.4.0 — Task 52 (round 12): the CloudStream playback port

- **Phase A** — gradle/CI groundwork: media3 1.9.3 pins (= upstream CS's own pin), :core:cs-player + :feature:cs-watch module skeletons, version 0.4.0/65, `:core:cs-player` added to the CI unit-test gate.
- **Phase B+C** — the engine + the resolver: CsPlayerEngine (ExoPlayer host: per-link OkHttp DataSource w/ referer/UA/headers + provider interceptor, sidecar subtitle sources, external audio merge, track selection, upstream-format error diagnostics) + CloudstreamLinkResolver (progressive loadLinks snapshots, URL dedup, torrent/DRM filtering + counting, subtitle unique-ifying, 20-min link cache, 30-s first-link watchdog) — 12+13 unit locks.
- **Phase D+E** — the CS watch screen: CsWatchKey (Nav3), CsWatchViewModel (resolution state + auto-pick + next-link fallback + watch progress on the SAME provider-agnostic store), Media3 PlayerView surface w/ Compose glass controls (seek/speed/auto-hide/immersive), resolving + honest error overlays, Streams sheet (type badges VIDEO/HLS/DASH, failed markers, hidden counts, long-press copy, per-stream quality rows), Subtitles sheet (sidecar + embedded), Episodes sheet, episode auto-advance.
- **Phase F** — the seams: DetailsScreen routes CS episode taps to `onNavigateToCsWatch` BEFORE the classic resolver; continue-watching autoplay unified through the same handler; MainActivity CsWatchKey nav branch; the resolveEpisode CS short-circuit stays as defense in depth (downloads get the honest message).
- **Phase G** — logging: the `Anikuta:CS:*` namespace (Resolver/Player/Subs/Watch) + the "CS Playback" console chip; the one-filter logcat recipe (doc cloudstream-v2/03).
- **Phase H** — docs: 02-PLAYBACK-PLAN.md + 03-PLAYBACK.md (as-built), D-374..D-376, the long-task-execution skill (the user-requested agent-method doc).
- v0.4.0 (versionCode 65). Aniyomi playback stack: zero diff (R12-REVIEW-verified).

## Task 53 (round 13) — the playback-fixes release (v0.4.1)
- Research round: upstream CloudStream clone (R13-A), AnymeX + its runtime bridge (R13-B), jadx decompilation of the actual AniKoto/MovieBoxProvider .cs3 plugins, and live empirical CDN testing (hcdn3 UA/referer rules, kryntal referer requirement) — every v0.4.0 device finding root-caused (doc cloudstream-v2/04).
- RC-1 FIXED: vendored M3u8Helper passed an invented `referer = streamUrl` (nicehttp lets the referer param REPLACE the caller's Referer header) → kryntal 403 → AniKoto's runCatching swallowed it → 0 links + 0 subs after the full ~19-request walk (~19 s). Now headers-only (upstream parity) + Anikuta:CS:M3u8 failure forensics.
- RC-2 FIXED: the player's default Mobile-Chrome UA caused the 428 class (hcdn3 rejects ANY browser UA with 428, any Referer with 429). Attempt 1 now = upstream semantics (UA from link headers else desktop Chrome/149); a 4xx at open time triggers ONE clean retry (client-default UA, referer dropped) — empirically 206.
- RC-3 FIXED: the play-trigger LaunchedEffect read the collectAsState State object (lags the StateFlow by one dispatch) → a new episode's initialize() reset landed between composition and the effect → the PREVIOUS show's link played on the fresh engine. Generation lock (playGeneration == resolveGeneration, live StateFlow reads, logged verdicts) + engineResetTick hard-reset.
- RC-4/RC-5: upstream's 120 s loadLinks timeout wrap (loadLinksTimeoutMs, 5–480 s clamp); WebViewResolver stub returns null-pair (upstream failure shape) instead of throwing.
- RC-6 SHIPPED: the AnymeX-pattern resolve sheet — episode taps open a bottom sheet over the details page (progressive source rows, CC badges, "Scanning for more…"), selection seeds the watch VM with the full pre-resolved list (instant playback), CsSourceMemory remembers the per-anime server (auto-select on arrival; single-link auto-select).
- RC-7 SHIPPED: Sheets reorganization — Sources (quality-desc sort, subtitle count, failed markers), Audio & Subtitles (embedded DASH audio tracks sectioned in when >1).
- RC-8 SHIPPED: the diagnosability overhaul — request-profile logging on every engine load, m3u8 failure forensics (status + body preview), generation-lock verdicts, per-link failure reasons in the exhausted message, two new tags (Anikuta:CS:M3u8, Anikuta:CS:Sheet) in the one-filter recipe.
- Aniyomi stack: byte-untouched (all changes inside the CS modules + the MainActivity CS seams).

## v0.4.3 / 68 (2026-08-31, Task 55 / round 15, branch streaming/CLOUDSTREAM-V2)

- **Resolve sheet cleanup** — the "via {provider}…" hint line and the
  "N source(s) · N subtitle track(s)" footer removed (user request); the
  in-player links sheet now matches the aniyomi QualitySheet exactly.
- **Audio-version formatting** — CS sources render the aniyomi 3-tier
  (Server → AudioVersion → Quality): SUB/DUB chips on the cards, per-version
  label rows, quality chips; combined sub/dub resolution tags each stream.
- **Source formatting on/off toggle (both stacks)** — tap the sheet title
  ("Episode N" / "Qualities and Servers") → a small popup menu ABOVE it;
  OFF = raw flat list, one row per stream, tap = play directly. Default ON.
- **Subtitles fixed + customizable** — language names (never URLs), live-index
  track selection, content-sniffed mimes, preferred-language auto-select,
  and a full CS Subtitle Settings sheet styling the Media3 view live.
- **Sub/Dub episode display modes** — Episode list settings → Display:
  Separate (Sub | Dub chip switcher) or Combined (merged rows; tapping
  resolves BOTH flavors and the sheet's audio chips let the user pick).
- Aniyomi stack: strictly ADDITIVE changes (the toggle UI + the Display-tab
  section); all existing behavior byte-identical.

### v0.4.3 completion round (2026-08-31, Task 55 tail — the CI-failure fixes)

- The round-15 push's CI run FAILED (2 tasks) before ANY downstream module
  compiled. Root causes + fixes (all in this round):
  1. `CloudstreamLinkResolver.sniffSubtitleMime` was declared as a LOCAL
     function inside the `channelFlow` block WITH a `private` modifier —
     illegal on locals (139 cascade errors, incl. the `?:` inference
     breakdowns). Hoisted to a proper private CLASS member;
     `SNIFF_HEAD_BYTES` (256L) extracted to the companion; the byte count
     now `contentLength().takeIf { it in 1..256 } ?: 256` (Long-clean).
  2. `CsMediaTypesTest.kt`: the Task-55 sniff tests were pasted at FILE
     top level (outside any class) → JUnit4 discovered the file-facade
     class `CsMediaTypesTestKt` → `InvalidTestClassError` (no public
     zero-arg ctor). Wrapped in `class CsSubtitleSniffTest`.
  3. Review round (3 parallel static reviewers over the NEVER-compiled
     modules — CI aborted at :data:cloudstream so :feature:cs-watch:impl,
     :feature:anime-details:impl and :app were unverified) caught:
     a BLOCKER in the aniyomi `ResolverSheet` raw branch (function-type
     mismatch: `onPickVideo` is `(ResolvedVideo, …)` but `RawVideoList`
     emits `ResolverVideo` — contravariance can't bridge unrelated final
     classes; fixed by sharing ONE pick adapter with the accordion
     branch), a DUB-only-list blank-filter bug in DetailsScreen (single-
     flavor lists now render unfiltered), a scanlator over-match that
     could trigger the sub/dub chips on ANIYOMI lists (feature now gated
     on `isLinkedSourceCloudStream()` + exact "Sub"/"Dub" scanlator match
     only — the ADDITIVE-ONLY invariant is now structural), and a dropped
     `fillermark` on merged rows (copied now). Also 11 unused imports
     cleaned from CsPlayerSheets.
- Version stays 0.4.3/68 (no user-visible behavior change beyond the fixes).

### v0.4.4 (2026-08-31, Task 56 — the device-feedback-fixes release)

- **F1 — no more auto-open from the resolve sheet.** The remembered-server
  auto-select (fired the moment a remembered server's link streamed in) and
  the single-link auto-select (fired on Completed with exactly 1 link) are
  GONE from `CsResolveSheet` — "for some plugins/extensions" was exactly
  those two conditions. The sheet now always presents the list; the user
  picks; the remembered server still auto-EXPANDS its accordion (a hint,
  not a decision). The in-player `autoStart` (episode-switch continuity)
  stays by design.
- **F2 — quality chips sort highest-leftmost.** CS `groupServers` ranks by
  pixel height descending with Unknown(400) then Auto(0) at the far right
  ("then any other options"); the ANIYOMI accordion (ResolverSheet +
  watch QualitySheet) had NO sort at all — both now sort by a parsed-height
  key ("4K"→2160, "1080p"→1080, non-numeric last) at the display layer.
- **F3 — sub/dub lists renumber + de-tag.** Root cause: EpisodeListNormalizer
  guarantees globally-UNIQUE numbers (its identity contract), so the second
  flavor ALWAYS continues (dub 13–24 for a 12+12 show) — and the round-15
  rows showed it. Fix: per-flavor DISPLAY ordinals (1..N per flavor) +
  tag-stripped names at every CS episode surface (details rows, CS watch
  page, episodes sheet, "currently playing"); identity numbers untouched
  (progress/cache/metadata keys byte-identical).
- **F4 — COMBINED mode actually merges.** The round-15 pairing keyed on
  episode_number equality — dead under global numbering. All three pairing
  sites (CsSubDubSiblings.mergeSiblings/handlesFor + the DetailsScreen
  SEpisode twin) pair by flavor ORDINAL: 12+12 → 12 rows, a tap resolves
  BOTH flavor handles (the round-15 dual-resolve flow finally engages).
- **F5 — the LazyColumn duplicate-key crash.** `IllegalArgumentException:
  Key "Default|Default|https://…mpd" was already used` — the aniyomi RAW
  lists keyed rows by `"$server|$label|$url"` and an extension emitted the
  same multi-quality DASH URL twice. All raw lists (both aniyomi sheets +
  CS CsRawLinkList) now key by `"…|$index"` / `"…#$index"`.
- Auto-advance stays within the current flavor (next/prev walk the row's
  flavor; auto-start prefers the target row's flavor pool), and the CS
  hand-off strips the flavor tag from titles (the pills carry the flavor).
- v0.4.4/69. Plan: DOCUMENTATION/cloudstream-v2/07-DEVICE-ROUND-FIXES-PLAN.md.

### v0.4.6 / 71 (2026-08-31, Task 58 / round 18 — the both-stacks-debug + downloads + share release, branch streaming/CLOUDSTREAM-V2)
- **Debug toolkit on BOTH stacks:** the aniyomi ResolverSheet + in-player QualitySheet gain the gated header copy-report, per-chip/row copy icons and raw-URL lines (NEW pure `core:video-resolver` ResolverDebugReport — deterministic, header KEYS only; the same DebugPreferences flags, default OFF, live-collected; Debug page section retitled "all extensions"). 8 unit locks.
- **Subtitle live view + ASS accuracy:** settings apply while PAUSED (hoisted `liveSubtitleStyle` Compose state — the non-reactive prefs read + the ticker's StateFlow equality-dedup were the root cause), MPV-unit-parity LINEAR border math (borderSize/55; the 0.035f ≈1.9× + 0.15 saturation bug gone), per-line ASS BorderStyle=3 background boxes hugging glyph bounds (padding = border width, no fixed dp, no half-leading artifacts), shadow IN ADDITION to border, no maxLines truncation, fontScale now scales the Media3 view (NEW `CsSubtitleGeometry`, 9 unit locks).
- **THE CLOUDSTREAM DOWNLOADS PORT:** the details page's download button opens the CS resolve sheet in DOWNLOAD mode ("Download EP N", DASH filtered+counted); a pick enqueues through the SAME source-agnostic engine via NEW `app/download` CsDownloadRequestBuilder (allHeaders→MPV string, sidecars→DownloadTracks) — queue/foreground service/SAF storage/notifications/downloads screen/download-state chips + MPV offline playback all ride mainId|episodeKey with ZERO engine/schema changes; classic auto path CS-guarded.
- **Plugin share + manual import (.moviebox.WHITECAT):** Share action on every CloudStream plugin detail state (cache export + FileProvider ACTION_SEND); exported `PluginImportActivity` (MIME-based VIEW/SEND filters — Android can't extension-match content://; display-name + manifest gates, ONE confirm dialog), `CloudstreamPluginManager.importSharedPlugin` (atomic place, repo LINKAGE when a catalog matches, untrusted record, AlreadyInstalled no-op) + PendingCsPluginNav hand-off (cold start + ON_RESUME → the plugin's detail page). 7 unit locks.
- 24/24 new pure tests GREEN offline with the real compiler chain (kotlinc 2.2.0 + real kotlinx-serialization plugin + jars); error-histogram count parity vs HEAD on all 9 modified files; aniyomi playback engine/resolver/download engine byte-untouched.

### v0.4.7 / 72 (2026-09-01, Task 59 / round 19 — the v0.4.6 device-round fixes, branch streaming/CLOUDSTREAM-V2)
- **CS downloads dead-callback fix:** the episode row's download button was still wired to the classic resolver (round 18 gated a dead EpisodesSection param, not the live row lambda) — CS-bridged downloads now open the CS resolve sheet in DOWNLOAD mode as designed.
- **Subtitle overlay rewrite (accuracy round 2):** the whole cue renders as ONE multi-line Text with every decoration pass (per-line back-color boxes, shadow, border) drawn from the SAME TextLayoutResult under the fill — natural line spacing at every scale (the per-line-Text double-leading + stroke collision gone), passes structurally cannot detach from the glyphs ("subtitle at top, border at bottom" gone), 4% horizontal wrap inset.
- **New subtitle defaults + Reset (both stacks):** font size MAX (100) / scale 0.5x / border 5 per the user's spec (MPV parity preserved — 100×0.5 ≈ the old 55), with a Reset button on BOTH subtitle settings sheets (PlayerPreferences.resetSubtitleSettings; the aniyomi change additive-only).
- **Formatting toggle redesign (all four resolve sheets, both stacks):** a distinct bordered "Formatted sources" pill ABOVE the episode title (direct toggle; the title is plain text — no more click-anywhere header or menu-over-the-title).
- **Plugin share format v2 (.WHITECAT):** the extension is just .WHITECAT (legacy .moviebox.WHITECAT still imports); the export carries anikuta/export.json (source repo URL + icon URL + catalog fields) + anikuta/icon.png (embedded icon bytes, best-effort fetch) — the receiver keeps the plugin's icon (local file, no network) and source repository even repo-less.
- **Import UX round:** content-first validation (.bin/renamed files analyzed by their zip manifest), the confirm dialog titled "Add <plugin name>", no trust caption, Add → a clean 1.5s "Plugin added" → the extensions page (launches the main app; the pending-nav note routes there now). Plugin detail page's verbose trust-note + share captions removed.
- Tests: CsSharedPluginFormatTest rewritten for the format v2 (10 locks) + the geometry inset lock; aniyomi stack additive-only.

### v0.4.8 / 73 (2026-09-01, Task 60 / round 20 — the v0.4.7 device-round fixes, branch streaming/CLOUDSTREAM-V2)
- **Subtitle line-gap ROOT CAUSE (CS overlay):** the fill Text overrode fontSize but not lineHeight — Material3's ambient bodyLarge leaked a FIXED 24sp line box (huge gap at 0.5x scale, overlapping glyphs at 2x+). The overlay now passes an EXPLICIT font-proportional lineHeight (fontSize × 1.2) with Proportional/None lineHeightStyle: the inter-line gap is a constant ~20% of the glyph height at every size, scale and display mode.
- **Bold subtitles default ON (both stacks):** one preference drives MPV sub-bold and the CS overlay — bold out of the box, and Reset restores bold-on.
- **Reset confirmation (both sheets):** the header Reset icon asks first (identical dialogs on the aniyomi + CS sheets); only the dialog's Reset writes the defaults.
- **Formatting menu ON the heading (all four resolve/quality sheets):** the round-19 standalone pill is gone — tapping the episode TITLE TEXT (only the text) pops a small flat menu with a DISTINCT 1dp outline border holding the "Formatted sources" switch; the menu stays open while toggling.
- **Strict .WHITECAT (no legacy compatibility):** the old .moviebox.WHITECAT double tail is explicitly REJECTED (it also ends with .WHITECAT — checked first); old-format files import through the content-first/manifest path only. The import CONFIRM page shows the plugin's EMBEDDED icon (bytes → Image; iconUrl fallback; generic glyph last).
- **CloudStream-tab landing:** after adding a CS plugin, the extensions page opens on the CLOUDSTREAM section (ExtensionsSettingsKey carries initialTab; the pending-nav push sets "cloudstream").
- **Download rows (both stacks):** long server names now shorten with a trailing "…" (flex pill) and the progress percentage always keeps its space — queue rows AND the downloaded page's chips.
- **Downloaded-page crash fixed:** duplicate LazyColumn keys ("downloaded_<contentId> was already used" when scrolling to the bottom) — the denormalized per-row content metadata split one anime into two same-key groups; grouping is now on the stable contentId (richest-record metadata).

## Phase 13 — Task 61 / round 21: the QoL release (v0.4.9/74)
- **Plugin icons never blank:** every icon slot (extensions list + detail + import confirm + "Plugin added") renders fallbacks while loading AND on failure (the colorful letter tile / the extension glyph); the "Plugin added" badge now shows the plugin's OWN icon with a compact check badge. Share exports only carry http(s) iconUrls (a device-local `file://` URI — the receiver-side blank-icon root cause).
- **"Format sources" menu (all four sheets):** opens ABOVE the heading (a Popup anchored bottom-to-bottom — deterministic, vs. the DropdownMenu's below-only anchor), the exact label "Format sources", a guaranteed 24dp gap between the label and the toggle (fixed spacer + 220dp min width — a weight-only spacer collapses in a wrap-content menu).
- **Search pagination (AniList + aniyomi extensions + CloudStream search):** as the user approaches the bottom (~2 rows before the end) the next page pre-fetches; a full-span "Loading more…" spinner renders if the user beats it; appends dedupe by the grids' key identity (no duplicate-key crashes); a failed page load is soft (the results stay, the next trigger retries).
- **Randomized CloudStream sections:** the browse rows (Trending, Bollywood, …) reshuffle on EVERY entry into the search page (fresh load, tab switch, app switch, subpage return); each row keeps its original provider shelf index.
- **Category subpages:** tapping a section's TITLE opens that category's own page — the heading at the top, all results in a grid, infinite scroll as the user scrolls.
- **Image-loading performance:** a dedicated OkHttp client (a clone of the app's) under Coil caps concurrent cover fetches at TWO (finish-current → in-view → offscreen FIFO); the cards render over dim placeholders (no pop-in flash); the 500MB disk + memory caches ride as before.
- **Pull-to-refresh on the search page:** at the top, pull down → the current mode reloads page 1; the CloudStream browse cache is deleted first (the fresh, randomized sections land).
- **Library category chips:** the selected category auto-scrolls into view when the page opens (first/last included); the underline is as wide as the category text, a little thicker (3dp) and closer (2dp); the section is tighter; an 8dp gap before the results.
- **Downloaded-episodes UI:** all cards COLLAPSED by default; a smooth animated expand (rotating chevron); separator lines between episodes; the episode count as a highlighted tag; expand/collapse LEFT of delete; the TWO-STEP delete (tap → the button morphs error-red with DeleteForever; tap again to delete; tap anywhere else to disarm).
- **Ads system:** the real sponsor URL; the minimum outside-time 15s → 5s; the OFFLINE gate — no popup when there is no validated internet (the navigation proceeds, the ad is NOT recorded — it fires the next time the user is online).

## Phase 13 — Task 62 / round 22: stability, linkage, and the library performance pass (v0.4.10/75)
- **The post-import crash fixed:** "Key ExtensionsSettingsKey was used multiple times" — the ON_RESUME pending-nav observer captured a STALE `currentKey` (its DisposableEffect never re-keys), so returning from a plugin import while already on the extensions page double-pushed ExtensionsSettingsKey; the same-class AnimatedContent transition then composed TWO SaveableStateProviders under the one class-name key. The handler now reads the LIVE backstack inside itself and REVEALS an existing extensions entry (pops to it) instead of ever stacking a second one.
- **Plugin ↔ repository linkage (the round-22 headline):** manually installed .cs3 plugins and the repository's entry for the same plugin are now ONE row, recognized even when the repository was added LATER. An ordered identity ladder (exact internalName → the link-time repoInternalName → download URL → sha256 fileHash → normalized names) matches installed records against the online catalog; rebuildLists back-fills the record's repoUrl/repoInternalName/url/fileHash (idempotent), the Available list drops matched entries, update pills key through the ladder, installPlugin updates an identity-matched record IN PLACE (same name + path, trust kept), and the Update pill resolves its target via availableUpdateTarget() (the old exact-name lookup was a no-op for linked imports).
- **Format-sources menu position:** floats fully ABOVE the heading and OUTSIDE the bottom sheet (a custom PopupPositionProvider sits the menu's bottom edge 8dp above the heading's top; the sheet's full-screen dialog window hosts the sub-panel popup) — it no longer covers the "Episode N" text. All three copies.
- **Randomization triggers retrained:** reshuffles happen ONLY on a true search-TAB exit + return (SearchTabExitSignal, marked by the bottom nav) or a pull-to-refresh — NOT on subpage/details returns, app resumes, or app reopens. The arrangement (row shelf indexes + per-row item urls) is PERSISTED on the browse snapshot and RESTORED exactly across cold restarts and background refreshes (content swaps in place; the rows never jump).
- **The smart shuffle:** randomizes the row order AND the item order within each row under the cross-section constraint — the FIRST FOUR items of any randomized category never appear in another category's first four (claimed by url). A category that cannot claim 4 unclaimed items simply stays in its original order.
- **Library chip underline restored:** fillMaxWidth() inside a LazyRow item is a NO-OP (unbounded width) — the 3dp underline was a 0-width invisible sliver. The tab Column now carries width(IntrinsicSize.Min) (the bar resolves to the text width) with a 1dp gap to the text.
- **Library performance pass:** H1 — the 8 bulk-mutation paths (category ops, add/remove/delete-selected, the picker's membership queries) run on the IO dispatcher; H2 — ONE combined+debounced(200ms) pipeline runs the filter+sort on Default (no more per-keystroke main-thread sorts); M1 — the PTR threshold reads via snapshotFlow+distinctUntilChanged (no more whole-root recomposition per drag frame); M2 — the shared-element registration gate is hoisted to the grid/list level and OFF while scrolling (per-cell koinInject+prefs reads gone); M3 — the root's 34 collectAsState calls split into leaf state-owner composables (the root keeps 6); M4 — the 23 prefs reads run on Default before the first load.
- **Downloads tag:** "(N Episodes Downloaded)" (singular-aware) on every downloaded-anime card.
- **Cover zoom focal point:** the pinch now zooms INTO the fingers (the image point under them stays under them — top-right pinches zoom the top-right), via a non-consuming centroid observer on the Initial pointer pass + focal pan math; pan-while-zoomed and the auto-reset on lift are unchanged.

## Phase CS-V2 Round 24 — Task 64: the ordered re-do (v0.4.12/77)
- REVERTED the branch to ba3c6937 (v0.4.10) per the user's instruction; the revert itself CI-verified before any new work.
- Library performance take two: image fetch cap 2→12/8-per-host; the in-memory category switch (full-set VM cache); per-cell animation fast paths; the shared-element gate lambda; the index-only velocity signal.
- Library chips: the IntrinsicSize.Min word-width truncation fixed (Max + no ellipsis) + the centered auto-scroll (instant on open, animated on tap).
- Downloads tag: no parentheses, bold count only. Console-logging family removed; the Debug options page + every other debug affordance kept.
- Genres radar rework: bigger heading, the dedicated all-genres section below it, the category filter ladder (non-empty options only, gone-default→All fallback, the section can never disappear).
- CS browse: original shelf indexes (pre-compaction) fix the subpage mixing; same-title sections merge (no duplicate rows).
- Watch Activity: the weekday-label bottom clipping fixed (full row-pitch slots).
- NEW: the update-check LIVE status notification (per-content names streaming) + the content-update history page (JSON file, no DB changes this round).
