# 23 — DEVICE ROUND 32 PLAN (v0.4.20)

Feedback source: the v0.4.19 device report (OnePlus KB2001, Android 14). The wizard is
now FULLY approved ("everything was exactly how I wanted it to be" — zero wizard changes
this round). The manual subtitle-import flow works end-to-end on device (picked file →
applied → shown). This round is the SUBTITLE-SYSTEM MATURITY round: five findings.

## What the report APPROVED (do not touch)

- The onboarding wizard end-to-end (all steps, layout, animations, granted states).
- The manual "Add subtitle file" flow mechanics (SAF picker → applied → showing).
- The deletion system (round-30 closure stands — zero deletion code touched).

## The five findings + root causes (researched in-repo, exact lines)

### A. Downloaded episodes' subtitles still not in the selector (the headline)

The report: *"It did not show me the subtitles in the subtitles category. This was a
huge issue."*

Research verdict (Explore agent + direct reads):

- The player wiring EXISTS (WatchScreen `initMpv` L722-736 sets
  `obs.pendingSubtitleTracks` from `watchKey.parseSubtitleTracks()` for BOTH network and
  `content://` videos; `PlayerObserver` consumes them on FILE_LOADED → stages via
  `SubtitleEngine` (which handles `content://`) → `sub-add`).
- The naming matches (publish writes `subtitle_E%05d_{lang}_{idx}.{ext}`; the scan
  matches `subtitle_E${epNumPadded}_` — both `%05d`).
- BUT the resolution chain has FOUR fragile links, any one of which yields an empty
  list → empty pending tracks → an empty selector:
  1. **Stale in-memory cache** — `resolveSubtitleTracks` reads
     `_downloadedEpisodes.value` (refreshed only on some paths); a download that JUST
     completed (the report's exact flow: download → open) may not be in the cache yet.
  2. **`findContentFolder(mainId)` silent-fails** — it walks `.data.json` manifests;
     an unparseable json (the round 25-28 corruption class) or mainId drift → `null` →
     zero files, no fallback.
  3. **`labelForUri` is broken for REAL SAF URIs** — `Uri.parse(uri).lastPathSegment`
     on a SAF document URI is the whole decoded document path
     (`primary:Title/subtitles/subtitle_E00001_english_0.srt`), which does NOT start
     with `subtitle_E` → every label falls back to "Subtitle N" (cosmetic, but wrong).
  4. **The UI has no fallback when the auto-path fails** — the sheet lists only MPV's
     live track-list; if the pending pipeline dropped the subs anywhere, the user has
     no way to load them except the manual picker.

### B. "Add subtitle file" row placement + description

The report: *"it should be shown below the subtitle settings but above the off button"*
and *"its description should not be shown"*. Current sheet: Settings row (fixed, above
list) → LazyColumn [Off → tracks → Add (LAST, with a description line)]. Target:
[Settings] → [Add subtitle file (FIRST list item, NO description)] → [Off] → [tracks].

### C. Subtitle settings not applied on episode open

The report: *"When I opened a new episode, my old subtitle settings were not applied
directly. For them to apply I had to change something… The issue is with the loading."*

Root cause: the LIVE `applySubtitlePreferences()` (the reliable `setProperty*` path —
`AnikutaMPVView.kt:178-199`) has exactly ONE call site — the settings sheet's
on-change callback (`WatchScreen.kt:1428`). The start-time path is the INIT-time
`setOptionString` pass (`applySubtitlePreferencesInit`, L144-166) — string-set is not
reliable on all devices for numerics (the code's own comment says exactly this). So
remembered styles only take effect after a manual tweak re-runs the live path.

### D. Remember the selected subtitle (pre-apply on open)

The report: *"make it remember the location of the selected subtitle files. If the user
manually selects a subtitle, then that subtitle file will be remembered for that
specific episode… that subtitle will be selected and will be pre-applied on that."*

No per-series/per-episode subtitle selection memory exists anywhere today.

### E. Manual import creates a duplicate copy

The report: *"If I select a subtitle file from my subtitles folder, then another copy of
that subtitle file is created in the subtitles folder of that specific series."*

Root cause: `importSubtitleFile` unconditionally copies the picked source into
`subtitle_E{num:5}_manual_{name}.{ext}` — there is ZERO dedup against the episode's
existing subtitle files (the user picking the episode's OWN subtitle file through the
SAF picker produces a second, renamed copy).

## The fixes (D-408)

### A. The robust detection + loading system (layered, belt-and-braces)

**`resolveSubtitleTracks` becomes a 4-layer chain** (`DefaultDownloadManager`):

0. **Stale-cache reload** — if the row lookup fails (or the row's URIs are all blank),
   reload the cache from the DB once and retry (kills the download→open race).
1. **DB row `subtitleUris`** (fast path, unchanged).
2. **The videoUri-derived walk** (NEW, the most robust locator): the episode's OWN
   video file document id (`primary:Root/video/Title/episodes/E1.mkv`) gives the
   content folder directly — walk up 1-2 segments from the SAF root via `findFile`
   (immune to `.data.json` corruption AND mainId drift — the video is PLAYING from
   that folder, it cannot lie). New:
   `DownloadStorageProvider.findSubtitleFilesForEpisodeNearVideo(videoUri, episodeNumber)`.
3. **The mainId manifest walk** (existing `findSubtitleFilesForEpisode` — refactored
   onto a shared scan helper).
4. **The title fallback** (NEW for the resolver): the delete flow's proven
   `findContentFolderByTitle(mainId, row.content.title)` + the shared scan.

Labels: `DownloadedSubtitleLabels.labelForUri` now takes the last `/` segment of the
decoded document path (+ a public `fileNameOf` helper for the dedup).

**The sheet's "Available in storage" section** (the player-side safety net): when the
subtitle sheet opens, WatchScreen resolves the CURRENT episode's on-disk subtitle files
(the same resolver) and passes the ones NOT already loaded as MPV tracks to the sheet.
Tapping one runs the PROVEN manual-import load path (stage → `sub-add "select"` →
refresh → toast) — *"When the user clicks on those subtitles will be loaded from
storage onto the player and will be shown exactly like how they currently are."* If the
auto pending-track path works, the section is empty (all tracks already listed); if
anything upstream dropped them, the user still sees + can load them.

### B. Sheet layout

"Add subtitle file" moves to the FIRST LazyColumn item (below the fixed Settings row,
above "Off"); the description line is removed (the label alone). The empty-stream
message drops its stale "below" reference.

### C. Settings applied on every file load

`PlayerObserver` now calls `mpvView?.applySubtitlePreferences()` (the reliable live
`setProperty*` path) on FILE_LOADED and on the PLAYBACK_RESTART treated-as-loaded
fallback — every new file, every new episode, every fresh screen.

### D. The per-series selected-subtitle memory

`PlayerPreferences` gains `get/setPreferredSubtitleTrack(mainId, label)` ("" = none,
"off" = explicitly off, else the track's display label). Persisted: on every sheet
track selection (including Off), on every manual import, on every storage-row load.
Pre-applied: a new `PlayerObserver.onTracksLoaded` hook (fired after every track-list
reload) lets WatchScreen match the remembered label against the live tracks (name or
lang) and set `sid` — the remembered subtitle is selected automatically on open, on
episode switch, and after external tracks finish loading. "off" pre-applies `sid=no`.

### E. Import dedup

`importManualSubtitle` gains a dedup phase before the copy: if the picked file's
decoded document id equals an existing track's, OR the raw filename matches
(case-insensitive) an existing file in the episode's subtitles/ folder, NO copy is
made — the existing track is returned (and appended to the DB row if it was only
found by the disk scan). Picking files from anywhere else still persists a copy (the
persistence design is unchanged for genuinely-new files).

## Non-features (explicitly)

- The wizard, the ads grace, the deletion system, the blob art: ZERO changes.
- No DB migrations (the `.sq` layer is untouched this round).
- The pending-track `sub-add` flag stays `"auto"` (activation is the memory's job, not
  a forced default).

## Verification

CI (the compiler of record): full build + all unit tests. Device checklist in the
release notes: (1) download → open → the selector LISTS the episode's subtitles with
real labels; (2) the "Available in storage" section shows + loads any the auto path
missed; (3) Add-row placement + no description; (4) subtitle styles survive episode
changes with zero tweaks; (5) a picked track pre-applies on the next open + episode
switch; (6) picking the episode's own subtitle file creates NO copy.
