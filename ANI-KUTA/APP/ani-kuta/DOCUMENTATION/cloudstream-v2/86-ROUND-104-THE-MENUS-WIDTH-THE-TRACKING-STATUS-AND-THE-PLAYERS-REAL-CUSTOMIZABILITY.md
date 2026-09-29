# Round 104 — The Menu's Width, The Tracking Status, The Unstoppable Scroll, And The Player List's Real Customizability

> Round record 86. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The device round (v1.1.60)

The user's report, decomposed into the four work streams this round executes:

1. **THE THREE-DOT MENU, width + color** — the crimped structure is approved but:
   - "it is way too wide, even more wider than it should actually be. So I would
     like you to reduce its width and I would like you to make it a bit better."
   - "the coloring is not proper, like the background color and the color for
     the buttons is not proper. Like the background color is a bit on the
     lighter side, while the actual buttons are on the darker side, which is
     apparently not a good idea."
   - "the UI of the tracking button is looking a bit different, and it is not
     looking that proper."
2. **THE TRACKING SHEET, status + feel** — the contextual buttons are approved
   ("happy with the bottom buttons… if I just click Save, then it does not start
   tracking… when I click Start Tracking, then it loads, and then the tracking
   status changes properly… the unlinking functionality was apparently working
   properly too"). The orders:
   - A SYNC-STATUS TEXT at the top, "just left of the delete button, the trash
     can icon" — "like currently not syncing tracking, or it should say syncing
     tracking synced. But this is just a general idea, but you need to decide on
     what it should actually display, how it should actually display."
   - The three selectors' width: "it is taking up almost all the width, but it
     should be handled properly. It should take almost half of the device's
     width, like it should calculate that and handle it properly as such."
   - "implement properly, like vibrations and other kinds of effects for it
     properly."
   - The sheet's height: "the height could be improved a little bit too… you
     can increase the height of it a little bit more. Not too much, but just
     slightly more."
   - TOASTS: Start Tracking → "it should properly load everything up and handle
     the things properly, and after doing that it should show me a toast
     notification, a beautiful toast notification, saying that tracking
     started". Remove From Tracking → "it should properly give me the details,
     like no longer tracking."
   - The Remove confirmation copy: "properly formatted with proper line
     breaking where needed and everything managed properly."
3. **THE SCROLL** — "it was not properly scrolling to the appropriate episode…
   it just scrolls slightly, but then it stops. Like it should smoothly scroll,
   and it should keep on scrolling to that point when it reaches that specific
   episode only then should it stop, or until unless the user interrupts it by
   manually trying to change things or such." Plus: "after scrolling to that
   area… add the effect of highlighting, like it will highlight that specific
   episode a bit and then just change the things to the normal ones."
4. **THE PLAYER EPISODE LIST, the real customizability** — the round-103 four
   paradigms were "still not good and quite limited":
   - The WATCHED FILTER (Off / Show watched / Hide watched) "is most definitely
     not needed, and it is apparently unnecessary… It should be completely
     removed."
   - The preview's scrolling: "Just like how it is being handled properly for
     the episode list page, it is not being handled properly here. I want the
     same kind of effect, same scrolling, the same logics and everything like
     that to be exactly the same. Like if I scroll that, then one of them should
     get hidden and the other one should snap properly, and same goes for the
     scrolling up too."
   - Keep the normal + detailed layouts with the synopsis / date-pill / dim-
     watched options — "apparently on the details one, it does not have any
     functionality properly implemented for that, for dimming the watched
     episodes."
   - The COMPACT layout "is just trash. It is not good. I want you to
     completely remove it, and instead of this layout I would like you to
     redesign a completely new, proper, beautiful, good-looking layout from
     scratch."
   - The episode tags on the thumbnails' top-left: "I don't like them, so
     remove them. It should only be kept in the detailed view."
   - The GRID: "somewhat satisfied… but apparently it is not proper either. It
     does not look good… things need to be handled better." The options below
     "should properly adjust accordingly and should disappear or appear smoothly
     depending on what's available to edit."
   - The BANNER: "satisfied… but same issues, like a lot of the things do not
     get shown properly… the episode number is not shown properly, it is not
     customizable… add a density slider too, like I can select what the size of
     them should be easily, and it would properly show in live view."
   - SWIPE on the player list: "there should be the swipe functionality too,
     exactly like how it is on the details page."

## 1. Root-cause research (pre-implementation, verified against HEAD 80ac4dad)

- **The menu (DetailsActionMenu.kt, 524 lines)**: the content Column is
  `.width(300.dp)` on BOTH pages. The panel wears `containerColor = surface`
  + `tonalElevation = 6.dp` — with an explicit containerColor the tonal
  overlay is computed but the panel reads essentially as flat `surface`, while
  the section cards sit on `surfaceVariant.copy(0.32f)` — in LIGHT theme that
  overlay is DARKER than the panel (the user's exact complaint: "background…
  lighter side… buttons… darker side"); in dark it lands nearly same-color
  (invisible hierarchy). The Tracking row double-insets (`padding(6.dp)` then
  its own rounded wash) — a pill INSIDE the card, geometry no other row has.
- **The sheet (TrackSheet.kt, 907 lines)**: the top Box holds ONLY the trash
  IconButton; the pickers' wheel Surface is `.fillMaxWidth()` (the width
  complaint); `PickerCell` clicks carry no haptic; the confirm dialogs render
  one long run-on string. The sheet's `heightIn(max = 0.85×screen)` is only a
  cap — the CONTENT height is the felt height.
- **The scroll (WatchScreen.kt:2078 / CsWatchPage.kt:428)**: BOTH "Scroll to
  Current" handlers declare `val scrollScope = rememberCoroutineScope()` —
  INSIDE the LazyColumn's HEADER ITEM. The moment the glide starts, the header
  scrolls out of the composed window → the item is disposed → the scope's
  jobs are CANCELLED → the glide dies almost immediately: the exact "it just
  scrolls slightly, but then it stops" the user reported. (The round-103
  scroller itself is sound — bounded, clamped, snap-free — the bug is at the
  call sites.) User interruption ALREADY works by construction: `animateScrollBy`
  runs at `MutatePriority.Default`; a touch drag enters the scroll mutex at
  `UserInput` priority and cancels the animated scroll mid-glide.
- **The dim gap (PlayerEpisodeListLayouts.kt:219-235)**: the DETAILED row's
  watched treatment only swaps the card's background tint — the CONTENT (title,
  thumbnail, pills) stays full-strength. The details page's CLASSIC row (the
  reference) dims the WHOLE card (`graphicsLayer alpha 0.5`) + grayscales the
  thumbnail.
- **The layouts**: COMPACT is a shrunken DETAILED (the "not unique" verdict);
  the EP badge sits top-left on DETAILED + COMPACT thumbnails and top-start on
  GRID cells; GRID ignores showDatePill; BANNER's ghost number is fixed-size,
  untoggleable, and the aspect is hard-coded 16:9.
- **The settings screen (PlayerEpisodeListSettingsScreen.kt, 534 lines)**: the
  preview's collapse is a raw `derivedStateOf(hidePx)` (the D-556-era clip) —
  NO two-phase snap, NO direction-split priority scroll, NO fling handoff (the
  details page's D-557/D-558 machinery in EpisodeListSettingsScreen.kt:285-540
  is the approved reference). The Watched filter card + the preview's slot
  guards + both stacks' filter branches all ride `watchedFilter` (player-scoped
  ONLY — the details page's own filter is a different pref class and stays).
- **The swipe reference (EpisodeLayouts.kt:348-460)**: `SwipeToToggleWatched` —
  35%-of-screen threshold, ±1.5× clamp, `stageCross` on crossing,
  `releaseConfirm` + toggle on release, 300ms spring-back, the D-557
  rememberUpdatedState fix. It is `internal` to anime-details; the player needs
  its own copy in `:core:designsystem/playerlist/` (which already depends on
  `:core:common` — HapticHelper available).
- **Watched toggling**: `WatchProgressStore.toggleWatched(episodeKey)` exists
  (setUserMarkedWatched / setAutoMarkSuppressed under the hood). MPV key =
  `buildEpisodeKey(mainId, ep.episodeNumber)`; CS key =
  `CsWatchViewModel.episodeKey(mainId, displayNumber)` (the flavor ordinal) —
  both stacks already observe their watched state live.

## 2. The design decisions (pre-implementation)

- **D-700 (the menu's width + color)**: width 300→264dp on both pages. The
  hierarchy INVERTS to cards-brighter-than-panel in BOTH themes: dark →
  panel `surface`, cards `surfaceVariant` (a real lift); light → panel
  `surfaceContainerHigh` (clearly grey), cards `surface` (near-white) — the
  inset-grouped look, tonalElevation 0 (the explicit colors carry the tone).
  The unselected chip goes theme-aware (dark: `surfaceContainerHighest@0.4`;
  light: `surfaceContainer`). The Tracking row loses its outer 6dp inset —
  identical metrics to MenuDiscRow, the state wash INSIDE the row's own
  rounded rect; grey idle tone theme-aware (dark: `onSurface@4.5%`).
- **D-701 (the tracking status + toasts)**: a STATUS CHIP at the top-LEFT of
  the sheet (the trash keeps top-right): a dot + label pill whose four states
  derive from the existing params — `isSaving` → "Syncing…" (primary, a tiny
  spinner in place of the dot); `error` → "Not synced" (error tone);
  `isTracked` → "Synced with AniList" (the success green); else "Not
  tracking" (muted). The wheel centers at HALF THE SCREEN WIDTH (computed
  from `LocalConfiguration`). Picker open/close tick; the sheet gains a
  breathing pass (cells 16dp vpad, divider 20dp, bottom spacer 28dp). The
  confirm dialogs get bullet-line bodies. The ViewModel gains a one-shot
  `trackingNotice` (STARTED / STOPPED); DetailsScreen renders a custom toast
  host — a rounded pill, icon + message, slide+fade in, ~2.4s hold, fade out,
  haptic `releaseConfirm`/`stageCross` on show.
- **D-702 (the unstoppably honest scroll)**: the scope hoists OUT of the lazy
  items to the page-level composable in BOTH stacks (the root-cause fix —
  the glide survives the header's disposal). After a COMPLETED glide the
  page bumps an `arrivalPulse` token; the shared renderer's current
  row/cell/card plays a highlight overlay (a primary wash + ring at ~0.9
  strength fading to the normal treatment over ~1.1s). A user touch cancels
  the scroll coroutine BEFORE the token bump — an interrupted glide never
  pulses. Interruption itself needs no new code (the mutex priority).
- **D-703 (the player list's real customizability)**:
  - The watched filter is REMOVED COMPLETELY (pref + both stacks' branches +
    the settings card + the slot guards + the empty state).
  - COMPACT is REPLACED by TRACKLIST — a from-scratch typographic paradigm:
    the episode NUMBER as the hero (a fixed 52dp column, 25sp ExtraBold,
    ghost-muted idle / primary when current), a hairline divider, the title +
    pills to the right, the thin progress underline, swipe-to-toggle. The
    lenient migration folds COMPACT/MINIMAL → TRACKLIST (the slot's
    replacement inherits its users).
  - The DETAILED row's dim becomes the real one: whole-row `graphicsLayer`
    alpha 0.5 + grayscale thumbnail (the details page's CLASSIC treatment).
  - The EP TAG survives ONLY on the DETAILED row; the GRID cell loses its
    badge (the number moves into the bottom scrim: "EP 12" before the
    title); the BANNER keeps its ghost number (top-end, not a tag) and
    GAINS the controls: `showEpisodeNumber` toggle + `bannerDensity`
    (0..1 slider; aspect 21:9 → 4:3; default 0.5 ≈ today's 16:9) — all
    live in the preview.
  - The Elements card becomes STYLE-AWARE with animated rows
    (expandVertically/shrinkVertically): DETAILED = synopsis+date+dim;
    TRACKLIST = date+dim; GRID = dim; BANNER = date+dim+number+density.
  - The settings preview gains the details page's D-557/D-558 machinery
    VERBATIM (direction-split priority scroll, halfway snap, crossed latch,
    settle windows, fling handoff, GRID exemption) and the preview rows
    become SWIPEABLE through the same shared gesture (a local
    watchedOverride flip, the details preview's pattern).
  - `PlayerEpisodeSwipe.kt` (new, `:core:designsystem/playerlist/`): the
    swipe-to-toggle gesture, player-scoped twin of the details page's
    algebra; `PlayerEpisodeListEntry`/`PlayerEpisodeGridRow` gain an optional
    `onToggleWatched` (rows/banner wrap in the swipe; GRID long-presses) and
    the `arrivalPulse` token.

## 3. Execution order

Stream A (the menu) → Stream B (the tracking status + toasts) → Stream C (the
scroll fix + pulse) → Stream D (the player list's real customizability) →
self-review → the two guided sub-agent audits → CI → the ledger → v1.1.61 →
ntfy (topic **TASK808DONE** — the user's standing this-session override).

---
*(The execution record, the CI history, the judgment calls, and the round-105
checklist append below as the streams land.)*

---

## 4. The execution record

| Stream | Files | Commit |
|---|---|---|
| WS-A the menu | DetailsActionMenu.kt (width + hierarchy + chips + tracking row) | 8fa16554 |
| WS-B the tracking status + toasts | TrackSheet.kt (status chip + wheel width + haptics + breathing + bullet dialogs + TrackingToastHost), DetailsViewModel.kt (TrackingNotice + the two emit points + the types), DetailsScreen.kt (the toast host wiring) | 8fa16554 |
| WS-C the scroll | WatchScreen.kt + CsWatchPage.kt (the page-level scopes + the arrival pulse), PlayerEpisodeListLayouts.kt (the pulse overlays) | 8fa16554 |
| WS-D the player list | PlayerEpisodeListLayouts.kt (full rework: TRACKLIST + the real dim + the EP-tag exclusivity + the grid scrim number + the banner controls + the pulse + the swipe adapter), PlayerEpisodeSwipe.kt (NEW), PlayerEpisodeListPreferences.kt + PreferenceStore.kt (the filter removal + showEpisodeNumber/bannerDensity + FloatSerializer), WatchScreen.kt + CsWatchPage.kt (the filter removal + the swipe/long-press wiring + the banner knobs), PlayerEpisodeListSettingsScreen.kt (full rework: the D-557/D-558 port + the style-aware elements + the slider + the preview swipe) | 8fa16554 |
| The audit fixes | DetailsActionMenu.kt + TrackSheet.kt (the scheme-luminance polarity), TrackSheet.kt ("Tracking on"), DetailsViewModel.kt (the stale comment), PlayerEpisodeListSettingsScreen.kt (the import prune), PlayerEpisodeListLayouts.kt (TextAlign.End) | 87e0d8af |
| The CI fixes | PlayerEpisodeListLayouts.kt + PlayerEpisodeSwipe.kt (the matchParentSize member-extension imports + the BoxScope import) | f68af121 |

**THE JUDGMENT CALLS (disclosed):**
- **The status chip's green label says "Tracking on", not "Synced with AniList"** — the
  SA1-F2 audit finding: the chip reads the OPT-IN state, and the rare
  failed-sync-but-tracked case (plus the logged-out-but-tracked case) would have
  made "Synced" a lie. "Tracking on" states exactly what the flag says.
- **The polarity derives from the scheme's background LUMINANCE, not
  isSystemInDarkTheme()** — the SA1-F1 finding (MEDIUM): MainActivity's ThemeMode
  can force a theme opposite the system, which would have re-inverted the whole
  round's hierarchy on exactly those devices. The scheme IS what renders.
- **The wheel keeps its 168dp five-row viewport** (the link-sources parity the user
  approved) — the "slightly taller" order is answered by the status row + the
  breathing pass, not by stretching the approved wheel geometry.
- **The swipe wrapper OWNS the entry's outer padding** (SwipeableEntry applies
  padding(h10, v3/v4) on the wrapper) — the gesture + the background icon then
  cover exactly the card's visual footprint, the details page's structure.
- **The GRID's episode number lives in the bottom scrim's title line** ("EP 12 ·
  Title" in one ExtraBold+Bold line) — the number stays readable without any
  overlay tag on the thumbnail (the user's stated aversion).
- **The banner density maps 0f→21:9 … 1f→4:3** with 0.5≈16.5:9 — the default
  preserves the classic look byte-for-byte while the slider's two ends are the
  honest cinematic extremes; the live label snaps to the familiar ratios.
- **The preview's swipe flips a LOCAL watchedOverride** (the details preview's
  interactive pattern) rather than touching any store — the settings preview
  stays inert data-wise; the stacks' swipes write the real stores.
- **The KEY_WATCHED_FILTER tombstone stays** (SA2-F2, deliberate) — the same
  pattern as the round-103 KEY_SORT_MODE: a future re-add must not silently
  inherit a stale mode.

## 5. The CI history (the D-472 ledger — 2 runs, within budget)

1. **Run 36556911837 on 87e0d8af — FAILURE:** the member-extension import trap's
   MIRROR facet — `matchParentSize` imported as a top-level symbol in BOTH
   playerlist files (it is a BoxScope MEMBER extension: receiver-resolved, never
   importable), and the layouts file also missed the `BoxScope` import for
   ArrivalPulseOverlay's receiver. Two files, three errors, one class.
2. **Run 36557222112 on f68af121 — GREEN** (the two imports fixed; the error
   class swept repo-wide — zero remaining member-extension imports).

## 6. The sub-agent audits (the standing ≥2 order, D-689)

- **SA1 (the menu + tracking): S1-S12 all PASS** — the width, the theme-aware
  hierarchy (verified against Theme.kt's actual container roles incl. AMOLED),
  the tracking row's geometry parity, the chip visibility in both themes, the
  status chip's four states + priority, the half-width wheel with the round-103
  contract intact, the haptic guards, the toast contract (the emit gating, the
  replay keying, the auto-dismiss sequence, the pass-through overlay), the
  bullet dialogs' truthfulness (cross-checked against the ViewModel's actual
  delete behavior), the breathing pass, the regression sweep, the compile-risk
  scan.
- **SA2 (the scroll + player list + settings): T1-T14 all PASS** (T7 with one
  cosmetic sub-claim) — the root-cause scope fix (with the reasoning of WHY the
  old placement failed), the arrival-pulse interruption semantics (traced
  through SmoothCenterScroll's bare suspension points), the swipe algebra vs
  the details reference (branch-for-branch), both stacks' key math (verified
  identical to the isWatched computations), the grid long-press, the filter
  removal completeness (the details page's own filter verified untouched),
  TRACKLIST's renderer, the real dim, the EP-tag exclusivity, the banner math,
  the D-557/D-558 port fidelity (branch-by-branch against the reference), the
  style-aware elements, the preview override, the regression + compile scan.
- **Applied (lead-verified):** SA1-F1 (the scheme-luminance polarity — MEDIUM),
  SA1-F2 ("Tracking on"), SA1-F6 (the stale comment), SA2-F1 (the import
  prune), SA2-F3 (the numeral's TextAlign.End).
- **Not applied (documented):** SA1-F3 (the stale-notice replay after a
  mid-toast navigation — rare, harmless, a true event replay), SA1-F4 (the
  light scheme's latent missing surfaceContainerHighest — no current consumer),
  SA1-F5 (pre-existing lint noise), SA1-F7 (the theoretical wheel seed tick —
  byte-identical to the approved reference), SA1-F8 (the deliberate
  delete-failure ordering), SA2-F2 (the tombstone — deliberate), SA2-F4..F9
  (INFO — plan-vs-impl cosmetic deltas, the reference-inherited no-onDragCancel,
  the wash-only grid/banner pulses).

## 7. The round-105 checklist (the device round on v1.1.61)

1. **The menu:** the narrower width (264dp); the CARDS brighter than the panel
   in BOTH themes (and under a FORCED theme opposite the system — the
   luminance fix); the Tracking row reading as one of the family with its
   grey↔green states intact.
2. **The tracking sheet:** the status chip left of the trash can
   (Syncing…/Not synced/Tracking on/Not tracking — watch it flip while a save
   runs); the wheel centered at half the screen; the tick on picker open/close;
   the slightly taller sheet; the bullet-line confirmations; the beautiful
   toast after Start Tracking completes ("Tracking started") and after Remove
   ("No longer tracking") — including the haptic.
3. **The scroll:** tap Scroll to Current on a LONG list — the glide must run
   the WHOLE way (the round-104 root-cause fix), center the current episode,
   then the highlight pulse that fades to normal; TOUCH the list mid-glide —
   it must stop immediately with NO pulse; episode 1 / the last episode stop
   un-centered at the edges.
4. **The player list:** the watched filter is GONE everywhere (settings +
   both players); the Tracklist layout (the big number + spine); the DETAILED
   dim on watched rows; the EP tag ONLY on Detailed (the grid's number in the
   scrim); the banner's Episode number toggle + the Banner size slider (live);
   the Elements rows appearing/disappearing smoothly per layout; the settings
   preview's scroll behaving EXACTLY like the details page's (the two-phase
   snap, the fling handoff); SWIPE a row on either player to toggle watched
   (long-press on grid) — and in the settings preview.
