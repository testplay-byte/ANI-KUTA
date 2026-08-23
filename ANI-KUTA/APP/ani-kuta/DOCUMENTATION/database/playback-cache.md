# playback_cache_entry — Video Playback Cache

> Branch: `test-feature/video-cache-new-download` (D-243). Plan:
> `DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` (Part A).

## What it stores

One row per cached video (streamed-episode replay cache). The data file lives at
`<filesDir>/playback-cache/<cache_key>.bin`; this table is the metadata index
(the settings screen's "Cached episodes" list + LRU eviction source).

## Columns

| Column | Type | Description |
|--------|------|-------------|
| `cache_key` | TEXT PK | sha256(mainId + episodeNumber + sourceId + serverKey) — the STABLE identity. NOT the video URL (extension localhost proxy URLs change every resolve — D-066). |
| `main_id` | TEXT NOT NULL | The content's stable mainId (denormalized copy for display). |
| `anime_title` | TEXT NOT NULL | Denormalized display copy (settings screen row title). |
| `episode_number` | REAL NOT NULL | Episode number (Double; WatchKey's Float converted once at key construction). |
| `episode_title` | TEXT NOT NULL | Denormalized display copy. |
| `source_id` | INTEGER NOT NULL | Aniyomi source.id (identity component). |
| `server_key` | TEXT NOT NULL | `"server\|audio\|quality"` — ResolverVideo.videoTitle minus the volatile urlHash segment. |
| `quality` | TEXT NOT NULL | Display quality label. |
| `content_type` | TEXT NOT NULL | Captured from the first upstream response (default `video/mp4`). |
| `upstream_url` | TEXT NOT NULL | The latest upstream URL (refreshed EVERY play — URLs are per-session volatile). |
| `upstream_headers` | TEXT NOT NULL | MPV comma format (`"Key: Value,Key2: Value2"`), replicated by the proxy. |
| `content_length` | INTEGER NULL | Total bytes (null = unknown — entry serves via redirect passthrough until known). |
| `cached_bytes` | INTEGER NOT NULL | Sum of the cached ranges' lengths. |
| `cached_ranges` | TEXT NOT NULL | Merged, sorted `"a-b,c-d"` byte ranges present in the .bin file. |
| `complete` | INTEGER NOT NULL | 1 when ranges cover `[0, content_length-1]` — the instant-replay fast path (disk-only serving, upstream never contacted). |
| `created_at` | INTEGER NOT NULL | Epoch millis. |
| `last_accessed_at` | INTEGER NOT NULL | Epoch millis — the LRU eviction order. |

Index: `idx_playback_cache_main (main_id)`.

## Why no FK to main_entry

Cache data is denormalized + disposable: entries may outlive content rows (a
library deletion shouldn't nuke the cache entry — eviction owns the lifecycle).
Display fields survive independently.

## Queries

`insertEntry` (INSERT OR REPLACE — concurrent first-opens race-safe), `getEntry`,
`updateUpstream` (fresh URL/headers + LRU touch, throttled to once per 60s),
`updateProgress` (UPDATE-only — a deleted row is never resurrected; tombstone rule),
`touchEntry`, `deleteEntry`, `deleteAll`, `listEntries` (LRU desc, reactive Flow for
the settings screen), `listEntriesForEviction` (LRU asc), `totalCachedBytes`
(COALESCE SUM, reactive), `countEntries`.

## Creation on existing installs

No schema version bump, no `.sqm` — `DatabaseDriverFactory.migrateSchemaIfNeeded`
has an unconditional `onCreate(db)` on every open (D-198 block) + a
`hasColumn(db, "playback_cache_entry", "cache_key")` guard was added following the
Phase TR/NOTIF/Genre precedent. Existing installs get the table on first app open.

## Consistency model

- In-memory live ranges are the source of truth while an entry streams; DB flushes
  are throttled (≥2s or ≥4MB or stream close). After a crash, the DB may UNDER-report
  cached bytes — serving from DB-recorded ranges is always safe (never serves beyond
  recorded ranges; extra file bytes get re-fetched + overwritten with identical data).
- Stale verification on channel open + startup sweep: missing `.bin` → row deleted;
  file shorter than recorded ranges → clamped; `complete` but size ≠ content_length → reset.
