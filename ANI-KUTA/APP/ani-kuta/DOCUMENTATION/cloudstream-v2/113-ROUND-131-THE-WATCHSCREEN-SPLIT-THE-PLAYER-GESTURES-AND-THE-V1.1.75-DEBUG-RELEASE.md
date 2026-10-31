# 113 · ROUND 131 — THE WATCHSCREEN SPLIT (PHASES 1+2), THE PLAYER GESTURES, AND THE v1.1.75 DEBUG RELEASE

> 2026-10-31. The user's order: execute the WatchScreen size plan properly
> ("optimize it and handle it better"), all of it in one single go — and add
> proper gesture features to the player (right-side vertical = volume,
> left-side vertical = brightness, horizontal = seek with the video visibly
> moving, all configurable in settings, "make sure the UI of the gestures is
> proper too"), then the v1.1.75 debug release and a testing HTML covering
> this round AND round 130. Decisions D-807..D-810.

## 1. The split, phase by phase (D-807, D-808)

The plan (doc 112 §6) called WatchScreen.kt "2,100 lines" — the real number
was **4,482**. The plan's Phase 3 (the switch pipeline) was explicitly OUT of
scope this round; Phases 1+2 both landed.

**Phase 1 (D-807) — the stateless tail moved verbatim.** Lines 3696–4482 —
FullscreenMode, PlayerSurface, ControlButton, the round-120 overlays, the
skip/resume pills, PlayerEpisodesSheet, the star-rating bar, the episode
pickers, and the format/parse helpers — moved byte-for-byte into
**`WatchScreenUi.kt`** (same package, `private` → `internal`). Compile green
first try.

**Phase 2 (D-808) — the two state machines moved into a controller.**
**`WatchPlaybackController.kt`** (feature/watch/impl) is a plain class —
NOT a ViewModel — owned with `remember` inside WatchScreen: it must live and
die with the composition (never recreated across MINIMIZED↔FULLSCREEN, which
is what keeps playback from restarting on the swap). It owns:

- the **Up Next machine**: `offerNext` / `onDismiss` / `onPlayUpNext` /
  `reArmIfSeekedBack` (the >15 s re-arm) / `onFileLoadedReset` /
  `clearDurableOnFreshReplay` / `pruneTo` / the countdown, plus both
  dismissal stores (in-session + the D-804 durable list);
- the **AniSkip machine**: the skip-timestamps state, the fetch (cached-first,
  the v3 latch, the three-way contract, the `mal=` URL fallback), the
  AUTO/BUTTON tick `when`, the pill action, and the computed `skipRanges`
  (AniSkip wins per side, manual stamps the fallback).

THE CONTRACT that kept rounds 120–130's hardening intact: **every Compose
`LaunchedEffect` STAYED in WatchScreen.kt with its keys byte-identical** —
only the bodies became thin calls into the controller. Timing semantics live
in the keys; the verifier diffed all of them against git HEAD. Two subtle
fidelity points worth recording:

- `offerNext`'s successor is a **provider lambda** — the old code resolved
  `nextEpisodeInList()` only when an offer actually proceeded; an eager
  argument would have re-run the list scan (and its warn log) on every
  suppressed poll tick. The verifier caught the first draft being eager.
- The fetch wraps the DB + network block in `withContext(Dispatchers.IO)`
  (a synchronous SQLDelight read + a synchronous OkHttp `execute()` —
  either on the main thread would ANR or throw), and the final write carries
  the stale-result guard (`isCurrent`) so a cancelled episode-N fetch can't
  clobber episode-M's already-resolved bands. Both were dropped in the first
  controller draft and restored — the lesson: the effect body was NOT "off
  the main thread" by magic; the OLD inline code did the shifting explicitly.

`onEpisodeSwitch` is a settable hook assigned right after the lambda's
declaration (declaration-order independence). The fetch's stale guard and
the introEndSec manual fallback both verified.

**Result:** 4,482 → ~3,3xx lines across three files, app compile green, the
full test suite green. Phase 3 (the switch pipeline → its own class) stays
deferred — it is the riskiest machine (D-749 supersede logic) and deserves
its own round + device pass.

## 2. The gestures (D-809)

**New file: `core/player/.../controls/PlayerGestures.kt`.** The design:

- **Master gate:** the legacy `player_gestures` preference
  (`gestureControlsEnabled`) — which had ZERO consumers since it was written —
  is finally the switch, migrated to a `Preference<Boolean>` handle (same
  key; stored values survive).
- **Right-half vertical swipe** → volume: starts from MPV's live `volume`,
  full screen height = the whole range, clamped to `volume-max` (boost
  respected), applied via `MPVLib.setPropertyInt` (guarded — a drag landing
  before mpv init is a no-op, not a crash).
- **Left-half vertical swipe** → brightness: `window.attributes
  .screenBrightness`, starting from the current override (0.5 when
  BRIGHTNESS_OVERRIDE_NONE is active), **reset to BRIGHTNESS_OVERRIDE_NONE
  when the controls overlay disposes** — a dimmed window never leaks into
  the rest of the app, including across the minimize/fullscreen swap (each
  overlay owns its own state; exactly one disposes per swap).
- **Horizontal swipe** → live seek: the axis locks on the first movement
  (density-scaled 8dp slop); while dragging, an absolute seek fires every
  ~450 ms so **the video visibly follows the finger**, and the exact target
  lands on release. Full screen width = the whole episode.
- **The OSD:** a glass panel hugging the adjusted side (volume right,
  brightness left — following the side the gesture STARTED on) with an icon,
  a vertical fill bar, and the live percentage; seek gets a centered panel
  with direction arrows + the running delta + the target time. The timeout
  (2.5 s) calls `hideOsdOnly()` — NOT cancel — because a finger resting
  mid-drag (reading the seek target) is natural; the gesture must stay live
  underneath. The verifier caught the first draft killing the gesture.
- **Touch arbitration:** the drag surface is a full-size Box that coexists
  with the tap detectors (`detectDragGestures` never consumes a tap; the tap
  detector cancels on slop) and sits BESIDE the seekbar's own drag detector,
  which wins as the topmost hit. Lock mode gates gestures like everything
  else. Both control stacks (FullscreenControls + MinimizedControls) got the
  identical layer, so both orientations and both modes work.

**Settings (Player → Playback behavior → Gestures):** the master switch;
per-side pickers (Volume / Brightness / None) for right and left; the
swipe-to-seek toggle. The sub-rows hide when the master is off. New prefs:
`pref_player_gesture_right_side` (default VOLUME),
`pref_player_gesture_left_side` (default BRIGHTNESS),
`pref_player_gesture_horizontal_seek` (default on).

## 3. The v1.1.75 debug release (D-810)

Routine A, executed on the round-131 green head (`d179102a`): release branch
`release/1.1.75` cut from main, ONE bump commit (10120/1.1.20 → **10175 /
1.1.75**), branch + annotated tag `v1.1.75` pushed → the Release APK workflow
builds the debug arm64-v8a APK and creates the GitHub release (stable +
latest). CI ledger for the cycle (D-472): **2 runs** — the main Build APK
(run 1, the round's code) and the Release APK (run 2, the release).

## 4. The device-test pack

`planning/2026-10-31-round-131-device-test.{html,md}` — the combined
rounds-130+131 pack: the gestures (vertical sides, horizontal live-seek,
settings), the round-130 machines getting their first device pass (skip
bands incl. the airplane-mode latch test, the durable dismissal incl. the
re-arm and fresh-replay clears, the downloads open-start + mid-download
kill, the `.data.json` stamps), and a zero-change regression sweep for the
split (the contract's proof is behavioral: nothing may feel different).

## 5. The adversarial verification

The verifier's five accepted findings, all fixed before the commit: (1) the
fetch lost its IO dispatcher (main-thread DB + OkHttp → ANR /
NetworkOnMainThreadException, silently swallowed into a permanent Error);
(2) the stale-result guard was dropped (a cancelled fetch could wipe the
switched-to episode's bands); (3) the manual introEndSec fallback was
missing from the computed skip ranges; (4) the OSD timeout could kill an
active gesture; (5) eager `nextEpisodeInList()`. Plus the cheap ones: the
MPV calls guarded, the slop density-scaled, the controller keyed on
`watchKey.mainId`. Explicitly verified fine: all 21+ effect keys
byte-identical, the whole controller logic parity (latch order, the "|outro"
suffix keys, the 15 s re-arm, the three-way contract), the Phase-1 verbatim
move, and the touch arbitration.
