# Known Database Flaws — the register (RESOLVED round 127)

> Rounds 125–127. Round 125 surfaced the register; round 126 built the docs,
> logging, and lock tests; **round 127 fixed every registered flaw** (the
> user's order: "all of those need to be well planned for, well handled, well
> managed and need to be fixed properly"). The full evidence lives in the
> workspace report (`planning/2026-10-25-database-flaws-report.html`). The
> lock tests in `:core:content` (ContentSchemaAndFlowsTest), `:core:database`
> (SchemaMigrationTest) and `:core:watch-progress`
> (SqlDelightWatchProgressStoreTest) pin every fixed behavior — a regression
> is a red build, not a silent wipe.

| # | Flaw | The fix (round 127) | Decision note |
|---|---|---|---|
| B1 | The download scanner `INSERT OR REPLACE`d `main_entry` from the `.data.json` snapshot (check-then-insert race; stale re-commits with fresh timestamps) | `insertMainEntryIfMissing` = INSERT OR IGNORE (race closed, D-791); existing rows are NEVER touched by the scan — the data-axis write now runs only for a genuinely missing row; the ext axis restores via `restoreExtensionAxisFromSnapshot` only when empty | Pure code; the DB stays canonical per D-242 |
| B2 | The axis UPDATEs set every column including nulls — one forgetful writer erased stored metadata | COALESCE merges at the storage level (D-792): `updateDataSourceAxis`/`updateExtensionAxis` preserve null-covered fields; the intentional switches use `replaceDataSourceAxis`/`replaceExtensionAxis` (WARN-logged). This is also the structural answer to "refresh replaces good data with a parsed stub" | The user lifted the §30 gate; the round-126 lock test was flipped in the same commit |
| B3 | `display_source` flips didn't validate the surviving axis | `updateMainEntryDisplaySource` refuses a flip to an axis with no link (and refuses non-axis values), loudly logged (D-794) | Small code change as planned |
| B4 | Restored extension-only downloads had no ext axis | The scanner derives a minimal ext axis from the snapshot's own fields (`restoreExtensionAxisFromSnapshot`, D-793) — no `.data.json` format change needed | The user was offered the format change; derive-on-scan won (zero migration risk) |
| B5 | Three title sources could drift | `main_entry.title` is the single display truth with a validated writer (D-797): blank/whitespace candidates are refused; rounds 124–125's writer sync + reader precedence are pinned by tests | — |
| B6 | `content_id` regeneration hardcoded "aniyomi" in two paths | One shared helper `ecosystemNameForEntry` (D-796): bridged id → cloudstream, else the row's real system name, else null (legacy id shape preserved) | — |
| B7 | The same extension id stored twice in two types (`extension_id` TEXT, `source_id` INTEGER) could drift | The INTEGER twin is DERIVED in the ext-axis SQL (`NULLIF(CAST(extension_id AS INTEGER), 0)`) + a startup `reconcileExtensionIds` realigns legacy rows (D-795) | Both columns kept (hot-path reads + the main_entry denormalization); physical unification can be a later `.sqm` if ever needed |

## Also fixed in the same wave

- **The cumulative watch-mark** (the round-126 device report's one flaw, the
  user's spec): `watch_progress.played_time_ms` (the first `.sqm` migration,
  D-789) — watched = fraction threshold **AND** ≥1 minute actually played,
  TOTALLED over sessions. A seek-to-end after having genuinely watched marks
  the episode; a fresh failed stream never can. Both players accumulate (the
  MPV holder + the CS ticker).
- **The lock tests caught two latent repo defects** during the round's
  full-suite run: `PlaybackResumePolicyTest`'s stale expectation (round 121,
  never ran) and `:core:video-resolver`'s test source set missing its junit
  dependency. Both fixed.

## The standing rule

The schema is the user's design — changes arrive through `.sqm` migrations
with a data-preservation test, never silently. Every write logs itself. Every
fragile behavior has a lock test.
