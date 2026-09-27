# ROUND 94 — THE LONG-PRESS KEY-SPACE FIX + THE INSTALL DEADLOCK + THE NSFW TRI-STATE + THE MULTI-FIELD SEARCH + THE BARS-ONLY STAGE TIMINGS

> The v1.1.50 device round. Round 93's rework shipped with two hard
> regressions (the long-press was completely dead; single-tap installs
> self-deadlocked after the download) plus four requested refinements (the
> NSFW tri-state, the name+language+version search, the auto-scroll quality,
> the bars-only stage timings). Implementation commit: `ed0c179f` — **CI
> GREEN on the FIRST run** (Build APK run **36320244240**).

---

## 0. The round in one paragraph

The v1.1.50 report opened with the two failures that matter most: "the long
press functionality is gone… It does not open up the selection at all" and
"I was unable to install the extensions at all. Like I clicked on them, they
downloaded, but after downloading they did not show me the install prompt"
— with the companion symptom "I was not able to install multiple extensions
at the same time now. Like I was unable to click them." Both were round-93
birth defects, and both had crisp root causes: the drag-selection handler
hit-tested LazyColumn ITEM KEYS against raw pkg-name sets (never a match),
and `installExtension` held `installMutex` across the download before
calling `dispatchAndAwaitInstall`, which locks the SAME non-reentrant mutex
(a permanent self-deadlock that also parked every other install behind the
dead lock). Alongside the fixes, four refinements: the NSFW filter became a
persisted tri-state (off / on / only — default off), the search now matches
name + language + version, the drag auto-scroll gained a real velocity model
(quadratic edge ramp + hold acceleration + per-tick hit-testing so rows
scrolling under a stationary finger still join the selection), and the
full-details stage timings dropped every label for BARS ONLY. All of it
landed as D-648..D-652.

## 1. The long-press key-space fix (D-648)

**Root cause:** round 93's `rememberDragSelectionModifier` resolved the row
under the finger by comparing `layoutInfo.visibleItemsInfo[].key` against a
`selectableKeys` set the callers filled with RAW pkgNames (aniyomi) and RAW
internalNames (CloudStream). The lists' item keys are PREFIXED —
`"installed-…"`, `"errored-…"`, `"untrusted-…"`,
`"available-…-<versionCode>"`, `"cs-installed-…"`, `"cs-available-…"` — so
`info.key in selectableKeys` was false for EVERY row on BOTH tabs. The
long-press gesture fired into a void: no selection entry, no drag-paint, no
range — exactly "It does not open up the selection at all."

**The fix:** the selection state now lives in the EXACT item-key space. Each
screen builds its flattened `orderedSelectableKeys` with the same key
builders the `items()` calls use (`installedKeyOf(pkg)`,
`availableKeyOf(pkg, versionCode)`, `cs-installed-…`, …); `selectedKeys`,
`selectionAnchor`, `toggleSelected`, `dragSelectStart` and the bar's
per-section subsets all speak that language; the rows compute their own key
for `selected`/`onToggleSelected`. Select-all, the range select and the
batch actions ride the same keys.

## 2. The auto-scroll rework (D-648)

The v1.1.50 report: scrolling "was not properly handled. Like if I scrolled
way too much to the bottom, it did not scroll quickly." Three defects in the
round-93 loop, all fixed:

1. **A fixed ~875px/s ceiling** (`AUTO_SCROLL_STEP_PX = 14` per 16ms tick)
   — now the speed ramps QUADRATICALLY with edge depth inside a 96dp zone,
   from a 19px/tick base at the very edge.
2. **No acceleration over time** — the loop now holds a tick counter and
   ramps to ×2.1 over ~0.9s of continuous edge-holding (≈2,500px/s at full
   hold — fast enough to cross a long Available section in a second or two).
3. **The velocity-keyed `LaunchedEffect` restarted on every finger twitch**
   (each new velocity value cancelled and relaunched the loop — choppy
   timing), and **hit-testing only ran on drag EVENTS**, so rows scrolling
   under a STATIONARY finger at the edge were skipped. The loop is now keyed
   on the drag SESSION (the anchor), reads the finger's last Y from state
   each tick, and hit-tests + range-selects EVERY TICK while it scrolls.
   The physical viewport bounds come from `onSizeChanged` on the list itself
   (`layoutInfo.viewportEndOffset`'s relationship to bottom content padding
   made the old bottom-edge math unreliable — with 210dp of selection-mode
   bottom padding the old comparison could misjudge the zone entirely).

The callbacks and the key set ride `rememberUpdatedState`, so a mid-drag
list mutation (an install completing) ranges over the CURRENT keys, and the
gesture detector is no longer restarted by key-set changes.

## 3. The install deadlock fix (D-649)

**Root cause:** round 93 wrapped the whole single-install flow in
`installMutex.withLock { download…; dispatchAndAwaitInstall(…) }` — but
`dispatchAndAwaitInstall` ITSELF does `installMutex.withLock`. Kotlin's
`Mutex` is NOT reentrant: the inner lock waits forever on the outer one the
same coroutine already holds. The download completed, the row froze at 100%,
the system prompt never dispatched ("after downloading they did not show me
the install prompt"), and — because the mutex stayed held FOREVER — every
subsequent install tap silently queued behind it with no state, no ring, no
feedback ("unable to click them"; each new tap just stacked another blocked
coroutine). The batch path alone escaped (its phase 2 calls
`dispatchAndAwaitInstall` without holding the lock), which is why the bug
read as "single taps broke" in the report.

**The fix — downloads FREE, prompts SERIALIZED:**
- `installExtension` no longer touches the mutex. It sets `Pending`
  IMMEDIATELY at collection (with the double-tap guard re-checked at
  collection start — two rapid taps used to both pass the call-time check
  and double-download onto the same deterministic temp file), downloads in
  parallel with every other install, then hands off to
  `dispatchAndAwaitInstall`.
- `dispatchAndAwaitInstall` sets `Installing` BEFORE the mutex wait — a row
  queued behind another package's prompt pulses "Installing" instead of
  freezing on a full download ring (the queue is VISIBLE), then the mutex
  guarantees exactly ONE awaited system prompt at a time.
- Cancellation hygiene: if the collector dies while downloading or queued
  (prompt never dispatched), the downloaded temp APK is deleted on the spot
  (the page-exit sweep only runs on navigation); if it dies while the prompt
  is on screen, the file is left for the system to settle.
- `setInstallState` switched to atomic `MutableStateFlow.update` — with
  downloads now genuinely parallel, the old read-modify-write
  `value = value + …` could clobber a concurrent sibling's entry.

Net behavior, exactly as the user specified: every tapped row immediately
shows its own queue/download state ("they should be queued to download"),
downloads overlap freely, and the system prompts arrive strictly one at a
time, each awaited before the next fires.

## 4. The NSFW tri-state (D-650)

"One shared filters bar" now means one shared NSFW MODE: **off** (the
default — every NSFW row hidden) / **on** (everything) / **only** (just the
NSFW rows). The filters pill cycles the three on tap (VisibilityOff /
Visibility / Explicit icons, "NSFW off" / "NSFW on" / "NSFW only" labels)
and every change writes straight to the new
`AppPreferences.extensionsNsfwMode`; the page reads it on every entry, so it
"will remember the last state it was on" across visits, app restarts
included. The old split (a session-local aniyomi toggle defaulting ON + the
persisted CloudStream `cloudstreamShowNsfw` gate defaulting OFF) is retired —
note this CHANGES the aniyomi tab's default from show-NSFW to hide-NSFW,
per the report's "The default state should be NSFW off."

The enum (`NsfwFilterMode` + its `raw` form and `passes(isNsfw)`) lives in
`core/preferences` as public API, because the search screen's CloudStream
source picker — whose gate comment literally said "the only way the gate can
change is via the Extensions settings screen" — follows the SAME tri-state
now (off hides NSFW sources, on shows all, only keeps just the NSFW ones):
one NSFW doctrine across the app, and the retired boolean's stored value is
simply orphaned (debug-first, §30 — no migration).

## 5. The multi-field search (D-651)

`matchesExtensionSearch(query, name, lang, version)` — one
case-insensitive substring pass over the row's NAME + LANGUAGE + VERSION:
typing `14` finds the version-14 extensions, `fr`/`en` finds the language
tags. Wired into all four aniyomi sections and all four CloudStream
sections (the CS side searches `name + language + version.toString()`; the
available-rows search the wrapped `SitePlugin`'s fields). The testing
screen's target list keeps the name-only `matchesSearch` — its rows carry
no meaningful language/version.

## 6. The bars-only stage timings (D-652)

The v1.1.50 report on the full-details page: "all the stage timings were
shown at the top, all together combined in a list view, which was not
good… only the bars will be shown in parallel to each other, very close to
each other, just a slight padding on them with each other… There won't be
any text to them, nothing, only bars." The card's "Stage timings" title,
the per-stage dot + label + duration line, and the caption are all GONE —
`StageTimingBars` renders one 7dp rounded bar per STARTED stage, stacked
with a 3dp hair between them, failed bars dimmed to 55% alpha and skipped
bars to 30% (the verdict still reads at a glance without a word). The
D-637 motion survives intact: a bar reveals (expand+fade) the moment its
stage starts, the running bar GROWS LIVE (150ms ticker), earlier bars
RENORMALIZE (animated 350ms) when a new maximum lands, never-run stages
render nothing, and an untested target shows the one quiet "Nothing timed
yet" line.

## 7. What this round did NOT touch

- The Link Sources sheet (frozen since round 92 — still perfect).
- The two-row selection bar's layout (D-640 — only its key-space inputs
  changed), the uninstall chains, the page-exit hook, the CS wave-delete,
  the testing logic rules, and the run-all target picker (still FUTURE).
- The batch install manager flow (`installExtensionsBatch`) — its phase
  structure already matched the new contract; it simply shares the fixed
  `dispatchAndAwaitInstall`.

## 8. Verification

- Brace/paren balance on all eight touched files (all zero).
- Module-wide greps: no references to `StageTimingRows`,
  `AUTO_SCROLL_STEP_PX`, `selectedPkgs`, `selectedNames`, `showNsfw`,
  `csShowNsfw`, or `cloudstreamShowNsfw` remain (doc-comment mentions
  only); `matchesSearch` survives exactly where it should (the testing
  target list).
- The one external `installExtension` caller (AniyomiExtensionProvider)
  compiles unchanged against the same signature.
- `SitePlugin.version` confirmed already used elsewhere in the CS manager
  (the available-row search's `it.plugin.version.toString()` compiles).
- CI: Build APK run **36320244240** on `ed0c179f` **GREEN on the FIRST
  run** — inside the D-472 ≤2 budget.

## 9. The device-round checklist (v1.1.51)

**Extensions — selection (the round-93 regression):**
- [ ] Long-press ANY row on the Aniyomi tab: the selection opens (haptic +
      check bubble + the two-row bar). Repeat on the CloudStream tab.
- [ ] Long-press row A, then long-press row B further down: everything in
      between selects.
- [ ] Long-press and DRAG: rows join as the finger crosses them.
- [ ] Drag into the top/bottom edge and HOLD: the list auto-scrolls — it
      starts gentle, ACCELERATES the longer you hold (about a second to
      full speed), and the rows scrolling under your STATIONARY finger keep
      joining the selection.
- [ ] Select all still selects every row on the tab; the count never clips.

**Extensions — installs (the round-93 regression):**
- [ ] Tap Download on one available extension: it downloads, then the
      system install prompt appears (no more frozen 100% ring).
- [ ] Tap Download on SEVERAL extensions one after another: every tapped
      row shows its own progress immediately (they download in parallel),
      and the system prompts come strictly ONE AT A TIME — answering each
      brings the next; a row waiting for its turn pulses "Installing".
- [ ] Leaving the page mid-download resets the rows and sweeps the temp
      APKs (unchanged from round 93).

**Extensions — filters:**
- [ ] The NSFW pill cycles off → on → only → off; OFF is the default on a
      fresh install; ONLY shows just the NSFW rows.
- [ ] Leave the page and come back (or restart the app): the NSFW mode is
      exactly where you left it — on BOTH tabs.
- [ ] Search `14` → the version-14 extensions appear; search `en` / `fr` →
      the language-tagged rows appear; names still match as before.

**Testing — full details:**
- [ ] The stage timings card is BARS ONLY — no title, no labels, no
      durations, no caption; thin bars packed close together.
- [ ] Run the tests: the bars reveal one by one as each stage starts, the
      running one grows live, and earlier ones rescale when a longer stage
      lands.
