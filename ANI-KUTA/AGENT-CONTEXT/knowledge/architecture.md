# Architecture — Concept & Design

> The **design** behind the architecture. The **rules** live in `CORE_RULES.md` §7.
> Diagrams, layer descriptions, and the module graph.
> For the full module list with deps, see `module-map.md`.

---

## Core Principle: UI ↔ Backend Separation

The app is split into two independent layers per screen/feature. The UI can be customized without touching data logic, and data logic can be reworked without breaking the UI.

```
┌─────────────────────────────────────────┐
│  FRONTEND (UI Layer) — Jetpack Compose   │
│  - Renders data                          │
│  - Handles user input                    │
│  - Customizable: themes, layouts, toggles│
│  - Talks to backend ONLY via contracts   │
└──────────────────┬──────────────────────┘
                   │  contracts (interfaces / repositories / ViewModels)
┌──────────────────▼──────────────────────┐
│  BACKEND (Data Layer)                    │
│  - Fetches data (AniList GraphQL / ext)  │
│  - Processes / transforms / caches       │
│  - Persists to SQLDelight (28 tables)    │
│  - Exposes Flow<T> / StateFlow<UiState>  │
└─────────────────────────────────────────┘
```

### Two patterns for getting data into a screen
1. **UI calls for data** — the screen's ViewModel calls a repository to fetch what it needs (most screens).
2. **UI is provided data** — a parent pre-loads and passes it down (e.g. WatchKey carries pre-resolved video data — though this is a known god-object concern, see Deferred Concerns).

Both are valid. The contract (interface) is what matters.

### Player Screen Carve-Out (ADR-025)
The watch page is **exempt** from strict UI/backend separation due to the single-MPV-instance constraint. The screen composable owns the MPV view cache + event bridge + lifecycle effects; `PlayerStateHolder` (plain class, NOT ViewModel) holds observable state; `PlayerObserver` decouples MPV events via a Callback interface. This is the old project's proven pattern (D-044). The watch page's *surrounding* UI (top nav, episode list, episode details) is still split into separate composables.

---

## Actual Module Graph (46 modules)

```
                         ┌──────────┐
                         │   :app   │  (shell: Application, MainActivity, Profile/Settings VMs)
                         └────┬─────┘
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
   ┌─────────────────┐  ┌─────────────┐  ┌──────────────┐
   │ :feature:*:impl │  │ :data:ext   │  │  :core:*     │
   │  (10 features)  │  │ (loader/mgr)│  │  (26 mods)   │
   └────────┬────────┘  └──────┬──────┘  └──────┬───────┘
            │                  │                │
            ▼                  ▼                ▼
   ┌─────────────────┐  ┌─────────────┐  ┌──────────────┐
   │ :feature:*:api  │  │ :core:      │  │ :core:common │
   │ (NavKey contracts)│ │  source-api │  │ (Logger etc) │
   └─────────────────┘  │ (Injekt iso)│  └──────────────┘
                        └─────────────┘
```

### Dependency rules
- **Feature modules never depend on each other.** Communicate via `:core` contracts or navigation (`NavKey`).
- **Feature modules use api/impl split:** `:feature:X:api` (NavKey + contracts, depended on by `:app` + sibling impls) + `:feature:X:impl` (Screen + ViewModel, depended on by `:app` only).
- **Core modules** may depend on other core modules, but **no cycles**.
- `:core:source-api` uses **Injekt** (isolated to Aniyomi ext binary-compat); everything else uses **Koin**.
- `:feature:debug-bubble` is `debugImplementation` only — release builds contain **zero** debug-bubble code (D-163).

### Layer responsibilities
| Layer | Modules | Responsibility |
|-------|---------|---------------|
| **App** | `:app` | Application class (Koin 22 modules + Injekt + crash handler), MainActivity (hand-rolled nav), ViewModels for non-modularized screens |
| **Feature** | 10 features (18 Gradle modules with api/impl) | One per user-facing screen. UI + ViewModel. Calls core repositories. |
| **Data** | `:data:extension` | Extension loader + manager + trust + installer (Aniyomi binary-compat) |
| **Core** | 26 modules | Contracts, repositories, SQLDelight, network, player, resolver, download engine, metadata, trackers, content, cache, ratings, notifications, schedule, updates, debug-api |

---

## Navigation (D-150 — hand-rolled, NOT Nav3)

```
mutableStateListOf<NavKey>(AnimeBrowseKey)   ← backstack (remember { } — NOT rememberSaveable)
        │
        ▼
when (currentKey) {                          ← single dispatch in AppRoot()
    is AnimeBrowseKey    -> BrowseScreen(...)
    is AnimeDetailsKey   -> DetailsScreen(...)
    is WatchKey          -> WatchScreen(...)
    ... (24 NavKey branches total)
}
```

- **Nav3 (`androidx.navigation3`) was REMOVED** (D-150). No Nav3 dependency in any `build.gradle.kts`.
- Backstack is `remember { mutableStateListOf(...) }` — does **NOT survive process death** (R7 limitation, accepted).
- `WatchKey` is a 15-field god-object with 5 pre-serialized strings — flagged as a Deferred Concern (refactor candidate).
- `configChanges` in AndroidManifest mitigates config changes (rotation, theme) — Activity is NOT recreated.

---

## Database (SQLDelight — 28 tables / 15 .sq files)

```
┌─────────────────────────────────────────────────────────┐
│  SQLDelight 2.0.2  (AndroidSqliteDriver — system SQLite)│
│  28 tables across 15 .sq files                           │
│  PRAGMA foreign_keys = ON (D-166)                        │
│  onOpen migration (idempotent: hasColumn + IF [NOT] EX)  │
└─────────────────────────────────────────────────────────┘
```

### Table groups
| Group | .sq file | Tables | Count |
|-------|----------|--------|-------|
| App | `app.sq` | app_metadata | 1 |
| Watch | `watch.sq` | watch_progress | 1 |
| Activity | `tracking.sq` | activity_event | 1 |
| Library | `library.sq` | library_category, library_item | 2 |
| Content (identity) | `content.sq` | data_source, system, content_ext_repo, content_ext, content, anilist_detail, extension_detail, other_source_detail | 8 |
| Customization | `customization.sq` | user_customization | 1 |
| Data cache | `dataCache.sq` | anime_metadata_cache, data_cache_episode, browse_cache | 3 |
| Downloads | `downloadQueue.sq`, `downloadedEpisode.sq` | download_queue, downloaded_episode | 2 |
| Schedule | `episodeSchedule.sq` | episode_schedule | 1 |
| Updates | `episodeUpdate.sq`, `animeUpdateState.sq` | episode_update, anime_update_state | 2 |
| Genres | `genres.sq` | genre, content_genre | 2 |
| Notifications | `notifications.sq` | notification_config, notification_sent | 2 |
| Ratings | `ratings.sq` | user_rating, user_episode_rating | 2 |
| **Total** | **15 files** | | **28 tables** |

### SQLite constraints
- minSdk 24 → API 24-28 ships SQLite 3.9-3.22 → **no UPSERT** (needs 3.24+). `INSERT OR REPLACE` used instead.
- CHECK constraints can't be ALTER-TABLE'd onto existing installs (deferred — D-166).
- Debug builds rebuild schema freely (CORE_RULES §30) — no `.sqm` migration files needed yet.

---

## Customization Hooks
1. **Theme tokens** (`:core:designsystem`) — lime #B1F256 accent, 10 functional presets + CUSTOM (D-053), warm-dark surface ramp, AMOLED. Swap-able at runtime.
2. **Component variants** (`:core:designsystem`) — floating pill nav, translucent cards, collapsible headers, scroll blur overlay.
3. **Layout options** (feature modules) — grid vs list, sort, density.
4. **Behavior toggles** (`:core:preferences`) — feature flags, auto-link strategy, download prefs, notification prefs, player prefs.
5. **Subtitle settings** (`:core:player`) — 12 MPV subtitle preferences, live-apply via `setPropertyInt/Double`.

---

## Web Dashboard (companion — separate product)
A full Next.js 16 project at `DASHBOARD/webpage/`. Deployed to GitHub Pages via Actions. **14 pages**: Overview, Architecture, Modules, Database, DB Viewer, Design, Progress, Analytics, Planning, Decisions, Downloads-Plan, Phase-D, Debug-Bubble, Testing. See `knowledge/dashboard.md` for details. Design language: `DASHBOARD/webpage/DESIGN.md` (MEMORY OS — strictly followed, CORE_RULES §16).

---

## DI Wiring
```
AnikutaApp.onCreate():
  1. Thread.setDefaultUncaughtExceptionHandler(AnikutaCrashHandler)  ← FIRST (CORE_RULES §29)
  2. ExtensionAppHolder.init(this) + Injekt registration (NetworkHelper, Application, Context, Json)
  3. startKoin { 22 modules + debugKoinModules() }
  4. Logger.setAppender(DebugLogBuffer)  ← debug only (D-164)
  5. wrapDebugOkHttp + wrapDebugSqlDriver  ← debug only (D-164)
  6. WorkManager initialized via Configuration.Provider
```

---

## Known Architectural Debt (Deferred Concerns)
Tracked in `memory/progress.md` → "Deferred Concerns" section. Summary:
1. `WatchKey` god-object (15 fields, 5 serialized strings) — refactor to identifier-only.
2. Nav backstack doesn't survive process death (R7) — hybrid `rememberSaveable` fix possible.
3. `HttpDownloader.reResolver` orphaned (D-149) — built but not wired; signatures mismatched.
4. Dead download code: `DownloadVideoPickerSheet`, `setRetryingStatus` (D-151).
5. Main-thread `runBlocking` in Downloads→Watch SAF scan (ANR risk).
6. 4 god-class .kt files >2000 lines (LibraryScreen, DetailsScreen, DetailsViewModel, WatchScreen).
7. DB migrations use `onOpen` instead of `.sqm` files (acceptable for debug per §30; needs `.sqm` before production).
8. AniList tracker is a placeholder (OAuth/sync stubs — not yet implemented).
