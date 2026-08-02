# Watch Progress Table

## `watch_progress`
Per-episode watch progress. Separate from `activity_event` for query efficiency.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_key` | TEXT | PRIMARY KEY | Temporary key |
| `position` | INTEGER | NOT NULL | Position in seconds |
| `duration` | INTEGER | NOT NULL | Total duration in seconds |
| `completed` | INTEGER | NOT NULL DEFAULT 0 | 1 if watched to completion |
| `completed_at` | INTEGER | | When completed (epoch millis) |
| `last_watched_at` | INTEGER | NOT NULL | Epoch millis |

**Index**: `idx_watch_progress_last_watched` ON `last_watched_at DESC` — "Continue Watching" query.

**Why**: "Continue Watching" needs O(1) access to the most recent progress. If progress were stored only in `activity_event`, it would require scanning thousands of play/pause events. This table is 1 row per episode — `SELECT * FROM watch_progress ORDER BY last_watched_at DESC LIMIT 20` is instant.

**Writer**: `:core:player` (via `:core:watch-progress` interface).
**Reader**: Phase 4 `:feature:anime-history:impl` + `:feature:anime-library:impl` (Continue Watching section).
