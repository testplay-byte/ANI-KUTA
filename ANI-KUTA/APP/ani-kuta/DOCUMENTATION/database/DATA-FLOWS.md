# Data Flows — which action writes what

> Round 126. The write-path map: each user action → the tables it touches →
> the code path. Every write here logs itself (D-785, CORE_RULES §5) — filter
> logcat by `Anikuta:` and the DB story of any session reads top-to-bottom.

## Opening a content page (AniList-first)

1. **AniList resolve** → `resolveOrCreateForAniList` (ContentResolver):
   `INSERT main_entry` (if new) + `INSERT OR REPLACE content_details`
   (data axis via `updateDataSourceAxis`).
2. **Metadata refresh** (the silent stage-2 / pull-to-refresh / the ⋮ Refresh):
   `updateDataSourceAxis` — null-merged against the stored row (D-248).
   When the AniList axis is the displayed identity, `main_entry.title` syncs
   (D-769/F5).
3. **Extension restore/enrich** (a linked source exists):
   `loadLinkedSource` → `extensionBase` from the ext axis (with the extras'
   year/score/title — D-784/RC5); the background enrichment fetch persists
   via `updateExtensionAxis` (null-merged).

## Linking a source

- **Link an extension source** (`linkSource`): `linkExtensionToExisting`
  (one transaction: `updateMainEntrySources` — main_entry's identity columns
  + a regenerated `content_id` — plus `updateExtensionAxis`) → the
  background enrichment persists the full ext axis (null-merged, D-784/RC3).
  When `display_source == 'extension'`, `main_entry.title` syncs to the
  extension identity.
- **Link to AniList** (`linkAniListEntry`): `cacheManualLink` + the link pref
  + `linkAniList` → then `switchDataSource(ANILIST)` automatically (D-777/R10).

## Switching the displayed data source

`switchDataSource(priority)`: `remergeBases` (in-memory) →
`updateMainEntryDisplaySource` ('data_source' | 'extension') →
`updateMainEntryTitle` (the title follows the newly-primary base) →
the target axis auto-refreshes.

## Unlinking

`unlinkAniList` / `unlinkExtension` (ContentResolver): regenerate content_id,
clear the departed axis (`clearDataSourceAxis`/`clearExtensionAxis`), flip
`display_source` to the survivor, retitle. Known trap: the flips don't
validate the surviving axis (flaw B3).

## Playback

- **Watch progress**: `WatchProgressStore.save` every 10s of real playback +
  the pre-switch/exit saves — each gated on reached-playback + ≥60s of
  actual played time + not-error (D-785's R1 spec: a watch-mark needs the
  user's fraction threshold AND ≥1 minute played). The store's auto-mark
  (`completed = true`) fires when the written fraction exceeds
  `watchPreferences.autoMarkThreshold`.
- **Playback cache**: `playback_cache_entry` identity/progress rows.

## Downloads

- **Queue** → `download_queue` rows (order/status/progress mutate per tick).
- **Completion** → `downloaded_episode` row + the `.data.json` sidecar.
- **The folder scan** (every app start) → reconciles `.data.json` ↔ the DB
  (`upsertContentRecord` = `INSERT OR REPLACE main_entry` + the data axis
  when the snapshot has an AniList id). KNOWN FLAW: the REPLACE can roll
  main_entry's identity/display_source back to the snapshot (B1) — the
  scanner must become insert-if-missing (the decision is pending).

## Library

- **Add/remove/category moves** → `library_item` / `library_category`.
- The Library READ joins `main_entry` (title/identity) +
  `content_details` (cover by display_source) + the badge aggregates —
  a reader, not a writer, but the title the Library shows IS
  `main_entry.title`.

## Tracking & notifications

- **Tracker binding** → `track_entry` / `content_tracking_state`.
- **Notification config** → `notification_config`; **posted** →
  `notification_sent` (the dedupe ledger).

## The logging contract (D-785)

Every repository write emits one line: table + key + payload shape (or
old → new for identity fields). Hot-path writes (progress saves) log at
DEBUG; identity/structural writes (main_entry, full-row replaces, axis
clears) log at INFO or WARN (the FULL-ROW `content_details` replace logs at
WARN — it is the one call that can erase both axes; flaw B2).
