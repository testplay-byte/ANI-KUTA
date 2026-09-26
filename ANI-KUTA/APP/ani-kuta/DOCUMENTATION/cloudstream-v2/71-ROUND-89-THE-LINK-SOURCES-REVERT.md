# 71 — ROUND 89: THE LINK SOURCES REVERT (D-625)

> Round: 89 · Date: 2026-09-26 · Base: v1.1.45 (round 88) · Ship: v1.1.46/10146
> Input: the user's round-89 session order — NOT a device report this time, a DIRECTION correction: "Currently the debug application is on version 1.1.45, but there are some issues on this version. There are some changes on this version which were made, which are not perfect. What I would like you to do is to revert back versions before it, meaning version 1.1.43… we are not reverting back the whole things. But… a specific thing… the link sources changes. Nothing else needs to be reverted or changed. Nothing in the sense of extension testing needs to be reverted or anything like that." Plus: release v1.1.46 properly so the app updates in-app.

---

## 0. THE SCOPE DECISION (what "the link sources changes" means, exactly)

The diff audit `v1.1.43..HEAD` settled the scope before anything was touched. Everything that
changed between v1.1.43 and v1.1.45 falls into exactly four buckets:

| Bucket | Files | Disposition |
|---|---|---|
| **The Link Sources sheet** | `feature/anime-details/impl/…/ManualSearchSheet.kt` (the ONLY anime-details change: +186/−95) | **REVERTED** — this IS "the link sources changes": D-612 (the ime-aware adaptive cap), D-613 (the two stacked ecosystem section cards), D-614 (the two side-by-side columns) |
| Extension testing (rounds 87-88) | `extensionssettings/testing/**` (15 files), `MainActivity.kt` (+3 — the D-619 live-run pill wiring) | **KEPT** — the user's explicit "Nothing in the sense of extension testing needs to be reverted": the un-killable chain (D-593), TestingPalette (D-594), the payload + link-accumulation work (D-595/D-596), the detail/run/hub/list rework (D-597..D-611, D-615..D-624) |
| Version bookkeeping | `build-logic/…/AndroidConfig.kt` (10143→10120 — the release-tag vs mainline doctrine difference) | **KEPT** — D-430: the mainline carries 1.1.20/10120; the bump rides the release branch. Not a real change; don't "fix" |
| Docs/memory | `AGENT-CONTEXT/**`, `DOCUMENTATION/cloudstream-v2/69+70` | **KEPT** — the ledger records history; history stays |

## 1. D-625 — THE REVERT ITSELF

`ManualSearchSheet.kt` is restored to its **v1.1.43 state, byte-identical** (verified: `git diff
v1.1.43 -- <file>` is empty before the annotation), +11 lines of ONE annotation comment in the
source-list section banner (the user's comment-management order: future edits must be able to
trust the file's story). What came back:

- **The single bounded LazyColumn** (`heightIn(max = 430.dp)`) — the D-587 round-86 "normal
  alphabetical list": the rounded-RECT "Aniyomi" heading + its sources, then "CloudStream" + its
  sources, one scroll, both buckets pre-alphabetized.
- **`SourceSectionHeading`** — the full-width rounded-rect heading (14sp Bold label, count right).
- **`SourceListRow`** — the full-width row: 28dp icons (`WheelSourceIcon`/`WheelIconFallback`
  back at their fixed 28dp — the `size: Dp` parameter is gone), 12sp names (13sp selected), the
  full selected treatment (tint + 1.5dp border + ExtraBold + 18dp check bubble), the linked ✓.
- What left with the revert: `SourceColumnCard`, `SourceColumnRow`, the two hardcoded palette
  accent dots, the adaptive `listMaxHeight` computation, the `LocalDensity`/`Dp` imports, the
  empty-ecosystem notes.

Verification: (a) the file diff vs v1.1.43 = the annotation comment ONLY; (b) a module-wide grep
for `SourceColumnCard|SourceColumnRow|listMaxHeight` = zero matches (all reverted symbols were
`private` to this file — nothing external could reference them); (c) the restored file is the
exact blob that CI built green at tag v1.1.43 (compile safety by identity, not by hope).

## 2. THE KNOWN CONSEQUENCE (disclosed, not sugarcoated)

**The D-612 squish fix rode these same lines.** With the fixed 430dp cap restored, the search
bar can again be squeezed if the keyboard + a full list exceed the screen height — the exact
v1.1.43 behavior the user reported in the v1.1.44 round ("the search bar is squished — there's
no room for it"). This is NOT an oversight: the user ordered the link-sources changes reverted
to v1.1.43, and D-612 was one of those changes (it was built for the new layouts). If the fix is
wanted back on the OLD layout, it is a one-line cap change (`430.dp` → the adaptive
computation) — flagged to the user in the round-89 report; the decision is theirs.

## 3. THE RECOVERY PATH (why the annotation matters)

The D-614 two-column implementation is NOT lost: the complete round-88 file lives at tag
v1.1.45 (commit 05a79f80) and in `release/1.1.45`. If the user ever wants the columns back,
cherry-pick the file's history — never re-implement from scratch. The annotation comment in the
restored file says exactly this, so a future agent reading the code cold cannot miss it.

## 4. CI + THE RELEASE

- **CI (verify the RESET itself — the Task-63/64 lesson):** revert commit `d87b8d02` pushed to
  the mainline → Build APK run 36243004630 → **GREEN** (recorded in the progress ledger).
- **The release (the user's "do the release properly" order, v1.1.46/10146 authorized this
  session):** `release/1.1.46` cut from the green docs head; the bump commit
  (`10120 → 10146`, `1.1.20 → 1.1.46`) rides the branch per D-430; the annotated tag v1.1.46
  carries the full user-facing bullet body (D-466 — the honest what-you'll-see list for a revert
  release); Release APK run → green → the stable-latest release publishes with the arm64-v8a
  debug APK + SHA256SUMS.txt → the in-app updater (debug builds check this repo, D-440) picks
  it up. Run id + artifact facts recorded in the progress ledger.

## 5. Open items for the next device round

- The squish behavior (see §2): if the search bar clips with the keyboard open, the one-line
  D-612-style cap fix is ready to re-apply on the old layout — user's call.
- The dashboard's data did not change this round (no status-level facts moved besides the
  release line) — no dashboard debt added.
- The user signaled the NEXT round will carry workflow/process instructions ("I'll give you some
  more instructions on how to work in the codebase properly") — the round-90 input is expected
  to be that process order, not a device report.
