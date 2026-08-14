# Old vs New Project

> Comparison between the OLD ANI-KUTA (reference) and the NEW ANI-KUTA (this project).

## Old Project
- **Location**: `REFERENCES/old-kuta/ANIKUTA/` (cloned from the `ANI_KUTA_NEW` repo via sparse checkout).
- **Size**: ~643 files, 36 active Gradle modules, 451 Kotlin files.
- **Package**: `app.confused.anikuta.*` (note: `app.confused` — the new project uses `com.confused`).
- **Status**: Works, but was not planned/documented/structured properly. Reimagined Aniyomi (anime streaming).
- **Documentation**: `REFERENCES/old-kuta/DOCUMENTATION/` — 10-file structured analysis (5326 lines): overview, architecture, tech-stack, core-modules (1658 lines), data-modules, feature-modules (995 lines), data-flow, features-breakdown, rebuild-notes, README.

## New Project (this one)
- **Location**: `APP/ani-kuta/`.
- **Size**: 46 Gradle modules, 331 Kotlin files, 26 SQLDelight tables.
- **Package**: `com.confused.anikuta.*`.
- **Status**: Clean rebuild. Modular, documented, customizable, future-proof. All major phases complete. Debug builds only.

## Comparison

| Aspect | Old Project | New Project |
|--------|-------------|-------------|
| Modules | 36 active | 46 (1 app + 26 core + 1 data + 18 feature) |
| Kotlin files | 451 | 331 (leaner — less duplication) |
| DI | Koin + Injekt (spread everywhere) | Koin 4.2.2 (primary) + Injekt (isolated to Aniyomi ext only — D-034) |
| Persistence | SQLDelight | SQLDelight 2.0.2 (same choice — D-035) |
| Navigation | Voyager 1.0.1 | Hand-rolled `mutableStateListOf<NavKey>` (D-150 — Nav3 was tried + removed) |
| Player | MPV (aniyomi-mpv-lib) | MPV (aniyomi-mpv-lib 1.18.n) — ported from old project (D-044) |
| Extensions | Aniyomi-compat | Aniyomi-compat (same — D-027) + future multi-extension design (D-031) |
| Identity | Two-tier (ContentId/LocalId) | Two-ID system (Main ID + Content ID) — simplified from graph model (D-135) |
| Tracking | Aniyomi tracker sync | Internal activity-tracker PRIMARY (D-045) + AniList tracker secondary (placeholder) |
| Crash handling | Basic | Global crash handler + ErrorActivity (CORE_RULES §29) |
| Logging | Ad-hoc `Log.d()` | Central `Logger` wrapper, filtered, toggleable (CORE_RULES §20) |
| Documentation | None | AGENT-CONTEXT/ (rules + memory + knowledge) + APP/ani-kuta/DOCUMENTATION/ + dashboard |
| CI | None | GitHub Actions (APK build + ABI verify + dashboard deploy) |
| Builds | Local | CI-only (CORE_RULES §8) |

## Migration Notes
### Carried over (proven patterns)
- MPV player architecture (single-instance, `PlayerStateHolder` + `PlayerObserver` — D-044)
- Aniyomi extension binary-compat (Injekt isolated — D-034)
- SQLDelight for persistence (D-035)
- Floating pill bottom nav + translucent cards + scroll blur (design language)
- Lime accent #B1F256 + warm-dark surface ramp (design language)

### Redesigned
- `WatchScreen` (old: 2386 LOC god-class → new: still large at 2017 but split into PlayerStateHolder/Observer/sheets)
- `AnimeDetailsVM` (old: 1013 LOC → new: `DetailsViewModel` 2159 LOC — still a refactor candidate)
- `SetupWizard` (old: 1840 LOC → not yet ported)
- Identity system (old: ContentId/LocalId → new: simplified two-ID Main+Content — D-135)
- Navigation (old: Voyager → new: hand-rolled, Nav3 tried + removed — D-150)

### Dropped (not carried)
- Ads system (deferred — D-033, designed but not built)
- Injekt spread (now isolated to ext-compat only)

### Still pending (future phases)
- Backup/restore (D-047 — deferred to Phase 5+; needs identity + all data tables first)
- Manga reader (D-030 — modular, later)
- Novels (D-030 — later)
- Ads system (D-033 — designed, deferred)
- Identity system evolution (D-032 — flexible + switchable, current two-ID is the starting point)
- AniList tracker full implementation (currently placeholder)
- Release signing (Phase 9 — debug builds only currently)
