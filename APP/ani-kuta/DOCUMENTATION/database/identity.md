# Identity Tables (DEFERRED to Phase 4)

> These tables are NOT yet built. They will be added in Phase 4 when the identity system is implemented.
> See `APP/ani-kuta/DOCUMENTATION/17-database-schema.md` for the full design.

## Planned Tables
- `content_uid` — the app's stable UUID for each content.
- `external_reference` — links ContentUID to external systems (Aniyomi, AniList, MAL, etc.).
- `episode_uid` — stable UUID for each episode.
- `episode_external_ref` — links EpisodeUID to external systems.
- `identity_event` — merge/split log (for undo).

## Migration (Phase 4)
All tables using temporary `content_key` / `episode_key` strings will be migrated to use `content_uid` / `episode_uid` foreign keys. The migration:
1. Parse each `content_key` → (ecosystem, source_id, external_id).
2. Call `IdentityResolver.resolveOrCreate()` → get ContentUID.
3. Update the row with the new UID.
4. Dedup rows that map to the same ContentUID.
