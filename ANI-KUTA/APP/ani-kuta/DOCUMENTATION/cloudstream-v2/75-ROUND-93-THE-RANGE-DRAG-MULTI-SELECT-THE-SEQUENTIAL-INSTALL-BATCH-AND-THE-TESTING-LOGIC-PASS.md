# ROUND 93 — THE RANGE/DRAG MULTI-SELECT + THE SEQUENTIAL INSTALL BATCH + THE ALPHABETICAL ORDER + THE TESTING LOGIC PASS

> The v1.1.49 device round. The Link Sources sheet was reported PERFECT and is
> untouched. The work split into three fronts: the extensions page's
> multi-select and batch operations (the bulk of the report), the page-exit
> hygiene, and the extension-testing system's logic + full-details pass.
> Implementation commit: `c7354ca2`.

---

## 0. The round in one paragraph

The user's v1.1.49 report accepted the Link Sources sheet wholesale and
focused on the extensions page: multi-select lacked range/drag selection and
its bottom bar clipped, the aniyomi batch install re-fired the system prompt
per completed download ("the exact same pop-up again and again, even while
Android was already installing"), the CloudStream batch delete collapsed all
rows at once and installs never animated their move to Untrusted, sorting had
to go entirely ("just handle it in alphabetical order everywhere"), and
leaving the page left rows stuck on downloading/installing until the app was
killed. On the testing side: a ping failure must fail the whole chain by
default, a search failure with a working home page must still count as a
working extension (the chain continues from home), the search ladder must try
exactly three phrases in order, and the full-details page's "glowing blocks"
and static stage-timings strip needed the professional treatment. All of it
landed as D-640..D-647.

## 1. The multi-select rework (D-640 + D-641)

**The root bug (D-641):** a second long-press called `enterSelection(pkg)`
again, which RESET `selectedPkgs` to just that row — the selection "moved"
instead of ranging. The fix is a **range anchor** (`selectionAnchor`) plus a
flattened **selectable key order** (every section's row keys in visual
order): a long-press inside selection mode now selects anchor…row inclusive,
and the anchor moves to the new row (the modern-UI pattern the user asked
for).

**The drag-paint (D-641):** one shared `rememberDragSelectionModifier`
(ExtensionListChrome.kt) rides each tab's LazyColumn and OWNS the long-press
for the whole list — `detectDragGesturesAfterLongPress` resolves the row
under the finger via `layoutInfo.visibleItemsInfo` (with a
last-selectable-at-or-above fallback for header gaps), paints the anchor→row
range as the finger crosses rows, and **auto-scrolls** near the viewport
edges (a velocity state + a 16ms `scrollBy` loop, speed scaling with edge
proximity). The rows themselves are now plain `clickable` — taps toggle/open,
and the parent consumes the moves so no tap fires after a drag.

**The two-row bar (D-640):** the old single-row bar put the X, the count and
up to four action pills on one line — the count got cut off and the buttons
crowded. The bar is now two rows: a STATUS row (X + the count/batch progress
with the full width + a **Select all** pill while selecting) over a hairline
over an ACTION row whose pills are **weight-filled** (`Modifier.weight(1f)`)
— Install / Trust / Untrust / Delete all fit at once, and a two-action
selection stretches them evenly.

## 2. The sequential install batch — the repeat-prompt fix (D-642)

**Root cause:** `ExtensionInstaller.downloadAndInstall` completed its flow
the moment the install SERVICE was dispatched — "dispatched" is not
"answered". The round-92 batch loop therefore fired every system prompt
back-to-back as each download landed.

**The fix:** the manager now keeps a `pendingInstallResults` map
(pkg → `CompletableDeferred<InstallStep>`); `onInstallResult` (the service's
terminal report) completes it. `installExtension` (single) now awaits the
answer (5-minute safety timeout) before completing — and holds the install
mutex throughout, so even multiple single-row taps can never stack prompts.
The new `installExtensionsBatch` runs **downloads in parallel** (the user's
explicit allowance) and then dispatches **strictly one prompt at a time**,
awaiting each answer: Installed → next; Error/timeout → toasted with the
name and the batch continues; **a denied prompt stops the batch** (cancel
means cancel). Cancellation (the bar's X, leaving the page) deletes every
downloaded-but-undispatched file and resets the untouched rows. The bar
carries "Installing n/N…" with the X while it runs. The installer was split
into `downloadToTemp` (parallel-safe, deletes partials on cancellation) +
`dispatchInstall` (+ a dispatched-file registry so the sweep never deletes a
file the service may still be reading).

## 3. The faster uninstall chain + the page-exit hygiene (D-643)

The chained system-uninstall batch advanced on the manager's flows re-scan —
~a second of PackageManager queries per removal. It now advances on the
**ACTION_PACKAGE_REMOVED broadcast itself** (the screen's ghost receiver),
so the next prompt fires the instant the previous uninstall lands; the
flows-watcher stays as the fallback. (Android stacks simultaneous
ACTION_DELETE prompts anyway — sequential-with-broadcast-advance keeps the
progress bar honest while reading just as fast.)

**The exit hook:** the screen's `onDispose` now cancels the install batch,
drops the uninstall queue, cancels the CS side's in-flight install jobs
(`cancelActiveInstalls` — the manager tracks its install Jobs now), resets
every still-visible downloading/installing row, and sweeps abandoned temp
APKs (>60s old, never dispatched). No more killing the app to clear the
page. Disclosed honestly: a system prompt that is CURRENTLY on screen is
system UI — the app cannot dismiss it; if the user answers it anyway, the
install lands normally.

## 4. The CloudStream animations (D-645)

The batch delete now runs **one row at a time**: each row enters the
`exitingNames` set, plays the shared exit choreography (settle dip →
slide + fade, ~350ms), and only then does its uninstall fire (the loop
staggers 370ms + 140ms settle). An install completing on an available row
plays the same exit — the "Done" beat reads first, then the row slides out
as the list refresh moves it into Untrusted (the manager's completion beat
rose 700 → 850ms to give the motion its window). Trust/Untrust batches keep
their existing stagger.

## 5. Sorting removed entirely (D-644)

The sort pill + its menu, `ExtensionSortMode`, `sortExtensions`, the CS sort
twins, the manual reorder mode (the header's SwapVert pill, the
`reorderedInstalled` shadow list, the row arrows) — all gone. Every section
on both tabs sorts **alphabetically by name** (case-insensitive; the
enabled-first tiebreak retired with it — "alphabetical everywhere").
`ExtensionReorderList.kt` stays (AutoLink's screen uses it).

## 6. The testing logic (D-646)

- **PING is a hard prerequisite** — `HomePageTest`/`SearchTest` now
  `requiresAnyOf = setOf(PING)`; a failed ping fails HOME_PAGE and SEARCH
  ("Not run — Ping failed") and the gate cascades through DETAILS →
  EPISODE_LIST → VIDEO_RESOLVE → STREAM_PLAY. Exactly the user's rule.
- **The SEARCH forgiveness** — mirroring the round-86 DETAILS forgiveness: a
  FAILED search with a PASSED home page is rewritten at chain end to
  SKIPPED "Forgiven — continued from the home page". The chain already
  continued from the home pool (round-85 candidate pools); now the VERDICT
  is honest too — a home-page-working extension with broken search reads as
  healthy when the rest passes.
- **The phrase ladder is exactly three**, in order: Jujutsu Kaisen (Anime) →
  Interstellar (Movie) → Link Click (Donghua) — first hit wins, the rest are
  skipped, all-fail fails. (The movie/donghua picks are the ladder's
  established phrases; Breaking Bad retired with the trim.)

## 7. The full-details professional pass (D-647)

- **The result cards lost their "glowing" washes**: neutral `surface` cards
  with a hairline border and a thin kind-colored LEADING EDGE (the transient
  banner's language); the kind's hue survives only in the header dot + that
  edge. The payload inset is a neutral `surfaceVariant` wash.
- **The stage timings play the live run's motion** (the twin of the run
  screen's D-637 machine): one row per STARTED stage (expand+fade reveal),
  each with a TIME BAR sized against the longest stage shown; the running
  bar grows live (150ms ticker) and earlier bars renormalize (animated) when
  a new maximum lands. Stages that never ran render nothing — the old
  equal-placeholder segmented strip (`MultiStageProgressBar`) is gone; an
  untested target shows the honest "Nothing timed yet" line.

## 8. What this round did NOT touch

The Link Sources sheet (reported perfect — frozen), the run screen's hero
(D-637, explicitly praised), the suite-health ring, the stats screen, the
AutoLink reorder list, and every surface outside the extensions/testing
features.

## 9. Verification

- Brace/paren balance on all eleven edited files (the engine file's -4 is a
  pre-existing counter artifact of quoted text in comments — identical on
  the CI-green original).
- Module-wide greps: no references to `ExtensionSortMode`, `sortExtensions`,
  `sortCs*`, `MultiStageProgressBar`, `STAGE_NOMINAL_MS`,
  `downloadAndInstall`, `reorderMode`, `reorderedInstalled`, `isReordering`,
  `HeaderIconButton`, or row-level `onLongPress` remain (doc-comment
  mentions only). `ExtensionReorderList`'s only remaining caller is
  AutoLinkSettingsScreen, which is untouched.
- The one external `installExtension` caller (AniyomiExtensionProvider,
  its own long-lived scope) compiles unchanged against the same signature —
  it simply awaits the prompt's answer now, which is the correct behavior
  for a programmatic install too.
- CI: Build APK run **36315107156** on `c7354ca2` (see §10 for the honest
  run history).

## 10. The device-round checklist (v1.1.50)

**Extensions — selection:**
- [ ] Long-press one row, then long-press another further down: everything
      in between selects (the anchor moves to the new row).
- [ ] Long-press and DRAG up/down: rows join as the finger crosses them;
      near the top/bottom edge the list auto-scrolls and the paint continues.
- [ ] The bar: the count is on its own line (never cut off); Select all
      selects every row on the tab; all four buttons (Install / Trust /
      Untrust / Delete) fit on one row when the selection is mixed.
- [ ] Both tabs behave identically.

**Extensions — batches:**
- [ ] Aniyomi Install on 4 extensions: downloads may overlap, but the system
      prompts come ONE at a time — answering each (Install) brings the next;
      denying one STOPS the batch; a failed install toasts the name and the
      batch continues; the bar shows "Installing n/N…" and X stops it.
- [ ] Aniyomi Delete: the next system prompt appears the instant the
      previous uninstall lands (no ~1s pause between prompts).
- [ ] CloudStream Delete: rows leave ONE AT A TIME with the slide-out, not a
      single collapse.
- [ ] CloudStream Install: when a plugin finishes, its available row slides
      out and it appears in Untrusted.
- [ ] Leave the page with a download/install in flight: everything cancels,
      rows are clean on return, no temp files linger (a system prompt
      already on screen can still be answered — it completes).

**Extensions — order + filters:**
- [ ] No sort pill, no reorder pill; every section alphabetical on both
      tabs; search/language/NSFW filters still work.

**Testing:**
- [ ] An extension whose site is dead: PING fails and every other test
      reads FAILED ("Not run — Ping failed").
- [ ] An extension with a broken search but a working home page: the chain
      continues from home, the SEARCH row reads "Forgiven — continued from
      the home page", and the extension counts as healthy when the rest
      passes.
- [ ] The search ladder: first phrase hit wins (check the attempts pill).
- [ ] The full-details page: neutral professional result cards (no color
      washes); the stage timings appear as each test starts, bars sized
      against the longest so far, the running one growing live.
