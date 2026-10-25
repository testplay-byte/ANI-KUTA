# Database Documentation — Index

> Per CORE_RULES §24: one file per table group + this README + er-diagram + changelog.
> NOTE (test-feature branch): this folder is being populated incrementally — the
> older table groups' docs are still pending (doc-debt; the schema itself is fully
> described by the `.sq` files in `core/database/src/main/sqldelight/`).

| File | Tables | Status |
|------|--------|--------|
| [SCHEMA.md](SCHEMA.md) | **ALL 25 tables** — the spine diagram, the content identity system in full, the per-group inventory, the maintainability rules | ✅ rounds 126–127 |
| [DATA-FLOWS.md](DATA-FLOWS.md) | — (the write paths: every user action → the tables it touches) | ✅ rounds 126–127 |
| [FLAWS.md](FLAWS.md) | — (the B1–B7 register — **every flaw RESOLVED in round 127**, with the decisions noted) | ✅ round 127 |
| `playback-cache.md` | `playback_cache_entry` | ✅ D-243 (test-feature branch) |
| *(pending)* | app_settings, watch_progress, activity_event, library_*, main_entry + content_details + lookup tables, data-cache trio, download_queue + downloaded_episode, episode_schedule, episode_update + anime_update_state, genre + content_genre, notification_config + notification_sent, ratings pair, track_entry | doc-debt |

## Migration changelog

- **round 127 (D-789..D-798)**: the hardening wave — **the first VERSIONED
  migration** (`1.sqm`, v1 → v2: `watch_progress.played_time_ms`, the
  cumulative played-time gate; `SchemaMigrationTest` proves the v1→v2
  conversion with data preservation), the axis-merge/replace pairs (B2/D-792),
  the scanner insert-if-missing + ext-axis restore (B1/D-791, B4/D-793), the
  validated display_source flips (B3/D-794), the single-truth title writer
  (B5/D-797), the shared ecosystem helper (B6/D-796), the canonical
  extension-id pair + startup reconciliation (B7/D-795). The lock suites:
  `:core:content` (ContentSchemaAndFlowsTest), `:core:database`
  (SchemaMigrationTest — NEW), `:core:watch-progress`
  (SqlDelightWatchProgressStoreTest — NEW).
- **round 126 (D-785)**: the doc-debt payoff — SCHEMA.md (all 25 tables) +
  DATA-FLOWS.md (the write-path map) + FLAWS.md (the B1–B7 register); the
  DB-write logging standard lands in CORE_RULES §5; the schema-behavior lock
  tests land in `:core:content`'s test source set. No schema change.
- **test-feature/video-cache-new-download (D-243)**: added `playback_cache_entry` (+ `idx_playback_cache_main`). No version bump / no `.sqm` — created via the unconditional `onCreate(db)` + a `hasColumn` guard in `DatabaseDriverFactory` (existing installs get it on first open). Table count 23 → 24.
