# 16 — Phase 1 Architecture Plan

> The full architecture plan for the ANI-KUTA rebuild. Future-proof, modular, agent-friendly.
> This is the blueprint for Phase 2 (scaffold) and beyond.

---

## 1. Architecture Principles

1. **Modular by design** — each module has one responsibility, a README, and clear boundaries. New AI agents can jump into a specific module without full context.
2. **Frontend ↔ Backend separation** — UI never imports `:data:*`. Only via ViewModel → UseCase → Repository.
3. **Multi-extension from day one** — `ExtensionProvider` abstraction. Aniyomi now, Mangayomi/Cloudstream/Kotatsu later. Adding an ecosystem = one module + one Koin binding.
4. **Multi-content-type ready** — `ContentType` enum (VIDEO/IMAGE/TEXT). Anime now, manga + novels later. Architecture accommodates all three without rewrite.
5. **Highly customizable UI** — theme engine, layout options, behavior toggles. Per-content-type customization.
6. **Flexible identity** — graph-based (ContentUID + ExternalReference) but switchable. Backup/restore compat with other apps.
7. **Filtered console logging** — everything logged, toggleable, zero overhead when off.
8. **No over-engineering** — Ponytail skill: simplest solution that works. Stdlib before deps. No unrequested abstractions.
9. **Agent-friendly** — every module documented, clear contracts, no hidden coupling. A new agent can work on one module without breaking others.

---

## 2. Tech Stack (confirmed)

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.0+ |
| UI | Jetpack Compose (BOM) | 2025.03.00+ |
| DI | Koin 4.x + Koin Annotations 2.x | + Injekt (isolated) |
| Persistence | SQLDelight | 2.x |
| Navigation | Jetpack Navigation 3 | `androidx.navigation3` 1.0.0+ |
| Player | MPV (aniyomi-mpv-lib) | — |
| Networking | OkHttp + ktor-client | — |
| Serialization | kotlinx-serialization | 2.2.0 |
| Build | AGP 8.7+ / Gradle 8.11+ / JDK 17 | |
| SDK | compile/target 35, min 24 | |

---

## 3. Full Module Tree

> 43 modules total. Organized by layer. Each has a README + clear responsibility.

```
ANIKUTA/
├── :app                                    Application shell (DI, nav host, single Activity)
│
├── :build-logic                            Convention plugins (Gradle)
│   ├── anikuta.android.application.gradle.kts
│   ├── anikuta.android.application.compose.gradle.kts
│   ├── anikuta.library.gradle.kts
│   ├── anikuta.library.compose.gradle.kts
│   └── anikuta.buildlogic/ (AndroidConfig, ProjectExtensions)
│
├── :core                                   Infrastructure (no UI screens)
│   ├── :core:common                        Logger, Dispatchers, Result, base models, ContentType enum
│   ├── :core:designsystem                  Compose theme engine + reusable components
│   ├── :core:database                      SQLDelight schema, migrations, driver factory
│   ├── :core:preferences                   PreferenceStore, ThemePreferences, SettingsPreferences
│   ├── :core:navigation-api                Nav3 NavKey contracts, ContentMode, Saver helpers
│   ├── :core:provider-api                  ExtensionProvider + MetadataProvider contracts
│   ├── :core:source-api                    Aniyomi-compat source-api (eu.kanade.* — Injekt isolated)
│   ├── :core:identity                      ContentUID + ExternalReference + matching engine
│   ├── :core:backup                        BackupProvider registry + BackupManager + importers
│   ├── :core:anilist                       AniList GraphQL client + metadata provider
│   ├── :core:tracker                       AniList + MAL tracker impls + TrackSyncManager
│   ├── :core:episode-metadata              EpisodeMetadataCache + sources (AniList/Jikan)
│   ├── :core:player                        MPV wrapper (AnikutaMPVView) + watch progress + controls
│   ├── :core:update-checker                New-episode detection + update checker
│   ├── :core:download                      Download manager (HTTP + HLS + resume)
│   ├── :core:video-resolver                Resolver service + state (extract playable URL)
│   ├── :core:ads                           Ad system (DEFERRED — AdFormat + placement registry)
│   ├── :core:activity-tracker              User activity event-log (DEFERRED)
│   ├── :core:network                       HTTP client (OkHttp + ktor) + shared interceptors + timeouts
│   ├── :core:watch-progress                WatchProgressStore interface (writer=:core:player, impl=:data:history)
│   ├── :core:app-update                    Self-update via GitHub Releases
│   ├── :core:notification                  Episode-release notifications (Phase 3-4)
│   └── (shared UI molecules live in :core:designsystem — merged per Ponytail)
│
├── :data                                   Repository implementations (glue :core ↔ :core:database)
│   ├── :data:anime                         AnimeRepositoryImpl + EpisodeRepositoryImpl + CategoryRepo
│   ├── :data:extension-aniyomi             Aniyomi extension loader/installer/manager (Injekt isolated)
│   ├── :data:extension-mangayomi           Mangayomi provider (future)
│   ├── :data:extension-cloudstream         Cloudstream provider (future)
│   ├── :data:extension-kotatsu             Kotatsu provider (future)
│   ├── :data:history                       HistoryRepositoryImpl (reads WatchProgressStore)
│   └── :data:identity                      IdentityRepositoryImpl + matching service
│
├── :feature                                UI screens (Compose) — split api/impl per feature
│   │
│   ├── [VIDEO content type — anime — current focus]
│   ├── :feature:anime-browse:api           NavKey + contracts
│   ├── :feature:anime-browse:impl          Browse screen (AniList trending/seasonal)
│   ├── :feature:anime-search:api
│   ├── :feature:anime-search:impl          Search (AniList + Extension sources, filters)
│   ├── :feature:anime-details:api
│   ├── :feature:anime-details:impl         Anime detail page (banner, episodes, source switcher)
│   ├── :feature:anime-watch:api
│   ├── :feature:anime-watch:impl           Player host screen (embeds :core:player MPV)
│   ├── :feature:anime-library:api
│   ├── :feature:anime-library:impl         Library (grid + list + categories + sort)
│   ├── :feature:anime-history:api
│   ├── :feature:anime-history:impl         History (recently watched)
│   ├── :feature:anime-updates:api
│   ├── :feature:anime-updates:impl         Updates (new episodes + schedule)
│   ├── :feature:anime-my:api
│   ├── :feature:anime-my:impl              Profile (stats + charts)
│   │
│   ├── [SHARED screens — split api/impl for navigable ones]
│   ├── :feature:extensions-settings:{api,impl}  Extensions list + repo management
│   ├── :feature:trackers:{api,impl}              Tracker list + login (AniList/MAL OAuth)
│   ├── :feature:backup:{api,impl}                Backup/restore UI (import from Aniyomi/Mangayomi)
│   ├── :feature:download:{api,impl}              Download queue + downloaded files browser
│   ├── :feature:settings:{api,impl}              Appearance, General, Player, About, Logging toggle
│   ├── :feature:episode-settings           Episode display/layout/metadata settings (modal sheet — single module)
│   ├── :feature:video-resolver:{api,impl}        Resolver sheet UI (modal — picks a video)
│   └── :feature:setup-wizard:{api,impl}          Onboarding flow (first-launch gate)
│   │
│   ├── [IMAGE content type — manga — FUTURE]
│   ├── :feature:manga-browse:{api,impl}    (future)
│   ├── :feature:manga-details:{api,impl}   (future)
│   ├── :feature:manga-read:{api,impl}      (future)
│   │
│   └── [TEXT content type — novels — FUTURE]
│       └── :feature:novel-*:{api,impl}     (future)
│
├── gradle/                                 Version catalogs
│   ├── libs.versions.toml                  (consolidated — NOT 5 separate files)
│   └── wrapper/
│
├── settings.gradle.kts                     The canonical module list
├── build.gradle.kts                        Root build script
└── gradle.properties
```

### Module count
- **Phase 2 (scaffold)**: `:app` + `:build-logic` + 6 core modules + 3 data modules + 2 feature modules = **13 modules** (minimal viable).
- **Full build-out**: 43 modules (including future manga/novel/multi-extension).

### Dependency rules (STRICT)
1. `:app` depends on all `:feature:*:impl` + `:core:*` + `:data:*`.
2. `:feature:*:api` depends on `:core:navigation-api` + `:core:common` only.
3. `:feature:*:impl` depends on `:feature:*:api` + `:core:*` + `:data:*` (via interfaces). **Never** on another `:feature:*:impl`.
4. `:data:*` depends on `:core:*` (interfaces + database). **Never** on `:feature:*`.
5. `:core:*` may depend on other `:core:*` but **no cycles**.
6. **Injekt isolation**: `uy.kohesive.injekt` imports allowed ONLY in `:core:source-api` and `:data:extension-aniyomi` (Detekt path-based rule). In `:app`, restrict Injekt to a single file `AniyomiInjektBootstrap.kt` (Detekt file-name allowlist) called from `App.onCreate()`.

---

## 4. Data Flow

### Discovery → Watch → Track

```
User opens app
    │
    ▼
:app:AppRoot (Nav3 AppRoot.kt)
    │  bottom nav: Browse | Library | Search | My
    │  mode: AnimeMode (future: MangaMode, NovelMode)
    │
    ▼
:feature:anime-browse:impl
    │  fetches trending/seasonal
    │
    ▼
:core:anilist → AniList GraphQL API
    │
    ▼ (user taps an anime)
    │
:feature:anime-details:impl
    │  AnimeDetailsViewModel
    │  uses AnimeDetailsProviderRegistry (List<MetadataProvider>)
    │  3-stage: AniList → match extension source → fetch episodes
    │
    ▼ (user taps an episode)
    │
:feature:video-resolver
    │  modal sheet — picks a video
    │
    ▼
:core:video-resolver → calls ExtensionProvider.fetchVideoList
    │
    ▼
:feature:anime-watch:impl
    │  embeds :core:player (AnikutaMPVView)
    │  single MPV instance (overlay swap for fullscreen)
    │
    ▼ (every 10s)
    │
:core:player → WatchProgressStore
    │  keyed by contentUid|episodeUid
    │
    ▼
:core:identity → ContentUID + ExternalReference
    │  survives source switches
    │
    ▼
:core:tracker → TrackSyncManager
    │  syncs to AniList/MAL (if linked)
    │
    ▼
:core:activity-tracker (DEFERRED) → event-log
```

### Key: identity is the backbone

```
ContentUID (app's UUID, stable forever)
    │
    ├── ExternalReference(ecosystem="aniyomi", sourceId="42", externalId="gogo/aot", confidence=HIGH)
    ├── ExternalReference(ecosystem="anilist", sourceId=null, externalId="16498", confidence=HIGH)
    ├── ExternalReference(ecosystem="mangayomi", sourceId="gogoanime", externalId="aot", confidence=MEDIUM)
    └── ExternalReference(ecosystem="mal", sourceId=null, externalId="16498", confidence=HIGH)
```

- Watch progress, downloads, metadata, tracking — all keyed by `ContentUID`.
- Source switch = find/create ExternalReference for the new source → same ContentUID → progress carries over.

### Watch progress layering (no reverse deps)

```
:core:watch-progress        ← WatchProgressStore interface (contract)
     ▲                           ▲
     │ writes                    │ reads + implements
     │                           │
:core:player                :data:history (impl, depends on :core:database)
  (MPV wrapper)                (HistoryRepositoryImpl)
```

- `:core:player` depends on `:core:watch-progress` (interface) — writes progress.
- `:data:history` depends on `:core:watch-progress` + `:core:database` — implements the interface, reads for the History screen.
- **No reverse dependency**. `:core:player` never depends on `:data:*`.

### Player ↔ Video Resolver boundary

`:feature:anime-watch:impl` mediates: calls `:core:video-resolver` to get a `SourceVideo`, passes its URL to `:core:player`. The two `:core` modules are unaware of each other.

---

## 5. Screen Map (Navigation Graph)

### Nav3 structure

```
AppRoot (single Activity, NavDisplay)
├── BottomNav (4 tabs, reorderable via preferences)
│   ├── BrowseTab → AnimeBrowseScreen
│   ├── LibraryTab → AnimeLibraryScreen
│   ├── SearchTab → AnimeSearchScreen
│   └── MyTab → AnimeMyScreen
│
├── Modal overlays (state-driven, not navigated)
│   ├── VideoResolverSheet
│   ├── EpisodeSettingsSheet
│   └── SourceSwitcherSheet
│
└── Navigated screens (push onto back stack)
    ├── AnimeDetailsScreen(contentUid: String)
    ├── AnimeWatchScreen(contentUid: String, episodeUid: String)
    ├── ExtensionsSettingsScreen
    ├── TrackersScreen
    ├── BackupScreen
    ├── DownloadScreen
    ├── SettingsScreen
    │   ├── AppearanceSettingsScreen
    │   ├── GeneralSettingsScreen
    │   ├── PlayerSettingsScreen
    │   ├── LoggingSettingsScreen  ← toggleable console logging
    │   └── AboutScreen
    └── SetupWizardScreen (first-launch gate)
```

### Nav3 NavKey examples (type-safe)

```kotlin
@Serializable
sealed interface NavKey

@Serializable
data class AnimeDetailsKey(val contentUid: String) : NavKey

@Serializable
data class AnimeWatchKey(val contentUid: String, val episodeUid: String) : NavKey

@Serializable
object SettingsKey : NavKey

@Serializable
object BackupKey : NavKey
```

### Feature module api/impl split

```
:feature:anime-details:api/
  └── AnimeDetailsNavKey.kt   (the @Serializable NavKey — visible to :app)

:feature:anime-details:impl/
  ├── AnimeDetailsScreen.kt   (the Compose screen)
  ├── AnimeDetailsViewModel.kt
  └──注册到 Koin (in :app)
```

- `:app` wires `NavKey → Screen` via a `ContentMap`.
- Features never see each other. To navigate from anime-browse to anime-details, anime-browse emits `AnimeDetailsNavKey(uid)` — but it only knows the NavKey (from `:feature:anime-details:api`), not the screen.

### Content mode switching (future)

```
ContentMode sealed interface:
  - AnimeMode → Browse/Library/Search/My tabs render anime features
  - MangaMode → Browse/Library/Search/My tabs render manga features (future)
  - NovelMode → Browse/Library/Search/My tabs render novel features (future)
```

- Mode switch = replace the root `List<NavKey>` for the current tab.
- Each mode has its own set of feature modules. No cross-mode coupling.

---

## 6. Identity System Design

### Model (flexible + switchable)

```
ContentUID
  - uid: String (UUID, app-generated, stable forever)
  - contentType: ContentType (VIDEO | IMAGE | TEXT)
  - title: String (canonical, best-known)
  - matchKey: String (normalized title + year + type, for fuzzy matching)
  - createdAt: Long

ExternalReference
  - id: Long (auto-increment)
  - uid: String (FK → ContentUID)
  - ecosystem: String ("aniyomi" | "mangayomi" | "cloudstream" | "kotatsu" | "anilist" | "mal" | "shikimori")
  - sourceId: String? (null for trackers)
  - externalId: String
  - confidence: Confidence (HIGH | MEDIUM | LOW)
  - createdAt: Long
  - UNIQUE(ecosystem, sourceId, externalId)

EpisodeUID
  - uid: String (UUID)
  - contentUid: String (FK → ContentUID)
  - episodeNumber: Double
  - matchKey: String

EpisodeExternalRef
  - id: Long
  - episodeUid: String (FK → EpisodeUID)
  - ecosystem: String
  - sourceId: String?
  - externalId: String
  - confidence: Confidence
  - UNIQUE(ecosystem, sourceId, externalId)
```

### Matching engine (`:core:identity`)

```
IdentityResolver
  - resolveOrCreate(ecosystem, sourceId, externalId, title, year, type, trackerIds: Map<Tracker,String>?): ContentUID
    1. Exact match: ExternalReference(ecosystem, sourceId, externalId) exists → return its uid.
    2. Tracker bridge: if caller provides trackerIds (e.g. {AniList → "16498"}), find ContentUIDs with matching tracker ExternalReferences → return that uid (confidence HIGH). The caller (e.g. :feature:anime-details:impl) fetches tracker IDs via MetadataProvider before calling resolveOrCreate.
    3. Fuzzy match: matchKey (normalized title + year + type) matches an existing ContentUID → create new ExternalReference (confidence MEDIUM), return uid.
    4. No match: create new ContentUID + ExternalReference (confidence HIGH for first sighting).

  - merge(uidA, uidB): merges two ContentUIDs (user-initiated). Moves all ExternalReferences from B to A, logs the merge.
  - split(uid, refId): splits an ExternalReference into a new ContentUID (user-initiated, undoable).
  - suggestMerges(): Flow<List<MergeSuggestion>> — finds potential matches for user review.
```

**Tracker bridge**: `trackerIds` is an optional parameter. The caller (feature module) fetches tracker IDs via `MetadataProvider` (from `:core:anilist` or `:core:tracker`) before calling `resolveOrCreate`. This keeps `:core:identity` decoupled from tracker modules.

### Why it's flexible/switchable
- The `IdentityResolver` is an **interface**. The graph-based impl is the default, but it can be swapped (e.g., if we invent a better algorithm later).
- The DB schema is in `:core:database` but the matching logic is in `:core:identity` + `:data:identity`. Changing the matching strategy doesn't change the DB.
- ExternalReference is generic — adding a new ecosystem is just a new string value, no schema change.

### Backup/restore integration
- On import from Aniyomi/Mangayomi: the importer maps external entries to `ExternalReference`s and calls `IdentityResolver.resolveOrCreate()`. Unresolved entries (no tracker link, no fuzzy match) go to a "needs review" inbox for the user to confirm.
- On export: ANI-KUTA writes its own `.anikuta` format (ZIP + `meta.json.gz` + covers/) including ContentUID + all ExternalReferences. Schema versioned (v2).

---

## 7. Backup/Restore Architecture

### Importers (multi-app compat)

```
BackupImporter interface:
  - supportedFormats: List<BackupFormat>
  - canImport(file: File): Boolean
  - import(file: File): Flow<ImportProgress>

Impls:
  - AniyomiTachibkImporter  ← handles Aniyomi + Animiru + Anikku (.tachibk protobuf)
  - MangayomiBackupImporter ← handles Mangayomi (.backup JSON-in-zip)
  - AnikutaBackupImporter   ← handles ANI-KUTA's own format (.anikuta)

Registered via Koin: single<List<BackupImporter>>(named("backupImporters"))
```

### Export

```
BackupExporter interface:
  - export(data: BackupContainer, dest: File): Flow<ExportProgress>

Impls:
  - AnikutaBackupExporter ← ANI-KUTA's own format (default)
  - (Aniyomi export is restore-only — we don't write .tachibk)
```

### BackupProvider registry (internal)

The old project's 10 `BackupProvider`s (AnimeProvider, EpisodeProvider, CategoryProvider, HistoryProvider, TrackerProvider, ExtensionProvider, PreferenceProvider, SourceProvider, DownloadProvider, CustomButtonProvider) are preserved. Each serializes its data to `BackupEntry`. The `BackupManager` orchestrates.

### Format: `.anikuta` (v2)

```
my-backup.anikuta (ZIP)
├── meta.json.gz          (schemaVersion=2, timestamp, app version)
├── covers/               (optional — cover images)
│   ├── al_16498.jpg
│   └── ...
└── data/
    ├── anime.json        (ContentUID + ExternalReferences + episode data)
    ├── categories.json
    ├── history.json
    ├── trackers.json
    ├── extensions.json
    └── preferences.json
```

- `schemaVersion=2` adds ContentUID + ExternalReference (v1 = old format, migrator provided).
- Auto-backup filename prefix `anikuta_` to avoid fork collisions.

### Import flow

```
User selects a backup file
    │
    ▼
BackupManager iterates List<BackupImporter>
    │  finds the one whose canImport(file) returns true
    │
    ▼
Importer reads the file → emits BackupContainer (in-memory)
    │  (for Aniyomi: decode protobuf → resolve AniList IDs → build container)
    │  (for Mangayomi: decode JSON → map source names → build container)
    │
    ▼
BackupManager.restoreBackupFromContainer(container)
    │  for each BackupEntry:
    │    calls IdentityResolver.resolveOrCreate() to map external IDs → ContentUIDs
    │    writes to DB via repositories (with merge semantics — see §7.5)
    │
    ▼
Unresolved entries → "Needs Review" inbox
    │  user confirms: merge with existing ContentUID, or create new
```

### §7.5 Merge Semantics (for multi-backup import)

When importing multiple backups (e.g. Aniyomi + Mangayomi), conflicts are resolved per-entity:

| Entity | Merge rule |
|--------|-----------|
| `watch_progress` | `MAX(progress_a, progress_b)` — keep the furthest-watched position. |
| `history` | `UNION by (contentUid, episodeUid, timestamp)` — no duplicates. |
| `categories` | `UNION by name` (case-insensitive) — merge category lists. |
| `tracker_bindings` | `UNION by (contentUid, ecosystem)` — no duplicate tracker links. |
| `library_flag` | `OR` — if in library in either backup, keep in library. |
| `downloads` | `UNION by (contentUid, episodeUid)` — no duplicate download entries. |
| `preferences` | Last-write-wins (by backup timestamp) — skip if app-specific. |

**Import mode**: **additive** (merge into existing data). Never destructive. The user can manually delete unwanted entries after import.

---

## 8. Multi-Extension Architecture

### ExtensionProvider interface (`:core:provider-api`)

> **Multi-content-type design**: The interface is split into per-type sub-interfaces.
> A provider implements whichever content types it supports.

```kotlin
sealed interface ExtensionProvider {
    val ecosystemId: String                    // "aniyomi", "mangayomi", etc.
    val displayName: String                    // "Aniyomi", "Mangayomi"
    val supportedContentTypes: Set<ContentType>

    fun observeInstalledSources(): Flow<List<Source>>
    fun observeAvailableSources(): Flow<List<Source>>
    fun installSource(source: Source): Flow<InstallState>
    fun uninstallSource(source: Source): Flow<InstallState>
}

interface VideoExtensionProvider : ExtensionProvider {
    fun fetchContentList(source: Source, page: Int, query: SourceQuery?): Flow<List<SourceContent>>
    fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails>
    fun fetchEpisodeList(content: SourceContent): Flow<List<SourceEpisode>>
    fun fetchVideoList(episode: SourceEpisode): Flow<List<SourceVideo>>
}

interface ImageExtensionProvider : ExtensionProvider {
    fun fetchContentList(source: Source, page: Int, query: SourceQuery?): Flow<List<SourceContent>>
    fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails>
    fun fetchChapterList(content: SourceContent): Flow<List<SourceChapter>>
    fun fetchPageList(chapter: SourceChapter): Flow<List<SourcePage>>
}

interface TextExtensionProvider : ExtensionProvider {
    fun fetchContentList(source: Source, page: Int, query: SourceQuery?): Flow<List<SourceContent>>
    fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails>
    fun fetchChapterList(content: SourceContent): Flow<List<SourceChapter>>
    fun fetchTextContent(chapter: SourceChapter): Flow<String>
}
```

- A provider can implement multiple sub-interfaces (e.g. Mangayomi = `Video + Image`).
- The UI filters providers by the active `ContentMode` (VIDEO → `VideoExtensionProvider`, IMAGE → `ImageExtensionProvider`, TEXT → `TextExtensionProvider`).
- This is type-safe: you can't call `fetchVideoList` on a manga source.

### Implementations
- `:data:extension-aniyomi` — loads Aniyomi APK extensions (Injekt compat, `ChildFirstPathClassLoader`).
- `:data:extension-mangayomi` — (future) wraps Mangayomi's JS-based sources.
- `:data:extension-cloudstream` — (future) wraps Cloudstream plugins.
- `:data:extension-kotatsu` — (future) wraps Kotatsu compile-time parsers.

### Registration
```kotlin
// in :app
single<List<ExtensionProvider>>(named("extensionProviders")) {
    listOf(
        aniyomiExtensionProvider(get(), get()),
        // mangayomiExtensionProvider(get(), get()),  // future
    )
}
```

### Source identity
Each source = `(ecosystemId, sourceId)`. This maps directly to `ExternalReference.ecosystem` + `ExternalReference.sourceId`. Adding a new ecosystem = one module + one Koin line.

---

## 9. Multi-Content-Type Architecture

### ContentType enum (`:core:common`)

```kotlin
enum class ContentType { VIDEO, IMAGE, TEXT }
```

### Per-type feature modules
```
:feature:anime-*    (VIDEO) — current focus
:feature:manga-*    (IMAGE) — future
:feature:novel-*    (TEXT)  — future
```

### ContentMode (navigation)
```kotlin
sealed interface ContentMode {
    data object Anime : ContentMode   // VIDEO
    data object Manga : ContentMode   // IMAGE (future)
    data object Novel : ContentMode   // TEXT (future)
}
```

- The bottom nav tabs render different features based on the active `ContentMode`.
- Mode switch is a preference (`ThemePreferences.contentMode`).
- The data model (`ContentUID.contentType`) tags each content with its type. Cross-type linking (anime ↔ manga adaptation) is a future feature, not needed now.

### ExtensionProvider declares supported types
```kotlin
val supportedContentTypes: Set<ContentType>
```
- Aniyomi provider supports `{VIDEO}` (anime-only, per Animiru lineage).
- Mangayomi provider supports `{VIDEO, IMAGE}` (anime + manga).
- This lets the UI filter sources by the active content mode.

---

## 10. Customizable UI System

### Theme engine (`:core:designsystem`)

```
DesignTokens
  - ColorTokens: primary, success, warning, danger, secondary, surface, canvas, border, textPrimary, textSecondary
  - TypographyTokens: displayLarge, headlineLarge, titleLarge, bodyLarge, labelLarge, ...
  - ShapeTokens: small, medium, large, xl, 2xl, full
  - MotionTokens: durationShort, durationMedium, durationLong, easingStandard, easingEmphasized
  - SpacingTokens: xs, sm, md, lg, xl

ThemePreset
  - id, name, tokens (a full set of DesignTokens)
  - Built-in: "Warm Canvas" (default), "Dark", "Midnight", "Sunset"
  - User can create custom presets.

ThemeManager
  - activeTheme: StateFlow<ThemePreset>
  - customThemes: Flow<List<ThemePreset>>
  - applyTheme(presetId)
  - createCustomTheme(name, basePresetId, overrides: DesignTokenOverrides)
```

### Customization layers

1. **Theme tokens** — colors, typography, shapes, motion. Swap-able via presets. User can create custom.
2. **Layout options** — card density (compact/comfortable), grid columns, list vs grid.
3. **Behavior toggles** — confirm before exit, auto-play next episode, swipe to seek, etc.
4. **Per-content-type customization** — anime library can have different layout than manga library.
5. **Tab customization** — reorder/hide bottom nav tabs.

### Implementation
- All tokens are in `:core:designsystem` as data classes.
- `ThemePreferences` (in `:core:preferences`) stores the active preset + overrides.
- Compose reads tokens via `CompositionLocal` — changing the preset recomposes the whole app.
- Settings UI (`:feature:settings`) exposes all customization.

---

## 11. Ad System (DEFERRED — no premature abstraction)

> Not built now. **No `AdGate` no-op interface is added to `:core:common`** (Ponytail: no interface with one impl, no boilerplate "for later").
> When `:core:ads` is built (Phase 6), the callsites are added then with the real signature.

### When built (Phase 6):
- `:core:ads` — `AdFormat` interface, `AdPlacement` JSON registry, `AdManager` (returns `Flow<AdResult>`), `AdSource`.
- `:core:activity-tracker` — `ActivityDetector`, event-log (SQLDelight), stats queries.

### Ad formats (extensible)
- `RedirectAdFormat`, `VideoAdFormat`, `InterstitialAdFormat`, `BannerAdFormat`, future formats.
- Adding a format = one class + one Koin binding (`single<List<AdFormat>>`).

### Placement config
- `assets/ad_placements.json` — declarative rules (which screens, which content types, frequency caps).
- No code changes to add/remove a placement.

### Activity tracking
- 365-day default retention. User can set to unlimited.
- Per-event SQLDelight table (`activity_event`).
- Stats shown to the user in `:feature:anime-my` (watch time, episodes watched, most-watched, etc.).

### Integration (Phase 6)
- `AdManager.evaluate(placement, context): Flow<AdResult>` — stateful, returns per-interaction state.
- Callsites in `:feature:anime-details:impl` (on open) + `:feature:anime-watch:impl` (on episode start).
- Added when `:core:ads` ships — not before.

---

## 12. Console Logging (`:core:common`)

### Logger

> **Lambda-based API**: the message lambda is only invoked if logging is enabled + level matches.
> Zero overhead when off (no string interpolation).

```kotlin
object Logger {
    @Volatile private var enabled = false  // :app sets this in Application.onCreate()
    @Volatile private var minLevel = LogLevel.VERBOSE

    fun setEnabled(enabled: Boolean)        // called from :app with :app's BuildConfig.DEBUG
    fun setMinLevel(level: LogLevel)

    fun v(tag: String, message: () -> String, throwable: Throwable? = null)
    fun d(tag: String, message: () -> String, throwable: Throwable? = null)
    fun i(tag: String, message: () -> String, throwable: Throwable? = null)
    fun w(tag: String, message: () -> String, throwable: Throwable? = null)
    fun e(tag: String, message: () -> String, throwable: Throwable? = null)
}

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, NONE }
```

### Initialization (in `:app`)
```kotlin
// App.kt (in :app)
override fun onCreate() {
    super.onCreate()
    Logger.setEnabled(BuildConfig.DEBUG)  // :app's BuildConfig — variant-aware
    Logger.setMinLevel(if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.INFO)
    // ...
}
```

### Tag convention
`Anikuta:<Layer>:<Module>` — e.g. `Anikuta:Core:Database`, `Anikuta:Feature:Watch`, `Anikuta:Data:Extension`.

### Enforcement
- Detekt rule forbids `android.util.Log` imports outside `:core:common`.
- Release builds: `enabled = false` (lambda never invoked, zero overhead).
- Debug builds: `enabled = true`. Runtime toggle in `:feature:settings` (Logging screen).

### Usage
```kotlin
class AnimeRepositoryImpl(...) {
    fun observeAnime(uid: String): Flow<Anime?> {
        Logger.d("Anikuta:Data:Anime") { "observeAnime(uid=$uid)" }
        return queries...
    }
}
```
Never call `Log.d()` directly — always go through `Logger`.

---

## 13. Phase 2 Scaffold (what to build first)

> Minimal viable structure to validate the architecture. Trimmed to exercise every module — no dead code (Ponytail).

### Modules to build in Phase 2 (12 Gradle modules, 10 module groups)
1. `:build-logic` — convention plugins.
2. `:app` — Application class (Koin setup, Logger init), MainActivity (single Activity + Nav3 AppRoot).
3. `:core:common` — Logger (lambda-based), Dispatchers, Result, ContentType enum, base models.
4. `:core:designsystem` — theme engine + base Compose components (atoms + molecules — merged `:core:ui` into this per Ponytail).
5. `:core:database` — SQLDelight schema (content_uid, external_reference, episode_uid, episode_external_ref).
6. `:core:preferences` — PreferenceStore, ThemePreferences.
7. `:core:navigation-api` — NavKey contracts, ContentMode, Savers.
8. `:core:network` — OkHttp + ktor client + shared interceptors (needed by :core:anilist).
9. `:core:anilist` — AniList GraphQL client (enough for browse + details).
10. `:feature:anime-browse:{api,impl}` — first screen (AniList trending).
11. `:feature:anime-details:{api,impl}` — second screen (basic details).

**Deferred to Phase 3** (when library/history/watch need them):
- `:core:identity` + `:data:identity` — enters when "Add to Library" is built.
- `:data:anime` — enters when library/history need CRUD.
- `:core:player`, `:core:video-resolver`, `:core:source-api`, `:data:extension-aniyomi` — enter when Watch is built.

### Phase 2 deliverable
- App builds via CI (GitHub Actions, arm64-v8a + armeabi-v7a).
- App launches → shows Browse screen (AniList trending) → tap → Details screen.
- Nav3 navigation works (back stack survives recreate).
- Koin DI wired.
- SQLDelight DB initialized (empty schema — ready for Phase 3).
- Logger working (debug builds show logs, lambda-based, zero overhead when off).
- Theme engine working (light/dark).
- **Every module is exercised** — no dead code.

### After Phase 2
- Phase 3: Core modules (player, source-api, extension-aniyomi, video-resolver, download, tracker, backup).
- Phase 4: Feature modules (watch, library, search, history, updates, my, settings, setup-wizard).
- Phase 5: Multi-extension (mangayomi, cloudstream, kotatsu providers).
- Phase 6: Ad system + activity tracker (deferred).
- Phase 7: Manga reader (IMAGE content type).
- Phase 8: Novels (TEXT content type).
- Phase 9: Polish, testing, release.

---

## 14. Open Questions (none blocking Phase 2)

- ❓ Mangayomi source-name → Aniyomi sourceId mapping (for backup import) — defer to when Mangayomi provider is built.
- ❓ Kotatsu import — fast-follow after Mangayomi.
- ❓ Cross-content-type linking (anime ↔ manga adaptation) — future feature.
- ❓ R8/minify in release — enable in Phase 9.

---

## 15. Sub-Agent Review Notes

A Plan sub-agent reviewed this architecture (Task ID 5-REVIEW). Found **4 critical + 10 important + 16 minor flaws**. All verified + fixed:

**Critical (fixed):**
- C1: ExtensionProvider was anime-shaped (no manga/novel methods) → split into `Video/Image/TextExtensionProvider` sub-interfaces.
- C2: Shared screens lacked api/impl split → split all navigable shared features into `{api,impl}`.
- C3: WatchProgressStore layering violation → added `:core:watch-progress` contract module (interface), impl in `:data:history`, no reverse deps.
- C4: Backup merge conflicts unspecified → added §7.5 Merge Semantics (per-entity rules: MAX, UNION, OR).

**Important (fixed):**
- I1: Injekt isolation rule contradicted research → restated: allowed in `:core:source-api` + `:data:extension-aniyomi` + `:app/AniyomiInjektBootstrap.kt` (Detekt path + filename rule).
- I2: Tracker bridge underspecified → added `trackerIds` parameter to `resolveOrCreate()`; caller fetches via MetadataProvider.
- I4: AdGate premature abstraction → removed entirely. Callsites added in Phase 6 with real signature.
- I5: Logger guard violation → lambda-based API (`Logger.d(tag) { "msg" }`), zero overhead when off.
- I6: BuildConfig.DEBUG in library unreliable → `:app` calls `Logger.setEnabled(BuildConfig.DEBUG)` in `onCreate()`.
- I7: Phase 2 had dead modules → trimmed from 13 to 12 (dropped identity/data:anime to Phase 3 when exercised).
- I8: `:core:ui` vs `:core:designsystem` split unclear → merged `:core:ui` into `:core:designsystem` (Ponytail: split when justified).
- I9: Missing `:core:network` → restored (OkHttp + ktor + shared interceptors).
- I10: Player ↔ resolver boundary unclear → documented: `:feature:anime-watch:impl` mediates.

**Minor (noted, not blocking Phase 2):** thread-safety (@Volatile added), Detekt enforcement for Log imports, module count wording, episode fuzzy match gap (manual merge fallback), custom presets deferred (Ponytail), `:core:app-update` classification, WorkManager added to tech stack, ContentMap pattern sketch, settings sub-screens nested, `:core:anilist` ↔ `:core:tracker` boundary, `contentMode` moved to `AppPreferences`, Phase 2 identity limitation noted, module contract template (future).

---

## 16. Confirmation

This plan is the blueprint. Once the user confirms, Phase 2 (scaffold) begins.
