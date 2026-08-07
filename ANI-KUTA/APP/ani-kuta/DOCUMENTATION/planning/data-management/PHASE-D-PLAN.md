# Phase D — Data Management & Caching (Plan v2)

> **Status:** PLANNING — to be implemented in the next session
> **Date:** Post-Phase C, session web-f53f0459
> **Depends on:** Phase C (content ID system ✅, library ✅)

---

## 1. Problem Statement

The app currently fetches data from the network on EVERY screen load:
- **Browse page** — fetches trending anime from AniList every time it's opened.
- **Details page** — fetches full AniList details every time an anime is opened.
- **Library page** — fetches AniList data for each library entry (partially fixed with D-141 in-memory cache, but not persisted to disk).
- **Episode metadata** — fetched from Anikage/Jikan/AniList every time.

This causes:
1. **Slow loading** — every screen waits for network.
2. **Unnecessary data usage** — same data fetched repeatedly.
3. **No offline support** — can't browse library without network.
4. Data is lost on app restart (in-memory cache only).

---

## 2. Goals

1. **Local-first data storage** — all metadata (anime info, episode info, covers) stored locally in the database. Network is only used for refresh or opening new content.
2. **Smart refresh** — multi-stage refresh on the details page (episodes list → metadata → full refresh) with vibration. Pull-to-refresh on browse page. 6-hour auto-update on the **homepage only** (not other pages).
3. **Image caching** — cover images + episode thumbnails stored locally via Coil's disk cache (500MB, configurable in future). Survives restart.
4. **Solid caching** — all cached data persists across restarts. No data is lost when the device shuts down.
5. **Performance** — library loads instantly from local storage. No network calls on tab switch. Lazy loading for smooth scrolling.
6. **Two source types** — properly handle both the AniList data source (metadata, synopsis, score) AND the extension source (episodes, playing source info).

**NOT in Phase D:** Backup/restore functionality. The plan keeps future windows open for it, but it will NOT be implemented in this phase.

---

## 3. Architecture

### 3.1 New module: `:core:data-cache`

```
core/data-cache/
├── build.gradle.kts
└── src/main/java/com/confused/anikuta/core/datacache/
    ├── AnimeMetadataCache.kt       ← stores anime metadata by mainId
    ├── EpisodeMetadataCache.kt     ← stores episode metadata by mainId + episodeNumber
    ├── BrowseDataCache.kt          ← stores browse page data (trending, etc.)
    ├── CacheRefreshManager.kt      ← manages refresh intervals (6-hour auto-update, homepage only)
    └── DataCacheModule.kt          ← Koin DI
```

### 3.2 Database tables (new)

#### `anime_metadata_cache`
Stores the full anime metadata for each content (mainId). **Never expires** — the user manually refreshes.

| Column | Type | Description |
|--------|------|-------------|
| `main_id` | TEXT PK FK → content(main_id) | |
| `title` | TEXT | |
| `description` | TEXT | synopsis |
| `cover_url` | TEXT | remote URL |
| `banner_url` | TEXT | |
| `score` | INTEGER | |
| `episodes` | INTEGER | |
| `season` | TEXT | |
| `season_year` | INTEGER | |
| `status` | TEXT | |
| `genres` | TEXT | comma-separated |
| `source_type` | TEXT | `anilist`/`tmdb`/`extension` — which source provided this data |
| `fetched_at` | INTEGER | when this data was last fetched (for display, not expiration) |

**Note:** No `expires_at` column — metadata never expires. The user manually refreshes via the refresh button or pull-to-refresh.

#### `episode_metadata_cache`
Stores episode-level metadata.

| Column | Type | Description |
|--------|------|-------------|
| `main_id` | TEXT FK → content(main_id) | |
| `episode_number` | REAL | |
| `title` | TEXT | episode title |
| `description` | TEXT | episode synopsis |
| `thumbnail_url` | TEXT | remote URL |
| `air_date` | INTEGER | epoch millis |
| `fetched_at` | INTEGER | when this data was last fetched |
| PRIMARY KEY | composite (main_id, episode_number) | |

#### `browse_cache`
Stores browse page sections (trending, popular, etc.).

| Column | Type | Description |
|--------|------|-------------|
| `section_key` | TEXT PK | `trending`/`popular`/`top_rated` |
| `data_json` | TEXT | serialized list of anime IDs + minimal info |
| `fetched_at` | INTEGER | when this data was last fetched |
| `expires_at` | INTEGER | fetched_at + 6h (only the browse page auto-expires) |

### 3.3 Image caching strategy

- Use Coil's built-in disk cache for cover images + thumbnails.
- Configure Coil's `diskCache` with a max size of **500MB** (easily configurable in the future).
- Images are cached by URL — no need for manual download.
- **Survives restart** — Coil's disk cache is persistent (stored in the app's cache directory).
- When the user refreshes a details page, the cover image is automatically updated by Coil (it re-fetches if the URL changed).

### 3.4 Refresh strategy

#### Browse page (homepage)
- **6-hour auto-update**: checked on browse page open. If the cache is older than 6 hours, refresh in the background. Show old data immediately.
- **Pull-to-refresh**: user scrolls down past the top → vibration → "Release to refresh" → loads new data in background → swaps when ready.
- **Manual refresh**: a refresh button in the header.
- **Auto-update is homepage-only** — no other page auto-refreshes.

#### Details page (multi-stage with vibration)
- **Scroll down a little**: vibration → "Refresh episodes list" → only refreshes the episode list from the extension source. If new episodes found, auto-fetch their metadata.
- **Scroll down more**: vibration → "Refresh metadata" → only refreshes metadata (synopsis, score, etc.) from the data source (AniList/TMDB/etc.). No episode changes.
- **Scroll down even more**: vibration → "Refresh all" → full refresh (episodes + metadata + cover images).
- Visual indicators: refresh icons show when the user scrolls past each threshold. When released, a circular spinning indicator shows until refresh completes, then smoothly disappears.
- The "Refresh" button in the three-dot menu does a full refresh (same as stage 3).

#### Library page
- Loads entirely from local cache instantly (no network on tab switch).
- No auto-refresh — the user manually refreshes via the details page.
- Pull-to-refresh: force-refresh all entries' metadata in the background.

### 3.5 Two source types — proper handling

The app has TWO kinds of sources that need to be properly cached and managed:

1. **Data source** (AniList, TMDB, Kitsu) — provides metadata: synopsis, score, episodes count, season, genres, cover image, banner image. Stored in `anime_metadata_cache` with `source_type = 'anilist'`.

2. **Extension/playing source** (Aniyomi extensions) — provides the actual episodes list + video URLs. The extension detail (description, genres, status, author, artist, thumbnail) is already stored in `extension_detail`. Episode metadata is stored in `episode_metadata_cache`.

When the user refreshes:
- "Refresh episodes list" → fetches from the extension source only.
- "Refresh metadata" → fetches from the data source (AniList) only.
- "Refresh all" → fetches from both.

Both caches are updated independently. The data-source selector (AniList/Extension toggle) determines which cache to read from for display.

### 3.6 Library data management

The library page should NOT store its own copy of anime data. Instead:
- The `content` table stores the identity (mainId, contentId, source links).
- The `anime_metadata_cache` table stores the display data (title, cover, score, etc.).
- The `episode_metadata_cache` table stores episode-level data.
- The `extension_detail` table stores extension-specific data.
- The library page reads from `anime_metadata_cache` via `mainId` — no duplication.
- When data changes (new episodes, updated score), only the cache table is updated — the library automatically reflects the change.

### 3.7 Lazy loading + performance

- Use `LazyVerticalGrid` / `LazyColumn` with proper keys (already done — `mainId`).
- Paginate library loading if the library is large (>100 entries).
- Pre-load cover images for visible + nearby items (Coil does this automatically).
- Use `derivedStateOf` for computed filter/sort results to prevent unnecessary recompositions.

---

## 4. Implementation Phases

### D.1 — Local metadata cache (anime + episode)
- Add `anime_metadata_cache` + `episode_metadata_cache` tables.
- Create `AnimeMetadataCache` + `EpisodeMetadataCache` repositories.
- Update DetailsViewModel to read from cache first, then fetch from network if not cached.
- Update EpisodeMetadataFetcher to use cache.
- When refreshing, update the cache (not just the in-memory state).
- Cover images automatically update via Coil when the URL changes.

### D.2 — Browse page cache + refresh
- Add `browse_cache` table.
- Create `BrowseDataCache` repository.
- Update BrowseViewModel to read from cache first.
- Implement pull-to-refresh with vibration.
- Implement 6-hour auto-update (homepage only).

### D.3 — Details page multi-stage refresh
- Implement scroll-based refresh triggers (vibration + visual indicators at each stage).
- Stage 1: refresh episodes list only (from extension source).
- Stage 2: refresh metadata only (from data source).
- Stage 3: refresh all (both sources + cover images).
- Circular spinning indicator during refresh, smooth fade-out when complete.
- Wire the three-dot menu "Refresh" button to stage 3.

### D.4 — Image caching
- Configure Coil's disk cache (500MB, persistent).
- Ensure images survive restart.
- Pre-download cover images for library entries on first library load.

### D.5 — Library performance
- Library loads entirely from `anime_metadata_cache` (no network on tab switch).
- Remove the in-memory `anilistCache` — replaced by the persistent DB cache.
- Background refresh of stale entries (only when the user pulls to refresh).
- Lazy loading + pagination for large libraries.

---

## 5. Confirmed Decisions (from user)

| # | Question | Answer |
|---|----------|--------|
| Q-001 | Cache expiration | **Never expires** — user manually refreshes. No `expires_at` on metadata. |
| Q-002 | Image cache size | **500MB** (configurable in future). |
| Q-003 | Backup file format | **Not in Phase D** — deferred to a future phase. |
| Q-004 | Auto-backup | **Not needed** — deferred. |
| Q-005 | Refresh vibration | **Yes** — on every page with refresh functionality (homepage + details page). |

### Additional confirmed decisions
- 6-hour auto-update is **homepage only** — not on any other page.
- Metadata never expires — the user manually refreshes.
- All cached data must survive device restart (solid caching, not in-memory only).
- Two source types (data source + extension source) must be properly separated + cached independently.
- Backup/restore is NOT in Phase D — only the data management + caching.

---

## 6. Future Considerations (NOT in Phase D)

- **Backup/restore** — export all data (including images) to a file. Restore on any device. Will be a separate phase.
- **Score/episode badges on covers** — display score, released episodes, downloaded episodes, sub/dub counts on library cover images.
- **Browse page hero section** — a large featured anime at the top.
- **Dedicated browse sections** — trending, popular, top rated, new releases.
- **Configurable image cache size** — let the user set the max disk cache size.
