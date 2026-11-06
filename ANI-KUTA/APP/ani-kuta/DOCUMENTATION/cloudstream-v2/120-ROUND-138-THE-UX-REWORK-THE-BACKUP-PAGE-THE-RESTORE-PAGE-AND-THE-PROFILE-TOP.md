# Round 138 — The UX Rework Round: The Backup Page, The Restore Page, And The Profile Top

**Date:** 2026-11-06 · **Commits:** 779f672a (code) + the release line · **CI:** Build APK 42264720611 + Release APK 42264832072 (see the ledger at the bottom)

The v1.1.81 device report approved the round-137 interop outright — "restored a backup from another
system… properly", CloudStream restore "handled… beautifully" — and returned three UI orders plus a
release order:

## 1. The backup page — one format, one chip (D-833)
The report: "remove this option. It should only backup in our own format… on the backup button at the
very bottom right, there will be a section or a button… a one-time thing."
- The persistent **Export format card is REMOVED** (`BackupPreferences.exportFormat` deleted with it) —
  the Backup tile always writes the native `.anikuta` archive and says so.
- A small **"Export as…" chip** floats at the tile's BOTTOM-RIGHT (`BentoTile` gained an optional
  `trailing` slot in a BottomEnd Box — a child clickable consumes its own tap, so the tile's onClick
  never double-fires). Tapping it opens a compact picker listing ONLY the four foreign formats
  (`INTEROP_TARGETS`), each row closing the popup and running exactly ONE export. Nothing persists —
  the next Backup is native again.
- **Half-line descriptions** (the report: "at most half a line"): `InteropFormat` descriptions rewritten
  to ".tachibk — library + progress" shape; the popup renders name + description on one line.

## 2. The restore page — rebuilt (D-834)
The report: "the UI of that page is very bad… way too ugly… not modern, clean."
- **ONE theme-tinted hero** at the very top — the whole section IS the file picker (primary@0.12
  surface, primary@0.3 border, 20dp corners, an icon chip, "Pick a backup file"/"Pick another file",
  and a one-line subtitle).
- **ONE picker for every format**: the picked file is read once — a `.anikuta` manifest shows the
  details card + the confirm dialog (the heavy extension installs stay guarded); ANY other file runs
  the interop restore immediately (additive-only — safe to just do).
- **The supported-format chips** below: ANI-KUTA .anikuta / Aniyomi .tachibk / Anikku .anikku / AniList
  .json / CloudStream .json, plus the "everything is ADDED — nothing overwritten; Mihon manga comes
  later" note.
- The old two-picker structure (the separate "Restore from other apps" card) is gone; the busy flag is
  wrapped in `try/finally` so it can NEVER stick; re-picking the same document re-runs (the generation
  counter). The old page's helper set (statOr, the toast formatters, DetailRow/SectionLabel) survives.

## 3. The profile top — the deep dive (D-835)
The report: "the section which is at the very top when I haven't scrolled is way too bad, but as soon
as I scroll slightly, then it moves up to the very top where it needs to be."
**Root cause:** the at-rest state and the settled state were DIFFERENT LAYOUTS — the round-135
in-list tab band (item 0) with its collapse-in-layout modifier, the measured threshold, and the
magnetic snap made "rest" a half-state the report read as glitchy; the settled state (band gone,
avatar under the header) was the good one.
**Fix — remove the difference:** the band + `collapsingTabBand` + the snap effect are DELETED. The
Stats/Timeline pill lives PERMANENTLY in the pinned header (equal-width segments, left of the gear);
the avatar row is list item 0 in BOTH tabs; `collapsed` uses the app's standard signal; the scrim keeps
a simple offset/140 driver; the title is PINNED at the settled 24sp (with the pill sharing the row, a
larger expanded size truncated "My Profile" — the D-559 width-pressure class). There is no half-state
left: at rest == the settled state the report called "where it needs to be".

## 4. Verification + CI
Self-verified (the agent launcher was interrupted by the environment twice — the pass ran inline):
dangling-reference sweep (exportFormat/pref, the old restore pickers, the band symbols — all clean),
the width-pressure fix, the busy-state `try/finally`, the tap-propagation reasoning, INTEROP_TARGETS
excluding ANIKUTA, and two compile fixes (a forward-referenced local fun, an unresolved lambda label →
if/else). Local assembleDebug BUILD SUCCESSFUL before any CI run. CI ledger: Build APK 42264720611
(the round commit) + Release APK 42264832072 — 2/2, within D-472.

## 5. The release (D-836)
The user's explicit order ("you can do the release, debug release of it too"). Routine A on 779f672a:
release/1.1.82, ONE bump commit (10120/1.1.20 → 10182/1.1.82, a89c187c), branch + annotated tag
v1.1.82 (the user-facing bullet body) → **Release APK run 42264832072 — GREEN**. Verified live:
stable-latest "ANI-KUTA v1.1.82 (round 138)", ani-kuta-v1.1.82-debug-arm64-v8a.apk (64,000,949
bytes), the downloaded SHA256 (6f0e5eba24f12ee01b19571c828e8e176b2debd71a2badd42f2e77898a1aab17)
matches SHA256SUMS.txt. v1.1.81 installs get the in-app offer carrying round 138.

## Documented observations (for the next full report round)
- The header row's width budget is tight on <360dp screens (the pill + gear + title); ellipsis guards it.
- `SegmentedToggle` (the settings' shared widget) is now unused by the profile — candidate for reuse
  audits elsewhere.
- The interop restore runs without a confirm dialog by design (additive-only); if the user ever wants
  a preview for foreign formats too, the manifest-style detail card would need per-format summary
  support in InteropManager (parse-only mode).
- `WatchSessionRecorder`'s per-caller baseline state survives process death invisibly (factory-scoped,
  in-memory) — worst case one lost delta per process start; acceptable.
