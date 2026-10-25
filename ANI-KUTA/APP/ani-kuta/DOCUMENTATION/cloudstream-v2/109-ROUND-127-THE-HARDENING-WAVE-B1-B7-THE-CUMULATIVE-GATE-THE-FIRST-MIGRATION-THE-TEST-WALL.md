# 109 · ROUND 127 — THE HARDENING WAVE: B1–B7 FIXED, THE CUMULATIVE PLAYED-TIME GATE, THE FIRST MIGRATION, THE TEST WALL

> 2026-10-25. The user's round-126 report confirmed every round-126 fix
> passing (failed ≠ watched; ≥1 min of real playback marks; audio-version
> resume) and ordered the full database hardening: **"all of those [B1–B7]
> need to be well planned for, well handled, well managed and need to be
> fixed properly"**, the five display root causes handled properly, schema
> updates must **convert old databases in place**, and — for the first time —
> **the user will NOT device-test this round** ("I am confident in your
> capabilities... you would have did the logic testing"). The tests are the
> device round. Decisions D-789..D-798.

## 1. The device report that drove the round

- ✅ Failed episode → no card, never marked watched (round 126 holds).
- ✅ Under 1 min → not watched; ~1:20 of real playback → watched + progress.
- ✅ Same server, different audio version → resumes from the saved mark.
- ❌ **The one flaw**: played to ~90%, then let it run 5s, back out → NOT
  marked watched. The user's spec, verbatim: *"the total duration of the
  episode watched should be more than one minute. Not the one session one,
  but the overall... previously I watched it for more than one minute. So
  now if I open it up and mark it as watched, like completed just by
  seeking or such, then it should most definitely be marked as watched."*

## 2. D-789 — the cumulative played-time gate (the schema change)

Round 126's 60s gate was per-session (the accumulator died with the
composition). The fix makes the minute a PERSISTED, CUMULATIVE total:

- **Schema**: `watch_progress.played_time_ms INTEGER NOT NULL DEFAULT 0` —
  introduced through **the project's first versioned migration** (`1.sqm`,
  schema v1 → v2; every existing install upgrades via
  `AndroidSqliteDriver.Callback` → `Schema.migrate`; the on-open probes stay
  as a guarded safety net). `SchemaMigrationTest` (core/database, NEW) runs
  the REAL migration on a faithful v1 database and asserts the column lands
  AND the data survives AND the migrated DB is fully usable.
- **Model/store**: `WatchProgress.playedTimeMs` (callers pass the total they
  know: persisted base + session); the store records it MONOTONIC
  (`max(stored, incoming)` — the 10s repeating saves are idempotent, never
  double-counting) and its auto-mark now requires **fraction > threshold AND
  cumulative ≥ 60s** (`WatchProgress.MIN_PLAYED_FOR_WATCHED_MS`, one constant
  for every gate).
- **MPV (WatchScreen)**: `persistedPlayedBaseMs` captured per episode at the
  resume read; reset on episode switch; all three gates (periodic, EOF, exit)
  and all four save payloads carry the cumulative total. Log lines:
  `PeriodicSave eval … cumulativePlayedMs=…`, `EOF hook: … cumulativePlayedMs=…`.
- **CloudStream (CsWatchViewModel)**: its own session accumulator (10s ticker
  deltas, 1..60s forward = playback; seeks excluded), reset per episode;
  `saveProgress` passes persisted + session — the CS player accumulates too
  (without this, CS-only viewers could never cross the floor).
- **Preferences seam**: `WatchPreferences` became an interface +
  `DefaultWatchPreferences` (the store is JVM-testable; behavior unchanged).
- The exact device scenarios replay as 9 lock tests in
  `SqlDelightWatchProgressStoreTest` (core/watch-progress, NEW): the fresh
  failure never marks; <60s never marks even at 100%; ≥60s at >85% marks;
  **the previously-watched episode completing by seek later DOES mark**; no
  double-count; a non-tracking caller can't lower the total; suppression and
  the sticky mark behave.

## 3. D-790..D-795 — B1–B5 and B7, the registered flaws, resolved

- **B1/D-791 (the scanner rollback)**: `insertMainEntryIfMissing` (INSERT OR
  IGNORE — the check-then-insert race is closed; a concurrent resolver's row
  can no longer be clobbered wholesale). The data-axis write now happens ONLY
  for a genuinely missing details row — the old path re-committed the row's
  own values with a fresh `data_updated_at` every scan. An existing row is
  untouchable by the scan, period.
- **B2/D-792 (the axis erasure class)**: `updateDataSourceAxis` /
  `updateExtensionAxis` are **COALESCE MERGES** in the `.sq` — a null field
  preserves the stored value; the "refresh replaces good data with a parsed
  stub" wipe is impossible at the storage layer. The intentional switches use
  the new `replaceDataSourceAxis` / `replaceExtensionAxis` (WARN-logged — the
  only axis writes allowed to erase). The round-126 B2 lock test was flipped
  in the same commit.
- **B3/D-794 (display_source validation)**: `updateMainEntryDisplaySource`
  refuses a flip to an axis with no link (and non-axis values), loudly
  logged. A row can no longer claim to display emptiness.
- **B4/D-793 (restored extension-only downloads had no ext axis)**: the
  scanner's `restoreExtensionAxisFromSnapshot` derives the minimal ext axis
  from the snapshot's own fields (systemId → system name → extension_type;
  extension_id; anime_url; cover). Guarded to the empty-axis case so a
  relinked entry can't be dragged back. **No `.data.json` format change**
  (the offered option; derive-on-scan won — zero migration risk).
- **B5/D-797 (title unification)**: `main_entry.title` is the single display
  truth with a VALIDATED writer — blank/whitespace candidates are refused
  (the URL-slug junk class); rounds 124–125's writer sync + reader precedence
  are pinned by tests.
- **B7/D-795 (the twin id columns)**: in content_details the TEXT
  `extension_id` is canonical — the INTEGER `source_id` is DERIVED in both
  ext-axis statements (`NULLIF(CAST(extension_id AS INTEGER), 0)`), and a
  startup `reconcileExtensionIds` realigns legacy drift. Both columns kept
  (hot-path reads); physical unification can be a later `.sqm` if ever needed.

## 4. D-796 — B6, the shared ecosystem helper

`ecosystemNameForEntry(repo, entry)`: bridged id → "cloudstream"; else the
row's real system name via `getSystemById`; else null (legacy id shape
preserved). The two content_id-regeneration paths that hardcoded "aniyomi"
now call it — a Sora/MangaYomi row's id keeps its own ecosystem shape across
link/unlink cycles.

## 5. The test wall (the user's standing order, this round literally)

- **219 tests, 0 failures** across all 10 test-bearing modules; the full app
  compiles. New suites: `SchemaMigrationTest` (4), `SqlDelightWatchProgressStoreTest`
  (9), + 12 new/flipped locks in `ContentSchemaAndFlowsTest` (22 total).
- The full-suite run exposed **two latent repo defects** (both fixed):
  `PlaybackResumePolicyTest`'s stale round-121 expectation (the test never
  ran until now — CI builds only), and `:core:video-resolver`'s test source
  set missing its junit dependency.

## 6. The user's question, answered in code

"if you have any concerns... ask me" — three decisions were taken without a
blocking question (each documented in FLAWS.md and D-789..D-798): **B4** =
derive-on-scan (no snapshot format change); **B7** = derive + reconcile
(columns kept); **B1** = the scan never touches existing rows (the
`.data.json` timestamp-rolling in duplicate arbitration was NOT changed —
noted for a future round). Everything else was the registered fix direction,
unchanged.

## 7. CI ledger (D-472)

Commit `«HEAD»` → Build APK run **«RUN_ID» GREEN first-try** (the
`anikuta-apk` artifact, versionCode 10120; the mainline stays 1.1.20/10120).
ONE run — inside the D-472 budget. The test suites ride `test`/`check` per
the doc 102 §7 disclosure; the pre-push verification ran ALL of them locally.
