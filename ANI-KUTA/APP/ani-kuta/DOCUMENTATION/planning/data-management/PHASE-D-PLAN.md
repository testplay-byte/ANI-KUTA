# Phase D — Data Management, Caching & Backup/Restore (Plan)

> **Status:** PLANNING — to be implemented in the next session
> **Date:** Post-Phase C, session web-f53f0459
> **Depends on:** Phase C (content ID system ✅, library ✅)

---

## 1. Problem Statement

The app currently fetches data from the network on EVERY screen load:
- **Browse page** — fetches trending anime from AniList every time it's opened.
- **Details page** — fetches full AniList details every time an anime is opened.
- **Library page** — fetches AniList data for each library entry (partially fixed with D-141 in-memory cache, but not persisted).
- **Episode metadata** — fetched from Anikage/Jikan/AniList every time.

This causes:
1. **Slow loading** — every screen waits for network.
2. **Unnecessary data usage** — same data fetched repeatedly.
3. **No offline support** — can't browse library without network.
4. **No backup/restore** — if the app is uninstalled, all data is lost.

---

## 2. Goals

1. **Local-first data storage** — all metadata (anime info, episode info, covers) stored locally. Network is only used for refresh.
2. **Smart refresh** — multi-stage refresh on the details page (episodes list → metadata → full refresh). Pull-to-refresh on browse page. Auto-update every 6 hours.
3. **Image caching** — cover images + episode thumbnails stored locally for offline access.
4. **Backup/restore** — export all data (including images) to a backup file. Restore on any device.
5. **Performance** — library loads instantly from local storage. No network calls on tab switch.

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
    ├── ImageCache.kt               ← downloads + caches cover/thumbnail images
    ├── CacheRefreshManager.kt      ← manages refresh intervals (6-hour auto-update)
    └── DataCacheModule.kt          ← Koin DI
```

### 3.2 Database tables (new)

#### `anime_metadata_cache`
Stores the full anime metadata for each content (mainId).

| Column | Type | Description |
|--------|------|-------------|
| `main_id` | TEXT PK FK → content(main_id) | |
| `title` | TEXT | |
| `description` | TEXT | synopsis |
| `cover_url` | TEXT | remote URL |
| `cover_local_path` | TEXT | local cached path |
| `banner_url` | TEXT | |
| `banner_local_path` | TEXT | |
| `score` | INTEGER | |
| `episodes` | INTEGER | |
| `season` | TEXT | |
| `season_year` | INTEGER | |
| `status` | TEXT | |
| `genres` | TEXT | comma-separated |
| `source_type` | TEXT | `anilist`/`tmdb`/`extension` |
| `fetched_at` | INTEGER | when this data was last fetched |
| `expires_at` | INTEGER | when this data expires (fetched_at + 6h) |

#### `episode_metadata_cache`
Stores episode-level metadata.

| Column | Type | Description |
|--------|------|-------------|
| `main_id` | TEXT FK → content(main_id) | |
| `episode_number` | REAL | |
| `title` | TEXT | episode title |
| `description` | TEXT | episode synopsis |
| `thumbnail_url` | TEXT | remote URL |
| `thumbnail_local_path` | TEXT | local cached path |
| `air_date` | INTEGER | epoch millis |
| `fetched_at` | INTEGER | |

#### `browse_cache`
Stores browse page sections (trending, popular, etc.).

| Column | Type | Description |
|--------|------|-------------|
| `section_key` | TEXT PK | `trending`/`popular`/`top_rated` |
| `data_json` | TEXT | serialized list of anime IDs + minimal info |
| `fetched_at` | INTEGER | |
| `expires_at` | INTEGER | fetched_at + 6h |

#### `image_cache`
Tracks locally downloaded images.

| Column | Type | Description |
|--------|------|-------------|
| `url` | TEXT PK | remote URL |
| `local_path` | TEXT | local file path |
| `downloaded_at` | INTEGER | |
| `size_bytes` | INTEGER | |

### 3.3 Image caching strategy

- Use Coil's built-in image cache (disk cache) for cover images + thumbnails.
- Configure Coil's `diskCache` with a max size (e.g. 500MB).
- Images are cached by URL — no need for manual download.
- For backup: export the Coil cache directory + the `image_cache` table.

### 3.4 Refresh strategy

#### Browse page
- **Pull-to-refresh**: user scrolls down past the top → vibration → "Release to refresh" → loads new data in background → swaps when ready.
- **Auto-update**: every 6 hours (checked on browse page open). If the cache is older than 6 hours, refresh in the background. Show old data immediately.
- **Manual refresh**: a refresh button in the header.

#### Details page (multi-stage)
- **Scroll down a little**: vibration → "Refresh episodes list" → only refreshes the episode list from the extension source. If new episodes found, auto-fetch their metadata.
- **Scroll down more**: "Refresh metadata" → only refreshes AniList metadata (synopsis, score, etc.). No episode changes.
- **Scroll down even more**: "Refresh all" → full refresh (episodes + metadata + cover images).

#### Library page
- Loads from local cache instantly.
- Background refresh: if any entry's metadata is older than 6 hours, refresh in the background.
- Pull-to-refresh: force-refresh all entries.

### 3.5 Backup/restore

#### Backup format
A ZIP file containing:
- `anikuta_backup.json` — all database tables as JSON (content, library, metadata caches, settings).
- `images/` — all cached cover images + thumbnails.

#### Backup options
The user can choose what to include:
- ✅ Library entries (content + library_item tables)
- ✅ Anime metadata (anime_metadata_cache + episode_metadata_cache)
- ✅ Cover images (Coil cache)
- ⬜ Browse page data (browse_cache)
- ⬜ Settings (preferences)

#### Restore
- Import the ZIP file.
- Restore database tables (INSERT OR REPLACE).
- Restore images to Coil cache directory.
- Verify content IDs match (if not, create new content records).

### 3.6 Library data management

The library page should NOT store its own copy of anime data. Instead:
- The `content` table stores the identity (mainId, contentId, source links).
- The `anime_metadata_cache` table stores the display data (title, cover, score, etc.).
- The `episode_metadata_cache` table stores episode-level data.
- The library page reads from `anime_metadata_cache` via `mainId` — no duplication.
- When data changes (new episodes, updated score), only the cache table is updated — the library automatically reflects the change.

This avoids the problem of "if we save all data in the library page, we need to update the whole library every time."

---

## 4. Implementation Phases

### D.1 — Local metadata cache (anime + episode)
- Add `anime_metadata_cache` + `episode_metadata_cache` tables.
- Create `AnimeMetadataCache` + `EpisodeMetadataCache` repositories.
- Update DetailsViewModel to read from cache first, then fetch from network if stale.
- Update EpisodeMetadataFetcher to use cache.

### D.2 — Browse page cache + refresh
- Add `browse_cache` table.
- Create `BrowseDataCache` repository.
- Update BrowseViewModel to read from cache first.
- Implement pull-to-refresh.
- Implement 6-hour auto-update.

### D.3 — Details page multi-stage refresh
- Implement scroll-based refresh triggers (vibration + 3 stages).
- Stage 1: refresh episodes list only.
- Stage 2: refresh metadata only.
- Stage 3: refresh all.

### D.4 — Image caching
- Configure Coil's disk cache.
- Add `image_cache` table for tracking.
- Pre-download cover images for library entries.

### D.5 — Backup/restore
- Create backup export (ZIP with JSON + images).
- Create restore import.
- Add backup settings screen.
- Add backup file picker.

### D.6 — Library performance
- Library loads entirely from local cache (no network on tab switch).
- Background refresh of stale entries.
- Pull-to-refresh for force-update.

---

## 5. Open Questions

### Q-001: Cache expiration
How long should metadata be cached before it's considered stale?
- Option A: 6 hours (same as browse auto-update).
- Option B: 24 hours.
- Option C: Configurable by the user.

### Q-002: Image cache size
What should the max disk cache size be for images?
- Option A: 500MB (reasonable for most devices).
- Option B: 1GB.
- Option C: Configurable.

### Q-003: Backup file format
ZIP or custom format?
- Option A: ZIP (standard, easy to share).
- Option B: Custom binary (more efficient but non-standard).

### Q-004: Auto-backup
Should the app auto-backup periodically?
- Option A: No (manual only).
- Option B: Weekly auto-backup to a user-selected location.

### Q-005: Refresh vibration
Should the multi-stage refresh on the details page use vibration?
- Option A: Yes (haptic feedback when the user scrolls past each threshold).
- Option B: No (visual indicator only).

---

## 6. Future Considerations

- **Score/episode badges on covers** — the user wants to display score, released episodes, downloaded episodes, sub/dub counts on library cover images. This data would come from the metadata cache + watch progress (Phase E).
- **Browse page hero section** — a large featured anime at the top of the browse page. Data would come from the browse cache.
- **Dedicated browse sections** — trending, popular, top rated, new releases. Each section cached separately.
- **Source link persistence** — the details page should remember which source was linked + restore it on reopen (partially done in D-140 via `loadLinkedSource`).
