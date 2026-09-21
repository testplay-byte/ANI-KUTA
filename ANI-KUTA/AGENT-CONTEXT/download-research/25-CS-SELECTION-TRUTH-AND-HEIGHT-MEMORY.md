# 25 — CS SELECTION TRUTH + THE HEIGHT MEMORY

**Round 65 · D-553 · the v1.1.26 device round's logcat verdict + its three fixes.**

## 1. What the v1.1.26 device round validated (DO NOT TOUCH)

The D-552 pick targets work on-device — the user's own capture (Moon Knight EP 2,
MovieBox, 4 audio versions: Hindi / Original / Malayalam / Tamil, all probed
`1080p · 720p · 480p`):

> "It started resolving and after resolving it showed me all the episodes and all the
> available resolutions for it properly … then I clicked on one of the available
> resolutions, from there I selected 480p. The video loaded and it started to play
> properly without any problems."

The full D-552 chain executed **exactly as designed** (logcat, 20:53:35.866 → 38.947):

```
picked: MovieBox (Malayalam Audio) 1080p (Malayalam) at 480p
seeded: … selected=MovieBox (Malayalam Audio) 1080p at 480p
play request: id=1 … startAt=480p
start[profile=upstream]: MovieBox (Malayalam Audio) 1080p type=DASH …_1080_h265_671/index.mpd
READY: track groups video=1 audio=1 text=0
start-height pin: 480p → video group=0 track=2
video track selected: 480p (g0/t2)
```

The MPD URL carrying `_1080_` is NOT a bug — it is the manifest (all reps); the pin
selected rep index 2 = 480p, and the decoder confirmed `856x480`. **480p genuinely
played.** Untouched this round: RAW mode, sub/dub grouping, the probe, the resolve
flow, the publish layout, data.json, the queue, the scanner.

## 2. The three new defects (and their root causes in code)

### A — Collapsed header truncates the audio-version names
The `CsServerCard` header row renders the FULL version labels ("Hindi", "Original",
"Malayalam", "Tamil") as chips at intrinsic width next to the weighted server name.
With 4 versions the row overflows — the user asked: "if there is not enough space to
show the full names … their simplified or minimified versions should show."

### B — The picked height does not survive re-entry (and the truth is invisible)
Two halves:
1. **Re-entry loses the height.** `CsWatchViewModel.initialize`'s SAME-key re-entry
   path calls `requestPlay(link, resumeMs, isResume = true, keepPosition = false)`
   with NO `initialHeight` → no start pin → ABR climbs to 1080p. `CsSourceMemory`
   remembers the server per mainId but has NO height memory. `autoStart` (episode
   auto-advance / auto-pick) has the same hole. Only the SEEDED path (sheet pick →
   `PreResolvedSeed.initialVideoHeight`) pins — which is why the capture above is
   correct while a plain re-open of the same episode is not.
2. **The truth is invisible.** The engine's `onVideoSizeChanged` feeds state but logs
   NOTHING, and the player dialog's selection marker was computed from the wrong
   predicate (see C). The user's "I have concerns that maybe it was playing in 1080p"
   is the direct product of an unobservable truth.

### C — The player dialog shows no current selection
Three stacked causes:
1. **Wrong selected-track predicate.** `CsWatchScreen` computed `selectedTrackLabel`
   via `group.isSelected` — true for the WHOLE group when ANY of its tracks plays —
   then took `videoTracks.firstOrNull { … }` → the FIRST rep (track 0 = 1080p), never
   the actually-selected rep. Had the section rendered, it would have lied HIGH.
2. **The "Quality for this stream" section is unreachable.** `CsLinksSheet` is a
   non-scrolling `Column(heightIn(max))`; the accordion (a LazyColumn) sits before the
   variants section — with one tall server card (MovieBox = 1 server × 4 versions × 3
   chips) the section is composed but pushed out of the clipped bottom. The user sees
   "no qualities for this stream".
3. **The accordion deliberately marks nothing.** The D-552 probed chips render
   `isSelected = false` BY DESIGN ("all chips of the current link would light up").
   With (2) hiding the live-truth section, NO selection marker exists anywhere —
   exactly what the user reported.

## 3. The fix plan (D-553)

### FIX-A — short forms when the header is tight
- `shortAudioLabel()` in `CsSourceListUi`: deterministic mapping for the common
  languages (Hindi→HIN, Original→ORIG, Malayalam→MAL, Tamil→TAM, …) with a
  first-3-letters fallback; SUB/DUB pass through unchanged.
- Render rule: ≤2 chips → full labels (unchanged); ≥3 chips → short forms. Plus
  `maxLines = 1, Ellipsis` as the belt-and-braces tail. Deterministic (no text
  measuring, no layout loops), future-proof for any provider's version count.
- Unit test in `CsSourceListUiTest` (pure JVM — sandbox-executable like Round 64).

### FIX-B — the height memory + the truth log
- `CsSourceMemory`: new `height:<mainId>` memory (`rememberHeight`/`recallHeight`;
  null/≤0 clears). Same prefs file, backward compatible, no migration.
- Writers: `CsResolveSheet.pick()` (the sheet's chip) and `CsWatchViewModel.selectLink`
  (the in-player pick) remember the height WITH the server. A null-height pick (a
  declared chip / raw row) CLEARS it — an unpinnable choice must not fake a preference.
- Readers: the SAME-key re-entry path pins the recalled height; `autoStart` pins it
  when the remembered SERVER matched (one memory unit: server + height ride together;
  a fresh show without memory stays on ABR).
- Engine: `onVideoSizeChanged` logs `video size: WxH — playing <label>` on every
  change — the decoder's own verdict, making the playback truth permanently
  diagnosable from logcat.
- The mis-pin is self-healing by D-552 semantics: a height the new stream does not
  offer logs "not offered — staying on ABR" and never breaks playback.

### FIX-C — the player dialog tells the truth
- `selectedTrackLabel` is now computed with `group.isTrackSelected(track.trackIndex)`
  — the ACTUALLY-playing rep, not the first rep of a playing group. The open-effect
  keys gain `engineState.videoHeight` so the sheet refreshes the marker live while
  ABR moves.
- `CsLinksSheet` becomes one scrollable body (`verticalScroll`); the accordion, the
  raw list and the variants rows convert LazyColumn → Column (tiny lists — the outer
  scroll now owns scrolling). The "Quality for this stream" section is ALWAYS
  reachable.
- Current-selection markers in the accordion:
  - probed chips: `isSelected = link.url == currentLinkUrl && height == currentPlayingHeight`
    (the live decoder height rides in from `CsEngineState.videoHeight` — exactly ONE
    chip lights: the resolution actually playing of the version actually playing);
  - version label: tinted primary when any of its links is the current link;
  - the sheet gains a "Now playing: <audio> · <label>" line under the hint — the
    answer to "which resolution am I watching" without hunting.
- `CsServerAccordion` grows an optional `currentPlayingHeight` param (default null —
  the resolve sheet call sites stay untouched).

## 4. Why this is the robust shape

- The pin stays ONE mechanism (the D-552 `TrackSelectionOverride` at READY) — the
  memory only decides what the pin's height is; no second selection path exists.
- The truth is now observable at three layers: the decoder log (logcat), the
  "Now playing" line (always), and the highlighted chip/track row (precise).
- Every consumer of `groupServers`/`CsServerAccordion` keeps its signature
  (new params default null) — RAW mode, download mode, sub/dub grouping untouched.
- The height memory is per-show (mainId), matching the user's mental model: "I can
  precisely download/play the version I am hoping for" — and it survives process
  death like the server memory does.

## 5. Verification + release workflow

1. Pure-logic sandbox compile: `shortAudioLabel` + its tests via kotlinc+junit
   (the Round-64 milestone pattern — no Gradle, no local build).
2. Full-file re-reads of every touched region (no line-number surgery residue).
3. CI run #1: feature branch push → `build-apk.yml` (assembleDebug) green.
4. RELEASE-FIRST: `release/1.1.27` branch-point → version 1.1.27/10127 → tag
   `v1.1.27` → CI run #2 (`release-apk.yml`) green. (≤2 runs — the budget.)
5. Docs: this record + handoff §21 + memory (D-553) + the LIVE ledger lines.
6. Worklog + ntfy close-out.
