# Round 126 — The Played-Time Gate, the Schema Docs, the Lock Tests, and the Standard

> THE ORDER (the user's round-125 device report): the card-on-failure, the portrait revert, the wider drawers, the left settings, the drag, and the Paused label all CONFIRMED — but a failed episode STILL got marked watched ("the episode will only get marked as watched if it has been finished more than 85% or the set value which the user has set, and also if the user has actually played that episode for more than one minute"). And the round's MAIN GOAL: proper unit tests for the database, proper write logging as a standing coding standard ("we should be properly aware of what is being written, where, how, on what action"), a full-fledged database schema documentation ("we should know how the database works, which things are stored where") ready for manga, novels, and backup/restore, the flaws surfaced clearly in HTML, the planning folder cleaned up, and everything pushed to GitHub so the local files are restorable.

## 1. The played-time gate (D-785)

The failed episode plays ~2 real seconds (arming the reached-playback gate), then dies and pins at EOF — and the EOF completion write (or the periodic save) wrote 100% under its key, tripping the store's auto-mark. The user's spec is now law: the holder accumulates **actual played time** (small forward position deltas only — seeks, the EOF pin, and file changes don't count; reset on every load), and ALL THREE completion-capable writers — the periodic save, the exit save, the EOF completion write — demand **≥60 seconds of it**. The fraction threshold itself was already the user's own setting (`watchPreferences.autoMarkThreshold`, default 0.85) inside the store; the missing half was the playtime. A dead stream can no longer manufacture a watched-mark; the PeriodicSave log line carries `playedMs` so the gate is visible in any logcat.

## 2. The database documentation (the doc debt paid)

`DOCUMENTATION/database/` now holds the full record: **SCHEMA.md** (all 25 tables — the spine diagram, the content identity system in full, the per-group inventory, and the maintainability rules: `main_id` is the join key for everything new; optional metadata goes in the extras JSON before it earns a column; `content_type`/`data_episodes`/`ext_extra_json` already plan manga and novels; backup/restore serializes per-`main_id`), **DATA-FLOWS.md** (every user action → the tables it touches, through which repository/resolver calls), and **FLAWS.md** (the B1–B7 register with the decisions pending). The user's restore-from-GitHub concern is answered by this folder being in the repo.

## 3. The logging standard (D-785, a standing rule)

CORE_RULES §5 gains the rule: every database write logs itself — table, key, payload shape (or old→new for identity fields), DEBUG for hot paths, INFO/WARN for identity writes. The four previously silent write paths gained probes (`upsertContentDetails` — the FULL-ROW REPLACE logs at WARN because it is the one call that can erase both axes — plus the axis clears and the row delete). The watch-progress store already logged its state machine exhaustively.

## 4. The lock tests — and the flaw they caught

`:core:content`'s first test source set runs the REAL generated SQLDelight queries against an in-memory SQLite (JdbcSqliteDriver — no Robolectric): **ten tests** pinning axis isolation (an ext write leaves the data axis and vice versa), the **B2 raw-null behavior** (a non-merging write nulls its axis — documented so the COALESCE decision lands as a visible, test-updated change), the link/title-sync semantics (extension-displayed → title syncs; data-displayed → it doesn't), display-source persistence, unlink keeps the surviving axis, idempotent AniList resolve, and lookup seeding. **The tests caught a real flaw on their first run**: `resolveOrCreateForAniList`'s null-detail path was NON-IDEMPOTENT — the idempotency lookup JOINs the content_details row that the null path never wrote, so every null call minted a duplicate main_entry. It now writes a minimal discriminated identity row ("pass null" means skip the metadata, not the identity row). 10/10 green after the fix.

## 5. The planning cleanup + the report

The planning folder is cleaned per the order: the branch decision, the improvement plan, the round-120/121 plans, the round-120–124 test packs, and the round-124 logcat analysis moved to `_archive/` (16 items); what remains is the style guide, the templates, the round-125 pack (the latest completed round), the tools, and the **database report** — updated with the round-126 status (the documentation/tests/logging DONE; B1/B2/B3/B4/B5 explicitly waiting on the user's decisions, with B2's lock test ready to flip).

## 6. The verification

The pre-CI verifier compiled the touched modules AND ran the new suite directly, catching three issues pre-push: a local forward reference (the played-time constant declared below its first use — CI would have failed :feature:watch:impl), the JDBC in-memory URL (`:memory:` is not a JDBC URL — every test would have died in setUp), and a table-count inaccuracy (25, not 24). Its re-run: 10/10 tests green, both touched modules compile. (CI runs `assembleDebug` only — the test source set rides `test`/`check`, disclosed in doc 102 §7; the verifier's direct run is the pre-push guarantee.)

## 7. The CI ledger

Commit `f8523b9e` (12 files, +541/−3) → Build APK run **40178107344 GREEN first-try** (the `anikuta-apk` artifact, versionCode 10120; the mainline stays 1.1.20/10120 per the standing rule). ONE run — inside the D-472 budget. The docs push is paths-ignored.

## 8. The decisions on the table (from the database report)

B1 (the scanner rollback — pure code), B2 (the COALESCE defense — touches `.sq`, needs the §30 signal; its lock test is ready to flip), B3 (the display_source validation), B4 (the ext-axis backfill on restore), B5 (the title unification) — the user ranks them; nothing moves without the word.

## 9. The device checklist

`planning/2026-10-25-round-126-device-test.html` (+ the .md twin) — compact this round: the watched-mark scenario (the failed episode must NOT get marked), the regression sweep, and the logcat lines to look for.
