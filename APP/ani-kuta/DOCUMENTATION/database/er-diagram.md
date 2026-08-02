# Entity Relationship Diagram

> Current schema (Phase 3a). Text-based ER diagram.

## Tables (no foreign keys yet — identity system deferred to Phase 4)

```
┌─────────────────────┐     ┌──────────────────────────┐
│ app_metadata        │     │ installed_source          │
│ (key PK, value)     │     │ (ecosystem+source_id PK)  │
└─────────────────────┘     │  + signature_fingerprint  │
                            │  + package_name            │
                            └──────────────────────────┘
                                       │
                            ┌──────────▼───────────────┐
                            │ extension_repo            │
                            │ (ecosystem+url PK)        │
                            └──────────────────────────┘

┌─────────────────────────┐     ┌──────────────────────────┐
│ content_metadata_cache  │     │ episode_metadata_cache   │
│ (content_key PK)        │     │ (episode_key PK)         │
│  title, description,    │     │  title, thumbnail_url,   │
│  genres, status, year,  │     │  air_date, description   │
│  cover_url, source      │     │  source                  │
└─────────────────────────┘     └──────────────────────────┘

┌─────────────────────────┐     ┌──────────────────────────┐
│ watch_progress          │     │ activity_event            │
│ (episode_key PK)        │     │ (id PK)                   │
│  position, duration,    │     │  event_type, content_key, │
│  completed,             │     │  episode_key, session_id, │
│  last_watched_at        │     │  timestamp, duration_ms   │
└─────────────────────────┘     └──────────────────────────┘

┌─────────────────────────┐     ┌──────────────────────────┐
│ user_customization      │     │ download_queue            │
│ (id PK)                 │     │ (id PK)                   │
│  content_key,           │     │  episode_key, state,      │
│  episode_key (nullable),│     │  progress                 │
│  custom_title,          │     └──────────────────────────┘
│  custom_thumbnail,      │
│  custom_description     │     ┌──────────────────────────┐
└─────────────────────────┘     │ downloaded_episode        │
                                │ (episode_key PK)          │
                                │  file_path, file_size     │
                                └──────────────────────────┘
```

## Relationships (Phase 4+ — when identity system is built)

When ContentUID is introduced (Phase 4), the temporary `content_key` / `episode_key` strings will be migrated to foreign keys:

```
content_uid (uid PK) ──< external_reference
                   ──< library_entry
                   ──< episode_uid ──< watch_progress
                                  ──< history
                                  ──< download_queue
                                  ──< downloaded_episode
                                  ──< episode_metadata_cache
```

Until then, tables are loosely coupled by string keys (no enforced FKs).
