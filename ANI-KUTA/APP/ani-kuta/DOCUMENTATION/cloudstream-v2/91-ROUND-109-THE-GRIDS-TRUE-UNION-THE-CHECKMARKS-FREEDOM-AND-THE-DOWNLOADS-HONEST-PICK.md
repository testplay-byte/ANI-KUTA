# Round 109 — The Grid's True Union, The Checkmark's Freedom, And The Download's Honest Pick

**Round type:** device-round response (on v1.1.65) · **Work orders:** D-719,
D-720, D-721 · **Branch:** `feature/round-57-cloudstream-downloads`

The v1.1.65 device round's three verdicts, verbatim:

> "Both of them have different kind of grid view, which is not good. I want
> the player page to have the grid view, which looks and feels exactly the
> same as the grid view of the details page… Like how the sub and dub
> episode tags are shown and also how the date is shown. I want that exact
> same thing to be happening with the grid view of the player page too."

> "In the player page grid view, apparently there is no option to remove the
> checkmark from the watched episodes. If I remove the checkmark, then the
> grayness of it also gets removed, which is not good."

> "On the player page I went on and tried clicking on the download button,
> and apparently the things were not that much satisfactory. Like it
> directly, automatically started to load, and after loading, it apparently
> did not show me any bottom-up resolved video lists for it or anything like
> that, how it gets handled on the details page… it automatically selects one
> of the video streams and starts downloading it automatically, which is not
> a good idea."

## 1. The root causes (found before a line was written)

**THE GRID'S DRIFT** — the round-108 rework gave the player grid the details
ANATOMY but not the details VOCABULARY; side-by-side, ten differences:
the tags (the player's `Pill` = a neutral gray 5dp rounded rectangle for
EVERYTHING — the details' chips are 50%-radius capsules with SUB primary /
DUB tertiary / HSUB neutral type-coding); the date (the player showed
"Oct 12, 2025" — the details grid's chip carries the SHORT "Oct 12"); the
corner radius (12dp vs 16dp); the plate (surfaceVariant background vs the
details' pure plate); the number tile (26sp vs 22sp at 0.55 alpha); the
title (12sp/15sp vs 13sp/16sp, no watched dim); the progress bars
(full-bleed 3dp edge bars vs the details' inset rounded pills); the chips'
FlowRow rhythm (5dp vs 4dp); the watched check (a centered 34dp disc vs the
details' quiet bottom-start bubble); the badge inset (5dp vs 6dp).

**THE CHECKMARK'S COUPLING** — round 105 wired `gridWatchedCheckmark` as ONE
unit (grayscale + check together) and retired `dimWatched` from the GRID.
Turning the knob off removed BOTH.

**THE DOWNLOAD'S AUTO-PICK** — both stacks auto-picked. The CS stack resolved
inline then picked (current server → highest quality) and enqueued without
ever showing a list. The MPV stack's `enqueueClassic` ran the auto-download
engine, and on its ShowPicker fallback made a "BEST-EFFORT" first-video
grab — the code even admitted it: "the player has no picker sheet."

## 2. The fixes

### D-719 — THE GRID'S TRUE UNION (one implementation, two pages)

- **NEW `:core:designsystem` component/episodelist/EpisodeMetaChips.kt** —
  the details page's own pieces, VERBATIM ports, now shared:
  `EpisodeDateChip` (the quiet surface capsule), `EpisodeAudioChip` (the
  type-coded SUB/DUB/HSUB capsules), `EpisodeWatchProgressBar` (the inset
  rounded pill), and `formatShortDate` ("MMM d"). The details module's
  internal copies are DELETED — it imports the shared ones (the classic row,
  the grid, the timeline, and the unit test all resolve through the import;
  zero call-site churn). The two grids can never drift again: one
  implementation, pixel parity by construction.
- **The player GRID cell reworked to the details grid VERBATIM**: the 16dp
  pure plate (no background box), the 22sp/0.55 quiet number tile, the
  decoupled watched treatment (below), the details' bottom-start check
  bubble, the 6dp badge inset, the inset shared progress pills (download =
  tertiary with track; watch = primary), the 13sp/16sp title with the
  watched 0.55 dim, and the chips FlowRow at the details' (4,4) rhythm —
  the date capsule (SHORT date) leading, then the color-coded audio
  capsules. The player's tag MODEL survives unchanged (deduped, normalized,
  ordered — buildRowTags builds the audio pills; the date renders
  separately).
- **`dateTextShort` joins `PlayerEpisodeRowData`** — both player stacks
  (WatchScreen + CsWatchPage) and the settings preview compute it from the
  metadata's air date; the rows keep the long date, exactly like the
  details page's own classic-vs-grid split.

### D-720 — THE CHECKMARK'S FREEDOM

- `dimWatched` (default on) now owns the GRID's grayscale + the 0.32 dim
  overlay — the same knob the other three styles read.
- `gridWatchedCheckmark` (default on) owns ONLY the check bubble.
- Removing the checkmark KEEPS the grayness; turning the dim off keeps the
  check. The settings rows say so: "The check bubble on watched thumbnails"
  + the Dim row now visible for ALL four styles.

### D-721 — THE DOWNLOAD'S HONEST PICK (both stacks)

- **The CS stack**: the badge's download tap (after the D-403-style folder
  gate — the persisted-URI write-grant check, with an honest toast pointing
  at Settings → Downloads) opens the SAME `CsResolveSheet` the details page
  opens, in DOWNLOAD mode — "Download EP N", the progressive resolved list,
  the server/audio/resolution chips — mounted in the player's own module
  over the playing page. The USER'S pick enqueues through the same
  `CsDownloadRequestBuilder` chain the details page's `handleCsDownloadPick`
  uses (D-550's sibling DASH variants ride the full list; D-552's chosen
  height rides the pick — the old player path dropped it). Dismissal
  cancels and downloads nothing. The inline resolver + auto-pick +
  InFlight machinery is deleted.
- **The MPV stack**: `PlayerDownloadController.enqueueClassic` is RETIRED —
  replaced by `resolveForPicker` (resolve ONLY, returns
  `Outcome.PickerReady(servers)`) + `enqueuePicked` (the user's video, the
  details page's `handleDownloadSpecificVideo` chain). The controller's
  ShowPicker best-effort branch is deleted; `DownloadOrchestrator.resolveServers`
  is public now. The dormant `DownloadVideoPickerSheet` (feature/download —
  zero callers since D.5) moved to `:core:designsystem` and found its first
  real caller: the watch screen mounts it on `PickerReady` (the badge shows
  InFlight during the resolve — the details page's own row-spinner rhythm),
  the pick enqueues, dismissal downloads nothing.

## 3. The round-110 checklist (the device round on v1.1.66)

1. **The two grids, side by side**: the player page's grid and the details
   page's grid read as the SAME design — the 16dp plates, the same number
   tiles, the same title sizing, the same inset progress pills, the same
   chip capsules (SUB primary, DUB tertiary, HSUB neutral) and the same
   SHORT date ("Oct 12") in the quiet date capsule.
2. **The checkmark**: Settings → Appearance → Player page → GRID — turn
   "Watched checkmark" OFF: the grayness + dim STAY, only the bubble
   disappears. Turn "Dim watched episodes" OFF: the check stays, the
   grayness lifts. Both rows visible under the GRID style now.
3. **The CS download flow**: on the CS player page, tap a row's download
   badge → the bottom-up "Download EP N" sheet slides up with the resolved
   server/audio/resolution list → pick → "Download queued" + the badge
   starts its real progress. Dismiss = nothing downloads. NO automatic
   stream selection anywhere.
4. **The extension download flow**: on the MPV player page (an aniyomi
   source), tap download → the badge spins while resolving → the
   "Choose video to download" sheet shows the server accordion → pick a
   quality → the download enqueues.
5. **The folder gate**: with no download folder picked, tapping download
   (either stack) shows "No download folder — pick one in Settings →
   Downloads first" instead of a failed enqueue.
6. **The settings previews**: the player settings' GRID preview shows the
   new anatomy live (chips, short date, the decoupled knobs).

## 4. The CI history (the D-472 ledger)
- **Run 36743722996 (Build APK, commit 7641b6d1) — FAILURE**: the CS sheet
  mount anchored on the description section's tail (identical-looking
  closing braces) and landed inside `CsCurrentlyPlayingSection`, where the
  page-level state does not exist — 12 unresolved references, one cause.
  Fixed by moving the mount into CsWatchPage's own body (3f23252a).
- **Run 36744466626 (Build APK, commit 3f23252a) — GREEN** in ~5m.

## 5. The audit record
(the implementation was self-audited line-by-line this round: bracket
balance 0/0 on all 14 touched files; the stale-reference sweep is clean —
enqueueClassic/csEnqueueInFlight/csLinkResolver/sourceId-param live only in
comments; the when-exhaustiveness over the new Outcome is total at both
sites; the sheet callbacks' arities match the contracts; the label
`return@PlayerEpisodeDownloadActions` follows the repo-proven pattern.)

## 6. The release record (the D-565 loop)
Released **v1.1.66 / 10166**: `release/1.1.66` cut from the green mainline
head 3f23252a (Build APK run 36744466626 GREEN after one fix round; the
ledger commit is docs-only); the bump (10120→10166, 1.1.20→1.1.66) rode the
branch per D-430; the annotated tag `v1.1.66` carries the honest bullet
body (D-466); **Release APK run 36745155205 GREEN FIRST-TRY → v1.1.66
LIVE** (published 2026-09-30T16:38:22Z, stable latest, arm64-v8a debug APK
63.5 MB + SHA256SUMS.txt — verified via the API: the tag, the latest flag,
the assets). The mainline twin (the docs-only mirror, no version bump per
D-430) lands immediately after this record. Awaiting the user's device
round (checklist §3).
