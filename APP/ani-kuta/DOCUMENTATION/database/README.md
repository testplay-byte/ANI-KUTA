# Database Documentation

> Canonical documentation for the ANI-KUTA database schema.
> Per CORE_RULES.md §24: this MUST be updated whenever the schema changes.

## Schema Version
Current: **2** (Phase 3a — basic tables added)

## Table Groups

| Group | Tables | Status | Doc |
|-------|--------|--------|-----|
| **App** | `app_metadata` | ✅ Phase 2 | [`app.md`](app.md) |
| **Extensions** | `installed_source`, `extension_repo` | ✅ Phase 3a | [`extensions.md`](extensions.md) |
| **Metadata** | `content_metadata_cache`, `episode_metadata_cache` | ✅ Phase 3a | [`metadata.md`](metadata.md) |
| **Tracking** | `activity_event` | ✅ Phase 3a | [`tracking.md`](tracking.md) |
| **Watch** | `watch_progress` | ✅ Phase 3a | [`watch.md`](watch.md) |
| **Customization** | `user_customization` | ✅ Phase 3a | [`customization.md`](customization.md) |
| **Downloads** | `download_queue`, `downloaded_episode` | ✅ Phase 3a | [`downloads.md`](downloads.md) |
| **Identity** | (deferred to Phase 4) | ⏳ | [`identity.md`](identity.md) |
| **Library** | (deferred to Phase 4) | ⏳ | [`library.md`](library.md) |

## Temporary Content Key Format
Until the identity system is built (Phase 4), content is identified by a composite string key:
```
"<ecosystem>:<source_id|->:<external_id>"
```
Examples:
- `"animiru:gogo:attack-on-titan"` — Animiru extension source
- `"anilist:-:16498"` — AniList tracker (no source_id)

Phase 4 will introduce `ContentUID` (UUID) and migrate all tables.

## Migration History
See [`changelog.md`](changelog.md) for the full migration log.

## ER Diagram
See [`er-diagram.md`](er-diagram.md) for the entity relationship diagram.
