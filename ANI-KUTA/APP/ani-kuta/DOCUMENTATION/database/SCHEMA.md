# Database Schema — the full reference

> Round 126 (the user's order): the doc-debt payoff — every table group, what
> it stores, and who writes/reads it. **The source of truth is the `.sq`
> files** in `core/database/src/main/sqldelight/com/confused/anikuta/core/database/`;
> this document describes them as of round 126 and must move with them
> (the D-785 documentation rule). 25 tables across 17 files.

## The spine

```
data_source (lookup)      system (lookup)
        └────────── main_entry ──────────┘
                     │  1:1
              content_details   ← the two metadata axes (data_* / ext_*)
                     │
     ┌───────────────┼──────────────────────────────┐
     │ every other table hangs off main_id:         │
 watch_progress · library_item · content_genre ·   │
 user_rating · user_episode_rating · track_entry · │
 content_tracking_state · data_cache_episode ·     │
 browse_cache · download_queue · downloaded_episode│
 episode_schedule · episode_update ·               │
 anime_update_state · notification_sent ·          │
 playback_cache_entry · activity_event             │
```

## 1. The content identity system (`content.sq`) — the core

### `data_source` — the metadata/tracking lookup (AniList, TMDB, Kitsu, MAL)
Seeded once at first launch; the user never edits it. `main_entry.data_source_id` references it.

### `system` — the extension-system lookup (Aniyomi, CloudStream, Sora, MangaYomi)
Seeded once. `main_entry.system_id` references it. The D-540 ecosystem rule:
the recorded system follows the content's REAL ecosystem (a bit-62 synthetic
source id means CloudStream), never a hardcoded name.

### `main_entry` — the central identity (one row per content)
- `main_id` — the STABLE UUID (never changes; every other table hangs off it).
- `content_id` — the structured identity string; REGENERATED on source
  switch/link/unlink (the ContentIdGenerator shape: data-source/system/repo/
  sourceId/animeUrl segments). Everything keyed by content identity (download
  snapshots, `.data.json`) keys on this.
- `title` — **the display title** (what the Library shows). Written by
  insert/switch/link/unlink/axis-refresh title-sync (D-769).
- `content_type` / `content_format` — `'anime'`/`'video'` today; **manga and
  novels ride these columns** (D-198 planned them; no schema change needed).
- Denormalized extension identity: `extension_id` + `source_id` + `anime_url`
  — kept for the hot `getMainEntryByExtension` open-path lookup (no JOIN).
  The AUTHORITATIVE extension metadata lives in `content_details`.
- `display_source` — the user's persisted choice: `'data_source'` (show the
  data axis) or `'extension'` (show the ext axis). Written by
  switchDataSource/link/unlink; read by the merge default, the Library cover
  order, and the title-sync gates.

### `content_details` — the metadata, 1:1, TWO independent axes in one row
- **`data_*` axis** — the metadata/tracking source (AniList today):
  `data_source_type` ('anilist' | … | NULL = not linked), `data_source_ref_id`,
  score, episodes (NULL for manga/novels by design), season, year, status,
  genres (comma-joined), synopsis, cover, banner, `data_extra_json`
  (DataSourceExtras: idMal, trailer, age rating, studio…), updated_at.
- **`ext_*` axis** — the playback source (Aniyomi/CloudStream today):
  `extension_type` ('aniyomi' | 'cloudstream' | …), `extension_id`, `source_id`,
  `anime_url`, description, genres, status, author, artist, thumbnail,
  `ext_extra_json` (ExtensionExtras: year, score, **title** [D-777],
  scanlator/chapter/volume — the last pair is where manga chapters go),
  updated_at.
- `cover_accent_argb` — the Palette-extracted adaptive color.
- **The axis discipline**: `updateDataSourceAxis`/`updateExtensionAxis` are
  partial UPDATEs (the sibling axis untouched) but set EVERY column of their
  own axis — callers MUST null-merge first (flaw B2 in FLAWS.md). The axis
  switches/unlinks: `updateDataSourceAxis`+`updateMainEntryDisplaySource`,
  `clearDataSourceAxis`, `clearExtensionAxis`.

## 2. The table groups (16 more files)

| File | Tables | Stores | Key linkage |
|---|---|---|---|
| `watch.sq` | `watch_progress` | Per-episode resume position/duration, the watched state machine (completed + `auto_mark_suppressed` + `user_marked_watched`), watch_count, timestamps, and `played_time_ms` — the CUMULATIVE actually-played milliseconds across all sessions (D-789). The auto-mark fires on `save()` when the fraction exceeds `watchPreferences.autoMarkThreshold` (the user's setting; default 0.85) **AND** the played total reached 60 s — watched = genuinely finished AND genuinely watched (round 126's per-session gate became cumulative in round 127). | `episode_key` (PK) + `main_id` |
| `library.sq` | `library_category`, `library_item` | The user's categories and which `main_id`s are in them (added_at order). | `main_id` |
| `genres.sq` | `genre`, `content_genre` | The genre vocabulary + the content↔genre join. | `main_id` |
| `track.sq` | `track_entry`, `content_tracking_state` | Tracker (AniList/MAL) bindings and the tracked progress state. | `main_id` |
| `ratings.sq` | `user_rating`, `user_episode_rating` | The user's per-content and per-episode ratings. | `main_id` |
| `dataCache.sq` | `data_cache_episode`, `browse_cache` | The episode-list cache (extension metadata per episode) + the browse SWR cache. | `main_id` |
| `downloadQueue.sq` | `download_queue` | The queue: order, status, progress, per-task identity (content_id-keyed). | `main_id` |
| `downloadedEpisode.sq` | `downloaded_episode` | The downloaded files registry (URI, size, quality/audio, content_id). | `main_id` |
| `episodeSchedule.sq` | `episode_schedule` | Airing schedule rows (per episode, airingAt). | `main_id` |
| `episodeUpdate.sq` / `animeUpdateState.sq` | `episode_update`, `anime_update_state` | The update-check engine's per-episode finds and per-content state (auto-update enabled, next airing, learned delay). | `main_id` |
| `notifications.sq` | `notification_config`, `notification_sent` | The per-content notification config + the dedupe ledger of what was already posted. | `main_id` |
| `playbackCache.sq` | `playback_cache_entry` | The video-caching window identity/progress (D-243). | `main_id` |
| `tracking.sq` | `activity_event` | The in-app activity feed. | `main_id` |
| `appSettings.sq` | `app_settings` | The key-value app settings that survive reinstalls. | — |

## 3. The rules that keep it maintainable

1. **`main_id` is the join key for everything new.** A future table (manga
   chapters, backup manifests, novels) hangs off `main_id` — backup/restore
   then serializes per-content as a closed row set.
2. **New optional metadata goes in the extras JSON** (`data_extra_json` /
   `ext_extra_json`, `ignoreUnknownKeys = true`) before it earns a column
   (the Task 47/D-777 pattern — additive, no migration).
3. **Schema CHANGES go through versioned `.sqm` migrations.** Round 127
   (D-789/D-798) introduced the first one (`1.sqm`, v1 → v2: the
   `watch_progress.played_time_ms` column); every future schema change adds
   `N.sqm`, and `SchemaMigrationTest` (core/database) proves each migration
   converts a real v1-shaped database **with its data intact** — the user's
   requirement that an app update upgrades old installs in place. The old
   `onOpen` column probes in `DatabaseDriverFactory` remain as a safety net
   (all guarded/idempotent); new changes must NOT go through them.
4. **Every write logs itself** (D-785, CORE_RULES §5) — see DATA-FLOWS.md for
   the write-path map and FLAWS.md for the known traps.
5. **Axis writes MERGE by default; replace is explicit** (D-792, round 127).
   `updateDataSourceAxis`/`updateExtensionAxis` are COALESCE merges (a null
   field preserves the stored value — the "refresh replaces good data with a
   stub" wipe class is impossible); `replaceDataSourceAxis`/`replaceExtensionAxis`
   are the intentional whole-axis switches and log at WARN. In content_details
   the TEXT `extension_id` is canonical: the INTEGER `source_id` is derived in
   the SQL and can never drift (B7/D-795).
