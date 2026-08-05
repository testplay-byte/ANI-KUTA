# Extension Details Page — Architecture Plan

> **Status:** PLANNING (not yet implemented)
> **Date:** Phase 5c, session web-f53f0459

## Problem

Details page only works for AniList anime. `AnimeDetailsKey` carries only
`animeId: Int`. Extension search results can't open details.

## Proposed Architecture

### 1. UnifiedAnime (`:core:common`)
Source-agnostic model bridging AniList + extension. All fields except `title` nullable.

### 2. Sealed AnimeDetailsKey
- `AniList(animeId: Int)` — existing
- `Extension(sourceId, animeUrl, title, thumbnailUrl)` — new

### 3. DetailsViewModel — Dual Entry
- `loadFromAniList(id)` — existing
- `loadFromExtension(sourceId, url, title, thumb)` — new (enrich via `getAnimeDetails`)

### 4. Auto-Link (per-extension setting)
- ON: search AniList by title → auto-link if match → merge data
- NO MATCH: manual link sheet (search + pick or skip)
- OFF: show extension data as-is

### 5. contentId (future)
- AniList: `"al:154587"` / Extension: `"aniyomi:$sourceId:$url"`

## Phases
- **A:** MVP (UnifiedAnime, sealed key, wire callback, ext details, skip metadata)
- **B:** Auto-link (per-ext setting, AniList search, manual link sheet, merge)
- **C:** contentId (migrate identity, watch progress, library)
- **D:** Multi-source (MAL, TMDB, Kitsu providers)

## 15 Questions for User

1. Auto-link default ON or OFF?
2. Match: exact only, fuzzy (0.80), or always manual?
3. Enrich: `getAnimeDetails()` first or background?
4. Watch progress: contentId now or later?
5. Manual link: show "skip" option?
6. Per-ext settings UI location?
7. Reuse `ContentMetadata` or new `UnifiedAnime`?
8. Cache auto-link results (ExtensionLinkStore)?
9. Port `AnimeDetailsProvider` pattern?
10. Handle `getAnimeDetails()` binary incompat crashes?
11. Add `contentId` to `WatchKey` now?
12. Split Phase A into A1 (nav) + A2 (refactor)?
13. Auto-link settings: ext settings page or details menu?
14. Loading indicator during enrichment?
15. Manual link: bottom sheet or full screen?
