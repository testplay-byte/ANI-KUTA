# Round 106 — The Menu's Final Trim, The Steppers' Real Feel, The Honest Toasts, And The Player List's Downloads + Grid Rework

> Round record 88. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The device round (v1.1.62)

The user's report, decomposed into the four work streams this round executes:

1. **THE THREE-DOT MENU — the final trim.** The menu "shows properly and it
   is perfect in width" — one residual order:
   - "I would just slightly recommend you to make it a little bit more less
     wider. Make it a little bit more less wider and a little bit more
     properly handled. But besides that, everything else is looking quite
     proper about it."
2. **THE TRACKING SHEET — the steppers' real feel.** The tracking entry
   ("quite satisfactory"), the picker's open/close ("shown the proper
   options"), and the wheel contract: all approved. Four orders:
   - THE CLOSE'S SMOOTHNESS: "when I click the progress again, it closes
     properly with an animation, but the animation needs a little bit
     smoothness maybe."
   - THE SELECTION'S PERSISTENCE: "the plus and minus buttons are apparently
     glitchy. Like if I click the plus or minus button, then the progress
     gets increased or decreased one time, but the selection on them
     disappears, which is not a good option. So make sure that the selection
     stays and properly gets shown."
   - THE SCORE'S STEP: "about the score, the score apparently jumps 10
     points, like a whole point, which is not good, while it should only
     jump 0.1 points."
   - THE LONG-PRESS MOTOR: "if I long press on the buttons, then it should
     automatically smoothly start scrolling, and the scrolling should be
     dynamic. Like first of all, it should be slowly scrolling, then it
     should speed up at a constant speed, then it should keep on scrolling
     until I leave the minus button. So handle it properly like so."
3. **THE TOASTS — the placement + the app-wide sweep.** Two orders:
   - THE PLACEMENT: "the bottom toast notifications, which apparently show,
     those are not being handled that well. Like they are being shown way
     too much aligned to the bottom, which is not a good idea. So I would
     like you to improve them."
   - THE SWEEP: "if there are any other kind of toast notifications
     throughout the application, I would like you to improve them, and I
     would like you to format them properly and theme them appropriately as
     needed."
4. **THE PLAYER PAGE'S EPISODE LIST — the downloads + the grid rework.**
   The DETAILED and TRACKLIST layouts: "the detailed one is good. It is
   proper. I don't have any issues with that, and the track list one is
   proper too" — except:
   - THE DOWNLOAD BUTTONS: "they do not show the download buttons there. And
     in a way, that is okay. Like in the player page there could be a toggle
     for this, like a dedicated separate toggle, like given a proper
     dedicated section for it, like download, and the toggle will be used to
     turn on or turn off the download button for every single one of them.
     If turned off, then on the player page the download button will not
     show. But if it is turned on, then the download button will show. And
     also I need you to manage the downloading functionality properly too
     and make sure that the downloading functionality from the player page
     is managed properly, it is working properly, and it is handled properly
     as needed."
   - THE GRID'S REWORK: "I am not satisfied with the grid UI at all. Like
     the things are not managed properly. Like the date does not get shown
     properly, the audio versions do not get shown properly, and also the
     other details do not get shown properly. Like you need to look into the
     things properly. You need to understand, you need to plan, and you need
     to handle the things much better and much more properly. You need to
     think about the layout management. You need to think about the overall
     experience of the things and make them much better and much more
     proper."
   - THE BANNER'S CURRENT TREATMENT: "about the currently playing. The grid
     view has the ability to select between play button and the themed
     tint, but the banner does not have it. So I want you to implement it
     there properly too."
   - THE TAGS: "about the tags, I want you to make sure that all the tags
     are considered properly and handled properly too. Like you need to
     focus on what a good layout actually would look like, how it would be,
     and what are the necessary placement of the elements and other key
     things like that."
   - THE OPTIONS: "all the relevant options for each one of the layouts
     should be available and easily customizable."

## 1. Root-cause research (pre-implementation, verified against HEAD 2d6f0456)

### 1.1 The menu's width (WS-A)

`DetailsActionMenu.kt:166,283` — both DropdownMenus' content Column is
`width(240.dp)` (D-704's 264→240 crimp). The device round's "a little bit
more less wider" = one more modest step: **240dp → 220dp**, both menus
(main + share — one width, one rhythm, the D-701 doctrine). Everything else
byte-identical. "A little bit more properly handled": at 220dp the chip pair
and the rows keep their current metrics — the 10dp outer padding + 16dp
cards leave 184dp of row width, still comfortable for every current label.

### 1.2 The tracking sheet's steppers (WS-B) — three root causes + one new motor

**THE SELECTION'S DISAPPEARANCE — TWO interacting causes, found:**

1. **The index-0 skip (the hard bug).** `TrackSheet.kt:774`: the
   seed/re-center effect guards `if (selectedIndex > 0 && …)` — index 0 is
   SKIPPED entirely. Tapping − down to "Not started"/"—" (or Watching→ any
   0-indexed state) moves the selection highlight to row 0, but the wheel
   never scrolls: the highlighted row sits in the blurred top peek (the
   distance-falloff blur applies at distance ≥ 1) while the old row stays
   centered. The selection "disappears" into the blur. (The guard predates
   the round-105 external re-centering — it was a seed-position
   micro-optimization that became a correctness bug the moment +/− could
   target 0.) FIX: drop the `> 0` guard — the seed at index 0 is already
   centered by the contentPadding geometry (delta ≈ 0 → no-op), and the
   external re-center path glides row 0 back under the same suppression as
   every other index.
2. **The selected row blurs mid-glide.** `TrackSheet.kt:960-963` — the row
   blur is applied by DISTANCE ONLY (`if (blurRadius > 0.dp)
   Modifier.blur(blurRadius)`), while the comment two lines above claims
   "never on the centered/selected row". A +/− tap moves the highlight to
   the NEXT row while it is still one row away from center → the freshly
   selected row renders BLURRED (0.8dp) for the whole glide. With quick
   tap-tap-taps the selection is almost never crisp — "glitchy". FIX: honor
   the comment — `!selected` joins the blur condition.

**THE SCORE'S STEP.** `TrackSheet.kt:658`: `SCORE_STEPPER_STEP = 10` (D-705's
AniYomi-parity choice — ±1.0 per tap). The device round now overrides it
explicitly: the stepper must move **±0.1** (one wheel index). FIX: the
constant → 1 + the comments updated. (The wheel itself was always the 0.1
control — the stepper and the wheel now agree.)

**THE CLOSE'S SMOOTHNESS.** `TrackSheet.kt:281-282`: the picker's
AnimatedVisibility uses bare `tween(300)` (default easing, symmetric) on
both enter and exit, and `expandVertically`'s default `expandFrom` grows
from the BOTTOM. FIX: explicit easing curves — enter
`expandVertically(tween(300, EaseOutCubic), expandFrom = Top) +
fadeIn(tween(180, EaseOutCubic))`; exit `shrinkVertically(tween(280,
EaseInOutCubic), shrinkTowards = Top) + fadeOut(tween(200, EaseInQuad))`.
The fade is deliberately asymmetric (fast in, structured out) — the shrink
carries the motion, the fade only kills the text before it clips.

**THE LONG-PRESS MOTOR (the new machinery).** The steppers use plain
`clickable` — no hold behavior exists. DESIGN (the user's exact curve):
press → 380ms hold threshold → the WHEEL ITSELF starts motorized scrolling
(slow start, linear acceleration, a constant cruise) → release (or the
scale's edge) stops it and settles to the nearest row. Implementation:
- `TrackStepperButton` replaces `clickable` with an `awaitEachGesture`
  pointer handler: down → a 380ms delayed `onHoldStart(direction)`; a quick
  release (< threshold) fires the single `onClick` step; a release after the
  motor engaged fires only `onHoldEnd`. A pressed wash (primary @10%)
  replaces the lost ripple. A cancelled gesture (finger slid off) also ends
  the motor and never steps.
- The wheel (`TrackingWheelPicker`) gains a `motorDirection` state (-1/0/+1,
  hoisted to the sheet's picker Row). A `LaunchedEffect(motorDirection)`
  runs the frame loop: `velocity = min(vMax, v0 + a·t)` in px/s
  (v0 ≈ 40dp/s ≈ 1 row/s; a ≈ 260dp/s²; vMax ≈ 420dp/s ≈ 10.7 rows/s —
  slow start → constant cruise, exactly the ordered curve), scrolling by
  `velocity·dt` per frame via raw `scrollBy` (which keeps
  `isScrollInProgress` true → the EXISTING scroll-driven selection + the
  per-row tick machinery drive the selection — the motor is literally the
  wheel spinning itself). Edge clamp: `canScrollBackward/Forward` breaks the
  loop. The loop's `finally` (release OR edge) settles to the nearest row
  center under `NonCancellable` + the programmatic suppression.
- The seed/re-center effect learns `motorDirection` as a key and early-
  returns while the motor owns the wheel (no re-center fighting the motor);
  after the release the settle leaves it centered → the re-center is the
  no-op confirmation.

### 1.3 The toasts (WS-C) — the flush-bottom placement + the app-wide audit

**THE PLACEMENT.** `DetailsScreen.kt:1909-1918`: the TrackingToastHost sits
in `Box(fillMaxSize, BottomCenter)` with NO bottom inset — the pill renders
flush against the very bottom edge of the screen ("way too much aligned to
the bottom"). FIX: `navigationBarsPadding()` + an 18dp lift inside that Box.
The MarkPrevious/MarkSeries snackbars (the same bottom-anchored-box pattern,
`TrackSheet.kt:1317,1376`) get the same insets treatment for consistency.

**THE APP-WIDE AUDIT (the sweep).** 26 `Toast.makeText` call sites render
the UNGTHEMED system toast (grey box, system font, no tone): debug-bubble's
ConsoleTab (2), extensions-settings (3 files, 7 sites), ResolverSheet (1),
CoverViewerOverlay (3), CsSourceListUi (1), PlayerSheets (1), WatchScreen
(4), AppIconScreen (2), MainActivity (2 helpers), ErrorActivity (1). DESIGN:
one in-app themed toast system, then the sweep:
- `core/designsystem/component/toast/AppToast.kt` — an `AppToast` object
  (thread-safe `MutableStateFlow<Request?>`; `show(message, tone,
  durationMillis)` callable from ANY context — composables, ViewModels,
  MainActivity's threaded helpers) + the `AppToastHost()` composable: the
  TrackingToastHost's APPROVED pill language (the tone disc + glyph + the
  message, the scheme-luminance-aware surface, the 280/2400/320ms rhythm,
  the show haptic), BottomCenter, navigationBarsPadding + 16dp, a
  pass-through Box.
- ONE host at MainActivity's root (over the nav — it covers every screen,
  player included).
- The sweep: every app-owned `Toast.makeText(...).show()` → `AppToast.show`
  with the tone inferred from the site's semantics (success/error/neutral).
  CommonActivity's legacy `showToast` (the vendored cloudstream surface)
  ALSO routes into AppToast — the same MainActivity hosts it.
  ErrorActivity (a separate process-less crash activity) keeps its system
  toast — no themed host exists there; documented, not swept.

### 1.4 The player list's downloads + the grid/banner rework (WS-D)

**THE DOWNLOAD BUTTON — the current truth.** The player list renders NO
download control: the DETAILED row carries an inert `showDownloadHint`
glyph (a hint, not a button — `PlayerEpisodeListLayouts.kt:636,673`), and
TRACKLIST/GRID/BANNER carry nothing. The user orders a real, toggleable,
WORKING control on every layout.

**THE ARCHITECTURE (verified against the module graph):**
- The download ENGINE is feature-agnostic: everything downstream of a
  `DownloadRequest` keys on (mainId, episodeKey) — and BOTH player stacks
  already inject `DownloadManager` (core/download): the states map
  (`episodeDownloadStates`, keyed `"$mainId|$episodeKey"`), the queue
  (pause/resume/cancel/retry by task id — found exactly like
  DetailsViewModel's `findTaskId`), `getDownloadedEpisodeUri`, and
  `enqueueDownload` are all already reachable.
- The CLASSIC enqueue orchestration (content identity → source lookup →
  auto-engine) lives in :app (`handleDownloadEpisode` +
  `DownloadOrchestrator`) — unreachable from the feature modules. FIX: a
  thin `PlayerDownloadController` INTERFACE in core/download
  (`enqueueClassic(mainId, episodeInfo): PlayerDownloadOutcome` +
  `contentInfoFor(mainId)`), implemented in :app by extracting
  MainActivity's proven `handleDownloadEpisode` body, registered in
  AnikutaApp's Koin next to the orchestrator. Both player stacks
  koinInject it — no class moves, no new module deps (feature/watch +
  feature/cs-watch already depend on core/download).
- The CS path: `CsDownloadRequestBuilder` (:app, pure object — imports only
  core.cs-player + core.download types) MOVES to core/download
  (`com.confused.anikuta.core.download.cs`), gaining core/download →
  core/cs-player (NO cycle: cs-player depends only on core/common). The
  CS stack then resolves the tapped episode's links itself (it already
  injects `CloudstreamLinkResolver` + owns the per-handle resolve pattern)
  and enqueues via the moved builder: one link → direct; several → a
  lightweight in-player picker sheet (server/quality/audio rows in the
  established pill language) → the picked link.

**THE RENDER MODEL.** `PlayerEpisodeRowData` gains `downloadState:
PlayerDownloadRenderState?` (null = hidden) — a render-only sealed mirror
of the badge's 7 states (NotDownloaded / InFlight / Downloading(progress) /
Paused / Error / Downloaded — the details page's approved EpisodeDownloadBadge
contract, the D-481 render-only doctrine). `PlayerEpisodeListDisplay` gains
`showDownloadButton: Boolean = false`. The dispatcher
(`PlayerEpisodeListEntry`) gains an optional `downloadActions` bag; the
BADGE (`PlayerEpisodeDownloadBadge` — the details page's 32dp translucent
circle visual, verbatim) renders in all FOUR layouts when the toggle is on:
DETAILED + TRACKLIST trailing (the TRACKLIST's glyph slot), GRID + BANNER
top-end translucent (the details grid's placement; the BANNER's badge sits
at the top corner OPPOSITE the big number — never colliding with the
number-position knob).

**THE GRID'S REWORK — the root cause of "the date does not get shown
properly".** The current cell crams ALL metadata into a bottom SCRIM on a
half-width image: `PlayerEpisodeListLayouts.kt:1084-1093` — pills (date +
audio + subdub + flavors) in ONE non-wrapping `Row(spacedBy(6.dp))`. A
half-width cell is ~170dp; "Oct 12, 2025" + "SUB" + "DUB" ≈ 150dp+ of
pills → the row CLIPS (no wrap, no scroll) — the date and the audio
versions literally cannot all be shown. The title strip has the same
problem ("EP 12" + a long title → ellipsis almost immediately).
REDESIGN (the details page's APPROVED grid anatomy — the text block below
the plate — player-flavored):
- The 16:9 plate stays full-bleed (12dp corners) carrying ONLY the
  over-image treatments: the current PLAY disc / TINT wash + ring, the
  watched checkmark, the download badge, the progress bar.
- BELOW the plate: the text block — the "EP N" themed number label +
  the title (the `gridTitles` knob), then the chips in a WRAPPING
  `FlowRow`: the date chip + every audio/subdub/flavor pill, ALL visible,
  wrapping to as many lines as they need ("all the tags are considered
  properly and handled properly").
- `PlayerEpisodeListPreferences` gains `showAudioPills` (default on — the
  details page's parity knob; "all the relevant options for each one of
  the layouts should be available").

**THE BANNER'S CURRENT TREATMENT.** The BANNER hardcodes the centered play
disc (`PlayerEpisodeListLayouts.kt:1313-1328`) while the GRID owns the
PLAY/TINT knob. FIX: the exact port — `PlayerEpisodeListPreferences` gains
`bannerCurrentStyle` ("PLAY"/"TINT"), the display bundle carries it, the
BANNER's current treatment branches on it (TINT = the grayscaled imagery
under the 50% themed wash + the ring; PLAY = today's disc), and the
settings screen gains the "Currently playing" segmented row for BANNER.

**THE TAGS EVERYWHERE.** The same non-wrapping pills Row exists in DETAILED
(:621-645), TRACKLIST (:843-850), GRID (:1084-1093), and BANNER
(:1352-1359). ALL FOUR become wrapping FlowRows — the tags are data, never
clipped.

**THE SETTINGS SCREEN.** A new proper "Download button" card (the toggle +
the description — "a dedicated separate section for it, like download"),
the BANNER's "Currently playing" row, the "Audio pills" row, and the live
preview's badge demo (the preview rows carry a cycling demo state via the
`previewTapAll` pattern — the D-557 preview doctrine).

## 2. The design decisions (pre-implementation)

- **D-708 (WS-A)**: the menu's final trim — 240dp → 220dp on both menus,
  everything else byte-identical.
- **D-709 (WS-B)**: the steppers' real feel — the index-0 re-center fix +
  the selected-row never-blurs fix (the selection STAYS and shows); the
  score stepper's step 10 → 1 (±0.1 — the user's explicit override of
  D-705's AniYomi step); the picker's eased open/close; the LONG-PRESS
  MOTOR (380ms threshold → the wheel scrolls itself: slow start, linear
  ramp, constant cruise until release/edge; settle to the nearest row).
- **D-710 (WS-C)**: the honest toasts — the tracking toast lifted above
  the nav bar (+18dp); the AppToast system (the object + the root host +
  the themed pill) and the app-wide sweep of every app-owned
  `Toast.makeText` site (CommonActivity included; ErrorActivity documented
  as the exception — no themed host exists in the crash activity).
- **D-711 (WS-D)**: the player list's downloads + the grid/banner rework —
  the `showDownloadButton` pref + the dedicated settings card; the render
  model (`downloadState` + `downloadActions` + the badge in all four
  layouts); the `PlayerDownloadController` interface (core/download) with
  the :app impl (the extracted handleDownloadEpisode); the CS builder's
  move to core/download + the CS stack's resolve-and-pick flow; the GRID's
  text-block rework with wrapping tags; the BANNER's PLAY/TINT port; the
  `showAudioPills` knob; wrapping FlowRows for every pills row.

## 3. Execution order

1. WS-A the menu (the 2-line width change) — the smallest, most isolated.
2. WS-B the tracking sheet (the wheel fixes + the steppers' motor).
3. WS-C the toasts (the lift + the AppToast system + the sweep).
4. WS-D the player list (the prefs → the render model → the four layouts →
   the settings screen → the MPV wiring → the CS wiring → the builder move).
5. The sub-agent audits (≥2, D-689) → the lead verification + fixes.
6. CI → the ledger → the release.

## 4. The execution record

Implemented in commit **0dbe9159** (+ the audit fixes in **d028b8a0**),
26 files touched:

**WS-A (D-708)** — `DetailsActionMenu.kt`: both content Columns
240dp → 220dp (lines 170 + 287 post-edit); the header comment carries the
round-106 note. Byte-identical otherwise.

**WS-B (D-709)** — `TrackSheet.kt`:
- The seed/re-center effect: the `selectedIndex > 0` guard GONE
  (`if (items.isNotEmpty())`), + the `motorDirection` key/early-return.
- `TrackingWheelRow`'s blur: `if (blurRadius > 0.dp && !selected)`.
- `SCORE_STEPPER_STEP = 1` + the comments rewritten (the user's override).
- The picker's AnimatedVisibility: the eased curves (see D-709).
- THE MOTOR: `TrackStepperButton` on `awaitEachGesture` (the 380ms delayed
  handoff via scope.launch, the quick-release step, the cancelled-gesture
  neither, the pressed wash); the wheel's frame-loop effect (the ramp, the
  raw scrollBy, the edge clamp, the NonCancellable nearest-row settle); the
  hoisted `stepperMotorDirection` (reset on ANY picker transition — SA1-F4);
  the `nearestRowCenterDelta` helper.

**WS-C (D-710)** — `DetailsScreen.kt` (the three inset lifts +
the navigationBarsPadding import); `AppToast.kt` (NEW — the object, the
tones, the host, the pill); `MainActivity.kt` (the root host + the two
helpers + the toned call sites); the 20-site sweep (ExtensionsSettings ×3,
CloudstreamPluginDetail ×3, ExtensionRepoSettings ×1, ResolverSheet ×1,
CoverViewerOverlay ×3, CsSourceListUi ×1, PlayerSheets ×1, WatchScreen ×3,
AppIconScreen ×2, ConsoleTab ×2) + the six dead-import drops.

**WS-D (D-711)** — the prefs (+3); `PlayerEpisodeListLayouts.kt` (the
render model, the display bundle, the four reworked layouts, the badge, the
FlowRows, the OptIns); `PlayerDownloadController.kt` (NEW, core/download);
`AndroidPlayerDownloadController.kt` (NEW, :app — the extracted chain);
`CsDownloadRequestBuilder.kt` MOVED to core/download/cs (+ the gradle dep);
`AnikutaApp.kt` (the Koin registration); `WatchScreen.kt` (the MPV wiring);
`CsWatchPage.kt` + `CsWatchScreen.kt` (the CS wiring + the sourceId param);
`PlayerEpisodeListSettingsScreen.kt` (the Download card, the Audio pills
row, the BANNER's Currently playing row, the preview's demo badge, the
anchor shift); `SettingsSearchIndex.kt` (the entry).

## 5. The CI history (the D-472 ledger — 2 runs, WITHIN the ≤2 budget)

- CI run 1 (**36622858251**, Build APK on d028b8a0): **FAILURE** — ONE
  error, `:app:compileDebugKotlin`:
  `AndroidPlayerDownloadController.kt:92:24 Argument type mismatch:
  actual type is 'String?', but 'String' was expected` —
  `DownloadEpisodeInfo.name` is non-null while the controller's neutral
  `EpisodeInfo.name` is `String?`; the extracted chain's own
  `episode.name ?: ""` fallback is the fix (af7f3887). A NEW trap class
  (the bridge's neutral tuple vs the engine's non-null contract) — swept:
  the other tuple consumers all take nullable.
- CI run 2 (**36623851282**, Build APK on af7f3887): **GREEN** — within
  the D-472 ≤2 budget.
- PROCESS DISCLOSED: the ledger files mid-write (decisions D-708..D-711,
  changelog, progress, lessons, doc 88's execution record) rode the
  CI-fix commit via a careless `git add -A` (the round-105 slip's repeat).

## 6. The sub-agent audits (the standing ≥2 order, D-689)

**SA1 — streams 1-3 (menu + tracking sheet + toasts):** PASS after the
lead-verified fixes. 3 HIGH (all pre-CI compile blockers: the missing
`kotlinx.coroutines.withContext` import; `HapticHelper.stageCross` called
without its Context param; the missing `FontWeight` import in AppToast) —
all applied. 2 MEDIUM applied (F4: the stale motor on a mid-hold picker
switch — the reset is now unconditional on ANY transition; F10: the
showDownloadToast tone plumbing — ERROR/SUCCESS per call site). F5 (the
two ExtensionInstaller OS-fallback toasts in data/extension) DOCUMENTED as
accepted exceptions — the module cannot reach the designsystem and the
notices fire during OS intent handoffs. LOWs: F6 (the six dead imports —
applied), F7 (the unused animateFloatAsState — applied), F9 (the slide
measuring the screen — applied via the restructure), F8 (the id-counter
race — documented). The verified PASSES: the gesture contract, the motor's
cancellation semantics (the MutatorMutex serializes; no double-settle),
the index-0 math, the fence's non-interaction with programmatic scrolls,
the four stepper call sites, the sweep's module graph.

**SA2 — stream 4 (the player list):** PASS after the lead-verified fixes.
2 HIGH (both pre-CI compile blockers: `MinimizedMode` referencing
`WatchScreen`'s out-of-scope `downloadManager` local — fixed with the
function's own inject; `event.subs` → `event.subtitles`) — applied. 3
MEDIUM applied (F3: the two LOST settings rows — the Audio pills switch +
the BANNER's Currently playing segmented row — a failed batch edit had
silently dropped them, re-applied and verified; F4: the BANNER's watched
check chip collided with the download badge at top-start — the check now
rides the scrim's chips flow as the leading chip; F5: the CS pick ranked
by a digit-parse of the "4K"/"Auto" labels — now the raw quality int).
LOWs: F6 (the unused Pause import — applied), F7 (the prefs KDoc indent —
applied), F10 (the GRID matcher's title disambiguation — applied); F8/F9
documented (the badge dims with its watched row — consistent with the
card-dim semantics; the InFlight tap is a no-op during the resolve window
— bounded by the resolver's own timeouts). The verified PASSES: the
module graph (no cycle), the render model, the GRID cell's structure, the
controller's every field against the real models, the MPV hoisting + the
stale-closure analysis, the CS resolve-flow termination (the ≤120s budget
+ the watchdog), the Koin graph, the anchor mapping, the search entry.

## 7. The round-107 checklist (the device round on v1.1.63)

1. **The menu**: the narrower width (220dp) — same structure, same
   hierarchy, same rows.
2. **The tracking sheet**: tap +/− — the wheel GLIDES the new row to
   center and the selection highlight stays CRISP through the glide (no
   smear, no vanishing); tap − all the way down to "Not started"/"—" —
   the wheel follows to the top row (the old bug left it stranded); the
   score's +/− moves 0.1 per tap; HOLD +/− — the wheel starts slow, ramps
   up, cruises until release (the selection + the ticks follow), then
   settles on a row; the picker's open/close glides smoothly (no abrupt
   shrink at the end).
3. **The toasts**: the tracking toast floats ABOVE the navigation bar (no
   edge-hugging); the app's other toasts (extension installs, cover saves,
   subtitle imports, the download notices, the debug console's copies) all
   wear the themed pill with the right tone.
4. **The player page**: Settings → Player page → **Download** — the
   dedicated card; toggle it ON and EVERY layout carries the badge:
   - DETAILED/TRACKLIST: the trailing badge — tap download (the spinner →
     the progress ring → the % numeral), pause/resume, retry, and the
     check → plays the offline file.
   - GRID: the top-end translucent badge; the cell's new anatomy (the
     text block below the plate; EVERY tag visible, wrapping).
   - BANNER: the badge at the corner opposite the big number; the
     "Currently playing" PLAY/TINT option (the GRID's parity); the
     watched check riding the chips row.
   - The CS stack: tap download → the resolve → the "Download queued:
     server · quality" toast; the MPV stack: the classic auto-engine pick.
   - The Audio pills toggle; the preview's tappable demo badge (cycles
     all six states).

