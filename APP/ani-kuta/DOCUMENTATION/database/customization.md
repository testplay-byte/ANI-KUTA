# User Customization Table

## `user_customization`
User's custom metadata overrides (custom thumbnails, titles, descriptions).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| `content_key` | TEXT | NOT NULL | Content to override |
| `episode_key` | TEXT | | Episode to override (NULL = content-level override) |
| `custom_title` | TEXT | | User's custom title |
| `custom_thumbnail` | TEXT | | User's custom thumbnail (file path or URL) |
| `custom_description` | TEXT | | User's custom description |
| `updated_at` | INTEGER | NOT NULL | Epoch millis |

**Partial unique indexes** (SQLite treats NULL as distinct in UNIQUE):
- `idx_custom_unique_content` UNIQUE ON `(content_key)` WHERE `episode_key IS NULL` — one content-level override per content.
- `idx_custom_unique_episode` UNIQUE ON `(episode_key)` WHERE `episode_key IS NOT NULL` — one episode-level override per episode.

**Why**: The user wants the ability to customize metadata per anime/episode (D-046). `LocalMetadataProvider` reads from this table and overrides external metadata (AniList/extension). Priority: local override > AniList > extension source.
