# Activity Event Table (Internal Tracking)

## `activity_event`
User activity event log. The core of the internal tracking system (D-045).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `event_type` | TEXT | NOT NULL | WATCH_START, WATCH_PAUSE, WATCH_COMPLETE, SEARCH, DOWNLOAD_START, DOWNLOAD_COMPLETE, LIBRARY_ADD, LIBRARY_REMOVE, RATING, etc. |
| `content_key` | TEXT | | Associated content (nullable for non-content events) |
| `episode_key` | TEXT | | Associated episode (nullable) |
| `session_id` | TEXT | NOT NULL | App session ID (UUID, new per process restart) |
| `route` | TEXT | | Screen route when event occurred |
| `content_type` | TEXT | | VIDEO, IMAGE, TEXT |
| `duration_ms` | INTEGER | | Event duration (e.g. watch time) |
| `payload` | TEXT | | JSON blob for extra data |
| `timestamp` | INTEGER | NOT NULL | Epoch millis |

**Indexes**:
- `idx_activity_timestamp` ON `timestamp DESC` — recent events query.
- `idx_activity_type` ON `event_type` — filter by type.
- `idx_activity_content` ON `content_key` — per-content stats.

**Why**: The user's KEY requirement (D-045) — a full-fledged internal tracking system. Records everything the user does. 365-day default retention, unlimited option. Prune worker runs daily.

**Write batching**: Player events batched in memory, flushed every 30s or on pause/stop (not every 10s) to reduce DB load.
