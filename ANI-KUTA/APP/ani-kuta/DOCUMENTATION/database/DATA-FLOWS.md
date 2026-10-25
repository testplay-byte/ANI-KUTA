# Data Flows — which action writes what

> Rounds 126–127. The write-path map: each user action → the tables it
> touches → the code path. Every write here logs itself (D-785, CORE_RULES
> §5) — filter logcat by `Anikuta:` and the DB story of any session reads
> top-to-bottom. Round 127 hardened every flow below: axis writes MERGE
> (D-792), flips validate (D-794), the scanner never clobbers (D-791), and
> the watch-mark gate is cumulative (D-789).

## Opening a content page (AniList-first)

1. **AniList resolve** → `resolveOrCreateForAniList` (ContentResolver):
   `INSERT main_entry` (if new) + the content_details identity row
   (D-787's discriminated minimum) + the data axis via `updateDataSourceAxis`.
2. **Metadata refresh** (the silent stage-2 / pull-to-refresh / the ⋮ Refresh):
   `updateDataSourceAxis` — now a COALESCE MERGE (D-792): a field the refresh
   didn't fetch PRESERVES its stored value; erasing is impossible from this
   path. When the AniList axis is the displayed identity, `main_entry.title`
   syncs (D-769/F5, blank-refused per D-797).
3. **Extension restore/enrich** (a linked source exists):
   `loadLinkedSource` → `extensionBase` from the ext axis (with the extras'
   year/score/title — D-784/RC5); the background enrichment persists
   via `updateExtensionAxis` (a MERGE — the same erase-proofing).

## Linking a source

- **Link an extension source** (`linkSource`): `linkExtensionToExisting`
  (one transaction: `updateMainEntrySources` — main_entry's identity columns
  + a regenerated `content_id` via the shared ecosystem helper (D-796) —
  plus `updateExtensionAxis`) → the background enrichment persists the full
  ext axis (merge, D-784/RC3). When `display_source == 'extension'`,
  `main_entry.title` syncs to the extension identity. In content_details the
  INTEGER `source_id` is derived from the TEXT `extension_id` in the SQL —
  the pair cannot drift (D-795).
- **Link to AniList** (`linkAniListEntry`): `cacheManualLink` + the link pref
  + `linkAniList` → then `switchDataSource(ANILIST)` automatically (D-777/R10).

## Switching the displayed data source

`switchDataSource(priority)`: `remergeBases` (in-memory) →
`updateMainEntryDisplaySource` ('data_source' | 'extension' — VALIDATED since
D-794: the flip is refused, loudly logged, when the target axis is not
linked) → `updateMainEntryTitle` (the title follows the newly-primary base)
→ the target axis auto-refreshes. A whole-axis switch that must overwrite
stale fields uses `replaceDataSourceAxis`/`replaceExtensionAxis` (WARN — the
only axis writes allowed to erase, D-792).

## Unlinking

`unlinkAniList` / `unlinkExtension` (ContentResolver): regenerate content_id
(the shared ecosystem helper — no more hardcoded "aniyomi", D-796), clear the
departed axis (`clearDataSourceAxis`/`clearExtensionAxis`), flip
`display_source` to the survivor (validated since D-794), retitle.

## Playback

- **Watch progress**: `WatchProgressStore.save` every 10s of real playback +
  the pre-switch/exit saves — each gated on reached-playback + the CUMULATIVE
  played total (persisted `played_time_ms` base + this session) ≥60s +
  not-error (D-789). The store records the total monotonic (max of stored vs
  incoming — repeating saves never double-count) and its auto-mark
  (`completed = true`) fires only when the written fraction exceeds
  `watchPreferences.autoMarkThreshold` **AND** the cumulative total reached
  60 s: finishing a previously (partially) watched episode by seeking marks
  it; a fresh failed stream (~2s played, pinned at EOF) never can.
- **Playback cache**: `playback_cache_entry` identity/progress rows.

## Downloads

- **Queue** → `download_queue` rows (order/status/progress mutate per tick).
- **Completion** → `downloaded_episode` row + the `.data.json` sidecar.
- **The folder scan** (every app start) → reconciles `.data.json` ↔ the DB:
  `upsertContentRecord` = `insertMainEntryIfMissing` (INSERT OR IGNORE — an
  existing row is canonical and is NEVER touched; B1/D-791); the data axis
  writes only for a genuinely missing details row; the EXT axis restores from
  the snapshot's own fields when the row has none (B4/D-793 —
  `restoreExtensionAxisFromSnapshot`). A relinked entry can no longer be
  rolled back to its download-time state by a scan.
- **Startup heal** (before the scan): `healCloudstreamEcosystem` (D-540) +
  `reconcileExtensionIds` (B7/D-795 — realigns any legacy row whose
  `source_id` disagrees with the canonical `extension_id`).

## Library

- **Add/remove/category moves** → `library_item` / `library_category`.
- The Library READ joins `main_entry` (title/identity) +
  `content_details` (cover by display_source) + the badge aggregates —
  a reader, not a writer, but the title the Library shows IS
  `main_entry.title` (the single display truth, D-797).

## Tracking & notifications

- **Tracker binding** → `track_entry` / `content_tracking_state`.
- **Notification config** → `notification_config`; **posted** →
  `notification_sent` (the dedupe ledger).

## The logging contract (D-785)

Every repository write emits one line: table + key + payload shape (or
old → new for identity fields). Hot-path writes (progress saves) log at
DEBUG; identity/structural writes (main_entry, full-row replaces, axis
clears) log at INFO or WARN — the FULL-ROW `content_details` replace, the
intentional axis REPLACES (switches), and every REFUSED write (B3/B5) log at
WARN, because those are the lines a future device report will be mined for.
