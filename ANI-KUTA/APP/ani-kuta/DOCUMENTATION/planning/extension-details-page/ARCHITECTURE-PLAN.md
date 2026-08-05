# Extension Details Page — Architecture Plan

> **Status:** Phase A + B IMPLEMENTED. Phase C/D pending.
> **Date:** Phase 5d, session web-f53f0459

## Problem

Details page only works for AniList anime. `AnimeDetailsKey` carries only
`animeId: Int`. Extension search results can't open details.

## Architecture (Implemented)

### 1. UnifiedAnime (`:core:common`) — Phase A ✅
Source-agnostic model bridging AniList + extension. All fields except `title` nullable.
Has `anilistId`, `sourceId`, `animeUrl`, `entryMode` (ANILIST/EXTENSION).

### 2. Sealed AnimeDetailsKey — Phase A ✅
- `AniList(animeId: Int)` — existing
- `Extension(sourceId, animeUrl, title, thumbnailUrl)` — new

### 3. DetailsViewModel — Dual Entry — Phase A ✅
- `loadFromAniList(id)` — existing
- `loadFromExtension(sourceId, url, title, thumb)` — Phase A

### 4. Auto-Link — Phase B ✅
**Modules:**
- `:core:smart-matcher` — SmartMatcher + AutoLinkService + TitleNormalizer + LevenshteinDistance
- `AutoLinkPreferences` in `:core:preferences` — global toggle + strategy + threshold + per-source overrides + link cache

**Flow:**
1. `loadFromExtension()` → fetches extension details → kicks off `performAutoLink()`.
2. `performAutoLink()` → checks per-source setting → cache check → AniList search → SmartMatcher.
3. On `Matched`/`Cached` → `mergeAniListIntoUnified()` via `AniListDetailsProvider.mergeInto()`.
4. On `NoMatch` → `showManualLinkSheet = true` → UI shows `ManualLinkSheet`.
5. On `Skipped` (disabled or MANUAL strategy) → stays on extension data only.
6. User can manually link/unlink from the three-dot menu.

**SmartMatcher algorithm:**
- Normalize titles (lowercase, strip punctuation, remove season/year suffixes).
- Levenshtein similarity ratio (0.0–1.0).
- Contains bonus (+0.05): one title's core tokens inside the other.
- Year bonus (+0.10): if both have a matching year.
- Cap at 1.0. If score ≥ threshold (default 0.80) → Match.

**Settings (AutoLinkSettingsScreen):**
- Global: master toggle + strategy (Fuzzy/Strict/Manual) + threshold slider (0.50–1.00).
- Per-extension: 3-way override (Default/Always link/Never link) per installed extension.
- Accessed from SettingsScreen hub → "Metadata" → "Auto-Link".

**Manual link sheet:**
- Bottom sheet with search field (pre-filled with extension title).
- Auto-searches on open.
- Results list: cover + title + score + year + Link button.
- "Skip AniList link" button at the bottom.

### 5. contentId (future) — Phase C
- AniList: `"al:154587"` / Extension: `"aniyomi:$sourceId:$url"`

## Phases
- **A:** MVP (UnifiedAnime, sealed key, wire callback, ext details, skip metadata) — ✅ DONE
- **B:** Auto-link (SmartMatcher, per-ext setting, AniList search, manual link sheet, merge) — ✅ DONE
- **C:** contentId (migrate identity, watch progress, library)
- **D:** Multi-source (MAL, TMDB, Kitsu providers)

## Files (Phase B)

### New module: `:core:smart-matcher`
- `core/smart-matcher/build.gradle.kts`
- `TitleNormalizer.kt` — title normalization (lowercase, strip punctuation, remove suffixes)
- `LevenshteinDistance.kt` — character-level edit distance + similarity ratio
- `MatchResult.kt` — sealed: Match/NoMatch/Skipped/Error
- `SmartMatcherConfig.kt` — threshold + strategy (FUZZY/STRICT/MANUAL) + bonuses
- `SmartMatcher.kt` — main matching logic
- `AutoLinkResult.kt` — sealed: Cached/Matched/NoMatch/Skipped/Error
- `AutoLinkService.kt` — orchestrator (cache → search → match → cache result)
- `SmartMatcherModule.kt` — Koin DI

### New files in existing modules
- `core/preferences/.../AutoLinkPreferences.kt` — global + per-source settings + link cache
- `feature/anime-details/impl/.../ManualLinkSheet.kt` — bottom sheet for manual AniList linking
- `feature/extensions-settings/impl/.../AutoLinkSettingsScreen.kt` — settings UI
- `feature/extensions-settings/api/.../ExtensionsSettingsKey.kt` — added `AutoLinkSettingsKey`

### Modified files
- `settings.gradle.kts` — added `:core:smart-matcher`
- `app/build.gradle.kts` — added `:core:smart-matcher` dep
- `feature/anime-details/impl/build.gradle.kts` — added `:core:smart-matcher` dep
- `feature/extensions-settings/impl/build.gradle.kts` — added `:core:preferences` dep
- `core/anilist/.../AniListModule.kt` — registered `AniListDetailsProvider` as concrete type
- `app/.../AnikutaApp.kt` — registered `AutoLinkPreferences` + `smartMatcherModule`
- `feature/anime-details/impl/.../DetailsModule.kt` — updated ViewModel DI
- `feature/anime-details/impl/.../DetailsViewModel.kt` — major rewrite with auto-link logic
- `feature/anime-details/impl/.../DetailsScreen.kt` — ManualLinkSheet + auto-link badge + menu items + DownloadEpisodeButton fix
- `app/.../SettingsScreen.kt` — added "Auto-Link" nav row
- `app/.../MainActivity.kt` — wired AutoLinkSettingsScreen navigation

## 15 Questions for User (answered)
1. Auto-link default ON — ✅ (global toggle defaults ON)
2. Match: fuzzy (0.80 threshold) — ✅ (configurable)
3. Enrich: getAnimeDetails first, then background auto-link — ✅
4. Watch progress: contentId later (Phase C) — deferred
5. Manual link: show "skip" option — ✅
6. Per-ext settings UI: dedicated AutoLinkSettingsScreen — ✅
7. Reuse UnifiedAnime — ✅
8. Cache auto-link results — ✅ (AutoLinkPreferences.cacheAniListId)
9. Port AnimeDetailsProvider pattern — ✅ (Phase A)
10. Handle getAnimeDetails crashes — ✅ (ExtensionDetailsProvider catches Throwable)
11. contentId in WatchKey: later (Phase C) — deferred
12. Split Phase A: not needed — ✅
13. Auto-link settings location: SettingsScreen hub → "Metadata" → "Auto-Link" — ✅
14. Loading indicator during enrichment — ✅ (auto-link spinner in banner)
15. Manual link: bottom sheet — ✅
