# Database Changelog

> Migration history. Every schema change gets an entry here (CORE_RULES.md §24).

---

## Version 1 → 2 (Phase 3a — 2026-08-02)

**What changed**: Added 9 new tables for Phase 3a.

**New tables:**
- `installed_source` — installed extensions (ecosystem, source_id, name, version, package_name, signature_fingerprint, is_enabled, installed_at, last_updated_at)
- `extension_repo` — extension repos (ecosystem, url, name, added_at)
- `content_metadata_cache` — cached content metadata (content_key PK, title, description, genres, status, year, cover_url, source, updated_at)
- `episode_metadata_cache` — cached episode metadata (episode_key PK, title, thumbnail_url, air_date, description, source, updated_at)
- `activity_event` — internal tracking log (id, event_type, content_key, episode_key, session_id, route, content_type, duration_ms, payload, timestamp)
- `watch_progress` — watch progress per episode (episode_key PK, position, duration, completed, completed_at, last_watched_at)
- `user_customization` — user metadata overrides (id, content_key, episode_key, custom_title, custom_thumbnail, custom_description, updated_at)
- `download_queue` — download queue (id, episode_key, state, progress, error_message, queued_at, started_at, completed_at)
- `downloaded_episode` — downloaded files on disk (episode_key PK, file_path, file_size, quality, downloaded_at)

**Why**: Phase 3a needs storage for extensions, metadata, internal tracking, watch progress, user customizations, and downloads. The complex identity system (ContentUID + ExternalReference) is deferred to Phase 4.

**Temporary key format**: All tables use `content_key` / `episode_key` strings (`"<ecosystem>:<source_id|->:<external_id>"`). Phase 4 will introduce ContentUID and migrate.

---

## Version 1 (Phase 2 — 2026-08-02)

**What changed**: Initial schema.

**Tables:**
- `app_metadata` — key-value store for app-level flags (schema version, migration flags, etc.)

**Why**: Phase 2 minimal viable structure. Just a key-value store to track schema version.
