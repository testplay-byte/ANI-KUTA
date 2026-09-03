# 19 — DEVICE ROUND 28 (Task 68): THE v0.4.15 FINDINGS + THE ONBOARDING WIZARD

> Plan doc. Branch: `streaming/CLOUDSTREAM-V2`. Baseline: v0.4.15/80
> (round 27 shipped, CI green 33678931661 + 33679756635, Release live).
> The round-27 device round CONFIRMED: the delete button's 2.5x grow direction
> + function, the CS browse loader's reliability, the update-check history's
> correctness (next-release projection, learned delay, calculated, landed,
> next-recheck — "showing me the detailed info of it properly"), and the
> details of the update system. Do not regress any of those.
> This round fixes the five findings + ships the requested onboarding wizard.

## The device report (v0.4.15)

1. **Delete button animation**: it grows (right direction) but "the animation
   was apparently kind of not smooth and it was not handled that well."
2. **Armed state not dismissible**: "if I tapped anywhere OUTSIDE the delete
   button when it is in a bigger size, it apparently does not go away to its
   original state."
3. **Multi-episode deletion is broken** (the big one): with ONE episode the
   delete is perfect (file + folder + data.json all cleaned). With TWO OR
   MORE: deleting episodes one-by-one leaves the `.data.json` STALE ("it was
   still showing me that the episode was downloaded… the data.json file was
   not updated"), and on the LAST episode the FILE + the folder also survive
   ("the folder sometimes did not get deleted"). User's explicit pipeline
   spec: "if the user clicks the delete button then it will update the
   data.json file of that specific content and then it will delete that
   content properly afterwards and handle each and every single thing
   properly." Robust, logged, clean architecture, error handling.
4. **Search top-bar reveal is INVERTED**: "If I try to scroll down, then it
   apparently hides. If I scroll up with my finger from bottom to up, then it
   shows. If I try to scroll to the very top again from the bottom, then it
   gets hidden again. You have reversed the working and the functionalities."
5. **Update-check history**: confirmed working + detailed — improvements
   DEFERRED by the user ("let's work on it later"). Not in this round.
6. **NEW FEATURE — the onboarding setup wizard**: replace the scattered
   first-launch prompts (folder + notifications + battery) with a dedicated
   wizard: a custom (non-Material) beautiful highly-animated welcome screen,
   a quick theme picker in the same flow, easy permission steps with real
   verification, and SKIPPABLE permissions (a skipped folder only breaks
   downloading, which then shows a clear "no download folder selected" error
   with an inline picker).

---

## Item 1 — the synchronized delete-button choreography (D-399)

**Root cause (why it wasn't smooth):** the round-27 grow was THREE
independent animations: `animateDpAsState` for the glyph size (220ms), a
SECOND `animateDpAsState` for the IconButton frame at each call site (220ms),
and an `AnimatedContent` cross-morph for the glyph identity (150ms). The
150ms morph finished EARLY — the DeleteForever glyph landed at small size,
then kept growing — reading as a two-stage stutter rather than one grow.

**Fix:** ONE animation progress drives everything. `armedProgress =
animateFloatAsState(if (armed) 1f else 0f, tween(260, Motion.EasingEmphasized))`
(the app's premium emphasized curve). From that single float:
- glyphSize = `idleIconSize + idleIconSize * (ARMED_ICON_GROWTH - 1) * progress`
  (16→40dp per row, 20→50dp delete-all — still exactly 2.5x);
- the IconButton frame = `32.dp + 16.dp * progress` / `36.dp + 20.dp * progress`;
- the glyph identity swap = a staggered crossfade driven by the SAME progress
  (idle glyph fades OUT over p∈[0, 0.625], armed glyph fades IN over
  p∈[0.375, 1]) — no `AnimatedContent`, no timing mismatch, plus a subtle
  0.7→1.0 armed-glyph scale for the "pop".

Layout-phase measured size is kept (D-397's clip/resolution fix) — this round
only synchronizes the choreography.

## Item 2 — tap-outside disarms the armed button (D-400)

**Root cause:** the armed state (`confirmDeleteKey`) lives PER-CARD. Taps on
the SAME card's rows/header disarm it, but taps on OTHER cards, the header,
or blank screen areas hit nothing that resets the state — so the armed
(grown) button just sits there.

**Fix (the standard ancestor-interceptor pattern):** hoist the armed state
to the SCREEN level (`ArmedDelete(cardId, targetKey)` — one armed button in
the whole list at a time) and:
- the armed delete buttons report their bounds in WINDOW space
  (`onGloballyPositioned` → `boundsInWindow()` into a shared `Rect?`);
- the screen content Box installs ONE `pointerInput(armedDelete)` gesture
  observer: on every DOWN, if the touch point (converted to window space via
  the Box's own `positionInWindow()`) is OUTSIDE the armed button's rect →
  disarm. Taps ON the button hit its own clickable (the interceptor stays
  silent); taps elsewhere — other rows, other cards, the back header, blank
  space, even the start of a scroll — disarm instantly. Children keep
  priority (the interceptor only observes DOWNs, consumes nothing).

In-card guards (row tap disarms-and-plays) are kept — they now route through
the hoisted state.

## Item 3 — the data.json-first deletion pipeline (D-401, THE BIG ONE)

**Root causes (three, compounding):**
1. **ORDER**: files were deleted FIRST (Phase 2a URI deletes + 2b disk sweep),
   the `.data.json` updated AFTER (Phase 3). Sibling deletions invalidate SAF
   DocumentFile URIs on some providers — the very window in which the
   `.data.json` write runs. The single-episode case only LOOKED clean because
   the last-episode folder delete removes the whole `.data.json` (the entry
   write never has to succeed). With 2+ episodes every intermediate delete
   depends on that fragile write.
2. **NO SERIALIZATION**: `deleteDownloadedEpisode` has no mutex
   (the scanner needed one for the same read-modify-write hazard). Two
   in-flight deletes each read the full episodes list before the other's
   write lands → last-writer-wins leaves deleted episodes in `.data.json`,
   and BOTH compute `dbRemaining = count - 1 > 0` → NEITHER triggers the
   last-episode folder cleanup (the surviving husk folder).
3. **SILENT-SUCCESS HOLES in the removal ladder**: (a) a key mismatch
   returned `true` (idempotent) WITHOUT touching the file; (b) the write
   verification treated a NULL re-read as VERIFIED. Both make a failed write
   look like success while Phase 5 still kills the DB row.

**Fix — the pipeline the user specified, in that order, serialized, verified:**
- `DefaultDownloadManager` gains a `deleteMutex`: every public delete
  (episode + anime) is serialized; the delete-all fallback loop calls the
  PUBLIC per-episode delete (lock acquired per call — no reentrancy).
- `DownloadStorageProvider` gains a `treeMutex` serializing ALL its tree
  mutations (`.data.json` writes: `writeDataJson`/`upsert`/`remove`/
  `replaceEpisodesInDataJson`; + `deleteContentFolder` +
  `deleteEpisodeFilesOnDisk`) — download completions, scanner reconciles,
  and deletes can never interleave a read-modify-write again.
  (No nesting inversions: deleteMutex→treeMutex and scanMutex→treeMutex are
  the only orders — no cycles.)
- **NEW PHASE ORDER for `deleteDownloadedEpisode`:**
  1. DB capture (episodeNumber + the row's videoUri) — before anything mutates;
  2. locate the content folder;
  3. capture the `.data.json` entry (URIs) — pre-mutation snapshot;
  4. **UPDATE + VERIFY the `.data.json` FIRST** — `removeEpisodeFromDataJson`
     now takes the episodeNumber too: match by key, fall back to
     number-drift reconciliation (the scanner rebuilds entries under
     number-matching, so keys can drift). STRICT verify: the re-read must be
     NON-NULL and must contain NONE of the removed entries (by key OR
     number). The 3-attempt ladder (fresh-index retry → nuclear
     delete-recreate) stays as defense-in-depth, but the write now happens
     BEFORE any file deletion — the stale-URI window is gone by design;
  5. delete the episode's files (the captured URI deletes, best-effort) +
     the disk-truth sweep (`deleteEpisodeFilesOnDisk`, number-token match,
     survivors report);
  6. the series-folder cleanup decision (DB-driven `dbRemaining == 0` — now
     accurate under the mutex);
  7. DB row delete + cache refresh (LAST — the UI reflects reality only
     after the disk is consistent).
- Pure decision logic extracted to `DeletionMatching` (key/number matching +
  strict verification verdict) — unit-tested (core:download's first test
  source set).
- The "content folder NOT FOUND" path + all phase failures keep their
  honest, loud ERROR logs (the round-27 discipline).

## Item 4 — the search top-bar reveal sign fix (D-402)

**Root cause (proven from the pinned Compose/M3 sources):** in Compose
nested scrolling, `available.y > 0` is the FINGER moving DOWN (content
toward the top / the P2R pull — material3's own `PullToRefresh` grows its
indicator on POSITIVE y, commented "Swiping down"). The round-27 latch read
it as "scroll position delta" and got it EXACTLY BACKWARDS:
finger-down → hide (should reveal), finger-up → reveal (should hide), and
arriving at the very top (only reachable via finger-down deltas / P2R pulls)
latched the bar HIDDEN — every symptom in the report, 1:1.

**Fix:**
- the two branches swap: `dy > +THRESHOLD` (finger down, toward top) →
  reveal; `dy < -THRESHOLD` (finger up, into the content) → hide;
- a NEW at-top force-reveal: a `derivedStateOf` on the ACTIVE mode's scroll
  state (`scrollState.value == 0` for Idle, `firstVisibleItemIndex == 0 &&
  firstVisibleItemScrollOffset == 0` for the grids/browse list, always-true
  for static states) — at the top the bar is ALWAYS visible, belt and
  braces with the corrected latch;
- the mode-transition re-reveal (LaunchedEffect on `uiState::class`) stays;
- the decision extracted to a PURE function `searchBarNextCollapsed(
  current, dy, atTop, threshold)` — unit-tested in the feature module's new
  test source set (all gesture directions × at-top × dead-zone);
- the P2R interaction is now correct by construction: a pull at the top
  grows the indicator while the bar STAYS visible (at-top force-reveal),
  where the old code hid it.

## Item 5 — the onboarding setup wizard (D-403, new feature)

**Today's behavior:** `FirstRunSetupDialog` (a plain M3 AlertDialog) fires
on EVERY launch while any of the three (notifications / folder / battery)
is unmet — the user's exact complaint ("All of that is good but it is not
handled properly").

**The new `feature:onboarding` module** (api + impl, the cs-watch pattern):
- **Welcome step** — a custom, non-Material animated screen: a time-driven
  aurora Canvas (soft radial blobs drifting on `rememberInfiniteTransition`,
  accent-seeded), a drifting particle field, the ANI-KUTA wordmark revealing
  letter-by-letter with staggered slide+fade, a gradient glow CTA. No
  Material components in the visual core.
- **Theme step** — quick curated theme cards (System / Midnight / Daylight /
  AMOLED / Dusk / Night / Teal light…) passed in from MainActivity as
  display data + a selection callback (the app-side `ThemePreferences`
  stays in :app — no type leakage). Selection applies LIVE: the whole app
  re-themes instantly (CORE_RULES §23).
- **Storage step** — SAF folder pick + REAL verification
  (`DocumentFile.fromTreeUri(...)?.canWrite()`); shows the folder name +
  a Verified chip. Skippable ("pick it when you download").
- **Notifications step** — POST_NOTIFICATIONS request (Android 13+;
  pre-13 auto-verifies) + `checkSelfPermission` verification chip. Skippable.
- **Battery step** — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (with the
  settings fallback) + `isIgnoringBatteryOptimizations` verification;
  ON_RESUME re-verification (the user leaves to system settings and comes
  back). Skippable.
- **Finish step** — a summary checklist (verified / skipped per step) +
  "Start watching".
- Step transitions: direction-aware slide+fade (`AnimatedContent`,
  emphasized easing); a segmented progress bar; back navigation between
  steps; wizard is excluded from the bottom nav + the update sheet.
- **Gating**: `AppPreferences.onboardingCompleted` (the lastTab pattern);
  AppRoot's start destination becomes `OnboardingKey` when it's false;
  `FirstRunSetupDialog` is REMOVED (file deleted) — no more every-launch
  nagging; the startup update-check is suppressed during onboarding.
- **The deferred-folder contract**: starting a download (classic path, CS
  path, specific-video path) with no (or dead) download folder shows a clear
  "No download folder selected" dialog with an inline folder picker; picking
  a folder RETRIES the download automatically. The folder check is
  `DocumentFile.fromTreeUri(...)?.canWrite()` (a local, main-thread-safe
  permission check).

## Non-goals / deferred

- Update-check history robustness improvements (user: "let's work on it
  later").
- Any change to the aniyomi stack, the CS browse loader, the watch screens,
  the DB schema (`.data.json` gains no fields; the wizard flag lives in the
  existing SharedPreferences store).

## Execution order

A. Item 3 (D-401) — the deletion pipeline (highest priority, user-emphasized).
B. Items 1+2 (D-399/D-400) — the delete-button UX (one file).
C. Item 4 (D-402) — the search reveal sign fix + policy tests.
D. Item 5 (D-403) — the onboarding wizard + the no-folder gate + the
   FirstRunSetupDialog removal.
E. Docs + version 0.4.16/81 + tag + release + 2 ntfy notifications.
