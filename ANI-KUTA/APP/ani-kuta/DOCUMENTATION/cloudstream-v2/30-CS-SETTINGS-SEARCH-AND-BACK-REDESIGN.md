# 30 — CS: THE SETTINGS SEARCH + THE HEADING-BACK REDESIGN + THE LAYOUT POLISH (round 70 / D-558)

Round 70 implements the v1.1.31 device round. The verified-good carry held
(the swipe toggles "properly, without any problems" on every swipe; the
download cycle "everything was working perfectly"; classic and grid are done),
and the round's four streams: the scroll-collapse hardened into a
DETERMINISTIC priority scroll, the timeline got its thin gap, CINEMA got its
own customizability section, and the two app-wide additions — the SETTINGS
SEARCH (modular, synonym-aware, navigate + scroll + highlight) and the
HEADING-BACK redesign (the back button moves left of the title and the title
itself becomes the back button).

## D-558-A — the scroll collapse v3: SPLIT BY DIRECTION (deterministic by construction)

The v1.1.31 round kept the D-557 snap but exposed two blind spots: "if I
quickly swipe up to scroll, then the top live preview does not scroll first.
The bottom section scrolls" (fast flings slipped past the drag-only
accumulator, whose magnitude never saw the fling velocity), and "if I have
scrolled to the very bottom and then try to scroll up … the live preview
starts to [expand]" — the expansion was a PRE-scroll consumer, so it stole
up-deltas while the list was still scrolled down.

The v3 connection in `EpisodeListSettingsScreen` is split BY DIRECTION:

- **DOWN while open** (drag OR fling): the collapse consumes EVERYTHING
  first — the list cannot move until the preview snaps collapsed ("no matter
  what happens, the first of all thing will be that the live preview will
  move up"). After the snap the connection RELEASES the same gesture (the
  crossed latch doubles as the release valve): remaining drag deltas flow
  into the list ("and after that then the bottom section will begin to
  scroll over"). A consumed down-fling (velocity < −1000) additionally HANDS
  ITS MOMENTUM to the list through a spline-decay scroll — the documented
  `AnimationState(initialValue, initialVelocity).animateDecay(spline) { dispatchRawDelta(delta) }`
  fling pattern — so a fast swipe collapses AND keeps scrolling instead of
  dying at the snap (the v1.1.30 complaint's other half).
- **UP while collapsed**: the connection NEVER consumes in pre-scroll — the
  list always scrolls first; only the POST-scroll LEFTOVER (the list at the
  very top with delta to spare) expands the preview, with the same halfway
  snap and drag-follow. An up-fling's leftover settles the expansion in
  `onPostFling` ("first of all the bottom section should scroll to the very
  top", THEN the preview opens).

The halfway snap, the crossed latch (one flip per gesture), the 180ms settle
window, the GRID exemption and the style-switch re-open carry over from the
D-557 design unchanged.

## D-558-B — the timeline thin gap

The union blob over-merged: the card's material "is kind of way too close to
the release date … expanding towards the release date side a bit too much. So
I was hoping for a thin area between those to feel like an actual blob kind
of feel." `TimelineBlobCardShape` keeps ONE outline but its two sub-shapes
are DISJOINT now — the symmetric stadium keeps hugging the node + date
(bump 2..44dp) and the card body starts 8dp to its right (cardLeft 52dp), a
thin background-colored breath between them (content padding re-derives from
cardLeft). No seam ever re-appears because there is no junction — the shapes
simply do not touch.

## D-558-C — CINEMA customizability: the dedicated animated section

The user asked for the CINEMA layout's own options in "a dedicated section …
below the layout section and above the element section … only show for the
cinema section … if we switch to any other section, then it will smoothly,
with beautiful clean animations, disappear." Implementation:

- **Storage**: three new `EpisodeListPreferences` keys — `cinemaNumberCorner`
  (RIGHT default = today's look / LEFT), `cinemaNumberStyle` (SOLID default /
  FROSTED), `cinemaWatchedCheck` (default FALSE — "by default it will be
  turned off"). `EpisodeListDisplayStyle` gains the three fields (defaults =
  current behavior — the test lock holds).
- **The card**: the ghost number aligns TopStart or TopEnd per the corner
  pref; FROSTED renders the number BETWEEN two layers of glass — a
  translucent rounded plate (white 16% + a 28% hairline) behind and a
  vertical frost veil ON TOP (22%→6% gradient) — "the text will actually be
  frosted glass kind of effect … slightly transparent, frosted effect will be
  on top of it". The watched dim/grayscale stays; the centered circular
  check renders only when `cinemaWatchedCheck` is on.
- **The section**: a LazyColumn item between Layout and Elements whose
  content is an `AnimatedVisibility(selectedStyle == CINEMA)` —
  expandVertically + fade in 300ms, shrink + fade out 240ms. Inside: two
  single-row SegmentedToggles ("Number position" Top left/Top right; "Number
  style" Solid/Frosted — the Layout section's format, NO description lines
  per the user's explicit spec) + a description-less switch row ("Watched
  check mark"). The live preview re-shapes instantly (the same prefs).
- **The bottom hint**: the page's last item is the quiet centered caption
  "Can be further customized by clicking episodes on the page" (the user's
  exact wording, "almost exactly like this").

## D-558-D — the settings SEARCH (the big one)

"Adding search functionality for the settings and all the related things …
smarter … theme, UI, accent … sorted properly based on the relevance …
clicking on any of those results leads the user to the appropriate settings
page and properly scrolls to the appropriate level and properly highlights
it." Architecture — FIVE new files in `app/settings/search/`, deliberately
modular ("future-proof, like we can easily edit, configure, change, manage"):

- **`SettingsSearchModels.kt`** — `SettingsSearchPage` (one value per
  destination, each carrying its breadcrumb), `SettingsSearchEntry`
  (id/title/page/anchor/keywords — the data record), `SettingsSearchResult`.
  Pure Kotlin, no Compose.
- **`SettingsSearchEngine.kt`** — the pure relevance scorer. Every token must
  match somewhere; per-token weights (title exact 100 / prefix 85 /
  word-boundary 75 / contains 60; keyword exact 65 / prefix 50 / contains 40;
  synonym-title 45 / synonym-keyword 35; subsequence typo floor 20); the
  built-in synonym map (theme ⇄ ui ⇄ accent ⇄ palette; ep → episode; dl →
  download; …); ties break alphabetically; 12-result cap. The weights are
  the single tuning surface — the class KDoc is the maintenance manual.
- **`SettingsSearchIndex.kt`** — the ONE curated list (~45 entries covering
  every settings hub row, appearance/general/theme/palettes/AMOLED/adaptive/
  blur, the episode-list page incl. the new cinema options, details page,
  app icon, extensions, auto-link, updates/notifications family, player,
  caching, downloads, trackers, about, debug, history/updates/profile).
  Adding an item = appending one entry; no engine/UI/navigation edits.
- **`SettingsSearchNavigator.kt`** — the consume-once pending-anchor holder
  (`request(page, anchor)` on tap; `takeAnchor(page)` in the destination —
  mismatched reads leave the request intact, so chained pushes deliver the
  anchor only to the top screen).
- **`SettingsHighlight.kt`** — `rememberSettingsAnchorScroll` (a
  LaunchedEffect scrolling the LazyColumn to the anchor's item index; each
  screen owns its anchor→index map, mode-aware where items are conditional)
  + `SettingsHighlightTarget` (wraps a row; when active it pulses three soft
  primary glows; dormant targets render as a plain Box — zero cost).

**The UI**: the hub gains a rounded search bar (search glyph + field + clear)
as the first row; typing swaps the hub rows for the ranked results — each row
shows the title + the "Settings → Appearance → General" breadcrumb, and the
empty state suggests query words. Tap → `onOpenSearchResult` in MainActivity
→ `SettingsSearchNavigator.request` + the destination's backstack pushes
(sub-page targets chain BOTH keys — Appearance → General — so back pops
naturally) → the target screen consumes its anchor, scrolls, pulses.

## D-558-E — the heading IS the back button (app-wide)

"Move the back button to the left side, just left of the top heading text …
we will be turning the top heading text into the back button … the heading
should not be moved to the right that much." `CollapsingHeader` gains
`onBack: (() -> Unit)? = null`: a COMPACT 30dp arrow box + an 8dp breath
before the title, and the whole title becomes tappable back. The trailing
actions slot keeps every screen's other icons (delete-sweep, refresh,
settings, filters). Converted everywhere the user listed — all settings
screens (Settings, Appearance, General, Episode list, Details page, App Icon,
Player, Video caching, Updates & Notifications, Update categories, Check
history, Notifications, Poster, Poster library, Debug, About, Trackers) plus
History, Updates, Downloads + its settings/files screens, the Extensions
family (Extensions, Repositories, Extension detail, Source preferences,
Auto-Link, CS plugin detail), CS category, Downloads' own screens, and
Profile (which had no back at all). The top-right `BackAction` retires
app-wide (~24 call sites); UpdatesSettingsScreen GAINED its first-ever back
affordance (previously gesture-only).

## Independent review (pre-push)

1 blocker (the search bar's `focusManager` resolved outside its composable —
`LocalFocusManager.current` hoisted inside) + 2 advisories (the
UpdatesSettingsScreen mode-aware anchor map missed the check-now card —
indexes recomputed for OFF/AUTO/MANUAL; a dead ArrowBack import) — ALL fixed
pre-push. Brace/paren balance machine-verified on every changed file.

Untouched BY DESIGN: the verified-good carry (swipe, download cycle, classic,
grid, the real-library preview, the 0.67 sheets, the sliding pill), the prefs
keys of prior rounds, the download pipeline, the resolve flow, the season
systems, the outer LazyColumn virtualizer.
