# 03 — DATA LAYER (30 tables, schema v4, verified 2026-11-13)

## 1. The schema spine

`core/database/src/main/sqldelight/com/confused/anikuta/core/database/` — 17 `.sq` files +
**3 versioned migrations** (1.sqm v1→2, 2.sqm v2→3, 3.sqm v3→v4). Driver:
`DatabaseDriverFactory` — AndroidSqliteDriver + `PRAGMA foreign_keys = ON` on every open +
the guarded legacy ALTER pass (`migrateSchemaIfNeeded`, now also run as the onUpgrade
PRE-FLIGHT, round 145 — before the .sqm chain, so the v1-era installs get their late columns
before the chain SELECTs them).

**The tables (30)** — the spine groups:
- Identity: `main_entry`, `data_source`, `content_details` (the ext/data axes + the extras
  JSON), `content_ext_repo` (dropped — dead), `system` (the extension systems).
- Library: `library_category`, `library_item`.
- Watch: `watch_progress` (position/duration/completed + the CF1 fields + `played_time_ms`
  monotonic), `watch_session` (the per-streak ledger — the Profile's per-day truth).
- Content detail: `episode_metadata`-shaped rows inside content_details' extras, `genres`,
  `content_genre`.
- Updates: `anime_update_state` (the smart-release state incl. `learned_offset_ms`),
  `episode_update` (the new-episode feed), `update_state`.
- Notifications: `notification_config` (per-anime), `notification_sent` (the 90-day dedup).
- Schedule: `episode_schedule` (library-scoped airing rows).
- Tracking: `track_entry`, `content_tracking_state`, `activity_event`.
- Ratings: `user_rating`, `user_episode_rating`.
- Downloads: `download_queue`, `downloaded_episode` (+ the `.data.json` sidecar per folder).
- Cache: `data_cache_episode` (+ browse_cache), `playback_cache_entry`.

## 2. The migrations (the upgrade story)

| Version | File | What it does | The trap it avoids |
|---|---|---|---|
| v1→v2 | 1.sqm | adds `played_time_ms` (the cumulative played counter) | the D-789 cumulative watch-mark |
| v2→v3 | 2.sqm | creates `watch_session` (the per-day ledger) | |
| v3→v4 | 3.sqm | rebuilds watch_progress + watch_session WITHOUT the main_entry FK CASCADE | D-862: tracking survives identity deletion |

**The onUpgrade PRE-FLIGHT (round 145, F1)**: `onUpgrade` runs `migrateSchemaIfNeeded` BEFORE
the .sqm chain — the chain SELECTs columns the legacy onOpen ALTERs used to add; without the
pre-flight a v1-era install crashed the chain ("no such column"). The remaining documented
cohort: a device with NO watch_progress table at all (v0.2.16-and-earlier) still needs a
one-time uninstall (its upgrade path predates every migration — known limitation).

**The hasColumn contract (round 145, F2)**: inspect EVERY row of PRAGMA table_info (the old
moveToFirst+moveToNext skipped row 0 — the first column was invisible to every guard).

## 3. The write contracts (the rules that protect the data)

1. **Axis writes are COALESCE merges** (D-792): a null field preserves the stored value; the
   intentional-replace writers (`replaceDataSourceAxis`/`replaceExtensionAxis`) exist but are
   UNWIRED (the audit's F6 — a known gap: a source switch merges, potentially mixing two
   sources' fields).
2. **The full-row `upsertContentDetails` runs only on genuinely-missing rows** (verified).
3. **Titles have a validated writer** (D-797): blank/whitespace refused.
4. **Removal deletes ONLY library_item** — identity + progress + tracking survive (D-862);
   `clearByMainId`/`deleteMainEntry`/`deleteContentDetails` are dead code (zero callers).
5. **The scanner writes are skip-if-exists + INSERT OR IGNORE** (D-791 — no rollback of
   user data by a scan).
6. **Backup restore = one transaction, INSERT OR IGNORE, parents before children** — merge-
   only; nothing on the device is overwritten or deleted.

## 4. The watch-tracking model

- `watch_progress` per episode (`episode_key = "<mainId>|<episode%05d>"` — EpisodeKeys.build):
  position/duration/completed + `played_time_ms` (MONOTONIC — the store takes max(existing,
  incoming)) + the CF1 fields (user_marked_watched sticky, auto_mark_suppressed).
- The watch-mark gate: fraction > the user's threshold AND cumulative ≥ 60s.
- `watch_session` per CONTINUOUS STREAK: started_at/ended_at/played_ms — the Profile's
  per-day watch flow, day-sheet, time DNA, and heatmap ALL read this ledger (the day-sheet's
  episode list derives from it directly, D-862).
- The writers: WatchSessionRecorder (the per-caller baseline, the 5-minute gap, the delta
  clamps) wired into BOTH players.

## 5. The test wall

`core/database/src/test/.../SchemaMigrationTest.kt` — 6 tests: the v1→v2 column + data
preservation, the migrated-DB usability, the v3→v4 FK strip, and the v3→v4 survive-deletion
proof (foreign_keys ON → delete main_entry → the rows survive through the typed API).
`core/content/src/test/.../ContentSchemaAndFlowsTest.kt` — 22 tests pinning the axis
semantics. ⚠ These run LOCALLY only (CI builds assembleDebug only) — and local Gradle is
paused by the user's machine boundary: they are pinned but UNRUN since round 144 (disclosed).
