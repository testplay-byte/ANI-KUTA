# Feature Modules Analysis

> Deep analysis of the `feature/` modules in the old ANIKUTA project at
> `/home/z/my-project/ANIKUTA-PROJECT/REFERENCES/old-kuta/ANIKUTA/`.
> Source code is read directly; this document summarizes findings for the
> rebuild team.

## Summary

The old ANIKUTA project ships 19 `feature/` modules. They fall into three groups:

- **Active feature modules (10):** `library`, `updates`, `history`, `browse`,
  `search`, `my`, `anime-details`, `episode-settings`, `video-resolver`,
  `watch`, `extensions-settings`, `settings` (UI-only), `trackers`, `backup`,
  `download`, `setup-wizard` — all have real Kotlin source files implementing
  Compose screens + (most) ViewModels.

- **Stub feature modules (4):** `home`, `more`, `episode-list`, `player` —
  reserved module slots with no source files (functionality lives elsewhere).
  All but `more` are NOT depended on by `:app`; `player` IS depended on by
  `:app` but as a no-op empty module.

- **Tally:** 14 of the 19 listed modules are active; 4 are empty stubs; 1
  (`more`) is an empty stub whose UI lives inline in `:app`'s `MainActivity.kt`.

**Architectural pattern (consistent across feature modules):**
1. **Compose screen** — top-level `@Composable` function exposed to `:app`.
2. **ViewModel** — `class XxxViewModel(...): ViewModel()` exposing a single
   `StateFlow<XxxState>`; constructor-injected dependencies (no `SavedStateHandle`).
3. **State** — immutable data class (often `@Immutable`); sealed interfaces for
   dialogs/sheets (exhaustive `when` in the screen).
4. **Koin module** — `feature/xxx/src/.../di/XxxModule.kt` with
   `viewModelOf(::XxxViewModel)` (and any feature-local preferences singletons).
5. **Components** — sub-composables in a `components/` subpackage.
6. **Navigation:** the app uses a hand-rolled state-machine nav host in
   `:app`'s `MainActivity.kt` (NO Voyager, NO Compose Navigation). Screens are
   rendered based on `var currentScreen: SomeScreen?` state. The Voyager
   migration is a deferred decision.

**Cross-cutting design rules (from DESIGN_LANGUAGE/):**
- `#B1F256` primary via `MaterialTheme.colorScheme.primary`.
- `RobotoFamily` font for all text.
- `surfaceVariant.copy(alpha = 0.4f)` card backgrounds with `RoundedCornerShape(12.dp)`.
- `CollapsingHeader` for page titles (collapses on scroll).
- Bottom sheets: `dragHandle = null` (design principle #2).
- No nested `LazyColumn` inside another `LazyColumn`.
- No indigo/blue colors.

## Module Index

| Module | Purpose | Status |
|--------|---------|--------|
| `:feature:library` | Library screen (anime grid + categories + continue-watching) | ✅ Active |
| `:feature:updates` | Updates feed + Schedule (calendar + list) | ✅ Active |
| `:feature:history` | History screen (day-bucketed watch progress) | ✅ Active |
| `:feature:browse` | Browse / home (trending anime grid) | ✅ Active |
| `:feature:search` | Dual-source search (AniList + extensions) | ✅ Active |
| `:feature:my` | My Profile (stats, genre radar, behind-status) | ✅ Active |
| `:feature:anime-details` | Unified anime details page (AniList + extension) | ✅ Active |
| `:feature:episode-settings` | 4-screen episode-display customization flow | ✅ Active |
| `:feature:video-resolver` | Video resolver bottom sheet (server/audio/quality) | ✅ Active (UI only — logic in `:core:video-resolver`) |
| `:feature:watch` | YouTube-style watch page (mini-player + episode list) | ✅ Active |
| `:feature:extensions-settings` | Extensions management (3-category) + repo settings | ✅ Active |
| `:feature:settings` | General/Appearance/Player/About/Ads settings | ✅ Active (README is stale — claims stub) |
| `:feature:trackers` | Tracker login/binding (AniList + MAL) | ✅ Active |
| `:feature:backup` | Backup & Restore UI (4 sections + Aniyomi restore) | ✅ Active |
| `:feature:download` | Downloads UI (queue + downloaded library + settings) | ✅ Active |
| `:feature:setup-wizard` | First-launch wizard (14 steps) | ✅ Active |
| `:feature:home` | Home screen | ⚠️ Empty stub (functionality in `:feature:browse`) |
| `:feature:more` | More tab | ⚠️ Empty stub (UI inline in `:app`'s `MainActivity.kt`) |
| `:feature:episode-list` | Episode list component | ⚠️ Empty stub (in `:feature:anime-details`) |
| `:feature:player` | Fullscreen player | ⚠️ Empty stub (overlay in `:core:player` + `:feature:watch`) |

---

## `:feature:library`

**Purpose:** The Library screen — the user's personal anime collection.
Renders a category-tabbed grid/list of favorited anime with continue-watching
section, sort/customize sheets, and selection-mode actions (move to category,
delete).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:anilist`,
`:core:preferences`, `:core:player` (`WatchProgressStore` for continue-watching),
`:core:download` (`DownloadManager` for "Delete Anime" downloads cleanup),
`:data:anime`. Coil 3 (image loading). Koin.

**Status:** ✅ Active. 11 source files (1 screen + 1 VM + 1 state + 1 prefs +
1 DI + 7 components).

**Key files:**
- `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryScreen.kt`
  — ~801 LOC. Top-level Compose screen. `CollapsingHeader`, `CategoryTabs`,
  `ContinueWatchingSection`, `LibraryGridCard`/`LibraryListRow` (per display
  mode), `SelectionActionBar`, search field, `SortSheet` + `CustomizeSheet`
  bottom sheets, `LibraryEmptyState`.
- `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryViewModel.kt`
  — ~468 LOC. Combines `AnimeRepository.observeFavorites` +
  `CategoryRepository.observeVisible` + `CategoryRepository.observeAllLinks` +
  `WatchProgressStore.changes` + `LibraryPreferences` into `LibraryState`.
  Selection-mode state (keyed by `Anime.id`). Sort + display mode are GLOBAL
  (Q2/Q3 decisions). Continue-watching derived from progress map.
- `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryState.kt`
  — `@Immutable data class LibraryState` + `CategoryFilter` sealed interface
  + `ContinueWatchingItem` + `LibraryDialog` sealed interface (CustomizeSheet,
  SortSheet, OptionsSheet, MoveToCategorySheet, DeleteConfirmation).
- `feature/library/src/main/java/app/confused/anikuta/feature/library/LibraryPreferences.kt`
  — Typed prefs wrapper (`PreferenceStore`-backed): display mode, columns
  portrait/landscape, sort type + ascending, show continue-watching, badge
  toggles + positions, show score, show total entries, title lines.
- `feature/library/src/main/java/app/confused/anikuta/feature/library/di/LibraryModule.kt`
  — Koin: `single { LibraryPreferences(get()) }` + `viewModelOf(::LibraryViewModel)`.
- `feature/library/src/main/java/app/confused/anikuta/feature/library/components/`
  — `CategoryTabs.kt`, `ContinueWatchingSection.kt`, `CustomizeSheet.kt`,
  `LibraryEmptyState.kt`, `LibraryGridCard.kt`, `LibraryListRow.kt`,
  `SelectionActionBar.kt`, `SortSheet.kt`.

**Key classes/interfaces:** `LibraryScreen`, `LibraryViewModel`, `LibraryState`,
`LibraryPreferences`, `LibraryDialog`, `CategoryFilter`, `ContinueWatchingItem`.

**UI components provided:** `LibraryScreen` (the screen), 8 sub-components.

**Notes:** Per Q5, continue-watching is a section at the top (removable). Per
Q6, NO status filter — the 5 keywords are category-name suggestions only.

---

## `:feature:updates`

**Purpose:** Updates feed (new episodes since last check) + Schedule tab
(upcoming airing episodes for library anime, with calendar + list views).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:anilist`,
`:core:preferences`, `:core:update-checker` (`UpdateChecker` + `UpdateResult`),
`:core:provider-api` (resolves `AiringScheduleProvider` via
`MetadataProviderRegistry`), `:data:anime`. Coil 3. activity-compose
(`BackHandler`). Koin.

**Status:** ✅ Active. 8 source files (1 screen + 1 VM + 1 state + 1 DI +
4 components).

**Key files:**
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/UpdatesScreen.kt`
  — Top-level screen. Two tabs: Updates (feed) + Schedule (calendar/list).
  Pull-to-refresh triggers `viewModel.checkForUpdates()`. Live "Currently
  checking" card during in-flight checks.
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/UpdatesViewModel.kt`
  — ~321 LOC. Collects `updateChecker.getLastResults()` reactively.
  `checkForUpdates()` triggers a fresh check. `fetchSchedule()` reads library
  favorites, chunks their AniList IDs into batches of 50 (AniList `id_in`
  practical max), asks the active `AiringScheduleProvider` (resolved via
  `MetadataProviderRegistry` — Phase 7 ADR-041) for each chunk's upcoming
  episodes, flattens into sorted `ScheduleEntry` list.
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/UpdatesState.kt`
  — `UpdatesTab` enum (UPDATES / SCHEDULE), `ScheduleViewMode` enum (LIST /
  CALENDAR), `ScheduleEntry`, `CheckProgressUi` sealed interface
  (Idle / Checking / Completed), `@Immutable data class UpdatesState`.
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/di/UpdatesModule.kt`
  — Koin: `viewModelOf(::UpdatesViewModel)`.
- `feature/updates/src/main/java/app/confused/anikuta/feature/updates/ScheduleCalendar.kt`,
  `ScheduleTabContent.kt`, `LiveCheckCard.kt`, `AudioBadges.kt` — sub-components.

**Key classes/interfaces:** `UpdatesScreen`, `UpdatesViewModel`, `UpdatesState`,
`ScheduleEntry`, `UpdatesTab`, `ScheduleViewMode`, `CheckProgressUi`.

**UI components provided:** `UpdatesScreen`, `ScheduleCalendar`,
`ScheduleTabContent`, `LiveCheckCard`, `AudioBadges`.

**Notes:** Per ADR-041, the schedule fetch now routes through the registry
instead of calling `AniListApi.fetchAiringSchedule` directly. Adding MAL/TMDB
later = one module + one Koin line. `anilistApi` is retained as a constructor
dep for fallback / future-proofing.

---

## `:feature:history`

**Purpose:** History screen — day-bucketed (Today / Yesterday / This Week /
Earlier) watch-progress entries. Tapping a row opens the anime detail page.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:player` (`WatchProgressStore` — the history data source), `:data:anime`
(for cover-url resolution from `content_id`). Coil 3. activity-compose. Koin.

**Status:** ✅ Active. 5 source files (1 screen + 1 VM + 1 state + 1 DI + 1
extra).

**Key files:**
- `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryScreen.kt`
  — Top-level screen with `LazyColumn` grouped by `HistorySection`. Pull-to-
  refresh, clear-history confirmation dialog.
- `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryViewModel.kt`
  — ~128 LOC. Observes `WatchProgressStore.changes` (NOT `HistoryRepository`).
  Parses `WatchProgressStore.parseKey` for content_id + episode number,
  resolves content_id → `Anime` via `AnimeRepository.getByContentId`. Bug fix:
  unlinked anime history rows are now openable (was broken with the old
  `anilistId`-keyed format — `onOpenAnime(0)` → error state).
- `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryState.kt`
  — `HistorySection` enum (TODAY / YESTERDAY / THIS_WEEK / EARLIER),
  `@Immutable HistoryEntry` (with `anime: Anime?` for orphaned entries),
  `@Immutable HistoryState`.
- `feature/history/src/main/java/app/confused/anikuta/feature/history/di/HistoryModule.kt`.
- `feature/history/src/main/java/app/confused/anikuta/feature/history/HistoryUpdatesMoreEntries.kt`
  — More-screen entry point.

**Key classes/interfaces:** `HistoryScreen`, `HistoryViewModel`, `HistoryState`,
`HistoryEntry`, `HistorySection`.

**UI components provided:** `HistoryScreen`, `HistoryUpdatesMoreEntries`.

**Notes:** **Important:** the History screen reads `WatchProgressStore`
(`:core:player`, JSON-in-prefs), NOT `HistoryRepository` (`:data:history`,
SQLDelight `animehistory` table). The SQLDelight table is dead code at runtime
today. The Phase 3 (ADR-050) content_id keying fixed a long-standing bug
where unlinked extension anime couldn't be opened from history.

---

## `:feature:browse`

**Purpose:** Browse / home screen — trending anime grid. This is the "Home"
bottom-nav tab. `:feature:home` is an empty stub; this module does the work.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:anilist`,
`:core:provider-api` (resolves `HomeFeedProvider` via
`MetadataProviderRegistry`), `:core:preferences` (`ThemePreferences` for
scroll-blur overlay toggle). Coil 3. Koin (GlobalContext.get().get<ThemePreferences>()).

**Status:** ✅ Active. 1 source file (no ViewModel, no state, no DI).

**Key files:**
- `feature/browse/src/main/java/app/confused/anikuta/feature/browse/BrowseScreen.kt`
  — ~371 LOC. Compose screen with `CollapsingHeader` + `LazyVerticalGrid` of
  `AnimeCard` (cover + title). Resolves the active `HomeFeedProvider` via
  `MetadataProviderRegistry.allForCapability` (NOT `forCapability` — the
  stale-while-revalidate pattern needs a non-suspend lookup for cached data).
  Loading/error/empty states. `onOpenAnime: (Int) -> Unit` callback for
  navigation.

**Key classes/interfaces:** `BrowseScreen`.

**UI components provided:** `BrowseScreen` (also serves as the "Home" tab).

**Notes:** Phase 7 (ADR-041) — no longer calls `AniListApi` directly. Adding
MAL/TMDB later = one module + one Koin line; this screen stays unchanged.
The screen reads `ThemePreferences.headerBlurEffect` to toggle the
`ScrollBlurOverlay` under the collapsing header.

---

## `:feature:search`

**Purpose:** Dual-source search — AniList (via `MetadataProviderRegistry` →
`SearchProvider`) + Extension (via `SourceMatcher`). Top bar with source
toggle + search field; results grid; recent searches (per-source); extension
linking flow when tapping an extension result.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:anilist`,
`:core:preferences`, `:core:source-api`, `:core:provider-api`,
`:data:extension` (`AnimeExtensionManager`, `SourceMatcher`). Coil 3.
kotlinx-serialization (for `RecentSearchesStore` JSON). Koin.

**Status:** ✅ Active. 14 source files (1 screen + 2 VMs + 2 data + 10 UI
components). NO README.md.

**Key files:**
- `feature/search/src/main/java/app/confused/anikuta/feature/search/ui/SearchScreen.kt`
  — ~315 LOC. Top-level screen. `SourceToggle` (AniList/Extension),
  `SearchTopBar` with `SearchBar`, `RecentSearchesCard` (per-source),
  `ResultsCard` (grid). When source=EXTENSION + blank query, shows
  `ExtensionResultsView` (Popular + Latest rows). When AniList result tapped →
  `onOpenAnime(id)`. When Extension result tapped → `onOpenExtensionResult`
  (starts linking flow).
- `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/SearchViewModel.kt`
  — ~647 LOC. The main search VM. `SearchSource` enum (ANILIST / EXTENSION),
  `SearchResult` sealed class (AniList wraps `UnifiedAnime`, Extension wraps
  `AnimeCatalogueSource` + `SAnime`). Debounces queries. Phase 7 — routes
  AniList search through `MetadataProviderRegistry` → `SearchProvider`.
- `feature/search/src/main/java/app/confused/anikuta/feature/search/viewmodel/ExtensionLinkingViewModel.kt`
  — ~206 LOC. The extension→AniList linking flow. `ExtensionLinkingState`
  sealed class (Loading / Linked / NeedsManualLink / GoWithoutLinking / Error).
  Checks `ExtensionLinkStore` cache first (instant if hit); else auto-searches
  AniList by the extension anime's title; if confident match → auto-link;
  else → `NeedsManualLink` (show `ExtensionLinkingSheet`).
- `feature/search/src/main/java/app/confused/anikuta/feature/search/data/RecentSearchesStore.kt`
  — Per-source recent-searches persistence (JSON via `PreferenceStore`).
- `feature/search/src/main/java/app/confused/anikuta/feature/search/data/SearchUiPreferences.kt`
  — UI prefs (selected source, last query).
- `feature/search/src/main/java/app/confused/anikuta/feature/search/ui/` — 10
  UI components: `SearchTopBar`, `SearchBar`, `SourceToggle`, `FilterSheet`,
  `RecentSearchesCard`, `ResultsCard`, `ResultAnimeCard`, `ExtensionResultsView`,
  `ExtensionLinkingSheet`, `ExtensionSourcePickerSheet`.

**Key classes/interfaces:** `SearchScreen`, `SearchViewModel`,
`ExtensionLinkingViewModel`, `SearchUiState`, `SearchSource`, `SearchResult`,
`ExtensionRow`, `ExtensionLinkingState`, `RecentSearchesStore`,
`SearchUiPreferences`.

**UI components provided:** `SearchScreen` + 10 sub-components.

**Notes:** No README.md. No Koin DI module file (manual `ViewModelProvider`
factory pattern via `viewModel { ... }` in `SearchScreen.kt`). The extension
linking flow is one of the most complex UX flows in the app — auto-link +
manual-link + go-without-linking paths.

---

## `:feature:my`

**Purpose:** The "My Profile" page (ADR-021) — personalized dashboard with
profile header, quick stats, genre radar chart, status distribution,
recently-watched, and a "Behind Status" tab. Pull-to-refresh re-fetches
AniList stats.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:player` (`WatchProgressStore`), `:core:tracker` (`StatsCalculator` +
`TrackerManager`), `:data:anime`. Coil 3. Koin.

**Status:** ✅ Active. 17 source files (1 screen + 1 VM + 1 state + 1 prefs +
1 DI + 12 components + 1 extra).

**Key files:**
- `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfileScreen.kt`
  — ~279 LOC. Two tabs: Main (profile header, quick stats, genre radar chart,
  status distribution, recently watched) + Behind Status (summary cards + behind
  anime list). Pull-to-refresh. Settings button → `CustomizationSheet`.
- `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfileViewModel.kt`
  — ~167 LOC. Observes `StatsCalculator.observeStats()` for local stats. When
  AniList is linked (`trackerManager.anilist.username`), fetches enriched
  `TrackerUserStats`. Per RULES §3: calls Repository/StatsCalculator only.
- `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfileState.kt`
  — `@Immutable data class ProfileState` with `effectiveDisplayName`,
  `effectiveAvatarUrl`, `displayStats`, `showAniListStats` helpers.
- `feature/my/src/main/java/app/confused/anikuta/feature/my/ProfilePreferences.kt`
  — User-customized name/avatar overrides (separate from tracker data).
- `feature/my/src/main/java/app/confused/anikuta/feature/my/di/MyModule.kt`.
- `feature/my/src/main/java/app/confused/anikuta/feature/my/components/` — 12
  components: `ProfileHeader`, `ProfileTabBar`, `QuickStatsRow`,
  `GenreRadarChart` (Canvas-drawn radar), `GenreChipsSection`,
  `GenreAnimeSheet`, `StatusDistributionSection`, `ScoreDistributionSection`,
  `DistributionChart`, `RecentlyWatchedSection`, `BehindStatusSection`,
  `BehindStatusTab`, `CustomizationSheet`, `EditProfileDialog`,
  `ChangeAvatarSheet`, `ResetStatsDialog`.

**Key classes/interfaces:** `ProfileScreen`, `ProfileViewModel`, `ProfileState`,
`ProfilePreferences`.

**UI components provided:** `ProfileScreen` + 12 sub-components (incl.
Canvas-drawn `GenreRadarChart`).

**Notes:** Per the README, "Skeleton (Phase 1)" — but the module is fully
implemented. Stale README.

---

## `:feature:anime-details`

**Purpose:** The **unified** anime detail screen — renders data from either
AniList OR an extension via the `AnimeDetailsProviderRegistry` translation
layer (doc 05). ONE screen serves both data sources; a three-dot menu
(`SourceSwitcherMenu`) switches the source in-place. Includes banner, info,
episodes section, source/extension switching, manual search, AniList search,
download control, episode download state.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:anilist`,
`:core:preferences`, `:core:episode-metadata`, `:core:source-api`,
`:data:extension` (`AnimeExtensionManager`, `SourceMatcher`). Coil 3.
activity-compose. Koin. Lifecycle (`runtime-compose` for
`collectAsStateWithLifecycle`).

**Status:** ✅ Active. 13 source files (1 screen + 1 VM + ~11 components /
sheets / helpers). NO `di/` package (VM is created via `ViewModelProvider`
factory in `AnimeDetailScreen.kt`).

**Key files:**
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailScreen.kt`
  — ~327 LOC. Top-level Compose screen. `MaterialTheme(dynamicScheme)` wrap
  preserved for Phase 9 adaptive theming (cover color → color scheme). Entry
  modes: AniList (`animeId != null`), Extension (`extensionSource +
  extensionSAnime != null`), Library-no-source (`extensionSourceId != null` —
  for library anime whose source extension was uninstalled).
  `forceInitialRefresh` flag for the post-unlink fresh-fetch flow.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt`
  — ~1013 LOC. The largest file in any feature module. Source-agnostic —
  holds a `DetailsRequest` + `currentDataSource`, calls
  `registry.forSource(currentDataSource).load(request)`. In-place source
  switching via `switchDataSource` / `switchExtension`. Library keying: linked
  anime by `anilistId`, unlinked by `sourceId + url`. Episode metadata
  enrichment, watched flags, download state.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/DetailBanner.kt`
  — Cover banner with gradient + title.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/DetailInfo.kt`
  — Description, genres, score, status, next airing.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/DetailContent.kt`
  — Combines banner + info + episodes into the scrollable content.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodesSection.kt`
  — ~1022 LOC. Episode list with thumbnails, titles, summaries, dates, audio
  pills, download buttons, seen/bookmark flags, click-to-play.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/SourceSwitcherMenu.kt`
  — ~269 LOC. Three-dot dropdown menu: switch data source (AniList↔Extension),
  switch extension, link/unlink from AniList, manual search, refresh.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/ManualSearchSheet.kt`
  — Bottom sheet for picking a different extension source for the anime.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AniListSearchSheet.kt`
  — Bottom sheet for manually searching AniList to link an extension anime.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/EpisodeDownloadControl.kt`,
  `EpisodeDownloadState.kt`, `EpisodeStates.kt` — Download button + state.
- `feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/WatchEpisodeContext.kt`
  — `data class` carrying anime title + cover URL + cover color + episode
  metadata map from details → watch page.

**Key classes/interfaces:** `AnimeDetailScreen`, `AnimeDetailViewModel`,
`WatchEpisodeContext`.

**UI components provided:** `AnimeDetailScreen` + ~11 sub-components.

**Notes:** This is the most complex screen in the app. The unified
AniList+extension rendering is a key differentiator from the Aniyomi
reference. The `:feature:episode-list` stub exists because the original plan
was to extract this into its own module, but it ended up tightly coupled to
`AnimeDetailViewModel` + `EpisodeDisplayPreferences`. The same `EpisodesSection`
composable is reused on the watch page.

---

## `:feature:episode-settings`

**Purpose:** The 4-screen episode-display customization flow: Hub (live
preview + 3 links), Display (show/hide toggles), Layout (position knobs),
Metadata (fetch toggles).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`
(`EpisodeDisplayPreferences` + `EpisodeMetadataPreferences` — moved to
`:core:preferences` in Phase 8), `:core:episode-metadata`. activity-compose
(`BackHandler` + system bars padding). Lifecycle. Koin. Coroutines.

**Status:** ✅ Active. 9 source files (4 screens + scaffold + components +
preview + remember-hook + page-sealed-interface). NO `di/` package.

**Key files:**
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeSettingsPage.kt`
  — `sealed interface EpisodeSettingsPage` with 4 variants: Hub, Display,
  Layout, Metadata. Drives the hand-rolled nav state in `MainActivity.kt`.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeSettingsHubScreen.kt`
  — Hub: sticky `EpisodeRowPreview` at top + scrollable 3-row list (Display /
  Layout / Metadata) below.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeDisplaySettingsScreen.kt`
  — Show/hide toggles for episode-number, titles, summaries, thumbnails,
  dates, audio pills + title max-lines.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeLayoutSettingsScreen.kt`
  — Position knobs: thumbnail side, title position, synopsis position, date
  position, episode-number position, thumbnail size.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeMetadataSettingsScreen.kt`
  — Master toggle + per-field fetch toggles.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/SettingsScaffold.kt`
  — Shared sub-page scaffold (back arrow + title + content).
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/SettingsComponents.kt`
  — `SwitchSettingsRow`, `SegmentedRow` reusable components.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/EpisodeRowPreview.kt`
  — Live preview of an episode row reflecting current prefs.
- `feature/episode-settings/src/main/java/app/confused/anikuta/feature/episodesettings/RememberEpisodeDisplayPrefs.kt`
  — Composable hook reading `EpisodeDisplayPreferences` reactively.

**Key classes/interfaces:** `EpisodeSettingsPage`, `EpisodeSettingsHubScreen`,
`EpisodeDisplaySettingsScreen`, `EpisodeLayoutSettingsScreen`,
`EpisodeMetadataSettingsScreen`, `SettingsSubpageScaffold`,
`SwitchSettingsRow`, `SegmentedRow`, `EpisodeRowPreview`.

**UI components provided:** 4 screens + scaffold + components + preview +
remember-hook.

**Notes:** Phase 8 (Doc 04 violation 2) — removed the dep on
`:feature:anime-details` by moving `EpisodeDisplayPreferences` +
`EpisodeDisplayPrefs` to `:core:preferences`. **Watch-page episode list has
its own SEPARATE preferences** (`PlayerEpisodePreferences` in `:core:player`)
per user requirement: "the details page and the watch page would be
customizable separately."

---

## `:feature:video-resolver`

**Purpose:** The video resolver bottom sheet — appears after tapping an
episode. 3-tier hierarchy: Server (expandable) → Audio → Quality. UI only —
the resolver logic + types live in `:core:video-resolver` (Phase 8 fix).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:source-api`,
`:core:video-resolver` (Phase 8 — logic + types moved here). activity-compose.
Coroutines. NO Coil, NO Koin.

**Status:** ✅ Active (UI only). 3 source files.

**Key files:**
- `feature/video-resolver/src/main/java/app/confused/anikuta/feature/videoresolver/VideoResolverSheet.kt`
  — ~130 LOC. Bottom sheet with no drag handle (design principle #2),
  partial height (max 70% screen). Renders `VideoResolverState`:
  Resolving (spinner), Show (expandable server accordion with audio/quality),
  NoSources ("No video sources available" + install hint), Error (message +
  retry), Hidden.
- `feature/video-resolver/src/main/java/app/confused/anikuta/feature/videoresolver/ResolverServerContent.kt`
  — The expandable server accordion item.
- `feature/video-resolver/src/main/java/app/confused/anikuta/feature/videoresolver/ResolverStates.kt`
  — State composables: `ResolvingContent`, `NoSourcesContent`, `ErrorContent`.

**Key classes/interfaces:** `VideoResolverSheet`.

**UI components provided:** `VideoResolverSheet` + 3 state composables.

**Notes:** Per Phase 8 (Doc 04 violations 3+4), the resolver logic
(`ResolverService`, `VideoTitleParser`, strategy objects) + types
(`ResolverServer`, `ResolverVideo`, `SubtitleTrack`, `ResolverResult`,
`VideoResolverState`) were moved to `:core:video-resolver`. This module keeps
ONLY the UI. **For the rebuild: follow the same separation — feature module
owns UI, core module owns logic.**

---

## `:feature:watch`

**Purpose:** The watch page (ADR-012 — YouTube-style). Hosts the MPV
mini-player + episode list + episode description + player sheets. Per ADR-025,
the watch page's mini-player and the fullscreen player share ONE MPV instance
("Maximize" swaps the Compose overlay, doesn't navigate).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:source-api`, `:core:player`, `:core:episode-metadata`, `:data:anime`,
`:data:history`, `:core:video-resolver` (Phase 8 — types + service, no UI from
`:feature:video-resolver`). Coil 3. activity-compose. Lifecycle. Koin.

**Status:** ✅ Active. 5 source files (1 screen + 1 request data class + 1
prefs hook + 2 sheets).

**Key files:**
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchScreen.kt`
  — ~2386 LOC. The single largest file in the project. Top-level screen with:
  MPV mini-player (`AnikutaMPVView`), episode list (reuses
  `EpisodesSection`-style rendering but with watch-page-specific prefs), play
  controls, episode description, settings sheets (speed, subtitle, etc.),
  BackHandler, window-inset control, fullscreen-mode overlay swap.
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchRequest.kt`
  — `data class` threading ALL context from details → watch page: video URL +
  headers, anime title + cover URL + cover color, episode URL + number,
  source ID + source, server + audio + quality, episode list, episode
  metadata, subtitle/audio tracks, resolved servers.
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/WatchEpisodeDisplayPrefs.kt`
  — SEPARATE from the details page's `EpisodeDisplayPrefs`. Per user:
  customizable independently. Backed by `PlayerEpisodePreferences`
  (`player_ep_*` keys).
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/sheets/PlayerSheets.kt`
  — Player-related bottom sheets.
- `feature/watch/src/main/java/app/confused/anikuta/feature/watch/sheets/SpeedSheet.kt`
  — Playback speed picker.

**Key classes/interfaces:** `WatchScreen`, `WatchRequest`,
`WatchEpisodeDisplayPrefs`.

**UI components provided:** `WatchScreen`, `PlayerSheets`, `SpeedSheet`.

**Notes:** Per ADR-025, the app uses a single MPV instance. The
`:feature:player` stub exists for the (deferred) case where the fullscreen
overlay might become a proper backstack entry. The 2386-line `WatchScreen.kt`
is a candidate for refactor (split into mini-player + episode list + sheets
+ overlay sub-components).

---

## `:feature:extensions-settings`

**Purpose:** Extensions management UI — 3-category structure (Trusted Sources
→ Installed → Available) with Anime/Manga `TwoWayToggle` on top + per-section
empty-state copy. Plus the extension-repo settings screen (CRUD over
`ExtensionRepoRepository`).

**Dependencies:** `:core:common`, `:core:designsystem`, `:data:extension`,
`:core:source-api`. AndroidX core-ktx (DrawableCompat). Coil 3. Coroutines.
Lifecycle. activity-compose. Koin.

**Status:** ✅ Active. 2 source files (README says "UI scaffold" but it's
substantially implemented).

**Key files:**
- `feature/extensions-settings/src/main/java/app/confused/anikuta/feature/extensionssettings/ExtensionsSettingsScreen.kt`
  — ~446 LOC. Renders the 3-category structure with `TwoWayToggle` (Anime /
  Manga) on top. Per-extension rows with icon, name, version, install/update
  /trust/uninstall buttons. Collects `installedExtensionsFlow` +
  `availableExtensionsFlow` + `untrustedExtensionsFlow` from
  `AnimeExtensionManager`.
- `feature/extensions-settings/src/main/java/app/confused/anikuta/feature/extensionssettings/ExtensionRepoSettingsScreen.kt`
  — ~296 LOC. Repo CRUD UI. `LazyColumn` of repos with delete buttons, FAB to
  add a new repo via `AlertDialog` + `OutlinedTextField`. Shows repo count +
  fetches the available extensions on first launch.

**Key classes/interfaces:** `ExtensionsSettingsScreen`,
`ExtensionRepoSettingsScreen`.

**UI components provided:** `ExtensionsSettingsScreen`,
`ExtensionRepoSettingsScreen`.

**Notes:** Real data binding is in place (collects flows from
`AnimeExtensionManager`). Drag-reorderable trusted sources is deferred (per
the README). The `:data:extension` module's `AnimeExtensionManager` is the
main collaborator.

---

## `:feature:settings`

**Purpose:** The settings screens — General (auto-link toggle, default details
view), Appearance (theme mode, AMOLED, palettes, custom color), Player
(general player prefs), About (app version, update check, clear cache, export
logs), Ads (ad settings). Note: the README claims "Empty stub" but the module
is **fully implemented**.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:player` (`PlayerPreferences`), `:core:ads`, `:core:app-update`. Koin.
Lifecycle. Coroutines.

**Status:** ✅ Active (README is stale — claims "Empty stub — NOT YET
IMPLEMENTED"). 8 source files.

**Key files:**
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/GeneralSettingsScreen.kt`
  — ~489 LOC. Auto-link toggle (controls whether extension anime are
  auto-searched + linked to AniList when opened). Default details view
  (AniList vs Extension). Setup-wizard prefs.
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AppearanceScreen.kt`
  — Appearance landing page (list of buttons to sub-pages).
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AppearanceGeneralScreen.kt`
  — Theme mode (light/dark/system), AMOLED toggle, palette presets.
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/PalettePreviewCard.kt`
  — Palette preview cards.
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/CustomColorSheet.kt`
  — Custom color picker bottom sheet.
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/PlayerGeneralScreen.kt`
  — Player general prefs (resume, brightness, etc.).
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AboutScreen.kt`
  — App version, update check, clear cache, export logs, GitHub link.
- `feature/settings/src/main/java/app/confused/anikuta/feature/settings/AdSettingsSection.kt`
  — Ad frequency/timing settings.

**Key classes/interfaces:** `GeneralSettingsScreen`, `AppearanceScreen`,
`AppearanceGeneralScreen`, `PalettePreviewCard`, `CustomColorSheet`,
`PlayerGeneralScreen`, `AboutScreen`, `AdSettingsSection`.

**UI components provided:** 8 settings screens + the custom color sheet.

**Notes:** **README IS WRONG** — the module is fully implemented. This is
likely because the implementation happened after the README was written. The
rebuild should treat the source code as the source of truth. The settings
screens are NOT yet unified into a single "Settings" root — they're scattered
across this module + `:feature:trackers`, `:feature:backup`,
`:feature:download`, `:feature:extensions-settings`, `:feature:episode-settings`.
The future `:feature:settings` module should be the aggregation root that links
to these, not a reimplementation.

---

## `:feature:trackers`

**Purpose:** Trackers settings screen — list of tracker cards (AniList, MAL).
Each card shows the tracker name, connection status, and a Login/Logout button.
Login opens the browser OAuth flow; logout clears the stored token (with
confirmation).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:tracker` (`TrackerManager`). Coil 3 (tracker logos). Coroutines.
Lifecycle. Koin.

**Status:** ✅ Active. 5 source files (1 screen + 1 VM + 1 state + 1 DI +
1 component).

**Key files:**
- `feature/trackers/src/main/java/app/confused/anikuta/feature/trackers/TrackersSettingsScreen.kt`
  — ~95 LOC. `LazyColumn` of `TrackerCard`s. Loading state. Section header.
- `feature/trackers/src/main/java/app/confused/anikuta/feature/trackers/TrackersViewModel.kt`
  — ~72 LOC. Observes `trackerManager.anilist.username` + `trackerManager.mal.username`
  via `combine`. Builds `List<TrackerUiState>`. Per RULES §3: calls
  `TrackerManager` only.
- `feature/trackers/src/main/java/app/confused/anikuta/feature/trackers/TrackersState.kt`
  — `TrackerUiState` + `TrackersState` data classes.
- `feature/trackers/src/main/java/app/confused/anikuta/feature/trackers/di/TrackersModule.kt`
  — Koin: `viewModelOf(::TrackersViewModel)`.
- `feature/trackers/src/main/java/app/confused/anikuta/feature/trackers/components/TrackerCard.kt`
  — Single tracker card with logo, name, status, login/logout button.

**Key classes/interfaces:** `TrackersSettingsScreen`, `TrackersViewModel`,
`TrackersState`, `TrackerUiState`.

**UI components provided:** `TrackersSettingsScreen` + `TrackerCard`.

**Notes:** Only AniList + MAL are implemented (matches ADR-019 — "user picks
which tracker(s)"). Shikimori / Bangumi / Simkl are deferred. The README still
says "Skeleton (Phase 1)" — stale.

---

## `:feature:backup`

**Purpose:** Backup & Restore UI — a full-page settings screen with four
sections: Backup (category checkboxes + create button), Restore (file picker +
format detection + summary + confirm), Auto-backup (enable switch + frequency
selector + category checkboxes), Storage (SAF folder selector + storage usage
display). Plus the Aniyomi-format restore flow.

**Dependencies:** `:core:backup` (engine: `BackupManager`, `BackupStorage`,
etc.), `:core:anilist` (for Aniyomi translation), `:core:designsystem`,
`:core:preferences`, `:core:common`. Coil 3 (Aniyomi restore linking screen).
activity-compose. Lifecycle. Koin.

**Status:** ✅ Active (Implementation complete — README confirms). 14 source
files (1 screen + 1 VM + 2 DIs + 8 components + 2 Aniyomi-restore files).

**Key files:**
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/BackupSettingsScreen.kt`
  — Main screen with 4 sections + state overlays (`BackupUiState` sealed class).
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/BackupViewModel.kt`
  — ~305 LOC. State management via `sealed class BackupUiState` (Idle /
  Creating / Created / ReadingFile / RestorePending / Restoring / Restored /
  Error). Minimum 5-second restore animation (`MIN_RESTORE_ANIMATION_MS`).
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/di/BackupFeatureModule.kt`
  — Koin module.
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/di/AniyomiRestoreModule.kt`
  — Koin module for the Aniyomi-restore VM.
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/components/` — 8
  components: `BackupCategoryList`, `RestoreCategoryRow`, `FrequencySelector`,
  `MaxBackupsSelector`, `RestoreConfirmSheet`, `RestoreSummaryDialog`,
  `RestoreCompleteDialog`, `BackupSuccessDialog`, `RestoreAnimationOverlay`.
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/aniyomi/AniyomiRestoreFlow.kt`
  — ~948 LOC. The Aniyomi-format restore flow — a separate screen that
  translates an Aniyomi backup into ANIKUTA's format, with a linking step
  (extension anime → AniList).
- `feature/backup/src/main/java/app/confused/anikuta/feature/backup/aniyomi/AniyomiRestoreViewModel.kt`
  — VM for the Aniyomi restore flow.

**Key classes/interfaces:** `BackupSettingsScreen`, `BackupViewModel`,
`BackupUiState`, `AniyomiRestoreFlow`, `AniyomiRestoreViewModel`.

**UI components provided:** `BackupSettingsScreen` + 8 sub-components +
`AniyomiRestoreFlow`.

**Notes:** Reached from `SettingsScreen` (in `:app`) via "Backup & Restore"
row under a "Data" section. Wired as a `showBackup` state flag in
`MainActivity.kt`. The Aniyomi restore is a major feature — lets users
migrate from Aniyomi to ANIKUTA with their library + watch progress intact.

---

## `:feature:download`

**Purpose:** The Downloads UI — queue (active/pending/paused/errored with
progress bars + pause/resume/cancel/retry) + downloaded library (grouped by
anime, expandable cards with per-episode delete + delete-all) + settings
bottom sheet (folder, Wi-Fi-only, concurrency, show-download-button toggle).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:download` (`DownloadManager`, `DownloadPreferences`), `:core:source-api`,
`:core:video-resolver` (Phase 8 — `ResolverServer` + `ResolverVideo` types for
`DownloadVideoPickerSheet`). Coil 3. activity-compose. Lifecycle. Koin.

**Status:** ✅ Active. 13 source files (1 main screen + 1 downloaded-files
screen + 1 settings screen + 1 VM + 1 state + 1 picker sheet + 1 more-entries +
1 module-info + 1 DI + 4 components).

**Key files:**
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadsScreen.kt`
  — Main screen. `CollapsingHeader(title = "Downloads")` with settings gear.
  Queue section + Downloaded section + empty state. Single `LazyColumn` (NO
  nested LazyColumn — expanded episode lists are plain `Column`s).
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadedFilesScreen.kt`
  — Downloaded-files browser.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadSettingsScreen.kt`
  — Settings bottom sheet (`dragHandle = null`): folder, Wi-Fi-only,
  concurrency, show-download-button toggle.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadViewModel.kt`
  — ~106 LOC. Observes `manager.activeDownloads` + `manager.completedDownloads` +
  folder-URI pref, combines into `DownloadUiState`. Auto-clears COMPLETED
  entries from the active queue after 10 seconds (per owner request). Forwards
  pause/resume/cancel/delete/retry to `DownloadManager`.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadUiState.kt`
  — `DownloadUiState` + `DownloadedAnimeKey` (keyed by `contentId` since Phase
  6 ADR-050) + `isInQueueSection` extension.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadVideoPickerSheet.kt`
  — Lets the user pick which video to download when an episode has multiple
  servers/qualities.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/DownloadsMoreEntries.kt`
  — More-screen entry point.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/ExtensionSourceInfo.kt`
  — Data class for source display.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/di/DownloadModule.kt`
  — Koin: `downloadFeatureModule`.
- `feature/download/src/main/java/app/confused/anikuta/feature/download/components/` —
  `QueueRow.kt`, `DownloadedAnimeCard.kt`, `DownloadsEmptyState.kt`,
  `DragReorderableList.kt`.

**Key classes/interfaces:** `DownloadsScreen`, `DownloadedFilesScreen`,
`DownloadSettingsScreen`, `DownloadViewModel`, `DownloadUiState`,
`DownloadedAnimeKey`, `DownloadsMoreEntries`.

**UI components provided:** `DownloadsScreen`, `DownloadedFilesScreen`,
`DownloadSettingsScreen`, `DownloadVideoPickerSheet`, `DownloadsMoreEntries`,
4 sub-components.

**Notes:** Enqueue orchestration (resolve video URL → enqueue) does NOT live
here — it's in `:app`'s `DownloadOrchestrator` (which depends on
`:feature:video-resolver` + `:core:download`). This screen only observes
`DownloadManager` flows + issues pause/cancel/delete commands. Per Rule §14
(feature isolation), this module cannot import `:feature:video-resolver`.

---

## `:feature:setup-wizard`

**Purpose:** The first-launch setup wizard — a 14-step flow covering welcome,
theme selection, download folder, permissions, restore, format, processing,
summary, linking, manual, restore-summary, restore-processing,
restore-success, poison (a check), finish.

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:ads`, `:core:download`. Koin. Lifecycle. activity-result contracts.
Material icons extended. Coroutines.

**Status:** ✅ Active. 2 source files (1 wizard app + 1 visuals component).
Single 1840-LOC file.

**Key files:**
- `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/SetupWizardApp.kt`
  — ~1840 LOC. The entire wizard in one file. `enum class WizardStep` (14
  steps), state hoisting, animated transitions between steps, theme picker
  (`AccentPreset`, `PaletteMode`, `ThemeMode`), folder picker (SAF),
  permissions requests (storage, battery optimization), restore-from-backup
  integration, format selection, processing animation, summary screen,
  AniList linking, manual source linking, restore-summary,
  restore-processing, restore-success, poison-check (verifies setup is
  valid), finish.
- `feature/setup-wizard/src/main/java/app/confused/anikuta/feature/setupwizard/components/WizardVisuals.kt`
  — Shared visual components (logo, gradients, progress dots).

**Key classes/interfaces:** `SetupWizardApp`, `WizardStep`, `WizardVisuals`.

**UI components provided:** `SetupWizardApp` (the entire wizard) +
`WizardVisuals`.

**Notes:** The 1840-LOC single file is a candidate for refactor (split into
one composable per step). The `agent-ctx/` folder contains several Z.ai-Code
markdown files documenting the wizard's design iterations
(`SETUP-WIZARD-UI-POLISH-Z.ai Code.md`, `SETUP-WIZARD-FEATURE-Z.ai Code.md`,
`SETUP-WIZARD-REAL-THEME-Z.ai Code.md`, `POST-INSTALL-POPUP-DOTS-PROGRESS-Z.ai Code.md`).

---

## `:feature:home` (stub)

**Purpose:** Reserved slot for a Home screen module.

**Dependencies:** None beyond the `anikuta.library.compose` convention plugin.

**Status:** ⚠️ Empty stub. **No source files**. Not depended on by `:app`.

**Notes:** The home/browse page was implemented as a single `:feature:browse`
module (the "Home" bottom-nav tab renders `BrowseScreen`). This stub exists
only because the original module list included it. **Do not add code here.**
Home/browse work goes in `:feature:browse`. May be removed in a future cleanup
pass.

---

## `:feature:more` (stub)

**Purpose:** Reserved slot for the More tab (settings, downloads, stats, about).

**Dependencies:** None beyond the `anikuta.library.compose` convention plugin.

**Status:** ⚠️ Empty stub. **No source files**. Not depended on by `:app`.

**Notes:** The "More" tab UI is currently rendered inline in `MainActivity.kt`
(the hand-rolled state-machine nav host) rather than in a dedicated feature
module. This was a pragmatic early decision; the More screen is a simple
settings-list router that delegates to the various feature settings screens
(trackers, backup, downloads, extensions, episode-settings, etc.). When the
Voyager navigation migration happens, the More screen may be extracted into
this module.

---

## `:feature:episode-list` (stub)

**Purpose:** Reserved slot for an episode-list component module.

**Dependencies:** None beyond the `anikuta.library.compose` convention plugin.

**Status:** ⚠️ Empty stub. **No source files**. Not depended on by `:app`.

**Notes:** The episode list is implemented as `EpisodesSection.kt` (+ episode
rows) inside `:feature:anime-details` rather than as a standalone module. The
episode list is tightly coupled to the details page (shares
`AnimeDetailViewModel` + `EpisodeDisplayPreferences`), so extracting it would
add coupling overhead without benefit. The same row composable is also
rendered on the `:feature:watch` page (mini-player + episode list below),
which consumes the same row via a shared dependency on `:feature:anime-details`.

---

## `:feature:player` (stub)

**Purpose:** Reserved slot for a fullscreen player screen module.

**Dependencies:** `build.gradle.kts` declares deps on `:core:common`,
`:core:designsystem`, `:core:player`, `:core:source-api`, activity-compose,
Coil, Koin — but **no source files use them**.

**Status:** ⚠️ Empty stub. No source files. **IS depended on by `:app`**
(`implementation(projects.feature.player)` in `app/build.gradle.kts`).
The dependency is a no-op (empty module).

**Notes:** Per ADR-025, the app uses a **single MPV instance** — the watch
page's mini-player and the fullscreen player share one MPV surface.
"Maximize" swaps the Compose overlay (the fullscreen controls live in
`:core:player/controls/FullscreenControls.kt`), it does NOT navigate to a
separate fullscreen player activity/screen. So there is no separate
`:feature:player` screen. The dependency can be removed from `:app`'s deps +
`settings.gradle.kts` in a future cleanup pass, or repurposed if the team
ever splits the fullscreen overlay into its own feature module. The Voyager
navigation migration may revisit this.

---

## Cross-cutting observations for the rebuild

1. **Stale READMEs are a real problem.** Several feature READMEs claim
   "Skeleton (Phase 1)" when the module is fully implemented (`library`,
   `updates`, `history`, `browse`, `my`, `anime-details`, `episode-settings`,
   `video-resolver`, `watch`, `extensions-settings`, `trackers`). The
   `:feature:settings` README claims "Empty stub — NOT YET IMPLEMENTED" but
   ships 8 implemented screens. **For the rebuild: treat source code as the
   source of truth, not README status lines.**

2. **No Voyager / Compose Navigation.** The app uses a hand-rolled
   state-machine nav host in `:app`'s `MainActivity.kt` (`var currentScreen:
   SomeScreen?` state). This is a deferred decision — the Voyager migration
   may revisit `:feature:more`, `:feature:home`, `:feature:player` stubs.

3. **Phase 7 (ADR-041) — provider abstraction.** All AniList-direct calls in
   `:feature:browse`, `:feature:search`, `:feature:updates` were routed
   through `MetadataProviderRegistry` → capability interfaces
   (`HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`). Adding
   MAL/TMDB later = one module + one Koin line; the feature screens stay
   unchanged. **For the rebuild: build this abstraction from day one.**

4. **Phase 8 (Doc 04 violations) — feature→feature deps removed.** Three
   architectural violations were fixed:
   - `:core:backup` no longer depends on `:data:extension` (inverted via
     `SourceLinkBackupAccess` port).
   - `:feature:episode-settings` no longer depends on `:feature:anime-details`
     (moved `EpisodeDisplayPreferences` to `:core:preferences`).
   - `:feature:video-resolver` no longer has its logic — moved to
     `:core:video-resolver`. `:feature:watch` and `:feature:download` now
     depend on `:core:video-resolver` for types + service.

   **For the rebuild: enforce the no-feature→feature-dep rule from day one.**
   Feature modules depend ONLY on `:core/`, `:data/`, and (sometimes)
   `:core:designsystem`. The `:app` module orchestrates cross-feature flows.

5. **Single MPV instance (ADR-025).** The watch page + fullscreen player
   share one MPV surface. "Maximize" is an overlay swap, not a navigation.
   The `:feature:player` stub exists for a potential future split.

6. **Hand-rolled nav = state flags everywhere.** `MainActivity.kt` holds
   `var showBackup`, `var episodeSettingsPage: EpisodeSettingsPage?`,
   `var currentScreen: SomeScreen`, etc. The 2386-LOC `WatchScreen.kt` and
   the 1840-LOC `SetupWizardApp.kt` are candidates for refactor — split them
   into per-step / per-section composables.

7. **Watch-page episode prefs are SEPARATE from details-page episode prefs.**
   `:feature:watch`'s `WatchEpisodeDisplayPrefs` (backed by
   `PlayerEpisodePreferences` in `:core:player`) is independent from
   `:feature:anime-details`'s `EpisodeDisplayPrefs` (backed by
   `EpisodeDisplayPreferences` in `:core:preferences`). Per user requirement:
   customizable separately.

8. **Test coverage:** no feature module ships unit tests. The ViewModel
   logic (especially `AnimeDetailViewModel`'s 1013 LOC) is a candidate for
   testing. The rebuild should add tests for the state-updating functions +
   the source-switching / extension-switching flows.

9. **DI pattern:** feature modules expose a `di/XxxModule.kt` with
   `viewModelOf(::XxxViewModel)` (Koin's DSL). `:feature:anime-details` and
   `:feature:search` are the exceptions — they use manual `ViewModelProvider`
   factories (because their VMs take runtime parameters like `animeId` /
   `extensionSource`).

10. **Bottom-sheet design principle:** `dragHandle = null` on all
    `ModalBottomSheet` usages (per DESIGN_LANGUAGE principle #2). This is
    consistently followed in `:feature:library` (SortSheet, CustomizeSheet),
    `:feature:video-resolver` (VideoResolverSheet), `:feature:download`
    (DownloadSettingsSheet), `:feature:backup` (RestoreConfirmSheet), etc.
