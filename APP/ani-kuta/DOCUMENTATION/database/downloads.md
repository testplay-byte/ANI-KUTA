# Downloads Tables

## `download_queue`
The download queue (episodes to download or downloading).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `episode_key` | TEXT | NOT NULL | Episode to download |
| `state` | TEXT | NOT NULL | QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED |
| `progress` | INTEGER | NOT NULL DEFAULT 0 | 0-100 |
| `error_message` | TEXT | | If failed |
| `queued_at` | INTEGER | NOT NULL | Epoch millis |
| `started_at` | INTEGER | | |
| `completed_at` | INTEGER | | |

**Index**: `idx_download_state` ON `state` — filter by state.

## `downloaded_episode`
Downloaded episode files on disk.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_key` | TEXT | PRIMARY KEY | |
| `file_path` | TEXT | NOT NULL | Path to downloaded file |
| `file_size` | INTEGER | NOT NULL | Bytes |
| `quality` | TEXT | | e.g. "1080p", "720p" |
| `downloaded_at` | INTEGER | NOT NULL | Epoch millis |

**Why**: `download_queue` tracks the queue state. `downloaded_episode` tracks completed files on disk. On completion, the queue row is deleted and the downloaded_episode row is created.
