# ANI-KUTA App Design Language

> The canonical design language for the ANI-KUTA Android app. This is a **living
> document** — updated as design decisions are made. When the user requests UI
> changes, update this doc AND propagate to the code.
>
> **Status:** Fresh start (session web-3a43f99b). The old design language doc
> was deleted. We're rebuilding from the ground up, one rule at a time.
>
> **Distinction:** This is the *app's* design language. The *dashboard's* design
> language lives at `DASHBOARD/webpage/DESIGN.md` (separate product).

---

## 1. Purpose

This document captures the UI/UX rules that define how the ANI-KUTA app looks,
feels, and moves. Every screen follows these rules. When in doubt, refer here.

Rules are added one at a time, only after the user confirms them. Don't add
speculative rules.

---

## 2. Rules

### 2.1 — Collapsing Header (shrinks on scroll)

Every screen with a scrollable list has a **pinned top header** with the screen
title. The header:

- **Expanded (at top):** Large title — 32sp, ExtraBold (800), letterSpacing
  -0.02sp. Status-bar padding applies.
- **Collapsed (scrolled past 20px):** Title shrinks to 24sp (ExtraBold). The
  transition animates via `animateFloatAsState`, 300ms, `FastOutSlowInEasing`.
- **Pinned:** The header sits OUTSIDE the scroll container. It never scrolls
  away — only the title size changes. Actions (back, settings, etc.) stay in
  place.
- **Implementation:** `CollapsingHeader` composable in `:core:designsystem`.
  Pass a `LazyListState` / `LazyGridState` and compute `collapsed =
  state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 20`.

### 2.2 — Scroll Blur Overlay (frosted-glass at header edge)

When content scrolls beneath the pinned header, a **frosted-glass blur overlay**
fades in at the header's bottom edge:

- **At top (scroll = 0):** No blur. The header's background is solid.
- **Scrolled:** A gradient scrim fades in — transparent → `surface.copy(alpha
  = 0.8f)` → `surface` — at the header's bottom edge. The scrim has rounded
  bottom corners.
- **Scroll back to top:** The blur fades out smoothly. The header grows back
  to full size.
- **Implementation:** `ScrollBlurOverlay` composable in `:core:designsystem`.
  Takes a `scrollOffset: () -> Float` lambda. Fades in based on the offset.
  Aligned `TopCenter` over the content Box.
- **Toggle:** Respects `ThemePreferences.headerBlurEffect` — when `false`, no
  overlay renders.

### 2.3 — Hide-on-Scroll Top Bar (Search page specific)

On screens with a multi-row top section (search bar + filters + source toggle),
the **entire top section smoothly hides** when the user scrolls down, and
**smoothly reappears** when scrolling back up:

- **Expanded:** Title (large) + source toggle (AniList/Extension) + search bar
  + filters/sort row — all visible.
- **Scrolled down:** The source toggle + filters row fade + slide out. The
  search bar animates up to replace the toggle's position. The title shrinks.
- **Scrolled back to top:** Everything smoothly re-expands.
- **Animation:** 300ms, `FastOutSlowInEasing`. Use `animateDpAsState` for
  position, `animateFloatAsState` for alpha.
- **Implementation:** `SearchTopBar` in `:feature:anime-search`. The pattern
  can be reused on other multi-row screens if needed.

---

### 2.4 — Nav-Row Icon Language (unified across More + Settings hubs)

Every list row that navigates to a sub-screen — whether on the **More** page or
any **Settings / Appearance / Notifications** hub — uses the **same icon
treatment**: a **bare 24dp `Icon` tinted `primary`, no container box.**

- **Implementation:** reuse [`MoreListRow`](#1-more-page) directly for every
  nav-row slot. Do NOT create a per-screen `*NavRow` variant and do NOT wrap
  the icon in a `primaryContainer` "chip"/"tile" `Surface` — that was the old
  `SettingsNavRow` / `AppearanceNavRow` pattern and it looked like a different
  visual format from the More page (user feedback, D-250).
- **Typography:** title `RobotoFamily ExtraBold 16sp`, subtitle `Normal 13sp`,
  trailing `Icons.Filled.ChevronRight`. All inherited from `MoreListRow`.
- **Back button:** every settings sub-screen's `CollapsingHeader` `actions` slot
  uses the shared `BackAction` from `:core:designsystem` (36dp `CircleShape`
  `surfaceVariant` button + 18dp `Icons.AutoMirrored.Filled.ArrowBack`). No
  per-screen copies.
- **Established:** D-250 (2026-08-24) — user reported that the Settings page
  icons "change to some other kind of format" vs. the More page; root cause was
  the chip-box `primaryContainer` wrapper. Unified to bare icons everywhere.

---

### 2.5 — Cover Badge Language (pointed tags, D-252)

Badges overlaid on cover art (Library grid modes, Browse cards) share one
visual language:

- **Pointed tip**: the chip nearest the cover CENTER tapers into a 45°
  triangle tip (`PointedTagShape` in `:core:designsystem:badge`) — badges
  read as pointed flags pointing INTO the cover. Text keeps +4dp padding on
  the pointed side so it never overlaps the transparent tip.
- **Corner-flush**: the badge row sits flush with the cover corner; its outer
  corner clips to the cover's corner radius (12dp rounded modes, 0dp for
  COVER_ONLY's square covers — `coverCornerRadius` param). No floating
  badges with inset padding on grid covers.
- **Shared colors**: `BadgeColorScheme` (designsystem) — SUB blue / DUB
  orange / Total green / Score amber / All-Caught-Up red, hand-picked Material
  pairs that adapt to the APPLIED theme (background luminance, not the system
  setting). Browse's score tag uses the same amber score colors — no
  per-screen badge palettes.
- **Compound badges** (SUB+DUB split) draw their split background with
  `drawBehind` — always `Modifier.clip(shape).drawBehind { ... }` (clip BEFORE
  draw), because M3 Surface applies its own shape-clip AFTER user modifiers.
- **Outlined (D-257)**: every cover badge carries a 1dp outline at the chip's
  own content color @ 50% alpha (Browse score tag + Library simple chips via
  the m3 `Surface(border=…)` param — the stroke follows `PointedTagShape`
  incl. the 45° tip; the compound sub|dub badge draws its outline as a manual
  stroked Path inside the same drawBehind, replicating the pointed geometry —
  a Surface border can't trace hand-drawn paint). Keeps the tags crisp
  against busy cover art.

### 2.6 — Custom Theme (D-254)

When the CUSTOM accent preset is active, the theme comes entirely from the
user's `CustomThemeColors` (accent + background + heading + card, each with a
brightness offset):

- Custom colors apply **as-is in both light & dark mode**; the mode toggle
  only affects presets. AMOLED is skipped while custom is active.
- One pick derives a coherent theme: text colors by background luminance;
  surface ramp = background lerped toward text; card family →
  surfaceVariant/containers.
- Heading color flows through `LocalHeadingColor` (Unspecified sentinel →
  default onBackground) — CollapsingHeader titles read it.
- The editor (CustomPaletteSheet) forces alpha opaque — translucent theme
  surfaces are not supported.
- **Editor + picker sheet rules (D-259)**: editor sheets use a STICKY header
  (title + primary action OUTSIDE the scroll area; no X button — dismiss via
  swipe/scrim) with a scroll-driven `ScrollBlurOverlay` scrim at the top of
  the content. All value sliders are `ThinSlider` (4dp track + 18dp
  rounded-square thumb with surface halo; 36dp grab area) and every numeric
  value renders as a TAPPABLE chip that opens the shared NumericEntrySheet
  keypad (live-applied). Presets are exactly FIVE distinct colors in a single
  equal-width line of rounded tiles.

---

## 3. Future Rules (pending user confirmation)

Rules the user has hinted at but not yet confirmed. Don't implement until
confirmed.

- *Button press feedback* (scale-down + ripple).
- *Card press animation* (scale 0.95f on press, no ripple).
- *Section card backgrounds* (dedicated surface per section, minimal padding).
- *Bottom-up sheet* cap at 70% screen height (D-052).
- *Floating pill bottom nav* (4 tabs, translucent).
- *Translucent cards* (no shadow, surfaceVariant at low alpha).
- *Hero pager* — IMPLEMENTED (D-253 full-bleed → D-256 poster+banner → **D-257 hero v3**:
  inset 16:9 rounded 20dp card, infinite forward-only auto-advance, dots below
  the card). Promote to a confirmed rule after user device verification of v3.

---

## 4. Color System

(Lives in `:core:designsystem/theme/Color.kt`. Will be documented here as
rules solidify.)

- **Dark theme (default):** Warm-purple-tinted darks. Background `#14111F`,
  5-tier surface ramp → `#3D3656`. Primary lime `#B1F256`.
- **Light theme:** Warm-neutral backgrounds. Background `#FAF9F6`.
- **AMOLED:** Pure black backgrounds/surfaces when `amoled = true` + dark.
- **Accent override:** `AnikutaTheme(accentSeed = ...)` overrides the primary
  family (D-053). Background/surface ramp stays fixed.
- **Custom theme (D-254):** `AnikutaTheme(customTheme = ...)` builds the whole
  scheme from the user's per-element picks — see §2.6.

---

## 5. Typography

- **Font:** Roboto (bundled — 4 TTF files: regular, medium, bold, black). Fixes
  bold-text rendering on all devices.
- **Family:** `RobotoFamily` in `:core:designsystem`.
- **Titles:** ExtraBold (800). Section labels: 14sp ExtraBold primary color.
- **Body:** Medium (500). 14-15sp.
- **Captions:** 11-12sp. onSurfaceVariant color.

---

## 6. Motion

- **Duration:** Standard 300ms, Short 150ms.
- **Easing:** `FastOutSlowInEasing` for all transitions.
- **60fps target:** No heavy work on main thread during animation. Use
  `graphicsLayer` for scale/alpha (avoids recomposition).
- **No instant cuts:** State changes (expand/collapse, appear/disappear)
  always animate — never pop in/out.

---

## 7. Update Process

1. User requests a UI change.
2. Update this doc (add/modify a rule).
3. Implement in code (`:core:designsystem` or the feature module).
4. Verify visually.
5. Commit doc + code in the same commit (CORE_RULES §22).

---

## 8. Deep-Dive Documentation

The rules above are the **quick reference**. For implementation details, code
snippets, and screen-by-screen breakdowns, see the **Design System** folder:

```
APP/ani-kuta/DOCUMENTATION/DESIGN-SYSTEM/
├── README.md                           — Index + how to use
├── 01-navigation-and-sheets.md         — Bottom nav, collapsing header, scroll blur, bottom sheets, filter sheet, hide-on-scroll
├── 02-lists-cards-pills.md             — Pill/tag system, episode rows, download rows, library cards, downloads page
├── 03-settings-extensions-profile.md   — More page, profile, appearance, extensions, extension details, download settings, update sheet, bottom-up menu pattern
└── 04-player-details-search-library.md — Player (portrait+fullscreen), subtitle settings, details top, search, library
```

**22+ design patterns** documented across **18+ screens** with verbatim code
snippets, file paths, and design decision tables. Created in D-216.

---

*This document is the source of truth for the app's UI. If code disagrees with
this doc, fix the code (or update the doc if the change is intentional).*
