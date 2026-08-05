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
                    ┌────────┴────────┐
                    │                 │
              Auto-link ON       Auto-link OFF
                    │                 │
                    ▼                 ▼
           Search AniList      Show extension data
           by title            as-is (no score,
                    │           no episode metadata)
              ┌─────┴─────┐
            Match       No Match
              │           │
              ▼           ▼
         Merge AL    Manual Link Sheet
         data into   (search + pick or skip)
         Unified     ─────────┬────────────
              │               │
              └───────┬───────┘
                      ▼
              Details Screen
              (unified rendering)
                      │
              ┌───────┼───────────┐
              ▼       ▼           ▼
          Banner   Synopsis   Episodes
                              (from source)
                              │
                   ┌──────────┴──────────┐
                   │                     │
             anilistId != null      anilistId == null
                   │                     │
                   ▼                     ▼
             Fetch episode         Skip metadata
             metadata              (use ext data)
             (Anikage/Jikan)
```

## Phases
```
A (MVP)              B (Auto-Link)         C (contentId)        D (Multi-Source)
────────             ─────────────         ────────────         ────────────────
UnifiedAnime         Per-ext setting       contentId            MAL provider
Sealed NavKey        AL search + match     Source linking       TMDB provider
Wire callback        Manual link sheet     Watch progress       Kitsu provider
loadFromExtension()  Merge AL data         Library              Priority order
Screen refactor      ExtensionLinkStore    Cross-source ID      Per-ext config
Skip metadata        Episode metadata
```
