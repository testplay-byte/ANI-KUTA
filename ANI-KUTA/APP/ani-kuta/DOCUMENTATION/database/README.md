# Database Documentation — Index

> Per CORE_RULES §24: one file per table group + this README + er-diagram + changelog.
> NOTE (test-feature branch): this folder is being populated incrementally — the
> older table groups' docs are still pending (doc-debt; the schema itself is fully
> described by the `.sq` files in `core/database/src/main/sqldelight/`).

| File | Tables | Status |
|------|--------|--------|
| `playback-cache.md` | `playback_cache_entry` | ✅ D-243 (test-feature branch) |
| *(pending)* | app_settings, watch_progress, activity_event, library_*, main_entry + content_details + lookup tables, data-cache trio, download_queue + downloaded_episode, episode_schedule, episode_update + anime_update_state, genre + content_genre, notification_config + notification_sent, ratings pair, track_entry | doc-debt |

## Migration changelog

- **test-feature/video-cache-new-download (D-243)**: added `playback_cache_entry` (+ `idx_playback_cache_main`). No version bump / no `.sqm` — created via the unconditional `onCreate(db)` + a `hasColumn` guard in `DatabaseDriverFactory` (existing installs get it on first open). Table count 23 → 24.
