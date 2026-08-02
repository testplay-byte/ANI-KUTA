# Data Modules Analysis

> Deep analysis of the `data/` modules in the old ANIKUTA project at
> `/home/z/my-project/ANIKUTA-PROJECT/REFERENCES/old-kuta/ANIKUTA/`.
> Source code is read directly; this document summarizes findings for the
> rebuild team.

## Summary

The old ANIKUTA project ships five `data/` modules:

- **`:data:anime`** — SQLDelight-backed repository implementations for the anime
  domain: `AnimeRepository`, `EpisodeRepository`, `CategoryRepository`, plus the
  AniList-side `AnimeDetailsProvider`. The largest, most substantive data module.
- **`:data:extension`** — The Aniyomi-compatible anime extension system
  (loader + installer + manager + repo API + link stores). The second-largest
  data module — Phase 4B-complete; mirrors the Aniyomi reference architecture.
- **`:data:history`** — Tiny SQLDelight-backed `HistoryRepository` for the
  `animehistory` table. Implemented, but **not the source of truth** for the
  History screen (`:feature:history` reads `WatchProgressStore` from
  `:core:player` instead).
- **`:data:manga`** — Empty stub. Manga deferred per ADR-009 (anime-first).
- **`:data:tracker`** — Empty stub. Tracker implementations live in `:core:tracker`.

Three of the five modules are real implementations; two are intentionally-empty
placeholders reserving module slots. The whole `data/` layer never calls into
`:feature/` or `:app` — it only implements interfaces declared in `:core/` and
is wired into Koin by `:app`'s `di/` package.

**Architectural pattern (consistent across all data modules):**

1. **Interface in `:core:common`** (e.g., `AnimeRepository`, `HistoryRepository`,
   `EpisodeRepository`, `CategoryRepository`) — ViewModels depend on the
   interface, never the implementation.
2. **Implementation in `:data/<x>`** — constructor-injected `AnikutaDatabase`
   (SQLDelight) + `DispatcherProvider` only. No business logic outside persistence.
3. **Mapper object** — pure function from SQLDelight row type → domain model
   (`AnimeMapper`, `EpisodeMapper`, `CategoryMapper`, `HistoryMapper`).
4. **Koin binding in `:app`'s `RepositoryModule`** — `single<AnimeRepository> { AnimeRepositoryImpl(get(), get()) }`.

The `:data:extension` module breaks this pattern slightly — it's larger and
also implements the source-matching / extension-loading / extension-installing
subsystems, not just SQLDelight persistence. It also implements
`AnimeDetailsProvider`, `EpisodeFetchGateway`, and `SourceLinkBackupAccess`
(ports declared in `:core/`).

## Module Index

| Module | Purpose | Status |
|--------|---------|--------|
| `:data:anime` | Anime / Episode / Category repository impls + AniList `AnimeDetailsProvider` | ✅ Active (largest data module) |
| `:data:extension` | Aniyomi-compatible extension loader / installer / manager / repo + link stores + extension `AnimeDetailsProvider` | ✅ Active (Phase 4B complete) |
| `:data:history` | `HistoryRepository` impl (SQLDelight `animehistory`) | ✅ Active but unused by UI |
| `:data:manga` | Manga repository impls + manga DB schema | ⚠️ Empty stub (ADR-009 deferral) |
| `:data:tracker` | Tracker impls (MAL / AniList / Shikimori / Bangumi / Simkl) | ⚠️ Empty stub (logic moved to `:core:tracker`) |

---

## `:data:anime`

**Purpose:** SQLDelight-backed implementations of the anime-domain repositories
(`AnimeRepository`, `EpisodeRepository`, `CategoryRepository`) plus the
AniList-side `AnimeDetailsProvider` (one of two providers — the other is in
`:data:extension`). Owns no UI, no networking, no preferences beyond the source
preference file.

**Dependencies (from `build.gradle.kts`):**
- `:core:common` — repository interfaces + domain models + `DispatcherProvider`.
- `:core:database` — `AnikutaDatabase` + SQLDelight queries.
- `:core:anilist` — `AniListApi` (used by `AniListDetailsProvider`).
- `:core:episode-metadata` — `EpisodeMetadata` model (transitive).
- `:core:source-api` — `SAnime`, `SEpisode`, `AnimeCatalogueSource` (used by the
  provider for the stage-3 episode fetch).
- `:data:extension` — `SourceMatcher`, `SourceLinkStore`, `ExtensionLinkStore`,
  `AnimeExtensionManager` (used by the provider for stage-2 source matching).
- `libs.sqldelight.coroutines` — Flow adapters (`asFlow().mapToList(io)`).
- `kotlinx.coroutines.core`.
- Test: `libs.bundles.test` + `kotlinx.coroutines.test`.

**Status:** ✅ Active — substantial implementation. The package contains 6 main
source files + 1 test file. Implements a 3-stage AniList→extension pipeline.

**Key files:**

- `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeRepositoryImpl.kt`
  — The `AnimeRepository` impl. ~460 LOC. Backed by SQLDelight `animes` table.
  Reactive (`observeAll`, `observeFavorites`, `observeById`, `observeBySource`,
  `observeByAnilistId`, `observeBySourceAndUrl`) + suspending reads/writes.
  Two-tier identity (ADR-050): `local_id` + `content_id` columns with
  `backfillIdentityColumns(priority)` for migration. Handles the
  "INSERT conflicts UNIQUE(source_id, url)" edge case by checking first.
- `data/anime/src/main/java/app/confused/anikuta/data/anime/AnimeMapper.kt`
  — Pure mapper object. Maps ~40 SQLDelight columns → `Anime` domain model.
  Splits the `genre` comma-string → `List<String>`. Builds `SourceProvenance`
  from the flat provenance columns.
- `data/anime/src/main/java/app/confused/anikuta/data/anime/EpisodeRepositoryImpl.kt`
  — The `EpisodeRepository` impl. ~96 LOC. Reactive + suspending reads/writes.
  `upsert`, `updateSeen`, `updateBookmark`, `delete`, `deleteByAnimeId`.
- `data/anime/src/main/java/app/confused/anikuta/data/anime/EpisodeMapper.kt`
  — Mapper for the 20-column `episodes` table → `Episode` (incl. ADR-024
  status-tracking columns `releaseDate`, `lastRefresh`, `lastMetadataFetch`,
  `nextEpisodeCheck`).
- `data/anime/src/main/java/app/confused/anikuta/data/anime/CategoryRepositoryImpl.kt`
  — The `CategoryRepository` impl. ~198 LOC. Handles the `categories` table +
  the `anime_category` junction. The Default category (id=1) is protected —
  rename / delete / reorder / hide all refuse for it. Reordering is serialized
  by a `Mutex` (rapid drag-and-drop safety). `ensureDefaultExists()` is a safety
  net called on app startup.
- `data/anime/src/main/java/app/confused/anikuta/data/anime/CategoryMapper.kt`
  — Mapper for the 5-column `categories` table → `Category`.
- `data/anime/src/main/java/app/confused/anikuta/data/anime/details/AniListDetailsProvider.kt`
  — `AnimeDetailsProvider` for `DataSource.ANILIST`. ~337 LOC. Implements the
  three-stage load:
    1. AniList fetch (`AniListApi.fetchById`).
    2. Source match (`SourceMatcher.matchAll(title)` or saved `SourceLinkStore`
       link keyed by `content_id`).
    3. Episode fetch (`source.getEpisodeList(sAnime)`) → persist to DB for
       offline re-open.
  DB-first short-circuit: if episodes already in DB, return instantly. Wrapped
  in try-catch (Throwable — extension bytecode can throw `Error` subclasses).
- `data/anime/src/test/java/app/confused/anikuta/data/anime/AnimeMapperTest.kt`
  — JUnit 5 tests for `AnimeMapper` — covers full-mapping + edge cases
  (nullable fields, genre-string parsing, provenance null).

**Key classes/interfaces:**
- `AnimeRepositoryImpl` — implements `AnimeRepository` (`:core:common`).
- `EpisodeRepositoryImpl` — implements `EpisodeRepository` (`:core:common`).
- `CategoryRepositoryImpl` — implements `CategoryRepository` (`:core:common`),
  owns the `categories` + `anime_category` junction.
- `AniListDetailsProvider` — implements `AnimeDetailsProvider` (`:core:common`),
  registered in the `AnimeDetailsProviderRegistry` (Koin multi-binding).
- `AnimeMapper`, `EpisodeMapper`, `CategoryMapper` — pure mapper objects.

**Notes:**
- The provider's per-anime source-preference file is a plain `SharedPreferences`
  (`anikuta_source_prefs`) — NOT migrated to `PreferenceStore` yet.
- `AnimeRepositoryImpl.upsert` does an `INSERT`-with-check-then-`UPDATE` to avoid
  the UNIQUE(source_id, url) constraint violation that the old code hit when an
  extension-anime row already existed and the user re-saved it.
- All implementations use `android.util.Log` with a `TAG` constant for filterable
  logcat output (ADR-033).
- Wiring: registered in `:app`'s `app/.../di/RepositoryModule.kt` (Koin
  `single<AnimeRepository> { AnimeRepositoryImpl(get(), get()) }` etc.).

---

## `:data:extension`

**Purpose:** The Aniyomi-compatible anime extension system — loads, manages,
and installs **external APK extensions** that implement the `:core:source-api`
contract (ADR-029). This is the runtime half of the source system (the contract
half lives in `:core:source-api`). Includes extension loading (DEX classloader),
repo-index fetching, APK download/install via PackageInstaller, signature trust
management, source matching, and two link stores (AniList↔extension).

**Dependencies (from `build.gradle.kts`):**
- `:core:common` — interfaces + domain models.
- `:core:source-api` — the Aniyomi-compatible contract (`AnimeSource`, `SAnime`,
  `SEpisode`, `AnimeCatalogueSource`).
- `:core:preferences` — `PreferenceStore` for `ExtensionLinkStore` /
  `SourceLinkStore` / `DetailsViewPreferenceStore`.
- `:core:update-checker` — `EpisodeFetchGateway` port (implemented here by
  `EpisodeFetchGatewayImpl`; the `:core→:data` inversion via interface).
- `:core:anilist` — `AniListApi` (used by `ExtensionDetailsProvider` for the
  AniList merge on linked extension anime).
- `:core:episode-metadata` — `EpisodeMetadata` model.
- `:core:designsystem` — `PaletteExtraction.extractFromBitmap` for cover-color
  extraction on extension-sourced covers.
- `:core:backup` — `SourceLinkBackupAccess` port (implemented by
  `SourceLinkBackupAccessImpl`).
- `androidx.core:core-ktx:1.15.0` — `NotificationCompat` + `ContextCompat`.
- `com.squareup.okhttp3:okhttp:5.0.0-alpha.14` — repo index + APK download.
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` — parse `index.json`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1`.
- `io.reactivex:rxjava:1.3.8` — source-api compat (deprecated `fetch*` API).

**Status:** ✅ Active — Phase 4B complete. The largest `data/` module by file
count (~22 Kotlin source files). Architecturally faithful to the Aniyomi
reference.

**Key files:**

*Public façade + manager:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/AnimeExtensionManager.kt`
  — Public façade. ~252 LOC. Owns 3 `StateFlow`s:
  `installedExtensionsFlow`, `availableExtensionsFlow`, `untrustedExtensionsFlow`.
  On `init` does `loader.loadExtensions(context)` + registers an
  `ExtensionInstallReceiver` listener. Public ops: `findAvailableExtensions`,
  `installExtension` (returns `Flow<InstallStep>`), `updateExtension`,
  `uninstallExtension`, `trust`, `untrust`. Registered in Koin as a singleton
  (ADR-023).
- `data/extension/src/main/java/app/confused/anikuta/data/extension/api/AnimeExtensionApi.kt`
  — The "fetch available extensions" orchestrator. ~99 LOC. Calls
  `ExtensionRepoApi.fetchExtensions(repo)` for every configured repo
  concurrently, merges by `pkgName` (first repo wins), and recomputes
  `hasUpdate` / `isObsolete` on installed extensions.

*Loader:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/loader/AnimeExtensionLoader.kt`
  — ~280 LOC. Scans `PackageManager` for the `tachiyomi.animeextension` feature
  flag, validates `libVersion` ∈ [12, 16], SHA-256 hashes the signing cert,
  builds a `ChildFirstPathClassLoader`, instantiates source classes (or calls
  `createSources()` on an `AnimeSourceFactory`), returns `AnimeLoadResult`.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/loader/ChildFirstPathClassLoader.kt`
  — Parent-last `PathClassLoader`. Extension's bundled deps win over the app's
  classpath (binary-compat only at the `:core:source-api` boundary). Falls back
  to plain `PathClassLoader` on `LinkageError`.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/loader/HashUtil.kt`
  — SHA-256 hex helper.

*Installer:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/installer/AnimeExtensionInstaller.kt`
  — ~160 LOC. Downloads the APK via OkHttp (not Android's `DownloadManager`).
  One install at a time (Mutex). Emits `Flow<InstallStep>` for UI progress.
  Starts `ExtensionInstallService` to actually run the install.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/installer/ExtensionInstallService.kt`
  — Foreground service owning one `PackageInstallerBackend`. Calls
  `startForeground` within 5s (Android 12+ requirement). Processes one request
  per `startService`, then stops itself.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/installer/PackageInstallerBackend.kt`
  — `PackageInstaller.Session` + `PendingIntent` result handling.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/installer/ExtensionInstallReceiver.kt`
  — BroadcastReceiver for `ACTION_PACKAGE_*` system broadcasts → refreshes the
  manager's registries via full re-scan.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/installer/InstallStep.kt`
  — `enum class InstallStep { Idle, Pending, Downloading, Installing, Installed, Error }`
  with `isCompleted()` helper.

*Repo management:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/repo/ExtensionRepo.kt`
  — `@Serializable data class`. Holds `baseUrl`, `name`, `shortName`, `website`,
  `iconUrl`. Derives `indexUrl`, `apkUrl(apkName)`, `iconUrlFor(pkgName)`.
  Companion `DEFAULT` = the Aniyomi repo URL.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/repo/ExtensionRepoRepository.kt`
  — ~123 LOC. SharedPreferences-backed JSON-array CRUD. Exposes
  `repos: StateFlow<List<ExtensionRepo>>`. Insert / upsert / delete / replace.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/repo/ExtensionRepoApi.kt`
  — ~257 LOC. Fetches + parses a single repo's `index.json`. Lenient JSON
  decoder. Maps to `AnimeExtension.Available` entries.

*Models:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/model/AnimeExtension.kt`
  — `sealed class AnimeExtension` with three variants:
  `Installed` (with live `List<AnimeSource>` + Drawable icon + hasUpdate + isObsolete),
  `Available` (with source *metadata* + apkName + iconUrl + repoUrl),
  `Untrusted` (with signatureHash). Mirrors the Aniyomi reference shape.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/model/AnimeLoadResult.kt`
  — `sealed interface`: `Success` / `Untrusted` / `Error` / `UnrecognizedExtension`
  (the last is ANIKUTA-specific — the reference returns `Error` for the same case).

*Trust:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/trust/TrustExtension.kt`
  — Reads/writes the `trusted_extensions` SharedPreferences set. Format:
  `"pkgName:versionCode:signatureHash"` (Aniyomi-compatible). An updated
  extension with a new versionCode must be re-trusted.

*Source matching:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt`
  — ~399 LOC. Searches trusted extension sources for an anime by title.
  `match(title)` returns the first match (priority order). `matchAll(title)`
  searches every source concurrently and returns all matches ranked by
  similarity. Title normalization: lowercase + strip parentheticals + strip
  non-alphanumerics + collapse whitespace. Similarity: exact=1.0,
  substring=0.95, else Levenshtein. Threshold: 0.80. Each source call wrapped
  in try-catch (one broken extension doesn't kill the search). All source
  calls wrapped in `withContext(Dispatchers.IO)` — CRITICAL because the source
  API delegates to RxJava `awaitSingle()` → `call.execute()` synchronously.

*Link stores (AniList↔extension):*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/SourceLinkStore.kt`
  — Persists AniList→extension source links (skips re-search on every app
  open). Keyed by `content_id` (`"al:154587"`). Stores full match info
  (sourceId + SAnime URL + SAnime title). Backed by `PreferenceStore.getObject`.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/ExtensionLinkStore.kt`
  — Reverse lookup: extension→AniList link. Key: `"$sourceId:$animeUrl"`.
  Value: `content_id` (e.g., `"al:154587"`). Used by the Search page's
  extension→AniList linking flow.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/DetailsViewPreferenceStore.kt`
  — Remembers the user's preferred data source (AniList vs Extension) per
  anime. Keys: `anilistId.toString()` for linked, `"ext:{sourceId}:{url}"` for
  unlinked.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/cache/SourceLinkBackupAccessImpl.kt`
  — Phase 8 architectural fix (Doc 04 violation 1). Implements
    `SourceLinkBackupAccess` from `:core:backup` by wrapping
    `SourceLinkStore` + `ExtensionLinkStore`. The `:core:backup` module injects
    the interface and never touches data-layer types directly.

*Migration + provider + gateway:*
- `data/extension/src/main/java/app/confused/anikuta/data/extension/migration/SourceLinkMigrator.kt`
  — ~151 LOC. Migrates `SourceLinkStore` + `ExtensionLinkStore` from legacy
  formats (key=`anilistId.toString()`) to new content_id-based formats
  (key=`"al:154587"`). Idempotent + crash-resistant.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/details/ExtensionDetailsProvider.kt`
  — `AnimeDetailsProvider` for `DataSource.EXTENSION`. ~518 LOC. The largest
  file in the module. Translates an extension `SAnime` → `UnifiedAnime`,
  fetches episode list from the extension, calls `getAnimeDetails` for
  enrichment (closes a gap from old ANIKUTA), merges AniList data if linked,
  extracts Palette cover-color via OkHttp + `PaletteExtraction`, persists
  episodes to SQLDelight.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/details/SAnimeMapper.kt`
  — Extension-only `SAnime` → `UnifiedAnime`. ~143 LOC. AniList-only fields
  (score/format/season/studios/nextAiring) are null — merged separately by the
  provider when the anime is linked.
- `data/extension/src/main/java/app/confused/anikuta/data/extension/updatechecker/EpisodeFetchGatewayImpl.kt`
  — ~90 LOC. Implements `EpisodeFetchGateway` from `:core:update-checker`.
  Bridges the `:core:update-checker` (which can't depend on `:data:extension`)
  to the real extension stack. Calls `SourceMatcher.matchAll(title)` → takes
  first match → `source.getEpisodeList(sAnime)` → maps to `EpisodeInfo`.

**Key classes/interfaces:**
- `AnimeExtensionManager` — public façade (Koin singleton).
- `AnimeExtensionApi` — orchestrator for `findAvailableExtensions` /
  `checkForUpdates`.
- `AnimeExtensionLoader` — PackageManager scan + DEX classloader instantiation.
- `AnimeExtensionInstaller` + `ExtensionInstallService` +
  `PackageInstallerBackend` + `ExtensionInstallReceiver` — the install stack.
- `ExtensionRepoRepository` + `ExtensionRepoApi` + `ExtensionRepo` — repo CRUD +
  index fetch.
- `TrustExtension` — signature-trust SharedPreferences gate.
- `SourceMatcher` — title-based source search.
- `SourceLinkStore` + `ExtensionLinkStore` + `DetailsViewPreferenceStore` — link
  + preference persistence.
- `SourceLinkBackupAccessImpl` — backup adapter (Phase 8 architectural fix).
- `SourceLinkMigrator` — legacy → content_id migration.
- `ExtensionDetailsProvider` + `AniListDetailsProvider` (in `:data:anime`) — the
  two `AnimeDetailsProvider` implementations for the unified details page.
- `EpisodeFetchGatewayImpl` — bridges `:core:update-checker` to extensions.

**Notes:**
- Manifest metadata uses `tachiyomi.animeextension.*` (anime-specific, NOT the
  generic `tachiyomi.extension` the manga side uses).
- The APK's `versionName` must be `<libversion>.<patch>` where `libversion` ∈ [12, 16].
- The APK's application label must start with `Aniyomi: ` (stripped for display).
- Default repo: `https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo`.
- **Phase 4B NOT done** (deferred to later agents): wiring into Browse,
  Anime Details episode list, Video Resolver real servers, Extensions Settings
  real data binding, private `.ext` file installs, Shizuku installer, once-a-day
  `checkForUpdates` throttle.
- Wiring: registered in `:app`'s `app/.../di/ExtensionModule.kt` (Koin).

---

## `:data:history`

**Purpose:** SQLDelight-backed implementation of `HistoryRepository` against
the `animehistory` table. Tiny module.

**Dependencies (from `build.gradle.kts`):**
- `:core:common` — `HistoryRepository` interface + `History` model + `DispatcherProvider`.
- `:core:database` — `AnikutaDatabase` + `animehistoryQueries`.
- `libs.sqldelight.coroutines`, `kotlinx.coroutines.core`.
- Test: `libs.bundles.test` + `kotlinx.coroutines.test`.

**Status:** ✅ Implemented but **unused by the UI**. The `:feature:history`
screen reads `WatchProgressStore` (`:core:player`) as the source of truth —
NOT this repository. `HistoryRepository` exists for completeness / future use
(e.g., backup/restore, server-side history sync).

**Key files:**
- `data/history/src/main/java/app/confused/anikuta/data/history/HistoryRepositoryImpl.kt`
  — ~38 LOC. Implements `observeAll`, `observeByAnimeId`, `upsert`, `delete`,
  `deleteByAnimeId`. Reactive reads via `.asFlow().mapToList(io)`.
- `data/history/src/main/java/app/confused/anikuta/data/history/HistoryMapper.kt`
  — Pure mapper. 5 columns (`id`, `animeId`, `episodeId`, `seenAt`,
  `lastSecondSeen`) → `History`.

**Key classes/interfaces:**
- `HistoryRepositoryImpl` — implements `HistoryRepository` (`:core:common`).
- `HistoryMapper` — pure mapper object.

**Notes:**
- No tests in this module (the test file is missing).
- No `di/` package — the impl is registered in `:app`'s `repositoryModule`.
- Per the `:feature:history` ViewModel comments, `WatchProgressStore` (a
  JSON-in-SharedPreferences store from `:core:player`) is the active history
  data source. The SQLDelight `animehistory` table is essentially dead code at
  runtime today. **For the rebuild, decide early whether to use SQLDelight or
  a SharedPreferences store as the history source of truth.**

---

## `:data:manga`

**Purpose:** Reserved slot for manga repository implementations + manga DB
schema. ANIKUTA is anime-first (ADR-009); manga is deferred.

**Dependencies:** None beyond the `anikuta.library` convention plugin.
`build.gradle.kts` is essentially empty (just `namespace = ...`).

**Status:** ⚠️ Empty stub. **No source files**. Not depended on by `:app`.

**Notes:**
- The architecture is "ready" for manga: the `:core:database` module supports
  the dual-schema pattern (separate `sqldelight/` + `sqldelightanime/` source
  sets). `:feature:library` already has anime/manga tab scaffolding.
- When manga is implemented, this module will hold `MangaRepositoryImpl`,
  `ChapterRepositoryImpl`, the manga DB schema, and mappers. No structural
  changes needed — just filling in the implementation.
- The manga tab is hidden in the UI (toggleable off per ADR-009).

---

## `:data:tracker`

**Purpose:** Reserved slot for tracker implementations (MAL, AniList,
Shikimori, Bangumi, Simkl).

**Dependencies:** None beyond the `anikuta.library` convention plugin.

**Status:** ⚠️ Empty stub. **No source files**. Not depended on by `:app`.

**Notes:**
- The original architecture plan (ARCHITECTURE.md §3) put tracker
  *implementations* in `:data:tracker` with interfaces in `:core`. In practice,
  the tracker system was implemented entirely in `:core:tracker` because:
    1. Tracker logic is shared across multiple features (profile, trackers
       settings, backup) → belongs in `:core`.
    2. The Aniyomi-compatible tracker interface already lives in
       `:core:source-api` territory, and the OAuth/refresh/sync logic is
       cohesive enough to live in one `:core:tracker` module.
- Only AniList + MAL are implemented; Shikimori / Bangumi / Simkl are deferred
  (ADR-019 lists them as "user picks which tracker(s)").
- **For the rebuild:** consolidate to `:core:tracker` and skip the empty
  `:data:tracker` slot.

---

## Cross-cutting observations for the rebuild

1. **Architecture rule:** every data module implements interfaces declared in
   `:core:common`. ViewModels depend on the interface, Koin binds the impl in
   `:app`. This pattern is consistently followed in `:data:anime` and
   `:data:history`; `:data:extension` extends it by also implementing provider
   ports from other `:core/` modules (`AnimeDetailsProvider`, `EpisodeFetchGateway`,
   `SourceLinkBackupAccess`).

2. **Phase 8 architectural fixes (Doc 04 violations):** the original code had
   `:core:backup` depending on `:data:extension` (core→data inversion). This
   was fixed by declaring `SourceLinkBackupAccess` in `:core:backup` and
   implementing it in `:data:extension` (`SourceLinkBackupAccessImpl`). The
   same pattern was applied to `EpisodeFetchGateway` (`:core:update-checker` →
   `:data:extension`'s `EpisodeFetchGatewayImpl`). **For the rebuild, follow
   this port-in-core / impl-in-data pattern from day one.**

3. **Two-tier identity (ADR-050):** every anime has both a `local_id`
   (extension-stable, e.g., `"aniyomi:123:url"`) and a `content_id`
   (provider-aware, e.g., `"al:154587"`). This unifies linked + unlinked
   extension anime + makes backup/restore portable across providers. The
   `AnimeRepositoryImpl.backfillIdentityColumns(priority)` migrates pre-ADR-050
   rows. **The rebuild should ship this from the start — retrofitting it is
   non-trivial (the SourceLinkMigrator exists only because of late adoption).**

4. **Source-matching threading (CRITICAL):** every call into
   `source.getSearchAnime()` / `source.getEpisodeList()` MUST be wrapped in
   `withContext(Dispatchers.IO)`. The Aniyomi source API delegates to RxJava
   `awaitSingle()` → `call.execute()` which runs synchronously on the calling
   thread. Calling from Main throws `NetworkOnMainThreadException`. All such
   calls in `SourceMatcher`, `AniListDetailsProvider`,
   `ExtensionDetailsProvider`, and `EpisodeFetchGatewayImpl` are correctly
   wrapped.

5. **Stale READMEs:** `data/anime/README.md` says "Skeleton (Phase 1)" but the
   module is fully implemented. Same for `data/history/README.md` ("Skeleton
   Phase 1" vs. "✅ Implemented" later in the same file). Treat README status
   lines as unreliable; the actual code is the source of truth.

6. **Test coverage:** only `:data:anime` has a test file (`AnimeMapperTest.kt`).
   `:data:extension`, `:data:history` have no unit tests. The rebuild should
   add tests for the link stores, the migrator, and the source matcher (the
   logic-heavy parts).
