# 35 — The launcher icons RESIZED-not-cropped + the animated hero logo back on the popup + the button-navigation-safe bottom bar (Round 75 / D-563, the v1.1.36 device round)

**Date:** 2026-09-24 (round 75) · **Branch:** `feature/round-57-cloudstream-downloads` · **Base:** c0f40a5a (round-74 LIVE mirror) · **Next release:** v1.1.37 (10137, both releases this cycle)

The v1.1.36 device round accepted the icon FUNCTION (the launcher icon actually changes now), the debug-options page, and the entry-click Always-sponsor trigger — and set three new directives plus the standing release order:

1. **The non-default icons are CROPPED** — "the default main icon, which is the first one, is proper, but the other ones are not proper… the issues are with their cropping level, like they have been cropped badly on the right and left sides… make sure that the app's icons are not cropped but rather resized… so that the icon looks properly exactly like how the preview showed. Like the presets are shown properly, but when the app icon gets applied, it is zoomed in."
2. **The popup lost its logo** — "previously at the top of the pop-up it had an SVG logo, but now it does not. So I would like you to add that SVG logo with a proper animation and such."
3. **The bottom nav hides under the system buttons** — "when the user is using buttons as system navigation… the bottom navigation bar shows under the bottom navigation bar buttons… If the user is using button navigation, then the bottom navigation bar, the one with the Home, the Library, the Search, and the More, will be shown a little bit up, properly adjusted for and calculated, so that it does not show under the buttons."
4. **The release** — "build the release version of it and make sure that the version is exactly the same version, 1.1.3" — the same 1.1.3x shorthand as round 74's order (delivered as 1.1.36 and accepted), so the next record in the sequence: **v1.1.37/10137**, both releases.

## 1. WHY THE PRESETS LOOKED ZOOMED (the adaptive-icon geometry, root-caused)

Round 74's aliases made the icon REALLY switch — but each preset's artwork was placed as the adaptive icon's **BACKGROUND** layer (`<background android:drawable="@drawable/preset_icon_<key>"/>`). The adaptive-icon contract: every layer is a **108×108dp canvas**, and the launcher's shape mask shows only the **central ~72dp (~66%)** — the layer is rendered at full canvas size and the mask crops it. Result: every preset rendered **~1.5× zoomed with ~16.7% cut off each side** — exactly "cropped badly on the right and left sides… it is zoomed in." The DEFAULT icon survived the same crop because its artwork (`ic_launcher_bg.webp`) was DESIGNED for full-bleed (the character sits deep inside the safe zone); the six preset artworks are full-bleed designs whose mouth reaches toward the edges — nothing was designed to be cut.

**The fix — RESIZE, don't crop** (pure resource change; the D-562 alias/switch/reconcile machinery, the manifest, and the in-app preview JPGs are untouched):

- **`drawable-nodpi/preset_icon_<key>_fg.png` (NEW, 6 files, 512×512 RGBA):** the artwork resized (LANCZOS) to **66% of the canvas (338px)** and centered on transparency. 66% of 108dp ≈ 71dp — inside the ~72dp visible circle of EVERY mask shape (circle, squircle, rounded square), so **the entire design is always visible**: edge midpoints sit just inside a circle mask, corners are untouched by squircles. Nothing is ever cropped.
- **The adaptive XMLs (6 rewritten):** `<background android:drawable="@color/preset_icon_<key>_bg"/>` — a **SOLID color sampled from the artwork's own outer 6px frame** (machine-computed; e.g. teal `#272738`, sky `#BBE0F5`, gold `#F8F2E1`) + `<foreground android:drawable="@drawable/preset_icon_<key>_fg"/>`. Because each artwork's background is near-uniform (corner deltas ≤ 5 per channel), the inset artwork **melts seamlessly into the matched background** — the icon reads as the full design with breathing room, no visible seam, no ring.
- **`mipmap-xxxhdpi/ic_launcher_<key>.jpg` (6 regenerated, the API 24–25 legacy fallback):** 512×512 RGB — the artwork at **72%** centered on the same matched solid background (legacy launchers shrink-to-fit rather than crop; the padding keeps the design safe under any legacy mask).
- **`values/colors.xml`:** the six `preset_icon_<key>_bg` colors added.
- **Verification:** a machine-composited preview sheet (full canvas / 72dp circle mask / rounded-square mask per preset) confirmed the complete mouth visible in every shape with a seamless background blend — before anything shipped.

The in-app preview (`AppIconScreen` hero + cells) still paints `drawable/preset_icon_<key>` (the untouched full-bleed JPGs) — the page now matches the launcher exactly: full artwork, nothing cropped.

## 2. THE ANIMATED HERO LOGO RETURNS (SmartLinkAdInterstitial)

The D-562 heading-only pass deleted the `HeroBubble` — and the user missed "the SVG logo at the top." The D-560 shape returns to ALL THREE states with the same glyphs it always carried (Pending: `OpenInNew`, Waiting: the spinner, TryAgain: `Refresh`), now **animated twice over**:

- **ENTRANCE — a spring pop:** `Animatable(0f → 1f)` on `Spring.DampingRatioMediumBouncy`/`StiffnessMediumLow` driving scale `0.6 → 1` (with the bouncy overshoot) + a fade-in. Fired in a `LaunchedEffect(Unit)` the moment the hero composes — and because the card's `Crossfade` composes a FRESH hero per state, the pop **replays on every state transition**.
- **BREATHING — a quiet loop:** `rememberInfiniteTransition.animateFloat` 1f → 1.045f, 1.2s each way, `RepeatMode.Reverse`, `FastOutSlowInEasing`, multiplied into the settled scale — the mark gently breathes while the card waits. Motion, not decoration: the D-560 "no furniture" rule holds (same 64dp tinted circle, primary @ 0.10, no borders/gradients).
- **Draw-phase only:** both animations are read inside the `graphicsLayer` lambda — scale/alpha updates never trigger recompositions.

Everything else on the card is the frozen D-562/D-561 shape byte-for-byte: "Support AniKuta" → "(It just takes a few seconds)" → Continue → Not now → the overlay row only while the consent is missing. The heading remains the first TEXT element.

## 3. THE BOTTOM BAR CLEARS THE SYSTEM BUTTONS (D-563)

The app is edge-to-edge (`enableEdgeToEdge`, transparent nav bars) and `AnikutaBottomNavBar` carried only a **fixed 16dp vertical padding** — with 3-button navigation the pill rendered at the physical bottom edge, **under the system's back/home/recents buttons**.

- **The pill** (`AnikutaBottomNavBar` outer Box): `.navigationBarsPadding()` applied BEFORE the fixed 16dp margin — the pill lifts above whatever the system draws at the bottom (48dp+ of buttons, the ~16dp gesture pill) in EVERY mode; the floating-pill scrolls-behind design is unchanged (content still flows underneath).
- **The clearance math:** lifting the pill moves its top edge to ~122dp above the screen bottom in button mode — the root tabs' hard-coded 90/110dp scroll tails would let the last rows slide UNDER the lifted pill. A new designsystem helper **`bottomBarClearance(base: Dp)`** returns `base + WindowInsets.navigationBars` bottom — and the ROOT-TAB scrolling containers now use it: **BrowseScreen** (the grid, 90dp), **LibraryScreen** (staggered grid / cover-only grid / compact grid / list — 4 sites, the 160dp selection-mode tails included), **SearchScreen** (the recents column + both result grids + both list tails — 5 sites), **MoreScreen** (the list, 110dp). Gesture navigation reports the small ~16dp gesture-pill inset through the SAME math — one formula, both modes.
- **Scope discipline:** the helper touches only scroll TAIL space (padding after the last item) — lists still draw edge-to-edge while scrolling, so the approved gesture-mode look is untouched. Sub-screens and the details page (no pill, bottom-anchored gradient scrims — frozen round-71 work) are deliberately untouched.

## 4. VERIFICATION

- The six adaptive XMLs + colors.xml machine-parsed (minidom); every `@color`/`@drawable` reference cross-checked to a real resource.
- Brace/paren balance machine-verified (nesting-aware comment stripping) on all six touched Kotlin files.
- The icon preview sheet (full/circle/squircle per preset) machine-composited and visually confirmed BEFORE shipping.
- The frozen carry untouched: the D-560/D-561/D-562 popup machinery lines, the overlay row, the alias switch + reconcile, the timeline neck, the frosted text, the heading-back, the search family, the wizard one-liner, the episode list, the scroll collapse.

## 5. THE RELEASE

v1.1.37/10137 — both releases from the SAME tag per the standing order: implementation CI green → release/1.1.37 cut → the bump commit (the record comment rides it) → tag v1.1.37 (What's-New bullets + the Install line, message file repo-external) → the debug release run → the release-build-once.yml dispatch (the 5-ABI ACTUAL release artifact). The ≤2 budget disclosed as superseded by the explicit both-releases order, fourth consecutive cycle.
