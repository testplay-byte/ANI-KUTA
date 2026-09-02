# 18 — DEVICE ROUND 27 (Task 67): THE FIVE FINDINGS OF THE v0.4.14 ROUND

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: v0.4.14/79
> (round 26 shipped, CI green 33670414361 + 33671258821, Release live).
> The round-26 device round CONFIRMED the delete button now grows (the
> direction is right), the delete button's function, the dedicated CS
> browse loader's search reliability ("I can give it a pass"), and the
> update-check history's general correctness. This round fixes the five
> findings — root-caused, each item its own commit, CI-verified.

## The device report (v0.4.14)

1. **Delete button rendering**: the armed glyph grows, but its TOP,
   LEFT, and RIGHT get CUT OFF and its RESOLUTION degrades badly; 3x
   was too big anyway — re-specced to **2.5x**. ("Handle it like
   that.")
2. **Delete animation residue**: the deleted episode's exit animation
   plays properly, but the episode row that MOVES UP renders with its
   content gone — the card has to be collapsed + re-expanded to see
   it again.
3. **Files not deleted AT ALL**: after deleting the very last episode
   of a series, the files are still on disk — "this time it didn't
   even delete the actual files themselves either." The round-26
   regression: must be handled "much better and much more properly and
   reliably."
4. **Search page options reveal**: after scrolling the CS search
   results, scrolling back to the very top did NOT bring back the
   options row (filter options + the list/extension selector); the
   reveal animation never played. (The CS browse loader itself
   passed.)
5. **Update-check history detail**: it works and shows the results,
   but the user wants per-series depth — "what it calculated, what it
   landed, the delay, and other stuff like that for that specific
   series properly… so that I know that it is working properly."

## Item 1 — the delete button grows IN THE LAYOUT (D-397)

**Root cause:** the round-26 grow was a **draw-phase**
`Modifier.scale(3f)` on a 16dp glyph inside a fixed 32dp `IconButton`.
Draw-phase scaling paints OUTSIDE the layout box, so (a) the card's
rounded `Surface` (which clips to its shape) cut the 48dp glyph's
top/left/right, and (b) the rasterized layer stretched at draw time —
the reported resolution loss. And 3x overshot the user's intent.

**Fix (per the 2.5x re-spec):** grow in the **LAYOUT phase** —
`animateDpAsState` animates the glyph's measured size (16dp → 40dp per
episode row, 20dp → 50dp delete-all header — exactly 2.5x) and the
CALLER animates the `IconButton` frame with it (32dp → 48dp, 36dp →
56dp). Real measured size means the row/header cede the width (nothing
can clip), and the VECTOR path re-rasters at its final size — crisp at
any scale. The `AnimatedContent` glyph morph (Delete ↔ DeleteForever)
is unchanged; its subtle 0.6x→1x scale is cosmetic only. The text
column (weight 1f) re-ellipsizes by ~16dp during the 220ms grow — a
smooth, deliberate-looking reflow.

## Item 2 — the moving row keeps its content (D-397b)

**Root cause:** the episode rows render via `forEachIndexed` inside a
plain `Column` — Compose's `remember` slots in that structure are
**POSITIONAL**. The deleted row's exit choreography had already
animated its `rowAlpha` Animatable to 0 and `rowOffsetX` to ~1200px;
when the list shrank, the row that moved UP into the vacated POSITION
inherited those slots — it rendered fully transparent + translated
off-screen ("its content disappears") until the card was collapsed +
re-expanded (which discards + recreates the composition).

**Fix:** wrap each row in `key(task.episode.episodeKey)`. The slots
now follow the EPISODE identity: the deleted key's slots (with their
dead animation state) are discarded, and every surviving row keeps its
own alpha=1 state. The row/`AnimatedVisibility` structure is otherwise
byte-identical.

## Item 3 — the disk-truth file deletion (D-393)

**Root cause:** the round-26 flow deleted episode files ONLY through
the `.data.json` entry's recorded URIs
(`DocumentsContract.deleteDocument`). That single dependency has three
silent failure modes, any ONE of which leaves the files on disk while
the DB row still dies (the app shows the episode deleted): a stale
`.data.json` (the round-25 sync bug's residue — the entry lookup finds
a mismatched key or nothing), a null `videoUri`, or a URI delete that
returns false / throws. No disk verification existed anywhere. (The
series-folder cleanup had the mirror-image flaw: it keyed on the
`.data.json` re-read, so GHOST entries blocked the last-episode folder
delete.)

**Fix — Phase 2 now has TWO independent paths + the decision is
DB-driven:**
- **2a** — the URI deletes (unchanged, best-effort);
- **2b (new)** — `DownloadStorageProvider.deleteEpisodeFilesOnDisk()`:
  a **census → delete → verify** sweep of `episodes/` + `subtitles/`
  (+ legacy root-level files), keyed on the episode number captured
  FROM THE DB ROW before anything else mutates state. Matching is by
  the canonical FILENAME token (regex ` - E(\d{5}(\.\d+)?)\.[^.]+$` /
  `^subtitle_E(\d{5}(\.\d+)?)_`, full-token comparison so EP 1 never
  matches an `E00001.5` file). Verification re-lists the folders; any
  survivor triggers retry rounds (settle pauses — some SAF providers
  need a beat between sibling deletions) and lands in a
  `DiskSweepReport` (matched / videosDeleted / subtitlesDeleted /
  survivors) with per-file log lines. `survivors.isEmpty()` is the
  on-disk GUARANTEE, independent of any recorded state.
- **Phase 4** — `maybeDeleteSeriesFolder` now decides by the DB alone:
  `dbRemaining == 0` (this row in flight) is exactly the user's "that
  was the last episode" — immune to `.data.json` ghosts (which blocked
  the round-26 delete) AND safe in reverse (a `.data.json` that says
  zero while live DB rows exist can never nuke playable files). On a
  failed folder delete it checks `countEpisodeVideoFiles()` before
  sweeping the rows — the sweep only runs when nothing playable
  remains, and an ERROR log names any survivor.
- The delete-all path inherits all of this: the whole-folder delete
  first, then the fallback per-episode loop where the LAST row's
  Phase-4 (dbRemaining hits 0) folds the folder delete in naturally.
- The lying log line ("file + DB row deletion will still proceed"
  when the folder isn't found — files were NOT deleted in that branch)
  now states the orphan reality at ERROR level.

## Item 4 — the search top-bar reveal latch (D-395)

**Root causes (two, compounding):**
1. **Stale cross-mode signal** — `collapsed` OR-ed the offsets of ALL
   THREE scroll states (the Idle column's `ScrollState`, the results
   grid, the browse list) regardless of which mode was active. The
   Idle `ScrollState` object SURVIVES mode switches (remembered at the
   screen level), so a recents column scrolled away once latched the
   bar collapsed in EVERY later mode — scrolling the browse list back
   to the very top could never un-latch it.
2. **Top-only reveal** — even in a single mode, the bar re-expanded
   only at the literal top (offset ≤ 20px); an upward gesture that
   stopped mid-list left the options hidden ("the animation did not
   play").

**Fix:** ONE `NestedScrollConnection` on the content Box (below the
pull-to-refresh) implements the standard app-bar latch: any downward
delta past an 8px threshold COLLAPSES, any upward delta REVEALS
(horizontal rows produce dy≈0 and never trip it; a pull-to-refresh
drag at the top reveals too). `LaunchedEffect(uiState::class)` re-reveals
on every content-mode transition — fresh content means a fresh context,
and the filter/source row is the entry point to that content. The old
derived expression is gone entirely; the blur overlay keeps its own
per-state lambdas.

## Item 5 — the per-series smart-schedule panel (D-396)

**Root cause (of the gap):** the history recorded per-item outcomes
(outcome / detail / nextAction text) but none of the SCHEDULER's math —
the user had no way to verify what the smart-update system actually
computed vs. what really fired.

**Fix — a 4-line fact panel per item row (rendered only when schedule
data exists; legacy entries unchanged):**
- **Next release** — `EP 8 · Fri 09:30 PM (in 18h 33m)` (or
  "aired … ago") — from the check-time record;
- **Learned delay** — `+45m (source lag)` or `+10m default (not
  learned yet)`;
- **Calculated** — `release + learned delay`, captured AT CHECK TIME
  (`UpdateCheckItemLog.expectedCheckAt`, computed with the SAME
  formula + clamp as `SmartReleaseScheduler`);
- **Landed** — the series' REAL WorkManager one-shot fire time
  (resolved LIVE on every history refresh via a new per-anime
  `sr_main_<mainId>` tag on every one-shot — WorkInfo exposes tags,
  not unique names) + the drift versus the calculated target
  (`· on time` / `· drift +12m` / `· drift 5m early`), or
  "not scheduled — the periodic sweep covers it".

All new fields are nullable-with-defaults, so the persisted JSON from
older rounds keeps decoding; one-shots scheduled before this release
carry no per-anime tag and simply don't resolve a landed time until
the next re-aim (the ScheduleEngine re-aims after every schedule
refresh — the map self-heals within one cycle).

## Delivery

- `4d2ffd9c` — Item 1 + Item 2 (one file: the delete UX pair).
- `9eaca7ff` — Item 3 (provider sweep + manager orchestration).
- `ea44353c` — Item 4 (search screen).
- `d9af9571` — Item 5 (updates core + app).
- CI run 33678931667 — the compiler of record.
- Version → 0.4.15/80; tag → release; 2 ntfy.sh notifications.

## Invariants (unchanged)

- The aniyomi stack is untouched (all five fixes are in
  feature/download, core/download, feature/anime-search, core/updates,
  :app/settings).
- No database schema changes (the new fields live in the JSON log
  file's model, which is `@Serializable` with defaults).
- The CS browse loader module (round 26's D-390) is untouched — the
  search-page fix is purely the top-bar reveal.
