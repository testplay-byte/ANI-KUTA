# 22 — DEVICE ROUND 31 PLAN (v0.4.19)

Feedback source: the v0.4.18 device report (OnePlus KB2001, Android 14). The round-30
wizard rework landed well — the user confirmed the flow works end-to-end (folder
verified, notifications, background, Start Watching → preloaded library). This round
polishes the last wizard wrinkles and delivers two PLAYER features the user has been
asking for.

## What the report APPROVED (do not touch)

- The wizard's overall flow, the button-at-bottom placement (D-406), the step
  progression, the finish summary grid, the preloaded library after Start Watching.
- The data.json deletion system (confirmed resolved in round 30 — zero deletion code
  changes this round).
- The wizard's color palette / blob colors on the welcome screen.

## Round 31 work items

### A. The welcome background — the merge shade-pop + the fixed paths (art polish)

Report: *"When they combine together with each other, they suddenly change their
shades… The globes can transform into different shapes and get merged with the
different random places and such, not a simple fixed path."*

Root causes in the D-406 engine:

1. **THE SPLIT-CHILD POP** — child B of a split was drawn only once
   `split > 0.02`, exactly on top of child A, with the SAME alpha → the
   double-draw composite made the center shade JUMP at birth and SNAP back at the
   merge. That is the "suddenly change their shades when they combine".
2. **FIXED PATHS** — each blob's center rode ONE Lissajous orbit around a fixed
   anchor — periodic, predictable, "a simple fixed path".
3. **ONE SHAPE PER BLOB** — each blob morphs organic ↔ the SAME polygon forever.

Fixes (all inside the existing zero-allocation, single-monotonic-clock engine):

- **Screen-blend compositing** for the blob layer — overlaps now blend like light
  (smooth brightening where they meet — a true liquid merge, no dominance pop);
  the split child is drawn with an `alpha` ramp proportional to the split so a
  merge/separation is perfectly continuous.
- **Three-harmonic wandering** — each axis of each blob's center sums three
  incommensurate sinusoids with larger amplitudes → the path never retraces, never
  repeats, and blobs genuinely cross each other at varying screen locations
  ("merged with the different random places").
- **Shape sequences** — each blob now cycles through a LIST of polygon shapes
  (e.g. hexagon → triangle → pentagon → square) on a slow staged cycle, crossfaded
  silhouette-math (identical to the existing organic↔polygon blend, one more lerp).
- **Radius breathing** — a slow per-blob scale pulse for extra life.

Everything else stays: the monotonic clock (no wraps), pre-allocated paths/arrays,
cached brushes, the outline layer, the tagline + wordmark.

### B. The theme carousel centering

Report: *"the carousel of the themes could be centered. It should not be aligned to
the top with the light and dark buttons but it should be centered between the bottom
one and the above one."*

The `LazyRow` filled the whole weighted middle area but its items were TOP-aligned
(LazyRow's default cross-axis alignment) → the cards hugged the toggle. Fix:
`verticalAlignment = Alignment.CenterVertically` — the carousel now floats in the
exact vertical middle between the Light/Dark toggle and the Continue button.

### C. The permission steps' granted moment (notifications + background)

Report: *"it got a line to the left side instead of staying centered. The whole
notification checkmark and the 'Notifications enabled' were aligned to the left
side"* (and the same for Background Use after granting).

The CTA→label swap was an abrupt `if/else` with no transition and no explicit
centering container. Fix: the swap is now an `AnimatedContent` with
`contentAlignment = Alignment.Center` + full-width explicitly-centered children —
the granted state (morphed check + label) can only ever render dead-center, and the
action→granted transition animates smoothly (fade+scale, the wizard's motion
language).

### D. The folder step — show the full path

Report: *"it could show the full folder path which the user had selected. Besides
that, clicking the 'Folder Verified' text could be improved."*

- New `OnboardingPermissions.describeFolderPath()` — converts the SAF tree URI to
  a readable chain ("Internal storage › ANI-KUTA › Downloads") via
  `DocumentsContract.getTreeDocumentId` (+ graceful fallbacks).
- The granted state renders the path inside the polished glass panel — full path,
  ellipsized middle, centered, with the verified check above it and the full-width
  Change-folder button below.

### E. Ads — the first-ever content click is ad-free

Report: *"For the very first time the user opens up the application and clicks on
any of the contents, he should not be shown the advertisement pop-up… afterwards
the normal advertisement system will work."*

- `AdPreferences`: new persisted `ads_first_open_grace_consumed` flag.
- `AdsRepository.consumeFirstOpenGrace()` — one-shot, atomic.
- `AdsCoordinator.requestNavigation()`: after the enabled gate, before the
  cooldown gate — if the grace is still available, consume it + proceed
  immediately (no interstitial, no cooldown recorded). Exactly ONE ad-free
  content open per install; every later navigation follows the normal
  6h-cooldown system.

### F. Downloaded episodes — subtitles in the selector (the core fix)

Report: *"With the downloaded episodes… their subtitles do not get shown in the
subtitle selector… they are stored properly in the local storage, in the subtitles
folder with proper numbering and naming… it does not handle those properly."*

Root cause: the **Details-page hand-off passed `""` for subtitle tracks when
playing a downloaded episode** (the comment claimed "they're on disk" — but the
player never scanned the disk). Only the Downloads-page path wired them, and the
in-player episode switch used generic "Subtitle N" labels.

Fix — ONE shared resolver in `:core:download` so every path agrees:

- `DownloadManager.resolveSubtitleTracks(mainId, episodeNumber)` → DB
  `subtitleUris` first, **disk-scan fallback** (the content folder's `subtitles/`
  subfolder, pattern-matched by episode number — the exact scan the Downloads page
  had inline) → labels parsed from the file names (English/Español Latino/…).
- Wired into: **DetailsScreen** (the downloaded branch now serializes the tracks
  exactly like the streamed branch), **MainActivity**
  (`buildWatchKeyForDownloadedEpisode` now delegates to the resolver), and
  **WatchScreen's in-player episode switch** (proper labels + the disk fallback).

### G. The subtitle sheet — the permanent "add subtitles manually" option

Report: *"add a permanent option there: the option to add subtitles manually. When
the user clicks that option, he will be led to the device's file picker, where the
user can pick any kind of subtitle files (VTT, SRT, or any other relevant ones).
After selecting those files, those subtitles will start to show up properly."*

- `SubtitleTracksSheet`: a permanent, visually distinct "Add subtitle file" row
  (Plus icon, dashed accent border) — always present, for every episode.
- A custom `ActivityResultContract` (`ACTION_OPEN_DOCUMENT` +
  `EXTRA_ALLOW_MULTIPLE`) → the system picker allows selecting MULTIPLE subtitle
  files. Extensions validated post-pick: `.srt .vtt .ass .ssa .sub .ttml`.
- For a DOWNLOADED episode: the picked file is copied into the episode's dedicated
  `subtitles` folder (naming `subtitle_E0000N_manual_<name>.<ext>`), appended to
  the DB row AND `.data.json` (the durable source of truth — survives reinstalls
  via the scanner) — it shows up on every future play of that episode.
- For a streamed episode: the file is staged via the existing `SubtitleEngine`
  (session-scoped).
- Either way: staged to the player cache → MPV `sub-add` with the `select` flag
  (activates immediately) → the track list refreshes → the sheet lists it with a
  readable label (the picked file's display name). The user can then switch
  tracks/off exactly like any provider subtitle.

### H. Delivery

Version 0.4.19 (versionCode 84) → push → CI green → GitHub release → ntfy notify →
progress.md/changelog updates.
