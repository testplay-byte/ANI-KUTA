# Known Database Flaws — the register (decisions pending)

> Round 126. The user's order: surface anything that would bite us later,
> clearly, and clarify NOW. The full evidence lives in the workspace report
> (`planning/2026-10-25-database-flaws-report.html`); this is the repo-side
> register. **Nothing here changes the schema without the user's §30/Q2
> signal.** The lock tests in `:core:content`'s test source set pin the
> CURRENT behaviors (including the fragile ones) so any future change is a
> conscious, test-visible decision.

| # | Flaw | Can bite when | The fix direction | Decision needed |
|---|---|---|---|---|
| B1 | The download scanner `INSERT OR REPLACE`s `main_entry` from the `.data.json` snapshot on every scan; its reconciliation then reads the rolled-back row | A relink/switch/title change made after the last scan silently reverts on the next scan (mixed-state row) | Scanner becomes insert-if-missing, or reconciles against the PRE-upsert state | **Yes — approve the scanner change** (pure code, no schema) |
| B2 | The axis UPDATEs set every column including nulls | One forgetful writer erases stored metadata (the last one was fixed in round 125; the class remains) | `SET col = COALESCE(:col, col)` in the .sq — storage-level erase-proofing | **Yes — touches .sq (your §30/Q2 signal)** |
| B3 | `display_source` flips don't validate the surviving axis | A row claims to display an empty axis | Validate `hasDataSourceLink`/`hasExtensionLink` before persisting a flip | Yes — small code change |
| B4 | Restored extension-only downloads have no ext axis (the snapshot format doesn't carry it) | After reinstall: bare title, no cover, blank extension details | Derive a minimal ext axis on scan, or extend the snapshot format | Yes — the format change is your call |
| B5 | Three title sources (`main_entry.title`, `ExtensionExtras.title`, the navigation title) can drift | The displayed name can vary per session | `main_entry.title` becomes the single display truth; the extras title is a validated cache | Yes |
| B6 | `content_id` regeneration hardcodes "aniyomi" in one path | A CloudStream-bridged row's id churns shapes across link/unlink cycles | One shared ecosystem-derivation helper | Low priority |
| B7 | The same extension id stored twice in two types (`extension_id` TEXT, `source_id` INTEGER) | None today (writers agree by convention) | One canonical column, the other derived | Schema change — defer |

## The status

- **Fixed already** (rounds 123–125): the raw-write wipes at five ext-axis
  writers (null-merge everywhere), the raw-int status, the restore's dropped
  year/score, the library-title sync, the reopen-enrichment persistence.
- **The lock tests** (`ContentSchemaAndFlowsTest`) pin: axis isolation, the
  link/unlink/title-sync semantics, the display-source persistence, and the
  B2 raw-null behavior (documented, so the COALESCE decision lands as a
  visible, test-updated change).
