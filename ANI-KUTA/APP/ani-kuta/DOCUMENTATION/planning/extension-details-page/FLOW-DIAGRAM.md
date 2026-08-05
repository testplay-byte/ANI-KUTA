# Extension Details Page — Visual Flow Diagram

```
USER TAPS ANIME
    │
    ├── AniList entry ──→ AnimeDetailsKey.AniList(id)
    │                        │
    │                        ▼
    │                   loadFromAniList(id)
    │                        │
    │                        ▼
    │                   UnifiedAnime
    │                   (anilistId set, sourceId null)
    │                        │
    │                        ▼
    │                   Details Screen ◄── (existing flow)
    │
    └── Extension entry ─→ AnimeDetailsKey.Extension(sourceId, url, title, thumb)
                             │
                             ▼
                        loadFromExtension(sourceId, url, title, thumb)
                             │
                             ▼
                        source.getAnimeDetails(sAnime)  ← enrich sparse data
                             │
                             ▼
                        UnifiedAnime
                        (anilistId null, sourceId set)
                             │
                             ▼
                        fetchEpisodesFromSource()  ◄── episodes load in parallel
                             │
                             ▼
                        performAutoLink()  ◄── Phase B
                             │
                    ┌────────┴────────┐
                    │                 │
              Auto-link ON       Auto-link OFF
              (per-source)       (per-source override = "off"
                    │             OR global toggle = false)
                    │                 │
                    ▼                 ▼
              Cache check      AutoLinkState.Skipped
                    │                 │
              ┌─────┴─────┐           ▼
            HIT        MISS      Show extension data
              │           │      as-is (no AniList metadata)
              │           ▼
              │      Search AniList by title
              │      (anilistApi.searchAnime)
              │           │
              │           ▼
              │      SmartMatcher.findBestMatch()
              │      (normalize → Levenshtein → year/contains bonus)
              │           │
              │      ┌────┴────┐
              │    MATCH     NO MATCH
              │      │         │
              │      ▼         ▼
              │  Cache the   showManualLinkSheet = true
              │  anilistId   ┌────────────────────────────┐
              │      │       │  ManualLinkSheet (Phase B)  │
              │      │       │  • Search field (pre-filled)│
              └──────┤       │  • Results: cover+title+    │
                     │       │    score+year → "Link" btn   │
                     ▼       │  • "Skip AniList link" btn   │
              mergeAniList   └─────────┬────────────────────┘
              IntoUnified()            │
                     │           ┌─────┴─────┐
                     │         LINK        SKIP
                     │           │           │
                     │           ▼           ▼
                     │      cacheManualLink  AutoLinkState.Skipped
                     │      mergeAniList     (proceed without linking)
                     │      IntoUnified()
                     │           │
                     └─────┬─────┘
                           ▼
                   UnifiedAnime
                   (anilistId set, sourceId set)
                   — AniList metadata merged:
                     synopsis, score, episodes,
                     season, genres, bannerUrl, idMal
                           │
                           ▼
                   Episode metadata fetch
                   (Anikage/Jikan/AniList streaming)
                   — runs now that anilistId is set
                           │
                           ▼
                   Details Screen
                   (unified rendering)
                           │
                   ┌───────┼───────────┐
                   ▼       ▼           ▼
               Banner   Synopsis   Episodes
               (+ "Linked     (merged    (from source)
                to AniList"    from AL)    │
                badge)                ┌────┴────┐
                                  anilistId  anilistId
                                  != null    == null
                                     │          │
                                     ▼          ▼
                               Fetch ep    Skip metadata
                               metadata    (ext data only)
```

## Three-dot menu (extension entries only)
```
  ┌─────────────────────┐
  │ Refresh             │
  │ Share               │
  │─────────────────────│  ← divider (only for extension entries)
  │ Link to AniList     │  ← if NOT linked (opens ManualLinkSheet)
  │   OR                │
  │ Unlink AniList      │  ← if linked (clears cache + removes AL fields)
  └─────────────────────┘
```

## Phases
```
A (MVP)              B (Auto-Link)         C (contentId)        D (Multi-Source)
────────             ─────────────         ────────────         ────────────────
UnifiedAnime         Per-ext setting       contentId            MAL provider
Sealed NavKey        AL search + match     Source linking       TMDB provider
Wire callback        Manual link sheet     Watch progress       Kitsu provider
loadFromExtension()  SmartMatcher          Library              Priority order
Skip metadata        AutoLinkService       Cross-source ID      Per-ext config
                     AutoLinkPreferences
                     Episode metadata
                     (post-link fetch)

✅ DONE              ✅ DONE              ⏳ PLANNED           ⏳ PLANNED
```

## Settings UI (Phase B)
```
Settings Screen
  ├── Appearance
  ├── Extensions
  └── Metadata
       └── Auto-Link  ──→  AutoLinkSettingsScreen
                            │
                            ├── Global
                            │   ├── Auto-link toggle (ON/OFF)
                            │   ├── Match strategy (Fuzzy/Strict/Manual)
                            │   └── Fuzzy threshold slider (0.50–1.00)
                            │
                            └── Per-extension overrides
                                ├── Extension A [Default / Always / Never]
                                ├── Extension B [Default / Always / Never]
                                └── Extension C [Default / Always / Never]
```
