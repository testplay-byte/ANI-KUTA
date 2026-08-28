# playback_cache_entry — Video Playback Cache

> Branch: `test-feature/video-cache-new-download` (D-243). Plan:
> `DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` (Part A).

## What it stores

One row per cached video (streamed-episode replay cache). Storage layout:
- **Progressive entries**: `<filesDir>/playback-cache/<cache_key>.bin` — merged byte ranges (session-2: learn-mode serving + chunked-with-tee when the upstream length is unknown; the separate probe is gone).
- **HLS entries** (session-2): `<filesDir>/playback-cache/<cache_key>.seg/` — one file per media segment (`seg_<i>_<urlHash8>.ts`) + the init segment (`init_<urlHash8>.ts`). The proxy REWRITES playlists (variants → `/p/<key>/<i>`, segments → `/s/<key>/<i|init>`) so MPV's segment fetches go through the cache. This table is the metadata index (the settings screen's "Cached episodes" list + LRU eviction source).

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
| `content_length` | INTEGER NULL | Total bytes (progressive; null = unknown — learn-mode/chunked path). |
| `cached_bytes` | INTEGER NOT NULL | Sum of the cached ranges' lengths (progressive) or of the cached segment files (HLS). |
| `cached_ranges` | TEXT NOT NULL | Merged, sorted `"a-b,c-d"` byte ranges present in the .bin file (progressive only). |
| `complete` | INTEGER NOT NULL | 1 when fully cached (progressive: ranges cover `[0, len-1]`; HLS: all VOD segments present). Instant-replay fast path. |
| `created_at` | INTEGER NOT NULL | Epoch millis. |
| `last_accessed_at` | INTEGER NOT NULL | Epoch millis — the LRU eviction order. |
| `segment_total` | INTEGER NOT NULL | HLS: segment count from the last media-playlist parse (0 = progressive entry). |
| `segments_cached` | INTEGER NOT NULL | HLS: distinct cached segment indices (recounted from disk — race-safe source of truth). |
| `subtitle_tracks` | TEXT NOT NULL | External subtitle tracks (WatchKey wire format `"url\u001Flang"` per line) — for tap-to-play. |
| `audio_tracks` | TEXT NOT NULL | External audio tracks (same format) — for tap-to-play. |

Index: `idx_playback_cache_main (main_id)`.

## Why no FK to main_entry

Cache data is denormalized + disposable: entries may outlive content rows (a
library deletion shouldn't nuke the cache entry — eviction owns the lifecycle).
Display fields survive independently.

## Queries

`insertEntry` (INSERT OR REPLACE — concurrent first-opens race-safe), `getEntry`,
`updateUpstream` (fresh URL/headers + track lists + LRU touch, throttled to once per 60s),
`updateProgress` (UPDATE-only — a deleted row is never resurrected; tombstone rule),
`updateSegmentStats` (HLS: recounted-from-disk segment count/bytes/complete),
`touchEntry`, `deleteEntry`, `deleteAll`, `listEntries` (LRU desc, reactive Flow for
the settings screen), `listEntriesForEviction` (LRU asc), `totalCachedBytes`
(COALESCE SUM, reactive), `countEntries`.

## Creation on existing installs

No schema version bump, no `.sqm` — `DatabaseDriverFactory.migrateSchemaIfNeeded`
has an unconditional `onCreate(db)` on every open (D-198 block) + a
`hasColumn(db, "playback_cache_entry", "cache_key")` guard, and (session-2) four
ALTER guards for `segment_total`, `segments_cached`, `subtitle_tracks`,
`audio_tracks` following the D-223 pattern. Existing installs get the table +
columns on first app open.

## Consistency model

- Progressive: in-memory live ranges are the source of truth while an entry streams; DB flushes are throttled (≥2s or ≥4MB or stream close). After a crash, the DB may UNDER-report cached bytes — serving from DB-recorded ranges is always safe (never serves beyond recorded ranges; extra file bytes get re-fetched + overwritten with identical data).
- HLS: the `.seg` directory is the source of truth — `segments_cached`/`cached_bytes` are RECOUNTED from disk after every segment fetch (self-heals serve/fill races).
- Stale verification on channel open + startup sweep: missing `.bin`/`.seg` → row reset/removed; file shorter than recorded ranges → clamped; `complete` but size mismatch → reset.
- Background fill (session-2): a per-entry job fetches remaining gaps (8 MB blocks, player-frontier-aware ±32 MB) or missing segments (VOD only) until complete; cancels on delete/evict/disable.
