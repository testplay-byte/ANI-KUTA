# ROUND 95 — THE STILL-LIFT LONG-PRESS FIX + THE LAST-PROMPT INSTALL RACE + THE STAGE-TIMING READ-OUTS + THE RESULTS-SECTION REDESIGN + THE TESTING DEBUG GATE

> The v1.1.51 device round. The report confirmed the round-94 fixes landed
> ("everything is looking quite good… everything is working quite properly
> too") and brought one interaction defect (a still-finger lift after a
> long-press deselected the row), one serious batch-install defect (the very
> LAST extension of a ten-batch never showed its system prompt), one bar
> defect (the X cancelled the install but the bottom bar stayed), the
> stage-timing read-out asks (durations inside the bars + the one-line
> completion verdict), the full item-by-item results-section redesign, the
> extension-testing hide behind a TEN-SECOND debug-options hold, and a
> general "slight performance drops here and there" note. Implementation
> commit: `f4efa62e` (Build APK run **36340511405**).

---

## 0. The round in one paragraph

Three defects with crisp mechanisms: (1) the still-lift — the list-level
`detectDragGesturesAfterLongPress` fires `onDragStart` at the 400ms mark and
enters selection mode, but the ROW's own `clickable` stays armed underneath
(its tap detector has no long-press timeout), so lifting a STILL finger
completed an ordinary tap that hit the row's freshly-swapped
selection-mode lambda and deselected the just-selected row; moving the
finger made the parent consume the moves, cancelling the pending tap —
which is exactly why "for the selection to count, I have to move my
finger." (2) The last-prompt loss — `ExtensionInstallService` called PLAIN
`stopSelf()` from each install's finally, and a plain stopSelf stops the
service UNCONDITIONALLY; when a batch's next `startService` landed in the
stop window, `onStartCommand` launched install N+1 and the queued stopSelf
destroyed the service anyway — `onDestroy` cancelled the scope mid-install
(no prompt, no result, the row stuck on "Installing"). (3) The bar's X —
the UI collector's progress resets sat AFTER the `collect`, so cancellation
threw past them and `batchInstallProgress` never cleared. On top of the
fixes: the stage bars carry their durations in scrim chips at the right
edge plus the settled one-line verdict; the results section took the full
redesign (kind-colored 15sp headings with no dot and no leading accent
edge, per-kind header facts, top-3 payload lists with a shared Expand
button, the one-line details dossier with status/genre pills, stream facts
and the last-frame preview); the extension-testing system became hidden
behind `extensionTestingEnabled` + a TEN-SECOND hold on the Settings
"Debug options" row; and the filter/sort derivations on both tabs were
memoized. All of it landed as D-653..D-658.

## 1. The still-lift long-press fix (D-653)

**Root cause:** `detectDragGesturesAfterLongPress` owns the long-press for
the whole list (D-641), but it does not (and cannot) retire the row's own
tap detector. A plain `Modifier.clickable` has NO long-press timeout —
press, hold any length, release fires the click. So the still-finger lift
after the selection entered was a perfectly ordinary tap as far as the row
was concerned, and the tap hit `onToggleSelected` (the lambda the row had
just swapped to in selection mode) — deselecting the single selected row,
emptying the selection, exiting selection mode. The v1.1.51 report: "if I
long press and do not move my finger anywhere, then the selection
automatically disappears… if I lift my finger, then the selection goes
away."

**The fix:** the drag handler now drives a `DragSelectionSessionState`
(`ExtensionListChrome.kt`) — `active` flips true in `onDragStart` and false
in `onDragEnd`/`onDragCancel`. Every row's BODY clickable on both tabs
gains `enabled = !dragSessionActive` (a plain `dragSessionActive: Boolean`
param on all eight row composables): the moment a long-press session
begins, the recomposition disposes the row's pending tap detector, so the
eventual lift — moving or still — can never fire a click. Taps resume the
instant the session ends. The flag is read inside each row composable, so
only the rows recompose at the session's start and end. This also fixes the
SECOND long-press case (a still-finger lift at the end of a range-select
used to toggle that row back off).

## 2. The install pipeline hardening (D-654)

Three independent defects:

- **THE STOPSELF RACE (the last-prompt bug).** `ExtensionInstallService`
  processed one install per start and called plain `stopSelf()` in its
  finally. The docs' warning applies: a plain stopSelf stops the service
  even when a NEWER start request has been delivered. The batch's
  transition N→N+1 is exactly that window — `onInstallResult(N)` completes
  the deferred, the batch dispatches `startService(N+1)`, and if that start
  lands while the service instance is still alive, `onStartCommand(N+1)`
  launches the install coroutine and the pending `stopSelf` then destroys
  the service: `onDestroy` → `scope.cancel()` → install N+1 dies mid-flight
  (no PackageInstaller commit, no result broadcast, the manager's deferred
  never completes — the row pulses "Installing" until the 5-minute safety
  timeout). The v1.1.51 report: "the very last one apparently does not show
  me the pop-up. It does not give me the pop-up and nothing happens."
  **Fix:** `stopSelf(startId)` on every exit path — the stop only lands
  when THIS start request is the most recent one.

- **THE BACKEND INSTANCE CLOBBER.** `PackageInstallerBackend` kept
  `activeSessionId` / `resultDeferred` / `resultReceiver` as INSTANCE
  fields resolved through a shared `resolve()`. The prompts are serialized
  by the manager's mutex, but the bookkeeping overlaps: install N's
  terminal `resolve()` runs on the broadcast (main thread) while install
  N+1's `install()` (already dispatched the microsecond N's deferred
  completed, on IO) assigns its own deferred to the same field — N's late
  `resultReceiver = null` could orphan N+1's registration and N+1's result
  broadcast would complete NOTHING. **Fix:** every piece of state is now
  LOCAL to the `install` call (captured by the receiver's closure), the
  class is stateless, and each commit's status PendingIntent carries its
  OWN session id as an extra that the receiver filters on — the system
  fires the action at every matching dynamic receiver, so without the
  filter two overlapping installs could answer each other's broadcasts.

- **THE X-BUTTON BAR.** The batch collector's
  `batchInstallProgress = null` sat AFTER the `collect` — cancellation (the
  bar's X) threw out of the collect and skipped it, so the bar's
  `visible = selectionMode || batchRunning || installRunning` stayed true
  forever. **Fix:** the resets moved into a `finally`.

## 3. The stage-timing read-outs (D-655)

The v1.1.51 report on the bars-only stage timings (D-652): "alongside with
the bar, we could show small text inside the bars themselves, on the right
side, in a way that it is clearly visible. And what it will show is the
duration of each one of those bars." Every bar is 15dp tall now (7dp
before) and carries a small SCRIM CHIP at its right edge — a translucent
dark pill behind white 9sp text holding the bar's duration. The scrim
solves the contrast problem completely: it reads over the light kind colors
(sky / amber / lime), over the dimmed failed (55%) / skipped (30%) fills,
and over the bare track of a short bar; the single anchored position means
the running bar's live growth (its chip ticks with the 150ms ticker) never
makes the label jump. Below the stack, the ONE-LINE verdict — never
wrapped, per the report: "All tests successfully completed in 1m 42s"
(healthy chain) or "All tests failed in 23 s" (any failure), colored
primary / error. It lands only once the run has SETTLED (live runs stay
quiet until their verdict is final; aborted and never-run targets show
nothing).

## 4. The results-section redesign (D-656)

The report's item-by-item spec, implemented exactly:

- **THE TIMELINE:** the bubbles are theme colored — each wears its KIND's
  palette color (FAILED keeps the error red and SKIPPED dims to 35% — the
  verdict must stay legible, per the D-594 doctrine).
- **THE CARDS:** the 3dp leading accent edge is GONE ("on the left side of
  them, it does not need to show the theme colored line or such"). The new
  header row: the kind's heading at 15sp ExtraBold in its kind color with
  NO dot; the per-kind MIDDLE fact (weight-filled, one ellipsized line);
  the duration on the very right (live while running).
- **PING:** the middle shows the URL it tried; the body keeps only
  "Responded, HTTP 200" — the message itself lost its duration trailer
  (PingTest now says exactly that; the round trip stays in the payload's
  rttMs).
- **HOME PAGE:** the middle shows "15 entries" (the RAW total — a new
  `entryCount` payload field; the browsable list stays capped at 12); no
  message or first-title lines; the results grid shows the TOP THREE by
  default with the shared Expand button.
- **SEARCH:** the middle shows "10, 4, \<name\>" — the raw total, the
  ATTEMPT NUMBER ONLY when a second attempt was made ("it will also not
  show the total number of attempts unless second attempts were made"),
  the first result's name (never the category — anime/movie/donghua is not
  shown; the name compresses via the row's ellipsis). The stat pills
  ("1 attempt", "won on …") are GONE; top-3 results + Expand below.
- **DETAILS:** no message/detail lines — only the LOADED DETAILS section:
  the title on ONE line, the status as a primary-tinted pill + every genre
  as its own quiet pill (the old crammed `status · genre · genre` string
  could not be formatted properly), the URL as its own monospace line. The
  synopsis is retired from the CARD (this is a compression pass; it stays
  captured in the payload/store).
- **EPISODE LIST:** the middle shows the locale-grouped total ("13
  episodes", "1,000 episodes"); the body is a LIST VIEW now — one row per
  episode (number bold + name), top three by default, collapsed, with the
  Expand button and a quiet "+512 more on the site" line for the uncaptured
  remainder.
- **VIDEO RESOLVE:** the middle shows "5 links" (a new `videoCount` payload
  field — the raw total over the 24-row cap); top-3 link rows + Expand.
- **STREAM PLAY:** simple and clean — the stream FACTS (LINK as its own
  monospace line, LOADED with the byte total + HTTP code) and the live
  preview, which now KEEPS ITS LAST FRAME after success: the 30s cap
  PAUSES playback (a paused ExoPlayer holds its current frame on the
  surface — the report's "properly cached temporarily"), the caption swaps
  to "Stream played successfully · last frame held", and everything
  releases when the card leaves composition. Only the FAILED path collapses
  to the quiet strip.

New plumbing: `TestPayload.entryCount` / `TestPayload.videoCount` (+ the
store's JSON round-trip, opt-read so older blobs load clean), populated by
HomePageTest / SearchTest / VideoResolveTest. The shared `PayloadExpandButton`
("Expand · 9 more" / "Collapse") serves the results grid, the episode list
and the link rows; the run screen's tap-expanded payload views inherit the
same language for free.

## 5. The extension-testing debug gate (D-657)

The report: "for this extension testing system, I would like you to hide it
initially. By default it will be hidden… the user has to enable it. And how
it will be enabled and where it will be placed is in the settings, in the
debug options… the user can currently turn on Always Sponsor and also
another toggle will be given, the Extension Testing. When it has been turned
on, then the user will be given the option to go to the Extension Testing
options… the user has to long press on it for a bit more longer than usually
necessary. He has to long press on it for 10 seconds."

- `AppPreferences.extensionTestingEnabled` (default FALSE) + its flow. The
  extensions header's Science pill only renders while it is ON — the whole
  testing system is invisible otherwise.
- `MoreListRow` gained `holdActivationMillis`: when set, the row swaps its
  combinedClickable for a custom tap-or-long-hold detector — a tap fires
  the click, a robbed gesture (a scroll stealing the press) fires nothing,
  and a hold that lasts the FULL window fires the long action while the
  finger is still down. The Settings "Debug options" row passes 10,000ms.
- The hidden debug page (reached by the 10s hold) gained the "Extension
  Testing" toggle (same bare title+Switch anatomy as "Always sponsor") and,
  while ON, a door row — "Open Extension Testing" — routing to the same
  screen the Science pill opens.

## 6. The perf pass (D-658)

The report's "slight performance drops here and there": the filter+sort
passes on BOTH tabs (four sections each) are now `remember`-ed on their
inputs — every unrelated body recomposition (a batch-progress tick, a
selection change) used to re-run all eight passes over the full repo
catalog. The resting payload cards now load a third of the thumbnails (top-3
by default instead of the all-12 grid) — the biggest per-card cost on
stored testing pages was the SubcomposeAsyncImage wall. Both changes are
conservative; nothing behavioral moved.

## 7. Verification

Brace/paren balance on all eighteen touched files (zero); module-wide
greps: zero dangling references to `PayloadEpisodeChips` / `SearchStatPill`
/ the two-argument `resultsSectionLabel` / the one-argument `TimelineBubble`;
the one external `installExtension` caller compiles unchanged; the
`getParcelableExtra` call in the backend kept its existing (accepted)
deprecation form; the "no sponsor words" standing order is respected (the
pre-existing "Always sponsor" toggle label is the frozen D-562 surface; all
NEW copy avoids the words). CI: Build APK run **36340511405** on `f4efa62e`
(the run number is recorded in the header — the ledger below carries the
final verdict).
