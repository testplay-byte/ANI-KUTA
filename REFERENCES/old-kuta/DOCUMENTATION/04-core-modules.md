# Core Modules Analysis

> Deep analysis of the 19 `:core:*` modules in the old ANIKUTA project (reimagined Aniyomi).
> Source: `/home/z/my-project/ANIKUTA-PROJECT/REFERENCES/old-kuta/ANIKUTA/core/`.

## Summary

The `:core:*` layer is the **foundation** of the ANIKUTA app — pure contracts,
data models, persistence, infrastructure, and engine logic that the `:app`,
`:data:*`, and `:feature:*` layers compose. Per `ARCHITECTURE.md §3`, core
modules may depend on other `:core:*` modules but NEVER on `:data:*` or
`:feature:*` (that direction is inverted via interface injection — see
`:core:update-checker/EpisodeFetchGateway`, `:core:backup/SourceLinkBackupAccess`).

The layer spans:

- **Domain core** — `:core:common` (models, repository interfaces, identity),
  `:core:provider-api` (metadata-provider umbrella).
- **Persistence** — `:core:database` (SQLDelight), `:core:preferences`
  (typed SharedPreferences wrapper).
- **UI foundation** — `:core:designsystem` (theme, colors, components).
- **External data** — `:core:anilist`, `:core:tracker`, `:core:episode-metadata`,
  `:core:source-api` (Aniyomi extension contract).
- **Engines** — `:core:player` (MPV), `:core:download`, `:core:backup`,
  `:core:video-resolver`, `:core:update-checker`.
- **App infra** — `:core:ads`, `:core:app-update`.
- **Removed stubs** — `:core:network`, `:core:notification`, `:core:source-local`
  (folders + READMEs remain for archaeology; the `include(...)` lines were
  removed in Phase 9 because they had 0 `.kt` files).

Key cross-cutting themes:

1. **Two-tier identity (ADR-050)** — `LocalId` (per-source) + `ContentId`
   (per-content, survives source switches). Cross-cutting stores (watch
   progress, downloads, history, episode metadata, tracker) key off
   `ContentId` so a source switch doesn't lose state.
2. **Pluggable registries** — every extension point (metadata providers,
   episode-metadata sources, anime-details providers, backup providers,
   update sources, video-resolver strategies) uses a registry populated via
   Koin multi-binding (`single<List<T>>`). Adding a new provider = one class
   + one Koin line.
3. **Aniyomi extension binary compatibility (ADR-029)** — `:core:source-api`
  ships the exact `eu.kanade.tachiyomi.animesource.*` package, uses Injekt
  for `NetworkHelper` resolution, and supports both the legacy RxJava
  `fetch*` API + the modern suspend `get*` API so compiled extension APKs
  load and run unmodified.
4. **Dispatchers are injected** (`DispatcherProvider` in `:core:common`)
   so tests can swap them.
5. **All sensitive operations on `Dispatchers.IO`** (per `RULES §9`).

## Module Index

| Module | Purpose | Status |
|--------|---------|--------|
| `:core:common` | Domain models, repository interfaces, two-tier identity, util | active |
| `:core:designsystem` | Material 3 theme, color palette, typography, reusable Compose components | active |
| `:core:database` | SQLDelight schema (`AnikutaDatabase`), Android driver factory | active |
| `:core:preferences` | `PreferenceStore` interface + Android impl + typed preference holders | active |
| `:core:provider-api` | Metadata-provider umbrella + capability interfaces (ADR-041) | active |
| `:core:anilist` | AniList GraphQL client + rate limiter + local cache + provider adapter | active |
| `:core:tracker` | AniList + MAL OAuth trackers, sync manager, profile stats | active |
| `:core:episode-metadata` | Pluggable per-episode metadata sources (Jikan, Anikage, AniList) | active |
| `:core:source-api` | Aniyomi extension contract (AnimeSource, Video, NetworkHelper) | active |
| `:core:player` | MPV wrapper, watch-progress store, controls, preferences | active |
| `:core:update-checker` | Library new-episode detection + EpisodeFetchGateway abstraction | active |
| `:core:download` | Modular download engine (OkHttp + HLS + advanced Range/resume) | active |
| `:core:backup` | Multi-format backup/restore (ANIKUTA zip + Aniyomi protobuf read) | active |
| `:core:video-resolver` | Server/audio/quality hierarchy + resolver strategy | active |
| `:core:ads` | On-device-tracked ad system with quota/cooldown/min-stay | active |
| `:core:app-update` | GitHub-releases self-update + APK download + install | active |
| `:core:network` | (Was: HTTP client + interceptors) — empty stub, networking lives in `:core:source-api` | **removed** (Phase 9) |
| `:core:notification` | (Was: episode-release notifications ADR-014) — not yet implemented | **removed** (Phase 9) |
| `:core:source-local` | (Was: local-files-as-source) — not yet implemented | **removed** (Phase 9) |

> **Note on the 3 removed stubs:** `settings.gradle.kts` does NOT `include()`
> them (Phase 9 cleanup). The folders + READMEs remain on disk explaining
> why each was deferred — they are documentation-only at this point and are
> NOT depended on by `:app`.

---

## :core:common

**Purpose:** Domain models, repository contracts, two-tier identity system,
and shared utilities — the kernel every other `:core:*` module depends on.

**Dependencies:** `kotlinx.coroutines.core`, JUnit test bundle.
- Plugin: `anikuta.library` (no Compose, no Android UI deps).
- Imports `androidx.lifecycle` indirectly through some util files.

**Status:** active. README says "Skeleton (Phase 1)" but the module is
fully populated with the domain layer.

**Key files:**
- `model/Anime.kt` — main anime domain model; carries library + status-tracking
  + two-tier identity (`localId`, `contentId`, `provenance`) fields per ADR-024/050.
- `model/Episode.kt` — episode domain model with anime-specific
  (`fillermark`, `summary`, `previewUrl`) + ADR-024 status-tracking fields.
- `model/Category.kt` — library category; `DEFAULT_ID = 1L`, `order`, `hidden`, `flags`.
- `model/Track.kt` — tracker-binding domain model.
- `model/History.kt` — watch-history entry.
- `model/Source.kt` — content source (extension) descriptor.
- `model/ContentId.kt` — `@JvmInline value class ContentId` + `ContentIdGenerator`
  + `ContentIdPriority`. Tier 2 identity (`"<providerKey>:<remoteId>"`, e.g.
  `"al:154587"`). User-configurable priority order (ADR-050).
- `model/LocalId.kt` — `@JvmInline value class LocalId` + `LocalIdGenerator` +
  `ParsedLocalId`. Tier 1 identity (`"<system>:<extensionId>:<sourceContentId>"`).
- `model/SourceProvenance.kt` — full "where did this come from?" metadata
  (system, repo, extension pkg/version, source name, discovered/resolved
  timestamps, link confidence).
- `model/MetadataProviderId.kt` — enum (`ANILIST`, `MAL`, `TMDB`, `KITSU`).
- `model/ExtensionSystem.kt` — enum (`ANIYOMI`, `CLOUDSTREAM`).
- `model/LibrarySort.kt` + `model/LibraryDisplayMode.kt` — global library
  sort/display prefs.
- `model/details/UnifiedAnime.kt` — the unified anime value consumed by
  `AnimeDetailScreen` (produced by `AnimeDetailsProvider`).
- `model/details/AnimeDetailsProvider.kt` — pluggable data-source-agnostic
  provider interface for the details page.
- `model/details/AnimeDetailsProviderRegistry.kt` — registry (Koin
  multi-binding `single<List<AnimeDetailsProvider>>`).
- `model/details/DetailsRequest.kt` + `DataSource.kt` — sealed request +
  enum (`ANILIST`, `EXTENSION`).
- `model/details/HtmlToPlainText.kt` — synopsis HTML normalizer.
- `repository/AnimeRepository.kt` — interface (CRUD + observe + identity
  backfill + AniList-link management).
- `repository/EpisodeRepository.kt`, `HistoryRepository.kt`,
  `CategoryRepository.kt`, `TrackRepository.kt` — repository contracts.
- `di/DispatcherProvider.kt` — `interface DispatcherProvider { io, main, default }`
  + `DefaultDispatcherProvider` for testability (ADR-023).
- `util/RelativeTime.kt` — `formatTimeAgo`, `formatTimeUntil`,
  `formatPlaybackTimestamp`, `formatDetailedCountdown`, `calendarDayKey`,
  `relativeDayBucket`.
- `util/CategorySuggester.kt` — 3-char-substring matcher for "Watching",
  "Completed", etc. with case-preservation.

**Public API:**
- `Anime`, `Episode`, `Category`, `Track`, `History`, `Source` data classes.
- `ContentId`, `LocalId`, `SourceProvenance`, `ContentIdPriority`,
  `ContentIdGenerator`, `LocalIdGenerator`, `ParsedLocalId`.
- `MetadataProviderId`, `ExtensionSystem`.
- `AnimeRepository`, `EpisodeRepository`, `HistoryRepository`,
  `CategoryRepository`, `TrackRepository` interfaces.
- `AnimeDetailsProvider`, `AnimeDetailsProviderRegistry`, `UnifiedAnime`,
  `DetailsRequest`, `DetailsResult`, `DataSource`.
- `DispatcherProvider`, `DefaultDispatcherProvider`.
- `LibrarySort`, `LibrarySortType`, `LibraryDisplayMode`.
- `formatTimeAgo`, `formatTimeUntil`, `formatPlaybackTimestamp`,
  `formatDuration`, `formatDetailedCountdown`, `calendarDayKey`,
  `relativeDayBucket`, `CategorySuggester`.

**Notes:**
- Two-tier identity (ADR-050) is the architectural keystone: `LocalId` is
  per-source, `ContentId` is per-content. All cross-cutting stores
  (`WatchProgressStore`, `DownloadManager`, `EpisodeMetadataCache`,
  `TrackSyncManager`, history) migrated to key off `ContentId` so source
  switches don't lose state.
- `Anime` has 30+ fields — deliberately denormalized: AniList metadata
  (`anilistId`, `coverColor`, `score`, `totalEpisodes`, `nextAiringEpisode`),
  status-tracking (`releaseDate`, `lastRefresh`, `lastMetadataFetch`,
  `nextEpisodeCheck`), and the identity columns are all flat.
- `AnimeRepository` has both AniList-keyed methods (`getByAnilistId`,
  `updateAnilistMetadata`) AND identity-keyed methods (`getByLocalId`,
  `getByContentId`, `updateIdentity`, `backfillIdentityColumns`) — a
  transitional API that supports both pre- and post-ADR-050 code paths.
- `releasedEpisodes` is a computed `val` (`nextAiringEpisode - 1` if airing,
  else `totalEpisodes`).

---

## :core:designsystem

**Purpose:** The single entry point for app theming — Material 3 `ColorScheme`,
typography, shapes, motion, and reusable Compose components. Implements the
owner's chosen Lime accent (`#B1F256`) + AMOLED + animated dark↔light
transitions + cover-color dynamic theming.

**Dependencies:** `:core:common`, `:core:preferences`, `kotlinx.coroutines.core`,
`androidx.palette:palette-ktx:1.0.0`.
- Plugin: `anikuta.library.compose`.
- Bundles 4 Roboto TTF weights in `res/font/` (regular/medium/bold/black).

**Status:** active.

**Key files:**
- `theme/Theme.kt` — `@Composable AnikutaTheme(...)` — the single entry point.
  Resolves `themeMode` (Light/Dark/System) + `accentPreset` + `amoled` +
  `paletteMode` (SIMPLIFIED/FULL) into a `ColorScheme`. Animates every color
  role via `animateColorAsState(400ms)` for smooth dark↔light cross-fades.
  Also sets status-bar appearance + supports full-palette custom overrides.
- `theme/Color.kt` — full color palette: dark (5 surface tiers + 3 text tiers
  + M3 roles), light (warm-neutral, darker cards), AMOLED (pure black + grey
  surfaces), functional (`WarnDark`, `SuccessDark`).
- `theme/AccentColors.kt` — `AccentScheme` data class + `accentScheme(color)`
  HSL-based derivation (boosts saturation for light mode to avoid mud;
  darkens ~35% for primaryContainer).
- `theme/CoverColor.kt` — `generateDynamicScheme(coverColor, darkTheme, amoled)`
  returns a `ColorScheme?` tinted with the anime's cover (used by watch +
  details pages). Returns null when `coverColor == 0` (caller falls back).
- `theme/PaletteExtraction.kt` — `PaletteExtraction.extractFromBitmap(bitmap)`
  returns ARGB int via Palette API. `extractCoverColor(url)` is a documented
  skeleton (caller must do the download via Coil).
- `theme/Type.kt` — bundled `RobotoFamily` + `AnikutaTypography` (M3 type
  scale with `ExtraBold` 800 for display/headline/title/label — fixes
  "bold text isn't bold" on devices missing ExtraBold).
- `theme/Shape.kt`, `theme/Motion.kt` — shapes + animation durations.
- `component/ScrollBlurOverlay.kt` — the "frosted glass" gradient scrim that
  fades in on scroll under pinned headers. Pure `graphicsLayer` + `drawBehind`
  (no `RenderEffect`, GPU-cheap, no recomposition on scroll).
- `component/CollapsingHeader.kt` — pinned header that animates font size
  (36sp → 26sp) + padding on scroll.
- `component/AnikutaBottomSheet.kt`, `SettingsGroupCard.kt`,
  `CategoryPickerDialog.kt`, `CustomToggle.kt`, `MoreListRow.kt`,
  `SearchField.kt`, `ListSectionHeader.kt`, `SectionHeader.kt`,
  `NavIcons.kt`, `SegmentedToggles.kt`, `EmptyState.kt`,
  `AddCategoryDialog.kt`, `BottomNavBar.kt` — reusable UI primitives.

**Public API:**
- `AnikutaTheme` composable.
- `generateDynamicScheme(coverColor, darkTheme, amoled)`, `extractDominantColor(bitmap)`.
- `PaletteExtraction.extractFromBitmap(bitmap)`.
- `accentScheme(color)`, `accentSchemeFor(preset, color)`, `AccentScheme`.
- `RobotoFamily`, `AnikutaTypography`, `AnikutaShapes`, `Motion`.
- Color tokens (`BgDark`, `Surface1Dark` ... `PrimaryDark`, `BgLight` ...).
- `CollapsingHeader`, `ScrollBlurOverlay`, `BottomNavBar`, `EmptyState`,
  `SettingsGroupCard`, `AnikutaBottomSheet`, `CustomToggle`,
  `SegmentedToggles`, `MoreListRow`, `SearchField`, `CategoryPickerDialog`,
  `AddCategoryDialog`, `SectionHeader`, `ListSectionHeader`, `NavIcons`.

**Notes:**
- The owner's spec is "dark theme is the default". The palette has 5 surface
  tiers for tonal hierarchy.
- AMOLED mode uses pure black for `background` but keeps subtle greys
  (`#121212`, `#1A1A1A`, `#242424`) for surfaces so cards are visible.
- `accentScheme` uses HSL math (not HCT) — simpler but produces harmonious
  results. Light-mode primary is forced to `lightness ~40% + saturation ~70%`
  to avoid the "muddy tint" bug.
- `ScrollBlurOverlay` is the canonical "frosted glass without RenderEffect"
  pattern — uses a gradient scrim whose color matches the screen's background,
  producing the optical illusion of blur at zero GPU cost. Worth studying.

---

## :core:database

**Purpose:** SQLDelight schema + Android driver for the `AnikutaDatabase`.
Single source of truth for the relational tables (`animes`, `episodes`,
`categories`, `anime_category`, `animehistory`, `animetrack`). Currently
anime-only; a separate manga database is reserved per ADR-009.

**Dependencies:** `libs.bundles.sqldelight` (driver + coroutines ext),
`libs.sqldelight.dialects.sql`, `kotlinx.coroutines.core`, JUnit + coroutines-test.
- Plugins: `anikuta.library` + `app.cash.sqldelight`.
- Database name: `AnikutaDatabase`, package `app.confused.anikuta.core.database`.

**Status:** active. Schema is at migration 2.sqm (v3) — added ADR-024
status-tracking columns + ADR-050 identity columns.

**Key files:**
- `DatabaseDriverFactory.kt` — creates `AndroidSqliteDriver` with
  `AnikutaDatabase.Schema`, file `anikuta.db`. Logs with tag `AnikutaDb`.
- `src/main/sqldelight/.../animes.sq` — anime table with library columns,
  status-tracking columns, two-tier identity columns (`local_id`, `content_id`),
  source-provenance columns (`system`, `repo_url`, `extension_pkg_name`, …),
  bookkeeping (`discovered_at`, `last_resolved_at`, `link_confidence`).
  Indexes: unique on `anilist_id` (partial), unique on `local_id` (partial),
  non-unique on `content_id`, unique on `(source_id, url)`. ~25 queries
  (CRUD, identity, metadata update, cover-only updates, etc.).
- `src/main/sqldelight/.../episodes.sq` — episode table with anime-specific
  fields + ADR-024 columns. Unique index on `(anime_id, episode_number)`.
  FK `anime_id → animes(_id) ON DELETE CASCADE`.
- `src/main/sqldelight/.../categories.sq` — category table; seeds Default
  category (id=1) via `INSERT OR IGNORE`. Deletion-protection for id=1 is
  enforced at the app layer (SQLDelight parser doesn't support OLD/NEW triggers).
- `src/main/sqldelight/.../anime_category.sq` — anime↔category junction.
- `src/main/sqldelight/.../animehistory.sq` — watch-history rows.
- `src/main/sqldelight/.../animetrack.sq` — tracker bindings.
- `src/main/sqldelight/.../1.sqm`, `2.sqm` — schema migrations.

**Public API:**
- `AnikutaDatabase` (SQLDelight-generated) — exposes `.animesQueries`,
  `.episodesQueries`, `.categoriesQueries`, `.animeCategoryQueries`,
  `.animehistoryQueries`, `.animetrackQueries`.
- `DatabaseDriverFactory(context).create(): SqlDriver`.

**Notes:**
- SQLDelight (not Room) was chosen per ADR-024 — type-safe SQL with compile-time
  query verification.
- The `animes` table is heavily denormalized (~30 columns). The two-tier
  identity columns are nullable (Phase 1 transition — backfilled on first
  launch post-migration via `AnimeRepository.backfillIdentityColumns`).
- The unique partial index `idx_animes_anilist_id WHERE anilist_id IS NOT NULL`
  enforces one-row-per-AniList-ID for linked anime, while allowing many
  unlinked extension-only rows.
- `link_confidence` int: 0=none, 1=auto-matched (may be wrong), 2=user-confirmed.

---

## :core:preferences

**Purpose:** `PreferenceStore` abstraction + Android `SharedPreferences`
implementation + typed preference holders (theme, content-id priority,
linking, details-view, setup-wizard, episode-display, provider prefs).

**Dependencies:** `:core:common`, `:core:provider-api`, `androidx.preference:preference-ktx:1.2.1`,
`kotlinx-coroutines-core`, `androidx.core:core-ktx:1.15.0`, Koin BOM + core + android.
- Plugin: `anikuta.library`.

**Status:** active.

**Key files:**
- `PreferenceStore.kt` — interface with `getString/getLong/getInt/getFloat/
  getBoolean/getStringSet/getObject/getAll` + `getEnum` reified helper.
- `Preference.kt` — interface with `key()/get()/set()/isSet()/delete()/
  defaultValue()/changes(): Flow<T>/stateIn(scope): StateFlow<T>` + helpers
  (`getAndSet`, `deleteAndGet`, `plusAssign`/`minusAssign` for sets, `toggle`).
  Companion marks keys with `__APP_STATE_` / `__PRIVATE_` prefixes for
  backup filtering.
- `AndroidPreferenceStore.kt` — `SharedPreferences`-backed impl using
  `PreferenceManager.getDefaultSharedPreferences(context)` + a `keyFlow`
  built from `OnSharedPreferenceChangeListener`.
- `AndroidPreference.kt` — internal primitive + object preference impls.
- `AndroidProviderPreferences.kt` — `ProviderPreferences` (provider-api
  capability selection) Android impl.
- `ThemePreferences.kt` — `themeMode`, `amoled`, `accentPreset`,
  `customAccentColor`, `paletteMode` (SIMPLIFIED/FULL), `customBackground/
  Card/TextColor`, `customPaletteMode` (remembered for "back to Custom"),
  `adaptiveColorsDetails`, `adaptiveColorsPlayer`, `headerBlurEffect`.
  Methods: `setCustomAccent`, `applyFullPalettePreset`, `selectCustom`.
  `AccentPreset` enum has 15 presets (10 accent-only + 5 full-palette) +
  CUSTOM. `PaletteMode` enum.
- `ContentIdPreferences.kt` — stores `ContentIdPriority` as
  comma-separated `MetadataProviderId.key` string. Robust parser drops
  unknown keys + appends missing providers in declaration order.
- `LinkingPreferences.kt` — `autoLinkEnabled` (default true), `linkingProvider`
  (default AniList; future: user picks MAL/TMDB). Documents future
  per-extension + per-anime overrides.
- `DetailsViewPreferences.kt` — global default details-page data source
  (`"anilist"` / `"extension"` / `"entry_mode"`). Per-anime overrides live
  in `:data:extension` and take precedence.
- `EpisodeDisplayPrefs.kt` + `EpisodeDisplayPreferences.kt` — episode-row
  display prefs (showThumbnails/Titles/Summaries/Dates/EpisodeNumber/
  AudioPills, thumbnailSize, titleMaxLines, positions, background toggles,
  showDownloadButton). Moved here in Phase 8 to fix Doc 04 violation 2
  (feature→feature dep).
- `SetupWizardPreferences.kt` — `isCompleted`/`setCompleted`/`observeCompleted`
  — gates the 15-screen onboarding.
- `di/PreferenceModule.kt` — Koin module binding `PreferenceStore` +
  all 7 preference holder classes.

**Public API:**
- `PreferenceStore`, `Preference<T>`, `AndroidPreferenceStore`,
  `getEnum<T>(key, default)` reified helper.
- `ThemePreferences`, `ThemeMode`, `AccentPreset`, `PaletteMode`.
- `ContentIdPreferences` (uses `ContentIdPriority` from `:core:common`).
- `LinkingPreferences`.
- `DetailsViewPreferences`, `DataSource`.
- `EpisodeDisplayPreferences`, `EpisodeDisplayPrefs`.
- `SetupWizardPreferences`.
- `ProviderPreferences` (interface — impl in this module; defined in
  `:core:provider-api`).
- `preferenceModule` Koin module.

**Notes:**
- The `Preference` interface is reactive — every change emits on `changes()`
  Flow, so ViewModels can observe prefs without polling.
- `AndroidPreferenceStore` uses `PreferenceManager.getDefaultSharedPreferences`
  — the app has ONE shared prefs file (every pref key lives in the same
  SharedPreferences). This is what `BackupProvider.getAll()` reads.
- `Preference.isPrivate(key)` / `isAppStateKey(key)` are markers used by
  `PreferencesBackupProvider` to filter what gets backed up.
- The `keyFlow` uses `callbackFlow` + `OnSharedPreferenceChangeListener` —
  the same flow is shared by every `AndroidPreference` instance, so adding
  a listener is cheap.

---

## :core:provider-api

**Purpose:** The umbrella contract for metadata providers (AniList, MAL,
TMDB, Kitsu, …) + capability sub-interfaces + a runtime registry with
fallback. Per ADR-041 — designed so a new provider = one module + one Koin
line, no existing code changes.

**Dependencies:** `:core:common`, `kotlinx.coroutines.core`, JUnit + coroutines-test.
- Plugin: `anikuta.library`.

**Status:** active.

**Key files:**
- `MetadataProvider.kt` — the umbrella interface. Every provider implements it:
  `id: MetadataProviderId`, `displayName`, `requiresAuth`,
  `capabilities: Set<MetadataCapability>`, `suspend isAvailable(): Boolean`.
- `Capabilities.kt` — `MetadataCapability` enum (`HOME_FEED`, `SEARCH`,
  `AIRING_SCHEDULE`, `COVER_IMAGES`, `DETAILS`, `EPISODE_METADATA`) + the
  capability sub-interfaces:
    - `HomeFeedProvider` — `fetchTrending`, `fetchPopular`,
      `getCachedTrending()`, `getCachedPopular()` (SWR pattern).
    - `SearchProvider` — `search`, `searchWithFilters` (`SearchFilters`
      data class with all-nullable fields).
    - `AiringScheduleProvider` — `fetchSchedule(ids: List<Int>)`
      returns `List<AiringScheduleInfo>`.
    - `CoverImageProvider` — `fetchCover(providerId: String): CoverImageInfo?`.
- `MetadataProviderRegistry.kt` — registry populated via Koin multi-binding
  (`single<List<MetadataProvider>>`). `forCapability<T>(capability)` resolves
  the active provider by:
    1. User's `activeProviderFor(capability)` preference.
    2. User's `fallbackOrder(capability)`.
    3. Remaining providers in declaration order.
  Returns first available (where `isAvailable()` returns true).
  Also `allForCapability<T>(capability)` for the Settings UI.
- (No README — created in Phase 2 as part of ADR-041.)

**Public API:**
- `MetadataProvider`, `MetadataCapability`.
- `HomeFeedProvider`, `SearchProvider`, `AiringScheduleProvider`,
  `CoverImageProvider`.
- `SearchFilters`, `AiringScheduleInfo`, `CoverImageInfo`.
- `MetadataProviderRegistry`.
- `ProviderPreferences` interface (impl in `:core:preferences`).

**Notes:**
- The capability-based design means a provider can implement just the
  capabilities it supports (e.g., a future MAL provider could declare
  `setOf(SEARCH, DETAILS)` without `AIRING_SCHEDULE`).
- The registry's fallback chain handles network-down gracefully — if
  AniList is unreachable, the next registered provider (e.g., MAL) is tried.
- `AnimeDetailsProvider` (in `:core:common`) and `EpisodeMetadataSource`
  (in `:core:episode-metadata`) are NOT in this module — they pre-date
  ADR-041. A future phase may consolidate them under `MetadataCapability.DETAILS`
  and `MetadataCapability.EPISODE_METADATA`. The `AniListMetadataProvider`
  adapter (in `:core:anilist`) implements the umbrella but NOT `DETAILS` —
  that's still served by the separate `AniListDetailsProvider` in `:data:anime`.

---

## :core:anilist

**Purpose:** AniList GraphQL client (raw HTTP + kotlinx-serialization, no
Apollo per ADR-030) + rate limiter + local persistent cache + the
`AniListMetadataProvider` adapter that maps the API to the
`:core:provider-api` capability interfaces.

**Dependencies:** `:core:common`, `:core:preferences`, `:core:provider-api`,
`com.squareup.okhttp3:okhttp:5.0.0-alpha.14`, `kotlinx-serialization-json:1.9.0`,
`kotlinx-coroutines-core:1.10.1`, JUnit5 + coroutines-test.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.

**Status:** active.

**Key files:**
- `api/AniListApi.kt` — the GraphQL client. Uses public AniList API (no auth
  for browse/search/schedule). Methods: `fetchTrending`, `fetchPopular`,
  `searchAnime`, `searchAnimeWithFilters`, `fetchById`, `fetchAiringSchedule`,
  `getCachedTrending`, `getCachedPopular`. Has an in-memory 5-min detail
  cache. Builds GraphQL queries by hand via `buildJsonObject`. Sends POST
  to `https://graphql.anilist.co`.
- `api/AniListRateLimiter.kt` — sliding-window rate limiter (max 80 req/min
  below AniList's 90/min limit). "Fast mode" for the first 40 reqs (no
  delay), then incremental delays for the remaining 40. Thread-safe via
  `ConcurrentLinkedDeque` + `AtomicInteger`.
- `api/LocalAniListCache.kt` — persistent cache (via `PreferenceStore`):
  trending list, popular list (both 24h TTL), individual anime details
  (24h TTL, keyed by AniList ID). JSON-serialized `AniListAnime`.
- `model/AniListAnime.kt` — `@Serializable` AniList Media subset (id, title,
  coverImage, averageScore, format, episodes, status, genres, season,
  studios, nextAiringEpisode, idMal, etc.) + extension helpers
  (`displayTitle`, `coverUrl`, `coverColorHex`, `seasonDisplay`,
  `studioName`, `nextAiringDisplay`).
- `details/AniListMetadataProvider.kt` — adapter implementing `MetadataProvider`
  + `HomeFeedProvider` + `SearchProvider` + `AiringScheduleProvider` +
  `CoverImageProvider`. Wraps the existing `AniListApi` (does NOT rewrite
  it). `isAvailable()` returns true (AniList is unauthenticated + the API
  handles rate-limit backoff internally).
- `details/AniListAnimeMapper.kt` — `AniListAnime → UnifiedAnime` mapper
  (referenced by `AniListMetadataProvider`).

**Public API:**
- `AniListApi` — the GraphQL client.
- `AniListRateLimiter`.
- `LocalAniListCache`.
- `AniListAnime`, `AniListTitle`, `AniListCoverImage`, `AniListFuzzyDate`,
  `AniListStudioConnection`, `AniListStudio`, `AniListAiringSchedule`,
  `AiringScheduleInfo` + the `AniListAnime.displayTitle/coverUrl/...`
  extension helpers.
- `AniListMetadataProvider` (the `MetadataProvider` adapter).

**Notes:**
- Auth for personalized data (tracker sync) is deferred to Phase 7 (ADR-013)
  and lives in `:core:tracker/AniListTracker` — the browse-side `AniListApi`
  is unauthenticated.
- `AniListApi` is registered as a Koin singleton in `:app/NavModule.kt`
  (with cache + rate limiter) — the in-memory caches are process-wide so a
  shared instance is correct.
- The `AniListMetadataProvider` adapter is intentionally thin — the
  capability pattern was retrofitted onto the existing API without
  rewriting the GraphQL plumbing.
- The rate limiter's "fast mode for the first 40 reqs" was tuned for backup
  restore: small backups (≤40 anime) run at full speed; large backups spread
  out to avoid hitting the 90/min cap.

---

## :core:tracker

**Purpose:** Multi-tracker infrastructure (AniList + MAL) — OAuth flows,
token storage, progress sync, profile stats. Implements ADR-019 (multiple
trackers) + ADR-013 (tracker integration).

**Dependencies:** `:core:common`, `:core:database`, `:core:preferences`,
`:core:anilist`, `:core:player` (for `WatchProgressStore`), OkHttp,
kotlinx-serialization, Koin, `libs.sqldelight.coroutines`.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- `buildConfig` enabled; BuildConfig fields: `ANILIST_CLIENT_ID="5338"`,
  `MAL_CLIENT_ID="686b980ff4240fccce7f6a654cea07ce"`.

**Status:** active.

**Key files:**
- `Tracker.kt` — the contract: `id`, `name`, `isLoggedIn`, `username: Flow<String?>`,
  `getAuthUrl()`, `handleAuthCallback(callbackUrl): Boolean`, `logout()`,
  `updateProgress(remoteAnimeId, episodeNumber, status)`,
  `fetchUserAnimeList()`, `fetchUserStats()`. Constants `ANILIST_ID=2`,
  `MAL_ID=1` (match Aniyomi conventions). `TrackStatus` enum (WATCHING,
  COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH, REPEATING).
  `TrackAnimeEntry` + `TrackerUserStats` data classes.
- `TrackerManager.kt` — registry of `AniListTracker` + `MalTracker`.
  `getTracker(id)`, `loggedInTrackers()`, `loggedInTrackersFlow` (combined).
- `TrackSyncManager.kt` — auto-syncs watch progress to linked trackers.
  Listens to `WatchProgressStore.changes`, debounces 10s, groups by
  AniList ID, calls `Tracker.updateProgress` for each binding. Computes
  status (WATCHING vs COMPLETED based on `episodeNumber >= totalEpisodes`).
  All work on `Dispatchers.IO`. Uses `Mutex` to serialize syncs.
- `StatsCalculator.kt` — computes `ProfileStats` from local data
  (library + watch progress) + optionally enriches with AniList stats when
  linked. Two modes per ADR-013: local (no AniList) vs AniList-linked.
  Exposes `Flow<ProfileStats>` that re-computes on library/progress changes.
  Computes: totalAnime, totalEpisodesWatched, totalWatchMinutes, meanScore,
  genre/format/status/score/country distributions, behindAnime list,
  recentlyWatched list.
- `ProfileStats.kt` — the stats payload (consumed by `:feature:my`).
- `AnimeTrack.kt` — domain model for the `animetrack` SQLDelight table row.
- `TrackRepository.kt` — CRUD for the `animetrack` table
  (`getTracks(animeId)`, `getTrack(animeId, trackerId)`, `getAllTracks()`,
  `bind(...)`, `unbind(...)`, `updateLastSeen(id, lastSeen)`).
- `TrackerBackupProvider.kt` — interface (`export(): TrackerBackupData`,
  `restore(data)`) + `TrackerBackupData` data class. Documents the keys
  that must be backed up (AniList token/username/avatar/userId, MAL OAuth
  JSON, all `animetrack` rows).
- `TrackerBackupProviderImpl.kt` — concrete impl used by `:core:backup`'s
  `TrackerBackupProviderAdapter`.
- `anilist/AniListTracker.kt` — OAuth implicit grant (response_type=token).
  Token extracted from URL fragment via regex (Uri.getQueryParameter can't
  read fragments). 1-year validity → no refresh. Stores token/username/
  avatar/userId in `PreferenceStore`. `CLIENT_ID="5338"` (matches Aniyomi).
- `anilist/AniListTrackApi.kt` + `AniListViewer.kt` — HTTP API calls
  (fetchViewer, updateProgress, fetchUserAnimeList, fetchUserStats).
- `mal/MalTracker.kt` — OAuth PKCE (authorization-code grant). Code_verifier
  held in a static var (lost if process killed mid-OAuth). Access tokens
  expire in 1h → `ensureFreshToken()` auto-refreshes. Stores serialized
  `MalOAuth` (with refresh token). `CLIENT_ID="686b980ff4240fccce7f6a654cea07ce"`.
- `mal/MalTrackApi.kt`, `mal/MalOAuth.kt`, `mal/PkceUtil.kt` — MAL HTTP API
  + OAuth model + PKCE code_verifier/Challenge generator.
- `di/TrackerModule.kt` — Koin module binding all trackers + the manager +
  repository + sync manager + stats calculator + `TrackerBackupProvider`.

**Public API:**
- `Tracker` interface, `TrackStatus`, `TrackAnimeEntry`, `TrackerUserStats`.
- `TrackerManager`, `TrackerManager.ANILIST_ID`, `MAL_ID`.
- `AniListTracker` (with `avatar: Flow<String?>` extra), `MalTracker`.
- `TrackSyncManager` (call `start()` once at app startup).
- `StatsCalculator` (`observeStats(): Flow<ProfileStats>`,
  `fetchAniListStats()`, `observeAniListUsername()`, `observeAniListAvatar()`,
  `isAniListLinked()`).
- `ProfileStats`, `BehindAnime`.
- `AnimeTrack`, `TrackRepository`.
- `TrackerBackupProvider`, `TrackerBackupData`, `TrackerBackupProviderImpl`.
- `trackerModule` Koin module.

**Notes:**
- AniList uses implicit grant (token in URL fragment) — different from MAL's
  PKCE (code in query string, exchanged for token). The `Tracker` interface
  abstracts both.
- The sync manager's 10s debounce + `Mutex` prevents API spam during
  rapid seek/save cycles.
- Stats are computed locally from `WatchProgressStore` (JSON-in-prefs) +
  `AnimeRepository.observeFavorites()` — the local mode works without any
  tracker login. When AniList is linked, `fetchAniListStats()` enriches
  with format/country distributions that aren't stored locally.
- `TrackSyncManager.syncPendingProgress` parses the `"al:<anilistId>"` prefix
  out of the `ContentId` key to find the AniList ID — this is the bridge
  between the two-tier identity system and the tracker's remote IDs.
- Both tracker CLIENT_IDs are checked into source — these are public OAuth
  client IDs (not secrets); PKCE/implicit-grant flows don't require a secret.

---

## :core:episode-metadata

**Purpose:** Pluggable per-episode metadata enrichment — fetches episode
titles/descriptions/thumbnails/air-dates/filler flags from multiple sources
in parallel, merges per-field (first non-null wins), and caches by
`ContentId`. Per ADR-022.

**Dependencies:** `:core:common`, `:core:preferences`, OkHttp,
kotlinx-serialization-json, kotlinx-coroutines-core, Koin.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- No Compose (pure data module — UI lives in `:feature:episode-settings`).

**Status:** active.

**Key files:**
- `source/EpisodeMetadataSource.kt` — the pluggable source interface
  (`id`, `name`, `supports(request): Boolean`, `fetchAll(request): Map<Int,
  EpisodeMetadata>`, `providedFields: Set<EpisodeMetadataField>`).
  `EpisodeMetadataField` enum (`TITLE`, `DESCRIPTION`, `THUMBNAIL`,
  `AIR_DATE`, `FILLER`).
- `source/EpisodeMetadataSourceRegistry.kt` — mutable list-based registry
  (`register`, `unregister`, `getAll`, `getSupported(request)`).
- `source/jikan/JikanMalSource.kt` — Jikan v4 API (free, no auth).
  Provides TITLE + AIR_DATE. Paginated (max 10 pages = ~200 episodes).
  Courtesy delay 500ms + 400ms between pages + 429 rate-limit handling.
- `source/anikage/AnikageCcSource.kt` — Anikage.cc API.
  Provides TITLE + DESCRIPTION + THUMBNAIL + AIR_DATE.
- `source/anilist/AniListStreamingSource.kt` — AniList streaming episodes.
  Provides TITLE + THUMBNAIL.
- `repository/EpisodeMetadataRepository.kt` — the public API. `fetchAll(request)`
  checks in-memory cache → local persistent cache → fetches from all
  registered sources in parallel (`async`/`awaitAll`) → merges per-field
  (registration order = merge priority) → caches both in-memory + locally.
  Respects per-field prefs (`fetchTitles`, `fetchSummaries`,
  `fetchThumbnails`, `fetchAirDates`).
- `repository/EpisodeMetadataCache.kt` — persistent cache keyed by
  `content_id` (was keyed by `anilistId` pre-Phase-4 — switched so unlinked
  extension anime can also cache). JSON-serialized via `PreferenceStore`.
- `model/EpisodeMetadata.kt` — `@Serializable` data class (animeId,
  episodeNumber, title, description, thumbnailUrl, airDate, filler,
  lastFetched) + `EpisodeMetadataRequest` + `EpisodeMetadataResult` sealed.
- `util/EpisodeTitleParser.kt` — title parsing utilities.
- `migration/EpisodeMetadataMigrator.kt` — Phase 4 migration: re-keys
  cache entries from `anilistId` to `content_id` keys.
- `EpisodeMetadataPreferences.kt` — `enabled()`, `fetchTitles()`,
  `fetchSummaries()`, `fetchThumbnails()`, `fetchAirDates()`.
- `di/EpisodeMetadataModule.kt` — Koin module. Source priority (registration
  order = merge priority): 1. Jikan, 2. Anikage, 3. AniList. Creates a
  dedicated `OkHttpClient` (30s connect/read) locally — NOT injected from
  the extension `NetworkHelper` — keeps the module self-contained.

**Public API:**
- `EpisodeMetadataSource`, `EpisodeMetadataField`.
- `EpisodeMetadataSourceRegistry`.
- `EpisodeMetadataRepository`, `EpisodeMetadataRequest`, `EpisodeMetadataResult`.
- `EpisodeMetadata`, `EpisodeMetadataCache`.
- `EpisodeMetadataPreferences`.
- `EpisodeMetadataMigrator`.
- `JikanMalSource`, `AnikageCcSource`, `AniListStreamingSource`.
- `episodeMetadataModule` Koin module.

**Notes:**
- The merge priority (Jikan → Anikage → Kitsu → AniList for title;
  Anikage → Kitsu for description; etc.) matches the old ANIKUTA project.
- All sources are fetched in parallel — a slow source doesn't block others.
  Each source's failure is isolated (returns empty map, logged).
- The cache key migration (anilistId → content_id) is the Phase 4
  ADR-050 adaptation — unlinked extension anime can now have cached metadata.
- The module deliberately creates its own `OkHttpClient` + `Json` rather
  than injecting from the extension `NetworkHelper` — this keeps the
  metadata module decoupled from the extension DI graph.

---

## :core:source-api

**Purpose:** The Aniyomi/Mihon extension contract — `AnimeSource`,
`AnimeHttpSource`, `SAnime`/`SEpisode`/`Video`/`Hoster` models, `NetworkHelper`,
interceptors, and utilities. Compiled extensions load these classes at
runtime via the extension classloader. Per ADR-029.

**Dependencies:** `api(com.squareup.okhttp3:okhttp)` (api-exposed because
`Video.headers` is a public field of type `okhttp3.Headers`),
`org.jsoup:jsoup:1.19.1`, `kotlinx-serialization-json:1.9.0`,
`kotlinx-serialization-json-okio:1.9.0`, `kotlinx-coroutines-core:1.10.1`,
`io.reactivex:rxjava:1.3.8` + `io.reactivex:rxandroid:1.2.1` (for the
deprecated `fetch*` API), `org.nanohttpd:nanohttpd:2.3.1`,
`api(com.github.mihonapp:injekt:91edab2317)` (api-exposed so extensions can
resolve `Injekt.get<T>()`), `compileOnly(com.github.skydoves:compose-stable-marker:1.0.5)`,
`androidx.preference:preference-ktx:1.2.1`.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- Uses `-Xcontext-receivers` compiler flag (for `context(Json)` in
  `OkHttpExtensions.parseAs<T>()`).

**Status:** active.

**Key files:**
- `animesource/AnimeSource.kt` — the basic interface. `id: Long`, `name`,
  `lang`. Suspend methods: `getAnimeDetails`, `getEpisodeList`, `getSeasonList`,
  `getHosterList(episode)`, `getVideoList(hoster)` (ext-lib 16+),
  `getVideoList(episode)` (legacy). Deprecated RxJava `fetch*` methods
  default to throwing — extensions override ONE of the two.
- `animesource/AnimeCatalogueSource.kt` — `AnimeSource` + popular/search/
  latest + filter list. Suspend `getPopularAnime`, `getSearchAnime`,
  `getLatestUpdates` + deprecated `fetch*` counterparts.
- `animesource/online/AnimeHttpSource.kt` — abstract base class for HTTP
  sources. `protected val network: NetworkHelper by injectLazy()` (Injekt
  resolution). `baseUrl`, `versionId=1`, `id` generated via MD5 of
  `"${name.lowercase()}/$lang/$versionId"` (first 64 bits, sign bit cleared).
  `headers` builder. Default impls of all `fetch*` and `get*` methods.
  Helper extension functions `SEpisode.setUrlWithoutDomain(url)`,
  `SAnime.setUrlWithoutDomain(url)`. `getVideoSize`, `videoRequest`,
  `safeVideoRequest`. `sortHosters`/`sortVideos` overridable.
- `animesource/online/ParsedAnimeHttpSource.kt` — Jsoup-based base.
- `animesource/online/ResolvableAnimeSource.kt` — for sources that resolve
  videos on-demand.
- `animesource/AnimeSourceFactory.kt` — for extensions that ship multiple
  sources (one extension, many sources).
- `animesource/ConfigurableAnimeSource.kt` — interface for sources with a
  preference screen. `getSourcePreferences()` via `ExtensionAppHolder.app`
  (replaces Injekt.get<Application>() per ADR-023).
- `animesource/UnmeteredSource.kt` — marker for sources that don't count
  against metered connections.
- `animesource/PreferenceScreen.kt` — typealias to `androidx.preference.PreferenceScreen`.
- `animesource/model/SAnime.kt` — interface (`url`, `title`, `artist`,
  `author`, `description`, `genre`, `status`, `thumbnail_url`,
  `background_url`, `update_strategy`, `fetch_type`, `season_number`,
  `initialized`). `SAnimeImpl` is the impl. Status constants (UNKNOWN=0,
  ONGOING=1, COMPLETED=2, LICENSED=3, PUBLISHING_FINISHED=4, CANCELLED=5,
  ON_HIATUS=6).
- `animesource/model/SEpisode.kt` — interface (`url`, `name`, `date_upload`,
  `episode_number`, `fillermark`, `scanlator`, `summary`, `preview_url`).
- `animesource/model/Video.kt` — the resolved video model (`videoUrl`,
  `videoTitle`, `resolution`, `bitrate`, `headers`, `preferred`,
  `subtitleTracks`, `audioTracks`, `timestamps`, `mpvArgs`, `ffmpegStreamArgs`,
  `ffmpegVideoArgs`, `internalData`, `initialized`, `status: State`).
  Includes `SerializableVideo` companion for JSON serialization (used by
  backup + extension IPC). Backward-compat `quality`/`url` properties +
  legacy constructors (ext-lib 1.5 → 1.6 migration).
- `animesource/model/Hoster.kt` — ext-lib 16+ "hoster" concept (a server
  that hosts one or more videos). `hosterUrl`, `hosterName`, `videoList`,
  `internalData`, `lazy`, `status: State`. `SerializableHoster` companion.
  `List<Video>.toHosterList()` extension for legacy sources.
- `animesource/model/AnimeFilterList.kt`, `AnimeFilter.kt` — search filters.
- `animesource/model/AnimesPage.kt` — popular/search result page.
- `animesource/model/FetchType.kt`, `AnimeUpdateStrategy.kt`,
  `ThumbnailInfo.kt`, `HttpServer.kt` — supporting models.
- `network/NetworkHelper.kt` — **CRITICAL** — MUST be a `class` (not an
  interface) for binary compat. Configures OkHttpClient (30s connect, 30s
  read, 2min call) + `UncaughtExceptionInterceptor` + `UserAgentInterceptor`
  + `IgnoreGzipInterceptor`. `cloudflareClient` (alias to `client` — WebView
  bypass not yet implemented). `defaultUserAgent` (Chrome 130 Mobile).
  `DefaultNetworkHelper` typealias for back-compat.
- `network/Requests.kt` — `GET`, `POST`, `HEAD` builder functions +
  `asObservableSuccess`, `awaitSuccess`, `newCachelessCallWithProgress`.
- `network/OkHttpExtensions.kt` — `Response.parseAs<T>()` (uses
  `context(Json)` receiver — that's why `-Xcontext-receivers` is enabled).
- `network/ProgressResponseBody.kt`, `network/ProgressListener.kt` —
  progress-tracking response body for downloads.
- `network/interceptor/UserAgentInterceptor.kt`,
  `RateLimitInterceptor.kt`, `SpecificHostRateLimitInterceptor.kt`,
  `IgnoreGzipInterceptor.kt`, `UncaughtExceptionInterceptor.kt` — the
  interceptor chain.
- `util/JsoupExtensions.kt` — Jsoup helpers.
- `util/JsonExtensions.kt` — JSON helpers.
- `util/RxExtension.kt` — RxJava `awaitSingle` (bridges `Observable` to suspend).
- `util/VideoInfo.kt` — video utilities.

**Public API:** (everything in `eu.kanade.tachiyomi.*`)
- `AnimeSource`, `AnimeCatalogueSource`, `AnimeHttpSource`,
  `ParsedAnimeHttpSource`, `ResolvableAnimeSource`,
  `AnimeSourceFactory`, `ConfigurableAnimeSource`, `UnmeteredSource`.
- `SAnime`, `SAnimeImpl`, `SEpisode`, `SEpisodeImpl`, `Video`,
  `SerializableVideo`, `Hoster`, `SerializableHoster`,
  `Track`, `TimeStamp`, `ChapterType`, `AnimeFilterList`, `AnimeFilter`,
  `AnimesPage`, `FetchType`, `AnimeUpdateStrategy`, `ThumbnailInfo`,
  `HttpServer`.
- `NetworkHelper`, `DefaultNetworkHelper` typealias.
- `GET`, `POST`, `HEAD`, `Response.parseAs<T>()`, `asObservableSuccess`,
  `awaitSuccess`, `newCachelessCallWithProgress`.
- `ProgressListener`, `ProgressResponseBody`.
- `UserAgentInterceptor`, `RateLimitInterceptor`,
  `SpecificHostRateLimitInterceptor`, `IgnoreGzipInterceptor`,
  `UncaughtExceptionInterceptor`.
- `ExtensionAppHolder`, `preferenceKey()`, `sourcePreferences()`.
- Jsoup/JSON/RxJava helpers.

**Notes:**
- **Binary compatibility is THE constraint here.** Extensions (Keiyoushi/
  Aniyomi) are compiled against the reference `eu.kanade.tachiyomi.animesource.*`
  bytecode. This module MUST match the reference's class/interface
  declarations, method signatures, AND field layouts — or extensions throw
  `IncompatibleClassChangeError` / `NoSuchMethodError` at runtime.
  The build comments document every such constraint (e.g., `network` MUST
  be `by injectLazy()` not a direct field; `NetworkHelper` MUST be a class
  not an interface).
- `-Xcontext-receivers` is enabled because the reference's
  `OkHttpExtensions.parseAs<T>()` uses `context(Json)`. Extensions compiled
  against the reference call `response.parseAs<T>()` with a context
  receiver — we MUST match the compiled signature.
- Both RxJava 1.x (`fetch*` API) AND the modern suspend `get*` API are
  supported. The `@Deprecated` annotations preserve source compat for
  extensions being upgraded; the suspend defaults delegate to the RxJava
  defaults via `awaitSingle()`.
- The `Video` model has both `videoUrl` (the playable URL) AND a legacy
  `url` (a "videoPageUrl" — the page that contains the video). The legacy
  constructor + `url` getter exist for ext-lib 1.5 → 1.6 migration.
- `ExtensionAppHolder` is a static `Application` holder set during app
  startup — this replaces `Injekt.get<Application>()` in the reference
  (the app uses Koin, not Injekt, for its own DI — but extensions still
  use Injekt for `NetworkHelper` resolution).

---

## :core:player

**Purpose:** MPV video player wrapper, watch-progress + playback-state
persistence, player preferences, controls UI (fullscreen + minimized +
seekbar + subtitle/audio pickers + episode switching). Per ADR-025
(single MPV instance, no recreation on mode switches).

**Dependencies:** `:core:common`, `:core:designsystem`, `:core:preferences`,
`:core:source-api`, `api(anikutaLibs.aniyomi.mpv)` (api-exposed —
`AnikutaMPVView extends is.xyz.mpv.BaseMPVView`), `anikutaLibs.ffmpeg.kit`,
`anikutaLibs.arthenica.smartexceptions`, `anikutaLibs.seeker`,
`anikutaLibs.nanohttpd`, `anikutaLibs.mediasession`,
`anikutaLibs.truetypeparser`, Coil3 (episode thumbnails), Koin, kotlinx-coroutines,
kotlinx-serialization.
- Plugins: `anikuta.library.compose` + `kotlin.plugin.serialization`.
- Bundles `assets/subfont.ttf` for subtitle rendering.

**Status:** active.

**Key files:**
- `AnikutaMPVView.kt` — thin wrapper over `is.xyz.mpv.BaseMPVView`. The
  2-param `(Context, AttributeSet)` constructor is REQUIRED because the
  view is inflated from `R.layout.mpv_view` (XML) — Compose's `AndroidView`
  factory can't pass a real `AttributeSet` (the previous fake-AttributeSet
  attempt crashed with `ClassCastException: XmlPullAttributes cannot be
  cast to XmlBlock$Parser`). Reads `playerPreferences` from a companion
  `lateinit var` because the view is XML-inflated (can't use Koin ctor
  injection). Exposes MPV properties as Kotlin properties: `duration`,
  `timePos`, `paused`, `volume`, `hwdecActive`, `sid`, `aid`. Track-list
  parsing for subtitle/audio track enumeration + selection.
- `PlayerStateHolder.kt` — shared state across MINIMIZED ↔ FULLSCREEN
  transitions (per ADR-025, the MPV view is never recreated). Plain class
  (not a ViewModel) — owned by the screen-level composable. `StateFlow`s
  for: `playerMode`, `loadingState`, `errorMessage`, `isPlaying`,
  `position`, `duration`, `buffering`, `bufferAheadTime`, `controlsVisible`,
  `controlsLocked`, `episodeList`, `currentEpisodeIndex`,
  `isSwitchingEpisode`, `subtitleTracks`, `audioTracks`,
  `currentSubtitleId`, `currentAudioId`. Update methods called by the host.
- `PlayerObserver.kt` — MPV event observer (property-changed, end-file,
  etc.) bridges into `PlayerStateHolder`.
- `PlayerInitializer.kt` — initializes MPVLib with config + assets.
- `MpvConfigManager.kt` — creates/manages `mpv/mpv.conf`, `mpv/input.conf`,
  `mpv/fonts/` in `context.filesDir`. Advanced users can edit for full MPV
  control. Default configs written on first launch.
- `WatchProgressStore.kt` — keyed by `"$contentId|$episodeNumber"` (Phase 3
  ADR-050 migration). Saves playback position per episode. `changes` Flow
  consumed by History/TrackSync/Stats. `key(contentId, episodeNumber)`,
  `parseKey(key)`, `save(...)`, `get(contentId, episodeNumber)`, `getAll()`,
  `clearForAnime(contentId)`.
- `PlaybackStateStore.kt` — saves the LAST video URL + audio track + subtitle
  track + resolution used per episode. When resuming from History, the
  player tries the same URL first; falls back to re-resolving if dead.
  Same key format as `WatchProgressStore`.
- `PlayerPreferences.kt` — minimal player prefs (speed, tryHWDecoding,
  gpuNext, volumeBoostCap, preferredAudioLanguages, seekStepSeconds,
  brightness, autoHideControls) + Phase 7.5 episode-list display prefs
  (showEpisodeTitles/Summaries/Thumbnails/Dates/Number/AudioPills,
  synopsisPosition, thumbnailSize, etc.).
- `PlayerEpisodePreferences.kt` — episode-row display prefs (companion to
  `EpisodeDisplayPreferences` in `:core:preferences`).
- `PlayerEnums.kt` — `PlayerMode` (MINIMIZED, FULLSCREEN),
  `PlayerLoadingState`, `EpisodeListItem`, `VideoTrack`.
- `PlayerUtils.kt` — formatting + helper functions.
- `controls/FullscreenControls.kt` — fullscreen player chrome (seekbar,
  play/pause, skip, audio/subtitle pickers, lock controls, episode switching).
- `controls/MinimizedControls.kt` — mini-player controls (play/pause + tap
  to expand).
- `controls/MinimalSeekbar.kt` — the seekbar component (uses `seeker` lib).
- `controls/EpisodeSwitchingOverlay.kt` — next/prev episode overlay.
- `controls/SubtitleSettingsSheet.kt` — subtitle font/size/color picker.
- `controls/ColorPickerSheet.kt` — color picker bottom sheet.
- `controls/NumericEntrySheet.kt` — "jump to timestamp" entry sheet.
- `controls/ThemedGlass.kt` — themed glass effect for controls.
- `subtitles/SubtitleTrackFormatter.kt` — formats subtitle track labels.
- `migration/WatchProgressMigrator.kt` — Phase 3 migration: re-keys
  `WatchProgressStore` + `PlaybackStateStore` from `"$anilistId:$episodeUrl"`
  to `"$contentId|$episodeNumber"`. Drops entries with `anilistId == 0`
  (the polluted unlinked-anime entries — Doc 01 §6.3).
- `di/PlayerModule.kt` — Koin module binding `PlayerPreferences`,
  `PlayerEpisodePreferences`, `WatchProgressStore`, `PlaybackStateStore`,
  `WatchProgressMigrator`.
- `src/test/.../WatchProgressStoreKeyTest.kt` — unit tests for the key
  format + parseKey.

**Public API:**
- `AnikutaMPVView` (extends `BaseMPVView`) + `Companion.playerPreferences`
  lateinit.
- `PlayerStateHolder` + `PlayerMode`, `PlayerLoadingState`,
  `EpisodeListItem`, `VideoTrack`.
- `PlayerObserver`.
- `PlayerInitializer`.
- `MpvConfigManager` (`MPV_DIR`, `ensureConfigFiles(context)`,
  `getConfigDir(context)`, `readMpvConf`, `writeMpvConf`, etc.).
- `WatchProgressStore` + `WatchProgressStore.Progress`.
- `PlaybackStateStore` + `PlaybackStateStore.PlaybackState`.
- `PlayerPreferences`, `PlayerEpisodePreferences`.
- `WatchProgressMigrator`.
- Compose controls: `FullscreenControls`, `MinimizedControls`,
  `MinimalSeekbar`, `EpisodeSwitchingOverlay`, `SubtitleSettingsSheet`,
  `ColorPickerSheet`, `NumericEntrySheet`, `ThemedGlass`.
- `playerModule` Koin module.

**Notes:**
- The MPV view is XML-inflated (not Compose) — Compose's `AndroidView`
  wraps `LayoutInflater.inflate(R.layout.mpv_view, null)`. This was forced
  by Android's `obtainStyledAttributes` requiring a `XmlBlock$Parser`
  (only obtainable from real XML inflation, not from `Xml.newPullParser()`).
- The companion-`lateinit` pattern for `playerPreferences` is a known
  compromise — the host must populate it BEFORE inflating the view. Fails
  loudly on first property access if forgotten.
- Both `WatchProgressStore` AND `PlaybackStateStore` use the same
  `"$contentId|$episodeNumber"` key format — Phase 3 ADR-050 unified them
  so source switches preserve both position AND the resolved video URL.
- MPV's `sid`/`aid` properties are read via `getPropertyString()` (not
  `getPropertyInt()`) because MPV returns "no" for unset tracks, which
  crashes the int reader.
- The MPV config dir (`context.filesDir/mpv/`) is user-editable — advanced
  users can drop custom `mpv.conf`/`input.conf`/fonts.

---

## :core:update-checker

**Purpose:** Reusable, UI-agnostic core of the "Updates" feature — checks
library anime for new episode releases. Designed to be callable from a
future `WorkManager` periodic worker without rewriting any logic.

**Dependencies:** `:core:common`, `:core:source-api`, `:core:anilist`,
`:core:preferences`, kotlinx-serialization-json (for `StoredResult` DTOs),
kotlinx-coroutines-core, Koin.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- Deliberately does NOT depend on `:data:extension` (per `ARCHITECTURE.md §3`).

**Status:** active.

**Key files:**
- `UpdateChecker.kt` — the orchestrator. `checkForUpdates()` iterates
  library favorites, calls `EpisodeFetchGateway.fetchEpisodes(title)` for
  each, diffs against `lastKnownEpisodeCount`, parses sub/dub availability,
  cross-refs AniList `nextAiringEpisode`. Persists results to
  `UpdateCheckerPreferences.storedResults` so the list survives process
  death. Auto-expires `isNew` after 2 days (per user feedback round 4).
  Emits live progress via `getCheckProgress(): StateFlow<UpdateCheckProgress>`
  — the Updates page renders a "Currently checking" card showing which
  anime is being searched + index/total. Catches `Throwable` per-anime
  (extension bytecode can throw `IncompatibleClassChangeError`).
- `EpisodeFetchGateway.kt` — interface declared here, implemented in
  `:data:extension/EpisodeFetchGatewayImpl`, injected via Koin. This is
  the architectural inversion that lets `:core:update-checker` stay free
  of `:data:*` deps. Returns `EpisodeFetchResult.Success(sourceName,
  episodes)` or `EpisodeFetchResult.NoSource`.
- `UpdateModels.kt` — `UpdateResult` (anime, newEpisodeCount, newEpisodes,
  checkedAt, hasSub, hasDub, sourceName, isNew), `UpdateCheckProgress`
  sealed (`Idle`, `Checking`, `Completed`), `EpisodeInfo` (trimmed
  serializable view of `SEpisode`), `AudioAvailability`.
- `UpdateCheckerPreferences.kt` — `lastCheckTimestamp()`, `lastKnownEpisodeCount(animeId)`,
  `setLastKnownEpisodeCount(...)`, `storedResults()` (serialized
  `List<StoredResult>`), `checkIntervalHours()`.
- `SubDubParser.kt` — parses "SUB"/"DUB"/"HSUB"/"HARDSUB" tokens from
  episode names + scanlator fields.
- `di/UpdateCheckerModule.kt` — Koin module binding `UpdateCheckerPreferences`
  + `UpdateChecker`. `EpisodeFetchGateway` is NOT registered here (it's
  bound in `:data:extension/extensionModule`).

**Public API:**
- `UpdateChecker` (`checkForUpdates(): List<UpdateResult>`,
  `checkAnime(animeId): UpdateResult?`, `acknowledgeResult(animeId)`,
  `getLastResults(): StateFlow<List<UpdateResult>>`,
  `getLastCheckTimestamp(): StateFlow<Long>`,
  `getCheckProgress(): StateFlow<UpdateCheckProgress>`).
- `EpisodeFetchGateway` interface + `EpisodeFetchResult` sealed.
- `UpdateResult`, `UpdateCheckProgress`, `EpisodeInfo`, `AudioAvailability`.
- `UpdateCheckerPreferences`.
- `SubDubParser`.
- `updateCheckerModule` Koin module.

**Notes:**
- The `EpisodeFetchGateway` inversion is the key architectural trick —
  it lets `:core:update-checker` be both (a) free of `:data:*` deps and
  (b) trivially testable (swap in a fake gateway).
- The `StoredResult` DTO + auto-expire-after-2-days logic fixes the user
  complaint that the list cleared on app close+reopen AND that "new"
  highlights never disappeared.
- The check is designed to be called from `viewModelScope` (manual
  pull-to-refresh) OR a future `WorkManager` worker — the suspend API +
  no UI coupling makes both callers work without changes.
- Catches `Throwable` (not `Exception`) because extension bytecode can
  throw `IncompatibleClassChangeError` / `NoClassDefFoundError` — one
  broken extension must never abort the whole check.

---

## :core:download

**Purpose:** The download engine — modular, future-proof system for
downloading anime episodes (video + subtitles + metadata) to a user-selected
SAF folder in an AniList-first structure. Supports direct video + unencrypted
HLS; encrypted HLS / DASH documented as next step (FFmpegKit).

**Dependencies:** `:core:common`, `:core:preferences`, `:core:source-api`,
`com.squareup.okhttp3:okhttp:5.0.0-alpha.14`, `androidx.documentfile:documentfile:1.0.1`
(SAF), `kotlinx-serialization-json:1.9.0`, `kotlinx-coroutines-core:1.10.1`,
Koin.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- NO Compose — pure logic + SAF I/O + notifications. UI lives in
  `:feature:download`.
- Does NOT depend on `:feature:video-resolver` — `:app`'s `DownloadOrchestrator`
  passes the already-resolved `DownloadRequest` into this module.

**Status:** active. README says "Production-ready for unencrypted HLS +
direct video."

**Key files:**
- `DownloadManager.kt` — the pluggable contract (ADR-020). Flows:
  `activeDownloads`, `completedDownloads`, `allDownloads`,
  `episodeDownloadStates`. Methods: `enqueueDownload`, `pauseDownload`,
  `resumeDownload`, `cancelDownload`, `deleteDownload`,
  `deleteAnimeDownloads`, `retryDownload`, `removeFromQueue`,
  `setDownloadFolder(treeUriString)`, `isFolderReady()`,
  `isEpisodeDownloaded(contentId, episodeNumber)`,
  `getDownloadedVideoUri(...)`, `getDownloadedSubtitleUris(...)`,
  `getDownloadedEpisodes(contentId)`.
- `DefaultDownloadManager.kt` — the single implementation. Wires
  `DownloadQueue` + `HttpDownloader` + `DownloadStore` +
  `DownloadStorageProvider` + `TempDownloadCache` + `DownloadPreferences` +
  `ServerDiscoveryStore` + `DynamicProgressTracker` +
  `DownloadNotificationManager` + `DownloadLogger`.
- `DownloadQueue.kt` — the state machine + concurrency manager.
  `Semaphore(concurrentDownloads)` permits guarantee at most N
  simultaneous downloads. Progress throttled to `PERSIST_INTERVAL_MS`
  for SharedPreferences writes. Connectivity check (`connectivityCheck`
  lambda) — skips if Wi-Fi-only is on and not on Wi-Fi. `onTaskCompleted`/
  `onTaskError` callbacks.
- `DownloadTask.kt` — `@Serializable` data class (id, request, status,
  progress, downloadedBytes, totalBytes, errorMessage, createdAt,
  updatedAt, videoUri, subtitleUris). `key: String` =
  `"$contentId|$episodeNumber"` (source-independent).
- `DownloadStatus.kt` — enum (QUEUED, DOWNLOADING, PAUSED, COMPLETED,
  ERROR, CANCELLED).
- `DownloadRequest.kt` — the resolved request (anime + episode + videoUrl +
  videoHeaders + subtitleTracks + audioTracks).
- `DownloadModels.kt` — `DownloadedEpisode`, `DownloadException`.
- `HttpDownloader.kt` — the actual HTTP downloader (DEFAULT method).
  Pipeline: download to internal cache → validate (reject HLS/DASH/HTML/
  tiny files) → download subtitles → write metadata.json → publish to SAF
  → clean up temp. Falls back to `HlsDownloader` for `.m3u8` URLs. Falls
  back to `AdvancedHttpDownloader` for the ADVANCED method.
- `HlsDownloader.kt` — HLS playlist parsing + segment download + PNG
  anti-scraping header stripping (finds IEND marker, skips to MPEG-TS
  sync byte) + concatenation into `.ts`.
- `advanced/AdvancedHttpDownloader.kt` — multi-threaded Range-request
  downloader with resume + auto-retry. Falls back to Normal for HLS +
  unsupported servers.
- `advanced/DownloadResumeManager.kt` — per-chunk resume metadata.
- `DownloadStore.kt` — persists the queue as JSON in `PreferenceStore`.
- `DownloadStorageProvider.kt` — SAF folder structure (AniList-first:
  `AniList/{anilistId}/{episodeNumber}/video.mkv`) + publish.
- `TempDownloadCache.kt` — internal cache for partial downloads (cleans
  up stale dirs from previous crashes).
- `DownloadPreferences.kt` — all download settings (concurrent downloads,
  Wi-Fi-only, download folder URI, method, etc.).
- `DownloadNotificationManager.kt` — Android notifications (progress,
  completion, error).
- `DownloadLogger.kt` — uniform tag (`AnikutaDownload`).
- `ServerDiscoveryStore.kt` — caches discovered server names per source.
- `DynamicProgressTracker.kt` — smart progress estimation (50MB-ahead,
  90% cap — for chunked transfers where Content-Length is unknown).
- `VideoTypeDetector.kt` — magic-byte validation (rejects HTML/PNG/JPEG
  masquerading as video).
- `di/DownloadModule.kt` — Koin module. Binds `DownloadPreferences`,
  `DownloadStore`, `ServerDiscoveryStore`, `TempDownloadCache`,
  `DownloadResumeManager`, `AdvancedHttpDownloader`, dedicated
  `OkHttpClient` (qualifier `"download"`, 60s read/write timeouts —
  separate from extension NetworkHelper so a stuck download can't
  starve extension HTTP calls), `DownloadManager` → `DefaultDownloadManager`.

**Public API:**
- `DownloadManager` interface + `DefaultDownloadManager`.
- `DownloadQueue` + `onTaskCompleted`/`onTaskError` callbacks.
- `DownloadTask`, `DownloadStatus`, `DownloadRequest`, `DownloadedEpisode`,
  `DownloadException`.
- `HttpDownloader`, `HlsDownloader`, `AdvancedHttpDownloader`,
  `DownloadResumeManager`.
- `DownloadStore`, `DownloadStorageProvider`, `TempDownloadCache`,
  `DownloadPreferences`, `ServerDiscoveryStore`, `DynamicProgressTracker`,
  `DownloadNotificationManager`, `DownloadLogger`, `VideoTypeDetector`.
- `downloadModule` Koin module.

**Notes:**
- The modular `DownloadManager` interface (ADR-020) is the future-proofing
  keystone — a future `OneDmDownloadManager` (multi-threaded, 1DM-style)
  swaps in via a Koin binding change. No consumer code changes.
- The "PNG anti-scraping" discovery is notable: some CDNs (megaplay.buzz,
  kotocdn.site) prepend PNG image headers to HLS segments to prevent
  direct downloading. The extension's `LocalProxyServer` strips these
  before serving to MPV; `HlsDownloader` does the same for downloads.
  Without this, downloads would produce files starting with PNG magic
  bytes → falsely rejected as "corrupt."
- The internal-cache-first pipeline (download → validate → publish to SAF)
  ensures the user's folder NEVER contains partial/corrupt files. A
  "completed" task always has a real, validated video on disk.
- The `episodeDownloadStates` Flow is collected once per screen (not per
  row) — the UI builds a local lookup map keyed by `"$contentId|$episodeNumber"`
  for O(1) row state.
- All logs use tag `AnikutaDownload` — filter with `adb logcat -s AnikutaDownload:V`.

---

## :core:backup

**Purpose:** Backup/restore engine — the aggregation point for all backup
data. Format-agnostic (ANIKUTA zip + Aniyomi protobuf read). Per ADR-028
+ ADR-036.

**Dependencies:** `:core:common`, `:core:database`, `:core:preferences`,
`:core:player` (WatchProgressStore), `:core:episode-metadata` (EpisodeMetadataCache),
`:core:tracker` (TrackerBackupProvider + TrackRepository), `:core:anilist`
(AniListApi for Aniyomi translation), `kotlinx-serialization-json:1.9.0`,
`kotlinx-serialization-protobuf:1.9.0` (for Aniyomi compat), `kotlinx-coroutines-core:1.10.1`,
OkHttp (cover downloads), Koin, `androidx.work:work-runtime-ktx:2.10.0`
(auto-backup), `androidx.documentfile:documentfile:1.0.1` (SAF).
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.
- Phase 8 fix: removed `:data:extension` dep — `SourceLinkBackupProvider`
  now injects the `SourceLinkBackupAccess` interface declared here; the
  impl (`SourceLinkBackupAccessImpl`) lives in `:data:extension` and is
  Koin-bound in `:app/.../di/ExtensionModule.kt`. This is the same
  architectural inversion as `EpisodeFetchGateway`.

**Status:** active. README says "Implementation complete (Agent 1 — Backup & Restore)."

**Key files:**
- `BackupProvider.kt` — the per-data-source contract (`id`, `export():
  BackupEntry`, `import(entry): Boolean`).
- `BackupEntry.kt` — sealed class with 10 subclasses (`Library`,
  `AnimeDetails`, `Episodes`, `EpisodeMetadata`, `WatchProgress`,
  `SourceLinks`, `Tracker`, `Categories`, `Preferences`, `CoverImages`).
  `providerId` is a computed `val` (no backing field → not serialized).
- `BackupFormat.kt` — interface (`type`, `write(container, covers, output)`,
  `read(input): BackupContainer`, `readCovers(input): Map<Int, ByteArray>`,
  `detect(input): Boolean`).
- `BackupFormatType.kt` — enum (`ANIKUTA`, `ANIYOMI`).
- `BackupCategory.kt` — enum of 10 user-selectable categories (library,
  anime_details, episodes, episode_metadata, watch_progress, source_links,
  tracker, categories, preferences, cover_images) with `defaultSelected`.
- `BackupOptions.kt` — `categories: Set<String>`, `format: BackupFormatType`.
  `includes(providerId)` helper.
- `BackupManager.kt` — the orchestrator. `createBackup(options, output)`:
  collect from all selected providers → download cover images if selected →
  build `BackupContainer` → write via `AnikutaBackupFormat`. `restoreBackup(input)`:
  detect format → read container → validate schema → for each entry, find
  matching provider → call `import()`. Returns `RestoreSummary` with
  per-category results.
- `BackupStorage.kt` — SAF folder + file management (`hasFolder()`,
  `generateBackupName(isAuto)`, `createAutoBackupFile(name)`,
  `createManualBackupFile(name)`, list/delete backups).
- `BackupPreferences.kt` — auto-backup config (enabled, frequency,
  maxBackups, categories) + SAF folder URI + last auto-backup timestamp.
- `AutoBackupWorker.kt` — `CoroutineWorker` that performs automatic
  backups on a WorkManager periodic schedule. Reads `BackupPreferences`
  for category selection + creates backup in `ANIKUTA/auto_backup/`.
  Exits `Result.success()` (no-op) if no folder or disabled.
- `AutoBackupScheduler.kt` — enqueues/cancels the WorkManager periodic work.
- `BackupResult.kt` — sealed (`Success<T>` / `Error`) + `CreateSummary`,
  `RestoreSummary`, `CreateCategoryResult`, `RestoreCategoryResult`.
- `RestoreSummary.kt` — per-category restore stats.
- `format/AnikutaBackupFormat.kt` — the ANIKUTA `.anikuta` format (ZIP
  containing `meta.json.gz` — gzipped JSON of `BackupContainer` + optional
  `covers/<anilistId>.jpg` files).
- `format/AniyomiBackupFormat.kt` — Aniyomi `.tachibk` protobuf reader
  (restore-only). Uses `kotlinx-serialization-protobuf` + minimal model
  classes in `format/aniyomi/`. Matches anime to AniList IDs via tracker
  entries.
- `format/BackupFormatDetector.kt` — auto-detects format by peeking first
  bytes (ZIP magic vs protobuf).
- `format/aniyomi/AniyomiBackupModels.kt` — minimal `@Serializable`
  protobuf models for Aniyomi backup.
- `translation/AniyomiBackupTranslator.kt` — translates Aniyomi backup
  → ANIKUTA `BackupContainer`.
- `model/BackupContainer.kt` — the serializable container (`entries:
  List<BackupEntry>`, `schemaVersion: Int`, `createdAt: Long`,
  `appVersion: String`).
- `model/AnimeBackup.kt`, `EpisodeBackup.kt`, `CategoryBackup.kt`,
  `EpisodeMetadataBackup.kt`, `WatchProgressBackup.kt`,
  `SourceLinkBackup.kt`, `TrackerBackupModel.kt`, `PreferenceBackup.kt`,
  `AnimeCategoryBackup.kt` — per-category backup models.
- `provider/AnimeBackupProviders.kt` — `LibraryBackupProvider` +
  `AnimeDetailsBackupProvider` (read from `animes` table).
- `provider/EpisodeBackupProvider.kt` — reads from `episodes` table.
- `provider/CategoryBackupProvider.kt` — reads from `categories` +
  `anime_category` tables.
- `provider/EpisodeMetadataBackupProvider.kt` — reads from
  `EpisodeMetadataCache`.
- `provider/WatchProgressBackupProvider.kt` — reads from `WatchProgressStore`.
- `provider/SourceLinkBackupProvider.kt` — reads from `SourceLinkBackupAccess`
  (interface — impl in `:data:extension`).
- `provider/TrackerBackupProviderAdapter.kt` — adapts
  `:core:tracker.TrackerBackupProvider` to the `BackupProvider` contract.
- `provider/PreferencesBackupProvider.kt` — reads `PreferenceStore.getAll()`.
- `provider/CoverImageProvider.kt` + `CoverDownloader.kt` — downloads
  cover images via OkHttp for self-contained backups.
- `provider/BackupMappers.kt` — DB row ↔ backup model mappers.
- `provider/SourceLinkBackupAccess.kt` — interface declared here; impl in
  `:data:extension/SourceLinkBackupAccessImpl`. Phase 8 architectural fix.
- `di/BackupModule.kt` — Koin module. **Critical:** all 10 providers are
  bound as a single `single<List<BackupProvider>>(named("backupProviders"))`
  (NOT 10 separate `single<BackupProvider>` — in Koin, multiple same-type
  `single<T>` with no qualifier OVERWRITE each other, leaving only the
  last one. This was the root cause of "only 1 category saved.")

**Public API:**
- `BackupProvider` interface + `BackupEntry` sealed class + 10 subclasses.
- `BackupFormat` interface + `BackupFormatType` enum.
- `BackupCategory` enum + `BackupOptions`.
- `BackupManager` (`createBackup(options, output): BackupResult<CreateSummary>`,
  `restoreBackup(input): BackupResult<RestoreSummary>`).
- `BackupStorage`, `BackupPreferences`, `AutoBackupWorker`,
  `AutoBackupScheduler`.
- `BackupResult`, `CreateSummary`, `RestoreSummary`,
  `CreateCategoryResult`, `RestoreCategoryResult`.
- `AnikutaBackupFormat`, `AniyomiBackupFormat`, `BackupFormatDetector`,
  `AniyomiBackupTranslator`.
- `BackupContainer`, `AnimeBackup`, `EpisodeBackup`, `CategoryBackup`,
  `EpisodeMetadataBackup`, `WatchProgressBackup`, `SourceLinkBackup`,
  `TrackerBackupModel`, `PreferenceBackup`, `AnimeCategoryBackup`.
- `SourceLinkBackupAccess` interface (impl in `:data:extension`).
- `CoverImageProvider`, `CoverDownloader`.
- `backupModule` Koin module.

**Notes:**
- The `.anikuta` file is a ZIP containing `meta.json.gz` (gzipped JSON of
  the polymorphic `BackupEntry` sealed class) + optional `covers/<anilistId>.jpg`
  files. Self-contained backups include covers; standard backups reference
  URLs.
- Aniyomi compatibility is **restore-only** — the app can import Aniyomi
  `.tachibk` protobuf backups but does NOT write them. Anime are matched
  to AniList IDs via their tracker entries.
- The 10-provider Koin `List<BackupProvider>` binding is a footgun —
  multiple `single<BackupProvider>` with no qualifier would silently
  overwrite. The module comment explicitly warns against this.
- The auto-backup WorkManager worker runs on a periodic schedule
  (`AutoBackupScheduler`). If no folder is selected or auto-backup is
  disabled, the worker exits `Result.success()` (no retry — the condition
  won't change without user action).
- Adding a new backup category: (1) add `BackupCategory` enum entry,
  (2) add `BackupEntry` subclass, (3) create `BackupProvider` impl in
  `provider/`, (4) register in `BackupModule.kt`'s `listOf(...)`.
  `BackupManager` picks it up automatically.

---

## :core:video-resolver

**Purpose:** Pure-Kotlin logic + types for the video resolver — the
3-tier hierarchy (Server → Audio → Quality) produced from a flat
`List<Video>`. Phase 8 module-boundary fix (Doc 04 violations 3+4):
moved logic-only types out of `:feature:video-resolver` so `:feature:watch`
+ `:feature:download` can depend on this core module instead of on each other.

**Dependencies:** `:core:source-api`, `kotlinx-coroutines-core:1.10.1`.
- Plugin: `anikuta.library` (no Compose, no serialization plugin).

**Status:** active.

**Key files:**
- `VideoResolverStrategy.kt` — strategy interface. Two impls:
    - `StructuredResolverStrategy` — groups videos into 3-tier hierarchy
      via `VideoTitleParser.groupVideosByServer`. Server names from
      hoster name → title parsing → auto-named "Server A/B/...". Returns
      empty if ALL videos unparseable (caller falls back to raw).
    - `RawResolverStrategy` — flat list, no forced structure. Per user
      feedback: "if it cannot make any sense of things... it should not
      do any formatting on it at all."
    - `ResolverStrategyPicker` — auto-detects: if ≥50% of videos have
      structure (server name, audio tokens, quality) OR hoster names are
      available → structured; else raw.
- `ResolverService.kt` — resolves videos from `AnimeSource` + `SEpisode`.
  Handles both legacy `getVideoList(episode)` (ext-lib < 16) AND new
  `getHosterList(episode)` + `getVideoList(hoster)` (ext-lib 16+).
  Key fix for structured extensions (like AnikotoS): when a hoster's
  `Hoster.videoList` is already populated, uses those videos directly
  instead of calling `getVideoList(hoster)`. Picks strategy via
  `ResolverStrategyPicker`; falls back to raw if structured returns empty.
- `VideoResolverState.kt` — the state machine driving `VideoResolverSheet`
  (which stays in `:feature:video-resolver`). Sealed `Hidden` / `Resolving`
  / `Show` / `NoSources` / `Error`. Plus the resolver hierarchy data
  types: `ResolverServer`, `ResolverAudioVersion`, `ResolverVideo`,
  `SubtitleTrack`.
- `VideoTitleParser.kt` — title parsing + grouping logic. Regexes for
  quality (`\b(\d{3,4})p\b`) + audio (`\b(SUB|DUB|HSUB|HARDSUB|...)\b`).

**Public API:**
- `VideoResolverStrategy` interface + `StructuredResolverStrategy`,
  `RawResolverStrategy`, `ResolverStrategyPicker`.
- `ResolverService` (`resolve(source, episode): ResolverResult`).
- `VideoResolverState` sealed + `ResolverServer`, `ResolverAudioVersion`,
  `ResolverVideo`, `SubtitleTrack`.
- `ResolverResult` sealed (`Success(servers)`, `NoSources`, `Error(message)`).
- `VideoTitleParser`.

**Notes:**
- This module is small (4 files) but architecturally important — it
  resolves a circular feature→feature dep that existed in earlier phases.
  The UI sheet (`VideoResolverSheet`, `ResolverServerContent`,
  `ResolverStates`) stays in `:feature:video-resolver`; only the logic
  + types moved here.
- The strategy picker's "≥50% structured" heuristic is per user feedback:
  extensions with messy video titles should show a flat list, not force
  incorrect grouping.
- The legacy `getVideoList(episode)` API is supported alongside ext-lib
  16's `getHosterList(episode)` + `getVideoList(hoster)` — extensions
  compiled against either version work.

---

## :core:ads

**Purpose:** On-device-tracked advertising system with user-configurable
quota/cooldown/min-stay. Highly customizable — every aspect controlled by
user-facing settings. No data is sent to any server.

**Dependencies:** `:core:common`, `:core:preferences`,
`kotlinx-coroutines-core:1.10.1`, `androidx.core:core-ktx:1.15.0`,
`androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, Koin.
- Plugin: `anikuta.library`.

**Status:** active.

**Key files:**
- `AdManager.kt` — the central orchestrator + state machine
  (`AdInteractionState` sealed: `Idle`, `DialogShowing`, `AdInProgress`,
  `ReturnedTooEarly`, `Completed`, `Cancelled`). `shouldShowAd()` checks
  enabled + daily quota + cooldown. `startAdDialog()`, `acceptAd()`,
  `cancelAd(): Boolean`, `onAdReturn(): Boolean` (counts ad only if user
  stayed ≥ `minStaySeconds`), `dismissTooEarly()`, `cancelFromTooEarly()`,
  `forceReset()`, `getRemainingCooldownMs()`.
- `AdTracker.kt` — on-device tracker. Privacy: ALL tracking is on-device
  only — no data sent to any server. Tracks `adsShownToday` (resets at
  midnight via `resetDailyIfNeeded`), `lastAdTimestamp`, `totalAdsShown`
  (lifetime), `lastResetDate`. `recordAdView()` increments all three +
  sets timestamp. Observable Flows for UI display.
- `AdsPreferences.kt` — user-configurable prefs:
    - `adsEnabled` (master on/off, default true)
    - `dailyAdQuota` (1–1000, default **1000** testing mode — production
      would be 1–10)
    - `cooldownMinutes` (0–1440, default 30)
    - `minStaySeconds` (1–60, default 2)
    - `adUrl` (default `https://www.effectivecpmnetwork.com/...`)
    - `adName: AdName` (`POISON` ☠️ or `PILLS` 💊 — controls emoji + title
      text)
    - `adTiming: AdTiming` (`APP_OPEN`, `EPISODE_START`, `BOTH`)
- `AdBranding.kt` — `AdName` enum (POISON ☠️ "Your daily dose of poison is
  here." / PILLS 💊 "Your daily dose of pills is here.") + `AdTiming` enum.
- `di/AdsModule.kt` — Koin module binding `AdsPreferences` + `AdTracker`
  + `AdManager` (all singletons — process-wide state).

**Public API:**
- `AdManager` + `AdInteractionState` sealed.
- `AdTracker` (`resetDailyIfNeeded()`, `getAdsShownToday()`,
  `getLastAdTimestamp()`, `getTotalAdsShown()`, `recordAdView()`,
  `observeAdsShownToday()`, `observeLastAdTimestamp()`,
  `observeTotalAdsShown()`, `resetAll()`).
- `AdsPreferences` + getters/setters/observers for all 7 prefs.
- `AdName`, `AdTiming`.
- `adsModule` Koin module.

**Notes:**
- The `AdInteractionState` state machine is the core abstraction — it
  tracks the full lifecycle (Idle → DialogShowing → AdInProgress →
  Completed/Cancelled/ReturnedTooEarly → Idle) and ensures ads are only
  counted when the user actually stayed for the minimum time.
- The "ReturnedTooEarly" state is notable — if the user closes the browser
  before `minStaySeconds`, a "please stay longer" message is shown and
  the ad is NOT counted. The user can retry or cancel.
- The default daily quota of 1000 is intentionally high for testing —
  production would be 1–10.
- The `AppController` (in `:app`) calls `evaluateAdGate` before every
  anime-detail navigation; if an ad should be shown, it stores the
  deferred navigation lambda + calls `startAdDialog`.
- All tracking is on-device — this is explicitly called out in `AdTracker`'s
  docstring for privacy.

---

## :core:app-update

**Purpose:** App self-update system — checks GitHub Releases for newer
versions, downloads the APK, launches the system installer. Per the
APP-UPDATE-SYSTEM doc.

**Dependencies:** `:core:common`, `:core:preferences`,
`kotlinx-coroutines-core:1.10.1`, `kotlinx-serialization-json:1.9.0`,
`com.squareup.okhttp3:okhttp:5.0.0-alpha.14`, `androidx.core:core-ktx:1.15.0`,
`androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, Koin.
- Plugins: `anikuta.library` + `kotlin.plugin.serialization`.

**Status:** active.

**Key files:**
- `AppUpdateManager.kt` — the central orchestrator. StateFlows:
  `latestUpdate: AppUpdateInfo?`, `downloadProgress: DownloadProgress?`,
  `isChecking: Boolean`, `lastCheckError: String?`. Methods:
  `checkForUpdateOnStartup()` (respects auto-check enabled + 6-hour
  dismiss cooldown), `checkForUpdate(): AppUpdateInfo?` (manual, always
  runs), `shouldShowDialog(): Boolean`, `dismissUpdate()`,
  `startDownload()` (delegates to `UpdateDownloader`, records APK in
  `AppUpdatePreferences` on completion), `installDownloadedApk(path)`
  (records pending-post-install marker, launches system installer),
  `cleanupOldDownloads()` (deletes APKs whose version ≤ installed),
  `deleteAllDownloadedApks()` (post-install cleanup), `parseVersionCode(name)`
  (`major*10000 + minor*100 + patch`).
- `UpdateSource.kt` — pluggable source interface (`id`,
  `fetchLatestUpdate(currentCode, currentName): AppUpdateInfo?`).
- `GitHubUpdateSource.kt` — checks `https://api.github.com/repos/{owner}/{repo}/releases/latest`.
  Parses `tag_name` (strips `v`), `name`, `body` (changelog),
  `published_at`, finds first `.apk` asset → `browser_download_url` +
  `size`. Version comparison via parsed `versionCode`. GitHub API rate
  limit: 60 req/hour unauthenticated (sufficient for once-on-startup).
  Configured for `owner = "Confused-Creature-180"`, `repo = "APP_BETA"`.
- `UpdateModels.kt` — `AppUpdateInfo` (versionName, versionCode,
  downloadUrl, changelog, releaseDate, source, apkSizeBytes, releaseName),
  `DownloadProgress` (bytesDownloaded, totalBytes, percent,
  speedBytesPerSec, isComplete, error) with companion factories
  (`downloading`, `complete`, `error`), `DownloadedApk` (versionName,
  filePath, downloadedAt, sizeBytes, source).
- `UpdateDownloader.kt` — OkHttp streaming download with progress Flow.
  `getApkFile(versionName): File`, `clearAllDownloads()`.
- `ApkInstaller.kt` — launches the system installer via `ACTION_INSTALL_PACKAGE`
  (or `ACTION_VIEW` with `application/vnd.android.package-archive`).
- `AppUpdatePreferences.kt` — `isUpdateCheckEnabled`, dismiss cooldown
  tracking (`isDismissedInCooldown(versionName)`,
  `recordDismissal(versionName)` — 6-hour cooldown), last check timestamp,
  downloaded APKs list (`addDownloadedApk`, `removeDownloadedApk`,
  `getDownloadedApkPath(versionName)`, `isVersionDownloaded(versionName)`),
  pending-post-install marker (`setPendingPostInstall(versionName)`,
  `consumePendingPostInstall()`).
- `di/AppUpdateModule.kt` — Koin module. Binds `AppUpdatePreferences`,
  `GitHubUpdateSource` (qualified `"github"`), dedicated `OkHttpClient`
  (qualified `"appUpdate"` — 60s read, follows redirects, separate from
  extension NetworkHelper), `List<UpdateSource>` (priority order — first
  non-null wins), `AppUpdateManager`.

**Public API:**
- `AppUpdateManager` + StateFlows + all methods above.
- `UpdateSource` interface + `GitHubUpdateSource`.
- `AppUpdateInfo`, `DownloadProgress`, `DownloadedApk`.
- `UpdateDownloader`, `ApkInstaller`.
- `AppUpdatePreferences`.
- `appUpdateModule` Koin module.

**Notes:**
- The pluggable `UpdateSource` design means a future `CustomJsonUpdateSource`
  or `FirebaseRemoteConfigSource` can be added with one class + one Koin
  line — no manager changes.
- The 6-hour dismiss cooldown prevents the update dialog from re-appearing
  immediately after the user dismisses it (but a manual check from Settings
  bypasses the cooldown).
- The "pending post-install" marker is the trick that lets the app show
  a success popup + clean up the downloaded APK after the user installs
  an update — the marker is set BEFORE launching the installer, then
  checked + cleared on next startup.
- The `cleanupOldDownloads` heuristic (delete APKs whose version ≤
  installed) handles the "just installed" case: after an update install,
  the APK file is still on disk, but its version matches the now-installed
  version, so it's deleted.
- The `deleteAllDownloadedApks` is more aggressive — called by the
  post-install popup because the GitHub release tag version (e.g., "0.3.0"
  → code 300) doesn't match the APK's actual build versionCode (e.g., 7),
  so the version comparison would fail to delete.

---

## :core:network  (REMOVED — Phase 9)

**Purpose:** (Originally planned) HTTP client + interceptors + rate limiting.

**Status:** **REMOVED** in Phase 9. `settings.gradle.kts` does NOT
`include(":core:network")`. The folder + README remain for archaeology.

**Why removed:** Networking (OkHttp client, `NetworkHelper`, rate-limit
interceptors, user-agent interceptor, etc.) was implemented inside
`:core:source-api` to match the Aniyomi extension contract — extensions
call `Injekt.get<NetworkHelper>()`, and `NetworkHelper` must be a class
in the `eu.kanade.tachiyomi.network` package per the extension compat
fix. A separate `:core:network` module would have either (a) duplicated
the `NetworkHelper` or (b) created a circular dep.

**What's here now:** Just `core/network/README.md` (explaining why it's
a stub) + `core/network/src/main/AndroidManifest.xml` (empty) +
`core/network/build.gradle.kts` (anikuta.library, no deps). 0 `.kt` files.

**Future:** A refactor could extract shared networking (the `:core:anilist`
client has its own OkHttp instance) into this module, but it's not
currently needed.

---

## :core:notification  (REMOVED — Phase 9)

**Purpose:** (Planned) Episode-release notification channels + WorkManager
scheduling (ADR-014 — dual-mode: AniList fire-and-forget at scheduled
release time vs Extension poll-with-backoff).

**Status:** **REMOVED** in Phase 9. `settings.gradle.kts` does NOT
`include(":core:notification")`. The folder + README remain for archaeology.

**Why removed:** ADR-014 specifies a feature that was never implemented.
The `:core:update-checker` module already implements the new-episode
*detection* logic (manual checking). The notification *scheduling*
(WorkManager) + the notification channels + the per-series/global preference
UI are the missing pieces that would live here.

**What's here now:** Just `core/notification/README.md` (explaining why
it's a stub + the dual-mode spec) + `core/notification/src/main/AndroidManifest.xml`
(empty) + `core/notification/build.gradle.kts` (anikuta.library, no deps).
0 `.kt` files.

**Note:** Download notifications are a SEPARATE concern — they're already
implemented in `:core:download/DownloadNotificationManager.kt` and are
NOT part of this module's scope.

**Future:** Re-add when episode-release notifications are implemented per
ADR-014. The roadmap is Phase 9+.

---

## :core:source-local  (REMOVED — Phase 9)

**Purpose:** (Planned) A "local source" that treats files on the device's
storage as an anime source (like Aniyomi's local source) — lets users add
their own downloaded video files as a browsable "source" in browse/library.

**Status:** **REMOVED** in Phase 9. `settings.gradle.kts` does NOT
`include(":core:source-local")`. The folder + README remain for archaeology.

**Why removed:** Never implemented. Lower-priority future-work item — the
architecture reserves a slot for it per `ARCHITECTURE.md §3`, but it's
not explicitly on the Phase 9 roadmap yet.

**What's here now:** Just `core/source-local/README.md` (explaining why
it's a stub + the distinction from the downloads system) +
`core/source-local/src/main/AndroidManifest.xml` (empty) +
`core/source-local/build.gradle.kts` (anikuta.library, no deps). 0 `.kt` files.

**Note:** This is distinct from `:core:download` (which downloads
episodes FROM extension sources for offline playback). The local source
would let users bring their own files independent of any extension.

**Future:** Re-add when local-files-as-source is implemented. Would
implement `AnimeSource` (from `:core:source-api`) backed by a SAF folder
scan instead of an HTTP API.

---

## Cross-cutting observations

### Dependency graph (simplified)

```
:core:common ──────────────────────────────────── foundation
   ↑
:core:preferences  :core:database  :core:provider-api
   ↑                  ↑                ↑
:core:designsystem  :core:tracker  :core:anilist ── :core:provider-api
   ↑                  ↑    ↑          ↑    ↑
:core:player ───── :core:source-api  :core:episode-metadata
   ↑                  ↑                ↑
:core:video-resolver  :core:download  :core:update-checker
                       ↑                ↑
                    :core:backup ───────┘ (also: tracker, player, episode-metadata, anilist, database, preferences)
:core:ads  :core:app-update (depend only on :core:common + :core:preferences)
```

### Architectural patterns

1. **Two-tier identity (ADR-050)** — `LocalId` (per-source) + `ContentId`
   (per-content, survives source switches). Cross-cutting stores migrated
   to key off `ContentId`:
   - `WatchProgressStore` key: `"$contentId|$episodeNumber"`
   - `PlaybackStateStore` key: same
   - `DownloadTask.key`: same
   - `EpisodeMetadataCache` key: `contentId`
   - `TrackSyncManager` parses `al:<anilistId>` from `ContentId` to sync

2. **Pluggable registries via Koin multi-binding** — every extension
   point uses `single<List<T>>`:
   - `List<MetadataProvider>` (provider-api)
   - `List<AnimeDetailsProvider>` (common)
   - `List<EpisodeMetadataSource>` (episode-metadata — via `register()`
     not Koin, but same idea)
   - `List<BackupProvider>` (backup — `named("backupProviders")`)
   - `List<UpdateSource>` (app-update)
   Adding a new provider = one class + one entry in the `listOf(...)`.
   No manager/registry changes.

3. **Architectural inversion via gateway interfaces** — when a `:core:*`
   module needs `:data:*` functionality, an interface is declared in
   `:core:*` and implemented in `:data:*`, injected via Koin:
   - `EpisodeFetchGateway` (declared in `:core:update-checker`, impl in
     `:data:extension`)
   - `SourceLinkBackupAccess` (declared in `:core:backup`, impl in
     `:data:extension`)
   - `TrackerBackupProvider` (declared in `:core:tracker`, impl in
     `:core:tracker` itself — but adapted via `TrackerBackupProviderAdapter`
     in `:core:backup`)

4. **Aniyomi extension binary compat (ADR-029)** — `:core:source-api`
   ships the exact `eu.kanade.tachiyomi.animesource.*` package. Every
   class/interface/field declaration is constrained by what extensions
   were compiled against. The build comments document each constraint
   (e.g., `NetworkHelper` MUST be a class not interface; `network` MUST
   be `by injectLazy()`; `-Xcontext-receivers` enabled for
   `Response.parseAs<T>()`).

5. **Dispatchers injected** — `DispatcherProvider` (in `:core:common`)
   lets tests swap dispatchers. All network/DB work on `Dispatchers.IO`
   per `RULES §9`.

6. **Reactive everything** — `Preference<T>.changes(): Flow<T>`,
   `WatchProgressStore.changes`, `AnimeRepository.observeAll()`,
   `DownloadManager.activeDownloads`, `AdManager.state`,
   `AppUpdateManager.latestUpdate` / `downloadProgress`. ViewModels
   compose these via `combine` / `map` — no polling.

7. **Phase markers in source comments** — many files document their
   phase ("Phase 3 ADR-050 migration", "Phase 8 Doc 04 violation fix",
   "Session 1 item 9.5") — useful for tracing why a particular decision
   was made.

### For the rebuild

The core layer is the part most worth porting nearly verbatim. The two-tier
identity system, the pluggable registries, the architectural inversion
pattern, and the Aniyomi extension binary compat are all architectural
decisions that should be preserved. The modules to scrutinize most
carefully during the rebuild:

- `:core:common` — the domain model is denormalized and the
  `AnimeRepository` has both AniList-keyed AND identity-keyed methods
  (transitional API). A rebuild could clean this up.
- `:core:source-api` — the binary compat constraints are subtle; every
  declaration is load-bearing. Don't refactor without re-reading the
  build comments.
- `:core:download` — the internal-cache-first pipeline + PNG anti-scraping
  + multi-method `DownloadManager` interface are the result of hard-won
  production experience.
- `:core:backup` — the 10-provider `List<BackupProvider>` Koin binding is
  a footgun (multiple `single<T>` with no qualifier overwrite). The
  rebuild should preserve the `named("backupProviders")` pattern.
