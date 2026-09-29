# Round 105 — The Menu's Last Crimp, The Sheet's Side Buttons, The Honest Renames, And The Player List's Finished Customizability

> Round record 87. Planning header written BEFORE implementation (the workflow §3 rule).
> Status: PLANNING → IN EXECUTION.

## 0. The device round (v1.1.61)

The user's report, decomposed into the four work streams this round executes:

1. **THE THREE-DOT MENU — one last crimp.** The structure, the look, the
   hierarchy: all approved ("quite happy with the size of the menu, the
   overall look of it, the width of it too"). One residual order:
   - "I would like you to make the width a little bit smaller, like a bit
     more, but keep everything else exactly the same."
2. **THE TRACKING SHEET — the quality-of-life pass.** The tracking button,
   the sheet, the status chip ("Not tracking" at the top left — "perfect"),
   and the pickers' clean look: all approved. Four orders:
   - THE WHEEL-SCROLL LEAK: "if I try to swipe down from that menu, then it
     will not close the tracking menu, bottom-up menu itself. Like currently,
     if I am, for example, on episode number one, or on the episode not
     started, and then I scroll down, then the bottom-up tracking menu
     apparently starts to close… It should not happen if I scroll down on
     that area."
   - THE SIDE BUTTONS: "the current area for selecting the progress or the
     rating is quite proper, like it is centered, but the right and left
     sides are apparently empty… you can add a plus or minus buttons for the
     progress and for the scores there… The buttons itself will be adaptable
     buttons, like they will adapt based on the available space… They will
     adapt their width, and their height will be the same as the normal one."
   - THE STATUS SIDES: "for the status, it will not show plus or minus
     buttons, but instead it will show a better kind of visual, or better
     suitable button options there, so that things look proper."
   - THE SAVE BUTTON: "the save button when the tracking is not started. I
     would like you to improve that save button, and I would like you to make
     it a bit better and a bit more proper."
3. **THE APPEARANCE ENTRIES — the honest renames.** In Settings → Appearance
   → Episode List: "one option says episode list, and the other one says
   player episode list… name the first one, which is currently named as
   episode list, as details page, and the bottom one, which is currently
   named as player episode list, as player page."
4. **THE PLAYER EPISODE LIST SETTINGS — the formatting + the real knobs.**
   The preview's collapse/expand scroll: approved ("these things are handled
   properly"). The options themselves: "the overall experience is not that
   proper. Like the options are not that well formatted or that well
   handled." Per layout:
   - DETAILED: "there is a lack of customizability there still. Like, I
     should be given an option there to show or hide the progress bar, and
     also I would like you to add small short descriptions to the elements,
     like for the synopsis, the date pill, the dim watched episodes, and
     such."
   - TRACKLIST: "the UI of it and the layout can be improved a little bit.
     Like the episode number was shown on the left side, but there was some
     padding on the left too. Like there was way too much padding, and then
     the actual episode number showed. And also the episode number should be
     made a little bit bigger. Besides this, there was no option to turn on
     or show the synopsis or turn off the synopsis."
   - GRID: "there were not much options there at all… for the watched
     episode, it only gives the user one option, whether to dim the watched
     episodes or not. But it should properly give the user one option, which
     is to show the checkmark on the watched episodes or not, rather than
     showing the user the dim option here. And there should be the same thing
     for the currently playing episode too. The user should be given the
     option between showing the play button on the currently playing or
     rather theme the currently playing episode list or cover image or such
     in the themed color… if the user has selected theme, then the whole
     thumbnail image will be tinted."
   - BANNER: "at the bottom left, the name of the current episode should be
     shown, and above it the details should be shown, and also the details
     need to be shown in a better-looking tag, just like how they are being
     shown in the episode list vibe. Besides that, there should be the option
     to select where the episode number should show, whether it should show
     on the top right side or on the top left side. And also there should be
     the option to select in what format the episode number should be shown.
     Should it be shown in solid themed color, or should it be shown in a
     frosted theme color? Because currently it is not showing in any of those
     formats. Besides that, next you have implemented the banner size
     selection, but apparently that is most definitely not how it is meant to
     be. It should not change the height of the banner, but what it should
     change is the actual size of the whole thumbnail cover image banner
     itself… If the user has selected it to be smaller, then there will be
     some padding on the left and right sides and also some padding between
     each individual episodes themselves… and also… the UI of the banner size
     bar."

## 1. Root-cause research (pre-implementation, verified against HEAD e258e70b)

- **The menu width (DetailsActionMenu.kt:163/:280)**: both content Columns
  sit at `.width(264.dp)`; the rows are maxLines=1 + ellipsis (nothing can
  overflow). A pure constant change.
- **The wheel-scroll leak (TrackSheet.kt:665-712)**: the wheel is a real
  snap LazyColumn inside the ModalBottomSheet. M3's sheet installs a
  NestedScrollConnection on its content — when the wheel CANNOT consume a
  drag (at the list's top edge, exactly "episode not started" / episode 1),
  the leftover delta flows UP to the sheet's connection, which translates it
  into the sheet's anchored drag → the sheet starts closing. The gesture is
  never contained at the wheel. THE FIX: a containment connection on the
  wheel's own Surface — onPostScroll returns ALL leftover (the sheet's
  connection never sees a wheel-area delta) and onPostFling returns the
  leftover velocity (no dismissal momentum); onPreScroll/onPreFling consume
  NOTHING (the wheel's own scrolling and snap fling run first, untouched).
  The sheet stays closable from every OTHER area (the header, the cells, the
  dates, the buttons) — only the wheel area is fenced.
- **The empty sides (TrackSheet.kt:265-294)**: the AnimatedVisibility's Box
  centers the wheel at half the screen; the remaining ≈half splits into two
  dead margins. The steppers fill them with weight(1f) each side (adaptive
  width by construction), height = the wheel's fixed 168dp viewport
  ("their height will be the same as the normal one"). The wheel itself
  needs a new capability: re-centering when the selection changes from
  OUTSIDE (the +/− taps) — today only the seed effect (:645) and taps
  (:692-708) move it. A LaunchedEffect(selectedIndex) that waits for
  scroll-idle and animates to the selection only when the CENTERED row
  differs (guarded by the tap-centering suppression counter, renamed to the
  programmatic-centering counter) — this ALSO closes the doc-86 §6 F3 gap
  (the background draft re-seed jumps the selection without re-centering).
- **The status sides**: cycling prev/next is meaningless for an unordered
  enum; the two genuinely common quick actions are Watching and Completed.
  Quick-set buttons (icon disc + label, active state when the draft's status
  matches) — useful "button options", not decoration.
- **The Save button (TrackSheet.kt:364-372)**: today a flat
  surfaceVariant@0.5 fill that reads as an equal-weight peer of Start
  Tracking. A proper secondary: the OUTLINED treatment (transparent fill +
  1.25dp outlineVariant stroke + onSurfaceVariant Medium text) with a small
  Save glyph — the Material filled/outlined pair language.
- **The renames (AppearanceScreen.kt:101-123)**: the rows and their target
  screens' titles. COLLISION FOUND: a THIRD row is already titled
  "Details page" (the background/tint/animation screen,
  DetailsPageSettingsScreen.kt:74). Renaming "Episode list" → "Details page"
  as ordered would leave TWO identical row titles on one screen — resolved
  by retitling the background one to "Details background" (its subtitle
  already says "Background image, tint, and animation"). DISCLOSED to the
  user as a judgment call. The player row's subtitle also still says
  "filter" — stale since the round-104 filter removal; refreshed.
- **The TRACKLIST left padding (PlayerEpisodeListLayouts.kt:648-672)**: the
  number column is a FIXED 52dp with TextAlign.End — a single-digit number
  ("5") sits ~39dp into its column before the glyph even starts, plus the
  row's 12dp padding: the exact "way too much padding, and then the actual
  episode number showed". THE ROOT-CAUSE FIX: size the column to the LIST's
  widest number, not a constant — the new `tracklistReferenceNumber` flows
  from each caller (the list's max episode number, pre-formatted), the row
  measures it once with a TextMeasurer, and the column becomes
  exact-fit + TextAlign.Start (the digit hugs the left edge at 10dp row
  padding; the hairline spine stays at a list-stable position — the classic
  pressed-tracklist typography). The number grows 21sp → 24sp ("a little bit
  bigger"), the spine 30dp → 34dp.
- **The GRID (PlayerEpisodeListLayouts.kt:770-913)**: watchedGray rides
  display.dimWatched (:781) — the knob the user wants REPLACED here by a
  checkmark toggle; the current treatment is the play disc only (:845-860);
  the scrim carries "EP N · Title" (:862-900) and NO pills (showDatePill is
  ignored by GRID — a doc-86 §1 noted gap).
- **The BANNER (PlayerEpisodeListLayouts.kt:921-1088)**: the aspect is
  density-driven (bannerAspectRatio, 21:9→4:3 at fixed width — the "it
  changes the height" the user rejects); the number is WHITE@0.20 (:980 —
  "not showing in any of those formats": neither solid-themed nor
  frosted-themed); the scrim puts the TITLE first and the translucent white
  chips BELOW (:1033-1073) — the inverse of the ordered layout, in the
  wrong tag language. THE REFERENCES: the details page's CINEMA card
  (EpisodeLayouts.kt:1170-1238) already carries the approved number
  treatments — FROSTED = two stacked copies (a blurred primary@0.45 halo on
  S+; a crisp primary@0.58 with a soft dark shadow on top) and SOLID =
  primary 56sp with a dark shadow; and the details settings screen already
  speaks the "Number style: Solid/Frosted" + corner control vocabulary. The
  player banner ports those treatments verbatim. THE SIZE SEMANTICS: the
  slider becomes an ITEM-WIDTH scale — the banner card keeps a FIXED 16:9
  aspect; bannerSize 1f = today's full-bleed; smaller = the swipe wrapper
  narrows to a fraction (lerp 0.66f…1f) and CENTERS (the "padding on the
  left and right sides"), while the per-item vertical padding grows
  4dp→10dp (the "padding between each individual episodes"). The old
  bannerDensity key is tombstoned (its stored semantics are incompatible —
  the D-529 lesson; default 1f preserves the current look byte-for-byte).

## 2. The design decisions (pre-implementation)

- **D-704 (the menu's last crimp)**: 264dp → 240dp on both pages; every
  other metric byte-identical.
- **D-705 (the sheet's QoL)**: the wheel-scroll containment connection
  (post-scroll + post-fling full consumption, pre-consumption zero); the
  ±steppers (PROGRESS ±1 within 0..effectiveTotal; SCORE ±10 indices =
  ±1.0 on the display scale — the AniYomi stepper's step — within 0..100,
  from "—" the + lands at 1.0; adaptive weight(1f) sides, 168dp height,
  at-bound disabled states, lightTick on press); the external-selection
  re-centering (the programmatic-centering suppression, idle-waiting, only
  when centered ≠ selected); the status quick-sets (LEFT Watching /
  RIGHT Completed, icon disc + label, active ring when current, tick +
  re-center on tap); the outlined Save (transparent fill + 1.25dp
  outlineVariant + onSurfaceVariant Medium + the Save glyph; the tracked
  state's primary Save unchanged).
- **D-706 (the honest renames)**: "Episode list" → "Details page" (row +
  the episode-list settings screen's title); "Player episode list" →
  "Player page" (row + the player settings screen's title); the background
  row → "Details background" (row + its screen's title — the collision
  fix, disclosed); the section label → "Episode Lists"; the player row's
  subtitle de-staled ("Row style, elements, and live preview" — no
  "filter"); the six code comments that cite the old entry name updated.
- **D-707 (the player list's finished customizability)**:
  - Prefs: showProgressBar (true), bannerSize (1f), bannerNumberPosition
    ("end"), bannerNumberStyle ("frosted"), gridWatchedCheckmark (true),
    gridCurrentStyle ("play"), gridTitles (true); bannerDensity tombstoned.
    String knobs parse through lenient fromKey lookups (the rowStyle
    doctrine); the display bundle carries the typed enums.
  - DETAILED: the progress bar gated on showProgressBar; the Elements rows
    gain one-line descriptions (the details page's EpisodeListSwitchRow
    vocabulary).
  - TRACKLIST: the exact-fit number column (the caller's
    tracklistReferenceNumber, TextMeasurer, TextAlign.Start, 24sp
    ExtraBold, 10dp row start padding, 34dp spine); the OPTIONAL synopsis
    (showSynopsis now DETAILED + TRACKLIST; 2 lines under the title); the
    underline gated on showProgressBar.
  - GRID: the watched treatment becomes the CHECKMARK knob (grayscale +
    check together — dim no longer applies to GRID); the current treatment
    becomes PLAY (the disc, today) vs TINT (grayscale imagery + a
    primary@0.5 wash + the ring); the scrim's title line gated on
    gridTitles; a pills row (date via showDatePill + audio) joins the scrim
    in the Pill() language.
  - BANNER: the aspect FIXED at 16:9; the overlay inverts (the pills row
    ABOVE, the episode NAME at the bottom-left — both in the Pill() /
    white-Bold language); the number ported from CINEMA verbatim (position
    START/END + FROSTED two-copy / SOLID shadowed, both THEMED —
    primary-colored at last); the size slider = the item-width scale
    (fraction lerp 0.66f…1f centered + vertical padding 4…10dp), its UI
    re-labeled ("Banner size" + a live percentage + "Small"/"Large" ends).
  - The Elements card: every row described; the banner's position/style
    rows NESTED under the Episode number toggle (animated appear/hide); the
    Layout card loses its duplicate inner title and gains a per-style
    caption; the Sort card loses its duplicate inner title too.
  - Callers: WatchScreen + CsWatchPage + the settings preview collect the
    new prefs, build the full display bundle, and pass their list's max
    number as the tracklist reference.

## 3. Execution order

Stream A (the width) → Stream C (the renames) → Stream B (the sheet) →
Stream D (the player list) → self-review → the two guided sub-agent audits →
CI → the ledger → v1.1.62 → ntfy (topic **TASK808DONE** — the user's
standing this-session override).

---
*(The execution record, the CI history, the judgment calls, and the round-106
checklist append below as the streams land.)*
