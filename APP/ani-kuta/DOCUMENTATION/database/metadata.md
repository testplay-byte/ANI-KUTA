# Metadata Cache Tables

## `content_metadata_cache`
Cached content-level metadata (description, genres, status, year).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `content_key` | TEXT | PRIMARY KEY | Temporary key: "<ecosystem>:<source_id|->:<external_id>" |
| `title` | TEXT | | Canonical title |
| `description` | TEXT | | Synopsis |
| `genres` | TEXT | | JSON array of genre strings |
| `status` | TEXT | | RELEASING, FINISHED, etc. |
| `year` | INTEGER | | Release year |
| `cover_url` | TEXT | | Cover image URL |
| `source` | TEXT | NOT NULL | Which provider: "anilist", "extension", etc. |
| `updated_at` | INTEGER | NOT NULL | When cached (epoch millis) |

**Why**: Prevents re-fetching from AniList/extension every time the Details screen opens. `content_key` is temporary — Phase 4 migrates to ContentUID FK.

## `episode_metadata_cache`
Cached episode metadata (thumbnails, titles, air dates).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `episode_key` | TEXT | PRIMARY KEY | Temporary key |
| `title` | TEXT | | Episode title |
| `thumbnail_url` | TEXT | | Episode thumbnail URL |
| `air_date` | INTEGER | | Air date (epoch millis) |
| `description` | TEXT | | Episode synopsis |
| `source` | TEXT | NOT NULL | Which provider |
| `updated_at` | INTEGER | NOT NULL | When cached |

**Why**: Enables the episode list to show thumbnails + titles without re-fetching from the source each time.
